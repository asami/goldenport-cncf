package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowContinuation}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.value.{BaseContent, DescriptiveAttributes}

/** Binds an explicitly declared completion Operation to a persisted WorkOrder.
  * Result payload decoding and response projection remain application-owned.
  * @since   Sep. 25, 2026
  * @version Oct.  8, 2026
  */
object WorkflowCompletionServiceOperation {
  final case class SelectedClosing(
    program: ExecUowM[ActionExecution],
    nextInstance: Option[WorkflowInstancePersistence.InstanceRecord] = None
  )

  trait Codec[R] {
    def resultTypeIdentity: String
    def decodeC(request: Request): Consequence[ContinuationResult[R]]
    def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse]
    def encodeAfterCommitC(
      resume: ContinuationRuntime.Resume,
      submitted: ContinuationResult[R],
      completed: Option[WorkflowInstancePersistence.InstanceRecord]
    ): Consequence[OperationResponse] = encodeC(resume)
  }

  /** Selects a typed closing Action from admitted StateMachine/Workflow facts.
    * Selection must be pure; the runtime interprets the returned program only
    * after the issued WorkOrder and saved suspension pass admission.
    */
  trait ClosingProgram[R] {
    def selectC(
      submitted: ContinuationResult[R],
      current: WorkflowInstancePersistence.InstanceRecord
    ): Consequence[SelectedClosing]
  }

  def bindC[W, R](
    definition: GeneratedProvidedApiAbi.Definition,
    operation: GeneratedProvidedApiAbi.Operation,
    requestDefinition: spec.RequestDefinition,
    responseDefinition: spec.ResponseDefinition,
    codec: Codec[R],
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => UnitOfWork,
    closingProgram: Option[ClosingProgram[R]] = None,
    managedCompletion: Option[WorkflowManagedCompletion[R]] = None,
    descriptiveAttributes: DescriptiveAttributes = DescriptiveAttributes.empty
  ): Consequence[spec.OperationDefinition] =
    if (definition == null || operation == null || requestDefinition == null ||
        responseDefinition == null || codec == null || runtime == null ||
        (runtime == ContinuationRuntime.empty && (managedCompletion == null || managedCompletion.isEmpty)) || issuedPersistence == null ||
        instancePersistence == null || configuration == null || freshUnitOfWork == null ||
        descriptiveAttributes == null ||
        closingProgram == null || closingProgram.exists(_ == null) ||
        managedCompletion == null || managedCompletion.exists(value => value == null ||
          value.resultTypeIdentity != codec.resultTypeIdentity) ||
        (managedCompletion.nonEmpty && closingProgram.isEmpty) ||
        definition.schemaVersion != GeneratedProvidedApiAbi.schemaVersion ||
        !definition.providesOperation(operation) ||
        !_name(definition.identity) || !_name(definition.version) ||
        !_name(operation.service) || !_name(operation.name) ||
        !_name(codec.resultTypeIdentity) || operation.inputType != Some(codec.resultTypeIdentity))
      Consequence.configurationInvalid("public completion binding requires a declared typed Provided Operation")
    else configuration.validateC.map { accepted =>
      new BoundOperation(
        definition, operation, requestDefinition, responseDefinition, codec,
        runtime, issuedPersistence, instancePersistence, accepted, freshUnitOfWork, closingProgram, managedCompletion,
        descriptiveAttributes
      )
    }

  private final class BoundOperation[W, R](
    definition: GeneratedProvidedApiAbi.Definition,
    operation: GeneratedProvidedApiAbi.Operation,
    requestDefinition: spec.RequestDefinition,
    responseDefinition: spec.ResponseDefinition,
    codec: Codec[R],
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => UnitOfWork,
    closingProgram: Option[ClosingProgram[R]],
    managedCompletion: Option[WorkflowManagedCompletion[R]],
    descriptiveAttributes: DescriptiveAttributes
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      content = BaseContent.simple(operation.name, descriptiveAttributes),
      request = requestDefinition,
      response = responseDefinition
    )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != operation.name ||
          !request.service.contains(operation.service))
        Consequence.stateConflict("public completion request differs from the declared Operation")
      else codec.decodeC(request).flatMap { submitted =>
        if (submitted == null || submitted.handle == null || submitted.result == null ||
            submitted.continuationId == null ||
            submitted.handle.workflowIdentity == null ||
            submitted.handle.workflowRevision == null ||
            submitted.handle.workflowIdentity.value != definition.identity ||
            submitted.handle.workflowRevision.value != definition.version ||
            submitted.result.typeIdentity != codec.resultTypeIdentity)
          Consequence.stateConflict("public completion payload differs from the declared Workflow or result type")
        else submitted.handle.validateC.map { _ =>
          BoundAction(request, submitted, operation, codec, runtime,
            issuedPersistence, instancePersistence, configuration, freshUnitOfWork, closingProgram, managedCompletion)
        }
      }
  }

  private final case class BoundAction[W, R](
    request: Request,
    submitted: ContinuationResult[R],
    operation: GeneratedProvidedApiAbi.Operation,
    codec: Codec[R],
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => UnitOfWork,
    closingProgram: Option[ClosingProgram[R]],
    managedCompletion: Option[WorkflowManagedCompletion[R]]
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, submitted, operation, codec, runtime,
        issuedPersistence, instancePersistence, configuration, freshUnitOfWork, closingProgram, managedCompletion)
  }

  private final case class BoundCall[W, R](
    core: ActionCall.Core,
    submitted: ContinuationResult[R],
    operation: GeneratedProvidedApiAbi.Operation,
    codec: Codec[R],
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    instancePersistence: WorkflowInstancePersistence,
    configuration: WorkflowInstancePersistence.Configuration,
    freshUnitOfWork: () => UnitOfWork,
    closingProgram: Option[ClosingProgram[R]],
    managedCompletion: Option[WorkflowManagedCompletion[R]]
  ) extends ProcedureActionCall {
    private var _selected_closing: Option[(WorkflowInstancePersistence.InstanceRecord, SelectedClosing)] = None
    private def _admit_issued_c(): Consequence[Unit] =
      issuedPersistence.loadC(submitted.continuationId).flatMap {
        case Some(issued) if issued != null && issued.handle == submitted.handle =>
          issued.current match {
            case work: WorkflowContinuation.WorkOrder[?] if work.request != null &&
                work.request.completionOperation == StateMachineOperationIdentity(operation.service, operation.name) =>
              Consequence.unit
            case _ => Consequence.stateConflict("public completion Operation differs from the issued WorkOrder")
          }
        case _ => Consequence.stateConflict("public completion has no matching issued WorkOrder")
      }

    def execute(): Consequence[OperationResponse] =
      _admit_issued_c().flatMap { _ =>
        closingProgram match {
          case None => Consequence.unit
          case Some(selector) =>
            for {
              loaded <- instancePersistence.load(configuration, submitted.handle.instanceIdentity)
              current <- loaded match {
                case Some(value) if value != null => value.validateC
                case _ => Consequence.stateConflict("public completion WorkflowInstance is unavailable")
              }
              selected <- selector.selectC(submitted, current)
              _ <- _admit_selected_c(current, selected)
            } yield {
              _selected_closing = Some(current -> selected)
              ()
            }
        }
      }.map(_ => OperationResponse.void)

    override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
      val selected = _selected_closing
      _selected_closing = None
      for {
        _ <- if (closingProgram.isEmpty || selected.nonEmpty) Consequence.unit
             else Consequence.stateConflict("public completion has no selected closing Action")
        owner <- component match {
          case Some(value) => Consequence.success(value.componentId)
          case None => Consequence.stateConflict("public completion Component is unavailable")
        }
        completion <- managedCompletion match {
          case Some(bound) => selected match {
            case Some((current, closing)) =>
              bound.completeC(owner, submitted, current, closing, executionContext)
                .map(value => value.resume -> Some(value.instance))
            case None => Consequence.stateConflict("Managed completion requires selected closing execution")
          }
          case None => for {
            resumed <- WorkflowProtocolV1.resumePersistedWorkOrderC(
              owner, submitted, issuedPersistence, runtime, instancePersistence, configuration,
              freshUnitOfWork, selected.map(_._2.program))
            completed <- selected match {
              case Some((current, SelectedClosing(_, Some(next)))) =>
                instancePersistence.append(configuration, current.identity, current.revision, next.history.last)
                  .flatMap { persisted =>
                    if (persisted.identity == next.identity && persisted.revision == next.revision &&
                        persisted.lifecycle == next.lifecycle && persisted.currentProgression == next.currentProgression &&
                        persisted.suspension == next.suspension) Consequence.success(Some(persisted))
                    else Consequence.stateConflict("public completion WorkflowInstance append diverged")
                  }
              case _ => Consequence.success(None)
            }
          } yield resumed -> completed
        }
        encoded <- codec.encodeAfterCommitC(completion._1, submitted, completion._2)
      } yield encoded
    }

    private def _admit_selected_c(
      current: WorkflowInstancePersistence.InstanceRecord,
      selected: SelectedClosing
    ): Consequence[Unit] =
      if (selected == null || selected.program == null || selected.nextInstance == null)
        Consequence.stateConflict("public completion closing Action selection is incomplete")
      else selected.nextInstance match {
        case None if managedCompletion.nonEmpty =>
          Consequence.stateConflict("Managed completion requires an adjacent selected next instance")
        case None => Consequence.unit
        case Some(next) if next != null && next.history != null && next.history.nonEmpty =>
          next.validateC.flatMap { admitted =>
            current.appendC(current.revision, admitted.history.last).flatMap { projected =>
              if (admitted.identity == current.identity && admitted.revision == projected.revision &&
                  admitted.definition.workflowIdentity == current.definition.workflowIdentity &&
                  admitted.definition.workflowRevision == current.definition.workflowRevision) Consequence.unit
              else Consequence.stateConflict("public completion next WorkflowInstance is not the selected append")
            }
          }
        case _ => Consequence.stateConflict("public completion next WorkflowInstance is missing")
      }
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
