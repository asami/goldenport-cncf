package org.goldenport.cncf.action

import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionClockDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Behavior internal clock DSL" should {
    "read the clock injected into the execution context" in {
      Given("an execution context with a selected runtime clock")
      val instant = Instant.parse("2026-07-28T09:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      val context = ExecutionContext.create(clock)
      val behavior = new _ClockBehavior(Behavior.Core(context, None, None))

      When("component behavior reads time through the internal DSL")
      val snapshot = behavior.snapshot()

      Then("the DSL exposes the injected clock and its current time")
      snapshot._1 should be theSameInstanceAs clock
      snapshot._2 shouldBe instant
      snapshot._3 shouldBe ZonedDateTime.ofInstant(instant, context.timezone)
    }
  }

  private final class _ClockBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    def snapshot(): (Clock, Instant, ZonedDateTime) =
      (execution_clock, current_instant, current_zoned_datetime)
  }
}
