package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import java.time.Duration
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import JobDurableRuntimeFixtures.success
import JobRetryCrossProcessFixtures.Fixture

/*
 * Real retry settlement precedes independent terminal or safe refused startup.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object JobRetryCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    split: Int, id: Option[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var closure = Map.empty[String, String]
    try {
      facts = arguments.mode match {
        case "immediate-writer" | "delayed-writer" | "pending-writer" | "pending-crash-writer" =>
          _writer(fixture, arguments)
        case "terminal-reader" => _terminal_reader(fixture, arguments.id.get)
        case "refused-reader" => _refused_reader(fixture, arguments.id.get)
        case _ => throw new IllegalArgumentException("closed retry mode required")
      }
    } finally closure = fixture.close()
    JobRetryCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ _identity(arguments) ++ closure)
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length == 4 || args.length == 5, "retry probe requires mode/root/receipt/split and reader ID")
    val mode = args(0)
    require(JobRetryCrossProcessFixtures.modes.contains(mode), "closed six-mode retry probe required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-retry-cross-process" &&
      root.getParent.getParent.getParent.getParent.getFileName.toString == "target",
      "owned absolute retry native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") &&
      receipt.getFileName.toString == s"$mode.receipt" && !Files.exists(receipt),
      "new owned retry receipt required")
    val split = args(3).toInt
    require(split >= 1 && split <= 59, "retry split must be independently selected in 1..59")
    val id = args.drop(4).headOption.map(value => success(JobId.parse(value)))
    require(if (mode.endsWith("writer")) id.isEmpty else id.nonEmpty, "writer/reader ID arguments must agree")
    Arguments(mode, root, receipt, split, id)
  }

  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    success(fixture.start())
    fixture.original.owner.health.state shouldBe JobDurableOperationalState.Ready
    fixture.engine.retrySchedule shouldBe InMemoryJobEngine.RetrySchedule.default
    fixture.engine.retrySchedule.maxRetries shouldBe 3
    fixture.engine.retrySchedule.delayedRetryDelays shouldBe Vector(
      Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))
    val immediate = arguments.mode == "immediate-writer"
    val task = new JobDurableLifetimeRecoveryFixtures.Task
    task.body = _ => {
      if (task.runs.get() == 1) TaskFailed(Conclusion.simple(if (immediate) "retry now" else "retry later")
        .copy(disposition = Disposition(if (immediate) Disposition.UserAction.RetryNow else Disposition.UserAction.RetryLater)))
      else TaskSucceeded(OperationResponse.Scalar("ok"))
    }
    val id = fixture.submit(task)
    val first = _drain(fixture)
    if (immediate) first shouldBe 2
    else {
      first shouldBe 1
      _pending(fixture, id)
      fixture.engine.getStatus(id) shouldBe Some(JobStatus.Submitted)
      fixture.engine.getResult(id) shouldBe None
      val live = fixture.engine.query(id).get
      live.retry.kind shouldBe JobRetryKind.Delayed
      live.retry.attemptCount shouldBe 1
      live.retry.nextRetryDueAt shouldBe Some(JobDurableRuntimeFixtures.instant.plusSeconds(60))
      fixture.engine.resourceSnapshot.pending shouldBe 1
      fixture.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get
        .pendingTimerCount should be >= 1
      fixture.engine._work_queue.size() shouldBe 0
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 1
      _requests(fixture, id, 5)
      _task_pairs(fixture, id, 1)
      fixture.engine.durableLifecycleWriteFailureFacts shouldBe empty
      if (arguments.mode == "pending-writer" || arguments.mode == "pending-crash-writer") {
        val facts = _facts(fixture, id, Some(task), 1, "", false)
        if (arguments.mode == "pending-crash-writer") _halt(arguments, facts)
        return facts
      }
      val before = fixture.rows(id)
      val mutations = fixture.mutationCounts
      fixture.original.advance(Duration.ofSeconds(arguments.split.toLong))
      fixture.engine._now() shouldBe JobDurableRuntimeFixtures.instant.plusSeconds(arguments.split.toLong)
      _drain(fixture) shouldBe 0
      fixture.rows(id) shouldBe before
      fixture.mutationCounts shouldBe mutations
      _requests(fixture, id, 5)
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 1
      fixture.original.advance(Duration.ofSeconds(59L - arguments.split.toLong))
      fixture.engine._now() shouldBe JobDurableRuntimeFixtures.instant.plusSeconds(59)
      _drain(fixture) shouldBe 0
      fixture.rows(id) shouldBe before
      fixture.mutationCounts shouldBe mutations
      _requests(fixture, id, 5)
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 1
      _pending(fixture, id)
      fixture.original.advance(Duration.ofSeconds(1))
      fixture.engine._now() shouldBe JobDurableRuntimeFixtures.instant.plusSeconds(60)
      _drain(fixture) shouldBe 1
    }
    fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
    fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
    val live = fixture.engine.query(id).get
    live.retry.attemptCount shouldBe 1
    val taskids = fixture.evidence.requests.map(_.request).collect {
      case request: DurableJobLifecycleWriteRequest.TaskStartIntent => request.taskid.value
    }
    taskids.size shouldBe 2
    taskids.distinct shouldBe taskids
    live.tasks.totalCount shouldBe 2
    live.tasks.fetchedCount shouldBe 2
    live.tasks.tasks.size shouldBe 2
    // Presentation sorts equal start times by task ID; exact identity correspondence
    // is checked here while signed history chronology is checked separately.
    live.tasks.tasks.map(model => model.taskId.value -> model.status).toMap shouldBe
      taskids.zip(Vector(JobTaskStatus.Failed, JobTaskStatus.Succeeded)).toMap
    task.runs.get() shouldBe 2
    task.canonical.get() shouldBe 2
    fixture.original.port.calls.get() shouldBe 0
    _requests(fixture, id, 8)
    _task_pairs(fixture, id, 2)
    _terminal(fixture, id)
    fixture.engine.durableLifecycleWriteFailureFacts shouldBe empty
    fixture.nativeWrites should be > 0
    fixture.entity.lifeWrites.get() should be > 0
    _facts(fixture, id, Some(task), 2, "", false)
  }

  private def _drain(fixture: Fixture): Int =
    success(fixture.original.owner._execute_c { Consequence.success(fixture.engine.drainAll()) })

  private def _terminal_reader(fixture: Fixture, id: JobId): Map[String, String] = {
    fixture.original.startup.entries = Vector(JobDurableRuntimeFixtures.candidate(id))
    var before = Vector.empty[Option[Record]]
    fixture.original.startup.before = () => before = fixture.rows(id)
    success(fixture.start())
    fixture.original.owner.health.state shouldBe JobDurableOperationalState.Ready
    fixture.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Registered)
    fixture.engine.durableRuntimeRehydrationReport.get.candidates.map(_.fact) should not contain DurableJobRuntimeRehydrationFact.Registered
    fixture.original.tasks.isEmpty shouldBe true
    val query = fixture.engine.query(id).get
    query.status shouldBe JobStatus.Succeeded
    query.origin shouldBe JobDataOrigin.Durable
    query.persistence shouldBe JobPersistencePolicy.Persistent
    query.input shouldBe None
    query.calltree shouldBe None
    query.result shouldBe None
    fixture.engine.getResult(id) shouldBe None
    success(fixture.engine.queryManagementResult(id)(using fixture.managementContext)) shouldBe
      Some(JobManagementResult.UnavailableAfterRestart(query.resultSummary))
    _terminal(fixture, id)
    _zero_reader(fixture, id, before)
    val facts = _facts(fixture, id, None, 0, "", fixture.rows(id) == before)
    fixture.rows(id) shouldBe before
    facts
  }

  private def _refused_reader(fixture: Fixture, id: JobId): Map[String, String] = {
    val task = new JobDurableLifetimeRecoveryFixtures.Task
    fixture.original.tasks.put(id, List(task))
    fixture.original.startup.entries = Vector(JobDurableRuntimeFixtures.candidate(id))
    var before = Vector.empty[Option[Record]]
    fixture.original.startup.before = () => before = fixture.rows(id)
    val started = fixture.start()
    started.isSuccess shouldBe false
    val refusal = JobDurableRuntimeFixtures.failureConclusion(started).observation.getEffectiveMessage
    refusal shouldBe Some("durable runtime startup refused")
    fixture.engine.durableOperationalHealth.get.state should not be JobDurableOperationalState.Ready
    fixture.engine.durableOperationalHealth.get.executionAvailable shouldBe false
    fixture.engine._durable_runtime_ready shouldBe false
    fixture.engine._durable_runtime_owner_option shouldBe None
    fixture.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) should not contain DurableJobTerminalProjectionFact.Registered
    fixture.engine.durableRuntimeRehydrationReport.get.candidates.map(_.fact) should not contain DurableJobRuntimeRehydrationFact.Registered
    fixture.engine.query(id) shouldBe None
    _pending(fixture, id)
    val decision = success(DurableJobRecovery.classify(fixture.signed(id).record,
      JobDurableLifetimeRecoveryFixtures.replay))
    decision.outcome shouldBe DurableJobRecoveryOutcome.RecoveryRequired
    decision.reasons shouldBe Vector(DurableJobRecoveryReason.ActiveLifecycle, DurableJobRecoveryReason.ExecutionAlreadyStarted)
    decision.failedEvidenceCategories shouldBe Vector.empty
    task.runs.get() shouldBe 0
    task.canonical.get() shouldBe 0
    task.getters.get() shouldBe 0
    _zero_reader(fixture, id, before)
    val facts = _facts(fixture, id, Some(task), 0, refusal.get, fixture.rows(id) == before)
    fixture.rows(id) shouldBe before
    facts
  }

  private def _zero_reader(fixture: Fixture, id: JobId, before: Vector[Option[Record]]): Unit = {
    fixture.original.startup.calls.get() shouldBe 1
    fixture.original.port.calls.get() shouldBe 0
    fixture.evidence.requests shouldBe Vector.empty
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    fixture.engine.drainAll() shouldBe 0
    fixture.engine.durableLifecycleWriteFailureFacts shouldBe empty
    fixture.mutationCounts shouldBe Vector(0, 0, 0, 0)
    fixture.rows(id) shouldBe before
  }

  private def _requests(fixture: Fixture, id: JobId, count: Int): Unit = {
    val requests = fixture.evidence.requests
    requests.map(_.jobid).distinct shouldBe Vector(id)
    requests.map(_.request.boundary) shouldBe JobRetryCrossProcessFixtures.boundaries.take(count)
    requests.map(_.expectedrevision) shouldBe (1L to count.toLong).toVector
    fixture.original.evidence.requests shouldBe empty
  }

  private def _task_pairs(fixture: Fixture, id: JobId, count: Int): Unit = {
    val requests = fixture.evidence.requests.map(_.request)
    val starts = requests.collect { case request: DurableJobLifecycleWriteRequest.TaskStartIntent => request }
    val outcomes = requests.collect { case request: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint => request }
    val taskids = fixture.signed(id).record.body.tasks.map(_.taskId)
    taskids.size shouldBe count
    taskids.distinct shouldBe taskids
    starts.map(_.taskid.value) shouldBe taskids
    outcomes.map(_.completedtaskid.value) shouldBe taskids
    starts.map(_.taskreadmodels.map(_.taskId.value)) shouldBe taskids.indices.map(index => taskids.take(index + 1)).toVector
    outcomes.map(_.taskreadmodels.map(_.taskId.value)) shouldBe taskids.indices.map(index => taskids.take(index + 1)).toVector
    (starts.flatMap(_.taskreadmodels) ++ outcomes.flatMap(_.taskreadmodels)).foreach { model =>
      model.taskKind shouldBe "operation"
      model.targetKind shouldBe Some("operation")
      model.relation shouldBe Some("root")
      model.transactionRole shouldBe Some("own")
      model.transactionScope shouldBe Some("per-task")
      model.component shouldBe Some("component-a")
      model.service shouldBe Some("service-a")
      model.operation shouldBe Some("run")
    }
    val events = fixture.signed(id).record.body.timeline.filter(_.kind.startsWith("task.durable-"))
    events.map(_.kind) shouldBe Vector.fill(count)(Vector("task.durable-start-intent", "task.durable-outcome-checkpoint")).flatten
    events.flatMap(_.taskId) shouldBe taskids.flatMap(taskid => Vector(taskid, taskid))
  }

  private def _pending(fixture: Fixture, id: JobId): Unit = {
    val snapshot = fixture.signed(id)
    val body = snapshot.record.body
    snapshot.record.format shouldBe DurableRecordFormat.V2
    body.identity.revision shouldBe 5L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
    body.result shouldBe DurableResultOutcome.Pending
    body.lifecycle.schedule.startedAt should not be empty
    // V2 schedule retains the latest task finish while the job remains Running/Pending.
    body.lifecycle.schedule.completedAt shouldBe Some(JobDurableRuntimeFixtures.instant)
    body.lifecycle.retry.nextRetryAt shouldBe None
    body.tasks.size shouldBe 1
    body.tasks.head.transaction.outcome shouldBe DurableTransactionOutcome.Failed
    fixture.native(id).seed.remaining shouldBe 1L
    _history(fixture, id, 1)
    _paid(fixture, id)
  }

  private def _terminal(fixture: Fixture, id: JobId): Unit = {
    val snapshot = fixture.signed(id)
    val body = snapshot.record.body
    snapshot.record.format shouldBe DurableRecordFormat.V2
    body.identity.revision shouldBe 8L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.lifecycle.schedule.startedAt should not be empty
    body.lifecycle.schedule.completedAt should not be empty
    body.tasks.map(_.transaction.outcome) shouldBe Vector(DurableTransactionOutcome.Failed, DurableTransactionOutcome.Committed)
    val retry = body.lifecycle.retry
    retry.attempts.map(_.number) shouldBe Vector(1, 2)
    retry.attempts.map(_.outcome) shouldBe Vector(DurableAttemptOutcome.Failed, DurableAttemptOutcome.Succeeded)
    retry.attempts.map(_.failure) shouldBe Vector(Some(JobRetryCrossProcessFixtures.retryFailure), None)
    retry.attempts.foreach(_.finishedAt should not be empty)
    retry.maxAttempts shouldBe 3
    retry.nextRetryAt shouldBe None
    retry.exhausted shouldBe false
    retry.recoveryRequired shouldBe false
    fixture.native(id).seed.remaining shouldBe 0L
    _history(fixture, id, 2)
    _paid(fixture, id)
    // The writer can compare the terminal attempt times to the exact models;
    // the independent reader observes those closed times solely in native state.
    fixture.evidence.requests.lastOption.foreach { request =>
      request.request match {
        case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, models) =>
          retry.attempts.map(_.startedAt) shouldBe models.map(_.startedAt)
          retry.attempts.map(_.finishedAt) shouldBe models.map(_.finishedAt)
        case _ => throw new IllegalStateException("terminal source request required")
      }
    }
  }

  private def _history(fixture: Fixture, id: JobId, count: Int): Unit = {
    val body = fixture.signed(id).record.body
    val taskids = body.tasks.map(_.taskId)
    taskids.size shouldBe count
    taskids.distinct shouldBe taskids
    body.tasks.foreach { task =>
      task.kind shouldBe DurableTaskKind.Operation
      task.transaction.role shouldBe DurableTransactionRole.Own
      task.transaction.scope shouldBe DurableTransactionScope.PerTask
    }
    val events = body.timeline.filter(_.kind.startsWith("task.durable-"))
    events.map(_.kind) shouldBe Vector.fill(count)(Vector("task.durable-start-intent", "task.durable-outcome-checkpoint")).flatten
    events.flatMap(_.taskId) shouldBe taskids.flatMap(taskid => Vector(taskid, taskid))
    fixture.native(id).seed.normalids shouldBe taskids.toSet
  }

  private def _paid(fixture: Fixture, id: JobId): Unit = {
    val body = fixture.signed(id).record.body
    body.identity.jobId shouldBe id.value
    val supplied = DurableJobKeyedEvidenceFixtures.recipe().projection
    body.authorization shouldBe supplied.authorization
    body.definitionSnapshot shouldBe supplied.definitionSnapshot
    body.retention shouldBe supplied.retention
    val native = fixture.native(id)
    native.seed.paid shouldBe JobLifetimeSnapshot(4L, 192L, 2L, 2L)
    native.seed.claims shouldBe Set.empty
    native.cancelclaimed shouldBe false
  }

  private def _facts(fixture: Fixture, id: JobId, task: Option[JobDurableLifetimeRecoveryFixtures.Task],
    drain: Int, refusal: String, rowsunchanged: Boolean): Map[String, String] = {
    val snapshot = fixture.signed(id)
    val native = fixture.native(id)
    Map("jobid" -> id.value, "status" -> snapshot.record.body.lifecycle.status.wire,
      "semanticRevision" -> snapshot.record.body.identity.revision.toString,
      "providerRevision" -> snapshot.providerrevision.toString,
      "chargedtasks" -> native.seed.paid.chargedtasks.toString,
      "historycredits" -> native.seed.paid.historycredits.toString,
      "admissions" -> native.seed.paid.admissions.toString, "cancelcredits" -> native.seed.paid.cancelcredits.toString,
      "remaining" -> native.seed.remaining.toString, "normalcount" -> native.seed.normalids.size.toString,
      "claims" -> native.seed.claims.size.toString, "cancelclaimed" -> native.cancelclaimed.toString,
      "taskcount" -> snapshot.record.body.tasks.size.toString,
      "attemptcount" -> snapshot.record.body.lifecycle.retry.attempts.size.toString,
      "ready" -> (fixture.engine.durableOperationalHealth.get.state == JobDurableOperationalState.Ready).toString,
      "refusal" -> refusal, "port" -> fixture.original.port.calls.get().toString,
      "task" -> task.map(_.runs.get()).getOrElse(0).toString,
      "canonical" -> task.map(_.canonical.get()).getOrElse(0).toString,
      "getters" -> task.map(_.getters.get()).getOrElse(0).toString,
      "queue" -> fixture.engine._work_queue.size().toString, "drain" -> drain.toString,
      "nativewrites" -> fixture.nativeWrites.toString, "lifewrites" -> fixture.entity.lifeWrites.get().toString,
      "sourcecalls" -> fixture.original.startup.calls.get().toString,
      "keyedcalls" -> fixture.evidence.requests.size.toString, "rowsunchanged" -> rowsunchanged.toString)
  }
  private def _identity(arguments: Arguments): Map[String, String] = {
    val identity = JobCrossProcessFixtures.identity()
    Map("mode" -> arguments.mode, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid)
  }
  private def _halt(arguments: Arguments, facts: Map[String, String]): Nothing = {
    val closure = Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed").map(_ -> "false").toMap
    JobRetryCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ _identity(arguments) ++ closure)
    Runtime.getRuntime.halt(71)
    throw new IllegalStateException("owned JVM halt returned")
  }
}
