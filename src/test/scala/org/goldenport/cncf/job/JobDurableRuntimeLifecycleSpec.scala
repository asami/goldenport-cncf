package org.goldenport.cncf.job

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.subsystem.SystemNode
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import JobDurableRuntimeFixtures.*

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobDurableRuntimeLifecycleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:durable-job-runtime-owner, example:$example, rules:$rules, phase:69.7, slice:JM69-09D1B")

  "DL1 Actual owned CLI activation" must _scenario("DL1", "R2,R3,R4,R18") {
    "start the extras provider after final assembly SPI and startup import and close its physical owner" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DL1: canonical typed argv, isolated controlled descriptor/home, and an actual extras component/provider")
      val path = root(); val fixture = new CliInputs(path); fixture.extra = true
      var handle = Option.empty[org.goldenport.cncf.bootstrap.CncfHandle]
      fixture.before = selected => {
        selected.components.map(_.name) should contain("org.goldenport.cncf.test.DurableExtra")
        selected.componentApiResolver should not be null
        selected.runtimeStartupImportConfigurationC.isSuccess shouldBe true
        selected.systemNode.activeLeaseCount shouldBe 1
        selected.jobEngine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Starting
      }
      try {
        When("new CncfRuntime.initializeHandle completes the final assembly and activates the admitted exact provider")
        handle = Some(success(fixture.initialize()))
        val subsystem = handle.get.subsystem
        Then("the returned original engine is Ready and repeated owned close finalizes only its actual node and pool")
        fixture.subsystem.get should be theSameInstanceAs subsystem
        fixture.finalComponents should contain("org.goldenport.cncf.test.DurableExtra")
        fixture.builds.get() shouldBe 1; fixture.startup.calls.get() shouldBe 1
        subsystem.jobEngine.durableOperationalHealth.get shouldBe JobDurableOperationalHealth(JobDurableOperationalState.Ready, true, true, true, true, true, true)
        success(subsystem._initialize_durable_job_runtime_c())
        fixture.builds.get() shouldBe 1
        val node = subsystem.systemNode
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
        handle.get.close(); handle.get.close()
        node.state shouldBe SystemNode.State.Stopped
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe false
        subsystem.jobEngine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Closed
      } finally { handle.foreach(_.close()); fixture.subsystem.foreach(org.goldenport.cncf.subsystem.Subsystem.shutdownOwned); remove(path) }
    }
  }
  "DL2 Complete source barrier on both original timers" must _scenario("DL2", "R5,R7,R8") {
    "refuse ingress claims and sweep before commit then release original execution exactly once" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DL2: manual and realtime native profiles with actual discovery held before authentication")
      Vector(true, false).foreach { manual =>
        val path = root(); val fixture = new Fixture(path, manual = manual)
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1); val callback = new CountDownLatch(1)
        val claims = new AtomicInteger
        fixture.startup.before = () => { entered.countDown(); await(release) }
        val running = actor(fixture.start())
        try {
        try {
          await(entered)
          val task = new JobDurableLifetimeRecoveryFixtures.Task
          val before = (fixture.ids.calls.get(), fixture.engine.resourceSnapshot, task.getters.get())
          val registration = fixture.engine._timer.schedule(fixture.engine.timeSource.now().plusMillis(1)) {
            if (fixture.engine.drainOne()) claims.incrementAndGet()
            callback.countDown()
          }
          try {
            When("submit, manual drain, and a real original-timer callback overlap the still incomplete source")
            fixture.engine.submit(List(task), fixture.context).isSuccess shouldBe false
            fixture.engine.drainAll() shouldBe 0
            if (manual) fixture.advance()
            await(callback)
            (fixture.ids.calls.get(), fixture.engine.resourceSnapshot, task.getters.get()) shouldBe before
            claims.get() shouldBe 0; task.runs.get() shouldBe 0
            fixture.retention.calls.get() shouldBe 0
            fixture.engine._workers_started.get() shouldBe false
            fixture.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Starting
          } finally registration.close()
        } finally { release.countDown(); join(running._1) }
          Then("only complete startup publishes Ready and the original worker or manual queue executes one subsequent task")
          running._2.get().toOption.get.isSuccess shouldBe true
          fixture.startup.calls.get() shouldBe 1
          fixture.engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Ready
          val task = new JobDurableLifetimeRecoveryFixtures.Task
          val id = fixture.submit(task)
          if (manual) fixture.engine.drainAll() shouldBe 1
          else success(fixture.engine.awaitResult(id, 5000L)) shouldBe JobResult.Success(org.goldenport.protocol.operation.OperationResponse.Scalar("ok"))
          task.runs.get() shouldBe 1; task.canonical.get() shouldBe 1
          fixture.engine._workers_started.get() shouldBe !manual
          success(fixture.subsystem._initialize_durable_job_runtime_c())
          fixture.builds.get() shouldBe 1
        } finally { fixture.close(); remove(path) }
      }
    }
  }
  "DL3 Original realtime registration failure" must _scenario("DL3", "R8,R13") {
    "return fixed startup refusal and perform actual owned CLI cleanup without fallback execution" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DL3: the original realtime ScheduledJobTimer closed inside the already-begun provider factory")
      val path = root(); val fixture = new CliInputs(path)
      fixture.before = selected => {
        val engine = selected.jobEngine.asInstanceOf[InMemoryJobEngine]
        engine.durableOperationalHealth.get.state shouldBe JobDurableOperationalState.Starting
        engine._timer.shutdown()
      }
      try {
        When("the actual owner registers maintenance on that closed original ScheduledExecutorService")
        val result = fixture.initialize(realtime = true)
        Then("native registration throws and ordinary CLI cleanup closes gate, binding, and actual physical owner")
        result.isSuccess shouldBe false
        JobDurableRuntimeFixtures.failureConclusion(result).show should include("durable runtime startup refused")
        JobDurableRuntimeFixtures.failureConclusion(result).show should not include path.toString
        fixture.builds.get() shouldBe 1; fixture.startup.calls.get() shouldBe 1
        val subsystem = fixture.subsystem.get
        val engine = subsystem.jobEngine.asInstanceOf[InMemoryJobEngine]
        subsystem.systemNode.state shouldBe SystemNode.State.Stopped
        subsystem.systemNode.activeLeaseCount shouldBe 0
        subsystem.systemNode.bindingCount shouldBe 0
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe false
        engine._durable_runtime_owner_option shouldBe None
        engine._durable_runtime_failed shouldBe true
        engine._durable_runtime_initializing shouldBe false
        engine.drainAll() shouldBe 0
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        engine.submit(List(task), fixture.context).isSuccess shouldBe false
        task.runs.get() shouldBe 0; task.getters.get() shouldBe 0
        subsystem._initialize_durable_job_runtime_c().isSuccess shouldBe false
        fixture.builds.get() shouldBe 1
      } finally { fixture.subsystem.foreach(org.goldenport.cncf.subsystem.Subsystem.shutdownOwned); remove(path) }
    }
  }
  "DL4 Admitted maintenance versus logical shutdown" must _scenario("DL4", "R9,R12,R13") {
    "finish nested managed work while rejecting future callbacks and waiting for the real binding drain" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DL4: the original registered callback blocked inside retention discovery under a real binding lease")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        val entered = new CountDownLatch(1); val release = new CountDownLatch(1)
        val nested = new AtomicInteger
        fixture.retention.before = () => {
          fixture.node.activeLeaseCount shouldBe 1
          entered.countDown(); await(release)
          success(fixture.subsystem.resolveManagedComponentDataStoreC(fixture.environment, fixture.request)).asInstanceOf[org.goldenport.cncf.datastore.sql.SqlDataStore].managedResourceOption.get should be theSameInstanceAs fixture.provider.get.managedResourceOption.get
          success(fixture.owner._entity_store.load[DurableJobLifetimeQuotaEntity](success(DurableJobLifetimeQuotaEntity.entityId))(using summon[org.goldenport.cncf.entity.EntityPersistent[DurableJobLifetimeQuotaEntity]], fixture.context))
          nested.incrementAndGet()
        }
        val callback = actor(fixture.advance())
        var closing = Option.empty[(Thread, java.util.concurrent.atomic.AtomicReference[Either[Throwable, Consequence[Unit]]])]
        try {
          await(entered)
          When("Subsystem shutdown closes owner registration and gate while its already admitted source remains in flight")
          closing = Some(actor(fixture.subsystem.shutdownC().map(_ => ())))
          eventually(fixture.owner.health.state == JobDurableOperationalState.Closed)
          fixture.owner._scheduler.status.closed shouldBe true
          fixture.owner._scheduler.status.inFlight shouldBe true
          fixture.owner._scheduler.status.nextDueAt shouldBe None
          fixture.owner._entity_store.load[DurableJobLifetimeQuotaEntity](success(DurableJobLifetimeQuotaEntity.entityId))(using summon[org.goldenport.cncf.entity.EntityPersistent[DurableJobLifetimeQuotaEntity]], fixture.context).isSuccess shouldBe false
          closing.get._1.isAlive shouldBe true
          fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
        } finally { release.countDown(); join(callback._1); closing.foreach(value => join(value._1)) }
        Then("admitted nested work finishes, there is no rearm or deadlock, and physical closure remains with SystemNode")
        callback._2.get() shouldBe Right(())
        closing.get._2.get().toOption.get.isSuccess shouldBe true
        nested.get() shouldBe 1
        fixture.retention.calls.get() shouldBe 1
        fixture.advance(Duration.ofSeconds(2))
        fixture.retention.calls.get() shouldBe 1
        fixture.node.activeLeaseCount shouldBe 0
        fixture.node.state shouldBe SystemNode.State.Running
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe true
        success(fixture.node.shutdownC())
        fixture.provider.get.managedResourceOption.get.isOpen shouldBe false
      }
    }
  }
  "DL5 Two logical bindings one physical pool" must _scenario("DL5", "R1,R3,R13") {
    "close one owner while the other original engine remains Ready and usable" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DL5: two independent logical Subsystems resolving the same managed SQLite on one SystemNode")
      val path = root(); val node = SystemNode.create()
      val first = new Fixture(path, node = node, ownsNode = false)
      val second = new Fixture(path, node = node, ownsNode = false)
      try {
        success(first.start()); success(second.start())
        val firstpool = first.provider.get.managedResourceOption.get
        val secondpool = second.provider.get.managedResourceOption.get
        firstpool should be theSameInstanceAs secondpool
        first.engine should not be theSameInstanceAs(second.engine)
        first.owner should not be theSameInstanceAs(second.owner)
        When("the first logical owner shuts down and the second admits genuine native work")
        success(first.subsystem.shutdownC())
        val task = new JobDurableLifetimeRecoveryFixtures.Task
        val id = second.submit(task)
        second.engine.drainAll() shouldBe 1
        Then("the shared physical pool remains open until the explicit SystemNode owner finalizes")
        first.owner.health.state shouldBe JobDurableOperationalState.Closed
        second.owner.health.state shouldBe JobDurableOperationalState.Ready
        second.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
        task.runs.get() shouldBe 1
        firstpool.isOpen shouldBe true; node.state shouldBe SystemNode.State.Running
        success(second.subsystem.shutdownC())
        firstpool.isOpen shouldBe true
        success(node.shutdownC())
        firstpool.isOpen shouldBe false; node.state shouldBe SystemNode.State.Stopped
      } finally { first.close(); second.close(); node.shutdownC(); remove(path) }
    }
  }
  "DL6 Authorized additive operator catalog" must _scenario("DL6", "R14,R16,R19") {
    "preserve legacy categories and refuse permission before health I/O across seeded shrinking native sequences" in {
      Given("docs/spec/durable-job-runtime-owner-contract.md DL6: actual unconfigured/ready/degraded/closed owners and authorized or ordinary principals")
      JobDurableRuntimeFixtures.withFixture { fixture =>
        val measured = new HealthProbe(fixture.engine)
        val service = new JobExperienceService(measured)
        val component = _component(fixture)
        val operator = JobExperienceOperationalAuditFixtures.context()
        val ordinary = JobExperienceOperationalAuditFixtures.context(privilege = SecurityContext.Privilege.User)
        val before = (fixture.entity.loads.get(), fixture.startup.calls.get(), fixture.retention.calls.get(), fixture.clock.calls.get(), fixture.ids.calls.get())
        When("an ordinary caller requests the actual operator catalog before any engine health lookup")
        service.operatorCatalog(component)(using ordinary).isSuccess shouldBe false
        Then("authorization refuses before the health getter or any native I/O")
        measured.healthcalls.get() shouldBe 0
        (fixture.entity.loads.get(), fixture.startup.calls.get(), fixture.retention.calls.get(), fixture.clock.calls.get(), fixture.ids.calls.get()) shouldBe before
        _assert_catalog(success(service.operatorCatalog(component)(using operator)), fixture, JobDurableOperationalState.Unconfigured)
        success(fixture.start())
        _assert_catalog(success(service.operatorCatalog(component)(using operator)), fixture, JobDurableOperationalState.Ready)
        fixture.retention.fault = true; fixture.advance()
        _assert_catalog(success(service.operatorCatalog(component)(using operator)), fixture, JobDurableOperationalState.Degraded)
        success(fixture.subsystem.shutdownC())
        _assert_catalog(success(service.operatorCatalog(component)(using operator)), fixture, JobDurableOperationalState.Closed)
      }
      val property = Prop.forAll(Gen.choose(1, 8).flatMap(size => Gen.listOfN(size, Gen.choose(0, 3)))) { operations => JobDurableRuntimeFixtures.withFixture { fixture =>
        success(fixture.start())
        val service = new JobExperienceService(fixture.engine); val component = _component(fixture)
        val operator = JobExperienceOperationalAuditFixtures.context()
        var closed = false
        operations.forall { operation =>
          operation match {
            case 1 if !closed => fixture.retention.fault = true; fixture.advance()
            case 2 => success(fixture.subsystem.shutdownC()); closed = true
            case _ => fixture.engine.durableOperationalHealth
          }
          val before = (fixture.entity.loads.get(), fixture.startup.calls.get(), fixture.retention.calls.get(), fixture.clock.calls.get(), fixture.ids.calls.get())
          val catalog = success(service.operatorCatalog(component)(using operator))
          _assert_catalog(catalog, fixture, fixture.engine.durableOperationalHealth.get.state)
          before == (fixture.entity.loads.get(), fixture.startup.calls.get(), fixture.retention.calls.get(), fixture.clock.calls.get(), fixture.ids.calls.get()) && fixture.node.activeLeaseCount == 0
        }
      }}
      When("seed 69709702 runs at least 24 ordinarily shrinking catalog/health/close sequences")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709702L)), property)
      Then("all generated catalogs preserve availability, legacy runtime health, and the fixed public projection without private values")
      result.passed shouldBe true
      result.succeeded should be >= 24
    }
  }

  private def _assert_catalog(record: Record, fixture: Fixture, state: JobDurableOperationalState): Unit = {
    val categories = record.getAny("categories").collect {
      case values: Seq[?] => values.collect { case value: Record => value }.toVector
    }.getOrElse(Vector.empty)
    categories.map(_.getString("name").get) shouldBe Vector("search", "diagnostics", "recovery", "definitions", "queue", "scheduler", "health", "retention", "durable-health", "durable-maintenance")
    categories(7).getBoolean("available") shouldBe Some(false)
    categories(7).getString("handoff") shouldBe Some("Phase 69.7")
    val legacy = record.asMap("health").asInstanceOf[Record]
    legacy.getBoolean("runtime-only") shouldBe Some(true)
    legacy.getInt("running") shouldBe fixture.engine.metrics.map(_.running)
    val health = record.asMap("durable-health").asInstanceOf[Record]
    health.asMap.keySet shouldBe Set("state", "configured", "storage-ready", "startup-ready", "maintenance-registered", "maintenance-healthy", "execution-available")
    health.getString("state") shouldBe Some(state.toString)
    health.asMap.filterNot(_._1 == "state").values.forall(_.isInstanceOf[Boolean]) shouldBe true
    categories(8).getBoolean("available") shouldBe Some(fixture.engine.durableOperationalHealth.get.configured)
    categories(9).getBoolean("available") shouldBe Some(fixture.engine.durableOperationalHealth.get.maintenanceRegistered)
    health.toString should not include providerId
    health.toString should not include fixture.root.toString
    health.toString should not include "PRIVATE"
  }

  private def _component(fixture: Fixture): Component =
    success(JobControlComponent.Factory.createC(ComponentCreate(fixture.subsystem, ComponentOrigin.Builtin))).primary

  private final class HealthProbe(delegate: JobEngine) extends JobEngine {
    val healthcalls = new AtomicInteger
    override def durableOperationalHealth: Option[JobDurableOperationalHealth] = { healthcalls.incrementAndGet(); delegate.durableOperationalHealth }
    override def metrics: Option[JobMetrics] = delegate.metrics
    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = delegate.submit(tasks, ctx)
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = delegate.submit(tasks, ctx, option)
    def getStatus(jobId: JobId): Option[JobStatus] = delegate.getStatus(jobId)
    def getResult(jobId: JobId): Option[JobResult] = delegate.getResult(jobId)
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = delegate.awaitResult(jobId, timeoutMillis)
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy)(using ctx: ExecutionContext): Consequence[JobControlResponse] = delegate.control(jobId, request, policy)
    def query(jobId: JobId): Option[JobQueryReadModel] = delegate.query(jobId)
    def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = delegate.queryTasks(jobId, offset, limit)
    def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = delegate.queryTimeline(jobId, offset, limit)
  }
}
