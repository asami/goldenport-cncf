package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy, EntityMutationExecutionPolicy, EntityPersistent,
  EntityRevisionCarrier, EntityStore, RevisionPreconditionPolicy, StandardEntityStore
}
import org.simplemodeling.model.datatype.EntityRevision
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Signed retained-lifecycle authority and bounded maintenance semantics.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobRetentionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DurableJobRetentionCommand.*
  import DurableJobRetentionFacet.*

  "Signed retention" should {
    "expiry and terminal admission" which {
      "E1 expire TTL inclusively after both expiry and finite retention floor" in {
        Given("JM69-09B1; signed terminal TTL records with and without a later finite floor")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val plain = _create(fixture, _record("ttl"))
        val floor = _create(fixture, _record("floor", retention = _ttl.copy(retainUntil = Some(_deadline.plusSeconds(10)))))
        val keep = _create(fixture, _record("keep", retention = _ttl.copy(expiresAt = None)))

        When("expiry is requested just before, at, and after the exact admitted deadlines")
        val before = fixture.maintenance.maintainObserved(plain, _access, Expire, _deadline.minusNanos(1))
        val earlyfloor = fixture.maintenance.maintainObserved(floor, _access, Expire, _deadline)
        val earlydelete = fixture.maintenance.maintainObserved(floor, _access, Delete, _deadline)
        val kept = fixture.maintenance.maintainObserved(keep, _access, Expire, Instant.MAX)
        val unchangedplain = _load(fixture, "ttl")
        val unchangedfloor = _load(fixture, "floor")
        val unchangedkeep = _load(fixture, "keep")
        val due = fixture.maintenance.maintainObserved(plain, _access, Expire, _deadline)
        val floorat = fixture.maintenance.maintainObserved(floor, _access, Expire, _deadline.plusSeconds(10))
        val expired = _load(fixture, "ttl")
        val floorexpired = _load(fixture, "floor")

        Then("only inclusive due TTLs advance exactly one revision, while Keep and future floors do not write")
        Vector(before, earlyfloor, earlydelete, kept) shouldBe Vector(NotDue, NotDue, NotDue, Kept)
        Vector(unchangedplain, unchangedfloor, unchangedkeep) shouldBe Vector(plain, floor, keep)
        Vector(due, floorat) shouldBe Vector(Expired, Expired)
        _advanced(plain, expired, _deadline)
        _advanced(floor, floorexpired, _deadline.plusSeconds(10))
        expired.record.body.retention.deletionState shouldBe DurableDeletionState.Expired
      }

      "E2 expire all terminal outcomes and refuse every V2 nonterminal without writes" in {
        DurableJobLifecycleStatus.values.toVector.foreach { status =>
          Given(s"JM69-09B1; signed V2 ${status.wire} with matching result and a due TTL")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val observed = _create(fixture, _record("status", status = status, v2 = true))
          val terminal = Set(DurableJobLifecycleStatus.Succeeded, DurableJobLifecycleStatus.Failed, DurableJobLifecycleStatus.Cancelled).contains(status)

          When("expiry and deletion are requested against the admitted lifecycle")
          val expiry = fixture.maintenance.maintainObserved(observed, _access, Expire, _deadline)
          val current = _load(fixture, "status")
          val deletion = fixture.maintenance.maintainObserved(current, _access, Delete, _deadline)
          val finalvalue = _load(fixture, "status")

          Then("terminal outcomes advance, while submitted, running and suspended preserve both revisions")
          expiry shouldBe (if (terminal) Expired else NotTerminal)
          deletion shouldBe (if (terminal) Deleted else NotTerminal)
          if (terminal) {
            _advanced(observed, current, _deadline)
            _advanced(current, finalvalue, _deadline)
          } else {
            current shouldBe observed
            finalvalue shouldBe observed
          }
        }
      }
    }

    "persisted legal hold" which {
      "E3 retain a hold across fresh stores and explicitly release its replacement floor" in {
        Given("JM69-09B1; a due TTL with a finite floor and test-owned shared provider")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val initial = _create(fixture, _record("hold", retention = _ttl.copy(retainUntil = Some(_deadline))))

        When("hold replaces the floor and a distinct store and context observe its signed value")
        val applied = fixture.maintenance.maintainObserved(initial, _access, Hold, _deadline)
        val freshcontext = ExecutionContext.withJobContext(fixture.context, JobContext.empty)
        val freshstore = new DurableJobStore(fixture.entitystore)
        val maintenance = new DurableJobRetentionMaintenance(freshstore)
        val held = _success(freshstore.load("hold", _access)(using freshcontext)).get
        val refusedexpiry = maintenance.maintainObserved(held, _access, Expire, Instant.MAX)(using freshcontext)
        val refuseddelete = maintenance.maintainObserved(held, _access, Delete, Instant.MAX)(using freshcontext)
        val reapplied = maintenance.maintainObserved(held, _access, Hold, _deadline)(using freshcontext)
        val unchanged = _success(freshstore.load("hold", _access)(using freshcontext)).get

        Then("canonical legal hold survives admission and repeated hold, expiry and deletion do not write")
        applied shouldBe HoldApplied
        _advanced(initial, held, _deadline)
        held.record.body.retention.retainUntil shouldBe Some(Instant.MAX)
        held.record.body.retention.expiresAt shouldBe initial.record.body.retention.expiresAt
        Vector(refusedexpiry, refuseddelete, reapplied) shouldBe Vector(Held, Held, Held)
        unchanged shouldBe held

        When("release explicitly replaces the floor and expiry is requested at that replacement")
        val replacement = _deadline.plusSeconds(10)
        val released = maintenance.maintainObserved(held, _access, ReleaseHold(Some(replacement)), _deadline)(using freshcontext)
        val afterrelease = _success(freshstore.load("hold", _access)(using freshcontext)).get
        val early = maintenance.maintainObserved(afterrelease, _access, Expire, _deadline)(using freshcontext)
        val expired = maintenance.maintainObserved(afterrelease, _access, Expire, replacement)(using freshcontext)
        val finalvalue = _success(freshstore.load("hold", _access)(using freshcontext)).get

        Then("release preserves expiry, enforces its explicit floor and permits inclusive expiry")
        released shouldBe HoldReleased
        _advanced(held, afterrelease, _deadline)
        afterrelease.record.body.retention shouldBe held.record.body.retention.copy(retainUntil = Some(replacement))
        early shouldBe NotDue
        expired shouldBe Expired
        _advanced(afterrelease, finalvalue, replacement)
      }

      "E4 allow active executions to hold and explicitly clear the floor" in {
        Given("JM69-09B1; a signed V2 running record which may be held during execution")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val initial = _create(fixture, _record("running-hold", status = DurableJobLifecycleStatus.Running, v2 = true))

        When("hold is persisted and release explicitly supplies None")
        val applied = fixture.maintenance.maintainObserved(initial, _access, Hold, _deadline)
        val held = _load(fixture, "running-hold")
        val released = fixture.maintenance.maintainObserved(held, _access, ReleaseHold(None), _deadline)
        val cleared = _load(fixture, "running-hold")

        Then("only the floor changes and execution/result facts remain identical")
        applied shouldBe HoldApplied
        released shouldBe HoldReleased
        _advanced(initial, held, _deadline)
        _advanced(held, cleared, _deadline)
        cleared.record.body.retention shouldBe _ttl
      }

      "E17 clear a terminal hold explicitly and permit already due expiry" in {
        Given("JM69-09B1; a terminal signed legal hold with an already due preserved expiry")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val held = _create(fixture, _record("clear-hold", retention = _ttl.copy(retainUntil = Some(Instant.MAX))))

        When("release explicitly clears the floor, then expiry observes that current signed checkpoint")
        val release = fixture.maintenance.maintainObserved(held, _access, ReleaseHold(None), _deadline)
        val cleared = _load(fixture, "clear-hold")
        val expiry = fixture.maintenance.maintainObserved(cleared, _access, Expire, _deadline)
        val expired = _load(fixture, "clear-hold")

        Then("no overwritten floor is restored and due expiry advances one further revision")
        release shouldBe HoldReleased
        cleared.record.body.retention shouldBe _ttl
        _advanced(held, cleared, _deadline)
        expiry shouldBe Expired
        _advanced(cleared, expired, _deadline)
        expired.record.body.retention shouldBe _ttl.copy(deletionState = DurableDeletionState.Expired)
      }

      "E5 refuse invalid releases, nonActive hold commands, backward time and exhausted revisions" in {
        val heldretention = _ttl.copy(retainUntil = Some(Instant.MAX))
        val invalidcases = Vector(
          (_ttl, ReleaseHold(None), _deadline, 1L, InvalidTransition),
          (heldretention, ReleaseHold(Some(Instant.MAX)), _deadline, 1L, InvalidTransition),
          (heldretention, ReleaseHold(Some(_instant.minusNanos(1))), _deadline, 1L, InvalidTransition),
          (heldretention, ReleaseHold(Some(null)), _deadline, 1L, InvalidTransition),
          (heldretention, ReleaseHold(null), _deadline, 1L, InvalidTransition),
          (_ttl, Hold, _instant, 1L, InvalidTransition),
          (_ttl, Delete, _instant, 1L, InvalidTransition),
          (_ttl, Hold, null, 1L, InvalidTransition),
          (_ttl, Expire, null, 1L, InvalidTransition),
          (_ttl, Hold, _deadline, Long.MaxValue, RevisionExhausted)
        ) ++ _inactive_retentions.flatMap(retention => Vector(
          (retention, Hold, _deadline, 1L, InvalidTransition),
          (retention, ReleaseHold(None), _deadline, 1L, InvalidTransition)
        ))
        invalidcases.zipWithIndex.foreach { case ((retention, command, now, revision, expected), index) =>
          Given(s"JM69-09B1; invalid transition case $index with signed canonical storage")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val observed = _create(fixture, _record("invalid", retention = retention, revision = revision))

          When("the inadmissible retention change is requested")
          val result = fixture.maintenance.maintainObserved(observed, _access, command, now)
          val current = _load(fixture, "invalid")

          Then("the closed refusal leaves semantic/provider revisions and canonical record unchanged")
          result shouldBe expected
          current shouldBe observed
        }
      }
    }

    "logical deletion and optimistic authority" which {
      "E6 delete terminal Keep, TTL and Expired values while retaining every body reference" in {
        Vector(_ttl.copy(expiresAt = None), _ttl, _ttl.copy(deletionState = DurableDeletionState.Expired)).zipWithIndex.foreach { case (retention, index) =>
          Given(s"JM69-09B1; terminal retained body $index with input, result, calltree and definition references")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val observed = _create(fixture, _record("delete", retention = retention))

          When("authorized deletion is checkpointed and expiry/deletion are repeated")
          val deleted = fixture.maintenance.maintainObserved(observed, _access, Delete, _deadline)
          val current = _load(fixture, "delete")
          val repeateddelete = fixture.maintenance.maintainObserved(current, _access, Delete, _deadline)
          val repeatedexpiry = fixture.maintenance.maintainObserved(current, _access, Expire, _deadline)
          val finalvalue = _load(fixture, "delete")

          Then("logical deletion records the injected time without clearing any reference and is idempotent")
          deleted shouldBe Deleted
          _advanced(observed, current, _deadline)
          current.record.body.retention shouldBe retention.copy(deletedAt = Some(_deadline), deletionState = DurableDeletionState.Deleted)
          Vector(repeateddelete, repeatedexpiry) shouldBe Vector(AlreadyDeleted, AlreadyDeleted)
          finalvalue shouldBe current
        }
      }

      "E7 preserve already Expired and Tombstoned outcomes without advancing revisions" in {
        Vector(
          (_ttl.copy(deletionState = DurableDeletionState.Expired), Expire, AlreadyExpired),
          (_tombstoned, Expire, Tombstoned),
          (_tombstoned, Delete, Tombstoned)
        ).foreach { case (retention, command, expected) =>
          Given("JM69-09B1; an existing signed inactive retention value")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val observed = _create(fixture, _record("stable", retention = retention))

          When("the already applied transition is requested")
          val result = fixture.maintenance.maintainObserved(observed, _access, command, _deadline)
          val current = _load(fixture, "stable")

          Then("the existing closed facet is stable without any write")
          result shouldBe expected
          current shouldBe observed
        }
      }

      "E8 preserve V1 and V2 integrity and refuse stale legitimate observed values" in {
        Vector(false, true).foreach { v2 =>
          Given(s"JM69-09B1; an authorized signed ${if (v2) "V2" else "V1"} observed snapshot")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val initial = _create(fixture, _record("version", v2 = v2))

          When("expiry advances and an older record or provider revision attempts deletion")
          val expiry = fixture.maintenance.maintainObserved(initial, _access, Expire, _deadline)
          val expired = _load(fixture, "version")
          val stalerecord = fixture.maintenance.maintainObserved(initial.copy(providerrevision = expired.providerrevision), _access, Delete, _deadline)
          val staleprovider = fixture.maintenance.maintainObserved(expired.copy(providerrevision = initial.providerrevision), _access, Delete, _deadline)
          val stale = fixture.maintenance.maintainObserved(initial, _access, Delete, _deadline)
          val retained = _load(fixture, "version")
          val canonical = _success(DurableJobRecordCodec.canonicalJson(expired.record))
          val decoded = DurableJobRecordCodec.decode(canonical, _access)

          Then("format and canonical integrity survive exactly one semantic/provider revision and stale values cannot overwrite")
          expiry shouldBe Expired
          _advanced(initial, expired, _deadline)
          expired.record.format shouldBe initial.record.format
          decoded shouldBe Consequence.Success(expired.record)
          Vector(stalerecord, staleprovider, stale) shouldBe Vector(Stale, Stale, Stale)
          retained shouldBe expired
        }
      }

      "E9 require exact tenant, subject, required scopes and the separate manage capability" in {
        val accesses = Vector(
          _access.copy(tenantId = "foreign"),
          _access.copy(subjectId = "foreign"),
          _access.copy(scopes = Set(DurableJobRetentionPolicy.manageScope)),
          _access.copy(scopes = Set("job.read")),
          _access.copy(subjectId = "foreign", scopes = _access.scopes ++ Set("admin", "*"))
        )
        accesses.foreach { access =>
          Given("JM69-09B1; intact signed observed storage and an insufficient or foreign caller")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val initial = _create(fixture, _record("access"))

          When("the caller requests a due expiry")
          val result = fixture.maintenance.maintainObserved(initial, access, Expire, _deadline)
          val current = _load(fixture, "access")

          Then("all scope sets retain exact subject/tenant admission and do not change requiredScopes")
          result shouldBe Refused
          current shouldBe initial
          current.record.body.authorization.requiredScopes shouldBe Set("job.read")
        }
      }

      "E10 refuse tampered snapshots and corrupt storage without repair or re-signing" in {
        Given("JM69-09B1; canonical storage and a caller snapshot with a tampered integrity digest")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val initial = _create(fixture, _record("corrupt"))
        val tampered = initial.copy(record = initial.record.copy(integrity = initial.record.integrity.copy(unsignedBodySha256 = "b" * 64)))
        val missing = initial.copy(record = _record("missing"))

        When("maintenance is presented the tampered value and then a separately corrupted provider value")
        val memoryrefusal = fixture.maintenance.maintainObserved(tampered, _access, Expire, _deadline)
        val missingrefusal = fixture.maintenance.maintainObserved(missing, _access, Expire, _deadline)
        val stillintact = _load(fixture, "corrupt")
        _corrupt(fixture, initial)
        val providerbefore = _raw(fixture, "corrupt")
        val storagerefusal = fixture.maintenance.maintainObserved(initial, _access, Expire, _deadline)
        val providerafter = _raw(fixture, "corrupt")
        val corruptload = fixture.store.loadStartupRecovery("corrupt", _access)

        Then("both corruption facets refuse and the exact corrupt provider revision/text remains unchanged")
        memoryrefusal shouldBe Corrupt
        missingrefusal shouldBe Missing
        stillintact shouldBe initial
        storagerefusal shouldBe Corrupt
        providerafter shouldBe providerbefore
        corruptload shouldBe DurableJobStoreStartupRecoveryLoad.Corrupt
      }

      "E11 refuse a checkpoint race while retaining the concurrent authoritative hold" in {
        Given("JM69-09B1; a test-owned provider hook which applies a concurrent hold immediately before optimistic update")
        val provider = new RacingEntityStore
        val fixture = _fixture(provider)
        given ExecutionContext = fixture.context
        val initial = _create(fixture, _record("race"))
        var concurrent: Option[DurableJobStoreSnapshot] = None
        provider.arm(() => {
          val freshcontext = ExecutionContext.withJobContext(fixture.context, JobContext.empty)
          val freshstore = new DurableJobStore(provider)
          val maintenance = new DurableJobRetentionMaintenance(freshstore)
          maintenance.maintainObserved(initial, _access, Hold, _deadline)(using freshcontext) shouldBe HoldApplied
          concurrent = _success(freshstore.load("race", _access)(using freshcontext))
        })

        When("expiry passes current-snapshot admission but loses the provider checkpoint race")
        val result = fixture.maintenance.maintainObserved(initial, _access, Expire, _deadline)
        val current = _load(fixture, "race")

        Then("the failed optimistic write returns only WriteRefused and never overwrites the concurrent record")
        result shouldBe WriteRefused
        Some(current) shouldBe concurrent
        _advanced(initial, current, _deadline)
        current.record.body.retention shouldBe _ttl.copy(retainUntil = Some(Instant.MAX))
      }
    }

    "bounded expiry sweep" which {
      "E12 report independent closed facets in source order and preserve earlier successful expiry" in {
        Given("JM69-09B1; ordered explicit candidates containing due, missing, corrupt, refused, held, nonterminal and future jobs")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val due = _create(fixture, _record("due"))
        val corrupt = _create(fixture, _record("bad"))
        _corrupt(fixture, corrupt)
        val refused = _create(fixture, _record("refused"))
        val held = _create(fixture, _record("held", retention = _ttl.copy(retainUntil = Some(Instant.MAX))))
        val active = _create(fixture, _record("active", status = DurableJobLifecycleStatus.Submitted, v2 = true))
        val future = _create(fixture, _record("future", retention = _ttl.copy(expiresAt = Some(_deadline.plusSeconds(1)))))
        val source = _source(Vector(
          _candidate(0, "due"), _candidate(1, "missing"), _candidate(2, "bad"),
          _candidate(3, "refused", _access.copy(subjectId = "foreign")),
          _candidate(4, "held"), _candidate(5, "active"), _candidate(6, "future"),
          _candidate(7, "no-capability", _access.copy(scopes = Set("job.read")))
        ))

        When("the complete valid source batch is admitted with its exact requested count")
        val report = _success(fixture.maintenance.sweep(source, 8, _deadline))
        val afterdue = _load(fixture, "due")
        val afterrefused = _load(fixture, "refused")
        val afterheld = _load(fixture, "held")
        val afteractive = _load(fixture, "active")
        val afterfuture = _load(fixture, "future")

        Then("ordinal-only reports keep closed refusal facets and earlier successes are durable")
        report.entries shouldBe Vector(Expired, Missing, Corrupt, Refused, Held, NotTerminal, NotDue, Refused)
          .zipWithIndex.map { case (facet, index) => DurableJobRetentionReportEntry(index.toLong, facet) }
        source.requestedBounds shouldBe Vector(8)
        _advanced(due, afterdue, _deadline)
        Vector(afterrefused, afterheld, afteractive, afterfuture) shouldBe Vector(refused, held, active, future)
      }

      "E13 admit exactly 1024 candidates and generated ordered bounded missing sequences" in {
        Given("JM69-09B1; an exact maximum source of distinct contiguous candidates and an empty owned provider")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val candidates = Vector.tabulate(1024)(index => _candidate(index.toLong, s"missing-$index"))
        val source = _source(candidates)

        When("the maximum candidate batch is swept")
        val report = _success(fixture.maintenance.sweep(source, 1024, _deadline))

        Then("all 1024 ordinal/facet entries are retained without truncation")
        report.entries shouldBe candidates.map(candidate => DurableJobRetentionReportEntry(candidate.ordinal, Missing))
        source.requestedBounds shouldBe Vector(1024)

        Given("generated positive bounded unique source sequences over fresh test-owned providers")
        val property = Prop.forAll(Gen.choose(1, 24)) { count =>
          val generatedfixture = _fixture()
          val generated = Vector.tabulate(count)(index => _candidate(index.toLong, s"generated-$index"))
          When("the generated complete source is swept with its exact count")
          val result = _success(generatedfixture.maintenance.sweep(_source(generated), count, _deadline)(using generatedfixture.context))
          Then("every candidate retains contiguous order and the missing-only closed facet")
          result.entries shouldBe generated.map(candidate => DurableJobRetentionReportEntry(candidate.ordinal, Missing))
          true
        }

        When("ScalaCheck interprets the bounded source-order property")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property)

        Then("all generated ordered batches satisfy the report contract")
        checked.passed shouldBe true
      }

      "E14 reject every malformed source batch before any candidate effect" in {
        val malformed = Vector(
          Vector(_candidate(0, "due"), _candidate(1, "extra")),
          Vector(_candidate(0, "due"), _candidate(1, "due")),
          Vector(_candidate(0, "due"), _candidate(2, "gap")),
          Vector(_candidate(1, "due")),
          Vector(_candidate(0, "due"), _candidate(1, "  ")),
          Vector(_candidate(0, "due"), _candidate(1, null)),
          Vector(_candidate(0, "due"), null),
          null
        )
        malformed.zipWithIndex.foreach { case (entries, index) =>
          Given(s"JM69-09B1; malformed batch $index which could otherwise expire the first candidate")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val observed = _create(fixture, _record("due"))
          val source = _source(entries)

          When("the entire invalid source response is checked before maintenance")
          val result = fixture.maintenance.sweep(source, if (index == 0) 1 else 2, _deadline)
          val current = _load(fixture, "due")

          Then("a bounded failure is returned and even the valid first candidate has no write")
          result shouldBe a[Consequence.Failure[_]]
          _failure_message(result).length should be < 1024
          current shouldBe observed
        }
      }

      "E15 reject invalid bounds and failing sources before store admission" in {
        Given("JM69-09B1; an unused valid source, failed/throwing sources and an unregistered empty provider")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val source = _source(Vector(_candidate(0, "unused")))
        val failed = new DurableJobRetentionCandidateSource {
          def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] =
            Consequence.stateInvalid("private-provider-failure")
        }
        val thrown = new DurableJobRetentionCandidateSource {
          def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] =
            throw new IllegalStateException("private-provider-failure")
        }

        When("illegal bounds and source failures are requested")
        val bounds = Vector(0, -1, 1025).map(bound => fixture.maintenance.sweep(source, bound, _deadline))
        val failures = Vector(failed, thrown, null).map(value => fixture.maintenance.sweep(value, 1, _deadline))
        val collection = fixture.context.entitySpace.entityOption(DurableJobStoreEntity.Collection)

        Then("sources are untouched for illegal bounds and raw source failures never escape or register storage")
        bounds.foreach(_ shouldBe a[Consequence.Failure[_]])
        failures.foreach(_ shouldBe a[Consequence.Failure[_]])
        source.requestedBounds shouldBe Vector.empty
        failures.map(_failure_message).distinct.size shouldBe 1
        failures.map(_failure_message).foreach { message =>
          message should not include "private-provider-failure"
          message.length should be < 1024
        }
        collection shouldBe None
      }
    }

    "inactive consumer refusal" which {
      "E16 keep canonical maintenance reads while refusing public and startup/runtime recovery exposure" in {
        val refusals = _inactive_retentions.zipWithIndex.map { case (retention, index) =>
          Given(s"JM69-09B1; signed inactive retention state ${retention.deletionState.wire} in owned canonical storage")
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val jobid = s"inactive-$index"
          val observed = _create(fixture, _record(jobid, retention = retention))
          val canonical = _success(DurableJobRecordCodec.canonicalJson(observed.record))
          val source = new DurableJobStartupRecoverySource {
            def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] =
              Consequence.success(Vector(DurableJobStartupRecoveryCandidate(0L, jobid, _access, DurableReplayEvidence())))
          }

          When("authorized canonical reads, ordinary projection and recovery consume the inactive value")
          val admitted = fixture.store.loadStartupRecovery(jobid, _access)
          val decoded = DurableJobRecordCodec.decode(canonical, _access)
          val projection = DurableJobRecordCodec.publicProjection(observed.record, _access)
          val recovery = DurableJobRecovery.classify(observed.record, DurableReplayEvidence())
          val startup = _success(new DurableJobStartupRecoveryCoordinator(source, fixture.store).recover(1))

          Then("maintenance still reads the complete canonical value while all consumer exposure fails closed")
          admitted shouldBe DurableJobStoreStartupRecoveryLoad.Admitted(observed)
          decoded shouldBe Consequence.Success(observed.record)
          projection shouldBe a[Consequence.Failure[_]]
          recovery shouldBe a[Consequence.Failure[_]]
          startup.candidates.map(_.fact) shouldBe Vector(DurableJobStartupRecoveryFact.Corrupt)
          (_failure_message(projection), _failure_message(recovery))
        }

        Then("each consumer uses one fixed bounded refusal for every inactive state")
        refusals.map(_._1).distinct.size shouldBe 1
        refusals.map(_._2).distinct.size shouldBe 1
        refusals.foreach { case (projection, recovery) =>
          projection.length should be < 1024
          recovery.length should be < 1024
        }

        Given("an Active signed terminal TTL which remains active until explicit maintenance")
        val active = _record("active-readable")

        When("projection and recovery classify Active retention even at an already due injected deadline")
        val projection = active.publicProjection(_access)
        val recovery = DurableJobRecovery.classify(active, DurableReplayEvidence())

        Then("ordinary Active behavior remains the admitted terminal result without consulting a clock")
        projection shouldBe a[Consequence.Success[_]]
        _success(recovery).outcome shouldBe DurableJobRecoveryOutcome.TerminalSucceeded
      }
    }
  }

  private val _instant = Instant.parse("2026-10-01T01:02:03Z")
  private val _deadline = _instant.plusSeconds(60)
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read", DurableJobRetentionPolicy.manageScope))
  private val _ttl = DurableRetentionState(None, Some(_deadline), None, DurableDeletionState.Active, None)
  private val _tombstoned = _ttl.copy(
    deletedAt = Some(_deadline), deletionState = DurableDeletionState.Tombstoned,
    tombstone = Some(DurableTombstone(_deadline, "cleanup-complete", None))
  )
  private val _inactive_retentions = Vector(
    _ttl.copy(deletionState = DurableDeletionState.Expired),
    _ttl.copy(deletedAt = Some(_deadline), deletionState = DurableDeletionState.Deleted),
    _tombstoned
  )

  private final case class Fixture(
    store: DurableJobStore,
    maintenance: DurableJobRetentionMaintenance,
    entitystore: EntityStore,
    context: ExecutionContext
  )

  private final class TestSource(entries: Vector[DurableJobRetentionCandidate]) extends DurableJobRetentionCandidateSource {
    private var _requested_bounds: Vector[Int] = Vector.empty
    def requestedBounds: Vector[Int] = _requested_bounds
    def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] = {
      _requested_bounds = _requested_bounds :+ maxCandidates
      Consequence.success(entries)
    }
  }

  private final class RacingEntityStore extends StandardEntityStore {
    private var _before_update: Option[() => Unit] = None
    def arm(action: () => Unit): Unit = _before_update = Some(action)
    override def updateDetached[T](
      changes: T,
      expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy
    )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] = {
      if (tc.id(changes).collection == DurableJobStoreEntity.Collection) {
        val action = _before_update
        _before_update = None
        action.foreach(_())
      }
      super.updateDetached(changes, expectedRevision, executionPolicy)
    }
  }

  private def _fixture(entitystore: EntityStore = EntityStore.standard()): Fixture = {
    val store = new DurableJobStore(entitystore)
    Fixture(store, new DurableJobRetentionMaintenance(store), entitystore, ExecutionContext.test())
  }

  private def _candidate(ordinal: Long, jobid: String, access: DurableJobRecordAccess = _access): DurableJobRetentionCandidate =
    DurableJobRetentionCandidate(ordinal, jobid, access)

  private def _source(entries: Vector[DurableJobRetentionCandidate]): TestSource = new TestSource(entries)

  private def _create(fixture: Fixture, record: DurableJobRecord)(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _success(fixture.store.create(record, _access))

  private def _load(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    _success(fixture.store.load(jobid, _access)).getOrElse(fail("expected retained fixture"))

  private def _raw(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): EntityRevisionCarrier[DurableJobStoreEntity] =
    _success(fixture.entitystore.loadDetached[DurableJobStoreEntity](_success(DurableJobStoreEntity.entityId(jobid)))).get

  private def _corrupt(fixture: Fixture, observed: DurableJobStoreSnapshot)(using ctx: ExecutionContext): Unit = {
    val jobid = observed.record.body.identity.jobId
    val entityid = _success(DurableJobStoreEntity.entityId(jobid))
    _success(fixture.entitystore.updateDetached(
      DurableJobStoreEntity(entityid, jobid, "not-json"), Some(observed.providerrevision),
      EntityMutationExecutionPolicy(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
        preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired,
        observedRevision = Some(observed.providerrevision)
      )
    ))
    ()
  }

  private def _advanced(previous: DurableJobStoreSnapshot, current: DurableJobStoreSnapshot, now: Instant): Unit = {
    current.providerrevision.value shouldBe previous.providerrevision.value + 1L
    current.record.format shouldBe previous.record.format
    current.record.body.identity shouldBe previous.record.body.identity.copy(revision = previous.record.body.identity.revision + 1L, updatedAt = now)
    current.record.body.copy(identity = previous.record.body.identity, retention = previous.record.body.retention) shouldBe previous.record.body
    current.record.integrity should not be previous.record.integrity
  }

  private def _record(
    jobid: String,
    status: DurableJobLifecycleStatus = DurableJobLifecycleStatus.Succeeded,
    retention: DurableRetentionState = _ttl,
    revision: Long = 1L,
    v2: Boolean = false
  ): DurableJobRecord = {
    val reference = DurableExternalReference("opaque-reference", "blob", Some("application/octet-stream"), 12L, _digest)
    val result = status match {
      case DurableJobLifecycleStatus.Succeeded => DurableResultOutcome.Succeeded(DurableValue.External(reference))
      case DurableJobLifecycleStatus.Failed => DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "failed", false))
      case DurableJobLifecycleStatus.Cancelled => DurableResultOutcome.Cancelled(None)
      case _ => DurableResultOutcome.Pending
    }
    val body = DurableJobRecordBody(
      DurableJobIdentity(jobid, revision, _instant, _instant.plusSeconds(20)),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(status, 7, DurableRunMode.Async,
        DurableRetryEvidence(Vector.empty, 3, None, false, false),
        DurableScheduleState(Some(_instant), None, None)),
      Vector(DurableTaskDescriptor("task-root", None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component", Some("service"), "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector(DurableInputReference("source", DurableValue.External(reference))), result,
      Vector(DurableTimelineEvent(1L, _instant, "submitted", Some("task-root"), Some("accepted"))),
      Vector(DurableDiagnosticSummary("execution", "info", "info", "retained")), Some(reference),
      DurableDefinitionSnapshot("definition", "daily-job", 1, 1L, _digest, Some(reference), Some("jcl"), Map("profile" -> "test")),
      retention
    )
    _success(if (v2) DurableJobRecord.createV2(body) else DurableJobRecord.create(body))
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _failure_message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case Consequence.Success(_) => fail("expected bounded refusal")
  }
}
