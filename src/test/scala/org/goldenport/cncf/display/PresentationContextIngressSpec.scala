package org.goldenport.cncf.display

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import logicalui.{DisplayServiceReadFixture, TypedDisplayProjectionFixture}
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.http.{DisplayServiceHttpRouteFixture, HttpExecutionEngine, HttpRuntimeBindingAdmissionFixture}
import org.goldenport.protocol.{Argument, Property, Protocol, Request, Switch}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.OperationResponse
import org.http4s.{Header, Headers, Method, Request as Http4sRequest, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class PresentationContextIngressSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PresentationContextRuntimeFixture.*

  private def _encoded(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8)
  private def _status(fixture: Fixture, request: org.goldenport.http.HttpRequest): Int =
    fixture.subsystem.executeHttpWithExecutionResponse(request).response.status.code

  "Physical presentation ingress" should afterWord(
    "in spec:display-presentation-context, example:BI1..BI10, rules:R5,R7, phase:96") {
    "canonical Request evidence" which {
      "BI1 accept only one exact String property and reject duplicate or coerced candidates" in {
        Given("R7: canonical reserved Properties with duplicate null Option collection and numeric candidates")
        val request = Request.of(componentId.name, "operation", "evaluate")
        val invalid = Vector(null, Some(emptyWire), Vector(emptyWire), 1).map(value =>
          request.copy(properties = List(property(value)))) :+
          request.copy(properties = List(property(emptyWire), property(emptyWire)))
        When("physical Request metadata is extracted without coercion")
        val accepted = PresentationContextIngress.requestC(withCandidate(request))
        val rejected = invalid.map(PresentationContextIngress.requestC(_))
        Then("exactly one actual String is retained and all other physical representations fail")
        success(accepted) shouldBe Some(emptyWire)
        rejected.foreach(_.isSuccess shouldBe false)
      }
      "BI2 ignore payload arguments switches and normalized punctuation aliases" in {
        Given("R7: context-shaped business input and noncanonical metadata spellings")
        val request = Request.of(componentId.name, "operation", "evaluate").copy(
          arguments = List(Argument("cncf.presentationContext", emptyWire)),
          switches = List(Switch("cncf.presentationContext", true, None)),
          properties = List(Property("cncf.presentation-context", emptyWire, None),
            Property("CNCF.PresentationContext", emptyWire, None), Property("payload", emptyWire, None)))
        When("the exact reserved metadata boundary selects candidates")
        val result = PresentationContextIngress.requestC(request)
        Then("none of the business or alias values becomes a presentation context")
        success(result) shouldBe None
      }
    }
    "raw HTTP evidence and ordinary double enrichment" which {
      "BI3 accept one real header query or form candidate exactly once" in {
        Given("R7: ordinary HTTP routing with one physical candidate and normal authentication")
        val fixture = open(declared = false)
        try {
          val uri = "/rest/v1/" + componentId.name + "/operation/evaluate.json?cncf.presentationContext=" + _encoded(emptyWire)
          val candidates = Vector(http(header = record("x-CnCf-PrEsEnTaTiOn-CoNtExT" -> emptyWire)),
            http(query = record("cncf.presentationContext" -> emptyWire), uri = Some(uri)),
            http(form = record("cncf.presentationContext" -> emptyWire)))
          When("each actual subsystem HTTP route enriches the canonical Request twice")
          val results = candidates.map(_status(fixture, _))
          Then("each candidate is admitted once and the provider and business execute once per call")
          results shouldBe Vector(200, 200, 200)
          fixture.provider.requests should have size 3
          fixture.evidence.business should have size 3
          fixture.evidence.business.foreach(_.presentationContext should not be empty)
        } finally fixture.close()
      }
      "BI4 traverse the existing Http4s app with header literal and encoded queries while preserving unrelated business input" in {
        Given("R7: the actual existing Http4s application and raw ordered transport headers")
        val fixture = open(declared = false)
        try {
          val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))
          val app = DisplayServiceHttpRouteFixture.app(server)
          val path = "/rest/v1/" + componentId.name + "/operation/evaluate.json"
          val headers = Headers(Header.Raw(CIString("X-Textus-Session"), "session-one"),
            Header.Raw(CIString("X-CNCF-Presentation-Context"), emptyWire))
          val headerrequest = Http4sRequest[IO](Method.GET, Uri.unsafeFromString(path), headers = headers)
          val queryrequest = Http4sRequest[IO](Method.GET,
            Uri.unsafeFromString(path + "?cncf.presentationContext=" + _encoded(emptyWire)),
            headers = Headers(Header.Raw(CIString("X-Textus-Session"), "session-one")))
          val unrelated = "space +& /日本"
          val encodedrequest = Http4sRequest[IO](Method.GET,
            Uri.unsafeFromString(path + "?cncf%2EpresentationContext=" + _encoded(emptyWire) +
              "&business%2Ekey=" + _encoded(unrelated)),
            headers = Headers(Header.Raw(CIString("X-Textus-Session"), "session-one")))
          When("all three real HTTP requests cross the existing app adaptation and ordinary Subsystem")
          val statuses = Vector(headerrequest, queryrequest, encodedrequest)
            .map(request => app.run(request).unsafeRunSync().status.code)
          Then("each candidate is admitted once and only the unrelated exact argument reaches business input")
          statuses shouldBe Vector(200, 200, 200)
          fixture.provider.requests should have size 3
          fixture.evidence.business should have size 3
          fixture.evidence.requests.map(_.arguments).toVector shouldBe
            Vector(Nil, Nil, List(Argument("business%2Ekey", unrelated)))
          fixture.evidence.requests.foreach { request =>
            request.properties.exists(property => Set("cncf.presentationContext", "cncf%2EpresentationContext").contains(property.name)) shouldBe false
            request.switches shouldBe empty
          }
        } finally fixture.close()
      }
      "BI5 reject equal or unequal candidates across distinct physical locations and repeated headers" in {
        Given("R7: HTTP metadata duplicates whose equality cannot hide physical multiplicity")
        val fixture = open(declared = false)
        try {
          val candidates = Vector(http(query = record("cncf.presentationContext" -> emptyWire),
            header = record("X-CNCF-Presentation-Context" -> emptyWire)),
            http(form = record("cncf.presentationContext" -> emptyWire), header = record("X-CNCF-Presentation-Context" -> wire())),
            http(header = record("X-CNCF-Presentation-Context" -> emptyWire, "x-cncf-presentation-context" -> emptyWire)))
          When("the ordinary authorized HTTP route counts physical candidates")
          val statuses = candidates.map(_status(fixture, _))
          Then("every duplicate fails before provider or business regardless of value equality")
          statuses.foreach(_ should be >= 400)
          fixture.evidence.events.toVector shouldBe Vector.fill(candidates.size)("authorize")
          fixture.provider.requests shouldBe empty
          fixture.evidence.business shouldBe empty
        } finally fixture.close()
      }
      "BI6 retain raw query duplicates despite a collapsed structured mirror and reject contradictions or invalid scalar types" in {
        Given("R7: original URI query duplicates and independent structured Record representations")
        val fixture = open(declared = false)
        try {
          val prefix = "/rest/v1/" + componentId.name + "/operation/evaluate.json?cncf%2EpresentationContext="
          val single = prefix + _encoded(emptyWire)
          val duplicate = single + "&cncf.presentationContext=" + _encoded(emptyWire)
          val candidates = Vector(http(query = record("cncf.presentationContext" -> emptyWire), uri = Some(duplicate)),
            http(query = record("cncf.presentationContext" -> wire()), uri = Some(single)),
            http(query = record("cncf.presentationContext" -> 1), uri = Some(single)),
            http(query = record("cncf.presentationContext" -> Some(emptyWire))),
            http(uri = Some(prefix + "%GG")))
          When("authorized ingress decodes raw keys and values once and compares physical query mirrors")
          val statuses = candidates.map(_status(fixture, _))
          Then("raw duplicates contradictions wrong types and malformed escapes all fail before provider or business")
          statuses.foreach(_ should be >= 400)
          fixture.evidence.events.toVector shouldBe Vector.fill(candidates.size)("authorize")
          fixture.provider.requests shouldBe empty
          fixture.evidence.business shouldBe empty
          Given("one percent-encoded raw query with an identical single structured String mirror")
          When("the valid mirror is routed through ordinary double enrichment")
          val accepted = _status(fixture, http(query = record("cncf.presentationContext" -> emptyWire), uri = Some(single)))
          Then("the mirror represents one physical query and its candidate is admitted exactly once")
          accepted shouldBe 200
          fixture.provider.requests should have size 1
        } finally fixture.close()
      }
      "BI7 count independent canonical properties and raw HTTP while honoring typed raw HTTP source" in {
        Given("R7: an ordinary Request plus separate HTTP and an internally reconstructed Request with a raw HTTP source")
        val fixture = open(declared = false)
        try {
          val transport = http(header = record("X-CNCF-Presentation-Context" -> emptyWire))
          val independent = withCandidate(fixture.request)
          val sourced = independent.copy(source = Some(Request.Source.Http(transport)))
          When("both calls traverse ordinary Request plus HTTP execution")
          val duplicate = success(fixture.subsystem.executeWithExecutionResponse(independent, transport)).response
          val mirrored = success(fixture.subsystem.executeWithExecutionResponse(sourced, transport)).response
          Then("independent candidates reject and a typed source suppresses generated property mirrors")
          duplicate.asInstanceOf[OperationResponse.Http].response.status.code should be >= 400
          mirrored shouldBe OperationResponse.Scalar("ordinary")
          fixture.provider.requests should have size 1
          fixture.evidence.business should have size 1
        } finally fixture.close()
      }
      "BI8 authorize before parsing malformed presentation escapes on actual HTTP" in {
        Given("R7: a denied Operation and a malformed reserved raw-query value")
        val fixture = open(denied = true)
        try {
          val prefix = "/rest/v1/" + componentId.name + "/operation/evaluate.json?"
          val uris = Vector("cncf.presentationContext", "cncf%2EpresentationContext").map(prefix + _ + "=%GG")
          When("the original HTTP request passes generic enrichment and reaches Operation authorization")
          val statuses = uris.map(uri => _status(fixture, http(uri = Some(uri))))
          Then("authorization denies without presentation parsing provider evaluation or business")
          statuses.foreach(_ should be >= 400)
          fixture.evidence.events.toVector shouldBe Vector.fill(uris.size)("authorize")
          fixture.provider.requests shouldBe empty
          fixture.evidence.evaluations shouldBe empty
          fixture.evidence.business shouldBe empty
        } finally fixture.close()
      }
      "BI9 keep exact reserved query spellings out of registered Display read and query business requests" in {
        Given("R7: registered Detail and List in the authenticated ordinary subsystem with its inherited provider")
        val fixture = open(declared = false)
        try {
          val row = success(TypedDisplayProjectionFixture.captureC(1, 1L))
          val component = new DisplayServiceReadFixture.ReadComponent(Vector(row))
          DisplayServiceReadFixture.initialize(component, fixture.subsystem,
            protocol = Protocol(handler = ProtocolHandler.default))
          success(new ComponentFactory().bootstrapC(component))
          fixture.subsystem.add(Vector(component))
          val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))
          val app = DisplayServiceHttpRouteFixture.app(server)
          val keys = Vector("cncf.presentationContext", "cncf%2EpresentationContext", "%63ncf.presentationContext")
          val headers = Headers(Header.Raw(CIString("X-Textus-Session"), "session-one"))
          keys.foreach { key => Vector("read", "query").foreach { operation =>
            Given("one exact physical context spelling and the registered " + operation + " selectors")
            val screen = if (operation == "read") "capture-detail" else "collecting-resources"
            val resource = if (operation == "read") "&resourceId=" + _encoded(row.resource.entityId.value) else ""
            val uri = Uri.unsafeFromString("/rest/v1/" + component.name + "/display/" + operation +
              ".json?resourceType=" + _encoded(DisplayServiceReadFixture.resourceType) + "&screenId=" + screen +
              resource + "&textus.output.shape=envelope&" + key + "=" + _encoded(emptyWire))
            val providers = fixture.provider.requests.size
            val finds = component.browser.findCalls.size
            val queries = component.browser.queryCalls.size
            When("the real Http4s app interprets the ordinary registered Display request")
            val response = app.run(Http4sRequest[IO](Method.GET, uri, headers = headers)).unsafeRunSync()
            val text = response.bodyText.compile.string.unsafeRunSync()
            Then("the strict Display request succeeds with one provider and exactly one resolved contextual source call")
            withClue(text) { response.status.code shouldBe 200 }
            fixture.provider.requests.size shouldBe providers + 1
            component.browser.findCalls.size shouldBe finds + (if (operation == "read") 1 else 0)
            component.browser.queryCalls.size shouldBe queries + (if (operation == "query") 1 else 0)
            val context = if (operation == "read") component.browser.findCalls.last._2 else component.browser.queryCalls.last._2
            context.presentationContext should not be empty
            context.presentationContext.get.variant shouldBe Some("server-variant")
            val json = io.circe.parser.parse(text).fold(error => fail(error.message), identity)
            val payload = json.hcursor.downField("data").focus.get
            val safe = success(PresentationContextCodec.decodeJsonC(payload.hcursor.downField("presentationContext").focus.get))
            safe.locale shouldBe Some(context.core.locale.toLanguageTag)
            safe.variant shouldBe Some("server-variant")
            safe.flow.get.displayInstance shouldBe "server-display"
            safe.flow.get.correlation shouldBe "server-correlation"
          } }
          fixture.provider.requests should have size 6
          component.browser.findCalls should have size 3
          component.browser.queryCalls should have size 3
          component.browser.contextFreeCalls shouldBe 0
          fixture.evidence.business shouldBe empty
        } finally fixture.close()
      }
      "BI10 preserve encoded-looking canonical business arguments with absent or independent HTTP metadata" in {
        Given("R7: a Source.None canonical business argument and separately supplied raw HTTP context")
        val fixture = open(declared = false)
        try {
          val argument = Argument("cncf%2EpresentationContext", "ordinary business")
          val request = fixture.request.copy(arguments = List(argument), properties = List(Property("x-textus-session", "session-one", None)), source = None)
          When("the canonical Request executes without presentation metadata")
          val absent = success(fixture.subsystem.executeOperationResponse(request))
          Then("the ordinary encoded-looking argument remains business input and no provider is invoked")
          absent shouldBe OperationResponse.Scalar("ordinary")
          fixture.evidence.requests.last.arguments shouldBe List(argument)
          fixture.provider.requests shouldBe empty
          Given("the same canonical Request plus independent HTTP context header evidence")
          val transport = http(header = record("X-CNCF-Presentation-Context" -> emptyWire))
          When("normal Request plus HTTP execution restores the independent metadata")
          val restored = success(fixture.subsystem.executeWithExecutionResponse(request, transport)).response
          Then("the argument is preserved independently and one provider supplies the resolved business context")
          restored shouldBe OperationResponse.Scalar("ordinary")
          fixture.evidence.requests.map(_.arguments).toVector shouldBe Vector(List(argument), List(argument))
          fixture.provider.requests should have size 1
          fixture.evidence.business should have size 2
          fixture.evidence.business.head.presentationContext shouldBe None
          fixture.evidence.business.last.presentationContext should not be empty
        } finally fixture.close()
      }
    }
  }
}
