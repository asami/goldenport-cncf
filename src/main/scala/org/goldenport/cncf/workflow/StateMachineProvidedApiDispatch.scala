package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.ExecUowM

/** An inbound call names a declared Workflow and Service Operation explicitly. */
final case class StateMachineProvidedApiRequest(
  workflowIdentity: String,
  workflowRevision: String,
  runId: StateMachineRunIdentity,
  operation: StateMachineOperationIdentity,
  input: Option[StateMachineOperationInput],
  context: ContextBundle
)

/** Component-owned implementation; effects are expressed only as UnitOfWork operations. */
trait StateMachineProvidedApiProgram {
  def workflowIdentity: String
  def operation: StateMachineOperationIdentity
  def program(request: StateMachineProvidedApiRequest): ExecUowM[ActionExecution]
}

trait StateMachineProvidedApiProgramSource {
  def stateMachineProvidedApiPrograms: Vector[StateMachineProvidedApiProgram] = Vector.empty
}

final class StateMachineProvidedApiDispatcher private (
  definitions: Vector[GeneratedProvidedApiAbi.Definition],
  workflowDefinitions: Vector[GeneratedWorkflowAbi.Definition],
  programs: Map[(String, StateMachineOperationIdentity), StateMachineProvidedApiProgram]
) {
  def resolveC(request: StateMachineProvidedApiRequest): Consequence[StateMachineProvidedApiProgram] =
    if (request == null || request.workflowIdentity == null || request.workflowIdentity.trim.isEmpty ||
        request.workflowRevision == null || request.workflowRevision.trim.isEmpty ||
        request.runId == null || request.runId.value == null || request.runId.value.trim.isEmpty ||
        request.operation == null || request.operation.service == null || request.operation.operation == null ||
        request.context == null || request.context.snapshot == null ||
        request.context.snapshot.workflowRevision != request.workflowRevision)
      Consequence.stateConflict("StateMachine Provided API request is incomplete or has incompatible workflow provenance")
    else
      definitions.find(_.identity == request.workflowIdentity) match {
        case Some(definition) if definition.version == request.workflowRevision =>
          definition.providedOperations.find(op =>
            op.service == request.operation.service && op.name == request.operation.operation
          ) match {
            case Some(operation) =>
              val expected = operation.inputType.map(StateMachineInputTypeReference.apply)
              val invalidInput = request.input.exists(input =>
                  input == null || input.typeReference == null || input.contextReference == null ||
                  input.contextReference.identity == null || input.contextReference.identity.trim.isEmpty ||
                  input.contextReference.revision == null || input.contextReference.revision.trim.isEmpty
                )
              val actual = if (invalidInput) None else request.input.map(_.typeReference)
              if (invalidInput || expected != actual)
                Consequence.stateConflict("StateMachine Provided API input type or context is incompatible")
              else
                programs.get((request.workflowIdentity, request.operation)) match {
                  case Some(program) => Consequence.success(program)
                  case None => Consequence.operationNotFound(s"StateMachine Provided API implementation:${request.operation.canonicalValue}")
                }
            case None => Consequence.operationNotFound(s"StateMachine Provided API declaration:${request.operation.canonicalValue}")
          }
        case _ => Consequence.operationNotFound(s"StateMachine Provided API workflow:${request.workflowIdentity}@${request.workflowRevision}")
      }

  def admitResultC(
    request: StateMachineProvidedApiRequest,
    result: ActionExecution
  ): Consequence[ActionExecution] =
    result match {
      case ActionExecution.Completed(value) =>
        val expected = definitions.find(_.identity == request.workflowIdentity).flatMap(
          _.providedOperations.find(op =>
            op.service == request.operation.service && op.name == request.operation.operation
          )
        ).flatMap(_.resultType).map(StateMachineResultTypeReference.apply)
        if (value == null || value.typeReference == null || value.contextReference == null ||
            value.contextReference.identity == null || value.contextReference.identity.trim.isEmpty ||
            value.contextReference.revision == null || value.contextReference.revision.trim.isEmpty ||
            expected != Some(value.typeReference))
          Consequence.stateConflict("StateMachine Provided API result type or context is incompatible")
        else Consequence.success(result)
      case ActionExecution.Suspended(_) =>
        Consequence.stateConflict("StateMachine Provided API suspension requires durable continuation publication")
      case ActionExecution.Failed(null) | null =>
        Consequence.stateConflict("StateMachine Provided API outcome is incomplete")
      case valid => Consequence.success(valid)
    }

  /** Only a committing caller may accept a suspension, after matching its declared Required SPI. */
  def admitCommittingOutcomeC(
    request: StateMachineProvidedApiRequest,
    result: ActionExecution
  ): Consequence[ActionExecution] =
    if (request == null || request.runId == null || request.context == null)
      Consequence.stateConflict("StateMachine Provided API request is incomplete")
    else result match {
      case ActionExecution.Suspended(continuation) =>
        val required = Option(continuation).flatMap(x => Option(x.requiredOperation))
        val provided = definitions.find(_.identity == request.workflowIdentity).toVector
          .flatMap(_.providedOperations).find(op => request.operation != null &&
            op.service == request.operation.service && op.name == request.operation.operation)
        val declared = workflowDefinitions.find(d =>
          d.workflow.identity.value == request.workflowIdentity &&
          d.workflow.revision.value == request.workflowRevision
        ).toVector.flatMap(_.requiredSpis).find(spi => required.exists(op =>
          op.identity != null && spi.identity.value == op.identity.capability &&
          spi.actionIdentity.value == op.actionIdentity &&
          op.operation != null && spi.operation.serviceIdentity.value == op.operation.service &&
          spi.operation.operationIdentity.value == op.operation.operation &&
          spi.operation.inputType.map(_.value) == Option(op.inputType).flatten.map(_.value) &&
          spi.operation.resultType.map(_.value) == Option(op.resultType).flatten.map(_.value)
        ))
        if (continuation == null || continuation.runId != request.runId ||
            continuation.continuationId == null || !_name(continuation.continuationId.value) ||
            continuation.expectedRevision == null || !_name(continuation.expectedRevision.value) ||
            continuation.context != request.context ||
            required.isEmpty || required.get.metadata == null ||
            required.get.metadata.completionContract == null ||
            required.get.metadata.evidenceContract == null || declared.isEmpty ||
            provided.isEmpty)
          Consequence.stateConflict("StateMachine Provided API suspension is not bound to an admitted Required SPI")
        else Consequence.success(result)
      case other => admitResultC(request, other)
    }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}

object StateMachineProvidedApiDispatcher {
  val empty: StateMachineProvidedApiDispatcher =
    new StateMachineProvidedApiDispatcher(Vector.empty, Vector.empty, Map.empty)

  def createC(
    definitions: Vector[GeneratedProvidedApiAbi.Definition],
    workflowDefinitions: Vector[GeneratedWorkflowAbi.Definition],
    programs: Vector[StateMachineProvidedApiProgram]
  ): Consequence[StateMachineProvidedApiDispatcher] = {
    if (definitions == null || workflowDefinitions == null || programs == null || programs.exists(_ == null))
      Consequence.configurationInvalid("StateMachine Provided API programs are malformed")
    else if (programs.map(p => (p.workflowIdentity, p.operation)).distinct.size != programs.size)
      Consequence.configurationInvalid("duplicate StateMachine Provided API implementation")
    else if (programs.exists(p =>
        p.workflowIdentity == null || p.workflowIdentity.trim.isEmpty || p.operation == null ||
        !definitions.exists(d => d.identity == p.workflowIdentity && d.providedOperations.exists(op =>
          op.service == p.operation.service && op.name == p.operation.operation
        ))
      ))
      Consequence.configurationInvalid("StateMachine Provided API implementation is not declared by admitted metadata")
    else
      Consequence.success(new StateMachineProvidedApiDispatcher(
        definitions,
        workflowDefinitions,
        programs.map(p => (p.workflowIdentity, p.operation) -> p).toMap
      ))
  }
}
