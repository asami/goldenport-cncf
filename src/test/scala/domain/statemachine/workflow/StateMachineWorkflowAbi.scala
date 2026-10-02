package domain.statemachine.workflow

object StateMachineWorkflowAbi {
  val VERSION: String = "cozy.cml.statemachine-workflow-abi.v2"

  final case class SourceIdentity(line: Option[Int])
  final case class WorkflowSourceCorrelation(root: SourceIdentity, definition: SourceIdentity)
  final case class State(name: String, source: SourceIdentity)
  final case class Operation(service: String, name: String, inputType: Option[String], resultType: Option[String], source: SourceIdentity)
  final case class Action(identity: String, kind: String, operation: Operation, inputBinding: Option[String], source: SourceIdentity)
  final case class StateMachineOperationIdentity(service: String, operation: String) {
    def canonicalValue: String = s"$service.$operation"
  }
  final case class StateMachineInputTypeReference(value: String)
  final case class StateMachineResultTypeReference(value: String)
  final case class StateMachineProvidedOperation(identity: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference])
  final case class StateMachineRequiredOperationIdentity(capability: String)
  final case class StateMachineConstraint(identity: String, value: String)
  final case class StateMachineRequiredOperationMetadata(contextContract: ContextContract, completionContract: CompletionContract, evidenceContract: EvidenceContract, constraints: Vector[StateMachineConstraint])
  final case class StateMachineRequiredOperation(identity: StateMachineRequiredOperationIdentity, actionIdentity: String, operation: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference], metadata: StateMachineRequiredOperationMetadata)
  final case class StateMachineApiSpi(providedOperations: Vector[StateMachineProvidedOperation], requiredOperations: Vector[StateMachineRequiredOperation])
  final case class ProviderIdentity(value: String)
  final case class StateMachineProviderBinding(requiredOperation: StateMachineRequiredOperationIdentity, provider: ProviderIdentity)
  final case class ContextReference(identity: String, revision: String)
  final case class ContextSnapshot(workflowRevision: String, modelRevision: Option[String] = None, workspaceRevision: Option[String] = None, evidenceRevision: Option[String] = None)
  final case class ContextBundle(summary: String, requiredFacts: Vector[ContextReference], references: Vector[ContextReference], snapshot: ContextSnapshot)
  final case class ContextContract(identity: String, requiredFacts: Vector[ContextReference], requiredReferences: Vector[ContextReference])
  final case class CompletionContract(identity: String, requiredFacts: Vector[ContextReference])
  final case class EvidenceContract(identity: String, requiredEvidence: Vector[ContextReference])
  final case class StateMachineOperationInput(typeReference: StateMachineInputTypeReference, contextReference: ContextReference)
  final case class StateMachineOperationResult(typeReference: StateMachineResultTypeReference, contextReference: ContextReference)
  final case class StateMachineOperationFailure(code: String, message: String, evidence: Vector[ContextReference])
  final case class ProviderExecutionRequest(runId: StateMachineRunIdentity, requiredOperation: StateMachineRequiredOperation, input: Option[StateMachineOperationInput], context: ContextBundle)
  trait StateMachineProvider {
    def identity: ProviderIdentity
    def execute(request: ProviderExecutionRequest): ActionExecution
  }
  final case class StateMachineRunIdentity(value: String)
  final case class ContinuationIdentity(value: String)
  final case class StateMachineRevision(value: String)
  final case class Continuation(runId: StateMachineRunIdentity, continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision, requiredOperation: StateMachineRequiredOperation, context: ContextBundle)
  final case class ContinuationResult(runId: StateMachineRunIdentity, continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision, operation: StateMachineOperationIdentity, requiredOperation: StateMachineRequiredOperationIdentity, contextSnapshot: ContextSnapshot, requiredOperationMetadata: StateMachineRequiredOperationMetadata, completion: StateMachineOperationResult, completionFacts: Vector[ContextReference], evidence: Vector[ContextReference])
  sealed trait ActionExecution
  object ActionExecution {
    final case class Completed(result: StateMachineOperationResult) extends ActionExecution
    final case class Suspended(continuation: Continuation) extends ActionExecution
    final case class Failed(failure: StateMachineOperationFailure) extends ActionExecution
  }
  final case class WorkflowRequiredOperationDescriptor(identity: StateMachineRequiredOperationIdentity, actionIdentity: String, operation: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference], capabilitySource: SourceIdentity, actionSource: SourceIdentity)
  sealed trait InvocationTarget { def identity: String }
  object InvocationTarget {
    final case class Action(identity: String) extends InvocationTarget
    final case class Participant(identity: String) extends InvocationTarget
  }
  final case class RetryPolicy(maximumAttempts: Int, fixedRetryDelayMillis: Long)
  final case class InvocationPolicy(identity: String, target: InvocationTarget,
    actionIdentity: String, retry: Option[RetryPolicy],
    executionTimeoutMillis: Option[Long], source: SourceIdentity)
  final case class WorkflowDescriptor(schemaVersion: String, identity: String, version: String, source: WorkflowSourceCorrelation, states: Vector[State], actions: Vector[Action], requiredOperations: Vector[WorkflowRequiredOperationDescriptor], invocationPolicies: Vector[InvocationPolicy] = Vector.empty)
  final case class ComponentFactoryMetadata(workflow: WorkflowDescriptor)
}
