package org.goldenport.cncf.job

import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method, Proxy}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{EventBus, EventEngine, EventStore}
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, UnitOfWork}
import org.goldenport.protocol.operation.OperationResponse

/*
 * Real configured native owner; only outer-event faults and task observations
 * are modeled here. Every case owns a finally-cleaned target-private root.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobUnitOfWorkSubmissionFixtures {
  type NativeFixture = JobDurableRuntimeFixtures.Fixture
  enum Ending { case Commit, Abort, Dispose }
  enum EventFault { case None, PrepareRejected, CommitException, EmitException }

  val option: JobSubmitOption = JobSubmitOption(
    persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async)

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)

  def withFixture[A](body: Fixture => A): A =
    configuredFixture(_ => ())(body)

  def configuredFixture[A](prepare: NativeFixture => Unit)(body: Fixture => A): A =
    JobDurableRuntimeFixtures.withFixture { native =>
      prepare(native)
      success(native.start())
      val fixture = new Fixture(native)
      try body(fixture) finally fixture.disposeAll()
    }

  final class Recorder extends CommitRecorder {
    private val _entries = ArrayBuffer.empty[String]
    def entries: Vector[String] = _entries.toVector
    def record(message: String): Unit = { _entries += message; () }
  }

  final class Fixture(val native: NativeFixture) {
    val events: EventStore = EventStore.inMemory
    val recorder = new Recorder
    private val _unit_of_works = ArrayBuffer.empty[UnitOfWork]
    native.engine.withEventBus(EventBus.default(EventEngine.noop(DataStore.noop(), eventstore = events)))

    def unitOfWork(fault: EventFault = EventFault.None): UnitOfWork = {
      val result = new UnitOfWork(native.context, eventEngine(fault, recorder, events), recorder)
      _unit_of_works += result
      result
    }
    def stage(task: JobTask, unitOfWork: UnitOfWork, submitOption: JobSubmitOption = option): JobId =
      success(native.engine.stageSubmit(List(task), unitOfWork, submitOption))
    def timerCount: Int =
      native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.pendingTimerCount
    def ledger: Option[DurableJobLifetimeQuotaRecord] = success(native.owner._execute_c {
      val id = success(DurableJobLifetimeQuotaEntity.entityId)
      val collection = DataStore.CollectionId.EntityStore(id.collection)
      native.context.dataStoreSpace.dataStore(collection)
        .flatMap(_.load(collection, DataStore.EntryId(id))(using native.context)).map(_.map { row =>
          success(DurableJobLifetimeQuotaCodec.decode(row.getString("canonicalJson").get))
        })
    })
    def failNativeEmit(): Unit = {
      native.engine.withEventBus(EventBus.default(eventEngine(EventFault.EmitException, recorder, events)))
      ()
    }
    def end(unitOfWork: UnitOfWork, ending: Ending): Consequence[Unit] = ending match {
      case Ending.Commit => unitOfWork.commit()
      case Ending.Abort => unitOfWork.abort()
      case Ending.Dispose => unitOfWork.dispose()
    }
    def disposeAll(): Unit = { _unit_of_works.foreach(_.dispose()); () }
  }

  def eventEngine(fault: EventFault, recorder: CommitRecorder,
    store: EventStore): EventEngine = {
    val delegate = EventEngine.noop(DataStore.noop(recorder), recorder, store)
    Proxy.newProxyInstance(classOf[EventEngine].getClassLoader, Array(classOf[EventEngine]),
      new InvocationHandler {
        def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
          (fault, method.getName) match {
            case (EventFault.PrepareRejected, "prepare") =>
              recorder.record("EventEngine.prepare")
              PrepareResult.Rejected("planned UOW prepare rejection")
            case (EventFault.CommitException, "commit") =>
              throw new IllegalStateException("planned EventEngine commit exception")
            case (EventFault.EmitException, "emit") =>
              throw new IllegalStateException("planned native event commit exception")
            case _ =>
              try method.invoke(delegate, Option(args).getOrElse(Array.empty[Object])*)
              catch { case exception: InvocationTargetException => throw exception.getCause }
          }
      }).asInstanceOf[EventEngine]
  }

  final class Task extends JobTask {
    val getters = new AtomicInteger
    val runs = new AtomicInteger
    val canonical = new AtomicInteger
    val admissions = new AtomicInteger
    def actionId: ActionId = {
      getters.incrementAndGet()
      ActionId("cncf", "uow", Some(JobDurableRuntimeFixtures.instant), Some("task"))
    }
    override def requestSummary: Option[String] = { getters.incrementAndGet(); Some("native UOW task") }
    override def requestParameters: Map[String, String] = { getters.incrementAndGet(); Map("origin" -> "uow") }
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def serviceName: Option[String] = Some("service-a")
    override def operationName: Option[String] = Some("run")
    def run(ctx: ExecutionContext): TaskOutcome = { runs.incrementAndGet(); TaskSucceeded(OperationResponse.Scalar("ok")) }
    override def observeCanonicalOutcome(outcome: TaskOutcome, ctx: ExecutionContext, cancelled: Boolean): Unit = {
      canonical.incrementAndGet(); ()
    }
    override def observeAdmissionFailure(conclusion: Conclusion, ctx: ExecutionContext): Unit = {
      admissions.incrementAndGet(); ()
    }
  }

  final class UnsupportedEngine(delegate: JobEngine) extends JobEngine {
    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = delegate.submit(tasks, ctx)
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] =
      delegate.submit(tasks, ctx, option)
    def getStatus(jobId: JobId): Option[JobStatus] = delegate.getStatus(jobId)
    def getResult(jobId: JobId): Option[JobResult] = delegate.getResult(jobId)
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = delegate.awaitResult(jobId, timeoutMillis)
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy)
      (using ExecutionContext): Consequence[JobControlResponse] = delegate.control(jobId, request, policy)
    def query(jobId: JobId): Option[JobQueryReadModel] = delegate.query(jobId)
    def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = delegate.queryTasks(jobId, offset, limit)
    def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = delegate.queryTimeline(jobId, offset, limit)
  }
}
