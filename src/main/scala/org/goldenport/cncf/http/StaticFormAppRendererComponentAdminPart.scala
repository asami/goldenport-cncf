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
trait StaticFormAppRendererComponentAdminPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererSystemAdminPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  def renderComponentAdmin(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map(renderComponentAdmin(_, NamingConventions.toNormalizedSegment(componentName), webDescriptor))

  def renderComponentAdminDescriptor(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      Page(simple_page(
        title = s"${escape(component.name)} Web Descriptor",
        subtitle = "Component Management Console descriptor view",
        body =
          s"""${admin_nav_card(Vector(
               "Component admin" -> s"/web/${componentPath}/admin",
               "System descriptor" -> "/web/system/admin/descriptor"
             ))}
             |${web_descriptor_section_nav}
             |${web_descriptor_control_tables(webDescriptor, Some(componentPath))}
             |${web_descriptor_asset_composition_table(webDescriptor, Some(componentPath))}
             |${web_descriptor_json_panel(
               "completed-descriptor",
               "Completed Descriptor JSON",
               "The completed view applies framework defaults and resolves component route placeholders for this component.",
               web_descriptor_json(webDescriptor, completed = true, componentSegment = Some(componentPath))
             )}
             |${web_descriptor_json_panel(
               "configured-descriptor",
               "Configured Descriptor JSON",
               "The configured view keeps explicit descriptor entries for comparison.",
               web_descriptor_json(webDescriptor, completed = false)
             )}""".stripMargin
      ))
    }

  def renderSystemManual(
    subsystem: Subsystem
  ): Page =
    subsystem.components.headOption match {
      case Some(component) =>
        Page(system_manual_page(subsystem, component))
      case None =>
        Page(simple_page(
          title = "System Specification",
          subtitle = "Generated runtime specification",
          body = admin_card("No components", "<p>No component reference entries are available.</p>")
        ))
    }

  def renderSystemDocument(
    subsystem: Subsystem
  ): Page = {
    val body =
      s"""${manual_card("Generated documents",
           s"""<p>CNCF-generated runtime documents expose implementation-facing specifications and machine-readable interface descriptions.</p>
              |${admin_link_list_group(Vector(
                "Generated Specification" -> "/web/system/document/specification",
                "OpenAPI JSON" -> "/web/system/document/specification/openapi.json",
                "MCP endpoint" -> "/mcp",
                "System dashboard" -> "/web/system/dashboard",
                "Console" -> "/web/console"
              ))}""".stripMargin)}
         |${manual_card("Component documents", manual_component_document_links(subsystem.components))}
         |${manual_card("Document model", """<p class="mb-0">Use <strong>Specification</strong> for generated CNCF metadata. Component-packaged <strong>User Guide</strong> and <strong>Reference Manual</strong> documents remain product documents owned by each component.</p>""")}""".stripMargin
    Page(simple_page("System Documents", "Specifications and component-packaged documents", body))
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
          Vector("App" -> s"/web/${escape(path)}")
        case Vector(app) =>
          Vector("App" -> s"/web/${escape(app)}")
        case apps =>
          apps.map(app => s"App: ${app}" -> s"/web/${escape(app)}")
      }
      s"""<div class="list-group-item">
         |  <div class="d-flex flex-wrap justify-content-between gap-2 align-items-center">
         |    <strong>${escape(component.name)}</strong>
         |    ${admin_action_row(appLinks ++ Vector(
           "Admin" -> s"/web/${escape(path)}/admin",
           "Document" -> s"/web/${escape(path)}/document"
         ), primary = false)}
         |  </div>
         |</div>""".stripMargin
    }.mkString("\n")
    val recommendations =
      if (componentLinks.isEmpty)
        admin_card(
          "Recommended Links",
          admin_link_list_group(Vector(
            "System documents" -> "/web/system/document",
            "System admin" -> "/web/system/admin",
            "System dashboard" -> "/web/system/dashboard",
            "Performance" -> "/web/system/performance"
          ))
        )
      else
        admin_card(
          "Recommended Links",
          s"""${admin_link_list_group(Vector(
               "System documents" -> "/web/system/document",
               "System admin" -> "/web/system/admin",
               "System dashboard" -> "/web/system/dashboard",
               "Performance" -> "/web/system/performance"
             ))}
             |<h3 class="h6 mt-3">Components</h3>
             |<div class="list-group">${componentLinks}</div>""".stripMargin
        )
    Page(simple_page(
      title = "CNCF Runtime Help",
      subtitle = "Development and demo entry points",
      body =
        s"""${admin_card(
             "Runtime",
             s"""${admin_table(
                  None,
                  s"""<tr><th>Subsystem</th><td>${escape(subsystem.name)}</td></tr>
                     |<tr><th>Operation mode</th><td>${escape(runtime.operationMode.name)}</td></tr>
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
      component <- find_component(subsystem, componentName)
    } yield Page(manual_page(
      title = s"${escape(component.name)} Specification",
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
      component <- find_component(subsystem, componentName)
    } yield {
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val generated =
        Vector(
          "Generated Specification" -> s"/web/${escape(componentPath)}/document/specification",
          "OpenAPI JSON" -> "/web/system/document/specification/openapi.json",
          "MCP endpoint" -> "/mcp"
        )
      val packaged =
        if (documents.isEmpty)
          web_empty_state("No packaged User Guide, Reference Manual, or component Specification documents were found.")
        else
          admin_link_list_group(documents.map(x => x.title -> x.href))
      val body =
        s"""${manual_card("Generated specification",
             s"""<p>CNCF generates this read-only specification from component, service, operation, schema, and projection metadata.</p>
                |${admin_link_list_group(generated)}""".stripMargin)}
           |${manual_card("Packaged component documents", packaged)}
           |${manual_card("Document roles",
             """<div class="table-responsive">
                |  <table class="table table-sm table-hover align-middle mb-0">
                |    <tbody>
                |      <tr><th>Specification</th><td>Generated or component-packaged technical contract for developers and operators.</td></tr>
                |      <tr><th>User Guide</th><td>Task-oriented guide for people using the component or application feature.</td></tr>
                |      <tr><th>Reference Manual</th><td>Human-authored detailed reference that complements generated metadata.</td></tr>
                |    </tbody>
                |  </table>
                |</div>""".stripMargin)}""".stripMargin
      Page(simple_page(s"${escape(component.name)} Documents", "Specifications, user guides, and reference manuals", body))
    }

  def renderComponentManualService(
    subsystem: Subsystem,
    componentName: String,
    serviceName: String
  ): Option[Page] =
    for {
      component <- find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, serviceName))
    } yield Page(manual_page(
      title = s"${escape(component.name)}.${escape(service.name)} Specification",
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
      component <- find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, serviceName))
      operation <- service.operations.operations.find(o => NamingConventions.equivalentByNormalized(o.name, operationName))
    } yield Page(manual_page(
      title = s"${escape(component.name)}.${escape(service.name)}.${escape(operation.name)} Specification",
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
    find_component(subsystem, componentName).map { component =>
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
           |  <td><a href="/web/${componentPath}/admin/entities/${entityPath}">${escape(descriptor.entityName)}</a></td>
           |  <td><code>${escape(descriptor.collectionId.name)}</code></td>
           |  <td>${escape(descriptor.entityKind.toString)}</td>
           |  <td>${escape(descriptor.usageKind.toString)}</td>
           |  <td>${escape(descriptor.effectiveOperationKind.toString)}</td>
           |  <td>${escape(descriptor.applicationDomain.toString)}</td>
           |  <td>${descriptor.workingSet.map(_.entityIds.size.toString).getOrElse("none")}</td>
           |  <td><code>${escape(workingsetpolicy)}</code></td>
           |  <td>${escape(policysource)}</td>
           |  <td><code>${escape(workingSetState)}</code></td>
           |  <td>${residentCount}</td>
           |  <td>${escape(workingSetError)}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          admin_empty_state("No entity runtime descriptors are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>Entity</th><th>Collection</th><th>Kind</th><th>Usage</th><th>Operation</th><th>Domain</th><th>Working set</th><th>Policy</th><th>Source</th><th>Status</th><th>Resident</th><th>Error</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      val nav = admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} Entity Administration",
        subtitle = "Entity CRUD management baseline",
        body =
          s"""${nav}
             |${admin_card("Entity CRUD", body)}
             |${admin_storage_shape_section(component, None, detailed = false)}""".stripMargin
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "entity", entityPath)
      )
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val displayFields = admin_entity_display_fields(
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
      val searchFields = admin_entity_searchable_fields(component, entityPath, WebTableColumnResolver.defaultViewName)
      val filterFields = displayFields
      val searchProfile = SearchPlanningProfile(
        searchableFields = searchFields,
        filterFields = filterFields,
        sortableFields = displayFields
      )
      val semanticUnsupported = admin_search_mode(searchContext).exists(_ != SearchMode.FullText)
      val searchValues =
        if (semanticUnsupported)
          Map.empty[String, String]
        else
          admin_search_values(searchContext, searchProfile)
      val result =
        if (semanticUnsupported)
          AdminListResult(Vector.empty, 1, effectivePageRequest.pageSize, false, Some(0), Vector.empty)
        else
          admin_entity_list(subsystem, componentPath, entityPath, effectivePageRequest, searchValues)
      val warningHtml = admin_warnings(result.warnings)
      val searchFeedback =
        if (semanticUnsupported)
          """<div class="alert alert-warning admin-search-feedback" role="alert">Semantic or hybrid search is not configured for this Static Form page.</div>"""
        else
          ""
      val searchHref = admin_search_href(basePath, searchContext, searchProfile)
      val searchControls = admin_search_card(
        basePath,
        searchContext,
        searchProfile,
        filterFields,
        displayFields,
        result.total,
        result.items.size
      )
      val table = admin_read_result_list_table(
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
      val nav = admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Entity types" -> s"/web/${componentPath}/admin/entities",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(entityLabel)} Administration",
        subtitle = "Entity record list baseline",
        body =
          s"""${nav}
             |${admin_storage_shape_section(component, Some(entityPath), detailed = true)}
             |${searchControls}
             |${searchFeedback}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <div>
             |        <h2 class="card-title mb-1">${escape(entityLabel)} records</h2>
             |        <p class="card-text text-body-secondary">List with paging${result.total.map(t => s" · total ${escape(t.toString)}").getOrElse("")}</p>
             |      </div>
             |      <a class="btn btn-primary" href="${escape(basePath)}/new">New ${escape(entityLabel)}</a>
             |    </div>
             |    ${warningHtml}
             |    ${table}
             |    ${paging_nav(result.page, result.pageSize, result.total, searchHref, Some(result.hasNext))}
             |  </div>
             |</article>""".stripMargin
      ))
    }

  protected def admin_storage_shape_section(
    component: Component,
    entityName: Option[String],
    detailed: Boolean
  ): String = {
    val describe = DescribeProjection.project(component, Some(component.name))
    val entities = manual_record_seq(describe.asMap.get("entityCollections")).filter { record =>
      entityName.forall(name => record.getString("entityName").exists(NamingConventions.equivalentByNormalized(_, name)))
    }
    if (entities.isEmpty)
      entityName.map(_ => admin_card("Storage shape", admin_empty_state("No storage-shape metadata is registered for this entity."))).getOrElse("")
    else {
      val summaryRows = entities.map(admin_storage_shape_summary_row).mkString("\n")
      val detailHtml =
        if (detailed)
          entities.map(admin_storage_shape_field_table).mkString("\n")
        else
          ""
      admin_card(
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

  protected def admin_storage_shape_summary_row(
    record: Record
  ): String = {
    val shape = manual_record_values(record.asMap.get("storageShape"))
    val fields = manual_record_seq(shape.get("fields"))
    val fieldSummary =
      if (fields.isEmpty)
        "none"
      else
        fields.flatMap(_.getString("classification")).groupBy(identity).toVector.sortBy(_._1).map {
          case (classification, rows) => s"$classification=${rows.size}"
        }.mkString(", ")
    s"""<tr>
       |  <td><code>${escape(record.getString("entityName").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("collectionId").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("memoryPolicy").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("workingSetPolicy").getOrElse("-"))}</code></td>
       |  <td><code>${escape(shape.get("policy").flatMap(manual_scalar).getOrElse(""))}</code></td>
       |  <td>${escape(fieldSummary)}</td>
       |</tr>""".stripMargin
  }

  protected def admin_storage_shape_field_table(
    record: Record
  ): String = {
    val entityName = record.getString("entityName").getOrElse("")
    val shape = manual_record_values(record.asMap.get("storageShape"))
    val fields = manual_record_seq(shape.get("fields"))
    if (fields.isEmpty)
      admin_empty_state("No storage-shape field metadata.")
    else {
      val rows = fields.map { field =>
        s"""<tr>
           |  <td><code>${escape(field.getString("logicalName").getOrElse(""))}</code></td>
           |  <td><code>${escape(field.getString("storageName").getOrElse(""))}</code></td>
           |  <td>${escape(field.getString("classification").getOrElse(""))}</td>
           |  <td>${escape(field.getString("storageKind").getOrElse(""))}</td>
           |  <td>${escape(field.getString("dataType").getOrElse(""))}</td>
           |  <td>${escape(field.getString("source").getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<section class="mt-3">
         |  <h3 class="h6">${escape(entityName)} storage fields</h3>
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val routeId = entity_route_id(id)
      val querySuffix = hidden_form_context_query_suffix(values)
      val readRecord = admin_entity_read_record(subsystem, componentPath, entityPath, id)
      val body = admin_entity_record_table_from_record(subsystem, component, componentPath, entityPath, id, readRecord, webDescriptor)
      val images = admin_entity_images_section(readRecord, id)
      val tags = admin_entity_tags_section(subsystem, readRecord, id, values)
      val associations = admin_entity_associations_section(subsystem, component, entityPath, readRecord, id)
      val nav = admin_nav_card(Vector(
        s"Back to ${entityLabel} records" -> s"${basePath}${querySuffix}",
        "Entity types" -> s"/web/${componentPath}/admin/entities"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(entityLabel)} Detail",
        subtitle = "Entity record detail baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <h2 class="card-title mb-0">${escape(entityLabel)} detail</h2>
             |      <a class="btn btn-primary" href="${escape(basePath + "/" + escape_path_segment(routeId) + "/edit" + querySuffix)}">Edit</a>
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = title_label(entityPath)
      val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val routeId = entity_route_id(id)
      val actionPath = s"/form/${componentPath}/admin/entities/${entityPath}/${routeId}/update"
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
      val effectiveValidation = validation.filter(_.webSchema.selector == displaySchema.selector)
      val hiddenContext = hidden_form_context_inputs(values)
      val controls = admin_record_controls(
        displaySchema.fields,
        admin_entity_record_fields(subsystem, componentPath, entityPath, id, "detail").getOrElse(Vector("id" -> id)),
        values,
        "field",
        effectiveValidation,
        includeExtensionFields = false
      )
      val imageAttachments = admin_entity_image_attachment_controls("imageAttachments")
      val nav = admin_nav_card(Vector(
        "Detail" -> s"${webBasePath}/${routeId}",
        s"Back to ${entityLabel} records" -> webBasePath
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(entityLabel)} Edit",
        subtitle = "Entity record edit baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">Edit ${escape(entityLabel)}</h2>
             |    ${form_error_panel(values)}${form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${escape(actionPath)}" class="admin-form" enctype="multipart/form-data">
             |      ${controls}
             |      ${imageAttachments}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Update</button>
             |        <a class="btn btn-outline-secondary" href="${escape(webBasePath)}/${escape(routeId)}">Cancel</a>
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(componentName)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = title_label(entityPath)
      val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val actionPath = s"/form/${componentPath}/admin/entities/${entityPath}/create"
      val webSchema = WebSchemaResolver.resolveEntity(
        component,
        componentPath,
        entityPath,
        webDescriptor,
        admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val displaySchema = admin_entity_create_schema(component, entityPath, webSchema)
      val effectiveValidation = validation.filter(_.webSchema.selector == displaySchema.selector)
      val hiddenContext = hidden_form_context_inputs(values)
      val controls = admin_new_controls(displaySchema.fields, values, "entityFields", "id=sales-order-1&#10;status=draft", effectiveValidation)
      val imageAttachments = admin_entity_image_attachment_controls("newImageAttachments")
      val nav = admin_nav_card(Vector(
        s"Back to ${entityLabel} records" -> webBasePath,
        "Entity types" -> s"/web/${componentPath}/admin/entities"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(entityLabel)} New",
        subtitle = "Entity record create baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">New ${escape(entityLabel)}</h2>
             |    ${form_error_panel(values)}${form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${escape(actionPath)}" class="admin-form" enctype="multipart/form-data">
             |      ${controls}
             |      ${imageAttachments}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Create</button>
             |        <a class="btn btn-outline-secondary" href="${escape(webBasePath)}">Cancel</a>
             |      </div>
             |    </form>
             |  </div>
             |</article>""".stripMargin
      ))
    }

  protected def admin_entity_image_attachment_controls(
    idPrefix: String
  ): String = {
    val rows = (0 until 3).map { index =>
      val base = s"imageAttachments.${index}"
      s"""<div class="row g-2 align-items-end mb-2">
         |  <div class="col-md-2">
         |    <label class="form-label" for="${escape(idPrefix)}Role${index}">Role</label>
         |    <input class="form-control" id="${escape(idPrefix)}Role${index}" name="${base}.role" list="entityImageAttachmentRoleOptions">
         |  </div>
         |  <div class="col-md-3">
         |    <label class="form-label" for="${escape(idPrefix)}BlobId${index}">Existing Blob id</label>
         |    <input class="form-control" id="${escape(idPrefix)}BlobId${index}" name="${base}.blobId">
         |  </div>
         |  <div class="col-md-4">
         |    <label class="form-label" for="${escape(idPrefix)}File${index}">Upload image</label>
         |    <input class="form-control" id="${escape(idPrefix)}File${index}" name="${base}.file" type="file" accept="image/*">
         |  </div>
         |  <div class="col-md-2">
         |    <label class="form-label" for="${escape(idPrefix)}Sort${index}">Sort</label>
         |    <input class="form-control" id="${escape(idPrefix)}Sort${index}" name="${base}.sortOrder">
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
    val entityLabel = title_label(entityPath)
    val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
    val rows = submitted_fields_rows(values)
    Page(simple_page(
      title = s"${escape(componentName)} ${escape(entityLabel)} Update Result",
      subtitle = "Entity record update submission baseline",
      body =
        s"""${admin_nav_card(Vector(
             "Detail" -> s"${webBasePath}/${id}",
             s"Back to ${entityLabel} records" -> webBasePath,
             "Edit again" -> s"${webBasePath}/${id}/edit"
           ))}
           |${admin_card(
             "Update submitted",
             s"""<p>${escape(message)}</p>
                |${admin_table(Some(admin_result_rows(applied, resultStatus, message)), rows)}""".stripMargin
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
    val entityLabel = title_label(entityPath)
    val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
    val rows = submitted_fields_rows(values)
    Page(simple_page(
      title = s"${escape(componentName)} ${escape(entityLabel)} Create Result",
      subtitle = "Entity record create submission baseline",
      body =
        s"""${admin_nav_card(Vector(
             s"Back to ${entityLabel} records" -> webBasePath,
             "Create another" -> s"${webBasePath}/new"
           ))}
           |${admin_card(
             "Create submitted",
             s"""<p>${escape(message)}</p>
                |${admin_table(Some(admin_result_rows(applied, resultStatus, message)), rows)}""".stripMargin
           )}""".stripMargin
    ))
  }

  protected final case class AdminListResult(
    items: Vector[AdminReadListItem],
    page: Int,
    pageSize: Int,
    hasNext: Boolean,
    total: Option[Int],
    warnings: Vector[String]
  ) {
    def ids: Vector[String] =
      items.map(_.id)
  }

  protected def admin_entity_list(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    pageRequest: PageRequest,
    searchValues: Map[String, String] = Map.empty
  ): AdminListResult =
    admin_list_result(
      subsystem,
      "/admin/entity/list",
      Record.create(Vector("component" -> componentPath, "entity" -> entityPath, "view" -> WebTableColumnResolver.defaultViewName) ++ pageRequest.toPairs ++ searchValues.toVector)
    )

  protected def admin_entity_searchable_fields(
    component: Component,
    entityPath: String,
    view: String
  ): Vector[String] =
    org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(component, entityPath)
      .defaultSearchFields(view)

  protected def admin_search_mode(
    values: Map[String, String]
  ): Option[SearchMode] =
    values.get("searchMode").orElse(values.get("search_mode")).flatMap(SearchMode.parse)

  protected def admin_search_values(
    values: Map[String, String],
    profile: SearchPlanningProfile
  ): Map[String, String] = {
    val knownFilters = profile.filterFields.map(x => NamingConventions.toNormalizedSegment(x).replace("-", "") -> x).toMap
    values.collect {
      case (key, value) if admin_search_control_key(key) && value.trim.nonEmpty => key -> value
      case (key, value) if value.trim.nonEmpty && knownFilters.contains(NamingConventions.toNormalizedSegment(key).replace("-", "")) => key -> value
    }
  }

  protected def admin_search_control_key(key: String): Boolean =
    Set("q", "text", "sort", "sortBy", "sort_by", "direction", "order", "searchMode", "search_mode", "includeTotal", "include_total").contains(key)

  protected def admin_search_card(
    action: String,
    values: Map[String, String],
    profile: SearchPlanningProfile,
    filterFields: Vector[String],
    sortFields: Vector[String],
    total: Option[Int],
    fetched: Int
  ): String = {
    val q = values.get("q").orElse(values.get("text")).getOrElse("")
    val selectedMode = admin_search_mode(values).getOrElse(SearchMode.FullText)
    val selectedSort = values.get("sort").orElse(values.get("sortBy")).orElse(values.get("sort_by")).getOrElse("")
    val selectedDirection = values.get("direction").orElse(values.get("order")).getOrElse("asc")
    val filtercontrols = filterFields.filterNot(x => x == "id").take(renderer_config.adminFilterFieldLimit).map { field =>
      val value = values.get(field).getOrElse("")
      s"""<div class="col-12 col-md-4 col-xl-2">
         |  <label class="form-label" for="adminSearchFilter${escape(field)}">${escape(title_label(field))}</label>
         |  <input class="form-control" id="adminSearchFilter${escape(field)}" name="${escape(field)}" value="${escape(value)}">
         |</div>""".stripMargin
    }.mkString("\n")
    val sortOptions = ("", "Default") +: sortFields.map(x => x -> title_label(x))
    val sortHtml = sortOptions.map { case (value, label) =>
      val selected = if (value == selectedSort) " selected" else ""
      s"""<option value="${escape(value)}"${selected}>${escape(label)}</option>"""
    }.mkString("\n")
    val chips = admin_search_chips(values, profile, action)
    val summary =
      total.map(t => s"${t} total records").getOrElse(s"${fetched} records on this page")
    s"""<article class="card admin-card admin-search-card">
       |  <div class="card-body">
       |    <div class="d-flex flex-column flex-md-row justify-content-between gap-2 mb-3">
       |      <div>
       |        <h2 class="card-title h5 mb-1">Search</h2>
       |        <p class="card-text text-body-secondary mb-0">Full-text search uses <code>q</code>; <code>text</code> is accepted as a compatibility alias. ${escape(summary)}</p>
       |      </div>
       |      <a class="btn btn-sm btn-outline-secondary align-self-start" href="${escape(action)}">Clear search</a>
       |    </div>
       |    ${chips}
       |    <form method="get" action="${escape(action)}" class="row g-2 align-items-end">
       |      <div class="col-12 col-md-5">
       |        <label class="form-label" for="adminSearchQ">Search text</label>
       |        <input class="form-control" id="adminSearchQ" name="q" value="${escape(q)}" placeholder="Search visible text fields">
       |      </div>
       |      <div class="col-12 col-md-2">
       |        <label class="form-label" for="adminSearchMode">Mode</label>
       |        <select class="form-select" id="adminSearchMode" name="searchMode">
       |          ${search_mode_option(SearchMode.FullText, selectedMode)}
       |          ${search_mode_option(SearchMode.Semantic, selectedMode)}
       |          ${search_mode_option(SearchMode.Hybrid, selectedMode)}
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
       |      ${filtercontrols}
       |      <div class="col-12 d-flex flex-wrap gap-2 justify-content-end">
       |        <input type="hidden" name="includeTotal" value="${escape(values.getOrElse("includeTotal", "false"))}">
       |        <button class="btn btn-primary" type="submit">Search</button>
       |      </div>
       |    </form>
       |  </div>
       |</article>""".stripMargin
  }

  protected def search_mode_option(
    mode: SearchMode,
    selected: SearchMode
  ): String = {
    val selectedAttr = if (mode == selected) " selected" else ""
    s"""<option value="${escape(mode.name)}"${selectedAttr}>${escape(title_label(mode.name))}</option>"""
  }

  protected def admin_search_chips(
    values: Map[String, String],
    profile: SearchPlanningProfile,
    clearHref: String
  ): String = {
    val searchValues = admin_search_values(values, profile)
    val chips = searchValues.toVector.sortBy(_._1).map { case (key, value) =>
      s"""<span class="badge rounded-pill text-bg-light border">${escape(key)}: ${escape(value)}</span>"""
    }
    if (chips.isEmpty)
      ""
    else
      s"""<div class="d-flex flex-wrap gap-2 align-items-center mb-3 admin-search-active-filters"><span class="text-body-secondary small">Active filters</span>${chips.mkString}<a class="btn btn-sm btn-outline-secondary" href="${escape(clearHref)}">Clear</a></div>"""
  }

  protected def admin_search_href(
    basePath: String,
    values: Map[String, String],
    profile: SearchPlanningProfile
  ): String = {
    val pairs = admin_search_values(values, profile).toVector
    val suffix =
      if (pairs.isEmpty)
        "?page={page}&pageSize={pageSize}"
      else
        pairs.map { case (key, value) => s"${escapeQuery(key)}=${escapeQuery(value)}" }.mkString("?", "&", "&page={page}&pageSize={pageSize}")
    s"${basePath}${suffix}"
  }

  protected def admin_entity_record_table(
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
      admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
      fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
    )
    val displayFields = admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
    admin_record_table(
      displayFields,
      admin_entity_record_fields(subsystem, componentPath, entityPath, id, "detail"),
      s"""No record is currently available for id <code>${escape(id)}</code>."""
    )
  }

  protected def admin_entity_record_table_from_record(
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
      admin_entity_schema_fields(subsystem, component, componentPath, entityPath),
      fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
    )
    val displayFields = admin_entity_display_fields(component, entityPath, "detail", webSchema.fieldNames)
    val fields = record.flatMap(_.getString("fields")).map(field_lines)
    admin_record_table(
      displayFields,
      fields,
      s"""No record is currently available for id <code>${escape(id)}</code>."""
    )
  }

  protected def admin_entity_read_record(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    id: String
  ): Option[Record] =
    admin_operation_record(
      subsystem,
      "/admin/entity/read",
      Record.data("component" -> componentPath, "entity" -> entityPath, "id" -> id, "view" -> "detail")
    )

  protected def admin_entity_record_fields(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    id: String,
    view: String
  ): Option[Vector[(String, String)]] =
    admin_record_fields(
      subsystem,
      "/admin/entity/read",
      Record.data("component" -> componentPath, "entity" -> entityPath, "id" -> id, "view" -> view)
    )

  protected def admin_entity_schema_fields(
    subsystem: Subsystem,
    component: Component,
    componentPath: String,
    entityPath: String
  ): Vector[String] =
    descriptor_entity_schema_fields(component, entityPath)
      .getOrElse {
        admin_schema_fields(
          subsystem,
          "/admin/entity/list",
          "/admin/entity/read",
          Record.data("component" -> componentPath, "entity" -> entityPath),
          Record.data("component" -> componentPath, "entity" -> entityPath)
        )
      }

  protected def admin_entity_display_fields(
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

  protected def admin_entity_create_schema(
    component: Component,
    entityPath: String,
    webSchema: WebSchemaResolver.ResolvedWebSchema
  ): WebSchemaResolver.ResolvedWebSchema = {
    val displayFields = admin_entity_create_fields(component, entityPath, webSchema.fieldNames)
    webSchema.copy(fields = admin_display_web_fields(webSchema.fields, displayFields))
  }

  protected def admin_entity_create_fields(
    component: Component,
    entityPath: String,
    fallback: Vector[String]
  ): Vector[String] =
    admin_entity_view_fields(component, entityPath, "create")
      .orElse(admin_entity_view_fields(component, entityPath, "detail"))
      .getOrElse(fallback)

  protected def admin_entity_view_fields(
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

  protected def admin_display_web_fields(
    fields: Vector[WebSchemaResolver.ResolvedWebField],
    fieldNames: Vector[String]
  ): Vector[WebSchemaResolver.ResolvedWebField] =
    fieldNames.map { name =>
      fields.find(x => NamingConventions.equivalentByNormalized(x.name, name))
        .map(_.copy(name = name))
        .getOrElse(WebSchemaResolver.ResolvedWebField(name))
    }

  protected def admin_data_list(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String,
    pageRequest: PageRequest
  ): AdminListResult =
    admin_list_result(
      subsystem,
      "/admin/data/list",
      Record.create(Vector("component" -> componentPath, "data" -> dataPath) ++ pageRequest.toPairs)
    )

  protected def admin_list_result(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): AdminListResult =
    admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        val items = admin_record_items(record)
        AdminListResult(
          items,
          record.getInt("page").getOrElse(1),
          record.getInt("pageSize").getOrElse(20),
          record.getBoolean("hasNext").getOrElse(false),
          record.getInt("total"),
          admin_record_warnings(record)
        )
      case None =>
        AdminListResult(Vector.empty, 1, 20, false, None, Vector.empty)
    }

  protected def admin_record_warnings(
    record: Record
  ): Vector[String] =
    record.getAny("warnings") match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString).filter(_.nonEmpty)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.map(_.toString).filter(_.nonEmpty)
      case Some(value) => Vector(value.toString).filter(_.nonEmpty)
      case None =>
        record.getString("totalUnavailableReason").map(reason => s"total count is not available: ${reason}").toVector
    }

  protected def admin_warnings(
    warnings: Vector[String]
  ): String =
    if (warnings.isEmpty)
      ""
    else
      s"""<div class="alert alert-warning admin-feedback" role="alert">
         |  <p class="alert-heading fw-semibold mb-2">Warning</p>
         |  <p class="mb-0">${warnings.map(escape).mkString("<br>")}</p>
         |</div>""".stripMargin

  protected def admin_empty_state(
    message: String
  ): String =
    web_empty_state(message, "admin-empty-state")

  protected def web_empty_state(
    message: String,
    cssClass: String = "web-empty-state"
  ): String =
    s"""<div class="alert alert-info ${escape(cssClass)}" role="status">
       |  <p class="mb-0">${escape(message)}</p>
       |</div>""".stripMargin

  protected def admin_empty_table_cell(
    colspan: Int,
    message: String
  ): String =
    s"""<tr><td colspan="${colspan}"><div class="admin-empty-state text-body-secondary py-3">${escape(message)}</div></td></tr>"""

  protected def admin_nav_card(
    links: Vector[(String, String)]
  ): String = {
    val items = links.map { case (label, href) =>
      s"""<li class="nav-item"><a class="nav-link border" href="${escape(href)}">${escape(label)}</a></li>"""
    }.mkString("\n")
    s"""<article class="card admin-card admin-nav">
       |  <div class="card-body">
       |    <h2 class="card-title h5">Navigation</h2>
       |    <ul class="nav nav-pills flex-column flex-sm-row gap-2">${items}</ul>
       |  </div>
       |</article>""".stripMargin
  }

  protected def admin_link_list_group(
    links: Vector[(String, String)]
  ): String = {
    val items = links.map { case (label, href) =>
      s"""<a class="list-group-item list-group-item-action" href="${escape(href)}">${escape(label)}</a>"""
    }.mkString("\n")
    s"""<div class="list-group">${items}</div>"""
  }

  protected def admin_action_row(
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
        s"""<a class="${css}" href="${escape(href)}">${escape(label)}</a>"""
    }.mkString("\n")
    s"""<div class="admin-action-row d-flex flex-wrap gap-2">${items}</div>"""
  }

  protected def admin_confirm_post_form(
    action: String,
    label: String,
    title: String,
    message: String,
    hiddenFields: Vector[(String, String)],
    formClass: String = "d-inline",
    triggerClass: String = "btn btn-outline-danger btn-sm"
  ): String = {
    val modalid = admin_confirm_modal_id(action, hiddenFields)
    val hidden = hiddenFields.map { case (name, value) =>
      s"""<input type="hidden" name="${escape(name)}" value="${escape(value)}">"""
    }.mkString("\n")
    s"""<button class="${escape(triggerClass)}" type="button" data-bs-toggle="modal" data-bs-target="#${modalid}">${escape(label)}</button>
       |<div class="modal fade" id="${modalid}" tabindex="-1" aria-labelledby="${modalid}-title" aria-hidden="true">
       |  <div class="modal-dialog">
       |    <div class="modal-content">
       |      <div class="modal-header">
       |        <h2 class="modal-title h5" id="${modalid}-title">${escape(title)}</h2>
       |        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
       |      </div>
       |      <div class="modal-body"><p class="mb-0">${escape(message)}</p></div>
       |      <div class="modal-footer">
       |        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
       |        <form method="post" action="${escape(action)}" class="${escape(formClass)}">
       |          ${hidden}
       |          <button class="btn btn-danger" type="submit">${escape(label)}</button>
       |        </form>
       |      </div>
       |    </div>
       |  </div>
       |</div>
       |<noscript>
       |  <form method="post" action="${escape(action)}" class="${escape(formClass)}">
       |    ${hidden}
       |    <button class="${escape(triggerClass)}" type="submit">${escape(label)}</button>
       |  </form>
       |</noscript>""".stripMargin
  }

  protected def admin_confirm_modal_id(
    action: String,
    hiddenFields: Vector[(String, String)]
  ): String = {
    val seed = (action +: hiddenFields.map { case (k, v) => s"${k}=${v}" }).mkString("|")
    s"admin-confirm-${java.lang.Integer.toUnsignedString(seed.hashCode)}"
  }

  protected def admin_table(
    head: Option[String],
    rows: String,
    tableClass: String = "table table-sm table-hover align-middle mb-0"
  ): String =
    s"""<div class="table-responsive">
       |  <table class="${escape(tableClass)}">
       |    ${head.map(x => s"<thead>${x}</thead>").getOrElse("")}
       |    <tbody>${rows}</tbody>
       |  </table>
       |</div>""".stripMargin

  protected def admin_card(
    title: String,
    body: String,
    id: Option[String] = None
  ): String = {
    val idAttr = id.map(x => s""" id="${escape(x)}"""").getOrElse("")
    s"""<article${idAttr} class="card admin-card">
       |  <div class="card-body">
       |    <h2 class="card-title">${escape(title)}</h2>
       |    ${body}
       |  </div>
       |</article>""".stripMargin
  }

  protected def admin_data_record_table(
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
      admin_data_schema_fields(subsystem, componentPath, dataPath),
      fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
    )
    admin_record_table(
      webSchema.fieldNames,
      admin_data_record_fields(subsystem, componentPath, dataPath, id),
      s"""No data record is currently available for id <code>${escape(id)}</code>."""
    )
  }

  protected def admin_data_record_fields(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String,
    id: String
  ): Option[Vector[(String, String)]] =
    admin_record_fields(
      subsystem,
      "/admin/data/read",
      Record.data("component" -> componentPath, "data" -> dataPath, "id" -> id)
    )

  protected def admin_data_schema_fields(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String
  ): Vector[String] =
    admin_schema_fields(
      subsystem,
      "/admin/data/list",
      "/admin/data/read",
      Record.data("component" -> componentPath, "data" -> dataPath),
      Record.data("component" -> componentPath, "data" -> dataPath)
    )

  protected def admin_schema_fields(
    subsystem: Subsystem,
    listPath: String,
    readPath: String,
    listForm: Record,
    readBaseForm: Record
  ): Vector[String] =
    id_first_if_present(admin_operation_record(subsystem, listPath, listForm).toVector.flatMap { record =>
      admin_record_items(record).headOption.toVector.flatMap { item =>
        admin_record_fields(
          subsystem,
          readPath,
          Record.create((readBaseForm.asMap + ("id" -> item.id)).toVector)
        ).toVector.flatten.map(_._1)
      }
    }.distinct) match {
      case xs if xs.nonEmpty => xs
      case _ => Vector("id")
    }

  protected def id_first_if_present(
    fields: Vector[String]
  ): Vector[String] =
    if (fields.contains("id"))
      "id" +: fields.filterNot(_ == "id")
    else
      fields

  protected def descriptor_entity_schema_fields(
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

  protected def admin_record_fields(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[Vector[(String, String)]] =
    admin_operation_record(subsystem, path, form)
      .flatMap(_.getString("fields"))
      .map(field_lines)

  protected def admin_operation_lines(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Vector[String] =
    admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        record.getAny("ids") match {
          case Some(xs: Seq[?]) =>
            xs.toVector.map(_.toString)
          case _ =>
            record.getString("fields").map(lines).getOrElse(Vector.empty)
        }
      case None =>
        Vector.empty
    }

  protected def admin_operation_record(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[Record] =
    admin_operation_response(subsystem, path, form).collect {
      case OperationResponse.RecordResponse(record) => record
    }

  protected def admin_operation_response(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[OperationResponse] =
    admin_protocol_request(path, form).flatMap { request =>
      subsystem.executeOperationResponse(request).toOption
    }

  protected def admin_protocol_request(
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

  protected def admin_read_result_table(
    subsystem: Subsystem,
    path: String,
    form: Record,
    emptyMessage: String,
    schemaFields: Vector[String] = Vector.empty
  ): String =
    admin_operation_value_lines(subsystem, path, form)
      .filter(_.nonEmpty)
      .map(lines => field_table(admin_schema_ordered_fields(schemaFields, field_lines(lines.mkString("\n")))))
      .getOrElse(admin_empty_state(emptyMessage))

  protected def admin_read_result_table_web_schema(
    subsystem: Subsystem,
    path: String,
    form: Record,
    emptyMessage: String,
    webFields: Vector[WebSchemaResolver.ResolvedWebField]
  ): String = {
    val schemaFields = webFields.map(_.name)
    val labels = web_field_labels(webFields)
    admin_operation_value_lines(subsystem, path, form)
      .filter(_.nonEmpty)
      .map(lines => field_table(admin_schema_ordered_fields(schemaFields, field_lines(lines.mkString("\n"))), labels))
      .getOrElse(admin_empty_state(emptyMessage))
  }

  protected def admin_read_result_list(
    subsystem: Subsystem,
    path: String,
    form: Record,
    basePath: String,
    emptyMessage: String,
    pageRequest: PageRequest,
    schemaFields: Vector[String] = Vector.empty
  ): String =
    admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        val items = admin_record_items(record)
        val warnings = admin_warnings(admin_record_warnings(record))
        val page = record.getInt("page").getOrElse(pageRequest.page)
        val pageSize = record.getInt("pageSize").getOrElse(pageRequest.pageSize)
        val total = record.getInt("total")
        val hasNext = record.getBoolean("hasNext")
        val table = admin_read_result_list_table(items, schemaFields, basePath, emptyMessage)
        s"""<p>List with paging${total.map(t => s" · total ${escape(t.toString)}").getOrElse("")}</p>
           |${warnings}
           |${table}
           |${paging_nav(page, pageSize, total, pageRequest.href(basePath), hasNext)}""".stripMargin
      case None =>
        admin_empty_state(emptyMessage)
    }

  protected def admin_read_result_list_web_schema(
    subsystem: Subsystem,
    path: String,
    form: Record,
    basePath: String,
    emptyMessage: String,
    pageRequest: PageRequest,
    webFields: Vector[WebSchemaResolver.ResolvedWebField]
  ): String =
    admin_operation_record(subsystem, path, form) match {
      case Some(record) =>
        val schemaFields = webFields.map(_.name)
        val labels = web_field_labels(webFields)
        val items = admin_record_items(record)
        val warnings = admin_warnings(admin_record_warnings(record))
        val page = record.getInt("page").getOrElse(pageRequest.page)
        val pageSize = record.getInt("pageSize").getOrElse(pageRequest.pageSize)
        val total = record.getInt("total")
        val hasNext = record.getBoolean("hasNext")
        val table = admin_read_result_list_table_labeled(items, schemaFields, labels, basePath, emptyMessage)
        s"""<p>List with paging${total.map(t => s" · total ${escape(t.toString)}").getOrElse("")}</p>
           |${warnings}
           |${table}
           |${paging_nav(page, pageSize, total, pageRequest.href(basePath), hasNext)}""".stripMargin
      case None =>
        admin_empty_state(emptyMessage)
    }

  protected def admin_read_result_list_table_labeled(
    items: Vector[AdminReadListItem],
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
          admin_empty_table_cell(3, emptyMessage)
        } else {
          items.map { item =>
            val href = s"${basePath}/${escape_path_segment(admin_read_list_route_id(item))}${hidden_form_context_query_suffix(linkContext)}"
            val actions = admin_read_list_actions(href, includeEdit)
            s"""<tr><td><code>${escape(item.id)}</code></td><td>${escape(item.label)}</td><td>${actions}</td></tr>"""
          }.mkString("\n")
        }
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr><th>ID</th><th>Value</th><th>Actions</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    } else {
      val headings = schemaFields.map(x => s"<th>${escape(labels.getOrElse(x, x))}</th>").mkString
      val rows =
        if (items.isEmpty) {
          admin_empty_table_cell(schemaFields.size + 1, emptyMessage)
        } else {
          items.map { item =>
            val values = admin_read_list_item_fields(item)
            val href = s"${basePath}/${escape_path_segment(admin_read_list_route_id(item, values))}${hidden_form_context_query_suffix(linkContext)}"
            val columns = schemaFields.map { field =>
              val value = {
                if (field == "id") values.getOrElse(field, item.id)
                else if (field == "label") values.getOrElse(field, item.label)
                else if (field == "value") values.getOrElse(field, item.value)
                else admin_display_value(field, schemaFields.toSet, values)
              }
              s"<td>${escape(value)}</td>"
            }.mkString
            s"""<tr>${columns}<td>${admin_read_list_actions(href, includeEdit)}</td></tr>"""
          }.mkString("\n")
        }
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
         |  <thead><tr>${headings}<th>Actions</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def admin_read_result_list_table(
    items: Vector[AdminReadListItem],
    schemaFields: Vector[String],
    basePath: String,
    emptyMessage: String,
    includeEdit: Boolean = false,
    linkContext: Map[String, String] = Map.empty
  ): String =
    admin_read_result_list_table_labeled(items, schemaFields, Map.empty, basePath, emptyMessage, includeEdit, linkContext)

  protected def admin_read_list_route_id(
    item: AdminReadListItem,
    values: Map[String, String] = Map.empty
  ): String =
    values.get("shortid")
      .orElse(admin_read_list_item_fields(item).get("shortid"))
      .filter(_.nonEmpty)
      .getOrElse(item.id)

  protected def entity_route_id(
    id: String
  ): String =
    EntityId.parse(id).toOption.map(_.parts.entropy).getOrElse(id)

  protected def web_field_labels(
    fields: Vector[WebSchemaResolver.ResolvedWebField]
  ): Map[String, String] =
    fields.flatMap(field => field.label.map(label => field.name -> label)).toMap

  protected def admin_read_list_actions(
    href: String,
    includeEdit: Boolean
  ): String =
    if (includeEdit) {
      val editHref = append_path_before_query(href, "/edit")
      s"""<div class="btn-group btn-group-sm" role="group" aria-label="Record actions"><a class="btn btn-outline-primary" href="${escape(href)}">Detail</a><a class="btn btn-outline-secondary" href="${escape(editHref)}">Edit</a></div>"""
    }
    else
      s"""<a class="btn btn-outline-primary btn-sm" href="${escape(href)}">Detail</a>"""

  protected def append_path_before_query(
    href: String,
    suffix: String
  ): String =
    href.indexOf('?') match {
      case -1 => href + suffix
      case n => href.substring(0, n) + suffix + href.substring(n)
    }

  protected def admin_read_list_item_fields(
    item: AdminReadListItem
  ): Map[String, String] =
    field_lines(item.value).toMap ++ item.fields ++ Map(
      "id" -> item.id,
      "label" -> item.label,
      "value" -> item.value
    )

  protected final case class AdminReadListItem(
    id: String,
    label: String,
    value: String,
    fields: Map[String, String] = Map.empty
  )

  protected def admin_record_items(record: Record): Vector[AdminReadListItem] =
    record.getAny("items") match {
      case Some(xs: Seq[?]) => xs.toVector.flatMap(admin_record_item)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.flatMap(admin_record_item)
      case Some(value) => admin_record_item(value).toVector
      case None =>
        admin_record_ids(record).map(x => AdminReadListItem(x, x, x)) match {
          case xs if xs.nonEmpty => xs
          case _ => admin_record_values(record).map(x => AdminReadListItem(x, x, x))
        }
    }

  protected def admin_record_ids(record: Record): Vector[String] =
    record.getAny("ids") match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.map(_.toString)
      case Some(value) => Vector(value.toString)
      case None => Vector.empty
    }

  protected def admin_record_item(value: Any): Option[AdminReadListItem] =
    value match {
      case record: Record =>
        val fields = record.asMap.view.mapValues(_.toString).toMap
        val id = fields.getOrElse("id", "")
        val label = fields.getOrElse("label", id)
        val itemValue = fields.getOrElse("value", field_map_text(fields).getOrElse(label))
        Option.when(id.nonEmpty)(AdminReadListItem(id, label, itemValue, fields))
      case map: scala.collection.Map[?, ?] =>
        val fields = map.toVector.map { case (key, itemValue) => key.toString -> itemValue.toString }.toMap
        val id = fields.getOrElse("id", "")
        val label = fields.getOrElse("label", id)
        val itemValue = fields.getOrElse("value", field_map_text(fields).getOrElse(label))
        Option.when(id.nonEmpty)(AdminReadListItem(id, label, itemValue, fields))
      case x =>
        val text = x.toString
        Option.when(text.nonEmpty)(AdminReadListItem(text, text, text))
    }

  protected def field_map_text(
    fields: Map[String, String]
  ): Option[String] =
    Option.when(fields.nonEmpty) {
      fields.toVector.sortBy(_._1).map { case (key, value) => s"${key}=${value}" }.mkString("\n")
    }

  protected def admin_record_values(record: Record): Vector[String] =
    record.getAny("values") match {
      case Some(xs: Seq[?]) => xs.toVector.map(_.toString)
      case Some(xs: java.util.List[?]) => xs.toArray.toVector.map(_.toString)
      case Some(value) => Vector(value.toString)
      case None => record.getString("fields").map(lines).getOrElse(Vector.empty)
    }

  protected def admin_operation_value_lines(
    subsystem: Subsystem,
    path: String,
    form: Record
  ): Option[Vector[String]] =
    admin_operation_response(subsystem, path, form).flatMap {
      case OperationResponse.RecordResponse(record) =>
        record.getString("fields").map(lines)
      case _ =>
        None
    }

  protected def lines(text: String): Vector[String] =
    text.linesIterator.toVector.map(_.trim).filter(_.nonEmpty)

  protected def field_lines(text: String): Vector[(String, String)] =
    lines(text).map { line =>
      val i = line.indexOf("=")
      if (i < 0) line -> ""
      else line.take(i) -> line.drop(i + 1)
    }

  protected def admin_key_value_table(
    lines: Vector[String],
    emptyMessage: String
  ): String =
    if (lines.isEmpty) {
      admin_empty_state(emptyMessage)
    } else {
      value_table(lines)
    }

  protected def admin_record_table(
    schemaFields: Vector[String],
    fields: Option[Vector[(String, String)]],
    emptyMessage: String
  ): String =
    fields match {
      case Some(xs) if xs.nonEmpty =>
        field_table(admin_schema_ordered_fields(schemaFields, xs))
      case _ =>
        admin_empty_state(emptyMessage)
    }

  protected def field_table(fields: Vector[(String, String)]): String = {
    field_table(fields, Map.empty)
  }

  protected def field_table(
    fields: Vector[(String, String)],
    labels: Map[String, String]
  ): String = {
    val rows = fields.map {
      case (key, value) =>
        s"""<tr><th>${escape(labels.getOrElse(key, key))}</th><td>${escape(value)}</td></tr>"""
    }.mkString("\n")
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <tbody>${rows}</tbody>
       |</table></div>""".stripMargin
  }

  protected def value_table(lines: Vector[String]): String = {
    val rows = lines.map { line =>
      val i = line.indexOf("=")
      val (key, value) =
        if (i < 0) "value" -> line
        else line.take(i) -> line.drop(i + 1)
      s"""<tr><th>${escape(key)}</th><td>${escape(value)}</td></tr>"""
    }.mkString("\n")
    s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
       |  <tbody>${rows}</tbody>
       |</table></div>""".stripMargin
  }

  protected def escape_path_segment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  protected def escape_query(value: String): String =
    StaticFormAppRendererSupport.escapeQuery(value)

  protected def form_initial_fields(values: Map[String, String]): String =
    values.toVector
      .sortBy(_._1)
      .map { case (key, value) => s"${escape(key)}=${escape(value)}" }
      .mkString("\n")

  protected def submitted_fields_rows(values: Map[String, String]): String =
    if (values.isEmpty) {
      """<tr><td colspan="2">No submitted fields.</td></tr>"""
    } else {
      values.toVector.sortBy(_._1).map {
        case (key, value) =>
          s"""<tr><th>${escape(key)}</th><td>${escape(value)}</td></tr>"""
      }.mkString("\n")
    }

  protected def view_definition(
    component: Component,
    viewName: String
  ) =
    component.viewDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, viewName))
      .orElse(strip_surface_suffix(viewName, "view").flatMap(base =>
        component.viewDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, base))
      ))

  protected def aggregate_definition(
    component: Component,
    aggregateName: String
  ) =
    component.aggregateDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, aggregateName))
      .orElse(strip_surface_suffix(aggregateName, "aggregate").flatMap(base =>
        component.aggregateDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, base))
      ))

  protected def strip_surface_suffix(
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

  protected def aggregate_operation_actions(
    component: Component,
    aggregateName: String
  ): String = {
    val componentPath = NamingConventions.toNormalizedSegment(component.name)
    val bindings = aggregate_operation_bindings(component, aggregateName)
    if (bindings.isEmpty) {
      admin_empty_state("No aggregate operations are currently exposed.")
    } else {
      val grouped = bindings.groupBy(_.kind).withDefaultValue(Vector.empty)
      val summary = Vector(
        "create" -> "Create operations construct a new aggregate root.",
        "read" -> "Read operations retrieve aggregate state.",
        "update" -> "Update and command operations mutate aggregate state."
      ).map { case (kind, text) =>
        val count = grouped(kind).size
        s"""<li><strong>${escape(kind)}</strong>: ${count} ${escape(text)}</li>"""
      }.mkString("\n")
      val rows = bindings.map { binding =>
        val path = form_operation_path(componentPath, binding.service, binding.operation)
        val style = if (binding.kind == "create") "btn-primary" else if (binding.kind == "update") "btn-warning" else "btn-outline-secondary"
        val label = aggregate_operation_action_label(binding.kind)
        s"""<tr><td>${escape(binding.kind)}</td><td>${escape(binding.service)}</td><td>${escape(binding.operation)}</td><td><a class="btn btn-sm ${style}" href="${escape(path)}">${escape(label)}</a></td></tr>"""
      }.mkString("\n")
      s"""<ul>${summary}</ul>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Kind</th><th>Service</th><th>Operation</th><th>Action</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
  }

  protected def aggregate_instance_operation_actions(
    component: Component,
    aggregateName: String,
    id: String
  ): String = {
    val componentPath = NamingConventions.toNormalizedSegment(component.name)
    val bindings = aggregate_operation_bindings(component, aggregateName)
      .filter(x => x.kind == "read" || x.kind == "update")
    if (bindings.isEmpty) {
      admin_empty_state("No aggregate instance operations are currently exposed.")
    } else {
      val rows = bindings.map { binding =>
        val detailPath = s"/web/${componentPath}/admin/aggregates/${NamingConventions.toNormalizedSegment(aggregateName)}/${escape_path_segment(id)}"
        val path = form_operation_path(componentPath, binding.service, binding.operation, admin_operation_context("id" -> id, "crud.success.href" -> detailPath))
        val style = if (binding.kind == "update") "btn-warning" else "btn-outline-secondary"
        val label = aggregate_operation_action_label(binding.kind)
        s"""<tr><td>${escape(binding.kind)}</td><td>${escape(binding.service)}</td><td>${escape(binding.operation)}</td><td><a class="btn btn-sm ${style}" href="${escape(path)}">${escape(label)}</a></td></tr>"""
      }.mkString("\n")
      s"""<p>Instance operations are opened as normal Operation forms with the aggregate id prefilled.</p>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Kind</th><th>Service</th><th>Operation</th><th>Action</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
  }

  protected def aggregate_operation_action_label(kind: String): String =
    kind match {
      case "create" => "Create aggregate"
      case "read" => "Read aggregate"
      case "update" => "Run update command"
      case _ => "Open operation"
    }

  protected def admin_operation_context(
    values: (String, String)*
  ): Map[String, String] =
    values.toMap

  protected def form_operation_path(
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
        case (key, value) => s"${escapeQuery(key)}=${escapeQuery(value)}"
      }.mkString("?", "&", "")
  }

  protected final case class AggregateOperationBinding(
    kind: String,
    service: String,
    operation: String
  )

  protected def aggregate_operation_bindings(
    component: Component,
    aggregateName: String
  ): Vector[AggregateOperationBinding] = {
    val definition = aggregate_definition(component, aggregateName)
    val commandNames = definition.map(_.commands.map(_.name)).getOrElse(Vector.empty)
    val readNames = Vector(s"read-${aggregateName}", s"get-${aggregateName}", s"load-${aggregateName}", s"search-${aggregateName}")
    val createNames =
      definition.map(_.creates.map(_.name)).filter(_.nonEmpty)
        .getOrElse(Vector(s"create-${aggregateName}", s"new-${aggregateName}"))
    val updateNames = Vector(s"update-${aggregateName}") ++ commandNames
    val candidates =
      operation_bindings(component, readNames, "read") ++
        operation_bindings(component, createNames, "create") ++
        operation_bindings(component, updateNames, "update")
    candidates
      .groupBy(x => (x.kind, NamingConventions.toNormalizedSegment(x.operation)))
      .values
      .toVector
      .flatMap(preferred_aggregate_binding)
      .sortBy(x => (x.kind, x.service, x.operation))
  }

  protected def preferred_aggregate_binding(
    bindings: Vector[AggregateOperationBinding]
  ): Option[AggregateOperationBinding] =
    bindings match {
      case Vector(one) =>
        Some(one)
      case many =>
        many.find(x => NamingConventions.equivalentByNormalized(x.service, "aggregate"))
    }

  protected def operation_bindings(
    component: Component,
    names: Vector[String],
    kind: String
  ): Vector[AggregateOperationBinding] = {
    val normalized = names.map(NamingConventions.toNormalizedSegment).toSet
    component.services.services.flatMap { service =>
      service.serviceDefinition.operations.operations.toVector.flatMap { operation =>
        val name = operation.name
        if (normalized.contains(NamingConventions.toNormalizedSegment(name)))
          Some(AggregateOperationBinding(kind, service.serviceDefinition.name, name))
        else
          None
      }
    }
  }

  def renderComponentAdminViews(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val rows = component.viewDefinitions.map { definition =>
        val viewPath = NamingConventions.toNormalizedSegment(definition.name)
        val viewNames =
          if (definition.viewNames.isEmpty) "default"
          else definition.viewNames.map(escape).mkString(", ")
        val queries =
          if (definition.queries.isEmpty) "none"
          else definition.queries.map(q => escape(q.name)).mkString(", ")
        val sourceEvents =
          if (definition.sourceEvents.isEmpty) "none"
          else definition.sourceEvents.map(escape).mkString(", ")
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/views/${viewPath}">${escape(definition.name)}</a></td>
           |  <td>${escape(definition.entityName)}</td>
           |  <td>${viewNames}</td>
           |  <td>${queries}</td>
           |  <td>${sourceEvents}</td>
           |  <td>${definition.rebuildable.map(_.toString).getOrElse("unspecified")}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          admin_empty_state("No view definitions are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>View</th><th>Entity</th><th>Names</th><th>Queries</th><th>Source events</th><th>Rebuildable</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      val nav = admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} View Administration",
        subtitle = "View read management baseline",
        body =
          s"""${nav}
             |${admin_card("View read", body)}
             |${admin_card("Deferred capabilities", "<p class=\"mb-0\">View mutation and rebuild controls stay deferred in this slice. This page exposes read/list drill-down only.</p>")}""".stripMargin
      ))
    }

  def renderComponentAdminViewDetail(
    subsystem: Subsystem,
    componentName: String,
    viewName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val basePath = s"/web/${componentPath}/admin/views/${viewPath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "view", viewPath)
      )
      val definition = view_definition(component, viewName)
      val entityName = definition.map(_.entityName).getOrElse(strip_surface_suffix(viewPath, "view").getOrElse(viewPath))
      val webSchema = WebSchemaResolver.resolveView(
        component,
        componentPath,
        viewPath,
        Some(entityName),
        webDescriptor,
        viewFields = definition.flatMap(_.fieldsFor("summary")).orElse(admin_entity_view_fields(component, entityName, "summary")),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val metadata = definition match {
        case Some(d) =>
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <tbody>
             |    <tr><th>View</th><td>${escape(d.name)}</td></tr>
             |    <tr><th>Entity</th><td>${escape(d.entityName)}</td></tr>
             |    <tr><th>Names</th><td>${escape(if (d.viewNames.isEmpty) "default" else d.viewNames.mkString(", "))}</td></tr>
             |    <tr><th>Queries</th><td>${escape(if (d.queries.isEmpty) "none" else d.queries.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>Source events</th><td>${escape(if (d.sourceEvents.isEmpty) "none" else d.sourceEvents.mkString(", "))}</td></tr>
             |    <tr><th>Rebuildable</th><td>${escape(d.rebuildable.map(_.toString).getOrElse("unspecified"))}</td></tr>
             |  </tbody>
             |</table></div>""".stripMargin
        case None =>
          admin_empty_state(s"No view definition is registered for ${viewName}.")
      }
      val nav = admin_nav_card(Vector(
        "View definitions" -> s"/web/${componentPath}/admin/views",
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      val readResult = admin_read_result_list_web_schema(
        subsystem,
        "/admin/view/read",
        Record.create(Vector("component" -> componentPath, "view" -> viewPath) ++ effectivePageRequest.toPairs),
        basePath,
        s"No view records are currently available for ${viewName}.",
        effectivePageRequest,
        webSchema.fields
      )
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(viewPath))} View",
        subtitle = "View read baseline",
        body =
          s"""${nav}
             |${admin_card(s"${title_label(viewPath)} metadata", metadata)}
             |${admin_card("Read result", readResult)}""".stripMargin
      ))
    }

  def renderComponentAdminViewInstanceDetail(
    subsystem: Subsystem,
    componentName: String,
    viewName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val basePath = s"/web/${componentPath}/admin/views/${viewPath}"
      val definition = view_definition(component, viewName)
      val entityName = definition.map(_.entityName).getOrElse(strip_surface_suffix(viewPath, "view").getOrElse(viewPath))
      val webSchema = WebSchemaResolver.resolveView(
        component,
        componentPath,
        viewPath,
        Some(entityName),
        webDescriptor,
        viewFields = definition.flatMap(_.fieldsFor("detail")).orElse(admin_entity_view_fields(component, entityName, "detail")),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val nav = admin_nav_card(Vector(
        s"Back to ${title_label(viewPath)} view" -> basePath,
        "View definitions" -> s"/web/${componentPath}/admin/views",
        "Component admin" -> s"/web/${componentPath}/admin"
      ))
      val body = admin_read_result_table_web_schema(
        subsystem,
        "/admin/view/read",
        Record.data("component" -> componentPath, "view" -> viewPath, "id" -> id),
        s"No view record is currently available for ${id}.",
        webSchema.fields
      )
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(viewPath))} View Detail",
        subtitle = "View instance read baseline",
        body =
          s"""${nav}
             |${admin_card(id, body)}""".stripMargin
      ))
    }

  def renderComponentAdminAggregates(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val rows = component.aggregateDefinitions.map { definition =>
        val aggregatePath = NamingConventions.toNormalizedSegment(definition.name)
        val members =
          if (definition.members.isEmpty) "none"
          else definition.members.map(m => escape(s"${m.name}:${m.entityName}")).mkString(", ")
        val commands =
          if (definition.commands.isEmpty) "none"
          else definition.commands.map(c => escape(c.name)).mkString(", ")
        val creates =
          if (definition.creates.isEmpty) "none"
          else definition.creates.map(c => escape(c.name)).mkString(", ")
        val state =
          if (definition.state.isEmpty) "none"
          else definition.state.map(s => escape(s.name)).mkString(", ")
        val invariants =
          if (definition.invariants.isEmpty) "none"
          else definition.invariants.map(i => escape(i.name)).mkString(", ")
        s"""<tr>
           |  <td><a href="/web/${componentPath}/admin/aggregates/${aggregatePath}">${escape(definition.name)}</a></td>
           |  <td>${escape(definition.entityName)}</td>
           |  <td>${members}</td>
           |  <td>${creates}</td>
           |  <td>${commands}</td>
           |  <td>${state}</td>
           |  <td>${invariants}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      val body =
        if (rows.isEmpty) {
          admin_empty_state("No aggregate definitions are registered for this component.")
        } else {
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <thead><tr><th>Aggregate</th><th>Entity</th><th>Members</th><th>Creates</th><th>Commands</th><th>State</th><th>Invariants</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      val nav = admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} Aggregate Administration",
        subtitle = "Aggregate CRUD management baseline",
        body =
          s"""${nav}
             |${admin_card("Aggregate read", body)}
             |${admin_card("Deferred capabilities", "<p class=\"mb-0\">Aggregate mutation and destructive administration remain deferred. Use this surface for read/list drill-down and related operation handoff.</p>")}""".stripMargin
      ))
    }

  def renderComponentAdminAggregateDetail(
    subsystem: Subsystem,
    componentName: String,
    aggregateName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val basePath = s"/web/${componentPath}/admin/aggregates/${aggregatePath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "aggregate", aggregatePath)
      )
      val definition = aggregate_definition(component, aggregateName)
      val entityName = definition.map(_.entityName).getOrElse(strip_surface_suffix(aggregatePath, "aggregate").getOrElse(aggregatePath))
      val webSchema = WebSchemaResolver.resolveAggregate(
        component,
        componentPath,
        aggregatePath,
        Some(entityName),
        webDescriptor,
        viewFields = admin_entity_view_fields(component, entityName, "summary"),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val metadata = definition match {
        case Some(d) =>
          s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle">
             |  <tbody>
             |    <tr><th>Aggregate</th><td>${escape(d.name)}</td></tr>
             |    <tr><th>Entity</th><td>${escape(d.entityName)}</td></tr>
             |    <tr><th>Members</th><td>${escape(if (d.members.isEmpty) "none" else d.members.map(m => s"${m.name}:${m.entityName}").mkString(", "))}</td></tr>
             |    <tr><th>Creates</th><td>${escape(if (d.creates.isEmpty) "none" else d.creates.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>Commands</th><td>${escape(if (d.commands.isEmpty) "none" else d.commands.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>State</th><td>${escape(if (d.state.isEmpty) "none" else d.state.map(_.name).mkString(", "))}</td></tr>
             |    <tr><th>Invariants</th><td>${escape(if (d.invariants.isEmpty) "none" else d.invariants.map(_.name).mkString(", "))}</td></tr>
             |  </tbody>
             |</table></div>""".stripMargin
        case None =>
          admin_empty_state(s"No aggregate definition is registered for ${aggregateName}.")
      }
      val nav = admin_nav_card(Vector(
        "Aggregate definitions" -> s"/web/${componentPath}/admin/aggregates",
        "Component admin" -> s"/web/${componentPath}/admin",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      val readResult = admin_read_result_list_web_schema(
        subsystem,
        "/admin/aggregate/read",
        Record.create(Vector("component" -> componentPath, "aggregate" -> aggregatePath) ++ effectivePageRequest.toPairs),
        basePath,
        s"No aggregate records are currently available for ${aggregateName}.",
        effectivePageRequest,
        webSchema.fields
      )
      val operations = aggregate_operation_actions(component, aggregateName)
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(aggregatePath))} Aggregate",
        subtitle = "Aggregate read baseline",
        body =
          s"""${nav}
             |${admin_card(s"${title_label(aggregatePath)} metadata", metadata)}
             |${admin_card("Read result", readResult)}
             |${admin_card("Operations", operations)}""".stripMargin
      ))
    }

  def renderComponentAdminAggregateInstanceDetail(
    subsystem: Subsystem,
    componentName: String,
    aggregateName: String,
    id: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val basePath = s"/web/${componentPath}/admin/aggregates/${aggregatePath}"
      val definition = aggregate_definition(component, aggregateName)
      val entityName = definition.map(_.entityName).getOrElse(strip_surface_suffix(aggregatePath, "aggregate").getOrElse(aggregatePath))
      val webSchema = WebSchemaResolver.resolveAggregate(
        component,
        componentPath,
        aggregatePath,
        Some(entityName),
        webDescriptor,
        viewFields = admin_entity_view_fields(component, entityName, "detail"),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val nav = admin_nav_card(Vector(
        s"Back to ${title_label(aggregatePath)} aggregate" -> basePath,
        "Aggregate definitions" -> s"/web/${componentPath}/admin/aggregates",
        "Component admin" -> s"/web/${componentPath}/admin"
      ))
      val body = admin_read_result_table_web_schema(
        subsystem,
        "/admin/aggregate/read",
        Record.data("component" -> componentPath, "aggregate" -> aggregatePath, "id" -> id),
        s"No aggregate record is currently available for ${id}.",
        webSchema.fields
      )
      val operations = aggregate_instance_operation_actions(component, aggregateName, id)
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(aggregatePath))} Aggregate Detail",
        subtitle = "Aggregate instance read baseline",
        body =
          s"""${nav}
             |${admin_card(id, body)}
             |${admin_card("Instance operations", operations)}""".stripMargin
      ))
    }

  def renderComponentAdminData(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val nav = admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Descriptor" -> s"/web/${componentPath}/admin/descriptor",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} Data Administration",
        subtitle = "Data record management baseline",
        body =
          s"""${nav}
             |${admin_card("Data record management", "<p>Concrete data collections use the existing list/detail/new/edit flows. Open a descriptor-backed data surface from the component descriptor or a direct collection link.</p><p class=\"mb-0\"><span class=\"badge text-bg-secondary\">Implemented baseline</span></p>")}
             |${admin_card("Deferred capabilities", "<p class=\"mb-0\">Delete flows, destructive confirmation patterns, and runtime configuration mutation remain deferred in this slice.</p>")}""".stripMargin
      ))
    }

  def renderComponentAdminDataType(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    find_component(subsystem, componentName).map { component =>
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
        admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.IdFirst
      )
      val result = admin_data_list(subsystem, componentPath, dataPath, effectivePageRequest)
      val warningHtml = admin_warnings(result.warnings)
      val table = admin_read_result_list_table(
        result.items,
        webSchema.fieldNames,
        basePath,
        "No records are currently available for this data collection.",
        includeEdit = true
      )
      val nav = admin_nav_card(Vector(
        "Component admin" -> s"/web/${componentPath}/admin",
        "Data CRUD" -> s"/web/${componentPath}/admin/data",
        "Operation forms" -> s"/form/${componentPath}"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(dataPath))} Data Administration",
        subtitle = "Data record list baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <div>
             |        <h2 class="card-title mb-1">${escape(title_label(dataPath))} records</h2>
             |        <p class="card-text text-body-secondary">List with paging${result.total.map(t => s" · total ${escape(t.toString)}").getOrElse("")}</p>
             |      </div>
             |      <a class="btn btn-primary" href="${escape(basePath)}/new">New ${escape(title_label(dataPath))}</a>
             |    </div>
             |    ${warningHtml}
             |    ${table}
             |    ${paging_nav(result.page, result.pageSize, result.total, effectivePageRequest.href(basePath), Some(result.hasNext))}
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val basePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val nav = admin_nav_card(Vector(
        s"Back to ${title_label(dataPath)} records" -> basePath
      ))
      val body = admin_data_record_table(subsystem, componentPath, dataPath, id, webDescriptor)
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(dataPath))} Data Detail",
        subtitle = "Data record detail baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <div class="d-flex flex-wrap gap-2 align-items-center justify-content-between mb-3">
             |      <h2 class="card-title mb-0">${escape(title_label(dataPath))} detail</h2>
             |      <a class="btn btn-primary" href="${escape(basePath)}/${escape(id)}/edit">Edit</a>
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val actionPath = s"/form/${componentPath}/admin/data/${dataPath}/${id}/update"
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val effectiveValidation = validation.filter(_.webSchema.selector == webSchema.selector)
      val hiddenContext = hidden_form_context_inputs(values)
      val controls = admin_record_controls(
        webSchema.fields,
        admin_data_record_fields(subsystem, componentPath, dataPath, id).getOrElse(Vector("id" -> id)),
        values,
        "data-field",
        effectiveValidation
      )
      val nav = admin_nav_card(Vector(
        "Detail" -> s"${webBasePath}/${id}",
        s"Back to ${title_label(dataPath)} records" -> webBasePath
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(dataPath))} Data Edit",
        subtitle = "Data record edit baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">Edit ${escape(title_label(dataPath))}</h2>
             |    ${form_error_panel(values)}${form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${escape(actionPath)}" class="admin-form">
             |      ${controls}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Update</button>
             |        <a class="btn btn-outline-secondary" href="${escape(webBasePath)}/${escape(id)}">Cancel</a>
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
    find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val actionPath = s"/form/${componentPath}/admin/data/${dataPath}/create"
      val webSchema = WebSchemaResolver.resolveData(
        componentPath,
        dataPath,
        webDescriptor,
        admin_data_schema_fields(subsystem, componentPath, dataPath),
        fieldOrderStrategy = WebSchemaResolver.FieldOrderStrategy.SchemaOrder
      )
      val effectiveValidation = validation.filter(_.webSchema.selector == webSchema.selector)
      val hiddenContext = hidden_form_context_inputs(values)
      val controls = admin_new_controls(webSchema.fields, values, "dataFields", "id=record-1&#10;status=draft", effectiveValidation)
      val nav = admin_nav_card(Vector(
        s"Back to ${title_label(dataPath)} records" -> webBasePath,
        "Data CRUD" -> s"/web/${componentPath}/admin/data"
      ))
      Page(simple_page(
        title = s"${escape(component.name)} ${escape(title_label(dataPath))} Data New",
        subtitle = "Data record create baseline",
        body =
          s"""${nav}
             |<article class="card admin-card">
             |  <div class="card-body">
             |    <h2 class="card-title">New ${escape(title_label(dataPath))}</h2>
             |    ${form_error_panel(values)}${form_validation_panel(effectiveValidation)}
             |    <form method="post" action="${escape(actionPath)}" class="admin-form">
             |      ${controls}
             |      ${hiddenContext}
             |      <div class="d-flex flex-wrap gap-2">
             |        <button type="submit" class="btn btn-primary">Create</button>
             |        <a class="btn btn-outline-secondary" href="${escape(webBasePath)}">Cancel</a>
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
    Page(simple_page(
      title = s"${escape(componentName)} ${escape(title_label(dataPath))} Data Update Result",
      subtitle = "Data record update submission baseline",
      body =
        s"""${admin_nav_card(Vector(
             "Detail" -> s"${webBasePath}/${id}",
             s"Back to ${title_label(dataPath)} records" -> webBasePath,
             "Edit again" -> s"${webBasePath}/${id}/edit"
           ))}
           |${admin_card(
             "Update submitted",
             s"""<p>${escape(message)}</p>
                |${admin_table(Some(admin_result_rows(applied, resultStatus, message)), submitted_fields_rows(values))}""".stripMargin
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
    Page(simple_page(
      title = s"${escape(componentName)} ${escape(title_label(dataPath))} Data Create Result",
      subtitle = "Data record create submission baseline",
      body =
        s"""${admin_nav_card(Vector(
             s"Back to ${title_label(dataPath)} records" -> webBasePath,
             "Create another" -> s"${webBasePath}/new"
           ))}
           |${admin_card(
             "Create submitted",
             s"""<p>${escape(message)}</p>
                |${admin_table(Some(admin_result_rows(applied, resultStatus, message)), submitted_fields_rows(values))}""".stripMargin
           )}""".stripMargin
    ))
  }

  protected def admin_result_rows(
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
      s"<tr><th>${escape(key)}</th><td>${escape(value)}</td></tr>"
    }.mkString
}
