package org.goldenport.cncf.metrics

import java.time.Instant
import org.goldenport.record.Record

/*
 * @since   May. 11, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class MetricScopeDefinition(
  scope: String,
  label: String,
  description: String,
  labelKeys: Vector[String]
) {
  def toRecord: Record =
    Record.dataAuto(
      "scope" -> scope,
      "label" -> label,
      "description" -> description,
      "label_keys" -> labelKeys
    )
}

final case class RuntimeMetricPoint(
  scope: String,
  name: String,
  labels: Map[String, String],
  count: Long,
  errorCount: Long = 0L,
  durationCount: Long = 0L,
  durationTotalMillis: Long = 0L,
  durationMinMillis: Option[Long] = None,
  durationMaxMillis: Option[Long] = None
) {
  def durationAvgMillis: Option[BigDecimal] =
    Option.when(durationCount > 0)(
      BigDecimal(durationTotalMillis) / BigDecimal(durationCount)
    )

  def toRecord: Record =
    Record.dataOption(
      "scope" -> Some(scope),
      "name" -> Some(name),
      "labels" -> Some(Record.data(labels.toVector.sortBy(_._1)*)),
      "count" -> Some(count),
      "error_count" -> Some(errorCount),
      "duration_count" -> Option.when(durationCount > 0)(durationCount),
      "duration_total_millis" -> Option.when(durationCount > 0)(durationTotalMillis),
      "duration_min_millis" -> durationMinMillis,
      "duration_max_millis" -> durationMaxMillis,
      "duration_avg_millis" -> durationAvgMillis.map(_.setScale(2, BigDecimal.RoundingMode.HALF_UP))
    )
}

final case class RuntimeMetricsSnapshot(
  generatedAt: Instant,
  points: Vector[RuntimeMetricPoint],
  catalog: Vector[MetricScopeDefinition]
) {
  def toRecord: Record =
    Record.dataAuto(
      "generated_at" -> generatedAt.toString,
      "metrics" -> points.map(_.toRecord),
      "catalog" -> catalog.map(_.toRecord)
    )
}

object RuntimeMetricsCatalog {
  val scopes: Vector[MetricScopeDefinition] = Vector(
    MetricScopeDefinition(
      "web.request",
      "Web request",
      "HTTP/Web request counts, errors, status class, and elapsed time.",
      Vector("outcome", "status")
    ),
    MetricScopeDefinition(
      "action.execution",
      "Action execution",
      "Action execution counts, failures, and elapsed time.",
      Vector("outcome")
    ),
    MetricScopeDefinition(
      "authorization.decision",
      "Authorization decision",
      "Authorization allow/deny decision counts.",
      Vector("outcome", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "dsl.chokepoint",
      "DSL chokepoint",
      "Internal DSL chokepoint success/failure counts.",
      Vector("outcome")
    ),
    MetricScopeDefinition(
      "validation",
      "Validation",
      "Validation failure counts grouped by structured diagnostic key.",
      Vector("diagnostic_key")
    ),
    MetricScopeDefinition(
      "operation-request-validation",
      "Operation request validation",
      "Operation request validation failure counts grouped by structured diagnostic key.",
      Vector("diagnostic_key")
    ),
    MetricScopeDefinition(
      "blob.operation",
      "Blob operation",
      "Blob operation counts grouped by outcome, kind, source, backend, and diagnostic key.",
      Vector("outcome", "kind", "source", "backend", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "rule.execution",
      "Rule execution",
      "Rule firing counts, failures, and elapsed time grouped by operation, RuleSet, and diagnostic key.",
      Vector("outcome", "operation", "rule_set", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "spi.invocation",
      "SPI invocation",
      "Canonical SPI invocation counts, failures, provider/socket components, and elapsed time.",
      Vector("outcome", "contract", "operation", "provider_component", "socket_component", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "operation-evaluation.delivery",
      "Operation evaluation delivery",
      "Payload-safe Corpus/Experiment fact delivery counts, limitations, failures, and elapsed time.",
      Vector(
        "outcome",
        "operation",
        "status",
        "fact_kind",
        "fact_source",
        "sink_contract",
        "provider_component",
        "socket_component",
        "limitation_kinds",
        "diagnostic_keys"
      )
    ),
    MetricScopeDefinition(
      "mcp-client.invocation",
      "MCP client invocation",
      "Provider-neutral MCP catalog and tool invocation counts, failures, and elapsed time without payload or transport data.",
      Vector("outcome", "operation", "server_set", "server", "tool", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "process.execution",
      "Process execution",
      "Approved Process Execution counts, failures, terminal state, and elapsed time without payload values.",
      Vector("outcome", "capability", "driver", "termination", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "resource-tree.snapshot",
      "Resource tree snapshot",
      "Read-only admitted resource tree snapshot counts and failures without physical paths or content.",
      Vector("outcome", "tree", "provider", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "resource-tree.query",
      "Resource tree query",
      "Bounded admitted resource tree query counts and failures without physical paths, selector values, or content.",
      Vector("outcome", "tree", "provider", "selector", "max_depth", "max_visited_directories", "max_entries", "max_entry_bytes", "max_total_bytes", "visited_directories", "matched_entries", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "service-container.lifecycle",
      "Service container lifecycle",
      "Managed service lifecycle counts, failures, and elapsed time without provider payloads or credentials.",
      Vector("outcome", "operation", "ownership_mode", "owner_kind", "owner_id", "service_id", "cleanup_policy", "status", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "component-initialization.parameter-resolution",
      "Component initialization parameter resolution",
      "Bootstrap parameter resolution counts and structured failures with bounded logical identity and provenance only.",
      Vector("component", "component_instance", "parameter", "requirement", "confidentiality", "provenance", "outcome", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "entity.conditional-transition",
      "Entity conditional transition",
      "Conditional transition outcomes and structured diagnostics without Entity payload or expected values.",
      Vector("outcome", "diagnostic_key")
    ),
    MetricScopeDefinition(
      "diagnostic-payload.externalization",
      "Diagnostic payload externalization",
      "Diagnostic payload externalization outcomes grouped by payload kind and destination.",
      Vector("status", "payload_kind", "destination")
    ),
    MetricScopeDefinition(
      "otel.export",
      "OpenTelemetry export",
      "OpenTelemetry export outcomes grouped by exported signal.",
      Vector("status", "signal")
    ),
    MetricScopeDefinition(
      "entity-access",
      "Entity access",
      "Entity/data/view access metrics from the entity access registry.",
      Vector("entity", "source", "outcome", "reason", "working_set_state")
    ),
    MetricScopeDefinition(
      "component",
      "Component",
      "Component-owned bounded metrics with contract-defined, low-cardinality labels.",
      Vector("component")
    )
  )

  def toRecord: Record =
    Record.dataAuto("scopes" -> scopes.map(_.toRecord))
}
