package org.goldenport.cncf.http

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import org.goldenport.Conclusion
import org.goldenport.bag.Bag
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.observability.ConclusionDisclosure
import org.goldenport.cncf.protocol.HttpFailureTransportMetadata
import org.goldenport.conclusion.{Disposition, Interpretation}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.observation.{Cause, Descriptor, Observation, Phenomenon, Taxonomy}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality
import org.http4s.{Header, Method, Request as HRequest, Status as HStatus, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * @since   Aug. 15, 2026
 * @version Oct. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpFailureTransportMetadataSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "HTTP failure transport metadata" should {
    "render explicitly materialized failure status metadata in a structured JSON error" in {
      Given("an argument-taxonomy Conclusion with explicit application status metadata")
      val conclusion = _conclusion()
      val source = HttpFailureTransportMetadata.attach(
        HttpResponse.text(HttpStatus.BadRequest, _message),
        conclusion
      )

      When("the exact Subsystem transport helper output is rendered for a JSON client")
      val response = _server
        ._to_http_response_with_metadata(
          source,
          Some(_request("application/json")),
          None,
          None,
          None,
          RuntimeContext.ExecutionMetadata.empty
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val error = parse(body).toOption.map(_.hcursor.downField("error"))

      Then("the public envelope preserves only the safe status metadata and no internal headers")
      response.status shouldBe HStatus.BadRequest
      error.flatMap(_.get[Int]("status").toOption) shouldBe Some(400)
      error.flatMap(_.get[String]("message").toOption) shouldBe Some(_message)
      error.flatMap(_.get[Long]("detailCode").toOption) shouldBe conclusion.status.detailCode.map(_.code)
      error.flatMap(_.get[Long]("appCode").toOption) shouldBe Some(_app_code)
      error.flatMap(_.get[String]("appStatus").toOption) shouldBe Some(_app_status)
      _internal_headers(response) shouldBe empty
    }

    "preserve an explicitly empty application status in a structured JSON error" in {
      Given("an argument-taxonomy Conclusion with an explicitly empty application status")
      val conclusion = _conclusion(Some(""))
      val source = HttpFailureTransportMetadata.attach(
        HttpResponse.text(HttpStatus.BadRequest, _message),
        conclusion
      )

      When("the exact Subsystem transport helper output is rendered for a JSON client")
      val response = _server
        ._to_http_response_with_metadata(
          source,
          Some(_request("application/json")),
          None,
          None,
          None,
          RuntimeContext.ExecutionMetadata.empty
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val error = parse(body).toOption.map(_.hcursor.downField("error"))

      Then("the public envelope retains all materialized metadata without internal headers")
      response.status shouldBe HStatus.BadRequest
      error.flatMap(_.get[Long]("detailCode").toOption) shouldBe conclusion.status.detailCode.map(_.code)
      error.flatMap(_.get[Long]("appCode").toOption) shouldBe Some(_app_code)
      error.flatMap(_.get[String]("appStatus").toOption) shouldBe Some("")
      _internal_headers(response) shouldBe empty
    }

    "preserve the legacy plain failure body while removing transport metadata" in {
      Given("an explicitly materialized failure response produced by the Subsystem transport helper")
      val source = HttpFailureTransportMetadata.attach(
        HttpResponse.text(HttpStatus.BadRequest, _message),
        _conclusion()
      )

      When("the response is rendered for a text/plain client")
      val response = _server
        ._to_http_response_with_metadata(
          source,
          Some(_request("text/plain")),
          None,
          None,
          None,
          RuntimeContext.ExecutionMetadata.empty
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the plain message remains unchanged and metadata headers are not emitted")
      response.status shouldBe HStatus.BadRequest
      body shouldBe _message
      _internal_headers(response) shouldBe empty
    }

    "ignore malformed internal metadata and never emit it" in {
      Given("a fallback response carrying malformed case-varied internal metadata headers")
      val source = HttpResponse.text(HttpStatus.BadRequest, _message).withHeader(
        Record.data(
          HttpFailureTransportMetadata.DETAIL_CODE_HEADER.toLowerCase(java.util.Locale.ROOT) -> "not-a-number",
          HttpFailureTransportMetadata.APP_STATUS_HEADER.toLowerCase(java.util.Locale.ROOT) -> "%%%"
        )
      )

      When("the fallback response is rendered for a structured client")
      val response = _server
        ._to_http_response_with_metadata(
          source,
          Some(_request("application/json")),
          None,
          None,
          None,
          RuntimeContext.ExecutionMetadata.empty
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val error = parse(body).toOption.map(_.hcursor.downField("error"))

      Then("the malformed values are ignored rather than thrown, rendered, or leaked as headers")
      response.status shouldBe HStatus.BadRequest
      error.flatMap(_.get[Long]("detailCode").toOption) shouldBe None
      error.flatMap(_.get[Long]("appCode").toOption) shouldBe None
      error.flatMap(_.get[String]("appStatus").toOption) shouldBe None
      _internal_headers(response) shouldBe empty
    }

    "reject duplicate case-equivalent internal metadata without projecting any metadata" in {
      Given("a fallback response carrying duplicate case-equivalent internal application-code headers")
      val source = HttpResponse.text(HttpStatus.BadRequest, _message).withHeader(
        Record.data(
          HttpFailureTransportMetadata.DETAIL_CODE_HEADER -> "100020003004",
          HttpFailureTransportMetadata.APP_CODE_HEADER -> _app_code.toString,
          HttpFailureTransportMetadata.APP_CODE_HEADER.toLowerCase(java.util.Locale.ROOT) -> "7402",
          HttpFailureTransportMetadata.APP_STATUS_HEADER -> "cmVhc29u"
        )
      )

      When("the fallback response is rendered for a structured client")
      val response = _server
        ._to_http_response_with_metadata(
          source,
          Some(_request("application/json")),
          None,
          None,
          None,
          RuntimeContext.ExecutionMetadata.empty
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val error = parse(body).toOption.map(_.hcursor.downField("error"))

      Then("the duplicate metadata is ignored without throwing, projecting, or leaking headers")
      response.status shouldBe HStatus.BadRequest
      error.flatMap(_.get[Long]("detailCode").toOption) shouldBe None
      error.flatMap(_.get[Long]("appCode").toOption) shouldBe None
      error.flatMap(_.get[String]("appStatus").toOption) shouldBe None
      _internal_headers(response) shouldBe empty
    }

    "not promote a Conclusion Reason facet to an application status" in {
      Given("a taxonomy-bearing Conclusion whose cause has a Reason but no explicit application status")
      val conclusion = _conclusion(None)
      val source = HttpFailureTransportMetadata.attach(
        HttpResponse.text(HttpStatus.BadRequest, _message),
        conclusion
      )

      When("the response is rendered for a structured client")
      val response = _server
        ._to_http_response_with_metadata(
          source,
          Some(_request("application/json")),
          None,
          None,
          None,
          RuntimeContext.ExecutionMetadata.empty
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val error = parse(body).toOption.map(_.hcursor.downField("error"))

      Then("the public envelope omits application status while retaining the explicit application code")
      error.flatMap(_.get[Long]("appCode").toOption) shouldBe Some(_app_code)
      error.flatMap(_.get[String]("appStatus").toOption) shouldBe None
      _internal_headers(response) shouldBe empty
    }

    "pass trusted structured JSON and YAML through exactly once and strip the internal marker" in {
      Given("framework-produced structured envelopes carrying the fixed internal response marker")
      val error = StructuredHttpError.fromConclusion(_conclusion(), "/failure-transport", "GET", OperationMode.Production)
      val record = error._envelope_record(ConclusionDisclosure.Detail.Full)
      val bodies = Vector("application/json" -> RecordEncoder.json(record), "application/yaml" -> RecordEncoder.yaml(record))
      bodies.foreach { case (mime, body) =>
        Given("one already projected envelope in its declared representation")
        val source = HttpFailureTransportMetadata.markStructured(HttpResponse.Text(HttpStatus.BadRequest,
          ContentType(MimeType(mime), Some(java.nio.charset.StandardCharsets.UTF_8)), Bag.text(body)))
        When("the actual server transport gate renders the trusted response")
        val response = _server._to_http_response_with_metadata(source, Some(_request(mime)), None, None, None,
          RuntimeContext.ExecutionMetadata.empty).unsafeRunSync()
        val rendered = response.as[String].unsafeRunSync()
        Then("the envelope bytes and status survive and every internal header disappears")
        rendered shouldBe body
        response.status shouldBe HStatus.BadRequest
        _internal_headers(response) shouldBe empty
      }
    }

    "preserve selected Parameter confidentiality and exact optional legacy plain metadata at both Conclusion amounts" in {
      Given("a schema-classified value appearing only in a Conclusion Message and its associated Parameter and Value facets")
      val classifiedvalue = "classifiedValue"
      val privatefield = "privateField"
      val message = "Unavailable content " + classifiedvalue + "."
      val facets = Vector(Descriptor.Facet.Message(message), Descriptor.Facet.Parameter.argument(privatefield),
        Descriptor.Facet.Value(classifiedvalue))
      val confidentiality = Map(privatefield -> DataConfidentiality.Secret)
      val credentials = Vector.empty[String]
      val metadata = Vector(
        (Some(100020003004L), Some(_app_code), Some(_app_status)),
        (None, None, Some("")),
        (None, None, None))
      for ((detailcode, appcode, appstatus) <- metadata; detail <- Vector(
          ConclusionDisclosure.Detail.Standard, ConclusionDisclosure.Detail.Full)) {
        Given("an unchanged original Conclusion with populated empty or absent optional status metadata and no physical credentials")
        val source = _conclusion(appstatus)
        val original = source.copy(status = source.status.copy(appCode = appcode),
          observation = source.observation.copy(cause = Cause.create(facets)))
        val error = StructuredHttpError.fromConclusion(original, "/failure-transport", "GET", OperationMode.Production)
          .copy(detailCode = detailcode)
        When("the selected security projection supplies the error record to the production legacy plain formatter and structured encoders")
        val record = error._envelope_record(detail, confidentiality, credentials)
        val projected = record.getRecord("error").get
        val plain = Http4sHttpServer._plain_structured_error_body(projected)
        val json = RecordEncoder.json(record)
        val yaml = RecordEncoder.yaml(record)
        Then("all formats exclude the Conclusion-only secret while plain retains exact safe message labels order numeric values and optional empty status")
        val expected = Vector(Some("Unavailable content [redacted]."), Some("status: 400"), Some("statusText: Bad Request"),
          detailcode.map(value => s"detailCode: $value"), appcode.map(value => s"appCode: $value"),
          appstatus.map(value => s"appStatus: $value")).flatten.mkString("\n")
        plain shouldBe expected
        Vector(plain, json, yaml).foreach(_ should not include classifiedvalue)
        val cursor = parse(json).toOption.get.hcursor.downField("error")
        cursor.get[String]("message") shouldBe Right("Unavailable content [redacted].")
        cursor.get[Int]("status") shouldBe Right(400)
        cursor.get[String]("statusText") shouldBe Right("Bad Request")
        cursor.get[Long]("detailCode").toOption shouldBe detailcode
        cursor.get[Long]("appCode").toOption shouldBe appcode
        cursor.get[String]("appStatus").toOption shouldBe appstatus
        cursor.downField("conclusion").downField("observation").downField("cause")
          .get[String]("value") shouldBe Right("[redacted]")
        original.cause.getMessage shouldBe Some(message)
        original.cause.descriptor.facets shouldBe facets
        original.status.appCode shouldBe appcode
        original.status.appStatus shouldBe appstatus
        error.conclusion.get should be theSameInstanceAs original
        error.message shouldBe message
        credentials shouldBe empty
      }
    }

    "ignore a client-supplied structured marker when wrapping a legacy explicit failure" in {
      Given("a legacy plain response and a client request carrying the internal header name")
      val source = HttpResponse.text(HttpStatus.BadRequest, _message)
      val request = _request("application/json").putHeaders(Header.Raw(
        CIString(HttpFailureTransportMetadata.STRUCTURED_ERROR_HEADER), "1"))
      When("the server projects the explicit failure through its legacy fallback")
      val response = _server._to_http_response_with_metadata(source, Some(request), None, None, None,
        RuntimeContext.ExecutionMetadata.empty).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      Then("only a trusted response marker can select passthrough and the client marker never reaches the wire")
      parse(body).toOption.get.hcursor.downField("error").get[String]("message") shouldBe Right(_message)
      _internal_headers(response) shouldBe empty
    }
  }

  private lazy val _server: Http4sHttpServer =
    HttpRuntimeBindingAdmissionFixture.server(
      new HttpExecutionEngine(HttpRuntimeBindingAdmissionFixture.default(Some("server")))
    )

  private def _conclusion(appstatus: Option[String] = Some(_app_status)): Conclusion =
    Conclusion(
      status = Conclusion.Status(appCode = Some(_app_code), appStatus = appstatus),
      observation = Observation(
        phenomenon = Phenomenon.Failure,
        taxonomy = Taxonomy(Taxonomy.Category.Argument, Taxonomy.Symptom.Invalid),
        cause = Cause.create(Vector(Descriptor.Facet.Message(_message), Descriptor.Facet.Reason(_reason))),
        timestamp = Instant.EPOCH
      ),
      interpretation = Interpretation.domainFailure,
      disposition = Disposition.fix
    )

  private def _request(accept: String): HRequest[IO] =
    HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/failure-transport"))
      .putHeaders(Header.Raw(CIString("Accept"), accept))

  private def _internal_headers(
    response: org.http4s.Response[IO]
  ): Vector[String] =
    response.headers.headers
      .map(_.name.toString)
      .filter(HttpFailureTransportMetadata.isInternalHeader)
      .toVector

  private val _app_code = 7401L
  private val _app_status = "project-identity-required"
  private val _message = "Project identity is required."
  private val _reason = "project-identity-required"
}
