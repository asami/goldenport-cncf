package org.goldenport.cncf.job

import java.io.{FilterInputStream, InputStream}
import java.nio.file.{Files, Path}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.blob.BlobRepository.given
import org.goldenport.cncf.context.{Capability, ExecutionContext, SecurityContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * Composition over the original actual managed native and LocalBlob fixtures.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobIntegrityInspectionFixtures {
  val access = JobDurableRuntimeFixtures.access
  val replay = JobDurableLifetimeRecoveryFixtures.replay
  val instant = JobDurableRuntimeFixtures.instant
  val lifetimeId = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaEntity.entityId)
  val blobQuotaId = JobDurableRuntimeFixtures.success(DurableJobOwnedBlobQuotaEntity.entityId)
  val storageId = JobDurableRuntimeFixtures.success(DurableJobStorageQuotaEntity.entityId)

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def failure[A](result: Consequence[A]): Conclusion = JobDurableRuntimeFixtures.failureConclusion(result)
  def operator(capability: String = "job_admin"): ExecutionContext = {
    val base = ExecutionContext.create(SecurityContext.Privilege.User)
    ExecutionContext.withSecurityContext(base, base.security.copy(capabilities = Set(Capability(capability))))
  }
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

  final class Source extends DurableJobStartupRecoverySource {
    var entries = Vector.empty[DurableJobStartupRecoveryCandidate]
    var before: () => Unit = () => ()
    var result = Option.empty[Consequence[Vector[DurableJobStartupRecoveryCandidate]]]
    val calls = new AtomicInteger
    var limits = Vector.empty[Int]
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
      calls.incrementAndGet(); limits :+= maxCandidates; before()
      result.getOrElse(Consequence.success(entries))
    }
  }

  final class ObservedEntityStore extends StandardEntityStore {
    val creates = new AtomicInteger
    val cas = new AtomicInteger
    val deletes = new AtomicInteger
    val loads = new AtomicInteger
    val lifetimeloads = new AtomicInteger
    val blobloads = new AtomicInteger
    var before: EntityId => Unit = _ => ()
    override def create[T](entity: T, options: EntityCreateOptions)
        (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      creates.incrementAndGet(); super.create(entity, options)
    }
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      loads.incrementAndGet(); before(id)
      if (id.collection == BlobRepository.CollectionId) blobloads.incrementAndGet()
      super.load(id)
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[Option[EntityRevisionCarrier[T]]] = {
      loads.incrementAndGet(); before(id)
      if (id.collection == DurableJobLifetimeQuotaEntity.Collection) lifetimeloads.incrementAndGet()
      super.loadDetached(id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[EntityRevisionCarrier[T]] = {
      cas.incrementAndGet(); super.updateDetached(changes, expectedRevision, executionPolicy)
    }
    override def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = {
      deletes.incrementAndGet(); super.delete(id)
    }
    override def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = {
      deletes.incrementAndGet(); super.deleteHard(id)
    }
  }

  final class ObservedBlobStore(val delegate: LocalBlobStore) extends BlobStore {
    val gets = new AtomicInteger
    val puts = new AtomicInteger
    val deletes = new AtomicInteger
    val opens = new AtomicInteger
    val closes = new AtomicInteger
    val bytes = new AtomicLong
    var refs = Vector.empty[BlobStorageRef]
    var mode = "normal"
    var before: () => Unit = () => ()
    var streamBefore: () => Unit = () => ()
    var closeBefore: () => Unit = () => ()
    def name: String = delegate.name
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      puts.incrementAndGet(); delegate.put(request, payload)
    }
    def delete(ref: BlobStorageRef): Consequence[Unit] = {
      deletes.incrementAndGet(); delegate.delete(ref)
    }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = delegate.accessUrl(ref)
    def status(): Consequence[BlobStoreStatus] = delegate.status()
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = {
      gets.incrementAndGet(); refs :+= ref; before()
      if (mode == "provider-throw") throw new IllegalStateException("PRIVATE provider secret")
      else if (mode == "provider-refuse") Consequence.stateInvalid("PRIVATE provider secret")
      else if (mode == "provider-interrupt") throw new InterruptedException("PRIVATE provider secret")
      else delegate.get(ref).map { original =>
        val payload = new BinaryBag {
          val bag: Bag = original.payload.bag
          override def openInputStream(): InputStream = {
            opens.incrementAndGet()
            val stream = original.payload.openInputStream()
            new FilterInputStream(stream) {
              private var _zero = mode == "zero"
              private var _entered = false
              private def _enter(): Unit = {
                if (!_entered) { _entered = true; streamBefore() }
                if (mode == "stream-throw") throw new IllegalStateException("PRIVATE stream secret")
                if (mode == "stream-interrupt") throw new InterruptedException("PRIVATE stream secret")
              }
              override def read(): Int = {
                _enter(); val value = in.read()
                if (value >= 0) bytes.incrementAndGet()
                value
              }
              override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
                _enter()
                if (_zero) { _zero = false; 0 }
                else {
                  val count = in.read(buffer, offset, length)
                  if (count > 0) bytes.addAndGet(count.toLong)
                  count
                }
              }
              override def close(): Unit =
                try { closeBefore(); if (mode == "close-throw") throw new IllegalStateException("PRIVATE close secret") }
                finally { closes.incrementAndGet(); in.close() }
            }
          }
        }
        mode match {
          case "null-result" => null
          case "null-payload" => original.copy(payload = null)
          case "wrong-id" => original.copy(id = accessId("wrong_blob"), payload = payload)
          case "wrong-ref" => original.copy(storageRef = ref.copy(key = "wrong"), payload = payload)
          case "wrong-content" => original.copy(contentType = ContentType.APPLICATION_JSON, payload = payload)
          case "wrong-size" => original.copy(byteSize = original.byteSize + 1L, payload = payload)
          case "wrong-digest" => original.copy(digest = "0" * 64, payload = payload)
          case "uppercase-digest" => original.copy(digest = original.digest.toUpperCase(java.util.Locale.ROOT), payload = payload)
          case _ => original.copy(payload = payload)
        }
      }
    }
    private def _blob_id(label: String): EntityId = success(EntityId.bridgeFromParts(
      major = "cncf", minor = "builtin", collection = BlobRepository.CollectionId,
      timestamp = instant, entropy = label))
    def accessId(label: String): EntityId = _blob_id(label)
  }

  final case class Root(snapshot: DurableJobStoreSnapshot, references: Vector[DurableExternalReference]) {
    def jobId: String = snapshot.record.body.identity.jobId
  }
  final case class NativeState(rows: Vector[Option[Record]], metadata: Vector[Blob], payloads: Vector[Vector[Byte]])
  final case class Effects(creates: Int, cas: Int, deletes: Int, blobputs: Int, blobdeletes: Int,
    ports: Int, ids: Int, clocks: Int, timers: Int, resources: JobResourceSnapshot, queue: Int,
    taskgetters: Int, taskruns: Int, taskoutcomes: Int, compensations: Int)

  final class Fixture(val root: Path) {
    val original = new JobDurableRuntimeFixtures.Fixture(root)
    val source = new Source
    val entity = new ObservedEntityStore
    val blob = new ObservedBlobStore(original.blob)
    var sql = Option.empty[SqlDataStore]
    var blobPolicy = DurableJobOwnedBlobResourcePolicy.default
    val context = original.context
    val engine = original.engine
    val node = original.node
    private var _tasks = Vector.empty[JobDurableLifetimeRecoveryFixtures.Task]
    val registered = new ConfiguredDurableJobRuntimeProvider(JobDurableRuntimeFixtures.providerId, selected => {
      selected.resolveManagedComponentDataStoreC(original.environment, original.request).map { datastore =>
        val native = datastore.asInstanceOf[SqlDataStore]
        val managed = JobDurableRuntimeFixtures.managedSql(selected,
          original.environment, original.request, root, native)
        sql = Some(managed)
        context.dataStoreSpace.useDataStore(managed)
        context.entityStoreSpace.addEntityStore(entity)
        JobDurableLifetimeRecoveryFixtures.installJobEntity(entity, context)
        DurableJobRuntimeInputs(entity, blob, original.evidence, source, original.port, original.retention,
          context, original.storagePolicy, blobPolicy, original.lifetimePolicy)
      }
    })
    def start(): Consequence[Unit] = {
      original.admit(); success(original.subsystem._register_durable_job_provider_c(registered))
      original.subsystem._initialize_durable_job_runtime_c()
    }
    def owner: DurableJobRuntimeOwner = engine._durable_runtime_owner_option.get
    def use[A](body: => A): A = success(owner._execute_c(Consequence.success(body)))
    def inspect(maxCandidates: Int = 100, maxPhysicalBytes: Long = 16777216L): Consequence[JobDurableIntegrityReport] =
      owner._execute_c(new DurableJobIntegrityInspection(source, owner._store, owner._payloads,
        owner._lifetime, maxPhysicalBytes).inspect(maxCandidates)(using context))
    def publish(label: String, payloads: Vector[Array[Byte]] = Vector.empty,
      terminalTasks: Boolean = false): Root = use {
      given ExecutionContext = context
      val id = JobInputBoundsFixtures.id(label)
      val task = new JobDurableLifetimeRecoveryFixtures.Task
      _tasks :+= task
      original.tasks.put(id, List(task))
      val supplied = JobDurableLifetimeRecoveryFixtures.record(id)
      val record = if (!terminalTasks) supplied else success(DurableJobRecord.createV2(supplied.body.copy(
        tasks = supplied.body.tasks.zipWithIndex.map { case (descriptor, index) =>
          descriptor.copy(taskId = TaskId("cncf", "integrity", Some(instant), Some(s"terminal$index")).value,
            transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
              DurableTransactionScope.PerTask, DurableTransactionOutcome.Pending))
        })))
      val first = JobDurableLifetimeRecoveryFixtures.publish(owner._store, record, original.lifetimePolicy)
      val refs = payloads.map(bytes => success(owner._payloads.putObserved(first, access, None,
        ContentType.TEXT_PLAIN, Bag.binary(bytes))))
      val snapshot = if (refs.isEmpty) first else attach(first, refs)
      Root(snapshot, refs)
    }
    def select(roots: Root*): Unit = source.entries = roots.toVector.zipWithIndex.map { case (value, index) =>
      candidate(value.jobId, index.toLong)
    }
    def attach(snapshot: DurableJobStoreSnapshot, references: Vector[DurableExternalReference]): DurableJobStoreSnapshot =
      checkpoint(snapshot, snapshot.record.body.copy(inputs = references.zipWithIndex.map { case (ref, index) =>
        DurableInputReference(s"payload-$index", DurableValue.External(ref))
      }))
    def checkpoint(snapshot: DurableJobStoreSnapshot, body: DurableJobRecordBody): DurableJobStoreSnapshot = use {
      val record = success(DurableJobRecord.createV2(body.copy(identity = body.identity.copy(
        revision = snapshot.record.body.identity.revision + 1L))))
      success(owner._store.checkpoint(snapshot, record, access)(using context))
    }
    def terminal(root: Root): Root = use {
      root.snapshot.record.body.tasks.foreach(task => success(owner._lifetime.associate(root.jobId, access,
        task.taskId)(using context)))
      val body = root.snapshot.record.body
      root.copy(snapshot = checkpoint(root.snapshot, body.copy(
        tasks = body.tasks.map(descriptor =>
          if (descriptor.transaction.role == DurableTransactionRole.None) descriptor
          else descriptor.copy(transaction = descriptor.transaction.copy(outcome = DurableTransactionOutcome.Committed))),
        lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
          schedule = body.lifecycle.schedule.copy(startedAt = Some(instant.plusSeconds(10)), completedAt = Some(instant.plusSeconds(20)))),
        result = DurableResultOutcome.Succeeded(DurableValue.Absent))))
    }
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
    def blobMetadata(ref: DurableExternalReference): Blob = use {
      success(entity.load[Blob](success(EntityId.parse(ref.reference)))(using summon[EntityPersistent[Blob]], context)).get
    }
    def replaceMetadata(ref: DurableExternalReference, change: Blob => Blob): Unit = use {
      val current = blobMetadata(ref)
      val changed = change(current)
      success(sql.get.save(DataStore.CollectionId.EntityStore(current.id.collection), DataStore.EntryId(current.id),
        row(current.id).get ++ summon[EntityPersistent[Blob]].toStoreRecord(changed))(using context))
    }
    def physicalPath(ref: DurableExternalReference): Path = {
      val stored = blobMetadata(ref).storageRef.get
      root.resolve("blobs").resolve(stored.container).resolve(stored.key)
    }
    def state(roots: Root*): NativeState = {
      val refs = roots.toVector.flatMap(_.references).distinct
      NativeState((roots.toVector.map(value => jobEntityId(value.jobId)) ++ Vector(storageId, lifetimeId, blobQuotaId)).map(row),
        refs.map(blobMetadata), refs.map(ref => Files.readAllBytes(physicalPath(ref)).toVector))
    }
    def effects: Effects = Effects(entity.creates.get(), entity.cas.get(), entity.deletes.get(),
      blob.puts.get(), blob.deletes.get(), original.port.calls.get(), original.ids.calls.get(), original.clock.calls.get(),
      original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.pendingTimerCount,
      engine.resourceSnapshot, engine._work_queue.size(), _tasks.map(_.getters.get()).sum,
      _tasks.map(_.runs.get()).sum, _tasks.map(_.canonical.get()).sum, _tasks.map(_.compensations.get()).sum)
    def lifetimeText: String = row(lifetimeId).get.getString("canonicalJson").get
    def blobQuotaText: String = row(blobQuotaId).get.getString("canonicalJson").get
    def mutateLifetime(change: DurableJobLifetimeQuotaRecord => DurableJobLifetimeQuotaRecord): Unit =
      replaceText(lifetimeId, success(DurableJobLifetimeQuotaCodec.encode(change(success(DurableJobLifetimeQuotaCodec.decode(lifetimeText))))))
    def mutateBlobQuota(change: DurableJobOwnedBlobQuotaRecord => DurableJobOwnedBlobQuotaRecord): Unit =
      replaceText(blobQuotaId, success(DurableJobOwnedBlobQuotaCodec.encode(change(success(DurableJobOwnedBlobQuotaCodec.decode(blobQuotaText))))))
    def close(): Unit = original.close()
  }
}
