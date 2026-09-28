package org.goldenport.cncf.information

import java.time.Instant
import domain.statemachine.informationLifecycle
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.tag.{Tag, TaggingWorkflow}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationConflict,
  InformationFieldEvent,
  InformationFieldState,
  InformationLifecycleState,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationSpaceSnapshot,
  InformationValidationIssue
}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object InformationSpaceEditorProjection {
  val InformationTagSpace: String = "information"
  val InformationTagRole: String = "information-tag"

  def component(
    component: Component,
    domain: String
  )(using ctx: ExecutionContext): Consequence[InformationEditorProjection] =
    InformationEditorProfile.forDomain(domain) match {
      case Some(profile) =>
        component.informationSpace.snapshotC.map { snapshot =>
          InformationEditorProjection(
            component.name,
            profile.domain,
            profile.fields,
            _information_projections(profile, snapshot, Map.empty)
          )
        }
      case None =>
        Consequence.argumentInvalid(s"information editor profile not found: $domain")
    }

  def componentWithTags(
    component: Component,
    domain: String
  )(using ExecutionContext): Consequence[InformationEditorProjection] =
    InformationEditorProfile.forDomain(domain) match {
      case Some(profile) =>
        component.informationSpace.snapshotC.flatMap { snapshot =>
          _information_tags(snapshot).map { tags =>
            InformationEditorProjection(
              component.name,
              profile.domain,
              profile.fields,
              _information_projections(profile, snapshot, tags)
            )
          }
        }
      case None =>
        Consequence.argumentInvalid(s"information editor profile not found: $domain")
    }

  def informationTagSourceIds(
    tagref: String,
    tagspace: String = InformationTagSpace
  )(using ExecutionContext): Consequence[Set[String]] =
    TaggingWorkflow(tagSpace = tagspace).searchSourceIds(tagref, includeDescendants = true, Some(InformationTagRole))

  def profileOption(domain: String): Option[InformationEditorProfile] =
    InformationEditorProfile.forDomain(domain)

  private def _information_tags(
    snapshot: InformationSpaceSnapshot
  )(using ExecutionContext): Consequence[Map[String, Vector[InformationTagProjection]]] =
    snapshot.information.foldLeft(Consequence.success(Map.empty[String, Vector[InformationTagProjection]])) { (z, information) =>
      z.flatMap { xs =>
        TaggingWorkflow(tagSpace = InformationTagSpace)
          .listEntityTags(information.id.print, Some(InformationTagRole))
          .map(summary => xs + (information.id.print -> summary.tags.map(InformationTagProjection.from)))
      }
    }

  private def _information_projections(
    profile: InformationEditorProfile,
    snapshot: InformationSpaceSnapshot,
    tags: Map[String, Vector[InformationTagProjection]]
  ): Vector[InformationEditorRecordProjection] =
    snapshot.information
      .filter(_.domain == profile.domain)
      .sortBy(_.lifecycleAttributes.updatedAt.toEpochMilli)
      .reverse
      .map { information =>
        InformationEditorRecordProjection(
          informationId = information.id,
          revision = information.revision,
          domain = information.domain,
          state = information.state,
          title = _title(information.workingData),
          updatedAt = information.lifecycleAttributes.updatedAt,
          tags = tags.getOrElse(information.id.print, Vector.empty),
          fields = profile.fields.map(field => _information_field_projection(field, information)),
          publication = information.publicationStatuses.headOption,
          actions = _information_actions(information)
        )
      }

  private def _information_field_projection(
    field: InformationFieldDescriptor,
    information: Information
  ): InformationEditorFieldProjection =
    InformationEditorFieldProjection(
      field,
      _value(information.workingData, field.fieldPath),
      information.validationIssues.filter(_.fieldPath == field.fieldPath),
      information.resolutionCandidates.filter(_.fieldPath == field.fieldPath).sortBy(_.candidateKey),
      information.conflicts.filter(_.fieldPath == field.fieldPath).sortBy(_.conflictKey),
      _field_events(information, field).headOption,
      _field_events(information, field)
    )

  private def _field_events(
    information: Information,
    field: InformationFieldDescriptor
  ): Vector[InformationFieldEvent] =
    information.fieldEvents.filter(_.fieldPath == field.fieldPath).sortBy(_.occurredAt.toEpochMilli).reverse

  private def _information_actions(information: Information): Vector[InformationEditorActionDescriptor] =
    Vector(
      _action("save", "Save", _save_available(information), None),
      _action("validate", "Validate", _validate_available(information), None),
      _action("resolve", "Resolve", information.resolutionCandidates.nonEmpty && _permits_transition(information, "selectResolution", InformationLifecycleState.ready_for_confirmation), Some("available when unresolved candidates exist")),
      _action("confirm", "Confirm", _confirm_available(information), Some("requires valid and resolved information")),
      _action("reject", "Reject", _permits_transition(information, "reject", InformationLifecycleState.rejected), None),
      _action("reopen", "Reopen", _permits_transition(information, "reopen", InformationLifecycleState.imported), None),
      _action("publish", "Publish", _publish_available(information), Some("requires confirmed information")),
      _action("materialize", "Materialize", information.state == InformationLifecycleState.confirmed || information.state == InformationLifecycleState.published, Some("creates KnowledgeFrame / KnowledgeSpace projection"))
    )

  private def _save_available(information: Information): Boolean =
    information.state == InformationLifecycleState.imported ||
      _permits_transition(information, "update", InformationLifecycleState.imported)

  private def _validate_available(information: Information): Boolean = {
    val (event, state) =
      if (InformationSpace.validate(information).nonEmpty)
        "validateInvalid" -> InformationLifecycleState.invalid
      else if (information.resolutionCandidates.exists(!_.selected))
        "validateNeedsResolution" -> InformationLifecycleState.needs_resolution
      else
        "validateReady" -> InformationLifecycleState.ready_for_confirmation
    _permits_transition(information, event, state)
  }

  private def _confirm_available(information: Information): Boolean =
    information.state == InformationLifecycleState.confirmed ||
      _permits_transition(information, "confirm", InformationLifecycleState.confirmed)

  private def _publish_available(information: Information): Boolean =
    information.state == InformationLifecycleState.published ||
      _permits_transition(information, "publish", InformationLifecycleState.published)

  private def _permits_transition(
    information: Information,
    event: String,
    state: InformationLifecycleState
  ): Boolean =
    informationLifecycle.permits(information.state.value, event, state.value)

  private def _action(
    name: String,
    label: String,
    enabled: Boolean,
    reason: Option[String]
  ): InformationEditorActionDescriptor =
    InformationEditorActionDescriptor(name, label, enabled, if (enabled) None else reason)

  private def _title(record: Record): Option[String] =
    _value(record, "title").orElse(_value(record, "name"))

  private def _value(
    record: Record,
    fieldpath: String
  ): Option[String] =
    record.getString(fieldpath).map(_.trim).filter(_.nonEmpty)
}
