package org.goldenport.cncf.component.builtin.debug

import java.nio.charset.StandardCharsets

import io.circe.parser.parse
import org.goldenport.bag.Bag
import org.goldenport.http.HttpRequest
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}
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
      val req = HttpRequest.fromPath(HttpRequest.GET, "/debug/http/echo?x=1")
      val res = subsystem.executeHttp(req)
      res.code shouldBe 200
      val json = parse(res.getString.getOrElse(fail("empty body")))
        .getOrElse(fail("invalid json"))
      json.hcursor.downField("method").as[String].getOrElse(fail("method missing")) shouldBe "GET"
      json.hcursor.downField("body").downField("present").as[Boolean].getOrElse(fail("body missing")) shouldBe false
      val queryX = json.hcursor.downField("query").downField("x").as[Vector[String]]
      queryX.fold(f => fail(f.message), values => values.headOption shouldBe Some("1"))
    }

    "reflect POST body form data" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val payload = Bag.text("hello", StandardCharsets.UTF_8)
      val contentType =
        ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8))
      val form = Record.create(Vector("payload" -> MimeBody(contentType, payload)))
      val req = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/debug/http/echo",
        form = form
      )
      val res = subsystem.executeHttp(req)
      res.code shouldBe 200
      val json = parse(res.getString.getOrElse(fail("empty body")))
        .getOrElse(fail("invalid json"))
      val bodyCursor = json.hcursor.downField("body")
      bodyCursor.downField("present").as[Boolean].getOrElse(fail("body missing")) shouldBe true
      bodyCursor.downField("size").as[Long].getOrElse(fail("size missing")) should be > 0L
      bodyCursor.downField("preview").as[String].getOrElse(fail("preview missing")).length should be > 0
    }
  }
}
