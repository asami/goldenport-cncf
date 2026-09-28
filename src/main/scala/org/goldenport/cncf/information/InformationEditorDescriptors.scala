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
final case class InformationFieldMappingDescriptor(
  targetKind: String,
  targetPath: String,
  profileLayer: String,
  description: String
)

final case class InformationFieldDescriptor(
  fieldPath: String,
  label: String,
  description: String,
  example: Option[String],
  requiredness: String,
  validationHint: Option[String],
  resolverAssisted: Boolean,
  mappings: Vector[InformationFieldMappingDescriptor]
)

final case class InformationEditorActionDescriptor(
  name: String,
  label: String,
  enabled: Boolean,
  reason: Option[String] = None
)

final case class InformationTagProjection(
  tagSpace: String,
  key: String,
  path: String,
  title: Option[String],
  description: Option[String]
)

object InformationTagProjection {
  def from(tag: Tag): InformationTagProjection =
    InformationTagProjection(
      tag.tagSpace,
      tag.key,
      tag.path,
      tag.title,
      tag.description
    )
}

final case class InformationEditorFieldProjection(
  descriptor: InformationFieldDescriptor,
  value: Option[String],
  validationIssues: Vector[InformationValidationIssue],
  resolutionCandidates: Vector[InformationResolutionCandidate],
  conflicts: Vector[InformationConflict],
  status: Option[InformationFieldEvent] = None,
  events: Vector[InformationFieldEvent] = Vector.empty
)

final case class InformationEditorRecordProjection(
  informationId: EntityId,
  revision: EntityRevision,
  domain: String,
  state: InformationLifecycleState,
  title: Option[String],
  updatedAt: Instant,
  tags: Vector[InformationTagProjection] = Vector.empty,
  fields: Vector[InformationEditorFieldProjection],
  publication: Option[InformationPublicationStatus],
  actions: Vector[InformationEditorActionDescriptor]
) {
  def informationIdString: String =
    informationId.print
}
