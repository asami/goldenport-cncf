package org.simplemodeling.componentframework.context

import java.time.Instant
import org.simplemodeling.componentframework.config.ResolvedConfig

/*
 * @since   Dec. 21, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
final case class UniversalId(
  value: String
)

object UniversalId {
  def generate(prefix: String): UniversalId =
    UniversalId(s"${prefix}-${java.util.UUID.randomUUID().toString}")
}

final case class ExecutionContext(
  executionId: UniversalId,
  timestamp: Instant,
  environment: EnvironmentContext,
  security: Option[SecurityContext],
  observability: ObservabilityContext,
  resolvedConfig: ResolvedConfig,
  runtime: RuntimeContext
)

object ExecutionContext {
  // TEMPORARY builder (to be refined after demo)
  def build(
    runtime: RuntimeContext,
    resolvedConfig: ResolvedConfig,
    environment: EnvironmentContext,
    security: Option[SecurityContext] = None,
    observability: ObservabilityContext = ObservabilityContext.empty,
    executionId: UniversalId = UniversalId.generate("exec"),
    timestamp: Instant = Instant.now()
  ): ExecutionContext =
    ExecutionContext(
      executionId = executionId,
      timestamp = timestamp,
      environment = environment,
      security = security,
      observability = observability,
      resolvedConfig = resolvedConfig,
      runtime = runtime
    )
}
