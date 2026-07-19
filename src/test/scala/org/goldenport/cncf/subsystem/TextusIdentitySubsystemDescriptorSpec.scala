package org.goldenport.cncf.subsystem

import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 26, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextusIdentitySubsystemDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "TextusIdentitySubsystemDescriptor" should {
    "load the component coordinate independently from the subsystem version" in {
      Given("a canonical descriptor whose subsystem and component versions differ")
      val directory = Files.createTempDirectory("textus-identity-subsystem-descriptor")
      val path = directory.resolve("subsystem-descriptor.yaml")
      Files.writeString(
        path,
        """subsystem: textus-identity
          |version: 0.1.1-SNAPSHOT
          |components:
          |  - component: textus-user-account
          |    coordinate: org.simplemodeling.car:textus-user-account:0.6.0-SNAPSHOT
          |""".stripMargin
      )

      When("the Textus identity descriptor is loaded through the structured subsystem parser")
      val descriptor = TextusIdentitySubsystemDescriptor.load(path).toOption.get

      Then("the component identity comes from its binding rather than the subsystem version")
      descriptor.subsystemName shouldBe "textus-identity"
      descriptor.componentName shouldBe "textus-user-account"
      descriptor.componentVersion shouldBe "0.6.0-SNAPSHOT"
      descriptor.componentVersionOption shouldBe Some("0.6.0-SNAPSHOT")
    }
  }
}
