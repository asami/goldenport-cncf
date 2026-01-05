package org.goldenport.cncf.event

import org.goldenport.id.UniversalId

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EventId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "event")

object EventId {
  def generate(): EventId =
    EventId("cncf", "event")
}
