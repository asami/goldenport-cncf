package org.goldenport.cncf.http

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebOperationAuthorizationPolicySpec extends AnyWordSpec with Matchers {
  "WebOperationAuthorizationPolicy" should {
    "derive admin operation authorization parameters from the admin operation selector" in {
      val subsystem = _subsystem_with_admin()
      val rule = WebOperationAuthorizationPolicy
        .operationRule(subsystem, "admin.entity.create", RuntimeConfig.default)
        .getOrElse(fail("admin operation rule is missing"))

      rule.operationModes shouldBe Vector.empty
      rule.anonymousOperationModes shouldBe Vector(OperationMode.Develop, OperationMode.Test)
      rule.allowAnonymous shouldBe true
    }

    "honor the runtime anonymous-admin switch as an operation authorization parameter source" in {
      val subsystem = _subsystem_with_admin()
      val rule = WebOperationAuthorizationPolicy
        .operationRule(
          subsystem,
          "admin.entity.create",
          RuntimeConfig.default.copy(webDevelopAnonymousAdmin = false)
        )
        .getOrElse(fail("admin operation rule is missing"))

      rule.allowAnonymous shouldBe false
    }
  }

  private def _subsystem_with_admin(): Subsystem = {
    val subsystem = TestComponentFactory.emptySubsystem("web-operation-authorization-policy")
    val admin = AdminComponent.Factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin)).primary
    subsystem.add(admin)
  }
}
