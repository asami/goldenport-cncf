package org.goldenport.cncf.job

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobIntegrityInspectionFixtures.*
import JobDurableIntegrityStatus.*

/*
 * @since   Oct.  3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobIntegrityInspectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-integrity-operations, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1C")

  "IP1 Whole trusted source admission" must _scenario("IP1", "I3,I4,I12") {
    "refuse every malformed tail before the valid prefix reaches native storage" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I3,I4,I12 IP1: an actual Ready managed owner and a genuine valid prefix")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val root = fixture.publish("admission")
        val first = candidate(root.jobId)
        val tail = candidate(JobInputBoundsFixtures.id("tail").value, 1L)
        val invalid = Vector(Vector(first, null), Vector(first, tail.copy(ordinal = 7L)),
          Vector(first, first.copy(ordinal = 1L)), Vector(first, tail.copy(jobId = "x" * 513)),
          Vector(first, tail.copy(jobId = "界" * 200)), Vector(first, tail.copy(access = null)),
          Vector(first, tail.copy(access = access.copy(scopes = null))),
          Vector(first, tail.copy(replayEvidence = replay.copy(input = null))),
          Vector(first, tail.copy(replayEvidence = null)), null)
        Given("native load and trusted source call counters are captured before any refused inspection")
        val before = fixture.entity.loads.get()
        val calls = fixture.source.calls.get()
        Vector(0, -1, 1025, Int.MaxValue).foreach { limit =>
          When(s"the Ready owner receives the invalid candidate bound $limit")
          val refused = fixture.inspect(limit)
          Then("the invalid bound returns the fixed refusal before trusted discovery")
          _fixed(refused)
        }
        Then("all invalid bounds leave the trusted source call count unchanged")
        fixture.source.calls.get() shouldBe calls
        invalid.foreach { entries =>
          Given("the trusted source contains an invalid whole vector with the genuine valid prefix")
          fixture.source.entries = entries
          When("the Ready owner admits a candidate limit of two and requests that whole vector")
          val refused = fixture.inspect(2)
          Then("the malformed whole vector returns the fixed refusal before any prefix native load")
          _fixed(refused)
        }
        Then("each malformed vector is discovered exactly once with the admitted limit of two")
        fixture.source.limits.drop(calls) shouldBe Vector.fill(invalid.size)(2)
        Given("the valid source candidate has an explicit private-text discovery refusal")
        fixture.source.entries = Vector(first)
        fixture.source.result = Some(Consequence.stateInvalid("PRIVATE source refusal"))
        When("inspection requests that source refusal with a limit of one")
        val sourcerefused = fixture.inspect(1)
        Then("the source refusal is reduced to the fixed operational refusal")
        _fixed(sourcerefused)
        Given("the source returns a null consequence instead of the valid candidate")
        fixture.source.result = Some(null)
        When("inspection requests the null source result with a limit of one")
        val nullrefused = fixture.inspect(1)
        Then("the null source result returns the same fixed operational refusal")
        _fixed(nullrefused)
        Given("the source throws an exception containing private text before returning its vector")
        fixture.source.result = None
        fixture.source.before = () => throw new IllegalStateException("PRIVATE source throw")
        When("inspection invokes the throwing source with a limit of one")
        val throwrefused = fixture.inspect(1)
        Then("the source exception returns the fixed refusal without exposing its text")
        _fixed(throwrefused)
        Given("the valid source callback is restored before invalid private byte budgets are supplied")
        fixture.source.before = () => ()
        When("inspection is constructed with a zero private byte budget")
        val zerorefused = fixture.inspect(1, 0L)
        Then("the zero byte budget returns the fixed refusal")
        _fixed(zerorefused)
        When("inspection is constructed with a private byte budget above the original ceiling")
        val excessbudgetrefused = fixture.inspect(1, 16777217L)
        Then("the excessive byte budget returns the fixed refusal")
        _fixed(excessbudgetrefused)
        Given("the trusted source supplies three distinct candidates for an admitted limit of two")
        val excess = Vector.tabulate(3)(index => candidate(JobInputBoundsFixtures.id(s"excess$index").value, index.toLong))
        fixture.source.entries = excess
        When("inspection requests that excessive whole vector with a limit of two")
        val excessrefused = fixture.inspect(2)
        Then("the excessive vector returns the fixed refusal")
        _fixed(excessrefused)
        Then("every refusal is constant and discovery occurs exactly once per admitted batch with no prefix native load")
        fixture.entity.loads.get() shouldBe before
        fixture.blob.gets.get() shouldBe 0
      }
    }
  }
  "IP2 Genuine native integrity" must _scenario("IP2", "I1,I3,I5,I6,I7,I9,I10") {
    "preserve all canonical records revisions payments and local bytes while inspecting zero and owned targets" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I1,I3,I5,I6,I7,I9,I10 IP2: genuinely Published zero-owned and two-owned native roots")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val empty = fixture.publish("no_owned")
        val owned = fixture.publish("real_owned", Vector(Array[Byte](1, 2, 3), Array.emptyByteArray))
        fixture.select(empty, owned)
        val before = fixture.state(empty, owned); val effects = fixture.effects
        When("the trusted source supplies both roots to one read-only operation")
        val report = success(fixture.inspect(2))
        Then("ordinal-only Healthy evidence counts three actual bytes and leaves native rows metadata and authority untouched")
        report shouldBe JobDurableIntegrityReport(Vector(JobDurableIntegrityEntry(0L, Healthy), JobDurableIntegrityEntry(1L, Healthy)), 3L)
        report.isHealthy shouldBe true
        fixture.state(empty, owned) shouldBe before; fixture.effects shouldBe effects
        fixture.blob.gets.get() shouldBe 2; fixture.blob.opens.get() shouldBe 2; fixture.blob.closes.get() shouldBe 2
        fixture.blob.bytes.get() shouldBe report.physicalBytesRead
      }
    }
  }
  "IP3 Native closed load facets" must _scenario("IP3", "I3,I5,I12") {
    "retain source order across missing corrupt unauthorized and unaffected good candidates" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I3,I5,I12 IP3: actual absent tampered intact unauthorized and good native rows")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val corrupt = fixture.publish("corrupt"); val denied = fixture.publish("denied"); val good = fixture.publish("good")
        fixture.replaceText(fixture.jobEntityId(corrupt.jobId), "PRIVATE invalid canonical credential")
        fixture.source.entries = Vector(candidate(JobInputBoundsFixtures.id("missing").value), candidate(corrupt.jobId, 1L),
          candidate(denied.jobId, 2L, access.copy(subjectId = "foreign", scopes = Set.empty)), candidate(good.jobId, 3L))
        val before = fixture.state(corrupt, denied, good); val effects = fixture.effects
        When("one admitted whole vector requests exact native load evidence")
        val report = success(fixture.inspect(4))
        Then("only ordinals and closed facets escape and the unaffected final candidate remains Healthy")
        report.entries.map(_.status) shouldBe Vector(Missing, Corrupt, AccessRefused, Healthy)
        report.entries.map(_.ordinal) shouldBe Vector(0L, 1L, 2L, 3L)
        report.isHealthy shouldBe false; report.physicalBytesRead shouldBe 0L
        Vector(corrupt.jobId, denied.jobId, good.jobId, "PRIVATE", "credential", access.tenantId).foreach(value => report.toString should not include value)
        fixture.state(corrupt, denied, good) shouldBe before; fixture.effects shouldBe effects
      }
    }
  }
  "IP4 Authenticated inactive records" must _scenario("IP4", "I3,I5") {
    "classify expired deleted and tombstoned records without reading lifetime or physical payloads" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I3,I5 IP4: three signed authorized inactive retained records with genuine owned bytes")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val states = Vector(DurableDeletionState.Expired, DurableDeletionState.Deleted, DurableDeletionState.Tombstoned)
        val roots = states.zipWithIndex.map { case (state, index) =>
          val root = fixture.publish(s"inactive$index", Vector(Array[Byte](7)))
          val retention = state match {
            case DurableDeletionState.Expired => root.snapshot.record.body.retention.copy(expiresAt = Some(instant), deletionState = state)
            case DurableDeletionState.Deleted => root.snapshot.record.body.retention.copy(deletedAt = Some(instant), deletionState = state)
            case _ => root.snapshot.record.body.retention.copy(deletedAt = Some(instant), deletionState = state,
              tombstone = Some(DurableTombstone(instant, "retained", None)))
          }
          root.copy(snapshot = fixture.checkpoint(root.snapshot, root.snapshot.record.body.copy(retention = retention)))
        }
        fixture.select(roots*)
        val before = fixture.state(roots*); val effects = fixture.effects; val lifetime = fixture.entity.lifetimeloads.get()
        When("inspection authenticates each signed inactive record")
        val report = success(fixture.inspect(3))
        Then("all entries are Inactive without revival payment or Blob access and the report is nonhealthy")
        report.entries.map(_.status) shouldBe Vector.fill(3)(Inactive); report.isHealthy shouldBe false
        fixture.entity.lifetimeloads.get() shouldBe lifetime; fixture.blob.gets.get() shouldBe 0
        fixture.state(roots*) shouldBe before; fixture.effects shouldBe effects
      }
    }
  }
  "IP5 Intact legacy format" must _scenario("IP5", "I5") {
    "require migration for a signed Active V1 without converting or paying it" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I5 IP5: an actual authorized signed V1 native row with no lifetime payment")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val id = JobInputBoundsFixtures.id("legacy")
        val body = JobDurableLifetimeRecoveryFixtures.record(id).body
        val record = success(DurableJobRecord.create(body.copy(
          lifecycle = body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded,
            schedule = body.lifecycle.schedule.copy(startedAt = Some(instant.plusSeconds(10)), completedAt = Some(instant.plusSeconds(20)))),
          result = DurableResultOutcome.Succeeded(DurableValue.Absent))))
        val root = fixture.use(Root(success(fixture.owner._store.create(record, access)(using fixture.context)), Vector.empty))
        fixture.select(root)
        val before = fixture.state(root); val effects = fixture.effects; val lifetime = fixture.entity.lifetimeloads.get()
        When("inspection encounters the intact Active V1 format")
        val report = success(fixture.inspect(1))
        Then("MigrationRequired retains exact native canonical bytes and revision with no lifetime or Blob access")
        report.entries.map(_.status) shouldBe Vector(MigrationRequired)
        fixture.state(root) shouldBe before; fixture.effects shouldBe effects
        fixture.entity.lifetimeloads.get() shouldBe lifetime; fixture.blob.gets.get() shouldBe 0
        fixture.row(lifetimeId) shouldBe None
      }
    }
  }
  "IP6 Refused lifetime authority" must _scenario("IP6", "I5,I12") {
    "preserve missing Pending wrong-policy and invalid-paid native ledgers without repair or payload access" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I5,I12 IP6: Ready owners precede deliberate damage to genuinely Published lifetime authority")
      Vector("missing", "pending", "policy", "paid").foreach { mode =>
        DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
          val root = fixture.publish(s"lifetime_$mode", Vector(Array[Byte](1)))
          fixture.select(root)
          mode match {
            case "missing" => fixture.removeRow(lifetimeId)
            case "pending" => fixture.mutateLifetime(current => current.copy(entries = current.entries.map(_.copy(rootstate = DurableJobLifetimeQuotaRootState.Pending))))
            case "policy" => fixture.mutateLifetime(current => current.copy(policy = current.policy.copy(maxledgerjobs = 2)))
            case _ => fixture.replaceText(lifetimeId, fixture.lifetimeText.replace("\"chargedTasks\":2", "\"chargedTasks\":0"))
          }
          val before = fixture.state(root); val effects = fixture.effects
          When(s"inspection requests the actual $mode lifetime authority")
          val report = success(fixture.inspect(1))
          Then("LifetimeRefused retains the damaged or absent native ledger with no reconstruction or physical get")
          report.entries.map(_.status) shouldBe Vector(LifetimeRefused)
          fixture.state(root) shouldBe before; fixture.effects shouldBe effects; fixture.blob.gets.get() shouldBe 0
        }
      }
    }
  }
  "IP7 Whole immutable ownership proof" must _scenario("IP7", "I6,I8") {
    "refuse protected collisions conflicting duplicates and metadata mismatches before the first get" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I6,I8 IP7: genuine owned bytes and unchanged paid native ownership")
      Vector("shared", "definition", "duplicate", "owner", "binding", "size").foreach { mode =>
        DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
          val initial = fixture.publish(s"ownership_$mode", Vector(Array[Byte](1, 2)))
          val ref = initial.references.head
          val snapshot = mode match {
            case "shared" => fixture.attach(initial.snapshot, Vector(ref, ref.copy(storageKind = "shared-blob")))
            case "definition" => fixture.checkpoint(initial.snapshot, initial.snapshot.record.body.copy(
              definitionSnapshot = initial.snapshot.record.body.definitionSnapshot.copy(declaredSource = Some(ref))))
            case "duplicate" => fixture.attach(initial.snapshot, Vector(ref, ref.copy(byteSize = ref.byteSize + 1L)))
            case "owner" => fixture.replaceMetadata(ref, blob => blob.copy(attributes = blob.attributes +
              (DurableJobOwnedBlobPayloads.ownerAttribute -> ("0" * 64)))); initial.snapshot
            case "binding" => fixture.replaceMetadata(ref, blob => blob.copy(attributes = blob.attributes +
              (DurableJobOwnedBlobPayloads.bindingAttribute -> ("0" * 64)))); initial.snapshot
            case _ => fixture.replaceMetadata(ref, blob => blob.copy(byteSize = Some(ref.byteSize + 1L))); initial.snapshot
          }
          val root = initial.copy(snapshot = snapshot); fixture.select(root)
          val before = fixture.state(root); val effects = fixture.effects
          When(s"the entire candidate's $mode proof is inspected")
          val report = success(fixture.inspect(1))
          Then("PayloadRefused precedes every physical get and leaves metadata bytes and retained capacity unchanged")
          report.entries.map(_.status) shouldBe Vector(PayloadRefused); fixture.blob.gets.get() shouldBe 0
          fixture.state(root) shouldBe before; fixture.effects shouldBe effects
        }
      }
    }
  }
  "IP8 Strict Published Blob ledger" must _scenario("IP8", "I6,I7") {
    "refuse hostile native quota states without mutation and inspect repeated owned references once" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I6,I7 IP8: a genuinely Published Blob ledger and deliberate native hostile variants")
      Vector("pending", "released", "missing", "malformed", "binding", "policy", "duplicate").foreach { mode =>
        DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
          val first = fixture.publish(s"blobquota_$mode", Vector(Array[Byte](4, 5)))
          val root = if (mode == "duplicate") first.copy(snapshot = fixture.attach(first.snapshot,
            Vector.fill(3)(first.references.head))) else first
          fixture.select(root)
          mode match {
            case "pending" => fixture.mutateBlobQuota(current => current.copy(entries = current.entries.map(_.copy(
              state = DurableJobOwnedBlobQuotaState.Pending, bindingsha256 = None))))
            case "released" => fixture.mutateBlobQuota(current => current.copy(entries = current.entries.map(_.copy(state = DurableJobOwnedBlobQuotaState.Released))))
            case "missing" => fixture.removeRow(blobQuotaId)
            case "malformed" => fixture.replaceText(blobQuotaId, "PRIVATE malformed native ledger")
            case "binding" => fixture.mutateBlobQuota(current => current.copy(entries = current.entries.map(_.copy(bindingsha256 = Some("0" * 64)))))
            case "policy" => fixture.mutateBlobQuota(current => current.copy(policy = current.policy.copy(maxpayloadbytes = 64)))
            case _ => ()
          }
          val before = fixture.state(root); val effects = fixture.effects
          When(s"inspection encounters the native $mode Blob quota proof")
          val report = success(fixture.inspect(1))
          Then("only exact Published metadata succeeds and inspection never bootstraps reconciles releases or performs CAS")
          report.entries.map(_.status) shouldBe Vector(if (mode == "duplicate") Healthy else PayloadRefused)
          fixture.blob.gets.get() shouldBe (if (mode == "duplicate") 1 else 0)
          fixture.state(root) shouldBe before; fixture.effects shouldBe effects
        }
      }
    }
  }
  "IP9 Bounded real physical stream" must _scenario("IP9", "I3,I9,I12") {
    "detect tamper truncation sentinel overrun provider faults and interruption while closing every opened stream" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I3,I9,I12 IP9: actual local payload files and explicit fault wrappers over their real streams")
      val modes = Vector("tamper", "truncate", "overrun", "zero", "uppercase-digest", "provider-refuse",
        "provider-throw", "provider-interrupt", "stream-throw", "stream-interrupt", "close-throw", "null-result",
        "null-payload", "wrong-id", "wrong-ref", "wrong-content", "wrong-size", "wrong-digest")
      modes.foreach { mode =>
        DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
          val root = fixture.publish(s"physical_${mode.replace('-', '_')}", Vector(Array[Byte](1, 2, 3)))
          fixture.select(root)
          mode match {
            case "tamper" => Files.write(fixture.physicalPath(root.references.head), Array[Byte](1, 7, 3))
            case "truncate" => Files.write(fixture.physicalPath(root.references.head), Array[Byte](1, 2))
            case "overrun" => Files.write(fixture.physicalPath(root.references.head), Array[Byte](1, 2, 3, 4, 5, 6))
            case _ => fixture.blob.mode = mode
          }
          val before = fixture.state(root); val effects = fixture.effects
          When(s"inspection consumes the actual $mode physical proof")
          val result = fixture.inspect(1)
          try {
            Then("exact physical bytes determine the closed facet and consumption stays within one sentinel with finally-close")
            if (mode.endsWith("interrupt")) {
              _fixed(result); Thread.currentThread().isInterrupted shouldBe true
              Thread.interrupted()
            } else {
              val report = success(result)
              report.entries.map(_.status) shouldBe Vector(if (Set("zero", "uppercase-digest").contains(mode)) Healthy else PayloadRefused)
              report.physicalBytesRead shouldBe fixture.blob.bytes.get()
            }
            fixture.blob.bytes.get() should be <= 4L
            if (mode == "overrun") fixture.blob.bytes.get() shouldBe 4L
            if (mode == "zero") fixture.blob.bytes.get() shouldBe 3L
            fixture.blob.opens.get() shouldBe fixture.blob.closes.get()
            fixture.blob.opens.get() should be <= 1
            fixture.state(root) shouldBe before; fixture.effects shouldBe effects
          } finally { Thread.interrupted(); () }
        }
      }
    }
  }
  "IP10 Inclusive native byte budget property" must _scenario("IP10", "I6,I8,I9,I13") {
    "reserve whole candidates before gets and retain duplicate order across exact-fit and one-under budgets" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I6,I8,I9,I13 IP10: bounded byte lengths and duplicate ordering with seed 69709703 and ordinary shrinking")
      val generated = for { first <- Gen.choose(0, 16); second <- Gen.choose(0, 16); reverse <- Gen.oneOf(true, false) } yield (first, second, reverse)
      val property = Prop.forAll(generated) { case (first, second, reverse) =>
        DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
          val initial = fixture.publish("budget_first", Vector(Array.fill[Byte](first)(1), Array.fill[Byte](second)(2)))
          val order = if (reverse) initial.references.reverse else initial.references
          val root = initial.copy(snapshot = fixture.attach(initial.snapshot, order ++ order))
          val next = fixture.publish("budget_second", Vector(Array[Byte](9)))
          fixture.select(root, next)
          val before = fixture.state(root, next); val effects = fixture.effects
          val cost = first.toLong + second.toLong + 2L
          When("both real owned Jobs cross the exact inclusive total and a whole-candidate one-under budget")
          val exact = success(fixture.inspect(2, cost + 2L))
          val calls = fixture.blob.gets.get(); val refs = fixture.blob.refs.size
          val under = success(fixture.inspect(2, cost - 1L))
          Then("exact fit verifies in original distinct order and excessive candidate reservation performs zero gets for that candidate")
          exact.entries.map(_.status) shouldBe Vector(Healthy, Healthy)
          exact.physicalBytesRead shouldBe first.toLong + second.toLong + 1L
          fixture.blob.refs.take(2).map(_.print) shouldBe order.map(ref => fixture.blobMetadata(ref).storageRef.get.print)
          under.entries.head.status shouldBe ResourceBoundRefused
          under.physicalBytesRead should be <= (cost - 1L)
          fixture.blob.refs.drop(refs).forall(ref => ref == fixture.blobMetadata(next.references.head).storageRef.get) shouldBe true
          fixture.blob.gets.get() - calls should be <= 1
          fixture.blob.mode = "wrong-digest"
          val refused = success(fixture.inspect(2, cost + 1L))
          refused.entries.map(_.status) shouldBe Vector(PayloadRefused, ResourceBoundRefused)
          refused.physicalBytesRead shouldBe 0L
          fixture.blob.mode = "normal"
          fixture.state(root, next) shouldBe before; fixture.effects shouldBe effects
        }
        true
      }
      When("ScalaCheck executes at least 24 seeded native cases with shrinking enabled")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709703L)), property)
      Then("the bounded physical budget property succeeds for every generated native case")
      checked.passed shouldBe true; checked.succeeded should be >= 24
      Given("an original one-byte policy and a genuinely Published target whose native size and binding are deliberately damaged above that ceiling")
      val path = JobDurableRuntimeFixtures.root(); val fixture = new Fixture(path)
      fixture.blobPolicy = DurableJobOwnedBlobResourcePolicy.default.copy(maxpayloadbytes = 1)
      try {
        success(fixture.start())
        val first = fixture.publish("payload_ceiling", Vector(Array[Byte](1)))
        val bytes = Array[Byte](1, 2)
        val digest = DurableJobOwnedBlobResourcePolicy.sha256(bytes)
        val ref = first.references.head.copy(byteSize = 2L, sha256 = digest)
        fixture.replaceMetadata(first.references.head, blob => {
          val binding = DurableJobOwnedBlobPayloads.bindingFingerprint(blob.id, blob.storageRef.get, blob.contentType.get, 2L, digest)
          blob.copy(byteSize = Some(2L), digest = Some(digest), attributes = blob.attributes + (DurableJobOwnedBlobPayloads.bindingAttribute -> binding))
        })
        val metadata = fixture.blobMetadata(ref)
        fixture.mutateBlobQuota(current => current.copy(entries = current.entries.map(_.copy(bytesize = 2L,
          payloadsha256 = digest, bindingsha256 = metadata.attributes.get(DurableJobOwnedBlobPayloads.bindingAttribute)))))
        val root = first.copy(snapshot = fixture.attach(first.snapshot, Vector(ref)))
        fixture.select(root)
        val before = fixture.state(root); val effects = fixture.effects
        When("the complete candidate requests physical admission above its original per-payload ceiling")
        val report = success(fixture.inspect(1, 3L))
        Then("ResourceBoundRefused occurs before the first get without changing the hostile original native facts")
        report.entries.map(_.status) shouldBe Vector(ResourceBoundRefused)
        fixture.blob.gets.get() shouldBe 0; fixture.state(root) shouldBe before; fixture.effects shouldBe effects
      } finally { fixture.close(); JobDurableRuntimeFixtures.remove(path) }
    }
  }
  private def _fixed(result: Consequence[?]): Unit =
    failure(result).show shouldBe failure(DurableJobIntegrityInspection.refused[Unit]).show
}
