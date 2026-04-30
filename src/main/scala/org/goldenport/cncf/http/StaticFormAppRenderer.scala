package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.JobQueryReadModel
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.operation.{AssociationBindingOperationDefinition, CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationImageBinding, ImageBindingOperationDefinition}
import org.goldenport.cncf.projection.{AuthorizationPolicyProjection, DescribeProjection, HelpProjection, SchemaProjection}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Request as ProtocolRequest}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.{Json, JsonObject}
import io.circe.parser.parse

/*
 * @since   Apr. 12, 2026
 * @version Apr. 30, 2026
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
      StaticFormAppLayout.AssetCompletionOptions()
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
      ) ++ _framework_action_values(metadata) ++ metadata.toTemplateValues
      val base = page.copy(values = page.values ++ formValues ++ resultValues)
      if (status >= 400)
        base
          .withValue("error.status", status.toString)
          .withValue("error.body", body)
      else
        base

    private def _default_paging_href: String =
      s"/form/${page.componentPath}/${page.servicePath}/${page.operationPath}/result?page={page}&pageSize={pageSize}"

    private def _framework_action_values(metadata: FormResultMetadata): Map[String, String] =
      _detail_action_values(metadata) ++ _job_action_values(metadata) ++ _return_action_values

    private def _job_action_values(metadata: FormResultMetadata): Map[String, String] =
      metadata.jobId match {
        case Some(jobid) =>
          val href = page.values.getOrElse("result.job.href", s"/form/${page.componentPath}/${page.servicePath}/${page.operationPath}/jobs/${jobid}/await")
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
        _find_component(subsystem, app).map(renderComponentDashboard(_, NamingConventions.toNormalizedSegment(app)))
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
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
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
             |${services}""".stripMargin,
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
    validation: Option[FormValidationResult] = None
  ): Option[Page] =
    _resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor).map { context =>
      val action = s"/form/${context.componentPath}/${context.servicePath}/${context.operationPath}"
      val effectiveValues = _operation_form_prefill_values(subsystem, context, values)
      val effectiveValidation = validation.filter(_.webSchema.selector == context.webSchema.selector)
      val controls = _operation_form_controls(context, effectiveValues, effectiveValidation)
      val hiddenContext = _hidden_form_context_inputs(effectiveValues)
      val errorPanel = _form_error_panel(effectiveValues) + _form_validation_panel(effectiveValidation)
      val enctype = _operation_form_enctype(context.webSchema, context.imageBinding)
      Page(_simple_page(
        title = s"${_escape(context.component.name)}.${_escape(context.serviceName)}.${_escape(context.operationName)}",
        subtitle = "HTML form operation",
        body =
          s"""<article>
             |  ${errorPanel}
             |  <form method="post" action="${_escape(action)}"${enctype}>
             |    ${controls}
             |    ${hiddenContext}
             |    <button type="submit" class="btn btn-primary">Run</button>
             |    <a class="btn btn-outline-secondary" href="/form/${context.componentPath}">Operations</a>
             |  </form>
             |</article>""".stripMargin,
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
      Page(_complete_widget_assets(template, rendered, properties.assetCompletion))
    else
      Page(StaticFormAppLayout.completeDeclaredAssets(
        _simple_page(
          title = s"${_escape(properties.operationLabel)} Result",
          subtitle = s"HTTP ${properties.status}",
          body = rendered
        ),
        properties.assetCompletion
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
    val code = _escape(error.code)
    val body =
      s"""<section class="alert alert-danger" role="alert">
         |  <h2 class="h5">Request failed</h2>
         |  <p class="mb-2">${_escape(error.message)}</p>
         |  <p class="mb-0"><strong>Error code:</strong> <code>${code}</code></p>
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
      "error.code" -> error.code,
      "error.detailCode" -> error.code,
      "error.codeSource" -> error.codeSource,
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
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/admin">System admin</a> · <a href="/web/system/dashboard">System dashboard</a> · <a href="/form/admin/execution/history">Execution history</a></p>
           |</article>
           |<article>
           |  <h2>Debug Jobs</h2>
           |  <p>Query requests normally run directly. Requests submitted with <code>--debug.trace-job</code> are retained here for operational debugging.</p>
           |  <div class="table-responsive mt-3">
           |    <table class="table table-sm align-middle">
           |      <thead><tr><th>Job</th><th>Status</th><th>Target</th><th>Result</th><th>Trace</th><th>Updated</th><th>Actions</th></tr></thead>
           |      <tbody>${rows}</tbody>
           |    </table>
           |  </div>
           |</article>""".stripMargin
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
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/admin/jobs">Debug jobs</a> · <a href="/web/system/jobs/${_escape_path_segment(model.jobId.value)}">System job result</a> · <a href="/web/system/admin">System admin</a></p>
           |</article>
           |<article>
           |  <h2>Summary</h2>
           |  <dl class="row">
           |    <dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${_escape(model.jobId.value)}</code></dd>
           |    <dt class="col-sm-3">Status</dt><dd class="col-sm-9">${_escape(model.status.toString)}</dd>
           |    <dt class="col-sm-3">Persistence</dt><dd class="col-sm-9">${_escape(model.persistence.toString)}</dd>
           |    <dt class="col-sm-3">Target</dt><dd class="col-sm-9"><code>${_escape(target)}</code></dd>
           |    <dt class="col-sm-3">Result</dt><dd class="col-sm-9">${_escape(model.resultSummary.message.getOrElse(""))}</dd>
           |    <dt class="col-sm-3">Trace ID</dt><dd class="col-sm-9"><code>${_escape(model.lineage.correlationId.getOrElse(model.debug.parameters.getOrElse("traceId", "")))}</code></dd>
           |    <dt class="col-sm-3">Updated</dt><dd class="col-sm-9">${_escape(model.updatedAt.toString)}</dd>
           |  </dl>
           |</article>
           |<article>
           |  <h2>Calltree</h2>
           |  ${calltree}
           |</article>
           |<article>
           |  <h2>Tasks</h2>
           |  <div class="table-responsive"><table class="table table-sm"><thead><tr><th>Task</th><th>Status</th><th>Component</th><th>Operation</th><th>Message</th><th>Started</th><th>Finished</th></tr></thead><tbody>${taskrows}</tbody></table></div>
           |</article>
           |<article>
           |  <h2>Timeline</h2>
           |  <div class="table-responsive"><table class="table table-sm"><thead><tr><th>#</th><th>Kind</th><th>At</th><th>Task</th><th>Note</th></tr></thead><tbody>${eventrows}</tbody></table></div>
           |</article>""".stripMargin
    ))
  }

  def renderBlobAdmin(): Page =
    Page(_simple_page(
      title = "Blob Admin",
      subtitle = "Blob metadata, associations, and store diagnostics",
      body =
        s"""<article>
           |  <h2>Management</h2>
           |  <p>Use these pages to inspect Blob metadata, manage entity associations, and run controlled Blob admin actions.</p>
           |  ${_admin_entry_cards(Vector(
             _admin_entry_card("Blobs", "List Blob metadata rows and open detail pages.", "/web/blob/admin/blobs"),
             _admin_entry_card("Associations", "Inspect, attach, and detach Blob-to-entity association records.", "/web/blob/admin/associations"),
             _admin_entry_card("Store Status", "Inspect the active BlobStore backend status.", "/web/blob/admin/store"),
             _admin_entry_card("Delete", "Open a Blob detail page to run controlled delete with optional force.", "/web/blob/admin/blobs")
           ))}
           |</article>
           |${_admin_card("Authorization requirements",
             """<p>Blob admin actions use the existing admin operation gate plus generic resource policies.</p>
               |<ul>
               |  <li><code>collection:blob:delete</code> controls Blob metadata delete.</li>
               |  <li><code>association:blob_attachment:create/delete/search/list</code> controls Blob attachment operations.</li>
               |  <li><code>store:blobstore:status</code> controls BlobStore status diagnostics.</li>
               |</ul>
               |<p class="mb-0"><a href="/web/system/manual#authorization-policies">View effective authorization policies</a></p>""".stripMargin
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
             |<article>
             |  <h2>Blobs</h2>
             |  <p>Showing metadata rows only. Payload bytes remain in BlobStore.</p>
             |  <div class="table-responsive mt-3">
             |    <table class="table table-sm align-middle">
             |      <thead><tr><th>ID</th><th>Kind</th><th>Source</th><th>Filename</th><th>Content Type</th><th>Bytes</th><th>Digest</th><th>Display URL</th><th>Actions</th></tr></thead>
             |      ${table}
             |    </table>
             |  </div>
             |  ${paging}
             |</article>
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
             |${_admin_card("Actions", s"""<p><a class="btn btn-outline-danger" href="/web/blob/admin/blobs/${_escape_path_segment(id)}/delete">Delete Blob</a></p>""")}
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
           |  <button class="btn btn-danger" type="submit">Delete Blob</button>
           |  <a class="btn btn-outline-secondary" href="/web/blob/admin/blobs/${_escape_path_segment(id)}">Cancel</a>
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
             |<p><a class="btn btn-primary" href="/web/blob/admin/blobs">Back to Blobs</a> <a class="btn btn-outline-secondary" href="/web/blob/admin/associations">Associations</a></p>
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
             |<article>
             |  <h2>Attach Blob</h2>
             |  ${attach}
             |</article>
             |<article>
             |  <h2>Filters</h2>
             |  ${filters}
             |</article>
             |<article>
             |  <h2>Associations</h2>
             |  <div class="table-responsive mt-3">
             |    <table class="table table-sm align-middle">
             |      <thead><tr><th>Association</th><th>Source Entity</th><th>Blob</th><th>Role</th><th>Sort</th><th>Domain</th><th>Collection</th><th>Actions</th></tr></thead>
             |      ${table}
             |    </table>
             |  </div>
             |  ${paging}
             |</article>
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
             |<p><a class="btn btn-primary" href="/web/blob/admin/associations?sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}">Back to Associations</a> <a class="btn btn-outline-secondary" href="/web/blob/admin/blobs/${_escape_path_segment(form.getOrElse("id", ""))}">Blob detail</a></p>
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
             |<p><a class="btn btn-primary" href="/web/blob/admin/associations?sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}">Back to Associations</a> <a class="btn btn-outline-secondary" href="/web/blob/admin/blobs/${_escape_path_segment(form.getOrElse("id", ""))}">Blob detail</a></p>
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
             |<article>
             |  <h2>Attach Association</h2>
             |  ${attach}
             |</article>
             |<article>
             |  <h2>Filters</h2>
             |  ${filters}
             |</article>
             |<article>
             |  <h2>Associations</h2>
             |  <div class="table-responsive mt-3">
             |    <table class="table table-sm align-middle">
             |      <thead><tr><th>Association</th><th>Source Entity</th><th>Target Entity</th><th>Target kind</th><th>Role</th><th>Sort</th><th>Domain</th><th>Actions</th></tr></thead>
             |      ${table}
             |    </table>
             |  </div>
             |  ${paging}
             |</article>
             |${_manual_raw_details("Raw association list", record)}""".stripMargin
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
             |<p><a class="btn btn-primary" href="/web/admin/associations?domain=${_escape_query(form.getOrElse("domain", ""))}&amp;sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}">Back to Associations</a></p>
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
             |<p><a class="btn btn-primary" href="/web/admin/associations?domain=${_escape_query(form.getOrElse("domain", ""))}&amp;sourceEntityId=${_escape_query(form.getOrElse("sourceEntityId", ""))}">Back to Associations</a></p>
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

  private def _blob_admin_is_truthy(
    value: String
  ): Boolean =
    Set("true", "1", "yes", "on").contains(value.trim.toLowerCase(java.util.Locale.ROOT))

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
    s"""<form method="post" action="/web/blob/admin/associations/detach" class="d-inline ms-2">
       |  <input type="hidden" name="sourceEntityId" value="${_escape(source)}">
       |  <input type="hidden" name="id" value="${_escape(blobid)}">
       |  <input type="hidden" name="role" value="${_escape(role)}">
       |  <button class="btn btn-outline-danger btn-sm" type="submit">Detach</button>
       |</form>""".stripMargin
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
    s"""<form method="post" action="/web/admin/associations/detach" class="d-inline">
       |  <input type="hidden" name="domain" value="${_escape(domain)}">
       |  <input type="hidden" name="sourceEntityId" value="${_escape(source)}">
       |  <input type="hidden" name="targetEntityId" value="${_escape(target)}">
       |  <input type="hidden" name="targetKind" value="${_escape(targetKind)}">
       |  <input type="hidden" name="role" value="${_escape(role)}">
       |  <button class="btn btn-outline-danger btn-sm" type="submit">Detach</button>
       |</form>""".stripMargin
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
    s"""<form method="post" action="/web/blob/admin/associations/detach" class="d-inline ms-2">
       |  <input type="hidden" name="sourceEntityId" value="${_escape(sourceId)}">
       |  <input type="hidden" name="id" value="${_escape(id)}">
       |  <input type="hidden" name="role" value="${_escape(role)}">
       |  <button class="btn btn-outline-danger btn-sm" type="submit">Detach</button>
       |</form>""".stripMargin
  }

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
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/web/system/admin/descriptor">System descriptor</a></p>
             |</article>
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
          title = "System Manual",
          subtitle = "Read-only runtime reference",
          body = """<article><h2>No components</h2><p>No component reference entries are available.</p></article>"""
        ))
    }

  def renderRuntimeLanding(
    subsystem: Subsystem
  ): Page = {
    val runtime = RuntimeConfig.from(subsystem.configuration)
    val appComponents = subsystem.components.filterNot(_.origin == ComponentOrigin.Builtin)
    val effectiveComponents =
      if (appComponents.nonEmpty) appComponents
      else subsystem.components
    val componentLinks = effectiveComponents.map { component =>
      val path = NamingConventions.toNormalizedSegment(component.name)
      s"""<li><strong>${_escape(component.name)}</strong>: <a href="/web/${_escape(path)}">App</a> · <a href="/web/${_escape(path)}/admin">Admin</a> · <a href="/web/${_escape(path)}/manual">Manual</a></li>"""
    }.mkString("\n")
    val recommendations =
      if (componentLinks.isEmpty)
        """<article>
          |  <h2>Recommended Links</h2>
          |  <ul>
          |    <li><a href="/web/system/manual">System manual</a></li>
          |    <li><a href="/web/system/admin">System admin</a></li>
          |    <li><a href="/web/system/dashboard">System dashboard</a></li>
          |    <li><a href="/web/system/performance">Performance</a></li>
          |  </ul>
          |</article>""".stripMargin
      else
        s"""<article>
           |  <h2>Recommended Links</h2>
           |  <ul>
           |    <li><a href="/web/system/manual">System manual</a></li>
           |    <li><a href="/web/system/admin">System admin</a></li>
           |    <li><a href="/web/system/dashboard">System dashboard</a></li>
           |    <li><a href="/web/system/performance">Performance</a></li>
           |    ${componentLinks}
           |  </ul>
           |</article>""".stripMargin
    Page(_simple_page(
      title = "CNCF Runtime Help",
      subtitle = "Development and demo entry points",
      body =
        s"""<article>
           |  <h2>Runtime</h2>
           |  <div class="table-responsive"><table class="table table-sm">
           |    <tbody>
           |      <tr><th>Subsystem</th><td>${_escape(subsystem.name)}</td></tr>
           |      <tr><th>Operation mode</th><td>${_escape(runtime.operationMode.name)}</td></tr>
           |      <tr><th>Components</th><td>${effectiveComponents.size}</td></tr>
           |    </tbody>
           |  </table></div>
           |  <p>This page is shown only outside production when no explicit root or <code>/web</code> route is configured.</p>
           |</article>
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
      title = s"${_escape(component.name)} Manual",
      subtitle = "Component reference",
      component = component,
      selector = Some(component.name),
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(componentName)}/manual",
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
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(componentName)}/manual/${NamingConventions.toNormalizedSegment(service.name)}",
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
      currentPath = s"/web/${NamingConventions.toNormalizedSegment(componentName)}/manual/${NamingConventions.toNormalizedSegment(service.name)}/${NamingConventions.toNormalizedSegment(operation.name)}",
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
        val workingsetpolicy = descriptor.workingSetPolicy.map(_.label).getOrElse("none")
        val policysource = descriptor.workingSetPolicySource.map(_.toString.toLowerCase).getOrElse("none")
        val collection = component.entitySpace.entityOption[Any](descriptor.collectionId.name)
        val workingSetStatus = collection.map(_.workingSetStatus)
        val workingSetState = workingSetStatus.map(_.state.label).getOrElse("unknown")
        val residentCount = collection.map(_.residentCount).getOrElse(0)
        val workingSetError = workingSetStatus.flatMap(_.error).getOrElse("")
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/entities/${entityPath}">${_escape(descriptor.entityName)}</a></td>
           |  <td><code>${_escape(descriptor.collectionId.name)}</code></td>
           |  <td>${_escape(descriptor.usageKind.toString)}</td>
           |  <td>${_escape(descriptor.operationKind.toString)}</td>
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
             |  <thead><tr><th>Entity</th><th>Collection</th><th>Usage</th><th>Operation</th><th>Domain</th><th>Working set</th><th>Policy</th><th>Source</th><th>Status</th><th>Resident</th><th>Error</th></tr></thead>
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
             |${_admin_storage_shape_section(component, Some(entityPath), detailed = true)}
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
      val componentlets = _componentlet_table(component)
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityCount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val dataCount = _admin_surface_selector_count(webDescriptor, Some(componentPath), "data")
      val aggregateCount = component.aggregateDefinitions.size
      val viewCount = component.viewDefinitions.size
      val formsCount = component.protocol.services.services.map(_.operations.operations.toVector.size).sum
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
      val technicalDetails =
        s"""<details class="mt-3">
           |  <summary>Technical details</summary>
           |  <article class="mt-3">
           |    <h3>${_escape(component.name)}</h3>
           |    <p>Version ${_escape(version)}</p>
           |    ${componentlets}
           |    ${services}
           |  </article>
           |</details>""".stripMargin
      s"""<article>
         |  <h2>${_escape(component.name)}</h2>
         |  <p>Version ${_escape(version)}</p>
         |  ${cards}
         |  ${technicalDetails}
         |</article>""".stripMargin
    }.mkString("\n")
    val componentInventory =
      if (componentFormsPath.isEmpty)
        _system_admin_component_inventory(components)
      else
        ""
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
           |${componentInventory}
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
        |  <p>Job control entry points are reserved for system admin operations. Use builtin job and event surfaces as the authoritative source for cross-component continuation observability.</p>
        |  <div class="card mb-3"><div class="card-body">
        |    <h3 class="h5">Operator Checklist</h3>
        |    <ul class="mb-0">
        |      <li>Use <code>job_control.job.get_job_status</code> for source/target component, reception policy, task relation, transaction relation, and task summary.</li>
        |      <li>Use <code>job_control.job.load_job_history</code> for per-task execution timeline.</li>
        |      <li>Use <code>event.event.load_event</code> for dispatch contract, policy source, and unsupported-policy rejection evidence.</li>
        |      <li>Use <code>job_control.job_admin.load_job_events</code> for job-to-event cross-links.</li>
        |    </ul>
        |  </div></div>
        |  <ul>
        |    <li><a href="/web/system/admin/jobs">Debug jobs</a></li>
        |    <li><a href="/form/admin/execution/diagnostics">Execution diagnostics</a></li>
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
      s"""<article>
         |  <h2>Management Console Home</h2>
         |  <p>Use the cards below for ordinary management work. Operation selectors and protocol paths stay available as technical reference only.</p>
         |  ${_component_admin_management_cards(componentPath, path)}
         |  <details class="mt-3">
         |    <summary>Technical details</summary>
         |    <div class="mt-3">
         |      <p><a href="${_escape(path)}">Operation forms</a></p>
         |      ${_component_admin_management_links(path)}
         |    </div>
         |  </details>
         |</article>""".stripMargin
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
      _admin_entry_card("Descriptor", "Inspect descriptor controls and admin-surface mappings.", s"/web/${componentPath}/admin/descriptor"),
      _admin_entry_card("Forms", "Open controlled operation forms outside the admin CRUD surfaces.", formsPath)
    ))

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
    s"""<article>
       |  <h2 id="descriptor-controls">Descriptor Controls <a class="btn btn-sm btn-outline-secondary ms-2" href="#completed-descriptor">Completed JSON</a></h2>
       |  <p>Completed apps, routes, form access, authorization, and admin surfaces.</p>
       |  ${_web_descriptor_filter_control}
       |  ${_web_descriptor_apps_table(descriptor, componentSegment)}
       |  ${_web_descriptor_routes_table(descriptor, componentSegment)}
       |  ${_web_descriptor_form_controls_table(descriptor, componentSegment)}
       |  ${_web_descriptor_admin_surfaces_table(descriptor, componentSegment)}
       |  ${_web_descriptor_filter_script}
       |</article>""".stripMargin
  }

  private def _web_descriptor_section_nav: String =
    """<article class="descriptor-section-nav">
      |  <h2>Descriptor Sections</h2>
      |  <nav class="nav nav-pills flex-column flex-sm-row gap-2">
      |    <a class="nav-link border" href="#descriptor-controls">Descriptor Controls</a>
      |    <a class="nav-link border" href="#asset-composition">Asset Composition</a>
      |    <a class="nav-link border" href="#completed-descriptor">Completed JSON</a>
      |    <a class="nav-link border" href="#configured-descriptor">Configured JSON</a>
      |  </nav>
      |</article>""".stripMargin

  private def _web_descriptor_json_panel(
    id: String,
    title: String,
    description: String,
    json: String
  ): String =
    s"""<article id="${_escape(id)}">
       |  <details class="descriptor-json-details">
       |    <summary class="h2 mb-3">${_escape(title)}</summary>
       |    <p>${_escape(description)}</p>
       |    ${_raw_format_tabs(json, _json_to_yaml(json), "descriptor")}
       |  </details>
       |</article>""".stripMargin

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
    s"""<article>
       |  <h2>Component Management Console</h2>
       |  <p>Use component admin pages for ordinary entity/data/view/aggregate work. System admin stays read-only.</p>
       |  <div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |    <thead><tr><th>Component</th><th>Admin</th><th>Descriptor</th><th>Forms</th></tr></thead>
       |    <tbody>${rows}</tbody>
       |  </table></div>
       |</article>""".stripMargin
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
    val dslChokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val validation = RuntimeDashboardMetrics.validationSnapshot
    val validationDiagnostics = RuntimeDashboardMetrics.validationDiagnosticCounts
    val operationRequestValidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot
    val operationRequestValidationDiagnostics = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts
    val blobOperations = RuntimeDashboardMetrics.blobOperationSnapshot
    val blobDiagnostics = RuntimeDashboardMetrics.blobDiagnosticCounts
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
           |  <h3 class="h6 mt-3">Diagnostic</h3>
           |  ${_diagnostics_table(authorizationDiagnostics)}
           |</article>
           |<article>
           |  <h2>DSL Chokepoints</h2>
           |  ${_summary_table(dslChokepoints.summary)}
           |</article>
           |<article>
           |  <h2>Validation</h2>
           |  ${_summary_table(validation.summary)}
           |  <h3 class="h6 mt-3">Diagnostic</h3>
           |  ${_diagnostics_table(validationDiagnostics)}
           |</article>
           |<article>
           |  <h2>Operation Request Validation</h2>
           |  ${_summary_table(operationRequestValidation.summary)}
           |  <h3 class="h6 mt-3">Diagnostic</h3>
           |  ${_diagnostics_table(operationRequestValidationDiagnostics)}
           |</article>
           |<article>
           |  <h2>Blob operations</h2>
           |  ${_summary_table(blobOperations.summary)}
           |  <h3 class="h6 mt-3">Diagnostic</h3>
           |  ${_diagnostics_table(blobDiagnostics)}
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
         |${_manual_authorization_policy_section(describe)}
         |${_manual_projection_card("Help", "/web/system/manual", help, Some("help"))}
         |${_manual_projection_card("Describe", "/web/system/manual", describe, Some("describe"))}
         |${_manual_projection_card("Schema", "/web/system/manual", schema, Some("schema"))}""".stripMargin
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
         "Web manual" -> currentPath,
         "REST" -> restPath,
         "Form" -> formPath,
         "Form API" -> formApiPath,
       "OpenAPI JSON" -> "/web/system/manual/openapi.json"
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
          """|    article { background: #ffffff; border: 1px solid #d9dee5; border-radius: 8px; padding: 20px; }
             |    h2 { margin: 0 0 14px; font-size: 20px; }
             |    h3 { margin: 16px 0 8px; font-size: 16px; }
             |    p { margin: 0; color: #4d5662; }
             |    li { margin: 6px 0; }
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
    val card = """(?s)<textus(?::card(?!-)|-card(?!-))\b([^>]*)>(.*?)</textus(?::card|-card)>""".r
    val recordCard = """<textus(?::record-card|-record-card)\b([^>]*)></textus(?::record-card|-record-card)>""".r
    val cardList = """<textus(?::card-list|-card-list)\b([^>]*)></textus(?::card-list|-card-list)>""".r
    val summaryCard = """<textus(?::summary-card|-summary-card)\b([^>]*)></textus(?::summary-card|-summary-card)>""".r
    val actionCard = """<textus(?::action-card|-action-card)\b([^>]*)></textus(?::action-card|-action-card)>""".r
    val actionGroup = """<textus(?::action-group|-action-group)\b([^>]*)></textus(?::action-group|-action-group)>""".r
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
    val e2 = jobPanel.replaceAllIn(e1a, m => {
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
    val n = propertyList.replaceAllIn(l1, m =>
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
      val systemHref = s"/web/system/jobs/${_escape_path_segment(jobId)}"
      val adminHref = s"/web/system/admin/jobs/${_escape_path_segment(jobId)}"
      s"""<section class="textus-job-panel border rounded p-3 mb-3 bg-light"><div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-3"><div><h3 class="h5 mb-1">${_escape(title)}</h3><p class="text-secondary mb-0">${_escape(description)}</p></div><div class="d-flex gap-2"><a class="btn btn-outline-secondary btn-sm" href="${_escape(systemHref)}">Open job page</a><a class="btn btn-outline-secondary btn-sm" href="${_escape(adminHref)}">Debug detail</a></div></div>${ticket}${actions}</section>"""
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
      _empty_state(message)
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
      case _ => "info"
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
    val items = attrs.get("items").toVector.flatMap(_.split("\\|").toVector).flatMap(_nav_item(_, properties))
    if (items.isEmpty)
      ""
    else if (style == "list")
      s"""<nav class="textus-nav-list"><div class="list-group">${items.map { case (label, href, css) => s"""<a class="list-group-item list-group-item-action ${_escape(css)}" href="${_escape(href)}">${_escape(label)}</a>""" }.mkString}</div></nav>"""
    else
      s"""<nav class="d-flex flex-wrap gap-2 mt-3 textus-nav-list">${items.map { case (label, href, css) => s"""<a class="${_escape(css)}" href="${_escape(href)}">${_escape(label)}</a>""" }.mkString}</nav>"""
  }

  private def _nav_item(
    text: String,
    properties: FormPageProperties
  ): Option[(String, String, String)] = {
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
      Some((_resolve_attr_value(label, properties), _resolve_attr_value(href, properties), css))
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
      if (scope == "component") s"/web/${NamingConventions.toNormalizedSegment(name)}/manual"
      else "/web/system/manual"
    s"""{"scope":"${_json(scope)}","name":"${_json(name)}","version":${version.map(v => "\"" + _json(v) + "\"").getOrElse("null")},"observedAt":"${java.time.Instant.now.toString}","status":"UP","cncf":{"version":"${_json(CncfVersion.current)}"},"subsystem":{"name":"${_json(subsystemName)}","version":${subsystemVersion.map(v => "\"" + _json(v) + "\"").getOrElse("null")}},"componentCount":${components.size},"serviceCount":${serviceCount},"operationCount":${operationCount},"actions":{"actionCalls":${_snapshot_json(actionCalls, includeRecent = false)},"jobs":${_jobs_json(running, queued, completed, failed)}},"dsl":{"chokepoints":${_snapshot_json(dslChokepoints, includeRecent = false)},"validation":${_snapshot_json(validation, includeRecent = false)},"validationDiagnostics":${_string_long_map_json(validationDiagnostics)},"operationRequestValidation":${_snapshot_json(operationRequestValidation, includeRecent = false)},"operationRequestValidationDiagnostics":${_string_long_map_json(operationRequestValidationDiagnostics)}},"authorization":{"decisions":${_snapshot_json(authorizationDecisions, includeRecent = false)},"diagnostics":${_string_long_map_json(authorizationDiagnostics)}},"blob":{"operations":${_snapshot_json(blobOperations, includeRecent = false)},"diagnostics":${_string_long_map_json(blobDiagnostics)}},"assembly":{"warnings":{"count":${assemblyWarningCount}}},"html":{"requests":${_snapshot_json(htmlRequests, includeRecent = true, Some(avgMillis))}},"links":{"admin":"${_json(adminPath)}","performance":"/web/system/performance","manual":"${_json(manualPath)}","console":"/web/console","assemblyWarnings":"/form/admin/assembly/warnings"},"components":${componentJson}}"""
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
    counts: Map[String, Long]
  ): String =
    if (counts.isEmpty)
      """<p class="text-secondary">No diagnostics have been recorded.</p>"""
    else {
      val rows = counts.toVector.sortBy(_._1).map {
        case (kind, count) =>
          s"<tr><td><code>${_escape(kind)}</code></td><td>${count}</td></tr>"
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>Diagnostic</th><th>Count</th></tr></thead>
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
