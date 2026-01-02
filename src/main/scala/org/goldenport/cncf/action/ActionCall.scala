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
 *  version Jan.  1, 2026
 * @version Jan.  2, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ActionCall(val core: ActionCall.Core) extends ActionCall.Core.Holder {
  def name: String = objectToSnakeName("ActionCall", this)

  def action: Action
  def accesses: Seq[ResourceAccess]

  def execute(): Consequence[OperationResponse]
}

object ActionCall {
  final case class Core(
    action: Action,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  )
  object Core {
    trait Holder {
      def core: Core

      def action: Action = core.action
      def executionContext: ExecutionContext = core.executionContext
      def correlationId: Option[CorrelationId] = core.correlationId
    }
  }
}
