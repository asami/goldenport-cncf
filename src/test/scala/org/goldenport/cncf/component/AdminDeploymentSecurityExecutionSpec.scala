package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.{CncfConfigurationCandidateDecoder, CncfConfigurationDocumentBatch, CncfConfigurationDocumentLocation, CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 *  version Apr. 11, 2026
 *  version Jul. 30, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminDeploymentSecurityExecutionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "AdminComponent" should {
    "render admitted visible values and redact confidential bindings" in {
      Given("a visible execution profile and a confidential random seed")
      val secret = "admin-diagnostic-secret"
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val bindings = _take(_collection(Vector(
        CncfConfigurationParameterCatalog.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("seeded"),
        CncfConfigurationParameterCatalog.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue(secret)
      )))
      _take(subsystem.admitRuntimeConfigurationBindingsC(bindings))
      val admin = _admin_component(subsystem)

      When("both Admin diagnostic endpoints execute against the admitted collection")
      Vector(
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.config.show",
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.variation.list"
      ).foreach { selector =>
        val text = _take(_execute(admin, _build_request(subsystem.resolver, selector))).asInstanceOf[OperationResponse.Scalar[String]].value

        Then("the visible value is retained and the confidential value is redacted")
        text should include ("textus.execution.profile")
        text should include ("seeded")
        text should include ("\"state\":\"redacted\"")
        text should not include secret
      }
    }
    "execute admin.deployment.securityMermaid requests" in {
      Given("a command subsystem with an Admin security Mermaid request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val admincomponent = _admin_component(subsystem)
      val request = _build_request(
        subsystem.resolver,
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.deployment.securityMermaid"
      )

      When("the canonical Admin security Mermaid request executes")
      val result = _execute(admincomponent, request)

      Then("the security Mermaid response describes the deployment")
      result match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include ("flowchart LR")
          text should include ("ExecutionContext(SecurityContext)")
          text should include ("ActionCall")
          text should include ("UnitOfWork")
        case other =>
          fail(s"expected mermaid scalar but got $other")
      }
    }

    "execute admin.deployment.securityMarkdown requests" in {
      Given("a command subsystem with an Admin security Markdown request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val admincomponent = _admin_component(subsystem)
      val request = _build_request(
        subsystem.resolver,
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.deployment.securityMarkdown"
      )

      When("the canonical Admin security Markdown request executes")
      val result = _execute(admincomponent, request)

      Then("the security Markdown response describes the deployment")
      result match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include ("# Security Deployment Specification")
          text should include ("## Diagram")
          text should include ("## Authentication Providers")
          text should include ("## Framework Chokepoints")
        case other =>
          fail(s"expected markdown scalar but got $other")
      }
    }

    "execute binding-derived configuration, variation list, and variation describe requests" in {
      Given("an Admin Subsystem with an admitted empty binding collection")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      subsystem.admitRuntimeConfigurationBindingsC(ConfigurationBindingCollection.empty[CncfConfigurationTarget]).isSuccess shouldBe true
      val admincomponent = _admin_component(subsystem)
      val listrequest = _build_request(
        subsystem.resolver,
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.variation.list"
      )
      val showrequest = _build_request(
        subsystem.resolver,
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.config.show"
      )
      val describerequest = _build_request(
        subsystem.resolver,
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.variation.describe"
      ).copy(
        arguments = List(Argument("key", RuntimeConfig.executionHistoryRecentLimitKey))
      )

      When("the Admin configuration and variation operations execute")
      val listresponse = _execute(admincomponent, listrequest)
      val showresponse = _execute(admincomponent, showrequest)
      val describeresponse = _execute(admincomponent, describerequest)

      Then("the diagnostic boundary and declared variation points remain available")
      listresponse match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include ("textus.configuration-binding-diagnostic.v1")
          text should include (s"key  : ${RuntimeConfig.executionHistoryRecentLimitKey}")
          text should include ("brief: Recent execution history size.")
          text should not include ("detail: Number of most recent action execution records")
        case other =>
          fail(s"expected variation list scalar but got $other")
      }

      showresponse match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include ("textus.configuration-binding-diagnostic.v1")
          text should not include ("Config Snapshot")
        case other =>
          fail(s"expected configuration diagnostic scalar but got $other")
      }

      describeresponse match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.getString("key") shouldBe Some(RuntimeConfig.executionHistoryRecentLimitKey)
          record.getString("brief") shouldBe Some("Recent execution history size.")
          record.getString("detail").exists(_.contains("Number of most recent action execution records")) shouldBe true
        case other =>
          fail(s"expected variation describe record but got $other")
      }
    }
  }

  private def _admin_component(subsystem: org.goldenport.cncf.subsystem.Subsystem): Component =
    subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
      .getOrElse(fail("admin component not found"))

  private def _execute(
    component: Component,
    request: Request
  ): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case other =>
        Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    }

  private def _build_request(
    resolver: OperationResolver,
    selector: String
  ): Request =
    resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Request.of(
          component = component,
          service = service,
          operation = operation
        )
      case other =>
        fail(s"resolver failed for $selector: $other")
    }

  private def _collection(entries: Vector[(String, ConfigurationValue)]) =
    for {
      candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(CncfConfigurationDocumentBatch(
        new CncfConfigurationDocumentLocation.SubsystemInstance(_target),
        _take(ConfigurationSourceAdmission.create(ConfigurationOrigin.Home, "home", "admin-diagnostic", 10, "admin-diagnostic", () => Consequence.success(ConfigurationDocument.Object(entries.map { case (key, value) => ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value)) }))))
      )))
      context <- CncfConfigurationResolutionContext.forSubsystem(_identity)
      collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
    } yield collection

  private def _identity: SubsystemInstanceId = _take(SubsystemInstanceId.create("platform", "default"))
  private def _target: CncfConfigurationTarget.SubsystemInstance = _take(CncfConfigurationTarget.SubsystemInstance.create(_identity))
  private def _take[A](result: Consequence[A]): A = result.getOrElse(fail(result.display))
}
