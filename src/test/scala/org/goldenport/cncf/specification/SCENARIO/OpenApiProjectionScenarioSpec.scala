package org.goldenport.cncf.specification.SCENARIO

import org.goldenport.Consequence
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.cncf.http.HttpExecutionEngine
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class OpenApiProjectionScenarioSpec extends AnyWordSpec with Matchers {

  "OpenAPI projection" should {
    "be reachable via server-emulator path" in {
      val req = HttpRequest.fromCurlLike(
        Vector("http://localhost/spec/export/openapi.json")
      )
      val engine = HttpExecutionEngine.Factory.engine()

      req match {
        case Consequence.Success(httpReq) =>
          val res = engine.execute(httpReq)
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
      val subsystem = DefaultSubsystemFactory.default()
      val req = Request(
        component = Some("spec"),
        service = Some("export"),
        operation = "openapi",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )

      subsystem.execute(req) match {
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
