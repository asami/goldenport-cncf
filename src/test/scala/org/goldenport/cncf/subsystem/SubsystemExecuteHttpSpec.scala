package org.goldenport.cncf.subsystem

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{RuntimeMetadata, RuntimeMetadataInfo}
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

    "return runtime introspection for admin.system.ping" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
      val res = subsystem.executeHttp(req)
      val expected = RuntimeMetadata.format(
        RuntimeMetadataInfo("server", RuntimeMetadata.SubsystemName, CncfVersion.current, CncfVersion.current)
      )

      res.code shouldBe 200
      res.getString.value.shouldBe(expected)
    }

    "return not found for unknown operation" in {
      val subsystem = DefaultSubsystemFactory.default()
      val req = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/unknown")
      val res = subsystem.executeHttp(req)

      res.code shouldBe 404
    }
  }
}
