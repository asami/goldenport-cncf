package org.goldenport.cncf.workflow

import java.time.{Clock, Instant, ZoneOffset}
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
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-1", "result.v1")
      val origin = _uow("origin")

      runtime.stageSuspensionC(origin, continuation).isSuccess shouldBe true
      runtime.stageSuspensionC(origin, continuation).isFaillure shouldBe true
      runtime.claimC(continuation.continuationId).isFaillure shouldBe true

      origin.commit().isSuccess shouldBe true
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
    }

    "resume exactly once through a fresh UnitOfWork after an admitted claim" in {
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-2", "result.v1")
      val origin = _uow("origin")
      runtime.stageSuspensionC(origin, continuation)
      origin.commit().isSuccess shouldBe true
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true
      var freshCreated = 0

      val resumed = runtime.resumeC(
        continuation.continuationId,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => { freshCreated += 1; _uow("resume") }
      )

      resumed.isSuccess shouldBe true
      freshCreated shouldBe 1
      runtime.isCompleted(continuation.continuationId) shouldBe true
      runtime.resumeC(
        continuation.continuationId,
        StateMachineOperationResult(StateMachineResultTypeReference("result.v1"), ContextReference("result:1", "1")),
        () => _uow("resume-again")
      ).isFaillure shouldBe true
    }

    "reject an incompatible typed result without consuming the continuation" in {
      val runtime = new ContinuationRuntime.InMemory
      val continuation = _continuation("continuation-3", "result.v1")
      val origin = _uow("origin")
      runtime.stageSuspensionC(origin, continuation)
      origin.commit().isSuccess shouldBe true
      runtime.claimC(continuation.continuationId).isSuccess shouldBe true

      runtime.resumeC(
        continuation.continuationId,
        StateMachineOperationResult(StateMachineResultTypeReference("wrong.v1"), ContextReference("result:1", "1")),
        () => _uow("resume")
      ).isFaillure shouldBe true
      runtime.isAvailable(continuation.continuationId) shouldBe true
      runtime.isCompleted(continuation.continuationId) shouldBe false
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
}
