package org.goldenport.cncf.job

import java.util.concurrent.atomic.AtomicBoolean
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobCommandSyncAndTaskFirstSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Command Job execution path" should {
    "support explicit synchronous wait for command result via JobId" in {
      Given("a component and command action submitted as job")
      TestComponentFactory.withEmptySubsystem("job_command_sync_task_first_spec") { subsystem =>
      val component = _component(subsystem)
      val ctx = ExecutionContext.test()
      val action = _command_action("sync", "Command(sync)")
      val task = ActionTask(ActionId.generate(), action, component.actionEngine, Some(component))
      val jobid = component.logic.submitJob(List(task), ctx).toOption.get

      When("awaiting command result explicitly")
      val result = component.logic.awaitJobResult(jobid)

      Then("response is returned synchronously with succeeded status")
      result shouldBe Consequence.success(OperationResponse.Scalar("Command(sync)"))
      component.logic.getJobStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      }
    }

    "keep task-first invariant for command execution path" in {
      Given("a command action executed through component logic")
      TestComponentFactory.withEmptySubsystem("job_command_sync_task_first_spec") { subsystem =>
      val component = _component(subsystem)
      val ctx = ExecutionContext.test()
      val executed = new AtomicBoolean(false)
      val action = new CommandAction() {
        val request = org.goldenport.protocol.Request.ofOperation("taskfirst")
        override def createCall(core: ActionCall.Core): ActionCall = {
          val self = this
          val c = core
          new ActionCall {
            override val core: ActionCall.Core = c
            override def action: Action = self
            def execute(): Consequence[OperationResponse] = {
              executed.set(true)
              Consequence.success(OperationResponse.Scalar("ok"))
            }
          }
        }
      }
      val response = component.logic.executeAction(action, ctx)

      When("resolving returned job id and waiting task completion")
      val jobid = response.toOption.flatMap {
        case OperationResponse.Scalar(v) => JobId.parse(v.toString).toOption
        case _ => None
      }.getOrElse(fail("command response should be a JobId scalar"))
      _await(executed)

      Then("execution remains under Job lifecycle and is retrievable by JobId")
      jobid.value.nonEmpty shouldBe true
      executed.get() shouldBe true
      }
    }
  }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem
  ): Component = {
    val component = new Component() {}
    val id = ComponentId("job_command_sync_task_first_spec")
    val core = Component.Core.create(
      name = "job_command_sync_task_first_spec",
      componentid = id,
      instanceid = ComponentInstanceId.default(id),
      protocol = Protocol.empty,
      jobEngine = subsystem.jobEngine
    )
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }

  private def _command_action(actionname: String, value: String): CommandAction =
    new CommandAction() {
      val request = org.goldenport.protocol.Request.ofOperation(actionname)
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

  private def _await(
    predicate: AtomicBoolean,
    timeoutMillis: Long = 3000L
  ): Unit = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!predicate.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L)
    }
    predicate.get() shouldBe true
  }
}
