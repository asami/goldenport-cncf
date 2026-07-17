package org.goldenport.cncf.processexecution

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
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionJobCancellationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  private val _process_await_timeout_millis = 15000L

  "Process Execution Job cancellation" should {
    "propagate an active Job cancellation to the registered process handle" in {
      Given("a Job task executing an admitted Process Execution handle")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val driver = new _blocking_driver(_result(capability, ProcessExecutionTermination.Cancelled))
      val context = _context(driver)
      val engine = createJobEngine()
      val task = new _process_task(_execution(capability))
      val worker = _submit_sync(engine, task, context)
      withClue(s"worker=${worker.getState} process failure=${task.failure.map(_.display)}") {
        driver.awaitEntered.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS) shouldBe true
      }
      val jobid = task.jobId.getOrElse(fail("missing active Job id"))
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("the Job receives a cancellation control command")
      engine.control(jobid, JobControlRequest(JobControlCommand.Cancel)).isSuccess shouldBe true

      Then("the handle is signalled once and the Job remains terminally cancelled")
      awaitCondition(driver.cancelCount.get == 1) shouldBe true
      awaitStatus(engine, jobid, Set(JobStatus.Cancelled)) shouldBe Some(JobStatus.Cancelled)
      driver.cancelCount.get shouldBe 1
      worker.join(_process_await_timeout_millis)
    }

    "preserve a process completion that wins before later Job cancellation" in {
      Given("a Job task whose Process Execution handle has already completed")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val driver = new _completed_driver(_result(capability, ProcessExecutionTermination.Exited(0)))
      val processcompleted = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val context = _context(driver)
      val engine = createJobEngine()
      val task = new _process_task(_execution(capability), Some(() => {
        processcompleted.countDown()
        release.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS)
      }))
      val worker = _submit_sync(engine, task, context)
      withClue(s"worker=${worker.getState} process failure=${task.failure.map(_.display)}") {
        processcompleted.await(_process_await_timeout_millis, TimeUnit.MILLISECONDS) shouldBe true
      }
      val jobid = task.jobId.getOrElse(fail("missing active Job id"))
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("cancellation arrives after the process completion but before task settlement")
      try {
        engine.control(jobid, JobControlRequest(JobControlCommand.Cancel)).isSuccess shouldBe true

        Then("the completed handle is not cancelled retroactively")
        driver.cancelCount.get shouldBe 0
        driver.completedResult shouldBe Some(driver.result)
      } finally {
        release.countDown()
        worker.join(_process_await_timeout_millis)
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
      observabilityContext = base.observability,
      processExecutionDriverOption = Some(driver)
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

  private val _limits = ProcessExecutionLimits(
    Some(100L), Some(100L), Some(100L), Some(100L), Some(100L), Some(100L),
    Some(100L), Some(100L), Some(100L), Some(100L), Some(100L)
  )
}

private final class _process_task(
  execution: ResolvedProcessExecution,
  onSuccess: Option[() => Unit] = None
) extends JobTask {
  @volatile var failure: Option[Conclusion] = None
  @volatile var jobId: Option[JobId] = None

  val actionId: ActionId = ActionId.generate()

  def run(ctx: ExecutionContext): TaskOutcome = {
    jobId = ctx.jobContext.jobId
    new UnitOfWorkInterpreter(new UnitOfWork(ctx)).interpret(UnitOfWorkOp.ProcessExec(execution)) match {
      case Consequence.Success(_) =>
        onSuccess.foreach(_())
        TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Void())
      case Consequence.Failure(conclusion) =>
        failure = Some(conclusion)
        TaskFailed(conclusion)
    }
  }
}

private final class _blocking_driver(
  val result: ProcessExecutionResult
) extends ProcessExecutionDriver {
  val awaitEntered = new CountDownLatch(1)
  val cancelCount = new AtomicInteger(0)
  private val _cancelled = new CountDownLatch(1)

  val safeIdentity: String = "blocking-test-driver"

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    Consequence.success(new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] = {
        awaitEntered.countDown()
        _cancelled.await(3000L, TimeUnit.MILLISECONDS)
        Consequence.success(result)
      }

      def cancelC: Consequence[Unit] = {
        cancelCount.incrementAndGet()
        _cancelled.countDown()
        Consequence.unit
      }
    })
  }
}

private final class _completed_driver(
  val result: ProcessExecutionResult
) extends ProcessExecutionDriver {
  val cancelCount = new AtomicInteger(0)
  @volatile var completedResult: Option[ProcessExecutionResult] = None

  val safeIdentity: String = "completed-test-driver"

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = {
    val _ = execution
    Consequence.success(new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] = {
        completedResult = Some(result)
        Consequence.success(result)
      }

      def cancelC: Consequence[Unit] = {
        cancelCount.incrementAndGet()
        Consequence.unit
      }
    })
  }
}
