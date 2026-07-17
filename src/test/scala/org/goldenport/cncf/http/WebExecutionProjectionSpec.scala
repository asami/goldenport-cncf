package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebExecutionProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Web execution projection" should {
    "publish only the canonical execution context shape" in {
      Given("a standalone anonymous Web execution policy")
      val projection = WebExecutionProjection.create(
        Locale.forLanguageTag("ja-JP"),
        ZoneId.of("Asia/Tokyo"),
        WebExecutionProjectionPolicy(),
        WebExecutionSubjectProjection.ANONYMOUS,
        Vector.empty
      )

      When("the public page context is projected")
      val json = projection.toPageContextJson

      Then("the execution root has stable locale, timezone, format, mode, subject, and capability fields")
      json.hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("ja-JP")
      json.hcursor.downField("execution").get[String]("timezone").toOption shouldBe Some("Asia/Tokyo")
      json.hcursor.downField("execution").downField("format").get[String]("date").toOption shouldBe Some("localized-medium")
      json.hcursor.downField("execution").downField("format").get[String]("dateTime").toOption shouldBe Some("application-default")
      json.hcursor.downField("execution").get[String]("applicationMode").toOption shouldBe Some("standalone")
      json.hcursor.downField("execution").downField("subject").get[Boolean]("authenticated").toOption shouldBe Some(false)
      json.hcursor.downField("execution").get[Vector[String]]("capabilities").toOption shouldBe Some(Vector.empty)
      projection.toPageContextRecord.asMap.keySet shouldBe Set("execution")
      projection.toPageContextRecord.asMap("execution") shouldBe projection.toRecord
      projection.toRecord.asMap("locale") shouldBe "ja-JP"
      projection.toRecord.asMap("timezone") shouldBe "Asia/Tokyo"
      projection.toRecord.asMap("format") shouldBe projection.format.toRecord
    }

    "preserve an explicit public display name without accepting internal subject data" in {
      Given("an authenticated subject with an explicit public display name")
      val displayname = "<Admin & Operator>"
      val subject = WebExecutionSubjectProjection.create(true, Some(displayname))

      When("the subject is projected")
      val projection = WebExecutionProjection.create(
        Locale.ENGLISH,
        ZoneId.of("UTC"),
        WebExecutionProjectionPolicy(),
        subject,
        Vector.empty
      )
      val json = projection.toPageContextJson

      Then("the typed public value is retained and internal identity fields cannot appear")
      json.hcursor.downField("execution").downField("subject").get[String]("displayName").toOption shouldBe Some(displayname)
      val text = json.noSpaces
      text should not include "sessionId"
      text should not include "principalId"
      text should not include "attributes"
      text should not include "token"
    }

    "deny capability publication unless the capability is explicitly allowlisted" in {
      Given("effective capabilities containing public and internal grants")
      val policy = WebExecutionProjectionPolicy(
        publicCapabilities = Vector("knowledge:read", "page_admin")
      )

      When("the effective capability set is projected")
      val selected = policy.selectPublicCapabilities(
        Vector("page-admin", "knowledge:read", "internal:root", "knowledge:read")
      )

      Then("only normalized allowlisted capabilities are published in deterministic order")
      selected shouldBe Vector("knowledge:read", "pageadmin")
      WebExecutionProjectionPolicy().selectPublicCapabilities(Vector("knowledge:read")) shouldBe Vector.empty
    }

    "select public capabilities deterministically for arbitrary input order and duplication" in {
      Given("generated effective and public capability sequences")
      val capability = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString)
      val property = Prop.forAll(Gen.listOf(capability), Gen.listOf(capability)) { (effective, public) =>
        val forward = WebExecutionProjectionPolicy(publicCapabilities = public.toVector)
          .selectPublicCapabilities(effective)
        val reversed = WebExecutionProjectionPolicy(publicCapabilities = (public.reverse ++ public).toVector)
          .selectPublicCapabilities(effective.reverse ++ effective)
        val expected = effective.toSet.intersect(public.toSet).toVector.sorted
        forward == expected && reversed == expected
      }

      When("the capability selector is checked repeatedly")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("selection is stable and equal to the set intersection")
      checked.passed shouldBe true
    }

    "parse only canonical application modes and stable display format policy identifiers" in {
      Given("canonical, compatibility, and invalid public policy values")
      When("the values are parsed")
      val canonical = WebApplicationMode.parse("multi-user")
      val compatibility = WebApplicationMode.parse("multi_user")
      val validformat = WebDisplayFormatPolicyId.parse("localized-short")
      val invalidformat = WebDisplayFormatPolicyId.parse("java.time.Format@123")

      Then("canonical values are emitted and implementation formatter identities are rejected")
      canonical shouldBe Some(WebApplicationMode.MultiUser)
      compatibility.map(_.name) shouldBe Some("multi-user")
      validformat.map(_.name) shouldBe Some("localized-short")
      invalidformat shouldBe None
    }
  }
}
