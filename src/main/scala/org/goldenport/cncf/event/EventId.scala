package org.goldenport.cncf.event

import org.goldenport.Consequence
import org.goldenport.id.UniversalId

/*
 * @since   Jan.  7, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EventId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "event")

object EventId {
  def generate(): EventId =
    EventId("cncf", "event")

  def parse(s: String): Consequence[EventId] =
    UniversalId.parseParts(s, "event").map(parts => EventId(parts.major, parts.minor))
}
