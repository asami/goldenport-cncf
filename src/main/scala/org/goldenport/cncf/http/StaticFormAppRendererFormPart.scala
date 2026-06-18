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
import org.goldenport.record.io.RecordDecoder
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataConfidentiality, Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.{Json, JsonObject}
import io.circe.parser.parse

/*
 * @since   May. 18, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererFormPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormResultPart with StaticFormAppRendererSystemAdminPart =>
  import StaticFormAppRendererSupport.*

  protected final case class OperationWebSchemaContext(
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
  protected final case class FormDefinitionNavigation(
    mode: String,
    method: String,
    submitPath: String,
    htmlPath: String,
    actions: Vector[FormDefinitionAction] = Vector.empty
  )
  protected final case class FormDefinitionAction(
    name: String,
    method: String,
    path: String
  )

  def renderFormIndex(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val services = component.protocol.services.services.map { service =>
        val operations = service.operations.operations.toVector.filter { operation =>
          webDescriptor.isFormEnabled(operation_selector(component.name, service.name, operation.name))
        }.map { operation =>
          val path = s"/form/${componentPath}/${NamingConventions.toNormalizedSegment(service.name)}/${NamingConventions.toNormalizedSegment(operation.name)}"
          s"""<a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center" href="${escape(path)}"><span>${escape(operation.name)}</span><span class="badge text-bg-secondary">operation</span></a>"""
        }.mkString("\n")
        val body =
          if (operations.isEmpty)
            admin_empty_state("No form operations are enabled for this service.")
          else
            s"""<div class="list-group">${operations}</div>"""
        s"""<div class="col-12 col-lg-6">
           |  <article class="card admin-card h-100">
           |    <div class="card-body">
           |      <h2 class="card-title h5">${escape(service.name)}</h2>
           |      ${body}
           |    </div>
           |  </article>
           |</div>""".stripMargin
      }.mkString("\n")
      val navigation = admin_nav_card(Vector(
        "Dashboard" -> s"/web/${componentPath}/dashboard",
        "Admin configuration" -> s"/web/${componentPath}/admin"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} Forms",
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
    resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor).map { context =>
      val action = s"/form/${context.componentPath}/${context.servicePath}/${context.operationPath}"
      val formselector = Vector(context.componentPath, context.servicePath, context.operationPath).mkString(".")
      val effectiveValues = operation_form_prefill_values(subsystem, context, values)
      val effectiveValidation = validation.filter(_.webSchema.selector == context.webSchema.selector)
      val controls = operation_form_controls(context, effectiveValues, effectiveValidation)
      val hiddenContext = hidden_form_context_inputs(effectiveValues)
      val errorPanel = form_error_panel(effectiveValues) + form_validation_panel(effectiveValidation)
      val enctype = operation_form_enctype(context.webSchema, context.imageBinding)
      val debugPanel = operation_form_debug_panel(context, values, operationMode, showExecutionDebugPanel)
      Page(simple_page(
        title = s"${escape(context.component.name)}.${escape(context.serviceName)}.${escape(context.operationName)}",
        subtitle = "HTML form operation",
        body =
          s"""<article class="card admin-card" data-textus-page="static-form-operation" data-textus-section="operation-form">
             |  <div class="card-body">
             |    <div data-textus-section="form-errors">${errorPanel}</div>
             |    <form method="post" action="${escape(action)}"${enctype} data-textus-form="${escape(formselector)}">
             |      <div class="row g-3">
             |        <div class="col-12" data-textus-section="form-controls">${controls}</div>
             |      </div>
             |      ${hiddenContext}
             |      <div class="admin-action-row d-flex flex-wrap gap-2 mt-3" data-textus-section="form-actions">
             |        <button type="submit" class="btn btn-primary" data-textus-action="submit">Run</button>
             |        <a class="btn btn-outline-secondary" href="/form/${context.componentPath}" data-textus-action="operations">Operations</a>
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

  protected def operation_form_enctype(
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    imageBinding: Option[CmlOperationImageBinding] = None
  ): String =
    if (webSchema.fields.exists(field => is_blob_datatype(field.dataType.getOrElse(""))) ||
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
    resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map { context =>
        form_definition_json(
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displaySchema = admin_entity_create_schema(component, entityPath, webSchema)
      form_definition_json(
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displayFields = admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
      val displaySchema = webSchema.copy(fields = admin_display_web_fields(webSchema.fields, displayFields))
      form_definition_json(
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      form_definition_json(
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      form_definition_json(
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val definition = view_definition(component, viewName)
      val entityName = definition.map(_.entityName).getOrElse(strip_surface_suffix(viewPath, "view").getOrElse(viewPath))
      val webSchema = WebSchemaResolver.resolveView(
        component,
        componentPath,
        viewPath,
        Some(entityName),
        webDescriptor,
        viewFields = definition.flatMap(_.fieldsFor("summary")).orElse(admin_entity_view_fields(component, entityName, "summary")),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      form_definition_json(
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val definition = aggregate_definition(component, aggregateName)
      val entityName = definition.map(_.entityName).getOrElse(strip_surface_suffix(aggregatePath, "aggregate").getOrElse(aggregatePath))
      val webSchema = WebSchemaResolver.resolveAggregate(
        component,
        componentPath,
        aggregatePath,
        Some(entityName),
        webDescriptor,
        viewFields = admin_entity_view_fields(component, entityName, "summary"),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      form_definition_json(
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
    resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map(context => form_validation_json(validate_operation_form(context, values)))

  def validateOperationForm(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[FormValidationResult] =
    resolve_operation_web_schema_context(subsystem, componentName, serviceName, operationName, webDescriptor)
      .map(context => validate_operation_form(context, values))

  def validateComponentAdminEntityForm(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty,
    view: Option[String] = None
  ): Option[FormValidationResult] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val formSchema = view match {
        case Some("create") => admin_entity_create_schema(component, entityPath, webSchema)
        case Some(v) =>
          val displayFields = admin_entity_display_fields(component, entityPath, v, webSchema.fieldNames)
          webSchema.copy(fields = admin_display_web_fields(webSchema.fields, displayFields))
        case None => webSchema
      }
      validate_form(formSchema, values)
    }

  def validateComponentAdminDataForm(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    values: Map[String, String],
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[FormValidationResult] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      validate_form(webSchema, values)
    }

  protected def form_error_panel(values: Map[String, String]): String = {
    val xs = values.filter { case (key, _) =>
      (key == "error" || key.startsWith("error.")) &&
        !key.startsWith("error.diagnostic.")
    }
    if (xs.isEmpty) ""
    else
      s"""<div class="alert alert-danger admin-feedback" role="alert">
         |  <p class="alert-heading fw-semibold mb-2">Form submission failed.</p>
         |  <dl class="row mb-0">${property_rows(xs)}</dl>
         |</div>""".stripMargin
  }

  protected def form_validation_panel(
    validation: Option[FormValidationResult]
  ): String =
    validation match {
      case Some(result) if !result.valid =>
        val errors =
          result.errors.map(x => s"""<li>${escape(x.message)}</li>""").mkString("\n")
        val warnings =
          if (result.warnings.isEmpty)
            ""
          else
            s"""<p class="mb-1">Warnings</p><ul>${result.warnings.map(x => s"<li>${escape(x.message)}</li>").mkString("\n")}</ul>"""
        s"""<div class="alert alert-danger admin-feedback" role="alert">
           |  <p class="alert-heading fw-semibold mb-2">Validation failed.</p>
           |  <ul class="mb-0">${errors}</ul>
           |  ${warnings}
           |</div>""".stripMargin
      case _ =>
        ""
    }

  protected def operation_form_debug_panel(
    context: OperationWebSchemaContext,
    values: Map[String, String],
    operationMode: OperationMode,
    enabled: Boolean
  ): String =
    if (!enabled || !is_development_operation_mode(operationMode))
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
      val executionmetadata = RuntimeContext.ExecutionMetadata(
        sagaId = values.get("error.diagnostic.saga.id"),
        executionJobId = values.get("error.diagnostic.job.id"),
        executionTaskId = values.get("error.diagnostic.task.id"),
        traceId = values.get("error.diagnostic.trace.id"),
        executionId = values.get("error.diagnostic.id"),
        failure = values.get("error.diagnostic.failure"),
        inlineCallTree = values.get("error.diagnostic.calltree.json")
          .flatMap(x => new RecordDecoder().json(x).toOption)
      )
      execution_debug_panel(FormResultProperties(
        pageProperties,
        status,
        "text/plain",
        body,
        executionMetadata = executionmetadata,
        operationMode = operationMode,
        fieldConfidentiality = field_confidentiality(context.webSchema)
      ), pageProperties)
    }

  protected def field_confidentiality(
    schema: WebSchemaResolver.ResolvedWebSchema
  ): Map[String, DataConfidentiality] =
    schema.fields.map(field => field.name -> field.confidentiality).toMap

  protected def admin_form_fields(
    defaults: Vector[(String, String)],
    values: Map[String, String]
  ): Vector[(String, String)] = {
    val userValues = values.filterNot { case (key, _) => key == "error" || key.startsWith("error.") }
    val defaultKeys = defaults.map(_._1).toSet
    defaults.map {
      case (key, value) => key -> userValues.getOrElse(key, value)
    } ++ userValues.filterNot { case (key, _) => defaultKeys.contains(key) }.toVector.sortBy(_._1)
  }

  protected def admin_new_fields_value(values: Map[String, String]): String =
    values.getOrElse(
      "fields",
      visible_form_values(values)
        .toVector
        .sortBy(_._1)
        .map { case (key, value) => s"${key}=${value}" }
        .mkString("\n")
    )

  protected val hidden_form_context_exact_keys: Set[String] =
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
    hidden_form_context_exact_keys.contains(key) || key.startsWith("search.")

  protected def is_hidden_form_context_key(key: String): Boolean =
    isHiddenFormContextKey(key)

  protected def hidden_form_context_values(values: Map[String, String]): Vector[(String, String)] =
    values.toVector
      .filter { case (key, value) => is_hidden_form_context_key(key) && value.nonEmpty }
      .sortBy(_._1)

  protected def visible_form_values(values: Map[String, String]): Map[String, String] =
    values.filterNot { case (key, _) =>
      key == "error" || key.startsWith("error.") || is_hidden_form_context_key(key)
    }

  protected def hidden_form_context_inputs(values: Map[String, String]): String =
    hidden_form_context_values(values).map { case (key, value) =>
      s"""<input type="hidden" name="${escape(key)}" value="${escape(value)}" data-textus-field="${escape(key)}">"""
    }.mkString("\n")

  protected def hidden_form_context_query_suffix(values: Map[String, String]): String =
    hidden_form_context_values(values) match {
      case xs if xs.isEmpty => ""
      case xs =>
        xs.map { case (key, value) =>
          s"${escapeQuery(key)}=${escapeQuery(value)}"
        }.mkString("?", "&", "")
    }

  protected def admin_new_controls(
    schemaFields: Vector[WebSchemaResolver.ResolvedWebField],
    values: Map[String, String],
    fieldsId: String,
    placeholder: String,
    validation: Option[FormValidationResult] = None
  ): String =
    if (schemaFields.isEmpty)
      admin_fields_textarea(values, fieldsId, placeholder, "Use one name=value pair per line.")
    else {
      val userValues = visible_form_values(values).filterNot { case (key, _) => key == "fields" }
      val schemaNames = schemaFields.map(_.name).toSet
      val validationMessages = validation_messages_by_field(validation)
      val controls = schemaFields.map { field =>
        val key = field.name
        val value = userValues.getOrElse(key, "")
        admin_field_control(key, s"new-field-${NamingConventions.toNormalizedSegment(key)}", value, Some(field), validationMessages.getOrElse(key, Vector.empty))
      }.mkString("\n")
      val extras = userValues.filterNot { case (key, _) => schemaNames.contains(key) }
      val extraText = extras.toVector.sortBy(_._1).map { case (key, value) => s"${key}=${value}" }.mkString("\n")
      val extra =
        s"""<div class="mb-3">
           |  <label class="form-label" for="${escape(fieldsId)}">Additional fields</label>
           |  <textarea class="form-control" id="${escape(fieldsId)}" name="fields" rows="3">${escape(values.getOrElse("fields", extraText))}</textarea>
           |  <div class="form-text">Use one name=value pair per line for extension fields.</div>
           |</div>""".stripMargin
      s"${controls}\n${extra}"
    }

  protected def admin_record_controls(
    schemaFields: Vector[WebSchemaResolver.ResolvedWebField],
    defaults: Vector[(String, String)],
    values: Map[String, String],
    idPrefix: String,
    validation: Option[FormValidationResult] = None,
    includeExtensionFields: Boolean = true
  ): String = {
    val submittedValues =
      if (includeExtensionFields)
        visible_form_values(values)
      else {
        val schemaNames = schemaFields.map(_.name).toSet
        visible_form_values(values).filter { case (key, _) => is_admin_schema_field(schemaNames, key) }
      }
    val fields = admin_resolved_schema_ordered_fields(schemaFields, admin_form_fields(defaults, submittedValues))
    val validationMessages = validation_messages_by_field(validation)
    fields.map {
      case (Some(field), _, value) =>
        admin_field_control(field.name, s"${idPrefix}-${NamingConventions.toNormalizedSegment(field.name)}", value, Some(field), validationMessages.getOrElse(field.name, Vector.empty))
      case (None, key, value) =>
        admin_field_control(key, s"${idPrefix}-${NamingConventions.toNormalizedSegment(key)}", value, None)
    }.mkString("\n")
  }

  protected def admin_field_control(
    name: String,
    id: String,
    value: String,
    webField: Option[WebSchemaResolver.ResolvedWebField],
    validationMessages: Vector[FormValidationMessage] = Vector.empty
  ): String = {
    val descriptor = webField.map(_.asControl)
    val inputType = descriptor.flatMap(_.controlType).getOrElse("text")
    val required = if (descriptor.flatMap(_.required).getOrElse(false)) " required" else ""
    operation_parameter_control(
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

  protected def admin_resolved_schema_ordered_fields(
    schemaFields: Vector[WebSchemaResolver.ResolvedWebField],
    fields: Vector[(String, String)]
  ): Vector[(Option[WebSchemaResolver.ResolvedWebField], String, String)] = {
    val values = fields.toMap
    val schemaNames = schemaFields.map(_.name).toSet
    val schemaRows = schemaFields.map(field => (Some(field), field.name, admin_display_value(field.name, schemaNames, values)))
    val extensionRows = fields
      .filterNot { case (key, _) => is_admin_schema_field(schemaNames, key) || is_derived_backing_field(schemaNames, key) }
      .distinctBy(_._1)
      .sortBy(_._1)
      .map { case (key, value) => (None, key, value) }
    schemaRows ++ extensionRows
  }

  protected def is_derived_backing_field(
    schemaNames: Set[String],
    key: String
  ): Boolean =
    (key == "title" && schemaNames.contains("subject")) ||
      (key == "content" && schemaNames.contains("body"))

  protected def derived_backing_value(
    key: String,
    schemaNames: Set[String],
    values: Map[String, String]
  ): Option[String] =
    key match {
      case "subject" if schemaNames.contains("subject") => values.get("title")
      case "body" if schemaNames.contains("body") => values.get("content")
      case _ => None
    }

  protected def admin_schema_ordered_fields(
    schemaFields: Vector[String],
    fields: Vector[(String, String)]
  ): Vector[(String, String)] = {
    val values = fields.toMap
    val schemaNames = schemaFields.toSet
    val schemaRows = schemaFields.map(key => key -> admin_display_value(key, schemaNames, values))
    val extensionRows = fields.filterNot { case (key, _) => is_admin_schema_field(schemaNames, key) || is_derived_backing_field(schemaNames, key) }
    (schemaRows ++ extensionRows).distinctBy(_._1)
  }

  protected def is_admin_schema_field(
    schemaNames: Set[String],
    key: String
  ): Boolean =
    schemaNames.contains(key) ||
      schemaNames.exists(name => NamingConventions.equivalentByNormalized(name, key))

  protected def admin_display_value(
    key: String,
    schemaNames: Set[String],
    values: Map[String, String]
  ): String =
    admin_value_by_name(values, key)
      .orElse(derived_backing_value(key, schemaNames, values))
      .getOrElse("")

  protected def admin_value_by_name(
    values: Map[String, String],
    key: String
  ): Option[String] =
    values.get(key).orElse {
      values.collectFirst {
        case (candidate, value) if NamingConventions.equivalentByNormalized(candidate, key) => value
      }
    }

  protected def admin_schema_field_names(
    adminFields: Vector[WebDescriptor.AdminField],
    fallback: Vector[String]
  ): Vector[String] =
    adminFields.map(_.name) match {
      case xs if xs.nonEmpty => xs
      case _ => fallback
    }

  protected def admin_field_controls(
    adminFields: Vector[WebDescriptor.AdminField]
  ): Map[String, WebDescriptor.FormControl] =
    adminFields.map(x => x.name -> x.control).toMap

  protected def admin_fields_textarea(
    values: Map[String, String],
    fieldsId: String,
    placeholder: String,
    help: String
  ): String =
    s"""<div class="mb-3">
       |  <label class="form-label" for="${escape(fieldsId)}">Fields</label>
       |  <textarea class="form-control" id="${escape(fieldsId)}" name="fields" rows="8" placeholder="${placeholder}">${escape(admin_new_fields_value(values))}</textarea>
       |  <div class="form-text">${escape(help)}</div>
       |</div>""".stripMargin

  protected def operation_form_controls(
    context: OperationWebSchemaContext,
    values: Map[String, String],
    validation: Option[FormValidationResult] = None
  ): String = {
    val webSchema = context.webSchema
    val fields = webSchema.fields
    val bindingControls = operation_binding_controls(context)
    if (fields.isEmpty)
      s"""${operation_form_fields_textarea(visible_form_values(values).filterNot { case (key, _) => is_operation_binding_only_field(context, key) }, "Fields")}
         |${bindingControls}""".stripMargin
    else {
      val fieldNames = fields.map(_.name).toSet
      val validationMessages = validation_messages_by_field(validation)
      val controls = fields.map { field =>
        val name = field.name
        val id = s"field-${NamingConventions.toNormalizedSegment(name)}"
        val value = values.getOrElse(name, "")
        val descriptor = Some(field.asControl)
        val required = if (field.required) " required" else ""
        val help = web_schema_field_help(field)
        val fieldMessages = validationMessages.getOrElse(name, Vector.empty)
        operation_parameter_control(
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
          validationMessages = fieldMessages,
          textusfieldselector = true
        )
      }.mkString("\n")
      val extraValues = visible_form_values(values).filterNot { case (key, _) =>
        fieldNames.contains(key) || is_operation_binding_only_field(context, key)
      }
      val extra =
        if (extraValues.isEmpty)
          operation_form_fields_textarea(Map.empty, "Additional fields", rows = 3)
        else
          operation_form_fields_textarea(extraValues, "Additional fields", rows = 3)
      s"""${controls}
         |${bindingControls}
         |${extra}""".stripMargin
    }
  }

  protected def operation_binding_controls(
    context: OperationWebSchemaContext
  ): String =
    Vector(
      context.imageBinding.filter(_.createsAttachment).map(binding => operation_image_attachment_controls(context, binding)),
      context.associationBinding.filter(_.isAutomaticCreate).map(binding => operation_association_binding_controls(context, binding))
    ).flatten.mkString("\n")

  protected def operation_image_attachment_controls(
    context: OperationWebSchemaContext,
    binding: CmlOperationImageBinding
  ): String = {
    val schemaFields = context.webSchema.fields.map(_.name).toSet
    val roles = if (binding.roles.nonEmpty) binding.roles else Vector("primary", "cover", "thumbnail", "gallery", "inline")
    val options = roles.map(role => s"""<option value="${escape(role)}">""").mkString
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

  protected def operation_association_binding_controls(
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
        if (targetFields.contains(name)) s"${humanize_field_name(name)} Target id"
        else if (sourceFields.contains(name)) s"${humanize_field_name(name)} Source id"
        else humanize_field_name(name)
      s"""<div class="col-md-4">
         |  <label class="form-label" for="operationAssociation${escape(NamingConventions.toNormalizedSegment(name))}">${escape(label)}</label>
         |  <input class="form-control" id="operationAssociation${escape(NamingConventions.toNormalizedSegment(name))}" name="${escape(name)}">
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

  protected def validation_messages_by_field(
    validation: Option[FormValidationResult]
  ): Map[String, Vector[FormValidationMessage]] =
    validation.toVector
      .flatMap(result => result.errors ++ result.warnings)
      .flatMap(message => message.field.map(_ -> message))
      .groupMap(_._1)(_._2)

  protected def resolve_operation_web_schema_context(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String,
    operationName: String,
    webDescriptor: WebDescriptor
  ): Option[OperationWebSchemaContext] =
    for {
      component <- find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(x => NamingConventions.equivalentByNormalized(x.name, serviceName))
      operation <- service.operations.operations.find(x => NamingConventions.equivalentByNormalized(x.name, operationName))
      context <- {
        val selectorCandidates = operation_selector_candidates(component, componentName, service.name, operation.name)
        val resolvedSelector = selectorCandidates.find(selector =>
          webDescriptor.form.contains(selector) ||
            webDescriptor.exposureOf(selector) != WebDescriptor.Exposure.Internal
        ).orElse(selectorCandidates.headOption).getOrElse(operation_selector(component.name, service.name, operation.name))
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
              admin_field_controls(adminFields)
          val operationParameters = operation.specification.request.parameters.toVector
          val cmlParameters =
            if (operationParameters.nonEmpty)
              Vector.empty
            else
              cml_operation_parameters(component, service.name, operation.name)
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
            associationBinding = operation_association_binding(component, operation),
            imageBinding = operation_image_binding(component, operation)
          ))
        }
      }
    } yield
      context

  protected def operation_association_binding(
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

  protected def operation_image_binding(
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

  protected def operation_selector_candidates(
    component: Component,
    requestedComponentName: String,
    serviceName: String,
    operationName: String
  ): Vector[String] =
    (Vector(requestedComponentName, component.name) ++
      component.artifactMetadata.toVector.flatMap(m => m.component.toVector :+ m.name))
      .foldLeft(Vector.empty[String]) { (z, componentCandidate) =>
        val selector = operation_selector(componentCandidate, serviceName, operationName)
        if (z.contains(selector))
          z
        else
          z :+ selector
      }

  protected def cml_operation_parameters(
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
            getOrElse(BaseContent.Builder(field.name).label(humanize_field_name(field.name)).build()),
          kind = ParameterDefinition.Kind.Argument,
          domain = ValueDomain(
            datatype = cml_operation_datatype(field.datatype),
            multiplicity = cml_operation_multiplicity(field.multiplicity)
          ),
          web = org.goldenport.schema.WebColumn(
            controlType = field.controlType.orElse(cml_operation_control_type(field.name, field.datatype)),
            required = field.required,
            placeholder = field.placeholder,
            help = field.help
          )
        )
      }
    }

  protected def humanize_field_name(
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

  protected def cml_operation_control_type(
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

  protected def cml_operation_datatype(
    datatype: String
  ): org.goldenport.schema.DataType =
    Option(datatype).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("") match {
      case "boolean" | "bool" => XBoolean
      case "int" | "integer" | "long" | "short" => XInt
      case "datetime" | "date-time" | "timestamp" => XDateTime
      case _ => XString
    }

  protected def cml_operation_multiplicity(
    multiplicity: String
  ): Multiplicity =
    Option(multiplicity).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("") match {
      case "?" | "0..1" | "zeroone" | "zero-one" | "zero_one" => Multiplicity.ZeroOne
      case _ => Multiplicity.One
    }

  protected def operation_form_prefill_values(
    subsystem: Subsystem,
    context: OperationWebSchemaContext,
    values: Map[String, String]
  ): Map[String, String] =
    if (!NamingConventions.equivalentByNormalized(context.serviceName, "aggregate"))
      values
    else
      values.get("id").filter(_.nonEmpty) match {
        case Some(id) =>
          aggregate_name_for_operation(context.component, context.operationName)
            .flatMap { aggregateName =>
              admin_operation_value_lines(
                subsystem,
                "/admin/aggregate/read",
                Record.data(
                  "component" -> context.componentPath,
                  "aggregate" -> NamingConventions.toNormalizedSegment(aggregateName),
                  "id" -> id
                )
              ).map(lines => field_lines(lines.mkString("\n")).toMap)
            }
            .map(prefill => prefill ++ values)
            .getOrElse(values)
        case None =>
          values
      }

  protected def aggregate_name_for_operation(
    component: Component,
    operationName: String
  ): Option[String] = {
    val operationKey = NamingConventions.toNormalizedSegment(operationName)
    component.aggregateDefinitions.find { definition =>
      val names = definition.creates.map(_.name) ++ definition.commands.map(_.name)
      names.exists(name => NamingConventions.equivalentByNormalized(name, operationKey))
    }.map(_.name)
  }

  protected def form_definition_json(
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
      "actions" -> Json.arr(navigation.actions.map(form_definition_action_json)*),
      "fields" -> Json.arr((webSchema.fields.map(web_field_json) ++ operationBindings.toVector.flatMap(operation_binding_field_jsons))*)
    )
    val bindings = operationBindings.toVector.map(context => "bindings" -> operation_bindings_json(context))
    Page(Json.obj((base ++ bindings)*).noSpaces)
  }

  protected def form_definition_action_json(
    action: FormDefinitionAction
  ): Json =
    Json.obj(
      "name" -> Json.fromString(action.name),
      "method" -> Json.fromString(action.method),
      "path" -> Json.fromString(action.path)
    )

  protected def validate_form(
    webSchema: WebSchemaResolver.ResolvedWebSchema,
    values: Map[String, String]
  ): FormValidationResult = {
    val fieldNames = webSchema.fields.map(_.name).toSet
    val errors = webSchema.fields.flatMap { field =>
      validate_field(field, values.getOrElse(field.name, ""))
    }
    val warnings = values.toVector
      .filterNot { case (key, _) => key == "fields" || fieldNames.contains(key) }
      .sortBy(_._1)
      .map { case (key, _) =>
        FormValidationMessage(Some(key), "unknown-field", s"${key} is not defined in the form schema.")
      }
    FormValidationResult(webSchema, values, errors, warnings)
  }

  protected def validate_operation_form(
    context: OperationWebSchemaContext,
    values: Map[String, String]
  ): FormValidationResult =
    validate_form(
      context.webSchema,
      values.filterNot { case (key, _) => is_operation_binding_only_field(context, key) }
    )

  protected def is_operation_binding_only_field(
    context: OperationWebSchemaContext,
    key: String
  ): Boolean =
    is_operation_binding_field(context, key) &&
      !context.webSchema.fields.exists(_.name == key)

  protected def is_operation_binding_field(
    context: OperationWebSchemaContext,
    key: String
  ): Boolean =
    context.imageBinding.exists(_ => is_image_binding_field(key)) ||
      context.associationBinding.exists(binding => is_association_binding_field(binding, key))

  protected def is_image_binding_field(key: String): Boolean =
    key.startsWith("imageAttachments.") ||
      key.startsWith("blob.") ||
      key.startsWith("blobId.")

  protected def is_association_binding_field(
    binding: CmlOperationAssociationBinding,
    key: String
  ): Boolean =
    (binding.parameters ++
      binding.sourceEntityIdParameters ++
      binding.targetIdParameters ++
      binding.sortOrderParameters).contains(key) ||
      binding.targetIdParameters.exists(p => key == s"${p}.sortOrder")

  protected def operation_binding_field_jsons(
    context: OperationWebSchemaContext
  ): Vector[Json] = {
    val schemaFields = context.webSchema.fields.map(_.name).toSet
    (context.imageBinding.filter(_.createsAttachment).toVector.flatMap(operation_image_binding_field_jsons) ++
      context.associationBinding.filter(_.isAutomaticCreate).toVector.flatMap(operation_association_binding_field_jsons)).
      filterNot(json => json.hcursor.downField("name").as[String].toOption.exists(schemaFields.contains))
  }

  protected def operation_image_binding_field_jsons(
    binding: CmlOperationImageBinding
  ): Vector[Json] = {
    val roles = if (binding.roles.nonEmpty) binding.roles else Vector("primary", "cover", "thumbnail", "gallery", "inline")
    (0 until 3).toVector.flatMap { index =>
      val base = s"imageAttachments.${index}"
      Vector(
        Some(binding_field_json(s"${base}.role", "Image role", "text", "image", "role", values = roles)),
        Option.when(binding.acceptsExistingBlobId)(binding_field_json(s"${base}.blobId", "Existing Blob id", "text", "image", "existingBlobId")),
        Option.when(binding.acceptsUpload)(binding_field_json(s"${base}.file", "Upload image", "file", "image", "upload")),
        Some(binding_field_json(s"${base}.sortOrder", "Sort order", "number", "image", "sortOrder"))
      ).flatten
    }
  }

  protected def operation_association_binding_field_jsons(
    binding: CmlOperationAssociationBinding
  ): Vector[Json] = {
    val sourceFields =
      if (binding.sourceEntityIdMode == CmlOperationAssociationBinding.SourceEntityIdModeParameter)
        binding.sourceEntityIdParameters
      else
        Vector.empty
    val targets = binding.targetIdParameters.map(name =>
      binding_field_json(name, humanize_field_name(name), "text", "association", "targetEntityId")
    )
    val sources = sourceFields.map(name =>
      binding_field_json(name, humanize_field_name(name), "text", "association", "sourceEntityId")
    )
    val sorts = binding.sortOrderParameters.map(name =>
      binding_field_json(name, humanize_field_name(name), "number", "association", "sortOrder")
    )
    (sources ++ targets ++ sorts).distinct
  }

  protected def binding_field_json(
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

  protected def operation_bindings_json(
    context: OperationWebSchemaContext
  ): Json =
    Json.obj(
      "imageBinding" -> context.imageBinding.map(image_binding_json).getOrElse(Json.Null),
      "associationBinding" -> context.associationBinding.map(association_binding_json).getOrElse(Json.Null)
    )

  protected def image_binding_json(
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

  protected def association_binding_json(
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

  protected def validate_field(
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
        validate_multiplicity(field, value) ++
        validate_field_values(field, value) ++
        validate_datatype(field, value) ++
        validate_hints(field, value)
  }

  protected def validate_hints(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] =
    form_field_values(field, value).flatMap { x =>
      validate_hint_value(field, x)
    }

  protected def validate_hint_value(
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

  protected def validate_multiplicity(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] = {
    val multiplicity = field.multiplicity.map(_.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    val values =
      if (field.multiple || field.asControl.values.nonEmpty || field.controlType == "select")
        split_form_field_values(value)
      else
        Vector(value)
    if (!field.multiple && is_single_multiplicity(multiplicity) && values.size > 1)
      Vector(FormValidationMessage(Some(field.name), "multiplicity", s"${field.label.getOrElse(field.name)} accepts only one value."))
    else
      Vector.empty
  }

  protected def validate_field_values(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] = {
    val allowed = field.asControl.values.toSet
    if (allowed.isEmpty)
      Vector.empty
    else
      form_field_values(field, value)
        .filterNot(allowed.contains)
        .map(x => FormValidationMessage(Some(field.name), "invalid-value", s"${x} is not an allowed value for ${field.label.getOrElse(field.name)}."))
  }

  protected def validate_datatype(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[FormValidationMessage] =
    form_field_values(field, value).flatMap { x =>
      validate_datatype_value(field, x)
    }

  protected def validate_datatype_value(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Option[FormValidationMessage] = {
    val datatype = field.dataType.map(_.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    val controlType = field.controlType.toLowerCase(java.util.Locale.ROOT)
    val label = field.label.getOrElse(field.name)
    def error(code: String, expected: String): Option[FormValidationMessage] =
      Some(FormValidationMessage(Some(field.name), code, s"${label} must be ${expected}."))
    if (is_boolean_type(datatype, controlType) && !is_boolean_value(value))
      error("datatype", "a boolean value")
    else if (is_integer_type(datatype) && !value.toLongOption.isDefined)
      error("datatype", "an integer")
    else if (is_number_type(datatype, controlType) && !scala.util.Try(BigDecimal(value)).isSuccess)
      error("datatype", "a number")
    else if (is_date_type(datatype, controlType) && !scala.util.Try(java.time.LocalDate.parse(value)).isSuccess)
      error("datatype", "a date value")
    else if (is_datetime_type(datatype, controlType) && !is_datetime_value(value))
      error("datatype", "a datetime value")
    else
      None
  }

  protected def form_field_values(
    field: WebSchemaResolver.ResolvedWebField,
    value: String
  ): Vector[String] =
    if (field.multiple)
      split_form_field_values(value)
    else
      Vector(value)

  protected def split_form_field_values(
    value: String
  ): Vector[String] =
    value.split("[,\n]").toVector.map(_.trim).filter(_.nonEmpty)

  protected def is_single_multiplicity(
    multiplicity: String
  ): Boolean =
    multiplicity.contains("one") && !multiplicity.contains("many")

  protected def is_boolean_type(
    datatype: String,
    controlType: String
  ): Boolean =
    datatype.contains("bool") || controlType == "checkbox"

  protected def is_boolean_value(
    value: String
  ): Boolean =
    Set("true", "false", "yes", "no", "on", "off", "1", "0").contains(value.toLowerCase(java.util.Locale.ROOT))

  protected def is_integer_type(
    datatype: String
  ): Boolean =
    datatype.contains("int") || datatype.contains("long")

  protected def is_number_type(
    datatype: String,
    controlType: String
  ): Boolean =
    controlType == "number" || datatype.contains("decimal") || datatype.contains("double") || datatype.contains("float") || datatype.contains("number")

  protected def is_date_type(
    datatype: String,
    controlType: String
  ): Boolean =
    (controlType == "date" || datatype.contains("date")) && !is_datetime_type(datatype, controlType)

  protected def is_datetime_type(
    datatype: String,
    controlType: String
  ): Boolean =
    controlType == "datetime-local" || datatype.contains("datetime") || datatype.contains("timestamp")

  protected def is_datetime_value(
    value: String
  ): Boolean =
    scala.util.Try(java.time.LocalDateTime.parse(value)).isSuccess ||
      scala.util.Try(java.time.OffsetDateTime.parse(value)).isSuccess

  protected def form_validation_json(
    result: FormValidationResult
  ): Page =
    Page(Json.obj(
      "selector" -> Json.fromString(result.webSchema.selector),
      "surface" -> Json.fromString(result.webSchema.surface.name),
      "valid" -> Json.fromBoolean(result.valid),
      "errors" -> Json.arr(result.errors.map(form_validation_message_json)*),
      "warnings" -> Json.arr(result.warnings.map(form_validation_message_json)*),
      "fields" -> Json.arr(result.webSchema.fields.map(web_field_json)*)
    ).noSpaces)

  protected def form_validation_message_json(
    message: FormValidationMessage
  ): Json =
    Json.obj(
      "field" -> message.field.map(Json.fromString).getOrElse(Json.Null),
      "code" -> Json.fromString(message.code),
      "message" -> Json.fromString(message.message)
    )

  protected def web_field_json(
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
      "validation" -> web_validation_hints_json(field.validation),
      "source" -> Json.fromString(field.source.toString)
    )
  }

  protected def web_validation_hints_json(
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

  protected def web_schema_field_help(
    field: WebSchemaResolver.ResolvedWebField
  ): String =
    field.help.getOrElse {
      Vector(
        field.dataType.getOrElse("unknown"),
        field.multiplicity.getOrElse("unknown")
      ).mkString("; ")
    }

  protected def operation_parameter_required(
    parameter: org.goldenport.protocol.spec.ParameterDefinition
  ): Boolean =
    !Option(parameter.domain.multiplicity).map(_.toString.toLowerCase).exists(x => x.contains("zero"))

  protected def operation_parameter_input_type(
    parameter: org.goldenport.protocol.spec.ParameterDefinition
  ): String = {
    val name = parameter.name.toLowerCase
    val datatypeName = Option(parameter.domain.datatype).map(_.name).getOrElse("")
    val datatype = datatypeName.toLowerCase(java.util.Locale.ROOT)
    if (is_blob_datatype(datatypeName)) "file"
    else if (datatype.contains("bool")) "checkbox"
    else if (operation_parameter_multiline(name, datatype)) "textarea"
    else if (name.contains("password") || name.contains("secret") || name.contains("token")) "password"
    else if (datatype.contains("int") || datatype.contains("long") || datatype.contains("decimal") || datatype.contains("number")) "number"
    else if (datatype.contains("datetime") || datatype.contains("timestamp")) "datetime-local"
    else if (datatype.contains("date")) "date"
    else "text"
  }

  protected def operation_parameter_multiline(
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

  protected def operation_parameter_control(
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
    validationMessages: Vector[FormValidationMessage] = Vector.empty,
    textusfieldselector: Boolean = false
  ): String = {
    val displayLabel = label.orElse(descriptor.flatMap(_.label)).getOrElse(name)
    val fieldselector = if (textusfieldselector) s""" data-textus-field="${escape(name)}"""" else ""
    val invalidClass = if (validationMessages.nonEmpty) " is-invalid" else ""
    val validationAttr = validation_attribute_text(descriptor.map(_.validation).getOrElse(org.goldenport.schema.WebValidationHints.empty))
    val feedback =
      if (validationMessages.isEmpty)
        ""
      else
        s"""<div class="invalid-feedback">${escape(validationMessages.map(_.message).mkString(" "))}</div>"""
    if (descriptor.exists(_.hidden) || inputType == "hidden") {
      s"""<input type="hidden" id="${escape(id)}" name="${escape(name)}" value="${escape(value)}"${fieldselector}>"""
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
            s"""<option value=""${selected}>${escape(label)}</option>"""
          }
          .toVector
      val options = (placeholderOption ++ values.map { candidate =>
        val selected = if (candidate == value) " selected" else ""
        s"""<option value="${escape(candidate)}"${selected}>${escape(candidate)}</option>"""
      }).mkString("\n")
      s"""<div class="mb-3"${fieldselector}>
         |  <label class="form-label" for="${escape(id)}">${escape(displayLabel)}</label>
         |  <select class="form-select${invalidClass}" id="${escape(id)}" name="${escape(name)}"${required}${multiple}${disabled}${validationAttr}>
         |    ${options}
         |  </select>
         |  ${feedback}
         |  <div class="form-text">${escape(help)}</div>
         |</div>""".stripMargin
    } else if (inputType == "checkbox") {
      val checked =
        if (Set("true", "on", "1", "yes").contains(value.toLowerCase)) " checked" else ""
      val disabled = if (readonly) " disabled" else ""
      s"""<div class="mb-3 form-check"${fieldselector}>
         |  <input type="hidden" name="${escape(name)}" value="false">
         |  <input class="form-check-input${invalidClass}" id="${escape(id)}" name="${escape(name)}" type="checkbox" value="true"${checked}${required}${disabled}${validationAttr}>
         |  <label class="form-check-label" for="${escape(id)}">${escape(displayLabel)}</label>
         |  ${feedback}
         |  <div class="form-text">${escape(help)}</div>
         |</div>""".stripMargin
    } else if (inputType == "textarea") {
      val readonlyAttr = if (readonly) " readonly" else ""
      val placeholderAttr = placeholder.map(x => s""" placeholder="${escape(x)}"""").getOrElse("")
      s"""<div class="mb-3"${fieldselector}>
         |  <label class="form-label" for="${escape(id)}">${escape(displayLabel)}</label>
         |  <textarea class="form-control${invalidClass}" id="${escape(id)}" name="${escape(name)}" rows="5"${required}${readonlyAttr}${placeholderAttr}${validationAttr}>${escape(value)}</textarea>
         |  ${feedback}
         |  <div class="form-text">${escape(help)}</div>
         |</div>""".stripMargin
    } else if (inputType == "file") {
      val disabled = if (readonly) " disabled" else ""
      s"""<div class="mb-3"${fieldselector}>
         |  <label class="form-label" for="${escape(id)}">${escape(displayLabel)}</label>
         |  <input class="form-control${invalidClass}" id="${escape(id)}" name="${escape(name)}" type="file"${required}${disabled}${validationAttr}>
         |  ${feedback}
         |  <div class="form-text">${escape(help)}</div>
         |</div>""".stripMargin
    } else {
      val readonlyAttr = if (readonly) " readonly" else ""
      val placeholderAttr = placeholder.map(x => s""" placeholder="${escape(x)}"""").getOrElse("")
      s"""<div class="mb-3"${fieldselector}>
         |  <label class="form-label" for="${escape(id)}">${escape(displayLabel)}</label>
         |  <input class="form-control${invalidClass}" id="${escape(id)}" name="${escape(name)}" type="${escape(inputType)}" value="${escape(value)}"${required}${readonlyAttr}${placeholderAttr}${validationAttr}>
         |  ${feedback}
         |  <div class="form-text">${escape(help)}</div>
         |</div>""".stripMargin
    }
  }

  protected def validation_attribute_text(
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
      attrs.map { case (key, value) => s""" ${key}="${escape(value)}"""" }.mkString
  }

  protected def operation_parameter_help(
    parameter: org.goldenport.protocol.spec.ParameterDefinition
  ): String = {
    val kind = parameter.kind.toString
    val datatype = Option(parameter.domain.datatype).map(_.name).getOrElse("unknown")
    val multiplicity = Option(parameter.domain.multiplicity).map(_.toString).getOrElse("unknown")
    s"${kind}; ${datatype}; ${multiplicity}"
  }

  protected def is_blob_datatype(datatype: String): Boolean =
    datatype.trim.equalsIgnoreCase("blob")

  protected def operation_form_fields_textarea(
    values: Map[String, String],
    label: String,
    rows: Int = 6
  ): String = {
    val initialFields = form_initial_fields(values)
    s"""<div class="mb-3" data-textus-field="fields">
       |  <label class="form-label" for="formFields">${escape(label)}</label>
       |  <textarea class="form-control" id="formFields" name="fields" rows="${rows}" placeholder="name=value&#10;keyword=sample">${initialFields}</textarea>
       |  <div class="form-text">Use one name=value pair per line. Query-style values are also accepted.</div>
       |</div>""".stripMargin
  }
}
