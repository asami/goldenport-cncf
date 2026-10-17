package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import JobNativeRefusalCrossProcessFixtures.Fault

/*
 * Genuine execution precedes one negative input; fresh startup observes native refusal.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object JobNativeRefusalCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    count: Int, fault: Fault, badindex: Int, ids: Vector[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new DurableJobBackupFixtures.Fixture(arguments.root)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    try {
      facts = arguments.mode match {
        case "writer" => _writer(fixture, arguments.count, arguments.fault, arguments.badindex)
        case "refused-reader" => _reader(fixture, arguments.ids, arguments.fault, arguments.badindex)
        case _ => throw new IllegalArgumentException("closed native refusal mode required")
      }
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
        if (arguments.mode == "writer") {
          owner.nonEmpty shouldBe true
          owner.get._backup_closed shouldBe true
        } else {
          owner shouldBe None
        }
      } finally fixture.close()
    }
    val identity = JobCrossProcessFixtures.identity()
    JobNativeRefusalCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode, "fault" -> arguments.fault.wire, "badindex" -> arguments.badindex.toString,
      "root" -> arguments.root.toString, "sqlite" -> arguments.root.resolve("jobs.db").toString,
      "pid" -> identity.pid.toString, "start" -> identity.start.toString, "uuid" -> identity.uuid,
      "subsystemclosed" -> "true", "nodeclosed" -> (fixture.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> (!fixture.sql.get.managedResourceOption.get.isOpen).toString,
      "activeleases" -> fixture.node.activeLeaseCount.toString, "ownerinstalled" -> owner.nonEmpty.toString,
      "ownerclosure" -> (if (owner.nonEmpty) "installed-closed" else "not-installed")))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 6 && args.length <= 9, "mode/root/receipt/count/fault/badindex and at most three IDs required")
    val mode = args(0)
    require(Set("writer", "refused-reader").contains(mode), "closed native refusal mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root), "absolute native directory required")
    require(root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-native-refusal-cross-process",
      "owned native refusal namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && !Files.exists(receipt), "new owned receipt required")
    val count = args(3).toInt
    val fault = Fault.parse(args(4))
    val badindex = args(5).toInt
    require(count >= 1 && count <= 3 && badindex >= 0 && badindex < count, "bounded native fault case required")
    val ids = args.drop(6).toVector.map(value => JobDurableRuntimeFixtures.success(JobId.parse(value)))
    require(ids.distinct.size == ids.size && (if (mode == "writer") ids.isEmpty else ids.size == count),
      "unique native candidate count must agree")
    Arguments(mode, root, receipt, count, fault, badindex, ids)
  }

  private def _writer(fixture: DurableJobBackupFixtures.Fixture, count: Int,
    fault: Fault, badindex: Int): Map[String, String] = {
    JobDurableRuntimeFixtures.success(fixture.start())
    fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
    val tasks = Vector.fill(count)(new JobDurableLifetimeRecoveryFixtures.Task)
    val ids = tasks.map(task => JobDurableRuntimeFixtures.success(fixture.owner._execute_c {
      fixture.engine.submit(List(task), fixture.context,
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async))
    }))
    ids.distinct.size shouldBe count
    ids.foreach(id => JobDurableRuntimeFixtures.success(JobId.parse(id.value)) shouldBe id)
    val drained = fixture.use(fixture.engine.drainAll())
    drained shouldBe count
    tasks.foreach { task =>
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 1
    }
    ids.foreach { id =>
      fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
      fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
      val requests = fixture.original.evidence.requests.filter(_.jobid == id)
      requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
      requests.map(_.expectedrevision) shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
    }
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine._work_queue.size() shouldBe 0
    // All positive terminal/payment observations precede the single deliberate fault.
    val jobfacts = ids.zipWithIndex.flatMap { case (id, index) =>
      (_terminal(fixture, id) ++ _payment(fixture, id) ++ Map("id" -> id.value,
        "ordinal" -> index.toString, "terminalfact" -> "PreFaultSucceeded", "runtimefact" -> "NotRecovered"))
        .map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
    val facts = _facts(fixture, ids, "pre-fault", true, "", drained,
      tasks.map(_.runs.get()).sum, tasks.map(_.getters.get()).sum, 0, 0) ++ jobfacts
    _fault(fixture, ids, fault, badindex)
    facts
  }

  private def _fault(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId],
    fault: Fault, badindex: Int): Unit = {
    val before = _rows(fixture, ids)
    val entityid = fixture.jobEntityId(ids(badindex).value)
    val original = before(badindex).get
    val text = original.getString("canonicalJson").get
    val damaged = fault match {
      case Fault.Malformed => Some("PRIVATE invalid canonical credential text")
      case Fault.Integrity =>
        val digest = fixture.signed(ids(badindex).value).record.integrity.unsignedBodySha256
        val replacement = if (digest == "0" * 64) "1" * 64 else "0" * 64
        replacement should fullyMatch regex "[0-9a-f]{64}"
        replacement should not be digest
        Some(_replace_once(text, s"\"unsignedBodySha256\":\"$digest\"", s"\"unsignedBodySha256\":\"$replacement\""))
      case Fault.FutureVersion =>
        val prefix = s"{\"format\":{\"schemaId\":\"${DurableRecordFormat.V2.schemaId}\",\"version\":2},\"record\":"
        text.startsWith(prefix) shouldBe true
        Some(_replace_once(text, prefix, prefix.replace("\"version\":2", "\"version\":3")))
      case Fault.Noncanonical => Some(text + " ")
      case Fault.Missing => None
    }
    damaged match {
      case Some(value) => fixture.replaceText(entityid, value)
      case None => fixture.removeRow(entityid)
    }
    val after = _rows(fixture, ids)
    after.size shouldBe before.size
    after(badindex) should not be before(badindex)
    damaged match {
      case Some(value) =>
        val changed = after(badindex).get
        changed.getString("canonicalJson") shouldBe Some(value)
        changed.removeKeys(Set("canonicalJson")) shouldBe original.removeKeys(Set("canonicalJson"))
      case None => after(badindex) shouldBe None
    }
    before.indices.filterNot(_ == badindex).foreach(index => after(index) shouldBe before(index))
    _bad_native(fixture, ids(badindex), fault)
    ids.zipWithIndex.filterNot(_._2 == badindex).foreach { case (id, _) => _terminal(fixture, id); () }
    _rows(fixture, ids) shouldBe after
  }

  private def _replace_once(text: String, old: String, replacement: String): String = {
    val index = text.indexOf(old)
    index should be >= 0
    text.lastIndexOf(old) shouldBe index
    text.take(index) + replacement + text.drop(index + old.length)
  }

  private def _reader(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId],
    fault: Fault, badindex: Int): Map[String, String] = {
    // This JVM constructs its own original access/replay/recipe, never a writer receipt or Task.
    fixture.source.entries = ids.zipWithIndex.map { case (id, index) =>
      DurableJobStartupRecoveryCandidate(index.toLong, id.value, DurableJobBackupFixtures.access,
        JobDurableLifetimeRecoveryFixtures.replay)
    }
    var before = Vector.empty[Option[Record]]
    fixture.source.before = () => before = _rows(fixture, ids)
    val idsinitial = fixture.original.ids.calls.get()
    val clocksinitial = fixture.original.clock.calls.get()
    val started = fixture.start()
    started.isSuccess shouldBe false
    val conclusion = JobDurableRuntimeFixtures.failureConclusion(started)
    conclusion.observation.getEffectiveMessage shouldBe Some("durable runtime startup refused")
    val health = fixture.engine.durableOperationalHealth.get
    health.state shouldBe JobDurableOperationalState.Degraded
    health.state should not be JobDurableOperationalState.Ready
    health.executionAvailable shouldBe false
    fixture.engine._durable_runtime_ready shouldBe false
    fixture.engine._durable_runtime_owner_option shouldBe None
    fixture.source.calls.get() shouldBe 1
    before.size shouldBe ids.size + 3
    val terminal = fixture.engine.durableTerminalProjectionReport.get
    val runtime = fixture.engine.durableRuntimeRehydrationReport.get
    val ordinals = ids.indices.map(_.toLong).toVector
    terminal.candidates.map(_.jobId) shouldBe ids.map(_.value)
    terminal.candidates.map(_.ordinal) shouldBe ordinals
    runtime.candidates.map(_.jobId) shouldBe ids.map(_.value)
    runtime.candidates.map(_.ordinal) shouldBe ordinals
    val badfact = if (fault == Fault.Missing) DurableJobTerminalProjectionFact.Missing else DurableJobTerminalProjectionFact.Corrupt
    terminal.candidates.map(_.fact) shouldBe ids.indices.map(index =>
      if (index == badindex) badfact else DurableJobTerminalProjectionFact.Registered).toVector
    runtime.candidates.map(_.fact) shouldBe Vector.fill(ids.size)(DurableJobRuntimeRehydrationFact.Refused)
    fixture.engine.durableTerminalProjections.size shouldBe ids.size - 1
    val jobfacts = ids.zipWithIndex.flatMap { case (id, index) =>
      fixture.engine.getResult(id) shouldBe None
      val nativefacts = if (index == badindex) {
        fixture.engine.query(id) shouldBe None
        fixture.engine.getStatus(id) shouldBe None
        fixture.engine.durableTerminalProjection(id) shouldBe None
        _bad_native(fixture, id, fault)
        Map("revision" -> "NotAdmitted", "providerrevision" -> "NotAdmitted", "status" -> "NotAdmitted", "result" -> "NotAdmitted")
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
        _terminal(fixture, id)
      }
      (nativefacts ++ _payment(fixture, id, index == badindex) ++ Map("id" -> id.value, "ordinal" -> index.toString,
        "terminalfact" -> (if (index == badindex) badfact.toString else "Registered"), "runtimefact" -> "Refused"))
        .map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
    _rows(fixture, ids) shouldBe before
    fixture.original.tasks.isEmpty shouldBe true
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    val drained = fixture.use(fixture.engine.drainAll())
    drained shouldBe 0
    val effects = fixture.effects
    Vector(effects.ports, effects.creates, effects.writes, effects.deletes, effects.lifewrites,
      effects.queue, effects.getters, effects.bodies, effects.timers).foreach(value => value shouldBe 0)
    effects.ids shouldBe idsinitial
    effects.clocks shouldBe clocksinitial
    _bad_native(fixture, ids(badindex), fault)
    ids.zipWithIndex.filterNot(_._2 == badindex).foreach { case (id, _) => _terminal(fixture, id); () }
    _rows(fixture, ids) shouldBe before
    _facts(fixture, ids, "startup-read", false, "durable runtime startup refused", drained, 0, 0,
      effects.ids - idsinitial, effects.clocks - clocksinitial) ++ jobfacts
  }

  private def _bad_native(fixture: DurableJobBackupFixtures.Fixture, id: JobId, fault: Fault): Unit = {
    val loaded = fixture.use(fixture.store.loadStartupRecovery(id.value, DurableJobBackupFixtures.access)(using fixture.context))
    loaded shouldBe (if (fault == Fault.Missing) DurableJobStoreStartupRecoveryLoad.Missing else DurableJobStoreStartupRecoveryLoad.Corrupt)
    if (fault == Fault.Missing) fixture.row(fixture.jobEntityId(id.value)) shouldBe None
    else fixture.row(fixture.jobEntityId(id.value)).nonEmpty shouldBe true
  }

  private def _rows(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId]): Vector[Option[Record]] = {
    (ids.map(id => fixture.jobEntityId(id.value)) ++ Vector(DurableJobBackupFixtures.storageId,
      DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)).map(fixture.row)
  }

  private def _terminal(fixture: DurableJobBackupFixtures.Fixture, id: JobId): Map[String, String] = {
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
    native.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    Map("revision" -> body.identity.revision.toString, "providerrevision" -> snapshot.providerrevision.toString,
      "status" -> body.lifecycle.status.wire, "result" -> "Succeeded(Absent)")
  }

  private def _payment(fixture: DurableJobBackupFixtures.Fixture, id: JobId, bad: Boolean = false): Map[String, String] = {
    // Privileged stored payment observation grants no authority for a damaged/missing Job.
    val lifetime = fixture.row(DurableJobBackupFixtures.lifetimeId).get
    val entries = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaCodec.decode(
      lifetime.getString("canonicalJson").get)).entries.filter(_.jobid == id.value)
    entries.size shouldBe 1
    entries.head.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
    val native = entries.head.snapshot
    val result = fixture.use(fixture.lifetime.snapshot(
      id.value, DurableJobBackupFixtures.access)(using fixture.context))
    if (bad) {
      result.isSuccess shouldBe false
      JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage shouldBe
        Some("Durable job lifetime quota unavailable")
    } else {
      JobDurableRuntimeFixtures.success(result) shouldBe native
    }
    native.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    native.seed.remaining shouldBe 0L
    native.seed.normalids.size shouldBe 1
    native.seed.claims shouldBe Set.empty
    native.cancelclaimed shouldBe false
    Map("chargedtasks" -> native.seed.paid.chargedtasks.toString, "historycredits" -> native.seed.paid.historycredits.toString,
      "admissions" -> native.seed.paid.admissions.toString, "cancelcredits" -> native.seed.paid.cancelcredits.toString,
      "remaining" -> native.seed.remaining.toString, "normalid" -> native.seed.normalids.head,
      "claims" -> native.seed.claims.size.toString, "cancelclaimed" -> native.cancelclaimed.toString,
      "quotaaccess" -> (if (bad) "RefusedNativeObservation" else "Authenticated"))
  }

  private def _facts(fixture: DurableJobBackupFixtures.Fixture, ids: Vector[JobId], observation: String,
    ready: Boolean, refusal: String, drained: Int, bodies: Int, getters: Int, iddelta: Int, clockdelta: Int): Map[String, String] = {
    val lifetime = fixture.row(DurableJobBackupFixtures.lifetimeId).get
    val paidroots = JobDurableRuntimeFixtures.success(DurableJobLifetimeQuotaCodec.decode(
      lifetime.getString("canonicalJson").get)).entries.count(_.rootstate == DurableJobLifetimeQuotaRootState.Published)
    paidroots shouldBe ids.size
    val effects = fixture.effects
    Map("count" -> ids.size.toString, "terminalobservation" -> observation, "ready" -> ready.toString,
      "refusal" -> refusal, "candidates" -> fixture.source.entries.size.toString,
      "registered" -> fixture.engine.durableTerminalProjectionReport.toVector.flatMap(_.candidates)
        .count(_.fact == DurableJobTerminalProjectionFact.Registered).toString,
      "runtimejobs" -> (fixture.engine.runtimeState.durableJobs.size() + fixture.engine.runtimeState.runtimeJobs.size()).toString,
      "terminaljobs" -> fixture.engine.durableTerminalProjections.size.toString, "ports" -> effects.ports.toString,
      "bodies" -> bodies.toString, "getters" -> getters.toString, "queue" -> effects.queue.toString,
      "drained" -> drained.toString, "creates" -> effects.creates.toString, "writes" -> effects.writes.toString,
      "deletes" -> effects.deletes.toString, "lifewrites" -> effects.lifewrites.toString,
      "ids" -> iddelta.toString, "clocks" -> clockdelta.toString, "timers" -> effects.timers.toString,
      "rowsunchanged" -> (observation == "startup-read").toString, "paidroots" -> paidroots.toString)
  }
}
