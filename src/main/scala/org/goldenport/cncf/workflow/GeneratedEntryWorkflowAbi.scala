package org.goldenport.cncf.workflow

import org.goldenport.Consequence

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 */
/** A narrow binding for a generated, actionless Workflow entry and its
  * Provided Start operation, optionally followed by ordered stages and
  * terminal branches. It does not admit a runnable transition graph or
  * substitute for Candidate-Admission metadata.
  */
object GeneratedEntryWorkflowAbi {
  val workflowSchemaVersion = "cozy.cml.statemachine-workflow-abi.v1"
  val providedApiSchemaVersion = "cozy.cml.statemachine-provided-api-abi.v1"
  val providedGeneratorIdentity = "cozy.modeler.StateMachineProvidedApiAbiGenerator"
  val producerIdentity = "cozy.generated-entry-workflow.v1"

  final case class State(identity: String, sourceLine: Int)
  final case class StartOperation(
    service: String,
    operation: String,
    inputType: String,
    resultType: String,
    sourceLine: Int
  )
  final case class StageOperation(
    service: String,
    operation: String,
    inputType: String,
    resultType: String,
    sourceLine: Int
  )
  final case class Definition(
    workflowSchema: String,
    providedApiSchema: String,
    providedGenerator: String,
    workflowIdentity: String,
    workflowRevision: String,
    providedWorkflowIdentity: String,
    providedWorkflowRevision: String,
    sourceResource: String,
    rootLine: Int,
    definitionLine: Int,
    providedRootLine: Int,
    providedDefinitionLine: Int,
    states: Vector[State],
    actionCount: Int,
    requiredOperationCount: Int,
    providedOperationCount: Int,
    startOperation: StartOperation,
    postEntryOperation: Option[StageOperation] = None,
    terminalOperation: Option[StageOperation] = None,
    additionalTerminalOperations: Vector[StageOperation] = Vector.empty,
    additionalStageOperations: Vector[StageOperation] = Vector.empty
  ) {
    private def _stages: Vector[StageOperation] =
      postEntryOperation.toVector ++ additionalStageOperations
    private def _terminals: Vector[StageOperation] =
      terminalOperation.toVector ++ additionalTerminalOperations

    def validateC: Consequence[Definition] =
      if (workflowSchema != workflowSchemaVersion ||
          providedApiSchema != providedApiSchemaVersion ||
          !_name(workflowIdentity) || !_name(workflowRevision) ||
          providedWorkflowIdentity != workflowIdentity ||
          providedWorkflowRevision != workflowRevision ||
          !_name(sourceResource) || rootLine <= 0 || definitionLine <= 0 ||
          postEntryOperation == null || terminalOperation == null ||
          additionalTerminalOperations == null || additionalStageOperations == null ||
          additionalStageOperations.nonEmpty && postEntryOperation.isEmpty ||
          terminalOperation.nonEmpty && postEntryOperation.isEmpty ||
          additionalTerminalOperations.nonEmpty && terminalOperation.isEmpty ||
          states == null || states.size != 1 + _stages.size + _terminals.size ||
          states.exists(state => state == null || !_name(state.identity) || state.sourceLine <= 0) ||
          states.map(_.identity).distinct.size != states.size ||
          actionCount != 0 || requiredOperationCount != 0 ||
          providedOperationCount != 1 + _stages.size + _terminals.size ||
          startOperation == null || !_name(startOperation.service) ||
          !_name(startOperation.operation) || !startOperation.operation.startsWith("start") ||
          !_name(startOperation.inputType) || !_name(startOperation.resultType) ||
          startOperation.sourceLine <= 0 ||
          _stages.exists(operation => operation == null ||
            !_name(operation.service) || !_name(operation.operation) ||
            operation.operation.startsWith("start") ||
            operation.service != startOperation.service ||
            !_name(operation.inputType) || !_name(operation.resultType) ||
            operation.sourceLine <= 0) ||
          _stages.map(_.operation).distinct.size != _stages.size ||
          _terminals.exists(operation => operation == null ||
            !_name(operation.service) || !_name(operation.operation) ||
            operation.operation.startsWith("start") ||
            operation.service != startOperation.service ||
            _stages.exists(_.operation == operation.operation) ||
            !_name(operation.inputType) || !_name(operation.resultType) ||
            operation.sourceLine <= 0) ||
          _terminals.map(_.operation).distinct.size != _terminals.size)
        Consequence.stateConflict("generated entry Workflow ABI is incomplete or not actionless")
      else Consequence.success(this)

  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
