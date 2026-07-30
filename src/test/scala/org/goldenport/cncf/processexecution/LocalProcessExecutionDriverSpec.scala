package org.goldenport.cncf.processexecution

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.resource.{ResourceTreeAccess, ResourceTreeEntry, ResourceTreeLimits, ResourceTreeReference}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 34 controlled local Process Execution.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 30, 2026
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

    "supply only fixed runtime-owned environment values after clearing ambient process state" in {
      Given("a local JVM probe and a Process definition with one fixed environment binding")
      val execution = _execution(
        Vector("environment", "TEXTUS_TEST_VALUE"),
        environment = Map("TEXTUS_TEST_VALUE" -> "runtime-owned")
      )
      val driver = new LocalProcessExecutionDriver()

      When("the runtime starts the resolved execution without a caller environment map")
      val result = try {
        for {
          handle <- driver.startC(execution)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
      }

      Then("the child receives the fixed runtime value and no component request controls environment")
      result.toOption.map(x => _text(x.stdout)) shouldBe Some("runtime-owned")
      classOf[ProcessExecutionRequest].getDeclaredFields.map(_.getName) should not contain "environment"
    }

    "reject a controlled launcher that has not opted into fixed runtime environment bindings" in {
      Given("a legacy controlled launcher and a Process definition with a fixed environment binding")
      val calls = new AtomicInteger(0)
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = {
          calls.incrementAndGet()
          throw new IOException("legacy launcher should not receive an environment-bound launch")
        }
      }
      val execution = _execution(
        Vector("streams", "literal"),
        environment = Map("TEXTUS_TEST_VALUE" -> "runtime-owned")
      )
      val driver = new LocalProcessExecutionDriver("legacy-local-process", launcher)

      When("the runtime starts the environment-bound execution")
      val result = try {
        for {
          handle <- driver.startC(execution)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
      }

      Then("the launcher fails before its environment-oblivious method can run")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed)
      calls.get shouldBe 0
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

    "reject WorkArea-dependent requests that bypass the runtime-owned WorkArea" in {
      Given("an admitted request whose input requires a runtime-owned WorkArea")
      val path = WorkAreaRelativePath.parseC("input/request.json").toOption.get
      val execution = _execution(Vector("streams", "literal"), input = ProcessExecutionInput.WorkAreaFile(path))
      val driver = new LocalProcessExecutionDriver()

      When("the local driver is asked to start the request without the WorkArea overload")
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

    "reject admitted resource trees when the WorkArea overload is bypassed" in {
      Given("an admitted logical resource tree on an otherwise resolved local Process request")
      val reference = ResourceTreeReference.parseC("fixtures").toOption.get
      val target = WorkAreaRelativePath.parseC("fixtures").toOption.get
      val limits = ResourceTreeLimits(maxDepth = 1, maxEntries = 1, maxFileBytes = 16L, maxTotalBytes = 16L)
      val entry = ResourceTreeEntry.createC("request.txt", Vector(1.toByte)).toOption.get
      val snapshot = ResourceTreeAccess.inMemory(Map(reference -> Vector(entry))).snapshot(reference, limits).toOption.get
      val tree = ProcessExecutionResourceTreeInput.createC(snapshot, target).toOption.get
      val execution = _execution(
        Vector("streams", "literal"),
        resourceTrees = Vector(tree),
        allowedresourcetrees = Map(reference -> limits)
      )
      val driver = new LocalProcessExecutionDriver()

      When("the driver is called without the runtime-owned WorkArea")
      val result = try {
        driver.startC(execution)
      } finally {
        driver.close()
      }

      Then("the driver rejects the tree rather than creating a host-materialized input")
      result.isFaillure shouldBe true
      driver.activeProcessCount shouldBe 0
      driver.activeHandleCount shouldBe 0
    }

    "run against an execution WorkArea without exposing host paths in logical artifacts" in {
      Given("a runtime-owned WorkArea, a declared output, and a WorkArea-backed stdin file")
      val inputpath = WorkAreaRelativePath.parseC("input/request.txt").toOption.get
      val outputpath = WorkAreaRelativePath.parseC("output/result.txt").toOption.get
      val artifactname = ProcessArtifactName.parseC("result").toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(
        artifactname,
        outputpath,
        ProcessArtifactKind.File,
        16L
      ).toOption.get
      val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
      Files.createDirectories(workspace.root.resolve("input"))
      Files.write(workspace.root.resolve(inputpath.value), "request".getBytes(StandardCharsets.UTF_8))
      val execution = _execution(
        Vector("copy-stdin-to-file", outputpath.value),
        input = ProcessExecutionInput.WorkAreaFile(inputpath),
        outputs = Vector(output)
      )
      val driver = new LocalProcessExecutionDriver()

      When("the local driver is supplied the UnitOfWork-owned WorkArea")
      val result = try {
        for {
          handle <- driver.startC(execution, workspace)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
        workspace.close()
      }

      Then("the process uses the scoped working directory and returns only bounded logical metadata")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0))
      result.toOption.map(_.artifacts.map(_.name.print)) shouldBe Some(Vector("result"))
      result.toOption.map(_.artifacts.map(_.byteCount)) shouldBe Some(Vector(7L))
      result.toOption.map(_.artifacts.toString).getOrElse("") should not include workspace.root.toString
      Files.exists(workspace.root) shouldBe false
    }

    "resolve declared output paths beneath an explicitly selected WorkArea working directory" in {
      Given("an execution with a nested WorkArea working directory and a relative declared output")
      val workingdirectory = WorkAreaRelativePath.parseC("work").toOption.get
      val outputpath = WorkAreaRelativePath.parseC("result.txt").toOption.get
      val artifactname = ProcessArtifactName.parseC("result").toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(
        artifactname,
        outputpath,
        ProcessArtifactKind.File,
        16L
      ).toOption.get
      val workspace = ProcessExecutionWorkArea.allocateC(WorkAreaSpace.create(RuntimeConfig.default)).toOption.get
      val execution = _execution(
        Vector("write-file", outputpath.value, "ok"),
        outputs = Vector(output),
        workingdirectory = Some(workingdirectory)
      )
      val driver = new LocalProcessExecutionDriver()

      When("the process writes its output relative to the selected directory")
      val result = try {
        for {
          handle <- driver.startC(execution, workspace)
          completed <- handle.awaitC
        } yield completed
      } finally {
        driver.close()
        workspace.close()
      }

      Then("artifact collection resolves the declared path beneath that directory")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0))
      result.toOption.map(_.artifacts.map(_.name.print)) shouldBe Some(Vector("result"))
      result.toOption.map(_.artifacts.map(_.byteCount)) shouldBe Some(Vector(2L))
      Files.exists(workspace.root) shouldBe false
    }
  }

  private def _execution(
    arguments: Vector[String],
    limits: ProcessExecutionLimits = _limits(),
    input: ProcessExecutionInput = ProcessExecutionInput.Empty,
    outputs: Vector[ProcessExecutionOutputDeclaration] = Vector.empty,
    workingdirectory: Option[WorkAreaRelativePath] = None,
    resourceTrees: Vector[ProcessExecutionResourceTreeInput] = Vector.empty,
    allowedresourcetrees: Map[ResourceTreeReference, ResourceTreeLimits] = Map.empty,
    environment: Map[String, String] = Map.empty
  ): ResolvedProcessExecution = {
    val capability = ProcessCapabilityId.parseC("local-probe").toOption.get
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "local-probe",
      _java_path,
      _fixed_arguments,
      ProcessArgumentPolicy(_fixed_arguments, arguments.toSet),
      limits,
      outputs.map(_.name).toSet,
      allowsworkingdirectory = workingdirectory.nonEmpty,
      allowedresourcetrees = allowedresourcetrees,
      environment = environment
    ).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    policy.resolveC(
      ProcessExecutionRequest(
        capability,
        arguments = arguments,
        input = input,
        workingDirectory = workingdirectory,
        outputs = outputs,
        resourceTrees = resourceTrees
      ),
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
