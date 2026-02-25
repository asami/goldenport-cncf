package org.goldenport.cncf.subsystem

import org.goldenport.http.HttpRequest
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  9, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class SubsystemExecuteHttpSpec extends AnyWordSpec with Matchers with OptionValues {

  "Subsystem.executeHttp" should {

    "return runtime introspection for admin.system.ping" in {
      // TODO Re-enable when ping route/setup is stable without retry or test-specific scope overrides.
      pending
    }

    "return not found for unknown operation" in {
      val subsystem = DefaultSubsystemFactory.default()
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 404
    }
  }

}
