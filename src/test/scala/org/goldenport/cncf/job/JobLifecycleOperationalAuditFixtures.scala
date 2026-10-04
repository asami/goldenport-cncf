package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import io.circe.{Json, parser}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, IdGenerationContext, TraceId}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.log.{LogBackend, StructuredLogEvent}
import org.goldenport.protocol.operation.OperationResponse

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobLifecycleOperationalAuditFixtures {
  private val _trace_id: TraceId = TraceId("native", "lifecycle")
  private val _correlation_id: CorrelationId = CorrelationId("native", "lifecycle")
  val instant: Instant = JobExperienceOperationalAuditFixtures.instant
  val secret: String = JobExperienceOperationalAuditFixtures.secret
  val names: Vector[String] = Vector("job.submitted", "job.running", "job.succeeded", "job.failed",
    "job.delayed.scheduled", "job.delayed.enqueued", "job.retry.immediate.submitted",
    "job.retry.delayed.scheduled", "job.retry.delayed.submitted", "job.retry.submitted",
    "job.recovery-required", "job.cancelled", "job.suspended", "job.resumed")

  final class RecordingBackend extends LogBackend {
    private val _events = ArrayBuffer.empty[StructuredLogEvent]
    private val _lines = ArrayBuffer.empty[String]
    override def logStructured(event: StructuredLogEvent): Unit = { _events += event; super.logStructured(event) }
    def writeLine(line: String): Unit = { _lines += line }
    def events: Vector[StructuredLogEvent] = _events.toVector.filter(event => event.message.contains("job.lifecycle.audit.v1 bytes=") || event.message.contains("job.audit.v1 bytes="))
    def lifecycle: Vector[StructuredLogEvent] = events.filter(_.attributes.getString("audit.kind").contains("job-lifecycle"))
    def calls: Vector[StructuredLogEvent] = lifecycle.filter(_.attributes.getString("record-kind").contains("call"))
    def experience: Vector[StructuredLogEvent] = events.filter(_.attributes.getString("audit.kind").contains("job-experience"))
    def lines: Vector[String] = _lines.toVector.filter(line => line.contains("job.lifecycle.audit.v1 bytes=") || line.contains("job.audit.v1 bytes="))
    def clear(): Unit = { _events.clear(); _lines.clear() }
  }

  def withBackend[A](backend: LogBackend, policy: org.goldenport.cncf.observability.VisibilityPolicy = org.goldenport.cncf.observability.VisibilityPolicy.AllowAll)(body: => A): A =
    JobExperienceOperationalAuditFixtures.withBackend(backend, policy)(body)

  def framed(line: String): Json = {
    val marker = if (line.contains("job.lifecycle.audit.v1 bytes=")) "job.lifecycle.audit.v1 bytes=" else "job.audit.v1 bytes="
    val start = line.indexOf(marker)
    if (start < 0) throw new IllegalArgumentException("missing native audit frame")
    val tail = line.substring(start + marker.length)
    val separator = tail.indexOf(' ')
    val length = tail.take(separator).toInt
    val offset = line.take(start + marker.length + separator + 1).getBytes(UTF_8).length
    val body = new String(line.getBytes(UTF_8).slice(offset, offset + length), UTF_8)
    parser.parse(body).fold(error => throw error, identity)
  }

  final class CountingTime(delegate: ManualJobTimeSource) extends JobTimeSource {
    val calls: AtomicInteger = new AtomicInteger(0)
    def now(): Instant = { calls.incrementAndGet(); delegate.now() }
  }

  final class NativeStore extends EventStore {
    private val _delegate = EventStore.inMemory
    private val _results = ArrayBuffer.empty[Consequence[Vector[EventRecord]]]
    val appendCalls: AtomicInteger = new AtomicInteger(0)
    val readCalls: AtomicInteger = new AtomicInteger(0)
    val fault: IllegalStateException = new IllegalStateException(secret)
    var throwAfterAppend: Boolean = false
    var failAfterAppend: Boolean = false
    def results: Vector[Consequence[Vector[EventRecord]]] = _results.toVector
    def append(records: Seq[EventRecord]): Consequence[Vector[EventRecord]] = {
      appendCalls.incrementAndGet()
      val committed = _delegate.append(records)
      if (throwAfterAppend) throw fault
      val result = if (failAfterAppend) Consequence.stateInvalid[Vector[EventRecord]](secret) else committed
      _results += result
      result
    }
    def load(id: EventId): Consequence[Option[EventRecord]] = { readCalls.incrementAndGet(); _delegate.load(id) }
    def query(q: EventStore.Query): Consequence[Vector[EventRecord]] = { readCalls.incrementAndGet(); _delegate.query(q) }
    def replay(q: EventStore.Query): Consequence[Vector[EventRecord]] = { readCalls.incrementAndGet(); _delegate.replay(q) }
    def stored: Vector[EventRecord] = _delegate.query(EventStore.Query()).toOption.get
  }

  final class NativeBus(delegate: EventBus) extends EventBus {
    private val _results = ArrayBuffer.empty[Consequence[EventPublishResult]]
    val calls: AtomicInteger = new AtomicInteger(0)
    val fault: IllegalStateException = new IllegalStateException(secret)
    var throwAfterPublish: Boolean = false
    def results: Vector[Consequence[EventPublishResult]] = _results.toVector
    def register(subscription: EventSubscription): Unit = delegate.register(subscription)
    def subscriptions: Vector[EventSubscription] = delegate.subscriptions
    def subscriptionsVisible(policy: EventPolicyEngine)(using ExecutionContext): Consequence[Vector[EventSubscription]] = delegate.subscriptionsVisible(policy)
    def publish(event: DomainEvent, option: EventPublishOption): Consequence[EventPublishResult] = delegate.publish(event, option)
    private[cncf] def publishRuntime(event: DomainEvent, option: EventPublishOption)(using ExecutionContext): Consequence[EventPublishResult] = {
      calls.incrementAndGet()
      val result = delegate.publishRuntime(event, option)
      if (throwAfterPublish) throw fault
      _results += result
      result
    }
    def publishAuthorized(event: DomainEvent, option: EventPublishOption, policy: EventPolicyEngine)(using ExecutionContext): Consequence[EventPublishResult] = delegate.publishAuthorized(event, option, policy)
  }

  final case class NativeFixture(engine: InMemoryJobEngine, context: ExecutionContext,
    clock: JobExperienceOperationalAuditFixtures.CountingClock,
    ids: JobExperienceOperationalAuditFixtures.CountingIds, time: CountingTime,
    manual: ManualJobTimeSource, timer: InMemoryJobEngine.ManualJobTimer,
    store: NativeStore, bus: NativeBus, dispatched: ArrayBuffer[DomainEvent]) {
    def observations: (Int, Int, Int, Int, Int, Int, Int) =
      (clock.calls.get(), ids.calls.get(), time.calls.get(), timer.pendingCount,
        store.appendCalls.get(), store.readCalls.get(), bus.calls.get())
    def facts(jobId: JobId) = (engine._get_record(jobId), engine.query(jobId), engine.getStatus(jobId),
      engine.getResult(jobId), engine.resourceSnapshot, engine.lifetimeSnapshot(jobId), store.stored, observations)
    def submit(tasks: List[JobTask] = Nil, option: JobSubmitOption = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)): JobId =
      engine.submit(tasks, context, option).toOption.get
  }

  def withNative[A](useBus: Boolean = false, noSink: Boolean = false)(body: NativeFixture => A): A = {
    val clock = new JobExperienceOperationalAuditFixtures.CountingClock
    val ids = new JobExperienceOperationalAuditFixtures.CountingIds(IdGenerationContext.deterministic(
      IdGenerationContext.DEFAULT_NAMESPACE, Clock.fixed(instant, ZoneOffset.UTC), "native-lifecycle-audit"))
    val base = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
    // Independent graphs receive the same original core-owned diagnostic references.
    // Their native ID provider/state/timer/store graphs remain independent.
    val observed = ExecutionContext.withObservabilityContext(base, base.observability.copy(
      traceId = _trace_id, correlationId = Some(_correlation_id)))
    val context = ExecutionContext.withSecurityContext(observed, JobExperienceOperationalAuditFixtures.context().security)
    val manual = new ManualJobTimeSource(instant)
    val time = new CountingTime(manual)
    val timer = new InMemoryJobEngine.ManualJobTimer(manual)
    val store = new NativeStore
    val bus = new NativeBus(EventBus.default(EventEngine.noop(DataStore.noop(), eventstore = store)))
    val dispatched = ArrayBuffer.empty[DomainEvent]
    bus.register(EventSubscription("native-observation", handler = new EventDispatchHandler {
      def dispatch(event: DomainEvent): Consequence[Unit] = { dispatched += event; Consequence.unit }
    }))
    val engine = new InMemoryJobEngine(schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
      timeSource = time, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
    if (!noSink) engine.withEventStore(store)
    if (useBus) engine.withEventBus(bus)
    try body(NativeFixture(engine, context, clock, ids, time, manual, timer, store, bus, dispatched))
    finally { engine.shutdown(); timer.shutdown() }
  }

  def record(context: ExecutionContext, tasks: Vector[JobTaskReadModel] = Vector.empty): JobRecord =
    JobRecord(JobId("native", "job", Some(instant), Some("original")), Nil, context,
      JobStatus.Submitted, None, JobPersistencePolicy.Ephemeral, JobRunMode.Async, 0,
      createdAt = instant, updatedAt = instant, taskReadModels = tasks, timeline = Vector.empty,
      debug = JobDebugInfo(Some(secret), Map("private" -> secret), Vector(secret)))

  def acknowledgement(record: JobRecord, name: String = "job.submitted"): EventRecord =
    EventRecord(EventId("native", "event", Some(instant), Some("original")), name, name,
      Map("private" -> secret), Map("cncf.job.id" -> record.id.value, "private" -> secret),
      instant, true, EventRecord.Status.Stored, EventLane.NonTransactional, 1L)

  final class RetryTask extends JobTask {
    val calls: AtomicInteger = new AtomicInteger(0)
    val actionId: ActionId = ActionId("native", "retry", Some(instant), Some("original"))
    def run(ctx: ExecutionContext): TaskOutcome =
      if (calls.incrementAndGet() == 1) TaskFailed(Conclusion.simple("retry now").copy(
        disposition = Disposition(Disposition.UserAction.RetryNow)))
      else TaskSucceeded(OperationResponse.Scalar("completed"))
  }
}
