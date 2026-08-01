package org.goldenport.cncf.config

import java.nio.file.Paths

import org.goldenport.Consequence
import org.goldenport.configuration.source.file.ConfigTextDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class StandaloneUserProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "StandaloneUserProfile" should {
    "decode the approved standalone-only document as typed common and subsystem values" in {
      Given("the approved textus/v1 standalone user document")
      val document = _parse(
        """apiVersion: textus/v1
          |kind: StandaloneUserProfile
          |user:
          |  id: user-1
          |  displayName: Example User
          |  locale: ja-JP
          |  timezone: Asia/Tokyo
          |subsystems:
          |  textus-art-scene:
          |    user:
          |      locale: en-US
          |""".stripMargin
      )

      When("the schema is decoded")
      val result = StandaloneUserProfile.parse(document, "approved.yaml")

      Then("it preserves optional typed fields without selecting an effective user")
      result shouldBe a[Consequence.Success[_]]
      val profile = result.toOption.get
      profile.user.flatMap(_.id) shouldBe Some("user-1")
      profile.user.flatMap(_.displayName) shouldBe Some("Example User")
      profile.user.flatMap(_.locale) shouldBe Some("ja-JP")
      profile.user.flatMap(_.timezone) shouldBe Some("Asia/Tokyo")
      profile.subsystems("textus-art-scene").locale shouldBe Some("en-US")
    }

    "accept a partial typed subsystem overlay without selecting a common user" in {
      Given("a partial standalone profile")
      val partial = _parse(
        """apiVersion: textus/v1
          |kind: StandaloneUserProfile
          |subsystems:
          |  textus-art-scene:
          |    user:
          |      timezone: Asia/Tokyo
          |""".stripMargin
      )
      When("the partial document is decoded")
      val result = StandaloneUserProfile.parse(partial, "partial.yaml")

      Then("the admitted subsystem field remains partial")
      result shouldBe a[Consequence.Success[_]]
      result.toOption.get.user shouldBe None
      result.toOption.get.subsystems("textus-art-scene").timezone shouldBe Some("Asia/Tokyo")
    }

    "reject unknown and malformed document structure" in {
      Given("invalid standalone profile contract variants")
      val invalid = Vector(
        "wrong-api" -> "apiVersion: textus/v2\nkind: StandaloneUserProfile\n",
        "wrong-kind" -> "apiVersion: textus/v1\nkind: FixedUserProfile\n",
        "unknown-envelope" -> "apiVersion: textus/v1\nkind: StandaloneUserProfile\nmode: standalone\n",
        "unknown-user" -> "apiVersion: textus/v1\nkind: StandaloneUserProfile\nuser:\n  email: x@example.test\n",
        "wrong-user-type" -> "apiVersion: textus/v1\nkind: StandaloneUserProfile\nuser: user-1\n",
        "unknown-subsystem-field" -> "apiVersion: textus/v1\nkind: StandaloneUserProfile\nsubsystems:\n  textus-art-scene:\n    locale: ja-JP\n",
        "missing-subsystem-user" -> "apiVersion: textus/v1\nkind: StandaloneUserProfile\nsubsystems:\n  textus-art-scene: {}\n"
      )

      When("the variants are decoded")
      val invalidresults = invalid.map { case (label, text) => label -> StandaloneUserProfile.parse(_parse(text), s"$label.yaml") }

      Then("each variant fails closed")
      invalidresults.foreach { case (_, result) => result shouldBe a[Consequence.Failure[_]] }
    }
  }

  private def _parse(text: String) =
    ConfigTextDecoder.decode(Paths.get("standalone-user-profile.yaml"), text).toOption.get
}
