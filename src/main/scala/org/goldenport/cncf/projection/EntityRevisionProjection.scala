package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.entity.{
  EntityRevisionBinding,
  EntityRevisionRepresentation
}
import org.simplemodeling.model.datatype.EntityRevision

/*
 * Read-only projection of one admitted Entity revision.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object EntityRevisionProjection {
  def recordRevision(
    binding: Option[EntityRevisionBinding],
    record: Record
  ): Option[EntityRevision] =
    binding.collect {
      case active
          if active.representation ==
            EntityRevisionRepresentation.Embedded =>
        active
    }.flatMap(active =>
      record.getAny(active.storageFieldName).flatMap(_revision)
    )

  def responseRevision(
    response: Record
  ): Option[EntityRevision] =
    _field_revision(response, "version")
      .orElse(
        response.getRecord("data").flatMap(_field_revision(_, "version"))
      )
      .orElse(
        response.getRecord("record").flatMap(_field_revision(_, "revision"))
      )
      .orElse(
        response
          .getRecord("data")
          .flatMap(_.getRecord("record"))
          .flatMap(_field_revision(_, "revision"))
      )
      .orElse(_field_revision(response, "revision"))
      .orElse(
        response.getRecord("data").flatMap(_field_revision(_, "revision"))
      )

  def projectViewRecord(
    binding: Option[EntityRevisionBinding],
    source: Record,
    view: Record
  ): Record =
    projectRecord(binding, view, recordRevision(binding, source))

  def projectRecord(
    binding: Option[EntityRevisionBinding],
    record: Record,
    revision: Option[EntityRevision]
  ): Record =
    (binding, revision) match {
      case (
            Some(active),
            Some(current)
          ) if active.representation ==
            EntityRevisionRepresentation.Embedded =>
        record.upsertSingle(
          active.storageFieldName,
          current.value
        )
      case _ =>
        record
    }

  def projectResponse(
    binding: Option[EntityRevisionBinding],
    response: Record,
    revision: Option[EntityRevision]
  ): Record =
    (binding, revision) match {
      case (
            Some(active),
            Some(current)
          ) if active.representation ==
            EntityRevisionRepresentation.Detached =>
        response.upsertSingle("version", current.value.toString)
      case _ =>
        response
    }

  private def _field_revision(
    record: Record,
    name: String
  ): Option[EntityRevision] =
    record.getAny(name).flatMap(_revision)

  private def _revision(
    value: Any
  ): Option[EntityRevision] =
    value match {
      case revision: EntityRevision =>
        Some(revision)
      case number: java.lang.Number =>
        EntityRevision.createC(number.toString).toOption
      case text: String =>
        EntityRevision.createC(text.trim).toOption
      case _ =>
        None
    }
}
