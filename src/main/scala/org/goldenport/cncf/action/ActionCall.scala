package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.util.StringUtils.objectToSnakeName
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.security.{Action as SecurityAction, SecuredResource}

/*
 * @since   Apr. 11, 2025
 *  version Apr. 14, 2025
 *  version Dec. 31, 2025
 * @version Jan.  1, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ActionCall {
  def name: String = objectToSnakeName("ActionCall", this)

  def action: Action
  def executionContext: ExecutionContext
  def correlationId: Option[CorrelationId]
  def request: OperationRequest
  def accesses: Seq[ResourceAccess]

  def apply(): Consequence[OperationResponse] = apply(request)
  def apply(req: OperationRequest): Consequence[OperationResponse]
}

trait ActionCallBuilder {
  def build(
    opdef: OperationDefinition,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[ActionCall]
}

object ActionCallBuilder {
  val default: ActionCallBuilder = new DefaultActionCallBuilder()

  def build(
    opdef: OperationDefinition,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[ActionCall] =
    default.build(opdef, request, executionContext, correlationId)
}

final class DefaultActionCallBuilder extends ActionCallBuilder {
  def build(
    opdef: OperationDefinition,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[ActionCall] = {
    opdef.createOperationRequest(request).flatMap { opreq =>
      _validate_correlation(executionContext, correlationId).map { observed =>
        DefaultActionCall(
          action = ProtocolAction(request.operation),
          executionContext = executionContext,
          correlationId = observed,
          request = opreq
        )
      }
    }
  }

  private def _validate_correlation(
    executioncontext: ExecutionContext,
    correlationid: Option[CorrelationId]
  ): Consequence[Option[CorrelationId]] = {
    val observed = executioncontext.observability.correlationId
    if (correlationid.isDefined && correlationid != observed) {
      Consequence.failure("correlationId must match ObservabilityContext.correlationId")
    } else {
      Consequence.success(observed)
    }
  }
}

final case class DefaultActionCall(
  action: Action,
  executionContext: ExecutionContext,
  correlationId: Option[CorrelationId],
  request: OperationRequest
) extends ActionCall {
  def accesses: Seq[ResourceAccess] = Nil

  def apply(req: OperationRequest): Consequence[OperationResponse] =
    Consequence.failure("ActionCall execution is delegated to ActionLogic")
}

// TEMPORARY (to be removed after demo)
final case class ResourceAccess(
  resource: SecuredResource,
  action: SecurityAction
)

private final case class ProtocolAction(
  name: String
) extends Action
