package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * Actual native providers with measured delegates and acknowledgement fault sites.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationalMetricsFixtures {
  val instant: Instant = Instant.parse("2026-10-03T01:02:03Z")
  val now: Instant = instant.plusSeconds(60L)
  val access: DurableJobRecordAccess = DurableJobRecordAccess("tenant-a", "subject-a",
    Set("job.read", DurableJobRetentionPolicy.manageScope))
  val active: DurableRetentionState = DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
  val due: DurableRetentionState = active.copy(expiresAt = Some(instant.plusSeconds(30L)))
  val content: ContentType = ContentType.APPLICATION_OCTET_STREAM

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.show)
  }

  def message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => throw new IllegalStateException("expected a failed native result")
  }

  def signed(body: DurableJobRecordBody, v2: Boolean): DurableJobRecord =
    success(if (v2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))

  def record(jobId: String, v2: Boolean = false, retention: DurableRetentionState = active): DurableJobRecord = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", Some("action-a"))
    signed(DurableJobRecordBody(
      DurableJobIdentity(jobId, 1L, instant, instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(DurableJobLifecycleStatus.Succeeded, 7, DurableRunMode.Async,
        DurableRetryEvidence(Vector(DurableAttemptEvidence(1, instant, Some(instant), DurableAttemptOutcome.Succeeded, None)),
          3, None, false, false), DurableScheduleState(Some(instant), Some(instant), Some(instant))),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation, DurableTaskTarget("operation", operation),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask,
          DurableTransactionOutcome.Committed), None)),
      Vector.empty, DurableResultOutcome.Succeeded(DurableValue.Absent), Vector.empty,
      Vector(DurableDiagnosticSummary("execution", "ok", "info", "PRIVATE-PAYLOAD /PRIVATE/PATH")), None,
      DurableDefinitionSnapshot("definition", "daily", 1, 1L, "a" * 64, None, None, Map.empty), retention), v2)
  }

  def next(record: DurableJobRecord): DurableJobRecord =
    signed(record.body.copy(identity = record.body.identity.copy(revision = record.body.identity.revision + 1L,
      updatedAt = record.body.identity.updatedAt.plusSeconds(1L))), record.format == DurableRecordFormat.V2)

  def canonical(record: DurableJobRecord): String = success(DurableJobRecordCodec.canonicalJson(record))
  def bytes(record: DurableJobRecord): Long = canonical(record).getBytes(StandardCharsets.UTF_8).length.toLong
  def jobId(value: String): EntityId = success(DurableJobStoreEntity.entityId(value))
  def jobCollection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)

  def point(operation: String, outcome: String, count: Long, error: Boolean = false): RuntimeMetricPoint =
    RuntimeMetricPoint("job.durable.operation", "calls", Map("operation" -> operation, "outcome" -> outcome),
      count, errorCount = if (error) count else 0L)

  final case class Fixture(root: Path, provider: MeasuredDataStore, entity: MeasuredEntityStore,
    clock: MeasuredClock, ids: MeasuredIds, context: ExecutionContext, store: DurableJobStore,
    blob: MeasuredBlobStore, payloads: DurableJobOwnedBlobPayloads,
    retention: DurableJobRetentionMaintenance, cleanup: DurableJobPayloadCleanup, maintenance: DurableJobMaintenance) {
    private var _closed = false
    def close(): Unit = if (!_closed) {
      _closed = true
      success(provider.delegate.closeC())
    }
    def observations: (Vector[String], Vector[String], Int, Int, Vector[String]) =
      (provider.calls, entity.calls, clock.calls, ids.calls, blob.calls)
    def rawJob(jobId: String)(using ctx: ExecutionContext): Option[Record] =
      success(provider.load(jobCollection, DataStore.EntryId(DurableJobOperationalMetricsFixtures.jobId(jobId))))
    def rawQuota(blobQuota: Boolean = false)(using ctx: ExecutionContext): Option[Record] = {
      val id = if (blobQuota) success(DurableJobOwnedBlobQuotaEntity.entityId) else success(DurableJobStorageQuotaEntity.entityId)
      success(provider.load(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id)))
    }
    def seed(jobId: String, text: String)(using ctx: ExecutionContext): Unit = {
      val id = DurableJobOperationalMetricsFixtures.jobId(jobId)
      val row = DurableJobStoreEntity(id, jobId, text).toRecord() ++ Record.dataAuto("cncf_revision" -> 1L)
      success(provider.create(jobCollection, DataStore.EntryId(id), row))
    }
    def metadata(reference: DurableExternalReference)(using ctx: ExecutionContext): Blob = {
      import BlobRepository.given
      success(entity.load[Blob](success(EntityId.parse(reference.reference)))).get
    }
    def path(blob: Blob): Path = root.resolve("payloads").resolve(blob.storageRef.get.container).resolve(blob.storageRef.get.key)
    def physical(blob: Blob): Vector[Byte] = {
      val read = success(this.blob.delegate.get(blob.storageRef.get))
      val stream = read.payload.openInputStream()
      try stream.readAllBytes().toVector finally stream.close()
    }
    def put(snapshot: DurableJobStoreSnapshot, data: Array[Byte])(using ctx: ExecutionContext): Consequence[DurableExternalReference] =
      payloads.putObserved(snapshot, access, Some("PRIVATE-filename.bin"), content, Bag.binary(data))
    def attach(snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference,
      retentionState: DurableRetentionState = due)(using ctx: ExecutionContext): DurableJobStoreSnapshot = {
      val body = snapshot.record.body.copy(identity = snapshot.record.body.identity.copy(
        revision = snapshot.record.body.identity.revision + 1L, updatedAt = instant),
        inputs = Vector(DurableInputReference("PRIVATE-input", DurableValue.External(reference))), retention = retentionState)
      success(store.checkpoint(snapshot, signed(body, snapshot.record.format == DurableRecordFormat.V2), access))
    }
  }

  def withFixture(storagePolicy: DurableJobStorageResourcePolicy = DurableJobStorageResourcePolicy.default,
    payloadPolicy: DurableJobOwnedBlobResourcePolicy = DurableJobOwnedBlobResourcePolicy.default)(body: Fixture => Unit): Unit = {
    val parent = Path.of("target", "durable-job-operational-metrics", "work").toAbsolutePath
    Files.createDirectories(parent)
    val root = Files.createTempDirectory(parent, "native-")
    var fixture: Option[Fixture] = None
    try {
      val opened = _open(root, storagePolicy, payloadPolicy)
      fixture = Some(opened)
      body(opened)
    } finally {
      try fixture.foreach(_.close())
      finally {
        val stream = Files.walk(root)
        try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { val _ = Files.deleteIfExists(path) })
        finally stream.close()
      }
    }
  }

  private def _open(root: Path, storagepolicy: DurableJobStorageResourcePolicy,
    payloadpolicy: DurableJobOwnedBlobResourcePolicy): Fixture = {
    val provider = new MeasuredDataStore(SqlDataStore.sqlite(root.resolve("jobs.db").toString))
    try {
      val entity = new MeasuredEntityStore
      val clock = new MeasuredClock
      val ids = new MeasuredIds(IdGenerationContext.deterministic(IdGenerationContext.IdNamespace("cncf", "builtin"),
        clock, "phase697_durable_operation_metrics"))
      val context = ExecutionContext.withIdGenerationContext(ExecutionContext.test(), ids)
      context.dataStoreSpace.useDataStore(provider)
      context.entityStoreSpace.addEntityStore(entity)
      val store = new DurableJobStore(entity, storagepolicy)
      val blob = new MeasuredBlobStore(LocalBlobStore(root.resolve("payloads")))
      val payloads = new DurableJobOwnedBlobPayloads(store, entity, blob, payloadpolicy)
      val retention = new DurableJobRetentionMaintenance(store)
      val cleanup = new DurableJobPayloadCleanup(store, payloads)
      Fixture(root, provider, entity, clock, ids, context, store, blob, payloads, retention, cleanup,
        new DurableJobMaintenance(store, retention, cleanup))
    } catch {
      case error: Throwable =>
        val _ = provider.delegate.closeC()
        throw error
    }
  }

  final class MeasuredClock extends Clock {
    private var _calls = 0
    def calls: Int = _calls
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this
    def instant(): Instant = { _calls += 1; DurableJobOperationalMetricsFixtures.instant }
  }

  final class MeasuredIds(delegate: IdGenerationContext) extends IdGenerationContext {
    private var _calls = 0
    def calls: Int = _calls
    def namespace: IdGenerationContext.IdNamespace = delegate.namespace
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = { _calls += 1; delegate.entityId(collection, purpose) }
    def opaqueId(purpose: String): String = { _calls += 1; delegate.opaqueId(purpose) }
  }

  final class Source(entries: Vector[DurableJobRetentionCandidate], fail: Boolean = false) extends DurableJobRetentionCandidateSource {
    private var _calls = 0
    def calls: Int = _calls
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] = {
      _calls += 1
      if (fail) Consequence.stateInvalid("PRIVATE-source-failure") else Consequence.success(entries)
    }
  }

  final class MeasuredEntityStore extends StandardEntityStore {
    private var _calls = Vector.empty[String]
    var losecreateack = false
    var loseupdateack = false
    var losebloback = false
    var refuseupdate = false
    var loadsentinel: Option[Throwable] = None
    def calls: Vector[String] = _calls
    override def create[T](entity: T, options: EntityCreateOptions)
      (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      val collection = tc.collection(entity)
      _calls :+= s"create:${collection.name}"
      val result = super.create(entity, options)
      if (result.isSuccess && collection == DurableJobStoreEntity.Collection && losecreateack)
        throw new IllegalStateException("PRIVATE-lost-create-ack /PRIVATE/path")
      if (result.isSuccess && collection == BlobRepository.CollectionId && losebloback)
        throw new IllegalStateException("PRIVATE-lost-blob-ack /PRIVATE/path")
      result
    }
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      _calls :+= s"load:${id.collection.name}"
      super.load[T](id)
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] = {
      _calls :+= s"loadDetached:${id.collection.name}"
      if (id.collection == DurableJobStoreEntity.Collection) loadsentinel.foreach(throw _)
      super.loadDetached[T](id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val collection = tc.id(changes).collection
      _calls :+= s"updateDetached:${collection.name}"
      if (collection == DurableJobStoreEntity.Collection && refuseupdate)
        Consequence.stateInvalid("PRIVATE-refused-checkpoint")
      else {
        val result = super.updateDetached(changes, expectedRevision, executionPolicy)
        if (result.isSuccess && collection == DurableJobStoreEntity.Collection && loseupdateack)
          throw new IllegalStateException("PRIVATE-lost-checkpoint-ack /PRIVATE/path")
        result
      }
    }
  }

  final class MeasuredBlobStore(val delegate: BlobStore) extends BlobStore {
    private var _calls = Vector.empty[String]
    private var _issued = Vector.empty[BlobPutResult]
    var refusedelete = false
    def calls: Vector[String] = _calls
    def issued: Vector[BlobPutResult] = _issued
    def name: String = delegate.name
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      _calls :+= "put"
      delegate.put(request, payload).map { result => _issued :+= result; result }
    }
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = { _calls :+= "get"; delegate.get(ref) }
    def delete(ref: BlobStorageRef): Consequence[Unit] = {
      _calls :+= "delete"
      if (refusedelete) Consequence.stateInvalid("PRIVATE-delete-failure") else delegate.delete(ref)
    }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = delegate.accessUrl(ref)
    def status(): Consequence[BlobStoreStatus] = delegate.status()
  }

  final class MeasuredDataStore(val delegate: SqlDataStore)
    extends DataStore with SearchableDataStore with EntityVersionedMutationDataStore with EntityCreateDataStore {
    private var _calls = Vector.empty[String]
    def calls: Vector[String] = _calls
    def isAccept(cid: DataStore.CollectionId): Boolean = true
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = delegate.entityMutationProviderCapabilities
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      _calls :+= "createEntity"
      delegate.createEntity(plan)
    }
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _calls :+= "create"; delegate.create(collection, id, record)
    }
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] = {
      _calls :+= "load"; delegate.load(collection, id)
    }
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _calls :+= "save"; delegate.save(collection, id, record)
    }
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _calls :+= "update"; delegate.update(collection, id, changes)
    }
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _calls :+= "delete"; delegate.delete(collection, id)
    }
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] = {
      _calls :+= "search"; delegate.search(collection, directive)
    }
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      _calls :+= "mutateEntityDirect"; delegate.mutateEntityDirect(plan)
    }
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      _calls :+= "compareAndSetEntity"; delegate.compareAndSetEntity(plan)
    }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      _calls :+= "mutateVersionedEntity"; delegate.mutateVersionedEntity(plan)
    }
    def prepare(tx: TransactionContext): PrepareResult = record_prepare(CommitRecorder.noop)
    def commit(tx: TransactionContext): Unit = record_commit(CommitRecorder.noop)
    def abort(tx: TransactionContext): Unit = record_abort(CommitRecorder.noop)
  }
}
