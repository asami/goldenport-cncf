package org.goldenport.cncf.action

import java.time.{Clock, Duration, Instant, ZoneOffset, ZonedDateTime}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 16, 2026
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
      snapshot._4.timestamp shouldBe Some(instant)
    }

    "await a bounded delay through the internal DSL" in {
      Given("a component behavior with a caller-owned execution context")
      val context = ExecutionContext.create(Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC))
      val behavior = new _ClockBehavior(Behavior.Core(context, None, None))

      When("the behavior requests a zero-duration delay")
      val result = behavior.delay(Duration.ZERO)

      Then("the delay completes through CNCF without ambient clock access")
      result.isSuccess shouldBe true

      When("the behavior requests a negative delay")
      val invalid = behavior.delay(Duration.ofMillis(-1L))

      Then("the internal DSL rejects it before waiting")
      invalid.isSuccess shouldBe false
    }

    "advance the execution-context-owned manual clock through the internal DSL" in {
      Given("a controlled execution profile without a global static binding")
      val start = Instant.parse("2026-07-28T09:00:00Z")
      val configuration = _controlled_configuration(start)
      val base = ExecutionContext.create()
      val global = GlobalRuntimeContext.create(
        "execution-clock-dsl-spec",
        RuntimeConfig.from(configuration),
        configuration,
        base.observability,
        AliasResolver.empty
      )
      val context = base.asInstanceOf[ExecutionContext.Instance].copy(
        cncfCore = base.cncfCore.copy(
          scope = global,
          executionControl = global.executionProfileRuntime.baseBinding.control
        )
      )
      val behavior = new _ClockBehavior(Behavior.Core(context, None, None))

      When("the behavior requests a bounded manual delay")
      val result = behavior.delay(Duration.ofMillis(250L))

      Then("the execution-context-owned virtual clock advances without host waiting")
      result.isSuccess shouldBe true
      global.executionProfileRuntime.testControl.map(_.now) shouldBe Some(start.plusMillis(250L))
    }
  }

  private def _controlled_configuration(start: Instant): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("test"),
        RuntimeConfig.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("controlled"),
        RuntimeConfig.EXECUTION_KEY -> ConfigurationValue.StringValue("execution-clock-dsl-spec"),
        RuntimeConfig.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue(start.toString),
        RuntimeConfig.EXECUTION_RANDOM_MODE_KEY -> ConfigurationValue.StringValue("seeded"),
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue("execution-clock-dsl-seed"),
        RuntimeConfig.EXECUTION_IDS_MODE_KEY -> ConfigurationValue.StringValue("deterministic"),
        RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_ORDERING_MODE_KEY -> ConfigurationValue.StringValue("deterministic")
      )),
      ConfigurationTrace.empty
    )

  private final class _ClockBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    def snapshot(): (Clock, Instant, ZonedDateTime, EntityId) =
      (
        execution_clock,
        current_instant,
        current_zoned_datetime,
        collection_entity_id(EntityCollectionId("sample", "clock", "snapshot"), "clock-spec")
      )

    def delay(duration: Duration) =
      await_delay(duration)
  }
}
