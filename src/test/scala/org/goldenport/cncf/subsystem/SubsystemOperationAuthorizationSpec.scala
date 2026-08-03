package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.admin.AdminComponent
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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Jul. 30, 2026
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

    "deny production admin operation by default even when ingress security resolves a system admin subject" in {
      val subsystem = _subsystem(OperationMode.Production)
      val request = Request.of(
        component = "admin",
        service = "system",
        operation = "ping",
        properties = List(
          Property("principalId", "admin-test", None),
          Property("cncf.security.privilege", "system", None),
          Property("role", "system_admin", None)
        )
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[_]]
    }

    "deny production admin operation when enabled but ingress security only resolved fallback system admin fields" in {
      val subsystem = _subsystem(
        OperationMode.Production,
        RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
      )
      val request = Request.of(
        component = "admin",
        service = "system",
        operation = "ping",
        properties = List(
          Property("principalId", "admin-test", None),
          Property("cncf.security.privilege", "system", None),
          Property("role", "system_admin", None)
        )
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[_]]
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
        component = "domain",
        service = "entity",
        operation = "createPerson"
      )

      subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _subsystem(
    operationMode: OperationMode,
    entries: (String, ConfigurationValue)*
  ): Subsystem = {
    val values = Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(operationMode.name),
        RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.StringValue("true")
      ) ++ entries.toMap
    val subsystem = TestComponentFactory.subsystemWithConfig(
      values,
      name = s"subsystem-operation-authorization-${operationMode.name}"
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
