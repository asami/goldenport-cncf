package org.goldenport.cncf.job

import java.nio.file.Files
import java.time.{Duration, Instant}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SessionContext}
import org.goldenport.cncf.subsystem.SystemNode
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobMigrationCheckpointFixtures.*

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobDurableMigrationStartupSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-migration-checkpoint, example:$example, rules:$rules, phase:69.7, slice:D2")

  "MI1 Explicit trusted migration under the original barrier" must _scenario("MI1", "M1,M2,M3,M11,M12") {
    "fetch once checkpoint before registration and recover the same entire cached terminal and runtime vector" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M1,M2,M3,M11,M12 MI1: genuine native legacy terminal and unstarted V2 roots under explicit job_admin or content_admin composition")
      Vector("job_admin", "content_admin").foreach { capability =>
        withPath { path =>
          val seed = new Fixture(path)
          val roots = try {
            success(seed.start())
            Vector(seed.legacy(seed.publish("startup_terminal", Vector(Array[Byte](2, 3)))), seed.publish("startup_runtime"))
          } finally seed.close()
          val fixture = new Fixture(path, capability)
          fixture.migration = Some(true); fixture.select(roots*)
          val id = success(JobId.parse(roots.last.jobId)); val task = new JobDurableLifetimeRecoveryFixtures.Task
          fixture.original.tasks.put(id, List(task))
          val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
          fixture.blob.streamBefore = () => {
            val probe = actor(fixture.engine.durableOperationalHealth)
            join(probe._1)
            probe._2.get().toOption.get.get.state shouldBe JobDurableOperationalState.Starting
            entered.countDown(); await(release)
          }
          var running = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
          try {
            When("the trusted provider enters real startup and migration reaches physical preflight")
            running = Some(actor(fixture.start())); await(entered)
            Then("original submit and manual drain stay blocked without IDs body getters workers port calls or timers")
            val before = (fixture.original.ids.calls.get(), fixture.original.clock.calls.get(), fixture.engine.resourceSnapshot, task.getters.get())
            fixture.engine.submit(List(task), fixture.context).isSuccess shouldBe false
            fixture.engine.drainAll() shouldBe 0
            (fixture.original.ids.calls.get(), fixture.original.clock.calls.get(), fixture.engine.resourceSnapshot, task.getters.get()) shouldBe before
            fixture.source.calls.get() shouldBe 1; fixture.original.port.calls.get() shouldBe 0
            fixture.entity.jobwrites.get() shouldBe 0; fixture.engine._durable_lifecycle_write_bridge shouldBe None
            fixture.engine._durable_runtime_owner_option shouldBe None; fixture.engine._workers_started.get() shouldBe false
            fixture.effects.timers shouldBe 0; task.runs.get() shouldBe 0; task.canonical.get() shouldBe 0
            When("complete physical verification is released before the original entire cached recovery")
            release.countDown(); join(running.get._1)
            Then("only the acknowledged native migration plus both original recovery passes publish Ready and one maintenance scheduler")
            success(running.get._2.get().toOption.get)
            fixture.signed(roots.head.jobId).record.format shouldBe DurableRecordFormat.V2
            fixture.signed(roots.head.jobId).record.body shouldBe roots.head.snapshot.record.body.copy(
              identity = roots.head.snapshot.record.body.identity.copy(revision = roots.head.snapshot.record.body.identity.revision + 1L))
            fixture.signed(roots.last.jobId) shouldBe roots.last.snapshot
            fixture.source.calls.get() shouldBe 1; fixture.original.port.calls.get() shouldBe 1
            fixture.engine.query(success(JobId.parse(roots.head.jobId))).get.tasks.tasks.map(_.taskId.value) shouldBe roots.head.snapshot.record.body.tasks.map(_.taskId)
            fixture.engine.getStatus(id) shouldBe Some(JobStatus.Submitted)
            fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
            fixture.owner._scheduler.status.started shouldBe true
            fixture.blob.opens.get() shouldBe 1; fixture.blob.closes.get() shouldBe 1
            task.runs.get() shouldBe 0; task.canonical.get() shouldBe 0
            val timers = fixture.effects.timers
            When("initialization is repeated on the same already Ready provider")
            val repeat = fixture.original.subsystem._initialize_durable_job_runtime_c()
            Then("original idempotence retains scheduler registration and the one cached source fetch")
            success(repeat); fixture.effects.timers shouldBe timers
            fixture.source.calls.get() shouldBe 1; fixture.original.builds.get() shouldBe 1
          } finally { release.countDown(); running.foreach(value => join(value._1)); fixture.close() }
        }
      }
    }
  }
  "MI2 Default and explicit false compatibility" must _scenario("MI2", "M2,M3,M11") {
    "retain original V1 refusal unconfigured and V2 behavior while refusing unprivileged explicit migration" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M2,M3,M11 MI2: genuinely paid terminal sources under default and explicit-false provider inputs")
      Vector(None, Some(false)).foreach { migration =>
        withPath { path =>
          val roots = seed(path, Vector("default_legacy"))
          val fixture = new Fixture(path); fixture.migration = migration; fixture.select(roots*)
          try {
            When("the unchanged provider starts over a canonical paid V1 without explicit migration")
            val result = fixture.start()
            Then("original startup refuses V1 with one source call bridge bind no helper checkpoint or physical get")
            _startup_refused(result); fixture.inputs.get.migrateV1OnStartup shouldBe false
            fixture.signed(roots.head.jobId) shouldBe roots.head.snapshot
            fixture.entity.jobwrites.get() shouldBe 0; fixture.entity.lifewrites.get() shouldBe 0
            fixture.blob.gets.get() shouldBe 0; fixture.source.calls.get() shouldBe 1
            fixture.engine._durable_lifecycle_write_bridge should not be empty
            fixture.engine._durable_runtime_owner_option shouldBe None; fixture.engine.drainAll() shouldBe 0
          } finally fixture.close()
        }
        withPath { path =>
          val seed = new Fixture(path)
          val root = try { success(seed.start()); seed.terminal(seed.publish("default_v2")) } finally seed.close()
          val fixture = new Fixture(path); fixture.migration = migration; fixture.select(root)
          try {
            When("the same default or false composition starts over already V2 native terminal storage")
            val result = fixture.start()
            Then("original recovery becomes Ready without migration I/O and retains exact paid native storage")
            success(result); fixture.signed(root.jobId) shouldBe root.snapshot
            fixture.entity.jobwrites.get() shouldBe 0; fixture.entity.lifewrites.get() shouldBe 0
            fixture.blob.gets.get() shouldBe 0; fixture.source.calls.get() shouldBe 1
            fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
          } finally fixture.close()
        }
      }
      Given("an actual original Subsystem has no admitted durable provider configuration")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        When("unconfigured original initialization is requested")
        val result = fixture.subsystem._initialize_durable_job_runtime_c()
        Then("the engine remains Unconfigured without any provider or discovery activation")
        success(result); fixture.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Unconfigured
        fixture.builds.get() shouldBe 0; fixture.startup.calls.get() shouldBe 0
      }
      Given("a trusted provider explicitly enables migration with an ordinary context lacking Operator capabilities")
      withPath { path =>
        val roots = seed(path, Vector("unauthorized_startup")); val fixture = new Fixture(path, "")
        fixture.migration = Some(true); fixture.select(roots*)
        var probeloads = -1
        fixture.source.before = () => probeloads = fixture.entity.loads.get()
        try {
          When("startup reaches opt-in preparation after probes and the one trusted source fetch")
          val result = fixture.start()
          Then("Operator denial precedes migration native I/O bridge binding and Ready")
          _startup_refused(result); fixture.entity.loads.get() shouldBe probeloads
          fixture.entity.jobwrites.get() shouldBe 0; fixture.entity.lifewrites.get() shouldBe 0
          fixture.blob.gets.get() shouldBe 0; fixture.source.calls.get() shouldBe 1
          fixture.engine._durable_lifecycle_write_bridge shouldBe None; fixture.engine._durable_runtime_owner_option shouldBe None
        } finally fixture.close()
      }
    }
  }
  "MI3 Whole startup failure and partial durable migration" must _scenario("MI3", "M2,M4,M9,M10,M11") {
    "keep invalid-tail preflight mutation-free and retain actual migrated prefixes through later native recovery or timer failure" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M2,M4,M9,M10,M11 MI3: explicit startup with genuine native prefix and an invalid foreign-access suffix")
      withPath { path =>
        val roots = seed(path, Vector("invalid_prefix", "invalid_suffix")); val fixture = new Fixture(path)
        fixture.migration = Some(true); fixture.select(roots*)
        fixture.source.entries = fixture.source.entries.updated(1, fixture.source.entries(1).copy(access = access.copy(subjectId = "PRIVATE foreign")))
        try {
          When("complete migration preflight encounters the invalid native suffix")
          val result = fixture.start()
          Then("startup never checkpoints the valid prefix or publishes Ready and retains original payment")
          _startup_refused(result); roots.foreach(root => fixture.signed(root.jobId) shouldBe root.snapshot)
          fixture.entity.jobwrites.get() shouldBe 0; fixture.entity.lifewrites.get() shouldBe 0
          fixture.engine._durable_lifecycle_write_bridge shouldBe None; fixture.engine._durable_runtime_owner_option shouldBe None
          fixture.engine.drainAll() shouldBe 0; fixture.engine._workers_started.get() shouldBe false
        } finally fixture.close()
      }
      Vector("checkpoint", "ack", "port", "recovery", "timer").foreach { mode =>
        Given("genuine preflight-valid roots precede a deliberately injected later startup boundary failure")
        withPath { path =>
          val seed = new Fixture(path)
          var paid = Option.empty[org.goldenport.record.Record]
          val roots = try {
            success(seed.start())
            val first = seed.legacy(seed.publish(s"prefix_$mode"))
            val last = if (mode == "port" || mode == "recovery") {
              val root = seed.publish(s"suffix_$mode")
              if (mode == "recovery") root.copy(snapshot = seed.checkpoint(root.snapshot,
                root.snapshot.record.body.copy(lifecycle = root.snapshot.record.body.lifecycle.copy(status = DurableJobLifecycleStatus.Running)))) else root
            } else seed.legacy(seed.publish(s"suffix_$mode"))
            paid = seed.row(lifetimeId); Vector(first, last)
          } finally seed.close()
          val fixture = new Fixture(path); fixture.migration = Some(true); fixture.select(roots*)
          val tasks = roots.map(_ => new JobDurableLifetimeRecoveryFixtures.Task)
          roots.zip(tasks).foreach { case (root, task) => fixture.original.tasks.put(success(JobId.parse(root.jobId)), List(task)) }
          mode match {
            case "checkpoint" => fixture.entity.beforeJobWrite = () => {
              if (fixture.entity.jobwrites.get() == 2) throw new IllegalStateException("PRIVATE suffix checkpoint failure")
            }
            case "ack" => fixture.entity.loseJobAck = true
            case "port" => fixture.port = new DurableJobRuntimeRehydrationPort {
              def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
                decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] =
                Consequence.stateInvalid("PRIVATE startup port refusal")
            }
            case "timer" => fixture.entity.afterJobCommit = () => {
              fixture.entity.afterJobCommit = () => ()
              val control = fixture.original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get
              control.advanceBy(Duration.between(control.now, Instant.MAX))
            }
            case _ => ()
          }
          try {
            When(s"explicit startup encounters the later $mode failure after a genuine prefix commit")
            val result = fixture.start()
            Then("fixed startup refusal preserves actual V2 prefix and payment while original admission workers and Ready remain closed")
            _startup_refused(result); fixture.signed(roots.head.jobId).record.format shouldBe DurableRecordFormat.V2
            fixture.signed(roots.head.jobId).record.body shouldBe roots.head.snapshot.record.body.copy(
              identity = roots.head.snapshot.record.body.identity.copy(revision = roots.head.snapshot.record.body.identity.revision + 1L))
            fixture.row(lifetimeId) shouldBe paid; fixture.entity.lifewrites.get() shouldBe 0
            fixture.source.calls.get() shouldBe 1; fixture.engine._durable_runtime_owner_option shouldBe None
            fixture.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Degraded
            fixture.engine._workers_started.get() shouldBe false; fixture.engine.drainAll() shouldBe 0
            tasks.foreach(task => { task.runs.get() shouldBe 0; task.canonical.get() shouldBe 0 })
            if (mode == "checkpoint" || mode == "ack") fixture.signed(roots.last.jobId) shouldBe roots.last.snapshot
            if (mode == "ack") success(fixture.use(fixture.store.resourceSnapshot()(using fixture.context))).pendingreservations shouldBe 1
          } finally fixture.close()
        }
      }
    }
  }
  "MI4 Actual SystemNode drain during migration" must _scenario("MI4", "M2,M8,M11") {
    "keep the original physical pool alive through stream close and prevent Ready after actual engine close flags" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M2,M8,M11 MI4: genuine V1 payload held at consumption and finally-close barriers under real provider startup")
      withPath { path =>
        val roots = seed(path, Vector("drain"), payload = true); val fixture = new Fixture(path)
        fixture.migration = Some(true); fixture.select(roots*)
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val closingstream = new CountDownLatch(1); val close = new CountDownLatch(1)
        fixture.blob.streamBefore = () => { fixture.node.activeLeaseCount shouldBe 1; entered.countDown(); await(release) }
        fixture.blob.closeBefore = () => { fixture.node.activeLeaseCount shouldBe 1; closingstream.countDown(); await(close) }
        var running = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
        var closing = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
        var stopping = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
        var acknowledged = Option.empty[DurableJobStoreSnapshot]
        fixture.entity.afterJobCommit = () => acknowledged = Some(fixture.signed(roots.head.jobId))
        try {
          When("explicit startup holds the real stream under its actual managed lease")
          running = Some(actor(fixture.start())); await(entered)
          val pool = fixture.sql.get.managedResourceOption.get
          When("actual Subsystem shutdown sets engine close flags and actual SystemNode shutdown starts draining")
          closing = Some(actor(fixture.original.subsystem.shutdownC().map(_ => ())))
          JobDurableRuntimeFixtures.eventually(fixture.engine._admission_closed)
          stopping = Some(actor(fixture.node.shutdownC()))
          JobDurableRuntimeFixtures.eventually(fixture.node.state == SystemNode.State.Stopping)
          Then("both shutdown actors wait and the node-owned pool stays open through physical consumption")
          closing.get._1.isAlive shouldBe true; stopping.get._1.isAlive shouldBe true; pool.isOpen shouldBe true
          When("consumption resumes and the original stream enters finally-close")
          release.countDown(); await(closingstream)
          Then("the same managed pool still stays open until that exact stream close completes")
          pool.isOpen shouldBe true; fixture.blob.closes.get() shouldBe 0; fixture.node.activeLeaseCount shouldBe 1
          When("the physical close and admitted migration operation are released")
          close.countDown(); join(running.get._1); join(closing.get._1); join(stopping.get._1)
          Then("original close flags refuse Ready while the genuinely acknowledged checkpoint survives and only the node closes its pool")
          _startup_refused(running.get._2.get().toOption.get)
          success(closing.get._2.get().toOption.get); success(stopping.get._2.get().toOption.get)
          acknowledged.get.record.format shouldBe DurableRecordFormat.V2
          fixture.blob.opens.get() shouldBe 1; fixture.blob.closes.get() shouldBe 1
          fixture.node.activeLeaseCount shouldBe 0; fixture.node.state shouldBe SystemNode.State.Stopped; pool.isOpen shouldBe false
          fixture.engine._durable_runtime_owner_option shouldBe None; fixture.engine._workers_started.get() shouldBe false
        } finally {
          release.countDown(); close.countDown(); running.foreach(value => join(value._1))
          closing.foreach(value => join(value._1)); stopping.foreach(value => join(value._1)); fixture.close()
        }
      }
    }
  }
  "MI5 Fresh managed recovery after migration" must _scenario("MI5", "M2,M6,M7,M10,M11") {
    "register unchanged terminal facts from persisted V2 payment without re-migration and retain sticky degraded health" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M2,M6,M7,M10,M11 MI5: native SQLite and Blob files with genuinely paid V1 terminal publication")
      withPath { path =>
        val roots = seed(path, Vector("fresh"), payload = true)
        val first = new Fixture(path); first.migration = Some(true); first.select(roots*)
        var before = Option.empty[NativeState]; var snapshot = Option.empty[DurableJobStoreSnapshot]
        try {
          When("the first explicitly enabled managed owner migrates and completes original recovery")
          val result = first.start()
          Then("the acknowledged native V2 and unchanged payment are ready for fresh context recovery")
          success(result); snapshot = Some(first.signed(roots.head.jobId)); before = Some(first.state(roots*))
          first.entity.jobwrites.get() shouldBe 1; first.entity.lifewrites.get() shouldBe 0
        } finally first.close()
        Given("a distinct original engine and context are constructed over the same native files")
        val fresh = new Fixture(path); fresh.migration = Some(true); fresh.select(roots*)
        try {
          When("the fresh trusted owner preflights actual V2 and registers original terminal facts")
          val result = fresh.start()
          Then("no migration or paid root mutation repeats and exact canonical rows provider revisions metadata and bytes survive")
          success(result); fresh.signed(roots.head.jobId) shouldBe snapshot.get; fresh.state(roots*) shouldBe before.get
          fresh.entity.jobwrites.get() shouldBe 0; fresh.entity.lifewrites.get() shouldBe 0; fresh.entity.creates.get() shouldBe 0
          fresh.original.port.calls.get() shouldBe 0; fresh.original.ids.calls.get() shouldBe 0; fresh.original.clock.calls.get() shouldBe 0
          fresh.engine.query(success(JobId.parse(roots.head.jobId))).get.tasks.tasks.map(_.taskId.value) shouldBe roots.head.snapshot.record.body.tasks.map(_.taskId)
          fresh.owner.health.state shouldBe JobDurableOperationalState.Ready
          Given("a deliberate physical fault degrades that original owner")
          val file = fresh.physicalPath(roots.head.references.head)
          Files.write(file, Array[Byte](9, 2, 3))
          When("authorized original integrity inspection observes the physical fault")
          val fault = success(fresh.engine.inspectDurableIntegrity(1)(using fresh.context))
          Then("the existing owner records sticky Degraded health")
          fault.entries.head.status shouldBe JobDurableIntegrityStatus.PayloadRefused
          fresh.owner.health.state shouldBe JobDurableOperationalState.Degraded
          Given("the same native physical bytes are restored without paid or Job mutation")
          Files.write(file, Array[Byte](1, 2, 3))
          When("already V2 migration preflight and original integrity inspection later succeed")
          val migration = fresh.migrate(fresh.source.entries, 1)
          val healthy = success(fresh.engine.inspectDurableIntegrity(1)(using fresh.context))
          Then("read-only projection and preparation do not heal sticky degradation or re-sign native storage")
          success(migration); healthy.entries.head.status shouldBe JobDurableIntegrityStatus.Healthy
          fresh.owner.health.state shouldBe JobDurableOperationalState.Degraded
          fresh.state(roots*) shouldBe before.get; fresh.entity.jobwrites.get() shouldBe 0; fresh.entity.lifewrites.get() shouldBe 0
        } finally fresh.close()
      }
    }
  }
  "MI6 Seeded exact access and startup redaction" must _scenario("MI6", "M3,M4,M8,M11,M13") {
    "shrink at least 24 real signed capability foreign-access provider interruption and hostile credential cases" in {
      Given("docs/spec/durable-job-migration-checkpoint-contract.md M3,M4,M8,M11,M13 MI6: finite real managed legacy sources and independently supplied hostile strings")
      val generator = for { mode <- Gen.choose(0, 4); capability <- Gen.oneOf("job_admin", "content_admin", ""); token <- Gen.choose(0, 31) } yield (mode, capability, token)
      val property = Prop.forAll(generator) { case (mode, capability, token) =>
        withPath { path =>
          val roots = seed(path, Vector("redaction"), payload = true)
          val fixture = new Fixture(path, capability)
          val hostile = s"PRIVATE credential=/secret/path/$token provider-token-$token"
          fixture.selectedContext = ExecutionContext.withSecurityContext(fixture.context, fixture.context.security.copy(
            session = Some(SessionContext(tokenId = Some(hostile), attributes = Map("credential" -> hostile)))))
          fixture.migration = Some(true); fixture.select(roots*)
          mode match {
            case 0 => fixture.source.entries = Vector(candidate(roots.head.jobId, recordAccess = access.copy(subjectId = hostile)))
            case 1 => fixture.blob.mode = "provider-throw"
            case 2 => fixture.blob.mode = "stream-interrupt"
            case 3 => fixture.source.entries = Vector(candidate(roots.head.jobId, 9L))
            case _ => fixture.source.before = () => throw new IllegalStateException(hostile)
          }
          try {
            When("the explicitly enabled original provider receives exact foreign access or a hostile startup boundary failure")
            val result = fixture.start()
            val interrupted = Thread.currentThread().isInterrupted
            if (interrupted) Thread.interrupted()
            Then("only the fixed startup refusal escapes and every native signed paid state survives without body IDs or clock effects")
            _startup_refused(result)
            failure(result).show should not include hostile
            fixture.signed(roots.head.jobId) shouldBe roots.head.snapshot
            fixture.entity.jobwrites.get() shouldBe 0; fixture.entity.lifewrites.get() shouldBe 0; fixture.entity.creates.get() shouldBe 0
            fixture.original.port.calls.get() shouldBe 0; fixture.original.ids.calls.get() shouldBe 0; fixture.original.clock.calls.get() shouldBe 0
            fixture.engine._durable_runtime_owner_option shouldBe None; fixture.engine._workers_started.get() shouldBe false
            fixture.engine.drainAll() shouldBe 0; fixture.effects.timers shouldBe 0
            fixture.blob.closes.get() shouldBe fixture.blob.opens.get()
            if (mode == 2 && capability.nonEmpty) interrupted shouldBe true
            Given("the same exact signed state is passed directly with independent foreign access to the migration boundary")
            val before = fixture.state(roots*); val effects = fixture.effects
            fixture.blob.mode = "normal"
            When("a fresh helper requests migration using foreign source access")
            val direct = fixture.migrate(Vector(candidate(roots.head.jobId, recordAccess = access.copy(tenantId = hostile))), 1)
            Then("only the fixed migration refusal escapes with exact paid and native state unchanged")
            failure(direct).observation.getEffectiveMessage shouldBe Some("durable job migration checkpoint refused")
            failure(direct).show should not include hostile
            fixture.state(roots*) shouldBe before; fixture.effects shouldBe effects
            true
          } finally { Thread.interrupted(); fixture.close() }
        }
      }
      When("ordinary shrinking ScalaCheck executes seed 69709704 with minimum 24 successful native cases")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709704L)), property)
      Then("at least 24 cases retain exact native access interruption and fixed redaction")
      result.passed shouldBe true; result.succeeded should be >= 24
    }
  }

  private def _startup_refused(result: Consequence[?]): Unit =
    failure(result).observation.getEffectiveMessage shouldBe Some("durable runtime startup refused")
}
