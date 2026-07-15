package org.goldenport.cncf.context

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeClockSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RuntimeClock" should {
    "start at the configured instant and continue with the base clock" in {
      Given("an advancing base clock and a later virtual start")
      val basestart = Instant.parse("2026-07-15T00:00:00Z")
      val virtualstart = Instant.parse("2026-07-28T09:00:00Z")
      val baseclock = new _AdvancingClock(basestart, ZoneOffset.UTC)

      When("an offset runtime clock is created")
      val runtimeclock = RuntimeClock.offset(baseclock, virtualstart)

      Then("the clock starts at the requested instant")
      runtimeclock.isVirtual shouldBe true
      runtimeclock.virtualStartAt shouldBe Some(virtualstart)
      runtimeclock.clock.instant() shouldBe virtualstart

      When("the base clock advances")
      baseclock.advance(Duration.ofMinutes(15))

      Then("the virtual clock advances by the same duration")
      runtimeclock.clock.instant() shouldBe virtualstart.plus(Duration.ofMinutes(15))
    }

    "parse date-times with an explicit offset into one instant" in {
      RuntimeClock.parseInstant("2026-07-28T18:00:00+09:00") shouldBe
        Instant.parse("2026-07-28T09:00:00Z")
    }

    "keep zone views attached to the same manual timeline" in {
      Given("a manual runtime clock and another zone view")
      val start = Instant.parse("2026-07-28T09:00:00Z")
      val runtimeclock = RuntimeClock.manual(start, ZoneOffset.UTC)
      val tokyo = runtimeclock.clock.withZone(ZoneId.of("Asia/Tokyo"))

      When("the runtime-owned manual timeline advances")
      runtimeclock.manual_clock.get.advanceBy(Duration.ofHours(2L))

      Then("all zone views report the same advanced instant")
      runtimeclock.clock.instant() shouldBe start.plus(Duration.ofHours(2L))
      tokyo.instant() shouldBe runtimeclock.clock.instant()
      tokyo.getZone() shouldBe ZoneId.of("Asia/Tokyo")
    }

    "reject local date-times without an offset" in {
      an[IllegalArgumentException] should be thrownBy {
        RuntimeClock.parseInstant("2026-07-28T18:00:00")
      }
    }
  }

  private final class _AdvancingClock(
    initial: Instant,
    zone: ZoneId
  ) extends Clock {
    private var _current = initial
    private val _zone = zone

    override def getZone(): ZoneId = _zone

    override def withZone(zone: ZoneId): Clock =
      new _AdvancingClock(_current, zone)

    override def instant(): Instant = _current

    def advance(duration: Duration): Unit =
      _current = _current.plus(duration)
  }
}
