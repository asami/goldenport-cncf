package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}

/**
 * Minimum application-neutral Workflow values; JSON is an encoding, not their authority.
 * @since   Sep. 25, 2026
 * @version Oct.  9, 2026
 */
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
  enum ExecutionPlacement(val value: String) {
    case Inline extends ExecutionPlacement("INLINE")
    case Delegated extends ExecutionPlacement("DELEGATED")
  }
  enum ExecutionIndependence(val value: String) {
    case Optional extends ExecutionIndependence("OPTIONAL")
    case Required extends ExecutionIndependence("REQUIRED")
  }
  /** Runtime placement, not a Workflow-wide semantic invocation mode. */
  final case class ExecutionDisposition(
    placement: ExecutionPlacement,
    independence: ExecutionIndependence,
    originContextIdentity: String
  ) {
    def validateC: Consequence[ExecutionDisposition] =
      if (placement == null || independence == null || !_name(originContextIdentity))
        Consequence.stateConflict("Workflow execution disposition is incomplete")
      else Consequence.success(this)
  }
  /** Opaque application-semantic class; never a provider/model/effort selector. */
  final case class ReasoningClassReference(value: String)
  final case class ExecutionRequirement(
    capabilities: Vector[CapabilityRequirement],
    risk: RiskLevel,
    reasoning: ReasoningLevel,
    reviewRequired: Boolean,
    disposition: Option[ExecutionDisposition] = None,
    reasoningClass: Option[ReasoningClassReference] = None
  ) {
    def validateC: Consequence[ExecutionRequirement] =
      if (capabilities == null || capabilities.exists(x => x == null || !_name(x.identity)) ||
          risk == null || !_name(risk.value) || reasoning == null ||
          disposition == null || reasoningClass == null ||
          disposition.exists(x => x == null || x.validateC.toOption.isEmpty) ||
          reasoningClass.exists(x => x == null || !_name(x.value)))
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

  /** Project a Terminal only from the completed, durable instance named by the Handle. */
  def projectTerminalC[O](
    handle: WorkflowHandle,
    record: InstanceRecord,
    result: TypedValue[O],
    presentation: MinimalPresentation
  ): Consequence[WorkflowInteraction[Nothing, O]] =
    if (handle == null || record == null || result == null || presentation == null)
      Consequence.stateConflict("Workflow Terminal projection is incomplete")
    else for {
      admittedHandle <- handle.validateC
      admittedRecord <- record.validateC
      expected <- WorkflowHandle.fromRecordC(admittedHandle.componentIdentity, admittedRecord)
      _ <- if (expected == admittedHandle &&
          admittedRecord.lifecycle == WorkflowInstancePersistence.Lifecycle.Completed &&
          admittedRecord.suspension.isEmpty)
        Consequence.unit
      else Consequence.stateConflict("Workflow Terminal requires the matching completed instance")
      admittedResult <- result.validateC
      admittedPresentation <- presentation.validateC
    } yield WorkflowInteraction(
      admittedHandle, WorkflowContinuation.Terminal(admittedResult, admittedPresentation)
    )

  /** Returned dispatch diagnostics are not a saved-copy attestation or placement selector. */
  final case class SkillDispatchEvidence(
    requestedRequirement: ExecutionRequirement,
    selectedWorkerProfile: String,
    mappingPolicyVersion: String
  ) {
    def validateC: Consequence[SkillDispatchEvidence] =
      if (requestedRequirement == null || !_name(selectedWorkerProfile) ||
          !_name(mappingPolicyVersion))
        Consequence.stateConflict("Skill dispatch evidence is incomplete")
      else requestedRequirement.validateC.map(_ => this)
  }

  final case class ExecutionEvidence(
    references: Vector[ContextReference],
    workerIdentity: Option[String] = None,
    modelIdentity: Option[String] = None,
    skillDispatch: Option[SkillDispatchEvidence] = None,
    execution: Option[ExecutionObservation] = None
  )

  /** Supplied by the owning executor, not fabricated by semantic result content. */
  final case class ExecutionObservation(
    placement: ExecutionPlacement,
    originContextIdentity: String,
    executorContextIdentity: String,
    profileIdentity: String
  ) {
    def validateC: Consequence[ExecutionObservation] =
      if (placement == null || !Vector(originContextIdentity, executorContextIdentity, profileIdentity).forall(_name))
        Consequence.stateConflict("Workflow execution observation is incomplete")
      else Consequence.success(this)

    /** Consume placement relations, not a repeated requirement/profile validation.
      * A blank executor must not pass delegated independence merely by inequality.
      */
    def admitC(requirement: ExecutionRequirement): Consequence[ExecutionObservation] =
      if (requirement == null || requirement.disposition == null)
        Consequence.stateConflict("Workflow execution observation has no selected disposition")
      else requirement.disposition match {
        case Some(selected) if selected != null && _name(executorContextIdentity) &&
            selected.placement == placement &&
            selected.originContextIdentity == originContextIdentity &&
            (placement != ExecutionPlacement.Inline || executorContextIdentity == originContextIdentity) &&
            (placement != ExecutionPlacement.Delegated || executorContextIdentity != originContextIdentity) &&
            (selected.independence != ExecutionIndependence.Required || executorContextIdentity != originContextIdentity) =>
          Consequence.success(this)
        case _ => Consequence.stateConflict("Workflow execution observation does not satisfy its selected placement/independence")
      }
  }

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
    admitIssuedResultC(issued, submitted, WorkflowIssuedContinuationKind.WorkOrder)

  /** Structural Decision result admission only; not human verification or a claim. */
  def admitDecisionResultC[W, R](issued: WorkflowInteraction[W, Nothing],
    submitted: ContinuationResult[R]): Consequence[StateMachineOperationResult] =
    admitIssuedResultC(issued, submitted, WorkflowIssuedContinuationKind.Decision)

  private def admitIssuedResultC[W, R](issued: WorkflowInteraction[W, Nothing],
    submitted: ContinuationResult[R], kind: WorkflowIssuedContinuationKind): Consequence[StateMachineOperationResult] =
    if (issued == null || issued.handle == null || submitted == null ||
        submitted.handle == null || submitted.result == null ||
        submitted.result.value == null || submitted.completionFacts == null ||
        submitted.evidence == null || submitted.evidence.references == null)
      Consequence.stateConflict("Workflow result is incomplete")
    else {
      val selected = issued.current match {
        case work: WorkflowContinuation.WorkOrder[?] if kind == WorkflowIssuedContinuationKind.WorkOrder =>
          Some(work.request -> Some(work.requirement))
        case decision: WorkflowContinuation.Decision[?] if kind == WorkflowIssuedContinuationKind.Decision =>
          Some(decision.request -> None)
        case _ => None
      }
      selected match {
      case Some((request, requirement)) =>
        if (request == null || request.context == null || request.context.snapshot == null ||
            request.completion == null || request.completion.requiredFacts == null ||
            request.evidence == null || request.evidence.requiredEvidence == null)
          Consequence.stateConflict("Issued WorkOrder is incomplete")
        else {
          val requiredType = Option(request.resultType).flatten.flatMap(Option(_)).map(_.value)
          val actualType = Option(submitted.result.typeIdentity)
          val facts = submitted.completionFacts
          val evidence = submitted.evidence.references
          if (issued.handle != submitted.handle ||
            request.runId != submitted.runId ||
            request.continuationId != submitted.continuationId ||
            request.expectedRevision != submitted.expectedRevision ||
            !sameContextScope(request.context.snapshot, submitted.contextSnapshot) ||
            requiredType.isEmpty || requiredType != actualType ||
            !request.completion.requiredFacts.forall(facts.contains) ||
            !request.evidence.requiredEvidence.forall(evidence.contains) ||
            requirement.exists(r => r == null || r.disposition == null ||
              r.disposition.exists(_ => submitted.evidence.execution == null ||
                !submitted.evidence.execution.exists(x => x != null && x.admitC(r).toOption.nonEmpty))))
            Consequence.stateConflict("Workflow result does not satisfy the issued WorkOrder")
          else Consequence.success(StateMachineOperationResult(
            StateMachineResultTypeReference(submitted.result.typeIdentity),
            submitted.resultReference
          ))
        }
      case _ => Consequence.stateConflict("Workflow result requires an issued WorkOrder")
      }
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
      case work: WorkflowContinuation.WorkOrder[?] if work.request != null =>
        for {
          claim <- runtime.recoverClaimC(work.request.continuationId)
          selected <- _select_claimed_work_order_c(issued, claim)
          result <- admitResultC(selected, submitted)
          resumed <- closingProgram match {
            case Some(program) => runtime.resumeWithProgramC(claim, result, freshUnitOfWork, program)
            case None => runtime.resumeC(claim, result, freshUnitOfWork)
          }
        } yield resumed
      case _ => Consequence.stateConflict("Workflow continuation adapter requires an issued WorkOrder")
    }

  /** Resolve actual native conditions without certifying saved projection content. */
  private[workflow] def _select_claimed_work_order_c[W](
    issued: WorkflowInteraction[W, Nothing], claim: ContinuationRuntime.Claim
  ): Consequence[WorkflowInteraction[W, Nothing]] =
    if (issued == null || issued.handle == null || claim == null ||
        claim.continuation == null || !_name(claim.claimId))
      Consequence.stateConflict("Workflow claimed source selection is incomplete")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[W] if work.request != null &&
          work.request.input != null && work.request.completionOperation != null =>
        for {
          handle <- issued.handle.validateC
          resolved <- _continuation_request_c(handle, claim.continuation,
            work.request.input, work.request.completionOperation)
          _ <- if (sameRequestTarget(work.request, resolved)) Consequence.unit
            else Consequence.stateConflict("Issued WorkOrder targets another native Continuation contract")
        } yield issued.copy(current = work.copy(request = work.request.copy(
          completion = resolved.completion, evidence = resolved.evidence)))
      case _ => Consequence.stateConflict("Workflow claimed source requires an issued WorkOrder")
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
    } yield stored

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
  private[workflow] def projectClaimedDecisionC[W](handle: WorkflowHandle,
    claim: ContinuationRuntime.Claim, input: Option[TypedValue[W]],
    completionOperation: StateMachineOperationIdentity, presentation: MinimalPresentation
  ): Consequence[WorkflowInteraction[W, Nothing]] =
    if (handle == null || claim == null || claim.continuation == null || !_name(claim.claimId) ||
        completionOperation == null || !_name(completionOperation.service) || !_name(completionOperation.operation) ||
        input == null || presentation == null)
      Consequence.stateConflict("Workflow Decision projection requires a claimed Continuation and complete bindings")
    else for {
      admittedHandle <- handle.validateC
      admittedPresentation <- presentation.validateC
      request <- _continuation_request_c(admittedHandle, claim.continuation, input, completionOperation)
      _ <- if (request.resultType != null && request.resultType.nonEmpty) Consequence.unit
        else Consequence.stateConflict("Claimed Decision requires an answer result type")
    } yield WorkflowInteraction(admittedHandle, WorkflowContinuation.Decision(request, admittedPresentation))

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

  /** Actual executable/model scope, not workspace/evidence immutability. */
  private[workflow] def sameContextScope(left: ContextSnapshot, right: ContextSnapshot): Boolean =
    left != null && right != null && left.workflowRevision == right.workflowRevision && left.modelRevision == right.modelRevision

  private[workflow] def sameRequestTarget(left: ContinuationRequest[?], right: ContinuationRequest[?]): Boolean =
    left != null && right != null && left.runId == right.runId && left.continuationId == right.continuationId &&
      left.expectedRevision == right.expectedRevision && left.requiredOperation == right.requiredOperation &&
      left.operation == right.operation && left.resultType == right.resultType &&
      left.completionOperation == right.completionOperation &&
      left.completion != null && right.completion != null && left.completion.identity == right.completion.identity &&
      left.evidence != null && right.evidence != null && left.evidence.identity == right.evidence.identity &&
      left.context != null && right.context != null && sameContextScope(left.context.snapshot, right.context.snapshot)

}
