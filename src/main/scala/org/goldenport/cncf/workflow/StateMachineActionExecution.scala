package org.goldenport.cncf.workflow

/*
 * Provider-neutral StateMachine action outcome values.
 *
 * These values preserve the Cozy 62.3 source ABI at the CNCF consumer
 * boundary.  They deliberately model outcomes and their typed context only:
 * provider selection, dispatch, persistence, and continuation resumption are
 * outside this initial execution slice.
 *
 * @since   Sep. 22, 2026
 * @version Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class StateMachineOperationIdentity(service: String, operation: String) {
  def canonicalValue: String = s"$service.$operation"
}

final case class StateMachineInputTypeReference(value: String)

final case class StateMachineResultTypeReference(value: String)

final case class StateMachineRequiredOperationIdentity(capability: String)

final case class ContextReference(identity: String, revision: String)

final case class ContextSnapshot(
  workflowRevision: String,
  modelRevision: Option[String] = None,
  workspaceRevision: Option[String] = None,
  evidenceRevision: Option[String] = None
)

final case class ContextBundle(
  summary: String,
  requiredFacts: Vector[ContextReference],
  references: Vector[ContextReference],
  snapshot: ContextSnapshot
)

final case class ContextContract(
  identity: String,
  requiredFacts: Vector[ContextReference],
  requiredReferences: Vector[ContextReference]
)

final case class CompletionContract(
  identity: String,
  requiredFacts: Vector[ContextReference]
)

final case class EvidenceContract(
  identity: String,
  requiredEvidence: Vector[ContextReference]
)

final case class StateMachineConstraint(identity: String, value: String)

final case class StateMachineRequiredOperationMetadata(
  contextContract: ContextContract,
  completionContract: CompletionContract,
  evidenceContract: EvidenceContract,
  constraints: Vector[StateMachineConstraint]
)

final case class StateMachineRequiredOperation(
  identity: StateMachineRequiredOperationIdentity,
  actionIdentity: String,
  operation: StateMachineOperationIdentity,
  inputType: Option[StateMachineInputTypeReference],
  resultType: Option[StateMachineResultTypeReference],
  metadata: StateMachineRequiredOperationMetadata
)

final case class StateMachineOperationResult(
  typeReference: StateMachineResultTypeReference,
  contextReference: ContextReference
)

final case class StateMachineOperationFailure(
  code: String,
  message: String,
  evidence: Vector[ContextReference]
)

final case class StateMachineRunIdentity(value: String)

final case class ContinuationIdentity(value: String)

final case class StateMachineRevision(value: String)

final case class Continuation(
  runId: StateMachineRunIdentity,
  continuationId: ContinuationIdentity,
  expectedRevision: StateMachineRevision,
  requiredOperation: StateMachineRequiredOperation,
  context: ContextBundle
)

sealed trait ActionExecution

object ActionExecution {
  final case class Completed(result: StateMachineOperationResult) extends ActionExecution
  final case class Suspended(continuation: Continuation) extends ActionExecution
  final case class Failed(failure: StateMachineOperationFailure) extends ActionExecution
}
