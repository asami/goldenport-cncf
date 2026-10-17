package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import org.scalatest.matchers.should.Matchers
import JobCrossProcessFixtures.Identity

/*
 * Owned sequential cursor JVMs and closed, bounded selected observations.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobCursorCrossProcessFixtures extends Matchers {
  enum Mode(val wire: String) {
    case Writer extends Mode("writer")
    case Appender extends Mode("appender")
    case FirstReader extends Mode("first-reader")
    case ContinueReader extends Mode("continue-reader")
    case FilterReader extends Mode("filter-reader")
    case CallerReader extends Mode("caller-reader")
    case InvalidReader extends Mode("invalid-reader")
    case BoundsReader extends Mode("bounds-reader")
    case ExpiredReader extends Mode("expired-reader")
  }

  private val _receipt_limit = 16384
  private val _common_keys = Set("mode", "root", "sqlite", "pid", "start", "uuid", "count",
    "ready", "sourcecalls", "registered", "runtimejobs", "ports", "tasks", "canonical", "getters",
    "queue", "drained", "creates", "writes", "deletes", "lifewrites", "keyedcalls", "rowsunchanged",
    "oldrowsunchanged", "rawabsent", "managementunavailable", "paidroots", "policycalls", "outcome",
    "failures", "pagecount", "total", "cursor", "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed", "leases")
  private val _job_keys = Set("id", "revision", "providerrevision", "status", "result", "chargedtasks",
    "historycredits", "admissions", "cancelcredits", "remaining", "normalcount", "claims", "cancelclaimed")

  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("cursor receipt boolean refused")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = Vector.tabulate(number("count").toInt)(index =>
      JobDurableRuntimeFixtures.success(JobId.parse(apply(s"job.$index.id"))))
    def pageIds: Vector[JobId] = Vector.tabulate(number("pagecount").toInt)(index =>
      JobDurableRuntimeFixtures.success(JobId.parse(apply(s"page.$index.id"))))
    def cursor: String = apply("cursor")
    def jobFacts(jobId: JobId): Map[String, String] = {
      val index = ids.indexOf(jobId)
      require(index >= 0, "selected native receipt Job required")
      _job_keys.map(key => key -> apply(s"job.$index.$key")).toMap
    }
  }

  final case class Child(receipt: Receipt, identity: Identity, exitCode: Int, exited: Boolean)

  final class Case(val root: Path) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    private var _sequence = 0

    def writer(count: Int): Child = _launch(Mode.Writer, count, 1, "", Vector.empty)
    def appender(ids: Vector[JobId]): Child = {
      ids.size shouldBe 2
      _launch(Mode.Appender, 2, 1, "", ids)
    }
    def reader(mode: Mode, ids: Vector[JobId], firstLimit: Int, cursor: String = ""): Child = {
      require(mode != Mode.Writer && mode != Mode.Appender, "closed reader mode required")
      _launch(mode, ids.size, firstLimit, cursor, ids)
    }

    private def _launch(mode: Mode, count: Int, firstlimit: Int, cursor: String, ids: Vector[JobId]): Child = {
      require(count >= 2 && count <= 3 && firstlimit >= 1 && firstlimit < count,
        "bounded native cursor process count and first limit required")
      require(cursor.length <= 280, "opaque API cursor transport bound required")
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute java.home/bin/java required")
      _sequence += 1
      val label = s"${_sequence}-${mode.wire}"
      val receiptpath = control.resolve(s"$label.receipt")
      val stdout = logs.resolve(s"$label.stdout.log")
      val stderr = logs.resolve(s"$label.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobCursorCrossProcessProbe", mode.wire, native.toString,
        receiptpath.toString, count.toString, firstlimit.toString, cursor) ++ ids.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS))
          throw new IllegalStateException(s"cursor child ${mode.wire} timed out; logs retained at $logs")
        val exit = process.exitValue()
        if (exit != 0) throw new IllegalStateException(
          s"cursor child ${mode.wire} exit $exit; logs retained at $logs; ${_excerpt(stderr)}")
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode.wire
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.number("count") shouldBe (if (mode == Mode.Appender) 3L else count.toLong)
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        if (mode != Mode.Writer && mode != Mode.Appender) receipt.ids shouldBe ids.sortBy(_.print)
        if (mode == Mode.Appender) receipt.ids.toSet should contain allElementsOf ids
        Child(receipt, receipt.identity, exit, !process.isAlive)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS))
              throw new IllegalStateException("owned cursor child did not terminate")
          }
        }
      }
    }
  }

  def withCase[A](body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-cursor-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root))
      successful = true
      result
    } finally if (successful) JobDurableRuntimeFixtures.remove(root)
  }

  def receiptKeys(count: Int, pageCount: Int): Set[String] = {
    require(count >= 2 && count <= 3 && pageCount >= 0 && pageCount <= count,
      "bounded cursor receipt counts required")
    _common_keys ++ (0 until count).flatMap(index => _job_keys.map(key => s"job.$index.$key")) ++
      (0 until pageCount).map(index => s"page.$index.id")
  }

  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe receiptKeys(facts("count").toInt, facts("pagecount").toInt)
    facts("cursor").length should be <= 280
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
    require(size > 0L && size <= _receipt_limit, "bounded cursor receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "cursor receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size, "duplicate cursor receipt key refused")
    val count = facts.getOrElse("count", throw new IllegalArgumentException("cursor receipt count missing")).toInt
    val pagecount = facts.getOrElse("pagecount", throw new IllegalArgumentException("page count missing")).toInt
    require(facts.keySet == receiptKeys(count, pagecount), "closed bounded cursor receipt keys required")
    require(facts("cursor").length <= 280, "cursor receipt token bound required")
    val total = facts("total").toInt
    require(total >= 0 && total <= count, "bounded authorized receipt total required")
    val receipt = Receipt(facts)
    require(receipt.ids.distinct.size == count && receipt.pageIds.distinct.size == pagecount &&
      receipt.pageIds.forall(receipt.ids.contains), "distinct selected native page IDs required")
    require(Mode.values.exists(_.wire == receipt("mode")), "closed cursor receipt mode required")
    UUID.fromString(receipt.identity.uuid)
    receipt
  }

  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8)
    finally input.close()
  }
}
