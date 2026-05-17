package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.JobQueryReadModel
import org.goldenport.cncf.knowledge.{KnowledgeNodeId, KnowledgeSpaceProjection}
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, DiagnosticPayloadReference}
import org.goldenport.cncf.operation.{AssociationBindingOperationDefinition, CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationImageBinding, ImageBindingOperationDefinition}
import org.goldenport.cncf.projection.{AuthorizationPolicyProjection, DescribeProjection, HelpProjection, SchemaProjection}
import org.goldenport.cncf.search.{SearchMode, SearchPlanningProfile, WebSearchQueryPlanner}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Request as ProtocolRequest}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataConfidentiality, Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.{Json, JsonObject}
import io.circe.parser.parse

/*
 * @since   May. 18, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererObservabilityPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererSystemAdminPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  def renderSystemPerformance(subsystem: Subsystem): Page =
    Page(performance_page(subsystem))

  def renderSystemAdminObservability(subsystem: Subsystem): Page =
    Page(observability_admin_page(subsystem))

  def renderSystemAdminObservabilityMetrics(subsystem: Subsystem): Page =
    Page(observability_metrics_page(subsystem))

  def renderSystemAdminObservabilityDiagnostics(): Page =
    Page(observability_diagnostics_page())

  def renderSystemAdminObservabilityDiagnostic(
    scope: String,
    diagnosticKey: String
  ): Option[Page] =
    RuntimeDashboardMetrics.diagnosticDetail(scope, diagnosticKey).map(group =>
      Page(observability_diagnostic_detail_page(group))
    )

  protected def observability_admin_page(subsystem: Subsystem): String = {
    val runtime = RuntimeConfig.from(subsystem.configuration)
    val config = runtime.diagnosticPayloadExternalizationConfig
    val scopeCards = RuntimeDashboardMetrics.diagnosticScopes.map(observability_scope_card).mkString("\n")
    simple_page(
      title = "System Observability",
      subtitle = "Structured diagnostic drill-down and diagnostic payload references",
      body =
        s"""${admin_nav_card(Vector(
             "Performance summary" -> "/web/system/performance",
             "Metrics" -> "/web/system/admin/observability/metrics",
             "All diagnostics" -> "/web/system/admin/observability/diagnostics",
             "System admin" -> "/web/system/admin"
           ))}
           |<div class="row g-3 mb-3">${scopeCards}</div>
           |${admin_card("Diagnostic Payload Externalization", payload_externalization_status(config, runtime.operationMode))}
           |${admin_card(
             "Safety policy",
             """<p class="mb-2">Diagnostic records are grouped by structured diagnostic key. Message text is evidence only and is not used for grouping.</p>
               |<p class="mb-0">Large payloads are summarized or linked. Payload bytes are resolved only through the system-admin payload route.</p>""".stripMargin
           )}""".stripMargin
    )
  }

  protected def observability_metrics_page(subsystem: Subsystem): String = {
    val snapshot = RuntimeDashboardMetrics.runtimeMetricsSnapshot(subsystem.entityAccessMetrics)
    val scopeCards = snapshot.catalog.map { scope =>
      val points = snapshot.points.filter(_.scope == scope.scope)
      admin_card(
        scope.label,
        s"""<p class="mb-2">${escape(scope.description)}</p>
           |<p><span class="badge text-bg-secondary">${points.map(_.count).sum}</span> event(s).</p>
           |<p class="text-secondary mb-0">Labels: ${escape(scope.labelKeys.mkString(", "))}</p>""".stripMargin,
        Some(s"metrics-${scope.scope.replace('.', '-')}")
      )
    }.mkString("\n")
    val metricTables = snapshot.catalog.map { scope =>
      val points = snapshot.points.filter(_.scope == scope.scope)
      admin_card(
        scope.label,
        metrics_points_table(points),
        Some(s"metrics-table-${scope.scope.replace('.', '-')}")
      )
    }.mkString("\n")
    simple_page(
      title = "Observability Metrics",
      subtitle = "Low-cardinality runtime metrics for dashboard and admin use",
      body =
        s"""${admin_nav_card(Vector(
             "Observability home" -> "/web/system/admin/observability",
             "Performance summary" -> "/web/system/performance",
             "Diagnostics" -> "/web/system/admin/observability/diagnostics"
           ))}
           |<div class="row g-3 mb-3">${scopeCards}</div>
           |${admin_card(
             "Metric label policy",
             """<p class="mb-2">Metric labels are operational grouping hints, not error semantics.</p>
               |<p class="mb-0">High-cardinality identifiers such as paths, entity ids, job ids, payload ids, request parameters, and user/session ids are excluded from default metric labels.</p>""".stripMargin
           )}
           |${metricTables}
           |${manual_raw_details("metrics snapshot", snapshot.toRecord)}""".stripMargin
    )
  }

  protected def observability_diagnostics_page(): String = {
    val scopes = RuntimeDashboardMetrics.diagnosticScopes
    val cards = scopes.map { scope =>
      admin_card(
        scope.label,
        s"""<p><span class="badge text-bg-secondary">${scope.totalCount}</span> diagnostic event(s).</p>
           |${observability_diagnostic_group_table(scope)}""".stripMargin,
        Some(s"observability-${scope.scope}")
      )
    }.mkString("\n")
    simple_page(
      title = "Observability Diagnostics",
      subtitle = "Structured diagnostic groups by scope",
      body =
        s"""${admin_nav_card(Vector(
             "Observability home" -> "/web/system/admin/observability",
             "Performance summary" -> "/web/system/performance"
           ))}
           |${cards}""".stripMargin
    )
  }

  protected def observability_diagnostic_detail_page(
    group: RuntimeDashboardMetrics.DiagnosticGroup
  ): String = {
    val record = group.latestRecord
    val structured = record.map(observability_structured_fields).getOrElse(admin_empty_state("No structured diagnostic record has been captured for this key."))
    val examples = observability_examples_table(group.recentExamples)
    val previous = record.map(previous_chain_html).getOrElse("")
    val payloads = record.map(payload_references_html).getOrElse("")
    val raw = record.map(manual_raw_details("diagnostic record", _)).getOrElse("")
    simple_page(
      title = s"${group.label} Diagnostic ${group.diagnosticKey}",
      subtitle = "Structured diagnostic facts, source-error chain, and payload references",
      body =
        s"""${admin_nav_card(Vector(
             "Observability diagnostics" -> "/web/system/admin/observability/diagnostics",
             "Observability home" -> "/web/system/admin/observability",
             "Performance summary" -> "/web/system/performance"
           ))}
           |${admin_card(
             "Summary",
             field_table(Vector(
               "Scope" -> group.label,
               "Diagnostic key" -> group.diagnosticKey,
               "Recent count" -> group.count.toString
             ))
           )}
           |${admin_card("Structured fields", structured)}
           |${admin_card("Recent examples", examples)}
           |${admin_card("Source-error trace", previous)}
           |${admin_card("Payload references", payloads)}
           |${raw}""".stripMargin
    )
  }

  protected def observability_scope_card(
    scope: RuntimeDashboardMetrics.DiagnosticScope
  ): String =
    s"""<div class="col-12 col-md-6 col-xl-3">
       |  ${admin_card(
             scope.label,
             s"""<p><span class="badge text-bg-secondary">${scope.totalCount}</span> diagnostic event(s).</p>
                |${admin_action_row(Vector("Open diagnostics" -> s"/web/system/admin/observability/diagnostics#observability-${scope.scope}"), primary = false)}""".stripMargin,
             Some(s"observability-card-${scope.scope}")
           )}
       |</div>""".stripMargin

  protected def metrics_points_table(
    points: Vector[RuntimeMetricPoint]
  ): String =
    if (points.isEmpty)
      admin_empty_state("No metrics have been recorded.")
    else {
      val rows = points.sortBy(x => (x.name, x.labels.toVector.sortBy(_._1).mkString("|"))).map { point =>
        val labels = metrics_labels_html(point)
        val duration = point.durationAvgMillis
          .map(avg => s"avg ${avg} ms, min ${point.durationMinMillis.getOrElse(0L)} ms, max ${point.durationMaxMillis.getOrElse(0L)} ms")
          .getOrElse("")
        s"""<tr>
           |  <td><code>${escape(point.name)}</code></td>
           |  <td>${labels}</td>
           |  <td>${point.count}</td>
           |  <td>${point.errorCount}</td>
           |  <td>${escape(duration)}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Metric</th><th>Labels</th><th>Count</th><th>Errors</th><th>Duration</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def metrics_labels_html(
    point: RuntimeMetricPoint
  ): String =
    if (point.labels.isEmpty)
      ""
    else
      point.labels.toVector.sortBy(_._1).map {
        case ("diagnostic_key", value) =>
          metric_diagnostic_link(point.scope, value)
        case (key, value) =>
          s"""<span class="badge text-bg-light border me-1">${escape(key)}=${escape(value)}</span>"""
      }.mkString

  protected def metric_diagnostic_link(
    metricScope: String,
    diagnosticKey: String
  ): String = {
    val diagnosticScope = metricScope match {
      case "authorization.decision" => Some("authorization")
      case "validation" => Some("validation")
      case "operation-request-validation" => Some("operation-request-validation")
      case "blob.operation" => Some("blob")
      case _ => None
    }
    diagnosticScope match {
      case Some(scope) =>
        val href = s"/web/system/admin/observability/diagnostics/${escape_path_segment(scope)}/${escape_path_segment(diagnosticKey)}"
        s"""<a class="badge text-bg-light border me-1" href="${escape(href)}">diagnostic_key=${escape(diagnosticKey)}</a>"""
      case None =>
        s"""<span class="badge text-bg-light border me-1">diagnostic_key=${escape(diagnosticKey)}</span>"""
    }
  }

  protected def payload_externalization_status(
    config: DiagnosticPayloadExternalizationConfig,
    mode: OperationMode
  ): String = {
    val rows = Vector(
      "enabled" -> config.enabled.toString,
      "destination" -> config.normalizedDestination(mode).getOrElse("disabled"),
      "localRoot" -> config.localRoot.toString,
      "thresholdBytes" -> config.thresholdBytes.toString,
      "payloadTargets" -> config.payloadTargets.toVector.sorted.mkString(", "),
      "operationExact" -> config.operationExact.toVector.sorted.mkString(", "),
      "operationContains" -> config.operationContains.mkString(", "),
      "allowRequestOverride" -> config.allowRequestOverride.toString,
      "unsafeOpaquePayloads" -> config.unsafeOpaquePayloads.toString,
      "retentionDays" -> config.retentionDays.map(_.toString).getOrElse("not configured"),
      "validationError" -> config.validationError.getOrElse("")
    )
    field_table(rows)
  }

  protected def observability_diagnostic_group_table(
    scope: RuntimeDashboardMetrics.DiagnosticScope
  ): String =
    if (scope.groups.isEmpty)
      admin_empty_state("No diagnostics have been recorded.")
    else {
      val rows = scope.groups.map { group =>
        val href = s"/web/system/admin/observability/diagnostics/${escape_path_segment(group.scope)}/${escape_path_segment(group.diagnosticKey)}"
        val latest = group.latestRecord.map(diagnostic_record_compact).getOrElse("")
        s"""<tr>
           |  <td><a href="${escape(href)}"><code>${escape(group.diagnosticKey)}</code></a></td>
           |  <td>${group.count}</td>
           |  <td>${latest}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Diagnostic</th><th>Count</th><th>Latest structured fields</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def observability_structured_fields(record: Record): String = {
    val keys = Vector(
      "diagnosticKey",
      "taxonomy",
      "taxonomyCategory",
      "taxonomySymptom",
      "causeKind",
      "interpretation",
      "userAction",
      "responsibility",
      "webStatus",
      "statusText",
      "detailCode",
      "appCode",
      "appStatus",
      "parameter",
      "fieldPath",
      "policy",
      "capability",
      "permission",
      "guard",
      "relation"
    )
    val rows = keys.flatMap(key => record_string(record, key).filter(_.nonEmpty).map(key -> _))
    if (rows.isEmpty)
      admin_empty_state("No structured diagnostic fields are available.")
    else
      field_table(rows)
  }

  protected def diagnostic_record_compact(record: Record): String = {
    val xs = Vector(
      record_string(record, "webStatus"),
      record_string(record, "statusText"),
      record_string(record, "detailCode").map(x => s"detailCode $x"),
      record_string(record, "taxonomy"),
      record_string(record, "causeKind").map(x => s"cause $x")
    ).flatten.filter(_.nonEmpty)
    xs.map(x => s"<span class=\"badge text-bg-light border me-1\">${escape(x)}</span>").mkString
  }

  protected def observability_examples_table(
    examples: Vector[RuntimeDashboardMetrics.DiagnosticExample]
  ): String =
    if (examples.isEmpty)
      admin_empty_state("No recent examples are available.")
    else {
      val rows = examples.map { example =>
        s"""<tr>
           |  <td>${escape(instant_text(example.observedAt))}</td>
           |  <td>${escape(example.operation.getOrElse(""))}</td>
           |  <td>${escape(example.kind.getOrElse(""))}</td>
           |  <td>${escape(example.sourceMode.getOrElse(""))}</td>
           |  <td>${escape(example.backend.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Observed at</th><th>Operation</th><th>Kind</th><th>Source</th><th>Backend</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def previous_chain_html(record: Record): String = {
    val previous = record_seq(record.getAny("previous"))
    if (previous.isEmpty)
      admin_empty_state("No previous Conclusion chain is attached.")
    else
      previous.zipWithIndex.map {
        case (entry, index) =>
          s"""<details class="mb-2">
             |  <summary>Source error ${index + 1}</summary>
             |  <div class="mt-2">${observability_structured_fields(entry)}</div>
             |  ${manual_raw_details(s"source error ${index + 1}", entry)}
             |</details>""".stripMargin
      }.mkString("\n")
  }

  protected def payload_references_html(record: Record): String = {
    val refs = payload_references(record)
    if (refs.isEmpty)
      admin_empty_state("No payload references are attached.")
    else {
      val rows = refs.map {
        case (path, href) =>
          val value =
            if (href.startsWith("/web/system/admin/observability/payloads/"))
              s"""<a href="${escape(href)}"><code>${escape(href)}</code></a>"""
            else
              s"<code>${escape(href)}</code>"
          s"<tr><th>${escape(path)}</th><td>${value}</td></tr>"
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
  }

  protected def payload_references(record: Record): Vector[(String, String)] =
    payload_references_from_any("diagnostic", record).distinct

  protected def payload_references_from_any(
    path: String,
    value: Any
  ): Vector[(String, String)] =
    value match {
      case Some(x) => payload_references_from_any(path, x)
      case r: Record =>
        val direct = diagnostic_payload_reference(r).toVector.flatMap { ref =>
          ref.href.orElse(ref.url).orElse(ref.path).orElse(ref.ref).map(path -> _)
        }
        direct ++ r.asMap.toVector.flatMap {
          case (key, v) => payload_references_from_any(s"$path.${key.toString}", v)
        }
      case xs: Seq[?] =>
        xs.toVector.zipWithIndex.flatMap { case (x, i) => payload_references_from_any(s"$path[$i]", x) }
      case xs: Array[?] =>
        xs.toVector.zipWithIndex.flatMap { case (x, i) => payload_references_from_any(s"$path[$i]", x) }
      case _ =>
        Vector.empty
    }

  protected def diagnostic_payload_reference(record: Record): Option[DiagnosticPayloadReference] = {
    val keys = record.asMap.keySet.map(_.toString).toSet
    val hasPayloadKey = keys.exists(key => key.startsWith("payload_") || key.startsWith("external_")) ||
      keys.contains("externalization_status") ||
      keys.contains("externalization_reason")
    val hasReferenceShape =
      keys.exists(Set("href", "url", "path", "ref")) &&
        keys.exists(Set("storage", "content_type", "contentType", "size_bytes"))
    if (hasPayloadKey || hasReferenceShape)
      DiagnosticPayloadReference.fromRecord(record)
    else
      None
  }

  protected def record_string(
    record: Record,
    key: String
  ): Option[String] =
    record.getAny(key).map(display_value).map(_.trim).filter(_.nonEmpty)

  protected def jobs_json(
    running: Int,
    queued: Int,
    completed: Int,
    failed: Int
  ): String = {
    val total = running + queued + completed + failed
    val summary = RuntimeDashboardMetrics.CountSummary(
      RuntimeDashboardMetrics.CountWindow(total, failed),
      RuntimeDashboardMetrics.CountWindow(total, failed),
      RuntimeDashboardMetrics.CountWindow(total, failed),
      RuntimeDashboardMetrics.CountWindow(running + queued, failed)
    )
    s"""{"running":${running},"queued":${queued},"completed":${completed},"failed":${failed},"total":${total},"summary":${summary_json(summary)}}"""
  }

  protected def summary_table(summary: RuntimeDashboardMetrics.CountSummary): String =
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <thead><tr><th>Window</th><th>Count</th><th>Errors</th></tr></thead>
       |  <tbody>
       |    ${summary_row("Total", summary.cumulative)}
       |    ${summary_row("1 day", summary.day)}
       |    ${summary_row("1 hour", summary.hour)}
       |    ${summary_row("1 minute", summary.minute)}
       |  </tbody>
       |</table></div>""".stripMargin

  protected def summary_row(
    label: String,
    window: RuntimeDashboardMetrics.CountWindow
  ): String =
    s"<tr><td>${escape(label)}</td><td>${window.total}</td><td>${window.errors}</td></tr>"

  protected def diagnostics_table(
    counts: Map[String, Long],
    records: Map[String, Record] = Map.empty,
    scope: Option[String] = None
  ): String =
    if (counts.isEmpty)
      """<p class="text-secondary">No diagnostics have been recorded.</p>"""
    else {
      val rows = counts.toVector.sortBy(_._1).map {
        case (kind, count) =>
          val diagnosticLabel = scope.map { s =>
            val href = s"/web/system/admin/observability/diagnostics/${escape_path_segment(s)}/${escape_path_segment(kind)}"
            s"""<a href="${escape(href)}"><code>${escape(kind)}</code></a>"""
          }.getOrElse(s"<code>${escape(kind)}</code>")
          val detail = records.get(kind).map { record =>
            val status = record.getInt("webStatus").map(_.toString).getOrElse("")
            val detailCode = record.getAny("detailCode").map(_.toString).getOrElse("")
            val taxonomy = record.getString("taxonomy").getOrElse("")
            val cause = record.getString("causeKind").getOrElse("")
            Vector(status, detailCode, taxonomy, cause).filter(_.nonEmpty).map(escape).mkString("<br>")
          }.getOrElse("")
          s"<tr><td>${diagnosticLabel}</td><td>${count}</td><td>${detail}</td></tr>"
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Diagnostic</th><th>Count</th><th>Structured detail</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def jobs_table(jobs: (Int, Int, Int, Int)): String = {
    val (running, queued, completed, failed) = jobs
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <thead><tr><th>Status</th><th>Count</th></tr></thead>
       |  <tbody>
       |    <tr><td>Total</td><td>${running + queued + completed + failed}</td></tr>
       |    <tr><td>Running</td><td>${running}</td></tr>
       |    <tr><td>Queued</td><td>${queued}</td></tr>
       |    <tr><td>Completed</td><td>${completed}</td></tr>
       |    <tr><td>Failed</td><td>${failed}</td></tr>
       |  </tbody>
       |</table></div>""".stripMargin
  }

  protected def latency_table(recent: Vector[RuntimeDashboardMetrics.RequestEntry]): String = {
    val count = recent.size
    val average =
      if (recent.isEmpty) 0L
      else recent.map(_.elapsedMillis).sum / recent.size
    val max =
      if (recent.isEmpty) 0L
      else recent.map(_.elapsedMillis).max
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <thead><tr><th>Metric</th><th>Value</th></tr></thead>
       |  <tbody>
       |    <tr><td>Recent samples</td><td>${count}</td></tr>
       |    <tr><td>Average elapsed</td><td>${average} ms</td></tr>
       |    <tr><td>Max elapsed</td><td>${max} ms</td></tr>
       |  </tbody>
       |</table></div>""".stripMargin
  }

  protected def recent_requests_table(recent: Vector[RuntimeDashboardMetrics.RequestEntry]): String =
    request_table(recent.reverse, "No recent requests")

  protected def recent_errors_table(recent: Vector[RuntimeDashboardMetrics.RequestEntry]): String =
    request_table(recent.filter(_.status >= 400).reverse, "No recent errors")

  protected def request_table(
    requests: Vector[RuntimeDashboardMetrics.RequestEntry],
    emptyMessage: String
  ): String =
    if (requests.isEmpty) {
      web_empty_state(emptyMessage)
    } else {
      val rows = requests.map { x =>
        s"""<tr><td>${escape(instant_text(x.observedAt))}</td><td>${escape(x.method)}</td><td><code>${escape(x.path)}</code></td><td>${x.status}</td><td>${x.elapsedMillis} ms</td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Observed at</th><th>Method</th><th>Path</th><th>Status</th><th>Elapsed</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def instant_text(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis).toString

  protected def component_json(component: Component): String = {
    val services = component.protocol.services.services.map { service =>
      val operations = service.operations.operations.toVector.map { operation =>
        val path = NamingConventions.toNormalizedPath(component.name, service.name, operation.name)
        s"""{"name":"${json(operation.name)}","path":"${json(path)}"}"""
      }.mkString("[", ",", "]")
      s"""{"name":"${json(service.name)}","operationCount":${service.operations.operations.length},"operations":${operations}}"""
    }.mkString("[", ",", "]")
    val operationCount = component.protocol.services.services.map(_.operations.operations.length).sum
    s"""{"name":"${json(component.name)}","version":${component.artifactMetadata.map(_.version).map(v => "\"" + json(v) + "\"").getOrElse("null")},"serviceCount":${component.protocol.services.services.size},"operationCount":${operationCount},"services":${services}}"""
  }

  protected def component_reference_list(components: Vector[Component]): String =
    s"""<div class="list-group">
       |${components.map { component =>
         val componentPath = NamingConventions.toNormalizedSegment(component.name)
         s"""  <div class="list-group-item">
            |    <div class="d-flex flex-wrap justify-content-between gap-2 align-items-center">
            |      <strong>${escape(component.name)}</strong>
            |      ${admin_action_row(Vector(
              "Dashboard" -> s"/web/${componentPath}/dashboard",
              "Admin" -> s"/web/${componentPath}/admin",
              "Forms" -> s"/form/${componentPath}"
            ), primary = false)}
            |    </div>
            |  </div>""".stripMargin
       }.mkString("\n")}
       |</div>""".stripMargin

  protected def component_form_list(components: Vector[Component]): String =
    s"""<div class="list-group">
       |${components.map { component =>
         val componentPath = NamingConventions.toNormalizedSegment(component.name)
         s"""  <a class="list-group-item list-group-item-action d-flex flex-wrap justify-content-between gap-2 align-items-center" href="/form/${componentPath}">
            |    <strong>${escape(component.name)}</strong>
            |    <span class="badge text-bg-secondary">Operation forms</span>
            |  </a>""".stripMargin
       }.mkString("\n")}
       |</div>""".stripMargin

  protected def job_metrics(subsystem: Subsystem): (Int, Int, Int, Int) =
    subsystem.jobEngine.metrics.map(x => (x.running, x.queued, x.completed, x.failed)).getOrElse((0, 0, 0, 0))

  protected def job_metrics(component: Component): (Int, Int, Int, Int) =
    component.jobEngine.metrics.map(x => (x.running, x.queued, x.completed, x.failed)).getOrElse((0, 0, 0, 0))

  protected def assembly_warning_count(subsystem: Subsystem): Int =
    assembly_report(subsystem).warnings.size

  protected def assembly_report(
    subsystem: Subsystem
  ): org.goldenport.cncf.assembly.AssemblyReport =
    org.goldenport.cncf.context.GlobalRuntimeContext.current
      .orElse(scala.util.Try(subsystem.globalRuntimeContext).toOption)
      .map(_.assemblyReport)
      .getOrElse(new org.goldenport.cncf.assembly.AssemblyReport)
}
