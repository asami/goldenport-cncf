package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.IteratorHasAsScala
import org.goldenport.Consequence
import org.goldenport.cncf.blob.{Blob, BlobRepository}
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, SubjectKind}
import org.goldenport.cncf.datastore.{DataStore, EntityCompareAndSetMutationPlan, EntityDirectMutationPlan,
  EntityMutationProviderCapabilities, EntityMutationProviderResult, EntityVersionedMutationPlan,
  EntityVersionedMutationResult, QueryDirective, SearchResult}
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Field, Record}
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.matchers.should.Matchers

/*
 * Original native Task publication, raw current callers and owned sequential JVMs.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponseQueryFixtures extends Matchers {
  import BlobRepository.given

  val access: DurableJobRecordAccess = JobDurableRuntimeFixtures.access
  val reviewFields: Vector[(String, String)] = Vector("reviewId" -> "review-1", "reportId" -> "report-1",
    "reportDigest" -> "sha256:abc", "reportDocument" -> "full report", "runDocument" -> "full run",
    "attestationDocument" -> "full attestation")
  val review: OperationResponse = OperationResponse.RecordResponse(Record(reviewFields.map {
    case (key, value) => Field(key, Field.Value.Single(value))
  }))
  val reviewWire: String = """{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":{"kind":"record","fields":[{"key":"reviewId","value":{"type":"string","value":"review-1"}},{"key":"reportId","value":{"type":"string","value":"report-1"}},{"key":"reportDigest","value":{"type":"string","value":"sha256:abc"}},{"key":"reportDocument","value":{"type":"string","value":"full report"}},{"key":"runDocument","value":{"type":"string","value":"full run"}},{"key":"attestationDocument","value":{"type":"string","value":"full attestation"}}]}}"""
  private val _jvm_uuid = UUID.randomUUID().toString
  private val _receipt_limit = 16384
  private val _receipt_keys = Set("mode", "root", "sqlite", "pid", "start", "uuid", "id", "revision",
    "providerrevision", "reference", "status", "quotastate", "chargedtasks", "historycredits", "admissions",
    "cancelcredits", "remaining", "normalids", "claims", "cancelclaimed", "tasks", "getters", "canonical",
    "sources", "ports", "writes", "entitywrites", "metadata", "gets", "opens", "closes", "puts", "deletes",
    "resolvers", "drained", "rawabsent", "managementunavailable", "rowsunchanged", "ready", "refusal",
    "domainChecked", "subsystemclosed", "ownerclosed", "nodeclosed", "sqlclosed", "leases")

  def success[A](result: Consequence[A]): A = JobDurableRuntimeFixtures.success(result)
  def message[A](result: Consequence[A]): Option[String] =
    JobDurableRuntimeFixtures.failureConclusion(result).observation.getEffectiveMessage
  def recipe(): DurableJobKeyedEvidenceFixtures.Recipe = {
    val original = DurableJobKeyedEvidenceFixtures.recipe("query")
    original.copy(projection = original.projection.copy(authorization = original.projection.authorization.copy(
      submitter = original.projection.authorization.submitter.copy(kind = SubjectKind.User.toString))))
  }
  def caller(base: ExecutionContext, subject: String = "subject-a", tenant: String = "tenant-a",
    scopes: Option[String] = Some("job.read"), overview: Boolean = false): ExecutionContext = {
    val principalattributes = Map("tenantId" -> tenant, "authenticated" -> "true") ++ scopes.map("scopes" -> _)
    ExecutionContext.withSecurityContext(base, base.security.copy(principal = new Principal {
      def id: PrincipalId = PrincipalId(subject)
      def attributes: Map[String, String] = principalattributes
    }, subjectKind = SubjectKind.User, session = None,
      capabilities = if (overview) Set(Capability("job_view")) else Set.empty))
  }

  final class Resolver extends DurableJobCallerAccessResolver {
    private val _contexts = new ConcurrentLinkedQueue[ExecutionContext]()
    var before: ExecutionContext => Unit = _ => ()
    var selected: ExecutionContext => Consequence[DurableJobRecordAccess] = context => {
      val principal = context.security.principal
      val attributes = principal.attributes
      Consequence.success(DurableJobRecordAccess(attributes.getOrElse("tenantId", null), principal.id.value,
        attributes.get("scopes").map(_.split(" ", -1).toSet).getOrElse(Set.empty)))
    }
    def contexts: Vector[ExecutionContext] = _contexts.iterator().asScala.toVector
    def resolve(context: ExecutionContext): Consequence[DurableJobRecordAccess] = {
      _contexts.add(context); before(context); selected(context)
    }
  }

  final class NativeSql(delegate: SqlDataStore) extends SqlDataStore(SqliteDialectDriver, null) {
    val writes = new AtomicInteger
    val metadata = new AtomicInteger
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = delegate.entityMutationProviderCapabilities
    override def isAccept(cid: DataStore.CollectionId): Boolean = delegate.isAccept(cid)
    override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)
        (using ctx: ExecutionContext): Consequence[Option[Record]] = {
      if (collection == DataStore.CollectionId.EntityStore(BlobRepository.CollectionId)) metadata.incrementAndGet()
      delegate.load(collection, id)
    }
    override def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)
        (using ctx: ExecutionContext): Consequence[Unit] = { writes.incrementAndGet(); delegate.create(collection, id, record) }
    override def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)
        (using ctx: ExecutionContext): Consequence[Unit] = { writes.incrementAndGet(); delegate.save(collection, id, record) }
    override def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)
        (using ctx: ExecutionContext): Consequence[Unit] = { writes.incrementAndGet(); delegate.update(collection, id, changes) }
    override def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)
        (using ctx: ExecutionContext): Consequence[Unit] = { writes.incrementAndGet(); delegate.delete(collection, id) }
    override def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] =
      delegate.search(collection, directive)
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] = delegate.count(collection, directive)
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)
        (using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = { writes.incrementAndGet(); delegate.mutateEntityDirect(plan) }
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)
        (using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = { writes.incrementAndGet(); delegate.compareAndSetEntity(plan) }
    override def mutateVersionedEntity(plan: EntityVersionedMutationPlan)
        (using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = { writes.incrementAndGet(); delegate.mutateVersionedEntity(plan) }
  }

  final case class Effects(writes: Int, entitywrites: Int, metadata: Int, gets: Int, opens: Int,
    closes: Int, consumed: Long, maxrequest: Int, puts: Int, deletes: Int, sources: Int, ports: Int,
    tasks: Int, getters: Int, canonical: Int, resolvers: Int)
  final case class Produced(id: JobId, snapshot: DurableJobStoreSnapshot, reference: DurableExternalReference,
    payment: DurableJobLifetimeQuotaSnapshot)

  final class Fixture(val root: Path, owned: Boolean = true) {
    val native = new JobDurableRuntimeFixtures.Fixture(root)
    val source = new DurableJobOperationResponsePublicationFixtures.Source(recipe(), owned)
    val blob = new DurableJobOperationResponsePayloadFixtures.MeasuredBlobStore(native.selectedBlob)
    val resolver = new Resolver
    private var _sql = Option.empty[NativeSql]
    private var _tasks = Vector.empty[JobDurableLifetimeRecoveryFixtures.Task]
    native.selectedEvidence = source
    native.selectedBlob = blob
    native.selectedCallerAccessResolver = Some(resolver)
    def sql: NativeSql = _sql.get
    def start(): Consequence[Unit] = native.start().map { _ =>
      val collection = DataStore.CollectionId.EntityStore(DurableJobStoreEntity.Collection)
      val managed = success(native.context.dataStoreSpace.dataStore(collection)).asInstanceOf[SqlDataStore]
      val measured = new NativeSql(managed)
      _sql = Some(measured)
      native.context.dataStoreSpace.useDataStore(measured)
    }
    def open(id: JobId): Unit = {
      native.startup.entries = Vector(JobDurableRuntimeFixtures.candidate(id))
      success(start())
      native.owner.health.state shouldBe JobDurableOperationalState.Ready
      native.engine.durableTerminalProjectionReport.get.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Registered)
    }
    def current: ExecutionContext = caller(native.context)
    def query(id: JobId, context: ExecutionContext): Consequence[Option[JobQueryReadModel]] = native.engine.queryVisible(id)(using context)
    def use[A](body: => Consequence[A]): Consequence[A] = native.owner._execute_c(body)
    def produce(response: OperationResponse = review): Produced = {
      val selected = DurableJobOperationResponsePublicationFixtures.task(response)
      _tasks :+= selected
      val id = success(use(native.engine.submit(List(selected), current,
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async))))
      native.engine.drainAll() shouldBe 1
      selected.runs.get() shouldBe 1
      selected.canonical.get() shouldBe 1
      source.requests.map(_.request.boundary) shouldBe DurableJobKeyedEvidenceFixtures.boundaries
      source.requests.map(_.expectedrevision) shouldBe (1L to 6L).toVector
      val snapshot = native.signed(id)
      Produced(id, snapshot, reference(snapshot), native.native(id))
    }
    def reference(snapshot: DurableJobStoreSnapshot): DurableExternalReference = snapshot.record.body.result match {
      case DurableResultOutcome.Succeeded(DurableValue.External(value)) => value
      case _ => throw new IllegalStateException("actual terminal owned response required")
    }
    def jobEntityId(id: JobId): EntityId = success(DurableJobStoreEntity.entityId(id.value))
    def row(id: EntityId): Option[Record] = success(use(sql.load(
      DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))(using native.context)))
    def rows(id: JobId, reference: Option[DurableExternalReference] = None): Vector[Option[Record]] =
      (Vector(jobEntityId(id), DurableJobBackupFixtures.storageId, DurableJobBackupFixtures.lifetimeId,
        DurableJobBackupFixtures.blobQuotaId) ++ reference.map(value => success(EntityId.parse(value.reference)))).map(row)
    def replaceText(id: EntityId, text: String): Unit = {
      val original = row(id).get
      success(use(sql.save(DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id),
        original ++ Record.dataAuto("canonicalJson" -> text))(using native.context)))
    }
    def removeRow(id: EntityId): Unit = success(use(sql.delete(
      DataStore.CollectionId.EntityStore(id.collection), DataStore.EntryId(id))(using native.context)))
    def checkpoint(snapshot: DurableJobStoreSnapshot, inactive: Boolean = false): DurableJobStoreSnapshot = {
      val body = snapshot.record.body
      val retention = if (inactive) body.retention.copy(deletionState = DurableDeletionState.Deleted,
        deletedAt = Some(JobDurableRuntimeFixtures.instant.plusSeconds(30))) else body.retention
      val changed = success(DurableJobRecord.createV2(body.copy(identity = body.identity.copy(revision = body.identity.revision + 1L),
        retention = retention)))
      success(use(native.owner._store.checkpoint(snapshot, changed, access)(using native.context)))
    }
    def quota: DurableJobOwnedBlobQuotaRecord = success(DurableJobOwnedBlobQuotaCodec.decode(
      row(DurableJobBackupFixtures.blobQuotaId).get.getString("canonicalJson").get))
    def releaseQuota(): Unit = replaceText(DurableJobBackupFixtures.blobQuotaId,
      success(DurableJobOwnedBlobQuotaCodec.encode(quota.copy(entries = quota.entries.map(_.copy(state = DurableJobOwnedBlobQuotaState.Released))))))
    def metadata(reference: DurableExternalReference): Blob = success(use(native.entity.load[Blob](
      success(EntityId.parse(reference.reference)))(using summon[EntityPersistent[Blob]], native.context))).get
    def replaceBlob(reference: DurableExternalReference, change: Blob => Blob): Unit = {
      val changed = change(metadata(reference))
      val original = row(changed.id).get
      success(use(sql.save(DataStore.CollectionId.EntityStore(changed.id.collection), DataStore.EntryId(changed.id),
        original ++ summon[EntityPersistent[Blob]].toStoreRecord(changed))(using native.context)))
    }
    def physical(reference: DurableExternalReference): Vector[Byte] = {
      val storage = metadata(reference).storageRef.get
      Files.readAllBytes(root.resolve("blobs").resolve(storage.container).resolve(storage.key)).toVector
    }
    def effects: Effects = Effects(sql.writes.get(), native.entity.writes.get(), sql.metadata.get(), blob.gets.get(),
      blob.opens.get(), blob.closes.get(), blob.consumed.get(), blob.maxRequest.get(), blob.puts.get(), blob.deletes.get(),
      source.requests.size, native.port.calls.get(), _tasks.map(_.runs.get()).sum, _tasks.map(_.getters.get()).sum,
      _tasks.map(_.canonical.get()).sum, resolver.contexts.size)
    def close(): Unit = native.close()
    def closed: Boolean = native.provider.forall(value => !value.managedResourceOption.get.isOpen)
  }

  def withFixture[A](body: Fixture => A): A = {
    val root = JobDurableRuntimeFixtures.root()
    val fixture = new Fixture(root)
    try { success(fixture.start()); body(fixture) }
    finally { fixture.close(); JobDurableRuntimeFixtures.remove(root) }
  }
  def withRecovered[A](body: (Fixture, Produced) => A): A = {
    val root = JobDurableRuntimeFixtures.root()
    val writer = new Fixture(root)
    var reader = Option.empty[Fixture]
    try {
      success(writer.start())
      val produced = writer.produce()
      writer.close()
      writer.closed shouldBe true
      val fresh = new Fixture(root)
      reader = Some(fresh)
      fresh.open(produced.id)
      body(fresh, produced)
    } finally { reader.foreach(_.close()); writer.close(); JobDurableRuntimeFixtures.remove(root) }
  }

  def expected(kind: String, input: String): OperationResponse = if (kind == "review") review else {
    require(kind == "scalar" && input.matches("[A-Za-z]{1,8}"), "bounded independent scalar input required")
    OperationResponse.Scalar(input)
  }
  def wire(kind: String, input: String): String = if (kind == "review") reviewWire else
    "{\"format\":{\"schemaId\":\"cncf.job-operation-response\",\"version\":1},\"response\":{\"kind\":\"scalar\",\"value\":{\"type\":\"string\",\"value\":\"" + input + "\"}}}"
  def assertDomain(response: OperationResponse, kind: String, input: String): Unit = {
    response shouldBe expected(kind, input)
    if (kind == "review") response match {
      case OperationResponse.RecordResponse(record) =>
        record.fields.map(field => field.key.toString -> field.value) shouldBe
          reviewFields.map { case (key, value) => key -> Field.Value.Single(value) }
      case _ => fail("independent six-field native Review required")
    } else response match {
      case OperationResponse.Scalar(value) => value shouldBe input; value.getClass shouldBe classOf[String]
      case _ => fail("independent native scalar required")
    }
  }
  def assertNative(fixture: Fixture, id: JobId, kind: String, input: String): Produced = {
    val snapshot = fixture.native.signed(id)
    val reference = fixture.reference(snapshot)
    val payment = fixture.native.native(id)
    val body = snapshot.record.body
    body.identity.jobId shouldBe id.value
    body.identity.revision shouldBe 6L
    body.lifecycle.status shouldBe DurableJobLifecycleStatus.Succeeded
    body.authorization shouldBe recipe().projection.authorization
    body.definitionSnapshot shouldBe recipe().projection.definitionSnapshot
    reference.storageKind shouldBe DurableJobOwnedBlobPayloads.storageKind
    reference.contentType shouldBe Some(DurableJobOperationResponsePayloads.contentType.header)
    success(EntityId.parse(reference.reference)).value shouldBe reference.reference
    fixture.physical(reference) shouldBe wire(kind, input).getBytes(StandardCharsets.UTF_8).toVector
    payment.seed.paid shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
    payment.seed.remaining shouldBe 0L
    payment.seed.normalids shouldBe body.tasks.map(_.taskId).toSet
    payment.seed.normalids.size shouldBe 1
    payment.seed.claims shouldBe Set.empty
    payment.cancelclaimed shouldBe false
    fixture.quota.entries.find(_.blobid == reference.reference).map(_.state) shouldBe Some(DurableJobOwnedBlobQuotaState.Published)
    Produced(id, snapshot, reference, payment)
  }

  final case class Identity(pid: Long, start: Instant, uuid: String)
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("closed result receipt boolean required")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def id: JobId = success(JobId.parse(apply("id")))
  }
  final case class Child(receipt: Receipt, identity: Identity, exited: Boolean, exitcode: Int)
  def identity(): Identity = {
    val process = ProcessHandle.current()
    Identity(process.pid(), process.info().startInstant().orElseThrow(() =>
      new IllegalStateException("actual process start instant required")), _jvm_uuid)
  }
  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe _receipt_keys
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
    require(size > 0L && size <= _receipt_limit, "bounded observational receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "result receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size && facts.keySet == _receipt_keys, "closed unique result receipt keys required")
    val receipt = Receipt(facts)
    UUID.fromString(receipt.identity.uuid)
    receipt.id
    receipt
  }
  final class Case(val root: Path) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    private var _writer_closed = false
    def writer(kind: String = "review", input: String = ""): Child = {
      require(!_writer_closed, "one genuine writer per result case")
      val child = _launch("writer", kind, input, None)
      child.receipt.flag("ownerclosed") shouldBe true
      child.receipt.flag("sqlclosed") shouldBe true
      child.exited shouldBe true
      _writer_closed = true
      child
    }
    def reader(id: JobId, kind: String = "review", input: String = "", foreign: Boolean = false): Child = {
      require(_writer_closed, "actual writer exit and physical close precede reader launch")
      _launch(if (foreign) "foreign-reader" else "reader", kind, input, Some(id))
    }
    private def _launch(mode: String, kind: String, input: String, id: Option[JobId]): Child = {
      expected(kind, input)
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "actual absolute Java executable required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.DurableJobOperationResponseQueryProbe", mode, native.toString,
        receiptpath.toString, kind, input) ++ id.toVector.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(logs.resolve(s"$mode.stdout.log").toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS))
          throw new IllegalStateException(s"result query $mode timed out; retained logs $logs")
        val exit = process.exitValue()
        if (exit != 0) throw new IllegalStateException(s"result query $mode exit $exit; retained logs $logs; ${_excerpt(stderr)}")
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        id.foreach(value => receipt.id shouldBe value)
        Child(receipt, receipt.identity, !process.isAlive, exit)
      } finally if (process.isAlive) {
        process.destroy()
        if (!process.waitFor(5L, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) throw new IllegalStateException("owned result child did not terminate")
        }
      }
    }
  }
  def withCase[A](body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-result-query/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try { val result = body(new Case(root)); successful = true; result }
    finally if (successful) JobDurableRuntimeFixtures.remove(root)
  }
  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8) finally input.close()
  }
}
