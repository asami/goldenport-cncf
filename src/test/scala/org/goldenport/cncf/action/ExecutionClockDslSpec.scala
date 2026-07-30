package org.goldenport.cncf.action

import java.time.{Clock, Duration, Instant, ZoneOffset, ZonedDateTime}
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionClockDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E1, rules:R1,R2, phase:36")
  private val _e2_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E2, rules:R1,R2,R10, phase:36")
  private val _e3_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E3, rules:R1,R2, phase:36")
  private val _e4_metadata =
    afterWord("in spec:component-runtime-boundary-capabilities, example:E4, rules:R1,R2,R10, phase:36")

  "Behavior internal clock DSL" should {
    "E3 read the clock injected into the execution context" must _e3_metadata {
      "when component behavior reads a bound clock" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R2; Example: E3; an execution context with a selected runtime clock")
        val instant = Instant.parse("2026-07-28T09:00:00Z")
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        val context = ExecutionContext.create(clock)
        val behavior = new ClockBehavior(Behavior.Core(context, None, None))

        When("component behavior reads time through the internal DSL")
        val snapshot = behavior.snapshot()

        Then("the DSL exposes the injected clock and its current time")
        snapshot._1 should be theSameInstanceAs clock
        snapshot._2 shouldBe instant
        snapshot._3 shouldBe ZonedDateTime.ofInstant(instant, context.timezone)
        snapshot._4.timestamp shouldBe Some(instant)
      }
    }

    "E3 await a bounded delay through the internal DSL" must _e3_metadata {
      "when a caller-owned context requests a bounded delay" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R2; Example: E3; a component behavior with a caller-owned execution context")
        val context = ExecutionContext.create(Clock.fixed(Instant.parse("2026-07-28T09:00:00Z"), ZoneOffset.UTC))
        val behavior = new ClockBehavior(Behavior.Core(context, None, None))

        When("the behavior requests a zero-duration delay")
        val result = behavior.delay(Duration.ZERO)

        Then("the delay completes through CNCF without ambient clock access")
        result.isSuccess shouldBe true

        When("the behavior requests a negative delay")
        val invalid = behavior.delay(Duration.ofMillis(-1L))

        Then("the internal DSL rejects it before waiting")
        invalid.isSuccess shouldBe false
      }
    }

    "E4 advance the execution-context-owned manual clock through the internal DSL" must _e4_metadata {
      "when controlled profile behavior awaits a delay" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R2, R10; Example: E4; a controlled execution profile without a global static binding")
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
        val behavior = new ClockBehavior(Behavior.Core(context, None, None))

        When("the behavior requests a bounded manual delay")
        val result = behavior.delay(Duration.ofMillis(250L))

        Then("the execution-context-owned virtual clock advances without host waiting")
        result.isSuccess shouldBe true
        global.executionProfileRuntime.testControl.map(_.now) shouldBe Some(start.plusMillis(250L))
      }
    }

    "E1 execute a component ActionCall against its injected execution clock" must _e1_metadata {
      "when generated component ActionCalls read time" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R2; Example: E1; generated fixed clocks and component operation ActionCalls")
        val property = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
          val instant = Instant.ofEpochSecond(epochsecond)
          val clock = Clock.fixed(instant, ZoneOffset.UTC)
          val context = ExecutionContext.create(clock)
          val component = new Component() {}
          val pair = ActionCallSupport.componentPair(component, context)
          val call = ActionCallSupport.actionCall("execution-clock", pair) { core =>
            ClockActionCall(core)
          }.asInstanceOf[ClockActionCall]

          val result = call.execute()

          result.isSuccess && call.snapshot.contains((clock, instant, ZonedDateTime.ofInstant(instant, context.timezone)))
        }

        When("generated component ActionCalls read time through the protected internal DSL")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("fixed component execution contexts prevent host-clock fallback for every generated sample")
        checked.passed shouldBe true
      }
    }

    "E2 bind a runtime-controlled clock into a component ActionCall" must _e2_metadata {
      "when a normal runtime creates a component ActionCall" in {
        Given("Spec: docs/spec/component-runtime-boundary-capabilities.md; Rules: R1, R2, R10; Example: E2; a GlobalRuntimeContext with a controlled manual clock")
        val start = Instant.parse("2026-07-28T09:00:00Z")
        val configuration = _controlled_configuration(start)
        val context = _runtime_context(configuration)
        val component = new Component() {}
        val pair = ActionCallSupport.componentPair(component, context)
        val call = ActionCallSupport.actionCall("execution-clock", pair) { core =>
          ClockActionCall(core)
        }.asInstanceOf[ClockActionCall]

        When("the component ActionCall reads execution time from the runtime context")
        val result = call.execute()

        Then("the ActionCall receives the configured instant rather than a host-clock value")
        result.isSuccess shouldBe true
        call.snapshot.map(_._2) shouldBe Some(start)
      }
    }
  }

  private def _controlled_configuration(start: Instant): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("test"),
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

  private def _runtime_context(configuration: ResolvedConfiguration): ExecutionContext = {
    val config = RuntimeConfig.from(configuration)
    val base = ExecutionContext.create()
    val global = GlobalRuntimeContext.create(
      "execution-clock-action-call-spec",
      config,
      configuration,
      base.observability,
      AliasResolver.empty
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "execution-clock-action-call-spec",
        parent = Some(global),
        observabilityContext = base.observability
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          Consequence.serviceUnavailable("not used by execution-clock ActionCall spec")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "execution-clock-action-call-spec",
      operationMode = config.operationMode
    )
    context
  }

  private final class ClockBehavior(
    val behaviorCore: Behavior.Core
  ) extends Behavior {
    def snapshot(): (Clock, Instant, ZonedDateTime, EntityId) =
      (
        execution_clock,
        current_instant,
        current_zoned_datetime,
        entity_id(EntityCollectionId("sample", "clock", "snapshot"), "clock-spec")
      )

    def delay(duration: Duration) =
      await_delay(duration)
  }

  private final case class ClockActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    private var _snapshot: Option[(Clock, Instant, ZonedDateTime)] = None

    def snapshot: Option[(Clock, Instant, ZonedDateTime)] =
      _snapshot

    override def execute(): Consequence[OperationResponse] = {
      _snapshot = Some((execution_clock, current_instant, current_zoned_datetime))
      Consequence.success(OperationResponse.void)
    }
  }
}
