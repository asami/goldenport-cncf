package org.goldenport.cncf.workflow

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.UnitOfWork
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

      When("the originating UnitOfWork commits")
      origin.commit().isSuccess shouldBe true

      Then("the continuation becomes claimable")
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
      val firstClaim = recovered.claimC(continuation.continuationId).toOption.getOrElse(fail("claim should succeed"))
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

  private def _uow(name: String): UnitOfWork = {
    val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)
    given ExecutionContext = ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(clock),
      IdGenerationContext.deterministic(IdGenerationContext.IdNamespace("test", "continuation"), clock, name)
    )
    new UnitOfWork(summon[ExecutionContext], EventEngine.noop(DataStore.noop()))
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
