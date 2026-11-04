package org.goldenport.cncf.action

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkEffectClass, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramRecorder, UnitOfWorkProgramStubProvider}
import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov.  5, 2026
 * @version Nov.  5, 2026
 */
final class ValidationCheckExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ValidationCheckExecutionDslSpec.Fixture

  private val _owner = ComponentId("org.goldenport.spec.ValidationCheckOwner")
  private val _workspace_id = WorkspaceId.parseC("project").toOption.get
  private val _check = ValidationCheckId.parseC("exact content").toOption.get
  private val _capability = ProcessCapabilityId.parseC("project-content-check").toOption.get
  private val _target = ValidationCheckTarget(_workspace_id, _check)
  private val _limits = ProcessExecutionLimits(Some(5000), Some(5000), Some(100), Some(65536), Some(65536),
    Some(65536), Some(16), Some(65536), Some(16), Some(65536), Some(65536))
  private val _receipt = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
    ProcessExecutionCapture(Vector(1.toByte, 2.toByte), 2L, false), ProcessExecutionCapture(Vector.empty, 0L, false),
    Vector.empty, 17L, "observed-check-program")

  "Typed validation check DSL" should {
    "record exactly one External intent without construction-time admission or driver allocation" in {
      Given("a construction context without workspace admission, process admission or a driver")
      val construction = _fixture(workspaceinstalled = false, processinstalled = false, driverinstalled = false)
      val executing = _fixture()
      val behavior = new ValidationCheckDslBehavior(Behavior.Core(construction.context, None, None))
      val conclusion = Consequence.serviceUnavailable("Explicit recording stub").conclusion
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.ValidationCheckExec(target, kind) =>
            Consequence.success(ValidationCheckExecutionResult(target, kind, _receipt, ValidationCheckPurpose.Behavior))
          case UnitOfWorkOp.ValidationCheckExecAttempt(target, kind) =>
            Consequence.success(ValidationCheckExecutionAttempt.AdmissionRejected(target, kind, conclusion))
          case _ => Consequence.notImplemented("Unexpected recording operation")
        }
      }
      val uow = new UnitOfWork(executing.context)
      try {
        When("both thin programs are constructed and recorded")
        val direct = behavior.check(_target)
        val attempt = behavior.attempt(_target)
        val recorded = Vector(UnitOfWorkProgramRecorder.record(direct, provider), UnitOfWorkProgramRecorder.record(attempt, provider))
        Then("recording preserves the exact External intents without executing either driver")
        recorded.map(_.occurrences.map(_.operation)) shouldBe Vector(
          Vector(UnitOfWorkOp.ValidationCheckExec(_target, ValidationCheckOperation.Run)),
          Vector(UnitOfWorkOp.ValidationCheckExecAttempt(_target, ValidationCheckOperation.Run)))
        recorded.foreach(_.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.External))
        construction.profile.driver.executions shouldBe empty
        executing.profile.driver.executions shouldBe empty
        When("another owning UoW interprets the constructed attempt")
        val result = new UnitOfWorkInterpreter(uow).run(attempt)
        Then("the executing context supplies current component, workspace and capability authority")
        result.toOption.get match {
          case ValidationCheckExecutionAttempt.Observed(observed) => observed.purpose shouldBe ValidationCheckPurpose.Behavior
          case other => fail(s"Expected an admitted observation: $other")
        }
        executing.profile.driver.executions should have size 1
        construction.profile.driver.executions shouldBe empty
      } finally { uow.dispose(); construction.close(); executing.close() }
    }

    "retain the actual admitted process receipt and fixed host definition through the interpreter" in {
      Given("an explicitly controlled driver with one complete process receipt")
      val fixture = _fixture()
      val uow = new UnitOfWork(fixture.context)
      try {
        When("the thin direct DSL is executed in its owning UoW")
        val result = new UnitOfWorkInterpreter(uow).run(
          new ValidationCheckDslBehavior(Behavior.Core(fixture.context, None, None)).check(_target)).toOption.get
        Then("the typed result retains the exact receipt and the driver sees only the declared argv")
        result.processResult should be theSameInstanceAs _receipt
        result.outcome shouldBe ValidationCheckExecutionOutcome.Succeeded
        result.target shouldBe _target
        result.operation shouldBe ValidationCheckOperation.Run
        result.purpose shouldBe ValidationCheckPurpose.Behavior
        val execution = fixture.profile.driver.executions.head
        execution.request shouldBe ProcessExecutionRequest(_capability)
        execution.effectiveArguments shouldBe Vector("-Fx", "expected", "value.txt")
        execution.effectiveLimits shouldBe _limits
      } finally { uow.dispose(); fixture.close() }
    }

    "take each purpose from admitted host metadata in both direct and attempt operations" in {
      Given("controlled host declarations for compilation, behavior and lint under the same logical caller target")
      ValidationCheckPurpose.values.foreach { purpose =>
        val fixture = _fixture(purpose = purpose, executable = _tool("true"), arguments = Vector.empty)
        val uow = new UnitOfWork(fixture.context)
        try {
          val behavior = new ValidationCheckDslBehavior(Behavior.Core(fixture.context, None, None))
          val interpreter = new UnitOfWorkInterpreter(uow)
          When("the interpreter obtains the controlled receipt through each typed operation")
          val direct = interpreter.run(behavior.check(_target)).toOption.get
          val attempt = interpreter.run(behavior.attempt(_target)).toOption.get
          Then("both results carry the exact admitted purpose without adding a purpose to the caller target")
          direct.purpose shouldBe purpose
          attempt match {
            case ValidationCheckExecutionAttempt.Observed(observed) => observed.purpose shouldBe purpose
            case other => fail(s"Expected an admitted controlled observation: $other")
          }
          fixture.profile.driver.executions should have size 2
        } finally { uow.dispose(); fixture.close() }
      }
    }

    "reject missing authority, undeclared identities and substituted bindings without driver work" in {
      Given("independently absent current workspace, process grants, actual owner and declared check")
      val fixtures = Vector(
        _fixture(workspaceinstalled = false), _fixture(processinstalled = false),
        _fixture(capabilitygranted = false), _fixture(workspacegranted = false),
        _fixture(owner = ComponentId("org.goldenport.spec.OtherOwner")),
        _fixture(permission = WorkspacePermission.ReadOnly), _fixture(alternatebinding = true))
      fixtures.foreach { fixture =>
        val uow = new UnitOfWork(fixture.context)
        try {
          When("the current interpreter handles a check attempt and direct operation")
          val interpreter = new UnitOfWorkInterpreter(uow)
          val attempt = interpreter.interpret(UnitOfWorkOp.ValidationCheckExecAttempt(_target, ValidationCheckOperation.Run))
          val direct = interpreter.interpret(UnitOfWorkOp.ValidationCheckExec(_target, ValidationCheckOperation.Run))
          Then("attempt reports real admission rejection, direct fails and neither launches a driver")
          attempt.toOption.get shouldBe a[ValidationCheckExecutionAttempt.AdmissionRejected]
          direct.isFaillure shouldBe true
          fixture.profile.driver.executions shouldBe empty
        } finally { uow.dispose(); fixture.close() }
      }
      val fixture = _fixture()
      val uow = new UnitOfWork(fixture.context)
      try {
        val interpreter = new UnitOfWorkInterpreter(uow)
        val targets = Vector(_target.copy(checkIdentity = ValidationCheckId.parseC("Exact content").toOption.get),
          _target.copy(workspaceId = WorkspaceId.parseC("unknown").toOption.get))
        When("unknown exact check or workspace identities are interpreted")
        val denied = targets.map(target => interpreter.interpret(
          UnitOfWorkOp.ValidationCheckExecAttempt(target, ValidationCheckOperation.Run)))
        Then("the declarations are not replaced by another check")
        denied.foreach(_.toOption.get shouldBe a[ValidationCheckExecutionAttempt.AdmissionRejected])
        fixture.profile.driver.executions shouldBe empty
      } finally { uow.dispose(); fixture.close() }
    }

    "refuse a tagged generic ProcessExec before reaching the driver" in {
      Given("an actual typed admission resolved for the current component")
      val fixture = _fixture()
      val uow = new UnitOfWork(fixture.context)
      try {
        val execution = ValidationCheckExecutionAdmission.resolveC(fixture.context, _target, ValidationCheckOperation.Run).toOption.get
        When("that admitted execution is submitted through generic ProcessExec")
        val result = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ProcessExec(execution))
        Then("the generic interpreter rejects its semantic tag with no driver call")
        result.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally { uow.dispose(); fixture.close() }
    }

    "retain admitted start, await and missing-driver failures as Uncertain without replay" in {
      Given("controlled start and await failures after successful current admission")
      for (failstart <- Vector(true, false)) {
        var starts = 0
        val driver = new ProcessExecutionDriver {
          val safeIdentity = "controlled-failing-check"
          def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
            starts += 1
            if (failstart) Consequence.serviceUnavailable("Controlled start failure")
            else Consequence.success(new ProcessExecutionHandle {
              def awaitC: Consequence[ProcessExecutionResult] = Consequence.serviceUnavailable("Controlled await failure")
              def cancelC: Consequence[Unit] = Consequence.unit
            })
          }
        }
        val fixture = _fixture(driver = Some(driver))
        val uow = new UnitOfWork(fixture.context)
        try {
          When("one admitted attempt reaches the controlled driver")
          val result = new UnitOfWorkInterpreter(uow).interpret(
            UnitOfWorkOp.ValidationCheckExecAttempt(_target, ValidationCheckOperation.Run))
          Then("the attempt is Uncertain and the interpreter never retries it")
          result.toOption.get shouldBe a[ValidationCheckExecutionAttempt.Uncertain]
          starts shouldBe 1
          new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ValidationCheckExec(_target, ValidationCheckOperation.Run))
            .isFaillure shouldBe true
          starts shouldBe 2
        } finally { uow.dispose(); fixture.close() }
      }
      val fixture = _fixture(driverinstalled = false)
      val uow = new UnitOfWork(fixture.context)
      try {
        When("admission succeeds but no runtime driver is installed")
        val result = new UnitOfWorkInterpreter(uow).interpret(
          UnitOfWorkOp.ValidationCheckExecAttempt(_target, ValidationCheckOperation.Run))
        Then("the admitted failure is Uncertain and has no fabricated NotExecuted receipt")
        result.toOption.get shouldBe a[ValidationCheckExecutionAttempt.Uncertain]
      } finally { uow.dispose(); fixture.close() }
    }
  }

  "Real declared validation processes" should {
    "run fixed grep in the admitted cwd and observe success versus actual nonzero exit from its contents" in {
      Given("a tiny admitted directory containing value.txt and an ordinary fixed grep definition")
      val driver = new LocalProcessExecutionDriver()
      val fixture = _fixture(driver = Some(driver))
      val uow = new UnitOfWork(fixture.context)
      try {
        val interpreter = new UnitOfWorkInterpreter(uow)
        val behavior = new ValidationCheckDslBehavior(Behavior.Core(fixture.context, None, None))
        When("grep reads matching content, then reads changed content through the same typed target")
        val matched = interpreter.evaluateInActiveUnitOfWorkC(behavior.check(_target)).toOption.get
        Files.writeString(fixture.checkout.resolve("value.txt"), "different\n")
        val unmatched = interpreter.evaluateInActiveUnitOfWorkC(behavior.attempt(_target)).toOption.get match {
          case ValidationCheckExecutionAttempt.Observed(result) => result
          case other => fail(s"Expected an actual process observation: $other")
        }
        Then("actual cwd and file contents determine zero versus nonzero exit")
        matched.outcome shouldBe ValidationCheckExecutionOutcome.Succeeded
        matched.processResult.termination shouldBe ProcessExecutionTermination.Exited(0)
        new String(matched.processResult.stdout.content.toArray, StandardCharsets.UTF_8) shouldBe "expected\n"
        unmatched.outcome shouldBe ValidationCheckExecutionOutcome.Failed(1)
        unmatched.processResult.termination shouldBe ProcessExecutionTermination.Exited(1)
        uow.commit().isSuccess shouldBe true
        Vector(matched, unmatched).foreach { result =>
          result.purpose shouldBe ValidationCheckPurpose.Behavior
          result.processResult.safeProgramIdentity shouldBe "project-content-check"
          result.processResult.stdout.byteCount shouldBe result.processResult.stdout.content.size.toLong
          result.processResult.stderr.byteCount shouldBe result.processResult.stderr.content.size.toLong
          result.processResult.stdout.truncated shouldBe false
          result.processResult.stderr.truncated shouldBe false
        }
      } finally { uow.dispose(); driver.close(); fixture.close() }
    }

    "observe actual sleep timeout and fixed continuous-output budget termination through the UoW" in {
      Given("ordinary fixed sleep and yes programs with finite host limits")
      val cases = Vector(
        (_tool("sleep"), Vector("2"), _limits.copy(executionTimeoutMillis = Some(100L)), ProcessExecutionTermination.TimedOut),
        (_tool("yes"), Vector("x"), _limits.copy(stdoutBytes = Some(64L)),
          ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)))
      cases.foreach { case (executable, arguments, limits, termination) =>
        val driver = new LocalProcessExecutionDriver()
        val fixture = _fixture(driver = Some(driver), executable = executable, arguments = arguments, limits = limits)
        val uow = new UnitOfWork(fixture.context)
        try {
          When("the owning interpreter launches and awaits that exact declared process")
          val actual = new UnitOfWorkInterpreter(uow).interpret(
            UnitOfWorkOp.ValidationCheckExecAttempt(_target, ValidationCheckOperation.Run)).toOption.get
          Then("the observed result retains actual timeout or output limit termination")
          actual match {
            case ValidationCheckExecutionAttempt.Observed(result) =>
              result.purpose shouldBe ValidationCheckPurpose.Behavior
              result.processResult.termination shouldBe termination
              result.outcome shouldBe ValidationCheckExecutionOutcome.Incomplete(termination)
              result.processResult.safeProgramIdentity shouldBe "project-content-check"
            case other => fail(s"Expected real process termination: $other")
          }
          driver.terminationConfirmed shouldBe true
        } finally { uow.dispose(); driver.close(); fixture.close() }
      }
    }
  }

  private def _fixture(
    workspaceinstalled: Boolean = true,
    processinstalled: Boolean = true,
    driverinstalled: Boolean = true,
    capabilitygranted: Boolean = true,
    workspacegranted: Boolean = true,
    permission: WorkspacePermission = WorkspacePermission.Manage,
    owner: ComponentId = _owner,
    alternatebinding: Boolean = false,
    driver: Option[ProcessExecutionDriver] = None,
    executable: Path = Paths.get("/usr/bin/grep"),
    arguments: Vector[String] = Vector("-Fx", "expected", "value.txt"),
    limits: ProcessExecutionLimits = _limits,
    purpose: ValidationCheckPurpose = ValidationCheckPurpose.Behavior
  ): Fixture = {
    val root = Files.createTempDirectory("validation-check-dsl-")
    val checkout = Files.createDirectory(root.resolve("checkout"))
    Files.writeString(checkout.resolve("value.txt"), "expected\n")
    val grants = if (workspacegranted) Vector(WorkspaceGrant(_owner, _workspace_id, permission)) else Vector.empty
    val workspace = WorkspaceExecutionAdmission.createC(Vector(_workspace_id -> checkout), grants).toOption.get
    val binding = if (alternatebinding) WorkspaceBinding.createC(_workspace_id, checkout).toOption.get
      else workspace.bindingC(_workspace_id).toOption.get
    val definition = ValidationCheckProgramDefinition.createC(binding, _check, purpose, "fixed-process", _capability,
      "project-content-check", executable, arguments, limits, Map.empty).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    val admission = ProcessExecutionAdmission.forComponentsC(policy,
      Map(_owner -> (if (capabilitygranted) Vector(ProcessExecutionGrant(_capability)) else Vector.empty))).toOption.get
    val profile = ProcessExecutionTestProfile(Map(_capability -> _receipt))
    val workareas = Files.createDirectory(root.resolve("workareas"))
    val space = new WorkAreaSpace(WorkAreaGroup(new FileWorkArea(
      WorkAreaId("validation_check_spec"), WorkArea.Limit(None, None, None), workareas)))
    GlobalContext.set(GlobalContext(space))
    val execution = ExecutionContext.create()
    val runtime = ScopeContext(ScopeKind.Runtime, "validation-check-spec", None, execution.observability,
      processexecutiondriveroption = if (driverinstalled) driver.orElse(Some(profile.driver)) else None,
      processexecutionadmissionoption = if (processinstalled) Some(admission) else None,
      workspaceExecutionAdmissionOption = if (workspaceinstalled) Some(workspace) else None)
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    val scope = Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown)
      .createChildScope(ScopeKind.Service, "validation").createChildScope(ScopeKind.Action, "check")
    Fixture(root, checkout, profile, execution.withScope(scope))
  }

  private def _tool(name: String): Path = Vector(Paths.get("/usr/bin", name), Paths.get("/bin", name))
    .find(path => Files.isRegularFile(path) && Files.isExecutable(path)).getOrElse(fail(s"Ordinary fixture tool unavailable: $name"))
}

private object ValidationCheckExecutionDslSpec {
  final case class Fixture(root: Path, checkout: Path, profile: ProcessExecutionTestProfile, context: ExecutionContext) {
    def close(): Unit = Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
  }
}

private final class ValidationCheckDslBehavior(val behaviorCore: Behavior.Core) extends Behavior {
  def check(target: ValidationCheckTarget) = validation_check(target)
  def attempt(target: ValidationCheckTarget) = validation_check_attempt(target)
}
