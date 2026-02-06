package org.goldenport.cncf.component.protocol

import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen

import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.model.value.BaseContent
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition, OperationDefinition => CoreOperationDefinition}
import org.goldenport.process.{ShellCommand, ShellCommandExecutor, ShellCommandResult}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{CommandParameterMappingRule, ShellCommandComponent}
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Feb.  6, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class ShellCommandOperationDefinitionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  "ShellCommandCall" should {
    "assemble the expected command vector" in {
      Given("a shell specification where the mapping rule is deterministic")
      val spec =
        shellSpec(Vector("bin", "shell"), new TestMappingRule("runme", Vector("alpha", "beta")))
      val (call, executor) = buildCall(spec, ShellCommandResult(
        exitCode = 0,
        stdout = Bag.empty,
        stderr = Bag.empty,
        files = Map.empty,
        directories = Map.empty
      ))

      When("the action call executes")
      call.execute()

      Then("the executor receives the composed vector")
      executor.lastCommand shouldBe Some(
        ShellCommand(
          command = Vector("bin", "shell", "runme", "alpha", "beta"),
          workDir = spec.workDirHint,
          env = spec.envHint,
          directive = ShellCommand.Directive.empty
        )
      )
    }

    "still return success for non-zero exit codes" in {
      Given("an executor that reports failure")
      val spec =
        shellSpec(Vector("bin", "fail"), new TestMappingRule("fail", Vector.empty))
      val result = ShellCommandResult(
        exitCode = 13,
        stdout = Bag.text("nonzero", StandardCharsets.UTF_8),
        stderr = Bag.text("error", StandardCharsets.UTF_8),
        files = Map.empty,
        directories = Map.empty
      )
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
      val spec =
        shellSpec(Vector("bin"), new TestMappingRule("noop", Vector.empty))
      val (call, _) = buildCall(spec, ShellCommandResult(
        exitCode = 0,
        stdout = Bag.empty,
        stderr = Bag.empty,
        files = Map.empty,
        directories = Map.empty
      ))
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
      val spec =
        shellSpec(
          Vector("bin", "hint"),
          new TestMappingRule("hinted", Vector.empty),
          workDirHint = Some(Paths.get("/tmp/hint")),
          envHint = Map("LC" -> "value")
        )
      val (call, executor) = buildCall(spec, ShellCommandResult(
        exitCode = 0,
        stdout = Bag.empty,
        stderr = Bag.empty,
        files = Map.empty,
        directories = Map.empty
      ))

      When("execute is invoked")
      call.execute()

      Then("the hints are reflected in the ShellCommand")
      executor.lastCommand shouldBe Some(
        ShellCommand(
          command = Vector("bin", "hint", "hinted"),
          workDir = spec.workDirHint,
          env = spec.envHint,
          directive = ShellCommand.Directive.empty
        )
      )
    }
  }

  private def buildCall(
    spec: ShellCommandSpecification,
    result: ShellCommandResult
  ): (ShellCommandCall, FakeCommandExecutor) = {
    val operation = new TestShellCommandOperationDefinition(spec)
    val request = Request.ofOperation("shell")
    val action = operation.createOperationRequest(request).toOption.get
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

  private class FakeCommandExecutor(result: ShellCommandResult)
      extends ShellCommandExecutor {
    var lastCommand: Option[ShellCommand] = None

    override def execute(command: ShellCommand): Consequence[ShellCommandResult] = {
      lastCommand = Some(command)
      Consequence.success(result)
    }
  }

  private class FakeShellCommandComponent(
    executorRef: FakeCommandExecutor
  ) extends ShellCommandComponent {
    override protected def shell_Executor: ShellCommandExecutor = executorRef
    override def commandExecutor: ShellCommandExecutor = executorRef
    override protected def base_Command(operation: CoreOperationDefinition): Vector[String] = Vector.empty
  }

  private class TestShellCommandOperationDefinition(
    override val shellSpecification: ShellCommandSpecification
  ) extends ShellCommandOperationDefinition {
    private val delegate =
      OperationDefinition(
        BaseContent.simple("shell"),
        RequestDefinition(),
        ResponseDefinition()
      )

    override def specification: OperationDefinition.Specification =
      delegate.specification
  }

  private class TestMappingRule(
    commandname: String,
    args: Vector[String]
  ) extends CommandParameterMappingRule {
    override def commandName(operation: CoreOperationDefinition): String = commandname

    override def parameters(
      operation: CoreOperationDefinition,
      request: Request
    ): CommandParameterMappingRule.Parameters =
      CommandParameterMappingRule.Parameters(args, ShellCommand.Directive.empty)
  }

  private def shellSpec(
    baseCommand: Vector[String],
    mappingRule: CommandParameterMappingRule,
    workDirHint: Option[Path] = None,
    envHint: Map[String, String] = Map.empty
  ): ShellCommandSpecification =
    ShellCommandSpecification.Instance(
      ShellCommandSpecification.Core(
        baseCommand,
        mappingRule,
        workDirHint,
        envHint
      )
    )
}
