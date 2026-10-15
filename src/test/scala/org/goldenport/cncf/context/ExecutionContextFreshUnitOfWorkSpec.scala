package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.job.{ActionId, JobCancellationScope, JobContext, JobDurableRuntimeFixtures, JobId, TaskId}
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionContextFreshUnitOfWorkSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Public fresh UnitOfWork context" should {
    "preserve the existing execution carrier" which {
      "FUC01 self-binds one fresh UnitOfWork while retaining the execution profile and facilities" in {
        Given("an Instance with explicit clock, security, deterministic IDs, invocation key and cancellable Job context")
        val instant = Instant.parse("2026-10-14T00:00:00Z")
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        val secured = ExecutionContext.withSecurityContext(ExecutionContext.create(clock),
          ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager).security)
        val ids = IdGenerationContext.deterministic(IdGenerationContext.IdNamespace("cncf", "fresh"), clock, "fresh-uow")
        val identified = ExecutionContext.withIdGenerationContext(secured, ids)
        val invoked = ExecutionContext.withExplicitExecutionInvocationKey(identified, "fresh-uow-invocation")
        val cancellation = new JobCancellationScope
        val job = JobContext(
          Some(JobId("cncf", "fresh", Some(instant), Some("job"))),
          Some(TaskId("cncf", "fresh", Some(instant), Some("task"))),
          Some(ActionId("cncf", "fresh", Some(instant), Some("action"))),
          traceMetadata = Map("source" -> "fresh-uow"), cancellationScope = Some(cancellation))
        val original = ExecutionContext.withJobContext(invoked, job)
        val originalunitofwork = original.unitOfWork

        When("the public fresh UnitOfWork transform is applied")
        val rebound = JobDurableRuntimeFixtures.success(ExecutionContext.withFreshUnitOfWorkContext(original))
        val fresh = rebound.unitOfWork
        try {
          Then("the fresh UnitOfWork is stable, distinct and self-bound to the returned context")
          rebound should not be theSameInstanceAs(original)
          fresh should not be theSameInstanceAs(originalunitofwork)
          rebound.unitOfWork should be theSameInstanceAs fresh
          fresh.executionContext should be theSameInstanceAs rebound
          rebound.unitOfWorkInterpreter should not be theSameInstanceAs(original.unitOfWorkInterpreter)

          And("core, security, profile, invocation, cancellation and runtime facilities retain their original values")
          rebound.core should be theSameInstanceAs original.core
          rebound.clock should be theSameInstanceAs clock
          rebound.random should be theSameInstanceAs original.random
          rebound.entropy should be theSameInstanceAs original.entropy
          rebound.security should be theSameInstanceAs original.security
          rebound.idGeneration should be theSameInstanceAs ids
          rebound.executionControl should be theSameInstanceAs original.executionControl
          rebound.jobContext should be theSameInstanceAs job
          rebound.jobContext.cancellationScope.get should be theSameInstanceAs cancellation
          rebound.framework shouldBe original.framework
          rebound.framework.executionInvocationKey shouldBe Some("fresh-uow-invocation")
          rebound.observability should be theSameInstanceAs original.observability
          rebound.cncfCore.resources should be theSameInstanceAs original.cncfCore.resources
          rebound.cncfCore.resourceTrees should be theSameInstanceAs original.cncfCore.resourceTrees
          rebound.operationEvaluation should be theSameInstanceAs original.operationEvaluation
          rebound.tagSpaces shouldBe original.tagSpaces
          rebound.runtime.core should be theSameInstanceAs original.runtime.core
          rebound.runtime.toToken shouldBe original.runtime.toToken
          rebound.runtime.context shouldBe original.runtime.context
          rebound.runtime.operationMode shouldBe original.runtime.operationMode
          rebound.runtime.transitionValidationHook should be theSameInstanceAs original.runtime.transitionValidationHook
          rebound.runtime.entityCreateDefaultsPolicy shouldBe original.runtime.entityCreateDefaultsPolicy
          rebound.dataStoreSpace should be theSameInstanceAs original.dataStoreSpace
          rebound.entityStoreSpace should be theSameInstanceAs original.entityStoreSpace
          rebound.entitySpace should be theSameInstanceAs original.entitySpace
        } finally {
          JobDurableRuntimeFixtures.success(fresh.dispose())
          JobDurableRuntimeFixtures.success(originalunitofwork.dispose())
        }
      }

      "FUC02 commits fresh callbacks independently of the open original UnitOfWork" in {
        Given("an open self-bound original UnitOfWork with a post-commit observation")
        val original = JobDurableRuntimeFixtures.success(
          ExecutionContext.withFreshUnitOfWorkContext(ExecutionContext.create()))
        val originalunitofwork = original.unitOfWork
        val originalcalls = new AtomicInteger
        originalunitofwork.stagePostCommitC {
          originalcalls.incrementAndGet()
          Consequence.unit
        }
        val freshcalls = new AtomicInteger

        When("a fresh context stages its own callback and commits through the preserved runtime action")
        val rebound = JobDurableRuntimeFixtures.success(ExecutionContext.withFreshUnitOfWorkContext(original))
        val fresh = rebound.unitOfWork
        try {
          fresh.stagePostCommitC {
            freshcalls.incrementAndGet()
            Consequence.unit
          }
          val committed = rebound.runtime.commitC()

          Then("only the fresh transaction commits and invokes its callback")
          committed.isSuccess shouldBe true
          freshcalls.get() shouldBe 1
          originalcalls.get() shouldBe 0
          fresh.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
          originalunitofwork.lastCommitTermination shouldBe None

          When("the original runtime independently aborts its still-open UnitOfWork")
          val aborted = original.runtime.abortC()

          Then("the original callback remains uncalled and the fresh committed result remains intact")
          aborted.isSuccess shouldBe true
          originalcalls.get() shouldBe 0
          freshcalls.get() shouldBe 1
          fresh.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
          originalunitofwork.lastCommitTermination shouldBe None
        } finally {
          JobDurableRuntimeFixtures.success(fresh.dispose())
          JobDurableRuntimeFixtures.success(originalunitofwork.dispose())
        }
      }

      "FUC03 refuses null and unsupported context implementations without a UnitOfWork effect" in {
        Given("a base Instance and a small unsupported context that delegates its abstract carrier members")
        val base = ExecutionContext.create()
        val unitofwork = base.unitOfWork
        val calls = new AtomicInteger
        unitofwork.stagePostCommitC {
          calls.incrementAndGet()
          Consequence.unit
        }
        val unsupported = new UnsupportedContext(base)
        try {
          Vector(null, unsupported).foreach { selected =>
            When("the public transform receives an unsupported carrier")
            val refused = ExecutionContext.withFreshUnitOfWorkContext(selected)

            Then("it returns structured operationInvalid and leaves the existing UnitOfWork untouched")
            val conclusion = JobDurableRuntimeFixtures.failureConclusion(refused)
            conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.operationInvalid
            conclusion.observation.cause.descriptor.facets should contain(
              org.goldenport.observation.Descriptor.Facet.Operation("execution-context-uow-rebind-unsupported"))
            base.unitOfWork should be theSameInstanceAs unitofwork
            unitofwork.lastCommitTermination shouldBe None
            calls.get() shouldBe 0
          }
        } finally JobDurableRuntimeFixtures.success(unitofwork.dispose())
      }
    }
  }

  private final class UnsupportedContext(base: ExecutionContext) extends ExecutionContext {
    override def core: org.goldenport.context.ExecutionContext.Core = base.core
    private[cncf] override def cncfCore: ExecutionContext.CncfCore = base.cncfCore
    override def withScope(parent: ScopeContext): ExecutionContext = base.withScope(parent)
  }
}
