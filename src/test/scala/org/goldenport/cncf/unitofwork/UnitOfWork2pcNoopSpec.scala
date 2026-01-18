package org.goldenport.cncf.unitofwork

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, Command, ResourceAccess}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ExecutionContextId, ObservabilityContext, RuntimeContext, TraceId}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.ConsequenceMatchers

/*
 * @since   Jan.  6, 2026
 * @version Jan. 17, 2026
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
        "EventEngine.abort",
        "DataStore.abort"
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
    val ctx = ExecutionContext.withRuntimeContext(base, runtime.runtime)
    val uow = new UnitOfWork(ctx, dataStore, eventEngine, recorder)
    runtime.bind(uow)

    val action = new Command() {
      // val name = "test"
      val request = Request.ofOperation("test")
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
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar("ok"))
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

  private def _testObservabilityContext(): ObservabilityContext = {
    val id = ExecutionContextId.generate()
    ObservabilityContext(
      traceId = TraceId(id),
      spanId = None,
      correlationId = Some(CorrelationId(id))
    )
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
    def create(id: org.goldenport.id.UniversalId, record: Record): Unit = {}

    def load(id: org.goldenport.id.UniversalId): Option[Record] =
      None

    def store(id: org.goldenport.id.UniversalId, record: Record): Unit = {}

    def update(id: org.goldenport.id.UniversalId, changes: Record): Unit = {}

    def delete(id: org.goldenport.id.UniversalId): Unit = {}

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
