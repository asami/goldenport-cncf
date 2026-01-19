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
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers
import java.time.Instant

/*
 * @since   Jan.  7, 2026
 * @version Jan. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngineObservabilitySeparationSpec
  extends AnyWordSpec
  with Matchers
  with ConsequenceMatchers {
  "ActionEngine observability separation" should {
    "emit ActionEvent without observe hooks on authorization failure" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new TestRuntimeContext
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
      runtime.bind(uow)

      var buildCalled = false
      val engine = new RecordingDenyActionEngine
      val action = new Query() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core, engine)
      }

      val result = engine.executeAuthorized("test-action", ctx) {
        buildCalled = true
        action.createCall(ActionCall.Core(action, ctx, None))
      }

      buildCalled shouldBe false
      result should be_failure
      engine.events shouldBe Vector.empty
      eventEngine.stagedEvents.size shouldBe 1
      eventEngine.stagedEvents.head match {
        case e: ActionEvent =>
          e.result shouldBe ActionResult.AuthorizationFailed
          e.reason.isDefined shouldBe true
        case other =>
          fail(s"unexpected event: ${other}")
      }
    }

    "separate observe hooks from ActionEvent on success" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new SuccessRuntimeContext("test-action")
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
      runtime.bind(uow)

      val engine = new RecordingAllowActionEngine
      val action = new Query() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core, engine)
      }

      val result = engine.executeAuthorized("test-action", ctx) {
        action.createCall(ActionCall.Core(action, ctx, None))
      }

      result should be_success
      engine.events shouldBe Vector(
        "observe_enter",
        "execute",
        "observe_leave"
      )
      eventEngine.stagedEvents.size shouldBe 1
      eventEngine.stagedEvents.head match {
        case e: ActionEvent =>
          e.result shouldBe ActionResult.Succeeded
          e.actionName shouldBe "test-action"
        case other =>
          fail(s"unexpected event: ${other}")
      }
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
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message

    def events: Vector[String] =
      buffer.toVector

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
      actionName: String,
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
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message

    def events: Vector[String] =
      buffer.toVector

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
      actionName: String,
      ec: ExecutionContext
    ): AuthorizationDecision =
      AuthorizationDecision.Deny
  }

  private abstract class RuntimeTestSupport {
    private var _unit_of_work: Option[UnitOfWork] = None
    private val observability = _testObservabilityContext()
    private val driver = FakeHttpDriver.okText("nop")

    val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "test-runtime-context",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver)
      ),
      unitOfWorkSupplier = () => _unit_of_work.getOrElse {
        throw new IllegalStateException("UnitOfWork has not been bound")
      },
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in observability spec")
      },
      unitOfWorkTryInterpreterFn = new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in observability spec")
      },
      unitOfWorkEitherInterpreterFn = new (UnitOfWorkOp ~> RuntimeContext.EitherThrowable) {
        def apply[A](op: UnitOfWorkOp[A]): Either[Throwable, A] =
          Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in observability spec"))
      },
      commitAction = commitAction,
      abortAction = _ => (),
      disposeAction = _ => (),
      token = token
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)

    protected def commitAction(unitOfWork: UnitOfWork): Unit
    protected def token: String
  }

  private final class TestRuntimeContext extends RuntimeTestSupport {
    override protected def commitAction(unitOfWork: UnitOfWork): Unit = ()
    override protected def token: String = "test-runtime-context"
  }

  private final class SuccessRuntimeContext(
    actionName: String
  ) extends RuntimeTestSupport {
    override protected def commitAction(unitOfWork: UnitOfWork): Unit = {
      val event = ActionEvent(
        ExecutionContextId.generate(),
        actionName,
        ActionResult.Succeeded,
        None,
        Instant.now()
      )
      unitOfWork.commit(Seq(event))
    }

    override protected def token: String = s"test-runtime-context-${actionName}"
  }

  private def _testObservabilityContext(): ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId("action_engine", "observability"),
      spanId = None,
      correlationId = Some(CorrelationId("action_engine", "runtime"))
    )

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message
  }
}
