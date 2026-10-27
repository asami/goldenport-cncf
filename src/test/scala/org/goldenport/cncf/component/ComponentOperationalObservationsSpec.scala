package org.goldenport.cncf.component

import io.circe.parser.parse
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.operationalobservation.OperationalObservationFixtures
import org.goldenport.cncf.projection.{HelpProjection, OpenApiProjection}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 24, 2026
 * @version Oct. 26, 2026
 */
final class ComponentOperationalObservationsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import OperationalObservationFixtures.*
  private def _component(context: ExecutionContext): Component =
    context.cncfCore.scope.asInstanceOf[Component.Context].component
  private def _request(component: Component, properties: List[Property] = Nil): Request =
    Request.of(component = component.name, service = "system", operation = "operationalObservations").copy(properties = properties)
  private def _execute(context: ExecutionContext, request: Request): Consequence[OperationResponse] =
    TestComponentFactory.withAdmittedEmptySubsystem("operational-observations") { subsystem =>
      val selected = _component(context)
      val component = TestComponentFactory.create(selected.name, selected.protocol, subsystem = subsystem)
      subsystem.add(component)
      val scoped = context.withScope(component.scopeContext)
      lazy val active: ExecutionContext = ExecutionContext.withRuntimeContext(
        scoped, scoped.runtime.withUnitOfWorkContext(active))
      component.logic.makeOperationRequest(request).flatMap {
        case action: Action => component.logic.executeAction(action, active)
        case _ => Consequence.operationInvalid("Expected the normal query Action")
      }
    }

  "The normal Component operationalObservations query" should {
    "be discoverable in Help and the ordinary GET projection" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1; an actual Component with default system operations")
      val component = _component(context())
      When("ordinary operation metadata is projected")
      val operation = component.protocol.services.services.find(_.name == "system").get.operations.operations.toVector
        .find(_.name == "operationalObservations").get
      val openapi = parse(OpenApiProjection.projectComponent(component)).toOption.get
      Then("Help and HTTP use the same operation definition and only the limit parameter is exposed")
      operation.specification.request.parameters.map(_.name) shouldBe List("limit")
      HelpProjection.projectModel(component, Some(owner.name + ".system")).toString should include ("operationalObservations")
      val paths = openapi.hcursor.downField("paths").focus.flatMap(_.asObject).get
      paths.toVector.exists { case (path, definition) =>
        path.toLowerCase.contains("operational") && definition.hcursor.downField("get").succeeded
      } shouldBe true
    }

    "execute through ordinary resolution, ActionCall and UoW with actual producer identity" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1,R2,R6; an authenticated administrative Component context")
      val execution = context()
      val component = _component(execution)
      When("the ordinary query is resolved and executed with the same ExecutionContext")
      val result = _execute(execution, _request(component)).TAKE
      Then("the business wire is the R6 not-configured response and has no invented profile or inventory")
      val json = result match {
        case OperationResponse.Json(value) => value
        case _ => fail("Expected fixed camelCase business JSON through the normal OperationResponse")
      }
      json.hcursor.get[Int]("protocolVersion").toOption shouldBe Some(1)
      json.hcursor.downField("configuration").get[String]("state").toOption shouldBe Some("not-configured")
      json.hcursor.downField("configuration").downField("profile").succeeded shouldBe false
      json.hcursor.downField("configuration").downField("healthContracts").succeeded shouldBe false
      json.hcursor.downField("producer").get[String]("componentId").toOption shouldBe Some(owner.name)
      json.hcursor.downField("producer").get[String]("instanceId").toOption shouldBe Some(component.instanceId.canonicalKey)
      json.hcursor.get[Int]("total").toOption shouldBe Some(0)
      json.hcursor.get[Vector[io.circe.Json]]("data").toOption shouldBe Some(Vector.empty)
    }

    "reject unauthorized subjects and every unsupported request selector" in {
      Given("Spec: docs/spec/operational-observation-read-model.md; Rules: R1,R7; anonymous, registration-only and malformed requests")
      val anonymous = context(privilege = SecurityContext.Privilege.Anonymous, authenticated = false)
      val ordinary = context(privilege = SecurityContext.Privilege.User)
      When("the same normal operation is requested")
      Then("no producer result is granted through request overrides or registration privilege")
      Vector(anonymous, ordinary).foreach { execution =>
        _execute(execution, _request(_component(execution))) match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.taxonomy shouldBe
              Conclusion.securityPermissionDenied("administrative read required").observation.taxonomy
          case _ => fail("Expected an authorization failure from the normal Component operation")
        }
      }
      val component = _component(context())
      val property = Prop.forAll(Gen.oneOf("profile", "url", "selector", "credentials", "containerId")) { name =>
        component.logic.makeOperationRequest(_request(component, List(Property(name, "private", None)))).isFaillure
      }
      Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property).passed shouldBe true
      Vector("0", "101", "1.5", "null", "-1").foreach { value =>
        component.logic.makeOperationRequest(_request(component, List(Property("limit", value, None)))).isFaillure shouldBe true
      }
      component.logic.makeOperationRequest(_request(component, List(Property("limit", "1", None), Property("limit", "2", None))))
        .isFaillure shouldBe true
    }
  }
}
