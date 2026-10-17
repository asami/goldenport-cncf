package org.goldenport.cncf.workflow

import org.goldenport.Consequence

/** Additive Cozy Provided API metadata; the admitted Workflow ABI v1 is unchanged. */
trait GeneratedProvidedApiMetadataProvider {
  def generatedProvidedApiDefinitions: Vector[GeneratedProvidedApiAbi.Definition] = Vector.empty
}

/** Declared Provided Operation resolution; provenance is diagnostic only.
  * @since   Sep. 25, 2026
  * @version Oct.  8, 2026
  */
object GeneratedProvidedApiAbi {
  val schemaVersion = "cozy.cml.statemachine-provided-api-abi.v1"
  val generatorIdentity = "cozy.modeler.StateMachineProvidedApiAbiGenerator"

  final case class SourceIdentity(line: Option[Int])
  final case class WorkflowSourceCorrelation(root: SourceIdentity, definition: SourceIdentity)
  final case class Operation(
    service: String,
    name: String,
    inputType: Option[String],
    resultType: Option[String],
    source: SourceIdentity
  ) {
    def key: String = s"$service.$name"
  }
  final case class Definition(
    schemaVersion: String,
    generator: String,
    identity: String,
    version: String,
    source: WorkflowSourceCorrelation,
    providedOperations: Vector[Operation]
  ) {
    def providesOperation(operation: Operation): Boolean =
      operation != null && providedOperations != null && providedOperations.exists(declared =>
        declared != null && declared.service == operation.service && declared.name == operation.name &&
          declared.inputType == operation.inputType && declared.resultType == operation.resultType)
  }

  /** Admission requires the matching already-accepted generated Workflow declaration. */
  def admitC(
    definitions: Vector[Definition],
    workflows: Vector[GeneratedWorkflowAbi.Definition]
  ): Consequence[Vector[Definition]] =
    if (definitions.isEmpty)
      Consequence.configurationInvalid("Provided API metadata is empty")
    else if (definitions.exists(_ == null) || workflows == null)
      Consequence.configurationInvalid("Provided API metadata is malformed")
    else if (definitions.map(_.identity).distinct.size != definitions.size)
      Consequence.configurationInvalid("duplicate Provided API workflow identity")
    else
      definitions.foldLeft(Consequence.success(Vector.empty[Definition])) { (acc, definition) =>
        acc.flatMap { admitted =>
          _admit_definition_c(definition, workflows).map(admitted :+ _)
        }
      }

  private def _admit_definition_c(
    definition: Definition,
    workflows: Vector[GeneratedWorkflowAbi.Definition]
  ): Consequence[Definition] = {
    val matches = workflows.filter(_.workflow.identity.value == definition.identity)
    if (definition.schemaVersion != schemaVersion)
      Consequence.configurationInvalid("unsupported Provided API producer ABI")
    else if (matches.size != 1 || matches.head.workflow.revision.value != definition.version)
      Consequence.configurationInvalid(s"Provided API workflow mismatch: ${definition.identity}")
    else if (definition.providedOperations == null || definition.providedOperations.isEmpty ||
      definition.providedOperations.exists(_ == null))
      Consequence.configurationInvalid(s"Provided API operations missing: ${definition.identity}")
    else if (definition.providedOperations.map(_.key).distinct.size != definition.providedOperations.size)
      Consequence.configurationInvalid(s"duplicate Provided API operation: ${definition.identity}")
    else if (definition.providedOperations.exists(op =>
      !_name(op.service) || !_name(op.name) ||
      op.inputType.exists(value => !_name(value)) || op.resultType.exists(value => !_name(value))
    ))
      Consequence.configurationInvalid(s"malformed Provided API operation: ${definition.identity}")
    else
      Consequence.success(definition)
  }

  private def _name(value: String): Boolean =
    value != null && value.trim.nonEmpty
}
