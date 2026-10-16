package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import DurableJobMigrationCheckpointFixtures.{Effects, Fixture, NativeState, Root, success}
import JobMigrationCrossProcessFixtures.{CasePlan, Form}

/*
 * Original managed native composition; full values and effects remain reader-local.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object JobMigrationCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path, plan: CasePlan, ids: Vector[JobId])
  private final case class Observation(rows: Vector[Option[Record]], loads: Vector[DurableJobStoreStartupRecoveryLoad],
    roots: Vector[Root], state: NativeState, paid: Vector[DurableJobLifetimeQuotaSnapshot], effects: Effects,
    jobacks: Int, nativeloads: Int)
  private val _malformed_text = "PRIVATE invalid migration tail"

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val capability = if (arguments.mode == "unauthorized-reader") "" else arguments.plan.capability
    val fixture = new Fixture(arguments.root, capability)
    var facts = Map.empty[String, String]
    var owner = Option.empty[DurableJobRuntimeOwner]
    var scheduler = Option.empty[DurableJobMaintenanceScheduler]
    try {
      facts = if (arguments.mode == "writer") _writer(fixture, arguments.plan)
        else _reader(fixture, arguments)
    } finally {
      owner = fixture.engine._durable_runtime_owner_option
      scheduler = owner.map(_._scheduler)
      val subsystemresult = fixture.original.subsystem.shutdownC()
      val noderesult = fixture.node.shutdownC()
      try {
        success(subsystemresult)
        success(noderesult)
        fixture.node.state shouldBe SystemNode.State.Stopped
        fixture.node.activeLeaseCount shouldBe 0
        fixture.sql.get.managedResourceOption.get.isOpen shouldBe false
        if (Set("writer", "migrating-reader", "fresh-reader").contains(arguments.mode)) {
          owner.nonEmpty shouldBe true
          owner.get._backup_closed shouldBe true
          scheduler.get.status.closed shouldBe true
          scheduler.get.status.started shouldBe false
          scheduler.get.status.nextDueAt shouldBe None
        } else {
          owner shouldBe None
          scheduler shouldBe None
        }
        fixture.original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime
          .testControl.get.pendingTimerCount shouldBe 0
      } finally fixture.close()
    }
    val identity = JobCrossProcessFixtures.identity()
    JobMigrationCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ Map(
      "mode" -> arguments.mode, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid, "count" -> arguments.plan.count.toString,
      "form" -> arguments.plan.form.wire, "payload" -> arguments.plan.payload.toString, "capability" -> capability,
      "subsystemclosed" -> "true", "nodeclosed" -> (fixture.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> (!fixture.sql.get.managedResourceOption.get.isOpen).toString,
      "activeleases" -> fixture.node.activeLeaseCount.toString,
      "ownerclosure" -> (if (owner.nonEmpty) "installed-closed" else "not-installed"),
      "maintenanceTimersAfterClose" -> fixture.original.subsystem.globalRuntimeContext.executionProfileRuntime
        .schedulingRuntime.testControl.get.pendingTimerCount.toString))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 7 && args.length <= 10, "mode/root/newreceipt/closed plan/at most three opaque IDs required")
    val mode = args(0)
    require(Set("writer", "migrating-reader", "fresh-reader", "default-reader", "false-reader",
      "unauthorized-reader", "invalid-reader").contains(mode), "closed migration mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root), "absolute native directory required")
    require(root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-migration-cross-process", "owned migration namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && receipt.getFileName.toString == s"$mode.receipt" &&
      !Files.exists(receipt), "new owned migration receipt required")
    val payload = args(5) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("closed payload flag required")
    }
    val plan = CasePlan(args(3).toInt, Form.parse(args(4)), payload, args(6))
    require((mode == "invalid-reader") == (plan.form == Form.InvalidTail && mode != "writer"),
      "invalid-tail mode must match its closed form")
    require(!Set("default-reader", "false-reader", "unauthorized-reader").contains(mode) ||
      plan.form == Form.AllV1 && !plan.payload, "closed refusal preparation required")
    val ids = args.drop(7).toVector.map(value => success(JobId.parse(value)))
    require(ids.distinct.size == ids.size && (if (mode == "writer") ids.isEmpty else ids.size == plan.count),
      "unique opaque Job IDs must match count")
    Arguments(mode, root, receipt, plan, ids)
  }

  private def _writer(fixture: Fixture, plan: CasePlan): Map[String, String] = {
    success(fixture.start())
    fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
    val tasks = Vector.fill(plan.count)(new JobDurableLifetimeRecoveryFixtures.Task)
    val ids = tasks.map(task => success(fixture.owner._execute_c {
      fixture.engine.submit(List(task), fixture.context,
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async))
    }))
    ids.distinct.size shouldBe plan.count
    val drained = fixture.use(fixture.engine.drainAll())
    drained shouldBe plan.count
    tasks.foreach { task =>
      task.runs.get() shouldBe 1
      task.canonical.get() shouldBe 1
    }
    val completed = ids.map { id =>
      fixture.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
      fixture.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
      val requests = fixture.original.evidence.requests.filter(_.jobid == id)
      requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
      requests.map(_.expectedrevision) shouldBe Vector(1L, 2L, 3L, 4L, 5L, 6L)
      val snapshot = fixture.signed(id.value)
      _terminal(snapshot, id)
      snapshot.record.format shouldBe DurableRecordFormat.V2
      snapshot.record.body.identity.revision shouldBe 6L
      _payment(fixture, id, authenticated = true)
      snapshot
    }
    val paidbefore = fixture.row(DurableJobMigrationCheckpointFixtures.lifetimeId)
    val roots = completed.zipWithIndex.map { case (snapshot, index) =>
      val withinput = if (plan.payload) {
        val ref = fixture.use(success(fixture.payloads.putObserved(snapshot,
          DurableJobMigrationCheckpointFixtures.access, None, ContentType.TEXT_PLAIN, Bag.binary(Array[Byte](1, 2, 3)))
          (using fixture.context)))
        val body = snapshot.record.body.copy(inputs = Vector(DurableInputReference("payload-0", DurableValue.External(ref))))
        val attached = fixture.checkpoint(snapshot, body)
        attached.record.body shouldBe body.copy(identity = body.identity.copy(revision = body.identity.revision + 1L))
        attached.providerrevision.value shouldBe snapshot.providerrevision.value + 1L
        Root(attached, Vector(ref))
      } else Root(snapshot, Vector.empty)
      val prepared = if (plan.form == Form.Mixed && index == 1) withinput else fixture.legacy(withinput)
      if (prepared != withinput) {
        prepared.snapshot.record.format shouldBe DurableRecordFormat.V1
        prepared.snapshot.record.body shouldBe withinput.snapshot.record.body.copy(identity =
          withinput.snapshot.record.body.identity.copy(revision = withinput.snapshot.record.body.identity.revision + 1L))
        prepared.snapshot.providerrevision.value shouldBe withinput.snapshot.providerrevision.value + 1L
      } else prepared.snapshot shouldBe withinput.snapshot
      _terminal(prepared.snapshot, ids(index))
      _payment(fixture, ids(index), authenticated = true)
      prepared
    }
    fixture.row(DurableJobMigrationCheckpointFixtures.lifetimeId) shouldBe paidbefore
    _payload_authority(fixture, roots)
    val observed = fixture.state(roots*)
    observed.payloads shouldBe (if (plan.payload) Vector.fill(plan.count)(Vector[Byte](1, 2, 3)) else Vector.empty)
    _storage(fixture, roots.map(_.snapshot))
    val jobfacts = roots.zipWithIndex.flatMap { case (root, index) =>
      (_snapshot_facts(root.snapshot) ++ _payment(fixture, ids(index), authenticated = true) ++ Map(
        "id" -> ids(index).value, "ordinal" -> index.toString, "sourceformat" -> root.snapshot.record.format.version.toString,
        "sourcerevision" -> root.snapshot.record.body.identity.revision.toString,
        "sourceproviderrevision" -> root.snapshot.providerrevision.value.toString,
        "terminalfact" -> "PreparedSucceeded", "runtimefact" -> "NotRecovered",
        "nativefact" -> (if (plan.form == Form.InvalidTail && index == plan.count - 1) "Corrupt" else "Admitted")))
        .map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
    if (plan.form == Form.InvalidTail) {
      val before = _rows(fixture, ids)
      val last = ids.last
      fixture.replaceText(fixture.jobEntityId(last.value), _malformed_text)
      val after = _rows(fixture, ids)
      val changed = after(plan.count - 1).get
      changed.getString("canonicalJson") shouldBe Some(_malformed_text)
      changed.removeKeys(Set("canonicalJson")) shouldBe before(plan.count - 1).get.removeKeys(Set("canonicalJson"))
      before.indices.filterNot(_ == plan.count - 1).foreach(index => after(index) shouldBe before(index))
      fixture.use(fixture.store.loadStartupRecovery(last.value, DurableJobMigrationCheckpointFixtures.access)
        (using fixture.context)) shouldBe DurableJobStoreStartupRecoveryLoad.Corrupt
      _rows(fixture, ids) shouldBe after
    }
    fixture.original.port.calls.get() shouldBe 0
    fixture.engine._work_queue.size() shouldBe 0
    val effects = fixture.effects
    effects.timers shouldBe 1
    _timer(fixture)
    _facts(fixture, ids, effects, effects, plan.v1Count, fixture.entity.jobacks.get(),
      ready = true, refusal = "", drained = drained, bodies = tasks.map(_.runs.get()).sum,
      getters = tasks.map(_.getters.get()).sum, rowsunchanged = false, writer = true) ++ jobfacts
  }

  private def _reader(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    val ids = arguments.ids
    val positive = Set("migrating-reader", "fresh-reader").contains(arguments.mode)
    if (arguments.mode == "unauthorized-reader") {
      fixture.context.security shouldBe fixture.original.context.security.copy(capabilities = Set.empty)
    }
    fixture.migration = arguments.mode match {
      case "default-reader" => None
      case "false-reader" => Some(false)
      case _ => Some(true)
    }
    fixture.source.entries = ids.zipWithIndex.map { case (id, index) =>
      DurableJobMigrationCheckpointFixtures.candidate(id.value, index.toLong)
    }
    var before = Option.empty[Observation]
    fixture.source.before = () => {
      fixture.node.activeLeaseCount shouldBe 1
      val rows = _rows(fixture, ids)
      val loads = ids.map(id => fixture.use(fixture.store.loadStartupRecovery(id.value,
        DurableJobMigrationCheckpointFixtures.access)(using fixture.context)))
      val roots = loads.collect { case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
        Root(snapshot, snapshot.record.body.inputs.collect { case DurableInputReference(_, DurableValue.External(ref)) => ref })
      }
      _payload_authority(fixture, roots)
      val state = fixture.state(roots*)
      val paid = _paid(fixture, ids)
      ids.zip(loads).foreach { case (id, load) => load match {
        case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
          _terminal(snapshot, id)
          _payment(fixture, id, authenticated = true)
        case DurableJobStoreStartupRecoveryLoad.Corrupt =>
          arguments.mode shouldBe "invalid-reader"
          id shouldBe ids.last
        case _ => fail("migration source requires actual admitted or deliberate Corrupt native input")
      }}
      rows(ids.size).nonEmpty shouldBe true
      rows(ids.size + 1).nonEmpty shouldBe true
      rows(ids.size + 2).nonEmpty shouldBe arguments.plan.payload
      val effects = fixture.effects
      effects.timers shouldBe 0
      effects.resources.retained shouldBe 0
      effects.resources.pending shouldBe 0
      effects.resources.executing shouldBe 0
      fixture.engine.runtimeState._native_lifetime_bindings.isEmpty shouldBe true
      ids.foreach(id => fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe None)
      fixture.original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.now shouldBe
        JobDurableRuntimeFixtures.instant
      fixture.engine.timeSource.now() shouldBe JobDurableRuntimeFixtures.instant
      before = Some(Observation(rows, loads, roots, state, paid, effects, fixture.entity.jobacks.get(), fixture.entity.loads.get()))
    }
    val started = fixture.start()
    val observed = before.get
    val v1count = observed.roots.count(_.snapshot.record.format == DurableRecordFormat.V1)
    fixture.source.calls.get() shouldBe 1
    fixture.source.entries.map(_.jobId) shouldBe ids.map(_.value)
    fixture.source.entries.map(_.ordinal) shouldBe ids.indices.map(_.toLong).toVector
    if (arguments.mode == "unauthorized-reader") fixture.entity.loads.get() shouldBe observed.nativeloads
    if (positive) {
      success(started)
      fixture.owner.health.state shouldBe JobDurableOperationalState.Ready
      fixture.engine._durable_runtime_ready shouldBe true
      val terminal = fixture.engine.durableTerminalProjectionReport.get
      val runtime = fixture.engine.durableRuntimeRehydrationReport.get
      terminal.candidates.map(_.jobId) shouldBe ids.map(_.value)
      runtime.candidates.map(_.jobId) shouldBe ids.map(_.value)
      terminal.candidates.map(_.ordinal) shouldBe ids.indices.map(_.toLong).toVector
      runtime.candidates.map(_.ordinal) shouldBe ids.indices.map(_.toLong).toVector
      terminal.candidates.map(_.fact) shouldBe Vector.fill(ids.size)(DurableJobTerminalProjectionFact.Registered)
      runtime.candidates.map(_.fact) shouldBe Vector.fill(ids.size)(DurableJobRuntimeRehydrationFact.Refused)
      observed.roots.size shouldBe ids.size
      if (arguments.mode == "fresh-reader") v1count shouldBe 0
      else v1count shouldBe arguments.plan.v1Count
      val destinations = ids.zip(observed.roots).map { case (id, source) =>
        val destination = fixture.signed(id.value)
        if (source.snapshot.record.format == DurableRecordFormat.V1) {
          destination.record.format shouldBe DurableRecordFormat.V2
          destination.record.body shouldBe source.snapshot.record.body.copy(identity =
            source.snapshot.record.body.identity.copy(revision = source.snapshot.record.body.identity.revision + 1L))
          destination.providerrevision.value shouldBe source.snapshot.providerrevision.value + 1L
        } else {
          destination shouldBe source.snapshot
          fixture.row(fixture.jobEntityId(id.value)) shouldBe observed.rows(ids.indexOf(id))
        }
        _terminal(destination, id)
        fixture.engine.durableTerminalProjection(id).get.snapshot shouldBe destination
        val query = fixture.engine.query(id).get
        query.status shouldBe JobStatus.Succeeded
        query.origin shouldBe JobDataOrigin.Durable
        query.persistence shouldBe JobPersistencePolicy.Persistent
        query.input shouldBe None
        query.calltree shouldBe None
        query.result shouldBe None
        fixture.engine.getResult(id) shouldBe None
        success(fixture.engine.queryManagementResult(id)(using fixture.context)) shouldBe
          Some(JobManagementResult.UnavailableAfterRestart(query.resultSummary))
        _payment(fixture, id, authenticated = true)
        destination
      }
      _storage(fixture, destinations)
      fixture.engine.durableTerminalProjections.map(_.queryReadModel.jobId).toSet shouldBe ids.toSet
      fixture.engine.runtimeState._native_lifetime_bindings.keySet.toSet shouldBe ids.toSet
      ids.zip(observed.paid).foreach { case (id, quota) =>
        fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe Some(quota.seed.paid)
      }
      val state = fixture.state(observed.roots*)
      state.metadata shouldBe observed.state.metadata
      state.payloads shouldBe observed.state.payloads
      state.payloads shouldBe (if (arguments.plan.payload) Vector.fill(ids.size)(Vector[Byte](1, 2, 3)) else Vector.empty)
      fixture.row(DurableJobMigrationCheckpointFixtures.lifetimeId) shouldBe observed.rows(ids.size + 1)
      fixture.row(DurableJobMigrationCheckpointFixtures.blobQuotaId) shouldBe observed.rows(ids.size + 2)
      if (v1count == 0) {
        _rows(fixture, ids) shouldBe observed.rows
        state shouldBe observed.state
      }
      fixture.blob.opens.get() shouldBe (if (arguments.plan.payload) ids.size else 0)
      fixture.blob.closes.get() shouldBe fixture.blob.opens.get()
      fixture.blob.gets.get() shouldBe fixture.blob.opens.get()
      fixture.blob.bytes.get() shouldBe (if (arguments.plan.payload) 3L * ids.size else 0L)
      _timer(fixture)
    } else {
      started.isSuccess shouldBe false
      JobDurableRuntimeFixtures.failureConclusion(started).observation.getEffectiveMessage shouldBe
        Some("durable runtime startup refused")
      fixture.engine._durable_runtime_owner_option shouldBe None
      fixture.engine._durable_runtime_ready shouldBe false
      fixture.engine._workers_started.get() shouldBe false
      fixture.engine._durable_lifecycle_write_bridge.nonEmpty shouldBe
        Set("default-reader", "false-reader").contains(arguments.mode)
      _rows(fixture, ids) shouldBe observed.rows
      observed.loads.zip(ids).foreach { case (load, id) =>
        fixture.use(fixture.store.loadStartupRecovery(id.value, DurableJobMigrationCheckpointFixtures.access)
          (using fixture.context)) shouldBe load
      }
      fixture.engine.durableTerminalProjections shouldBe Vector.empty
      fixture.engine.runtimeState._native_lifetime_bindings.isEmpty shouldBe true
      ids.foreach(id => fixture.engine.runtimeState._lifetime_budget.snapshot(id) shouldBe None)
      fixture.blob.opens.get() shouldBe 0
      fixture.blob.closes.get() shouldBe 0
      fixture.blob.gets.get() shouldBe 0
      fixture.blob.bytes.get() shouldBe 0L
      if (arguments.mode == "invalid-reader") {
        observed.loads.last shouldBe DurableJobStoreStartupRecoveryLoad.Corrupt
        observed.roots.size shouldBe ids.size - 1
      }
      fixture.effects.timers shouldBe 0
    }
    _paid(fixture, ids) shouldBe observed.paid
    fixture.original.tasks.isEmpty shouldBe true
    fixture.original.evidence.requests shouldBe Vector.empty
    fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.engine._work_queue.size() shouldBe 0
    val drained = fixture.use(fixture.engine.drainAll())
    drained shouldBe 0
    val jobfacts = ids.zipWithIndex.flatMap { case (id, index) =>
      val source = observed.loads(index) match {
        case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) => Some(snapshot)
        case _ => None
      }
      val nativefacts = source.map(snapshot => _snapshot_facts(if (positive) fixture.signed(id.value) else snapshot))
        .getOrElse(Map("format" -> "NotAdmitted", "revision" -> "NotAdmitted", "providerrevision" -> "NotAdmitted",
          "status" -> "NotAdmitted", "result" -> "NotAdmitted"))
      val terminalfact = fixture.engine.durableTerminalProjectionReport.map(_.candidates(index).fact.toString).getOrElse("NotRecovered")
      val runtimefact = fixture.engine.durableRuntimeRehydrationReport.map(_.candidates(index).fact.toString).getOrElse("NotRecovered")
      (nativefacts ++ _payment(fixture, id, authenticated = source.nonEmpty) ++ Map(
        "id" -> id.value, "ordinal" -> index.toString, "sourceformat" -> source.map(_.record.format.version.toString).getOrElse("Corrupt"),
        "sourcerevision" -> source.map(_.record.body.identity.revision.toString).getOrElse("NotAdmitted"),
        "sourceproviderrevision" -> source.map(_.providerrevision.value.toString).getOrElse("NotAdmitted"),
        "terminalfact" -> terminalfact, "runtimefact" -> runtimefact, "nativefact" -> (if (source.nonEmpty) "Admitted" else "Corrupt")))
        .map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
    if (!positive || v1count == 0) _rows(fixture, ids) shouldBe observed.rows
    fixture.row(DurableJobMigrationCheckpointFixtures.lifetimeId) shouldBe observed.rows(ids.size + 1)
    fixture.row(DurableJobMigrationCheckpointFixtures.blobQuotaId) shouldBe observed.rows(ids.size + 2)
    val effects = fixture.effects
    val migrated = if (positive) v1count else 0
    _no_business_effects(fixture, effects, observed.effects, migrated, if (positive) ids.size else 0)
    fixture.entity.jobacks.get() shouldBe observed.jobacks + migrated
    _facts(fixture, ids, effects, observed.effects, v1count, fixture.entity.jobacks.get() - observed.jobacks,
      ready = positive, refusal = if (positive) "" else "durable runtime startup refused", drained = drained,
      bodies = 0, getters = 0, rowsunchanged = !positive || v1count == 0, writer = false) ++ jobfacts
  }

  private def _no_business_effects(fixture: Fixture, after: Effects, before: Effects, migrated: Int, retained: Int): Unit = {
    after.jobwrites shouldBe before.jobwrites + migrated
    after.writes shouldBe before.writes + 3 * migrated
    after.clocks shouldBe before.clocks + 3 * migrated
    Vector(after.creates - before.creates, after.deletes - before.deletes, after.lifewrites - before.lifewrites,
      after.blobputs - before.blobputs, after.blobdeletes - before.blobdeletes, after.ports - before.ports,
      after.ids - before.ids, after.queue, after.getters, after.bodies, after.outcomes, after.compensations)
      .foreach(value => value shouldBe 0)
    after.resources shouldBe JobResourceSnapshot(retained, 0, 0, before.resources.policy)
    after.timers shouldBe (if (retained > 0) 1 else 0)
    fixture.original.retention.calls.get() shouldBe 0
    fixture.original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.now shouldBe
      JobDurableRuntimeFixtures.instant
    fixture.engine.timeSource.now() shouldBe JobDurableRuntimeFixtures.instant
  }

  private def _timer(fixture: Fixture): Unit = {
    val status = fixture.owner._scheduler.status
    status.started shouldBe true
    status.closed shouldBe false
    status.inFlight shouldBe false
    status.nextDueAt shouldBe Some(fixture.engine.timeSource.now().plusMillis(1000L))
    status.lastResult shouldBe None
    fixture.original.retention.calls.get() shouldBe 0
    fixture.original.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.now shouldBe
      JobDurableRuntimeFixtures.instant
    fixture.engine.timeSource.now() shouldBe JobDurableRuntimeFixtures.instant
  }

  private def _rows(fixture: Fixture, ids: Vector[JobId]): Vector[Option[Record]] = {
    (ids.map(id => fixture.jobEntityId(id.value)) ++ Vector(DurableJobMigrationCheckpointFixtures.storageId,
      DurableJobMigrationCheckpointFixtures.lifetimeId, DurableJobMigrationCheckpointFixtures.blobQuotaId)).map(fixture.row)
  }
  private def _paid(fixture: Fixture, ids: Vector[JobId]): Vector[DurableJobLifetimeQuotaSnapshot] = {
    val record = success(DurableJobLifetimeQuotaCodec.decode(
      fixture.row(DurableJobMigrationCheckpointFixtures.lifetimeId).get.getString("canonicalJson").get))
    record.policy shouldBe fixture.original.lifetimePolicy
    record.entries.size shouldBe ids.size
    ids.map { id =>
      val entries = record.entries.filter(_.jobid == id.value)
      entries.size shouldBe 1
      entries.head.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
      entries.head.snapshot
    }
  }
  private def _payment(fixture: Fixture, id: JobId, authenticated: Boolean): Map[String, String] = {
    val record = success(DurableJobLifetimeQuotaCodec.decode(
      fixture.row(DurableJobMigrationCheckpointFixtures.lifetimeId).get.getString("canonicalJson").get))
    val entries = record.entries.filter(_.jobid == id.value)
    entries.size shouldBe 1
    entries.head.rootstate shouldBe DurableJobLifetimeQuotaRootState.Published
    val native = entries.head.snapshot
    if (authenticated) {
      success(fixture.use(fixture.lifetime.snapshot(id.value, DurableJobMigrationCheckpointFixtures.access)
        (using fixture.context))) shouldBe native
      native.seed.normalids shouldBe fixture.signed(id.value).record.body.tasks.map(_.taskId).toSet
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
      "quotaaccess" -> (if (authenticated) "Authenticated" else "PrivilegedNativeObservation"))
  }
  private def _payload_authority(fixture: Fixture, roots: Vector[Root]): Unit = {
    val refs = roots.flatMap(_.references)
    if (refs.isEmpty) {
      fixture.row(DurableJobMigrationCheckpointFixtures.blobQuotaId) shouldBe None
    } else {
      val record = success(DurableJobOwnedBlobQuotaCodec.decode(
        fixture.row(DurableJobMigrationCheckpointFixtures.blobQuotaId).get.getString("canonicalJson").get))
      record.policy shouldBe fixture.original.blobPolicy
      record.entries.size shouldBe refs.size
      record.entries.map(_.blobid).toSet shouldBe refs.map(_.reference).toSet
      record.entries.foreach { entry =>
        entry.state shouldBe DurableJobOwnedBlobQuotaState.Published
        entry.bytesize shouldBe 3L
        entry.token.get should fullyMatch regex "[0-9a-f]{32}"
        entry.bindingsha256.nonEmpty shouldBe true
      }
      roots.foreach { root =>
        root.references.size shouldBe 1
        root.snapshot.record.body.inputs shouldBe Vector(DurableInputReference("payload-0", DurableValue.External(root.references.head)))
        val ref = root.references.head
        ref.storageKind shouldBe DurableJobOwnedBlobPayloads.storageKind
        val metadata = fixture.metadata(ref)
        metadata.attributes(DurableJobOwnedBlobPayloads.ownerAttribute) shouldBe
          DurableJobOwnedBlobPayloads.ownerFingerprint(root.snapshot.record)
        val entry = record.entries.find(_.blobid == ref.reference).get
        entry.ownersha256 shouldBe DurableJobOwnedBlobPayloads.ownerFingerprint(root.snapshot.record)
        metadata.attributes(DurableJobOwnedBlobPayloads.bindingAttribute) shouldBe entry.bindingsha256.get
        Files.readAllBytes(fixture.physicalPath(ref)).toVector shouldBe Vector[Byte](1, 2, 3)
      }
    }
  }
  private def _terminal(snapshot: DurableJobStoreSnapshot, id: JobId): Unit = {
    val body = snapshot.record.body
    body.identity.jobId shouldBe id.value
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.definitionSnapshot shouldBe DurableJobKeyedEvidenceFixtures.recipe().projection.definitionSnapshot
    body.tasks.size shouldBe 1
    body.tasks.head.transaction shouldBe DurableTransactionDescriptor(DurableTransactionRole.Own,
      DurableTransactionScope.PerTask, DurableTransactionOutcome.Committed)
  }
  private def _snapshot_facts(snapshot: DurableJobStoreSnapshot): Map[String, String] = {
    Map("format" -> snapshot.record.format.version.toString, "revision" -> snapshot.record.body.identity.revision.toString,
      "providerrevision" -> snapshot.providerrevision.value.toString, "status" -> snapshot.record.body.lifecycle.status.wire,
      "result" -> "Succeeded(Absent)")
  }
  private def _storage(fixture: Fixture, snapshots: Vector[DurableJobStoreSnapshot]): Unit = {
    val record = success(DurableJobStorageQuotaCodec.decode(
      fixture.row(DurableJobMigrationCheckpointFixtures.storageId).get.getString("canonicalJson").get))
    record.policy shouldBe fixture.original.storagePolicy
    record.entries.map(_.jobid).toSet shouldBe snapshots.map(_.record.body.identity.jobId).toSet
    record.entries.size shouldBe snapshots.size
    snapshots.foreach { snapshot =>
      val canonical = success(DurableJobRecordCodec.canonicalJson(snapshot.record))
      val expected = success(DurableJobStorageQuotaInventory.metadata(snapshot.record.body.identity.jobId,
        canonical, snapshot.providerrevision.value))
      record.entries.find(_.jobid == snapshot.record.body.identity.jobId).get shouldBe expected.entry
    }
    success(fixture.use(fixture.store.resourceSnapshot()(using fixture.context))).pendingreservations shouldBe 0
  }
  private def _facts(fixture: Fixture, ids: Vector[JobId], after: Effects, before: Effects, v1count: Int,
    jobacks: Int, ready: Boolean, refusal: String, drained: Int, bodies: Int, getters: Int,
    rowsunchanged: Boolean, writer: Boolean): Map[String, String] = {
    def _count_(value: Int, baseline: Int): String = (if (writer) value else value - baseline).toString
    val paid = _paid(fixture, ids)
    Map("ready" -> ready.toString, "refusal" -> refusal, "sourcecalls" -> fixture.source.calls.get().toString,
      "v1count" -> v1count.toString, "jobwrites" -> _count_(after.jobwrites, before.jobwrites), "jobacks" -> jobacks.toString,
      "nativewrites" -> _count_(after.writes, before.writes), "auditclocks" -> _count_(after.clocks, before.clocks),
      "creates" -> _count_(after.creates, before.creates), "deletes" -> _count_(after.deletes, before.deletes),
      "lifewrites" -> _count_(after.lifewrites, before.lifewrites), "blobputs" -> _count_(after.blobputs, before.blobputs),
      "blobdeletes" -> _count_(after.blobdeletes, before.blobdeletes), "physicalopens" -> fixture.blob.opens.get().toString,
      "physicalcloses" -> fixture.blob.closes.get().toString, "physicalbytes" -> fixture.blob.bytes.get().toString,
      "ids" -> _count_(after.ids, before.ids), "ports" -> _count_(after.ports, before.ports),
      "evidence" -> fixture.original.evidence.requests.size.toString, "bodies" -> bodies.toString, "getters" -> getters.toString,
      "queue" -> after.queue.toString, "drained" -> drained.toString,
      "runtimejobs" -> (fixture.engine.runtimeState.durableJobs.size() + fixture.engine.runtimeState.runtimeJobs.size()).toString,
      "retainedTerminalCount" -> fixture.engine.durableTerminalProjections.size.toString,
      "pending" -> after.resources.pending.toString, "executing" -> after.resources.executing.toString,
      "maintenanceTimersBeforeClose" -> after.timers.toString,
      "bridgebound" -> fixture.engine._durable_lifecycle_write_bridge.nonEmpty.toString,
      "rowsunchanged" -> rowsunchanged.toString, "paidroots" -> paid.size.toString)
  }
}
