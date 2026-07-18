package org.goldenport.cncf.spi.ai.runner

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  9, 2026
 * @version Jul. 18, 2026
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

  "AiExecutionClass" should {
    "preserve the standard caller selector without exposing provider selection" in {
      Given("an execution-class requirement for bounded implementation work")
      val requirement = AiRunnerRequirement(
        purpose = Some("software-implementation"),
        executionClass = Some(AiExecutionClass.StandardWork)
      )

      When("the runner receives the provider-neutral requirement")
      val parsed = AiExecutionClass.parse(requirement.executionClass.map(_.id).getOrElse(""))

      Then("the logical class is retained and only its documented identifier parses")
      requirement.executionClass shouldBe Some(AiExecutionClass.StandardWork)
      parsed shouldBe Some(AiExecutionClass.StandardWork)
      AiExecutionClass.parse("gpt-5") shouldBe None
    }
  }
}
