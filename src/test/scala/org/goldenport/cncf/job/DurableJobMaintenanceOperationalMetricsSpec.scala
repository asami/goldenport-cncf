package org.goldenport.cncf.job

import java.nio.file.Files
import java.time.{Duration, Instant}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.component.builtin.metrics.MetricsComponent
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.metrics.{ComponentMetricDefinition, RuntimeMetricPoint, RuntimeMetricsCatalog, RuntimeMetricsSnapshot}
import org.goldenport.cncf.observability.{OpenTelemetryExportConfig, OpenTelemetryExporterSupport}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class DurableJobMaintenanceOperationalMetricsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DurableJobOperationalMetricsFixtures.*
  import BlobRepository.given

  "E9 DM1 actual retention observations" should _scenario("E9", "R1,R3,R7") {
    "preserve V1 and V2 hold, expiry, deletion and unchanged native facets" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R7; E9/DM1: real SQLite terminal due V1 and V2 records and the actual retention policy/adapter")
      Vector(false, true).foreach { v2 =>
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = success(fixture.store.create(record("PRIVATE-retention", v2, due), access))
          When("actual Hold, Held, ReleaseHold, NotDue, Expire, Delete, AlreadyDeleted, authorization refusal and stale observation run in original order")
          val hold = fixture.retention.maintainObserved(original, access, DurableJobRetentionCommand.Hold, now)
          val held = _load(fixture, "PRIVATE-retention")
          val holdrow = fixture.rawJob("PRIVATE-retention").get
          val blocked = fixture.retention.maintainObserved(held, access, DurableJobRetentionCommand.Expire, now)
          val release = fixture.retention.maintainObserved(held, access, DurableJobRetentionCommand.ReleaseHold(None), now)
          val released = _load(fixture, "PRIVATE-retention")
          val notdue = fixture.retention.maintainObserved(released, access, DurableJobRetentionCommand.Expire, instant.plusSeconds(10L))
          val expire = fixture.retention.maintainObserved(released, access, DurableJobRetentionCommand.Expire, now)
          val expired = _load(fixture, "PRIVATE-retention")
          val delete = fixture.retention.maintainObserved(expired, access, DurableJobRetentionCommand.Delete, now)
          val deleted = _load(fixture, "PRIVATE-retention")
          val already = fixture.retention.maintainObserved(deleted, access, DurableJobRetentionCommand.Delete, now)
          val refused = fixture.retention.maintainObserved(null, null, null, null)
          val stale = fixture.retention.maintainObserved(original, access, DurableJobRetentionCommand.Expire, now)
          val row = fixture.rawJob("PRIVATE-retention").get
          val quota = fixture.rawQuota()
          val baseline = fixture.observations
          Then("four native checkpoints retain exact signed format and canonical retention while every direct facet and actual nested boundary counts once")
          Vector(hold, blocked, release, notdue, expire, delete, already, refused, stale) shouldBe Vector(
            DurableJobRetentionFacet.HoldApplied, DurableJobRetentionFacet.Held, DurableJobRetentionFacet.HoldReleased,
            DurableJobRetentionFacet.NotDue, DurableJobRetentionFacet.Expired, DurableJobRetentionFacet.Deleted,
            DurableJobRetentionFacet.AlreadyDeleted, DurableJobRetentionFacet.Refused, DurableJobRetentionFacet.Stale)
          held.record.body.retention shouldBe due.copy(retainUntil = Some(Instant.MAX))
          holdrow.getString("canonicalJson") shouldBe Some(canonical(held.record))
          released.record.body.retention shouldBe due
          expired.record.body.retention shouldBe due.copy(deletionState = DurableDeletionState.Expired)
          deleted.record.body.retention shouldBe due.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(now))
          Vector(held, released, expired, deleted).zipWithIndex.foreach { case (snapshot, index) =>
            snapshot.record.format shouldBe original.record.format
            snapshot.record.body.identity.revision shouldBe index.toLong + 2L
            snapshot.providerrevision.value shouldBe index.toLong + 2L
            snapshot.record.body.identity.updatedAt shouldBe now
          }
          row.getString("canonicalJson") shouldBe Some(canonical(deleted.record))
          row.getLong("cncf_revision") shouldBe Some(5L)
          fixture.entity.calls.count(_ == "updateDetached:durableJob") shouldBe 4
          val expected = Vector(point("create", "succeeded", 1L), point("load", "found", 8L),
            point("startup", "admitted", 8L), point("checkpoint", "succeeded", 4L),
            point("retention", "not_due", 1L), point("retention", "held", 1L),
            point("retention", "already_deleted", 1L), point("retention", "expired", 1L),
            point("retention", "deleted", 1L), point("retention", "hold_applied", 1L),
            point("retention", "hold_released", 1L), point("retention", "refused", 1L, true),
            point("retention", "stale", 1L, true))
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
          fixture.observations shouldBe baseline
          fixture.rawJob("PRIVATE-retention") shouldBe Some(row)
          fixture.rawQuota() shouldBe quota
        }
      }
    }
  }

  "E10 DM2 real bounded retention sweep observations" should _scenario("E10", "R1,R3,R7") {
    "count each original candidate once and only failed sweep cells for failed discovery or batches" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R7; E10/DM2: a native terminal due row and a measured real source with admitted, absent and capability-denied ordinals")
      DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
        given ExecutionContext = fixture.context
        val original = success(fixture.store.create(record("PRIVATE-sweep", true, due), access))
        val source = new Source(Vector(DurableJobRetentionCandidate(0L, "PRIVATE-sweep", access),
          DurableJobRetentionCandidate(1L, "PRIVATE-absent", access),
          DurableJobRetentionCandidate(2L, "PRIVATE-denied", access.copy(scopes = Set("job.read")))))
        val failure = new Source(Vector.empty, fail = true)
        val malformed = new Source(Vector(DurableJobRetentionCandidate(1L, "PRIVATE-sweep", access)))
        val empty = new Source(Vector.empty)
        When("a valid sweep returns its original three facets, then invalid bound, source failure, malformed batch and valid empty discovery run")
        val returned = success(fixture.retention.sweep(source, 3, now))
        val aftervalid = fixture.store.operationalMetrics
        val rows = fixture.rawJob("PRIVATE-sweep")
        val quota = fixture.rawQuota()
        val jobcalls = fixture.entity.calls.filter(_.endsWith(":durableJob"))
        val invalid = fixture.retention.sweep(source, 0, now)
        val failed = fixture.retention.sweep(failure, 3, now)
        val badbatch = fixture.retention.sweep(malformed, 3, now)
        val emptyresult = fixture.retention.sweep(empty, 3, now)
        val baseline = fixture.observations
        Then("the admitted candidate is not double observed, discovery failures produce no facets or native effects, and empty discovery produces only a returned sweep")
        returned.entries shouldBe Vector(DurableJobRetentionReportEntry(0L, DurableJobRetentionFacet.Expired),
          DurableJobRetentionReportEntry(1L, DurableJobRetentionFacet.Missing),
          DurableJobRetentionReportEntry(2L, DurableJobRetentionFacet.Refused))
        Vector(invalid, failed, badbatch).foreach(_ shouldBe a[Consequence.Failure[_]])
        emptyresult shouldBe Consequence.Success(DurableJobRetentionReport(Vector.empty))
        source.calls shouldBe 1
        failure.calls shouldBe 1
        malformed.calls shouldBe 1
        empty.calls shouldBe 1
        fixture.entity.calls.filter(_.endsWith(":durableJob")) shouldBe jobcalls
        jobcalls.count(_ == "loadDetached:durableJob") shouldBe 5
        jobcalls.count(_ == "updateDetached:durableJob") shouldBe 1
        original.providerrevision.value shouldBe 1L
        aftervalid shouldBe Vector(point("create", "succeeded", 1L), point("load", "found", 1L),
          point("startup", "admitted", 2L), point("startup", "missing", 1L),
          point("checkpoint", "succeeded", 1L), point("retention_sweep", "returned", 1L),
          point("retention", "expired", 1L), point("retention", "missing", 1L, true), point("retention", "refused", 1L, true))
        val expected = aftervalid.updated(5, point("retention_sweep", "returned", 2L))
          .patch(6, Vector(point("retention_sweep", "failed", 3L, true)), 0)
        (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
        fixture.observations shouldBe baseline
        source.calls shouldBe 1
        fixture.rawJob("PRIVATE-sweep") shouldBe rows
        fixture.rawQuota() shouldBe quota
        rows.get.getLong("cncf_revision") shouldBe Some(2L)
        success(DurableJobRecordCodec.decode(rows.get.getString("canonicalJson").get, access)).body.retention.deletionState shouldBe DurableDeletionState.Expired
      }
    }
  }

  "E11 DM3 real payload admission and rollback observations" should _scenario("E11", "R1,R3,R4,R8") {
    "count original payload results without treating the private rollback provider delete as a public delete" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R4,R8; E11/DM3: LocalBlobStore, real SQLite metadata and a one-blob native quota")
      DurableJobOperationalMetricsFixtures.withFixture(payloadPolicy = DurableJobOwnedBlobResourcePolicy(1, 3L, 3)) { fixture =>
        given ExecutionContext = fixture.context
        val original = success(fixture.store.create(record("PRIVATE-payload"), access))
        When("one native payload fills quota, a second is refused before physical put, and the exact owned target is prepared and physically deleted")
        val reference = success(fixture.put(original, Array[Byte](1, 2, 3)))
        val metadata = fixture.metadata(reference)
        val physical = fixture.physical(metadata)
        val filled = fixture.rawQuota(blobQuota = true)
        val refused = fixture.put(original, Array[Byte](4))
        val unchanged = fixture.rawQuota(blobQuota = true)
        val attached = fixture.attach(original, reference, active)
        val targets = success(fixture.payloads.prepare(attached.record))
        val disposed = fixture.payloads.delete(targets.head)
        val usage = success(fixture.payloads.resourceSnapshot())
        val quota = fixture.rawQuota(blobQuota = true)
        val baseline = fixture.observations
        Then("the original reference, byte content and metadata remain exact; refusal performs no put and acknowledged disposal alone releases native occupancy")
        physical shouldBe Vector[Byte](1, 2, 3)
        reference.storageKind shouldBe DurableJobOwnedBlobPayloads.storageKind
        reference.reference shouldBe metadata.id.value
        reference.byteSize shouldBe 3L
        reference.sha256 shouldBe metadata.digest.get
        refused shouldBe a[Consequence.Failure[_]]
        DurableJobOperationalMetricsFixtures.message(refused) should include("Durable Job-owned payload refused")
        DurableJobOperationalMetricsFixtures.message(refused) should not include "PRIVATE"
        unchanged shouldBe filled
        targets.map(_.id) shouldBe Vector(metadata.id)
        targets.map(_.storageRef) shouldBe Vector(metadata.storageRef.get)
        disposed shouldBe Consequence.Success(())
        fixture.blob.calls shouldBe Vector("put", "delete")
        Files.exists(fixture.path(metadata)) shouldBe false
        usage.occupiedblobs shouldBe 0L
        usage.pendingblobs shouldBe 0L
        usage.releasedblobs shouldBe 1L
        val expected = Vector(point("create", "succeeded", 1L), point("load", "found", 1L),
          point("startup", "admitted", 3L), point("checkpoint", "succeeded", 1L),
          point("payload_snapshot", "succeeded", 1L), point("payload_put", "succeeded", 1L),
          point("payload_put", "failed", 1L, true), point("payload_prepare", "succeeded", 1L),
          point("payload_delete", "succeeded", 1L))
        (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
        fixture.observations shouldBe baseline
        fixture.metadata(reference) shouldBe metadata
        fixture.rawQuota(blobQuota = true) shouldBe quota
      }
      Given("E11/R1,R3,R8: a separate native adapter loses metadata acknowledgement after the actual physical put and native metadata create")
      DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
        given ExecutionContext = fixture.context
        val original = success(fixture.store.create(record("PRIVATE-lost-blob", true), access))
        fixture.entity.losebloback = true
        When("the original publication failure invokes its private physical rollback and acknowledged native quota release")
        val result = fixture.put(original, Array[Byte](7, 8, 9))
        val issued = fixture.blob.issued.head
        val metadata = success(fixture.entity.load[Blob](issued.id)).get
        val usage = success(fixture.payloads.resourceSnapshot())
        val quota = fixture.rawQuota(blobQuota = true)
        val baseline = fixture.observations
        Then("payload_put alone records Failure, rollback never invents payload_delete, metadata survives and only acknowledged disposal frees occupancy")
        result shouldBe a[Consequence.Failure[_]]
        DurableJobOperationalMetricsFixtures.message(result) should include("Durable Job-owned payload refused")
        DurableJobOperationalMetricsFixtures.message(result) should not include "PRIVATE"
        fixture.blob.calls shouldBe Vector("put", "delete")
        metadata.id shouldBe issued.id
        metadata.storageRef shouldBe Some(issued.storageRef)
        Files.exists(fixture.path(metadata)) shouldBe false
        usage.occupiedblobs shouldBe 0L
        usage.releasedblobs shouldBe 1L
        val expected = Vector(point("create", "succeeded", 1L), point("startup", "admitted", 1L),
          point("payload_snapshot", "succeeded", 1L), point("payload_put", "failed", 1L, true))
        (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
        fixture.observations shouldBe baseline
        fixture.rawQuota(blobQuota = true) shouldBe quota
      }
      Given("E11/R1: an unavailable/null Store and null physical providers with a valid zero-owned reference record")
      val unavailable = new DurableJobOwnedBlobPayloads(null, null, null)
      val zero = record("PRIVATE-zero-owned")
      given ExecutionContext = ExecutionContext.test()
      When("the original zero-owned prepare returns before accessing any Store or provider")
      val empty = unavailable.prepare(zero)
      Then("the original successful empty vector survives without a newly introduced recorder dereference")
      empty shouldBe Consequence.Success(Vector.empty)
    }
  }

  "E12 DM4 native physical cleanup observations" should _scenario("E12", "R1,R3,R8") {
    "retain signed tombstones, early facets and ambiguous disposal/checkpoint failure authority" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R8; E12/DM4: real Deleted terminal V1 and V2 jobs with exclusively owned native payload metadata")
      Vector(false, true).foreach { v2 =>
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = success(fixture.store.create(record("PRIVATE-cleanup", v2), access))
          val reference = success(fixture.put(original, Array[Byte](1, 2, 3)))
          val metadata = fixture.metadata(reference)
          val deleted = fixture.attach(original, reference, active.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(now)))
          When("actual cleanup acknowledges owned-byte disposal and the signed checkpoint, then duplicate, stale and authorization-refused calls return")
          val tombstoned = fixture.cleanup.cleanupObserved(deleted, access, now)
          val current = _load(fixture, "PRIVATE-cleanup")
          val duplicate = fixture.cleanup.cleanupObserved(current, access, now)
          val stale = fixture.cleanup.cleanupObserved(deleted, access, now)
          val refused = fixture.cleanup.cleanupObserved(null, null, now)
          val usage = success(fixture.payloads.resourceSnapshot())
          val row = fixture.rawJob("PRIVATE-cleanup").get
          val quota = fixture.rawQuota(blobQuota = true)
          val baseline = fixture.observations
          Then("one exact native tombstone certifies acknowledged disposal, metadata remains, unchanged facets count once and repeated metrics add no effects")
          tombstoned shouldBe DurableJobCleanupFacet.Tombstoned
          duplicate shouldBe DurableJobCleanupFacet.AlreadyTombstoned
          stale shouldBe DurableJobCleanupFacet.Stale
          refused shouldBe DurableJobCleanupFacet.Refused
          current.record.format shouldBe deleted.record.format
          current.providerrevision.value shouldBe deleted.providerrevision.value + 1L
          current.record.body.identity shouldBe deleted.record.body.identity.copy(revision = 3L, updatedAt = now)
          current.record.body.retention shouldBe deleted.record.body.retention.copy(deletionState = DurableDeletionState.Tombstoned,
            tombstone = Some(DurableTombstone(now, "job-owned-payload-cleanup-complete", None)))
          row.getString("canonicalJson") shouldBe Some(canonical(current.record))
          Files.exists(fixture.path(metadata)) shouldBe false
          usage.occupiedblobs shouldBe 0L
          fixture.blob.calls shouldBe Vector("put", "delete")
          val expected = Vector(point("create", "succeeded", 1L), point("load", "found", 3L),
            point("startup", "admitted", 6L), point("checkpoint", "succeeded", 2L),
            point("payload_snapshot", "succeeded", 1L), point("payload_put", "succeeded", 1L),
            point("payload_prepare", "succeeded", 1L), point("payload_delete", "succeeded", 1L),
            point("cleanup", "tombstoned", 1L), point("cleanup", "already_tombstoned", 1L),
            point("cleanup", "refused", 1L, true), point("cleanup", "stale", 1L, true))
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
          fixture.observations shouldBe baseline
          fixture.metadata(reference) shouldBe metadata
          fixture.rawQuota(blobQuota = true) shouldBe quota
        }
      }
      Given("E12/R8: an actual held Deleted record in a separate real SQLite fixture")
      DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
        given ExecutionContext = fixture.context
        val original = success(fixture.store.create(record("PRIVATE-held-cleanup", retention = active.copy(
          retainUntil = Some(Instant.MAX), deletedAt = Some(instant), deletionState = DurableDeletionState.Deleted)), access))
        val row = fixture.rawJob("PRIVATE-held-cleanup")
        When("cleanup observes the legal hold before preparing or deleting payloads")
        val facet = fixture.cleanup.cleanupObserved(original, access, now)
        Then("Held retains the original native row, ownership quota and physical bytes untouched")
        facet shouldBe DurableJobCleanupFacet.Held
        fixture.rawJob("PRIVATE-held-cleanup") shouldBe row
        fixture.rawQuota(blobQuota = true) shouldBe None
        fixture.blob.calls shouldBe Vector.empty
        fixture.store.operationalMetrics shouldBe Vector(point("create", "succeeded", 1L),
          point("startup", "admitted", 1L), point("cleanup", "held", 1L))
      }
      Vector(false, true).foreach { checkpointfailure =>
        Given("E12/R1,R3,R8: separate actual provider failure before delete, or native checkpoint refusal after acknowledged physical disposal")
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = success(fixture.store.create(record("PRIVATE-failed-cleanup", true), access))
          val reference = success(fixture.put(original, Array[Byte](3, 2, 1)))
          val metadata = fixture.metadata(reference)
          val deleted = fixture.attach(original, reference, active.copy(deletionState = DurableDeletionState.Deleted, deletedAt = Some(now)))
          val row = fixture.rawJob("PRIVATE-failed-cleanup")
          val quotabefore = fixture.rawQuota(blobQuota = true)
          fixture.blob.refusedelete = !checkpointfailure
          fixture.entity.refuseupdate = checkpointfailure
          When("cleanup reaches the actual failing provider boundary without any added retry or compensation")
          val facet = fixture.cleanup.cleanupObserved(deleted, access, now)
          val usage = success(fixture.payloads.resourceSnapshot())
          val quota = fixture.rawQuota(blobQuota = true)
          val baseline = fixture.observations
          Then("the original refusal facet cannot certify a tombstone; only an acknowledged physical delete may release quota")
          facet shouldBe (if (checkpointfailure) DurableJobCleanupFacet.CheckpointRefused else DurableJobCleanupFacet.PayloadDeleteRefused)
          fixture.rawJob("PRIVATE-failed-cleanup") shouldBe row
          Files.exists(fixture.path(metadata)) shouldBe !checkpointfailure
          usage.occupiedblobs shouldBe (if (checkpointfailure) 0L else 1L)
          if (!checkpointfailure) {
            quota.map(_.removeKeys(Set("updated_at", "cncf_revision"))) shouldBe quotabefore.map(_.removeKeys(Set("updated_at", "cncf_revision")))
            quota.flatMap(_.getLong("cncf_revision")) shouldBe quotabefore.flatMap(_.getLong("cncf_revision")).map(_ + 2L)
          }
          fixture.blob.calls shouldBe Vector("put", "delete")
          val expected = Vector(point("create", "succeeded", 1L), point("load", "found", if (checkpointfailure) 2L else 1L),
            point("startup", "admitted", if (checkpointfailure) 4L else 3L), point("checkpoint", "succeeded", 1L)) ++
            Option.when(checkpointfailure)(point("checkpoint", "failed", 1L, true)).toVector ++
            Vector(point("payload_snapshot", "succeeded", 1L), point("payload_put", "succeeded", 1L),
              point("payload_prepare", "succeeded", 1L),
              point("payload_delete", if (checkpointfailure) "succeeded" else "failed", 1L, !checkpointfailure),
              point("cleanup", if (checkpointfailure) "checkpoint_refused" else "payload_delete_refused", 1L, true))
          val afterevidence = fixture.observations
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
          fixture.observations shouldBe afterevidence
          fixture.metadata(reference) shouldBe metadata
          fixture.rawQuota(blobQuota = true) shouldBe quota
          baseline._3 shouldBe afterevidence._3
          baseline._4 shouldBe afterevidence._4
        }
      }
    }
  }

  "E13 DM5 coordinated native maintenance and local scheduler observations" should _scenario("E13", "R1,R3,R4,R8") {
    "retain the actual expiry, deletion, disposal report and scheduler close guard" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R4,R8; E13/DM5: actual SQLite/retention/cleanup, exclusively owned bytes and the real ManualJobTimeSource/ManualJobTimer")
      Vector(false, true).foreach { v2 =>
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = success(fixture.store.create(record("PRIVATE-maintenance", v2, due), access))
          val reference = success(fixture.put(original, Array[Byte](1, 1, 2)))
          val metadata = fixture.metadata(reference)
          val attached = fixture.attach(original, reference)
          val source = new Source(Vector(DurableJobRetentionCandidate(0L, "PRIVATE-maintenance", access)))
          val failedsource = new Source(Vector.empty, fail = true)
          When("one coordinated sweep completes original expiry, deletion and disposal, then invalid bounds and source failure are refused")
          val report = success(fixture.maintenance.sweep(source, 1, now))
          val invalid = fixture.maintenance.sweep(source, 0, now)
          val failed = fixture.maintenance.sweep(failedsource, 1, now)
          val row = fixture.rawJob("PRIVATE-maintenance").get
          val expected = Vector(point("create", "succeeded", 1L), point("load", "found", 4L),
            point("startup", "admitted", 9L), point("checkpoint", "succeeded", 4L),
            point("payload_put", "succeeded", 1L), point("payload_prepare", "succeeded", 1L),
            point("payload_delete", "succeeded", 1L), point("retention", "expired", 1L),
            point("retention", "deleted", 1L), point("cleanup", "tombstoned", 1L),
            point("maintenance_sweep", "returned", 1L), point("maintenance_sweep", "failed", 2L, true))
          Then("the original ordered report and exact nested counts certify only actual local results; failed sweeps have no candidates or physical effects")
          report shouldBe DurableJobMaintenanceReport(Vector(DurableJobMaintenanceEntry(0L,
            Vector(DurableJobRetentionFacet.Expired, DurableJobRetentionFacet.Deleted), Some(DurableJobCleanupFacet.Tombstoned))))
          Vector(invalid, failed).foreach(_ shouldBe a[Consequence.Failure[_]])
          source.calls shouldBe 1
          failedsource.calls shouldBe 1
          attached.providerrevision.value shouldBe 2L
          row.getLong("cncf_revision") shouldBe Some(5L)
          success(DurableJobRecordCodec.decode(row.getString("canonicalJson").get, access)).body.retention.deletionState shouldBe DurableDeletionState.Tombstoned
          fixture.blob.calls shouldBe Vector("put", "delete")
          Files.exists(fixture.path(metadata)) shouldBe false
          fixture.store.operationalMetrics shouldBe expected
          Given("E13/R1,R4: the existing scheduler has a real ManualJobTimer delegate with a retained late callback for the original close guard")
          val time = new ManualJobTimeSource(now)
          val manual = new InMemoryJobEngine.ManualJobTimer(time)
          val timer = new RetainedManualTimer(manual)
          val scheduler = new DurableJobMaintenanceScheduler(fixture.maintenance, source, 1, Duration.ofSeconds(1L), time, timer)
          try {
            When("the scheduler starts, executes one due native sweep, rearms, closes and receives a retained late callback")
            val started = scheduler.start()
            val before = scheduler.status
            val fired = manual.advanceBy(Duration.ofSeconds(1L))
            val after = scheduler.status
            scheduler.close()
            val closed = scheduler.status
            val points = fixture.store.operationalMetrics
            val baseline = fixture.observations
            timer.replay(1)
            val late = manual.advanceBy(Duration.ofSeconds(1L))
            Then("one due callback records its actual AlreadyTombstoned report, closed rearm/callbacks add no call and physical effects remain exact")
            started shouldBe Consequence.Success(())
            before.started shouldBe true
            before.nextDueAt shouldBe Some(now.plusSeconds(1L))
            fired shouldBe 1
            after.inFlight shouldBe false
            after.nextDueAt shouldBe Some(now.plusSeconds(2L))
            after.lastResult shouldBe Some(Consequence.Success(DurableJobMaintenanceReport(Vector(
              DurableJobMaintenanceEntry(0L, Vector.empty, Some(DurableJobCleanupFacet.AlreadyTombstoned))))))
            timer.registrations shouldBe 2
            source.calls shouldBe 2
            closed.closed shouldBe true
            closed.started shouldBe false
            closed.nextDueAt shouldBe None
            late shouldBe 0
            manual.pendingCount shouldBe 0
            val scheduledexpected = expected.updated(2, point("startup", "admitted", 12L))
              .updated(10, point("maintenance_sweep", "returned", 2L))
              .patch(10, Vector(point("cleanup", "already_tombstoned", 1L)), 0)
            points shouldBe scheduledexpected
            (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe scheduledexpected)
            fixture.observations shouldBe baseline
            fixture.rawJob("PRIVATE-maintenance") shouldBe Some(row)
          } finally {
            scheduler.close()
            timer.shutdown()
          }
        }
      }
    }
  }

  "E14 DM6 actual native metrics supplier and OTEL consumer" should _scenario("E14", "R2,R4,R6") {
    "export positive closed Store cells through the existing Record service and cumulative monotonic sums" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R2,R4,R6; E14/DM6: actual mixed SQLite results, real runtime component registry and the existing optional fifth metrics supplier")
      DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
        given ExecutionContext = fixture.context
        fixture.seed("PRIVATE-corrupt-metrics", "PRIVATE-not-json")
        val original = success(fixture.store.create(record("PRIVATE-native-metrics", true), access))
        fixture.store.load("PRIVATE-native-metrics", access) shouldBe Consequence.Success(Some(original))
        fixture.store.load("PRIVATE-missing-metrics", access) shouldBe Consequence.Success(None)
        fixture.store.load("PRIVATE-native-metrics", access.copy(subjectId = "PRIVATE-foreign")) shouldBe a[Consequence.Failure[_]]
        fixture.store.loadStartupRecovery("PRIVATE-corrupt-metrics", access) shouldBe DurableJobStoreStartupRecoveryLoad.Corrupt
        fixture.store.loadStartupRecovery("PRIVATE-native-metrics", access.copy(subjectId = "PRIVATE-foreign")) shouldBe DurableJobStoreStartupRecoveryLoad.Refused
        fixture.store.loadStartupRecovery("PRIVATE-missing-metrics", access) shouldBe DurableJobStoreStartupRecoveryLoad.Missing
        val points = Vector(point("create", "succeeded", 1L), point("load", "found", 1L), point("load", "missing", 1L),
          point("load", "failed", 1L, true), point("startup", "missing", 1L), point("startup", "corrupt", 1L, true),
          point("startup", "refused", 1L, true))
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        try {
          subsystem.componentMetrics.record(ComponentMetricDefinition("durable.fixture", Vector("component")), Map("component" -> "fixture")) shouldBe true
          val componentbefore = subsystem.componentMetrics.snapshot()
          val order = ArrayBuffer.empty[String]
          val config = RuntimeConfig.default.copy(openTelemetryExportConfig = OpenTelemetryExportConfig())
          val policy = () => { order += "security"; subsystem.runtimeOperationSecurityPolicyC }
          val service = new MetricsComponent.DefaultMetricsService(subsystem.entityAccessMetrics, subsystem.componentMetrics,
            config, policy, () => { order += "jobs"; fixture.store.operationalMetrics })
          val row = fixture.rawJob("PRIVATE-native-metrics")
          val quota = fixture.rawQuota()
          val baseline = fixture.observations
          When("the native service consumes the actual Store snapshot before security and the actual OTEL exporter maps it to cumulative metrics")
          val record = success(service.loadRuntimeMetrics())
          val consumptionorder = order.toVector
          val catalog = success(service.loadMetricsCatalog())
          val snapshot = RuntimeMetricsSnapshot(now, fixture.store.operationalMetrics, RuntimeMetricsCatalog.scopes)
          val metrics = _otel_metrics(OpenTelemetryExporterSupport.metricsPayload(snapshot))
          val expectedotel = points.flatMap(point => Vector(point -> false) ++ Option.when(point.errorCount > 0L)(point -> true).toVector)
          Then("original registry points are retained, catalog has all 24 scopes and every count/error sum has exact two attributes with no durations or readiness claim")
          fixture.store.operationalMetrics shouldBe points
          _records(record, "metrics").filter(_.getString("scope").contains("job.durable.operation")) shouldBe points.map(_.toRecord)
          _records(snapshot.toRecord, "metrics") shouldBe points.map(_.toRecord)
          consumptionorder shouldBe Vector("jobs", "security")
          subsystem.componentMetrics.snapshot() shouldBe componentbefore
          _records(record, "metrics").filter(_.getString("scope").contains("component")) should contain allElementsOf
            componentbefore.map(_.toRuntimeMetricPoint.toRecord)
          _records(catalog, "scopes").size shouldBe 24
          _records(catalog, "scopes").flatMap(_.getString("scope")) shouldBe _original_scopes ++
            Vector("job.lifecycle", "job.operation", "job.durable.operation")
          RuntimeMetricsCatalog.scopes.last.labelKeys shouldBe Vector("operation", "outcome")
          record.getRecord("otel_export").flatMap(_.getString("status")) shouldBe Some("disabled")
          record.asMap.keySet should not contain "readiness"
          metrics.size shouldBe expectedotel.size
          metrics.zip(expectedotel).foreach { case (metric, (point, error)) =>
            val sum = metric.hcursor.downField("sum")
            metric.hcursor.get[String]("name").toOption shouldBe
              Some("cncf.job.durable.operation.calls." + (if (error) "error_count" else "count"))
            sum.get[Int]("aggregationTemporality").toOption shouldBe Some(2)
            sum.get[Boolean]("isMonotonic").toOption shouldBe Some(true)
            sum.downField("dataPoints").downArray.get[String]("asInt").toOption shouldBe
              Some((if (error) point.errorCount else point.count).toString)
            metric.hcursor.downField("gauge").focus shouldBe None
            val attributes = sum.downField("dataPoints").downArray.downField("attributes").as[Vector[io.circe.Json]].toOption.get
            attributes.size shouldBe 2
            attributes.map { attribute => attribute.hcursor.get[String]("key").toOption.get ->
              attribute.hcursor.downField("value").get[String]("stringValue").toOption.get }.toMap shouldBe
              point.labels.map { case (key, value) => s"cncf.$key" -> value }
          }
          points.foreach { point =>
            point.toRecord.asMap.keySet should not contain "duration_count"
            point.durationCount shouldBe 0L
            point.durationMinMillis shouldBe None
            point.durationMaxMillis shouldBe None
            (Vector(point.scope, point.name) ++ point.labels.keys ++ point.labels.values).foreach(_ should not include "PRIVATE")
          }
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe points)
          fixture.observations shouldBe baseline
          Given("E14/R6: the same native supplier with an unsupported OTEL format and a separate exact throwing supplier")
          val unsupported = new MetricsComponent.DefaultMetricsService(subsystem.entityAccessMetrics, subsystem.componentMetrics,
            config.copy(openTelemetryExportConfig = OpenTelemetryExportConfig(enabled = true, protocol = "unsupported")), policy,
            () => fixture.store.operationalMetrics)
          val sentinel = new IllegalStateException("PRIVATE-supplier-unavailable")
          val throwing = new MetricsComponent.DefaultMetricsService(subsystem.entityAccessMetrics, subsystem.componentMetrics,
            config, policy, () => throw sentinel)
          When("the existing service refuses unsupported export configuration and consumes the throwing supplier")
          val unsupportedrecord = success(unsupported.loadRuntimeMetrics())
          val thrown = intercept[IllegalStateException](throwing.loadRuntimeMetrics())
          Then("unsupported export retains all native points with original unavailable status and the exact exception propagates without an empty fallback")
          unsupportedrecord.getRecord("otel_export").flatMap(_.getString("status")) shouldBe Some("unavailable")
          _records(unsupportedrecord, "metrics").filter(_.getString("scope").contains("job.durable.operation")) shouldBe points.map(_.toRecord)
          (thrown eq sentinel) shouldBe true
          fixture.observations shouldBe baseline
          fixture.rawJob("PRIVATE-native-metrics") shouldBe row
          fixture.rawQuota() shouldBe quota
        } finally Subsystem.shutdownOwned(subsystem)
      }
    }
  }

  private val _original_scopes = Vector(
    "web.request", "action.execution", "authorization.decision", "dsl.chokepoint", "validation",
    "operation-request-validation", "blob.operation", "rule.execution", "spi.invocation",
    "operation-evaluation.delivery", "mcp-client.invocation", "process.execution", "resource-tree.snapshot",
    "resource-tree.query", "service-container.lifecycle", "component-initialization.parameter-resolution",
    "entity.conditional-transition", "diagnostic-payload.externalization", "otel.export", "entity-access", "component"
  )

  private final class RetainedManualTimer(delegate: InMemoryJobEngine.ManualJobTimer) extends JobTimer {
    private var _callbacks = Vector.empty[() => Unit]
    def registrations: Int = _callbacks.size
    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = {
      val action = () => body
      _callbacks :+= action
      delegate.schedule(dueat)(action())
    }
    def replay(index: Int): Unit = _callbacks(index)()
    override def shutdown(): Unit = delegate.shutdown()
  }

  private def _load(fixture: Fixture, jobid: String)(using ctx: ExecutionContext): DurableJobStoreSnapshot =
    success(fixture.store.load(jobid, access)).get

  private def _records(record: Record, key: String): Vector[Record] =
    record.getVector(key).getOrElse(Vector.empty).collect { case value: Record => value }

  private def _otel_metrics(payload: io.circe.Json): Vector[io.circe.Json] =
    payload.hcursor.downField("resourceMetrics").downArray.downField("scopeMetrics").downArray
      .downField("metrics").as[Vector[io.circe.Json]].toOption.get

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-operational-metrics, example:$example, rules:$rules, phase:69.7")
}
