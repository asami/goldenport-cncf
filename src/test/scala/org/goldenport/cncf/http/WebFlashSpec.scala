package org.goldenport.cncf.http

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebFlashSpec extends AnyWordSpec with Matchers {
  "WebFlash" should {
    "round-trip a bounded message key without carrying display text" in {
      val value = WebFlash.Value("success", "exhibition.review.saved")
      val encoded = WebFlash.encode(value).getOrElse(fail("flash value was not encoded"))

      encoded should not include "saved"
      WebFlash.decode(encoded) shouldBe Some(value)
    }

    "reject malformed, unsupported, and oversized values" in {
      WebFlash.encode(WebFlash.Value("script", "notice.saved")) shouldBe None
      WebFlash.encode(WebFlash.Value("success", "notice saved")) shouldBe None
      WebFlash.decode("not-base64-%") shouldBe None
      WebFlash.decode("x" * 513) shouldBe None
    }

    "scope cookie names to the owning component" in {
      WebFlash.cookieName("notice_board") shouldBe "textus-flash-notice-board"
    }
  }
}
