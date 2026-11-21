package org.goldenport.cncf.job

import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, Executors, TimeUnit}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{GlobalRuntimeContext, ScopeKind}
import org.goldenport.cncf.operation.evaluation.OperationEvaluationOperationIdentity
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{Subsystem, SystemNodeResourceLease}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 *  version Apr. 22, 2026
 *  version Aug. 13, 2026
 *  version Oct. 31, 2026
 * @version Nov. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemSharedJobEngineSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  "Subsystem shared JobEngine" should {
    "run two isolated ActionTask children concurrently under a joined foreground owner" in {
      val configuration = _configuration(Map("textus.job.scheduler.worker-count" -> ConfigurationValue.NumberValue(BigDecimal(2))))
      val subsystem = _configured_subsystem(configuration)
      val caller = Executors.newSingleThreadExecutor()
      Given("a configured shared subsystem and two independently admitted children")
      val timeoutmillis = 30000L
      val entered = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val submitted = new CountDownLatch(2)
      val children = new ConcurrentHashMap[String, ObservedChild]()
      val ids = new ConcurrentHashMap[String, JobId]()
      try {
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val control = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        admin.jobEngine eq control.jobEngine shouldBe true
        val engine = admin.jobEngine.asInstanceOf[InMemoryJobEngine]
        engine.schedulerConfig.workerCount shouldBe 2
        val actionengine = ActionEngine.create()
        def _child_(childName: String): Action = new CommandAction() {
          val request = Request.ofOperation(childName)
          override def createCall(core: ActionCall.Core): ActionCall = {
            val self = this
            val callcore = core
            new ActionCall {
              override val core: ActionCall.Core = callcore
              override def action: Action = self
              def execute(): Consequence[OperationResponse] = {
                val ctx = executionContext
                val lease = subsystem._with_managed_datastore_lease_c(x => Consequence.success(x)).toOption.get
                ExecutionContext.noteResponseJobId(ctx, childName)
                children.put(childName, ObservedChild(ctx, lease))
                entered.countDown()
                if (!release.await(timeoutmillis, TimeUnit.MILLISECONDS))
                  throw new IllegalStateException("child release timed out")
                Consequence.success(OperationResponse.Scalar(childName))
              }
            }
          }
        }
        val owner = new CommandAction() {
          val request = Request.ofOperation("foreground_owner")
          override def createCall(core: ActionCall.Core): ActionCall = {
            val self = this
            val callcore = core
            new ActionCall {
              override val core: ActionCall.Core = callcore
              override def action: Action = self
              def execute(): Consequence[OperationResponse] = {
                Vector("first", "second").foreach { name =>
                  val identity = OperationEvaluationOperationIdentity.fromResolvedRoute("spec", "jobs", name)
                  val prepared = ExecutionContext.prepareOperationEvaluation(executionContext, identity).toOption.get
                  val context = ExecutionContext.beginOperationEvaluationAttempt(prepared).toOption.get
                  val task = ActionTask(ActionId.generate(), _child_(name), actionengine, Some(admin))
                  val id = admin.logic.submitJob(List(task), context,
                    JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Async)).toOption.get
                  ids.put(name, id)
                  submitted.countDown()
                }
                Vector("first", "second").foreach { name =>
                  engine.awaitResult(ids.get(name), timeoutmillis).toOption.get match {
                    case JobResult.Success(response) => response shouldBe OperationResponse.Scalar(name)
                    case other => fail(s"child $name failed: $other")
                  }
                }
                Consequence.success(OperationResponse.Scalar("joined"))
              }
            }
          }
        }
        When("a foreground ActionTask submits and joins both children")
        val outer = caller.submit(new java.util.concurrent.Callable[JobId] {
          def call(): JobId = admin.logic.submitJob(
            List(ActionTask(ActionId.generate(), owner, actionengine, Some(admin))), ExecutionContext.create(),
            JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)).toOption.get
        })
        entered.await(timeoutmillis, TimeUnit.MILLISECONDS) shouldBe true
        submitted.await(timeoutmillis, TimeUnit.MILLISECONDS) shouldBe true
        Then("both are active with distinct contexts, leases and IDs while the owner waits")
        outer.isDone shouldBe false
        ids.size shouldBe 2
        ids.get("first") should not be ids.get("second")
        val first = children.get("first")
        val second = children.get("second")
        first.context.runtime.unitOfWork should not be theSameInstanceAs(second.context.runtime.unitOfWork)
        first.context.asInstanceOf[ExecutionContext.Instance].executionResponseCell should not be theSameInstanceAs(
          second.context.asInstanceOf[ExecutionContext.Instance].executionResponseCell)
        ExecutionContext.currentExecutionResponseState(first.context).responseJobId shouldBe Some("first")
        ExecutionContext.currentExecutionResponseState(second.context).responseJobId shouldBe Some("second")
        first.context.operationEvaluation.correlation.get.attemptId should not be second.context.operationEvaluation.correlation.get.attemptId
        first.lease.leaseId should not be second.lease.leaseId
        first.lease.isReleased shouldBe false
        second.lease.isReleased shouldBe false
        release.countDown()
        val outerid = outer.get(timeoutmillis, TimeUnit.MILLISECONDS)
        engine.awaitResult(outerid, timeoutmillis).toOption.get match {
          case JobResult.Success(response) => response shouldBe OperationResponse.Scalar("joined")
          case other => fail(s"foreground owner failed: $other")
        }
        first.lease.isReleased shouldBe true
        second.lease.isReleased shouldBe true
        info(s"configured=2 effective=${engine.schedulerConfig.workerCount} overlap=true first=${ids.get("first")} result=${engine.getResult(ids.get("first"))} second=${ids.get("second")} result=${engine.getResult(ids.get("second"))}")
      } finally {
        release.countDown()
        caller.shutdownNow()
        caller.awaitTermination(timeoutmillis, TimeUnit.MILLISECONDS)
        Subsystem.shutdownOwned(subsystem)
      }
    }

    "honor default, bounded, invalid and manual trusted scheduler settings" in {
      Given("default, invalid and controlled manual trusted host settings")
      val empty = _configured_subsystem(_configuration(Map.empty))
      try empty.jobEngine.asInstanceOf[InMemoryJobEngine].schedulerConfig.workerCount shouldBe 1
      finally Subsystem.shutdownOwned(empty)
      When("invalid counts are resolved and a controlled manual host requests two workers")
      Vector("0", "65", "1.5", "invalid").foreach { value =>
        intercept[IllegalArgumentException] {
          _configured_subsystem(_configuration(Map("textus.job.scheduler.worker-count" -> ConfigurationValue.StringValue(value))))
        }
      }
      val manual = _configuration(Map(
        "textus.job.scheduler.worker-count" -> ConfigurationValue.NumberValue(BigDecimal(2)),
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("test"),
        RuntimeConfig.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("controlled"),
        RuntimeConfig.EXECUTION_KEY -> ConfigurationValue.StringValue("job-scheduling-run"),
        RuntimeConfig.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue("2026-07-28T09:00:00Z"),
        RuntimeConfig.EXECUTION_RANDOM_MODE_KEY -> ConfigurationValue.StringValue("seeded"),
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue("job-scheduling-seed"),
        RuntimeConfig.EXECUTION_IDS_MODE_KEY -> ConfigurationValue.StringValue("deterministic"),
        RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_ORDERING_MODE_KEY -> ConfigurationValue.StringValue("deterministic")
      ))
      val subsystem = _configured_subsystem(manual)
      try {
        Then("manual scheduling retains one non-started worker")
        val scheduler = subsystem.jobEngine.asInstanceOf[InMemoryJobEngine].schedulerConfig
        scheduler.workerCount shouldBe 1
        scheduler.autoStartWorkers shouldBe false
      } finally Subsystem.shutdownOwned(subsystem)
    }

    "share one JobEngine across administrative components" in {
      Given("a subsystem with command support")
      SubsystemTestFixture.withAdmittedSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        val submitctx = ExecutionContext.create()
        val controlctx = ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager)
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)

        When("admin submits an asynchronous Job and job-control cancels it")
        admin.jobEngine eq jobcontrol.jobEngine shouldBe true
        val jobid = admin.logic.submitJob(
          List(BlockingTask(ActionId.generate(), entered, release)),
          submitctx,
          JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("shared-job-engine"))
        ).toOption.get

        entered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true
        try {
          jobcontrol.logic.controlJob(jobid, JobControlRequest(JobControlCommand.Cancel))(using controlctx).TAKE
          release.countDown()

          Then("the shared JobEngine records the cancellation after the admitted task settles")
          awaitStatus(jobcontrol.jobEngine, jobid, Set(JobStatus.Cancelled)) shouldBe Some(JobStatus.Cancelled)
        } finally {
          release.countDown()
        }
      }
    }

    "suspend a running Job through the subsystem operation" in {
      Given("a submitted Job whose task has entered and remains controlled by the specification")
      SubsystemTestFixture.withAdmittedSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        val submitctx = ExecutionContext.test()
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)

        admin.jobEngine eq jobcontrol.jobEngine shouldBe true
        val jobid = admin.logic.submitJob(
          List(BlockingTask(ActionId.generate(), entered, release)),
          submitctx,
          JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("subsystem-execute-job-control"))
        ).toOption.get

        entered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true

        When("the job-control suspend operation is executed")
        val request = Request.of(
          component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL.name,
          service = "job_admin",
          operation = "suspend_job",
          arguments = List(Argument("id", jobid.value)),
          properties = List(
            org.goldenport.protocol.Property("cncf.security.privilege", "content_admin", None)
          )
        )

        try {
          val result = subsystem.execute(request)

          Then("the shared JobEngine records the suspended state before the task may settle")
          result shouldBe a[Consequence.Success[_]]
          awaitStatus(jobcontrol.jobEngine, jobid, Set(JobStatus.Suspended)) shouldBe Some(JobStatus.Suspended)
        } finally {
          release.countDown()
        }
      }
    }
  }

  private final case class ObservedChild(context: ExecutionContext, lease: SystemNodeResourceLease)

  private def _configuration(values: Map[String, ConfigurationValue]): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty)

  private def _configured_subsystem(configuration: ResolvedConfiguration): Subsystem = {
    val base = ExecutionContext.create()
    val global = GlobalRuntimeContext.create("shared-job-engine-spec", RuntimeConfig.from(configuration),
      configuration, base.observability, AliasResolver.empty)
    RuntimeBindingAdmissionFixture.defaultWithScope(global.createChildScope(ScopeKind.Subsystem, "shared-job-engine-spec"),
      Some(RunMode.Command), configuration, AliasResolver.empty)
  }

  private final case class BlockingTask(
    actionId: ActionId,
    entered: CountDownLatch,
    release: CountDownLatch
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      entered.countDown()
      release.await()
      TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Void())
    }
  }
}
