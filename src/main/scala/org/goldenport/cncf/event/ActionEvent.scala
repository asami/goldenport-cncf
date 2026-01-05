package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.cncf.context.ExecutionContextId

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait DomainEvent extends Event

sealed trait ActionResult

object ActionResult {
  case object Succeeded extends ActionResult
  case object AuthorizationFailed extends ActionResult
}

/**
 * Outcome/fact event for actions (persisted via EventEngine/DataStore).
 */
final case class ActionEvent(
  executionContextId: ExecutionContextId,
  actionName: String,
  result: ActionResult,
  reason: Option[String],
  occurredAt: Instant
) extends DomainEvent

object ActionEvent {
  def authorizationFailed(
    executionContextId: ExecutionContextId,
    actionName: String,
    reason: String,
    occurredAt: Instant
  ): ActionEvent =
    ActionEvent(
      executionContextId,
      actionName,
      ActionResult.AuthorizationFailed,
      Some(reason),
      occurredAt
    )
}
