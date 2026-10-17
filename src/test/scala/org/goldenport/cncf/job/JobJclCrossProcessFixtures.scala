package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.context.{DataStoreContext, EntitySpaceContext, EntityStoreContext, ExecutionContext, RuntimeContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.{EntityConcurrencyPolicy, EntityRevisionBinding, EntityRevisionModelMetadata}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage}
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.EntityId

/*
 * Native public JCL production and independently supplied process selectors.
 * Receipts contain observations after local assertions and resource closure.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobJclCrossProcessFixtures extends Matchers {
  val componentName: String = "org.goldenport.cncf.test.JclProcessTarget"
  private val _jvm_uuid = UUID.randomUUID().toString
  private val _receipt_keys = Set("mode", "scenario", "alpha", "count", "id", "root", "sqlite", "pid", "start", "uuid",
    "ready", "domainchecked", "descriptorchecked", "sourcechecked", "nativeunchanged", "privateabsent", "foreignrefused",
    "actions", "commits", "sources", "pairs", "ports", "sqlwrites", "entitywrites", "entityloads", "publications",
    "gets", "opens", "puts", "deletes", "registered", "runtimejobs", "queue", "drained", "revision", "providerrevision",
    "chargedtasks", "historycredits", "admissions", "cancelcredits", "remaining", "normalids", "claims", "cancelclaimed",
    "facade", "lifetimepresent", "storagepresent", "blobquotapresent", "paidroots", "refusals", "subsystemclosed",
    "nodeclosed", "sqlclosed", "ownerclosed", "maintenanceclosed", "leaseunavailable", "workersclosed", "timersclosed",
    "schedulersclosed", "pending", "executing", "leases")

  enum Scenario(val wire: String) {
    case Inline extends Scenario("inline")
    case Stored extends Scenario("stored")
    case Refusals extends Scenario("refusals")
    case Ephemeral extends Scenario("ephemeral")
  }
  object Scenario {
    def parse(value: String): Scenario = {
      Scenario.values.find(_.wire == value).getOrElse(throw new IllegalArgumentException("closed C9 scenario required"))
    }
  }
  final case class Identity(pid: Long, start: Instant, uuid: String)
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = {
      apply(key) match {
        case "true" => true
        case "false" => false
        case _ => throw new IllegalArgumentException("closed C9 receipt boolean required")
      }
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = {
      if (apply("id").isEmpty) { Vector.empty } else { Vector(success(JobId.parse(apply("id")))) }
    }
  }
  final case class Child(receipt: Receipt, identity: Identity, exited: Boolean, exitcode: Int)
  final case class Observation(operation: String, parameters: Map[String, String], context: ExecutionContext, uow: UnitOfWork)
  final case class Effects(sqlwrites: Int, entitywrites: Int, entityloads: Int, publications: Int,
    gets: Int, opens: Int, puts: Int, deletes: Int, actions: Int, commits: Int, sources: Int, pairs: Int, ports: Int)

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def validateSelection(alpha: String, count: Int): Unit = {
    require(alpha.matches("[A-Za-z]{1,8}") && count >= 1 && count <= 3, "bounded independent C9 alpha/count required")
  }
  def key(alpha: String): String = s"definition-c9-$alpha"
  def operation(index: Int): String = s"$componentName.command.${Vector("root", "first", "last")(index)}"
  def privateValue(alpha: String, index: Int, replacement: Boolean = false): String = {
    s"private-c9-$alpha-${if (replacement) { "B" } else { "A" }}-$index"
  }
  def parameters(alpha: String, index: Int, replacement: Boolean = false): Map[String, String] = {
    Map("marker" -> privateValue(alpha, index, replacement), "shared" -> s"shared-c9-$alpha",
      "position" -> index.toString)
  }
  def source(alpha: String, count: Int, ephemeral: Boolean = false, replacement: Boolean = false): String = {
    validateSelection(alpha, count)
    val flow = if (count == 1) { "" } else {
      (1 until count).map { index =>
        s"""      - id: step-$index
           |        action: ${operation(index)}
           |        parameters:
           |          marker: ${privateValue(alpha, index, replacement)}
           |          position: "$index"
           |""".stripMargin
      }.mkString("  flow:\n    steps:\n", "", "")
    }
    s"""job:
       |  name: jcl-c9-$alpha
       |  target:
       |    action: ${operation(0)}
       |  parameters:
       |    marker: ${privateValue(alpha, 0, replacement)}
       |    shared: shared-c9-$alpha
       |    position: "0"
       |  submit:
       |    persistence: ${if (ephemeral) { "ephemeral" } else { "persistent" }}
       |  profile:
       |    expectedStatus: succeeded
       |$flow""".stripMargin
  }
  def descriptor(taskId: String, index: Int, expectedParent: Option[String],
    outcome: DurableTransactionOutcome): DurableTaskDescriptor = {
    DurableTaskDescriptor(taskId, expectedParent, DurableTaskKind.Action,
      DurableTaskTarget("action", DurableOperationReference(componentName, None, operation(index), None)),
      DurableTaskRelation(if (index == 0) { DurableTaskRelationKind.Root } else { DurableTaskRelationKind.Child }, expectedParent),
      DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, outcome), None)
  }

  def recipe(count: Int, definitionId: String = "inline-c9", definitionKey: String = "inline-c9"):
      DurableJobKeyedEvidenceFixtures.Recipe = {
    val original = DurableJobOperationResponseQueryFixtures.recipe()
    val definition = original.projection.definitionSnapshot.copy(definitionId = definitionId,
      key = definitionKey, format = Some("yaml"), declaredProfile = Map("expectedStatus" -> "Succeeded"))
    val pendingids = Vector.tabulate(count)(index => original.projection.tasks.head.taskId + s"-$index")
    original.copy(projection = original.projection.copy(tasks = pendingids.zipWithIndex.map { case (id, index) =>
      descriptor(id, index, if (index == 0) { None } else { Some(pendingids(index - 1)) }, DurableTransactionOutcome.Pending)
    },
      definitionSnapshot = definition))
  }

  final class Source(count: Int, supplied: () => DurableJobKeyedEvidenceFixtures.Recipe)
      extends DurableJobKeyedEvidenceSource {
    private val _requests = new ConcurrentLinkedQueue[DurableJobKeyedEvidenceRequest]
    private val _pairs = new ConcurrentLinkedQueue[DurableJobLifecycleWriteEvidence]
    def requests: Vector[DurableJobKeyedEvidenceRequest] = _requests.iterator().asScala.toVector
    def pairs: Vector[DurableJobLifecycleWriteEvidence] = _pairs.iterator().asScala.toVector
    def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse] = {
      _requests.add(request)
      val independent = supplied()
      val original = DurableJobKeyedEvidenceFixtures.pair(request, independent)
      val paired = original.copy(projection = original.projection.copy(
        tasks = original.projection.tasks.map(_.copy(kind = DurableTaskKind.Action))))
      val models = request.request match {
        case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, tasks) => tasks
        case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, tasks) => tasks
        case _ => Vector.empty
      }
      val expectedids = if (models.nonEmpty) { models.map(_.taskId.value) } else { independent.projection.tasks.map(_.taskId) }
      expectedids.size should (be >= 1 and be <= count)
      expectedids.distinct shouldBe expectedids
      paired.projection.tasks.map(_.taskId) shouldBe expectedids
      paired.projection.tasks.zipWithIndex.foreach { case (task, index) =>
        val expectedparent = if (index == 0) { None } else { Some(expectedids(index - 1)) }
        val outcome = if (models.nonEmpty && models(index).transactionOutcome.contains("committed")) {
          DurableTransactionOutcome.Committed
        } else { DurableTransactionOutcome.Pending }
        task shouldBe descriptor(expectedids(index), index, expectedparent, outcome)
      }
      _pairs.add(paired)
      Consequence.success(DurableJobKeyedEvidenceResponse(request.jobid, request.expectedrevision, paired))
    }
  }

  final class Fixture(val root: Path, val count: Int, manual: Boolean) {
    val native: JobDurableRuntimeFixtures.Fixture = new JobDurableRuntimeFixtures.Fixture(root, manual = manual)
    val observations: ConcurrentLinkedQueue[Observation] = new ConcurrentLinkedQueue[Observation]
    val commits: ConcurrentLinkedQueue[UnitOfWork] = new ConcurrentLinkedQueue[UnitOfWork]
    private var _definition_id = "inline-c9"
    private var _definition_key = "inline-c9"
    private var _sql = Option.empty[DurableJobOperationResponseQueryFixtures.NativeSql]
    private var _before_rows = Vector.empty[Option[Record]]
    private var _owner = Option.empty[DurableJobRuntimeOwner]
    private var _closed = false
    val source: Source = new Source(count, () => recipe(count, _definition_id, _definition_key))
    val blob: DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore =
      new DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore(native.selectedBlob)
    val resolver: DurableJobOperationResponseQueryFixtures.Resolver = new DurableJobOperationResponseQueryFixtures.Resolver
    native.selectedEvidence = source
    native.selectedBlob = blob
    native.selectedCallerAccessResolver = Some(resolver)
    val control: Component = {
      val component = JobControlComponent.Factory.createPrimary(ComponentCreate(native.subsystem, ComponentOrigin.Builtin))
      val installed = new ComponentFactory().bootstrap(component)
      native.subsystem.add(installed)
      installed
    }
    val target: Component = {
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(name = "command",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
          TargetOperation("root", observations, commits), TargetOperation("first", observations, commits),
          TargetOperation("last", observations, commits)))))))
      val id = ComponentId(componentName)
      val component = new Component() { override def displayName: String = componentName }
      val core = Component.Core.create(id.name, id, ComponentInstanceId.default(id), protocol, native.engine)
      val installed = new ComponentFactory().bootstrap(component.initialize(ComponentInit(native.subsystem, core, ComponentOrigin.Builtin)))
      native.subsystem.add(installed)
      installed
    }
    def owner: DurableJobRuntimeOwner = _owner.get
    def sql: DurableJobOperationResponseQueryFixtures.NativeSql = _sql.get
    def beforeRows: Vector[Option[Record]] = _before_rows
    def observationVector: Vector[Observation] = observations.iterator().asScala.toVector
    def selectDefinition(id: String, definitionKey: String): Unit = { _definition_id = id; _definition_key = definitionKey }

    def start(ids: Vector[JobId] = Vector.empty, definitionKey: Option[String] = None): Unit = {
      (control.jobEngine eq native.engine) shouldBe true
      (target.jobEngine eq native.engine) shouldBe true
      native.startup.entries = ids.zipWithIndex.map { case (id, index) => JobDurableRuntimeFixtures.candidate(id, index.toLong) }
      native.startup.before = () => {
        val collection = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
        val managed = success(native.context.dataStoreSpace.dataStore(collection)).asInstanceOf[SqlDataStore]
        _sql = Some(new DurableJobOperationResponseQueryFixtures.NativeSql(managed))
        native.context.dataStoreSpace.useDataStore(sql)
        _install_definition_collection()
        val definitionids = definitionKey.toVector.flatMap(selected => currentDefinition(selected).map(_.id))
        _before_rows = rows(ids, definitionids)
      }
      success(native.start())
      _owner = native.engine._durable_runtime_owner_option
      owner.health.state shouldBe JobDurableOperationalState.Ready
      native.startup.calls.get() shouldBe 1
    }

    private def _install_definition_collection(): Unit = {
      val declared = JobControlComponent.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
        .find(_.collectionId == JobEntityCollections.JobDefinition).get
      val binding = success(EntityRevisionBinding.resolve(EntityRevisionModelMetadata(declared.revisionModelKind.get, None),
        declared.revisionRepresentation))
      val persistent = JobDefinitionEntity.entityPersistent
      val realm = new EntityRealm[JobDefinitionEntity](declared.entityName,
        EntityLoader.fromEntityStore(native.entity)(using persistent, native.context),
        new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty)))
      val descriptor = EntityDescriptor(declared.collectionId,
        EntityRuntimePlan[JobDefinitionEntity](entityName = declared.entityName, memoryPolicy = EntityMemoryPolicy.StoreOnly,
          workingSet = None, partitionStrategy = declared.partitionStrategy, maxPartitions = declared.maxPartitions,
          maxEntitiesPerPartition = declared.maxEntitiesPerPartition,
          concurrencyPolicy = declared.concurrencyPolicy.getOrElse(EntityConcurrencyPolicy.default)), persistent, binding)
      native.context.entitySpace.registerEntity(declared.entityName, new EntityCollection(descriptor, EntityStorage(realm)))
    }

    def caller: ExecutionContext = DurableJobOperationResponseQueryFixtures.caller(native.context)
    def invoke(operationName: String, arguments: List[Argument]): Consequence[OperationResponse] = {
      val selector = s"${JobControlComponent.componentId.name}.job.$operationName"
      native.subsystem.resolver.resolve(selector) match {
        case OperationResolver.ResolutionResult.Resolved(_, component, service, operation) =>
          val request = Request.of(component = component, service = service, operation = operation, arguments = arguments)
          val base = control.logic.executionContext()
          val original = base.runtime
          val runtime = new RuntimeContext(core = original.core.copy(
            datastore = Some(DataStoreContext(native.context.dataStoreSpace)),
            entitystore = Some(EntityStoreContext(native.context.entityStoreSpace)),
            entityspace = Some(EntitySpaceContext(native.context.entitySpace))),
            unitofworksupplier = () => original.unitOfWork, unitofworkinterpreterfn = original.unitOfWorkInterpreter,
            commitaction = uow => { uow.commit(); () }, abortaction = uow => { uow.abort(); () },
            disposeaction = uow => { uow.dispose(); () }, token = original.toToken, context = original.context,
            operationMode = original.operationMode, transitionValidationHook = original.transitionValidationHook,
            entityCreateDefaultsPolicy = original.entityCreateDefaultsPolicy)
          val context = ExecutionContext.withSecurityContext(ExecutionContext.withRuntimeContext(base, runtime), caller.security)
          control.logic.makeOperationRequest(request).flatMap {
            case action: org.goldenport.cncf.action.Action => owner._execute_c(control.logic.executeAction(action, context))
            case _ => Consequence.operationInvalid("JCL protocol must resolve a real Action")
          }
        case _ => throw new IllegalStateException("installed public JobControl route required")
      }
    }
    def currentDefinition(definitionKey: String): Option[JobDefinitionEntity] = {
      import org.goldenport.cncf.directive.Query
      import org.goldenport.cncf.entity.{EntityQuery, EntitySearchScope}
      success(native.subsystem._with_managed_datastore_lease_c { _ =>
        native.entity.search[JobDefinitionEntity](EntityQuery(collection = JobEntityCollections.JobDefinition,
          query = Query.plan(Record.empty, where = Query.Eq("key", definitionKey)), scope = EntitySearchScope.Store))(
          using JobDefinitionEntity.entityPersistent, native.context).flatMap { result =>
          result.data.size should (be >= 0 and be <= 1)
          result.data.headOption match {
            case Some(found) =>
              found.key shouldBe definitionKey
              native.entity.loadDetached[JobDefinitionEntity](found.id)(using JobDefinitionEntity.entityPersistent, native.context)
                .map(_.map { carrier => carrier.revision.value should be > 0L; carrier.entity })
            case None => Consequence.success(None)
          }
        }
      })
    }
    def row(id: EntityId): Option[Record] = {
      success(native.subsystem._with_managed_datastore_lease_c { _ =>
        sql.load(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))(using native.context)
      })
    }
    def rows(ids: Vector[JobId], definitions: Vector[EntityId] = Vector.empty): Vector[Option[Record]] = {
      (ids.flatMap(id => Vector(success(DurableJobStoreEntity.entityId(id.value)), JobEntity.entityId(id))) ++ definitions ++
        Vector(DurableJobBackupFixtures.storageId, DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)).map(row)
    }
    def effects: Effects = Effects(sql.writes.get(), native.entity.writes.get(), native.entity.loads.get(), blob.issued.size,
      blob.gets.get(), blob.opens.get(), blob.puts.get(), blob.deletes.get(), observations.size(), commits.size(),
      source.requests.size, source.pairs.size, native.port.calls.get())

    def close(primary: Option[Throwable]): Unit = {
      if (!_closed) {
        _closed = true
        var failure = primary
        def _capture_(body: => Unit): Unit = {
          try { body } catch {
            case exception: Throwable =>
              failure match {
                case Some(existing) => existing.addSuppressed(exception)
                case None => failure = Some(exception)
              }
          }
        }
        _capture_ { success(native.subsystem.shutdownC()); () }
        _capture_ { success(native.node.shutdownC()); () }
        _capture_(native.engine._timer.shutdown())
        _capture_ {
          native.engine._worker_pool.awaitTermination(5000L, TimeUnit.MILLISECONDS) shouldBe true
          native.engine._worker_pool.isTerminated shouldBe true
          native.engine._scheduler.foreach { scheduler =>
            scheduler.awaitTermination(5000L, TimeUnit.MILLISECONDS) shouldBe true
            scheduler.isTerminated shouldBe true
          }
          native.node.state shouldBe SystemNode.State.Stopped
          native.node.activeLeaseCount shouldBe 0
          native.provider.get.managedResourceOption.get.isOpen shouldBe false
          owner._backup_closed shouldBe true
          owner._scheduler.status.closed shouldBe true
          owner._execute_c(Consequence.unit).isSuccess shouldBe false
          native.engine.resourceSnapshot.pending shouldBe 0
          native.engine.resourceSnapshot.executing shouldBe 0
          native.engine._work_queue.isEmpty shouldBe true
          if (manual) {
            native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime.testControl.get.pendingTimerCount shouldBe 0
          }
        }
        if (primary.isEmpty) { failure.foreach(throw _) }
      }
    }
    def closureFacts: Map[String, String] = {
      Map(
      "subsystemclosed" -> native.subsystem._shutdown_result_option.exists(_.isSuccess).toString,
      "nodeclosed" -> (native.node.state == SystemNode.State.Stopped).toString,
      "sqlclosed" -> native.provider.exists(value => !value.managedResourceOption.get.isOpen).toString,
      "ownerclosed" -> _owner.exists(_._backup_closed).toString,
      "maintenanceclosed" -> _owner.exists(_._scheduler.status.closed).toString,
      "leaseunavailable" -> _owner.exists(value => !value._execute_c(Consequence.unit).isSuccess).toString,
      "workersclosed" -> native.engine._worker_pool.isTerminated.toString, "timersclosed" -> _closed.toString,
      "schedulersclosed" -> native.engine._scheduler.forall(_.isTerminated).toString,
      "pending" -> native.engine.resourceSnapshot.pending.toString, "executing" -> native.engine.resourceSnapshot.executing.toString,
      "leases" -> native.node.activeLeaseCount.toString)
    }
  }

  private final case class TargetOperation(opname: String, observations: ConcurrentLinkedQueue[Observation],
    commits: ConcurrentLinkedQueue[UnitOfWork]) extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = opname, request = spec.RequestDefinition(), response = spec.ResponseDefinition.void)
    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(TargetAction(req, opname, observations, commits))
  }
  private final case class TargetAction(request: Request, opname: String, observations: ConcurrentLinkedQueue[Observation],
    commits: ConcurrentLinkedQueue[UnitOfWork]) extends CommandAction {
    override def createCall(core: ActionCall.Core): ActionCall = TargetCall(core, opname, observations, commits)
  }
  private final case class TargetCall(core: ActionCall.Core, opname: String, observations: ConcurrentLinkedQueue[Observation],
    commits: ConcurrentLinkedQueue[UnitOfWork]) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val context = core.executionContext
      val parameters = core.action.request.arguments.map(argument => argument.name -> argument.value.toString).toMap
      val uow = context.unitOfWork
      observations.add(Observation(opname, parameters, context, uow))
      uow.stagePostCommit { commits.add(uow); () }
      Consequence.success(OperationResponse.Scalar(parameters("marker")))
    }
  }

  def identity(): Identity = {
    val process = ProcessHandle.current()
    Identity(process.pid(), process.info().startInstant().orElseThrow(() =>
      new IllegalStateException("actual C9 process start instant required")), _jvm_uuid)
  }
  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe _receipt_keys
    val bytes = facts.toVector.sortBy(_._1).map { case (key, value) =>
      key + "=" + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
    bytes.length should be <= 16384
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }
  def readReceipt(path: Path): Receipt = {
    require(Files.size(path) > 0L && Files.size(path) <= 16384L, "bounded C9 receipt required")
    val entries = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "closed C9 entry required")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = entries.toMap
    require(facts.size == entries.size && facts.keySet == _receipt_keys, "closed unique C9 receipt fields required")
    val receipt = Receipt(facts)
    UUID.fromString(receipt.identity.uuid)
    receipt
  }
  final class Case(val root: Path, val scenario: Scenario, val alpha: String, val count: Int) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    def writer(): Child = _launch("writer", Vector.empty)
    def reader(ids: Vector[JobId], foreign: Boolean = false): Child = {
      _launch(if (foreign) { "foreign-reader" } else { "reader" }, if (scenario == Scenario.Ephemeral) { Vector.empty } else { ids })
    }
    private def _launch(mode: String, ids: Vector[JobId]): Child = {
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute Java executable required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val args = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobJclCrossProcessProbe", mode, native.toString, receiptpath.toString,
        scenario.wire, alpha, count.toString) ++ ids.map(_.value)
      val process = new ProcessBuilder(args*).directory(root.toFile)
        .redirectOutput(logs.resolve(s"$mode.stdout.log").toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() => new IllegalStateException("actual child start required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) { throw new IllegalStateException(s"C9 $mode timeout; logs at $logs") }
        val exit = process.exitValue()
        if (exit != 0) {
          val input = Files.newInputStream(stderr)
          val excerpt = try { new String(input.readNBytes(2048), StandardCharsets.UTF_8) } finally { input.close() }
          throw new IllegalStateException(s"C9 $mode exit $exit; logs at $logs; $excerpt")
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
        if (mode != "writer") { receipt.ids shouldBe ids }
        Child(receipt, receipt.identity, !process.isAlive, exit)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS)) { throw new IllegalStateException("owned C9 child did not terminate") }
          }
        }
      }
    }
  }
  def withCase[A](scenario: Scenario, alpha: String, count: Int)(body: Case => A): A = {
    validateSelection(alpha, count)
    require(scenario == Scenario.Inline || count == 1, "stored/refused/ephemeral C9 selector requires one task")
    val parent = Files.createDirectories(Paths.get("target/job-jcl-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try { val result = body(new Case(root, scenario, alpha, count)); successful = true; result }
    finally { if (successful) { JobDurableRuntimeFixtures.remove(root) } }
  }
}
