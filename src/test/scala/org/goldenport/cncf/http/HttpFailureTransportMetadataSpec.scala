package org.goldenport.cncf.http

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import org.goldenport.Conclusion
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.protocol.HttpFailureTransportMetadata
import org.goldenport.conclusion.{Disposition, Interpretation}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.observation.{Cause, Descriptor, Observation, Phenomenon, Taxonomy}
import org.goldenport.record.Record
import org.http4s.{Header, Method, Request as HRequest, Status as HStatus, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * @since   Aug. 15, 2026
 * @version Aug. 15, 2026
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
