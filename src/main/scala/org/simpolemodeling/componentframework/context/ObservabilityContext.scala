package org.simplemodeling.componentframework.context

import java.util.UUID

/*
 * @since   Dec. 21, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
final case class TraceId(
  value: String
)

object TraceId {
  def generate(): TraceId =
    TraceId(UUID.randomUUID().toString)
}

final case class SpanId(
  value: String
)

final case class ObservabilityContext(
  traceId: TraceId,
  spanId: Option[SpanId],
  correlationId: Option[String]
)

object ObservabilityContext {
  def empty: ObservabilityContext =
    ObservabilityContext(
      traceId = TraceId.generate(),
      spanId = None,
      correlationId = None
    )
}
