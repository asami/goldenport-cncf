package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import java.time.{Clock, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext => ScalaExecutionContext}
import scala.jdk.CollectionConverters.*
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.scalatest.matchers.should.Matchers

/*
 * Genuine native publication, closed evidence and fresh SQLite ownership.
 * The numeric oracle belongs to the scenario; no payment helper computes it.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobDurableLifetimeRecoveryFixtures extends Matchers {
  val access = JobInputBoundsFixtures.access
  val instant = JobInputBoundsFixtures.instant
  val replay = DurableReplayEvidence(Some("idempotency"), Some("input"), Some("definition"),
    Some("authorization"), Some("provider"), Some("compatibility"))

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.show)
  }

  def record(jobId: JobId, count: Int = 1, rows: Int = 0): DurableJobRecord = {
    val supplied = JobInputBoundsFixtures.evidence(1L).projection
    val descriptors = Vector.tabulate(count)(index => supplied.tasks.head.copy(taskId = s"declaration-$index"))
    success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(jobId.value, 1L, instant, instant.plusSeconds(20)), supplied.authorization,
      DurableJobLifecycle(DurableJobLifecycleStatus.Submitted, 0, DurableRunMode.Async,
        supplied.retry, DurableScheduleState(Some(instant.plusSeconds(10)), None, None)),
      descriptors, Vector.empty, DurableResultOutcome.Pending,
      Vector.tabulate(rows)(index => DurableTimelineEvent(index.toLong + 1L, instant,
        "job.submitted", None, None)), Vector.empty, None, supplied.definitionSnapshot, supplied.retention)))
  }

  def publish(store: DurableJobStore, record: DurableJobRecord,
    policy: DurableJobLifetimeQuotaPolicy = DurableJobLifetimeQuotaPolicy.default,
    count: Int = 1)(using ctx: ExecutionContext): DurableJobStoreSnapshot = {
    val quota = store.lifetimeQuota(policy)
    val lease = success(quota.reserveRoot(record, access, count))
    val snapshot = success(store.create(record, access))
    success(quota.publishRoot(lease, access)).seed.paid shouldBe
      JobLifetimeSnapshot(2L * count, 32L + 64L * count, 1L, 1L)
    snapshot
  }

  def publishTerminal(store: DurableJobStore, terminal: DurableJobRecord,
    policy: DurableJobLifetimeQuotaPolicy = DurableJobLifetimeQuotaPolicy.default)
    (using ctx: ExecutionContext): DurableJobStoreSnapshot = {
    val normal = terminal.body.tasks.filterNot(_.relation.kind == DurableTaskRelationKind.Compensation)
    val body = terminal.body
    val root = success(DurableJobRecord.createV2(body.copy(
      identity = body.identity.copy(revision = 1L),
      lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Submitted,
        retry = body.lifecycle.retry.copy(attempts = Vector.empty, nextRetryAt = None,
          exhausted = false, recoveryRequired = false),
        schedule = body.lifecycle.schedule.copy(startedAt = None, completedAt = None)),
      tasks = normal.map(task => task.copy(transaction = task.transaction.copy(
        outcome = DurableTransactionOutcome.Pending), compensation = None)),
      result = DurableResultOutcome.Pending, timeline = Vector.empty)))
    val first = publish(store, root, policy, math.min(normal.size, 1024))
    val quota = store.lifetimeQuota(policy)
    if (normal.size > 1024) success(quota.reserveWork(body.identity.jobId, access, normal.size - 1024))
    normal.foreach(task => success(quota.associate(body.identity.jobId, access, task.taskId)) shouldBe true)
    val claims = normal.filter(_.compensation.nonEmpty).map(_.taskId).toSet ++
      body.tasks.filter(_.relation.kind == DurableTaskRelationKind.Compensation)
        .flatMap(_.compensation.map(_.compensatesTaskId))
    claims.foreach(taskid => success(quota.claimCompensation(body.identity.jobId, access, taskid)) shouldBe true)
    val closed = success(DurableJobRecord.createV2(body.copy(identity = body.identity.copy(
      revision = first.record.body.identity.revision + 1L))))
    success(store.checkpoint(first, closed, access))
  }

  def bind(engine: InMemoryJobEngine, store: DurableJobStore, original: DurableJobRecord,
    context: ExecutionContext, policy: DurableJobLifetimeQuotaPolicy = DurableJobLifetimeQuotaPolicy.default): Source = {
    val source = new Source(store, original, context)
    engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source, policy))
    source
  }

  final class Source(store: DurableJobStore, original: DurableJobRecord, context: ExecutionContext)
      extends (DurableJobLifecycleWriteRequest => Consequence[DurableJobLifecycleWriteEvidence]) {
    var requests = Vector.empty[DurableJobLifecycleWriteRequest]
    var revisions = Vector.empty[Long]
    var refuse = Option.empty[DurableJobLifecycleWriteBoundary]
    var terminalstatus: JobStatus = JobStatus.Succeeded
    var transform: DurableJobLifecycleWriteEvidence => DurableJobLifecycleWriteEvidence = identity
    var before: DurableJobLifecycleWriteRequest => Unit = _ => ()
    def apply(request: DurableJobLifecycleWriteRequest): Consequence[DurableJobLifecycleWriteEvidence] = {
      requests :+= request
      val prior = success(store.load(original.body.identity.jobId, access)(using context)).get.record
      val revision = Math.addExact(prior.body.identity.revision, 1L)
      revisions :+= revision
      before(request)
      if (refuse.contains(request.boundary)) Consequence.stateInvalid("closed recovery source refusal")
      else {
        val models = request match {
          case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
          case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
          case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
          case _ => Vector.empty
        }
        val terminal = request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
        val result = if (!terminal) DurableResultOutcome.Pending else terminalstatus match {
          case JobStatus.Cancelled => DurableResultOutcome.Cancelled(None)
          case JobStatus.Failed => DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "failed", false))
          case _ => DurableResultOutcome.Succeeded(DurableValue.Absent)
        }
        val body = original.body
        val retry = body.lifecycle.retry.copy(nextRetryAt = None)
        val closedretry = if (terminal && models.nonEmpty) retry.copy(attempts = retry.attempts :+
          DurableAttemptEvidence(
            number = body.lifecycle.retry.attempts.size + 1,
            startedAt = models.map(_.startedAt).reduce((left, right) => if (left.isBefore(right)) left else right),
            finishedAt = models.flatMap(_.finishedAt).reduceOption((left, right) => if (left.isAfter(right)) left else right),
            outcome = terminalstatus match {
              case JobStatus.Cancelled => DurableAttemptOutcome.Cancelled
              case JobStatus.Failed => DurableAttemptOutcome.Failed
              case _ => DurableAttemptOutcome.Succeeded
            },
            failure = None)) else retry
        Consequence.success(transform(DurableJobLifecycleWriteEvidence(DurableJobProjectionEvidence(
          revision, body.authorization, if (models.isEmpty) prior.body.tasks else models.map(descriptor),
          body.inputs, result, closedretry, body.diagnostics,
          body.calltreeReference, body.definitionSnapshot, body.retention), access)))
      }
    }
  }

  def descriptor(model: JobTaskReadModel): DurableTaskDescriptor = {
    val outcome = model.transactionOutcome.get match {
      case "running" => DurableTransactionOutcome.Pending
      case "committed" => DurableTransactionOutcome.Committed
      case "failed" => DurableTransactionOutcome.Failed
      case _ => DurableTransactionOutcome.Compensated
    }
    val compensation = model.compensatesTaskId.map(id => DurableCompensationDescriptor(
      DurableOperationReference(model.component.get, model.service, model.compensationActionRef.get, None),
      id.value, DurableCompensationStatus.parse(model.compensationStatus.get).toOption.get,
      model.compensationFailureSummary.map(value => DurableFailureSummary("compensation", "failed", value, false))))
    DurableTaskDescriptor(model.taskId.value, model.parentTaskId.map(_.value), DurableTaskKind.Operation,
      DurableTaskTarget(model.targetKind.get, DurableOperationReference(model.component.get,
        model.service, model.operation.get, None)),
      DurableTaskRelation(DurableTaskRelationKind.parse(model.relation.get).toOption.get,
        model.parentTaskId.map(_.value)),
      DurableTransactionDescriptor(DurableTransactionRole.parse(model.transactionRole.get).toOption.get,
        DurableTransactionScope.parse(model.transactionScope.get).toOption.get, outcome), compensation)
  }

  final class Task(var relationkind: String = "root") extends JobTask {
    val getters = new AtomicInteger
    val runs = new AtomicInteger
    val canonical = new AtomicInteger
    val compensations = new AtomicInteger
    var body: ExecutionContext => TaskOutcome = _ => TaskSucceeded(OperationResponse.Scalar("ok"))
    def actionId: ActionId = { getters.incrementAndGet(); ActionId("cncf", "recovery", Some(instant), Some("action")) }
    override def requestSummary: Option[String] = { getters.incrementAndGet(); Some("operation") }
    override def taskKind = "operation"
    override def targetKind = Some("operation")
    override def relation = Some(relationkind)
    override def transactionRole = Some("own")
    override def transactionScope = Some("per-task")
    override def componentName = Some("component-a")
    override def serviceName = Some("service-a")
    override def operationName = Some("run")
    override def compensationTask = { compensations.incrementAndGet(); None }
    def run(ctx: ExecutionContext): TaskOutcome = { runs.incrementAndGet(); body(ctx) }
    override def observeCanonicalOutcome(outcome: TaskOutcome, ctx: ExecutionContext, cancelled: Boolean): Unit = {
      canonical.incrementAndGet(); ()
    }
  }

  final class LifetimeTask(
    body: ExecutionContext => TaskOutcome = _ => TaskSucceeded(OperationResponse.Scalar("ok")),
    companion: Option[JobTask] = None,
    relationkind: String = "root"
  ) extends JobTask {
    val getters = new AtomicInteger(0)
    val runs = new AtomicInteger(0)
    val admissions = new AtomicInteger(0)
    val compensations = new AtomicInteger(0)
    def actionId: ActionId = {
      getters.incrementAndGet()
      ActionId("cncf", "lifetime", Some(JobInputBoundsFixtures.instant), Some("task"))
    }
    override def requestSummary: Option[String] = { getters.incrementAndGet(); None }
    override def requestParameters: Map[String, String] = { getters.incrementAndGet(); Map.empty }
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some(relationkind)
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def serviceName: Option[String] = Some("service-a")
    override def operationName: Option[String] = Some("run")
    override def compensationTask: Option[JobTask] = { compensations.incrementAndGet(); companion }
    override def compensationActionRef: Option[String] = companion.map(_ => "compensate")
    def run(ctx: ExecutionContext): TaskOutcome = { runs.incrementAndGet(); body(ctx) }
    override def observeAdmissionFailure(conclusion: Conclusion, ctx: ExecutionContext): Unit = {
      val _ = conclusion
      val _ = ctx
      admissions.incrementAndGet()
      ()
    }
  }

  def lifetimeFacts(fixture: JobLifetimeFixtures.Fixture, task: LifetimeTask)
      : (JobResourceSnapshot, Int, Int, Int, Int, Int, Int, Int, Int, Int) =
    (fixture.engine.resourceSnapshot, fixture.ids.calls.get(), fixture.clock.calls.get(),
      JobLifetimeFixtures.events(fixture).size, fixture.timer.calls.get(), fixture.timer.pendingCount,
      task.getters.get(), task.runs.get(), fixture.engine._work_queue.size(), task.admissions.get())

  def durableInputs(value: JobInput): Vector[DurableInputReference] =
    value.payloads.zipWithIndex.map { case (payload, index) =>
      DurableInputReference(payload.fieldName.getOrElse(s"input-$index"), DurableValue.Absent)
    }

  final class SourceCandidates(var entries: Vector[DurableJobStartupRecoveryCandidate]) extends DurableJobStartupRecoverySource {
    val calls = new AtomicInteger
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      calls.incrementAndGet(); Consequence.success(entries)
    }
  }
  def candidates(jobId: JobId): SourceCandidates =
    new SourceCandidates(Vector(DurableJobStartupRecoveryCandidate(0L, jobId.value, access, replay)))

  final class Port(var build: DurableJobStoreSnapshot => DurableJobRuntimeRehydration) extends DurableJobRuntimeRehydrationPort {
    val calls = new AtomicInteger
    var before: () => Unit = () => ()
    def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
      decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] = {
      calls.incrementAndGet(); before(); Consequence.success(build(snapshot))
    }
  }

  final class ObservedEntityStore extends StandardEntityStore {
    val loads = new AtomicInteger
    val quotaloads = new AtomicInteger
    val writes = new AtomicInteger
    val quotawrites = new AtomicInteger
    val jobacks = new AtomicInteger
    var losejoback = false
    var contexts = Vector.empty[ExecutionContext]
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[Option[T]] = {
      contexts :+= ctx
      super.load(id)
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[Option[EntityRevisionCarrier[T]]] = {
      loads.incrementAndGet()
      if (id.collection == DurableJobLifetimeQuotaEntity.Collection) quotaloads.incrementAndGet()
      contexts :+= ctx
      super.loadDetached(id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[EntityRevisionCarrier[T]] = {
      writes.incrementAndGet()
      if (tc.id(changes).collection == DurableJobLifetimeQuotaEntity.Collection) quotawrites.incrementAndGet()
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (result.isSuccess && tc.id(changes).collection == DurableJobStoreEntity.Collection) {
        jobacks.incrementAndGet()
        if (losejoback) { losejoback = false; throw new IllegalStateException("lost native acknowledgement") }
      }
      result
    }
  }

  final class Owner(val provider: SqlDataStore, val entity: ObservedEntityStore,
    val store: DurableJobStore, val context: ExecutionContext, val engine: InMemoryJobEngine,
    val clock: JobInputBoundsFixtures.CountingClock, val timer: JobInputBoundsFixtures.CountingTimer,
    val ids: JobInputBoundsFixtures.CountingIds, val policy: DurableJobLifetimeQuotaPolicy,
    val events: EventStore) {
    val quota = store.lifetimeQuota(policy)
    def signed(jobId: JobId): DurableJobStoreSnapshot = success(store.load(jobId.value, access)(using context)).get
    def native(jobId: JobId): DurableJobLifetimeQuotaSnapshot = success(quota.snapshot(jobId.value, access)(using context))
    def close(): Unit = try engine.shutdown() finally { success(provider.closeC()); () }
  }

  final class Database(val root: Path) {
    private var _owners = Vector.empty[Owner]
    def open(lifetime: JobLifetimePolicy = JobLifetimePolicy.default,
      resources: JobResourcePolicy = JobResourcePolicy.default): Owner = {
      val state = InMemoryJobEngine.State()
      success(state.bindLifetimePolicy(lifetime)); success(state.bindResourcePolicy(resources))
      val clock = new JobInputBoundsFixtures.CountingClock
      val timer = new JobInputBoundsFixtures.CountingTimer(clock)
      val ids = new JobInputBoundsFixtures.CountingIds
      val context = ExecutionContext.withIdGenerationContext(
        ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC)), ids)
      val provider = SqlDataStore.sqlite(root.resolve("jobs.db").toString)
      val entity = new ObservedEntityStore
      context.dataStoreSpace.useDataStore(provider)
      context.entityStoreSpace.addEntityStore(entity)
      installJobEntity(entity, context)
      val store = new DurableJobStore(entity)
      val engine = new InMemoryJobEngine(state, schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
        timeSource = clock, timer = Some(timer), retrySchedule = InMemoryJobEngine.RetrySchedule(
          InMemoryJobEngine.RetrySchedule.default.delayedRetryDelays.take(resources.maxretryattempts),
          math.min(3, resources.maxretryattempts)))(ScalaExecutionContext.global)
      val events = EventStore.inMemory
      engine.withEventStore(events)
      val owner = new Owner(provider, entity, store, context, engine, clock, timer, ids,
        DurableJobLifetimeQuotaPolicy.default.copy(lifetime = lifetime), events)
      _owners :+= owner
      owner
    }
    def closeBeforeReopen(owner: Owner): Unit = {
      owner.close(); _owners = _owners.filterNot(_ eq owner)
    }
    def close(): Unit = {
      try _owners.reverse.foreach(_.close()) finally {
        val stream = Files.walk(root)
        try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse
          .foreach(path => { Files.deleteIfExists(path); () }) finally stream.close()
      }
    }
  }
  def withDatabase(body: Database => Unit): Unit = {
    val database = new Database(Files.createTempDirectory("native-lifetime-recovery-"))
    try body(database) finally database.close()
  }

  def installJobEntity(entity: StandardEntityStore, context: ExecutionContext): Unit = {
    val declared = JobControlComponent.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
      .find(_.collectionId == JobEntityCollections.Job).get
    val binding = success(EntityRevisionBinding.resolve(
      EntityRevisionModelMetadata(declared.revisionModelKind.get, None), declared.revisionRepresentation))
    val persistent = JobEntity.entityPersistent
    val realm = new EntityRealm[JobEntity](declared.entityName,
      EntityLoader.fromEntityStore(entity)(using persistent, context),
      new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty)))
    val descriptor = EntityDescriptor[JobEntity](declared.collectionId,
      EntityRuntimePlan[JobEntity](entityName = declared.entityName,
        memoryPolicy = EntityMemoryPolicy.StoreOnly, workingSet = None,
        partitionStrategy = declared.partitionStrategy, maxPartitions = declared.maxPartitions,
        maxEntitiesPerPartition = declared.maxEntitiesPerPartition,
        concurrencyPolicy = declared.concurrencyPolicy.getOrElse(EntityConcurrencyPolicy.default)),
      persistent, binding)
    context.entitySpace.registerEntity(declared.entityName, new EntityCollection(descriptor, EntityStorage(realm)))
  }
}
