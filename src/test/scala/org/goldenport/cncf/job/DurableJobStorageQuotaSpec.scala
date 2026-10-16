package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
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
 * Native retained capacity, persisted ambiguous reservations and fenced recovery.
 *
 * @since   Oct.  1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobStorageQuotaSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _now = Instant.parse("2026-10-01T01:02:03Z")
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))

  "Native durable Job storage capacity" should {
    "inclusive admission and unchanged read authority" which {
      "S1 admit the inclusive count and UTF-8 bytes and refuse one more native row" in {
        Given("JM69-09B3B1; two equally sized signed native records and an exact lowered count/byte policy")
        val first = _record("a")
        val second = _record("b")
        val policy = DurableJobStorageResourcePolicy(2, _bytes(first) + _bytes(second))
        _with_database(policy) { fixture =>
          given ExecutionContext = fixture.context
          When("both records fill native capacity and a third identity requests admission")
          val firstsnapshot = _success(fixture.store.create(first, _access))
          val secondsnapshot = _success(fixture.store.create(second, _access))
          val writes = fixture.provider.jobcreates
          val refused = fixture.store.create(_record("c"), _access)
          val loaded = fixture.store.load("a", _access)
          val foreign = fixture.store.load("a", _access.copy(subjectId = "foreign"))
          val usage = _success(fixture.store.resourceSnapshot())
          Then("inclusive capacity succeeds, the next native create never occurs and authorized reads remain available")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.provider.jobcreates shouldBe writes
          loaded shouldBe Consequence.Success(Some(firstsnapshot))
          fixture.store.load("b", _access) shouldBe Consequence.Success(Some(secondsnapshot))
          foreign shouldBe a[Consequence.Failure[_]]
          usage.retainedjobs shouldBe 2
          usage.canonicalbytes shouldBe policy.maxcanonicalbytes
          usage.pendingreservations shouldBe 0
        }
      }

      "S1 refuse one extra canonical byte independently of retained cardinality" in {
        Given("an actual native Job whose multibyte canonical value consumes the entire byte budget")
        val record = _record("a", "界" * 100)
        _with_database(DurableJobStorageResourcePolicy(2, _bytes(record))) { fixture =>
          given ExecutionContext = fixture.context
          When("the inclusive record is created and another retained identity requests any canonical bytes")
          val accepted = fixture.store.create(record, _access)
          val refused = fixture.store.create(_record("b"), _access)
          val usage = _success(fixture.store.resourceSnapshot())
          Then("the independent byte ceiling prevents the second provider row")
          accepted.map(_.record) shouldBe Consequence.Success(record)
          refused shouldBe a[Consequence.Failure[_]]
          fixture.provider.jobcreates shouldBe 1
          usage.canonicalbytes shouldBe _bytes(record)
          _bytes(record) should be > _canonical(record).length.toLong
        }
        Given("the same signed record with a lowered budget one byte below its actual UTF-8 value")
        _with_database(DurableJobStorageResourcePolicy(2, _bytes(record) - 1L)) { fixture =>
          given ExecutionContext = fixture.context
          When("native admission requests the one-over record")
          val refused = fixture.store.create(record, _access)
          Then("no native Job create occurs")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.provider.jobcreates shouldBe 0
        }
      }

      "S2 bound multibyte Job IDs before stable entropy and reject invalid or increased policy without writes" in {
        Given("a 512-byte multibyte Job ID and the same value extended to 513 UTF-8 bytes")
        val exactid = "界" * 170 + "ab"
        val overid = exactid + "c"
        _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
          given ExecutionContext = fixture.context
          When("both signed identities request actual native create")
          val accepted = fixture.store.create(_record(exactid), _access)
          val refused = fixture.store.create(_record(overid), _access)
          val blank = fixture.store.create(_record("\u3000"), _access)
          Then("only the inclusive bounded ID reaches provider publication")
          exactid.getBytes(StandardCharsets.UTF_8).length shouldBe 512
          overid.length should be < 512
          overid.getBytes(StandardCharsets.UTF_8).length shouldBe 513
          accepted shouldBe a[Consequence.Success[_]]
          Vector(refused, blank).foreach(_ shouldBe a[Consequence.Failure[_]])
          fixture.provider.jobcreates shouldBe 1
        }
        Vector(DurableJobStorageResourcePolicy(0), DurableJobStorageResourcePolicy(4097),
          DurableJobStorageResourcePolicy(1, 0L), DurableJobStorageResourcePolicy(1, 67108865L)).foreach { policy =>
          Given("an invalid or above-ceiling private policy and an empty native provider")
          _with_database(policy) { fixture =>
            given ExecutionContext = fixture.context
            When("a valid signed Job requests create under that policy")
            val result = fixture.store.create(_record("policy"), _access)
            Then("policy admission refuses before any persisted entity publication")
            result shouldBe a[Consequence.Failure[_]]
            fixture.provider.creates shouldBe 0
          }
        }
      }

      "S3 reserve only growth and release acknowledged shrink without releasing the identity" in {
        Given("a native Job with room for one large checkpoint but not a second Job at that size")
        val initial = _record("a")
        val grown = _next(initial, "x" * 1024)
        _with_database(DurableJobStorageResourcePolicy(2, _bytes(grown))) { fixture =>
          given ExecutionContext = fixture.context
          val original = _success(fixture.store.create(initial, _access))
          When("growth fills bytes, an equal-size checkpoint succeeds and an acknowledged shrink makes byte room")
          val growth = _success(fixture.store.checkpoint(original, grown, _access))
          val filled = _success(fixture.store.resourceSnapshot())
          val equal = _success(fixture.store.checkpoint(growth, _next(grown, "y" * 1024), _access))
          val blocked = fixture.store.create(_record("b"), _access)
          val shrunk = _success(fixture.store.checkpoint(equal, _next(equal.record, "small"), _access))
          val reduced = _success(fixture.store.resourceSnapshot())
          val additional = fixture.store.create(_record("b"), _access)
          Then("one identity pays the maximum candidate size, and only acknowledged actual bytes become available")
          filled.retainedjobs shouldBe 1
          filled.canonicalbytes shouldBe _bytes(grown)
          blocked shouldBe a[Consequence.Failure[_]]
          reduced.retainedjobs shouldBe 1
          reduced.canonicalbytes shouldBe _bytes(shrunk.record)
          reduced.pendingreservations shouldBe 0
          additional shouldBe a[Consequence.Success[_]]
          fixture.provider.jobcreates shouldBe 2
        }
      }

      "S3 retain terminal, Deleted and Tombstoned physical Jobs at the cardinality ceiling" in {
        Vector(DurableDeletionState.Active, DurableDeletionState.Deleted, DurableDeletionState.Tombstoned).foreach { state =>
          Given(s"a signed Succeeded Job physically retained with ${state.wire} retention")
          val record = _with_retention(_record("retained"), state)
          _with_database(DurableJobStorageResourcePolicy(1)) { fixture =>
            given ExecutionContext = fixture.context
            val observed = _success(fixture.store.create(record, _access))
            When("a different identity requests admission while the retained record is loaded")
            val result = fixture.store.create(_record("new"), _access)
            val loaded = fixture.store.load("retained", _access)
            Then("retention status never evicts a physical identity or disables its ordinary authorized store read")
            result shouldBe a[Consequence.Failure[_]]
            loaded shouldBe Consequence.Success(Some(observed))
            _success(fixture.store.resourceSnapshot()).retainedjobs shouldBe 1
            fixture.provider.jobcreates shouldBe 1
          }
        }
      }

      "S4 preserve duplicate, stale, noncontiguous and foreign checkpoint facts without an extra logical slot" in {
        Given("one native authoritative snapshot and a later contiguous acknowledged checkpoint")
        _with_database(DurableJobStorageResourcePolicy(1)) { fixture =>
          given ExecutionContext = fixture.context
          val original = _success(fixture.store.create(_record("a"), _access))
          val advanced = _success(fixture.store.checkpoint(original, _next(original.record), _access))
          val before = fixture.provider.jobupdates
          val ledgerbefore = _quota_text(fixture)
          When("invalid checkpoint requests precede a duplicate stable native create")
          val stale = fixture.store.checkpoint(original, _next(original.record), _access)
          val noncontiguous = fixture.store.checkpoint(advanced, _signed(advanced.record.body.copy(
            identity = advanced.record.body.identity.copy(revision = 99L))), _access)
          val foreign = fixture.store.checkpoint(advanced, _next(advanced.record), _access.copy(tenantId = "foreign"))
          val wrongid = fixture.store.checkpoint(advanced, _record("b", revision = 3L), _access)
          val ledgerafterinvalid = _quota_text(fixture)
          val duplicate = fixture.store.create(original.record, _access)
          Then("the original native revision/record survive, invalid checkpoints never reserve and duplicates pay no second slot")
          Vector(stale, noncontiguous, foreign, wrongid, duplicate).foreach(_ shouldBe a[Consequence.Failure[_]])
          ledgerafterinvalid shouldBe ledgerbefore
          fixture.provider.jobupdates shouldBe before
          fixture.store.load("a", _access) shouldBe Consequence.Success(Some(advanced))
          _success(fixture.store.resourceSnapshot()).retainedjobs shouldBe 1
        }
      }
    }

    "fresh provider ownership and complete native census" which {
      "S5 honor the persisted quota through new SQLite, EntityStore and ExecutionContext objects" in {
        Given("one physical SQLite database filled by a first independently constructed runtime")
        _with_database(DurableJobStorageResourcePolicy(1)) { first =>
          val record = _record("a")
          val observed = _success(first.store.create(record, _access)(using first.context))
          val fresh = _open(first.database, first.policy)
          val increased = _open(first.database, DurableJobStorageResourcePolicy(2))
          val decreased = _open(first.database, DurableJobStorageResourcePolicy(1, 33554432L))
          try {
            When("fresh native owners read the existing Job and request capacity with equal, increased and decreased policy")
            val loaded = fresh.store.load("a", _access)(using fresh.context)
            val full = fresh.store.create(_record("b"), _access)(using fresh.context)
            val changed = increased.store.create(_record("c"), _access)(using increased.context)
            val lower = decreased.store.checkpoint(observed, _next(record), _access)(using decreased.context)
            Then("fresh runtime maps and a changed configuration never reset or overwrite stored policy")
            loaded shouldBe Consequence.Success(Some(observed))
            Vector(full, changed, lower).foreach(_ shouldBe a[Consequence.Failure[_]])
            fresh.provider.jobcreates shouldBe 0
            increased.provider.jobcreates shouldBe 0
            decreased.provider.jobupdates shouldBe 0
            _success(fresh.store.resourceSnapshot()(using fresh.context)).retainedjobs shouldBe 1
          } finally { fresh.close(); increased.close(); decreased.close() }
        }
      }

      "S6 bootstrap only from all real native terminal and corrupt rows, with missing tables interpreted as empty" in {
        Given("native terminal and malformed canonical rows with no surviving operational ledger")
        _with_database(DurableJobStorageResourcePolicy(3)) { fixture =>
          given ExecutionContext = fixture.context
          val terminal = _record("terminal")
          _seed(fixture, "terminal", _canonical(terminal))
          _seed(fixture, "corrupt", "not-json")
          When("a third Job causes complete census and guarded bootstrap")
          val accepted = fixture.store.create(_record("new"), _access)
          val usage = _success(fixture.store.resourceSnapshot())
          val corrupt = fixture.store.load("corrupt", _access)
          Then("all real rows consume slots and stored-text bytes without repairing corrupt signed authority")
          accepted shouldBe a[Consequence.Success[_]]
          usage.retainedjobs shouldBe 3
          usage.canonicalbytes shouldBe _bytes(terminal) + 8L + _bytes(_record("new"))
          corrupt shouldBe a[Consequence.Failure[_]]
          fixture.store.loadStartupRecovery("corrupt", _access) shouldBe DurableJobStoreStartupRecoveryLoad.Corrupt
          _raw_job(fixture, "corrupt").getString("canonicalJson") shouldBe Some("not-json")
        }
        Given("an actual fresh SQLite database with neither native Job nor quota tables")
        _with_database(DurableJobStorageResourcePolicy(1)) { fixture =>
          given ExecutionContext = fixture.context
          When("the first signed Job bootstraps the empty native census")
          val result = fixture.store.create(_record("first"), _access)
          Then("guarded initialization admits one native Job")
          result shouldBe a[Consequence.Success[_]]
          _success(fixture.store.resourceSnapshot()).retainedjobs shouldBe 1
        }
      }

      "S6 refuse a pre-existing over-capacity census without partial ledger bootstrap or native write" in {
        Vector(DurableJobStorageResourcePolicy(1), DurableJobStorageResourcePolicy(3, 8L)).foreach { policy =>
          Given("two native malformed canonical rows exceeding a lowered count or aggregate byte ceiling")
          _with_database(policy) { fixture =>
            given ExecutionContext = fixture.context
            _seed(fixture, "a", "not-json")
            _seed(fixture, "b", "not-json")
            val before = fixture.provider.creates
            When("native admission requests complete missing-ledger bootstrap")
            val result = fixture.store.create(_record("c"), _access)
            Then("no truncated inventory or empty substitute ledger is published")
            result shouldBe a[Consequence.Failure[_]]
            fixture.provider.creates shouldBe before
            _raw_quota(fixture) shouldBe None
          }
        }
      }

      "S7 request stable explicit native pages bounded by the configured count plus one sentinel" in {
        Given("35 physically retained native rows, a missing ledger and an instrumented real searchable provider")
        _with_database(DurableJobStorageResourcePolicy(35)) { fixture =>
          given ExecutionContext = fixture.context
          (0 until 35).foreach(index => _seed(fixture, f"row-$index%02d", "not-json"))
          When("a further Job attempts admission at the complete retained count")
          val result = fixture.store.create(_record("extra"), _access)
          val requests = fixture.provider.requests
          Then("every page uses native Empty, ascending stable id, explicit bounded limits and no generic default count")
          result shouldBe a[Consequence.Failure[_]]
          requests.nonEmpty shouldBe true
          requests.foreach { request =>
            request.query shouldBe Query.Empty
            request.order shouldBe QueryOrder.By("id", OrderDirection.Asc)
            request.limit shouldBe QueryLimit.Limit(math.min(32, 36 - request.offset))
            request.offset should (be >= 0 and be <= 35)
          }
          requests.take(2).map(_.offset) shouldBe Vector(0, 32)
          fixture.provider.largestcensus shouldBe 35
          fixture.provider.countcalls shouldBe 0
          fixture.entity.searchcalls shouldBe 0
        }
      }

      "S6 preserve the winner of guarded singleton initialization when two fresh owners bootstrap an empty native database" in {
        Given("two distinct native SQLite owners with no quota entity and capacity for one retained Job")
        _with_database(DurableJobStorageResourcePolicy(1)) { first =>
          val second = _open(first.database, first.policy)
          val admitted = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val left = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          val right = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          first.entity.beforequotacreate = Some(() => { admitted.countDown(); _await(release) })
          second.entity.beforequotacreate = Some(() => { admitted.countDown(); _await(release) })
          val workers = Vector(
            new Thread(() => { left.set(first.store.create(_record("a"), _access)(using first.context)); () }),
            new Thread(() => { right.set(second.store.create(_record("b"), _access)(using second.context)); () })
          )
          try {
            When("both complete empty census and concurrently attempt the one stable quota identity")
            workers.foreach(_.start())
            _await(admitted)
            release.countDown()
            _join(workers)
            val results = Vector(left.get(), right.get())
            val usage = _success(first.store.resourceSnapshot()(using first.context))
            Then("guarded initialization preserves one persisted policy and refreshed admission permits one physical Job")
            first.entity.quotacreatecalls + second.entity.quotacreatecalls shouldBe 2
            results.count(_.isSuccess) shouldBe 1
            results.count(result => !result.isSuccess) shouldBe 1
            usage.retainedjobs shouldBe 1
            usage.maxstoredjobs shouldBe 1
            usage.pendingreservations shouldBe 0
            _physical_rows(first).size shouldBe 1
          } finally { release.countDown(); _join(workers); second.close() }
        }
      }

      "S7 reject oversupplying, duplicate, unordered, malformed and invalid-revision native pages" in {
        Vector("oversupply", "duplicate", "unordered", "bad-id", "bad-revision", "bad-row", "oversize", "cursor").foreach { mode =>
          Given(s"a real native provider with two seeded rows and an injected $mode census response")
          _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
            given ExecutionContext = fixture.context
            _seed(fixture, "a", "not-json")
            _seed(fixture, "b", "not-json")
            fixture.provider.censusmode = mode
            val before = fixture.provider.creates
            When("a signed Job requests persisted native capacity")
            val result = fixture.store.create(_record("new"), _access)
            Then("no partial operational ledger or native Job is published from the invalid census")
            result shouldBe a[Consequence.Failure[_]]
            fixture.provider.creates shouldBe before
            _raw_quota(fixture) shouldBe None
            fixture.entity.searchcalls shouldBe 0
          }
        }
      }
    }
  }

  "Persisted native reservations" should {
    "concurrent optimistic ownership" which {
      "S8 admit at most one contender for the final native count and byte capacity across fresh stores" in {
        Given("two independent SQLite-backed owners and one remaining retained slot with exactly one record of byte room")
        val policy = DurableJobStorageResourcePolicy(2, _bytes(_record("a")) + _bytes(_record("b")))
        _with_database(policy) { first =>
          val seed = _success(first.store.create(_record("a"), _access)(using first.context))
          val second = _open(first.database, policy)
          val admitted = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val left = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          val right = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          first.entity.beforequota = Some(() => { admitted.countDown(); _await(release) })
          second.entity.beforequota = Some(() => { admitted.countDown(); _await(release) })
          val workers = Vector(
            new Thread(() => { left.set(first.store.create(_record("b"), _access)(using first.context)); () }),
            new Thread(() => { right.set(second.store.create(_record("c"), _access)(using second.context)); () })
          )
          try {
            When("both owners reach the same observed native ledger revision before either CAS is released")
            workers.foreach(_.start())
            _await(admitted)
            release.countDown()
            _join(workers)
            val outcomes = Vector(left.get(), right.get())
            val usage = _success(first.store.resourceSnapshot()(using first.context))
            val rows = _physical_rows(first)
            Then("native ledger OCC admits one contender, preserves the earlier Job and leaves no losing physical row")
            outcomes.count(_.isSuccess) shouldBe 1
            outcomes.count(result => !result.isSuccess) shouldBe 1
            rows.size shouldBe 2
            usage.retainedjobs shouldBe 2
            usage.canonicalbytes shouldBe policy.maxcanonicalbytes
            usage.pendingreservations shouldBe 0
            first.provider.jobcreates + second.provider.jobcreates shouldBe 2
            first.store.load("a", _access)(using first.context) shouldBe Consequence.Success(Some(seed))
          } finally { release.countDown(); _join(workers); second.close() }
        }
      }

      "S9 permit two reserved checkpoints for one snapshot while original native Job OCC admits only one" in {
        Given("two independently constructed native owners observing the same current signed Job")
        _with_database(DurableJobStorageResourcePolicy(1)) { first =>
          val initial = _success(first.store.create(_record("a"), _access)(using first.context))
          val second = _open(first.database, first.policy)
          val admitted = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val left = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          val right = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          first.entity.beforejob = Some(() => { admitted.countDown(); _await(release) })
          second.entity.beforejob = Some(() => { admitted.countDown(); _await(release) })
          val workers = Vector(
            new Thread(() => { left.set(first.store.checkpoint(initial, _next(initial.record, "left"), _access)(using first.context)); () }),
            new Thread(() => { right.set(second.store.checkpoint(initial, _next(initial.record, "right"), _access)(using second.context)); () })
          )
          try {
            When("both candidates persist reservations and are then released to the original observed Job CAS")
            workers.foreach(_.start())
            _await(admitted)
            val held = _success(first.store.resourceSnapshot()(using first.context))
            release.countDown()
            _join(workers)
            val outcomes = Vector(left.get(), right.get())
            val winner = outcomes.flatMap(_.toOption).head
            val usage = _success(first.store.resourceSnapshot()(using first.context))
            Then("both real native writes compete, one record wins and its revision fences the losing reservation")
            held.retainedjobs shouldBe 1
            held.pendingreservations shouldBe 2
            outcomes.count(_.isSuccess) shouldBe 1
            outcomes.count(result => !result.isSuccess) shouldBe 1
            first.provider.jobupdates + second.provider.jobupdates shouldBe 2
            winner.providerrevision.value shouldBe initial.providerrevision.value + 1L
            first.store.load("a", _access)(using first.context) shouldBe Consequence.Success(Some(winner))
            usage.retainedjobs shouldBe 1
            usage.canonicalbytes shouldBe _bytes(winner.record)
            usage.pendingreservations shouldBe 0
          } finally { release.countDown(); _join(workers); second.close() }
        }
      }
    }

    "ambiguous failures and native revision proofs" which {
      "S10 preserve a reservation when native create fails before writing and allow only proven remaining capacity" in {
        Given("an injected provider exception after persisted admission but before the native Job write")
        _with_database(DurableJobStorageResourcePolicy(2)) { first =>
          first.entity.failcreatebefore = true
          When("the failed write is followed by requests from a fresh SQLite-backed owner")
          val failed = first.store.create(_record("a"), _access)(using first.context)
          val fresh = _open(first.database, first.policy)
          try {
            val remaining = fresh.store.create(_record("b"), _access)(using fresh.context)
            val full = fresh.store.create(_record("c"), _access)(using fresh.context)
            val usage = _success(fresh.store.resourceSnapshot()(using fresh.context))
            val missing = fresh.store.load("a", _access)(using fresh.context)
            Then("a missing row never proves the old writer fenced, and one remaining slot can still be used")
            failed shouldBe a[Consequence.Failure[_]]
            _failure_message(failed) should include(DurableJobStorageResourcePolicy.unavailableLabel)
            _failure_message(failed) should not include "private-provider-secret"
            remaining shouldBe a[Consequence.Success[_]]
            full shouldBe a[Consequence.Failure[_]]
            missing shouldBe Consequence.Success(None)
            usage.retainedjobs shouldBe 2
            usage.pendingreservations shouldBe 1
            _physical_rows(fresh).size shouldBe 1
            fresh.store.load("b", _access)(using fresh.context).map(_.map(_.record)) shouldBe Consequence.Success(Some(_record("b")))
          } finally fresh.close()
        }
      }

      "S11 reconcile a committed native create whose acknowledgement was lost without deleting its exact record" in {
        Given("a provider that commits create and throws before returning the native acknowledgement")
        _with_database(DurableJobStorageResourcePolicy(2)) { first =>
          first.entity.losecreateack = true
          val record = _record("a", "exact-result-evidence")
          When("a fresh actual SQLite owner observes the row and reserves the remaining capacity")
          val failed = first.store.create(record, _access)(using first.context)
          val held = _success(first.store.resourceSnapshot()(using first.context))
          val fresh = _open(first.database, first.policy)
          try {
            val original = fresh.store.load("a", _access)(using fresh.context)
            val next = fresh.store.create(_record("b"), _access)(using fresh.context)
            val reconciled = _success(fresh.store.resourceSnapshot()(using fresh.context))
            Then("the real retained row fences create reservations and survives failed acknowledgement exactly")
            failed shouldBe a[Consequence.Failure[_]]
            held.pendingreservations shouldBe 1
            original.map(_.map(_.record)) shouldBe Consequence.Success(Some(record))
            next shouldBe a[Consequence.Success[_]]
            reconciled.retainedjobs shouldBe 2
            reconciled.pendingreservations shouldBe 0
            _physical_rows(fresh).size shouldBe 2
          } finally fresh.close()
        }
      }

      "S11 reconcile a committed checkpoint whose native acknowledgement was lost using the actual advanced revision" in {
        Given("one admitted native snapshot and a provider that commits its checkpoint before losing acknowledgement")
        _with_database(DurableJobStorageResourcePolicy(1)) { first =>
          val initial = _success(first.store.create(_record("a"), _access)(using first.context))
          val changed = _next(initial.record, "exact-completed-value")
          first.entity.loseupdateack = true
          When("the failed acknowledgement is recovered by a fresh native owner and the Job advances again")
          val failed = first.store.checkpoint(initial, changed, _access)(using first.context)
          val held = _success(first.store.resourceSnapshot()(using first.context))
          val fresh = _open(first.database, first.policy)
          try {
            val observed = _success(fresh.store.load("a", _access)(using fresh.context)).get
            val advanced = fresh.store.checkpoint(observed, _next(observed.record, "later"), _access)(using fresh.context)
            val usage = _success(fresh.store.resourceSnapshot()(using fresh.context))
            Then("advanced native OCC fences the ambiguous candidate and no admitted record is rolled back")
            failed shouldBe a[Consequence.Failure[_]]
            held.pendingreservations shouldBe 1
            observed.record shouldBe changed
            observed.providerrevision.value shouldBe initial.providerrevision.value + 1L
            advanced.map(_.providerrevision.value) shouldBe Consequence.Success(initial.providerrevision.value + 2L)
            usage.pendingreservations shouldBe 0
            usage.canonicalbytes shouldBe _bytes(_success(advanced).record)
          } finally fresh.close()
        }
      }

      "S11 expose confirmation failure while keeping the admitted native record and its persisted charge" in {
        Given("a successful Job checkpoint whose following ledger confirmation is refused by the provider")
        _with_database(DurableJobStorageResourcePolicy(2)) { first =>
          val initial = _success(first.store.create(_record("a"), _access)(using first.context))
          val changed = _next(initial.record, "confirmation-evidence")
          first.entity.afterjob = Some(() => first.entity.failconfirmation = true)
          When("native acknowledgement is followed by failed quota confirmation and a fresh owner later reconciles")
          val failed = first.store.checkpoint(initial, changed, _access)(using first.context)
          val held = _success(first.store.resourceSnapshot()(using first.context))
          val fresh = _open(first.database, first.policy)
          try {
            val retained = fresh.store.load("a", _access)(using fresh.context)
            val next = fresh.store.create(_record("b"), _access)(using fresh.context)
            Then("confirmation is explicitly unavailable, while revision proof recovers the charge without rollback")
            failed shouldBe a[Consequence.Failure[_]]
            _failure_message(failed) should include(DurableJobStorageResourcePolicy.confirmationLabel)
            _failure_message(failed) should not include "private-confirmation-secret"
            held.pendingreservations shouldBe 1
            retained.map(_.map(_.record)) shouldBe Consequence.Success(Some(changed))
            next shouldBe a[Consequence.Success[_]]
            _success(fresh.store.resourceSnapshot()(using fresh.context)).pendingreservations shouldBe 0
          } finally fresh.close()
        }
      }

      "S11 confirm an already retired lease after another valid writer advanced while returning the original acknowledgement" in {
        Given("a second native owner that advances the acknowledged Job before the first owner confirms its lease")
        _with_database(DurableJobStorageResourcePolicy(1)) { first =>
          val initial = _success(first.store.create(_record("a"), _access)(using first.context))
          val second = _open(first.database, first.policy)
          val later = new AtomicReference[DurableJobStoreSnapshot]()
          first.entity.afterjob = Some(() => {
            val observed = _success(second.store.load("a", _access)(using second.context)).get
            later.set(_success(second.store.checkpoint(observed, _next(observed.record, "later"), _access)(using second.context)))
          })
          try {
            When("the first checkpoint receives its original acknowledgement after the later native revision is already durable")
            val acknowledged = first.store.checkpoint(initial, _next(initial.record, "first"), _access)(using first.context)
            val current = first.store.load("a", _access)(using first.context)
            Then("equal-or-later real revision proof admits confirmation without overwriting the newer Job")
            acknowledged.map(_.record) shouldBe Consequence.Success(_next(initial.record, "first"))
            acknowledged.map(_.providerrevision.value) shouldBe Consequence.Success(initial.providerrevision.value + 1L)
            current shouldBe Consequence.Success(Some(later.get()))
            later.get().providerrevision.value shouldBe initial.providerrevision.value + 2L
            _success(first.store.resourceSnapshot()(using first.context)).pendingreservations shouldBe 0
          } finally second.close()
        }
      }

      "S11 report a lost quota-confirmation acknowledgement while preserving committed native Job and accounting" in {
        Given("an admitted native Job whose confirmation CAS commits and then loses its provider acknowledgement")
        _with_database(DurableJobStorageResourcePolicy(2)) { first =>
          val initial = _success(first.store.create(_record("a"), _access)(using first.context))
          first.entity.afterjob = Some(() => first.entity.losequotaack = true)
          val changed = _next(initial.record, "confirmed-native-evidence")
          When("a checkpoint commits both native facts but its quota acknowledgement is lost, followed by a fresh owner")
          val failed = first.store.checkpoint(initial, changed, _access)(using first.context)
          val fresh = _open(first.database, first.policy)
          try {
            val retained = fresh.store.load("a", _access)(using fresh.context)
            val usage = _success(fresh.store.resourceSnapshot()(using fresh.context))
            val remaining = fresh.store.create(_record("b"), _access)(using fresh.context)
            Then("failure remains explicit while the confirmed charge and exact record survive without rollback")
            failed shouldBe a[Consequence.Failure[_]]
            _failure_message(failed) should include(DurableJobStorageResourcePolicy.confirmationLabel)
            _failure_message(failed) should not include "private-lost-quota-ack"
            retained.map(_.map(_.record)) shouldBe Consequence.Success(Some(changed))
            usage.retainedjobs shouldBe 1
            usage.canonicalbytes shouldBe _bytes(changed)
            usage.pendingreservations shouldBe 0
            remaining shouldBe a[Consequence.Success[_]]
          } finally fresh.close()
        }
      }

      "S12 hold suspended checkpoint bytes until a different native revision actually fences its original OCC write" in {
        Given("a suspended large checkpoint and a fresh owner that still observes its unchanged expected Job revision")
        val initialrecord = _record("a")
        val grown = _next(initialrecord, "x" * 1024)
        _with_database(DurableJobStorageResourcePolicy(2, _bytes(grown))) { first =>
          val initial = _success(first.store.create(initialrecord, _access)(using first.context))
          val fresh = _open(first.database, first.policy)
          val admitted = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val outcome = new AtomicReference[Consequence[DurableJobStoreSnapshot]]()
          first.entity.beforejob = Some(() => { admitted.countDown(); _await(release) })
          val worker = new Thread(() => { outcome.set(first.store.checkpoint(initial, grown, _access)(using first.context)); () })
          try {
            When("a fresh owner first attempts extra bytes, then advances the actual native revision before the old writer resumes")
            worker.start()
            _await(admitted)
            val before = _success(fresh.store.resourceSnapshot()(using fresh.context))
            val blocked = fresh.store.create(_record("b"), _access)(using fresh.context)
            val fenced = _success(fresh.store.checkpoint(initial, _next(initialrecord), _access)(using fresh.context))
            val admittednew = fresh.store.create(_record("b"), _access)(using fresh.context)
            release.countDown()
            _join(Vector(worker))
            val retained = fresh.store.load("a", _access)(using fresh.context)
            Then("unchanged revision holds the pending maximum, and only a proven advance releases bytes and blocks the resumed old write")
            before.pendingreservations shouldBe 1
            before.canonicalbytes shouldBe _bytes(grown)
            blocked shouldBe a[Consequence.Failure[_]]
            admittednew shouldBe a[Consequence.Success[_]]
            outcome.get() shouldBe a[Consequence.Failure[_]]
            retained shouldBe Consequence.Success(Some(fenced))
            _success(fresh.store.resourceSnapshot()(using fresh.context)).pendingreservations shouldBe 0
          } finally { release.countDown(); _join(Vector(worker)); fresh.close() }
        }
      }

      "S13 persist at most eight ambiguous checkpoint candidates for one Job and refuse the ninth before native mutation" in {
        Given("a current native Job and a provider that refuses each admitted checkpoint before writing its unchanged row")
        _with_database(DurableJobStorageResourcePolicy(1)) { fixture =>
          given ExecutionContext = fixture.context
          val initial = _success(fixture.store.create(_record("a"), _access))
          fixture.entity.failupdatebefore = true
          When("eight candidates obtain persisted reservations and a ninth attempts admission")
          val attempts = Vector.fill(8)(fixture.store.checkpoint(initial, _next(initial.record, "candidate"), _access))
          val held = _success(fixture.store.resourceSnapshot())
          val ninth = fixture.store.checkpoint(initial, _next(initial.record, "ninth"), _access)
          val ledger = _success(DurableJobStorageQuotaCodec.decode(_quota_text(fixture)))
          Then("each opaque token remains charged, the ninth never reaches Job update and failure diagnostics stay closed")
          attempts.foreach(_ shouldBe a[Consequence.Failure[_]])
          ninth shouldBe a[Consequence.Failure[_]]
          held.pendingreservations shouldBe 8
          held.retainedjobs shouldBe 1
          fixture.entity.jobcalls shouldBe 8
          fixture.provider.jobupdates shouldBe 0
          ledger.entries.head.pending.map(_.token).distinct.size shouldBe 8
          ledger.entries.head.pending.foreach(pending => pending.token should fullyMatch regex "[0-9a-f]{32}")
          _failure_message(ninth) should not include "candidate"
          _failure_message(attempts.head) should not include "private-provider-secret"
          fixture.store.load("a", _access) shouldBe Consequence.Success(Some(initial))
        }
      }

      "S13 stop after eight stale native ledger CAS attempts and conceal provider refusal details" in {
        Given("a surviving native ledger and an instrumented provider returning stale OCC on every reservation attempt")
        _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
          given ExecutionContext = fixture.context
          val initial = _success(fixture.store.create(_record("a"), _access))
          val before = fixture.entity.quotacalls
          fixture.entity.stalecount = 8
          When("a new Job exhausts the fixed retry budget")
          val stale = fixture.store.create(_record("b"), _access)
          val attempts = fixture.entity.quotacalls - before
          fixture.entity.failconfirmation = true
          val refused = fixture.store.create(_record("sensitive-id"), _access)
          Then("both bounded refusal paths leave Job authority intact and return constant resource labels")
          stale shouldBe a[Consequence.Failure[_]]
          refused shouldBe a[Consequence.Failure[_]]
          attempts shouldBe 8
          fixture.provider.jobcreates shouldBe 1
          _failure_message(stale) should include(DurableJobStorageResourcePolicy.unavailableLabel)
          _failure_message(refused) should include(DurableJobStorageResourcePolicy.unavailableLabel)
          _failure_message(refused) should not include "sensitive-id"
          _failure_message(refused) should not include "private-confirmation-secret"
          fixture.store.load("a", _access) shouldBe Consequence.Success(Some(initial))
        }
      }
    }
  }

  "Closed operational quota authority" should {
    "surviving ledger and conservative retained union" which {
      "S14 refuse every malformed surviving ledger without reset, rewrite or a native Job write" in {
        val variants = Vector("malformed", "duplicate-key", "unknown-field", "unknown-version", "null", "null-entries",
          "primitive", "missing-field", "duplicate-id", "unordered-id", "committed-pair", "bad-hash", "bad-revision",
          "bad-bytes", "over-pending", "duplicate-token", "over-entries", "over-id", "over-policy", "oversize", "deep")
        variants.zipWithIndex.foreach { case (name, index) =>
          Given(s"a real surviving quota entity containing $name operational JSON and two retained authorized native Jobs")
          _with_database(DurableJobStorageResourcePolicy(3)) { fixture =>
            given ExecutionContext = fixture.context
            val original = _success(fixture.store.create(_record("a"), _access))
            _success(fixture.store.create(_record("b"), _access))
            val malformed = _closed_variants(_quota_text(fixture))(index)
            _replace_quota(fixture, malformed)
            val before = fixture.provider.jobcreates
            When("a valid signed native Job requests capacity through the surviving invalid ledger")
            val result = fixture.store.create(_record("c"), _access)
            val after = _quota_text(fixture)
            val readable = fixture.store.load("a", _access)
            Then("invalid authority is refused rather than reconstructed, while original authorized reads keep their native facts")
            result shouldBe a[Consequence.Failure[_]]
            fixture.provider.jobcreates shouldBe before
            after shouldBe malformed
            readable shouldBe Consequence.Success(Some(original))
            _failure_message(result) should not include "canonicalJson"
          }
        }
      }

      "S14 admit an inclusive 16MiB operational wire value and then publish only validated generated ledger bytes" in {
        Given("a valid provider-persisted quota ledger padded with whitespace to exactly 16MiB UTF-8")
        _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
          given ExecutionContext = fixture.context
          _success(fixture.store.create(_record("a"), _access))
          val original = _quota_text(fixture)
          val padded = original + (" " * (16777216 - original.length))
          _replace_quota(fixture, padded)
          When("the actual owner loads that inclusive ledger and admits another native Job")
          val snapshot = fixture.store.resourceSnapshot()
          val accepted = fixture.store.create(_record("b"), _access)
          val generated = _quota_text(fixture)
          Then("the separate ledger wire budget admits the full value and validates the subsequent generated closed JSON")
          padded.getBytes(StandardCharsets.UTF_8).length shouldBe 16777216
          snapshot.map(_.retainedjobs) shouldBe Consequence.Success(1)
          accepted shouldBe a[Consequence.Success[_]]
          DurableJobStorageQuotaCodec.decode(generated) shouldBe a[Consequence.Success[_]]
          generated.getBytes(StandardCharsets.UTF_8).length should be <= 16777216
          _physical_rows(fixture).size shouldBe 2
        }
      }

      "S14 hold physically missing committed identities as ghosts and import additional actual rows only when their union fits" in {
        Given("a persisted committed native Job that is physically removed by a controlled fixture without deletion authority for its ledger")
        _with_database(DurableJobStorageResourcePolicy(3)) { fixture =>
          given ExecutionContext = fixture.context
          val original = _success(fixture.store.create(_record("a"), _access))
          _success(fixture.provider.delete(_job_collection, DataStore.EntryId(_job_id("a"))))
          _seed(fixture, "b", "not-json")
          When("a new Job reconciles the complete actual inventory with the missing committed identity")
          val accepted = fixture.store.create(_record("c"), _access)
          val usage = _success(fixture.store.resourceSnapshot())
          val full = fixture.store.create(_record("d"), _access)
          Then("the ghost still occupies its original charge and the corrupt actual row joins the retained union without repair")
          accepted shouldBe a[Consequence.Success[_]]
          full shouldBe a[Consequence.Failure[_]]
          usage.retainedjobs shouldBe 3
          usage.canonicalbytes shouldBe _bytes(original.record) + 8L + _bytes(_record("c"))
          fixture.store.load("a", _access) shouldBe Consequence.Success(None)
          _raw_job(fixture, "b").getString("canonicalJson") shouldBe Some("not-json")
          _physical_rows(fixture).size shouldBe 2
        }
        Given("a missing committed ghost and enough untracked native rows to overflow the retained union")
        _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
          given ExecutionContext = fixture.context
          _success(fixture.store.create(_record("a"), _access))
          _success(fixture.provider.delete(_job_collection, DataStore.EntryId(_job_id("a"))))
          _seed(fixture, "b", "not-json")
          _seed(fixture, "c", "not-json")
          val before = _quota_text(fixture)
          val writes = fixture.provider.jobcreates
          When("a writer observes two physical rows plus the conservatively held missing identity")
          val refused = fixture.store.create(_record("d"), _access)
          Then("an over-capacity union refuses without forgetting or overwriting the ghost")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.provider.jobcreates shouldBe writes
          _quota_text(fixture) shouldBe before
          _success(fixture.store.resourceSnapshot()).retainedjobs shouldBe 1
        }
      }

      "S14 refuse a native provider revision that moves backward rather than reducing committed accounting" in {
        Given("a successfully checkpointed Job followed by a controlled raw provider rollback to its older detached revision")
        _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
          given ExecutionContext = fixture.context
          val initial = _success(fixture.store.create(_record("a"), _access))
          _success(fixture.store.checkpoint(initial, _next(initial.record), _access))
          val before = _quota_text(fixture)
          val row = DurableJobStoreEntity(_job_id("a"), "a", _canonical(initial.record)).toRecord() ++
            Record.dataAuto("cncf_revision" -> initial.providerrevision.value)
          _success(fixture.provider.save(_job_collection, DataStore.EntryId(_job_id("a")), row))
          When("a further native writer reconciles the backward actual revision")
          val refused = fixture.store.create(_record("b"), _access)
          Then("the surviving newer quota authority is refused without repair or a new Job write")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.provider.jobcreates shouldBe 1
          _quota_text(fixture) shouldBe before
        }
      }
    }

    "actual provider capability and routing" which {
      "S15 refuse nonsearchable, non-native-CAS and split routing while preserving admitted read authority" in {
        Vector("nonsearchable", "non-native-cas", "split-routing").foreach { mode =>
          Given(s"an admitted native Job and $mode capacity routing presented by its runtime")
          _with_database(DurableJobStorageResourcePolicy(2)) { fixture =>
            given ExecutionContext = fixture.context
            val initial = _success(fixture.store.create(_record("a"), _access))
            val separate = new ObservedDataStore(SqlDataStore.sqlite(fixture.database.toString))
            try {
              mode match {
                case "nonsearchable" => fixture.context.dataStoreSpace.useDataStore(new OpaqueDataStore(fixture.provider))
                case "non-native-cas" => fixture.provider.nativecas = false
                case _ =>
                  fixture.provider.accept = _ == _job_collection
                  separate.accept = _ == _quota_collection
                  fixture.context.dataStoreSpace.useDataStore(fixture.provider).addDataStore(separate)
              }
              val creates = fixture.provider.creates + separate.creates
              val calls = fixture.entity.quotacalls
              When("that runtime attempts both native create and checkpoint under unsupported quota ownership")
              val create = fixture.store.create(_record("b"), _access)
              val checkpoint = fixture.store.checkpoint(initial, _next(initial.record), _access)
              val loaded = fixture.store.load("a", _access)
              Then("no quota or Job write is attempted, and ordinary authorized reads retain their original native snapshot")
              Vector(create, checkpoint).foreach(_ shouldBe a[Consequence.Failure[_]])
              fixture.provider.creates + separate.creates shouldBe creates
              fixture.entity.quotacalls shouldBe calls
              fixture.provider.jobupdates shouldBe 0
              loaded shouldBe Consequence.Success(Some(initial))
              _failure_message(create) should include(DurableJobStorageResourcePolicy.unavailableLabel)
              _failure_message(create) should not include fixture.database.toString
            } finally { val _ = separate.delegate.closeC() }
          }
        }
      }
    }

    "generated native lifecycle sequences" which {
      "S16 keep actual persisted facts and conservative pending union within finite policy through create, checkpoint, refusal and restart" in {
        Given("seeded generated finite native operation sequences, including ambiguous create failures and independently reopened SQLite owners")
        val property = Prop.forAll(Gen.choose(1, 4), Gen.listOfN(18, Gen.choose(0, 4))) { (capacity, sequence) =>
          _with_database(DurableJobStorageResourcePolicy(capacity, 20000L)) { first =>
            var current = first
            var opened = Vector.empty[Fixture]
            var snapshots = Vector.empty[DurableJobStoreSnapshot]
            try {
              sequence.zipWithIndex.foreach { case (operation, ordinal) =>
                Given("the current actual provider and its previously acknowledged native Jobs")
                When(s"generated operation $ordinal requests create, checkpoint, refusal or a fresh owner")
                operation match {
                  case 0 | 4 =>
                    current.entity.failcreatebefore = operation == 4
                    val result = current.store.create(_record(s"job-$ordinal"), _access)(using current.context)
                    result.toOption.foreach(value => snapshots :+= value)
                    current.entity.failcreatebefore = false
                  case 1 if snapshots.nonEmpty =>
                    val snapshot = snapshots.head
                    val result = current.store.checkpoint(snapshot, _next(snapshot.record, "x" * (ordinal + 1)), _access)(using current.context)
                    result.toOption.foreach(value => snapshots = value +: snapshots.tail)
                  case 2 =>
                    current = _open(first.database, first.policy)
                    opened :+= current
                  case _ =>
                    snapshots.headOption.foreach { snapshot =>
                      current.store.load(snapshot.record.body.identity.jobId, _access.copy(subjectId = "foreign"))(using current.context) shouldBe
                        a[Consequence.Failure[_]]
                    }
                }
                val rows = _physical_rows(current)
                val rawquota = _raw_quota(current)(using current.context)
                val usage = rawquota.map(_ => _success(current.store.resourceSnapshot()(using current.context)))
                Then("native retained count/bytes and the provider-persisted conservative union stay within the same lowered policy")
                rows.size should be <= capacity
                rows.map(_.getString("canonicalJson").get.getBytes(StandardCharsets.UTF_8).length.toLong).sum should be <= 20000L
                usage.foreach { value =>
                  value.retainedjobs should (be >= rows.size and be <= capacity)
                  value.canonicalbytes should be <= 20000L
                  value.pendingreservations should be <= capacity * 8
                }
                snapshots.foreach { snapshot =>
                  current.store.load(snapshot.record.body.identity.jobId, _access)(using current.context) shouldBe
                    Consequence.Success(Some(snapshot))
                }
              }
            } finally opened.foreach(_.close())
          }
          true
        }
        When("ScalaCheck interprets the seeded operation sequences against actual native SQLite stores")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(12).withInitialSeed(Seed(6970931L)), property)
        Then("every active generated lifecycle respects finite persisted authority across restarts and refusals")
        checked.passed shouldBe true
      }
    }
  }

  private final case class Fixture(
    database: Path,
    policy: DurableJobStorageResourcePolicy,
    provider: ObservedDataStore,
    entity: ObservedEntityStore,
    context: ExecutionContext,
    store: DurableJobStore
  ) {
    def close(): Unit = { val _ = provider.delegate.closeC() }
  }

  private def _with_database(policy: DurableJobStorageResourcePolicy)(body: Fixture => Unit): Unit = {
    val root = Files.createTempDirectory("durable-job-quota-")
    val fixture = _open(root.resolve("jobs.db"), policy)
    try body(fixture)
    finally {
      fixture.close()
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { val _ = Files.deleteIfExists(path) })
      finally stream.close()
    }
  }

  private def _open(database: Path, policy: DurableJobStorageResourcePolicy): Fixture = {
    val provider = new ObservedDataStore(SqlDataStore.sqlite(database.toString))
    val entity = new ObservedEntityStore
    val context = ExecutionContext.test()
    context.dataStoreSpace.useDataStore(provider)
    context.entityStoreSpace.addEntityStore(entity)
    Fixture(database, policy, provider, entity, context, new DurableJobStore(entity, policy))
  }

  private def _canonical(record: DurableJobRecord): String = _success(DurableJobRecordCodec.canonicalJson(record))
  private def _bytes(record: DurableJobRecord): Long = _canonical(record).getBytes(StandardCharsets.UTF_8).length.toLong
  private def _signed(body: DurableJobRecordBody): DurableJobRecord = _success(DurableJobRecord.create(body))

  private def _record(jobid: String, text: String = "small", revision: Long = 1L): DurableJobRecord = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", Some("action-a"))
    _signed(DurableJobRecordBody(
      DurableJobIdentity(jobid, revision, _now, _now),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(DurableJobLifecycleStatus.Succeeded, 7, DurableRunMode.Async,
        DurableRetryEvidence(Vector(DurableAttemptEvidence(1, _now, Some(_now), DurableAttemptOutcome.Succeeded, None)), 3, None, false, false),
        DurableScheduleState(Some(_now), Some(_now), Some(_now))),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation, DurableTaskTarget("operation", operation),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed), None)),
      Vector.empty, DurableResultOutcome.Succeeded(DurableValue.Absent), Vector.empty,
      Vector.fill(4)(DurableDiagnosticSummary("execution", "ok", "info", text)), None,
      DurableDefinitionSnapshot("definition", "daily", 1, 1L, "a" * 64, None, None, Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
    ))
  }

  private def _next(record: DurableJobRecord, text: String = "small"): DurableJobRecord =
    _signed(record.body.copy(
      identity = record.body.identity.copy(revision = record.body.identity.revision + 1L,
        updatedAt = record.body.identity.updatedAt.plusSeconds(1L)),
      diagnostics = Vector.fill(4)(DurableDiagnosticSummary("execution", "ok", "info", text))
    ))

  private def _with_retention(record: DurableJobRecord, state: DurableDeletionState): DurableJobRecord =
    _signed(record.body.copy(retention = state match {
      case DurableDeletionState.Active => record.body.retention
      case DurableDeletionState.Deleted => DurableRetentionState(None, None, Some(_now), state, None)
      case DurableDeletionState.Tombstoned => DurableRetentionState(None, None, Some(_now), state,
        Some(DurableTombstone(_now, "retained", None)))
      case _ => fail("unsupported retention fixture")
    }))

  private def _job_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
  private def _quota_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobStorageQuotaEntity.Collection)
  private def _job_id(jobid: String): EntityId = _success(DurableJobStoreEntity.entityId(jobid))
  private def _quota_id: EntityId = _success(DurableJobStorageQuotaEntity.entityId)

  private def _seed(fixture: Fixture, jobid: String, text: String)(using ctx: ExecutionContext): Unit = {
    val id = _job_id(jobid)
    val row = DurableJobStoreEntity(id, jobid, text).toRecord() ++ Record.dataAuto("cncf_revision" -> 1L)
    _success(fixture.provider.create(_job_collection, DataStore.EntryId(id), row))
  }

  private def _raw_job(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): Record =
    _success(fixture.provider.load(_job_collection, DataStore.EntryId(_job_id(jobid)))).get

  private def _raw_quota(fixture: Fixture)(using ctx: ExecutionContext): Option[Record] =
    _success(fixture.provider.load(_quota_collection, DataStore.EntryId(_quota_id)))

  private def _quota_text(fixture: Fixture)(using ctx: ExecutionContext): String =
    _raw_quota(fixture).get.getString("canonicalJson").get

  private def _physical_rows(fixture: Fixture): Vector[Record] =
    _success(fixture.provider.delegate.search(_job_collection,
      QueryDirective(Query.Empty, order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(32)))).records

  private def _replace_quota(fixture: Fixture, text: String)(using ctx: ExecutionContext): Unit = {
    val current = _raw_quota(fixture).get
    _success(fixture.provider.save(_quota_collection, DataStore.EntryId(_quota_id),
      current ++ Record.dataAuto("canonicalJson" -> text)))
  }

  private def _closed_variants(base: String): Vector[String] = {
    val json = parse(base).toOption.get
    val entries = json.asObject.get("entries").get.asArray.get.toVector
    val first = entries.head
    val pending = Json.obj("token" -> Json.fromString("a" * 32),
      "expectedProviderRevision" -> Json.fromLong(1L), "canonicalBytes" -> Json.fromLong(1L),
      "canonicalSha256" -> Json.fromString("a" * 64))
    def _entries_(values: Vector[Json]): String =
      json.mapObject(_.add("entries", Json.fromValues(values))).noSpaces
    def _first_(name: String, value: Json): String =
      _entries_(first.mapObject(_.add(name, value)) +: entries.tail)
    Vector(
      "{",
      base.replace("\"version\":1", "\"version\":1,\"version\":1"),
      json.mapObject(_.add("signature", Json.fromString("unknown"))).noSpaces,
      json.mapObject(_.add("version", Json.fromInt(2))).noSpaces,
      "null",
      json.mapObject(_.add("entries", Json.Null)).noSpaces,
      json.mapObject(_.add("maximumJobs", Json.fromString("3"))).noSpaces,
      json.mapObject(_.remove("version")).noSpaces,
      _entries_(Vector(first, first)),
      _entries_(entries.reverse),
      _first_("committedSha256", Json.Null),
      _first_("committedSha256", Json.fromString("G" * 64)),
      _first_("committedProviderRevision", Json.fromLong(0L)),
      _first_("committedBytes", Json.fromLong(1048577L)),
      _first_("pending", Json.fromValues(Vector.tabulate(9)(index => pending.mapObject(_.add("token", Json.fromString(f"$index%032x")))))),
      _entries_(entries.map(_.mapObject(_.add("pending", Json.arr(pending))))),
      _entries_(Vector.fill(4097)(first)),
      _first_("jobId", Json.fromString("x" * 513)),
      json.mapObject(_.add("maximumCanonicalBytes", Json.fromLong(67108865L))).noSpaces,
      " " * 16777217,
      "[" * 17 + "0" + "]" * 17
    )
  }

  private def _await(latch: CountDownLatch): Unit =
    if (!latch.await(10L, TimeUnit.SECONDS)) throw new IllegalStateException("bounded native quota barrier timed out")

  private def _join(workers: Vector[Thread]): Unit = workers.foreach { worker =>
    worker.join(10000L)
    if (worker.isAlive) {
      worker.interrupt()
      worker.join(10000L)
    }
    worker.isAlive shouldBe false
  }

  private def _failure_message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case Consequence.Success(_) => fail("expected a closed native capacity refusal")
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private final class ObservedEntityStore extends StandardEntityStore {
    var beforequotacreate: Option[() => Unit] = None
    var beforequota: Option[() => Unit] = None
    var beforejob: Option[() => Unit] = None
    var afterjob: Option[() => Unit] = None
    var failcreatebefore = false
    var failupdatebefore = false
    var losecreateack = false
    var loseupdateack = false
    var losequotaack = false
    var failconfirmation = false
    var stalecount = 0
    var quotacalls = 0
    var quotacreatecalls = 0
    var jobcalls = 0
    var searchcalls = 0

    override def create[T](entity: T, options: EntityCreateOptions)
        (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      val job = tc.collection(entity) == DurableJobStoreEntity.Collection
      if (tc.collection(entity) == DurableJobStorageQuotaEntity.Collection) {
        quotacreatecalls += 1
        val action = beforequotacreate
        beforequotacreate = None
        action.foreach(_())
      }
      if (job && failcreatebefore) throw new IllegalStateException("private-provider-secret /private/path raw-json")
      val result = super.create(entity, options)
      if (job && result.isSuccess && losecreateack) throw new IllegalStateException("private-lost-create-ack")
      result
    }

    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
        (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val job = tc.id(changes).collection == DurableJobStoreEntity.Collection
      if (job) {
        jobcalls += 1
        val action = beforejob
        beforejob = None
        action.foreach(_())
        if (failupdatebefore) throw new IllegalStateException("private-provider-secret /private/path raw-json")
      } else if (tc.id(changes).collection == DurableJobStorageQuotaEntity.Collection) {
        quotacalls += 1
        val action = beforequota
        beforequota = None
        action.foreach(_())
        if (failconfirmation) throw new IllegalStateException("private-confirmation-secret")
        if (stalecount > 0) {
          stalecount -= 1
          val expected = expectedRevision.get
          return EntityConcurrencyMetadata.staleMutation(expected, _success(expected.nextC))
        }
      }
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (!job && result.isSuccess && losequotaack) throw new IllegalStateException("private-lost-quota-ack")
      if (job && result.isSuccess) {
        val action = afterjob
        afterjob = None
        action.foreach(_())
        if (loseupdateack) throw new IllegalStateException("private-lost-update-ack")
      }
      result
    }

    override def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[org.goldenport.cncf.directive.SearchResult[T]] = {
      searchcalls += 1
      throw new IllegalStateException("EntityStore search is forbidden for quota inventory")
    }
    override def searchInternal[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[org.goldenport.cncf.directive.SearchResult[T]] = {
      searchcalls += 1
      throw new IllegalStateException("EntityStore internal search is forbidden for quota inventory")
    }
  }

  private final class ObservedDataStore(val delegate: SqlDataStore)
      extends DataStore with SearchableDataStore with EntityVersionedMutationDataStore {
    var creates = 0
    var jobcreates = 0
    var jobupdates = 0
    var countcalls = 0
    var requests = Vector.empty[QueryDirective]
    var largestcensus = 0
    var censusmode = "normal"
    var nativecas = true
    var accept: DataStore.CollectionId => Boolean = _ => true

    def isAccept(cid: DataStore.CollectionId): Boolean = accept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      if (nativecas) delegate.entityMutationProviderCapabilities else EntityMutationProviderCapabilities.empty
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      creates += 1
      if (collection == _job_collection) jobcreates += 1
      delegate.create(collection, id, record)
    }
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] =
      delegate.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] = {
      requests :+= directive
      delegate.search(collection, directive).map { result =>
        largestcensus = math.max(largestcensus, directive.offset + result.records.size)
        val rows = result.records
        if (rows.isEmpty) result
        else censusmode match {
          case "oversupply" => result.copy(records = Vector.fill(4)(rows.head))
          case "duplicate" => result.copy(records = Vector.fill(rows.size)(rows.head))
          case "unordered" => result.copy(records = rows.reverse)
          case "bad-id" => result.copy(records = rows.updated(0, rows.head.upsertSingle("jobId", "foreign")))
          case "bad-revision" => result.copy(records = rows.updated(0, rows.head.upsertSingle("cncf_revision", 0L)))
          case "bad-row" => result.copy(records = rows.updated(0, Record(rows.head.fields.filterNot(_.key == "canonicalJson"))))
          case "oversize" => result.copy(records = rows.updated(0, rows.head.upsertSingle("canonicalJson", "x" * 1048577)))
          case "cursor" => result.copy(cursor = Some(Cursor("private-cursor")))
          case _ => result
        }
      }
    }
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] = {
      countcalls += 1
      throw new IllegalStateException("default count is forbidden for quota inventory")
    }
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      delegate.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      if (plan.collection == _job_collection) jobupdates += 1
      delegate.compareAndSetEntity(plan)
    }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      if (plan.collection == _job_collection) jobupdates += 1
      delegate.mutateVersionedEntity(plan)
    }
    def prepare(tx: TransactionContext): PrepareResult = record_prepare(CommitRecorder.noop)
    def commit(tx: TransactionContext): Unit = record_commit(CommitRecorder.noop)
    def abort(tx: TransactionContext): Unit = record_abort(CommitRecorder.noop)
  }

  private final class OpaqueDataStore(delegate: ObservedDataStore)
      extends DataStore with EntityVersionedMutationDataStore {
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
