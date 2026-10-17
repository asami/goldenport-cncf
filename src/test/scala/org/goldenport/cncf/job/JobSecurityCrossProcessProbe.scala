package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import JobSecurityCrossProcessFixtures.Mode

/*
 * Independent candidate authorization and trusted native observation in a fresh JVM.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object JobSecurityCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: Mode, root: Path, receipt: Path,
    count: Int, badindex: Int, ids: Vector[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new DurableJobBackupFixtures.Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    try {
      facts = _reader(fixture, arguments.ids, arguments.mode, arguments.badindex)
    } finally {
      owner = fixture.engine._durable_runtime_owner_option
      val subsystemresult = fixture.subsystem.shutdownC()
      val noderesult = fixture.node.shutdownC()
      try {
        JobDurableRuntimeFixtures.success(subsystemresult)
        JobDurableRuntimeFixtures.success(noderesult)
        fixture.node.state shouldBe SystemNode.State.Stopped
        fixture.node.activeLeaseCount shouldBe 0
        fixture.sql.get.managedResourceOption.get.isOpen shouldBe false
        fixture.effects.timers shouldBe 0
        if (arguments.mode.denied) {
          owner shouldBe None
        } else {
          owner.nonEmpty shouldBe true
          owner.get._backup_closed shouldBe true
          val scheduler = owner.get._scheduler.status
          scheduler.closed shouldBe true
          scheduler.started shouldBe false
          scheduler.inFlight shouldBe false
          scheduler.nextDueAt shouldBe None
        }
      } finally fixture.close()
    }
    val identity = JobCrossProcessFixtures.identity()
    JobSecurityCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode.wire, "badindex" -> arguments.badindex.toString,
      "root" -> arguments.root.toString, "sqlite" -> arguments.root.resolve("jobs.db").toString,
      "pid" -> identity.pid.toString, "start" -> identity.start.toString, "uuid" -> identity.uuid,
      "subsystemclosed" -> "true", "nodeclosed" -> (fixture.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> (!fixture.sql.get.managedResourceOption.get.isOpen).toString,
      "activeleases" -> fixture.node.activeLeaseCount.toString, "ownerinstalled" -> owner.nonEmpty.toString,
      "ownerclosure" -> (if (owner.nonEmpty) "installed-closed" else "not-installed")))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 6 && args.length <= 8, "mode/root/receipt/count/index and one to three IDs required")
    val mode = Mode.parse(args(0))
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root), "absolute native directory required")
    require(root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-security-cross-process",
      "owned security native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && !Files.exists(receipt), "new owned receipt required")
    val count = args(3).toInt
    val badindex = args(4).toInt
    require(count >= 1 && count <= 3 && badindex >= 0 && badindex < count, "bounded security count/index required")
    val ids = args.drop(5).toVector.map(value => JobDurableRuntimeFixtures.success(JobId.parse(value)))
    require(ids.size == count && ids.distinct.size == count, "unique security candidate count must agree")
    Arguments(mode, root, receipt, count, badindex, ids)
  }

  private def _reader(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId],
    mode: Mode, badindex: Int): Map[String, String] = {
    // Static access and replay are constructed here, without a writer receipt,
    // signed-body-derived access or a new executable Task in this reader.
    fixture.source.entries = ids.zipWithIndex.map { case (id, index) =>
      val access = if (index == badindex) mode.access else DurableJobBackupFixtures.access
      DurableJobStartupRecoveryCandidate(index.toLong, id.value, access, JobDurableLifetimeRecoveryFixtures.replay)
    }
    var before = Vector.empty[Option[Record]]
    fixture.source.before = () => before = _rows(fixture, ids)
    val idsinitial = fixture.original.ids.calls.get()
    val clocksinitial = fixture.original.clock.calls.get()
    val started = fixture.start()
    val refusal = if (mode.denied) {
      started.isSuccess shouldBe false
      val message = JobDurableRuntimeFixtures.failureConclusion(started).observation.getEffectiveMessage
      message shouldBe Some("durable runtime startup refused")
      message.get
    } else {
      JobDurableRuntimeFixtures.success(started)
      ""
    }
    val health = fixture.engine.durableOperationalHealth.get
    if (mode.denied) {
      health.state shouldBe JobDurableOperationalState.Degraded
      health.state should not be JobDurableOperationalState.Ready
      health.executionAvailable shouldBe false
      fixture.engine._durable_runtime_ready shouldBe false
      fixture.engine._durable_runtime_owner_option shouldBe None
    } else {
      health.state shouldBe JobDurableOperationalState.Ready
      health.executionAvailable shouldBe true
      fixture.engine._durable_runtime_ready shouldBe true
      fixture.engine._durable_runtime_owner_option.nonEmpty shouldBe true
      fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
    }
    val timersinitial = fixture.effects.timers
    timersinitial shouldBe (if (mode.denied) 0 else 1)
    val schedulerinitial = fixture.engine._durable_runtime_owner_option.map(_._scheduler.status)
    if (mode.denied) {
      schedulerinitial shouldBe None
    } else {
      val scheduler = schedulerinitial.get
      scheduler.started shouldBe true
      scheduler.closed shouldBe false
      scheduler.inFlight shouldBe false
      scheduler.nextDueAt.nonEmpty shouldBe true
      scheduler.lastResult shouldBe None
    }
    fixture.source.calls.get() shouldBe 1
    before.size shouldBe ids.size + 3
    val terminal = fixture.engine.durableTerminalProjectionReport.get
    val runtime = fixture.engine.durableRuntimeRehydrationReport.get
    val ordinals = ids.indices.map(_.toLong).toVector
    terminal.candidates.map(_.jobId) shouldBe ids.map(_.value)
    terminal.candidates.map(_.ordinal) shouldBe ordinals
    runtime.candidates.map(_.jobId) shouldBe ids.map(_.value)
    runtime.candidates.map(_.ordinal) shouldBe ordinals
    terminal.candidates.map(_.fact) shouldBe ids.indices.map(index =>
      if (mode.denied && index == badindex) DurableJobTerminalProjectionFact.Refused
      else DurableJobTerminalProjectionFact.Registered).toVector
    runtime.candidates.map(_.fact) shouldBe Vector.fill(ids.size)(DurableJobRuntimeRehydrationFact.Refused)
    fixture.engine.durableTerminalProjections.size shouldBe ids.size - (if (mode.denied) 1 else 0)
    val jobfacts = ids.zipWithIndex.flatMap { case (id, index) =>
      val denied = mode.denied && index == badindex
      fixture.engine.getResult(id) shouldBe None
      val facade = if (denied) {
        fixture.engine.query(id) shouldBe None
        fixture.engine.getStatus(id) shouldBe None
        fixture.engine.durableTerminalProjection(id) shouldBe None
        // The denied candidate's access reaches the original typed native loader.
        // The job_admin ExecutionContext cannot override tenant/subject/scopes.
        fixture.use(fixture.store.loadStartupRecovery(id.value, fixture.source.entries(index).access)(using fixture.context)) shouldBe
          DurableJobStoreStartupRecoveryLoad.Refused
        "None"
      } else {
        val query = fixture.engine.query(id).get
        query.status shouldBe JobStatus.Succeeded
        fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
        query.origin shouldBe JobDataOrigin.Durable
        query.persistence shouldBe JobPersistencePolicy.Persistent
        query.input shouldBe None
        query.calltree shouldBe None
        query.result shouldBe None
        fixture.engine.durableTerminalProjection(id).get.snapshot shouldBe fixture.signed(id.value)
        if (!mode.denied) {
          JobDurableRuntimeFixtures.success(fixture.engine.queryManagementResult(id)(using fixture.context)) shouldBe
            Some(JobManagementResult.UnavailableAfterRestart(query.resultSummary))
          "UnavailableAfterRestart"
        } else {
          // Mixed refusal preserves healthy projections, without an installed owner.
          "NotObserved"
        }
      }
      // This separately trusted observer proves native integrity and payment;
      // its original access is never substituted into the denied candidate.
      (_native_job(fixture, id) ++ Map("id" -> id.value, "ordinal" -> terminal.candidates(index).ordinal.toString,
        "terminalfact" -> terminal.candidates(index).fact.toString,
        "runtimefact" -> runtime.candidates(index).fact.toString,
        "queryvisible" -> fixture.engine.query(id).nonEmpty.toString, "facade" -> facade))
        .map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
    fixture.original.tasks.isEmpty shouldBe true
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    val drained = fixture.use(fixture.engine.drainAll())
    drained shouldBe 0
    val effects = fixture.effects
    Vector(effects.ports, effects.creates, effects.writes, effects.deletes, effects.lifewrites,
      effects.queue, effects.getters, effects.bodies).foreach(value => value shouldBe 0)
    fixture.engine.resourceSnapshot.pending shouldBe 0
    fixture.engine.resourceSnapshot.executing shouldBe 0
    effects.ids shouldBe idsinitial
    effects.clocks shouldBe clocksinitial
    _rows(fixture, ids) shouldBe before
    val lifetime = fixture.row(DurableJobBackupFixtures.lifetimeId)
    val paidroots = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaCodec.decode(
      lifetime.get.getString("canonicalJson").get)).entries.count(_.rootstate == DurableJobLifetimeQuotaRootState.Published)
    paidroots shouldBe ids.size
    _rows(fixture, ids) shouldBe before
    fixture.effects.timers shouldBe timersinitial
    fixture.engine._durable_runtime_owner_option.map(_._scheduler.status) shouldBe schedulerinitial
    Map("count" -> ids.size.toString, "ready" -> (health.state == JobDurableOperationalState.Ready).toString,
      "health" -> health.state.toString, "executionavailable" -> health.executionAvailable.toString,
      "runtimeready" -> fixture.engine._durable_runtime_ready.toString, "refusal" -> refusal,
      "candidates" -> fixture.source.entries.size.toString, "sourcecalls" -> fixture.source.calls.get().toString,
      "registered" -> terminal.candidates.count(_.fact == DurableJobTerminalProjectionFact.Registered).toString,
      "runtimejobs" -> (fixture.engine.runtimeState.durableJobs.size() + fixture.engine.runtimeState.runtimeJobs.size()).toString,
      "terminaljobs" -> fixture.engine.durableTerminalProjections.size.toString, "ports" -> effects.ports.toString,
      "bodies" -> effects.bodies.toString, "getters" -> effects.getters.toString, "queue" -> effects.queue.toString,
      "drained" -> drained.toString, "creates" -> effects.creates.toString, "writes" -> effects.writes.toString,
      "deletes" -> effects.deletes.toString, "lifewrites" -> effects.lifewrites.toString,
      "ids" -> (effects.ids - idsinitial).toString, "clocks" -> (effects.clocks - clocksinitial).toString,
      "timers" -> effects.timers.toString, "rowsunchanged" -> "true", "lifetimepresent" -> lifetime.nonEmpty.toString,
      "storagepresent" -> fixture.row(DurableJobBackupFixtures.storageId).nonEmpty.toString,
      "blobquotapresent" -> fixture.row(DurableJobBackupFixtures.blobQuotaId).nonEmpty.toString,
      "paidroots" -> paidroots.toString) ++ jobfacts
  }

  private def _rows(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId]): Vector[Option[Record]] = {
    (ids.map(id => fixture.jobEntityId(id.value)) ++ Vector(DurableJobBackupFixtures.storageId,
      DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)).map(fixture.row)
  }

  private def _native_job(fixture: DurableJobBackupFixtures.Fixture, id: JobId): Map[String, String] = {
    val snapshot = fixture.signed(id.value)
    val body = snapshot.record.body
    snapshot.record.format shouldBe DurableRecordFormat.V2
    body.identity.jobId shouldBe id.value
    body.identity.revision shouldBe 6L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.definitionSnapshot shouldBe DurableJobKeyedEvidenceFixtures.recipe().projection.definitionSnapshot
    body.tasks.size shouldBe 1
    body.tasks.head.transaction shouldBe DurableTransactionDescriptor(DurableTransactionRole.Own,
      DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed)
    val native = fixture.use(JobDurableRuntimeFixtures.success(fixture.lifetime.snapshot(
      id.value, DurableJobBackupFixtures.access)(using fixture.context)))
    native.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    native.seed.remaining shouldBe 0L
    native.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    native.seed.normalids.size shouldBe 1
    native.seed.claims shouldBe Set.empty
    native.cancelclaimed shouldBe false
    Map("revision" -> body.identity.revision.toString, "providerrevision" -> snapshot.providerrevision.toString,
      "status" -> body.lifecycle.status.wire, "result" -> "Succeeded(Absent)",
      "definitionid" -> body.definitionSnapshot.definitionId, "definitionkey" -> body.definitionSnapshot.key,
      "definitionversion" -> body.definitionSnapshot.version.toString,
      "definitionrevision" -> body.definitionSnapshot.revision.toString,
      "chargedtasks" -> native.seed.paid.chargedtasks.toString, "historycredits" -> native.seed.paid.historycredits.toString,
      "admissions" -> native.seed.paid.admissions.toString, "cancelcredits" -> native.seed.paid.cancelcredits.toString,
      "remaining" -> native.seed.remaining.toString, "normalid" -> native.seed.normalids.head,
      "claims" -> native.seed.claims.size.toString, "cancelclaimed" -> native.cancelclaimed.toString)
  }
}
