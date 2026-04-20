package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.projection.{DescribeProjection, HelpProjection, SchemaProjection}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.Json
import io.circe.parser.parse

/*
 * @since   Apr. 12, 2026
 * @version Apr. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object StaticFormAppRenderer {
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
    values: Map[String, String] = Map.empty
  ) {
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
    defaultTableView: String = WebTableColumnResolver.defaultViewName
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
        "paging.page" -> page.values.getOrElse("paging.page", "1"),
        "paging.pageSize" -> page.values.getOrElse("paging.pageSize", "20"),
        "paging.chunkSize" -> page.values.getOrElse("paging.chunkSize", "1000"),
        "paging.href" -> page.values.getOrElse("paging.href", _default_paging_href)
      ) ++ metadata.toTemplateValues ++ _detail_action_values(metadata) ++ _job_action_values(metadata)
      val base = page.copy(values = page.values ++ formValues ++ resultValues)
      if (status >= 400)
        base
          .withValue("error.status", status.toString)
          .withValue("error.body", body)
      else
        base

    private def _default_paging_href: String =
      s"/form/${componentName}/${serviceName}/${operationName}/result?page={page}&pageSize={pageSize}"

    private def _job_action_values(metadata: FormResultMetadata): Map[String, String] =
      metadata.jobId match {
        case Some(jobid) =>
          val href = page.values.getOrElse("result.job.href", s"/form/${componentName}/${serviceName}/${operationName}/jobs/${jobid}/await")
          val common = Map(
            "result.job.href" -> href,
            "result.job.status" -> metadata.jobStatus.getOrElse("accepted"),
            "result.job.message" -> metadata.message.getOrElse("Command accepted."),
            "result.action.await.name" -> "await",
            "result.action.await.label" -> "Check result",
            "result.action.await.href" -> href,
            "result.action.await.method" -> "POST"
          )
          if (metadata.actions.isEmpty)
            common ++ Map(
              "result.actions.count" -> "1",
              "result.action.0.name" -> "await",
              "result.action.0.label" -> "Check result",
              "result.action.0.href" -> href,
              "result.action.0.method" -> "POST",
              "result.action.primary.name" -> "await",
              "result.action.primary.label" -> "Check result",
              "result.action.primary.href" -> href,
              "result.action.primary.method" -> "POST"
            )
          else
            common
        case None =>
          Map.empty
      }

    private def _detail_action_values(metadata: FormResultMetadata): Map[String, String] =
      metadata.id.flatMap(id => _detail_operation_name.map(_ -> id)) match {
        case Some((detailOperation, id)) =>
          val href = s"/form/${componentName}/${serviceName}/${detailOperation}/result?id=${_escape_query(id)}"
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
    webSchema: WebSchemaResolver.ResolvedWebSchema
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

  def render(
    subsystem: Subsystem,
    app: String,
    page: Vector[String] = Vector.empty,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] = {
    if (!webDescriptor.isAppEnabled(app, page))
      return None
    page match {
      case Vector() if app == "manual" =>
        Some(renderSystemManual(subsystem))
      case Vector() if app == "console" =>
        Some(renderSystemConsole(subsystem))
      case Vector("dashboard") =>
        _find_component(subsystem, app).map(renderComponentDashboard)
      case _ =>
        None
    }
  }

  def renderFormIndex(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val services = component.protocol.services.services.map { service =>
        val operations = service.operations.operations.toVector.filter { operation =>
          webDescriptor.isFormEnabled(_operation_selector(component.name, service.name, operation.name))
        }.map { operation =>
          val path = s"/form/${componentPath}/${NamingConventions.toNormalizedSegment(service.name)}/${NamingConventions.toNormalizedSegment(operation.name)}"
          s"""<li><a href="${_escape(path)}">${_escape(operation.name)}</a></li>"""
        }.mkString("\n")
        s"""<section><h2>${_escape(service.name)}</h2><ul>${operations}</ul></section>"""
      }.mkString("\n")
      Page(_simple_page(
        title = s"${_escape(component.name)} Forms",
        subtitle = "HTML form operations",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/dashboard">Dashboard</a> · <a href="/web/${componentPath}/admin">Admin configuration</a></p>
             |</article>
             |${services}""".stripMargin
      ))
    }

  def renderOperationForm(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    values: Map[String, String] = Map.empty,
    validation: Option[FormValidationResult] = None
  ): Option[Page] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor).map { context =>
      val action = s"/form/${context.componentPath}/${context.servicePath}/${context.operationPath}"
      val effectiveValues = _operation_form_prefill_values(subsystem, context, values)
      val effectiveValidation = validation.filter(_.webSchema.selector == context.webSchema.selector)
      val controls = _operation_form_controls(context.webSchema, effectiveValues, effectiveValidation)
      val hiddenContext = _hidden_form_context_inputs(effectiveValues)
      val errorPanel = _form_error_panel(effectiveValues) + _form_validation_panel(effectiveValidation)
      Page(_simple_page(
        title = s"${_escape(context.component.name)}.${_escape(context.serviceName)}.${_escape(context.operationName)}",
        subtitle = "HTML form operation",
        body =
          s"""<article>
             |  ${errorPanel}
             |  <form method="post" action="${_escape(action)}">
             |    ${controls}
             |    ${hiddenContext}
             |    <button type="submit" class="btn btn-primary">Run</button>
             |    <a class="btn btn-outline-secondary" href="/form/${context.componentPath}">Operations</a>
             |  </form>
             |</article>""".stripMargin
      ))
    }

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
          )
        )
      }

  def renderComponentAdminEntityFormDefinition(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      .map(context => _form_validation_json(_validate_form(context.webSchema, values)))

  def validateOperationForm(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[FormValidationResult] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map(context => _validate_form(context.webSchema, values))

  def validateComponentAdminEntityForm(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    view: Option[String] = None
  ): Option[FormValidationResult] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    values: Map[String, String],
    validation: Option[FormValidationResult] = None
  ): String = {
    val fields = webSchema.fields
    if (fields.isEmpty)
      _operation_form_fields_textarea(_visible_form_values(values), "Fields")
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
      val extraValues = _visible_form_values(values).filterNot { case (key, _) => fieldNames.contains(key) }
      val extra =
        if (extraValues.isEmpty)
          _operation_form_fields_textarea(Map.empty, "Additional fields", rows = 3)
        else
          _operation_form_fields_textarea(extraValues, "Additional fields", rows = 3)
      s"""${controls}
         |${extra}""".stripMargin
    }
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
      if webDescriptor.isFormEnabled(_operation_selector(component.name, service.name, operation.name))
    } yield {
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val servicePath = NamingConventions.toNormalizedSegment(service.name)
      val operationPath = NamingConventions.toNormalizedSegment(operation.name)
      val formDescriptor = webDescriptor.form.get(_operation_selector(component.name, service.name, operation.name))
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
        _operation_selector(component.name, service.name, operation.name),
        operationParameters ++ cmlParameters,
        descriptorControls
      )
      OperationWebSchemaContext(
        component,
        service.name,
        operation.name,
        componentPath,
        servicePath,
        operationPath,
        webSchema
      )
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
    navigation: FormDefinitionNavigation
  ): Page =
    Page(Json.obj(
      "selector" -> Json.fromString(webSchema.selector),
      "surface" -> Json.fromString(webSchema.surface.name),
      "source" -> Json.fromString(webSchema.source.toString),
      "mode" -> Json.fromString(navigation.mode),
      "method" -> Json.fromString(navigation.method),
      "submitPath" -> Json.fromString(navigation.submitPath),
      "htmlPath" -> Json.fromString(navigation.htmlPath),
      "actions" -> Json.arr(navigation.actions.map(_form_definition_action_json)*),
      "fields" -> Json.arr(webSchema.fields.map(_web_field_json)*)
    ).noSpaces)

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
    val datatype = Option(parameter.domain.datatype).map(_.toString.toLowerCase).getOrElse("")
    if (datatype.contains("bool")) "checkbox"
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
    val displayLabel = label.getOrElse(name)
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
      val options = descriptor.toVector.flatMap(_.values).map { candidate =>
        val selected = if (candidate == value) " selected" else ""
        s"""<option value="${_escape(candidate)}"${selected}>${_escape(candidate)}</option>"""
      }.mkString("\n")
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
    val datatype = Option(parameter.domain.datatype).map(_.toString).getOrElse("unknown")
    val multiplicity = Option(parameter.domain.multiplicity).map(_.toString).getOrElse("unknown")
    s"${kind}; ${datatype}; ${multiplicity}"
  }

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
      s"""<article>
         |  <h2>$${operation.label} Result</h2>
         |  <p>Content-Type $${result.contentType}</p>
         |  <textus:job-ticket></textus:job-ticket>
         |  <textus-error-panel source="error"></textus-error-panel>
         |  <textus-result-view source="result.body"></textus-result-view>
         |  <textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus-result-table>
         |  <textus-form-link href="crud.success.href" label="Back to detail"></textus-form-link>
         |  <textus-property-list source="result"></textus-property-list>
         |  <h3>Submitted Values</h3>
         |  <textus-property-list source="form"></textus-property-list>
         |  <p><a href="/form/${_escape(NamingConventions.toNormalizedSegment(properties.componentName))}/${_escape(NamingConventions.toNormalizedSegment(properties.serviceName))}/${_escape(NamingConventions.toNormalizedSegment(properties.operationName))}">Run again</a> · <a href="/form/${_escape(NamingConventions.toNormalizedSegment(properties.componentName))}">Operations</a></p>
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
    if (_is_html_document(template))
      Page(_complete_widget_assets(template, rendered))
    else
      Page(_simple_page(
        title = s"${_escape(properties.operationLabel)} Result",
        subtitle = s"HTTP ${properties.status}",
        body = rendered
      ))
  }

  def renderErrorTemplate(
    app: Option[String],
    status: Int,
    message: String,
    path: String,
    template: String
  ): Page = {
    val appName = app.getOrElse("system")
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
        "result.status" -> status.toString,
        "result.ok" -> "false",
        "result.body" -> message
      )
    )
    val rendered = _render_template(template, properties, Map.empty)
    if (_is_html_document(template))
      Page(rendered)
    else
      Page(_simple_page(
        title = s"HTTP ${status}",
        subtitle = _escape(path),
        body = rendered
      ))
  }

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
      "result.action.primary.method" -> "POST"
    )

  private def _system_job_template(
    title: String
  ): String =
    s"""<article>
       |  <h2>${_escape(title)}</h2>
       |  <textus:job-ticket></textus:job-ticket>
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
    Page(_dashboard_shell(
      title = s"${_escape(component.name)} Dashboard",
      subtitle = "Component health",
      statePath = s"/web/${NamingConventions.toNormalizedSegment(component.name)}/dashboard/state"
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
      operationalDetails = Some(_system_admin_operational_details),
      componentFormsPath = None
    ))

  def renderSystemAdminDescriptor(
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(_simple_page(
      title = "System Web Descriptor",
      subtitle = "Management Console descriptor view",
      body =
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/admin">System admin</a> · <a href="/web/system/dashboard">System dashboard</a></p>
           |</article>
           |<article>
           |  <h2>Completed Descriptor</h2>
           |  <p>The completed view applies framework defaults so the descriptor can be inspected as the runtime sees it.</p>
           |  <pre class="bg-light border rounded p-3"><code>${_escape(_web_descriptor_json(webDescriptor, completed = true))}</code></pre>
           |</article>
           |<article>
           |  <h2>Configured Descriptor</h2>
           |  <p>The configured view keeps explicit descriptor entries for comparison.</p>
           |  <pre class="bg-light border rounded p-3"><code>${_escape(_web_descriptor_json(webDescriptor, completed = false))}</code></pre>
           |</article>""".stripMargin
    ))

  def renderComponentAdmin(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map(renderComponentAdmin(_, webDescriptor))

  def renderComponentAdminDescriptor(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      Page(_simple_page(
        title = s"${_escape(component.name)} Web Descriptor",
        subtitle = "Component Management Console descriptor view",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/web/system/admin/descriptor">System descriptor</a></p>
             |</article>
             |<article>
             |  <h2>Completed Descriptor</h2>
             |  <p>The completed view applies framework defaults and resolves component route placeholders for this component.</p>
             |  <pre class="bg-light border rounded p-3"><code>${_escape(_web_descriptor_json(webDescriptor, completed = true, componentSegment = Some(componentPath)))}</code></pre>
             |</article>
             |<article>
             |  <h2>Configured Descriptor</h2>
             |  <p>The configured view keeps explicit descriptor entries for comparison.</p>
             |  <pre class="bg-light border rounded p-3"><code>${_escape(_web_descriptor_json(webDescriptor, completed = false))}</code></pre>
             |</article>""".stripMargin
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
          title = "System Manual",
          subtitle = "Read-only runtime reference",
          body = """<article><h2>No components</h2><p>No component reference entries are available.</p></article>"""
        ))
    }

  def renderComponentManual(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
    } yield Page(_manual_page(
      title = s"${_escape(component.name)} Manual",
      subtitle = "Component reference",
      component = component,
      selector = Some(component.name),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(component.name)}/manual",
      childNames = component.protocol.services.services.map(_.name).toVector
    ))

  def renderComponentManualService(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, serviceName))
    } yield Page(_manual_page(
      title = s"${_escape(component.name)}.${_escape(service.name)} Manual",
      subtitle = "Service reference",
      component = component,
      selector = Some(s"${component.name}.${service.name}"),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(component.name)}/manual/${NamingConventions.toNormalizedSegment(service.name)}",
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
      title = s"${_escape(component.name)}.${_escape(service.name)}.${_escape(operation.name)} Manual",
      subtitle = "Operation reference",
      component = component,
      selector = Some(s"${component.name}.${service.name}.${operation.name}"),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(component.name)}/manual/${NamingConventions.toNormalizedSegment(service.name)}/${NamingConventions.toNormalizedSegment(operation.name)}",
      childNames = Vector.empty
    ))

  def renderComponentAdminEntities(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val rows = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).map { descriptor =>
        val entityPath = NamingConventions.toNormalizedSegment(descriptor.entityName)
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/entities/${entityPath}">${_escape(descriptor.entityName)}</a></td>
           |  <td><code>${_escape(descriptor.collectionId.name)}</code></td>
           |  <td>${_escape(descriptor.usageKind.toString)}</td>
           |  <td>${_escape(descriptor.operationKind.toString)}</td>
           |  <td>${_escape(descriptor.applicationDomain.toString)}</td>
           |  <td>${descriptor.workingSet.map(_.entityIds.size.toString).getOrElse("none")}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          _admin_empty_state("No entity runtime descriptors are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>Entity</th><th>Collection</th><th>Usage</th><th>Operation</th><th>Domain</th><th>Working set</th></tr></thead>
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
             |${_admin_card("Entity CRUD", body)}""".stripMargin
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
      val result = _admin_entity_list(subsystem, componentPath, entityPath, effectivePageRequest)
      val warningHtml = _admin_warnings(result.warnings)
      val table = _admin_read_result_list_table(
        result.items,
        displayFields,
        basePath,
        "No records are currently available for this entity.",
        includeEdit = true,
        linkContext = pageContext ++ Map(
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
             |    ${_paging_nav(result.page, result.pageSize, result.total, effectivePageRequest.href(basePath), Some(result.hasNext))}
             |  </div>
             |</article>""".stripMargin
      ))
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val routeId = _entity_route_id(id)
      val querySuffix = _hidden_form_context_query_suffix(values)
      val body = _admin_entity_record_table(subsystem, component, componentPath, entityPath, id, webDescriptor)
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
             |</article>""".stripMargin
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
             |    <form method="post" action="${_escape(actionPath)}" class="admin-form">
             |      ${controls}
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
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
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
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="${_escape(webBasePath)}/${_escape(id)}">Detail</a> · <a href="${_escape(webBasePath)}">Back to ${_escape(entityLabel)} records</a> · <a href="${_escape(webBasePath)}/${_escape(id)}/edit">Edit again</a></p>
           |</article>
           |<article>
           |  <h2>Update submitted</h2>
           |  <p>${_escape(message)}</p>
           |  <div class="table-responsive"><table class="table table-sm">
           |    <thead>${_admin_result_rows(applied, resultStatus, message)}</thead>
           |    <tbody>${rows}</tbody>
           |  </table></div>
           |</article>""".stripMargin
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
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="${_escape(webBasePath)}">Back to ${_escape(entityLabel)} records</a> · <a href="${_escape(webBasePath)}/new">Create another</a></p>
           |</article>
           |<article>
           |  <h2>Create submitted</h2>
           |  <p>${_escape(message)}</p>
           |  <div class="table-responsive"><table class="table table-sm">
           |    <thead>${_admin_result_rows(applied, resultStatus, message)}</thead>
           |    <tbody>${rows}</tbody>
           |  </table></div>
           |</article>""".stripMargin
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
    pageRequest: PageRequest
  ): _AdminListResult =
    _admin_list_result(
      subsystem,
      "/admin/entity/list",
      Record.create(Vector("component" -> componentPath, "entity" -> entityPath, "view" -> WebTableColumnResolver.defaultViewName) ++ pageRequest.toPairs)
    )

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
      s"""<a class="btn btn-outline-secondary btn-sm" href="${_escape(href)}">${_escape(label)}</a>"""
    }.mkString("\n")
    s"""<article class="card admin-card admin-nav">
       |  <div class="card-body">
       |    <h2 class="card-title h5">Navigation</h2>
       |    <div class="d-flex flex-wrap gap-2">${items}</div>
       |  </div>
       |</article>""".stripMargin
  }

  private def _admin_card(
    title: String,
    body: String
  ): String =
    s"""<article class="card admin-card">
       |  <div class="card-body">
       |    <h2 class="card-title">${_escape(title)}</h2>
       |    ${body}
       |  </div>
       |</article>""".stripMargin

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
           |  <td>${_escape(definition.name)}</td>
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
             |${_admin_card("View read", body)}""".stripMargin
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
           |  <td>${_escape(definition.name)}</td>
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
             |${_admin_card("Aggregate CRUD", body)}""".stripMargin
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
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(_simple_page(
        title = s"${_escape(component.name)} Data Administration",
        subtitle = "Data CRUD management baseline",
        body =
          s"""${nav}
             |${_admin_card("Data CRUD", "Data CRUD execution is not enabled in this baseline. This page reserves the component-scoped management entry point for datastore records and document-level data operations.")}""".stripMargin
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
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="${_escape(webBasePath)}/${_escape(id)}">Detail</a> · <a href="${_escape(webBasePath)}">Back to ${_escape(_title_label(dataPath))} records</a> · <a href="${_escape(webBasePath)}/${_escape(id)}/edit">Edit again</a></p>
           |</article>
           |<article>
           |  <h2>Update submitted</h2>
           |  <p>${_escape(message)}</p>
           |  <div class="table-responsive"><table class="table table-sm">
           |    <thead>${_admin_result_rows(applied, resultStatus, message)}</thead>
           |    <tbody>${_submitted_fields_rows(values)}</tbody>
           |  </table></div>
           |</article>""".stripMargin
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
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="${_escape(webBasePath)}">Back to ${_escape(_title_label(dataPath))} records</a> · <a href="${_escape(webBasePath)}/new">Create another</a></p>
           |</article>
           |<article>
           |  <h2>Create submitted</h2>
           |  <p>${_escape(message)}</p>
           |  <div class="table-responsive"><table class="table table-sm">
           |    <thead>${_admin_result_rows(applied, resultStatus, message)}</thead>
           |    <tbody>${_submitted_fields_rows(values)}</tbody>
           |  </table></div>
           |</article>""".stripMargin
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
  ): Page = {
    val name = NamingConventions.toNormalizedSegment(component.name)
    Page(_admin_page(
      title = s"${_escape(component.name)} Admin Configuration",
      subtitle = "Current component runtime configuration",
      components = Vector(component),
      subsystemName = component.subsystem.map(_.name).getOrElse(component.name),
      subsystemVersion = component.subsystem.flatMap(_.version),
      dashboardPath = s"/web/${name}/dashboard",
      performancePath = "/web/system/performance",
      webDescriptor = webDescriptor,
      runtimeConfiguration = None,
      operationalDetails = None,
      componentFormsPath = Some(s"/form/${name}")
    ))
  }

  def renderSystemPerformance(subsystem: Subsystem): Page =
    Page(_performance_page(subsystem))

  def renderSystemConsole(subsystem: Subsystem): Page =
    Page(_simple_page(
      title = "System Console",
      subtitle = "Controlled operation entry",
      body =
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/dashboard">System dashboard</a> · <a href="/web/system/admin">Admin configuration</a> · <a href="/web/system/performance">Performance details</a> · <a href="/web/system/manual">Manual</a></p>
           |</article>
           |<article>
           |  <h2>Operation forms</h2>
           |  <p>Console links to operation forms. It does not execute operations inline.</p>
           |  ${_component_form_list(subsystem.components)}
           |</article>""".stripMargin
    ))

  private def _find_component(
    subsystem: Subsystem,
    name: String
  ): Option[Component] =
    subsystem.components.find(x => NamingConventions.equivalentByNormalized(x.name, name))

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
       |    .bars { display: grid; gap: 10px; }
       |    .bar { display: grid; grid-template-columns: minmax(120px, 220px) 1fr auto; gap: 10px; align-items: center; }
       |    .track { height: 10px; background: #e6ebf0; border-radius: 6px; overflow: hidden; }
       |    .fill { height: 100%; background: #2374ab; width: 0%; transition: width .25s ease; }
       |    .spark { height: 120px; display: grid; grid-template-columns: repeat(60, 1fr); gap: 3px; align-items: end; border-bottom: 1px solid #d9dee5; }
       |    .spark span { display: block; min-height: 2px; background: #2374ab; border-radius: 4px 4px 0 0; }
       |    .spark span.error { background: #c65454; }
       |    @keyframes pulse { 70% { box-shadow: 0 0 0 12px rgba(21,153,71,0); } 100% { box-shadow: 0 0 0 0 rgba(21,153,71,0); } }
       |    @media (max-width: 720px) { .bar { grid-template-columns: 1fr; } }
       |""".stripMargin,
      body =
        s"""|    <div class="status mb-3"><span class="pulse"></span><span id="statusText">Connecting</span></div>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-lg-4"><article id="healthPanel" class="card h-100 shadow-sm border-success"><div class="card-body"><h2 class="h5 card-title">Health</h2><div class="big"><span id="healthText" class="badge text-bg-success">UP</span></div><p class="text-secondary mb-0 mt-2" id="healthNote">Starting</p></div></article></div>
       |      <div class="col-12 col-lg-4"><article class="card h-100 shadow-sm"><div class="card-body"><h2 class="h5 card-title">Subsystem</h2><p class="mb-1"><strong id="subsystemName">-</strong></p><p class="text-secondary mb-0" id="subsystemVersion">-</p></div></article></div>
       |      <div class="col-12 col-lg-4"><article class="card h-100 shadow-sm"><div class="card-body"><h2 class="h5 card-title">CNCF</h2><p class="mb-1"><strong id="cncfVersion">-</strong></p><p class="mb-0"><a id="detailsLink" href="/web/system/admin">Admin details</a> · <a id="performanceLink" href="/web/system/performance">Performance details</a> · <a id="manualLink" href="/web/system/manual">Manual</a> · <a id="consoleLink" href="/web/console">Console</a></p></div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Components</span><strong id="componentCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Services</span><strong id="serviceCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Operations</span><strong id="operationCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">HTML requests</span><strong id="requestCount">0</strong><small class="text-secondary" id="requestErrors">errors 0</small></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Jobs</span><strong id="jobCount">0</strong><small class="text-secondary" id="jobErrors">errors 0</small></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100 shadow-sm"><div class="card-body"><span class="text-secondary">Assembly warnings</span><strong id="assemblyWarningCount">0</strong><small><a id="assemblyWarningsLink" href="/form/admin/assembly/warnings">details</a></small></div></div></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">Traffic</h2>
       |        <div class="btn-group mb-3" id="graphTabs" role="group">
       |          <button type="button" class="btn btn-outline-primary btn-sm" data-window="minute">1 minute</button>
       |          <button type="button" class="btn btn-primary btn-sm active" data-window="hour">1 hour</button>
       |          <button type="button" class="btn btn-outline-primary btn-sm" data-window="day">1 day</button>
       |        </div>
       |        <div class="spark" id="requestSpark"></div>
       |      </div></article></div>
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">Activity counts</h2>
       |        <div id="activityCounts"></div>
       |      </div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">ActionCall jobs</h2>
       |        <div class="bars" id="jobBars"></div>
       |      </div></article></div>
       |      <div class="col-12 col-xl-6"><article class="card h-100 shadow-sm"><div class="card-body">
       |        <h2 class="h5 card-title">Components</h2>
       |        <div class="bars" id="componentBars"></div>
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
       |      const health = (failedJobs > 0 || recentFailures > 0 || recentDenials > 0 || assemblyWarnings > 0) ? "WARN" : data.status;
       |      healthPanel.classList.toggle("border-success", health == "UP");
       |      healthPanel.classList.toggle("border-warning", health != "UP");
       |      healthText.className = health == "UP" ? "badge text-bg-success" : "badge text-bg-warning";
       |      healthText.textContent = health;
       |      healthNote.textContent = `jobs failed: $${failedJobs}, recent failures: $${recentFailures}, recent denials: $${recentDenials}, assembly warnings: $${assemblyWarnings}`;
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
       |      text.textContent = "UP · " + new Date(data.observedAt).toLocaleTimeString();
       |      const maxOps = Math.max(1, ...data.components.map(c => c.operationCount));
       |      componentBars.innerHTML = data.components.map(c => {
       |        const width = Math.round((c.operationCount / maxOps) * 100);
       |        return `<div class="bar"><span>$${escapeHtml(c.name)} $${escapeHtml(c.version || "")}</span><div class="track"><div class="fill" style="width:$${width}%"></div></div><span>$${c.operationCount}</span></div>`;
       |      }).join("");
       |      renderGraph();
       |      activityCounts.innerHTML = countTable(data);
       |      const jobTotal = Math.max(1, data.actions.jobs.total);
       |      jobBars.innerHTML = ["running","queued","completed","failed"].map(name => {
       |        const count = data.actions.jobs[name] || 0;
       |        const width = Math.round((count / jobTotal) * 100);
       |        return `<div class="bar"><span>$${name}</span><div class="track"><div class="fill" style="width:$${width}%"></div></div><span>$${count}</span></div>`;
       |      }).join("");
       |      configSummary.innerHTML = data.components.map(c => `<p><strong>$${escapeHtml(c.name)}</strong> $${escapeHtml(c.version || "unversioned")} · services $${c.serviceCount} · operations $${c.operationCount}</p>`).join("");
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
      s"""<article>
         |  <h2>${_escape(component.name)}</h2>
         |  <p>Version ${_escape(version)}</p>
         |  ${services}
         |</article>""".stripMargin
    }.mkString("\n")
    _simple_page(
      title = title,
      subtitle = subtitle,
      body =
        s"""<article>
           |  <h2>Runtime</h2>
           |  <div class="table-responsive"><table class="table table-sm">
           |    <tbody>
           |      <tr><th>CNCF version</th><td>${_escape(CncfVersion.current)}</td></tr>
           |      <tr><th>Subsystem</th><td>${_escape(subsystemName)}</td></tr>
           |      <tr><th>Subsystem version</th><td>${_escape(subsystemVersion.getOrElse("unversioned"))}</td></tr>
           |      <tr><th>Components</th><td>${components.size}</td></tr>
           |    </tbody>
           |  </table></div>
           |</article>
           |<article>
           |  <h2>Navigation</h2>
           |  <p><a href="${_escape(dashboardPath)}">Dashboard</a> · <a href="${_escape(performancePath)}">Performance details</a> · <a href="/web/system/manual">Manual</a> · <a href="/web/console">Console</a></p>
           |</article>
           |${_admin_operational_details(operationalDetails)}
           |${_component_admin_actions(componentFormsPath)}
           |<article>
           |  <h2>Web Descriptor</h2>
           |  ${_web_descriptor_summary(webDescriptor, descriptorPath)}
           |</article>
           |${_admin_runtime_configuration(runtimeConfiguration)}
           |${_admin_job_control(runtimeConfiguration)}
           |${componentBlocks}""".stripMargin
    )
  }

  private def _admin_job_control(
    configuration: Option[ResolvedConfiguration]
  ): String =
    configuration.map { _ =>
      """<article>
        |  <h2>Job Control</h2>
        |  <p>Job control entry points are reserved for system admin operations. The WEB-05 baseline is read-only and does not expose browser-side cancel, retry, or force-complete actions.</p>
        |  <ul>
        |    <li><a href="/form/admin/execution/history">Execution history</a></li>
        |    <li><a href="/form/admin/execution/calltree">Latest calltree</a></li>
        |  </ul>
        |  <p>Mutation actions must require explicit admin authorization, confirmation for destructive actions, and audit logging before they are enabled.</p>
        |</article>""".stripMargin
    }.getOrElse("")

  private def _admin_runtime_configuration(
    configuration: Option[ResolvedConfiguration]
  ): String =
    configuration.map { config =>
      s"""<article>
         |  <h2>Runtime Configuration</h2>
         |  <p>Resolved runtime configuration values are read-only. Sensitive values are masked.</p>
         |  <p>Configuration mutation must use a separate admin action surface with explicit admin authorization and audit logging.</p>
         |  ${_effective_runtime_configuration_table(config)}
         |  ${_runtime_configuration_table(config)}
         |</article>""".stripMargin
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
      s"""<article>
         |  <h2>Operational Details</h2>
         |  ${body}
         |</article>""".stripMargin
    }.getOrElse("")

  private def _system_admin_operational_details: String =
    """<div class="row g-3">
      |  <div class="col-12 col-lg-6">
      |    <section>
      |      <h3>Assembly</h3>
      |      <p><a href="/form/admin/assembly/warnings">Assembly warnings</a> · <a href="/form/admin/assembly/report">Assembly report</a></p>
      |    </section>
      |  </div>
      |  <div class="col-12 col-lg-6">
      |    <section>
      |      <h3>Execution</h3>
      |      <p><a href="/form/admin/execution/history">Execution history</a> · <a href="/form/admin/execution/calltree">Latest calltree</a></p>
      |    </section>
      |  </div>
      |</div>""".stripMargin

  private def _component_admin_actions(
    formsPath: Option[String]
  ): String =
    formsPath.map { path =>
      s"""<article>
         |  <h2>Component Operations</h2>
         |  <p><a href="${_escape(path)}">Operation forms</a></p>
         |  ${_component_admin_management_links(path)}
         |</article>""".stripMargin
    }.getOrElse("")

  private def _component_admin_management_links(
    formsPath: String
  ): String = {
    val componentPath = formsPath.stripPrefix("/form/")
    s"""<h3>Managed Data</h3>
       |<ul>
       |  <li><a href="/web/${_escape(componentPath)}/admin/entities">Entity CRUD</a></li>
       |  <li><a href="/web/${_escape(componentPath)}/admin/data">Data CRUD</a></li>
       |  <li><a href="/web/${_escape(componentPath)}/admin/aggregates">Aggregate CRUD</a></li>
       |  <li><a href="/web/${_escape(componentPath)}/admin/views">View read</a></li>
       |</ul>""".stripMargin
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
       |<p><a href="${_escape(descriptorPath)}">Completed descriptor JSON</a></p>
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
          s"""<tr><td><code>${_escape(selector)}</code></td><td>${_escape(admin.totalCount.name)}</td><td>${_escape(fields)}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Management Console Controls</h3>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Surface</th><th>Total count</th><th>Fields</th></tr></thead>
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
              "enabled" -> form.enabled.map(Json.fromBoolean).getOrElse(Json.Null)
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
                  "required" -> field.control.required.map(Json.fromBoolean).getOrElse(Json.Null),
                  "hidden" -> Json.fromBoolean(field.control.hidden),
                  "readonly" -> Json.fromBoolean(field.control.readonly),
                  "placeholder" -> field.control.placeholder.map(Json.fromString).getOrElse(Json.Null),
                  "help" -> field.control.help.map(Json.fromString).getOrElse(Json.Null),
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

  private def _web_descriptor_app_json(
    app: WebDescriptor.App
  ): Json = {
    val configured = Json.obj(
      "name" -> Json.fromString(app.name),
      "path" -> Json.fromString(app.path),
      "kind" -> Json.fromString(app.kind)
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
    val dslChokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val jobs = _job_metrics(subsystem)
    _simple_page(
      title = "System Performance",
      subtitle = "HTML request, ActionCall, authorization, and Jobs detail",
      body =
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/dashboard">System dashboard</a> · <a href="/web/system/admin">Admin configuration</a> · <a href="/web/system/manual">Manual</a> · <a href="/web/console">Console</a></p>
           |</article>
           |<article>
           |  <h2>Assembly warnings</h2>
           |  <p>${_assembly_warning_count(subsystem)} warning(s). <a href="/form/admin/assembly/warnings">Warning detail</a> · <a href="/form/admin/assembly/report">Assembly report</a></p>
           |</article>
           |<article>
           |  <h2>HTML request</h2>
           |  ${_summary_table(htmlRequests.summary)}
           |</article>
           |<article>
           |  <h2>Latency</h2>
           |  ${_latency_table(htmlRequests.recent)}
           |</article>
           |<article>
           |  <h2>Recent requests</h2>
           |  ${_recent_requests_table(htmlRequests.recent)}
           |</article>
           |<article>
           |  <h2>Recent errors</h2>
           |  ${_recent_errors_table(htmlRequests.recent)}
           |</article>
           |<article>
           |  <h2>ActionCall</h2>
           |  ${_summary_table(actionCalls.summary)}
           |  <p class="mt-3"><a href="/form/admin/execution/history">Execution history</a> · <a href="/form/admin/execution/calltree">Latest calltree</a></p>
           |</article>
           |<article>
           |  <h2>Authorization</h2>
           |  ${_summary_table(authorizationDecisions.summary)}
           |</article>
           |<article>
           |  <h2>DSL Chokepoints</h2>
           |  ${_summary_table(dslChokepoints.summary)}
           |</article>
           |<article>
           |  <h2>Jobs</h2>
           |  ${_jobs_table(jobs)}
           |</article>""".stripMargin
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
    val body =
      s"""${_manual_card("Reference navigation",
         s"""<p>This manual is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="${_escape(currentPath)}#help">Help</a>
            |  <a class="btn btn-outline-primary" href="${_escape(currentPath)}#describe">Describe</a>
            |  <a class="btn btn-outline-primary" href="${_escape(currentPath)}#schema">Schema</a>
            |  <a class="btn btn-outline-secondary" href="/web/system/manual/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${_manual_card("Children", childLinks)}
         |${_manual_card("Help", _manual_record(help), Some("help"))}
         |${_manual_card("Describe", _manual_record(describe), Some("describe"))}
         |${_manual_card("Schema", _manual_record(schema), Some("schema"))}""".stripMargin
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
      s"""${_manual_card("Reference navigation",
         s"""<p>This manual is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="/web/system/dashboard">System dashboard</a>
            |  <a class="btn btn-outline-primary" href="/web/system/admin">Admin configuration</a>
            |  <a class="btn btn-outline-primary" href="/web/system/performance">Performance details</a>
            |  <a class="btn btn-outline-secondary" href="/web/system/manual/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${_manual_card("Components", componentLinks)}
         |${_manual_card("Console handoff", """<p class="mb-0">Use <a href="/web/console">System Console</a> for controlled operation entry. Manual pages remain read-only and do not inline operation actions.</p>""")}
         |${_manual_card("Help", _manual_record(help), Some("help"))}
         |${_manual_card("Describe", _manual_record(describe), Some("describe"))}
         |${_manual_card("Schema", _manual_record(schema), Some("schema"))}""".stripMargin
    _simple_page("System Manual", "Read-only runtime reference", body)
  }

  private def _manual_component_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      _web_empty_state("No component reference entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.name)
        s"""<a class="btn btn-sm btn-outline-primary" href="/web/${_escape(segment)}/manual">${_escape(component.name)}</a>"""
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

  private def _manual_record(record: Record): String =
    s"""<dl class="row mb-0">${_property_rows(record.asMap.map { case (k, v) => k -> _manual_value(v) })}</dl>"""

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

  private def _manual_value(value: Any): String =
    value match {
      case null => ""
      case xs: Seq[?] => xs.map(_manual_value).mkString("[", ", ", "]")
      case m: Map[?, ?] => m.toVector.map { case (k, v) => s"${k}=${_manual_value(v)}" }.mkString("{", ", ", "}")
      case r: Record => r.asMap.map { case (k, v) => s"${k}=${_manual_value(v)}" }.mkString("{", ", ", "}")
      case x => x.toString
    }

  private def _simple_page(
    title: String,
    subtitle: String,
    body: String
  ): String =
    StaticFormAppLayout.bootstrapPage(StaticFormAppLayout.Options(
      title = title,
      subtitle = subtitle,
      body = body,
      extraHead =
        """|    article { background: #ffffff; border: 1px solid #d9dee5; border-radius: 8px; padding: 20px; }
           |    h2 { margin: 0 0 14px; font-size: 20px; }
           |    h3 { margin: 16px 0 8px; font-size: 16px; }
           |    p { margin: 0; color: #4d5662; }
           |    li { margin: 6px 0; }
           |""".stripMargin
    ))

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
    rendered: String
  ): String =
    StaticFormAppLayout.completeWidgetAssets(
      rendered,
      StaticFormAppLayout.AssetCompletionOptions(
        requiresBootstrap = _has_textus_widgets(template)
      )
    )

  private def _has_textus_widgets(template: String): Boolean =
    """<textus(?::|-)[A-Za-z0-9-]+\b""".r.findFirstIn(template).nonEmpty

  private def _render_property_expansions(
    template: String,
    properties: FormPageProperties
  ): String =
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(_escape(properties.value(m.group(1))))
    )

  private def _render_widgets(
    template: String,
    properties: FormPageProperties,
    tableColumns: Map[String, Vector[TableColumn]],
    defaultTableView: String
  ): String = {
    val resultView = """<textus-result-view\s+source="([^"]+)"\s*></textus-result-view>""".r
    val resultTable = """<textus-result-table\b([^>]*)></textus-result-table>""".r
    val recordCard = """<textus(?::record-card|-record-card)\b([^>]*)></textus(?::record-card|-record-card)>""".r
    val cardList = """<textus(?::card-list|-card-list)\b([^>]*)></textus(?::card-list|-card-list)>""".r
    val summaryCard = """<textus(?::summary-card|-summary-card)\b([^>]*)></textus(?::summary-card|-summary-card)>""".r
    val jobTicket = """<textus(?::job-ticket|-job-ticket)\b([^>]*)></textus(?::job-ticket|-job-ticket)>""".r
    val jobActions = """<textus(?::job-actions|-job-actions)\b([^>]*)></textus(?::job-actions|-job-actions)>""".r
    val alert = """<textus(?::alert|-alert)\b([^>]*)></textus(?::alert|-alert)>""".r
    val emptyState = """<textus(?::empty-state|-empty-state)\b([^>]*)></textus(?::empty-state|-empty-state)>""".r
    val pagination = """<textus(?::pagination|-pagination)\b([^>]*)></textus(?::pagination|-pagination)>""".r
    val formLink = """<textus-form-link\s+href="([^"]+)"\s+label="([^"]+)"\s*></textus-form-link>""".r
    val actionLink = """<textus(?::action-link|-action-link)\b([^>]*)></textus(?::action-link|-action-link)>""".r
    val actionForm = """<textus(?::action-form|-action-form)\b([^>]*)></textus(?::action-form|-action-form)>""".r
    val hiddenContext = """<textus(?::hidden-context|-hidden-context)\b([^>]*)></textus(?::hidden-context|-hidden-context)>""".r
    val propertyList = """<textus-property-list\s+source="([^"]+)"\s*></textus-property-list>""".r
    val errorPanel = """<textus-error-panel\s+source="([^"]+)"\s*></textus-error-panel>""".r
    val a = resultView.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(_render_result_view(m.group(1), properties))
    )
    val b = resultTable.replaceAllIn(a, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_result_table(attrs, properties, tableColumns, defaultTableView))
    })
    val c = recordCard.replaceAllIn(b, m => {
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
    val f0 = jobTicket.replaceAllIn(e, m => {
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
    val h = pagination.replaceAllIn(g, m => {
      val attrs = _widget_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(_render_pagination(attrs, properties))
    })
    val i = formLink.replaceAllIn(h, m =>
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
    val n = propertyList.replaceAllIn(l, m =>
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
    val table = _json_table(source, properties, page, pageSize, columns).getOrElse("")
    s"""${table}<div class="mt-3">${_render_pagination(attrs, properties)}</div>"""
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
        s"""<div class="row row-cols-1 row-cols-md-2 g-3 mt-3">${body}</div>"""
      }
    }.getOrElse(_empty_state(attrs.getOrElse("empty", "No records")))
    s"""${cards}<div class="mt-3">${_render_pagination(attrs, properties)}</div>"""
  }

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
    s"""<article class="card h-100 textus-record-card"><div class="card-body"><h3 class="h5 card-title">${_escape(title)}</h3>${subtitleHtml}<dl class="row mb-0">${rows}</dl></div></article>"""
  }

  private def _first_existing_field(
    obj: Map[String, Json],
    names: Vector[String]
  ): Option[String] =
    names.find(obj.contains)

  private def _empty_state(
    message: String
  ): String =
    s"""<div class="alert alert-secondary textus-empty-state" role="status">${_escape(message)}</div>"""

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

  private def _render_job_actions(
    attrs: Map[String, String],
    properties: FormPageProperties
  ): String = {
    val names = attrs.get("actions")
      .map(_.split(',').toVector.map(_.trim).filter(_.nonEmpty))
      .filter(_.nonEmpty)
      .getOrElse(Vector("await", "detail"))
    val buttons = names.flatMap { name =>
      _resolve_action(Map("source" -> s"result.action.${name}", "class" -> _job_action_class(name)), properties).map {
        case ActionWidgetValue(href, label, css, method) if method.equalsIgnoreCase("GET") =>
          s"""<a class="${_escape(css)}" href="${_escape(href)}">${_escape(label)}</a>"""
        case ActionWidgetValue(href, label, css, method) =>
          _action_form_html(method, href, css, label, _render_hidden_context(Map.empty, properties))
      }
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
      _empty_state(message)
    } else {
      ""
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
      case _ => "info"
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
    columns: Option[Vector[TableColumn]]
  ): Option[String] =
    _source_json(source, properties).flatMap { json =>
      val rows = _table_rows(json)
      rows.flatMap(xs => _records_table(_page_rows(xs, page, pageSize), columns))
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
    "([A-Za-z0-9_.:-]+)=\"([^\"]*)\"".r.findAllMatchIn(source).map { m =>
      m.group(1) -> m.group(2)
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
    columns: Option[Vector[TableColumn]]
  ): Option[String] = {
    val objects = rows.flatMap(_.asObject)
    if (objects.isEmpty) {
      None
    } else {
      val headers = columns.getOrElse(objects.flatMap(_.keys).distinct.map(name => TableColumn(name, name)))
      val head = headers.map(h => s"<th>${_escape(h.label)}</th>").mkString
      val body = objects.map { obj =>
        val cells = headers.map { h =>
          val value = obj(h.name).map(_json_cell).getOrElse("")
          s"<td>${_escape(value)}</td>"
        }.mkString
        s"<tr>${cells}</tr>"
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
    val dslChokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val avgMillis =
      if (htmlRequests.recent.isEmpty) 0L
      else htmlRequests.recent.map(_.elapsedMillis).sum / htmlRequests.recent.size
    val adminPath =
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/admin"
      else "/web/system/admin"
    val manualPath =
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/manual"
      else "/web/system/manual"
    s"""{"scope":"${_json(scope)}","name":"${_json(name)}","version":${version.map(v => "\"" + _json(v) + "\"").getOrElse("null")},"observedAt":"${java.time.Instant.now.toString}","status":"UP","cncf":{"version":"${_json(CncfVersion.current)}"},"subsystem":{"name":"${_json(subsystemName)}","version":${subsystemVersion.map(v => "\"" + _json(v) + "\"").getOrElse("null")}},"componentCount":${components.size},"serviceCount":${serviceCount},"operationCount":${operationCount},"actions":{"actionCalls":${_snapshot_json(actionCalls, includeRecent = false)},"jobs":${_jobs_json(running, queued, completed, failed)}},"dsl":{"chokepoints":${_snapshot_json(dslChokepoints, includeRecent = false)}},"authorization":{"decisions":${_snapshot_json(authorizationDecisions, includeRecent = false)}},"assembly":{"warnings":{"count":${assemblyWarningCount}}},"html":{"requests":${_snapshot_json(htmlRequests, includeRecent = true, Some(avgMillis))}},"links":{"admin":"${_json(adminPath)}","performance":"/web/system/performance","manual":"${_json(manualPath)}","console":"/web/console","assemblyWarnings":"/form/admin/assembly/warnings"},"components":${componentJson}}"""
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
    components.map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      s"""<p><strong>${_escape(component.name)}</strong> · <a href="/web/${componentPath}/dashboard">Dashboard</a> · <a href="/web/${componentPath}/admin">Admin</a> · <a href="/form/${componentPath}">Forms</a></p>"""
    }.mkString("\n")

  private def _component_form_list(components: Vector[Component]): String =
    components.map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      s"""<p><strong>${_escape(component.name)}</strong> · <a href="/form/${componentPath}">Operation forms</a></p>"""
    }.mkString("\n")

  private def _job_metrics(subsystem: Subsystem): (Int, Int, Int, Int) =
    subsystem.jobEngine.metrics.map(x => (x.running, x.queued, x.completed, x.failed)).getOrElse((0, 0, 0, 0))

  private def _job_metrics(component: Component): (Int, Int, Int, Int) =
    component.jobEngine.metrics.map(x => (x.running, x.queued, x.completed, x.failed)).getOrElse((0, 0, 0, 0))

  private def _assembly_warning_count(subsystem: Subsystem): Int =
    org.goldenport.cncf.context.GlobalRuntimeContext.current
      .orElse(scala.util.Try(subsystem.globalRuntimeContext).toOption)
      .map(_.assemblyReport.warnings.size)
      .getOrElse(0)

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
