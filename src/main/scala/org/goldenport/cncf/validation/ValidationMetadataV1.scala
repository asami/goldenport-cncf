package org.goldenport.cncf.validation

import scala.concurrent.duration.FiniteDuration
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ExecutionRequirement

/** Source declarations and normalized operation metadata, independent of discovery.
 * @since   Oct. 18, 2026
 * @version Oct. 19, 2026
 */
object ValidationMetadataV1 {
  val ABI_VERSION: Int = 1

  enum Purpose {
    case SMOKE, FOCUSED, ADMISSION, FULL, HEAVY
  }

  enum DurationClass {
    case NORMAL, LONG_RUNNING
  }

  final case class FeatureReference(scope: String, identity: String) {
    def validateC: Consequence[FeatureReference] =
      ValidationMetadataResolution.validateFeatureC(this)
  }

  final case class Declaration(
    abiVersion: Int = ABI_VERSION,
    purposes: Option[Set[Purpose]] = None,
    features: Set[FeatureReference] = Set.empty,
    expectedDuration: Option[FiniteDuration] = None,
    durationClass: Option[DurationClass] = None,
    executionRequirement: Option[ExecutionRequirement] = None,
    requiredSemantics: Set[String] = Set.empty,
    inputSchema: Option[Vector[ValidationInputsV1.Parameter]] = None
  ) {
    def validateC: Consequence[Declaration] =
      ValidationMetadataResolution.validateDeclarationC(this).map(x => copy(inputSchema = x.inputSchema))
  }

  final case class Origins(specification: Boolean, operation: Boolean)

  final case class Metadata(
    purposes: Set[Purpose],
    features: Set[FeatureReference],
    expectedDuration: Option[FiniteDuration],
    durationClass: DurationClass,
    executionRequirement: Option[ExecutionRequirement],
    origins: Origins,
    inputSchema: Vector[ValidationInputsV1.Parameter] = Vector.empty
  )

  def resolveC(
    specification: Option[Declaration],
    operation: Option[Declaration]
  ): Consequence[Metadata] =
    ValidationMetadataResolution.resolveC(specification, operation)
}
