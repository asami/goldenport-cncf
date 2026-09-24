package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}

/** Minimum application-neutral Workflow values; JSON is an encoding, not their authority. */
object WorkflowProtocolV1 {
  val schemaVersion = "cncf.workflow-protocol.v1"

  final case class WorkflowHandle(
    componentIdentity: ComponentId,
    workflowIdentity: WorkflowDefinitionIdentity,
    workflowRevision: WorkflowDefinitionRevision,
    instanceIdentity: InstanceIdentity,
    protocolVersion: String = schemaVersion
  ) {
    def validateC: Consequence[WorkflowHandle] =
      if (componentIdentity == null || !_name(componentIdentity.name) ||
          workflowIdentity == null || !_name(workflowIdentity.value) ||
          workflowRevision == null || !_name(workflowRevision.value) ||
          instanceIdentity == null || !_name(instanceIdentity.value) ||
          protocolVersion != schemaVersion)
        Consequence.stateConflict("WorkflowHandle is incomplete or has an unsupported protocol version")
      else Consequence.success(this)
  }

  object WorkflowHandle {
    /** A stable public identity derived from a validated durable instance record. */
    def fromRecordC(component: ComponentId, record: InstanceRecord): Consequence[WorkflowHandle] =
      if (record == null)
        Consequence.stateConflict("WorkflowHandle requires a durable WorkflowInstance")
      else record.validateC.flatMap { admitted =>
        WorkflowHandle(
          component,
          admitted.definition.workflowIdentity,
          admitted.definition.workflowRevision,
          admitted.identity
        ).validateC
      }
  }

  final case class TypedValue[A](typeIdentity: String, value: A) {
    def validateC: Consequence[TypedValue[A]] =
      if (!_name(typeIdentity) || value == null)
        Consequence.stateConflict("Workflow typed value is incomplete")
      else Consequence.success(this)
  }

  /** Built by an admitted profile-specific Start Operation, not a generic public start selector. */
  final case class WorkflowStartRequest[I](
    startOperation: StateMachineOperationIdentity,
    workflowIdentity: WorkflowDefinitionIdentity,
    workflowRevision: WorkflowDefinitionRevision,
    input: TypedValue[I],
    invocationReference: String,
    idempotencyKey: String
  ) {
    def validateC: Consequence[WorkflowStartRequest[I]] =
      if (startOperation == null || !_name(startOperation.service) || !_name(startOperation.operation) ||
          workflowIdentity == null || !_name(workflowIdentity.value) ||
          workflowRevision == null || !_name(workflowRevision.value) ||
          input == null || !_name(invocationReference) || !_name(idempotencyKey))
        Consequence.stateConflict("WorkflowStartRequest is incomplete")
      else input.validateC.map(_ => this)
  }

  enum ReasoningLevel(val value: String) {
    case Routine extends ReasoningLevel("ROUTINE")
    case Standard extends ReasoningLevel("STANDARD")
    case Deep extends ReasoningLevel("DEEP")
    case Critical extends ReasoningLevel("CRITICAL")
  }

  final case class CapabilityRequirement(identity: String)
  final case class RiskLevel(value: String)
  final case class ExecutionRequirement(
    capabilities: Vector[CapabilityRequirement],
    risk: RiskLevel,
    reasoning: ReasoningLevel,
    reviewRequired: Boolean
  ) {
    def validateC: Consequence[ExecutionRequirement] =
      if (capabilities == null || capabilities.exists(x => x == null || !_name(x.identity)) ||
          risk == null || !_name(risk.value) || reasoning == null)
        Consequence.stateConflict("Workflow execution requirement is incomplete")
      else Consequence.success(this)
  }

  /** Human-facing projection; none of these fields is a progression selector. */
  final case class MinimalPresentation(
    title: String,
    currentSituation: String,
    summary: Option[String] = None,
    nextAction: Option[String] = None,
    reason: Option[String] = None,
    progress: Option[String] = None
  ) {
    def validateC: Consequence[MinimalPresentation] =
      if (!_name(title) || !_name(currentSituation) ||
          Vector(summary, nextAction, reason, progress).exists(_.exists(x => !_name(x))))
        Consequence.stateConflict("Workflow presentation is incomplete")
      else Consequence.success(this)
  }

  /** Public projection of a single runtime Continuation, with no claim token. */
  final case class ContinuationRequest[W](
    runId: StateMachineRunIdentity,
    continuationId: ContinuationIdentity,
    expectedRevision: StateMachineRevision,
    requiredOperation: StateMachineRequiredOperationIdentity,
    operation: StateMachineOperationIdentity,
    input: Option[TypedValue[W]],
    resultType: Option[StateMachineResultTypeReference],
    context: ContextBundle,
    completion: CompletionContract,
    evidence: EvidenceContract,
    completionOperation: StateMachineOperationIdentity
  )

  sealed trait WorkflowContinuation[+W, +O] {
    def kind: String
  }

  object WorkflowContinuation {
    final case class WorkOrder[W](
      request: ContinuationRequest[W],
      requirement: ExecutionRequirement,
      presentation: MinimalPresentation
    ) extends WorkflowContinuation[W, Nothing] {
      val kind = "WORK_ORDER"
    }

    final case class Decision[W](
      request: ContinuationRequest[W],
      presentation: MinimalPresentation
    ) extends WorkflowContinuation[W, Nothing] {
      val kind = "DECISION"
    }

    final case class Wait(
      expectedRevision: StateMachineRevision,
      contextSnapshot: ContextSnapshot,
      presentation: MinimalPresentation
    ) extends WorkflowContinuation[Nothing, Nothing] {
      val kind = "WAIT"
    }

    final case class Terminal[O](
      result: TypedValue[O],
      presentation: MinimalPresentation
    ) extends WorkflowContinuation[Nothing, O] {
      val kind = "TERMINAL"
    }
  }

  /** The same framework Handle and current Continuation used by Start and later calls. */
  final case class WorkflowInteraction[W, O](
    handle: WorkflowHandle,
    current: WorkflowContinuation[W, O]
  )

  type WorkflowStartResult[W, O] = WorkflowInteraction[W, O]

  /** A separate-turn result is checked against the exact issued WorkOrder before resume. */
  final case class ExecutionEvidence(
    references: Vector[ContextReference],
    workerIdentity: Option[String] = None,
    modelIdentity: Option[String] = None
  )

  final case class ContinuationResult[R](
    handle: WorkflowHandle,
    runId: StateMachineRunIdentity,
    continuationId: ContinuationIdentity,
    expectedRevision: StateMachineRevision,
    contextSnapshot: ContextSnapshot,
    result: TypedValue[R],
    resultReference: ContextReference,
    completionFacts: Vector[ContextReference],
    evidence: ExecutionEvidence
  )

  /** This validation does not replace the runtime's one-shot claim/resume check. */
  def admitResultC[W, R](
    issued: WorkflowInteraction[W, Nothing],
    submitted: ContinuationResult[R]
  ): Consequence[StateMachineOperationResult] =
    if (issued == null || issued.handle == null || submitted == null ||
        submitted.handle == null || submitted.result == null ||
        submitted.resultReference == null || submitted.completionFacts == null ||
        submitted.evidence == null || submitted.evidence.references == null)
      Consequence.stateConflict("Workflow result is incomplete")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[?] =>
        val request = work.request
        if (request == null || request.context == null || request.context.snapshot == null ||
            request.completion == null || request.completion.requiredFacts == null ||
            request.evidence == null || request.evidence.requiredEvidence == null)
          Consequence.stateConflict("Issued WorkOrder is incomplete")
        else {
          val requiredType = Option(request.resultType).flatten.map(_.value)
          val actualType = Option(submitted.result.typeIdentity)
          val facts = submitted.completionFacts
          val evidence = submitted.evidence.references
          if (issued.handle != submitted.handle ||
            request.runId != submitted.runId ||
            request.continuationId != submitted.continuationId ||
            request.expectedRevision != submitted.expectedRevision ||
            request.context.snapshot != submitted.contextSnapshot ||
            requiredType.isEmpty || requiredType != actualType ||
            !_reference(submitted.resultReference) ||
            facts.exists(x => !_reference(x)) || facts.distinct.size != facts.size ||
            evidence.exists(x => !_reference(x)) || evidence.distinct.size != evidence.size ||
            !request.completion.requiredFacts.forall(facts.contains) ||
            !request.evidence.requiredEvidence.forall(evidence.contains) ||
            submitted.evidence.workerIdentity.exists(x => !_name(x)) ||
            submitted.evidence.modelIdentity.exists(x => !_name(x)))
            Consequence.stateConflict("Workflow result does not satisfy the issued WorkOrder")
          else submitted.handle.validateC.flatMap(_ =>
            submitted.result.validateC.map(_ =>
              StateMachineOperationResult(
                StateMachineResultTypeReference(submitted.result.typeIdentity),
                submitted.resultReference
              )
            )
          )
        }
      case _ => Consequence.stateConflict("Workflow result requires an issued WorkOrder")
    }

  /** Trusted continuation adapter; the public result never carries claim ownership. */
  private[workflow] def resumeIssuedWorkOrderC[W, R](
    issued: WorkflowInteraction[W, Nothing],
    submitted: ContinuationResult[R],
    runtime: ContinuationRuntime,
    freshUnitOfWork: () => org.goldenport.cncf.unitofwork.UnitOfWork,
    closingProgram: Option[ExecUowM[ActionExecution]] = None
  ): Consequence[ContinuationRuntime.Resume] =
    if (issued == null || runtime == null || freshUnitOfWork == null || closingProgram == null)
      Consequence.stateConflict("Workflow continuation adapter is incomplete")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[?] =>
        for {
          result <- admitResultC(issued, submitted)
          claim <- runtime.recoverClaimC(work.request.continuationId)
          projected <- projectClaimedWorkOrderC(
            issued.handle, claim, work.request.input, work.request.completionOperation,
            work.requirement, work.presentation
          )
          _ <- if (projected == issued) Consequence.unit
               else Consequence.stateConflict("Issued WorkOrder differs from the persisted Continuation")
          resumed <- closingProgram match {
            case Some(program) => runtime.resumeWithProgramC(claim, result, freshUnitOfWork, program)
            case None => runtime.resumeC(claim, result, freshUnitOfWork)
          }
        } yield resumed
      case _ => Consequence.stateConflict("Workflow continuation adapter requires an issued WorkOrder")
    }

  /** Issue only an already-claimed Continuation; a failed store write returns no WorkOrder. */
  def issueRecoveredWorkOrderC[W](
    handle: WorkflowHandle,
    identity: ContinuationIdentity,
    input: Option[TypedValue[W]],
    completionOperation: StateMachineOperationIdentity,
    requirement: ExecutionRequirement,
    presentation: MinimalPresentation,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W]
  ): Consequence[WorkflowInteraction[W, Nothing]] =
    if (runtime == null || issuedPersistence == null)
      Consequence.stateConflict("Workflow WorkOrder issuer is incomplete")
    else for {
      claim <- runtime.recoverClaimC(identity)
      projected <- projectClaimedWorkOrderC(handle, claim, input, completionOperation, requirement, presentation)
      stored <- issuedPersistence.putIfAbsentC(projected)
      result <- if (stored == projected) Consequence.success(stored)
                else Consequence.stateConflict("WorkOrder persistence returned a different issue")
    } yield result

  /** Load the exact issued WorkOrder before applying the WorkflowInstance resume guard. */
  def resumePersistedWorkOrderC[W, R](
    componentIdentity: ComponentId,
    submitted: ContinuationResult[R],
    issuedPersistence: IssuedWorkOrderPersistence[W],
    runtime: ContinuationRuntime,
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => org.goldenport.cncf.unitofwork.UnitOfWork,
    closingProgram: Option[ExecUowM[ActionExecution]] = None
  ): Consequence[ContinuationRuntime.Resume] =
    if (submitted == null || submitted.continuationId == null || issuedPersistence == null)
      Consequence.stateConflict("Workflow result has no issued WorkOrder lookup")
    else for {
      loaded <- issuedPersistence.loadC(submitted.continuationId)
      issued <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("issued WorkOrder is unavailable for resume")
      }
      _ <- issued.current match {
        case work: WorkflowContinuation.WorkOrder[?] if work.request != null &&
            work.request.continuationId == submitted.continuationId => Consequence.unit
        case _ => Consequence.stateConflict("issued WorkOrder lookup returned another Continuation")
      }
      resumed <- resumeIssuedWithInstanceGuardC(
        componentIdentity, issued, submitted, runtime, instancePersistence, configuration, freshUnitOfWork,
        closingProgram
      )
    } yield resumed

  /** Read-only WorkflowInstance guard before the loose, non-atomic Continuation resume. */
  def resumeIssuedWithInstanceGuardC[W, R](
    componentIdentity: ComponentId,
    issued: WorkflowInteraction[W, Nothing],
    submitted: ContinuationResult[R],
    runtime: ContinuationRuntime,
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => org.goldenport.cncf.unitofwork.UnitOfWork,
    closingProgram: Option[ExecUowM[ActionExecution]] = None
  ): Consequence[ContinuationRuntime.Resume] =
    if (componentIdentity == null || issued == null || issued.handle == null ||
        instancePersistence == null || configuration == null || runtime == null ||
        freshUnitOfWork == null || closingProgram == null)
      Consequence.stateConflict("Workflow instance resume guard is incomplete")
    else for {
      admittedConfiguration <- configuration.validateC
      loaded <- instancePersistence.load(admittedConfiguration, issued.handle.instanceIdentity)
      record <- loaded match {
        case Some(value) if value != null => value.validateC
        case _ => Consequence.stateConflict("Workflow instance is unavailable for resume")
      }
      expectedHandle <- WorkflowHandle.fromRecordC(componentIdentity, record)
      _ <- if (expectedHandle == issued.handle) Consequence.unit
           else Consequence.stateConflict("Issued WorkOrder Handle differs from the stored WorkflowInstance")
      _ <- issued.current match {
        case work: WorkflowContinuation.WorkOrder[?] =>
          val request = work.request
          val boundary = Option(record.suspension).flatten.flatMap(Option(_))
          if (record.lifecycle == WorkflowInstancePersistence.Lifecycle.Active &&
              request != null && request.continuationId != null &&
              request.completion != null && request.evidence != null &&
              boundary.exists(value =>
                value.continuationIdentity.value == request.continuationId.value &&
                value.completion.value == request.completion.identity &&
                value.evidence.value == request.evidence.identity
              )) Consequence.unit
          else Consequence.stateConflict("Issued WorkOrder differs from the stored suspension boundary")
        case _ => Consequence.stateConflict("Workflow instance resume requires an issued WorkOrder")
      }
      resumed <- resumeIssuedWorkOrderC(issued, submitted, runtime, freshUnitOfWork, closingProgram)
    } yield resumed

  /** Projection is possible only after the runtime has returned a durable claim. */
  def projectClaimedWorkOrderC[W](
    handle: WorkflowHandle,
    claim: ContinuationRuntime.Claim,
    input: Option[TypedValue[W]],
    completionOperation: StateMachineOperationIdentity,
    requirement: ExecutionRequirement,
    presentation: MinimalPresentation
  ): Consequence[WorkflowInteraction[W, Nothing]] =
    if (handle == null || claim == null || claim.continuation == null || !_name(claim.claimId) ||
        completionOperation == null || !_name(completionOperation.service) || !_name(completionOperation.operation) ||
        input == null || requirement == null || presentation == null)
      Consequence.stateConflict("Workflow WorkOrder projection requires a claimed Continuation and complete bindings")
    else for {
      admittedHandle <- handle.validateC
      admittedRequirement <- requirement.validateC
      admittedPresentation <- presentation.validateC
      request <- _continuation_request_c(admittedHandle, claim.continuation, input, completionOperation)
    } yield WorkflowInteraction(
      admittedHandle,
      WorkflowContinuation.WorkOrder(request, admittedRequirement, admittedPresentation)
    )

  private def _continuation_request_c[W](
    handle: WorkflowHandle,
    continuation: Continuation,
    input: Option[TypedValue[W]],
    completionOperation: StateMachineOperationIdentity
  ): Consequence[ContinuationRequest[W]] = {
    val required = continuation.requiredOperation
    val context = continuation.context
    val inputType = Option(required).flatMap(_.inputType).map(_.value)
    val actualType = input.flatMap(Option(_)).map(_.typeIdentity)
    if (continuation.runId == null || !_name(continuation.runId.value) ||
        continuation.continuationId == null || !_name(continuation.continuationId.value) ||
        continuation.expectedRevision == null || !_name(continuation.expectedRevision.value) ||
        required == null || required.identity == null || !_name(required.identity.capability) ||
        required.operation == null || !_name(required.operation.service) || !_name(required.operation.operation) ||
        required.metadata == null || required.metadata.completionContract == null ||
        !_name(required.metadata.completionContract.identity) ||
        required.metadata.evidenceContract == null || !_name(required.metadata.evidenceContract.identity) ||
        context == null || context.snapshot == null ||
        context.snapshot.workflowRevision != handle.workflowRevision.value ||
        inputType != actualType)
      Consequence.stateConflict("Workflow Continuation is incompatible with Handle or typed WorkOrder input")
    else input match {
      case Some(value) => value.validateC.map(_ => _request(continuation, input, completionOperation))
      case None => Consequence.success(_request(continuation, input, completionOperation))
    }
  }

  private def _request[W](
    continuation: Continuation,
    input: Option[TypedValue[W]],
    completionOperation: StateMachineOperationIdentity
  ): ContinuationRequest[W] =
    ContinuationRequest(
      continuation.runId,
      continuation.continuationId,
      continuation.expectedRevision,
      continuation.requiredOperation.identity,
      continuation.requiredOperation.operation,
      input,
      continuation.requiredOperation.resultType,
      continuation.context,
      continuation.requiredOperation.metadata.completionContract,
      continuation.requiredOperation.metadata.evidenceContract,
      completionOperation
    )

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _reference(value: ContextReference): Boolean =
    value != null && _name(value.identity) && _name(value.revision)
}
