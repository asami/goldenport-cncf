package org.goldenport.cncf.validation

import ValidationMetadataV1.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Local acceptance membership; aggregate body time is a guideline, not a timeout.
 * @since   Oct. 26, 2026
 * @version Oct. 26, 2026
 */
class AdmissionTestDeclaration extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(
    purposes = Some(Set(Purpose.ADMISSION, Purpose.FOCUSED, Purpose.FULL)),
    features = Set(FeatureReference("component:goldenport-cncf", "validation-metadata")),
    durationClass = Some(DurationClass.NORMAL),
    executionRequirement = Some(ExecutionRequirement(Vector.empty,
      RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false)))
}
