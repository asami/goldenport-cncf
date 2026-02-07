package org.goldenport.cncf.context

import org.goldenport.record.Record
import org.goldenport.cncf.observability.{CallTreeContext, ObservabilityEngine}
import org.goldenport.cncf.context.ScopeContext
import org.goldenport.cncf.context.ScopeKind
import java.util.Locale
import org.goldenport.id.UniversalId

/*
 * @since   Dec. 21, 2025
 *  version Dec. 31, 2025
 * @version Jan. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TraceId(
  subsystem: String,
  entry: String
) extends UniversalId(
  major = subsystem,
  minor = entry,
  kind  = "trace"
)

final case class SpanId(
  subsystem: String,
  scope: String,
  spanKind: String
) extends UniversalId(
  major   = subsystem,
  minor   = scope,
  kind    = "span",
  subkind = Some(spanKind)
)

final case class CorrelationId(
  subsystem: String,
  boundary: String
) extends UniversalId(
  major = subsystem,
  minor = boundary,
  kind  = "correlation"
)

final case class ObservabilityContext(
  traceId: TraceId,
  spanId: Option[SpanId],
  correlationId: Option[CorrelationId],
  callTreeContext: CallTreeContext = CallTreeContext.Disabled
) {
  def createChild(
    parent: ScopeContext,
    kind: ScopeKind,
    name: String
  ): ObservabilityContext = {
    val subsystem =
      if (kind == ScopeKind.Subsystem) Some(name)
      else _find_subsystem_(parent)
    val sanitizedName = _sanitize_label(name)
    val sanitizedSubsystem = subsystem.map(_sanitize_label)

    val nextTraceId = traceId

    val nextSpanId =
      sanitizedSubsystem.map(sub => SpanId(sub, sanitizedName, _span_kind_label_(kind, sanitizedName)))

    val nextCorrelationId =
      correlationId.orElse(sanitizedSubsystem.map(sub => CorrelationId(sub, "runtime")))

    ObservabilityContext(
      traceId = nextTraceId,
      spanId = nextSpanId,
      correlationId = nextCorrelationId,
      callTreeContext = callTreeContext
    )
  }

  private def _find_subsystem_(scope: ScopeContext): Option[String] =
    scope match {
      case s if s.core.kind == ScopeKind.Subsystem => Some(s.core.name)
      case s => s.core.parent.flatMap(p => _find_subsystem_(p))
    }

  private def _span_kind_label_(kind: ScopeKind, name: String): String =
    kind match {
      case ScopeKind.Runtime => "runtime"
      case ScopeKind.Subsystem => "subsystem"
      case ScopeKind.Component => "component"
      case ScopeKind.Service => "service"
      case ScopeKind.Action => _action_subkind_(name)
    }

  private def _action_subkind_(name: String): String = {
    val lower = name.toLowerCase(java.util.Locale.ROOT)
    if (lower.contains("message")) "message"
    else if (lower.contains("event")) "event"
    else "operation"
  }

  // Label sanitization intentionally lossy: collisions (e.g. foo-bar vs foo.bar) are acceptable here
  // because observability readability is prioritized and reversibility is not required.
  private def _sanitize_label(value: String): String = {
    val normalized = value.replaceAll("[^A-Za-z0-9_]", "_")
    if (normalized.isEmpty) "unnamed" else normalized
  }

  def emitInfo(
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    ObservabilityEngine.emitInfo(this, scope, name, attributes)

  def emitWarn(
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    ObservabilityEngine.emitWarn(this, scope, name, attributes)

  def emitError(
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    ObservabilityEngine.emitError(this, scope, name, attributes)
}

object ObservabilityContext {
  @deprecated(
    "ObservabilityContext requires TraceId (UniversalId) from core; empty context is invalid",
    "0.x.y"
  )
  def empty: ObservabilityContext =
    throw new UnsupportedOperationException(
      "Provide TraceId from core UniversalIdGenerator"
    )
}
