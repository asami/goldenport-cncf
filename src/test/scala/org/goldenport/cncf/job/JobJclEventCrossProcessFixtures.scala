package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import cats.data.NonEmptyVector
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{DomainEvent, EventDispatchHandler, EventRecord, EventStore, EventSubscription, ReceptionDomainEvent}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.EntityId

/*
 * Independent C10 selectors, native public targets and bounded owned JVMs.
 * The C9 composition supplies the unchanged configured provider and closure.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobJclEventCrossProcessFixtures extends Matchers {
  val componentName: String = "org.goldenport.cncf.test.JclEventProcessTarget"
  val observerName: String = "c10-existing-observer"
  private val _jvm_uuid: String = UUID.randomUUID().toString
  private val _receipt_keys: Set[String] = Set("mode", "scenario", "alpha", "count", "id", "root", "sqlite", "pid", "start", "uuid",
    "ready", "domainchecked", "descriptorchecked", "sourcechecked", "nativeunchanged", "privateabsent", "foreignrefused",
    "eventchecked", "eventcount", "observers", "subscriptions", "wrapperfailed", "failedactions", "actions", "commits",
    "sources", "pairs", "ports", "sqlwrites", "entitywrites", "entityloads", "publications", "gets", "opens", "puts", "deletes",
    "registered", "runtimejobs", "queue", "drained", "revision", "providerrevision", "chargedtasks", "historycredits",
    "admissions", "cancelcredits", "remaining", "normalids", "claims", "cancelclaimed", "facade", "lifetimepresent",
    "storagepresent", "blobquotapresent", "paidroots", "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed",
    "maintenanceclosed", "leaseunavailable", "workersclosed", "timersclosed", "schedulersclosed", "pending", "executing", "leases")

  enum Scenario(val wire: String) {
    case PersistentRoot extends Scenario("persistent-root")
    case PersistentFlow extends Scenario("persistent-flow")
    case NonPersistentEvent extends Scenario("nonpersistent-event")
    case EphemeralJob extends Scenario("ephemeral-job")
    case SourceFailure extends Scenario("source-failure")
    case DispatchFailure extends Scenario("dispatch-failure")
    case HandlerFailure extends Scenario("handler-failure")
    case ForeignReader extends Scenario("foreign-reader")
    case GeneratedPersistentFlow extends Scenario("generated-persistent-flow")
    def persistentJob: Boolean = Set(PersistentRoot, PersistentFlow, NonPersistentEvent, ForeignReader, GeneratedPersistentFlow).contains(this)
    def persistentEvent: Boolean = this != NonPersistentEvent
    def baseCount: Int = if (this == PersistentRoot) { 1 } else { 2 }
    def failed: Boolean = Set(SourceFailure, DispatchFailure, HandlerFailure).contains(this)
    def attemptedCount(handlers: Int): Int = this match {
      case SourceFailure | DispatchFailure => 2
      case HandlerFailure => 3
      case _ => baseCount + handlers
    }
    def committedCount(handlers: Int): Int = attemptedCount(handlers) - (if (this == SourceFailure || this == HandlerFailure) { 1 } else { 0 })
    def eventCount: Int = if (this == SourceFailure || !persistentEvent) { 0 } else { 1 }
    def observerCount: Int = if (this == SourceFailure) { 0 } else { 1 }
  }
  object Scenario {
    def parse(value: String): Scenario = Scenario.values.find(_.wire == value)
      .getOrElse(throw new IllegalArgumentException("closed C10 scenario required"))
  }
  final case class Identity(pid: Long, start: Instant, uuid: String)
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("closed receipt boolean required")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = if (apply("id").isEmpty) { Vector.empty } else { Vector(success(JobId.parse(apply("id")))) }
  }
  final case class Child(receipt: Receipt, identity: Identity, exited: Boolean, exitcode: Int)
  final case class Observation(operation: String, parameters: Map[String, String], context: ExecutionContext, uow: UnitOfWork, began: Instant)
  final case class EventObservation(event: ReceptionDomainEvent, context: ExecutionContext, before: Instant, after: Instant)
  final case class Boundary(boundary: DurableJobLifecycleWriteBoundary, active: Option[Int], count: Int, committed: Set[Int])

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def validateSelection(alpha: String, count: Int): Unit = {
    require(alpha.matches("[A-Za-z]{1,8}") && count >= 1 && count <= 3, "independent bounded C10 alpha/N required")
  }
  def operation(index: Int, scenario: Scenario): String = s"$componentName.command.${operationName(index, scenario)}"
  def operationName(index: Int, scenario: Scenario): String = {
    if (index == 0) { "root" } else if (index < scenario.baseCount) { "source" } else { s"handler${index - scenario.baseCount + 1}" }
  }
  def privateValue(alpha: String, index: Int): String = s"private-c10-$alpha-$index"
  def eventName(alpha: String): String = s"c10.accepted.$alpha"
  def parameters(alpha: String, index: Int): Map[String, String] = Map(
    "marker" -> privateValue(alpha, index), "inherited" -> s"root-c10-$alpha", "shared" -> s"override-$index", "position" -> index.toString)
  def parent(index: Int, scenario: Scenario, ids: Vector[String]): Option[String] = {
    if (index == 0) { None } else if (index < scenario.baseCount) { Some(ids(index - 1)) } else { Some(ids(scenario.baseCount - 1)) }
  }
  def relation(index: Int, scenario: Scenario): String = {
    if (index == 0) { "root" } else if (index < scenario.baseCount) { "child" } else { "continuation" }
  }
  def source(alpha: String, handlers: Int, scenario: Scenario): String = {
    validateSelection(alpha, handlers)
    val flow = if (scenario.baseCount == 1) { "" } else {
      s"""  flow:
         |    steps:
         |      - id: source
         |        action: ${operation(1, scenario)}
         |        parameters:
         |          marker: ${privateValue(alpha, 1)}
         |          shared: override-1
         |          position: "1"
         |""".stripMargin
    }
    val continuations = Vector.tabulate(handlers) { index =>
      val position = scenario.baseCount + index
      s"""      - id: handler-${index + 1}
         |        event: ${eventName(alpha)}
         |        action: ${operation(position, scenario)}
         |        parameters:
         |          marker: ${privateValue(alpha, position)}
         |          shared: override-$position
         |          position: "$position"
         |""".stripMargin
    }.mkString
    s"""job:
       |  name: jcl-c10-$alpha
       |  target:
       |    action: ${operation(0, scenario)}
       |  parameters:
       |    marker: ${privateValue(alpha, 0)}
       |    inherited: root-c10-$alpha
       |    shared: override-0
       |    position: "0"
       |  submit:
       |    persistence: ${if (scenario.persistentJob) { "persistent" } else { "ephemeral" }}
       |$flow  events:
       |    emit:
       |      - id: accepted
       |        after: ${if (scenario.baseCount == 1) { "root" } else { "source" }}
       |        name: ${eventName(alpha)}
       |        kind: c10-domain
       |        persistent: ${scenario.persistentEvent}
       |  onEvent:
       |    handlers:
       |$continuations""".stripMargin
  }
  def descriptor(id: String, index: Int, scenario: Scenario, ids: Vector[String], outcome: DurableTransactionOutcome): DurableTaskDescriptor = {
    val expectedparent = parent(index, scenario, ids)
    DurableTaskDescriptor(id, expectedparent, DurableTaskKind.Action,
      DurableTaskTarget("action", DurableOperationReference(componentName, None, operation(index, scenario), None)),
      DurableTaskRelation(if (index == 0) { DurableTaskRelationKind.Root } else if (index < scenario.baseCount) {
        DurableTaskRelationKind.Child
      } else { DurableTaskRelationKind.Continuation }, expectedparent),
      DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, outcome), None)
  }
  def recipe(scenario: Scenario): DurableJobKeyedEvidenceFixtures.Recipe = {
    val original = DurableJobOperationResponseQueryFixtures.recipe()
    val ids = Vector.tabulate(scenario.baseCount)(index => original.projection.tasks.head.taskId + s"-c10-$index")
    original.copy(projection = original.projection.copy(tasks = ids.zipWithIndex.map { case (id, index) =>
      descriptor(id, index, scenario, ids, DurableTransactionOutcome.Pending)
    }, definitionSnapshot = original.projection.definitionSnapshot.copy(definitionId = "inline-c10", key = "inline-c10",
      format = Some("yaml"), declaredProfile = Map.empty)))
  }
  def boundaries(scenario: Scenario, handlers: Int): Vector[Boundary] = {
    val base = scenario.baseCount
    val total = base + handlers
    val pre = Vector(DurableJobLifecycleWriteBoundary.Admission, DurableJobLifecycleWriteBoundary.StartIntent,
      DurableJobLifecycleWriteBoundary.RunningIntent).map(value => Boundary(value, None, base, Set.empty))
    val root = Vector(Boundary(DurableJobLifecycleWriteBoundary.TaskStartIntent, Some(0), 1, Set.empty)) ++
      (if (base == 2) { Vector(Boundary(DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint, Some(0), 1, Set(0)),
        Boundary(DurableJobLifecycleWriteBoundary.TaskStartIntent, Some(1), 2, Set(0))) } else { Vector.empty })
    val nested = Vector.tabulate(handlers) { index =>
      val position = base + index
      val committed = (0 until base - 1).toSet ++ (base until position).toSet
      Vector(Boundary(DurableJobLifecycleWriteBoundary.TaskStartIntent, Some(position), position + 1, committed),
        Boundary(DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint, Some(position), position + 1, committed + position))
    }.flatten
    pre ++ root ++ nested ++ Vector(
      Boundary(DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint, Some(base - 1), total, (0 until total).toSet),
      Boundary(DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint, None, total, (0 until total).toSet))
  }
  def checkModel(model: JobTaskReadModel, index: Int, scenario: Scenario, ids: Vector[String], committed: Boolean, failed: Boolean = false): Unit = {
    model.taskId.value shouldBe ids(index)
    model.taskKind shouldBe "action"
    model.targetKind shouldBe Some("action")
    model.component shouldBe Some(componentName)
    model.service shouldBe None
    model.operation shouldBe Some(operation(index, scenario))
    model.parentTaskId.map(_.value) shouldBe parent(index, scenario, ids)
    model.relation shouldBe Some(relation(index, scenario))
    model.transactionRole shouldBe Some("own")
    model.transactionScope shouldBe Some("per-task")
    model.transactionOutcome shouldBe Some(if (failed) { "failed" } else if (committed) { "committed" } else { "running" })
    model.status shouldBe (if (failed) { JobTaskStatus.Failed } else if (committed) { JobTaskStatus.Succeeded } else { JobTaskStatus.Running })
    model.result.success shouldBe !failed
    model.finishedAt.nonEmpty shouldBe (committed || failed)
    model.compensationActionRef shouldBe None
    model.compensatesTaskId shouldBe None
  }
  final class Source(scenario: Scenario, handlers: Int) extends DurableJobKeyedEvidenceSource {
    private val _requests: ConcurrentLinkedQueue[DurableJobKeyedEvidenceRequest] = new ConcurrentLinkedQueue
    private val _pairs: ConcurrentLinkedQueue[DurableJobLifecycleWriteEvidence] = new ConcurrentLinkedQueue
    private var _actual_ids: Vector[String] = Vector.empty
    def requests: Vector[DurableJobKeyedEvidenceRequest] = _requests.iterator().asScala.toVector
    def pairs: Vector[DurableJobLifecycleWriteEvidence] = _pairs.iterator().asScala.toVector
    def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse] = {
      val selected = boundaries(scenario, handlers)(requests.size)
      request.request.boundary shouldBe selected.boundary
      request.expectedrevision shouldBe requests.size.toLong + 1L
      val models = request.request match {
        case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
        case _ => Vector.empty
      }
      if (models.nonEmpty) {
        models should have size selected.count
        val ids = models.map(_.taskId.value)
        ids.distinct shouldBe ids
        ids.take(_actual_ids.size) shouldBe _actual_ids
        _actual_ids = ids
        models.zipWithIndex.foreach { case (model, index) =>
          checkModel(model, index, scenario, ids, selected.committed.contains(index))
        }
        request.request match {
          case DurableJobLifecycleWriteRequest.TaskStartIntent(jobid, taskid, parentid, _) =>
            jobid shouldBe request.jobid
            taskid.value shouldBe ids(selected.active.get)
            parentid.map(_.value) shouldBe parent(selected.active.get, scenario, ids)
          case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(jobid, taskid, parentid, _) =>
            jobid shouldBe request.jobid
            taskid.value shouldBe ids(selected.active.get)
            parentid.map(_.value) shouldBe parent(selected.active.get, scenario, ids)
          case _ => ()
        }
      }
      val original = DurableJobKeyedEvidenceFixtures.pair(request, recipe(scenario))
      val pair = original.copy(projection = original.projection.copy(tasks = original.projection.tasks.map(_.copy(kind = DurableTaskKind.Action))))
      val ids = if (models.nonEmpty) { _actual_ids } else { recipe(scenario).projection.tasks.map(_.taskId) }
      pair.projection.tasks shouldBe ids.zipWithIndex.map { case (id, index) =>
        descriptor(id, index, scenario, ids, if (selected.committed.contains(index)) { DurableTransactionOutcome.Committed } else { DurableTransactionOutcome.Pending })
      }
      _requests.add(request)
      _pairs.add(pair)
      Consequence.success(DurableJobKeyedEvidenceResponse(request.jobid, request.expectedrevision, pair))
    }
  }

  final class Fixture(val root: Path, val scenario: Scenario, val alpha: String, val handlers: Int, manual: Boolean) {
    private val _composition: JobJclCrossProcessFixtures.Fixture = new JobJclCrossProcessFixtures.Fixture(root, scenario.baseCount, manual)
    val native: JobDurableRuntimeFixtures.Fixture = _composition.native
    val observations: ConcurrentLinkedQueue[Observation] = new ConcurrentLinkedQueue
    val commits: ConcurrentLinkedQueue[UnitOfWork] = new ConcurrentLinkedQueue
    val events: ConcurrentLinkedQueue[EventObservation] = new ConcurrentLinkedQueue
    val trace: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue
    val source: Source = new Source(scenario, handlers)
    val sourceFailure: Conclusion = JobDurableRuntimeFixtures.failureConclusion(Consequence.argumentInvalid("c10 original source failure"))
    val dispatchFailure: Conclusion = JobDurableRuntimeFixtures.failureConclusion(Consequence.argumentInvalid("c10 original observer failure"))
    val handlerFailure: Conclusion = JobDurableRuntimeFixtures.failureConclusion(Consequence.argumentInvalid("c10 original handler failure"))
    native.selectedEvidence = source
    val target: Component = {
      val operations = Vector("root", "source", "handler1", "handler2", "handler3").map(name => TargetOperation(name, this))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(name = "command",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations))))))
      val id = ComponentId(componentName)
      val component = new Component() { override def displayName: String = componentName }
      val core = Component.Core.create(id.name, id, ComponentInstanceId.default(id), protocol, native.engine)
      val installed = new ComponentFactory().bootstrap(component.initialize(ComponentInit(native.subsystem, core, ComponentOrigin.Builtin)))
      native.subsystem.add(installed)
      installed
    }
    val subscriptionsBefore: Vector[EventSubscription] = native.subsystem.eventBus.subscriptions
    native.subsystem.eventBus.register(EventSubscription(observerName, eventName = Some(eventName(alpha)), kind = Some("c10-domain"),
      handler = new EventDispatchHandler {
        def dispatch(event: DomainEvent): Consequence[Unit] = Consequence.stateInvalid("C10 observer requires native authorized runtime context")
        override def dispatchAuthorized(event: DomainEvent)(using context: ExecutionContext): Consequence[Unit] = {
          val before = context.clock.instant()
          val received = event match {
            case value: ReceptionDomainEvent => value
            case _ => fail("original JCL ReceptionDomainEvent required")
          }
          trace.add("observer")
          events.add(EventObservation(received, context, before, context.clock.instant()))
          if (scenario == Scenario.DispatchFailure) { Consequence.Failure(dispatchFailure) } else { Consequence.unit }
        }
      }))
    val subscriptionsRegistered: Vector[EventSubscription] = native.subsystem.eventBus.subscriptions
    def owner: DurableJobRuntimeOwner = _composition.owner
    def sql: DurableJobOperationResponseQueryFixtures.NativeSql = _composition.sql
    def blob: DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore = _composition.blob
    def caller: ExecutionContext = _composition.caller
    def beforeRows: Vector[Option[Record]] = _composition.beforeRows
    def observationVector: Vector[Observation] = observations.iterator().asScala.toVector
    def eventVector: Vector[EventObservation] = events.iterator().asScala.toVector
    def traceVector: Vector[String] = trace.iterator().asScala.toVector
    def eventRecords: Vector[EventRecord] = success(native.subsystem.eventStore.query(EventStore.Query(name = Some(eventName(alpha)))))
    def row(id: EntityId): Option[Record] = _composition.row(id)
    def rows(ids: Vector[JobId]): Vector[Option[Record]] = _composition.rows(ids)
    def start(ids: Vector[JobId] = Vector.empty): Unit = {
      (target.jobEngine eq native.engine) shouldBe true
      subscriptionsRegistered.drop(subscriptionsBefore.size).map(_.name) shouldBe Vector(observerName)
      _composition.start(ids)
    }
    def submit(): Consequence[OperationResponse] = _composition.invoke("submit_job_definition",
      List(org.goldenport.protocol.Argument("body", JobJclEventCrossProcessFixtures.source(alpha, handlers, scenario))))
    def close(primary: Option[Throwable]): Unit = _composition.close(primary)
    def closureFacts: Map[String, String] = _composition.closureFacts
  }
  private final case class TargetOperation(opname: String, fixture: Fixture) extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = opname, request = spec.RequestDefinition(), response = spec.ResponseDefinition.void)
    override def createOperationRequest(req: Request): Consequence[OperationRequest] = Consequence.success(TargetAction(req, opname, fixture))
  }
  private final case class TargetAction(request: Request, opname: String, fixture: Fixture) extends CommandAction {
    override def createCall(core: ActionCall.Core): ActionCall = TargetCall(core, opname, fixture)
  }
  private final case class TargetCall(core: ActionCall.Core, opname: String, fixture: Fixture) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val context = core.executionContext
      val parameters = core.action.request.arguments.map(argument => argument.name -> argument.value.toString).toMap
      val uow = context.unitOfWork
      fixture.trace.add(opname)
      fixture.observations.add(Observation(opname, parameters, context, uow, context.clock.instant()))
      if (opname == "source" && fixture.scenario == Scenario.SourceFailure) { Consequence.Failure(fixture.sourceFailure) }
      else if (opname == "handler1" && fixture.scenario == Scenario.HandlerFailure) { Consequence.Failure(fixture.handlerFailure) }
      else {
        uow.stagePostCommit { fixture.commits.add(uow); () }
        Consequence.success(OperationResponse.Scalar(parameters("marker")))
      }
    }
  }
  def identity(): Identity = {
    val process = ProcessHandle.current()
    Identity(process.pid(), process.info().startInstant().orElseThrow(() => new IllegalStateException("actual process start required")), _jvm_uuid)
  }
  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe _receipt_keys
    facts.values.exists(_.contains("private-c10")) shouldBe false
    val bytes = facts.toVector.sortBy(_._1).map { case (key, value) =>
      key + "=" + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
    bytes.length should be <= 16384
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }
  def readReceipt(path: Path): Receipt = {
    require(Files.size(path) > 0L && Files.size(path) <= 16384L, "bounded scalar C10 receipt required")
    val entries = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "closed receipt entry required")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = entries.toMap
    require(facts.size == entries.size && facts.keySet == _receipt_keys, "closed unique receipt fields required")
    facts.values.exists(_.contains("private-c10")) shouldBe false
    val receipt = Receipt(facts)
    UUID.fromString(receipt.identity.uuid)
    receipt
  }
  final class Case(val root: Path, val scenario: Scenario, val alpha: String, val count: Int) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    def writer(): Child = _launch("writer", Vector.empty)
    def reader(ids: Vector[JobId]): Child = _launch("reader", if (scenario.persistentJob) { ids } else { Vector.empty })
    private def _launch(mode: String, ids: Vector[JobId]): Child = {
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute Java executable required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val argv = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobJclEventCrossProcessProbe", mode, native.toString, receiptpath.toString,
        scenario.wire, alpha, count.toString) ++ ids.map(_.value)
      val process = new ProcessBuilder(argv*).directory(root.toFile).redirectOutput(logs.resolve(s"$mode.stdout.log").toFile)
        .redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() => new IllegalStateException("actual owned child start required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) { throw new IllegalStateException(s"C10 $mode timeout; logs at $logs") }
        val exit = process.exitValue()
        if (exit != 0) {
          val input = Files.newInputStream(stderr)
          val excerpt = try { new String(input.readNBytes(2048), StandardCharsets.UTF_8) } finally { input.close() }
          throw new IllegalStateException(s"C10 $mode exit $exit; logs at $logs; $excerpt")
        }
        val receipt = readReceipt(receiptpath)
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        receipt("mode") shouldBe mode
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt("scenario") shouldBe scenario.wire
        receipt("alpha") shouldBe alpha
        receipt.number("count") shouldBe count.toLong
        if (mode == "reader") { receipt.ids shouldBe ids }
        Child(receipt, receipt.identity, !process.isAlive, exit)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS)) { throw new IllegalStateException("owned C10 child did not terminate") }
          }
        }
      }
    }
  }
  def withCase[A](scenario: Scenario, alpha: String, count: Int)(body: Case => A): A = {
    validateSelection(alpha, count)
    val parent = Files.createDirectories(Paths.get("target/job-jcl-event-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try { val result = body(new Case(root, scenario, alpha, count)); successful = true; result }
    finally { if (successful) { JobDurableRuntimeFixtures.remove(root) } }
  }
}
