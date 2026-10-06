package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.TimeUnit
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, SubjectKind}
import org.scalatest.matchers.should.Matchers
import DurableJobOperationResponseQueryFixtures.success

/*
 * Closed current callers and sequential owned native Job experience JVMs.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobExperienceCrossProcessFixtures extends Matchers {
  enum CallerMode(val wire: String) {
    case Owner extends CallerMode("owner")
    case JobAdmin extends CallerMode("job-admin")
    case ContentAdmin extends CallerMode("content-admin")
    case Foreign extends CallerMode("foreign")
    case Anonymous extends CallerMode("anonymous")
    case BroadReader extends CallerMode("broad-reader")
    case ContentManager extends CallerMode("content-manager")
    case SamePrincipalOtherKind extends CallerMode("same-principal-other-kind")
  }
  object CallerMode {
    def parse(value: String): CallerMode = values.find(_.wire == value)
      .getOrElse(throw new IllegalArgumentException("closed current caller mode required"))
  }
  enum Selection(val wire: String) {
    case Views extends Selection("views")
    case Operator extends Selection("operator")
    case Foreign extends Selection("foreign")
    case Anonymous extends Selection("anonymous")
    case Broad extends Selection("broad")
    case Diagnostics extends Selection("diagnostics")
    case NegativeOffset extends Selection("negative-offset")
    case ZeroLimit extends Selection("zero-limit")
    case OversizeLimit extends Selection("oversize-limit")
    case Controls extends Selection("controls")
    case Generated extends Selection("generated")
  }
  object Selection {
    def parse(value: String): Selection = values.find(_.wire == value)
      .getOrElse(throw new IllegalArgumentException("closed facade case selector required"))
  }

  private val _jvm_uuid: String = UUID.randomUUID().toString
  private val _receipt_limit: Int = 16384
  private val _receipt_keys: Set[String] = Set("mode", "root", "sqlite", "pid", "start", "uuid",
    "id", "revision", "providerrevision", "ready", "status", "resultAvailability", "callermode", "selection",
    "domainChecked", "zeroEffects", "nativeUnchanged", "rawabsent", "outcome", "minecount", "operatorcount",
    "applicationcount", "diagnostictasks", "diagnostictimeline", "controlrefusals", "tasks", "canonical",
    "sources", "puts", "chargedtasks", "historycredits", "admissions", "cancelcredits", "remaining",
    "ownerclose", "nodeclose", "subsystemclose", "sqlclose", "leases")

  def caller(base: ExecutionContext, mode: CallerMode): ExecutionContext = {
    val owner = mode == CallerMode.Owner || mode == CallerMode.SamePrincipalOtherKind
    val principalid = if (owner) "subject-a" else if (mode == CallerMode.Anonymous) "anonymous" else "foreign-subject"
    val kind = mode match {
      case CallerMode.Anonymous => SubjectKind.Anonymous
      case CallerMode.SamePrincipalOtherKind => SubjectKind.Subsystem
      case _ => SubjectKind.User
    }
    val capabilities: Set[Capability] = mode match {
      case CallerMode.JobAdmin => Set(Capability("job_admin"))
      case CallerMode.ContentAdmin => Set(Capability("content_admin"))
      case CallerMode.BroadReader => Set(Capability("job_view"))
      case CallerMode.ContentManager => Set(Capability("content_manager"))
      case _ => Set.empty
    }
    val principalattributes = if (kind == SubjectKind.Anonymous) Map.empty[String, String]
      else Map("tenantId" -> "tenant-a", "authenticated" -> "true", "scopes" -> "job.read")
    val context = ExecutionContext.withSecurityContext(base, base.security.copy(principal = new Principal {
      def id: PrincipalId = PrincipalId(principalid)
      def attributes: Map[String, String] = principalattributes
    }, subjectKind = kind, session = None, capabilities = capabilities))
    context.security.principal.id.value shouldBe principalid
    context.security.subjectKind shouldBe kind
    context.security.capabilities shouldBe capabilities
    context.security.session shouldBe None
    context
  }

  final case class Identity(pid: Long, start: Instant, uuid: String)
  final case class Receipt(facts: Map[String, String]) {
    def apply(key: String): String = facts(key)
    def number(key: String): Long = apply(key).toLong
    def flag(key: String): Boolean = apply(key) match {
      case "true" => true
      case "false" => false
      case _ => throw new IllegalArgumentException("closed observation boolean required")
    }
    def identity: Identity = Identity(number("pid"), Instant.parse(apply("start")), apply("uuid"))
    def id: JobId = success(JobId.parse(apply("id")))
  }
  final case class Child(receipt: Receipt, identity: Identity, exited: Boolean, exitcode: Int)

  def identity(): Identity = {
    val process = ProcessHandle.current()
    Identity(process.pid(), process.info().startInstant().orElseThrow(() =>
      new IllegalStateException("actual JVM start instant required")), _jvm_uuid)
  }
  def writeReceipt(path: Path, facts: Map[String, String]): Unit = {
    facts.keySet shouldBe _receipt_keys
    val bytes = facts.toVector.sortBy(_._1).map { case (key, value) =>
      key + "=" + Base64.getEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))
    }.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
    bytes.length should be <= _receipt_limit
    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    ()
  }
  def readReceipt(path: Path): Receipt = {
    val size = Files.size(path)
    require(size > 0L && size <= _receipt_limit, "bounded observational receipt required")
    val pairs = Files.readString(path, StandardCharsets.UTF_8).linesIterator.map { line =>
      val split = line.indexOf('=')
      require(split > 0, "closed receipt entry required")
      line.take(split) -> new String(Base64.getDecoder.decode(line.drop(split + 1)), StandardCharsets.UTF_8)
    }.toVector
    val facts = pairs.toMap
    require(pairs.size == facts.size && facts.keySet == _receipt_keys, "closed unique observation keys required")
    val receipt = Receipt(facts)
    UUID.fromString(receipt.identity.uuid)
    receipt.id
    receipt
  }

  final class Case(val root: Path, val input: String, val callerMode: CallerMode, val selection: Selection) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    private var _writer_closed: Boolean = false
    private var _reader_launched: Boolean = false

    def writer(): Child = {
      require(!_writer_closed, "one genuine writer per case required")
      val child = _launch("writer", None)
      Vector("ownerclose", "nodeclose", "subsystemclose", "sqlclose").foreach(key => child.receipt.flag(key) shouldBe true)
      child.receipt.number("leases") shouldBe 0L
      child.exited shouldBe true
      child.exitcode shouldBe 0
      _writer_closed = true
      child
    }
    def reader(id: JobId): Child = {
      require(_writer_closed && !_reader_launched, "writer exit/close precedes one fresh reader required")
      _reader_launched = true
      _launch("reader", Some(id))
    }
    private def _launch(mode: String, id: Option[JobId]): Child = {
      require(input.matches("[A-Za-z]{1,8}"), "independent bounded scalar input required")
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath.normalize()
      require(Files.isRegularFile(java) && Files.isExecutable(java), "actual absolute Java required")
      val receiptpath = control.resolve(s"$mode.receipt")
      val stderr = logs.resolve(s"$mode.stderr.log")
      val stdout = logs.resolve(s"$mode.stdout.log")
      val arguments = Vector(java.toString, "-classpath", JobCrossProcessFixtures.runtimeClasspath(),
        "org.goldenport.cncf.job.JobExperienceCrossProcessProbe", mode, native.toString, receiptpath.toString,
        selection.wire, callerMode.wire, input) ++ id.toVector.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual owned child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) throw new IllegalStateException(
          s"Job experience $mode timed out; retained $logs; ${_excerpt(stderr)}")
        val exit = process.exitValue()
        if (exit != 0) throw new IllegalStateException(
          s"Job experience $mode exit $exit; retained $logs; ${_excerpt(stderr)}")
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("root") shouldBe native.toString
        receipt("sqlite") shouldBe native.resolve("jobs.db").toString
        receipt("selection") shouldBe selection.wire
        receipt("callermode") shouldBe (if (mode == "writer") CallerMode.Owner.wire else callerMode.wire)
        receipt.identity.pid shouldBe pid
        receipt.identity.start shouldBe start
        id.foreach(value => receipt.id shouldBe value)
        Child(receipt, receipt.identity, !process.isAlive, exit)
      } finally if (process.isAlive) {
        process.destroy()
        if (!process.waitFor(5L, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          if (!process.waitFor(5L, TimeUnit.SECONDS)) throw new IllegalStateException("owned C6 child did not terminate")
        }
      }
    }
  }
  def withCase[A](input: String, callerMode: CallerMode, selection: Selection)(body: Case => A): A = {
    require(input.matches("[A-Za-z]{1,8}"), "independent alpha1..8 input required")
    val parent = Files.createDirectories(Paths.get("target/job-experience-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root, input, callerMode, selection))
      successful = true
      result
    } finally if (successful) JobDurableRuntimeFixtures.remove(root)
  }
  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8) finally input.close()
  }
}
