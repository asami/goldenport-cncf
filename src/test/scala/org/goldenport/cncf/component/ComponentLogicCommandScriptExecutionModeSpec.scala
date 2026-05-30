package org.goldenport.cncf.component

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, CallerTransactionPolicy, CommandAction, CommandExecutionMode, CommandExecutionPolicy, JobTransactionScope, OperationEventTransactionRequirement}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.dsl.script.ScriptAction
import org.goldenport.cncf.event.{ActionCallDispatcher, CmlEventCategory, CmlEventDefinition, CmlSubscriptionDefinition, DispatchRoute, DomainEvent, EventBus, EventEngine, EventReception, EventReceptionCondition, EventReceptionExecutionPolicy, EventReceptionRule, EventStore, ReceptionOutcome}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Property, Protocol, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 28, 2026
 * @version May. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentLogicCommandScriptExecutionModeSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentLogic command/script execution mode" should {
    "parse canonical and compatibility command execution mode names" in {
      Given("canonical production execution mode tokens")
      CommandExecutionPolicy.parse("sync").map(_.modeLabel) shouldBe Some("Sync")
      CommandExecutionPolicy.parse("job-sync").map(_.modeLabel) shouldBe Some("JobSync")
      CommandExecutionPolicy.parse("job-async").map(_.modeLabel) shouldBe Some("JobAsync")
      CommandExecutionPolicy.parse("job-sync-with-async-cont").map(_.modeLabel) shouldBe Some("JobSyncWithAsyncCont")
      CommandExecutionPolicy.parse("job-sync-with-async-continuation").map(_.modeLabel) shouldBe Some("JobSyncWithAsyncCont")

      Then("legacy compatibility tokens still parse")
      RuntimeConfig.parseCommandExecutionMode("job-sync") shouldBe Some(CommandExecutionMode.JobSync)
      RuntimeConfig.parseCommandExecutionMode("job-async") shouldBe Some(CommandExecutionMode.JobAsync)
      RuntimeConfig.parseCommandExecutionMode("job-sync-with-async-continuation") shouldBe Some(CommandExecutionMode.JobSyncWithAsyncCont)
      CommandExecutionPolicy.parse("sync-direct-no-job").map(_.modeLabel) shouldBe Some("Sync")
      CommandExecutionPolicy.parse("sync-job").map(_.modeLabel) shouldBe Some("JobSync")
      CommandExecutionPolicy.parse("async-job").map(_.modeLabel) shouldBe Some("JobAsync")
      CommandExecutionPolicy.parse("async-job-and-await").map(_.isDeprecatedCompatibilityMode) shouldBe Some(true)
      CommandExecutionPolicy.parse("sync-job-async-interface").map(_.isDeprecatedCompatibilityMode) shouldBe Some(true)
    }

    "parse command transaction semantics with strict defaults" in {
      Given("default command execution policy")
      val default = CommandExecutionPolicy.default

      Then("canonical command modes are transactionally strict unless explicitly relaxed")
      default.callerTransactionPolicy shouldBe CallerTransactionPolicy.JoinCaller
      default.eventTransactionRequirement shouldBe OperationEventTransactionRequirement.Required
      default.jobTransactionScope shouldBe JobTransactionScope.PerTask
      default.continuationEventTransactionRequirement shouldBe OperationEventTransactionRequirement.Required

      When("descriptor-style policy properties override the defaults")
      val parsed = CommandExecutionPolicy.parse(
        "managed-by-job=true,interface=sync,job-run=sync,caller-transaction-policy=new-transaction,event-transaction-requirement=best-effort,job-transaction-scope=whole-job,continuation-event-transaction-requirement=ignore"
      ).getOrElse(fail("policy should parse"))

      Then("the policy exposes the transaction semantics in its projection record")
      parsed.modeLabel shouldBe "JobSync"
      parsed.callerTransactionPolicy shouldBe CallerTransactionPolicy.NewTransaction
      parsed.eventTransactionRequirement shouldBe OperationEventTransactionRequirement.BestEffort
      parsed.jobTransactionScope shouldBe JobTransactionScope.WholeJob
      parsed.continuationEventTransactionRequirement shouldBe OperationEventTransactionRequirement.Ignore
      parsed.toRecord.getString("callerTransactionPolicy") shouldBe Some("new-transaction")
      parsed.toRecord.getString("eventTransactionRequirement") shouldBe Some("best-effort")
      parsed.toRecord.getString("jobTransactionScope") shouldBe Some("whole-job")
      parsed.toRecord.getString("continuationEventTransactionRequirement") shouldBe Some("ignore")
    }

    "use Sync for an unspecified command action" in {
      val component = TestComponentFactory.create("default_command_execution_mode", Protocol.empty)
      val action = _command_action("default_sync", "ok")

      _with_runtime_mode(RunMode.Command) {
        Given("a command action without execution metadata")
        When("executing through ComponentLogic")
        val result = component.logic.executeAction(action)

        Then("response is returned directly and no job is recorded")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 0, 0)
      }
    }

    "preserve explicit JobAsync command action behavior" in {
      val component = TestComponentFactory.create("explicit_async_command_execution_mode", Protocol.empty)
      val action = _command_action("explicit_async", "ok", mode = Some(CommandExecutionMode.JobAsync))
      val ctx = ExecutionContext.test()

      _with_runtime_mode(RunMode.Command) {
        Given("a command action that explicitly requests JobAsync")
        When("executing through ComponentLogic")
        val response = component.logic.executeAction(action, ctx).toOption.getOrElse(fail("execution failed"))
        val jobid = response match {
          case OperationResponse.Scalar(v) =>
            JobId.parse(v.toString).toOption.getOrElse(fail("response is not JobId scalar"))
          case _ =>
            fail("response is not scalar")
        }

        Then("interface returns a JobId and the job is recorded")
        jobid.value.nonEmpty shouldBe true
        OperationResponseFormatter.toResponse(
          _envelope_request("explicit_async", "job-async"),
          response,
          RunMode.Command,
          ctx.runtime.executionMetadata
        ) match {
          case Response.Json(value) =>
            value should include (""""data":null""")
            value should include (""""job":{"id":"""")
            value should not include (""""result"""")
          case other =>
            fail(s"unexpected response: ${other}")
        }
        _eventually_completed(component)
      }
    }

    "use Sync for command + SCRIPT combination" in {
      val component = TestComponentFactory.create("script_execution_mode", Protocol.empty)
      val action = _script_action()

      _with_runtime_mode(RunMode.Command) {
        Given("SCRIPT command action")
        When("executing through ComponentLogic in command runtime mode")
        val result = component.logic.executeAction(action)

        Then("response is returned and no job is recorded")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 0, 0)
      }
    }

    "use ScriptAction default JobSync outside command mode" in {
      val component = TestComponentFactory.create("script_execution_mode_non_command", Protocol.empty)
      val action = _script_action()

      _with_runtime_mode(RunMode.Script) {
        Given("SCRIPT action with default JobSync")
        When("executing through ComponentLogic in script runtime mode")
        val result = component.logic.executeAction(action)

        Then("response is returned and one completed job is recorded")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 1, 0)
      }
    }

    "allow framework meta parameter to force JobSync for command action" in {
      val component = TestComponentFactory.create("framework_meta_sync_job", Protocol.empty)
      val action = _command_action("meta_sync", "ok")
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(),
        CommandExecutionMode.JobSync
      )

      _with_runtime_mode(RunMode.Command) {
        Given("a command action with framework commandExecutionMode=JobSync")
        When("executing through ComponentLogic")
        val result = component.logic.executeAction(action, ctx)

        Then("result is awaited synchronously and completed under job lifecycle")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 1, 0)
      }
    }

    "record JobSyncWithAsyncCont primary command synchronously with continuation metadata" in {
      val component = TestComponentFactory.create("framework_meta_job_sync_with_async_cont", Protocol.empty)
      val action = _command_action("meta_sync_async_cont", "ok")
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(),
        CommandExecutionMode.JobSyncWithAsyncCont
      )

      _with_runtime_mode(RunMode.Command) {
        Given("a command action with framework commandExecutionMode=JobSyncWithAsyncCont")
        When("executing through ComponentLogic")
        val result = component.logic.executeAction(action, ctx)
        val jobid = ctx.runtime.executionMetadata.responseJobId
          .flatMap(x => JobId.parse(x).toOption)
          .getOrElse(fail("response job id is missing"))

        Then("primary result is returned synchronously and the Job records async-continuation intent")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        val model = component.jobEngine.query(jobid).getOrElse(fail("job read model is missing"))
        model.debug.parameters.get("command.execution.mode") shouldBe Some("JobSyncWithAsyncCont")
        model.debug.parameters.get("command.async-continuation") shouldBe Some("event-async-same-job-task")
        model.debug.executionNotes should contain ("async continuation via Event reception same-job task")

        And("canonical envelope exposes primary data, Job metadata, and continuation metadata")
        OperationResponseFormatter.toResponse(
          _envelope_request("meta_sync_async_cont", "job-sync-with-async-cont"),
          result.toOption.getOrElse(fail("primary response is missing")),
          RunMode.Command,
          ctx.runtime.executionMetadata
        ) match {
          case Response.Json(value) =>
            value should include (""""data":"ok"""")
            value should include (""""job":{"id":"""")
            value should include (""""continuation":{"mode":"event-async-same-job-task","policy":"async-same-job"}""")
            value should not include ("textus-execution")
            value should not include (""""result"""")
          case other =>
            fail(s"unexpected response: ${other}")
        }
      }
    }

    "drive JobSyncWithAsyncCont residual work through async Event continuation" in {
      val calls = ArrayBuffer.empty[String]
      val component = _event_component("job_sync_with_async_cont_event", calls)
      val action = _command_action("emitEvent", "unused")
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager),
        CommandExecutionMode.JobSyncWithAsyncCont
      )

      _with_runtime_mode(RunMode.Command) {
        Given("a JobSyncWithAsyncCont command that emits an event with async new-job reception")
        When("executing through ComponentLogic")
        val result = component.logic.executeAction(action, ctx)

        Then("the primary event emission returns synchronously")
        val outcome = result.toOption.collect {
          case OperationResponse.RecordResponse(record) =>
            record.getString("outcome")
        }.flatten.getOrElse(fail(s"unexpected primary response: ${result}"))
        outcome shouldBe ReceptionOutcome.Routed.toString

        And("the residual action runs through a continuation Task in the returned primary Job")
        _await_condition(calls.nonEmpty) shouldBe true
        calls.toVector shouldBe Vector("followup.action")
        val jobs = component.jobEngine.listJobs(limit = 20, persistentOnly = false)
        jobs.exists(_.debug.parameters.get("command.execution.mode").contains("JobSyncWithAsyncCont")) shouldBe true

        And("the returned primary JobId can trace the async continuation Task")
        val primaryjobid = ctx.runtime.executionMetadata.responseJobId
          .flatMap(x => JobId.parse(x).toOption)
          .getOrElse(fail("primary job id is missing"))
        val primaryjob = component.jobEngine.query(primaryjobid).getOrElse(fail("primary job read model is missing"))
        primaryjob.continuation.mode shouldBe Some("event-async-same-job-task")
        primaryjob.continuation.policy shouldBe Some("async-same-job")
        primaryjob.continuation.taskIds.nonEmpty shouldBe true
        primaryjob.tasks.tasks.map(_.taskId) should contain allElementsOf primaryjob.continuation.taskIds
        primaryjob.continuation.tasks.flatMap(_.action) should contain ("followup.action")
        primaryjob.tasks.tasks.filter(x => primaryjob.continuation.taskIds.contains(x.taskId)).flatMap(_.transactionRole) should contain ("own")
        primaryjob.tasks.tasks.filter(x => primaryjob.continuation.taskIds.contains(x.taskId)).flatMap(_.transactionScope) should contain ("per-task")
        primaryjob.lineage.receptionPolicy shouldBe Some(EventReceptionExecutionPolicy.AsyncSameJobSameSagaNewTransaction.modeName)
      }
    }

    "preserve deprecated AsyncJobAndAwait compatibility behavior" in {
      val component = TestComponentFactory.create("framework_meta_async_job_and_await", Protocol.empty)
      val action = _command_action("meta_async_job_and_await", "ok")
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(),
        CommandExecutionMode.AsyncJobAndAwait
      )

      _with_runtime_mode(RunMode.Command) {
        Given("a command action with deprecated commandExecutionMode=AsyncJobAndAwait")
        When("executing through ComponentLogic")
        val result = component.logic.executeAction(action, ctx)

        Then("the compatibility mode still returns the final result synchronously")
        result shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        _metrics(component) shouldBe (0, 0, 1, 0)
      }
    }

    "preserve deprecated SyncJobAsyncInterface compatibility behavior" in {
      val component = TestComponentFactory.create("framework_meta_sync_job_async_interface", Protocol.empty)
      val executed = new AtomicBoolean(false)
      val action = _command_action("meta_sync_async_interface", "ok", executed)
      val ctx = ExecutionContext.withFrameworkCommandExecutionMode(
        ExecutionContext.test(),
        CommandExecutionMode.SyncJobAsyncInterface
      )

      _with_runtime_mode(RunMode.Command) {
        Given("a command action with deprecated commandExecutionMode=SyncJobAsyncInterface")
        When("executing through ComponentLogic")
        val response = component.logic.executeAction(action, ctx).toOption.getOrElse(fail("execution failed"))
        val jobid = response match {
          case OperationResponse.Scalar(v) =>
            JobId.parse(v.toString).toOption.getOrElse(fail("response is not JobId scalar"))
          case _ =>
            fail("response is not scalar")
        }

        Then("interface returns JobId while execution already completed synchronously")
        response should not be OperationResponse.Scalar("ok")
        jobid.value.nonEmpty shouldBe true
        executed.get() shouldBe true
      }
    }
  }

  private def _script_action(): ScriptAction = {
    val request = Request.of(
      component = "SCRIPT",
      service = "DEFAULT",
      operation = "RUN"
    )
    ScriptAction("script_RUN", request, _ => "ok")
  }

  private def _command_action(
    operation: String,
    value: String,
    mode: Option[CommandExecutionMode] = None
  ): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(operation)
      override def commandExecutionMode: CommandExecutionMode =
        mode.getOrElse(super.commandExecutionMode)

      override def createCall(core: ActionCall.Core): ActionCall = {
        val self = this
        val c = core
        new ActionCall {
          override val core: ActionCall.Core = c
          override def action: Action = self
          def execute(): Consequence[OperationResponse] =
            Consequence.success(OperationResponse.Scalar(value))
        }
      }
    }

  private def _command_action(
    operation: String,
    value: String,
    executed: AtomicBoolean
  ): CommandAction =
    _command_action(operation, value, executed, None)

  private def _command_action(
    operation: String,
    value: String,
    executed: AtomicBoolean,
    mode: Option[CommandExecutionMode]
  ): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(operation)
      override def commandExecutionMode: CommandExecutionMode =
        mode.getOrElse(super.commandExecutionMode)

      override def createCall(core: ActionCall.Core): ActionCall = {
        val self = this
        val c = core
        new ActionCall {
          override val core: ActionCall.Core = c
          override def action: Action = self
          def execute(): Consequence[OperationResponse] = {
            executed.set(true)
            Consequence.success(OperationResponse.Scalar(value))
          }
        }
      }
    }

  private def _envelope_request(
    operation: String,
    mode: String
  ): Request =
    Request.ofOperation(operation).copy(
      properties = List(
        Property("textus.output.format", "json", None),
        Property("textus.output.shape", "envelope", None),
        Property("textus.command.execution-mode", mode, None)
      )
    )

  private def _event_component(
    name: String,
    calls: ArrayBuffer[String]
  ): Component = {
    val subsystem = TestComponentFactory.emptySubsystem("test")
    val component = new Component() {
      override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
        Vector(
          CmlEventDefinition(
            name = "primary.done",
            category = CmlEventCategory.NonActionEvent,
            kind = Some("done")
          )
        )

      override def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] =
        Vector(
          CmlSubscriptionDefinition(
            name = "primary-done-followup",
            eventName = "primary.done",
            route = DispatchRoute.Broadcast,
            actionName = "followup.action",
            declaredTargetUpperBound = 1
          )
        )

      override def eventReceptionRuleDefinitions: Vector[EventReceptionRule] =
        Vector(
          EventReceptionRule(
            name = "primary-done-async",
            condition = EventReceptionCondition(
              eventName = Some("primary.done"),
              eventKind = Some("done")
            ),
            policy = EventReceptionExecutionPolicy.AsyncSameJobSameSagaNewTransaction
          )
        )
    }
    val componentid = ComponentId(name)
    val core = Component.Core.create(
      name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty
    )
    val initialized = component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
    val store = EventStore.inMemory
    val engine = EventEngine.noop(DataStore.noop(), eventstore = store)
    val bus = EventBus.default(engine)
    val dispatcher = new ActionCallDispatcher {
      override def dispatchAction(actionname: String, event: DomainEvent): Consequence[Unit] = {
        val _ = event
        calls += actionname
        Consequence.unit
      }
    }
    val reception = EventReception.default(
      eventBus = bus,
      dispatcher = dispatcher,
      currentSubsystemName = Some(subsystem.name),
      currentComponentName = Some(name),
      jobEngine = Some(initialized.jobEngine)
    )
    initialized.eventReceptionDefinitions.foreach(reception.register)
    initialized.eventSubscriptionDefinitions.foreach(reception.registerSubscription)
    initialized.eventReceptionRuleDefinitions.foreach(reception.registerRule)
    initialized.withEventStore(store).withEventReception(reception)
  }

  private def _eventually_completed(component: Component): Unit = {
    _await_condition(_metrics(component)._3 > 0) shouldBe true
  }

  private def _await_condition(condition: => Boolean): Boolean = {
    var i = 0
    while (i < 100 && !condition) {
      Thread.sleep(10L)
      i += 1
    }
    condition
  }

  private def _metrics(component: Component): (Int, Int, Int, Int) =
    component.jobEngine.metrics match {
      case Some(metrics) =>
        (metrics.running, metrics.queued, metrics.completed, metrics.failed)
      case None =>
        fail("JobEngine metrics is unavailable")
    }

  private def _with_runtime_mode[T](
    mode: RunMode
  )(body: => T): T = {
    val execution = ExecutionContext.create()
    val httpDriver = FakeHttpDriver.okText("noop")
    val runtimeConfig = RuntimeConfig.default.copy(
      httpDriver = httpDriver,
      mode = mode
    )
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "component-logic-command-script-mode-spec",
      parent = None,
      observabilityContext = execution.observability,
      httpDriverOption = Some(httpDriver)
    ).core
    val context = new GlobalRuntimeContext(
      core = core,
      config = runtimeConfig,
      aliasResolver = AliasResolver.empty,
      runtimeMode = mode,
      commandExecutionMode = None,
      runtimeVersion = CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = CncfVersion.current
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(context)
    try body
    finally GlobalRuntimeContext.current = previous
  }
}
