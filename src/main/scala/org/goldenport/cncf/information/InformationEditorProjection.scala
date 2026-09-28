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
 * @since   May. 21, 2026
 *  version Aug. 31, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InformationEditorProjection(
  componentName: String,
  domain: String,
  fields: Vector[InformationFieldDescriptor],
  information: Vector[InformationEditorRecordProjection],
  output: InformationProjectionDescriptor = InformationProjectionContract.output,
  createApplicationInput: InformationProjectionDescriptor = InformationProjectionContract.createApplicationInput,
  conditionalUpdate: InformationConditionalUpdateProjection = InformationProjectionContract.conditionalUpdate
)
