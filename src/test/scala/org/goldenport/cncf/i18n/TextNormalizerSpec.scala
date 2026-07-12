package org.goldenport.cncf.i18n

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class TextNormalizerSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "TextNormalizer display-safe-v1" should {
    "normalize Japanese display text without blanket compatibility folding" in {
      Given("Japanese text containing width variants, whitespace, invisible formatting, and decomposed accents")
      given ExecutionContext = _context(Locale.JAPAN)
      val request = TextNormalizationRequest(
        "  ＡＢＣ１２３\u3000ﾊﾝｶｸ ｶﾞ e\u0301 \u200b  text  ",
        collectProvenance = true
      )

      When("normalizing with the display-safe profile")
      val result = _success(TextNormalizer.normalize(request))

      Then("display-safe canonical text and ordered provenance are returned")
      result.text shouldBe "ABC123 ハンカク ガ é text"
      result.locale shouldBe Locale.JAPAN
      result.profile shouldBe TextNormalizer.DisplaySafeV1
      result.transforms shouldBe Vector(
        "unicode-nfc",
        "unicode-spacing",
        "invisible-format-removal",
        "whitespace-collapse",
        "ja-ascii-width",
        "ja-katakana-width"
      )
    }

    "use the execution context locale when the request omits it" in {
      Given("an English execution context")
      given ExecutionContext = _context(Locale.US)

      When("normalizing full-width text without an explicit locale")
      val result = _success(TextNormalizer.normalize(TextNormalizationRequest("Ａ１")))

      Then("Japanese width conversion is not applied")
      result.text shouldBe "Ａ１"
      result.locale shouldBe Locale.US
    }

    "not apply lossy case or punctuation folding" in {
      Given("Japanese display text with meaningful case and punctuation")
      given ExecutionContext = _context(Locale.JAPAN)

      When("normalizing it")
      val result = _success(TextNormalizer.normalize(TextNormalizationRequest("Art—ART〜アート")))

      Then("case and punctuation distinctions remain")
      result.text shouldBe "Art—ART〜アート"
    }

    "reject an unknown profile with a structured consequence" in {
      Given("an unsupported profile identifier")
      given ExecutionContext = _context(Locale.JAPAN)

      When("normalization is requested")
      val result = TextNormalizer.normalize(TextNormalizationRequest("text", profile = "unknown-v1"))

      Then("the request fails without throwing")
      result match {
        case Consequence.Success(value) => fail(s"unexpected success: $value")
        case Consequence.Failure(_) => succeed
      }
    }
  }

  private def _context(locale: Locale): ExecutionContext = {
    val base = ExecutionContext.create().asInstanceOf[ExecutionContext.Instance]
    base.copy(core = base.core.copy(locale = locale))
  }

  private def _success[A](consequence: Consequence[A]): A =
    consequence match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}
