package org.goldenport.cncf.context

import java.math.MathContext
import java.nio.charset.Charset
import java.time.{Clock, Instant, ZoneId}
import java.util.Locale
import org.goldenport.context.{EntropyContext, EnvironmentContext as CoreEnvironmentContext, ExecutionContext as CoreExecutionContext, I18nContext, RandomContext, VirtualMachineContext}
import org.goldenport.id.{UniversalId as CoreUniversalId}
import org.goldenport.log.Logger
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.action.{CommandExecutionMode, CommandExecutionPolicy}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.http.{FakeHttpDriver, HttpDriver}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.observability.{CallTreeContext, DslChokepointHook}
import org.goldenport.cncf.resource.ResourceAccess
import cats.~>

/**
 * Runtime-only execution carrier and single entry point for action/component execution.
 * It exists only during runtime and MUST NOT contain application, system, or configuration state.
 * The core class carries platform-level assumptions, while ExecutionContext.CncfCore transports
 * CNCF-specific runtime state such as SecurityContext, ObservabilityContext, RuntimeContext, and JobContext.
 *
 * RuntimeContext governs execution behavior (UnitOfWork lifecycle, interpreters, commit/abort/dispose),
 * resolves HttpDriver and other execution resources via ScopeContext, and never defines execution targets.
 * ExecutionContext does not own scopes directly; runtime resolution flows ActionCall → ExecutionContext →
 * RuntimeContext → ScopeContext → HttpDriver.
 *
 * Explicit non-responsibilities include ApplicationContext (removed), SystemContext (removed), configuration
 * snapshots, and any persistent or boot-time state. Reintroducing those would be a design regression.
 *
 * Helpers such as create()/test() exist only for specs and demos to inject fake or in-memory RuntimeContext.
 * Production execution paths must supply real RuntimeContext instances.
 */
/*
 * @since   Dec. 21, 2025
 *  version Dec. 31, 2025
 *  version Jan. 20, 2026
 *  version Feb. 25, 2026
 *  version Apr. 25, 2026
 *  version May. 31, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ExecutionContext
  extends CoreExecutionContext
  with ExecutionContext.CncfCore.Holder {

  /** Canonical core execution context value */
  def core: CoreExecutionContext.Core

  /** CNCF execution-context-specific value */
  def cncfCore: ExecutionContext.CncfCore

  def withScope(parent: ScopeContext): ExecutionContext

  def dataStoreSpace: DataStoreSpace = runtime.dataStoreSpace

  def entityStoreSpace: EntityStoreSpace = runtime.entityStoreSpace

  def entitySpace: EntitySpace = runtime.entitySpace

  override def resources: ResourceAccess = cncfCore.resources

  def isAggregateInternalRead: Boolean = cncfCore.scope.isAggregateInternalRead

  lazy val transactionContext = TransactionContext(runtime)
}

object ExecutionContext {
  /**
    * Runtime namespace for CNCF-only extensions.
    */
  final case class CncfCore(
    scope: ScopeContext,
    security: SecurityContext,
    observability: ObservabilityContext,
    runtime: RuntimeContext,
    jobContext: org.goldenport.cncf.job.JobContext,
    framework: FrameworkParameter = FrameworkParameter(),
    idGeneration: IdGenerationContext = IdGenerationContext.default(IdGenerationContext.DefaultNamespace),
    executionControl: ExecutionControlContext = ExecutionControlContext.standard,
    tagSpaces: TagSpaceContext = TagSpaceContext.default,
    resources: ResourceAccess = ResourceAccess.unavailable
  ) {
    def major: String = idGeneration.namespace.major
    def minor: String = idGeneration.namespace.minor
    def withScope(p: ScopeContext): CncfCore = copy(scope = p)
  }

  object CncfCore {
    trait Holder {
      def cncfCore: CncfCore

      def security: SecurityContext = cncfCore.security
      def observability: ObservabilityContext = cncfCore.observability
      def runtime: RuntimeContext = cncfCore.runtime
      def operationMode: OperationMode = runtime.operationMode
      def unitOfWork: org.goldenport.cncf.unitofwork.UnitOfWork = runtime.unitOfWork
      def jobContext: org.goldenport.cncf.job.JobContext = cncfCore.jobContext
      def framework: FrameworkParameter = cncfCore.framework
      def idGeneration: IdGenerationContext = cncfCore.idGeneration
      def executionControl: ExecutionControlContext = cncfCore.executionControl
      def tagSpaces: TagSpaceContext = cncfCore.tagSpaces
      def resources: ResourceAccess = cncfCore.resources
      def major = cncfCore.major
      def minor = cncfCore.minor
    }
  }

  final case class TagSpaceContext(
    subsystem: Vector[String] = Vector.empty,
    component: Vector[String] = Vector.empty,
    user: Vector[String] = Vector.empty
  ) {
    def effective: Vector[String] =
      (subsystem ++ component ++ user).map(_.trim).filter(_.nonEmpty).distinct

    def withSubsystem(values: Vector[String]): TagSpaceContext =
      copy(subsystem = values)

    def withComponent(values: Vector[String]): TagSpaceContext =
      copy(component = values)

    def withUser(values: Vector[String]): TagSpaceContext =
      copy(user = values)
  }

  object TagSpaceContext {
    val default: TagSpaceContext = TagSpaceContext()
  }

  final case class FrameworkParameter(
    commandExecutionMode: Option[CommandExecutionMode] = None,
    commandExecutionPolicy: Option[CommandExecutionPolicy] = None,
    workingSetEnabled: Boolean = true,
    callTreeEnabled: Boolean = false,
    inlineCallTree: Boolean = false,
    traceJob: Boolean = false,
    saveCallTree: Boolean = false,
    dslChokepointHooks: Option[Vector[DslChokepointHook]] = None,
    executionInvocationKey: Option[String] = None
  )

  /**
    * Standard CNCF ExecutionContext implementation.
    *
    * Used by Engine, servers, CLI, and tests inside CNCF.
    */
  final case class Instance(
    core: CoreExecutionContext.Core,
    cncfCore: CncfCore
  ) extends ExecutionContext {
    def withScope(p: ScopeContext): Instance = {
      copy(cncfCore = cncfCore.withScope(p))
    }
  }

  def create(): ExecutionContext =
    _create(
      SecurityContext.Privilege.User,
      IdGenerationContext.default(IdGenerationContext.DefaultNamespace, RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock),
      RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock
    )

  def create(clock: Clock): ExecutionContext =
    _create(
      SecurityContext.Privilege.User,
      IdGenerationContext.default(IdGenerationContext.DefaultNamespace, clock),
      clock
    )

  def create(
    privilege: SecurityContext.Privilege
  ): ExecutionContext =
    _create(
      privilege,
      IdGenerationContext.default(IdGenerationContext.DefaultNamespace, RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock),
      RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock
    )

  private def _create(
    privilege: SecurityContext.Privilege,
    idgeneration: IdGenerationContext,
    clock: Clock
  ): ExecutionContext = {
    val core = _core(clock)
    val security = _security_context(privilege)
    val observability = _observability_context(core)
    lazy val runtime: RuntimeContext = _test_runtime_context(() => context, observability) // TODO
    lazy val context: ExecutionContext = Instance(
      core = core,
      cncfCore = CncfCore(
        runtime,
        security = security,
        observability = observability,
        runtime = runtime,
        jobContext = org.goldenport.cncf.job.JobContext.empty,
        idGeneration = idgeneration
      )
    )
    context
  }

  def create(runtime: RuntimeContext): ExecutionContext =
    create(runtime, runtime)

  def create(scope: ScopeContext, runtime: RuntimeContext): ExecutionContext = {
    val basecore = _core(_execution_clock(scope))
    val binding = _execution_profile_binding(runtime).orElse(_execution_profile_binding(scope))
    val core = binding.map(_core_with_profile(basecore, _)).getOrElse(basecore)
    val security = _security_context(SecurityContext.Privilege.User)
    val observability = _observability_context(core)
    val idgeneration = binding.map(_.idGeneration).getOrElse(_id_generation_context(scope))
    val executioncontrol = binding.map(_.control).getOrElse(ExecutionControlContext.standard)
    lazy val context: ExecutionContext = Instance(
      core = core,
      cncfCore = CncfCore(
        runtime,
        security = security,
        observability = observability,
        runtime = runtime,
        jobContext = org.goldenport.cncf.job.JobContext.empty,
        idGeneration = idgeneration,
        executionControl = executioncontrol,
        resources = _resource_access(scope)
      )
    )
    context
  }

  def create(
    namespace: IdGenerationContext.IdNamespace
  ): ExecutionContext =
    _create(
      SecurityContext.Privilege.User,
      IdGenerationContext.default(namespace, RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock),
      RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock
    )

  def create(
    namespace: IdGenerationContext.IdNamespace,
    clock: Clock
  ): ExecutionContext =
    _create(
      SecurityContext.Privilege.User,
      IdGenerationContext.default(namespace, clock),
      clock
    )

  /**
    * Test and Executable Spec ExecutionContext.
    *
    * - For tests and Executable Specs only.
    * - Observability / trace will be injected here in the future.
    * - This is distinct from prod / cli / http contexts.
    */
//  def test(component: Component): ExecutionContext =
  def test(): ExecutionContext =
    create()

  def test(
    privilege: SecurityContext.Privilege
  ): ExecutionContext =
    create(privilege)

  def withJobContext(
    ctx: ExecutionContext,
    jobContext: org.goldenport.cncf.job.JobContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(jobContext = jobContext)
      )
    case _ =>
      ctx
  }

  def withRuntimeContext(
    ctx: ExecutionContext,
    runtime: RuntimeContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val binding = _execution_profile_binding(runtime)
      val idgeneration = binding.map(_.idGeneration).getOrElse(_id_generation_context_for_rebound(i.cncfCore.idGeneration, runtime))
      val core = binding.map(_core_with_profile(i.core, _)).getOrElse(_core_for_rebound(i.core, runtime))
      val executioncontrol = binding.map(_.control).getOrElse(i.cncfCore.executionControl)
      i.copy(
        core = core,
        cncfCore = i.cncfCore.copy(
          scope = runtime,
          runtime = runtime,
          idGeneration = idgeneration,
          executionControl = executioncontrol
        )
      )
    case _ =>
      ctx
  }

  private def _with_runtime_context_preserving_execution_profile(
    ctx: ExecutionContext,
    runtime: RuntimeContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          scope = runtime,
          runtime = runtime
        )
      )
    case _ =>
      ctx
  }

  def withExecutionInvocation(
    ctx: ExecutionContext,
    operationselector: String,
    explicitkey: Option[String] = None
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val selectedkey = explicitkey.orElse(i.cncfCore.framework.executionInvocationKey)
      _global_runtime_context(i.cncfCore.runtime) match {
        case Some(global) =>
          val binding = global.executionProfileRuntime.nextBinding(operationselector, selectedkey)
          _rebind_runtime_context(i.copy(
            core = _core_with_profile(i.core, binding),
            cncfCore = i.cncfCore.copy(
              idGeneration = binding.idGeneration,
              executionControl = binding.control
            )
          ))
        case None =>
          i
      }
    case _ =>
      ctx
  }

  def withExplicitExecutionInvocationKey(
    ctx: ExecutionContext,
    key: String
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(cncfCore = i.cncfCore.copy(
        framework = i.cncfCore.framework.copy(executionInvocationKey = Some(key))
      ))
    case _ =>
      ctx
  }

  def withIdGenerationContext(
    ctx: ExecutionContext,
    idGeneration: IdGenerationContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          idGeneration = idGeneration
        )
      )
    case _ =>
      ctx
  }

  def withTagSpaces(
    ctx: ExecutionContext,
    tagSpaces: TagSpaceContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          tagSpaces = tagSpaces
        )
      )
    case _ =>
      ctx
  }

  def withResourceAccess(
    ctx: ExecutionContext,
    resources: ResourceAccess
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(resources = resources)
      )
    case _ =>
      ctx
  }

  def withRuntimeContextContext(
    ctx: ExecutionContext,
    context: RuntimeContext.Context
  ): ExecutionContext =
    withRuntimeContext(ctx, ctx.runtime.withContext(context))

  def withFrameworkCommandExecutionMode(
    ctx: ExecutionContext,
    mode: CommandExecutionMode
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          framework = i.cncfCore.framework.copy(
            commandExecutionMode = Some(mode)
          )
        )
      )
    case _ =>
      ctx
  }

  def withFrameworkCommandExecutionPolicy(
    ctx: ExecutionContext,
    policy: CommandExecutionPolicy
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          framework = i.cncfCore.framework.copy(
            commandExecutionPolicy = Some(policy)
          )
        )
      )
    case _ =>
      ctx
  }

  def withFrameworkWorkingSetEnabled(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          framework = i.cncfCore.framework.copy(
            workingSetEnabled = enabled
          )
        )
      )
    case _ =>
      ctx
  }

  def withFrameworkCallTreeEnabled(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val observability = i.cncfCore.observability.copy(
        callTreeContext = if (enabled) CallTreeContext.enabled else CallTreeContext.Disabled
      )
      _rebind_runtime_context(i.copy(
        cncfCore = i.cncfCore.copy(
          observability = observability,
          framework = i.cncfCore.framework.copy(
            callTreeEnabled = enabled
          )
        )
      ))
    case _ =>
      ctx
  }

  def withFrameworkInlineCallTreeEnabled(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val observability =
        if (enabled && !i.cncfCore.observability.callTreeContext.isEnabled)
          i.cncfCore.observability.copy(callTreeContext = CallTreeContext.enabled)
        else
          i.cncfCore.observability
      _rebind_runtime_context(i.copy(
        cncfCore = i.cncfCore.copy(
          observability = observability,
          framework = i.cncfCore.framework.copy(
            callTreeEnabled = i.cncfCore.framework.callTreeEnabled || enabled,
            inlineCallTree = enabled
          )
        )
      ))
    case _ =>
      ctx
  }

  def withFrameworkTraceJobEnabled(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val observability =
        if (enabled && !i.cncfCore.observability.callTreeContext.isEnabled)
          i.cncfCore.observability.copy(callTreeContext = CallTreeContext.enabled)
        else
          i.cncfCore.observability
      _rebind_runtime_context(i.copy(
        cncfCore = i.cncfCore.copy(
          observability = observability,
          framework = i.cncfCore.framework.copy(
            callTreeEnabled = i.cncfCore.framework.callTreeEnabled || enabled,
            traceJob = enabled
          )
        )
      ))
    case _ =>
      ctx
  }

  def withFrameworkSaveCallTreeEnabled(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val observability =
        if (enabled && !i.cncfCore.observability.callTreeContext.isEnabled)
          i.cncfCore.observability.copy(callTreeContext = CallTreeContext.enabled)
        else
          i.cncfCore.observability
      _rebind_runtime_context(i.copy(
        cncfCore = i.cncfCore.copy(
          observability = observability,
          framework = i.cncfCore.framework.copy(
            callTreeEnabled = i.cncfCore.framework.callTreeEnabled || enabled,
            saveCallTree = enabled
          )
        )
      ))
    case _ =>
      ctx
  }

  private def _rebind_runtime_context(
    ctx: Instance
  ): ExecutionContext = {
    lazy val rebound: ExecutionContext =
      _with_runtime_context_preserving_execution_profile(
        ctx,
        ctx.runtime.withUnitOfWorkContext(rebound, ctx.runtime.toToken)
      )
    rebound
  }

  def withFrameworkDslChokepointHooks(
    ctx: ExecutionContext,
    hooks: Vector[DslChokepointHook]
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          framework = i.cncfCore.framework.copy(
            dslChokepointHooks = Some(hooks)
          )
        )
      )
    case _ =>
      ctx
  }

  def withSecurityContext(
    ctx: ExecutionContext,
    security: SecurityContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(security = security)
      )
    case _ =>
      ctx
  }

  def withAggregateInternalRead(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext =
    ctx.withScope(ScopeContext.withAggregateInternalRead(ctx.cncfCore.scope, enabled))

  def withObservabilityContext(
    ctx: ExecutionContext,
    observability: ObservabilityContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(observability = observability)
      )
    case _ =>
      ctx
  }

  private def _id_generation_context(
    scope: ScopeContext
  ): IdGenerationContext =
    IdGenerationContext.default(_id_namespace(scope), _execution_clock(scope))

  private def _id_namespace(
    scope: ScopeContext
  ): IdGenerationContext.IdNamespace =
    _global_runtime_context(scope)
      .map(_.config.idNamespace)
      .getOrElse(IdGenerationContext.DefaultNamespace)

  private def _execution_clock(
    scope: ScopeContext
  ): Clock =
    _execution_clock_option(scope)
      .getOrElse(RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock)

  private def _execution_clock_option(
    scope: ScopeContext
  ): Option[Clock] =
    _global_runtime_context(scope)
      .map(_.config.executionClock.clock)

  private def _core_for_rebound(
    current: CoreExecutionContext.Core,
    runtime: RuntimeContext
  ): CoreExecutionContext.Core =
    _execution_profile_binding(runtime)
      .map(_core_with_profile(current, _))
      .orElse(_execution_clock_option(runtime).map(clock => _core_with_clock(current, clock)))
      .getOrElse(current)

  private def _core_with_profile(
    current: CoreExecutionContext.Core,
    binding: ExecutionProfileBinding
  ): CoreExecutionContext.Core =
    binding.environmentAssumptions.apply_to(current, binding.clock).copy(
      random = binding.random,
      entropy = binding.entropy
    )

  private def _core_with_clock(
    current: CoreExecutionContext.Core,
    clock: Clock
  ): CoreExecutionContext.Core = {
    val vm = current.vm match {
      case instant: VirtualMachineContext.Instant =>
        instant.copy(core = instant.core.copy(clock = clock))
      case other => other
    }
    current.copy(vm = vm, clock = clock)
  }

  private def _id_generation_context_for_rebound(
    current: IdGenerationContext,
    runtime: RuntimeContext
  ): IdGenerationContext =
    _execution_profile_binding(runtime)
      .map(_.idGeneration)
      .getOrElse(current)

  private def _execution_profile_binding(
    scope: ScopeContext
  ): Option[ExecutionProfileBinding] =
    _global_runtime_context(scope).map(_.executionProfileRuntime.baseBinding)

  private def _global_runtime_context(
    scope: ScopeContext
  ): Option[GlobalRuntimeContext] =
    scope match {
      case x: GlobalRuntimeContext => Some(x)
      case other => other.parent.flatMap(_global_runtime_context)
    }

  private def _resource_access(
    scope: ScopeContext
  ): ResourceAccess =
    _global_runtime_context(scope)
      .map { global =>
        ResourceAccess.standard(
          global.config.resourceUrlPolicy,
          global.config.textusUrnResourcePolicy,
          global.httpDriver
        )
      }
      .getOrElse(ResourceAccess.unavailable)

  private def _core(clock: Clock): CoreExecutionContext.Core =
    CoreExecutionContext.Core(
      environment = CoreEnvironmentContext.local(),
      vm = VirtualMachineContext.Instant(
        VirtualMachineContext.Core(
          clock = clock,
          timezone = ZoneId.of("UTC"),
          encoding = Charset.forName("UTF-8"),
          lineSeparator = "\n",
          mathContext = MathContext.DECIMAL64,
          environmentVariables = Map.empty,
          resourceBundleBaseNames = Nil,
          resourceBundleLocales = Nil,
          resourceBundleResolutionOrder = Nil
        )
      ),
      i18n = I18nContext.Instant(
        I18nContext.Core(
          textNormalizationPolicy = "default",
          textComparisonPolicy = "default",
          dateTimeFormatPolicy = "default",
          locale = Some(Locale.ROOT)
        )
      ),
      locale = Locale.ROOT,
      timezone = ZoneId.of("UTC"),
      encoding = Charset.forName("UTF-8"),
      clock = clock,
      math = MathContext.DECIMAL64,
      random = RandomContext.from("fixed"),
      entropy = EntropyContext.deterministic("cncf-test"),
      logger = TestLogger
    )

  private def _security_context(
    privilege: SecurityContext.Privilege
  ): SecurityContext =
    SecurityContext(
      principal = new TestPrincipal(privilege),
      capabilities = privilege.capabilities,
      level = privilege.level,
      subjectKind = privilege.subjectKind
    )

  private def _observability_context(
    core: CoreExecutionContext.Core
  ): ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime")),
      sagaId = None
    )

  private def _test_runtime_context(
    context: () => ExecutionContext,
    observability: ObservabilityContext
  ): RuntimeContext = {
    val driver = FakeHttpDriver.okText("nop")
    val consequenceinterpreter = new (UnitOfWorkOp ~> Consequence) {
      def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
        throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
    }
    new RuntimeContext(
      core = RuntimeContext.core(
        name = "execution-context-test",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(DataStoreSpace.default())),
        entitystore = Some(
          EntityStoreContext(
            EntityStoreSpace.create(
              org.goldenport.configuration.ResolvedConfiguration(
                org.goldenport.configuration.Configuration.empty,
                org.goldenport.configuration.ConfigurationTrace.empty
              )
            )
          )
        ),
        entityspace = Some(EntitySpaceContext(new EntitySpace()))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context()),
      unitOfWorkInterpreterFn = consequenceinterpreter,
      commitAction = uow => {
        val _ = uow.commit()
        ()
      },
      abortAction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "execution-context-test"
    )
  }

  private final class TestPrincipal(
    privilege: SecurityContext.Privilege
  ) extends Principal {
    def id: PrincipalId = privilege.principalId
    def attributes: Map[String, String] = privilege.attributes
  }

  private object TestLogger extends Logger {
    def trace(message: => String): Unit = {}
    def debug(message: => String): Unit = {}
    def info(message: => String): Unit = {}
    def warn(message: => String): Unit = {}
    def error(message: => String): Unit = {}
    def error(cause: Throwable, message: => String): Unit = {}
    def fatal(message: => String): Unit = {}
    def fatal(cause: Throwable, message: => String): Unit = {}
  }
}

final case class ExecutionContextId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends CoreUniversalId(major, minor, "execution_context", timestamp, entropy)

object ExecutionContextId {
  def generate(): ExecutionContextId =
    ExecutionContextId("cncf", "execution_context") // TODO

  def create(
    purpose: String,
    timestamp: Instant
  )(using ctx: ExecutionContext): ExecutionContextId =
    create(purpose, timestamp, ctx.idGeneration)

  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): ExecutionContextId =
    ExecutionContextId(
      major = idgeneration.namespace.major,
      minor = idgeneration.namespace.minor,
      timestamp = Some(timestamp),
      entropy = Some(idgeneration.opaqueId(s"execution-context.$purpose"))
    )
}
// final case class ExecutionContext(
//   executionId: UniversalId,
//   timestamp: Instant,
//   environment: EnvironmentContext,
//   security: Option[SecurityContext],
//   observability: ObservabilityContext,
//   resolvedConfig: ResolvedConfig,
//   runtime: RuntimeContext
// )

// object ExecutionContext {
//   // TEMPORARY builder (to be refined after demo)
//   def build(
//     runtime: RuntimeContext,
//     resolvedConfig: ResolvedConfig,
//     environment: EnvironmentContext,
//     security: Option[SecurityContext] = None,
//     observability: ObservabilityContext = ObservabilityContext.empty,
//     executionId: UniversalId = UniversalId.generate("exec"),
//     timestamp: Instant = Instant.now()
//   ): ExecutionContext =
//     ExecutionContext(
//       executionId = executionId,
//       timestamp = timestamp,
//       environment = environment,
//       security = security,
//       observability = observability,
//       resolvedConfig = resolvedConfig,
//       runtime = runtime
//     )
// }
