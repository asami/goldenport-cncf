package org.goldenport.cncf.job

import java.nio.file.Files
import java.time.{Duration, Instant}
import java.util.concurrent.CountDownLatch
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.blob.BlobRepository.given
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobDurableRuntimeFixtures.*

/*
 * @since   Oct.  3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobDurableRuntimeOwnerIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-runtime-owner, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1B")

  "DI1 Configured native composition" must _scenario("DI1", "R1,R6,R9,R11,R17") {
    "execute one genuine paid body on the original engine with exact keyed acknowledgements" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI1: one real managed SQLite, persistent Blob backend, and original Subsystem engine")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        val original = fixture.subsystem.jobEngine
        success(fixture.start())
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        When("the original engine admits and drains a persistent task")
        val id = fixture.submit(task)
        fixture.engine.drainAll() shouldBe 1
        Then("all six native keyed boundaries acknowledge revisions 1 through 6 and one body/canonical result")
        fixture.subsystem.jobEngine should be theSameInstanceAs original
        fixture.evidence.requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
        fixture.evidence.requests.map(_.expectedrevision) shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
        fixture.evidence.requests.map(_.jobid).distinct shouldBe Vector(id)
        fixture.signed(id).record.body.identity.revision shouldBe 6L
        fixture.native(id).seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
        fixture.native(id).seed.remaining shouldBe 0L
        task.runs.get() shouldBe 1; task.canonical.get() shouldBe 1
        fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
        fixture.owner.health shouldBe JobDurableOperationalHealth(JobDurableOperationalState.Ready, true, true, true, true, true, true)
        fixture.node.activeLeaseCount shouldBe 0
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
      }
    }
  }
  "DI2 Genuine physical reopen" must _scenario("DI2", "R6,R7,R17") {
    "continue the original Submitted payment and semantic revision through managed ownership" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI2: a Published pending root and closed original physical SQLite owner")
      val path = root()
      val id = JobInputBoundsFixtures.id("configured_reopen")
      val record = JobDurableLifetimeRecoveryFixtures.record(id)
      preseed(path, Vector(record))
      val fixture = new Fixture(path)
      try {
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        fixture.tasks.put(id, List(task))
        fixture.startup.entries = Vector(candidate(id))
        val source = new DurableJobKeyedEvidenceFixtures.Source(_ => DurableJobKeyedEvidenceFixtures.originalRecipe(record))
        fixture.selectedEvidence = source
        When("the new managed binding authenticates the original candidate then advances the original manual timer")
        success(fixture.start())
        val before = fixture.native(id)
        fixture.startup.calls.get() shouldBe 1; fixture.port.calls.get() shouldBe 1
        fixture.signed(id).record shouldBe record
        source.requests shouldBe empty
        fixture.engine.drainAll() shouldBe 0
        fixture.advance(Duration.ofSeconds(10))
        fixture.engine.drainAll()
        Then("next-revision native checkpoints retain prepaid authority and execute exactly once without Admission reconstruction")
        source.requests.map(_.expectedrevision) shouldBe Vector(2L, 3L, 4L, 5L, 6L)
        source.requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries.tail
        fixture.signed(id).record.body.identity.revision shouldBe 6L
        fixture.native(id).seed.paid shouldBe before.seed.paid
        fixture.native(id).seed.remaining shouldBe 0L
        task.runs.get() shouldBe 1; task.canonical.get() shouldBe 1
        fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
        val terminal = fixture.signed(id).record
        success(fixture.subsystem.shutdownC())
        fixture.node.state shouldBe SystemNode.State.Running
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
        success(fixture.node.shutdownC())
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe false
        val reopened = new Fixture(path)
        try {
          reopened.startup.entries = Vector(candidate(id))
          reopened.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Source(_ => DurableJobKeyedEvidenceFixtures.originalRecipe(terminal))
          success(reopened.start())
          reopened.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Registered)
          reopened.port.calls.get() shouldBe 0
          reopened.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
          reopened.engine.getResult(id) shouldBe None
          reopened.native(id).seed.paid shouldBe before.seed.paid
        } finally reopened.close()
      } finally { fixture.close(); remove(path) }
    }
  }
  "DI3 Whole-vector refusal after a genuine prefix" must _scenario("DI3", "R5,R7,R8") {
    "retain paid prefix authority while blocking manual concurrent and automatic work before suffix refusal" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI3: two genuinely Published roots, one eligible prefix and an unauthorized or refused-port suffix")
      Vector("access", "port", "foreign-port").foreach { mode =>
        val path = root()
        val ids = Vector(JobInputBoundsFixtures.id(s"prefix_${mode.replace('-', '_')}"), JobInputBoundsFixtures.id(s"suffix_${mode.replace('-', '_')}"))
        val records = ids.map(JobDurableLifetimeRecoveryFixtures.record(_))
        preseed(path, records)
        val fixture = new Fixture(path, manual = mode == "access")
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val tasks = ids.map(_ => new JobDurableLifetimeRecoveryFixtures.Task)
        try {
          ids.zip(tasks).foreach { case (id, task) => fixture.tasks.put(id, List(task)) }
          fixture.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Source(id => DurableJobKeyedEvidenceFixtures.originalRecipe(records(ids.indexOf(id))))
          fixture.startup.entries = ids.zipWithIndex.map { case (id, index) =>
            val value = candidate(id, index.toLong)
            if (mode == "access" && index == 1) value.copy(access = access.copy(subjectId = "PRIVATE foreign")) else value
          }
          fixture.selectedPort = new DurableJobRuntimeRehydrationPort {
            def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
              decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] =
              if (candidate.ordinal == 1L && mode == "port") Consequence.stateInvalid("PRIVATE port refusal")
              else fixture.port.rehydrate(candidate, snapshot, decision).map(value =>
                if (candidate.ordinal == 1L && mode == "foreign-port") value.copy(jobId = ids.head) else value)
          }
          fixture.startup.before = () => { entered.countDown(); await(release) }
          val running = actor(fixture.start())
          try {
            await(entered)
            When("discovery is still incomplete and ingress plus manual drain overlap startup")
            val before = (fixture.ids.calls.get(), fixture.engine.resourceSnapshot)
            fixture.engine.submit(List(tasks.head), fixture.context).isSuccess shouldBe false
            fixture.engine.drainAll() shouldBe 0
            (fixture.ids.calls.get(), fixture.engine.resourceSnapshot) shouldBe before
          } finally { release.countDown(); join(running._1) }
          Then("the fixed startup refusal closes execution without rolling back or refunding the authenticated paid prefix")
          val result = running._2.get().toOption.get
          result.isSuccess shouldBe false
          JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage shouldBe Some("durable runtime startup refused")
          fixture.engine.drainAll() shouldBe 0
          tasks.map(_.runs.get()) shouldBe Vector(0, 0)
          fixture.engine.runtimeState._native_lifetime_bindings.contains(ids.head) shouldBe true
          success(fixture.subsystem._with_managed_datastore_lease_c { _ =>
            val store = new DurableJobStore(fixture.entity)
            store.lifetimeQuota(fixture.lifetimePolicy).snapshot(ids.head.value, access)(using fixture.context).map { native =>
              native.seed shouldBe JobLifetimeBudget.Seed(JobLifetimeSnapshot(2L, 96L, 1L, 1L), 1L, Set.empty, Set.empty)
            }
          })
          fixture.subsystem._initialize_durable_job_runtime_c().isSuccess shouldBe false
          fixture.builds.get() shouldBe 1
        } finally { release.countDown(); fixture.close(); remove(path) }
      }
    }
  }
  "DI4 Native startup integrity refusal" must _scenario("DI4", "R6,R7,R8") {
    "refuse missing corrupt unavailable malformed and persisted-policy evidence without body execution or reflection" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI4: actual managed inputs with independent discovery, Blob, persisted-policy, and native record faults")
      Vector("missing", "source", "ordinal", "duplicate", "null-vector", "blob", "policy", "corrupt").foreach { mode =>
        val path = root(); val id = JobInputBoundsFixtures.id(s"integrity_${mode.replace('-', '_')}")
        val record = JobDurableLifetimeRecoveryFixtures.record(id)
        if (mode != "missing") {
          val database = new JobDurableLifetimeRecoveryFixtures.Database(path)
          val original = database.open()
          try {
            JobDurableLifetimeRecoveryFixtures.publish(original.store, record)(using original.context)
            if (mode == "corrupt") {
              val entityid = success(DurableJobStoreEntity.entityId(id.value))
              val entry = DataStore.EntryId(entityid)
              val row = success(original.provider.load(DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection), entry)(using original.context)).get
              success(original.provider.save(DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection), entry,
                row.upsertSingle("canonicalJson", "PRIVATE invalid durable body"))(using original.context))
            }
          } finally database.closeBeforeReopen(original)
        }
        val fixture = new Fixture(path); val task = new JobDurableLifetimeRecoveryFixtures.Task
        try {
          fixture.tasks.put(id, List(task)); fixture.startup.entries = Vector(candidate(id))
          mode match {
            case "source" => fixture.startup.fault = Some(Consequence.stateInvalid("PRIVATE source error"))
            case "ordinal" => fixture.startup.entries = Vector(candidate(id, 2L))
            case "duplicate" => fixture.startup.entries = Vector(candidate(id), candidate(id, 1L))
            case "null-vector" => fixture.startup.entries = null
            case "blob" => fixture.selectedBlob = new InMemoryBlobStore()
            case "policy" => fixture.lifetimePolicy = fixture.lifetimePolicy.copy(maxledgerjobs = 4095)
            case _ => ()
          }
          When(s"the configured native owner starts with the $mode fault")
          val result = fixture.start()
          Then("failure is fixed and redacted, no canonical body executes, and original recovery guards remain in force")
          result.isSuccess shouldBe false
          JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage shouldBe Some("durable runtime startup refused")
          task.runs.get() shouldBe 0; task.canonical.get() shouldBe 0
          fixture.engine.drainAll() shouldBe 0
          fixture.engine.durableOperationalHealth.get.executionAvailable shouldBe false
          fixture.engine.runtimeState._native_lifetime_bindings.contains(id) shouldBe false
        } finally { fixture.close(); remove(path) }
      }
    }
  }
  "DI5 Registered native retention and cleanup" must _scenario("DI5", "R12,R15,R17") {
    "expire genuine terminal TTL payloads while preserving held and shared authority until acknowledged cleanup" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI5: genuine terminal paid jobs, native owned Blob metadata, one hold and one shared reference")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        val supplied = DurableJobKeyedEvidenceFixtures.recipe()
        fixture.selectedEvidence = new DurableJobKeyedEvidenceFixtures.Source(_ => supplied.copy(projection = supplied.projection.copy(
          retention = supplied.projection.retention.copy(expiresAt = Some(instant.plusSeconds(1))))))
        success(fixture.start())
        given ExecutionContext = fixture.context
        val ids = Vector.fill(4)(fixture.submit(new JobDurableLifetimeRecoveryFixtures.Task))
        fixture.engine.drainAll() shouldBe 4
        val owned = success(fixture.owner._execute_c {
          _sequence_c(ids.take(3)) { id =>
            fixture.owner._store.load(id.value, access).flatMap(_.get match {
              case snapshot => fixture.owner._payloads.putObserved(snapshot, access, Some("native.bin"),
                ContentType.APPLICATION_OCTET_STREAM, Bag.binary(Array[Byte](1, 2, 3))).flatMap { reference =>
                _checkpoint(fixture, snapshot, snapshot.record.body.copy(inputs = Vector(DurableInputReference("payload", DurableValue.External(reference))))).map(_ => reference)
              }
            })
          }
        })
        success(fixture.owner._execute_c {
          for {
            held <- fixture.owner._store.load(ids(1).value, access)
            _ <- _checkpoint(fixture, held.get, held.get.record.body.copy(retention = held.get.record.body.retention.copy(retainUntil = Some(Instant.MAX))))
            shared <- fixture.owner._store.load(ids(2).value, access)
            _ <- _checkpoint(fixture, shared.get, shared.get.record.body.copy(inputs = shared.get.record.body.inputs :+
              DurableInputReference("shared", DurableValue.External(owned(2).copy(storageKind = "shared")))))
            kept <- fixture.owner._store.load(ids(3).value, access)
            _ <- _checkpoint(fixture, kept.get, kept.get.record.body.copy(retention = kept.get.record.body.retention.copy(expiresAt = None)))
          } yield ()
        })
        val before = ids.map(fixture.native)
        val blobs = success(fixture.owner._execute_c { _sequence_c(owned)(reference =>
          fixture.owner._entity_store.load[Blob](success(org.simplemodeling.model.datatype.EntityId.parse(reference.reference))).map(_.get)) })
        val quota = success(fixture.owner._execute_c(fixture.owner._payloads.resourceSnapshot()))
        quota.occupiedblobs shouldBe 3L
        fixture.retention.entries = ids.zipWithIndex.map { case (id, index) => DurableJobRetentionCandidate(index.toLong,
          id.value, access.copy(scopes = access.scopes + DurableJobRetentionPolicy.manageScope)) }
        When("the original registered timer crosses the exact terminal TTL")
        fixture.advance()
        Then("one owned payload is physically acknowledged and released, held/shared bytes remain, and native lifetime payments never refund")
        val report = fixture.owner._scheduler.status.lastResult.get.toOption.get
        report.entries.head.retention shouldBe Vector(DurableJobRetentionFacet.Expired, DurableJobRetentionFacet.Deleted)
        report.entries.head.cleanup shouldBe Some(DurableJobCleanupFacet.Tombstoned)
        report.entries(1).retention shouldBe Vector(DurableJobRetentionFacet.Held)
        report.entries(2).cleanup shouldBe Some(DurableJobCleanupFacet.OwnershipRefused)
        report.entries(3).retention shouldBe Vector(DurableJobRetentionFacet.Kept)
        ids.map(fixture.native) shouldBe before
        success(fixture.owner._execute_c(fixture.owner._payloads.resourceSnapshot())).occupiedblobs shouldBe 2L
        Files.exists(fixture.root.resolve("blobs").resolve(blobs.head.storageRef.get.container).resolve(blobs.head.storageRef.get.key)) shouldBe false
        blobs.tail.foreach(blob => Files.exists(fixture.root.resolve("blobs").resolve(blob.storageRef.get.container).resolve(blob.storageRef.get.key)) shouldBe true)
        fixture.owner.health.maintenanceHealthy shouldBe false
      }
    }
  }
  "DI6 Complete async execution lease" must _scenario("DI6", "R9,R11,R13") {
    "keep actual managed resolution alive after ingress and drain shutdown before physical finalization" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI6: an admitted async native body that resolves the actual managed datastore after submit returns")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        task.body = context => {
          val resolved = success(fixture.subsystem.resolveManagedComponentDataStoreC(fixture.environment, fixture.request))
          resolved.asInstanceOf[org.goldenport.cncf.datastore.sql.SqlDataStore].managedResourceOption.get should be theSameInstanceAs fixture.provider.get.managedResourceOption.get
          fixture.node.activeLeaseCount shouldBe 1
          entered.countDown(); await(release)
          success(fixture.owner._store.load(fixture.engine.listJobs().head.jobId.value, access)(using context)) should not be empty
          TaskSucceeded(OperationResponse.Scalar("ok"))
        }
        val id = fixture.submit(task)
        val executing = actor(fixture.engine.drainAll())
        var closing = Option.empty[(Thread, java.util.concurrent.atomic.AtomicReference[Either[Throwable, Consequence[Unit]]])]
        try {
          await(entered)
          When("logical shutdown overlaps the admitted native body and waits for the real binding drain")
          closing = Some(actor(fixture.subsystem.shutdownC().map(_ => ())))
          eventually(fixture.owner.health.state == JobDurableOperationalState.Closed)
          closing.get._1.isAlive shouldBe true
          fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
          fixture.node.activeLeaseCount shouldBe 1
        } finally { release.countDown(); join(executing._1); closing.foreach(value => join(value._1)) }
        Then("the admitted nested native effects finish before logical release; physical ownership closes only at node finalization")
        executing._2.get() shouldBe Right(1)
        closing.get._2.get().toOption.get.isSuccess shouldBe true
        task.runs.get() shouldBe 1
        fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
        fixture.node.activeLeaseCount shouldBe 0
        fixture.node.state shouldBe SystemNode.State.Running
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
        success(fixture.node.shutdownC())
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe false
      }
    }
  }
  "DI7 Shrinking native work sequences" must _scenario("DI7", "R6,R9,R12,R19") {
    "preserve genuine payment and finite admission drain maintenance refusal invariants" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DI7: ordinary shrinking operations on fresh managed SQLite and persistent Blob fixtures")
      val property = Prop.forAll(Gen.choose(1, 8).flatMap(size => Gen.listOfN(size, Gen.choose(0, 4)))) { operations => JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        var ids = Vector.empty[JobId]; var tasks = Vector.empty[JobDurableLifetimeRecoveryFixtures.Task]; var closed = false
        operations.forall { operation =>
          operation match {
            case 0 if !closed => val task = new JobDurableLifetimeRecoveryFixtures.Task; tasks :+= task; ids :+= fixture.submit(task)
            case 1 if !closed => fixture.engine.drainAll()
            case 2 if !closed => fixture.advance()
            case 3 => success(fixture.subsystem.shutdownC()); closed = true
            case 4 if closed => fixture.engine.submit(Nil, fixture.context).isSuccess shouldBe false
            case _ => ()
          }
          fixture.node.activeLeaseCount == 0 && tasks.forall(_.runs.get() <= 1) &&
            (closed || ids.forall(id => fixture.native(id).seed.paid == JobLifetimeSnapshot(2L, 96L, 1L, 1L) &&
              fixture.engine.getStatus(id).exists(_.toString == fixture.signed(id).record.body.lifecycle.status.toString)))
        }
      }}
      When("ScalaCheck runs seed 69709702 with normal shrinking and 24 minimum successes")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709702L)), property)
      Then("no generated sequence manufactures payment, overlaps native work or escapes the binding lease")
      result.passed shouldBe true
      result.succeeded should be >= 24
    }
  }

  private def _checkpoint(fixture: Fixture, current: DurableJobStoreSnapshot, body: DurableJobRecordBody)
    (using ctx: ExecutionContext): Consequence[DurableJobStoreSnapshot] =
    DurableJobRecord.createV2(body.copy(identity = body.identity.copy(revision = current.record.body.identity.revision + 1L)))
      .flatMap(record => fixture.owner._store.checkpoint(current, record, access))

  private def _sequence_c[A, B](values: Vector[A])(body: A => Consequence[B]): Consequence[Vector[B]] =
    values.foldLeft(Consequence.success(Vector.empty[B]))((result, value) => result.flatMap(items => body(value).map(items :+ _)))
}
