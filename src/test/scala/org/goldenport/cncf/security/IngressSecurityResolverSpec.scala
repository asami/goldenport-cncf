package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.context.SecurityLevel
import org.goldenport.protocol.{Property, Request}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class IngressSecurityResolverSpec extends AnyWordSpec with Matchers {
  "IngressSecurityResolver" should {
    "resolve content-manager privilege from request properties" in {
      val request = Request
        .of(component = "domain", service = "entity", operation = "loadPerson")
        .copy(
          properties = List(
            Property("privilege", "application_content_manager", None)
          )
        )

      val result = IngressSecurityResolver.resolve(request)
      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
      resolved.executionContext.security.hasCapability("content-manager") shouldBe true
    }

    "deny when requested capability is not granted by resolved privilege" in {
      val request = Request
        .of(component = "domain", service = "entity", operation = "loadPerson")
        .copy(
          properties = List(
            Property("privilege", "user", None),
            Property("capability", "content_manager", None)
          )
        )

      val result = IngressSecurityResolver.resolve(request)
      result shouldBe a[Consequence.Failure[_]]
    }
  }
}
