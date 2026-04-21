package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.value.BaseContent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemOperationAuthorizationSpec extends AnyWordSpec with Matchers {
  "Subsystem operation dispatch" should {
    "enforce operation authorization before dispatch for anonymous command-style requests" in {
      val subsystem = _subsystem(OperationMode.Production)
      val request = Request.of(
        component = "admin",
        service = "system",
        operation = "ping"
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[_]]
    }

    "allow the same admin operation in production when ingress security resolves an authenticated subject" in {
      val subsystem = _subsystem(OperationMode.Production)
      val request = Request.of(
        component = "admin",
        service = "system",
        operation = "ping",
        properties = List(
          Property("principalId", "admin-test", None),
          Property("cncf.security.privilege", "user", None)
        )
      )

      subsystem.executeOperationResponse(request) match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value.toString should include ("goldenport-cncf")
        case other =>
          fail(s"expected authenticated admin ping to execute but got $other")
      }
    }

    "allow anonymous admin dispatch in develop mode when the operation parameters permit it" in {
      val subsystem = _subsystem(OperationMode.Develop)
      val request = Request.of(
        component = "admin",
        service = "system",
        operation = "ping"
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Success[_]]
    }

    "enforce a descriptor-provided operation authorization rule for operations without a provider" in {
      val subsystem = TestComponentFactory.subsystemWithConfig(
        Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)
        ),
        name = "subsystem-operation-authorization-descriptor"
      )
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<test>"),
        subsystemName = "subsystem-operation-authorization-descriptor",
        componentBindings = Vector(GenericSubsystemComponentBinding("domain")),
        operationAuthorization = Map(
          "domain.entity.createPerson" ->
            org.goldenport.cncf.security.OperationAuthorizationRule(
              allowAnonymous = true,
              anonymousOperationModes = Vector(OperationMode.Develop, OperationMode.Test)
            )
        )
      )
      val op = spec.OperationDefinition(
        content = BaseContent.simple("createPerson"),
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )
      val service = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(op))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val domain = TestComponentFactory.create("domain", protocol, subsystem = subsystem)
      subsystem.withDescriptor(descriptor).add(Vector(domain))
      val request = Request.of(
        component = "domain",
        service = "entity",
        operation = "createPerson"
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[_]]
    }

    "enforce a CML operation authorization rule carried by generated component metadata" in {
      val subsystem = TestComponentFactory.subsystemWithConfig(
        Map(
          RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)
        ),
        name = "subsystem-operation-authorization-cml"
      )
      val domain = _cml_component(
        subsystem,
        OperationAuthorizationRule(
          allowAnonymous = true,
          anonymousOperationModes = Vector(OperationMode.Develop, OperationMode.Test)
        )
      )
      subsystem.add(Vector(domain))
      val request = Request.of(
        component = "domain",
        service = "entity",
        operation = "createPerson"
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _subsystem(operationMode: OperationMode): Subsystem = {
    val subsystem = TestComponentFactory.subsystemWithConfig(
      Map(
        RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue(operationMode.name),
        RuntimeConfig.WebDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("true")
      ),
      name = s"subsystem-operation-authorization-${operationMode.name}"
    )
    val admin = AdminComponent.Factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin)).primary
    subsystem.add(admin)
  }

  private def _cml_component(
    subsystem: Subsystem,
    rule: OperationAuthorizationRule
  ): Component = {
    val operation = _CmlActionOperation("createPerson")
    val service = spec.ServiceDefinition(
      name = "entity",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
    )
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = "createPerson",
            kind = "COMMAND",
            inputType = "CreatePerson",
            outputType = "CreatePersonResult",
            inputValueKind = "COMMAND_VALUE",
            operationAuthorization = Some(rule)
          )
        )
    }
    val componentId = ComponentId("domain")
    val core = Component.Core.create(
      name = "domain",
      componentid = componentId,
      instanceid = ComponentInstanceId.default(componentId),
      protocol = protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Main))
  }
}

private final case class _CmlActionOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_CmlAction(req))
}

private final case class _CmlAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _CmlActionCall(core)
}

private final case class _CmlActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar("cml-ok"))
}
