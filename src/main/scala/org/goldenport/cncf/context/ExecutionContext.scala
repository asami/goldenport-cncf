package org.goldenport.cncf.context

import java.math.MathContext
import java.nio.charset.Charset
import java.time.{Clock, ZoneId}
import java.util.Locale
import org.goldenport.context.{EnvironmentContext as CoreEnvironmentContext, ExecutionContext as CoreExecutionContext, I18nContext, RandomContext, VirtualMachineContext}
import org.goldenport.id.{UniversalId as CoreUniversalId}
import org.goldenport.log.Logger
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.action.CommandExecutionMode
import org.goldenport.cncf.http.{FakeHttpDriver, HttpDriver}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.observability.{CallTreeContext, DslChokepointHook}
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
 * @version Apr. 15, 2026
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
    framework: FrameworkParameter = FrameworkParameter()
  ) {
    def major = "sys"
    def minor = "sys"
    def withScope(p: ScopeContext): CncfCore = copy(scope = p)
  }

  object CncfCore {
    trait Holder {
      def cncfCore: CncfCore

      def security: SecurityContext = cncfCore.security
      def observability: ObservabilityContext = cncfCore.observability
      def runtime: RuntimeContext = cncfCore.runtime
      def unitOfWork: org.goldenport.cncf.unitofwork.UnitOfWork = runtime.unitOfWork
      def jobContext: org.goldenport.cncf.job.JobContext = cncfCore.jobContext
      def framework: FrameworkParameter = cncfCore.framework
      def major = cncfCore.major
      def minor = cncfCore.minor
    }
  }

  final case class FrameworkParameter(
    commandExecutionMode: Option[CommandExecutionMode] = None,
    callTreeEnabled: Boolean = false,
    dslChokepointHooks: Option[Vector[DslChokepointHook]] = None
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

  def create(): ExecutionContext = {
    val core = _core()
    val security = _security_context(SecurityContext.Privilege.User)
    val observability = _observability_context(core)
    lazy val runtime: RuntimeContext = _testRuntimeContext(() => context, observability) // TODO
    lazy val context: ExecutionContext = Instance(
      core = core,
      cncfCore = CncfCore(
        runtime,
        security = security,
        observability = observability,
        runtime = runtime,
        jobContext = org.goldenport.cncf.job.JobContext.empty
      )
    )
    context
  }

  def create(
    privilege: SecurityContext.Privilege
  ): ExecutionContext = {
    val core = _core()
    val security = _security_context(privilege)
    val observability = _observability_context(core)
    lazy val runtime: RuntimeContext = _testRuntimeContext(() => context, observability) // TODO
    lazy val context: ExecutionContext = Instance(
      core = core,
      cncfCore = CncfCore(
        runtime,
        security = security,
        observability = observability,
        runtime = runtime,
        jobContext = org.goldenport.cncf.job.JobContext.empty
      )
    )
    context
  }

  def create(runtime: RuntimeContext): ExecutionContext =
    create(runtime, runtime)

  def create(scope: ScopeContext, runtime: RuntimeContext): ExecutionContext = {
    val core = _core()
    val security = _security_context(SecurityContext.Privilege.User)
    val observability = _observability_context(core)
    lazy val context: ExecutionContext = Instance(
      core = core,
      cncfCore = CncfCore(
        runtime,
        security = security,
        observability = observability,
        runtime = runtime,
        jobContext = org.goldenport.cncf.job.JobContext.empty
      )
    )
    context
  }

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
      i.copy(
        cncfCore = i.cncfCore.copy(runtime = runtime)
      )
    case _ =>
      ctx
  }

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

  def withFrameworkCallTreeEnabled(
    ctx: ExecutionContext,
    enabled: Boolean
  ): ExecutionContext = ctx match {
    case i: Instance =>
      val observability = i.cncfCore.observability.copy(
        callTreeContext = if (enabled) CallTreeContext.enabled else CallTreeContext.Disabled
      )
      i.copy(
        cncfCore = i.cncfCore.copy(
          observability = observability,
          framework = i.cncfCore.framework.copy(
            callTreeEnabled = enabled
          )
        )
      )
    case _ =>
      ctx
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

  private def _core(): CoreExecutionContext.Core =
    CoreExecutionContext.Core(
      environment = CoreEnvironmentContext.local(),
      vm = VirtualMachineContext.Instant(
        VirtualMachineContext.Core(
          clock = Clock.systemUTC(),
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
      clock = Clock.systemUTC(),
      math = MathContext.DECIMAL64,
      random = RandomContext.from("fixed"),
      logger = _TestLogger
    )

  private def _security_context(
    privilege: SecurityContext.Privilege
  ): SecurityContext =
    SecurityContext(
      principal = new _TestPrincipal(privilege),
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
      correlationId = Some(CorrelationId("test", "runtime"))
    )

  private def _testRuntimeContext(
    context: () => ExecutionContext,
    observability: ObservabilityContext
  ): RuntimeContext = {
    val driver = FakeHttpDriver.okText("nop")
    val consequenceInterpreter = new (UnitOfWorkOp ~> Consequence) {
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
      unitOfWorkInterpreterFn = consequenceInterpreter,
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

  private final class _TestPrincipal(
    privilege: SecurityContext.Privilege
  ) extends Principal {
    def id: PrincipalId = privilege.principalId
    def attributes: Map[String, String] = privilege.attributes
  }

  private object _TestLogger extends Logger {
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
  minor: String
) extends CoreUniversalId(major, minor, "execution_context")

object ExecutionContextId {
  def generate(): ExecutionContextId =
    ExecutionContextId("cncf", "execution_context") // TODO
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
