package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.{Base64, UUID}
import java.util.concurrent.{Callable, CountDownLatch, CyclicBarrier, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 * @author  ASAMI, Tomoharu
 */
/** Every response here is explicitly test-owned provider data, never installed-runner evidence. */
final class RegisteredSbtHostDriverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RegisteredSbtHostDriverSpec.Fixture

  private val _stdout = Vector(
    "[info] org.example.SelectedSpec:", "[info] Total number of tests run: 1",
    "[info] Suites: completed 1, aborted 0",
    "[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0",
    "[info] All tests passed.").mkString("\n").getBytes(StandardCharsets.UTF_8).toVector
  private val _stderr = Vector(0.toByte, 127.toByte, 65.toByte)

  "Registered SBT host bridge" should {
    "publish exact admitted commands and preserve actual test-owned bytes for every typed operation" in {
      Given("runtime-admitted Compile, Test and TestOnly definitions and a local launcher that must remain unused")
      val fixtures = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly).map(_fixture(_))
      try {
        When("each start publishes one complete request and its test-owned host returns a bound terminal observation")
        val observations = fixtures.map { fixture =>
          val workarea = new ProcessExecutionWorkArea(Files.createDirectory(fixture.root.resolve("uow-workarea")))
          try {
            val handle = _take(fixture.driver.startC(fixture.execution, workarea))
            val request = _request(fixture)
            _write_response(request, _response(request))
            (fixture, request, _take(handle.awaitC))
          } finally workarea.close()
        }
        Then("the request contains only CNCF-owned fixed values and the returned captures retain every byte")
        observations.foreach { case (fixture, request, result) =>
          request.asObject.get.keys.toSet shouldBe Set("schema", "requestId", "workspaceId", "operation",
            "programIdentity", "workingDirectory", "executable", "arguments", "environment",
            "executionTimeoutMillis", "stdoutBytes", "stderrBytes", "responseFile")
          request.hcursor.get[String]("schema").toOption shouldBe Some("cncf.registered-sbt-request.v1")
          UUID.fromString(request.hcursor.get[String]("requestId").toOption.get).toString shouldBe
            request.hcursor.get[String]("requestId").toOption.get
          request.hcursor.get[String]("workspaceId").toOption shouldBe Some("test-checkout")
          request.hcursor.get[String]("operation").toOption shouldBe Some(fixture.operation match {
            case SbtOperation.Compile => "compile"
            case SbtOperation.Test => "test"
            case SbtOperation.TestOnly => "test-only"
          })
          request.hcursor.get[String]("workingDirectory").toOption shouldBe Some(fixture.root.resolve("checkout").toRealPath().toString)
          request.hcursor.get[String]("executable").toOption shouldBe Some(Paths.get("/usr/bin/true").toRealPath().toString)
          request.hcursor.get[Vector[String]]("arguments").toOption shouldBe Some(Vector(
            "--batch", "-Dsbt.server.autostart=false", "-Dsbt.supershell=false", "-Dsbt.color=false",
            fixture.operation match {
              case SbtOperation.Compile => "compile"
              case SbtOperation.Test => "test"
              case SbtOperation.TestOnly => "testOnly org.example.SelectedSpec"
            }))
          request.hcursor.get[Map[String, String]]("environment").toOption shouldBe Some(Map("BUILD_PROFILE" -> "test-owned"))
          request.hcursor.get[String]("programIdentity").toOption shouldBe Some("test-owned-build")
          request.hcursor.get[Long]("executionTimeoutMillis").toOption shouldBe Some(2000L)
          request.hcursor.get[Long]("stdoutBytes").toOption shouldBe Some(65536L)
          request.hcursor.get[Long]("stderrBytes").toOption shouldBe Some(65536L)
          request.hcursor.get[String]("responseFile").toOption shouldBe Some(fixture.configuration.responseFile.toString)
          result.termination shouldBe ProcessExecutionTermination.Exited(0)
          result.stdout shouldBe ProcessExecutionCapture(_stdout, _stdout.size.toLong, false)
          result.stderr shouldBe ProcessExecutionCapture(_stderr, _stderr.size.toLong, false)
          result.elapsedMillis shouldBe 17L
          result.safeProgramIdentity shouldBe "test-owned-build"
          result.artifacts shouldBe empty
          fixture.calls.get shouldBe 0
          if (fixture.operation == SbtOperation.TestOnly)
            SbtExecutionAdmission.decode(SbtTarget(fixture.workspace), fixture.operation, fixture.execution, result).testSelection shouldBe
              Some(SbtTestSelectionResult.Verified(Vector("org.example.SelectedSpec"), 1, 1L))
        }
      } finally fixtures.foreach(_close)
    }

    "name consecutive checks deterministically and delegate non-SBT without consuming a sequence" in {
      Given("one driver instance, exact base paths and a separately admitted non-SBT fixture")
      val fixture = _fixture()
      val generic = _take(ProcessExecutionTestProfile.admittedC(
        _take(ProcessCapabilityId.parseC("test-owned-generic")),
        ProcessExecutionResult(ProcessExecutionTermination.Exited(0), ProcessExecutionCapture(Vector.empty, 0L, false),
          ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 0L, "unused")))
      try {
        When("two SBT checks surround a delegated generic start")
        val firsthandle = _take(fixture.driver.startC(fixture.execution))
        val first = _request(fixture)
        _write_response(first, _response(first))
        val firstresult = firsthandle.awaitC
        val delegated = _take(fixture.driver.startC(generic.execution)).awaitC
        val secondhandle = _take(fixture.driver.startC(fixture.execution))
        val second = _request(fixture, 2)
        _write_response(second, _response(second))
        val secondresult = secondhandle.awaitC
        Then("only SBT allocates sequence numbers, each request has its own UUID, and all prior files remain")
        firstresult.isSuccess shouldBe true
        secondresult.isSuccess shouldBe true
        delegated.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed)
        fixture.calls.get shouldBe 1
        first.hcursor.get[String]("requestId").toOption should not be second.hcursor.get[String]("requestId").toOption
        second.hcursor.get[String]("responseFile").toOption shouldBe Some(fixture.configuration.responseFile.toString + ".2")
        _request(fixture) shouldBe first
        Files.readString(fixture.configuration.responseFile) shouldBe _response(first).noSpaces
        Files.exists(Paths.get(fixture.configuration.responseFile.toString + ".2")) shouldBe true
      } finally _close(fixture)
    }

    "keep an existing request untouched and never fall back to local SBT execution" in {
      Given("an already occupied request path for the first issued check")
      val fixture = _fixture()
      Files.writeString(fixture.configuration.requestFile, "earlier invocation")
      try {
        When("the admitted tagged start tries to publish its one request")
        val result = fixture.driver.startC(fixture.execution)
        Then("start fails structurally, preserves the earlier file and invokes no local launcher")
        result.isFaillure shouldBe true
        Files.readString(fixture.configuration.requestFile) shouldBe "earlier invocation"
        fixture.calls.get shouldBe 0
      } finally _close(fixture)
    }

    "retain exactly one complete request when independent contexts publish concurrently to the same path" in {
      Given("two independently admitted contexts with distinct request UUIDs and the same host-file configuration")
      val fixtures = Vector(_fixture(SbtOperation.Compile), _fixture(SbtOperation.Test))
      val configuration = fixtures.head.configuration
      val barrier = new CyclicBarrier(2)
      val executor = Executors.newFixedThreadPool(2)
      try {
        When("both contexts attempt publication after the same start barrier")
        val pending = fixtures.map { fixture =>
          val requestid = UUID.randomUUID().toString
          executor.submit(new Callable[(Fixture, String, Consequence[Unit])] {
            def call(): (Fixture, String, Consequence[Unit]) = {
              barrier.await(5L, TimeUnit.SECONDS)
              val metadata = fixture.execution.definition._sbt_metadata.get
              val directory = _take(metadata.binding.workingDirectoryC)
              (fixture, requestid, RegisteredSbtHostDriver.publishRequestC(
                fixture.execution, metadata, directory, configuration, requestid))
            }
          })
        }
        val results = pending.map(_.get(5L, TimeUnit.SECONDS))
        Then("one publication succeeds and the other fails without replacing the winner or launching a local process")
        results.count(_._3.isSuccess) shouldBe 1
        results.count(_._3.isFaillure) shouldBe 1
        val (winner, requestid, _) = results.find(_._3.isSuccess).get
        val retained = _request(fixtures.head)
        retained shouldBe Json.obj(
          "schema" -> Json.fromString("cncf.registered-sbt-request.v1"),
          "requestId" -> Json.fromString(requestid),
          "workspaceId" -> Json.fromString(winner.workspace.print),
          "operation" -> Json.fromString(if (winner.operation == SbtOperation.Compile) "compile" else "test"),
          "programIdentity" -> Json.fromString("test-owned-build"),
          "workingDirectory" -> Json.fromString(winner.root.resolve("checkout").toRealPath().toString),
          "executable" -> Json.fromString(Paths.get("/usr/bin/true").toRealPath().toString),
          "arguments" -> Json.fromValues(winner.execution.effectiveArguments.map(Json.fromString)),
          "environment" -> Json.obj("BUILD_PROFILE" -> Json.fromString("test-owned")),
          "executionTimeoutMillis" -> Json.fromLong(2000L),
          "stdoutBytes" -> Json.fromLong(65536L),
          "stderrBytes" -> Json.fromLong(65536L),
          "responseFile" -> Json.fromString(configuration.responseFile.toString))
        fixtures.foreach(_.calls.get shouldBe 0)
      } finally {
        executor.shutdownNow()
        fixtures.foreach(_close)
      }
    }

    "report a missing host response as uncertainty and cache that observation without replay" in {
      Given("a published admitted intent with a short finite timeout and no host receipt")
      val fixture = _fixture(timeout = 30L)
      try {
        val handle = _take(fixture.driver.startC(fixture.execution))
        When("the actual wait reaches its response deadline")
        val result = handle.awaitC
        val retained = handle.awaitC
        Then("there is no synthetic process timeout or safe-to-retry claim")
        result.isFaillure shouldBe true
        _failure_conclusion(result).show should include ("external execution state is unknown")
        retained shouldBe result
        Files.exists(fixture.configuration.requestFile) shouldBe true
        Files.exists(fixture.configuration.responseFile) shouldBe false
        fixture.calls.get shouldBe 0
      } finally _close(fixture)
    }

    "cancel an active local wait without claiming that the registered runner stopped" in {
      Given("a managed host handle awaiting an external response on a test-owned thread")
      val fixture = _fixture()
      val handle = _take(fixture.driver.startC(fixture.execution))
      val executor = Executors.newSingleThreadExecutor()
      val waiting = new CountDownLatch(1)
      val observed = executor.submit(new Callable[Consequence[ProcessExecutionResult]] {
        def call(): Consequence[ProcessExecutionResult] = {
          waiting.countDown()
          handle.awaitC
        }
      })
      try {
        waiting.await(1L, TimeUnit.SECONDS) shouldBe true
        When("the owning lifecycle cancels that handle")
        val cancelled = handle.cancelC
        val result = observed.get(1L, TimeUnit.SECONDS)
        Then("waiting ends as unknown external execution, while the issued request remains available to its host")
        cancelled.isSuccess shouldBe true
        result.isFaillure shouldBe true
        _failure_conclusion(result).show should include ("external execution state is unknown")
        Files.exists(fixture.configuration.requestFile) shouldBe true
        Files.exists(fixture.configuration.responseFile) shouldBe false
      } finally {
        executor.shutdownNow()
        _close(fixture)
      }
    }

    "refuse crossed, malformed, unproven and oversized host observations as uncertainty" in {
      Given("explicitly test-owned responses that violate one binding, proof, encoding or capture requirement")
      val changes: Vector[Json => String] = Vector(
        json => json.mapObject(_.add("requestId", Json.fromString(UUID.randomUUID().toString))).noSpaces,
        json => json.mapObject(_.add("programIdentity", Json.fromString("other-build"))).noSpaces,
        json => json.mapObject(_.add("processTerminated", Json.False)).noSpaces,
        json => json.mapObject(_.remove("processTerminated")).noSpaces,
        json => json.mapObject(_.add("processTerminated", Json.fromString("true"))).noSpaces,
        json => json.mapObject(_.remove("processTerminated").add("lockReleased", Json.True)).noSpaces,
        json => json.mapObject(_.remove("runner")).noSpaces,
        json => json.mapObject(_.add("runner", json.hcursor.downField("runner").focus.get.mapObject(
          _.add("model", Json.fromString("other-model"))))).noSpaces,
        json => json.mapObject(_.add("runner", json.hcursor.downField("runner").focus.get.mapObject(
          _.add("agentId", Json.fromString(""))))).noSpaces,
        json => json.mapObject(_.add("commandRequest", Json.fromString(""))).noSpaces,
        json => json.mapObject(_.add("commandResult", Json.fromString(""))).noSpaces,
        json => json.mapObject(_.add("stdoutBase64", Json.fromString("not base64!"))).noSpaces,
        json => json.mapObject(_.add("stdoutBase64", Json.fromString(Base64.getEncoder.encodeToString(new Array[Byte](65537))))).noSpaces,
        json => json.mapObject(_.add("elapsedMillis", Json.fromLong(-1L))).noSpaces,
        json => json.mapObject(_.add("exitCode", Json.fromDoubleOrNull(0.5))).noSpaces,
        json => json.mapObject(_.add("exceededStream", Json.fromString("stdout"))).noSpaces,
        json => json.mapObject(_.add("unknown", Json.True)).noSpaces,
        json => json.noSpaces.replace("\"processTerminated\":true", "\"processTerminated\":true,\"processTerminated\":true"),
        _ => "{malformed")
      When("each invalid observation is read at the trusted response boundary")
      val results = changes.map { change =>
        val fixture = _fixture()
        try {
          val handle = _take(fixture.driver.startC(fixture.execution))
          val request = _request(fixture)
          Files.writeString(fixture.configuration.responseFile, change(_response(request)))
          handle.awaitC
        } finally _close(fixture)
      }
      Then("none produces a process result or a successful build")
      results.foreach(_.isFaillure shouldBe true)
      results.foreach(result => _failure_conclusion(result).show should include ("external execution state is unknown"))
    }

    "preserve genuine test-owned terminal categories and both output-overflow streams" in {
      Given("bound test-owned host receipts with actual process termination proof and explicit incomplete terminal meanings")
      val cases = Vector(
        ("exited", "stdout", 7, ProcessExecutionTermination.Exited(7)),
        ("timed-out", "stdout", 0, ProcessExecutionTermination.TimedOut),
        ("cancelled", "stdout", 0, ProcessExecutionTermination.Cancelled),
        ("output-limit-exceeded", "stdout", 0, ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)),
        ("output-limit-exceeded", "stderr", 0, ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr)))
      When("the bridge decodes each actual test-owned terminal receipt")
      val results = cases.map { case (kind, stream, code, expected) =>
        val fixture = _fixture()
        try {
          val handle = _take(fixture.driver.startC(fixture.execution))
          val request = _request(fixture)
          _write_response(request, _response(request, termination = kind, exitcode = code, stream = stream))
          _take(handle.awaitC) -> expected
        } finally _close(fixture)
      }
      Then("incomplete observations never become exit-zero success and overflow retains its actual stream")
      results.foreach { case (result, expected) =>
        result.termination shouldBe expected
        result.stdout.truncated shouldBe (expected == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
        result.stderr.truncated shouldBe (expected == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr))
      }
    }

    "reject absent overflow stream and forbidden exit fields or NotStarted claims" in {
      Given("test-owned host receipts that cannot express the frozen terminal contract")
      val fixture = _fixture()
      try {
        val invalid: Vector[Json => Json] = Vector(
          _.mapObject(_.add("termination", Json.fromString("output-limit-exceeded")).remove("exitCode")),
          _.mapObject(_.add("termination", Json.fromString("timed-out"))),
          _.mapObject(_.add("termination", Json.fromString("not-started")).remove("exitCode")))
        When("successive issued checks receive those invalid terminal receipts")
        val results = invalid.zipWithIndex.map { case (change, index) =>
          val handle = _take(fixture.driver.startC(fixture.execution))
          val request = _request(fixture, index + 1)
          _write_response(request, change(_response(request)))
          handle.awaitC
        }
        Then("none can manufacture an observed process terminal or safe replay fact")
        results.foreach(_.isFaillure shouldBe true)
      } finally _close(fixture)
    }

    "close the owned local delegate and unblock outstanding host waits as unknown" in {
      Given("a pending tagged request and its runtime-owned local delegate")
      val fixture = _fixture()
      val handle = _take(fixture.driver.startC(fixture.execution))
      val generic = _take(ProcessExecutionTestProfile.admittedC(_take(ProcessCapabilityId.parseC("test-owned-generic")),
        ProcessExecutionResult(ProcessExecutionTermination.Exited(0), ProcessExecutionCapture(Vector.empty, 0L, false),
          ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 0L, "unused")))
      try {
        When("SystemNode ownership closes the decorator")
        fixture.driver.close()
        val pending = handle.awaitC
        val local = fixture.localdriver.startC(generic.execution)
        val tagged = fixture.driver.startC(fixture.execution)
        Then("both new routes are closed and no external-stop receipt is fabricated")
        pending.isFaillure shouldBe true
        local.isFaillure shouldBe true
        tagged.isFaillure shouldBe true
        fixture.calls.get shouldBe 0
      } finally _close(fixture)
    }
  }

  private def _fixture(operation: SbtOperation = SbtOperation.Test, timeout: Long = 2000L): Fixture = {
    val base = Files.createDirectories(Paths.get("target", "cncf", "work", "registered-sbt-host-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-")
    val checkout = Files.createDirectory(root.resolve("checkout"))
    val workspace = _take(WorkspaceId.parseC("test-checkout"))
    val binding = _take(_take(WorkspaceExecutionAdmission.createC(Vector(workspace -> checkout), Vector.empty)).bindingC(workspace))
    val capability = _take(ProcessCapabilityId.parseC("test-owned-build"))
    val limits = ProcessExecutionLimits(Some(1000L), Some(timeout), Some(100L), Some(65536L), Some(65536L),
      Some(65536L), Some(16L), Some(65536L), Some(16L), Some(65536L), Some(65536L))
    val definition = _take(SbtProgramDefinition.createC(binding, operation, capability, "test-owned-build",
      Paths.get("/usr/bin/true"), limits, Map("BUILD_PROFILE" -> "test-owned"),
      if (operation == SbtOperation.TestOnly) Vector("org.example.SelectedSpec") else Vector.empty))
    val policy = _take(ProcessExecutionPolicy.createC(Vector(definition)))
    val execution = _take(policy.resolveSbtC(definition, ProcessExecutionGrant(capability)))
    val configuration = _take(RegisteredSbtHostConfiguration.createC(root.resolve("request.json"), root.resolve("response.json")))
    val calls = new AtomicInteger(0)
    val localdriver = new LocalProcessExecutionDriver("test-owned-local", new LocalProcessLauncher {
      def startBlocking(command: Vector[String]): Process = {
        calls.incrementAndGet()
        throw new IllegalStateException("test-owned generic delegate observation")
      }
    })
    Fixture(root, workspace, operation, execution, configuration, calls, localdriver,
      new RegisteredSbtHostDriver(localdriver, configuration))
  }

  private def _request(fixture: Fixture, sequence: Int = 1): Json = {
    val path = if (sequence == 1) fixture.configuration.requestFile
      else Paths.get(fixture.configuration.requestFile.toString + "." + sequence.toString)
    parse(Files.readString(path)).toOption.get
  }

  private def _response(request: Json, termination: String = "exited", exitcode: Int = 0, stream: String = "stdout"): Json = {
    val base = Json.obj("schema" -> Json.fromString("cncf.registered-sbt-response.v1"),
      "requestId" -> request.hcursor.downField("requestId").focus.get,
      "programIdentity" -> request.hcursor.downField("programIdentity").focus.get,
      "termination" -> Json.fromString(termination),
      "stdoutBase64" -> Json.fromString(Base64.getEncoder.encodeToString(_stdout.toArray)),
      "stderrBase64" -> Json.fromString(Base64.getEncoder.encodeToString(_stderr.toArray)),
      "elapsedMillis" -> Json.fromLong(17L), "processTerminated" -> Json.True,
      "runner" -> Json.obj("logicalRole" -> Json.fromString("cncf_command_runner"),
        "agentId" -> Json.fromString("explicitly-test-owned-host-observation"), "model" -> Json.fromString("gpt-6-luna"),
        "reasoningEffort" -> Json.fromString("medium")),
      "commandRequest" -> Json.fromString("test-owned-command-request"),
      "commandResult" -> Json.fromString("test-owned-command-result"))
    termination match {
      case "exited" => base.mapObject(_.add("exitCode", Json.fromInt(exitcode)))
      case "output-limit-exceeded" => base.mapObject(_.add("exceededStream", Json.fromString(stream)))
      case _ => base
    }
  }

  private def _write_response(request: Json, response: Json): Unit = {
    Files.writeString(Paths.get(request.hcursor.get[String]("responseFile").toOption.get), response.noSpaces)
    ()
  }

  private def _failure_conclusion[A](result: Consequence[A]): Conclusion = result match {
    case Consequence.Failure(conclusion) => conclusion
    case Consequence.Success(value) => fail(s"Expected host observation failure, received success: $value")
  }

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Test-owned host fixture rejected: $failure")
  }

  private def _close(fixture: Fixture): Unit = {
    fixture.driver.close()
    new ProcessExecutionWorkArea(fixture.root).close()
  }
}

object RegisteredSbtHostDriverSpec {
  private final case class Fixture(
    root: Path,
    workspace: WorkspaceId,
    operation: SbtOperation,
    execution: ResolvedProcessExecution,
    configuration: RegisteredSbtHostConfiguration,
    calls: AtomicInteger,
    localdriver: LocalProcessExecutionDriver,
    driver: RegisteredSbtHostDriver
  )
}
