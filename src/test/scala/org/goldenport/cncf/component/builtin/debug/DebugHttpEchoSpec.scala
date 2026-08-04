package org.goldenport.cncf.component.builtin.debug

import java.nio.charset.StandardCharsets

import org.goldenport.bag.Bag
import org.goldenport.http.HttpRequest
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 21, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class DebugHttpEchoSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "Debug HTTP echo" should {
    "reflect GET query parameters" in {
      Given("a default subsystem and GET request with a query parameter")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val req = HttpRequest.fromPath(
        method = HttpRequest.GET,
        path = "/debug/http/echo",
        query = Record.create(Vector("x" -> "1"))
      )

      When("the subsystem executes the request")
      val res = subsystem.executeHttp(req)

      Then("the debug echo response reflects the request")
      res.code shouldBe 200
      val body = res.getString.getOrElse(fail("empty body"))
      body should include("cncf:")
      body should include("http:")
      body should include("method: \"GET\"")
      body should include("path: \"/debug/http/echo\"")
      body should include("x: \"1\"")
      body should include("present: false")
    }

    "reflect POST body data" in {
      Given("a default subsystem and POST request with a text body")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
      val payload = Bag.text("hello", StandardCharsets.UTF_8)
      val header = Record.create(Vector("Content-Type" -> "text/plain"))
      val req = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/debug/http/echo",
        header = header,
        body = Some(payload)
      )

      When("the subsystem executes the request")
      val res = subsystem.executeHttp(req)

      Then("the debug echo response reflects the request body")
      res.code shouldBe 200
      val body = res.getString.getOrElse(fail("empty body"))
      body should include("method: \"POST\"")
      body should include("path: \"/debug/http/echo\"")
      body should include("contentType: \"text/plain\"")
      body should include("present: true")
      body should include("preview:")
    }
  }
}
