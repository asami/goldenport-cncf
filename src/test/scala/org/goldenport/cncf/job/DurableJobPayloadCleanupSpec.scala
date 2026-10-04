package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Native physical disposal, complete immutable ownership preflight and retry.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobPayloadCleanupSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import BlobRepository.given
  import DurableJobCleanupFacet.*
  import DurableJobOwnedBlobPayloads.*

  "Job-owned physical cleanup" should {
    "P1 dispose distinct input, result and calltree files/sidecars while retaining shared assets and canonical V1/V2 evidence" in {
      Vector(false, true).foreach { v2 =>
        Given("three real owned local payloads, a duplicate input and independent shared/definition bytes")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("native", v2 = v2))
        val refs = Vector.fill(3)(_payload(fixture, active))
        val shared = _payload(fixture, active)
        val definition = _payload(fixture, active)
        val metadata = refs.map(_metadata(fixture, _))
        val sharedblob = _metadata(fixture, shared)
        val definitionblob = _metadata(fixture, definition)
        val body = active.record.body.copy(
          inputs = Vector(DurableInputReference("first", DurableValue.External(refs(0))), DurableInputReference("duplicate", DurableValue.External(refs(0))),
            DurableInputReference("shared", DurableValue.External(shared.copy(storageKind = "blob")))),
          result = DurableResultOutcome.Succeeded(DurableValue.External(refs(1))), calltreeReference = Some(refs(2)),
          definitionSnapshot = active.record.body.definitionSnapshot.copy(declaredSource = Some(definition)))
        val deleted = _checkpoint(fixture, active, body.copy(retention = _deleted))

        When("authorized cleanup preflights every owned reference and acknowledges each distinct provider deletion")
        val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
        val current = _load(fixture, "native")

        Then("bytes and sidecars vanish exactly once, audit metadata/assets survive, and only retention/identity advance")
        result shouldBe Tombstoned
        fixture.blob.deleted shouldBe metadata.map(_.storageRef.get)
        metadata.foreach { blob =>
          Files.exists(_path(fixture.root, blob)) shouldBe false
          Files.exists(_sidecar(fixture.root, blob)) shouldBe false
          _success(fixture.entity.load[Blob](blob.id)).get shouldBe blob
        }
        Vector(sharedblob, definitionblob).foreach { blob =>
          Files.readAllBytes(_path(fixture.root, blob)).toVector shouldBe Vector[Byte](1, 2, 3)
          Files.exists(_sidecar(fixture.root, blob)) shouldBe true
        }
        _tombstone(deleted, current)
      }
    }

    "P2 retain partial-delete authority and retry through fresh native providers, then make repeated tombstones no-ops" in {
      Given("two real payloads and a provider that fails the second deletion after disposing the first")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("retry", v2 = true))
      val refs = Vector.fill(2)(_payload(fixture, active))
      val blobs = refs.map(_metadata(fixture, _))
      val deleted = _attach_deleted(fixture, active, refs)
      fixture.blob.failAt = Some(2)

      When("cleanup partially succeeds and the stores, adapter, cleanup owner and context are reopened")
      val first = fixture.cleanup.cleanupObserved(deleted, _access, _now)
      val persisted = _load(fixture, "retry")
      val intermediate = blobs.map(metadata => (Files.exists(_path(fixture.root, metadata)), Files.exists(_sidecar(fixture.root, metadata))))
      val context = ExecutionContext.withJobContext(fixture.context, JobContext.empty)
      val entity = EntityStore.standard()
      val store = new DurableJobStore(entity)
      val blob = new MeasuredBlobStore(LocalBlobStore(fixture.root))
      val cleanup = new DurableJobPayloadCleanup(store, new DurableJobOwnedBlobPayloads(store, entity, blob))
      val resumed = _success(store.load("retry", _access)(using context)).get
      val retry = cleanup.cleanupObserved(resumed, _access, _now)(using context)
      val tombstone = _success(store.load("retry", _access)(using context)).get
      val count = blob.deleted.size
      val repeated = cleanup.cleanupObserved(tombstone, _access, _now)(using context)
      val finalvalue = _success(store.load("retry", _access)(using context)).get

      Then("the immutable Deleted snapshot survives failure and idempotent acknowledgements permit one final checkpoint")
      first shouldBe PayloadDeleteRefused
      persisted shouldBe deleted
      intermediate shouldBe Vector((false, false), (true, true))
      resumed shouldBe deleted
      retry shouldBe Tombstoned
      blob.deleted shouldBe blobs.map(_.storageRef.get)
      blobs.foreach { metadata =>
        Files.exists(_path(fixture.root, metadata)) shouldBe false
        Files.exists(_sidecar(fixture.root, metadata)) shouldBe false
        _success(entity.load[Blob](metadata.id)(using summon[EntityPersistent[Blob]], context)).get shouldBe metadata
      }
      _tombstone(deleted, tombstone)
      repeated shouldBe AlreadyTombstoned
      blob.deleted.size shouldBe count
      finalvalue shouldBe tombstone
    }

    "P3 refuse the complete plan for every missing, foreign, mutable or malformed ownership edge" in {
      val modes = Vector("job", "epoch", "tenant", "subject", "absent", "nonexclusive", "missing-owner", "external", "size", "digest",
        "type", "missing-type", "missing-size", "missing-digest", "missing-ref", "malformed-digest", "ref", "version", "etag", "binding", "wrong-store",
        "noncanonical", "wrong-collection", "shared", "definition", "conflict", "load-failure")
      modes.foreach { mode =>
        Given("a valid first native target followed by one deterministic ownership disagreement: " + mode)
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("ownership", v2 = true))
        val first = _payload(fixture, active)
        val second = _payload(fixture, active)
        val validblob = _metadata(fixture, first)
        val original = _metadata(fixture, second)
        val changed = mode match {
          case "nonexclusive" => original.copy(attributes = original.attributes.updated(ownershipAttribute, "shared"))
          case "missing-owner" => original.copy(attributes = original.attributes - ownerAttribute)
          case "external" => original.copy(sourceMode = BlobSourceMode.ExternalUrl, externalUrl = Some("https://example.test/payload"))
          case "size" => original.copy(byteSize = Some(999L))
          case "digest" => original.copy(digest = Some("b" * 64))
          case "type" => original.copy(contentType = Some(ContentType.TEXT_PLAIN))
          case "missing-type" => original.copy(contentType = None)
          case "missing-size" => original.copy(byteSize = None)
          case "missing-digest" => original.copy(digest = None)
          case "missing-ref" => original.copy(storageRef = None)
          case "malformed-digest" => original.copy(digest = Some("malformed"))
          case "ref" => original.copy(storageRef = original.storageRef.map(_.copy(key = "other-key")))
          case "version" => original.copy(storageRef = original.storageRef.map(_.copy(version = Some("v2"))))
          case "etag" => original.copy(storageRef = original.storageRef.map(_.copy(etag = Some("different"))))
          case "binding" => original.copy(attributes = original.attributes.updated(bindingAttribute, "b" * 64))
          case "wrong-store" => original.copy(storageRef = original.storageRef.map(_.copy(store = "other")))
          case _ => original
        }
        if (mode == "absent") _success(fixture.entity.deleteHard(original.id))
        else if (changed != original) _success(fixture.entity.save(changed))
        val badref = mode match {
          case "noncanonical" => second.copy(reference = " " + second.reference)
          case "wrong-collection" => second.copy(reference = _success(DurableJobStoreEntity.entityId("other")).value)
          case _ => second
        }
        var body = active.record.body.copy(inputs = Vector(DurableInputReference("valid", DurableValue.External(first)),
          DurableInputReference("invalid", DurableValue.External(badref))), retention = _deleted)
        if (mode == "epoch") body = body.copy(identity = body.identity.copy(createdAt = _instant.plusSeconds(1)))
        if (mode == "tenant") body = body.copy(authorization = body.authorization.copy(tenantId = "other-tenant"))
        if (mode == "subject") body = body.copy(authorization = body.authorization.copy(submitter = DurableSubject("other-subject", "user")))
        if (mode == "shared") body = body.copy(calltreeReference = Some(second.copy(storageKind = "opaque")))
        if (mode == "definition") body = body.copy(definitionSnapshot = body.definitionSnapshot.copy(declaredSource = Some(second)))
        if (mode == "conflict") body = body.copy(calltreeReference = Some(second.copy(byteSize = second.byteSize + 1)))
        val access = _access.copy(tenantId = body.authorization.tenantId, subjectId = body.authorization.submitter.id)
        val deleted = if (Set("job", "tenant", "subject").contains(mode)) {
          val id = if (mode == "job") "foreign-job" else "authorized-new-principal"
          _success(fixture.store.create(_sign(body.copy(identity = body.identity.copy(jobId = id, updatedAt = _now)), true), access))
        } else _checkpoint(fixture, active, body)
        if (mode == "load-failure") fixture.entity.failBlobLoad = true

        When("the entire ownership vector is preflighted before physical dispatch")
        val result = fixture.cleanup.cleanupObserved(deleted, access, _now)

        Then("the valid first file and sidecar still exist, no delete ran and no tombstone was written")
        result shouldBe OwnershipRefused
        fixture.blob.deleted shouldBe Vector.empty
        Files.readAllBytes(_path(fixture.root, validblob)).toVector shouldBe Vector[Byte](1, 2, 3)
        Files.exists(_sidecar(fixture.root, validblob)) shouldBe true
        _success(fixture.store.load(deleted.record.body.identity.jobId, access)).get shouldBe deleted
      }
    }

    "P4 require manage and exact ordinary authorization before provider loads and refuse corruption/staleness without repair" in {
      Given("a current signed Deleted snapshot and measured Job/Blob loads")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("authority", v2 = true))
      val ref = _payload(fixture, active)
      val metadata = _metadata(fixture, ref)
      val deleted = _attach_deleted(fixture, active, Vector(ref))
      fixture.entity.resetCounts()
      val accesses = Vector(_access.copy(scopes = Set("job.read")), _access.copy(tenantId = "foreign"),
        _access.copy(subjectId = "foreign"), _access.copy(scopes = Set(DurableJobRetentionPolicy.manageScope)))

      When("manage or ordinary tenant/subject/required-scope admission refuses")
      val refused = accesses.map(access => fixture.cleanup.cleanupObserved(deleted, access, _now))
      val loads = fixture.entity.jobLoads
      val corrupt = fixture.cleanup.cleanupObserved(deleted.copy(record = deleted.record.copy(integrity =
        deleted.record.integrity.copy(unsignedBodySha256 = "b" * 64))), _access, _now)
      val stale = fixture.cleanup.cleanupObserved(active, _access, _now)

      Then("closed facets expose no raw evidence, access denial performs zero loads and native bytes remain")
      refused shouldBe Vector.fill(4)(Refused)
      loads shouldBe 0
      corrupt shouldBe Corrupt
      stale shouldBe Stale
      fixture.entity.blobLoads shouldBe 0
      fixture.blob.deleted shouldBe Vector.empty
      Files.exists(_path(fixture.root, metadata)) shouldBe true

      When("an exact current legitimate snapshot requests cleanup")
      val admitted = fixture.cleanup.cleanupObserved(deleted, _access, _now)
      Then("normal plus explicit manage authority permits disposal")
      admitted shouldBe Tombstoned

      Given("a separate signed Deleted record whose stored canonical text is deliberately corrupted")
      val broken = _create(fixture, _record("corrupt-storage", retention = _deleted, v2 = true))
      _corrupt(fixture, broken)
      val rawbefore = _raw(fixture, "corrupt-storage")
      When("cleanup consumes the corrupted provider value")
      val storagecorrupt = fixture.cleanup.cleanupObserved(broken, _access, _now)
      val rawafter = _raw(fixture, "corrupt-storage")
      Then("corruption stays closed and never receives a repaired signature or revision")
      storagecorrupt shouldBe Corrupt
      rawafter shouldBe rawbefore

      Given("a supplied signed snapshot whose authoritative Job has disappeared")
      val missing = _create(fixture, _record("missing", retention = _deleted))
      _success(fixture.entity.deleteHard(_success(DurableJobStoreEntity.entityId("missing"))))
      When("cleanup reloads current authority")
      val absent = fixture.cleanup.cleanupObserved(missing, _access, _now)
      Then("missing is a closed no-effect result")
      absent shouldBe Missing
    }

    "P5 enforce lifecycle, hold, floor, time and revision boundaries before touching native bytes" in {
      val cases = Vector(
        (_active, DurableJobLifecycleStatus.Succeeded, _now, 1L, NotDeleted),
        (_active.copy(expiresAt = Some(_now), deletionState = DurableDeletionState.Expired), DurableJobLifecycleStatus.Succeeded, _now, 1L, NotDeleted),
        (_deleted.copy(retainUntil = Some(Instant.MAX)), DurableJobLifecycleStatus.Succeeded, _now, 1L, Held),
        (_deleted.copy(retainUntil = Some(_now.plusSeconds(1))), DurableJobLifecycleStatus.Succeeded, _now, 1L, NotDue),
        (_deleted, DurableJobLifecycleStatus.Succeeded, null, 1L, InvalidTime),
        (_deleted, DurableJobLifecycleStatus.Succeeded, _instant.minusNanos(1), 1L, InvalidTime),
        (_deleted, DurableJobLifecycleStatus.Succeeded, _now, Long.MaxValue, RevisionExhausted)
      ) ++ Vector(DurableJobLifecycleStatus.Submitted, DurableJobLifecycleStatus.Running, DurableJobLifecycleStatus.Suspended)
        .map(status => (_deleted, status, _now, 1L, NotTerminal))
      cases.foreach { case (retention, status, now, revision, expected) =>
        Given("a real owned payload transferred to a canonical lifecycle boundary fixture")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("boundary", v2 = true))
        val reference = _payload(fixture, active)
        val metadata = _metadata(fixture, reference)
        val next = _record("boundary", retention = retention, status = status, revision = revision, v2 = true)
        // Separate fixture identity permits an exhausted semantic revision without a noncontiguous checkpoint.
        _success(fixture.entity.saveDetached(DurableJobStoreEntity(_success(DurableJobStoreEntity.entityId("boundary")), "boundary",
          _success(DurableJobRecordCodec.canonicalJson(_sign(next.body.copy(inputs = Vector(DurableInputReference("payload", DurableValue.External(reference)))), true)))),
          Some(active.providerrevision), _policy(active.providerrevision)))
        val observed = _load(fixture, "boundary")
        fixture.entity.resetCounts()

        When("cleanup applies the admitted retained-lifecycle boundary")
        val facet = fixture.cleanup.cleanupObserved(observed, _access, now)

        Then("the expected closed refusal preserves bytes, metadata and Job revision")
        facet shouldBe expected
        fixture.blob.deleted shouldBe Vector.empty
        Files.exists(_path(fixture.root, metadata)) shouldBe true
        fixture.entity.blobLoads shouldBe 0
        _load(fixture, "boundary") shouldBe observed
      }

      Given("terminal Keep explicitly Deleted with only opaque/external evidence")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val external = DurableExternalReference("https://example.test/shared", "external-url", Some("text/plain"), 3L, "a" * 64)
      val record = _record("non-owned", retention = _deleted)
      val deleted = _create(fixture, _sign(record.body.copy(inputs = Vector(DurableInputReference("external", DurableValue.External(external)))), false))
      When("owned-payload cleanup sees no owned deletion target")
      val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
      val current = _load(fixture, "non-owned")
      Then("a zero-payload tombstone retains external evidence without any physical dispatch")
      result shouldBe Tombstoned
      fixture.blob.deleted shouldBe Vector.empty
      fixture.entity.blobLoads shouldBe 0
      current.record.body.inputs shouldBe deleted.record.body.inputs
      _tombstone(deleted, current)
    }

    "P6 require deletion acknowledgements despite failed reads and preserve newer state on final checkpoint race" in {
      Vector(false, true).foreach { throwing =>
        Given("a native target whose delete fails or throws and whose generic reads also fail")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("delete-failure"))
        val reference = _payload(fixture, active)
        val metadata = _metadata(fixture, reference)
        val deleted = _attach_deleted(fixture, active, Vector(reference))
        fixture.blob.failAt = Some(1)
        fixture.blob.throwDelete = throwing
        fixture.blob.failGet = true
        When("cleanup requests provider disposal")
        val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
        Then("neither a failed read nor an exception certifies absence or advances retention")
        result shouldBe PayloadDeleteRefused
        fixture.blob.getCount shouldBe 0
        Files.exists(_path(fixture.root, metadata)) shouldBe true
        _load(fixture, "delete-failure") shouldBe deleted
      }

      Given("a fully preflighted payload and a deterministic optimistic race immediately before the final provider write")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val active = _create(fixture, _record("checkpoint-race", v2 = true))
      val ref = _payload(fixture, active)
      val metadata = _metadata(fixture, ref)
      val deleted = _attach_deleted(fixture, active, Vector(ref))
      var raced: Option[DurableJobStoreSnapshot] = None
      fixture.entity.arm(() => raced = Some(_checkpoint(fixture, deleted, deleted.record.body.copy(diagnostics =
        Vector(DurableDiagnosticSummary("execution", "new", "info", "newer authoritative evidence"))))))
      When("physical disposal completes before the optimistic checkpoint loses its race")
      val first = fixture.cleanup.cleanupObserved(deleted, _access, _now)
      val current = _load(fixture, "checkpoint-race")
      val retried = fixture.cleanup.cleanupObserved(current, _access, _now)
      val finalvalue = _load(fixture, "checkpoint-race")
      Then("newer Deleted authority survives, lost bytes are never restored and a fresh idempotent retry finalizes")
      first shouldBe CheckpointRefused
      current shouldBe raced.get
      current.record.body.retention.deletionState shouldBe DurableDeletionState.Deleted
      Files.exists(_path(fixture.root, metadata)) shouldBe false
      retried shouldBe Tombstoned
      fixture.blob.deleted shouldBe Vector(metadata.storageRef.get, metadata.storageRef.get)
      finalvalue.record.body.diagnostics shouldBe current.record.body.diagnostics
      _tombstone(current, finalvalue)
    }

    "P7 admit exactly 1024 full-vector references, refuse 1025 before metadata and preserve seeded order/dedup" in {
      Vector(1024, 1025).foreach { count =>
        Given("one real native payload repeated across an entire bounded reference vector including protected definition evidence")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("bound"))
        val reference = _payload(fixture, active)
        val metadata = _metadata(fixture, reference)
        val definition = DurableExternalReference("opaque-definition", "blob", Some("text/plain"), 1L, "a" * 64)
        val body = active.record.body.copy(inputs = Vector.tabulate(count - 1)(index => DurableInputReference(s"input-$index", DurableValue.External(reference))),
          definitionSnapshot = active.record.body.definitionSnapshot.copy(declaredSource = Some(definition)), retention = _deleted)
        val deleted = _checkpoint(fixture, active, body)
        fixture.entity.resetCounts()
        When("cleanup admits the whole vector before metadata reads and deduplicates canonical IDs")
        val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
        Then("inclusive bound performs one metadata read/delete while oversupply performs none")
        result shouldBe (if (count == 1024) Tombstoned else ResourceBoundRefused)
        fixture.entity.blobLoads shouldBe (if (count == 1024) 1 else 0)
        fixture.blob.deleted.size shouldBe (if (count == 1024) 1 else 0)
        Files.exists(_path(fixture.root, metadata)) shouldBe (count == 1025)
      }

      Given("seeded small permutations and duplicate variants with three physically distinct native payloads")
      val gen = Gen.listOfN(8, Gen.choose(0, 2)).map(values => (values ++ List(0, 1, 2)).toVector)
      When("each generated vector is cleaned and each conflicting variant is preflighted")
      val property = Prop.forAll(gen) { order =>
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val active = _create(fixture, _record("property"))
        val refs = Vector.fill(3)(_payload(fixture, active))
        val blobs = refs.map(_metadata(fixture, _))
        val inputs = order.zipWithIndex.map { case (index, ordinal) => DurableInputReference(s"input-$ordinal", DurableValue.External(refs(index))) }
        val conflict = _checkpoint(fixture, active, active.record.body.copy(inputs = inputs,
          calltreeReference = Some(refs(order.head).copy(byteSize = 999L)), retention = _deleted))
        val refused = fixture.cleanup.cleanupObserved(conflict, _access, _now)
        val intact = blobs.forall(blob => Files.exists(_path(fixture.root, blob)))
        val valid = _checkpoint(fixture, conflict, conflict.record.body.copy(calltreeReference = None))
        val cleaned = fixture.cleanup.cleanupObserved(valid, _access, _now)
        refused == OwnershipRefused && intact && cleaned == Tombstoned &&
          fixture.blob.deleted == order.distinct.map(index => blobs(index).storageRef.get)
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(12).withInitialSeed(69092L), property)
      Then("all generated plans preserve first occurrence order and all-or-nothing preflight")
      result.passed shouldBe true
    }
  }

  private val _instant = Instant.parse("2026-10-01T01:02:03Z")
  private val _now = _instant.plusSeconds(60)
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read", DurableJobRetentionPolicy.manageScope))
  private val _active = DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
  private val _deleted = _active.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(_instant.plusSeconds(30)))

  private final case class Fixture(root: Path, entity: MeasuredEntityStore, store: DurableJobStore,
    blob: MeasuredBlobStore, payloads: DurableJobOwnedBlobPayloads, cleanup: DurableJobPayloadCleanup, context: ExecutionContext)

  private final class MeasuredEntityStore extends StandardEntityStore {
    private var _blob_loads = 0
    private var _job_loads = 0
    private var _before_update: Option[() => Unit] = None
    var failBlobLoad: Boolean = false
    def blobLoads: Int = _blob_loads
    def jobLoads: Int = _job_loads
    def resetCounts(): Unit = { _blob_loads = 0; _job_loads = 0 }
    def arm(action: () => Unit): Unit = _before_update = Some(action)
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
      if (id.collection == BlobRepository.CollectionId) {
        _blob_loads += 1
        if (failBlobLoad) return Consequence.stateInvalid("private-blob-load-failure")
      }
      super.load[T](id)
    }
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] = {
      if (id.collection == DurableJobStoreEntity.Collection) _job_loads += 1
      super.loadDetached[T](id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
      (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      if (tc.id(changes).collection == DurableJobStoreEntity.Collection) {
        val action = _before_update
        _before_update = None
        action.foreach(_())
      }
      super.updateDetached(changes, expectedRevision, executionPolicy)
    }
  }

  private final class MeasuredBlobStore(delegate: BlobStore) extends BlobStore {
    private var _deleted = Vector.empty[BlobStorageRef]
    private var _get_count = 0
    var failAt: Option[Int] = None
    var throwDelete: Boolean = false
    var failGet: Boolean = false
    def name: String = delegate.name
    def deleted: Vector[BlobStorageRef] = _deleted
    def getCount: Int = _get_count
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = delegate.put(request, payload)
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] = {
      _get_count += 1
      if (failGet) Consequence.stateInvalid("private-get-failure") else delegate.get(ref)
    }
    def delete(ref: BlobStorageRef): Consequence[Unit] = {
      _deleted :+= ref
      if (failAt.contains(_deleted.size)) {
        if (throwDelete) throw new IllegalStateException("private-delete-failure")
        else Consequence.stateInvalid("private-delete-failure")
      } else delegate.delete(ref)
    }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = delegate.accessUrl(ref)
    def status(): Consequence[BlobStoreStatus] = delegate.status()
  }

  private def _fixture(): Fixture = {
    val root = Files.createTempDirectory("job-payload-cleanup")
    val entity = new MeasuredEntityStore
    val context = ExecutionContext.test()
    val store = new DurableJobStore(entity)
    val blob = new MeasuredBlobStore(LocalBlobStore(root))
    val payloads = new DurableJobOwnedBlobPayloads(store, entity, blob)
    Fixture(root, entity, store, blob, payloads, new DurableJobPayloadCleanup(store, payloads), context)
  }

  private def _payload(fixture: Fixture, active: DurableJobStoreSnapshot)(using ctx: ExecutionContext): DurableExternalReference =
    _success(fixture.payloads.putObserved(active, _access, Some("payload.bin"), ContentType.APPLICATION_OCTET_STREAM, Bag.binary(Array[Byte](1, 2, 3))))

  private def _metadata(fixture: Fixture, ref: DurableExternalReference)(using ctx: ExecutionContext): Blob =
    _success(fixture.entity.load[Blob](_success(EntityId.parse(ref.reference)))).get

  private def _path(root: Path, blob: Blob): Path = root.resolve(blob.storageRef.get.container).resolve(blob.storageRef.get.key)
  private def _sidecar(root: Path, blob: Blob): Path = {
    val path = _path(root, blob)
    path.resolveSibling(path.getFileName.toString + ".blob-meta.properties")
  }

  private def _attach_deleted(fixture: Fixture, active: DurableJobStoreSnapshot, refs: Vector[DurableExternalReference])(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _checkpoint(fixture, active, active.record.body.copy(inputs = refs.zipWithIndex.map { case (ref, index) =>
      DurableInputReference(s"input-$index", DurableValue.External(ref)) }, retention = _deleted))

  private def _checkpoint(fixture: Fixture, observed: DurableJobStoreSnapshot, body: DurableJobRecordBody)(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _success(fixture.store.checkpoint(observed, _sign(body.copy(identity = body.identity.copy(revision = observed.record.body.identity.revision + 1L,
      updatedAt = _now)), observed.record.format == DurableRecordFormat.V2), _access))

  private def _create(fixture: Fixture, record: DurableJobRecord)(using ctx: ExecutionContext): DurableJobStoreSnapshot = _success(fixture.store.create(record, _access))
  private def _load(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): DurableJobStoreSnapshot = _success(fixture.store.load(jobid, _access)).get

  private def _policy(revision: EntityRevision): EntityMutationExecutionPolicy = EntityMutationExecutionPolicy(
    concurrencyPolicy = EntityConcurrencyPolicy.Optimistic, preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired, observedRevision = Some(revision))

  private def _raw(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): EntityRevisionCarrier[DurableJobStoreEntity] =
    _success(fixture.entity.loadDetached[DurableJobStoreEntity](_success(DurableJobStoreEntity.entityId(jobid)))).get

  private def _corrupt(fixture: Fixture, observed: DurableJobStoreSnapshot)(using ctx: ExecutionContext): Unit = {
    val jobid = observed.record.body.identity.jobId
    _success(fixture.entity.updateDetached(DurableJobStoreEntity(_success(DurableJobStoreEntity.entityId(jobid)), jobid, "not-json"),
      Some(observed.providerrevision), _policy(observed.providerrevision)))
    ()
  }

  private def _tombstone(previous: DurableJobStoreSnapshot, current: DurableJobStoreSnapshot): Unit = {
    current.record.format shouldBe previous.record.format
    current.record.body.identity shouldBe previous.record.body.identity.copy(revision = previous.record.body.identity.revision + 1L, updatedAt = _now)
    current.providerrevision.value shouldBe previous.providerrevision.value + 1L
    current.record.body.retention shouldBe previous.record.body.retention.copy(deletionState = DurableDeletionState.Tombstoned,
      tombstone = Some(DurableTombstone(previous.record.body.retention.deletedAt.get, "job-owned-payload-cleanup-complete", None)))
    current.record.body.copy(identity = previous.record.body.identity, retention = previous.record.body.retention) shouldBe previous.record.body
    DurableJobRecordCodec.decode(_success(DurableJobRecordCodec.canonicalJson(current.record)), _access) shouldBe Consequence.Success(current.record)
  }

  private def _record(jobid: String, retention: DurableRetentionState = _active, status: DurableJobLifecycleStatus = DurableJobLifecycleStatus.Succeeded,
    revision: Long = 1L, v2: Boolean = false): DurableJobRecord = {
    val result = status match {
      case DurableJobLifecycleStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.Absent)
      case DurableJobLifecycleStatus.Failed => DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "failed", false))
      case DurableJobLifecycleStatus.Cancelled => DurableResultOutcome.Cancelled(None)
      case _ => DurableResultOutcome.Pending
    }
    _sign(DurableJobRecordBody(DurableJobIdentity(jobid, revision, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(status, 1, DurableRunMode.Async, DurableRetryEvidence(Vector.empty, 1, None, false, false), DurableScheduleState(None, None, None)),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component", None, "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector.empty, result, Vector.empty, Vector.empty, None,
      DurableDefinitionSnapshot("definition", "job", 1, 1L, "a" * 64, None, None, Map.empty), retention), v2)
  }

  private def _sign(body: DurableJobRecordBody, v2: Boolean): DurableJobRecord = _success(if (v2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
