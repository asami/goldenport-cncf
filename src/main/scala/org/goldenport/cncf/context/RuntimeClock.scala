package org.goldenport.cncf.context

import java.time.{Clock, Duration, Instant}
import java.time.format.DateTimeFormatter
import scala.util.Try

/**
 * Runtime-owned clock selection carried into every CNCF ExecutionContext.
 *
 * A virtual start uses an offset clock: elapsed time continues to follow the
 * supplied base clock instead of freezing at the configured instant.
 */
/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeClock(
  clock: Clock,
  virtualStartAt: Option[Instant]
) {
  def isVirtual: Boolean = virtualStartAt.nonEmpty
}

object RuntimeClock {
  def system(baseclock: Clock): RuntimeClock =
    RuntimeClock(baseclock, None)

  def offset(
    baseclock: Clock,
    virtualstartat: Instant
  ): RuntimeClock = {
    val duration = Duration.between(baseclock.instant(), virtualstartat)
    RuntimeClock(Clock.offset(baseclock, duration), Some(virtualstartat))
  }

  def parseOffset(
    baseclock: Clock,
    value: String
  ): RuntimeClock =
    offset(baseclock, parseInstant(value))

  def parseInstant(value: String): Instant = {
    val normalized = value.trim
    Try(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(normalized))).getOrElse {
      throw new IllegalArgumentException(
        s"Virtual clock start must be an ISO-8601 date-time with an offset: ${value}"
      )
    }
  }
}
