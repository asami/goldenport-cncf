package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import org.scalatest.matchers.should.Matchers
import JobCrossProcessFixtures.{Child, Identity}

/*
 * Genuine original writers and independent startup-access readers over native storage.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobSecurityCrossProcessFixtures extends Matchers {
  enum Mode(val wire: String, val denied: Boolean) {
    case ExactAccess extends Mode("exact-access", false)
    case ExtraScopes extends Mode("extra-scopes", false)
    case ForeignTenant extends Mode("foreign-tenant", true)
    case ForeignSubject extends Mode("foreign-subject", true)
    case MissingScopes extends Mode("missing-scopes", true)
    case UnrelatedScopes extends Mode("unrelated-scopes", true)

    def access: DurableJobRecordAccess = {
      val original = DurableJobBackupFixtures.access
      this match {
        case ExactAccess => original
        case ExtraScopes => original.copy(scopes = Set("job.read", "unused.read"))
        case ForeignTenant => original.copy(tenantId = "foreign-tenant")
        case ForeignSubject => original.copy(subjectId = "foreign-subject")
        case MissingScopes => original.copy(scopes = Set.empty)
        case UnrelatedScopes => original.copy(scopes = Set("job_admin", "content_admin"))
      }
    }
  }
  object Mode {
    def parse(value: String): Mode = values.find(_.wire == value).getOrElse(
      throw new IllegalArgumentException("closed security mode required"))
  }

  private val _receipt_limit = 16384
  private val _common_keys = Set("mode", "badindex", "root", "sqlite", "pid", "start", "uuid", "count",
    "ready", "health", "executionavailable", "runtimeready", "refusal", "candidates", "sourcecalls",
    "registered", "runtimejobs", "terminaljobs", "ports", "bodies", "getters", "queue", "drained",
    "creates", "writes", "deletes", "lifewrites", "ids", "clocks", "timers", "rowsunchanged",
    "lifetimepresent", "storagepresent", "blobquotapresent", "paidroots", "subsystemclosed", "nodeclosed",
    "sqlclosed", "activeleases", "ownerinstalled", "ownerclosure")
  private val _native_keys = Set("id", "revision", "providerrevision", "status", "result", "definitionid",
    "definitionkey", "definitionversion", "definitionrevision", "chargedtasks", "historycredits", "admissions",
    "cancelcredits", "remaining", "normalid", "claims", "cancelclaimed")
  private val _job_keys = _native_keys ++ Set("ordinal", "terminalfact", "runtimefact", "queryvisible", "facade")

  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("security receipt boolean refused")
    }
    def mode: Mode = Mode.parse(apply("mode"))
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = Vector.tabulate(number("count").toInt)(index =>
      JobDurableRuntimeFixtures.success(JobId.parse(apply(s"job.$index.id"))))
    def jobFacts(index: Int): Map[String, String] = _native_keys.map(key => key -> apply(s"job.$index.$key")).toMap
  }
  final case class ReaderChild(receipt: Receipt, identity: Identity, exitCode: Int, exited: Boolean)

  final class Case(val root: Path) {
    private val _original = new JobCrossProcessFixtures.Case(root)
    val native: Path = _original.native
    val control: Path = _original.control
    val logs: Path = _original.logs

    def writer(count: Int): Child = {
      require(count >= 1 && count <= 3, "security writer count must be 1..3")
      _original.writer(count)
    }

    def reader(ids: Vector[JobId], mode: Mode, badIndex: Int): ReaderChild = {
      require(ids.size >= 1 && ids.size <= 3 && ids.distinct.size == ids.size &&
        badIndex >= 0 && badIndex < ids.size, "unique security candidate count/index must agree")
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute java.home/bin/java is required")
      val receiptpath = control.resolve("security-reader.receipt")
      val stdout = logs.resolve("security-reader.stdout.log")
      val stderr = logs.resolve("security-reader.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobSecurityCrossProcessProbe", mode.wire, native.toString,
        receiptpath.toString, ids.size.toString, badIndex.toString) ++ ids.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual security reader start instant is required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) {
          throw new IllegalStateException(s"security reader timed out; logs retained at $logs")
        }
        val exit = process.exitValue()
        if (exit != 0) {
          throw new IllegalStateException(s"security reader exit $exit; logs retained at $logs; ${_excerpt(stderr)}")
        }
        val receipt = readReceipt(receiptpath)
        receipt.mode shouldBe mode
        receipt.number("badindex") shouldBe badIndex.toLong
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.number("count") shouldBe ids.size.toLong
        receipt.ids shouldBe ids
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        ReaderChild(receipt, receipt.identity, exit, !process.isAlive)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
              throw new IllegalStateException("owned security reader did not terminate")
            }
          }
        }
      }
    }
  }

  def withCase[A](body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-security-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root))
      successful = true
      result
    } finally {
      if (successful) JobDurableRuntimeFixtures.remove(root)
    }
  }

  def receiptKeys(count: Int): Set[String] = {
    require(count >= 1 && count <= 3, "security receipt count must be 1..3")
    _common_keys ++ (0 until count).flatMap(index => _job_keys.map(key => s"job.$index.$key"))
  }

  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    val count = facts("count").toInt
    require(facts.keySet == receiptKeys(count), "closed security receipt keys required")
    val text = facts.toVector.sortBy(_._1).map { case (key, value) =>
      key + "=" + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }.mkString("", "\n", "\n")
    val bytes = text.getBytes(StandardCharsets.UTF_8)
    require(bytes.length <= _receipt_limit, "bounded security receipt required")
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }

  def readReceipt(path: Path): Receipt = {
    val size = Files.size(path)
    require(size > 0L && size <= _receipt_limit, "bounded security receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "security receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size, "duplicate security receipt key refused")
    val count = facts.getOrElse("count", throw new IllegalArgumentException("security receipt count missing")).toInt
    require(facts.keySet == receiptKeys(count), "closed security receipt keys required")
    val receipt = Receipt(facts)
    receipt.mode
    val badindex = receipt.number("badindex")
    require(badindex >= 0L && badindex < count && receipt.ids.distinct.size == count,
      "unique security receipt IDs and index must agree")
    UUID.fromString(receipt.identity.uuid)
    receipt
  }

  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8)
    finally input.close()
  }
}
