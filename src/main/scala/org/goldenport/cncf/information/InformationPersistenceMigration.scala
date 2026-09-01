package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.value.given_ValueReader_Instant
import org.simplemodeling.model.value.LifecycleAttributes

/*
 * Explicit persisted-record admission for Information roots predating the
 * generated revision and lifecycle representation.
 *
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[information] object InformationPersistenceMigration {
  object Preview {
    final case class Canonical(record: Record) extends Preview
    final case class LegacyV0(record: Record) extends Preview
    final case class Incompatible(code: String) extends Preview
  }

  sealed trait Preview

  val failurePrefix = "information-persistence-incompatible"

  private val _camelcase_flat_lifecycle_keys = Set(
    "createdAt",
    "updatedAt",
    "createdBy",
    "updatedBy",
    "postStatus",
    "aliveness"
  )

  private val _physical_flat_lifecycle_keys = Set(
    "created_at",
    "updated_at",
    "created_by",
    "updated_by",
    "post_status",
    "aliveness"
  )

  private val _shared_flat_lifecycle_keys =
    _camelcase_flat_lifecycle_keys intersect _physical_flat_lifecycle_keys

  private val _camelcase_flat_lifecycle_only_keys =
    _camelcase_flat_lifecycle_keys -- _shared_flat_lifecycle_keys

  private val _physical_flat_lifecycle_only_keys =
    _physical_flat_lifecycle_keys -- _shared_flat_lifecycle_keys

  /**
   * Classifies without changing the supplied record.  The complete flattened
   * lifecycle shape is canonical in either generated camelCase or physical
   * EntityStore snake_case spelling; lifecycleAttributes is the equivalent
   * logical shape.
   */
  def preview(record: Record): Preview = {
    val hasrevision = record.getAny("revision").isDefined
    val hasgroupedlifecycle = record.getAny("lifecycleAttributes").isDefined
    val hascompletegroupedlifecycle =
      _has_complete_grouped_lifecycle(record)
    val fieldkeys = record.fields.iterator.map(_.key).toSet
    val presentcamelcaseflatlifecycle =
      fieldkeys intersect _camelcase_flat_lifecycle_keys
    val presentphysicalflatlifecycle =
      fieldkeys intersect _physical_flat_lifecycle_keys
    val hasmixedflatlifecycle =
      (presentcamelcaseflatlifecycle intersect _camelcase_flat_lifecycle_only_keys).nonEmpty &&
        (presentphysicalflatlifecycle intersect _physical_flat_lifecycle_only_keys).nonEmpty
    val hascompletecamelcaseflatlifecycle =
      presentcamelcaseflatlifecycle == _camelcase_flat_lifecycle_keys &&
        (presentphysicalflatlifecycle -- _shared_flat_lifecycle_keys).isEmpty
    val hascompletephysicalflatlifecycle =
      presentphysicalflatlifecycle == _physical_flat_lifecycle_keys &&
        (presentcamelcaseflatlifecycle -- _shared_flat_lifecycle_keys).isEmpty
    val hasflatlifecycle =
      presentcamelcaseflatlifecycle.nonEmpty || presentphysicalflatlifecycle.nonEmpty
    val haslifecycle =
      hascompletegroupedlifecycle ||
        hascompletecamelcaseflatlifecycle ||
        hascompletephysicalflatlifecycle

    if (hasgroupedlifecycle && hasflatlifecycle)
      _incompatible("conflicting-legacy-and-canonical-audit-evidence")
    else if (hasmixedflatlifecycle)
      _incompatible("partial-or-mixed-revision-lifecycle")
    else if (hasrevision && haslifecycle)
      Preview.Canonical(record)
    else if (
      !hasrevision &&
        !hasgroupedlifecycle &&
        presentcamelcaseflatlifecycle == Set("updatedAt") &&
        presentphysicalflatlifecycle.isEmpty
    )
      _legacy_v0(record)
    else if (!hasrevision && !hasgroupedlifecycle && !hasflatlifecycle)
      _incompatible("legacy-v0-missing-updatedAt")
    else
      _incompatible("partial-or-mixed-revision-lifecycle")
  }

  def canonicalRecordC(record: Record): Consequence[Record] =
    preview(record) match {
      case Preview.Canonical(canonical) => Consequence.success(canonical)
      case Preview.LegacyV0(canonical) => Consequence.success(canonical)
      case Preview.Incompatible(code) =>
        Consequence.stateInvalid(code)
    }

  private def _has_complete_grouped_lifecycle(record: Record): Boolean =
    record.getAny("lifecycleAttributes") match {
      case Some(_: LifecycleAttributes) => true
      case Some(group: Record) =>
        val keys = group.fields.iterator.map(_.key).toSet
        val camelcase = keys intersect _camelcase_flat_lifecycle_keys
        val physical = keys intersect _physical_flat_lifecycle_keys
        val mixed =
          (camelcase intersect _camelcase_flat_lifecycle_only_keys).nonEmpty &&
            (physical intersect _physical_flat_lifecycle_only_keys).nonEmpty
        !mixed && (
          camelcase == _camelcase_flat_lifecycle_keys ||
            physical == _physical_flat_lifecycle_keys
        )
      case _ => false
    }

  private def _legacy_v0(record: Record): Preview =
    record.getAsC[Instant]("updatedAt") match {
      case Consequence.Success(Some(updatedat)) =>
        _canonicalize_legacy_bindings(_legacy_record(record, updatedat)) match {
          case Consequence.Success(canonical) => Preview.LegacyV0(canonical)
          case Consequence.Failure(_) => _incompatible("legacy-v0-invalid-identity-binding")
        }
      case _ =>
        _incompatible("legacy-v0-missing-or-invalid-updatedAt")
    }

  private def _legacy_record(record: Record, updatedat: Instant): Record =
    Record(record.fields.filterNot(_.key == "updatedAt")) ++
      Record.dataAuto(
        "revision" -> EntityRevision.INITIAL,
        "lifecycleAttributes" -> Information.lifecycleAttributes(updatedat)
      )

  private def _canonicalize_legacy_bindings(record: Record): Consequence[Record] =
    for {
      identitybindings <- _canonicalize_records(
        record,
        "identityBindings"
      )(_canonicalize_binding)
      candidates <- _canonicalize_records(
        identitybindings,
        "resolutionCandidates"
      )(_canonicalize_candidate)
    } yield candidates

  private def _canonicalize_candidate(record: Record): Consequence[Record] =
    record.getAny("binding") match {
      case Some(binding: Record) =>
        _canonicalize_binding(binding).map(
          record.upsertSingle("binding", _)
        )
      case _ =>
        Consequence.success(record)
    }

  private def _canonicalize_binding(record: Record): Consequence[Record] =
    for {
      normalized <- _normalize_legacy_binding(record)
      binding <- InformationIdentityBinding.createC(normalized)
    } yield InformationEntityRepository.bindingStoreRecord(binding)

  private def _normalize_legacy_binding(record: Record): Consequence[Record] =
    for {
      rdfsubject <- _normalize_legacy_alias(
        record,
        "rdf_subject",
        "rdfSubject"
      )
      knowledgenodeid <- _normalize_legacy_alias(
        rdfsubject,
        "knowledge_node_id",
        "knowledgeNodeId"
      )
    } yield knowledgenodeid.getAny("status") match {
      case Some(_) => knowledgenodeid
      case None =>
        knowledgenodeid.upsertSingle(
          "status",
          InformationBindingStatus.candidate
        )
    }

  private def _normalize_legacy_alias(
    record: Record,
    legacy: String,
    canonical: String
  ): Consequence[Record] =
    (record.getAny(legacy), record.getAny(canonical)) match {
      case (Some(_), Some(_)) =>
        Consequence.valueInvalid(
          s"legacy identity binding cannot provide both $legacy and $canonical"
        )
      case (Some(value), None) =>
        Consequence.success(
          Record(record.fields.filterNot(_.key == legacy)) ++
            Record.dataAuto(canonical -> value)
        )
      case _ => Consequence.success(record)
    }

  private def _canonicalize_records(
    record: Record,
    key: String
  )(
    canonicalize: Record => Consequence[Record]
  ): Consequence[Record] =
    record.getAny(key) match {
      case Some(values: Seq[?]) =>
        values.foldLeft(Consequence.success(Vector.empty[Record])) { (z, value) =>
          z.flatMap { records =>
            value match {
              case nested: Record => canonicalize(nested).map(records :+ _)
              case _ => Consequence.valueInvalid(s"$key requires record values")
            }
          }
        }.map(record.upsertSingle(key, _))
      case Some(values: Array[?]) =>
        _canonicalize_records(record.upsertSingle(key, values.toVector), key)(canonicalize)
      case _ =>
        Consequence.success(record)
    }

  private def _incompatible(code: String): Preview.Incompatible =
    Preview.Incompatible(s"$failurePrefix:$code")
}
