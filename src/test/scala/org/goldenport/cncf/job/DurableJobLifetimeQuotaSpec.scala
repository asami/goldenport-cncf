package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
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
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy,
  EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy}
import org.goldenport.cncf.unitofwork.{CommitRecorder, PrepareResult, TransactionContext}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Real native OCC lifetime evidence, exact reopen and conservative ambiguity.
 * B1 does not establish engine installation or real two-process acceptance.
 *
 * @since   Oct.  1, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class DurableJobLifetimeQuotaSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _now = Instant.parse("2026-10-01T01:02:03Z")
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _default = DurableJobLifetimeQuotaPolicy.default

  "Native durable lifetime authority" should {
    "root admission and exact publication" which {
      "LQ1 admit inclusive task, history, admission and held ceilings before the caller writes its signed Job" in {
        Given("an exact one-entry 12288-byte policy and one normal with one supplied note")
        val policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(2L, 97L, 1L), 1, 12288L)
        _with_database(policy) { fixture =>
          given ExecutionContext = fixture.context
          val record = _record("inclusive")
          When("a root reserves exact capacity before native Job publication")
          val lease = _success(fixture.quota.reserveRoot(record, _access, 1, 1L))
          val pending = _success(fixture.quota.resourceSnapshot())
          val signedbefore = fixture.store.load("inclusive", _access)
          val writes = fixture.provider.quotawrites
          val duplicate = fixture.quota.reserveRoot(record, _access, 1, 1L)
          val countover = fixture.quota.reserveRoot(_record("one-more"), _access, 0)
          Then("Pending pays exactly once, publishes no signed Job and refuses a duplicate and one more ledger entry")
          signedbefore shouldBe Consequence.Success(None)
          fixture.provider.jobcreates shouldBe 0
          pending.ledgerjobs shouldBe 1
          pending.heldbytes shouldBe 12288L
          pending.chargedtasks shouldBe 2L
          pending.historycredits shouldBe 97L
          pending.admissions shouldBe 1L
          pending.cancelcredits shouldBe 1L
          _failure(duplicate, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          _failure(countover, JobResourcePolicy.refused[Unit])
          fixture.provider.quotawrites shouldBe writes
          When("the caller creates the admitted signed record and publishes the issued root lease")
          val signed = _success(fixture.store.create(record, _access))
          val native = _raw_job(fixture, "inclusive")
          val published = _success(fixture.quota.publishRoot(lease, _access))
          val full = fixture.quota.reserveWork("inclusive", _access, 1)
          Then("the exact seed retains every prepaid credit and quota operations leave signed bytes, timestamps and revisions unchanged")
          published.seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(2L, 97L, 1L, 1L), 1L, Set.empty, Set.empty)
          published.cancelclaimed shouldBe false
          _failure(full, JobResourcePolicy.refused[Unit])
          _raw_job(fixture, "inclusive") shouldBe native
          fixture.store.load("inclusive", _access) shouldBe Consequence.Success(Some(signed))
          fixture.provider.requests.filter(_.limit == QueryLimit.Limit(1)) shouldBe Vector(QueryDirective(Query.Empty,
            order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(1), offset = 0))
          fixture.provider.countcalls shouldBe 0
          fixture.entity.searchcalls shouldBe 0
        }
        Given("the same normal with one byte less than its required prepaid held capacity")
        _with_database(policy.copy(maxledgerbytes = 12287L)) { fixture =>
          given ExecutionContext = fixture.context
          When("the one-over root requests reserveRoot")
          val result = fixture.quota.reserveRoot(_record("over"), _access, 1, 1L)
          Then("capacity refusal precedes every native quota and Job mutation")
          _failure(result, JobResourcePolicy.refused[Unit])
          fixture.provider.quotawrites shouldBe 0
          fixture.provider.jobcreates shouldBe 0
        }
        Given("the inclusive1024-normal root cardinality under the default finite policy")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          When("the complete maximum-sized ticket is reserved without creating a signed Job")
          val lease = fixture.quota.reserveRoot(_record("maximum-batch"), _access, 1024)
          val usage = _success(fixture.quota.resourceSnapshot())
          Then("the exact inclusive batch prepays all2048 charged slots and its complete history")
          lease shouldBe a[Consequence.Success[_]]
          usage.chargedtasks shouldBe 2048L
          usage.historycredits shouldBe 65568L
          usage.heldbytes shouldBe 4096L + 4096L * 2048L
          fixture.provider.jobcreates shouldBe 0
        }
      }

      "LQ2 reject malformed, foreign and unbounded proposals and lower-only policy before native publication" in {
        Given("a valid initial signed V2 candidate and malformed or unauthorized alternatives")
        val record = _record("candidate")
        val proposals = Vector((null, _access, 0, 0L), (record.copy(body = null), _access, 0, 0L),
          (record.copy(integrity = record.integrity.copy(unsignedBodySha256 = "b" * 64)), _access, 0, 0L),
          (record, _access.copy(subjectId = "foreign-private-subject"), 0, 0L),
          (record, null, 0, 0L), (_record("candidate", revision = 2L), _access, 0, 0L))
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          proposals.foreach { case (proposal, access, tasks, notes) =>
            Given("an empty native database and one malformed or foreign proposal")
            When("reserveRoot admits the complete candidate")
            val result = fixture.quota.reserveRoot(proposal, access, tasks, notes)
            Then("the closed invalid result precedes native writes")
            _failure(result, DurableJobLifetimeQuotaPolicy.invalid[Unit])
            fixture.provider.quotawrites shouldBe 0
            fixture.provider.jobcreates shouldBe 0
          }
          Vector((-1, 0L), (1025, 0L), (0, -1L), (0, Long.MaxValue)).foreach { case (tasks, notes) =>
            Given("an empty provider and an out-of-bound task or note cardinality")
            When("the otherwise valid root requests its prospective ticket")
            val result = fixture.quota.reserveRoot(record, _access, tasks, notes)
            Then("the existing capacity failure shape is stable and no authority is created")
            _failure(result, JobResourcePolicy.refused[Unit])
            fixture.provider.quotawrites shouldBe 0
          }
        }
        Vector(null, _default.copy(lifetime = null), _default.copy(lifetime = JobLifetimePolicy(8193L)),
          _default.copy(lifetime = JobLifetimePolicy(maxhistorycredits = 524289L)),
          _default.copy(lifetime = JobLifetimePolicy(maxadmissions = 16385L)),
          _default.copy(maxledgerjobs = 0), _default.copy(maxledgerjobs = 4097),
          _default.copy(maxledgerbytes = 0L), _default.copy(maxledgerbytes = 67108865L)).foreach { policy =>
          Given("an invalid or increased owner policy")
          _with_database(policy) { fixture =>
            given ExecutionContext = fixture.context
            When("a valid root requests native admission")
            val result = fixture.quota.reserveRoot(record, _access, 0)
            Then("the invalid configuration writes neither collection")
            _failure(result, DurableJobLifetimeQuotaPolicy.invalid[Unit])
            fixture.provider.quotawrites shouldBe 0
            fixture.provider.jobcreates shouldBe 0
          }
        }
        Given("a 512-byte multibyte opaque Job ID and its 513-byte extension")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val exactid = "界" * 170 + "ab"
          When("both opaque spellings request root admission")
          val accepted = fixture.quota.reserveRoot(_record(exactid), _access, 0)
          val before = fixture.provider.quotawrites
          val refused = fixture.quota.reserveRoot(_record(exactid + "c"), _access, 0)
          Then("the inclusive UTF-8 identity fits and one extra byte never reaches native mutation")
          exactid.getBytes(StandardCharsets.UTF_8).length shouldBe 512
          accepted shouldBe a[Consequence.Success[_]]
          _failure(refused, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          fixture.provider.quotawrites shouldBe before
          _ledger(fixture).entries.map(_.jobid) shouldBe Vector(exactid)
        }
        Given("null or malformed constructed operational values under a valid policy")
        val entry = DurableJobLifetimeQuotaEntry("constructed", "a" * 64, "b" * 64, "c" * 32,
          DurableJobLifetimeQuotaRootState.Pending, JobLifetimeSnapshot(2L, 96L, 1L, 1L),
          1L, Vector.empty, Vector.empty, false)
        Vector(null, DurableJobLifetimeQuotaRecord(null, Vector.empty),
          DurableJobLifetimeQuotaRecord(_default, null), DurableJobLifetimeQuotaRecord(_default, Vector(null)),
          DurableJobLifetimeQuotaRecord(_default, Vector(entry.copy(paid = null))),
          DurableJobLifetimeQuotaRecord(_default, Vector(entry.copy(normalids = null))),
          DurableJobLifetimeQuotaRecord(_default, Vector(entry.copy(claims = null))),
          DurableJobLifetimeQuotaRecord(_default, Vector(entry.copy(rootstate = null)))).foreach { value =>
          Given("one malformed constructed ledger with no native effect")
          When("both encoding and aggregate usage admit the complete value")
          val encoded = DurableJobLifetimeQuotaCodec.encode(value)
          val usage = DurableJobLifetimeQuotaCodec.usage(value)
          Then("both actual value operations refuse with the same closed invalid shape")
          _failure(encoded, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          _failure(usage, DurableJobLifetimeQuotaPolicy.invalid[Unit])
        }
      }

      "LQ3 publish only matching signed proof, recover a lost token, and keep repeated publication unchanged" in {
        Given("a Pending native root whose signed Job has not been created")
        _with_database(_default) { first =>
          val record = _record("root")
          val lease = _success(first.quota.reserveRoot(record, _access, 2)(using first.context))
          val pending = _raw_quota(first)(using first.context).get
          When("publication and executable snapshot are requested without signed proof")
          val absent = first.quota.publishRoot(lease, _access)(using first.context)
          val snapshot = first.quota.snapshot("root", _access)(using first.context)
          Then("no publication or refund occurs")
          _failure(absent, DurableJobLifetimeQuotaPolicy.confirmation[Unit])
          _failure(snapshot, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          _raw_quota(first)(using first.context) shouldBe Some(pending)
          Given("the caller's exact signed Job and a genuinely closed original provider")
          _success(first.store.create(record, _access)(using first.context))
          first.close()
          val fresh = _open(first.database, first.policy)
          try {
            When("fresh provider, EntityStore and context objects recover the stored Pending binding without the JVM lease")
            val recovered = _success(fresh.quota.recoverRoot("root", _access)(using fresh.context))
            val before = _raw_quota(fresh)(using fresh.context)
            val repeated = fresh.quota.publishRoot(lease, _access)(using fresh.context)
            val writes = fresh.provider.quotawrites
            val again = fresh.quota.recoverRoot("root", _access)(using fresh.context)
            Then("the exact root promotes once and both issued-lease and token-free repeats preserve paid counters and native revision")
            recovered.seed.paid shouldBe JobLifetimeSnapshot(4L, 160L, 1L, 1L)
            repeated shouldBe Consequence.Success(recovered)
            again shouldBe Consequence.Success(recovered)
            fresh.provider.quotawrites shouldBe writes
            _raw_quota(fresh)(using fresh.context) shouldBe before
            Given("a later legitimate native checkpoint with the same immutable owner")
            val signed = _success(fresh.store.load("root", _access)(using fresh.context)).get
            _success(fresh.store.checkpoint(signed, _next(record), _access)(using fresh.context))
            When("the original matching lease publishes again after signed history advanced")
            val later = fresh.quota.publishRoot(lease, _access)(using fresh.context)
            Then("Published counters remain exact and never reset to the root candidate")
            later shouldBe Consequence.Success(recovered)
            _raw_quota(fresh)(using fresh.context) shouldBe before
          } finally fresh.close()
        }
        Given("a Pending root and a valid but different native canonical Job with the same owner")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val lease = _success(fixture.quota.reserveRoot(_record("mismatch"), _access, 1))
          _success(fixture.store.create(_record("mismatch", "different"), _access))
          val before = _raw_quota(fixture)
          When("the issued root lease presents mismatching native signed proof")
          val result = fixture.quota.publishRoot(lease, _access)
          Then("the Pending entry and its full charge survive exactly")
          _failure(result, DurableJobLifetimeQuotaPolicy.confirmation[Unit])
          _raw_quota(fixture) shouldBe before
        }
      }
    }

    "paid expansion, association and safety intent" which {
      "LQ4 retain exact unused credits across independent SQLite owners and refuse every policy mismatch" in {
        Given("one published native root with unused prepaid normals and additional exact work credits")
        val policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(8L, 512L, 8L), 4, 131072L)
        _with_database(policy) { first =>
          _publish(first, _record("shared"), 2)
          _success(first.quota.reserveWork("shared", _access, 1)(using first.context))
          _success(first.quota.associate("shared", _access, "opaque/legacy:id")(using first.context))
          val original = _raw_job(first, "shared")(using first.context)
          val expected = _success(first.quota.snapshot("shared", _access)(using first.context))
          val before = _raw_quota(first)(using first.context)
          val fresh = _open(first.database, policy)
          val increased = _open(first.database, policy.copy(lifetime = policy.lifetime.copy(maxchargedtasks = 10L)))
          val decreased = _open(first.database, policy.copy(maxledgerbytes = 65536L))
          try {
            When("new provider, EntityStore and context objects load equal, increased and decreased configurations")
            val loaded = fresh.quota.snapshot("shared", _access)(using fresh.context)
            val higher = increased.quota.snapshot("shared", _access)(using increased.context)
            val lower = decreased.quota.reserveControl("shared", _access)(using decreased.context)
            Then("only exact persisted policy equality admits the exact unused seed, without signed or quota reset")
            loaded shouldBe Consequence.Success(expected)
            expected.seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(6L, 256L, 2L, 2L),
              2L, Set("opaque/legacy:id"), Set.empty)
            _failure(higher, DurableJobLifetimeQuotaPolicy.invalid[Unit])
            _failure(lower, DurableJobLifetimeQuotaPolicy.invalid[Unit])
            _raw_quota(fresh)(using fresh.context) shouldBe before
            _raw_job(fresh, "shared")(using fresh.context) shouldBe original
            fresh.provider.quotawrites + increased.provider.quotawrites + decreased.provider.quotawrites shouldBe 0
            fresh.provider.delegate should not be first.provider.delegate
            fresh.entity should not be first.entity
            fresh.context should not be first.context
          } finally { fresh.close(); increased.close(); decreased.close() }
        }
      }

      "LQ5 pay exact work, Retry, control and annotation deltas and refuse prospective overflow unchanged" in {
        Given("a root and exact lowered lifetime task/history/admission ceilings")
        val policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(8L, 512L, 5L), 2, 36864L)
        _with_database(policy) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("deltas"), 1)
          val native = _raw_job(fixture, "deltas")
          When("two normals, one Retry normal, one control and 96 annotation rows consume the exact ceilings")
          val work = _success(fixture.quota.reserveWork("deltas", _access, 2))
          val retry = _success(fixture.quota.reserveRetry("deltas", _access, 1))
          val control = _success(fixture.quota.reserveControl("deltas", _access))
          val annotation = _success(fixture.quota.reserveAnnotation("deltas", _access, 96L))
          val before = _raw_quota(fixture)
          val writes = fixture.provider.quotawrites
          val refusals = Vector(fixture.quota.reserveWork("deltas", _access, 1),
            fixture.quota.reserveRetry("deltas", _access, 0), fixture.quota.reserveControl("deltas", _access),
            fixture.quota.reserveAnnotation("deltas", _access, 0L))
          Then("actual CAS snapshots match every delta and all one-over counters refuse before mutation")
          work.seed.paid shouldBe JobLifetimeSnapshot(6L, 256L, 2L, 2L)
          retry.seed.paid shouldBe JobLifetimeSnapshot(8L, 352L, 3L, 3L)
          control.seed.paid shouldBe JobLifetimeSnapshot(8L, 384L, 4L, 3L)
          annotation.seed.paid shouldBe JobLifetimeSnapshot(8L, 512L, 5L, 3L)
          annotation.seed.remaining shouldBe 4L
          refusals.foreach(result => _failure(result, JobResourcePolicy.refused[Unit]))
          fixture.provider.quotawrites shouldBe writes
          _raw_quota(fixture) shouldBe before
          _raw_job(fixture, "deltas") shouldBe native
          _success(fixture.quota.resourceSnapshot()).heldbytes shouldBe 36864L
        }
        Given("unused per-Job counters but a global held ceiling one byte below a new normal's prepaid encoding")
        _with_database(_default.copy(maxledgerbytes = 12287L)) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("global"), 0)
          val before = _raw_quota(fixture)
          When("work requests two more charged slots")
          val result = fixture.quota.reserveWork("global", _access, 1)
          Then("global byte refusal leaves actual canonical bytes and provider revision untouched")
          _failure(result, JobResourcePolicy.refused[Unit])
          _raw_quota(fixture) shouldBe before
          _success(fixture.quota.snapshot("global", _access)).seed.paid shouldBe JobLifetimeSnapshot(0L, 32L, 1L, 1L)
        }
      }

      "LQ6 associate paid opaque IDs once and claim each original compensation once across native owners" in {
        Given("two prepaid normals, including an inclusive 512-byte opaque task spelling")
        _with_database(_default) { first =>
          _publish(first, _record("tasks"), 2)
          val exactid = "界" * 170 + "ab"
          val fresh = _open(first.database, first.policy)
          try {
            When("normal association runs down its paid slots and duplicate, extra and oversized IDs are requested")
            val firstid = first.quota.associate("tasks", _access, exactid)(using first.context)
            val secondid = fresh.quota.associate("tasks", _access, "a:opaque/old")(using fresh.context)
            val before = _raw_quota(first)(using first.context)
            val duplicate = fresh.quota.associate("tasks", _access, exactid)(using fresh.context)
            val extra = first.quota.associate("tasks", _access, "unpaid")(using first.context)
            val oversized = first.quota.associate("tasks", _access, exactid + "c")(using first.context)
            Then("exact association is paid once, IDs retain their spelling and refused work never rewrites the native ledger")
            firstid shouldBe Consequence.Success(true)
            secondid shouldBe Consequence.Success(true)
            duplicate shouldBe Consequence.Success(true)
            _failure(extra, JobResourcePolicy.refused[Unit])
            _failure(oversized, DurableJobLifetimeQuotaPolicy.invalid[Unit])
            _raw_quota(first)(using first.context) shouldBe before
            _success(first.quota.snapshot("tasks", _access)(using first.context)).seed.remaining shouldBe 0L
            When("two owners claim the same original and an unknown original requests a claim")
            val claimed = fresh.quota.claimCompensation("tasks", _access, exactid)(using fresh.context)
            val after = _raw_quota(fresh)(using fresh.context)
            val replay = first.quota.claimCompensation("tasks", _access, exactid)(using first.context)
            val unknown = fresh.quota.claimCompensation("tasks", _access, "unknown")(using fresh.context)
            Then("the existing associated original is claimed once, with no duplicate native write")
            claimed shouldBe Consequence.Success(true)
            replay shouldBe Consequence.Success(false)
            unknown shouldBe Consequence.Success(false)
            _raw_quota(first)(using first.context) shouldBe after
            _success(first.quota.snapshot("tasks", _access)(using first.context)).seed.claims shouldBe Set(exactid)
          } finally fresh.close()
        }
      }

      "LQ7 preserve prepaid compensation and Cancel at exhaustion and clear intent only with accepted paid Retry" in {
        Given("a Published Job whose two work tickets fill the entire task and held-byte ceilings")
        val policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(4L, 256L, 4L), 1, 20480L)
        _with_database(policy) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("safety"), 1)
          _success(fixture.quota.reserveWork("safety", _access, 1))
          _success(fixture.quota.associate("safety", _access, "original"))
          val native = _raw_job(fixture, "safety")
          When("expansion fails but the owed original compensation and prepaid cancellation are claimed")
          val full = fixture.quota.reserveWork("safety", _access, 1)
          val cancel = fixture.quota.cancel("safety", _access)
          val claim = fixture.quota.claimCompensation("safety", _access, "original")
          val after = _raw_quota(fixture)
          val replay = fixture.quota.cancel("safety", _access)
          val duplicate = fixture.quota.associate("safety", _access, "original")
          val child = fixture.quota.associate("safety", _access, "new-child")
          val control = fixture.quota.reserveControl("safety", _access)
          Then("safety consumes no extra task/history/admission/held credit, preserves duplicate settlement and blocks new work")
          _failure(full, JobResourcePolicy.refused[Unit])
          claim shouldBe Consequence.Success(true)
          cancel shouldBe Consequence.Success(true)
          replay shouldBe Consequence.Success(false)
          duplicate shouldBe Consequence.Success(true)
          _failure(child, JobResourcePolicy.refused[Unit])
          _failure(control, JobResourcePolicy.refused[Unit])
          _raw_quota(fixture) shouldBe after
          _success(fixture.quota.snapshot("safety", _access)).seed.paid shouldBe JobLifetimeSnapshot(4L, 192L, 2L, 1L)
          When("annotation adds its own closure credit and an admitted zero-normal Retry buys the next cancellation allowance")
          val annotation = _success(fixture.quota.reserveAnnotation("safety", _access, 0L))
          val retry = _success(fixture.quota.reserveRetry("safety", _access, 0))
          val recancel = fixture.quota.cancel("safety", _access)
          val replaybefore = _raw_quota(fixture)
          val rereplay = fixture.quota.cancel("safety", _access)
          Then("annotation retains intent, only paid Retry clears it, and the renewed intent cannot be consumed twice")
          annotation.cancelclaimed shouldBe true
          annotation.seed.paid shouldBe JobLifetimeSnapshot(4L, 224L, 3L, 1L)
          retry.cancelclaimed shouldBe false
          retry.seed.paid shouldBe JobLifetimeSnapshot(4L, 256L, 4L, 2L)
          recancel shouldBe Consequence.Success(true)
          rereplay shouldBe Consequence.Success(false)
          _raw_quota(fixture) shouldBe replaybefore
          _success(fixture.quota.resourceSnapshot()).heldbytes shouldBe 20480L
          _raw_job(fixture, "safety") shouldBe native
        }
        Given("one prepaid cancellation allowance consumed to zero")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("zero-cancel"), 0)
          _success(fixture.quota.cancel("zero-cancel", _access))
          val before = _raw_quota(fixture)
          When("Cancel is replayed while the intent remains persisted and credits are zero")
          val replay = fixture.quota.cancel("zero-cancel", _access)
          Then("the existing intent returns false without payment or rewrite")
          replay shouldBe Consequence.Success(false)
          _raw_quota(fixture) shouldBe before
          _success(fixture.quota.snapshot("zero-cancel", _access)).seed.paid.cancelcredits shouldBe 0L
        }
      }
    }

    "reopen, ghosts and encoding bounds" which {
      "LQ8 preserve Pending, Published, normal, claim and Cancel facts after the original provider is closed" in {
        Given("a Published root with settled evidence and another admitted Pending root")
        _with_database(_default) { first =>
          _publish(first, _record("published"), 2)
          _success(first.quota.associate("published", _access, "original")(using first.context))
          _success(first.quota.claimCompensation("published", _access, "original")(using first.context))
          _success(first.quota.cancel("published", _access)(using first.context))
          val pendingrecord = _record("pending")
          _success(first.quota.reserveRoot(pendingrecord, _access, 1)(using first.context))
          val expected = _success(first.quota.snapshot("published", _access)(using first.context))
          val native = _raw_job(first, "published")(using first.context)
          val ledger = _raw_quota(first)(using first.context)
          first.close()
          val fresh = _open(first.database, first.policy)
          try {
            When("independently constructed provider, EntityStore and context objects read the exact native facts")
            val snapshot = fresh.quota.snapshot("published", _access)(using fresh.context)
            val absent = fresh.quota.recoverRoot("pending", _access)(using fresh.context)
            val usage = _success(fresh.quota.resourceSnapshot()(using fresh.context))
            Then("all unused counters, associations, claims and cancellation intent survive, while absent signed proof leaves Pending charged")
            first.closed shouldBe true
            snapshot shouldBe Consequence.Success(expected)
            expected.seed.remaining shouldBe 1L
            expected.seed.claims shouldBe Set("original")
            expected.cancelclaimed shouldBe true
            usage.ledgerjobs shouldBe 2
            usage.chargedtasks shouldBe 6L
            _failure(absent, DurableJobLifetimeQuotaPolicy.confirmation[Unit])
            _raw_quota(fresh)(using fresh.context) shouldBe ledger
            _raw_job(fresh, "published")(using fresh.context) shouldBe native
            When("the fresh caller supplies the originally admitted Pending root's exact signed Job")
            _success(fresh.store.create(pendingrecord, _access)(using fresh.context))
            val recovered = fresh.quota.recoverRoot("pending", _access)(using fresh.context)
            Then("the lost JVM token is unnecessary and exact unused prepaid counters promote without reseeding")
            recovered.map(_.seed.paid) shouldBe Consequence.Success(JobLifetimeSnapshot(2L, 96L, 1L, 1L))
            _success(fresh.quota.resourceSnapshot()(using fresh.context)).chargedtasks shouldBe 6L
          } finally fresh.close()
        }
      }

      "LQ9 retain ghosts, refuse missing authority beside native Jobs, and preserve every corrupt surviving ledger unchanged" in {
        Given("a Published root whose signed row is physically removed by a controlled fixture")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("ghost"), 1)
          val before = _raw_quota(fixture)
          _success(fixture.provider.delete(_job_collection, DataStore.EntryId(_job_id("ghost"))))
          When("a reader requests an executable seed for the missing signed Job")
          val missing = fixture.quota.snapshot("ghost", _access)
          val usage = _success(fixture.quota.resourceSnapshot())
          Then("the missing native row cannot refund its occupied identity or counters")
          _failure(missing, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          usage.ledgerjobs shouldBe 1
          usage.heldbytes shouldBe 12288L
          _raw_quota(fixture) shouldBe before
          fixture.store.load("ghost", _access) shouldBe Consequence.Success(None)
        }
        Given("an existing retained signed Job and a physically missing lifetime ledger")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("retained"), 0)
          val original = _raw_job(fixture, "retained")
          _success(fixture.provider.delete(_quota_collection, DataStore.EntryId(_quota_id)))
          val writes = fixture.provider.quotawrites
          val searches = fixture.provider.requests.size
          When("a fresh root and a read-only aggregate request the unavailable authority")
          val result = fixture.quota.reserveRoot(_record("new"), _access, 0)
          val read = fixture.quota.resourceSnapshot()
          Then("a single bounded nonempty census refuses bootstrap, and ordinary signed reads retain their authority")
          _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          _failure(read, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          fixture.provider.quotawrites shouldBe writes
          fixture.provider.requests.size shouldBe searches + 1
          fixture.provider.requests.last.limit shouldBe QueryLimit.Limit(1)
          _raw_quota(fixture) shouldBe None
          _raw_job(fixture, "retained") shouldBe original
          fixture.store.load("retained", _access).map(_.nonEmpty) shouldBe Consequence.Success(true)
        }
        _corrupt_names.zipWithIndex.foreach { case (name, ordinal) =>
          Given(s"a surviving real native lifetime ledger containing $name corruption")
          _with_database(_default) { fixture =>
            given ExecutionContext = fixture.context
            _publish(fixture, _record("corrupt"), 2)
            _success(fixture.quota.associate("corrupt", _access, "a"))
            _success(fixture.quota.associate("corrupt", _access, "b"))
            val original = _raw_job(fixture, "corrupt")
            val malformed = _corrupt_variants(_quota_text(fixture))(ordinal)
            _replace_quota(fixture, malformed)
            val before = _raw_quota(fixture)
            val writes = fixture.provider.quotawrites
            When("authenticated snapshot, expansion and root reservation encounter that complete surviving authority")
            val results = Vector(fixture.quota.snapshot("corrupt", _access),
              fixture.quota.reserveControl("corrupt", _access), fixture.quota.reserveRoot(_record("new"), _access, 0))
            Then("invalid authority is refused intact and original authorized signed reads continue unchanged")
            results.foreach(result => _failure(result, DurableJobLifetimeQuotaPolicy.invalid[Unit]))
            fixture.provider.quotawrites shouldBe writes
            _raw_quota(fixture) shouldBe before
            _raw_job(fixture, "corrupt") shouldBe original
            fixture.store.load("corrupt", _access).map(_.nonEmpty) shouldBe Consequence.Success(true)
            fixture.store.load("corrupt", _access.copy(subjectId = "foreign")) shouldBe a[Consequence.Failure[_]]
          }
        }
        Vector("oversupply", "cursor", "range", "failure", "throw").foreach { mode =>
          Given(s"an empty native Job collection with a malformed $mode bounded census response")
          _with_database(_default) { fixture =>
            given ExecutionContext = fixture.context
            fixture.provider.censusmode = mode
            When("root bootstrap requires one complete empty native proof")
            val result = fixture.quota.reserveRoot(_record("census"), _access, 0)
            Then("no quota is created and no count or EntityStore search substitutes for that proof")
            _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            fixture.provider.quotawrites shouldBe 0
            fixture.provider.requests.size shouldBe 1
            fixture.provider.countcalls shouldBe 0
            fixture.entity.searchcalls shouldBe 0
          }
        }
      }

      "LQ10 pay for worst escaped ID pairs and admit inclusive wire and entry ceilings independently" in {
        Given("an exact 12288-byte hold containing a worst escaped 512-byte Job ID and future normal/claim pair")
        val escapedid = "\u0001" * 511 + "x"
        _with_database(_default.copy(maxledgerjobs = 1, maxledgerbytes = 12288L)) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record(escapedid), 1)
          When("the full paid normal ID and its compensation claim are persisted at the exact global held ceiling")
          val normal = fixture.quota.associate(escapedid, _access, escapedid)
          val claim = fixture.quota.claimCompensation(escapedid, _access, escapedid)
          val usage = _success(fixture.quota.resourceSnapshot())
          val text = _quota_text(fixture)
          val before = _raw_quota(fixture)
          val full = fixture.quota.reserveWork(escapedid, _access, 1)
          Then("the prepaid slots cover both worst escaped spellings and no already-paid association or claim fails capacity")
          escapedid.getBytes(StandardCharsets.UTF_8).length shouldBe 512
          normal shouldBe Consequence.Success(true)
          claim shouldBe Consequence.Success(true)
          usage.heldbytes shouldBe 4096L + 4096L * 2L
          text.getBytes(StandardCharsets.UTF_8).length.toLong should be <= usage.heldbytes
          _failure(full, JobResourcePolicy.refused[Unit])
          _raw_quota(fixture) shouldBe before
          _ledger(fixture).entries.head.normalids shouldBe Vector(escapedid)
          _ledger(fixture).entries.head.claims shouldBe Vector(escapedid)
        }
        Given("one zero-normal Pending entry and an independent inclusive entry-count ceiling")
        _with_database(_default.copy(maxledgerjobs = 1, maxledgerbytes = 8192L)) { fixture =>
          given ExecutionContext = fixture.context
          _success(fixture.quota.reserveRoot(_record("one"), _access, 0))
          val before = _raw_quota(fixture)
          When("a second zero-normal entry requests the spare byte capacity")
          val result = fixture.quota.reserveRoot(_record("two"), _access, 0)
          Then("the independent entry ceiling refuses while the first exact 4096-byte hold remains")
          _failure(result, JobResourcePolicy.refused[Unit])
          _raw_quota(fixture) shouldBe before
          _success(fixture.quota.resourceSnapshot()).heldbytes shouldBe 4096L
        }
        Given("a real persisted ledger padded to the inclusive 64MiB operational wire ceiling")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("wire"), 0)
          val original = _quota_text(fixture)
          val padded = original + " " * (67108864 - original.getBytes(StandardCharsets.UTF_8).length)
          _replace_quota(fixture, padded)
          When("the native aggregate reads the inclusive wire and then sees one additional byte")
          val exact = fixture.quota.resourceSnapshot()
          val before = _raw_quota(fixture)
          _replace_quota(fixture, padded + " ")
          val oversized = _raw_quota(fixture)
          val over = fixture.quota.resourceSnapshot()
          Then("inclusive wire admits the complete record, reads do not rewrite, and one extra byte refuses before parser allocation")
          exact.map(_.heldbytes) shouldBe Consequence.Success(4096L)
          before.get.getString("canonicalJson") shouldBe Some(padded)
          _failure(over, DurableJobLifetimeQuotaPolicy.invalid[Unit])
          _raw_quota(fixture) shouldBe oversized
        }
      }
    }

    "native concurrency and acknowledged mutation" which {
      "LQ11 choose one native singleton bootstrap and one last-work winner with bounded joined actors" in {
        Given("two independent native owners simultaneously observing an empty provider and one root slot")
        _with_database(_default.copy(maxledgerjobs = 1, maxledgerbytes = 12288L)) { first =>
          val second = _open(first.database, first.policy)
          val entered = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val left = new AtomicReference[Consequence[DurableJobLifetimeQuotaLease]]()
          val right = new AtomicReference[Consequence[DurableJobLifetimeQuotaLease]]()
          first.entity.beforecreate = Some(() => { entered.countDown(); _await(release) })
          second.entity.beforecreate = Some(() => { entered.countDown(); _await(release) })
          val workers = Vector(new Thread(() => { left.set(first.quota.reserveRoot(_record("left"), _access, 1)(using first.context)); () }),
            new Thread(() => { right.set(second.quota.reserveRoot(_record("right"), _access, 1)(using second.context)); () }))
          try {
            When("both complete bounded empty proof and attempt guarded creation of the same native singleton")
            workers.foreach(_.start())
            _await(entered)
            release.countDown()
            _join(workers)
            val results = Vector(left.get(), right.get())
            val winner = results.flatMap(_.toOption).head
            val ledger = _ledger(first)(using first.context)
            Then("one lease is issued, one root remains charged and every actor physically terminates")
            results.count(_.isSuccess) shouldBe 1
            results.count(result => !result.isSuccess) shouldBe 1
            ledger.entries.size shouldBe 1
            ledger.entries.head.jobid shouldBe winner.jobid
            ledger.entries.head.roottoken shouldBe winner.roottoken
            _success(first.quota.resourceSnapshot()(using first.context)).heldbytes shouldBe 12288L
            first.provider.jobcreates + second.provider.jobcreates shouldBe 0
            workers.foreach(_.isAlive shouldBe false)
          } finally { release.countDown(); _join(workers); second.close() }
        }
        Given("a Published zero-normal root and two owners racing for the final two charged slots")
        val policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(2L, 256L, 4L), 1, 12288L)
        _with_database(policy) { first =>
          _publish(first, _record("last"), 0)
          val second = _open(first.database, first.policy)
          val entered = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val left = new AtomicReference[Consequence[DurableJobLifetimeQuotaSnapshot]]()
          val right = new AtomicReference[Consequence[DurableJobLifetimeQuotaSnapshot]]()
          first.entity.beforeupdate = Some(() => { entered.countDown(); _await(release) })
          second.entity.beforeupdate = Some(() => { entered.countDown(); _await(release) })
          val native = _raw_job(first, "last")(using first.context)
          val workers = Vector(new Thread(() => { left.set(first.quota.reserveWork("last", _access, 1)(using first.context)); () }),
            new Thread(() => { right.set(second.quota.reserveWork("last", _access, 1)(using second.context)); () }))
          try {
            When("both observed-revision updates contend at the actual SQLite CAS boundary")
            workers.foreach(_.start())
            _await(entered)
            release.countDown()
            _join(workers)
            val results = Vector(left.get(), right.get())
            val snapshot = _success(second.quota.snapshot("last", _access)(using second.context))
            Then("exactly one paid delta commits and the stale loser cannot spend the last capacity twice")
            results.count(_.isSuccess) shouldBe 1
            results.filter(result => !result.isSuccess).foreach(result => _failure(result, JobResourcePolicy.refused[Unit]))
            snapshot.seed.paid shouldBe JobLifetimeSnapshot(2L, 128L, 2L, 2L)
            snapshot.seed.remaining shouldBe 1L
            _raw_job(first, "last")(using first.context) shouldBe native
            workers.foreach(_.isAlive shouldBe false)
          } finally { release.countDown(); _join(workers); second.close() }
        }
      }

      "LQ12 retry only explicit stale up to eight attempts and conserve real create/update acknowledgement losses" in {
        Given("a Published root and a provider returning eight explicit stale revisions")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          _publish(fixture, _record("stale"), 0)
          val before = _raw_quota(fixture)
          val calls = fixture.entity.updatecalls
          fixture.entity.stalecount = 8
          When("work exhausts the fixed native retry budget")
          val stale = fixture.quota.reserveWork("stale", _access, 1)
          Then("eight total attempts leave all bytes and paid credits intact")
          _failure(stale, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          fixture.entity.updatecalls - calls shouldBe 8
          _raw_quota(fixture) shouldBe before
          Given("two explicit stale acknowledgements before a new root's successful native CAS")
          fixture.entity.stalecount = 2
          fixture.entity.tokens = Vector.empty
          When("one new Pending root is retried with the same candidate token")
          val lease = _success(fixture.quota.reserveRoot(_record("stable-token"), _access, 0))
          val tokens = fixture.entity.tokens.filter(_._1 == "stable-token").map(_._2)
          Then("all three mutation candidates retain the one issued random root binding")
          tokens.size shouldBe 3
          tokens.distinct shouldBe Vector(lease.roottoken)
          Given("a generic pre-write quota failure carrying private source detail")
          fixture.entity.failbefore = true
          val genericbefore = _raw_quota(fixture)
          val genericcalls = fixture.entity.updatecalls
          When("the admitted work encounters the generic failure")
          val generic = fixture.quota.reserveWork("stale", _access, 1)
          fixture.entity.failbefore = false
          Then("the closed unavailable result is returned once without retry or mutation")
          _failure(generic, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          fixture.entity.updatecalls - genericcalls shouldBe 1
          _raw_quota(fixture) shouldBe genericbefore
        }
        Given("a provider that creates a real Pending quota before throwing away its create acknowledgement")
        _with_database(_default) { first =>
          first.entity.losecreateack = true
          When("root reservation loses its actual native acknowledgement")
          val result = first.quota.reserveRoot(_record("uncertain-root"), _access, 1)(using first.context)
          val fresh = _open(first.database, first.policy)
          try {
            val usage = _success(fresh.quota.resourceSnapshot()(using fresh.context))
            val duplicate = fresh.quota.reserveRoot(_record("uncertain-root"), _access, 1)(using fresh.context)
            Then("no lease or Job execution authority is issued, but the fresh native owner observes the entire Pending charge")
            _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            _failure(duplicate, DurableJobLifetimeQuotaPolicy.invalid[Unit])
            usage.chargedtasks shouldBe 2L
            usage.ledgerjobs shouldBe 1
            fresh.store.load("uncertain-root", _access)(using fresh.context) shouldBe Consequence.Success(None)
            first.entity.createcalls shouldBe 1
          } finally fresh.close()
        }
        Given("a Published real Job and quota writes that commit before losing each acknowledgement")
        _with_database(_default) { first =>
          _publish(first, _record("uncertain"), 1)
          val fresh = _open(first.database, first.policy)
          try {
            first.entity.loseupdateack = true
            When("paid work commits before its native acknowledgement is lost")
            val work = first.quota.reserveWork("uncertain", _access, 1)(using first.context)
            Then("the current call refuses, while the fresh reader sees the exact actual monotonic paid work")
            _failure(work, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            _success(fresh.quota.snapshot("uncertain", _access)(using fresh.context)).seed.paid shouldBe JobLifetimeSnapshot(4L, 192L, 2L, 2L)
            When("association commits and then loses its acknowledgement")
            val normal = first.quota.associate("uncertain", _access, "original")(using first.context)
            Then("the paid association survives and duplicate settlement is available without another write")
            _failure(normal, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            fresh.quota.associate("uncertain", _access, "original")(using fresh.context) shouldBe Consequence.Success(true)
            When("a compensation claim commits and then loses its acknowledgement")
            val claim = first.quota.claimCompensation("uncertain", _access, "original")(using first.context)
            Then("the uncertain original claim is never issued a second time")
            _failure(claim, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            fresh.quota.claimCompensation("uncertain", _access, "original")(using fresh.context) shouldBe Consequence.Success(false)
            When("prepaid Cancel commits and then loses its acknowledgement")
            val cancel = first.quota.cancel("uncertain", _access)(using first.context)
            Then("the persisted safety intent remains and a fresh Cancel returns false without another credit decrement")
            _failure(cancel, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            fresh.quota.cancel("uncertain", _access)(using fresh.context) shouldBe Consequence.Success(false)
            val snapshot = _success(fresh.quota.snapshot("uncertain", _access)(using fresh.context))
            snapshot.cancelclaimed shouldBe true
            snapshot.seed.paid.cancelcredits shouldBe 1L
            snapshot.seed.claims shouldBe Set("original")
            _raw_job(fresh, "uncertain")(using fresh.context).getString("canonicalJson") shouldBe Some(_canonical(_record("uncertain")))
          } finally fresh.close()
        }
      }

      "LQ13 reconcile lost signed-create acknowledgement only from exact actual signed proof" in {
        Given("a Pending quota and a Job EntityStore that actually creates the signed Job before throwing")
        _with_database(_default) { first =>
          val record = _record("lost-job")
          _success(first.quota.reserveRoot(record, _access, 1)(using first.context))
          first.entity.losejoback = true
          When("the caller's native Job create loses its acknowledgement between reserve and publication")
          val create = first.store.create(record, _access)(using first.context)
          first.close()
          val fresh = _open(first.database, first.policy)
          try {
            val actual = _raw_job(fresh, "lost-job")(using fresh.context)
            val recovered = fresh.quota.recoverRoot("lost-job", _access)(using fresh.context)
            Then("the original call remains refused and only fresh matching signed proof publishes the original full lifetime charge")
            create shouldBe a[Consequence.Failure[_]]
            actual.getString("canonicalJson") shouldBe Some(_canonical(record))
            recovered.map(_.seed.paid) shouldBe Consequence.Success(JobLifetimeSnapshot(2L, 96L, 1L, 1L))
            _raw_job(fresh, "lost-job")(using fresh.context) shouldBe actual
          } finally fresh.close()
        }
        Vector("absent", "foreign", "advanced", "corrupt").foreach { mode =>
          Given(s"a Pending lifetime root with $mode native proof")
          _with_database(_default) { fixture =>
            given ExecutionContext = fixture.context
            val record = _record("proof")
            _success(fixture.quota.reserveRoot(record, _access, 1))
            if (mode != "absent") _success(fixture.store.create(record, _access))
            if (mode == "advanced") {
              val current = _success(fixture.store.load("proof", _access)).get
              _success(fixture.store.checkpoint(current, _next(record), _access))
            }
            if (mode == "corrupt") _replace_job(fixture, "proof", "{private-corrupt-body")
            val before = _raw_quota(fixture)
            val access = if (mode == "foreign") _access.copy(tenantId = "foreign") else _access
            When("token-free recovery and executable snapshot inspect the actual native proof")
            val recover = fixture.quota.recoverRoot("proof", access)
            val snapshot = fixture.quota.snapshot("proof", access)
            Then("missing, unauthorized, advanced-incompatible and corrupt signed facts neither publish nor refund Pending evidence")
            _failure(recover, DurableJobLifetimeQuotaPolicy.confirmation[Unit])
            _failure(snapshot, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            _raw_quota(fixture) shouldBe before
            _success(fixture.quota.resourceSnapshot()).chargedtasks shouldBe 2L
          }
        }
      }

      "LQ14 require exact native acknowledgement identity, bytes, positive advancement and compatible routes" in {
        Vector("identity", "bytes", "zero", "policy").foreach { mode =>
          Given(s"a real quota create that returns a wrong $mode acknowledgement after persistence")
          _with_database(_default) { fixture =>
            given ExecutionContext = fixture.context
            fixture.entity.createackmode = mode
            When("root reserve receives the incompatible native acknowledgement")
            val result = fixture.quota.reserveRoot(_record("bad-create-ack"), _access, 1)
            fixture.entity.createackmode = "normal"
            Then("no lease is returned, actual paid Pending evidence remains and the caller has written no signed Job")
            _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
            _success(fixture.quota.resourceSnapshot()).chargedtasks shouldBe 2L
            fixture.provider.jobcreates shouldBe 0
          }
        }
        Vector("identity", "bytes", "zero", "regressive", "policy").foreach { mode =>
          Given(s"a real quota CAS that commits and returns a wrong $mode acknowledgement")
          _with_database(_default) { first =>
            _publish(first, _record("bad-update-ack"), 0)
            first.entity.updateackmode = mode
            first.provider.zeroack = mode == "zero"
            When("paid work receives a mismatching authoritative result")
            val result = first.quota.reserveWork("bad-update-ack", _access, 1)(using first.context)
            val fresh = _open(first.database, first.policy)
            try {
              Then("the current operation refuses and a fresh native reader observes the actual committed paid value")
              _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
              _success(fresh.quota.snapshot("bad-update-ack", _access)(using fresh.context)).seed.paid shouldBe JobLifetimeSnapshot(2L, 128L, 2L, 2L)
              first.entity.updatecalls shouldBe 2
              first.provider.zeroacks shouldBe (if (mode == "zero") 1 else 0)
            } finally fresh.close()
          }
        }
        Vector("unsupported", "nonnative", "nonsearchable", "split", "descriptor").foreach { mode =>
          Given(s"an empty native provider graph with $mode lifetime routing")
          _with_database(_default) { fixture =>
            given ExecutionContext = fixture.context
            var extra = Option.empty[ObservedDataStore]
            try {
              mode match {
                case "unsupported" => fixture.context.dataStoreSpace.useDataStore(new OpaqueDataStore(fixture.provider, false))
                case "nonnative" => fixture.provider.nativecas = false
                case "nonsearchable" => fixture.context.dataStoreSpace.useDataStore(new OpaqueDataStore(fixture.provider, true))
                case "split" =>
                  val other = new ObservedDataStore(SqlDataStore.sqlite(fixture.database.toString))
                  extra = Some(other)
                  fixture.provider.accept = _ != _quota_collection
                  other.accept = _ == _quota_collection
                  fixture.context.dataStoreSpace.addDataStore(other)
                case "descriptor" =>
                  _install_bad_job_descriptor(fixture)
                case _ => fail("unknown native route fixture")
              }
              When("reserveRoot attempts native lifetime authority")
              val result = fixture.quota.reserveRoot(_record("route"), _access, 0)
              Then("incompatible providers or descriptors refuse before quota/native Job mutation")
              _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
              fixture.provider.quotawrites shouldBe 0
              fixture.provider.jobcreates shouldBe 0
              extra.foreach(_.quotawrites shouldBe 0)
            } finally extra.foreach(provider => { val _ = provider.delegate.closeC() })
          }
        }
      }

      "LQ15 redact every actual source exception and expose numeric aggregate facts while preserving ordinary signed read/auth results" in {
        Given("an actual Pending bootstrap source whose exception carries private path, identity, body and authorization text")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          fixture.provider.censusmode = "throw"
          When("the real source search throws during bounded empty proof")
          val result = fixture.quota.reserveRoot(_record("private-sensitive-id"), _access, 0)
          Then("the outward taxonomy, status, cause and exact message contain only the fixed unavailable label")
          _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          _message(result) should not include "private-sensitive-id"
          _message(result) should not include "/private/path"
          _message(result) should not include "authorization"
          fixture.provider.quotawrites shouldBe 0
        }
        Given("an authorized Published signed Job with intact original read and hostile-access behavior")
        _with_database(_default) { fixture =>
          given ExecutionContext = fixture.context
          val signed = _publish(fixture, _record("read"), 1)
          val authorized = fixture.store.load("read", _access)
          val denied = fixture.store.load("read", _access.copy(subjectId = "foreign"))
          val before = _raw_quota(fixture)
          fixture.entity.failload = true
          When("an actual signed native load throws before quota expansion")
          val result = fixture.quota.reserveControl("read", _access)
          fixture.entity.failload = false
          val aggregate = _success(fixture.quota.resourceSnapshot())
          Then("quota refusal is closed and signed reads/auth facts and numeric-only aggregate values remain independent")
          _failure(result, DurableJobLifetimeQuotaPolicy.unavailable[Unit])
          _message(result) should not include "private-body"
          fixture.store.load("read", _access) shouldBe authorized
          authorized shouldBe Consequence.Success(Some(signed))
          val nowdenied = fixture.store.load("read", _access.copy(subjectId = "foreign"))
          _failure(nowdenied, denied)
          _raw_quota(fixture) shouldBe before
          aggregate.productIterator.forall(_.isInstanceOf[java.lang.Number]) shouldBe true
          aggregate.chargedtasks shouldBe 2L
          aggregate.heldbytes shouldBe 12288L
        }
      }
    }

    "seeded actual native operation sequences" which {
      "LQ16 preserve monotonic paid authority and unchanged signed records through at least 24 fixed-seed finite sequences" in {
        Given("ScalaCheck seed6970933611 and finite real root/expansion/refusal/association/claim/Cancel/Retry/reopen sequences")
        val property = Prop.forAll(Gen.listOfN(24, Gen.choose(0, 9)), Gen.choose(0, 2)) { (generated, taskcount) =>
          val policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(8L, 640L, 8L), 4, 163840L)
          _with_database(policy) { first =>
            _publish(first, _record("main"), 2)
            var current = first
            var opened = Vector.empty[Fixture]
            var paid = _success(current.quota.snapshot("main", _access)(using current.context)).seed.paid
            val native = _raw_job(current, "main")(using current.context)
            try {
              (Vector.range(0, 10) ++ generated).zipWithIndex.foreach { case (operation, ordinal) =>
                Given("the exact current native ledger, authenticated signed Job and prior paid counters")
                val before = _raw_quota(current)(using current.context)
                When(s"generated actual action $ordinal performs operation $operation")
                val result: Consequence[?] = operation match {
                  case 0 =>
                    val record = _record(s"root-$ordinal")
                    val reserved = current.quota.reserveRoot(record, _access, taskcount)(using current.context)
                    reserved.toOption.foreach { lease =>
                      _success(current.store.create(record, _access)(using current.context))
                      _success(current.quota.publishRoot(lease, _access)(using current.context))
                    }
                    reserved
                  case 1 => current.quota.reserveWork("main", _access, taskcount)(using current.context)
                  case 2 => current.quota.reserveAnnotation("main", _access, ordinal.toLong)(using current.context)
                  case 3 => current.quota.associate("main", _access, s"task-${ordinal % 3}")(using current.context)
                  case 4 => current.quota.claimCompensation("main", _access, s"task-${ordinal % 3}")(using current.context)
                  case 5 => current.quota.cancel("main", _access)(using current.context)
                  case 6 => current.quota.reserveRetry("main", _access, taskcount)(using current.context)
                  case 7 =>
                    current.close()
                    current = _open(first.database, policy)
                    opened :+= current
                    current.quota.snapshot("main", _access)(using current.context)
                  case 8 => current.quota.reserveWork("main", _access, 1025)(using current.context)
                  case _ => current.quota.reserveControl("main", _access)(using current.context)
                }
                val snapshot = _success(current.quota.snapshot("main", _access)(using current.context))
                val usage = _success(current.quota.resourceSnapshot()(using current.context))
                Then("actual paid task/history/admission counters are monotonic, refusal preserves bytes, and original signed facts remain exact")
                snapshot.seed.paid.chargedtasks should (be >= paid.chargedtasks and be <= policy.lifetime.maxchargedtasks)
                snapshot.seed.paid.historycredits should (be >= paid.historycredits and be <= policy.lifetime.maxhistorycredits)
                snapshot.seed.paid.admissions should (be >= paid.admissions and be <= policy.lifetime.maxadmissions)
                snapshot.seed.claims.subsetOf(snapshot.seed.normalids) shouldBe true
                2L * (snapshot.seed.normalids.size.toLong + snapshot.seed.remaining) should be <= snapshot.seed.paid.chargedtasks
                usage.heldbytes should be <= policy.maxledgerbytes
                usage.ledgerjobs should be <= policy.maxledgerjobs
                _raw_job(current, "main")(using current.context) shouldBe native
                result match {
                  case Consequence.Failure(_) =>
                    _raw_quota(current)(using current.context) shouldBe before
                    _failure(result, JobResourcePolicy.refused[Unit])
                  case Consequence.Success(_) => result.isSuccess shouldBe true
                }
                paid = snapshot.seed.paid
              }
            } finally opened.foreach(_.close())
          }
          true
        }
        When("ScalaCheck executes every generated sequence against independently reopened real SQLite providers")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
          .withInitialSeed(Seed(6970933611L)), property)
        Then("at least 24 successful sequences include actual actions and direct should assertions at every step")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
    }
  }

  private final case class Fixture(database: Path, policy: DurableJobLifetimeQuotaPolicy,
    provider: ObservedDataStore, entity: ObservedEntityStore, context: ExecutionContext,
    store: DurableJobStore, quota: DurableJobLifetimeQuota) {
    private var _closed = false
    def closed: Boolean = _closed
    def close(): Unit = if (!_closed) { _closed = true; val _ = provider.delegate.closeC() }
  }

  private def _with_database(policy: DurableJobLifetimeQuotaPolicy)(body: Fixture => Unit): Unit = {
    val area = Paths.get("target", "durable-job-lifetime-quota", "work")
    val _ = Files.createDirectories(area)
    val root = Files.createTempDirectory(area, "native-")
    var fixture = Option.empty[Fixture]
    try {
      val opened = _open(root.resolve("jobs.db"), policy)
      fixture = Some(opened)
      body(opened)
    } finally {
      fixture.foreach(_.close())
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => { val _ = Files.deleteIfExists(path) })
      finally stream.close()
    }
  }

  private def _open(database: Path, policy: DurableJobLifetimeQuotaPolicy): Fixture = {
    val provider = new ObservedDataStore(SqlDataStore.sqlite(database.toString))
    val entity = new ObservedEntityStore
    val context = ExecutionContext.test()
    context.dataStoreSpace.useDataStore(provider)
    context.entityStoreSpace.addEntityStore(entity)
    Fixture(database, policy, provider, entity, context, new DurableJobStore(entity), new DurableJobLifetimeQuota(entity, policy))
  }

  private def _record(jobid: String, text: String = "root", revision: Long = 1L): DurableJobRecord = {
    val operation = DurableOperationReference("component-a", Some("service-a"), "run", Some("action-a"))
    _success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(jobid, revision, _now, _now),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(DurableJobLifecycleStatus.Submitted, 7, DurableRunMode.Async,
        DurableRetryEvidence(Vector.empty, 3, None, false, false), DurableScheduleState(Some(_now), None, None)),
      Vector(DurableTaskDescriptor("root", None, DurableTaskKind.Operation, DurableTaskTarget("operation", operation),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Pending), None)),
      Vector.empty, DurableResultOutcome.Pending, Vector.empty,
      Vector(DurableDiagnosticSummary("execution", "admission", "info", text)), None,
      DurableDefinitionSnapshot("definition", "daily", 1, 1L, "a" * 64, None, None, Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None))))
  }

  private def _next(record: DurableJobRecord): DurableJobRecord = _success(DurableJobRecord.createV2(record.body.copy(
    identity = record.body.identity.copy(revision = record.body.identity.revision + 1L, updatedAt = _now.plusSeconds(1L)),
    diagnostics = record.body.diagnostics :+ DurableDiagnosticSummary("execution", "checkpoint", "info", "later"))))

  private def _publish(fixture: Fixture, record: DurableJobRecord, tasks: Int): DurableJobStoreSnapshot = {
    given ExecutionContext = fixture.context
    val lease = _success(fixture.quota.reserveRoot(record, _access, tasks))
    val signed = _success(fixture.store.create(record, _access))
    _success(fixture.quota.publishRoot(lease, _access))
    signed
  }

  private def _canonical(record: DurableJobRecord): String = _success(DurableJobRecordCodec.canonicalJson(record))
  private def _install_bad_job_descriptor(fixture: Fixture)(using ctx: ExecutionContext): Unit = {
    val collection = DurableJobStoreEntity.Collection
    val realm = new EntityRealm[DurableJobStoreEntity](entityName = collection.name,
      loader = EntityLoader.fromEntityStore(fixture.entity),
      state = new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty)))
    val descriptor = EntityDescriptor(collectionId = collection,
      plan = EntityRuntimePlan(entityName = collection.name, memoryPolicy = EntityMemoryPolicy.StoreOnly,
        workingSet = None, partitionStrategy = PartitionStrategy.byEntityId, maxPartitions = 1,
        maxEntitiesPerPartition = 1, concurrencyPolicy = EntityConcurrencyPolicy.None),
      persistent = summon[EntityPersistent[DurableJobStoreEntity]],
      revisionBinding = Some(EntityRevisionBinding(EntityRevisionRepresentation.Detached)))
    ctx.entitySpace.registerEntity(collection.name, new EntityCollection(descriptor, EntityStorage(realm)))
  }
  private def _job_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
  private def _quota_collection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(DurableJobLifetimeQuotaEntity.Collection)
  private def _job_id(jobid: String): EntityId = _success(DurableJobStoreEntity.entityId(jobid))
  private def _quota_id: EntityId = _success(DurableJobLifetimeQuotaEntity.entityId)
  private def _raw_job(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): Record =
    _success(fixture.provider.load(_job_collection, DataStore.EntryId(_job_id(jobid)))).get
  private def _raw_quota(fixture: Fixture)(using ctx: ExecutionContext): Option[Record] =
    _success(fixture.provider.load(_quota_collection, DataStore.EntryId(_quota_id)))
  private def _quota_text(fixture: Fixture)(using ctx: ExecutionContext): String =
    _raw_quota(fixture).get.getString("canonicalJson").get
  private def _ledger(fixture: Fixture)(using ctx: ExecutionContext): DurableJobLifetimeQuotaRecord =
    _success(DurableJobLifetimeQuotaCodec.decode(_quota_text(fixture)))
  private def _replace_quota(fixture: Fixture, text: String)(using ctx: ExecutionContext): Unit =
    _success(fixture.provider.save(_quota_collection, DataStore.EntryId(_quota_id),
      _raw_quota(fixture).get ++ Record.dataAuto("canonicalJson" -> text)))
  private def _replace_job(fixture: Fixture, jobid: String, text: String)(using ctx: ExecutionContext): Unit =
    _success(fixture.provider.save(_job_collection, DataStore.EntryId(_job_id(jobid)),
      _raw_job(fixture, jobid) ++ Record.dataAuto("canonicalJson" -> text)))

  private val _corrupt_names = Vector("malformed", "duplicate-key", "unknown-field", "missing-field", "version",
    "null", "primitive", "null-entries", "overflow", "fraction", "negative", "policy", "entry-field", "entry-missing",
    "hash", "token", "state", "cancel-type", "cancel-count", "remaining", "normal-order", "normal-duplicate",
    "claim-subset", "pending-evidence", "over-id", "over-id-count", "duplicate-job", "unordered-job", "deep")

  private def _corrupt_variants(base: String): Vector[String] = {
    val json = parse(base).toOption.get
    val first = json.asObject.get("entries").get.asArray.get.head
    def _root_json_(name: String, value: Json): String = json.mapObject(_.add(name, value)).noSpaces
    def _entry_(name: String, value: Json): String = _root_json_("entries", Json.arr(first.mapObject(_.add(name, value))))
    Vector("{", base.replace("\"version\":1", "\"version\":1,\"version\":1"), _root_json_("private", Json.True),
      json.mapObject(_.remove("version")).noSpaces, _root_json_("version", Json.fromInt(2)), "null", "0",
      _root_json_("entries", Json.Null), _entry_("chargedTasks", parse("9223372036854775808").toOption.get),
      _entry_("historyCredits", Json.fromDoubleOrNull(1.5)), _entry_("admissions", Json.fromLong(-1L)),
      _root_json_("maximumChargedTasks", Json.fromLong(8191L)), _entry_("unknown", Json.Null),
      _root_json_("entries", Json.arr(first.mapObject(_.remove("remainingNormals")))),
      _entry_("ownerSha256", Json.fromString("G" * 64)), _entry_("rootToken", Json.fromString("a" * 31)),
      _entry_("rootState", Json.fromString("Released")), _entry_("cancelClaimed", Json.fromInt(1)),
      _entry_("cancelCredits", Json.fromLong(2L)), _entry_("remainingNormals", Json.fromLong(1L)),
      _entry_("normalIds", Json.arr(Json.fromString("b"), Json.fromString("a"))),
      _entry_("normalIds", Json.arr(Json.fromString("a"), Json.fromString("a"))),
      _entry_("compensationClaims", Json.arr(Json.fromString("unassociated"))),
      _entry_("rootState", Json.fromString("Pending")), _entry_("jobId", Json.fromString("x" * 513)),
      _entry_("normalIds", Json.fromValues(Vector.fill(4097)(Json.fromString("a")))),
      _root_json_("entries", Json.arr(first, first)),
      _root_json_("entries", Json.arr(first.mapObject(_.add("jobId", Json.fromString("z"))), first)),
      "[" * 17 + "0" + "]" * 17)
  }

  private def _await(latch: CountDownLatch): Unit =
    if (!latch.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("bounded native lifetime barrier timed out")
  private def _join(workers: Vector[Thread]): Unit = workers.foreach { worker =>
    worker.join(2500L)
    if (worker.isAlive) { worker.interrupt(); worker.join(2500L) }
    worker.isAlive shouldBe false
  }
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
  private def _message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case Consequence.Success(_) => fail("expected a closed lifetime failure")
  }
  private def _failure[A, B](result: Consequence[A], expected: Consequence[B]): Unit = (result, expected) match {
    case (Consequence.Failure(actual), Consequence.Failure(reference)) =>
      actual.status shouldBe reference.status
      actual.observation.taxonomy shouldBe reference.observation.taxonomy
      actual.observation.cause.kind shouldBe reference.observation.cause.kind
      actual.observation.phenomenon shouldBe reference.observation.phenomenon
      actual.displayMessage shouldBe reference.displayMessage
    case _ => fail("expected stable closed lifetime failure shape")
  }

  private final class ObservedEntityStore extends StandardEntityStore {
    var beforecreate: Option[() => Unit] = None
    var beforeupdate: Option[() => Unit] = None
    var failbefore = false
    var failload = false
    var losecreateack = false
    var loseupdateack = false
    var losejoback = false
    var createackmode = "normal"
    var updateackmode = "normal"
    var stalecount = 0
    var createcalls = 0
    var updatecalls = 0
    var searchcalls = 0
    var tokens = Vector.empty[(String, String)]

    override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[Option[EntityRevisionCarrier[T]]] = {
      if (failload && id.collection == DurableJobStoreEntity.Collection)
        throw new IllegalStateException("private-body /private/path authorization tenant-a subject-a")
      super.loadDetached(id)
    }

    override def create[T](entity: T, options: EntityCreateOptions)(using tc: EntityPersistentCreate[T],
      ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      val quota = tc.collection(entity) == DurableJobLifetimeQuotaEntity.Collection
      if (quota) {
        createcalls += 1
        _remember(tc.toStoreRecord(entity))
        val action = beforecreate
        beforecreate = None
        action.foreach(_())
      }
      val result = super.create(entity, options)
      if (quota && result.isSuccess && losecreateack) throw new IllegalStateException("private-lost-quota-create-ack")
      if (tc.collection(entity) == DurableJobStoreEntity.Collection && result.isSuccess && losejoback)
        throw new IllegalStateException("private-lost-signed-job-ack")
      if (!quota) result else result.map { acknowledged =>
        createackmode match {
          case "identity" => acknowledged.copy(id = _job_id("wrong-native-id"))
          case "bytes" => acknowledged.copy(record = acknowledged.record.map(_.upsertSingle("canonicalJson", "{}")))
          case "zero" => acknowledged.copy(record = acknowledged.record.map(_.upsertSingle("cncf_revision", 0L)))
          case "policy" => acknowledged.copy(record = acknowledged.record.map(row =>
            row.upsertSingle("canonicalJson", _wrong_policy(row.getString("canonicalJson").get))))
          case _ => acknowledged
        }
      }
    }

    override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
        (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      val quota = tc.id(changes).collection == DurableJobLifetimeQuotaEntity.Collection
      if (quota) {
        updatecalls += 1
        _remember(tc.toStoreRecord(changes))
        val action = beforeupdate
        beforeupdate = None
        action.foreach(_())
        if (failbefore) throw new IllegalStateException("private-body /private/path authorization secret root-token")
        if (stalecount > 0) {
          stalecount -= 1
          val expected = expectedRevision.get
          return EntityConcurrencyMetadata.staleMutation(expected, _success(expected.nextC))
        }
      }
      val result = super.updateDetached(changes, expectedRevision, executionPolicy)
      if (quota && result.isSuccess && loseupdateack) throw new IllegalStateException("private-lost-quota-update-ack")
      if (!quota) result else result.map { acknowledged =>
        val entity = acknowledged.entity.asInstanceOf[DurableJobLifetimeQuotaEntity]
        updateackmode match {
          case "identity" => acknowledged.copy(entity = entity.copy(id = _job_id("wrong-native-id")).asInstanceOf[T])
          case "bytes" => acknowledged.copy(entity = entity.copy(canonicaljson = "{}").asInstanceOf[T])
          case "policy" => acknowledged.copy(entity = entity.copy(canonicaljson = _wrong_policy(entity.canonicaljson)).asInstanceOf[T])
          case "regressive" => acknowledged.copy(revision = expectedRevision.get)
          case _ => acknowledged
        }
      }
    }

    override def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[org.goldenport.cncf.directive.SearchResult[T]] = {
      searchcalls += 1
      throw new IllegalStateException("EntityStore search is not native lifetime proof")
    }
    override def searchInternal[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext)
        : Consequence[org.goldenport.cncf.directive.SearchResult[T]] = {
      searchcalls += 1
      throw new IllegalStateException("EntityStore internal search is not native lifetime proof")
    }

    private def _remember(record: Record): Unit = record.getString("canonicalJson").foreach { text =>
      val ledger = _success(DurableJobLifetimeQuotaCodec.decode(text))
      tokens ++= ledger.entries.map(entry => entry.jobid -> entry.roottoken)
    }
    private def _wrong_policy(text: String): String = {
      val record = _success(DurableJobLifetimeQuotaCodec.decode(text))
      _success(DurableJobLifetimeQuotaCodec.encode(record.copy(policy = record.policy.copy(
        lifetime = record.policy.lifetime.copy(maxchargedtasks = 8191L)))))
    }
  }

  private final class ObservedDataStore(val delegate: SqlDataStore)
      extends DataStore with SearchableDataStore with EntityVersionedMutationDataStore with EntityCreateDataStore {
    var quotawrites = 0
    var jobcreates = 0
    var countcalls = 0
    var requests = Vector.empty[QueryDirective]
    var censusmode = "normal"
    var nativecas = true
    var zeroack = false
    var zeroacks = 0
    var accept: DataStore.CollectionId => Boolean = _ => true

    def isAccept(cid: DataStore.CollectionId): Boolean = accept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      if (nativecas) delegate.entityMutationProviderCapabilities else EntityMutationProviderCapabilities.empty
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      if (plan.collection == _quota_collection) quotawrites += 1
      if (plan.collection == _job_collection) jobcreates += 1
      delegate.createEntity(plan)
    }
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      if (collection == _quota_collection) quotawrites += 1
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
      censusmode match {
        case "failure" => Consequence.stateInvalid("private-source-body authorization /private/path")
        case "throw" => throw new IllegalStateException("private-source-body authorization /private/path private-sensitive-id")
        case _ => delegate.search(collection, directive).map { result => censusmode match {
          case "oversupply" => result.copy(records = Vector(Record.empty, Record.empty))
          case "cursor" => result.copy(cursor = Some(Cursor("private-cursor")))
          case "range" => result.copy(range = ResultRange.Limited(2))
          case _ => result
        } }
      }
    }
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] = {
      countcalls += 1
      throw new IllegalStateException("count is not bounded empty lifetime proof")
    }
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
      delegate.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      if (plan.collection == _quota_collection) quotawrites += 1
      delegate.compareAndSetEntity(plan).map {
        case EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(record))
            if plan.collection == _quota_collection && zeroack =>
          EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(record.upsertSingle("cncf_revision", 0L)))
        case other => other
      }
    }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      if (plan.collection == _quota_collection) quotawrites += 1
      delegate.mutateVersionedEntity(plan).map {
        case EntityVersionedMutationResult.Applied(record) if plan.collection == _quota_collection && zeroack =>
          zeroacks += 1
          EntityVersionedMutationResult.Applied(record.upsertSingle("cncf_revision", 0L))
        case other => other
      }
    }
    def prepare(tx: TransactionContext): PrepareResult = record_prepare(CommitRecorder.noop)
    def commit(tx: TransactionContext): Unit = record_commit(CommitRecorder.noop)
    def abort(tx: TransactionContext): Unit = record_abort(CommitRecorder.noop)
  }

  private final class OpaqueDataStore(delegate: ObservedDataStore, native: Boolean)
      extends DataStore with EntityVersionedMutationDataStore with EntityCreateDataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean = true
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
      if (native) delegate.entityMutationProviderCapabilities else EntityMutationProviderCapabilities.empty
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      delegate.createEntity(plan)
    }
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
