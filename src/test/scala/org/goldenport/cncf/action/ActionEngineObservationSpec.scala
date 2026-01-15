package org.goldenport.cncf.action

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, SystemContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers

/*
 * @since   Jan.  6, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngineObservationSpec extends AnyWordSpec with Matchers with ConsequenceMatchers {
  "ActionEngine observation hooks" should {
    "not invoke observe_enter/leave on authorization failure" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new TestRuntimeContext
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.Instance(
        base.core,
        base.cncfCore.copy(runtime = runtime, system = SystemContext.empty)
      )
      val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
      runtime.bind(uow)

      val engine = new RecordingDenyActionEngine

      val action = new Query() {
        val name = "test-action"
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core, engine)
      }
      val result = engine.executeAuthorized("test-action", ctx) {
        action.createCall(ActionCall.Core(action, ctx, None))
      }

      result should be_failure
      engine.events shouldBe Vector.empty
    }

    "invoke observe_enter/leave around execute on success" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val runtime = new TestRuntimeContext
      val base = ExecutionContext.create()
      val ctx = ExecutionContext.Instance(
        base.core,
        base.cncfCore.copy(runtime = runtime, system = SystemContext.empty)
      )
      val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
      runtime.bind(uow)

      val engine = new RecordingAllowActionEngine
      val action = new Query() {
        val name = "test-action"
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

  private final class TestRuntimeContext extends RuntimeContext {
    private var _unit_of_work: UnitOfWork = null

    def bind(uow: UnitOfWork): Unit = {
      _unit_of_work = uow
    }

    def unitOfWork: UnitOfWork = _unit_of_work

    def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> Id) =
      new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in observation spec")
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in observation spec")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
      Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in observation spec"))

    def commit(): Unit = {}
    def abort(): Unit = {}
    def dispose(): Unit = {}

    def toToken: String = "test-runtime-context"
  }

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message
  }
}
