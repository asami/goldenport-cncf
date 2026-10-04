package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.blob.BlobRepository.given
import org.goldenport.cncf.context.{Capability, ExecutionContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * Actual managed runtime composition and genuine publication precede legacy V1.
 * Fault adapters retain the native decoder and current-binding SQL borrow.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobMigrationCheckpointFixtures {
  val access = JobDurableRuntimeFixtures.access
  val replay = JobDurableLifetimeRecoveryFixtures.replay
  val instant = JobDurableRuntimeFixtures.instant
  val lifetimeId = DurableJobIntegrityInspectionFixtures.lifetimeId
  val blobQuotaId = DurableJobIntegrityInspectionFixtures.blobQuotaId
  val storageId = DurableJobIntegrityInspectionFixtures.storageId

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def failure[A](result: Consequence[A]): Conclusion = JobDurableRuntimeFixtures.failureConclusion(result)
  def candidate(jobId: String, ordinal: Long = 0L,
    recordAccess: DurableJobRecordAccess = access): DurableJobStartupRecoveryCandidate =
    DurableJobStartupRecoveryCandidate(ordinal, jobId, recordAccess, replay)
  def await(latch: CountDownLatch): Unit = JobDurableRuntimeFixtures.await(latch)
  def join(thread: Thread): Unit = JobDurableRuntimeFixtures.join(thread)
  def actor[A](body: => A): (Thread, AtomicReference[Either[Throwable, A]]) = JobDurableRuntimeFixtures.actor(body)
  def withFixture[A](body: Fixture => A): A = {
    val path = JobDurableRuntimeFixtures.root()
    val fixture = new Fixture(path)
    try { success(fixture.start()); body(fixture) }
    finally { fixture.close(); JobDurableRuntimeFixtures.remove(path) }
  }
  def withPath[A](body: Path => A): A = {
    val path = JobDurableRuntimeFixtures.root()
    try body(path) finally JobDurableRuntimeFixtures.remove(path)
  }
  def seed(path: Path, labels: Vector[String], payload: Boolean = false): Vector[Root] = {
    val fixture = new Fixture(path)
    try {
      success(fixture.start())
      labels.map(label => fixture.legacy(fixture.publish(label,
        if (payload) Vector(Array[Byte](1, 2, 3)) else Vector.empty)))
    } finally fixture.close()
  }

  final case class Root(snapshot: DurableJobStoreSnapshot, references: Vector[DurableExternalReference]) {
    def jobId: String = snapshot.record.body.identity.jobId
  }
  final case class NativeState(rows: Vector[Option[Record]], metadata: Vector[Blob], payloads: Vector[Vector[Byte]])
  final case class Effects(creates: Int, writes: Int, deletes: Int, jobwrites: Int, lifewrites: Int,
    blobputs: Int, blobdeletes: Int, ports: Int, ids: Int, clocks: Int, timers: Int,
    resources: JobResourceSnapshot, queue: Int, getters: Int, bodies: Int, outcomes: Int, compensations: Int)

  final class NativeEntityStore extends StandardEntityStore {
    val loads = new AtomicInteger
    val creates = new AtomicInteger
    val writes = new AtomicInteger
    val deletes = new AtomicInteger
    val jobwrites = new AtomicInteger
    val jobacks = new AtomicInteger
    val lifewrites = new AtomicInteger
    var beforeLoad: EntityId => Unit = _ => ()
    var beforeJobWrite: () => Unit = () => ()
    var afterJobCommit: () => Unit = () => ()
    var loseJobAck = false
    override def create[T](entity: T, options: EntityCreateOptions)
        (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      creates.incrementAndGet(); super.create(entity, options)
    }
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      loads.incrementAndGet(); beforeLoad(id); super.load(id)
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[Option[EntityRevisionCarrier[T]]] = {
      loads.incrementAndGet(); beforeLoad(id); super.loadDetached(id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[EntityRevisionCarrier[T]] = {
      val collection = tc.id(changes).collection
      writes.incrementAndGet()
      if (collection == DurableJobLifetimeQuotaEntity.Collection) lifewrites.incrementAndGet()
      if (collection == DurableJobStoreEntity.Collection) { jobwrites.incrementAndGet(); beforeJobWrite() }
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (result.isSuccess && collection == DurableJobStoreEntity.Collection) {
        jobacks.incrementAndGet(); afterJobCommit()
        if (loseJobAck) { loseJobAck = false; throw new IllegalStateException("PRIVATE lost native acknowledgement") }
      }
      result
    }
    override def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = {
      deletes.incrementAndGet(); super.delete(id)
    }
    override def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = {
      deletes.incrementAndGet(); super.deleteHard(id)
    }
  }

  final class Fixture(val root: Path, capability: String = "job_admin") {
    val original = new JobDurableRuntimeFixtures.Fixture(root)
    val engine = original.engine
    val node = original.node
    val source = new DurableJobIntegrityInspectionFixtures.Source
    val entity = new NativeEntityStore
    val blob = new DurableJobIntegrityInspectionFixtures.ObservedBlobStore(original.blob)
    val context = ExecutionContext.withSecurityContext(original.context,
      original.context.security.copy(capabilities = if (capability.isEmpty) Set.empty else Set(Capability(capability))))
    val ordinary = ExecutionContext.withSecurityContext(context, context.security.copy(capabilities = Set.empty))
    var selectedContext = context
    var migration = Option.empty[Boolean]
    var sql = Option.empty[SqlDataStore]
    var port: DurableJobRuntimeRehydrationPort = original.port
    var inputBefore: () => Unit = () => ()
    var inputs = Option.empty[DurableJobRuntimeInputs]
    private var _tasks = Vector.empty[JobDurableLifetimeRecoveryFixtures.Task]
    val store = new DurableJobStore(entity, original.storagePolicy)
    val lifetime = store.lifetimeQuota(original.lifetimePolicy)
    val payloads = new DurableJobOwnedBlobPayloads(store, entity, blob, original.blobPolicy)
    val registered = new ConfiguredDurableJobRuntimeProvider(JobDurableRuntimeFixtures.providerId, selected => {
      original.builds.incrementAndGet(); inputBefore()
      selected.resolveManagedComponentDataStoreC(original.environment, original.request).map { datastore =>
        val native = datastore.asInstanceOf[SqlDataStore]
        val managed = JobDurableRuntimeFixtures.managedSql(selected, original.environment, original.request, root, native)
        sql = Some(managed)
        context.dataStoreSpace.useDataStore(managed)
        context.entityStoreSpace.addEntityStore(entity)
        JobDurableLifetimeRecoveryFixtures.installJobEntity(entity, context)
        val supplied = DurableJobRuntimeInputs(entity, blob, original.evidence, source, port, original.retention,
          selectedContext, original.storagePolicy, original.blobPolicy, original.lifetimePolicy)
        val value = migration.map(enabled => supplied.copy(migrateV1OnStartup = enabled)).getOrElse(supplied)
        inputs = Some(value)
        value
      }
    })
    def start(): Consequence[Unit] = {
      original.admit(); success(original.subsystem._register_durable_job_provider_c(registered))
      original.subsystem._initialize_durable_job_runtime_c()
    }
    def owner: DurableJobRuntimeOwner = original.owner
    def use[A](body: => A): A = success(original.subsystem._with_managed_datastore_lease_c(_ => Consequence.success(body)))
    def migrate(entries: Vector[DurableJobStartupRecoveryCandidate], limit: Int = 100,
      bytes: Long = 16777216L, operatorContext: Option[ExecutionContext] = None): Consequence[Unit] =
      original.subsystem._with_managed_datastore_lease_c { _ =>
        new DurableJobMigrationCheckpoint(store, payloads, original.lifetimePolicy,
          engine._resource_budget.policy, bytes).migrate(entries, limit)(using operatorContext.getOrElse(context))
      }
    def select(roots: Root*): Unit = source.entries = roots.toVector.zipWithIndex.map { case (value, index) =>
      candidate(value.jobId, index.toLong)
    }
    def publish(label: String, bytes: Vector[Array[Byte]] = Vector.empty): Root = use {
      given ExecutionContext = context
      val id = JobInputBoundsFixtures.id(label)
      val task = new JobDurableLifetimeRecoveryFixtures.Task
      _tasks :+= task; original.tasks.put(id, List(task))
      val supplied = JobDurableLifetimeRecoveryFixtures.record(id)
      val record = success(DurableJobRecord.createV2(supplied.body.copy(tasks = supplied.body.tasks.zipWithIndex.map {
        case (descriptor, index) => descriptor.copy(
          taskId = TaskId("cncf", "migration", Some(instant), Some(s"normal$index")).value,
          transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
            DurableTransactionScope.PerTask, DurableTransactionOutcome.Pending))
      })))
      val first = JobDurableLifetimeRecoveryFixtures.publish(store, record, original.lifetimePolicy)
      val refs = bytes.map(value => success(payloads.putObserved(first, access, None, ContentType.TEXT_PLAIN, Bag.binary(value))))
      Root(if (refs.isEmpty) first else checkpoint(first, first.record.body.copy(inputs = refs.zipWithIndex.map {
        case (ref, index) => DurableInputReference(s"payload-$index", DurableValue.External(ref))
      })), refs)
    }
    def terminal(root: Root): Root = use {
      root.snapshot.record.body.tasks.foreach(task => success(lifetime.associate(root.jobId, access, task.taskId)(using context)))
      val body = root.snapshot.record.body
      root.copy(snapshot = checkpoint(root.snapshot, body.copy(
        tasks = body.tasks.map(task => task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Committed))),
        lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
          schedule = body.lifecycle.schedule.copy(startedAt = Some(instant.plusSeconds(10)), completedAt = Some(instant.plusSeconds(20)))),
        result = DurableResultOutcome.Succeeded(DurableValue.Absent))))
    }
    def legacy(root: Root): Root = use {
      val closed = if (root.snapshot.record.body.lifecycle.status == DurableJobLifecycleStatus.Submitted) terminal(root) else root
      closed.copy(snapshot = checkpoint(closed.snapshot, closed.snapshot.record.body, legacy = true))
    }
    def unpaid(label: String): Root = use {
      val body = JobDurableLifetimeRecoveryFixtures.record(JobInputBoundsFixtures.id(label)).body
      val record = success(DurableJobRecord.create(body.copy(
        tasks = body.tasks.zipWithIndex.map { case (descriptor, index) => descriptor.copy(
          taskId = TaskId("cncf", "migration", Some(instant), Some(s"normal$index")).value,
          transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
            DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed))
        }, lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
          schedule = body.lifecycle.schedule.copy(startedAt = Some(instant.plusSeconds(10)), completedAt = Some(instant.plusSeconds(20)))),
        result = DurableResultOutcome.Succeeded(DurableValue.Absent))))
      Root(success(store.create(record, access)(using context)), Vector.empty)
    }
    def checkpoint(snapshot: DurableJobStoreSnapshot, body: DurableJobRecordBody,
      legacy: Boolean = false): DurableJobStoreSnapshot = use {
      val changed = body.copy(identity = body.identity.copy(revision = snapshot.record.body.identity.revision + 1L))
      val record = success(if (legacy) DurableJobRecord.create(changed) else DurableJobRecord.createV2(changed))
      success(store.checkpoint(snapshot, record, access)(using context))
    }
    def signed(jobId: String): DurableJobStoreSnapshot = use(success(store.load(jobId, access)(using context)).get)
    def jobEntityId(jobId: String): EntityId = success(DurableJobStoreEntity.entityId(jobId))
    def row(id: EntityId): Option[Record] = use {
      success(sql.get.load(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))(using context))
    }
    def replaceText(id: EntityId, text: String): Unit = use {
      success(sql.get.save(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id),
        row(id).get ++ Record.dataAuto("canonicalJson" -> text))(using context))
    }
    def removeRow(id: EntityId): Unit = use {
      success(sql.get.delete(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))(using context))
    }
    def metadata(ref: DurableExternalReference): Blob = use {
      success(entity.load[Blob](success(EntityId.parse(ref.reference)))(using summon[EntityPersistent[Blob]], context)).get
    }
    def replaceMetadata(ref: DurableExternalReference, change: Blob => Blob): Unit = use {
      val previous = metadata(ref)
      success(sql.get.save(DataStore.CollectionId.EntityStore(previous.id.collection), DataStore.EntryId(previous.id),
        row(previous.id).get ++ summon[EntityPersistent[Blob]].toStoreRecord(change(previous)))(using context))
    }
    def physicalPath(ref: DurableExternalReference): Path = {
      val storage = metadata(ref).storageRef.get
      root.resolve("blobs").resolve(storage.container).resolve(storage.key)
    }
    def state(roots: Root*): NativeState = {
      val refs = roots.toVector.flatMap(_.references).distinct
      NativeState((roots.toVector.map(value => jobEntityId(value.jobId)) ++ Vector(storageId, lifetimeId, blobQuotaId)).map(row),
        refs.map(metadata), refs.map(ref => Files.readAllBytes(physicalPath(ref)).toVector))
    }
    def effects: Effects = Effects(entity.creates.get(), entity.writes.get(), entity.deletes.get(), entity.jobwrites.get(),
      entity.lifewrites.get(), blob.puts.get(), blob.deletes.get(), original.port.calls.get(), original.ids.calls.get(),
      original.clock.calls.get(), original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.pendingTimerCount,
      engine.resourceSnapshot, engine._work_queue.size(), _tasks.map(_.getters.get()).sum, _tasks.map(_.runs.get()).sum,
      _tasks.map(_.canonical.get()).sum, _tasks.map(_.compensations.get()).sum)
    def mutateLifetime(change: DurableJobLifetimeQuotaRecord => DurableJobLifetimeQuotaRecord): Unit = {
      val text = row(lifetimeId).get.getString("canonicalJson").get
      replaceText(lifetimeId, success(DurableJobLifetimeQuotaCodec.encode(change(success(DurableJobLifetimeQuotaCodec.decode(text))))))
    }
    def mutateBlobQuota(change: DurableJobOwnedBlobQuotaRecord => DurableJobOwnedBlobQuotaRecord): Unit = {
      val text = row(blobQuotaId).get.getString("canonicalJson").get
      replaceText(blobQuotaId, success(DurableJobOwnedBlobQuotaCodec.encode(change(success(DurableJobOwnedBlobQuotaCodec.decode(text))))))
    }
    def close(): Unit = original.close()
  }
}
