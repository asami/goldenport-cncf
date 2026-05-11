package org.goldenport.cncf.metrics

import java.time.Instant
import org.goldenport.record.Record

/*
 * @since   May. 11, 2026
 * @version May. 11, 2026
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
      "diagnostic-payload.externalization",
      "Diagnostic payload externalization",
      "Diagnostic payload externalization outcomes grouped by payload kind and destination.",
      Vector("status", "payload_kind", "destination")
    ),
    MetricScopeDefinition(
      "entity-access",
      "Entity access",
      "Entity/data/view access metrics from the entity access registry.",
      Vector("entity", "source", "outcome", "reason", "working_set_state")
    )
  )

  def toRecord: Record =
    Record.dataAuto("scopes" -> scopes.map(_.toRecord))
}
