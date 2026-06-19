package org.goldenport.cncf.http

/*
 * @since   Jun. 19, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class WebUxProfileSpec extends AnyWordSpec with Matchers {
  "WebUxProfile" should {
    "resolve built-in profile names" in {
      WebUxProfile.parse("bootstrap") shouldBe Some(WebUxProfile.Bootstrap)
      WebUxProfile.parse("material") shouldBe Some(WebUxProfile.Material)
      WebUxProfile.parse("compact") shouldBe Some(WebUxProfile.Compact)
      WebUxProfile.parse("admin") shouldBe Some(WebUxProfile.Admin)
    }

    "use bootstrap as the default generated Web profile" in {
      WebUxProfile.default shouldBe WebUxProfile.Bootstrap
      WebDescriptor().effectiveProfile shouldBe WebUxProfile.Bootstrap
    }

    "reject unknown profile names deterministically" in {
      WebUxProfile.parse("neon") shouldBe None
    }
  }
}
