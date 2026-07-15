package org.goldenport.cncf.action

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ExecutionContextId, ObservabilityContext, RuntimeContext, TraceId}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{ActionEvent, ActionResult, EventEngine}
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers
import java.time.Instant

/*
 * @since   Jan.  7, 2026
 *  version Feb. 27, 2026
 *  version Mar. 12, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngineObservabilitySeparationSpec
  extends AnyWordSpec
  with Matchers
  with ConsequenceMatchers
  with GivenWhenThen {
  "ActionEngine observability separation" should {
    "emit ActionEvent without observe hooks on authorization failure" in {
      Given("an action engine that denies authorization before observation hooks")
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new TestRuntimeContext
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, eventEngine, recorder)
      runtime.bind(uow)

      var buildCalled = false
      val engine = new RecordingDenyActionEngine
      val action = new QueryAction() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core, engine)
      }

      When("the protected action is executed")
      val result = engine.executeAuthorized("test-action", ctx) {
        buildCalled = true
        action.createCall(ActionCall.Core(action, ctx, None, None))
      }

      Then("no observation hook runs and one denial event is committed")
      buildCalled shouldBe false
      result should be_failure
      engine.events shouldBe Vector.empty
      val deniedevents = eventEngine.eventStore.query(
        org.goldenport.cncf.event.EventStore.Query(
          name = Some("test-action"),
          lane = Some(org.goldenport.cncf.event.EventLane.Transactional)
        )
      ).toOption.get
      deniedevents should have size 1
      deniedevents.head.kind shouldBe ActionResult.AuthorizationFailed.toString.toLowerCase
      deniedevents.head.payload.get("reason").collect { case value: String => value }.exists(_.nonEmpty) shouldBe true
    }

    "separate observe hooks from ActionEvent on success" in {
      Given("an allowed action engine and an event-backed successful runtime")
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new SuccessRuntimeContext("test-action")
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, eventEngine, recorder)
      runtime.bind(uow)

      val engine = new RecordingAllowActionEngine
      val action = new QueryAction() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core, engine)
      }

      When("the action succeeds")
      val result = engine.executeAuthorized("test-action", ctx) {
        action.createCall(ActionCall.Core(action, ctx, None, None))
      }

      Then("observation hooks and the committed action event remain separate")
      result should be_success
      engine.events shouldBe Vector(
        "observe_enter",
        "execute",
        "observe_leave"
      )
      val succeededevents = eventEngine.eventStore.query(
        org.goldenport.cncf.event.EventStore.Query(
          name = Some("test-action"),
          lane = Some(org.goldenport.cncf.event.EventLane.Transactional)
        )
      ).toOption.get
      succeededevents should have size 1
      succeededevents.head.kind shouldBe ActionResult.Succeeded.toString.toLowerCase
    }
  }

  private final class TestActionCall(
    override val core: ActionCall.Core,
    engine: RecordingEngine
  ) extends ActionCall {
    override def execute(): Consequence[OperationResponse] = {
      engine.record("execute")
      Consequence.success(OperationResponse.Scalar("ok"))
    }
  }

  private sealed trait RecordingEngine {
    def record(message: String): Unit
  }

  private final class RecordingAllowActionEngine
    extends ActionEngine(
      ActionEngine.Config(),
      org.goldenport.cncf.security.AuthorizationEngine.create()
    )
    with RecordingEngine {
    private val _buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      _buffer += message

    def events: Vector[String] =
      _buffer.toVector

    override protected def observe_enter(
      call: ActionCall
    ): Unit =
      record("observe_enter")

    override protected def observe_leave(
      call: ActionCall,
      result: Consequence[OperationResponse]
    ): Unit =
      record("observe_leave")

    override protected def authorize_pre(
      actionname: String,
      ec: ExecutionContext
    ): AuthorizationDecision =
      AuthorizationDecision.Allow
  }

  private final class RecordingDenyActionEngine
    extends ActionEngine(
      ActionEngine.Config(),
      org.goldenport.cncf.security.AuthorizationEngine.create()
    )
    with RecordingEngine {
    private val _buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      _buffer += message

    def events: Vector[String] =
      _buffer.toVector

    override protected def observe_enter(
      call: ActionCall
    ): Unit =
      record("observe_enter")

    override protected def observe_leave(
      call: ActionCall,
      result: Consequence[OperationResponse]
    ): Unit =
      record("observe_leave")

    override protected def authorize_pre(
      actionname: String,
      ec: ExecutionContext
    ): AuthorizationDecision =
      AuthorizationDecision.Deny
  }

  private abstract class RuntimeTestSupport {
    private var _unit_of_work: Option[UnitOfWork] = None
    private val _observability = _test_observability_context()
    private val _driver = FakeHttpDriver.okText("nop")

    val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "test-runtime-context",
        parent = None,
        observabilityContext = _observability,
        httpDriverOption = Some(_driver)
      ),
      unitOfWorkSupplier = () => _unit_of_work.getOrElse {
        throw new IllegalStateException("UnitOfWork has not been bound")
      },
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in observability spec")
      },
      commitAction = commit_action,
      abortAction = _ => (),
      disposeAction = _ => (),
      token = token
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)

    protected def commit_action(unitofwork: UnitOfWork): Unit
    protected def token: String
  }

  private final class TestRuntimeContext extends RuntimeTestSupport {
    override protected def commit_action(unitofwork: UnitOfWork): Unit = ()
    override protected def token: String = "test-runtime-context"
  }

  private final class SuccessRuntimeContext(
    actionname: String
  ) extends RuntimeTestSupport {
    override protected def commit_action(unitofwork: UnitOfWork): Unit = {
      val event = ActionEvent(
        ExecutionContextId.generate(),
        actionname,
        ActionResult.Succeeded,
        None,
        Instant.now()
      )
      unitofwork.commit(Seq(event))
    }

    override protected def token: String = s"test-runtime-context-${actionname}"
  }

  private def _test_observability_context(): ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId("action_engine", "observability"),
      spanId = None,
      correlationId = Some(CorrelationId("action_engine", "runtime"))
    )

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val _buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      _buffer += message
  }
}
