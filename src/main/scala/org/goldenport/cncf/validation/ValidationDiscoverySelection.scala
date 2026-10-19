package org.goldenport.cncf.validation

import org.goldenport.Consequence
import ValidationDiscoveryV1.*
import ValidationMetadataV1.*
import ValidationArtifactEnumeration.{textValid, packageValid, classValid, diagnosticCodes}
import ValidationDiscovery.{failure, ordered}

/** Pure admission, purpose/feature queries and exact supplemental references.
 * @since   Oct. 18, 2026
 * @version Oct. 19, 2026
 */
private[validation] object ValidationDiscoverySelection {
  def selectC(inventory: Inventory, query: SelectionQuery): Consequence[Vector[Operation]] =
    _inventory(inventory).flatMap { _ =>
      _query(query).map { _ =>
        val ambiguous = inventory.operations.groupBy(_.reference).collect {
          case (reference, operations) if operations.size > 1 => reference
        }.toSet
        ordered(inventory.operations.filter { operation =>
          !ambiguous.contains(operation.reference) && operation.metadata.exists { metadata =>
            (query.purposes.isEmpty || metadata.purposes.exists(query.purposes.contains)) &&
              (query.features.isEmpty || metadata.features.exists(query.features.contains))
          }
        })
      }
    }

  def resolveC(inventory: Inventory, reference: OperationReference): Consequence[Operation] =
    _inventory(inventory).flatMap { _ =>
      _reference(reference).flatMap { _ =>
        inventory.operations.filter(_.reference == reference) match {
          case Vector() => failure("reference", "absent", "present exact logical operation reference")
          case Vector(operation) if operation.metadata.nonEmpty => Consequence.success(operation)
          case Vector(_) => failure("reference", "invalid", "valid normalized operation metadata")
          case _ => failure("reference", "ambiguous", "one unambiguous logical operation reference")
        }
      }
    }

  private def _query(query: SelectionQuery): Consequence[Unit] =
    if (query == null || query.purposes == null || query.features == null || query.purposes.contains(null)) {
      failure("query", "query", "non-null typed purpose and feature sets")
    } else {
      query.features.toVector.foldLeft(Consequence.unit) { (result, feature) =>
        result.flatMap { _ =>
          if (feature == null || feature.validateC.toOption.isEmpty) failure("features", "feature", "valid scoped feature")
          else Consequence.unit
        }
      }
    }

  private def _inventory(inventory: Inventory): Consequence[Unit] = {
    if (inventory == null || inventory.scope == null || inventory.completeness == null ||
        inventory.operations == null || inventory.diagnostics == null) {
      failure("inventory", "null", "non-null typed inventory fields")
    } else {
      _scope(inventory.scope).flatMap { _ =>
        val count = inventory.diagnostics.size + inventory.operations.filter(_ != null).map { operation =>
          Option(operation.diagnostics).fold(0)(_.size)
        }.sum
        if (count > 256 || !_diagnostics_valid(inventory.diagnostics)) {
          failure("diagnostics", "diagnostics", "at most 256 bounded known discovery diagnostics")
        } else {
          inventory.operations.foldLeft(Consequence.unit)((result, operation) => result.flatMap(_ => _operation(operation)))
            .flatMap { _ =>
              val grouped = inventory.operations.groupBy(x => (x.reference.component, x.provenance.artifactIdentity))
              if (grouped.values.exists(_.map(_.diagnostics.size).sum > 32)) {
                failure("diagnostics", "diagnostics", "at most 32 diagnostics per artifact")
              } else if (inventory.operations.exists { operation =>
                  !inventory.scope.artifacts.exists { artifact =>
                    artifact.componentIdentity == operation.reference.component &&
                      artifact.artifactIdentity == operation.provenance.artifactIdentity &&
                      artifact.artifactRevision == operation.provenance.artifactRevision &&
                      artifact.sourceRevision == operation.provenance.sourceRevision &&
                      operation.selector.suiteClassName.startsWith(artifact.packagePrefix + ".")
                  }
                }) {
                failure("operations", "provenance", "operation within its declared artifact namespace and revisions")
              } else Consequence.unit
            }
        }
      }
    }
  }

  private def _scope(scope: DiscoveryScope): Consequence[Unit] = {
    if (!textValid(scope.identity) || !textValid(scope.revision) || scope.artifacts == null ||
        scope.artifacts.contains(null)) {
      failure("scope", "scope", "exact scope identity/revision and non-null artifact vector")
    } else if (scope.artifacts.map(x => (x.componentIdentity, x.artifactIdentity)).distinct.size != scope.artifacts.size) {
      failure("artifacts", "duplicate-artifact", "distinct component/artifact keys")
    } else if (scope.artifacts.exists { artifact =>
        !Vector(artifact.componentIdentity, artifact.artifactIdentity, artifact.artifactRevision,
          artifact.sourceRevision).forall(textValid) || !packageValid(artifact.packagePrefix) ||
          artifact.root == null || !artifact.root.isAbsolute || artifact.root.normalize != artifact.root
      }) {
      failure("scope", "artifact", "well-formed artifact scope")
    } else Consequence.unit
  }

  private def _operation(operation: Operation): Consequence[Unit] = {
    if (operation == null || operation.provenance == null || operation.selector == null ||
        operation.metadata == null || operation.metadata.contains(null) || operation.diagnostics == null) {
      failure("operation", "null", "non-null operation data and optional metadata")
    } else if (operation.provenance.abiVersion != ABI_VERSION) {
      failure("abiVersion", "abi", "supported metadata ABI version 1")
    } else {
      _reference(operation.reference).flatMap { _ =>
        val provenance = operation.provenance
        if (!Vector(provenance.artifactIdentity, provenance.artifactRevision, provenance.sourceRevision,
            provenance.operationRevision).forall(textValid) || !classValid(operation.selector.suiteClassName) ||
            !textValid(operation.selector.testName) || !_diagnostics_valid(operation.diagnostics)) {
          failure("operation", "operation", "well-formed provenance, framework selector and diagnostics")
        } else operation.metadata match {
          case None => Consequence.unit
          case Some(metadata) => _metadata(metadata)
        }
      }
    }
  }

  private def _metadata(metadata: Metadata): Consequence[Unit] = {
    if (metadata.origins == null || metadata.inputSchema == null)
      failure("metadata", "metadata", "normalized metadata origins and input schema")
    else {
      val declaration = Declaration(purposes = Some(metadata.purposes), features = metadata.features,
        expectedDuration = metadata.expectedDuration, durationClass = Some(metadata.durationClass),
        executionRequirement = metadata.executionRequirement,
        requiredSemantics = if (metadata.inputSchema.nonEmpty) Set("inputSchema") else Set.empty,
        inputSchema = Some(metadata.inputSchema))
      val normalized = ValidationMetadataV1.resolveC(None, Some(declaration)).toOption
      val canonicalduration = metadata.expectedDuration != null && metadata.expectedDuration.forall { duration =>
        duration != null && duration.unit == java.util.concurrent.TimeUnit.NANOSECONDS
      }
      if (canonicalduration && normalized.exists(_.copy(origins = metadata.origins) == metadata)) Consequence.unit
      else failure("metadata", "metadata", "valid canonical metadata")
    }
  }

  private def _reference(reference: OperationReference): Consequence[Unit] =
    if (reference == null || !Vector(reference.component, reference.specification, reference.operation).forall(textValid)) {
      failure("reference", "reference", "exact nonempty bounded logical identities")
    } else Consequence.unit

  private def _diagnostics_valid(diagnostics: Vector[Diagnostic]): Boolean =
    diagnostics != null && diagnostics.forall { value =>
      value != null && diagnosticCodes.contains(value.code) && value.source != null && value.message != null &&
        value.source.length <= 512 && value.message.length <= 512 &&
        !value.source.exists(_.isControl) && !value.message.exists(_.isControl)
    }
}
