package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.config.{CncfConfigurationCandidateDecoder, CncfConfigurationDocumentBatch, CncfConfigurationDocumentLocation, CncfConfigurationResolutionContext, CncfConfigurationTarget, OperationMode, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.value.BaseContent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemOperationAuthorizationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Subsystem operation dispatch" should {
    "enforce operation authorization before dispatch for anonymous command-style requests" in {
      Given("a production subsystem and an anonymous admin ping request")
      val subsystem = _subsystem(OperationMode.Production)
      val request = Request.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "system",
        operation = "ping"
      )

      When("the request is dispatched")
      val result = subsystem.executeOperationResponse(request)
      Then("authorization rejects the request before dispatch")
      result shouldBe a[Consequence.Failure[_]]
    }

    "deny production admin operation by default even when ingress security resolves a system admin subject" in {
      Given("a production subsystem with fallback system-admin ingress fields")
      val subsystem = _subsystem(OperationMode.Production)
      val request = Request.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "system",
        operation = "ping",
        properties = List(
          Property("principalId", "admin-test", None),
          Property("cncf.security.privilege", "system", None),
          Property("role", "system_admin", None)
        )
      )

      When("the request is dispatched")
      val result = subsystem.executeOperationResponse(request)
      Then("the production default still denies the operation")
      result shouldBe a[Consequence.Failure[_]]
    }

    "deny production admin operation when enabled but ingress security only resolved fallback system admin fields" in {
      Given("a production subsystem with web admin enabled and fallback identity fields")
      val subsystem = _subsystem(
        OperationMode.Production,
        RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
      )
      val request = Request.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "system",
        operation = "ping",
        properties = List(
          Property("principalId", "admin-test", None),
          Property("cncf.security.privilege", "system", None),
          Property("role", "system_admin", None)
        )
      )

      When("the request is dispatched")
      val result = subsystem.executeOperationResponse(request)
      Then("fallback identity fields do not bypass authorization")
      result shouldBe a[Consequence.Failure[_]]
    }

    "allow anonymous admin dispatch in develop mode when the operation parameters permit it" in {
      Given("a develop subsystem that permits anonymous admin dispatch")
      val subsystem = _subsystem(OperationMode.Develop)
      val request = Request.of(
        component = BuiltinComponentIdentity.ADMIN.name,
        service = "system",
        operation = "ping"
      )

      When("the request is dispatched")
      val result = subsystem.executeOperationResponse(request)
      Then("the operation succeeds")
      result shouldBe a[Consequence.Success[_]]
    }

    "enforce a descriptor-provided operation authorization rule for operations without a provider" in {
      Given("a production subsystem with a descriptor authorization rule")
      val subsystem = TestComponentFactory.subsystemWithConfig(
        Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)
        ),
        name = "subsystem-operation-authorization-descriptor"
      )
      _admit_runtime_operation_policy(subsystem, Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)
      ))
      val descriptor = GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<test>"),
        subsystemName = "subsystem-operation-authorization-descriptor",
        componentBindings = Vector(GenericSubsystemComponentBinding("org.goldenport.cncf.test.Domain")),
        operationAuthorization = Map(
          "org.goldenport.cncf.test.Domain.entity.createPerson" ->
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
        component = "org.goldenport.cncf.test.Domain",
        service = "entity",
        operation = "createPerson"
      )

      When("the descriptor-bound operation is dispatched")
      val result = subsystem.executeOperationResponse(request)
      Then("the descriptor rule rejects the request")
      result shouldBe a[Consequence.Failure[_]]
    }

    "enforce a CML operation authorization rule carried by generated component metadata" in {
      Given("a production subsystem with generated CML authorization metadata")
      val subsystem = TestComponentFactory.subsystemWithConfig(
        Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)
        ),
        name = "subsystem-operation-authorization-cml"
      )
      _admit_runtime_operation_policy(subsystem, Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)
      ))
      val domain = _cml_component(
        subsystem,
        OperationAuthorizationRule(
          allowAnonymous = true,
          anonymousOperationModes = Vector(OperationMode.Develop, OperationMode.Test)
        )
      )
      subsystem.add(Vector(domain))
      val request = Request.of(
        component = "org.goldenport.cncf.test.Domain",
        service = "entity",
        operation = "createPerson"
      )

      When("the generated operation is dispatched")
      val result = subsystem.executeOperationResponse(request)
      Then("the generated authorization rule rejects the request")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _subsystem(
    operationmode: OperationMode,
    entries: (String, ConfigurationValue)*
  ): Subsystem = {
    val values = Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(operationmode.name),
        RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("true")
      ) ++ entries.toMap
    val subsystem = TestComponentFactory.subsystemWithConfig(
      values,
      name = s"subsystem-operation-authorization-${operationmode.name}"
    )
    _admit_runtime_operation_policy(subsystem, values)
    val admin = AdminComponent.Factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin)).primary
    subsystem.add(admin)
  }

  private def _admit_runtime_operation_policy(
    subsystem: Subsystem,
    values: Map[String, ConfigurationValue]
  ): Unit = {
    val identity = _take(SubsystemInstanceId.create("platform", "default"))
    val target = _take(CncfConfigurationTarget.SubsystemInstance.create(identity))
    val candidates = _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
      CncfConfigurationDocumentBatch(
        new CncfConfigurationDocumentLocation.SubsystemInstance(target),
        _take(ConfigurationSourceAdmission.create(
          ConfigurationOrigin.Home,
          "home",
          "subsystem-operation-authorization-spec",
          10,
          "subsystem-operation-authorization-spec",
          () => Consequence.success(ConfigurationDocument.Object(values.toVector.map { case (key, value) =>
            ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value))
          }))
        ))
      )
    )))
    val context = _take(CncfConfigurationResolutionContext.forSubsystem(identity))
    val bindings = _take(ConfigurationBindingResolver.resolve(candidates, context.generic))
    _take(subsystem.admitRuntimeConfigurationBindingsC(bindings))
  }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))

  private def _cml_component(
    subsystem: Subsystem,
    rule: OperationAuthorizationRule
  ): Component = {
    val operation = CmlActionOperation("createPerson")
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
    val componentid = ComponentId("org.goldenport.cncf.test.Domain")
    val core = Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = ComponentInstanceId.default(componentid),
      protocol = protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Main))
  }
}

private final case class CmlActionOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(CmlAction(req))
}

private final case class CmlAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    CmlActionCall(core)
}

private final case class CmlActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar("cml-ok"))
}
