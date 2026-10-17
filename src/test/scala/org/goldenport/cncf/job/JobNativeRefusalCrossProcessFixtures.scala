package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.scalatest.matchers.should.Matchers
import JobCrossProcessFixtures.Identity

/*
 * Owned sequential JVMs observe deliberate native faults without transferring state.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobNativeRefusalCrossProcessFixtures extends Matchers {
  enum Fault(val wire: String) {
    case Malformed extends Fault("malformed")
    case Integrity extends Fault("integrity")
    case FutureVersion extends Fault("future-version")
    case Noncanonical extends Fault("noncanonical")
    case Missing extends Fault("missing")
  }

  object Fault {
    def parse(value: String): Fault = {
      values.find(_.wire == value).getOrElse(throw new IllegalArgumentException("closed native fault required"))
    }
  }

  private val _common_keys = Set("mode", "fault", "badindex", "root", "sqlite", "pid", "start", "uuid",
    "count", "terminalobservation", "ready", "refusal", "candidates", "registered", "runtimejobs", "terminaljobs",
    "ports", "bodies", "getters", "queue", "drained", "creates", "writes", "deletes", "lifewrites",
    "ids", "clocks", "timers", "rowsunchanged", "paidroots", "ownerinstalled", "ownerclosure",
    "subsystemclosed", "nodeclosed", "sqlclosed", "activeleases")
  private val _job_keys = Set("id", "ordinal", "terminalfact", "runtimefact", "revision", "providerrevision",
    "status", "result", "chargedtasks", "historycredits", "admissions", "cancelcredits", "remaining",
    "normalid", "claims", "cancelclaimed", "quotaaccess")
  private val _payment_keys = Set("chargedtasks", "historycredits", "admissions", "cancelcredits",
    "remaining", "normalid", "claims", "cancelclaimed")

  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = {
      apply(key) match {
        case "true" => true
        case "false" => false
        case _ => throw new IllegalArgumentException("native refusal receipt boolean refused")
      }
    }
    def identity: Identity = Identity(number("pid"), java.time.Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = Vector.tabulate(number("count").toInt)(index =>
      JobDurableRuntimeFixtures.success(JobId.parse(apply(s"job.$index.id"))))
    def paymentFacts(index: Int): Map[String, String] = {
      _payment_keys.map(key => key -> apply(s"job.$index.$key")).toMap
    }
  }

  final case class Child(receipt: Receipt, identity: Identity, exitCode: Int, exited: Boolean)

  final class Case(val root: Path) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))

    def writer(count: Int, fault: Fault, badIndex: Int): Child = _launch("writer", count, fault, badIndex, Vector.empty)
    def reader(ids: Vector[JobId], fault: Fault, badIndex: Int): Child = {
      _launch("refused-reader", ids.size, fault, badIndex, ids)
    }

    private def _launch(mode: String, count: Int, fault: Fault, badindex: Int, ids: Vector[JobId]): Child = {
      count should (be >= 1 and be <= 3)
      badindex should (be >= 0 and be < count)
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "absolute Java executable required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stdout = logs.resolve(s"$mode.stdout.log")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobNativeRefusalCrossProcessProbe", mode, native.toString,
        receiptpath.toString, count.toString, fault.wire, badindex.toString) ++ ids.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) {
          throw new IllegalStateException(s"native refusal $mode timed out; logs retained at $logs; ${_excerpt(stderr)}")
        }
        val exit = process.exitValue()
        if (exit != 0) {
          throw new IllegalStateException(s"native refusal $mode exit $exit; logs retained at $logs; ${_excerpt(stderr)}")
        }
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("fault") shouldBe fault.wire
        receipt.number("badindex") shouldBe badindex.toLong
        receipt.number("count") shouldBe count.toLong
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        if (mode == "refused-reader") receipt.ids shouldBe ids
        Child(receipt, receipt.identity, exit, !process.isAlive)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
              throw new IllegalStateException("owned native refusal child did not terminate")
            }
          }
        }
      }
    }
  }

  def withCase[A](body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-native-refusal-cross-process/work").toAbsolutePath.normalize())
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
    require(count >= 1 && count <= 3, "native refusal receipt count required")
    _common_keys ++ (0 until count).flatMap(index => _job_keys.map(key => s"job.$index.$key"))
  }

  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe receiptKeys(facts("count").toInt)
    val bytes = facts.toVector.sortBy(_._1).map { case (key, value) =>
      key + "=" + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
    bytes.length should be <= 16384
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }

  def readReceipt(path: Path): Receipt = {
    val size = Files.size(path)
    require(size > 0L && size <= 16384L, "bounded native refusal receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "native refusal receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size, "duplicate native refusal receipt key refused")
    val count = facts.getOrElse("count", throw new IllegalArgumentException("receipt count missing")).toInt
    require(facts.keySet == receiptKeys(count), "closed native refusal receipt keys required")
    val receipt = Receipt(facts)
    require(receipt.ids.distinct.size == count, "unique native receipt IDs required")
    require(receipt.number("badindex") >= 0 && receipt.number("badindex") < count, "receipt bad index refused")
    require(Set("writer", "refused-reader").contains(receipt("mode")), "receipt mode refused")
    Fault.parse(receipt("fault"))
    java.util.UUID.fromString(receipt.identity.uuid)
    receipt
  }

  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8)
    finally input.close()
  }
}
