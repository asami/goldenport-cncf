package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, ExecutionContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import JobCrossProcessFixtures.Identity
import JobDurableRuntimeFixtures.success

/*
 * Sequential owned JVMs observe genuine scheduled admission and abrupt exit.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobScheduledCrossProcessFixtures extends Matchers {
  private val _receipt_limit = 16384
  private val _modes = Set("scheduled-writer", "unstarted-crash-writer", "running-crash-writer",
    "scheduled-reader", "refused-reader", "terminal-reader")
  private val _keys = Set("mode", "root", "sqlite", "pid", "start", "uuid", "jobid", "status",
    "semanticRevision", "providerRevision", "chargedtasks", "historycredits", "admissions",
    "cancelcredits", "remaining", "normalcount", "claims", "cancelclaimed", "ready", "refusal",
    "port", "task", "canonical", "queue", "drain", "nativewrites", "sourcecalls",
    "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed")

  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("scheduled receipt boolean refused")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def jobId: JobId = success(JobId.parse(apply("jobid")))
    def nativeFacts: Map[String, String] = facts.filter { case (key, _) =>
      Set("jobid", "status", "semanticRevision", "providerRevision", "chargedtasks",
        "historycredits", "admissions", "cancelcredits", "remaining", "normalcount",
        "claims", "cancelclaimed").contains(key)
    }
  }
  final case class Child(receipt: Receipt, identity: Identity, exitCode: Int, exited: Boolean)

  final class Case(val root: Path) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))

    def writer(delay: Int, mode: String = "scheduled-writer"): Child = {
      require(Set("scheduled-writer", "unstarted-crash-writer", "running-crash-writer").contains(mode))
      _launch(mode, delay, None)
    }
    def reader(jobId: JobId, delay: Int, mode: String = "scheduled-reader"): Child = {
      require(Set("scheduled-reader", "refused-reader", "terminal-reader").contains(mode))
      _launch(mode, delay, Some(jobId))
    }

    private def _launch(mode: String, delay: Int, id: Option[JobId]): Child = {
      require(delay >= 1 && delay <= 20, "independent scheduled delay must be 1..20")
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute Java executable required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stdout = logs.resolve(s"$mode.stdout.log")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobScheduledCrossProcessProbe", mode, native.toString,
        receiptpath.toString, delay.toString) ++ id.toVector.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS))
          throw new IllegalStateException(s"scheduled $mode timed out; logs retained at $logs")
        val exit = process.exitValue()
        val expected = if (mode.endsWith("crash-writer")) 71 else 0
        if (exit != expected) throw new IllegalStateException(
          s"scheduled $mode exit $exit, expected $expected; logs at $logs; ${_excerpt(stderr)}")
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        id.foreach(value => receipt.jobId shouldBe value)
        Vector("subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed")
          .foreach(key => receipt.flag(key) shouldBe (expected == 0))
        Child(receipt, receipt.identity, exit, !process.isAlive)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS))
              throw new IllegalStateException("owned scheduled child did not terminate")
          }
        }
      }
    }
  }

  // Count actual native create/update/delete effects while retaining the
  // original profile, evidence, startup source, Port/Task and managed backend.
  final class Fixture(val root: Path) {
    val original = new JobDurableRuntimeFixtures.Fixture(root)
    val engine = original.engine
    val subsystem = original.subsystem
    val node = original.node
    val context = original.context
    val entity = new DurableJobBackupFixtures.NativeEntityStore
    val store = new DurableJobStore(entity, original.storagePolicy)
    val lifetime = store.lifetimeQuota(original.lifetimePolicy)
    private var _sql = Option.empty[SqlDataStore]
    private val _registered = new ConfiguredDurableJobRuntimeProvider(JobDurableRuntimeFixtures.providerId, selected => {
      original.builds.incrementAndGet()
      selected.resolveManagedComponentDataStoreC(original.environment, original.request).map { datastore =>
        val managed = JobDurableRuntimeFixtures.managedSql(selected, original.environment,
          original.request, root, datastore.asInstanceOf[SqlDataStore])
        _sql = Some(managed)
        context.dataStoreSpace.useDataStore(managed)
        context.entityStoreSpace.addEntityStore(entity)
        JobDurableLifetimeRecoveryFixtures.installJobEntity(entity, context)
        DurableJobRuntimeInputs(entity, original.blob, original.evidence, original.startup,
          original.port, original.retention, context, original.storagePolicy,
          original.blobPolicy, original.lifetimePolicy)
      }
    })

    def start(): Consequence[Unit] = {
      original.admit()
      success(subsystem._register_durable_job_provider_c(_registered))
      subsystem._initialize_durable_job_runtime_c()
    }
    def use[A](body: => A): A =
      success(subsystem._with_managed_datastore_lease_c(_ => Consequence.success(body)))
    def signed(jobId: JobId): DurableJobStoreSnapshot =
      use(success(store.load(jobId.value, JobDurableRuntimeFixtures.access)(using context)).get)
    def native(jobId: JobId): DurableJobLifetimeQuotaSnapshot =
      use(success(lifetime.snapshot(jobId.value, JobDurableRuntimeFixtures.access)(using context)))
    def rows(jobId: JobId): Vector[Option[Record]] = use {
      val ids = Vector(success(DurableJobStoreEntity.entityId(jobId.value)),
        DurableJobBackupFixtures.storageId, DurableJobBackupFixtures.lifetimeId,
        DurableJobBackupFixtures.blobQuotaId)
      ids.map(id => success(_sql.get.load(DataStore.CollectionId.EntityStore(id.collection),
        DataStore.EntryId(id))(using context)))
    }
    def submit(task: JobTask, delay: Option[Int]): JobId = success(original.owner._execute_c {
      engine.submit(List(task), context, JobSubmitOption(persistence = JobPersistencePolicy.Persistent,
        runMode = JobRunMode.Async, scheduledStartAt = delay.map(value =>
          JobDurableRuntimeFixtures.instant.plusSeconds(value.toLong))))
    })
    def managementContext: ExecutionContext = ExecutionContext.withSecurityContext(context,
      context.security.copy(capabilities = context.security.capabilities + Capability("job_admin")))
    def mutationCounts: Vector[Int] = Vector(entity.creates.get(), entity.writes.get(),
      entity.deletes.get(), entity.lifeWrites.get())
    def nativeWrites: Int = entity.creates.get() + entity.writes.get() + entity.deletes.get()
    def close(): Map[String, String] = {
      val owner = engine._durable_runtime_owner_option
      val subsystemresult = subsystem.shutdownC()
      val noderesult = node.shutdownC()
      success(subsystemresult)
      success(noderesult)
      node.state shouldBe SystemNode.State.Stopped
      node.activeLeaseCount shouldBe 0
      _sql.get.managedResourceOption.get.isOpen shouldBe false
      owner.foreach(value => value._backup_closed shouldBe true)
      Map("subsystemclosed" -> subsystemresult.isSuccess.toString,
        "nodeclosed" -> (node.state == SystemNode.State.Stopped).toString,
        "sqlclosed" -> (!_sql.get.managedResourceOption.get.isOpen).toString,
        "ownerclosed" -> owner.forall(_._backup_closed).toString)
    }
  }

  def withCase[A](body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-scheduled-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root))
      successful = true
      result
    } finally if (successful) JobDurableRuntimeFixtures.remove(root)
  }

  def modes: Set[String] = _modes
  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe _keys
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
    require(size > 0L && size <= _receipt_limit, "bounded scheduled receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "scheduled receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size && facts.keySet == _keys, "unique closed scheduled receipt keys required")
    require(_modes.contains(facts("mode")), "closed scheduled receipt mode required")
    val receipt = Receipt(facts)
    UUID.fromString(receipt.identity.uuid)
    receipt.jobId
    receipt
  }

  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8)
    finally input.close()
  }
}
