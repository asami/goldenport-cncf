package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import org.scalatest.matchers.should.Matchers
import JobCrossProcessFixtures.Identity

/*
 * Sequential owned JVMs exchange bounded observations and opaque identities only.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobMigrationCrossProcessFixtures extends Matchers {
  enum Form(val wire: String) {
    case AllV1 extends Form("all-v1")
    case Mixed extends Form("mixed")
    case InvalidTail extends Form("invalid-tail")
  }
  object Form {
    def parse(value: String): Form = {
      Form.values.find(_.wire == value).getOrElse(throw new IllegalArgumentException("closed migration form required"))
    }
  }
  final case class CasePlan(count: Int, form: Form, payload: Boolean, capability: String) {
    require(count >= 1 && count <= 3 && (form == Form.AllV1 || count == 3), "bounded migration vector required")
    require(Set("job_admin", "content_admin").contains(capability), "closed Operator capability required")
    require(form != Form.InvalidTail || !payload, "malformed-tail fixture has no payload")
    def v1Count: Int = if (form == Form.Mixed) count - 1 else count
  }
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("migration receipt boolean refused")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = Vector.tabulate(number("count").toInt)(index =>
      JobDurableRuntimeFixtures.success(JobId.parse(apply(s"job.$index.id"))))
    def paymentFacts(index: Int): Map[String, String] = {
      _payment_keys.map(key => key -> apply(s"job.$index.$key")).toMap
    }
  }
  final case class Child(receipt: Receipt, identity: Identity, exitCode: Int, exited: Boolean)

  private val _receipt_limit = 16384
  private val _common_keys = Set("mode", "root", "sqlite", "pid", "start", "uuid", "count", "form", "payload",
    "capability", "ready", "refusal", "sourcecalls", "v1count", "jobwrites", "jobacks", "nativewrites", "auditclocks",
    "creates", "deletes", "lifewrites", "blobputs", "blobdeletes", "physicalopens", "physicalcloses", "physicalbytes",
    "ids", "ports", "evidence", "bodies", "getters", "queue", "drained", "runtimejobs", "retainedTerminalCount",
    "pending", "executing", "maintenanceTimersBeforeClose", "maintenanceTimersAfterClose", "bridgebound", "rowsunchanged",
    "paidroots", "subsystemclosed", "nodeclosed", "sqlclosed", "activeleases", "ownerclosure")
  private val _payment_keys = Set("chargedtasks", "historycredits", "admissions", "cancelcredits", "remaining",
    "normalid", "claims", "cancelclaimed")
  private val _job_keys = _payment_keys ++ Set("id", "ordinal", "format", "revision", "providerrevision",
    "sourceformat", "sourcerevision", "sourceproviderrevision", "terminalfact", "runtimefact", "nativefact",
    "status", "result", "quotaaccess")
  private val _modes = Set("writer", "migrating-reader", "fresh-reader", "default-reader", "false-reader",
    "unauthorized-reader", "invalid-reader")

  final class Case(val root: Path, val plan: CasePlan) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    private var _writer_exited = false
    private var _migration_exited = false
    private var _launched_modes = Set.empty[String]

    def writer(): Child = {
      require(!_writer_exited, "writer already exited")
      val child = _launch("writer", Vector.empty)
      _writer_exited = child.exited
      child
    }
    def reader(mode: String, ids: Vector[JobId]): Child = {
      require(_writer_exited, "writer must exit before any reader")
      require(mode != "writer" && _modes.contains(mode), "closed reader mode required")
      require(mode != "fresh-reader" || _migration_exited, "migration reader must exit before fresh V2 reader")
      val child = _launch(mode, ids)
      if (mode == "migrating-reader") _migration_exited = child.exited
      child
    }
    private def _launch(mode: String, ids: Vector[JobId]): Child = {
      require(!_launched_modes.contains(mode), "new mode receipt required")
      require(if (mode == "writer") ids.isEmpty else ids.size == plan.count && ids.distinct.size == plan.count,
        "unique opaque candidate vector required")
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute Java executable required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stdout = logs.resolve(s"$mode.stdout.log")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobMigrationCrossProcessProbe", mode, native.toString, receiptpath.toString,
        plan.count.toString, plan.form.wire, plan.payload.toString, plan.capability) ++ ids.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      _launched_modes += mode
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) {
          throw new IllegalStateException(s"migration $mode timed out; retained logs $logs; ${_excerpt(stderr)}")
        }
        val exit = process.exitValue()
        if (exit != 0) {
          throw new IllegalStateException(s"migration $mode exit $exit; retained logs $logs; ${_excerpt(stderr)}")
        }
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.number("count") shouldBe plan.count.toLong
        receipt("form") shouldBe plan.form.wire
        receipt.flag("payload") shouldBe plan.payload
        receipt("capability") shouldBe (if (mode == "unauthorized-reader") "" else plan.capability)
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        if (mode != "writer") receipt.ids shouldBe ids
        Child(receipt, receipt.identity, exit, !process.isAlive)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
              throw new IllegalStateException("owned migration child did not terminate")
            }
          }
        }
      }
    }
  }

  def withCase[A](plan: CasePlan)(body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-migration-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root, plan))
      successful = true
      result
    } finally {
      if (successful) JobDurableRuntimeFixtures.remove(root)
    }
  }
  def receiptKeys(count: Int): Set[String] = {
    require(count >= 1 && count <= 3, "bounded migration receipt count required")
    _common_keys ++ (0 until count).flatMap(index => _job_keys.map(key => s"job.$index.$key"))
  }
  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe receiptKeys(facts("count").toInt)
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
    require(size > 0L && size <= _receipt_limit, "bounded migration receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "migration receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size, "duplicate migration receipt key refused")
    val count = facts.getOrElse("count", throw new IllegalArgumentException("receipt count missing")).toInt
    require(facts.keySet == receiptKeys(count), "closed migration receipt keys required")
    val receipt = Receipt(facts)
    require(receipt.ids.distinct.size == count, "unique migration Job IDs required")
    require(_modes.contains(receipt("mode")), "closed migration receipt mode required")
    UUID.fromString(receipt.identity.uuid)
    receipt
  }
  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8)
    finally input.close()
  }
}
