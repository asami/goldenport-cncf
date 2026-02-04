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

/*
 * @since   Jan.  6, 2026
 * @version Feb.  4, 2026
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
      val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
      val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
      runtime.bind(uow)

      var buildCalled = false
      val engine = new DenyingActionEngine

      val action = new Query() {
        // val name = "test-action"
        val request = Request.ofOperation("test-action")
        def createCall(core: ActionCall.Core): ActionCall =
          new TestActionCall(core)
      }

      val result = engine.executeAuthorized("test-action", ctx) {
        buildCalled = true
        action.createCall(ActionCall.Core(action, ctx, None, None))
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

  private final class TestRuntimeContext {
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
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in NOOP spec")
      },
      unitOfWorkTryInterpreterFn = new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in NOOP spec")
      },
      unitOfWorkEitherInterpreterFn = new (UnitOfWorkOp ~> RuntimeContext.EitherThrowable) {
        def apply[A](op: UnitOfWorkOp[A]): Either[Throwable, A] =
          Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in NOOP spec"))
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "test-runtime-context"
    )

    def bind(uow: UnitOfWork): Unit =
      _unit_of_work = Some(uow)
  }

  private def _testObservabilityContext(): ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId("action_engine", "authorization"),
      spanId = None,
      correlationId = Some(CorrelationId("action_engine", "runtime"))
    )

  private final class InMemoryCommitRecorder extends CommitRecorder {
    private val buffer = scala.collection.mutable.ArrayBuffer.empty[String]

    def record(message: String): Unit =
      buffer += message

    def entries: Vector[String] =
      buffer.toVector
  }
}
