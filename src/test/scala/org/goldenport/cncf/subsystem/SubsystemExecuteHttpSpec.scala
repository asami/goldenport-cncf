package org.goldenport.cncf.subsystem

import org.goldenport.http.HttpRequest
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
class SubsystemExecuteHttpSpec extends AnyWordSpec with Matchers with OptionValues {

  "Subsystem.executeHttp" should {

    "return ok for admin.system.ping" in {
      val subsystem = DefaultSubsystemFactory.default()
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 200
      res.getString.value.should(include("ok"))
    }

    "return not found for unknown operation" in {
      val subsystem = DefaultSubsystemFactory.default()
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 404
    }
  }
}
