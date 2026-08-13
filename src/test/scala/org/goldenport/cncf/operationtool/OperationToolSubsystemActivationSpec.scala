package org.goldenport.cncf.operationtool

import java.nio.file.{Files, Path}

import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.GenericSubsystemFactory
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for subsystem-owned Operation tool activation.
 *
 * @since   Jul. 21, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationToolSubsystemActivationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Subsystem Operation tool activation" should {
    "activate configured exact admissions during generic subsystem startup" in {
      Given("a generic subsystem selecting one internal Operation tool policy")
      val policy = _policy_file()
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.OPERATION_TOOL_POLICY_KEY -> ConfigurationValue.StringValue(policy.toString)
        )),
        ConfigurationTrace.empty
      )
      val context = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = "operation_tool_runtime_factory",
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )

      When("generic subsystem assembly completes")
      val subsystem = GenericSubsystemFactory.defaultWithScope(
        subsystemName = "operation_tool_runtime_factory",
        context = context,
        mode = Some(RunMode.Command),
        configuration = configuration,
        aliasResolver = AliasResolver.empty
      )

      Then("the subsystem retains the admitted in-process tool set")
      subsystem.operationToolSetIds.map(_.print) shouldBe Vector("builtin-tools")
      subsystem.shutdownC().isSuccess shouldBe true
    }

    "install admitted services into existing and subsequently added sockets" in {
      Given("one admitted Operation and two consumer-owned sockets")
      given ExecutionContext = ExecutionContext.create()
      val toolsetid = OperationToolSetId.parseC("builtin-tools").toOption.get
      val firstsocket = _socket(toolsetid)
      val secondsocket = _socket(toolsetid)
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      subsystem.add(_component("first_consumer", firstsocket))

      When("the subsystem activates the policy and later adds another consumer")
      val activation = subsystem.activateOperationToolRuntimeC(_policy_file())
      subsystem.add(_component("second_consumer", secondsocket))

      Then("both sockets receive the same admitted service")
      activation.isSuccess shouldBe true
      firstsocket.service(toolsetid).isSuccess shouldBe true
      secondsocket.service(toolsetid).isSuccess shouldBe true
      subsystem.shutdownC().isSuccess shouldBe true
    }

    "retain no registry when a required Operation is absent" in {
      Given("a policy naming an Operation outside the assembled subsystem")
      given ExecutionContext = ExecutionContext.create()
      val path = _policy_file("org.goldenport.cncf.Missing.service.operation")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))

      When("runtime activation validates exact admission")
      val result = subsystem.activateOperationToolRuntimeC(path)

      Then("activation fails atomically")
      result.isFaillure shouldBe true
      subsystem.operationToolSetIds shouldBe Vector.empty
      subsystem.shutdownC().isSuccess shouldBe true
    }
  }

  private def _policy_file(
    operation: String = "org.goldenport.cncf.Admin.system.ping"
  ): Path = {
    val directory = Files.createTempDirectory("cncf-operation-tool-subsystem")
    val policy = directory.resolve("operation-tools.yaml")
    Files.writeString(policy,
      s"""toolSets:
         |  - id: builtin-tools
         |    operations:
         |      - $operation
         |""".stripMargin
    )
    policy
  }

  private def _socket(toolsetid: OperationToolSetId): OperationToolSocket =
    OperationToolSocket.createC(Vector(OperationToolRequirement(toolsetid))).toOption.get

  private def _component(
    name: String,
    socket: OperationToolSocket
  ): Component =
    TestComponentFactory
      .create(name, Protocol.empty)
      .withPort(Component.Port.input(socket))
}
