package org.goldenport.cncf.http

/*
 * @since   Jun. 19, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class TextusWidgetVocabularySpec extends AnyWordSpec with Matchers {
  "TextusWidgetVocabulary" should {
    "resolve built-in widgets by canonical namespace name" in {
      val table = TextusWidgetVocabulary.lookup("textus:table").get

      table.canonicalName shouldBe "textus:table"
      table.category shouldBe TextusWidgetCategory.Display
      table.sourceBinding shouldBe true
      table.viewBinding shouldBe true
      table.columnsBinding shouldBe true
    }

    "resolve existing dash aliases for compatibility" in {
      TextusWidgetVocabulary.lookup("textus-result-view").map(_.canonicalName) shouldBe Some("textus:result-view")
      TextusWidgetVocabulary.lookup("textus-line-list").map(_.canonicalName) shouldBe Some("textus:line-list")
      TextusWidgetVocabulary.lookup("textus-error-panel").map(_.canonicalName) shouldBe Some("textus:error-panel")
      TextusWidgetVocabulary.lookup("textus-action-form").map(_.canonicalName) shouldBe Some("textus:action-form")
      TextusWidgetVocabulary.lookup("textus-table") shouldBe None
    }

    "classify line-list as display-only vocabulary" in {
      val definition = TextusWidgetVocabulary.lookup("textus:line-list").get

      definition.category shouldBe TextusWidgetCategory.Display
      definition.implemented shouldBe true
      definition.actionBinding shouldBe false
    }

    "classify editable-line-list as implemented form-edit vocabulary" in {
      val definition = TextusWidgetVocabulary.lookup("textus:editable-line-list").get

      definition.category shouldBe TextusWidgetCategory.FormEdit
      definition.implemented shouldBe true
      definition.sourceBinding shouldBe true
      definition.optionalAttributes should contain ("add-label")
      TextusWidgetVocabulary.lookup("textus-editable-line-list") shouldBe None
    }

    "project normalized widget metadata for renderer and demo consumers" in {
      val projection = TextusWidgetVocabulary.project(
        "textus:card-list",
        Map("source" -> "result.items", "view" -> "summary")
      ).toOption.get

      projection.requestedName shouldBe "textus:card-list"
      projection.canonicalName shouldBe "textus:card-list"
      projection.category shouldBe "display"
      projection.implemented shouldBe true
      projection.attributes should contain ("source" -> "result.items")
      projection.metadataSources should contain ("cml-schema-view")
      projection.metadataSources should contain ("web-descriptor")
      projection.metadataSources should contain ("operation-result")
      projection.metadataSources should contain ("page-context")
      projection.metadataSources should contain ("widget-attributes")
    }

    "fail unknown widget projection deterministically" in {
      TextusWidgetVocabulary.lookup("textus:not-a-widget") shouldBe None
      TextusWidgetVocabulary.project("textus:not-a-widget") shouldBe Left("Unknown Textus widget: textus:not-a-widget")
    }
  }
}
