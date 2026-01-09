package org.goldenport.cncf.context

import java.math.MathContext
import java.nio.charset.Charset
import java.time.{Clock, ZoneId}
import java.util.Locale
import org.goldenport.context.{EnvironmentContext as CoreEnvironmentContext, ExecutionContext as CoreExecutionContext, I18nContext, RandomContext, VirtualMachineContext}
import org.goldenport.id.{UniversalId as CoreUniversalId}
import org.goldenport.log.Logger
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import cats.~>

/**
 * CNCF ExecutionContext extends goldenport core ExecutionContext
 * with runtime-only execution state.
 *
 * - Core execution assumptions are provided via ExecutionContext.Core
 * - RuntimeContext is CNCF-specific and MUST NOT leak into core logic
 *
 * This is a value-backed abstract class.
 */
/*
 * @since   Dec. 21, 2025
 *  version Dec. 31, 2025
 * @version Jan. 10, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ExecutionContext
  extends CoreExecutionContext
  with ExecutionContext.CncfCore.Holder {

  /** Canonical core execution context value */
  def core: CoreExecutionContext.Core

  /** CNCF execution-context-specific value */
  def cncfCore: ExecutionContext.CncfCore
}

object ExecutionContext {

  /**
   * Marker for application-specific execution context carried by CNCF.
   * CNCF transports this value but does not interpret it.
   */
  trait ApplicationContext

  /**
   * Runtime namespace for CNCF-only extensions.
   */
  case class CncfCore(
    security: SecurityContext,
    observability: ObservabilityContext,
    runtime: RuntimeContext,
    jobContext: org.goldenport.cncf.job.JobContext,
    application: Option[ApplicationContext] = None,
    system: SystemContext = SystemContext.empty
  )
  object CncfCore {
    trait Holder {
      def cncfCore: CncfCore

      def security: SecurityContext = cncfCore.security
      def observability: ObservabilityContext = cncfCore.observability
      def runtime: RuntimeContext = cncfCore.runtime
      def unitOfWork: org.goldenport.cncf.unitofwork.UnitOfWork = runtime.unitOfWork
      def jobContext: org.goldenport.cncf.job.JobContext = cncfCore.jobContext
      def application: Option[ApplicationContext] = cncfCore.application
      def system: SystemContext = cncfCore.system
    }
  }

  /**
   * Standard CNCF ExecutionContext implementation.
   *
   * Used by Engine, servers, CLI, and tests inside CNCF.
   */
  final case class Instance(
    core: CoreExecutionContext.Core,
    cncfCore: CncfCore
  ) extends ExecutionContext

  def create(): ExecutionContext = {
    val core = _core()
    val security = _security_context()
    val observability = _observability_context(core)
    lazy val context: ExecutionContext = Instance(
      core = core,
      cncfCore = CncfCore(
        security = security,
        observability = observability,
        runtime = new _TestRuntimeContext(() => context),
        jobContext = org.goldenport.cncf.job.JobContext.empty,
        system = SystemContext.empty
      )
    )
    context
  }

  def createWith(
    application: Option[ApplicationContext]
  ): ExecutionContext =
    application
      .map(app => withApplicationContext(create(), app))
      .getOrElse(create())

  def createWithSystem(
    system: SystemContext
  ): ExecutionContext =
    withSystemContext(create(), system)

  /**
   * Test and Executable Spec ExecutionContext.
   *
   * - For tests and Executable Specs only.
   * - Observability / trace will be injected here in the future.
   * - This is distinct from prod / cli / http contexts.
   */
  def test(): ExecutionContext =
    create()

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

  def withApplicationContext(
    ctx: ExecutionContext,
    app: ApplicationContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          application = Some(app)
        )
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

  def withSystemContext(
    ctx: ExecutionContext,
    system: SystemContext
  ): ExecutionContext = ctx match {
    case i: Instance =>
      i.copy(
        cncfCore = i.cncfCore.copy(
          system = system
        )
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

  private def _security_context(): SecurityContext =
    SecurityContext(
      principal = new _TestPrincipal(),
      capabilities = Set.empty,
      level = SecurityLevel("test")
    )

  private def _observability_context(
    core: CoreExecutionContext.Core
  ): ObservabilityContext = {
    val id = ExecutionContextId.generate()
    ObservabilityContext(
      traceId = TraceId(id),
      spanId = None,
      correlationId = Some(CorrelationId(id))
    )
  }

  private final class _TestRuntimeContext(
    context: () => ExecutionContext
  ) extends RuntimeContext {
    lazy val unitOfWork: UnitOfWork = new UnitOfWork(context())

    def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> cats.Id) =
      new (UnitOfWorkOp ~> cats.Id) {
        def apply[A](fa: UnitOfWorkOp[A]): cats.Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in test context")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
      Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in test context"))

    def commit(): Unit = {
      unitOfWork.commit()
      ()
    }

    def abort(): Unit = {
      unitOfWork.rollback()
      ()
    }
    def dispose(): Unit = {}

    def toToken: String = "execution-context-test"
  }

  private final class _TestPrincipal extends Principal {
    def id: PrincipalId = PrincipalId("test-principal")
    def attributes: Map[String, String] = Map.empty
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

final case class UniversalId(
  value: CoreUniversalId
)

object UniversalId {
  @deprecated(
    "UniversalId generation is core responsibility; receive UniversalId from upstream",
    "0.x.y"
  )
  def generate(prefix: String): UniversalId =
    throw new UnsupportedOperationException(
      "UniversalId must be generated by core UniversalIdGenerator"
    )
}

final case class ExecutionContextId(
  major: String,
  minor: String
) extends CoreUniversalId(major, minor, "execution_context")

object ExecutionContextId {
  def generate(): ExecutionContextId =
    ExecutionContextId("cncf", "execution_context")
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
