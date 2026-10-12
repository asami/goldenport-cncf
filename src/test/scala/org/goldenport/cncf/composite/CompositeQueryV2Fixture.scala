package org.goldenport.cncf.composite

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.{ResolvedParameter, RuntimeConfig}
import org.goldenport.datatype.ContentType
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeKind}
import org.goldenport.cncf.config.RuntimeOperationSecurityPolicy
import org.goldenport.cncf.job.{InMemoryJobEngine, JobControlPolicy, JobControlRequest, JobControlResponse, JobEngine, JobId, JobResult, JobStatus, JobSubmitOption, JobTask, JobTaskPage, JobTimelinePage}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.security.{OperationAuthorizationProvider, OperationAuthorizationRule}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, TestComponentFactory}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.goldenport.schema.XString

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryV2Fixture(
  workerCount: Int = 4,
  suppliedJobEngine: InMemoryJobEngine = null,
  marker: String = "fixture",
  ownResourceScope: Boolean = false
) {
  val state: CompositeQueryV2Fixture.State = new CompositeQueryV2Fixture.State
  val jobEngine: InMemoryJobEngine = Option(suppliedJobEngine).getOrElse(
    InMemoryJobEngine.create(InMemoryJobEngine.SchedulerConfig(workerCount = workerCount))
  )
  val subsystem: Subsystem = _create_subsystem()
  private val _component = TestComponentFactory.create(
    "CompositeQueryV2",
    Protocol(services = spec.ServiceDefinitionGroup(Vector(new CompositeQueryV2Fixture.FixtureService(state)))),
    subsystem = subsystem
  )
  val componentName: String = _component.componentId.name
  subsystem.add(_component)

  private def _create_subsystem(): Subsystem = {
    val name = s"composite-query-v2-$marker"
    val configuration = ResolvedConfiguration(
      Configuration(Map("composite.fixture.marker" -> ConfigurationValue.StringValue(marker))),
      ConfigurationTrace.empty
    )
    if (ownResourceScope) {
      val global = GlobalRuntimeContext.create(
        name,
        RuntimeConfig.from(configuration),
        configuration,
        ExecutionContext.test().observability,
        AliasResolver.empty
      )
      val target = Subsystem(
        name = name,
        scopeContext = Some(global.createChildScope(ScopeKind.Subsystem, name)),
        configuration = configuration,
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command
      ).enableControlledTestExecution()
      RuntimeBindingAdmissionFixture.admit(target)
    } else {
      TestComponentFactory.admittedSubsystemWithConfig(
        configuration.configuration.values,
        name = name
      )
    }
  }

  def close(): Unit = {
    try jobEngine.shutdown()
    finally subsystem.shutdown()
  }

  def request(operation: String = "echo"): Request =
    Request.of(componentName, "sample", operation)

  def branch(
    branchId: String,
    required: Boolean = true,
    dependencies: Vector[CompositeQueryV2Dependency] = Vector.empty,
    fallback: Option[CompositeQueryV2Fallback] = None
  ): CompositeQueryV2Branch =
    CompositeQueryV2Branch(
      branchId,
      request().copy(properties = List(Property("fixture.branch", branchId, None))),
      required = required,
      dependencies = dependencies,
      fallback = fallback
    )

  def record(value: String): OperationResponse =
    OperationResponse.RecordResponse(Record.data("value" -> value))

  def awaitEntered(count: Long = 1L): Boolean = state.awaitEntered(count)

  def release(): Unit = state.release.countDown()
  def releaseFirst(): Unit = state.firstRelease.countDown()
  def releaseSecond(): Unit = state.secondRelease.countDown()
}

object CompositeQueryV2Fixture {
  final class State {
    val entered: CountDownLatch = new CountDownLatch(1)
    val release: CountDownLatch = new CountDownLatch(1)
    val firstRelease: CountDownLatch = new CountDownLatch(1)
    val secondRelease: CountDownLatch = new CountDownLatch(1)
    val active: AtomicInteger = new AtomicInteger(0)
    val maxActive: AtomicInteger = new AtomicInteger(0)
    val enteredCount: AtomicInteger = new AtomicInteger(0)
    val exitedCount: AtomicInteger = new AtomicInteger(0)
    val queryCalls: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val commandCalls: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
    val observations: ConcurrentLinkedQueue[Observation] = new ConcurrentLinkedQueue[Observation]()
    val cancelledObserved: AtomicBoolean = new AtomicBoolean(false)
    private val _entry_monitor = new Object

    def awaitEntered(count: Long): Boolean = _entry_monitor.synchronized {
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500L)
      while (enteredCount.get() < count && System.nanoTime() < deadline) {
        _entry_monitor.wait(math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())))
      }
      enteredCount.get() >= count
    }

    def awaitExited(count: Long): Boolean = _entry_monitor.synchronized {
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500L)
      while (exitedCount.get() < count && System.nanoTime() < deadline) {
        _entry_monitor.wait(math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())))
      }
      exitedCount.get() >= count
    }

    def execute(context: ExecutionContext, request: Request): Consequence[OperationResponse] = {
      val operation = request.operation
      val branch = request.properties.find(_.name == "fixture.branch").map(_.value.toString).getOrElse(operation)
      queryCalls.add(branch)
      ExecutionContext.noteExecutionResponse(
        context,
        RuntimeContext.ExecutionResponseMetadata(admittedMode = s"fixture-$branch")
      )
      observations.add(Observation(
        branch,
        operation,
        context.runtime.asInstanceOf[AnyRef],
        context.unitOfWork.asInstanceOf[AnyRef],
        context.asInstanceOf[ExecutionContext.Instance].cncfCore.resources.asInstanceOf[AnyRef],
        context.asInstanceOf[ExecutionContext.Instance].cncfCore.resourceTrees.asInstanceOf[AnyRef],
        context.resolvedParameters.asInstanceOf[AnyRef],
        context.resolvedParameters.get("composite.fixture.marker").map(x => ResolvedParameter.format_value(x.value)).getOrElse(""),
        context.operationEvaluation,
        context.asInstanceOf[ExecutionContext.Instance].executionResponseCell.asInstanceOf[AnyRef],
        ExecutionContext.currentExecutionResponseState(context),
        context.security.principal.id.value,
        context.security.session.flatMap(_.sessionId).getOrElse(""),
        context.security.session.flatMap(_.tokenId).getOrElse(""),
        context.security.principal.attributes.getOrElse("tenant", ""),
        context.observability.traceId.print,
        context.observability.correlationId.map(_.print).getOrElse("")
      ))
      val count = enteredCount.incrementAndGet()
      _entry_monitor.synchronized(_entry_monitor.notifyAll())
      entered.countDown()
      val current = active.incrementAndGet()
      maxActive.updateAndGet(x => math.max(x, current))
      try {
        operation match {
          case "failure" => Consequence.operationInvalid("fixture-query-failure")
          case "route" => Consequence.operationNotFound("fixture-route-missing")
          case "configuration" => Consequence.configurationInvalid("fixture-configuration-invalid")
          case "protocol" => Consequence.operationInvalid("fixture-protocol-invalid")
          case "unavailable" => Consequence.serviceUnavailable("fixture-service-unavailable")
          case "throw" => throw new IllegalStateException(_secret(request))
          case "rawfailure" => Consequence.operationInvalid(_secret(request))
          case "payload" => Consequence.success(OperationResponse.Scalar(_secret(request)))
          // Deliberately malformed producer witness; no external ScalarValue evidence is defined.
          case "hostileScalar" => Consequence.success(OperationResponse.Scalar(new Object {
            override def toString: String = throw new IllegalStateException("must-not-stringify")
          })(using null))
          case "opaque" => Consequence.success(OperationResponse.Opaque(new Object))
          case "http" => Consequence.success(OperationResponse.Http(HttpResponse.Text(HttpStatus.Ok, ContentType.TEXT_PLAIN, Bag.text("fixture-http"))))
          case "large" => Consequence.success(OperationResponse.Scalar("x" * 512))
          case "deny" => Consequence.operationIllegal("fixture-target-authorization", "denied")
          case "gate" | "gate2" | "slow" =>
            val _ = count
            release.await(1500L, TimeUnit.MILLISECONDS)
            cancelledObserved.set(context.jobContext.cancellationScope.exists(_.isCancelled))
            _response(context, branch)
          case "gateone" =>
            firstRelease.await(1500L, TimeUnit.MILLISECONDS)
            _response(context, branch)
          case "gatetwo" =>
            secondRelease.await(1500L, TimeUnit.MILLISECONDS)
            _response(context, branch)
          case "failgate" =>
            release.await(1500L, TimeUnit.MILLISECONDS)
            cancelledObserved.set(context.jobContext.cancellationScope.exists(_.isCancelled))
            Consequence.operationInvalid("fixture-late-failure")
          case _ => _response(context, branch)
        }
      } finally {
        active.decrementAndGet()
        exitedCount.incrementAndGet()
        _entry_monitor.synchronized(_entry_monitor.notifyAll())
      }
    }

    def command(context: ExecutionContext): Consequence[OperationResponse] = {
      commandCalls.add(context.scope.name)
      Consequence.success(OperationResponse.void)
    }

    private def _response(context: ExecutionContext, branch: String): Consequence[OperationResponse] = {
      val subject = SecuritySubject.current(using context)
      Consequence.success(OperationResponse.RecordResponse(Record.dataAuto(
        "operation" -> context.scope.name,
        "branch" -> branch,
        "subject" -> subject.subjectId,
        "trace" -> context.observability.traceId.print,
        "correlation" -> context.observability.correlationId.map(_.print).getOrElse("")
      )))
    }

    private def _secret(request: Request): String =
      request.properties.find(_.name == "password").map(_.value.toString).getOrElse("fixture-secret")
  }

  final case class Observation(
    branchId: String,
    operation: String,
    runtime: AnyRef,
    unitOfWork: AnyRef,
    resources: AnyRef,
    resourceTrees: AnyRef,
    resolvedParameters: AnyRef,
    configurationMarker: String,
    operationEvaluation: org.goldenport.cncf.operation.evaluation.OperationEvaluationContext,
    responseCell: AnyRef,
    responseState: ExecutionContext.ExecutionResponseState,
    principal: String,
    session: String,
    token: String,
    tenant: String,
    trace: String,
    correlation: String
  )

  private final class FixtureService(state: State) extends spec.ServiceDefinition {
    val specification: spec.ServiceDefinition.Specification =
      spec.ServiceDefinition.Specification(
        name = "sample",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
          FixtureQueryOperation("echo", state),
          FixtureQueryOperation("gate", state),
          FixtureQueryOperation("gate2", state),
          FixtureQueryOperation("slow", state),
          FixtureQueryOperation("gateone", state),
          FixtureQueryOperation("gatetwo", state),
          FixtureQueryOperation("failgate", state),
          FixtureQueryOperation("failure", state),
          FixtureQueryOperation("route", state),
          FixtureQueryOperation("configuration", state),
          FixtureQueryOperation("protocol", state),
          FixtureDeniedQueryOperation(state),
          FixtureQueryOperation("unavailable", state),
          FixtureQueryOperation("throw", state),
          FixtureQueryOperation("rawfailure", state),
          FixtureQueryOperation("payload", state),
          FixtureQueryOperation("hostileScalar", state),
          FixtureQueryOperation("opaque", state),
          FixtureQueryOperation("http", state),
          FixtureQueryOperation("large", state),
          FixtureCommandOperation(state)
        ))
      )
  }

  private final case class FixtureQueryOperation(operationName: String, state: State) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = operationName,
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition(result = List(XString))
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.success(FixtureQueryAction(request, state))
  }

  private final case class FixtureDeniedQueryOperation(state: State)
      extends spec.OperationDefinition with OperationAuthorizationProvider {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "deny",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition(result = List(XString))
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.success(FixtureQueryAction(request, state))

    def operationAuthorization(policy: RuntimeOperationSecurityPolicy): OperationAuthorizationRule = {
      val _ = policy
      OperationAuthorizationRule(deny = true)
    }
  }

  private final case class FixtureCommandOperation(state: State) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "command",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.success(FixtureCommandAction(request, state))
  }

  private final case class FixtureQueryAction(request: Request, state: State) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall = FixtureQueryCall(core, state)
  }

  private final case class FixtureQueryCall(core: ActionCall.Core, state: State) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      state.execute(core.executionContext, core.action.asInstanceOf[FixtureQueryAction].request)
  }

  private final case class FixtureCommandAction(request: Request, state: State) extends CommandAction {
    def createCall(core: ActionCall.Core): ActionCall = FixtureCommandCall(core, state)
  }

  private final case class FixtureCommandCall(core: ActionCall.Core, state: State) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = state.command(core.executionContext)
  }
}

/* The wrapper is deliberately an official JobEngine delegate: it only exposes
 * admission and canonical-settlement seams needed to prove composition behavior. */
final class CompositeQueryV2ControlledJobEngine(delegate: JobEngine) extends JobEngine {
  val admissions: AtomicInteger = new AtomicInteger(0)
  val rejectAdmissions: AtomicBoolean = new AtomicBoolean(false)
  val omitCanonical: AtomicBoolean = new AtomicBoolean(false)
  val omitCanonicalCallbacks: AtomicInteger = new AtomicInteger(0)
  val submitted: ConcurrentLinkedQueue[JobId] = new ConcurrentLinkedQueue[JobId]()
  val cancellationControls: CountDownLatch = new CountDownLatch(1)

  def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] =
    submit(tasks, ctx, JobSubmitOption())

  def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = {
    admissions.incrementAndGet()
    if (rejectAdmissions.get()) Consequence.serviceUnavailable("controlled-admission-refusal")
    else delegate.submit(tasks.map(_wrap), ctx, option).map { jobid =>
      submitted.add(jobid)
      jobid
    }
  }

  def getStatus(jobId: JobId): Option[JobStatus] = delegate.getStatus(jobId)
  def getResult(jobId: JobId): Option[JobResult] = delegate.getResult(jobId)
  def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = delegate.awaitResult(jobId, timeoutMillis)
  def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy)(using ExecutionContext): Consequence[JobControlResponse] = {
    cancellationControls.countDown()
    delegate.control(jobId, request, policy)
  }
  def query(jobId: JobId): Option[org.goldenport.cncf.job.JobQueryReadModel] = delegate.query(jobId)
  def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = delegate.queryTasks(jobId, offset, limit)
  def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = delegate.queryTimeline(jobId, offset, limit)
  override def shutdown(): Unit = delegate.shutdown()

  private def _wrap(task: JobTask): JobTask = new JobTask {
    val actionId = task.actionId
    override def taskKind: String = task.taskKind
    override def targetKind: Option[String] = task.targetKind
    override def componentName: Option[String] = task.componentName
    override def serviceName: Option[String] = task.serviceName
    override def operationName: Option[String] = task.operationName
    override def requestSummary: Option[String] = task.requestSummary
    override def requestParameters: Map[String, String] = task.requestParameters
    override def defaultPersistence = task.defaultPersistence
    def run(ctx: ExecutionContext) = task.run(ctx)
    override def observeCanonicalOutcome(outcome: org.goldenport.cncf.job.TaskOutcome, ctx: ExecutionContext, cancelled: Boolean): Unit =
      if (!omitCanonical.get() && !_omit_canonical_callback()) task.observeCanonicalOutcome(outcome, ctx, cancelled)
    override def observeAdmissionFailure(conclusion: org.goldenport.Conclusion, ctx: ExecutionContext): Unit =
      task.observeAdmissionFailure(conclusion, ctx)
  }

  private def _omit_canonical_callback(): Boolean = {
    var omitted = false
    var pending = true
    while (pending) {
      val count = omitCanonicalCallbacks.get()
      if (count <= 0) pending = false
      else if (omitCanonicalCallbacks.compareAndSet(count, count - 1)) {
        omitted = true
        pending = false
      }
    }
    omitted
  }
}
