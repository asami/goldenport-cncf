package org.goldenport.cncf.subsystem

import org.goldenport.http.HttpRequest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class SubsystemExecuteHttpSpec extends AnyWordSpec with Matchers {

  "Subsystem.executeHttp" should {

    "return runtime introspection for canonical slash route /admin/system/ping" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 200
      res.getString.getOrElse("") should include ("runtime: goldenport-cncf")
    }

    "allow compatibility dot route /admin.system.ping for now" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin.system.ping")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 200
      res.getString.getOrElse("") should include ("runtime: goldenport-cncf")
    }

    "return not found for unknown operation" in {
      val subsystem = DefaultSubsystemFactory.default()
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 404
    }
  }

}
