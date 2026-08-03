package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.http4s.{Method, Request as HRequest, Uri}
import org.http4s.headers.`Content-Type`
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 *  version Jul. 30, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebRecordFormBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "A Web record control" should {
    "render as a JSON textarea through the operation form renderer" in {
      Given("a runtime Web descriptor that marks an operation field as JSON")
      val fixture = _fixture()

      When("the operation form is rendered")
      val html = fixture.renderer
        .renderOperationForm(fixture.subsystem, "debug", "http", "echo", fixture.descriptor)
        .map(_.body)
        .getOrElse(fail("debug echo form is missing"))

      Then("the record field is rendered as a textarea rather than a text input")
      html should include("<textarea")
      html should include("name=\"metadata\"")
      html should not include "type=\"json\""
    }

    "decode a submitted JSON object before operation dispatch" in {
      Given("a form API endpoint configured with a JSON record control")
      val fixture = _fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))
      val metadata = URLEncoder.encode("{\"source\":\"admin\",\"attempt\":2}", StandardCharsets.UTF_8)

      When("the JSON form field is submitted")
      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/debug/http/echo", s"metadata=$metadata"),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()

      Then("dispatch succeeds with the structured value")
      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("source")
      body should include("admin")
    }

    "reject malformed JSON before operation dispatch" in {
      Given("a form API endpoint configured with a JSON record control")
      val fixture = _fixture()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(fixture.subsystem))
      val malformed = URLEncoder.encode("{source:admin", StandardCharsets.UTF_8)

      When("malformed JSON is submitted")
      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/debug/http/echo", s"metadata=$malformed"),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()

      Then("the form boundary rejects it deterministically")
      response.status.code shouldBe 400
    }
  }

  private final case class Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    descriptor: WebDescriptor,
    renderer: StaticFormAppRenderer
  )

  private def _fixture(): Fixture = {
    val root = Files.createTempDirectory("web-record-form-boundary-spec")
    val web = root.resolve("web.yaml")
    Files.writeString(
      web,
      """expose:
        |  debug.http.echo: public
        |form:
        |  debug.http.echo:
        |    enabled: true
        |    controls:
        |      metadata:
        |        type: json
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    val configuration = ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(web.toString)
      )),
      ConfigurationTrace.empty
    )
    val subsystem = DefaultSubsystemFactory.default(None, configuration)
    val descriptor = WebDescriptor.load(web).TAKE
    Fixture(subsystem, descriptor, StaticFormAppRenderer())
  }

  private def _post_form_request(path: String, body: String): HRequest[IO] =
    HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString(path))
      .withEntity(body)
      .withContentType(`Content-Type`.parse("application/x-www-form-urlencoded").toOption.get)
}
