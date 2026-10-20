package org.goldenport.cncf.processexecution

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId}
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
 *  version Jul. 30, 2026
 * @version Oct. 19, 2026
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
    Vector("removed", "non-executable").foreach { condition =>
      s"confirm a $condition admitted SBT executable was not started and permit only fresh dispatch after restoration" in {
        Given(s"an admitted owned executable later made $condition and a controlled successful launcher")
        val root = _sbt_root()
        val cleanup = new ProcessExecutionWorkArea(root)
        val executable = _sbt_executable(root)
        val execution = _sbt_execution(root, SbtOperation.Test, executable = executable)
        val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
        val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
        val driver = new LocalProcessExecutionDriver("unavailable-build", _sbt_launcher(calls, succeed = true))
        if (condition == "removed") Files.delete(executable)
        else Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rw-------"))
        try {
          When("both start variants receive the execution before any launcher submission")
          val handles = Vector(driver.startC(execution), driver.startC(execution, workarea)).map(_.toOption.get)
          val results = handles.map(_.awaitC.toOption.get)

          Then("both completed handles retain empty actual results without launcher calls or active resources")
          results.foreach { result =>
            result.termination shouldBe ProcessExecutionTermination.NotStarted
            result.stdout shouldBe ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
            result.stderr shouldBe ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
            result.artifacts shouldBe empty
            result.elapsedMillis should be >= 0L
            result.elapsedMillis should be < 5000L
            result.safeProgramIdentity shouldBe execution.definition.safeProgramIdentity
          }
          calls shouldBe empty
          driver.activeProcessCount shouldBe 0
          driver.activeHandleCount shouldBe 0

          When("the same owned executable is restored and fresh starts are explicitly requested")
          if (condition == "removed") Files.createFile(executable)
          Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"))
          val fresh = Vector(driver.startC(execution), driver.startC(execution, workarea)).map(_.flatMap(_.awaitC))
          handles.foreach(_.cancelC)
          val retained = handles.map(_.awaitC.toOption.get)

          Then("only the fresh calls dispatch normally and the first handles remain permanently completed")
          fresh.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0)))
          retained shouldBe results
          calls should have size 2
          calls.iterator().asScala.toVector.foreach { case (command, cwd, environment) =>
            command.head shouldBe executable.toRealPath().toString
            command.last shouldBe "test"
            cwd shouldBe root.resolve("checkout").toRealPath()
            environment shouldBe Map("TEXTUS_BUILD_PROFILE" -> "controlled")
          }
          driver.activeProcessCount shouldBe 0
          driver.activeHandleCount shouldBe 0
        } finally { driver.close(); workarea.close(); cleanup.close() }
      }
    }

    "confirm closed-before-start typed SBT without changing generic or Git closure failures" in {
      Given("a closed driver and admitted SBT, generic and Git executions")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val execution = _sbt_execution(root, SbtOperation.Compile)
      val gitfixture = GitExecutionSpecSupport.create()
      val gitexecution = GitExecutionAdmission.resolveC(gitfixture.context, gitfixture.target, GitOperation.Head).toOption.get
      val generic = _execution(Vector("streams", "literal"))
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("closed-build", _sbt_launcher(calls))
      driver.close()
      try {
        When("each execution is submitted through both start variants after closure")
        val typed = Vector(driver.startC(execution), driver.startC(execution, workarea)).map(_.flatMap(_.awaitC))
        val other = Vector(generic, gitexecution).flatMap(value =>
          Vector(driver.startC(value), driver.startC(value, workarea)))

        Then("only typed SBT has confirmed NotStarted results and no call allocates active work")
        typed.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.NotStarted))
        other.foreach(_.isFaillure shouldBe true)
        calls shouldBe empty
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally { driver.close(); workarea.close(); cleanup.close(); gitfixture.close() }
    }

    "reject malformed tagged requests before classifying unavailable executables or closed drivers" in {
      Given("an admitted SBT execution with a forged argv and its owned executable removed after admission")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val executable = _sbt_executable(root)
      val admitted = _sbt_execution(root, SbtOperation.Test, executable = executable)
      val execution = admitted.copy(request = admitted.request.copy(arguments = Vector("unapproved")))
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("malformed-build", _sbt_launcher(calls))
      Files.delete(executable)
      try {
        When("both variants receive the malformed request before and after driver closure")
        val open = Vector(driver.startC(execution), driver.startC(execution, workarea))
        driver.close()
        val closed = Vector(driver.startC(execution), driver.startC(execution, workarea))

        Then("request validation fails structurally instead of producing a NotStarted receipt")
        (open ++ closed).foreach(_.isFaillure shouldBe true)
        calls shouldBe empty
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally { driver.close(); workarea.close(); cleanup.close() }
    }

    "retain LaunchFailed when typed SBT times out after launcher submission" in {
      Given("an admitted SBT execution and a controlled delayed launcher under a finite launch timeout")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val calls = new AtomicInteger(0)
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = throw new IOException("explicit cwd required")
        override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = {
          calls.incrementAndGet()
          Thread.sleep(1000L)
          throw new IOException("delayed controlled launch")
        }
      }
      val execution = _sbt_execution(root, SbtOperation.Test, limits = _limits(launchtimeout = 100L))
      val driver = new LocalProcessExecutionDriver("delayed-build", launcher)
      try {
        When("both start variants submit a launch that cannot complete in time")
        val results = Vector(driver.startC(execution).flatMap(_.awaitC), driver.startC(execution, workarea).flatMap(_.awaitC))

        Then("timeout remains LaunchFailed and never establishes a confirmed unexecuted fact")
        results.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed))
        calls.get shouldBe 2
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally { driver.close(); workarea.close(); cleanup.close() }
    }

    "retain LaunchFailed when typed SBT driver closure occurs after launcher entry" in {
      Given("controlled launchers that close their driver after submission and return a completed process")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val execution = _sbt_execution(root, SbtOperation.Test)
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val drivers = Vector.fill(2) {
        val reference = new AtomicReference[LocalProcessExecutionDriver]()
        val delegate = _sbt_launcher(calls, succeed = true)
        val launcher = new LocalProcessLauncher {
          def startBlocking(command: Vector[String]): Process = throw new IOException("explicit cwd required")
          override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = {
            reference.get.close()
            delegate.startBlocking(command, workingDirectory, environment)
          }
        }
        val driver = new LocalProcessExecutionDriver("closing-build", launcher)
        reference.set(driver)
        driver
      }
      try {
        When("each start variant observes closure only after its launcher has been entered")
        val results = Vector(drivers.head.startC(execution), drivers.last.startC(execution, workarea)).map(_.flatMap(_.awaitC))

        Then("the terminal remains LaunchFailed because a launch was already submitted")
        results.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed))
        calls should have size 2
        drivers.foreach { driver =>
          driver.activeProcessCount shouldBe 0
          driver.activeHandleCount shouldBe 0
        }
      } finally { drivers.foreach(_.close()); workarea.close(); cleanup.close() }
    }

    "send normal Git vectors with fixed environment and canonical workspace cwd through both start variants" in {
      Given("an admitted typed Git Head and a controlled launcher that records direct launch attempts")
      val fixture = GitExecutionSpecSupport.create()
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("controlled-git", _sbt_launcher(calls))
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(fixture.root.resolve("driver-area")))
      val execution = GitExecutionAdmission.resolveC(fixture.context, fixture.target, GitOperation.Head).toOption.get
      try {
        When("the direct and WorkArea variants start the same admitted typed execution")
        val results = Vector(driver.startC(execution), driver.startC(execution, workarea)).map(_.flatMap(_.awaitC))
        Then("both reach the normal Git executable once with no wrapper, fixed argv, environment and actual checkout")
        results.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed))
        val recorded = calls.iterator().asScala.toVector
        recorded should have size 2
        recorded.foreach { case (command, cwd, environment) =>
          command shouldBe Vector(Paths.get("/usr/bin/git").toRealPath().toString) ++ GitProgramDefinition.prefix ++
            Vector("rev-parse", "--verify", "HEAD^{commit}")
          cwd shouldBe fixture.checkout.toRealPath()
          environment shouldBe Map("PATH" -> "/usr/bin:/bin") ++ GitProgramDefinition.fixedEnvironment
        }
        driver.activeHandleCount shouldBe 0
        driver.activeProcessCount shouldBe 0
      } finally { driver.close(); workarea.close(); fixture.close() }
    }

    "deny Git binding invalidation and request override before either launcher entry" in {
      Given("an admitted Git Head, a forged request override and a binding invalidated after admission")
      val fixture = GitExecutionSpecSupport.create()
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("invalid-git", _sbt_launcher(calls))
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(fixture.root.resolve("driver-area")))
      val execution = GitExecutionAdmission.resolveC(fixture.context, fixture.target, GitOperation.Head).toOption.get
      val forged = execution.copy(request = execution.request.copy(arguments = Vector("--help")))
      try {
        When("the driver receives a non-template argument and then the original root is moved before both starts")
        val argument = driver.startC(forged, workarea)
        Files.move(fixture.checkout, fixture.root.resolve("moved-checkout"))
        val roots = Vector(driver.startC(execution), driver.startC(execution, workarea))
        Then("all prelaunch checks fail without any controlled launcher call or live process resource")
        argument.isFaillure shouldBe true
        roots.foreach(_.isFaillure shouldBe true)
        calls shouldBe empty
        driver.activeHandleCount shouldBe 0
        driver.activeProcessCount shouldBe 0
      } finally { driver.close(); workarea.close(); fixture.close() }
    }

    "reclaim normal Git handles and captures after bounded output termination" in {
      Given("an actual temporary Git repository and a finite stdout bound smaller than its root output")
      val fixture = GitExecutionSpecSupport.create()
      GitExecutionSpecSupport.setup(fixture.checkout, "init", "--initial-branch=main")
      val driver = new LocalProcessExecutionDriver()
      val admitted = GitExecutionAdmission.resolveC(fixture.context, fixture.target, GitOperation.Root).toOption.get
      val bounded = admitted.copy(effectiveLimits = admitted.effectiveLimits.copy(stdoutBytes = Some(1L)))
      try {
        When("the normal direct Git process exceeds its bounded stdout capture")
        val result = driver.startC(bounded).flatMap(_.awaitC)
        Then("the shared lifecycle returns an incomplete bounded capture and removes every active handle")
        result.toOption.get.termination shouldBe ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)
        result.toOption.get.stdout.truncated shouldBe true
        result.toOption.get.stdout.content.size shouldBe 1
        driver.activeHandleCount shouldBe 0
        driver.activeProcessCount shouldBe 0
      } finally { driver.close(); fixture.close() }
    }

    "send fixed Compile and Test vectors with the admitted checkout cwd through both start variants" in {
      Given("tagged build definitions and a controlled launcher that records attempts without starting SBT")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("controlled-build", _sbt_launcher(calls))
      val executions = Vector(SbtOperation.Compile, SbtOperation.Test).map(_sbt_execution(root, _))
      try {
        When("each semantic operation uses the direct and UnitOfWork WorkArea driver overloads")
        val results = executions.flatMap { execution =>
          Vector(driver.startC(execution).flatMap(_.awaitC), driver.startC(execution, workarea).flatMap(_.awaitC))
        }

        Then("the controlled launcher receives only the fixed vector, fixed environment and actual checkout")
        results.map(_.toOption.map(_.termination)) shouldBe Vector.fill(4)(Some(ProcessExecutionTermination.LaunchFailed))
        val recorded = calls.iterator().asScala.toVector
        recorded.map(_._2) shouldBe Vector.fill(4)(root.resolve("checkout").toRealPath())
        recorded.map(_._1.last) shouldBe Vector("compile", "compile", "test", "test")
        recorded.foreach { case (command, _, environment) =>
          command shouldBe Vector(Paths.get("/usr/bin/true").toRealPath().toString, "--batch",
            "-Dsbt.server.autostart=false", "-Dsbt.supershell=false", "-Dsbt.color=false",
            command.last)
          environment shouldBe Map("TEXTUS_BUILD_PROFILE" -> "controlled")
        }
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally {
        driver.close()
        workarea.close()
        cleanup.close()
      }
    }

    "keep generic working directories relative to the existing WorkArea" in {
      Given("a generic admitted probe with a nested WorkArea cwd and the same controlled launcher")
      val root = _sbt_root()
      val workarea = new ProcessExecutionWorkArea(root)
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("controlled-generic", _sbt_launcher(calls))
      val execution = _execution(Vector("streams", "literal"),
        workingdirectory = Some(WorkAreaRelativePath.parseC("nested").toOption.get))
      try {
        When("the generic execution starts through the WorkArea overload")
        val result = driver.startC(execution, workarea).flatMap(_.awaitC)

        Then("its working directory still resolves under the WorkArea and its arguments are unchanged")
        result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed)
        calls.iterator().asScala.toVector.map(_._2) shouldBe Vector(root.resolve("nested"))
        calls.iterator().asScala.toVector.map(_._1) shouldBe Vector(_java_path +: execution.effectiveArguments)
      } finally {
        driver.close()
        workarea.close()
      }
    }

    "reject a retargeted workspace before either launcher entry and confirm subsequent driver closure" in {
      Given("an admitted configured root symlink later pointing at a different checkout")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val first = Files.createDirectory(root.resolve("first"))
      val second = Files.createDirectory(root.resolve("second"))
      val configured = Files.createSymbolicLink(root.resolve("configured"), first)
      val execution = _sbt_execution(root, SbtOperation.Test, Some(configured))
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("retargeted-build", _sbt_launcher(calls))
      Files.delete(configured)
      Files.createSymbolicLink(configured, second)
      try {
        When("both driver overloads receive the retargeted binding and the driver then closes")
        val direct = driver.startC(execution)
        val scoped = driver.startC(execution, workarea)
        driver.close()
        val closed = driver.startC(execution).flatMap(_.awaitC)

        Then("binding checks fail before the launcher and a subsequent closed-driver start is confirmed unexecuted")
        Vector(direct, scoped).foreach(_.isFaillure shouldBe true)
        closed.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.NotStarted)
        calls shouldBe empty
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
        Files.isDirectory(first) shouldBe true
        Files.isDirectory(second) shouldBe true
      } finally {
        driver.close()
        workarea.close()
        cleanup.close()
      }
    }

    "reject every mixed WorkArea input and request override for a tagged build before launch" in {
      Given("one admitted Test intent and forged requests carrying argv, cwd, stdin, files, trees or outputs")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val execution = _sbt_execution(root, SbtOperation.Test)
      val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
      val path = WorkAreaRelativePath.parseC("input.txt").toOption.get
      val name = ProcessArtifactName.parseC("input").toOption.get
      val file = ProcessExecutionInputFile.createC(name, path, Vector(1.toByte), 16L).toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(name, path, ProcessArtifactKind.File, 16L).toOption.get
      val reference = ResourceTreeReference.parseC("fixtures").toOption.get
      val treelimits = ResourceTreeLimits(maxDepth = 1, maxEntries = 1, maxFileBytes = 16L, maxTotalBytes = 16L)
      val entry = ResourceTreeEntry.createC("input.txt", Vector(1.toByte)).toOption.get
      val snapshot = ResourceTreeAccess.inMemory(Map(reference -> Vector(entry))).snapshot(reference, treelimits).toOption.get
      val tree = ProcessExecutionResourceTreeInput.createC(snapshot, path).toOption.get
      val requests = Vector(
        execution.request.copy(arguments = Vector("runMain hostile.Main")),
        execution.request.copy(workingDirectory = Some(path)),
        execution.request.copy(input = ProcessExecutionInput.Bytes(Vector(1.toByte))),
        execution.request.copy(input = ProcessExecutionInput.WorkAreaFile(path)),
        execution.request.copy(inputFiles = Vector(file)),
        execution.request.copy(resourceTrees = Vector(tree)),
        execution.request.copy(outputs = Vector(output)),
        execution.request.copy(requestedLimits = ProcessExecutionLimits(stdoutBytes = Some(8L)))
      )
      val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
      val driver = new LocalProcessExecutionDriver("mixed-build", _sbt_launcher(calls))
      try {
        When("program policy and both local driver overloads receive the unsupported mixed inputs")
        val policyresults = requests.map(execution.definition.validateRequestC(_, execution.effectiveLimits))
        val driverresults = requests.flatMap { request =>
          val changed = execution.copy(request = request)
          Vector(driver.startC(changed), driver.startC(changed, workarea))
        }

        Then("every mixed request is denied without a launcher call or checkout projection")
        policyresults.foreach(_.isFaillure shouldBe true)
        driverresults.foreach(_.isFaillure shouldBe true)
        calls shouldBe empty
        Files.exists(root.resolve("checkout").resolve(path.value)) shouldBe false
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally {
        driver.close()
        workarea.close()
        cleanup.close()
      }
    }

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

  private def _sbt_root(): Path = {
    val base = Files.createDirectories(Paths.get("target", "sbt-local-driver-spec").toAbsolutePath)
    Files.createTempDirectory(base, "case-")
  }

  private def _sbt_launcher(
    calls: ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])],
    succeed: Boolean = false
  ): LocalProcessLauncher = new LocalProcessLauncher {
    def startBlocking(command: Vector[String]): Process =
      throw new IOException("controlled launcher requires an explicit working directory")

    override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = {
      calls.add((command, workingDirectory, environment))
      if (succeed) new Process {
        def getInputStream = new ByteArrayInputStream(Array.emptyByteArray)
        def getErrorStream = new ByteArrayInputStream(Array.emptyByteArray)
        def getOutputStream = new ByteArrayOutputStream()
        def waitFor(): Int = 0
        def exitValue(): Int = 0
        def destroy(): Unit = ()
        override def isAlive(): Boolean = false
      }
      else throw new IOException("controlled launch failure; no host process is created")
    }
  }

  private def _sbt_executable(root: Path): Path = {
    val executable = Files.createFile(root.resolve("owned-sbt"))
    Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"))
    executable
  }

  private def _sbt_execution(
    root: Path,
    operation: SbtOperation,
    configuredroot: Option[Path] = None,
    executable: Path = Paths.get("/usr/bin/true"),
    limits: ProcessExecutionLimits = _limits()
  ): ResolvedProcessExecution = {
    val checkout = configuredroot.getOrElse(Files.createDirectories(root.resolve("checkout")))
    val binding = WorkspaceBinding.createC(WorkspaceId.parseC("project").toOption.get, checkout).toOption.get
    val capability = ProcessCapabilityId.parseC("project-build").toOption.get
    val definition = SbtProgramDefinition.createC(binding, operation, capability, "project-build",
      executable, limits, Map("TEXTUS_BUILD_PROFILE" -> "controlled")).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    policy.resolveSbtC(definition, ProcessExecutionGrant(capability)).toOption.get
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
