package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.id.UniversalId

/*
 * @since   Jan.  7, 2026
 *  version Mar. 30, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EventId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "event", timestamp, entropy)

object EventId {
  def generate(): EventId =
    EventId("cncf", "event")

  def create(
    purpose: String,
    timestamp: Instant
  )(using ctx: ExecutionContext): EventId =
    create(purpose, timestamp, ctx.idGeneration)

  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): EventId =
    EventId(
      major = idgeneration.namespace.major,
      minor = idgeneration.namespace.minor,
      timestamp = Some(timestamp),
      entropy = Some(idgeneration.opaqueId(s"event.$purpose"))
    )

  def parse(s: String): Consequence[EventId] =
    UniversalId.parseParts(s, "event").map(parts => EventId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy)))
}
