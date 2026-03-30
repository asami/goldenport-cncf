package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.id.UniversalId

/*
 * @since   Jan.  7, 2026
 * @version Mar. 30, 2026
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

  def parse(s: String): Consequence[EventId] =
    UniversalId.parseParts(s, "event").map(parts => EventId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy)))
}
