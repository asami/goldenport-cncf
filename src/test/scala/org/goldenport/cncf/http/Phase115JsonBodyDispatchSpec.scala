package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import cats.data.NonEmptyVector
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.entity.{EntityMutationAdapterDefaults, EntityRevisionTransport}
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField, CmlOperationUpdateField}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.convert.ValueReader
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XJson, XSemiStructuredData, XString}
import org.goldenport.value.BaseContent
import org.http4s.{Header, Method, Request, Response, Uri}
import org.http4s.headers.`Content-Type`
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.directive.Update
import org.typelevel.ci.CIString

/*
 * Actual REST bytes through Http4s, runtime admission, ComponentLogic and ActionCall.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase115JsonBodyDispatchSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import Phase115JsonBodyDispatchSpec.*

  "Phase 115 JSON REST dispatch" should {
    "reject invalid semantic input before ActionCall" which {
      Vector(
        "plain null patch" -> """{"nickname":null}""",
        "adaptive null operand" -> """{"nickname__value_or_null":null}""",
        "null command operand" -> """{"nickname__update_command":null}"""
      ).foreach { case (label, text) =>
        s"return ordinary HTTP 400 for a $label without clearing retained state" in {
          Given("an admitted runtime with a selected typed Entity update Operation and retained optional text")
          val fixture = _fixture()

          When("real application/json bytes are posted through routes.orNotFound")
          val response = _dispatch(fixture, text)
          val body = response.as[String].unsafeRunSync()

          Then("the structured policy failure is a client error and no ActionCall or mutation exists")
          response.status.code shouldBe 400
          response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.html)
          body should include ("Request failed")
          body should include ("structured-error-debug")
          body should include ("explicit-clear-required")
          fixture.capture.created shouldBe 0
          fixture.capture.calls shouldBe 0
          fixture.capture.nickname shouldBe Some("retained")
        }
      }

      "reject a semantic null sequence position instead of treating the object as Bag or an empty patch" in {
        Given("a valid object containing raw Json and a separate semantic sequence with a null position")
        val fixture = _fixture()
        val text = """{"payload":{"raw":null},"evidence":{"values":[{"label":"first"},null,{"label":"last"}]}}"""

        When("actual JSON bytes cross syntax admission and Core semantic Record conversion")
        val response = _dispatch(fixture, text)
        val body = response.as[String].unsafeRunSync()

        Then("conversion failure is HTTP 400 and neither binding nor mutation silently succeeds")
        response.status.code shouldBe 400
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.html)
        body should include ("Request failed")
        body should include ("structured-error-debug")
        fixture.capture.created shouldBe 0
        fixture.capture.calls shouldBe 0
        fixture.capture.nickname shouldBe Some("retained")
      }

      "reject a raw collection null root through the same ordinary client error route" in {
        Given("an optional generated request field whose actual source multiplicity is multiple Json")
        val fixture = _fixture()

        When("JSON null is submitted instead of the required syntax array")
        val response = _dispatch(fixture, """{"payloads":null}""")
        val body = response.as[String].unsafeRunSync()

        Then("the root is never scalar-promoted, omitted or used as an adaptive command")
        response.status.code shouldBe 400
        response.contentType.map(_.mediaType) shouldBe Some(org.http4s.MediaType.text.html)
        body should include ("Request failed")
        body should include ("structured-error-debug")
        fixture.capture.created shouldBe 0
        fixture.capture.calls shouldBe 0
      }

      "preserve conflicts until the existing request normalizer rejects them" in {
        Given("a real Entity update Operation and conflicting plain and explicit value carriers")
        val fixture = _fixture()

        When("the HTTP body contains both effective instructions for the same parameter")
        val response = _dispatch(fixture, """{"nickname":"first","nickname__value":"second"}""")

        Then("existing directive validation returns HTTP 400 before ActionCall or retained state mutation")
        response.status.code shouldBe 400
        fixture.capture.created shouldBe 0
        fixture.capture.calls shouldBe 0
        fixture.capture.nickname shouldBe Some("retained")
      }
    }

    "retain explicit update meaning through actual binding" which {
      "leave an omitted optional field unchanged when an unrelated patch executes" in {
        Given("a real Operation whose ActionCall captures and applies the supplied optional field update")
        val fixture = _fixture()

        When("a body updates only its unrelated title")
        val response = _dispatch(fixture, """{"title":"after"}""")

        Then("the ActionCall receives no nickname assignment and retains its previous value")
        response.status.code shouldBe 200
        fixture.capture.created shouldBe 1
        fixture.capture.calls shouldBe 1
        fixture.capture.nickname shouldBe Some("retained")
        fixture.capture.requests.head.arguments.find(_.name == "nickname") shouldBe None
        fixture.capture.requests.head.arguments.find(_.name == "title").map(_.value) shouldBe Some("after")
      }

      "bind the explicit null command to typed SetNull and clear only that optional value" in {
        Given("nullable scalar source metadata and a retained optional value")
        val fixture = _fixture()

        When("the actual REST body submits the existing null command carrier")
        val response = _dispatch(fixture, """{"nickname__update_command":"null"}""")

        Then("ComponentLogic materializes a present typed clear before the actual ActionCall executes")
        response.status.code shouldBe 200
        fixture.capture.created shouldBe 1
        fixture.capture.calls shouldBe 1
        fixture.capture.requests.head.arguments.find(_.name == "nickname").map(_.value) shouldBe Some(Update.SetNull)
        fixture.capture.requests.head.arguments.exists(_.name.endsWith("__update_command")) shouldBe false
        fixture.capture.nickname shouldBe None
      }

      "preserve explicit empty String assignment through ActionCall" in {
        Given("a real nullable String Operation and the explicit value carrier")
        val fixture = _fixture()

        When("JSON supplies a zero-length String as an explicit value")
        val response = _dispatch(fixture, """{"nickname__value":""}""")

        Then("the bound base argument is present and assigns the empty String")
        response.status.code shouldBe 200
        fixture.capture.requests.head.arguments.find(_.name == "nickname").map(_.value) shouldBe Some("")
        fixture.capture.nickname shouldBe Some("")
        fixture.capture.calls shouldBe 1
      }

      "allow only a zero-length String adaptive operand to choose typed null" in {
        Given("the existing adaptive nullable String carrier and its source metadata")
        val fixture = _fixture()

        When("JSON supplies the blank String that the normalizer already recognizes")
        val response = _dispatch(fixture, """{"nickname__value_or_null":""}""")

        Then("the actual ActionCall receives SetNull and the adaptive carrier no longer reaches binding")
        response.status.code shouldBe 200
        fixture.capture.requests.head.arguments.find(_.name == "nickname").map(_.value) shouldBe Some(Update.SetNull)
        fixture.capture.requests.head.arguments.exists(_.name.contains("__value")) shouldBe false
        fixture.capture.nickname shouldBe None
        fixture.capture.calls shouldBe 1
      }
    }

    "deliver raw documents to the actual Operation" which {
      Vector("null", "{\"gone\":null,\"positions\":[null],\"decimal\":1.2300e+40}").foreach { literal =>
        s"retain a scalar document assignment $literal" in {
          Given("an admitted typed Operation with canonical scalar XJson and a distinct optional String")
          val fixture = _fixture()
          val expected = _json(literal)

          When("real JSON bytes assign the raw document through canonical REST dispatch")
          val response = _dispatch(fixture, s"""{"payload":$literal}""")

          Then("the original raw node reaches ActionCall as an assignment while omitted optional text remains intact")
          response.status.code shouldBe 200
          fixture.capture.calls shouldBe 1
          val actual = fixture.capture.requests.head.arguments.find(_.name == "payload").map(_.value)
          actual shouldBe Some(expected)
          actual.get.asInstanceOf[Json].noSpaces shouldBe literal
          fixture.capture.nickname shouldBe Some("retained")
        }
      }

      Vector("payloads", "payloads__value").foreach { key =>
        s"retain ordered collection children through the $key binding" in {
          Given("an optional generated request field with multiple raw Json source metadata")
          val fixture = _fixture()
          val array = _json("""[null,1.2300e+40,{"gone":null},null,1.2300e+40]""")
          val expected = array.asArray.get

          When("actual JSON array bytes pass through ingress, carrier materialization and typed collection reading")
          val response = _dispatch(fixture, Json.obj(key -> array).noSpaces)
          val witness: Vector[Json] = fixture.capture.rawvalues

          Then("the actual Operation and ActionCall retain every ordered duplicate and null position")
          response.status.code shouldBe 200
          fixture.capture.created shouldBe 1
          fixture.capture.calls shouldBe 1
          witness shouldBe expected
          witness.map(_.noSpaces) shouldBe Vector("null", "1.2300e+40", "{\"gone\":null}", "null", "1.2300e+40")
          fixture.capture.requests.head.arguments.filter(_.name == "payloads").map(_.value).toVector shouldBe expected
          fixture.capture.requests.head.arguments.exists(_.name.contains("__value")) shouldBe false
        }
      }
    }

    "retain revision transport and ordinary security" which {
      "carry a valid If-Match through actual PUT JSON decoding into the existing strict revision metadata" in {
        Given("a selected admin/entity/update route and the canonical strong revision validator")
        val fixture = _fixture(admin = true)
        val headers = Vector("If-Match" -> "\"revision-7\"", "Authorization" -> "Bearer phase115-evidence", "X-Phase115" -> "retained")

        When("a real PUT body passes the decoder, existing mutation adapter and execution engine")
        val response = _dispatch(fixture, """{"title":"after","payload":null}""", Method.PUT, headers, "?mode=original")
        val source = fixture.capture.requests.head.source.collect { case GRequest.Source.Http(http) => http }

        Then("the original method, headers, context and strict revision selection reach the Operation unchanged")
        response.status.code shouldBe 200
        fixture.capture.calls shouldBe 1
        source.map(_.method) shouldBe Some(HttpRequest.PUT)
        source.flatMap(_.header.getString(EntityMutationAdapterDefaults.profilePropertyName)) shouldBe
          Some(EntityMutationAdapterDefaults.strictRestProfile)
        source.flatMap(_.header.getString(EntityRevisionTransport.observedRevisionPropertyName)) shouldBe Some("7")
        source.flatMap(_.header.getString("Authorization")) shouldBe Some("Bearer phase115-evidence")
        source.flatMap(_.header.getString("X-Phase115")) shouldBe Some("retained")
        source.flatMap(_.context.originalUri) shouldBe Some(fixture.path + "?mode=original")
        fixture.capture.requests.head.arguments.find(_.name == "payload").map(_.value) shouldBe Some(Json.Null)
      }

      Vector("not-a-validator", "W/\"revision-7\"").foreach { validator =>
        s"reject invalid If-Match $validator before ActionCall" in {
          Given("the same actual admin Entity PUT path with a malformed or weak validator")
          val fixture = _fixture(admin = true)

          When("valid JSON syntax passes decoding and the existing revision transport evaluates If-Match")
          val response = _dispatch(fixture, """{"title":"after"}""", Method.PUT, Vector("If-Match" -> validator))

          Then("the established revision failure stays a client error with zero ActionCalls or mutation")
          response.status.code shouldBe 400
          fixture.capture.created shouldBe 0
          fixture.capture.calls shouldBe 0
          fixture.capture.nickname shouldBe Some("retained")
        }
      }

      "retain managed idempotent revision selection when PUT omits If-Match" in {
        Given("an actual admin Entity PUT without a strict validator")
        val fixture = _fixture(admin = true)

        When("the JSON request executes through the unchanged mutation adapter")
        val response = _dispatch(fixture, """{"title":"after"}""", Method.PUT)
        val source = fixture.capture.requests.head.source.collect { case GRequest.Source.Http(http) => http }

        Then("the existing idempotent profile is retained without inventing an observed revision")
        response.status.code shouldBe 200
        source.flatMap(_.header.getString(EntityMutationAdapterDefaults.profilePropertyName)) shouldBe
          Some(EntityMutationAdapterDefaults.idempotentRestProfile)
        source.flatMap(_.header.getAny(EntityRevisionTransport.observedRevisionPropertyName)) shouldBe None
      }

      "enforce the normal provider authentication gate after successful JSON admission" in {
        Given("a real CML Operation requiring provider authentication and no admitted authentication provider")
        val fixture = _fixture(rule = Some(OperationAuthorizationRule(requireProviderAuthentication = true)))

        When("valid raw JSON is posted with a header that does not itself establish provider authentication")
        val response = _dispatch(fixture, """{"payload":{"raw":null}}""", headers = Vector("Authorization" -> "Bearer unverified"))

        Then("ordinary Operation authorization denies execution with zero ActionCalls and no mutation")
        response.status.code shouldBe 403
        fixture.capture.created shouldBe 0
        fixture.capture.calls shouldBe 0
        fixture.capture.nickname shouldBe Some("retained")
      }
    }
  }

  private def _fixture(admin: Boolean = false, rule: Option[OperationAuthorizationRule] = None): Fixture = {
    val capture = new Capture
    val subsystem = TestComponentFactory.emptySubsystem("phase115-json-body")
    val operation = new CapturingOperation(capture)
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
        name = "entity", operations = spec.OperationDefinitionGroup(NonEmptyVector.one(operation))))),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector(RestIngress())), egresses = EgressCollection(Vector(RestEgress())),
        projections = ProjectionCollection()))
    val metadata = CmlOperationDefinition(
      name = "update", kind = "COMMAND", inputType = "Policy115", outputType = "Unit", inputValueKind = "ENTITY_UPDATE",
      operationAuthorization = rule,
      parameters = Vector(
        CmlOperationField("title", "String", "?", update = Some(CmlOperationUpdateField("1", false))),
        CmlOperationField("nickname", "String", "?", update = Some(CmlOperationUpdateField("?", true))),
        CmlOperationField("payload", "Json", "?", update = Some(CmlOperationUpdateField("?", true))),
        CmlOperationField("payloads", "Json", "?", update = Some(CmlOperationUpdateField("*", false))),
        CmlOperationField("evidence", "XSemiStructuredData", "?", update = Some(CmlOperationUpdateField("?", true)))))
    val component = new Component {
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(metadata)
    }
    val name = if (admin) "admin" else "policy115"
    val componentid = if (admin) BuiltinComponentIdentity.ADMIN else TestComponentFactory.componentId(name)
    val core = Component.Core.create(componentid.name, componentid, ComponentInstanceId(componentid, name), protocol)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
    subsystem.add(Vector(component))
    val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
    Fixture(server, capture, subsystem, s"/rest/v1/$name/entity/update")
  }

  private def _dispatch(fixture: Fixture, text: String, method: Method = Method.POST,
    headers: Vector[(String, String)] = Vector.empty, query: String = ""): Response[IO] = {
    val request = Request[IO](method, Uri.unsafeFromString(fixture.path + query))
      .withBodyStream(Stream.emits(text.getBytes(StandardCharsets.UTF_8).toVector).covary[IO])
      .withContentType(`Content-Type`.parse("application/json").toOption.get)
      .putHeaders(Header.Raw(CIString("Accept"), "application/json"))
    val enriched = headers.foldLeft(request) { case (current, (name, value)) =>
      current.putHeaders(Header.Raw(CIString(name), value))
    }
    fixture.server.routes(null).orNotFound.run(enriched).unsafeRunSync()
  }
  private def _json(text: String): Json = io.circe.parser.parse(text).fold(error => fail(error.message), identity)
}

private object Phase115JsonBodyDispatchSpec {
  final case class Fixture(server: Http4sHttpServer, capture: Capture, subsystem: Subsystem, path: String)

  final class Capture {
    private var _created = 0
    private var _calls = 0
    private var _requests = Vector.empty[GRequest]
    private var _nickname = Option("retained")
    private var _raw_values = Vector.empty[Json]

    def created: Int = _created
    def calls: Int = _calls
    def requests: Vector[GRequest] = _requests
    def nickname: Option[String] = _nickname
    def rawvalues: Vector[Json] = _raw_values
    def constructing(): Unit = { _created += 1 }
    def execute(request: GRequest, rawValues: Vector[Json]): Consequence[OperationResponse] = {
      _calls += 1
      _requests = _requests :+ request
      _raw_values = rawValues
      request.arguments.find(_.name == "nickname").foreach { argument =>
        argument.value match {
          case Update.SetNull => _nickname = None
          case text: String => _nickname = Some(text)
          case _ => ()
        }
      }
      Consequence.success(OperationResponse.Scalar("updated"))
    }
  }

  final class CapturingOperation(capture: Capture) extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = "update",
      request = spec.RequestDefinition(parameters = List(
        _parameter("title", XString), _parameter("nickname", XString), _parameter("payload", XJson),
        _parameter("payloads", XJson), _parameter("evidence", XSemiStructuredData))),
      response = spec.ResponseDefinition.void)

    override def createOperationRequest(req: GRequest): Consequence[OperationRequest] = {
      val values = req.arguments.filter(_.name == "payloads").map(_.value).toVector
      val input = values match {
        case Vector(empty: Vector[?]) if empty.isEmpty => Vector.empty
        case _ => values
      }
      summon[ValueReader[Vector[Json]]].readC(input).map { rawvalues =>
        new QueryAction with OperationRequest.Core.Holder {
          override val core: OperationRequest.Core = OperationRequest.Core(req)
          override def createCall(core: ActionCall.Core): ActionCall = {
            capture.constructing()
            val callcore = core
            new ProcedureActionCall {
              override val core: ActionCall.Core = callcore
              override def execute(): Consequence[OperationResponse] = capture.execute(req, rawvalues)
            }
          }
        }
      }
    }

    private def _parameter(name: String, datatype: DataType): spec.ParameterDefinition =
      spec.ParameterDefinition(content = BaseContent.simple(name), kind = spec.ParameterDefinition.Kind.Argument,
        domain = ValueDomain(datatype, Multiplicity.ZeroOne))
  }
}
