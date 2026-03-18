package org.goldenport.cncf.context

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 18, 2026
 * @version Mar. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionContextPrivilegeSpec extends AnyWordSpec with Matchers {
  "ExecutionContext.test(privilege)" should {
    "create user privilege context" in {
      val ctx = ExecutionContext.test(SecurityContext.Privilege.User)
      ctx.security.level shouldBe SecurityLevel("user")
      ctx.security.hasCapability("user") shouldBe true
      ctx.security.principal.attributes.get("role") shouldBe Some("user")
    }

    "create application content manager privilege context" in {
      val ctx = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      ctx.security.level shouldBe SecurityLevel("content_manager")
      ctx.security.hasCapability("content-manager") shouldBe true
      ctx.security.principal.attributes.get("role") shouldBe Some("content_manager")
    }
  }
}
