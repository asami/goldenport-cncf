package org.goldenport.cncf.action

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, SystemContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{ActionEvent, ActionResult, EventEngine}
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
class ActionEngineAuthorizationFailureCommitSpec extends AnyWordSpec with Matchers with ConsequenceMatchers{
  "ActionEngine authorization failure" should {
    "commit ActionEvent without invoking ActionCall" in {
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

      var buildCalled = false
      val engine = new DenyingActionEngine

      val action = new Query() {
        val name = "test-action"
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core)
      }

      val result = engine.executeAuthorized("test-action", ctx) {
        buildCalled = true
        action.createCall(ActionCall.Core(action, ctx, None))
      }

      buildCalled shouldBe false
      result should be_failure
      eventEngine.stagedEvents.size shouldBe 1
      eventEngine.stagedEvents.head match {
        case e: ActionEvent =>
          e.result shouldBe ActionResult.AuthorizationFailed
          e.reason.isDefined shouldBe true
          e.actionName shouldBe "test-action"
        case other =>
          fail(s"unexpected event: ${other}")
      }
      recorder.entries shouldBe Vector(
        "UnitOfWork.prepare",
        "DataStore.prepare",
        "EventEngine.prepare",
        "UnitOfWork.commit",
        "DataStore.commit",
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
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in NOOP spec")
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in NOOP spec")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
      Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in NOOP spec"))

    def commit(): Unit = {}
    def abort(): Unit = {}
    def dispose(): Unit = {}

    def toToken: String = "test-runtime-context"
  }

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message

    def entries: Vector[String] =
      buffer.toVector
  }
}
