package org.goldenport.cncf.job

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import scala.util.Try
import org.scalatest.matchers.should.Matchers

/*
 * Owned sequential JVMs and bounded observational receipts, never domain state.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobCrossProcessFixtures extends Matchers {
  private val _jvm_uuid = UUID.randomUUID().toString
  private val _receipt_limit = 16384
  private val _common_keys = Set("mode", "root", "sqlite", "pid", "start", "uuid", "count",
    "ready", "refusal", "candidates", "registered", "runtimejobs", "terminaljobs", "ports",
    "bodies", "getters", "queue", "drained", "creates", "writes", "deletes", "lifewrites",
    "rowsunchanged", "lifetimepresent", "storagepresent", "blobquotapresent", "paidroots",
    "subsystemclosed", "nodeclosed", "sqlclosed", "ownerclosed")
  private val _job_keys = Set("id", "revision", "providerrevision", "status", "result",
    "definitionid", "definitionkey", "definitionversion", "definitionrevision",
    "chargedtasks", "historycredits", "admissions", "cancelcredits", "remaining",
    "normalid", "claims", "cancelclaimed", "facade")

  final case class Identity(pid: Long, start: Instant, uuid: String)
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("cross-process receipt boolean refused")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def ids: Vector[JobId] = Vector.tabulate(number("count").toInt)(index =>
      JobDurableRuntimeFixtures.success(JobId.parse(apply(s"job.$index.id"))))
    def jobFacts(index: Int): Map[String, String] = _job_keys.filterNot(_ == "facade")
      .map(key => key -> apply(s"job.$index.$key")).toMap
  }
  final case class Child(receipt: Receipt, identity: Identity, exitCode: Int, exited: Boolean)

  final class Case(val root: Path) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))

    def writer(count: Int): Child = _launch("writer", count, Vector.empty)
    def reader(ids: Vector[JobId], refused: Boolean = false): Child =
      _launch(if (refused) "refused-reader" else "reader", ids.size, ids)

    private def _launch(mode: String, count: Int, ids: Vector[JobId]): Child = {
      count should (be >= 0 and be <= 3)
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java),
        "absolute java.home/bin/java executable is required for cross-process acceptance")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stdout = logs.resolve(s"$mode.stdout.log")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val arguments = Vector(java.toString, "-classpath", runtimeClasspath(),
        "org.goldenport.cncf.job.JobCrossProcessProbe", mode, native.toString,
        receiptpath.toString, count.toString) ++ ids.map(_.value)
      val builder = new ProcessBuilder(arguments*)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      val process = builder.start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual child process start instant is required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS))
          throw new IllegalStateException(s"cross-process $mode timed out; logs retained at $logs")
        val exit = process.exitValue()
        if (exit != 0) throw new IllegalStateException(
          s"cross-process $mode exit $exit; logs retained at $logs; ${_excerpt(stderr)}")
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt.number("count") shouldBe count.toLong
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        if (mode != "writer") receipt.ids shouldBe ids
        Child(receipt, receipt.identity, exit, !process.isAlive)
      } finally {
        if (process.isAlive) {
          process.destroy()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(5L, TimeUnit.SECONDS))
              throw new IllegalStateException("owned cross-process child did not terminate")
          }
        }
      }
    }
  }

  def identity(): Identity = {
    val process = ProcessHandle.current()
    Identity(process.pid(), process.info().startInstant().orElseThrow(() =>
      new IllegalStateException("actual current process start instant is required")), _jvm_uuid)
  }

  def withCase[A](body: Case => A): A = {
    val parent = Files.createDirectories(Paths.get("target/job-cross-process/work").toAbsolutePath.normalize())
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
    require(count >= 0 && count <= 3, "cross-process receipt count refused")
    _common_keys ++ (0 until count).flatMap(index => _job_keys.map(key => s"job.$index.$key"))
  }

  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    val count = facts("count").toInt
    facts.keySet shouldBe receiptKeys(count)
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
    require(size > 0L && size <= _receipt_limit, "bounded cross-process receipt is required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "cross-process receipt entry refused")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(facts.size == pairs.size, "duplicate cross-process receipt key refused")
    val count = facts.getOrElse("count", throw new IllegalArgumentException("receipt count missing")).toInt
    require(facts.keySet == receiptKeys(count), "closed cross-process receipt keys required")
    val receipt = Receipt(facts)
    require(receipt.ids.distinct.size == count, "distinct native receipt Job IDs required")
    UUID.fromString(receipt.identity.uuid)
    receipt
  }

  def runtimeClasspath(): String = {
    val properties = Option(System.getProperty("java.class.path")).toVector
      .flatMap(_.split(java.util.regex.Pattern.quote(File.pathSeparator)).toVector).filter(_.nonEmpty)
      .map(value => Paths.get(value).toAbsolutePath.normalize().toString)
    var loader = Option(getClass.getClassLoader)
    var entries = Vector.empty[String]
    while (loader.nonEmpty) {
      loader.foreach {
        case value: URLClassLoader => entries ++= value.getURLs.toVector.flatMap(_file_url)
        case _ => ()
      }
      loader = loader.flatMap(value => Option(value.getParent))
    }
    val codesources = Vector(JobCrossProcessProbe.getClass, classOf[InMemoryJobEngine]).flatMap(value =>
      Option(value.getProtectionDomain).flatMap(domain => Option(domain.getCodeSource))
        .flatMap(source => _file_url(source.getLocation)))
    require(codesources.size == 2, "probe and main code-source classpath entries are required")
    val classpath = (codesources ++ properties ++ entries).distinct
    require(classpath.nonEmpty && classpath.forall(value => Files.exists(Paths.get(value))),
      "actual file test runtime classpath is required for cross-process acceptance")
    classpath.mkString(File.pathSeparator)
  }

  private def _file_url(url: URL): Option[String] =
    if (url.getProtocol.equalsIgnoreCase("file"))
      Try(Paths.get(url.toURI).toAbsolutePath.normalize().toString).toOption
    else None

  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8)
    finally input.close()
  }
}
