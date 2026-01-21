package org.goldenport.cncf.component.builtin.debug

import java.nio.charset.StandardCharsets

import org.goldenport.bag.Bag
import org.goldenport.http.HttpRequest
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 21, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
class DebugHttpEchoSpec extends AnyWordSpec with Matchers {

  "Debug HTTP echo" should {
    "reflect GET query parameters" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val req = HttpRequest.fromPath(
        method = HttpRequest.GET,
        path = "/debug/http/echo",
        query = Record.create(Vector("x" -> "1"))
      )
      val res = subsystem.executeHttp(req)
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
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val payload = Bag.text("hello", StandardCharsets.UTF_8)
      val header = Record.create(Vector("Content-Type" -> "text/plain"))
      val req = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/debug/http/echo",
        header = header,
        body = Some(payload)
      )
      val res = subsystem.executeHttp(req)
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
