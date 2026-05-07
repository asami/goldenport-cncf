package org.goldenport.cncf.usernotification

import java.time.Instant
import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Shared user-notification provider contract.
 *
 * User-notification providers belong to deployed components. CNCF owns
 * provider resolution and invocation; providers own notification persistence
 * and user-facing delivery state.
 *
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class UserNotificationRequest(
  recipientUserId: String,
  notificationType: String,
  channel: String,
  title: String,
  body: String,
  priority: Option[String] = None,
  status: String = "Queued",
  dedupeKey: Option[String] = None,
  actionUrl: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  correlationId: Option[String] = None
)

final case class UserNotificationResult(
  accepted: Boolean = true,
  notificationId: Option[String] = None,
  providerNotificationId: Option[String] = Some(UUID.randomUUID.toString),
  acceptedAt: Instant = Instant.now,
  attributes: Map[String, String] = Map.empty
)

trait UserNotificationProvider:
  def name: String

  def notify(
    request: UserNotificationRequest
  )(using ExecutionContext): Consequence[UserNotificationResult]
