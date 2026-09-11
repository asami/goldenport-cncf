package org.goldenport.cncf.job

import java.time.{Duration, Instant}

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.event.EventStore
import org.goldenport.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/*
 * Executable specification for configured durable admission, task lifecycle
 * checkpointing, and refusal closure.
 *
 * @since   Sep.  9, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class DurableJobLifecycleWriteBridgeSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E1, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e2 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E2, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e3 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E3, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e4 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E4, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e5 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E5, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e6 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E6, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e7 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E7, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03N"
  )
  private val _e8 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E8, rules:R1,R2,R5, phase:69.1, slice:JM69-03L"
  )
  private val _e9 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E9, rules:R1,R3,R5, phase:69.1, slice:JM69-03L"
  )
  private val _e10 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E10, rules:R1,R3,R5, phase:69.1, slice:JM69-03L"
  )
  private val _e11 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E11, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e12 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E12, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e13 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E13, rules:R1,R2,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e14 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E14, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03N"
  )
  private val _e15 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E15, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e16 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E16, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03L"
  )
  private val _e17 = afterWord(
    "in spec:durable-job-write-bridge-contract, example:E17, rules:R1,R3,R4,R5,R6, phase:69.1, slice:JM69-03O"
  )
  "DurableJobLifecycleWriteBridge" should {
    "E1 checkpoint a completed asynchronous task after its revision-four TaskStartIntent" must _e1 {
      "when the initial asynchronous dispatch crosses the durable task boundary" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E1; a configured Persistent engine, closed task lifecycle evidence, and a task that observes durable state when it runs")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      var observedrunningintent = Option.empty[DurableJobStoreSnapshot]
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        Some(request => {
          observedrunningintent = _success(store.load(request.jobid.value, _access))
          Consequence.success(_evidence(4L, request.taskreadmodels.map(_task_descriptor)))
        })
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      var observed = Option.empty[DurableJobStoreSnapshot]
      var observedexecutionevents = Vector.empty[String]
      var observedtaskid = Option.empty[TaskId]
      val task = new RecordingTask(executioncontext => {
        executioncontext.jobContext.jobId.foreach { jobid =>
          observedtaskid = executioncontext.jobContext.taskId
          observedexecutionevents = engine.query(jobid).toVector.flatMap(
            _.timeline.events.map(_.kind)
          )
          observed = _success(store.load(jobid.value, _access)(using executioncontext))
        }
      })

      When("the Persistent asynchronous job is admitted and its one queued start is drained")
      val jobid = _success(engine.submit(List(task), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("durable snapshot is missing"))
      val canonical = _success(DurableJobRecordCodec.canonicalJson(stored.record))

      Then("the task observes revision four before its body and the completed local outcome is checkpointed at revision five")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      task.runCount shouldBe 1
      observed.map(_.record.body.identity.revision) shouldBe Some(4L)
      observed.map(_.record.body.lifecycle.status) shouldBe Some(DurableJobLifecycleStatus.Running)
      observedrunningintent.map(_.record.body.identity.revision) shouldBe Some(3L)
      observedrunningintent.toVector.flatMap(_.record.body.timeline).find(
        _.kind == "job.durable-running-intent"
      ).map(_.taskId) shouldBe Some(None)
      observedexecutionevents should contain("job.scheduler.started")
      observedexecutionevents should contain("job.running")
      stored.record.body.identity.revision shouldBe 6L
      stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
      stored.record.body.timeline
        .map(_.kind)
        .filter(kind => kind.startsWith("job.durable-") || kind.startsWith("task.durable-")) shouldBe Vector(
          "job.durable-start-intent",
          "job.durable-running-intent",
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint",
          "job.durable-terminal-outcome-checkpoint"
      )
      stored.record.body.timeline.last.taskId shouldBe None
      val retainedoutcome = stored.record.body.timeline.find(
        _.kind == "task.durable-outcome-checkpoint"
      ).getOrElse(fail("retained task outcome timeline event is missing"))
      retainedoutcome.taskId shouldBe observedtaskid.map(_.value)
      source.taskStartRequests.map(_.taskid) shouldBe observedtaskid.toVector
      source.taskStartEvidences.head.projection.tasks.map(_.taskId) shouldBe
        source.taskStartRequests.head.taskreadmodels.map(_.taskId.value)
      source.taskOutcomeRequests.map(_.completedtaskid) shouldBe observedtaskid.toVector
      source.taskOutcomeEvidences.head.projection.tasks.map(_.taskId) shouldBe
        source.taskOutcomeRequests.head.taskreadmodels.map(_.taskId.value)
      canonical should not include "RAW-TASK-BODY"
      canonical should not include "RAW-RESULT-BODY"
      engine.query(jobid).map(_.timeline.events.map(_.kind)) shouldBe defined
      engine.query(jobid).toVector.flatMap(_.timeline.events.map(_.kind)) should contain("job.scheduler.started")
      engine.shutdown()
      }
    }

    "E2 checkpoint a completed synchronous task after its revision-four TaskStartIntent" must _e2 {
      "when synchronous submission crosses the durable task boundary" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E2; a configured Persistent engine, closed task lifecycle evidence, and a task that observes durable state when it runs")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      var observedrunningintent = Option.empty[DurableJobStoreSnapshot]
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        Some(request => {
          observedrunningintent = _success(store.load(request.jobid.value, _access))
          Consequence.success(_evidence(4L, request.taskreadmodels.map(_task_descriptor)))
        })
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      var observed = Option.empty[DurableJobStoreSnapshot]
      var observedtaskid = Option.empty[TaskId]
      val task = new RecordingTask(executioncontext => {
        executioncontext.jobContext.jobId.foreach { jobid =>
          observedtaskid = executioncontext.jobContext.taskId
          observed = _success(store.load(jobid.value, _access)(using executioncontext))
        }
      })

      When("the Persistent synchronous job is admitted and started")
      val jobid = _success(engine.submit(
        List(task),
        context,
        JobSubmitOption(runMode = JobRunMode.Sync)
      ))
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("durable snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("job read model is missing"))

      Then("the task observes revision four and normal execution completes from the revision-five outcome checkpoint")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      observed.map(_.record.body.identity.revision) shouldBe Some(4L)
      observed.map(_.record.body.lifecycle.status) shouldBe Some(DurableJobLifecycleStatus.Running)
      observedrunningintent.map(_.record.body.identity.revision) shouldBe Some(3L)
      observedrunningintent.toVector.flatMap(_.record.body.timeline).find(
        _.kind == "job.durable-running-intent"
      ).map(_.taskId) shouldBe Some(None)
      stored.record.body.identity.revision shouldBe 6L
      stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
      stored.record.body.timeline
        .map(_.kind)
        .filter(kind => kind.startsWith("job.durable-") || kind.startsWith("task.durable-")) shouldBe Vector(
          "job.durable-start-intent",
          "job.durable-running-intent",
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint",
          "job.durable-terminal-outcome-checkpoint"
      )
      stored.record.body.timeline.last.taskId shouldBe None
      val retainedoutcome = stored.record.body.timeline.find(
        _.kind == "task.durable-outcome-checkpoint"
      ).getOrElse(fail("retained task outcome timeline event is missing"))
      retainedoutcome.taskId shouldBe observedtaskid.map(_.value)
      source.taskStartRequests.map(_.taskid) shouldBe observedtaskid.toVector
      source.taskStartEvidences.head.projection.tasks.map(_.taskId) shouldBe
        source.taskStartRequests.head.taskreadmodels.map(_.taskId.value)
      model.status shouldBe JobStatus.Succeeded
      model.timeline.events.map(_.kind) should contain("job.running")
      task.runCount shouldBe 1
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E3 advance two sequential task outcomes through contiguous durable revisions" must _e3 {
      "when normal tasks complete sequentially" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E3; a configured Persistent engine with two successful normal tasks and closed task lifecycle evidence")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val first = new RecordingTask(_ => ())
      val second = new RecordingTask(_ => (), relationkind = "child")

      When("the Persistent asynchronous job runs both tasks in order")
      val jobid = _success(engine.submit(List(first, second), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("outcome snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("runtime record is missing"))
      val canonical = _success(DurableJobRecordCodec.canonicalJson(stored.record))
      val taskids = model.tasks.tasks.map(_.taskId)

      Then("each post-body checkpoint extends the retained Running/Pending snapshot without raw result data")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      first.runCount shouldBe 1
      second.runCount shouldBe 1
      first.canonicalObservationCount shouldBe 1
      second.canonicalObservationCount shouldBe 1
      stored.record.body.identity.revision shouldBe 8L
      stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
      stored.record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
      stored.record.body.timeline
        .map(_.kind)
        .filter(_.startsWith("task.durable-")) shouldBe Vector(
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint",
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint"
        )
      source.taskStartRequests.map(_.taskreadmodels.map(_.taskId)) shouldBe
        Vector(taskids.take(1), taskids)
      source.taskOutcomeRequests.map(_.taskreadmodels.map(_.taskId)) shouldBe
        Vector(taskids.take(1), taskids)
      source.taskOutcomeRequests.map(_.completedtaskid) shouldBe taskids
      canonical should not include "RAW-TASK-BODY"
      canonical should not include "RAW-RESULT-BODY"
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E4 checkpoint a queued same-job task outcome after its revision-four TaskStartIntent" must _e4 {
      "when queued same-job admission crosses the durable task boundary" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E4; a configured Persistent engine with an established revision-three empty-job snapshot and a queued same-job task")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(_ => ())

      When("the same-job task is queued and drained")
      val jobid = _success(engine.submit(
        List.empty,
        context,
        JobSubmitOption(runMode = JobRunMode.Sync)
      ))
      _success(engine.enqueueTaskInJob(jobid, task, context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("outcome snapshot is missing"))

      Then("the queued body runs once and is followed by the closed outcome checkpoint")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint
      )
      task.runCount shouldBe 1
      task.canonicalObservationCount shouldBe 1
      stored.record.body.identity.revision shouldBe 5L
      stored.record.body.timeline.last.kind shouldBe "task.durable-outcome-checkpoint"
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E5 checkpoint a direct same-job task outcome after its revision-four TaskStartIntent" must _e5 {
      "when direct same-job admission crosses the durable task boundary" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E5; a configured Persistent engine with an established revision-three empty-job snapshot and a direct same-job task")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      var observed = Option.empty[DurableJobStoreSnapshot]
      var observedtaskid = Option.empty[TaskId]
      val task = new RecordingTask(executioncontext => {
        executioncontext.jobContext.jobId.foreach { jobid =>
          observedtaskid = executioncontext.jobContext.taskId
          observed = _success(store.load(jobid.value, _access)(using executioncontext))
        }
      })

      When("the empty Persistent job establishes revision three and a direct same-job task is run")
      val jobid = _success(engine.submit(
        List.empty,
        context,
        JobSubmitOption(runMode = JobRunMode.Sync)
      ))
      val outcome = engine.runTaskInJobSync(jobid, task, context)
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("task-start snapshot is missing"))

      Then("the direct same-job body observes revision four and its closed outcome checkpoint reaches revision five")
      outcome shouldBe a[Consequence.Success[_]]
      task.runCount shouldBe 1
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint
      )
      observed.map(_.record.body.identity.revision) shouldBe Some(4L)
      stored.record.body.identity.revision shouldBe 5L
      stored.record.body.timeline.last.kind shouldBe "task.durable-outcome-checkpoint"
      stored.record.body.timeline.last.taskId shouldBe observedtaskid.map(_.value)
      source.taskStartRequests.map(_.taskid) shouldBe observedtaskid.toVector
      source.taskStartEvidences.head.projection.tasks.map(_.taskId) shouldBe
        source.taskStartRequests.head.taskreadmodels.map(_.taskId.value)
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E6 checkpoint a normal task when local lifecycle time advances beyond retained durable time" must _e6 {
      "when local time advances after revision three" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E6; a configured Persistent engine whose local task lifecycle time advances after the revision-three durable checkpoint")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val timesource = new ManualJobTimeSource(_instant)
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val advancingsource: DurableJobLifecycleWriteEvidenceSource = request => {
        if (request.boundary == DurableJobLifecycleWriteBoundary.RunningIntent)
          timesource.advanceBy(Duration.ofMillis(5L))
        source(request)
      }
      val engine = _engine(timesource)
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, advancingsource))
      val task = new RecordingTask(_ => ())

      When("the initial normal JobRun registers and completes the task after local time advances")
      val jobid = _success(engine.submit(List(task), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("outcome snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("runtime record is missing"))
      val localtask = model.tasks.tasks.head

      Then("both durable task candidates project with contiguous revisions and a timestamp after the registered local lifecycle")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      stored.record.body.identity.revision shouldBe 6L
      stored.record.body.identity.updatedAt.isAfter(localtask.startedAt) shouldBe true
      stored.record.body.identity.updatedAt.isAfter(
        localtask.finishedAt.getOrElse(fail("local task finish is missing"))
      ) shouldBe true
      stored.record.body.timeline
        .map(_.kind)
        .filter(_.startsWith("task.durable-")) shouldBe Vector(
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint"
        )
      task.runCount shouldBe 1
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E7 checkpoint every in-process retry attempt through TaskStartIntent and TaskOutcomeCheckpoint" must _e7 {
      "when an immediate retry follows the initial durable task checkpoint" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E7; a configured Persistent engine with a task that requests immediate retry before succeeding")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(
        _ => (),
        outcomes = Some(attempt =>
          if (attempt == 1)
            TaskFailed(_retry_now_conclusion)
          else
            TaskSucceeded(OperationResponse.Scalar("RAW-RESULT-BODY"))
        )
      )

      When("the initial JobRun fails once and its immediate RetryRun completes")
      val jobid = _success(engine.submit(List(task), context))
      engine.drainAll() shouldBe 2
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("outcome snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("runtime record is missing"))
      val canonical = _success(DurableJobRecordCodec.canonicalJson(stored.record))
      val taskids = model.tasks.tasks.map(_.taskId)

      Then("the failed first attempt and committed final attempt retain ordered exact task-id pairs at terminal revision eight without raw execution data")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      taskids should have size 2
      taskids.distinct shouldBe taskids
      source.taskStartRequests.map(_.taskid) shouldBe taskids
      source.taskOutcomeRequests.map(_.completedtaskid) shouldBe taskids
      source.taskStartRequests.map(_.taskreadmodels.map(_.taskId)) shouldBe
        Vector(taskids.take(1), taskids)
      source.taskOutcomeRequests.map(_.taskreadmodels.map(_.taskId)) shouldBe
        Vector(taskids.take(1), taskids)
      stored.record.body.identity.revision shouldBe 8L
      stored.record.format shouldBe DurableRecordFormat.V2
      stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
      stored.record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
      stored.record.body.tasks.map(_.taskId) shouldBe taskids.map(_.value)
      stored.record.body.tasks.map(_.transaction.outcome) shouldBe Vector(
        DurableTransactionOutcome.Failed,
        DurableTransactionOutcome.Committed
      )
      stored.record.body.timeline
        .map(_.kind)
        .filter(_.startsWith("task.durable-")) shouldBe Vector(
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint",
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint"
        )
      stored.record.body.timeline
        .filter(_.kind.startsWith("task.durable-"))
        .flatMap(_.taskId) shouldBe taskids.flatMap(taskid => Vector(taskid.value, taskid.value))
      stored.record.body.timeline.map(_.kind) should contain(
        "job.durable-terminal-outcome-checkpoint"
      )
      task.runCount shouldBe 2
      model.status shouldBe JobStatus.Succeeded
      canonical should not include "RAW-TASK-BODY"
      canonical should not include "RAW-RESULT-BODY"
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E17 retain local public control Retry descendants without durable bridge writes" must _e17 {
      "when configured Persistent terminal failure and cancellation receive control Retry whose descendants request immediate or delayed retry" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E17; configured Persistent failed and cancelled terminal jobs with public control Retry and local RetryNow or RetryLater descendants")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())

      val failedsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _failed_terminal_result
      )
      val failedengine = _engine()
      failedengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, failedsource)
      )
      val failedtask = new RecordingTask(
        _ => (),
        outcomes = Some(attempt =>
          if (attempt == 1)
            TaskFailed(Consequence.stateInvalid[Nothing]("terminal task failed").conclusion)
          else if (attempt == 2)
            TaskFailed(_retry_now_conclusion)
          else
            TaskSucceeded(OperationResponse.Scalar("RAW-RESULT-BODY"))
        )
      )
      val failedid = _success(failedengine.submit(List(failedtask), context))
      failedengine.drainAll() shouldBe 1

      val cancelledsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _cancelled_terminal_result
      )
      val cancelledengine = _engine()
      cancelledengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, cancelledsource)
      )
      var cancelrequested = true
      val cancelledtask = new RecordingTask(executioncontext =>
        if (cancelrequested) {
          cancelrequested = false
          executioncontext.jobContext.jobId.foreach { jobid =>
            _success(cancelledengine.control(
              jobid,
              JobControlRequest(JobControlCommand.Cancel),
              _permissive_control_policy
            )(using executioncontext))
          }
        }
      )
      val cancelledid = _success(cancelledengine.submit(List(cancelledtask), context))
      cancelledengine.drainAll() shouldBe 1

      val delayedtime = new ManualJobTimeSource(_instant)
      val delayedtimer = new InMemoryJobEngine.ManualJobTimer(delayedtime)
      val delayedsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _failed_terminal_result
      )
      val delayedengine = new InMemoryJobEngine(
        retrySchedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(1L))),
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(autoStartWorkers = false),
        timeSource = delayedtime,
        timer = Some(delayedtimer)
      )(scala.concurrent.ExecutionContext.global)
      delayedengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, delayedsource)
      )
      val delayedtask = new RecordingTask(
        _ => (),
        outcomes = Some(attempt =>
          if (attempt == 1)
            TaskFailed(Consequence.stateInvalid[Nothing]("delayed terminal task failed").conclusion)
          else if (attempt == 2)
            TaskFailed(_retry_later_conclusion)
          else
            TaskSucceeded(OperationResponse.Scalar("RAW-RESULT-BODY"))
        )
      )
      val delayedid = _success(delayedengine.submit(List(delayedtask), context))
      delayedengine.drainAll() shouldBe 1

      When("public control Retry drains the terminal jobs and their immediate or delayed local retry descendants")
      _success(failedengine.control(
        failedid,
        JobControlRequest(JobControlCommand.Retry),
        _permissive_control_policy
      ))
      _success(cancelledengine.control(
        cancelledid,
        JobControlRequest(JobControlCommand.Retry),
        _permissive_control_policy
      ))
      failedengine.drainAll() shouldBe 2
      cancelledengine.drainAll() shouldBe 1
      _success(delayedengine.control(
        delayedid,
        JobControlRequest(JobControlCommand.Retry),
        _permissive_control_policy
      ))
      delayedengine.drainAll() shouldBe 1
      delayedtimer.advanceBy(Duration.ofMillis(1L)) shouldBe 1
      delayedengine.drainAll() shouldBe 1
      val failed = _success(store.load(failedid.value, _access)).getOrElse(
        fail("failed terminal snapshot is missing")
      )
      val cancelled = _success(store.load(cancelledid.value, _access)).getOrElse(
        fail("cancelled terminal snapshot is missing")
      )
      val delayed = _success(store.load(delayedid.value, _access)).getOrElse(
        fail("delayed terminal snapshot is missing")
      )

      Then("each control Retry and its descendants complete locally without a new durable task or terminal checkpoint against the retained terminal snapshot")
      val terminalboundaries = Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      failedsource.boundaries shouldBe terminalboundaries
      cancelledsource.boundaries shouldBe terminalboundaries
      delayedsource.boundaries shouldBe terminalboundaries
      failedtask.runCount shouldBe 3
      cancelledtask.runCount shouldBe 2
      delayedtask.runCount shouldBe 3
      failed.record.body.identity.revision shouldBe 6L
      cancelled.record.body.identity.revision shouldBe 6L
      delayed.record.body.identity.revision shouldBe 6L
      failed.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Failed
      cancelled.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Cancelled
      delayed.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Failed
      failedengine.getStatus(failedid) shouldBe Some(JobStatus.Succeeded)
      cancelledengine.getStatus(cancelledid) shouldBe Some(JobStatus.Succeeded)
      delayedengine.getStatus(delayedid) shouldBe Some(JobStatus.Succeeded)
      failedengine.durableLifecycleWriteFailureFacts shouldBe empty
      cancelledengine.durableLifecycleWriteFailureFacts shouldBe empty
      delayedengine.durableLifecycleWriteFailureFacts shouldBe empty
      failedengine.shutdown()
      cancelledengine.shutdown()
      delayedengine.shutdown()
      }
    }

    "E17 run a delayed retry rehydrated into a replacement engine without new bridge writes" must _e17 {
      "when a Persistent delayed retry survives only as runtime state and its replacement engine reaches the due instant" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E17; a configured Persistent delayed-retry record, its retained bridge snapshot, and a replacement manual scheduler")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val state = InMemoryJobEngine.State()
      val originaltime = new ManualJobTimeSource(_instant)
      val originaltimer = new InMemoryJobEngine.ManualJobTimer(originaltime)
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val bridge = new DurableJobLifecycleWriteBridge(store, source)
      val original = new InMemoryJobEngine(
        runtimeState = state,
        retrySchedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(1L))),
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(autoStartWorkers = false),
        timeSource = originaltime,
        timer = Some(originaltimer)
      )(scala.concurrent.ExecutionContext.global)
      original.bindDurableLifecycleWriteBridge(bridge)
      val task = new RecordingTask(
        _ => (),
        outcomes = Some(attempt =>
          if (attempt == 1)
            TaskFailed(_retry_later_conclusion)
          else
            TaskSucceeded(OperationResponse.Scalar("RAW-RESULT-BODY"))
        )
      )

      When("the first engine reaches the delayed retry boundary, then a replacement engine rehydrates and drains the due retry")
      val jobid = _success(original.submit(List(task), context))
      original.drainOne() shouldBe true
      val boundariesbeforereplacement = source.boundaries
      original.shutdown()
      val replacementtime = new ManualJobTimeSource(_instant)
      val replacementtimer = new InMemoryJobEngine.ManualJobTimer(replacementtime)
      val replacement = new InMemoryJobEngine(
        runtimeState = state,
        retrySchedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(1L))),
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(autoStartWorkers = false),
        timeSource = replacementtime,
        timer = Some(replacementtimer)
      )(scala.concurrent.ExecutionContext.global)
      replacement.bindDurableLifecycleWriteBridge(bridge)
      replacementtimer.advanceBy(Duration.ofMillis(1L)) shouldBe 1
      replacement.drainAll() shouldBe 1

      Then("the rehydrated body runs locally with no new bridge boundary or refusal")
      task.runCount shouldBe 2
      source.boundaries shouldBe boundariesbeforereplacement
      replacement.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      replacement.durableLifecycleWriteFailureFacts shouldBe empty
      replacement.shutdown()
      }
    }

    "E17 observe compensation only after the terminal checkpoint settles" must _e17 {
      "when a Persistent failure compensates after successful or refused terminal evidence" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E17; matching and incompatible terminal evidence for two locally compensated Persistent failures")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())

      val acceptedsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _failed_terminal_result
      )
      val acceptedengine = _engine()
      acceptedengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, acceptedsource)
      )
      val acceptedcompensation = new RecordingTask(_ => ())
      val acceptedfirst = new RecordingTask(
        _ => (),
        compensation = Some(acceptedcompensation)
      )
      val acceptedsecond = new RecordingTask(
        _ => (),
        outcome = TaskFailed(Consequence.stateInvalid[Nothing]("accepted terminal failure").conclusion),
        relationkind = "child"
      )

      val refusedsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val refusedengine = _engine()
      refusedengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, refusedsource)
      )
      val refusedcompensation = new RecordingTask(_ => ())
      val refusedfirst = new RecordingTask(
        _ => (),
        compensation = Some(refusedcompensation)
      )
      val refusedsecond = new RecordingTask(
        _ => (),
        outcome = TaskFailed(Consequence.stateInvalid[Nothing]("refused terminal failure").conclusion),
        relationkind = "child"
      )

      When("both jobs execute their committed task, failure, and local compensation through the terminal durable gate")
      val acceptedid = _success(acceptedengine.submit(List(acceptedfirst, acceptedsecond), context))
      acceptedengine.drainAll() shouldBe 1
      val refusedid = _success(refusedengine.submit(List(refusedfirst, refusedsecond), context))
      refusedengine.drainAll() shouldBe 1

      Then("only the accepted terminal checkpoint releases the compensation canonical observation, while both compensation bodies remain bridge-free")
      acceptedfirst.runCount shouldBe 1
      acceptedsecond.runCount shouldBe 1
      acceptedsource.boundaries.count(_ == DurableJobLifecycleWriteBoundary.TaskStartIntent) shouldBe 2
      acceptedsource.boundaries.count(_ == DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint) shouldBe 2
      acceptedengine.durableLifecycleWriteFailureFacts shouldBe empty
      acceptedsource.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      acceptedengine.getStatus(acceptedid) shouldBe Some(JobStatus.Failed)
      acceptedfirst.canonicalObservationCount shouldBe 1
      acceptedsecond.canonicalObservationCount shouldBe 1
      refusedfirst.runCount shouldBe 1
      refusedsecond.runCount shouldBe 1
      refusedsource.boundaries.count(_ == DurableJobLifecycleWriteBoundary.TaskStartIntent) shouldBe 2
      refusedsource.boundaries.count(_ == DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint) shouldBe 2
      acceptedcompensation.runCount shouldBe 1
      acceptedcompensation.canonicalObservationCount shouldBe 1
      refusedcompensation.runCount shouldBe 1
      refusedcompensation.canonicalObservationCount shouldBe 0
      refusedsource.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      refusedengine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(
          refusedid,
          DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
        )
      )
      refusedengine.getStatus(refusedid) shouldBe Some(JobStatus.Failed)
      acceptedengine.shutdown()
      refusedengine.shutdown()
      }
    }

    "E8 refuse configured Persistent admission before a local job, queue, event, or task is admitted" must _e8 {
      "when Admission refuses" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R2,R5; E8; a configured bridge whose closed Admission evidence refuses and one observable task")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.argumentInvalid("admission evidence refused"),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(_ => ())

      When("the Persistent asynchronous submission asks the bridge to admit the job")
      val submitted = engine.submit(List(task), context)

      Then("the original refusal is returned before local execution effects and the closed fact records only Admission refusal")
      submitted shouldBe a[Consequence.Failure[_]]
      source.boundaries shouldBe Vector(DurableJobLifecycleWriteBoundary.Admission)
      task.admissionFailureCount shouldBe 1
      task.runCount shouldBe 0
      engine.listJobs() shouldBe empty
      engine.drainAll() shouldBe 0
      engine.durableLifecycleWriteFailureFacts.map(_.failure) shouldBe
        Vector(DurableJobLifecycleWriteFailure.AdmissionRefused)
      engine.shutdown()
      }
    }

    "E9 refuse start intent before scheduler, running, or task effects while retaining the admitted submitted record" must _e9 {
      "when StartIntent refuses" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R5; E9; a configured bridge whose Admission evidence succeeds and whose StartIntent evidence refuses")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.argumentInvalid("start intent evidence refused"),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(_ => ())

      When("the admitted Persistent asynchronous job reaches its initial JobRun work item")
      val jobid = _success(engine.submit(List(task), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("admission snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("admitted runtime record is missing"))

      Then("the revision-one submitted record remains and normal execution transitions never occur")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent
      )
      stored.record.body.identity.revision shouldBe 1L
      stored.record.body.timeline.map(_.kind) should not contain "job.durable-start-intent"
      model.status shouldBe JobStatus.Submitted
      model.timeline.events.map(_.kind) should not contain "job.scheduler.started"
      model.timeline.events.map(_.kind) should not contain "job.running"
      task.admissionFailureCount shouldBe 0
      task.runCount shouldBe 0
      engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(jobid, DurableJobLifecycleWriteFailure.StartIntentRefused)
      )
      engine.shutdown()
      }
    }

    "E10 refuse RunningIntent before scheduler, running, or task effects while retaining revision two" must _e10 {
      "when RunningIntent refuses" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R5; E10; a configured bridge whose Admission and StartIntent evidence succeeds and whose RunningIntent evidence refuses")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.argumentInvalid("running intent evidence refused")
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(_ => ())

      When("the admitted Persistent asynchronous job reaches its initial JobRun work item")
      val jobid = _success(engine.submit(List(task), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("start-intent snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("admitted runtime record is missing"))

      Then("the revision-two Submitted record remains and normal execution transitions never occur")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent
      )
      stored.record.body.identity.revision shouldBe 2L
      stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Submitted
      stored.record.body.timeline.map(_.kind) should contain("job.durable-start-intent")
      stored.record.body.timeline.map(_.kind) should not contain "job.durable-running-intent"
      model.status shouldBe JobStatus.Submitted
      model.timeline.events.map(_.kind) should not contain "job.scheduler.started"
      model.timeline.events.map(_.kind) should not contain "job.running"
      task.admissionFailureCount shouldBe 0
      task.runCount shouldBe 0
      engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(jobid, DurableJobLifecycleWriteFailure.RunningIntentRefused)
      )
      engine.shutdown()
      }
    }

    "E11 refuse TaskStartIntent after local Running registration without a body, second task, or terminal durable result" must _e11 {
      "when TaskStartIntent refuses" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E11; a configured bridge whose Admission through RunningIntent evidence succeeds and whose closed TaskStartIntent evidence refuses")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        Some(_ => Consequence.argumentInvalid("task-start intent evidence refused"))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val first = new RecordingTask(_ => ())
      val second = new RecordingTask(_ => ())

      When("the admitted Persistent asynchronous job reaches its first locally registered task")
      val jobid = _success(engine.submit(List(first, second), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("running snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("runtime record is missing"))
      val later = new RecordingTask(_ => ())
      val direct = engine.runTaskInJobSync(jobid, later, context)
      val queued = engine.enqueueTaskInJob(jobid, later, context)
      val control = engine.control(
        jobid,
        JobControlRequest(JobControlCommand.Suspend),
        _permissive_control_policy
      )

      Then("the durable Store retains revision three and the refusal also fail-closes direct, queued, and control admission")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent
      )
      source.taskStartRequests should have size 1
      source.taskStartRequests.head.jobid shouldBe jobid
      source.taskStartRequests.head.taskreadmodels.map(_.taskId) shouldBe model.tasks.tasks.map(_.taskId)
      stored.record.body.identity.revision shouldBe 3L
      stored.record.body.result shouldBe DurableResultOutcome.Pending
      stored.record.body.timeline.map(_.kind) should not contain "task.durable-start-intent"
      first.runCount shouldBe 0
      second.runCount shouldBe 0
      later.runCount shouldBe 0
      direct shouldBe a[Consequence.Failure[_]]
      queued shouldBe a[Consequence.Failure[_]]
      control shouldBe a[Consequence.Failure[_]]
      engine.drainAll() shouldBe 0
      model.status shouldBe JobStatus.Running
      model.result shouldBe None
      model.tasks.tasks should have size 1
      model.timeline.events.map(_.kind) should not contain "task.transaction.committed"
      model.timeline.events.map(_.kind) should not contain "task.transaction.failed"
      model.timeline.events.map(_.kind) should not contain "job.succeeded"
      model.timeline.events.map(_.kind) should not contain "job.failed"
      engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(jobid, DurableJobLifecycleWriteFailure.TaskStartIntentRefused)
      )
      engine.shutdown()
      }
    }

    "E12 refuse a post-body task outcome checkpoint without follow-on task, compensation, terminal settlement, or canonical observation" must _e12 {
      "when TaskOutcomeCheckpoint refuses" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E12; a configured bridge whose closed TaskOutcomeCheckpoint evidence refuses after a failed task body")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        taskoutcome = Some(_ => Consequence.argumentInvalid("task outcome checkpoint refused"))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val first = new RecordingTask(
        _ => (),
        TaskFailed(Consequence.stateInvalid[Nothing]("task body failed").conclusion)
      )
      val second = new RecordingTask(_ => ())

      When("the first task has locally registered its failed completion and transaction before the Store refuses")
      val jobid = _success(engine.submit(List(first, second), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("task-start snapshot is missing"))
      val model = engine.query(jobid).getOrElse(fail("runtime record is missing"))
      val later = new RecordingTask(_ => ())
      val direct = engine.runTaskInJobSync(jobid, later, context)
      val queued = engine.enqueueTaskInJob(jobid, later, context)
      val control = engine.control(
        jobid,
        JobControlRequest(JobControlCommand.Suspend),
        _permissive_control_policy
      )

      Then("the prior durable task-start snapshot remains and the refusal also fail-closes direct, queued, and control admission")
      source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint
      )
      source.taskOutcomeRequests should have size 1
      source.taskOutcomeRequests.head.taskreadmodels.map(_.taskId) shouldBe model.tasks.tasks.map(_.taskId)
      stored.record.body.identity.revision shouldBe 4L
      stored.record.body.result shouldBe DurableResultOutcome.Pending
      stored.record.body.timeline.map(_.kind) should not contain "task.durable-outcome-checkpoint"
      first.runCount shouldBe 1
      second.runCount shouldBe 0
      later.runCount shouldBe 0
      direct shouldBe a[Consequence.Failure[_]]
      queued shouldBe a[Consequence.Failure[_]]
      control shouldBe a[Consequence.Failure[_]]
      engine.drainAll() shouldBe 0
      first.canonicalObservationCount shouldBe 0
      second.canonicalObservationCount shouldBe 0
      model.status shouldBe JobStatus.Running
      model.result shouldBe None
      model.timeline.events.map(_.kind) should contain("task.transaction.failed")
      model.timeline.events.map(_.kind) should not contain "job.failed"
      model.timeline.events.map(_.kind) should not contain "job.succeeded"
      engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(jobid, DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused)
      )
      engine.shutdown()
      }
    }

    "E13 bypass the configured bridge for Ephemeral work without exposing a durable capability" must _e13 {
      "when Ephemeral work runs synchronously" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R2,R6; E13; a configured bridge with recording closed evidence and one Ephemeral synchronous task")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(_ => ())

      When("the Ephemeral job is submitted synchronously")
      val jobid = _success(engine.submit(
        List(task),
        context,
        JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)
      ))

      Then("the task runs under existing semantics without a durable create, checkpoint, or failure fact")
      source.boundaries shouldBe empty
      task.runCount shouldBe 1
      _success(store.load(jobid.value, _access)) shouldBe None
      engine.durableLifecycleWriteFailureFacts shouldBe empty
      engine.shutdown()
      }
    }

    "E16 refuse a terminal checkpoint without advancing the retained snapshot or observing the terminal outcome" must _e16 {
      "when terminal evidence disagrees with a locally settled successful Persistent job" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E16; a configured Persistent engine with closed task evidence and incompatible terminal result evidence")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())
      val source = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _failed_terminal_result
      )
      val engine = _engine()
      engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, source))
      val task = new RecordingTask(_ => ())

      When("the final local success reaches the closed terminal durable gate")
      val jobid = _success(engine.submit(List(task), context))
      engine.drainAll() shouldBe 1
      val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("nonterminal snapshot is missing"))

      Then("the retained Running/Pending snapshot remains authoritative and no canonical task outcome is observed")
      source.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      stored.record.body.identity.revision shouldBe 5L
      stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
      stored.record.body.result shouldBe DurableResultOutcome.Pending
      task.canonicalObservationCount shouldBe 0
      engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(
          jobid,
          DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
        )
      )
      engine.shutdown()
      }
    }

    "E15 checkpoint configured Persistent success, terminal failure, and cancellation before fresh-engine terminal recovery" must _e15 {
      "when three actual engine paths settle with closed terminal evidence" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E15; one shared durable store, three configured Persistent engines, and closed success, failure, and cancellation evidence")
      val context = ExecutionContext.test()
      given ExecutionContext = context
      val store = new DurableJobStore(EntityStore.standard())

      val succeededsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L))
      )
      val succeededengine = _engine()
      succeededengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, succeededsource)
      )
      val succeededid = _success(succeededengine.submit(List(new RecordingTask(_ => ())), context))
      succeededengine.drainAll() shouldBe 1

      val failedsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _failed_terminal_result
      )
      val failedengine = _engine()
      failedengine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, failedsource))
      val failedid = _success(failedengine.submit(
        List(new RecordingTask(
          _ => (),
          TaskFailed(Consequence.stateInvalid[Nothing]("terminal task failed").conclusion)
        )),
        context
      ))
      failedengine.drainAll() shouldBe 1

      val cancelledsource = new EvidenceSource(
        Consequence.success(_evidence(1L)),
        Consequence.success(_evidence(2L)),
        Consequence.success(_evidence(3L)),
        terminalresult = _cancelled_terminal_result
      )
      val cancelledeventstore = EventStore.inMemory
      val cancelledengine = _engine().withEventStore(cancelledeventstore)
      cancelledengine.bindDurableLifecycleWriteBridge(
        new DurableJobLifecycleWriteBridge(store, cancelledsource)
      )
      val cancelledtask = new RecordingTask(executioncontext =>
        executioncontext.jobContext.jobId.foreach { jobid =>
          _success(cancelledengine.control(
            jobid,
            JobControlRequest(JobControlCommand.Cancel),
            _permissive_control_policy
          )(using executioncontext))
        }
      )
      val cancelledid = _success(cancelledengine.submit(List(cancelledtask), context))
      cancelledengine.drainAll() shouldBe 1

      When("a newly created engine receives only the three closed terminal recovery candidates")
      val freshengine = _engine()
      val report = _success(freshengine.recoverDurableTerminalFacts(
        _recovery_source(Vector(succeededid, failedid, cancelledid)),
        store,
        3
      ))
      val succeeded = _success(store.load(succeededid.value, _access)).getOrElse(fail("success snapshot is missing"))
      val failed = _success(store.load(failedid.value, _access)).getOrElse(fail("failure snapshot is missing"))
      val cancelled = _success(store.load(cancelledid.value, _access)).getOrElse(fail("cancellation snapshot is missing"))
      val cancelledevents = cancelledeventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)

      Then("each real settlement leaves a V2 terminal record and fresh recovery registers only non-executable terminal facts")
      Vector(succeeded, failed, cancelled).foreach { snapshot =>
        snapshot.record.format shouldBe DurableRecordFormat.V2
      }
      succeeded.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
      failed.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Failed
      cancelled.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Cancelled
      cancelled.record.body.identity.revision shouldBe 6L
      succeeded.record.body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
      failed.record.body.result shouldBe _failed_terminal_result
      cancelled.record.body.result shouldBe _cancelled_terminal_result
      succeededsource.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      failedsource.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      cancelledsource.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      cancelledsource.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      cancelledsource.taskStartEvidences.map(_.projection.semanticRevision) shouldBe Vector(4L)
      cancelledsource.taskOutcomeEvidences.map(_.projection.semanticRevision) shouldBe Vector(5L)
      cancelled.record.body.timeline
        .map(_.kind)
        .filter(_.startsWith("task.durable-")) shouldBe Vector(
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint"
        )
      cancelledevents.count(_.name == "job.cancelled") shouldBe 1
      cancelledevents.lastOption.map(_.name) shouldBe Some("job.cancelled")
      report.candidates.map(_.fact) shouldBe Vector.fill(3)(DurableJobTerminalProjectionFact.Registered)
      freshengine.getStatus(succeededid) shouldBe Some(JobStatus.Succeeded)
      freshengine.getStatus(failedid) shouldBe Some(JobStatus.Failed)
      freshengine.getStatus(cancelledid) shouldBe Some(JobStatus.Cancelled)
      freshengine.runtimeState.durableJobs.isEmpty shouldBe true
      freshengine.runtimeState.runtimeJobs.isEmpty shouldBe true
      freshengine.drainAll() shouldBe 0
      succeededengine.shutdown()
      failedengine.shutdown()
      cancelledengine.shutdown()
      freshengine.shutdown()
      }
    }

    "E14 property-check local-time advancement with normal and immediate-retry dispatch" must _e14 {
      "when ScalaCheck varies post-revision-three local time and retry mode" in {
      Given("docs/spec/durable-job-write-bridge-contract.md; R1,R3,R4,R5,R6; E14; generated positive local-time advances and normal or immediate-retry Persistent task dispatch")
      val property = Prop.forAll(_lifecycle_parameters) { case (advance, retry) =>
        val context = ExecutionContext.test()
        given ExecutionContext = context
        val timesource = new ManualJobTimeSource(_instant)
        val store = new DurableJobStore(EntityStore.standard())
        val source = new EvidenceSource(
          Consequence.success(_evidence(1L)),
          Consequence.success(_evidence(2L)),
          Consequence.success(_evidence(3L))
        )
        val advancingsource: DurableJobLifecycleWriteEvidenceSource = request => {
          if (request.boundary == DurableJobLifecycleWriteBoundary.RunningIntent)
            timesource.advanceBy(Duration.ofMillis(advance))
          source(request)
        }
        val engine = _engine(timesource)
        engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, advancingsource))
        val task = new RecordingTask(
          _ => (),
          outcomes = Option.when(retry)(attempt =>
            if (attempt == 1)
              TaskFailed(_retry_now_conclusion)
            else
              TaskSucceeded(OperationResponse.Scalar("RAW-RESULT-BODY"))
          )
        )

        When("the generated Persistent job drains its normal run and optional immediate retry")
        val jobid = _success(engine.submit(List(task), context))
        val drains = engine.drainAll()
        val stored = _success(store.load(jobid.value, _access)).getOrElse(fail("outcome snapshot is missing"))
        val model = engine.query(jobid).getOrElse(fail("runtime record is missing"))
        val taskids = model.tasks.tasks.map(_.taskId)
        val initialtask = model.tasks.tasks.head
        val taskevents = stored.record.body.timeline.map(_.kind).filter(_.startsWith("task.durable-"))
        val canonical = _success(DurableJobRecordCodec.canonicalJson(stored.record))
        val attempts = if (retry) 2 else 1
        val expectedtaskvectors =
          if (retry) Vector(taskids.take(1), taskids) else Vector(taskids)
        val expectedtaskboundaries = Vector.fill(attempts)(Vector(
          DurableJobLifecycleWriteBoundary.TaskStartIntent,
          DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint
        )).flatten
        val expectedtaskevents = Vector.fill(attempts)(Vector(
          "task.durable-start-intent",
          "task.durable-outcome-checkpoint"
        )).flatten
        val expectedtaskids = taskids.flatMap(taskid => Vector(taskid.value, taskid.value))
        val expectedtransactionoutcomes =
          if (retry)
            Vector(DurableTransactionOutcome.Failed, DurableTransactionOutcome.Committed)
          else
            Vector(DurableTransactionOutcome.Committed)

        Then("each generated normal run has one exact task-id pair while retry history retains ordered failed-first and committed-last pairs without raw execution data")
        val result =
          drains == (if (retry) 2 else 1) &&
            stored.record.body.identity.revision == (if (retry) 8L else 6L) &&
            stored.record.body.lifecycle.status == DurableJobLifecycleStatus.Succeeded &&
            stored.record.body.result == DurableResultOutcome.Succeeded(DurableValue.Absent) &&
            stored.record.body.tasks.map(_.transaction.outcome) == expectedtransactionoutcomes &&
            stored.record.body.identity.updatedAt.isAfter(initialtask.startedAt) &&
            stored.record.body.identity.updatedAt.isAfter(
              initialtask.finishedAt.getOrElse(fail("initial task finish is missing"))
            ) &&
            source.boundaries == (Vector(
              DurableJobLifecycleWriteBoundary.Admission,
              DurableJobLifecycleWriteBoundary.StartIntent,
              DurableJobLifecycleWriteBoundary.RunningIntent
            ) ++ expectedtaskboundaries :+ DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint) &&
            taskevents == expectedtaskevents &&
            stored.record.body.timeline.filter(_.kind.startsWith("task.durable-")).flatMap(_.taskId) == expectedtaskids &&
            source.taskStartRequests.map(_.taskid) == taskids &&
            source.taskOutcomeRequests.map(_.completedtaskid) == taskids &&
            source.taskStartRequests.map(_.taskreadmodels.map(_.taskId)) == expectedtaskvectors &&
            source.taskOutcomeRequests.map(_.taskreadmodels.map(_.taskId)) == expectedtaskvectors &&
            canonical.contains("RAW-TASK-BODY") == false &&
            canonical.contains("RAW-RESULT-BODY") == false &&
            task.runCount == (if (retry) 2 else 1)
        engine.shutdown()
        result
      }

      When("ScalaCheck evaluates the bounded lifecycle bridge property")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(16), property)

      Then("all generated normal and immediate-retry dispatches satisfy the frozen durable bridge invariant")
      checked.passed shouldBe true
      }
    }
  }

  private val _instant = Instant.parse("2026-09-09T01:02:03Z")
  private val _digest = "a" * 64
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _failed_terminal_result = DurableResultOutcome.Failed(
    DurableFailureSummary("execution", "failed", "failed", retryable = false)
  )
  private val _cancelled_terminal_result = DurableResultOutcome.Cancelled(Some(
    DurableFailureSummary("control", "cancelled", "cancelled", retryable = false)
  ))
  private val _lifecycle_parameters = for {
    advance <- Gen.choose(1L, 24L)
    retry <- Gen.oneOf(true, false)
  } yield (advance, retry)
  private val _permissive_control_policy = new JobControlPolicy {
    def authorize(
      jobId: JobId,
      request: JobControlRequest
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.unit
    }
  }

  private final class EvidenceSource(
    admission: Consequence[DurableJobLifecycleWriteEvidence],
    startintent: Consequence[DurableJobLifecycleWriteEvidence],
    runningintent: Consequence[DurableJobLifecycleWriteEvidence],
    taskstart: Option[DurableJobLifecycleWriteRequest.TaskStartIntent =>
      Consequence[DurableJobLifecycleWriteEvidence]] = None,
    taskoutcome: Option[DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint =>
      Consequence[DurableJobLifecycleWriteEvidence]] = None,
    terminalresult: DurableResultOutcome = DurableResultOutcome.Succeeded(DurableValue.Absent)
  ) extends (DurableJobLifecycleWriteRequest => Consequence[DurableJobLifecycleWriteEvidence]) {
    private var _requests = Vector.empty[DurableJobLifecycleWriteRequest]
    private var _taskstartevidences = Vector.empty[DurableJobLifecycleWriteEvidence]
    private var _taskoutcomeevidences = Vector.empty[DurableJobLifecycleWriteEvidence]

    def apply(request: DurableJobLifecycleWriteRequest): Consequence[DurableJobLifecycleWriteEvidence] = {
      _requests :+= request
      val result = request match {
        case DurableJobLifecycleWriteRequest.Admission => admission
        case DurableJobLifecycleWriteRequest.StartIntent => startintent
        case DurableJobLifecycleWriteRequest.RunningIntent => runningintent
        case taskstartrequest: DurableJobLifecycleWriteRequest.TaskStartIntent =>
          taskstart.map(_(taskstartrequest)).getOrElse(_checkpoint_evidence(taskstartrequest.taskreadmodels))
        case taskoutcomerequest: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint =>
          taskoutcome
            .map(_(taskoutcomerequest))
            .getOrElse(_checkpoint_evidence(taskoutcomerequest.taskreadmodels))
        case terminalrequest: DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint =>
          _terminal_evidence(terminalrequest.taskreadmodels, terminalresult)
      }
      request match {
        case _: DurableJobLifecycleWriteRequest.TaskStartIntent =>
          result.toOption.foreach(value => _taskstartevidences :+= value)
        case _: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint =>
          result.toOption.foreach(value => _taskoutcomeevidences :+= value)
        case _ => ()
      }
      result
    }

    def boundaries: Vector[DurableJobLifecycleWriteBoundary] = _requests.map(_.boundary)

    def taskStartRequests: Vector[DurableJobLifecycleWriteRequest.TaskStartIntent] =
      _requests.collect { case request: DurableJobLifecycleWriteRequest.TaskStartIntent => request }

    def taskStartEvidences: Vector[DurableJobLifecycleWriteEvidence] = _taskstartevidences

    def taskOutcomeRequests: Vector[DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint] =
      _requests.collect { case request: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint => request }

    def taskOutcomeEvidences: Vector[DurableJobLifecycleWriteEvidence] = _taskoutcomeevidences

    private def _checkpoint_evidence(
      taskreadmodels: Vector[JobTaskReadModel]
    ): Consequence[DurableJobLifecycleWriteEvidence] =
      Consequence.success(_evidence(_requests.size.toLong, taskreadmodels.map(_task_descriptor)))

    private def _terminal_evidence(
      taskreadmodels: Vector[JobTaskReadModel],
      result: DurableResultOutcome
    ): Consequence[DurableJobLifecycleWriteEvidence] =
      Consequence.success(_evidence(
        _requests.size.toLong,
        taskreadmodels.map(_terminal_task_descriptor),
        result,
        _terminal_retry(result)
      ))

  }

  private final class RecordingTask(
    onrun: ExecutionContext => Unit,
    outcome: TaskOutcome = TaskSucceeded(OperationResponse.Scalar("RAW-RESULT-BODY")),
    relationkind: String = "root",
    outcomes: Option[Int => TaskOutcome] = None,
    compensation: Option[JobTask] = None,
    compensationref: Option[String] = None
  ) extends JobTask {
    val actionId: ActionId =
      ActionId("cncf", "action", Some(_instant), Some("RAW-TASK-BODY"))
    var runCount: Int = 0
    var admissionFailureCount: Int = 0
    var canonicalObservationCount: Int = 0

    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some(relationkind)
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def compensationTask: Option[JobTask] = compensation
    override def compensationActionRef: Option[String] = compensationref
    override def componentName: Option[String] = Some("component-a")
    override def operationName: Option[String] = Some("run")

    def run(ctx: ExecutionContext): TaskOutcome = {
      runCount += 1
      onrun(ctx)
      outcomes.map(_(runCount)).getOrElse(outcome)
    }

    override def observeAdmissionFailure(
      conclusion: org.goldenport.Conclusion,
      ctx: ExecutionContext
    ): Unit =
      admissionFailureCount += 1

    override def observeCanonicalOutcome(
      outcome: TaskOutcome,
      ctx: ExecutionContext,
      cancelled: Boolean
    ): Unit =
      val _ = outcome
      val _ = ctx
      val _ = cancelled
      canonicalObservationCount += 1
  }

  private def _engine(timesource: JobTimeSource = new ManualJobTimeSource(_instant)): InMemoryJobEngine =
    new InMemoryJobEngine(
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(autoStartWorkers = false),
      timeSource = timesource
    )(scala.concurrent.ExecutionContext.global)

  private def _retry_now_conclusion: Conclusion =
    Conclusion.simple("retry now").copy(disposition = Disposition(Disposition.UserAction.RetryNow))

  private def _retry_later_conclusion: Conclusion =
    Conclusion.simple("retry later").copy(disposition = Disposition(Disposition.UserAction.RetryLater))

  private def _terminal_retry(result: DurableResultOutcome): DurableRetryEvidence = {
    val outcome = result match {
      case DurableResultOutcome.Succeeded(_) => DurableAttemptOutcome.Succeeded
      case DurableResultOutcome.Failed(_) => DurableAttemptOutcome.Failed
      case DurableResultOutcome.Cancelled(_) => DurableAttemptOutcome.Cancelled
      case DurableResultOutcome.Pending => fail("terminal evidence cannot be pending")
    }
    val failure = result match {
      case DurableResultOutcome.Failed(summary) => Some(summary)
      case _ => None
    }
    DurableRetryEvidence(
      Vector(DurableAttemptEvidence(1, _instant, Some(_instant.plusMillis(1L)), outcome, failure)),
      3,
      None,
      exhausted = false,
      recoveryRequired = outcome == DurableAttemptOutcome.Failed
    )
  }

  private def _recovery_source(jobids: Vector[JobId]): DurableJobStartupRecoverySource =
    new DurableJobStartupRecoverySource {
      def candidates(
        maxCandidates: Int
      ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
        val entries = jobids.zipWithIndex.map { case (jobid, index) =>
          DurableJobStartupRecoveryCandidate(
            index.toLong,
            jobid.value,
            _access,
            DurableReplayEvidence(
              idempotency = Some("idempotency-proof"),
              input = Some("input-proof"),
              definition = Some("definition-proof"),
              authorization = Some("authorization-proof"),
              provider = Some("provider-proof"),
              compatibility = Some("compatibility-proof")
            )
          )
        }
        Consequence.success(entries.take(maxCandidates))
      }
    }

  private def _evidence(
    revision: Long,
    tasks: Vector[DurableTaskDescriptor] = Vector(_closed_task_descriptor),
    result: DurableResultOutcome = DurableResultOutcome.Pending,
    retry: DurableRetryEvidence = DurableRetryEvidence(
      Vector.empty,
      3,
      None,
      exhausted = false,
      recoveryRequired = false
    )
  ): DurableJobLifecycleWriteEvidence = {
    DurableJobLifecycleWriteEvidence(
      projection = DurableJobProjectionEvidence(
        semanticRevision = revision,
        authorization = DurableJobAuthorization(
          "tenant-a",
          DurableSubject("subject-a", "user"),
          DurableVisibility.Subject,
          Set("job.read")
        ),
        tasks = tasks,
        inputs = Vector.empty,
        result = result,
        retry = retry,
        diagnostics = Vector(DurableDiagnosticSummary("admission", "accepted", "info", "closed evidence")),
        calltreeReference = None,
        definitionSnapshot = DurableDefinitionSnapshot(
          "definition-001",
          "durable-job",
          1,
          1L,
          _digest,
          None,
          None,
          Map.empty
        ),
        retention = DurableRetentionState(
          Some(_instant.plusSeconds(86400L)),
          None,
          None,
          DurableDeletionState.Active,
          None
        )
      ),
      access = _access
    )
  }

  private val _closed_task_descriptor = DurableTaskDescriptor(
    taskId = "closed-task-001",
    parentTaskId = None,
    kind = DurableTaskKind.Operation,
    target = DurableTaskTarget(
      "operation",
      DurableOperationReference("component-a", None, "run", None)
    ),
    relation = DurableTaskRelation(DurableTaskRelationKind.Root, None),
    transaction = DurableTransactionDescriptor(
      DurableTransactionRole.Own,
      DurableTransactionScope.PerTask,
      DurableTransactionOutcome.Pending
    ),
    compensation = None
  )

  private def _task_descriptor(model: JobTaskReadModel): DurableTaskDescriptor =
    DurableTaskDescriptor(
      taskId = model.taskId.value,
      parentTaskId = model.parentTaskId.map(_.value),
      kind = DurableTaskKind.parse(model.taskKind).fold(message => fail(message), identity),
      target = DurableTaskTarget(
        model.targetKind.getOrElse(fail("task target kind is missing")),
        DurableOperationReference(
          model.component.getOrElse(fail("task component is missing")),
          model.service,
          model.operation.getOrElse(fail("task operation is missing")),
          None
        )
      ),
      relation = DurableTaskRelation(
        DurableTaskRelationKind.parse(model.relation.getOrElse(
          fail("task relation is missing")
        )).fold(message => fail(message), identity),
        model.parentTaskId.map(_.value)
      ),
      transaction = DurableTransactionDescriptor(
        DurableTransactionRole.parse(model.transactionRole.getOrElse(
          fail("task transaction role is missing")
        )).fold(message => fail(message), identity),
        DurableTransactionScope.parse(model.transactionScope.getOrElse(
          fail("task transaction scope is missing")
        )).fold(message => fail(message), identity),
        model.transactionOutcome match {
          case Some("running") => DurableTransactionOutcome.Pending
          case Some(value) => DurableTransactionOutcome.parse(value).fold(message => fail(message), identity)
          case None => fail("task transaction outcome is missing")
        }
      ),
      compensation = None
    )

  private def _terminal_task_descriptor(model: JobTaskReadModel): DurableTaskDescriptor = {
    val normalizedmodel =
      if (model.relation.contains("compensation"))
        model.copy(transactionOutcome = model.transactionOutcome.map {
          case "compensation-committed" | "compensation-failed" => "compensated"
          case value => value
        })
      else
        model
    _task_descriptor(normalizedmodel).copy(
      compensation =
        if (model.relation.contains("compensation"))
          Some(DurableCompensationDescriptor(
            DurableOperationReference(
              model.component.getOrElse(fail("compensation task component is missing")),
              model.service,
              model.compensationActionRef.filter(_.trim.nonEmpty).getOrElse(
                fail("compensation task action is missing")
              ),
              None
            ),
            model.compensatesTaskId.map(_.value).filter(_.trim.nonEmpty).getOrElse(
              fail("compensation task target is missing")
            ),
            model.compensationStatus.filter(_.trim.nonEmpty).map(
              DurableCompensationStatus.parse(_).fold(message => fail(message), identity)
            ).getOrElse(fail("compensation task status is missing")),
            model.compensationFailureSummary.map { summary =>
              if (summary.trim.nonEmpty)
                DurableFailureSummary("compensation", "failed", summary, retryable = false)
              else
                fail("compensation task failure summary is blank")
            }
          ))
        else
          None
    )
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail("expected Consequence.Success"))
}
