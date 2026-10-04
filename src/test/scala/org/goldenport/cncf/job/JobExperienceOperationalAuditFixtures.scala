package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext, Principal, PrincipalId, SecurityContext, SessionContext}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder, StructuredLogEvent}
import org.goldenport.cncf.observability.{ObservabilityEngine, VisibilityPolicy}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobExperienceOperationalAuditFixtures {
  val instant: Instant = Instant.parse("2026-10-03T00:00:00Z")
  val secret: String = "PRIVATE-credential-provider-/path/request-秘密"

  final class RecordingBackend extends LogBackend {
    private val _events = ArrayBuffer.empty[StructuredLogEvent]
    private val _lines = ArrayBuffer.empty[String]
    override def logStructured(event: StructuredLogEvent): Unit = {
      _events += event
      super.logStructured(event)
    }
    def writeLine(line: String): Unit = { _lines += line }
    def events: Vector[StructuredLogEvent] = _events.toVector.filter(_.message.contains("job.audit.v1 bytes="))
    def lines: Vector[String] = _lines.toVector.filter(_.contains("job.audit.v1 bytes="))
    def clear(): Unit = { _events.clear(); _lines.clear() }
  }

  final class ThrowingBackend extends LogBackend {
    val calls: AtomicInteger = new AtomicInteger(0)
    def writeLine(line: String): Unit = {
      calls.incrementAndGet()
      throw new IllegalStateException(secret)
    }
  }

  def withBackend[A](backend: LogBackend, policy: VisibilityPolicy = VisibilityPolicy.AllowAll)(body: => A): A = {
    val original = LogBackendHolder.backend.getOrElse(throw new IllegalStateException("backend fixture requires the installed holder object"))
    val originalpolicy = ObservabilityEngine.visibilityPolicy
    try {
      // Keep the saved bootstrap buffer intact instead of replaying prior tests.
      LogBackendHolder.install(LogBackend.NopLogBackend)
      LogBackendHolder.install(backend)
      ObservabilityEngine.updateVisibilityPolicy(policy)
      body
    } finally {
      LogBackendHolder.install(original)
      ObservabilityEngine.updateVisibilityPolicy(originalpolicy)
    }
  }

  final class CountingClock extends Clock {
    val calls: AtomicInteger = new AtomicInteger(0)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(JobExperienceOperationalAuditFixtures.instant, zone)
    def instant(): Instant = { calls.incrementAndGet(); JobExperienceOperationalAuditFixtures.instant }
  }

  final class CountingIds(delegate: IdGenerationContext) extends IdGenerationContext {
    val calls: AtomicInteger = new AtomicInteger(0)
    def namespace: IdGenerationContext.IdNamespace = delegate.namespace
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
      calls.incrementAndGet(); delegate.entityId(collection, purpose)
    }
    def opaqueId(purpose: String): String = { calls.incrementAndGet(); delegate.opaqueId(purpose) }
  }

  def context(subject: String = "owner", session: String = "owner-session",
    privilege: SecurityContext.Privilege = SecurityContext.Privilege.ApplicationContentManager): ExecutionContext = {
    val base = ExecutionContext.create(privilege)
    ExecutionContext.withSecurityContext(base, base.security.copy(
      principal = new Principal {
        def id: PrincipalId = PrincipalId(subject)
        def attributes: Map[String, String] = Map("authenticated" -> "true")
      },
      session = Some(SessionContext(sessionId = Some(session)))
    ))
  }

  final case class NativeFixture(manual: JobEngineTestFixture.ManualFixture, engine: MeasuredEngine,
    owner: ExecutionContext, foreign: ExecutionContext, ordinary: ExecutionContext,
    clock: CountingClock, ids: CountingIds) {
    def service: JobExperienceService = new JobExperienceService(engine)
    def snapshot(jobIds: Vector[JobId]): Vector[Option[JobQueryReadModel]] = jobIds.map(manual.engine.query)
    def observations: (Int, Int, Instant) = (clock.calls.get(), ids.calls.get(), manual.clock.now())
    def submit(tasks: List[JobTask] = Nil, foreignOwner: Boolean = false): JobId =
      manual.engine.submit(tasks, if (foreignOwner) foreign else owner,
        JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)).toOption.get
  }

  def withNative[A](body: NativeFixture => A): A = {
    val manual = JobEngineTestFixture.manual(initialTime = instant)
    val clock = new CountingClock
    val ids = new CountingIds(IdGenerationContext.deterministic(IdGenerationContext.DEFAULT_NAMESPACE,
      Clock.fixed(instant, ZoneOffset.UTC), "job-experience-audit"))
    val base = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
    val ownersecurity = context().security
    val owner = ExecutionContext.withSecurityContext(base, ownersecurity)
    val foreign = ExecutionContext.withSecurityContext(base, context("foreign", "foreign-session").security)
    val ordinary = ExecutionContext.withSecurityContext(base, context(privilege = SecurityContext.Privilege.User).security)
    try body(NativeFixture(manual, new MeasuredEngine(manual.engine), owner, foreign, ordinary, clock, ids))
    finally { manual.engine.shutdown(); manual.timer.shutdown() }
  }

  final class MeasuredEngine(delegate: JobEngine) extends JobEngine {
    private val _calls = ArrayBuffer.empty[String]
    def calls: Vector[String] = _calls.toVector
    def clear(): Unit = _calls.clear()
    private def _record[A](name: String)(body: => A): A = { _calls += name; body }
    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = delegate.submit(tasks, ctx)
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = delegate.submit(tasks, ctx, option)
    def getStatus(jobId: JobId): Option[JobStatus] = delegate.getStatus(jobId)
    def getResult(jobId: JobId): Option[JobResult] = delegate.getResult(jobId)
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = delegate.awaitResult(jobId, timeoutMillis)
    def query(jobId: JobId): Option[JobQueryReadModel] = delegate.query(jobId)
    def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = delegate.queryTasks(jobId, offset, limit)
    def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = delegate.queryTimeline(jobId, offset, limit)
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy)(using ctx: ExecutionContext): Consequence[JobControlResponse] =
      _record("control")(delegate.control(jobId, request, policy))
    override def queryPage(request: JobManagementQuery, policy: JobQueryPolicy)(using ctx: ExecutionContext): Consequence[JobManagementPage] =
      _record("page")(delegate.queryPage(request, policy))
    override def queryManagementDetail(jobId: JobId, policy: JobQueryPolicy)(using ctx: ExecutionContext): Consequence[Option[JobManagementDetail]] =
      _record("detail")(delegate.queryManagementDetail(jobId, policy))
    override def queryManagementResult(jobId: JobId, policy: JobQueryPolicy)(using ctx: ExecutionContext): Consequence[Option[JobManagementResult]] =
      _record("result")(delegate.queryManagementResult(jobId, policy))
    override def queryManagementTasks(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy)(using ctx: ExecutionContext): Consequence[Option[JobTaskPage]] =
      _record("tasks")(delegate.queryManagementTasks(jobId, offset, limit, policy))
    override def queryManagementTimeline(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy)(using ctx: ExecutionContext): Consequence[Option[JobTimelinePage]] =
      _record("timeline")(delegate.queryManagementTimeline(jobId, offset, limit, policy))
    override def metrics: Option[JobMetrics] = _record("metrics")(delegate.metrics)
  }

  final class AdversarialTask(action: ActionId) extends JobTask {
    val executions: AtomicInteger = new AtomicInteger(0)
    def actionId: ActionId = action
    override def taskKind: String = "action"
    override def targetKind: Option[String] = Some(secret + "-target")
    override def relation: Option[String] = Some(secret + "-relation")
    override def transactionRole: Option[String] = Some(secret + "-role")
    override def transactionScope: Option[String] = Some(secret + "-scope")
    override def compensationActionRef: Option[String] = Some(secret + "-compensation")
    override def componentName: Option[String] = Some(secret + "-component")
    override def serviceName: Option[String] = Some(secret + "-service")
    override def operationName: Option[String] = Some(secret + "-operation")
    def run(ctx: ExecutionContext): TaskOutcome = {
      executions.incrementAndGet()
      TaskFailed(Consequence.stateInvalid[Nothing](secret + "-failure").conclusion)
    }
  }

  def hostileTasks(marker: String = secret, count: Int = 2): JobTaskPage = {
    val ids = IdGenerationContext.deterministic(IdGenerationContext.DEFAULT_NAMESPACE, "hostile-pages")
    val parent = TaskId.create("parent", instant, ids)
    val compensates = TaskId.create("compensates", instant, ids)
    val rows = Vector.tabulate(count) { index =>
      JobTaskReadModel(TaskId.create("task", instant, ids), Some(parent), JobTaskStatus.Failed,
        instant, Some(instant.plusSeconds(index)), JobTaskResultSummary(false, Some(marker + "-result")),
        Some(marker + "-component"), Some(marker + "-service"), Some(marker + "-operation"),
        marker + "-kind", Some(marker + "-target"), Some(marker + "-relation"), Some(marker + "-role"),
        Some(marker + "-scope"), Some(marker + "-outcome"), Some(marker + "-action"), Some(compensates),
        Some(marker + "-compensation"), Some(marker + "-compensation-failure"), true)
    }
    JobTaskPage(7, 11, 97, 83, rows)
  }

  def hostileTimeline(tasks: JobTaskPage, marker: String = secret): JobTimelinePage =
    JobTimelinePage(9, 13, 101, 89, tasks.tasks.zipWithIndex.map { case (task, index) =>
      JobTimelineEvent(index.toLong + 10L, instant.plusSeconds(index), marker + "-event",
        Some(task.taskId), task.parentTaskId, Some(marker + "-note"))
    })

  def view(jobId: JobId): JobExperienceView = {
    val summary = JobManagementSummary(jobId, JobStatus.Failed, JobPersistencePolicy.Ephemeral,
      JobDataOrigin.Runtime, instant, instant, None)
    val detail = JobManagementDetail(summary, JobManagementRetrySummary(JobRetryKind.None, 3, 5,
      None, false, true, false, false), JobResultSummary(JobStatus.Failed, false, Some(secret)), 2, 3)
    JobExperienceView(detail, JobExperienceStatus.Failed, JobExperienceVocabulary("Failed", "Contact operator"),
      JobExperienceProgress("terminal", 2), JobExperienceResult.Available(secret), Vector.empty, false)
  }

  def framed(line: String): Json = {
    val bytes = line.getBytes(UTF_8)
    val marker = "job.audit.v1 bytes="
    val start = line.indexOf(marker)
    if (start < 0) throw new IllegalArgumentException("missing audit frame")
    val tail = line.substring(start + marker.length)
    val separator = tail.indexOf(' ')
    val length = tail.take(separator).toInt
    val prefix = line.take(start + marker.length + separator + 1).getBytes(UTF_8).length
    val body = new String(bytes.slice(prefix, prefix + length), UTF_8)
    parser.parse(body).fold(error => throw error, identity)
  }
}
