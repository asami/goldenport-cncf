package org.goldenport.cncf.job

import java.nio.file.{Files, Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Argument
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.yaml.snakeyaml.parser.ParserException
import JobJclCrossProcessFixtures.{Fixture, Scenario, descriptor, operation, parameters, privateValue, recipe, source, success, validateSelection}

/*
 * Public JCL writer and independently fresh native source/metadata reader.
 * No source, Task, signed record, payment or definition crosses JVM arguments.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object JobJclCrossProcessProbe extends Matchers {
  private final case class Arguments(mode: String, root: Path, receipt: Path, scenario: Scenario,
    alpha: String, count: Int, ids: Vector[JobId])

  def main(args: Array[String]): Unit = {
    val arguments = _arguments(args)
    val fixture = new Fixture(arguments.root, arguments.count, manual = arguments.mode != "writer")
    var facts = Map.empty[String, String]
    var primary = Option.empty[Throwable]
    try {
      facts = if (arguments.mode == "writer") { _writer(fixture, arguments) } else { _reader(fixture, arguments) }
    } catch {
      case exception: Throwable => primary = Some(exception); throw exception
    } finally { fixture.close(primary) }
    val identity = JobJclCrossProcessFixtures.identity()
    JobJclCrossProcessFixtures.writeReceipt(arguments.receipt, facts ++ fixture.closureFacts ++ Map(
      "mode" -> arguments.mode, "scenario" -> arguments.scenario.wire, "alpha" -> arguments.alpha,
      "count" -> arguments.count.toString, "root" -> arguments.root.toString,
      "sqlite" -> arguments.root.resolve("jobs.db").toString, "pid" -> identity.pid.toString,
      "start" -> identity.start.toString, "uuid" -> identity.uuid))
  }

  private def _arguments(args: Array[String]): Arguments = {
    require(args.length >= 6 && args.length <= 7, "closed C9 mode/root/receipt/scenario/alpha/count/opaque Job ID required")
    val mode = args(0)
    require(Set("writer", "reader", "foreign-reader").contains(mode), "closed C9 mode required")
    val root = Paths.get(args(1))
    val receipt = Paths.get(args(2))
    require(root.isAbsolute && root.normalize() == root && Files.isDirectory(root) &&
      root.getFileName.toString == "native" && root.getParent.getFileName.toString.startsWith("case-") &&
      root.getParent.getParent.getFileName.toString == "work" &&
      root.getParent.getParent.getParent.getFileName.toString == "job-jcl-cross-process", "owned C9 native namespace required")
    require(receipt.isAbsolute && receipt.normalize() == receipt && receipt.getParent == root.getParent.resolve("control") &&
      receipt.getFileName.toString == s"$mode.receipt" && !Files.exists(receipt), "new owned C9 receipt required")
    val scenario = Scenario.parse(args(3))
    val alpha = args(4)
    val count = args(5).toInt
    validateSelection(alpha, count)
    require(scenario == Scenario.Inline || count == 1, "one-task stored/refused/ephemeral selection required")
    val ids = args.drop(6).toVector.map(value => success(JobId.parse(value)))
    val expected = if (mode == "writer" || scenario == Scenario.Ephemeral || scenario == Scenario.Refusals) { 0 } else { 1 }
    require(ids.size == expected, "only completed persistent readers receive one opaque Job ID")
    require(mode != "foreign-reader" || scenario == Scenario.Inline, "foreign C9 selection requires Persistent inline completion")
    Arguments(mode, root, receipt, scenario, alpha, count, ids)
  }

  private def _record(response: OperationResponse): Record = {
    response match {
      case OperationResponse.RecordResponse(record) => record
      case _ => fail("real public JobControl RecordResponse required")
    }
  }
  private def _invoke(fixture: Fixture, name: String, arguments: List[Argument]): Record = {
    _record(success(fixture.invoke(name, arguments)))
  }

  private def _writer(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    fixture.start()
    fixture.beforeRows shouldBe Vector.fill(3)(None)
    if (arguments.scenario == Scenario.Refusals) {
      _refusals(fixture, arguments)
      _facts(fixture, arguments, Vector.empty, persistent = false, sourcechecked = false, foreign = false, refusals = 4)
    } else {
      val stored = arguments.scenario == Scenario.Stored
      val ephemeral = arguments.scenario == Scenario.Ephemeral
      val body = source(arguments.alpha, arguments.count, ephemeral)
      var definition = Option.empty[JobDefinitionEntity]
      if (stored) {
        val created = _invoke(fixture, "create_job_definition", List(Argument("key", JobJclCrossProcessFixtures.key(arguments.alpha)),
          Argument("body", body), Argument("status", "active")))
        val obtained = _invoke(fixture, "get_job_definition", List(Argument("key", JobJclCrossProcessFixtures.key(arguments.alpha))))
        obtained shouldBe created
        definition = fixture.currentDefinition(JobJclCrossProcessFixtures.key(arguments.alpha))
        definition.nonEmpty shouldBe true
        val current = definition.get
        current.id.value shouldBe created.getString("id").get
        current.jclSource shouldBe body
        current.status shouldBe JobDefinitionStatus.Active
        fixture.selectDefinition(current.id.value, current.key)
      }
      val submitted = _invoke(fixture, "submit_job_definition", List(Argument("body",
        if (stored) { s"jobDefinitionRef: ${JobJclCrossProcessFixtures.key(arguments.alpha)}" } else { body })))
      submitted.getAny("submitted-job-ids").nonEmpty shouldBe true
      val ids = submitted.getAny("submitted-job-ids").collect { case values: Seq[?] =>
        values.toVector.map(value => success(JobId.parse(value.toString)))
      }.getOrElse(fail("actual opaque public submitted Job IDs required"))
      ids should have size 1
      val id = ids.head
      success(fixture.native.engine.awaitResult(id, 5000L)) shouldBe
        JobResult.Success(OperationResponse.Scalar(privateValue(arguments.alpha, arguments.count - 1)))
      fixture.native.engine._state_monitor.synchronized {
        fixture.native.engine.getStatus(id) shouldBe Some(JobStatus.Succeeded)
        fixture.native.engine.getResult(id) shouldBe Some(JobResult.Success(
          OperationResponse.Scalar(privateValue(arguments.alpha, arguments.count - 1))))
        fixture.native.engine._durable_lifecycle_write_failures.containsKey(id) shouldBe false
        _live(fixture, id, arguments)
        if (!ephemeral) {
          _boundaries(fixture, id, arguments)
          val terminal = _native(fixture, id, arguments, definition)
          terminal.record.body.tasks.map(_.taskId) shouldBe fixture.native.engine.query(id).get.tasks.tasks.map(_.taskId.value)
        }
        if (stored) { _submitted_source(fixture, id, arguments, definition.get) }
      }
      val observations = fixture.observationVector
      observations.map(_.operation) shouldBe Vector("root", "first", "last").take(arguments.count)
      observations.map(_.parameters) shouldBe Vector.tabulate(arguments.count)(index => parameters(arguments.alpha, index))
      observations.map(observation => observation.context.jobContext.currentTask.orElse(observation.context.jobContext.taskId)) shouldBe
        fixture.native.engine.query(id).get.tasks.tasks.map(task => Some(task.taskId))
      fixture.commits.size() shouldBe arguments.count
      observations.foreach { observation =>
        observation.context.security.principal.id.value shouldBe "subject-a"
        observation.context.security.principal.attributes("tenantId") shouldBe "tenant-a"
        observation.context.security.subjectKind.toString shouldBe "User"
        observation.context.scope.core.name shouldBe operation(Vector("root", "first", "last").indexOf(observation.operation))
        observation.context.jobContext.jobId shouldBe Some(id)
        observation.context.jobContext.currentTask.orElse(observation.context.jobContext.taskId).nonEmpty shouldBe true
        (observation.context.dataStoreSpace eq fixture.native.context.dataStoreSpace) shouldBe true
        (observation.context.entityStoreSpace eq fixture.native.context.entityStoreSpace) shouldBe true
        observation.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        observation.uow.lastCommitResult.exists(_.isSuccess) shouldBe true
        fixture.commits.contains(observation.uow) shouldBe true
      }
      success(fixture.native.engine.queryManagementResult(id)(using fixture.caller)) shouldBe
        Some(JobManagementResult.Available(JobResult.Success(OperationResponse.Scalar(privateValue(arguments.alpha, arguments.count - 1)))))
      if (stored) {
        val current = definition.get
        _invoke(fixture, "update_job_definition", List(Argument("key", current.key),
          Argument("body", source(arguments.alpha, 1, replacement = true))))
        _invoke(fixture, "retire_job_definition", List(Argument("key", current.key)))
        val changed = _invoke(fixture, "get_job_definition", List(Argument("key", current.key)))
        changed.getString("id") shouldBe Some(current.id.value)
        changed.getString("jclSource") shouldBe Some(source(arguments.alpha, 1, replacement = true))
        changed.getString("definitionStatus") shouldBe Some("retired")
        fixture.currentDefinition(current.key).get.id shouldBe current.id
        _submitted_source(fixture, id, arguments, current)
        fixture.observationVector shouldBe observations
        observations.count(_.parameters("marker") == privateValue(arguments.alpha, 0, replacement = true)) shouldBe 0
      }
      if (ephemeral) { _empty(fixture, ids) }
      _no_payload(fixture)
      fixture.native.tasks.isEmpty shouldBe true
      fixture.native.port.calls.get() shouldBe 0
      fixture.native.engine._work_queue.isEmpty shouldBe true
      _facts(fixture, arguments, ids, !ephemeral, stored, foreign = false, refusals = 0)
    }
  }

  private def _live(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val live = fixture.native.engine.query(id).get
    live.status shouldBe JobStatus.Succeeded
    live.tasks.totalCount shouldBe arguments.count
    val taskids = live.tasks.tasks.map(_.taskId.value)
    taskids should have size arguments.count
    taskids.distinct shouldBe taskids
    live.tasks.tasks.zipWithIndex.foreach { case (task, index) =>
      val expectedparent = if (index == 0) { None } else { Some(taskids(index - 1)) }
      task.taskKind shouldBe "action"
      task.targetKind shouldBe Some("action")
      task.component shouldBe Some(JobJclCrossProcessFixtures.componentName)
      task.service shouldBe None
      task.operation shouldBe Some(operation(index))
      task.relation shouldBe Some(if (index == 0) { "root" } else { "child" })
      task.parentTaskId.map(_.value) shouldBe expectedparent
      task.transactionRole shouldBe Some("own")
      task.transactionScope shouldBe Some("per-task")
      task.transactionOutcome shouldBe Some("committed")
      task.status shouldBe JobTaskStatus.Succeeded
      task.result.success shouldBe true
      task.compensationActionRef shouldBe None
      task.compensatesTaskId shouldBe None
    }
    live.debug.parameters.get("cncf.job.entitySync") shouldBe None
    live.debug.executionNotes.exists(_.contains("job-entity-sync-failed")) shouldBe false
    live.debug.parameters("jcl.target.action") shouldBe operation(0)
    if (arguments.count > 1) {
      live.debug.parameters("jcl.flow.step.ids") shouldBe (1 until arguments.count).map(index => s"step-$index").mkString(",")
    }
  }
  private def _boundaries(fixture: Fixture, id: JobId, arguments: Arguments): Unit = {
    val boundaries = Vector(DurableJobLifecycleWriteBoundary.Admission, DurableJobLifecycleWriteBoundary.StartIntent,
      DurableJobLifecycleWriteBoundary.RunningIntent) ++ Vector.fill(arguments.count)(Vector(
      DurableJobLifecycleWriteBoundary.TaskStartIntent, DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint)).flatten ++
      Vector(DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
    fixture.source.requests.map(_.jobid).distinct shouldBe Vector(id)
    fixture.source.requests.map(_.request.boundary) shouldBe boundaries
    fixture.source.requests.map(_.expectedrevision) shouldBe (1L to (4L + 2L * arguments.count)).toVector
    fixture.source.pairs.size shouldBe boundaries.size
    fixture.source.requests.zip(fixture.source.pairs).foreach { case (request, pair) =>
      pair.projection.semanticRevision shouldBe request.expectedrevision
      val models = request.request match {
        case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
        case _ => Vector.empty
      }
      val pendingids = recipe(arguments.count).projection.tasks.map(_.taskId)
      val expectedids = if (models.nonEmpty) { models.map(_.taskId.value) } else { pendingids }
      pair.projection.tasks.map(_.taskId) shouldBe expectedids
      expectedids.size should (be >= 1 and be <= arguments.count)
      expectedids.distinct shouldBe expectedids
      pair.projection.tasks.zipWithIndex.foreach { case (task, index) =>
        val expectedparent = if (index == 0) { None } else { Some(expectedids(index - 1)) }
        val outcome = if (models.nonEmpty && models(index).transactionOutcome.contains("committed")) {
          DurableTransactionOutcome.Committed
        } else { DurableTransactionOutcome.Pending }
        task shouldBe descriptor(expectedids(index), index, expectedparent, outcome)
      }
      if (models.nonEmpty) {
        val starting = request.request.boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent
        models.zipWithIndex.foreach { case (model, index) =>
          val running = starting && index == models.size - 1
          model.transactionOutcome shouldBe Some(if (running) { "running" } else { "committed" })
          model.status shouldBe (if (running) { JobTaskStatus.Running } else { JobTaskStatus.Succeeded })
          model.parentTaskId.map(_.value) shouldBe (if (index == 0) { None } else { Some(expectedids(index - 1)) })
          model.relation shouldBe Some(if (index == 0) { "root" } else { "child" })
        }
      }
    }
  }
  private def _native(fixture: Fixture, id: JobId, arguments: Arguments,
    definition: Option[JobDefinitionEntity]): DurableJobStoreSnapshot = {
    val snapshot = fixture.native.signed(id)
    val body = snapshot.record.body
    val expected = definition.map(current => recipe(arguments.count, current.id.value, current.key))
      .getOrElse(recipe(arguments.count)).projection
    body.identity.jobId shouldBe id.value
    body.identity.revision shouldBe 4L + 2L * arguments.count
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.result shouldBe DurableResultOutcome.Succeeded(DurableValue.Absent)
    body.authorization shouldBe expected.authorization
    body.definitionSnapshot shouldBe expected.definitionSnapshot
    body.definitionSnapshot.declaredSource shouldBe None
    body.inputs shouldBe Vector.empty
    body.diagnostics shouldBe Vector.empty
    body.calltreeReference shouldBe None
    body.tasks should have size arguments.count
    val taskids = body.tasks.map(_.taskId)
    taskids.distinct shouldBe taskids
    body.tasks.zipWithIndex.foreach { case (task, index) =>
      val expectedparent = if (index == 0) { None } else { Some(taskids(index - 1)) }
      task shouldBe descriptor(taskids(index), index, expectedparent, DurableTransactionOutcome.Committed)
    }
    val payment = fixture.native.native(id)
    val notes = if (arguments.count == 1) { 1L } else { 2L }
    payment.seed.paid shouldBe JobLifetimeSnapshot(2L * arguments.count, 32L + 64L * arguments.count + notes, 1L, 1L)
    payment.seed.remaining shouldBe 0L
    payment.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    payment.seed.normalids.size shouldBe arguments.count
    payment.seed.claims shouldBe Set.empty
    payment.cancelclaimed shouldBe false
    Vector.tabulate(arguments.count)(index => privateValue(arguments.alpha, index)).foreach(value => body.toString.contains(value) shouldBe false)
    body.toString.contains(source(arguments.alpha, arguments.count)) shouldBe false
    snapshot
  }
  private def _submitted_source(fixture: Fixture, id: JobId, arguments: Arguments,
    definition: JobDefinitionEntity): Unit = {
    val body = source(arguments.alpha, arguments.count)
    val entityid = JobEntity.entityId(id)
    val carrier = success(fixture.owner._execute_c {
      fixture.native.entity.loadDetached[JobEntity](entityid)(using JobEntity.entityPersistent, fixture.native.context)
    }).getOrElse(fail("engine-generated positive typed JobEntity required"))
    carrier.revision.value should be > 0L
    carrier.entity.id shouldBe entityid
    val record = carrier.entity.record
    record.getString("jobId") shouldBe Some(id.value)
    record.getString("status") shouldBe Some("Succeeded")
    record.getString("persistence") shouldBe Some("Persistent")
    record.getString("jobDefinitionId") shouldBe Some(definition.id.value)
    record.getString("jobDefinitionKey") shouldBe Some(definition.key)
    val snapshot = record.getRecord("jobDefinitionSnapshot").getOrElse(fail("actual stored submitted source snapshot required"))
    val debug = record.getRecord("debugParameters").getOrElse(fail("actual stored debug parameters required"))
    Vector(snapshot, debug).foreach { value =>
      value.getString("jcl.jobDefinition.id") shouldBe Some(definition.id.value)
      value.getString("jcl.jobDefinition.key") shouldBe Some(definition.key)
      value.getString("jcl.jobDefinition.source") shouldBe Some(body)
      value.getString("jcl.jobDefinition.format") shouldBe Some("yaml")
      Vector("version", "revision", "hash").foreach(name => value.getAny(s"jcl.jobDefinition.$name") shouldBe None)
    }
    debug.getString("cncf.job.entitySync") shouldBe None
    record.getString("debugExecutionNotes").exists(_.contains("job-entity-sync-failed")) shouldBe false
    record.getRecord("declaredProfile").get.getString("expectedStatus") shouldBe Some("Succeeded")
    val signed = _native(fixture, id, arguments, Some(definition)).record.body.definitionSnapshot
    signed.definitionId shouldBe snapshot.getString("jcl.jobDefinition.id").get
    signed.key shouldBe snapshot.getString("jcl.jobDefinition.key").get
    signed.format shouldBe snapshot.getString("jcl.jobDefinition.format")
    signed.declaredSource shouldBe None
    if (arguments.mode == "writer") {
      val live = fixture.native.engine.query(id).get.debug
      val captured = live.jobDefinitionSnapshot.get
      captured.id shouldBe definition.id.value
      captured.key shouldBe definition.key
      captured.jclSource shouldBe Some(body)
      captured.jclFormat shouldBe Some("yaml")
      captured.profile.flatMap(_.expectedStatus) shouldBe Some(JobStatus.Succeeded)
      live.parameters("jcl.jobDefinition.source") shouldBe body
    }
  }

  private def _refusals(fixture: Fixture, arguments: Arguments): Unit = {
    val body = source(arguments.alpha, 1)
    val draftkey = JobJclCrossProcessFixtures.key(arguments.alpha) + "-draft"
    val retiredkey = JobJclCrossProcessFixtures.key(arguments.alpha) + "-retired"
    Vector(draftkey -> "draft", retiredkey -> "retired").foreach { case (selected, status) =>
      _invoke(fixture, "create_job_definition", List(Argument("key", selected), Argument("body", body), Argument("status", status)))
    }
    val definitions = Vector(draftkey, retiredkey).map(selected => fixture.currentDefinition(selected).get)
    val rows = fixture.rows(Vector.empty, definitions.map(_.id))
    val effects = fixture.effects
    val jobs = fixture.native.engine.listJobs(persistentOnly = false)
    val invalid = Vector(s"jobDefinitionRef: $draftkey", s"jobDefinitionRef: $retiredkey", "job: [",
      body + "  unsupported: forbidden\n")
    invalid.zipWithIndex.foreach { case (text, index) =>
      val result = fixture.invoke("submit_job_definition", List(Argument("body", text)))
      result.isSuccess shouldBe false
      val conclusion = JobDurableRuntimeFixtures.failureConclusion(result)
      val message = conclusion.observation.getEffectiveMessage.get
      if (index == 2) {
        message should include ("expected the node content")
        conclusion.getException.get.getClass shouldBe classOf[ParserException]
        conclusion.observation.taxonomy shouldBe Taxonomy(Taxonomy.Category.System, Taxonomy.Symptom.Corrupted)
      } else {
        if (index < 2) { message shouldBe s"JobDefinition is not active: ${Vector(draftkey, retiredkey)(index)}" }
        else { message shouldBe "job contains unsupported keys: unsupported" }
        conclusion.observation.taxonomy shouldBe JobDurableRuntimeFixtures.failureConclusion(Consequence.argumentInvalid("expected JCL refusal")).observation.taxonomy
      }
    }
    fixture.native.engine.listJobs(persistentOnly = false) shouldBe jobs
    fixture.rows(Vector.empty, definitions.map(_.id)) shouldBe rows
    fixture.effects.copy(entityloads = effects.entityloads) shouldBe effects
    fixture.source.requests shouldBe Vector.empty
    fixture.observationVector shouldBe Vector.empty
    definitions.zip(Vector(draftkey, retiredkey)).foreach { case (before, selected) => fixture.currentDefinition(selected) shouldBe Some(before) }
    fixture.row(DurableJobBackupFixtures.lifetimeId) shouldBe None
    fixture.row(DurableJobBackupFixtures.storageId) shouldBe None
    _no_payload(fixture)
  }
  private def _reader(fixture: Fixture, arguments: Arguments): Map[String, String] = {
    val stored = arguments.scenario == Scenario.Stored
    fixture.start(arguments.ids, if (stored) { Some(JobJclCrossProcessFixtures.key(arguments.alpha)) } else { None })
    val definition = if (stored) {
      val current = fixture.currentDefinition(JobJclCrossProcessFixtures.key(arguments.alpha)).getOrElse(fail("fresh exact-key stored definition required"))
      current.key shouldBe JobJclCrossProcessFixtures.key(arguments.alpha)
      current.jclSource shouldBe source(arguments.alpha, 1, replacement = true)
      current.jclSource should not be source(arguments.alpha, 1)
      current.jclFormat shouldBe "yaml"
      current.status shouldBe JobDefinitionStatus.Retired
      Some(current)
    } else { None }
    val definitions = definition.toVector.map(_.id)
    fixture.rows(arguments.ids, definitions) shouldBe fixture.beforeRows
    val rows = fixture.rows(arguments.ids, definitions)
    val payments = arguments.ids.map(fixture.native.native)
    val snapshots = arguments.ids.map(id => _native(fixture, id, arguments, definition))
    val foreign = arguments.mode == "foreign-reader"
    val caller = if (foreign) {
      DurableJobOperationResponseQueryFixtures.caller(fixture.native.context, subject = "foreign-subject", tenant = "foreign-tenant")
    } else { fixture.caller }
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
      raw.debug.parameters.values.exists(_.contains("private-c9")) shouldBe false
      raw.debug.jobDefinitionSnapshot.flatMap(_.jclSource) shouldBe None
      raw.tasks.totalCount shouldBe arguments.count
      val taskids = raw.tasks.tasks.map(_.taskId.value)
      taskids shouldBe snapshot.record.body.tasks.map(_.taskId)
      taskids.distinct shouldBe taskids
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
        actual.observation.getEffectiveMessage.get.contains("private-c9") shouldBe false
      } else {
        success(visible).get.result shouldBe None
        success(management) shouldBe Some(JobManagementResult.UnavailableAfterRestart(raw.resultSummary))
      }
      raw.tasks.tasks.zipWithIndex.foreach { case (task, index) =>
        task.taskId.value shouldBe snapshot.record.body.tasks(index).taskId
        task.taskKind shouldBe "action"
        task.targetKind shouldBe Some("action")
        task.component shouldBe Some(JobJclCrossProcessFixtures.componentName)
        task.operation shouldBe Some(operation(index))
        task.service shouldBe None
        task.relation shouldBe Some(if (index == 0) { "root" } else { "child" })
        task.parentTaskId.map(_.value) shouldBe (if (index == 0) { None } else { Some(taskids(index - 1)) })
        task.transactionRole shouldBe Some("own")
        task.transactionScope shouldBe Some("per-task")
        task.transactionOutcome shouldBe Some("committed")
        task.status shouldBe JobTaskStatus.Succeeded
        task.result.success shouldBe true
        task.compensationActionRef shouldBe None
        task.compensatesTaskId shouldBe None
      }
      if (stored) { _submitted_source(fixture, id, arguments, definition.get) }
    }
    fixture.native.tasks.isEmpty shouldBe true
    fixture.native.engine.runtimeState.durableJobs.isEmpty shouldBe true
    fixture.native.engine.runtimeState.runtimeJobs.isEmpty shouldBe true
    fixture.native.engine._work_queue.isEmpty shouldBe true
    fixture.native.engine.drainAll() shouldBe 0
    fixture.observationVector shouldBe Vector.empty
    fixture.commits.size() shouldBe 0
    fixture.source.requests shouldBe Vector.empty
    fixture.source.pairs shouldBe Vector.empty
    fixture.native.port.calls.get() shouldBe 0
    fixture.sql.writes.get() shouldBe 0
    fixture.native.entity.writes.get() shouldBe 0
    _no_payload(fixture)
    fixture.rows(arguments.ids, definitions) shouldBe rows
    arguments.ids.map(fixture.native.native) shouldBe payments
    arguments.ids.map(id => _native(fixture, id, arguments, definition)) shouldBe snapshots
    fixture.rows(arguments.ids, definitions) shouldBe rows
    if (arguments.ids.isEmpty) { _empty(fixture, Vector.empty) }
    _facts(fixture, arguments, arguments.ids, arguments.ids.nonEmpty, stored, foreign, refusals = 0)
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
    fixture.native.engine.durableTerminalProjections shouldBe Vector.empty
    _no_payload(fixture)
  }
  private def _facts(fixture: Fixture, arguments: Arguments, ids: Vector[JobId], persistent: Boolean,
    sourcechecked: Boolean, foreign: Boolean, refusals: Int): Map[String, String] = {
    val effects = fixture.effects
    val payment = if (persistent) { Some(fixture.native.native(ids.head)) } else { None }
    val snapshot = if (persistent) { Some(fixture.native.signed(ids.head)) } else { None }
    val paidroots = fixture.row(DurableJobBackupFixtures.lifetimeId).map(row => success(
      DurableJobLifetimeQuotaCodec.decode(row.getString("canonicalJson").get)).entries.count(
      _.rootstate == DurableJobLifetimeQuotaRootState.Published)).getOrElse(0)
    paidroots shouldBe (if (persistent) { 1 } else { 0 })
    Map("id" -> ids.headOption.map(_.value).getOrElse(""), "ready" -> (fixture.owner.health.state == JobDurableOperationalState.Ready).toString,
      "domainchecked" -> (arguments.mode == "writer" && refusals == 0).toString,
      "descriptorchecked" -> persistent.toString, "sourcechecked" -> sourcechecked.toString,
      "nativeunchanged" -> "true", "privateabsent" -> "true", "foreignrefused" -> foreign.toString,
      "actions" -> effects.actions.toString, "commits" -> effects.commits.toString, "sources" -> effects.sources.toString,
      "pairs" -> effects.pairs.toString, "ports" -> effects.ports.toString, "sqlwrites" -> effects.sqlwrites.toString,
      "entitywrites" -> effects.entitywrites.toString, "entityloads" -> effects.entityloads.toString,
      "publications" -> effects.publications.toString, "gets" -> effects.gets.toString, "opens" -> effects.opens.toString,
      "puts" -> effects.puts.toString, "deletes" -> effects.deletes.toString,
      "registered" -> fixture.native.engine.durableTerminalProjections.size.toString,
      "runtimejobs" -> (fixture.native.engine.runtimeState.durableJobs.size() + fixture.native.engine.runtimeState.runtimeJobs.size()).toString,
      "queue" -> fixture.native.engine._work_queue.size().toString, "drained" -> "0",
      "revision" -> snapshot.map(_.record.body.identity.revision).getOrElse(0L).toString,
      "providerrevision" -> snapshot.map(_.providerrevision.value).getOrElse(0L).toString,
      "chargedtasks" -> payment.map(_.seed.paid.chargedtasks).getOrElse(0L).toString,
      "historycredits" -> payment.map(_.seed.paid.historycredits).getOrElse(0L).toString,
      "admissions" -> payment.map(_.seed.paid.admissions).getOrElse(0L).toString,
      "cancelcredits" -> payment.map(_.seed.paid.cancelcredits).getOrElse(0L).toString,
      "remaining" -> payment.map(_.seed.remaining).getOrElse(0L).toString,
      "normalids" -> payment.map(_.seed.normalids.size).getOrElse(0).toString,
      "claims" -> payment.map(_.seed.claims.size).getOrElse(0).toString,
      "cancelclaimed" -> payment.exists(_.cancelclaimed).toString,
      "facade" -> (if (arguments.mode == "writer" && refusals == 0) { "Available" }
        else if (foreign) { "Refused" } else if (persistent) { "UnavailableAfterRestart" } else { "Empty" }),
      "lifetimepresent" -> fixture.row(DurableJobBackupFixtures.lifetimeId).nonEmpty.toString,
      "storagepresent" -> fixture.row(DurableJobBackupFixtures.storageId).nonEmpty.toString,
      "blobquotapresent" -> fixture.row(DurableJobBackupFixtures.blobQuotaId).nonEmpty.toString,
      "paidroots" -> paidroots.toString, "refusals" -> refusals.toString)
  }
}
