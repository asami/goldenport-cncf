package org.goldenport.cncf.cli

import java.nio.file.{Files, Path, Paths}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationTarget, SubsystemInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.action.Behavior
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Owning-runtime SPI specs. These are not workflow semantic acceptance or a product SBT bridge. */
/*
 * @since   Oct.  7, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeProcessExecutionInitializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Runtime process setup rejected: $failure")
  }
  private val _owner = ComponentId("org.example.RuntimeWorkflow")
  private val _other = ComponentId("org.example.OtherWorkflow")
  private val _capability = _take(ProcessCapabilityId.parseC("runtime-noop"))
  private val _limits = ProcessExecutionLimits(Some(1000), Some(1000), Some(1000), Some(64), Some(64),
    Some(64), Some(8), Some(512), Some(1), Some(64), Some(128))
  private val _definition = _take(ProcessProgramDefinition.fromRuntimeC(_capability, "runtime-noop", "/usr/bin/true",
    Vector.empty, ProcessArgumentPolicy(Vector.empty, admission = ProcessArgumentAdmission.Template,
      permittedArgumentTemplates = Set(ProcessArgumentTemplate(Vector.empty))), _limits, Set.empty))
  private val _plan = _take(RuntimeProcessExecutionPlan.createC(Vector(_definition),
    Map(_owner -> Vector(ProcessExecutionGrant(_capability)))))
  private val _text = _take(RuntimeProcessExecutionPlanCodec.encode(_plan))
    .asInstanceOf[org.goldenport.configuration.ConfigurationValue.StringValue].value
  private val _key = CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY
  private def _controlled_arguments(cwd: Path): Array[String] = {
    val path = cwd.resolve("test.yaml")
    Files.writeString(path,
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: process-runtime
        |  time:
        |    mode: manual
        |    start-at: '2026-10-06T00:00:00Z'
        |  random:
        |    mode: seeded
        |    seed: process-runtime
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    val home = Files.createDirectories(cwd.resolve("home"))
    Array(s"--textus.test.descriptor=$path", s"--textus.test.home.path=$home",
      "--textus.test.home.inherit.runtime=false")
  }
  private def _host_directory(configured: Boolean): Path = {
    val workroot = Files.createDirectories(Paths.get("target/cncf/work/runtime-process-bootstrap").toAbsolutePath.normalize)
    val cwd = Files.createTempDirectory(workroot, "episode-")
    val directory = Files.createDirectories(cwd.resolve(".textus"))
    Files.createDirectories(cwd.resolve(".cncf"))
    if (configured) {
      Files.writeString(directory.resolve("config.json"), Json.obj(_key -> Json.fromString(_text)).noSpaces)
    }
    cwd
  }
  private def _action_scope(root: ScopeContext, id: ComponentId): ScopeContext = {
    val component = Component.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty)
    Component.Context("same-display-label", root, component, ComponentOrigin.Embed)
      .createChildScope(ScopeKind.Service, "workflow").createChildScope(ScopeKind.Action, "advance")
  }
  private def _v2_text(cwd: Path): String = {
    val id = _take(WorkspaceId.parseC("runtime-checkout"))
    val capability = _take(ProcessCapabilityId.parseC("runtime-test"))
    val plan = _take(RuntimeProcessExecutionPlan.createV2C(Vector(_definition),
      Map(_owner -> Vector(ProcessExecutionGrant(_capability), ProcessExecutionGrant(capability))),
      Vector(WorkspaceRuntimeDeclaration(id, cwd)), Vector(WorkspaceGrant(_owner, id, WorkspacePermission.Manage)),
      Vector(SbtRuntimeDeclaration(id, SbtOperation.Test, capability, "runtime-test", Paths.get("/usr/bin/true"),
        _limits, Map("BUILD_PROFILE" -> "controlled")))))
    _take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue].value
  }

  "Canonical Process Execution bootstrap" should {
    "install one V3 workspace binding for protected Git results and existing SBT metadata with independent owner grants" in {
      Given("a selected V3 Subsystem file, an actual local Git checkout and separate SBT and Git capabilities")
      val cwd = _host_directory(configured = false)
      GitExecutionSpecSupport.setup(cwd, "init", "--initial-branch=main")
      val id = GitExecutionSpecSupport.workspaceId
      val git = GitExecutionSpecSupport.declaration(GitOperationKind.Root, cwd.resolve("fixed-remote.git").toString)
      val buildcapability = _take(ProcessCapabilityId.parseC("runtime-test"))
      val plan = _take(RuntimeProcessExecutionPlan.createV3C(Vector(_definition),
        Map(_owner -> Vector(ProcessExecutionGrant(_capability), ProcessExecutionGrant(git.capability), ProcessExecutionGrant(buildcapability))),
        Vector(WorkspaceRuntimeDeclaration(id, cwd)), Vector(WorkspaceGrant(_owner, id, WorkspacePermission.Manage)),
        Vector(SbtRuntimeDeclaration(id, SbtOperation.Test, buildcapability, "runtime-test", Paths.get("/usr/bin/true"), _limits, Map.empty)),
        Vector(git)))
      val text = _take(RuntimeProcessExecutionPlanCodec.encode(plan)).asInstanceOf[ConfigurationValue.StringValue].value
      Files.writeString(Files.createDirectories(cwd.resolve(".textus")).resolve("config.json"), Json.obj(_key -> Json.fromString(text)).noSpaces)
      val runtime = new CncfRuntime()
      var typed = Option.empty[GitExecutionResult[String]]
      var sbt = Option.empty[ResolvedProcessExecution]
      var inherited = false
      var denied = Vector.empty[Boolean]
      When("public embedding bootstrap supplies inherited actual component scopes and the protected Root program runs")
      val subsystem = _take(runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd), extraComponents = s => {
        val root = s.globalRuntimeContext
        val scope = _action_scope(root, _owner)
        val context = ExecutionContext.create().withScope(scope)
        inherited = root.workspaceExecutionAdmissionOption.get.bindingC(id).toOption.get eq
          scope.workspaceExecutionAdmissionOption.get.bindingC(id).toOption.get
        sbt = SbtExecutionAdmission.resolveC(context, SbtTarget(id), SbtOperation.Test).toOption
        val behavior = new GitSpecBehavior(Behavior.Core(context, None, None))
        typed = new UnitOfWorkInterpreter(new UnitOfWork(context)).run(behavior.program(GitTarget(id), GitOperation.Root)).toOption
        denied = Vector(_action_scope(root, _other), root.createChildScope(ScopeKind.Component, _owner.name)).map { wrong =>
          val current = ExecutionContext.create().withScope(wrong)
          new UnitOfWorkInterpreter(new UnitOfWork(current)).run(
            new GitSpecBehavior(Behavior.Core(current, None, None)).program(GitTarget(id), GitOperation.Root)).isFaillure
        }
        Vector.empty
      }))
      try {
        Then("the actual protected Git value names the canonical checkout and SBT retains its fixed existing declaration")
        inherited shouldBe true
        typed.get.valueC.toOption shouldBe Some(cwd.toRealPath().toString)
        typed.get.outcome shouldBe GitExecutionOutcome.Succeeded
        sbt.get.effectiveArguments.last shouldBe "test"
        denied shouldBe Vector(true, true)
        subsystem.globalRuntimeContext.processExecutionDriverOption.get shouldBe a[LocalProcessExecutionDriver]
      } finally { Subsystem.shutdownOwned(subsystem); runtime.closeEmbedding() }
    }

    "install the selected V2 file's shared workspace binding in actual inherited Component context" in {
      Given("a real selected V2 host file with independent Manage and process grants")
      val cwd = _host_directory(configured = false)
      Files.writeString(Files.createDirectories(cwd.resolve(".textus")).resolve("config.json"),
        Json.obj(_key -> Json.fromString(_v2_text(cwd))).noSpaces)
      val runtime = new CncfRuntime()
      val id = _take(WorkspaceId.parseC("runtime-checkout"))
      var runtimebinding = Option.empty[WorkspaceBinding]
      var inheritedbinding = Option.empty[WorkspaceBinding]
      var admitted = Option.empty[ResolvedProcessExecution]
      var denied = Vector.empty[Boolean]
      When("the public runtime preflights the file and constructs actual owning and wrong-owner Component scopes")
      val subsystem = _take(runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd), extraComponents = s => {
        val root = s.globalRuntimeContext
        val scope = _action_scope(root, _owner)
        runtimebinding = root.workspaceExecutionAdmissionOption.flatMap(_.bindingC(id).toOption)
        inheritedbinding = scope.workspaceExecutionAdmissionOption.flatMap(_.bindingC(id).toOption)
        admitted = SbtExecutionAdmission.resolveC(ExecutionContext.create().withScope(scope), SbtTarget(id), SbtOperation.Test).toOption
        denied = Vector(_action_scope(root, _other), root.createChildScope(ScopeKind.Component, _owner.name)).map { value =>
          SbtExecutionAdmission.resolveC(ExecutionContext.create().withScope(value), SbtTarget(id), SbtOperation.Test).isFaillure
        }
        Vector.empty
      }))
      try {
        Then("the identical binding is inherited, typed resolution succeeds only for the actual owner and the standard driver is installed")
        runtimebinding.isDefined shouldBe true
        (runtimebinding.get eq inheritedbinding.get) shouldBe true
        _take(runtimebinding.get.workingDirectoryC) shouldBe cwd.toRealPath()
        admitted.isDefined shouldBe true
        admitted.get.effectiveArguments shouldBe Vector("--batch", "-Dsbt.server.autostart=false",
          "-Dsbt.supershell=false", "-Dsbt.color=false", "test")
        denied shouldBe Vector(true, true)
        subsystem.globalRuntimeContext.processExecutionDriverOption.get shouldBe a[LocalProcessExecutionDriver]
      } finally {
        Subsystem.shutdownOwned(subsystem)
        runtime.closeEmbedding()
      }
      Given("the owning subsystem has shut down its standard driver after admitting the typed SBT execution")
      When("the stopped owning driver receives the already admitted typed SBT execution")
      val stopped = _take(_take(subsystem.globalRuntimeContext.processExecutionDriverOption.get.startC(admitted.get)).awaitC)
      Then("the original SystemNode remains stopped and the typed result reports NotStarted")
      subsystem.systemNode.state shouldBe SystemNode.State.Stopped
      stopped.termination shouldBe ProcessExecutionTermination.NotStarted
    }

    "refuse V2 authority supplied by a Component source or command argument" in {
      Given("a valid V2 plan and Component targets instead of a selected Subsystem file")
      val cwd = _host_directory(configured = false)
      val text = _v2_text(cwd)
      val selected = _take(SubsystemInstanceId.default("platform"))
      val targets = Vector(_take(CncfConfigurationTarget.ComponentClass.create(_owner)),
        _take(CncfConfigurationTarget.ComponentInstance.create(selected, ComponentInstanceId.default(_owner))))
      val definition = _take(CncfConfigurationParameterCatalog.closed.schema.definition(_key))
      val runtime = new CncfRuntime()
      var constructed = false
      When("the catalog resolves Component authority and the initializer sees CLI authority")
      val componentresults = targets.map(target => definition.input(_key, target, ConfigurationValue.StringValue(text), "component.config"))
      val result = runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd) :+ s"--${_key}=${text}",
        extraComponents = _ => { constructed = true; Vector.empty })
      try {
        Then("neither source grants process or workspace authority before Component construction")
        componentresults.foreach(_.isFaillure shouldBe true)
        result.isFaillure shouldBe true
        constructed shouldBe false
      } finally runtime.closeEmbedding()
    }

    "install runtime-owned driver and component-scoped grants before Component construction and close with its SystemNode" in {
      Given("a real host file, controlled runtime and two actual component identities sharing a display label")
      val cwd = _host_directory(configured = true)
      val runtime = new CncfRuntime()
      var observed = false
      var driver: Option[ProcessExecutionDriver] = None
      var admitted: Option[ResolvedProcessExecution] = None
      When("the public runtime initializes and its host SPI resolves and runs only the granted request")
      val subsystem = _take(runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd), extraComponents = s => {
        val root = s.globalRuntimeContext
        observed = true
        driver = root.processExecutionDriverOption
        val selected = _take(ProcessExecutionAdmission.resolveC(_action_scope(root, _owner), ProcessExecutionRequest(_capability)))
        admitted = Some(selected)
        ProcessExecutionAdmission.resolveC(_action_scope(root, _other), ProcessExecutionRequest(_capability)).isSuccess shouldBe false
        ProcessExecutionAdmission.resolveC(root.createChildScope(ScopeKind.Component, _owner.name),
          ProcessExecutionRequest(_capability)).isSuccess shouldBe false
        ProcessExecutionAdmission.resolveC(_action_scope(root, _owner),
          ProcessExecutionRequest(_capability, Vector("--unapproved"))).isSuccess shouldBe false
        _take(_take(driver.get.startC(selected)).awaitC).termination shouldBe ProcessExecutionTermination.Exited(0)
        Vector.empty
      }))
      try {
        Then("construction sees the shared driver but neither other components nor labels gain its grants")
        observed shouldBe true
        driver.get shouldBe a[LocalProcessExecutionDriver]
        subsystem.systemNode.state shouldBe SystemNode.State.Running
      } finally {
        Subsystem.shutdownOwned(subsystem)
        runtime.closeEmbedding()
      }
      And("the original SystemNode closes its driver and repeated close is terminal without new execution")
      subsystem.systemNode.state shouldBe SystemNode.State.Stopped
      driver.get.startC(admitted.get).isSuccess shouldBe false
      _take(subsystem.systemNode.shutdownC()) shouldBe ()
    }

    "close the owning driver on a later Component assembly failure" in {
      Given("host process configuration and a Component construction callback that will fail")
      val cwd = _host_directory(configured = true)
      val runtime = new CncfRuntime()
      var driver: Option[ProcessExecutionDriver] = None
      var execution: Option[ResolvedProcessExecution] = None
      When("the public initialization installs its driver then construction fails")
      val result = runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd), extraComponents = s => {
        val root = s.globalRuntimeContext
        driver = root.processExecutionDriverOption
        execution = ProcessExecutionAdmission.resolveC(_action_scope(root, _owner), ProcessExecutionRequest(_capability)).toOption
        throw new IllegalStateException("test-only assembly failure")
      })
      try {
        Then("startup reports failure and the captured driver cannot start another process")
        result.isSuccess shouldBe false
        driver.isDefined shouldBe true
        execution.isDefined shouldBe true
        driver.get.startC(execution.get).isSuccess shouldBe false
      } finally runtime.closeEmbedding()
    }

    "preserve deny-by-absence without creating a driver" in {
      Given("a controlled runtime with no process plan")
      val cwd = _host_directory(configured = false)
      val runtime = new CncfRuntime()
      When("the public initialization enters Component assembly")
      val subsystem = _take(runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd)))
      try {
        Then("the ordinary scope has neither a process driver nor an ambient grant fallback")
        val root = subsystem.globalRuntimeContext
        root.processExecutionDriverOption shouldBe None
        ProcessExecutionAdmission.resolveC(_action_scope(root, _owner), ProcessExecutionRequest(_capability)).isSuccess shouldBe false
      } finally {
        Subsystem.shutdownOwned(subsystem)
        runtime.closeEmbedding()
      }
    }

    "refuse command-argument grants before Component construction" in {
      Given("a valid host plan passed as a CLI argument instead of a selected host file")
      val cwd = _host_directory(configured = false)
      val runtime = new CncfRuntime()
      var observed = false
      When("the public initializer admits runtime sources")
      val result = runtime.initializeForEmbedding(cwd, _controlled_arguments(cwd) :+ s"--${_key}=${_text}",
        extraComponents = _ => { observed = true; Vector.empty })
      try {
        Then("the process authority request is refused before any Component is constructed")
        result.isSuccess shouldBe false
        observed shouldBe false
      } finally runtime.closeEmbedding()
    }
  }
}
