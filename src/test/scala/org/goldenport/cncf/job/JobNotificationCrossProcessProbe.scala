package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import java.util.Locale
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventLane, EventRecord, EventStore}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.cncf.usernotification.{UserNotificationProviderRuntime, UserNotificationRequest}
import org.scalatest.matchers.should.Matchers
import DurableJobOperationResponseQueryFixtures.{Fixture, Produced, assertNative, expected, success}
import JobNotificationCrossProcessFixtures.{Mode, Wiring, configure, parameters, privateMarker}

/*
 * Genuine lifecycle forwarding and independent enabled-provider native recovery.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object JobNotificationCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    notificationmode: Mode, input: String, id: Option[JobId])
  private final case class Counts(tasks: Int, getters: Int, canonical: Int, minecount: Int)

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root)
    var wiring = Option.empty[Wiring]
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    var subsystemclosed = false
    try {
      val configured = configure(fixture, arguments.notificationmode, arguments.mode == "reader")
      wiring = Some(configured)
      facts = if (arguments.mode == "writer") _writer(fixture, configured, arguments)
        else _reader(fixture, configured, arguments)
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
        owner.foreach { value =>
          value._backup_closed shouldBe true
          value._scheduler.status.closed shouldBe true
          value._scheduler.status.started shouldBe false
        }
        fixture.native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime
          .testControl.get.pendingTimerCount shouldBe 0
      } finally {
        wiring.foreach(_.provider.close())
        fixture.close()
      }
    }
    owner.nonEmpty shouldBe true
    wiring.get.provider.closed shouldBe true
    val identity = JobNotificationCrossProcessFixtures.identity()
    JobNotificationCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode, "notificationmode" -> arguments.notificationmode.wire,
      "root" -> arguments.root.toString, "sqlite" -> arguments.root.resolve("jobs.db").toString,
      "pid" -> identity.pid.toString, "start" -> identity.start.toString, "uuid" -> identity.uuid,
      "ownerclose" -> owner.exists(_._backup_closed).toString,
      "schedulerclose" -> owner.exists(_._scheduler.status.closed).toString,
      "nodeclose" -> (fixture.native.node.state == SystemNode.State.Stopped).toString,
      "subsystemclose" -> subsystemclosed.toString, "sqlclose" -> fixture.closed.toString,
      "providerclose" -> wiring.get.provider.closed.toString, "leases" -> fixture.native.node.activeLeaseCount.toString,
      "timers" -> fixture.native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime
        .testControl.get.pendingTimerCount.toString))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length == 5 || args.length == 6, "closed mode/root/newreceipt/notificationmode/input/optional id required")
    val mode = args(0)
    require(mode == "writer" || mode == "reader", "closed writer/reader mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-notification-cross-process",
      "absolute owned C7 native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && receipt.getFileName.toString == s"$mode.receipt" &&
      !Files.exists(receipt), "new owned C7 receipt required")
    require((mode == "writer") == (args.length == 5), "only fresh reader takes opaque id in addition to independent mode/input")
    val notificationmode = Mode.parse(args(3))
    val input = args(4)
    expected("scalar", input)
    val id = args.drop(5).headOption.map(value => success(JobId.parse(value)))
    Arguments(mode, root, receipt, notificationmode, input, id)
  }

  private def _writer(fixture: Fixture, wiring: Wiring, arguments: Arguments): Map[String, String] = {
    success(fixture.start())
    fixture.native.owner.health.state shouldBe JobDurableOperationalState.Ready
    val context = wiring.context
    UserNotificationProviderRuntime.providers(context) shouldBe
      (if (arguments.notificationmode == Mode.Disabled) Vector.empty else Vector(wiring.provider))
    val response = expected("scalar", arguments.input)
    val selected = DurableJobOperationResponsePublicationFixtures.task(response)
    val id = success(fixture.native.owner._execute_c(fixture.native.engine.submit(List(selected), context,
      JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async,
        parameters = parameters(arguments.notificationmode)))))
    fixture.native.engine.drainAll() shouldBe 1
    selected.runs.get() shouldBe 1
    selected.canonical.get() shouldBe 1
    selected.getters.get() should be > 0
    val counters = Counts(selected.runs.get(), selected.getters.get(), selected.canonical.get(), -1)
    fixture.source.requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
    fixture.source.requests.map(_.expectedrevision) shouldBe (1L to 6L).toVector
    val native = assertNative(fixture, id, "scalar", arguments.input)
    val rows = fixture.rows(id, Some(native.reference))
    val physical = fixture.physical(native.reference)
    val quota = fixture.quota
    _writer_notification(fixture, wiring, id, arguments.notificationmode)
    fixture.native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
    fixture.native.engine.getResult(id) shouldBe Some(JobResult.Success(response))
    success(fixture.native.engine.queryManagementResult(id)(using context)) shouldBe
      Some(JobManagementResult.Available(JobResult.Success(response)))
    assertNative(fixture, id, "scalar", arguments.input) shouldBe native
    fixture.rows(id, Some(native.reference)) shouldBe rows
    fixture.physical(native.reference) shouldBe physical
    fixture.quota shouldBe quota
    Counts(selected.runs.get(), selected.getters.get(), selected.canonical.get(), -1) shouldBe counters
    fixture.effects.tasks shouldBe 0
    fixture.effects.getters shouldBe 0
    fixture.effects.canonical shouldBe 0
    fixture.effects.sources shouldBe 6
    fixture.effects.puts shouldBe 1
    Vector(fixture.effects.ports, fixture.effects.resolvers, fixture.effects.gets, fixture.effects.opens,
      fixture.effects.closes, fixture.effects.deletes).foreach(value => value shouldBe 0)
    fixture.effects.writes should be > 0
    _facts(fixture, wiring, native, counters, false)
  }

  private def _events(fixture: Fixture): Vector[EventRecord] =
    success(fixture.native.subsystem.eventStore.query(EventStore.Query()))

  private def _writer_notification(fixture: Fixture, wiring: Wiring, id: JobId, mode: Mode): Unit = {
    val events = _events(fixture)
    val succeeded = events.filter(_.name == "job.succeeded")
    succeeded should have size 1
    val source = succeeded.head
    source.kind shouldBe "job.succeeded"
    source.payload("job-id") shouldBe id.value
    source.payload("status") shouldBe "Succeeded"
    source.payload("submitter-principal-id") shouldBe "subject-a"
    source.payload("submitter-subject-kind") shouldBe "User"
    source.payload("job-run-mode") shouldBe "async"
    source.payload("job-persistence") shouldBe "persistent"
    source.payload("recovery-required") shouldBe "false"
    source.payload.filter { case (key, _) => key.startsWith("web.") } shouldBe parameters(mode)
    val correlation = wiring.context.observability.correlationId.map(_.print)
    source.attributes.get("correlation-id") shouldBe correlation
    val diagnostics = events.filter(_.name.startsWith("user-notification.forwarding."))
    val skip = mode == Mode.NoApp || mode == Mode.Disabled
    wiring.provider.invocations.size shouldBe (if (skip) 0 else 1)
    diagnostics.size shouldBe (if (skip) 0 else 1)
    if (!skip) {
      val app = if (mode == Mode.UnsafeApp) "../private" else "blog"
      val request = UserNotificationRequest(recipientUserId = "subject-a", notificationType = "cncf.job",
        channel = "in-app", title = s"Job succeeded: $app.post.publish",
        body = s"Job ${id.value} is succeeded. See the Job page for details.", priority = None, status = "Queued",
        dedupeKey = Some(s"cncf.job:${id.value}:succeeded"),
        actionUrl = if (mode == Mode.UnsafeApp) None else Some(s"/web/blog/jobs/${id.value}"),
        metadata = Map("jobId" -> id.value, "trigger" -> "succeeded", "jobStatus" -> "succeeded",
          "sourceEventName" -> "job.succeeded", "app" -> app, "service" -> "post", "operation" -> "publish",
          "recoveryRequired" -> "false"), correlationId = correlation)
      val invocation = wiring.provider.invocations.head
      invocation.request shouldBe request
      invocation.context.security.principal.id.value shouldBe "subject-a"
      invocation.context.security.subjectKind.toString shouldBe "User"
      invocation.context.security shouldBe wiring.context.security
      invocation.context.cncfCore.scope shouldBe wiring.context.cncfCore.scope
      invocation.context.core shouldBe wiring.context.core
      invocation.context.clock shouldBe fixture.native.clock
      invocation.context.idGeneration shouldBe wiring.context.idGeneration
      invocation.context.dataStoreSpace shouldBe fixture.native.context.dataStoreSpace
      request.toString.contains(privateMarker) shouldBe false
      val accepted = mode == Mode.Accepted || mode == Mode.UnsafeApp
      val name = if (accepted) "user-notification.forwarding.sent" else "user-notification.forwarding.failed"
      val note = mode match {
        case Mode.Refused => "The notification provider refused delivery."
        case Mode.Failure => "The notification provider did not accept delivery."
        case Mode.Throw => "The notification provider failed while accepting delivery."
        case _ => "notification-c7"
      }
      val diagnostic = diagnostics.head
      diagnostic.name shouldBe name
      diagnostic.kind shouldBe name
      diagnostic.persistent shouldBe true
      diagnostic.status shouldBe EventRecord.Status.Stored
      diagnostic.lane shouldBe EventLane.NonTransactional
      diagnostic.sequence should be > 0L
      diagnostic.attributes shouldBe Map("cncf.userNotification.forwarding" -> "true")
      diagnostic.payload shouldBe Map("source-event-name" -> "job.succeeded", "job-id" -> id.value,
        "note" -> note, "recipient-user-id" -> "subject-a", "dedupe-key" -> s"cncf.job:${id.value}:succeeded")
      diagnostic.payload.toString.contains(privateMarker) shouldBe false
    }
  }

  private def _reader(fixture: Fixture, wiring: Wiring, arguments: Arguments): Map[String, String] = {
    val id = arguments.id.get
    fixture.open(id)
    UserNotificationProviderRuntime.providers(wiring.context) shouldBe Vector(wiring.provider)
    wiring.provider.mode shouldBe Mode.Accepted
    wiring.provider.invocations shouldBe Vector.empty
    _events(fixture) shouldBe Vector.empty
    val native = assertNative(fixture, id, "scalar", arguments.input)
    val rows = fixture.rows(id, Some(native.reference))
    val physical = fixture.physical(native.reference)
    val quota = fixture.quota
    val raw = fixture.native.engine.query(id).get
    raw.result shouldBe None
    raw.debug.parameters shouldBe Map.empty
    raw.submitter shouldBe JobSubmitter("subject-a", "User", None)
    fixture.native.engine.getResult(id) shouldBe None
    success(fixture.native.engine.queryManagementResult(id)(using wiring.context)) shouldBe
      Some(JobManagementResult.UnavailableAfterRestart(raw.resultSummary))
    val service = new JobExperienceService(fixture.native.engine)
    val baseline = fixture.effects
    val runtime = _runtime(fixture, id)
    val calls = wiring.provider.invocations
    val events = _events(fixture)
    given ExecutionContext = wiring.context
    fixture.native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
    fixture.native.engine.query(id) shouldBe Some(raw)
    fixture.native.engine.getResult(id) shouldBe None
    success(fixture.native.engine.queryManagementResult(id)) shouldBe
      Some(JobManagementResult.UnavailableAfterRestart(raw.resultSummary))
    val mine = JobExperienceScope.Mine(None)
    val page = success(service.list(JobExperienceQuery(mine), Some(Locale.ENGLISH)))
    page.entries.map(_.jobId) shouldBe Vector(id)
    page.totalCount shouldBe 1
    page.nextCursor shouldBe None
    val entry = page.entries.head
    entry.canonicalStatus shouldBe JobStatus.Succeeded
    entry.displayStatus shouldBe JobExperienceStatus.Completed
    entry.persistence shouldBe JobPersistencePolicy.Persistent
    entry.origin shouldBe JobDataOrigin.Durable
    entry.vocabulary shouldBe JobExperienceVocabulary("Completed", "View result")
    val view = success(service.get(id, mine, Some(Locale.ENGLISH)))
    view.detail.summary.jobId shouldBe id
    view.detail.summary.status shouldBe JobStatus.Succeeded
    view.detail.summary.persistence shouldBe JobPersistencePolicy.Persistent
    view.detail.summary.origin shouldBe JobDataOrigin.Durable
    view.displayStatus shouldBe JobExperienceStatus.Completed
    view.vocabulary shouldBe JobExperienceVocabulary("Completed", "View result")
    view.result shouldBe JobExperienceResult.UnavailableAfterRestart
    view.controls shouldBe Vector.empty
    view.refreshable shouldBe false
    // Service effect comparison precedes the final native observation I/O.
    fixture.effects shouldBe baseline
    _runtime(fixture, id) shouldBe runtime
    wiring.provider.invocations shouldBe calls
    _events(fixture) shouldBe events
    fixture.native.engine.drainAll() shouldBe 0
    fixture.effects shouldBe baseline
    _runtime(fixture, id) shouldBe runtime
    wiring.provider.invocations shouldBe Vector.empty
    _events(fixture) shouldBe Vector.empty
    assertNative(fixture, id, "scalar", arguments.input) shouldBe native
    fixture.rows(id, Some(native.reference)) shouldBe rows
    fixture.physical(native.reference) shouldBe physical
    fixture.quota shouldBe quota
    fixture.native.engine.query(id) shouldBe Some(raw)
    val observed = fixture.effects
    // Native physical audit, complete Blob row and physical recheck each load metadata once.
    observed.metadata shouldBe baseline.metadata + 3
    observed.copy(metadata = baseline.metadata) shouldBe baseline
    Vector(observed.writes, observed.entitywrites, observed.gets, observed.opens, observed.closes,
      observed.puts, observed.deletes, observed.resolvers, observed.sources, observed.ports,
      observed.tasks, observed.getters, observed.canonical).foreach(value => value shouldBe 0)
    _runtime(fixture, id) shouldBe runtime
    wiring.provider.invocations shouldBe Vector.empty
    _events(fixture) shouldBe Vector.empty
    _facts(fixture, wiring, native, Counts(0, 0, 0, 1), true)
  }

  private def _runtime(fixture: Fixture, id: JobId): Map[String, Any] = {
    val engine = fixture.native.engine
    val state = engine.runtimeState
    val scheduling = fixture.native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get
    state.durableJobs.isEmpty shouldBe true
    state.runtimeJobs.isEmpty shouldBe true
    fixture.native.tasks.isEmpty shouldBe true
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
      "scheduler" -> scheduler, "timers" -> scheduling.pendingTimerCount, "manualtime" -> scheduling.now,
      "enginetime" -> engine.timeSource.now(), "clocktime" -> fixture.native.clock.peek,
      "clocks" -> fixture.native.clock.calls.get(), "ids" -> fixture.native.ids.calls.get(),
      "retention" -> fixture.native.retention.calls.get(), "ready" -> fixture.native.owner.health)
  }

  private def _facts(fixture: Fixture, wiring: Wiring, produced: Produced, counts: Counts, reader: Boolean): Map[String, String] = {
    val paid = produced.payment.seed
    val events = _events(fixture)
    Map("id" -> produced.id.value, "revision" -> produced.snapshot.record.body.identity.revision.toString,
      "providerrevision" -> produced.snapshot.providerrevision.value.toString,
      "ready" -> (fixture.native.owner.health.state == JobDurableOperationalState.Ready).toString,
      "status" -> produced.snapshot.record.body.lifecycle.status.wire,
      "resultAvailability" -> (if (reader) "UnavailableAfterRestart" else "Available"),
      "domainChecked" -> "true", "zeroEffects" -> reader.toString, "nativeUnchanged" -> "true",
      "rawabsent" -> reader.toString, "providercalls" -> wiring.provider.invocations.size.toString,
      "diagnostics" -> events.count(_.name.startsWith("user-notification.forwarding.")).toString,
      "succeededevents" -> events.count(_.name == "job.succeeded").toString, "events" -> events.size.toString,
      "minecount" -> counts.minecount.toString, "tasks" -> counts.tasks.toString,
      "getters" -> counts.getters.toString, "canonical" -> counts.canonical.toString,
      "fixturetasks" -> fixture.effects.tasks.toString, "sources" -> fixture.effects.sources.toString,
      "puts" -> fixture.effects.puts.toString, "chargedtasks" -> paid.paid.chargedtasks.toString,
      "historycredits" -> paid.paid.historycredits.toString, "admissions" -> paid.paid.admissions.toString,
      "cancelcredits" -> paid.paid.cancelcredits.toString, "remaining" -> paid.remaining.toString)
  }
}
