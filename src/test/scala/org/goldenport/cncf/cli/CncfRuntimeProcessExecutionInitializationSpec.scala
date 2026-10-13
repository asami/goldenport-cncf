package org.goldenport.cncf.cli

import java.nio.file.{Files, Path, Paths}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.cncf.context.{ScopeContext, ScopeKind}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Owning-runtime SPI specs. These are not workflow semantic acceptance or a product SBT bridge. */
/*
 * @since Oct.  7, 2026
 * @version Oct.  7, 2026
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
        |    start-at: 2026-10-06T00:00:00Z
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
    Array(s"--textus.test.descriptor=$path")
  }
  private def _host_directory(configured: Boolean): Path = {
    val workroot = Files.createDirectories(Paths.get("target/cncf/work/runtime-process-bootstrap").toAbsolutePath.normalize)
    val cwd = Files.createTempDirectory(workroot, "episode-")
    if (configured) {
      val directory = Files.createDirectories(cwd.resolve(".textus"))
      Files.writeString(directory.resolve("config.json"), Json.obj(_key -> Json.fromString(_text)).noSpaces)
    }
    cwd
  }
  private def _action_scope(root: ScopeContext, id: ComponentId): ScopeContext = {
    val component = Component.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty)
    Component.Context("same-display-label", root, component, ComponentOrigin.Embed)
      .createChildScope(ScopeKind.Service, "workflow").createChildScope(ScopeKind.Action, "advance")
  }

  "Canonical Process Execution bootstrap" should {
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
