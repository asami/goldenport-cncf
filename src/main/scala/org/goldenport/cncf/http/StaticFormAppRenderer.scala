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
 * @since   Apr. 12, 2026
 *  version Apr. 30, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppRenderer {
  private val _call_tree_js_asset = "/web/assets/textus-calltree.js"

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
      values.getOrElse(name, "")
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
      ) ++ _framework_action_values(metadata) ++ metadata.toTemplateValues
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
        case (key, "{page}") => s"${_escape_query(key)}={page}"
        case (key, "{pageSize}") => s"${_escape_query(key)}={pageSize}"
        case (key, value) => s"${_escape_query(key)}=${_escape_query(value)}"
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
          val href = s"/form/${page.componentPath}/${page.servicePath}/${detailOperationPath}/result?id=${_escape_query(id)}"
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
  private final case class OperationWebSchemaContext(
    component: Component,
    serviceName: String,
    operationName: String,
    componentPath: String,
    servicePath: String,
    operationPath: String,
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    associationBinding: Option[CmlOperationAssociationBinding] = None,
    imageBinding: Option[CmlOperationImageBinding] = None
  )
  private final case class FormDefinitionNavigation(
    mode: String,
    method: String,
    submitPath: String,
    htmlPath: String,
    actions: Vector[FormDefinitionAction] = Vector.empty
  )
  private final case class FormDefinitionAction(
    name: String,
    method: String,
    path: String
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

  def render(
    subsystem: Subsystem,
    app: String,
    page: Vector[String] = Vector.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] = {
    page match {
      case Vector() if app == "document" =>
        Some(renderSystemDocument(subsystem))
      case Vector("specification") if app == "document" =>
        Some(renderSystemManual(subsystem))
      case Vector() if app == "console" =>
        Some(renderSystemConsole(subsystem))
      case _ if !webDescriptor.isAppEnabled(app, page) =>
        None
      case Vector("dashboard") =>
        _find_component(subsystem, app).map(renderComponentDashboard(_, NamingConventions.toNormalizedSegment(app)))
      case Vector() =>
        renderFormIndex(subsystem, app, webDescriptor)
      case _ =>
        None
    }
  }

  def renderStaticTemplate(
    app: String,
    page: Vector[String],
    template: String,
    assetCompletion: StaticFormAppLayout.AssetCompletionOptions =
      StaticFormAppLayout.AssetCompletionOptions(),
    pageContext: WebPageContext = WebPageContext.empty
  ): Page = {
    val pageName =
      if (page.isEmpty) "index"
      else page.mkString("/")
    val properties = FormPageProperties(
      app,
      "web",
      pageName,
      Map(
        "app" -> app,
        "page.name" -> pageName,
        "page.path" -> ("/web/" + (app +: page).mkString("/"))
      ) ++ _page_context_properties(pageContext)
    )
    val rendered = _render_template(template, properties, Map.empty)
    Page(_complete_widget_assets(template, rendered, assetCompletion))
  }

  private def _page_context_properties(
    pageContext: WebPageContext
  ): Map[String, String] = {
    defaultPageViewContextValues ++ pageContext.values
  }

  def isHtmlDocumentTemplate(template: String): Boolean =
    _is_html_document(template)

  def hasTextusMarkup(template: String): Boolean =
    _has_textus_widgets(template)

  def renderFormIndex(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val services = component.protocol.services.services.map { service =>
        val operations = service.operations.operations.toVector.filter { operation =>
          webDescriptor.isFormEnabled(_operation_selector(component.name, service.name, operation.name))
        }.map { operation =>
          val path = s"/form/${componentPath}/${NamingConventions.toNormalizedSegment(service.name)}/${NamingConventions.toNormalizedSegment(operation.name)}"
          s"""<a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center" href="${_escape(path)}"><span>${_escape(operation.name)}</span><span class="badge text-bg-secondary">operation</span></a>"""
        }.mkString("\n")
        val body =
          if (operations.isEmpty)
            _admin_empty_state("No form operations are enabled for this service.")
          else
            s"""<div class="list-group">${operations}</div>"""
        s"""<div class="col-12 col-lg-6">
           |  <article class="card admin-card h-100">
           |    <div class="card-body">
           |      <h2 class="card-title h5">${_escape(service.name)}</h2>
           |      ${body}
           |    </div>
           |  </article>
           |</div>""".stripMargin
      }.mkString("\n")
      val navigation = _admin_nav_card(Vector(
        "Dashboard" -> s"/web/${componentPath}/dashboard",
        "Admin configuration" -> s"/web/${componentPath}/admin"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} Forms",
        subtitle = "HTML form operations",
        body =
          s"""${navigation}
             |<section class="row g-3 mt-1">
             |  ${services}
             |</section>""".stripMargin,
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = webDescriptor.assets.merge(webDescriptor.appAssets(component.name)).css,
          declaredJs = webDescriptor.assets.merge(webDescriptor.appAssets(component.name)).js
        )
      ))
    }

  def renderOperationForm(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    values: Map[String, String] = Map.empty,
    validation: Option[FormValidationResult] = None,
    operationMode: OperationMode = RuntimeConfig.DefaultOperationMode,
    showExecutionDebugPanel: Boolean = false
  ): Option[Page] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor).map { context =>
      val action = s"/form/${context.componentPath}/${context.servicePath}/${context.operationPath}"
      val effectiveValues = _operation_form_prefill_values(subsystem, context, values)
      val effectiveValidation = validation.filter(_.webSchema.selector == context.webSchema.selector)
      val controls = _operation_form_controls(context, effectiveValues, effectiveValidation)
      val hiddenContext = _hidden_form_context_inputs(effectiveValues)
      val errorPanel = _form_error_panel(effectiveValues) + _form_validation_panel(effectiveValidation)
      val enctype = _operation_form_enctype(context.webSchema, context.imageBinding)
      val debugPanel = _operation_form_debug_panel(context, values, operationMode, showExecutionDebugPanel)
      Page(_simple_page(
        title = s"${_escape(context.component.name)}.${_escape(context.serviceName)}.${_escape(context.operationName)}",
        subtitle = "HTML form operation",
        body =
          s"""<article class="card admin-card">
             |  <div class="card-body">
             |    ${errorPanel}
             |    <form method="post" action="${_escape(action)}"${enctype}>
             |      <div class="row g-3">
             |        <div class="col-12">${controls}</div>
             |      </div>
             |      ${hiddenContext}
             |      <div class="admin-action-row d-flex flex-wrap gap-2 mt-3">
             |        <button type="submit" class="btn btn-primary">Run</button>
             |        <a class="btn btn-outline-secondary" href="/form/${context.componentPath}">Operations</a>
             |      </div>
             |    </form>
             |  </div>
             |</article>
             |${debugPanel}""".stripMargin,
        assetCompletion = StaticFormAppLayout.AssetCompletionOptions(
          declaredCss = webDescriptor.resultAssets(context.component.name, context.serviceName, context.operationName).css,
          declaredJs = webDescriptor.resultAssets(context.component.name, context.serviceName, context.operationName).js
        )
      ))
    }

  private def _operation_form_enctype(
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    imageBinding: Option[CmlOperationImageBinding] = None
  ): String =
    if (webSchema.fields.exists(field => _is_blob_datatype(field.dataType.getOrElse(""))) ||
      imageBinding.exists(_.acceptsUpload))
      """ enctype="multipart/form-data""""
    else
      ""

  def renderOperationFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map { context =>
        _form_definition_json(
          context.webSchema,
          FormDefinitionNavigation(
            mode = "operation",
            method = "POST",
            submitPath = s"/form-api/${context.componentPath}/${context.servicePath}/${context.operationPath}",
            htmlPath = s"/form/${context.componentPath}/${context.servicePath}/${context.operationPath}",
            actions = Vector(
              FormDefinitionAction("submit", "POST", s"/form/${context.componentPath}/${context.servicePath}/${context.operationPath}"),
              FormDefinitionAction("api-submit", "POST", s"/form-api/${context.componentPath}/${context.servicePath}/${context.operationPath}"),
              FormDefinitionAction("validate", "POST", s"/form-api/${context.componentPath}/${context.servicePath}/${context.operationPath}/validate")
            )
          ),
          operationBindings = Some(context)
        )
      }

  def renderComponentAdminEntityFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displaySchema = _admin_entity_create_schema(component, entityPath, webSchema)
      _form_definition_json(
        displaySchema,
        FormDefinitionNavigation(
          mode = "admin-entity",
          method = "POST",
          submitPath = s"/form/${componentPath}/admin/entities/${entityPath}/create",
          htmlPath = s"/web/${componentPath}/admin/entities/${entityPath}/new",
          actions = Vector(
            FormDefinitionAction("list", "GET", s"/web/${componentPath}/admin/entities/${entityPath}"),
            FormDefinitionAction("new", "GET", s"/web/${componentPath}/admin/entities/${entityPath}/new"),
            FormDefinitionAction("create", "POST", s"/form/${componentPath}/admin/entities/${entityPath}/create"),
            FormDefinitionAction("detail", "GET", s"/web/${componentPath}/admin/entities/${entityPath}/{id}"),
            FormDefinitionAction("edit", "GET", s"/web/${componentPath}/admin/entities/${entityPath}/{id}/edit"),
            FormDefinitionAction("update", "POST", s"/form/${componentPath}/admin/entities/${entityPath}/{id}/update")
          )
        )
      )
    }

  def renderComponentAdminEntityUpdateFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displayFields = _admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
      val displaySchema = webSchema.copy(fields = _admin_display_web_fields(webSchema.fields, displayFields))
      _form_definition_json(
        displaySchema,
        FormDefinitionNavigation(
          mode = "admin-entity-update",
          method = "POST",
          submitPath = s"/form/${componentPath}/admin/entities/${entityPath}/${id}/update",
          htmlPath = s"/web/${componentPath}/admin/entities/${entityPath}/${id}/edit",
          actions = Vector(
            FormDefinitionAction("list", "GET", s"/web/${componentPath}/admin/entities/${entityPath}"),
            FormDefinitionAction("detail", "GET", s"/web/${componentPath}/admin/entities/${entityPath}/${id}"),
            FormDefinitionAction("edit", "GET", s"/web/${componentPath}/admin/entities/${entityPath}/${id}/edit"),
            FormDefinitionAction("update", "POST", s"/form/${componentPath}/admin/entities/${entityPath}/${id}/update")
          )
        )
      )
    }

  def renderComponentAdminDataFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        _admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      _form_definition_json(
        webSchema,
        FormDefinitionNavigation(
          mode = "admin-data",
          method = "POST",
          submitPath = s"/form/${componentPath}/admin/data/${dataPath}/create",
          htmlPath = s"/web/${componentPath}/admin/data/${dataPath}/new",
          actions = Vector(
            FormDefinitionAction("list", "GET", s"/web/${componentPath}/admin/data/${dataPath}"),
            FormDefinitionAction("new", "GET", s"/web/${componentPath}/admin/data/${dataPath}/new"),
            FormDefinitionAction("create", "POST", s"/form/${componentPath}/admin/data/${dataPath}/create"),
            FormDefinitionAction("detail", "GET", s"/web/${componentPath}/admin/data/${dataPath}/{id}"),
            FormDefinitionAction("edit", "GET", s"/web/${componentPath}/admin/data/${dataPath}/{id}/edit"),
            FormDefinitionAction("update", "POST", s"/form/${componentPath}/admin/data/${dataPath}/{id}/update")
          )
        )
      )
    }

  def renderComponentAdminDataUpdateFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        _admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      _form_definition_json(
        webSchema,
        FormDefinitionNavigation(
          mode = "admin-data-update",
          method = "POST",
          submitPath = s"/form/${componentPath}/admin/data/${dataPath}/${id}/update",
          htmlPath = s"/web/${componentPath}/admin/data/${dataPath}/${id}/edit",
          actions = Vector(
            FormDefinitionAction("list", "GET", s"/web/${componentPath}/admin/data/${dataPath}"),
            FormDefinitionAction("detail", "GET", s"/web/${componentPath}/admin/data/${dataPath}/${id}"),
            FormDefinitionAction("edit", "GET", s"/web/${componentPath}/admin/data/${dataPath}/${id}/edit"),
            FormDefinitionAction("update", "POST", s"/form/${componentPath}/admin/data/${dataPath}/${id}/update")
          )
        )
      )
    }

  def renderComponentAdminViewFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    viewName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val definition = _view_definition(component, viewName)
      val entityName = definition.map(_.entityName).getOrElse(_strip_surface_suffix(viewPath, "view").getOrElse(viewPath))
      val webSchema = WebSchemaResolver.resolveView(
        component,
        componentPath,
        viewPath,
        Some(entityName),
        webDescriptor,
        viewFields = definition.flatMap(_.fieldsFor("summary")).orElse(_admin_entity_view_fields(component, entityName, "summary")),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      _form_definition_json(
        webSchema,
        FormDefinitionNavigation(
          mode = "admin-view",
          method = "GET",
          submitPath = s"/web/${componentPath}/admin/views/${viewPath}",
          htmlPath = s"/web/${componentPath}/admin/views/${viewPath}",
          actions = Vector(
            FormDefinitionAction("list", "GET", s"/web/${componentPath}/admin/views/${viewPath}"),
            FormDefinitionAction("detail", "GET", s"/web/${componentPath}/admin/views/${viewPath}/{id}")
          )
        )
      )
    }

  def renderComponentAdminAggregateFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    aggregateName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val definition = _aggregate_definition(component, aggregateName)
      val entityName = definition.map(_.entityName).getOrElse(_strip_surface_suffix(aggregatePath, "aggregate").getOrElse(aggregatePath))
      val webSchema = WebSchemaResolver.resolveAggregate(
        component,
        componentPath,
        aggregatePath,
        Some(entityName),
        webDescriptor,
        viewFields = _admin_entity_view_fields(component, entityName, "summary"),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      _form_definition_json(
        webSchema,
        FormDefinitionNavigation(
          mode = "admin-aggregate",
          method = "GET",
          submitPath = s"/web/${componentPath}/admin/aggregates/${aggregatePath}",
          htmlPath = s"/web/${componentPath}/admin/aggregates/${aggregatePath}",
          actions = Vector(
            FormDefinitionAction("list", "GET", s"/web/${componentPath}/admin/aggregates/${aggregatePath}"),
            FormDefinitionAction("detail", "GET", s"/web/${componentPath}/admin/aggregates/${aggregatePath}/{id}")
          )
        )
      )
    }

  def renderOperationFormValidation(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map(context => _form_validation_json(_validate_operation_form(context, values)))

  def validateOperationForm(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[FormValidationResult] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map(context => _validate_operation_form(context, values))

  def validateComponentAdminEntityForm(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    view: Option[String] = None
  ): Option[FormValidationResult] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val formSchema = view match {
        case Some("create") => _admin_entity_create_schema(component, entityPath, webSchema)
        case Some(v) =>
          val displayFields = _admin_entity_display_fields(component, entityPath, v, webSchema.fieldNames)
          webSchema.copy(fields = _admin_display_web_fields(webSchema.fields, displayFields))
        case None => webSchema
      }
      _validate_form(formSchema, values)
    }

  def validateComponentAdminDataForm(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[FormValidationResult] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        _admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      _validate_form(webSchema, values)
    }

  private def _form_error_panel(values: Map[String, String]): String = {
    val xs = values.filter { case (key, _) => key == "error" || key.startsWith("error.") }
    if (xs.isEmpty) ""
    else
      s"""<div class="alert alert-danger admin-feedback" role="alert">
         |  <p class="alert-heading fw-semibold mb-2">Form submission failed.</p>
         |  <dl class="row mb-0">${_property_rows(xs)}</dl>
         |</div>""".stripMargin
  }

  private def _form_validation_panel(
    validation: Option[FormValidationResult]
  ): String =
    validation match {
      case Some(result) if !result.valid =>
        val errors =
          result.errors.map(x => s"""<li>${_escape(x.message)}</li>""").mkString("\n")
        val warnings =
          if (result.warnings.isEmpty)
            ""
          else
            s"""<p class="mb-1">Warnings</p><ul>${result.warnings.map(x => s"<li>${_escape(x.message)}</li>").mkString("\n")}</ul>"""
        s"""<div class="alert alert-danger admin-feedback" role="alert">
           |  <p class="alert-heading fw-semibold mb-2">Validation failed.</p>
           |  <ul class="mb-0">${errors}</ul>
           |  ${warnings}
           |</div>""".stripMargin
      case _ =>
        ""
    }

  private def _operation_form_debug_panel(
    context: OperationWebSchemaContext,
    values: Map[String, String],
    operationMode: OperationMode,
    enabled: Boolean
  ): String =
    if (!enabled || !_is_development_operation_mode(operationMode))
      ""
    else {
      val status =
        values.get("error.status")
          .flatMap(x => scala.util.Try(x.trim.toInt).toOption)
          .getOrElse(400)
      val body = values.getOrElse("error.body", "")
      val pageProperties = FormPageProperties(
        context.component.name,
        context.serviceName,
        context.operationName,
        values
      )
      _execution_debug_panel(FormResultProperties(
        pageProperties,
        status,
        "text/plain",
        body,
        operationMode = operationMode,
        fieldConfidentiality = _field_confidentiality(context.webSchema)
      ), pageProperties)
    }

  private def _field_confidentiality(
    schema: WebSchemaResolver.ResolvedWebSchema
  ): Map[String, DataConfidentiality] =
    schema.fields.map(field => field.name -> field.confidentiality).toMap

  private def _admin_form_fields(
    defaults: Vector[(String, String)],
    values: Map[String, String]
  ): Vector[(String, String)] = {
    val userValues = values.filterNot { case (key, _) => key == "error" || key.startsWith("error.") }
    val defaultKeys = defaults.map(_._1).toSet
    defaults.map {
      case (key, value) => key -> userValues.getOrElse(key, value)
    } ++ userValues.filterNot { case (key, _) => defaultKeys.contains(key) }.toVector.sortBy(_._1)
  }

  private def _admin_new_fields_value(values: Map[String, String]): String =
    values.getOrElse(
      "fields",
      _visible_form_values(values)
        .toVector
        .sortBy(_._1)
        .map { case (key, value) => s"${key}=${value}" }
        .mkString("\n")
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

  private def _is_hidden_form_context_key(key: String): Boolean =
    isHiddenFormContextKey(key)

  private def _hidden_form_context_values(values: Map[String, String]): Vector[(String, String)] =
    values.toVector
      .filter { case (key, value) => _is_hidden_form_context_key(key) && value.nonEmpty }
      .sortBy(_._1)

  private def _visible_form_values(values: Map[String, String]): Map[String, String] =
    values.filterNot { case (key, _) =>
      key == "error" || key.startsWith("error.") || _is_hidden_form_context_key(key)
    }

  private def _hidden_form_context_inputs(values: Map[String, String]): String =
    _hidden_form_context_values(values).map { case (key, value) =>
      s"""<input type="hidden" name="${_escape(key)}" value="${_escape(value)}">"""
    }.mkString("\n")

  private def _hidden_form_context_query_suffix(values: Map[String, String]): String =
    _hidden_form_context_values(values) match {
      case xs if xs.isEmpty => ""
      case xs =>
        xs.map { case (key, value) =>
          s"${_escape_query(key)}=${_escape_query(value)}"
        }.mkString("?", "&", "")
    }

  private def _admin_new_controls(
    schemaFields: Vector[WebSchemaResolver.ResolvedWebField],
    values: Map[String, String],
    fieldsId: String,
    placeholder: String,
    validation: Option[FormValidationResult] = None
  ): String =
    if (schemaFields.isEmpty)
      _admin_fields_textarea(values, fieldsId, placeholder, "Use one name=value pair per line.")
    else {
      val userValues = _visible_form_values(values).filterNot { case (key, _) => key == "fields" }
      val schemaNames = schemaFields.map(_.name).toSet
      val validationMessages = _validation_messages_by_field(validation)
      val controls = schemaFields.map { field =>
        val key = field.name
        val value = userValues.getOrElse(key, "")
        _admin_field_control(key, s"new-field-${NamingConventions.toNormalizedSegment(key)}", value, Some(field), validationMessages.getOrElse(key, Vector.empty))
      }.mkString("\n")
      val extras = userValues.filterNot { case (key, _) => schemaNames.contains(key) }
      val extraText = extras.toVector.sortBy(_._1).map { case (key, value) => s"${key}=${value}" }.mkString("\n")
      val extra =
        s"""<div class="mb-3">
           |  <label class="form-label" for="${_escape(fieldsId)}">Additional fields</label>
           |  <textarea class="form-control" id="${_escape(fieldsId)}" name="fields" rows="3">${_escape(values.getOrElse("fields", extraText))}</textarea>
           |  <div class="form-text">Use one name=value pair per line for extension fields.</div>
           |</div>""".stripMargin
      s"${controls}\n${extra}"
    }

  private def _admin_record_controls(
    schemaFields: Vector[WebSchemaResolver.ResolvedWebField],
    defaults: Vector[(String, String)],
    values: Map[String, String],
    idPrefix: String,
    validation: Option[FormValidationResult] = None,
    includeExtensionFields: Boolean = true
  ): String = {
    val submittedValues =
      if (includeExtensionFields)
        _visible_form_values(values)
      else {
        val schemaNames = schemaFields.map(_.name).toSet
        _visible_form_values(values).filter { case (key, _) => _is_admin_schema_field(schemaNames, key) }
      }
    val fields = _admin_resolved_schema_ordered_fields(schemaFields, _admin_form_fields(defaults, submittedValues))
    val validationMessages = _validation_messages_by_field(validation)
    fields.map {
      case (Some(field), _, value) =>
        _admin_field_control(field.name, s"${idPrefix}-${NamingConventions.toNormalizedSegment(field.name)}", value, Some(field), validationMessages.getOrElse(field.name, Vector.empty))
      case (None, key, value) =>
        _admin_field_control(key, s"${idPrefix}-${NamingConventions.toNormalizedSegment(key)}", value, None)
    }.mkString("\n")
  }

  private def _admin_field_control(
    name: String,
    id: String,
    value: String,
    webField: Option[WebSchemaResolver.ResolvedWebField],
    validationMessages: Vector[FormValidationMessage] = Vector.empty
  ): String = {
    val descriptor = webField.map(_.asControl)
    val inputType = descriptor.flatMap(_.controlType).getOrElse("text")
    val required = if (descriptor.flatMap(_.required).getOrElse(false)) " required" else ""
    _operation_parameter_control(
      name,
      id,
      inputType,
      value,
      required,
      webField.flatMap(_.help).getOrElse("Admin field"),
      descriptor,
      readonly = webField.exists(_.readonly),
      placeholder = webField.flatMap(_.placeholder),
      label = webField.flatMap(_.label),
      validationMessages = validationMessages
    )
  }

  private def _admin_resolved_schema_ordered_fields(
    schemaFields: Vector[WebSchemaResolver.ResolvedWebField],
    fields: Vector[(String, String)]
  ): Vector[(Option[WebSchemaResolver.ResolvedWebField], String, String)] = {
    val values = fields.toMap
    val schemaNames = schemaFields.map(_.name).toSet
    val schemaRows = schemaFields.map(field => (Some(field), field.name, _admin_display_value(field.name, schemaNames, values)))
    val extensionRows = fields
      .filterNot { case (key, _) => _is_admin_schema_field(schemaNames, key) || _is_derived_backing_field(schemaNames, key) }
      .distinctBy(_._1)
      .sortBy(_._1)
      .map { case (key, value) => (None, key, value) }
    schemaRows ++ extensionRows
  }

  private def _is_derived_backing_field(
    schemaNames: Set[String],
    key: String
  ): Boolean =
    (key == "title" && schemaNames.contains("subject")) ||
      (key == "content" && schemaNames.contains("body"))

  private def _derived_backing_value(
    key: String,
    schemaNames: Set[String],
    values: Map[String, String]
  ): Option[String] =
    key match {
      case "subject" if schemaNames.contains("subject") => values.get("title")
      case "body" if schemaNames.contains("body") => values.get("content")
      case _ => None
    }

  private def _admin_schema_ordered_fields(
    schemaFields: Vector[String],
    fields: Vector[(String, String)]
  ): Vector[(String, String)] = {
    val values = fields.toMap
    val schemaNames = schemaFields.toSet
    val schemaRows = schemaFields.map(key => key -> _admin_display_value(key, schemaNames, values))
    val extensionRows = fields.filterNot { case (key, _) => _is_admin_schema_field(schemaNames, key) || _is_derived_backing_field(schemaNames, key) }
    (schemaRows ++ extensionRows).distinctBy(_._1)
  }

  private def _is_admin_schema_field(
    schemaNames: Set[String],
    key: String
  ): Boolean =
    schemaNames.contains(key) ||
      schemaNames.exists(name => NamingConventions.equivalentByNormalized(name, key))

  private def _admin_display_value(
    key: String,
    schemaNames: Set[String],
    values: Map[String, String]
  ): String =
    _admin_value_by_name(values, key)
      .orElse(_derived_backing_value(key, schemaNames, values))
      .getOrElse("")

  private def _admin_value_by_name(
    values: Map[String, String],
    key: String
  ): Option[String] =
    values.get(key).orElse {
      values.collectFirst {
        case (candidate, value) if NamingConventions.equivalentByNormalized(candidate, key) => value
      }
    }

  private def _admin_schema_field_names(
    adminFields: Vector[WebDescriptor.AdminField],
    fallback: Vector[String]
  ): Vector[String] =
    adminFields.map(_.name) match {
      case xs if xs.nonEmpty => xs
      case _ => fallback
    }

  private def _admin_field_controls(
    adminFields: Vector[WebDescriptor.AdminField]
  ): Map[String, WebDescriptor.FormControl] =
    adminFields.map(x => x.name -> x.control).toMap

  private def _admin_fields_textarea(
    values: Map[String, String],
    fieldsId: String,
    placeholder: String,
    help: String
  ): String =
    s"""<div class="mb-3">
       |  <label class="form-label" for="${_escape(fieldsId)}">Fields</label>
       |  <textarea class="form-control" id="${_escape(fieldsId)}" name="fields" rows="8" placeholder="${placeholder}">${_escape(_admin_new_fields_value(values))}</textarea>
       |  <div class="form-text">${_escape(help)}</div>
       |</div>""".stripMargin

  private def _operation_form_controls(
    context: OperationWebSchemaContext,
    values: Map[String, String],
    validation: Option[FormValidationResult] = None
  ): String = {
    val webSchema = context.webSchema
    val fields = webSchema.fields
    val bindingControls = _operation_binding_controls(context)
    if (fields.isEmpty)
      s"""${_operation_form_fields_textarea(_visible_form_values(values).filterNot { case (key, _) => _is_operation_binding_only_field(context, key) }, "Fields")}
         |${bindingControls}""".stripMargin
    else {
      val fieldNames = fields.map(_.name).toSet
      val validationMessages = _validation_messages_by_field(validation)
      val controls = fields.map { field =>
        val name = field.name
        val id = s"field-${NamingConventions.toNormalizedSegment(name)}"
        val value = values.getOrElse(name, "")
        val descriptor = Some(field.asControl)
        val required = if (field.required) " required" else ""
        val help = _web_schema_field_help(field)
        val fieldMessages = validationMessages.getOrElse(name, Vector.empty)
        _operation_parameter_control(
          name,
          id,
          field.controlType,
          value,
          required,
          help,
          descriptor,
          readonly = field.readonly,
          placeholder = field.placeholder,
          label = field.label,
          validationMessages = fieldMessages
        )
      }.mkString("\n")
      val extraValues = _visible_form_values(values).filterNot { case (key, _) =>
        fieldNames.contains(key) || _is_operation_binding_only_field(context, key)
      }
      val extra =
        if (extraValues.isEmpty)
          _operation_form_fields_textarea(Map.empty, "Additional fields", rows = 3)
        else
          _operation_form_fields_textarea(extraValues, "Additional fields", rows = 3)
      s"""${controls}
         |${bindingControls}
         |${extra}""".stripMargin
    }
  }

  private def _operation_binding_controls(
    context: OperationWebSchemaContext
  ): String =
    Vector(
      context.imageBinding.filter(_.createsAttachment).map(binding => _operation_image_attachment_controls(context, binding)),
      context.associationBinding.filter(_.isAutomaticCreate).map(binding => _operation_association_binding_controls(context, binding))
    ).flatten.mkString("\n")

  private def _operation_image_attachment_controls(
    context: OperationWebSchemaContext,
    binding: CmlOperationImageBinding
  ): String = {
    val schemaFields = context.webSchema.fields.map(_.name).toSet
    val roles = if (binding.roles.nonEmpty) binding.roles else Vector("primary", "cover", "thumbnail", "gallery", "inline")
    val options = roles.map(role => s"""<option value="${_escape(role)}">""").mkString
    val rows = (0 until 3).map { index =>
      val base = s"imageAttachments.${index}"
      val role =
        if (!schemaFields.contains(s"${base}.role"))
          s"""<div class="col-md-2">
             |  <label class="form-label" for="operationImageAttachmentRole${index}">Role</label>
             |  <input class="form-control" id="operationImageAttachmentRole${index}" name="${base}.role" list="operationImageAttachmentRoleOptions">
             |</div>""".stripMargin
        else
          ""
      val blobId =
        if (binding.acceptsExistingBlobId && !schemaFields.contains(s"${base}.blobId"))
          s"""<div class="col-md-3">
             |  <label class="form-label" for="operationImageAttachmentBlobId${index}">Existing Blob id</label>
             |  <input class="form-control" id="operationImageAttachmentBlobId${index}" name="${base}.blobId">
             |</div>""".stripMargin
        else
          ""
      val file =
        if (binding.acceptsUpload && !schemaFields.contains(s"${base}.file"))
          s"""<div class="col-md-4">
             |  <label class="form-label" for="operationImageAttachmentFile${index}">Upload image</label>
             |  <input class="form-control" id="operationImageAttachmentFile${index}" name="${base}.file" type="file" accept="image/*">
             |</div>""".stripMargin
        else
          ""
      val sortOrder =
        if (!schemaFields.contains(s"${base}.sortOrder"))
          s"""<div class="col-md-2">
             |  <label class="form-label" for="operationImageAttachmentSort${index}">Sort</label>
             |  <input class="form-control" id="operationImageAttachmentSort${index}" name="${base}.sortOrder">
             |</div>""".stripMargin
        else
          ""
      val columns = Vector(role, blobId, file, sortOrder).filter(_.trim.nonEmpty)
      if (columns.isEmpty)
        ""
      else
        s"""<div class="row g-2 align-items-end mb-2">
           |  ${columns.mkString("\n")}
           |</div>""".stripMargin
    }.filter(_.trim.nonEmpty).mkString("\n")
    if (rows.trim.isEmpty)
      ""
    else
      s"""<section class="border rounded p-3 mb-3">
         |  <h3 class="h6">Image Attachments</h3>
         |  ${rows}
         |  <datalist id="operationImageAttachmentRoleOptions">${options}</datalist>
         |</section>""".stripMargin
  }

  private def _operation_association_binding_controls(
    context: OperationWebSchemaContext,
    binding: CmlOperationAssociationBinding
  ): String = {
    val schemaFields = context.webSchema.fields.map(_.name).toSet
    val sourceFields =
      if (binding.sourceEntityIdMode == CmlOperationAssociationBinding.SourceEntityIdModeParameter)
        binding.sourceEntityIdParameters
      else
        Vector.empty
    val targetFields = binding.targetIdParameters
    val sortFields = binding.sortOrderParameters
    val rows = (sourceFields ++ targetFields ++ sortFields).distinct.filterNot(schemaFields.contains).map { name =>
      val label =
        if (targetFields.contains(name)) s"${_humanize_field_name(name)} Target id"
        else if (sourceFields.contains(name)) s"${_humanize_field_name(name)} Source id"
        else _humanize_field_name(name)
      s"""<div class="col-md-4">
         |  <label class="form-label" for="operationAssociation${_escape(NamingConventions.toNormalizedSegment(name))}">${_escape(label)}</label>
         |  <input class="form-control" id="operationAssociation${_escape(NamingConventions.toNormalizedSegment(name))}" name="${_escape(name)}">
         |</div>""".stripMargin
    }.mkString("\n")
    if (rows.isEmpty)
      ""
    else
      s"""<section class="border rounded p-3 mb-3">
         |  <h3 class="h6">Associations</h3>
         |  <div class="row g-2 align-items-end">
         |    ${rows}
         |  </div>
         |</section>""".stripMargin
  }

  private def _validation_messages_by_field(
    validation: Option[FormValidationResult]
  ): Map[String, Vector[FormValidationMessage]] =
    validation.toVector
      .flatMap(result => result.errors ++ result.warnings)
      .flatMap(message => message.field.map(_ -> message))
      .groupMap(_._1)(_._2)

  private def _resolve_operation_web_schema_context(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    webDescriptor: WebDescriptor
  ): Option[OperationWebSchemaContext] =
    for {
      component <- _find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(x => NamingConventions.equivalentByNormalized(x.name, serviceName))
      operation <- service.operations.operations.find(x => NamingConventions.equivalentByNormalized(x.name, operationName))
      context <- {
        val selectorCandidates = _operation_selector_candidates(component, componentName, service.name, operation.name)
        val resolvedSelector = selectorCandidates.find(selector =>
          webDescriptor.form.contains(selector) ||
            webDescriptor.exposureOf(selector) != WebDescriptor.Exposure.Internal
        ).orElse(selectorCandidates.headOption).getOrElse(_operation_selector(component.name, service.name, operation.name))
        if (!webDescriptor.isFormEnabled(resolvedSelector))
          None
        else {
          val componentPath = NamingConventions.toNormalizedSegment(componentName)
          val servicePath = NamingConventions.toNormalizedSegment(service.name)
          val operationPath = NamingConventions.toNormalizedSegment(operation.name)
          val formDescriptor = webDescriptor.form.get(resolvedSelector)
          val adminFields = webDescriptor.adminOperationFields(componentPath, "aggregate", servicePath, operationPath)
          val descriptorControls =
            if (formDescriptor.exists(_.controls.nonEmpty))
              formDescriptor.map(_.controls).getOrElse(Map.empty)
            else
              _admin_field_controls(adminFields)
          val operationParameters = operation.specification.request.parameters.toVector
          val cmlParameters =
            if (operationParameters.nonEmpty)
              Vector.empty
            else
              _cml_operation_parameters(component, service.name, operation.name)
          val webSchema = WebSchemaResolver.resolveOperationControls(
            resolvedSelector,
            operationParameters ++ cmlParameters,
            descriptorControls
          )
          Some(OperationWebSchemaContext(
            component,
            service.name,
            operation.name,
            componentPath,
            servicePath,
            operationPath,
            webSchema,
            associationBinding = _operation_association_binding(component, operation),
            imageBinding = _operation_image_binding(component, operation)
          ))
        }
      }
    } yield
      context

  private def _operation_association_binding(
    component: Component,
    operation: org.goldenport.protocol.spec.OperationDefinition
  ): Option[CmlOperationAssociationBinding] =
    operation match {
      case x: AssociationBindingOperationDefinition =>
        Some(x.associationBinding)
      case _ =>
        component.operationDefinitions
          .find(definition => NamingConventions.equivalentByNormalized(definition.name, operation.name))
          .flatMap(_.associationBinding)
    }

  private def _operation_image_binding(
    component: Component,
    operation: org.goldenport.protocol.spec.OperationDefinition
  ): Option[CmlOperationImageBinding] =
    operation match {
      case x: ImageBindingOperationDefinition =>
        Some(x.imageBinding)
      case _ =>
        component.operationDefinitions
          .find(definition => NamingConventions.equivalentByNormalized(definition.name, operation.name))
          .flatMap(_.imageBinding)
    }

  private def _operation_selector_candidates(
    component: Component,
    requestedComponentName: String,
    serviceName: String,
    operationName: String
  ): Vector[String] =
    (Vector(requestedComponentName, component.name) ++
      component.artifactMetadata.toVector.flatMap(m => m.component.toVector :+ m.name))
      .foldLeft(Vector.empty[String]) { (z, componentCandidate) =>
        val selector = _operation_selector(componentCandidate, serviceName, operationName)
        if (z.contains(selector))
          z
        else
          z :+ selector
      }

  private def _cml_operation_parameters(
    component: Component,
    serviceName: String,
    operationName: String
  ): Vector[ParameterDefinition] =
    component.operationDefinitions.find { definition =>
      NamingConventions.equivalentByNormalized(definition.name, operationName)
    }.toVector.flatMap { definition =>
      definition.parameters.map { field =>
        ParameterDefinition(
          content = field.label.
            map(label => BaseContent.Builder(field.name).label(label).build()).
            getOrElse(BaseContent.Builder(field.name).label(_humanize_field_name(field.name)).build()),
          kind = ParameterDefinition.Kind.Argument,
          domain = ValueDomain(
            datatype = _cml_operation_datatype(field.datatype),
            multiplicity = _cml_operation_multiplicity(field.multiplicity)
          ),
          web = org.goldenport.schema.WebColumn(
            controlType = field.controlType.orElse(_cml_operation_control_type(field.name, field.datatype)),
            required = field.required,
            placeholder = field.placeholder,
            help = field.help
          )
        )
      }
    }

  private def _humanize_field_name(
    name: String
  ): String = {
    val spaced = name.replace('_', ' ').replace('-', ' ').
      replaceAll("([a-z0-9])([A-Z])", "$1 $2").
      trim
    if (spaced.isEmpty)
      name
    else
      spaced.split("\\s+").map(_.capitalize).mkString(" ")
  }

  private def _cml_operation_control_type(
    name: String,
    datatype: String
  ): Option[String] = {
    val n = name.toLowerCase(java.util.Locale.ROOT)
    val t = Option(datatype).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    if (t == "text" || Vector("body", "content", "description", "comment", "message").exists(n.contains))
      Some("textarea")
    else
      None
  }

  private def _cml_operation_datatype(
    datatype: String
  ): org.goldenport.schema.DataType =
    Option(datatype).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("") match {
      case "boolean" | "bool" => XBoolean
      case "int" | "integer" | "long" | "short" => XInt
      case "datetime" | "date-time" | "timestamp" => XDateTime
      case _ => XString
    }

  private def _cml_operation_multiplicity(
    multiplicity: String
  ): Multiplicity =
    Option(multiplicity).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("") match {
      case "?" | "0..1" | "zeroone" | "zero-one" | "zero_one" => Multiplicity.ZeroOne
      case _ => Multiplicity.One
    }

  private def _operation_form_prefill_values(
    subsystem: Subsystem,
    context: OperationWebSchemaContext,
    values: Map[String, String]
  ): Map[String, String] =
    if (!NamingConventions.equivalentByNormalized(context.serviceName, "aggregate"))
      values
    else
      values.get("id").filter(_.nonEmpty) match {
        case Some(id) =>
          _aggregate_name_for_operation(context.component, context.operationName)
            .flatMap { aggregateName =>
              _admin_operation_value_lines(
                subsystem,
                "/admin/aggregate/read",
                Record.data(
                  "component" -> context.componentPath,
                  "aggregate" -> NamingConventions.toNormalizedSegment(aggregateName),
                  "id" -> id
                )
              ).map(lines => _field_lines(lines.mkString("\n")).toMap)
            }
            .map(prefill => prefill ++ values)
            .getOrElse(values)
        case None =>
          values
      }

  private def _aggregate_name_for_operation(
    component: Component,
    operationName: String
  ): Option[String] = {
    val operationKey = NamingConventions.toNormalizedSegment(operationName)
    component.aggregateDefinitions.find { definition =>
      val names = definition.creates.map(_.name) ++ definition.commands.map(_.name)
      names.exists(name => NamingConventions.equivalentByNormalized(name, operationKey))
    }.map(_.name)
  }

  private def _form_definition_json(
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    navigation: FormDefinitionNavigation,
    operationBindings: Option[OperationWebSchemaContext] = None
  ): Page = {
    val base = Vector(
      "selector" -> Json.fromString(webSchema.selector),
      "surface" -> Json.fromString(webSchema.surface.name),
      "source" -> Json.fromString(webSchema.source.toString),
      "mode" -> Json.fromString(navigation.mode),
      "method" -> Json.fromString(navigation.method),
      "submitPath" -> Json.fromString(navigation.submitPath),
      "htmlPath" -> Json.fromString(navigation.htmlPath),
      "actions" -> Json.arr(navigation.actions.map(_form_definition_action_json)*),
      "fields" -> Json.arr((webSchema.fields.map(_web_field_json) ++ operationBindings.toVector.flatMap(_operation_binding_field_jsons))*)
    )
    val bindings = operationBindings.toVector.map(context => "bindings" -> _operation_bindings_json(context))
    Page(Json.obj((base ++ bindings)*).noSpaces)
  }

  private def _form_definition_action_json(
    action: FormDefinitionAction
  ): Json =
    Json.obj(
      "name" -> Json.fromString(action.name),
      "method" -> Json.fromString(action.method),
      "path" -> Json.fromString(action.path)
    )

  private def _validate_form(
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    values: Map[String, String]
  ): FormValidationResult = {
    val fieldNames = webSchema.fields.map(_.name).toSet
    val errors = webSchema.fields.flatMap { field =>
      _validate_field(field, values.getOrElse(field.name, ""))
    }
    val warnings = values.toVector
      .filterNot { case (key, _) => key == "fields" || fieldNames.contains(key) }
      .sortBy(_._1)
      .map { case (key, _) =>
        FormValidationMessage(Some(key), "unknown-field", s"${key} is not defined in the form schema.")
      }
    FormValidationResult(webSchema, values, errors, warnings)
  }

  private def _validate_operation_form(
    context: OperationWebSchemaContext,
    values: Map[String, String]
  ): FormValidationResult =
    _validate_form(
      context.webSchema,
      values.filterNot { case (key, _) => _is_operation_binding_only_field(context, key) }
    )

  private def _is_operation_binding_only_field(
    context: OperationWebSchemaContext,
    key: String
  ): Boolean =
    _is_operation_binding_field(context, key) &&
      !context.webSchema.fields.exists(_.name == key)

  private def _is_operation_binding_field(
    context: OperationWebSchemaContext,
    key: String
  ): Boolean =
    context.imageBinding.exists(_ => _is_image_binding_field(key)) ||
      context.associationBinding.exists(binding => _is_association_binding_field(binding, key))

  private def _is_image_binding_field(key: String): Boolean =
    key.startsWith("imageAttachments.") ||
      key.startsWith("blob.") ||
      key.startsWith("blobId.")

  private def _is_association_binding_field(
    binding: CmlOperationAssociationBinding,
    key: String
  ): Boolean =
    (binding.parameters ++
      binding.sourceEntityIdParameters ++
      binding.targetIdParameters ++
      binding.sortOrderParameters).contains(key) ||
      binding.targetIdParameters.exists(p => key == s"${p}.sortOrder")

  private def _operation_binding_field_jsons(
    context: OperationWebSchemaContext
  ): Vector[Json] = {
    val schemaFields = context.webSchema.fields.map(_.name).toSet
    (context.imageBinding.filter(_.createsAttachment).toVector.flatMap(_operation_image_binding_field_jsons) ++
      context.associationBinding.filter(_.isAutomaticCreate).toVector.flatMap(_operation_association_binding_field_jsons)).
      filterNot(json => json.hcursor.downField("name").as[String].toOption.exists(schemaFields.contains))
  }

  private def _operation_image_binding_field_jsons(
    binding: CmlOperationImageBinding
  ): Vector[Json] = {
    val roles = if (binding.roles.nonEmpty) binding.roles else Vector("primary", "cover", "thumbnail", "gallery", "inline")
    (0 until 3).toVector.flatMap { index =>
      val base = s"imageAttachments.${index}"
      Vector(
        Some(_binding_field_json(s"${base}.role", "Image role", "text", "image", "role", values = roles)),
        Option.when(binding.acceptsExistingBlobId)(_binding_field_json(s"${base}.blobId", "Existing Blob id", "text", "image", "existingBlobId")),
        Option.when(binding.acceptsUpload)(_binding_field_json(s"${base}.file", "Upload image", "file", "image", "upload")),
        Some(_binding_field_json(s"${base}.sortOrder", "Sort order", "number", "image", "sortOrder"))
      ).flatten
    }
  }

  private def _operation_association_binding_field_jsons(
    binding: CmlOperationAssociationBinding
  ): Vector[Json] = {
    val sourceFields =
      if (binding.sourceEntityIdMode == CmlOperationAssociationBinding.SourceEntityIdModeParameter)
        binding.sourceEntityIdParameters
      else
        Vector.empty
    val targets = binding.targetIdParameters.map(name =>
      _binding_field_json(name, _humanize_field_name(name), "text", "association", "targetEntityId")
    )
    val sources = sourceFields.map(name =>
      _binding_field_json(name, _humanize_field_name(name), "text", "association", "sourceEntityId")
    )
    val sorts = binding.sortOrderParameters.map(name =>
      _binding_field_json(name, _humanize_field_name(name), "number", "association", "sortOrder")
    )
    (sources ++ targets ++ sorts).distinct
  }

  private def _binding_field_json(
    name: String,
    label: String,
    fieldType: String,
    bindingKind: String,
    bindingMode: String,
    values: Vector[String] = Vector.empty
  ): Json =
    Json.obj(
      "name" -> Json.fromString(name),
      "label" -> Json.fromString(label),
      "type" -> Json.fromString(fieldType),
      "required" -> Json.fromBoolean(false),
      "virtual" -> Json.fromBoolean(true),
      "bindingKind" -> Json.fromString(bindingKind),
      "bindingMode" -> Json.fromString(bindingMode),
      "values" -> Json.arr(values.map(Json.fromString)*)
    )

  private def _operation_bindings_json(
    context: OperationWebSchemaContext
  ): Json =
    Json.obj(
      "imageBinding" -> context.imageBinding.map(_image_binding_json).getOrElse(Json.Null),
      "associationBinding" -> context.associationBinding.map(_association_binding_json).getOrElse(Json.Null)
    )

  private def _image_binding_json(
    binding: CmlOperationImageBinding
  ): Json =
    Json.obj(
      "mediaKind" -> Json.fromString(binding.mediaKind),
      "acceptsUpload" -> Json.fromBoolean(binding.acceptsUpload),
      "acceptsExistingBlobId" -> Json.fromBoolean(binding.acceptsExistingBlobId),
      "acceptsArchiveBlobId" -> Json.fromBoolean(binding.acceptsArchiveBlobId),
      "createsAttachment" -> Json.fromBoolean(binding.createsAttachment),
      "roles" -> Json.arr(binding.roles.map(Json.fromString)*),
      "parameters" -> Json.arr(binding.parameters.map(Json.fromString)*)
    )

  private def _association_binding_json(
    binding: CmlOperationAssociationBinding
  ): Json =
    Json.obj(
      "domain" -> Json.fromString(binding.domain),
      "targetKind" -> Json.fromString(binding.targetKind),
      "createsAssociation" -> Json.fromBoolean(binding.createsAssociation),
      "roles" -> Json.arr(binding.roles.map(Json.fromString)*),
      "parameters" -> Json.arr(binding.parameters.map(Json.fromString)*),
      "sourceEntityIdMode" -> Json.fromString(binding.sourceEntityIdMode),
      "sourceEntityIdParameters" -> Json.arr(binding.sourceEntityIdParameters.map(Json.fromString)*),
      "targetIdParameters" -> Json.arr(binding.targetIdParameters.map(Json.fromString)*),
      "sortOrderParameters" -> Json.arr(binding.sortOrderParameters.map(Json.fromString)*)
    )

  private def _validate_field(
    field: WebSchemaResolver.ResolvedWebField,
    rawValue: String
  ): Vector[FormValidationMessage] = {
    val value = rawValue.trim
    val label = field.label.getOrElse(field.name)
    val isFrameworkField = field.asControl.hidden || field.asControl.system
    val requiredError =
      if (field.required && value.isEmpty && !isFrameworkField)
        Vector(FormValidationMessage(Some(field.name), "required", s"${label} is required."))
      else
        Vector.empty
    if (value.isEmpty)
      requiredError
    else
      requiredError ++
        _validate_multiplicity(field, value) ++
        _validate_field_values(field, value) ++
        _validate_datatype(field, value) ++
        _validate_hints(field, value)
  }

  private def _validate_hints(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] =
    _form_field_values(field, value).flatMap { x =>
      _validate_hint_value(field, x)
    }

  private def _validate_hint_value(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] = {
    val hints = field.validation
    val label = field.label.getOrElse(field.name)
    val length = value.length
    val lengthErrors =
      Vector(
        hints.minLength.filter(length < _).map(min => FormValidationMessage(Some(field.name), "min-length", s"${label} must be at least ${min} characters.")),
        hints.maxLength.filter(length > _).map(max => FormValidationMessage(Some(field.name), "max-length", s"${label} must be at most ${max} characters."))
      ).flatten
    val patternErrors =
      hints.pattern.toVector.flatMap { pattern =>
        if (scala.util.Try(value.matches(pattern)).getOrElse(false))
          Vector.empty
        else
          Vector(FormValidationMessage(Some(field.name), "pattern", s"${label} does not match the required pattern."))
      }
    val numericErrors =
      if (hints.min.isEmpty && hints.max.isEmpty)
        Vector.empty
      else
        scala.util.Try(BigDecimal(value)).toOption match {
          case Some(n) =>
            Vector(
              hints.min.filter(n < _).map(min => FormValidationMessage(Some(field.name), "min", s"${label} must be greater than or equal to ${min}.")),
              hints.max.filter(n > _).map(max => FormValidationMessage(Some(field.name), "max", s"${label} must be less than or equal to ${max}."))
            ).flatten
          case None =>
            Vector.empty
        }
    lengthErrors ++ patternErrors ++ numericErrors
  }

  private def _validate_multiplicity(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] = {
    val multiplicity = field.multiplicity.map(_.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    val values =
      if (field.multiple || field.asControl.values.nonEmpty || field.controlType == "select")
        _split_form_field_values(value)
      else
        Vector(value)
    if (!field.multiple && _is_single_multiplicity(multiplicity) && values.size > 1)
      Vector(FormValidationMessage(Some(field.name), "multiplicity", s"${field.label.getOrElse(field.name)} accepts only one value."))
    else
      Vector.empty
  }

  private def _validate_field_values(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] = {
    val allowed = field.asControl.values.toSet
    if (allowed.isEmpty)
      Vector.empty
    else
      _form_field_values(field, value)
        .filterNot(allowed.contains)
        .map(x => FormValidationMessage(Some(field.name), "invalid-value", s"${x} is not an allowed value for ${field.label.getOrElse(field.name)}."))
  }

  private def _validate_datatype(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] =
    _form_field_values(field, value).flatMap { x =>
      _validate_datatype_value(field, x)
    }

  private def _validate_datatype_value(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Option[FormValidationMessage] = {
    val datatype = field.dataType.map(_.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    val controlType = field.controlType.toLowerCase(java.util.Locale.ROOT)
    val label = field.label.getOrElse(field.name)
    def error(code: String, expected: String): Option[FormValidationMessage] =
      Some(FormValidationMessage(Some(field.name), code, s"${label} must be ${expected}."))
    if (_is_boolean_type(datatype, controlType) && !_is_boolean_value(value))
      error("datatype", "a boolean value")
    else if (_is_integer_type(datatype) && !value.toLongOption.isDefined)
      error("datatype", "an integer")
    else if (_is_number_type(datatype, controlType) && !scala.util.Try(BigDecimal(value)).isSuccess)
      error("datatype", "a number")
    else if (_is_date_type(datatype, controlType) && !scala.util.Try(java.time.LocalDate.parse(value)).isSuccess)
      error("datatype", "a date value")
    else if (_is_datetime_type(datatype, controlType) && !_is_datetime_value(value))
      error("datatype", "a datetime value")
    else
      None
  }

  private def _form_field_values(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[String] =
    if (field.multiple)
      _split_form_field_values(value)
    else
      Vector(value)

  private def _split_form_field_values(
    value: String
  ): Vector[String] =
    value.split("[,\n]").toVector.map(_.trim).filter(_.nonEmpty)

  private def _is_single_multiplicity(
    multiplicity: String
  ): Boolean =
    multiplicity.contains("one") && !multiplicity.contains("many")

  private def _is_boolean_type(
    datatype: String,
    controlType: String
  ): Boolean =
    datatype.contains("bool") || controlType == "checkbox"

  private def _is_boolean_value(
    value: String
  ): Boolean =
    Set("true", "false", "yes", "no", "on", "off", "1", "0").contains(value.toLowerCase(java.util.Locale.ROOT))

  private def _is_integer_type(
    datatype: String
  ): Boolean =
    datatype.contains("int") || datatype.contains("long")

  private def _is_number_type(
    datatype: String,
    controlType: String
  ): Boolean =
    controlType == "number" || datatype.contains("decimal") || datatype.contains("double") || datatype.contains("float") || datatype.contains("number")

  private def _is_date_type(
    datatype: String,
    controlType: String
  ): Boolean =
    (controlType == "date" || datatype.contains("date")) && !_is_datetime_type(datatype, controlType)

  private def _is_datetime_type(
    datatype: String,
    controlType: String
  ): Boolean =
    controlType == "datetime-local" || datatype.contains("datetime") || datatype.contains("timestamp")

  private def _is_datetime_value(
    value: String
  ): Boolean =
    scala.util.Try(java.time.LocalDateTime.parse(value)).isSuccess ||
      scala.util.Try(java.time.OffsetDateTime.parse(value)).isSuccess

  private def _form_validation_json(
    result: FormValidationResult
  ): Page =
    Page(Json.obj(
      "selector" -> Json.fromString(result.webSchema.selector),
      "surface" -> Json.fromString(result.webSchema.surface.name),
      "valid" -> Json.fromBoolean(result.valid),
      "errors" -> Json.arr(result.errors.map(_form_validation_message_json)*),
      "warnings" -> Json.arr(result.warnings.map(_form_validation_message_json)*),
      "fields" -> Json.arr(result.webSchema.fields.map(_web_field_json)*)
    ).noSpaces)

  private def _form_validation_message_json(
    message: FormValidationMessage
  ): Json =
    Json.obj(
      "field" -> message.field.map(Json.fromString).getOrElse(Json.Null),
      "code" -> Json.fromString(message.code),
      "message" -> Json.fromString(message.message)
    )

  private def _web_field_json(
    field: WebSchemaResolver.ResolvedWebField
  ): Json = {
    val control = field.asControl
    Json.obj(
      "name" -> Json.fromString(field.name),
      "label" -> field.label.map(Json.fromString).getOrElse(Json.Null),
      "type" -> Json.fromString(field.controlType),
      "dataType" -> field.dataType.map(Json.fromString).getOrElse(Json.Null),
      "multiplicity" -> field.multiplicity.map(Json.fromString).getOrElse(Json.Null),
      "required" -> Json.fromBoolean(field.required),
      "readonly" -> Json.fromBoolean(field.readonly),
      "hidden" -> Json.fromBoolean(control.hidden),
      "system" -> Json.fromBoolean(control.system),
      "values" -> Json.arr(control.values.map(Json.fromString)*),
      "multiple" -> Json.fromBoolean(control.multiple),
      "placeholder" -> field.placeholder.map(Json.fromString).getOrElse(Json.Null),
      "help" -> field.help.map(Json.fromString).getOrElse(Json.Null),
      "confidentiality" -> Json.fromString(field.confidentiality.label),
      "validation" -> _web_validation_hints_json(field.validation),
      "source" -> Json.fromString(field.source.toString)
    )
  }

  private def _web_validation_hints_json(
    hints: org.goldenport.schema.WebValidationHints
  ): Json =
    Json.obj(
      "min" -> hints.min.map(Json.fromBigDecimal).getOrElse(Json.Null),
      "max" -> hints.max.map(Json.fromBigDecimal).getOrElse(Json.Null),
      "step" -> hints.step.map(Json.fromBigDecimal).getOrElse(Json.Null),
      "minLength" -> hints.minLength.map(Json.fromInt).getOrElse(Json.Null),
      "maxLength" -> hints.maxLength.map(Json.fromInt).getOrElse(Json.Null),
      "pattern" -> hints.pattern.map(Json.fromString).getOrElse(Json.Null)
    )

  private def _web_schema_field_help(
    field: WebSchemaResolver.ResolvedWebField
  ): String =
    field.help.getOrElse {
      Vector(
        field.dataType.getOrElse("unknown"),
        field.multiplicity.getOrElse("unknown")
      ).mkString("; ")
    }

  private def _operation_parameter_required(
    parameter: org.goldenport.protocol.spec.ParameterDefinition
  ): Boolean =
    !Option(parameter.domain.multiplicity).map(_.toString.toLowerCase).exists(x => x.contains("zero"))

  private def _operation_parameter_input_type(
    parameter: org.goldenport.protocol.spec.ParameterDefinition
  ): String = {
    val name = parameter.name.toLowerCase
    val datatypeName = Option(parameter.domain.datatype).map(_.name).getOrElse("")
    val datatype = datatypeName.toLowerCase(java.util.Locale.ROOT)
    if (_is_blob_datatype(datatypeName)) "file"
    else if (datatype.contains("bool")) "checkbox"
    else if (_operation_parameter_multiline(name, datatype)) "textarea"
    else if (name.contains("password") || name.contains("secret") || name.contains("token")) "password"
    else if (datatype.contains("int") || datatype.contains("long") || datatype.contains("decimal") || datatype.contains("number")) "number"
    else if (datatype.contains("datetime") || datatype.contains("timestamp")) "datetime-local"
    else if (datatype.contains("date")) "date"
    else "text"
  }

  private def _operation_parameter_multiline(
    name: String,
    datatype: String
  ): Boolean =
    datatype.contains("text") ||
      datatype.contains("memo") ||
      datatype.contains("document") ||
      name.contains("body") ||
      name.contains("content") ||
      name.contains("description") ||
      name.contains("comment") ||
      name.contains("message")

  private def _operation_parameter_control(
    name: String,
    id: String,
    inputType: String,
    value: String,
    required: String,
    help: String,
    descriptor: Option[WebDescriptor.FormControl] = None,
    readonly: Boolean = false,
    placeholder: Option[String] = None,
    label: Option[String] = None,
    validationMessages: Vector[FormValidationMessage] = Vector.empty
  ): String = {
    val displayLabel = label.orElse(descriptor.flatMap(_.label)).getOrElse(name)
    val invalidClass = if (validationMessages.nonEmpty) " is-invalid" else ""
    val validationAttr = _validation_attribute_text(descriptor.map(_.validation).getOrElse(org.goldenport.schema.WebValidationHints.empty))
    val feedback =
      if (validationMessages.isEmpty)
        ""
      else
        s"""<div class="invalid-feedback">${_escape(validationMessages.map(_.message).mkString(" "))}</div>"""
    if (descriptor.exists(_.hidden) || inputType == "hidden") {
      s"""<input type="hidden" id="${_escape(id)}" name="${_escape(name)}" value="${_escape(value)}">"""
    } else if (inputType == "select" || descriptor.exists(_.values.nonEmpty)) {
      val multiple = if (descriptor.exists(_.multiple)) " multiple" else ""
      val disabled = if (readonly) " disabled" else ""
      val values = descriptor.toVector.flatMap(_.values)
      val placeholderOption =
        descriptor
          .flatMap(_.placeholder)
          .filter(_.nonEmpty)
          .filter(_ => required.trim.isEmpty && !values.contains(""))
          .map { label =>
            val selected = if (value.isEmpty) " selected" else ""
            s"""<option value=""${selected}>${_escape(label)}</option>"""
          }
          .toVector
      val options = (placeholderOption ++ values.map { candidate =>
        val selected = if (candidate == value) " selected" else ""
        s"""<option value="${_escape(candidate)}"${selected}>${_escape(candidate)}</option>"""
      }).mkString("\n")
      s"""<div class="mb-3">
         |  <label class="form-label" for="${_escape(id)}">${_escape(displayLabel)}</label>
         |  <select class="form-select${invalidClass}" id="${_escape(id)}" name="${_escape(name)}"${required}${multiple}${disabled}${validationAttr}>
         |    ${options}
         |  </select>
         |  ${feedback}
         |  <div class="form-text">${_escape(help)}</div>
         |</div>""".stripMargin
    } else if (inputType == "checkbox") {
      val checked =
        if (Set("true", "on", "1", "yes").contains(value.toLowerCase)) " checked" else ""
      val disabled = if (readonly) " disabled" else ""
      s"""<div class="mb-3 form-check">
         |  <input type="hidden" name="${_escape(name)}" value="false">
         |  <input class="form-check-input${invalidClass}" id="${_escape(id)}" name="${_escape(name)}" type="checkbox" value="true"${checked}${required}${disabled}${validationAttr}>
         |  <label class="form-check-label" for="${_escape(id)}">${_escape(displayLabel)}</label>
         |  ${feedback}
         |  <div class="form-text">${_escape(help)}</div>
         |</div>""".stripMargin
    } else if (inputType == "textarea") {
      val readonlyAttr = if (readonly) " readonly" else ""
      val placeholderAttr = placeholder.map(x => s""" placeholder="${_escape(x)}"""").getOrElse("")
      s"""<div class="mb-3">
         |  <label class="form-label" for="${_escape(id)}">${_escape(displayLabel)}</label>
         |  <textarea class="form-control${invalidClass}" id="${_escape(id)}" name="${_escape(name)}" rows="5"${required}${readonlyAttr}${placeholderAttr}${validationAttr}>${_escape(value)}</textarea>
         |  ${feedback}
         |  <div class="form-text">${_escape(help)}</div>
         |</div>""".stripMargin
    } else if (inputType == "file") {
      val disabled = if (readonly) " disabled" else ""
      s"""<div class="mb-3">
         |  <label class="form-label" for="${_escape(id)}">${_escape(displayLabel)}</label>
         |  <input class="form-control${invalidClass}" id="${_escape(id)}" name="${_escape(name)}" type="file"${required}${disabled}${validationAttr}>
         |  ${feedback}
         |  <div class="form-text">${_escape(help)}</div>
         |</div>""".stripMargin
    } else {
      val readonlyAttr = if (readonly) " readonly" else ""
      val placeholderAttr = placeholder.map(x => s""" placeholder="${_escape(x)}"""").getOrElse("")
      s"""<div class="mb-3">
         |  <label class="form-label" for="${_escape(id)}">${_escape(displayLabel)}</label>
         |  <input class="form-control${invalidClass}" id="${_escape(id)}" name="${_escape(name)}" type="${_escape(inputType)}" value="${_escape(value)}"${required}${readonlyAttr}${placeholderAttr}${validationAttr}>
         |  ${feedback}
         |  <div class="form-text">${_escape(help)}</div>
         |</div>""".stripMargin
    }
  }

  private def _validation_attribute_text(
    hints: org.goldenport.schema.WebValidationHints
  ): String = {
    val attrs = Vector(
      hints.min.map(x => "min" -> x.toString),
      hints.max.map(x => "max" -> x.toString),
      hints.step.map(x => "step" -> x.toString),
      hints.minLength.map(x => "minlength" -> x.toString),
      hints.maxLength.map(x => "maxlength" -> x.toString),
      hints.pattern.map(x => "pattern" -> x)
    ).flatten
    if (attrs.isEmpty)
      ""
    else
      attrs.map { case (key, value) => s""" ${key}="${_escape(value)}"""" }.mkString
  }

  private def _operation_parameter_help(
    parameter: org.goldenport.protocol.spec.ParameterDefinition
  ): String = {
    val kind = parameter.kind.toString
    val datatype = Option(parameter.domain.datatype).map(_.name).getOrElse("unknown")
    val multiplicity = Option(parameter.domain.multiplicity).map(_.toString).getOrElse("unknown")
    s"${kind}; ${datatype}; ${multiplicity}"
  }

  private def _is_blob_datatype(datatype: String): Boolean =
    datatype.trim.equalsIgnoreCase("blob")

  private def _operation_form_fields_textarea(
    values: Map[String, String],
    label: String,
    rows: Int = 6
  ): String = {
    val initialFields = _form_initial_fields(values)
    s"""<div class="mb-3">
       |  <label class="form-label" for="formFields">${_escape(label)}</label>
       |  <textarea class="form-control" id="formFields" name="fields" rows="${rows}" placeholder="name=value&#10;keyword=sample">${initialFields}</textarea>
       |  <div class="form-text">Use one name=value pair per line. Query-style values are also accepted.</div>
       |</div>""".stripMargin
  }

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
         |    <a class="btn btn-primary" href="/form/${_escape(NamingConventions.toNormalizedSegment(properties.componentName))}/${_escape(NamingConventions.toNormalizedSegment(properties.serviceName))}/${_escape(NamingConventions.toNormalizedSegment(properties.operationName))}">Run again</a>
         |    <a class="btn btn-outline-secondary" href="/form/${_escape(NamingConventions.toNormalizedSegment(properties.componentName))}">Operations</a>
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
    val rendered = _render_template(
      template,
      pageProperties,
      properties.tableColumns,
      properties.defaultTableView
    )
    val withJobPanel = _append_default_job_panel(rendered, pageProperties)
    val withDebug = _append_execution_debug_panel(withJobPanel, properties, pageProperties)
    val assetCompletion = _execution_debug_asset_completion(properties, pageProperties)
    if (_is_html_document(template))
      Page(_complete_widget_assets(template, withDebug, assetCompletion))
    else
      Page(StaticFormAppLayout.completeDeclaredAssets(
        _simple_page(
          title = s"${_escape(properties.operationLabel)} Result",
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
    val errorValues = error.map(_structured_error_values).getOrElse(Map.empty)
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
    val rendered = _render_template(template, properties, Map.empty)
    val body = _append_structured_error_panel(rendered, panel)
    if (_is_html_document(template))
      Page(body)
    else
      Page(_simple_page(
        title = s"HTTP ${status}",
        subtitle = _escape(path),
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
      s"""  <p class="mb-0"><strong>Application status:</strong> <code>${_escape(x)}</code></p>"""
    ).getOrElse("")
    val body =
      s"""<section class="alert alert-danger" role="alert">
         |  <h2 class="h5">Request failed</h2>
         |  <p class="mb-2">${_escape(error.message)}</p>
         |  <p class="mb-1"><strong>Status:</strong> <code>${error.status}</code></p>
         |  <p class="mb-1"><strong>Status text:</strong> <code>${_escape(error.statusText)}</code></p>
         |${detailcode}
         |${appstatus}
         |</section>
         |${renderStructuredErrorPanel(error)}""".stripMargin
    Page(_simple_page(
      title = s"HTTP ${error.status}",
      subtitle = _escape(error.path),
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
         |    <pre class="bg-light border rounded p-3"><code>${_escape(error.diagnosticYaml)}</code></pre>
         |  </details>
         |</section>""".stripMargin

  private def _structured_error_values(
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

  private def _append_structured_error_panel(
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

  private def _append_execution_debug_panel(
    html: String,
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): String = {
    val panel = _execution_debug_panel(properties, pageProperties)
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

  private def _append_default_job_panel(
    html: String,
    pageProperties: FormPageProperties
  ): String =
    if (pageProperties.value("result.job.id").isEmpty ||
        html.contains("textus-job-ticket") ||
        html.contains("textus-job-panel") ||
        html.contains("textus-job-actions"))
      html
    else
      html + _render_job_panel(Map("actions" -> "result,await,jobs"), pageProperties)

  private def _execution_debug_panel(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): String =
    if (!_is_development_operation_mode(properties.operationMode) || !_execution_debug_panel_enabled(properties, pageProperties))
      ""
    else {
      val metadata = properties.executionMetadata
      val calltreeHtml = metadata.inlineCallTree.map(_debug_calltree_html).getOrElse("")
      val jobid = metadata.responseJobId.orElse(metadata.debugJobId)
      val executionJobId = metadata.executionJobId.orElse(jobid)
      val sagaId = metadata.sagaId
      val taskId = metadata.executionTaskId
      val joblinks = jobid.map { id =>
        val appHref = pageProperties.value("result.job.href")
        val systemHref = s"/web/system/admin/jobs/${_escape_path_segment(id)}"
        val app = if (appHref.nonEmpty) s"""<a class="btn btn-sm btn-outline-primary" href="${_escape(appHref)}">Application job</a>""" else ""
        s"""<div class="d-flex flex-wrap gap-2 mt-2">${app}<a class="btn btn-sm btn-outline-secondary" href="${_escape(systemHref)}">System debug job</a></div>"""
      }.getOrElse("")
      val arguments = _debug_operation_arguments(pageProperties, properties.fieldConfidentiality)
      val argumentsHtml =
        if (arguments.nonEmpty)
          s"""<pre class="bg-light border rounded p-3"><code>${_escape(_debug_record_pretty(Record.dataAuto(arguments.toVector.sortBy(_._1)*)))}</code></pre>"""
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
           |  <dt class="col-sm-3">Operation</dt><dd class="col-sm-9"><code>${_escape(properties.operationLabel)}</code></dd>
           |  <dt class="col-sm-3">HTTP status</dt><dd class="col-sm-9">${properties.status}</dd>
           |  <dt class="col-sm-3">Mode</dt><dd class="col-sm-9">${_escape(properties.operationMode.name)}</dd>
           |  <dt class="col-sm-3">Saga</dt><dd class="col-sm-9">${_debug_context_value(sagaId)}</dd>
           |  <dt class="col-sm-3">Job</dt><dd class="col-sm-9">${_debug_context_value(executionJobId)}</dd>
           |  <dt class="col-sm-3">Task</dt><dd class="col-sm-9">${_debug_context_value(taskId)}</dd>
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
         |      <pre class="bg-light border rounded p-3"><code>${_escape(_debug_body_pretty(properties.body, properties.fieldConfidentiality).take(12000))}</code></pre>
         |      <h3 class="h6 mt-3">CallTree</h3>
         |      ${effectiveCalltreeHtml}
         |    </div>
         |  </details>
         |</section>""".stripMargin
    }

  private def _debug_context_value(
    value: Option[String]
  ): String =
    value.filter(_.trim.nonEmpty)
      .map(x => s"""<code>${_escape(x)}</code>""")
      .getOrElse("""<span class="text-secondary">none</span>""")

  private def _is_development_operation_mode(
    mode: OperationMode
  ): Boolean =
    mode == OperationMode.Develop || mode == OperationMode.Test

  private def _execution_debug_panel_enabled(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): Boolean =
    pageProperties.value("textus.debug.executionPanel").equalsIgnoreCase("true") ||
      !_execution_metadata_empty(properties.executionMetadata)

  private def _execution_debug_asset_completion(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): StaticFormAppLayout.AssetCompletionOptions = {
    val base = properties.assetCompletion
    if (_execution_debug_calltree_asset_required(properties, pageProperties) &&
        !base.declaredJs.contains(_call_tree_js_asset))
      base.copy(declaredJs = base.declaredJs :+ _call_tree_js_asset)
    else
      base
  }

  private def _execution_debug_calltree_asset_required(
    properties: FormResultProperties,
    pageProperties: FormPageProperties
  ): Boolean =
    _is_development_operation_mode(properties.operationMode) &&
      _execution_debug_panel_enabled(properties, pageProperties) &&
      properties.executionMetadata.inlineCallTree.nonEmpty

  private def _execution_metadata_empty(
    metadata: RuntimeContext.ExecutionMetadata
  ): Boolean =
    metadata.responseJobId.isEmpty &&
      metadata.debugJobId.isEmpty &&
      metadata.inlineCallTree.isEmpty

  private def _debug_body_pretty(
    body: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String = {
    val trimmed = body.trim
    if (trimmed.isEmpty)
      body
    else
      io.circe.parser.parse(trimmed).toOption
      .map(json => _debug_redact_json(_debug_compact_result_debug_json(json), confidentiality).spaces2)
      .getOrElse(_debug_redact_text(body, confidentiality))
  }

  private def _debug_compact_result_debug_json(
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

  private def _debug_operation_arguments(
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
    _visible_form_values(rawvalues).filterNot { case (key, _) =>
      key == "fields" ||
        key == "component" ||
        key == "service" ||
        key == "operation" ||
        key == "textus.debug.executionPanel" ||
        key.startsWith("textus.debug.")
    }.map { case (key, value) => key -> _debug_display_value(key, value, confidentiality) }
  }

  private def _debug_record_pretty(
    record: Record
  ): String =
    _manual_raw_json(_debug_redact_record(record))
      .map(_.spaces2)
      .getOrElse(_debug_redact_record(record).show)

  private def _debug_display_value(
    key: String,
    value: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String =
    if (_is_sensitive_debug_key(key, confidentiality)) "[redacted]" else value

  private def _debug_redact_record(
    record: Record,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record =
    Record.dataAuto(
      record.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) => key -> _debug_redact_value(key, value, confidentiality) }*
    )

  private def _debug_redact_value(
    key: String,
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    if (_is_sensitive_debug_key(key, confidentiality))
      "[redacted]"
    else
      value match {
        case r: Record =>
          _debug_redact_record(r, confidentiality)
        case xs: Seq[?] =>
          xs.map(_debug_redact_nested_value(_, confidentiality))
        case xs: Array[?] =>
          xs.toVector.map(_debug_redact_nested_value(_, confidentiality))
        case m: scala.collection.Map[?, ?] =>
          m.toVector.map {
            case (k, v) =>
              val childKey = Option(k).map(_.toString).getOrElse("")
              childKey -> _debug_redact_value(childKey, v, confidentiality)
          }.toMap
        case _ =>
          value
      }

  private def _debug_redact_nested_value(
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    value match {
      case r: Record =>
        _debug_redact_record(r, confidentiality)
      case xs: Seq[?] =>
        xs.map(_debug_redact_nested_value(_, confidentiality))
      case xs: Array[?] =>
        xs.toVector.map(_debug_redact_nested_value(_, confidentiality))
      case m: scala.collection.Map[?, ?] =>
        m.toVector.map {
          case (k, v) =>
            val childKey = Option(k).map(_.toString).getOrElse("")
            childKey -> _debug_redact_value(childKey, v, confidentiality)
        }.toMap
      case _ =>
        value
    }

  private def _debug_redact_json(
    json: Json,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Json =
    json.arrayOrObject(
      json,
      xs => Json.arr(xs.map(_debug_redact_json(_, confidentiality))*),
      obj => Json.fromJsonObject(JsonObject.fromIterable(
        obj.toIterable.map {
          case (key, value) =>
            key -> (if (_is_sensitive_debug_key(key, confidentiality)) Json.fromString("[redacted]") else _debug_redact_json(value, confidentiality))
        }
      ))
    )

  private def _is_sensitive_debug_key(
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

  private def _debug_redact_text(
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

  private final case class DebugCallTreeEvent(
    kind: String,
    label: String,
    attributes: Map[String, String],
    startedAtNanos: Long,
    endedAtNanos: Option[Long]
  )

  private final case class DebugCallTreeNode(
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

  private final case class DebugCallTreeObservation(
    label: String,
    displayLabel: String,
    kind: String,
    attributes: Map[String, String]
  )

  private final case class DebugCallTreeLine(
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

  private def _debug_calltree_html(
    record: Record
  ): String =
    _debug_calltree_nodes(record) match {
      case Some(nodes) if nodes.nonEmpty =>
        s"""<div class="textus-calltree-tree" data-textus-calltree>
           |  ${_debug_calltree_outline(nodes)}
           |</div>""".stripMargin
      case _ =>
        _debug_calltree_legacy_html(record)
    }

  private def _debug_calltree_legacy_html(
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
       |  ${_debug_calltree_outline(Vector(node))}
       |</div>""".stripMargin
  }

  private def _debug_calltree_nodes(
  record: Record
  ): Option[Vector[DebugCallTreeNode]] = {
    val structured = _debug_calltree_structured_nodes(record)
    if (structured.exists(_.nonEmpty))
      return structured
    val events = _debug_calltree_events(record)
    if (events.isEmpty)
      None
    else {
      val leaves = events.filter(_is_debug_leave_event).groupBy(x => (x.label, x.startedAtNanos))
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
        case (event, index) if event.kind != "enter" && !_is_debug_leave_event(event) =>
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
          case (parentIndex, parent) if parentIndex != index && _debug_calltree_contains(parent, node) =>
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

  private def _is_debug_leave_event(
    event: DebugCallTreeEvent
  ): Boolean =
    event.kind == "leave" || event.kind == "exit"

  private def _debug_calltree_structured_nodes(
    record: Record
  ): Option[Vector[DebugCallTreeNode]] =
    record.asMap.get("calltree") match {
      case Some(xs: Seq[?]) =>
        val nodes = xs.toVector.collect { case r: Record => _debug_calltree_structured_node(r) }.flatten
        if (nodes.nonEmpty) Some(nodes) else None
      case _ =>
        None
    }

  private def _debug_calltree_structured_node(
    record: Record
  ): Option[DebugCallTreeNode] = {
    val label = record.getString("label").orElse(record.getString("name"))
    label.map { label =>
      val topAttrs = _debug_calltree_top_level_attributes(record)
      val legacyAttrs = _debug_calltree_attributes(record.asMap.get("attributes"))
      val attrs = (if (legacyAttrs.nonEmpty) legacyAttrs else topAttrs) ++
        record.getString("kind").filter(_.nonEmpty).map("calltree_kind" -> _).toMap ++
        record.getString("display_label").filter(_.nonEmpty).map("display_label" -> _).toMap
      val enterAttrs = _debug_calltree_attributes(record.asMap.get("enter_attributes"))
      val leaveAttrs = _debug_calltree_attributes(record.asMap.get("leave_attributes"))
      val (children, flowObservations) = _debug_calltree_structured_flow(record)
      val observations = flowObservations ++ (record.asMap.get("observations") match {
        case Some(xs: Seq[?]) =>
          xs.toVector.collect { case r: Record => _debug_calltree_structured_observation(r) }.flatten
        case _ =>
          Vector.empty
      })
      DebugCallTreeNode(
        label,
        attrs,
        enterAttrs,
        leaveAttrs,
        attrs.get("started_at_nanos").flatMap(_to_long_option).getOrElse(0L),
        attrs.get("ended_at_nanos").flatMap(_to_long_option),
        children,
        observations
      )
    }
  }

  private def _debug_calltree_structured_flow(
    record: Record
  ): (Vector[DebugCallTreeNode], Vector[DebugCallTreeObservation]) =
    record.asMap.get("flow").orElse(record.asMap.get("children")) match {
      case Some(xs: Seq[?]) =>
        val records = xs.toVector.collect { case r: Record => r }
        val children = records.filterNot(_debug_calltree_is_observation_record).flatMap(_debug_calltree_structured_node)
        val observations = records.filter(_debug_calltree_is_observation_record).flatMap(_debug_calltree_structured_observation)
        children -> observations
      case _ =>
        Vector.empty -> Vector.empty
    }

  private def _debug_calltree_is_observation_record(
    record: Record
  ): Boolean = {
    val kind = record.getString("kind").getOrElse("").toLowerCase(java.util.Locale.ROOT)
    val label = record.getString("label").orElse(record.getString("name")).getOrElse("").toLowerCase(java.util.Locale.ROOT)
    kind == "metric" || kind == "observation"
  }

  private def _debug_calltree_structured_observation(
    record: Record
  ): Option[DebugCallTreeObservation] =
    record.getString("label").orElse(record.getString("name")).filterNot(_debug_calltree_legacy_io_boundary).map { label =>
      val topAttrs = _debug_calltree_top_level_attributes(record)
      val legacyAttrs = _debug_calltree_attributes(record.asMap.get("attributes"))
      val attrs = (if (legacyAttrs.nonEmpty) legacyAttrs else topAttrs) ++
        record.getString("kind").filter(_.nonEmpty).map("calltree_kind" -> _).toMap ++
        record.getString("display_label").filter(_.nonEmpty).map("display_label" -> _).toMap
      val node = DebugCallTreeNode(label, attrs, Map.empty, Map.empty, 0L, None, Vector.empty)
      DebugCallTreeObservation(
        label,
        _debug_calltree_display_label(node),
        _debug_calltree_node_kind(node),
        _debug_calltree_display_attributes(node, node.attributes)
      )
    }

  private def _debug_calltree_legacy_io_boundary(
    label: String
  ): Boolean = {
    val lower = label.toLowerCase(java.util.Locale.ROOT)
    lower == "io:input" || lower == "io:output"
  }

  private def _debug_calltree_top_level_attributes(
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
      .map { case (key, value) => key -> _debug_calltree_value_string(value) }

  private def _debug_calltree_value_string(
    value: Any
  ): String =
    value match {
      case r: Record => _manual_raw_json(r).map(_.spaces2).getOrElse(r.show)
      case xs: Seq[?] => _manual_raw_json(xs).map(_.spaces2).getOrElse(xs.mkString("[", ", ", "]"))
      case x => Option(x).map(_.toString).getOrElse("")
    }

  private def _debug_calltree_contains(
    parent: DebugCallTreeNode,
    child: DebugCallTreeNode
  ): Boolean = {
    val parentEnd = parent.endedAtNanos.getOrElse(Long.MaxValue)
    val childEnd = child.endedAtNanos.getOrElse(child.startedAtNanos)
    parent.startedAtNanos <= child.startedAtNanos && childEnd <= parentEnd &&
      (parent.startedAtNanos < child.startedAtNanos || parentEnd > childEnd)
  }

  private def _debug_calltree_events(
    record: Record
  ): Vector[DebugCallTreeEvent] =
    record.asMap.get("calltree") match {
      case Some(xs: Seq[?]) =>
        xs.toVector.collect { case r: Record => _debug_calltree_event(r) }.flatten
      case _ =>
        Vector.empty
    }

  private def _debug_calltree_event(
    record: Record
  ): Option[DebugCallTreeEvent] = {
    val attrs = _debug_calltree_attributes(record.asMap.get("attributes")) ++
      record.asMap.get("message").map(x => Map("message" -> x.toString)).getOrElse(Map.empty)
    for {
      kind <- _debug_record_string(record, "kind").map(_.toLowerCase(java.util.Locale.ROOT))
      label <- _debug_record_string(record, "label").filter(_.nonEmpty)
      start <- attrs.get("started_at_nanos")
        .orElse(attrs.get("ended_at_nanos"))
        .orElse(attrs.get("failed_at_nanos"))
        .orElse(attrs.get("sampled_at_nanos"))
        .flatMap(_to_long_option)
    } yield DebugCallTreeEvent(
      kind,
      label,
      attrs,
      start,
      attrs.get("ended_at_nanos")
        .orElse(attrs.get("failed_at_nanos"))
        .orElse(attrs.get("sampled_at_nanos"))
        .flatMap(_to_long_option)
    )
  }

  private def _debug_calltree_attributes(
    value: Option[Any]
  ): Map[String, String] =
    value match {
      case Some(r: Record) => r.asMap.map { case (k, v) => k -> v.toString }
      case Some(m: Map[?, ?]) => m.map { case (k, v) => k.toString -> v.toString }
      case _ => Map.empty
    }

  private def _debug_record_string(
    record: Record,
    key: String
  ): Option[String] =
    record.asMap.get(key).map(_.toString)

  private def _to_long_option(
    value: String
  ): Option[Long] =
    scala.util.Try(value.toLong).toOption

  private def _debug_calltree_outline(
    nodes: Vector[DebugCallTreeNode]
  ): String = {
    s"""<div class="list-group border rounded bg-white textus-calltree-outline" data-calltree-node-list>
       |${nodes.zipWithIndex.map { case (node, index) =>
         _debug_calltree_node_html(node, 0, Vector(index + 1), None)
       }.mkString}
       |</div>""".stripMargin
  }

  private def _debug_calltree_node_html(
    node: DebugCallTreeNode,
    depth: Int,
    path: Vector[Int],
    parentDisplayLabel: Option[String] = None
  ): String = {
    val kind = _debug_calltree_node_kind(node)
    val displayLabel = _debug_calltree_display_label(node)
    val pair = path.mkString("-")
    val line = DebugCallTreeLine("step", node.label, displayLabel, kind, _debug_calltree_display_attributes(node, node.attributes), depth, pair, parentDisplayLabel, node.observations)
    val children = node.children.zipWithIndex.map { case (child, index) =>
      _debug_calltree_node_html(child, depth + 1, path :+ (index + 1), Some(displayLabel))
    }.mkString
    _debug_calltree_line_html(line, children)
  }

  private def _debug_calltree_line_html(
    line: DebugCallTreeLine
  ): String =
    _debug_calltree_line_html(line, "")

  private def _debug_calltree_line_html(
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
    val attrs = _debug_calltree_attributes_html(line.attributes)
    val observations = _debug_calltree_observations_html(line.observations)
    val body = attrs + observations + childBlock
    val badges = _debug_calltree_badges(line)
    val realIo = if (_debug_calltree_has_highlight(line.attributes, "real_io")) "true" else ""
    val source = line.attributes
      .get("source")
      .orElse(line.attributes.get("cache_layer"))
      .orElse(line.attributes.get("datastore"))
      .getOrElse("")
    val style = s"padding-left:${if (line.depth == 0) 0.75 else 1.0}rem"
    val lane = if (line.depth == 0) "" else """<span class="textus-calltree-lane" aria-hidden="true"></span>"""
    s"""<div class="list-group-item py-2 textus-calltree-row textus-calltree-row-${_escape(line.role)}" style="${style}" data-calltree-node data-calltree-row data-calltree-${_escape(line.role)}="true" data-calltree-pair="${_escape(line.pair)}" data-calltree-depth="${line.depth}" data-calltree-kind="${_escape(line.kind)}" data-calltree-real-io="${_escape(realIo)}" data-calltree-source="${_escape(source)}">
       |  ${lane}
       |  <details${if (line.depth <= 1) " open" else ""}>
       |    <summary class="d-flex flex-wrap align-items-center gap-2">
       |      <span class="badge ${_debug_calltree_role_badge_variant(line.role)}" data-calltree-role>${_escape(line.kind)}</span>
       |      <span class="fw-semibold" data-calltree-label>${_escape(line.displayLabel)}</span>
       |      ${badges}
       |    </summary>
       |    <div class="mt-2">${body}</div>
       |  </details>
       |</div>""".stripMargin
    }

  private def _debug_calltree_observations_html(
    observations: Vector[DebugCallTreeObservation]
  ): String =
    if (observations.isEmpty)
      ""
    else {
      val items = observations.map { observation =>
        val attrs = _debug_calltree_attributes_html(observation.attributes)
        val badges = _debug_calltree_badges(observation.attributes, observation.kind)
        val realIo = if (_debug_calltree_has_highlight(observation.attributes, "real_io")) "true" else ""
        val source = observation.attributes
          .get("source")
          .orElse(observation.attributes.get("cache_layer"))
          .orElse(observation.attributes.get("datastore"))
          .getOrElse("")
        s"""<div class="border-start border-2 ps-2 py-1 mb-1 textus-calltree-observation" data-calltree-observation data-calltree-observation-kind="${_escape(observation.kind)}" data-calltree-observation-real-io="${_escape(realIo)}" data-calltree-observation-source="${_escape(source)}">
           |  <div class="d-flex flex-wrap align-items-center gap-2">
           |    <span class="badge text-bg-secondary">observation</span>
           |    <span class="fw-semibold" data-calltree-observation-label>${_escape(observation.displayLabel)}</span>
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

  private def _debug_calltree_display_label(
    node: DebugCallTreeNode
  ): String =
    node.attributes.get("display_label").filter(_.nonEmpty).getOrElse(node.label)

  private def _debug_calltree_display_attributes(
    node: DebugCallTreeNode,
    attributes: Map[String, String]
  ): Map[String, String] = {
    attributes
  }

  private def _debug_calltree_node_kind(
    node: DebugCallTreeNode
  ): String = {
    node.attributes.get("calltree_kind").filter(_.nonEmpty).getOrElse("step")
  }

  private def _debug_calltree_badges(
    node: DebugCallTreeNode
  ): String =
    _debug_calltree_badges(node.attributes, _debug_calltree_node_kind(node))

  private def _debug_calltree_badges(
    line: DebugCallTreeLine
  ): String =
    _debug_calltree_badges(line.attributes, line.kind)

  private def _debug_calltree_badges(
    attributes: Map[String, String],
    kind: String
  ): String = {
    val highlightBadges = _debug_calltree_highlights(attributes).map { highlight =>
      val variant = highlight match {
        case "real_io" => "text-bg-warning"
        case "cache_hit" => "text-bg-info"
        case _ => "text-bg-secondary"
      }
      s"""<span class="badge ${variant} ms-2" data-calltree-badge data-calltree-highlight="${_escape(highlight)}">${_escape(highlight)}</span>"""
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
        s"""<span class="badge ${variant} ms-2" data-calltree-badge>${_escape(label)}</span>"""
      }
    }
    (highlightBadges ++ badges).mkString
  }

  private def _debug_calltree_highlights(
    attributes: Map[String, String]
  ): Vector[String] =
    attributes.get("highlights")
      .toVector
      .flatMap(_.split("[,\\s]+").toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct ++
      (if (attributes.get("real_io").exists(_.equalsIgnoreCase("true"))) Vector("real_io") else Vector.empty)

  private def _debug_calltree_has_highlight(
    attributes: Map[String, String],
    name: String
  ): Boolean =
    _debug_calltree_highlights(attributes).contains(name)

  private def _debug_calltree_role_badge_variant(
    role: String
  ): String =
    role match {
      case "enter" => "text-bg-primary"
      case "leave" => "text-bg-dark"
      case _ => "text-bg-secondary"
    }

  private def _debug_calltree_attributes_html(
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
           val long = _debug_calltree_multiline_attribute(key, value)
           val body =
             if (_debug_calltree_payload_attribute(key))
               _debug_calltree_payload_html(key, value)
             else if (long)
               s"""<pre class="bg-light border rounded p-2 mb-1"><code>${_escape(value)}</code></pre>"""
             else
               s"""<code>${_escape(value)}</code>"""
           val longAttr = if (long) """ data-calltree-long-attribute="true"""" else ""
           s"""<dt class="col-sm-3" data-calltree-attribute-key="${_escape(key)}">${_escape(key)}</dt><dd class="col-sm-9" data-calltree-attribute data-calltree-attribute-key="${_escape(key)}"${longAttr}>${body}</dd>"""
         }.mkString}
         |</dl>""".stripMargin
  }

  private def _debug_calltree_multiline_attribute(
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

  private def _debug_calltree_payload_attribute(
    key: String
  ): Boolean =
    key == "result" || key == "response"

  private def _debug_calltree_payload_html(
    key: String,
    value: String
  ): String = {
    val parsed = _debug_calltree_payload_json(value)
    val summary = parsed.map(_debug_calltree_payload_summary).getOrElse(_debug_calltree_compact_text(value))
    val external = parsed.flatMap(_debug_calltree_external_payload)
    val details =
      if (_debug_calltree_payload_detail_required(parsed, value))
        s"""<details class="textus-calltree-payload-details mt-1">
           |  <summary class="small text-secondary">Show ${_escape(key)}</summary>
           |  <pre class="bg-light border rounded p-2 mt-1 mb-1"><code>${_escape(parsed.map(_.spaces2).getOrElse(value))}</code></pre>
           |</details>""".stripMargin
      else
        ""
    s"""<div class="textus-calltree-payload" data-calltree-payload="${_escape(key)}">
       |  <code>${_escape(summary)}</code>
       |  ${external.map(_debug_calltree_external_payload_html(key, _)).getOrElse("")}
       |  $details
       |</div>""".stripMargin
  }

  private def _debug_calltree_payload_json(
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

  private def _debug_calltree_payload_detail_required(
    parsed: Option[Json],
    raw: String
  ): Boolean =
    parsed match {
      case Some(json) =>
        !_debug_calltree_payload_one_line(json)
      case None =>
        raw.length > 120 || raw.contains("\n")
    }

  private def _debug_calltree_payload_one_line(
    json: Json
  ): Boolean =
    json.asObject.flatMap(_("kind")).flatMap(_.asString).exists { kind =>
      Set("void", "unit", "null", "none").contains(kind)
    } || json.asString.exists(_.length <= 120) ||
      json.asNumber.nonEmpty ||
      json.asBoolean.nonEmpty

  private def _debug_calltree_payload_summary(
    json: Json
  ): String =
    json.asObject match {
      case Some(obj) =>
        val kind = obj("kind").flatMap(_.asString)
        val inline = obj("inline").flatMap(_debug_calltree_json_scalar_string)
        val parts =
          Vector(
            kind,
            obj("record_count").flatMap(_debug_calltree_json_scalar_string).map(x => s"records=$x"),
            obj("field_count").flatMap(_debug_calltree_json_scalar_string).map(x => s"fields=$x"),
            obj("size_bytes").flatMap(_debug_calltree_json_scalar_string).map(x => s"${x} bytes"),
            obj("char_count").flatMap(_debug_calltree_json_scalar_string).map(x => s"${x} chars"),
            inline.filterNot(_ == "false").filter(_.length <= 80).map(x => s"inline=$x")
          ).flatten
        if (parts.nonEmpty)
          parts.mkString(" ")
        else
          _debug_calltree_compact_text(json.noSpaces)
      case None =>
        _debug_calltree_json_scalar_string(json).map(_debug_calltree_compact_text).getOrElse(_debug_calltree_compact_text(json.noSpaces))
    }

  private def _debug_calltree_json_scalar_string(
    json: Json
  ): Option[String] =
    json.asString
      .orElse(json.asNumber.map(_.toString))
      .orElse(json.asBoolean.map(_.toString))

  private def _debug_calltree_external_payload(
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

  private def _debug_calltree_external_payload_html(
    key: String,
    target: (String, Boolean)
  ): String = {
    val label = s"Open external $key"
    if (target._2)
      s"""<a class="btn btn-sm btn-outline-secondary ms-2" href="${_escape(target._1)}">${_escape(label)}</a>"""
    else
      s"""<span class="badge text-bg-light ms-2">external: ${_escape(target._1)}</span>"""
  }

  private def _debug_calltree_compact_text(
    value: String
  ): String =
    if (value.length <= 120) value else value.take(117) + "..."

  def renderSystemJobTicket(
    jobId: String
  ): Page =
    renderFormResult(
      FormResultProperties(
        FormPageProperties(
          "system",
          "job",
          "result",
          _system_job_values(jobId, "accepted", s"/web/system/jobs/${jobId}/await")
        ),
        200,
        "application/json",
        s"""{"jobId":"${_json(jobId)}","jobStatus":"accepted","message":"Job result is available from this system page."}"""
      ),
      _system_job_template("Job result")
    )

  def renderSystemJobResult(
    jobId: String,
    response: org.goldenport.http.HttpResponse
  ): Page = {
    val ok = response.code >= 200 && response.code < 400
    renderFormResult(
      FormResultProperties(
        FormPageProperties(
          "system",
          "job",
          "result",
          _system_job_values(jobId, if (ok) "completed" else "failed", s"/web/system/jobs/${jobId}/await")
        ),
        response.code,
        response.mime.value,
        response.getString.getOrElse("")
      ),
      _system_job_template("Job result")
    )
  }

  private def _system_job_values(
    jobId: String,
    status: String,
    awaitHref: String
  ): Map[String, String] =
    Map(
      "result.job.id" -> jobId,
      "result.job.status" -> status,
      "result.job.href" -> awaitHref,
      "result.action.await.name" -> "await",
      "result.action.await.label" -> "Check result",
      "result.action.await.href" -> awaitHref,
      "result.action.await.method" -> "POST",
      "result.action.primary.name" -> "await",
      "result.action.primary.label" -> "Check result",
      "result.action.primary.href" -> awaitHref,
      "result.action.primary.method" -> "POST",
      "result.action.detail.name" -> "debug-detail",
      "result.action.detail.label" -> "Debug detail",
      "result.action.detail.href" -> s"/web/system/admin/jobs/${_escape_path_segment(jobId)}",
      "result.action.detail.method" -> "GET"
    )

  private def _system_job_template(
    title: String
  ): String =
    s"""<article>
       |  <h2>${_escape(title)}</h2>
       |  <textus:job-ticket></textus:job-ticket>
       |  <textus:action-link source="result.action.detail" class="btn btn-outline-secondary btn-sm"></textus:action-link>
       |  <textus-error-panel source="error"></textus-error-panel>
       |  <textus-result-view source="result.body"></textus-result-view>
       |  <textus-property-list source="result"></textus-property-list>
       |</article>""".stripMargin

  def renderSubsystemDashboard(subsystem: Subsystem): Page =
    Page(_dashboard_shell(
      title = "CNCF Health",
      subtitle = subsystem.name + subsystem.version.map(v => s" ${_escape(v)}").getOrElse(""),
      statePath = "/web/system/dashboard/state"
    ))

  def renderComponentDashboard(component: Component): Page =
    renderComponentDashboard(component, NamingConventions.toNormalizedSegment(component.name))

  def renderComponentDashboard(component: Component, componentPath: String): Page =
    Page(_dashboard_shell(
      title = s"${_escape(component.name)} Dashboard",
      subtitle = "Component health",
      statePath = s"/web/${componentPath}/dashboard/state"
    ))

  def renderDashboardState(
    subsystem: Subsystem,
    componentName: Option[String]
  ): Option[Page] =
    componentName match {
      case Some(name) =>
        _find_component(subsystem, name).map(component =>
          Page(_component_dashboard_state(component))
        )
      case None =>
        Some(Page(_subsystem_dashboard_state(subsystem)))
    }

  def renderSystemAdmin(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(_admin_page(
      title = "System Admin Configuration",
      subtitle = "Current CNCF runtime configuration",
      components = subsystem.components,
      subsystemName = subsystem.name,
      subsystemVersion = subsystem.version,
      dashboardPath = "/web/system/dashboard",
      performancePath = "/web/system/performance",
      webDescriptor = webDescriptor,
      runtimeConfiguration = Some(subsystem.configuration),
      operationalDetails = Some(_system_admin_operational_details(webDescriptor, subsystem.components)),
      componentFormsPath = None
    ))

  def renderApplicationAdmin(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(_simple_page(
      title = "Application Admin",
      subtitle = "Application operator console",
      body =
        s"""${_admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "Tags" -> "/web/admin/tags",
             "Associations" -> "/web/admin/associations"
           ))}
           |${_application_admin_entry_pages(webDescriptor)}
           |${_application_admin_component_cards(subsystem, webDescriptor)}""".stripMargin
    ))

  def renderSystemAdminDescriptor(
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(_simple_page(
      title = "System Web Descriptor",
      subtitle = "Management Console descriptor view",
      body =
        s"""${_admin_nav_card(Vector("System admin" -> "/web/system/admin", "System dashboard" -> "/web/system/dashboard"))}
           |${_web_descriptor_section_nav}
           |${_web_descriptor_control_tables(webDescriptor)}
           |${_web_descriptor_asset_composition_table(webDescriptor)}
           |${_web_descriptor_json_panel(
             "completed-descriptor",
             "Completed Descriptor JSON",
             "The completed view applies framework defaults so the descriptor can be inspected as the runtime sees it.",
             _web_descriptor_json(webDescriptor, completed = true)
           )}
           |${_web_descriptor_json_panel(
             "configured-descriptor",
             "Configured Descriptor JSON",
             "The configured view keeps explicit descriptor entries for comparison.",
             _web_descriptor_json(webDescriptor, completed = false)
           )}""".stripMargin
    ))

  def renderSystemAdminAssemblyWarnings(
    subsystem: Subsystem
  ): Page = {
    val report = _assembly_report(subsystem)
    val rows =
      if (report.warnings.isEmpty)
        """<tr><td colspan="6" class="text-secondary">No assembly warnings.</td></tr>"""
      else
        report.warnings.map { warning =>
          s"""<tr>
             |  <td><code>${_escape(warning.kind)}</code></td>
             |  <td>${_escape(warning.severity)}</td>
             |  <td>${_escape(warning.componentName)}</td>
             |  <td>${_escape(warning.message)}</td>
             |  <td>${_escape(warning.selectedOrigin.getOrElse(""))}</td>
             |  <td>${_escape(warning.droppedOrigins.mkString(", "))}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    Page(_simple_page(
      title = "Assembly Warnings",
      subtitle = "Runtime assembly diagnostics",
      body =
        s"""${_admin_nav_card(Vector("System dashboard" -> "/web/system/dashboard", "System admin" -> "/web/system/admin", "Assembly report" -> "/web/system/admin/assembly/report"))}
           |${_admin_card(
             "Warnings",
             s"""<p>${report.warnings.size} warning(s). Assembly warnings are diagnostics, not health errors.</p>
                |${_admin_table(
                  Some("<tr><th>Kind</th><th>Severity</th><th>Component</th><th>Message</th><th>Selected</th><th>Dropped</th></tr>"),
                  rows
                )}""".stripMargin
           )}""".stripMargin
    ))
  }

  def renderSystemAdminAssemblyReport(
    subsystem: Subsystem
  ): Page = {
    val record = _assembly_report(subsystem).toRecord
    val json = _manual_raw_json(record).map(_.spaces2).getOrElse(_manual_raw_text(record))
    val yaml = _manual_raw_json(record).map(_json_to_yaml).getOrElse(_manual_raw_text(record))
    Page(_simple_page(
      title = "Assembly Report",
      subtitle = "Runtime assembly report",
      body =
        s"""${_admin_nav_card(Vector("System dashboard" -> "/web/system/dashboard", "System admin" -> "/web/system/admin", "Assembly warnings" -> "/web/system/admin/assembly/warnings"))}
           |${_admin_card("Report", _raw_format_tabs(json, yaml, "assembly-report"))}""".stripMargin
    ))
  }

  def renderSystemAdminJobs(
    subsystem: Subsystem
  ): Page = {
    val jobs = subsystem.jobEngine.listJobs(limit = 100, persistentOnly = true)
    val rows =
      if (jobs.isEmpty)
        """<tr><td colspan="7" class="text-secondary">No persistent jobs have been retained yet. Run a request with <code>--debug.trace-job</code> or <code>textus.debug.trace-job=true</code>.</td></tr>"""
      else
        jobs.map(_system_admin_job_row).mkString("\n")
    Page(_simple_page(
      title = "System Admin Jobs",
      subtitle = "Persistent job debug and calltree inspection",
      body =
        s"""${_admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "System dashboard" -> "/web/system/dashboard",
             "Execution history" -> "/form/admin/execution/history"
           ))}
           |${_admin_card(
             "Debug Jobs",
             s"""<p>Query requests normally run directly. Requests submitted with <code>--debug.trace-job</code> are retained here for operational debugging.</p>
                |${_admin_table(
                  Some("<tr><th>Job</th><th>Status</th><th>Target</th><th>Result</th><th>Trace</th><th>Updated</th><th>Actions</th></tr>"),
                  rows
                )}""".stripMargin
           )}""".stripMargin
    ))
  }

  def renderSystemAdminJob(
    subsystem: Subsystem,
    model: JobQueryReadModel
  ): Page = {
    val target = _job_target(model)
    val calltree = model.calltree.map(_job_calltree_panel).getOrElse(
      """<p class="text-secondary">No calltree was saved for this job. Use <code>--debug.save-calltree</code>, or inspect failed/slow persistent jobs.</p>"""
    )
    val taskrows =
      if (model.tasks.tasks.isEmpty)
        """<tr><td colspan="7" class="text-secondary">No task records are available.</td></tr>"""
      else
        model.tasks.tasks.map { task =>
          s"""<tr><td><code>${_escape(task.taskId.value)}</code></td><td>${_escape(task.status.toString)}</td><td>${_escape(task.component.getOrElse(""))}</td><td>${_escape(task.operation.getOrElse(""))}</td><td>${_escape(task.result.message.getOrElse(""))}</td><td>${_escape(task.startedAt.toString)}</td><td>${_escape(task.finishedAt.map(_.toString).getOrElse(""))}</td></tr>"""
        }.mkString("\n")
    val eventrows =
      if (model.timeline.events.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No timeline events are available.</td></tr>"""
      else
        model.timeline.events.map { event =>
          s"""<tr><td>${event.sequence}</td><td>${_escape(event.kind)}</td><td>${_escape(event.occurredAt.toString)}</td><td>${_escape(event.taskId.map(_.value).getOrElse(""))}</td><td>${_escape(event.note.getOrElse(""))}</td></tr>"""
        }.mkString("\n")
    Page(_simple_page(
      title = s"System Admin Job ${model.jobId.value}",
      subtitle = "Job-managed trace and calltree detail",
      body =
        s"""${_admin_nav_card(Vector(
             "Debug jobs" -> "/web/system/admin/jobs",
             "System job result" -> s"/web/system/jobs/${_escape_path_segment(model.jobId.value)}",
             "System admin" -> "/web/system/admin"
           ))}
           |${_admin_card(
             "Summary",
             s"""<dl class="row mb-0">
           |    <dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${_escape(model.jobId.value)}</code></dd>
           |    <dt class="col-sm-3">Status</dt><dd class="col-sm-9">${_escape(model.status.toString)}</dd>
           |    <dt class="col-sm-3">Persistence</dt><dd class="col-sm-9">${_escape(model.persistence.toString)}</dd>
           |    <dt class="col-sm-3">Target</dt><dd class="col-sm-9"><code>${_escape(target)}</code></dd>
           |    <dt class="col-sm-3">Result</dt><dd class="col-sm-9">${_escape(model.resultSummary.message.getOrElse(""))}</dd>
           |    <dt class="col-sm-3">Trace ID</dt><dd class="col-sm-9"><code>${_escape(model.lineage.correlationId.getOrElse(model.debug.parameters.getOrElse("traceId", "")))}</code></dd>
           |    <dt class="col-sm-3">Updated</dt><dd class="col-sm-9">${_escape(model.updatedAt.toString)}</dd>
           |  </dl>""".stripMargin
           )}
           |${_admin_card("Calltree", calltree)}
           |${_admin_card(
             "Tasks",
             _admin_table(
               Some("<tr><th>Task</th><th>Status</th><th>Component</th><th>Operation</th><th>Message</th><th>Started</th><th>Finished</th></tr>"),
               taskrows
             )
           )}
           |${_admin_card(
             "Timeline",
             _admin_table(
               Some("<tr><th>#</th><th>Kind</th><th>At</th><th>Task</th><th>Note</th></tr>"),
               eventrows
             )
           )}""".stripMargin
    ))
  }

  def renderApplicationJobs(
    app: String,
    jobs: Vector[JobQueryReadModel]
  ): Page = {
    val appPath = NamingConventions.toNormalizedSegment(app)
    val rows =
      if (jobs.isEmpty)
        """<tr><td colspan="6" class="text-secondary">No jobs are available for this application user.</td></tr>"""
      else
        jobs.map(_application_job_row(appPath, _)).mkString("\n")
    Page(_simple_page(
      title = s"${_escape(app)} Jobs",
      subtitle = "Your application jobs",
      body =
        s"""<article class="card">
           |  <div class="card-body">
           |    <div class="d-flex flex-wrap justify-content-between gap-2 align-items-start mb-3">
           |      <div>
           |        <h2 class="h5 card-title mb-1">My jobs</h2>
           |        <p class="text-secondary mb-0">Jobs started from this application by the current user or session.</p>
           |      </div>
           |      <a class="btn btn-outline-secondary btn-sm" href="/web/${_escape(appPath)}">Back to application</a>
           |    </div>
           |    <div class="table-responsive">
           |      <table class="table table-sm table-hover align-middle mb-0">
           |        <thead><tr><th>Job</th><th>Status</th><th>Target</th><th>Result</th><th>Updated</th><th>Actions</th></tr></thead>
           |        <tbody>${rows}</tbody>
           |      </table>
           |    </div>
           |  </div>
           |</article>""".stripMargin
    ))
  }

  def renderApplicationJob(
    app: String,
    model: JobQueryReadModel
  ): Page = {
    val appPath = NamingConventions.toNormalizedSegment(app)
    val target = _job_target(model)
    val calltree = model.calltree.map(_job_calltree_panel).getOrElse(
      """<p class="text-secondary">No CallTree was saved for this job yet.</p>"""
    )
    val result = model.result.map(_.print).getOrElse(model.resultSummary.message.getOrElse(""))
    Page(_simple_page(
      title = s"${_escape(app)} Job ${model.jobId.value}",
      subtitle = "Application job result",
      body =
        s"""<article class="card mb-3">
           |  <div class="card-body">
           |    <div class="d-flex flex-wrap justify-content-between gap-2 align-items-start mb-3">
           |      <div>
           |        <h2 class="h5 card-title mb-1">Job result</h2>
           |        <p class="text-secondary mb-0"><code>${_escape(target)}</code></p>
           |      </div>
           |      <a class="btn btn-outline-secondary btn-sm" href="/web/${_escape(appPath)}/jobs">My jobs</a>
           |    </div>
           |    <dl class="row mb-0">
           |      <dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${_escape(model.jobId.value)}</code></dd>
           |      <dt class="col-sm-3">Status</dt><dd class="col-sm-9"><span class="badge text-bg-${_escape(_job_status_variant(model.status.toString))}">${_escape(model.status.toString)}</span></dd>
           |      <dt class="col-sm-3">Result</dt><dd class="col-sm-9">${_escape(model.resultSummary.message.getOrElse(""))}</dd>
           |      <dt class="col-sm-3">Updated</dt><dd class="col-sm-9">${_escape(model.updatedAt.toString)}</dd>
           |    </dl>
           |  </div>
           |</article>
           |<article class="card mb-3">
           |  <div class="card-body">
           |    <h2 class="h5 card-title">Response</h2>
           |    <pre class="bg-light border rounded p-3 mb-0"><code>${_escape(result)}</code></pre>
           |  </div>
           |</article>
           |<article class="card">
           |  <div class="card-body">
           |    <h2 class="h5 card-title">CallTree</h2>
           |    ${calltree}
           |    <div class="d-flex flex-wrap gap-2 mt-3">
           |      <a class="btn btn-sm btn-outline-secondary" href="/web/system/admin/jobs/${_escape_path_segment(model.jobId.value)}">System debug detail</a>
           |      <a class="btn btn-sm btn-outline-secondary" href="/form/admin/execution/history">Execution history</a>
           |    </div>
           |  </div>
           |</article>""".stripMargin
    ))
  }

  private def _application_job_row(
    appPath: String,
    model: JobQueryReadModel
  ): String = {
    val target = _job_target(model)
    s"""<tr><td><code>${_escape(model.jobId.value)}</code></td><td>${_escape(model.status.toString)}</td><td><code>${_escape(target)}</code></td><td>${_escape(model.resultSummary.message.getOrElse(""))}</td><td>${_escape(model.updatedAt.toString)}</td><td><a href="/web/${_escape(appPath)}/jobs/${_escape_path_segment(model.jobId.value)}">Open</a></td></tr>"""
  }

  def renderBlobAdmin(): Page =
    Page(_simple_page(
      title = "Blob Admin",
      subtitle = "Blob metadata, associations, and store diagnostics",
      body =
        s"""${_admin_card("Management",
             s"""<p>Use these pages to inspect Blob metadata, manage entity associations, and run controlled Blob admin actions.</p>
                |${_admin_entry_cards(Vector(
             _admin_entry_card("Blobs", "List Blob metadata rows and open detail pages.", "/web/blob/admin/blobs"),
             _admin_entry_card("Associations", "Inspect, attach, and detach Blob-to-entity association records.", "/web/blob/admin/associations"),
             _admin_entry_card("Store Status", "Inspect the active BlobStore backend status.", "/web/blob/admin/store"),
             _admin_entry_card("Delete", "Open a Blob detail page to run controlled delete with optional force.", "/web/blob/admin/blobs")
           ))}""".stripMargin)}
           |${_admin_card("Authorization requirements",
             """<p>Blob admin actions use the existing admin operation gate plus generic resource policies.</p>
               |<ul>
               |  <li><code>collection:blob:delete</code> controls Blob metadata delete.</li>
               |  <li><code>association:blob_attachment:create/delete/search/list</code> controls Blob attachment operations.</li>
               |  <li><code>store:blobstore:status</code> controls BlobStore status diagnostics.</li>
               |</ul>
               |<p class="mb-0"><a href="/web/system/document/specification#authorization-policies">View effective authorization policies</a></p>""".stripMargin
           )}""".stripMargin
    ))

  def renderBlobAdminBlobs(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _blob_admin_record(subsystem, "admin_list_blobs", _blob_admin_page_args(params), requestProperties).map { record =>
      val rows = _record_seq(record.asMap.get("data"))
      val table =
        if (rows.isEmpty)
          s"""<tbody>${_admin_empty_table_cell(9, "No Blob metadata rows are available.")}</tbody>"""
        else
          s"""<tbody>${rows.map(_blob_admin_blob_row).mkString("\n")}</tbody>"""
      val nav = _admin_nav_card(Vector(
        "Blob admin" -> "/web/blob/admin",
        "Associations" -> "/web/blob/admin/associations",
        "Store" -> "/web/blob/admin/store"
      ))
      val paging = _blob_admin_paging("/web/blob/admin/blobs", params, record)
      Page(_simple_page(
        title = "Blob Admin Blobs",
        subtitle = "Read-only Blob metadata inventory",
        body =
          s"""${nav}
             |${_admin_card("Blobs",
               s"""<p>Showing metadata rows only. Payload bytes remain in BlobStore.</p>
                  |<div class="table-responsive mt-3">
                  |  <table class="table table-sm table-hover align-middle mb-0">
                  |    <thead><tr><th>ID</th><th>Kind</th><th>Source</th><th>Filename</th><th>Content Type</th><th>Bytes</th><th>Digest</th><th>Display URL</th><th>Actions</th></tr></thead>
                  |    ${table}
                  |  </table>
                  |</div>
                  |${paging}""".stripMargin)}
             |${_manual_raw_details("Raw Blob list", record)}""".stripMargin
      ))
    }

  def renderBlobAdminBlobDetail(
    subsystem: Subsystem,
    id: String,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _blob_admin_record(subsystem, "admin_get_blob", Vector("id" -> id), requestProperties).map { record =>
      val nav = _admin_nav_card(Vector(
        "Blobs" -> "/web/blob/admin/blobs",
        "Associations" -> s"/web/blob/admin/associations?id=${_escape_query(id)}",
        "Blob admin" -> "/web/blob/admin"
      ))
      Page(_simple_page(
        title = s"Blob ${_escape(id)}",
        subtitle = "Blob metadata detail",
        body =
          s"""${nav}
             |${_admin_card("Metadata", _field_table(_blob_admin_blob_fields(record)))}
             |${_admin_card("Access URLs", _field_table(_blob_admin_url_fields(record)))}
             |${_admin_card("Actions", s"""<div class="admin-action-row d-flex flex-wrap gap-2"><a class="btn btn-outline-danger" href="/web/blob/admin/blobs/${_escape_path_segment(id)}/delete">Delete Blob</a></div>""")}
             |${_manual_raw_details("Raw Blob metadata", record)}""".stripMargin
      ))
    }

  def renderBlobAdminBlobDelete(
    subsystem: Subsystem,
    id: String,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    for {
      blob <- _blob_admin_record(subsystem, "admin_get_blob", Vector("id" -> id), requestProperties)
      associations <- _blob_admin_record(subsystem, "admin_list_blob_associations", Vector("id" -> id, "offset" -> "0", "limit" -> "100"), requestProperties)
    } yield {
      val count = associations.getInt("fetchedCount").getOrElse(_record_seq(associations.asMap.get("data")).size)
      val warning =
        if (count > 0)
          s"""<div class="alert alert-warning">This Blob has ${count} association(s). Default delete will fail unless <code>force</code> is enabled.</div>"""
        else
          """<div class="alert alert-info">This Blob has no visible associations. Default delete is expected to remove metadata and managed payload if present.</div>"""
      val form =
        s"""<form method="post" action="/web/blob/admin/blobs/${_escape_path_segment(id)}/delete" class="border rounded p-3">
           |  <input type="hidden" name="id" value="${_escape(id)}">
           |  <div class="form-check mb-3">
           |    <input class="form-check-input" type="checkbox" id="blobAdminForceDelete" name="force" value="true">
           |    <label class="form-check-label" for="blobAdminForceDelete">Force delete and remove referencing Blob associations</label>
           |  </div>
           |  <div class="admin-action-row d-flex flex-wrap gap-2">
           |    <button class="btn btn-danger" type="submit">Delete Blob</button>
           |    <a class="btn btn-outline-secondary" href="/web/blob/admin/blobs/${_escape_path_segment(id)}">Cancel</a>
           |  </div>
           |</form>""".stripMargin
      Page(_simple_page(
        title = s"Delete Blob ${_escape(id)}",
        subtitle = "Confirm controlled Blob deletion",
        body =
          s"""${_admin_nav_card(Vector("Blob detail" -> s"/web/blob/admin/blobs/${_escape_path_segment(id)}", "Blobs" -> "/web/blob/admin/blobs", "Associations" -> s"/web/blob/admin/associations?id=${_escape_query(id)}"))}
             |${warning}
             |${_admin_card("Blob metadata", _field_table(_blob_admin_blob_fields(blob)))}
             |${_admin_card("Delete confirmation", form)}
             |${_manual_raw_details("Raw Blob metadata", blob)}
             |${_manual_raw_details("Raw Blob associations", associations)}""".stripMargin
      ))
    }

  def renderBlobAdminBlobDeleteResult(
    subsystem: Subsystem,
    id: String,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] = {
    val force = form.get("force").exists(_blob_admin_is_truthy)
    _blob_admin_record(subsystem, "admin_delete_blob", Vector("id" -> id, "force" -> force.toString), requestProperties).map { record =>
      Page(_simple_page(
        title = "Blob Deleted",
        subtitle = s"Deleted Blob ${_escape(id)}",
        body =
          s"""${_admin_nav_card(Vector("Blobs" -> "/web/blob/admin/blobs", "Associations" -> "/web/blob/admin/associations", "Blob admin" -> "/web/blob/admin"))}
             |${_admin_card("Delete result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Blobs" -> "/web/blob/admin/blobs", "Associations" -> "/web/blob/admin/associations"))}
             |${_manual_raw_details("Raw delete result", record)}""".stripMargin
      ))
    }
  }

  def renderBlobAdminAssociations(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _blob_admin_record(subsystem, "admin_list_blob_associations", _blob_admin_association_args(params), requestProperties).map { record =>
      val rows = _record_seq(record.asMap.get("data"))
      val table =
        if (rows.isEmpty)
          s"""<tbody>${_admin_empty_table_cell(8, "No Blob association rows are available for this filter.")}</tbody>"""
        else
          s"""<tbody>${rows.map(_blob_admin_association_row).mkString("\n")}</tbody>"""
      val nav = _admin_nav_card(Vector(
        "Blob admin" -> "/web/blob/admin",
        "Blobs" -> "/web/blob/admin/blobs",
        "Store" -> "/web/blob/admin/store"
      ))
      val filters = _blob_admin_association_filter_form(params)
      val attach = _blob_admin_association_attach_form(params)
      val paging = _blob_admin_paging("/web/blob/admin/associations", params, record)
      Page(_simple_page(
        title = "Blob Admin Associations",
        subtitle = "Read-only Blob-to-entity association inventory",
        body =
          s"""${nav}
             |<div class="row g-3">
             |  <div class="col-12 col-xl-6">${_admin_card("Attach Blob", attach)}</div>
             |  <div class="col-12 col-xl-6">${_admin_card("Filters", filters)}</div>
             |</div>
             |${_admin_card("Associations",
               s"""<div class="table-responsive mt-3">
                  |  <table class="table table-sm table-hover align-middle mb-0">
                  |    <thead><tr><th>Association</th><th>Source Entity</th><th>Blob</th><th>Role</th><th>Sort</th><th>Domain</th><th>Collection</th><th>Actions</th></tr></thead>
                  |    ${table}
                  |  </table>
                  |</div>
                  |${paging}""".stripMargin)}
             |${_manual_raw_details("Raw association list", record)}""".stripMargin
      ))
    }

  def renderBlobAdminAssociationAttachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _blob_admin_record(subsystem, "admin_attach_blob_to_entity", _blob_admin_association_mutation_args(form, includeSortOrder = true), requestProperties).map { record =>
      Page(_simple_page(
        title = "Blob Association Attached",
        subtitle = "Blob association was created or already existed",
        body =
          s"""${_admin_nav_card(Vector("Associations" -> "/web/blob/admin/associations", "Blob admin" -> "/web/blob/admin"))}
             |${_admin_card("Attach result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Associations" -> s"/web/blob/admin/associations?sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}", "Blob detail" -> s"/web/blob/admin/blobs/${_escape_path_segment(form.getOrElse("id", ""))}"))}
             |${_manual_raw_details("Raw attach result", record)}""".stripMargin
      ))
    }

  def renderBlobAdminAssociationDetachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _blob_admin_record(subsystem, "admin_detach_blob_from_entity", _blob_admin_association_mutation_args(form, includeSortOrder = false), requestProperties).map { record =>
      Page(_simple_page(
        title = "Blob Association Detached",
        subtitle = "Blob association was removed",
        body =
          s"""${_admin_nav_card(Vector("Associations" -> "/web/blob/admin/associations", "Blob admin" -> "/web/blob/admin"))}
             |${_admin_card("Detach result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Associations" -> s"/web/blob/admin/associations?sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}", "Blob detail" -> s"/web/blob/admin/blobs/${_escape_path_segment(form.getOrElse("id", ""))}"))}
             |${_manual_raw_details("Raw detach result", record)}""".stripMargin
      ))
    }

  def renderAdminAssociations(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_association_record(subsystem, "admin_list_associations", _admin_association_args(params), requestProperties).map { record =>
      val rows = _record_seq(record.asMap.get("data"))
      val table =
        if (rows.isEmpty)
          s"""<tbody>${_admin_empty_table_cell(8, "No Association rows are available for this filter.")}</tbody>"""
        else
          s"""<tbody>${rows.map(row => _admin_association_row(row, includeDetach = true)).mkString("\n")}</tbody>"""
      val filters = _admin_association_filter_form(params)
      val attach = _admin_association_attach_form(params)
      val paging = _admin_association_paging("/web/admin/associations", params, record)
      Page(_simple_page(
        title = "Association Administration",
        subtitle = "Generic Entity-to-Entity Association inventory",
        body =
          s"""${_admin_nav_card(Vector("System admin" -> "/web/system/admin", "Blob associations" -> "/web/blob/admin/associations"))}
             |<div class="row g-3">
             |  <div class="col-12 col-xl-6">${_admin_card("Attach Association", attach)}</div>
             |  <div class="col-12 col-xl-6">${_admin_card("Filters", filters)}</div>
             |</div>
             |${_admin_card("Associations",
               s"""<div class="table-responsive mt-3">
                  |  <table class="table table-sm table-hover align-middle mb-0">
                  |    <thead><tr><th>Association</th><th>Source Entity</th><th>Target Entity</th><th>Target kind</th><th>Role</th><th>Sort</th><th>Domain</th><th>Actions</th></tr></thead>
                  |    ${table}
                  |  </table>
                  |</div>
                  |${paging}""".stripMargin)}
             |${_manual_raw_details("Raw association list", record)}""".stripMargin
      ))
    }

  def renderAdminTags(
    subsystem: Subsystem,
    params: Map[String, String] = Map.empty,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    for {
      tree <- _admin_tag_record(subsystem, "tag_tree", _admin_tag_tree_args(params), requestProperties)
      search <- _admin_tag_search_record(subsystem, params, requestProperties)
    } yield {
      val tags = _record_seq(tree.asMap.get("data"))
      val tagSpace = params.get("tagSpace").filter(_.nonEmpty).getOrElse("default")
      val table =
        if (tags.isEmpty)
          s"""<tbody>${_admin_empty_table_cell(9, "No Tags are available for this TagSpace.")}</tbody>"""
        else
          s"""<tbody>${tags.map(_admin_tag_row).mkString("\n")}</tbody>"""
      val searchHtml = search.map(_admin_tag_search_result(params, _)).getOrElse("")
      val emptyAlert =
        if (tags.isEmpty) _admin_tag_empty_alert("No Tags are available for this TagSpace.")
        else ""
      Page(_simple_page(
        title = "Tag Administration",
        subtitle = "Hierarchical Tag tree and Entity-to-Tag management",
        body =
          s"""${_admin_nav_card(Vector("System admin" -> "/web/system/admin", "Associations" -> "/web/admin/associations"))}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-column flex-md-row justify-content-between gap-3 mb-3">
             |      <div>
             |        <h2 class="card-title h5 mb-1">TagSpace selector</h2>
             |        <p class="text-body-secondary mb-0">Current TagSpace <span class="badge text-bg-secondary">${_escape(tagSpace)}</span></p>
             |      </div>
             |      <div class="text-body-secondary small">Tags are shared master data inside the selected TagSpace.</div>
             |    </div>
             |    ${_admin_tag_filter_form(params)}
             |  </div>
             |</article>
             |<div class="row g-3">
             |  <div class="col-12 col-xl-6">
             |    <article class="card admin-card h-100">
             |      <div class="card-body">
             |        <h2 class="card-title h5">Create Tag</h2>
             |        ${_admin_tag_create_form(params)}
             |      </div>
             |    </article>
             |  </div>
             |  <div class="col-12 col-xl-6">
             |    <article class="card admin-card h-100">
             |      <div class="card-body">
             |        <h2 class="card-title h5">Search Entities by Tag</h2>
             |        ${_admin_tag_search_form(params)}
             |      </div>
             |    </article>
             |  </div>
             |</div>
             |${searchHtml}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-column flex-md-row justify-content-between gap-2 mb-3">
             |      <div>
             |        <h2 class="card-title h5 mb-1">Tags</h2>
             |        <p class="text-body-secondary mb-0">Browse, update, and move Tags in <span class="badge text-bg-secondary">${_escape(tagSpace)}</span>.</p>
             |      </div>
             |      <span class="badge text-bg-light border align-self-start">${tags.size} tags</span>
             |    </div>
             |    ${emptyAlert}
             |    <div class="table-responsive">
             |      <table class="table table-sm table-hover align-middle mb-0">
             |      <thead><tr><th>Path</th><th>Key</th><th>TagSpace</th><th>Usage</th><th>Sort</th><th>Title</th><th>Description</th><th>Update</th><th>Move</th></tr></thead>
             |      ${table}
             |    </table>
             |  </div>
             |  </div>
             |</article>
             |${_manual_raw_details("Raw tag tree", tree)}""".stripMargin
      ))
    }

  def renderAdminTagCreateResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_tag_record(subsystem, "tag_create", _admin_tag_mutation_args(form, includeParent = true), requestProperties).map { record =>
      _admin_tag_result_page("Tag Created", "Tag was created", form, record)
    }

  def renderAdminTagUpdateResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_tag_record(subsystem, "tag_update", _admin_tag_update_args(form), requestProperties).map { record =>
      _admin_tag_result_page("Tag Updated", "Tag metadata was updated", form, record)
    }

  def renderAdminTagMoveResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_tag_record(subsystem, "tag_move", _admin_tag_move_args(form), requestProperties).map { record =>
      _admin_tag_result_page("Tag Moved", "Tag path was updated", form, record)
    }

  def renderAdminTagAttachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_tag_record(subsystem, "tag_attach", _admin_tag_attach_args(form), requestProperties).map { record =>
      Page(_simple_page(
        title = "Tag Attached",
        subtitle = "TagAttachment was created or already existed",
        body =
          s"""${_admin_nav_card(Vector("Tags" -> "/web/admin/tags", "Associations" -> "/web/admin/associations"))}
             |${_admin_card("Attach result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Tags" -> s"/web/admin/tags?tagSpace=${_escape_query(form.getOrElse("tagSpace", ""))}&sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}"))}
             |${_manual_raw_details("Raw attach result", record)}""".stripMargin
      ))
    }

  def renderAdminTagDetachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_tag_record(subsystem, "tag_detach", _admin_tag_detach_args(form), requestProperties).map { record =>
      Page(_simple_page(
        title = "Tag Detached",
        subtitle = "TagAttachment was removed",
        body =
          s"""${_admin_nav_card(Vector("Tags" -> "/web/admin/tags", "Associations" -> "/web/admin/associations"))}
             |${_admin_card("Detach result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Tags" -> s"/web/admin/tags?tagSpace=${_escape_query(form.getOrElse("tagSpace", ""))}&sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}"))}
             |${_manual_raw_details("Raw detach result", record)}""".stripMargin
      ))
    }

  def renderAdminAssociationAttachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_association_record(subsystem, "admin_attach_association", _admin_association_mutation_args(form, includeSortOrder = true), requestProperties).map { record =>
      Page(_simple_page(
        title = "Association Attached",
        subtitle = "Association was created or already existed",
        body =
          s"""${_admin_nav_card(Vector("Associations" -> "/web/admin/associations", "Blob associations" -> "/web/blob/admin/associations"))}
             |${_admin_card("Attach result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Associations" -> s"/web/admin/associations?domain=${_escape_query(form.getOrElse("domain", ""))}&sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}"))}
             |${_manual_raw_details("Raw attach result", record)}""".stripMargin
      ))
    }

  def renderAdminAssociationDetachResult(
    subsystem: Subsystem,
    form: Map[String, String],
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _admin_association_record(subsystem, "admin_detach_association", _admin_association_mutation_args(form, includeSortOrder = false), requestProperties).map { record =>
      Page(_simple_page(
        title = "Association Detached",
        subtitle = "Association was removed",
        body =
          s"""${_admin_nav_card(Vector("Associations" -> "/web/admin/associations", "Blob associations" -> "/web/blob/admin/associations"))}
             |${_admin_card("Detach result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_admin_action_row(Vector("Back to Associations" -> s"/web/admin/associations?domain=${_escape_query(form.getOrElse("domain", ""))}&sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}"))}
             |${_manual_raw_details("Raw detach result", record)}""".stripMargin
      ))
    }

  def renderBlobAdminStore(
    subsystem: Subsystem,
    requestProperties: Vector[(String, String)] = Vector.empty
  ): Consequence[Page] =
    _blob_admin_record(subsystem, "admin_blob_store_status", Vector.empty, requestProperties).map { record =>
      val nav = _admin_nav_card(Vector(
        "Blob admin" -> "/web/blob/admin",
        "Blobs" -> "/web/blob/admin/blobs",
        "Associations" -> "/web/blob/admin/associations"
      ))
      Page(_simple_page(
        title = "Blob Admin Store",
        subtitle = "BlobStore backend status",
        body =
          s"""${nav}
             |${_admin_card("Store Status", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
             |${_manual_raw_details("Raw BlobStore status", record)}""".stripMargin
      ))
    }

  private def _blob_admin_record(
    subsystem: Subsystem,
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): Consequence[Record] =
    subsystem.executeOperationResponse(_blob_admin_request(operation, args, requestProperties)).flatMap {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case other =>
        Consequence.operationInvalid(s"Blob admin operation did not return a record: ${operation} (${other.getClass.getSimpleName})")
    }

  private def _admin_association_record(
    subsystem: Subsystem,
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): Consequence[Record] =
    subsystem.executeOperationResponse(_admin_association_request(operation, args, requestProperties)).flatMap {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case other =>
        Consequence.operationInvalid(s"Association admin operation did not return a record: ${operation} (${other.getClass.getSimpleName})")
    }

  private def _admin_tag_record(
    subsystem: Subsystem,
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): Consequence[Record] =
    subsystem.executeOperationResponse(_admin_tag_request(operation, args, requestProperties)).flatMap {
      case OperationResponse.RecordResponse(record) =>
        Consequence.success(record)
      case other =>
        Consequence.operationInvalid(s"Tag admin operation did not return a record: ${operation} (${other.getClass.getSimpleName})")
    }

  private def _admin_tag_search_record(
    subsystem: Subsystem,
    params: Map[String, String],
    requestProperties: Vector[(String, String)]
  ): Consequence[Option[Record]] =
    if (params.get("tagRef").orElse(params.get("tag")).exists(_.trim.nonEmpty) &&
        params.get("component").exists(_.trim.nonEmpty) &&
        params.get("entity").orElse(params.get("entityName")).exists(_.trim.nonEmpty))
      _admin_tag_record(subsystem, "tag_search_entities", _admin_tag_search_args(params), requestProperties).map(Some(_))
    else
      Consequence.success(None)

  private def _admin_tag_request(
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): ProtocolRequest =
    ProtocolRequest.of(
      component = "tag",
      service = "tag",
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList,
      properties = requestProperties.map { case (key, value) => Property(key, value, None) }.toList
    )

  private def _admin_association_request(
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): ProtocolRequest =
    ProtocolRequest.of(
      component = "admin",
      service = "association",
      operation = operation,
      arguments = args.map { case (key, value) => Argument(key, value) }.toList,
      properties = requestProperties.map { case (key, value) => Property(key, value, None) }.toList
    )

  private def _blob_admin_request(
    operation: String,
    args: Vector[(String, String)],
    requestProperties: Vector[(String, String)]
  ): ProtocolRequest =
    ProtocolRequest.of(
      component = "blob",
      service = "blob",
      operation = operation,
      properties = (requestProperties ++ args).map { case (key, value) => Property(key, value, None) }.toList
    )

  private def _blob_admin_page_args(
    params: Map[String, String]
  ): Vector[(String, String)] = {
    val limit = params.get("limit").orElse(params.get("pageSize")).flatMap(_.toIntOption).filter(_ > 0).getOrElse(100)
    val offset = params.get("offset").flatMap(_.toIntOption).filter(_ >= 0).getOrElse {
      params.get("page").flatMap(_.toIntOption).filter(_ > 0).map(page => (page - 1) * limit).getOrElse(0)
    }
    Vector("offset" -> offset.toString, "limit" -> limit.toString)
  }

  private def _blob_admin_association_args(
    params: Map[String, String]
  ): Vector[(String, String)] =
    _blob_admin_page_args(params) ++
      Vector("sourceEntityId", "id", "role").flatMap(key => params.get(key).filter(_.nonEmpty).map(key -> _))

  private def _blob_admin_association_mutation_args(
    values: Map[String, String],
    includeSortOrder: Boolean
  ): Vector[(String, String)] = {
    val base = Vector("sourceEntityId", "id", "role").flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))
    if (includeSortOrder)
      base ++ values.get("sortOrder").filter(_.nonEmpty).map("sortOrder" -> _).toVector
    else
      base
  }

  private def _admin_association_args(
    params: Map[String, String]
  ): Vector[(String, String)] = {
    val pageSize = params.get("pageSize").orElse(params.get("limit")).flatMap(_.toIntOption).filter(_ > 0).getOrElse(100)
    val page = params.get("page").flatMap(_.toIntOption).filter(_ > 0).getOrElse {
      params.get("offset").flatMap(_.toIntOption).filter(_ >= 0).map(offset => offset / pageSize + 1).getOrElse(1)
    }
    Vector("page" -> page.toString, "pageSize" -> pageSize.toString) ++
      Vector("domain" -> params.getOrElse("domain", "association")) ++
      Vector("sourceEntityId", "targetEntityId", "targetKind", "role")
        .flatMap(key => params.get(key).filter(_.nonEmpty).map(key -> _))
  }

  private def _admin_association_mutation_args(
    values: Map[String, String],
    includeSortOrder: Boolean
  ): Vector[(String, String)] = {
    val base = Vector("domain", "sourceEntityId", "targetEntityId", "targetKind", "role")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))
    if (includeSortOrder)
      base ++ values.get("sortOrder").filter(_.nonEmpty).map("sortOrder" -> _).toVector
    else
      base
  }

  private def _admin_tag_tree_args(
    params: Map[String, String]
  ): Vector[(String, String)] =
    params.get("tagSpace").filter(_.nonEmpty).map("tagSpace" -> _).toVector

  private def _admin_tag_mutation_args(
    values: Map[String, String],
    includeParent: Boolean
  ): Vector[(String, String)] = {
    val base = Vector("key", "tagSpace", "usageKind", "sortOrder", "title", "description")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))
    val parent =
      if (includeParent)
        values.get("parentTagId").filter(_.nonEmpty).map("parentTagId" -> _).toVector ++
          values.get("parentTagRef").filter(_.nonEmpty).map("parentTagRef" -> _).toVector
      else
        Vector.empty
    base ++ parent
  }

  private def _admin_tag_update_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("tagRef", "tagSpace", "usageKind", "sortOrder", "title", "description", "attributes")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  private def _admin_tag_move_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("tagRef", "tagSpace", "newParentTagRef", "newKey")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  private def _admin_tag_attach_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("sourceEntityId", "tagRef", "tagSpace", "role", "sortOrder")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  private def _admin_tag_detach_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("sourceEntityId", "tagRef", "tagSpace", "role")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  private def _admin_tag_search_args(
    values: Map[String, String]
  ): Vector[(String, String)] =
    Vector("component", "entity", "entityName", "tagRef", "tagSpace", "role", "includeDescendants")
      .flatMap(key => values.get(key).filter(_.nonEmpty).map(key -> _))

  private def _blob_admin_is_truthy(
    value: String
  ): Boolean =
    Set("true", "1", "yes", "on").contains(value.trim.toLowerCase(java.util.Locale.ROOT))

  private def _admin_tag_row(
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val path = record.getString("path").getOrElse("")
    val tagSpace = record.getString("tagSpace").getOrElse("")
    val usage = record.getString("usageKind").getOrElse("")
    val sort = record.getString("sortOrder").getOrElse("")
    s"""<tr>
       |  <td><div><code class="fw-semibold">${_escape(path)}</code></div><div class="small text-body-secondary">${_escape(id)}</div></td>
       |  <td><code>${_escape(record.getString("key").getOrElse(""))}</code></td>
       |  <td><span class="badge text-bg-secondary">${_escape(tagSpace)}</span></td>
       |  <td><span class="badge text-bg-light border">${_escape(usage)}</span></td>
       |  <td>${if (sort.isEmpty) "" else s"""<span class="badge text-bg-light border">${_escape(sort)}</span>"""}</td>
       |  <td>${_escape(record.getString("title").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("description").getOrElse(""))}</td>
       |  <td>${_admin_tag_update_form(record)}</td>
       |  <td>${_admin_tag_move_form(record)}</td>
       |</tr>""".stripMargin
  }

  private def _admin_tag_filter_form(
    params: Map[String, String]
  ): String =
    s"""<form method="get" action="/web/admin/tags" class="row g-2 align-items-end">
       |  <div class="col-12 col-md-7"><label class="form-label" for="tagAdminTagSpace">TagSpace</label><input class="form-control" id="tagAdminTagSpace" name="tagSpace" value="${_escape(params.getOrElse("tagSpace", ""))}" placeholder="default"><div class="form-text">Use blank to open the default TagSpace.</div></div>
       |  <div class="col-6 col-md-2"><button class="btn btn-primary w-100" type="submit">Open</button></div>
       |  <div class="col-6 col-md-2"><a class="btn btn-outline-secondary w-100" href="/web/admin/tags">Default</a></div>
       |</form>""".stripMargin

  private def _admin_tag_create_form(
    params: Map[String, String]
  ): String =
    s"""<form method="post" action="/web/admin/tags/create" class="row g-2 align-items-end">
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateSpace">TagSpace</label><input class="form-control" id="tagCreateSpace" name="tagSpace" value="${_escape(params.getOrElse("tagSpace", ""))}" placeholder="default"></div>
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateKey">Key</label><input class="form-control" id="tagCreateKey" name="key" required></div>
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateParent">Parent tag id/ref</label><input class="form-control" id="tagCreateParent" name="parentTagRef"></div>
       |  <div class="col-12 col-md-4"><label class="form-label" for="tagCreateUsage">Usage</label><input class="form-control" id="tagCreateUsage" name="usageKind" list="tagUsageOptions" value="general"></div>
       |  <div class="col-12 col-md-3"><label class="form-label" for="tagCreateSort">Sort</label><input class="form-control" id="tagCreateSort" name="sortOrder"></div>
       |  <div class="col-12 col-md-5"><label class="form-label" for="tagCreateTitle">Title</label><input class="form-control" id="tagCreateTitle" name="title"></div>
       |  <div class="col-12"><label class="form-label" for="tagCreateDescription">Description</label><input class="form-control" id="tagCreateDescription" name="description"></div>
       |  <div class="col-12 d-flex justify-content-end"><button class="btn btn-primary" type="submit">Create Tag</button></div>
       |  <datalist id="tagUsageOptions"><option value="general"><option value="cms"><option value="navigation"><option value="powertype"></datalist>
       |</form>""".stripMargin

  private def _admin_tag_update_form(
    record: Record
  ): String =
    s"""<form method="post" action="/web/admin/tags/update" class="vstack gap-1">
       |  <input type="hidden" name="tagRef" value="${_escape(record.getString("id").getOrElse(""))}">
       |  <input type="hidden" name="tagSpace" value="${_escape(record.getString("tagSpace").getOrElse(""))}">
       |  <div class="input-group input-group-sm"><span class="input-group-text">Title</span><input class="form-control form-control-sm" name="title" value="${_escape(record.getString("title").getOrElse(""))}" placeholder="Title"></div>
       |  <div class="input-group input-group-sm"><span class="input-group-text">Description</span><input class="form-control form-control-sm" name="description" value="${_escape(record.getString("description").getOrElse(""))}" placeholder="Description"></div>
       |  <div class="input-group input-group-sm"><span class="input-group-text">Usage</span><input class="form-control form-control-sm" name="usageKind" value="${_escape(record.getString("usageKind").getOrElse("general"))}" list="tagUsageOptions"><span class="input-group-text">Sort</span><input class="form-control form-control-sm" name="sortOrder" value="${_escape(record.getString("sortOrder").getOrElse(""))}" placeholder="Sort"><button class="btn btn-outline-primary btn-sm" type="submit">Save</button></div>
       |</form>""".stripMargin

  private def _admin_tag_move_form(
    record: Record
  ): String =
    s"""<form method="post" action="/web/admin/tags/move" class="vstack gap-1">
       |  <input type="hidden" name="tagRef" value="${_escape(record.getString("id").getOrElse(""))}">
       |  <input type="hidden" name="tagSpace" value="${_escape(record.getString("tagSpace").getOrElse(""))}">
       |  <div class="input-group input-group-sm"><span class="input-group-text">Parent</span><input class="form-control form-control-sm" name="newParentTagRef" value="${_escape(record.getString("parentTagId").getOrElse(""))}" placeholder="blank for root"></div>
       |  <div class="input-group input-group-sm"><span class="input-group-text">Key</span><input class="form-control form-control-sm" name="newKey" value="${_escape(record.getString("key").getOrElse(""))}" placeholder="New key"><button class="btn btn-outline-primary btn-sm" type="submit">Move</button></div>
       |</form>""".stripMargin

  private def _admin_tag_search_form(
    params: Map[String, String]
  ): String =
    s"""<form method="get" action="/web/admin/tags" class="row g-2 align-items-end">
       |  <div class="col-md-2"><label class="form-label" for="tagSearchSpace">TagSpace</label><input class="form-control" id="tagSearchSpace" name="tagSpace" value="${_escape(params.getOrElse("tagSpace", ""))}" placeholder="default"></div>
       |  <div class="col-md-2"><label class="form-label" for="tagSearchComponent">Component</label><input class="form-control" id="tagSearchComponent" name="component" value="${_escape(params.getOrElse("component", ""))}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="tagSearchEntity">Entity</label><input class="form-control" id="tagSearchEntity" name="entity" value="${_escape(params.getOrElse("entity", ""))}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="tagSearchRef">Tag ref/path</label><input class="form-control" id="tagSearchRef" name="tagRef" value="${_escape(params.getOrElse("tagRef", ""))}" required></div>
       |  <div class="col-md-1"><label class="form-label" for="tagSearchRole">Role</label><input class="form-control" id="tagSearchRole" name="role" value="${_escape(params.getOrElse("role", "tag"))}"></div>
       |  <div class="col-md-1"><label class="form-label" for="tagSearchDesc">Desc</label><select class="form-select" id="tagSearchDesc" name="includeDescendants"><option value="true"${if (params.get("includeDescendants").forall(_ != "false")) " selected" else ""}>yes</option><option value="false"${if (params.get("includeDescendants").contains("false")) " selected" else ""}>no</option></select></div>
       |  <div class="col-md-1"><button class="btn btn-primary w-100" type="submit">Search</button></div>
       |</form>""".stripMargin

  private def _admin_tag_search_result(
    params: Map[String, String],
    record: Record
  ): String = {
    val component = params.getOrElse("component", "")
    val entity = params.getOrElse("entity", params.getOrElse("entityName", ""))
    val tagRef = params.getOrElse("tagRef", params.getOrElse("tag", ""))
    val tagSpace = params.get("tagSpace").filter(_.nonEmpty).getOrElse("default")
    val role = params.getOrElse("role", "tag")
    val rows = _record_seq(record.asMap.get("data"))
    val emptyAlert =
      if (rows.isEmpty) _admin_tag_empty_alert("No visible Entities matched this Tag filter.")
      else ""
    val body =
      if (rows.isEmpty)
        s"""<tbody>${_admin_empty_table_cell(3, "No visible Entities matched this Tag filter.")}</tbody>"""
      else
        s"""<tbody>${rows.map(row => _admin_tag_search_result_row(component, entity, row)).mkString("\n")}</tbody>"""
    s"""<article class="card admin-card">
       |  <div class="card-body">
       |    <div class="d-flex flex-column flex-md-row justify-content-between gap-2 mb-3">
       |      <div>
       |        <h2 class="card-title h5 mb-1">Tag search result</h2>
       |        <p class="text-body-secondary mb-0">Tag <code>${_escape(tagRef)}</code> in <span class="badge text-bg-secondary">${_escape(tagSpace)}</span>, role <span class="badge text-bg-light border">${_escape(role)}</span>.</p>
       |      </div>
       |      <span class="badge text-bg-light border align-self-start">${rows.size} entities</span>
       |    </div>
       |    ${emptyAlert}
       |    <div class="table-responsive">
       |      <table class="table table-sm table-hover align-middle mb-0">
       |        <thead><tr><th>Entity</th><th>Title/Name</th><th>Raw</th></tr></thead>
       |        ${body}
       |      </table>
       |    </div>
       |  </div>
       |</article>""".stripMargin
  }

  private def _admin_tag_empty_alert(
    message: String
  ): String =
    s"""<div class="alert alert-secondary mb-3" role="status">${_escape(message)}</div>"""

  private def _admin_tag_search_result_row(
    component: String,
    entity: String,
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val label = record.getString("title").orElse(record.getString("name")).orElse(record.getString("label")).getOrElse("")
    val componentPath = NamingConventions.toNormalizedSegment(component)
    val entityPath = NamingConventions.toNormalizedSegment(entity)
    s"""<tr>
       |  <td><a href="/web/${_escape_path_segment(componentPath)}/admin/entities/${_escape_path_segment(entityPath)}/${_escape_path_segment(id)}"><code>${_escape(id)}</code></a></td>
       |  <td>${_escape(label)}</td>
       |  <td><code>${_escape(record.toString)}</code></td>
       |</tr>""".stripMargin
  }

  private def _admin_tag_result_page(
    title: String,
    subtitle: String,
    form: Map[String, String],
    record: Record
  ): Page =
    Page(_simple_page(
      title = title,
      subtitle = subtitle,
      body =
        s"""${_admin_nav_card(Vector("Tags" -> "/web/admin/tags", "Associations" -> "/web/admin/associations"))}
           |${_admin_card("Tag result", _field_table(record.asMap.toVector.map { case (k, v) => k -> _display_value(v) }.sortBy(_._1)))}
           |${_admin_action_row(Vector("Back to Tags" -> s"/web/admin/tags?tagSpace=${_escape_query(form.getOrElse("tagSpace", record.getString("tagSpace").getOrElse("")))}"))}
           |${_manual_raw_details("Raw tag result", record)}""".stripMargin
    ))

  private def _blob_admin_blob_row(
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val displayurl = record.getString("displayUrl").getOrElse("")
    val displaylink =
      if (displayurl.isEmpty) ""
      else
        _blob_admin_safe_display_url(displayurl) match {
          case Some(url) => s"""<a href="${_escape(url)}">display</a>"""
          case None => s"""<code>${_escape(displayurl)}</code>"""
        }
    s"""<tr>
       |  <td><code>${_escape(id)}</code></td>
       |  <td>${_escape(record.getString("kind").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("sourceMode").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("filename").getOrElse(""))}</td>
       |  <td><code>${_escape(record.getString("contentType").getOrElse(""))}</code></td>
       |  <td>${_escape(record.getString("byteSize").getOrElse(""))}</td>
       |  <td><code>${_escape(record.getString("digest").getOrElse(""))}</code></td>
       |  <td>${displaylink}</td>
       |  <td><a href="/web/blob/admin/blobs/${_escape_path_segment(id)}">Open</a></td>
       |</tr>""".stripMargin
  }

  private def _blob_admin_safe_display_url(
    url: String
  ): Option[String] = {
    val trimmed = url.trim
    if (trimmed.startsWith("/web/blob/") || trimmed.startsWith("/rest/v1/blob/"))
      Some(trimmed)
    else
      org.goldenport.cncf.blob.BlobExternalUrlPolicy.normalize(trimmed).toOption
  }

  private def _blob_admin_association_row(
    record: Record
  ): String = {
    val blobid = record.getString("targetEntityId").getOrElse("")
    s"""<tr>
       |  <td><code>${_escape(record.getString("associationId").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("sourceEntityId").getOrElse(""))}</code></td>
       |  <td><code>${_escape(blobid)}</code></td>
       |  <td>${_escape(record.getString("role").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("sortOrder").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("associationDomain").getOrElse(""))}</td>
       |  <td><code>${_escape(record.getString("id").getOrElse(""))}</code></td>
       |  <td><a href="/web/blob/admin/blobs/${_escape_path_segment(blobid)}">Blob</a>${_blob_admin_detach_form(record)}</td>
       |</tr>""".stripMargin
  }

  private def _blob_admin_detach_form(
    record: Record
  ): String = {
    val source = record.getString("sourceEntityId").getOrElse("")
    val blobid = record.getString("targetEntityId").getOrElse("")
    val role = record.getString("role").getOrElse("")
    _admin_confirm_post_form(
      action = "/web/blob/admin/associations/detach",
      label = "Detach",
      title = "Detach Blob association",
      message = s"Detach Blob ${blobid} from Entity ${source}?",
      hiddenFields = Vector("sourceEntityId" -> source, "id" -> blobid, "role" -> role),
      formClass = "d-inline ms-2"
    )
  }

  private def _admin_association_row(
    record: Record,
    includeDetach: Boolean
  ): String = {
    val detach = if (includeDetach) _admin_association_detach_form(record) else ""
    s"""<tr>
       |  <td><code>${_escape(record.getString("associationId").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("sourceEntityId").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("targetEntityId").getOrElse(""))}</code></td>
       |  <td>${_escape(record.getString("targetKind").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("role").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("sortOrder").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("associationDomain").getOrElse(""))}</td>
       |  <td>${detach}</td>
       |</tr>""".stripMargin
  }

  private def _admin_association_detach_form(
    record: Record
  ): String = {
    val domain = record.getString("associationDomain").getOrElse("")
    val source = record.getString("sourceEntityId").getOrElse("")
    val target = record.getString("targetEntityId").getOrElse("")
    val targetKind = record.getString("targetKind").getOrElse("")
    val role = record.getString("role").getOrElse("")
    _admin_confirm_post_form(
      action = "/web/admin/associations/detach",
      label = "Detach",
      title = "Detach Association",
      message = s"Detach ${targetKind} ${target} from Entity ${source}?",
      hiddenFields = Vector(
        "domain" -> domain,
        "sourceEntityId" -> source,
        "targetEntityId" -> target,
        "targetKind" -> targetKind,
        "role" -> role
      )
    )
  }

  private def _blob_admin_blob_fields(
    record: Record
  ): Vector[(String, String)] =
    Vector(
      "id",
      "kind",
      "sourceMode",
      "filename",
      "contentType",
      "byteSize",
      "digest",
      "storageRef",
      "externalUrl",
      "urlSource",
      "createdAt",
      "updatedAt"
    ).flatMap(key => record.getString(key).map(key -> _))

  private def _blob_admin_url_fields(
    record: Record
  ): Vector[(String, String)] =
    Vector("displayUrl", "downloadUrl").flatMap(key => record.getString(key).map(key -> _))

  private def _blob_admin_association_filter_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      _escape(params.getOrElse(key, ""))
    s"""<form method="get" action="/web/blob/admin/associations" class="row g-2 align-items-end">
       |  <div class="col-md-4"><label class="form-label" for="blobAdminSourceEntityId">Source entity</label><input class="form-control" id="blobAdminSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}"></div>
       |  <div class="col-md-4"><label class="form-label" for="blobAdminId">Blob id</label><input class="form-control" id="blobAdminId" name="id" value="${value("id")}"></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminRole">Role</label><input class="form-control" id="blobAdminRole" name="role" value="${value("role")}"></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminLimit">Limit</label><input class="form-control" id="blobAdminLimit" name="limit" value="${_escape(params.getOrElse("limit", "100"))}"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Filter</button> <a class="btn btn-outline-secondary" href="/web/blob/admin/associations">Clear</a></div>
       |</form>""".stripMargin
  }

  private def _blob_admin_association_attach_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      _escape(params.getOrElse(key, ""))
    s"""<form method="post" action="/web/blob/admin/associations/attach" class="row g-2 align-items-end">
       |  <div class="col-md-4"><label class="form-label" for="blobAdminAttachSourceEntityId">Source entity</label><input class="form-control" id="blobAdminAttachSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}" required></div>
       |  <div class="col-md-4"><label class="form-label" for="blobAdminAttachId">Blob id</label><input class="form-control" id="blobAdminAttachId" name="id" value="${value("id")}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminAttachRole">Role</label><input class="form-control" id="blobAdminAttachRole" name="role" value="${value("role")}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="blobAdminAttachSortOrder">Sort</label><input class="form-control" id="blobAdminAttachSortOrder" name="sortOrder"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Attach Blob</button></div>
       |</form>""".stripMargin
  }

  private def _admin_association_filter_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      _escape(params.getOrElse(key, ""))
    val domainValue = _escape(params.getOrElse("domain", "association"))
    s"""<form method="get" action="/web/admin/associations" class="row g-2 align-items-end">
       |  <div class="col-md-2"><label class="form-label" for="associationAdminDomain">Domain</label><input class="form-control" id="associationAdminDomain" name="domain" value="${domainValue}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAdminSourceEntityId">Source entity</label><input class="form-control" id="associationAdminSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}"></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAdminTargetEntityId">Target entity</label><input class="form-control" id="associationAdminTargetEntityId" name="targetEntityId" value="${value("targetEntityId")}"></div>
       |  <div class="col-md-2"><label class="form-label" for="associationAdminTargetKind">Target kind</label><input class="form-control" id="associationAdminTargetKind" name="targetKind" value="${value("targetKind")}"></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAdminRole">Role</label><input class="form-control" id="associationAdminRole" name="role" value="${value("role")}"></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAdminPageSize">Page size</label><input class="form-control" id="associationAdminPageSize" name="pageSize" value="${_escape(params.get("pageSize").orElse(params.get("limit")).getOrElse("100"))}"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Filter</button> <a class="btn btn-outline-secondary" href="/web/admin/associations">Clear</a></div>
       |</form>""".stripMargin
  }

  private def _admin_association_attach_form(
    params: Map[String, String]
  ): String = {
    def value(key: String): String =
      _escape(params.getOrElse(key, ""))
    val domainValue = _escape(params.getOrElse("domain", "association"))
    s"""<form method="post" action="/web/admin/associations/attach" class="row g-2 align-items-end">
       |  <div class="col-md-2"><label class="form-label" for="associationAttachDomain">Domain</label><input class="form-control" id="associationAttachDomain" name="domain" value="${domainValue}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAttachSourceEntityId">Source entity</label><input class="form-control" id="associationAttachSourceEntityId" name="sourceEntityId" value="${value("sourceEntityId")}" required></div>
       |  <div class="col-md-3"><label class="form-label" for="associationAttachTargetEntityId">Target entity</label><input class="form-control" id="associationAttachTargetEntityId" name="targetEntityId" value="${value("targetEntityId")}" required></div>
       |  <div class="col-md-2"><label class="form-label" for="associationAttachTargetKind">Target kind</label><input class="form-control" id="associationAttachTargetKind" name="targetKind" value="${value("targetKind")}" required></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAttachRole">Role</label><input class="form-control" id="associationAttachRole" name="role" value="${value("role")}" required></div>
       |  <div class="col-md-1"><label class="form-label" for="associationAttachSortOrder">Sort</label><input class="form-control" id="associationAttachSortOrder" name="sortOrder"></div>
       |  <div class="col-12"><button class="btn btn-primary" type="submit">Attach Association</button></div>
       |</form>""".stripMargin
  }

  private def _admin_entity_images_section(
    record: Option[Record],
    fallbackId: String
  ): String = {
    val sourceId = record.flatMap(_.getString("sourceEntityId")).orElse(record.flatMap(_.getString("id"))).getOrElse(fallbackId)
    val images = record.map(r => _record_seq(r.getAny("images"))).getOrElse(Vector.empty)
    val representative = record.flatMap(_.getAny("representativeImage")).collect { case r: Record => r }
    val representativeHtml = representative.map(_admin_entity_representative_image).getOrElse(_admin_empty_state("No representative image is currently derived."))
    val table =
      if (images.isEmpty)
        s"""<tbody>${_admin_empty_table_cell(7, "No BlobAttachment images are associated with this Entity.")}</tbody>"""
      else
        s"""<tbody>${images.map(row => _admin_entity_image_row(sourceId, row)).mkString("\n")}</tbody>"""
    s"""<article class="card admin-card mt-3">
       |  <div class="card-body">
       |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
       |      <h2 class="card-title mb-0">Images</h2>
       |      <a class="btn btn-outline-secondary btn-sm" href="/web/blob/admin/associations?sourceEntityId=${_escape_query(sourceId)}">Open Blob associations</a>
       |    </div>
       |    <section class="mb-3">
       |      <h3 class="h6">Representative Image</h3>
       |      ${representativeHtml}
       |    </section>
       |    <section class="mb-3">
       |      <h3 class="h6">Attach Existing Blob</h3>
       |      ${_admin_entity_image_attach_form(sourceId)}
       |    </section>
       |    <section>
       |      <h3 class="h6">Associated Images</h3>
       |      <div class="table-responsive">
       |        <table class="table table-sm align-middle">
       |          <thead><tr><th>Role</th><th>Sort</th><th>Blob</th><th>Kind</th><th>Filename</th><th>Display</th><th>Actions</th></tr></thead>
       |          ${table}
       |        </table>
       |      </div>
       |    </section>
       |  </div>
       |</article>""".stripMargin
  }

  private def _admin_entity_representative_image(
    record: Record
  ): String = {
    val url = _admin_entity_image_display_url(record)
    val id = record.getString("id").getOrElse("")
    val role = record.getString("role").getOrElse("")
    val filename = record.getString("filename").getOrElse(id)
    val media =
      url.flatMap(_blob_admin_safe_display_url) match {
        case Some(safe) =>
          s"""<img src="${_escape(safe)}" alt="${_escape(filename)}" class="img-thumbnail" style="max-width: 12rem; max-height: 8rem;">"""
        case None =>
          s"""<code>${_escape(url.getOrElse(""))}</code>"""
      }
    s"""<div class="d-flex flex-wrap gap-3 align-items-center">
       |  ${media}
       |  <div><div><strong>${_escape(role)}</strong></div><code>${_escape(id)}</code></div>
       |</div>""".stripMargin
  }

  private def _admin_entity_image_row(
    sourceId: String,
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val display = _admin_entity_image_display_url(record).flatMap(_blob_admin_safe_display_url) match {
      case Some(url) => s"""<a href="${_escape(url)}">display</a>"""
      case None => _admin_entity_image_display_url(record).map(x => s"""<code>${_escape(x)}</code>""").getOrElse("")
    }
    s"""<tr>
       |  <td>${_escape(record.getString("role").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("sortOrder").getOrElse(""))}</td>
       |  <td><code>${_escape(id)}</code></td>
       |  <td>${_escape(record.getString("kind").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("filename").getOrElse(""))}</td>
       |  <td>${display}</td>
       |  <td><a href="/web/blob/admin/blobs/${_escape_path_segment(id)}">Blob</a>${_admin_entity_image_detach_form(sourceId, record)}</td>
       |</tr>""".stripMargin
  }

  private def _admin_entity_image_display_url(
    record: Record
  ): Option[String] =
    record.getString("displayPath").orElse(record.getString("displayUrl"))

  private def _admin_entity_image_attach_form(
    sourceId: String
  ): String =
    s"""<form method="post" action="/web/blob/admin/associations/attach" class="row g-2 align-items-end">
       |  <input type="hidden" name="sourceEntityId" value="${_escape(sourceId)}">
       |  <div class="col-md-5"><label class="form-label" for="entityImageAttachId">Blob id</label><input class="form-control" id="entityImageAttachId" name="id" required></div>
       |  <div class="col-md-3"><label class="form-label" for="entityImageAttachRole">Role</label><input class="form-control" id="entityImageAttachRole" name="role" list="entityImageRoleOptions" required></div>
       |  <div class="col-md-2"><label class="form-label" for="entityImageAttachSortOrder">Sort</label><input class="form-control" id="entityImageAttachSortOrder" name="sortOrder"></div>
       |  <div class="col-md-2"><button class="btn btn-primary w-100" type="submit">Attach</button></div>
       |  <datalist id="entityImageRoleOptions"><option value="primary"><option value="cover"><option value="thumbnail"><option value="gallery"><option value="inline"></datalist>
       |</form>""".stripMargin

  private def _admin_entity_image_detach_form(
    sourceId: String,
    record: Record
  ): String = {
    val id = record.getString("id").getOrElse("")
    val role = record.getString("role").getOrElse("")
    _admin_confirm_post_form(
      action = "/web/blob/admin/associations/detach",
      label = "Detach",
      title = "Detach Entity image",
      message = s"Detach Blob ${id} from Entity ${sourceId}?",
      hiddenFields = Vector("sourceEntityId" -> sourceId, "id" -> id, "role" -> role),
      formClass = "d-inline ms-2"
    )
  }

  private def _admin_entity_tags_section(
    subsystem: Subsystem,
    record: Option[Record],
    fallbackId: String,
    values: Map[String, String]
  ): String = {
    val sourceId = record.flatMap(_.getString("sourceEntityId")).orElse(record.flatMap(_.getString("id"))).getOrElse(fallbackId)
    val tagSpace = values.getOrElse("tagSpace", "")
    val summary = _admin_operation_record(
      subsystem,
      "/tag/tag/tag_list_entity_tags",
      Record.dataAuto(
        "sourceEntityId" -> sourceId,
        "tagSpace" -> tagSpace
      )
    )
    val rows = summary.map { r =>
      val tags = _record_seq(r.asMap.get("tags"))
      val associations = _record_seq(r.asMap.get("associations"))
      tags.zipWithIndex.map { case (tag, index) =>
        _admin_entity_tag_row(sourceId, tagSpace, tag, associations.lift(index))
      }
    }.getOrElse(Vector.empty)
    val table =
      if (rows.isEmpty)
        s"""<tbody>${_admin_empty_table_cell(8, "No Tags are attached to this Entity for the selected TagSpace.")}</tbody>"""
      else
        s"""<tbody>${rows.mkString("\n")}</tbody>"""
    s"""<article class="card admin-card mt-3">
       |  <div class="card-body">
       |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
       |      <h2 class="card-title mb-0">Tags</h2>
       |      <a class="btn btn-outline-secondary btn-sm" href="/web/admin/tags?tagSpace=${_escape_query(tagSpace)}&amp;sourceEntityId=${_escape_query(sourceId)}">Open Tags</a>
       |    </div>
       |    <section class="mb-3">
       |      <h3 class="h6">Attach Tag</h3>
       |      ${_admin_entity_tag_attach_form(sourceId, tagSpace)}
       |    </section>
       |    <section>
       |      <h3 class="h6">Attached Tags</h3>
       |      <div class="table-responsive">
       |        <table class="table table-sm align-middle">
       |          <thead><tr><th>Path</th><th>TagSpace</th><th>Role</th><th>Sort</th><th>Usage</th><th>Title</th><th>Description</th><th>Actions</th></tr></thead>
       |          ${table}
       |        </table>
       |      </div>
       |    </section>
       |  </div>
       |</article>""".stripMargin
  }

  private def _admin_entity_tag_row(
    sourceId: String,
    tagSpace: String,
    record: Record,
    association: Option[Record]
  ): String = {
    val path = record.getString("path").getOrElse("")
    val role = association.flatMap(_.getString("role")).getOrElse("tag")
    val sortOrder = association.flatMap(_.getString("sortOrder")).getOrElse("")
    s"""<tr>
       |  <td><code>${_escape(path)}</code></td>
       |  <td>${_escape(record.getString("tagSpace").getOrElse(tagSpace))}</td>
       |  <td>${_escape(role)}</td>
       |  <td>${_escape(sortOrder)}</td>
       |  <td>${_escape(record.getString("usageKind").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("title").getOrElse(""))}</td>
       |  <td>${_escape(record.getString("description").getOrElse(""))}</td>
       |  <td>${_admin_entity_tag_detach_form(sourceId, tagSpace, path, role)}</td>
       |</tr>""".stripMargin
  }

  private def _admin_entity_tag_attach_form(
    sourceId: String,
    tagSpace: String
  ): String =
    s"""<form method="post" action="/web/admin/tags/attach" class="row g-2 align-items-end">
       |  <input type="hidden" name="sourceEntityId" value="${_escape(sourceId)}">
       |  <div class="col-md-3"><label class="form-label" for="entityTagAttachSpace">TagSpace</label><input class="form-control" id="entityTagAttachSpace" name="tagSpace" value="${_escape(tagSpace)}" placeholder="default"></div>
       |  <div class="col-md-4"><label class="form-label" for="entityTagAttachRef">Tag ref/path</label><input class="form-control" id="entityTagAttachRef" name="tagRef" required></div>
       |  <div class="col-md-2"><label class="form-label" for="entityTagAttachRole">Role</label><input class="form-control" id="entityTagAttachRole" name="role" value="tag" required></div>
       |  <div class="col-md-1"><label class="form-label" for="entityTagAttachSort">Sort</label><input class="form-control" id="entityTagAttachSort" name="sortOrder"></div>
       |  <div class="col-md-2"><button class="btn btn-primary w-100" type="submit">Attach</button></div>
       |</form>""".stripMargin

  private def _admin_entity_tag_detach_form(
    sourceId: String,
    tagSpace: String,
    tagRef: String,
    role: String
  ): String =
    _admin_confirm_post_form(
      action = "/web/admin/tags/detach",
      label = "Detach",
      title = "Detach Tag",
      message = s"Detach Tag ${tagRef} from Entity ${sourceId}?",
      hiddenFields = Vector(
        "sourceEntityId" -> sourceId,
        "tagSpace" -> tagSpace,
        "tagRef" -> tagRef,
        "role" -> role
      )
    )

  private def _admin_entity_associations_section(
    subsystem: Subsystem,
    component: Component,
    entityName: String,
    record: Option[Record],
    fallbackId: String
  ): String = {
    val relationships = _admin_entity_association_relationships(component, entityName)
    if (relationships.isEmpty)
      ""
    else {
      val sourceId = record.flatMap(_.getString("sourceEntityId")).orElse(record.flatMap(_.getString("id"))).getOrElse(fallbackId)
      val sourceIds = Vector(
        record.flatMap(_.getString("sourceEntityId")),
        record.flatMap(_.getString("id")),
        Some(fallbackId)
      ).flatten.filter(_.nonEmpty).distinct
      val sections = relationships.map { relationship =>
        val rows = _deduplicate_association_rows(
          sourceIds.flatMap(id => _admin_entity_association_rows(subsystem, relationship, id))
        )
        val table =
          if (rows.isEmpty)
            s"""<tbody>${_admin_empty_table_cell(8, "No Associations are currently linked for this relationship.")}</tbody>"""
          else
            s"""<tbody>${rows.map(row => _admin_association_row(row, includeDetach = true)).mkString("\n")}</tbody>"""
        val attach = _admin_entity_association_attach_form(sourceId, relationship)
        val openlink = relationship.associationDomain.filter(_.nonEmpty).map { domain =>
          s"""<a class="btn btn-outline-secondary btn-sm" href="/web/admin/associations?domain=${_escape_query(domain)}&amp;sourceEntityId=${_escape_query(sourceId)}">Open Associations</a>"""
        }.getOrElse("")
        s"""<section class="mb-3">
           |  <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between">
           |    <h3 class="h6 mb-0">${_escape(relationship.name)}</h3>
           |    ${openlink}
           |  </div>
           |  ${attach}
           |  <div class="table-responsive mt-2">
           |    <table class="table table-sm align-middle">
           |      <thead><tr><th>Association</th><th>Source Entity</th><th>Target Entity</th><th>Target kind</th><th>Role</th><th>Sort</th><th>Domain</th><th>Actions</th></tr></thead>
           |      ${table}
           |    </table>
           |  </div>
           |</section>""".stripMargin
      }.mkString("\n")
      s"""<article class="card admin-card mt-3">
         |  <div class="card-body">
         |    <h2 class="card-title">Associations</h2>
         |    ${sections}
         |  </div>
         |</article>""".stripMargin
    }
  }

  private def _deduplicate_association_rows(
    rows: Vector[Record]
  ): Vector[Record] =
    rows.foldLeft(Vector.empty[Record]) { (acc, row) =>
      val key = row.getString("id").getOrElse(row.toString)
      if (acc.exists(existing => existing.getString("id").getOrElse(existing.toString) == key))
        acc
      else
        acc :+ row
    }

  private def _admin_entity_association_relationships(
    component: Component,
    entityName: String
  ): Vector[CmlEntityRelationshipDefinition] =
    component.relationshipDefinitions.filter { relationship =>
      NamingConventions.equivalentByNormalized(relationship.sourceEntityName, entityName) &&
        relationship.storageMode == CmlEntityRelationshipDefinition.StorageAssociationRecord &&
        !_is_blob_attachment_relationship(relationship)
    }

  private def _is_blob_attachment_relationship(
    relationship: CmlEntityRelationshipDefinition
  ): Boolean =
    relationship.associationDomain.contains("blob_attachment") &&
      relationship.targetKind.exists(NamingConventions.equivalentByNormalized(_, "blob"))

  private def _admin_entity_association_rows(
    subsystem: Subsystem,
    relationship: CmlEntityRelationshipDefinition,
    sourceId: String
  ): Vector[Record] =
    relationship.associationDomain.toVector.flatMap { domain =>
      _admin_operation_record(
        subsystem,
        "/admin/association/admin_list_associations",
        Record.data(
          "domain" -> domain,
          "sourceEntityId" -> sourceId,
          "targetKind" -> relationship.targetKind.getOrElse(""),
          "pageSize" -> 100
        )
      ).toVector.flatMap(record => _record_seq(record.asMap.get("data")))
    }

  private def _admin_entity_association_attach_form(
    sourceId: String,
    relationship: CmlEntityRelationshipDefinition
  ): String =
    (relationship.associationDomain, relationship.targetKind) match {
      case (Some(domain), Some(targetKind)) if domain.nonEmpty && targetKind.nonEmpty =>
        val role = relationship.targetRole.orElse(relationship.sourceRole).getOrElse(relationship.name.split("\\.").lastOption.getOrElse("related"))
        s"""<form method="post" action="/web/admin/associations/attach" class="row g-2 align-items-end mt-2">
           |  <input type="hidden" name="domain" value="${_escape(domain)}">
           |  <input type="hidden" name="sourceEntityId" value="${_escape(sourceId)}">
           |  <input type="hidden" name="targetKind" value="${_escape(targetKind)}">
           |  <div class="col-md-5"><label class="form-label" for="associationTarget-${_escape(NamingConventions.toNormalizedSegment(relationship.name))}">Target entity</label><input class="form-control" id="associationTarget-${_escape(NamingConventions.toNormalizedSegment(relationship.name))}" name="targetEntityId" required></div>
           |  <div class="col-md-3"><label class="form-label" for="associationRole-${_escape(NamingConventions.toNormalizedSegment(relationship.name))}">Role</label><input class="form-control" id="associationRole-${_escape(NamingConventions.toNormalizedSegment(relationship.name))}" name="role" value="${_escape(role)}" required></div>
           |  <div class="col-md-2"><label class="form-label" for="associationSort-${_escape(NamingConventions.toNormalizedSegment(relationship.name))}">Sort</label><input class="form-control" id="associationSort-${_escape(NamingConventions.toNormalizedSegment(relationship.name))}" name="sortOrder"></div>
           |  <div class="col-md-2"><button class="btn btn-primary w-100" type="submit">Attach</button></div>
           |</form>""".stripMargin
      case _ =>
        _admin_empty_state("This relationship is metadata-only because associationDomain or targetKind is not defined.")
    }

  private def _blob_admin_paging(
    basePath: String,
    params: Map[String, String],
    record: Record
  ): String = {
    val offset = record.getInt("offset").getOrElse(0)
    val limit = record.getInt("limit").getOrElse(100)
    val hasmore = record.getBoolean("hasMore").getOrElse(false)
    val cleanparams = params -- Set("offset", "page", "pageSize")
    val querybase = cleanparams.toVector.sortBy(_._1).map { case (k, v) => s"${_escape_query(k)}=${_escape_query(v)}" }
    val prev =
      if (offset <= 0) ""
      else {
        val prevquery = (querybase :+ s"offset=${math.max(0, offset - limit)}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${prevquery}">Previous</a>"""
      }
    val next =
      if (!hasmore) ""
      else {
        val nextquery = (querybase :+ s"offset=${offset + limit}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${nextquery}">Next</a>"""
      }
    s"""<div class="d-flex flex-wrap gap-2 align-items-center"><span class="text-secondary">offset ${offset}, limit ${limit}</span>${prev}${next}</div>"""
  }

  private def _admin_association_paging(
    basePath: String,
    params: Map[String, String],
    record: Record
  ): String = {
    val page = record.getInt("page").getOrElse(1)
    val pageSize = record.getInt("pageSize").getOrElse(100)
    val hasnext = record.getBoolean("hasNext").getOrElse(false)
    val cleanparams = params -- Set("offset", "limit", "page", "pageSize")
    val querybase = cleanparams.toVector.sortBy(_._1).map { case (k, v) => s"${_escape_query(k)}=${_escape_query(v)}" } :+
      s"pageSize=${pageSize}"
    val prev =
      if (page <= 1) ""
      else {
        val prevquery = (querybase :+ s"page=${page - 1}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${prevquery}">Previous</a>"""
      }
    val next =
      if (!hasnext) ""
      else {
        val nextquery = (querybase :+ s"page=${page + 1}").mkString("&")
        s"""<a class="btn btn-outline-secondary btn-sm" href="${basePath}?${nextquery}">Next</a>"""
      }
    s"""<div class="d-flex flex-wrap gap-2 align-items-center"><span class="text-secondary">page ${page}, page size ${pageSize}</span>${prev}${next}</div>"""
  }

  private def _record_seq(
    value: Option[Any]
  ): Vector[Record] =
    value.toVector.flatMap {
      case r: Record => Vector(r)
      case xs: Vector[?] => xs.collect { case r: Record => r }
      case xs: Seq[?] => xs.toVector.collect { case r: Record => r }
      case xs: Array[?] => xs.toVector.collect { case r: Record => r }
      case _ => Vector.empty
    }

  private def _display_value(value: Any): String =
    value match {
      case null => ""
      case Some(x) => _display_value(x)
      case None => ""
      case r: Record => r.show
      case xs: Seq[?] => xs.map(_display_value).mkString(", ")
      case x => x.toString
    }

  private def _system_admin_job_row(
    model: JobQueryReadModel
  ): String = {
    val target = _job_target(model)
    val trace = model.lineage.correlationId
      .orElse(model.debug.parameters.get("traceId"))
      .getOrElse("")
    s"""<tr><td><code>${_escape(model.jobId.value)}</code></td><td>${_escape(model.status.toString)}</td><td><code>${_escape(target)}</code></td><td>${_escape(model.resultSummary.message.getOrElse(""))}</td><td><code>${_escape(trace)}</code></td><td>${_escape(model.updatedAt.toString)}</td><td><a href="/web/system/admin/jobs/${_escape_path_segment(model.jobId.value)}">Open</a></td></tr>"""
  }

  private def _job_target(
    model: JobQueryReadModel
  ): String =
    model.tasks.tasks.headOption.map { task =>
      Vector(task.component, task.service, task.operation).flatten.filter(_.nonEmpty).mkString(".")
    }.filter(_.nonEmpty).orElse(model.debug.requestSummary).getOrElse("")

  private def _job_calltree_panel(
    record: Record
  ): String =
    s"""<pre class="bg-light border rounded p-3"><code>${_escape(record.show)}</code></pre>"""

  def renderComponentAdmin(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map(renderComponentAdmin(_, NamingConventions.toNormalizedSegment(componentName), webDescriptor))

  def renderComponentAdminDescriptor(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      Page(_simple_page(
        title = s"${_escape(component.name)} Web Descriptor",
        subtitle = "Component Management Console descriptor view",
        body =
          s"""${_admin_nav_card(Vector(
               "Component admin" -> s"/web/${componentPath}/admin",
               "System descriptor" -> "/web/system/admin/descriptor"
             ))}
             |${_web_descriptor_section_nav}
             |${_web_descriptor_control_tables(webDescriptor, Some(componentPath))}
             |${_web_descriptor_asset_composition_table(webDescriptor, Some(componentPath))}
             |${_web_descriptor_json_panel(
               "completed-descriptor",
               "Completed Descriptor JSON",
               "The completed view applies framework defaults and resolves component route placeholders for this component.",
               _web_descriptor_json(webDescriptor, completed = true, componentSegment = Some(componentPath))
             )}
             |${_web_descriptor_json_panel(
               "configured-descriptor",
               "Configured Descriptor JSON",
               "The configured view keeps explicit descriptor entries for comparison.",
               _web_descriptor_json(webDescriptor, completed = false)
             )}""".stripMargin
      ))
    }

  def renderSystemManual(
    subsystem: Subsystem
  ): Page =
    subsystem.components.headOption match {
      case Some(component) =>
        Page(_system_manual_page(subsystem, component))
      case None =>
        Page(_simple_page(
          title = "System Specification",
          subtitle = "Generated runtime specification",
          body = _admin_card("No components", "<p>No component reference entries are available.</p>")
        ))
    }

  def renderSystemDocument(
    subsystem: Subsystem
  ): Page = {
    val body =
      s"""${_manual_card("Generated documents",
           s"""<p>CNCF-generated runtime documents expose implementation-facing specifications and machine-readable interface descriptions.</p>
              |${_admin_link_list_group(Vector(
                "Generated Specification" -> "/web/system/document/specification",
                "OpenAPI JSON" -> "/web/system/document/specification/openapi.json",
                "MCP endpoint" -> "/mcp",
                "System dashboard" -> "/web/system/dashboard",
                "Console" -> "/web/console"
              ))}""".stripMargin)}
         |${_manual_card("Component documents", _manual_component_document_links(subsystem.components))}
         |${_manual_card("Document model", """<p class="mb-0">Use <strong>Specification</strong> for generated CNCF metadata. Component-packaged <strong>User Guide</strong> and <strong>Reference Manual</strong> documents remain product documents owned by each component.</p>""")}""".stripMargin
    Page(_simple_page("System Documents", "Specifications and component-packaged documents", body))
  }

  def renderRuntimeLanding(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor()
  ): Page = {
    val runtime = RuntimeConfig.from(subsystem.configuration)
    val appComponents = subsystem.components.filterNot(_.origin == ComponentOrigin.Builtin)
    val effectiveComponents =
      if (appComponents.nonEmpty) appComponents
      else subsystem.components
    val componentLinks = effectiveComponents.map { component =>
      val path = NamingConventions.toNormalizedSegment(component.name)
      val appLinks = webDescriptor.routeAppsForComponent(component.name) match {
        case Vector() =>
          Vector("App" -> s"/web/${_escape(path)}")
        case Vector(app) =>
          Vector("App" -> s"/web/${_escape(app)}")
        case apps =>
          apps.map(app => s"App: ${app}" -> s"/web/${_escape(app)}")
      }
      s"""<div class="list-group-item">
         |  <div class="d-flex flex-wrap justify-content-between gap-2 align-items-center">
         |    <strong>${_escape(component.name)}</strong>
         |    ${_admin_action_row(appLinks ++ Vector(
           "Admin" -> s"/web/${_escape(path)}/admin",
           "Document" -> s"/web/${_escape(path)}/document"
         ), primary = false)}
         |  </div>
         |</div>""".stripMargin
    }.mkString("\n")
    val recommendations =
      if (componentLinks.isEmpty)
        _admin_card(
          "Recommended Links",
          _admin_link_list_group(Vector(
            "System documents" -> "/web/system/document",
            "System admin" -> "/web/system/admin",
            "System dashboard" -> "/web/system/dashboard",
            "Performance" -> "/web/system/performance"
          ))
        )
      else
        _admin_card(
          "Recommended Links",
          s"""${_admin_link_list_group(Vector(
               "System documents" -> "/web/system/document",
               "System admin" -> "/web/system/admin",
               "System dashboard" -> "/web/system/dashboard",
               "Performance" -> "/web/system/performance"
             ))}
             |<h3 class="h6 mt-3">Components</h3>
             |<div class="list-group">${componentLinks}</div>""".stripMargin
        )
    Page(_simple_page(
      title = "CNCF Runtime Help",
      subtitle = "Development and demo entry points",
      body =
        s"""${_admin_card(
             "Runtime",
             s"""${_admin_table(
                  None,
                  s"""<tr><th>Subsystem</th><td>${_escape(subsystem.name)}</td></tr>
                     |<tr><th>Operation mode</th><td>${_escape(runtime.operationMode.name)}</td></tr>
                     |<tr><th>Components</th><td>${effectiveComponents.size}</td></tr>""".stripMargin,
                  tableClass = "table table-sm align-middle mb-0"
                )}
                |<p class="mt-3 mb-0">This page is shown only outside production when no explicit root or <code>/web</code> route is configured.</p>""".stripMargin
           )}
           |${recommendations}""".stripMargin
    ))
  }

  def renderComponentManual(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
    } yield Page(_manual_page(
      title = s"${_escape(component.name)} Specification",
      subtitle = "Generated component specification",
      component = component,
      selector = Some(component.name),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(componentName)}/document/specification",
      childNames = component.protocol.services.services.map(_.name).toVector
    ))

  def renderComponentDocument(
    subsystem: Subsystem,
    componentName: String,
    documents: Vector[DocumentLink] = Vector.empty
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
    } yield {
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val generated =
        Vector(
          "Generated Specification" -> s"/web/${_escape(componentPath)}/document/specification",
          "OpenAPI JSON" -> "/web/system/document/specification/openapi.json",
          "MCP endpoint" -> "/mcp"
        )
      val packaged =
        if (documents.isEmpty)
          _web_empty_state("No packaged User Guide, Reference Manual, or component Specification documents were found.")
        else
          _admin_link_list_group(documents.map(x => x.title -> x.href))
      val body =
        s"""${_manual_card("Generated specification",
             s"""<p>CNCF generates this read-only specification from component, service, operation, schema, and projection metadata.</p>
                |${_admin_link_list_group(generated)}""".stripMargin)}
           |${_manual_card("Packaged component documents", packaged)}
           |${_manual_card("Document roles",
             """<div class="table-responsive">
                |  <table class="table table-sm table-hover align-middle mb-0">
                |    <tbody>
                |      <tr><th>Specification</th><td>Generated or component-packaged technical contract for developers and operators.</td></tr>
                |      <tr><th>User Guide</th><td>Task-oriented guide for people using the component or application feature.</td></tr>
                |      <tr><th>Reference Manual</th><td>Human-authored detailed reference that complements generated metadata.</td></tr>
                |    </tbody>
                |  </table>
                |</div>""".stripMargin)}""".stripMargin
      Page(_simple_page(s"${_escape(component.name)} Documents", "Specifications, user guides, and reference manuals", body))
    }

  def renderComponentManualService(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, serviceName))
    } yield Page(_manual_page(
      title = s"${_escape(component.name)}.${_escape(service.name)} Specification",
      subtitle = "Generated service specification",
      component = component,
      selector = Some(s"${component.name}.${service.name}"),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(componentName)}/document/specification/${NamingConventions.toNormalizedSegment(service.name)}",
      childNames = service.operations.operations.map(_.name).toVector
    ))

  def renderComponentManualOperation(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, serviceName))
      operation <- service.operations.operations.find(o => NamingConventions.equivalentByNormalized(o.name, operationName))
    } yield Page(_manual_page(
      title = s"${_escape(component.name)}.${_escape(service.name)}.${_escape(operation.name)} Specification",
      subtitle = "Generated operation specification",
      component = component,
      selector = Some(s"${component.name}.${service.name}.${operation.name}"),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(componentName)}/document/specification/${NamingConventions.toNormalizedSegment(service.name)}/${NamingConventions.toNormalizedSegment(operation.name)}",
      childNames = Vector.empty
    ))

  def renderComponentAdminEntities(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val rows = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).map { descriptor =>
        val entityPath = NamingConventions.toNormalizedSegment(descriptor.entityName)
        val workingsetpolicy = descriptor.effectiveWorkingSetPolicy.map(_.label).getOrElse("none")
        val policysource = descriptor.effectiveWorkingSetPolicySource.map(_.toString.toLowerCase).getOrElse("none")
        val collection = component.entitySpace.entityOption[Any](descriptor.collectionId.name)
        val workingSetStatus = collection.map(_.workingSetStatus)
        val workingSetState = workingSetStatus.map(_.state.label).getOrElse("unknown")
        val residentCount = collection.map(_.residentCount).getOrElse(0)
        val workingSetError = workingSetStatus.flatMap(_.error).getOrElse("")
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/entities/${entityPath}">${_escape(descriptor.entityName)}</a></td>
           |  <td><code>${_escape(descriptor.collectionId.name)}</code></td>
           |  <td>${_escape(descriptor.entityKind.toString)}</td>
           |  <td>${_escape(descriptor.usageKind.toString)}</td>
           |  <td>${_escape(descriptor.effectiveOperationKind.toString)}</td>
           |  <td>${_escape(descriptor.applicationDomain.toString)}</td>
           |  <td>${descriptor.workingSet.map(_.entityIds.size.toString).getOrElse("none")}</td>
           |  <td><code>${_escape(workingsetpolicy)}</code></td>
           |  <td>${_escape(policysource)}</td>
           |  <td><code>${_escape(workingSetState)}</code></td>
           |  <td>${residentCount}</td>
           |  <td>${_escape(workingSetError)}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          _admin_empty_state("No entity runtime descriptors are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>Entity</th><th>Collection</th><th>Kind</th><th>Usage</th><th>Operation</th><th>Domain</th><th>Working set</th><th>Policy</th><th>Source</th><th>Status</th><th>Resident</th><th>Error</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      val nav = _admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} Entity Administration",
        subtitle = "Entity CRUD management baseline",
        body =
          s"""${nav}
             |${_admin_card("Entity CRUD", body)}
             |${_admin_storage_shape_section(component, None, detailed = false)}""".stripMargin
      ))
    }

  def renderComponentAdminEntityType(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    pageContext: Map[String, String] = Map.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "entity", entityPath)
      )
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val displayFields = _admin_entity_display_fields(
        component,
        entityPath,
        WebTableColumnResolver.defaultViewName,
        webSchema.fieldNames
      )
      val searchContext =
        if (effectivePageRequest.effectiveIncludeTotal)
          pageContext + ("includeTotal" -> "true")
        else
          pageContext
      val searchFields = _admin_entity_searchable_fields(component, entityPath, WebTableColumnResolver.defaultViewName)
      val filterFields = displayFields
      val searchProfile = SearchPlanningProfile(
        searchableFields = searchFields,
        filterFields = filterFields,
        sortableFields = displayFields
      )
      val semanticUnsupported = _admin_search_mode(searchContext).exists(_ != SearchMode.FullText)
      val searchValues =
        if (semanticUnsupported)
          Map.empty[String, String]
        else
          _admin_search_values(searchContext, searchProfile)
      val result =
        if (semanticUnsupported)
          _AdminListResult(Vector.empty, 1, effectivePageRequest.pageSize, false, Some(0), Vector.empty)
        else
          _admin_entity_list(subsystem, componentPath, entityPath, effectivePageRequest, searchValues)
      val warningHtml = _admin_warnings(result.warnings)
      val searchFeedback =
        if (semanticUnsupported)
          """<div class="alert alert-warning admin-search-feedback" role="alert">Semantic or hybrid search is not configured for this Static Form page.</div>"""
        else
          ""
      val searchHref = _admin_search_href(basePath, searchContext, searchProfile)
      val searchControls = _admin_search_card(
        basePath,
        searchContext,
        searchProfile,
        filterFields,
        displayFields,
        result.total,
        result.items.size
      )
      val table = _admin_read_result_list_table(
        result.items,
        displayFields,
        basePath,
        "No records are currently available for this entity.",
        includeEdit = true,
        linkContext = searchContext ++ Map(
          "paging.page" -> result.page.toString,
          "paging.pageSize" -> result.pageSize.toString
        )
      )
      val nav = _admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Entity types" -> s"/web/${componentPath}/admin/entities",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} Administration",
        subtitle = "Entity record list baseline",
        body =
          s"""${nav}
             |${_admin_storage_shape_section(component, Some(entityPath), detailed = true)}
             |${searchControls}
             |${searchFeedback}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <div>
             |        <h2 class="card-title mb-1">${_escape(entityLabel)} records</h2>
             |        <p class="card-text text-body-secondary">List with paging${result.total.map(t => s" · total ${_escape(t.toString)}").getOrElse("")}</p>
             |      </div>
             |      <a class="btn btn-primary" href="${_escape(basePath)}/new">New ${_escape(entityLabel)}</a>
             |    </div>
             |    ${warningHtml}
             |    ${table}
             |    ${_paging_nav(result.page, result.pageSize, result.total, searchHref, Some(result.hasNext))}
             |  </div>
             |</article>""".stripMargin
      ))
    }

  private def _admin_storage_shape_section(
    component: Component,
    entityName: Option[String],
    detailed: Boolean
  ): String = {
    val describe = DescribeProjection.project(component, Some(component.name))
    val entities = _manual_record_seq(describe.asMap.get("entityCollections")).filter { record =>
      entityName.forall(name => record.getString("entityName").exists(NamingConventions.equivalentByNormalized(_, name)))
    }
    if (entities.isEmpty)
      entityName.map(_ => _admin_card("Storage shape", _admin_empty_state("No storage-shape metadata is registered for this entity."))).getOrElse("")
    else {
      val summaryRows = entities.map(_admin_storage_shape_summary_row).mkString("\n")
      val detailHtml =
        if (detailed)
          entities.map(_admin_storage_shape_field_table).mkString("\n")
        else
          ""
      _admin_card(
        "Storage shape",
        s"""<p class="text-body-secondary">Read-only effective SimpleEntity storage-shape metadata.</p>
           |<div class="table-responsive">
           |  <table class="table table-sm table-hover align-middle">
           |    <thead><tr><th>Entity</th><th>Collection</th><th>Memory policy</th><th>Working-set policy</th><th>Storage policy</th><th>Fields</th></tr></thead>
           |    <tbody>
           |      ${summaryRows}
           |    </tbody>
           |  </table>
           |</div>
           |${detailHtml}""".stripMargin
      )
    }
  }

  private def _admin_storage_shape_summary_row(
    record: Record
  ): String = {
    val shape = _manual_record_values(record.asMap.get("storageShape"))
    val fields = _manual_record_seq(shape.get("fields"))
    val fieldSummary =
      if (fields.isEmpty)
        "none"
      else
        fields.flatMap(_.getString("classification")).groupBy(identity).toVector.sortBy(_._1).map {
          case (classification, rows) => s"$classification=${rows.size}"
        }.mkString(", ")
    s"""<tr>
       |  <td><code>${_escape(record.getString("entityName").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("collectionId").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("memoryPolicy").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("workingSetPolicy").getOrElse("-"))}</code></td>
       |  <td><code>${_escape(shape.get("policy").flatMap(_manual_scalar).getOrElse(""))}</code></td>
       |  <td>${_escape(fieldSummary)}</td>
       |</tr>""".stripMargin
  }

  private def _admin_storage_shape_field_table(
    record: Record
  ): String = {
    val entityName = record.getString("entityName").getOrElse("")
    val shape = _manual_record_values(record.asMap.get("storageShape"))
    val fields = _manual_record_seq(shape.get("fields"))
    if (fields.isEmpty)
      _admin_empty_state("No storage-shape field metadata.")
    else {
      val rows = fields.map { field =>
        s"""<tr>
           |  <td><code>${_escape(field.getString("logicalName").getOrElse(""))}</code></td>
           |  <td><code>${_escape(field.getString("storageName").getOrElse(""))}</code></td>
           |  <td>${_escape(field.getString("classification").getOrElse(""))}</td>
           |  <td>${_escape(field.getString("storageKind").getOrElse(""))}</td>
           |  <td>${_escape(field.getString("dataType").getOrElse(""))}</td>
           |  <td>${_escape(field.getString("source").getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<section class="mt-3">
         |  <h3 class="h6">${_escape(entityName)} storage fields</h3>
         |  <div class="table-responsive">
         |    <table class="table table-sm table-hover align-middle admin-storage-shape-fields">
         |      <thead><tr><th>Logical name</th><th>Storage name</th><th>Classification</th><th>Storage kind</th><th>Data type</th><th>Source</th></tr></thead>
         |      <tbody>
         |        ${rows}
         |      </tbody>
         |    </table>
         |  </div>
         |</section>""".stripMargin
    }
  }

  def renderComponentAdminEntityDetail(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    values: Map[String, String] = Map.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val routeId = _entity_route_id(id)
      val querySuffix = _hidden_form_context_query_suffix(values)
      val readRecord = _admin_entity_read_record(subsystem, componentPath, entityPath, id)
      val body = _admin_entity_record_table_from_record(subsystem, component, componentPath, entityPath, id, readRecord, webDescriptor)
      val images = _admin_entity_images_section(readRecord, id)
      val tags = _admin_entity_tags_section(subsystem, readRecord, id, values)
      val associations = _admin_entity_associations_section(subsystem, component, entityPath, readRecord, id)
      val nav = _admin_nav_card(Vector(
        s"Back to ${entityLabel} records" -> s"${basePath}${querySuffix}",
        "Entity types" -> s"/web/${componentPath}/admin/entities"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} Detail",
        subtitle = "Entity record detail baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <h2 class="card-title mb-0">${_escape(entityLabel)} detail</h2>
             |      <a class="btn btn-primary" href="${_escape(basePath + "/" + _escape_path_segment(routeId) + "/edit" + querySuffix)}">Edit</a>
             |    </div>
             |    ${body}
             |  </div>
             |</article>
             |${images}
             |${tags}
             |${associations}""".stripMargin
      ))
    }

  def renderComponentAdminEntityEdit(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    id: String,
    values: Map[String, String] = Map.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    validation: Option[FormValidationResult] = None
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val routeId = _entity_route_id(id)
      val actionPath = s"/form/${componentPath}/admin/entities/${entityPath}/${routeId}/update"
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displayFields = _admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
      val displaySchema = webSchema.copy(fields = _admin_display_web_fields(webSchema.fields, displayFields))
      val effectiveValidation = validation.filter(_.webSchema.selector == displaySchema.selector)
      val hiddenContext = _hidden_form_context_inputs(values)
      val controls = _admin_record_controls(
        displaySchema.fields,
        _admin_entity_record_fields(subsystem, componentPath, entityPath, id, "detail").getOrElse(Vector("id" -> id)),
        values,
        "field",
        effectiveValidation,
        includeExtensionFields = false
      )
      val imageAttachments = _admin_entity_image_attachment_controls("imageAttachments")
      val nav = _admin_nav_card(Vector(
        "Detail" -> s"${webBasePath}/${routeId}",
        s"Back to ${entityLabel} records" -> webBasePath
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} Edit",
        subtitle = "Entity record edit baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">Edit ${_escape(entityLabel)}</h2>
             |    ${_form_error_panel(values)}${_form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${_escape(actionPath)}" class="admin-form" enctype="multipart/form-data">
             |      ${controls}
             |      ${imageAttachments}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Update</button>
             |        <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}/${_escape(routeId)}">Cancel</a>
             |      </div>
             |    </form>
             |  </div>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminEntityNew(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    values: Map[String, String] = Map.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    validation: Option[FormValidationResult] = None
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val actionPath = s"/form/${componentPath}/admin/entities/${entityPath}/create"
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displaySchema = _admin_entity_create_schema(component, entityPath, webSchema)
      val effectiveValidation = validation.filter(_.webSchema.selector == displaySchema.selector)
      val hiddenContext = _hidden_form_context_inputs(values)
      val controls = _admin_new_controls(displaySchema.fields, values, "entityFields", "id=sales-order-1&#10;status=draft", effectiveValidation)
      val imageAttachments = _admin_entity_image_attachment_controls("newImageAttachments")
      val nav = _admin_nav_card(Vector(
        s"Back to ${entityLabel} records" -> webBasePath,
        "Entity types" -> s"/web/${componentPath}/admin/entities"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} New",
        subtitle = "Entity record create baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">New ${_escape(entityLabel)}</h2>
             |    ${_form_error_panel(values)}${_form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${_escape(actionPath)}" class="admin-form" enctype="multipart/form-data">
             |      ${controls}
             |      ${imageAttachments}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Create</button>
             |        <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}">Cancel</a>
             |      </div>
             |    </form>
             |  </div>
             |</article>""".stripMargin
      ))
    }

  private def _admin_entity_image_attachment_controls(
    idPrefix: String
  ): String = {
    val rows = (0 until 3).map { index =>
      val base = s"imageAttachments.${index}"
      s"""<div class="row g-2 align-items-end mb-2">
         |  <div class="col-md-2">
         |    <label class="form-label" for="${_escape(idPrefix)}Role${index}">Role</label>
         |    <input class="form-control" id="${_escape(idPrefix)}Role${index}" name="${base}.role" list="entityImageAttachmentRoleOptions">
         |  </div>
         |  <div class="col-md-3">
         |    <label class="form-label" for="${_escape(idPrefix)}BlobId${index}">Existing Blob id</label>
         |    <input class="form-control" id="${_escape(idPrefix)}BlobId${index}" name="${base}.blobId">
         |  </div>
         |  <div class="col-md-4">
         |    <label class="form-label" for="${_escape(idPrefix)}File${index}">Upload image</label>
         |    <input class="form-control" id="${_escape(idPrefix)}File${index}" name="${base}.file" type="file" accept="image/*">
         |  </div>
         |  <div class="col-md-2">
         |    <label class="form-label" for="${_escape(idPrefix)}Sort${index}">Sort</label>
         |    <input class="form-control" id="${_escape(idPrefix)}Sort${index}" name="${base}.sortOrder">
         |  </div>
         |</div>""".stripMargin
    }.mkString("\n")
    s"""<section class="border rounded p-3 mb-3">
       |  <h3 class="h6">Image Attachments</h3>
       |  ${rows}
       |  <datalist id="entityImageAttachmentRoleOptions"><option value="primary"><option value="cover"><option value="thumbnail"><option value="gallery"><option value="inline"></datalist>
       |</section>""".stripMargin
  }

  def renderComponentAdminEntityUpdateResult(
    componentName: String,
    entityName: String,
    id: String,
    values: Map[String, String],
    applied: Boolean = false,
    message: String = "Entity update execution is not enabled in this baseline.",
    resultStatus: Int = 200
  ): Page = {
    val componentPath = NamingConventions.toNormalizedSegment(componentName)
    val entityPath = NamingConventions.toNormalizedSegment(entityName)
    val entityLabel = _title_label(entityPath)
    val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
    val rows = _submitted_fields_rows(values)
    Page(_simple_page(
      title = s"${_escape(componentName)} ${_escape(entityLabel)} Update Result",
      subtitle = "Entity record update submission baseline",
      body =
        s"""${_admin_nav_card(Vector(
             "Detail" -> s"${webBasePath}/${id}",
             s"Back to ${entityLabel} records" -> webBasePath,
             "Edit again" -> s"${webBasePath}/${id}/edit"
           ))}
           |${_admin_card(
             "Update submitted",
             s"""<p>${_escape(message)}</p>
                |${_admin_table(Some(_admin_result_rows(applied, resultStatus, message)), rows)}""".stripMargin
           )}""".stripMargin
    ))
  }

  def renderComponentAdminEntityCreateResult(
    componentName: String,
    entityName: String,
    values: Map[String, String],
    applied: Boolean = false,
    message: String = "Entity create execution is not enabled in this baseline.",
    resultStatus: Int = 200
  ): Page = {
    val componentPath = NamingConventions.toNormalizedSegment(componentName)
    val entityPath = NamingConventions.toNormalizedSegment(entityName)
    val entityLabel = _title_label(entityPath)
    val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
    val rows = _submitted_fields_rows(values)
    Page(_simple_page(
      title = s"${_escape(componentName)} ${_escape(entityLabel)} Create Result",
      subtitle = "Entity record create submission baseline",
      body =
        s"""${_admin_nav_card(Vector(
             s"Back to ${entityLabel} records" -> webBasePath,
             "Create another" -> s"${webBasePath}/new"
           ))}
           |${_admin_card(
             "Create submitted",
             s"""<p>${_escape(message)}</p>
                |${_admin_table(Some(_admin_result_rows(applied, resultStatus, message)), rows)}""".stripMargin
           )}""".stripMargin
    ))
  }

  private final case class _AdminListResult(
    items: Vector[_AdminReadListItem],
    page: Int,
    pageSize: Int,
    hasNext: Boolean,
    total: Option[Int],
    warnings: Vector[String]
  ) {
    def ids: Vector[String] =
      items.map(_.id)
  }

  private def _admin_entity_list(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    pageRequest: PageRequest,
    searchValues: Map[String, String] = Map.empty
  ): _AdminListResult =
    _admin_list_result(
      subsystem,
      "/admin/entity/list",
      Record.create(Vector("component" -> componentPath, "entity" -> entityPath, "view" -> WebTableColumnResolver.defaultViewName) ++ pageRequest.toPairs ++ searchValues.toVector)
    )

  private def _admin_entity_searchable_fields(
    component: Component,
    entityPath: String,
    view: String
  ): Vector[String] =
    org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(component, entityPath)
      .defaultSearchFields(view)

  private def _admin_search_mode(
    values: Map[String, String]
  ): Option[SearchMode] =
    values.get("searchMode").orElse(values.get("search_mode")).flatMap(SearchMode.parse)

  private def _admin_search_values(
    values: Map[String, String],
    profile: SearchPlanningProfile
  ): Map[String, String] = {
    val knownFilters = profile.filterFields.map(x => NamingConventions.toNormalizedSegment(x).replace("-", "") -> x).toMap
    values.collect {
      case (key, value) if _admin_search_control_key(key) && value.trim.nonEmpty => key -> value
      case (key, value) if value.trim.nonEmpty && knownFilters.contains(NamingConventions.toNormalizedSegment(key).replace("-", "")) => key -> value
    }
  }

  private def _admin_search_control_key(key: String): Boolean =
    Set("q", "text", "sort", "sortBy", "sort_by", "direction", "order", "searchMode", "search_mode", "includeTotal", "include_total").contains(key)

  private def _admin_search_card(
    action: String,
    values: Map[String, String],
    profile: SearchPlanningProfile,
    filterFields: Vector[String],
    sortFields: Vector[String],
    total: Option[Int],
    fetched: Int
  ): String = {
    val q = values.get("q").orElse(values.get("text")).getOrElse("")
    val selectedMode = _admin_search_mode(values).getOrElse(SearchMode.FullText)
    val selectedSort = values.get("sort").orElse(values.get("sortBy")).orElse(values.get("sort_by")).getOrElse("")
    val selectedDirection = values.get("direction").orElse(values.get("order")).getOrElse("asc")
    val filterControls = filterFields.filterNot(x => x == "id").take(6).map { field =>
      val value = values.get(field).getOrElse("")
      s"""<div class="col-12 col-md-4 col-xl-2">
         |  <label class="form-label" for="adminSearchFilter${_escape(field)}">${_escape(_title_label(field))}</label>
         |  <input class="form-control" id="adminSearchFilter${_escape(field)}" name="${_escape(field)}" value="${_escape(value)}">
         |</div>""".stripMargin
    }.mkString("\n")
    val sortOptions = ("", "Default") +: sortFields.map(x => x -> _title_label(x))
    val sortHtml = sortOptions.map { case (value, label) =>
      val selected = if (value == selectedSort) " selected" else ""
      s"""<option value="${_escape(value)}"${selected}>${_escape(label)}</option>"""
    }.mkString("\n")
    val chips = _admin_search_chips(values, profile, action)
    val summary =
      total.map(t => s"${t} total records").getOrElse(s"${fetched} records on this page")
    s"""<article class="card admin-card admin-search-card">
       |  <div class="card-body">
       |    <div class="d-flex flex-column flex-md-row justify-content-between gap-2 mb-3">
       |      <div>
       |        <h2 class="card-title h5 mb-1">Search</h2>
       |        <p class="card-text text-body-secondary mb-0">Full-text search uses <code>q</code>; <code>text</code> is accepted as a compatibility alias. ${_escape(summary)}</p>
       |      </div>
       |      <a class="btn btn-sm btn-outline-secondary align-self-start" href="${_escape(action)}">Clear search</a>
       |    </div>
       |    ${chips}
       |    <form method="get" action="${_escape(action)}" class="row g-2 align-items-end">
       |      <div class="col-12 col-md-5">
       |        <label class="form-label" for="adminSearchQ">Search text</label>
       |        <input class="form-control" id="adminSearchQ" name="q" value="${_escape(q)}" placeholder="Search visible text fields">
       |      </div>
       |      <div class="col-12 col-md-2">
       |        <label class="form-label" for="adminSearchMode">Mode</label>
       |        <select class="form-select" id="adminSearchMode" name="searchMode">
       |          ${_search_mode_option(SearchMode.FullText, selectedMode)}
       |          ${_search_mode_option(SearchMode.Semantic, selectedMode)}
       |          ${_search_mode_option(SearchMode.Hybrid, selectedMode)}
       |        </select>
       |      </div>
       |      <div class="col-12 col-md-3">
       |        <label class="form-label" for="adminSearchSort">Sort</label>
       |        <select class="form-select" id="adminSearchSort" name="sort">${sortHtml}</select>
       |      </div>
       |      <div class="col-12 col-md-2">
       |        <label class="form-label" for="adminSearchDirection">Order</label>
       |        <select class="form-select" id="adminSearchDirection" name="direction">
       |          <option value="asc"${if (selectedDirection != "desc") " selected" else ""}>Ascending</option>
       |          <option value="desc"${if (selectedDirection == "desc") " selected" else ""}>Descending</option>
       |        </select>
       |      </div>
       |      ${filterControls}
       |      <div class="col-12 d-flex flex-wrap gap-2 justify-content-end">
       |        <input type="hidden" name="includeTotal" value="${_escape(values.getOrElse("includeTotal", "false"))}">
       |        <button class="btn btn-primary" type="submit">Search</button>
       |      </div>
       |    </form>
       |  </div>
       |</article>""".stripMargin
  }

  private def _search_mode_option(
    mode: SearchMode,
    selected: SearchMode
  ): String = {
    val selectedAttr = if (mode == selected) " selected" else ""
    s"""<option value="${_escape(mode.name)}"${selectedAttr}>${_escape(_title_label(mode.name))}</option>"""
  }

  private def _admin_search_chips(
    values: Map[String, String],
    profile: SearchPlanningProfile,
    clearHref: String
  ): String = {
    val searchValues = _admin_search_values(values, profile)
    val chips = searchValues.toVector.sortBy(_._1).map { case (key, value) =>
      s"""<span class="badge rounded-pill text-bg-light border">${_escape(key)}: ${_escape(value)}</span>"""
    }
    if (chips.isEmpty)
      ""
    else
      s"""<div class="d-flex flex-wrap gap-2 align-items-center mb-3 admin-search-active-filters"><span class="text-body-secondary small">Active filters</span>${chips.mkString}<a class="btn btn-sm btn-outline-secondary" href="${_escape(clearHref)}">Clear</a></div>"""
  }

  private def _admin_search_href(
    basePath: String,
    values: Map[String, String],
    profile: SearchPlanningProfile
  ): String = {
    val pairs = _admin_search_values(values, profile).toVector
    val suffix =
      if (pairs.isEmpty)
        "?page={page}&pageSize={pageSize}"
      else
        pairs.map { case (key, value) => s"${_escape_query(key)}=${_escape_query(value)}" }.mkString("?", "&", "&page={page}&pageSize={pageSize}")
    s"${basePath}${suffix}"
  }

  private def _admin_entity_record_table(
    subsystem: Subsystem,
    component: Component,
    componentPath: String,
    entityPath: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): String = {
    val webSchema = WebSchemaResolver.resolveEntity(
      component,
      componentPath,
      entityPath,
      webDescriptor,
      _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
      fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
    )
    val displayFields = _admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
    _admin_record_table(
      displayFields,
      _admin_entity_record_fields(subsystem, componentPath, entityPath, id, "detail"),
      s"""No record is currently available for id <code>${_escape(id)}</code>."""
    )
  }

  private def _admin_entity_record_table_from_record(
    subsystem: Subsystem,
    component: Component,
    componentPath: String,
    entityPath: String,
    id: String,
    record: Option[Record],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): String = {
    val webSchema = WebSchemaResolver.resolveEntity(
      component,
      componentPath,
      entityPath,
      webDescriptor,
      _admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
      fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
    )
    val displayFields = _admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
    val fields = record.flatMap(_.getString("fields")).map(_field_lines)
    _admin_record_table(
      displayFields,
      fields,
      s"""No record is currently available for id <code>${_escape(id)}</code>."""
    )
  }

  private def _admin_entity_read_record(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    id: String
  ): Option[Record] =
    _admin_operation_record(
      subsystem,
      "/admin/entity/read",
      Record.data("component" -> componentPath, "entity" -> entityPath, "id" -> id, "view" -> "detail")
    )

  private def _admin_entity_record_fields(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    id: String,
    view: String
  ): Option[Vector[(String, String)]] =
    _admin_record_fields(
      subsystem,
      "/admin/entity/read",
      Record.data("component" -> componentPath, "entity" -> entityPath, "id" -> id, "view" -> view)
    )

  private def _admin_entity_schema_fields(
    subsystem: Subsystem,
    component: Component,
    componentPath: String,
    entityPath: String
  ): Vector[String] =
    _descriptor_entity_schema_fields(component, entityPath)
      .getOrElse {
        _admin_schema_fields(
          subsystem,
          "/admin/entity/list",
          "/admin/entity/read",
          Record.data("component" -> componentPath, "entity" -> entityPath),
          Record.data("component" -> componentPath, "entity" -> entityPath)
        )
      }

  private def _admin_entity_display_fields(
    component: Component,
    entityPath: String,
    view: String,
    fallback: Vector[String]
  ): Vector[String] =
    component.viewDefinitions
      .find(d =>
        NamingConventions.equivalentByNormalized(d.entityName, entityPath) ||
        NamingConventions.equivalentByNormalized(d.name, entityPath)
      )
      .flatMap(_.fieldsFor(view))
      .filter(_.nonEmpty)
      .getOrElse(fallback)

  private def _admin_entity_create_schema(
    component: Component,
    entityPath: String,
    webSchema: WebSchemaResolver.ResolvedWebSchema
  ): WebSchemaResolver.ResolvedWebSchema = {
    val displayFields = _admin_entity_create_fields(component, entityPath, webSchema.fieldNames)
    webSchema.copy(fields = _admin_display_web_fields(webSchema.fields, displayFields))
  }

  private def _admin_entity_create_fields(
    component: Component,
    entityPath: String,
    fallback: Vector[String]
  ): Vector[String] =
    _admin_entity_view_fields(component, entityPath, "create")
      .orElse(_admin_entity_view_fields(component, entityPath, "detail"))
      .getOrElse(fallback)

  private def _admin_entity_view_fields(
    component: Component,
    entityPath: String,
    view: String
  ): Option[Vector[String]] =
    component.viewDefinitions
      .find(d =>
        NamingConventions.equivalentByNormalized(d.entityName, entityPath) ||
        NamingConventions.equivalentByNormalized(d.name, entityPath)
      )
      .flatMap(_.fieldsFor(view))
      .filter(_.nonEmpty)

  private def _admin_display_web_fields(
    fields: Vector[WebSchemaResolver.ResolvedWebField],
    fieldNames: Vector[String]
  ): Vector[WebSchemaResolver.ResolvedWebField] =
    fieldNames.map { name =>
      fields.find(x => NamingConventions.equivalentByNormalized(x.name, name))
        .map(_.copy(name = name))
        .getOrElse(WebSchemaResolver.ResolvedWebField(name))
    }

  private def _admin_data_list(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String,
    pageRequest: PageRequest
  ): _AdminListResult =
    _admin_list_result(
      subsystem,
      "/admin/data/list",
      Record.create(Vector("component" -> componentPath, "data" -> dataPath) ++ pageRequest.toPairs)
    )

  private def _admin_list_result(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): _AdminListResult =
    _admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        val items = _admin_record_items(record)
        _AdminListResult(
          items,
          record.getInt("page").getOrElse(1),
          record.getInt("pageSize").getOrElse(20),
          record.getBoolean("hasNext").getOrElse(false),
          record.getInt("total"),
          _admin_record_warnings(record)
        )
      case None =>
        _AdminListResult(Vector.empty, 1, 20, false, None, Vector.empty)
    }

  private def _admin_record_warnings(
    record: Record
  ): Vector[String] =
    record.getAny("warnings") match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString).filter(_.nonEmpty)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.map(_.toString).filter(_.nonEmpty)
      case Some(value) => Vector(value.toString).filter(_.nonEmpty)
      case None =>
        record.getString("totalUnavailableReason").map(reason => s"total count is not available: ${reason}").toVector
    }

  private def _admin_warnings(
    warnings: Vector[String]
  ): String =
    if (warnings.isEmpty)
      ""
    else
      s"""<div class="alert alert-warning admin-feedback" role="alert">
         |  <p class="alert-heading fw-semibold mb-2">Warning</p>
         |  <p class="mb-0">${warnings.map(_escape).mkString("<br>")}</p>
         |</div>""".stripMargin

  private def _admin_empty_state(
    message: String
  ): String =
    _web_empty_state(message, "admin-empty-state")

  private def _web_empty_state(
    message: String,
    cssClass: String = "web-empty-state"
  ): String =
    s"""<div class="alert alert-info ${_escape(cssClass)}" role="status">
       |  <p class="mb-0">${_escape(message)}</p>
       |</div>""".stripMargin

  private def _admin_empty_table_cell(
    colspan: Int,
    message: String
  ): String =
    s"""<tr><td colspan="${colspan}"><div class="admin-empty-state text-body-secondary py-3">${_escape(message)}</div></td></tr>"""

  private def _admin_nav_card(
    links: Vector[(String, String)]
  ): String = {
    val items = links.map { case (label, href) =>
      s"""<li class="nav-item"><a class="nav-link border" href="${_escape(href)}">${_escape(label)}</a></li>"""
    }.mkString("\n")
    s"""<article class="card admin-card admin-nav">
       |  <div class="card-body">
       |    <h2 class="card-title h5">Navigation</h2>
       |    <ul class="nav nav-pills flex-column flex-sm-row gap-2">${items}</ul>
       |  </div>
       |</article>""".stripMargin
  }

  private def _admin_link_list_group(
    links: Vector[(String, String)]
  ): String = {
    val items = links.map { case (label, href) =>
      s"""<a class="list-group-item list-group-item-action" href="${_escape(href)}">${_escape(label)}</a>"""
    }.mkString("\n")
    s"""<div class="list-group">${items}</div>"""
  }

  private def _admin_action_row(
    links: Vector[(String, String)],
    primary: Boolean = true
  ): String = {
    val items = links.zipWithIndex.map {
      case ((label, href), index) =>
        val css =
          if (primary && index == 0)
            "btn btn-primary"
          else
            "btn btn-outline-secondary"
        s"""<a class="${css}" href="${_escape(href)}">${_escape(label)}</a>"""
    }.mkString("\n")
    s"""<div class="admin-action-row d-flex flex-wrap gap-2">${items}</div>"""
  }

  private def _admin_confirm_post_form(
    action: String,
    label: String,
    title: String,
    message: String,
    hiddenFields: Vector[(String, String)],
    formClass: String = "d-inline",
    triggerClass: String = "btn btn-outline-danger btn-sm"
  ): String = {
    val modalid = _admin_confirm_modal_id(action, hiddenFields)
    val hidden = hiddenFields.map { case (name, value) =>
      s"""<input type="hidden" name="${_escape(name)}" value="${_escape(value)}">"""
    }.mkString("\n")
    s"""<button class="${_escape(triggerClass)}" type="button" data-bs-toggle="modal" data-bs-target="#${modalid}">${_escape(label)}</button>
       |<div class="modal fade" id="${modalid}" tabindex="-1" aria-labelledby="${modalid}-title" aria-hidden="true">
       |  <div class="modal-dialog">
       |    <div class="modal-content">
       |      <div class="modal-header">
       |        <h2 class="modal-title h5" id="${modalid}-title">${_escape(title)}</h2>
       |        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
       |      </div>
       |      <div class="modal-body"><p class="mb-0">${_escape(message)}</p></div>
       |      <div class="modal-footer">
       |        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
       |        <form method="post" action="${_escape(action)}" class="${_escape(formClass)}">
       |          ${hidden}
       |          <button class="btn btn-danger" type="submit">${_escape(label)}</button>
       |        </form>
       |      </div>
       |    </div>
       |  </div>
       |</div>
       |<noscript>
       |  <form method="post" action="${_escape(action)}" class="${_escape(formClass)}">
       |    ${hidden}
       |    <button class="${_escape(triggerClass)}" type="submit">${_escape(label)}</button>
       |  </form>
       |</noscript>""".stripMargin
  }

  private def _admin_confirm_modal_id(
    action: String,
    hiddenFields: Vector[(String, String)]
  ): String = {
    val seed = (action +: hiddenFields.map { case (k, v) => s"${k}=${v}" }).mkString("|")
    s"admin-confirm-${java.lang.Integer.toUnsignedString(seed.hashCode)}"
  }

  private def _admin_table(
    head: Option[String],
    rows: String,
    tableClass: String = "table table-sm table-hover align-middle mb-0"
  ): String =
    s"""<div class="table-responsive">
       |  <table class="${_escape(tableClass)}">
       |    ${head.map(x => s"<thead>${x}</thead>").getOrElse("")}
       |    <tbody>${rows}</tbody>
       |  </table>
       |</div>""".stripMargin

  private def _admin_card(
    title: String,
    body: String,
    id: Option[String] = None
  ): String = {
    val idAttr = id.map(x => s""" id="${_escape(x)}"""").getOrElse("")
    s"""<article${idAttr} class="card admin-card">
       |  <div class="card-body">
       |    <h2 class="card-title">${_escape(title)}</h2>
       |    ${body}
       |  </div>
       |</article>""".stripMargin
  }

  private def _admin_data_record_table(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): String = {
    val webSchema = WebSchemaResolver.resolveData(
      componentPath,
      dataPath,
      webDescriptor,
      _admin_data_schema_fields(subsystem, componentPath, dataPath),
      fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
    )
    _admin_record_table(
      webSchema.fieldNames,
      _admin_data_record_fields(subsystem, componentPath, dataPath, id),
      s"""No data record is currently available for id <code>${_escape(id)}</code>."""
    )
  }

  private def _admin_data_record_fields(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String,
    id: String
  ): Option[Vector[(String, String)]] =
    _admin_record_fields(
      subsystem,
      "/admin/data/read",
      Record.data("component" -> componentPath, "data" -> dataPath, "id" -> id)
    )

  private def _admin_data_schema_fields(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String
  ): Vector[String] =
    _admin_schema_fields(
      subsystem,
      "/admin/data/list",
      "/admin/data/read",
      Record.data("component" -> componentPath, "data" -> dataPath),
      Record.data("component" -> componentPath, "data" -> dataPath)
    )

  private def _admin_schema_fields(
    subsystem: Subsystem,
    listPath: String,
    readPath: String,
    listForm: Record,
    readBaseForm: Record
  ): Vector[String] =
    _id_first_if_present(_admin_operation_record(subsystem, listPath, listForm).toVector.flatMap { record =>
      _admin_record_items(record).headOption.toVector.flatMap { item =>
        _admin_record_fields(
          subsystem,
          readPath,
          Record.create((readBaseForm.asMap + ("id" -> item.id)).toVector)
        ).toVector.flatten.map(_._1)
      }
    }.distinct) match {
      case xs if xs.nonEmpty => xs
      case _ => Vector("id")
    }

  private def _id_first_if_present(
    fields: Vector[String]
  ): Vector[String] =
    if (fields.contains("id"))
      "id" +: fields.filterNot(_ == "id")
    else
      fields

  private def _descriptor_entity_schema_fields(
    component: Component,
    entityName: String
  ): Option[Vector[String]] =
    component.componentDescriptors
      .flatMap(_.entityRuntimeDescriptors)
      .find(d => NamingConventions.equivalentByNormalized(d.entityName, entityName))
      .flatMap(_.schema.map(WebSchemaResolver.fromSchema(_).map(_.name)).filter(_.nonEmpty))
      .map {
        case xs if xs.contains("id") => xs
        case xs => "id" +: xs
      }

  private def _admin_record_fields(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[Vector[(String, String)]] =
    _admin_operation_record(subsystem, path, form)
      .flatMap(_.getString("fields"))
      .map(_field_lines)

  private def _admin_operation_lines(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Vector[String] =
    _admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        record.getAny("ids") match {
          case Some(xs: Seq[?]) =>
            xs.toVector.map(_.toString)
          case _ =>
            record.getString("fields").map(_lines).getOrElse(Vector.empty)
        }
      case None =>
        Vector.empty
    }

  private def _admin_operation_record(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[Record] =
    _admin_operation_response(subsystem, path, form).collect {
      case OperationResponse.RecordResponse(record) => record
    }

  private def _admin_operation_response(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[OperationResponse] =
    _admin_protocol_request(path, form).flatMap { request =>
      subsystem.executeOperationResponse(request).toOption
    }

  private def _admin_protocol_request(
    path: String,
    form: Record
  ): Option[ProtocolRequest] =
    path.stripPrefix("/").split("/").toVector match {
      case Vector(component, service, operation) =>
        Some(
          ProtocolRequest.of(
            component = component,
            service = service,
            operation = operation,
            arguments = form.asMap.toVector.map { case (key, value) => Argument(key, value.toString) }.toList
          )
        )
      case _ =>
        None
    }

  private def _admin_read_result_table(
    subsystem: Subsystem,
    path: String,
    form: Record,
    emptyMessage: String,
    schemaFields: Vector[String] = Vector.empty
  ): String =
    _admin_operation_value_lines(subsystem, path, form)
      .filter(_.nonEmpty)
      .map(lines => _field_table(_admin_schema_ordered_fields(schemaFields, _field_lines(lines.mkString("\n")))))
      .getOrElse(_admin_empty_state(emptyMessage))

  private def _admin_read_result_table_web_schema(
    subsystem: Subsystem,
    path: String,
    form: Record,
    emptyMessage: String,
    webFields: Vector[WebSchemaResolver.ResolvedWebField]
  ): String = {
    val schemaFields = webFields.map(_.name)
    val labels = _web_field_labels(webFields)
    _admin_operation_value_lines(subsystem, path, form)
      .filter(_.nonEmpty)
      .map(lines => _field_table(_admin_schema_ordered_fields(schemaFields, _field_lines(lines.mkString("\n"))), labels))
      .getOrElse(_admin_empty_state(emptyMessage))
  }

  private def _admin_read_result_list(
    subsystem: Subsystem,
    path: String,
    form: Record,
    basePath: String,
    emptyMessage: String,
    pageRequest: PageRequest,
    schemaFields: Vector[String] = Vector.empty
  ): String =
    _admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        val items = _admin_record_items(record)
        val warnings = _admin_warnings(_admin_record_warnings(record))
        val page = record.getInt("page").getOrElse(pageRequest.page)
        val pageSize = record.getInt("pageSize").getOrElse(pageRequest.pageSize)
        val total = record.getInt("total")
        val hasNext = record.getBoolean("hasNext")
        val table = _admin_read_result_list_table(items, schemaFields, basePath, emptyMessage)
        s"""<p>List with paging${total.map(t => s" · total ${_escape(t.toString)}").getOrElse("")}</p>
           |${warnings}
           |${table}
           |${_paging_nav(page, pageSize, total, pageRequest.href(basePath), hasNext)}""".stripMargin
      case None =>
        _admin_empty_state(emptyMessage)
    }

  private def _admin_read_result_list_web_schema(
    subsystem: Subsystem,
    path: String,
    form: Record,
    basePath: String,
    emptyMessage: String,
    pageRequest: PageRequest,
    webFields: Vector[WebSchemaResolver.ResolvedWebField]
  ): String =
    _admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        val schemaFields = webFields.map(_.name)
        val labels = _web_field_labels(webFields)
        val items = _admin_record_items(record)
        val warnings = _admin_warnings(_admin_record_warnings(record))
        val page = record.getInt("page").getOrElse(pageRequest.page)
        val pageSize = record.getInt("pageSize").getOrElse(pageRequest.pageSize)
        val total = record.getInt("total")
        val hasNext = record.getBoolean("hasNext")
        val table = _admin_read_result_list_table_labeled(items, schemaFields, labels, basePath, emptyMessage)
        s"""<p>List with paging${total.map(t => s" · total ${_escape(t.toString)}").getOrElse("")}</p>
           |${warnings}
           |${table}
           |${_paging_nav(page, pageSize, total, pageRequest.href(basePath), hasNext)}""".stripMargin
      case None =>
        _admin_empty_state(emptyMessage)
    }

  private def _admin_read_result_list_table_labeled(
    items: Vector[_AdminReadListItem],
    schemaFields: Vector[String],
    labels: Map[String, String],
    basePath: String,
    emptyMessage: String,
    includeEdit: Boolean = false,
    linkContext: Map[String, String] = Map.empty
  ): String =
    if (schemaFields.isEmpty) {
      val rows =
        if (items.isEmpty) {
          _admin_empty_table_cell(3, emptyMessage)
        } else {
          items.map { item =>
            val href = s"${basePath}/${_escape_path_segment(_admin_read_list_route_id(item))}${_hidden_form_context_query_suffix(linkContext)}"
            val actions = _admin_read_list_actions(href, includeEdit)
            s"""<tr><td><code>${_escape(item.id)}</code></td><td>${_escape(item.label)}</td><td>${actions}</td></tr>"""
          }.mkString("\n")
        }
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>ID</th><th>Value</th><th>Actions</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    } else {
      val headings = schemaFields.map(x => s"<th>${_escape(labels.getOrElse(x, x))}</th>").mkString
      val rows =
        if (items.isEmpty) {
          _admin_empty_table_cell(schemaFields.size + 1, emptyMessage)
        } else {
          items.map { item =>
            val values = _admin_read_list_item_fields(item)
            val href = s"${basePath}/${_escape_path_segment(_admin_read_list_route_id(item, values))}${_hidden_form_context_query_suffix(linkContext)}"
            val columns = schemaFields.map { field =>
              val value = {
                if (field == "id") values.getOrElse(field, item.id)
                else if (field == "label") values.getOrElse(field, item.label)
                else if (field == "value") values.getOrElse(field, item.value)
                else _admin_display_value(field, schemaFields.toSet, values)
              }
              s"<td>${_escape(value)}</td>"
            }.mkString
            s"""<tr>${columns}<td>${_admin_read_list_actions(href, includeEdit)}</td></tr>"""
          }.mkString("\n")
        }
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr>${headings}<th>Actions</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _admin_read_result_list_table(
    items: Vector[_AdminReadListItem],
    schemaFields: Vector[String],
    basePath: String,
    emptyMessage: String,
    includeEdit: Boolean = false,
    linkContext: Map[String, String] = Map.empty
  ): String =
    _admin_read_result_list_table_labeled(items, schemaFields, Map.empty, basePath, emptyMessage, includeEdit, linkContext)

  private def _admin_read_list_route_id(
    item: _AdminReadListItem,
    values: Map[String, String] = Map.empty
  ): String =
    values.get("shortid")
      .orElse(_admin_read_list_item_fields(item).get("shortid"))
      .filter(_.nonEmpty)
      .getOrElse(item.id)

  private def _entity_route_id(
    id: String
  ): String =
    EntityId.parse(id).toOption.map(_.parts.entropy).getOrElse(id)

  private def _web_field_labels(
    fields: Vector[WebSchemaResolver.ResolvedWebField]
  ): Map[String, String] =
    fields.flatMap(field => field.label.map(label => field.name -> label)).toMap

  private def _admin_read_list_actions(
    href: String,
    includeEdit: Boolean
  ): String =
    if (includeEdit) {
      val editHref = _append_path_before_query(href, "/edit")
      s"""<div class="btn-group btn-group-sm" role="group" aria-label="Record actions"><a class="btn btn-outline-primary" href="${_escape(href)}">Detail</a><a class="btn btn-outline-secondary" href="${_escape(editHref)}">Edit</a></div>"""
    }
    else
      s"""<a class="btn btn-outline-primary btn-sm" href="${_escape(href)}">Detail</a>"""

  private def _append_path_before_query(
    href: String,
    suffix: String
  ): String =
    href.indexOf('?') match {
      case -1 => href + suffix
      case n => href.substring(0, n) + suffix + href.substring(n)
    }

  private def _admin_read_list_item_fields(
    item: _AdminReadListItem
  ): Map[String, String] =
    _field_lines(item.value).toMap ++ item.fields ++ Map(
      "id" -> item.id,
      "label" -> item.label,
      "value" -> item.value
    )

  private final case class _AdminReadListItem(
    id: String,
    label: String,
    value: String,
    fields: Map[String, String] = Map.empty
  )

  private def _admin_record_items(record: Record): Vector[_AdminReadListItem] =
    record.getAny("items") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_admin_record_item)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.flatMap(_admin_record_item)
      case Some(value) => _admin_record_item(value).toVector
      case None =>
        _admin_record_ids(record).map(x => _AdminReadListItem(x, x, x)) match {
          case xs if xs.nonEmpty => xs
          case _ => _admin_record_values(record).map(x => _AdminReadListItem(x, x, x))
        }
    }

  private def _admin_record_ids(record: Record): Vector[String] =
    record.getAny("ids") match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.map(_.toString)
      case Some(value) => Vector(value.toString)
      case None => Vector.empty
    }

  private def _admin_record_item(value: Any): Option[_AdminReadListItem] =
    value match {
      case record: Record =>
        val fields = record.asMap.view.mapValues(_.toString).toMap
        val id = fields.getOrElse("id", "")
        val label = fields.getOrElse("label", id)
        val itemValue = fields.getOrElse("value", _field_map_text(fields).getOrElse(label))
        Option.when(id.nonEmpty)(_AdminReadListItem(id, label, itemValue, fields))
      case map: scala.collection.Map[?, ?] =>
        val fields = map.toVector.map { case (key, itemValue) => key.toString -> itemValue.toString }.toMap
        val id = fields.getOrElse("id", "")
        val label = fields.getOrElse("label", id)
        val itemValue = fields.getOrElse("value", _field_map_text(fields).getOrElse(label))
        Option.when(id.nonEmpty)(_AdminReadListItem(id, label, itemValue, fields))
      case x =>
        val text = x.toString
        Option.when(text.nonEmpty)(_AdminReadListItem(text, text, text))
    }

  private def _field_map_text(
    fields: Map[String, String]
  ): Option[String] =
    Option.when(fields.nonEmpty) {
      fields.toVector.sortBy(_._1).map { case (key, value) => s"${key}=${value}" }.mkString("\n")
    }

  private def _admin_record_values(record: Record): Vector[String] =
    record.getAny("values") match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.map(_.toString)
      case Some(value) => Vector(value.toString)
      case None => record.getString("fields").map(_lines).getOrElse(Vector.empty)
    }

  private def _admin_operation_value_lines(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[Vector[String]] =
    _admin_operation_response(subsystem, path, form).flatMap {
      case OperationResponse.RecordResponse(record) =>
        record.getString("fields").map(_lines)
      case _ =>
        None
    }

  private def _lines(text: String): Vector[String] =
    text.linesIterator.toVector.map(_.trim).filter(_.nonEmpty)

  private def _field_lines(text: String): Vector[(String, String)] =
    _lines(text).map { line =>
      val i = line.indexOf("=")
      if (i < 0) line -> ""
      else line.take(i) -> line.drop(i + 1)
    }

  private def _admin_key_value_table(
    lines: Vector[String],
    emptyMessage: String
  ): String =
    if (lines.isEmpty) {
      _admin_empty_state(emptyMessage)
    } else {
      _value_table(lines)
    }

  private def _admin_record_table(
    schemaFields: Vector[String],
    fields: Option[Vector[(String, String)]],
    emptyMessage: String
  ): String =
    fields match {
      case Some(xs) if xs.nonEmpty =>
        _field_table(_admin_schema_ordered_fields(schemaFields, xs))
      case _ =>
        _admin_empty_state(emptyMessage)
    }

  private def _field_table(fields: Vector[(String, String)]): String = {
    _field_table(fields, Map.empty)
  }

  private def _field_table(
    fields: Vector[(String, String)],
    labels: Map[String, String]
  ): String = {
    val rows = fields.map {
      case (key, value) =>
        s"""<tr><th>${_escape(labels.getOrElse(key, key))}</th><td>${_escape(value)}</td></tr>"""
    }.mkString("\n")
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <tbody>${rows}</tbody>
       |</table></div>""".stripMargin
  }

  private def _value_table(lines: Vector[String]): String = {
    val rows = lines.map { line =>
      val i = line.indexOf("=")
      val (key, value) =
        if (i < 0) "value" -> line
        else line.take(i) -> line.drop(i + 1)
      s"""<tr><th>${_escape(key)}</th><td>${_escape(value)}</td></tr>"""
    }.mkString("\n")
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <tbody>${rows}</tbody>
       |</table></div>""".stripMargin
  }

  private def _escape_path_segment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  private def _escape_query(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  private def _form_initial_fields(values: Map[String, String]): String =
    values.toVector
      .sortBy(_._1)
      .map { case (key, value) => s"${_escape(key)}=${_escape(value)}" }
      .mkString("\n")

  private def _submitted_fields_rows(values: Map[String, String]): String =
    if (values.isEmpty) {
      """<tr><td colspan="2">No submitted fields.</td></tr>"""
    } else {
      values.toVector.sortBy(_._1).map {
        case (key, value) =>
          s"""<tr><th>${_escape(key)}</th><td>${_escape(value)}</td></tr>"""
      }.mkString("\n")
    }

  private def _view_definition(
    component: Component,
    viewName: String
  ) =
    component.viewDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, viewName))
      .orElse(_strip_surface_suffix(viewName, "view").flatMap(base =>
        component.viewDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, base))
      ))

  private def _aggregate_definition(
    component: Component,
    aggregateName: String
  ) =
    component.aggregateDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, aggregateName))
      .orElse(_strip_surface_suffix(aggregateName, "aggregate").flatMap(base =>
        component.aggregateDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, base))
      ))

  private def _strip_surface_suffix(
    value: String,
    surface: String
  ): Option[String] = {
    val normalized = NamingConventions.toNormalizedSegment(value)
    val suffix = s"-${NamingConventions.toNormalizedSegment(surface)}"
    if (normalized.endsWith(suffix))
      Some(normalized.dropRight(suffix.length))
    else
      None
  }

  private def _aggregate_operation_actions(
    component: Component,
    aggregateName: String
  ): String = {
    val componentPath = NamingConventions.toNormalizedSegment(component.name)
    val bindings = _aggregate_operation_bindings(component, aggregateName)
    if (bindings.isEmpty) {
      _admin_empty_state("No aggregate operations are currently exposed.")
    } else {
      val grouped = bindings.groupBy(_.kind).withDefaultValue(Vector.empty)
      val summary = Vector(
        "create" -> "Create operations construct a new aggregate root.",
        "read" -> "Read operations retrieve aggregate state.",
        "update" -> "Update and command operations mutate aggregate state."
      ).map { case (kind, text) =>
        val count = grouped(kind).size
        s"""<li><strong>${_escape(kind)}</strong>: ${count} ${_escape(text)}</li>"""
      }.mkString("\n")
      val rows = bindings.map { binding =>
        val path = _form_operation_path(componentPath, binding.service, binding.operation)
        val style = if (binding.kind == "create") "btn-primary" else if (binding.kind == "update") "btn-warning" else "btn-outline-secondary"
        val label = _aggregate_operation_action_label(binding.kind)
        s"""<tr><td>${_escape(binding.kind)}</td><td>${_escape(binding.service)}</td><td>${_escape(binding.operation)}</td><td><a class="btn btn-sm ${style}" href="${_escape(path)}">${_escape(label)}</a></td></tr>"""
      }.mkString("\n")
      s"""<ul>${summary}</ul>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Kind</th><th>Service</th><th>Operation</th><th>Action</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
  }

  private def _aggregate_instance_operation_actions(
    component: Component,
    aggregateName: String,
    id: String
  ): String = {
    val componentPath = NamingConventions.toNormalizedSegment(component.name)
    val bindings = _aggregate_operation_bindings(component, aggregateName)
      .filter(x => x.kind == "read" || x.kind == "update")
    if (bindings.isEmpty) {
      _admin_empty_state("No aggregate instance operations are currently exposed.")
    } else {
      val rows = bindings.map { binding =>
        val detailPath = s"/web/${componentPath}/admin/aggregates/${NamingConventions.toNormalizedSegment(aggregateName)}/${_escape_path_segment(id)}"
        val path = _form_operation_path(componentPath, binding.service, binding.operation, _admin_operation_context("id" -> id, "crud.success.href" -> detailPath))
        val style = if (binding.kind == "update") "btn-warning" else "btn-outline-secondary"
        val label = _aggregate_operation_action_label(binding.kind)
        s"""<tr><td>${_escape(binding.kind)}</td><td>${_escape(binding.service)}</td><td>${_escape(binding.operation)}</td><td><a class="btn btn-sm ${style}" href="${_escape(path)}">${_escape(label)}</a></td></tr>"""
      }.mkString("\n")
      s"""<p>Instance operations are opened as normal Operation forms with the aggregate id prefilled.</p>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Kind</th><th>Service</th><th>Operation</th><th>Action</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
  }

  private def _aggregate_operation_action_label(kind: String): String =
    kind match {
      case "create" => "Create aggregate"
      case "read" => "Read aggregate"
      case "update" => "Run update command"
      case _ => "Open operation"
    }

  private def _admin_operation_context(
    values: (String, String)*
  ): Map[String, String] =
    values.toMap

  private def _form_operation_path(
    componentPath: String,
    service: String,
    operation: String,
    values: Map[String, String] = Map.empty
  ): String = {
    val base = s"/form/${componentPath}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    if (values.isEmpty)
      base
    else
      base + values.toVector.sortBy(_._1).map {
        case (key, value) => s"${_escape_query(key)}=${_escape_query(value)}"
      }.mkString("?", "&", "")
  }

  private final case class _AggregateOperationBinding(
    kind: String,
    service: String,
    operation: String
  )

  private def _aggregate_operation_bindings(
    component: Component,
    aggregateName: String
  ): Vector[_AggregateOperationBinding] = {
    val definition = _aggregate_definition(component, aggregateName)
    val commandNames = definition.map(_.commands.map(_.name)).getOrElse(Vector.empty)
    val readNames = Vector(s"read-${aggregateName}", s"get-${aggregateName}", s"load-${aggregateName}", s"search-${aggregateName}")
    val createNames =
      definition.map(_.creates.map(_.name)).filter(_.nonEmpty)
        .getOrElse(Vector(s"create-${aggregateName}", s"new-${aggregateName}"))
    val updateNames = Vector(s"update-${aggregateName}") ++ commandNames
    val candidates =
      _operation_bindings(component, readNames, "read") ++
        _operation_bindings(component, createNames, "create") ++
        _operation_bindings(component, updateNames, "update")
    candidates
      .groupBy(x => (x.kind, NamingConventions.toNormalizedSegment(x.operation)))
      .values
      .toVector
      .flatMap(_preferred_aggregate_binding)
      .sortBy(x => (x.kind, x.service, x.operation))
  }

  private def _preferred_aggregate_binding(
    bindings: Vector[_AggregateOperationBinding]
  ): Option[_AggregateOperationBinding] =
    bindings match {
      case Vector(one) =>
        Some(one)
      case many =>
        many.find(x => NamingConventions.equivalentByNormalized(x.service, "aggregate"))
    }

  private def _operation_bindings(
    component: Component,
    names: Vector[String],
    kind: String
  ): Vector[_AggregateOperationBinding] = {
    val normalized = names.map(NamingConventions.toNormalizedSegment).toSet
    component.services.services.flatMap { service =>
      service.serviceDefinition.operations.operations.toVector.flatMap { operation =>
        val name = operation.name
        if (normalized.contains(NamingConventions.toNormalizedSegment(name)))
          Some(_AggregateOperationBinding(kind, service.serviceDefinition.name, name))
        else
          None
      }
    }
  }

  def renderComponentAdminViews(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val rows = component.viewDefinitions.map { definition =>
        val viewPath = NamingConventions.toNormalizedSegment(definition.name)
        val viewNames =
          if (definition.viewNames.isEmpty) "default"
          else definition.viewNames.map(_escape).mkString(", ")
        val queries =
          if (definition.queries.isEmpty) "none"
          else definition.queries.map(q => _escape(q.name)).mkString(", ")
        val sourceEvents =
          if (definition.sourceEvents.isEmpty) "none"
          else definition.sourceEvents.map(_escape).mkString(", ")
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/views/${viewPath}">${_escape(definition.name)}</a></td>
           |  <td>${_escape(definition.entityName)}</td>
           |  <td>${viewNames}</td>
           |  <td>${queries}</td>
           |  <td>${sourceEvents}</td>
           |  <td>${definition.rebuildable.map(_.toString).getOrElse("unspecified")}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          _admin_empty_state("No view definitions are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>View</th><th>Entity</th><th>Names</th><th>Queries</th><th>Source events</th><th>Rebuildable</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      val nav = _admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} View Administration",
        subtitle = "View read management baseline",
        body =
          s"""${nav}
             |${_admin_card("View read", body)}
             |${_admin_card("Deferred capabilities", "<p class=\"mb-0\">View mutation and rebuild controls stay deferred in this slice. This page exposes read/list drill-down only.</p>")}""".stripMargin
      ))
    }

  def renderComponentAdminViewDetail(
    subsystem: Subsystem,
    componentName: String,
    viewName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val basePath = s"/web/${componentPath}/admin/views/${viewPath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "view", viewPath)
      )
      val definition = _view_definition(component, viewName)
      val entityName = definition.map(_.entityName).getOrElse(_strip_surface_suffix(viewPath, "view").getOrElse(viewPath))
      val webSchema = WebSchemaResolver.resolveView(
        component,
        componentPath,
        viewPath,
        Some(entityName),
        webDescriptor,
        viewFields = definition.flatMap(_.fieldsFor("summary")).orElse(_admin_entity_view_fields(component, entityName, "summary")),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val metadata = definition match {
        case Some(d) =>
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <tbody>
             |    <tr><th>View</th><td>${_escape(d.name)}</td></tr>
             |    <tr><th>Entity</th><td>${_escape(d.entityName)}</td></tr>
             |    <tr><th>Names</th><td>${_escape(if (d.viewNames.isEmpty) "default" else d.viewNames.mkString(", "))}</td></tr>
             |    <tr><th>Queries</th><td>${_escape(if (d.queries.isEmpty) "none" else d.queries.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>Source events</th><td>${_escape(if (d.sourceEvents.isEmpty) "none" else d.sourceEvents.mkString(", "))}</td></tr>
             |    <tr><th>Rebuildable</th><td>${_escape(d.rebuildable.map(_.toString).getOrElse("unspecified"))}</td></tr>
             |  </tbody>
             |</table></div>""".stripMargin
        case None =>
          _admin_empty_state(s"No view definition is registered for ${viewName}.")
      }
      val nav = _admin_nav_card(Vector(
        "View definitions" -> s"/web/${componentPath}/admin/views",
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      val readResult = _admin_read_result_list_web_schema(
        subsystem,
        "/admin/view/read",
        Record.create(Vector("component" -> componentPath, "view" -> viewPath) ++ effectivePageRequest.toPairs),
        basePath,
        s"No view records are currently available for ${viewName}.",
        effectivePageRequest,
        webSchema.fields
      )
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(viewPath))} View",
        subtitle = "View read baseline",
        body =
          s"""${nav}
             |${_admin_card(s"${_title_label(viewPath)} metadata", metadata)}
             |${_admin_card("Read result", readResult)}""".stripMargin
      ))
    }

  def renderComponentAdminViewInstanceDetail(
    subsystem: Subsystem,
    componentName: String,
    viewName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val basePath = s"/web/${componentPath}/admin/views/${viewPath}"
      val definition = _view_definition(component, viewName)
      val entityName = definition.map(_.entityName).getOrElse(_strip_surface_suffix(viewPath, "view").getOrElse(viewPath))
      val webSchema = WebSchemaResolver.resolveView(
        component,
        componentPath,
        viewPath,
        Some(entityName),
        webDescriptor,
        viewFields = definition.flatMap(_.fieldsFor("detail")).orElse(_admin_entity_view_fields(component, entityName, "detail")),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val nav = _admin_nav_card(Vector(
        s"Back to ${_title_label(viewPath)} view" -> basePath,
        "View definitions" -> s"/web/${componentPath}/admin/views",
        "Component admin" -> s"/web/${componentPath}/admin"
      ))
      val body = _admin_read_result_table_web_schema(
        subsystem,
        "/admin/view/read",
        Record.data("component" -> componentPath, "view" -> viewPath, "id" -> id),
        s"No view record is currently available for ${id}.",
        webSchema.fields
      )
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(viewPath))} View Detail",
        subtitle = "View instance read baseline",
        body =
          s"""${nav}
             |${_admin_card(id, body)}""".stripMargin
      ))
    }

  def renderComponentAdminAggregates(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val rows = component.aggregateDefinitions.map { definition =>
        val aggregatePath = NamingConventions.toNormalizedSegment(definition.name)
        val members =
          if (definition.members.isEmpty) "none"
          else definition.members.map(m => _escape(s"${m.name}:${m.entityName}")).mkString(", ")
        val commands =
          if (definition.commands.isEmpty) "none"
          else definition.commands.map(c => _escape(c.name)).mkString(", ")
        val creates =
          if (definition.creates.isEmpty) "none"
          else definition.creates.map(c => _escape(c.name)).mkString(", ")
        val state =
          if (definition.state.isEmpty) "none"
          else definition.state.map(s => _escape(s.name)).mkString(", ")
        val invariants =
          if (definition.invariants.isEmpty) "none"
          else definition.invariants.map(i => _escape(i.name)).mkString(", ")
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/aggregates/${aggregatePath}">${_escape(definition.name)}</a></td>
           |  <td>${_escape(definition.entityName)}</td>
           |  <td>${members}</td>
           |  <td>${creates}</td>
           |  <td>${commands}</td>
           |  <td>${state}</td>
           |  <td>${invariants}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          _admin_empty_state("No aggregate definitions are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>Aggregate</th><th>Entity</th><th>Members</th><th>Creates</th><th>Commands</th><th>State</th><th>Invariants</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      val nav = _admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} Aggregate Administration",
        subtitle = "Aggregate CRUD management baseline",
        body =
          s"""${nav}
             |${_admin_card("Aggregate read", body)}
             |${_admin_card("Deferred capabilities", "<p class=\"mb-0\">Aggregate mutation and destructive administration remain deferred. Use this surface for read/list drill-down and related operation handoff.</p>")}""".stripMargin
      ))
    }

  def renderComponentAdminAggregateDetail(
    subsystem: Subsystem,
    componentName: String,
    aggregateName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val basePath = s"/web/${componentPath}/admin/aggregates/${aggregatePath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "aggregate", aggregatePath)
      )
      val definition = _aggregate_definition(component, aggregateName)
      val entityName = definition.map(_.entityName).getOrElse(_strip_surface_suffix(aggregatePath, "aggregate").getOrElse(aggregatePath))
      val webSchema = WebSchemaResolver.resolveAggregate(
        component,
        componentPath,
        aggregatePath,
        Some(entityName),
        webDescriptor,
        viewFields = _admin_entity_view_fields(component, entityName, "summary"),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val metadata = definition match {
        case Some(d) =>
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <tbody>
             |    <tr><th>Aggregate</th><td>${_escape(d.name)}</td></tr>
             |    <tr><th>Entity</th><td>${_escape(d.entityName)}</td></tr>
             |    <tr><th>Members</th><td>${_escape(if (d.members.isEmpty) "none" else d.members.map(m => s"${m.name}:${m.entityName}").mkString(", "))}</td></tr>
             |    <tr><th>Creates</th><td>${_escape(if (d.creates.isEmpty) "none" else d.creates.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>Commands</th><td>${_escape(if (d.commands.isEmpty) "none" else d.commands.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>State</th><td>${_escape(if (d.state.isEmpty) "none" else d.state.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>Invariants</th><td>${_escape(if (d.invariants.isEmpty) "none" else d.invariants.map(_.name).mkString(", "))}</td></tr>
             |  </tbody>
             |</table></div>""".stripMargin
        case None =>
          _admin_empty_state(s"No aggregate definition is registered for ${aggregateName}.")
      }
      val nav = _admin_nav_card(Vector(
        "Aggregate definitions" -> s"/web/${componentPath}/admin/aggregates",
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      val readResult = _admin_read_result_list_web_schema(
        subsystem,
        "/admin/aggregate/read",
        Record.create(Vector("component" -> componentPath, "aggregate" -> aggregatePath) ++ effectivePageRequest.toPairs),
        basePath,
        s"No aggregate records are currently available for ${aggregateName}.",
        effectivePageRequest,
        webSchema.fields
      )
      val operations = _aggregate_operation_actions(component, aggregateName)
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(aggregatePath))} Aggregate",
        subtitle = "Aggregate read baseline",
        body =
          s"""${nav}
             |${_admin_card(s"${_title_label(aggregatePath)} metadata", metadata)}
             |${_admin_card("Read result", readResult)}
             |${_admin_card("Operations", operations)}""".stripMargin
      ))
    }

  def renderComponentAdminAggregateInstanceDetail(
    subsystem: Subsystem,
    componentName: String,
    aggregateName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val basePath = s"/web/${componentPath}/admin/aggregates/${aggregatePath}"
      val definition = _aggregate_definition(component, aggregateName)
      val entityName = definition.map(_.entityName).getOrElse(_strip_surface_suffix(aggregatePath, "aggregate").getOrElse(aggregatePath))
      val webSchema = WebSchemaResolver.resolveAggregate(
        component,
        componentPath,
        aggregatePath,
        Some(entityName),
        webDescriptor,
        viewFields = _admin_entity_view_fields(component, entityName, "detail"),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val nav = _admin_nav_card(Vector(
        s"Back to ${_title_label(aggregatePath)} aggregate" -> basePath,
        "Aggregate definitions" -> s"/web/${componentPath}/admin/aggregates",
        "Component admin" -> s"/web/${componentPath}/admin"
      ))
      val body = _admin_read_result_table_web_schema(
        subsystem,
        "/admin/aggregate/read",
        Record.data("component" -> componentPath, "aggregate" -> aggregatePath, "id" -> id),
        s"No aggregate record is currently available for ${id}.",
        webSchema.fields
      )
      val operations = _aggregate_instance_operation_actions(component, aggregateName, id)
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(aggregatePath))} Aggregate Detail",
        subtitle = "Aggregate instance read baseline",
        body =
          s"""${nav}
             |${_admin_card(id, body)}
             |${_admin_card("Instance operations", operations)}""".stripMargin
      ))
    }

  def renderComponentAdminData(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val nav = _admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Descriptor" -> s"/web/${componentPath}/admin/descriptor",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} Data Administration",
        subtitle = "Data record management baseline",
        body =
          s"""${nav}
             |${_admin_card("Data record management", "<p>Concrete data collections use the existing list/detail/new/edit flows. Open a descriptor-backed data surface from the component descriptor or a direct collection link.</p><p class=\"mb-0\"><span class=\"badge text-bg-secondary\">Implemented baseline</span></p>")}
             |${_admin_card("Deferred capabilities", "<p class=\"mb-0\">Delete flows, destructive confirmation patterns, and runtime configuration mutation remain deferred in this slice.</p>")}""".stripMargin
      ))
    }

  def renderComponentAdminDataType(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val basePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "data", dataPath)
      )
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        _admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val result = _admin_data_list(subsystem, componentPath, dataPath, effectivePageRequest)
      val warningHtml = _admin_warnings(result.warnings)
      val table = _admin_read_result_list_table(
        result.items,
        webSchema.fieldNames,
        basePath,
        "No records are currently available for this data collection.",
        includeEdit = true
      )
      val nav = _admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Data CRUD" -> s"/web/${componentPath}/admin/data",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data Administration",
        subtitle = "Data record list baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <div>
             |        <h2 class="card-title mb-1">${_escape(_title_label(dataPath))} records</h2>
             |        <p class="card-text text-body-secondary">List with paging${result.total.map(t => s" · total ${_escape(t.toString)}").getOrElse("")}</p>
             |      </div>
             |      <a class="btn btn-primary" href="${_escape(basePath)}/new">New ${_escape(_title_label(dataPath))}</a>
             |    </div>
             |    ${warningHtml}
             |    ${table}
             |    ${_paging_nav(result.page, result.pageSize, result.total, effectivePageRequest.href(basePath), Some(result.hasNext))}
             |  </div>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataDetail(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val basePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val nav = _admin_nav_card(Vector(
        s"Back to ${_title_label(dataPath)} records" -> basePath
      ))
      val body = _admin_data_record_table(subsystem, componentPath, dataPath, id, webDescriptor)
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data Detail",
        subtitle = "Data record detail baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <h2 class="card-title mb-0">${_escape(_title_label(dataPath))} detail</h2>
             |      <a class="btn btn-primary" href="${_escape(basePath)}/${_escape(id)}/edit">Edit</a>
             |    </div>
             |    ${body}
             |  </div>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataEdit(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    id: String,
    values: Map[String, String] = Map.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    validation: Option[FormValidationResult] = None
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val actionPath = s"/form/${componentPath}/admin/data/${dataPath}/${id}/update"
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        _admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val effectiveValidation = validation.filter(_.webSchema.selector == webSchema.selector)
      val hiddenContext = _hidden_form_context_inputs(values)
      val controls = _admin_record_controls(
        webSchema.fields,
        _admin_data_record_fields(subsystem, componentPath, dataPath, id).getOrElse(Vector("id" -> id)),
        values,
        "data-field",
        effectiveValidation
      )
      val nav = _admin_nav_card(Vector(
        "Detail" -> s"${webBasePath}/${id}",
        s"Back to ${_title_label(dataPath)} records" -> webBasePath
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data Edit",
        subtitle = "Data record edit baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">Edit ${_escape(_title_label(dataPath))}</h2>
             |    ${_form_error_panel(values)}${_form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${_escape(actionPath)}" class="admin-form">
             |      ${controls}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Update</button>
             |        <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}/${_escape(id)}">Cancel</a>
             |      </div>
             |    </form>
             |  </div>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataNew(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    values: Map[String, String] = Map.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    validation: Option[FormValidationResult] = None
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val actionPath = s"/form/${componentPath}/admin/data/${dataPath}/create"
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        _admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val effectiveValidation = validation.filter(_.webSchema.selector == webSchema.selector)
      val hiddenContext = _hidden_form_context_inputs(values)
      val controls = _admin_new_controls(webSchema.fields, values, "dataFields", "id=record-1&#10;status=draft", effectiveValidation)
      val nav = _admin_nav_card(Vector(
        s"Back to ${_title_label(dataPath)} records" -> webBasePath,
        "Data CRUD" -> s"/web/${componentPath}/admin/data"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data New",
        subtitle = "Data record create baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">New ${_escape(_title_label(dataPath))}</h2>
             |    ${_form_error_panel(values)}${_form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${_escape(actionPath)}" class="admin-form">
             |      ${controls}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Create</button>
             |        <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}">Cancel</a>
             |      </div>
             |    </form>
             |  </div>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataUpdateResult(
    componentName: String,
    dataName: String,
    id: String,
    values: Map[String, String],
    applied: Boolean,
    message: String,
    resultStatus: Int = 200
  ): Page = {
    val componentPath = NamingConventions.toNormalizedSegment(componentName)
    val dataPath = NamingConventions.toNormalizedSegment(dataName)
    val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
    Page(_simple_page(
      title = s"${_escape(componentName)} ${_escape(_title_label(dataPath))} Data Update Result",
      subtitle = "Data record update submission baseline",
      body =
        s"""${_admin_nav_card(Vector(
             "Detail" -> s"${webBasePath}/${id}",
             s"Back to ${_title_label(dataPath)} records" -> webBasePath,
             "Edit again" -> s"${webBasePath}/${id}/edit"
           ))}
           |${_admin_card(
             "Update submitted",
             s"""<p>${_escape(message)}</p>
                |${_admin_table(Some(_admin_result_rows(applied, resultStatus, message)), _submitted_fields_rows(values))}""".stripMargin
           )}""".stripMargin
    ))
  }

  def renderComponentAdminDataCreateResult(
    componentName: String,
    dataName: String,
    values: Map[String, String],
    applied: Boolean,
    message: String,
    resultStatus: Int = 200
  ): Page = {
    val componentPath = NamingConventions.toNormalizedSegment(componentName)
    val dataPath = NamingConventions.toNormalizedSegment(dataName)
    val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
    Page(_simple_page(
      title = s"${_escape(componentName)} ${_escape(_title_label(dataPath))} Data Create Result",
      subtitle = "Data record create submission baseline",
      body =
        s"""${_admin_nav_card(Vector(
             s"Back to ${_title_label(dataPath)} records" -> webBasePath,
             "Create another" -> s"${webBasePath}/new"
           ))}
           |${_admin_card(
             "Create submitted",
             s"""<p>${_escape(message)}</p>
                |${_admin_table(Some(_admin_result_rows(applied, resultStatus, message)), _submitted_fields_rows(values))}""".stripMargin
           )}""".stripMargin
    ))
  }

  private def _admin_result_rows(
    applied: Boolean,
    resultStatus: Int,
    resultBody: String
  ): String =
    Vector(
      "Applied" -> applied.toString,
      "result.status" -> resultStatus.toString,
      "result.ok" -> (resultStatus >= 200 && resultStatus < 400).toString,
      "result.body" -> resultBody
    ).map { case (key, value) =>
      s"<tr><th>${_escape(key)}</th><td>${_escape(value)}</td></tr>"
    }.mkString

  def renderComponentAdmin(
    component: Component,
    webDescriptor: WebDescriptor
  ): Page =
    renderComponentAdmin(component, NamingConventions.toNormalizedSegment(component.name), webDescriptor)

  def renderComponentAdmin(
    component: Component,
    componentPath: String,
    webDescriptor: WebDescriptor
  ): Page = {
    Page(_admin_page(
      title = s"${_escape(component.name)} Admin Configuration",
      subtitle = "Current component runtime configuration",
      components = Vector(component),
      subsystemName = component.subsystem.map(_.name).getOrElse(component.name),
      subsystemVersion = component.subsystem.flatMap(_.version),
      dashboardPath = s"/web/${componentPath}/dashboard",
      performancePath = "/web/system/performance",
      webDescriptor = webDescriptor,
      runtimeConfiguration = None,
      operationalDetails = None,
      componentFormsPath = Some(s"/form/${componentPath}")
    ))
  }

  def renderSystemPerformance(subsystem: Subsystem): Page =
    Page(_performance_page(subsystem))

  def renderSystemAdminObservability(subsystem: Subsystem): Page =
    Page(_observability_admin_page(subsystem))

  def renderSystemAdminObservabilityMetrics(subsystem: Subsystem): Page =
    Page(_observability_metrics_page(subsystem))

  def renderSystemAdminObservabilityDiagnostics(): Page =
    Page(_observability_diagnostics_page())

  def renderSystemAdminObservabilityDiagnostic(
    scope: String,
    diagnosticKey: String
  ): Option[Page] =
    RuntimeDashboardMetrics.diagnosticDetail(scope, diagnosticKey).map(group =>
      Page(_observability_diagnostic_detail_page(group))
    )

  def renderSystemAdminKnowledge(subsystem: Subsystem): Page =
    Page(_knowledge_admin_page(subsystem))

  def renderSystemAdminKnowledgeComponent(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    KnowledgeSpaceProjection
      .componentOption(subsystem.components, componentName)
      .map(component => Page(_knowledge_component_page(subsystem, component)))

  def renderSystemAdminKnowledgeNode(
    subsystem: Subsystem,
    componentName: String,
    nodeId: String
  ): Option[Page] =
    for {
      component <- KnowledgeSpaceProjection.componentOption(subsystem.components, componentName)
      projection <- KnowledgeSpaceProjection.nodeOption(component, KnowledgeNodeId(nodeId))
    } yield Page(_knowledge_node_page(subsystem, projection))

  def renderSystemConsole(subsystem: Subsystem): Page =
    Page(_simple_page(
      title = "System Console",
      subtitle = "Controlled operation entry",
      body =
        s"""${_admin_nav_card(Vector(
             "System dashboard" -> "/web/system/dashboard",
             "Admin configuration" -> "/web/system/admin",
             "Performance details" -> "/web/system/performance",
             "Documents" -> "/web/system/document"
           ))}
           |${_admin_card(
             "Operation forms",
             s"""<p>Console links to operation forms. It does not execute operations inline.</p>
                |${_component_form_list(subsystem.components)}""".stripMargin
           )}""".stripMargin
    ))

  private def _find_component(
    subsystem: Subsystem,
    name: String
  ): Option[Component] =
    subsystem.components.find(x =>
      NamingConventions.equivalentByNormalized(x.name, name) ||
        x.artifactMetadata.toVector.exists { metadata =>
          metadata.component.exists(NamingConventions.equivalentByNormalized(_, name)) ||
            NamingConventions.equivalentByNormalized(metadata.name, name)
        }
    )

  private def _operation_selector(
    componentName: String,
    serviceName: String,
    operationName: String
  ): String =
    Vector(componentName, serviceName, operationName)
      .map(NamingConventions.toNormalizedSegment)
      .mkString(".")

  private def _dashboard_shell(
    title: String,
    subtitle: String,
    statePath: String
  ): String =
    StaticFormAppLayout.bootstrapPage(StaticFormAppLayout.Options(
      title = title,
      subtitle = subtitle,
      extraHead =
       """|    .status { display: flex; align-items: center; gap: 10px; color: #4d5662; }
       |    .pulse { width: 10px; height: 10px; border-radius: 50%; background: #159947; box-shadow: 0 0 0 0 rgba(21,153,71,.55); animation: pulse 1s infinite; }
       |    .metric strong { display: block; font-size: 30px; margin-top: 6px; }
       |    .big { font-size: 34px; font-weight: 700; }
       |    .dashboard-spark { height: 120px; display: flex; gap: .25rem; align-items: end; border-bottom: var(--bs-border-width) solid var(--bs-border-color); }
       |    .dashboard-spark span { display: block; flex: 1 1 0; min-width: 2px; min-height: 2px; background: var(--bs-primary); border-radius: .25rem .25rem 0 0; }
       |    .dashboard-spark span.error { background: var(--bs-danger); }
       |    @keyframes pulse { 70% { box-shadow: 0 0 0 12px rgba(21,153,71,0); } 100% { box-shadow: 0 0 0 0 rgba(21,153,71,0); } }
       |""".stripMargin,
      body =
        s"""|    <div class="status mb-3"><span class="pulse"></span><span id="statusText">Connecting</span></div>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-lg-4"><article id="healthPanel" class="card h-100 shadow-sm border-success"><div class="card-body"><h2 class="h5 card-title">Health</h2><div class="big"><span id="healthText" class="badge text-bg-success">UP</span></div><p class="text-secondary mb-0 mt-2" id="healthNote">Starting</p></div></article></div>
       |      <div class="col-12 col-lg-4"><article class="card h-100 shadow-sm"><div class="card-body"><h2 class="h5 card-title">Subsystem</h2><p class="mb-1"><strong id="subsystemName">-</strong></p><p class="text-secondary mb-0" id="subsystemVersion">-</p></div></article></div>
       |      <div class="col-12 col-lg-4"><article class="card h-100 shadow-sm"><div class="card-body"><h2 class="h5 card-title">CNCF</h2><p class="mb-1"><strong id="cncfVersion">-</strong></p><p class="mb-0"><a id="detailsLink" href="/web/system/admin">Admin details</a> · <a id="performanceLink" href="/web/system/performance">Performance details</a> · <a id="manualLink" href="/web/system/document">Documents</a> · <a id="consoleLink" href="/web/console">Console</a></p></div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12"><article class="card shadow-sm"><div class="card-body">
       |        <div class="d-flex flex-column flex-lg-row justify-content-between gap-2 mb-3">
       |          <div>
       |            <h2 class="h5 card-title mb-1">Recent failures</h2>
       |            <p class="text-secondary mb-0">Recent failures are diagnostics; they do not change runtime Health.</p>
       |          </div>
       |          <a class="btn btn-outline-primary btn-sm align-self-start" id="recentFailuresDetailLink" href="/web/system/performance#recent-errors">Failure details</a>
       |        </div>
       |        <div class="list-group list-group-horizontal-lg" id="recentFailuresList">
       |          <a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-3" id="httpRecentErrorsLink" href="/web/system/performance#recent-errors"><span>HTTP recent errors</span><span class="badge text-bg-secondary" id="httpRecentErrorsCount">0</span></a>
       |          <a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-3" id="authorizationDenialsLink" href="/web/system/performance#authorization"><span>Authorization denials</span><span class="badge text-bg-secondary" id="authorizationDenialsCount">0</span></a>
       |          <a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-3" id="failedJobsLink" href="/form/admin/execution/history"><span>Failed jobs</span><span class="badge text-bg-secondary" id="failedJobsCount">0</span></a>
       |          <a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-3" id="assemblyWarningsDiagnosticLink" href="/web/system/admin/assembly/warnings"><span>Assembly warnings</span><span class="badge text-bg-secondary" id="assemblyWarningsDiagnosticCount">0</span></a>
       |        </div>
       |      </div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Components</span><strong id="componentCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Services</span><strong id="serviceCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Operations</span><strong id="operationCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">HTML requests</span><strong id="requestCount">0</strong><small class="text-secondary" id="requestErrors">errors 0</small></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Jobs</span><strong id="jobCount">0</strong><small class="text-secondary" id="jobErrors">errors 0</small></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Assembly warnings</span><strong id="assemblyWarningCount">0</strong><small><a id="assemblyWarningsLink" href="/web/system/admin/assembly/warnings">details</a></small></div></div></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">Traffic</h2>
       |        <div class="btn-group mb-3" id="graphTabs" role="group">
       |          <button type="button" class="btn btn-outline-primary btn-sm" data-window="minute">1 minute</button>
       |          <button type="button" class="btn btn-primary btn-sm active" data-window="hour">1 hour</button>
       |          <button type="button" class="btn btn-outline-primary btn-sm" data-window="day">1 day</button>
       |        </div>
       |        <div class="dashboard-spark" id="requestSpark"></div>
       |      </div></article></div>
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">Activity counts</h2>
       |        <div id="activityCounts"></div>
       |      </div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">ActionCall jobs</h2>
       |        <div class="list-group list-group-flush" id="jobBars"></div>
       |      </div></article></div>
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">Components</h2>
       |        <div class="list-group list-group-flush" id="componentBars"></div>
       |      </div></article></div>
       |    </section>
       |    <article class="card shadow-sm"><div class="card-body">
       |      <h2 class="h5 card-title">Configuration summary</h2>
       |      <div id="configSummary"></div>
       |    </div></article>
       |""".stripMargin,
      extraScript =
        s"""|  <script>
       |    const statePath = "${statePath}";
       |    const text = document.getElementById("statusText");
       |    const healthPanel = document.getElementById("healthPanel");
       |    const healthText = document.getElementById("healthText");
       |    const healthNote = document.getElementById("healthNote");
       |    const subsystemName = document.getElementById("subsystemName");
       |    const subsystemVersion = document.getElementById("subsystemVersion");
       |    const cncfVersion = document.getElementById("cncfVersion");
       |    const detailsLink = document.getElementById("detailsLink");
       |    const performanceLink = document.getElementById("performanceLink");
       |    const manualLink = document.getElementById("manualLink");
       |    const consoleLink = document.getElementById("consoleLink");
       |    const componentCount = document.getElementById("componentCount");
       |    const serviceCount = document.getElementById("serviceCount");
       |    const operationCount = document.getElementById("operationCount");
       |    const requestCount = document.getElementById("requestCount");
       |    const requestErrors = document.getElementById("requestErrors");
       |    const jobCount = document.getElementById("jobCount");
       |    const jobErrors = document.getElementById("jobErrors");
       |    const assemblyWarningCount = document.getElementById("assemblyWarningCount");
       |    const assemblyWarningsLink = document.getElementById("assemblyWarningsLink");
       |    const recentFailuresDetailLink = document.getElementById("recentFailuresDetailLink");
       |    const httpRecentErrorsLink = document.getElementById("httpRecentErrorsLink");
       |    const httpRecentErrorsCount = document.getElementById("httpRecentErrorsCount");
       |    const authorizationDenialsLink = document.getElementById("authorizationDenialsLink");
       |    const authorizationDenialsCount = document.getElementById("authorizationDenialsCount");
       |    const failedJobsLink = document.getElementById("failedJobsLink");
       |    const failedJobsCount = document.getElementById("failedJobsCount");
       |    const assemblyWarningsDiagnosticLink = document.getElementById("assemblyWarningsDiagnosticLink");
       |    const assemblyWarningsDiagnosticCount = document.getElementById("assemblyWarningsDiagnosticCount");
       |    const requestSpark = document.getElementById("requestSpark");
       |    const activityCounts = document.getElementById("activityCounts");
       |    const jobBars = document.getElementById("jobBars");
       |    const componentBars = document.getElementById("componentBars");
       |    const configSummary = document.getElementById("configSummary");
       |    const graphTabs = document.getElementById("graphTabs");
       |    let graphWindow = "hour";
       |    let latestData = null;
       |
       |    function escapeHtml(value) {
       |      return String(value).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","\\"":"&quot;","'":"&#39;"}[c]));
       |    }
       |
       |    function render(data) {
       |      latestData = data;
       |      const failedJobs = data.actions.jobs.failed || 0;
       |      const recentFailures = data.html.requests.summary.minute.errors || 0;
       |      const recentDenials = data.authorization.decisions.summary.minute.errors || 0;
       |      const assemblyWarnings = data.assembly.warnings.count || 0;
       |      const health = data.status || "UP";
       |      const healthVariant = health == "UP" ? "success" : (health == "DOWN" ? "danger" : "warning");
       |      healthPanel.classList.remove("border-success", "border-warning", "border-danger");
       |      healthPanel.classList.add("border-" + healthVariant);
       |      healthText.className = "badge text-bg-" + healthVariant;
       |      healthText.textContent = health;
       |      healthNote.textContent = `Runtime status: $${health}. Recent failures are listed separately.`;
       |      recentFailuresDetailLink.href = data.links.performance + "#recent-errors";
       |      httpRecentErrorsLink.href = data.links.performance + "#recent-errors";
       |      authorizationDenialsLink.href = data.links.performance + "#authorization";
       |      failedJobsLink.href = "/form/admin/execution/history";
       |      assemblyWarningsDiagnosticLink.href = data.links.assemblyWarnings;
       |      setDiagnosticBadge(httpRecentErrorsCount, recentFailures, "danger");
       |      setDiagnosticBadge(authorizationDenialsCount, recentDenials, "warning");
       |      setDiagnosticBadge(failedJobsCount, failedJobs, "danger");
       |      setDiagnosticBadge(assemblyWarningsDiagnosticCount, assemblyWarnings, "warning");
       |      subsystemName.textContent = data.subsystem.name;
       |      subsystemVersion.textContent = "subsystem " + (data.subsystem.version || "unversioned");
       |      cncfVersion.textContent = data.cncf.version;
       |      detailsLink.href = data.links.admin;
       |      performanceLink.href = data.links.performance;
       |      manualLink.href = data.links.manual;
       |      consoleLink.href = data.links.console;
       |      componentCount.textContent = data.componentCount;
       |      serviceCount.textContent = data.serviceCount;
       |      operationCount.textContent = data.operationCount;
       |      requestCount.textContent = data.html.requests.summary.cumulative.count;
       |      requestErrors.textContent = "errors " + data.html.requests.summary.cumulative.errors;
       |      jobCount.textContent = data.actions.jobs.total;
       |      jobErrors.textContent = "errors " + data.actions.jobs.failed;
       |      assemblyWarningCount.textContent = assemblyWarnings;
       |      assemblyWarningsLink.href = data.links.assemblyWarnings;
       |      text.textContent = health + " · " + new Date(data.observedAt).toLocaleTimeString();
       |      const maxOps = Math.max(1, ...data.components.map(c => c.operationCount));
       |      componentBars.innerHTML = data.components.map(c => {
       |        const width = Math.round((c.operationCount / maxOps) * 100);
       |        return dashboardProgressRow(escapeHtml(c.name), escapeHtml(c.version || ""), c.operationCount, width, "primary");
       |      }).join("");
       |      renderGraph();
       |      activityCounts.innerHTML = countTable(data);
       |      const jobTotal = Math.max(1, data.actions.jobs.total);
       |      jobBars.innerHTML = ["running","queued","completed","failed"].map(name => {
       |        const count = data.actions.jobs[name] || 0;
       |        const width = Math.round((count / jobTotal) * 100);
       |        const variant = name === "failed" ? "danger" : (name === "running" ? "success" : "primary");
       |        return dashboardProgressRow(name, "", count, width, variant);
       |      }).join("");
       |      configSummary.innerHTML = data.components.map(c => `<p><strong>$${escapeHtml(c.name)}</strong> $${escapeHtml(c.version || "unversioned")} · services $${c.serviceCount} · operations $${c.operationCount}</p>`).join("");
       |    }
       |
       |    function setDiagnosticBadge(element, count, variant) {
       |      element.textContent = count;
       |      element.className = count > 0 ? `badge text-bg-$${variant}` : "badge text-bg-secondary";
       |    }
       |
       |    function dashboardProgressRow(label, subtitle, count, width, variant) {
       |      const note = subtitle ? `<small class="text-body-secondary">$${subtitle}</small>` : "";
       |      return `<div class="list-group-item px-0">
       |        <div class="d-flex flex-wrap justify-content-between align-items-baseline gap-2 mb-1">
       |          <span><span class="fw-semibold">$${label}</span> $${note}</span>
       |          <span class="badge text-bg-secondary">$${count}</span>
       |        </div>
       |        <div class="progress" role="progressbar" aria-valuenow="$${width}" aria-valuemin="0" aria-valuemax="100" style="height:.6rem">
       |          <div class="progress-bar bg-$${variant}" style="width:$${width}%"></div>
       |        </div>
       |      </div>`;
       |    }
       |
       |    function countTable(data) {
       |      const rows = [
       |        ["HTML request", data.html.requests.summary],
       |        ["ActionCall", data.actions.actionCalls.summary],
       |        ["DSL Chokepoints", data.dsl.chokepoints.summary],
       |        ["Jobs", data.actions.jobs.summary],
       |        ["Authorization", data.authorization.decisions.summary]
       |      ];
       |      const cells = rows.map(([name, summary]) => `<tr><td>$${name}</td><td>$${summary.cumulative.count}</td><td>$${summary.cumulative.errors}</td><td>$${summary.day.count}</td><td>$${summary.day.errors}</td><td>$${summary.hour.count}</td><td>$${summary.hour.errors}</td><td>$${summary.minute.count}</td><td>$${summary.minute.errors}</td></tr>`).join("");
       |      return `<div class="table-responsive"><table class="table table-sm table-hover align-middle"><thead><tr><th>Level</th><th>Total</th><th>Total err</th><th>1d</th><th>1d err</th><th>1h</th><th>1h err</th><th>1m</th><th>1m err</th></tr></thead><tbody>$${cells}</tbody></table></div>`;
       |    }
       |
       |    function renderGraph() {
       |      if (!latestData) return;
       |      const buckets = latestData.html.requests.series[graphWindow];
       |      const maxReq = Math.max(1, ...buckets.map(b => b.count));
       |      requestSpark.title = graphWindow + " / avg " + latestData.html.requests.recentAverageMillis + "ms";
       |      requestSpark.innerHTML = buckets.map(b => `<span class="$${b.errors > 0 ? "error" : ""}" style="height:$${Math.max(2, Math.round((b.count / maxReq) * 110))}px"></span>`).join("");
       |    }
       |
       |    graphTabs.addEventListener("click", event => {
       |      if (event.target.tagName !== "BUTTON") return;
       |      graphWindow = event.target.dataset.window;
       |      graphTabs.querySelectorAll("button").forEach(b => {
       |        const selected = b.dataset.window === graphWindow;
       |        b.classList.toggle("active", selected);
       |        b.classList.toggle("btn-primary", selected);
       |        b.classList.toggle("btn-outline-primary", !selected);
       |      });
       |      renderGraph();
       |    });
       |
       |    async function refresh() {
       |      try {
       |        const res = await fetch(statePath, { cache: "no-store" });
       |        render(await res.json());
       |      } catch (e) {
       |        text.textContent = "Refresh failed";
       |      }
       |    }
       |
       |    refresh();
       |    setInterval(refresh, 1000);
       |  </script>
       |""".stripMargin
    ))

  private def _subsystem_dashboard_state(subsystem: Subsystem): String =
    _dashboard_state_json(subsystem.components, "subsystem", subsystem.name, subsystem.version, _job_metrics(subsystem), subsystem.name, subsystem.version, _assembly_warning_count(subsystem))

  private def _admin_page(
    title: String,
    subtitle: String,
    components: Vector[Component],
    subsystemName: String,
    subsystemVersion: Option[String],
    dashboardPath: String,
    performancePath: String,
    webDescriptor: WebDescriptor,
    runtimeConfiguration: Option[ResolvedConfiguration],
      operationalDetails: Option[String],
      componentFormsPath: Option[String]
  ): String = {
    val descriptorPath = componentFormsPath
      .map(_.stripPrefix("/form/"))
      .map(componentPath => s"/web/${componentPath}/admin/descriptor")
      .getOrElse("/web/system/admin/descriptor")
    val componentBlocks = components.map { component =>
      val services = component.protocol.services.services.map { service =>
        val operations = service.operations.operations.toVector.map { operation =>
          val path = NamingConventions.toNormalizedPath(component.name, service.name, operation.name)
          s"""<li>${_escape(operation.name)} <code>${_escape(path)}</code></li>"""
        }.mkString("\n")
        s"""<section><h3>${_escape(service.name)}</h3><ul>${operations}</ul></section>"""
      }.mkString("\n")
      val version = component.artifactMetadata.map(_.version).getOrElse("unversioned")
      val componentlets = _componentlet_table(component)
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityCount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val dataCount = _admin_surface_selector_count(webDescriptor, Some(componentPath), "data")
      val aggregateCount = component.aggregateDefinitions.size
      val viewCount = component.viewDefinitions.size
      val formsCount = component.protocol.services.services.map(_.operations.operations.toVector.size).sum
      val componentAdminPages = webDescriptor.adminPagesFor(componentPath)
      val cards = _admin_entry_cards(Vector(
        _admin_entry_card(
          "Entities",
          s"${_pluralize(entityCount, "runtime descriptor", "runtime descriptors")} ready for list/detail/new/edit.",
          s"/web/${componentPath}/admin/entities",
          Some(entityCount.toString)
        ),
        _admin_entry_card(
          "Data",
          if (dataCount > 0)
            s"${_pluralize(dataCount, "descriptor surface", "descriptor surfaces")} available through data admin."
          else
            "Open descriptor-backed data collections and concrete datastore records.",
          s"/web/${componentPath}/admin/data",
          Some(dataCount.toString)
        ),
        _admin_entry_card(
          "Aggregates",
          s"${_pluralize(aggregateCount, "aggregate", "aggregates")} available for read/list drill-down.",
          s"/web/${componentPath}/admin/aggregates",
          Some(aggregateCount.toString)
        ),
        _admin_entry_card(
          "Views",
          s"${_pluralize(viewCount, "view definition", "view definitions")} available for read-only inspection.",
          s"/web/${componentPath}/admin/views",
          Some(viewCount.toString)
        ),
        _admin_entry_card(
          "Descriptor",
          "Inspect routes, forms, auth controls, and admin surfaces.",
          s"/web/${componentPath}/admin/descriptor"
        ),
        _admin_entry_card(
          "Forms",
          s"${_pluralize(formsCount, "operation form", "operation forms")} available for controlled execution.",
          s"/form/${componentPath}",
          Some(formsCount.toString)
        )
      ))
      val componentOwnedAdminPages = _component_owned_admin_pages(componentAdminPages)
      val technicalDetails =
        s"""<details class="mt-3">
           |  <summary>Technical details</summary>
           |  <div class="mt-3">
           |    <h3>${_escape(component.name)}</h3>
           |    <p class="text-body-secondary">Version ${_escape(version)}</p>
           |    ${componentlets}
           |    ${services}
           |  </div>
           |</details>""".stripMargin
      _admin_card(
        component.name,
        s"""<p class="text-body-secondary">Version ${_escape(version)}</p>
           |${cards}
           |${componentOwnedAdminPages}
           |${technicalDetails}""".stripMargin
      )
    }.mkString("\n")
    val componentInventory =
      if (componentFormsPath.isEmpty)
        _system_admin_component_inventory(components)
      else
        ""
    val runtimeRows =
      s"""<tr><th>CNCF version</th><td>${_escape(CncfVersion.current)}</td></tr>
         |<tr><th>Subsystem</th><td>${_escape(subsystemName)}</td></tr>
         |<tr><th>Subsystem version</th><td>${_escape(subsystemVersion.getOrElse("unversioned"))}</td></tr>
         |<tr><th>Components</th><td>${components.size}</td></tr>""".stripMargin
    val runtimeCard =
      _admin_card(
        "Runtime",
        _admin_table(None, runtimeRows, tableClass = "table table-sm align-middle mb-0")
      )
    val primaryNav = Vector(
      "Application admin" -> "/web/admin",
      "Dashboard" -> dashboardPath,
      "Performance details" -> performancePath,
      "Observability" -> "/web/system/admin/observability",
      "Documents" -> "/web/system/document",
      "Console" -> "/web/console"
    )
    _simple_page(
      title = title,
      subtitle = subtitle,
      body =
        s"""${runtimeCard}
           |${_admin_nav_card(primaryNav)}
           |${componentInventory}
           |${_admin_operational_details(operationalDetails)}
           |${_component_admin_actions(componentFormsPath)}
           |${_admin_card("Web Descriptor", _web_descriptor_summary(webDescriptor, descriptorPath))}
           |${_admin_runtime_configuration(runtimeConfiguration)}
           |${_admin_job_control(runtimeConfiguration)}
           |${componentBlocks}""".stripMargin
    )
  }

  private def _admin_job_control(
    configuration: Option[ResolvedConfiguration]
  ): String =
    configuration.map { _ =>
      _admin_card(
        "Job Control",
        s"""<p>Job control entry points are reserved for system admin operations. Use builtin job and event surfaces as the authoritative source for cross-component continuation observability.</p>
           |<h3 class="h6 mt-3">Operator Checklist</h3>
           |<div class="list-group mb-3">
           |  <div class="list-group-item">Use <code>job_control.job.get_job_status</code> for source/target component, reception policy, task relation, transaction relation, and task summary.</div>
           |  <div class="list-group-item">Use <code>job_control.job.load_job_history</code> for per-task execution timeline.</div>
           |  <div class="list-group-item">Use <code>event.event.load_event</code> for dispatch contract, policy source, and unsupported-policy rejection evidence.</div>
           |  <div class="list-group-item">Use <code>job_control.job_admin.load_job_events</code> for job-to-event cross-links.</div>
           |</div>
           |${_admin_link_list_group(Vector(
             "Debug jobs" -> "/web/system/admin/jobs",
             "Execution diagnostics" -> "/form/admin/execution/diagnostics",
             "Execution history" -> "/form/admin/execution/history",
             "Latest calltree" -> "/form/admin/execution/calltree"
           ))}
           |<p class="mt-3 mb-0">Mutation actions must require explicit admin authorization, confirmation for destructive actions, and audit logging before they are enabled.</p>""".stripMargin
      )
    }.getOrElse("")

  private def _application_admin_entry_pages(
    webDescriptor: WebDescriptor
  ): String = {
    val pages = webDescriptor.adminPagesForAudience(WebDescriptor.AdminAudience.Application)
    if (pages.isEmpty)
      _admin_card("Application Admin Pages", _admin_empty_state("No descriptor-declared application admin pages are available."))
    else
      _admin_card("Application Admin Pages", _component_owned_admin_pages_list(pages))
  }

  private def _application_admin_component_cards(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor
  ): String = {
    val cards = subsystem.components.toVector.flatMap { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityCount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val dataCount = _admin_surface_selector_count(webDescriptor, Some(componentPath), "data")
      val aggregateCount = component.aggregateDefinitions.size
      val viewCount = component.viewDefinitions.size
      val entries = Vector(
        Option.when(entityCount > 0)(_admin_entry_card(
          "Entities",
          s"${_pluralize(entityCount, "runtime descriptor", "runtime descriptors")} available for application operations.",
          s"/web/${componentPath}/admin/entities",
          Some(entityCount.toString)
        )),
        Option.when(dataCount > 0)(_admin_entry_card(
          "Data",
          s"${_pluralize(dataCount, "descriptor surface", "descriptor surfaces")} available for application operations.",
          s"/web/${componentPath}/admin/data",
          Some(dataCount.toString)
        )),
        Option.when(aggregateCount > 0)(_admin_entry_card(
          "Aggregates",
          s"${_pluralize(aggregateCount, "aggregate", "aggregates")} available for application operations.",
          s"/web/${componentPath}/admin/aggregates",
          Some(aggregateCount.toString)
        )),
        Option.when(viewCount > 0)(_admin_entry_card(
          "Views",
          s"${_pluralize(viewCount, "view definition", "view definitions")} available for application operations.",
          s"/web/${componentPath}/admin/views",
          Some(viewCount.toString)
        ))
      ).flatten
      Option.when(entries.nonEmpty) {
        _admin_card(
          component.name,
          s"""<p class="text-body-secondary">Application-facing generic admin surfaces.</p>
             |${_admin_entry_cards(entries)}
             |${_admin_action_row(Vector("Component admin" -> s"/web/${componentPath}/admin"), primary = false)}""".stripMargin
        )
      }
    }
    if (cards.isEmpty)
      _admin_card("Component Admin Surfaces", _admin_empty_state("No application-facing component admin surfaces are available."))
    else
      cards.mkString("\n")
  }

  private def _component_owned_admin_pages(
    pages: Vector[WebDescriptor.AdminPage]
  ): String =
    if (pages.isEmpty)
      ""
    else {
      s"""<section class="mt-3">
         |  <h3 class="h6">Component Admin Pages</h3>
         |  ${_component_owned_admin_pages_list(pages)}
         |</section>""".stripMargin
    }

  private def _component_owned_admin_pages_list(
    pages: Vector[WebDescriptor.AdminPage]
  ): String = {
    val items = pages.map { page =>
      val description = Option(page.description).map(_.trim).filter(_.nonEmpty)
        .map(value => s"""<p class="mb-2 text-body-secondary">${_escape(value)}</p>""")
        .getOrElse("")
      s"""<a class="list-group-item list-group-item-action" href="${_escape(page.href)}">
         |  <div class="d-flex justify-content-between align-items-start gap-3">
         |    <div>
         |      <div class="fw-semibold">${_escape(page.effectiveLabel)}</div>
         |      ${description}
         |      <code>${_escape(page.href)}</code>
         |    </div>
         |    <span class="badge text-bg-secondary">${_escape(page.effectivePermission)}</span>
         |  </div>
         |</a>""".stripMargin
    }
    s"""<div class="list-group">${items.mkString("\n")}</div>"""
  }

  private def _admin_runtime_configuration(
    configuration: Option[ResolvedConfiguration]
  ): String =
    configuration.map { config =>
      _admin_card(
        "Runtime Configuration",
        s"""<p>Resolved runtime configuration values are read-only. Sensitive values are masked.</p>
           |<p>Configuration mutation must use a separate admin action surface with explicit admin authorization and audit logging.</p>
           |${_effective_runtime_configuration_table(config)}
           |${_runtime_configuration_table(config)}""".stripMargin
      )
    }.getOrElse("")

  private def _effective_runtime_configuration_table(
    config: ResolvedConfiguration
  ): String = {
    val runtime = RuntimeConfig.from(config)
    val rows = Vector(
      "textus.operation-mode" -> runtime.operationMode.name,
      "textus.mode" -> runtime.mode.name,
      "textus.web.develop.anonymous-admin" -> runtime.webDevelopAnonymousAdmin.toString,
      "textus.web.operation.dispatcher" -> runtime.webOperationDispatcher
    ).map {
      case (key, value) =>
        s"""<tr><td><code>${_escape(key)}</code></td><td>${_escape(value)}</td></tr>"""
    }
    s"""<h3>Effective Runtime Policy</h3>
       |<div class="table-responsive"><table class="table table-sm">
       |  <thead><tr><th>Key</th><th>Value</th></tr></thead>
       |  <tbody>${rows.mkString("\n")}</tbody>
       |</table></div>""".stripMargin
  }

  private def _runtime_configuration_table(
    config: ResolvedConfiguration
  ): String = {
    val rows = config.configuration.values.toVector
      .filter { case (key, _) => _is_runtime_configuration_key(key) }
      .sortBy(_._1)
      .map {
        case (key, value) =>
          val sensitive = _is_sensitive_configuration_key(key)
          val rendered = if (sensitive) "********" else _configuration_value_text(value)
          val visibility = if (sensitive) "masked" else "visible"
          s"""<tr><td><code>${_escape(key)}</code></td><td>${_escape(rendered)}</td><td>${visibility}</td></tr>"""
      }
    if (rows.isEmpty) {
      "<p>No explicit runtime configuration values are resolved.</p>"
    } else {
      s"""<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Key</th><th>Value</th><th>Visibility</th></tr></thead>
         |  <tbody>${rows.mkString("\n")}</tbody>
         |</table></div>""".stripMargin
    }
  }

  private def _is_runtime_configuration_key(key: String): Boolean =
    key.startsWith("textus.") || key.startsWith("cncf.")

  private def _is_sensitive_configuration_key(key: String): Boolean = {
    val normalized = key.toLowerCase
    Vector("password", "passwd", "secret", "token", "credential", "apikey", "api-key", "private-key")
      .exists(normalized.contains)
  }

  private def _configuration_value_text(
    value: ConfigurationValue
  ): String =
    value match {
      case ConfigurationValue.StringValue(v) => v
      case ConfigurationValue.NumberValue(v) => v.toString
      case ConfigurationValue.BooleanValue(v) => v.toString
      case ConfigurationValue.ListValue(vs) => vs.map(_configuration_value_text).mkString("[", ", ", "]")
      case ConfigurationValue.ObjectValue(vs) =>
        vs.toVector.sortBy(_._1).map {
          case (key, value) => s"${key}: ${_configuration_value_text(value)}"
        }.mkString("{", ", ", "}")
      case ConfigurationValue.NullValue => "null"
    }

  private def _admin_operational_details(
    html: Option[String]
  ): String =
    html.map { body =>
      _admin_card("Operational Details", body)
    }.getOrElse("")

  private def _system_admin_operational_details(
    webDescriptor: WebDescriptor,
    components: Vector[Component]
  ): String = {
    val systemPages = webDescriptor.adminPagesForAudience(WebDescriptor.AdminAudience.System)
    val systemPageLinks =
      if (systemPages.isEmpty)
        ""
      else
        s"""<div class="mt-3">
           |  <h3 class="h6">System Admin Pages</h3>
           |  ${_component_owned_admin_pages_list(systemPages)}
           |</div>""".stripMargin
    s"""<div class="row g-3">
      |  <div class="col-12 col-lg-6">
      |    <section class="h-100">
      |      <h3 class="h6">Assembly</h3>
      |      <div class="list-group">
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/assembly/warnings">Assembly warnings</a>
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/assembly/report">Assembly report</a>
      |      </div>
      |    </section>
      |  </div>
      |  <div class="col-12 col-lg-6">
      |    <section class="h-100">
      |      <h3 class="h6">Execution</h3>
      |      <div class="list-group">
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/knowledge">KnowledgeSpace</a>
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/observability">Observability diagnostics</a>
      |        <a class="list-group-item list-group-item-action" href="/form/admin/execution/history">Execution history</a>
      |        <a class="list-group-item list-group-item-action" href="/form/admin/execution/calltree">Latest calltree</a>
      |      </div>
      |    </section>
      |  </div>
      |</div>
      |${systemPageLinks}
      |${_component_dev_dir_diagnostics(components)}""".stripMargin
  }

  private def _component_dev_dir_diagnostics(
    components: Vector[Component]
  ): String = {
    val rows = components.flatMap { component =>
      component.artifactMetadata.toVector.filter(_.sourceType == "component-dev-dir").map { metadata =>
        val base = metadata.archivePath.map(p => Paths.get(p).toAbsolutePath.normalize)
        val classpath = base.map(_.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"))
        val webRoots = base.toVector.flatMap { path =>
          Vector(path.resolve("car.d").resolve("web"), path.resolve("src").resolve("main").resolve("web"), path.resolve("web"))
            .filter(Files.isDirectory(_))
        }.map(path => _escape(path.toString)).mkString("<br>")
        s"""<tr>
           |  <td><code>${_escape(component.name)}</code></td>
           |  <td><code>${_escape(base.map(_.toString).getOrElse(""))}</code></td>
           |  <td><code>${_escape(classpath.map(_.toString).getOrElse(""))}</code></td>
           |  <td>${if (webRoots.isEmpty) "<span class=\"text-body-secondary\">No Web root</span>" else webRoots}</td>
           |</tr>""".stripMargin
      }
    }
    if (rows.isEmpty) {
      ""
    } else {
      s"""<div class="mt-3">
         |  <h3 class="h6">Component Development Directories</h3>
         |  <div class="table-responsive">
         |    <table class="table table-sm table-hover align-middle">
         |      <thead><tr><th>Component</th><th>Development directory</th><th>Runtime classpath</th><th>Web resource roots</th></tr></thead>
         |      <tbody>${rows.mkString("\n")}</tbody>
         |    </table>
         |  </div>
         |</div>""".stripMargin
    }
  }

  private def _componentlet_table(
    component: Component
  ): String = {
    val rows = component.componentDescriptors
      .flatMap(_.componentlets)
      .sortBy(_.name)
      .map { componentlet =>
        s"""<tr>
           |  <td><code>${_escape(componentlet.name)}</code></td>
           |  <td>${_escape(componentlet.kind.getOrElse("componentlet"))}</td>
           |  <td>${_escape(componentlet.archiveScope.getOrElse(""))}</td>
           |  <td>${_escape(componentlet.implementationClass.getOrElse(""))}</td>
           |  <td>${_escape(componentlet.factoryObject.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }
      .mkString("\n")
    if (rows.isEmpty)
      ""
    else
      s"""<section>
         |  <h3>Componentlets</h3>
         |  <div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |    <thead><tr><th>Name</th><th>Kind</th><th>Archive scope</th><th>Implementation</th><th>Factory</th></tr></thead>
         |    <tbody>${rows}</tbody>
         |  </table></div>
         |</section>""".stripMargin
  }

  private def _manual_componentlet_section(
    component: Component
  ): String = {
    val body = _componentlet_table(component)
    if (body.isEmpty)
      ""
    else
      _manual_card("Componentlets", body, Some("componentlets"))
  }

  private def _component_admin_actions(
    formsPath: Option[String]
  ): String =
    formsPath.map { path =>
      val componentPath = path.stripPrefix("/form/")
      s"""<section class="admin-section">
         |  <h2 class="h5">Component Admin</h2>
         |  <p>Use Application Admin for ordinary operator workflows. This component page remains available for component-specific drill-down and technical reference.</p>
         |  ${_admin_action_row(Vector("Application admin" -> "/web/admin"), primary = false)}
         |  ${_component_admin_management_cards(componentPath, path)}
         |  <details class="mt-3">
         |    <summary>Technical details</summary>
         |    <div class="mt-3">
         |      ${_admin_action_row(Vector("Operation forms" -> path), primary = false)}
         |      ${_component_admin_management_links(path)}
         |    </div>
         |  </details>
         |</section>""".stripMargin
    }.getOrElse("")

  private def _component_admin_management_cards(
    componentPath: String,
    formsPath: String
  ): String =
    _admin_entry_cards(Vector(
      _admin_entry_card("Entities", "List, detail, create, and update entity records.", s"/web/${componentPath}/admin/entities"),
      _admin_entry_card("Data", "Manage concrete data collections and datastore records.", s"/web/${componentPath}/admin/data"),
      _admin_entry_card("Aggregates", "Inspect aggregate records and aggregate-level operations.", s"/web/${componentPath}/admin/aggregates"),
      _admin_entry_card("Views", "Browse read-only view projections and instance detail.", s"/web/${componentPath}/admin/views"),
      _admin_entry_card("Tags", "Browse TagSpaces and search tagged Entities.", "/web/admin/tags"),
      _admin_entry_card("Descriptor", "Inspect descriptor controls and admin-surface mappings.", s"/web/${componentPath}/admin/descriptor"),
      _admin_entry_card("Forms", "Open controlled operation forms outside the admin CRUD surfaces.", formsPath)
    ))

  private def _component_admin_management_links(
    formsPath: String
  ): String = {
    val componentPath = formsPath.stripPrefix("/form/")
    s"""<h3 class="h6">Managed Data</h3>
       |${_admin_link_list_group(Vector(
         "Entity CRUD" -> s"/web/${componentPath}/admin/entities",
         "Data CRUD" -> s"/web/${componentPath}/admin/data",
         "Aggregate CRUD" -> s"/web/${componentPath}/admin/aggregates",
         "View read" -> s"/web/${componentPath}/admin/views",
         "Tags" -> "/web/admin/tags"
       ))}""".stripMargin
  }

  private def _web_descriptor_summary(
    descriptor: WebDescriptor,
    descriptorPath: String
  ): String =
    s"""<div class="table-responsive"><table class="table table-sm">
       |  <tbody>
       |    <tr><th>Status</th><td>${if (descriptor.hasControls) "configured" else "default"}</td></tr>
       |    <tr><th>Auth mode</th><td>${_escape(descriptor.auth.mode)}</td></tr>
       |    <tr><th>Exposure entries</th><td>${descriptor.expose.size}</td></tr>
       |    <tr><th>Authorization entries</th><td>${descriptor.authorization.size}</td></tr>
       |    <tr><th>Form entries</th><td>${descriptor.form.size}</td></tr>
       |    <tr><th>App entries</th><td>${descriptor.apps.size}</td></tr>
       |    <tr><th>Route entries</th><td>${descriptor.routes.size}</td></tr>
       |    <tr><th>Admin entries</th><td>${descriptor.admin.size}</td></tr>
       |  </tbody>
       |</table></div>
       |${_admin_action_row(Vector("Completed descriptor JSON" -> descriptorPath), primary = false)}
       |${_web_descriptor_app_list(descriptor)}
       |${_web_descriptor_route_list(descriptor)}
       |${_web_descriptor_exposure_list(descriptor)}
       |${_web_descriptor_admin_list(descriptor)}""".stripMargin

  private def _web_descriptor_admin_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.admin.isEmpty) {
      "<p>No Management Console controls are configured.</p>"
    } else {
      val rows = descriptor.admin.toVector.sortBy(_._1).map {
        case (selector, admin) =>
          val fields = admin.fields.map(_.name).mkString(", ")
          val kind = _admin_surface_kind(selector).getOrElse("deferred")
          val destination = _web_descriptor_admin_surface_destination(selector, None)
          s"""<tr><td>${destination}</td><td>${_escape(kind)}</td><td>${_escape(admin.totalCount.name)}</td><td>${_escape(fields)}</td><td><code>${_escape(selector)}</code></td></tr>"""
      }.mkString("\n")
      s"""<h3>Management Console Controls</h3>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Destination</th><th>Type</th><th>Total count</th><th>Fields</th><th>Raw selector</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _web_descriptor_json(
    descriptor: WebDescriptor,
    completed: Boolean = false,
    componentSegment: Option[String] = None
  ): String =
    Json.obj(
      "status" -> Json.fromString(if (descriptor.hasControls) "configured" else "default"),
      "defaultView" -> Json.fromString(descriptor.defaultView),
      "auth" -> Json.obj(
        "mode" -> Json.fromString(descriptor.auth.mode)
      ),
      "assets" -> _web_descriptor_assets_json(descriptor.assets),
      "theme" -> _web_descriptor_theme_json(descriptor.theme),
      "assetComposition" -> (
        if (completed)
          _web_descriptor_asset_composition_json(descriptor, componentSegment)
        else
          Json.Null
      ),
      "expose" -> Json.fromFields(
        descriptor.expose.toVector.sortBy(_._1).map {
          case (selector, exposure) => selector -> Json.fromString(exposure.name)
        }
      ),
      "authorization" -> Json.fromFields(
        descriptor.authorization.toVector.sortBy(_._1).map {
          case (selector, authorization) =>
            selector -> Json.obj(
              "roles" -> Json.arr(authorization.roles.map(Json.fromString)*),
              "scopes" -> Json.arr(authorization.scopes.map(Json.fromString)*),
              "capabilities" -> Json.arr(authorization.capabilities.map(Json.fromString)*)
            )
        }
      ),
      "form" -> Json.fromFields(
        descriptor.form.toVector.sortBy(_._1).map {
          case (selector, form) =>
            selector -> Json.obj(
              "enabled" -> form.enabled.map(Json.fromBoolean).getOrElse(Json.Null),
              "assets" -> _web_descriptor_assets_json(form.assets)
            )
        }
      ),
      "pages" -> Json.fromFields(
        descriptor.pages.toVector.sortBy(_._1).map {
          case (selector, page) =>
            selector -> Json.obj(
              "title" -> page.title.map(Json.fromString).getOrElse(Json.Null),
              "heading" -> page.heading.map(Json.fromString).getOrElse(Json.Null),
              "subtitle" -> page.subtitle.map(Json.fromString).getOrElse(Json.Null),
              "submitLabel" -> page.submitLabel.map(Json.fromString).getOrElse(Json.Null),
              "fields" -> Json.arr(page.fields.map(Json.fromString)*),
              "controls" -> Json.fromFields(page.controls.toVector.sortBy(_._1).map {
                case (name, control) => name -> _web_descriptor_form_control_json(control)
              })
            )
        }
      ),
      "admin" -> Json.fromFields(
        descriptor.admin.toVector.sortBy(_._1).map {
          case (selector, admin) =>
            selector -> Json.obj(
              "totalCount" -> Json.fromString(admin.totalCount.name),
              "fields" -> Json.arr(admin.fields.map(field =>
                Json.obj(
                  "name" -> Json.fromString(field.name),
                  "type" -> field.control.controlType.map(Json.fromString).getOrElse(Json.Null),
                  "label" -> field.control.label.map(Json.fromString).getOrElse(Json.Null),
                  "required" -> field.control.required.map(Json.fromBoolean).getOrElse(Json.Null),
                  "hidden" -> Json.fromBoolean(field.control.hidden),
                  "readonly" -> Json.fromBoolean(field.control.readonly),
                  "placeholder" -> field.control.placeholder.map(Json.fromString).getOrElse(Json.Null),
                  "help" -> field.control.help.map(Json.fromString).getOrElse(Json.Null),
                  "defaultValue" -> field.control.defaultValue.map(Json.fromString).getOrElse(Json.Null),
                  "values" -> Json.arr(field.control.values.map(Json.fromString)*)
                )
              )*)
            )
        }
      ),
      "apps" -> Json.arr(
        descriptor.apps.map { app =>
          _web_descriptor_app_json(if (completed) app.completedFor(componentSegment) else app)
        }*
      ),
      "routes" -> Json.arr(
        descriptor.routes.map(_web_descriptor_route_json)*
      )
    ).spaces2

  private def _web_descriptor_form_control_json(
    control: WebDescriptor.FormControl
  ): Json =
    Json.obj(
      "type" -> control.controlType.map(Json.fromString).getOrElse(Json.Null),
      "label" -> control.label.map(Json.fromString).getOrElse(Json.Null),
      "required" -> control.required.map(Json.fromBoolean).getOrElse(Json.Null),
      "hidden" -> Json.fromBoolean(control.hidden),
      "readonly" -> Json.fromBoolean(control.readonly),
      "placeholder" -> control.placeholder.map(Json.fromString).getOrElse(Json.Null),
      "help" -> control.help.map(Json.fromString).getOrElse(Json.Null),
      "defaultValue" -> control.defaultValue.map(Json.fromString).getOrElse(Json.Null),
      "values" -> Json.arr(control.values.map(Json.fromString)*)
    )

  private def _web_descriptor_assets_json(
    assets: WebDescriptor.Assets
  ): Json =
    Json.obj(
      "autoComplete" -> Json.fromBoolean(assets.autoComplete),
      "css" -> Json.arr(assets.css.map(Json.fromString)*),
      "js" -> Json.arr(assets.js.map(Json.fromString)*)
    )

  private def _web_descriptor_theme_json(
    theme: WebDescriptor.Theme
  ): Json =
    Json.obj(
      "name" -> theme.name.map(Json.fromString).getOrElse(Json.Null),
      "css" -> Json.arr(theme.css.map(Json.fromString)*),
      "variables" -> Json.fromFields(
        theme.variables.toVector.sortBy(_._1).map {
          case (key, value) => key -> Json.fromString(value)
        }
      )
    )

  private def _web_descriptor_asset_composition_json(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Json = {
    val forms = _web_descriptor_form_asset_entries(descriptor, componentSegment)
    Json.obj(
      "global" -> _web_descriptor_assets_json(descriptor.assets),
      "apps" -> Json.fromFields(
        descriptor.apps.map(app => app.normalizedName -> _web_descriptor_assets_json(app.assets))
      ),
      "forms" -> Json.fromFields(
        forms.map {
          case (selector, _, _, _, form) => selector -> _web_descriptor_assets_json(form.assets)
        }
      ),
      "resolvedForms" -> Json.fromFields(
        forms.map {
          case (selector, component, service, operation, _) =>
            selector -> Json.obj(
              "component" -> Json.fromString(component),
              "service" -> Json.fromString(service),
              "operation" -> Json.fromString(operation),
              "componentFormIndex" -> _web_descriptor_assets_json(descriptor.formIndexAssets(component)),
              "operationInput" -> _web_descriptor_assets_json(descriptor.resultAssets(component, service, operation)),
              "operationResult" -> _web_descriptor_assets_json(descriptor.resultAssets(component, service, operation))
            )
        }
      )
    )
  }

  private def _web_descriptor_form_asset_entries(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Vector[(String, String, String, String, WebDescriptor.Form)] =
    descriptor.form.toVector.sortBy(_._1).flatMap {
      case (selector, form) =>
        selector.split("\\.", 3).toVector match {
          case Vector(component, service, operation) if componentSegment.forall(_ == component) =>
            Some((selector, component, service, operation, form))
          case _ =>
            None
        }
    }

  private def _web_descriptor_control_tables(
    descriptor: WebDescriptor,
    componentSegment: Option[String] = None
  ): String = {
    val body =
      s"""<p>Completed apps, routes, form access, authorization, and admin surfaces.</p>
         |${_admin_action_row(Vector("Completed JSON" -> "#completed-descriptor"), primary = false)}
         |${_web_descriptor_filter_control}
         |${_web_descriptor_apps_table(descriptor, componentSegment)}
         |${_web_descriptor_routes_table(descriptor, componentSegment)}
         |${_web_descriptor_form_controls_table(descriptor, componentSegment)}
         |${_web_descriptor_admin_surfaces_table(descriptor, componentSegment)}
         |${_web_descriptor_filter_script}""".stripMargin
    _admin_card("Descriptor Controls", body, Some("descriptor-controls"))
  }

  private def _web_descriptor_section_nav: String =
    _admin_card(
      "Descriptor Sections",
      """<nav class="nav nav-pills flex-column flex-sm-row gap-2 descriptor-section-nav">
        |  <a class="nav-link border" href="#descriptor-controls">Descriptor Controls</a>
        |  <a class="nav-link border" href="#asset-composition">Asset Composition</a>
        |  <a class="nav-link border" href="#completed-descriptor">Completed JSON</a>
        |  <a class="nav-link border" href="#configured-descriptor">Configured JSON</a>
        |</nav>""".stripMargin
    )

  private def _web_descriptor_json_panel(
    id: String,
    title: String,
    description: String,
    json: String
  ): String =
    _admin_card(
      title,
      s"""<details class="descriptor-json-details">
         |  <summary class="h5 mb-3">${_escape(title)}</summary>
         |  <p>${_escape(description)}</p>
         |  ${_raw_format_tabs(json, _json_to_yaml(json), "descriptor")}
         |</details>""".stripMargin,
      Some(id)
    )

  private def _web_descriptor_filter_control: String =
    """<div class="mb-3">
      |  <label class="form-label" for="web-descriptor-filter">Filter descriptor tables</label>
      |  <input class="form-control" id="web-descriptor-filter" type="search" placeholder="Filter apps, routes, forms, authorization, and admin surfaces" data-textus-descriptor-filter>
      |  <p class="alert alert-warning mt-2 mb-0 d-none" data-textus-descriptor-filter-empty>No descriptor rows match the filter.</p>
      |</div>""".stripMargin

  private def _web_descriptor_filter_script: String =
    """<script>
      |(() => {
      |  const input = document.querySelector("[data-textus-descriptor-filter]");
      |  if (!input) return;
      |  const rows = Array.from(document.querySelectorAll("[data-descriptor-row]"));
      |  const empty = document.querySelector("[data-textus-descriptor-filter-empty]");
      |  input.addEventListener("input", () => {
      |    const query = input.value.trim().toLowerCase();
      |    let visible = 0;
      |    rows.forEach((row) => {
      |      const hidden = query.length > 0 && !row.textContent.toLowerCase().includes(query);
      |      row.classList.toggle("d-none", hidden);
      |      if (!hidden) visible += 1;
      |    });
      |    if (empty) empty.classList.toggle("d-none", query.length === 0 || visible > 0);
      |  });
      |})();
      |</script>""".stripMargin

  private def _web_descriptor_apps_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val apps = _web_descriptor_scoped_apps(descriptor, componentSegment)
    val rows = apps.map { app =>
      val completed = app.completedFor(componentSegment)
      s"""<tr data-descriptor-row>
         |  <td><code>${_escape(completed.name)}</code></td>
         |  <td>${_web_descriptor_path_link(completed.effectivePath)}</td>
         |  <td><code>${_escape(completed.effectiveRoot)}</code></td>
         |  <td>${_web_descriptor_route_link(completed.effectiveRoute)}</td>
         |  <td>${_escape(completed.effectiveKind)}</td>
         |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No Web app descriptor entries are configured.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Apps <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Name</th><th>Path</th><th>Root</th><th>Route</th><th>Kind</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  private def _web_descriptor_scoped_apps(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Vector[WebDescriptor.App] =
    componentSegment match {
      case None => descriptor.apps
      case Some(component) =>
        descriptor.apps.filter { app =>
          NamingConventions.equivalentByNormalized(app.name, component) ||
            descriptor.routes.exists(route =>
              NamingConventions.equivalentByNormalized(route.target.component, component) &&
                NamingConventions.equivalentByNormalized(route.target.app, app.name)
            )
        }
    }

  private def _web_descriptor_routes_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val filtered = descriptor.routes.filter(route =>
      componentSegment.forall(component => NamingConventions.equivalentByNormalized(route.target.component, component))
    )
    val rows = filtered.map { route =>
      s"""<tr data-descriptor-row>
         |  <td>${_web_descriptor_path_link(route.path)}</td>
         |  <td>${_escape(route.kind.name)}</td>
         |  <td><code>${_escape(route.target.component)}</code></td>
         |  <td><code>${_escape(route.target.app)}</code></td>
         |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="4" class="text-secondary">No Web route descriptor entries are configured for this scope.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Routes <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Path</th><th>Kind</th><th>Target component</th><th>Target app</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  private def _web_descriptor_form_controls_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val selectors = (descriptor.expose.keySet ++ descriptor.form.keySet ++ descriptor.authorization.keySet)
      .toVector
      .filter(selector => _selector_matches_component(selector, componentSegment))
      .sortBy(identity)
    val rows = selectors.map { selector =>
      val exposure = descriptor.exposureOf(selector).name
      val form = descriptor.form.get(selector)
      val authorization = descriptor.authorization.get(selector)
      s"""<tr data-descriptor-row>
         |  <td>${_web_descriptor_form_link(selector)}</td>
         |  <td>${_escape(exposure)}</td>
         |  <td>${form.flatMap(_.enabled).map(_.toString).getOrElse("default")}</td>
         |  <td>${_escape(_web_descriptor_csv(authorization.map(_.roles).getOrElse(Vector.empty)))}</td>
         |  <td>${_escape(_web_descriptor_csv(authorization.map(_.scopes).getOrElse(Vector.empty)))}</td>
         |  <td>${_escape(_web_descriptor_csv(authorization.map(_.capabilities).getOrElse(Vector.empty)))}</td>
         |  <td>${authorization.exists(_.allowAnonymous)}</td>
         |  <td>${_escape(_web_descriptor_csv(authorization.map(_.operationModes.map(_.name)).getOrElse(Vector.empty)))}</td>
         |  <td>${_escape(_web_descriptor_csv(authorization.map(_.anonymousOperationModes.map(_.name)).getOrElse(Vector.empty)))}</td>
         |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="9" class="text-secondary">No form, exposure, or authorization entries are configured for this scope.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Form Access And Authorization <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Selector</th><th>Exposure</th><th>Form</th><th>Roles</th><th>Scopes</th><th>Capabilities</th><th>Anonymous</th><th>Operation modes</th><th>Anonymous modes</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  private def _web_descriptor_admin_surfaces_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val rows = descriptor.admin.toVector.sortBy(_._1).filter {
      case (selector, _) => _admin_selector_matches_component(selector, componentSegment)
    }.map {
      case (selector, admin) =>
        val fields = admin.fields.map { field =>
          val control = field.control.controlType.getOrElse("default")
          val required = field.control.required.map(value => s", required=${value}").getOrElse("")
          s"${field.name}:${control}${required}"
        }
        val kind = _admin_surface_kind(selector).getOrElse("deferred")
        val destination = _web_descriptor_admin_surface_destination(selector, componentSegment)
        val support =
          if (_web_descriptor_admin_surface_path(selector, componentSegment).isDefined) "implemented baseline"
          else "deferred"
        s"""<tr data-descriptor-row>
           |  <td>${destination}</td>
           |  <td>${_escape(kind)}</td>
           |  <td>${_escape(admin.totalCount.name)}</td>
           |  <td>${_escape(_web_descriptor_csv(fields))}</td>
           |  <td>${_escape(support)}</td>
           |  <td><code>${_escape(selector)}</code></td>
           |</tr>""".stripMargin
    }
    val body =
      if (rows.isEmpty)
        """<tr><td colspan="6" class="text-secondary">No Management Console surfaces are configured.</td></tr>"""
      else
        rows.mkString("\n")
    s"""<h3>Admin Surfaces <span class="badge text-bg-secondary">${rows.size}</span></h3>
       |<div class="table-responsive"><table class="table table-sm align-middle">
       |  <thead><tr><th>Destination</th><th>Type</th><th>Total count</th><th>Fields</th><th>Support</th><th>Raw selector</th></tr></thead>
       |  <tbody>${body}</tbody>
       |</table></div>""".stripMargin
  }

  private def _selector_matches_component(
    selector: String,
    componentSegment: Option[String]
  ): Boolean =
    componentSegment.forall { component =>
      selector.split("\\.", 2).headOption.exists(head =>
        NamingConventions.equivalentByNormalized(head, component)
      )
    }

  private def _admin_selector_matches_component(
    selector: String,
    componentSegment: Option[String]
  ): Boolean =
    componentSegment.forall { component =>
      selector.split("\\.", 2).toVector match {
        case Vector(surface, _) if _admin_surface_path(surface).isDefined =>
          true
        case Vector(head, _) =>
          NamingConventions.equivalentByNormalized(head, component)
        case _ =>
          true
      }
    }

  private def _web_descriptor_csv(
    values: Vector[String]
  ): String =
    if (values.isEmpty)
      "none"
    else
      values.mkString(", ")

  private def _web_descriptor_path_link(
    path: String
  ): String =
    if (path.startsWith("/web"))
      s"""<a href="${_escape(path)}"><code>${_escape(path)}</code></a>"""
    else
      s"""<code>${_escape(path)}</code>"""

  private def _web_descriptor_route_link(
    route: String
  ): String =
    if (route.startsWith("/web") && !route.contains("{"))
      s"""<a href="${_escape(route)}"><code>${_escape(route)}</code></a>"""
    else
      s"""<code>${_escape(route)}</code>"""

  private def _web_descriptor_form_link(
    selector: String
  ): String =
    selector.split("\\.", 3).toVector match {
      case Vector(component, service, operation) =>
        val path = s"/form/${component}/${service}/${operation}"
        s"""<a href="${_escape(path)}"><code>${_escape(selector)}</code></a>"""
      case _ =>
        s"""<code>${_escape(selector)}</code>"""
    }

  private def _web_descriptor_admin_surface_path(
    selector: String,
    componentSegment: Option[String]
  ): Option[String] = {
    def component_from_selector: Option[(String, String)] =
      selector.split("\\.", 3).toVector match {
        case Vector(component, surface, name) => Some(component -> s"${surface}.${name}")
        case _ => None
      }
    component_from_selector.flatMap {
      case (component, rest) => _admin_surface_relative_path(rest).map(path => s"/web/${component}/admin/${path}")
    }.orElse(
      componentSegment.flatMap(component =>
        _admin_surface_relative_path(selector).map(path => s"/web/${component}/admin/${path}")
      )
    )
  }

  private def _web_descriptor_admin_surface_destination(
    selector: String,
    componentSegment: Option[String]
  ): String = {
    val maybePath =
      _web_descriptor_admin_surface_path(selector, componentSegment)
    maybePath match {
      case Some(path) => s"""<a href="${_escape(path)}"><code>${_escape(path)}</code></a>"""
      case None => """<span class="text-secondary">Deferred or unsupported</span>"""
    }
  }

  private def _web_descriptor_admin_surface_link(
    selector: String,
    componentSegment: Option[String]
  ): String = {
    val maybePath =
      _web_descriptor_admin_surface_path(selector, componentSegment)
    maybePath match {
      case Some(path) => s"""<a href="${_escape(path)}"><code>${_escape(selector)}</code></a>"""
      case None => s"""<code>${_escape(selector)}</code>"""
    }
  }

  private def _admin_surface_kind(
    selector: String
  ): Option[String] =
    (selector.split("\\.", 3).toVector match {
      case Vector(surface, _) =>
        _admin_surface_path(surface)
      case Vector(_, surface, _) =>
        _admin_surface_path(surface)
      case _ =>
        None
    }).map {
      case "entities" => "entity"
      case "data" => "data"
      case "aggregates" => "aggregate"
      case "views" => "view"
      case other => other
    }

  private def _admin_surface_selector_count(
    descriptor: WebDescriptor,
    componentSegment: Option[String],
    kind: String
  ): Int =
    descriptor.admin.keys.count(selector =>
      _admin_selector_matches_component(selector, componentSegment) &&
      _admin_surface_kind(selector).contains(kind)
    )

  private def _pluralize(
    n: Int,
    singular: String,
    plural: String
  ): String =
    if (n == 1) s"1 ${singular}" else s"${n} ${plural}"

  private def _admin_entry_card(
    title: String,
    description: String,
    href: String,
    badge: Option[String] = None
  ): String =
    s"""<div class="col-12 col-md-6 col-xl-4">
       |  <article class="card h-100 shadow-sm admin-card">
       |    <div class="card-body">
       |      <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
       |        <h3 class="h5 card-title mb-0">${_escape(title)}</h3>
       |        ${badge.map(v => s"""<span class="badge text-bg-secondary">${_escape(v)}</span>""").getOrElse("")}
       |      </div>
       |      <p class="card-text text-body-secondary">${_escape(description)}</p>
       |      <a class="btn btn-primary btn-sm" href="${_escape(href)}">Open ${_escape(title)}</a>
       |    </div>
       |  </article>
       |</div>""".stripMargin

  private def _admin_entry_cards(
    cards: Vector[String]
  ): String =
    s"""<div class="row g-3">${cards.mkString("\n")}</div>"""

  private def _system_admin_component_inventory(
    components: Vector[Component]
  ): String = {
    val rows = components.sortBy(_.name).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      s"""<tr>
         |  <td>${_escape(component.name)}</td>
         |  <td><a href="/web/${componentPath}/admin">Component admin</a></td>
         |  <td><a href="/web/${componentPath}/admin/descriptor">Descriptor</a></td>
         |  <td><a href="/form/${componentPath}">Forms</a></td>
         |</tr>""".stripMargin
    }.mkString("\n")
    _admin_card(
      "Component Management Console",
      s"""<p>Use component admin pages for ordinary entity/data/view/aggregate work. System admin stays read-only.</p>
         |${_admin_table(
           Some("<tr><th>Component</th><th>Admin</th><th>Descriptor</th><th>Forms</th></tr>"),
           rows
         )}""".stripMargin
    )
  }

  private def _admin_surface_relative_path(
    selector: String
  ): Option[String] =
    selector.split("\\.", 2).toVector match {
      case Vector(surface, name) =>
        _admin_surface_path(surface).map(path => s"${path}/${NamingConventions.toNormalizedSegment(name)}")
      case _ =>
        None
    }

  private def _admin_surface_path(
    surface: String
  ): Option[String] =
    NamingConventions.toNormalizedSegment(surface) match {
      case "entity" | "entities" => Some("entities")
      case "data" => Some("data")
      case "aggregate" | "aggregates" => Some("aggregates")
      case "view" | "views" => Some("views")
      case _ => None
    }

  private def _web_descriptor_asset_composition_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String] = None
  ): String = {
    val forms = _web_descriptor_form_asset_entries(descriptor, componentSegment)
    val scopeRows =
      Vector(_web_descriptor_asset_scope_row("global", "web.assets", descriptor.assets)) ++
        Vector(_web_descriptor_theme_scope_row("global", "web.theme", descriptor.theme)) ++
        descriptor.apps.map(app =>
          _web_descriptor_asset_scope_row("app", app.normalizedName, app.assets)
        ) ++
        descriptor.apps.map(app =>
          _web_descriptor_theme_scope_row("app theme", app.normalizedName, app.theme)
        ) ++
        forms.map {
          case (selector, _, _, _, form) =>
            _web_descriptor_asset_scope_row("form", selector, form.assets)
        }
    val resolvedRows = forms.flatMap {
      case (selector, component, service, operation, _) =>
        Vector(
          _web_descriptor_resolved_asset_row(
            selector,
            "component form index",
            descriptor.formIndexAssets(component)
          ),
          _web_descriptor_resolved_asset_row(
            selector,
            "operation input",
            descriptor.resultAssets(component, service, operation)
          ),
          _web_descriptor_resolved_asset_row(
            selector,
            "operation result",
            descriptor.resultAssets(component, service, operation)
          )
        )
    }
    val scopeBody =
      if (scopeRows.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No descriptor asset scopes are configured.</td></tr>"""
      else
        scopeRows.mkString("\n")
    val resolvedBody =
      if (resolvedRows.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No form asset compositions are resolved for this scope.</td></tr>"""
      else
        resolvedRows.mkString("\n")
    s"""<article>
       |  <h2 id="asset-composition">Asset Composition <a class="btn btn-sm btn-outline-secondary ms-2" href="#completed-descriptor">Completed JSON</a></h2>
       |  <p>Configured descriptor asset scopes and completed Static Form page asset lists.</p>
       |  <h3>Configured Scopes</h3>
       |  <div class="table-responsive"><table class="table table-sm align-middle">
       |    <thead><tr><th>Scope</th><th>Selector</th><th>Auto complete</th><th>CSS</th><th>JS</th></tr></thead>
       |    <tbody>${scopeBody}</tbody>
       |  </table></div>
       |  <h3>Resolved Form Pages</h3>
       |  <div class="table-responsive"><table class="table table-sm align-middle">
       |    <thead><tr><th>Form</th><th>Page</th><th>Auto complete</th><th>CSS</th><th>JS</th></tr></thead>
       |    <tbody>${resolvedBody}</tbody>
       |  </table></div>
       |</article>""".stripMargin
  }

  private def _web_descriptor_asset_scope_row(
    scope: String,
    selector: String,
    assets: WebDescriptor.Assets
  ): String =
    s"""<tr>
       |  <td>${_escape(scope)}</td>
       |  <td><code>${_escape(selector)}</code></td>
       |  <td>${assets.autoComplete}</td>
       |  <td>${_web_descriptor_asset_url_list(assets.css)}</td>
       |  <td>${_web_descriptor_asset_url_list(assets.js)}</td>
       |</tr>""".stripMargin

  private def _web_descriptor_theme_scope_row(
    scope: String,
    selector: String,
    theme: WebDescriptor.Theme
  ): String =
    s"""<tr>
       |  <td>${_escape(scope)}</td>
       |  <td><code>${_escape(selector)}</code></td>
       |  <td>true</td>
       |  <td>${_web_descriptor_asset_url_list(theme.css)}${_web_descriptor_theme_variables(theme)}</td>
       |  <td><span class="text-secondary">none</span></td>
       |</tr>""".stripMargin

  private def _web_descriptor_theme_variables(
    theme: WebDescriptor.Theme
  ): String =
    if (theme.variables.isEmpty)
      ""
    else {
      val vars = theme.variables.toVector.sortBy(_._1).map {
        case (key, value) => s"${key}=${value}"
      }.mkString(", ")
      s"""<div><small class="text-secondary">variables: ${_escape(vars)}</small></div>"""
    }

  private def _web_descriptor_resolved_asset_row(
    selector: String,
    page: String,
    assets: WebDescriptor.Assets
  ): String =
    s"""<tr>
       |  <td><code>${_escape(selector)}</code></td>
       |  <td>${_escape(page)}</td>
       |  <td>${assets.autoComplete}</td>
       |  <td>${_web_descriptor_asset_url_list(assets.css)}</td>
       |  <td>${_web_descriptor_asset_url_list(assets.js)}</td>
       |</tr>""".stripMargin

  private def _web_descriptor_asset_url_list(
    urls: Vector[String]
  ): String =
    if (urls.isEmpty)
      """<span class="text-secondary">none</span>"""
    else
      urls.map(url => s"""<div><code>${_escape(url)}</code></div>""").mkString

  private def _web_descriptor_app_json(
    app: WebDescriptor.App
  ): Json = {
    val configured = Json.obj(
      "name" -> Json.fromString(app.name),
      "path" -> Json.fromString(app.path),
      "kind" -> Json.fromString(app.kind),
      "theme" -> _web_descriptor_theme_json(app.theme),
      "assets" -> _web_descriptor_assets_json(app.assets)
    )
    val optionalFields = Vector(
      app.root.map("root" -> Json.fromString(_)),
      app.route.map("route" -> Json.fromString(_))
    ).flatten
    if (optionalFields.isEmpty)
      configured
    else
      configured.deepMerge(Json.obj(optionalFields*))
  }

  private def _web_descriptor_app_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.apps.isEmpty) {
      "<p>Using built-in Web HTML app defaults.</p>"
    } else {
      val rows = descriptor.apps.map { app =>
        val completed = app.completed
        s"""<tr><td>${_escape(completed.name)}</td><td><code>${_escape(completed.effectivePath)}</code></td><td><code>${_escape(completed.effectiveRoot)}</code></td><td><code>${_escape(completed.effectiveRoute)}</code></td><td>${_escape(completed.effectiveKind)}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Apps</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Name</th><th>Path</th><th>Root</th><th>Route</th><th>Kind</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _web_descriptor_route_json(
    route: WebDescriptor.Route
  ): Json =
    Json.obj(
      "path" -> Json.fromString(route.path),
      "kind" -> Json.fromString(route.kind.name),
      "target" -> Json.obj(
        "component" -> Json.fromString(route.target.component),
        "app" -> Json.fromString(route.target.app)
      )
    )

  private def _web_descriptor_route_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.routes.isEmpty) {
      "<p>No subsystem Web route aliases are configured.</p>"
    } else {
      val rows = descriptor.routes.map { route =>
        s"""<tr><td><code>${_escape(route.path)}</code></td><td>${_escape(route.kind.name)}</td><td><code>${_escape(route.target.component)}</code></td><td><code>${_escape(route.target.app)}</code></td></tr>"""
      }.mkString("\n")
      s"""<h3>Routes</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Path</th><th>Kind</th><th>Target component</th><th>Target app</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _web_descriptor_exposure_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.expose.isEmpty) {
      "<p>No explicit Web exposure entries.</p>"
    } else {
      val rows = descriptor.expose.toVector.sortBy(_._1).map {
        case (selector, exposure) =>
          val auth = descriptor.authorization.get(selector).map(_ => "yes").getOrElse("no")
          val form = descriptor.form.get(selector).flatMap(_.enabled).map(_.toString).getOrElse("default")
          s"""<tr><td><code>${_escape(selector)}</code></td><td>${_escape(exposure.name)}</td><td>${auth}</td><td>${form}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Operation Exposure</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Selector</th><th>Exposure</th><th>Authorization</th><th>Form</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _performance_page(subsystem: Subsystem): String = {
    val htmlRequests = RuntimeDashboardMetrics.htmlSnapshot
    val actionCalls = RuntimeDashboardMetrics.actionCallSnapshot
    val authorizationDecisions = RuntimeDashboardMetrics.authorizationDecisionSnapshot
    val authorizationDiagnostics = RuntimeDashboardMetrics.authorizationDiagnosticCounts
    val authorizationDiagnosticRecords = RuntimeDashboardMetrics.authorizationDiagnosticRecords
    val dslChokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val validation = RuntimeDashboardMetrics.validationSnapshot
    val validationDiagnostics = RuntimeDashboardMetrics.validationDiagnosticCounts
    val validationDiagnosticRecords = RuntimeDashboardMetrics.validationDiagnosticRecords
    val operationRequestValidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot
    val operationRequestValidationDiagnostics = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts
    val operationRequestValidationDiagnosticRecords = RuntimeDashboardMetrics.operationRequestValidationDiagnosticRecords
    val blobOperations = RuntimeDashboardMetrics.blobOperationSnapshot
    val blobDiagnostics = RuntimeDashboardMetrics.blobDiagnosticCounts
    val blobDiagnosticRecords = RuntimeDashboardMetrics.blobDiagnosticRecords
    val jobs = _job_metrics(subsystem)
    def card(title: String, body: String, id: Option[String] = None): String =
      s"""<div class="col-12">${_admin_card(title, body, id)}</div>"""
    val navigationCard = card(
      "Navigation",
      """<nav class="nav nav-pills flex-column flex-sm-row gap-2">
        |  <a class="nav-link border" href="/web/system/dashboard">System dashboard</a>
        |  <a class="nav-link border" href="/web/system/admin">Admin configuration</a>
        |  <a class="nav-link border" href="/web/system/admin/observability">Observability drill-down</a>
        |  <a class="nav-link border" href="/web/system/admin/observability/metrics">Metrics</a>
        |  <a class="nav-link border" href="/web/system/document">Documents</a>
        |  <a class="nav-link border" href="/web/console">Console</a>
        |</nav>""".stripMargin,
      Some("performance-navigation")
    )
    val assemblyActions = _admin_action_row(Vector(
      "Warning detail" -> "/web/system/admin/assembly/warnings",
      "Assembly report" -> "/web/system/admin/assembly/report"
    ), primary = false)
    val assemblyCard = card(
      "Assembly warnings",
      s"""<p><span class="badge text-bg-secondary">${_assembly_warning_count(subsystem)}</span> warning(s).</p>
         |${assemblyActions}""".stripMargin
    )
    val recentErrorsCard = card(
      "Recent errors",
      s"""<p class="text-secondary">HTTP 4xx/5xx entries shown as Dashboard recent failures. They are diagnostics and do not change runtime Health.</p>
         |${_recent_errors_table(htmlRequests.recent)}""".stripMargin,
      Some("recent-errors")
    )
    val actionCallActions = _admin_action_row(Vector(
      "Execution history" -> "/form/admin/execution/history",
      "Latest calltree" -> "/form/admin/execution/calltree"
    ), primary = false)
    val actionCallCard = card(
      "ActionCall",
      s"""${_summary_table(actionCalls.summary)}
         |${actionCallActions}""".stripMargin
    )
    val authorizationCard = card(
      "Authorization",
      s"""${_summary_table(authorizationDecisions.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${_diagnostics_table(authorizationDiagnostics, authorizationDiagnosticRecords, Some("authorization"))}""".stripMargin,
      Some("authorization")
    )
    val validationCard = card(
      "Validation",
      s"""${_summary_table(validation.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${_diagnostics_table(validationDiagnostics, validationDiagnosticRecords, Some("validation"))}""".stripMargin
    )
    val operationRequestValidationCard = card(
      "Operation Request Validation",
      s"""${_summary_table(operationRequestValidation.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${_diagnostics_table(operationRequestValidationDiagnostics, operationRequestValidationDiagnosticRecords, Some("operation-request-validation"))}""".stripMargin
    )
    val blobOperationsCard = card(
      "Blob operations",
      s"""${_summary_table(blobOperations.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${_diagnostics_table(blobDiagnostics, blobDiagnosticRecords, Some("blob"))}""".stripMargin
    )
    val cards = Vector(
      navigationCard,
      assemblyCard,
      card("HTML request", _summary_table(htmlRequests.summary), Some("html-requests")),
      card("Latency", _latency_table(htmlRequests.recent)),
      card("Recent requests", _recent_requests_table(htmlRequests.recent)),
      recentErrorsCard,
      actionCallCard,
      authorizationCard,
      card("DSL Chokepoints", _summary_table(dslChokepoints.summary)),
      validationCard,
      operationRequestValidationCard,
      blobOperationsCard,
      card("Jobs", _jobs_table(jobs), Some("jobs"))
    ).mkString("\n")
    _simple_page(
      title = "System Performance",
      subtitle = "HTML request, ActionCall, authorization, and Jobs detail",
      body =
        s"""<section class="row g-3">
           |${cards}
           |</section>""".stripMargin
    )
  }

  private def _component_dashboard_state(component: Component): String =
    _dashboard_state_json(Vector(component), "component", component.name, component.artifactMetadata.map(_.version), _job_metrics(component), component.subsystem.map(_.name).getOrElse(component.name), component.subsystem.flatMap(_.version), component.subsystem.map(_assembly_warning_count).getOrElse(0))

  private def _manual_page(
    title: String,
    subtitle: String,
    component: Component,
    selector: Option[String],
    currentPath: String,
    childNames: Vector[String]
  ): String = {
    val help = HelpProjection.project(component, selector)
    val describe = DescribeProjection.project(component, selector)
    val schema = SchemaProjection.project(component, selector)
    val childLinks = _manual_child_links(currentPath, childNames)
    val componentletCard =
      if (selector.exists(NamingConventions.equivalentByNormalized(component.name, _)))
        _manual_componentlet_section(component)
      else
        ""
    val storageShapeCard =
      if (selector.exists(NamingConventions.equivalentByNormalized(component.name, _)))
        _manual_storage_shape_section(describe)
      else
        ""
    val authorizationPolicyCard =
      if (selector.exists(NamingConventions.equivalentByNormalized(component.name, _)))
        _manual_authorization_policy_section(describe)
      else
        ""
    val body =
      s"""${_manual_card("Specification navigation",
         s"""<p>This generated specification is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="${_escape(currentPath)}#help">Help</a>
            |  <a class="btn btn-outline-primary" href="${_escape(currentPath)}#describe">Describe</a>
            |  <a class="btn btn-outline-primary" href="${_escape(currentPath)}#schema">Schema</a>
            |  <a class="btn btn-outline-secondary" href="/web/system/document/specification/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${_manual_card("Children", childLinks)}
         |${componentletCard}
         |${storageShapeCard}
         |${authorizationPolicyCard}
         |${_manual_projection_card("Help", currentPath, help, Some("help"))}
         |${_manual_projection_card("Describe", currentPath, describe, Some("describe"))}
         |${_manual_projection_card("Schema", currentPath, schema, Some("schema"))}""".stripMargin
    _simple_page(title, subtitle, body)
  }

  private def _system_manual_page(
    subsystem: Subsystem,
    component: Component
  ): String = {
    val help = HelpProjection.project(component, None)
    val describe = DescribeProjection.project(component, None)
    val schema = SchemaProjection.project(component, None)
    val componentLinks = _manual_component_links(subsystem.components)
    val body =
      s"""${_manual_card("Specification navigation",
         s"""<p>This generated specification is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="/web/system/dashboard">System dashboard</a>
            |  <a class="btn btn-outline-primary" href="/web/system/admin">Admin configuration</a>
            |  <a class="btn btn-outline-primary" href="/web/system/performance">Performance details</a>
            |  <a class="btn btn-outline-secondary" href="/web/system/document/specification/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${_manual_card("Components", componentLinks)}
         |${_manual_card("Console handoff", """<p class="mb-0">Use <a href="/web/console">System Console</a> for controlled operation entry. Specification pages remain read-only and do not inline operation actions.</p>""")}
         |${_manual_authorization_policy_section(describe)}
         |${_manual_projection_card("Help", "/web/system/document/specification", help, Some("help"))}
         |${_manual_projection_card("Describe", "/web/system/document/specification", describe, Some("describe"))}
         |${_manual_projection_card("Schema", "/web/system/document/specification", schema, Some("schema"))}""".stripMargin
    _simple_page("System Specification", "Generated runtime specification", body)
  }

  private def _manual_component_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      _web_empty_state("No component reference entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.name)
        s"""<a class="btn btn-sm btn-outline-primary" href="/web/${_escape(segment)}/document/specification">${_escape(component.name)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  private def _manual_component_document_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      _web_empty_state("No component document entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.name)
        s"""<a class="btn btn-sm btn-outline-primary" href="/web/${_escape(segment)}/document">${_escape(component.name)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  private def _manual_child_links(
    currentPath: String,
    children: Vector[String]
  ): String =
    if (children.isEmpty)
      _web_empty_state("No child reference entries.")
    else
      children.map { child =>
        val segment = NamingConventions.toNormalizedSegment(child)
        s"""<a class="btn btn-sm btn-outline-primary" href="${_escape(currentPath + "/" + segment)}">${_escape(child)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  private def _manual_projection_card(
    title: String,
    currentPath: String,
    record: Record,
    id: Option[String] = None
  ): String =
    _manual_card(
      title,
      _manual_projection_body(title, currentPath, record),
      id
    )

  private def _manual_projection_body(
    title: String,
    currentPath: String,
    record: Record
  ): String =
    s"""${_manual_projection_summary(currentPath, record)}
       |${_manual_raw_details(title, record)}""".stripMargin

  private def _manual_projection_summary(
    currentPath: String,
    record: Record
  ): String = {
    val recordType = record.getString("type").getOrElse("")
    recordType match {
      case "operation" =>
        _manual_operation_summary(currentPath, record)
      case "service" =>
        _manual_service_summary(record)
      case "component" =>
        _manual_component_summary(record)
      case "subsystem" =>
        _manual_subsystem_summary(record)
      case "schema" =>
        _manual_schema_summary(record)
      case _ =>
        _manual_generic_summary(record)
    }
  }

  private def _manual_subsystem_summary(
    record: Record
  ): String = {
    val name = record.getString("name").getOrElse("subsystem")
    val summary = record.getString("summary").getOrElse("")
    val children = _manual_seq_values(record.asMap.get("children"))
    val detailComponents = _manual_record_values(record.asMap.get("details")).get("components").map(x => _manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val components = if (detailComponents.nonEmpty) detailComponents else children
    s"""<p class="mb-3">${_escape(if (summary.nonEmpty) summary else s"Subsystem: $name")}</p>
       |${_manual_kv_summary(Vector(
         "Name" -> name,
         "Component count" -> components.size.toString
       ))}
       |${_manual_badges("Components", components)}""".stripMargin
  }

  private def _manual_component_summary(
    record: Record
  ): String = {
    val services = _manual_record_seq(record.asMap.get("services")).flatMap(_.getString("name"))
    val componentlets = _manual_record_seq(record.asMap.get("componentlets")).flatMap(_.getString("name"))
    val aggregates = _manual_record_seq(record.asMap.get("aggregates")).flatMap(_.getString("name"))
    val views = _manual_record_seq(record.asMap.get("views")).flatMap(_.getString("name"))
    val relationships = _manual_record_seq(record.asMap.get("relationshipDefinitions"))
    val operationDefs = _manual_record_seq(record.asMap.get("operationDefinitions")).flatMap(_.getString("name"))
    val artifact = _manual_record_values(record.asMap.get("artifact"))
    s"""<p class="mb-3">${_escape(record.getString("summary").getOrElse(s"Component ${record.getString("name").getOrElse("")}"))}</p>
       |${_manual_kv_summary(Vector(
         "Name" -> record.getString("name").getOrElse(""),
         "Origin" -> record.getString("origin").getOrElse(""),
         "Artifact" -> artifact.get("name").flatMap(_manual_scalar).getOrElse(""),
         "Version" -> artifact.get("version").flatMap(_manual_scalar).getOrElse(""),
         "Service count" -> services.size.toString,
         "Componentlet count" -> componentlets.size.toString,
         "Aggregate count" -> aggregates.size.toString,
         "View count" -> views.size.toString,
         "Relationship count" -> relationships.size.toString,
         "Operation definition count" -> operationDefs.size.toString
       ))}
       |${_manual_badges("Services", services)}
       |${_manual_badges("Componentlets", componentlets)}
       |${_manual_relationship_table(relationships)}""".stripMargin
  }

  private def _manual_relationship_table(
    relationships: Vector[Record]
  ): String =
    if (relationships.isEmpty)
      ""
    else {
      val rows = relationships.map { r =>
        val name = _escape(r.getString("name").getOrElse(""))
        val kind = _escape(r.getString("kind").getOrElse(""))
        val source = _escape(r.getString("sourceEntityName").getOrElse(""))
        val target = _escape(r.getString("targetEntityName").getOrElse(""))
        val targetModel = _escape(r.getString("targetModelKind").getOrElse(""))
        val storage = _escape(r.getString("storageMode").getOrElse(""))
        val parent = _escape(r.getString("parentIdField").getOrElse(""))
        val value = _escape(r.getString("valueField").getOrElse(""))
        val sort = _escape(r.getString("sortOrderField").getOrElse(""))
        val domain = _escape(r.getString("associationDomain").getOrElse(""))
        val targetKind = _escape(r.getString("targetKind").getOrElse(""))
        val lifecycle = _escape(r.getString("lifecyclePolicy").getOrElse(""))
        s"<tr><td>$name</td><td>$kind</td><td>$source</td><td>$target</td><td>$targetModel</td><td>$storage</td><td>$parent</td><td>$value</td><td>$sort</td><td>$domain</td><td>$targetKind</td><td>$lifecycle</td></tr>"
      }.mkString("\n")
      s"""<section class="mt-3">
         |  <h3 class="h6">Relationships</h3>
         |  <div class="table-responsive">
         |    <table class="table table-sm align-middle">
         |      <thead><tr><th>Name</th><th>Kind</th><th>Source</th><th>Target</th><th>Target model</th><th>Storage</th><th>Parent field</th><th>Value field</th><th>Sort field</th><th>Domain</th><th>Target kind</th><th>Lifecycle</th></tr></thead>
         |      <tbody>${rows}</tbody>
         |    </table>
         |  </div>
         |</section>""".stripMargin
    }

  private def _manual_storage_shape_section(
    record: Record
  ): String = {
    val entities = _manual_record_seq(record.asMap.get("entityCollections"))
    if (entities.isEmpty)
      ""
    else {
      val summaryRows = entities.map(_manual_storage_shape_summary_row).mkString("\n")
      val fieldTables = entities.map(_manual_storage_shape_field_table).mkString("\n")
      _manual_card(
        "Storage shape",
        s"""<p class="mb-3">Effective SimpleEntity storage-shape metadata from the component projection.</p>
           |<div class="table-responsive">
           |  <table class="table table-sm table-hover align-middle manual-summary-table">
           |    <thead><tr><th>Entity</th><th>Collection</th><th>Memory policy</th><th>Working-set policy</th><th>Storage policy</th></tr></thead>
           |    <tbody>
           |      ${summaryRows}
           |    </tbody>
           |  </table>
           |</div>
           |${fieldTables}""".stripMargin,
        Some("storage-shape")
      )
    }
  }

  private def _manual_authorization_policy_section(
    record: Record
  ): String = {
    val policy = record.getAny("authorizationPolicies").collect { case r: Record => r }
    policy match {
      case Some(p) if AuthorizationPolicyProjection.hasVisiblePolicy(p) =>
        val roles = _manual_record_seq(p.asMap.get("roleDefinitions"))
        val resources = _manual_record_seq(p.asMap.get("resourcePolicies"))
        val blobRequirements = _manual_record_seq(p.asMap.get("blobOperationRequirements"))
        val roleTable =
          if (roles.isEmpty)
            _web_empty_state("No role definitions are configured.")
          else
            s"""<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-roles">
               |    <thead><tr><th>Role</th><th>Includes</th><th>Capabilities</th><th>Source</th></tr></thead>
               |    <tbody>${roles.map(_manual_authorization_role_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        val resourceTable =
          if (resources.isEmpty)
            _web_empty_state("No resource policies are configured.")
          else
            s"""<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-resources">
               |    <thead><tr><th>Family</th><th>Resource</th><th>Action</th><th>Capabilities</th><th>Permission</th><th>Source</th></tr></thead>
               |    <tbody>${resources.map(_manual_authorization_resource_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        val blobRequirementTable =
          if (blobRequirements.isEmpty)
            ""
          else
            s"""<h3 class="h6 mt-3">Blob operation requirements</h3>
               |<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-blob-requirements">
               |    <thead><tr><th>Operation</th><th>Family</th><th>Resource</th><th>Action</th><th>Requirement</th></tr></thead>
               |    <tbody>${blobRequirements.map(_manual_authorization_blob_requirement_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        _manual_card(
          "Authorization policies",
          s"""<p class="mb-3">Read-only view of descriptor-backed authorization policy and Blob operation requirements.</p>
             |<h3 class="h6">Resource policies</h3>
             |${resourceTable}
             |<h3 class="h6 mt-3">Role definitions</h3>
             |${roleTable}
             |${blobRequirementTable}
             |${_manual_raw_details("Authorization policies", p)}""".stripMargin,
          Some("authorization-policies")
        )
      case _ =>
        ""
    }
  }

  private def _manual_authorization_role_row(
    record: Record
  ): String =
    s"""<tr>
       |  <td><code>${_escape(record.getString("name").getOrElse(""))}</code></td>
       |  <td>${_escape(_manual_seq_values(record.asMap.get("includes")).mkString(", "))}</td>
       |  <td>${_escape(_manual_seq_values(record.asMap.get("capabilities")).mkString(", "))}</td>
       |  <td>${_escape(record.getString("source").getOrElse(""))}</td>
       |</tr>""".stripMargin

  private def _manual_authorization_resource_row(
    record: Record
  ): String =
    s"""<tr>
       |  <td>${_escape(record.getString("family").getOrElse(""))}</td>
       |  <td><code>${_escape(record.getString("resource").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("action").getOrElse(""))}</code></td>
       |  <td>${_escape(_manual_seq_values(record.asMap.get("requiredCapabilities")).mkString(", "))}</td>
       |  <td>${_escape(record.getString("permissionOverride").filter(_.nonEmpty).getOrElse("-"))}</td>
       |  <td>${_escape(record.getString("source").getOrElse(""))}</td>
       |</tr>""".stripMargin

  private def _manual_authorization_blob_requirement_row(
    record: Record
  ): String =
    s"""<tr>
       |  <td><code>${_escape(record.getString("operation").getOrElse(""))}</code></td>
       |  <td>${_escape(record.getString("family").getOrElse(""))}</td>
       |  <td><code>${_escape(record.getString("resource").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("action").getOrElse(""))}</code></td>
       |  <td>${_escape(record.getString("requirement").getOrElse(""))}</td>
       |</tr>""".stripMargin

  private def _manual_storage_shape_summary_row(
    record: Record
  ): String = {
    val shape = _manual_record_values(record.asMap.get("storageShape"))
    val policy = shape.get("policy").flatMap(_manual_scalar).getOrElse("")
    s"""<tr>
       |  <td><code>${_escape(record.getString("entityName").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("collectionId").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("memoryPolicy").getOrElse(""))}</code></td>
       |  <td><code>${_escape(record.getString("workingSetPolicy").getOrElse("-"))}</code></td>
       |  <td><code>${_escape(policy)}</code></td>
       |</tr>""".stripMargin
  }

  private def _manual_storage_shape_field_table(
    record: Record
  ): String = {
    val entityName = record.getString("entityName").getOrElse("")
    val shape = _manual_record_values(record.asMap.get("storageShape"))
    val fields = _manual_record_seq(shape.get("fields"))
    if (fields.isEmpty)
      s"""<section class="mt-3">
         |  <h3 class="h6">${_escape(entityName)} fields</h3>
         |  ${_web_empty_state("No storage-shape field metadata.")}
         |</section>""".stripMargin
    else {
      val rows = fields.map { field =>
        s"""<tr>
           |  <td><code>${_escape(field.getString("logicalName").getOrElse(""))}</code></td>
           |  <td><code>${_escape(field.getString("storageName").getOrElse(""))}</code></td>
           |  <td>${_escape(field.getString("classification").getOrElse(""))}</td>
           |  <td>${_escape(field.getString("storageKind").getOrElse(""))}</td>
           |  <td>${_escape(field.getString("dataType").getOrElse(""))}</td>
           |  <td>${_escape(field.getString("source").getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<section class="mt-3">
         |  <h3 class="h6">${_escape(entityName)} fields</h3>
         |  <div class="table-responsive">
         |    <table class="table table-sm table-hover align-middle manual-storage-shape-fields">
         |      <thead><tr><th>Logical name</th><th>Storage name</th><th>Classification</th><th>Storage kind</th><th>Data type</th><th>Source</th></tr></thead>
         |      <tbody>
         |        ${rows}
         |      </tbody>
         |    </table>
         |  </div>
         |</section>""".stripMargin
    }
  }

  private def _manual_service_summary(
    record: Record
  ): String = {
    val children = _manual_seq_values(record.asMap.get("children"))
    val operations = _manual_record_seq(record.asMap.get("operations")).flatMap(_.getString("name"))
    val items = if (operations.nonEmpty) operations else children
    s"""<p class="mb-3">${_escape(record.getString("summary").getOrElse("Service reference"))}</p>
       |${_manual_kv_summary(Vector(
         "Service" -> record.getString("name").getOrElse(""),
         "Operation count" -> items.size.toString
       ))}
       |${_manual_badges("Operations", items)}""".stripMargin
  }

  private def _manual_operation_summary(
    currentPath: String,
    record: Record
  ): String = {
    val qualifiedName = record.getString("name").getOrElse("")
    val qualifiedSegments = qualifiedName.split("\\.").toVector.filter(_.nonEmpty)
    val component = record.getString("component").orElse(qualifiedSegments.headOption).getOrElse("")
    val service = record.getString("service").orElse(qualifiedSegments.lift(1)).getOrElse("")
    val operation = qualifiedSegments.lift(2).orElse(Option(qualifiedName).filter(_.nonEmpty)).getOrElse("")
    val selector = _manual_selector_map(record)
    val details = _manual_record_values(record.asMap.get("details"))
    val arguments = details.get("arguments").map(x => _manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val returns = details.get("returns").map(x => _manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val description = details.get("description").map(x => _manual_seq_values(Some(x))).getOrElse(Vector.empty).mkString(" ")
    val selectorText = selector.get("canonical").flatMap(_manual_scalar).orElse(record.getString("selector")).getOrElse(qualifiedName)
    val restPath = selector.get("rest").flatMap(_manual_scalar).map(_manual_canonical_rest_path).getOrElse(s"/rest/v1/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}")
    val formPath = s"/form/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    val formApiPath = s"/form-api/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    val describeArgumentRows = _manual_record_seq(record.asMap.get("arguments")).map(_manual_parameter_row)
    val parameterRows =
      if (describeArgumentRows.nonEmpty)
        describeArgumentRows
      else
        _manual_schema_parameters_from_help(details, component, service, operation)
    s"""<p class="mb-3">${_escape(record.getString("summary").getOrElse("Operation reference"))}</p>
       |${if (description.nonEmpty) s"""<p class="mb-3">${_escape(description)}</p>""" else ""}
       |${_manual_kv_summary(Vector(
         "Selector" -> selectorText,
         "Component" -> component,
         "Service" -> service,
         "Operation" -> operation,
         "Arguments" -> (if (parameterRows.nonEmpty) parameterRows.size else arguments.size).toString,
         "Returns" -> returns.mkString(", ")
       ))}
       |${_manual_link_group(Vector(
         "Web specification" -> currentPath,
         "REST" -> restPath,
         "Form" -> formPath,
         "Form API" -> formApiPath,
       "OpenAPI JSON" -> "/web/system/document/specification/openapi.json"
      ))}
       |${_manual_child_entity_binding_summary(record)}
       |${_manual_association_binding_summary(record)}
       |${_manual_image_binding_summary(record)}
       |${_manual_parameter_table(parameterRows)}
       |${_manual_response_summary(returns)}""".stripMargin
  }

  private def _manual_child_entity_binding_summary(
    record: Record
  ): String = {
    val bindings = _manual_record_seq(record.asMap.get("childEntityBindings"))
    if (bindings.isEmpty)
      ""
    else {
      val rows = bindings.flatMap { binding =>
        val name = binding.getString("name").getOrElse("")
        val entity = binding.getString("entityName").getOrElse("")
        val input = binding.getString("inputParameter").getOrElse("")
        val parent = binding.getString("parentIdField").getOrElse("")
        val relationship = binding.getString("relationshipName").getOrElse("")
        val source = binding.getString("sourceEntityIdMode").getOrElse("")
        val policy = binding.getString("failurePolicy").getOrElse("")
        val title = Vector(name, entity).filter(_.nonEmpty).mkString(" / ")
        Vector(
          s"${title} relationship" -> relationship,
          s"${title} input" -> input,
          s"${title} parent field" -> parent,
          s"${title} source id mode" -> source,
          s"${title} failure policy" -> policy
        )
      }.filter { case (_, value) => value.nonEmpty }
      s"""<section class="mt-3">
         |  <h3 class="h6">Child Entity Binding</h3>
         |  ${_manual_kv_summary(rows)}
         |</section>""".stripMargin
    }
  }

  private def _manual_association_binding_summary(
    record: Record
  ): String = {
    val binding = _manual_record_values(record.asMap.get("associationBinding"))
    if (binding.isEmpty)
      ""
    else {
      val behavior = Vector(
        "create" -> binding.get("createsAssociation").flatMap(_manual_scalar).contains("true"),
        "detach" -> binding.get("detachesAssociation").flatMap(_manual_scalar).contains("true")
      ).collect { case (label, true) => label }
      val rows = Vector(
        "Domain" -> binding.get("domain").flatMap(_manual_scalar).getOrElse(""),
        "Target kind" -> binding.get("targetKind").flatMap(_manual_scalar).getOrElse(""),
        "Behavior" -> behavior.mkString(", "),
        "Source id mode" -> binding.get("sourceEntityIdMode").flatMap(_manual_scalar).getOrElse(""),
        "Parameters" -> _manual_seq_values(binding.get("parameters")).mkString(", "),
        "Source id parameters" -> _manual_seq_values(binding.get("sourceEntityIdParameters")).mkString(", "),
        "Roles" -> _manual_seq_values(binding.get("roles")).mkString(", "),
        "Target id parameters" -> _manual_seq_values(binding.get("targetIdParameters")).mkString(", "),
        "Sort order parameters" -> _manual_seq_values(binding.get("sortOrderParameters")).mkString(", ")
      ).filter { case (_, value) => value.nonEmpty }
      s"""<section class="mt-3">
         |  <h3 class="h6">Association Binding</h3>
         |  ${_manual_kv_summary(rows)}
         |</section>""".stripMargin
    }
  }

  private def _manual_image_binding_summary(
    record: Record
  ): String = {
    val binding = _manual_record_values(record.asMap.get("imageBinding"))
    if (binding.isEmpty)
      ""
    else {
      val modes = Vector(
        "upload" -> binding.get("acceptsUpload").flatMap(_manual_scalar).contains("true"),
        "existing Blob id" -> binding.get("acceptsExistingBlobId").flatMap(_manual_scalar).contains("true"),
        "archive Blob id" -> binding.get("acceptsArchiveBlobId").flatMap(_manual_scalar).contains("true")
      ).collect { case (label, true) => label }
      val behavior = Vector(
        "attach" -> binding.get("createsAttachment").flatMap(_manual_scalar).contains("true"),
        "detach" -> binding.get("detachesAttachment").flatMap(_manual_scalar).contains("true")
      ).collect { case (label, true) => label }
      val rows = Vector(
        "Media kind" -> binding.get("mediaKind").flatMap(_manual_scalar).getOrElse("image"),
        "Accepted input" -> modes.mkString(", "),
        "Behavior" -> behavior.mkString(", "),
        "Roles" -> _manual_seq_values(binding.get("roles")).mkString(", "),
        "Parameters" -> _manual_seq_values(binding.get("parameters")).mkString(", ")
      ).filter { case (_, value) => value.nonEmpty }
      s"""<section class="mt-3">
         |  <h3 class="h6">Image Binding</h3>
         |  ${_manual_kv_summary(rows)}
         |</section>""".stripMargin
    }
  }

  private def _manual_canonical_rest_path(
    path: String
  ): String =
    if (path == null || path.isEmpty)
      ""
    else if (path.startsWith("/rest/v"))
      path
    else if (path.startsWith("/"))
      s"/rest/v1${path}"
    else
      s"/rest/v1/${path}"

  private def _manual_schema_summary(
    record: Record
  ): String = {
    val targetType = record.getString("targetType").getOrElse(record.getString("type").getOrElse(""))
    targetType match {
      case "operation" =>
        val request = _manual_record_values(record.asMap.get("request"))
        val response = _manual_record_values(record.asMap.get("response"))
        val params = _manual_record_seq(request.get("parameters")).map(_manual_parameter_row)
        val result = response.get("result").flatMap(_manual_scalar)
        s"""${_manual_kv_summary(Vector(
           "Schema target" -> record.getString("name").getOrElse(""),
           "Parameter count" -> params.size.toString,
           "Result" -> result.getOrElse("")
         ))}
         |${_manual_parameter_table(params)}
         |${_manual_response_summary(result.toVector)}""".stripMargin
      case "service" =>
        val ops = _manual_record_seq(record.asMap.get("operations")).flatMap(_.getString("name"))
        s"""${_manual_kv_summary(Vector(
           "Schema target" -> record.getString("name").getOrElse(""),
           "Operation count" -> ops.size.toString
         ))}
         |${_manual_badges("Operations", ops)}""".stripMargin
      case "component" =>
        val services = _manual_record_seq(record.asMap.get("services")).flatMap(_.getString("name"))
        val aggregates = _manual_record_seq(record.asMap.get("aggregateCollections")).flatMap(_.getString("name"))
        val views = _manual_record_seq(record.asMap.get("viewCollections")).flatMap(_.getString("name"))
        s"""${_manual_kv_summary(Vector(
           "Schema target" -> record.getString("name").getOrElse(""),
           "Service count" -> services.size.toString,
           "Aggregate count" -> aggregates.size.toString,
           "View count" -> views.size.toString
         ))}
         |${_manual_badges("Services", services)}""".stripMargin
      case _ =>
        _manual_generic_summary(record)
    }
  }

  private def _manual_generic_summary(
    record: Record
  ): String =
    _manual_kv_summary(Vector(
      "Type" -> record.getString("type").getOrElse(""),
      "Name" -> record.getString("name").getOrElse(""),
      "Summary" -> record.getString("summary").getOrElse("")
    ))

  private def _manual_response_summary(
    returns: Vector[String]
  ): String =
    if (returns.isEmpty)
      ""
    else
      s"""<section class="mt-3">
         |  <h3 class="h6">Response</h3>
         |  <p class="mb-0">${_escape(returns.mkString(", "))}</p>
         |</section>""".stripMargin

  private def _manual_parameter_table(
    rows: Vector[Vector[String]]
  ): String =
    if (rows.isEmpty)
      _web_empty_state("No parameter details.")
    else {
      val body = rows.map { row =>
        s"""<tr>${row.map(x => s"<td>${_escape(x)}</td>").mkString}</tr>"""
      }.mkString("\n")
      s"""<section class="mt-3">
         |  <h3 class="h6">Parameters</h3>
         |  <div class="table-responsive">
         |    <table class="table table-sm table-hover align-middle">
         |      <thead><tr><th>Name</th><th>Kind</th><th>Type</th><th>Multiplicity</th><th>Help</th></tr></thead>
         |      <tbody>
         |        ${body}
         |      </tbody>
         |    </table>
         |  </div>
         |</section>""".stripMargin
    }

  private def _manual_schema_parameters_from_help(
    details: Map[String, Any],
    component: String,
    service: String,
    operation: String
  ): Vector[Vector[String]] =
    details.get("arguments").map(x => _manual_seq_values(Some(x))).getOrElse(Vector.empty).map { name =>
      Vector(name, "argument", "", "", "")
    }

  private def _manual_parameter_row(
    record: Record
  ): Vector[String] =
    Vector(
      record.getString("name").getOrElse(""),
      record.getString("kind").getOrElse(""),
      record.getString("type").getOrElse(record.getString("datatype").getOrElse("")),
      record.getString("multiplicity").getOrElse(""),
      record.getString("help").orElse(record.getString("placeholder")).orElse(record.getString("default")).getOrElse("")
    )

  private def _manual_selector_map(
    record: Record
  ): Map[String, Any] =
    _manual_record_values(record.asMap.get("selector"))

  private def _manual_kv_summary(
    items: Vector[(String, String)]
  ): String = {
    val effective = items.filter { case (_, v) => v != null && v.nonEmpty }
    if (effective.isEmpty)
      _web_empty_state("No summary details.")
    else {
      val rows = effective.map { case (key, value) =>
        s"""<tr><th>${_escape(key)}</th><td><code>${_escape(value)}</code></td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive">
         |  <table class="table table-sm table-hover align-middle manual-summary-table mb-0">
         |    <tbody>
         |      ${rows}
         |    </tbody>
         |  </table>
         |</div>""".stripMargin
    }
  }

  private def _manual_link_group(
    links: Vector[(String, String)]
  ): String = {
    val effective = links.filter { case (_, href) => href != null && href.nonEmpty }
    if (effective.isEmpty)
      ""
    else
      effective.map { case (label, href) =>
        s"""<a class="btn btn-sm btn-outline-secondary" href="${_escape(href)}">${_escape(label)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2 mt-3">""", "\n", "</div>")
  }

  private def _manual_badges(
    title: String,
    items: Vector[String]
  ): String =
    if (items.isEmpty)
      ""
    else
      s"""<section class="mt-3">
         |  <h3 class="h6">${_escape(title)}</h3>
         |  <div class="d-flex flex-wrap gap-2">${items.map(x => s"""<span class="badge text-bg-light border">${_escape(x)}</span>""").mkString("\n")}</div>
         |</section>""".stripMargin

  private def _manual_raw_details(
    title: String,
    record: Record
  ): String =
    val rendered = _manual_raw_json(record).map(_.spaces2).getOrElse(_manual_raw_text(record))
    val yaml = _manual_raw_json(record).map(_json_to_yaml).getOrElse(_manual_raw_text(record))
    s"""<details class="mt-3 manual-raw-details">
       |  <summary>Raw ${_escape(title)}</summary>
       |  ${_raw_format_tabs(rendered, yaml, "manual")}
       |</details>""".stripMargin

  private def _manual_raw_json(value: Any): Option[Json] =
    value match {
      case Some(x) => _manual_raw_json(x)
      case None => Some(Json.Null)
      case null => Some(Json.Null)
      case r: Record =>
        Some(Json.fromJsonObject(JsonObject.fromIterable(
          r.asMap.toVector.sortBy(_._1.toString).flatMap { case (k, v) =>
            _manual_raw_json(v).map(k -> _)
          }
        )))
      case m: Map[?, ?] =>
        Some(Json.fromJsonObject(JsonObject.fromIterable(
          m.toVector.sortBy(_._1.toString).flatMap { case (k, v) =>
            _manual_raw_json(v).map(k.toString -> _)
          }
        )))
      case xs: Seq[?] =>
        Some(Json.fromValues(xs.toVector.flatMap(_manual_raw_json)))
      case s: String => Some(Json.fromString(s))
      case b: Boolean => Some(Json.fromBoolean(b))
      case i: Int => Some(Json.fromInt(i))
      case l: Long => Some(Json.fromLong(l))
      case d: Double if !d.isNaN && !d.isInfinity => Some(Json.fromDoubleOrNull(d))
      case f: Float if !f.isNaN && !f.isInfinity => Some(Json.fromFloatOrNull(f))
      case n: Number => Some(Json.fromString(n.toString))
      case x => Some(Json.fromString(x.toString))
    }

  private def _manual_raw_text(value: Any, indent: Int = 0): String = {
    val pad = "  " * indent
    value match {
      case Some(x) => _manual_raw_text(x, indent)
      case None => "null"
      case null => "null"
      case r: Record =>
        r.asMap.toVector.sortBy(_._1.toString).map { case (k, v) =>
          s"${pad}${k}: ${_manual_raw_text(v, indent + 1).stripPrefix("  " * (indent + 1))}"
        }.mkString("\n")
      case m: Map[?, ?] =>
        m.toVector.sortBy(_._1.toString).map { case (k, v) =>
          s"${pad}${k}: ${_manual_raw_text(v, indent + 1).stripPrefix("  " * (indent + 1))}"
        }.mkString("\n")
      case xs: Seq[?] =>
        xs.toVector.map { x =>
          val rendered = _manual_raw_text(x, indent + 1)
          if (rendered.contains("\n")) s"${pad}-\n$rendered" else s"${pad}- $rendered"
        }.mkString("\n")
      case x => x.toString
    }
  }

  private def _manual_record_values(
    value: Option[Any]
  ): Map[String, Any] =
    value match {
      case Some(Some(x)) => _manual_record_values(Some(x))
      case Some(r: Record) => r.asMap
      case Some(m: Map[?, ?]) => m.toVector.collect { case (k: String, v) => k -> v }.toMap
      case _ => Map.empty
    }

  private def _manual_record_seq(
    value: Option[Any]
  ): Vector[Record] =
    value match {
      case Some(Some(x)) => _manual_record_seq(Some(x))
      case Some(xs: Seq[?]) => xs.collect { case r: Record => r }.toVector
      case _ => Vector.empty
    }

  private def _manual_seq_values(
    value: Option[Any]
  ): Vector[String] =
    value match {
      case Some(Some(x)) => _manual_seq_values(Some(x))
      case Some(xs: Seq[?]) => xs.toVector.flatMap(_manual_scalar)
      case Some(x) => _manual_scalar(x).toVector
      case None => Vector.empty
    }

  private def _manual_scalar(
    value: Any
  ): Option[String] =
    value match {
      case Some(x) => _manual_scalar(x)
      case None => None
      case null => None
      case s: String if s.nonEmpty => Some(s)
      case b: Boolean => Some(b.toString)
      case n: Number => Some(n.toString)
      case _ => None
    }

  private def _raw_format_tabs(
    json: String,
    yaml: String,
    prefix: String
  ): String = {
    val token = s"${prefix}-${math.abs((json + yaml).hashCode)}"
    s"""<div class="${_escape(prefix)}-raw-tabs mt-3">
       |  <ul class="nav nav-tabs" id="${token}-tablist" role="tablist">
       |    <li class="nav-item" role="presentation">
       |      <button class="nav-link active" id="${token}-json-tab" data-bs-toggle="tab" data-bs-target="#${token}-json-pane" type="button" role="tab" aria-controls="${token}-json-pane" aria-selected="true">JSON</button>
       |    </li>
       |    <li class="nav-item" role="presentation">
       |      <button class="nav-link" id="${token}-yaml-tab" data-bs-toggle="tab" data-bs-target="#${token}-yaml-pane" type="button" role="tab" aria-controls="${token}-yaml-pane" aria-selected="false">YAML</button>
       |    </li>
       |  </ul>
       |  <div class="tab-content border border-top-0 rounded-bottom">
       |    <div class="tab-pane fade show active" id="${token}-json-pane" role="tabpanel" aria-labelledby="${token}-json-tab" tabindex="0">
       |      <pre class="bg-light border-0 rounded-0 rounded-bottom p-3 mb-0"><code>${_escape(json)}</code></pre>
       |    </div>
       |    <div class="tab-pane fade" id="${token}-yaml-pane" role="tabpanel" aria-labelledby="${token}-yaml-tab" tabindex="0">
       |      <pre class="bg-light border-0 rounded-0 rounded-bottom p-3 mb-0"><code>${_escape(yaml)}</code></pre>
       |    </div>
       |  </div>
       |</div>""".stripMargin
  }

  private def _json_to_yaml(jsonText: String): String =
    io.circe.parser.parse(jsonText).toOption.map(_json_to_yaml).getOrElse(jsonText)

  private def _json_to_yaml(json: Json): String = {
    def go(value: Json, indent: Int): String = {
      val pad = "  " * indent
      value.fold(
        jsonNull = "null",
        jsonBoolean = _.toString,
        jsonNumber = _.toString,
        jsonString = s => _yaml_quote(s),
        jsonArray = xs =>
          if (xs.isEmpty) "[]"
          else xs.toVector.map { item =>
            item.fold(
              jsonNull = s"${pad}- null",
              jsonBoolean = b => s"${pad}- ${b}",
              jsonNumber = n => s"${pad}- ${n}",
              jsonString = s => s"${pad}- ${_yaml_quote(s)}",
              jsonArray = _ => s"${pad}-\n${go(item, indent + 1)}",
              jsonObject = _ => s"${pad}-\n${go(item, indent + 1)}"
            )
          }.mkString("\n"),
        jsonObject = obj =>
          if (obj.isEmpty) "{}"
          else obj.toVector.map { case (key, item) =>
            item.fold(
              jsonNull = s"${pad}${key}: null",
              jsonBoolean = b => s"${pad}${key}: ${b}",
              jsonNumber = n => s"${pad}${key}: ${n}",
              jsonString = s => s"${pad}${key}: ${_yaml_quote(s)}",
              jsonArray = _ => s"${pad}${key}:\n${go(item, indent + 1)}",
              jsonObject = _ => s"${pad}${key}:\n${go(item, indent + 1)}"
            )
          }.mkString("\n")
      )
    }
    go(json, 0)
  }

  private def _yaml_quote(s: String): String =
    "\"" + s.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c => c.toString
    } + "\""

  private def _manual_card(
    title: String,
    body: String,
    id: Option[String] = None
  ): String = {
    val idText = id.map(x => s""" id="${_escape(x)}"""").getOrElse("")
    s"""<article${idText} class="card manual-card shadow-sm">
       |  <div class="card-body">
       |    <h2 class="card-title h5">${_escape(title)}</h2>
       |    ${body}
       |  </div>
       |</article>""".stripMargin
  }

  private def _simple_page(
    title: String,
    subtitle: String,
    body: String,
    assetCompletion: StaticFormAppLayout.AssetCompletionOptions =
      StaticFormAppLayout.AssetCompletionOptions()
  ): String =
    StaticFormAppLayout.completeDeclaredAssets(
      StaticFormAppLayout.bootstrapPage(StaticFormAppLayout.Options(
        title = title,
        subtitle = subtitle,
        body = body,
        extraHead =
          """|    .admin-card, .manual-card, .textus-card { margin-bottom: 1rem; }
             |    .admin-section { margin-bottom: 1rem; }
             |    .admin-section > h2 { margin-bottom: .75rem; }
             |    .admin-action-row { margin-top: 1rem; }
             |    .admin-empty-state { color: var(--bs-secondary-color); }
             |""".stripMargin
      )),
      assetCompletion
    )

  private def _property_rows(properties: Map[String, String]): String =
    properties.toVector.sortBy(_._1).map { case (key, value) =>
      s"""<dt class="col-sm-3">${_escape(key)}</dt><dd class="col-sm-9"><code>${_escape(value)}</code></dd>"""
    }.mkString("\n")

  private def _render_template(
    template: String,
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String = WebTableColumnResolver.defaultViewName
  ): String = {
    val widgets = _render_widgets(template, properties, tableColumns, defaultTableView)
    _render_property_expansions(widgets, properties)
  }

  private def _is_html_document(template: String): Boolean = {
    val text = template.dropWhile(_.isWhitespace).toLowerCase(java.util.Locale.ROOT)
    text.startsWith("<!doctype html") || text.startsWith("<html")
  }

  private def _complete_widget_assets(
    template: String,
    rendered: String,
    options: StaticFormAppLayout.AssetCompletionOptions
  ): String = {
    val hasTextusWidgets = _has_textus_widgets(template)
    StaticFormAppLayout.completeWidgetAssets(
      rendered,
      options.copy(
        requiresBootstrap = hasTextusWidgets,
        requiresTextusWidgets = hasTextusWidgets
      )
    )
  }

  private def _has_textus_widgets(template: String): Boolean =
    """<textus(?::|-)[A-Za-z0-9-]+\b""".r.findFirstIn(template).nonEmpty

  private def _render_property_expansions(
    template: String,
    properties: FormPageProperties
  ): String =
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(
        _escape(
          properties.values.get(m.group(1))
            .orElse(_source_json(m.group(1), properties).map(_json_cell))
            .getOrElse("")
        )
      )
    )

  private def _render_widgets(
    template: String,
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val resultView = """<textus-result-view\s+source="([^"]+)"\s*></textus-result-view>""".r
    val resultTable = """<textus-result-table\b([^>]*)></textus-result-table>""".r
    val card = """(?s)<textus(?::card(?!-)|-card(?!-))\b([^>]*)>(.*?)</textus(?::card|-card)>""".r
    val recordCard = """<textus(?::record-card|-record-card)\b([^>]*)></textus(?::record-card|-record-card)>""".r
    val cardList = """<textus(?::card-list|-card-list)\b([^>]*)></textus(?::card-list|-card-list)>""".r
    val summaryCard = """<textus(?::summary-card|-summary-card)\b([^>]*)></textus(?::summary-card|-summary-card)>""".r
    val actionCard = """<textus(?::action-card|-action-card)\b([^>]*)></textus(?::action-card|-action-card)>""".r
    val actionGroup = """<textus(?::action-group|-action-group)\b([^>]*)></textus(?::action-group|-action-group)>""".r
    val confirmAction = """<textus(?::confirm-action|-confirm-action)\b([^>]*)></textus(?::confirm-action|-confirm-action)>""".r
    val jobPanel = """<textus(?::job-panel|-job-panel)\b([^>]*)></textus(?::job-panel|-job-panel)>""".r
    val jobTicket = """<textus(?::job-ticket|-job-ticket)\b([^>]*)></textus(?::job-ticket|-job-ticket)>""".r
    val jobActions = """<textus(?::job-actions|-job-actions)\b([^>]*)></textus(?::job-actions|-job-actions)>""".r
    val alert = """<textus(?::alert|-alert)\b([^>]*)></textus(?::alert|-alert)>""".r
    val emptyState = """<textus(?::empty-state|-empty-state)\b([^>]*)></textus(?::empty-state|-empty-state)>""".r
    val statusBadge = """<textus(?::status-badge|-status-badge)\b([^>]*)></textus(?::status-badge|-status-badge)>""".r
    val pagination = """<textus(?::pagination|-pagination)\b([^>]*)></textus(?::pagination|-pagination)>""".r
    val navList = """<textus(?::nav-list|-nav-list)\b([^>]*)></textus(?::nav-list|-nav-list)>""".r
    val formLink = """<textus-form-link\s+href="([^"]+)"\s+label="([^"]+)"\s*></textus-form-link>""".r
    val actionLink = """<textus(?::action-link|-action-link)\b([^>]*)></textus(?::action-link|-action-link)>""".r
    val actionForm = """<textus(?::action-form|-action-form)\b([^>]*)></textus(?::action-form|-action-form)>""".r
    val hiddenContext = """<textus(?::hidden-context|-hidden-context)\b([^>]*)></textus(?::hidden-context|-hidden-context)>""".r
    val descriptionList = """<textus(?::description-list|-description-list)\b([^>]*)></textus(?::description-list|-description-list)>""".r
    val htmlField = """<textus(?::html-field|-html-field)\b([^>]*)></textus(?::html-field|-html-field)>""".r
    val propertyList = """<textus-property-list\s+source="([^"]+)"\s*></textus-property-list>""".r
    val errorPanel = """<textus-error-panel\s+source="([^"]+)"\s*></textus-error-panel>""".r
    val a = resultView.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(_render_result_view(m.group(1), properties))
    )
    val b = resultTable.replaceAllIn(a, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_result_table(attrs, properties, tableColumns, defaultTableView))
    })
    val b1 = card.replaceAllIn(b, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_card(attrs, m.group(2), properties))
    })
    val c = recordCard.replaceAllIn(b1, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_record_card(attrs, properties, tableColumns, defaultTableView))
    })
    val d = cardList.replaceAllIn(c, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_card_list(attrs, properties, tableColumns, defaultTableView))
    })
    val e = summaryCard.replaceAllIn(d, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_summary_card(attrs, properties))
    })
    val e1 = actionCard.replaceAllIn(e, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_action_card(attrs, properties))
    })
    val e1a = actionGroup.replaceAllIn(e1, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_action_group(attrs, properties))
    })
    var confirmActionIndex = 0
    val e1b = confirmAction.replaceAllIn(e1a, m => {
      confirmActionIndex = confirmActionIndex + 1
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_confirm_action(attrs, properties, confirmActionIndex))
    })
    val e2 = jobPanel.replaceAllIn(e1b, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_job_panel(attrs, properties))
    })
    val f0 = jobTicket.replaceAllIn(e2, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_job_ticket(attrs, properties))
    })
    val f1 = jobActions.replaceAllIn(f0, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_job_actions(attrs, properties))
    })
    val f = alert.replaceAllIn(f1, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_alert(attrs, properties))
    })
    val g = emptyState.replaceAllIn(f, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_empty_state(attrs, properties))
    })
    val g1 = statusBadge.replaceAllIn(g, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_status_badge(attrs, properties))
    })
    val h = pagination.replaceAllIn(g1, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_pagination(attrs, properties))
    })
    val h1 = navList.replaceAllIn(h, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_nav_list(attrs, properties))
    })
    val i = formLink.replaceAllIn(h1, m =>
      java.util.regex.Matcher.quoteReplacement(_render_form_link(m.group(1), m.group(2), properties))
    )
    val j = actionLink.replaceAllIn(i, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_action_link(attrs, properties))
    })
    val k = actionForm.replaceAllIn(j, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_action_form(attrs, properties))
    })
    val l = hiddenContext.replaceAllIn(k, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_hidden_context(attrs, properties))
    })
    val l1 = descriptionList.replaceAllIn(l, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_description_list(attrs, properties, tableColumns, defaultTableView))
    })
    val l2 = htmlField.replaceAllIn(l1, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_html_field(attrs, properties))
    })
    val n = propertyList.replaceAllIn(l2, m =>
      java.util.regex.Matcher.quoteReplacement(_render_property_list(m.group(1), properties))
    )
    errorPanel.replaceAllIn(n, m =>
      java.util.regex.Matcher.quoteReplacement(_render_error_panel(m.group(1), properties))
    )
  }

  private def _render_hidden_context(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val standard = properties.values.toVector.collect {
      case (key, value) if isHiddenFormContextKey(key) && value.nonEmpty => key -> value
    }.sortBy(_._1)
    val standardKeys = standard.map(_._1).toSet
    val explicit = attrs.get("keys").toVector.flatMap(_hidden_context_keys).flatMap { key =>
      properties.values.get(key).filter(_.nonEmpty).map(key -> _)
    }.filterNot { case (key, _) => standardKeys.contains(key) }
    (standard ++ explicit).map { case (key, value) =>
      s"""<input type="hidden" name="${_escape(key)}" value="${_escape(value)}">"""
    }.mkString("\n")
  }

  private def _hidden_context_keys(value: String): Vector[String] =
    value.split(',').toVector.map(_.trim).filter(_.nonEmpty).distinct

  private def _render_action_link(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val action = _resolve_action(attrs, properties)
    action.map {
      case ActionWidgetValue(href, label, css, method) if method.equalsIgnoreCase("GET") =>
        s"""<a class="${_escape(css)}" href="${_escape(href)}">${_escape(label)}</a>"""
      case ActionWidgetValue(href, label, css, method) =>
        _action_form_html(method, href, css, label, _render_hidden_context(Map.empty, properties))
    }.getOrElse("")
  }

  private def _render_action_form(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String =
    _resolve_action(attrs, properties).map {
      case ActionWidgetValue(href, label, css, method) =>
        val effectiveMethod = attrs.getOrElse("method", method)
        val context =
          if (_widget_bool(attrs, "context", default = true))
            _render_hidden_context(Map.empty, properties)
          else
            ""
        _action_form_html(effectiveMethod, href, css, label, context)
    }.getOrElse("")

  private def _render_action_group(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val sourceActions = attrs.get("source").toVector.flatMap { source =>
      _source_json(source, properties).flatMap(_.asArray).toVector.flatten.flatMap(FormResultMetadata.Action.fromJson)
    }
    val sourcePrefix = attrs.getOrElse("source-prefix", "result.action")
    val context =
      if (_widget_bool(attrs, "context", default = true))
        _render_hidden_context(Map.empty, properties)
      else
        ""
    val buttons =
      if (sourceActions.nonEmpty)
        sourceActions.flatMap { action =>
          _action_value(action, attrs.get("button-class").getOrElse(_action_group_button_class(action.name.getOrElse(""))))
            .map(_action_html(_, context))
        }
      else
        _action_group_names(attrs, properties).flatMap { name =>
          val actionAttrs = Map(
            "source" -> s"${sourcePrefix}.${name}",
            "class" -> attrs.getOrElse("button-class", _action_group_button_class(name))
          )
          _resolve_action(actionAttrs, properties).map(_action_html(_, context))
        }
    if (buttons.isEmpty)
      ""
    else {
      val css = attrs.getOrElse("class", "d-flex flex-wrap gap-2 mt-3 textus-action-group")
      s"""<div class="${_escape(css)}">${buttons.mkString}</div>"""
    }
  }

  private def _render_confirm_action(
    attrs: Map[String, String],
    properties: FormPageProperties,
    index: Int
  ): String = {
    val actionAttrs =
      if (attrs.contains("class"))
        attrs
      else
        attrs + ("class" -> "btn btn-outline-danger")
    _resolve_action(actionAttrs, properties).map {
      case ActionWidgetValue(href, label, css, method) =>
        val modalId = attrs.get("id").filter(_.trim.nonEmpty)
          .getOrElse(s"textus-confirm-action-${index}")
        val title = _attr_value(attrs, "title", properties).getOrElse("Confirm action")
        val message = _attr_value(attrs, "message", properties)
          .getOrElse(s"Please confirm ${label}.")
        val variant = _bootstrap_variant(attrs.getOrElse("variant", "danger"))
        val confirmLabel = _attr_value(attrs, "confirm-label", properties).getOrElse(label)
        val cancelLabel = _attr_value(attrs, "cancel-label", properties).getOrElse("Cancel")
        val context =
          if (_widget_bool(attrs, "context", default = true))
            _render_hidden_context(Map.empty, properties)
          else
            ""
        val confirm = _action_html(ActionWidgetValue(href, confirmLabel, css, method), context)
        val fallback = confirm
        s"""<span class="textus-confirm-action"><button type="button" class="${_escape(css)}" data-bs-toggle="modal" data-bs-target="#${_escape(modalId)}">${_escape(label)}</button></span><div class="modal fade" id="${_escape(modalId)}" tabindex="-1" aria-labelledby="${_escape(modalId)}-label" aria-hidden="true"><div class="modal-dialog"><div class="modal-content"><div class="modal-header border-${_escape(variant)}"><h2 class="modal-title h5" id="${_escape(modalId)}-label">${_escape(title)}</h2><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="${_escape(cancelLabel)}"></button></div><div class="modal-body">${_escape(message)}</div><div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">${_escape(cancelLabel)}</button>${confirm}</div></div></div></div><noscript>${fallback}</noscript>"""
    }.getOrElse("")
  }

  private def _action_group_names(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): Vector[String] =
    attrs.get("actions").map(_.split(',').toVector.map(_.trim).filter(_.nonEmpty)).filter(_.nonEmpty)
      .getOrElse {
        val count = properties.value("result.actions.count").toIntOption.getOrElse(0)
        if (count > 0)
          (0 until count).map(_.toString).toVector
        else
          Vector("primary")
      }

  private def _action_group_button_class(name: String): String =
    name.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "primary" | "submit" | "save" | "await" | "refresh" | "result" => "btn btn-primary"
      case _ => "btn btn-outline-primary"
    }

  private def _action_html(
    action: ActionWidgetValue,
    hiddenContext: String
  ): String =
    action match {
      case ActionWidgetValue(href, label, css, method) if method.equalsIgnoreCase("GET") =>
        s"""<a class="${_escape(css)}" href="${_escape(href)}">${_escape(label)}</a>"""
      case ActionWidgetValue(href, label, css, method) =>
        _action_form_html(method, href, css, label, hiddenContext)
    }

  private def _action_value(
    action: FormResultMetadata.Action,
    css: String
  ): Option[ActionWidgetValue] =
    action.href.map { href =>
      val label = action.label.orElse(action.name).getOrElse("Open")
      val method = action.method.getOrElse("GET")
      ActionWidgetValue(href, label, css, method)
    }

  private final case class ActionWidgetValue(
    href: String,
    label: String,
    css: String,
    method: String
  )

  private def _resolve_action(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): Option[ActionWidgetValue] = {
    val source = attrs.getOrElse("source", "result.action.primary")
    val href = properties.value(s"${source}.href")
    Option.when(href.nonEmpty) {
      val label = attrs.get("label")
        .orElse(_property_non_empty(properties, s"${source}.label"))
        .orElse(_property_non_empty(properties, s"${source}.name"))
        .getOrElse("Open")
      val css = attrs.getOrElse("class", "btn btn-primary")
      val method = _property_non_empty(properties, s"${source}.method").getOrElse("GET")
      ActionWidgetValue(href, label, css, method)
    }
  }

  private def _action_form_html(
    method: String,
    href: String,
    css: String,
    label: String,
    hiddenContext: String
  ): String = {
    val inputs =
      if (hiddenContext.isEmpty)
        ""
      else
        s"${hiddenContext}\n"
    s"""<form method="${_escape(method.toLowerCase(java.util.Locale.ROOT))}" action="${_escape(href)}" class="d-inline">${inputs}<button type="submit" class="${_escape(css)}">${_escape(label)}</button></form>"""
  }

  private def _widget_bool(
    attrs: Map[String, String],
    name: String,
    default: Boolean
  ): Boolean =
    attrs.get(name).map(_.trim.toLowerCase(java.util.Locale.ROOT)) match {
      case Some("false" | "no" | "off" | "0") => false
      case Some("true" | "yes" | "on" | "1") => true
      case Some(_) => default
      case None => default
    }

  private def _property_non_empty(
    properties: FormPageProperties,
    name: String
  ): Option[String] =
    properties.values.get(name).map(_.trim).filter(_.nonEmpty)

  private def _render_form_link(
    hrefPath: String,
    label: String,
    properties: FormPageProperties
  ): String =
    properties.values.get(hrefPath).filter(_.nonEmpty) match {
      case Some(href) =>
        s"""<p><a class="btn btn-outline-primary" href="${_escape(href)}">${_escape(label)}</a></p>"""
      case None =>
        ""
    }

  private def _render_result_view(
    source: String,
    properties: FormPageProperties
  ): String = {
    val value = _source_text(source, properties).getOrElse("")
    s"""<pre class="mt-3 p-3 bg-light border rounded">${_escape(value)}</pre>"""
  }

  private def _render_result_table(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val pagePath = attrs.getOrElse("page", "paging.page")
    val pageSizePath = attrs.getOrElse("page-size", "paging.pageSize")
    val totalPath = attrs.getOrElse("total", "paging.total")
    val hrefPath = attrs.getOrElse("href", "paging.href")
    val columns = _table_columns(attrs.get("columns")).orElse(_table_columns(source, attrs, tableColumns, defaultTableView))
    val page = _int_property(properties, pagePath, 1)
    val pageSize = _int_property(properties, pageSizePath, 20)
    val total = _optional_int_property(properties, totalPath)
    val href = properties.value(hrefPath)
    val table = _json_table(source, properties, page, pageSize, columns, attrs).getOrElse("")
    s"""${table}<div class="mt-3">${_render_pagination(attrs, properties)}</div>"""
  }

  private def _render_card(
    attrs: Map[String, String],
    inner: String,
    properties: FormPageProperties
  ): String = {
    val title = _attr_value(attrs, "title", properties)
    val subtitle = _attr_value(attrs, "subtitle", properties)
    val footer = _attr_value(attrs, "footer", properties)
    val extraClass = attrs.get("class").map(x => s" ${_escape(x)}").getOrElse("")
    val titleHtml = title.filter(_.nonEmpty).map { x =>
      s"""<h3 class="h5 card-title">${_escape(x)}</h3>"""
    }.getOrElse("")
    val subtitleHtml = subtitle.filter(_.nonEmpty).map { x =>
      s"""<p class="card-subtitle text-secondary mb-2">${_escape(x)}</p>"""
    }.getOrElse("")
    val footerHtml = footer.filter(_.nonEmpty).map { x =>
      s"""<div class="card-footer text-secondary">${_escape(x)}</div>"""
    }.getOrElse("")
    s"""<article class="card textus-card${extraClass}"><div class="card-body">${titleHtml}${subtitleHtml}${inner}</div>${footerHtml}</article>"""
  }

  private def _render_record_card(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val columns = _table_columns(attrs.get("columns")).orElse(_table_columns(source, attrs, tableColumns, defaultTableView))
    _source_json(source, properties).flatMap(_record_json).flatMap(_.asObject).map { obj =>
      _record_card_html(obj.toMap, columns, attrs)
    }.getOrElse(_empty_state(attrs.getOrElse("empty", "No record")))
  }

  private def _render_card_list(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val pagePath = attrs.getOrElse("page", "paging.page")
    val pageSizePath = attrs.getOrElse("page-size", "paging.pageSize")
    val columns = _table_columns(attrs.get("columns")).orElse(_table_columns(source, attrs, tableColumns, defaultTableView))
    val page = _int_property(properties, pagePath, 1)
    val pageSize = _int_property(properties, pageSizePath, 20)
    val cards = _source_json(source, properties).flatMap(_table_rows).map { rows =>
      val objects = _page_rows(rows, page, pageSize).flatMap(_.asObject).map(_.toMap)
      if (objects.isEmpty)
        _empty_state(attrs.getOrElse("empty", "No records"))
      else {
        val body = objects.map { obj =>
          s"""<div class="col">${_record_card_html(obj, columns, attrs)}</div>"""
        }.mkString("\n")
        s"""<div class="${_card_list_row_class(attrs)}">${body}</div>"""
      }
    }.getOrElse(_empty_state(attrs.getOrElse("empty", "No records")))
    s"""${cards}<div class="mt-3">${_render_pagination(attrs, properties)}</div>"""
  }

  private def _card_list_row_class(
    attrs: Map[String, String]
  ): String = {
    val cols = _bootstrap_col_count(attrs.get("cols"), 1)
    val md = _bootstrap_col_count(attrs.get("md"), 2)
    val lg = attrs.get("lg").flatMap(_bootstrap_col_count_option)
    (Vector("row", s"row-cols-${cols}", s"row-cols-md-${md}") ++
      lg.map(x => s"row-cols-lg-${x}") ++
      Vector("g-3", "mt-3")).mkString(" ")
  }

  private def _bootstrap_col_count(
    value: Option[String],
    default: Int
  ): Int =
    value.flatMap(_bootstrap_col_count_option).getOrElse(default)

  private def _bootstrap_col_count_option(
    value: String
  ): Option[Int] =
    scala.util.Try(value.trim.toInt).toOption.filter(x => x >= 1 && x <= 6)

  private def _record_json(json: Json): Option[Json] =
    json.asObject.map(_ => json).orElse {
      _table_rows(json).flatMap(_.headOption)
    }

  private def _record_card_html(
    obj: Map[String, Json],
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): String = {
    val fields = columns.getOrElse(obj.keys.toVector.map(name => TableColumn(name, name)))
    val titleField = attrs.get("title").orElse(_first_existing_field(obj, Vector("title", "subject", "name", "label", "id")))
    val subtitleField = attrs.get("subtitle").orElse(_first_existing_field(obj, Vector("recipient_name", "sender_name", "status", "updated_at")))
    val title = titleField.flatMap(obj.get).map(_json_cell).filter(_.nonEmpty).getOrElse(attrs.getOrElse("label", "Record"))
    val subtitle = subtitleField.flatMap(obj.get).map(_json_cell).filter(_.nonEmpty)
    val rows = fields.map { column =>
      val value = obj.get(column.name).map(_json_cell).getOrElse("")
      s"""<dt class="col-sm-4">${_escape(column.label)}</dt><dd class="col-sm-8">${_escape(value)}</dd>"""
    }.mkString
    val subtitleHtml = subtitle.map(x => s"""<p class="card-subtitle text-secondary mb-2">${_escape(x)}</p>""").getOrElse("")
    val actionHtml = _record_action_html(obj, attrs)
    s"""<article class="card h-100 textus-record-card"><div class="card-body"><h3 class="h5 card-title">${_escape(title)}</h3>${subtitleHtml}<dl class="row mb-0">${rows}</dl>${actionHtml}</div></article>"""
  }

  private def _record_action_html(
    obj: Map[String, Json],
    attrs: Map[String, String]
  ): String =
    attrs.get("detail-href").flatMap(_record_href(_, obj, attrs)).map { href =>
      val label = attrs.getOrElse("detail-label", "Open detail")
      s"""<div class="mt-3"><a class="btn btn-sm btn-outline-primary" href="${_escape(href)}">${_escape(label)}</a></div>"""
    }.getOrElse("")

  private def _record_href(
    template: String,
    obj: Map[String, Json],
    attrs: Map[String, String]
  ): Option[String] = {
    val pattern = """\{([A-Za-z0-9_.-]+)\}""".r
    var ok = true
    val base = pattern.replaceAllIn(template, m => {
      obj.get(m.group(1)).map(_json_cell).filter(_.nonEmpty) match {
        case Some(value) => java.util.regex.Matcher.quoteReplacement(value)
        case None =>
          ok = false
          ""
      }
    })
    Option.when(ok)(_append_detail_params(base, obj, attrs))
  }

  private def _append_detail_params(
    href: String,
    obj: Map[String, Json],
    attrs: Map[String, String]
  ): String = {
    val params = attrs.toVector.collect {
      case (key, value) if key.startsWith("detail-param-") =>
        key.stripPrefix("detail-param-") -> _record_value_template(value, obj)
    }.collect {
      case (key, Some(value)) if key.nonEmpty && value.nonEmpty =>
        s"${_escape_query(key)}=${_escape_query(value)}"
    }
    if (params.isEmpty)
      href
    else {
      val sep = if (href.contains("?")) "&" else "?"
      s"${href}${sep}${params.mkString("&")}"
    }
  }

  private def _record_value_template(
    template: String,
    obj: Map[String, Json]
  ): Option[String] = {
    val pattern = """\{([A-Za-z0-9_.-]+)\}""".r
    var ok = true
    val value = pattern.replaceAllIn(template, m => {
      obj.get(m.group(1)).map(_json_cell).filter(_.nonEmpty) match {
        case Some(value) => java.util.regex.Matcher.quoteReplacement(value)
        case None =>
          ok = false
          ""
      }
    })
    Option.when(ok)(value)
  }

  private def _first_existing_field(
    obj: Map[String, Json],
    names: Vector[String]
  ): Option[String] =
    names.find(obj.contains)

  private def _empty_state(
    message: String
  ): String =
    _empty_state(message, None)

  private def _empty_state(
    message: String,
    action: Option[(String, String)]
  ): String = {
    val actionHtml = action.map { case (label, href) =>
      s"""<div class="mt-2"><a class="btn btn-sm btn-primary" href="${_escape(href)}">${_escape(label)}</a></div>"""
    }.getOrElse("")
    s"""<div class="alert alert-secondary textus-empty-state" role="status">${_escape(message)}${actionHtml}</div>"""
  }

  private def _render_summary_card(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val title = _attr_value(attrs, "title", properties).getOrElse("Summary")
    val value = _attr_value(attrs, "value", properties)
      .orElse(attrs.get("source").flatMap(source => _source_text(source, properties)))
      .getOrElse("")
    val subtitle = _attr_value(attrs, "subtitle", properties)
    val variant = _bootstrap_variant(attrs.getOrElse("variant", "primary"))
    val subtitleHtml = subtitle.filter(_.nonEmpty).map { x =>
      s"""<p class="text-secondary mb-0">${_escape(x)}</p>"""
    }.getOrElse("")
    s"""<article class="card h-100 textus-summary-card border-${_escape(variant)}"><div class="card-body"><p class="text-secondary mb-1">${_escape(title)}</p><strong class="display-6 text-${_escape(variant)}">${_escape(value)}</strong>${subtitleHtml}</div></article>"""
  }

  private def _render_action_card(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String =
    _resolve_action(attrs, properties).map {
      case ActionWidgetValue(href, label, css, method) =>
        val title = _attr_value(attrs, "title", properties).getOrElse(label)
        val description = _attr_value(attrs, "description", properties)
          .orElse(_attr_value(attrs, "subtitle", properties))
        val body = description.filter(_.nonEmpty).map { x =>
          s"""<p class="card-text text-secondary">${_escape(x)}</p>"""
        }.getOrElse("")
        val action = _action_html(ActionWidgetValue(href, label, css, method), _render_hidden_context(Map.empty, properties))
        s"""<article class="card h-100 textus-action-card"><div class="card-body"><h3 class="h5 card-title">${_escape(title)}</h3>${body}<div class="mt-3">${action}</div></div></article>"""
    }.getOrElse("")

  private def _render_job_ticket(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.job")
    val jobId = properties.value(s"${source}.id")
    if (jobId.isEmpty)
      ""
    else {
      val title = _attr_value(attrs, "title", properties).getOrElse("Job accepted")
      val status = _property_non_empty(properties, s"${source}.status").getOrElse("accepted")
      val message = _property_non_empty(properties, s"${source}.message")
        .orElse(_property_non_empty(properties, "result.message"))
        .getOrElse("The command is running asynchronously.")
      val variant = _job_status_variant(status)
      val actions =
        if (_widget_bool(attrs, "actions", default = true))
          _render_job_actions(attrs, properties)
        else
          ""
      s"""<article class="card textus-job-ticket border-${_escape(variant)} mb-3"><div class="card-body"><div class="d-flex flex-wrap align-items-start justify-content-between gap-2"><div><h3 class="h5 card-title mb-1">${_escape(title)}</h3><p class="text-secondary mb-2">${_escape(message)}</p></div><span class="badge text-bg-${_escape(variant)}">${_escape(status)}</span></div><dl class="row mb-3"><dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${_escape(jobId)}</code></dd></dl>${actions}</div></article>"""
    }
  }

  private def _render_job_panel(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.job")
    val jobId = properties.value(s"${source}.id")
    if (jobId.isEmpty)
      ""
    else {
      val title = _attr_value(attrs, "title", properties).getOrElse("Command accepted")
      val description = _attr_value(attrs, "description", properties)
        .orElse(_attr_value(attrs, "subtitle", properties))
        .orElse(_property_non_empty(properties, "result.message"))
        .getOrElse("The command is running asynchronously.")
      val ticketAttrs = attrs + ("actions" -> "false")
      val ticket = _render_job_ticket(ticketAttrs, properties)
      val actions = _render_job_actions(attrs, properties)
      val appHref = properties.value(s"${source}.href")
      val appJobsHref = properties.value("result.jobs.href")
      val systemHref = s"/web/system/jobs/${_escape_path_segment(jobId)}"
      val adminHref = s"/web/system/admin/jobs/${_escape_path_segment(jobId)}"
      val appLinks = Vector(
        Option.when(appHref.nonEmpty)(s"""<a class="btn btn-outline-primary btn-sm" href="${_escape(appHref)}">Open job result</a>"""),
        Option.when(appJobsHref.nonEmpty)(s"""<a class="btn btn-outline-secondary btn-sm" href="${_escape(appJobsHref)}">My jobs</a>""")
      ).flatten.mkString
      s"""<section class="textus-job-panel border rounded p-3 mb-3 bg-light"><div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-3"><div><h3 class="h5 mb-1">${_escape(title)}</h3><p class="text-secondary mb-0">${_escape(description)}</p></div><div class="d-flex flex-wrap gap-2">${appLinks}<a class="btn btn-outline-secondary btn-sm" href="${_escape(systemHref)}">System job page</a><a class="btn btn-outline-secondary btn-sm" href="${_escape(adminHref)}">Debug detail</a></div></div>${ticket}${actions}</section>"""
    }
  }

  private def _render_job_actions(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val names = attrs.get("actions")
      .map(_.split(',').toVector.map(_.trim).filter(_.nonEmpty))
      .filter(_.nonEmpty)
      .getOrElse(Vector("await", "detail"))
    val buttons = names.flatMap { name =>
      val context = _render_hidden_context(Map.empty, properties)
      _resolve_action(Map("source" -> s"result.action.${name}", "class" -> _job_action_class(name)), properties)
        .map(_action_html(_, context))
    }
    if (buttons.isEmpty)
      ""
    else
      s"""<div class="d-flex flex-wrap gap-2 textus-job-actions">${buttons.mkString}</div>"""
  }

  private def _job_status_variant(
    status: String
  ): String =
    status.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "completed" | "complete" | "succeeded" | "success" | "done" => "success"
      case "failed" | "failure" | "error" => "danger"
      case "running" | "queued" | "accepted" | "pending" => "primary"
      case "cancelled" | "canceled" | "suspended" => "warning"
      case _ => "secondary"
    }

  private def _job_action_class(
    name: String
  ): String =
    name.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "await" | "refresh" | "result" => "btn btn-primary"
      case _ => "btn btn-outline-primary"
    }

  private def _render_alert(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val variant = _bootstrap_variant(attrs.getOrElse("variant", attrs.getOrElse("type", "info")))
    val title = _attr_value(attrs, "title", properties)
    val message = _attr_value(attrs, "message", properties)
      .orElse(attrs.get("source").flatMap(source => _source_text(source, properties)))
      .orElse(_property_non_empty(properties, "error.message"))
      .orElse(_property_non_empty(properties, "result.message"))
      .getOrElse("")
    if (title.exists(_.nonEmpty) || message.nonEmpty) {
      val titleHtml = title.filter(_.nonEmpty).map(x => s"""<p class="alert-heading fw-semibold mb-1">${_escape(x)}</p>""").getOrElse("")
      s"""<div class="alert alert-${_escape(variant)} textus-alert" role="alert">${titleHtml}${_escape(message)}</div>"""
    } else {
      ""
    }
  }

  private def _render_empty_state(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val shouldRender = attrs.get("source") match {
      case Some(source) =>
        _source_json(source, properties).flatMap(_table_rows).forall(_.isEmpty)
      case None =>
        true
    }
    if (shouldRender) {
      val message = _attr_value(attrs, "message", properties).getOrElse("No records")
      val action = for {
        label <- _attr_value(attrs, "action-label", properties)
        href <- _attr_value(attrs, "action-href", properties)
      } yield label -> href
      _empty_state(message, action)
    } else {
      ""
    }
  }

  private def _render_status_badge(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val value = attrs.get("value").map(_resolve_attr_value(_, properties))
      .orElse(attrs.get("source").flatMap(source => _source_text(source, properties)))
      .orElse(_property_non_empty(properties, "result.status"))
      .orElse(_property_non_empty(properties, "result.outcome"))
      .getOrElse("")
    if (value.isEmpty)
      ""
    else {
      val variant = attrs.get("variant").map(_bootstrap_variant).getOrElse(_status_variant(value))
      val label = attrs.get("label").map(_resolve_attr_value(_, properties)).filter(_.nonEmpty).getOrElse(value)
      s"""<span class="badge text-bg-${_escape(variant)} textus-status-badge">${_escape(label)}</span>"""
    }
  }

  private def _attr_value(
    attrs: Map[String, String],
    name: String,
    properties: FormPageProperties
  ): Option[String] =
    attrs.get(name).map(_resolve_attr_value(_, properties)).filter(_.nonEmpty)

  private def _resolve_attr_value(
    value: String,
    properties: FormPageProperties
  ): String = {
    val propertyPattern = """^\$\{([A-Za-z0-9_.-]+)\}$""".r
    value match {
      case propertyPattern(name) => properties.value(name)
      case _ => properties.values.get(value).filter(_.nonEmpty).getOrElse(value)
    }
  }

  private def _bootstrap_variant(
    value: String
  ): String =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "error" | "danger" => "danger"
      case "warn" | "warning" => "warning"
      case "success" => "success"
      case "primary" => "primary"
      case "secondary" => "secondary"
      case "light" => "light"
      case "dark" => "dark"
      case "info" => "info"
      case _ => "secondary"
    }

  private def _status_variant(
    value: String
  ): String =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "ok" | "success" | "succeeded" | "done" | "completed" | "published" | "active" => "success"
      case "warn" | "warning" | "pending" | "queued" | "running" | "draft" => "warning"
      case "error" | "failed" | "failure" | "denied" | "rejected" | "inactive" => "danger"
      case "info" | "accepted" => "primary"
      case _ => "secondary"
    }

  private def _render_pagination(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val pagePath = attrs.getOrElse("page", "paging.page")
    val pageSizePath = attrs.getOrElse("page-size", "paging.pageSize")
    val totalPath = attrs.getOrElse("total", "paging.total")
    val hrefPath = attrs.getOrElse("href", "paging.href")
    val hasNextPath = attrs.getOrElse("has-next", "paging.hasNext")
    val page = _int_property(properties, pagePath, 1)
    val pageSize = _int_property(properties, pageSizePath, 20)
    val total = _optional_int_property(properties, totalPath)
    val href = properties.value(hrefPath)
    val hasNext = _optional_bool_property(properties, hasNextPath)
    _paging_nav(page, pageSize, total, href, hasNext)
  }

  private def _render_nav_list(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val style = attrs.getOrElse("style", "buttons").trim.toLowerCase(java.util.Locale.ROOT)
    val items =
      attrs.get("items").toVector.flatMap(_.split("\\|").toVector).flatMap(_nav_item(_, properties)) ++
        attrs.get("source").toVector.flatMap(source => _source_json(source, properties).toVector.flatMap(_nav_items_from_json(_, properties)))
    if (items.isEmpty)
      ""
    else if (style == "list")
      s"""<nav class="textus-nav-list"><div class="list-group">${items.map(_nav_item_html(_, listStyle = true, properties)).mkString}</div></nav>"""
    else
      s"""<nav class="d-flex flex-wrap gap-2 mt-3 textus-nav-list">${items.map(_nav_item_html(_, listStyle = false, properties)).mkString}</nav>"""
  }

  private final case class NavListItem(
    label: String,
    href: String,
    css: String,
    method: String = "GET"
  )

  private def _nav_item(
    text: String,
    properties: FormPageProperties
  ): Option[NavListItem] = {
    val index = text.indexOf(':')
    if (index < 0)
      None
    else {
      val label = text.take(index).trim
      val rest = text.drop(index + 1).trim
      val last = rest.lastIndexOf(':')
      val (href, css) =
        if (last >= 0 && _looks_like_css_class(rest.drop(last + 1).trim))
          rest.take(last).trim -> rest.drop(last + 1).trim
        else
          rest -> "btn btn-outline-secondary"
      Some(NavListItem(_resolve_attr_value(label, properties), _resolve_attr_value(href, properties), css))
    }
  }

  private def _nav_items_from_json(
    json: Json,
    properties: FormPageProperties
  ): Vector[NavListItem] =
    json.asArray.getOrElse(Vector.empty).flatMap(_.asObject).flatMap { obj =>
      val values = obj.toMap
      for {
        label <- values.get("label").map(_json_cell).filter(_.nonEmpty)
        href <- values.get("href").map(_json_cell).filter(_.nonEmpty)
      } yield {
        val css = values.get("class").map(_json_cell).filter(_.nonEmpty).getOrElse("btn btn-outline-secondary")
        val method = values.get("method").map(_json_cell).filter(_.nonEmpty).getOrElse("GET")
        NavListItem(
          _resolve_attr_value(label, properties),
          _resolve_attr_value(href, properties),
          css,
          method
        )
      }
    }

  private def _nav_item_html(
    item: NavListItem,
    listStyle: Boolean,
    properties: FormPageProperties
  ): String = {
    val method = item.method.trim.toUpperCase(java.util.Locale.ROOT)
    if (method == "GET") {
      val css =
        if (listStyle)
          s"list-group-item list-group-item-action ${item.css}"
        else
          item.css
      s"""<a class="${_escape(css)}" href="${_escape(item.href)}">${_escape(item.label)}</a>"""
    } else {
      val css =
        if (listStyle)
          s"list-group-item list-group-item-action ${item.css}"
        else
          item.css
      _action_form_html(method, item.href, css, item.label, _render_hidden_context(Map.empty, properties))
    }
  }

  private def _looks_like_css_class(value: String): Boolean =
    value.startsWith("btn ") ||
      value.startsWith("btn-") ||
      value.startsWith("link-") ||
      value.startsWith("textus-")

  private def _render_property_list(
    source: String,
    properties: FormPageProperties
  ): String = {
    val prefix = if (source.endsWith(".")) source else source + "."
    val xs = properties.values.collect {
      case (key, value) if key == source || key.startsWith(prefix) => key -> value
    }
    s"""<dl class="row">${_property_rows(xs)}</dl>"""
  }

  private def _render_description_list(
    attrs: Map[String, String],
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val columns = _table_columns(attrs.get("columns")).orElse(_table_columns(source, attrs, tableColumns, defaultTableView))
    _source_json(source, properties).flatMap(_record_json).flatMap(_.asObject).map { obj =>
      val fields = columns.getOrElse(obj.keys.toVector.map(name => TableColumn(name, name)))
      val rows = fields.flatMap { column =>
        obj(column.name).map { json =>
          s"""<dt class="col-sm-4">${_escape(column.label)}</dt><dd class="col-sm-8">${_escape(_json_cell(json))}</dd>"""
        }
      }.mkString
      if (rows.isEmpty)
        _empty_state(attrs.getOrElse("empty", "No details"))
      else
        s"""<dl class="row textus-description-list">${rows}</dl>"""
    }.getOrElse(_empty_state(attrs.getOrElse("empty", "No details")))
  }

  private def _render_html_field(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val source = attrs.getOrElse("source", "result.body")
    val field = attrs.getOrElse("field", "content")
    val css = attrs.getOrElse("class", "textus-html-field")
    val html = _source_json(source, properties)
      .flatMap(_record_json)
      .flatMap(json => _json_at(json, field.split('.').toVector))
      .flatMap(_.asString)
      .getOrElse("")
    if (html.isEmpty)
      ""
    else
      s"""<div class="${_escape(css)}">${html}</div>"""
  }

  private def _render_error_panel(
    source: String,
    properties: FormPageProperties
  ): String = {
    val prefix = if (source.endsWith(".")) source else source + "."
    val xs = properties.values.collect {
      case (key, value) if key == source || key.startsWith(prefix) => key -> value
    }
    if (xs.isEmpty) ""
    else s"""<div class="alert alert-danger" role="alert">${_property_rows(xs)}</div>"""
  }

  private def _json_table(
    source: String,
    properties: FormPageProperties,
    page: Int,
    pageSize: Int,
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): Option[String] =
    _source_json(source, properties).flatMap { json =>
      val rows = _table_rows(json)
      rows.flatMap(xs => _records_table(_page_rows(xs, page, pageSize), columns, attrs))
    }

  private def _table_rows(
    json: Json
  ): Option[Vector[Json]] =
    json.asArray
      .orElse(_json_array_at(json, "data"))
      .orElse(_json_array_at(json, "items"))
      .orElse(_json_array_at(json, "result"))
      .orElse(_json_array_at(json, "records"))

  private def _json_array_at(
    json: Json,
    name: String
  ): Option[Vector[Json]] =
    json.hcursor.downField(name).focus.flatMap(_.asArray)

  private def _table_columns(columns: Option[String]): Option[Vector[TableColumn]] =
    columns.map(_.split(',').toVector.flatMap(_table_column)).filter(_.nonEmpty)

  private def _table_column(value: String): Option[TableColumn] = {
    val text = value.trim
    if (text.isEmpty)
      None
    else
      text.split(":", 2).toList match {
        case name :: label :: Nil => Some(TableColumn(name.trim, label.trim))
        case name :: Nil => Some(TableColumn(name.trim, name.trim))
        case _ => None
      }
  }

  private def _table_columns(
    source: String,
    attrs: Map[String, String],
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): Option[Vector[TableColumn]] =
    _table_column_key(source, attrs, defaultTableView).flatMap(tableColumns.get)
      .orElse(_table_column_key(s"${source}.data", attrs, defaultTableView).flatMap(tableColumns.get))
      .orElse(tableColumns.get(source))
      .orElse(tableColumns.get(s"${source}.data"))
      .orElse(tableColumns.get("result.data"))
      .orElse(tableColumns.get("result.body.data"))
      .filter(_.nonEmpty)

  private def _table_column_key(
    source: String,
    attrs: Map[String, String],
    defaultTableView: String
  ): Option[String] =
    attrs.get("entity").map { entity =>
      val view = attrs.getOrElse("view", defaultTableView)
      _table_column_key(source, entity, view)
    }

  private[http] def tableColumnKey(
    source: String,
    entity: String,
    view: String = WebTableColumnResolver.defaultViewName
  ): String =
    _table_column_key(source, entity, view)

  private def _table_column_key(
    source: String,
    entity: String,
    view: String
  ): String =
    s"${source}|entity=${NamingConventions.toNormalizedSegment(entity)}|view=${NamingConventions.toNormalizedSegment(view)}"

  private def _widget_attrs(source: String): Map[String, String] =
    """([A-Za-z0-9_.:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""".r.findAllMatchIn(source).map { m =>
      m.group(1) -> Option(m.group(2)).getOrElse(m.group(3))
    }.toMap

  private def _source_text(
    source: String,
    properties: FormPageProperties
  ): Option[String] =
    properties.values.get(source).orElse(_source_json(source, properties).map(_.spaces2))

  private def _source_json(
    source: String,
    properties: FormPageProperties
  ): Option[Json] =
    properties.values.get(source).flatMap(parse(_).toOption).orElse {
      val body = properties.values.get("result.body")
      body.flatMap(parse(_).toOption).flatMap { json =>
        val path =
          if (source.startsWith("result.body."))
            source.stripPrefix("result.body.").split('.').toVector
          else if (source.startsWith("result."))
            source.stripPrefix("result.").split('.').toVector
          else
            Vector.empty
        if (path.isEmpty) None else _json_at(json, path)
      }
    }

  private def _json_at(
    json: Json,
    path: Vector[String]
  ): Option[Json] =
    path.foldLeft(Option(json)) { (z, name) =>
      z.flatMap(_.hcursor.downField(name).focus)
    }

  private def _page_rows(
    rows: Vector[Json],
    page: Int,
    pageSize: Int
  ): Vector[Json] = {
    val offset = math.max(0, page - 1) * math.max(1, pageSize)
    rows.slice(offset, offset + math.max(1, pageSize))
  }

  private def _records_table(
    rows: Vector[Json],
    columns: Option[Vector[TableColumn]],
    attrs: Map[String, String]
  ): Option[String] = {
    val objects = rows.flatMap(_.asObject)
    if (objects.isEmpty) {
      None
    } else {
      val headers = columns.getOrElse(objects.flatMap(_.keys).distinct.map(name => TableColumn(name, name)))
      val actionHeader = attrs.get("detail-href").map(_ => "<th>Actions</th>").getOrElse("")
      val head = headers.map(h => s"<th>${_escape(h.label)}</th>").mkString + actionHeader
      val body = objects.map { obj =>
        val cells = headers.map { h =>
          val value = obj(h.name).map(_json_cell).getOrElse("")
          s"<td>${_escape(value)}</td>"
        }.mkString
        val actionCell = attrs.get("detail-href").flatMap(_record_href(_, obj.toMap, attrs)).map { href =>
          val label = attrs.getOrElse("detail-label", "Open detail")
          s"""<td><a class="btn btn-sm btn-outline-primary" href="${_escape(href)}">${_escape(label)}</a></td>"""
        }.getOrElse(attrs.get("detail-href").map(_ => "<td></td>").getOrElse(""))
        s"<tr>${cells}${actionCell}</tr>"
      }.mkString("\n")
      Some(s"""<div class="table-responsive mt-3"><table class="table table-sm table-striped"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>""")
    }
  }

  private def _json_cell(json: Json): String =
    json.asString
      .orElse(json.asNumber.map(_.toString))
      .orElse(json.asBoolean.map(_.toString))
      .getOrElse(json.noSpaces)

  private def _paging_nav(
    page: Int,
    pageSize: Int,
    total: Option[Int],
    href: String,
    hasNext: Option[Boolean] = None
  ): String = {
    val prev = math.max(1, page - 1)
    val next = page + 1
    val last = total.map(t => math.max(1, (t + pageSize - 1) / pageSize))
    val prevDisabled = page <= 1
    val nextDisabled = last.exists(page >= _) || hasNext.contains(false)
    val pages = last.map(l => (1 to math.min(l, 5)).toVector).getOrElse(Vector.empty)
    val pageItems = pages.map { p =>
      val active = if (p == page) " active" else ""
      s"""<li class="page-item${active}"><a class="page-link" href="${_escape(_paging_href(href, p, pageSize))}">${p}</a></li>"""
    }.mkString
    val current =
      if (last.isDefined) ""
      else s"""<li class="page-item active"><span class="page-link">Page ${page}</span></li>"""
    s"""<nav aria-label="Result pages"><ul class="pagination">
       |<li class="page-item${if (prevDisabled) " disabled" else ""}"><a class="page-link" href="${_escape(_paging_href(href, prev, pageSize))}">Previous</a></li>
       |${pageItems}${current}
       |<li class="page-item${if (nextDisabled) " disabled" else ""}"><a class="page-link" href="${_escape(_paging_href(href, next, pageSize))}">Next</a></li>
       |</ul></nav>""".stripMargin
  }

  private def _paging_href(
    href: String,
    page: Int,
    pageSize: Int
  ): String =
    href.replace("{page}", page.toString).replace("{pageSize}", pageSize.toString)

  private def _int_property(
    properties: FormPageProperties,
    name: String,
    default: Int
  ): Int =
    _optional_int_property(properties, name).getOrElse(default)

  private def _optional_int_property(
    properties: FormPageProperties,
    name: String
  ): Option[Int] =
    properties.value(name).toIntOption

  private def _optional_bool_property(
    properties: FormPageProperties,
    name: String
  ): Option[Boolean] =
    properties.value(name).trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "yes" | "on" | "1" => Some(true)
      case "false" | "no" | "off" | "0" => Some(false)
      case _ => None
    }

  private def _dashboard_state_json(
    components: Vector[Component],
    scope: String,
    name: String,
    version: Option[String],
    jobs: (Int, Int, Int, Int),
    subsystemName: String,
    subsystemVersion: Option[String],
    assemblyWarningCount: Int
  ): String = {
    val serviceCount = components.map(_.protocol.services.services.size).sum
    val operationCount = components.flatMap(_.protocol.services.services).map(_.operations.operations.length).sum
    val componentJson = components.map(_component_json).mkString("[", ",", "]")
    val (running, queued, completed, failed) = jobs
    val htmlRequests = RuntimeDashboardMetrics.htmlSnapshot
    val actionCalls = RuntimeDashboardMetrics.actionCallSnapshot
    val authorizationDecisions = RuntimeDashboardMetrics.authorizationDecisionSnapshot
    val authorizationDiagnostics = RuntimeDashboardMetrics.authorizationDiagnosticCounts
    val dslChokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val validation = RuntimeDashboardMetrics.validationSnapshot
    val validationDiagnostics = RuntimeDashboardMetrics.validationDiagnosticCounts
    val operationRequestValidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot
    val operationRequestValidationDiagnostics = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts
    val blobOperations = RuntimeDashboardMetrics.blobOperationSnapshot
    val blobDiagnostics = RuntimeDashboardMetrics.blobDiagnosticCounts
    val avgMillis =
      if (htmlRequests.recent.isEmpty) 0L
      else htmlRequests.recent.map(_.elapsedMillis).sum / htmlRequests.recent.size
    val adminPath =
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/admin"
      else "/web/system/admin"
    val manualPath =
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/document"
      else "/web/system/document"
    s"""{"scope":"${_json(scope)}","name":"${_json(name)}","version":${version.map(v => "\"" + _json(v) + "\"").getOrElse("null")},"observedAt":"${java.time.Instant.now.toString}","status":"UP","cncf":{"version":"${_json(CncfVersion.current)}"},"subsystem":{"name":"${_json(subsystemName)}","version":${subsystemVersion.map(v => "\"" + _json(v) + "\"").getOrElse("null")}},"componentCount":${components.size},"serviceCount":${serviceCount},"operationCount":${operationCount},"actions":{"actionCalls":${_snapshot_json(actionCalls, includeRecent = false)},"jobs":${_jobs_json(running, queued, completed, failed)}},"dsl":{"chokepoints":${_snapshot_json(dslChokepoints, includeRecent = false)},"validation":${_snapshot_json(validation, includeRecent = false)},"validationDiagnostics":${_string_long_map_json(validationDiagnostics)},"operationRequestValidation":${_snapshot_json(operationRequestValidation, includeRecent = false)},"operationRequestValidationDiagnostics":${_string_long_map_json(operationRequestValidationDiagnostics)}},"authorization":{"decisions":${_snapshot_json(authorizationDecisions, includeRecent = false)},"diagnostics":${_string_long_map_json(authorizationDiagnostics)}},"blob":{"operations":${_snapshot_json(blobOperations, includeRecent = false)},"diagnostics":${_string_long_map_json(blobDiagnostics)}},"assembly":{"warnings":{"count":${assemblyWarningCount}}},"html":{"requests":${_snapshot_json(htmlRequests, includeRecent = true, Some(avgMillis))}},"links":{"admin":"${_json(adminPath)}","performance":"/web/system/performance","manual":"${_json(manualPath)}","console":"/web/console","assemblyWarnings":"/web/system/admin/assembly/warnings"},"components":${componentJson}}"""
  }

  private def _snapshot_json(
    snapshot: RuntimeDashboardMetrics.Snapshot,
    includeRecent: Boolean,
    recentAverageMillis: Option[Long] = None
  ): String = {
    val recent =
      if (includeRecent) {
        val xs = snapshot.recent.map { x =>
          s"""{"observedAt":${x.observedAt},"method":"${_json(x.method)}","path":"${_json(x.path)}","status":${x.status},"elapsedMillis":${x.elapsedMillis}}"""
        }.mkString("[", ",", "]")
        s""","recent":${xs}"""
      } else {
        ""
      }
    val avg = recentAverageMillis.map(x => s""","recentAverageMillis":${x}""").getOrElse("")
    s"""{"summary":${_summary_json(snapshot.summary)},"series":{"minute":${_buckets_json(snapshot.bucketsByMinute)},"hour":${_buckets_json(snapshot.bucketsByHour)},"day":${_buckets_json(snapshot.bucketsByDay)}}${avg}${recent}}"""
  }

  private def _summary_json(summary: RuntimeDashboardMetrics.CountSummary): String =
    s"""{"cumulative":${_window_json(summary.cumulative)},"day":${_window_json(summary.day)},"hour":${_window_json(summary.hour)},"minute":${_window_json(summary.minute)}}"""

  private def _window_json(window: RuntimeDashboardMetrics.CountWindow): String =
    s"""{"count":${window.total},"errors":${window.errors}}"""

  private def _buckets_json(buckets: Vector[RuntimeDashboardMetrics.RequestBucket]): String =
    buckets.map(x => s"""{"period":${x.period},"count":${x.count},"errors":${x.errors}}""").mkString("[", ",", "]")

  private def _string_long_map_json(values: Map[String, Long]): String =
    values.toVector
      .sortBy(_._1)
      .map { case (k, v) => s""""${_json(k)}":${v}""" }
      .mkString("{", ",", "}")

  private def _knowledge_admin_page(subsystem: Subsystem): String = {
    val projections = KnowledgeSpaceProjection.components(subsystem.components)
    val rows =
      if (projections.isEmpty)
        _admin_empty_table_cell(13, "No components are loaded.")
      else
        projections.map { projection =>
          val path = _escape_path_segment(projection.componentName)
          val status = projection.status
          val counts = projection.counts
          val source = projection.sourceDiagnostics
          s"""<tr>
             |  <td><a href="/web/system/admin/knowledge/${path}">${_escape(projection.componentName)}</a></td>
             |  <td><span class="badge text-bg-secondary">${_escape(status.state.label)}</span></td>
             |  <td>${_escape(source.sourceKind)}</td>
             |  <td>${_escape(source.providerStatus)}</td>
             |  <td>${counts.nodeCount}</td>
             |  <td>${counts.relationshipCount}</td>
             |  <td>${counts.frameCount}</td>
             |  <td>${counts.factCount}</td>
             |  <td>${counts.evidenceCount}</td>
             |  <td>${counts.provenanceCount}</td>
             |  <td>${counts.entityBindingCount}</td>
             |  <td>${counts.tagBindingCount}</td>
             |  <td>${_escape(status.error.getOrElse(""))}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    _simple_page(
      title = "System Knowledge",
      subtitle = "Component-owned KnowledgeSpace status and compact graph projection",
      body =
        s"""${_admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "Performance summary" -> "/web/system/performance",
             "Observability" -> "/web/system/admin/observability"
           ))}
           |${_admin_card(
             "KnowledgeSpace Components",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Component</th><th>Status</th><th>Source</th><th>Provider</th><th>Nodes</th><th>Relationships</th><th>Frames</th><th>Facts</th><th>Evidence</th><th>Provenance</th><th>Entity bindings</th><th>Tag bindings</th><th>Error</th></tr></thead>
                |  <tbody>${rows}</tbody>
                |</table></div>""".stripMargin
           )}
           |${_admin_card(
             "Projection policy",
             """<p class="mb-2">This page shows CNCF operational semantic projections, not raw RDF triples, Vector DB payloads, or source documents.</p>
               |<p class="mb-0">KnowledgeSpace is component-owned. Cross-component views are rendered as read-only aggregation.</p>""".stripMargin
           )}""".stripMargin
    )
  }

  private def _knowledge_component_page(
    subsystem: Subsystem,
    component: Component
  ): String = {
    val projection = KnowledgeSpaceProjection.component(component)
    val path = _escape_path_segment(component.name)
    val previewlimit = 50
    val sortednodes = projection.nodes.sortBy(_.id.print)
    val sortedrelationships = projection.relationships.sortBy(_.id.print)
    val sortedframes = projection.frames.sortBy(_.id.print)
    val sortedfacts = projection.facts.sortBy(_.id.print)
    val nodepreview = sortednodes.take(previewlimit)
    val relationshippreview = sortedrelationships.take(previewlimit)
    val framepreview = sortedframes.take(previewlimit)
    val factpreview = sortedfacts.take(previewlimit)
    val similarityrepresentations = projection.nodes.map(_.similarity.representations.size).sum + projection.relationships.map(_.similarity.representations.size).sum
    val similaritysearchentries = projection.nodes.map(_.similarity.searchEntries.size).sum + projection.relationships.map(_.similarity.searchEntries.size).sum
    val nodecaption =
      if (sortednodes.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortednodes.size} nodes.</p>"""
      else
        ""
    val relationshipcaption =
      if (sortedrelationships.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortedrelationships.size} relationships.</p>"""
      else
        ""
    val framecaption =
      if (sortedframes.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortedframes.size} frames.</p>"""
      else
        ""
    val factcaption =
      if (sortedfacts.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortedfacts.size} facts.</p>"""
      else
        ""
    val nodes =
      if (sortednodes.isEmpty)
        _admin_empty_table_cell(4, "No knowledge nodes are loaded.")
      else
        nodepreview.map { node =>
          s"""<tr>
             |  <td><a href="/web/system/admin/knowledge/${path}/nodes/${_escape_path_segment(node.id.print)}"><code>${_escape(node.id.print)}</code></a></td>
             |  <td>${_escape(node.category.print)}</td>
             |  <td>${_escape(node.presentation.defaultLabel.getOrElse(""))}</td>
             |  <td>${node.identity.externalIdentifiers.size}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    val relationships =
      if (sortedrelationships.isEmpty)
        _admin_empty_table_cell(7, "No knowledge relationships are loaded.")
      else
        relationshippreview.map(_knowledge_relationship_row(_, Some(path))).mkString("\n")
    val frames = _knowledge_frame_rows(framepreview, emptycolspan = 8, "No knowledge frames are loaded.")
    val facts = _knowledge_fact_rows(factpreview, emptycolspan = 6, "No knowledge facts are loaded.")
    _simple_page(
      title = s"System Knowledge ${component.name}",
      subtitle = "Component KnowledgeSpace compact projection",
      body =
        s"""${_admin_nav_card(Vector(
             "System knowledge" -> "/web/system/admin/knowledge",
             "System admin" -> "/web/system/admin"
           ))}
           |${_admin_card(
             "Status",
             _field_table(Vector(
               "Component" -> component.name,
               "Subsystem" -> subsystem.name,
               "State" -> projection.status.state.label,
               "Ready" -> projection.status.isReady.toString,
               "Nodes" -> projection.counts.nodeCount.toString,
               "Relationships" -> projection.counts.relationshipCount.toString,
               "Frames" -> projection.counts.frameCount.toString,
               "Facts" -> projection.counts.factCount.toString,
               "Evidence" -> projection.counts.evidenceCount.toString,
               "Provenance" -> projection.counts.provenanceCount.toString,
               "External identifiers" -> projection.counts.externalIdentifierCount.toString,
               "Entity bindings" -> projection.counts.entityBindingCount.toString,
               "Tag bindings" -> projection.counts.tagBindingCount.toString,
               "Similarity representations" -> similarityrepresentations.toString,
               "Similarity search entries" -> similaritysearchentries.toString,
               "Projection source" -> projection.sourceDiagnostics.sourceKind,
               "Storage" -> projection.sourceDiagnostics.storage,
               "Provider status" -> projection.sourceDiagnostics.providerStatus,
               "Projection mode" -> projection.sourceDiagnostics.projectionMode,
               "Error" -> projection.status.error.getOrElse("")
             ))
           )}
           |${_admin_card(
             "Nodes",
             s"""${nodecaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Node</th><th>Category</th><th>Label</th><th>External identifiers</th></tr></thead>
                |  <tbody>${nodes}</tbody>
                |</table></div>""".stripMargin
           )}
           |${_admin_card(
             "Relationships",
             s"""${relationshipcaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Relationship</th><th>Kind</th><th>RDF predicate</th><th>Source</th><th>Target</th><th>Semantic types</th><th>Evidence</th></tr></thead>
                |  <tbody>${relationships}</tbody>
                |</table></div>""".stripMargin
           )}
           |${_admin_card(
             "Frames",
             s"""${framecaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Frame</th><th>Kind</th><th>Route</th><th>Provider</th><th>Purpose</th><th>Query</th><th>Focus nodes</th><th>Facts</th></tr></thead>
                |  <tbody>${frames}</tbody>
                |</table></div>""".stripMargin
           )}
           |${_admin_card(
             "Facts",
             s"""${factcaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Fact</th><th>Kind</th><th>Subject</th><th>Relationship</th><th>Predicate</th><th>Value</th></tr></thead>
                |  <tbody>${facts}</tbody>
                |</table></div>""".stripMargin
           )}""".stripMargin
    )
  }

  private def _knowledge_node_page(
    subsystem: Subsystem,
    projection: org.goldenport.cncf.knowledge.KnowledgeNodeProjection
  ): String = {
    val componentpath = _escape_path_segment(projection.componentName)
    val node = projection.node
    val externalids = _knowledge_external_identifier_table(node.identity.externalIdentifiers)
    val from =
      if (projection.relationshipsFrom.isEmpty)
        _admin_empty_table_cell(7, "No outgoing relationships.")
      else
        projection.relationshipsFrom.sortBy(_.id.print).map(_knowledge_relationship_row(_, Some(componentpath))).mkString("\n")
    val to =
      if (projection.relationshipsTo.isEmpty)
        _admin_empty_table_cell(7, "No incoming relationships.")
      else
        projection.relationshipsTo.sortBy(_.id.print).map(_knowledge_relationship_row(_, Some(componentpath))).mkString("\n")
    val evidence = _knowledge_evidence_table(projection.evidence)
    val provenance = _knowledge_provenance_table(projection.provenance)
    val frames = _knowledge_frame_table(projection.frames)
    val facts = _knowledge_fact_table(projection.facts)
    val identity = _knowledge_identity_table(node)
    val presentation = _knowledge_presentation_table(node.presentation)
    val semantics = _knowledge_semantics_table(node.semantics)
    val structure = _knowledge_structure_table(node.structure)
    val bindings = _knowledge_bindings_table(node.bindings)
    val similarity = _knowledge_similarity_table(node.similarity)
    val operations = _knowledge_operations_table(node.operations)
    _simple_page(
      title = s"Knowledge Node ${node.id.print}",
      subtitle = s"${projection.componentName} KnowledgeSpace node detail",
      body =
        s"""${_admin_nav_card(Vector(
             "Component knowledge" -> s"/web/system/admin/knowledge/${componentpath}",
             "System knowledge" -> "/web/system/admin/knowledge"
           ))}
           |${_admin_card("Node identity", identity)}
           |${_admin_card("Presentation", presentation)}
           |${_admin_card("Semantics", semantics)}
           |${_admin_card("Structure", structure)}
           |${_admin_card("Entity and Tag bindings", bindings)}
           |${_admin_card("Similarity", similarity)}
           |${_admin_card("Operations", operations)}
           |${_admin_card("External identifiers", externalids)}
           |${_admin_card("Frames", frames)}
           |${_admin_card("Facts", facts)}
           |${_admin_card(
             "Outgoing relationships",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Relationship</th><th>Kind</th><th>RDF predicate</th><th>Source</th><th>Target</th><th>Semantic types</th><th>Evidence</th></tr></thead>
                |  <tbody>${from}</tbody>
                |</table></div>""".stripMargin
           )}
           |${_admin_card(
             "Incoming relationships",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Relationship</th><th>Kind</th><th>RDF predicate</th><th>Source</th><th>Target</th><th>Semantic types</th><th>Evidence</th></tr></thead>
                |  <tbody>${to}</tbody>
                |</table></div>""".stripMargin
           )}
           |${_admin_card("Evidence", evidence)}
           |${_admin_card("Provenance", provenance)}""".stripMargin
    )
  }

  private def _knowledge_relationship_row(
    relationship: org.goldenport.cncf.knowledge.KnowledgeRelationship,
    componentpath: Option[String] = None
  ): String = {
    val source = _knowledge_node_link(componentpath, relationship.sourceNodeId)
    val target = _knowledge_node_link(componentpath, relationship.targetNodeId)
    val semantictypes = relationship.semanticTypes.map(x => s"${x.system}:${x.name}").mkString(", ")
    s"""<tr>
       |  <td><code>${_escape(relationship.id.print)}</code></td>
       |  <td>${_escape(relationship.kind.print)}</td>
       |  <td>${_escape(relationship.rdfPredicate.map(_.print).getOrElse(""))}</td>
       |  <td>${source}</td>
       |  <td>${target}</td>
       |  <td>${_escape(semantictypes)}</td>
       |  <td>${_escape(relationship.evidenceIds.map(_.print).mkString(", "))}</td>
       |</tr>""".stripMargin
  }

  private def _knowledge_node_link(
    componentpath: Option[String],
    id: org.goldenport.cncf.knowledge.KnowledgeNodeId
  ): String =
    componentpath
      .map(path => s"""<a href="/web/system/admin/knowledge/${path}/nodes/${_escape_path_segment(id.print)}"><code>${_escape(id.print)}</code></a>""")
      .getOrElse(s"<code>${_escape(id.print)}</code>")

  private def _knowledge_frame_table(
    frames: Vector[org.goldenport.cncf.knowledge.KnowledgeFrame]
  ): String =
    if (frames.isEmpty)
      _admin_empty_state("No frames linked to this node projection.")
    else {
      val rows = _knowledge_frame_rows(frames.sortBy(_.id.print), emptycolspan = 8, "No frames linked to this node projection.")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Frame</th><th>Kind</th><th>Route</th><th>Provider</th><th>Purpose</th><th>Query</th><th>Focus nodes</th><th>Facts</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _knowledge_frame_rows(
    frames: Vector[org.goldenport.cncf.knowledge.KnowledgeFrame],
    emptycolspan: Int,
    emptymessage: String
  ): String =
    if (frames.isEmpty)
      _admin_empty_table_cell(emptycolspan, emptymessage)
    else
      frames.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${_escape(item.id.print)}</code></td>
           |  <td>${_escape(item.kind.print)}</td>
           |  <td>${_escape(item.origin.route.print)}</td>
           |  <td>${_escape(item.origin.provider.getOrElse(""))}</td>
           |  <td>${_escape(item.purpose.map(_.print).getOrElse(""))}</td>
           |  <td>${_escape(item.query.map(_.print).getOrElse(""))}</td>
           |  <td>${_escape(item.focusNodeIds.map(_.print).mkString(", "))}</td>
           |  <td>${_escape(item.factIds.map(_.print).mkString(", "))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")

  private def _knowledge_fact_table(
    facts: Vector[org.goldenport.cncf.knowledge.KnowledgeFact]
  ): String =
    if (facts.isEmpty)
      _admin_empty_state("No facts linked to this node projection.")
    else {
      val rows = _knowledge_fact_rows(facts.sortBy(_.id.print), emptycolspan = 6, "No facts linked to this node projection.")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Fact</th><th>Kind</th><th>Subject</th><th>Relationship</th><th>Predicate</th><th>Value</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _knowledge_fact_rows(
    facts: Vector[org.goldenport.cncf.knowledge.KnowledgeFact],
    emptycolspan: Int,
    emptymessage: String
  ): String =
    if (facts.isEmpty)
      _admin_empty_table_cell(emptycolspan, emptymessage)
    else
      facts.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${_escape(item.id.print)}</code></td>
           |  <td>${_escape(item.kind.print)}</td>
           |  <td>${_escape(item.subjectNodeId.map(_.print).getOrElse(""))}</td>
           |  <td>${_escape(item.relationshipId.map(_.print).getOrElse(""))}</td>
           |  <td>${_escape(item.predicate.getOrElse(""))}</td>
           |  <td>${_escape(item.value.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")

  private def _knowledge_identity_table(
    node: org.goldenport.cncf.knowledge.KnowledgeNode
  ): String =
    _field_table(Vector(
      "Id" -> node.id.print,
      "Category" -> node.category.print,
      "RDF node" -> node.identity.rdfNode.map(_.print).getOrElse(""),
      "Canonical" -> node.identity.identityLinks.canonical.map(_.print).getOrElse(""),
      "Same as" -> node.identity.identityLinks.sameAs.map(_.print).mkString(", "),
      "Equivalent to" -> node.identity.identityLinks.equivalentTo.map(_.print).mkString(", ")
    ))

  private def _knowledge_presentation_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodePresentation
  ): String =
    _field_table(Vector(
      "Default label" -> value.labels.default.getOrElse(""),
      "Localized labels" -> value.labels.localized.toVector.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(", "),
      "Alternative labels" -> value.labels.alternatives.mkString(", "),
      "Canonical name" -> value.names.canonical.getOrElse(""),
      "Aliases" -> value.names.aliases.mkString(", "),
      "Description" -> value.descriptions.default.getOrElse(""),
      "Localized descriptions" -> value.descriptions.localized.toVector.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(", ")
    ))

  private def _knowledge_semantics_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeSemantics
  ): String =
    _field_table(Vector(
      "Semantic types" -> value.semanticTypes.map(x => s"${x.system}:${x.name}:${x.status.print}").mkString(", "),
      "Roles" -> value.roles.toVector.sorted.mkString(", "),
      "Confidence" -> value.confidence.value.map(_.toString).orElse(value.confidence.status).getOrElse(""),
      "Confidentiality" -> Vector(value.confidentiality.status, value.confidentiality.visibility).flatten.mkString(", "),
      "Valid from" -> value.temporal.validFrom.map(_.toString).getOrElse(""),
      "Valid to" -> value.temporal.validTo.map(_.toString).getOrElse(""),
      "Observed at" -> value.temporal.observedAt.map(_.toString).getOrElse(""),
      "Lifecycle state" -> value.lifecycle.state.getOrElse("")
    ))

  private def _knowledge_structure_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeStructure
  ): String =
    _field_table(Vector(
      "Translations" -> value.correspondences.translations.map(_.nodeId.print).mkString(", "),
      "Localized versions" -> value.correspondences.localizedVersions.map(_.nodeId.print).mkString(", "),
      "Same concepts" -> value.correspondences.sameConcepts.map(_.nodeId.print).mkString(", "),
      "Same resources" -> value.correspondences.sameResources.map(_.nodeId.print).mkString(", "),
      "Source alignments" -> value.correspondences.sourceAlignments.map(_.nodeId.print).mkString(", "),
      "Aliases" -> value.correspondences.aliases.map(_.nodeId.print).mkString(", "),
      "Primary classification" -> value.classifications.primary.map(_.print).getOrElse(""),
      "Broader" -> value.classifications.broader.map(_.print).mkString(", "),
      "Narrower" -> value.classifications.narrower.map(_.print).mkString(", "),
      "Additional classifications" -> value.classifications.additional.map(_.print).mkString(", "),
      "Parent" -> value.hierarchy.parent.map(_.print).getOrElse(""),
      "Children" -> value.hierarchy.children.map(_.print).mkString(", "),
      "Part of" -> value.partWhole.partOf.map(_.print).mkString(", "),
      "Has part" -> value.partWhole.hasPart.map(_.print).mkString(", "),
      "Member of" -> value.partWhole.memberOf.map(_.print).mkString(", "),
      "Has member" -> value.partWhole.hasMember.map(_.print).mkString(", ")
    ))

  private def _knowledge_bindings_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeBindings
  ): String =
    _field_table(Vector(
      "Entity bindings" -> value.entityBindings.map(x => Vector(x.component, Some(x.entityName), Some(x.entityId), x.entityVersion).flatten.mkString(":")).mkString(", "),
      "Tag bindings" -> value.tagBindings.map(x => s"${x.tagSpace}:${x.tagId}").mkString(", ")
    ))

  private def _knowledge_similarity_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeSimilarity
  ): String =
    _field_table(Vector(
      "Status" -> value.status.print,
      "Representations" -> value.representations.map(x => Vector(x.method, x.model, x.metric, x.context, x.payloadReference).flatten.mkString(":")).mkString(", "),
      "Search entries" -> value.searchEntries.map(x => Vector(x.provider, x.collection, x.searchId, x.indexedAt.map(_.toString)).flatten.mkString(":")).mkString(", ")
    ))

  private def _knowledge_operations_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeOperations
  ): String =
    _field_table(Vector(
      "Materialized at" -> value.materializedAt.map(_.toString).getOrElse(""),
      "Frame ids" -> value.frameIds.map(_.print).mkString(", "),
      "Validation status" -> value.validationStatus.print
    ))

  private def _knowledge_external_identifier_table(
    ids: Vector[org.goldenport.cncf.knowledge.ExternalKnowledgeIdentifier]
  ): String =
    if (ids.isEmpty)
      _admin_empty_state("No external identifiers.")
    else {
      val rows = ids.sortBy(_.key).map { id =>
        s"""<tr><td>${_escape(id.system)}</td><td>${_escape(id.kind.getOrElse(""))}</td><td><code>${_escape(id.value)}</code></td><td><code>${_escape(id.key)}</code></td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>System</th><th>Kind</th><th>Value</th><th>Key</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _knowledge_evidence_table(
    evidence: Vector[org.goldenport.cncf.knowledge.KnowledgeEvidence]
  ): String =
    if (evidence.isEmpty)
      _admin_empty_state("No evidence linked to this node projection.")
    else {
      val rows = evidence.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${_escape(item.id.print)}</code></td>
           |  <td>${_escape(item.kind)}</td>
           |  <td>${_escape(item.source.kind)}</td>
           |  <td><code>${_escape(item.source.value)}</code></td>
           |  <td>${_escape(item.summary.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Evidence</th><th>Kind</th><th>Source kind</th><th>Source</th><th>Summary</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _knowledge_provenance_table(
    provenance: Vector[org.goldenport.cncf.knowledge.KnowledgeProvenance]
  ): String =
    if (provenance.isEmpty)
      _admin_empty_state("No provenance linked to this node projection.")
    else {
      val rows = provenance.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${_escape(item.id.print)}</code></td>
           |  <td>${_escape(item.origin)}</td>
           |  <td>${_escape(item.owner.getOrElse(""))}</td>
           |  <td>${_escape(item.generatedBy.getOrElse(""))}</td>
           |  <td>${_escape(item.confidence.map(_.toString).getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Provenance</th><th>Origin</th><th>Owner</th><th>Generated by</th><th>Confidence</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _observability_admin_page(subsystem: Subsystem): String = {
    val runtime = RuntimeConfig.from(subsystem.configuration)
    val config = runtime.diagnosticPayloadExternalizationConfig
    val scopeCards = RuntimeDashboardMetrics.diagnosticScopes.map(_observability_scope_card).mkString("\n")
    _simple_page(
      title = "System Observability",
      subtitle = "Structured diagnostic drill-down and diagnostic payload references",
      body =
        s"""${_admin_nav_card(Vector(
             "Performance summary" -> "/web/system/performance",
             "Metrics" -> "/web/system/admin/observability/metrics",
             "All diagnostics" -> "/web/system/admin/observability/diagnostics",
             "System admin" -> "/web/system/admin"
           ))}
           |<div class="row g-3 mb-3">${scopeCards}</div>
           |${_admin_card("Diagnostic Payload Externalization", _payload_externalization_status(config, runtime.operationMode))}
           |${_admin_card(
             "Safety policy",
             """<p class="mb-2">Diagnostic records are grouped by structured diagnostic key. Message text is evidence only and is not used for grouping.</p>
               |<p class="mb-0">Large payloads are summarized or linked. Payload bytes are resolved only through the system-admin payload route.</p>""".stripMargin
           )}""".stripMargin
    )
  }

  private def _observability_metrics_page(subsystem: Subsystem): String = {
    val snapshot = RuntimeDashboardMetrics.runtimeMetricsSnapshot(subsystem.entityAccessMetrics)
    val scopeCards = snapshot.catalog.map { scope =>
      val points = snapshot.points.filter(_.scope == scope.scope)
      _admin_card(
        scope.label,
        s"""<p class="mb-2">${_escape(scope.description)}</p>
           |<p><span class="badge text-bg-secondary">${points.map(_.count).sum}</span> event(s).</p>
           |<p class="text-secondary mb-0">Labels: ${_escape(scope.labelKeys.mkString(", "))}</p>""".stripMargin,
        Some(s"metrics-${scope.scope.replace('.', '-')}")
      )
    }.mkString("\n")
    val metricTables = snapshot.catalog.map { scope =>
      val points = snapshot.points.filter(_.scope == scope.scope)
      _admin_card(
        scope.label,
        _metrics_points_table(points),
        Some(s"metrics-table-${scope.scope.replace('.', '-')}")
      )
    }.mkString("\n")
    _simple_page(
      title = "Observability Metrics",
      subtitle = "Low-cardinality runtime metrics for dashboard and admin use",
      body =
        s"""${_admin_nav_card(Vector(
             "Observability home" -> "/web/system/admin/observability",
             "Performance summary" -> "/web/system/performance",
             "Diagnostics" -> "/web/system/admin/observability/diagnostics"
           ))}
           |<div class="row g-3 mb-3">${scopeCards}</div>
           |${_admin_card(
             "Metric label policy",
             """<p class="mb-2">Metric labels are operational grouping hints, not error semantics.</p>
               |<p class="mb-0">High-cardinality identifiers such as paths, entity ids, job ids, payload ids, request parameters, and user/session ids are excluded from default metric labels.</p>""".stripMargin
           )}
           |${metricTables}
           |${_manual_raw_details("metrics snapshot", snapshot.toRecord)}""".stripMargin
    )
  }

  private def _observability_diagnostics_page(): String = {
    val scopes = RuntimeDashboardMetrics.diagnosticScopes
    val cards = scopes.map { scope =>
      _admin_card(
        scope.label,
        s"""<p><span class="badge text-bg-secondary">${scope.totalCount}</span> diagnostic event(s).</p>
           |${_observability_diagnostic_group_table(scope)}""".stripMargin,
        Some(s"observability-${scope.scope}")
      )
    }.mkString("\n")
    _simple_page(
      title = "Observability Diagnostics",
      subtitle = "Structured diagnostic groups by scope",
      body =
        s"""${_admin_nav_card(Vector(
             "Observability home" -> "/web/system/admin/observability",
             "Performance summary" -> "/web/system/performance"
           ))}
           |${cards}""".stripMargin
    )
  }

  private def _observability_diagnostic_detail_page(
    group: RuntimeDashboardMetrics.DiagnosticGroup
  ): String = {
    val record = group.latestRecord
    val structured = record.map(_observability_structured_fields).getOrElse(_admin_empty_state("No structured diagnostic record has been captured for this key."))
    val examples = _observability_examples_table(group.recentExamples)
    val previous = record.map(_previous_chain_html).getOrElse("")
    val payloads = record.map(_payload_references_html).getOrElse("")
    val raw = record.map(_manual_raw_details("diagnostic record", _)).getOrElse("")
    _simple_page(
      title = s"${group.label} Diagnostic ${group.diagnosticKey}",
      subtitle = "Structured diagnostic facts, source-error chain, and payload references",
      body =
        s"""${_admin_nav_card(Vector(
             "Observability diagnostics" -> "/web/system/admin/observability/diagnostics",
             "Observability home" -> "/web/system/admin/observability",
             "Performance summary" -> "/web/system/performance"
           ))}
           |${_admin_card(
             "Summary",
             _field_table(Vector(
               "Scope" -> group.label,
               "Diagnostic key" -> group.diagnosticKey,
               "Recent count" -> group.count.toString
             ))
           )}
           |${_admin_card("Structured fields", structured)}
           |${_admin_card("Recent examples", examples)}
           |${_admin_card("Source-error trace", previous)}
           |${_admin_card("Payload references", payloads)}
           |${raw}""".stripMargin
    )
  }

  private def _observability_scope_card(
    scope: RuntimeDashboardMetrics.DiagnosticScope
  ): String =
    s"""<div class="col-12 col-md-6 col-xl-3">
       |  ${_admin_card(
             scope.label,
             s"""<p><span class="badge text-bg-secondary">${scope.totalCount}</span> diagnostic event(s).</p>
                |${_admin_action_row(Vector("Open diagnostics" -> s"/web/system/admin/observability/diagnostics#observability-${scope.scope}"), primary = false)}""".stripMargin,
             Some(s"observability-card-${scope.scope}")
           )}
       |</div>""".stripMargin

  private def _metrics_points_table(
    points: Vector[RuntimeMetricPoint]
  ): String =
    if (points.isEmpty)
      _admin_empty_state("No metrics have been recorded.")
    else {
      val rows = points.sortBy(x => (x.name, x.labels.toVector.sortBy(_._1).mkString("|"))).map { point =>
        val labels = _metrics_labels_html(point)
        val duration = point.durationAvgMillis
          .map(avg => s"avg ${avg} ms, min ${point.durationMinMillis.getOrElse(0L)} ms, max ${point.durationMaxMillis.getOrElse(0L)} ms")
          .getOrElse("")
        s"""<tr>
           |  <td><code>${_escape(point.name)}</code></td>
           |  <td>${labels}</td>
           |  <td>${point.count}</td>
           |  <td>${point.errorCount}</td>
           |  <td>${_escape(duration)}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Metric</th><th>Labels</th><th>Count</th><th>Errors</th><th>Duration</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _metrics_labels_html(
    point: RuntimeMetricPoint
  ): String =
    if (point.labels.isEmpty)
      ""
    else
      point.labels.toVector.sortBy(_._1).map {
        case ("diagnostic_key", value) =>
          _metric_diagnostic_link(point.scope, value)
        case (key, value) =>
          s"""<span class="badge text-bg-light border me-1">${_escape(key)}=${_escape(value)}</span>"""
      }.mkString

  private def _metric_diagnostic_link(
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
        val href = s"/web/system/admin/observability/diagnostics/${_escape_path_segment(scope)}/${_escape_path_segment(diagnosticKey)}"
        s"""<a class="badge text-bg-light border me-1" href="${_escape(href)}">diagnostic_key=${_escape(diagnosticKey)}</a>"""
      case None =>
        s"""<span class="badge text-bg-light border me-1">diagnostic_key=${_escape(diagnosticKey)}</span>"""
    }
  }

  private def _payload_externalization_status(
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
    _field_table(rows)
  }

  private def _observability_diagnostic_group_table(
    scope: RuntimeDashboardMetrics.DiagnosticScope
  ): String =
    if (scope.groups.isEmpty)
      _admin_empty_state("No diagnostics have been recorded.")
    else {
      val rows = scope.groups.map { group =>
        val href = s"/web/system/admin/observability/diagnostics/${_escape_path_segment(group.scope)}/${_escape_path_segment(group.diagnosticKey)}"
        val latest = group.latestRecord.map(_diagnostic_record_compact).getOrElse("")
        s"""<tr>
           |  <td><a href="${_escape(href)}"><code>${_escape(group.diagnosticKey)}</code></a></td>
           |  <td>${group.count}</td>
           |  <td>${latest}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Diagnostic</th><th>Count</th><th>Latest structured fields</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _observability_structured_fields(record: Record): String = {
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
    val rows = keys.flatMap(key => _record_string(record, key).filter(_.nonEmpty).map(key -> _))
    if (rows.isEmpty)
      _admin_empty_state("No structured diagnostic fields are available.")
    else
      _field_table(rows)
  }

  private def _diagnostic_record_compact(record: Record): String = {
    val xs = Vector(
      _record_string(record, "webStatus"),
      _record_string(record, "statusText"),
      _record_string(record, "detailCode").map(x => s"detailCode $x"),
      _record_string(record, "taxonomy"),
      _record_string(record, "causeKind").map(x => s"cause $x")
    ).flatten.filter(_.nonEmpty)
    xs.map(x => s"<span class=\"badge text-bg-light border me-1\">${_escape(x)}</span>").mkString
  }

  private def _observability_examples_table(
    examples: Vector[RuntimeDashboardMetrics.DiagnosticExample]
  ): String =
    if (examples.isEmpty)
      _admin_empty_state("No recent examples are available.")
    else {
      val rows = examples.map { example =>
        s"""<tr>
           |  <td>${_escape(_instant_text(example.observedAt))}</td>
           |  <td>${_escape(example.operation.getOrElse(""))}</td>
           |  <td>${_escape(example.kind.getOrElse(""))}</td>
           |  <td>${_escape(example.sourceMode.getOrElse(""))}</td>
           |  <td>${_escape(example.backend.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Observed at</th><th>Operation</th><th>Kind</th><th>Source</th><th>Backend</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _previous_chain_html(record: Record): String = {
    val previous = _record_seq(record.getAny("previous"))
    if (previous.isEmpty)
      _admin_empty_state("No previous Conclusion chain is attached.")
    else
      previous.zipWithIndex.map {
        case (entry, index) =>
          s"""<details class="mb-2">
             |  <summary>Source error ${index + 1}</summary>
             |  <div class="mt-2">${_observability_structured_fields(entry)}</div>
             |  ${_manual_raw_details(s"source error ${index + 1}", entry)}
             |</details>""".stripMargin
      }.mkString("\n")
  }

  private def _payload_references_html(record: Record): String = {
    val refs = _payload_references(record)
    if (refs.isEmpty)
      _admin_empty_state("No payload references are attached.")
    else {
      val rows = refs.map {
        case (path, href) =>
          val value =
            if (href.startsWith("/web/system/admin/observability/payloads/"))
              s"""<a href="${_escape(href)}"><code>${_escape(href)}</code></a>"""
            else
              s"<code>${_escape(href)}</code>"
          s"<tr><th>${_escape(path)}</th><td>${value}</td></tr>"
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
  }

  private def _payload_references(record: Record): Vector[(String, String)] =
    _payload_references_from_any("diagnostic", record).distinct

  private def _payload_references_from_any(
    path: String,
    value: Any
  ): Vector[(String, String)] =
    value match {
      case Some(x) => _payload_references_from_any(path, x)
      case r: Record =>
        val direct = _diagnostic_payload_reference(r).toVector.flatMap { ref =>
          ref.href.orElse(ref.url).orElse(ref.path).orElse(ref.ref).map(path -> _)
        }
        direct ++ r.asMap.toVector.flatMap {
          case (key, v) => _payload_references_from_any(s"$path.${key.toString}", v)
        }
      case xs: Seq[?] =>
        xs.toVector.zipWithIndex.flatMap { case (x, i) => _payload_references_from_any(s"$path[$i]", x) }
      case xs: Array[?] =>
        xs.toVector.zipWithIndex.flatMap { case (x, i) => _payload_references_from_any(s"$path[$i]", x) }
      case _ =>
        Vector.empty
    }

  private def _diagnostic_payload_reference(record: Record): Option[DiagnosticPayloadReference] = {
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

  private def _record_string(
    record: Record,
    key: String
  ): Option[String] =
    record.getAny(key).map(_display_value).map(_.trim).filter(_.nonEmpty)

  private def _jobs_json(
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
    s"""{"running":${running},"queued":${queued},"completed":${completed},"failed":${failed},"total":${total},"summary":${_summary_json(summary)}}"""
  }

  private def _summary_table(summary: RuntimeDashboardMetrics.CountSummary): String =
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <thead><tr><th>Window</th><th>Count</th><th>Errors</th></tr></thead>
       |  <tbody>
       |    ${_summary_row("Total", summary.cumulative)}
       |    ${_summary_row("1 day", summary.day)}
       |    ${_summary_row("1 hour", summary.hour)}
       |    ${_summary_row("1 minute", summary.minute)}
       |  </tbody>
       |</table></div>""".stripMargin

  private def _summary_row(
    label: String,
    window: RuntimeDashboardMetrics.CountWindow
  ): String =
    s"<tr><td>${_escape(label)}</td><td>${window.total}</td><td>${window.errors}</td></tr>"

  private def _diagnostics_table(
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
            val href = s"/web/system/admin/observability/diagnostics/${_escape_path_segment(s)}/${_escape_path_segment(kind)}"
            s"""<a href="${_escape(href)}"><code>${_escape(kind)}</code></a>"""
          }.getOrElse(s"<code>${_escape(kind)}</code>")
          val detail = records.get(kind).map { record =>
            val status = record.getInt("webStatus").map(_.toString).getOrElse("")
            val detailCode = record.getAny("detailCode").map(_.toString).getOrElse("")
            val taxonomy = record.getString("taxonomy").getOrElse("")
            val cause = record.getString("causeKind").getOrElse("")
            Vector(status, detailCode, taxonomy, cause).filter(_.nonEmpty).map(_escape).mkString("<br>")
          }.getOrElse("")
          s"<tr><td>${diagnosticLabel}</td><td>${count}</td><td>${detail}</td></tr>"
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Diagnostic</th><th>Count</th><th>Structured detail</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _jobs_table(jobs: (Int, Int, Int, Int)): String = {
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

  private def _latency_table(recent: Vector[RuntimeDashboardMetrics.RequestEntry]): String = {
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

  private def _recent_requests_table(recent: Vector[RuntimeDashboardMetrics.RequestEntry]): String =
    _request_table(recent.reverse, "No recent requests")

  private def _recent_errors_table(recent: Vector[RuntimeDashboardMetrics.RequestEntry]): String =
    _request_table(recent.filter(_.status >= 400).reverse, "No recent errors")

  private def _request_table(
    requests: Vector[RuntimeDashboardMetrics.RequestEntry],
    emptyMessage: String
  ): String =
    if (requests.isEmpty) {
      _web_empty_state(emptyMessage)
    } else {
      val rows = requests.map { x =>
        s"""<tr><td>${_escape(_instant_text(x.observedAt))}</td><td>${_escape(x.method)}</td><td><code>${_escape(x.path)}</code></td><td>${x.status}</td><td>${x.elapsedMillis} ms</td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Observed at</th><th>Method</th><th>Path</th><th>Status</th><th>Elapsed</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  private def _instant_text(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis).toString

  private def _component_json(component: Component): String = {
    val services = component.protocol.services.services.map { service =>
      val operations = service.operations.operations.toVector.map { operation =>
        val path = NamingConventions.toNormalizedPath(component.name, service.name, operation.name)
        s"""{"name":"${_json(operation.name)}","path":"${_json(path)}"}"""
      }.mkString("[", ",", "]")
      s"""{"name":"${_json(service.name)}","operationCount":${service.operations.operations.length},"operations":${operations}}"""
    }.mkString("[", ",", "]")
    val operationCount = component.protocol.services.services.map(_.operations.operations.length).sum
    s"""{"name":"${_json(component.name)}","version":${component.artifactMetadata.map(_.version).map(v => "\"" + _json(v) + "\"").getOrElse("null")},"serviceCount":${component.protocol.services.services.size},"operationCount":${operationCount},"services":${services}}"""
  }

  private def _component_reference_list(components: Vector[Component]): String =
    s"""<div class="list-group">
       |${components.map { component =>
         val componentPath = NamingConventions.toNormalizedSegment(component.name)
         s"""  <div class="list-group-item">
            |    <div class="d-flex flex-wrap justify-content-between gap-2 align-items-center">
            |      <strong>${_escape(component.name)}</strong>
            |      ${_admin_action_row(Vector(
              "Dashboard" -> s"/web/${componentPath}/dashboard",
              "Admin" -> s"/web/${componentPath}/admin",
              "Forms" -> s"/form/${componentPath}"
            ), primary = false)}
            |    </div>
            |  </div>""".stripMargin
       }.mkString("\n")}
       |</div>""".stripMargin

  private def _component_form_list(components: Vector[Component]): String =
    s"""<div class="list-group">
       |${components.map { component =>
         val componentPath = NamingConventions.toNormalizedSegment(component.name)
         s"""  <a class="list-group-item list-group-item-action d-flex flex-wrap justify-content-between gap-2 align-items-center" href="/form/${componentPath}">
            |    <strong>${_escape(component.name)}</strong>
            |    <span class="badge text-bg-secondary">Operation forms</span>
            |  </a>""".stripMargin
       }.mkString("\n")}
       |</div>""".stripMargin

  private def _job_metrics(subsystem: Subsystem): (Int, Int, Int, Int) =
    subsystem.jobEngine.metrics.map(x => (x.running, x.queued, x.completed, x.failed)).getOrElse((0, 0, 0, 0))

  private def _job_metrics(component: Component): (Int, Int, Int, Int) =
    component.jobEngine.metrics.map(x => (x.running, x.queued, x.completed, x.failed)).getOrElse((0, 0, 0, 0))

  private def _assembly_warning_count(subsystem: Subsystem): Int =
    _assembly_report(subsystem).warnings.size

  private def _assembly_report(
    subsystem: Subsystem
  ): org.goldenport.cncf.assembly.AssemblyReport =
    org.goldenport.cncf.context.GlobalRuntimeContext.current
      .orElse(scala.util.Try(subsystem.globalRuntimeContext).toOption)
      .map(_.assemblyReport)
      .getOrElse(new org.goldenport.cncf.assembly.AssemblyReport)

  private def _escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  private def _title_label(
    segment: String
  ): String =
    segment
      .split("[-_]")
      .toVector
      .filter(_.nonEmpty)
      .map(x => x.head.toUpper + x.tail)
      .mkString(" ")

  private def _json(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
}
