package org.goldenport.cncf.entity

import java.nio.charset.{Charset, StandardCharsets}
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{
  DataStore,
  EntityVersionedSideEffect
}
import org.goldenport.record.Record
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.datatype.EntityId

/*
 * Storage policy for SimpleEntity content bodies.
 *
 * The logical model is ContentBody. CT-01 uses text bodies for HTML/Markdown,
 * but the policy boundary is intentionally content-body oriented rather than
 * String-column oriented so a later binary ContentBody variant can use the same
 * inline/overflow decision without leaking DB storage details to applications.
 *
 * Small text content stays inline in the entity record and large content moves
 * to a side collection using charset-aware byte sizing, so DB VARCHAR limits
 * and multibyte text do not leak into application models.
 *
 * @since   May.  4, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object ContentBodyStoragePolicy {
  final case class Config(
    inlineByteThreshold: Int = 4096
  )

  final case class VersionedPreparation(
    record: Record,
    sideEffects: Vector[EntityVersionedSideEffect]
  )

  private val _default_config = Config()
  private val _overflow_collection =
    DataStore.CollectionId("cncf_content_body_overflow")

  def prepareForSave(
    id: EntityId,
    record: Record,
    config: Config = _default_config,
    preserveExistingOverflowOnMissingContent: Boolean = false
  )(using ctx: ExecutionContext): Consequence[Record] =
    planForVersionedSave(
      id,
      record,
      config,
      preserveExistingOverflowOnMissingContent
    ).flatMap { preparation =>
      _execute_side_effects(preparation.sideEffects).map(_ =>
        preparation.record
      )
    }

  def planForVersionedSave(
    id: EntityId,
    record: Record,
    config: Config = _default_config,
    preserveExistingOverflowOnMissingContent: Boolean = false
  ): Consequence[VersionedPreparation] =
    if (_is_set_null(record, "content"))
      Consequence.success(
        VersionedPreparation(
          _without_content(_without_overflow_fields(record)),
          Vector(_delete_effect(id))
        )
      )
    else _content(record) match {
      case Some(text) =>
        val charset = _charset(record)
        val bytes = text.getBytes(charset)
        val digest = _sha256(bytes)
        val metadata = Record.dataAuto(
          "content_byte_size" -> bytes.length,
          "content_digest" -> digest,
          "content_charset" -> charset.name()
        )
        if (bytes.length <= config.inlineByteThreshold)
          Consequence.success(
            VersionedPreparation(
              _without_overflow_fields(record) ++ metadata ++ Record.dataAuto(
                "content_storage" -> "inline"
              ),
              Vector(_delete_effect(id))
            )
          )
        else {
          val ref = _overflow_ref(id)
          val overflow = Record.dataAuto(
            "id" -> ref,
            "source_entity_id" -> id.value,
            "field" -> "content",
            "content" -> text,
            "content_byte_size" -> bytes.length,
            "content_digest" -> digest,
            "content_charset" -> charset.name()
          )
          Consequence.success(
            VersionedPreparation(
              _without_content(record) ++ metadata ++ Record.dataAuto(
                "content_storage" -> "overflow",
                "content_ref" -> ref
              ),
              Vector(
                EntityVersionedSideEffect.Save(
                  _overflow_collection,
                  DataStore.StringEntryId(ref),
                  overflow
                )
              )
            )
          )
        }
      case None if preserveExistingOverflowOnMissingContent && _has_overflow_reference(record) =>
        Consequence.success(VersionedPreparation(record, Vector.empty))
      case None =>
        Consequence.success(
          VersionedPreparation(
            _without_overflow_fields(record),
            Vector(_delete_effect(id))
          )
        )
    }

  def deleteOverflow(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _delete_overflow(id)

  def hydrate(
    id: EntityId,
    record: Record
  )(using ctx: ExecutionContext): Consequence[Record] =
    _string(record, "content_storage") match {
      case Some("overflow") =>
        val ref = _string(record, "content_ref").getOrElse(_overflow_ref(id))
        for {
          ds <- ctx.dataStoreSpace.dataStore(_overflow_collection)
          loaded <- ds.load(
            _overflow_collection,
            DataStore.StringEntryId(ref)
          )
        } yield loaded.flatMap(_content) match {
          case Some(text) => record ++ Record.dataAuto("content" -> text)
          case None => record
        }
      case _ =>
        Consequence.success(record)
    }

  def hydrateAll(
    collection: org.simplemodeling.model.datatype.EntityCollectionId,
    records: Vector[Record]
  )(using ctx: ExecutionContext): Consequence[Vector[Record]] =
    records.foldLeft(Consequence.success(Vector.empty[Record])) { (z, record) =>
      z.flatMap { xs =>
        _string(record, "id")
          .flatMap(EntityId.parse(_).toOption)
          .map(id => hydrate(id, record).map(xs :+ _))
          .getOrElse(Consequence.success(xs :+ record))
      }
    }

  private def _delete_overflow(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] =
    ctx.dataStoreSpace.dataStore(_overflow_collection).flatMap { ds =>
      ds.delete(
        _overflow_collection,
        DataStore.StringEntryId(_overflow_ref(id))
      ).recoverWith(_ => Consequence.unit)
    }

  private def _delete_effect(
    id: EntityId
  ): EntityVersionedSideEffect =
    EntityVersionedSideEffect.Delete(
      _overflow_collection,
      DataStore.StringEntryId(_overflow_ref(id))
    )

  private def _execute_side_effects(
    effects: Vector[EntityVersionedSideEffect]
  )(using
    ctx: ExecutionContext
  ): Consequence[Unit] =
    effects.foldLeft(Consequence.unit) { (z, effect) =>
      z.flatMap { _ =>
        ctx.dataStoreSpace.dataStore(effect.collection).flatMap { datastore =>
          effect match {
            case EntityVersionedSideEffect.Save(
                  collection,
                  entryid,
                  record
                ) =>
              datastore.save(collection, entryid, record)
            case EntityVersionedSideEffect.Delete(
                  collection,
                  entryid
                ) =>
              datastore.delete(collection, entryid).recoverWith(_ =>
                Consequence.unit
              )
          }
        }
      }
    }

  private def _content(record: Record): Option[String] =
    _string(record, "content")

  private def _is_set_null(record: Record, name: String): Boolean =
    record.getAny(name).exists {
      case Update.SetNull => true
      case Some(Update.SetNull) => true
      case _ => false
    }

  private def _charset(record: Record): Charset =
    _string(record, "content_charset", "charset")
      .flatMap(x => scala.util.Try(Charset.forName(x)).toOption)
      .getOrElse(StandardCharsets.UTF_8)

  private def _has_overflow_reference(record: Record): Boolean =
    _string(record, "content_storage").contains("overflow") || _string(record, "content_ref").nonEmpty

  private def _overflow_ref(id: EntityId): String =
    s"${id.value}:content"

  private def _without_content(record: Record): Record =
    Record(record.fields.filterNot(field => _normalize(field.key) == "content"))

  private def _without_overflow_fields(record: Record): Record =
    Record(record.fields.filterNot { field =>
      Set("contentstorage", "contentref").contains(_normalize(field.key))
    })

  private def _string(record: Record, names: String*): Option[String] =
    names.iterator.flatMap(record.getAny).map {
      case null => ""
      case Update.SetNull => ""
      case Some(v) => v.toString
      case v => v.toString
    }.map(_.trim).filter(_.nonEmpty).nextOption()

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

  private def _normalize(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)
}
