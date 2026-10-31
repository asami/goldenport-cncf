package org.goldenport.cncf.job

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobLifetimeRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobLifetimeFixtures.*
  import JobDurableLifetimeRecoveryFixtures.{LifetimeTask as NativeTask, lifetimeFacts as nativeFacts}
  import org.goldenport.protocol.operation.OperationResponse
  import JobInputBoundsFixtures.{success, sync, id, instant, access, input, descriptor, evidence,
    CountingClock, CountingTimer}

  "Lifetime bootstrap and native registration" should {
    "LR1 reject complete malformed footprints without partially seeding either budget" in {
      val invalids: Vector[JobRecord => JobRecord] = Vector(
        _.copy(tasks = null), _.copy(tasks = List(null)),
        _.copy(tasks = List.fill(5)(new Task)), _.copy(taskReadModels = null),
        _.copy(taskReadModels = Vector(null)),
        _.copy(taskReadModels = Vector.tabulate(5)(index => _model(index))),
        _.copy(taskDefinitions = null), _.copy(taskDefinitions = Map(_task_id(0) -> null)),
        _.copy(taskDefinitions = (0 until 9).map(index => _task_id(index) -> new Task).toMap),
        _.copy(timeline = null), _.copy(timeline = Vector(null)),
        _.copy(timeline = Vector.fill(161)(_event(1))),
        _.copy(debug = null), record => record.copy(debug = record.debug.copy(executionNotes = null)),
        record => record.copy(debug = record.debug.copy(executionNotes = Vector(null))),
        record => record.copy(debug = record.debug.copy(executionNotes = Vector.fill(161)("note")))
      )
      for (durable <- Vector(true, false);
           status <- Vector(JobStatus.Submitted, JobStatus.Succeeded, JobStatus.Failed, JobStatus.Cancelled);
           build <- invalids) {
        Given("a real previously paid State plus valid prefix and a late invalid footprint in either complete map")
        val prior = fresh(JobLifetimePolicy(8, 160, 4), JobResourcePolicy(maxtaskspersubmission = 4))
        val task = new Task
        try {
          val priorid = success(prior.engine.submit(List(new Task), prior.context, sync))
          val state = prior.engine.runtimeState
          val target = if (durable) state.durableJobs else state.runtimeJobs
          val opposite = if (durable) state.runtimeJobs else state.durableJobs
          val prefix = _live_record(id("prefix"), task, prior.context).copy(status = JobStatus.Succeeded)
          val invalid = build(_live_record(id("invalid"), task, prior.context).copy(status = status))
          val other = _live_record(id("opposite"), task, prior.context).copy(status = JobStatus.Succeeded)
          target.put(prefix.id, prefix)
          target.put(invalid.id, invalid)
          opposite.put(other.id, other)
          val resources = state._resource_budget.snapshot
          val lifetime = paid(prior, priorid)
          val clock = new CountingClock
          val timer = new CountingTimer(clock)
          When("a second real owner attaches to all preseeded records")
          val thrown = intercept[IllegalArgumentException] {
            new InMemoryJobEngine(runtimeState = state,
              schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
              timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
          }
          Then("the refusal preserves the complete maps and all preexisting paid and resource counters")
          thrown.getMessage shouldBe "JobEngine resource bootstrap refused"
          target.get(prefix.id) shouldBe prefix
          target.get(invalid.id) shouldBe invalid
          opposite.get(other.id) shouldBe other
          state._resource_budget.snapshot shouldBe resources
          paid(prior, priorid) shouldBe lifetime
          prior.engine.lifetimeSnapshot(prefix.id) shouldBe None
          prior.engine.lifetimeSnapshot(invalid.id) shouldBe None
          prior.engine.lifetimeSnapshot(other.id) shouldBe None
          clock.calls.get() shouldBe 0
          timer.calls.get() shouldBe 0
          timer.pendingCount shouldBe 0
          task.getters.get() shouldBe 0
          task.runs.get() shouldBe 0
        } finally prior.engine.shutdown()
      }
      Given("one bounded retained identity followed by an oversupply sentinel and a null map container")
      val state = InMemoryJobEngine.State()
      success(state.bindResourcePolicy(JobResourcePolicy(maxretainedjobs = 1)))
      val task = new Task
      val context = ExecutionContext.test()
      Vector("one", "sentinel").foreach { label =>
        val record = _live_record(id(label), task, context).copy(status = JobStatus.Succeeded)
        state.runtimeJobs.put(record.id, record)
      }
      Vector(state, InMemoryJobEngine.State(durableJobs = null)).foreach { candidate =>
        val clock = new CountingClock
        val timer = new CountingTimer(clock)
        val resources = candidate._resource_budget.snapshot
        When("actual construction attempts the malformed complete State")
        val thrown = intercept[IllegalArgumentException] {
          new InMemoryJobEngine(runtimeState = candidate,
            schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
            timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
        }
        Then("neither seed budget nor clock, timer or task has any partial effect")
        thrown.getMessage shouldBe "JobEngine resource bootstrap refused"
        candidate._resource_budget.snapshot shouldBe resources
        clock.calls.get() shouldBe 0
        timer.calls.get() shouldBe 0
        task.getters.get() shouldBe 0
      }
    }

    "LR2 retain inclusive componentwise duplicate floors and restore one original due admission" in {
      Given("duplicate closed records whose independent task/history/admission floors differ and an unstarted scheduled root")
      val state = InMemoryJobEngine.State()
      success(state.bindLifetimePolicy(JobLifetimePolicy(5, 128, 3)))
      val task = new Task
      val context = ExecutionContext.test()
      val duplicateid = id("duplicate")
      val first = _live_record(duplicateid, task, context).copy(status = JobStatus.Succeeded,
        taskReadModels = Vector(_model(0)), timeline = Vector.tabulate(96)(index => _event(index + 1)))
      val second = first.copy(taskReadModels = Vector(_model(0).copy(compensationStatus = Some("succeeded")), _model(1)),
        taskDefinitions = (0 until 5).map(index => _task_id(index) -> task).toMap, timeline = Vector.empty)
      val scheduled = _live_record(id("scheduled"), task, context)
        .copy(scheduledStartAt = Some(instant.plusSeconds(10)))
      state.durableJobs.put(first.id, first)
      state.runtimeJobs.put(second.id, second)
      state.durableJobs.put(scheduled.id, scheduled)
      When("an actual owner attaches to both complete maps")
      val fixture = JobInputBoundsFixtures.fresh(state)
      try {
        Then("one identity takes the componentwise maximum and the due root pays its prospective batch once")
        paid(fixture, duplicateid) shouldBe JobLifetimeSnapshot(5, 128, 3, 1)
        paid(fixture, scheduled.id) shouldBe JobLifetimeSnapshot(2, 96, 2, 1)
        fixture.engine.resourceSnapshot.retained shouldBe 2
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.timer.pendingCount shouldBe 1
        fixture.timer.calls.get() shouldBe 1
        task.getters.get() shouldBe 0
        state.durableJobs.get(first.id) shouldBe first
        state.runtimeJobs.get(second.id) shouldBe second
        val before = paid(fixture, duplicateid)
        When("a second owner reattaches and reselects the occupied original compensation")
        val other = JobInputBoundsFixtures.fresh(state)
        try {
          Then("paid floors, original inputs, claims and physical due ownership remain exact")
          paid(other, duplicateid) shouldBe before
          paid(other, scheduled.id) shouldBe paid(fixture, scheduled.id)
          other.engine._run_compensations(duplicateid, None, Vector(_task_id(0) -> task), other.context) shouldBe empty
          task.compensations.get() shouldBe 0
          other.timer.pendingCount shouldBe 0
          other.engine.resourceSnapshot shouldBe fixture.engine.resourceSnapshot
          When("the original due timer promotes and dispatches the admitted live batch")
          fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          fixture.engine.drainAll() shouldBe 1
          Then("normal execution consumes no second lifetime payment and releases the original physical lease")
          task.runs.get() shouldBe 1
          paid(fixture, scheduled.id) shouldBe JobLifetimeSnapshot(2, 96, 2, 1)
          fixture.engine.resourceSnapshot.pending shouldBe 0
          fixture.engine.resourceSnapshot.executing shouldBe 0
          paid(fixture, duplicateid) shouldBe before
        } finally other.engine.shutdown()
      } finally fixture.engine.shutdown()
    }

    "LR3 refuse oversized signed or live startup footprints after source and port discovery" in {
      Vector((1, 3, 0), (5, 1, 0), (1, 1, 161), (1, 1025, 0)).zipWithIndex.foreach {
        case ((signedcount, livecount, rows), index) =>
          Given("an actual signed proof-complete V2 record and an eligible port returning a complete live batch")
          val fixture = fresh(JobLifetimePolicy(4, 160, 2))
          given ExecutionContext = fixture.context
          val store = new DurableJobStore(EntityStore.standard())
          val jobid = id(s"nativerefused$index")
          val native = success(store.create(_native_record(jobid, signedcount, rows), access))
          val task = new Task
          val sourcecalls = new AtomicInteger(0)
          val portcalls = new AtomicInteger(0)
          try {
            val before = facts(fixture, task)
            When("the native coordinator invokes the source and eligible reconstruction port")
            val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sourcecalls), store,
              _port(portcalls, _ => DurableJobRuntimeRehydration(jobid, List.fill(livecount)(task),
                fixture.context, None, None)), 1))
            Then("closed Refused occurs before runtime construction, callback, reservation or physical timer")
            report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
            sourcecalls.get() shouldBe 1
            portcalls.get() shouldBe 1
            facts(fixture, task) shouldBe before
            fixture.engine.lifetimeSnapshot(jobid) shouldBe None
            fixture.engine._get_record(jobid) shouldBe None
            fixture.engine.drainAll() shouldBe 0
            success(store.load(jobid.value, access)) shouldBe Some(native)
          } finally fixture.engine.shutdown()
      }
      Given("a signed one-task record at the exact genuinely prepaid boundary")
      val fixture = fresh(JobLifetimePolicy(4, 192, 2))
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val jobid = id("nativepositive")
      val native = _publish(fixture, store, _native_record(jobid))
      val quota = store.lifetimeQuota(_policy(fixture))
      success(quota.reserveWork(jobid.value, access, 1)).seed.paid shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
      JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, native.record, fixture.context, _policy(fixture))
      val seed = success(quota.snapshot(jobid.value, access)).seed
      val task = new NativeTask
      val sourcecalls = new AtomicInteger(0)
      val portcalls = new AtomicInteger(0)
      try {
        When("the eligible reconstruction registers and its original timer becomes due")
        val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sourcecalls), store,
          _port(portcalls, _ => DurableJobRuntimeRehydration(jobid, List(task), fixture.context, None, None)), 1))
        Then("the admitted record retains native timestamps and one prepaid physical due state")
        report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
        paid(fixture, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
        fixture.engine._get_record(jobid).get.createdAt shouldBe native.record.body.identity.createdAt
        fixture.engine._get_record(jobid).get.updatedAt shouldBe native.record.body.identity.updatedAt
        fixture.timer.pendingCount shouldBe 1
        fixture.engine.drainAll() shouldBe 0
        sourcecalls.get() shouldBe 1
        portcalls.get() shouldBe 1
        task.runs.get() shouldBe 0
        success(store.load(jobid.value, access)) shouldBe Some(native)
        success(quota.snapshot(jobid.value, access)).seed shouldBe seed
        When("the original native schedule is promoted through normal dispatch")
        fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
        fixture.engine.drainAll() shouldBe 1
        Then("the live task runs once with unchanged paid counters and actual acknowledged checkpoints")
        task.runs.get() shouldBe 1
        paid(fixture, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
        fixture.engine.resourceSnapshot.pending shouldBe 0
        fixture.engine.resourceSnapshot.executing shouldBe 0
        val completed = success(store.load(jobid.value, access)).get
        completed.record.body.identity.revision shouldBe native.record.body.identity.revision + 5L
        completed.record.body.identity.createdAt shouldBe native.record.body.identity.createdAt
        val settled = success(quota.snapshot(jobid.value, access)).seed
        settled.paid shouldBe seed.paid
        settled.remaining shouldBe 1L
        settled.normalids.size shouldBe 1
        settled.claims shouldBe empty
      } finally fixture.engine.shutdown()
    }

    "LR4 bound terminal native projection and retain exact Published payment without executable work" in {
      Vector(false, true).foreach { accepted =>
        Given("a real signed terminal record with two committed normal descriptors and an inclusive or insufficient observed execution bound")
        val fixture = fresh(JobLifetimePolicy(if (accepted) 4 else 3, 160, 2))
        given ExecutionContext = fixture.context
        val store = new DurableJobStore(EntityStore.standard())
        val jobid = id(if (accepted) "terminalpositive" else "terminalrefused")
        val terminal = _native_record(jobid, 2, terminal = true)
        val native = if (accepted) JobDurableLifetimeRecoveryFixtures.publishTerminal(store, terminal, _policy(fixture))
          else success(store.create(terminal, access))
        val sourcecalls = new AtomicInteger(0)
        try {
          val clocks = fixture.clock.calls.get()
          When("the terminal-only coordinator registers the admitted closed fact")
          val report = success(fixture.engine.recoverDurableTerminalFacts(_source(jobid, sourcecalls), store, 1))
          Then("the signed footprint gates projection while preserving native authority and avoiding runtime work")
          report.candidates.map(_.fact) shouldBe Vector(if (accepted)
            DurableJobTerminalProjectionFact.Registered else DurableJobTerminalProjectionFact.Refused)
          sourcecalls.get() shouldBe 1
          fixture.engine._get_record(jobid) shouldBe None
          fixture.engine.getResult(jobid) shouldBe None
          fixture.timer.calls.get() shouldBe 0
          fixture.engine._work_queue.size() shouldBe 0
          fixture.clock.calls.get() shouldBe clocks
          fixture.engine.resourceSnapshot.pending shouldBe 0
          success(store.load(jobid.value, access)) shouldBe Some(native)
          if (accepted) {
            paid(fixture, jobid) shouldBe JobLifetimeSnapshot(4, 160, 1, 1)
            fixture.engine.durableTerminalProjection(jobid).get.snapshot shouldBe native
            fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
            fixture.engine.queryTasks(jobid).get.tasks.map(_.status) shouldBe Vector.fill(2)(JobTaskStatus.Succeeded)
            val quota = store.lifetimeQuota(_policy(fixture))
            val seed = success(quota.snapshot(jobid.value, access)).seed
            seed.remaining shouldBe 0L
            seed.normalids shouldBe terminal.body.tasks.map(_.taskId).toSet
            seed.claims shouldBe empty
            val before = paid(fixture, jobid)
            When("the same signed terminal identity is offered again")
            val duplicate = success(fixture.engine.recoverDurableTerminalFacts(_source(jobid, sourcecalls), store, 1))
            Then("closed duplicate refusal conserves exact native payment and original projection")
            duplicate.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Refused)
            paid(fixture, jobid) shouldBe before
            success(quota.snapshot(jobid.value, access)).seed shouldBe seed
            fixture.engine.durableTerminalProjection(jobid).get.snapshot shouldBe native
          } else {
            fixture.engine.lifetimeSnapshot(jobid) shouldBe None
            fixture.engine.durableTerminalProjection(jobid) shouldBe None
            fixture.engine.resourceSnapshot.retained shouldBe 0
          }
        } finally fixture.engine.shutdown()
      }
    }

    "LR5 retain original input and task history across real nested execution and shared ownership" in {
      Given("a Published V2 declaration and original inline/Blob input whose compatible metadata must be retained")
      val fixture = fresh(JobLifetimePolicy(4, 192, 2))
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val jobid = id("authority")
      val value = input(Vector(descriptor.copy(filename = Some("legacy.txt"), byteSize = Some(3),
        sha256 = Some("abc"), inlineBase64 = Some("YWJj"), blobId = Some("blob-1"))))
      val native = _publish(fixture, store, _native_record(jobid,
        inputs = JobDurableLifetimeRecoveryFixtures.durableInputs(value)))
      val quota = store.lifetimeQuota(_policy(fixture))
      JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, native.record, fixture.context, _policy(fixture))
      val child = new NativeTask(relationkind = "child")
      val task = new NativeTask(body = ctx => {
        success(fixture.engine.runTaskInJobSync(jobid, child, ctx))
        TaskSucceeded(OperationResponse.Scalar("ok"))
      })
      val sourcecalls = new AtomicInteger(0)
      val portcalls = new AtomicInteger(0)
      try {
        When("the real port reconstructs input and the original root runs its child while Running/Pending")
        val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sourcecalls), store,
          _port(portcalls, _ => DurableJobRuntimeRehydration(jobid, List(task), fixture.context, Some(value), None)), 1))
        Then("registration preserves the actual root payment and exact original sanitized metadata")
        report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
        native.record.body.inputs shouldBe Vector(DurableInputReference("input-0", DurableValue.Absent))
        fixture.engine._get_record(jobid).get.input shouldBe Some(value.copy(payloads = value.payloads.map(_.sanitized)))
        fixture.engine._get_record(jobid).get.input.get.payloads.head.sha256 shouldBe Some("abc")
        fixture.engine._get_record(jobid).get.input.get.payloads.head.inlineBase64 shouldBe None
        fixture.engine._get_record(jobid).get.input.get.payloads.head.blobId shouldBe None
        paid(fixture, jobid) shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
        success(store.load(jobid.value, access)) shouldBe Some(native)
        When("the original due timer promotes the root and its nested actual child")
        fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
        fixture.engine.drainAll() shouldBe 1
        Then("both actual tasks consume exactly two paid admissions and leave immutable original task events")
        task.runs.get() shouldBe 1
        child.runs.get() shouldBe 1
        paid(fixture, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
        val original = fixture.engine._get_record(jobid).get
        val originalid = original.taskReadModels.find(_.parentTaskId.isEmpty).get.taskId
        val originalevents = original.timeline.filter(_.taskId.contains(originalid))
        originalevents should not be empty
        original.taskDefinitions.get(originalid) shouldBe Some(task)
        original.taskReadModels.find(_.taskId == originalid).get.component shouldBe Some("component-a")
        original.taskReadModels.find(_.taskId == originalid).get.transactionOutcome shouldBe Some("committed")
        original.taskReadModels.find(_.parentTaskId.contains(originalid)).get.transactionOutcome shouldBe Some("committed")
        val acknowledged = success(store.load(jobid.value, access)).get
        acknowledged.record.body.identity.createdAt shouldBe native.record.body.identity.createdAt
        acknowledged.record.body.authorization shouldBe native.record.body.authorization
        acknowledged.record.body.inputs shouldBe native.record.body.inputs
        acknowledged.record.body.definitionSnapshot shouldBe native.record.body.definitionSnapshot
        acknowledged.record.body.retention shouldBe native.record.body.retention
        acknowledged.record.body.identity.revision shouldBe native.record.body.identity.revision + 7L
        val seed = success(quota.snapshot(jobid.value, access)).seed
        seed.remaining shouldBe 0L
        seed.normalids shouldBe original.taskReadModels.map(_.taskId.value).toSet
        seed.claims shouldBe empty
        val refused = new NativeTask(relationkind = "child")
        val attachbefore = nativeFacts(fixture, refused)
        When("a second State owner attaches and a new child attempts work at the full policy")
        val other = JobInputBoundsFixtures.fresh(fixture.engine.runtimeState)
        try {
          Then("shared ownership preserves every observation and refuses before getters, clocks, ids and native writes")
          nativeFacts(fixture, refused) shouldBe attachbefore
          paid(other, jobid) shouldBe seed.paid
          val before = nativeFacts(other, refused)
          refusal(other.engine.enqueueTaskInJob(jobid, refused, other.context))
          nativeFacts(other, refused) shouldBe before
          success(store.load(jobid.value, access)) shouldBe Some(acknowledged)
          success(quota.snapshot(jobid.value, access)).seed shouldBe seed
          fixture.engine._get_record(jobid).get.timeline.filter(_.taskId.contains(originalid)) shouldBe originalevents
          sourcecalls.get() shouldBe 1
          portcalls.get() shouldBe 1
          paid(fixture, jobid) shouldBe seed.paid
        } finally other.engine.shutdown()
      } finally fixture.engine.shutdown()
    }

    "LR6 conserve at least 24 seeded real signed registration and due-drain attempts" in {
      Given("finite signed descriptor/live-task footprints with both exact positive and one-credit-short policies")
      val generator = for {
        signedcount <- Gen.choose(1, 3)
        livecount <- Gen.choose(1, 3)
        rows <- Gen.choose(0, 8)
      } yield (signedcount, livecount, rows)
      val property = Prop.forAll(generator) { case (signedcount, livecount, rows) =>
        Vector(true, false).foreach { accepted =>
          val history = 64L * (signedcount + livecount) + 64L
          val fixture = fresh(JobLifetimePolicy(2L * (signedcount + livecount),
            history - (if (accepted) 0 else 1), 2))
          given ExecutionContext = fixture.context
          val store = new DurableJobStore(EntityStore.standard())
          val jobid = id(s"seeded$signedcount$livecount$rows$accepted")
          val root = _publish(fixture, store, _native_record(jobid, signedcount))
          val quota = store.lifetimeQuota(_policy(fixture))
          val historic = Vector.tabulate(signedcount)(index => s"historic-native-$index")
          historic.foreach(taskid => success(quota.associate(jobid.value, access, taskid)) shouldBe true)
          val closed = root.record.body.copy(identity = root.record.body.identity.copy(revision = 2L),
            tasks = root.record.body.tasks.zip(historic).map { case (task, taskid) =>
              task.copy(taskId = taskid, transaction = DurableTransactionDescriptor(DurableTransactionRole.Own,
                DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed))
            }, timeline = _native_record(jobid, signedcount, rows).body.timeline)
          val native = success(store.checkpoint(root, success(DurableJobRecord.createV2(closed)), access))
          val rootseed = success(quota.snapshot(jobid.value, access)).seed
          rootseed.paid shouldBe JobLifetimeSnapshot(2L * signedcount, 32L + 64L * signedcount, 1, 1)
          val prepayment = quota.reserveWork(jobid.value, access, livecount)
          if (accepted) success(prepayment).seed.paid shouldBe JobLifetimeSnapshot(2L * (signedcount + livecount), history, 2, 2)
          else {
            refusal(prepayment)
            success(quota.snapshot(jobid.value, access)).seed shouldBe rootseed
          }
          JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, native.record, fixture.context, _policy(fixture))
          val beforeseed = success(quota.snapshot(jobid.value, access)).seed
          val tasks = Vector.tabulate(livecount)(index =>
            new NativeTask(relationkind = if (index == 0) "root" else "child"))
          val sourcecalls = new AtomicInteger(0)
          val portcalls = new AtomicInteger(0)
          try {
            val before = tasks.map(task => nativeFacts(fixture, task))
            When("a real signed source and eligible port offer the generated complete batch")
            val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sourcecalls), store,
              _port(portcalls, _ => DurableJobRuntimeRehydration(jobid, tasks.toList,
                fixture.context, None, None)), 1))
            Then("only the exact policy registers, and every refused attempt conserves all runtime effects")
            report.candidates.map(_.fact) shouldBe Vector(if (accepted)
              DurableJobRuntimeRehydrationFact.Registered else DurableJobRuntimeRehydrationFact.Refused)
            sourcecalls.get() shouldBe 1
            portcalls.get() shouldBe 1
            success(store.load(jobid.value, access)) shouldBe Some(native)
            success(quota.snapshot(jobid.value, access)).seed shouldBe beforeseed
            if (accepted) {
              val expected = JobLifetimeSnapshot(2L * (signedcount + livecount), history, 2, 2)
              paid(fixture, jobid) shouldBe expected
              fixture.engine.resourceSnapshot.retained shouldBe 1
              fixture.engine.resourceSnapshot.pending shouldBe 1
              fixture.timer.pendingCount shouldBe 1
              fixture.timer.calls.get() shouldBe 1
              fixture.engine.drainAll() shouldBe 0
              tasks.map(_.runs.get()) shouldBe Vector.fill(livecount)(0)
              fixture.ids.calls.get() shouldBe 0
              When("the original physical timer promotes and the queue dispatches the entire generated batch")
              fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
              fixture.engine.drainAll() shouldBe 1
              Then("all live tasks finish once while exact paid counters and the original closed prefix remain retained")
              tasks.map(_.runs.get()).sum shouldBe livecount
              tasks.map(_.runs.get()) shouldBe Vector.fill(livecount)(1)
              fixture.engine.queryTasks(jobid).get.tasks.size shouldBe livecount
              fixture.engine.resourceSnapshot.pending shouldBe 0
              fixture.engine.resourceSnapshot.executing shouldBe 0
              fixture.timer.pendingCount shouldBe 0
              paid(fixture, jobid) shouldBe expected
              val completed = success(store.load(jobid.value, access)).get
              completed.record.body.tasks.take(signedcount) shouldBe native.record.body.tasks
              completed.record.body.timeline.take(rows) shouldBe native.record.body.timeline
              completed.record.body.identity.revision shouldBe native.record.body.identity.revision + 3L + 2L * livecount
              val settled = success(quota.snapshot(jobid.value, access)).seed
              settled.paid shouldBe expected
              settled.remaining shouldBe 0L
              settled.normalids.size shouldBe signedcount + livecount
              settled.normalids should contain allElementsOf historic
              settled.claims shouldBe empty
            } else {
              tasks.map(task => nativeFacts(fixture, task)) shouldBe before
              fixture.engine._get_record(jobid) shouldBe None
              fixture.engine.lifetimeSnapshot(jobid) shouldBe None
              fixture.engine.drainAll() shouldBe 0
            }
          } finally fixture.engine.shutdown()
        }
        true
      }
      When("ScalaCheck executes the fixed seed with at least 24 successful real behavior checks")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(6970933541L)), property)
      Then("every generated exact and insufficient signed admission satisfies conservation and due dispatch")
      val failurelocation = result.status match {
        case failed: Test.PropException =>
          failed.e.getStackTrace.filter(_.getFileName == "JobLifetimeRecoverySpec.scala").take(6).mkString(" | ")
        case _ => ""
      }
      withClue(s"LR6 generated recovery status=${result.status}, succeeded=${result.succeeded}, location=$failurelocation: ") {
        result.passed shouldBe true
      }
      result.succeeded should be >= 24
    }
  }

  private def _policy(fixture: Fixture): DurableJobLifetimeQuotaPolicy =
    DurableJobLifetimeQuotaPolicy.default.copy(lifetime = fixture.engine.runtimeState._lifetime_budget.policy)

  private def _publish(fixture: Fixture, store: DurableJobStore, record: DurableJobRecord)
      (using ExecutionContext): DurableJobStoreSnapshot =
    JobDurableLifetimeRecoveryFixtures.publish(store, record, _policy(fixture), record.body.tasks.size)

  private def _task_id(index: Int): TaskId = TaskId("cncf", "lifetime", Some(instant), Some(s"task$index"))

  private def _model(index: Int): JobTaskReadModel =
    JobTaskReadModel(_task_id(index), None, JobTaskStatus.Succeeded, instant, Some(instant),
      JobTaskResultSummary(true, Some("ok")), relation = Some("root"), transactionOutcome = Some("committed"))

  private def _event(sequence: Int): JobTimelineEvent =
    JobTimelineEvent(sequence.toLong, instant, "task.succeeded", None, None, None)

  private def _live_record(jobid: JobId, task: JobTask, context: ExecutionContext): JobRecord =
    JobRecord(jobid, List(task), context, JobStatus.Submitted, None,
      JobPersistencePolicy.Persistent, JobRunMode.Async, 0, None, instant, instant,
      Vector.empty, timeline = Vector.empty, debug = JobDebugInfo(None, Map.empty, Vector.empty),
      input = Some(input(Vector(descriptor))))

  private def _source(jobid: JobId, calls: AtomicInteger): DurableJobStartupRecoverySource =
    new DurableJobStartupRecoverySource {
      def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
        val _ = maxCandidates
        calls.incrementAndGet()
        Consequence.success(Vector(DurableJobStartupRecoveryCandidate(0L, jobid.value, access,
          DurableReplayEvidence(Some("idempotency"), Some("input"), Some("definition"),
            Some("authorization"), Some("provider"), Some("compatibility")))))
      }
    }

  private def _port(calls: AtomicInteger,
    build: DurableJobStartupRecoveryCandidate => DurableJobRuntimeRehydration): DurableJobRuntimeRehydrationPort =
    new DurableJobRuntimeRehydrationPort {
      def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
        decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] = {
        val _ = snapshot
        val _ = decision
        calls.incrementAndGet()
        Consequence.success(build(candidate))
      }
    }

  private def _native_record(jobid: JobId, count: Int = 1, rows: Int = 0,
    terminal: Boolean = false, inputs: Vector[DurableInputReference] = Vector.empty): DurableJobRecord = {
    val supplied = evidence(1L).projection
    val tasks = Vector.tabulate(count) { index =>
      supplied.tasks.head.copy(taskId = _task_id(index).value,
        transaction = if (terminal) DurableTransactionDescriptor(DurableTransactionRole.Own,
          DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed)
        else supplied.tasks.head.transaction)
    }
    val timeline = Vector.tabulate(rows)(index =>
      DurableTimelineEvent(index.toLong + 1, instant, "job.submitted", None, None))
    success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(jobid.value, 1L, instant, instant.plusSeconds(20)), supplied.authorization,
      DurableJobLifecycle(if (terminal) DurableJobLifecycleStatus.Succeeded else DurableJobLifecycleStatus.Submitted,
        0, DurableRunMode.Async, supplied.retry,
        DurableScheduleState(if (terminal) None else Some(instant.plusSeconds(10)), None, None)),
      tasks, inputs, if (terminal) DurableResultOutcome.Succeeded(DurableValue.Absent) else DurableResultOutcome.Pending,
      timeline, Vector.empty, None, supplied.definitionSnapshot, supplied.retention)))
  }
}
