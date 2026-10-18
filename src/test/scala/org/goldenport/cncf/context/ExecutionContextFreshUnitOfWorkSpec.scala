package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.provider.{ProviderCall, ProviderEngine, ProviderRequest}
import org.goldenport.cncf.resource.{ResourceTreeAccess, ResourceTreeEntry, ResourceTreeLimits, ResourceTreeReference}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.cncf.job.{ActionId, JobCancellationScope, JobContext, JobDurableRuntimeFixtures, JobId, TaskId}
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination

/*
 * @since   Oct. 14, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionContextFreshUnitOfWorkSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ExecutionContext.withFreshUnitOfWorkContext" should {
    "bind a fresh interpreter and provider step while preserving the selected execution capabilities" in {
      Given("an instance with an explicit clock, execution profile/control, session, framework settings and admitted resource tree")
      val clock = Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC)
      val reference = ResourceTreeReference.parseC("fresh-uow").TAKE
      val entry = ResourceTreeEntry.createC("evidence.txt", Vector(1.toByte)).TAKE
      val trees = ResourceTreeAccess.inMemory(Map(reference -> Vector(entry)))
      val base = ExecutionContext.withResourceTreeAccess(ExecutionContext.create(clock), trees)
      val secured = ExecutionContext.withSecurityContext(base, base.security.copy(
        session = Some(SessionContext(sessionId = Some("fresh-uow-session")))
      )).asInstanceOf[ExecutionContext.Instance]
      val control = secured.executionControl.copy(
        profile = secured.executionControl.profile.copy(name = "fresh-uow-spec"),
        invocation = Some(ExecutionInvocationIdentity("fresh-uow-invocation", 7L, "provider.fresh-uow", true))
      )
      val source = secured.copy(cncfCore = secured.cncfCore.copy(
        executionControl = control,
        framework = secured.framework.copy(workingSetEnabled = false, traceJob = true)
      ))
      val originaluow = source.unitOfWork
      val originalinterpreter = source.unitOfWorkInterpreter

      When("the public helper rebinds the instance and a provider_step runs through the returned runtime")
      val fresh = ExecutionContext.withFreshUnitOfWorkContext(source).TAKE
      val call = new ContextProviderCall(ProviderCall.Core(
        ProviderRequest("fresh-uow", "observe"), fresh, None, None
      ))
      val observed = ProviderEngine.execute(call)
      val snapshot = fresh.resourceTrees.snapshot(reference, ResourceTreeLimits.default)

      Then("the provider observes the rebound UoW context and all selected capability, profile, security and framework values survive")
      fresh should not be theSameInstanceAs(source)
      fresh.unitOfWork should not be theSameInstanceAs(originaluow)
      fresh.unitOfWorkInterpreter should not be theSameInstanceAs(originalinterpreter)
      fresh.unitOfWork.executionContext should be theSameInstanceAs fresh
      observed.toOption.get should be theSameInstanceAs fresh
      snapshot.toOption.map(_.entries) shouldBe Some(Vector(entry))
      fresh.runtime.core should be theSameInstanceAs source.runtime.core
      fresh.runtime.context shouldBe source.runtime.context
      fresh.runtime.operationMode shouldBe source.runtime.operationMode
      fresh.unitOfWork.httpDriver should be theSameInstanceAs originaluow.httpDriver
      fresh.core should be theSameInstanceAs source.core
      fresh.clock should be theSameInstanceAs clock
      fresh.idGeneration should be theSameInstanceAs source.idGeneration
      fresh.executionControl shouldBe control
      fresh.security shouldBe source.security
      fresh.security.session.flatMap(_.sessionId) shouldBe Some("fresh-uow-session")
      fresh.framework shouldBe source.framework
      fresh.cncfCore.resources should be theSameInstanceAs source.cncfCore.resources
      fresh.cncfCore.resourceTrees should be theSameInstanceAs source.cncfCore.resourceTrees
    }

    "copy initial diagnostics into an independent public fresh UnitOfWork runtime" in {
      Given("a source runtime with explicit trace and execution diagnostics before a public fresh UnitOfWork binding")
      val source = ExecutionContext.create()
      source.runtime.noteExecutionDiagnostics(Some("source-trace"), Some("source-execution"), None)
      val initial = source.runtime.executionMetadata

      When("the public helper creates a fresh UnitOfWork context")
      val fresh = ExecutionContext.withFreshUnitOfWorkContext(source).TAKE

      Then("the new runtime inherits the initial diagnostics with its own self-bound UnitOfWork")
      fresh.runtime should not be theSameInstanceAs(source.runtime)
      fresh.unitOfWork.executionContext should be theSameInstanceAs fresh
      fresh.runtime.executionMetadata shouldBe initial

      When("the fresh runtime changes its diagnostic fields")
      fresh.runtime.noteExecutionDiagnostics(Some("fresh-trace"), Some("fresh-execution"), Some("fresh-failure"))
      val freshmetadata = fresh.runtime.executionMetadata

      Then("the source diagnostics retain their original values")
      source.runtime.executionMetadata shouldBe initial
      freshmetadata.traceId shouldBe Some("fresh-trace")
      freshmetadata.executionId shouldBe Some("fresh-execution")
      freshmetadata.failure shouldBe Some("fresh-failure")

      When("the source runtime independently changes its diagnostics")
      source.runtime.noteExecutionDiagnostics(Some("source-next-trace"), Some("source-next-execution"), Some("source-failure"))

      Then("the fresh diagnostics retain their own values and do not follow the source update")
      fresh.runtime.executionMetadata shouldBe freshmetadata
      source.runtime.executionMetadata.traceId shouldBe Some("source-next-trace")
      source.runtime.executionMetadata.executionId shouldBe Some("source-next-execution")
      source.runtime.executionMetadata.failure shouldBe Some("source-failure")
    }

    "reject an unsupported context implementation with the declared operation failure" in {
      Given("an execution context implementation outside the supported Instance")
      val base = ExecutionContext.create()
      val unsupported = new ExecutionContext {
        def core: org.goldenport.context.ExecutionContext.Core = base.core
        private[cncf] def cncfCore: ExecutionContext.CncfCore = base.cncfCore
        def withScope(parent: ScopeContext): ExecutionContext = this
      }

      When("a fresh Unit of Work binding is requested")
      val result = ExecutionContext.withFreshUnitOfWorkContext(unsupported)

      Then("the operation fails with the unsupported rebind reason")
      result.isSuccess shouldBe false
      result match {
        case Consequence.Failure(conclusion) => conclusion.display should include("execution-context-uow-rebind-unsupported")
        case _ => fail("Expected the unsupported-context operation failure")
      }
    }
  }

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

  private final class ContextProviderCall(val core: ProviderCall.Core) extends ProviderCall[ExecutionContext] {
    protected def build_Program: ExecUowM[ExecutionContext] =
      provider_step("provider:fresh-uow.observe") {
        Consequence.success(executionContext.unitOfWork.executionContext)
      }
  }

  private final class UnsupportedContext(base: ExecutionContext) extends ExecutionContext {
    override def core: org.goldenport.context.ExecutionContext.Core = base.core
    private[cncf] override def cncfCore: ExecutionContext.CncfCore = base.cncfCore
    override def withScope(parent: ScopeContext): ExecutionContext = base.withScope(parent)
  }
}
