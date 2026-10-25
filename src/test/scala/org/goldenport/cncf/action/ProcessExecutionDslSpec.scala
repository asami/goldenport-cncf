package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramRecorder, UnitOfWorkProgramStubProvider}
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
 *  version Jul. 30, 2026
 * @version Oct. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _exit_codes = Gen.choose(-16, 16)

  "E7 Host-source DSL routing" must afterWord("in spec:host-source-read, example:E7, rules:R3,R4,R5, phase:6.4") {
    "route both typed entry points through one resolved ProcessExec per call and preserve every terminal mapping" in {
      Given("docs/spec/host-source-read.md R3 R4 R5 E7, a component grant and actual deterministic terminal receipts")
      val base = HostSourceReadSpecSupport.result()
      val receipts = Vector(base) ++ HostSourceReadSpecSupport.failedTerminations.map(t => base.copy(termination = t)) ++ Vector(
        base.copy(termination = ProcessExecutionTermination.TerminationUnconfirmed),
        base.copy(stdout = base.stdout.copy(truncated = true)),
        base.copy(stderr = base.stderr.copy(byteCount = 1)),
        base.copy(stdout = ProcessExecutionCapture(Vector(0xff.toByte), 1, false)))
      When("the direct and Free typed host-source APIs execute in their ordinary UnitOfWork")
      val results = receipts.map { receipt =>
        val profile = ProcessExecutionTestProfile(Map(HostSourceReadSpecSupport.capability -> receipt))
        val context = HostSourceReadSpecSupport.contextFor(HostSourceReadSpecSupport.definition(), receipt, driver = Some(profile.driver))
        val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
        val direct = behavior.directSource(HostSourceReadSpecSupport.target, HostSourceReadSpecSupport.payload)(using new UnitOfWork(context))
        val free = new UnitOfWorkInterpreter(new UnitOfWork(context)).run(
          behavior.freeSource(HostSourceReadSpecSupport.target, HostSourceReadSpecSupport.payload))
        (receipt, direct, free, profile.driver.executions,
          context.observability.callTreeContext.build().map(_.toRecord.print).getOrElse(""))
      }
      Then("each call uses the real ProcessExec interpreter exactly once, without retry or success forgery")
      results.foreach { case (receipt, direct, free, executions, rendered) =>
        val expected = HostSourceRead.decodeC(receipt)
        direct.toOption shouldBe expected.toOption
        free.toOption shouldBe expected.toOption
        direct.isFaillure shouldBe expected.isFaillure
        free.isFaillure shouldBe expected.isFaillure
        executions should have size 2
        executions.foreach { execution =>
          execution.request.arguments shouldBe empty
          execution.request.input shouldBe ProcessExecutionInput.Bytes(HostSourceReadSpecSupport.payload.bytes)
          execution.definition.safeProgramIdentity shouldBe HostSourceReadSpecSupport.capability.print
        }
        rendered should include ("uow:process-exec")
      }
    }

    "record only the existing resolved ProcessExec algebra operation" in {
      Given("docs/spec/host-source-read.md R5 E7, an admitted component source call and a typed terminal stub")
      val receipt = HostSourceReadSpecSupport.result()
      val context = HostSourceReadSpecSupport.contextFor(HostSourceReadSpecSupport.definition(), receipt)
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.ProcessExec(_) => Consequence.success(receipt).asInstanceOf[Consequence[A]]
          case _ => Consequence.serviceUnavailable("Unexpected host-source algebra operation")
        }
      }
      When("the typed Free program is recorded")
      val recording = UnitOfWorkProgramRecorder.record(
        behavior.freeSource(HostSourceReadSpecSupport.target, HostSourceReadSpecSupport.payload), provider)
      Then("one resolved ProcessExec carries the payload and no new operation or raw selection crosses the algebra")
      recording.result.toOption shouldBe Some(HostSourceReadResult.Complete(receipt.stdout.content))
      recording.occurrences should have size 1
      recording.occurrences.head.operation match {
        case UnitOfWorkOp.ProcessExec(execution) =>
          execution.request.capability shouldBe HostSourceReadSpecSupport.capability
          execution.request.arguments shouldBe empty
        case other => fail(s"Expected ProcessExec, received $other")
      }
    }
  }

  "E8 Host-source DSL refusal" must afterWord("in spec:host-source-read, example:E8, rules:R3,R5, phase:6.4") {
    "refuse wider declarations and missing component grants before either path invokes its driver" in {
      Given("docs/spec/host-source-read.md R3 R5 E8, wrong identity, wider templates and unauthorized component scopes")
      val receipt = HostSourceReadSpecSupport.result()
      val profile = ProcessExecutionTestProfile(Map(HostSourceReadSpecSupport.capability -> receipt))
      val definitions = Vector(HostSourceReadSpecSupport.definition(identity = "another-reader"),
        HostSourceReadSpecSupport.definition(policy = HostSourceReadSpecSupport.templatePolicy(Set(
          ProcessArgumentTemplate(Vector.empty), ProcessArgumentTemplate(Vector(ProcessArgumentSlot.Literal("extra")))))))
      val contexts = definitions.map(d => HostSourceReadSpecSupport.contextFor(d, receipt, driver = Some(profile.driver))) ++ Vector(
        HostSourceReadSpecSupport.contextFor(HostSourceReadSpecSupport.definition(), receipt, granted = false, driver = Some(profile.driver)),
        HostSourceReadSpecSupport.contextFor(HostSourceReadSpecSupport.definition(), receipt, installed = false, driver = Some(profile.driver)))
      When("both typed entry points attempt source admission")
      val results = contexts.flatMap { context =>
        val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
        Vector(behavior.directSource(HostSourceReadSpecSupport.target, HostSourceReadSpecSupport.payload)(using new UnitOfWork(context)),
          new UnitOfWorkInterpreter(new UnitOfWork(context)).run(
            behavior.freeSource(HostSourceReadSpecSupport.target, HostSourceReadSpecSupport.payload)))
      }
      Then("every refusal remains a structured failure and no driver invocation occurs")
      results.foreach(_.isFaillure shouldBe true)
      profile.driver.executions shouldBe empty
    }
  }

  "Process Execution internal DSL" should {
    "construct equivalent UnitOfWork intent through direct and Free execution paths" in {
      Given("generated admitted executions and deterministic runtime driver results")

      When("a Behavior invokes Process Execution directly and through an ExecUowM program")
      val property = Prop.forAll(_exit_codes) { exitcode =>
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val result = _result(capability, exitcode)
        val fixture = ProcessExecutionTestProfile.admittedC(capability, result).toOption.get
        val context = _context(Some(fixture.profile.driver))
        val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      val behavior = new ProcessExecutionBehavior(Behavior.Core(context, None, None))
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
      observabilitycontext = base.observability,
      processexecutiondriveroption = driver,
      processexecutionadmissionoption = admission
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

private final class ProcessExecutionBehavior(
  val behaviorCore: Behavior.Core
) extends Behavior {
  def freeSource(target: HostSourceTarget, payload: HostSourcePayload) =
    host_source_read(target, payload)

  def directSource(target: HostSourceTarget, payload: HostSourcePayload)(using UnitOfWork): Consequence[HostSourceReadResult] =
    host_source_read_c(target, payload)

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
