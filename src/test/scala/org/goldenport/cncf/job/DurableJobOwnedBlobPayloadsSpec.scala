package org.goldenport.cncf.job

import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Fresh allocation, immutable provider ownership and failed-publication rollback.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobOwnedBlobPayloadsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import BlobRepository.given
  import DurableJobOwnedBlobPayloads.*

  "Job-owned payload publication" should {
    "O1 persist native bytes and exact ownership for current V1 and V2 snapshots across fresh adapters" in {
      Vector(false, true).foreach { v2 =>
        Given("an admitted current Active signed record and a test-owned native local BlobStore")
        val root = Files.createTempDirectory("job-owned-producer")
        val fixture = _fixture(LocalBlobStore(root))
        given ExecutionContext = fixture.context
        val observed = _success(fixture.store.create(_record("owner", v2), _access))
        val payload = "owned native bytes".getBytes(StandardCharsets.UTF_8)

        When("the trusted adapter allocates and publishes two payloads without a manage capability")
        val reference = _success(fixture.adapter.putObserved(observed, _access, Some("input.bin"), ContentType.APPLICATION_OCTET_STREAM, Bag.binary(payload)))
        val second = _success(fixture.adapter.putObserved(observed, _access, Some("second.bin"), ContentType.APPLICATION_OCTET_STREAM, Bag.binary(payload)))
        val id = _id(reference.reference)
        val freshcontext = ExecutionContext.withJobContext(fixture.context, JobContext.empty)
        val freshentity = EntityStore.standard()
        val metadata = _success(freshentity.load[Blob](id)(using summon[EntityPersistent[Blob]], freshcontext)).get
        val freshblob = LocalBlobStore(root)
        val read = _success(freshblob.get(metadata.storageRef.get))
        val attached = _sign(observed.record.body.copy(inputs = Vector(DurableInputReference("input", DurableValue.External(reference)))), v2)
        val targets = _success(new DurableJobOwnedBlobPayloads(new DurableJobStore(freshentity), freshentity, freshblob).prepare(attached)(using freshcontext))

        Then("fresh stores retain exact bytes, provider binding and length-prefixed creation-principal labels")
        reference.storageKind shouldBe storageKind
        reference.reference shouldBe id.value
        id.collection shouldBe BlobRepository.CollectionId
        second.reference should not be reference.reference
        reference.byteSize shouldBe payload.length.toLong
        reference.sha256 shouldBe _sha(payload)
        metadata.kind shouldBe BlobKind.Binary
        metadata.sourceMode shouldBe BlobSourceMode.Managed
        metadata.externalUrl shouldBe None
        metadata.accessUrl shouldBe BlobUrl.cncfRoute(id)
        metadata.attributes shouldBe Map(ownershipAttribute -> "exclusive-v1", ownerAttribute -> _fingerprint(Vector(
          Some("owner"), Some(_instant.toString), Some("tenant-a"), Some("user"), Some("subject-a"))),
          bindingAttribute -> _fingerprint(Vector(Some(id.value), Some(metadata.storageRef.get.print), None, None,
            Some(read.contentType.header), Some(payload.length.toString), Some(_sha(payload)))))
        _bytes(read.payload) shouldBe payload.toVector
        Files.exists(root.resolve(metadata.storageRef.get.container).resolve(metadata.storageRef.get.key)) shouldBe true
        targets.map(_.storageRef) shouldBe Vector(metadata.storageRef.get)
        _success(fixture.store.load("owner", _access)).get shouldBe observed
      }

      Given("a configured in-memory provider using the same real EntityStore metadata contract")
      val fixture = _fixture(InMemoryBlobStore())
      given ExecutionContext = fixture.context
      val observed = _success(fixture.store.create(_record("memory", false), _access))
      When("publication stores native in-memory bytes")
      val reference = _success(fixture.adapter.putObserved(observed, _access, None, ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](1, 2))))
      val blob = _success(fixture.entitystore.load[Blob](_id(reference.reference))).get
      Then("both typed metadata and provider bytes exist")
      _bytes(_success(fixture.blob.get(blob.storageRef.get)).payload) shouldBe Vector[Byte](1, 2)
      blob.contentType.map(_.header) shouldBe reference.contentType
    }

    "O2 refuse foreign, corrupt, stale and inactive authority before any put or delete" in {
      Given("a real signed store, measured providers and an earlier optimistic snapshot")
      val fixture = _fixture(InMemoryBlobStore())
      given ExecutionContext = fixture.context
      val observed = _success(fixture.store.create(_record("admission", true), _access))
      val current = _success(fixture.store.checkpoint(observed, _sign(observed.record.body.copy(
        identity = observed.record.body.identity.copy(revision = 2L, updatedAt = _instant.plusSeconds(1))), true), _access))
      val accesses = Vector(_access.copy(tenantId = "foreign"), _access.copy(subjectId = "foreign"), _access.copy(scopes = Set.empty), null)
      val corrupt = current.copy(record = current.record.copy(integrity = current.record.integrity.copy(unsignedBodySha256 = "b" * 64)))
      val deleted = _success(fixture.store.create(_record("inactive", true, _active.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(_instant))), _access))

      When("producer admission sees incorrect access, stale/signature-corrupt snapshots and logical deletion")
      val results = accesses.map(access => fixture.adapter.putObserved(current, access, None, ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](1)))) ++
        Vector(observed, corrupt, deleted, null).map(snapshot => fixture.adapter.putObserved(snapshot, _access, None, ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](1))))

      Then("constant bounded refusals publish nothing and do not touch payload providers or Blob metadata")
      results.foreach(_ shouldBe a[Consequence.Failure[_]])
      results.map(_message).distinct.size shouldBe 1
      results.foreach(result => _message(result).length should be < 1024)
      fixture.blob.putCount shouldBe 0
      fixture.blob.deleted shouldBe Vector.empty
      fixture.entitystore.blobLoads shouldBe 0
    }

    "O2 reject a reused allocated ID and metadata-load failures without interpreting failure as absence" in {
      Vector(false, true).foreach { loadfailure =>
        Given("an already owned native payload and a runtime allocator deliberately issuing its existing canonical ID")
        val fixture = _fixture(InMemoryBlobStore())
        given ExecutionContext = fixture.context
        val observed = _success(fixture.store.create(_record("existing", false), _access))
        val existing = _success(fixture.adapter.putObserved(observed, _access, Some("old.bin"), ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](7))))
        val metadata = _success(fixture.entitystore.load[Blob](_id(existing.reference))).get
        val context = ExecutionContext.withIdGenerationContext(fixture.context, new FixedIds(metadata.id))
        fixture.entitystore.failBlobLoad = loadfailure
        val puts = fixture.blob.putCount

        When("a new publication encounters an existing ID or an injected typed metadata failure")
        val result = fixture.adapter.putObserved(observed, _access, None, ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](9)))(using context)

        Then("the old bytes remain and neither put nor delete is dispatched")
        result shouldBe a[Consequence.Failure[_]]
        fixture.blob.putCount shouldBe puts
        fixture.blob.deleted shouldBe Vector.empty
        _bytes(_success(fixture.blob.get(metadata.storageRef.get)).payload) shouldBe Vector[Byte](7)
      }
    }

    "O3 roll back only the newly issued payload after invalid acknowledgements, metadata failure or deletion races" in {
      Vector("size", "digest", "type", "id", "store", "null", "null-ref", "create", "race", "rollback").foreach { mode =>
        Given("unrelated bytes and a current signed job with one deterministic post-put refusal")
        val fixture = _fixture(InMemoryBlobStore())
        given ExecutionContext = fixture.context
        val observed = _success(fixture.store.create(_record("rollback", true), _access))
        val unrelated = _success(fixture.adapter.putObserved(observed, _access, Some("keep.bin"), ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](7))))
        val old = _success(fixture.entitystore.load[Blob](_id(unrelated.reference))).get
        fixture.blob.transform = mode match {
          case "size" => _.copy(byteSize = -1L)
          case "digest" | "rollback" => _.copy(digest = "bad")
          case "type" => _.copy(contentType = null)
          case "id" => _.copy(id = old.id)
          case "store" => result => result.copy(storageRef = result.storageRef.copy(store = "foreign"))
          case "null" => _ => null
          case "null-ref" => _.copy(storageRef = null)
          case _ => identity
        }
        fixture.entitystore.failBlobCreate = mode == "create"
        fixture.blob.failDelete = mode == "rollback"
        if (mode == "race") fixture.blob.afterPut = () => {
          val retention = observed.record.body.retention.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(_instant.plusSeconds(1)))
          _success(fixture.store.checkpoint(observed, _sign(observed.record.body.copy(retention = retention,
            identity = observed.record.body.identity.copy(revision = 2L, updatedAt = _instant.plusSeconds(1))), true), _access))
          ()
        }

        When("publication refuses after the provider has acknowledged a fresh allocation")
        val result = fixture.adapter.putObserved(observed, _access, Some("new.bin"), ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](9)))

        Then("failure stays bounded, rollback targets only the new handle and unrelated ownership remains intact")
        result shouldBe a[Consequence.Failure[_]]
        _message(result) should not include "private-provider"
        fixture.blob.deleted.size shouldBe (if (Set("id", "store", "null", "null-ref").contains(mode)) 0 else 1)
        fixture.blob.deleted.foreach(_.key should not be old.storageRef.get.key)
        _bytes(_success(fixture.blob.get(old.storageRef.get)).payload) shouldBe Vector[Byte](7)
        if (mode == "race") {
          val newlyissued = fixture.blob.issued.last
          _success(fixture.entitystore.load[Blob](newlyissued.id)).nonEmpty shouldBe true
          _success(fixture.store.load("rollback", _access)).get.record.body.retention.deletionState shouldBe DurableDeletionState.Deleted
        }
        if (Set("rollback", "id", "store", "null", "null-ref").contains(mode)) fixture.blob.get(fixture.blob.issued.last.storageRef) shouldBe a[Consequence.Success[_]]
        else fixture.blob.get(fixture.blob.issued.last.storageRef) shouldBe a[Consequence.Failure[_]]
      }
    }
  }

  private val _instant = Instant.parse("2026-10-01T01:02:03Z")
  private val _active = DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))

  private final case class Fixture(store: DurableJobStore, entitystore: MeasuredEntityStore, blob: MeasuredBlobStore,
    adapter: DurableJobOwnedBlobPayloads, context: ExecutionContext)

  private final class FixedIds(id: EntityId) extends IdGenerationContext {
    def namespace: IdGenerationContext.IdNamespace = IdGenerationContext.DEFAULT_NAMESPACE
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = id
    def opaqueId(purpose: String): String = "fixed"
  }

  private final class MeasuredEntityStore extends StandardEntityStore {
    var blobLoads: Int = 0
    var failBlobLoad: Boolean = false
    var failBlobCreate: Boolean = false
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      if (id.collection == BlobRepository.CollectionId) {
        blobLoads += 1
        if (failBlobLoad) return Consequence.stateInvalid("private-provider-load")
      }
      super.load[T](id)
    }
    override def create[T](entity: T, options: EntityCreateOptions)(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
      if (failBlobCreate && tc.collection(entity) == BlobRepository.CollectionId) Consequence.stateInvalid("private-provider-create")
      else super.create(entity, options)
  }

  private final class MeasuredBlobStore(delegate: BlobStore) extends BlobStore {
    private var _put_count = 0
    private var _deleted = Vector.empty[BlobStorageRef]
    private var _issued = Vector.empty[BlobPutResult]
    var transform: BlobPutResult => BlobPutResult = identity
    var afterPut: () => Unit = () => ()
    var failDelete: Boolean = false
    def name: String = delegate.name
    def putCount: Int = _put_count
    def deleted: Vector[BlobStorageRef] = _deleted
    def issued: Vector[BlobPutResult] = _issued
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      _put_count += 1
      delegate.put(request, payload).map { result =>
        _issued :+= result
        afterPut()
        transform(result)
      }
    }
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = delegate.get(ref)
    def delete(ref: BlobStorageRef): Consequence[Unit] = {
      _deleted :+= ref
      if (failDelete) Consequence.stateInvalid("private-provider-delete") else delegate.delete(ref)
    }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = delegate.accessUrl(ref)
    def status(): Consequence[BlobStoreStatus] = delegate.status()
  }

  private def _fixture(blob: BlobStore): Fixture = {
    val context = ExecutionContext.test()
    val entity = new MeasuredEntityStore
    val store = new DurableJobStore(entity)
    val measured = new MeasuredBlobStore(blob)
    Fixture(store, entity, measured, new DurableJobOwnedBlobPayloads(store, entity, measured), context)
  }

  private def _record(jobid: String, v2: Boolean, retention: DurableRetentionState = _active): DurableJobRecord = {
    val body = DurableJobRecordBody(DurableJobIdentity(jobid, 1L, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(DurableJobLifecycleStatus.Succeeded, 1, DurableRunMode.Async,
        DurableRetryEvidence(Vector.empty, 1, None, false, false), DurableScheduleState(None, None, None)),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component", None, "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector.empty, DurableResultOutcome.Succeeded(DurableValue.Absent), Vector.empty, Vector.empty, None,
      DurableDefinitionSnapshot("definition", "job", 1, 1L, "a" * 64, None, None, Map.empty), retention)
    _sign(body, v2)
  }

  private def _sign(body: DurableJobRecordBody, v2: Boolean): DurableJobRecord =
    _success(if (v2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))

  private def _id(value: String): EntityId = _success(EntityId.parse(value))

  private def _sha(bytes: Array[Byte]): String = MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString

  private def _fingerprint(values: Vector[Option[String]]): String = {
    val bytes = new ByteArrayOutputStream()
    val output = new DataOutputStream(bytes)
    values.foreach {
      case None => output.writeByte(0)
      case Some(value) =>
        val encoded = value.getBytes(StandardCharsets.UTF_8)
        output.writeByte(1)
        output.writeInt(encoded.length)
        output.write(encoded)
    }
    output.close()
    _sha(bytes.toByteArray)
  }

  private def _bytes(payload: BinaryBag): Vector[Byte] = {
    val stream = payload.openInputStream()
    try stream.readAllBytes().toVector finally stream.close()
  }

  private def _message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => fail("expected bounded failure")
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
