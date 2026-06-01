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
import org.goldenport.cncf.naming.PropertyValueResolver
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
 *  version May. 24, 2026
 * @version Jun. 01, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppRendererSupport {
  final case class Page(body: String)
  final case class PageRequest(
    page: Int = 1,
    pageSize: Int = 20,
    includeTotal: Boolean = false,
    totalCountPolicy: WebDescriptor.TotalCountPolicy = WebDescriptor.TotalCountPolicy.Disabled
  ) {
    def effectiveIncludeTotal: Boolean =
      includeTotal && totalCountPolicy.allowsTotal

    def toPairs: Vector[(String, Any)] =
      Vector(
        "page" -> page,
        "pageSize" -> pageSize
      ) ++
        Vector("totalCountPolicy" -> totalCountPolicy.name) ++
        (if (effectiveIncludeTotal) Vector("includeTotal" -> true) else Vector.empty)

    def href(basePath: String): String = {
      val total = if (effectiveIncludeTotal) "&includeTotal=true" else ""
      s"${basePath}?page={page}&pageSize={pageSize}${total}"
    }

    def withTotalCountPolicy(policy: WebDescriptor.TotalCountPolicy): PageRequest =
      copy(totalCountPolicy = policy)
  }
  final case class FormPageProperties(
  componentName: String,
  serviceName: String,
  operationName: String,
  values: Map[String, String] = Map.empty,
  componentPathOverride: Option[String] = None,
  servicePathOverride: Option[String] = None,
  operationPathOverride: Option[String] = None
) {
    def componentPath: String =
      componentPathOverride.getOrElse(NamingConventions.toNormalizedSegment(componentName))

    def servicePath: String =
      servicePathOverride.getOrElse(NamingConventions.toNormalizedSegment(serviceName))

    def operationPath: String =
      operationPathOverride.getOrElse(NamingConventions.toNormalizedSegment(operationName))

    def operationLabel: String =
      s"${componentName}.${serviceName}.${operationName}"

    def withValue(name: String, value: String): FormPageProperties =
      copy(values = values + (name -> value))

    def value(name: String): String =
      PropertyValueResolver.value(values, name).getOrElse("")
  }
  final case class FormResultProperties(
    page: FormPageProperties,
    status: Int,
    contentType: String,
    body: String,
    tableColumns: Map[String, Vector[TableColumn]] = Map.empty,
    defaultTableView: String = WebTableColumnResolver.defaultViewName,
    assetCompletion: StaticFormAppLayout.AssetCompletionOptions =
      StaticFormAppLayout.AssetCompletionOptions(),
    executionMetadata: RuntimeContext.ExecutionMetadata =
      RuntimeContext.ExecutionMetadata.empty,
    operationMode: OperationMode = RuntimeConfig.DefaultOperationMode,
    fieldConfidentiality: Map[String, DataConfidentiality] = Map.empty
  ) {
    def componentName: String = page.componentName
    def serviceName: String = page.serviceName
    def operationName: String = page.operationName

    def operationLabel: String =
      page.operationLabel

    def nextPageProperties: FormPageProperties =
      val formValues = page.values.collect {
        case (key, value) if !isHiddenFormContextKey(key) => s"form.${key}" -> value
      }
      val metadata = FormResultMetadata.fromBody(body)
      val resultValues = Map(
        "component" -> componentName,
        "service" -> serviceName,
        "operation" -> operationName,
        "operation.label" -> operationLabel,
        "result.status" -> status.toString,
        "result.ok" -> (status >= 200 && status < 400).toString,
        "result.contentType" -> contentType,
        "result.body" -> body,
        "paging.page" -> page.values.getOrElse("paging.page", page.values.getOrElse("page", "1")),
        "paging.pageSize" -> page.values.getOrElse("paging.pageSize", page.values.getOrElse("pageSize", page.values.getOrElse("limit", "20"))),
        "paging.chunkSize" -> page.values.getOrElse("paging.chunkSize", "1000"),
        "paging.href" -> page.values.getOrElse("paging.href", _default_paging_href)
      ) ++ _framework_action_values(metadata) ++
        metadata.toTemplateValues ++
        FormResultMetadata.executionTemplateValues(executionMetadata)
      val base = page.copy(values = page.values ++ formValues ++ resultValues)
      if (status >= 400)
        base
          .withValue("error.status", status.toString)
          .withValue("error.body", body)
      else
        base

    private def _default_paging_href: String =
      _form_result_paging_href(
        s"/form/${page.componentPath}/${page.servicePath}/${page.operationPath}/result",
        page.values
      )

    private def _form_result_paging_href(
      base: String,
      values: Map[String, String]
    ): String = {
      val reserved = Set(
        "page",
        "pageSize",
        "offset",
        "textus.debug.executionPanel"
      )
      val context = values.toVector
        .filter { case (key, value) =>
          value.nonEmpty &&
            !reserved.contains(key) &&
            !key.startsWith("paging.") &&
            !key.startsWith("result.") &&
            !key.startsWith("error.") &&
            !key.startsWith("form.") &&
            _is_safe_paging_context_key(key)
        }
        .sortBy(_._1)
      val pairs = context ++ Vector("page" -> "{page}", "pageSize" -> "{pageSize}")
      val query = pairs.map {
        case (key, "{page}") => s"${escapeQuery(key)}={page}"
        case (key, "{pageSize}") => s"${escapeQuery(key)}={pageSize}"
        case (key, value) => s"${escapeQuery(key)}=${escapeQuery(value)}"
      }.mkString("&")
      s"${base}?${query}"
    }

    private def _is_safe_paging_context_key(key: String): Boolean = {
      val normalized = key.trim.toLowerCase(java.util.Locale.ROOT)
      val safeKeys = Set(
        "textus.form.page",
        "q",
        "text",
        "tag",
        "searchmode",
        "sort",
        "order",
        "limit",
        "includetotal",
        "includedescendants",
        "unreadonly",
        "includedismissed",
        "notificationtype",
        "channel",
        "status"
      )
      safeKeys.contains(normalized)
    }

    private def _framework_action_values(metadata: FormResultMetadata): Map[String, String] =
      _detail_action_values(metadata) ++ _job_action_values(metadata) ++ _return_action_values

    private def _job_action_values(metadata: FormResultMetadata): Map[String, String] =
      metadata.jobId match {
        case Some(jobid) =>
          val awaitHref = page.values.getOrElse("result.job.await.href", s"/form/${page.componentPath}/${page.servicePath}/${page.operationPath}/jobs/${jobid}/await")
          val appJobHref = page.values.getOrElse("result.job.href", s"/web/${page.componentPath}/jobs/${jobid}")
          val appJobsHref = page.values.getOrElse("result.jobs.href", s"/web/${page.componentPath}/jobs")
          val common = Map(
            "result.job.href" -> appJobHref,
            "result.job.await.href" -> awaitHref,
            "result.jobs.href" -> appJobsHref,
            "result.job.status" -> metadata.jobStatus.getOrElse("accepted"),
            "result.job.message" -> metadata.message.getOrElse("Command accepted."),
            "result.action.result.name" -> "result",
            "result.action.result.label" -> "Open job result",
            "result.action.result.href" -> appJobHref,
            "result.action.result.method" -> "GET",
            "result.action.jobs.name" -> "jobs",
            "result.action.jobs.label" -> "My jobs",
            "result.action.jobs.href" -> appJobsHref,
            "result.action.jobs.method" -> "GET",
            "result.action.await.name" -> "await",
            "result.action.await.label" -> "Check result",
            "result.action.await.href" -> awaitHref,
            "result.action.await.method" -> "POST"
          )
          if (metadata.actions.isEmpty)
            common ++ Map(
              "result.actions.count" -> "2",
              "result.action.0.name" -> "result",
              "result.action.0.label" -> "Open job result",
              "result.action.0.href" -> appJobHref,
              "result.action.0.method" -> "GET",
              "result.action.1.name" -> "await",
              "result.action.1.label" -> "Check result",
              "result.action.1.href" -> awaitHref,
              "result.action.1.method" -> "POST",
              "result.action.primary.name" -> "result",
              "result.action.primary.label" -> "Open job result",
              "result.action.primary.href" -> appJobHref,
              "result.action.primary.method" -> "GET"
            )
          else
            common
        case None =>
          Map.empty
      }

    private def _detail_action_values(metadata: FormResultMetadata): Map[String, String] =
      metadata.id.flatMap(id => _detail_operation_name.map(_ -> id)) match {
        case Some((detailOperation, id)) =>
          val detailOperationPath = NamingConventions.toNormalizedSegment(detailOperation)
          val href = s"/form/${page.componentPath}/${page.servicePath}/${detailOperationPath}/result?id=${escapeQuery(id)}"
          val common = Map(
            "result.action.detail.name" -> "detail",
            "result.action.detail.label" -> "Open detail",
            "result.action.detail.href" -> href,
            "result.action.detail.method" -> "GET"
          )
          if (metadata.actions.isEmpty && metadata.jobId.isEmpty)
            common ++ Map(
              "result.actions.count" -> "1",
              "result.action.0.name" -> "detail",
              "result.action.0.label" -> "Open detail",
              "result.action.0.href" -> href,
              "result.action.0.method" -> "GET",
              "result.action.primary.name" -> "detail",
              "result.action.primary.label" -> "Open detail",
              "result.action.primary.href" -> href,
              "result.action.primary.method" -> "GET"
            )
          else
            common
        case None =>
          Map.empty
      }

    private def _detail_operation_name: Option[String] = {
      val name = NamingConventions.toNormalizedSegment(operationName)
      Vector("post-", "create-", "update-").collectFirst {
        case prefix if name.startsWith(prefix) => s"get-${name.drop(prefix.length)}"
      }
    }

    private def _return_action_values: Map[String, String] =
      page.values.get("return.href").filter(_.nonEmpty).map { href =>
        Map(
          "result.action.return.name" -> "return",
          "result.action.return.label" -> "Back",
          "result.action.return.href" -> href,
          "result.action.return.method" -> "GET"
        )
      }.getOrElse(Map.empty)
  }
  final case class TableColumn(
    name: String,
    label: String
  )
  final case class FormValidationResult(
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    values: Map[String, String],
    errors: Vector[FormValidationMessage],
    warnings: Vector[FormValidationMessage]
  ) {
    def valid: Boolean = errors.isEmpty
  }
  final case class FormValidationMessage(
    field: Option[String],
    code: String,
    message: String
  )
  final case class DocumentLink(
    title: String,
    href: String
  )

  val defaultPageViewContextValues: Map[String, String] = Map(
    "pageContext.session.authenticated" -> "false",
    "pageContext.security.capabilities" -> "",
    "pageContext.notification.available" -> "false",
    "pageContext.notification.unconfirmedCount" -> "0",
    "pageContext.notification.indicatorHidden" -> "hidden",
    "pageContext.notification.badgeHidden" -> "hidden",
    "pageContext.jobs.activeCount" -> "0",
    "pageContext.jobs.unconfirmedCount" -> "0",
    "pageContext.jobs.visible" -> "false",
    "pageContext.jobs.hidden" -> "hidden",
    "pageContext.jobs.activeBadgeHidden" -> "hidden",
    "pageContext.jobs.unconfirmedBadgeHidden" -> "hidden"
  )

  private val _hidden_form_context_exact_keys: Set[String] =
    Set(
      "crud.origin.href",
      "crud.success.href",
      "crud.error.href",
      "paging.page",
      "paging.pageSize",
      "paging.chunkSize",
      "paging.includeTotal",
      "paging.href",
      "continuation.id",
      "return.href",
      "textus.form.page",
      "cncf.form.page",
      "textus.admin.principalId",
      "textus.admin.subjectId",
      "version",
      "etag",
      "csrf"
    )

  def isHiddenFormContextKey(key: String): Boolean =
    _hidden_form_context_exact_keys.contains(key) || key.startsWith("search.")

  def isHtmlDocumentTemplate(template: String): Boolean = {
    val text = template.dropWhile(_.isWhitespace).toLowerCase(java.util.Locale.ROOT)
    text.startsWith("<!doctype html") || text.startsWith("<html")
  }

  def hasTextusMarkup(template: String): Boolean =
    """<textus(?::|-)[A-Za-z0-9-]+\b""".r.findFirstIn(template).nonEmpty

  def tableColumnKey(
    source: String,
    entity: String,
    view: String = WebTableColumnResolver.defaultViewName
  ): String =
    s"${source}|entity=${NamingConventions.toNormalizedSegment(entity)}|view=${NamingConventions.toNormalizedSegment(view)}"

  def escapeQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

abstract class StaticFormAppRendererSupport(
  protected val renderer_config: StaticFormAppRendererConfig
) {
  import StaticFormAppRendererSupport.*
}
