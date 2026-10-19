package org.goldenport.cncf.action

import java.nio.file.{Files, Path}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramRecorder, UnitOfWorkProgramStubProvider}
import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Actual-context admission of exact scenario intent through the existing interpreter.
 * @since   Oct. 18, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class SbtScenarioExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SbtScenarioExecutionDslSpec.*

  private val _owner = ComponentId("org.goldenport.spec.ScenarioOwner")
  private val _workspace_id = WorkspaceId.parseC("scenario").toOption.get
  private val _capability = ProcessCapabilityId.parseC("scenario-test-only").toOption.get
  private val _class_name = "org.example.ParameterSpec"
  private val _scenario = SbtScenarioSelection(_class_name, "parameter case 1")
  private val _limits = ProcessExecutionLimits(Some(5000L), Some(5000L), Some(100L), Some(65536L),
    Some(65536L), Some(65536L), Some(16L), Some(65536L), Some(16L), Some(65536L), Some(65536L))

  "Exact typed scenario execution" should {
    "construct and record exact scenario intent without runtime services or authority" in {
      Given("a Behavior context without workspace admission, process admission or a driver and an explicit recording stub")
      val fixture = _fixture(Vector(_class_name))
      val context = fixture.context.withScope(ScopeContext(ScopeKind.Runtime, "record-only", None, fixture.context.observability))
      val target = SbtTarget(_workspace_id, Some(_scenario))
      val supplied = SbtExecutionResult(target, SbtOperation.TestOnly, fixture.result,
        Some(SbtTestSelectionResult.Verified(Vector(_class_name), 1, 1L)))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.SbtExec(_, SbtOperation.TestOnly) => Consequence.success(supplied)
          case _ => Consequence.notImplemented("unexpected scenario recording operation")
        }
      }
      try {
        When("the exact program is constructed and recorded without interpretation")
        val behavior = new SbtScenarioDslBehavior(Behavior.Core(context, None, None))
        val recording = UnitOfWorkProgramRecorder.record(behavior.testOnly(target), provider)
        Then("one exact typed occurrence and the deliberate stub result are retained without a driver call")
        recording.occurrences.map(_.operation) shouldBe Vector(UnitOfWorkOp.SbtExec(target, SbtOperation.TestOnly))
        recording.result.toOption shouldBe Some(supplied)
        fixture.profile.driver.executions shouldBe empty
        When("actual admission is attempted in that service-free context")
        val denied = SbtExecutionAdmission.resolveC(context, target, SbtOperation.TestOnly)
        Then("recording has granted no workspace or process authority")
        denied.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "use current context B to narrow its host allowlist and retain the exact target" in {
      Given("a program constructed in A and an independent B with the same logical workspace and selected class")
      val contexta = _fixture(Vector("org.other.ConstructionSpec"))
      val contextb = _fixture(Vector(_class_name, "org.example.OtherSpec"))
      val target = SbtTarget(_workspace_id, Some(_scenario))
      val behavior = new SbtScenarioDslBehavior(Behavior.Core(contexta.context, None, None))
      val program = behavior.testOnly(target)
      try {
        When("the existing interpreter evaluates the constructed program using B")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(program).toOption.get
        Then("B dispatches one literal class/name vector and preserves its actual process receipt")
        result.target shouldBe target
        result.outcome shouldBe SbtExecutionOutcome.Succeeded
        result.processResult shouldBe contextb.result
        result.testSelection shouldBe Some(SbtTestSelectionResult.Verified(Vector(_class_name), 1, 1L))
        val execution = contextb.profile.driver.executions.head
        execution.effectiveArguments shouldBe Vector("--batch", "-Dsbt.server.autostart=false",
          "-Dsbt.supershell=false", "-Dsbt.color=false", "testOnly org.example.ParameterSpec -- -t \"parameter case 1\"")
        execution.request.arguments shouldBe empty
        execution.request.workingDirectory shouldBe None
        execution.definition.capability shouldBe _capability
        execution.effectiveLimits shouldBe _limits
        contextb.workspace.bindingC(_workspace_id).toOption.get.workingDirectoryC.toOption shouldBe
          Some(contextb.root.resolve("checkout").toRealPath())
        contextb.profile.driver.executions should have size 1
        contexta.profile.driver.executions shouldBe empty
      } finally Vector(contexta, contextb).foreach(x => _close(x.root))
    }

    "deny missing current grants and classes outside the current host allowlist before the driver" in {
      Given("current contexts with an absent class, ReadOnly access or an absent actual component grant")
      val fixtures = Vector(_fixture(Vector("org.example.OtherSpec")),
        _fixture(Vector(_class_name), permission = WorkspacePermission.ReadOnly),
        _fixture(Vector(_class_name), granted = false),
        _fixture(Vector(_class_name), owner = ComponentId("org.example.OtherOwner")))
      try {
        When("the exact scenario intent is interpreted under each current context")
        val results = fixtures.map(x => new UnitOfWorkInterpreter(new UnitOfWork(x.context)).interpret(
          UnitOfWorkOp.SbtExec(SbtTarget(_workspace_id, Some(_scenario)), SbtOperation.TestOnly)))
        Then("none of the declarations or construction facts supplies missing runtime authority")
        results.foreach(_.isFaillure shouldBe true)
        fixtures.foreach(_.profile.driver.executions shouldBe empty)
      } finally fixtures.foreach(x => _close(x.root))
    }

    "reject scenario intent for Compile and Test and reject explicit null selectors" in {
      Given("an authorized current host and malformed or operation-incompatible scenario options")
      val fixture = _fixture(Vector(_class_name))
      val intents = Vector(
        UnitOfWorkOp.SbtExec(SbtTarget(_workspace_id, Some(_scenario)), SbtOperation.Compile),
        UnitOfWorkOp.SbtExec(SbtTarget(_workspace_id, Some(_scenario)), SbtOperation.Test),
        UnitOfWorkOp.SbtExec(SbtTarget(_workspace_id, null), SbtOperation.TestOnly),
        UnitOfWorkOp.SbtExec(SbtTarget(_workspace_id, Some(null)), SbtOperation.TestOnly))
      try {
        When("the existing typed interpreter admits each intent")
        val results = intents.map(new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret)
        Then("all inputs fail structurally without any process execution")
        results.foreach(_.isFaillure shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "preserve tagged generic denial and fixed request restrictions after narrowing" in {
      Given("an internally resolved exact scenario under an authorized context")
      val fixture = _fixture(Vector(_class_name))
      val execution = SbtExecutionAdmission.resolveC(fixture.context,
        SbtTarget(_workspace_id, Some(_scenario)), SbtOperation.TestOnly).toOption.get
      try {
        When("a generic tagged operation or request overrides attempt to reuse that admission")
        val generic = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.ProcessExec(execution))
        val requests = Vector(execution.request.copy(arguments = Vector("test")),
          execution.request.copy(workingDirectory = Some(WorkAreaRelativePath.parseC("other").toOption.get)),
          execution.request.copy(input = ProcessExecutionInput.Bytes(Vector(1.toByte))),
          execution.request.copy(requestedLimits = ProcessExecutionLimits(executionTimeoutMillis = Some(1L))))
        val overrides = requests.map(execution.definition.validateRequestC(_, execution.effectiveLimits))
        Then("the narrowed definition still requires typed intent with the original empty request")
        generic.isFaillure shouldBe true
        overrides.foreach(_.isFaillure shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally _close(fixture.root)
    }

    "accept exact literal names and reject unsupported rendering without normalization" in {
      Given("bounded names including Unicode, spaces and punctuation, and malformed rendering inputs")
      val accepted = Vector("parameter case 1", "日本語のケース", "a'-$()[]", "a" * 512)
      val rejected = Vector(null, "", " leading", "trailing ", "a" * 513, "line\nfeed", "quote\"", "back\\slash", "semi;colon")
      When("public selector validation admits each exact name and class")
      val valid = accepted.map(x => SbtScenarioSelection(_class_name, x).validateC)
      val invalid = rejected.map(x => SbtScenarioSelection(_class_name, x).validateC) ++
        Vector(SbtScenarioSelection(null, "name").validateC, SbtScenarioSelection("bad.Class*", "name").validateC)
      Then("accepted selectors are unchanged and unsupported literal inputs have structured failures")
      valid.map(_.toOption.get.testName) shouldBe accepted
      invalid.foreach(_.isFaillure shouldBe true)
    }

    "preserve generated selector identity and reject injected rendering delimiters" in {
      Given("generated exact scenario names and each forbidden delimiter")
      val names = Gen.choose(1, 300).map(x => "case " + "x" * x)
      When("the public model admits originals and contaminated variants")
      val property = Prop.forAll(names, Gen.oneOf("\"", "\\", ";", "\n", "\u0000")) { (name, delimiter) =>
        val selection = SbtScenarioSelection(_class_name, name)
        selection.validateC.toOption.contains(selection) &&
          SbtScenarioSelection(_class_name, name + delimiter + "end").validateC.isFaillure
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(64), property)
      Then("the accepted selector has exact identity and no delimiter becomes executable text")
      checked.passed shouldBe true
    }
  }

  "E7 Typed scenario parameters in the current host" must afterWord(
    "in spec:validation-test-metadata, example:E7, rules:R1,R3,R5, phase:6.2"
  ) {
    "render literal ConfigMap tokens inside the current host task with an empty request" in {
      Given("docs/spec/validation-test-metadata.md R1,R3,R5 E7: typed inputs constructed in A and independent current B")
      val contexta = _fixture(Vector("org.other.ConstructionSpec"))
      val contextb = _fixture(Vector(_class_name, "org.example.OtherSpec"))
      val parameters = Map("label" -> SbtScenarioParameters.Value.Text("日本語 ' $() literal case"),
        "enabled" -> SbtScenarioParameters.Value.BooleanValue(false),
        "count" -> SbtScenarioParameters.Value.Integer(-2L))
      val target = SbtTarget(_workspace_id, Some(_scenario.copy(parameters = parameters)))
      val behavior = new SbtScenarioDslBehavior(Behavior.Core(contexta.context, None, None))
      val program = behavior.testOnly(target)
      try {
        When("the existing interpreter evaluates the program under B")
        val result = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).run(program).toOption.get
        Then("B retains exact typed target data and appends sorted literal tokens only inside its task")
        result.target shouldBe target
        result.target.scenario.get.parameters shouldBe parameters
        result.outcome shouldBe SbtExecutionOutcome.Succeeded
        result.processResult shouldBe contextb.result
        contextb.profile.driver.executions should have size 1
        val execution = contextb.profile.driver.executions.head
        val arguments = Vector("--batch", "-Dsbt.server.autostart=false", "-Dsbt.supershell=false", "-Dsbt.color=false",
          "testOnly org.example.ParameterSpec -- -t \"parameter case 1\" " +
            "\"-Dcncf.validation.input.count=-2\" \"-Dcncf.validation.input.enabled=false\" " +
            "\"-Dcncf.validation.input.label=日本語 ' $() literal case\"")
        execution.definition.fixedArguments shouldBe arguments
        execution.effectiveArguments shouldBe arguments
        execution.request.arguments shouldBe empty
        execution.request.workingDirectory shouldBe None
        execution.effectiveLimits shouldBe _limits
        execution.definition.capability shouldBe _capability
        contextb.workspace.bindingC(_workspace_id).toOption.get.workingDirectoryC.toOption shouldBe
          Some(contextb.root.resolve("checkout").toRealPath())
        contexta.profile.driver.executions shouldBe empty
        When("generic tagged intent or existing request overrides try to reuse the typed admission")
        val generic = new UnitOfWorkInterpreter(new UnitOfWork(contextb.context)).interpret(UnitOfWorkOp.ProcessExec(execution))
        val denied = Vector(execution.request.copy(arguments = Vector("test")),
          execution.request.copy(workingDirectory = Some(WorkAreaRelativePath.parseC("other").toOption.get)),
          execution.request.copy(input = ProcessExecutionInput.Bytes(Vector(1.toByte))),
          execution.request.copy(requestedLimits = ProcessExecutionLimits(executionTimeoutMillis = Some(1L))))
          .map(execution.definition.validateRequestC(_, execution.effectiveLimits))
        Then("typed parameters confer no generic execution or request override authority")
        generic.isFaillure shouldBe true
        denied.foreach(_.isFaillure shouldBe true)
        contextb.profile.driver.executions should have size 1
      } finally Vector(contexta, contextb).foreach(x => _close(x.root))
    }

    "reject malformed typed data before the driver and retain all current authority gates" in {
      Given("docs/spec/validation-test-metadata.md R1,R5 E7: malformed inputs and hosts missing independent permissions")
      val fixture = _fixture(Vector(_class_name))
      val malformed = Vector[Map[String, SbtScenarioParameters.Value]](null,
        Map("label" -> null), Map("Label" -> SbtScenarioParameters.Value.Text("case")),
        Map("label" -> SbtScenarioParameters.Value.Text(null)),
        Map("label" -> SbtScenarioParameters.Value.Text("bad;value")),
        Map("label" -> SbtScenarioParameters.Value.Text("bad\"value")),
        Map("label" -> SbtScenarioParameters.Value.Text("bad\\value")),
        Map("label" -> SbtScenarioParameters.Value.Text("bad\nvalue")),
        Map("label" -> SbtScenarioParameters.Value.Text("x" * 513)))
      val restricted = Vector(_fixture(Vector("org.example.OtherSpec")),
        _fixture(Vector(_class_name), permission = WorkspacePermission.ReadOnly),
        _fixture(Vector(_class_name), granted = false),
        _fixture(Vector(_class_name), owner = ComponentId("org.example.OtherOwner")))
      try {
        When("the current interpreter admits malformed data or valid data without current host authority")
        val invalid = malformed.map { parameters =>
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.SbtExec(
            SbtTarget(_workspace_id, Some(_scenario.copy(parameters = parameters))), SbtOperation.TestOnly))
        }
        val target = SbtTarget(_workspace_id, Some(_scenario.copy(parameters =
          Map("count" -> SbtScenarioParameters.Value.Integer(2)))))
        val denied = restricted.map(x => new UnitOfWorkInterpreter(new UnitOfWork(x.context))
          .interpret(UnitOfWorkOp.SbtExec(target, SbtOperation.TestOnly)))
        Then("malformed data and missing workspace, owner, grant or class permissions fail before dispatch")
        (invalid ++ denied).foreach(_.isFaillure shouldBe true)
        (Vector(fixture) ++ restricted).foreach(_.profile.driver.executions shouldBe empty)
      } finally (Vector(fixture) ++ restricted).foreach(x => _close(x.root))
    }
  }

  private def _fixture(
    classes: Vector[String],
    permission: WorkspacePermission = WorkspacePermission.Manage,
    granted: Boolean = true,
    owner: ComponentId = _owner
  ): Fixture = {
    val base = Files.createDirectories(Path.of("target", "sbt-scenario-dsl-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-")
    val checkout = Files.createDirectory(root.resolve("checkout"))
    val workspace = WorkspaceExecutionAdmission.createC(Vector(_workspace_id -> checkout),
      Vector(WorkspaceGrant(_owner, _workspace_id, permission))).toOption.get
    val definition = SbtProgramDefinition.createC(workspace.bindingC(_workspace_id).toOption.get,
      SbtOperation.TestOnly, _capability, "scenario-test-only", Path.of("/usr/bin/true"),
      _limits, Map("BUILD_PROFILE" -> "scenario"), classes).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    val admission = ProcessExecutionAdmission.forComponentsC(policy,
      Map(_owner -> (if (granted) Vector(ProcessExecutionGrant(_capability)) else Vector.empty))).toOption.get
    val text = s"[info] ${_class_name}:\n[info] Total number of tests run: 1\n" +
      "[info] Suites: completed 1, aborted 0\n[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0\n[info] All tests passed."
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    val result = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
      ProcessExecutionCapture(bytes, bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0L, false),
      Vector.empty, 11L, "scenario-test-only")
    val profile = ProcessExecutionTestProfile(Map(_capability -> result))
    val workareas = Files.createDirectories(base.resolve("workareas"))
    GlobalContext.set(GlobalContext(new WorkAreaSpace(WorkAreaGroup(new FileWorkArea(
      WorkAreaId("sbt_scenario_spec"), WorkArea.Limit(None, None, None), workareas)))))
    val execution = ExecutionContext.create()
    val runtime = ScopeContext(ScopeKind.Runtime, "scenario-spec", None, execution.observability,
      processexecutiondriveroption = Some(profile.driver), processexecutionadmissionoption = Some(admission),
      workspaceExecutionAdmissionOption = Some(workspace))
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    val scope = Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown)
      .createChildScope(ScopeKind.Service, "build").createChildScope(ScopeKind.Action, "scenario")
    Fixture(root, workspace, profile, result, execution.withScope(scope))
  }

  private def _close(root: Path): Unit = Using.resource(Files.walk(root)) { stream =>
    stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
  }
}

object SbtScenarioExecutionDslSpec {
  private final case class Fixture(
    root: Path,
    workspace: WorkspaceExecutionAdmission,
    profile: ProcessExecutionTestProfile,
    result: ProcessExecutionResult,
    context: ExecutionContext
  )
}

private final class SbtScenarioDslBehavior(val behaviorCore: Behavior.Core) extends Behavior {
  def testOnly(target: SbtTarget) = sbt_test_only(target)
}
