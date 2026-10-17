package org.goldenport.cncf.validation

import java.nio.file.Path
import org.goldenport.Consequence

/** Scoped actual ScalaTest inventory and pure selection contract.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
object ValidationDiscoveryV1 {
  enum Completeness {
    case COMPLETE, PARTIAL, UNKNOWN
  }

  final case class OperationReference(component: String, specification: String, operation: String)
  final case class Provenance(
    artifactIdentity: String,
    artifactRevision: String,
    sourceRevision: String,
    operationRevision: String,
    abiVersion: Int = ValidationMetadataV1.ABI_VERSION
  )
  final case class ScalaTestSelector(suiteClassName: String, testName: String)
  final case class ArtifactScope(
    componentIdentity: String,
    artifactIdentity: String,
    artifactRevision: String,
    sourceRevision: String,
    root: Path,
    packagePrefix: String
  )
  final case class DiscoveryScope(identity: String, revision: String, artifacts: Vector[ArtifactScope])
  final case class Diagnostic(code: String, source: String, message: String)
  final case class Operation(
    reference: OperationReference,
    provenance: Provenance,
    selector: ScalaTestSelector,
    metadata: Option[ValidationMetadataV1.Metadata],
    diagnostics: Vector[Diagnostic]
  )
  final case class Inventory(
    scope: DiscoveryScope,
    completeness: Completeness,
    operations: Vector[Operation],
    diagnostics: Vector[Diagnostic]
  )
  final case class SelectionQuery(
    purposes: Set[ValidationMetadataV1.Purpose] = Set.empty,
    features: Set[ValidationMetadataV1.FeatureReference] = Set.empty
  )

  def discoverC(scope: DiscoveryScope, loader: ClassLoader): Consequence[Inventory] =
    ValidationDiscovery.discoverC(scope, loader)

  def selectC(inventory: Inventory, query: SelectionQuery): Consequence[Vector[Operation]] =
    ValidationDiscoverySelection.selectC(inventory, query)

  def resolveC(inventory: Inventory, reference: OperationReference): Consequence[Operation] =
    ValidationDiscoverySelection.resolveC(inventory, reference)
}
