package org.goldenport.cncf.information

import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 *  version Jul. 30, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationAuthorizationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "InformationCapabilities" should {
    "provide the complete ordered canonical capability vector" in {
      Given("the public capability facade")
      When("its aggregate capability vector is resolved")
      val actual = InformationCapabilities.all
      Then("it preserves every canonical capability exactly once and in order")
      actual shouldBe Vector(
        "information:read",
        "information:import",
        "information:edit",
        "information:validate",
        "information:resolve",
        "information:confirm",
        "information:reject",
        "information:publish",
        "information:conflict:read",
        "information:conflict:resolve",
        "information:audit:read"
      )
      actual.exists(_.startsWith("paper_information")) shouldBe false
    }

    "keep confirmation and publication as separate canonical capabilities" in {
      Given("the public Information capability catalog")
      val capabilities = InformationCapabilities.all
      When("the confirmation and publication capabilities are resolved")
      val lifecyclecapabilities = Vector(
        InformationCapabilities.confirm,
        InformationCapabilities.publish
      )
      Then("confirmation and publication remain distinct canonical public capabilities")
      lifecyclecapabilities shouldBe Vector(
        "information:confirm",
        "information:publish"
      )
      lifecyclecapabilities.distinct should have size 2
      capabilities should contain allElementsOf lifecyclecapabilities
    }
  }
}
