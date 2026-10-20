package org.goldenport.cncf.processexecution

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.{ConcurrentLinkedQueue, FutureTask, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.resource.{ResourceTreeAccess, ResourceTreeEntry, ResourceTreeLimits, ResourceTreeReference}
import org.goldenport.cncf.subsystem.SystemNode
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
 * @version Oct. 20, 2026
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
    "automatically cancel a generic UnitOfWork process through the runtime driver stop entry" in {
      Given("an ordinary UnitOfWork process and its runtime-owned local driver without an application cancellation callback")
      val driver = new LocalProcessExecutionDriver()
      GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
      val base = ExecutionContext.create()
      val scope = ScopeContext(kind = ScopeKind.Runtime, name = "runtime-stop-spec", parent = None,
        observabilitycontext = base.observability, processexecutiondriveroption = Some(driver))
      val uow = new UnitOfWork(base.withScope(scope))
      val execution = _execution(Vector("10"), _limits(executiontimeout = 20000L),
        executable = "/bin/sleep", fixedarguments = Vector.empty)
      val result = new AtomicReference[org.goldenport.Consequence[ProcessExecutionResult]]()
      val worker = new Thread(() => {
        result.set(new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ProcessExec(execution)))
      })
      worker.setDaemon(true)
      try {
        When("the runtime stop entry is called while the actual interpreter owns an active process handle")
        worker.start()
        _await_condition(driver.activeHandleCount == 1, 5000L)
        val stopped = driver.requestStopC()
        worker.join(5000L)
        Then("the ordinary UnitOfWork returns its actual cancelled receipt and completes normal resource finalization")
        stopped.isSuccess shouldBe true
        worker.isAlive shouldBe false
        Option(result.get).flatMap(_.toOption).map(_.termination) shouldBe Some(ProcessExecutionTermination.Cancelled)
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
        driver.terminationConfirmed shouldBe true
      } finally {
        driver.close()
        worker.join(5000L)
        uow.dispose()
      }
    }

    "retain captures and the owning await when a runtime stop closes admission" in {
      Given("an admitted SBT handle whose real local process writes both streams before waiting")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val ready = root.resolve("ready")
      val script = Files.writeString(root.resolve("waiting.sh"), """trap 'exit 0' TERM
printf 'retained-output'
printf 'retained-error' >&2
printf ready > "$1"
while :; do /bin/sleep 1; done
""")
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = throw new IOException("explicit cwd required")
        override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process =
          LocalProcessLauncher.local.startBlocking(Vector("/bin/sh", script.toString, ready.toString), workingDirectory, environment)
      }
      val driver = new LocalProcessExecutionDriver("stopping-build", launcher)
      val execution = _sbt_execution(root, SbtOperation.Test, limits = _limits(executiontimeout = 10000L))
      try {
        When("the runtime requests stop after output is written and before the owning handle is awaited")
        val handle = driver.startC(execution).toOption.get
        _await_condition(Files.exists(ready), 3000L)
        val stopped = driver.requestStopC()
        val retained = driver.activeHandleCount
        val refused = driver.startC(execution).flatMap(_.awaitC)
        val result = handle.awaitC.toOption.get
        val repeated = handle.awaitC.toOption.get
        Then("admission is closed while the original await preserves cancellation, captures and normal cleanup")
        stopped.isSuccess shouldBe true
        retained shouldBe 1
        refused.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.NotStarted)
        result.termination shouldBe ProcessExecutionTermination.Cancelled
        _text(result.stdout) shouldBe "retained-output"
        _text(result.stderr) shouldBe "retained-error"
        repeated shouldBe result
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
        driver.terminationConfirmed shouldBe true
      } finally { driver.close(); cleanup.close() }
    }

    "force a retained TERM-resistant grandchild after its root exits before the grace expires" in {
      Given("a real owned root, child and TERM-resistant grandchild with explicit fixture PID files")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val grandpid = root.resolve("grandchild.pid")
      val childpid = root.resolve("child.pid")
      val grand = Files.writeString(root.resolve("grandchild.sh"), """trap '' TERM
printf 'grandchild-output'
printf '%s' "$$" > "$1"
while :; do :; done
""")
      val child = Files.writeString(root.resolve("child.sh"), """trap 'exit 0' TERM
/bin/sh "$1" "$2" &
printf '%s' "$$" > "$3"
wait
""")
      val script = Files.writeString(root.resolve("root.sh"), """trap 'exit 0' TERM
/bin/sh "$1" "$2" "$3" "$4" &
printf 'root-output'
printf 'root-error' >&2
wait
""")
      val process = new AtomicReference[Process]()
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = throw new IOException("explicit cwd required")
        override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = {
          val actual = LocalProcessLauncher.local.startBlocking(Vector("/bin/sh", script.toString,
            child.toString, grand.toString, grandpid.toString, childpid.toString), workingDirectory, environment)
          process.set(actual)
          actual
        }
      }
      val driver = new LocalProcessExecutionDriver("tree-stop", launcher)
      var owned = Vector.empty[ProcessHandle]
      try {
        When("stop snapshots the tree, the root exits on TERM and the resistant descendant outlives the root")
        val handle = driver.startC(_sbt_execution(root, SbtOperation.Test,
          limits = _limits(executiontimeout = 10000L, terminationgrace = 1000L))).toOption.get
        _await_condition(Files.exists(grandpid) && Files.size(grandpid) > 0L && Files.exists(childpid) && Files.size(childpid) > 0L, 3000L)
        val grandhandle = ProcessHandle.of(Files.readString(grandpid).toLong).get()
        owned = Vector(ProcessHandle.of(Files.readString(childpid).toLong).get(), grandhandle)
        val awaited = new FutureTask[org.goldenport.Consequence[ProcessExecutionResult]](() => handle.awaitC)
        val awaiting = new Thread(awaited)
        awaiting.setDaemon(true)
        awaiting.start()
        val stopper = new Thread(() => { driver.requestStopC(); () })
        stopper.setDaemon(true)
        stopper.start()
        _await_condition(!process.get.isAlive, 1500L)
        val rootfirst = grandhandle.isAlive
        val premature = awaited.isDone
        stopper.join(4000L)
        val result = awaited.get(3000L, TimeUnit.MILLISECONDS).toOption.get
        Then("the same cancellation waits for retained descendants, forces the survivor and retains actual streams")
        rootfirst shouldBe true
        premature shouldBe false
        stopper.isAlive shouldBe false
        process.get.isAlive shouldBe false
        owned.foreach(_.isAlive shouldBe false)
        result.termination shouldBe ProcessExecutionTermination.Cancelled
        _text(result.stdout) should include ("root-output")
        _text(result.stdout) should include ("grandchild-output")
        _text(result.stderr) should include ("root-error")
        driver.activeHandleCount shouldBe 0
      } finally {
        driver.close()
        owned.foreach(handle => if (handle.isAlive) handle.destroyForcibly())
        Option(process.get).foreach(value => if (value.isAlive) value.destroyForcibly())
        cleanup.close()
      }
    }

    Vector(true, false).foreach { initiallyalive =>
    s"retain an incomplete observation when descendant inspection is unavailable and root initially alive is $initiallyalive" in {
      Given("a controlled process with unavailable descendant inspection or a root that already exited before any tree observation")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val events = new ConcurrentLinkedQueue[ProcessExecutionProgressEvent]()
      val destroyed = new AtomicInteger(0)
      val observer = new ProcessExecutionProgress {
        def report(event: ProcessExecutionProgressEvent): Unit = { events.add(event); () }
      }
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = throw new IOException("explicit cwd required")
        override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = new Process {
          private val _alive = new java.util.concurrent.atomic.AtomicBoolean(initiallyalive)
          def getInputStream = new ByteArrayInputStream("kept-output".getBytes(StandardCharsets.UTF_8))
          def getErrorStream = new ByteArrayInputStream("kept-error".getBytes(StandardCharsets.UTF_8))
          def getOutputStream = new ByteArrayOutputStream()
          def waitFor(): Int = 0
          def exitValue(): Int = if (_alive.get) throw new IllegalThreadStateException() else 0
          def destroy(): Unit = { destroyed.incrementAndGet(); _alive.set(false) }
          override def isAlive(): Boolean = _alive.get
        }
      }
      val driver = new LocalProcessExecutionDriver("unconfirmed-stop", launcher, observer)
      val execution = _sbt_execution(root, SbtOperation.Test)
      try {
        When("runtime stop observes the root exit but cannot confirm the process tree before awaiting")
        val handle = driver.startC(execution).toOption.get
        val another = driver.startC(execution).toOption.get
        val stopped = driver.requestStopC()
        val result = handle.awaitC.toOption.get
        val second = another.awaitC.toOption.get
        val last = events.iterator().asScala.toVector.last
        Then("the result retains captures and Incomplete while telemetry cannot claim a terminal exit")
        stopped.isFaillure shouldBe true
        second.termination shouldBe ProcessExecutionTermination.TerminationUnconfirmed
        destroyed.get shouldBe (if (initiallyalive) 2 else 0)
        result.termination shouldBe ProcessExecutionTermination.TerminationUnconfirmed
        SbtExecutionResult(SbtTarget(execution.definition._sbt_metadata.get.binding.workspaceId), SbtOperation.Test, result).outcome shouldBe
          SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.TerminationUnconfirmed)
        _text(result.stdout) shouldBe "kept-output"
        _text(result.stderr) shouldBe "kept-error"
        last.state shouldBe ProcessExecutionProgressState.Uncertain
        last.termination shouldBe None
        last.exitCode shouldBe None
        driver.terminationConfirmed shouldBe false
        intercept[IllegalStateException](driver.close()).getMessage shouldBe
          "Local Process Execution termination could not be confirmed"
        driver.terminationConfirmed shouldBe false
      } finally { intercept[IllegalStateException](driver.close()); cleanup.close() }
    }
    }

    "fail ordinary owned shutdown while closing every resource when process termination is unconfirmed" in {
      Given("a SystemNode owning an ordinary marker before a local driver with two unconfirmed process trees")
      val node = SystemNode.create()
      val closed = new AtomicInteger(0)
      val destroyed = new AtomicInteger(0)
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = new Process {
          private val _alive = new java.util.concurrent.atomic.AtomicBoolean(true)
          def getInputStream = new ByteArrayInputStream(Array.emptyByteArray)
          def getErrorStream = new ByteArrayInputStream(Array.emptyByteArray)
          def getOutputStream = new ByteArrayOutputStream()
          def waitFor(): Int = 0
          def exitValue(): Int = if (_alive.get) throw new IllegalThreadStateException() else 0
          def destroy(): Unit = { destroyed.incrementAndGet(); _alive.set(false) }
          override def isAlive(): Boolean = _alive.get
        }
      }
      val driver = new LocalProcessExecutionDriver("ordinary-unconfirmed-close", launcher)
      node.ownRuntimeResourceC(new AutoCloseable {
        def close(): Unit = { closed.incrementAndGet(); () }
      }).isSuccess shouldBe true
      node.ownRuntimeResourceC(driver).isSuccess shouldBe true
      try {
        driver.startC(_execution(Vector("streams", "literal"))).isSuccess shouldBe true
        driver.startC(_execution(Vector("streams", "literal"))).isSuccess shouldBe true
        driver.activeHandleCount shouldBe 2
        When("ordinary SystemNode shutdown closes its last-registered driver before the other owned resource")
        val result = node.shutdownC()
        val repeated = node.shutdownC()
        Then("both handles receive cleanup, the remaining resource closes and the shutdown retains failed confirmation")
        result.isFaillure shouldBe true
        repeated.isFaillure shouldBe true
        destroyed.get shouldBe 2
        closed.get shouldBe 1
        node.state shouldBe SystemNode.State.Stopped
        driver.activeHandleCount shouldBe 0
        driver.activeProcessCount shouldBe 0
        driver.terminationConfirmed shouldBe false
        intercept[IllegalStateException](driver.close()).getMessage shouldBe
          "Local Process Execution termination could not be confirmed"
      } finally { node.shutdownC() }
    }

    "observe sequential admitted SBT exits without changing results or disclosing command data" in {
      Given("one runtime driver, admitted Compile and Test operations with differing effective budgets, and a metadata observer")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val events = new ConcurrentLinkedQueue[ProcessExecutionProgressEvent]()
      val observer = new ProcessExecutionProgress {
        def report(event: ProcessExecutionProgressEvent): Unit = { events.add(event); () }
      }
      val driver = new LocalProcessExecutionDriver(progress = observer)
      val executions = Vector(
        _sbt_execution(root, SbtOperation.Compile, executable = Paths.get("/usr/bin/true"),
          limits = _limits(launchtimeout = 3100L, executiontimeout = 7200L, terminationgrace = 120L)),
        _sbt_execution(root, SbtOperation.Test, executable = Paths.get("/usr/bin/false"),
          limits = _limits(launchtimeout = 4300L, executiontimeout = 9100L, terminationgrace = 240L)))
      try {
        When("the local driver starts and awaits both real processes consecutively, including repeated awaits")
        val results = executions.map { execution =>
          val handle = driver.startC(execution).toOption.get
          handle.awaitC.toOption.get -> handle.awaitC.toOption.get
        }
        val observed = events.iterator().asScala.toVector
        val lines = observed.map(ProcessExecutionProgress.jsonLine)
        Then("each operation reports launch, observed start, wait and actual exit once with unchanged zero and nonzero results")
        results.foreach { case (first, repeated) => repeated shouldBe first }
        results.map(_._1.termination) shouldBe Vector(ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.Exited(1))
        observed.grouped(4).zip(executions).foreach { case (group, execution) =>
          group.map(_.state) shouldBe Vector(ProcessExecutionProgressState.Launching,
            ProcessExecutionProgressState.Started, ProcessExecutionProgressState.Waiting, ProcessExecutionProgressState.Terminal)
          group.map(_.operation).distinct shouldBe Vector(execution.definition._sbt_metadata.get.operation)
          group.map(_.workspaceId.print).distinct shouldBe Vector("project")
          group.map(_.programIdentity).distinct shouldBe Vector("project-build")
          group.head.pid shouldBe None
          group.head.launchTimeoutMillis shouldBe execution.effectiveLimits.launchTimeoutMillis
          group.head.executionTimeoutMillis shouldBe execution.effectiveLimits.executionTimeoutMillis
          group.head.terminationGraceMillis shouldBe execution.effectiveLimits.terminationGraceMillis
          val launching = io.circe.parser.parse(ProcessExecutionProgress.jsonLine(group.head)
            .stripPrefix("[cncf-sbt-event] ")).toOption.get
          launching.hcursor.get[Long]("launchTimeoutMillis").toOption shouldBe execution.effectiveLimits.launchTimeoutMillis
          launching.hcursor.get[Long]("executionTimeoutMillis").toOption shouldBe execution.effectiveLimits.executionTimeoutMillis
          launching.hcursor.get[Long]("terminationGraceMillis").toOption shouldBe execution.effectiveLimits.terminationGraceMillis
          group.tail.foreach { event =>
            event.launchTimeoutMillis shouldBe None
            event.executionTimeoutMillis shouldBe None
            event.terminationGraceMillis shouldBe None
            val json = io.circe.parser.parse(ProcessExecutionProgress.jsonLine(event)
              .stripPrefix("[cncf-sbt-event] ")).toOption.get
            json.asObject.get.keys.toSet.intersect(Set("launchTimeoutMillis", "executionTimeoutMillis", "terminationGraceMillis")) shouldBe empty
          }
          group.tail.flatMap(_.pid).distinct should have size 1
          group.last.exitCode shouldBe Some(if (group.head.operation == SbtOperation.Compile) 0 else 1)
          group.last.termination shouldBe Some(results(if (group.head.operation == SbtOperation.Compile) 0 else 1)._1.termination)
          group.dropRight(1).foreach(_.termination shouldBe None)
        }
        observed should have size 8
        lines.foreach { line =>
          line should startWith ("[cncf-sbt-event] {")
          line should not include "TEXTUS_BUILD_PROFILE"
          line should not include "controlled"
          line should not include "--batch"
          line should not include root.toString
          line should not include "stdout"
          line should not include "stderr"
        }
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally { driver.close(); cleanup.close() }
    }

    "isolate observer failures from actual SBT launch and completion" in {
      Given("an admitted real local SBT-tagged process and an observer throwing at every event")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val count = new AtomicInteger(0)
      val observer = new ProcessExecutionProgress {
        def report(event: ProcessExecutionProgressEvent): Unit = {
          count.incrementAndGet()
          throw new IllegalStateException("observer failure")
        }
      }
      val driver = new LocalProcessExecutionDriver(progress = observer)
      try {
        When("the driver launches and awaits the admitted process")
        val result = driver.startC(_sbt_execution(root, SbtOperation.Compile)).flatMap(_.awaitC)
        Then("the unchanged actual result and cleanup survive all four observer failures")
        result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0))
        count.get shouldBe 4
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
      } finally { driver.close(); cleanup.close() }
    }

    Vector("removed", "non-executable").foreach { condition =>
      s"confirm a $condition admitted SBT executable was not started and permit only fresh dispatch after restoration" in {
        Given(s"an admitted owned executable later made $condition and a controlled successful launcher")
        val root = _sbt_root()
        val cleanup = new ProcessExecutionWorkArea(root)
        val executable = _sbt_executable(root)
        val execution = _sbt_execution(root, SbtOperation.Test, executable = executable)
        val workarea = new ProcessExecutionWorkArea(Files.createDirectory(root.resolve("workarea")))
        val calls = new ConcurrentLinkedQueue[(Vector[String], Path, Map[String, String])]()
        val events = new ConcurrentLinkedQueue[ProcessExecutionProgressEvent]()
        val observer = new ProcessExecutionProgress {
          def report(event: ProcessExecutionProgressEvent): Unit = { events.add(event); () }
        }
        val driver = new LocalProcessExecutionDriver("unavailable-build", _sbt_launcher(calls, succeed = true), observer)
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
          val prelaunch = events.iterator().asScala.toVector
          prelaunch should have size 2
          prelaunch.foreach { event =>
            event.state shouldBe ProcessExecutionProgressState.Terminal
            event.termination shouldBe Some(ProcessExecutionTermination.NotStarted)
            event.pid shouldBe None
            event.exitCode shouldBe None
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

    "retain an honest final audit when a timed-out launcher continues beyond an empty stop snapshot" in {
      Given("an admitted execution and an actual launcher Callable held past its deadline despite interruption")
      val root = _sbt_root()
      val cleanup = new ProcessExecutionWorkArea(root)
      val release = new java.util.concurrent.CountDownLatch(1)
      val entered = new java.util.concurrent.CountDownLatch(1)
      val returned = new java.util.concurrent.CountDownLatch(1)
      val process = new AtomicReference[Process]()
      val launcher = new LocalProcessLauncher {
        def startBlocking(command: Vector[String]): Process = throw new IOException("explicit cwd required")
        override def startBlocking(command: Vector[String], workingDirectory: Path, environment: Map[String, String]): Process = {
          entered.countDown()
          while (release.getCount > 0L) {
            try release.await()
            catch { case _: InterruptedException => () }
          }
          try {
            val actual = LocalProcessLauncher.local.startBlocking(Vector("/usr/bin/true"), workingDirectory, environment)
            process.set(actual)
            actual
          } finally returned.countDown()
        }
      }
      val driver = new LocalProcessExecutionDriver("late-launch", launcher)
      try {
        When("launch times out, stop snapshots no registered handle and normal close clears only owned registries")
        val result = driver.startC(_sbt_execution(root, SbtOperation.Test, limits = _limits(launchtimeout = 100L))).flatMap(_.awaitC)
        val stopped = driver.requestStopC()
        val before = driver.terminationConfirmed
        intercept[IllegalStateException](driver.close())
        val after = driver.terminationConfirmed
        release.countDown()
        val finished = returned.await(5000L, TimeUnit.MILLISECONDS)
        _await_condition(Option(process.get).forall(value => !value.isAlive), 5000L)
        Then("successful empty stop does not certify in-flight or abandoned launch cleanup, even after close")
        entered.getCount shouldBe 0L
        result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed)
        stopped.isSuccess shouldBe true
        before shouldBe false
        after shouldBe false
        finished shouldBe true
        driver.terminationConfirmed shouldBe false
      } finally {
        release.countDown()
        returned.await(5000L, TimeUnit.MILLISECONDS)
        Option(process.get).foreach(value => if (value.isAlive) value.destroyForcibly())
        intercept[IllegalStateException](driver.close())
        cleanup.close()
      }
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
      val events = new ConcurrentLinkedQueue[ProcessExecutionProgressEvent]()
      val observer = new ProcessExecutionProgress {
        def report(event: ProcessExecutionProgressEvent): Unit = { events.add(event); () }
      }
      val driver = new LocalProcessExecutionDriver("delayed-build", launcher, observer)
      try {
        When("both start variants submit a launch that cannot complete in time")
        val results = Vector(driver.startC(execution).flatMap(_.awaitC), driver.startC(execution, workarea).flatMap(_.awaitC))

        Then("timeout remains LaunchFailed and never establishes a confirmed unexecuted fact")
        results.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.LaunchFailed))
        val observed = events.iterator().asScala.toVector
        observed.map(_.state) shouldBe Vector(ProcessExecutionProgressState.Launching, ProcessExecutionProgressState.Uncertain,
          ProcessExecutionProgressState.Launching, ProcessExecutionProgressState.Uncertain)
        observed.foreach { event =>
          event.pid shouldBe None
          event.termination shouldBe None
          event.exitCode shouldBe None
        }
        calls.get shouldBe 2
        driver.activeProcessCount shouldBe 0
        driver.activeHandleCount shouldBe 0
        driver.terminationConfirmed shouldBe false
      } finally { intercept[IllegalStateException](driver.close()); workarea.close(); cleanup.close() }
    }

    "retain the actual unconfirmed handle when driver closure races an already exited launch" in {
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
            intercept[IllegalStateException](reference.get.close())
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

        Then("the returned handle preserves captures and uncertainty rather than claiming that a submitted process never started")
        results.foreach(_.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.TerminationUnconfirmed))
        calls should have size 2
        drivers.foreach { driver =>
          driver.activeProcessCount shouldBe 0
          driver.activeHandleCount shouldBe 0
        }
      } finally { drivers.foreach(driver => intercept[IllegalStateException](driver.close())); workarea.close(); cleanup.close() }
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
      } finally { intercept[IllegalStateException](driver.close()); workarea.close(); fixture.close() }
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
        intercept[IllegalStateException](driver.close())
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
        intercept[IllegalStateException](driver.close())
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
      val events = new ConcurrentLinkedQueue[ProcessExecutionProgressEvent]()
      val observer = new ProcessExecutionProgress {
        def report(event: ProcessExecutionProgressEvent): Unit = { events.add(event); () }
      }
      val driver = new LocalProcessExecutionDriver(progress = observer)

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
      events shouldBe empty
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
        intercept[IllegalStateException](driver.close())
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
        intercept[IllegalStateException](driver.close())
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

  private def _await_condition(condition: => Boolean, millis: Long): Unit = {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis)
    while (!condition && System.nanoTime() < deadline) Thread.sleep(1L)
    if (!condition) throw new IllegalStateException("owned process fixture did not reach the required lifecycle state")
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
    environment: Map[String, String] = Map.empty,
    executable: String = _java_path,
    fixedarguments: Vector[String] = _fixed_arguments
  ): ResolvedProcessExecution = {
    val capability = ProcessCapabilityId.parseC("local-probe").toOption.get
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "local-probe",
      executable,
      fixedarguments,
      ProcessArgumentPolicy(fixedarguments, arguments.toSet),
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
