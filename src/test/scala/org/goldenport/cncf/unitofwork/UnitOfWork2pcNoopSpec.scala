package org.goldenport.cncf.unitofwork

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, Command, ResourceAccess}
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, SystemContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class UnitOfWork2pcNoopSpec extends AnyWordSpec with Matchers with ConsequenceMatchers {
  "UnitOfWork 2PC NOOP" should {
    "commit in the expected order when all participants prepare" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = DataStore.noop(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val actionCall = buildActionCall(recorder, dataStore, eventEngine)

      val result = actionCall.commit()

      result should be_success
      recorder.entries shouldBe Vector(
        "ActionCall.commit",
        "UnitOfWork.prepare",
        "DataStore.prepare",
        "EventEngine.prepare",
        "UnitOfWork.commit",
        "DataStore.commit",
        "EventEngine.commit",
        "DataStore.commit"
      )
    }

    "abort in the expected order when a participant rejects prepare" in {
      val recorder = new InMemoryCommitRecorder
      val dataStore = new RejectingDataStore(recorder)
      val eventEngine = EventEngine.noop(dataStore, recorder)
      val actionCall = buildActionCall(recorder, dataStore, eventEngine)

      val result = actionCall.commit()

      result should be_failure
      recorder.entries shouldBe Vector(
        "ActionCall.commit",
        "UnitOfWork.prepare",
        "DataStore.prepare",
        "EventEngine.prepare",
        "UnitOfWork.abort",
        "DataStore.abort",
        "EventEngine.abort"
      )
    }
  }

  private def buildActionCall(
    recorder: CommitRecorder,
    dataStore: DataStore,
    eventEngine: EventEngine
  ): ActionCall = {
    val runtime = new TestRuntimeContext
    val base = ExecutionContext.create()
    val ctx = ExecutionContext.Instance(
      base.core,
      base.cncfCore.copy(runtime = runtime, system = SystemContext.empty)
    )
    val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
    runtime.bind(uow)

    val action = new Command("test") {
      def createCall(core: ActionCall.Core): ActionCall =
        new TestActionCall(core)
    }
    val core = ActionCall.Core(action, ctx, None)
    action.createCall(core)
  }

  private final class TestActionCall(
    override val core: ActionCall.Core
  ) extends ActionCall {
    override def action: Action = core.action
    override def accesses: Seq[ResourceAccess] = Nil
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar("ok"))
  }

  private final class TestRuntimeContext extends RuntimeContext {
    private var _unit_of_work: UnitOfWork = null

    def bind(uow: UnitOfWork): Unit = {
      _unit_of_work = uow
    }

    def unitOfWork: UnitOfWork = _unit_of_work

    def unitOfWorkInterpreter[T]: (UnitOfWork.UnitOfWorkOp ~> Id) =
      new (UnitOfWork.UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWork.UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in NOOP spec")
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWork.UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWork.UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWork.UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in NOOP spec")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWork.UnitOfWorkOp[T]): Either[Throwable, T] =
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

  private final class RejectingDataStore(
    recorder: CommitRecorder
  ) extends DataStore {
    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("DataStore.prepare")
      PrepareResult.Rejected("rejected")
    }

    def commit(tx: TransactionContext): Unit =
      recorder.record("DataStore.commit")

    def abort(tx: TransactionContext): Unit =
      recorder.record("DataStore.abort")
  }
}
