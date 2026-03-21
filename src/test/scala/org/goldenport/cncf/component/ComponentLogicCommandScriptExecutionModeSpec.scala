package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.dsl.script.ScriptAction
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentLogicCommandScriptExecutionModeSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentLogic command/script execution mode" should {
    "use SyncDirectNoJob for command + SCRIPT combination" in {
      val component = TestComponentFactory.create("script_execution_mode", Protocol.empty)
      val action = _script_action()

      withRuntimeMode(RunMode.Command) {
        Given("SCRIPT command action")
        When("executing through ComponentLogic in command runtime mode")
        val result = component.logic.executeAction(action)

        Then("response is returned and no job is recorded")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 0, 0)
      }
    }

    "use ScriptAction default SyncJob outside command mode" in {
      val component = TestComponentFactory.create("script_execution_mode_non_command", Protocol.empty)
      val action = _script_action()

      withRuntimeMode(RunMode.Script) {
        Given("SCRIPT action with default SyncJob")
        When("executing through ComponentLogic in script runtime mode")
        val result = component.logic.executeAction(action)

        Then("response is returned and one completed job is recorded")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 1, 0)
      }
    }
  }

  private def _script_action(): ScriptAction = {
    val request = Request.of(
      component = "SCRIPT",
      service = "DEFAULT",
      operation = "RUN"
    )
    ScriptAction("script_RUN", request, _ => "ok")
  }

  private def _metrics(component: Component): (Int, Int, Int, Int) =
    component.jobEngine.metrics match {
      case Some(metrics) =>
        (metrics.running, metrics.queued, metrics.completed, metrics.failed)
      case None =>
        fail("JobEngine metrics is unavailable")
    }

  private def withRuntimeMode[T](
    mode: RunMode
  )(body: => T): T = {
    val execution = ExecutionContext.create()
    val httpDriver = FakeHttpDriver.okText("noop")
    val runtimeConfig = RuntimeConfig.default.copy(
      httpDriver = httpDriver,
      mode = mode
    )
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "component-logic-command-script-mode-spec",
      parent = None,
      observabilityContext = execution.observability,
      httpDriverOption = Some(httpDriver)
    ).core
    val context = new GlobalRuntimeContext(
      core = core,
      config = runtimeConfig,
      aliasResolver = AliasResolver.empty,
      runtimeMode = mode,
      runtimeVersion = CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = CncfVersion.current
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(context)
    try body
    finally GlobalRuntimeContext.current = previous
  }
}
