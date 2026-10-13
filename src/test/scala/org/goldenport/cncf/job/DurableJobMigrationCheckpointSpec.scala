package org.goldenport.cncf.job

import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CountDownLatch
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobMigrationCheckpointFixtures.*

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobMigrationCheckpointSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-migration-checkpoint, example:$example, rules:$rules, phase:69.7, slice:D2")

  "MC1 Authorized whole-vector admission" must _scenario("MC1", "M3,M4,M11") {
    "refuse malformed requests before any healthy prefix native load or write" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M3,M4,M11 MC1: a genuine native prefix and independently authorized and ordinary contexts")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val root = fixture.legacy(fixture.publish("admission"))
        val first = candidate(root.jobId)
        val tail = candidate(JobInputBoundsFixtures.id("tail").value, 1L)
        val entries = Vector[Vector[DurableJobStartupRecoveryCandidate]](null, Vector(first, null),
          Vector(first, tail.copy(ordinal = 9L)), Vector(first, first.copy(ordinal = 1L)),
          Vector(first, tail.copy(jobId = "x" * 513)), Vector(first, tail.copy(jobId = "界" * 200)),
          Vector(first, tail.copy(access = null)), Vector(first, tail.copy(access = access.copy(scopes = null))),
          Vector(first, tail.copy(replayEvidence = replay.copy(input = null))),
          Vector(first, tail.copy(replayEvidence = null)), Vector(first, tail, tail.copy(ordinal = 2L)))
        val before = fixture.state(root); val effects = fixture.effects; val loads = fixture.entity.loads.get()
        entries.foreach { batch =>
          Given("the cached batch contains an invalid tail or null whole vector")
          val malformed = batch
          When("the explicit Operator migration admits the entire cached batch with a bound of two")
          val result = fixture.migrate(malformed, 2)
          Then("the fixed refusal precedes every prefix load and checkpoint")
          _fixed(result)
        }
        Given("an ordinary context and invalid constructor inputs are supplied together")
        val unauthorized = new DurableJobMigrationCheckpoint(null, null, null, null, 0L)
        When("that ordinary context requests the malformed migration")
        val denied = unauthorized.migrate(null, 0)(using fixture.ordinary)
        Then("Operator admission returns the same constant refusal without touching native storage")
        _fixed(denied)
        Vector(0, -1, 1025, Int.MaxValue).foreach { limit =>
          Given("a valid cached prefix accompanies an invalid candidate bound")
          val valid = Vector(first)
          When(s"the Operator supplies bound $limit")
          val result = fixture.migrate(valid, limit)
          Then("the bound fails before native effects")
          _fixed(result)
        }
        Given("authorized construction has null or invalid policies and lower-only byte budgets")
        val invalid = Vector(new DurableJobMigrationCheckpoint(null, fixture.payloads, fixture.original.lifetimePolicy, fixture.engine._resource_budget.policy),
          new DurableJobMigrationCheckpoint(fixture.store, null, fixture.original.lifetimePolicy, fixture.engine._resource_budget.policy),
          new DurableJobMigrationCheckpoint(fixture.store, fixture.payloads, null, fixture.engine._resource_budget.policy),
          new DurableJobMigrationCheckpoint(fixture.store, fixture.payloads, fixture.original.lifetimePolicy, null),
          new DurableJobMigrationCheckpoint(fixture.store, fixture.payloads, fixture.original.lifetimePolicy.copy(maxledgerjobs = 0), fixture.engine._resource_budget.policy),
          new DurableJobMigrationCheckpoint(fixture.store, fixture.payloads, fixture.original.lifetimePolicy, fixture.engine._resource_budget.policy.copy(maxretryattempts = 0)),
          new DurableJobMigrationCheckpoint(fixture.store, fixture.payloads, fixture.original.lifetimePolicy, fixture.engine._resource_budget.policy, 0L),
          new DurableJobMigrationCheckpoint(fixture.store, fixture.payloads, fixture.original.lifetimePolicy, fixture.engine._resource_budget.policy, 16777217L))
        invalid.foreach { helper =>
          When("the invalid internal constructor is used with the valid cached prefix")
          val result = helper.migrate(Vector(first), 1)(using fixture.context)
          Then("construction policy refusal remains constant")
          _fixed(result)
        }
        Then("the whole admission surface leaves rows revisions paid authority Blob and business counters unchanged")
        fixture.entity.loads.get() shouldBe loads
        fixture.state(root) shouldBe before; fixture.effects shouldBe effects
        fixture.source.calls.get() shouldBe 1; fixture.blob.gets.get() shouldBe 0
      }
    }
  }
  "MC2 Genuine native checkpoint" must _scenario("MC2", "M1,M5,M6,M7,M8,M9,M10,M12") {
    "advance only signed format and contiguous semantic and provider revisions with all paid ownership retained" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M1,M5..M10,M12 MC2: a genuinely Published root, associated normal TaskId, complete Own/PerTask descriptor and owned bytes")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val terminal = fixture.terminal(fixture.publish("checkpoint", Vector(Array[Byte](3, 5, 7))))
        val body = terminal.snapshot.record.body
        val rich = body.copy(retention = body.retention.copy(retainUntil = Some(Instant.MAX)),
          timeline = Vector(DurableTimelineEvent(1L, instant.plusSeconds(20), "task.completed", Some(body.tasks.head.taskId), Some("closed"))),
          diagnostics = Vector(DurableDiagnosticSummary("execution", "closed", "info", "closed")),
          calltreeReference = Some(terminal.references.head), result = DurableResultOutcome.Succeeded(DurableValue.External(terminal.references.head)))
        val root = fixture.legacy(terminal.copy(snapshot = fixture.checkpoint(terminal.snapshot, rich)))
        val before = fixture.state(root); val effects = fixture.effects
        val original = root.snapshot
        val paid = fixture.row(lifetimeId); val blobs = fixture.row(blobQuotaId)
        When("explicit authorized migration checkpoints the admitted terminal V1")
        val result = fixture.migrate(Vector(candidate(root.jobId)), 1)
        Then("the native canonical destination is V2 with exactly one semantic and provider revision increment")
        success(result)
        val after = fixture.signed(root.jobId)
        after.record.format shouldBe DurableRecordFormat.V2
        after.record.body shouldBe original.record.body.copy(identity = original.record.body.identity.copy(revision = original.record.body.identity.revision + 1L))
        after.providerrevision.value shouldBe original.providerrevision.value + 1L
        fixture.row(fixture.jobEntityId(root.jobId)).get.getString("canonicalJson").get shouldBe success(DurableJobRecordCodec.canonicalJson(after.record))
        fixture.row(lifetimeId) shouldBe paid; fixture.row(blobQuotaId) shouldBe blobs
        fixture.state(root).metadata shouldBe before.metadata; fixture.state(root).payloads shouldBe before.payloads
        DurableJobOwnedBlobPayloads.ownerFingerprint(after.record) shouldBe DurableJobOwnedBlobPayloads.ownerFingerprint(original.record)
        val storage = success(DurableJobStorageQuotaCodec.decode(fixture.row(storageId).get.getString("canonicalJson").get)).entries.find(_.jobid == root.jobId).get
        storage.committedrevision shouldBe Some(after.providerrevision.value); storage.pending shouldBe Vector.empty
        storage.committedbytes shouldBe success(DurableJobRecordCodec.canonicalBytes(after.record)).length.toLong
        fixture.entity.jobwrites.get() shouldBe effects.jobwrites + 1
        fixture.entity.lifewrites.get() shouldBe effects.lifewrites
        _business_unchanged(fixture.effects, effects)
        fixture.blob.gets.get() shouldBe 1; fixture.blob.opens.get() shouldBe 1; fixture.blob.closes.get() shouldBe 1
      }
    }
  }
  "MC3 Existing V2 and ordered mixed batches" must _scenario("MC3", "M4,M6,M9,M10") {
    "validate paid V2 without a write and reject bad native tails before any prefix checkpoint" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M4,M6,M9,M10 MC3: an ordered genuine V1 and already V2 batch")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val legacy = fixture.legacy(fixture.publish("mixed_legacy"))
        val current = fixture.terminal(fixture.publish("mixed_current"))
        val existing = fixture.row(fixture.jobEntityId(current.jobId)); val writes = fixture.entity.jobwrites.get()
        val batch = Vector(candidate(current.jobId), candidate(legacy.jobId, 1L))
        When("the mixed vector is migrated and a fresh helper then observes the actual destinations")
        val first = fixture.migrate(batch, 2); val before = fixture.state(legacy, current)
        val effects = fixture.effects; val repeat = fixture.migrate(batch, 2)
        Then("only the V1 checkpoints once and repeat validates exact V2 paid storage without re-signing")
        success(first); success(repeat)
        fixture.entity.jobwrites.get() shouldBe writes + 1
        fixture.row(fixture.jobEntityId(current.jobId)) shouldBe existing
        fixture.state(legacy, current) shouldBe before; fixture.effects shouldBe effects
      }
      Vector("corrupt", "missing", "foreign").foreach { mode =>
        Given("a valid native V1 prefix precedes a structurally valid but invalid native suffix")
        DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
          val prefix = fixture.legacy(fixture.publish(s"prefix_$mode"))
          val tail = fixture.legacy(fixture.publish(s"tail_$mode"))
          if (mode == "corrupt") fixture.replaceText(fixture.jobEntityId(tail.jobId), "PRIVATE corrupt record")
          if (mode == "missing") fixture.removeRow(fixture.jobEntityId(tail.jobId))
          val batch = Vector(candidate(prefix.jobId), candidate(tail.jobId, 1L,
            if (mode == "foreign") access.copy(subjectId = "foreign") else access))
          val before = fixture.state(prefix, tail); val effects = fixture.effects
          When(s"complete preflight encounters the $mode suffix")
          val result = fixture.migrate(batch, 2)
          Then("no valid prefix write occurs and exact native damage or absence is retained")
          _fixed(result); fixture.state(prefix, tail) shouldBe before; fixture.effects shouldBe effects
        }
      }
    }
  }
  "MC4 Pre-existing paid ownership" must _scenario("MC4", "M6,M7,M12") {
    "refuse absent Pending malformed mismatched or insufficient lifetime payment without reconstruction" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M6,M7,M12 MC4: a genuine managed signed V1 created without any lifetime ledger")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val root = fixture.unpaid("unpaid"); val before = fixture.state(root); val effects = fixture.effects
        When("explicit migration attempts the unpaid legacy row")
        val result = fixture.migrate(Vector(candidate(root.jobId)), 1)
        Then("absence stays absent and no paid root or binding is fabricated")
        _fixed(result); fixture.row(lifetimeId) shouldBe None
        fixture.state(root) shouldBe before; fixture.effects shouldBe effects
      }
      Vector("missing", "pending", "malformed", "policy", "insufficient").foreach { mode =>
        Given("a legitimate paid root was checkpointed to V1 before deliberate negative ledger corruption")
        DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
          val root = fixture.legacy(fixture.publish(s"paid_$mode"))
          mode match {
            case "missing" => fixture.removeRow(lifetimeId)
            case "pending" => fixture.mutateLifetime(current => current.copy(entries = current.entries.map(_.copy(rootstate = DurableJobLifetimeQuotaRootState.Pending, normalids = Vector.empty, claims = Vector.empty, cancelclaimed = false))))
            case "malformed" => fixture.replaceText(lifetimeId, "PRIVATE malformed paid authority")
            case "policy" => fixture.mutateLifetime(current => current.copy(policy = current.policy.copy(maxledgerjobs = 2)))
            case _ =>
              val text = fixture.row(lifetimeId).get.getString("canonicalJson").get
              fixture.replaceText(lifetimeId, text.replace("\"chargedTasks\":2", "\"chargedTasks\":0"))
          }
          val before = fixture.state(root); val effects = fixture.effects
          When(s"migration preflight requests the $mode native lifetime proof")
          val result = fixture.migrate(Vector(candidate(root.jobId)), 1)
          Then("fixed refusal preserves exact damaged payment and every Job revision without reserve publish reset or refund")
          _fixed(result); fixture.state(root) shouldBe before; fixture.effects shouldBe effects
          fixture.blob.gets.get() shouldBe 0
        }
      }
    }
  }
  "MC5 Closed legacy and terminal compatibility" must _scenario("MC5", "M4,M5,M6") {
    "refuse exhausted inactive unprojectable raw V0 and unknown stored records before Job mutation" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M4,M5,M6 MC5: genuine V1 sources and intentional native compatibility faults")
      Vector("exhausted", "expired", "deleted", "tombstoned", "task-id", "pending-task", "evidence", "raw-v0", "unknown").foreach { mode =>
        DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
          val initial = fixture.legacy(fixture.publish(s"closed_${mode.replace('-', '_')}"))
          val body = initial.snapshot.record.body
          val changed = mode match {
            case "expired" => body.copy(retention = body.retention.copy(expiresAt = Some(instant.plusSeconds(30)), deletionState = DurableDeletionState.Expired))
            case "deleted" => body.copy(retention = body.retention.copy(deletedAt = Some(instant.plusSeconds(30)), deletionState = DurableDeletionState.Deleted))
            case "tombstoned" => body.copy(retention = body.retention.copy(deletedAt = Some(instant.plusSeconds(30)),
              deletionState = DurableDeletionState.Tombstoned, tombstone = Some(DurableTombstone(instant.plusSeconds(30), "closed", None))))
            case "task-id" => body.copy(tasks = body.tasks.map(_.copy(taskId = "not-a-native-task-id")))
            case "pending-task" => body.copy(tasks = body.tasks.map(task => task.copy(transaction = task.transaction.copy(outcome = DurableTransactionOutcome.Pending))))
            case _ => body
          }
          val root = if (changed == body) initial else initial.copy(snapshot = fixture.checkpoint(initial.snapshot, changed, legacy = true))
          val canonical = success(DurableJobRecordCodec.canonicalJson(root.snapshot.record))
          mode match {
            case "exhausted" => fixture.replaceText(fixture.jobEntityId(root.jobId), success(DurableJobRecordCodec.canonicalJson(
              success(DurableJobRecord.create(body.copy(identity = body.identity.copy(revision = Long.MaxValue)))))))
            case "raw-v0" => fixture.replaceText(fixture.jobEntityId(root.jobId), success(DurableJobRecordCodec.canonicalV0Json(body)))
            case "unknown" => fixture.replaceText(fixture.jobEntityId(root.jobId), canonical.replace("\"version\":1", "\"version\":99"))
            case _ => ()
          }
          val batch = Vector(if (mode == "evidence") candidate(root.jobId).copy(replayEvidence = replay.copy(provider = null)) else candidate(root.jobId))
          val before = fixture.state(root); val effects = fixture.effects
          When(s"explicit migration encounters the $mode native source")
          val result = fixture.migrate(batch, 1)
          Then("fixed refusal leaves the signed or intentionally corrupt native row and paid authority unchanged")
          _fixed(result); fixture.state(root) shouldBe before; fixture.effects shouldBe effects
        }
      }
      Given("an admitted V1 value and impossible nonterminal V1 body are supplied to the unchanged value codec")
      val submitted = JobDurableLifetimeRecoveryFixtures.record(JobInputBoundsFixtures.id("codec"))
      val body = submitted.body.copy(tasks = submitted.body.tasks.zipWithIndex.map { case (descriptor, index) => descriptor.copy(
          taskId = TaskId("cncf", "migration", Some(instant), Some(s"normal$index")).value,
          transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
            DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed))
        },
        lifecycle = submitted.body.lifecycle.copy(status = DurableJobLifecycleStatus.Succeeded), result = DurableResultOutcome.Succeeded(DurableValue.Absent))
      val legacy = success(DurableJobRecord.create(body))
      When("value-only migration and nonterminal V1 signing are requested")
      val migrated = success(DurableJobRecord.migrateV1ToV2(legacy)); val refused = DurableJobRecord.create(submitted.body)
      Then("value migration retains the exact body while the closed V1 schema refuses pending work")
      migrated.body shouldBe body; migrated.format shouldBe DurableRecordFormat.V2; refused.isSuccess shouldBe false
    }
  }
  "MC6 Native race during physical preflight" must _scenario("MC6", "M8,M9,M11") {
    "retain a competing acknowledged checkpoint and refuse the final exact source mismatch" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M8,M9,M11 MC6: a genuine paid V1 payload held by an actual bounded stream barrier")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val root = fixture.legacy(fixture.publish("race", Vector(Array[Byte](1, 2))))
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        fixture.blob.streamBefore = () => { entered.countDown(); await(release) }
        val paid = fixture.row(lifetimeId); val blobs = fixture.row(blobQuotaId)
        When("migration begins physical preflight before an independent native writer wins")
        val running = actor(fixture.migrate(Vector(candidate(root.jobId)), 1))
        var winner = Option.empty[DurableJobStoreSnapshot]; var before = Option.empty[NativeState]; var effects = Option.empty[Effects]
        try {
          await(entered)
          When("a competing original optimistic checkpoint advances the native source during physical verification")
          winner = Some(fixture.checkpoint(root.snapshot, root.snapshot.record.body.copy(identity = root.snapshot.record.body.identity.copy(updatedAt = instant.plusSeconds(21))), legacy = true))
          before = Some(fixture.state(root)); effects = Some(fixture.effects)
        } finally { release.countDown(); join(running._1) }
        Then("the final source mismatch refuses with no migration checkpoint and no paid or Blob alteration")
        _fixed(running._2.get().toOption.get)
        fixture.signed(root.jobId) shouldBe winner.get; fixture.state(root) shouldBe before.get; fixture.effects shouldBe effects.get
        fixture.row(lifetimeId) shouldBe paid; fixture.row(blobQuotaId) shouldBe blobs
        fixture.blob.opens.get() shouldBe 1; fixture.blob.closes.get() shouldBe 1
      }
    }
  }
  "MC7 Native commit before lost acknowledgement" must _scenario("MC7", "M9,M10,M11") {
    "preserve the genuine committed destination and conservative reservation without retry or claimed success" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M9,M10,M11 MC7: a genuine V1 source and a native update adapter that loses only the acknowledgement")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val root = fixture.legacy(fixture.publish("lost_ack")); val paid = fixture.row(lifetimeId)
        val effects = fixture.effects; fixture.entity.loseJobAck = true
        When("the original Job update commits V2 and its real provider wrapper then throws")
        val result = fixture.migrate(Vector(candidate(root.jobId)), 1)
        Then("the operation refuses and retains actual V2 plus the unresolved conservative storage reservation")
        _fixed(result)
        val destination = fixture.signed(root.jobId)
        destination.record.format shouldBe DurableRecordFormat.V2
        destination.record.body shouldBe root.snapshot.record.body.copy(identity = root.snapshot.record.body.identity.copy(revision = root.snapshot.record.body.identity.revision + 1L))
        destination.providerrevision.value shouldBe root.snapshot.providerrevision.value + 1L
        fixture.entity.jobwrites.get() shouldBe effects.jobwrites + 1
        success(fixture.use(fixture.store.resourceSnapshot()(using fixture.context))).pendingreservations shouldBe 1
        fixture.row(lifetimeId) shouldBe paid; _business_unchanged(fixture.effects, effects)
        Given("a fresh helper sees the genuinely committed V2 and unchanged paid ledger")
        val before = fixture.state(root); val aftereffects = fixture.effects
        When("another explicitly enabled migration reads actual storage")
        val fresh = fixture.migrate(Vector(candidate(root.jobId)), 1)
        Then("original paid guards validate that actual V2 without a second Job checkpoint or rollback")
        success(fresh); fixture.state(root) shouldBe before; fixture.effects shouldBe aftereffects
      }
    }
  }
  "MC8 Failed suffix after acknowledged prefix" must _scenario("MC8", "M4,M9,M10") {
    "retain only genuine native acknowledgements and winning suffix state without whole-batch success" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M4,M9,M10 MC8: two preflight-valid V1 roots and a suffix race after prefix acknowledgement")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val first = fixture.legacy(fixture.publish("partial_first")); val last = fixture.legacy(fixture.publish("partial_last"))
        val paid = fixture.row(lifetimeId); val blobs = fixture.row(blobQuotaId); val effects = fixture.effects
        var winner = Option.empty[DurableJobStoreSnapshot]
        fixture.entity.afterJobCommit = () => {
          fixture.entity.afterJobCommit = () => ()
          winner = Some(fixture.checkpoint(last.snapshot, last.snapshot.record.body.copy(identity = last.snapshot.record.body.identity.copy(updatedAt = instant.plusSeconds(21))), legacy = true))
        }
        When("complete preflight succeeds and the first checkpoint is acknowledged before suffix application")
        val result = fixture.migrate(Vector(candidate(first.jobId), candidate(last.jobId, 1L)), 2)
        Then("fixed refusal retains migrated prefix and exact competing suffix with no refund compensation or business execution")
        _fixed(result)
        fixture.signed(first.jobId).record.format shouldBe DurableRecordFormat.V2
        fixture.signed(last.jobId) shouldBe winner.get
        fixture.signed(last.jobId).record.format shouldBe DurableRecordFormat.V1
        fixture.entity.jobwrites.get() shouldBe effects.jobwrites + 2
        fixture.row(lifetimeId) shouldBe paid; fixture.row(blobQuotaId) shouldBe blobs
        _business_unchanged(fixture.effects, effects)
      }
      Given("a malformed native suffix accompanies a new valid V1 prefix before any application")
      DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
        val first = fixture.legacy(fixture.publish("invalid_first")); val last = fixture.legacy(fixture.publish("invalid_last"))
        fixture.replaceText(fixture.jobEntityId(last.jobId), "PRIVATE invalid tail")
        val before = fixture.state(first, last); val effects = fixture.effects
        When("full preflight encounters that invalid tail")
        val result = fixture.migrate(Vector(candidate(first.jobId), candidate(last.jobId, 1L)), 2)
        Then("full preflight refusal makes zero prefix mutation")
        _fixed(result); fixture.state(first, last) shouldBe before; fixture.effects shouldBe effects
      }
    }
  }
  "MC9 Original physical and Blob proof" must _scenario("MC9", "M8,M11") {
    "refuse metadata ledger and physical faults before migration and close every opened real stream" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M8,M11 MC9: actual owned files metadata Published Blob rows and fault wrappers over real streams")
      val modes = Vector("shared", "definition", "metadata", "pending", "missing", "released", "malformed",
        "tamper", "truncate", "overrun", "zero", "stream-throw", "stream-interrupt", "provider-throw", "provider-interrupt", "close-throw")
      modes.foreach { mode =>
        DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
          val initial = fixture.legacy(fixture.publish(s"physical_${mode.replace('-', '_')}", Vector(Array[Byte](1, 2, 3))))
          val ref = initial.references.head
          val body = initial.snapshot.record.body
          val changed = mode match {
            case "shared" => body.copy(inputs = body.inputs :+ DurableInputReference("shared", DurableValue.External(ref.copy(storageKind = "shared-blob"))))
            case "definition" => body.copy(definitionSnapshot = body.definitionSnapshot.copy(declaredSource = Some(ref)))
            case _ => body
          }
          val root = if (changed == body) initial else initial.copy(snapshot = fixture.checkpoint(initial.snapshot, changed, legacy = true))
          mode match {
            case "metadata" => fixture.replaceMetadata(ref, blob => blob.copy(attributes = blob.attributes.updated(DurableJobOwnedBlobPayloads.bindingAttribute, "0" * 64)))
            case "pending" => fixture.mutateBlobQuota(current => current.copy(entries = current.entries.map(_.copy(state = DurableJobOwnedBlobQuotaState.Pending, bindingsha256 = None))))
            case "released" => fixture.mutateBlobQuota(current => current.copy(entries = current.entries.map(_.copy(state = DurableJobOwnedBlobQuotaState.Released))))
            case "missing" => fixture.removeRow(blobQuotaId)
            case "malformed" => fixture.replaceText(blobQuotaId, "PRIVATE malformed Blob payment")
            case "tamper" => Files.write(fixture.physicalPath(ref), Array[Byte](9, 2, 3))
            case "truncate" => Files.write(fixture.physicalPath(ref), Array[Byte](1, 2))
            case "overrun" => Files.write(fixture.physicalPath(ref), Array[Byte](1, 2, 3, 4, 5))
            case _ => fixture.blob.mode = mode
          }
          val before = fixture.state(root); val effects = fixture.effects
          When(s"full migration preflight consumes the $mode physical proof")
          val result = fixture.migrate(Vector(candidate(root.jobId)), 1)
          Then("only zero-read fallback verifies; every refused proof retains native authority and closes all opened streams")
          if (mode == "zero") {
            success(result); fixture.entity.jobwrites.get() shouldBe effects.jobwrites + 1
            fixture.state(root).metadata shouldBe before.metadata; fixture.state(root).payloads shouldBe before.payloads
            fixture.row(lifetimeId) shouldBe before.rows(before.rows.size - 2)
            _business_unchanged(fixture.effects, effects)
          } else { _fixed(result); fixture.state(root) shouldBe before; fixture.effects shouldBe effects }
          fixture.blob.closes.get() shouldBe fixture.blob.opens.get()
          if (mode.endsWith("interrupt")) {
            Thread.currentThread().isInterrupted shouldBe true
            Thread.interrupted() shouldBe true
          }
          if (mode == "overrun") fixture.blob.bytes.get() shouldBe 4L
        }
      }
    }
  }
  "MC10 Seeded whole-operation physical bounds" must _scenario("MC10", "M4,M8,M13") {
    "shrink at least 24 real managed exact-fit one-under duplicate-reference and source-order cases" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M4,M8,M13 MC10: finite real native cases with duplicate references within each Job and distinct owned bytes across Jobs")
      val generator = for { size <- Gen.choose(0, 12); exact <- Gen.oneOf(true, false); reverse <- Gen.oneOf(true, false) } yield (size, exact, reverse)
      val property = Prop.forAll(generator) { case (size, exact, reverse) =>
        DurableJobMigrationCheckpointFixtures.withFixture { fixture =>
          val roots = Vector("budget_first", "budget_last").map(label => {
            val root = fixture.legacy(fixture.publish(label, Vector(Array.fill[Byte](size)(7))))
            root.copy(snapshot = fixture.checkpoint(root.snapshot, root.snapshot.record.body.copy(
              inputs = Vector.tabulate(3)(index => DurableInputReference(s"duplicate-$index", DurableValue.External(root.references.head)))), legacy = true))
          })
          val selected = if (reverse) roots.reverse else roots
          val batch = selected.zipWithIndex.map { case (root, index) => candidate(root.jobId, index.toLong) }
          val before = fixture.state(roots*); val effects = fixture.effects
          val cost = 2L * (size.toLong + 1L)
          When("the entire cached native batch is migrated with exact whole-target capacity or one byte less including each sentinel")
          val result = fixture.migrate(batch, 2, if (exact) cost else cost - 1L)
          Then("refusal preserves native rows and payment; exact-fit verifies each Job's owned bytes once despite repeated references")
          if (exact) {
            success(result); fixture.entity.jobwrites.get() shouldBe effects.jobwrites + 2
            roots.foreach(root => fixture.signed(root.jobId).record.body shouldBe root.snapshot.record.body.copy(
              identity = root.snapshot.record.body.identity.copy(revision = root.snapshot.record.body.identity.revision + 1L)))
            fixture.blob.gets.get() shouldBe 2; fixture.blob.bytes.get() shouldBe 2L * size
            fixture.row(lifetimeId) shouldBe before.rows(before.rows.size - 2)
            fixture.row(blobQuotaId) shouldBe before.rows.last
            fixture.state(roots*).metadata shouldBe before.metadata; fixture.state(roots*).payloads shouldBe before.payloads
            _business_unchanged(fixture.effects, effects)
          } else { _fixed(result); fixture.state(roots*) shouldBe before; fixture.effects shouldBe effects }
          fixture.blob.closes.get() shouldBe fixture.blob.opens.get()
          Given("the same native states accompany byte budgets outside the original lower-only 16 MiB ceiling")
          val stable = fixture.state(roots*); val stableeffects = fixture.effects
          When("zero and above-ceiling private byte budgets are requested")
          val zero = fixture.migrate(batch, 2, 0L); val excess = fixture.migrate(batch, 2, 16777217L)
          Then("both bounds refuse without any native capacity paid or Job effect")
          _fixed(zero); _fixed(excess); fixture.state(roots*) shouldBe stable; fixture.effects shouldBe stableeffects
          true
        }
      }
      When("the ordinary shrinking ScalaCheck property runs with seed 69709704 and minimum 24 successes")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709704L)), property)
      Then("at least 24 native cases satisfy the frozen physical and payment boundary")
      withClue(s"MC10 ScalaCheck status: ${result.status}: ") {
        result.passed shouldBe true; result.succeeded should be >= 24
      }
    }
  }

  private def _fixed(result: Consequence[?]): Unit =
    failure(result).observation.getEffectiveMessage shouldBe Some("durable job migration checkpoint refused")

  private def _business_unchanged(after: Effects, before: Effects): Unit = {
    after.creates shouldBe before.creates; after.deletes shouldBe before.deletes
    after.lifewrites shouldBe before.lifewrites; after.blobputs shouldBe before.blobputs; after.blobdeletes shouldBe before.blobdeletes
    after.ports shouldBe before.ports; after.ids shouldBe before.ids
    // Original managed updates obtain one timestamp per counted native update.
    after.clocks shouldBe before.clocks + (after.writes - before.writes); after.timers shouldBe before.timers
    after.resources shouldBe before.resources; after.queue shouldBe before.queue
    after.getters shouldBe before.getters; after.bodies shouldBe before.bodies; after.outcomes shouldBe before.outcomes
    after.compensations shouldBe before.compensations
  }
}
