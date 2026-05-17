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
trait StaticFormAppRendererFormResultPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererSystemAdminPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  protected val call_tree_js_asset = "/web/assets/textus-calltree.js"


  def renderFormResult(
    properties: FormResultProperties
  ): Page =
    renderFormResult(properties, None)

  def renderFormResult(
    properties: FormResultProperties,
    template: String
  ): Page =
    renderFormResult(properties, Some(template))

  def renderFormResult(
    properties: FormResultProperties,
    template: Option[String]
  ): Page = {
    val effectiveTemplate = template.getOrElse(
      s"""<article class="card admin-card">
         |  <div class="card-body">
         |  <h2 class="card-title">$${operation.label} Result</h2>
         |  <p>Content-Type $${result.contentType}</p>
         |  <textus:job-ticket></textus:job-ticket>
         |  <textus-error-panel source="error"></textus-error-panel>
         |  <textus-result-view source="result.body"></textus-result-view>
         |  <textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus-result-table>
         |  <textus-form-link href="crud.success.href" label="Back to detail"></textus-form-link>
         |  <textus-property-list source="result"></textus-property-list>
         |  <h3>Submitted Values</h3>
         |  <textus-property-list source="form"></textus-property-list>
         |  <div class="admin-action-row d-flex flex-wrap gap-2 mt-3">
         |    <a class="btn btn-primary" href="/form/${escape(NamingConventions.toNormalizedSegment(properties.componentName))}/${escape(NamingConventions.toNormalizedSegment(properties.serviceName))}/${escape(NamingConventions.toNormalizedSegment(properties.operationName))}">Run again</a>
         |    <a class="btn btn-outline-secondary" href="/form/${escape(NamingConventions.toNormalizedSegment(properties.componentName))}">Operations</a>
         |  </div>
         |  </div>
         |</article>""".stripMargin
    )
    renderFormResultTemplate(properties, effectiveTemplate)
  }

  def renderFormResultTemplate(
    properties: FormResultProperties,
    template: String
  ): Page = {
    val pageProperties = properties.nextPageProperties
    val rendered = render_template(
      template,
      pageProperties,
      properties.tableColumns,
      properties.defaultTableView
    )
    val withJobPanel = append_default_job_panel(rendered, pageProperties)
    val withDebug = append_execution_debug_panel(withJobPanel, properties, pageProperties)
    val assetCompletion = execution_debug_asset_completion(properties, pageProperties)
    if (is_html_document(template))
      Page(complete_widget_assets(template, withDebug, assetCompletion))
    else
      Page(StaticFormAppLayout.completeDeclaredAssets(
        simple_page(
          title = s"${escape(properties.operationLabel)} Result",
          subtitle = s"HTTP ${properties.status}",
          body = withDebug
        ),
        assetCompletion
      ))
  }

  def renderErrorTemplate(
    app: Option[String],
    status: Int,
    message: String,
    path: String,
    template: String
  ): Page =
    renderErrorTemplate(app, status, message, path, None, template)

  def renderErrorTemplate(
    app: Option[String],
    status: Int,
    message: String,
    path: String,
    error: Option[StructuredHttpError],
    template: String
  ): Page = {
    val appName = app.getOrElse("system")
    val panel = error.map(renderStructuredErrorPanel).getOrElse("")
    val errorValues = error.map(structured_error_values).getOrElse(Map.empty)
    val properties = FormPageProperties(
      appName,
      "web",
      "error",
      Map(
        "app" -> appName,
        "component" -> appName,
        "error.status" -> status.toString,
        "error.message" -> message,
        "error.body" -> message,
        "error.path" -> path,
        "error.debugPanel" -> panel,
        "result.status" -> status.toString,
        "result.ok" -> "false",
        "result.body" -> message
      ) ++ errorValues
    )
    val rendered = render_template(template, properties, Map.empty)
    val body = append_structured_error_panel(rendered, panel)
    if (is_html_document(template))
      Page(body)
    else
      Page(simple_page(
        title = s"HTTP ${status}",
        subtitle = escape(path),
        body = body
      ))
  }

  def renderStructuredErrorPage(
    app: Option[String],
    error: StructuredHttpError
  ): Page = {
    val detailcode = error.detailCode.map(x =>
      s"""  <p class="mb-0"><strong>Detail code:</strong> <code>${x}</code></p>"""
    ).getOrElse("")
    val appstatus = error.appStatus.map(x =>
      s"""  <p class="mb-0"><strong>Application status:</strong> <code>${escape(x)}</code></p>"""
    ).getOrElse("")
    val body =
      s"""<section class="alert alert-danger" role="alert">
         |  <h2 class="h5">Request failed</h2>
         |  <p class="mb-2">${escape(error.message)}</p>
         |  <p class="mb-1"><strong>Status:</strong> <code>${error.status}</code></p>
         |  <p class="mb-1"><strong>Status text:</strong> <code>${escape(error.statusText)}</code></p>
         |${detailcode}
         |${appstatus}
         |</section>
         |${renderStructuredErrorPanel(error)}""".stripMargin
    Page(simple_page(
      title = s"HTTP ${error.status}",
      subtitle = escape(error.path),
      body = body
    ))
  }

  def renderStructuredErrorPanel(
    error: StructuredHttpError
  ): String =
    if (!error.debugEnabled)
      ""
    else
      s"""<section class="mt-4 structured-error-debug">
         |  <details>
         |    <summary>Debug error details</summary>
         |    <p class="text-secondary mb-2">Structured error projection for non-production troubleshooting.</p>
         |    <pre class="bg-light border rounded p-3"><code>${escape(error.diagnosticYaml)}</code></pre>
         |  </details>
         |</section>""".stripMargin

  protected def structured_error_values(
    error: StructuredHttpError
  ): Map[String, String] =
    Map(
      "error.status" -> error.status.toString,
      "error.statusText" -> error.statusText,
      "error.detailCode" -> error.detailCode.map(_.toString).getOrElse(""),
      "error.appCode" -> error.appCode.map(_.toString).getOrElse(""),
      "error.appStatus" -> error.appStatus.getOrElse(""),
      "error.mode" -> error.operationMode.name,
      "error.method" -> error.method,
      "error.debugYaml" -> (if (error.debugEnabled) error.diagnosticYaml else "")
    )

  protected def append_structured_error_panel(
    html: String,
    panel: String
  ): String =
    if (panel.isEmpty || html.contains("structured-error-debug"))
      html
    else {
      val marker = "</body>"
      val index = html.toLowerCase(java.util.Locale.ROOT).lastIndexOf(marker)
      if (index >= 0)
        html.substring(0, index) + panel + html.substring(index)
      else
        html + panel
    }

  protected def append_execution_debug_panel(
    html: String,
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): String = {
    val panel = execution_debug_panel(properties, pageProperties)
    if (panel.isEmpty || html.contains("textus-execution-debug-panel"))
      html
    else {
      val marker = "</body>"
      val index = html.toLowerCase(java.util.Locale.ROOT).lastIndexOf(marker)
      if (index >= 0)
        html.substring(0, index) + panel + html.substring(index)
      else
        html + panel
    }
  }

  protected def append_default_job_panel(
    html: String,
    pageProperties: FormPageProperties
  ): String =
    if (pageProperties.value("result.job.id").isEmpty ||
        html.contains("textus-job-ticket") ||
        html.contains("textus-job-panel") ||
        html.contains("textus-job-actions"))
      html
    else
      html + render_job_panel(Map("actions" -> "result,await,jobs"), pageProperties)

  protected def execution_debug_panel(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): String =
    if (!is_development_operation_mode(properties.operationMode) || !execution_debug_panel_enabled(properties, pageProperties))
      ""
    else {
      val metadata = properties.executionMetadata
      val calltreeHtml = metadata.inlineCallTree.map(debug_calltree_html).getOrElse("")
      val jobid = metadata.responseJobId.orElse(metadata.debugJobId)
      val executionJobId = metadata.executionJobId.orElse(jobid)
      val sagaId = metadata.sagaId
      val taskId = metadata.executionTaskId
      val joblinks = jobid.map { id =>
        val appHref = pageProperties.value("result.job.href")
        val systemHref = s"/web/system/admin/jobs/${escape_path_segment(id)}"
        val app = if (appHref.nonEmpty) s"""<a class="btn btn-sm btn-outline-primary" href="${escape(appHref)}">Application job</a>""" else ""
        s"""<div class="d-flex flex-wrap gap-2 mt-2">${app}<a class="btn btn-sm btn-outline-secondary" href="${escape(systemHref)}">System debug job</a></div>"""
      }.getOrElse("")
      val arguments = debug_operation_arguments(pageProperties, properties.fieldConfidentiality)
      val argumentsHtml =
        if (arguments.nonEmpty)
          s"""<pre class="bg-light border rounded p-3"><code>${escape(debug_record_pretty(Record.dataAuto(arguments.toVector.sortBy(_._1)*)))}</code></pre>"""
        else
          """<p class="text-secondary mb-0">No operation arguments were captured for this response.</p>"""
      val effectiveCalltreeHtml =
        if (calltreeHtml.nonEmpty)
          calltreeHtml
        else
          """<p class="text-secondary mb-0">CallTree was not captured for this response.</p>"""
      val debugVariant =
        if (properties.status >= 200 && properties.status < 400)
          ("success", "text-success-emphasis")
        else
          ("danger", "text-danger-emphasis")
      val summary =
        s"""<dl class="row mb-3">
           |  <dt class="col-sm-3">Operation</dt><dd class="col-sm-9"><code>${escape(properties.operationLabel)}</code></dd>
           |  <dt class="col-sm-3">HTTP status</dt><dd class="col-sm-9">${properties.status}</dd>
           |  <dt class="col-sm-3">Mode</dt><dd class="col-sm-9">${escape(properties.operationMode.name)}</dd>
           |  <dt class="col-sm-3">Saga</dt><dd class="col-sm-9">${debug_context_value(sagaId)}</dd>
           |  <dt class="col-sm-3">Job</dt><dd class="col-sm-9">${debug_context_value(executionJobId)}</dd>
           |  <dt class="col-sm-3">Task</dt><dd class="col-sm-9">${debug_context_value(taskId)}</dd>
           |</dl>""".stripMargin
      s"""<section class="container my-4 textus-execution-debug-panel">
         |  <details class="border border-${debugVariant._1} border-2 border-start border-start-4 rounded bg-${debugVariant._1}-subtle shadow-sm">
         |    <summary class="p-3 fw-semibold ${debugVariant._2}">Development execution diagnostics</summary>
         |    <div class="border-top border-${debugVariant._1} p-3 bg-${debugVariant._1}-subtle">
         |      ${summary}
         |      ${joblinks}
         |      <h3 class="h6 mt-3">Operation arguments</h3>
         |      ${argumentsHtml}
         |      <h3 class="h6 mt-3">Result body</h3>
         |      <pre class="bg-light border rounded p-3"><code>${escape(debug_body_pretty(properties.body, properties.fieldConfidentiality).take(renderer_config.debugBodyPreviewChars))}</code></pre>
         |      <h3 class="h6 mt-3">CallTree</h3>
         |      ${effectiveCalltreeHtml}
         |    </div>
         |  </details>
         |</section>""".stripMargin
    }

  protected def debug_context_value(
    value: Option[String]
  ): String =
    value.filter(_.trim.nonEmpty)
      .map(x => s"""<code>${escape(x)}</code>""")
      .getOrElse("""<span class="text-secondary">none</span>""")

  protected def is_development_operation_mode(
    mode: OperationMode
  ): Boolean =
    mode == OperationMode.Develop || mode == OperationMode.Test

  protected def execution_debug_panel_enabled(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): Boolean =
    pageProperties.value("textus.debug.executionPanel").equalsIgnoreCase("true") ||
      !execution_metadata_empty(properties.executionMetadata)

  protected def execution_debug_asset_completion(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): StaticFormAppLayout.AssetCompletionOptions = {
    val base = properties.assetCompletion
    if (execution_debug_calltree_asset_required(properties, pageProperties) &&
        !base.declaredJs.contains(call_tree_js_asset))
      base.copy(declaredJs = base.declaredJs :+ call_tree_js_asset)
    else
      base
  }

  protected def execution_debug_calltree_asset_required(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): Boolean =
    is_development_operation_mode(properties.operationMode) &&
      execution_debug_panel_enabled(properties, pageProperties) &&
      properties.executionMetadata.inlineCallTree.nonEmpty

  protected def execution_metadata_empty(
    metadata: RuntimeContext.ExecutionMetadata
  ): Boolean =
    metadata.responseJobId.isEmpty &&
      metadata.debugJobId.isEmpty &&
      metadata.inlineCallTree.isEmpty

  protected def debug_body_pretty(
    body: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String = {
    val trimmed = body.trim
    if (trimmed.isEmpty)
      body
    else
      io.circe.parser.parse(trimmed).toOption
      .map(json => debug_redact_json(debug_compact_result_debug_json(json), confidentiality).spaces2)
      .getOrElse(debug_redact_text(body, confidentiality))
  }

  protected def debug_compact_result_debug_json(
    json: Json
  ): Json =
    json.asObject.flatMap { obj =>
      obj("debug").flatMap(_.asObject).map { debug =>
        val compactDebug =
          if (debug("calltree").nonEmpty)
            Json.fromJsonObject(JsonObject.fromIterable(
              debug.toIterable.map {
                case ("calltree", _) => "calltree" -> Json.fromString("[shown in CallTree panel]")
                case x => x
              }
            ))
          else
            Json.fromJsonObject(debug)
        Json.fromJsonObject(JsonObject.fromIterable(
          obj.toIterable.map {
            case ("debug", _) => "debug" -> compactDebug
            case x => x
          }
        ))
      }
    }.getOrElse(json)

  protected def debug_operation_arguments(
    pageProperties: FormPageProperties,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Map[String, String] = {
    val formvalues = pageProperties.values.toVector.collect {
      case (key, value) if key.startsWith("form.") => key.stripPrefix("form.") -> value
    }.toMap
    val rawvalues =
      if (formvalues.nonEmpty)
        formvalues
      else
        pageProperties.values.filterNot { case (key, _) =>
          key.contains(".") ||
            key == "component" ||
            key == "service" ||
            key == "operation"
        }
    visible_form_values(rawvalues).filterNot { case (key, _) =>
      key == "fields" ||
        key == "component" ||
        key == "service" ||
        key == "operation" ||
        key == "textus.debug.executionPanel" ||
        key.startsWith("textus.debug.")
    }.map { case (key, value) => key -> debug_display_value(key, value, confidentiality) }
  }

  protected def debug_record_pretty(
    record: Record
  ): String =
    manual_raw_json(debug_redact_record(record))
      .map(_.spaces2)
      .getOrElse(debug_redact_record(record).show)

  protected def debug_display_value(
    key: String,
    value: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String =
    if (is_sensitive_debug_key(key, confidentiality)) "[redacted]" else value

  protected def debug_redact_record(
    record: Record,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record =
    Record.dataAuto(
      record.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) => key -> debug_redact_value(key, value, confidentiality) }*
    )

  protected def debug_redact_value(
    key: String,
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    if (is_sensitive_debug_key(key, confidentiality))
      "[redacted]"
    else
      value match {
        case r: Record =>
          debug_redact_record(r, confidentiality)
        case xs: Seq[?] =>
          xs.map(debug_redact_nested_value(_, confidentiality))
        case xs: Array[?] =>
          xs.toVector.map(debug_redact_nested_value(_, confidentiality))
        case m: scala.collection.Map[?, ?] =>
          m.toVector.map {
            case (k, v) =>
              val childKey = Option(k).map(_.toString).getOrElse("")
              childKey -> debug_redact_value(childKey, v, confidentiality)
          }.toMap
        case _ =>
          value
      }

  protected def debug_redact_nested_value(
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    value match {
      case r: Record =>
        debug_redact_record(r, confidentiality)
      case xs: Seq[?] =>
        xs.map(debug_redact_nested_value(_, confidentiality))
      case xs: Array[?] =>
        xs.toVector.map(debug_redact_nested_value(_, confidentiality))
      case m: scala.collection.Map[?, ?] =>
        m.toVector.map {
          case (k, v) =>
            val childKey = Option(k).map(_.toString).getOrElse("")
            childKey -> debug_redact_value(childKey, v, confidentiality)
        }.toMap
      case _ =>
        value
    }

  protected def debug_redact_json(
    json: Json,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Json =
    json.arrayOrObject(
      json,
      xs => Json.arr(xs.map(debug_redact_json(_, confidentiality))*),
      obj => Json.fromJsonObject(JsonObject.fromIterable(
        obj.toIterable.map {
          case (key, value) =>
            key -> (if (is_sensitive_debug_key(key, confidentiality)) Json.fromString("[redacted]") else debug_redact_json(value, confidentiality))
        }
      ))
    )

  protected def is_sensitive_debug_key(
    key: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Boolean = {
    confidentiality.get(key)
      .orElse(confidentiality.find { case (k, _) => NamingConventions.equivalentByNormalized(k, key) }.map(_._2))
      .exists(_.shouldRedactByDefault) ||
    {
    val normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")
    normalized.contains("password") ||
      normalized.contains("passwd") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie") ||
      normalized.contains("credential") ||
      normalized.contains("apikey") ||
      normalized.contains("privatekey")
    }
  }

  protected def debug_redact_text(
    value: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String = {
    val metadataSensitive = confidentiality.collect {
      case (key, level) if level.shouldRedactByDefault => java.util.regex.Pattern.quote(key)
    }.mkString("|")
    val sensitiveBase = """password|passwd|secret|token|access[-_]?session[-_]?id|refresh[-_]?session[-_]?id|session[-_]?id|session|authorization|cookie|credential|api[-_]?key|private[-_]?key"""
    val sensitive = if (metadataSensitive.isEmpty) sensitiveBase else s"$sensitiveBase|$metadataSensitive"
    val jsonLike = s"""(?i)("(?:$sensitive)"\\s*:\\s*)"[^"]*"""".r
    val formLike = s"""(?i)(^|[?&\\s,;])($sensitive)(\\s*[=:]\\s*)([^&\\s,;]+)""".r
    val jsonRedacted = jsonLike.replaceAllIn(value, m => s"""${m.group(1)}"[redacted]"""")
    formLike.replaceAllIn(jsonRedacted, m => s"${m.group(1)}${m.group(2)}${m.group(3)}[redacted]")
  }

  protected final case class DebugCallTreeEvent(
    kind: String,
    label: String,
    attributes: Map[String, String],
    startedAtNanos: Long,
    endedAtNanos: Option[Long]
  )

  protected final case class DebugCallTreeNode(
    label: String,
    attributes: Map[String, String],
    enterAttributes: Map[String, String],
    leaveAttributes: Map[String, String],
    startedAtNanos: Long,
    endedAtNanos: Option[Long],
    children: Vector[DebugCallTreeNode],
    observations: Vector[DebugCallTreeObservation] = Vector.empty
  ) {
    def enterDisplayAttributes: Map[String, String] =
      if (enterAttributes.nonEmpty) enterAttributes else attributes
    def leaveDisplayAttributes: Map[String, String] =
      if (leaveAttributes.nonEmpty) leaveAttributes else attributes
  }

  protected final case class DebugCallTreeObservation(
    label: String,
    displayLabel: String,
    kind: String,
    attributes: Map[String, String]
  )

  protected final case class DebugCallTreeLine(
    role: String,
    label: String,
    displayLabel: String,
    kind: String,
    attributes: Map[String, String],
    depth: Int,
    pair: String,
    parentDisplayLabel: Option[String] = None,
    observations: Vector[DebugCallTreeObservation] = Vector.empty
  )

  protected def debug_calltree_html(
    record: Record
  ): String =
    debug_calltree_nodes(record) match {
      case Some(nodes) if nodes.nonEmpty =>
        s"""<div class="textus-calltree-tree" data-textus-calltree>
           |  ${debug_calltree_outline(nodes)}
           |</div>""".stripMargin
      case _ =>
        debug_calltree_legacy_html(record)
    }

  protected def debug_calltree_legacy_html(
    record: Record
  ): String = {
    val label = record.getString("name")
      .orElse(record.getString("label"))
      .getOrElse("CallTree")
    val attrs = record.asMap
      .filterNot { case (key, _) => key == "name" || key == "label" || key == "children" }
      .map { case (key, value) => key -> value.toString }
    val children = record.asMap.get("children") match {
      case Some(xs: Seq[?]) =>
        xs.toVector.map {
          case r: Record =>
            val childLabel = r.getString("name").orElse(r.getString("label")).getOrElse("node")
            val childAttrs = r.asMap.filterNot { case (key, _) => key == "name" || key == "label" || key == "children" }.map { case (key, value) => key -> value.toString }
            DebugCallTreeNode(childLabel, childAttrs, childAttrs, childAttrs, 0L, None, Vector.empty)
          case x =>
            DebugCallTreeNode(x.toString, Map.empty, Map.empty, Map.empty, 0L, None, Vector.empty)
        }
      case _ =>
        Vector.empty
    }
    val node = DebugCallTreeNode(label, attrs, attrs, attrs, 0L, None, children)
    s"""<div class="textus-calltree-tree" data-textus-calltree>
       |  ${debug_calltree_outline(Vector(node))}
       |</div>""".stripMargin
  }

  protected def debug_calltree_nodes(
  record: Record
  ): Option[Vector[DebugCallTreeNode]] = {
    val structured = debug_calltree_structured_nodes(record)
    if (structured.exists(_.nonEmpty))
      return structured
    val events = debug_calltree_events(record)
    if (events.isEmpty)
      None
    else {
      val leaves = events.filter(is_debug_leave_event).groupBy(x => (x.label, x.startedAtNanos))
      val spanNodes = events.zipWithIndex.collect { case (entry, index) if entry.kind == "enter" =>
        val leave = leaves.get((entry.label, entry.startedAtNanos)).flatMap(_.headOption)
        val leaveAttrs = leave.map(_.attributes).getOrElse(Map.empty)
        index -> DebugCallTreeNode(
          entry.label,
          entry.attributes ++ leaveAttrs,
          entry.attributes,
          leaveAttrs,
          entry.startedAtNanos,
          leave.flatMap(_.endedAtNanos).orElse(entry.endedAtNanos),
          Vector.empty
        )
      }
      val markerNodes = events.zipWithIndex.collect {
        case (event, index) if event.kind != "enter" && !is_debug_leave_event(event) =>
          index -> DebugCallTreeNode(
            event.label,
            event.attributes,
            event.attributes,
            Map.empty,
            event.startedAtNanos,
            event.endedAtNanos.orElse(Some(event.startedAtNanos)),
            Vector.empty
          )
      }
      val nodes = spanNodes ++ markerNodes
      val byindex = nodes.toMap
      val parentCandidates = spanNodes.toMap
      val parentByIndex = nodes.map { case (index, node) =>
        index -> parentCandidates.collect {
          case (parentIndex, parent) if parentIndex != index && debug_calltree_contains(parent, node) =>
            val span = parent.endedAtNanos.getOrElse(Long.MaxValue) - parent.startedAtNanos
            (parentIndex, span, parent.startedAtNanos)
        }.toVector.sortBy { case (_, span, start) => (span, -start) }.headOption.map(_._1)
      }.toMap
      def build(index: Int): DebugCallTreeNode = {
        val node = byindex(index)
        val children = parentByIndex.collect { case (childIndex, Some(parentIndex)) if parentIndex == index => childIndex }
          .toVector
          .sortBy(child => byindex(child).startedAtNanos)
          .map(build)
        node.copy(children = children)
      }
      Some(parentByIndex.collect { case (index, None) => index }
        .toVector
        .sortBy(index => byindex(index).startedAtNanos)
        .map(build))
    }
  }

  protected def is_debug_leave_event(
    event: DebugCallTreeEvent
  ): Boolean =
    event.kind == "leave" || event.kind == "exit"

  protected def debug_calltree_structured_nodes(
    record: Record
  ): Option[Vector[DebugCallTreeNode]] =
    record.asMap.get("calltree") match {
      case Some(xs: Seq[?]) =>
        val nodes = xs.toVector.collect { case r: Record => debug_calltree_structured_node(r) }.flatten
        if (nodes.nonEmpty) Some(nodes) else None
      case _ =>
        None
    }

  protected def debug_calltree_structured_node(
    record: Record
  ): Option[DebugCallTreeNode] = {
    val label = record.getString("label").orElse(record.getString("name"))
    label.map { label =>
      val topAttrs = debug_calltree_top_level_attributes(record)
      val legacyAttrs = debug_calltree_attributes(record.asMap.get("attributes"))
      val attrs = (if (legacyAttrs.nonEmpty) legacyAttrs else topAttrs) ++
        record.getString("kind").filter(_.nonEmpty).map("calltree_kind" -> _).toMap ++
        record.getString("display_label").filter(_.nonEmpty).map("display_label" -> _).toMap
      val enterAttrs = debug_calltree_attributes(record.asMap.get("enter_attributes"))
      val leaveAttrs = debug_calltree_attributes(record.asMap.get("leave_attributes"))
      val (children, flowObservations) = debug_calltree_structured_flow(record)
      val observations = flowObservations ++ (record.asMap.get("observations") match {
        case Some(xs: Seq[?]) =>
          xs.toVector.collect { case r: Record => debug_calltree_structured_observation(r) }.flatten
        case _ =>
          Vector.empty
      })
      DebugCallTreeNode(
        label,
        attrs,
        enterAttrs,
        leaveAttrs,
        attrs.get("started_at_nanos").flatMap(to_long_option).getOrElse(0L),
        attrs.get("ended_at_nanos").flatMap(to_long_option),
        children,
        observations
      )
    }
  }

  protected def debug_calltree_structured_flow(
    record: Record
  ): (Vector[DebugCallTreeNode], Vector[DebugCallTreeObservation]) =
    record.asMap.get("flow").orElse(record.asMap.get("children")) match {
      case Some(xs: Seq[?]) =>
        val records = xs.toVector.collect { case r: Record => r }
        val children = records.filterNot(debug_calltree_is_observation_record).flatMap(debug_calltree_structured_node)
        val observations = records.filter(debug_calltree_is_observation_record).flatMap(debug_calltree_structured_observation)
        children -> observations
      case _ =>
        Vector.empty -> Vector.empty
    }

  protected def debug_calltree_is_observation_record(
    record: Record
  ): Boolean = {
    val kind = record.getString("kind").getOrElse("").toLowerCase(java.util.Locale.ROOT)
    val label = record.getString("label").orElse(record.getString("name")).getOrElse("").toLowerCase(java.util.Locale.ROOT)
    kind == "metric" || kind == "observation"
  }

  protected def debug_calltree_structured_observation(
    record: Record
  ): Option[DebugCallTreeObservation] =
    record.getString("label").orElse(record.getString("name")).filterNot(debug_calltree_legacy_io_boundary).map { label =>
      val topAttrs = debug_calltree_top_level_attributes(record)
      val legacyAttrs = debug_calltree_attributes(record.asMap.get("attributes"))
      val attrs = (if (legacyAttrs.nonEmpty) legacyAttrs else topAttrs) ++
        record.getString("kind").filter(_.nonEmpty).map("calltree_kind" -> _).toMap ++
        record.getString("display_label").filter(_.nonEmpty).map("display_label" -> _).toMap
      val node = DebugCallTreeNode(label, attrs, Map.empty, Map.empty, 0L, None, Vector.empty)
      DebugCallTreeObservation(
        label,
        debug_calltree_display_label(node),
        debug_calltree_node_kind(node),
        debug_calltree_display_attributes(node, node.attributes)
      )
    }

  protected def debug_calltree_legacy_io_boundary(
    label: String
  ): Boolean = {
    val lower = label.toLowerCase(java.util.Locale.ROOT)
    lower == "io:input" || lower == "io:output"
  }

  protected def debug_calltree_top_level_attributes(
    record: Record
  ): Map[String, String] =
    record.asMap
      .filterNot { case (key, _) =>
        key == "label" ||
          key == "name" ||
          key == "kind" ||
          key == "display_label" ||
          key == "calltree_kind" ||
          key == "flow" ||
          key == "children" ||
          key == "observations" ||
          key == "attributes" ||
          key == "enter_attributes" ||
          key == "leave_attributes"
      }
      .map { case (key, value) => key -> debug_calltree_value_string(value) }

  protected def debug_calltree_value_string(
    value: Any
  ): String =
    value match {
      case r: Record => manual_raw_json(r).map(_.spaces2).getOrElse(r.show)
      case xs: Seq[?] => manual_raw_json(xs).map(_.spaces2).getOrElse(xs.mkString("[", ", ", "]"))
      case x => Option(x).map(_.toString).getOrElse("")
    }

  protected def debug_calltree_contains(
    parent: DebugCallTreeNode,
    child: DebugCallTreeNode
  ): Boolean = {
    val parentEnd = parent.endedAtNanos.getOrElse(Long.MaxValue)
    val childEnd = child.endedAtNanos.getOrElse(child.startedAtNanos)
    parent.startedAtNanos <= child.startedAtNanos && childEnd <= parentEnd &&
      (parent.startedAtNanos < child.startedAtNanos || parentEnd > childEnd)
  }

  protected def debug_calltree_events(
    record: Record
  ): Vector[DebugCallTreeEvent] =
    record.asMap.get("calltree") match {
      case Some(xs: Seq[?]) =>
        xs.toVector.collect { case r: Record => debug_calltree_event(r) }.flatten
      case _ =>
        Vector.empty
    }

  protected def debug_calltree_event(
    record: Record
  ): Option[DebugCallTreeEvent] = {
    val attrs = debug_calltree_attributes(record.asMap.get("attributes")) ++
      record.asMap.get("message").map(x => Map("message" -> x.toString)).getOrElse(Map.empty)
    for {
      kind <- debug_record_string(record, "kind").map(_.toLowerCase(java.util.Locale.ROOT))
      label <- debug_record_string(record, "label").filter(_.nonEmpty)
      start <- attrs.get("started_at_nanos")
        .orElse(attrs.get("ended_at_nanos"))
        .orElse(attrs.get("failed_at_nanos"))
        .orElse(attrs.get("sampled_at_nanos"))
        .flatMap(to_long_option)
    } yield DebugCallTreeEvent(
      kind,
      label,
      attrs,
      start,
      attrs.get("ended_at_nanos")
        .orElse(attrs.get("failed_at_nanos"))
        .orElse(attrs.get("sampled_at_nanos"))
        .flatMap(to_long_option)
    )
  }

  protected def debug_calltree_attributes(
    value: Option[Any]
  ): Map[String, String] =
    value match {
      case Some(r: Record) => r.asMap.map { case (k, v) => k -> v.toString }
      case Some(m: Map[?, ?]) => m.map { case (k, v) => k.toString -> v.toString }
      case _ => Map.empty
    }

  protected def debug_record_string(
    record: Record,
    key: String
  ): Option[String] =
    record.asMap.get(key).map(_.toString)

  protected def to_long_option(
    value: String
  ): Option[Long] =
    scala.util.Try(value.toLong).toOption

  protected def debug_calltree_outline(
    nodes: Vector[DebugCallTreeNode]
  ): String = {
    s"""<div class="list-group border rounded bg-white textus-calltree-outline" data-calltree-node-list>
       |${nodes.zipWithIndex.map { case (node, index) =>
         debug_calltree_node_html(node, 0, Vector(index + 1), None)
       }.mkString}
       |</div>""".stripMargin
  }

  protected def debug_calltree_node_html(
    node: DebugCallTreeNode,
    depth: Int,
    path: Vector[Int],
    parentDisplayLabel: Option[String] = None
  ): String = {
    val kind = debug_calltree_node_kind(node)
    val displayLabel = debug_calltree_display_label(node)
    val pair = path.mkString("-")
    val line = DebugCallTreeLine("step", node.label, displayLabel, kind, debug_calltree_display_attributes(node, node.attributes), depth, pair, parentDisplayLabel, node.observations)
    val children = node.children.zipWithIndex.map { case (child, index) =>
      debug_calltree_node_html(child, depth + 1, path :+ (index + 1), Some(displayLabel))
    }.mkString
    debug_calltree_line_html(line, children)
  }

  protected def debug_calltree_line_html(
    line: DebugCallTreeLine
  ): String =
    debug_calltree_line_html(line, "")

  protected def debug_calltree_line_html(
    line: DebugCallTreeLine,
    childrenHtml: String
  ): String = {
    val childBlock =
      if (childrenHtml.isEmpty)
        ""
      else
        s"""<div class="list-group mt-2 textus-calltree-children" data-calltree-children>
           |${childrenHtml}
           |</div>""".stripMargin
    val attrs = debug_calltree_attributes_html(line.attributes)
    val observations = debug_calltree_observations_html(line.observations)
    val body = attrs + observations + childBlock
    val badges = debug_calltree_badges(line)
    val realIo = if (debug_calltree_has_highlight(line.attributes, "real_io")) "true" else ""
    val source = line.attributes
      .get("source")
      .orElse(line.attributes.get("cache_layer"))
      .orElse(line.attributes.get("datastore"))
      .getOrElse("")
    val style = s"padding-left:${if (line.depth == 0) 0.75 else 1.0}rem"
    val lane = if (line.depth == 0) "" else """<span class="textus-calltree-lane" aria-hidden="true"></span>"""
    s"""<div class="list-group-item py-2 textus-calltree-row textus-calltree-row-${escape(line.role)}" style="${style}" data-calltree-node data-calltree-row data-calltree-${escape(line.role)}="true" data-calltree-pair="${escape(line.pair)}" data-calltree-depth="${line.depth}" data-calltree-kind="${escape(line.kind)}" data-calltree-real-io="${escape(realIo)}" data-calltree-source="${escape(source)}">
       |  ${lane}
       |  <details${if (line.depth <= renderer_config.callTreeInitialOpenDepth) " open" else ""}>
       |    <summary class="d-flex flex-wrap align-items-center gap-2">
       |      <span class="badge ${debug_calltree_role_badge_variant(line.role)}" data-calltree-role>${escape(line.kind)}</span>
       |      <span class="fw-semibold" data-calltree-label>${escape(line.displayLabel)}</span>
       |      ${badges}
       |    </summary>
       |    <div class="mt-2">${body}</div>
       |  </details>
       |</div>""".stripMargin
    }

  protected def debug_calltree_observations_html(
    observations: Vector[DebugCallTreeObservation]
  ): String =
    if (observations.isEmpty)
      ""
    else {
      val items = observations.map { observation =>
        val attrs = debug_calltree_attributes_html(observation.attributes)
        val badges = debug_calltree_badges(observation.attributes, observation.kind)
        val realIo = if (debug_calltree_has_highlight(observation.attributes, "real_io")) "true" else ""
        val source = observation.attributes
          .get("source")
          .orElse(observation.attributes.get("cache_layer"))
          .orElse(observation.attributes.get("datastore"))
          .getOrElse("")
        s"""<div class="border-start border-2 ps-2 py-1 mb-1 textus-calltree-observation" data-calltree-observation data-calltree-observation-kind="${escape(observation.kind)}" data-calltree-observation-real-io="${escape(realIo)}" data-calltree-observation-source="${escape(source)}">
           |  <div class="d-flex flex-wrap align-items-center gap-2">
           |    <span class="badge text-bg-secondary">observation</span>
           |    <span class="fw-semibold" data-calltree-observation-label>${escape(observation.displayLabel)}</span>
           |    ${badges}
           |  </div>
           |  <div class="mt-1">${attrs}</div>
           |</div>""".stripMargin
      }.mkString
      s"""<details class="mt-2" data-calltree-observations>
         |  <summary class="small text-secondary fw-semibold">Step observations (${observations.length})</summary>
         |  <div class="mt-2">${items}</div>
         |</details>""".stripMargin
    }

  protected def debug_calltree_display_label(
    node: DebugCallTreeNode
  ): String =
    node.attributes.get("display_label").filter(_.nonEmpty).getOrElse(node.label)

  protected def debug_calltree_display_attributes(
    node: DebugCallTreeNode,
    attributes: Map[String, String]
  ): Map[String, String] = {
    attributes
  }

  protected def debug_calltree_node_kind(
    node: DebugCallTreeNode
  ): String = {
    node.attributes.get("calltree_kind").filter(_.nonEmpty).getOrElse("step")
  }

  protected def debug_calltree_badges(
    node: DebugCallTreeNode
  ): String =
    debug_calltree_badges(node.attributes, debug_calltree_node_kind(node))

  protected def debug_calltree_badges(
    line: DebugCallTreeLine
  ): String =
    debug_calltree_badges(line.attributes, line.kind)

  protected def debug_calltree_badges(
    attributes: Map[String, String],
    kind: String
  ): String = {
    val highlightBadges = debug_calltree_highlights(attributes).map { highlight =>
      val variant = highlight match {
        case "real_io" => "text-bg-warning"
        case "cache_hit" => "text-bg-info"
        case _ => "text-bg-secondary"
      }
      s"""<span class="badge ${variant} ms-2" data-calltree-badge data-calltree-highlight="${escape(highlight)}">${escape(highlight)}</span>"""
    }
    val keys = Vector("outcome", "duration_millis", "cache_layer", "source", "datastore")
    val badges = keys.flatMap { key =>
      attributes.get(key).filter(_.nonEmpty).map { value =>
        val variant =
          key match {
            case "outcome" if value == "failure" || value == "failed" || value == "error" => "text-bg-danger"
            case "outcome" if value == "success" || value == "succeeded" => "text-bg-success"
            case "outcome" if value == "start" => "text-bg-primary"
            case "cache_layer" => "text-bg-info"
            case "duration_millis" => "text-bg-light"
            case _ => "text-bg-secondary"
          }
        val label = if (key == "duration_millis") s"${value}ms" else s"${key}=${value}"
        s"""<span class="badge ${variant} ms-2" data-calltree-badge>${escape(label)}</span>"""
      }
    }
    (highlightBadges ++ badges).mkString
  }

  protected def debug_calltree_highlights(
    attributes: Map[String, String]
  ): Vector[String] =
    attributes.get("highlights")
      .toVector
      .flatMap(_.split("[,\\s]+").toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct ++
      (if (attributes.get("real_io").exists(_.equalsIgnoreCase("true"))) Vector("real_io") else Vector.empty)

  protected def debug_calltree_has_highlight(
    attributes: Map[String, String],
    name: String
  ): Boolean =
    debug_calltree_highlights(attributes).contains(name)

  protected def debug_calltree_role_badge_variant(
    role: String
  ): String =
    role match {
      case "enter" => "text-bg-primary"
      case "leave" => "text-bg-dark"
      case _ => "text-bg-secondary"
    }

  protected def debug_calltree_attributes_html(
    attributes: Map[String, String]
  ): String = {
    def key_order(key: String): (Int, String) =
      key match {
        case "calltree_kind" => (-1, key)
        case "display_label" => (-1, key)
        case "component" => (0, key)
        case "service" => (1, key)
        case "operation" => (2, key)
        case _ => (100, key)
      }
    val visible = attributes.toVector
      .filterNot { case (key, _) => key == "started_at_nanos" || key == "ended_at_nanos" }
      .filterNot { case (key, _) => key == "calltree_kind" || key == "display_label" || key == "highlights" || key == "real_io" }
      .sortBy { case (key, _) => key_order(key) }
    if (visible.isEmpty)
      ""
    else
      s"""<dl class="row small mb-2" data-calltree-attributes>
         |${visible.map { case (key, value) =>
           val long = debug_calltree_multiline_attribute(key, value)
           val body =
             if (debug_calltree_payload_attribute(key))
               debug_calltree_payload_html(key, value)
             else if (long)
               s"""<pre class="bg-light border rounded p-2 mb-1"><code>${escape(value)}</code></pre>"""
             else
               s"""<code>${escape(value)}</code>"""
           val longAttr = if (long) """ data-calltree-long-attribute="true"""" else ""
           s"""<dt class="col-sm-3" data-calltree-attribute-key="${escape(key)}">${escape(key)}</dt><dd class="col-sm-9" data-calltree-attribute data-calltree-attribute-key="${escape(key)}"${longAttr}>${body}</dd>"""
         }.mkString}
         |</dl>""".stripMargin
  }

  protected def debug_calltree_multiline_attribute(
    key: String,
    value: String
  ): Boolean =
    value.length > 120 ||
      key == "sql" ||
      key == "query" ||
      key == "request" ||
      key == "resolved_parameters" ||
      key == "response" ||
      key == "result"

  protected def debug_calltree_payload_attribute(
    key: String
  ): Boolean =
    key == "result" || key == "response"

  protected def debug_calltree_payload_html(
    key: String,
    value: String
  ): String = {
    val parsed = debug_calltree_payload_json(value)
    val summary = parsed.map(debug_calltree_payload_summary).getOrElse(debug_calltree_compact_text(value))
    val external = parsed.flatMap(debug_calltree_external_payload)
    val details =
      if (debug_calltree_payload_detail_required(parsed, value))
        s"""<details class="textus-calltree-payload-details mt-1">
           |  <summary class="small text-secondary">Show ${escape(key)}</summary>
           |  <pre class="bg-light border rounded p-2 mt-1 mb-1"><code>${escape(parsed.map(_.spaces2).getOrElse(value))}</code></pre>
           |</details>""".stripMargin
      else
        ""
    s"""<div class="textus-calltree-payload" data-calltree-payload="${escape(key)}">
       |  <code>${escape(summary)}</code>
       |  ${external.map(debug_calltree_external_payload_html(key, _)).getOrElse("")}
       |  $details
       |</div>""".stripMargin
  }

  protected def debug_calltree_payload_json(
    value: String
  ): Option[Json] =
    parse(value).toOption.flatMap { json =>
      json.asString match {
        case Some(s) =>
          val trimmed = s.trim
          if (trimmed.startsWith("{") || trimmed.startsWith("["))
            parse(trimmed).toOption.orElse(Some(json))
          else
            Some(json)
        case None => Some(json)
      }
    }

  protected def debug_calltree_payload_detail_required(
    parsed: Option[Json],
    raw: String
  ): Boolean =
    parsed match {
      case Some(json) =>
        !debug_calltree_payload_one_line(json)
      case None =>
        raw.length > 120 || raw.contains("\n")
    }

  protected def debug_calltree_payload_one_line(
    json: Json
  ): Boolean =
    json.asObject.flatMap(_("kind")).flatMap(_.asString).exists { kind =>
      Set("void", "unit", "null", "none").contains(kind)
    } || json.asString.exists(_.length <= 120) ||
      json.asNumber.nonEmpty ||
      json.asBoolean.nonEmpty

  protected def debug_calltree_payload_summary(
    json: Json
  ): String =
    json.asObject match {
      case Some(obj) =>
        val kind = obj("kind").flatMap(_.asString)
        val inline = obj("inline").flatMap(debug_calltree_json_scalar_string)
        val parts =
          Vector(
            kind,
            obj("record_count").flatMap(debug_calltree_json_scalar_string).map(x => s"records=$x"),
            obj("field_count").flatMap(debug_calltree_json_scalar_string).map(x => s"fields=$x"),
            obj("size_bytes").flatMap(debug_calltree_json_scalar_string).map(x => s"${x} bytes"),
            obj("char_count").flatMap(debug_calltree_json_scalar_string).map(x => s"${x} chars"),
            inline.filterNot(_ == "false").filter(_.length <= 80).map(x => s"inline=$x")
          ).flatten
        if (parts.nonEmpty)
          parts.mkString(" ")
        else
          debug_calltree_compact_text(json.noSpaces)
      case None =>
        debug_calltree_json_scalar_string(json).map(debug_calltree_compact_text).getOrElse(debug_calltree_compact_text(json.noSpaces))
    }

  protected def debug_calltree_json_scalar_string(
    json: Json
  ): Option[String] =
    json.asString
      .orElse(json.asNumber.map(_.toString))
      .orElse(json.asBoolean.map(_.toString))

  protected def debug_calltree_external_payload(
    json: Json
  ): Option[(String, Boolean)] =
    json.asObject.flatMap { obj =>
      Vector("external_href", "external_url", "payload_href", "payload_url", "href", "url")
        .flatMap(key => obj(key).flatMap(_.asString))
        .headOption
        .map(_ -> true)
        .orElse(
          Vector("external_path", "payload_path", "file_path", "path", "file", "ref")
            .flatMap(key => obj(key).flatMap(_.asString))
            .headOption
            .map(_ -> false)
        )
    }

  protected def debug_calltree_external_payload_html(
    key: String,
    target: (String, Boolean)
  ): String = {
    val label = s"Open external $key"
    if (target._2)
      s"""<a class="btn btn-sm btn-outline-secondary ms-2" href="${escape(target._1)}">${escape(label)}</a>"""
    else
      s"""<span class="badge text-bg-light ms-2">external: ${escape(target._1)}</span>"""
  }

  protected def debug_calltree_compact_text(
    value: String
  ): String =
    if (value.length <= 120) value else value.take(117) + "..."
}
