package org.goldenport.cncf.action

import java.time.{Clock, Instant, ZoneOffset}
import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, IdGenerationContext, ObservabilityContext, RuntimeContext, TraceId}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{ActionResult, EventEngine, EventLane, EventRecord, EventStore}
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers
import org.scalacheck.{Gen, Prop, Test}

/*
 * @since   Jan.  6, 2026
 *  version Feb. 27, 2026
 *  version Mar. 12, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngineAuthorizationFailureCommitSpec extends AnyWordSpec with Matchers with ConsequenceMatchers with GivenWhenThen {
  "ActionEngine authorization failure" should {
    "commit ActionEvent without invoking ActionCall" in {
      Given("an action engine that denies authorization before building the action call")
      val recorder = new InMemoryCommitRecorder
      val datastore = DataStore.noop(recorder)
      val eventengine = EventEngine.noop(datastore, recorder)
      val runtime = new TestRuntimeContext
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, eventengine, recorder)
      runtime.bind(uow)

      var buildcalled = false
      val engine = new DenyingActionEngine

      val action = new QueryAction() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core)
      }

      When("the protected action is executed")
      val result = engine.executeAuthorized("test-action", ctx) {
        buildcalled = true
        action.createCall(ActionCall.Core(action, ctx, None, None))
      }

      Then("the action call is not built and one authorization failure event is committed")
      buildcalled shouldBe false
      result should be_failure
      val events = eventengine.eventStore.query(
        org.goldenport.cncf.event.EventStore.Query(
          name = Some("test-action"),
          lane = Some(org.goldenport.cncf.event.EventLane.Transactional)
        )
      ).toOption.get
      events should have size 1
      events.head.kind shouldBe ActionResult.AuthorizationFailed.toString.toLowerCase
      events.head.payload.get("reason").collect { case value: String => value }.exists(_.nonEmpty) shouldBe true
      recorder.entries shouldBe Vector(
        "UnitOfWork.prepare",
        "EventEngine.prepare",
        "UnitOfWork.commit",
        "EventEngine.commit",
        "DataStore.commit"
      )
    }

    "derive denial Event identity and time from the caller execution profile" in {
      Given("generated fixed caller clocks and deterministic caller ID streams")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _denial_event(instant, "authorization-event-seed")
        val right = _denial_event(instant, "authorization-event-seed")

        left.id == right.id &&
        left.createdAt == instant &&
        right.createdAt == instant &&
        left.id.timestamp.contains(instant) &&
        left.attributes.get("executionContextId") == right.attributes.get("executionContextId") &&
        left.attributes.get("executionContextId").exists(_.contains("authorization"))
      }

      When("equivalent authorization denials are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("their ActionEvent context identity and timestamp are replay-stable")
      checked.passed shouldBe true
    }
  }

  private def _denial_event(
    instant: Instant,
    seed: String
  ): EventRecord = {
    val recorder = new InMemoryCommitRecorder
    val datastore = DataStore.noop(recorder)
    val eventengine = EventEngine.noop(datastore, recorder)
    val runtime = new TestRuntimeContext
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val idgeneration = IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("authorization", "action"),
      clock,
      seed
    )
    val profiled = ExecutionContext.withIdGenerationContext(base, idgeneration)
    val ctx = ExecutionContext.withRuntimeContext(profiled, runtime.runtime)
    val uow = new UnitOfWork(ctx, eventengine, recorder)
    runtime.bind(uow)
    val engine = new DenyingActionEngine

    val result = engine.executeAuthorized("test-action", ctx) {
      throw new IllegalStateException("denied ActionCall must not be built")
    }
    result should be_failure
    eventengine.eventStore.query(
      EventStore.Query(
        name = Some("test-action"),
        lane = Some(EventLane.Transactional)
      )
    ).toOption.get.head
  }

  private def _instant(offset: Int): Instant = {
    val epoch = 1_700_000_000L + Math.floorMod(offset.toLong, 1_000_000L)
    Instant.ofEpochSecond(epoch)
  }

  private final class TestActionCall(
    override val core: ActionCall.Core
  ) extends ActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar("ok"))
  }

  private final class DenyingActionEngine extends ActionEngine(
    ActionEngine.Config(),
    org.goldenport.cncf.security.AuthorizationEngine.create()
  ) {
    override protected def authorize_pre(
      actionname: String,
      ec: ExecutionContext
    ): AuthorizationDecision =
      AuthorizationDecision.Deny
  }

  private final class TestRuntimeContext {
    private var _unit_of_work: Option[UnitOfWork] = None
    private val _observability = _test_observability_context()
    private val _driver = FakeHttpDriver.okText("nop")

    val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "test-runtime-context",
        parent = None,
        observabilitycontext = _observability,
        httpdriveroption = Some(_driver)
      ),
      unitofworksupplier = () => _unit_of_work.getOrElse {
        throw new IllegalStateException("UnitOfWork has not been bound")
      },
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in NOOP spec")
      },
      commitaction = _ => (),
      abortaction = _ => (),
      disposeaction = _ => (),
      token = "test-runtime-context"
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)
  }

  private def _test_observability_context(): ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId("action_engine", "authorization"),
      spanId = None,
      correlationId = Some(CorrelationId("action_engine", "runtime"))
    )

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val _buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      _buffer += message

    def entries: Vector[String] =
      _buffer.toVector
  }
}
