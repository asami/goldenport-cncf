package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.composite.{CompositeQueryV2AggregateStatus, CompositeQueryV2Fixture, CompositeQueryV2Outcome}
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import CompositeQueryNativeCrossProcessFixtures.{Fixture, Persistence, branchId, descriptor, privateValue, recipe, success, validateSelection}

/*
 * Actual public native facade completion and independently fresh no-replay reads.
 * Query bodies and complete native records remain local to this process.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object CompositeQueryNativeCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path,
    persistence: Persistence, alpha: String, count: Int, ids: Vector[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root, manual = arguments.mode != "writer")
    var facts = Map.empty[String, String]
    var primary = Option.empty[Throwable]
    try {
      facts = if (arguments.mode == "writer") _writer(fixture, arguments) else _reader(fixture, arguments)
    } catch {
      case exception: Throwable => primary = Some(exception); throw exception
    } finally {
      fixture.close(primary)
    }
    val identity = CompositeQueryNativeCrossProcessFixtures.identity()
    CompositeQueryNativeCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ fixture.closureFacts ++ Map(
      "mode" -> arguments.mode, "persistence" -> arguments.persistence.wire, "alpha" -> arguments.alpha,
      "count" -> arguments.count.toString, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 6 && args.length <= 8, "closed C8 mode/root/newreceipt/persistence/alpha/count/opaque IDs required")
    val mode = args(0)
    require(Set("writer", "reader", "foreign-reader").contains(mode), "closed C8 process mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "composite-query-native-cross-process",
      "absolute owned C8 native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt &&
      receipt.getParent == root.getParent.resolve("control") && receipt.getFileName.toString == s"$mode.receipt" &&
      !Files.exists(receipt), "new owned C8 control receipt required")
    val persistence = Persistence.parse(args(3))
    val alpha = args(4)
    val count = args(5).toInt
    validateSelection(alpha, count)
    val ids = args.drop(6).toVector.map(value => success(JobId.parse(value)))
    require(ids.distinct.size == ids.size, "distinct opaque C8 candidates required")
    val expected = if (mode == "writer" || persistence == Persistence.Ephemeral) 0 else count
    require(ids.size == expected, "only persistent readers receive actual opaque diagnostic Job IDs")
    require(mode != "foreign-reader" || persistence == Persistence.PersistentDiagnostics,
      "foreign reader requires genuine completed native diagnostics")
    Arguments(mode, root, receipt, persistence, alpha, count, ids)
  }

  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    fixture.start()
    fixture.beforeRows shouldBe Vector.fill(3)(None)
    val caller = fixture.caller
    val callerstate = ExecutionContext.currentExecutionResponseState(caller)
    val response = fixture.invoke(arguments.alpha, arguments.count, arguments.persistence, caller)
    response.status shouldBe CompositeQueryV2AggregateStatus.Succeeded
    response.requiredSucceeded shouldBe true
    val branchids = Vector.tabulate(arguments.count)(index => branchId(arguments.alpha, index))
    response.results.map(_.branchId) shouldBe branchids
    response.results.zipWithIndex.foreach { case (result, index) =>
      result.required shouldBe true
      result.isUsable shouldBe true
      result.outcome shouldBe CompositeQueryV2Outcome.Succeeded
      result.failure shouldBe None
      result.recoveredFailure shouldBe None
      result.response.get match {
        case scalar: OperationResponse.Scalar[?] =>
          scalar.value shouldBe privateValue(arguments.alpha, index)
          scalar.value.getClass shouldBe classOf[String]
        case _ => fail("the real target Query must return the independently selected scalar")
      }
    }
    response.diagnostics.map(_.branchId) shouldBe branchids
    response.diagnostics.map(_.targetId) shouldBe Vector.fill(arguments.count)("target-c8")
    response.diagnostics.foreach { diagnostic =>
      diagnostic.outcome shouldBe CompositeQueryV2Outcome.Succeeded
      diagnostic.failureCode shouldBe None
      diagnostic.cancelled shouldBe false
      diagnostic.fallback shouldBe false
      diagnostic.elapsedMillis should be >= 0L
      diagnostic.queuedMillis should be >= 0L
      diagnostic.jobId.nonEmpty shouldBe true
    }
    val ids = response.diagnostics.map(diagnostic => success(JobId.parse(diagnostic.jobId.get)))
    ids.distinct.size shouldBe arguments.count
    // The aggregate callback precedes the native terminal checkpoint. Await the
    // real result and then observe settlement only after its original monitor releases.
    ids.foreach { id =>
      success(fixture.native.engine.awaitResult(id, 5000L)) shouldBe JobResult.Success(OperationResponse.void)
      fixture.native.engine._state_monitor.synchronized {
        fixture.native.engine.getResult(id) shouldBe Some(JobResult.Success(OperationResponse.void))
        fixture.native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
        fixture.native.engine._durable_lifecycle_write_failures.containsKey(id) shouldBe false
        val tasks = fixture.native.engine.queryTasks(id, 0, 10).get.tasks
        tasks should have size 1
        tasks.head.status shouldBe JobTaskStatus.Succeeded
        tasks.head.result.success shouldBe true
        if (arguments.persistence == Persistence.PersistentDiagnostics) {
          tasks.head.taskKind shouldBe "operation"
          tasks.head.targetKind shouldBe Some("operation")
          tasks.head.component shouldBe Some("composite-query-v2")
          tasks.head.service shouldBe None
          tasks.head.operation shouldBe Some("composite-query-v2")
          tasks.head.relation shouldBe Some("root")
          tasks.head.transactionRole shouldBe Some("own")
          tasks.head.transactionScope shouldBe Some("per-task")
          tasks.head.transactionOutcome shouldBe Some("committed")
        } else {
          tasks.head.taskKind shouldBe "composite-query-v2"
          tasks.head.targetKind shouldBe Some("query")
          tasks.head.component shouldBe None
          tasks.head.service shouldBe None
          tasks.head.operation shouldBe Some("composite-query-v2")
          tasks.head.relation shouldBe None
          tasks.head.transactionRole shouldBe None
          tasks.head.transactionScope shouldBe None
        }
      }
    }
    _context(fixture, caller, arguments)
    ExecutionContext.currentExecutionResponseState(caller) shouldBe callerstate
    fixture.actorAlive shouldBe false
    fixture.assertUnusedTarget()
    fixture.native.tasks.isEmpty shouldBe true
    fixture.native.engine._work_queue.isEmpty shouldBe true
    val persistent = arguments.persistence == Persistence.PersistentDiagnostics
    if (persistent) _boundaries(fixture, ids) else _empty(fixture, ids)
    val snapshots = if (persistent) ids.map(id => _native(fixture, id, arguments)) else Vector.empty
    val rows = fixture.rows(ids)
    ids.foreach { id =>
      val live = fixture.native.engine.query(id).get
      live.result shouldBe Some(OperationResponse.void)
      live.input shouldBe None
      live.calltree shouldBe None
      live.debug.parameters shouldBe Map.empty
      live.debug.requestSummary shouldBe Some("composite-query-v2")
      live.submitter shouldBe JobSubmitter("subject-a", "User", Some("query-session-c8"))
      success(fixture.native.engine.queryManagementResult(id)(using caller)) shouldBe
        Some(JobManagementResult.Available(JobResult.Success(OperationResponse.void)))
    }
    if (persistent) {
      ids.map(id => _native(fixture, id, arguments)) shouldBe snapshots
      fixture.rows(ids) shouldBe rows
    }
    _no_payload_effects(fixture)
    _jobs(fixture, ids, persistent, true, "Available", arguments) ++
      _facts(fixture, ids, persistent, false, false, false, 0, arguments)
  }

  private def _context(fixture: Fixture, caller: ExecutionContext, arguments: Arguments): Unit = {
    val state = fixture.targetFixture.state
    state.queryCalls.size() shouldBe arguments.count
    state.commandCalls.size() shouldBe 0
    state.enteredCount.get() shouldBe arguments.count
    state.exitedCount.get() shouldBe arguments.count
    state.active.get() shouldBe 0
    state.maxActive.get() should (be >= 1 and be <= arguments.count)
    val observations = state.observations.toArray(new Array[CompositeQueryV2Fixture.Observation](0)).toVector
    observations.map(_.branchId).toSet shouldBe Vector.tabulate(arguments.count)(index => branchId(arguments.alpha, index)).toSet
    val template = fixture.installedContext.asInstanceOf[ExecutionContext.Instance]
    (template.runtime.parent.get eq fixture.installedScope) shouldBe true
    observations.foreach { observation =>
      observation.operation shouldBe "payload"
      observation.configurationMarker shouldBe "cq2-diagnostics"
      observation.principal shouldBe caller.security.principal.id.value
      observation.session shouldBe "query-session-c8"
      observation.token shouldBe "query-token-c8"
      observation.tenant shouldBe "tenant-a"
      observation.trace shouldBe caller.observability.traceId.print
      observation.correlation shouldBe caller.observability.correlationId.map(_.print).get
      val runtime = observation.runtime.asInstanceOf[RuntimeContext]
      (runtime.parent.get eq fixture.installedScope) shouldBe true
      runtime.core.kind shouldBe template.runtime.core.kind
      runtime.core.name shouldBe template.runtime.core.name
      (observation.runtime eq caller.runtime) shouldBe false
      (observation.unitOfWork eq caller.unitOfWork) shouldBe false
      (observation.resources eq caller.asInstanceOf[ExecutionContext.Instance].cncfCore.resources) shouldBe false
      (observation.resourceTrees eq caller.asInstanceOf[ExecutionContext.Instance].cncfCore.resourceTrees) shouldBe false
      (observation.resolvedParameters eq caller.resolvedParameters.asInstanceOf[AnyRef]) shouldBe false
      (observation.responseCell eq caller.asInstanceOf[ExecutionContext.Instance].executionResponseCell) shouldBe false
      observation.operationEvaluation should not be caller.operationEvaluation
      observation.responseState.response.map(_.admittedMode) shouldBe Some(s"fixture-${observation.branchId}")
    }
    if (arguments.count == 2) {
      (observations(0).runtime eq observations(1).runtime) shouldBe false
      (observations(0).unitOfWork eq observations(1).unitOfWork) shouldBe false
      (observations(0).responseCell eq observations(1).responseCell) shouldBe false
    }
  }

  private def _boundaries(fixture: Fixture, ids: Vector[JobId]): Unit = {
    val requests = fixture.source.requests
    val pairs = fixture.source.pairs
    requests.size shouldBe ids.size * 6
    requests.map(_.jobid).toSet shouldBe ids.toSet
    pairs.size shouldBe requests.size
    ids.foreach { id =>
      val selected = requests.filter(_.jobid == id)
      selected.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
      selected.map(_.expectedrevision) shouldBe (1L to 6L).toVector
    }
    pairs.groupBy(_.projection.semanticRevision).view.mapValues(_.size).toMap shouldBe
      (1L to 6L).map(revision => revision -> ids.size).toMap
    pairs.foreach { pair =>
      pair.access shouldBe recipe().access
      val projection = pair.projection
      projection.authorization shouldBe recipe().projection.authorization
      projection.definitionSnapshot shouldBe recipe().projection.definitionSnapshot
      projection.inputs shouldBe Vector.empty
      projection.calltreeReference shouldBe None
      projection.diagnostics shouldBe Vector.empty
      projection.tasks should have size 1
      val outcome = if (projection.semanticRevision >= 5L) DurableTransactionOutcome.Committed else DurableTransactionOutcome.Pending
      projection.tasks.head shouldBe descriptor(projection.tasks.head.taskId, outcome)
      projection.result shouldBe (if (projection.semanticRevision == 6L)
        DurableResultOutcome.Succeeded(DurableValue.Absent) else DurableResultOutcome.Pending)
    }
  }

  private def _native(fixture: Fixture, id: JobId, arguments: Arguments): DurableJobStoreSnapshot = {
    val snapshot = fixture.native.signed(id)
    val body = snapshot.record.body
    body.identity.jobId shouldBe id.value
    body.identity.revision shouldBe 6L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.authorization shouldBe recipe().projection.authorization
    body.definitionSnapshot shouldBe recipe().projection.definitionSnapshot
    body.inputs shouldBe Vector.empty
    body.calltreeReference shouldBe None
    body.diagnostics shouldBe Vector.empty
    body.tasks should have size 1
    body.tasks.head shouldBe descriptor(body.tasks.head.taskId, DurableTransactionOutcome.Committed)
    val payment = fixture.native.native(id)
    payment.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    payment.seed.remaining shouldBe 0L
    payment.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    payment.seed.normalids.size shouldBe 1
    payment.seed.claims shouldBe Set.empty
    payment.cancelclaimed shouldBe false
    val text = body.toString
    Vector.tabulate(arguments.count)(index => privateValue(arguments.alpha, index)).foreach(value => text.contains(value) shouldBe false)
    Vector.tabulate(arguments.count)(index => branchId(arguments.alpha, index)).foreach(value => text.contains(value) shouldBe false)
    Vector("password", "query-token-c8", "target-c8", "fixture.branch",
      s"${fixture.targetFixture.componentName}.sample.payload").foreach(value => text.contains(value) shouldBe false)
    snapshot
  }

  private def _reader(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    val ids = arguments.ids
    fixture.targetFixture.state.queryCalls.size() shouldBe 0
    fixture.targetFixture.state.commandCalls.size() shouldBe 0
    fixture.targetFixture.state.observations.size() shouldBe 0
    fixture.start(ids)
    fixture.rows(ids) shouldBe fixture.beforeRows
    val persistent = arguments.persistence == Persistence.PersistentDiagnostics
    val engine = fixture.native.engine
    engine.durableTerminalProjectionReport.get.candidates.map(_.jobId) shouldBe ids.map(_.value)
    engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe
      Vector.fill(ids.size)(DurableJobTerminalProjectionFact.Registered)
    engine.durableRuntimeRehydrationReport.get.candidates.map(_.fact) shouldBe
      Vector.fill(ids.size)(DurableJobRuntimeRehydrationFact.Refused)
    val snapshots = ids.map(id => _native(fixture, id, arguments))
    val payments = ids.map(fixture.native.native)
    val rows = fixture.rows(ids)
    ids.zip(snapshots).foreach { case (id, snapshot) =>
      engine.durableTerminalProjection(id).get.snapshot shouldBe snapshot
      val raw = engine.query(id).get
      raw.status shouldBe JobStatus.Succeeded
      raw.origin shouldBe JobDataOrigin.Durable
      raw.persistence shouldBe JobPersistencePolicy.Persistent
      raw.submitter shouldBe JobSubmitter("subject-a", "User", None)
      raw.result shouldBe None
      raw.input shouldBe None
      raw.calltree shouldBe None
      raw.debug.parameters shouldBe Map.empty
      engine.getResult(id) shouldBe None
    }
    if (!persistent) _empty(fixture, Vector.empty)
    val foreign = arguments.mode == "foreign-reader"
    val caller = if (foreign) DurableJobOperationResponseQueryFixtures.caller(
      fixture.native.context, subject = "foreign-subject", overview = false) else fixture.caller
    val baseline = fixture.effects
    val runtime = _runtime(fixture, ids)
    ids.foreach { id =>
      val raw = engine.query(id).get
      engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
      engine.getResult(id) shouldBe None
      val visible = engine.queryVisible(id)(using caller)
      val management = engine.queryManagementResult(id)(using caller)
      if (foreign) {
        val refusal = JobQueryPolicy.default.authorizeRead(raw)(using caller)
        refusal.isSuccess shouldBe false
        visible.isSuccess shouldBe false
        success(management) shouldBe None
        val actualrefusal = JobDurableRuntimeFixtures.failureConclusion(visible)
        val expectedrefusal = JobDurableRuntimeFixtures.failureConclusion(refusal)
        // Separate policy calls generate independent observation timestamps.
        actualrefusal.copy(observation = actualrefusal.observation.copy(timestamp = expectedrefusal.observation.timestamp)) shouldBe expectedrefusal
        val message = JobDurableRuntimeFixtures.failureConclusion(visible).observation.getEffectiveMessage.get
        Vector("query-private-c8", "query-branch-c8", "query-token-c8", "cq2-diagnostics").foreach(value =>
          message.contains(value) shouldBe false)
      } else {
        success(visible) shouldBe Some(raw)
        success(visible).get.result shouldBe None
        success(management) shouldBe Some(JobManagementResult.UnavailableAfterRestart(raw.resultSummary))
      }
      engine.query(id) shouldBe Some(raw)
    }
    fixture.effects shouldBe baseline
    _runtime(fixture, ids) shouldBe runtime
    val drained = engine.drainAll()
    drained shouldBe 0
    fixture.effects shouldBe baseline
    _runtime(fixture, ids) shouldBe runtime
    // Full local native audits are observations after the service no-effect
    // barrier. Their real Entity loads remain counted, never reset or suppressed.
    ids.map(id => _native(fixture, id, arguments)) shouldBe snapshots
    ids.map(fixture.native.native) shouldBe payments
    fixture.rows(ids) shouldBe rows
    fixture.rows(ids) shouldBe fixture.beforeRows
    _no_payload_effects(fixture)
    val observed = fixture.effects
    Vector(observed.sqlwrites, observed.entitywrites, observed.sources, observed.pairs, observed.ports,
      observed.resolvers, observed.queries, observed.commands, observed.observations).foreach(value => value shouldBe 0)
    observed.entityloads should be >= baseline.entityloads
    fixture.assertUnusedTarget()
    _jobs(fixture, ids, persistent, false, if (foreign) "Refused" else "UnavailableAfterRestart", arguments) ++
      _facts(fixture, ids, persistent, true, !foreign && persistent, foreign, drained, arguments)
  }

  private def _runtime(fixture: Fixture, ids: Vector[JobId]): Map[String, Any] = {
    val engine = fixture.native.engine
    val state = engine.runtimeState
    val scheduling = fixture.native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get
    val scheduler = fixture.owner._scheduler.status
    state.durableJobs.isEmpty shouldBe true
    state.runtimeJobs.isEmpty shouldBe true
    fixture.native.tasks.isEmpty shouldBe true
    engine._work_queue.isEmpty shouldBe true
    state._native_lifetime_bindings.keySet.toSet shouldBe ids.toSet
    ids.foreach(id => state._lifetime_budget.snapshot(id) shouldBe Some(JobLifetimeSnapshot(2L, 96L, 1L, 1L)))
    engine.resourceSnapshot.retained shouldBe ids.size
    engine.resourceSnapshot.pending shouldBe 0
    engine.resourceSnapshot.executing shouldBe 0
    scheduler.started shouldBe true
    scheduler.closed shouldBe false
    scheduler.nextDueAt shouldBe Some(engine.timeSource.now().plusMillis(1000L))
    scheduler.lastResult shouldBe None
    scheduling.pendingTimerCount shouldBe 1
    fixture.native.retention.calls.get() shouldBe 0
    Map("resources" -> engine.resourceSnapshot, "bindings" -> state._native_lifetime_bindings.toMap,
      "paid" -> ids.map(state._lifetime_budget.snapshot), "durable" -> state.durableJobs.size(),
      "runtime" -> state.runtimeJobs.size(), "tasks" -> fixture.native.tasks.toMap,
      "queue" -> engine._work_queue.size(), "sequence" -> engine._work_sequence.get(),
      "scheduler" -> scheduler, "timers" -> scheduling.pendingTimerCount, "time" -> scheduling.now,
      "enginetime" -> engine.timeSource.now(), "clocktime" -> fixture.native.clock.peek,
      "clocks" -> fixture.native.clock.calls.get(), "ids" -> fixture.native.ids.calls.get(),
      "candidates" -> fixture.native.startup.calls.get(), "retention" -> fixture.native.retention.calls.get(),
      "health" -> fixture.owner.health, "targetqueries" -> fixture.targetFixture.state.queryCalls.size(),
      "targetcommands" -> fixture.targetFixture.state.commandCalls.size(),
      "targetobservations" -> fixture.targetFixture.state.observations.size())
  }

  private def _empty(fixture: Fixture, ids: Vector[JobId]): Unit = {
    fixture.rows(ids) shouldBe Vector.fill(ids.size + 3)(None)
    fixture.source.requests shouldBe Vector.empty
    fixture.source.pairs shouldBe Vector.empty
    fixture.native.engine.listJobs(persistentOnly = true) shouldBe Vector.empty
    fixture.native.engine.durableTerminalProjections shouldBe Vector.empty
    fixture.native.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.native.entity.writes.get() shouldBe 0
    fixture.sql.writes.get() shouldBe 0
  }

  private def _no_payload_effects(fixture: Fixture): Unit = {
    val effects = fixture.effects
    Vector(effects.gets, effects.opens, effects.closes, effects.puts, effects.deletes, effects.publications,
      effects.ports, effects.resolvers, effects.metadata).foreach(value => value shouldBe 0)
    fixture.row(DurableJobBackupFixtures.blobQuotaId) shouldBe None
    val blobroot = fixture.root.resolve("blobs")
    val count = if (Files.notExists(blobroot)) 0L else {
      val files = Files.walk(blobroot)
      try files.filter(path => Files.isRegularFile(path)).count() finally files.close()
    }
    count shouldBe 0L
  }

  private def _facts(fixture: Fixture, ids: Vector[JobId], persistent: Boolean, reader: Boolean,
    unavailable: Boolean, foreign: Boolean, drained: Int, arguments: Arguments): Map[String, String] = {
    val effects = fixture.effects
    val lifetime = fixture.row(DurableJobBackupFixtures.lifetimeId)
    val storage = fixture.row(DurableJobBackupFixtures.storageId)
    val paidroots = lifetime.map(row => success(DurableJobLifetimeQuotaCodec.decode(
      row.getString("canonicalJson").get)).entries.count(_.rootstate == DurableJobLifetimeQuotaRootState.Published)).getOrElse(0)
    paidroots shouldBe (if (persistent) arguments.count else 0)
    lifetime.nonEmpty shouldBe persistent
    storage.nonEmpty shouldBe persistent
    Map("idcount" -> ids.size.toString, "ready" -> (fixture.owner.health.state == JobDurableOperationalState.Ready).toString,
      "domainChecked" -> (!reader).toString, "contextChecked" -> (!reader).toString,
      "descriptorChecked" -> persistent.toString, "privateAbsent" -> "true", "nativeUnchanged" -> "true",
      "zeroEffects" -> reader.toString, "rawabsent" -> reader.toString,
      "managementunavailable" -> unavailable.toString, "foreignrefused" -> foreign.toString,
      "queries" -> effects.queries.toString, "commands" -> effects.commands.toString, "observations" -> effects.observations.toString,
      "sources" -> effects.sources.toString, "pairs" -> effects.pairs.toString, "ports" -> effects.ports.toString,
      "resolvers" -> effects.resolvers.toString, "sqlwrites" -> effects.sqlwrites.toString,
      "entitywrites" -> effects.entitywrites.toString, "entityloads" -> effects.entityloads.toString,
      "metadata" -> effects.metadata.toString, "gets" -> effects.gets.toString, "opens" -> effects.opens.toString,
      "closes" -> effects.closes.toString, "puts" -> effects.puts.toString, "deletes" -> effects.deletes.toString,
      "publications" -> effects.publications.toString, "candidates" -> fixture.native.startup.entries.size.toString,
      "registered" -> fixture.native.engine.durableTerminalProjectionReport.get.candidates.count(
        _.fact == DurableJobTerminalProjectionFact.Registered).toString,
      "runtimejobs" -> (fixture.native.engine.runtimeState.durableJobs.size() + fixture.native.engine.runtimeState.runtimeJobs.size()).toString,
      "terminaljobs" -> fixture.native.engine.durableTerminalProjections.size.toString, "drained" -> drained.toString,
      "queue" -> fixture.native.engine._work_queue.size().toString, "paidroots" -> paidroots.toString,
      "lifetimepresent" -> lifetime.nonEmpty.toString, "storagepresent" -> storage.nonEmpty.toString,
      "blobquotapresent" -> fixture.row(DurableJobBackupFixtures.blobQuotaId).nonEmpty.toString)
  }

  private def _jobs(fixture: Fixture, ids: Vector[JobId], persistent: Boolean, live: Boolean,
    facade: String, arguments: Arguments): Map[String, String] = {
    ids.zipWithIndex.flatMap { case (id, index) =>
      val snapshot = if (persistent) Some(_native(fixture, id, arguments)) else None
      val payment = if (persistent) Some(fixture.native.native(id)) else None
      val paid = payment.map(_.seed.paid).getOrElse(JobLifetimeSnapshot(0L, 0L, 0L, 0L))
      Map("id" -> id.value, "native" -> persistent.toString,
        "revision" -> snapshot.map(_.record.body.identity.revision).getOrElse(0L).toString,
        "providerrevision" -> snapshot.map(_.providerrevision.value).getOrElse(0L).toString,
        "status" -> "succeeded", "result" -> (if (persistent) "Succeeded(Absent)" else "AbsentNative"),
        "liveVoid" -> live.toString, "facade" -> facade,
        "chargedtasks" -> paid.chargedtasks.toString, "historycredits" -> paid.historycredits.toString,
        "admissions" -> paid.admissions.toString, "cancelcredits" -> paid.cancelcredits.toString,
        "remaining" -> payment.map(_.seed.remaining).getOrElse(0L).toString,
        "normalids" -> payment.map(_.seed.normalids.size).getOrElse(0).toString,
        "claims" -> payment.map(_.seed.claims.size).getOrElse(0).toString,
        "cancelclaimed" -> payment.exists(_.cancelclaimed).toString).map { case (key, value) => s"job.$index.$key" -> value }
    }.toMap
  }
}
