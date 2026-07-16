package org.goldenport.cncf.processexecution

import java.io.IOException
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 34 controlled local Process Execution.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class LocalProcessExecutionDriverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _exit_codes = Gen.choose(1, 15)
  private val _flooding_streams = Gen.oneOf(
    "flood-stdout" -> ProcessExecutionStream.Stdout,
    "flood-stderr" -> ProcessExecutionStream.Stderr
  )
  private val _java_path =
    java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString
  private val _probe_classpath =
    java.nio.file.Paths.get(
      classOf[LocalProcessExecutionDriverSpec].getProtectionDomain.getCodeSource.getLocation.toURI
    ).toString
  private val _fixed_arguments = Vector(
    "-cp",
    _probe_classpath,
    "org.goldenport.cncf.processexecution.ProcessExecutionLocalDriverProbe"
  )

  "Local Process Execution driver" should {
    "launch an approved argument vector directly and drain stdout and stderr concurrently" in {
      Given("a local JVM probe and a literal value that a shell would normally expand")
      val execution = _execution(Vector("streams", "$HOME"))
      val driver = new LocalProcessExecutionDriver()

      When("the runtime starts the resolved capability-bound execution without a shell")
      val result = try {
        for {
          handle <- driver.startC(execution)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
      }

      Then("the probe receives the literal argument and both independently captured streams")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0))
      result.toOption.map(x => _text(x.stdout)) shouldBe Some("stdout:$HOME")
      result.toOption.map(x => _text(x.stderr)) shouldBe Some("stderr")
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }

    "retain provider-interpretable non-zero exits without converting them to framework failure" in {
      Given("generated non-zero exit codes for an approved local probe")
      val property = Prop.forAll(_exit_codes) { exitcode =>
        val driver = new LocalProcessExecutionDriver()
        val result = try {
          for {
            handle <- driver.startC(_execution(Vector("exit", exitcode.toString)))
            completed <- handle.awaitC
          } yield completed
        } finally {
          driver.close()
        }
        result.toOption.exists(_.termination == ProcessExecutionTermination.Exited(exitcode)) &&
        driver.activeProcessCount == 0 &&
        driver.activeHandleCount == 0
      }

      When("each process completes with its requested exit code")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("each outcome remains a successful Process Execution terminal result")
      checked.passed shouldBe true
    }

    "terminate a timed out process and remove it from runtime tracking" in {
      Given("a long-running local probe under a short execution timeout")
      val execution = _execution(
        Vector("sleep", "10000"),
        _limits(executiontimeout = 50L, terminationgrace = 100L)
      )
      val driver = new LocalProcessExecutionDriver()

      When("the handle is awaited beyond its effective execution timeout")
      val result = try {
        for {
          handle <- driver.startC(execution)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
      }

      Then("the process receives termination and no active process remains")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.TimedOut)
      result.toOption.exists(_.elapsedMillis < 1000L) shouldBe true
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }

    "cancel an active process idempotently and return a cancelled terminal result" in {
      Given("a long-running local probe with a started Process Execution handle")
      val execution = _execution(Vector("sleep", "10000"))
      val driver = new LocalProcessExecutionDriver()

      When("runtime cancellation is requested repeatedly before awaiting completion")
      val result = try {
        for {
          handle <- driver.startC(execution)
          _ <- handle.cancelC
          _ <- handle.cancelC
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
      }

      Then("cancellation terminates the process once and removes it from tracking")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Cancelled)
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }

    "close an unawaited handle without retaining a process or worker lifecycle" in {
      Given("a started long-running local process whose caller abandons the handle")
      val execution = _execution(Vector("sleep", "10000"))
      val driver = new LocalProcessExecutionDriver()
      val handle = driver.startC(execution).toOption.get

      When("the runtime driver itself is closed")
      driver.close()

      Then("the active process and its local handle are both removed")
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
      handle.cancelC.isSuccess shouldBe true
    }

    "refuse a new local process request after driver lifecycle closure" in {
      Given("a closed local Process Execution driver")
      val driver = new LocalProcessExecutionDriver()
      driver.close()

      When("a resolved execution is submitted after closure")
      val result = driver.startC(_execution(Vector("streams", "literal")))

      Then("the runtime returns a structured unavailable-driver failure without retaining work")
      result.isFaillure shouldBe true
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }

    "bound either flooding output stream while still draining it to terminal completion" in {
      Given("generated stdout/stderr flooding probes and independent small capture limits")
      val property = Prop.forAll(_flooding_streams) { case (argument, stream) =>
        val execution = _execution(
          Vector(argument),
          _limits(stdoutbytes = 128L, stderrbytes = 128L, executiontimeout = 5000L, terminationgrace = 100L)
        )
        val driver = new LocalProcessExecutionDriver()
        val result = try {
          for {
            handle <- driver.startC(execution)
            completed <- handle.awaitC
          } yield completed
        } finally {
          driver.close()
        }
        val capture = result.toOption.map { value =>
          if (stream == ProcessExecutionStream.Stdout) value.stdout else value.stderr
        }
        result.toOption.exists(_.termination == ProcessExecutionTermination.OutputLimitExceeded(stream)) &&
        capture.exists(x => x.byteCount <= 128L && x.truncated) &&
        driver.activeProcessCount == 0 &&
        driver.activeHandleCount == 0
      }

      When("each bounded reader reaches its independent stream limit")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("the terminal result identifies the limited stream and retains only its bounded capture")
      checked.passed shouldBe true
    }

    "apply launch timeout before a delayed runtime launcher can create a process" in {
      Given("a controlled launcher that does not return before the launch timeout")
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = {
          Thread.sleep(1000L)
          throw new IOException("delayed launch")
        }
      }
      val execution = _execution(Vector("streams", "literal"), _limits(launchtimeout = 25L))
      val driver = new LocalProcessExecutionDriver("slow-local-process", launcher)

      When("the runtime starts the execution through the delayed launcher")
      val result = try {
        for {
          handle <- driver.startC(execution)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
      }

      Then("launch timeout is a terminal process outcome and no process is retained")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed)
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }

    "reject WorkArea-dependent requests before selecting a local host process" in {
      Given("an admitted request whose input requires the next WorkArea slice")
      val path = WorkAreaRelativePath.parseC("input/request.json").toOption.get
      val execution = _execution(Vector("streams", "literal"), input = ProcessExecutionInput.WorkAreaFile(path))
      val driver = new LocalProcessExecutionDriver()

      When("the current local driver is asked to start that unresolved WorkArea request")
      val result = try {
        driver.startC(execution)
      } finally {
        driver.close()
      }

      Then("it reports a structured unsupported policy result without starting a process")
      result.isFaillure shouldBe true
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }
  }

  private def _execution(
    arguments: Vector[String],
    limits: ProcessExecutionLimits = _limits(),
    input: ProcessExecutionInput = ProcessExecutionInput.Empty
  ): ResolvedProcessExecution = {
    val capability = ProcessCapabilityId.parseC("local-probe").toOption.get
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "local-probe",
      _java_path,
      _fixed_arguments,
      ProcessArgumentPolicy(_fixed_arguments, arguments.toSet),
      limits,
      Set.empty
    ).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    policy.resolveC(
      ProcessExecutionRequest(capability, arguments = arguments, input = input),
      ProcessExecutionGrant(capability)
    ).toOption.get
  }

  private def _limits(
    launchtimeout: Long = 5000L,
    executiontimeout: Long = 5000L,
    terminationgrace: Long = 500L,
    stdoutbytes: Long = 65536L,
    stderrbytes: Long = 65536L
  ): ProcessExecutionLimits =
    ProcessExecutionLimits(
      launchTimeoutMillis = Some(launchtimeout),
      executionTimeoutMillis = Some(executiontimeout),
      terminationGraceMillis = Some(terminationgrace),
      stdinBytes = Some(65536L),
      stdoutBytes = Some(stdoutbytes),
      stderrBytes = Some(stderrbytes),
      argumentCount = Some(16L),
      argumentBytes = Some(65536L),
      artifactCount = Some(16L),
      artifactBytes = Some(65536L),
      workAreaBytes = Some(65536L)
    )

  private def _text(capture: ProcessExecutionCapture): String =
    new String(capture.content.toArray, "UTF-8")
}
