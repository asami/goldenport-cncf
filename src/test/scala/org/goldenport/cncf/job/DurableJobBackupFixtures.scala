package org.goldenport.cncf.job

import java.io.{FilterInputStream, IOException, InputStream}
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.blob.BlobRepository.given
import org.goldenport.cncf.context.{Capability, ExecutionContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.{ManagedSqlDataStoreResource, SqlDataStore, SqlDataStoreIdentity}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * Actual managed native publication and fresh ownership, bounded target trees.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobBackupFixtures {
  val access: DurableJobRecordAccess = JobDurableRuntimeFixtures.access
  val instant = JobDurableRuntimeFixtures.instant
  val lifetimeId: EntityId = DurableJobIntegrityInspectionFixtures.lifetimeId
  val blobQuotaId: EntityId = DurableJobIntegrityInspectionFixtures.blobQuotaId
  val storageId: EntityId = DurableJobIntegrityInspectionFixtures.storageId
  private val _other_collection = EntityCollectionId("cncf", "backup", "unrelated")
  val otherId: EntityId = success(EntityId.bridgeFromParts(major = _other_collection.major,
    minor = _other_collection.minor, collection = _other_collection,
    timestamp = java.time.Instant.EPOCH, entropy = "unrelated_row"))

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def failure[A](result: Consequence[A]): Conclusion = JobDurableRuntimeFixtures.failureConclusion(result)
  def operator(capability: String = "job_admin"): ExecutionContext = DurableJobIntegrityInspectionFixtures.operator(capability)
  def await(latch: CountDownLatch): Unit = JobDurableRuntimeFixtures.await(latch)
  def join(thread: Thread): Unit = JobDurableRuntimeFixtures.join(thread)
  def actor[A](body: => A): (Thread, AtomicReference[Either[Throwable, A]]) = JobDurableRuntimeFixtures.actor(body)
  def withPath[A](body: Path => A): A = {
    val path = JobDurableRuntimeFixtures.root()
    try body(path) finally JobDurableRuntimeFixtures.remove(path)
  }
  def withFixture[A](body: Fixture => A): A = {
    val path = JobDurableRuntimeFixtures.root()
    val fixture = new Fixture(Files.createDirectory(path.resolve("native")))
    try { success(fixture.start()); body(fixture) }
    finally { fixture.close(); JobDurableRuntimeFixtures.remove(path) }
  }

  final case class Root(snapshot: DurableJobStoreSnapshot, references: Vector[DurableExternalReference]) {
    def jobId: String = snapshot.record.body.identity.jobId
  }
  final case class NativeState(rows: Vector[Option[Record]], snapshots: Vector[DurableJobStoreSnapshot],
    metadata: Vector[Blob], payloads: Vector[Vector[Byte]])
  final case class Effects(creates: Int, writes: Int, deletes: Int, lifewrites: Int, ports: Int, ids: Int,
    clocks: Int, timers: Int, resources: JobResourceSnapshot, queue: Int, getters: Int, bodies: Int)

  final class NativeEntityStore extends StandardEntityStore {
    val creates = new AtomicInteger
    val writes = new AtomicInteger
    val deletes = new AtomicInteger
    val lifeWrites = new AtomicInteger
    override def create[T](entity: T, options: EntityCreateOptions)
        (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      creates.incrementAndGet(); super.create(entity, options)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[EntityRevisionCarrier[T]] = {
      writes.incrementAndGet()
      if (tc.id(changes).collection == DurableJobLifetimeQuotaEntity.Collection) lifeWrites.incrementAndGet()
      super.updateDetached(changes, expectedRevision, executionPolicy)
    }
    override def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = {
      deletes.incrementAndGet(); super.delete(id)
    }
    override def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = {
      deletes.incrementAndGet(); super.deleteHard(id)
    }
  }

  final class Fixture(val root: Path, val node: SystemNode = SystemNode.create()) {
    val original = new JobDurableRuntimeFixtures.Fixture(root, node)
    val subsystem = original.subsystem
    val engine = original.engine
    val entity = new NativeEntityStore
    val blob = new LocalBlobStore(root.resolve("blobs"))
    val source = new DurableJobIntegrityInspectionFixtures.Source
    val context = ExecutionContext.withSecurityContext(original.context,
      original.context.security.copy(capabilities = Set(Capability("job_admin"))))
    val ordinary = ExecutionContext.withSecurityContext(context, context.security.copy(capabilities = Set.empty))
    var selectedBlob: BlobStore = blob
    var migrateV1 = false
    var lifetimePolicy = original.lifetimePolicy
    var sql = Option.empty[SqlDataStore]
    val store = new DurableJobStore(entity, original.storagePolicy)
    val lifetime = store.lifetimeQuota(original.lifetimePolicy)
    val payloads = new DurableJobOwnedBlobPayloads(store, entity, blob, original.blobPolicy)
    private var _tasks = Vector.empty[JobDurableLifetimeRecoveryFixtures.Task]
    val registered = new ConfiguredDurableJobRuntimeProvider(JobDurableRuntimeFixtures.providerId, selected => {
      original.builds.incrementAndGet()
      selected.resolveManagedComponentDataStoreC(original.environment, original.request).map { datastore =>
        val native = datastore.asInstanceOf[SqlDataStore]
        val managed = JobDurableRuntimeFixtures.managedSql(selected, original.environment, original.request, root, native)
        sql = Some(managed)
        context.dataStoreSpace.useDataStore(managed)
        context.entityStoreSpace.addEntityStore(entity)
        JobDurableLifetimeRecoveryFixtures.installJobEntity(entity, context)
        DurableJobRuntimeInputs(entity, selectedBlob, original.evidence, source, original.port, original.retention,
          context, original.storagePolicy, original.blobPolicy, lifetimePolicy, migrateV1)
      }
    })
    def start(): Consequence[Unit] = {
      original.admit(); success(subsystem._register_durable_job_provider_c(registered))
      subsystem._initialize_durable_job_runtime_c()
    }
    def owner: DurableJobRuntimeOwner = original.owner
    def use[A](body: => A): A = success(subsystem._with_managed_datastore_lease_c(_ => Consequence.success(body)))
    def prepare(policy: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy.default): DurableJobBackupSource =
      success(DurableJobBackupOperations.prepare(owner, policy)(using context))
    def select(roots: Root*): Unit = source.entries = roots.toVector.zipWithIndex.map { case (root, index) =>
      DurableJobStartupRecoveryCandidate(index.toLong, root.jobId, access, JobDurableLifetimeRecoveryFixtures.replay)
    }
    def publish(label: String, bytes: Vector[Array[Byte]] = Vector.empty): Root = use {
      given ExecutionContext = context
      val id = JobInputBoundsFixtures.id(label)
      val task = new JobDurableLifetimeRecoveryFixtures.Task
      _tasks :+= task; original.tasks.put(id, List(task))
      val supplied = JobDurableLifetimeRecoveryFixtures.record(id)
      val record = success(DurableJobRecord.createV2(supplied.body.copy(tasks = supplied.body.tasks.zipWithIndex.map {
        case (descriptor, index) => descriptor.copy(
          taskId = TaskId("cncf", "backup", Some(instant), Some(s"normal$index")).value,
          transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
            DurableTransactionScope.PerTask, DurableTransactionOutcome.Pending))
      })))
      val first = JobDurableLifetimeRecoveryFixtures.publish(store, record, original.lifetimePolicy)
      val refs = bytes.map(value => success(payloads.putObserved(first, access, None, ContentType.TEXT_PLAIN, Bag.binary(value))))
      val snapshot = if (refs.isEmpty) first else checkpoint(first, first.record.body.copy(inputs = refs.zipWithIndex.map {
        case (reference, index) => DurableInputReference(s"payload-$index", DurableValue.External(reference))
      }))
      Root(snapshot, refs)
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
    def legacy(root: Root): Root = root.copy(snapshot = checkpoint(root.snapshot, root.snapshot.record.body, legacy = true))
    def checkpoint(snapshot: DurableJobStoreSnapshot, body: DurableJobRecordBody, legacy: Boolean = false): DurableJobStoreSnapshot = use {
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
    def unrelated(): Unit = use {
      success(sql.get.save(DataStore.CollectionId.EntityStore(otherId.collection), DataStore.EntryId(otherId),
        Record.dataAuto("retained" -> "unrelated native data"))(using context))
    }
    def metadata(reference: DurableExternalReference): Blob = use {
      success(entity.load[Blob](success(EntityId.parse(reference.reference)))(using summon[EntityPersistent[Blob]], context)).get
    }
    def physicalPath(reference: DurableExternalReference): Path = {
      val storage = metadata(reference).storageRef.get
      root.resolve("blobs").resolve(storage.container).resolve(storage.key)
    }
    def state(roots: Root*): NativeState = {
      val refs = roots.toVector.flatMap(_.references).distinct
      NativeState((roots.toVector.map(root => jobEntityId(root.jobId)) ++ Vector(storageId, lifetimeId, blobQuotaId)).map(row),
        roots.toVector.map(root => signed(root.jobId)), refs.map(metadata), refs.map(reference => Files.readAllBytes(physicalPath(reference)).toVector))
    }
    def effects: Effects = Effects(entity.creates.get(), entity.writes.get(), entity.deletes.get(), entity.lifeWrites.get(),
      original.port.calls.get(), original.ids.calls.get(), original.clock.calls.get(),
      subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.pendingTimerCount,
      engine.resourceSnapshot, engine._work_queue.size(), _tasks.map(_.getters.get()).sum, _tasks.map(_.runs.get()).sum)
    def stop(): Consequence[Unit] = subsystem.shutdownC().flatMap(_ => node.shutdownC())
    def close(): Unit = {
      // Replay actual results. Only an intentionally failed extra native resource
      // may make node completion fail; the genuine SQL pool must close successfully.
      val subsystemresult = subsystem.shutdownC()
      val noderesult = node.shutdownC()
      sql.foreach(value => success(value.managedResourceOption.get.closeC()))
      if (subsystemresult.isSuccess && noderesult.isSuccess) ()
      else if (sql.exists(_.managedResourceOption.get.isOpen)) throw new IllegalStateException("native fixture pool still open")
    }
  }

  final class CopyReader(val mode: String = "normal", before: () => Unit = () => ()) extends DurableJobBackupCopyReader {
    val opens = new AtomicInteger
    val closes = new AtomicInteger
    private val _reads = new AtomicInteger
    def open(path: Path): InputStream = {
      opens.incrementAndGet()
      val input = DurableJobBackupCopyReader.native.open(path)
      new FilterInputStream(input) {
        override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
          val count = _reads.incrementAndGet()
          if (count == 1) before()
          if (count > 1) mode match {
            case "throw" => throw new IOException("PRIVATE path credentials payload")
            case "interrupt" => throw new InterruptedException("PRIVATE path credentials payload")
            case "zero" => return 0
            case "short" => return -1
            case _ => ()
          }
          in.read(buffer, offset, length)
        }
        override def read(): Int = if (mode == "growth") 1 else in.read()
        override def close(): Unit = try in.close() finally { closes.incrementAndGet(); () }
      }
    }
  }

  def physical(root: Path): Map[String, Vector[Byte]] = {
    var result = Map.empty[String, Vector[Byte]]
    var entries = 0
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def visitFile(path: Path, attributes: BasicFileAttributes): FileVisitResult = {
        entries += 1
        require(entries <= 16384 && attributes.isRegularFile && attributes.size() <= 1048576L)
        result += root.relativize(path).toString -> Files.readAllBytes(path).toVector
        FileVisitResult.CONTINUE
      }
    })
    result
  }
  def dataFacts(database: Path, blobs: Path): Map[String, Vector[Byte]] =
    Map("jobs.db" -> Files.readAllBytes(database).toVector) ++
      (if (Files.exists(blobs)) physical(blobs).map { case (path, bytes) => s"blobs/$path" -> bytes } else Map.empty)
  def marker(path: Path): Path = path.resolve("backup.properties")
  def backup(fixture: Fixture, destination: Path,
    policy: DurableJobBackupResourcePolicy = DurableJobBackupResourcePolicy.default): DurableJobBackupSource = {
    val source = fixture.prepare(policy)
    success(fixture.stop())
    success(DurableJobBackupOperations.backup(source, destination)(using fixture.context))
    source
  }
  def restore(artifact: Path, destination: Path)(using ctx: ExecutionContext): DurableJobRestoreResult =
    success(DurableJobBackupOperations.restore(artifact, destination, "local", BlobStorageRef.DefaultContainer))

  def failedClose(node: SystemNode, path: Path): Unit = {
    val binding = node.bind()
    val lease = success(binding.acquireLeaseC())
    try {
      val identity = success(SqlDataStoreIdentity.sqliteC(path.resolve("failed-extra.db").toString,
        SqlDataStoreIdentity.Credential.reference("fixture", "v1"), node.hmacKey))
      success(binding.resolveC(lease, "failed-extra", identity)(Consequence.success(
        ManagedSqlDataStoreResource.hikari(null, () => throw new IOException("PRIVATE physical close")))))
    } finally { lease.release(); binding.release() }
  }
  def boundedDrainNode(): SystemNode = {
    val nanos = new AtomicLong
    val runtime = new SystemNode.DrainRuntime {
      def nanoTime(): Long = nanos.get()
      def await(monitor: Object, millis: Long): Unit = { nanos.addAndGet(2000000L); () }
    }
    SystemNode.withHmacKey(SqlDataStoreIdentity.HmacKey(Array.fill[Byte](32)(19)), () => (), 1L, runtime)
  }
}
