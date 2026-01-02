package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.cncf.action.ActionLogic
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}

/*
 * @since   Jan.  1, 2026
 * @version Jan.  1, 2026
 * @author  ASAMI, Tomoharu
 */
trait Component {
  def service: Service
  def receptor: Receptor
}

trait ComponentActionEntry {
  def name: String
  def opdef: OperationDefinition
  def logic: ActionLogic
}

trait Service {
  def entries: Seq[ComponentActionEntry]

  def call(
    name: String,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse]
}

trait Receptor {
  def entries: Seq[ComponentActionEntry]

  def receive(
    name: String,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse]
}
