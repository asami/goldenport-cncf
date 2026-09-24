package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec

/** Binds one admitted Provided Operation to the ordinary public Service/ActionEngine path.
  * The application supplies its transport codec; CNCF owns operation identity and commit staging.
  */
object StateMachineProvidedApiServiceOperation {
  trait Codec {
    def decodeC(request: Request): Consequence[StateMachineProvidedApiRequest]
    def encodeC(outcome: ActionExecution): Consequence[OperationResponse]
    /** Called only after successful UnitOfWork commit and post-commit persistence.
      * A projection failure reports an error but cannot roll back that commit.
      */
    def encodeAfterCommitC(
      request: StateMachineProvidedApiRequest,
      outcome: ActionExecution,
      encoded: OperationResponse
    ): Consequence[OperationResponse] = Consequence.success(encoded)
  }

  /** Application-supplied WorkflowInstance write, ordered before Continuation publication. */
  trait SuspensionPersistence {
    def persistC(request: StateMachineProvidedApiRequest, continuation: Continuation): Consequence[Unit]
  }

  def bindC(
    definition: GeneratedProvidedApiAbi.Definition,
    operation: GeneratedProvidedApiAbi.Operation,
    requestDefinition: spec.RequestDefinition,
    responseDefinition: spec.ResponseDefinition,
    codec: Codec,
    suspensionPersistence: Option[SuspensionPersistence] = None
  ): Consequence[spec.OperationDefinition] =
    if (definition == null || operation == null || requestDefinition == null ||
        responseDefinition == null || codec == null ||
        definition.schemaVersion != GeneratedProvidedApiAbi.schemaVersion ||
        definition.generator != GeneratedProvidedApiAbi.generatorIdentity ||
        definition.providedOperations == null || !definition.providedOperations.contains(operation) ||
        !_name(definition.identity) || !_name(definition.version) ||
        !_name(operation.service) || !_name(operation.name) ||
        suspensionPersistence == null || suspensionPersistence.exists(_ == null))
      Consequence.configurationInvalid("public Service binding requires a declared Provided Operation")
    else Consequence.success(new BoundOperation(
      definition, operation, requestDefinition, responseDefinition, codec, suspensionPersistence
    ))

  private final class BoundOperation(
    definition: GeneratedProvidedApiAbi.Definition,
    operation: GeneratedProvidedApiAbi.Operation,
    requestDefinition: spec.RequestDefinition,
    responseDefinition: spec.ResponseDefinition,
    codec: Codec,
    suspensionPersistence: Option[SuspensionPersistence]
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = operation.name,
      request = requestDefinition,
      response = responseDefinition
    )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != operation.name ||
          !request.service.contains(operation.service))
        Consequence.stateConflict("public Service request differs from the bound Provided Operation")
      else codec.decodeC(request).flatMap { decoded =>
        if (decoded == null || decoded.workflowIdentity != definition.identity ||
            decoded.workflowRevision != definition.version ||
            decoded.operation != StateMachineOperationIdentity(operation.service, operation.name))
          Consequence.stateConflict("public Service payload differs from the declared Provided Operation")
        else Consequence.success(BoundAction(request, decoded, codec, suspensionPersistence))
      }
  }

  private final case class BoundAction(
    request: Request,
    providedRequest: StateMachineProvidedApiRequest,
    codec: Codec,
    suspensionPersistence: Option[SuspensionPersistence]
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, providedRequest, codec, suspensionPersistence)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    providedRequest: StateMachineProvidedApiRequest,
    codec: Codec,
    suspensionPersistence: Option[SuspensionPersistence]
  ) extends ProcedureActionCall {
    private var _encoded_outcome: Option[ActionExecution] = None

    def execute(): Consequence[OperationResponse] =
      {
        val interpreter = new UnitOfWorkInterpreter(executionContext.runtime.unitOfWork)
        val staged = suspensionPersistence match {
          case Some(persistence) => interpreter.stageProvidedForExternalCommitC(
            providedRequest, continuation => persistence.persistC(providedRequest, continuation)
          )
          case None => interpreter.stageProvidedForExternalCommitC(providedRequest)
        }
        staged.flatMap { outcome =>
          codec.encodeC(outcome).map { encoded =>
            _encoded_outcome = Some(outcome)
            encoded
          }
        }
      }

    override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
      val outcome = _encoded_outcome
      _encoded_outcome = None
      outcome match {
        case Some(value) => codec.encodeAfterCommitC(providedRequest, value, response)
        case None => Consequence.stateConflict("public Service response has no staged outcome after commit")
      }
    }
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
