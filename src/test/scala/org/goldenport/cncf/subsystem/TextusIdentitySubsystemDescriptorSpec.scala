package org.goldenport.cncf.subsystem

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 26, 2026
 * @version Mar. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusIdentitySubsystemDescriptorSpec extends AnyWordSpec with Matchers {
  "TextusIdentitySubsystemDescriptor" should {
    "load the canonical minimal descriptor" in {
      val descriptor = TextusIdentitySubsystemDescriptor.load().toOption.get

      descriptor.subsystemName shouldBe "textus-identity"
      descriptor.componentName shouldBe "textus-user-account"
      descriptor.coordinate shouldBe "org.simplemodeling.car:textus-user-account:0.1.0"
      descriptor.componentVersion shouldBe Some("0.1.0")
    }
  }
}
