package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers

/*
 * A fresh native writer or reader. Receipts follow assertions and real closure.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
object JobCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    count: Int, ids: Vector[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new DurableJobBackupFixtures.Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    try {
      facts = arguments.mode match {
        case "writer" => _writer(fixture, arguments.count)
        case "reader" => _reader(fixture, arguments.ids, false)
        case "refused-reader" => _reader(fixture, arguments.ids, true)
        case _ => throw new IllegalArgumentException("closed probe mode required")
      }
      owner = fixture.engine._durable_runtime_owner_option
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
    val receipt = facts ++ Map("mode" -> arguments.mode, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid, "subsystemclosed" -> "true",
      "nodeclosed" -> (fixture.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> (!fixture.sql.get.managedResourceOption.get.isOpen).toString,
      "ownerclosed" -> owner.forall(_._backup_closed).toString)
    JobCrossProcessFixtures.writeReceipt(arguments.receipt, receipt)
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 4 && args.length <= 7, "probe requires mode/root/receipt/count and at most three native IDs")
    val mode = args(0)
    require(Set("writer", "reader", "refused-reader").contains(mode), "closed probe mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root), "explicit absolute native directory required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && !Files.exists(receipt),
      "new owned control receipt path required")
    require(root.getFileName.toString == "native" &&
      root.getParent.getFileName.toString.startsWith("case-"), "owned native case namespace required")
    val count = args(3).toInt
    require(count >= 0 && count <= 3, "probe count must be 0..3")
    val ids = args.drop(4).toVector.map(value => JobDurableRuntimeFixtures.success(JobId.parse(value)))
    require(ids.distinct.size == ids.size, "distinct native candidate IDs required")
    require(if (mode == "writer") ids.isEmpty else ids.size == count, "probe count/ID arguments must agree")
    require(mode != "refused-reader" || count == 1, "refused reader requires one genuine writer ID")
    Arguments(mode, root, receipt, count, ids)
  }

  private def _writer(fixture: DurableJobBackupFixtures.Fixture, count: Int): Map[String, String] = {
    JobDurableRuntimeFixtures.success(fixture.start())
    fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
    val tasks = Vector.fill(count)(new JobDurableLifetimeRecoveryFixtures.Task)
    val ids = tasks.map(task => JobDurableRuntimeFixtures.success(fixture.owner._execute_c {
      fixture.engine.submit(List(task), fixture.context,
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async))
    }))
    ids.distinct.size shouldBe count
    val drained = fixture.use(fixture.engine.drainAll())
    drained shouldBe count
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
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine._work_queue.size() shouldBe 0
    if (count == 0) _empty(fixture)
    _facts(fixture, ids, true, "", count == 0, drained, tasks.map(_.runs.get()).sum,
      tasks.map(_.getters.get()).sum) ++ _jobs(fixture, ids, "Available")
  }

  private def _reader(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId],
    refused: Boolean): Map[String, String] = {
    // This static recipe is created independently in this JVM. No writer
    // receipt, terminal record, task object or runtime closure is consumed.
    fixture.source.entries = ids.zipWithIndex.map { case (id, index) =>
      val access = if (refused) DurableJobBackupFixtures.access.copy(subjectId = "foreign_subject")
        else DurableJobBackupFixtures.access
      DurableJobStartupRecoveryCandidate(index.toLong, id.value, access, JobDurableLifetimeRecoveryFixtures.replay)
    }
    var before = Vector.empty[Option[Record]]
    fixture.source.before = () => before = _rows(fixture, ids)
    val started = fixture.start()
    val refusal = if (refused) {
      started.isSuccess shouldBe false
      val message = JobDurableRuntimeFixtures.failureConclusion(started).observation.getEffectiveMessage
      message shouldBe Some("durable runtime startup refused")
      fixture.engine.durableOperationalHealth.get.state should not be JobDurableOperationalState.Ready
      fixture.engine.durableTerminalProjections shouldBe Vector.empty
      fixture.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe
        Vector(DurableJobTerminalProjectionFact.Refused)
      ids.foreach(id => fixture.engine.query(id) shouldBe None)
      message.get
    } else {
      JobDurableRuntimeFixtures.success(started)
      fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
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
      ""
    }
    fixture.source.calls.get() shouldBe 1
    fixture.original.tasks.isEmpty shouldBe true
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    val drained = fixture.engine.drainAll()
    drained shouldBe 0
    fixture.entity.creates.get() shouldBe 0
    fixture.entity.writes.get() shouldBe 0
    fixture.entity.deletes.get() shouldBe 0
    fixture.entity.lifeWrites.get() shouldBe 0
    _rows(fixture, ids) shouldBe before
    if (ids.isEmpty) _empty(fixture)
    val jobfacts = _jobs(fixture, ids, if (refused) "None" else "UnavailableAfterRestart")
    _rows(fixture, ids) shouldBe before
    _facts(fixture, ids, !refused, refusal, true, drained, 0, 0) ++ jobfacts
  }

  private def _rows(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId]): Vector[Option[Record]] =
    (ids.map(id => fixture.jobEntityId(id.value)) ++ Vector(DurableJobBackupFixtures.storageId,
      DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)).map(fixture.row)

  private def _empty(fixture: DurableJobBackupFixtures.Fixture): Unit = {
    fixture.row(DurableJobBackupFixtures.storageId) shouldBe None
    fixture.row(DurableJobBackupFixtures.lifetimeId) shouldBe None
    fixture.row(DurableJobBackupFixtures.blobQuotaId) shouldBe None
    fixture.engine.listJobs() shouldBe Vector.empty
    fixture.engine.durableTerminalProjections shouldBe Vector.empty
    fixture.entity.creates.get() shouldBe 0
    fixture.entity.writes.get() shouldBe 0
    fixture.entity.deletes.get() shouldBe 0
  }

  private def _jobs(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId],
    facade: String): Map[String, String] = ids.zipWithIndex.flatMap { case (id, index) =>
    val snapshot = fixture.signed(id.value)
    val body = snapshot.record.body
    val native = fixture.use(JobDurableRuntimeFixtures.success(fixture.lifetime.snapshot(
      id.value, DurableJobBackupFixtures.access)(using fixture.context)))
    val definition = DurableJobKeyedEvidenceFixtures.recipe().projection.definitionSnapshot
    body.identity.jobId shouldBe id.value
    body.identity.revision shouldBe 6L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.definitionSnapshot shouldBe definition
    native.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    native.seed.remaining shouldBe 0L
    native.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    native.seed.normalids.size shouldBe 1
    native.seed.claims shouldBe Set.empty
    native.cancelclaimed shouldBe false
    Map("id" -> id.value, "revision" -> body.identity.revision.toString,
      "providerrevision" -> snapshot.providerrevision.toString, "status" -> body.lifecycle.status.wire,
      "result" -> "Succeeded(Absent)", "definitionid" -> body.definitionSnapshot.definitionId,
      "definitionkey" -> body.definitionSnapshot.key, "definitionversion" -> body.definitionSnapshot.version.toString,
      "definitionrevision" -> body.definitionSnapshot.revision.toString,
      "chargedtasks" -> native.seed.paid.chargedtasks.toString,
      "historycredits" -> native.seed.paid.historycredits.toString,
      "admissions" -> native.seed.paid.admissions.toString,
      "cancelcredits" -> native.seed.paid.cancelcredits.toString,
      "remaining" -> native.seed.remaining.toString, "normalid" -> native.seed.normalids.head,
      "claims" -> native.seed.claims.size.toString, "cancelclaimed" -> native.cancelclaimed.toString,
      "facade" -> facade).map { case (key, value) => s"job.$index.$key" -> value }
  }.toMap

  private def _facts(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId], ready: Boolean,
    refusal: String, rowsunchanged: Boolean, drained: Int, bodies: Int, getters: Int): Map[String, String] = {
    val lifetime = fixture.row(DurableJobBackupFixtures.lifetimeId)
    val paidroots = lifetime.map(row => JobDurableRuntimeFixtures.success(
      DurableJobLifetimeQuotaCodec.decode(row.getString("canonicalJson").get)).entries.count(
        _.rootstate == DurableJobLifetimeQuotaRootState.Published)).getOrElse(0)
    paidroots shouldBe ids.size
    val effects = fixture.effects
    Map("count" -> ids.size.toString, "ready" -> ready.toString, "refusal" -> refusal,
      "candidates" -> fixture.source.entries.size.toString,
      "registered" -> fixture.engine.durableTerminalProjectionReport.toVector.flatMap(_.candidates)
        .count(_.fact == DurableJobTerminalProjectionFact.Registered).toString,
      "runtimejobs" -> (fixture.engine.runtimeState.durableJobs.size() + fixture.engine.runtimeState.runtimeJobs.size()).toString,
      "terminaljobs" -> fixture.engine.durableTerminalProjections.size.toString,
      "ports" -> effects.ports.toString, "bodies" -> bodies.toString, "getters" -> getters.toString,
      "queue" -> effects.queue.toString, "drained" -> drained.toString, "creates" -> effects.creates.toString,
      "writes" -> effects.writes.toString, "deletes" -> effects.deletes.toString,
      "lifewrites" -> effects.lifewrites.toString, "rowsunchanged" -> rowsunchanged.toString,
      "lifetimepresent" -> lifetime.nonEmpty.toString,
      "storagepresent" -> fixture.row(DurableJobBackupFixtures.storageId).nonEmpty.toString,
      "blobquotapresent" -> fixture.row(DurableJobBackupFixtures.blobQuotaId).nonEmpty.toString,
      "paidroots" -> paidroots.toString)
  }
}
