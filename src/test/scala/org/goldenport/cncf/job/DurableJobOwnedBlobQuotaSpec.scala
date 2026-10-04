package org.goldenport.cncf.job

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.time.Instant
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Actual native bytes, conservative persisted reservations and acknowledged disposal.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobOwnedBlobQuotaSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import BlobRepository.given
  import DurableJobOwnedBlobPayloads.*
  import DurableJobOwnedBlobQuotaState.*
  import DurableJobCleanupFacet.*

  private val _instant = Instant.parse("2026-10-01T01:02:03Z")
  private val _now = _instant.plusSeconds(100)
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read", DurableJobRetentionPolicy.manageScope))
  private val _content = ContentType.APPLICATION_OCTET_STREAM
  private val _active = DurableRetentionState(None, None, None, DurableDeletionState.Active, None)

  "Native Job-owned Blob capacity" should {
    "bounded immutable admission" which {
      "P1 admit inclusive occupied objects and physical bytes, including a zero-byte slot" in {
        Given("a real SQLite owner and LocalBlobStore with two object slots and three payload bytes")
        _with_database(DurableJobOwnedBlobResourcePolicy(2, 3L, 3)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("inclusive"))
          When("three bytes and a zero-byte payload fill the independent inclusive limits")
          val first = _success(_put(fixture, active, Array[Byte](1, 2, 3)))
          val zero = _success(_put(fixture, active, Array.emptyByteArray))
          val before = _quota_text(fixture)
          val puts = fixture.blob.puts
          val refused = _put(fixture, active, Array.emptyByteArray)
          val usage = _success(fixture.adapter.resourceSnapshot())
          Then("actual files and sidecars exist and another zero-byte object changes neither ledger nor Blob provider")
          _physical(fixture, first) shouldBe Vector[Byte](1, 2, 3)
          _physical(fixture, zero) shouldBe Vector.empty
          Files.exists(_sidecar(fixture, _metadata(fixture, zero))) shouldBe true
          refused shouldBe a[Consequence.Failure[_]]
          fixture.blob.puts shouldBe puts
          _quota_text(fixture) shouldBe before
          usage.occupiedblobs shouldBe 2L
          usage.payloadbytes shouldBe 3L
          usage.pendingblobs shouldBe 0L
          usage.ledgerentries shouldBe 2
        }
        Given("an independent three-slot owner with only three aggregate bytes")
        _with_database(DurableJobOwnedBlobResourcePolicy(3, 3L, 3)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("bytes"))
          val first = _success(_put(fixture, active, Array[Byte](4, 5, 6)))
          val before = _quota_text(fixture)
          When("another one-byte object requests admission below the object count limit")
          val refused = _put(fixture, active, Array[Byte](7))
          Then("aggregate bytes refuse before put and retain the inclusive file and ledger")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.blob.puts shouldBe 1
          _quota_text(fixture) shouldBe before
          _physical(fixture, first) shouldBe Vector[Byte](4, 5, 6)
        }
      }

      "P2 consume one bounded stream and close success, oversize and exceptional streams before allocation" in {
        Given("a lowered three-byte payload ceiling and streams whose metadata and second-open content are untrusted")
        _with_database(DurableJobOwnedBlobResourcePolicy(4, 12L, 3)) { fixture =>
          val active = _create(fixture, _record("stream"))(using fixture.context)
          val ids = new FixedIds(_id("stream"))
          given ExecutionContext = ExecutionContext.withIdGenerationContext(fixture.context, ids)
          val bag = new MeasuredBag(Array[Byte](1, 2, 3), Array[Byte](9, 9, 9, 9, 9))
          When("an inclusive stream is detached and handed to the actual LocalBlobStore")
          val accepted = _success(fixture.adapter.putObserved(active, _access, None, _content, bag))
          Then("only the first original stream was consumed and its exact bytes were physically written")
          bag.opens shouldBe 1
          bag.closed shouldBe 1
          bag.readbytes shouldBe 3
          _physical(fixture, accepted) shouldBe Vector[Byte](1, 2, 3)
          val before = _quota_text(fixture)
          Vector(false, true).foreach { exception =>
            Given("an original stream that is oversize or fails after a bounded prefix")
            val invalid = new MeasuredBag(Array.fill[Byte](20)(7), Array[Byte](8), if (exception) Some(2) else None)
            val allocations = ids.calls
            When("bounded admission reads at most the payload ceiling plus one")
            val refused = fixture.adapter.putObserved(active, _access, None, _content, invalid)
            Then("the stream closes, no new ID or put occurs, and persisted accounting remains unchanged")
            refused shouldBe a[Consequence.Failure[_]]
            invalid.closed shouldBe 1
            invalid.opens shouldBe 1
            invalid.readbytes should be <= 4
            ids.calls shouldBe allocations
            fixture.blob.puts shouldBe 1
            _quota_text(fixture) shouldBe before
          }
          val invalidpolicies = Vector(DurableJobOwnedBlobResourcePolicy(0, 12L, 3), DurableJobOwnedBlobResourcePolicy(5, 0L, 3),
            DurableJobOwnedBlobResourcePolicy(5, 12L, 0), DurableJobOwnedBlobResourcePolicy(4097, 12L, 3),
            DurableJobOwnedBlobResourcePolicy(5, 268435457L, 3), DurableJobOwnedBlobResourcePolicy(5, 12L, 8388609))
          val adapters = invalidpolicies.map(policy => new DurableJobOwnedBlobPayloads(fixture.store, fixture.entity, fixture.blob, policy))
          adapters.foreach { adapter =>
            Given("a policy outside one of the independent positive hard ceilings")
            val invalid = new MeasuredBag(Array[Byte](1), Array[Byte](2))
            val allocations = ids.calls
            When("producer admission validates the private policy")
            val refused = adapter.putObserved(active, _access, None, _content, invalid)
            Then("validation precedes even the original stream and allocation")
            refused shouldBe a[Consequence.Failure[_]]
            invalid.opens shouldBe 0
            ids.calls shouldBe allocations
          }
          Vector((Some("x" * 1025), _content), (Some("界" * 400), _content),
            (None, ContentType.parse("x" * 513)), (None, null)).foreach { (filename, content) =>
            Given("an invalid bounded filename or requested content header")
            val invalid = new MeasuredBag(Array[Byte](1), Array[Byte](2))
            val allocations = ids.calls
            When("the original producer validates metadata before payload admission")
            val refused = fixture.adapter.putObserved(active, _access, filename, content, invalid)
            Then("the original stream, allocator and native Blob put remain untouched")
            refused shouldBe a[Consequence.Failure[_]]
            invalid.opens shouldBe 0
            ids.calls shouldBe allocations
            fixture.blob.puts shouldBe 1
          }
        }
      }

      "P3 retain signed V1/V2 Active authority and exactly the original ownership attributes" in {
        Vector(false, true).foreach { v2 =>
          Given("a current signed Active native Job in format V1 or V2")
          _with_database(DurableJobOwnedBlobResourcePolicy(4, 8L, 2)) { fixture =>
            given ExecutionContext = fixture.context
            val original = _create(fixture, _record("authority", v2))
            val current = _checkpoint(fixture, original, original.record.body)
            val inactive = _create(fixture, _record("inactive", v2, _active.copy(deletionState = DurableDeletionState.Deleted,
              deletedAt = Some(_instant))))
            val corrupt = current.copy(record = current.record.copy(integrity = current.record.integrity.copy(unsignedBodySha256 = "b" * 64)))
            When("stale, corrupt, inactive and exact foreign authorization requests precede a legitimate request")
            val rejected = Vector(original, corrupt, inactive, null).map(snapshot => _put(fixture, snapshot, Array[Byte](1))) ++
              Vector(_access.copy(tenantId = "foreign"), _access.copy(subjectId = "foreign"), _access.copy(scopes = Set.empty))
                .map(access => fixture.adapter.putObserved(current, access, None, _content, Bag.binary(Array[Byte](1))))
            val admitted = _success(_put(fixture, current, Array[Byte](1, 2)))
            val metadata = _metadata(fixture, admitted)
            Then("authority refuses before puts and admitted metadata has only the exact three bounded original labels")
            rejected.foreach(_ shouldBe a[Consequence.Failure[_]])
            fixture.blob.puts shouldBe 1
            metadata.attributes.keySet shouldBe Set(ownershipAttribute, ownerAttribute, bindingAttribute)
            metadata.attributes(ownershipAttribute) shouldBe "exclusive-v1"
            metadata.attributes(ownerAttribute) shouldBe ownerFingerprint(current.record)
            metadata.attributes(bindingAttribute) shouldBe bindingFingerprint(metadata.id, metadata.storageRef.get,
              metadata.contentType.get, metadata.byteSize.get, metadata.digest.get)
            metadata.accessUrl shouldBe BlobUrl.cncfRoute(metadata.id)
            _physical(fixture, admitted) shouldBe Vector[Byte](1, 2)
          }
        }
      }

      "P4 preserve physical occupied bytes across independent owners and refuse all persisted policy mismatches" in {
        Given("one acknowledged payload under an independently reopenable SQLite and LocalBlobStore owner")
        _with_database(DurableJobOwnedBlobResourcePolicy(1, 2L, 2)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("restart"))
          val reference = _success(_put(fixture, active, Array[Byte](1, 2)))
          val before = _quota_text(fixture)
          fixture.close()
          val policies = Vector(fixture.policy, fixture.policy.copy(maxownedblobs = 2),
            fixture.policy.copy(maxownedbytes = 3L), fixture.policy.copy(maxpayloadbytes = 1))
          policies.foreach { policy =>
            Given("fresh provider objects over the same files with an exact or mismatched private policy")
            val fresh = _open(fixture.root, policy)
            try {
              val observed = _load(fresh, "restart")(using fresh.context)
              When("the fresh producer requests another physical payload")
              val refused = _put(fresh, observed, Array[Byte](3))(using fresh.context)
              Then("no policy reset or new physical put is admitted and the original bytes survive")
              refused shouldBe a[Consequence.Failure[_]]
              fresh.blob.puts shouldBe 0
              _quota_text(fresh)(using fresh.context) shouldBe before
              _physical(fresh, reference)(using fresh.context) shouldBe Vector[Byte](1, 2)
              if (policy == fixture.policy) {
                val usage = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
                usage.productArity shouldBe 8
                usage.occupiedblobs shouldBe 1L
                usage.payloadbytes shouldBe 2L
                usage.productIterator.foreach(value => value shouldBe a[java.lang.Number])
              } else fresh.adapter.resourceSnapshot()(using fresh.context) shouldBe a[Consequence.Failure[_]]
            } finally fresh.close()
          }
        }
      }
    }

    "complete native metadata and concurrency" which {
      "P5 import all exclusive physical assets and retain charges when metadata disappears" in {
        Given("missing operational accounting beside real exclusive, protected and ordinary unowned files")
        _with_database(DurableJobOwnedBlobResourcePolicy(3, 6L, 2)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("import"))
          val owned = _seed_payload(fixture, active.record, "owned", Array[Byte](1, 2), owned = true)
          val protectedref = _seed_payload(fixture, active.record, "protected", Array[Byte](3, 4), owned = true)
          val ordinary = _seed_payload(fixture, active.record, "ordinary", Array[Byte](8, 9), owned = false)
          val changed = _checkpoint(fixture, active, active.record.body.copy(
            inputs = Vector(DurableInputReference("shared", DurableValue.External(protectedref.copy(storageKind = "blob")))),
            definitionSnapshot = active.record.body.definitionSnapshot.copy(declaredSource = Some(protectedref))))
          When("a legitimate producer imports the complete census then fills the last slot")
          val admitted = _success(_put(fixture, changed, Array[Byte](5, 6)))
          val charged = _success(fixture.adapter.resourceSnapshot())
          val original = _metadata(fixture, owned)
          _success(fixture.entity.deleteHard(original.id))
          _success(fixture.blob.delegate.delete(original.storageRef.get))
          val before = _quota_text(fixture)
          val fresh = _open(fixture.root, fixture.policy)
          try {
            val observed = _load(fresh, "import")(using fresh.context)
            val refused = _put(fresh, observed, Array[Byte](7))(using fresh.context)
            Then("exclusive protected assets count, ordinary bytes survive and absent metadata never frees the old reservation")
            charged.occupiedblobs shouldBe 3L
            charged.payloadbytes shouldBe 6L
            refused shouldBe a[Consequence.Failure[_]]
            _quota_text(fresh)(using fresh.context) shouldBe before
            _physical(fresh, protectedref)(using fresh.context) shouldBe Vector[Byte](3, 4)
            _physical(fresh, ordinary)(using fresh.context) shouldBe Vector[Byte](8, 9)
            _physical(fresh, admitted)(using fresh.context) shouldBe Vector[Byte](5, 6)
          } finally fresh.close()
        }
        Vector("partial", "nonexclusive", "binding", "owner", "external", "size", "digest", "content", "ref").foreach { mode =>
          Given("real pre-existing bytes with one malformed exclusive metadata fact: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(2, 4L, 2)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("invalid-import"))
            val reference = _seed_payload(fixture, active.record, "bad", Array[Byte](1), owned = true)
            val original = _metadata(fixture, reference)
            val changed = mode match {
              case "partial" => original.copy(attributes = original.attributes - ownerAttribute)
              case "nonexclusive" => original.copy(attributes = original.attributes.updated(ownershipAttribute, "shared"))
              case "binding" => original.copy(attributes = original.attributes.updated(bindingAttribute, "b" * 64))
              case "owner" => original.copy(attributes = original.attributes.updated(ownerAttribute, "UPPER"))
              case "external" => original.copy(externalUrl = Some("https://example.invalid/payload"))
              case "size" => original.copy(byteSize = Some(8388609L))
              case "digest" => original.copy(digest = Some("g" * 64))
              case "content" => original.copy(contentType = Some(ContentType.parse("x" * 513)))
              case _ => original.copy(storageRef = original.storageRef.map(_.copy(key = "x" * 2049)))
            }
            _replace_blob(fixture, changed)
            val raw = _raw_blob(fixture, original.id)
            When("missing-ledger bootstrap sees the complete native metadata")
            val refused = _put(fixture, active, Array[Byte](2))
            Then("malformed evidence is refused before ledger publication, put or deletion and is not repaired")
            refused shouldBe a[Consequence.Failure[_]]
            _raw_quota(fixture) shouldBe None
            _raw_blob(fixture, original.id) shouldBe raw
            fixture.blob.puts shouldBe 0
            fixture.blob.deletes shouldBe Vector.empty
            Files.readAllBytes(_path(fixture, original)).toVector shouldBe Vector[Byte](1)
          }
        }
      }

      "P6 traverse native thirty-two-row pages and refuse every injected incomplete or malformed census" in {
        Given("forty mixed real metadata rows and actual LocalBlobStore files across a native page boundary")
        _with_database(DurableJobOwnedBlobResourcePolicy(32, 32L, 1)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("pages"))
          val seeded = Vector.tabulate(40)(index => _seed_payload(fixture, active.record, s"row_$index", Array[Byte](1), index % 2 == 0))
          When("the complete bounded native inventory is imported before one additional physical payload")
          val admitted = _success(_put(fixture, active, Array[Byte](2)))
          val usage = _success(fixture.adapter.resourceSnapshot())
          Then("native ascending id and explicit pages count all owned rows while leaving ordinary bytes alone")
          usage.occupiedblobs shouldBe 21L
          fixture.provider.requests.exists(_.offset == 32) shouldBe true
          fixture.provider.requests.foreach { request =>
            request.query shouldBe Query.Empty
            request.order shouldBe QueryOrder.By("id", OrderDirection.Asc)
            request.limit shouldBe QueryLimit.Limit(32)
          }
          _physical(fixture, seeded.last) shouldBe Vector[Byte](1)
          _physical(fixture, admitted) shouldBe Vector[Byte](2)
        }
        Vector("oversupply", "duplicate", "unordered", "wrong-id", "bad-row", "cursor", "range").foreach { mode =>
          Given("forty real native Blob rows with a measured native search fault: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(32, 32L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("bad-pages"))
            val refs = Vector.tabulate(40)(index => _seed_payload(fixture, active.record, s"bad_$index", Array[Byte](1), index % 2 == 0))
            fixture.provider.censusmode = mode
            When("producer bootstrap requires every native page to be complete and canonical")
            val refused = _put(fixture, active, Array[Byte](2))
            Then("no truncation, generic EntityStore search, put, delete or ledger publication is accepted")
            refused shouldBe a[Consequence.Failure[_]]
            _raw_quota(fixture) shouldBe None
            fixture.entity.genericsearches shouldBe 0
            fixture.blob.puts shouldBe 0
            fixture.blob.deletes shouldBe Vector.empty
            _physical(fixture, refs.head) shouldBe Vector[Byte](1)
          }
        }
        Given("a complete actual native SQLite census containing 32769 metadata rows including ordinary rows and real owned bytes")
        _with_database(DurableJobOwnedBlobResourcePolicy(2, 2L, 1)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("sentinel"))
          val owned = _seed_payload(fixture, active.record, "sentinel_owned", Array[Byte](1), owned = true)
          _seed_payload(fixture, active.record, "sentinel_ordinary", Array[Byte](9), owned = false)
          _fill_census(fixture, 32767)
          When("the bounded inventory reaches the final single-row sentinel")
          val refused = _put(fixture, active, Array[Byte](2))
          Then("oversupply beyond the inclusive total-row ceiling refuses without physical or quota effects")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.provider.requests.last.offset shouldBe 32768
          fixture.provider.requests.last.limit shouldBe QueryLimit.Limit(1)
          _raw_quota(fixture) shouldBe None
          fixture.blob.puts shouldBe 0
          fixture.blob.deletes shouldBe Vector.empty
          _physical(fixture, owned) shouldBe Vector[Byte](1)
        }
      }

      "P7 race independent native bootstrap and the last count or byte slot without overcommitting" in {
        Vector("bootstrap", "count", "bytes").foreach { mode =>
          Given("two independently opened actual provider owners competing at " + mode)
          val policy = if (mode == "bootstrap") DurableJobOwnedBlobResourcePolicy(1, 1L, 1)
            else if (mode == "count") DurableJobOwnedBlobResourcePolicy(2, 3L, 1)
            else DurableJobOwnedBlobResourcePolicy(3, 2L, 1)
          _with_database(policy) { fixture =>
            val active = _create(fixture, _record("race"))(using fixture.context)
            if (mode != "bootstrap") _success(_put(fixture, active, Array[Byte](8))(using fixture.context))
            val fresh = _open(fixture.root, policy)
            try {
              val other = _load(fresh, "race")(using fresh.context)
              val ready = new CountDownLatch(2)
              val proceed = new CountDownLatch(1)
              val barrier = () => { ready.countDown(); _await(proceed) }
              if (mode == "bootstrap") {
                fixture.entity.beforequotacreate = Some(barrier)
                fresh.entity.beforequotacreate = Some(barrier)
              } else {
                fixture.entity.beforequota = Some(barrier)
                fresh.entity.beforequota = Some(barrier)
              }
              When("both native writers observe the same available capacity behind bounded barriers")
              val results = _race(Vector(
                () => _put(fixture, active, Array[Byte](1))(using fixture.context),
                () => _put(fresh, other, Array[Byte](2))(using fresh.context)), ready, proceed)
              Then("exactly one new physical payload is admitted and the winner's persisted policy is retained")
              results.count(_.isSuccess) shouldBe 1
              fixture.blob.puts + fresh.blob.puts shouldBe (if (mode == "bootstrap") 1 else 2)
              val usage = _success(fixture.adapter.resourceSnapshot()(using fixture.context))
              usage.occupiedblobs shouldBe (if (mode == "bootstrap") 1L else 2L)
              usage.payloadbytes shouldBe usage.occupiedblobs
              usage.maxownedblobs shouldBe policy.maxownedblobs
              results.flatMap(_.toOption).foreach(ref => _physical(fixture, ref)(using fixture.context).size shouldBe 1)
            } finally fresh.close()
          }
        }
      }

      "P8 serialize concurrent releases and admission, bound stale retries and permanently refuse released ID reuse" in {
        Given("two real acknowledged objects and three independent owners at the occupied ceiling")
        _with_database(DurableJobOwnedBlobResourcePolicy(2, 2L, 1)) { fixture =>
          val active = _create(fixture, _record("release-race"))(using fixture.context)
          val refs = Vector.fill(2)(_success(_put(fixture, active, Array[Byte](1))(using fixture.context)))
          val body = _signed(active.record.body.copy(inputs = refs.zipWithIndex.map { (ref, index) =>
            DurableInputReference(s"p$index", DurableValue.External(ref)) }))
          val owners = Vector.fill(2)(_open(fixture.root, fixture.policy))
          try {
            val targets = owners.map(owner => _success(owner.adapter.prepare(body)(using owner.context)))
            val ready = new CountDownLatch(3)
            val proceed = new CountDownLatch(1)
            (fixture +: owners).foreach(owner => owner.entity.beforequotaread = Some(() => { ready.countDown(); _await(proceed) }))
            When("two exact target releases and one new reservation compete through real native CAS")
            val actions: Vector[() => Consequence[Any]] = Vector(
              () => owners(0).adapter.delete(targets(0)(0))(using owners(0).context),
              () => owners(1).adapter.delete(targets(1)(1))(using owners(1).context),
              () => _put(fixture, active, Array[Byte](2))(using fixture.context))
            val results = _race(actions, ready, proceed)
            Then("releases remain exact and any admitted bytes fit the actual inclusive capacity")
            results.take(2).foreach(_.isSuccess shouldBe true)
            val usage = _success(fixture.adapter.resourceSnapshot()(using fixture.context))
            usage.releasedblobs shouldBe 2L
            usage.occupiedblobs should be <= 1L
            usage.payloadbytes shouldBe usage.occupiedblobs
            refs.foreach(ref => Files.exists(_path(fixture, _metadata(fixture, ref)(using fixture.context))) shouldBe false)
            val oldid = _success(EntityId.parse(refs.head.reference))
            _success(fixture.entity.deleteHard(oldid)(using fixture.context))
            val fixed = ExecutionContext.withIdGenerationContext(fixture.context, new FixedIds(oldid))
            Given("released accounting survives after the old metadata is removed")
            val before = _quota_text(fixture)(using fixture.context)
            val puts = fixture.blob.puts
            When("the trusted allocator is deliberately made to request the old canonical ID")
            val refused = _put(fixture, active, Array[Byte](3))(using fixed)
            Then("the surviving release marker prevents ID reuse without another put or ledger change")
            refused shouldBe a[Consequence.Failure[_]]
            fixture.blob.puts shouldBe puts
            _quota_text(fixture)(using fixture.context) shouldBe before
          } finally owners.foreach(_.close())
        }
        Vector("retry", "exhaust", "nonstale").foreach { mode =>
          Given("an existing native singleton and an injected optimistic boundary: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(3, 3L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("retry-bound"))
            _success(_put(fixture, active, Array[Byte](1)))
            val before = _quota_text(fixture)
            val calls = fixture.entity.quotacalls
            fixture.entity.stalecount = if (mode == "retry") 2 else if (mode == "exhaust") 20 else 0
            fixture.entity.failquota = mode == "nonstale"
            When("a new reservation crosses that exact native update boundary")
            val result = _put(fixture, active, Array[Byte](2))
            Then("only stale refusals retry and failed admission never publishes physical payloads")
            if (mode == "retry") {
              result.isSuccess shouldBe true
              fixture.entity.quotacalls - calls shouldBe 4
              _physical(fixture, _success(result)) shouldBe Vector[Byte](2)
            } else {
              result shouldBe a[Consequence.Failure[_]]
              fixture.entity.quotacalls - calls shouldBe (if (mode == "exhaust") 8 else 1)
              fixture.blob.puts shouldBe 1
              _quota_text(fixture) shouldBe before
            }
          }
        }
      }
    }
  }
  "Native publication and disposal authority" should {
    "conservative producer acknowledgement" which {
      "P9 preserve Pending charge after put failure, lost response and a suspended writer across fresh owners" in {
        Vector("before", "throw-before", "lost", "suspended").foreach { mode =>
          Given("one persisted reservation crossing an actual LocalBlobStore put boundary: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(1, 1L, 1)) { fixture =>
            val active = _create(fixture, _record("ambiguous"))(using fixture.context)
            fixture.blob.putmode = if (mode == "suspended") "normal" else mode
            val ready = new CountDownLatch(1)
            val proceed = new CountDownLatch(1)
            val outcome = new AtomicReference[Consequence[DurableExternalReference]]()
            if (mode == "suspended") fixture.blob.beforeput = Some(() => { ready.countDown(); _await(proceed) })
            val worker = new Thread(() => outcome.set(_put(fixture, active, Array[Byte](1))(using fixture.context)), "suspended-blob-writer")
            When("the first writer fails ambiguously or pauses after its native reservation and a fresh owner requests the slot")
            worker.start()
            var fresh: Option[Fixture] = None
            try {
              if (mode == "suspended") _await(ready) else _join(Vector(worker))
              val owner = _open(fixture.root, fixture.policy)
              fresh = Some(owner)
              val current = _load(owner, "ambiguous")(using owner.context)
              val before = _quota_text(owner)(using owner.context)
              val usage = _success(owner.adapter.resourceSnapshot()(using owner.context))
              val refused = _put(owner, current, Array[Byte](2))(using owner.context)
              Then("the exact Pending count and bytes survive absent metadata and prevent a second physical write")
              usage.occupiedblobs shouldBe 1L
              usage.payloadbytes shouldBe 1L
              usage.pendingblobs shouldBe 1L
              _ledger(owner)(using owner.context).entries.head.token.get should fullyMatch regex "[0-9a-f]{32}"
              refused shouldBe a[Consequence.Failure[_]]
              owner.blob.puts shouldBe 0
              _quota_text(owner)(using owner.context) shouldBe before
              if (mode == "lost") {
                val issued = fixture.blob.issued.head
                Files.exists(_issued_path(fixture, issued)) shouldBe true
                Files.exists(_issued_sidecar(fixture, issued)) shouldBe true
                _success(owner.blob.delegate.get(issued.storageRef)).byteSize shouldBe 1L
              } else fixture.blob.issued shouldBe Vector.empty
              proceed.countDown(); _join(Vector(worker))
              if (mode == "suspended") {
                outcome.get().isSuccess shouldBe true
                _physical(owner, _success(outcome.get()))(using owner.context) shouldBe Vector[Byte](1)
              } else {
                outcome.get() shouldBe a[Consequence.Failure[_]]
                fixture.blob.deletes shouldBe Vector.empty
              }
            } finally { proceed.countDown(); _join(Vector(worker)); fresh.foreach(_.close()) }
          }
        }
      }

      "P10 distinguish failed rollback deletion from refused or committed release CAS without changing publication refusal" in {
        Vector("create", "readback", "current", "confirm", "rollback-before", "rollback-lost", "release-before", "release-lost").foreach { mode =>
          Given("a valid fresh put with a publication or rollback boundary failure: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(1, 1L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("rollback"))
            _create(fixture, _record("next"))
            val unrelated = _seed_payload(fixture, active.record, "unrelated", Array[Byte](9), owned = false)
            fixture.entity.failblobcreate = mode != "readback" && mode != "current" && mode != "confirm"
            fixture.entity.failconfirm = mode == "confirm"
            fixture.blob.afterput = Some(() => {
              fixture.entity.failblobload = mode == "readback"
              fixture.entity.failcurrent = mode == "current"
            })
            fixture.blob.deletemode = if (mode == "rollback-before") "before" else if (mode == "rollback-lost") "lost" else "normal"
            fixture.entity.releasefault = if (mode == "release-before") "before" else if (mode == "release-lost") "lost" else "normal"
            When("publication refuses and the adapter rolls back only its proven newly allocated handle")
            val result = _put(fixture, active, Array[Byte](1))
            fixture.entity.failblobload = false
            fixture.entity.failcurrent = false
            val issued = fixture.blob.issued.head
            val occupied = Set("rollback-before", "rollback-lost", "release-before").contains(mode)
            val fresh = _open(fixture.root, fixture.policy)
            try {
              val usage = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
              val persisted = _ledger(fresh)(using fresh.context).entries.head
              val next = _put(fresh, _load(fresh, "next")(using fresh.context), Array[Byte](2))(using fresh.context)
              Then("current-call refusal, physical evidence and durable accounting each retain their exact independent outcome")
              result shouldBe a[Consequence.Failure[_]]
              _message(result) should include("Durable Job-owned payload refused")
              _message(result) should not include "private-"
              fixture.blob.deletes shouldBe Vector(issued.storageRef)
              Files.exists(_issued_path(fixture, issued)) shouldBe (mode == "rollback-before")
              Files.exists(_issued_sidecar(fixture, issued)) shouldBe (mode == "rollback-before")
              usage.occupiedblobs shouldBe (if (occupied) 1L else 0L)
              usage.payloadbytes shouldBe usage.occupiedblobs
              persisted.state shouldBe (if (occupied) Pending else Released)
              persisted.token.nonEmpty shouldBe true
              next.isSuccess shouldBe !occupied
              _physical(fresh, unrelated)(using fresh.context) shouldBe Vector[Byte](9)
              fixture.store.load("rollback", _access) shouldBe Consequence.Success(Some(active))
              if (Set("readback", "current", "confirm").contains(mode)) {
                _success(fresh.entity.load[Blob](issued.id)(using summon[EntityPersistent[Blob]], fresh.context)).nonEmpty shouldBe true
                persisted.bindingsha256.nonEmpty shouldBe true
              }
            } finally fresh.close()
          }
        }
      }

      "P11 refuse forged acknowledgements with exact original rollback authority and preserve valid digest spelling" in {
        Vector("null", "foreign-id", "foreign-store", "null-ref", "size", "digest", "type", "uppercase").foreach { mode =>
          Given("actual local bytes whose provider acknowledgement is transformed at " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(1, 1L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("ack"))
            fixture.blob.transform = value => mode match {
              case "null" => null
              case "foreign-id" => value.copy(id = _id("foreign"))
              case "foreign-store" => value.copy(storageRef = value.storageRef.copy(store = "foreign"))
              case "null-ref" => value.copy(storageRef = null)
              case "size" => value.copy(byteSize = 2L)
              case "digest" => value.copy(digest = "b" * 64)
              case "type" => value.copy(contentType = ContentType.TEXT_PLAIN)
              case _ => value.copy(digest = value.digest.toUpperCase(java.util.Locale.ROOT))
            }
            When("the adapter requires exact size, requested content type and case-insensitive SHA content proof")
            val result = _put(fixture, active, Array[Byte](1))
            val issued = fixture.blob.issued.head
            val rollback = Set("size", "digest", "type").contains(mode)
            val fresh = _open(fixture.root, fixture.policy)
            try {
              val usage = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
              Then("forged references never publish, invalid handles never delete, and valid uppercase digest retains its exact binding")
              if (mode == "uppercase") {
                val reference = _success(result)
                val blob = _metadata(fresh, reference)(using fresh.context)
                reference.sha256 shouldBe issued.digest.toUpperCase(java.util.Locale.ROOT)
                blob.digest shouldBe Some(reference.sha256)
                blob.attributes(bindingAttribute) shouldBe bindingFingerprint(blob.id, blob.storageRef.get, blob.contentType.get, 1L, reference.sha256)
                _ledger(fresh)(using fresh.context).entries.head.payloadsha256 shouldBe issued.digest
                _physical(fresh, reference)(using fresh.context) shouldBe Vector[Byte](1)
                usage.pendingblobs shouldBe 0L
              } else {
                result shouldBe a[Consequence.Failure[_]]
                _success(fresh.entity.load[Blob](issued.id)(using summon[EntityPersistent[Blob]], fresh.context)) shouldBe None
                fixture.blob.deletes shouldBe (if (rollback) Vector(issued.storageRef) else Vector.empty)
                Files.exists(_issued_path(fixture, issued)) shouldBe !rollback
                Files.exists(_issued_sidecar(fixture, issued)) shouldBe !rollback
                usage.occupiedblobs shouldBe (if (rollback) 0L else 1L)
                usage.pendingblobs shouldBe usage.occupiedblobs
              }
            } finally fresh.close()
          }
        }
      }
    }

    "whole-plan cleanup and retained audit evidence" which {
      "P12 refuse a corrupt last target, inconsistent quota fact and protected overlaps before any deletion" in {
        Vector("metadata", "quota", "shared", "definition", "ordinary-only").foreach { mode =>
          Given("two real owned physical payloads in a signed Deleted Job with a whole-plan condition: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(2, 2L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("preflight"))
            val refs = Vector.fill(2)(_success(_put(fixture, active, Array[Byte](1))))
            val deleted = _deleted(fixture, active, refs)
            val observed = mode match {
              case "shared" => _checkpoint(fixture, deleted, deleted.record.body.copy(inputs = deleted.record.body.inputs :+
                DurableInputReference("shared", DurableValue.External(refs.last.copy(storageKind = "blob")))))
              case "definition" => _checkpoint(fixture, deleted, deleted.record.body.copy(
                definitionSnapshot = deleted.record.body.definitionSnapshot.copy(declaredSource = Some(refs.last))))
              case "ordinary-only" => _checkpoint(fixture, deleted, deleted.record.body.copy(inputs =
                refs.zipWithIndex.map { (ref, index) => DurableInputReference(s"ordinary-$index", DurableValue.External(ref.copy(storageKind = "blob"))) }))
              case _ => deleted
            }
            if (mode == "metadata") {
              val last = _metadata(fixture, refs.last)
              _replace_blob(fixture, last.copy(attributes = last.attributes.updated(bindingAttribute, "b" * 64)))
            }
            if (mode == "quota") {
              val ledger = _ledger(fixture)
              val last = ledger.entries.last
              _replace_quota(fixture, _success(DurableJobOwnedBlobQuotaCodec.encode(ledger.copy(entries =
                ledger.entries.dropRight(1) :+ last.copy(ownersha256 = "b" * 64)))))
            }
            val before = _quota_text(fixture)
            val searches = fixture.provider.requests.size
            When("cleanup must admit every original typed reference and exact quota fact before its first delete")
            val result = fixture.cleanup.cleanupObserved(observed, _access, _now)
            Then("a bad final target refuses the whole plan and ordinary-only cleanup keeps its no-Blob-provider tombstone path")
            result shouldBe (if (mode == "ordinary-only") Tombstoned else OwnershipRefused)
            fixture.blob.deletes shouldBe Vector.empty
            _quota_text(fixture) shouldBe before
            refs.foreach(ref => Files.exists(_path(fixture, _metadata(fixture, ref))) shouldBe true)
            if (mode == "ordinary-only") {
              fixture.provider.requests.size shouldBe searches
              _tombstone(observed, _load(fixture, "preflight"))
            } else fixture.store.load("preflight", _access) shouldBe Consequence.Success(Some(observed))
          }
        }
      }

      "P13 release exact physical count and bytes, retain metadata and references, and never decrement twice" in {
        Vector(false, true).foreach { v2 =>
          Given("a signed Deleted V1 or V2 Job containing two actual acknowledged payloads")
          _with_database(DurableJobOwnedBlobResourcePolicy(2, 3L, 2)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("dispose", v2))
            val refs = Vector(_success(_put(fixture, active, Array[Byte](1, 2))), _success(_put(fixture, active, Array[Byte](3))))
            val metadata = refs.map(_metadata(fixture, _))
            val deleted = _deleted(fixture, active, refs)
            When("whole cleanup acknowledges physical disposal and exact durable releases then checkpoints the tombstone")
            val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
            val tombstoned = _load(fixture, "dispose")
            val fresh = _open(fixture.root, fixture.policy)
            try {
              val targets = _success(fresh.adapter.prepare(tombstoned.record)(using fresh.context))
              val retry = targets.map(target => fresh.adapter.delete(target)(using fresh.context))
              val usage = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
              val newjob = _create(fresh, _record("new-payload"))(using fresh.context)
              val admitted = _put(fresh, newjob, Array[Byte](4, 5))(using fresh.context)
              Then("files and sidecars disappear, audit records remain, signed tombstone fields alone advance and freed bytes admit new payload")
              result shouldBe Tombstoned
              _tombstone(deleted, tombstoned)
              metadata.foreach { blob =>
                Files.exists(_path(fixture, blob)) shouldBe false
                Files.exists(_sidecar(fixture, blob)) shouldBe false
                _success(fresh.entity.load[Blob](blob.id)(using summon[EntityPersistent[Blob]], fresh.context)) shouldBe Some(blob)
              }
              tombstoned.record.body.inputs shouldBe deleted.record.body.inputs
              retry.foreach(_.isSuccess shouldBe true)
              fresh.blob.deletes.size shouldBe 2
              usage.occupiedblobs shouldBe 0L
              usage.payloadbytes shouldBe 0L
              usage.releasedblobs shouldBe 2L
              admitted.isSuccess shouldBe true
              _physical(fresh, _success(admitted))(using fresh.context) shouldBe Vector[Byte](4, 5)
              fresh.cleanup.cleanupObserved(tombstoned, _access, _now)(using fresh.context) shouldBe AlreadyTombstoned
              fixture.cleanup.cleanupObserved(deleted, _access, _now) shouldBe Stale
            } finally fresh.close()
          }
        }
      }

      "P14 keep Deleted on partial or ambiguous disposal, honor durable Released after lost CAS response and complete a fresh retry" in {
        Vector("delete-before", "delete-lost", "release-before", "release-lost", "acknowledged").foreach { mode =>
          Given("two exact owned payloads and a separate Active producer crossing cleanup boundary " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(2, 2L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("partial"))
            _create(fixture, _record("next"))
            val refs = Vector.fill(2)(_success(_put(fixture, active, Array[Byte](1))))
            val blobs = refs.map(_metadata(fixture, _))
            val deleted = _deleted(fixture, active, refs)
            if (mode == "delete-before") fixture.blob.faildeleteat = Some(2)
            if (mode == "delete-lost") fixture.blob.deletemode = "lost"
            if (mode.startsWith("release")) fixture.blob.afterdelete = Some(() =>
              fixture.entity.releasefault = if (mode == "release-before") "before" else "lost")
            fixture.blob.failget = true
            When("cleanup crosses physical deletion and native release as separate acknowledgement boundaries")
            val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
            val fresh = _open(fixture.root, fixture.policy)
            try {
              fresh.blob.failget = true
              val usage = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
              val ledger = _ledger(fresh)(using fresh.context)
              val beforefiles = blobs.map(blob => Files.exists(_path(fixture, blob)) -> Files.exists(_sidecar(fixture, blob)))
              val beforejob = _load(fresh, "partial")(using fresh.context)
              val next = _put(fresh, _load(fresh, "next")(using fresh.context), Array[Byte](2))(using fresh.context)
              val retry = fresh.cleanup.cleanupObserved(beforejob, _access, _now)(using fresh.context)
              val completed = _load(fresh, "partial")(using fresh.context)
              val after = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
              Then("failed calls preserve the original signed Deleted fact and only persisted Released entries free capacity")
              result shouldBe (if (mode == "acknowledged") Tombstoned else PayloadDeleteRefused)
              if (mode != "acknowledged") beforejob shouldBe deleted
              val occupied = if (Set("delete-lost", "release-before").contains(mode)) 2L
                else if (mode == "acknowledged") 0L else 1L
              usage.occupiedblobs shouldBe occupied
              usage.payloadbytes shouldBe occupied
              ledger.entries.count(_.state == Released) shouldBe (2L - occupied).toInt
              beforefiles shouldBe (if (mode == "acknowledged") Vector(false -> false, false -> false)
                else Vector(false -> false, true -> true))
              next.isSuccess shouldBe (occupied < 2L)
              retry shouldBe (if (mode == "acknowledged") AlreadyTombstoned else Tombstoned)
              if (mode != "acknowledged") {
                fresh.blob.deletes shouldBe blobs.map(_.storageRef.get)
                _tombstone(deleted, completed)
              }
              after.releasedblobs shouldBe 2L
              after.occupiedblobs shouldBe (if (next.isSuccess) 1L else 0L)
              blobs.foreach { blob =>
                Files.exists(_path(fixture, blob)) shouldBe false
                Files.exists(_sidecar(fixture, blob)) shouldBe false
                _success(fresh.entity.load[Blob](blob.id)(using summon[EntityPersistent[Blob]], fresh.context)) shouldBe Some(blob)
              }
              completed.record.body.inputs shouldBe deleted.record.body.inputs
            } finally fresh.close()
          }
        }
      }

      "P15 retain released capacity after a stale or failed Job checkpoint and never let suspended confirmation revive disposed bytes" in {
        Vector("stale", "failed").foreach { mode =>
          Given("a real Deleted Job whose checkpoint boundary is changed after acknowledged payload release: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(1, 1L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("checkpoint"))
            val reference = _success(_put(fixture, active, Array[Byte](1)))
            val blob = _metadata(fixture, reference)
            val deleted = _deleted(fixture, active, Vector(reference))
            var newer = deleted
            fixture.entity.beforejob = Some(() => {
              if (mode == "stale") newer = _checkpoint(fixture, deleted, deleted.record.body.copy(
                diagnostics = Vector(DurableDiagnosticSummary("execution", "newer", "info", "newer fact"))))
              else fixture.entity.failjob = true
            })
            When("cleanup acknowledges physical deletion and release but its final native Job checkpoint cannot commit")
            val result = fixture.cleanup.cleanupObserved(deleted, _access, _now)
            fixture.entity.failjob = false
            val usage = _success(fixture.adapter.resourceSnapshot())
            Then("the original or newer signed Job remains and physical release is never resurrected by checkpoint failure")
            result shouldBe CheckpointRefused
            _load(fixture, "checkpoint") shouldBe newer
            usage.occupiedblobs shouldBe 0L
            usage.releasedblobs shouldBe 1L
            Files.exists(_path(fixture, blob)) shouldBe false
            fixture.cleanup.cleanupObserved(newer, _access, _now) shouldBe Tombstoned
          }
        }
        Given("a valid producer suspended immediately before native quota confirmation after exact metadata publication")
        _with_database(DurableJobOwnedBlobResourcePolicy(1, 1L, 1)) { fixture =>
          val active = _create(fixture, _record("suspended-confirm"))(using fixture.context)
          val ready = new CountDownLatch(1)
          val proceed = new CountDownLatch(1)
          val outcome = new AtomicReference[Consequence[DurableExternalReference]]()
          fixture.entity.beforeconfirm = Some(() => { ready.countDown(); _await(proceed) })
          val worker = new Thread(() => outcome.set(_put(fixture, active, Array[Byte](1))(using fixture.context)), "suspended-confirm-fixture")
          worker.start()
          var owner: Option[Fixture] = None
          try {
            _await(ready)
            val fresh = _open(fixture.root, fixture.policy); owner = Some(fresh)
            val issued = fixture.blob.issued.head
            val ref = DurableExternalReference(issued.id.value, storageKind, Some(issued.contentType.header), issued.byteSize, issued.digest)
            val deleted = _deleted(fresh, _load(fresh, "suspended-confirm")(using fresh.context), Vector(ref))(using fresh.context)
            When("a fresh native owner cleans up the exact payload before the old confirmation writer resumes")
            val cleaned = fresh.cleanup.cleanupObserved(deleted, _access, _now)(using fresh.context)
            proceed.countDown(); _join(Vector(worker))
            val usage = _success(fresh.adapter.resourceSnapshot()(using fresh.context))
            Then("the stale confirmation refuses, Released remains authoritative and neither bytes nor metadata are recreated")
            cleaned shouldBe Tombstoned
            outcome.get() shouldBe a[Consequence.Failure[_]]
            usage.occupiedblobs shouldBe 0L
            usage.releasedblobs shouldBe 1L
            _ledger(fresh)(using fresh.context).entries.head.state shouldBe Released
            Files.exists(_issued_path(fixture, issued)) shouldBe false
            _success(fresh.entity.load[Blob](issued.id)(using summon[EntityPersistent[Blob]], fresh.context)).nonEmpty shouldBe true
          } finally { proceed.countDown(); _join(Vector(worker)); owner.foreach(_.close()) }
        }
      }
    }
  }

  "Closed native accounting authority" should {
    "surviving wire and historical bounds" which {
      "P16 refuse malformed surviving ledgers byte-identically before physical writes and admit the inclusive closed wire" in {
        val variants = Vector("syntax", "version", "unknown", "null", "primitive", "missing", "duplicate-key", "duplicate-entry-key",
          "duplicate-id", "unordered-id", "bad-id", "bad-owner", "bad-digest", "bad-content", "negative-bytes", "over-payload",
          "overflow", "fraction", "policy", "policy-type", "usage", "pending-binding", "pending-token", "published-binding", "bad-binding", "released-pair",
          "unknown-state", "duplicate-token", "bad-token", "unknown-entry", "missing-entry", "null-entry", "entry-shape", "deep", "oversize")
        variants.foreach { variant =>
          Given("two actual acknowledged files beside a surviving invalid closed ledger: " + variant)
          _with_database(DurableJobOwnedBlobResourcePolicy(3, 6L, 2)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("invalid-ledger"))
            val refs = Vector.fill(2)(_success(_put(fixture, active, Array[Byte](1))))
            val invalid = _closed_variant(_quota_text(fixture), variant)
            _replace_quota(fixture, invalid)
            val puts = fixture.blob.puts
            val calls = fixture.entity.quotacalls
            When("an otherwise authorized producer and strict operational decoder encounter the invalid persisted authority")
            val decoded = DurableJobOwnedBlobQuotaCodec.decode(invalid)
            val refused = _put(fixture, active, Array[Byte](2))
            val fresh = _open(fixture.root, fixture.policy)
            try {
              val reopened = fresh.adapter.resourceSnapshot()(using fresh.context)
              Then("neither bootstrap, normalization, repair nor physical mutation replaces the invalid native text")
              decoded shouldBe a[Consequence.Failure[_]]
              refused shouldBe a[Consequence.Failure[_]]
              reopened shouldBe a[Consequence.Failure[_]]
              _quota_text(fresh)(using fresh.context) shouldBe invalid
              fixture.blob.puts shouldBe puts
              fixture.blob.deletes shouldBe Vector.empty
              fixture.entity.quotacalls shouldBe calls
              refs.foreach(ref => _physical(fresh, ref)(using fresh.context) shouldBe Vector[Byte](1))
              _message(refused) should not include "canonicalJson"
              _message(refused) should not include fixture.root.toString
            } finally fresh.close()
          }
        }
        Given("a valid closed ledger with inclusive 16MiB UTF-8 whitespace and an actual physical payload")
        _with_database(DurableJobOwnedBlobResourcePolicy(2, 2L, 1)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("inclusive-wire"))
          val ref = _success(_put(fixture, active, Array[Byte](1)))
          val original = _quota_text(fixture)
          val padded = original + (" " * (16777216 - original.length))
          _replace_quota(fixture, padded)
          When("the decoder admits the inclusive wire and a producer emits the next canonical ledger")
          val decoded = DurableJobOwnedBlobQuotaCodec.decode(padded)
          val accepted = _put(fixture, active, Array[Byte](2))
          val generated = _quota_text(fixture)
          val regenerated = decoded.flatMap(DurableJobOwnedBlobQuotaCodec.encode).flatMap(DurableJobOwnedBlobQuotaCodec.decode)
          Then("closed fields and immutable entries round-trip while generated wires receive the same bounded strict validation")
          padded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length shouldBe 16777216
          decoded.isSuccess shouldBe true
          regenerated shouldBe decoded
          accepted.isSuccess shouldBe true
          generated.length should be <= 16777216
          _physical(fixture, ref) shouldBe Vector[Byte](1)
          _physical(fixture, _success(accepted)) shouldBe Vector[Byte](2)
          _ledger(fixture).entries.map(_.state) shouldBe Vector(Published, Published)
        }
      }

      "P17 retain the inclusive historical entry ceiling independently of occupied capacity and refuse marker eviction" in {
        Given("one real released physical payload and 16383 additional valid retained native release markers")
        _with_database(DurableJobOwnedBlobResourcePolicy(1, 1L, 1)) { fixture =>
          given ExecutionContext = fixture.context
          val active = _create(fixture, _record("history"))
          val reference = _success(_put(fixture, active, Array[Byte](1)))
          val blob = _metadata(fixture, reference)
          val record = _signed(active.record.body.copy(inputs = Vector(DurableInputReference("owned", DurableValue.External(reference)))))
          val target = _success(fixture.adapter.prepare(record)).head
          _success(fixture.adapter.delete(target))
          val retained = _ledger(fixture).entries.head
          val generated = Vector.tabulate(16383)(index => DurableJobOwnedBlobQuotaEntry(_id(f"released_$index%05d").value,
            "a" * 64, _content.header, 1L, "b" * 64, None, Released, Some("c" * 64)))
          val ledger = DurableJobOwnedBlobQuotaRecord(fixture.policy, (generated :+ retained).sortBy(_.blobid))
          val canonical = _success(DurableJobOwnedBlobQuotaCodec.encode(ledger))
          _replace_quota(fixture, canonical)
          val puts = fixture.blob.puts
          When("an actual producer requests a new physical ID while occupied count and bytes are zero")
          val usage = _success(fixture.adapter.resourceSnapshot())
          val refused = _put(fixture, active, Array[Byte](2))
          val over = ledger.copy(entries = (ledger.entries :+ retained.copy(blobid = _id("extra").value, token = None)).sortBy(_.blobid))
          Then("bounded history refuses admission and encoding above the inclusive ceiling without discarding any release marker")
          usage.occupiedblobs shouldBe 0L
          usage.payloadbytes shouldBe 0L
          usage.releasedblobs shouldBe 16384L
          usage.ledgerentries shouldBe 16384
          refused shouldBe a[Consequence.Failure[_]]
          fixture.blob.puts shouldBe puts
          _quota_text(fixture) shouldBe canonical
          DurableJobOwnedBlobQuotaCodec.encode(over) shouldBe a[Consequence.Failure[_]]
          Files.exists(_path(fixture, blob)) shouldBe false
          _metadata(fixture, reference) shouldBe blob
        }
      }
    }

    "provider ownership and generated finite operations" which {
      "P18 refuse unsupported and split native Job, Blob or quota ownership even with equal physical SQLite paths" in {
        Vector("nonsearchable", "non-native-cas", "split-job", "split-blob", "split-quota").foreach { mode =>
          Given("an actual owned payload and an unsupported or independently routed native owner: " + mode)
          _with_database(DurableJobOwnedBlobResourcePolicy(2, 2L, 1)) { fixture =>
            given ExecutionContext = fixture.context
            val active = _create(fixture, _record("routing"))
            val reference = _success(_put(fixture, active, Array[Byte](1)))
            val deleted = _deleted(fixture, active, Vector(reference))
            val next = _create(fixture, _record("next"))
            val before = _quota_text(fixture)
            val calls = fixture.entity.quotacalls
            val separate = new ObservedDataStore(SqlDataStore.sqlite(fixture.root.resolve("jobs.db").toString))
            try {
              mode match {
                case "nonsearchable" => fixture.context.dataStoreSpace.useDataStore(new OpaqueDataStore(fixture.provider))
                case "non-native-cas" => fixture.provider.nativecas = false
                case _ =>
                  val split = if (mode == "split-job") DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
                    else if (mode == "split-blob") _blob_collection else _quota_collection
                  fixture.provider.accept = _ != split
                  separate.accept = _ == split
                  fixture.context.dataStoreSpace.useDataStore(fixture.provider).addDataStore(separate)
              }
              When("producer and whole cleanup require one actual searchable native CAS provider across all three collections")
              val refused = _put(fixture, next, Array[Byte](2))
              val cleanup = fixture.cleanup.cleanupObserved(deleted, _access, _now)
              Then("equal path labels cannot prove ownership and no quota CAS, Blob put or delete crosses the refused route")
              refused shouldBe a[Consequence.Failure[_]]
              cleanup shouldBe OwnershipRefused
              fixture.blob.puts shouldBe 1
              fixture.blob.deletes shouldBe Vector.empty
              fixture.entity.quotacalls shouldBe calls
              _quota_text(fixture) shouldBe before
              _physical(fixture, reference) shouldBe Vector[Byte](1)
              _load(fixture, "routing") shouldBe deleted
              _message(refused) should not include fixture.root.toString
            } finally { val _ = separate.delegate.closeC() }
          }
        }
      }

      "P19 bound seeded actual publish, refusal, cleanup, failure and fresh-owner sequences by conservative native usage" in {
        Given("seeded finite operation sequences over real native Job metadata and local payloads with unrelated audit bytes")
        val property = Prop.forAll(Gen.listOfN(18, Gen.choose(0, 5))) { sequence =>
          _with_database(DurableJobOwnedBlobResourcePolicy(3, 5L, 2)) { first =>
            val unrelatedjob = _create(first, _record("ordinary"))(using first.context)
            val unrelated = _seed_payload(first, unrelatedjob.record, "ordinary", Array[Byte](9), owned = false)(using first.context)
            var current = first
            var owners = Vector.empty[Fixture]
            var jobs = Vector.empty[DurableJobStoreSnapshot]
            var audit = Vector.empty[Blob]
            try {
              sequence.zipWithIndex.foreach { (operation, ordinal) =>
                Given("previously admitted signed Jobs, surviving conservative reservations and retained Blob metadata")
                When(s"generated operation $ordinal requests publish, failed put, foreign access, cleanup or fresh ownership")
                operation match {
                  case 0 | 1 | 5 =>
                    val active = _create(current, _record(s"sequence-$ordinal"))(using current.context)
                    current.blob.putmode = if (operation == 5) "before" else "normal"
                    val bytes = Array.fill[Byte](if (operation == 1) 2 else 1)((ordinal + 1).toByte)
                    val result = _put(current, active, bytes)(using current.context)
                    current.blob.putmode = "normal"
                    result.toOption.foreach { ref =>
                      audit :+= _metadata(current, ref)(using current.context)
                      jobs :+= _deleted(current, active, Vector(ref))(using current.context)
                    }
                  case 2 if jobs.nonEmpty =>
                    current.cleanup.cleanupObserved(jobs.head, _access, _now)(using current.context) shouldBe Tombstoned
                    jobs = jobs.tail
                  case 3 => current = _open(first.root, first.policy); owners :+= current
                  case _ =>
                    val active = _load(current, "ordinary")(using current.context)
                    current.adapter.putObserved(active, _access.copy(subjectId = "foreign"), None, _content,
                      Bag.binary(Array[Byte](1)))(using current.context) shouldBe a[Consequence.Failure[_]]
                }
                val ledger = _raw_quota(current)(using current.context).map(_ => _ledger(current)(using current.context))
                val files = _actual_payload_files(first.root)
                val ordinarypath = _path(current, _metadata(current, unrelated)(using current.context))
                val ownedfiles = files.filterNot(_ == ordinarypath)
                val physicalbytes = ownedfiles.map(Files.size).sum
                Then("real live files never exceed conservative occupied usage and neither unrelated bytes nor audit metadata disappear")
                ledger.foreach { record =>
                  val usage = _success(DurableJobOwnedBlobQuotaCodec.usage(record))
                  usage shouldBe _success(current.adapter.resourceSnapshot()(using current.context))
                  usage.occupiedblobs should (be >= ownedfiles.size.toLong and be <= 3L)
                  usage.payloadbytes should (be >= physicalbytes and be <= 5L)
                  usage.occupiedblobs shouldBe record.entries.count(_.state != Released).toLong
                  usage.payloadbytes shouldBe record.entries.filterNot(_.state == Released).map(_.bytesize).sum
                  record.entries.filter(_.state == Released).foreach { entry =>
                    _success(current.entity.load[Blob](_success(EntityId.parse(entry.blobid)))(using summon[EntityPersistent[Blob]], current.context))
                      .foreach(blob => Files.exists(_path(current, blob)) shouldBe false)
                  }
                }
                _physical(current, unrelated)(using current.context) shouldBe Vector[Byte](9)
                audit.foreach(blob => _success(current.entity.load[Blob](blob.id)(using summon[EntityPersistent[Blob]], current.context)) shouldBe Some(blob))
                ownedfiles.foreach(path => Files.exists(path.resolveSibling(path.getFileName.toString + ".blob-meta.properties")) shouldBe true)
              }
            } finally owners.foreach(_.close())
          }
          true
        }
        When("ScalaCheck interprets the finite seeded operations through actual independently reopened providers")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8).withInitialSeed(Seed(6970932L)), property)
        Then("all generated active sequences retain bounded physical and persisted accounting authority")
        checked.passed shouldBe true
      }
    }
  }

  private final case class Fixture(
    root: Path, policy: DurableJobOwnedBlobResourcePolicy, provider: ObservedDataStore,
    entity: ObservedEntityStore, context: ExecutionContext, store: DurableJobStore,
    blob: ObservedBlobStore, adapter: DurableJobOwnedBlobPayloads, cleanup: DurableJobPayloadCleanup
  ) {
    private var _closed = false
    def close(): Unit = if (!_closed) { _closed = true; val _ = provider.delegate.closeC() }
  }

  private def _with_database(policy: DurableJobOwnedBlobResourcePolicy)(body: Fixture => Unit): Unit = {
    val root = Files.createTempDirectory("durable-owned-blob-quota-")
    val fixture = _open(root, policy)
    try body(fixture)
    finally {
      fixture.close()
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { val _ = Files.deleteIfExists(path) })
      finally stream.close()
    }
  }

  private def _open(root: Path, policy: DurableJobOwnedBlobResourcePolicy): Fixture = {
    val provider = new ObservedDataStore(SqlDataStore.sqlite(root.resolve("jobs.db").toString))
    val entity = new ObservedEntityStore
    val context = ExecutionContext.withIdGenerationContext(ExecutionContext.test(),
      IdGenerationContext.default(IdGenerationContext.IdNamespace("cncf", "builtin")))
    context.dataStoreSpace.useDataStore(provider)
    context.entityStoreSpace.addEntityStore(entity)
    val store = new DurableJobStore(entity)
    val blob = new ObservedBlobStore(LocalBlobStore(root.resolve("payloads")))
    val adapter = new DurableJobOwnedBlobPayloads(store, entity, blob, policy)
    Fixture(root, policy, provider, entity, context, store, blob, adapter, new DurableJobPayloadCleanup(store, adapter))
  }

  private def _record(jobid: String, v2: Boolean = false, retention: DurableRetentionState = _active): DurableJobRecord =
    _signed(DurableJobRecordBody(DurableJobIdentity(jobid, 1L, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(DurableJobLifecycleStatus.Succeeded, 1, DurableRunMode.Async,
        DurableRetryEvidence(Vector.empty, 1, None, false, false), DurableScheduleState(None, None, None)),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component", None, "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector.empty, DurableResultOutcome.Succeeded(DurableValue.Absent), Vector.empty, Vector.empty, None,
      DurableDefinitionSnapshot("definition", "job", 1, 1L, "a" * 64, None, None, Map.empty), retention), v2)

  private def _signed(body: DurableJobRecordBody, v2: Boolean = false): DurableJobRecord =
    _success(if (v2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))
  private def _create(fixture: Fixture, record: DurableJobRecord)(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _success(fixture.store.create(record, _access))
  private def _load(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _success(fixture.store.load(jobid, _access)).get
  private def _checkpoint(fixture: Fixture, observed: DurableJobStoreSnapshot, body: DurableJobRecordBody)
      (using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _success(fixture.store.checkpoint(observed, _signed(body.copy(identity = body.identity.copy(
      revision = observed.record.body.identity.revision + 1L, updatedAt = _now)), observed.record.format == DurableRecordFormat.V2), _access))
  private def _put(fixture: Fixture, observed: DurableJobStoreSnapshot, bytes: Array[Byte])
      (using ctx: ExecutionContext): Consequence[DurableExternalReference] =
    fixture.adapter.putObserved(observed, _access, None, _content, Bag.binary(bytes))
  private def _deleted(fixture: Fixture, active: DurableJobStoreSnapshot, refs: Vector[DurableExternalReference])
      (using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _checkpoint(fixture, active, active.record.body.copy(inputs = refs.zipWithIndex.map { (ref, index) =>
      DurableInputReference(s"input-$index", DurableValue.External(ref)) },
      retention = _active.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(_now))))
  private def _id(entropy: String): EntityId = _success(EntityId.bridgeFromParts(
    major = "cncf", minor = "builtin", collection = BlobRepository.CollectionId, timestamp = _instant, entropy = entropy))
  private def _blob_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(BlobRepository.CollectionId)
  private def _quota_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobOwnedBlobQuotaEntity.Collection)
  private def _quota_id: EntityId = _success(DurableJobOwnedBlobQuotaEntity.entityId)
  private def _metadata(fixture: Fixture, ref: DurableExternalReference)(using ctx: ExecutionContext): Blob =
    _success(fixture.entity.load[Blob](_success(EntityId.parse(ref.reference)))).get
  private def _path(fixture: Fixture, blob: Blob): Path =
    fixture.root.resolve("payloads").resolve(blob.storageRef.get.container).resolve(blob.storageRef.get.key)
  private def _sidecar(fixture: Fixture, blob: Blob): Path = {
    val path = _path(fixture, blob)
    path.resolveSibling(path.getFileName.toString + ".blob-meta.properties")
  }
  private def _issued_path(fixture: Fixture, put: BlobPutResult): Path =
    fixture.root.resolve("payloads").resolve(put.storageRef.container).resolve(put.storageRef.key)
  private def _issued_sidecar(fixture: Fixture, put: BlobPutResult): Path = {
    val path = _issued_path(fixture, put)
    path.resolveSibling(path.getFileName.toString + ".blob-meta.properties")
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
  private def _physical(fixture: Fixture, ref: DurableExternalReference)(using ctx: ExecutionContext): Vector[Byte] = {
    val blob = _metadata(fixture, ref)
    val read = _success(fixture.blob.delegate.get(blob.storageRef.get))
    val stream = read.payload.openInputStream()
    try stream.readAllBytes().toVector finally stream.close()
  }
  private def _raw_blob(fixture: Fixture, id: EntityId)(using ctx: ExecutionContext): Record =
    _success(fixture.provider.load(_blob_collection, DataStore.EntryId(id))).get
  private def _replace_blob(fixture: Fixture, blob: Blob)(using ctx: ExecutionContext): Unit = {
    _success(fixture.provider.save(_blob_collection, DataStore.EntryId(blob.id),
      summon[EntityPersistent[Blob]].toStoreRecord(blob) ++ Record.dataAuto("cncf_revision" -> 1L)))
    ()
  }
  private def _raw_quota(fixture: Fixture)(using ctx: ExecutionContext): Option[Record] =
    _success(fixture.provider.load(_quota_collection, DataStore.EntryId(_quota_id)))
  private def _quota_text(fixture: Fixture)(using ctx: ExecutionContext): String =
    _raw_quota(fixture).get.getString("canonicalJson").get
  private def _ledger(fixture: Fixture)(using ctx: ExecutionContext): DurableJobOwnedBlobQuotaRecord =
    _success(DurableJobOwnedBlobQuotaCodec.decode(_quota_text(fixture)))
  private def _replace_quota(fixture: Fixture, text: String)(using ctx: ExecutionContext): Unit = {
    _success(fixture.provider.save(_quota_collection, DataStore.EntryId(_quota_id),
      _raw_quota(fixture).get ++ Record.dataAuto("canonicalJson" -> text)))
    ()
  }
  private def _actual_payload_files(root: Path): Vector[Path] = {
    val payloads = root.resolve("payloads")
    if (!Files.exists(payloads)) Vector.empty
    else {
      val stream = Files.walk(payloads)
      try stream.iterator().asScala.filter(path => Files.isRegularFile(path) && !path.getFileName.toString.endsWith(".blob-meta.properties")).toVector
      finally stream.close()
    }
  }
  private def _closed_variant(base: String, variant: String): String = {
    val json = parse(base).toOption.get
    val entries = json.asObject.get("entries").get.asArray.get.toVector
    val first = entries.head
    def _entries_(values: Vector[Json]): String = json.mapObject(_.add("entries", Json.fromValues(values))).noSpaces
    def _first_(name: String, value: Json): String = _entries_(first.mapObject(_.add(name, value)) +: entries.tail)
    variant match {
      case "syntax" => "{"
      case "version" => json.mapObject(_.add("version", Json.fromInt(2))).noSpaces
      case "unknown" => json.mapObject(_.add("signature", Json.fromString("unknown"))).noSpaces
      case "null" => "null"
      case "primitive" => json.mapObject(_.add("entries", Json.fromString("[]"))).noSpaces
      case "missing" => json.mapObject(_.remove("version")).noSpaces
      case "duplicate-key" => base.replace("\"version\":1", "\"version\":1,\"version\":1")
      case "duplicate-entry-key" => base.replace("\"state\":\"Published\"", "\"state\":\"Published\",\"state\":\"Published\"")
      case "duplicate-id" => _entries_(Vector(first, first))
      case "unordered-id" => _entries_(entries.reverse)
      case "bad-id" => _first_("blobId", Json.fromString("x" * 1025))
      case "bad-owner" => _first_("ownerSha256", Json.fromString("A" * 64))
      case "bad-digest" => _first_("payloadSha256", Json.fromString("G" * 64))
      case "bad-content" => _first_("contentType", Json.fromString("界" * 200))
      case "negative-bytes" => _first_("byteSize", Json.fromLong(-1L))
      case "over-payload" => _first_("byteSize", Json.fromLong(8388609L))
      case "overflow" => _first_("byteSize", Json.fromBigInt(BigInt(Long.MaxValue) + 1))
      case "fraction" => _first_("byteSize", Json.fromBigDecimal(BigDecimal("1.1")))
      case "policy" => json.mapObject(_.add("maximumOwnedBytes", Json.fromLong(268435457L))).noSpaces
      case "policy-type" => json.mapObject(_.add("maximumOwnedBlobs", Json.fromString("3"))).noSpaces
      case "usage" => json.mapObject(_.add("maximumOwnedBytes", Json.fromLong(1L))).noSpaces
      case "pending-binding" => _first_("state", Json.fromString("Pending"))
      case "pending-token" => _entries_(first.mapObject(_.add("state", Json.fromString("Pending")).add("bindingSha256", Json.Null).add("token", Json.Null)) +: entries.tail)
      case "published-binding" => _first_("bindingSha256", Json.Null)
      case "bad-binding" => _first_("bindingSha256", Json.fromString("G" * 64))
      case "released-pair" => _entries_(first.mapObject(_.add("state", Json.fromString("Released")).add("bindingSha256", Json.Null).add("token", Json.Null)) +: entries.tail)
      case "unknown-state" => _first_("state", Json.fromString("Acknowledged"))
      case "duplicate-token" => _entries_(entries.map(_.mapObject(_.add("token", Json.fromString("a" * 32)))))
      case "bad-token" => _first_("token", Json.fromString("A" * 32))
      case "unknown-entry" => _first_("owner", Json.fromString("unknown"))
      case "missing-entry" => _entries_(first.mapObject(_.remove("state")) +: entries.tail)
      case "null-entry" => _entries_(Vector(Json.Null))
      case "entry-shape" => _entries_(Vector(Json.fromInt(1)))
      case "deep" => "[" * 17 + "0" + "]" * 17
      case _ => " " * 16777217
    }
  }
  private def _seed_payload(fixture: Fixture, record: DurableJobRecord, entropy: String, bytes: Array[Byte], owned: Boolean)
      (using ctx: ExecutionContext): DurableExternalReference = {
    val id = _id(entropy)
    val put = _success(fixture.blob.delegate.put(BlobPutRequest(id, BlobKind.Binary, None, _content, Map.empty), Bag.binary(bytes)))
    val attributes = if (!owned) Map.empty[String, String] else Map(ownershipAttribute -> "exclusive-v1",
      ownerAttribute -> ownerFingerprint(record), bindingAttribute -> bindingFingerprint(id, put.storageRef, put.contentType, put.byteSize, put.digest))
    _success(fixture.entity.create(BlobCreate(id, BlobKind.Binary, BlobSourceMode.Managed, None,
      Some(put.contentType), Some(put.byteSize), Some(put.digest), Some(put.storageRef), None, BlobUrl.cncfRoute(id), attributes)))
    DurableExternalReference(id.value, if (owned) storageKind else "blob", Some(put.contentType.header), put.byteSize, put.digest)
  }

  private def _fill_census(fixture: Fixture, count: Int)(using ctx: ExecutionContext): Unit = {
    val rows = _success(fixture.provider.delegate.search(_blob_collection, QueryDirective(Query.Empty,
      order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(32)))).records
    val source = rows.find(row => _success(summon[EntityPersistent[Blob]].fromStoreRecord(row)).attributes.isEmpty).get.getString("id").get
    val connection = DriverManager.getConnection("jdbc:sqlite:" + fixture.root.resolve("jobs.db"))
    try {
      val statement = connection.createStatement()
      val columns = try {
        val result = statement.executeQuery("PRAGMA table_info(\"blob\")")
        try { val values = Vector.newBuilder[String]; while (result.next()) values += result.getString("name"); values.result() }
        finally result.close()
      } finally statement.close()
      val projection = columns.map(name => if (name == "id") "?" else "\"" + name + "\"").mkString(",")
      val insert = connection.prepareStatement("INSERT INTO \"blob\" SELECT " + projection + " FROM \"blob\" WHERE id = ?")
      connection.setAutoCommit(false)
      try {
        Vector.tabulate(count)(index => _id(f"census_$index%05d").value).foreach { id =>
          insert.setString(1, id); insert.setString(2, source); insert.addBatch()
        }
        val _ = insert.executeBatch()
        connection.commit()
      } finally insert.close()
    } finally connection.close()
  }

  private def _await(latch: CountDownLatch): Unit =
    if (!latch.await(10L, TimeUnit.SECONDS)) throw new IllegalStateException("bounded Blob quota barrier timed out")
  private def _join(workers: Vector[Thread]): Unit = workers.foreach { worker =>
    worker.join(10000L)
    if (worker.isAlive) { worker.interrupt(); worker.join(10000L) }
    worker.isAlive shouldBe false
  }
  private def _race[A](actions: Vector[() => Consequence[A]], ready: CountDownLatch, proceed: CountDownLatch): Vector[Consequence[A]] = {
    val outcomes = actions.map(_ => new AtomicReference[Either[Throwable, Consequence[A]]]())
    val workers = actions.zip(outcomes).map { (action, outcome) => new Thread(() => {
      try outcome.set(Right(action())) catch { case error: Throwable => outcome.set(Left(error)) }
    }, "owned-blob-quota-fixture") }
    workers.foreach(_.start())
    try {
      _await(ready); proceed.countDown(); _join(workers)
      outcomes.map(_.get() match { case Right(value) => value; case Left(error) => throw error })
    } finally { proceed.countDown(); _join(workers) }
  }
  private def _message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => fail("expected fixed payload refusal")
  }
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private final class FixedIds(id: EntityId) extends IdGenerationContext {
    var calls = 0
    def namespace: IdGenerationContext.IdNamespace = IdGenerationContext.IdNamespace("cncf", "builtin")
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = { calls += 1; id }
    def opaqueId(purpose: String): String = "fixed"
  }
  private final class MeasuredBag(first: Array[Byte], second: Array[Byte], failure: Option[Int] = None) extends BinaryBag {
    val bag: Bag = Bag.binary(first)
    var opens = 0
    var closed = 0
    var readbytes = 0
    override def openInputStream(): InputStream = {
      opens += 1
      val bytes = if (opens == 1) first else second
      new ByteArrayInputStream(bytes) {
        private def _admit(): Unit = if (failure.exists(readbytes >= _)) throw new IllegalStateException("private-stream-failure")
        override def read(): Int = { _admit(); val value = super.read(); if (value >= 0) readbytes += 1; value }
        override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
          _admit()
          val bounded = failure.fold(length)(value => math.min(length, value - readbytes))
          val count = super.read(buffer, offset, bounded)
          if (count > 0) readbytes += count
          count
        }
        override def close(): Unit = { closed += 1; super.close() }
      }
    }
  }

  private final class ObservedBlobStore(val delegate: BlobStore) extends BlobStore {
    var puts = 0
    var deletes = Vector.empty[BlobStorageRef]
    var issued = Vector.empty[BlobPutResult]
    var putmode = "normal"
    var deletemode = "normal"
    var faildeleteat: Option[Int] = None
    var failget = false
    var transform: BlobPutResult => BlobPutResult = identity
    var beforeput: Option[() => Unit] = None
    var afterput: Option[() => Unit] = None
    var afterdelete: Option[() => Unit] = None
    def name: String = delegate.name
    def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] = {
      puts += 1
      val before = beforeput; beforeput = None; before.foreach(_())
      if (putmode == "before") return Consequence.stateInvalid("private-put-before-effect")
      if (putmode == "throw-before") throw new IllegalStateException("private-put-before-effect")
      val result = delegate.put(request, payload)
      result.map { value =>
        issued :+= value
        val after = afterput; afterput = None; after.foreach(_())
        if (putmode == "lost") throw new IllegalStateException("private-put-lost-response")
        transform(value)
      }
    }
    def get(ref: BlobStorageRef): Consequence[BlobReadResult] =
      if (failget) Consequence.stateInvalid("private-get-refused") else delegate.get(ref)
    def delete(ref: BlobStorageRef): Consequence[Unit] = {
      deletes :+= ref
      if (deletemode == "before" || faildeleteat.contains(deletes.size)) Consequence.stateInvalid("private-delete-before-effect")
      else delegate.delete(ref).map { _ =>
        val after = afterdelete; afterdelete = None; after.foreach(_())
        if (deletemode == "lost") throw new IllegalStateException("private-delete-lost-response")
        ()
      }
    }
    def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] = delegate.accessUrl(ref)
    def status(): Consequence[BlobStoreStatus] = delegate.status()
  }

  private final class ObservedEntityStore extends StandardEntityStore {
    var beforequotacreate: Option[() => Unit] = None
    var beforequota: Option[() => Unit] = None
    var beforequotaread: Option[() => Unit] = None
    var beforeconfirm: Option[() => Unit] = None
    var beforejob: Option[() => Unit] = None
    var quotacalls = 0
    var genericsearches = 0
    var stalecount = 0
    var failquota = false
    var failblobcreate = false
    var failblobload = false
    var failcurrent = false
    var failconfirm = false
    var releasefault = "normal"
    var failjob = false
    override def create[T](entity: T, options: EntityCreateOptions)
        (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      if (tc.collection(entity) == DurableJobOwnedBlobQuotaEntity.Collection) {
        val action = beforequotacreate; beforequotacreate = None; action.foreach(_())
      }
      if (tc.collection(entity) == BlobRepository.CollectionId && failblobcreate)
        Consequence.stateInvalid("private-blob-create-refused")
      else super.create(entity, options)
    }
    override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] =
      if (id.collection == BlobRepository.CollectionId && failblobload) Consequence.stateInvalid("private-blob-readback-refused")
      else super.load[T](id)
    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] = {
      if (id.collection == DurableJobOwnedBlobQuotaEntity.Collection) {
        val action = beforequotaread; beforequotaread = None; action.foreach(_())
      }
      if (id.collection == DurableJobStoreEntity.Collection && failcurrent) Consequence.stateInvalid("private-current-refused")
      else super.loadDetached[T](id)
    }
    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
        (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val quota = tc.id(changes).collection == DurableJobOwnedBlobQuotaEntity.Collection
      if (quota) {
        quotacalls += 1
        val action = beforequota; beforequota = None; action.foreach(_())
        if (stalecount > 0) {
          stalecount -= 1
          return EntityConcurrencyMetadata.staleMutation(expectedRevision.get, _success(expectedRevision.get.nextC))
        }
        if (failquota) return Consequence.stateInvalid("private-nonstale-quota-refused")
        val text = tc.toStoreRecord(changes).getString("canonicalJson").get
        if (text.contains("\"state\":\"Published\"")) {
          val action = beforeconfirm; beforeconfirm = None; action.foreach(_())
        }
        if (failconfirm && text.contains("\"state\":\"Published\"")) {
          failconfirm = false
          return Consequence.stateInvalid("private-confirmation-refused")
        }
        if (releasefault == "before" && text.contains("\"state\":\"Released\""))
          return Consequence.stateInvalid("private-release-before-effect")
        val result = super.updateDetached(changes, expectedRevision, executionPolicy)
        if (releasefault == "lost" && text.contains("\"state\":\"Released\"") && result.isSuccess)
          throw new IllegalStateException("private-release-lost-response")
        result
      } else {
        if (tc.id(changes).collection == DurableJobStoreEntity.Collection) {
          val action = beforejob; beforejob = None; action.foreach(_())
          if (failjob) return Consequence.stateInvalid("private-job-checkpoint-refused")
        }
        super.updateDetached(changes, expectedRevision, executionPolicy)
      }
    }
    override def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[org.goldenport.cncf.directive.SearchResult[T]] = {
      genericsearches += 1
      throw new IllegalStateException("generic EntityStore census is forbidden")
    }
    override def searchInternal[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[org.goldenport.cncf.directive.SearchResult[T]] = {
      genericsearches += 1
      throw new IllegalStateException("generic EntityStore census is forbidden")
    }
  }

  private final class ObservedDataStore(val delegate: SqlDataStore)
      extends DataStore with SearchableDataStore with EntityVersionedMutationDataStore {
    var requests = Vector.empty[QueryDirective]
    var censusmode = "normal"
    var nativecas = true
    var accept: DataStore.CollectionId => Boolean = _ => true
    def isAccept(cid: DataStore.CollectionId): Boolean = accept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      if (nativecas) delegate.entityMutationProviderCapabilities else EntityMutationProviderCapabilities.empty
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] = {
      if (collection == _blob_collection) requests :+= directive
      delegate.search(collection, directive).map { result =>
        val rows = result.records
        if (collection != _blob_collection || rows.isEmpty) result
        else censusmode match {
          case "oversupply" => result.copy(records = rows :+ rows.head)
          case "duplicate" => result.copy(records = rows.updated(rows.size - 1, rows.head))
          case "unordered" => result.copy(records = rows.reverse)
          case "wrong-id" => result.copy(records = rows.updated(0, rows.head.upsertSingle("id", "foreign")))
          case "bad-row" => result.copy(records = rows.updated(0, Record(rows.head.fields.filterNot(field => field.key == "createdAt" || field.key == "created_at"))))
          case "cursor" => result.copy(cursor = Some(Cursor("private-cursor")))
          case "range" => result.copy(range = ResultRange.Limited(31))
          case _ => result
        }
      }
    }
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] =
      throw new IllegalStateException("default native count is forbidden")
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      delegate.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      delegate.compareAndSetEntity(plan)
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] =
      delegate.mutateVersionedEntity(plan)
    def prepare(tx: TransactionContext): PrepareResult = record_prepare(CommitRecorder.noop)
    def commit(tx: TransactionContext): Unit = record_commit(CommitRecorder.noop)
    def abort(tx: TransactionContext): Unit = record_abort(CommitRecorder.noop)
  }

  private final class OpaqueDataStore(delegate: ObservedDataStore) extends DataStore with EntityVersionedMutationDataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean = true
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = delegate.entityMutationProviderCapabilities
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      delegate.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      delegate.compareAndSetEntity(plan)
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] =
      delegate.mutateVersionedEntity(plan)
    def prepare(tx: TransactionContext): PrepareResult = record_prepare(CommitRecorder.noop)
    def commit(tx: TransactionContext): Unit = record_commit(CommitRecorder.noop)
    def abort(tx: TransactionContext): Unit = record_abort(CommitRecorder.noop)
  }
}
