package org.goldenport.cncf.naming

import io.circe.parser.parse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 01, 2026
 * @version Jun. 01, 2026
 * @author  ASAMI, Tomoharu
 */
final class PropertyValueResolverSpec extends AnyWordSpec with Matchers {
  "PropertyValueResolver" should {
    "resolve map values by camel snake and kebab property path aliases" in {
      val values = Map(
        "result.body.data.current.workTitle" -> "源氏物語",
        "pageContext.sessionAuthenticated" -> "true"
      )

      PropertyValueResolver.value(values, "result.body.data.current.workTitle") shouldBe Some("源氏物語")
      PropertyValueResolver.value(values, "result.body.data.current.work_title") shouldBe Some("源氏物語")
      PropertyValueResolver.value(values, "result.body.data.current.work-title") shouldBe Some("源氏物語")
      PropertyValueResolver.value(values, "pageContext.session-authenticated") shouldBe Some("true")
    }

    "resolve Record values by property aliases without requiring duplicate fields" in {
      val record = Record.dataAuto(
        "workTitle" -> "源氏物語",
        "textualWorkInformationId" -> "info-1",
        "rdfUri" -> "https://example.test/book/1",
        "isbn13" -> "9784003510179"
      )

      PropertyValueResolver.recordString(record, "work_title") shouldBe Some("源氏物語")
      PropertyValueResolver.recordString(record, "textual-work-information-id") shouldBe Some("info-1")
      PropertyValueResolver.recordString(record, "rdf_uri") shouldBe Some("https://example.test/book/1")
      PropertyValueResolver.recordString(record, "isbn-13") shouldBe Some("9784003510179")
    }

    "resolve JSON fields by property aliases segment by segment" in {
      val json = parse(
        """{"data":{"current":{"workTitle":"源氏物語","textualWorkInformationId":"info-1","rdfUri":"https://example.test/book/1","isbn13":"9784003510179"}}}"""
      ).toOption.get

      PropertyValueResolver.jsonAt(json, Vector("data", "current", "work_title")).flatMap(_.asString) shouldBe Some("源氏物語")
      PropertyValueResolver.jsonAt(json, Vector("data", "current", "textual-work-information-id")).flatMap(_.asString) shouldBe Some("info-1")
      PropertyValueResolver.jsonAt(json, Vector("data", "current", "rdf_uri")).flatMap(_.asString) shouldBe Some("https://example.test/book/1")
      PropertyValueResolver.jsonAt(json, Vector("data", "current", "isbn-13")).flatMap(_.asString) shouldBe Some("9784003510179")
    }

    "not silently choose an ambiguous map value" in {
      val values = Map(
        "result.current.workTitle" -> "源氏物語",
        "result.current.work_title" -> "古い値"
      )

      PropertyValueResolver.value(values, "result.current.work-title") shouldBe None
    }

    "allow duplicate aliases when they carry the same value" in {
      val values = Map(
        "result.current.workTitle" -> "源氏物語",
        "result.current.work_title" -> "源氏物語"
      )

      PropertyValueResolver.value(values, "result.current.work-title") shouldBe Some("源氏物語")
    }

    "not silently choose an ambiguous Record value" in {
      val record = Record.dataAuto(
        "workTitle" -> "源氏物語",
        "work_title" -> "古い値"
      )

      PropertyValueResolver.recordString(record, "work-title") shouldBe None
    }

    "not silently choose an ambiguous JSON value" in {
      val json = parse("""{"current":{"workTitle":"源氏物語","work_title":"古い値"}}""").toOption.get

      PropertyValueResolver.jsonAt(json, Vector("current", "work-title")).flatMap(_.asString) shouldBe None
    }
  }
}
