package org.goldenport.cncf.protocol.spec

import java.nio.file.Paths

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen

import org.goldenport.Consequence
import org.goldenport.model.value.BaseContent
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition, OperationDefinition => CoreOperationDefinition}
import org.goldenport.process.{ExternalCommand, ExternalCommandExecutor, ExternalCommandResult}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{CommandParameterMappingRule, ShellCommandComponent}
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Feb.  5, 2026
 * @version Feb.  5, 2026
 * @author  ASAMI, Tomoharu
 */
class ShellCommandOperationDefinitionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  "ShellCommandCall" should {
    "assemble the expected command vector" in {
      Given("a shell specification where the mapping rule is deterministic")
      val spec = ShellCommandSpecification(
        baseCommand = Vector("bin", "shell"),
        mappingRule = new TestMappingRule("runme", Vector("alpha", "beta"))
      )
      val (call, executor) = buildCall(spec, ExternalCommandResult(0, "out", "err"))

      When("the action call executes")
      call.execute()

      Then("the executor receives the composed vector")
      executor.lastCommand shouldBe Some(
        ExternalCommand(
          command = Vector("bin", "shell", "runme", "alpha", "beta"),
          workDir = spec.workDirHint,
          env = spec.envHint
        )
      )
    }

    "still return success for non-zero exit codes" in {
      Given("an executor that reports failure")
      val spec = ShellCommandSpecification(
        baseCommand = Vector("bin", "fail"),
        mappingRule = new TestMappingRule("fail", Vector.empty)
      )
      val result = ExternalCommandResult(13, "nonzero", "error")
      val (call, _) = buildCall(spec, result)

      When("execute is invoked")
      val response = call.execute()

      Then("the OperationResponse is successful with stdout")
      response match {
        case Consequence.Success(OperationResponse.Scalar(body)) =>
          body shouldBe "nonzero"
        case other =>
          fail(s"expected success response, got $other")
      }
    }

    "fail when no ShellCommandComponent is present" in {
      Given("a shell call without a component")
      val spec = ShellCommandSpecification(
        baseCommand = Vector("bin"),
        mappingRule = new TestMappingRule("noop", Vector.empty)
      )
      val (call, _) = buildCall(spec, ExternalCommandResult(0, "", ""))
      val core = ActionCall.Core(
        call.core.action,
        ExecutionContext.test(),
        None,
        None
      )
      val brokenCall = call.core.action.createCall(core)

      When("execute is invoked")
      val response = brokenCall.execute()

      Then("a failure consequence is returned")
      response shouldBe a[Consequence.Failure[_]]
    }

    "pass hints to the executor" in {
      Given("a specification with env and workDir hints")
      val spec = ShellCommandSpecification(
        baseCommand = Vector("bin", "hint"),
        mappingRule = new TestMappingRule("hinted", Vector.empty),
        workDirHint = Some(Paths.get("/tmp/hint")),
        envHint = Map("LC" -> "value")
      )
      val (call, executor) = buildCall(spec, ExternalCommandResult(0, "", ""))

      When("execute is invoked")
      call.execute()

      Then("the hints are reflected in the ExternalCommand")
      executor.lastCommand shouldBe Some(
        ExternalCommand(
          command = Vector("bin", "hint", "hinted"),
          workDir = spec.workDirHint,
          env = spec.envHint
        )
      )
    }
  }

  private def buildCall(
    spec: ShellCommandSpecification,
    result: ExternalCommandResult
  ): (ShellCommandCall, FakeCommandExecutor) = {
    val operation = new TestShellCommandOperationDefinition(spec)
    val request = Request.ofOperation("shell")
    val action = operation.createOperationRequest(request).take
    val executor = new FakeCommandExecutor(result)
    val component = new FakeShellCommandComponent(executor)
    val core = ActionCall.Core(
      action,
      ExecutionContext.test(),
      Some(component),
      None
    )
    action.createCall(core) match {
      case call: ShellCommandCall => (call, executor)
      case other => fail(s"unexpected action call: $other")
    }
  }

  private class FakeCommandExecutor(result: ExternalCommandResult)
      extends ExternalCommandExecutor {
    var lastCommand: Option[ExternalCommand] = None

    override def execute(command: ExternalCommand): Consequence[ExternalCommandResult] = {
      lastCommand = Some(command)
      Consequence.success(result)
    }
  }

  private class FakeShellCommandComponent(
    executorRef: FakeCommandExecutor
  ) extends ShellCommandComponent {
    override protected def executor: ExternalCommandExecutor = executorRef
    override def commandExecutor: ExternalCommandExecutor = executorRef
    override protected def baseCommand(operation: CoreOperationDefinition): Vector[String] = Vector.empty
  }

  private class TestShellCommandOperationDefinition(
    override val shellSpecification: ShellCommandSpecification
  ) extends ShellCommandOperationDefinition {
    override val specification: OperationDefinition.Specification =
      new OperationDefinition.Specification(
        BaseContent.simple("shell"),
        RequestDefinition(),
        ResponseDefinition()
      )
  }

  private class TestMappingRule(
    commandname: String,
    args: Vector[String]
  ) extends CommandParameterMappingRule {
    override def commandName(operation: CoreOperationDefinition): String = commandname
    override def arguments(operation: CoreOperationDefinition): Vector[String] = args
  }
}
