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

/*
 * @since   Jan.  6, 2026
 *  version Feb. 27, 2026
 *  version Mar. 12, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngineAuthorizationFailureCommitSpec extends AnyWordSpec with Matchers with ConsequenceMatchers with GivenWhenThen {
  "ActionEngine authorization failure" should {
    "commit ActionEvent without invoking ActionCall" in {
      Given("an action engine that denies authorization before building the action call")
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new TestRuntimeContext
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, eventEngine, recorder)
      runtime.bind(uow)

      var buildCalled = false
      val engine = new DenyingActionEngine

      val action = new QueryAction() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core)
      }

      When("the protected action is executed")
      val result = engine.executeAuthorized("test-action", ctx) {
        buildCalled = true
        action.createCall(ActionCall.Core(action, ctx, None, None))
      }

      Then("the action call is not built and one authorization failure event is committed")
      buildCalled shouldBe false
      result should be_failure
      val events = eventEngine.eventStore.query(
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
        observabilityContext = _observability,
        httpDriverOption = Some(_driver)
      ),
      unitOfWorkSupplier = () => _unit_of_work.getOrElse {
        throw new IllegalStateException("UnitOfWork has not been bound")
      },
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in NOOP spec")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
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
