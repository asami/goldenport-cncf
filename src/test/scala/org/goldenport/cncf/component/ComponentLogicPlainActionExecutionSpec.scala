package org.goldenport.cncf.component

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import scala.collection.mutable.ArrayBuffer

import cats.data.NonEmptyVector
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode, CommandExecutionPolicy, FunctionalActionCall, QueryAction}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, ExecutionInvocationIdentity, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionAdmission, HttpExecutionGrant, HttpExecutionLimits, HttpExecutionMethod}
import org.goldenport.cncf.job.{ActionId, ActionTask, InMemoryJobEngine, JobEngine, JobId, JobPersistencePolicy, JobResult, JobStatus, JobSubmitOption, JobTaskStatus}
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.operation.evaluation.{OperationEvaluationAdmissionDiagnostic, OperationEvaluationExecutionReport, OperationEvaluationOperationIdentity}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkTermination}
import org.goldenport.bag.Bag
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since Aug. 12, 2026
 * @version Oct. 22, 2026
 * @author ASAMI, Tomoharu
 */
final class ComponentLogicPlainActionExecutionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with BeforeAndAfterEach {
  private val _test_subsystems = ArrayBuffer.empty[org.goldenport.cncf.subsystem.Subsystem]
  private val _test_job_engines = ArrayBuffer.empty[JobEngine]

  private val _e1 = afterWord(
    "in spec:action-execution-semantics, example:E1, rules:R1,R2,R3,R9,R10,R11,R12, phase:57.1, slice:AES-03"
  )
  private val _e2 = afterWord(
    "in spec:action-execution-semantics, example:E2, rules:R1,R3,R9, phase:57.1, slice:AES-03"
  )
  private val _e3 = afterWord(
    "in spec:action-execution-semantics, example:E3, rules:R2,R4,R11, phase:57.1, slice:AES-02"
  )
  private val _e9 = afterWord(
    "in spec:action-execution-semantics, example:E9, rules:R5,R6,R13, phase:57.2, slice:AES-05A"
  )

  override protected def afterEach(): Unit =
    try {
      _test_job_engines.reverse.foreach(_.shutdown())
    } finally {
      try {
        _test_subsystems.foreach(_.shutdown())
      } finally {
        _test_job_engines.clear()
        _test_subsystems.clear()
        super.afterEach()
      }
    }

  "ComponentLogic plain Action execution" should {
    "E1 route-resolved plain Action" must _e1 {
      "return the exact scalar or record response without an implicit Job" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R1,R2,R3,R9,R10,R11,R12; Example: E1; a route-resolved component with an unclassified plain Action")
        val component = _routed_component("plain-direct-default", "plain-default", "OTHER")
        val payloads = Vector(
          OperationResponse.Scalar("plain-scalar"),
          OperationResponse.RecordResponse(Record.data("status" -> "plain-record"))
        )
        val property = Prop.forAll(Gen.oneOf(payloads)) { response =>
          val action = _plain_action("plain-default", response, componentname = Some(component.name))
          val result = component.logic.executeAction(action, ExecutionContext.test())
          result == Consequence.success(response) && _jobs(component).isEmpty
        }

        When("the bounded response-identity property is evaluated")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

        Then("each plain Action response is returned directly and no Job read model exists")
        checked.passed shouldBe true
        payloads.foreach { response =>
          val action = _plain_action("plain-default", response, componentname = Some(component.name))
          val context = ExecutionContext.test()
          val result = component.logic.executeAction(action, context)
          result shouldBe Consequence.success(response)
          _jobs(component) shouldBe empty
          ExecutionContext.currentExecutionResponse(context) shouldBe Some(
            RuntimeContext.ExecutionResponseMetadata.direct
          )
        }
      }

      "keep a plain Action direct when traceJob is enabled" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R3,R4; Example: E1; a plain Action and an execution context with explicit debug traceJob")
        val component = _routed_component("plain-direct-trace", "plain-trace", "OTHER")
        val response = OperationResponse.Scalar("plain-trace-response")
        val action = _plain_action("plain-trace", response, componentname = Some(component.name))
        val context = ExecutionContext.withFrameworkTraceJobEnabled(ExecutionContext.test(), enabled = true)

        When("the plain Action executes with traceJob enabled")
        val result = component.logic.executeAction(action, context)

        Then("traceJob does not turn the plain route into a debug or persistent Job")
        result shouldBe Consequence.success(response)
        _jobs(component) shouldBe empty
        component.jobEngine.listJobs(persistentOnly = true) shouldBe empty
      }

      "return a structured direct failure without Job state" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R12; Example: E1; a plain Action whose bound ActionCall returns a structured failure")
        val component = _routed_component("plain-direct-failure", "plain-failure", "OTHER")
        val action = _plain_failure_action("plain-failure", Some(component.name))

        When("the failing plain Action executes")
        val result = component.logic.executeAction(action, ExecutionContext.test())

        Then("the failure is synchronous and no Job or read-model state is created")
        result shouldBe a[Consequence.Failure[?]]
        result match {
          case Consequence.Failure(conclusion) => conclusion.show should include("plain failure")
          case other => fail(s"expected structured failure, got $other")
        }
        _jobs(component) shouldBe empty
      }
      "bind ActionCall context to the action child scope" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R9; Example: E1; a plain Action that records its bound ActionCall execution context")
        val component = _component("plain-action-scope")
        val observed = new AtomicReference[Option[ExecutionContext]](None)
        val action = _plain_action(
          "plain-action-scope",
          OperationResponse.Scalar("scope-response"),
          observed = observed
        )

        When("the Action is executed")
        val result = component.logic.executeAction(action, ExecutionContext.test())

        Then("the ActionCall sees an Action child scope")
        result shouldBe Consequence.success(OperationResponse.Scalar("scope-response"))
        val bound = observed.get().getOrElse(fail("ActionCall context was not captured"))
        bound.scope.kind shouldBe ScopeKind.Action
        bound.scope.name shouldBe action.name
      }

      Vector("target-only", "caller-only", "no-grants").foreach { grantcase =>
        s"use the target-component UoW owner from a self-bound caller with $grantcase HTTP authority" in {
          Given(s"Spec: docs/spec/action-execution-semantics.md; Rules: R9,R10; Example: E1; distinct caller and target components, $grantcase inherited HTTP grants, and an already self-bound caller Action runtime")
          val caller = _component(s"uow-caller-$grantcase")
          val target = _component(s"uow-target-$grantcase")
          val grants = grantcase match {
            case "target-only" => Map(target.componentId -> HttpExecutionGrant(
              Vector("http://127.0.0.1:8089"), Set(HttpExecutionMethod.Post)))
            case "caller-only" => Map(caller.componentId -> HttpExecutionGrant(
              Vector("http://127.0.0.1:8089"), Set(HttpExecutionMethod.Post)))
            case _ => Map.empty[ComponentId, HttpExecutionGrant]
          }
          val admission = HttpExecutionAdmission.fromRuntimeC(
            "http://127.0.0.1:8089/", HttpExecutionLimits(1000, 1000, 4096), grants
          ).getOrElse(fail("HTTP authority fixture was not admitted"))
          val driver = new ActionOwnerHttpDriver
          val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
            .asInstanceOf[ExecutionContext.Instance]
          val root = ScopeContext.Instance(base.runtime.core.copy(
            httpDriverOption = Some(driver), httpExecutionAdmissionOption = Some(admission)
          ))
          caller.withScopeContext(root)
          target.withScopeContext(root)
          val supplied = _self_bound_action_context(base, caller, root)
          val parentruntime = supplied.runtime
          val parentscope = supplied.scope
          val parentuow = supplied.unitOfWork
          val parentinterpreter = supplied.unitOfWorkInterpreter
          val parentcommits = new AtomicInteger
          parentuow.stagePostCommitC {
            parentcommits.incrementAndGet()
            Consequence.unit
          }
          val observed = new AtomicReference[Option[ExecutionContext]](None)
          val action = _http_owner_action(target, observed)
          supplied.unitOfWork.executionContext should be theSameInstanceAs supplied

          When("the target component executes its ordinary ActionCall and declarative HTTP POST through the runtime interpreter")
          val result = target.logic.executeAction(action, supplied)

          Then("only target authority admits the actual effect, and frontend and interpreted Action ownership agree")
          if (grantcase == "target-only") {
            result shouldBe Consequence.success(OperationResponse.Scalar("target-uow-response"))
            driver.calls shouldBe 1
          } else {
            result shouldBe a[Consequence.Failure[?]]
            result match {
              case Consequence.Failure(conclusion) =>
                ConclusionDiagnostics.classify(conclusion).policy shouldBe Some("http.execution.grant")
              case other => fail(s"expected target grant denial, got $other")
            }
            driver.calls shouldBe 0
          }
          val bound = observed.get().getOrElse(fail("target ActionCall context was not captured"))
          bound.scope.kind shouldBe ScopeKind.Action
          bound.scope.name shouldBe action.name
          bound.scope.parent shouldBe Some(target.scopeContext)
          bound.unitOfWork.executionContext should be theSameInstanceAs bound
          bound.unitOfWork.executionContext.scope.parent shouldBe Some(target.scopeContext)
          bound.scope.httpExecutionAdmissionOption shouldBe Some(admission)
          bound.unitOfWork should not be theSameInstanceAs(parentuow)
          bound.security should be theSameInstanceAs supplied.security
          bound.core should be theSameInstanceAs supplied.core
          bound.executionControl shouldBe supplied.executionControl
          bound.idGeneration should be theSameInstanceAs supplied.idGeneration
          bound.observability should be theSameInstanceAs supplied.observability
          bound.jobContext shouldBe supplied.jobContext
          bound.framework shouldBe supplied.framework
          if (grantcase == "target-only")
            bound.unitOfWork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)

          And("child completion leaves the original parent context, interpreter, UoW and pending callback untouched")
          supplied.scope should be theSameInstanceAs parentscope
          supplied.runtime should be theSameInstanceAs parentruntime
          supplied.unitOfWork should be theSameInstanceAs parentuow
          supplied.unitOfWorkInterpreter should be theSameInstanceAs parentinterpreter
          parentuow.executionContext should be theSameInstanceAs supplied
          parentuow.lastCommitTermination shouldBe None
          parentcommits.get() shouldBe 0
        }
      }
    }

    "E2 no-route component embedding" must _e2 {
      "preserve supplied context for no-route component embedding" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R1,R3,R9; Example: E2; a component embedding with no operation route and a supplied security context")
        val component = _component("plain-no-route-embedding")
        val observed = new AtomicReference[Option[ExecutionContext]](None)
        val response = OperationResponse.RecordResponse(Record.data("embedded" -> true))
        val action = _plain_action("embedded-no-route", response, observed = observed)
        val supplied = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
        val supplieduow = supplied.unitOfWork
        val suppliedcore = supplied.runtime.core
        val suppliedowner = supplied.entitySpace

        When("the embedded Action is executed through ComponentLogic")
        val result = component.logic.executeAction(action, supplied)

        Then("the direct response, callee EntitySpace owner and other supplied context fields are retained without changing the parent")
        result shouldBe Consequence.success(response)
        _jobs(component) shouldBe empty
        val bound = observed.get().getOrElse(fail("bound ActionCall context was not observed"))
        bound.security shouldBe supplied.security
        bound.runtime should not be theSameInstanceAs(supplied.runtime)
        bound.unitOfWork should not be theSameInstanceAs(supplieduow)
        bound.unitOfWork.executionContext should be theSameInstanceAs bound
        bound.entitySpace should be theSameInstanceAs component.entitySpace
        bound.runtime.entitySpace should be theSameInstanceAs component.entitySpace
        bound.runtime.core.copy(entityspace = suppliedcore.entityspace) shouldBe suppliedcore
        bound.runtime.context shouldBe supplied.runtime.context
        bound.runtime.toToken shouldBe supplied.runtime.toToken
        bound.runtime.operationMode shouldBe supplied.runtime.operationMode
        bound.runtime.transitionValidationHook should be theSameInstanceAs supplied.runtime.transitionValidationHook
        bound.runtime.entityCreateDefaultsPolicy shouldBe supplied.runtime.entityCreateDefaultsPolicy
        bound.idGeneration shouldBe supplied.idGeneration
        bound.operationEvaluation shouldBe supplied.operationEvaluation
        bound.scope.kind shouldBe ScopeKind.Action
        bound.scope.name shouldBe action.name
        bound.operationEvaluation.invocation shouldBe None
        bound.jobContext.jobId shouldBe None
        supplied.runtime.core should be theSameInstanceAs suppliedcore
        supplied.entitySpace should be theSameInstanceAs suppliedowner
        supplied.unitOfWork should be theSameInstanceAs supplieduow
        supplieduow.executionContext should be theSameInstanceAs supplied
      }
    }

    "E3 query and trace-Job behavior" must _e3 {
      "execute QueryAction directly by default without a Job" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2,R4,R11; Example: E3; a QueryAction with no explicit trace request")
        val component = _routed_component("query-direct", "query-direct", "QUERY")
        val response = OperationResponse.Scalar("query-response")
        val action = _query_action("query-direct", response, Some(component.name))

        When("the query executes normally")
        val context = ExecutionContext.test()
        val direct = component.logic.executeAction(action, context)

        Then("the default query route returns directly without a Job")
        direct shouldBe Consequence.success(response)
        _jobs(component) shouldBe empty
        ExecutionContext.currentExecutionResponse(context) shouldBe Some(
          RuntimeContext.ExecutionResponseMetadata.direct
        )
      }

      "trace QueryAction through one persistent synchronous Job explicitly" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2,R4,R11; Example: E3; a QueryAction with explicit traceJob enabled")
        val component = _routed_component("query-trace", "query-trace", "QUERY")
        val response = OperationResponse.Scalar("query-response")
        val action = _query_action("query-trace", response, Some(component.name))
        val tracecontext = ExecutionContext.withFrameworkTraceJobEnabled(ExecutionContext.test(), enabled = true)

        When("the traced query executes")
        val traced = component.logic.executeAction(action, tracecontext)

        Then("the traced query awaits one persistent synchronous Job and keeps the response exact")
        traced shouldBe Consequence.success(response)
        val jobs = component.jobEngine.listJobs(persistentOnly = false)
        jobs should have size 1
        jobs.head.persistence shouldBe JobPersistencePolicy.Persistent
        jobs.head.status shouldBe JobStatus.Succeeded
        jobs.head.result shouldBe Some(response)
        ExecutionContext.currentExecutionResponse(tracecontext) shouldBe Some(
          RuntimeContext.ExecutionResponseMetadata.queryTraceJobResult
        )
      }
    }

    "E9 execution response transport metadata" must _e9 {
      "execute a CommandAction directly by default" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a CommandAction with the default synchronous direct policy")
        val component = _routed_component("command-direct-default", "command-direct", "COMMAND")
        val response = OperationResponse.Scalar("command-response")
        val action = _command_action("command-direct", response, CommandExecutionMode.Sync, Some(component.name))

        When("the command executes")
        val context = ExecutionContext.test()
        val result = component.logic.executeAction(action, context)

        Then("the command response is direct and no Job is created")
        result shouldBe Consequence.success(response)
        _jobs(component) shouldBe empty
        ExecutionContext.currentExecutionResponse(context) shouldBe Some(
          RuntimeContext.ExecutionResponseMetadata.direct
        )
      }

      "return a parseable submitted JobId for an explicitly asynchronous CommandAction" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a CommandAction with explicit JobAsync policy and a non-starting scheduler")
        val executed = new AtomicBoolean(false)
        val manualengine = InMemoryJobEngine.create(
          InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false)
        )
        val component = _routed_component(
          "command-job-async",
          "command-job-async",
          "COMMAND",
          jobengine = Some(manualengine)
        )
        val action = _command_action(
          "command-job-async",
          OperationResponse.Scalar("command-job-response"),
          CommandExecutionMode.JobAsync,
          Some(component.name),
          executed = executed
        )

        When("the command executes")
        val context = ExecutionContext.test()
        val result = component.logic.executeAction(action, context)

        Then("the asynchronous interface returns a submitted persistent JobId without executing the ActionCall")
        val value = result match {
          case Consequence.Success(OperationResponse.Scalar(raw)) => raw.toString
          case other => fail(s"expected JobId response, got $other")
        }
        val jobid = JobId.parse(value).toOption.getOrElse(fail("command response was not a parseable JobId"))
        val job = component.jobEngine.query(jobid).getOrElse(fail("submitted Job read model was not found"))
        executed.get() shouldBe false
        _jobs(component) should have size 1
        job.status shouldBe JobStatus.Submitted
        job.result shouldBe None
        job.persistence shouldBe JobPersistencePolicy.Persistent
        ExecutionContext.currentExecutionResponse(context).map(_.responseKind) shouldBe Some(
          RuntimeContext.ExecutionResponseKind.AcceptedJob
        )
        ExecutionContext.currentExecutionResponse(context).map(_.admittedMode) shouldBe Some("JobAsync")
      }

      "retain the selected admission label across framework, definition, and action precedence" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; CommandActions with competing execution authorities")
        val component = _routed_component(
          "command-admission-precedence",
          "command-admission",
          "COMMAND",
          commandpolicy = Some(CommandExecutionPolicy())
        )
        val action = _command_action("command-admission", OperationResponse.Scalar("ok"), CommandExecutionMode.SyncDirectNoJob, Some(component.name))

        When("framework legacy, typed definition, and action fallback authority are selected")
        val framework = ExecutionContext.withFrameworkCommandExecutionMode(ExecutionContext.test(), CommandExecutionMode.SyncDirectNoJob)
        component.logic.executeAction(action, framework) shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        val frameworkmetadata = ExecutionContext.currentExecutionResponse(framework).getOrElse(fail("framework metadata missing"))
        val definition = ExecutionContext.test()
        component.logic.executeAction(action, definition) shouldBe Consequence.success(OperationResponse.Scalar("ok"))
        val definitionmetadata = ExecutionContext.currentExecutionResponse(definition).getOrElse(fail("definition metadata missing"))

        Then("the raw admitted authority and normalized effective policy remain distinct")
        frameworkmetadata.admittedMode shouldBe "SyncDirectNoJob"
        frameworkmetadata.effectiveMode shouldBe CommandExecutionMode.Sync
        definitionmetadata.admittedMode shouldBe "Sync"
        definitionmetadata.effectiveMode shouldBe CommandExecutionMode.Sync
      }
    }

    "E9 execution response transport metadata" must _e9 {
      "retain raw action fallback admission when no definition policy exists" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a CommandAction whose definition has no typed or legacy execution policy")
        val component = _routed_component("command-action-fallback", "command-action-fallback", "COMMAND")
        val action = _command_action(
          "command-action-fallback",
          OperationResponse.Scalar("action-fallback-response"),
          CommandExecutionMode.SyncDirectNoJob,
          Some(component.name)
        )
        val context = ExecutionContext.test()

        When("the command selects its concrete action legacy mode")
        val result = component.logic.executeAction(action, context)

        Then("the admitted label retains the raw action mode while effective policy is normalized")
        result shouldBe Consequence.success(OperationResponse.Scalar("action-fallback-response"))
        ExecutionContext.currentExecutionResponse(context).map(_.admittedMode) shouldBe Some("SyncDirectNoJob")
        ExecutionContext.currentExecutionResponse(context).map(_.effectiveMode) shouldBe Some(CommandExecutionMode.Sync)
      }

      "retain the default raw admission for a generic Action classified as COMMAND" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a generic Action classified as COMMAND without framework or definition policy")
        val component = _routed_component("generic-command-default", "generic-command-default", "COMMAND")
        val action = _plain_action(
          "generic-command-default",
          OperationResponse.Scalar("generic-command-response"),
          componentname = Some(component.name)
        )
        val context = ExecutionContext.test()

        When("the classified generic command selects its default execution policy")
        val result = component.logic.executeAction(action, context)

        Then("the admitted legacy default remains distinct from the effective synchronous mode")
        result shouldBe Consequence.success(OperationResponse.Scalar("generic-command-response"))
        ExecutionContext.currentExecutionResponse(context).map(_.admittedMode) shouldBe Some("SyncDirectNoJob")
        ExecutionContext.currentExecutionResponse(context).map(_.effectiveMode) shouldBe Some(CommandExecutionMode.Sync)
      }

      "restore outer direct metadata after nested managed, failing, and throwing child actions" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; an outer Action that invokes managed, failing, and throwing child Actions through its Action scope")
        val manualengine = InMemoryJobEngine.create(
          InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false)
        )
        val component = _routed_component(
          "nested-response-metadata",
          "outer-direct",
          "OTHER",
          jobengine = Some(manualengine),
          additionaldefinitions = Vector(
            CmlOperationDefinition(
              name = "child-accepted",
              kind = "COMMAND",
              commandExecutionPolicy = Some(CommandExecutionPolicy(
                interfaceMode = org.goldenport.cncf.action.CommandInterfaceMode.Async,
                jobRunMode = org.goldenport.cncf.action.CommandJobRunMode.Async,
                managedByJob = true
              )),
              inputType = "ChildInput",
              outputType = "ChildOutput",
              inputValueKind = "COMMAND_VALUE"
            ),
            CmlOperationDefinition(
              name = "child-failure",
              kind = "COMMAND",
              inputType = "ChildInput",
              outputType = "ChildOutput",
              inputValueKind = "COMMAND_VALUE"
            ),
            CmlOperationDefinition(
              name = "child-throw",
              kind = "COMMAND",
              inputType = "ChildInput",
              outputType = "ChildOutput",
              inputValueKind = "COMMAND_VALUE"
            )
          )
        )
        val accepted = _command_action(
          "child-accepted",
          OperationResponse.Scalar("not-run"),
          CommandExecutionMode.JobAsync,
          Some(component.name)
        )
        val failure = _command_failure_action("child-failure", Some(component.name))
        val throwing = _command_failure_action("child-throw", Some(component.name), throwfailure = true)
        val thrown = new AtomicReference[Option[Throwable]](None)
        val outer = _nested_outer_action(component, accepted, failure, throwing, thrown)
        val context = ExecutionContext.prepareOperationEvaluation(
          ExecutionContext.test(),
          OperationEvaluationOperationIdentity.fromResolvedRoute(
            component.name,
            "entity",
            "outer-direct"
          )
        ).getOrElse(fail("outer operation evaluation context was not prepared"))
        context.runtime.updateExecutionMetadata(_.copy(
          operationEvaluation = Some(OperationEvaluationExecutionReport.empty),
          traceId = Some("outer-trace"),
          executionId = Some("outer-execution")
        ))

        When("the outer direct Action invokes accepted, failing, and throwing children")
        val result = component.logic.executeAction(outer, context)

        Then("the outer response fields are restored while evaluation and diagnostics remain accumulated")
        result shouldBe Consequence.success(OperationResponse.Scalar("outer-response"))
        thrown.get() should not be empty
        _jobs(component) should have size 1
        _jobs(component).head.status shouldBe JobStatus.Submitted
        ExecutionContext.currentExecutionResponse(context).map(_.responseKind) shouldBe Some(
          RuntimeContext.ExecutionResponseKind.Direct
        )
        context.runtime.executionMetadata.responseJobId shouldBe None
        context.runtime.executionMetadata.debugJobId shouldBe None
        context.runtime.executionMetadata.operationEvaluation shouldBe Some(OperationEvaluationExecutionReport.empty)
        context.runtime.executionMetadata.traceId should not be empty
        context.runtime.executionMetadata.traceId should not be Some("outer-trace")
        context.runtime.executionMetadata.executionId should not be empty
        context.runtime.executionMetadata.executionId should not be Some("outer-execution")
        context.runtime.executionMetadata.failure shouldBe None
      }

      "accumulate nested Action metadata through a global ancestor while preserving selected invocation binding" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R9,R11,R13; Example: E9; a self-bound caller below a GlobalRuntimeContext and nested ordinary component Actions")
        val component = _component("global-nested-metadata")
        val base = ExecutionContext.test()
        val global = GlobalRuntimeContext.create(
          "global-nested-metadata",
          RuntimeConfig.default,
          ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
          base.observability,
          AliasResolver.empty
        )
        val runtime = new RuntimeContext(
          core = RuntimeContext.core(
            name = "global-nested-metadata", parent = Some(global), observabilitycontext = base.observability
          ),
          unitofworksupplier = () => base.unitOfWork,
          unitofworkinterpreterfn = base.runtime.unitOfWorkInterpreter,
          commitaction = _ => (),
          abortaction = _ => (),
          disposeaction = _ => (),
          token = "global-nested-metadata"
        )
        val source = ExecutionContext.create(runtime).asInstanceOf[ExecutionContext.Instance]
        val context = ExecutionContext.withFreshUnitOfWorkContext(source.copy(
          cncfCore = source.cncfCore.copy(executionControl = source.executionControl.copy(
            profile = source.executionControl.profile.copy(name = "source-profile-must-not-win")
          ))
        )).getOrElse(fail("global caller UnitOfWork was not bound"))
        val parentruntime = context.runtime
        val parentuow = context.unitOfWork
        val parentcontrol = context.executionControl
        val childidentity = OperationEvaluationOperationIdentity.fromResolvedRoute(
          component.name, "entity", "global-child"
        )
        val childadmission = OperationEvaluationAdmissionDiagnostic.admitted(childidentity)
        val childreport = OperationEvaluationExecutionReport.empty.appendAdmission(childadmission)
        context.runtime.updateExecutionMetadata(_.copy(
          traceId = Some("global-parent-trace"),
          executionId = Some("global-parent-execution"),
          operationEvaluation = Some(OperationEvaluationExecutionReport.empty)
        ))
        val outerobserved = new AtomicReference[Option[ExecutionContext]](None)
        val childobserved = new AtomicReference[Option[ExecutionContext]](None)
        val child = _metadata_action(component, "global-child", OperationResponse.Scalar("child-response"), childcontext => {
          childobserved.set(Some(childcontext))
          childcontext.runtime.noteExecutionDiagnostics(Some("global-child-trace"), Some("global-child-execution"), None)
          childcontext.runtime.updateExecutionMetadata(_.copy(operationEvaluation = Some(childreport)))
        })
        val outer = _metadata_action(component, "global-outer", OperationResponse.Scalar("outer-response"), outercontext => {
          outerobserved.set(Some(outercontext))
          When("the outer Action invokes the child through ordinary ComponentLogic with its global runtime lineage")
          val childresult = component.logic.executeAction(child, outercontext)
          Then("the outer Action sees the child's observable admission and diagnostics after the child returns")
          childresult shouldBe Consequence.success(OperationResponse.Scalar("child-response"))
          outercontext.runtime.executionMetadata.operationEvaluation shouldBe Some(childreport)
          outercontext.runtime.executionMetadata.traceId should not be Some("global-parent-trace")
          outercontext.runtime.executionMetadata.executionId should not be Some("global-parent-execution")
        })

        When("the original caller executes the outer Action through ordinary ComponentLogic")
        val result = component.logic.executeAction(outer, context)

        Then("the original caller retains child metadata and its UoW while each Action keeps its selected invocation and self-bound UoW")
        result shouldBe Consequence.success(OperationResponse.Scalar("outer-response"))
        context.runtime.executionMetadata.operationEvaluation shouldBe Some(childreport)
        context.runtime.executionMetadata.operationEvaluation.toVector.flatMap(_.admissions) should contain(childadmission)
        context.runtime.executionMetadata.traceId should not be empty
        context.runtime.executionMetadata.traceId should not be Some("global-parent-trace")
        context.runtime.executionMetadata.executionId should not be empty
        context.runtime.executionMetadata.executionId should not be Some("global-parent-execution")
        context.runtime should be theSameInstanceAs parentruntime
        context.unitOfWork should be theSameInstanceAs parentuow
        parentuow.executionContext should be theSameInstanceAs context
        context.executionControl shouldBe parentcontrol
        val outercontext = outerobserved.get().getOrElse(fail("outer Action context was not captured"))
        val childcontext = childobserved.get().getOrElse(fail("child Action context was not captured"))
        outercontext.unitOfWork should not be theSameInstanceAs(parentuow)
        childcontext.unitOfWork should not be theSameInstanceAs(parentuow)
        childcontext.unitOfWork should not be theSameInstanceAs(outercontext.unitOfWork)
        Vector(outercontext, childcontext).foreach { bound =>
          bound.unitOfWork.executionContext should be theSameInstanceAs bound
          bound.unitOfWork.executionContext.core should be theSameInstanceAs bound.core
          bound.unitOfWork.executionContext.idGeneration should be theSameInstanceAs bound.idGeneration
          bound.unitOfWork.executionContext.executionControl shouldBe bound.executionControl
          bound.executionControl.profile shouldBe global.executionProfileRuntime.baseBinding.control.profile
          bound.executionControl.profile should not be parentcontrol.profile
        }
        outercontext.executionControl.invocation.map(_.operationSelector) shouldBe Some(
          ExecutionInvocationIdentity.operationSelector(Some(component.name), Some("entity"), "global-outer")
        )
        childcontext.executionControl.invocation.map(_.operationSelector) shouldBe Some(
          ExecutionInvocationIdentity.operationSelector(Some(component.name), Some("entity"), "global-child")
        )
        childcontext.executionControl.invocation.get.ordinal should be > outercontext.executionControl.invocation.get.ordinal
      }

      "isolate an Ephemeral no-global Action Job's worker metadata and response throughout completion" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R6,R9,R11,R13; Example: E9; a no-global caller with seeded metadata and a manually drained Ephemeral component Action Job without calltree tracing")
        val manualengine = InMemoryJobEngine.create(
          InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false)
        )
        val component = _routed_component(
          "ephemeral-metadata-isolation", "ephemeral-worker", "OTHER", jobengine = Some(manualengine)
        )
        val parentcontext = ExecutionContext.test()
        val parentuow = parentcontext.unitOfWork
        val parentadmission = OperationEvaluationAdmissionDiagnostic.admitted(
          OperationEvaluationOperationIdentity.fromResolvedRoute(component.name, "entity", "parent")
        )
        val workeradmission = OperationEvaluationAdmissionDiagnostic.admitted(
          OperationEvaluationOperationIdentity.fromResolvedRoute(component.name, "entity", "ephemeral-worker")
        )
        val parentreport = OperationEvaluationExecutionReport.empty.appendAdmission(parentadmission)
        val workerreport = parentreport.appendAdmission(workeradmission)
        parentcontext.runtime.updateExecutionMetadata(_.copy(
          traceId = Some("ephemeral-parent-trace"),
          executionId = Some("ephemeral-parent-execution"),
          operationEvaluation = Some(parentreport)
        ))
        val workerstarted = new CountDownLatch(1)
        val workerrelease = new CountDownLatch(1)
        val workerobserved = new AtomicReference[Option[ExecutionContext]](None)
        val workerfailure = new AtomicReference[Option[Throwable]](None)
        val drained = new AtomicBoolean(false)
        val response = OperationResponse.Scalar("ephemeral-worker-response")
        val action = _metadata_action(component, "ephemeral-worker", response, workercontext => {
          try {
            workerobserved.set(Some(workercontext))
            workercontext.runtime.noteExecutionDiagnostics(Some("ephemeral-worker-trace"), Some("ephemeral-worker-execution"), None)
            workercontext.runtime.updateExecutionMetadata(metadata => metadata.copy(
              operationEvaluation = Some(metadata.operationEvaluation.getOrElse(OperationEvaluationExecutionReport.empty)
                .appendAdmission(workeradmission))
            ))
            ExecutionContext.noteExecutionResponse(workercontext, RuntimeContext.ExecutionResponseMetadata.direct)
            workerstarted.countDown()
            workerrelease.await(5, TimeUnit.SECONDS) shouldBe true
          } catch {
            case e: Throwable =>
              workerfailure.set(Some(e))
              throw e
          }
        })
        val task = ActionTask(ActionId.generate(), action, component.actionEngine, component = Some(component))

        When("the public JobEngine submission explicitly selects Ephemeral persistence")
        val jobid = manualengine.submit(
          List(task), parentcontext, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)
        ).getOrElse(fail("Ephemeral Action Job submission failed"))
        Given("the admitted caller records its AcceptedJob response before the worker is allowed to run")
        ExecutionContext.noteExecutionResponse(parentcontext, RuntimeContext.ExecutionResponseMetadata.direct.copy(
          responseKind = RuntimeContext.ExecutionResponseKind.AcceptedJob
        ))
        ExecutionContext.noteResponseJobId(parentcontext, jobid.value)
        val parentmetadata = parentcontext.runtime.executionMetadata
        val parentresponse = ExecutionContext.currentExecutionResponseState(parentcontext)

        Then("the submitted Job is Ephemeral and its Action has not run")
        manualengine.query(jobid).map(_.persistence) shouldBe Some(JobPersistencePolicy.Ephemeral)
        manualengine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
        workerobserved.get() shouldBe None
        parentcontext.framework.callTreeEnabled shouldBe false
        parentcontext.observability.callTreeContext.isEnabled shouldBe false

        When("the actual asynchronous dequeue runs the component ActionCall and blocks after worker-local writes")
        val worker = new Thread(() => {
          try {
            drained.set(manualengine.drainOne())
          } catch {
            case e: Throwable => workerfailure.set(Some(e))
          }
        })
        worker.start()
        try {
          Then("the blocked worker owns its metadata, response and self-bound UoW while the submitter remains unchanged")
          workerstarted.await(5, TimeUnit.SECONDS) shouldBe true
          workerfailure.get().foreach(e => fail("Ephemeral worker failed", e))
          val workercontext = workerobserved.get().getOrElse(fail("Ephemeral Action context was not captured"))
          workercontext.observability.callTreeContext.isEnabled shouldBe false
          workercontext.unitOfWork.executionContext should be theSameInstanceAs workercontext
          workercontext.unitOfWork should not be theSameInstanceAs(parentuow)
          workercontext.runtime.executionMetadata.operationEvaluation shouldBe Some(workerreport)
          workercontext.runtime.executionMetadata.traceId shouldBe Some("ephemeral-worker-trace")
          workercontext.runtime.executionMetadata.executionId shouldBe Some("ephemeral-worker-execution")
          ExecutionContext.currentExecutionResponse(workercontext) shouldBe Some(RuntimeContext.ExecutionResponseMetadata.direct)
          parentcontext.runtime.executionMetadata shouldBe parentmetadata
          ExecutionContext.currentExecutionResponseState(parentcontext) shouldBe parentresponse
        } finally {
          When("the worker is released and joined even if a blocked-state expectation fails")
          workerrelease.countDown()
          worker.join(5000L)
        }

        Then("the actual Action task and Job succeed, the worker retains its writes, and parent metadata and response remain unchanged")
        worker.isAlive shouldBe false
        workerfailure.get().foreach(e => fail("Ephemeral worker failed", e))
        drained.get() shouldBe true
        manualengine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        manualengine.getResult(jobid) shouldBe Some(JobResult.Success(response))
        val model = manualengine.query(jobid).getOrElse(fail("Ephemeral Job read model is missing"))
        model.persistence shouldBe JobPersistencePolicy.Ephemeral
        model.tasks.tasks.map(_.status) shouldBe Vector(JobTaskStatus.Succeeded)
        model.tasks.tasks.head.result.success shouldBe true
        val workercontext = workerobserved.get().getOrElse(fail("completed Action context was not captured"))
        workercontext.runtime.executionMetadata.operationEvaluation shouldBe Some(workerreport)
        workercontext.runtime.executionMetadata.traceId should not be empty
        workercontext.runtime.executionMetadata.traceId should not be parentmetadata.traceId
        workercontext.runtime.executionMetadata.executionId should not be empty
        workercontext.runtime.executionMetadata.executionId should not be parentmetadata.executionId
        ExecutionContext.currentExecutionResponse(workercontext) shouldBe Some(RuntimeContext.ExecutionResponseMetadata.direct)
        parentcontext.runtime.executionMetadata shouldBe parentmetadata
        ExecutionContext.currentExecutionResponseState(parentcontext) shouldBe parentresponse
        parentcontext.unitOfWork should be theSameInstanceAs parentuow
      }

      "isolate accepted parent metadata from a controlled asynchronous Job worker" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; an AcceptedJob parent and a worker that records a separate direct response while held at a barrier")
        val manualengine = InMemoryJobEngine.create(
          InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false)
        )
        val component = _routed_component(
          "async-response-cell-isolation",
          "async-response-cell-isolation",
          "COMMAND",
          jobengine = Some(manualengine)
        )
        val workerstarted = new CountDownLatch(1)
        val workerrelease = new CountDownLatch(1)
        val action = _command_action(
          "async-response-cell-isolation",
          OperationResponse.Scalar("worker-response"),
          CommandExecutionMode.JobAsync,
          Some(component.name),
          duringexecute = { workercontext =>
            ExecutionContext.noteExecutionResponse(
              workercontext,
              RuntimeContext.ExecutionResponseMetadata.direct
            )
            workerstarted.countDown()
            workerrelease.await(5, TimeUnit.SECONDS) shouldBe true
          }
        )
        val parentcontext = ExecutionContext.test()

        When("the parent returns AcceptedJob and the manually drained worker enters its response activity")
        val result = component.logic.executeAction(action, parentcontext)
        val worker = new Thread(() => {
          val _ = manualengine.drainOne()
          ()
        })
        worker.start()

        Then("the parent AcceptedJob response state remains intact until the worker is released")
        try {
          workerstarted.await(5, TimeUnit.SECONDS) shouldBe true
          val acceptedjobid = result.toOption.collect {
            case OperationResponse.Scalar(value) => value.toString
          }.getOrElse(fail("AcceptedJob response id is missing"))
          ExecutionContext.currentExecutionResponse(parentcontext).map(_.responseKind) shouldBe Some(
            RuntimeContext.ExecutionResponseKind.AcceptedJob
          )
          ExecutionContext.currentExecutionResponseState(parentcontext).responseJobId shouldBe Some(acceptedjobid)
          result shouldBe a[Consequence.Success[?]]
        } finally {
          workerrelease.countDown()
          worker.join(5000L)
        }
        worker.isAlive shouldBe false
      }
    }

  }

  private def _component(name: String): Component = {
    val subsystem = TestComponentFactory.emptySubsystem(s"$name-subsystem")
    _test_subsystems += subsystem
    TestComponentFactory.create(name, Protocol.empty, subsystem = subsystem)
  }

  private def _routed_component(
    name: String,
    operationname: String,
    kind: String,
    jobengine: Option[JobEngine] = None,
    commandpolicy: Option[CommandExecutionPolicy] = None,
    additionaldefinitions: Vector[CmlOperationDefinition] = Vector.empty
  ): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "entity",
            operations = spec.OperationDefinitionGroup(
              NonEmptyVector.one(PlainRouteOperation(operationname))
            )
          )
        )
      )
    )
    val componentid = TestComponentFactory.componentId(name)
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = operationname,
            kind = kind,
            commandExecutionPolicy = commandpolicy,
            inputType = "PlainInput",
            outputType = "PlainOutput",
            inputValueKind = if (kind == "COMMAND") "COMMAND_VALUE" else "QUERY_VALUE"
          )
        ) ++ additionaldefinitions
    }
    val subsystem = TestComponentFactory.emptySubsystem(s"$name-subsystem")
    _test_subsystems += subsystem
    val selectedjobengine = jobengine.getOrElse(subsystem.jobEngine)
    jobengine.foreach(engine => _test_job_engines += engine)
    val core = Component.Core.create(
      componentid.name,
      componentid,
      ComponentInstanceId.default(componentid),
      protocol,
      selectedjobengine
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _self_bound_action_context(
    base: ExecutionContext.Instance,
    caller: Component,
    root: ScopeContext
  ): ExecutionContext.Instance = {
    lazy val active: ExecutionContext.Instance = ExecutionContext.Instance(
      base.core,
      base.cncfCore.copy(
        scope = caller.scopeContext.createChildScope(ScopeKind.Action, "caller-action"),
        runtime = runtime,
        executionControl = base.executionControl.copy(
          profile = base.executionControl.profile.copy(name = "action-owner-spec")
        )
      )
    )
    lazy val unitofwork = new UnitOfWork(active)
    lazy val interpreter = new (cats.~>[UnitOfWorkOp, Consequence]) {
      def apply[A](op: UnitOfWorkOp[A]): Consequence[A] =
        new UnitOfWorkInterpreter(unitofwork).interpret(op)
    }
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = root.core,
      unitofworksupplier = () => unitofwork,
      unitofworkinterpreterfn = interpreter,
      commitaction = uow => { val _ = uow.commit(); () },
      abortaction = uow => { val _ = uow.abort(); () },
      disposeaction = uow => { val _ = uow.dispose(); () },
      token = base.runtime.toToken,
      context = base.runtime.context,
      operationMode = base.runtime.operationMode,
      transitionValidationHook = base.runtime.transitionValidationHook,
      entityCreateDefaultsPolicy = base.runtime.entityCreateDefaultsPolicy
    )
    active
  }

  private def _http_owner_action(
    target: Component,
    observed: AtomicReference[Option[ExecutionContext]]
  ): Action = new Action {
    val request: Request = Request.of(
      component = target.name, service = "entity", operation = "target-http-owner"
    )

    override def createCall(core: ActionCall.Core): ActionCall = {
      val captured = core
      new FunctionalActionCall {
        override val core: ActionCall.Core = captured

        protected def build_Program: ExecUowM[OperationResponse] = {
          observed.set(Some(executionContext))
          http_post("/selected", Some("child-body"), Map("X-Action" -> "target")).map { response =>
            OperationResponse.Scalar(response.getString.getOrElse("missing HTTP response"))
          }
        }
      }
    }
  }

  private final class ActionOwnerHttpDriver extends HttpDriver {
    private val _calls = new AtomicInteger
    def calls: Int = _calls.get()

    def get(path: String, headers: Map[String, String], properties: Vector[Property]): HttpResponse =
      fail("unexpected GET in target Action POST program")

    def post(
      path: String,
      body: Option[String],
      headers: Map[String, String],
      properties: Vector[Property]
    ): HttpResponse = {
      _calls.incrementAndGet()
      path shouldBe "http://127.0.0.1:8089/selected"
      body shouldBe Some("child-body")
      headers shouldBe Map("X-Action" -> "target")
      HttpResponse.Text(
        HttpStatus.Ok,
        ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
        Bag.text("target-uow-response", StandardCharsets.UTF_8)
      )
    }

    def put(
      path: String,
      body: Option[String],
      headers: Map[String, String],
      properties: Vector[Property]
    ): HttpResponse = fail("unexpected PUT in target Action POST program")
  }

  private def _metadata_action(
    component: Component,
    operationname: String,
    response: OperationResponse,
    duringexecute: ExecutionContext => Unit
  ): Action = new Action {
    val request: Request = Request.of(component = component.name, service = "entity", operation = operationname)

    override def createCall(core: ActionCall.Core): ActionCall = {
      val captured = core
      new ActionCall {
        override val core: ActionCall.Core = captured

        override def execute(): Consequence[OperationResponse] = {
          duringexecute(executionContext)
          Consequence.success(response)
        }
      }
    }
  }

  private def _plain_action(
    operationname: String,
    response: OperationResponse,
    executed: AtomicBoolean = new AtomicBoolean(false),
    observed: AtomicReference[Option[ExecutionContext]] = new AtomicReference[Option[ExecutionContext]](None),
    componentname: Option[String] = None
  ): Action =
    new Action {
      val request = componentname.map(name => Request.of(
        component = name,
        service = "entity",
        operation = operationname
      )).getOrElse(Request.ofOperation(operationname))

      override def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          override val core: ActionCall.Core = captured

          override def execute(): Consequence[OperationResponse] = {
            observed.set(Some(executionContext))
            executed.set(true)
            Consequence.success(response)
          }
        }
      }
    }

  private def _plain_failure_action(
    operationname: String,
    componentname: Option[String] = None
  ): Action =
    new Action {
      val request = componentname.map(name => Request.of(
        component = name,
        service = "entity",
        operation = operationname
      )).getOrElse(Request.ofOperation(operationname))

      override def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          override val core: ActionCall.Core = captured

          override def execute(): Consequence[OperationResponse] =
            Consequence.operationInvalid("plain failure")
        }
      }
    }

  private def _query_action(
    operationname: String,
    response: OperationResponse,
    componentname: Option[String] = None
  ): QueryAction =
    new QueryAction() {
      val request = componentname.map(name => Request.of(
        component = name,
        service = "entity",
        operation = operationname
      )).getOrElse(Request.ofOperation(operationname))

      override def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          override val core: ActionCall.Core = captured

          override def execute(): Consequence[OperationResponse] =
            Consequence.success(response)
        }
      }
    }

  private def _command_action(
    operationname: String,
    response: OperationResponse,
    mode: CommandExecutionMode,
    componentname: Option[String] = None,
    executed: AtomicBoolean = new AtomicBoolean(false),
    duringexecute: ExecutionContext => Unit = _ => ()
  ): CommandAction =
    new CommandAction() {
      val request = componentname.map(name => Request.of(
        component = name,
        service = "entity",
        operation = operationname
      )).getOrElse(Request.ofOperation(operationname))

      override def commandExecutionMode: CommandExecutionMode = mode

      override def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          override val core: ActionCall.Core = captured

          override def execute(): Consequence[OperationResponse] = {
            duringexecute(executionContext)
            executed.set(true)
            Consequence.success(response)
          }
        }
      }
    }

  private def _command_failure_action(
    operationname: String,
    componentname: Option[String],
    throwfailure: Boolean = false
  ): CommandAction =
    new CommandAction() {
      val request = componentname.map(name => Request.of(
        component = name,
        service = "entity",
        operation = operationname
      )).getOrElse(Request.ofOperation(operationname))

      override def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          override val core: ActionCall.Core = captured
          override def execute(): Consequence[OperationResponse] = {
            val diagnostic = if (throwfailure) "nested child throw" else "nested child failure"
            executionContext.runtime.noteExecutionDiagnostics(
              Some(s"$diagnostic-trace"),
              Some(s"$diagnostic-execution"),
              Some(diagnostic)
            )
            if (throwfailure)
              throw new scala.util.control.ControlThrowable {}
            else
              Consequence.operationInvalid(diagnostic)
          }
        }
      }
    }

  private def _nested_outer_action(
    targetcomponent: Component,
    accepted: CommandAction,
    failure: CommandAction,
    throwing: CommandAction,
    thrown: AtomicReference[Option[Throwable]]
  ): Action =
    new Action {
      val request: Request = Request.of(
        component = targetcomponent.name,
        service = "entity",
        operation = "outer-direct"
      )

      override def createCall(core: ActionCall.Core): ActionCall = {
        val captured = core
        new ActionCall {
          override val core: ActionCall.Core = captured
          override def execute(): Consequence[OperationResponse] = {
            targetcomponent.logic.executeAction(accepted, executionContext) shouldBe a[Consequence.Success[?]]
            targetcomponent.logic.executeAction(failure, executionContext) shouldBe a[Consequence.Failure[?]]
            try {
              targetcomponent.logic.executeAction(throwing, executionContext)
            } catch {
              case e: scala.util.control.ControlThrowable => thrown.set(Some(e))
            }
            Consequence.success(OperationResponse.Scalar("outer-response"))
          }
        }
      }
    }

  private def _jobs(component: Component) =
    component.jobEngine.listJobs(limit = 100, persistentOnly = false)
}

private final case class PlainRouteOperation(
  operationname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = operationname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(PlainRouteAction(req))
}

private final case class PlainRouteAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall = {
    val captured = core
    new ActionCall {
      override val core: ActionCall.Core = captured

      override def execute(): Consequence[OperationResponse] =
        Consequence.success(OperationResponse.Scalar("protocol-route-action"))
    }
  }
}
