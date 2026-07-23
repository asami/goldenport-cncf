package org.goldenport.cncf.operation.evaluation

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import cats.data.NonEmptyVector

import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction, CommandExecutionMode, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, SecurityContext}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobCommandMode, JobContext, JobControlCommand, JobControlOption, JobControlRequest, JobId, JobPersistencePolicy, JobResult, JobRunMode, JobSubmitOption, JobTask, TaskFailed, TaskId, TaskSucceeded}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.spi.evaluation.{CorpusEvaluationSink, CorpusEvaluationSinkSocket, DeterministicCorpusEvaluationSink}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, Subsystem}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.observation.Cause
import org.goldenport.value.BaseContent
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for automatic capture at the authorized operation
 * chokepoint.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationAutomaticCaptureSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterEach {
  private val _subsystems = ArrayBuffer.empty[Subsystem]

  override protected def afterEach(): Unit =
    try {
      _subsystems.foreach(_.shutdown())
      _subsystems.clear()
    } finally {
      super.afterEach()
  }

  "Automatic operation evaluation capture" should {
    "record canonical operation outcomes" which {
    "emit one structural start and terminal after an authorized operation" in {
      Given("an operation component with a connected deterministic Corpus sink")
      val fixture = _fixture()

      When("a successful query crosses the subsystem operation chokepoint")
      val result = fixture.subsystem.executeOperationResponse(_request("success"))

      Then("the business result is preserved and one correlated attempt is captured")
      result shouldBe Consequence.success(OperationResponse.Scalar("success"))
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      val start = fixture.sink.facts.head.asInstanceOf[OperationEvaluationStartFact]
      val terminal = fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact]
      terminal.outcome shouldBe OperationEvaluationOutcome.Success
      terminal.diagnostic shouldBe None
      terminal.correlation.executionId shouldBe start.correlation.executionId
      terminal.correlation.attemptId shouldBe start.correlation.attemptId
    }

    "capture a structured failure without changing its Conclusion" in {
      Given("the same component and connected sink")
      val fixture = _fixture()

      When("the ActionCall returns a structured failure")
      val result = fixture.subsystem.executeOperationResponse(_request("failure"))

      Then("the original failure remains canonical and the terminal fact classifies it")
      result shouldBe a[Consequence.Failure[_]]
      val terminal = fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact]
      terminal.outcome shouldBe OperationEvaluationOutcome.Failure
      terminal.diagnostic.map(_.diagnosticKey) shouldBe Some("argument")
    }

    "capture a request-construction failure after authorization" in {
      Given("an authorized operation whose applicative request construction fails")
      val fixture = _fixture()

      When("the post-authorization request validation rejects the invocation")
      val result = fixture.subsystem.executeOperationResponse(_request("requestFailure"))

      Then("the original failure and one structural start-terminal pair are retained")
      result shouldBe a[Consequence.Failure[_]]
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      val terminal = fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact]
      terminal.outcome shouldBe OperationEvaluationOutcome.Failure
      terminal.diagnostic.map(_.diagnosticKey) shouldBe Some("argument")
    }

    "classify a canonical timeout from its structured Conclusion" in {
      Given("an operation that returns a timeout Cause without changing its business failure")
      val fixture = _fixture()

      When("the timeout crosses the automatic capture chokepoint")
      val result = fixture.subsystem.executeOperationResponse(_request("timeout"))

      Then("the original failure remains canonical and the terminal outcome is timeout")
      result shouldBe a[Consequence.Failure[_]]
      val terminal = fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact]
      terminal.outcome shouldBe OperationEvaluationOutcome.Timeout
      terminal.diagnostic.flatMap(_.causeKind) shouldBe Some("timeout")
    }

    "capture a later framework response-binding failure as the canonical terminal outcome" in {
      Given("a successful ActionTask whose framework response binding will fail")
      val fixture = _fixture()
      val runtime = new OperationEvaluationDeliveryRuntime()
      try {
        val operation = _success(OperationEvaluationOperationIdentity.createC("evaluation", "operation", "success"))
        val prepared = _success(ExecutionContext.prepareOperationEvaluation(
          fixture.component.logic.executionContext(),
          operation
        ))
        val action = EvaluationQueryAction(_request("success"), failure = false)
        val task = ActionTask(ActionId.generate(), action, ActionEngine.create(), Some(fixture.component))
        val evaluated = new OperationEvaluationActionTask(
          task,
          new OperationEvaluationAttemptCapture(operation, runtime),
          (_, _) => Consequence.argumentInvalid("planned response-binding failure")
        )

        When("the business Action succeeds before framework response binding fails")
        val outcome = evaluated.run(prepared)

        Then("the Task and terminal fact both retain the binding failure")
        outcome shouldBe a[TaskFailed]
        fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
        val terminal = fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact]
        terminal.outcome shouldBe OperationEvaluationOutcome.Failure
        terminal.diagnostic.map(_.diagnosticKey) shouldBe Some("argument")
      } finally {
        runtime.close()
      }
    }

    "capture the worker attempt of an asynchronous Job under the returned JobId" in {
      Given("a JobAsync operation with a connected deterministic sink")
      val fixture = _fixture()

      When("the command returns its JobId and the worker completes")
      val submitted = fixture.subsystem.executeOperationResponse(_request("jobAsync"))
      val jobid = submitted.toOption.collect {
        case OperationResponse.Scalar(value) => JobId.parse(value.toString).toOption
      }.flatten.getOrElse(fail(s"JobId missing: $submitted"))
      fixture.component.logic.awaitJobResult(jobid) shouldBe Consequence.success(OperationResponse.Scalar("job-success"))

      Then("the automatic facts identify the worker Job and one logical execution")
      _await_fact_count(fixture.sink, 2) shouldBe true
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      val correlations = fixture.sink.facts.map(_.correlation)
      correlations.map(_.executionId).distinct.size shouldBe 1
      correlations.map(_.attemptId).distinct.size shouldBe 1
      correlations.flatMap(_.jobId).distinct shouldBe Vector(jobid)
      fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe OperationEvaluationOutcome.Success
    }

    "capture a query-only rejection after authorized Action construction" in {
      Given("an authorized Command operation invoked through the query-only surface")
      val fixture = _fixture()
      given ExecutionContext = fixture.component.logic.executionContext()

      When("the query-only boundary rejects the constructed Command Action")
      val result = fixture.subsystem.executeQueryOnlyWithMetadata(_request("jobAsync"))

      Then("the rejection remains canonical and receives exactly one attempt pair")
      result shouldBe a[Consequence.Failure[_]]
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
        OperationEvaluationOutcome.Failure
    }

    "capture Job admission rejection before a prepared operation task runs" in {
      Given("an authorized operation task and an invalid synchronous delayed submission")
      val fixture = _fixture()
      val action = EvaluationJobAction(_request("jobAsync"))
      val context = fixture.component.logic.executionContext()
      val rawtask = ActionTask(
        ActionId.generate(),
        action,
        fixture.component.actionEngine,
        Some(fixture.component)
      )
      val (task, preparedcontext) = _success(
        fixture.subsystem._prepare_operation_task(action, rawtask, context)
      )
      val option = JobSubmitOption(
        runMode = JobRunMode.Sync,
        scheduledStartAt = Some(context.clock.instant().plusSeconds(60L))
      )

      When("JobEngine rejects the submission before invoking the task")
      val result = fixture.subsystem.jobEngine.submit(List(task), preparedcontext, option)

      Then("the submission failure receives exactly one attempt pair without business execution")
      result shouldBe a[Consequence.Failure[_]]
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
        OperationEvaluationOutcome.Failure
    }
    }

    "protect framework execution boundaries" which {
    "isolate provider failure and disconnected optional sinks from business execution" in {
      Given("one component with a failing sink and another with no installed sink")
      val calls = new AtomicInteger(0)
      val identity = _success(OperationEvaluationSinkIdentity.createC(
        CorpusEvaluationSink.CONTRACT_NAME,
        "evaluation",
        "failing-corpus"
      ))
      val failing = new CorpusEvaluationSink {
        override val sinkIdentityOption = Some(identity)
        def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext) = {
          calls.incrementAndGet()
          Consequence.operationInvalid("planned provider failure")
        }
        def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext) = {
          calls.incrementAndGet()
          Consequence.operationInvalid("planned provider failure")
        }
        def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext) =
          Consequence.operationInvalid("not used")
      }
      val connected = _fixture(failing)
      val disconnectedsubsystem = _track(TestComponentFactory.emptySubsystem("evaluation-disconnected"))
      disconnectedsubsystem.add(_component(disconnectedsubsystem, None))

      When("both components execute the same successful operation")
      val connectedexecution = connected.subsystem.executeWithMetadata(_request("success"))
      val disconnectedresult = disconnectedsubsystem.executeOperationResponse(_request("success"))

      Then("auxiliary provider behavior never replaces the successful business result")
      connectedexecution.map(_.response) shouldBe Consequence.success(OperationResponse.Scalar("success"))
      disconnectedresult shouldBe Consequence.success(OperationResponse.Scalar("success"))
      calls.get() shouldBe 2
      val report = connectedexecution.toOption
        .flatMap(_.metadata.operationEvaluation)
        .getOrElse(fail("delivery report missing"))
      report.deliveries.map(_.status) shouldBe Vector.fill(2)(OperationEvaluationDeliveryStatus.Failed)
      report.deliveries.flatMap(_.limitationKinds) should contain only OperationEvaluationLimitationKind.ProviderFailure
    }

    "emit no fact when operation authorization rejects the request" in {
      Given("a production subsystem whose descriptor denies anonymous operation use")
      val subsystem = _track(TestComponentFactory.subsystemWithConfig(
        Map(RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue(OperationMode.Production.name)),
        name = "evaluation-authorization"
      ))
      val sink = _success(DeterministicCorpusEvaluationSink.createC("evaluation", "test-corpus"))
      val component = _component(subsystem, Some(sink))
      subsystem.withDescriptor(GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<test>"),
        subsystemName = "evaluation-authorization",
        componentBindings = Vector(GenericSubsystemComponentBinding("evaluation")),
        operationAuthorization = Map(
          "evaluation.operation.success" -> OperationAuthorizationRule(allowAnonymous = false)
        )
      )).add(component)

      When("the anonymous request is rejected before ActionCall construction")
      val result = subsystem.executeOperationResponse(_request("success"))

      Then("authorization remains canonical and no evaluation attempt exists")
      result shouldBe a[Consequence.Failure[_]]
      sink.facts shouldBe empty
    }

    "normalize a resolved legacy route into a mandatory capture identity" in {
      Given("an otherwise valid operation whose legacy route name is not a bounded evaluation name")
      val fixture = _fixture()

      When("the authorized operation crosses the automatic capture chokepoint")
      val result = fixture.subsystem.executeOperationResponse(_request("legacy operation"))

      Then("business execution succeeds and the resolved route still produces a correlated attempt")
      result shouldBe Consequence.success(OperationResponse.Scalar("success"))
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      val operations = fixture.sink.facts.map(_.correlation.operation)
      operations.distinct.size shouldBe 1
      operations.head.operation.print should startWith ("legacy_operation_")
    }

    "route public ComponentLogic execution through authorization and automatic capture" in {
      Given("an Action created by a component protocol and a connected sink")
      val fixture = _fixture()
      val action = _success(fixture.component.logic.makeOperationRequest(_request("success"))) match {
        case value: Action => value
        case other => fail(s"Action missing: $other")
      }

      When("an internal caller invokes the public ComponentLogic operation entry")
      val result = fixture.component.logic.executeAction(action, fixture.component.logic.executionContext())

      Then("the subsystem chokepoint authorizes and captures the operation")
      result shouldBe Consequence.success(OperationResponse.Scalar("success"))
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
    }

    "aggregate nested operation diagnostics without clearing the parent report" in {
      Given("a parent evaluation report and a child operation using the same RuntimeContext")
      val fixture = _fixture()
      val base = fixture.component.logic.executionContext()
      val parentidentity = _success(
        OperationEvaluationOperationIdentity.createC("evaluation", "operation", "parent")
      )
      val parentprepared = _success(ExecutionContext.prepareOperationEvaluation(base, parentidentity))
      val parentattempted = _success(ExecutionContext.beginOperationEvaluationAttempt(parentprepared))
      val parentfact = OperationEvaluationStartFact.create(
        OperationEvaluationFactId.create("parent", parentattempted.clock.instant(), parentattempted.idGeneration),
        parentattempted.operationEvaluation.correlation.getOrElse(fail("parent correlation missing")),
        parentattempted.clock.instant()
      )
      val sinkidentity = fixture.sink.sinkIdentityOption.getOrElse(fail("sink identity missing"))
      val parentresult = _success(OperationEvaluationDeliveryResult.createC(
        parentfact.id,
        sinkidentity,
        OperationEvaluationDeliveryStatus.Delivered
      ))
      base.runtime.noteOperationEvaluationDelivery(
        OperationEvaluationDeliveryDiagnostic.from(parentfact, parentresult)
      )
      val childidentity = _success(
        OperationEvaluationOperationIdentity.createC("evaluation", "operation", "success")
      )
      val childcontext = _success(
        ExecutionContext.prepareOperationEvaluation(parentattempted, childidentity)
      )
      val action = _success(fixture.component.logic.makeOperationRequest(_request("success"))) match {
        case value: Action => value
        case other => fail(s"Action missing: $other")
      }

      When("the nested operation crosses ComponentLogic execution")
      val result = fixture.component.logic.executeAction(action, childcontext)

      Then("the report retains the parent diagnostic and identifies the child diagnostics")
      result shouldBe Consequence.success(OperationResponse.Scalar("success"))
      val report = base.runtime.executionMetadata.operationEvaluation.getOrElse(fail("evaluation report missing"))
      report.deliveries.map(_.operation.operation.print) should contain ("parent")
      report.deliveries.map(_.operation.operation.print) should contain ("success")
      report.deliveries.count(_.operation.operation.print == "parent") shouldBe 1
      report.deliveries.count(_.operation.operation.print == "success") should be >= 2
    }

    "preserve an already resolved ad-hoc Action outside the operation route boundary" in {
      Given("a component Action whose request does not identify a resolvable operation route")
      val fixture = _fixture()
      val action = EvaluationQueryAction(Request.ofOperation("ad-hoc"), failure = false)

      When("the owning ComponentLogic executes the already resolved Action")
      val result = fixture.component.logic.executeAction(action, fixture.component.logic.executionContext())

      Then("legacy execution is preserved without inventing an automatic operation identity")
      result shouldBe Consequence.success(OperationResponse.Scalar("success"))
      fixture.sink.facts shouldBe empty
    }

    "capture Event continuation inline under its inherited Job context" in {
      Given("an authorized Event continuation running inside another Job task")
      val fixture = _fixture()
      val base = fixture.component.logic.executionContext()
      val occurredat = base.clock.instant()
      val outerjobid = JobId.create("event.outer", occurredat, base.idGeneration)
      val outertaskid = TaskId.create("event.outer", occurredat, base.idGeneration)
      val outeractionid = ActionId.create("event.outer", occurredat, base.idGeneration)
      val context = ExecutionContext.withJobContext(
        base,
        JobContext(
          jobId = Some(outerjobid),
          taskId = Some(outertaskid),
          actionId = Some(outeractionid),
          currentTask = Some(outertaskid)
        )
      )
      val action = EvaluationQueryAction(_request("success"), failure = false)

      When("the Event dispatcher executes the parsed Action in the same transaction")
      val result = fixture.component.logic.executeEventContinuationAction(action, context)

      Then("capture completes inline and remains correlated with the inherited Job")
      result shouldBe Consequence.success(OperationResponse.Scalar("success"))
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      fixture.sink.facts.flatMap(_.correlation.jobId).distinct shouldBe Vector(outerjobid)
      fixture.sink.facts.flatMap(_.correlation.taskId).distinct shouldBe Vector(outertaskid)
    }

    "decorate an internally submitted Job task before it reaches JobEngine" in {
      Given("a resolved Action intended for a Rule, Workflow, or JCL Job submission")
      val fixture = _fixture()
      val action = EvaluationQueryAction(
        Request.of(
          component = "evaluation",
          service = "operation",
          operation = "success",
          arguments = List(Argument("sample", "value"))
        ),
        failure = false
      )
      val context = fixture.component.logic.executionContext()
      val rawtask = ActionTask(ActionId.generate(), action, fixture.component.actionEngine, Some(fixture.component))
      val (task, preparedcontext) = _success(fixture.subsystem._prepare_operation_task(action, rawtask, context))

      When("the framework submits the prepared task directly to JobEngine")
      val jobid = _success(fixture.subsystem.jobEngine.submit(List(task), preparedcontext))

      Then("the decorator preserves Job metadata, defaults, result, and operation facts")
      fixture.subsystem.jobEngine.awaitResult(jobid, 1000L) shouldBe
        Consequence.success(JobResult.Success(OperationResponse.Scalar("success")))
      val readmodel = fixture.subsystem.jobEngine.query(jobid).getOrElse(fail("Job read model missing"))
      readmodel.persistence shouldBe JobPersistencePolicy.Ephemeral
      readmodel.debug.requestSummary should not be empty
      readmodel.debug.parameters.get("sample") shouldBe Some("value")
      _await_fact_count(fixture.sink, 2) shouldBe true
      fixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      fixture.sink.facts.flatMap(_.correlation.jobId).distinct shouldBe Vector(jobid)
    }

    "deliver a cross-component prepared task only to the target component sink" in {
      Given("source and target components with distinct Corpus sinks in one subsystem")
      val subsystem = _track(TestComponentFactory.emptySubsystem("evaluation-cross-component"))
      val sourcesink = _success(
        DeterministicCorpusEvaluationSink.createC("evaluationsource", "source-corpus")
      )
      val targetsink = _success(
        DeterministicCorpusEvaluationSink.createC("evaluationtarget", "target-corpus")
      )
      val source = _named_component(subsystem, "evaluationsource", Some(sourcesink))
      val target = _named_component(subsystem, "evaluationtarget", Some(targetsink))
      subsystem.add(source)
      subsystem.add(target)
      val base = source.logic.executionContext()
      val occurredat = base.clock.instant()
      val jobid = JobId.create("cross-component", occurredat, base.idGeneration)
      val taskid = TaskId.create("cross-component", occurredat, base.idGeneration)
      val actionid = ActionId.create("cross-component", occurredat, base.idGeneration)
      val context = ExecutionContext.withJobContext(
        base,
        JobContext(
          jobId = Some(jobid),
          taskId = Some(taskid),
          actionId = Some(actionid),
          currentTask = Some(taskid)
        )
      )
      val request = Request.of(
        component = "evaluationtarget",
        service = "operation",
        operation = "success"
      )
      val action = EvaluationQueryAction(request, failure = false)
      val rawtask = ActionTask(
        ActionId.generate(),
        action,
        target.actionEngine,
        Some(target)
      )

      When("the source prepares and runs the target operation through the internal chokepoint")
      val (task, preparedcontext) = _success(
        subsystem._prepare_operation_task(action, rawtask, context)
      )
      val outcome = task.run(preparedcontext)

      Then("the target sink owns the attempt while inherited execution state remains intact")
      outcome shouldBe TaskSucceeded(OperationResponse.Scalar("success"))
      (preparedcontext.security == context.security) shouldBe true
      (preparedcontext.jobContext == context.jobContext) shouldBe true
      sourcesink.facts shouldBe empty
      targetsink.facts.map(_.factKind.token) shouldBe Vector("operation-start", "operation-terminal")
      targetsink.facts.flatMap(_.correlation.jobId).distinct shouldBe Vector(jobid)
      targetsink.facts.flatMap(_.correlation.taskId).distinct shouldBe Vector(taskid)
    }

    "isolate capture bookkeeping failures from the canonical Task outcome" in {
      Given("capture clocks that fail while creating the start and terminal facts")
      val startfixture = _fixture()
      val terminalfixture = _fixture()

      def _run_(fixture: Fixture, failoncall: Int): org.goldenport.cncf.job.TaskOutcome = {
        val clock = new EvaluationFailingClock(Clock.fixed(Instant.parse("2026-07-23T01:00:00Z"), ZoneOffset.UTC))
        val context = _context_with_clock(fixture, clock)
        val identity = OperationEvaluationOperationIdentity.fromResolvedRoute("evaluation", "operation", "success")
        val prepared = _success(ExecutionContext.prepareOperationEvaluation(context, identity))
        val action = EvaluationQueryAction(_request("success"), failure = false)
        val task = ActionTask(ActionId.generate(), action, fixture.component.actionEngine, Some(fixture.component))
        val runtime = new OperationEvaluationDeliveryRuntime()
        try {
          clock.failOnCall(failoncall)
          new OperationEvaluationActionTask(
            task,
            new OperationEvaluationAttemptCapture(identity, runtime),
            (response, _) => Consequence.success(response)
          ).run(prepared)
        } finally {
          runtime.close()
        }
      }

      When("bookkeeping fails before start delivery or before terminal construction")
      val startoutcome = _run_(startfixture, 2)
      val terminaloutcome = _run_(terminalfixture, 3)

      Then("the business result remains successful and only constructible facts are retained")
      startoutcome shouldBe TaskSucceeded(OperationResponse.Scalar("success"))
      terminaloutcome shouldBe TaskSucceeded(OperationResponse.Scalar("success"))
      startfixture.sink.facts shouldBe empty
      terminalfixture.sink.facts.map(_.factKind.token) shouldBe Vector("operation-start")
    }

    "preserve interruption raised by an auxiliary canonical-outcome observer" in {
      Given("a synchronous Job task whose auxiliary observer is interrupted")
      val subsystem = _track(TestComponentFactory.emptySubsystem("evaluation-observer-interruption"))
      val context = ExecutionContext.create()
      val task = new JobTask {
        override val actionId = ActionId.generate()
        def run(context: ExecutionContext) =
          TaskSucceeded(OperationResponse.Scalar("success"))
        override def observeCanonicalOutcome(
          outcome: org.goldenport.cncf.job.TaskOutcome,
          context: ExecutionContext,
          cancelled: Boolean
        ): Unit =
          throw new InterruptedException("planned observer interruption")
      }

      When("JobEngine isolates the auxiliary observer from the successful Job outcome")
      try {
        val jobid = _success(subsystem.jobEngine.submit(
          List(task),
          context,
          JobSubmitOption(runMode = JobRunMode.Sync)
        ))

        Then("the Job succeeds while the caller interruption signal remains set")
        subsystem.jobEngine.awaitResult(jobid, 1000L) shouldBe
          Consequence.success(JobResult.Success(OperationResponse.Scalar("success")))
        Thread.currentThread.isInterrupted shouldBe true
      } finally {
        val _ = Thread.interrupted()
      }
    }
    }

    "preserve per-Task Job lifecycle outcomes" which {
    "classify cancellation requested while an admitted Job task is running" in {
      Given("a JobAsync operation held after automatic start capture")
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val fixture = _fixture(EvaluationBlockingJobOperation("jobCancelable", entered, release))
      val submitted = fixture.subsystem.executeOperationResponse(_request("jobCancelable"))
      val jobid = submitted.toOption.collect {
        case OperationResponse.Scalar(value) => JobId.parse(value.toString).toOption
      }.flatten.getOrElse(fail(s"JobId missing: $submitted"))
      entered.await(3L, TimeUnit.SECONDS) shouldBe true
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("the Job is cancelled before the admitted ActionCall returns successfully")
      try {
        fixture.component.logic.controlJob(
          jobid,
          JobControlRequest(
            JobControlCommand.Cancel,
            JobControlOption(mode = JobCommandMode.Async)
          )
        ) shouldBe a[Consequence.Success[_]]
        release.countDown()
        _await_fact_count(fixture.sink, 2) shouldBe true

        Then("the terminal fact follows canonical Job cancellation rather than Task success")
        fixture.sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe
          OperationEvaluationOutcome.Cancellation
      } finally {
        release.countDown()
      }
    }

    "reject cancellation after canonical Job settlement even while terminal delivery is pending" in {
      Given("a successful Job whose terminal sink is deliberately blocked after settlement")
      val terminalentered = new CountDownLatch(1)
      val terminalrelease = new CountDownLatch(1)
      val sink = new EvaluationBlockingTerminalSink(terminalentered, terminalrelease)
      val fixture = _fixture(sink)
      val submitted = fixture.subsystem.executeOperationResponse(_request("jobAsync"))
      val jobid = submitted.toOption.collect {
        case OperationResponse.Scalar(value) => JobId.parse(value.toString).toOption
      }.flatten.getOrElse(fail(s"JobId missing: $submitted"))
      terminalentered.await(3L, TimeUnit.SECONDS) shouldBe true
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("cancellation races with auxiliary terminal delivery after Job settlement")
      try {
        val cancellation = fixture.component.logic.controlJob(
          jobid,
          JobControlRequest(JobControlCommand.Cancel, JobControlOption(mode = JobCommandMode.Async))
        )

        Then("the settled success wins and the terminal fact cannot be reclassified as cancellation")
        cancellation shouldBe a[Consequence.Failure[_]]
        terminalrelease.countDown()
        _await_fact_count(sink, 2) shouldBe true
        fixture.component.logic.awaitJobResult(jobid) shouldBe Consequence.success(OperationResponse.Scalar("job-success"))
        sink.facts.last.asInstanceOf[OperationEvaluationTerminalFact].outcome shouldBe OperationEvaluationOutcome.Success
      } finally {
        terminalrelease.countDown()
      }
    }

    "complete automatic capture for compensation tasks" in {
      Given("a successful operation with compensation followed by a failing operation")
      val fixture = _fixture()
      val context = fixture.component.logic.executionContext()
      val compensationaction = EvaluationQueryAction(_request("compensate"), failure = false)
      val rawcompensation = ActionTask(
        ActionId.generate(),
        compensationaction,
        fixture.component.actionEngine,
        Some(fixture.component)
      )
      val (compensation, _) = _success(
        fixture.subsystem._prepare_operation_task(compensationaction, rawcompensation, context)
      )
      val primaryaction = EvaluationQueryAction(_request("success"), failure = false)
      val rawprimary = ActionTask(
        ActionId.generate(),
        primaryaction,
        fixture.component.actionEngine,
        Some(fixture.component),
        compensationActionRef = Some("evaluation.operation.compensate"),
        compensationTask = Some(compensation)
      )
      val failureaction = EvaluationQueryAction(_request("failure"), failure = true)
      val rawfailure = ActionTask(
        ActionId.generate(),
        failureaction,
        fixture.component.actionEngine,
        Some(fixture.component)
      )
      val (primary, primarycontext) = _success(
        fixture.subsystem._prepare_operation_task(primaryaction, rawprimary, context)
      )
      val (failure, _) = _success(
        fixture.subsystem._prepare_operation_task(failureaction, rawfailure, context)
      )

      When("JobEngine compensates the committed primary task")
      val jobid = _success(fixture.subsystem.jobEngine.submit(
        List(primary, failure),
        primarycontext,
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Sync
        )
      ))
      fixture.subsystem.jobEngine.awaitResult(jobid, 1000L).toOption.collect {
        case _: JobResult.Failure => true
      } shouldBe Some(true)

      Then("the primary, failing, and compensation attempts all receive terminal facts")
      _await_fact_count(fixture.sink, 6) shouldBe true
      val terminals = fixture.sink.facts.collect {
        case fact: OperationEvaluationTerminalFact =>
          fact.correlation.operation.operation.print -> fact.outcome
      }.toMap
      terminals should contain allOf (
        "success" -> OperationEvaluationOutcome.Success,
        "failure" -> OperationEvaluationOutcome.Failure,
        "compensate" -> OperationEvaluationOutcome.Success
      )
    }

    "retain target component ownership when a detached compensation task runs" in {
      Given("source and target components with separate sinks and a target compensation task")
      val subsystem = _track(TestComponentFactory.emptySubsystem("evaluation-cross-component-compensation"))
      val sourcesink = _success(
        DeterministicCorpusEvaluationSink.createC("evaluationsource", "source-corpus")
      )
      val targetsink = _success(
        DeterministicCorpusEvaluationSink.createC("evaluationtarget", "target-corpus")
      )
      val source = _named_component(subsystem, "evaluationsource", Some(sourcesink))
      val target = _named_component(subsystem, "evaluationtarget", Some(targetsink))
      subsystem.add(source)
      subsystem.add(target)
      val context = source.logic.executionContext()
      val compensationaction = EvaluationQueryAction(
        Request.of(
          component = "evaluationtarget",
          service = "operation",
          operation = "compensate"
        ),
        failure = false
      )
      val rawcompensation = ActionTask(
        ActionId.generate(),
        compensationaction,
        target.actionEngine,
        Some(target)
      )
      val (compensation, _) = _success(
        subsystem._prepare_operation_task(compensationaction, rawcompensation, context)
      )
      val primaryaction = EvaluationQueryAction(
        Request.of(
          component = "evaluationsource",
          service = "operation",
          operation = "success"
        ),
        failure = false
      )
      val rawprimary = ActionTask(
        ActionId.generate(),
        primaryaction,
        source.actionEngine,
        Some(source),
        compensationActionRef = Some("evaluationtarget.operation.compensate"),
        compensationTask = Some(compensation)
      )
      val failureaction = EvaluationQueryAction(
        Request.of(
          component = "evaluationsource",
          service = "operation",
          operation = "failure"
        ),
        failure = true
      )
      val rawfailure = ActionTask(
        ActionId.generate(),
        failureaction,
        source.actionEngine,
        Some(source)
      )
      val (primary, primarycontext) = _success(
        subsystem._prepare_operation_task(primaryaction, rawprimary, context)
      )
      val (failure, _) = _success(
        subsystem._prepare_operation_task(failureaction, rawfailure, context)
      )

      When("JobEngine runs the detached compensation under the primary Job context")
      val jobid = _success(subsystem.jobEngine.submit(
        List(primary, failure),
        primarycontext,
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Sync
        )
      ))
      subsystem.jobEngine.awaitResult(jobid, 1000L).toOption.collect {
        case _: JobResult.Failure => true
      } shouldBe Some(true)

      Then("primary facts stay with the source and compensation facts use only the target sink")
      _await_fact_count(sourcesink, 4) shouldBe true
      _await_fact_count(targetsink, 2) shouldBe true
      sourcesink.facts.collect {
        case fact: OperationEvaluationTerminalFact =>
          fact.correlation.operation.operation.print
      } should contain theSameElementsAs Vector("success", "failure")
      targetsink.facts.collect {
        case fact: OperationEvaluationTerminalFact =>
          fact.correlation.operation.operation.print
      } shouldBe Vector("compensate")
    }

    "retain each task outcome when a later task is cancelled" in {
      Given("a two-task Job whose first operation succeeds before the second blocks")
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val fixture = _fixture(EvaluationBlockingJobOperation("jobCancelable", entered, release))
      val context = fixture.component.logic.executionContext()
      val successaction = EvaluationQueryAction(_request("success"), failure = false)
      val rawsuccess = ActionTask(
        ActionId.generate(),
        successaction,
        fixture.component.actionEngine,
        Some(fixture.component)
      )
      val blockingaction = EvaluationBlockingJobAction(_request("jobCancelable"), entered, release)
      val rawblocking = ActionTask(
        ActionId.generate(),
        blockingaction,
        fixture.component.actionEngine,
        Some(fixture.component)
      )
      val (success, preparedcontext) = _success(
        fixture.subsystem._prepare_operation_task(successaction, rawsuccess, context)
      )
      val (blocking, _) = _success(
        fixture.subsystem._prepare_operation_task(blockingaction, rawblocking, context)
      )
      val jobid = _success(fixture.subsystem.jobEngine.submit(
        List(success, blocking),
        preparedcontext,
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Async
        )
      ))
      entered.await(3L, TimeUnit.SECONDS) shouldBe true
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("the Job is cancelled while only the second task is running")
      try {
        fixture.subsystem.jobEngine.control(
          jobid,
          JobControlRequest(JobControlCommand.Cancel, JobControlOption(mode = JobCommandMode.Async))
        ) shouldBe a[Consequence.Success[_]]
        release.countDown()

        Then("the first terminal remains success and only the running task is cancellation")
        _await_fact_count(fixture.sink, 4) shouldBe true
        val terminals = fixture.sink.facts.collect {
          case fact: OperationEvaluationTerminalFact =>
            fact.correlation.operation.operation.print -> fact.outcome
        }.toMap
        terminals should contain allOf (
          "success" -> OperationEvaluationOutcome.Success,
          "jobcancelable" -> OperationEvaluationOutcome.Cancellation
        )
      } finally {
        release.countDown()
      }
    }
    }

    "bound sink-induced recursion" which {
    "suppress delivery back into the currently active sink" in {
      Given("a connected sink identity already active in the caller context")
      val fixture = _fixture()
      val runtime = new OperationEvaluationDeliveryRuntime()
      val operation = _success(OperationEvaluationOperationIdentity.createC("evaluation", "operation", "success"))
      val prepared = _success(ExecutionContext.prepareOperationEvaluation(fixture.component.logic.executionContext(), operation))
      val attempted = _success(ExecutionContext.beginOperationEvaluationAttempt(prepared))
      val identity = fixture.sink.sinkIdentityOption.getOrElse(fail("sink identity missing"))
      val active = _success(ExecutionContext.withActiveOperationEvaluationSink(attempted, identity))
      val fact = OperationEvaluationStartFact.create(
        OperationEvaluationFactId.create("reentrant", active.clock.instant(), active.idGeneration),
        active.operationEvaluation.correlation.getOrElse(fail("correlation missing")),
        active.clock.instant()
      )

      When("automatic delivery reaches the same sink boundary")
      val results = runtime.deliverAutomatic(fact, active)
      runtime.close()

      Then("provider invocation is suppressed but the nested operation context remains valid")
      results.map(_.status) shouldBe Vector(OperationEvaluationDeliveryStatus.Limited)
      results.flatMap(_.limitations).map(_.kind) shouldBe Vector(OperationEvaluationLimitationKind.ReentrantSuppressed)
      fixture.sink.facts shouldBe empty
    }

    "apply the explicit cross-sink policy before automatic provider invocation" in {
      Given("two equivalent Corpus targets reached from an active Experiment sink")
      val deniedfixture = _fixture()
      val allowedfixture = _fixture()
      val deniedruntime = new OperationEvaluationDeliveryRuntime()
      val allowedruntime = new OperationEvaluationDeliveryRuntime()
      val operation = _success(OperationEvaluationOperationIdentity.createC(
        "evaluation",
        "operation",
        "success"
      ))
      val experiment = _success(OperationEvaluationSinkIdentity.createC(
        "experiment-evaluation-sink",
        "evaluation",
        "textus-experiment"
      ))
      val route = _success(OperationEvaluationCrossSinkRoute.createC(
        "experiment-evaluation-sink",
        "corpus-evaluation-sink"
      ))
      val policy = _success(OperationEvaluationCrossSinkPolicy.createC(Vector(route), 2))

      def _active_context_(fixture: Fixture): ExecutionContext = {
        val prepared = _success(ExecutionContext.prepareOperationEvaluation(
          fixture.component.logic.executionContext(),
          operation
        ))
        val attempted = _success(ExecutionContext.beginOperationEvaluationAttempt(prepared))
        _success(ExecutionContext.withActiveOperationEvaluationSink(attempted, experiment))
      }

      val deniedcontext = _active_context_(deniedfixture)
      val activeallowed = _active_context_(allowedfixture)
      val allowedcontext = activeallowed.withScope(
        ScopeContext.withOperationEvaluationCrossSinkPolicy(
          activeallowed.cncfCore.scope,
          policy
        )
      )
      val deniedfact = OperationEvaluationStartFact.create(
        OperationEvaluationFactId.create(
          "cross-sink-denied",
          deniedcontext.clock.instant(),
          deniedcontext.idGeneration
        ),
        deniedcontext.operationEvaluation.correlation.getOrElse(fail("denied correlation missing")),
        deniedcontext.clock.instant()
      )
      val allowedfact = OperationEvaluationStartFact.create(
        OperationEvaluationFactId.create(
          "cross-sink-allowed",
          allowedcontext.clock.instant(),
          allowedcontext.idGeneration
        ),
        allowedcontext.operationEvaluation.correlation.getOrElse(fail("allowed correlation missing")),
        allowedcontext.clock.instant()
      )

      try {
        When("automatic delivery runs under default-deny and allowlisted policies")
        val denied = deniedruntime.deliverAutomatic(deniedfact, deniedcontext)
        val allowed = allowedruntime.deliverAutomatic(allowedfact, allowedcontext)

        Then("only the explicitly allowlisted route invokes its provider")
        denied.map(_.status) shouldBe Vector(OperationEvaluationDeliveryStatus.Limited)
        denied.flatMap(_.limitations).map(_.kind) shouldBe
          Vector(OperationEvaluationLimitationKind.CrossSinkSuppressed)
        deniedfixture.sink.facts shouldBe empty
        allowed.map(_.status) shouldBe Vector(OperationEvaluationDeliveryStatus.Delivered)
        allowedfixture.sink.facts shouldBe Vector(allowedfact)
      } finally {
        deniedruntime.close()
        allowedruntime.close()
      }
    }
    }
  }

  private final case class Fixture(
    subsystem: Subsystem,
    sink: DeterministicCorpusEvaluationSink,
    component: EvaluationComponent
  )

  private def _fixture(): Fixture = {
    val subsystem = _track(TestComponentFactory.emptySubsystem("evaluation-automatic"))
    val sink = _success(DeterministicCorpusEvaluationSink.createC("evaluation", "test-corpus"))
    val component = _component(subsystem, Some(sink))
    subsystem.add(component)
    Fixture(subsystem, sink, component)
  }

  private def _fixture(sink: CorpusEvaluationSink): Fixture = {
    val subsystem = _track(TestComponentFactory.emptySubsystem("evaluation-provider-failure"))
    val component = _component(subsystem, Some(sink))
    subsystem.add(component)
    Fixture(subsystem, _success(DeterministicCorpusEvaluationSink.createC("unused", "unused")), component)
  }

  private def _fixture(operation: spec.OperationDefinition): Fixture = {
    val subsystem = _track(TestComponentFactory.emptySubsystem("evaluation-cancellation"))
    val sink = _success(DeterministicCorpusEvaluationSink.createC("evaluation", "test-corpus"))
    val component = _component(subsystem, Some(sink), Vector(operation))
    subsystem.add(component)
    Fixture(subsystem, sink, component)
  }

  private def _component(
    subsystem: Subsystem,
    sink: Option[CorpusEvaluationSink],
    additionaloperations: Vector[spec.OperationDefinition] = Vector.empty
  ): EvaluationComponent =
    _named_component(subsystem, "evaluation", sink, additionaloperations)

  private def _named_component(
    subsystem: Subsystem,
    componentname: String,
    sink: Option[CorpusEvaluationSink],
    additionaloperations: Vector[spec.OperationDefinition] = Vector.empty
  ): EvaluationComponent = {
    val operations = NonEmptyVector.fromVectorUnsafe(Vector(
      EvaluationOperation("success", failure = false),
      EvaluationOperation("failure", failure = true),
      EvaluationRequestFailureOperation("requestFailure"),
      EvaluationOperation("compensate", failure = false),
      EvaluationTimeoutOperation("timeout"),
      EvaluationJobOperation("jobAsync"),
      EvaluationOperation("legacy operation", failure = false)
    ) ++ additionaloperations)
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(
      spec.ServiceDefinition("operation", spec.OperationDefinitionGroup(operations))
    )))
    val component = new EvaluationComponent
    val componentid = ComponentId(componentname)
    val core = Component.Core.create(
      componentname,
      componentid,
      ComponentInstanceId.default(componentid),
      protocol
    )
    sink.foreach(component.installSpi)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Main)).asInstanceOf[EvaluationComponent]
  }

  private def _request(operation: String): Request =
    Request.of(component = "evaluation", service = "operation", operation = operation)

  private def _track(subsystem: Subsystem): Subsystem = {
    _subsystems += subsystem
    subsystem
  }

  private def _await_fact_count(
    sink: DeterministicCorpusEvaluationSink,
    count: Int
  ): Boolean = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
    while (sink.facts.size < count && System.nanoTime() < deadline)
      Thread.sleep(5L)
    sink.facts.size >= count
  }

  private def _await_fact_count(
    sink: EvaluationBlockingTerminalSink,
    count: Int
  ): Boolean = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
    while (sink.facts.size < count && System.nanoTime() < deadline)
      Thread.sleep(5L)
    sink.facts.size >= count
  }

  private def _context_with_clock(
    fixture: Fixture,
    clock: Clock
  ): ExecutionContext = {
    val base = ExecutionContext.create(clock).asInstanceOf[ExecutionContext.Instance]
    val installed = fixture.component.logic.executionContext()
    base.copy(cncfCore = base.cncfCore.copy(
      scope = installed.cncfCore.scope,
      runtime = installed.runtime
    ))
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(result.toString))
}

private final class EvaluationFailingClock(delegate: Clock) extends Clock {
  private val _calls = new AtomicInteger(0)
  @volatile private var _failure_call = Int.MaxValue

  def failOnCall(value: Int): Unit = {
    _calls.set(0)
    _failure_call = value
  }

  def getZone: ZoneId = delegate.getZone
  override def withZone(zone: ZoneId): Clock = new EvaluationFailingClock(delegate.withZone(zone))
  def instant(): Instant =
    if (_calls.incrementAndGet() == _failure_call)
      throw new IllegalStateException("planned capture clock failure")
    else
      delegate.instant()
}

private final class EvaluationBlockingTerminalSink(
  terminalEntered: CountDownLatch,
  terminalRelease: CountDownLatch
) extends CorpusEvaluationSink {
  private val _sink_identity = _success(OperationEvaluationSinkIdentity.createC(
    CorpusEvaluationSink.CONTRACT_NAME,
    "evaluation",
    "blocking-terminal"
  ))
  private val _facts = new ConcurrentLinkedQueue[OperationEvaluationFact]()

  override val sinkIdentityOption: Option[OperationEvaluationSinkIdentity] = Some(_sink_identity)
  def facts: Vector[OperationEvaluationFact] = _facts.iterator.asScala.toVector

  def recordStart(fact: OperationEvaluationStartFact)(using ExecutionContext) = _record(fact)

  def recordTerminal(fact: OperationEvaluationTerminalFact)(using ExecutionContext) = {
    terminalEntered.countDown()
    terminalRelease.await()
    _record(fact)
  }

  def submitCandidate(fact: CorpusCandidateFact)(using ExecutionContext) = _record(fact)

  private def _record(fact: OperationEvaluationFact): Consequence[OperationEvaluationDeliveryResult] = {
    _facts.add(fact)
    OperationEvaluationDeliveryResult.createC(
      fact.id,
      _sink_identity,
      OperationEvaluationDeliveryStatus.Delivered
    )
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(throw new IllegalArgumentException(result.toString))
}

private final class EvaluationComponent extends Component with CorpusEvaluationSinkSocket

private final case class EvaluationOperation(
  operationname: String,
  failure: Boolean
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(EvaluationQueryAction(request, failure))
}

private final case class EvaluationRequestFailureOperation(
  operationname: String
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.argumentInvalid("planned request construction failure")
}

private final case class EvaluationQueryAction(
  request: Request,
  failure: Boolean
) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall = EvaluationActionCall(core, failure)
}

private final case class EvaluationActionCall(
  core: ActionCall.Core,
  failure: Boolean
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    if (failure) Consequence.argumentInvalid("evaluation failure")
    else Consequence.success(OperationResponse.Scalar("success"))
}

private final case class EvaluationJobOperation(
  operationname: String
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(EvaluationJobAction(request))
}

private final case class EvaluationTimeoutOperation(
  operationname: String
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(EvaluationTimeoutQueryAction(request))
}

private final case class EvaluationTimeoutQueryAction(
  request: Request
) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall = EvaluationTimeoutActionCall(core)
}

private final case class EvaluationTimeoutActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.serviceUnavailable("planned evaluation timeout", Cause.Kind.Timeout, Seq.empty)
}

private final case class EvaluationJobAction(
  request: Request
) extends CommandAction {
  override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.JobAsync
  def createCall(core: ActionCall.Core): ActionCall = EvaluationJobActionCall(core)
}

private final case class EvaluationJobActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar("job-success"))
}

private final case class EvaluationBlockingJobOperation(
  operationname: String,
  entered: CountDownLatch,
  release: CountDownLatch
) extends spec.OperationDefinition {
  val specification = spec.OperationDefinition.Specification(
    name = operationname,
    request = spec.RequestDefinition(),
    response = spec.ResponseDefinition.void
  )

  def createOperationRequest(request: Request): Consequence[OperationRequest] =
    Consequence.success(EvaluationBlockingJobAction(request, entered, release))
}

private final case class EvaluationBlockingJobAction(
  request: Request,
  entered: CountDownLatch,
  release: CountDownLatch
) extends CommandAction {
  override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.JobAsync
  def createCall(core: ActionCall.Core): ActionCall = EvaluationBlockingJobActionCall(core, entered, release)
}

private final case class EvaluationBlockingJobActionCall(
  core: ActionCall.Core,
  entered: CountDownLatch,
  release: CountDownLatch
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] = {
    entered.countDown()
    release.await()
    Consequence.success(OperationResponse.Scalar("cancelled-after-admission"))
  }
}
