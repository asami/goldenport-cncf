package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.SystemNode
import org.scalatest.matchers.should.Matchers
import DurableJobOperationResponseQueryFixtures.{Fixture, Produced, assertDomain, assertNative, expected, success}
import JobExperienceCrossProcessFixtures.{CallerMode, Selection, caller}

/*
 * Independent current callers consume the actual production facade and native facts.
 * Service effects are compared before final native observation I/O.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object JobExperienceCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    selection: Selection, caller: CallerMode, input: String, id: Option[JobId])
  private final case class Outcomes(minecount: Int, operatorcount: Int = -1,
    applicationcount: Int = -1, diagnostictasks: Int = -1, diagnostictimeline: Int = -1,
    controlrefusals: Int = 0, availability: String = "Hidden", outcome: String = "hidden")

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    var subsystemclosed = false
    try {
      facts = if (arguments.mode == "writer") _writer(fixture, arguments) else _reader(fixture, arguments)
    } finally {
      owner = fixture.native.engine._durable_runtime_owner_option
      val stopped = fixture.native.subsystem.shutdownC()
      val nodeclosed = fixture.native.node.shutdownC()
      try {
        success(stopped)
        success(nodeclosed)
        subsystemclosed = stopped.isSuccess
        fixture.native.node.state shouldBe SystemNode.State.Stopped
        fixture.native.node.activeLeaseCount shouldBe 0
        fixture.closed shouldBe true
        owner.nonEmpty shouldBe true
        owner.get._backup_closed shouldBe true
        owner.get._scheduler.status.closed shouldBe true
        owner.get._scheduler.status.started shouldBe false
        fixture.native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime
          .testControl.get.pendingTimerCount shouldBe 0
      } finally fixture.close()
    }
    val identity = JobExperienceCrossProcessFixtures.identity()
    JobExperienceCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid,
      "selection" -> arguments.selection.wire,
      "callermode" -> (if (arguments.mode == "writer") CallerMode.Owner.wire else arguments.caller.wire),
      "ownerclose" -> owner.exists(_._backup_closed).toString,
      "nodeclose" -> (fixture.native.node.state == SystemNode.State.Stopped).toString,
      "subsystemclose" -> subsystemclosed.toString, "sqlclose" -> fixture.closed.toString,
      "leases" -> fixture.native.node.activeLeaseCount.toString))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length == 6 || args.length == 7, "closed mode/root/newreceipt/selector/caller/input/optional id required")
    val mode = args(0)
    require(mode == "writer" || mode == "reader", "closed writer/reader mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-experience-cross-process",
      "absolute owned C6 native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && receipt.getFileName.toString == s"$mode.receipt" &&
      !Files.exists(receipt), "new owned C6 receipt required")
    require((mode == "writer") == (args.length == 6), "reader accepts only opaque id in addition to independent selectors/input")
    val selection = Selection.parse(args(3))
    val callermode = CallerMode.parse(args(4))
    val input = args(5)
    expected("scalar", input)
    val id = args.drop(6).headOption.map(value => success(JobId.parse(value)))
    Arguments(mode, root, receipt, selection, callermode, input, id)
  }

  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    success(fixture.start())
    fixture.native.owner.health.state shouldBe JobDurableOperationalState.Ready
    fixture.current.security.subjectKind.toString shouldBe "User"
    fixture.current.security.principal.id.value shouldBe "subject-a"
    val response = expected("scalar", arguments.input)
    val produced = fixture.produce(response)
    val rows = fixture.rows(produced.id, Some(produced.reference))
    assertDomain(success(fixture.query(produced.id, fixture.current)).get.result.get, "scalar", arguments.input)
    fixture.native.engine.getResult(produced.id) shouldBe Some(JobResult.Success(response))
    success(fixture.native.engine.queryManagementResult(produced.id)(using fixture.current)) shouldBe
      Some(JobManagementResult.Available(JobResult.Success(response)))
    val native = assertNative(fixture, produced.id, "scalar", arguments.input)
    native shouldBe produced
    fixture.rows(produced.id, Some(produced.reference)) shouldBe rows
    fixture.effects.tasks shouldBe 1
    fixture.effects.canonical shouldBe 1
    fixture.effects.sources shouldBe 6
    fixture.effects.puts shouldBe 1
    Vector(fixture.effects.ports, fixture.effects.resolvers, fixture.effects.gets, fixture.effects.opens,
      fixture.effects.closes, fixture.effects.deletes).foreach(value => value shouldBe 0)
    fixture.effects.writes should be > 0
    _facts(fixture, native, Outcomes(-1, availability = "Available", outcome = "published"), false)
  }

  private def _reader(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    val id = arguments.id.get
    fixture.open(id)
    val native = assertNative(fixture, id, "scalar", arguments.input)
    val rows = fixture.rows(id, Some(native.reference))
    val physical = fixture.physical(native.reference)
    val quota = fixture.quota
    val raw = fixture.native.engine.query(id).get
    raw.result shouldBe None
    raw.debug.parameters shouldBe Map.empty
    raw.submitter shouldBe JobSubmitter("subject-a", "User", None)
    fixture.native.engine.getResult(id) shouldBe None
    success(fixture.native.engine.queryManagementResult(id)(using fixture.current)) shouldBe
      Some(JobManagementResult.UnavailableAfterRestart(raw.resultSummary))
    fixture.native.tasks.isEmpty shouldBe true
    fixture.native.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.native.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.native.engine._work_queue.isEmpty shouldBe true
    val context = caller(fixture.native.context, arguments.caller)
    val service = new JobExperienceService(fixture.native.engine)
    val baseline = fixture.effects
    val runtime = _runtime(fixture, id)
    val outcomes = _experience(service, native, raw, arguments)(using context)
    // This comparison deliberately precedes every final SQL/Blob metadata observation.
    fixture.effects shouldBe baseline
    _runtime(fixture, id) shouldBe runtime
    fixture.native.engine.drainAll() shouldBe 0
    fixture.effects shouldBe baseline
    _runtime(fixture, id) shouldBe runtime
    val verified = assertNative(fixture, id, "scalar", arguments.input)
    verified shouldBe native
    fixture.rows(id, Some(native.reference)) shouldBe rows
    fixture.physical(native.reference) shouldBe physical
    fixture.quota shouldBe quota
    fixture.native.engine.query(id).get shouldBe raw
    val observed = fixture.effects
    // assertNative physical metadata, complete Blob row, and physical recheck: three observation loads.
    observed.metadata shouldBe baseline.metadata + 3
    observed.copy(metadata = baseline.metadata) shouldBe baseline
    Vector(observed.writes, observed.entitywrites, observed.gets, observed.opens, observed.closes,
      observed.puts, observed.deletes, observed.resolvers, observed.sources, observed.ports,
      observed.tasks, observed.getters, observed.canonical).foreach(value => value shouldBe 0)
    _runtime(fixture, id) shouldBe runtime
    _facts(fixture, native, outcomes, true)
  }

  private def _experience(service: JobExperienceService, produced: Produced, raw: JobQueryReadModel, arguments: Arguments)
      (using ctx: ExecutionContext): Outcomes = {
    val id = produced.id
    val mine = JobExperienceScope.Mine(None)
    val owner = arguments.caller == CallerMode.Owner
    val anonymous = arguments.caller == CallerMode.Anonymous
    val admin = arguments.caller == CallerMode.JobAdmin || arguments.caller == CallerMode.ContentAdmin
    val operator = JobExperienceScope.Operator
    val operatorselected = Set(Selection.Operator, Selection.Generated, Selection.Broad, Selection.Controls).contains(arguments.selection)
    if (operatorselected) {
      val operatorpage = service.list(JobExperienceQuery(operator), Some(Locale.ENGLISH))
      val operatorview = service.get(id, operator, Some(Locale.ENGLISH))
      if (admin) {
        _page(success(operatorpage), Vector(id))
        _view(success(operatorview), produced)
      } else {
        _refusal(operatorpage, Consequence.operationIllegal[JobExperiencePage]("job.experience", "operator scope requires job_admin or content_admin"))
        _refusal(operatorview, Consequence.operationNotFound[JobExperienceView](s"job:${id.value}"))
      }
    }
    val page = service.list(JobExperienceQuery(mine), Some(Locale.ENGLISH))
    val view = service.get(id, mine, Some(Locale.ENGLISH))
    val minecount = if (anonymous) {
      _refusal(page, Consequence.operationIllegal[JobExperiencePage]("job.experience", "an authenticated subject is required"))
      -1
    } else {
      _page(success(page), if (owner) Vector(id) else Vector.empty)
      if (owner) 1 else 0
    }
    if (owner) _view(success(view), produced)
    else _refusal(view, Consequence.operationNotFound[JobExperienceView](s"job:${id.value}"))
    var outcome = Outcomes(minecount, availability = if (owner) "UnavailableAfterRestart" else "Hidden",
      outcome = if (owner) "owner-visible" else if (anonymous) "anonymous-refused" else "hidden")
    if (operatorselected && admin)
      outcome = outcome.copy(operatorcount = 1, availability = "UnavailableAfterRestart", outcome = "operator-visible")
    if (!admin) {
      _refusal(service.diagnostics(id, mine, 0, 100), Consequence.operationIllegal[JobExperienceDiagnostics](
        "job.experience.diagnostics", "operator diagnostics require job_admin or content_admin"))
    }
    if (arguments.selection == Selection.Broad) {
      success(JobQueryPolicy.default.authorizeRead(raw))
      val application = JobExperienceScope.Application("blog")
      _page(success(service.list(JobExperienceQuery(application), Some(Locale.ENGLISH))), Vector.empty)
      _refusal(service.get(id, application, Some(Locale.ENGLISH)), Consequence.operationNotFound[JobExperienceView](s"job:${id.value}"))
      outcome = outcome.copy(applicationcount = 0)
    }
    val diagnosticselection = Set(Selection.Diagnostics, Selection.NegativeOffset, Selection.ZeroLimit, Selection.OversizeLimit)
    if (diagnosticselection.contains(arguments.selection) || arguments.selection == Selection.Operator) {
      admin shouldBe true
      val offset = if (arguments.selection == Selection.NegativeOffset) -1 else 0
      val limit = if (arguments.selection == Selection.ZeroLimit) 0 else if (arguments.selection == Selection.OversizeLimit) 101 else 100
      val result = service.diagnostics(id, operator, offset, limit)
      if (arguments.selection == Selection.Diagnostics || arguments.selection == Selection.Operator) {
        val diagnostics = success(result)
        _diagnostics(diagnostics, produced)
        outcome = outcome.copy(diagnostictasks = diagnostics.tasks.totalCount,
          diagnostictimeline = diagnostics.timeline.totalCount)
      } else _refusal(result, Consequence.argumentInvalid[JobExperienceDiagnostics](
        "diagnostics offset must be nonnegative and limit must be in 1..100"))
    }
    if (arguments.selection == Selection.Controls) {
      (owner || admin) shouldBe true
      val scope = if (owner) mine else operator
      val commands = Vector(JobControlCommand.Cancel, JobControlCommand.Suspend, JobControlCommand.Resume, JobControlCommand.Retry)
      commands.foreach { command =>
        _refusal(service.control(id, scope, command), Consequence.operationIllegal[JobControlResponse](
          "job.experience.control", "the requested control is not available"))
      }
      outcome = outcome.copy(controlrefusals = commands.size)
    }
    outcome
  }

  private def _refusal[A](actual: Consequence[A], expected: Consequence[A]): Unit = {
    actual.isSuccess shouldBe false
    val actualconclusion = JobDurableRuntimeFixtures.failureConclusion(actual)
    val expectedconclusion = JobDurableRuntimeFixtures.failureConclusion(expected)
    actualconclusion.observation.getEffectiveMessage shouldBe expectedconclusion.observation.getEffectiveMessage
    actualconclusion.observation.taxonomy shouldBe expectedconclusion.observation.taxonomy
  }
  private def _page(page: JobExperiencePage, ids: Vector[JobId]): Unit = {
    page.entries.map(_.jobId) shouldBe ids
    page.totalCount shouldBe ids.size
    page.nextCursor shouldBe None
    page.entries.foreach { entry =>
      entry.canonicalStatus shouldBe JobStatus.Succeeded
      entry.displayStatus shouldBe JobExperienceStatus.Completed
      entry.vocabulary shouldBe JobExperienceVocabulary("Completed", "View result")
      entry.persistence shouldBe JobPersistencePolicy.Persistent
      entry.origin shouldBe JobDataOrigin.Durable
    }
  }
  private def _view(view: JobExperienceView, produced: Produced): Unit = {
    val identity = produced.snapshot.record.body.identity
    val summary = view.detail.summary
    summary.jobId shouldBe produced.id
    summary.status shouldBe JobStatus.Succeeded
    summary.persistence shouldBe JobPersistencePolicy.Persistent
    summary.origin shouldBe JobDataOrigin.Durable
    summary.createdAt shouldBe identity.createdAt
    summary.updatedAt shouldBe identity.updatedAt
    view.displayStatus shouldBe JobExperienceStatus.Completed
    view.vocabulary shouldBe JobExperienceVocabulary("Completed", "View result")
    view.progress shouldBe JobExperienceProgress("terminal", 1)
    view.detail.taskCount shouldBe 1
    view.detail.timelineCount shouldBe produced.snapshot.record.body.timeline.size
    view.result shouldBe JobExperienceResult.UnavailableAfterRestart
    view.controls shouldBe Vector.empty
    view.refreshable shouldBe false
  }
  private def _diagnostics(value: JobExperienceDiagnostics, produced: Produced): Unit = {
    val body = produced.snapshot.record.body
    value.jobId shouldBe produced.id
    value.tasks.offset shouldBe 0
    value.tasks.limit shouldBe 100
    value.tasks.totalCount shouldBe 1
    value.tasks.fetchedCount shouldBe 1
    value.tasks.tasks.size shouldBe 1
    val native = body.tasks.head
    native.kind shouldBe DurableTaskKind.Operation
    native.target.targetKind shouldBe "operation"
    native.relation.kind.wire shouldBe "root"
    native.transaction.role.wire shouldBe "own"
    native.transaction.scope.wire shouldBe "per-task"
    native.transaction.outcome shouldBe DurableTransactionOutcome.Committed
    val task = value.tasks.tasks.head
    task shouldBe JobTaskReadModel(success(TaskId.parse(native.taskId)), None, JobTaskStatus.Succeeded,
      body.lifecycle.schedule.startedAt.getOrElse(body.identity.createdAt), body.lifecycle.schedule.completedAt,
      JobTaskResultSummary(true, None), None, None, None, "operation", Some("operation"), Some("root"),
      Some("own"), Some("per-task"), Some("committed"), None, None, None, None, false)
    value.timeline.offset shouldBe 0
    value.timeline.limit shouldBe 100
    value.timeline.totalCount shouldBe body.timeline.size
    value.timeline.fetchedCount shouldBe body.timeline.size
    val kinds = Set("job.submitted", "job.async.queued", "job.running", "job.succeeded",
      "job.durable-start-intent", "job.durable-running-intent", "job.durable-terminal-outcome-checkpoint",
      "task.running", "task.succeeded", "task.transaction.committed", "task.durable-start-intent", "task.durable-outcome-checkpoint")
    body.timeline.foreach(event => kinds.contains(event.kind) shouldBe true)
    value.timeline.events shouldBe body.timeline.sortBy(event => (event.sequence, event.occurredAt.toEpochMilli)).map { event =>
      JobTimelineEvent(event.sequence, event.occurredAt, event.kind,
        event.taskId.map(value => success(TaskId.parse(value))), None, None)
    }
  }

  private def _runtime(fixture: Fixture, id: JobId): Map[String, Any] = {
    val engine = fixture.native.engine
    val state = engine.runtimeState
    val scheduling = fixture.native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get
    state.durableJobs.isEmpty shouldBe true
    state.runtimeJobs.isEmpty shouldBe true
    engine._work_queue.isEmpty shouldBe true
    state._native_lifetime_bindings.keySet.toSet shouldBe Set(id)
    state._lifetime_budget.snapshot(id) shouldBe Some(JobLifetimeSnapshot(2L, 96L, 1L, 1L))
    scheduling.pendingTimerCount shouldBe 1
    engine._resource_budget.snapshot.retained shouldBe 1
    engine._resource_budget.snapshot.pending shouldBe 0
    engine._resource_budget.snapshot.executing shouldBe 0
    val scheduler = fixture.native.owner._scheduler.status
    scheduler.started shouldBe true
    scheduler.closed shouldBe false
    scheduler.nextDueAt shouldBe Some(engine.timeSource.now().plusMillis(1000L))
    scheduler.lastResult shouldBe None
    fixture.native.retention.calls.get() shouldBe 0
    Map("resources" -> engine._resource_budget.snapshot, "bindings" -> state._native_lifetime_bindings.toMap,
      "paidseed" -> state._lifetime_budget.snapshot(id), "queue" -> engine._work_queue.size(),
      "worksequence" -> engine._work_sequence.get(), "tasks" -> fixture.native.tasks.toMap,
      "durablemaps" -> state.durableJobs.size(), "runtimemaps" -> state.runtimeJobs.size(),
      "scheduler" -> fixture.native.owner._scheduler.status, "timers" -> scheduling.pendingTimerCount,
      "manualtime" -> scheduling.now, "enginetime" -> engine.timeSource.now(),
      "clocktime" -> fixture.native.clock.peek, "clocks" -> fixture.native.clock.calls.get(),
      "ids" -> fixture.native.ids.calls.get(), "ready" -> fixture.native.owner.health)
  }
  private def _facts(fixture: Fixture, produced: Produced, outcomes: Outcomes, reader: Boolean): Map[String, String] = {
    val paid = produced.payment.seed
    Map("id" -> produced.id.value, "revision" -> produced.snapshot.record.body.identity.revision.toString,
      "providerrevision" -> produced.snapshot.providerrevision.value.toString,
      "ready" -> (fixture.native.owner.health.state == JobDurableOperationalState.Ready).toString,
      "status" -> produced.snapshot.record.body.lifecycle.status.wire,
      "resultAvailability" -> outcomes.availability, "outcome" -> outcomes.outcome,
      "minecount" -> outcomes.minecount.toString, "operatorcount" -> outcomes.operatorcount.toString,
      "applicationcount" -> outcomes.applicationcount.toString, "diagnostictasks" -> outcomes.diagnostictasks.toString,
      "diagnostictimeline" -> outcomes.diagnostictimeline.toString, "controlrefusals" -> outcomes.controlrefusals.toString,
      "tasks" -> fixture.effects.tasks.toString, "canonical" -> fixture.effects.canonical.toString,
      "sources" -> fixture.effects.sources.toString, "puts" -> fixture.effects.puts.toString,
      "chargedtasks" -> paid.paid.chargedtasks.toString, "historycredits" -> paid.paid.historycredits.toString,
      "admissions" -> paid.paid.admissions.toString, "cancelcredits" -> paid.paid.cancelcredits.toString,
      "remaining" -> paid.remaining.toString, "rawabsent" -> reader.toString,
      "domainChecked" -> "true", "zeroEffects" -> reader.toString, "nativeUnchanged" -> "true")
  }
}
