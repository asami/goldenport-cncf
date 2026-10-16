package org.goldenport.cncf.job

import java.io.{FilterInputStream, IOException, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.{
  Blob, BlobAccessUrl, BlobPutRequest, BlobPutResult, BlobReadResult, BlobRepository,
  BlobStorageRef, BlobStore, BlobStoreStatus
}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{
  DataStore, EntityCompareAndSetMutationPlan, EntityDirectMutationPlan,
  EntityMutationProviderCapabilities, EntityMutationProviderResult, QueryDirective,
  EntityVersionedMutationPlan, EntityVersionedMutationResult, SearchResult
}
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.entity.{EntityPersistent, StandardEntityStore}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Field, Record}
import org.simplemodeling.model.datatype.EntityId

/*
 * Measured native managed adapters; faults are explicit fixture actions only.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponsePayloadFixtures {
  import BlobRepository.given

  val access: DurableJobRecordAccess = DurableJobBackupFixtures.access
  val prefix = "{\"format\":{\"schemaId\":\"cncf.job-operation-response\",\"version\":1},\"response\":"
  val reviewFields = Vector("reviewId" -> "review-1", "reportId" -> "report-1", "reportDigest" -> "sha256:abc",
    "reportDocument" -> "full report", "runDocument" -> "full run", "attestationDocument" -> "full attestation")
  val review: OperationResponse = OperationResponse.RecordResponse(Record(reviewFields.map {
    case (key, value) => Field(key, Field.Value.Single(value))
  }))
  val reviewWire = """{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":{"kind":"record","fields":[{"key":"reviewId","value":{"type":"string","value":"review-1"}},{"key":"reportId","value":{"type":"string","value":"report-1"}},{"key":"reportDigest","value":{"type":"string","value":"sha256:abc"}},{"key":"reportDocument","value":{"type":"string","value":"full report"}},{"key":"runDocument","value":{"type":"string","value":"full run"}},{"key":"attestationDocument","value":{"type":"string","value":"full attestation"}}]}}"""

  def success[A](result: Consequence[A]): A = DurableJobBackupFixtures.success(result)
  def bytes(text: String): Array[Byte] = text.getBytes(StandardCharsets.UTF_8)
  def sha256(value: Array[Byte]): String = MessageDigest.getInstance("SHA-256").digest(value)
    .map(byte => f"${byte & 0xff}%02x").mkString
  def id(value: String): EntityId = success(EntityId.parse(value))
  def withFixture[A](body: Fixture => A): A = DurableJobBackupFixtures.withPath { path =>
    val fixture = new Fixture(Files.createDirectory(path.resolve("native")))
    try { fixture.start(); body(fixture) } finally fixture.close()
  }

  final case class ReadEffects(metadata: Int, gets: Int, opens: Int, reads: Int, fallbacks: Int,
    closes: Int, consumed: Long, maxrequest: Int, writes: Int, searches: Int, puts: Int,
    deletes: Int, urls: Int, statuses: Int)

  final case class NativeFacts(rows: Vector[Option[Record]], metadata: Blob, physical: Vector[Byte],
    runtime: DurableJobBackupFixtures.Effects)

  final class MeasuredEntityStore extends StandardEntityStore {
    val metadata = new AtomicInteger
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      if (id.collection == BlobRepository.CollectionId) metadata.incrementAndGet()
      super.load[T](id)
    }
  }

  // The wrapper forwards every exercised native method to the original managed
  // view. Its delegate keeps the genuine lease and datasource ownership.
  final class MeasuredSqlDataStore(delegate: SqlDataStore) extends SqlDataStore(SqliteDialectDriver, null) {
    val writes = new AtomicInteger
    val searches = new AtomicInteger
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      delegate.entityMutationProviderCapabilities
    override def isAccept(cid: DataStore.CollectionId): Boolean = delegate.isAccept(cid)
    override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)
        (using ctx: ExecutionContext): Consequence[Option[Record]] = delegate.load(collection, id)
    override def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)
        (using ctx: ExecutionContext): Consequence[Unit] = {
      writes.incrementAndGet(); delegate.create(collection, id, record)
    }
    override def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)
        (using ctx: ExecutionContext): Consequence[Unit] = {
      writes.incrementAndGet(); delegate.save(collection, id, record)
    }
    override def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)
        (using ctx: ExecutionContext): Consequence[Unit] = {
      writes.incrementAndGet(); delegate.update(collection, id, changes)
    }
    override def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)
        (using ctx: ExecutionContext): Consequence[Unit] = {
      writes.incrementAndGet(); delegate.delete(collection, id)
    }
    override def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] = {
      searches.incrementAndGet(); delegate.search(collection, directive)
    }
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] = {
      searches.incrementAndGet(); delegate.count(collection, directive)
    }
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)
        (using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      writes.incrementAndGet(); delegate.mutateEntityDirect(plan)
    }
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)
        (using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      writes.incrementAndGet(); delegate.compareAndSetEntity(plan)
    }
    override def mutateVersionedEntity(plan: EntityVersionedMutationPlan)
        (using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      writes.incrementAndGet(); delegate.mutateVersionedEntity(plan)
    }
    def reset(): Unit = { writes.set(0); searches.set(0) }
  }

  final class MeasuredBlobStore(delegate: BlobStore) extends BlobStore {
    val gets = new AtomicInteger
    val puts = new AtomicInteger
    val deletes = new AtomicInteger
    val opens = new AtomicInteger
    val reads = new AtomicInteger
    val fallbacks = new AtomicInteger
    val closes = new AtomicInteger
    val consumed = new AtomicLong
    val maxRequest = new AtomicInteger
    val urls = new AtomicInteger
    val statuses = new AtomicInteger
    var mode = "normal"
    var beforeRead: () => Unit = () => ()
    var beforeClose: () => Unit = () => ()
    var putTransform: BlobPutResult => BlobPutResult = identity
    var issued = Vector.empty[BlobPutResult]
    var requested = Vector.empty[BlobStorageRef]
    def name: String = delegate.name
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      puts.incrementAndGet()
      delegate.put(request, payload).map { result => issued :+= result; putTransform(result) }
    }
    def delete(ref: BlobStorageRef): Consequence[Unit] = { deletes.incrementAndGet(); delegate.delete(ref) }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = {
      urls.incrementAndGet(); delegate.accessUrl(ref)
    }
    def status(): Consequence[BlobStoreStatus] = { statuses.incrementAndGet(); delegate.status() }
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = {
      gets.incrementAndGet(); requested :+= ref
      if (mode == "get-throw") throw new IOException("PRIVATE path credentials payload")
      else if (mode == "get-refuse") Consequence.stateInvalid("PRIVATE path credentials payload")
      else if (mode == "get-interrupt") throw new InterruptedException("PRIVATE path credentials payload")
      else delegate.get(ref).map { original =>
        val payload = new BinaryBag {
          val bag: Bag = original.payload.bag
          override def openInputStream(): InputStream = {
            opens.incrementAndGet()
            if (mode == "null-stream") null
            else {
              val input = original.payload.openInputStream()
              new FilterInputStream(input) {
                private var _entered = false
                private var _zero = mode == "zero" || mode.startsWith("fallback-")
                private var _trailing = true
                private def _enter(): Unit = {
                  if (!_entered) { _entered = true; beforeRead() }
                  if (mode == "stream-throw") throw new IOException("PRIVATE path credentials payload")
                  if (mode == "stream-interrupt") throw new InterruptedException("PRIVATE path credentials payload")
                }
                override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
                  reads.incrementAndGet()
                  maxRequest.set(math.max(maxRequest.get(), length))
                  _enter()
                  if (_zero) { _zero = false; 0 }
                  else if (mode == "negative-count") -2
                  else if (mode == "oversized-count") length + 1
                  else if (mode == "short") -1
                  else {
                    val count = in.read(buffer, offset, length)
                    if (count > 0) {
                      consumed.addAndGet(count.toLong)
                      if (mode == "tamper") buffer(offset) = (buffer(offset) ^ 1).toByte
                      count
                    } else if (count == -1 && mode == "trailing" && _trailing) {
                      _trailing = false; buffer(offset) = 1; consumed.incrementAndGet(); 1
                    } else count
                  }
                }
                override def read(): Int = {
                  fallbacks.incrementAndGet(); _enter()
                  val value = if (mode == "fallback-negative") -2
                    else if (mode == "fallback-large") 256 else in.read()
                  if (value >= 0 && value <= 255) consumed.incrementAndGet()
                  value
                }
                override def readAllBytes(): Array[Byte] = throw new IllegalStateException("unbounded fixture read")
                override def close(): Unit = {
                  try {
                    beforeClose()
                    if (mode == "close-throw") throw new IOException("PRIVATE path credentials payload")
                    if (mode == "close-interrupt") throw new InterruptedException("PRIVATE path credentials payload")
                  } finally { closes.incrementAndGet(); in.close() }
                }
              }
            }
          }
        }
        mode match {
          case "null-result" => null
          case "null-payload" => original.copy(payload = null)
          case "wrong-id" => original.copy(id = DurableJobBackupFixtures.otherId, payload = payload)
          case "wrong-ref" => original.copy(storageRef = ref.copy(key = "foreign"), payload = payload)
          case "null-ref" => original.copy(storageRef = null, payload = payload)
          case "wrong-content" => original.copy(contentType = ContentType.TEXT_PLAIN, payload = payload)
          case "wrong-size" => original.copy(byteSize = original.byteSize + 1L, payload = payload)
          case "wrong-digest" => original.copy(digest = "0" * 64, payload = payload)
          case "null-content" => original.copy(contentType = null, payload = payload)
          case "null-digest" => original.copy(digest = null, payload = payload)
          case "uppercase-digest" => original.copy(digest = original.digest.toUpperCase(Locale.ROOT), payload = payload)
          case _ => original.copy(payload = payload)
        }
      }
    }
    def reset(): Unit = {
      gets.set(0); puts.set(0); deletes.set(0); opens.set(0); reads.set(0); fallbacks.set(0)
      closes.set(0); consumed.set(0L); maxRequest.set(0); urls.set(0); statuses.set(0)
      requested = Vector.empty
    }
  }

  final class Fixture(val root: Path) {
    val native = new DurableJobBackupFixtures.Fixture(root)
    val context: ExecutionContext = native.ordinary
    val entity = new MeasuredEntityStore
    val blob = new MeasuredBlobStore(native.blob)
    val store = new DurableJobStore(entity, native.original.storagePolicy)
    val payloads = new DurableJobOwnedBlobPayloads(store, entity, blob, native.original.blobPolicy)
    val responses = new DurableJobOperationResponsePayloads(payloads)
    private var _sql = Option.empty[MeasuredSqlDataStore]
    def sql: MeasuredSqlDataStore = _sql.get
    def start(): Unit = {
      success(native.start())
      val measured = new MeasuredSqlDataStore(native.sql.get)
      _sql = Some(measured)
      context.dataStoreSpace.useDataStore(measured)
    }
    def use[A](body: => A): A = native.use(body)
    def running(label: String, legacy: Boolean = false): DurableJobStoreSnapshot = {
      val first = native.publish(label).snapshot
      native.checkpoint(first, first.record.body.copy(lifecycle = first.record.body.lifecycle.copy(
        status = DurableJobLifecycleStatus.Running,
        schedule = first.record.body.lifecycle.schedule.copy(startedAt = Some(DurableJobBackupFixtures.instant.plusSeconds(10))))), legacy)
    }
    def terminal(snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference,
      legacy: Boolean = false): DurableJobStoreSnapshot = {
      val body = snapshot.record.body
      native.checkpoint(snapshot, body.copy(
        tasks = body.tasks.map(task => task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Committed))),
        lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
          schedule = body.lifecycle.schedule.copy(completedAt = Some(DurableJobBackupFixtures.instant.plusSeconds(20)))),
        result = DurableResultOutcome.Succeeded(DurableValue.External(reference))), legacy)
    }
    def publish(snapshot: DurableJobStoreSnapshot, response: OperationResponse = review): DurableExternalReference =
      use(success(responses.publishObserved(snapshot, access, response)(using context)))
    def publishBytes(snapshot: DurableJobStoreSnapshot, value: Array[Byte],
      contentType: ContentType = DurableJobOperationResponsePayloads.contentType): DurableExternalReference =
      use(success(payloads.putObserved(snapshot, access, Some("fixture.json"), contentType, Bag.binary(value))(using context)))
    def completed(label: String = "completed"): (DurableJobStoreSnapshot, DurableExternalReference) = {
      val active = running(label)
      val reference = publish(active)
      terminal(active, reference) -> reference
    }
    def read(snapshot: DurableJobStoreSnapshot, selectedAccess: DurableJobRecordAccess = access): Consequence[OperationResponse] =
      use(responses.readObserved(snapshot, selectedAccess)(using context))
    def readBytes(snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference,
      selectedAccess: DurableJobRecordAccess = access): Consequence[DurableJobOwnedBlobDetachedPayload] =
      use(payloads.readObserved(snapshot, selectedAccess, reference)(using context))
    def physical(reference: DurableExternalReference): Vector[Byte] =
      Files.readAllBytes(native.physicalPath(reference)).toVector
    def quota: DurableJobOwnedBlobQuotaRecord =
      success(DurableJobOwnedBlobQuotaCodec.decode(native.row(DurableJobBackupFixtures.blobQuotaId).get.getString("canonicalJson").get))
    def replaceQuota(change: DurableJobOwnedBlobQuotaRecord => DurableJobOwnedBlobQuotaRecord): Unit =
      native.replaceText(DurableJobBackupFixtures.blobQuotaId, success(DurableJobOwnedBlobQuotaCodec.encode(change(quota))))
    def replaceBlob(reference: DurableExternalReference, change: Blob => Blob): Unit = use {
      val changed = change(native.metadata(reference))
      success(native.sql.get.save(DataStore.CollectionId.EntityStore(changed.id.collection), DataStore.EntryId(changed.id),
        native.row(changed.id).get ++ summon[EntityPersistent[Blob]].toStoreRecord(changed))(using context))
    }
    def state(snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference): DurableJobBackupFixtures.NativeState =
      native.state(DurableJobBackupFixtures.Root(snapshot, Vector(reference)))
    def facts(snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference): NativeFacts = NativeFacts(
      Vector(native.jobEntityId(snapshot.record.body.identity.jobId), DurableJobBackupFixtures.storageId,
        DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId).map(native.row),
      native.metadata(reference), physical(reference), native.effects)
    def reset(): Unit = { entity.metadata.set(0); blob.reset(); sql.reset() }
    def effects: ReadEffects = ReadEffects(entity.metadata.get(), blob.gets.get(), blob.opens.get(), blob.reads.get(),
      blob.fallbacks.get(), blob.closes.get(), blob.consumed.get(), blob.maxRequest.get(), sql.writes.get(), sql.searches.get(),
      blob.puts.get(), blob.deletes.get(), blob.urls.get(), blob.statuses.get())
    def close(): Unit = native.close()
    def closed: Boolean = native.sql.forall(value => !value.managedResourceOption.get.isOpen)
  }

  def generatedRecord(values: List[Int]): Record = Record(values.zipWithIndex.map { case (number, index) =>
    val value: Any = Math.floorMod(number, 6) match {
      case 0 => number % 2 == 0
      case 1 => number
      case 2 => s"界$number"
      case 3 => List(number, number + 1)
      case 4 => Vector(true, s"v$number")
      case 5 => Json.obj("n" -> Json.fromInt(number))
    }
    Field(s"k${index % 2}", Field.Value.Single(value))
  }.toVector)

  def generatedWire(values: List[Int]): String = {
    val fields = values.zipWithIndex.map { case (number, index) =>
      val atom = Math.floorMod(number, 6) match {
        case 0 => s"{\"type\":\"boolean\",\"value\":${number % 2 == 0}}"
        case 1 => s"{\"type\":\"int\",\"value\":$number}"
        case 2 => s"{\"type\":\"string\",\"value\":\"界$number\"}"
        case 3 => s"{\"type\":\"list\",\"values\":[{\"type\":\"int\",\"value\":$number},{\"type\":\"int\",\"value\":${number + 1}}]}"
        case 4 => s"{\"type\":\"vector\",\"values\":[{\"type\":\"boolean\",\"value\":true},{\"type\":\"string\",\"value\":\"v$number\"}]}"
        case 5 => s"{\"type\":\"json\",\"value\":{\"n\":$number}}"
      }
      s"{\"key\":\"k${index % 2}\",\"value\":$atom}"
    }
    prefix + "{\"kind\":\"record\",\"fields\":[" + fields.mkString(",") + "]}}"
  }
}
