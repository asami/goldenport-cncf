package org.goldenport.cncf.context

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class SystemStatusExecutionDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "The default system.status operation" should {
    "derive its timestamp and uptime from the selected execution clock" in {
      Given("a runtime whose observable clock starts at one fixed instant")
      val property = Prop.forAll(Gen.chooseNum(0L, 31L * 24L * 60L * 60L)) { elapsedseconds =>
        val startedat = Instant.parse("2026-07-16T00:00:00Z")
        val clock = new _MutableClock(startedat)
        val context = _execution_context(clock)
        val component = TestComponentFactory.create("system_status_determinism", Protocol.empty)

        val elapsed = Duration.ofSeconds(elapsedseconds)
        val observedat = clock.advanceBy(elapsed)
        val response = _execute(
          component,
          Request.of(
            component = "system_status_determinism",
            service = "system",
            operation = "status"
          ),
          context
        )

        response match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("timestamp").contains(observedat.toString) &&
              record.getString("uptime").contains(elapsed.toString)
          case _ =>
            false
        }
      }

      When("logical runtime time advances before system.status executes")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("the response reports profile time and profile-relative uptime")
      checked.passed shouldBe true
    }

    "report zero uptime when an isolated fixture has no owning runtime" in {
      Given("generated fixture clocks without a GlobalRuntimeContext ancestor")
      val property = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val observedat = Instant.ofEpochSecond(epochsecond)
        val context = ExecutionContext.create(Clock.fixed(observedat, ZoneOffset.UTC))
        val component = TestComponentFactory.create("isolated_system_status", Protocol.empty)

        val response = _execute(
          component,
          Request.of(
            component = "isolated_system_status",
            service = "system",
            operation = "status"
          ),
          context
        )

        response match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("timestamp").contains(observedat.toString) &&
              record.getString("uptime").contains(Duration.ZERO.toString)
          case _ =>
            false
        }
      }

      When("system.status executes through the fixture ActionCall")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("it reports fixture time without inventing an ambient boot instant")
      checked.passed shouldBe true
    }
  }

  private def _execution_context(clock: _MutableClock): ExecutionContext = {
    val base = ExecutionContext.create()
    val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    val profile = RuntimeConfig.default.executionProfile.copy(
      runtimeClock = RuntimeClock.system(clock)
    )
    val global = GlobalRuntimeContext.create(
      "system-status-determinism-spec",
      RuntimeConfig.default.copy(executionProfile = profile),
      configuration,
      base.observability,
      AliasResolver.empty
    )
    val runtime = new RuntimeContext(
      core = RuntimeContext.core(
        name = "system-status-determinism-spec",
        parent = Some(global),
        observabilityContext = base.observability
      ),
      unitOfWorkSupplier = () => base.unitOfWork,
      unitOfWorkInterpreterFn = base.runtime.unitOfWorkInterpreter,
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "system-status-determinism-spec"
    )
    ExecutionContext.create(runtime)
  }

  private def _execute(
    component: Component,
    request: Request,
    executioncontext: ExecutionContext
  ): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        component.logic.execute(component.logic.createActionCall(action, executioncontext))
      case other =>
        Consequence.operationInvalid(s"unexpected operation request type: ${other.getClass.getName}")
    }
}

private final class _MutableClock(
  private var _current: Instant,
  private val _zone: ZoneId = ZoneOffset.UTC
) extends Clock {
  override def getZone(): ZoneId = _zone

  override def withZone(zone: ZoneId): Clock =
    new _MutableClock(_current, zone)

  override def instant(): Instant = _current

  def advanceBy(duration: Duration): Instant = {
    _current = _current.plus(duration)
    _current
  }
}
