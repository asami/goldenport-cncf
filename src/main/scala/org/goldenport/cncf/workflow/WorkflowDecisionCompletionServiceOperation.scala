package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowContinuation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowCompletionServiceOperation.{Codec, ClosingProgram, SelectedClosing}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.value.{BaseContent, DescriptiveAttributes}

/** Declared native Decision completion through host-bound managed capabilities.
  * The application codec owns human verification; structural admission here
  * never supplies consent, command permission or a WorkOrder fallback.
  * @since   Oct.  1, 2026
  * @version Oct. 12, 2026
  */
object WorkflowDecisionCompletionServiceOperation {
  def bindC[W, R](definition: GeneratedProvidedApiAbi.Definition,
    operation: GeneratedProvidedApiAbi.Operation,
    requestDefinition: spec.RequestDefinition, responseDefinition: spec.ResponseDefinition,
    codec: Codec[R], questions: WorkflowManagedIssuedDecision[W],
    instances: WorkflowInstancePersistenceBinding, closing: ClosingProgram[R],
    completion: WorkflowManagedDecisionCompletion[R],
    descriptiveAttributes: DescriptiveAttributes = DescriptiveAttributes.empty
  ): Consequence[spec.OperationDefinition] = {
    if (definition == null || operation == null || requestDefinition == null ||
        responseDefinition == null || codec == null || questions == null ||
        instances == null || closing == null || completion == null || descriptiveAttributes == null ||
        definition.schemaVersion != GeneratedProvidedApiAbi.schemaVersion ||
        !definition.providesOperation(operation) ||
        !_name(definition.identity) || !_name(definition.version) ||
        !_name(operation.service) || !_name(operation.name) ||
        !_name(codec.resultTypeIdentity) || operation.inputType == null ||
        !operation.inputType.exists(_name) ||
        completion.resultTypeIdentity != codec.resultTypeIdentity)
      Consequence.configurationInvalid("Decision completion requires a declared typed Operation and managed bindings")
    else Consequence.success(new BoundOperation(definition, operation, requestDefinition,
      responseDefinition, codec, questions, instances, closing, completion, descriptiveAttributes))
  }

  private final class BoundOperation[W, R](definition: GeneratedProvidedApiAbi.Definition,
    operation: GeneratedProvidedApiAbi.Operation,
    requestdefinition: spec.RequestDefinition, responsedefinition: spec.ResponseDefinition,
    codec: Codec[R], questions: WorkflowManagedIssuedDecision[W],
    instances: WorkflowInstancePersistenceBinding, closing: ClosingProgram[R],
    completion: WorkflowManagedDecisionCompletion[R],
    descriptiveattributes: DescriptiveAttributes
  ) extends spec.OperationDefinition {
    val specification = spec.OperationDefinition.Specification(
      content = BaseContent.simple(operation.name, descriptiveattributes),
      request = requestdefinition, response = responsedefinition)

    def createOperationRequest(request: Request): Consequence[OperationRequest] = {
      if (request == null || request.operation != operation.name || !request.service.contains(operation.service))
        Consequence.stateConflict("Decision request differs from its declared completion Operation")
      else codec.decodeC(request).flatMap { submitted =>
        if (submitted == null || submitted.handle == null || submitted.result == null ||
            submitted.continuationId == null || submitted.handle.workflowIdentity == null ||
            submitted.handle.workflowRevision == null ||
            submitted.handle.componentIdentity != instances.componentId ||
            submitted.handle.workflowIdentity.value != definition.identity ||
            submitted.handle.workflowRevision.value != definition.version ||
            submitted.result.typeIdentity != codec.resultTypeIdentity)
          Consequence.stateConflict("Decision result differs from its declared Component, Workflow or type")
        else submitted.handle.validateC.map(_ =>
          BoundAction(request, submitted, operation, codec, questions, instances, closing, completion))
      }
    }
  }

  private final case class BoundAction[W, R](request: Request, submitted: ContinuationResult[R],
    operation: GeneratedProvidedApiAbi.Operation, codec: Codec[R],
    questions: WorkflowManagedIssuedDecision[W], instances: WorkflowInstancePersistenceBinding,
    closing: ClosingProgram[R], completion: WorkflowManagedDecisionCompletion[R]
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, submitted, operation, codec, questions, instances, closing, completion)
  }

  private final case class BoundCall[W, R](core: ActionCall.Core, submitted: ContinuationResult[R],
    operation: GeneratedProvidedApiAbi.Operation, codec: Codec[R],
    questions: WorkflowManagedIssuedDecision[W], instances: WorkflowInstancePersistenceBinding,
    closing: ClosingProgram[R], completion: WorkflowManagedDecisionCompletion[R]
  ) extends ProcedureActionCall {
    private var _selected: Option[(InstanceRecord, SelectedClosing)] = None

    def execute(): Consequence[OperationResponse] = {
      _selected = None
      for {
        _ <- component match {
          case Some(value) if value.componentId == instances.componentId => Consequence.unit
          case _ => Consequence.stateConflict("Decision completion Component is unavailable or differs")
        }
        loaded <- instances.loadC(submitted.handle.instanceIdentity)(using executionContext)
        current <- loaded match {
          case Some(value) if value != null => Consequence.success(value)
          case _ => Consequence.stateConflict("Decision completion WorkflowInstance is unavailable")
        }
        proposed <- closing.selectC(submitted, current)
        admitted <- _admit_selected_c(current, proposed)
      } yield {
        _selected = Some(current -> admitted)
        OperationResponse.void
      }
    }

    override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
      val prepared = _selected
      _selected = None
      prepared match {
        case None => Consequence.stateConflict("Decision completion has no selected closing Action")
        case Some((current, program)) => for {
          owner <- component match {
            case Some(value) if value.componentId == instances.componentId => Consequence.success(value.componentId)
            case _ => Consequence.stateConflict("Decision completion Component is unavailable or differs")
          }
          completed <- completion.completeC(owner, submitted, current, program, executionContext)
          encoded <- codec.encodeAfterCommitC(completed.resume, submitted, Some(completed.instance))
        } yield encoded
      }
    }

    private def _admit_selected_c(current: InstanceRecord, proposed: SelectedClosing): Consequence[SelectedClosing] = {
      if (proposed == null || proposed.program == null || proposed.nextInstance == null)
        Consequence.stateConflict("Decision closing selection is incomplete")
      else proposed.nextInstance match {
        case Some(next) if next != null && next.definition != null &&
            next.history != null && next.history.nonEmpty => for {
          projected <- current._append_c_unchecked(current.revision, next.history.last)
          _ <- if (next.identity == current.identity && next.revision == projected.revision &&
              next.definition.workflowIdentity == current.definition.workflowIdentity &&
              next.definition.workflowRevision == current.definition.workflowRevision) Consequence.unit
            else Consequence.stateConflict("Decision next instance is not the selected adjacent append")
        } yield proposed.copy(nextInstance = Some(projected))
        case _ => Consequence.stateConflict("Decision completion requires an adjacent selected next instance")
      }
    }
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
