package org.goldenport.cncf.entity

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * Authoritative physical representation of one Entity revision.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityRevisionRepresentation(
  val label: String,
  val storageFieldName: String
) {
  case Embedded
      extends EntityRevisionRepresentation("embedded", "revision")
  case Detached
      extends EntityRevisionRepresentation("detached", "cncf_revision")

  override def toString: String =
    label
}

object EntityRevisionRepresentation {
  def parseC(text: String): Consequence[EntityRevisionRepresentation] =
    parseOption(text)
      .map(Consequence.success)
      .getOrElse(
        Consequence.argumentInvalid(
          s"unknown Entity revision representation: $text"
        )
      )

  def parseOption(
    text: String
  ): Option[EntityRevisionRepresentation] =
    Option(text)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .flatMap {
        case "embedded" =>
          Some(EntityRevisionRepresentation.Embedded)
        case "detached" =>
          Some(EntityRevisionRepresentation.Detached)
        case _ =>
          None
      }
}

/*
 * Authoritative generated Entity-model classification for revision binding.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
enum EntityRevisionModelKind {
  case SimpleEntity
  case NonSimpleEntity
}

/*
 * Generated Entity-model evidence used by assembly-time revision binding.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityRevisionModelMetadata(
  kind: EntityRevisionModelKind,
  revisionRepresentation: Option[EntityRevisionRepresentation]
)

/*
 * Immutable result of Entity-model and collection declaration resolution.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityRevisionBinding(
  representation: EntityRevisionRepresentation
) {
  def storageFieldName: String =
    representation.storageFieldName

  def validatePersistedRecord(
    record: Record
  ): Consequence[Unit] = {
    val hasembedded = _has_field(record, EntityRevisionRepresentation.Embedded.storageFieldName)
    val hasdetached = _has_field(record, EntityRevisionRepresentation.Detached.storageFieldName)
    if (hasembedded && hasdetached)
      Consequence.stateConflict(
        "persisted Entity record contains both embedded revision and detached cncf_revision"
      )
    else
      representation match {
        case EntityRevisionRepresentation.Embedded if hasembedded =>
          Consequence.unit
        case EntityRevisionRepresentation.Detached if hasdetached =>
          Consequence.unit
        case EntityRevisionRepresentation.Embedded if hasdetached =>
          Consequence.stateConflict(
            "embedded Entity revision cannot use detached cncf_revision"
          )
        case EntityRevisionRepresentation.Detached if hasembedded =>
          Consequence.stateConflict(
            "detached Entity revision cannot use embedded revision"
          )
        case EntityRevisionRepresentation.Embedded =>
          Consequence.stateConflict(
            "persisted embedded Entity record is missing revision"
          )
        case EntityRevisionRepresentation.Detached =>
          Consequence.stateConflict(
            "persisted detached Entity record is missing cncf_revision"
          )
      }
  }

  private def _has_field(
    record: Record,
    fieldname: String
  ): Boolean =
    record.keySet.contains(fieldname)
}

object EntityRevisionBinding {
  def resolve(
    model: EntityRevisionModelMetadata,
    collectionRepresentation: Option[EntityRevisionRepresentation]
  ): Consequence[EntityRevisionBinding] =
    model.kind match {
      case EntityRevisionModelKind.SimpleEntity =>
        _resolve_simple_entity(model.revisionRepresentation, collectionRepresentation)
      case EntityRevisionModelKind.NonSimpleEntity =>
        _resolve_non_simple_entity(model.revisionRepresentation, collectionRepresentation)
    }

  private def _resolve_simple_entity(
    modelrepresentation: Option[EntityRevisionRepresentation],
    collectionrepresentation: Option[EntityRevisionRepresentation]
  ): Consequence[EntityRevisionBinding] =
    (modelrepresentation, collectionrepresentation) match {
      case (Some(EntityRevisionRepresentation.Embedded), None) |
          (
            Some(EntityRevisionRepresentation.Embedded),
            Some(EntityRevisionRepresentation.Embedded)
          ) =>
        Consequence.success(
          EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
        )
      case (Some(EntityRevisionRepresentation.Embedded), Some(EntityRevisionRepresentation.Detached)) |
          (Some(EntityRevisionRepresentation.Detached), _) =>
        Consequence.configurationInvalid(
          "SimpleEntity revision must be Embedded and cannot select Detached"
        )
      case (None, _) =>
        Consequence.configurationInvalid(
          "SimpleEntity requires generated Embedded revision metadata"
        )
    }

  private def _resolve_non_simple_entity(
    modelrepresentation: Option[EntityRevisionRepresentation],
    collectionrepresentation: Option[EntityRevisionRepresentation]
  ): Consequence[EntityRevisionBinding] =
    (modelrepresentation, collectionrepresentation) match {
      case (Some(EntityRevisionRepresentation.Embedded), _) |
          (None, Some(EntityRevisionRepresentation.Embedded)) =>
        Consequence.configurationInvalid(
          "non-SimpleEntity revision cannot select Embedded"
        )
      case (
            Some(EntityRevisionRepresentation.Detached),
            Some(EntityRevisionRepresentation.Embedded)
          ) =>
        Consequence.configurationInvalid(
          "conflicting Entity revision representations: model=detached, collection=embedded"
        )
      case (Some(EntityRevisionRepresentation.Detached), _) |
          (None, Some(EntityRevisionRepresentation.Detached)) =>
        Consequence.success(
          EntityRevisionBinding(EntityRevisionRepresentation.Detached)
        )
      case (None, None) =>
        Consequence.configurationInvalid(
          "non-SimpleEntity revision requires an explicit Detached declaration"
        )
    }
}
