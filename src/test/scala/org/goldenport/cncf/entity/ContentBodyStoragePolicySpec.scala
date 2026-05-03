package org.goldenport.cncf.entity

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class ContentBodyStoragePolicySpec extends AnyWordSpec with Matchers {
  "ContentBodyStoragePolicy" should {
    "keep small text content inline with charset-aware byte metadata" in {
      given ExecutionContext = ExecutionContext.create()
      val id = _id("inline")
      val record = Record.dataAuto(
        "id" -> id.value,
        "content" -> "abc",
        "content_charset" -> "UTF-8"
      )

      val stored = _success(ContentBodyStoragePolicy.prepareForSave(id, record))

      stored.getString("content") shouldBe Some("abc")
      stored.getString("content_storage") shouldBe Some("inline")
      stored.getInt("content_byte_size") shouldBe Some(3)
      stored.getString("content_charset") shouldBe Some(StandardCharsets.UTF_8.name())
    }

    "overflow large text content by encoded byte length and hydrate it back" in {
      given ExecutionContext = ExecutionContext.create()
      val id = _id("overflow")
      val text = "日本語"
      val record = Record.dataAuto(
        "id" -> id.value,
        "content" -> text,
        "content_charset" -> "UTF-8"
      )

      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        record,
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, stored))

      stored.getString("content") shouldBe None
      stored.getString("content_storage") shouldBe Some("overflow")
      stored.getInt("content_byte_size") shouldBe Some(text.getBytes(StandardCharsets.UTF_8).length)
      hydrated.getString("content") shouldBe Some(text)
    }

    "preserve existing overflow content when a partial update omits content" in {
      given ExecutionContext = ExecutionContext.create()
      val id = _id("overflow_update")
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id" -> id.value,
          "content" -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val updateRecord = stored ++ Record.dataAuto("title" -> "updated")

      val updated = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        updateRecord,
        preserveExistingOverflowOnMissingContent = true
      ))
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, updated))

      updated.getString("content_storage") shouldBe Some("overflow")
      hydrated.getString("content") shouldBe Some("日本語")
      hydrated.getString("title") shouldBe Some("updated")
    }

    "clear overflow content on explicit setNull" in {
      given ExecutionContext = ExecutionContext.create()
      val id = _id("overflow_clear")
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id" -> id.value,
          "content" -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val clearRecord = stored ++ Record.dataAuto("content" -> Update.setNull[String])

      val cleared = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        clearRecord,
        preserveExistingOverflowOnMissingContent = true
      ))
      val hydrated = _success(ContentBodyStoragePolicy.hydrate(id, cleared))

      cleared.getString("content") shouldBe None
      cleared.getString("content_storage") shouldBe None
      hydrated.getString("content") shouldBe None
    }
  }

  private def _id(entropy: String): EntityId =
    EntityId("test", "content", EntityCollectionId("test", "content", "article"), entropy = Some(entropy))

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(c) => fail(c.toString)
    }
}
