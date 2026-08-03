package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationTargetSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-cncf-configuration-binding, example:E1, rules:GCF03-C1,C2, phase:55, slice:GCF-03"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-cncf-configuration-binding, example:E2, rules:GCF03-C3,C4, phase:55, slice:GCF-03"
  )

  "CNCF configuration target" should {
    "construct exactly four validated target forms" which {
      "E1 preserve normalized Subsystem instances and explicit default" must _e1 {
        "when an NFC-equivalent Subsystem label is constructed" in {
          Given("a decomposed Subsystem identity and explicit default instance")

          When("the identity is admitted")
          val subsystem = _take(SubsystemInstanceId.default("cafe\u0301"))

          Then("the stored identity is NFC-normalized and default remains explicit")
          subsystem.subsystem shouldBe "café"
          subsystem.instance shouldBe "default"
          SubsystemInstanceId.create(" subsystem", "default").isSuccess shouldBe false
          SubsystemInstanceId.create("subsystem", "x\u0000").isSuccess shouldBe false
          SubsystemInstanceId.create("a/b", "c").isSuccess shouldBe false
          SubsystemInstanceId.create("a", "b/c").isSuccess shouldBe false
          SubsystemInstanceId.create(".", "default").isSuccess shouldBe false
          SubsystemInstanceId.create("subsystem", "..").isSuccess shouldBe false
        }
      }

      "E2 require the containing Subsystem for ComponentInstance" must _e2 {
        "when all initial target factories are used" in {
          Given("one validated Component and Subsystem instance")
          val subsystem = _take(SubsystemInstanceId.default("orders"))
          val component = ComponentId("catalog")
          val instance = ComponentInstanceId("catalog", "default")

          When("the four target forms are constructed")
          val componentclass = _take(CncfConfigurationTarget.ComponentClass.create(component))
          val subsystemtarget = _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem))
          val componenttarget = _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, instance))

          Then("only Global, ComponentClass, SubsystemInstance, and qualified ComponentInstance exist")
          Vector(CncfConfigurationTarget.Global, componentclass, subsystemtarget, componenttarget).size shouldBe 4
          CncfConfigurationTarget.ComponentInstance.create(null, instance).isSuccess shouldBe false
          CncfConfigurationTarget.ComponentClass.create(null).isSuccess shouldBe false
          CncfConfigurationTarget.ComponentInstance.create(subsystem, ComponentInstanceId(" ", "default")).isSuccess shouldBe false
          CncfConfigurationTarget.ComponentInstance.create(subsystem, ComponentInstanceId("a-b", "default")).isSuccess shouldBe false
          componentclass shouldBe _take(CncfConfigurationTarget.ComponentClass.create(ComponentId("catalog")))
          componenttarget shouldBe _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, instance))
        }
      }
    }
  }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
