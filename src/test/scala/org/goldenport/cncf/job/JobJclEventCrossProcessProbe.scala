package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.{ScopeContext, ScopeKind}
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination
import org.goldenport.observation.Cause
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import JobJclEventCrossProcessFixtures.{Fixture, Scenario, boundaries, checkModel, descriptor, eventName, operation, operationName, parameters, privateValue, recipe, source, success, validateSelection}

/*
 * Local writer oracles and independently selected native terminal reading.
 * Only the opaque Job identity crosses from a persistent writer to its reader.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object JobJclEventCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path, scenario: Scenario,
    alpha: String, count: Int, ids: Vector[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root, arguments.scenario, arguments.alpha, arguments.count, manual = arguments.mode == "reader")
    var facts = Map.empty[String, String]
    var primary = Option.empty[Throwable]
    try {
      facts = if (arguments.mode == "writer") { _writer(fixture, arguments) } else { _reader(fixture, arguments) }
    } catch {
      case exception: Throwable => primary = Some(exception); throw exception
    } finally { fixture.close(primary) }
    val identity = JobJclEventCrossProcessFixtures.identity()
    JobJclEventCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ fixture.closureFacts ++ Map(
      "mode" -> arguments.mode, "scenario" -> arguments.scenario.wire, "alpha" -> arguments.alpha,
      "count" -> arguments.count.toString, "root" -> arguments.root.toString, "sqlite" -> arguments.root.resolve("jobs.db").toString,
      "pid" -> identity.pid.toString, "start" -> identity.start.toString, "uuid" -> identity.uuid))
  }
  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 6 && args.length <= 7, "closed C10 mode/root/receipt/scenario/alpha/N/opaque Job ID required")
    val mode = args(0)
    require(Set("writer", "reader").contains(mode), "closed process mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) && root.getFileName.toString == "native" &&
      root.getParent.getFileName.toString.startsWith("case-") && root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-jcl-event-cross-process", "owned native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt && receipt.getParent == root.getParent.resolve("control") &&
      receipt.getFileName.toString == s"$mode.receipt" && !Files.exists(receipt), "new owned receipt required")
    val scenario = Scenario.parse(args(3))
    val alpha = args(4)
    val count = args(5).toInt
    validateSelection(alpha, count)
    val ids = args.drop(6).toVector.map(value => success(JobId.parse(value)))
    ids.size shouldBe (if (mode == "reader" && scenario.persistentJob) { 1 } else { 0 })
    Arguments(mode, root, receipt, scenario, alpha, count, ids)
  }
  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    val scenario = arguments.scenario
    fixture.start()
    fixture.beforeRows shouldBe Vector.fill(3)(None)
    fixture.eventRecords shouldBe Vector.empty
    val submitted = success(fixture.submit()) match {
      case OperationResponse.RecordResponse(record) => record
      case _ => fail("actual public JobControl RecordResponse required")
    }
    val ids = submitted.getAny("submitted-job-ids").collect { case values: Seq[?] =>
      values.toVector.map(value => success(JobId.parse(value.toString)))
    }.getOrElse(fail("publicly returned actual Job IDs required"))
    ids should have size 1
    val id = ids.head
    withClue((submitted.getString("failure-message").toVector ++
      fixture.native.engine.query(id).toVector.flatMap(_.tasks.tasks)
        .filter(_.status == JobTaskStatus.Failed).flatMap(_.result.message)).mkString("; ").take(2048)) {
      submitted.getAny("success") shouldBe Some(!scenario.failed)
    }
    val expectedresult = if (scenario.failed) { JobResult.Failure(_expected_failure(fixture)) } else {
      JobResult.Success(OperationResponse.Scalar(privateValue(arguments.alpha, scenario.baseCount - 1)))
    }
    val result = success(fixture.native.engine.awaitResult(id, 5000L))
    _result(result, expectedresult)
    fixture.native.engine._state_monitor.synchronized {
      fixture.native.engine.getStatus(id) shouldBe Some(if (scenario.failed) { JobStatus.Failed } else { JobStatus.Succeeded })
      _result(fixture.native.engine.getResult(id).get, expectedresult)
      fixture.native.engine._durable_lifecycle_write_failures.containsKey(id) shouldBe false
      _live(fixture, id, arguments)
      _observations(fixture, id, arguments)
      _events(fixture, id, arguments)
      if (scenario.persistentJob) {
        _boundaries(fixture, id, arguments)
        val terminal = _native(fixture, id, arguments)
        terminal.record.body.tasks.map(_.taskId) shouldBe fixture.native.engine.query(id).get.tasks.tasks.map(_.taskId.value)
        _entity(fixture, id, arguments)
      } else { _empty(fixture, ids) }
    }
    success(fixture.native.engine.queryManagementResult(id)(using fixture.caller)) match {
      case Some(JobManagementResult.Available(actual)) => _result(actual, expectedresult)
      case _ => fail("live original management result required")
    }
    _no_payload(fixture)
    fixture.native.tasks.isEmpty shouldBe true
    fixture.native.port.calls.get() shouldBe 0
    fixture.native.engine._work_queue.isEmpty shouldBe true
    _facts(fixture, arguments, ids)
  }
  private def _expected_failure(fixture: Fixture): Conclusion = fixture.scenario match {
    case Scenario.SourceFailure => fixture.sourceFailure
    case Scenario.HandlerFailure => fixture.handlerFailure
    case Scenario.DispatchFailure => JobDurableRuntimeFixtures.failureConclusion(Consequence.operationInvalid(
      "event.dispatch", Cause.Kind.Inconsistency,
      Seq(Facet.Name(JobJclEventCrossProcessFixtures.observerName), Facet.Key(eventName(fixture.alpha)),
        Facet.State("c10-domain"), Facet.Message("event dispatch failed")), previous = Some(fixture.dispatchFailure)))
    case _ => fail("closed original failure scenario required")
  }
  private def _result(actual: JobResult, expected: JobResult): Unit = (actual, expected) match {
    case (JobResult.Failure(found), JobResult.Failure(selected)) =>
      val observation = found.observation
      val descriptor = observation.cause.descriptor
      val selecteddescriptor = selected.observation.cause.descriptor
      val normalized = if (selected.previous.nonEmpty && selecteddescriptor.facets.contains(Facet.Operation("event.dispatch"))) {
        descriptor.facets.filterNot(_.isInstanceOf[Facet.SrcPos]) shouldBe
          selecteddescriptor.facets.filterNot(_.isInstanceOf[Facet.SrcPos])
        val positions = descriptor.facets.collect { case Facet.SrcPos(pos) => pos }
        positions should have size 1
        val position = positions.head
        Paths.get(position.file).getFileName.toString shouldBe "EventBus.scala"
        position.line should be > 0
        position.column should be > 0
        observation.copy(timestamp = selected.observation.timestamp,
          cause = observation.cause.copy(descriptor = descriptor.copy(facets = selecteddescriptor.facets)))
      } else { observation.copy(timestamp = selected.observation.timestamp) }
      found.copy(observation = normalized) shouldBe selected
    case _ => actual shouldBe expected
  }
  private def _live(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val scenario = arguments.scenario
    val live = fixture.native.engine.query(id).get
    val count = scenario.attemptedCount(arguments.count)
    live.status shouldBe (if (scenario.failed) { JobStatus.Failed } else { JobStatus.Succeeded })
    live.tasks.totalCount shouldBe count
    val ids = live.tasks.tasks.map(_.taskId.value)
    ids should have size count
    ids.distinct shouldBe ids
    live.tasks.tasks.zipWithIndex.foreach { case (task, index) =>
      val failed = scenario.failed && (index == scenario.baseCount - 1 || (scenario == Scenario.HandlerFailure && index == scenario.baseCount))
      checkModel(task, index, scenario, ids, committed = !failed, failed = failed)
    }
    live.debug.parameters.get("cncf.job.entitySync") shouldBe None
    live.debug.executionNotes.exists(_.contains("job-entity-sync-failed")) shouldBe false
    live.debug.parameters("jcl.target.action") shouldBe operation(0, scenario)
    live.debug.parameters("jcl.event.ids") shouldBe "accepted"
    live.debug.parameters("jcl.continuation.ids") shouldBe (1 to arguments.count).map(index => s"handler-$index").mkString(",")
    live.debug.parameters.get("jcl.flow.step.ids") shouldBe (if (scenario.baseCount == 2) { Some("source") } else { None })
  }
  private def _observations(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val scenario = arguments.scenario
    val count = scenario.attemptedCount(arguments.count)
    val observations = fixture.observationVector
    val models = fixture.native.engine.query(id).get.tasks.tasks
    observations.map(_.operation) shouldBe Vector.tabulate(count)(index => operationName(index, scenario))
    observations.map(_.parameters) shouldBe Vector.tabulate(count)(index => parameters(arguments.alpha, index))
    fixture.commits.size() shouldBe scenario.committedCount(arguments.count)
    observations.zipWithIndex.foreach { case (observation, index) =>
      val context = observation.context
      context.security.principal.id.value shouldBe "subject-a"
      context.security.principal.attributes("tenantId") shouldBe "tenant-a"
      context.security.subjectKind.toString shouldBe "User"
      context.scope.core.name shouldBe operation(index, scenario)
      context.jobContext.jobId shouldBe Some(id)
      context.jobContext.taskId shouldBe Some(models(index).taskId)
      context.jobContext.currentTask shouldBe Some(models(index).taskId)
      context.jobContext.actionId.nonEmpty shouldBe true
      val expectedstack = if (index == 0) { Vector(models(0).taskId) } else if (index < scenario.baseCount) {
        Vector(models(0).taskId, models(index).taskId)
      } else { models.take(scenario.baseCount).map(_.taskId) :+ models(index).taskId }
      context.jobContext.taskStack shouldBe expectedstack
      context.jobContext.parentJobId shouldBe (if (index >= scenario.baseCount) { Some(id) } else { None })
      (context.dataStoreSpace eq fixture.native.context.dataStoreSpace) shouldBe true
      (context.entityStoreSpace eq fixture.native.context.entityStoreSpace) shouldBe true
      val failedaction = (scenario == Scenario.SourceFailure && index == 1) || (scenario == Scenario.HandlerFailure && index == scenario.baseCount)
      if (failedaction) {
        observation.uow.lastCommitResult shouldBe None
        observation.uow.lastCommitTermination shouldBe None
        observation.uow.lastAbortResult.exists(_.isSuccess) shouldBe true
        fixture.commits.contains(observation.uow) shouldBe false
      } else {
        observation.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        observation.uow.lastCommitResult.exists(_.isSuccess) shouldBe true
        observation.uow.lastAbortResult shouldBe None
        fixture.commits.contains(observation.uow) shouldBe true
      }
    }
    val actiontrace = observations.map(_.operation)
    val expectedtrace = if (scenario == Scenario.SourceFailure) { actiontrace } else {
      actiontrace.take(scenario.baseCount) ++ Vector("observer") ++ actiontrace.drop(scenario.baseCount)
    }
    fixture.traceVector shouldBe expectedtrace
  }
  private def _scope_name(scope: ScopeContext, kind: ScopeKind): Option[String] = {
    if (scope.core.kind == kind) { Some(scope.core.name) } else { scope.core.parent.flatMap(value => _scope_name(value, kind)) }
  }
  private def _events(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val scenario = arguments.scenario
    fixture.native.subsystem.eventBus.subscriptions shouldBe fixture.subscriptionsRegistered
    fixture.eventVector should have size scenario.observerCount
    val records = fixture.eventRecords
    records should have size scenario.eventCount
    fixture.eventVector.foreach { observation =>
      val event = observation.event
      val context = observation.context
      val model = fixture.native.engine.query(id).get.tasks.tasks(scenario.baseCount - 1)
      val sourceobservation = fixture.observationVector(scenario.baseCount - 1)
      context.jobContext.jobId shouldBe Some(id)
      context.jobContext.taskId shouldBe Some(model.taskId)
      context.jobContext.currentTask shouldBe Some(model.taskId)
      context.jobContext.taskStack shouldBe sourceobservation.context.jobContext.taskStack
      context.security.principal.id.value shouldBe "subject-a"
      context.security.principal.attributes("tenantId") shouldBe "tenant-a"
      context.security.subjectKind.toString shouldBe "User"
      (context.dataStoreSpace eq fixture.native.context.dataStoreSpace) shouldBe true
      (context.entityStoreSpace eq fixture.native.context.entityStoreSpace) shouldBe true
      event.name shouldBe eventName(arguments.alpha)
      event.kind shouldBe "c10-domain"
      event.payload shouldBe Map.empty
      event.occurredAt.isBefore(sourceobservation.began) shouldBe false
      event.occurredAt.isAfter(observation.before) shouldBe false
      observation.after.isBefore(observation.before) shouldBe false
      val correlation = context.observability.correlationId.map(_.print)
      val causation = context.jobContext.causationId.orElse(context.jobContext.actionId.map(_.print)).orElse(correlation)
      correlation.exists(_.nonEmpty) shouldBe true
      causation.exists(_.nonEmpty) shouldBe true
      val attributes = Map("jcl.event.id" -> "accepted", "jcl.event.after" -> (if (scenario.baseCount == 1) { "root" } else { "source" }),
        "cncf.context.jobId" -> id.print, "cncf.context.taskId" -> model.taskId.print) ++
        correlation.map("cncf.context.correlationId" -> _) ++ causation.map("cncf.context.causationId" -> _) ++
        _scope_name(context.scope, ScopeKind.Subsystem).map("cncf.source.subsystem" -> _) ++
        _scope_name(context.scope, ScopeKind.Component).orElse(Some(JobJclEventCrossProcessFixtures.componentName)).map("cncf.source.component" -> _) ++
        _scope_name(context.scope, ScopeKind.Action).orElse(Some(operation(scenario.baseCount - 1, scenario))).map("cncf.source.action" -> _)
      event.attributes shouldBe attributes
      event.attributes.values.exists(_.contains("private-c10")) shouldBe false
      records.foreach { record =>
        record.name shouldBe event.name
        record.kind shouldBe event.kind
        record.payload shouldBe event.payload
        record.attributes shouldBe event.attributes
        record.createdAt shouldBe event.occurredAt
        record.persistent shouldBe true
        record.status shouldBe EventRecord.Status.Stored
        record.sequence should be > 0L
        success(fixture.native.subsystem.eventStore.load(record.id)) shouldBe Some(record)
      }
    }
  }
  private def _boundaries(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val expected = boundaries(arguments.scenario, arguments.count)
    fixture.source.requests.map(_.jobid).distinct shouldBe Vector(id)
    fixture.source.requests.map(_.request.boundary) shouldBe expected.map(_.boundary)
    fixture.source.requests.map(_.expectedrevision) shouldBe (1L to (4L + 2L * (arguments.scenario.baseCount + arguments.count))).toVector
    fixture.source.pairs should have size expected.size
    fixture.source.requests.zip(fixture.source.pairs).zip(expected).foreach { case ((request, pair), selected) =>
      pair.projection.semanticRevision shouldBe request.expectedrevision
      pair.projection.tasks should have size selected.count
      val ids = pair.projection.tasks.map(_.taskId)
      pair.projection.tasks shouldBe ids.zipWithIndex.map { case (taskid, index) => descriptor(taskid, index, arguments.scenario, ids,
        if (selected.committed.contains(index)) { DurableTransactionOutcome.Committed } else { DurableTransactionOutcome.Pending }) }
    }
  }
  private def _native(fixture: Fixture, id: JobId, arguments: Arguments): DurableJobStoreSnapshot = {
    val scenario = arguments.scenario
    val total = scenario.baseCount + arguments.count
    val snapshot = fixture.native.signed(id)
    val body = snapshot.record.body
    body.identity.jobId shouldBe id.value
    body.identity.revision shouldBe 4L + 2L * total
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.authorization shouldBe recipe(scenario).projection.authorization
    body.definitionSnapshot shouldBe recipe(scenario).projection.definitionSnapshot
    body.definitionSnapshot.declaredSource shouldBe None
    body.inputs shouldBe Vector.empty
    body.diagnostics shouldBe Vector.empty
    body.calltreeReference shouldBe None
    body.tasks should have size total
    val ids = body.tasks.map(_.taskId)
    ids.distinct shouldBe ids
    body.tasks shouldBe ids.zipWithIndex.map { case (taskid, index) => descriptor(taskid, index, scenario, ids, DurableTransactionOutcome.Committed) }
    val payment = fixture.native.native(id)
    val notes = if (scenario.baseCount == 1) { 3L } else { 4L }
    payment.seed.paid shouldBe JobLifetimeSnapshot(2L * total, 32L + 64L * total + 32L * arguments.count + notes,
      1L + arguments.count, 1L + arguments.count)
    payment.seed.remaining shouldBe 0L
    payment.seed.normalids shouldBe ids.toSet
    payment.seed.claims shouldBe Set.empty
    payment.cancelclaimed shouldBe false
    Vector.tabulate(total)(index => privateValue(arguments.alpha, index)).foreach(value => body.toString.contains(value) shouldBe false)
    body.toString.contains(source(arguments.alpha, arguments.count, scenario)) shouldBe false
    snapshot
  }
  private def _entity(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val entityid = JobEntity.entityId(id)
    val carrier = success(fixture.owner._execute_c {
      fixture.native.entity.loadDetached[JobEntity](entityid)(using JobEntity.entityPersistent, fixture.native.context)
    }).getOrElse(fail("positive actual engine-created native JobEntity required"))
    carrier.revision.value should be > 0L
    carrier.entity.id shouldBe entityid
    val record = carrier.entity.record
    record.getString("jobId") shouldBe Some(id.value)
    record.getString("status") shouldBe Some("Succeeded")
    record.getString("persistence") shouldBe Some("Persistent")
    val debug = record.getRecord("debugParameters").getOrElse(fail("native inline JCL debug parameters required"))
    debug.getString("jcl.target.action") shouldBe Some(operation(0, arguments.scenario))
    debug.getString("jcl.event.ids") shouldBe Some("accepted")
    debug.getString("jcl.continuation.ids") shouldBe Some((1 to arguments.count).map(index => s"handler-$index").mkString(","))
    debug.getString("marker") shouldBe Some(privateValue(arguments.alpha, 0))
    debug.getString("cncf.job.entitySync") shouldBe None
    record.getString("debugExecutionNotes").exists(_.contains("job-entity-sync-failed")) shouldBe false
    // Inline submission supplies no saved definition/source snapshot.
    debug.getString("jcl.jobDefinition.source") shouldBe None
    record.getRecord("jobDefinitionSnapshot") shouldBe None
  }
  private def _reader(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    fixture.start(arguments.ids)
    fixture.rows(arguments.ids) shouldBe fixture.beforeRows
    val rows = fixture.rows(arguments.ids)
    val payments = arguments.ids.map(fixture.native.native)
    val snapshots = arguments.ids.map(id => _native(fixture, id, arguments))
    val foreign = arguments.scenario == Scenario.ForeignReader
    val caller = if (foreign) { DurableJobOperationResponseQueryFixtures.caller(fixture.native.context,
      subject = "foreign-subject", tenant = "foreign-tenant") } else { fixture.caller }
    fixture.native.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe
      Vector.fill(arguments.ids.size)(DurableJobTerminalProjectionFact.Registered)
    arguments.ids.zip(snapshots).foreach { case (id, snapshot) =>
      fixture.native.engine.durableTerminalProjection(id).get.snapshot shouldBe snapshot
      val raw = fixture.native.engine.query(id).get
      raw.status shouldBe JobStatus.Succeeded
      raw.origin shouldBe JobDataOrigin.Durable
      raw.persistence shouldBe JobPersistencePolicy.Persistent
      raw.result shouldBe None
      raw.input shouldBe None
      raw.calltree shouldBe None
      raw.debug.parameters.values.exists(_.contains("private-c10")) shouldBe false
      raw.debug.jobDefinitionSnapshot.flatMap(_.jclSource) shouldBe None
      fixture.native.engine.getResult(id) shouldBe None
      val visible = fixture.native.engine.queryVisible(id)(using caller)
      val management = fixture.native.engine.queryManagementResult(id)(using caller)
      if (foreign) {
        val refusal = JobQueryPolicy.default.authorizeRead(raw)(using caller)
        refusal.isSuccess shouldBe false
        visible.isSuccess shouldBe false
        success(management) shouldBe None
        val actual = JobDurableRuntimeFixtures.failureConclusion(visible)
        val expected = JobDurableRuntimeFixtures.failureConclusion(refusal)
        actual.copy(observation = actual.observation.copy(timestamp = expected.observation.timestamp)) shouldBe expected
        actual.observation.getEffectiveMessage.get.contains("private-c10") shouldBe false
      } else {
        success(visible).get.result shouldBe None
        success(management) shouldBe Some(JobManagementResult.UnavailableAfterRestart(raw.resultSummary))
      }
      raw.tasks.totalCount shouldBe arguments.scenario.baseCount + arguments.count
      val ids = raw.tasks.tasks.map(_.taskId.value)
      ids shouldBe snapshot.record.body.tasks.map(_.taskId)
      raw.tasks.tasks.zipWithIndex.foreach { case (model, index) => checkModel(model, index, arguments.scenario, ids, committed = true) }
      _entity(fixture, id, arguments)
    }
    fixture.native.tasks.isEmpty shouldBe true
    fixture.native.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.native.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.native.engine._work_queue.isEmpty shouldBe true
    fixture.native.engine.drainAll() shouldBe 0
    fixture.observationVector shouldBe Vector.empty
    fixture.commits.size() shouldBe 0
    fixture.eventVector shouldBe Vector.empty
    fixture.traceVector shouldBe Vector.empty
    fixture.eventRecords shouldBe Vector.empty
    fixture.native.subsystem.eventBus.subscriptions shouldBe fixture.subscriptionsRegistered
    fixture.source.requests shouldBe Vector.empty
    fixture.source.pairs shouldBe Vector.empty
    fixture.native.port.calls.get() shouldBe 0
    fixture.sql.writes.get() shouldBe 0
    fixture.native.entity.writes.get() shouldBe 0
    _no_payload(fixture)
    fixture.rows(arguments.ids) shouldBe rows
    arguments.ids.map(fixture.native.native) shouldBe payments
    arguments.ids.map(id => _native(fixture, id, arguments)) shouldBe snapshots
    fixture.rows(arguments.ids) shouldBe rows
    if (arguments.ids.isEmpty) { _empty(fixture, Vector.empty) }
    _facts(fixture, arguments, arguments.ids)
  }
  private def _no_payload(fixture: Fixture): Unit = {
    fixture.blob.issued shouldBe Vector.empty
    Vector(fixture.blob.gets.get(), fixture.blob.opens.get(), fixture.blob.puts.get(), fixture.blob.deletes.get())
      .foreach(value => value shouldBe 0)
    fixture.row(DurableJobBackupFixtures.blobQuotaId) shouldBe None
  }
  private def _empty(fixture: Fixture, ids: Vector[JobId]): Unit = {
    Vector(DurableJobBackupFixtures.storageId, DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)
      .foreach(id => fixture.row(id) shouldBe None)
    ids.foreach { id =>
      fixture.row(success(DurableJobStoreEntity.entityId(id.value))) shouldBe None
      fixture.row(JobEntity.entityId(id)) shouldBe None
    }
    fixture.source.requests shouldBe Vector.empty
    fixture.source.pairs shouldBe Vector.empty
    fixture.native.engine.durableTerminalProjections shouldBe Vector.empty
    fixture.sql.writes.get() shouldBe 0
    fixture.native.entity.writes.get() shouldBe 0
    _no_payload(fixture)
  }
  private def _facts(fixture: Fixture, arguments: Arguments, ids: Vector[JobId]): Map[String, String] = {
    val persistent = arguments.scenario.persistentJob
    val writer = arguments.mode == "writer"
    val payment = if (persistent) { Some(fixture.native.native(ids.head)) } else { None }
    val snapshot = if (persistent) { Some(fixture.native.signed(ids.head)) } else { None }
    val paidroots = fixture.row(DurableJobBackupFixtures.lifetimeId).map(row => success(
      DurableJobLifetimeQuotaCodec.decode(row.getString("canonicalJson").get)).entries.count(
      _.rootstate == DurableJobLifetimeQuotaRootState.Published)).getOrElse(0)
    paidroots shouldBe (if (persistent) { 1 } else { 0 })
    Map("id" -> ids.headOption.map(_.value).getOrElse(""), "ready" -> (fixture.owner.health.state == JobDurableOperationalState.Ready).toString,
      "domainchecked" -> writer.toString, "descriptorchecked" -> persistent.toString, "sourcechecked" -> persistent.toString,
      "nativeunchanged" -> "true", "privateabsent" -> "true", "foreignrefused" -> (!writer && arguments.scenario == Scenario.ForeignReader).toString,
      "eventchecked" -> "true", "eventcount" -> fixture.eventRecords.size.toString, "observers" -> fixture.eventVector.size.toString,
      "subscriptions" -> (fixture.native.subsystem.eventBus.subscriptions.size - fixture.subscriptionsBefore.size).toString,
      "wrapperfailed" -> (writer && ids.exists(id => fixture.native.engine.getStatus(id).contains(JobStatus.Failed))).toString,
      "failedactions" -> fixture.observationVector.count(_.uow.lastAbortResult.exists(_.isSuccess)).toString,
      "actions" -> fixture.observations.size().toString, "commits" -> fixture.commits.size().toString,
      "sources" -> fixture.source.requests.size.toString, "pairs" -> fixture.source.pairs.size.toString,
      "ports" -> fixture.native.port.calls.get().toString, "sqlwrites" -> fixture.sql.writes.get().toString,
      "entitywrites" -> fixture.native.entity.writes.get().toString, "entityloads" -> fixture.native.entity.loads.get().toString,
      "publications" -> fixture.blob.issued.size.toString, "gets" -> fixture.blob.gets.get().toString, "opens" -> fixture.blob.opens.get().toString,
      "puts" -> fixture.blob.puts.get().toString, "deletes" -> fixture.blob.deletes.get().toString,
      "registered" -> fixture.native.engine.durableTerminalProjections.size.toString,
      "runtimejobs" -> (fixture.native.engine.runtimeState.durableJobs.size() + fixture.native.engine.runtimeState.runtimeJobs.size()).toString,
      "queue" -> fixture.native.engine._work_queue.size().toString, "drained" -> "0",
      "revision" -> snapshot.map(_.record.body.identity.revision).getOrElse(0L).toString,
      "providerrevision" -> snapshot.map(_.providerrevision.value).getOrElse(0L).toString,
      "chargedtasks" -> payment.map(_.seed.paid.chargedtasks).getOrElse(0L).toString,
      "historycredits" -> payment.map(_.seed.paid.historycredits).getOrElse(0L).toString,
      "admissions" -> payment.map(_.seed.paid.admissions).getOrElse(0L).toString,
      "cancelcredits" -> payment.map(_.seed.paid.cancelcredits).getOrElse(0L).toString,
      "remaining" -> payment.map(_.seed.remaining).getOrElse(0L).toString, "normalids" -> payment.map(_.seed.normalids.size).getOrElse(0).toString,
      "claims" -> payment.map(_.seed.claims.size).getOrElse(0).toString, "cancelclaimed" -> payment.exists(_.cancelclaimed).toString,
      "facade" -> (if (writer) { "Available" } else if (arguments.scenario == Scenario.ForeignReader) { "Refused" }
        else if (persistent) { "UnavailableAfterRestart" } else { "Empty" }),
      "lifetimepresent" -> fixture.row(DurableJobBackupFixtures.lifetimeId).nonEmpty.toString,
      "storagepresent" -> fixture.row(DurableJobBackupFixtures.storageId).nonEmpty.toString,
      "blobquotapresent" -> fixture.row(DurableJobBackupFixtures.blobQuotaId).nonEmpty.toString, "paidroots" -> paidroots.toString)
  }
}
