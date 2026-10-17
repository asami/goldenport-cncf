package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.composite.{CompositeQueryEngine, CompositeQueryV2Branch, CompositeQueryV2Cancellation,
  CompositeQueryV2Fixture, CompositeQueryV2Persistence, CompositeQueryV2Policy, CompositeQueryV2Request,
  CompositeQueryV2Response, CompositeQueryV2Target, CompositeQueryV2Targets}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, Principal,
  PrincipalId, ScopeContext, SessionContext, TraceId}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.operation.evaluation.OperationEvaluationOperationIdentity
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.EntityId

/*
 * Original native ownership, independent Query target and sequential owned JVMs.
 * Receipts carry observations only; every domain assertion stays in its JVM.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object CompositeQueryNativeCrossProcessFixtures extends Matchers {
  private val _jvm_uuid = UUID.randomUUID().toString
  private val _receipt_limit = 16384
  private val _common_keys = Set("mode", "persistence", "alpha", "count", "idcount", "root", "sqlite",
    "pid", "start", "uuid", "ready", "domainChecked", "contextChecked", "descriptorChecked",
    "privateAbsent", "nativeUnchanged", "zeroEffects", "rawabsent", "managementunavailable", "foreignrefused",
    "queries", "commands", "observations", "sources", "pairs", "ports", "resolvers", "sqlwrites",
    "entitywrites", "entityloads", "metadata", "gets", "opens", "closes", "puts", "deletes", "publications",
    "candidates", "registered", "runtimejobs", "terminaljobs", "drained", "queue", "paidroots",
    "lifetimepresent", "storagepresent", "blobquotapresent", "callerclosed", "targetclosed", "targetnodeclosed",
    "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed", "leaseunavailable", "maintenanceclosed",
    "enginesclosed", "timersclosed", "workersclosed", "schedulersclosed", "pending", "executing", "leases")
  private val _job_keys = Set("id", "native", "revision", "providerrevision", "status", "result",
    "liveVoid", "facade", "chargedtasks", "historycredits", "admissions", "cancelcredits", "remaining",
    "normalids", "claims", "cancelclaimed")

  enum Persistence(val wire: String) {
    case Ephemeral extends Persistence("ephemeral")
    case PersistentDiagnostics extends Persistence("persistent-diagnostics")
  }

  object Persistence {
    def parse(value: String): Persistence = value match {
      case "ephemeral" => Persistence.Ephemeral
      case "persistent-diagnostics" => Persistence.PersistentDiagnostics
      case _ => throw new IllegalArgumentException("closed C8 persistence required")
    }
  }

  final case class Identity(pid: Long, start: Instant, uuid: String)
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("closed C8 boolean required")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = Vector.tabulate(number("idcount").toInt)(index =>
      success(JobId.parse(apply(s"job.$index.id"))))
  }
  final case class Child(receipt: Receipt, identity: Identity, exited: Boolean, exitcode: Int)
  final case class Effects(sqlwrites: Int, entitywrites: Int, entityloads: Int, quotaloads: Int,
    metadata: Int, gets: Int, opens: Int, closes: Int, puts: Int, deletes: Int, publications: Int,
    sources: Int, pairs: Int, ports: Int, resolvers: Int, queries: Int, commands: Int, observations: Int)

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)

  def descriptor(taskId: String, outcome: DurableTransactionOutcome): DurableTaskDescriptor = {
    DurableTaskDescriptor(taskId, None, DurableTaskKind.Operation,
      DurableTaskTarget("operation", DurableOperationReference("composite-query-v2", None, "composite-query-v2", None)),
      DurableTaskRelation(DurableTaskRelationKind.Root, None),
      DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, outcome), None)
  }

  def recipe(): DurableJobKeyedEvidenceFixtures.Recipe = {
    val original = DurableJobOperationResponseQueryFixtures.recipe()
    val definition = DurableJobKeyedEvidenceFixtures.recipe("cq2-diagnostics").projection.definitionSnapshot
    original.copy(projection = original.projection.copy(
      tasks = original.projection.tasks.map(task => descriptor(task.taskId, DurableTransactionOutcome.Pending)),
      inputs = Vector.empty, result = DurableResultOutcome.Pending, diagnostics = Vector.empty,
      calltreeReference = None, definitionSnapshot = definition))
  }

  def branchId(alpha: String, index: Int): String = s"query-branch-c8-$alpha-$index"
  def privateValue(alpha: String, index: Int): String = s"query-private-c8-$alpha-$index"

  def validateSelection(alpha: String, count: Int): Unit = {
    require(alpha.matches("[A-Za-z]{1,8}") && count >= 1 && count <= 2, "bounded independent C8 alpha/count required")
  }

  final class Fixture(val root: Path, manual: Boolean) {
    val native: JobDurableRuntimeFixtures.Fixture = new JobDurableRuntimeFixtures.Fixture(root, manual = manual)
    val targetFixture: CompositeQueryV2Fixture = new CompositeQueryV2Fixture(marker = "cq2-diagnostics", ownResourceScope = true)
    val targetEngine: InMemoryJobEngine = targetFixture.subsystem.jobEngine.asInstanceOf[InMemoryJobEngine]
    val targets: CompositeQueryV2Targets = success(CompositeQueryV2Targets.create(Vector("target-c8" -> targetFixture.subsystem)))
    val installedScope: ScopeContext = targetFixture.subsystem.components.find(
      _.componentId.name == targetFixture.componentName).get.scopeContext
    val installedContext: ExecutionContext = targetFixture.subsystem.components.find(
      _.componentId.name == targetFixture.componentName).get.logic.executionContext()
    val source: DurableJobKeyedEvidenceFixtures.Source = new DurableJobKeyedEvidenceFixtures.Source(_ => recipe())
    val blob: DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore =
      new DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore(native.selectedBlob)
    val resolver: DurableJobOperationResponseQueryFixtures.Resolver = new DurableJobOperationResponseQueryFixtures.Resolver
    private var _sql = Option.empty[DurableJobOperationResponseQueryFixtures.NativeSql]
    private var _before_rows = Vector.empty[Option[Record]]
    private var _actor = Option.empty[Thread]
    private var _cancellation = Option.empty[CompositeQueryV2Cancellation]
    private var _owner = Option.empty[DurableJobRuntimeOwner]
    private var _closed = false
    native.selectedEvidence = source
    native.selectedBlob = blob
    native.selectedCallerAccessResolver = Some(resolver)

    def sql: DurableJobOperationResponseQueryFixtures.NativeSql = _sql.get
    def beforeRows: Vector[Option[Record]] = _before_rows
    def actorAlive: Boolean = _actor.exists(_.isAlive)
    def owner: DurableJobRuntimeOwner = _owner.get

    def start(ids: Vector[JobId] = Vector.empty): Unit = {
      (native.subsystem.jobEngine eq native.engine) shouldBe true
      (targetFixture.jobEngine eq native.engine) shouldBe false
      (targetEngine eq native.engine) shouldBe false
      (targetEngine eq targetFixture.jobEngine) shouldBe false
      native.startup.entries = ids.zipWithIndex.map { case (id, index) =>
        JobDurableRuntimeFixtures.candidate(id, index.toLong)
      }
      native.startup.before = () => {
        val collection = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
        val managed = success(native.context.dataStoreSpace.dataStore(collection)).asInstanceOf[SqlDataStore]
        val measured = new DurableJobOperationResponseQueryFixtures.NativeSql(managed)
        _sql = Some(measured)
        native.context.dataStoreSpace.useDataStore(measured)
        _before_rows = rows(ids)
      }
      success(native.start())
      _owner = native.engine._durable_runtime_owner_option
      _owner.nonEmpty shouldBe true
      owner.health.state shouldBe JobDurableOperationalState.Ready
      native.startup.calls.get() shouldBe 1
      assertUnusedTarget()
    }

    def caller: ExecutionContext = {
      val original = DurableJobOperationResponseQueryFixtures.caller(native.context)
      val secured = ExecutionContext.withSecurityContext(original, original.security.copy(
        principal = new Principal {
          def id: PrincipalId = PrincipalId("subject-a")
          def attributes: Map[String, String] = original.security.principal.attributes + ("tenant" -> "tenant-a")
        }, session = Some(SessionContext(sessionId = Some("query-session-c8"), tokenId = Some("query-token-c8")))))
      val traced = ExecutionContext.withObservabilityContext(secured,
        ObservabilityContext(TraceId("c8", "trace"), None, Some(CorrelationId("c8", "correlation"))))
      success(ExecutionContext.prepareOperationEvaluation(traced,
        success(OperationEvaluationOperationIdentity.createC("fixture", "sample", "caller"))))
    }

    def invoke(alpha: String, count: Int, persistence: Persistence,
      context: ExecutionContext): CompositeQueryV2Response = {
      validateSelection(alpha, count)
      require(!manual && _actor.isEmpty, "one original realtime public-facade invocation required")
      val branches = Vector.tabulate(count) { index =>
        val branchid = branchId(alpha, index)
        val request = targetFixture.request("payload").copy(properties = List(
          Property("fixture.branch", branchid, None), Property("password", privateValue(alpha, index), None)))
        CompositeQueryV2Branch(branchid, request, target = CompositeQueryV2Target.Subsystem("target-c8"))
      }
      val defaultpolicy = CompositeQueryV2Policy(maxParallelism = count, timeoutMillis = 5000L)
      defaultpolicy.persistence shouldBe CompositeQueryV2Persistence.Ephemeral
      val policy = if (persistence == Persistence.Ephemeral) defaultpolicy else
        defaultpolicy.copy(persistence = CompositeQueryV2Persistence.PersistentDiagnostics)
      val request = CompositeQueryV2Request(branches, policy)
      val cancellation = CompositeQueryV2Cancellation.fresh
      _cancellation = Some(cancellation)
      val result = new AtomicReference[Either[Throwable, CompositeQueryV2Response]]()
      val actor = new Thread(() => {
        try {
          (native.subsystem.jobEngine eq native.engine) shouldBe true
          result.set(Right(success(owner._execute_c {
            new CompositeQueryEngine(native.subsystem).executeV2Blocking(request, targets, cancellation)(using context)
          })))
        } catch {
          case exception: Throwable => result.set(Left(exception))
        }
      }, "composite-query-native-c8-caller")
      _actor = Some(actor)
      actor.start()
      actor.join(10000L)
      actor.isAlive shouldBe false
      Option(result.get()).getOrElse(throw new IllegalStateException("C8 caller returned no result")) match {
        case Right(response) => response
        case Left(exception) => throw exception
      }
    }

    def row(id: EntityId): Option[Record] = {
      success(native.subsystem._with_managed_datastore_lease_c { _ =>
        sql.load(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))(using native.context)
      })
    }
    def rows(ids: Vector[JobId]): Vector[Option[Record]] = {
      (ids.map(id => success(DurableJobStoreEntity.entityId(id.value))) ++ Vector(
        DurableJobBackupFixtures.storageId, DurableJobBackupFixtures.lifetimeId, DurableJobBackupFixtures.blobQuotaId)).map(row)
    }
    def effects: Effects = Effects(sql.writes.get(), native.entity.writes.get(), native.entity.loads.get(),
      native.entity.quotaloads.get(), sql.metadata.get(), blob.gets.get(), blob.opens.get(), blob.closes.get(),
      blob.puts.get(), blob.deletes.get(), blob.issued.size, source.requests.size, source.pairs.size,
      native.port.calls.get(), resolver.contexts.size, targetFixture.state.queryCalls.size(),
      targetFixture.state.commandCalls.size(), targetFixture.state.observations.size())

    def assertUnusedTarget(): Unit = {
      Vector(targetFixture.jobEngine, targetEngine).foreach { engine =>
        engine.listJobs(persistentOnly = false) shouldBe Vector.empty
        engine._work_queue.isEmpty shouldBe true
        engine.resourceSnapshot.pending shouldBe 0
        engine.resourceSnapshot.executing shouldBe 0
      }
    }

    def close(primary: Option[Throwable]): Unit = {
      if (!_closed) {
        _closed = true
        var failure = primary
        def _capture_(body: => Unit): Unit = {
          try body catch {
            case exception: Throwable =>
              failure match {
                case Some(existing) => existing.addSuppressed(exception)
                case None => failure = Some(exception)
              }
          }
        }
        _capture_ {
          _cancellation.foreach(_.cancel())
          _actor.foreach { actor =>
            if (actor.isAlive) {
              actor.interrupt()
              actor.join(5000L)
            }
            actor.isAlive shouldBe false
          }
        }
        _capture_(targetFixture.jobEngine.shutdown())
        _capture_(Subsystem.shutdownOwned(targetFixture.subsystem))
        _capture_ { success(native.subsystem.shutdownC()); () }
        _capture_ { success(native.node.shutdownC()); () }
        val engines = Vector(targetFixture.jobEngine, targetEngine, native.engine)
        engines.foreach { engine =>
          _capture_(engine._timer.shutdown())
          _capture_ {
            engine._worker_pool.awaitTermination(5000L, TimeUnit.MILLISECONDS) shouldBe true
            engine._worker_pool.isTerminated shouldBe true
          }
          engine._scheduler.foreach { scheduler =>
            _capture_ {
              scheduler.awaitTermination(5000L, TimeUnit.MILLISECONDS) shouldBe true
              scheduler.isTerminated shouldBe true
            }
          }
          _capture_ {
            engine.resourceSnapshot.pending shouldBe 0
            engine.resourceSnapshot.executing shouldBe 0
            engine._work_queue.isEmpty shouldBe true
          }
        }
        _capture_ {
          native.node.state shouldBe SystemNode.State.Stopped
          native.node.activeLeaseCount shouldBe 0
          targetFixture.subsystem.systemNode.state shouldBe SystemNode.State.Stopped
          targetFixture.subsystem.systemNode.activeLeaseCount shouldBe 0
          native.subsystem._shutdown_result_option.exists(_.isSuccess) shouldBe true
          targetFixture.subsystem._shutdown_result_option.exists(_.isSuccess) shouldBe true
          native.provider.nonEmpty shouldBe true
          native.provider.foreach(value => value.managedResourceOption.get.isOpen shouldBe false)
          _owner.nonEmpty shouldBe true
          _owner.foreach { value =>
            value._backup_closed shouldBe true
            value._scheduler.status.closed shouldBe true
            value._scheduler.status.started shouldBe false
            value._execute_c(Consequence.unit).isSuccess shouldBe false
          }
          if (manual) {
            native.subsystem.globalRuntimeContext.executionProfileRuntime.schedulingRuntime
              .testControl.get.pendingTimerCount shouldBe 0
          }
          actorAlive shouldBe false
        }
        if (primary.isEmpty) failure.foreach(throw _)
      }
    }

    def closureFacts: Map[String, String] = {
      val engines = Vector(targetFixture.jobEngine, targetEngine, native.engine)
      val stopped = engines.forall(engine => engine._admission_closed && engine._force_cancel_requested)
      Map("callerclosed" -> (!actorAlive).toString,
        "targetclosed" -> targetFixture.subsystem._shutdown_result_option.exists(_.isSuccess).toString,
        "targetnodeclosed" -> (targetFixture.subsystem.systemNode.state == SystemNode.State.Stopped).toString,
        "subsystemclosed" -> native.subsystem._shutdown_result_option.exists(_.isSuccess).toString,
        "nodeclosed" -> (native.node.state == SystemNode.State.Stopped).toString,
        "sqlclosed" -> native.provider.exists(value => !value.managedResourceOption.get.isOpen).toString,
        "ownerclosed" -> _owner.exists(_._backup_closed).toString,
        "leaseunavailable" -> _owner.exists(value => !value._execute_c(Consequence.unit).isSuccess).toString,
        "maintenanceclosed" -> _owner.exists(_._scheduler.status.closed).toString,
        "enginesclosed" -> stopped.toString, "timersclosed" -> _closed.toString,
        "workersclosed" -> engines.forall(_._worker_pool.isTerminated).toString,
        "schedulersclosed" -> engines.forall(_._scheduler.forall(_.isTerminated)).toString,
        "pending" -> engines.map(_.resourceSnapshot.pending).sum.toString,
        "executing" -> engines.map(_.resourceSnapshot.executing).sum.toString,
        "leases" -> (native.node.activeLeaseCount + targetFixture.subsystem.systemNode.activeLeaseCount).toString)
    }
  }

  def identity(): Identity = {
    val process = ProcessHandle.current()
    Identity(process.pid(), process.info().startInstant().orElseThrow(() =>
      new IllegalStateException("actual C8 process start instant required")), _jvm_uuid)
  }

  def receiptKeys(count: Int): Set[String] = {
    require(count >= 0 && count <= 2, "bounded C8 receipt IDs required")
    _common_keys ++ (0 until count).flatMap(index => _job_keys.map(key => s"job.$index.$key"))
  }

  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe receiptKeys(facts("idcount").toInt)
    val text = facts.toVector.sortBy(_._1).map { case (key, value) =>
      key + "=" + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }.mkString("", "\n", "\n")
    val bytes = text.getBytes(StandardCharsets.UTF_8)
    bytes.length should be <= _receipt_limit
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }

  def readReceipt(path: Path): Receipt = {
    val size = Files.size(path)
    require(size > 0L && size <= _receipt_limit, "bounded C8 observation receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "C8 receipt entry required")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    val count = facts.getOrElse("idcount", throw new IllegalArgumentException("C8 receipt ID count required")).toInt
    require(facts.size == pairs.size && facts.keySet == receiptKeys(count), "unique closed C8 receipt keys required")
    val receipt = Receipt(facts)
    validateSelection(receipt("alpha"), receipt.number("count").toInt)
    Persistence.parse(receipt("persistence"))
    require(receipt.ids.distinct.size == count, "distinct opaque C8 IDs required")
    UUID.fromString(receipt.identity.uuid)
    receipt
  }

  final class Case(val root: Path, val alpha: String, val count: Int, val persistence: Persistence) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    private var _writer_exited = false

    def writer(): Child = {
      require(!_writer_exited, "one genuine C8 writer required")
      val child = _launch("writer", Vector.empty)
      child.exited shouldBe true
      Vector("ownerclosed", "sqlclosed", "callerclosed", "workersclosed").foreach(key => child.receipt.flag(key) shouldBe true)
      _writer_exited = true
      child
    }

    def reader(ids: Vector[JobId], foreign: Boolean = false): Child = {
      require(_writer_exited, "C8 writer must close and exit before independent reader launch")
      require(ids.size == (if (persistence == Persistence.Ephemeral) 0 else count), "closed independent reader candidates required")
      require(!foreign || persistence == Persistence.PersistentDiagnostics, "foreign reader requires genuine persistent candidates")
      _launch(if (foreign) "foreign-reader" else "reader", ids)
    }

    private def _launch(mode: String, ids: Vector[JobId]): Child = {
      validateSelection(alpha, count)
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "actual absolute java.home/bin/java required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.CompositeQueryNativeCrossProcessProbe", mode, native.toString,
        receiptpath.toString, persistence.wire, alpha, count.toString) ++ ids.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(logs.resolve(s"$mode.stdout.log").toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual C8 child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) {
          throw new IllegalStateException(s"C8 $mode timed out; retained logs $logs; ${_excerpt(stderr)}")
        }
        val exit = process.exitValue()
        if (exit != 0) {
          throw new IllegalStateException(s"C8 $mode exit $exit; retained logs $logs; ${_excerpt(stderr)}")
        }
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("persistence") shouldBe persistence.wire
        receipt("alpha") shouldBe alpha
        receipt.number("count") shouldBe count.toLong
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        if (mode != "writer") receipt.ids shouldBe ids
        Child(receipt, receipt.identity, !process.isAlive, exit)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
              throw new IllegalStateException("owned C8 child failed to terminate")
            }
          }
        }
      }
    }
  }

  def withCase[A](alpha: String, count: Int, persistence: Persistence)(body: Case => A): A = {
    validateSelection(alpha, count)
    val parent = Files.createDirectories(Paths.get("target/composite-query-native-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root, alpha, count, persistence))
      successful = true
      result
    } finally {
      if (successful) JobDurableRuntimeFixtures.remove(root)
    }
  }

  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8) finally input.close()
  }
}
