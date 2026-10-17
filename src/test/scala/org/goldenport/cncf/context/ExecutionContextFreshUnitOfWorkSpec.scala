package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.provider.{ProviderCall, ProviderEngine, ProviderRequest}
import org.goldenport.cncf.resource.{ResourceTreeAccess, ResourceTreeEntry, ResourceTreeLimits, ResourceTreeReference}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
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

  private final class ContextProviderCall(val core: ProviderCall.Core) extends ProviderCall[ExecutionContext] {
    protected def build_Program: ExecUowM[ExecutionContext] =
      provider_step("provider:fresh-uow.observe") {
        Consequence.success(executionContext.unitOfWork.executionContext)
      }
  }
}
