package org.goldenport.cncf.http

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.config.OperationMode
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
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpFailureTransportMetadataSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "E1 HTTP operation-conflict projection" should afterWord(
    "in spec:http-failure-transport, example:E1, rules:P12-HTTP-BLOB-CONFLICT-01,P12-HTTP-ENTITY-CONFLICT-01, phase:12"
  ) {
    "resolve Conflict consistently while preserving failure metadata and production filtering" in {
      Given("an operation-conflict Conclusion with explicit detail and application status metadata")
      val conflict = Consequence.operationConflict(
        "update-entity",
        Vector(Descriptor.Facet.Message("The entity revision is stale."), Descriptor.Facet.Reason("stale-entity-revision"))
      ).conclusion
      val conclusion = conflict.copy(status = conflict.status.copy(appCode = Some(_app_code), appStatus = Some(_app_status)))

      When("the failure is projected to its HTTP status and structured production envelope")
      val status = HttpFailureTransportMetadata.httpStatus(conclusion)
      val error = _structured_error(conclusion)
      val envelope = parse(error.envelopeJson).toOption.map(_.hcursor.downField("error"))

      Then("both projections use Conflict 409 and retain safe metadata with the diagnostic Conclusion privately available")
      status shouldBe HttpStatus.Conflict
      error.status shouldBe 409
      error.statusText shouldBe "Conflict"
      error.message shouldBe conclusion.displayMessage
      error.detailCode shouldBe conclusion.status.detailCode.map(_.code)
      error.appCode shouldBe Some(_app_code)
      error.appStatus shouldBe Some(_app_status)
      error.conclusion shouldBe Some(conclusion)
      envelope.flatMap(_.get[Int]("status").toOption) shouldBe Some(409)
      envelope.flatMap(_.get[String]("statusText").toOption) shouldBe Some("Conflict")
      envelope.flatMap(_.get[Long]("detailCode").toOption) shouldBe conclusion.status.detailCode.map(_.code)
      envelope.flatMap(_.get[Long]("appCode").toOption) shouldBe Some(_app_code)
      envelope.flatMap(_.get[String]("appStatus").toOption) shouldBe Some(_app_status)
      envelope.flatMap(_.get[io.circe.Json]("debug").toOption) shouldBe None
    }
  }

  "E2 HTTP argument-invalid projection" should afterWord(
    "in spec:http-failure-transport, example:E2, rules:P12-HTTP-BLOB-CONFLICT-01,P12-HTTP-ENTITY-CONFLICT-01, phase:12"
  ) {
    "retain Bad Request for ordinary invalid arguments" in {
      Given("an invalid-argument Conclusion produced by the semantic failure utility")
      val conclusion = Consequence.argumentInvalid[Unit]("The entity identifier is required.").conclusion

      When("the failure is projected to its HTTP status and structured error")
      val status = HttpFailureTransportMetadata.httpStatus(conclusion)
      val error = _structured_error(conclusion)

      Then("the existing Bad Request mapping remains 400")
      status shouldBe HttpStatus.BadRequest
      error.status shouldBe 400
      error.statusText shouldBe "Bad Request"
    }
  }

  "E3 HTTP operation-illegal projection" should afterWord(
    "in spec:http-failure-transport, example:E3, rules:P12-HTTP-BLOB-CONFLICT-01,P12-HTTP-ENTITY-CONFLICT-01, phase:12"
  ) {
    "ignore conflict wording when the typed operation symptom differs" in {
      Given("an illegal-operation Conclusion whose message contains conflict")
      val conclusion = Consequence.operationIllegal("update-entity", "A conflict word is descriptive only.").conclusion

      When("the failure is projected to its HTTP status and structured error")
      val status = HttpFailureTransportMetadata.httpStatus(conclusion)
      val error = _structured_error(conclusion)

      Then("message text does not promote the failure to Conflict")
      status shouldBe HttpStatus.BadRequest
      error.status shouldBe 400
      error.statusText shouldBe "Bad Request"
    }
  }

  "E4 HTTP non-operation-conflict projection" should afterWord(
    "in spec:http-failure-transport, example:E4, rules:P12-HTTP-BLOB-CONFLICT-01,P12-HTTP-ENTITY-CONFLICT-01, phase:12"
  ) {
    "retain the category mapping when only the typed conflict symptom matches" in {
      Given("an argument-taxonomy Conclusion with the Conflict symptom")
      val original = _conclusion()
      val conclusion = original.copy(observation = original.observation.copy(
        taxonomy = Taxonomy(Taxonomy.Category.Argument, Taxonomy.Symptom.Conflict)
      ))

      When("the failure is projected to its HTTP status and structured error")
      val status = HttpFailureTransportMetadata.httpStatus(conclusion)
      val error = _structured_error(conclusion)

      Then("the Argument category keeps Bad Request rather than selecting the operation-conflict override")
      status shouldBe HttpStatus.BadRequest
      error.status shouldBe 400
      error.statusText shouldBe "Bad Request"
    }
  }

  "E5 HTTP permission-denied projection" should afterWord(
    "in spec:http-failure-transport, example:E5, rules:P12-HTTP-BLOB-CONFLICT-01,P12-HTTP-ENTITY-CONFLICT-01, phase:12"
  ) {
    "retain Forbidden for security permission failures" in {
      Given("a permission-denied Conclusion produced by the semantic failure utility")
      val conclusion = Consequence.securityPermissionDenied[Unit]("The operation requires permission.").conclusion

      When("the failure is projected to its HTTP status and structured error")
      val status = HttpFailureTransportMetadata.httpStatus(conclusion)
      val error = _structured_error(conclusion)

      Then("the existing Forbidden mapping remains 403")
      status shouldBe HttpStatus.Forbidden
      error.status shouldBe 403
      error.statusText shouldBe "Forbidden"
    }
  }

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

  private def _structured_error(conclusion: Conclusion): StructuredHttpError =
    StructuredHttpError.fromConclusion(conclusion, "/failure-transport", "POST", OperationMode.Production)

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
