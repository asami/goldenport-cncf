package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import java.time.Duration
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import JobDurableRuntimeFixtures.success
import JobScheduledCrossProcessFixtures.Fixture

/*
 * Fresh native schedule and interruption observations, never transferred work.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
object JobScheduledCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    delay: Int, id: Option[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var closure = Map.empty[String, String]
    try {
      facts = arguments.mode match {
        case "scheduled-writer" | "unstarted-crash-writer" | "running-crash-writer" =>
          _writer(fixture, arguments)
        case "scheduled-reader" => _scheduled_reader(fixture, arguments.id.get, arguments.delay)
        case "terminal-reader" => _terminal_reader(fixture, arguments.id.get)
        case "refused-reader" => _refused_reader(fixture, arguments.id.get)
        case _ => throw new IllegalArgumentException("closed scheduled mode required")
      }
    } finally closure = fixture.close()
    JobScheduledCrossProcessFixtures.writeReceipt(arguments.receipt,
      facts ++ _identity(arguments) ++ closure)
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length == 4 || args.length == 5, "scheduled probe requires mode/root/receipt/delay and reader ID")
    val mode = args(0)
    require(JobScheduledCrossProcessFixtures.modes.contains(mode), "closed six-mode scheduled probe required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-scheduled-cross-process" &&
      root.getParent.getParent.getParent.getParent.getFileName.toString == "target",
      "owned absolute scheduled native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") &&
      receipt.getFileName.toString == s"$mode.receipt" && !Files.exists(receipt),
      "new owned scheduled receipt required")
    val delay = args(3).toInt
    require(delay >= 1 && delay <= 20, "scheduled delay must be independently selected in 1..20")
    val id = args.drop(4).headOption.map(value => success(JobId.parse(value)))
    require(if (mode.endsWith("writer")) id.isEmpty else id.nonEmpty, "writer/reader ID arguments must agree")
    Arguments(mode, root, receipt, delay, id)
  }

  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    success(fixture.start())
    fixture.original.owner.health.state shouldBe JobDurableOperationalState.Ready
    val task = new JobDurableLifetimeRecoveryFixtures.Task
    var id = Option.empty[JobId]
    if (arguments.mode == "running-crash-writer") task.body = _ => {
      val jobid = id.get
      fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Running)
      _running(fixture, jobid)
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 0
      fixture.original.evidence.requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries.take(4)
      fixture.original.evidence.requests.map(_.expectedrevision) shouldBe Vector(1L, 2L, 3L, 4L)
      _halt(arguments, _facts(fixture, jobid, Some(task), 0, ""))
    }
    id = Some(fixture.submit(task, if (arguments.mode == "running-crash-writer") None else Some(arguments.delay)))
    val jobid = id.get
    if (arguments.mode == "running-crash-writer") {
      fixture.engine.drainAll()
      throw new IllegalStateException("Running crash body did not halt its owned JVM")
    }
    fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
    fixture.engine.getResult(jobid) shouldBe None
    _submitted(fixture, jobid, arguments.delay)
    fixture.engine.drainAll() shouldBe 0
    task.runs.get() shouldBe 0
    task.canonical.get() shouldBe 0
    fixture.original.port.calls.get() shouldBe 0
    fixture.original.evidence.requests.map(_.request.boundary) shouldBe Vector(DurableJobLifecycleWriteBoundary.Admission)
    fixture.original.evidence.requests.map(_.expectedrevision) shouldBe Vector(1L)
    fixture.nativeWrites should be > 0
    val facts = _facts(fixture, jobid, Some(task), 0, "")
    if (arguments.mode == "unstarted-crash-writer") _halt(arguments, facts)
    facts
  }

  private def _scheduled_reader(fixture: Fixture, id: JobId, delay: Int): Map[String, String] = {
    val task = new JobDurableLifetimeRecoveryFixtures.Task
    fixture.original.tasks.put(id, List(task))
    fixture.original.startup.entries = Vector(JobDurableRuntimeFixtures.candidate(id))
    var before = Vector.empty[Option[Record]]
    fixture.original.startup.before = () => before = fixture.rows(id)
    success(fixture.start())
    fixture.original.owner.health.state shouldBe JobDurableOperationalState.Ready
    fixture.engine.durableRuntimeRehydrationReport.get.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
    fixture.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Refused)
    fixture.original.startup.calls.get() shouldBe 1
    fixture.original.port.calls.get() shouldBe 1
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.rows(id) shouldBe before
    fixture.mutationCounts shouldBe Vector(0, 0, 0, 0)
    _submitted(fixture, id, delay)
    fixture.engine.drainAll() shouldBe 0
    fixture.original.advance(Duration.ofSeconds(delay.toLong - 1L))
    fixture.engine._now() shouldBe JobDurableRuntimeFixtures.instant.plusSeconds(delay.toLong - 1L)
    fixture.engine.drainAll() shouldBe 0
    task.runs.get() shouldBe 0
    task.canonical.get() shouldBe 0
    fixture.rows(id) shouldBe before
    fixture.mutationCounts shouldBe Vector(0, 0, 0, 0)
    fixture.original.advance(Duration.ofSeconds(1L))
    fixture.engine._now() shouldBe JobDurableRuntimeFixtures.instant.plusSeconds(delay.toLong)
    val drained = fixture.engine.drainAll()
    drained shouldBe 1
    task.runs.get() shouldBe 1
    task.canonical.get() shouldBe 1
    fixture.original.evidence.requests.map(_.jobid).distinct shouldBe Vector(id)
    fixture.original.evidence.requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries.tail
    fixture.original.evidence.requests.map(_.expectedrevision) shouldBe Vector(2L, 3L, 4L, 5L, 6L)
    fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
    fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
    _terminal(fixture, id)
    fixture.nativeWrites should be > 0
    _facts(fixture, id, Some(task), drained, "")
  }

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
    val facts = _facts(fixture, id, None, 0, "")
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
    fixture.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) should not contain DurableJobTerminalProjectionFact.Registered
    fixture.engine.durableRuntimeRehydrationReport.get.candidates.map(_.fact) should not contain DurableJobRuntimeRehydrationFact.Registered
    fixture.engine.query(id) shouldBe None
    _running(fixture, id)
    val decision = success(DurableJobRecovery.classify(fixture.signed(id).record,
      JobDurableLifetimeRecoveryFixtures.replay))
    decision.outcome shouldBe DurableJobRecoveryOutcome.RecoveryRequired
    decision.reasons shouldBe Vector(DurableJobRecoveryReason.ActiveLifecycle, DurableJobRecoveryReason.ExecutionAlreadyStarted)
    decision.failedEvidenceCategories shouldBe Vector.empty
    task.runs.get() shouldBe 0
    task.canonical.get() shouldBe 0
    task.getters.get() shouldBe 0
    _zero_reader(fixture, id, before)
    val facts = _facts(fixture, id, Some(task), 0, refusal.get)
    fixture.rows(id) shouldBe before
    facts
  }

  private def _zero_reader(fixture: Fixture, id: JobId, before: Vector[Option[Record]]): Unit = {
    fixture.original.startup.calls.get() shouldBe 1
    fixture.original.port.calls.get() shouldBe 0
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    fixture.engine.drainAll() shouldBe 0
    fixture.mutationCounts shouldBe Vector(0, 0, 0, 0)
    fixture.rows(id) shouldBe before
  }

  private def _submitted(fixture: Fixture, id: JobId, delay: Int): Unit = {
    val body = fixture.signed(id).record.body
    body.identity.revision shouldBe 1L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Submitted
    body.result shouldBe DurableResultOutcome.Pending
    body.lifecycle.schedule.scheduledAt shouldBe Some(JobDurableRuntimeFixtures.instant.plusSeconds(delay.toLong))
    body.lifecycle.schedule.startedAt shouldBe None
    body.lifecycle.schedule.completedAt shouldBe None
    fixture.native(id).seed.remaining shouldBe 1L
    fixture.native(id).seed.normalids shouldBe Set.empty
    _paid(fixture, id)
  }
  private def _running(fixture: Fixture, id: JobId): Unit = {
    val body = fixture.signed(id).record.body
    body.identity.revision shouldBe 4L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
    body.result shouldBe DurableResultOutcome.Pending
    body.lifecycle.schedule.startedAt should not be empty
    body.lifecycle.schedule.completedAt shouldBe None
    body.tasks.size shouldBe 1
    body.tasks.head.transaction.outcome shouldBe DurableTransactionOutcome.Pending
    fixture.native(id).seed.remaining shouldBe 0L
    fixture.native(id).seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    _paid(fixture, id)
  }
  private def _terminal(fixture: Fixture, id: JobId): Unit = {
    val body = fixture.signed(id).record.body
    body.identity.revision shouldBe 6L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.tasks.size shouldBe 1
    body.tasks.head.transaction.outcome shouldBe DurableTransactionOutcome.Committed
    fixture.native(id).seed.remaining shouldBe 0L
    fixture.native(id).seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    _paid(fixture, id)
  }
  private def _paid(fixture: Fixture, id: JobId): Unit = {
    val snapshot = fixture.signed(id)
    snapshot.record.body.identity.jobId shouldBe id.value
    snapshot.record.body.definitionSnapshot shouldBe DurableJobKeyedEvidenceFixtures.recipe().projection.definitionSnapshot
    val native = fixture.native(id)
    native.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    native.seed.claims shouldBe Set.empty
    native.cancelclaimed shouldBe false
  }

  private def _facts(fixture: Fixture, id: JobId, task: Option[JobDurableLifetimeRecoveryFixtures.Task],
    drain: Int, refusal: String): Map[String, String] = {
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
      "ready" -> (fixture.engine.durableOperationalHealth.get.state == JobDurableOperationalState.Ready).toString,
      "refusal" -> refusal, "port" -> fixture.original.port.calls.get().toString,
      "task" -> task.map(_.runs.get()).getOrElse(0).toString,
      "canonical" -> task.map(_.canonical.get()).getOrElse(0).toString,
      "queue" -> fixture.engine._work_queue.size().toString, "drain" -> drain.toString,
      "nativewrites" -> fixture.nativeWrites.toString, "sourcecalls" -> fixture.original.startup.calls.get().toString)
  }
  private def _identity(arguments: Arguments): Map[String, String] = {
    val identity = JobCrossProcessFixtures.identity()
    Map("mode" -> arguments.mode, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid)
  }
  private def _halt(arguments: Arguments, facts: Map[String, String]): Nothing = {
    val closure = Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed").map(_ -> "false").toMap
    JobScheduledCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ _identity(arguments) ++ closure)
    Runtime.getRuntime.halt(71)
    throw new IllegalStateException("owned JVM halt returned")
  }
}
