package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import JobCursorCrossProcessFixtures.Mode

/*
 * Native writer, appender and independent summary-only cursor readers.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object JobCursorCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: Mode, root: Path, receipt: Path,
    count: Int, firstlimit: Int, cursor: String, ids: Vector[JobId])

  private final class ReadPolicy extends JobQueryPolicy {
    val calls = new AtomicInteger
    def authorizeRead(model: JobQueryReadModel)(using context: ExecutionContext): Consequence[Unit] = {
      calls.incrementAndGet()
      JobQueryPolicy.default.authorizeRead(model)
    }
  }

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new DurableJobBackupFixtures.Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    try {
      facts = arguments.mode match {
        case Mode.Writer => _writer(fixture, arguments.count)
        case Mode.Appender => _appender(fixture, arguments.ids)
        case _ => _reader(fixture, arguments)
      }
      owner = fixture.engine._durable_runtime_owner_option
      owner.nonEmpty shouldBe true
    } finally {
      val subsystemresult = fixture.subsystem.shutdownC()
      val noderesult = fixture.node.shutdownC()
      try {
        JobDurableRuntimeFixtures.success(subsystemresult)
        JobDurableRuntimeFixtures.success(noderesult)
        fixture.node.state shouldBe SystemNode.State.Stopped
        fixture.node.activeLeaseCount shouldBe 0
        fixture.sql.get.managedResourceOption.get.isOpen shouldBe false
        owner.foreach(value => value._backup_closed shouldBe true)
      } finally fixture.close()
    }
    val identity = JobCrossProcessFixtures.identity()
    JobCursorCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode.wire, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid,
      "subsystemclosed" -> "true", "nodeclosed" -> (fixture.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> (!fixture.sql.get.managedResourceOption.get.isOpen).toString,
      "ownerclosed" -> owner.exists(_._backup_closed).toString,
      "leases" -> fixture.node.activeLeaseCount.toString))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 6 && args.length <= 9, "closed cursor process arguments required")
    val mode = Mode.values.find(_.wire == args(0)).getOrElse(
      throw new IllegalArgumentException("closed cursor process mode required"))
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-"),
      "owned absolute native cursor namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && !Files.exists(receipt),
      "new owned cursor receipt path required")
    val count = args(3).toInt
    val firstlimit = args(4).toInt
    require(count >= 2 && count <= 3 && firstlimit >= 1 && firstlimit < count,
      "bounded independent count and first limit required")
    val cursor = args(5)
    require(cursor.length <= 280, "bounded opaque API cursor required")
    val continuation = Set(Mode.ContinueReader, Mode.FilterReader, Mode.CallerReader, Mode.ExpiredReader).contains(mode)
    require(continuation == cursor.nonEmpty, "only continuation modes receive an actual API cursor")
    val ids = args.drop(6).toVector.map(value => JobDurableRuntimeFixtures.success(JobId.parse(value)))
    require(ids.distinct.size == ids.size && (if (mode == Mode.Writer) ids.isEmpty else ids.size == count),
      "distinct native candidate arguments required")
    require(mode != Mode.Appender || count == 2, "appender starts with two genuine Jobs")
    require(mode != Mode.ExpiredReader || count == 3, "expired reader starts with three genuine Jobs")
    Arguments(mode, root, receipt, count, firstlimit, cursor, ids.sortBy(_.print))
  }

  private def _writer(fixture: DurableJobBackupFixtures.Fixture, count: Int): Map[String, String] = {
    JobDurableRuntimeFixtures.success(fixture.start())
    fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
    val tasks = Vector.fill(count)(new JobDurableLifetimeRecoveryFixtures.Task)
    val ids = _submit(fixture, tasks).sortBy(_.print)
    ids.distinct.size shouldBe count
    fixture.use(fixture.engine.drainAll()) shouldBe count
    _executed(fixture, tasks, ids)
    fixture.entity.creates.get() should be > 0
    fixture.entity.writes.get() should be > 0
    fixture.entity.lifeWrites.get() should be > 0
    _facts(fixture, ids, tasks, count, false, false, false, false) ++ _no_page ++ _jobs(fixture, ids)
  }

  private def _appender(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId]): Map[String, String] = {
    val before = _open(fixture, ids)
    _jobs(fixture, ids)
    _zero_reader(fixture, ids, before)
    val oldrows = ids.map(id => fixture.row(fixture.jobEntityId(id.value)))
    val oldsigned = ids.map(id => fixture.signed(id.value))
    val oldpayments = ids.map(id => fixture.use(JobDurableRuntimeFixtures.success(
      fixture.lifetime.snapshot(id.value, DurableJobBackupFixtures.access)(using fixture.context))))
    val oldquota = _quota(fixture)
    oldquota.entries.count(_.rootstate == DurableJobLifetimeQuotaRootState.Published) shouldBe 2
    val task = new JobDurableLifetimeRecoveryFixtures.Task
    val added = _submit(fixture, Vector(task))
    added should have size 1
    ids should not contain added.head
    fixture.use(fixture.engine.drainAll()) shouldBe 1
    _executed(fixture, Vector(task), added)
    ids.map(id => fixture.row(fixture.jobEntityId(id.value))) shouldBe oldrows
    ids.map(id => fixture.signed(id.value)) shouldBe oldsigned
    ids.map(id => fixture.use(JobDurableRuntimeFixtures.success(
      fixture.lifetime.snapshot(id.value, DurableJobBackupFixtures.access)(using fixture.context)))) shouldBe oldpayments
    val quota = _quota(fixture)
    quota.entries.filter(entry => ids.exists(_.value == entry.jobid)) shouldBe oldquota.entries
    quota.entries.count(_.rootstate == DurableJobLifetimeQuotaRootState.Published) shouldBe 3
    fixture.entity.writes.get() should be > 0
    fixture.entity.lifeWrites.get() should be > 0
    val all = (ids ++ added).sortBy(_.print)
    _facts(fixture, all, Vector(task), 1, false, true, false, false) ++ _no_page ++ _jobs(fixture, all)
  }

  private def _submit(fixture: DurableJobBackupFixtures.Fixture,
    tasks: Vector[JobDurableLifetimeRecoveryFixtures.Task]): Vector[JobId] =
    tasks.map(task => JobDurableRuntimeFixtures.success(fixture.owner._execute_c {
      fixture.engine.submit(List(task), fixture.context,
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async))
    }))

  private def _executed(fixture: DurableJobBackupFixtures.Fixture,
    tasks: Vector[JobDurableLifetimeRecoveryFixtures.Task], ids: Vector[JobId]): Unit = {
    tasks.foreach { task =>
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 1
    }
    ids.foreach { id =>
      fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
      fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
      JobDurableRuntimeFixtures.success(fixture.engine.queryManagementResult(id)(using fixture.context)) shouldBe
        Some(JobManagementResult.Available(JobResult.Success(OperationResponse.Scalar("ok"))))
      val requests = fixture.original.evidence.requests.filter(_.jobid == id)
      requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
      requests.map(_.expectedrevision) shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
    }
    fixture.original.evidence.requests.size shouldBe 6 * ids.size
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine._work_queue.size() shouldBe 0
  }

  private def _open(fixture: DurableJobBackupFixtures.Fixture,
    ids: Vector[JobId]): Vector[Option[Record]] = {
    // Independent static candidates; no receipt, Task, record or caller state is imported.
    fixture.source.entries = ids.zipWithIndex.map { case (id, index) =>
      DurableJobStartupRecoveryCandidate(index.toLong, id.value, DurableJobBackupFixtures.access,
        JobDurableLifetimeRecoveryFixtures.replay)
    }
    var before = Vector.empty[Option[Record]]
    fixture.source.before = () => before = _rows(fixture, ids)
    JobDurableRuntimeFixtures.success(fixture.start())
    fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
    fixture.source.calls.get() shouldBe 1
    val report = fixture.engine.durableTerminalProjectionReport.get
    report.candidates.map(_.jobId) shouldBe ids.map(_.value)
    report.candidates.map(_.fact) shouldBe Vector.fill(ids.size)(DurableJobTerminalProjectionFact.Registered)
    ids.foreach { id =>
      val snapshot = fixture.signed(id.value)
      fixture.engine.durableTerminalProjection(id).get.snapshot shouldBe snapshot
      val query = fixture.engine.query(id).get
      query.status shouldBe JobStatus.Succeeded
      query.origin shouldBe JobDataOrigin.Durable
      query.persistence shouldBe JobPersistencePolicy.Persistent
      query.input shouldBe None
      query.calltree shouldBe None
      query.result shouldBe None
      fixture.engine.getResult(id) shouldBe None
      JobDurableRuntimeFixtures.success(fixture.engine.queryManagementResult(id)(using fixture.context)) shouldBe
        Some(JobManagementResult.UnavailableAfterRestart(query.resultSummary))
    }
    _rows(fixture, ids) shouldBe before
    before
  }

  private def _reader(fixture: DurableJobBackupFixtures.Fixture, arguments: Arguments): Map[String, String] = {
    val ids = arguments.ids
    val before = _open(fixture, ids)
    val signed = ids.map(id => fixture.signed(id.value))
    val nativefacts = _jobs(fixture, ids)
    val policy = new ReadPolicy
    policy.visibilityKey shouldBe ""
    val request = JobManagementQuery(cursor = Option.when(arguments.cursor.nonEmpty)(
      JobManagementCursor.fromOpaque(arguments.cursor)))
    val outcome = arguments.mode match {
      case Mode.FirstReader =>
        val page = _page(fixture, JobManagementQuery(limit = arguments.firstlimit), policy, fixture.context,
          ids.take(arguments.firstlimit))
        page.totalCount shouldBe ids.size
        page.nextCursor.nonEmpty shouldBe true
        page.nextCursor.get.value.length should (be > 0 and be <= 280)
        policy.calls.get() shouldBe ids.size
        _page_facts(page)
      case Mode.ContinueReader =>
        request.limit shouldBe 100
        val page = _page(fixture, request, policy, fixture.context, ids.drop(arguments.firstlimit))
        page.totalCount shouldBe ids.size
        page.nextCursor shouldBe None
        policy.calls.get() shouldBe ids.size
        _page_facts(page)
      case Mode.FilterReader =>
        val result = fixture.engine.queryPage(request.copy(status = Some(JobStatus.Failed)), policy)(using fixture.context)
        _failure(result, "invalid cursor: request filter mismatch", true)
        policy.calls.get() shouldBe ids.size
        _refusal_facts("invalid cursor: request filter mismatch", 1)
      case Mode.CallerReader =>
        val foreign = ExecutionContext.withSecurityContext(fixture.context, fixture.context.security.copy(
          principal = new Principal {
            def id: PrincipalId = PrincipalId("foreign-cursor-subject")
            def attributes: Map[String, String] = Map.empty
          }, capabilities = Set.empty, session = None))
        ids.foreach(id => JobQueryPolicy.default.authorizeRead(fixture.engine.query(id).get)(using foreign).isSuccess shouldBe false)
        val result = fixture.engine.queryPage(request, policy)(using foreign)
        _failure(result, "invalid cursor: caller visibility mismatch", true)
        policy.calls.get() shouldBe ids.size
        val page = _page(fixture, JobManagementQuery(), policy, foreign, Vector.empty)
        page.totalCount shouldBe 0
        page.nextCursor shouldBe None
        policy.calls.get() shouldBe 2 * ids.size
        _page_facts(page) ++ _refusal_facts("invalid cursor: caller visibility mismatch", 1)
      case Mode.InvalidReader =>
        Vector("%", "x" * 281).foreach { token =>
          val result = fixture.engine.queryPage(JobManagementQuery(cursor = Some(JobManagementCursor.fromOpaque(token))), policy)(using fixture.context)
          _failure(result, "invalid cursor: malformed cursor", true)
          policy.calls.get() shouldBe 0
        }
        _refusal_facts("invalid cursor: malformed cursor", 2)
      case Mode.BoundsReader =>
        Vector(0, 101).foreach { limit =>
          val result = fixture.engine.queryPage(JobManagementQuery(limit = limit), policy)(using fixture.context)
          _failure(result, "limit must be in 1..100", false)
          policy.calls.get() shouldBe 0
        }
        _refusal_facts("limit must be in 1..100", 2)
      case Mode.ExpiredReader =>
        val result = fixture.engine.queryPage(request, policy)(using fixture.context)
        _failure(result, "expired snapshot: authorized source changed", true)
        policy.calls.get() shouldBe ids.size
        _refusal_facts("expired snapshot: authorized source changed", 1)
      case _ => throw new IllegalArgumentException("closed reader mode required")
    }
    _rows(fixture, ids) shouldBe before
    fixture.engine.drainAll() shouldBe 0
    _zero_reader(fixture, ids, before)
    ids.map(id => fixture.signed(id.value)) shouldBe signed
    _jobs(fixture, ids) shouldBe nativefacts
    _rows(fixture, ids) shouldBe before
    _facts(fixture, ids, Vector.empty, 0, true, true, true, true) ++ nativefacts ++ outcome ++
      Map("policycalls" -> policy.calls.get().toString)
  }

  private def _page(fixture: DurableJobBackupFixtures.Fixture, request: JobManagementQuery,
    policy: ReadPolicy, context: ExecutionContext, expected: Vector[JobId]): JobManagementPage = {
    val page = JobDurableRuntimeFixtures.success(fixture.engine.queryPage(request, policy)(using context))
    page.entries.map(_.jobId) shouldBe expected
    page.entries.foreach { summary =>
      summary.productElementNames.toSet shouldBe Set("jobId", "status", "persistence", "origin",
        "createdAt", "updatedAt", "scheduledStartAt")
      val native = fixture.signed(summary.jobId.value).record.body
      summary shouldBe JobManagementSummary(summary.jobId, JobStatus.Succeeded,
        JobPersistencePolicy.Persistent, JobDataOrigin.Durable, native.identity.createdAt,
        native.identity.updatedAt, native.lifecycle.schedule.scheduledAt)
    }
    page
  }

  private def _failure(result: Consequence[JobManagementPage], message: String, cursor: Boolean): Unit = {
    result.isSuccess shouldBe false
    val conclusion = JobDurableRuntimeFixtures.failureConclusion(result)
    val expected = JobDurableRuntimeFixtures.failureConclusion(Consequence.operationInvalid[JobManagementPage](
      if (cursor) "job.management-query.cursor" else "job.management-query", message))
    conclusion.status shouldBe expected.status
    conclusion.interpretation shouldBe expected.interpretation
    conclusion.disposition shouldBe expected.disposition
    conclusion.observation.getEffectiveMessage shouldBe Some(message)
    conclusion.observation.getEffectiveMessage.get should not include "foreign-cursor-subject"
  }

  private def _zero_reader(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId], before: Vector[Option[Record]]): Unit = {
    fixture.original.tasks.isEmpty shouldBe true
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    fixture.entity.creates.get() shouldBe 0
    fixture.entity.writes.get() shouldBe 0
    fixture.entity.deletes.get() shouldBe 0
    fixture.entity.lifeWrites.get() shouldBe 0
    _rows(fixture, ids) shouldBe before
  }

  private def _rows(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId]): Vector[Option[Record]] =
    (ids.map(id => fixture.jobEntityId(id.value)) ++ Vector(DurableJobBackupFixtures.storageId,
      DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)).map(fixture.row)

  private def _quota(fixture: DurableJobBackupFixtures.Fixture): DurableJobLifetimeQuotaRecord =
    JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaCodec.decode(
      fixture.row(DurableJobBackupFixtures.lifetimeId).get.getString("canonicalJson").get))

  private def _jobs(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId]): Map[String, String] = {
    ids.map(id => fixture.signed(id.value).record.body.identity.updatedAt).distinct.size shouldBe 1
    ids shouldBe ids.sortBy(_.print)
    ids.zipWithIndex.flatMap { case (id, index) =>
      val signed = fixture.signed(id.value)
      val body = signed.record.body
      val native = fixture.use(JobDurableRuntimeFixtures.success(fixture.lifetime.snapshot(
        id.value, DurableJobBackupFixtures.access)(using fixture.context)))
      body.identity.jobId shouldBe id.value
      body.identity.revision shouldBe 6L
      body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
      body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
      body.definitionSnapshot shouldBe DurableJobKeyedEvidenceFixtures.recipe().projection.definitionSnapshot
      body.tasks should have size 1
      body.tasks.head.transaction shouldBe DurableTransactionDescriptor(DurableTransactionRole.Own,
        DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed)
      native.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
      native.seed.remaining shouldBe 0L
      native.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
      native.seed.normalids.size shouldBe 1
      native.seed.claims shouldBe Set.empty
      native.cancelclaimed shouldBe false
      Map("id" -> id.value, "revision" -> body.identity.revision.toString,
        "providerrevision" -> signed.providerrevision.toString, "status" -> body.lifecycle.status.wire,
        "result" -> "Succeeded(Absent)", "chargedtasks" -> native.seed.paid.chargedtasks.toString,
        "historycredits" -> native.seed.paid.historycredits.toString, "admissions" -> native.seed.paid.admissions.toString,
        "cancelcredits" -> native.seed.paid.cancelcredits.toString, "remaining" -> native.seed.remaining.toString,
        "normalcount" -> native.seed.normalids.size.toString, "claims" -> native.seed.claims.size.toString,
        "cancelclaimed" -> native.cancelclaimed.toString).map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
  }

  private def _facts(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId],
    tasks: Vector[JobDurableLifetimeRecoveryFixtures.Task], drained: Int, rowsunchanged: Boolean,
    oldrowsunchanged: Boolean, rawabsent: Boolean, managementunavailable: Boolean): Map[String, String] = {
    val paidroots = _quota(fixture).entries.count(_.rootstate == DurableJobLifetimeQuotaRootState.Published)
    paidroots shouldBe ids.size
    fixture.source.calls.get() shouldBe 1
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine._work_queue.size() shouldBe 0
    Map("count" -> ids.size.toString, "ready" -> (fixture.owner.health.state == JobDurableOperationalState.Ready).toString,
      "sourcecalls" -> fixture.source.calls.get().toString,
      "registered" -> fixture.engine.durableTerminalProjectionReport.toVector.flatMap(_.candidates)
        .count(_.fact == DurableJobTerminalProjectionFact.Registered).toString,
      "runtimejobs" -> (fixture.engine.runtimeState.durableJobs.size() + fixture.engine.runtimeState.runtimeJobs.size()).toString,
      "ports" -> fixture.original.port.calls.get().toString, "tasks" -> tasks.map(_.runs.get()).sum.toString,
      "canonical" -> tasks.map(_.canonical.get()).sum.toString, "getters" -> tasks.map(_.getters.get()).sum.toString,
      "queue" -> fixture.engine._work_queue.size().toString, "drained" -> drained.toString,
      "creates" -> fixture.entity.creates.get().toString, "writes" -> fixture.entity.writes.get().toString,
      "deletes" -> fixture.entity.deletes.get().toString, "lifewrites" -> fixture.entity.lifeWrites.get().toString,
      "keyedcalls" -> fixture.original.evidence.requests.size.toString, "rowsunchanged" -> rowsunchanged.toString,
      "oldrowsunchanged" -> oldrowsunchanged.toString, "rawabsent" -> rawabsent.toString,
      "managementunavailable" -> managementunavailable.toString, "paidroots" -> paidroots.toString)
  }

  private def _no_page: Map[String, String] = Map("policycalls" -> "0", "outcome" -> "none", "failures" -> "0",
    "pagecount" -> "0", "total" -> "0", "cursor" -> "")

  private def _page_facts(page: JobManagementPage): Map[String, String] =
    Map("outcome" -> "page", "failures" -> "0", "pagecount" -> page.entries.size.toString,
      "total" -> page.totalCount.toString, "cursor" -> page.nextCursor.map(_.value).getOrElse("")) ++
      page.entries.zipWithIndex.map { case (summary, index) => s"page.$index.id" -> summary.jobId.value }

  private def _refusal_facts(message: String, failures: Int): Map[String, String] =
    _no_page ++ Map("outcome" -> message, "failures" -> failures.toString)
}
