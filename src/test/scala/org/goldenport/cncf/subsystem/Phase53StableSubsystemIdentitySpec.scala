package org.goldenport.cncf.subsystem

import java.nio.file.Path

import org.goldenport.cncf.component.ComponentDescriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 30, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase53StableSubsystemIdentitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 53 stable Subsystem identity" should {
    "prefer descriptor-owned identities over a path-derived fallback" in {
      Given("component descriptors with each currently observable identity source")
      val path = Path.of("target", "test-tmp", "phase-53", "path-derived.car")
      val declared = ComponentDescriptor(
        name = Some("display-like-name"),
        componentName = Some("component-name"),
        subsystemName = Some("declared-subsystem")
      )
      val component = ComponentDescriptor(
        name = Some("display-like-name"),
        componentName = Some("component-name")
      )
      val name = ComponentDescriptor(name = Some("display-like-name"))
      val pathderived = ComponentDescriptor()

      When("the existing Component descriptor projection creates implicit Subsystem descriptors")
      val declaredresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, declared).toOption.get
      val componentresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, component).toOption.get
      val nameresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, name).toOption.get
      val pathderivedresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, pathderived).toOption.get

      Then("the descriptor controls the stable identity before any path fallback")
      declaredresult.subsystemName shouldBe "declared-subsystem"
      componentresult.subsystemName shouldBe "component-name"
      nameresult.subsystemName shouldBe "display-like-name"
      pathderivedresult.subsystemName shouldBe "path-derived"
    }
  }
}
