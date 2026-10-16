package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobStoreOperationalMetricsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DurableJobOperationalMetricsFixtures.*

  "E4 DS1 native V1 and V2 storage observations" should _scenario("E4", "R1,R2,R3,R4") {
    "retain canonical rows, native revisions and the real checkpoint load nesting" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R2,R3,R4; E4/DS1: real SQLite and StandardEntityStore with valid signed committed terminal V1 and V2 records")
      Vector(false, true).foreach { v2 =>
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = record("PRIVATE-native-job", v2)
          When("native create, found and missing load, startup, current checkpoint and stale checkpoint return their original results")
          val created = success(fixture.store.create(original, access))
          val found = fixture.store.load("PRIVATE-native-job", access)
          val missing = fixture.store.load("PRIVATE-missing-job", access)
          val startup = fixture.store.loadStartupRecovery("PRIVATE-native-job", access)
          val advanced = success(fixture.store.checkpoint(created, next(original), access))
          val stale = fixture.store.checkpoint(created, next(original), access)
          val usage = success(fixture.store.resourceSnapshot())
          val row = fixture.rawJob("PRIVATE-native-job").get
          val quota = fixture.rawQuota()
          val baseline = fixture.observations
          val expected = Vector(point("create", "succeeded", 1L), point("load", "found", 3L),
            point("load", "missing", 1L), point("startup", "admitted", 1L),
            point("checkpoint", "succeeded", 1L), point("checkpoint", "failed", 1L, true),
            point("storage_snapshot", "succeeded", 1L))
          Then("only actual completed boundaries count, the current revision is canonical and twenty diagnostic reads cause no native activity")
          found shouldBe Consequence.Success(Some(created))
          missing shouldBe Consequence.Success(None)
          startup shouldBe DurableJobStoreStartupRecoveryLoad.Admitted(created)
          stale shouldBe a[Consequence.Failure[_]]
          created.record shouldBe original
          created.providerrevision.value shouldBe 1L
          advanced.providerrevision.value shouldBe 2L
          advanced.record shouldBe next(original)
          advanced.record.format shouldBe original.format
          row.getString("canonicalJson") shouldBe Some(canonical(advanced.record))
          row.getLong("cncf_revision") shouldBe Some(2L)
          usage.retainedjobs shouldBe 1
          usage.pendingreservations shouldBe 0
          usage.canonicalbytes shouldBe bytes(advanced.record)
          fixture.entity.calls.count(_ == "create:durableJob") shouldBe 1
          fixture.entity.calls.count(_ == "updateDetached:durableJob") shouldBe 1
          fixture.entity.calls.count(_ == "loadDetached:durableJob") shouldBe 6
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
          fixture.observations shouldBe baseline
          fixture.rawJob("PRIVATE-native-job") shouldBe Some(row)
          fixture.rawQuota() shouldBe quota
          _privacy(expected)
        }
      }
    }
  }

  "E5 DS2 native quota and authorization refusals" should _scenario("E5", "R1,R3,R4") {
    "retain structured failures and native rows without granting capacity through counters" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R4; E5/DS2: one admitted native row at a small real storage cardinality bound and private foreign/malformed requests")
      DurableJobOperationalMetricsFixtures.withFixture(DurableJobStorageResourcePolicy(1)) { fixture =>
        given ExecutionContext = fixture.context
        val original = success(fixture.store.create(record("PRIVATE-owner-job"), access))
        val advanced = success(fixture.store.checkpoint(original, next(original.record), access))
        val row = fixture.rawJob("PRIVATE-owner-job")
        val quota = fixture.rawQuota()
        val writes = fixture.entity.calls.count(_ == "create:durableJob")
        When("a second identity, foreign create, malformed record, stale checkpoint and foreign load are refused before native publication")
        val full = fixture.store.create(record("PRIVATE-capacity-job"), access)
        val foreign = fixture.store.create(record("PRIVATE-foreign-job"), access.copy(subjectId = "PRIVATE-foreign"))
        val malformed = fixture.store.create(original.record.copy(integrity = original.record.integrity.copy(unsignedBodySha256 = "0" * 64)), access)
        val stale = fixture.store.checkpoint(original, next(original.record), access)
        val denied = fixture.store.load("PRIVATE-owner-job", access.copy(subjectId = "PRIVATE-foreign"))
        val absent = fixture.store.load("PRIVATE-absent-job", access)
        val usage = success(fixture.store.resourceSnapshot())
        Then("failed calls remain failed, missing remains successful None, and only the original accepted row and checkpoint affect durable authority")
        Vector(full, foreign, malformed, stale, denied).foreach(_ shouldBe a[Consequence.Failure[_]])
        DurableJobOperationalMetricsFixtures.message(full) should include(DurableJobStorageResourcePolicy.capacityLabel)
        absent shouldBe Consequence.Success(None)
        advanced.providerrevision.value shouldBe 2L
        usage.retainedjobs shouldBe 1
        usage.pendingreservations shouldBe 0
        fixture.entity.calls.count(_ == "create:durableJob") shouldBe writes
        fixture.entity.calls.count(_ == "updateDetached:durableJob") shouldBe 1
        fixture.rawJob("PRIVATE-owner-job") shouldBe row
        fixture.rawJob("PRIVATE-capacity-job") shouldBe None
        fixture.rawQuota() shouldBe quota
        val expected = Vector(point("create", "succeeded", 1L), point("create", "failed", 3L, true),
          point("load", "found", 2L), point("load", "missing", 1L), point("load", "failed", 1L, true),
          point("checkpoint", "succeeded", 1L), point("checkpoint", "failed", 1L, true),
          point("storage_snapshot", "succeeded", 1L))
        val baseline = fixture.observations
        (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
        fixture.observations shouldBe baseline
        _privacy(expected)
      }
    }
  }

  "E6 DS3 closed startup and original exception boundary" should _scenario("E6", "R1,R2,R4") {
    "observe actual corruption, refusal and absence while leaving a thrown startup uncounted" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R2,R4; E6/DS3: actual raw SQLite corruption and a measured native loadDetached sentinel")
      DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
        given ExecutionContext = fixture.context
        fixture.seed("PRIVATE-corrupt-job", "PRIVATE-not-json")
        val owner = success(fixture.store.create(record("PRIVATE-authorized-job"), access))
        val corruptrow = fixture.rawJob("PRIVATE-corrupt-job")
        val ownerrow = fixture.rawJob("PRIVATE-authorized-job")
        When("startup classifies corrupted, unauthorized and missing rows without repairing them")
        val corrupt = fixture.store.loadStartupRecovery("PRIVATE-corrupt-job", access)
        val refused = fixture.store.loadStartupRecovery("PRIVATE-authorized-job", access.copy(subjectId = "PRIVATE-foreign"))
        val missing = fixture.store.loadStartupRecovery("PRIVATE-absent-job", access)
        val points = fixture.store.operationalMetrics
        val baseline = fixture.observations
        Then("the existing closed facets are returned and metrics cause no repair, quota write or provider call")
        corrupt shouldBe DurableJobStoreStartupRecoveryLoad.Corrupt
        refused shouldBe DurableJobStoreStartupRecoveryLoad.Refused
        missing shouldBe DurableJobStoreStartupRecoveryLoad.Missing
        points shouldBe Vector(point("create", "succeeded", 1L), point("startup", "missing", 1L),
          point("startup", "corrupt", 1L, true), point("startup", "refused", 1L, true))
        (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe points)
        fixture.observations shouldBe baseline
        fixture.rawJob("PRIVATE-corrupt-job") shouldBe corruptrow
        fixture.rawJob("PRIVATE-authorized-job") shouldBe ownerrow
        owner.record.body.identity.jobId shouldBe "PRIVATE-authorized-job"
        Given("E6/R1: the actual EntityStore is armed to throw one precise loadDetached exception")
        val sentinel = new IllegalStateException("PRIVATE-provider-sentinel")
        fixture.entity.loadsentinel = Some(sentinel)
        When("startup throws that sentinel, then create reaches its original bounded private load after a real write")
        val thrown = intercept[IllegalStateException](fixture.store.loadStartupRecovery("PRIVATE-authorized-job", access))
        val afterthrown = fixture.store.operationalMetrics
        val failedcreate = fixture.store.create(record("PRIVATE-write-before-read-failure"), access)
        fixture.entity.loadsentinel = None
        Then("startup propagates the identical exception without a count, while bounded create counts only its final native Failure")
        (thrown eq sentinel) shouldBe true
        afterthrown shouldBe points
        failedcreate shouldBe a[Consequence.Failure[_]]
        DurableJobOperationalMetricsFixtures.message(failedcreate) shouldBe DurableJobOperationalMetricsFixtures.message(DurableJobStorageResourcePolicy.unavailable[String])
        fixture.store.operationalMetrics shouldBe points.patch(1, Vector(point("create", "failed", 1L, true)), 0)
        fixture.rawJob("PRIVATE-write-before-read-failure").get.getString("canonicalJson") shouldBe
          Some(canonical(record("PRIVATE-write-before-read-failure")))
        _privacy(fixture.store.operationalMetrics)
      }
    }
  }

  "E7 DS4 lost native acknowledgement observations" should _scenario("E7", "R1,R3,R8") {
    "count failed create and checkpoint despite real persisted V1 and V2 effects and conservative reservations" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R1,R3,R8; E7/DS4: separate actual SQLite fixtures for post-super create and checkpoint acknowledgement loss")
      Vector(false, true).foreach { v2 =>
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = record("PRIVATE-lost-create", v2)
          fixture.entity.losecreateack = true
          When("the actual native create succeeds physically and the overriding adapter loses its acknowledgement")
          val result = fixture.store.create(original, access)
          val row = fixture.rawJob("PRIVATE-lost-create").get
          val quota = fixture.rawQuota()
          val usage = success(fixture.store.resourceSnapshot())
          val baseline = fixture.observations
          Then("Failure is counted without compensation, the canonical native row persists and its conservative pending reservation remains")
          result shouldBe a[Consequence.Failure[_]]
          DurableJobOperationalMetricsFixtures.message(result) shouldBe DurableJobOperationalMetricsFixtures.message(DurableJobStorageResourcePolicy.unavailable[String])
          row.getString("canonicalJson") shouldBe Some(canonical(original))
          row.getLong("cncf_revision") shouldBe Some(1L)
          usage.retainedjobs shouldBe 1
          usage.pendingreservations shouldBe 1
          fixture.entity.calls.count(_ == "create:durableJob") shouldBe 1
          val expected = Vector(point("create", "failed", 1L, true), point("storage_snapshot", "succeeded", 1L))
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
          fixture.observations shouldBe baseline
          fixture.rawQuota() shouldBe quota
        }
        DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
          given ExecutionContext = fixture.context
          val original = success(fixture.store.create(record("PRIVATE-lost-checkpoint", v2), access))
          val updated = next(original.record)
          fixture.entity.loseupdateack = true
          When("the native checkpoint advances the provider revision and the overriding adapter then loses acknowledgement")
          val result = fixture.store.checkpoint(original, updated, access)
          val row = fixture.rawJob("PRIVATE-lost-checkpoint").get
          val quota = fixture.rawQuota()
          val usage = success(fixture.store.resourceSnapshot())
          val baseline = fixture.observations
          Then("failed checkpoint retains its real nested load, persisted advanced bytes and conservative native reservation")
          result shouldBe a[Consequence.Failure[_]]
          DurableJobOperationalMetricsFixtures.message(result) shouldBe DurableJobOperationalMetricsFixtures.message(DurableJobStorageResourcePolicy.unavailable[String])
          row.getString("canonicalJson") shouldBe Some(canonical(updated))
          row.getLong("cncf_revision") shouldBe Some(2L)
          usage.retainedjobs shouldBe 1
          usage.pendingreservations shouldBe 1
          fixture.entity.calls.filter(_.endsWith(":durableJob")) shouldBe
            Vector("create:durableJob", "loadDetached:durableJob", "loadDetached:durableJob", "updateDetached:durableJob")
          val expected = Vector(point("create", "succeeded", 1L), point("load", "found", 1L),
            point("checkpoint", "failed", 1L, true), point("storage_snapshot", "succeeded", 1L))
          (0 until 20).foreach(_ => fixture.store.operationalMetrics shouldBe expected)
          fixture.observations shouldBe baseline
          fixture.rawQuota() shouldBe quota
        }
      }
    }
  }

  "E8 DS5 native Store lifetime and shared adapters" should _scenario("E8", "R4,R5") {
    "share one recorder across bound adapters while fresh stores and closed-provider snapshots retain their own history" in {
      Given("docs/spec/durable-job-operational-metrics-contract.md R4,R5; E8/DS5: actual persisted rows and two sets of payload, retention and cleanup adapters bound to the exact same Store")
      DurableJobOperationalMetricsFixtures.withFixture() { fixture =>
        given ExecutionContext = fixture.context
        val original = success(fixture.store.create(record("PRIVATE-shared-store"), access))
        val secondpayloads = new DurableJobOwnedBlobPayloads(fixture.store, fixture.entity, fixture.blob)
        val secondretention = new DurableJobRetentionMaintenance(fixture.store)
        val secondcleanup = new DurableJobPayloadCleanup(fixture.store, secondpayloads)
        When("both adapter sets return real zero-owned prepares and early authorization refusals, then a fresh Store is constructed against the same SQLite owner")
        fixture.payloads.prepare(original.record) shouldBe Consequence.Success(Vector.empty)
        secondpayloads.prepare(original.record) shouldBe Consequence.Success(Vector.empty)
        fixture.retention.maintainObserved(original, null, DurableJobRetentionCommand.Expire, now) shouldBe DurableJobRetentionFacet.Refused
        secondretention.maintainObserved(original, null, DurableJobRetentionCommand.Expire, now) shouldBe DurableJobRetentionFacet.Refused
        fixture.cleanup.cleanupObserved(original, null, now) shouldBe DurableJobCleanupFacet.Refused
        secondcleanup.cleanupObserved(original, null, now) shouldBe DurableJobCleanupFacet.Refused
        val fresh = new DurableJobStore(fixture.entity)
        val expected = Vector(point("create", "succeeded", 1L), point("payload_prepare", "succeeded", 2L),
          point("retention", "refused", 2L, true), point("cleanup", "refused", 2L, true))
        val baseline = fixture.observations
        Then("bound adapters share exactly one primitive history and the newly constructed Store starts empty without reading durable rows")
        fresh.operationalMetrics shouldBe Vector.empty
        fixture.store.operationalMetrics shouldBe expected
        (fresh.operationalMetricsRecorder eq fixture.store.operationalMetricsRecorder) shouldBe false
        fixture.observations shouldBe baseline
        Given("E8/R4,R5: the native SQLite provider is now explicitly closed")
        fixture.close()
        val closedbaseline = fixture.observations
        When("twenty diagnostic reads are requested after native close")
        val snapshots = Vector.fill(20)(fixture.store.operationalMetrics)
        Then("closed-provider diagnostics retain only their prior primitives and call no provider, clock or ID generator")
        snapshots.foreach(_ shouldBe expected)
        fresh.operationalMetrics shouldBe Vector.empty
        fixture.observations shouldBe closedbaseline
      }
    }
  }

  private def _privacy(points: Vector[RuntimeMetricPoint]): Unit = points.foreach { point =>
    point.labels.keySet shouldBe Set("operation", "outcome")
    (Vector(point.scope, point.name) ++ point.labels.keys ++ point.labels.values).foreach(_ should not include "PRIVATE")
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-operational-metrics, example:$example, rules:$rules, phase:69.7")
}
