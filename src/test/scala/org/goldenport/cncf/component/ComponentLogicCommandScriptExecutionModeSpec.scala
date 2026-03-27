package org.goldenport.cncf.component

import java.util.concurrent.atomic.AtomicBoolean
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.dsl.script.ScriptAction
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 28, 2026
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

    "allow framework meta parameter to force SyncJob for command action" in {
      val component = TestComponentFactory.create("framework_meta_sync_job", Protocol.empty)
      val action = _command_action("meta_sync", "ok")
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(),
        CommandExecutionMode.SyncJob
      )

      withRuntimeMode(RunMode.Command) {
        Given("a command action with framework commandExecutionMode=SyncJob")
        When("executing through ComponentLogic")
        val result = component.logic.executeAction(action, ctx)

        Then("result is awaited synchronously and completed under job lifecycle")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 1, 0)
      }
    }

    "allow framework meta parameter to run SyncJob with async interface" in {
      val component = TestComponentFactory.create("framework_meta_sync_job_async_interface", Protocol.empty)
      val executed = new AtomicBoolean(false)
      val action = _command_action("meta_sync_async_interface", "ok", executed)
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(),
        CommandExecutionMode.SyncJobAsyncInterface
      )

      withRuntimeMode(RunMode.Command) {
        Given("a command action with framework commandExecutionMode=SyncJobAsyncInterface")
        When("executing through ComponentLogic")
        val response = component.logic.executeAction(action, ctx).toOption.getOrElse(fail("execution failed"))
        val jobid = response match {
          case OperationResponse.Scalar(v) =>
            JobId.parse(v.toString).toOption.getOrElse(fail("response is not JobId scalar"))
          case _ =>
            fail("response is not scalar")
        }

        Then("interface returns JobId while execution already completed synchronously")
        response should not be OperationResponse.Scalar("ok")
        jobid.value.nonEmpty shouldBe true
        executed.get() shouldBe true
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

  private def _command_action(
    operation: String,
    value: String
  ): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(operation)

      override def createCall(core: ActionCall.Core): ActionCall = {
        val self = this
        val c = core
        new ActionCall {
          override val core: ActionCall.Core = c
          override def action: Action = self
          def execute(): Consequence[OperationResponse] =
            Consequence.success(OperationResponse.Scalar(value))
        }
      }
    }

  private def _command_action(
    operation: String,
    value: String,
    executed: AtomicBoolean
  ): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(operation)

      override def createCall(core: ActionCall.Core): ActionCall = {
        val self = this
        val c = core
        new ActionCall {
          override val core: ActionCall.Core = c
          override def action: Action = self
          def execute(): Consequence[OperationResponse] = {
            executed.set(true)
            Consequence.success(OperationResponse.Scalar(value))
          }
        }
      }
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
      commandExecutionMode = None,
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
