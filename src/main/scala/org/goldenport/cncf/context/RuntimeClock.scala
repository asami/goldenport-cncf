package org.goldenport.cncf.context

import java.time.{Clock, Duration, Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
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
  virtualStartAt: Option[Instant],
  mode: RuntimeClockMode,
  private[context] val manual_clock: Option[ManualRuntimeClock] = None
) {
  def isVirtual: Boolean = virtualStartAt.nonEmpty
  def isManual: Boolean = mode == RuntimeClockMode.Manual

  private[context] def runtime_instance: RuntimeClock =
    manual_clock match {
      case Some(_) => RuntimeClock.manual(virtualStartAt.getOrElse(clock.instant()), clock.getZone)
      case None => this
    }
}

enum RuntimeClockMode(val name: String) {
  case System extends RuntimeClockMode("system")
  case Offset extends RuntimeClockMode("offset")
  case Manual extends RuntimeClockMode("manual")
}

object RuntimeClock {
  def system(baseclock: Clock): RuntimeClock =
    RuntimeClock(baseclock, None, RuntimeClockMode.System)

  def offset(
    baseclock: Clock,
    virtualstartat: Instant
  ): RuntimeClock = {
    val duration = Duration.between(baseclock.instant(), virtualstartat)
    RuntimeClock(Clock.offset(baseclock, duration), Some(virtualstartat), RuntimeClockMode.Offset)
  }

  def manual(
    startat: Instant,
    zone: ZoneId
  ): RuntimeClock = {
    val clock = new ManualRuntimeClock(startat, zone)
    RuntimeClock(clock, Some(startat), RuntimeClockMode.Manual, Some(clock))
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

private[context] final class ManualRuntimeClock private (
  private val _current: AtomicReference[Instant],
  zone: ZoneId
) extends Clock {
  private[context] def this(
    initial: Instant,
    zone: ZoneId
  ) = this(new AtomicReference[Instant](initial), zone)

  override def getZone(): ZoneId = zone

  override def withZone(zone: ZoneId): Clock =
    new ManualRuntimeClock(_current, zone)

  override def instant(): Instant =
    _current.get()

  def advanceBy(duration: Duration): Instant = {
    require(!duration.isNegative, "manual clock duration must not be negative")
    _current.updateAndGet(_.plus(duration))
  }
}
