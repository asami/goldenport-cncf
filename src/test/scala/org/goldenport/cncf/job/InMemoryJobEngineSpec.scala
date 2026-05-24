package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import cats.~>
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction, ResourceAccess}
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, RuntimeContext}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.{EntityStore, EntityStoreSpace}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  4, 2026
 *  version Apr. 22, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class InMemoryJobEngineSpec extends AnyWordSpec with Matchers with GivenWhenThen with JobEngineTestFixture {
  "InMemoryJobEngine" should {
    "execute a Command as a Job and store the result" in {
      val action = new CommandAction() {
        // val name = "test"
        val request = Request.ofOperation("test")
        override def createCall(core: ActionCall.Core): ActionCall = {
          val actionself = this
          val _core_ = core
          new ActionCall {
            override val core: ActionCall.Core = _core_
            override def action: Action = actionself
            def execute(): Consequence[OperationResponse] =
              Consequence.success(OperationResponse.Scalar("ok"))
          }
        }
      }

      val actionengine = ActionEngine.create()
      val jobengine = createJobEngine()
      val ctx = ExecutionContext.test()
      val task = ActionTask(ActionId.generate(), action, actionengine, None)

      val jobid = _jobid(jobengine.submit(List(task), ctx))
      val result = awaitResult(jobengine, jobid)

      result.isDefined shouldBe true
      result.get match {
        case JobResult.Success(res) =>
          res.toResponse shouldBe Response.Scalar("ok")
        case JobResult.Failure(c) =>
          fail(c.toString)
      }
      jobengine.shutdown()
    }

    "synchronize persistent jobs to the Job SimpleEntity management record" in {
      import JobEntity.given

      val jobengine = createJobEngine()
      given ExecutionContext = ExecutionContext.test()
      val jobid = _jobid(jobengine.submit(List(_ValueTask("managed")), summon[ExecutionContext], JobSubmitOption(runMode = JobRunMode.Sync)))

      val entity = EntityStore.standard().load[JobEntity](JobEntity.entityId(jobid)).toOption.flatten

      entity.flatMap(_.record.getString("jobId")) shouldBe Some(jobid.value)
      entity.flatMap(_.record.getString("status")) shouldBe Some("Succeeded")
      entity.flatMap(_.record.getString("resultMessage")) shouldBe Some("ok")
      jobengine.shutdown()
    }

    "skip Job SimpleEntity management records for ephemeral runtime jobs" in {
      import JobEntity.given

      val jobengine = createJobEngine()
      given ExecutionContext = ExecutionContext.test()
      val jobid = _jobid(jobengine.submit(
        List(_ValueTask("ephemeral")),
        summon[ExecutionContext],
        JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)
      ))

      EntityStore.standard().load[JobEntity](JobEntity.entityId(jobid)).toOption.flatten shouldBe None
      jobengine.shutdown()
    }

    "record diagnostics when Job SimpleEntity synchronization fails" in {
      val jobengine = createJobEngine()
      val ctx = _context_without_job_entity_datastore()
      val jobid = _jobid(jobengine.submit(List(_ValueTask("diagnostic")), ctx, JobSubmitOption(runMode = JobRunMode.Sync)))
      val model = jobengine.query(jobid).getOrElse(fail("job read model is missing"))

      model.debug.parameters.get("cncf.job.entitySync") shouldBe Some("failed")
      model.debug.executionNotes.exists(_.startsWith("job-entity-sync-failed:")) shouldBe true
      jobengine.shutdown()
    }

    "retain Job input metadata and clean raw payload after TTL" in {
      val created = Instant.parse("2026-05-24T00:00:00Z")
      val bytes = "9780134685991\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val input = JobInput(
        payloads = Vector(
          JobInput.inline("fileContent", bytes, Some("books.txt"), Some("text/plain"), created),
          JobInput.blob("file", "blob-1", Some("large-books.xlsx"), Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), Some(128L * 1024L), Some("abc"), created)
        ),
        retentionPolicy = JobInputRetentionPolicy.Ttl,
        ttl = Duration.ofDays(7),
        createdAt = created
      )
      val jobengine = createJobEngine()
      val ctx = ExecutionContext.test()
      val jobid = _jobid(jobengine.submit(
        List(_ValueTask("import")),
        ctx,
        JobSubmitOption(runMode = JobRunMode.Sync, input = Some(input))
      ))

      val before = jobengine.query(jobid).flatMap(_.input).getOrElse(fail("job input is missing"))
      before.payloads.head.inlineBase64.nonEmpty shouldBe true
      before.payloads(1).blobId shouldBe Some("blob-1")
      before.payloads.head.sha256.nonEmpty shouldBe true

      jobengine.cleanupExpiredInputs(created.plus(Duration.ofDays(8))) shouldBe 1

      val after = jobengine.query(jobid).flatMap(_.input).getOrElse(fail("job input metadata is missing"))
      after.cleanedAt.nonEmpty shouldBe true
      after.payloads.head.inlineBase64 shouldBe None
      after.payloads(1).blobId shouldBe None
      after.payloads.head.sha256 shouldBe before.payloads.head.sha256
      after.payloads.head.filename shouldBe Some("books.txt")
      jobengine.shutdown()
    }

    "queue async jobs before execution and expose queue timeline" in {
      Given("a single-worker scheduler with a blocking first job")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blocker = _BlockingTask(entered, release, "blocker")
      val queued = _ValueTask("queued")

      When("two async jobs are submitted")
      val first = _jobid(jobengine.submit(List(blocker), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val second = _jobid(jobengine.submit(List(queued), ExecutionContext.test()))

      Then("the second job remains submitted with an async queued timeline event")
      awaitCondition {
        jobengine.query(second).exists { model =>
          model.status == JobStatus.Submitted &&
          model.timeline.events.exists(_.kind == "job.async.queued")
        }
      } shouldBe true

      release.countDown()
      awaitResult(jobengine, first)
      awaitResult(jobengine, second)
      jobengine.shutdown()
    }

    "serialize async execution when workerCount is one" in {
      Given("two blocking jobs and a single-worker scheduler")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val running = new AtomicInteger(0)
      val maxrunning = new AtomicInteger(0)
      val firstentered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val first = _ConcurrencyTask("first", running, maxrunning, Some(firstentered), release)
      val second = _ConcurrencyTask("second", running, maxrunning, None, release)

      When("both jobs are submitted")
      val firstid = _jobid(jobengine.submit(List(first), ExecutionContext.test()))
      firstentered.await(1, TimeUnit.SECONDS) shouldBe true
      val secondid = _jobid(jobengine.submit(List(second), ExecutionContext.test()))
      val queued = jobengine.query(secondid).get

      Then("only one job is running at a time")
      maxrunning.get() shouldBe 1
      queued.status shouldBe JobStatus.Submitted
      queued.timeline.events.exists(_.kind == "job.async.queued") shouldBe true

      release.countDown()
      awaitResult(jobengine, firstid)
      awaitResult(jobengine, secondid)
      jobengine.shutdown()
    }

    "allow parallel async execution up to workerCount" in {
      Given("two blocking jobs and a two-worker scheduler")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 2)
      )
      val running = new AtomicInteger(0)
      val maxrunning = new AtomicInteger(0)
      val entered = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val first = _ConcurrencyTask("first", running, maxrunning, Some(entered), release)
      val second = _ConcurrencyTask("second", running, maxrunning, Some(entered), release)

      When("both jobs are submitted")
      val firstid = _jobid(jobengine.submit(List(first), ExecutionContext.test()))
      val secondid = _jobid(jobengine.submit(List(second), ExecutionContext.test()))

      Then("both jobs can start in parallel")
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      maxrunning.get() shouldBe 2

      release.countDown()
      awaitResult(jobengine, firstid)
      awaitResult(jobengine, secondid)
      jobengine.shutdown()
    }

    "run lower numeric priority jobs before higher numeric priority jobs" in {
      Given("a busy single-worker scheduler and two queued jobs with different priorities")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val order = ArrayBuffer.empty[String]

      val blockerid = _jobid(jobengine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val lowid = _jobid(jobengine.submit(List(_RecordingTask("low", order)), ExecutionContext.test(), JobSubmitOption(priority = 10)))
      val highid = _jobid(jobengine.submit(List(_RecordingTask("high", order)), ExecutionContext.test(), JobSubmitOption(priority = -10)))

      When("worker capacity becomes available")
      release.countDown()
      awaitResult(jobengine, blockerid)
      awaitResult(jobengine, lowid)
      awaitResult(jobengine, highid)

      Then("the lower numeric priority runs first")
      order.toVector shouldBe Vector("high", "low")
      jobengine.shutdown()
    }

    "preserve FIFO order for same-priority queued jobs" in {
      Given("a busy single-worker scheduler and two queued jobs with the same priority")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val order = ArrayBuffer.empty[String]

      val blockerid = _jobid(jobengine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val firstid = _jobid(jobengine.submit(List(_RecordingTask("first", order)), ExecutionContext.test(), JobSubmitOption(priority = 0)))
      val secondid = _jobid(jobengine.submit(List(_RecordingTask("second", order)), ExecutionContext.test(), JobSubmitOption(priority = 0)))

      When("worker capacity becomes available")
      release.countDown()
      awaitResult(jobengine, blockerid)
      awaitResult(jobengine, firstid)
      awaitResult(jobengine, secondid)

      Then("queue order remains FIFO within the same priority")
      order.toVector shouldBe Vector("first", "second")
      jobengine.shutdown()
    }

    "queue same-job async tasks before execution" in {
      Given("a completed parent job and a busy single-worker scheduler")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val parentid = _jobid(jobengine.submit(List(_ValueTask("parent")), ExecutionContext.test()))
      awaitResult(jobengine, parentid)

      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blockerid = _jobid(jobengine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true

      When("a same-job async task is enqueued while the worker is busy")
      val taskid = jobengine.enqueueTaskInJob(parentid, _ValueTask("child"), ExecutionContext.test()).toOption.get

      Then("the task is queued and not started until worker capacity is available")
      awaitCondition {
        jobengine.query(parentid).exists(_.timeline.events.exists(e => e.kind == "job.same-job-async.queued" && e.taskId.contains(taskid)))
      } shouldBe true

      release.countDown()
      awaitResult(jobengine, blockerid)
      awaitCondition {
        jobengine.query(parentid).exists(_.tasks.tasks.exists(_.taskId == taskid))
      } shouldBe true
      jobengine.shutdown()
    }

    "inherit parent priority for same-job async tasks" in {
      Given("a completed high-priority parent job and a competing lower-priority job")
      val jobengine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val order = ArrayBuffer.empty[String]
      val parentid = _jobid(jobengine.submit(
        List(_ValueTask("parent")),
        ExecutionContext.test(),
        JobSubmitOption(priority = -5)
      ))
      awaitResult(jobengine, parentid)

      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blockerid = _jobid(jobengine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true

      val regularid = _jobid(jobengine.submit(
        List(_RecordingTask("regular", order)),
        ExecutionContext.test(),
        JobSubmitOption(priority = 5)
      ))

      When("a same-job async task is enqueued after the lower-priority job")
      val _ = jobengine.enqueueTaskInJob(parentid, _RecordingTask("same-job", order), ExecutionContext.test()).toOption.get
      release.countDown()
      awaitResult(jobengine, blockerid)
      awaitResult(jobengine, regularid)

      Then("the same-job task inherits the parent priority and runs first")
      awaitCondition(order.synchronized(order.size == 2)) shouldBe true
      order.synchronized(order.toVector) shouldBe Vector("same-job", "regular")
      jobengine.shutdown()
    }

    "treat scheduledStartAt at or before now as immediate async execution" in {
      Given("an async job whose scheduled start is already due")
      val jobengine = createJobEngine()

      When("the job is submitted")
      val jobid = _jobid(jobengine.submit(
        List(_ValueTask("immediate")),
        ExecutionContext.test(),
        JobSubmitOption(scheduledStartAt = Some(Instant.now().minusSeconds(1)))
      ))

      Then("it behaves like an immediate async submit")
      awaitResult(jobengine, jobid) shouldBe a[Some[_]]
      jobengine.query(jobid).get.timeline.events.exists(_.kind == "job.async.queued") shouldBe true
      jobengine.shutdown()
    }

    "reject delayed start beyond the built-in maximum window" in {
      Given("a delayed start request beyond the supported maximum")
      val jobengine = createJobEngine()

      When("the job is submitted")
      val result = jobengine.submit(
          List(_ValueTask("too-late")),
          ExecutionContext.test(),
          JobSubmitOption(scheduledStartAt = Some(Instant.now().plusSeconds(16 * 60L)))
        )

      Then("submission fails deterministically")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.show should include ("scheduledStartAt exceeds built-in max delay")
        case _ =>
          fail("expected delayed submit failure")
      }
      jobengine.shutdown()
    }

    "rehydrate persistent delayed jobs after engine restart" in {
      Given("a persistent delayed-start job and shared durable state")
      val state = InMemoryJobEngine.State()
      val schedule = InMemoryJobEngine.RetrySchedule.default
      val clock = new ManualJobTimeSource(Instant.parse("2026-05-04T00:00:00Z"))
      val timer1 = new InMemoryJobEngine.ManualJobTimer(clock)
      val scheduledat = clock.now().plusMillis(120L)
      val engine1 = createManualInMemoryJobEngine(state, schedule, clock, timer1)
      val jobid = _jobid(engine1.submit(
        List(_ValueTask("rehydrated")),
        ExecutionContext.test(),
        JobSubmitOption(scheduledStartAt = Some(scheduledat))
      ))
      engine1.query(jobid).flatMap(_.scheduledStartAt) shouldBe Some(scheduledat)

      When("the engine is restarted before the delayed start fires")
      engine1.shutdown()
      val timer2 = new InMemoryJobEngine.ManualJobTimer(clock)
      val engine2 = createManualInMemoryJobEngine(state, schedule, clock, timer2)

      Then("the delayed job is rehydrated and executes after manual clock advance")
      timer2.pendingCount shouldBe 1
      timer2.advanceBy(Duration.ofMillis(120L)) shouldBe 1
      engine2.drainAll()
      awaitResult(engine2, jobid) shouldBe a[Some[_]]
      engine2.shutdown()
    }

  }

  private def _jobid(p: Consequence[JobId]): JobId =
    p.toOption.get

  private final case class _ValueTask(
    value: String,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private final case class _BlockingTask(
    entered: CountDownLatch,
    release: CountDownLatch,
    value: String,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      entered.countDown()
      release.await(2, TimeUnit.SECONDS)
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private def _context_without_job_entity_datastore(): ExecutionContext = {
    val base = ExecutionContext.test()
    lazy val ctx: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "job-entity-sync-failure-test",
        parent = None,
        observabilityContext = base.observability,
        httpDriverOption = Some(FakeHttpDriver.okText("nop")),
        datastore = Some(DataStoreContext(new DataStoreSpace())),
        entitystore = Some(EntityStoreContext(new EntityStoreSpace()))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(ctx),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          Consequence.failure("unitOfWorkInterpreter is not used in this fixture")
      },
      commitAction = uow => { val _ = uow.commit(); () },
      abortAction = uow => { val _ = uow.rollback(); () },
      disposeAction = _ => (),
      token = "job-entity-sync-failure-test"
    )
    ctx
  }

  private final case class _ConcurrencyTask(
    value: String,
    running: AtomicInteger,
    maxrunning: AtomicInteger,
    entered: Option[CountDownLatch],
    release: CountDownLatch,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val current = running.incrementAndGet()
      _update_max(maxrunning, current)
      entered.foreach(_.countDown())
      release.await(2, TimeUnit.SECONDS)
      running.decrementAndGet()
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private final case class _RecordingTask(
    value: String,
    trace: ArrayBuffer[String],
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      trace.synchronized {
        trace += value
      }
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private def _update_max(maxrunning: AtomicInteger, current: Int): Unit = {
    var updated = false
    while (!updated) {
      val previous = maxrunning.get()
      if (current <= previous)
        updated = true
      else
        updated = maxrunning.compareAndSet(previous, current)
    }
  }

}
