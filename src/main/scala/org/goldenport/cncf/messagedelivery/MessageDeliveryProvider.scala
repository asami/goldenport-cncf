package org.goldenport.cncf.messagedelivery

import java.time.Instant
import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Shared message-delivery provider contract.
 *
 * - Message-delivery providers belong to deployed components.
 * - CNCF resolves delivery through subsystem wiring, analogous to auth-provider
 *   resolution.
 * - Providers own concrete delivery implementation; CNCF owns provider
 *   selection and runtime invocation.
 *
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
enum DeliveryChannel:
  case Email, Sms

object DeliveryChannel:
  def parse(p: String): Option[DeliveryChannel] =
    Option(p).map(_.trim.toLowerCase(java.util.Locale.ROOT)).collect {
      case "email" => Email
      case "sms" | "text" => Sms
    }

final case class UnifiedMessage(
  channel: DeliveryChannel,
  recipient: String,
  subject: Option[String] = None,
  body: String = "",
  templateId: Option[String] = None,
  attributes: Map[String, String] = Map.empty,
  correlationId: Option[String] = None
)

final case class MessageDeliveryResult(
  accepted: Boolean = true,
  providerMessageId: Option[String] = Some(UUID.randomUUID.toString),
  acceptedAt: Instant = Instant.now,
  attributes: Map[String, String] = Map.empty
)

trait MessageDeliveryProvider:
  def name: String

  def send(message: UnifiedMessage)(using ExecutionContext): Consequence[MessageDeliveryResult]
