package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.blob.{Blob, BlobPutResult, BlobRepository}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Actual configured owners and independent terminal publication intent.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponsePublicationFixtures {
  import BlobRepository.given

  val access: DurableJobRecordAccess = JobDurableRuntimeFixtures.access
  val boundaries: Vector[DurableJobLifecycleWriteBoundary] = DurableJobKeyedEvidenceFixtures.boundaries
  val review: OperationResponse = DurableJobOperationResponsePayloadFixtures.review
  val reviewFields: Vector[(String, String)] = DurableJobOperationResponsePayloadFixtures.reviewFields
  val reviewWire: String = DurableJobOperationResponsePayloadFixtures.reviewWire

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def task(response: OperationResponse): JobDurableLifetimeRecoveryFixtures.Task = {
    val selected = new JobDurableLifetimeRecoveryFixtures.Task
    selected.body = _ => TaskSucceeded(response)
    selected
  }

  final class Source(
    val recipe: DurableJobKeyedEvidenceFixtures.Recipe = DurableJobKeyedEvidenceFixtures.recipe("publication"),
    owned: Boolean = true
  ) extends DurableJobKeyedEvidenceSource {
    private val _requests = new ConcurrentLinkedQueue[DurableJobKeyedEvidenceRequest]()
    private val _pairs = new ConcurrentLinkedQueue[DurableJobLifecycleWriteEvidence]()
    var before: DurableJobKeyedEvidenceRequest => Unit = _ => ()
    var transform: (DurableJobKeyedEvidenceRequest, DurableJobLifecycleWriteEvidence) => DurableJobLifecycleWriteEvidence =
      (_, pair) => pair
    def requests: Vector[DurableJobKeyedEvidenceRequest] = _requests.iterator().asScala.toVector
    def pairs: Vector[DurableJobLifecycleWriteEvidence] = _pairs.iterator().asScala.toVector
    def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse] = {
      _requests.add(request)
      before(request)
      val original = DurableJobKeyedEvidenceFixtures.pair(request, recipe)
      val intent = if (owned && request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
        original.copy(resultPublication = DurableJobResultPublication.OwnedOperationResponse) else original
      val supplied = transform(request, intent)
      _pairs.add(supplied)
      Consequence.success(DurableJobKeyedEvidenceResponse(request.jobid, request.expectedrevision, supplied))
    }
  }

  final case class Prefix(snapshot: Option[DurableJobStoreSnapshot],
    native: Option[DurableJobLifetimeQuotaSnapshot], quota: Option[Record], writes: Int,
    jobacks: Int, ids: Int, puts: Int)

  final class Fixture(val root: Path, owned: Boolean = true) {
    val native = new JobDurableRuntimeFixtures.Fixture(root)
    val source = new Source(owned = owned)
    val blob = new DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore(native.selectedBlob)
    val events: EventStore = EventStore.inMemory
    native.selectedEvidence = source
    native.selectedBlob = blob
    native.engine.withEventStore(events)
    var terminalPrefix = Option.empty[Prefix]
    source.before = request => {
      if (request.request.boundary == DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
        terminalPrefix = Some(prefix(request.jobid))
    }
    def start(): Unit = { success(native.start()); () }
    def use[A](body: => Consequence[A]): Consequence[A] = native.owner._execute_c(body)
    def signed(jobId: JobId): DurableJobStoreSnapshot = native.signed(jobId)
    def prefix(jobId: JobId): Prefix = {
      val snapshot = success(use(native.owner._store.load(jobId.value, access)(using native.context)))
      val payment = if (snapshot.nonEmpty) Some(native.native(jobId)) else None
      val quota = quotaRow
      Prefix(snapshot, payment, quota, native.entity.writes.get(), native.entity.jobacks.get(),
        native.ids.calls.get(), blob.puts.get())
    }
    def quotaRow: Option[Record] = success(use {
      val id = success(DurableJobOwnedBlobQuotaEntity.entityId)
      val collection = DataStore.CollectionId.EntityStore(id.collection)
      native.context.dataStoreSpace.dataStore(collection).flatMap(_.load(collection, DataStore.EntryId(id))(using native.context))
    })
    def quota: Option[DurableJobOwnedBlobQuotaRecord] = quotaRow.map(row =>
      success(DurableJobOwnedBlobQuotaCodec.decode(row.getString("canonicalJson").get)))
    def metadata(reference: DurableExternalReference): Blob = success(use {
      given ExecutionContext = native.context
      native.entity.load[Blob](success(EntityId.parse(reference.reference))).map(_.get)
    })
    def physical(reference: DurableExternalReference): Vector[Byte] = {
      val ref = metadata(reference).storageRef.get
      Files.readAllBytes(root.resolve("blobs").resolve(ref.container).resolve(ref.key)).toVector
    }
    def issuedPhysical(result: BlobPutResult): Boolean = Files.exists(root.resolve("blobs")
      .resolve(result.storageRef.container).resolve(result.storageRef.key))
    def read(snapshot: DurableJobStoreSnapshot): Consequence[OperationResponse] = use {
      native.owner._result_payloads.readObserved(snapshot, access)(using native.context)
    }
    def readBytes(snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference)
        : Consequence[DurableJobOwnedBlobDetachedPayload] = use {
      native.owner._payloads.readObserved(snapshot, access, reference)(using native.context)
    }
    def reference(snapshot: DurableJobStoreSnapshot): DurableExternalReference = snapshot.record.body.result match {
      case DurableResultOutcome.Succeeded(DurableValue.External(reference)) => reference
      case _ => throw new IllegalStateException("expected actual acknowledged terminal owned reference")
    }
    def successEvents: Vector[String] = success(events.query(EventStore.Query())).map(_.name)
      .filter(_ == "job.succeeded")
    def close(): Unit = native.close()
  }

  def withFixture[A](owned: Boolean = true)(body: Fixture => A): A = {
    val root = JobDurableRuntimeFixtures.root()
    val fixture = new Fixture(root, owned)
    try { fixture.start(); body(fixture) }
    finally { fixture.close(); JobDurableRuntimeFixtures.remove(root) }
  }
}
