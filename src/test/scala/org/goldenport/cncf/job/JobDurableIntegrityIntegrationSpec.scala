package org.goldenport.cncf.job

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.subsystem.SystemNode
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DurableJobIntegrityInspectionFixtures.*
import JobDurableIntegrityStatus.*

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobDurableIntegrityIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-integrity-operations, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1C")

  "II1 Original configured public operations" must _scenario("II1", "I2,I3,I5,I11") {
    "authorize job and content administrators before bounds while retaining exact native candidate access" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I2,I3,I5,I11 II1: the original configured InMemory engine sole facade and genuinely Published local payload")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val root = fixture.publish("public_integrity", Vector(Array[Byte](1, 2)))
        fixture.select(root)
        val service = new JobExperienceService(fixture.engine)
        val before = fixture.state(root); val effects = fixture.effects
        When("job_admin and content_admin invoke both sole public Operator methods")
        val results = Vector("job_admin", "content_admin").flatMap { capability =>
          val caller = operator(capability)
          Vector(success(fixture.engine.inspectDurableIntegrity(1)(using caller)), success(service.operatorIntegrity(1)(using caller)))
        }
        val source = fixture.source.calls.get(); val loads = fixture.entity.loads.get(); val gets = fixture.blob.gets.get()
        val denied = ExecutionContext.create(SecurityContext.Privilege.User)
        val enginefailure = fixture.engine.inspectDurableIntegrity(0)(using denied)
        val facadefailure = service.operatorIntegrity(Int.MaxValue)(using denied)
        Then("authorized results agree and a no-capability malformed call performs no discovery native or owner effect")
        results.distinct shouldBe Vector(JobDurableIntegrityReport(Vector(JobDurableIntegrityEntry(0L, Healthy)), 2L))
        failure(enginefailure).show should include("operator scope requires job_admin or content_admin")
        failure(facadefailure).show should include("operator scope requires job_admin or content_admin")
        fixture.source.calls.get() shouldBe source; fixture.entity.loads.get() shouldBe loads; fixture.blob.gets.get() shouldBe gets
        fixture.node.activeLeaseCount shouldBe 0; fixture.state(root) shouldBe before; fixture.effects shouldBe effects
      }
    }
  }
  "II2 Real lifecycle admission" must _scenario("II2", "I2,I3,I11") {
    "refuse legacy unconfigured Starting Closing and Closed admission while keeping an empty Ready report nonhealthy" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I2,I3,I11 II2: a legacy concrete engine and actual unconfigured managed original engine")
      given ExecutionContext = operator()
      val legacy = new LegacyEngine
      When("the authorized Operator invokes the legacy engine's concrete default inspection")
      val unsupported = legacy.inspectDurableIntegrity()
      Then("the legacy concrete default reports its unsupported operation")
      failure(unsupported).show should include("not supported")
      Given("the original managed engine is unconfigured and startup and close actors have independent release tracking")
      val path = JobDurableRuntimeFixtures.root(); val fixture = new Fixture(path)
      val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
      var starting = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
      var closing = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
      try {
        val before = fixture.entity.loads.get()
        When("the authorized Operator invokes the actual unconfigured original engine")
        val unconfigured = fixture.engine.inspectDurableIntegrity(1)
        Then("unconfigured admission returns the fixed operational refusal")
        _fixed(unconfigured)
        Given("the actual source callback will hold startup discovery before Ready publication")
        fixture.source.before = () => { entered.countDown(); await(release) }
        When("actual native startup pauses at discovery before Ready publication")
        starting = Some(actor(fixture.start())); await(entered)
        When("the authorized Operator invokes inspection while the actual owner is Starting")
        val startingrefused = fixture.engine.inspectDurableIntegrity(1)
        Then("Starting admission refuses without another discovery or native load")
        _fixed(startingrefused)
        fixture.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Starting
        fixture.entity.loads.get() shouldBe before; fixture.source.calls.get() shouldBe 1
        When("the discovery barrier releases and the original startup actor completes")
        release.countDown(); join(starting.get._1); success(starting.get._2.get().toOption.get)
        Given("the actual Ready owner has an empty source with its startup callback restored")
        fixture.source.before = () => ()
        When("the authorized Operator inspects the empty Ready source")
        val empty = success(fixture.engine.inspectDurableIntegrity(1))
        Then("empty successful evidence remains nonhealthy and the actual owner stays Ready")
        empty.entries shouldBe empty.entries.empty; empty.isHealthy shouldBe false
        fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
        Given("source and native load counts are captured before actual close admission")
        val calls = fixture.source.calls.get(); val loads = fixture.entity.loads.get()
        When("the original timer registration holds actual owner close at Closing and then permits Closed")
        fixture.engine._timer.synchronized {
          closing = Some(actor(fixture.owner._stop_c()))
          JobDurableRuntimeFixtures.eventually(fixture.owner.health.state == JobDurableOperationalState.Closing)
          When("the original engine receives inspection while its owner is Closing")
          val closingengine = fixture.engine.inspectDurableIntegrity(1)
          Then("the Closing engine returns the fixed refusal")
          _fixed(closingengine)
          When("the actual Closing owner receives direct internal inspection")
          val closingowner = fixture.owner._inspect_integrity_c(1)
          Then("the Closing owner returns the fixed refusal")
          _fixed(closingowner)
        }
        When("the original close actor completes after the timer monitor is released")
        join(closing.get._1); success(closing.get._2.get().toOption.get)
        When("the original engine receives inspection after actual owner close")
        val closedengine = fixture.engine.inspectDurableIntegrity(1)
        Then("the Closed engine returns the fixed refusal")
        _fixed(closedengine)
        When("the actual Closed owner receives direct internal inspection")
        val closedowner = fixture.owner._inspect_integrity_c(1)
        Then("the Closed owner returns the fixed refusal")
        _fixed(closedowner)
        Then("unavailable lifecycle phases never discover or load and successful empty evidence never degrades or certifies health")
        fixture.owner.health.state shouldBe JobDurableOperationalState.Closed
        fixture.source.calls.get() shouldBe calls; fixture.entity.loads.get() shouldBe loads; fixture.blob.gets.get() shouldBe 0
      } finally {
        release.countDown(); starting.foreach(value => join(value._1)); closing.foreach(value => join(value._1))
        fixture.close(); JobDurableRuntimeFixtures.remove(path)
      }
    }
  }
  "II3 Full physical lifetime lease" must _scenario("II3", "I9,I11,I12") {
    "retain the actual managed node pool through blocked stream consumption and complete stream close" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I9,I11,I12 II3: a real native paid payload and independent consumption and close barriers")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val root = fixture.publish("lease_stream", Vector(Array[Byte](1, 2, 3))); fixture.select(root)
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val closingstream = new CountDownLatch(1); val close = new CountDownLatch(1)
        val pool = fixture.sql.get.managedResourceOption.get
        Given("the actual source callback is configured to probe the original engine's health from another actor")
        fixture.source.before = () => {
          When("the actual source callback probes operational health on a separate actor during admitted inspection")
          val probe = actor(fixture.engine.durableOperationalHealth)
          join(probe._1)
          Then("the source callback's health probe completes with Ready without an engine monitor blocking it")
          probe._2.get().toOption.get.get.state shouldBe JobDurableOperationalState.Ready
        }
        Given("the actual physical stream callback has a separate health probe and a consumption release barrier")
        fixture.blob.streamBefore = () => {
          When("the actual physical stream callback probes operational health on a separate actor")
          val probe = actor(fixture.engine.durableOperationalHealth)
          join(probe._1)
          Then("the stream callback's probe completes with Ready and the original node lease remains active")
          probe._2.get().toOption.get.get.state shouldBe JobDurableOperationalState.Ready
          fixture.node.activeLeaseCount shouldBe 1; entered.countDown(); await(release)
        }
        Given("the same opened stream has an independent close barrier with the original node lease counter")
        fixture.blob.closeBefore = () => {
          Then("the original node lease remains active when the opened physical stream enters its close barrier")
          fixture.node.activeLeaseCount shouldBe 1; closingstream.countDown(); await(close)
        }
        When("the authorized Operator starts actual inspection with the source and physical stream callbacks installed")
        val running = actor(fixture.engine.inspectDurableIntegrity(1)(using operator()))
        var stopping = Option.empty[(Thread, AtomicReference[Either[Throwable, Consequence[Unit]]])]
        try {
          await(entered)
          When("actual SystemNode shutdown overlaps an already admitted inspection and waits for its full physical lease")
          stopping = Some(actor(fixture.node.shutdownC()))
          JobDurableRuntimeFixtures.eventually(fixture.node.state == SystemNode.State.Stopping)
          Then("shutdown remains alive and the managed native pool remains open while stream consumption is blocked")
          stopping.get._1.isAlive shouldBe true; pool.isOpen shouldBe true
          When("physical consumption is released and the same opened stream reaches its close barrier")
          release.countDown(); await(closingstream)
          Then("shutdown still waits with the pool open and no completed physical stream close")
          stopping.get._1.isAlive shouldBe true; pool.isOpen shouldBe true
          fixture.blob.closes.get() shouldBe 0
        } finally {
          release.countDown(); close.countDown(); join(running._1); stopping.foreach(value => join(value._1))
        }
        Then("source and stream callbacks hold no engine monitor and shutdown physically closes only after the complete stream close and final native read")
        success(running._2.get().toOption.get).entries.map(_.status) shouldBe Vector(Healthy)
        success(stopping.get._2.get().toOption.get)
        fixture.blob.opens.get() shouldBe 1; fixture.blob.closes.get() shouldBe 1
        fixture.node.activeLeaseCount shouldBe 0; pool.isOpen shouldBe false
        fixture.node.state shouldBe SystemNode.State.Stopped
      }
    }
  }
  "II4 Actual competing native checkpoint" must _scenario("II4", "I7,I8,I10,I11") {
    "report Stale after a winning optimistic checkpoint without overwriting its revision or retained charges" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I7,I8,I10,I11 II4: a genuine native Job and a stream verification barrier before final reread")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val root = fixture.publish("checkpoint_race", Vector(Array[Byte](1, 2))); fixture.select(root)
        val lifetime = fixture.row(lifetimeId); val quota = fixture.row(blobQuotaId)
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        fixture.blob.streamBefore = () => { entered.countDown(); await(release) }
        val running = actor(fixture.engine.inspectDurableIntegrity(1)(using operator()))
        var winner = Option.empty[DurableJobStoreSnapshot]
        var native = Option.empty[NativeState]; var effects = Option.empty[Effects]
        try {
          await(entered)
          When("a second actual native lease commits an optimistic next-revision checkpoint before physical verification resumes")
          winner = Some(fixture.checkpoint(root.snapshot, root.snapshot.record.body.copy(
            identity = root.snapshot.record.body.identity.copy(updatedAt = instant.plusSeconds(21)))))
          native = Some(fixture.state(root)); effects = Some(fixture.effects)
        } finally { release.countDown(); join(running._1) }
        Then("the final exact provider snapshot mismatch is Stale and the inspection adds no overwrite native revision or quota payment")
        success(running._2.get().toOption.get).entries.map(_.status) shouldBe Vector(Stale)
        fixture.state(root) shouldBe native.get; fixture.effects shouldBe effects.get
        fixture.row(lifetimeId) shouldBe lifetime; fixture.row(blobQuotaId) shouldBe quota
        fixture.use(success(fixture.owner._store.load(root.jobId, access)(using fixture.context)).get) shouldBe winner.get
        winner.get.providerrevision.value should be > root.snapshot.providerrevision.value
        fixture.owner.health.state shouldBe JobDurableOperationalState.Degraded
      }
    }
  }
  "II5 Repeated and fresh managed inspection" must _scenario("II5", "I1,I5,I7,I10,I11") {
    "preserve paid authority across fresh startup and keep a genuine fault sticky after later Healthy evidence" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I1,I5,I7,I10,I11 II5: genuine terminal publication association and actual persisted owned bytes")
      DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
        val root = fixture.terminal(fixture.publish("fresh_integrity", Vector(Array[Byte](7, 8)), terminalTasks = true))
        fixture.select(root)
        val before = fixture.state(root); val effects = fixture.effects
        val paid = fixture.row(lifetimeId); val quota = fixture.row(blobQuotaId)
        (1 to 3).foreach { _ =>
          When("the authorized Operator repeats inspection through the sole original engine")
          val report = success(fixture.engine.inspectDurableIntegrity(1)(using operator()))
          Then("the repeated genuine terminal inspection remains Healthy")
          report.isHealthy shouldBe true
        }
        Then("repeated inspections preserve the exact original native state and effect snapshot")
        fixture.state(root) shouldBe before; fixture.effects shouldBe effects
        When("the original owner closes after the repeated inspections")
        fixture.close()
        Given("a fresh actual managed fixture uses the same persisted root and terminal source candidate")
        val fresh = new Fixture(fixture.root)
        try {
          fresh.select(root)
          When("the fresh actual managed owner performs ordinary startup over the terminal source candidate")
          success(fresh.start())
          Given("the fresh Ready owner's native state effects and port count are captured after ordinary startup")
          val afterstartup = fresh.state(root); val nativeeffects = fresh.effects
          val port = fresh.original.port.calls.get()
          When("the authorized Operator inspects the fresh Ready managed owner")
          val firstfresh = success(fresh.engine.inspectDurableIntegrity(1)(using operator()))
          Then("the fresh managed owner authenticates the genuine existing terminal record as Healthy")
          firstfresh.isHealthy shouldBe true
          Given("the actual Blob provider returns a wrong digest for the existing paid physical payload")
          fresh.blob.mode = "wrong-digest"
          When("the authorized Operator inspects that wrong-digest provider result")
          val refused = success(fresh.engine.inspectDurableIntegrity(1)(using operator()))
          Given("the actual Blob provider is restored to its original physical result")
          fresh.blob.mode = "normal"
          When("the authorized Operator inspects again after the provider result is restored")
          val healthy = success(fresh.engine.inspectDurableIntegrity(1)(using operator()))
          Then("ordinary fresh startup retains exact payment and inspections have no execution effects while a refusal remains sticky Degraded")
          refused.entries.map(_.status) shouldBe Vector(PayloadRefused); healthy.isHealthy shouldBe true
          fresh.owner.health.state shouldBe JobDurableOperationalState.Degraded
          fresh.state(root) shouldBe afterstartup; fresh.effects shouldBe nativeeffects
          fresh.row(lifetimeId) shouldBe paid; fresh.row(blobQuotaId) shouldBe quota
          fresh.original.port.calls.get() shouldBe port; port shouldBe 0
          fresh.engine.getStatus(success(JobId.parse(root.jobId))) shouldBe Some(JobStatus.Succeeded)
          fresh.engine._workers_started.get() shouldBe false
        } finally fresh.close()
      }
    }
  }
  "II6 Hostile native redaction property" must _scenario("II6", "I2,I3,I4,I5,I12,I13") {
    "keep seeded secret control and Unicode native failures outside every public projection without borrowing access" in {
      Given("docs/spec/durable-job-integrity-operations-contract.md I2,I3,I4,I5,I12,I13 II6: bounded hostile strings with seed 69709703 ordinary generators and shrinking")
      val characters = Gen.oneOf('a', 'Z', '/', '\\', '\n', '\r', '\t', '\u0000', '界', 'é', '\u202e')
      val generated = Gen.choose(0, 32).flatMap(size => Gen.listOfN(size, characters).map(_.mkString))
      val property = Prop.forAll(generated) { secret =>
        DurableJobIntegrityInspectionFixtures.withFixture { fixture =>
          val corrupt = fixture.publish("secret_native"); val denied = fixture.publish("foreign_native")
          val marker = s"private-token/$secret"
          fixture.replaceText(fixture.jobEntityId(corrupt.jobId), marker)
          fixture.source.entries = Vector(candidate(corrupt.jobId), candidate(denied.jobId, 1L,
            access.copy(tenantId = "foreign-tenant", subjectId = "foreign-subject", scopes = Set.empty)))
          val before = fixture.state(corrupt, denied); val effects = fixture.effects
          When("an admitted public Operator reads corrupted native content and an intact record under foreign exact candidate access")
          val report = success(fixture.engine.inspectDurableIntegrity(2)(using operator()))
          fixture.entity.before = id => if (id == fixture.jobEntityId(corrupt.jobId)) throw new IllegalStateException(marker)
          val failed = fixture.engine.inspectDurableIntegrity(2)(using operator("content_admin"))
          fixture.entity.before = _ => ()
          fixture.source.before = () => throw new IllegalStateException(marker)
          val sourcefailed = new JobExperienceService(fixture.engine).operatorIntegrity(2)(using operator())
          fixture.source.before = () => ()
          Then("closed ordinal facets and fixed batch refusals expose neither hostile text identities scopes provider text nor live authority")
          report.entries shouldBe Vector(JobDurableIntegrityEntry(0L, Corrupt), JobDurableIntegrityEntry(1L, AccessRefused))
          report.physicalBytesRead shouldBe 0L
          Vector(report.toString, failure(failed).show, failure(sourcefailed).show).foreach { projection =>
            Vector(marker, corrupt.jobId, denied.jobId, access.tenantId, "foreign-tenant", "foreign-subject").foreach(value => projection should not include value)
          }
          _fixed(failed); _fixed(sourcefailed)
          fixture.state(corrupt, denied) shouldBe before; fixture.effects shouldBe effects
          fixture.blob.gets.get() shouldBe 0; fixture.owner.health.state shouldBe JobDurableOperationalState.Degraded
        }
        true
      }
      When("ScalaCheck runs at least 24 seeded native hostile cases with ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709703L)), property)
      Then("all generated actual native redaction and exact-access cases satisfy the closed contract")
      checked.passed shouldBe true; checked.succeeded should be >= 24
    }
  }

  private def _fixed(result: Consequence[?]): Unit =
    failure(result).show shouldBe failure(DurableJobIntegrityInspection.refused[Unit]).show

  private final class LegacyEngine extends JobEngine {
    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = Consequence.operationInvalid("legacy.submit")
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = Consequence.operationInvalid("legacy.submit")
    def getStatus(jobId: JobId): Option[JobStatus] = None
    def getResult(jobId: JobId): Option[JobResult] = None
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = Consequence.operationInvalid("legacy.await")
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy)
        (using ExecutionContext): Consequence[JobControlResponse] = Consequence.operationInvalid("legacy.control")
    def query(jobId: JobId): Option[JobQueryReadModel] = None
    def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = None
    def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = None
  }
}
