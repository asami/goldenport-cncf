package org.goldenport.cncf.processexecution

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, IdGenerationContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.job.{
  ActionId,
  JobControlCommand,
  JobControlRequest,
  JobEngineTestFixture,
  JobId,
  JobPersistencePolicy,
  JobRunMode,
  JobStatus,
  JobSubmitOption,
  JobTask,
  TaskFailed,
  TaskOutcome,
  TaskSucceeded
}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Process Execution cancellation through Job.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionJobCancellationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  private val _process_await_timeout_millis = 15000L
  private val _e1_metadata =
    afterWord("in spec:process-execution-runtime, example:E1, rules:R1, phase:35")
  private val _e2_metadata =
    afterWord("in spec:process-execution-runtime, example:E2, rules:R2, phase:35")
  private val _e3_metadata =
    afterWord("in spec:process-execution-runtime, example:E3, rules:R3, phase:35")
  private val _e4_metadata =
    afterWord("in spec:process-execution-runtime, example:E4, rules:R4, phase:35")
  private val _e5_metadata =
    afterWord("in spec:process-execution-runtime, example:E5, rules:R4, phase:35")

  "Process Execution Job cancellation" should {
    "E1 propagate active Job cancellation to the registered process handle" must _e1_metadata {
      "when an active Job cancellation reaches a registered process" in {
        Given("Spec: docs/spec/process-execution-runtime.md; Rules: R1; Example: E1; a Job task executing an admitted Process Execution handle")
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val driver = new BlockingDriver(_result(capability, ProcessExecutionTermination.Cancelled))
        val context = _context(driver)
        val engine = createJobEngine()
        val task = new ProcessTask(_execution(capability))
        val worker = _submit_sync(engine, task, context)
        withClue(s"worker=${worker.getState} process failure=${task.failure.map(_.display)}") {
          driver.awaitentered.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS) shouldBe true
        }
        val jobid = task.jobid.getOrElse(fail("missing active Job id"))
        given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

        When("the Job receives a cancellation control command")
        engine.control(jobid, JobControlRequest(JobControlCommand.Cancel)).isSuccess shouldBe true

        Then("the handle is signalled once and the Job remains terminally cancelled")
        awaitCondition(driver.cancelcount.get == 1) shouldBe true
        awaitStatus(engine, jobid, Set(JobStatus.Cancelled)) shouldBe Some(JobStatus.Cancelled)
        driver.cancelcount.get shouldBe 1
        worker.join(_process_await_timeout_millis)
      }
    }

    "E2 preserve process completion that wins before later Job cancellation" must _e2_metadata {
      "when cancellation arrives after process completion" in {
        Given("Spec: docs/spec/process-execution-runtime.md; Rules: R2; Example: E2; a Job task whose Process Execution handle has already completed")
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val driver = new CompletedDriver(_result(capability, ProcessExecutionTermination.Exited(0)))
        val processcompleted = new CountDownLatch(1)
        val release = new CountDownLatch(1)
        val context = _context(driver)
        val engine = createJobEngine()
        val task = new ProcessTask(_execution(capability), Some(() => {
          processcompleted.countDown()
          release.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS)
        }))
        val worker = _submit_sync(engine, task, context)
        withClue(s"worker=${worker.getState} process failure=${task.failure.map(_.display)}") {
          processcompleted.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS) shouldBe true
        }
        val jobid = task.jobid.getOrElse(fail("missing active Job id"))
        given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

        When("cancellation arrives after the process completion but before task settlement")
        try {
          engine.control(jobid, JobControlRequest(JobControlCommand.Cancel)).isSuccess shouldBe true

          Then("the completed handle is not cancelled retroactively")
          driver.cancelcount.get shouldBe 0
          driver.completedresult shouldBe Some(driver.result)
        } finally {
          release.countDown()
          worker.join(_process_await_timeout_millis)
        }
      }
    }

    "E3 cancel and reap an active process when its UnitOfWork aborts" must _e3_metadata {
      "when an owning UnitOfWork aborts an active process" in {
        Given("Spec: docs/spec/process-execution-runtime.md; Rules: R3; Example: E3; a direct UnitOfWork Process Execution operation with an active handle")
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val driver = new BlockingDriver(_result(capability, ProcessExecutionTermination.Cancelled))
        val context = _context(driver)
        val uow = new UnitOfWork(context)
        @volatile var result = Option.empty[Consequence[ProcessExecutionResult]]
        val worker = new Thread(() => {
          result = Some(new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ProcessExec(_execution(capability))))
        })
        worker.start()
        driver.awaitentered.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS) shouldBe true

        When("the owning UnitOfWork aborts before process completion")
        val aborted = uow.abort()

        Then("the resource lifecycle cancels, awaits, and removes the active process")
        aborted.isSuccess shouldBe true
        worker.join(_process_await_timeout_millis)
        worker.isAlive shouldBe false
        driver.cancelcount.get shouldBe 1
        result.flatMap(_.toOption) shouldBe Some(driver.result)
      }
    }

    "E4 retain UnitOfWork ownership when initial process await fails" must _e4_metadata {
      "when initial process await fails before UnitOfWork abort" in {
        Given("Spec: docs/spec/process-execution-runtime.md; Rules: R4; Example: E4; a process handle whose initial await fails before terminal completion")
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val driver = new FailingAwaitDriver(_result(capability, ProcessExecutionTermination.Cancelled))
        val uow = new UnitOfWork(_context(driver))

        When("the initial await fails and the owning UnitOfWork subsequently aborts")
        val execution = new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ProcessExec(_execution(capability)))
        execution.isSuccess shouldBe false
        val root = driver.workarearoot.getOrElse(fail("missing Process Execution WorkArea"))
        Files.exists(root) shouldBe true
        val aborted = uow.abort()

        Then("the UnitOfWork still cancels, reaps, and closes the registered process resource")
        aborted.isSuccess shouldBe true
        driver.cancelcount.get shouldBe 1
        driver.awaitcount.get shouldBe 2
        Files.exists(root) shouldBe false
      }
    }

    "E5 continue process reaping when cancellation reports failure" must _e5_metadata {
      "when process cancellation reports a failure during reaping" in {
        Given("Spec: docs/spec/process-execution-runtime.md; Rules: R4; Example: E5; an owned process whose cancellation reports failure")
        val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
        val driver = new FailingAwaitDriver(
          _result(capability, ProcessExecutionTermination.Cancelled),
          failcancel = true
        )
        val uow = new UnitOfWork(_context(driver))
        new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.ProcessExec(_execution(capability))).isSuccess shouldBe false
        val root = driver.workarearoot.getOrElse(fail("missing Process Execution WorkArea"))

        When("the UnitOfWork reclaims the process resource")
        val aborted = uow.abort()

        Then("the cancellation failure is retained but await and WorkArea cleanup still complete")
        aborted.isSuccess shouldBe false
        driver.cancelcount.get shouldBe 1
        driver.awaitcount.get shouldBe 2
        Files.exists(root) shouldBe false
      }
    }
  }

  private def _context(driver: ProcessExecutionDriver): ExecutionContext = {
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    val base = ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(),
      IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("process", "cancellation"),
        "process-execution-job-cancellation"
      )
    )
    val scope = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "process-execution-job-cancellation-test",
      parent = None,
      observabilitycontext = base.observability,
      processexecutiondriveroption = Some(driver)
    )
    base.withScope(scope)
  }

  private def _submit_sync(
    engine: org.goldenport.cncf.job.InMemoryJobEngine,
    task: JobTask,
    context: ExecutionContext
  ): Thread = {
    val worker = new Thread(() => {
      val _ = engine.submit(
        List(task),
        context,
        JobSubmitOption(
          persistence = JobPersistencePolicy.Ephemeral,
          runMode = JobRunMode.Sync
        )
      )
    })
    worker.start()
    worker
  }

  private def _execution(capability: ProcessCapabilityId): ResolvedProcessExecution = {
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "codex-cli",
      "test-runtime-owned-location",
      Vector.empty,
      ProcessArgumentPolicy(Vector.empty, Set.empty),
      _limits,
      Set.empty
    ).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    policy.resolveC(ProcessExecutionRequest(capability), ProcessExecutionGrant(capability)).toOption.get
  }

  private def _result(
    capability: ProcessCapabilityId,
    termination: ProcessExecutionTermination
  ): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    ProcessExecutionResult(
      termination = termination,
      stdout = capture,
      stderr = capture,
      artifacts = Vector.empty,
      elapsedMillis = 1L,
      safeProgramIdentity = capability.print
    )
  }

  private lazy val _limits = ProcessExecutionLimits(
    Some(100L), Some(100L), Some(100L), Some(100L), Some(100L), Some(100L),
    Some(100L), Some(100L), Some(100L), Some(100L), Some(100L)
  )
}

private final class ProcessTask(
  execution: ResolvedProcessExecution,
  onsuccess: Option[() => Unit] = None
) extends JobTask {
  @volatile var failure: Option[Conclusion] = None
  @volatile var jobid: Option[JobId] = None

  val actionId: ActionId = ActionId.generate()

  def run(ctx: ExecutionContext): TaskOutcome = {
    jobid = ctx.jobContext.jobId
    new UnitOfWorkInterpreter(new UnitOfWork(ctx)).interpret(UnitOfWorkOp.ProcessExec(execution)) match {
      case Consequence.Success(_) =>
        onsuccess.foreach(_())
        TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Void())
      case Consequence.Failure(conclusion) =>
        failure = Some(conclusion)
        TaskFailed(conclusion)
    }
  }
}

private final class BlockingDriver(
  val result: ProcessExecutionResult
) extends ProcessExecutionDriver {
  val awaitentered = new CountDownLatch(1)
  val cancelcount = new AtomicInteger(0)
  private val _cancelled = new CountDownLatch(1)

  val safeIdentity: String = "blocking-test-driver"

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    Consequence.success(new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] = {
        awaitentered.countDown()
        _cancelled.await(3000L, TimeUnit.MILLISECONDS)
        Consequence.success(result)
      }

      def cancelC: Consequence[Unit] = {
        cancelcount.incrementAndGet()
        _cancelled.countDown()
        Consequence.unit
      }
    })
  }
}

private final class CompletedDriver(
  val result: ProcessExecutionResult
) extends ProcessExecutionDriver {
  val cancelcount = new AtomicInteger(0)
  @volatile var completedresult: Option[ProcessExecutionResult] = None

  val safeIdentity: String = "completed-test-driver"

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    Consequence.success(new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] = {
        completedresult = Some(result)
        Consequence.success(result)
      }

      def cancelC: Consequence[Unit] = {
        cancelcount.incrementAndGet()
        Consequence.unit
      }
    })
  }
}

private final class FailingAwaitDriver(
  val result: ProcessExecutionResult,
  failcancel: Boolean = false
) extends ProcessExecutionDriver {
  val awaitcount = new AtomicInteger(0)
  val cancelcount = new AtomicInteger(0)
  @volatile var workarearoot: Option[java.nio.file.Path] = None

  val safeIdentity: String = "failing-await-test-driver"

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    Consequence.operationIllegal("process_exec", "Process Execution WorkArea is required")
  }

  override def startC(
    execution: ResolvedProcessExecution,
    workarea: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    workarearoot = Some(workarea.root)
    Consequence.success(new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] =
        if (awaitcount.incrementAndGet() == 1)
          Consequence.operationIllegal("process_exec", "initial process await failed")
        else
          Consequence.success(result)

      def cancelC: Consequence[Unit] = {
        cancelcount.incrementAndGet()
        if (failcancel)
          Consequence.operationIllegal("process_exec", "process cancellation failed")
        else
          Consequence.unit
      }
    })
  }
}
