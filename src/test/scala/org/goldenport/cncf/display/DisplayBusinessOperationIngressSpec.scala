package org.goldenport.cncf.display

import io.circe.Json
import logicalui.DisplayServiceReadFixture
import org.goldenport.Consequence
import org.goldenport.cncf.action.QueryAction
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection, OpenApiProjector}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Argument, Property, Request, Switch}
import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
final class DisplayBusinessOperationIngressSpec extends AnyWordSpec with DisplayBusinessOperationFixture.Vocabulary {
  import DisplayBusinessOperationFixture.*

  "Ordinary Business metadata ingress" should {
    "registered read-only Operation" which {
      "BA5 return admitted JSON through ordinary subsystem execution and actual GET .json/envelope without invoking the target" in {
        Given("a normally bootstrapped component, a canonical Detail source and an ordinary review Operation with executable counters")
        val session = open()
        try {
          val expected = registry(session.component).TAKE.describeC(resourceType, "capture-detail").TAKE
          val originalrow = session.component.row
          val selected = operation(session.component)
          When("ordinary subsystem execution and the real in-process GET route request the metadata descriptor")
          val action = selected.createOperationRequest(request(session.component)).TAKE
          val direct = json(session.execute(request(session.component)))
          val response = session.http()
          val list = session.http("collecting-resources")
          Then("the registered query returns only the canonical inert descriptor in the normal JSON envelope, with the exact selected sources")
          action shouldBe a[QueryAction]
          selected.response.result shouldBe List(DataType.Named("OperationResponse"))
          selected.request.parameters.map(_.name) shouldBe List("resourceType", "screenId")
          selected.asInstanceOf[OpenApiOperationProjection].openApiHttpMethod shouldBe OpenApiHttpMethod.GET
          withClue(response.text) { response.status shouldBe 200 }
          withClue(list.text) { list.status shouldBe 200 }
          response.contentType shouldBe Some("application/json")
          response.data shouldBe direct
          response.json.hcursor.downField("execution").get[String]("interface-shape") shouldBe Right("json")
          val decoded = DisplayBusinessOperationCodec.decodeC(registry(session.component).TAKE, response.data.noSpaces).TAKE
          decoded.source.componentId shouldBe expected.source.componentId
          decoded.source.resourceType shouldBe expected.source.resourceType
          decoded.definition shouldBe expected.definition
          decoded.actions shouldBe expected.actions
          response.data.hcursor.downField("definition").get[String]("screenId") shouldBe Right("capture-detail")
          list.data.hcursor.downField("definition").get[String]("screenId") shouldBe Right("collecting-resources")
          list.data.hcursor.downField("actions").focus.get.asArray.get.map(_.hcursor.get[String]("scope").toOption.get) shouldBe
            Vector("screen", "screen", "item", "item")
          session.component.row shouldBe originalrow
          idle(session.component, session.target)

          When("the existing OpenAPI projector describes this installed ordinary Operation")
          val openapi = io.circe.parser.parse(OpenApiProjector.forSubsystem(session.subsystem)).toOption.get
          val path = "/rest/v1" + NamingConventions.toNormalizedPath(session.component.name, "display", "describeBusiness")
          val projected = openapi.hcursor.downField("paths").downField(path)
          Then("the opt-in method projection is GET without a new router or invocation protocol")
          projected.downField("GET").succeeded shouldBe true
          projected.downField("POST").succeeded shouldBe false
          idle(session.component, session.target)
        } finally session.close()
      }
      "BA5-read retain the exact subsequent Detail payload and read only on an explicit ordinary read" in {
        Given("the canonical resource at revision seven and a successful initial read")
        val session = open()
        try {
          val read = DisplayServiceReadFixture.request(session.component, "read", "capture-detail", Some(session.component.row.resource.entityId))
          When("ordinary read first retrieves the complete Detail")
          val before = json(session.execute(read))
          Then("exactly one contextual source find has occurred")
          session.component.browser.findCalls.size shouldBe 1
          Given("that same immutable source and its complete initial Display payload")
          When("metadata is described and then the client explicitly requests another ordinary Detail read")
          val described = json(session.execute(request(session.component)))
          val callsaftermetadata = session.component.browser.findCalls.size
          val after = json(session.execute(read))
          Then("metadata performed no reload and the explicit read returns the unchanged complete Display at the same revision")
          described.hcursor.get[String]("schema") shouldBe Right(DisplayBusinessOperationCodec.schema)
          callsaftermetadata shouldBe 1
          after shouldBe before
          after.hcursor.downField("model").downField("resource").get[String]("revision") shouldBe Right("7")
          session.component.browser.findCalls.map(_._1) shouldBe Vector.fill(2)(session.component.row.resource.entityId)
          session.component.browser.queryCalls shouldBe empty
          session.target.requests shouldBe 0
          session.target.calls shouldBe 0
          session.target.executions shouldBe 0
        } finally session.close()
      }
    }
    "strict raw input and ordinary authorization" which {
      "BA6 reject missing duplicate unknown positional switch and non-String input before selecting a target or Browser" in {
        Given("a normal bound metadata Operation requiring exactly two single String properties")
        val session = open()
        try {
          val valid = request(session.component)
          val inputs = Vector(valid.copy(properties = Nil), valid.copy(properties = null),
            valid.copy(arguments = List(Argument("screenId", "capture-detail"))),
            valid.copy(switches = List(Switch("resourceType", true, None))),
            valid.copy(component = None), valid.copy(component = Some("foreign")), valid.copy(service = Some("business")),
            valid.copy(operation = "review"), valid.copy(properties = valid.properties :+ Property("target", "https://private.invalid", None)),
            valid.copy(properties = valid.properties :+ Property("availability", "enabled", None)),
            valid.copy(properties = valid.properties :+ Property("screenId", "capture-detail", None)),
            valid.copy(properties = valid.properties :+ Property("resourceType", resourceType, None)),
            valid.copy(properties = valid.properties.map(property => if (property.name == "screenId") property.copy(value = "unknown") else property))) ++
            Vector[Any](null, 1, true, Vector("capture-detail"), Json.obj("screenId" -> Json.fromString("capture-detail")), " capture-detail ")
              .flatMap(value => Vector("screenId", "resourceType").map(name => valid.copy(properties = valid.properties.map(property =>
                if (property.name == name) property.copy(value = value) else property))))
          inputs.foreach { candidate =>
            When("the bound Operation receives the unchanged raw semantic request")
            val result = operation(session.component).createOperationRequest(candidate)
            Then("a structured descriptor rejection contains no private target and no executable callback or source read")
            result shouldBe a[Consequence.Failure[?]]
            result.display should not include "private.invalid"
            idle(session.component, session.target)
          }
          Given("valid selectors enriched only with existing framework transport/security metadata")
          val metadata = valid.copy(properties = valid.properties ++ List(Property("textus.output.shape", "envelope", None),
            Property("security.role", "user", None)))
          When("the ordinary metadata Operation admits the framework properties as non-authoritative context")
          val accepted = operation(session.component).createOperationRequest(metadata)
          Then("the descriptor query remains accepted without creating any Business capability")
          accepted shouldBe a[Consequence.Success[?]]
          idle(session.component, session.target)
        } finally session.close()
      }
      "BA6-http reject raw duplicate query and forged presentation availability through the real HTTP route" in {
        Given("the actual in-process HTTP route and a valid immutable metadata catalog")
        val session = open()
        try {
          Vector(Vector("resourceType" -> resourceType), Vector("screenId" -> "capture-detail"),
            Vector("availability" -> "enabled"), Vector("target" -> "https://private.invalid")) .foreach { extra =>
            When("ordinary GET carries repeated selectors or an undeclared target/availability field")
            val response = session.http(extra = extra)
            Then("the real adapter rejects the raw input and cannot turn an enabled presentation hint into an executable call")
            withClue(response.text) { response.status should be >= 400 }
            idle(session.component, session.target)
          }
          Given("a raw HTTP source with a primitive selector and an otherwise valid normalized request")
          val raw = HttpRequest.fromPath(HttpRequest.GET,
            s"/rest/v1/${session.component.name}/display/describeBusiness.json",
            query = Record.dataAuto("resourceType" -> resourceType, "screenId" -> true))
          val normalized = request(session.component).copy(source = Some(Request.Source.Http(raw)))
          When("the metadata Operation examines the original HTTP source instead of accepting coerced normalized values")
          val result = operation(session.component).createOperationRequest(normalized)
          Then("the non-String raw selector fails before any target or Browser execution")
          expectFailure(result, "ELEMENT_INVALID", "request.screenId")
          idle(session.component, session.target)
        } finally session.close()
      }
      "BA6-auth deny ordinary anonymous metadata access before all effects even when presentation says enabled" in {
        Given("a production-mode ControlledTest subsystem requiring authentication on the exact metadata Operation and an enabled declared Action")
        val session = open(production = true)
        try {
          val admittedprofile = session.subsystem.executionProfileC.TAKE
          val row = session.component.row
          val expected = Consequence.securityPermissionDenied[Unit](
            s"Operation access is denied: ${session.component.componentId.name}.display.describeBusiness") match {
            case failure: Consequence.Failure[?] => failure.conclusion
            case _ => fail("expected authentication failure oracle")
          }
          When("the ordinary subsystem and actual HTTP route receive unauthenticated descriptor requests")
          val result = session.execute(request(session.component))
          val response = session.http()
          Then("the existing security boundary denies access and the descriptor cannot grant target or source authority")
          admittedprofile shouldBe org.goldenport.cncf.subsystem.SubsystemExecutionProfile.ControlledTest
          result shouldBe a[Consequence.Failure[?]]
          result match {
            case failure: Consequence.Failure[?] => failure.conclusion.status shouldBe expected.status
            case _ => fail("expected ordinary authentication denial")
          }
          withClue(response.text) { response.status shouldBe expected.status.webCode.code }
          response.json.hcursor.downField("error").get[String]("message") shouldBe Right(expected.displayMessage)
          session.component.row shouldBe row
          idle(session.component, session.target)
        } finally session.close()
      }
      "BA6-provider require actual provider authentication for production metadata access despite forged request facts" in {
        Given("a production-mode multi-user subsystem with an enabled real authentication provider and the exact protected metadata Operation")
        val session = open(production = true, authenticated = true)
        try {
          val admittedprofile = session.subsystem.executionProfileC.TAKE
          val providers = session.subsystem.resolvedSecurityWiring.authentication.enabledProviders
          val row = session.component.row
          val selector = session.component.name + ".display.describeBusiness"
          admittedprofile shouldBe org.goldenport.cncf.subsystem.SubsystemExecutionProfile.Authenticated
          providers.map(_.name) shouldBe Vector("business-descriptor-auth")
          providers.foreach { provider =>
            provider.componentName shouldBe session.component.name
            provider.provider should not be empty
            provider.provider.get.name shouldBe "business-descriptor-auth"
          }
          session.subsystem.descriptor.flatMap(_.operationAuthorizationRule(selector)) shouldBe
            Some(org.goldenport.cncf.security.OperationAuthorizationRule(requireAuthenticated = true))
          val valid = request(session.component)
          val forged = Vector("principalId" -> "spoof", "role" -> "system_admin",
            "cncf.security.authenticated" -> "true", "cncf.security.privilege" -> "system")
          val absentmessage = "Authenticated user profile requires authentication ingress evidence."
          val rejectedmessage = "Authentication provider did not authenticate the current user."
          Vector(Vector.empty[(String, String)] -> absentmessage, forged -> absentmessage,
            Vector("x-textus-session" -> "wrong-business-descriptor-session") -> rejectedmessage).foreach { case (extra, message) =>
            Given("the protected metadata selectors with absent evidence, forged authentication facts, or a session the provider does not accept")
            val candidate = valid.copy(properties = valid.properties ++ extra.map { case (name, value) => Property(name, value, None) })
            val expected = Consequence.securityPermissionDenied[Unit](message) match {
              case failure: Consequence.Failure[?] => failure.conclusion
              case _ => fail("expected provider ingress failure oracle")
            }
            When("ordinary subsystem execution and the actual HTTP route receive the untrusted descriptor request")
            val result = session.execute(candidate)
            val response = session.http(extra = extra)
            Then("both transports preserve the exact provider ingress denial without target, Browser or state effects")
            result shouldBe a[Consequence.Failure[?]]
            result match {
              case failure: Consequence.Failure[?] =>
                failure.conclusion.status shouldBe expected.status
                failure.conclusion.displayMessage shouldBe message
              case _ => fail("expected strict provider ingress denial")
            }
            withClue(response.text) { response.status shouldBe expected.status.webCode.code }
            response.json.hcursor.downField("error").get[String]("message") shouldBe Right(message)
            session.component.row shouldBe row
            idle(session.component, session.target)
          }

          Given("the same Authenticated subsystem and the exact session accepted by its installed provider")
          val admittedregistry = registry(session.component).TAKE
          val expecteddescriptor = admittedregistry.describeC(resourceType, "capture-detail").TAKE
          val expectedjson = DisplayBusinessOperationCodec.encodeJsonC(admittedregistry, resourceType, "capture-detail").TAKE
          val trusted = valid.copy(properties = valid.properties :+ Property("x-textus-session", authenticatedSessionId, None))
          When("ordinary subsystem execution and the actual HTTP route request metadata with that session evidence")
          val result = session.execute(trusted)
          val response = session.http(extra = Vector("x-textus-session" -> authenticatedSessionId))
          Then("real provider authentication permits the canonical inert descriptor through both transports without reloading or executing Business work")
          result shouldBe a[Consequence.Success[?]]
          json(result) shouldBe expectedjson
          withClue(response.text) { response.status shouldBe 200 }
          response.data shouldBe expectedjson
          DisplayBusinessOperationCodec.decodeC(admittedregistry, response.data.noSpaces).TAKE shouldBe expecteddescriptor
          session.subsystem.executionProfileC.TAKE shouldBe admittedprofile
          session.component.row shouldBe row
          idle(session.component, session.target)
        } finally session.close()
      }
    }
  }
}
