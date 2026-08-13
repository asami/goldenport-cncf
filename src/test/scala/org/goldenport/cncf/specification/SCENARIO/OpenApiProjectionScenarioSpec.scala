package org.goldenport.cncf.specification.SCENARIO

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import org.goldenport.Consequence
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.cncf.http.HttpExecutionEngine
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class OpenApiProjectionScenarioSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "OpenAPI projection" should {
    "be reachable via server-emulator path" in {
      Given("an OpenAPI HTTP projection request")
      val req = HttpRequest.fromCurlLike(
        Vector("http://localhost/spec/export/openapi.json")
      )
      val engine = HttpExecutionEngine.Factory.engine()

      When("the server emulator processes the request")
      val result = req.map(engine.execute)

      Then("the projection returns an OpenAPI document")
      result match {
        case Consequence.Success(httpReq) =>
          val res = httpReq
          res.code shouldBe 200
          val body = res.getString.getOrElse("")
          body should include ("\"openapi\":\"3.0.0\"")
          body should include ("\"paths\"")
          body should startWith ("{")
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }

    "be reachable via command path" in {
      Given("a runtime subsystem and an OpenAPI export request")
      val subsystem = RuntimeBindingAdmissionFixture.default()
      val req = Request(
        component = Some("org.goldenport.cncf.Specification"),
        service = Some("export"),
        operation = "openapi",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )

      When("the command request reaches the OpenAPI projection")
      val result = subsystem.execute(req)

      Then("the projection exposes OpenAPI paths")
      result match {
        case Consequence.Success(res) =>
          res match {
            case Response.Scalar(value: String) =>
              value should include ("\"openapi\":\"3.0.0\"")
              value should include ("\"paths\"")
            case other =>
              fail(s"unexpected response: ${other.toString}")
          }
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }
  }
}
