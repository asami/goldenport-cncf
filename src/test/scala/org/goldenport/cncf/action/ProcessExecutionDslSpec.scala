package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 34 Process Execution UnitOfWork and
 * protected internal-DSL integration.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _exit_codes = Gen.choose(-16, 16)

  "Process Execution internal DSL" should {
    "construct equivalent UnitOfWork intent through direct and Free execution paths" in {
      Given("generated admitted executions and deterministic runtime driver results")

      When("a Behavior invokes Process Execution directly and through an ExecUowM program")
      val property = Prop.forAll(_exit_codes) { exitcode =>
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val result = _result(capability, exitcode)
        val fixture = ProcessExecutionTestProfile.admittedC(capability, result).toOption.get
        val context = _context(Some(fixture.profile.driver))
        val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
        val execution = fixture.execution
        val directuow = new UnitOfWork(context)
        val freeuow = new UnitOfWork(context)
        given UnitOfWork = directuow

        val direct = behavior.direct(execution)
        val free = new UnitOfWorkInterpreter(freeuow).run(behavior.free(execution))

        direct.toOption.contains(result) &&
        free.toOption.contains(result) &&
        fixture.profile.driver.executions == Vector(execution, execution)
      }

      Then("both paths reach the same admitted ProcessExec algebra case and driver intent")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property).passed shouldBe true
    }

    "refuse denied capability admission before a ProcessExec operation reaches the driver" in {
      Given("a registered capability with a deterministic driver but no matching grant")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val fixture = ProcessExecutionTestProfile.admittedC(capability, _result(capability, exitcode = 0)).toOption.get

      When("policy admission rejects a request before the protected DSL can receive resolved intent")
      val denied = fixture.policy.resolveC(
        ProcessExecutionRequest(capability),
        ProcessExecutionGrant(ProcessCapabilityId.parseC("other-cli").toOption.get)
      )

      Then("no Process Execution driver call occurs")
      denied.isFaillure shouldBe true
      fixture.profile.driver.executions shouldBe empty
    }

    "keep the UnitOfWork operation capability-resolved and free of raw process selection" in {
      Given("the public ProcessExec UnitOfWork operation type")
      val fields = classOf[UnitOfWorkOp.ProcessExec].getDeclaredFields.map(_.getName).toSet

      When("the operation payload is inspected")
      val forbidden = Set("request", "executable", "command", "shell", "environment")

      Then("only a resolved capability-bound intent crosses the UnitOfWork boundary")
      fields should contain ("execution")
      fields.intersect(forbidden) shouldBe empty
    }

    "return a structured failure when the canonical interpreter has no configured driver" in {
      Given("an admitted execution and a scope without a Process Execution driver")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val context = _context(None)
      val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
      val execution = ProcessExecutionTestProfile.admittedC(capability, _result(capability, exitcode = 0)).toOption.get.execution
      given UnitOfWork = new UnitOfWork(context)

      When("the direct DSL is interpreted")
      val result = behavior.direct(execution)

      Then("the interpreter fails without selecting a host process")
      result.isFaillure shouldBe true
    }

    "admit a codex-cli-style provider request through the scoped runtime service" in {
      Given("a provider Behavior with an installed runtime admission and deterministic driver")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val result = _result(capability, exitcode = 0)
      val fixture = ProcessExecutionTestProfile.admittedC(capability, result).toOption.get
      val context = _context(Some(fixture.profile.driver), Some(fixture.admission))
      val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
      val request = ProcessExecutionRequest(capability)
      given UnitOfWork = new UnitOfWork(context)

      When("the provider submits logical process intent through the protected DSL")
      val direct = behavior.directRequest(request)
      val free = new UnitOfWorkInterpreter(new UnitOfWork(context)).run(behavior.freeRequest(request))

      Then("the runtime admits the request before both paths enter the resolved-only UnitOfWork effect")
      direct.toOption shouldBe Some(result)
      free.toOption shouldBe Some(result)
      fixture.profile.driver.executions.map(_.request.capability) shouldBe Vector(capability, capability)
    }

    "reject a provider request before driver invocation when scoped admission is absent" in {
      Given("a provider Behavior with a configured driver but no runtime admission")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val result = _result(capability, exitcode = 0)
      val fixture = ProcessExecutionTestProfile.admittedC(capability, result).toOption.get
      val context = _context(Some(fixture.profile.driver))
      val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
      given UnitOfWork = new UnitOfWork(context)

      When("the provider submits a logical Process Execution request")
      val rejected = behavior.directRequest(ProcessExecutionRequest(capability))

      Then("the missing admission is structured and the driver remains untouched")
      rejected.isFaillure shouldBe true
      fixture.profile.driver.executions shouldBe empty
    }

    "record safe process result metrics without captured stdout content in the UnitOfWork calltree" in {
      Given("a calltree-enabled execution with a confidential captured stdout value")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val secret = "confidential-process-output"
      val capture = ProcessExecutionCapture(secret.getBytes("UTF-8").toVector, secret.length.toLong, truncated = false)
      val result = _result(capability, exitcode = 0).copy(stdout = capture)
      val fixture = ProcessExecutionTestProfile.admittedC(capability, result).toOption.get
      val context = _context(Some(fixture.profile.driver), calltreeenabled = true)
      val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
      given UnitOfWork = new UnitOfWork(context)

      When("the protected Process Execution DSL completes")
      val completed = behavior.direct(fixture.execution)
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")

      Then("the calltree retains structural execution metadata but not captured bytes")
      completed.toOption shouldBe Some(result)
      rendered should include ("uow:process-exec")
      rendered should include ("process.stdout_bytes")
      rendered should not include secret
    }

    "project structural Process Execution metrics without captured stdout content" in {
      Given("a deterministic Process Execution result containing confidential output")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val secret = "confidential-process-metric-output"
      val capture = ProcessExecutionCapture(secret.getBytes("UTF-8").toVector, secret.length.toLong, truncated = false)
      val result = _result(capability, exitcode = 0).copy(stdout = capture)
      val fixture = ProcessExecutionTestProfile.admittedC(capability, result).toOption.get
      val context = _context(Some(fixture.profile.driver))
      val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
      val before = RuntimeDashboardMetrics.processExecutionSnapshot.summary.cumulative.total
      given UnitOfWork = new UnitOfWork(context)

      When("the protected Process Execution DSL completes")
      behavior.direct(fixture.execution).toOption shouldBe Some(result)
      val snapshot = RuntimeDashboardMetrics.processExecutionSnapshot

      Then("the metrics include only structural execution accounting")
      snapshot.summary.cumulative.total should be >= (before + 1L)
      RuntimeDashboardMetrics.processExecutionDiagnosticRecords.values.map(_.print).mkString should not include secret
    }

    "omit an unsafe driver failure display from the UnitOfWork calltree" in {
      Given("a calltree-enabled execution with a driver that returns a confidential failure message")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val secret = "confidential-driver-failure"
      val fixture = ProcessExecutionTestProfile.admittedC(capability, _result(capability, exitcode = 0)).toOption.get
      val failingdriver = new ProcessExecutionDriver {
        val safeIdentity: String = "failing-test-driver"
        def startC(execution: ResolvedProcessExecution) =
          Consequence.serviceUnavailable(secret)
      }
      val context = _context(Some(failingdriver), calltreeenabled = true)
      val behavior = new _ProcessExecutionBehavior(Behavior.Core(context, None, None))
      val before = RuntimeDashboardMetrics.processExecutionSnapshot.summary.cumulative.total
      given UnitOfWork = new UnitOfWork(context)

      When("the protected Process Execution DSL receives the driver failure")
      val failed = behavior.direct(fixture.execution)
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")

      Then("the original structured failure is preserved without copying its display into calltree output")
      failed.isFaillure shouldBe true
      rendered should include ("uow:process-exec")
      rendered should not include secret
      RuntimeDashboardMetrics.processExecutionSnapshot.summary.cumulative.total should be >= (before + 1L)
      RuntimeDashboardMetrics.processExecutionDiagnosticRecords.values.map(_.print).mkString should not include secret
    }
  }

  private def _context(
    driver: Option[ProcessExecutionDriver],
    admission: Option[ProcessExecutionAdmission] = None,
    calltreeenabled: Boolean = false
  ): ExecutionContext = {
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    val base = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), calltreeenabled)
    val scope = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "process-execution-dsl-test",
      parent = None,
      observabilityContext = base.observability,
      processExecutionDriverOption = driver,
      processExecutionAdmissionOption = admission
    )
    base.withScope(scope)
  }

  private def _result(
    capability: ProcessCapabilityId,
    exitcode: Int
  ): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    ProcessExecutionResult(
      ProcessExecutionTermination.Exited(exitcode),
      capture,
      capture,
      Vector.empty,
      elapsedMillis = 1L,
      safeProgramIdentity = capability.print
    )
  }

}

private final class _ProcessExecutionBehavior(
  val behaviorCore: Behavior.Core
) extends Behavior {
  def free(execution: ResolvedProcessExecution) =
    process_exec(execution)

  def freeRequest(request: ProcessExecutionRequest) =
    process_exec(request)

  def direct(
    execution: ResolvedProcessExecution
  )(using UnitOfWork): Consequence[ProcessExecutionResult] =
    process_exec_c(execution)

  def directRequest(
    request: ProcessExecutionRequest
  )(using UnitOfWork): Consequence[ProcessExecutionResult] =
    process_exec_c(request)
}
