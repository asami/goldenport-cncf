package org.goldenport.cncf.workflow

import java.time.{Clock, Instant, ZoneOffset}
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.nio.file.{Files, Paths}
import cats.syntax.functor.*
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{ExecUowM, PrepareResult, UnitOfWork, UnitOfWorkOp, UnitOfWorkTermination}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class ContinuationRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ContinuationRuntime" should {
    "publish a suspended continuation only after the originating UnitOfWork commits" in {
      Given("a continuation staged in an uncommitted originating UnitOfWork")
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-1", "result.v1")
      val origin = _uow("origin")

      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      runtime.stageSuspensionC(origin, continuation).isFaillure shouldBe true
      runtime.claimC(continuation.continuationId).isFaillure shouldBe true
      runtime.recoverClaimC(continuation.continuationId).isFaillure shouldBe true

      When("the originating UnitOfWork commits")
      origin.commit().isSuccess shouldBe true

      Then("the continuation becomes claimable")
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      runtime.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)
    }

    "clear an in-memory staged identity after the originating UnitOfWork aborts" in {
      Given("a staged continuation whose first UnitOfWork does not commit")
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-aborted-stage", "result.v1")
      val first = _uow("origin-aborted-stage")
      runtime.stageSuspensionC(first, continuation).isSuccess shouldBe true

      When("the first UnitOfWork aborts and a fresh one stages the same identity")
      first.abort().isSuccess shouldBe true
      val retry = _uow("origin-aborted-stage-retry")
      val staged = runtime.stageSuspensionC(retry, continuation)

      Then("the aborted stage remains unclaimable and the retry can publish after commit")
      staged.isSuccess shouldBe true
      runtime.claimC(continuation.continuationId).isFaillure shouldBe true
      retry.commit().isSuccess shouldBe true
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
    }

    "clear a persistent staged identity after the originating UnitOfWork aborts" in {
      Given("a persistent runtime with a staged but uncommitted continuation")
      val persistence = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-persistent-aborted-stage", "result.v1")
      val first = _uow("origin-persistent-aborted-stage")
      runtime.stageSuspensionC(first, continuation).isSuccess shouldBe true

      When("the first UnitOfWork aborts and a fresh one stages the same identity")
      first.abort().isSuccess shouldBe true
      val retry = _uow("origin-persistent-aborted-stage-retry")
      val staged = runtime.stageSuspensionC(retry, continuation)

      Then("nothing was persisted before commit and the retry becomes claimable afterward")
      staged.isSuccess shouldBe true
      persistence.loadC(continuation.continuationId).toOption.flatten shouldBe None
      retry.commit().isSuccess shouldBe true
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
    }

    "resume exactly once through a fresh UnitOfWork after an admitted claim" in {
      Given("a committed and claimed continuation")
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-2", "result.v1")
      val origin = _uow("origin")
      runtime.stageSuspensionC(origin, continuation)
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      var freshCreated = 0

      When("the runtime resumes it with its required result type")
      val resumed = runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => { freshCreated += 1; _uow("resume") }
      )

      Then("it creates one fresh UnitOfWork and cannot be resumed a second time")
      resumed.isSuccess shouldBe true
      freshCreated shouldBe 1
      runtime.isCompleted(continuation.continuationId) shouldBe true
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => _uow("resume-again")
      ).isFaillure shouldBe true
    }

    "interpret a closing Action program before completing an in-memory continuation" in {
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-closing-action", "result.v1")
      val origin = _uow("closing-origin")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("closing-result", "1")
      )
      val firstClaim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("first claim must succeed"))
      var evaluated = 0
      val failed = runtime.resumeWithProgramC(firstClaim, result, () => _uow("closing-failed"),
        _closing_program(ActionExecution.Failed(StateMachineOperationFailure("closing-rejected", "rejected", Vector.empty))) {
          evaluated += 1
        }
      )
      failed shouldBe a[Consequence.Failure[_]]
      evaluated shouldBe 1
      runtime.isCompleted(continuation.continuationId) shouldBe false
      val retryClaim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("failed action must release claim"))
      val completed = runtime.resumeWithProgramC(retryClaim, result, () => _uow("closing-success"),
        _closing_program(ActionExecution.Completed(result)) { evaluated += 1 }
      )
      completed.isSuccess shouldBe true
      evaluated shouldBe 2
      runtime.isCompleted(continuation.continuationId) shouldBe true
      runtime.resumeWithProgramC(retryClaim, result, () => _uow("closing-duplicate"),
        _closing_program(ActionExecution.Completed(result)) { evaluated += 1 }
      ) shouldBe a[Consequence.Failure[_]]
      evaluated shouldBe 2
    }

    "release a persistent claim when a closing Action program rejects before commit" in {
      val persistence = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-persistent-closing-action", "result.v1")
      val origin = _uow("persistent-closing-origin")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("persistent-closing-result", "1")
      )
      val firstClaim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("first claim must succeed"))
      var evaluated = 0
      runtime.resumeWithProgramC(firstClaim, result, () => _uow("persistent-closing-failed"),
        _closing_program(ActionExecution.Suspended(continuation)) { evaluated += 1 }
      ) shouldBe a[Consequence.Failure[_]]
      evaluated shouldBe 1
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Available)
      val recovered = new PersistentContinuationRuntime(persistence)
      val retryClaim = recovered.claimC(continuation.continuationId).toOption.getOrElse(fail("claim must be released"))
      recovered.resumeWithProgramC(retryClaim, result, () => _uow("persistent-closing-success"),
        _closing_program(ActionExecution.Completed(result)) { evaluated += 1 }
      ).isSuccess shouldBe true
      evaluated shouldBe 2
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Completed)
    }

    "retain claim ownership when closing Action abort cleanup is uncertain" in {
      val persistence = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-closing-abort-fault", "result.v1")
      val origin = _uow("closing-abort-fault-origin")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim must succeed"))
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("closing-abort-fault-result", "1")
      )
      val failed = runtime.resumeWithProgramC(claim, result, () => _abort_failing_uow("closing-abort-fault"),
        _closing_program(ActionExecution.Failed(StateMachineOperationFailure("closing-rejected", "rejected", Vector.empty))) {
          ()
        }
      )
      failed shouldBe a[Consequence.Failure[_]]
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Claimed)
      runtime.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]
    }

    "reject an incompatible typed result without consuming the continuation" in {
      Given("a committed and claimed continuation with a required result type")
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-3", "result.v1")
      val origin = _uow("origin")
      runtime.stageSuspensionC(origin, continuation)
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))

      When("a result with a different type is supplied")
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("wrong.v1"), ContextReference("result:1", "1")),
        () => _uow("resume")
      ).isFaillure shouldBe true
      Then("the continuation returns to availability without completing")
      runtime.isAvailable(continuation.continuationId) shouldBe true
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
      runtime.isCompleted(continuation.continuationId) shouldBe false
    }

    "recover pending work through a recreated persistent runtime and complete it once" in {
      Given("a continuation persisted by an earlier runtime instance")
      val persistence = new ContinuationRuntimePersistence.InMemory
      val continuation = _continuation("continuation-restart", "result.v1")
      val origin = _uow("origin-restart")
      val first = new PersistentContinuationRuntime(persistence)

      first.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true

      When("a recreated runtime claims and resumes the continuation")
      val recovered = new PersistentContinuationRuntime(persistence)
      recovered.recoverClaimC(continuation.continuationId).isFaillure shouldBe true
      val firstClaim = recovered.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      val resumedRuntime = new PersistentContinuationRuntime(persistence)
      resumedRuntime.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(firstClaim)
      recovered.resumeC(
        firstClaim,
        StateMachineOperationResult(StateMachineResultTypeReference("wrong.v1"), ContextReference("result:restart", "1")),
        () => _uow("resume-restart-wrong")
      ).isFaillure shouldBe true
      val secondClaim = recovered.claimC(continuation.continuationId).toOption.getOrElse(fail("released claim should succeed"))
      recovered.resumeC(
        secondClaim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:restart", "1")),
        () => _uow("resume-restart")
      ).isSuccess shouldBe true

      Then("the shared persistence marks it completed and rejects another claim")
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Completed)
      new PersistentContinuationRuntime(persistence).claimC(continuation.continuationId).isFaillure shouldBe true
      new PersistentContinuationRuntime(persistence).recoverClaimC(continuation.continuationId).isFaillure shouldBe true
    }

    "retain claim ownership and completion across reopened local persistence adapters" in {
      Given("a committed Continuation in a caller-owned local directory")
      val root = Files.createTempDirectory(Files.createDirectories(Paths.get("target")), "continuation-local-")
      val continuation = _continuation("continuation-local-reopen", "result.v1")
      val first = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.LocalJson(root))
      val origin = _uow("origin-local-reopen")
      first.stageSuspensionC(origin, continuation) shouldBe Consequence.unit
      origin.commit() shouldBe Consequence.unit

      When("a new adapter claims and another runtime recovers the same private claim")
      val claimed = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.LocalJson(root))
      val claim = claimed.claimC(continuation.continuationId).toOption.getOrElse(fail("local claim should succeed"))
      val resumed = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.LocalJson(root))
      resumed.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)
      resumed.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("local-result", "1")),
        () => _uow("resume-local-reopen")
      ).isSuccess shouldBe true

      Then("another reopened adapter observes completion and cannot reissue work")
      val completed = new ContinuationRuntimePersistence.LocalJson(root)
      completed.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Completed)
      new PersistentContinuationRuntime(completed).claimC(continuation.continuationId).isFaillure shouldBe true
      new PersistentContinuationRuntime(completed).recoverClaimC(continuation.continuationId).isFaillure shouldBe true
    }

    "reject a mismatched persistent claim and incomplete result before creating a resume UnitOfWork" in {
      val persistence = new ContinuationRuntimePersistence.InMemory
      val continuation = _continuation("continuation-resume-admission", "result.v1")
      val runtime = new PersistentContinuationRuntime(persistence)
      val origin = _uow("origin-resume-admission")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = new PersistentContinuationRuntime(persistence).claimC(continuation.continuationId)
        .toOption.getOrElse(fail("claim should succeed"))
      var freshCreated = 0
      def fresh(): UnitOfWork = {
        freshCreated += 1
        _uow("resume-admission")
      }
      runtime.resumeC(
        claim.copy(continuation = continuation.copy(continuationId = null)),
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => fresh()
      ).isFaillure shouldBe true
      runtime.finalizeC(claim.copy(claimId = "")).isFaillure shouldBe true
      val foreign = claim.copy(continuation = continuation.copy(expectedRevision = StateMachineRevision("2")))
      runtime.resumeC(
        foreign,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => fresh()
      ).isFaillure shouldBe true
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Claimed)
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("", "1")),
        () => fresh()
      ).isFaillure shouldBe true
      freshCreated shouldBe 0
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Available)
      val retry = new PersistentContinuationRuntime(persistence).claimC(continuation.continuationId)
        .toOption.getOrElse(fail("released claim should be retriable"))
      runtime.resumeC(
        retry,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => fresh()
      ).isSuccess shouldBe true
      freshCreated shouldBe 1
    }

    "reject an incomplete in-memory result without consuming the continuation" in {
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-inmemory-admission", "result.v1")
      val origin = _uow("origin-inmemory-admission")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      var freshCreated = 0
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "")),
        () => { freshCreated += 1; _uow("resume-inmemory-admission") }
      ).isFaillure shouldBe true
      runtime.resumeC(
        claim.copy(continuation = continuation.copy(continuationId = null)),
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => { freshCreated += 1; _uow("resume-inmemory-missing-claim") }
      ).isFaillure shouldBe true
      freshCreated shouldBe 0
      runtime.isCompleted(continuation.continuationId) shouldBe false
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
    }

    "report a failed post-commit continuation write without claiming rollback" in {
      Given("a persistence adapter that rejects continuation creation before writing")
      val backing = new ContinuationRuntimePersistence.InMemory
      val persistence = new FailFirstCreationPersistence(backing)
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-create-fault", "result.v1")
      val origin = _uow("origin-create-fault")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true

      When("the UnitOfWork commits but the post-commit write fails")
      val result = origin.commit()

      Then("the committed outcome remains visible and no stored continuation can be claimed")
      result.isFaillure shouldBe true
      origin.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      backing.loadC(continuation.continuationId).toOption.flatten shouldBe None
      runtime.claimC(continuation.continuationId).isFaillure shouldBe true
    }

    "retain an indeterminate post-commit write as a loose-boundary limitation" in {
      Given("a persistence adapter that writes before reporting failure")
      val backing = new ContinuationRuntimePersistence.InMemory
      val persistence = new WriteThenFailCreationPersistence(backing)
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-indeterminate", "result.v1")
      val origin = _uow("origin-indeterminate")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true

      When("the post-commit callback receives an indeterminate write result")
      val result = origin.commit()

      Then("the UnitOfWork remains committed and storage must be reconciled")
      result.isFaillure shouldBe true
      origin.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      backing.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Available)
    }

    "reject a stale claim token and release a claim when its fresh UnitOfWork cannot be created" in {
      Given("a claimed continuation")
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-claim-token", "result.v1")
      val origin = _uow("origin-claim-token")
      runtime.stageSuspensionC(origin, continuation)
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))

      When("a stale token or an unavailable fresh UnitOfWork is used")
      runtime.resumeC(
        claim.copy(claimId = "stale"),
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:stale", "1")),
        () => _uow("resume-stale")
      ).isFaillure shouldBe true
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:null", "1")),
        () => null
      ).isFaillure shouldBe true

      Then("the original claim cannot be used and the released continuation can be claimed again")
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
    }

    "finalize a committed continuation without replaying work after a completion persistence fault" in {
      Given("a persistence adapter that fails its first completion write")
      val persistence = new FailFirstCompletionPersistence(new ContinuationRuntimePersistence.InMemory)
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-finalize", "result.v1")
      val origin = _uow("origin-finalize")
      runtime.stageSuspensionC(origin, continuation)
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))

      When("the fresh UnitOfWork commits but its completion callback fails")
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:finalize", "1")),
        () => _uow("resume-finalize")
      ).isFaillure shouldBe true

      Then("the owner can finalize the claim without resuming the work again")
      runtime.finalizeC(claim).isSuccess shouldBe true
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Completed)
      runtime.resumeC(
        claim,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:finalize", "1")),
        () => _uow("resume-finalize-again")
      ).isFaillure shouldBe true
    }

    "release a persistent claim after a fresh UnitOfWork aborts before commit" in {
      Given("a committed continuation claimed from persistent storage")
      val persistence = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-aborted-resume", "result.v1")
      val origin = _uow("origin-aborted-resume")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("result:aborted", "1")
      )
      val rejected = _rejected_uow("resume-aborted")

      When("the fresh UnitOfWork rejects prepare")
      val aborted = runtime.resumeC(claim, result, () => rejected)

      Then("the failed commit releases its claim")
      aborted.isFaillure shouldBe true
      rejected.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Aborted)
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Available)

      When("a later runtime retries the available continuation")
      val recovered = new PersistentContinuationRuntime(persistence)
      val retryclaim = recovered.claimC(continuation.continuationId).toOption.getOrElse(fail("retry claim should succeed"))
      val retried = recovered.resumeC(retryclaim, result, () => _uow("resume-retry"))

      Then("it completes once and cannot be claimed again")
      retried.isSuccess shouldBe true
      new PersistentContinuationRuntime(persistence).claimC(continuation.continuationId).isFaillure shouldBe true
    }

    "retain both failures when an aborted resume cannot release its claim" in {
      Given("a claimed continuation whose persistence rejects claim release")
      val backing = new ContinuationRuntimePersistence.InMemory
      val persistence = new FailReleasePersistence(backing)
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-release-fault", "result.v1")
      val origin = _uow("origin-release-fault")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("result:release-fault", "1")
      )

      When("the fresh UnitOfWork aborts and the release write also fails")
      val failed = runtime.resumeC(claim, result, () => _rejected_uow("resume-release-fault"))

      Then("both failures are reported and the unresolved claim remains fenced")
      val failures = failed match {
        case Consequence.Failure(conclusion) => conclusion.causes.map(_.display)
        case _ => fail("resume should fail")
      }
      failures should contain ("planned resume prepare rejection")
      failures should contain ("simulated claim release fault")
      failed.display should include ("planned resume prepare rejection")
      backing.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Claimed)
      runtime.claimC(continuation.continuationId).isFaillure shouldBe true
    }

    "release a persistent claim when the fresh UnitOfWork factory throws" in {
      Given("a committed and claimed continuation")
      val persistence = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation("continuation-factory-fault", "result.v1")
      val origin = _uow("origin-factory-fault")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("result:factory-fault", "1")
      )

      When("the factory throws before creating a fresh UnitOfWork")
      val failed = runtime.resumeC(claim, result, () => throw new IllegalStateException("planned factory fault"))

      Then("the original error is reported and the claim becomes available")
      failed.display should include ("planned factory fault")
      persistence.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Available)

      When("a later runtime retries the available continuation")
      val recovered = new PersistentContinuationRuntime(persistence)
      val retryclaim = recovered.claimC(continuation.continuationId).toOption.getOrElse(fail("retry claim should succeed"))
      val retried = recovered.resumeC(retryclaim, result, () => _uow("resume-factory-retry"))

      Then("the retry completes once and cannot be claimed again")
      retried.isSuccess shouldBe true
      recovered.claimC(continuation.continuationId).isFaillure shouldBe true
    }

    "preserve a factory failure when its claim release also fails" in {
      Given("a claimed continuation with failing claim-release persistence")
      val backing = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(new FailReleasePersistence(backing))
      val continuation = _continuation("continuation-factory-release-fault", "result.v1")
      val origin = _uow("origin-factory-release-fault")
      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      origin.commit().isSuccess shouldBe true
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("result.v1"), ContextReference("result:factory-release-fault", "1")
      )

      When("the factory and the attempted release both fail")
      val failed = runtime.resumeC(claim, result, () => throw new IllegalStateException("planned factory fault"))

      Then("the factory error remains authoritative and the unresolved claim stays fenced")
      val failures = failed match {
        case Consequence.Failure(conclusion) => conclusion.causes.map(_.display)
        case _ => fail("resume should fail")
      }
      failures should contain ("planned factory fault")
      failures should contain ("simulated claim release fault")
      failed.display should include ("planned factory fault")
      backing.loadC(continuation.continuationId).toOption.flatten.map(_.status) shouldBe
        Some(ContinuationRuntimePersistence.Status.Claimed)
      runtime.claimC(continuation.continuationId).isFaillure shouldBe true
    }
  }

  private def _continuation(id: String, resultType: String): Continuation = {
    val required = StateMachineRequiredOperation(
      StateMachineRequiredOperationIdentity("capability"), "action", StateMachineOperationIdentity("service", "operation"),
      None, Some(StateMachineResultTypeReference(resultType)),
      StateMachineRequiredOperationMetadata(
        ContextContract("context", Vector.empty, Vector.empty), CompletionContract("completion", Vector.empty),
        EvidenceContract("evidence", Vector.empty), Vector.empty
      )
    )
    Continuation(StateMachineRunIdentity("run"), ContinuationIdentity(id), StateMachineRevision("1"), required,
      ContextBundle("context", Vector.empty, Vector.empty, ContextSnapshot("1")))
  }

  private def _closing_program(outcome: ActionExecution)(evaluated: => Unit): ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](outcome).map { value =>
      evaluated
      value
    }

  private def _uow(name: String): UnitOfWork = {
    val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)
    given ExecutionContext = ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(clock),
      IdGenerationContext.deterministic(IdGenerationContext.IdNamespace("test", "continuation"), clock, name)
    )
    new UnitOfWork(summon[ExecutionContext], EventEngine.noop(DataStore.noop()))
  }

  private def _rejected_uow(name: String): UnitOfWork = {
    val context = _uow(name).executionContext
    val rejectingengine = Proxy.newProxyInstance(
      classOf[EventEngine].getClassLoader,
      Array(classOf[EventEngine]),
      new InvocationHandler {
        def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
          method.getName match {
            case "stage" => null
            case "prepare" => PrepareResult.Rejected("planned resume prepare rejection")
            case "abort" => null
            case other => throw new IllegalStateException(s"unexpected EventEngine method: $other")
          }
      }
    ).asInstanceOf[EventEngine]
    new UnitOfWork(context, rejectingengine)
  }

  private def _abort_failing_uow(name: String): UnitOfWork = {
    val context = _uow(name).executionContext
    val failingengine = Proxy.newProxyInstance(
      classOf[EventEngine].getClassLoader,
      Array(classOf[EventEngine]),
      new InvocationHandler {
        def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
          method.getName match {
            case "abort" => throw new IllegalStateException("planned closing Action abort fault")
            case other => throw new IllegalStateException(s"unexpected EventEngine method: $other")
          }
      }
    ).asInstanceOf[EventEngine]
    new UnitOfWork(context, failingengine)
  }

  private final class FailReleasePersistence(
    delegate: ContinuationRuntimePersistence
  ) extends ContinuationRuntimePersistence {
    def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] = delegate.createC(record)
    def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] = delegate.loadC(identity)
    def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] = delegate.claimC(identity)
    def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      Consequence.Failure(Conclusion.from(new IllegalStateException("simulated claim release fault")))
    def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      delegate.completeC(identity, claimId)
  }

  private final class FailFirstCreationPersistence(
    delegate: ContinuationRuntimePersistence
  ) extends ContinuationRuntimePersistence {
    def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
      Consequence.Failure(Conclusion.from(new IllegalStateException("simulated continuation creation fault")))
    def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] = delegate.loadC(identity)
    def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] = delegate.claimC(identity)
    def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      delegate.releaseC(identity, claimId)
    def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      delegate.completeC(identity, claimId)
  }

  private final class WriteThenFailCreationPersistence(
    delegate: ContinuationRuntimePersistence
  ) extends ContinuationRuntimePersistence {
    def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
      delegate.createC(record).flatMap(_ =>
        Consequence.Failure(Conclusion.from(new IllegalStateException("simulated indeterminate continuation write")))
      )
    def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] = delegate.loadC(identity)
    def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] = delegate.claimC(identity)
    def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      delegate.releaseC(identity, claimId)
    def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      delegate.completeC(identity, claimId)
  }

  private final class FailFirstCompletionPersistence(
    delegate: ContinuationRuntimePersistence
  ) extends ContinuationRuntimePersistence {
    private var _firstCompletion = true

    def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] = delegate.createC(record)
    def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] = delegate.loadC(identity)
    def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] = delegate.claimC(identity)
    def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      delegate.releaseC(identity, claimId)
    def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
      if (_firstCompletion) {
        _firstCompletion = false
        Consequence.Failure(Conclusion.from(new IllegalStateException("simulated completion persistence fault")))
      } else delegate.completeC(identity, claimId)
  }
}
