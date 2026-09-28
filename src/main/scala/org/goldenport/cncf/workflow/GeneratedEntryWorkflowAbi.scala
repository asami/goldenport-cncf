package org.goldenport.cncf.workflow

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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
          providedGenerator != providedGeneratorIdentity ||
          !_name(workflowIdentity) || !_name(workflowRevision) ||
          providedWorkflowIdentity != workflowIdentity ||
          providedWorkflowRevision != workflowRevision ||
          !_name(sourceResource) || rootLine <= 0 || definitionLine <= 0 ||
          providedRootLine != rootLine || providedDefinitionLine != definitionLine ||
          postEntryOperation == null || terminalOperation == null ||
          additionalTerminalOperations == null || additionalStageOperations == null ||
          additionalStageOperations.nonEmpty && postEntryOperation.isEmpty ||
          terminalOperation.nonEmpty && postEntryOperation.isEmpty ||
          additionalTerminalOperations.nonEmpty && terminalOperation.isEmpty ||
          states == null || states.size != 1 + _stages.size + _terminals.size ||
          states.exists(state => state == null || !_name(state.identity) || state.sourceLine <= 0) ||
          states.map(_.identity).distinct.size != states.size ||
          states.map(_.sourceLine).distinct.size != states.size ||
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
            operation.sourceLine <= startOperation.sourceLine) ||
          _stages.map(_.operation).distinct.size != _stages.size ||
          _stages.map(_.sourceLine).sliding(2).exists {
            case Vector(left, right) => left >= right
            case _ => false
          } ||
          _terminals.exists(operation => operation == null ||
            !_name(operation.service) || !_name(operation.operation) ||
            operation.operation.startsWith("start") ||
            operation.service != startOperation.service ||
            _stages.exists(_.operation == operation.operation) ||
            !_name(operation.inputType) || !_name(operation.resultType) ||
            operation.sourceLine <= _stages.last.sourceLine) ||
          _terminals.map(_.operation).distinct.size != _terminals.size ||
          _terminals.map(_.sourceLine).sliding(2).exists {
            case Vector(left, right) => left >= right
            case _ => false
          })
        Consequence.stateConflict("generated entry Workflow ABI is incomplete or not actionless")
      else Consequence.success(this)

    def fixtureSha256: String = _sha256((Vector(
      workflowSchema, providedApiSchema, providedGenerator, workflowIdentity, workflowRevision,
      providedWorkflowIdentity, providedWorkflowRevision, sourceResource,
      rootLine.toString, definitionLine.toString,
      providedRootLine.toString, providedDefinitionLine.toString,
      states.head.identity, states.head.sourceLine.toString,
      actionCount.toString, requiredOperationCount.toString, providedOperationCount.toString,
      startOperation.service, startOperation.operation, startOperation.inputType,
      startOperation.resultType, startOperation.sourceLine.toString
    ) ++ _stages.zipWithIndex.flatMap { case (operation, index) =>
      Vector(states(index + 1).identity, states(index + 1).sourceLine.toString,
        operation.service, operation.operation, operation.inputType,
        operation.resultType, operation.sourceLine.toString)
    } ++
      _terminals.zipWithIndex.flatMap { case (operation, index) =>
        Vector(states(index + _stages.size + 1).identity,
          states(index + _stages.size + 1).sourceLine.toString,
          operation.service, operation.operation, operation.inputType,
          operation.resultType, operation.sourceLine.toString)
      }).map(_framed).mkString)
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _framed(value: String): String = s"${value.length}:$value"
  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
