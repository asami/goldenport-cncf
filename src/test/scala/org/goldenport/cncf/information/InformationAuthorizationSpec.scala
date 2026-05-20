package org.goldenport.cncf.information

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationAuthorizationSpec
  extends AnyWordSpec
  with Matchers {

  "InformationCapabilities" should {
    "define canonical capability names without domain-specific names" in {
      InformationCapabilities.all should contain allOf (
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
      InformationCapabilities.all.exists(_.startsWith("paper_information")) shouldBe false
    }
  }
}
