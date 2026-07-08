package org.goldenport.cncf.spi.ai.runner

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  9, 2026
 * @version Jul.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class AiRunnerSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "AiTool" should {
    "parse known aliases and preserve unknown tool tokens" in {
      Given("a mixed tool list from profile or request properties")
      val raw = "url_context google_search vendor_special"

      When("the tool list is parsed")
      val result = AiTool.parseResult(raw)

      Then("known logical tools and unknown tokens are both retained")
      result.tools shouldBe Vector(AiTool.UrlContext, AiTool.WebSearch, AiTool.Unknown("vendor_special"))
      result.unknown shouldBe Vector("vendor_special")
    }
  }
}
