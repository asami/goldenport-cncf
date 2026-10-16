package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.{Base64, UUID}
import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor,
  GenericSubsystemRuntimeBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationProviderBinding}
import org.goldenport.cncf.usernotification.{UserNotificationProvider, UserNotificationRequest, UserNotificationResult}
import org.goldenport.protocol.Protocol
import org.scalatest.matchers.should.Matchers
import DurableJobOperationResponseQueryFixtures.{Fixture, success}

/*
 * Actual native subsystem notification wiring and bounded sequential JVM cases.
 *
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobNotificationCrossProcessFixtures extends Matchers {
  enum Mode(val wire: String) {
    case Accepted extends Mode("accepted")
    case NoApp extends Mode("no-app")
    case Disabled extends Mode("disabled")
    case Refused extends Mode("refused")
    case Failure extends Mode("failure")
    case Throw extends Mode("throw")
    case UnsafeApp extends Mode("unsafe-app")
  }
  object Mode {
    def parse(value: String): Mode = values.find(_.wire == value)
      .getOrElse(throw new IllegalArgumentException("closed notification mode required"))
  }

  val privateMarker: String = "provider-private-credential-c7"
  private val _provider_name: String = "notification-c7"
  private val _jvm_uuid: String = UUID.randomUUID().toString
  private val _receipt_limit: Int = 16384
  private val _receipt_keys: Set[String] = Set("mode", "notificationmode", "root", "sqlite", "pid", "start", "uuid",
    "id", "revision", "providerrevision", "ready", "status", "resultAvailability", "domainChecked", "zeroEffects",
    "nativeUnchanged", "rawabsent", "providercalls", "diagnostics", "succeededevents", "events", "minecount",
    "tasks", "canonical", "getters", "fixturetasks", "sources", "puts", "chargedtasks", "historycredits",
    "admissions", "cancelcredits", "remaining", "ownerclose", "nodeclose", "subsystemclose", "sqlclose",
    "providerclose", "schedulerclose", "timers", "leases")

  final case class Invocation(request: UserNotificationRequest, context: ExecutionContext)
  final class RecordingProvider(val mode: Mode) extends UserNotificationProvider with AutoCloseable {
    private val _invocations: ConcurrentLinkedQueue[Invocation] = new ConcurrentLinkedQueue[Invocation]()
    private var _closed: Boolean = false
    def name: String = _provider_name
    def invocations: Vector[Invocation] = _invocations.iterator().asScala.toVector
    def closed: Boolean = _closed
    def notify(request: UserNotificationRequest)(using context: ExecutionContext): Consequence[UserNotificationResult] = {
      require(!_closed, "owned provider must be open during dispatch")
      _invocations.add(Invocation(request, context))
      mode match {
        case Mode.Failure => Consequence.stateInvalid(privateMarker)
        case Mode.Throw => throw new IllegalStateException(privateMarker)
        case _ => Consequence.success(UserNotificationResult(accepted = mode != Mode.Refused,
          notificationId = Some("notification-c7"), providerNotificationId = None,
          acceptedAt = JobDurableRuntimeFixtures.instant, attributes = Map.empty))
      }
    }
    def close(): Unit = { _closed = true }
  }

  final case class Wiring(provider: RecordingProvider, component: Component, context: ExecutionContext)
  def configure(fixture: Fixture, mode: Mode, reader: Boolean): Wiring = {
    val native = fixture.native
    val selected = new RecordingProvider(if (reader) Mode.Accepted else mode)
    val component = new Component() {
      override def userNotificationProviders: Vector[UserNotificationProvider] = Vector(selected)
    }
    val id = ComponentId("org.goldenport.cncf.test.NotificationC7")
    val core = Component.Core.create(name = id.name, componentId = id,
      instanceId = ComponentInstanceId.default(id), protocol = Protocol.empty, jobEngine = native.engine)
    val initialized = component.initialize(ComponentInit(native.subsystem, core, ComponentOrigin.Builtin))
      .withArtifactMetadata(Component.ArtifactMetadata(sourceType = "spec", name = "NotificationC7",
        version = "0.0.0", component = Some(_provider_name)))
    native.subsystem.add(Vector(initialized))
    val actual = native.subsystem.components.find(_.componentId == id).get
    native.subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<memory>"),
      subsystemName = native.subsystem.name,
      componentBindings = Vector(GenericSubsystemComponentBinding(_provider_name)),
      runtime = Some(GenericSubsystemRuntimeBinding(userNotification = Some(GenericSubsystemUserNotificationBinding(
        providers = Vector(GenericSubsystemUserNotificationProviderBinding(name = _provider_name,
          component = _provider_name, enabled = Some(reader || mode != Mode.Disabled),
          priority = Some(100), isDefault = Some(true)))))))))
    actual.jobEngine shouldBe native.engine
    val scope = actual.logic.executionContext().cncfCore.scope
    val context = DurableJobOperationResponseQueryFixtures.caller(native.context)
      .withScope(scope)
    context.security.principal.id.value shouldBe "subject-a"
    context.security.subjectKind.toString shouldBe "User"
    context.core shouldBe fixture.current.core
    context.dataStoreSpace shouldBe native.context.dataStoreSpace
    context.cncfCore.scope shouldBe scope
    Wiring(selected, actual, context)
  }

  def parameters(mode: Mode): Map[String, String] = Map("web.service" -> "post", "web.operation" -> "publish") ++
    (if (mode == Mode.NoApp) Map.empty else Map("web.app" -> (if (mode == Mode.UnsafeApp) "../private" else "blog")))

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

  final class Case(val root: Path, val input: String, val notificationMode: Mode) {
    val native: Path = Files.createDirectory(root.resolve("native"))
    val control: Path = Files.createDirectory(root.resolve("control"))
    val logs: Path = Files.createDirectory(root.resolve("logs"))
    private var _writer_closed: Boolean = false
    private var _reader_launched: Boolean = false
    def writer(): Child = {
      require(!_writer_closed, "one real writer per notification case required")
      val child = _launch("writer", None)
      Vector("ownerclose", "nodeclose", "subsystemclose", "sqlclose", "providerclose", "schedulerclose")
        .foreach(key => child.receipt.flag(key) shouldBe true)
      child.receipt.number("leases") shouldBe 0L
      child.receipt.number("timers") shouldBe 0L
      child.exited shouldBe true
      child.exitcode shouldBe 0
      _writer_closed = true
      child
    }
    def reader(id: JobId): Child = {
      require(_writer_closed && !_reader_launched, "writer exit/close precedes one fresh enabled reader")
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
        "org.goldenport.cncf.job.JobNotificationCrossProcessProbe", mode, native.toString, receiptpath.toString,
        notificationMode.wire, input) ++ id.toVector.map(_.value)
      val process = new ProcessBuilder(arguments*).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        val pid = process.pid()
        val start = process.toHandle.info().startInstant().orElseThrow(() =>
          new IllegalStateException("actual owned child start instant required"))
        if (!process.waitFor(60L, TimeUnit.SECONDS)) throw new IllegalStateException(
          s"Job notification $mode timed out; retained $logs; ${_excerpt(stderr)}")
        val exit = process.exitValue()
        if (exit != 0) throw new IllegalStateException(
          s"Job notification $mode exit $exit; retained $logs; ${_excerpt(stderr)}")
        val receipt = readReceipt(receiptpath)
        receipt("mode") shouldBe mode
        receipt("notificationmode") shouldBe notificationMode.wire
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
          if (!process.waitFor(5L, TimeUnit.SECONDS)) throw new IllegalStateException("owned C7 child did not terminate")
        }
      }
    }
  }
  def withCase[A](input: String, mode: Mode)(body: Case => A): A = {
    require(input.matches("[A-Za-z]{1,8}"), "independent alpha1..8 input required")
    val parent = Files.createDirectories(Paths.get("target/job-notification-cross-process/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "case-")
    var successful = false
    try {
      val result = body(new Case(root, input, mode))
      successful = true
      result
    } finally if (successful) JobDurableRuntimeFixtures.remove(root)
  }
  private def _excerpt(path: Path): String = {
    val input = Files.newInputStream(path)
    try new String(input.readNBytes(2048), StandardCharsets.UTF_8) finally input.close()
  }
}
