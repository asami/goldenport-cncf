package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.component.ComponentIdentityCompatibilityAdapter
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
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[http] trait StaticFormAppRendererManualPart { self: StaticFormAppRendererSystemAdminPart & StaticFormAppRendererSupport & StaticFormAppRendererBlobTagPart & StaticFormAppRendererComponentAdminPart & StaticFormAppRendererCorePart & StaticFormAppRendererFormPart & StaticFormAppRendererJobPart & StaticFormAppRendererObservabilityPart & StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*
  protected def performance_page(subsystem: Subsystem): String = {
    val htmlrequests = RuntimeDashboardMetrics.htmlSnapshot
    val actioncalls = RuntimeDashboardMetrics.actionCallSnapshot
    val authorizationdecisions = RuntimeDashboardMetrics.authorizationDecisionSnapshot
    val authorizationdiagnostics = RuntimeDashboardMetrics.authorizationDiagnosticCounts
    val authorizationdiagnosticrecords = RuntimeDashboardMetrics.authorizationDiagnosticRecords
    val dslchokepoints = RuntimeDashboardMetrics.dslChokepointSnapshot
    val validation = RuntimeDashboardMetrics.validationSnapshot
    val validationdiagnostics = RuntimeDashboardMetrics.validationDiagnosticCounts
    val validationdiagnosticrecords = RuntimeDashboardMetrics.validationDiagnosticRecords
    val operationrequestvalidation = RuntimeDashboardMetrics.operationRequestValidationSnapshot
    val operationrequestvalidationdiagnostics = RuntimeDashboardMetrics.operationRequestValidationDiagnosticCounts
    val operationrequestvalidationdiagnosticrecords = RuntimeDashboardMetrics.operationRequestValidationDiagnosticRecords
    val bloboperations = RuntimeDashboardMetrics.blobOperationSnapshot
    val blobdiagnostics = RuntimeDashboardMetrics.blobDiagnosticCounts
    val blobdiagnosticrecords = RuntimeDashboardMetrics.blobDiagnosticRecords
    val jobs = job_metrics(subsystem)
    def _card_(title: String, body: String, id: Option[String] = None): String =
      s"""<div class="col-12">${admin_card(title, body, id)}</div>"""
    val navigationcard = _card_(
      "Navigation",
      """<nav class="nav nav-pills flex-column flex-sm-row gap-2">
        |  <a class="nav-link border" href="/web/system/dashboard">System dashboard</a>
        |  <a class="nav-link border" href="/web/system/admin">Admin configuration</a>
        |  <a class="nav-link border" href="/web/system/admin/observability">Observability drill-down</a>
        |  <a class="nav-link border" href="/web/system/admin/observability/metrics">Metrics</a>
        |  <a class="nav-link border" href="/man/system">Manuals</a>
        |  <a class="nav-link border" href="/web/console">Console</a>
        |</nav>""".stripMargin,
      Some("performance-navigation")
    )
    val assemblyactions = admin_action_row(Vector(
      "Warning detail" -> "/web/system/admin/assembly/warnings",
      "Assembly report" -> "/web/system/admin/assembly/report"
    ), primary = false)
    val assemblycard = _card_(
      "Assembly warnings",
      s"""<p><span class="badge text-bg-secondary">${assembly_warning_count(subsystem)}</span> warning(s).</p>
         |${assemblyactions}""".stripMargin
    )
    val recenterrorscard = _card_(
      "Recent errors",
      s"""<p class="text-secondary">HTTP 4xx/5xx entries shown as Dashboard recent failures. They are diagnostics and do not change runtime Health.</p>
         |${recent_errors_table(htmlrequests.recent)}""".stripMargin,
      Some("recent-errors")
    )
    val actioncallactions = admin_action_row(Vector(
      "Execution history" -> "/form/admin/execution/history",
      "Latest calltree" -> "/form/admin/execution/calltree"
    ), primary = false)
    val actioncallcard = _card_(
      "ActionCall",
      s"""${summary_table(actioncalls.summary)}
         |${actioncallactions}""".stripMargin
    )
    val authorizationcard = _card_(
      "Authorization",
      s"""${summary_table(authorizationdecisions.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(authorizationdiagnostics, authorizationdiagnosticrecords, Some("authorization"))}""".stripMargin,
      Some("authorization")
    )
    val validationcard = _card_(
      "Validation",
      s"""${summary_table(validation.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(validationdiagnostics, validationdiagnosticrecords, Some("validation"))}""".stripMargin
    )
    val operationrequestvalidationcard = _card_(
      "Operation Request Validation",
      s"""${summary_table(operationrequestvalidation.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(operationrequestvalidationdiagnostics, operationrequestvalidationdiagnosticrecords, Some("operation-request-validation"))}""".stripMargin
    )
    val bloboperationscard = _card_(
      "Blob operations",
      s"""${summary_table(bloboperations.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(blobdiagnostics, blobdiagnosticrecords, Some("blob"))}""".stripMargin
    )
    val cards = Vector(
      navigationcard,
      assemblycard,
      _card_("HTML request", summary_table(htmlrequests.summary), Some("html-requests")),
      _card_("Latency", latency_table(htmlrequests.recent)),
      _card_("Recent requests", recent_requests_table(htmlrequests.recent)),
      recenterrorscard,
      actioncallcard,
      authorizationcard,
      _card_("DSL Chokepoints", summary_table(dslchokepoints.summary)),
      validationcard,
      operationrequestvalidationcard,
      bloboperationscard,
      _card_("Jobs", jobs_table(jobs), Some("jobs"))
    ).mkString("\n")
    simple_page(
      title = "System Performance",
      subtitle = "HTML request, ActionCall, authorization, and Jobs detail",
      body =
        s"""<section class="row g-3">
           |${cards}
           |</section>""".stripMargin
    )
  }


  protected def component_dashboard_state(component: Component): String =
    dashboard_state_json(Vector(component), "component", component.name, component.artifactMetadata.map(_.version), job_metrics(component), component.subsystem.map(_.name).getOrElse(component.name), component.subsystem.flatMap(_.version), component.subsystem.map(assembly_warning_count).getOrElse(0))

  protected def manual_page(
    title: String,
    subtitle: String,
    component: Component,
    selector: Option[String],
    currentPath: String,
    childNames: Vector[String]
  ): String = {
    val componentpath = NamingConventions.toNormalizedSegment(component.displayName)
    val target = selector.getOrElse(component.displayName)
    val iscomponentroot = selector.exists { candidate =>
      NamingConventions.equivalentByNormalized(component.name, candidate) ||
        NamingConventions.equivalentByNormalized(component.displayName, candidate)
    }
    val help = manual_projection_or_error("Help", target)(HelpProjection.project(component, selector))
    val describe = manual_projection_or_error("Describe", target)(DescribeProjection.project(component, selector))
    val schema = manual_projection_or_error("Schema", target)(SchemaProjection.project(component, selector))
    val childlinks = manual_child_links(currentPath, childNames)
    val componentletcard =
      if (iscomponentroot)
        manual_componentlet_section(component)
      else
        ""
    val storageshapecard =
      if (iscomponentroot)
        manual_storage_shape_section(describe)
      else
        ""
    val authorizationpolicycard =
      if (iscomponentroot)
        manual_authorization_policy_section(describe)
      else
        ""
    val body =
      s"""${manual_card("Specification navigation",
         s"""<p>This generated specification is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<p class="mb-0">CLI help: <code>cncf command meta.help ${escape(componentpath)}</code></p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="${escape(currentPath)}#help">Help</a>
            |  <a class="btn btn-outline-primary" href="${escape(currentPath)}#describe">Describe</a>
            |  <a class="btn btn-outline-primary" href="${escape(currentPath)}#schema">Schema</a>
            |  <a class="btn btn-outline-secondary" href="/man/${escape(componentpath)}">Manual</a>
            |  <a class="btn btn-outline-secondary" href="/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${manual_card("Children", childlinks)}
         |${componentletcard}
         |${storageshapecard}
         |${authorizationpolicycard}
         |${manual_projection_card("Help", currentPath, help, Some("help"))}
         |${manual_projection_card("Describe", currentPath, describe, Some("describe"))}
         |${manual_projection_card("Schema", currentPath, schema, Some("schema"))}""".stripMargin
    simple_page(title, subtitle, body)
  }

  protected def system_manual_page(
    subsystem: Subsystem,
    component: Component
  ): String = {
    val help = manual_projection_or_error("Help", "system")(HelpProjection.project(component, None))
    val describe = manual_projection_or_error("Describe", "system")(DescribeProjection.project(component, None))
    val schema = manual_projection_or_error("Schema", "system")(SchemaProjection.project(component, None))
    val componentlinks = manual_component_links(subsystem.components)
    val body =
      s"""${manual_card("Specification navigation",
         s"""<p>This generated specification is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="/web/system/dashboard">System dashboard</a>
            |  <a class="btn btn-outline-primary" href="/web/system/admin">Admin configuration</a>
            |  <a class="btn btn-outline-primary" href="/web/system/performance">Performance details</a>
            |  <a class="btn btn-outline-secondary" href="/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${manual_card("Components", componentlinks)}
         |${manual_card("Console handoff", """<p class="mb-0">Use <a href="/web/console">System Console</a> for controlled operation entry. Specification pages remain read-only and do not inline operation actions.</p>""")}
         |${manual_authorization_policy_section(describe)}
         |${manual_projection_card("Help", "/help/system", help, Some("help"))}
         |${manual_projection_card("Describe", "/help/system", describe, Some("describe"))}
         |${manual_projection_card("Schema", "/help/system", schema, Some("schema"))}""".stripMargin
    simple_page("System Specification", "Generated runtime specification", body)
  }

  protected def manual_component_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      web_empty_state("No component reference entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.displayName)
        s"""<a class="btn btn-sm btn-outline-primary" href="/help/${escape(segment)}">${escape(component.displayName)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  protected def manual_component_document_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      web_empty_state("No component document entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.displayName)
        s"""<a class="btn btn-sm btn-outline-primary" href="/man/${escape(segment)}">${escape(component.displayName)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  protected def manual_child_links(
    currentPath: String,
    children: Vector[String]
  ): String =
    if (children.isEmpty)
      web_empty_state("No child reference entries.")
    else
      children.map { child =>
        val segment = NamingConventions.toNormalizedSegment(child)
        s"""<a class="btn btn-sm btn-outline-primary" href="${escape(currentPath + "/" + segment)}">${escape(child)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  protected def manual_projection_card(
    title: String,
    currentPath: String,
    record: Record,
    id: Option[String] = None
  ): String =
    manual_card(
      title,
      manual_projection_body(title, currentPath, record),
      id
    )

  private[http] def manual_projection_or_error(
    label: String,
    target: String
  )(projection: => Record): Record =
    try {
      projection
    } catch {
      case NonFatal(error) =>
        Record.data(
          "type" -> "error",
          "name" -> target,
          "summary" -> s"$label projection unavailable",
          "error" -> Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
        )
    }

  protected def manual_projection_body(
    title: String,
    currentPath: String,
    record: Record
  ): String =
    s"""${manual_projection_summary(currentPath, record)}
       |${manual_raw_details(title, record)}""".stripMargin

  protected def manual_projection_summary(
    currentPath: String,
    record: Record
  ): String = {
    val recordtype = record.getString("type").getOrElse("")
    recordtype match {
      case "operation" =>
        manual_operation_summary(currentPath, record)
      case "service" =>
        manual_service_summary(record)
      case "component" =>
        manual_component_summary(record)
      case "subsystem" =>
        manual_subsystem_summary(record)
      case "schema" =>
        manual_schema_summary(record)
      case _ =>
        manual_generic_summary(record)
    }
  }

  protected def manual_subsystem_summary(
    record: Record
  ): String = {
    val name = record.getString("name").getOrElse("subsystem")
    val summary = record.getString("summary").getOrElse("")
    val children = manual_seq_values(record.asMap.get("children"))
    val detailcomponents = manual_record_values(record.asMap.get("details")).get("components").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val components = if (detailcomponents.nonEmpty) detailcomponents else children
    s"""<p class="mb-3">${escape(if (summary.nonEmpty) summary else s"Subsystem: $name")}</p>
       |${manual_kv_summary(Vector(
         "Name" -> name,
         "Component count" -> components.size.toString
       ))}
       |${manual_badges("Components", components)}""".stripMargin
  }

  protected def manual_component_summary(
    record: Record
  ): String = {
    val services = manual_record_seq(record.asMap.get("services")).flatMap(_.getString("name"))
    val componentlets = manual_record_seq(record.asMap.get("componentlets")).flatMap(_.getString("name"))
    val aggregates = manual_record_seq(record.asMap.get("aggregates")).flatMap(_.getString("name"))
    val views = manual_record_seq(record.asMap.get("views")).flatMap(_.getString("name"))
    val relationships = manual_record_seq(record.asMap.get("relationshipDefinitions"))
    val operationdefs = manual_record_seq(record.asMap.get("operationDefinitions")).flatMap(_.getString("name"))
    val artifact = manual_record_values(record.asMap.get("artifact"))
    s"""<p class="mb-3">${escape(record.getString("summary").getOrElse(s"Component ${record.getString("name").getOrElse("")}"))}</p>
       |${manual_kv_summary(Vector(
         "Name" -> record.getString("name").getOrElse(""),
         "Origin" -> record.getString("origin").getOrElse(""),
         "Artifact" -> artifact.get("name").flatMap(manual_scalar).getOrElse(""),
         "Version" -> artifact.get("version").flatMap(manual_scalar).getOrElse(""),
         "Service count" -> services.size.toString,
         "Componentlet count" -> componentlets.size.toString,
         "Aggregate count" -> aggregates.size.toString,
         "View count" -> views.size.toString,
         "Relationship count" -> relationships.size.toString,
         "Operation definition count" -> operationdefs.size.toString
       ))}
       |${manual_badges("Services", services)}
       |${manual_badges("Componentlets", componentlets)}
       |${manual_relationship_table(relationships)}""".stripMargin
  }

  protected def manual_relationship_table(
    relationships: Vector[Record]
  ): String =
    if (relationships.isEmpty)
      ""
    else {
      val rows = relationships.map { r =>
        val name = escape(r.getString("name").getOrElse(""))
        val kind = escape(r.getString("kind").getOrElse(""))
        val source = escape(r.getString("sourceEntityName").getOrElse(""))
        val target = escape(r.getString("targetEntityName").getOrElse(""))
        val targetmodel = escape(r.getString("targetModelKind").getOrElse(""))
        val storage = escape(r.getString("storageMode").getOrElse(""))
        val parent = escape(r.getString("parentIdField").getOrElse(""))
        val value = escape(r.getString("valueField").getOrElse(""))
        val sort = escape(r.getString("sortOrderField").getOrElse(""))
        val domain = escape(r.getString("associationDomain").getOrElse(""))
        val targetkind = escape(r.getString("targetKind").getOrElse(""))
        val lifecycle = escape(r.getString("lifecyclePolicy").getOrElse(""))
        s"<tr><td>$name</td><td>$kind</td><td>$source</td><td>$target</td><td>$targetmodel</td><td>$storage</td><td>$parent</td><td>$value</td><td>$sort</td><td>$domain</td><td>$targetkind</td><td>$lifecycle</td></tr>"
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

  protected def manual_storage_shape_section(
    record: Record
  ): String = {
    val entities = manual_record_seq(record.asMap.get("entityCollections"))
    if (entities.isEmpty)
      ""
    else {
      val summaryrows = entities.map(manual_storage_shape_summary_row).mkString("\n")
      val fieldtables = entities.map(manual_storage_shape_field_table).mkString("\n")
      manual_card(
        "Storage shape",
        s"""<p class="mb-3">Effective SimpleEntity storage-shape metadata from the component projection.</p>
           |<div class="table-responsive">
           |  <table class="table table-sm table-hover align-middle manual-summary-table">
           |    <thead><tr><th>Entity</th><th>Collection</th><th>Memory policy</th><th>Working-set policy</th><th>Storage policy</th></tr></thead>
           |    <tbody>
           |      ${summaryrows}
           |    </tbody>
           |  </table>
           |</div>
           |${fieldtables}""".stripMargin,
        Some("storage-shape")
      )
    }
  }

  protected def manual_authorization_policy_section(
    record: Record
  ): String = {
    val policy = record.getAny("authorizationPolicies").collect { case r: Record => r }
    policy match {
      case Some(p) if AuthorizationPolicyProjection.hasVisiblePolicy(p) =>
        val roles = manual_record_seq(p.asMap.get("roleDefinitions"))
        val resources = manual_record_seq(p.asMap.get("resourcePolicies"))
        val blobrequirements = manual_record_seq(p.asMap.get("blobOperationRequirements"))
        val roletable =
          if (roles.isEmpty)
            web_empty_state("No role definitions are configured.")
          else
            s"""<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-roles">
               |    <thead><tr><th>Role</th><th>Includes</th><th>Capabilities</th><th>Source</th></tr></thead>
               |    <tbody>${roles.map(manual_authorization_role_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        val resourcetable =
          if (resources.isEmpty)
            web_empty_state("No resource policies are configured.")
          else
            s"""<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-resources">
               |    <thead><tr><th>Family</th><th>Resource</th><th>Action</th><th>Capabilities</th><th>Permission</th><th>Source</th></tr></thead>
               |    <tbody>${resources.map(manual_authorization_resource_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        val blobrequirementtable =
          if (blobrequirements.isEmpty)
            ""
          else
            s"""<h3 class="h6 mt-3">Blob operation requirements</h3>
               |<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-blob-requirements">
               |    <thead><tr><th>Operation</th><th>Family</th><th>Resource</th><th>Action</th><th>Requirement</th></tr></thead>
               |    <tbody>${blobrequirements.map(manual_authorization_blob_requirement_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        manual_card(
          "Authorization policies",
          s"""<p class="mb-3">Read-only view of descriptor-backed authorization policy and Blob operation requirements.</p>
             |<h3 class="h6">Resource policies</h3>
             |${resourcetable}
             |<h3 class="h6 mt-3">Role definitions</h3>
             |${roletable}
             |${blobrequirementtable}
             |${manual_raw_details("Authorization policies", p)}""".stripMargin,
          Some("authorization-policies")
        )
      case _ =>
        ""
    }
  }

  protected def manual_authorization_role_row(
    record: Record
  ): String =
    s"""<tr>
       |  <td><code>${escape(record.getString("name").getOrElse(""))}</code></td>
       |  <td>${escape(manual_seq_values(record.asMap.get("includes")).mkString(", "))}</td>
       |  <td>${escape(manual_seq_values(record.asMap.get("capabilities")).mkString(", "))}</td>
       |  <td>${escape(record.getString("source").getOrElse(""))}</td>
       |</tr>""".stripMargin

  protected def manual_authorization_resource_row(
    record: Record
  ): String =
    s"""<tr>
       |  <td>${escape(record.getString("family").getOrElse(""))}</td>
       |  <td><code>${escape(record.getString("resource").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("action").getOrElse(""))}</code></td>
       |  <td>${escape(manual_seq_values(record.asMap.get("requiredCapabilities")).mkString(", "))}</td>
       |  <td>${escape(record.getString("permissionOverride").filter(_.nonEmpty).getOrElse("-"))}</td>
       |  <td>${escape(record.getString("source").getOrElse(""))}</td>
       |</tr>""".stripMargin

  protected def manual_authorization_blob_requirement_row(
    record: Record
  ): String =
    s"""<tr>
       |  <td><code>${escape(record.getString("operation").getOrElse(""))}</code></td>
       |  <td>${escape(record.getString("family").getOrElse(""))}</td>
       |  <td><code>${escape(record.getString("resource").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("action").getOrElse(""))}</code></td>
       |  <td>${escape(record.getString("requirement").getOrElse(""))}</td>
       |</tr>""".stripMargin

  protected def manual_storage_shape_summary_row(
    record: Record
  ): String = {
    val shape = manual_record_values(record.asMap.get("storageShape"))
    val policy = shape.get("policy").flatMap(manual_scalar).getOrElse("")
    s"""<tr>
       |  <td><code>${escape(record.getString("entityName").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("collectionId").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("memoryPolicy").getOrElse(""))}</code></td>
       |  <td><code>${escape(record.getString("workingSetPolicy").getOrElse("-"))}</code></td>
       |  <td><code>${escape(policy)}</code></td>
       |</tr>""".stripMargin
  }

  protected def manual_storage_shape_field_table(
    record: Record
  ): String = {
    val entityname = record.getString("entityName").getOrElse("")
    val shape = manual_record_values(record.asMap.get("storageShape"))
    val fields = manual_record_seq(shape.get("fields"))
    if (fields.isEmpty)
      s"""<section class="mt-3">
         |  <h3 class="h6">${escape(entityname)} fields</h3>
         |  ${web_empty_state("No storage-shape field metadata.")}
         |</section>""".stripMargin
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
         |  <h3 class="h6">${escape(entityname)} fields</h3>
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

  protected def manual_service_summary(
    record: Record
  ): String = {
    val children = manual_seq_values(record.asMap.get("children"))
    val operations = manual_record_seq(record.asMap.get("operations")).flatMap(_.getString("name"))
    val items = if (operations.nonEmpty) operations else children
    s"""<p class="mb-3">${escape(record.getString("summary").getOrElse("Service reference"))}</p>
       |${manual_kv_summary(Vector(
         "Service" -> record.getString("name").getOrElse(""),
         "Operation count" -> items.size.toString
       ))}
       |${manual_badges("Operations", items)}""".stripMargin
  }

  protected def manual_operation_summary(
    currentPath: String,
    record: Record
  ): String = {
    val qualifiedname = record.getString("name").getOrElse("")
    val qualifiedsegments = qualifiedname.split("\\.").toVector.filter(_.nonEmpty)
    val component = record.getString("component").orElse(qualifiedsegments.headOption).getOrElse("")
    val service = record.getString("service").orElse(qualifiedsegments.lift(1)).getOrElse("")
    val operation = qualifiedsegments.lift(2).orElse(Option(qualifiedname).filter(_.nonEmpty)).getOrElse("")
    val selector = manual_selector_map(record)
    val details = manual_record_values(record.asMap.get("details"))
    val arguments = details.get("arguments").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val returns = details.get("returns").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val description = details.get("description").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty).mkString(" ")
    val selectortext = selector.get("canonical").flatMap(manual_scalar).orElse(record.getString("selector")).getOrElse(qualifiedname)
    val restpath = selector.get("rest").flatMap(manual_scalar).map(manual_canonical_rest_path).getOrElse(s"/rest/v1/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}")
    val formpath = s"/form/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    val formapipath = s"/form-api/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    val describeargumentrows = manual_record_seq(record.asMap.get("arguments")).map(manual_parameter_row)
    val parameterrows =
      if (describeargumentrows.nonEmpty)
        describeargumentrows
      else
        manual_schema_parameters_from_help(details, component, service, operation)
    s"""<p class="mb-3">${escape(record.getString("summary").getOrElse("Operation reference"))}</p>
       |${if (description.nonEmpty) s"""<p class="mb-3">${escape(description)}</p>""" else ""}
       |${manual_kv_summary(Vector(
         "Selector" -> selectortext,
         "Component" -> component,
         "Service" -> service,
         "Operation" -> operation,
         "Arguments" -> (if (parameterrows.nonEmpty) parameterrows.size else arguments.size).toString,
         "Returns" -> returns.mkString(", ")
       ))}
       |${manual_link_group(Vector(
         "Web specification" -> currentPath,
         "REST" -> restpath,
         "Form" -> formpath,
         "Form API" -> formapipath,
       "OpenAPI JSON" -> "/openapi.json"
      ))}
       |${manual_child_entity_binding_summary(record)}
       |${manual_association_binding_summary(record)}
       |${manual_image_binding_summary(record)}
       |${manual_parameter_table(parameterrows)}
       |${manual_response_summary(returns)}""".stripMargin
  }

  protected def manual_child_entity_binding_summary(
    record: Record
  ): String = {
    val bindings = manual_record_seq(record.asMap.get("childEntityBindings"))
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
         |  ${manual_kv_summary(rows)}
         |</section>""".stripMargin
    }
  }

  protected def manual_association_binding_summary(
    record: Record
  ): String = {
    val binding = manual_record_values(record.asMap.get("associationBinding"))
    if (binding.isEmpty)
      ""
    else {
      val behavior = Vector(
        "create" -> binding.get("createsAssociation").flatMap(manual_scalar).contains("true"),
        "detach" -> binding.get("detachesAssociation").flatMap(manual_scalar).contains("true")
      ).collect { case (label, true) => label }
      val rows = Vector(
        "Domain" -> binding.get("domain").flatMap(manual_scalar).getOrElse(""),
        "Target kind" -> binding.get("targetKind").flatMap(manual_scalar).getOrElse(""),
        "Behavior" -> behavior.mkString(", "),
        "Source id mode" -> binding.get("sourceEntityIdMode").flatMap(manual_scalar).getOrElse(""),
        "Parameters" -> manual_seq_values(binding.get("parameters")).mkString(", "),
        "Source id parameters" -> manual_seq_values(binding.get("sourceEntityIdParameters")).mkString(", "),
        "Roles" -> manual_seq_values(binding.get("roles")).mkString(", "),
        "Target id parameters" -> manual_seq_values(binding.get("targetIdParameters")).mkString(", "),
        "Sort order parameters" -> manual_seq_values(binding.get("sortOrderParameters")).mkString(", ")
      ).filter { case (_, value) => value.nonEmpty }
      s"""<section class="mt-3">
         |  <h3 class="h6">Association Binding</h3>
         |  ${manual_kv_summary(rows)}
         |</section>""".stripMargin
    }
  }

  protected def manual_image_binding_summary(
    record: Record
  ): String = {
    val binding = manual_record_values(record.asMap.get("imageBinding"))
    if (binding.isEmpty)
      ""
    else {
      val modes = Vector(
        "upload" -> binding.get("acceptsUpload").flatMap(manual_scalar).contains("true"),
        "existing Blob id" -> binding.get("acceptsExistingBlobId").flatMap(manual_scalar).contains("true"),
        "archive Blob id" -> binding.get("acceptsArchiveBlobId").flatMap(manual_scalar).contains("true")
      ).collect { case (label, true) => label }
      val behavior = Vector(
        "attach" -> binding.get("createsAttachment").flatMap(manual_scalar).contains("true"),
        "detach" -> binding.get("detachesAttachment").flatMap(manual_scalar).contains("true")
      ).collect { case (label, true) => label }
      val rows = Vector(
        "Media kind" -> binding.get("mediaKind").flatMap(manual_scalar).getOrElse("image"),
        "Accepted input" -> modes.mkString(", "),
        "Behavior" -> behavior.mkString(", "),
        "Roles" -> manual_seq_values(binding.get("roles")).mkString(", "),
        "Parameters" -> manual_seq_values(binding.get("parameters")).mkString(", ")
      ).filter { case (_, value) => value.nonEmpty }
      s"""<section class="mt-3">
         |  <h3 class="h6">Image Binding</h3>
         |  ${manual_kv_summary(rows)}
         |</section>""".stripMargin
    }
  }

  protected def manual_canonical_rest_path(
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

  protected def manual_schema_summary(
    record: Record
  ): String = {
    val targettype = record.getString("targetType").getOrElse(record.getString("type").getOrElse(""))
    targettype match {
      case "operation" =>
        val request = manual_record_values(record.asMap.get("request"))
        val response = manual_record_values(record.asMap.get("response"))
        val params = manual_record_seq(request.get("parameters")).map(manual_parameter_row)
        val result = response.get("result").flatMap(manual_scalar)
        s"""${manual_kv_summary(Vector(
           "Schema target" -> record.getString("name").getOrElse(""),
           "Parameter count" -> params.size.toString,
           "Result" -> result.getOrElse("")
         ))}
         |${manual_parameter_table(params)}
         |${manual_response_summary(result.toVector)}""".stripMargin
      case "service" =>
        val ops = manual_record_seq(record.asMap.get("operations")).flatMap(_.getString("name"))
        s"""${manual_kv_summary(Vector(
           "Schema target" -> record.getString("name").getOrElse(""),
           "Operation count" -> ops.size.toString
         ))}
         |${manual_badges("Operations", ops)}""".stripMargin
      case "component" =>
        val services = manual_record_seq(record.asMap.get("services")).flatMap(_.getString("name"))
        val aggregates = manual_record_seq(record.asMap.get("aggregateCollections")).flatMap(_.getString("name"))
        val views = manual_record_seq(record.asMap.get("viewCollections")).flatMap(_.getString("name"))
        s"""${manual_kv_summary(Vector(
           "Schema target" -> record.getString("name").getOrElse(""),
           "Service count" -> services.size.toString,
           "Aggregate count" -> aggregates.size.toString,
           "View count" -> views.size.toString
         ))}
         |${manual_badges("Services", services)}""".stripMargin
      case _ =>
        manual_generic_summary(record)
    }
  }

  protected def manual_generic_summary(
    record: Record
  ): String =
    manual_kv_summary(Vector(
      "Type" -> record.getString("type").getOrElse(""),
      "Name" -> record.getString("name").getOrElse(""),
      "Summary" -> record.getString("summary").getOrElse("")
    ))

  protected def manual_response_summary(
    returns: Vector[String]
  ): String =
    if (returns.isEmpty)
      ""
    else
      s"""<section class="mt-3">
         |  <h3 class="h6">Response</h3>
         |  <p class="mb-0">${escape(returns.mkString(", "))}</p>
         |</section>""".stripMargin

  protected def manual_parameter_table(
    rows: Vector[Vector[String]]
  ): String =
    if (rows.isEmpty)
      web_empty_state("No parameter details.")
    else {
      val body = rows.map { row =>
        s"""<tr>${row.map(x => s"<td>${escape(x)}</td>").mkString}</tr>"""
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

  protected def manual_schema_parameters_from_help(
    details: Map[String, Any],
    component: String,
    service: String,
    operation: String
  ): Vector[Vector[String]] =
    details.get("arguments").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty).map { name =>
      Vector(name, "argument", "", "", "")
    }

  protected def manual_parameter_row(
    record: Record
  ): Vector[String] =
    Vector(
      record.getString("name").getOrElse(""),
      record.getString("kind").getOrElse(""),
      record.getString("type").getOrElse(record.getString("datatype").getOrElse("")),
      record.getString("multiplicity").getOrElse(""),
      record.getString("help").orElse(record.getString("placeholder")).orElse(record.getString("default")).getOrElse("")
    )

  protected def manual_selector_map(
    record: Record
  ): Map[String, Any] =
    manual_record_values(record.asMap.get("selector"))

  protected def manual_kv_summary(
    items: Vector[(String, String)]
  ): String = {
    val effective = items.filter { case (_, v) => v != null && v.nonEmpty }
    if (effective.isEmpty)
      web_empty_state("No summary details.")
    else {
      val rows = effective.map { case (key, value) =>
        s"""<tr><th>${escape(key)}</th><td><code>${escape(value)}</code></td></tr>"""
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

  protected def manual_link_group(
    links: Vector[(String, String)]
  ): String = {
    val effective = links.filter { case (_, href) => href != null && href.nonEmpty }
    if (effective.isEmpty)
      ""
    else
      effective.map { case (label, href) =>
        s"""<a class="btn btn-sm btn-outline-secondary" href="${escape(href)}">${escape(label)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2 mt-3">""", "\n", "</div>")
  }

  protected def manual_badges(
    title: String,
    items: Vector[String]
  ): String =
    if (items.isEmpty)
      ""
    else
      s"""<section class="mt-3">
         |  <h3 class="h6">${escape(title)}</h3>
         |  <div class="d-flex flex-wrap gap-2">${items.map(x => s"""<span class="badge text-bg-light border">${escape(x)}</span>""").mkString("\n")}</div>
         |</section>""".stripMargin

  protected def manual_raw_details(
    title: String,
    record: Record
  ): String =
    val rendered = manual_raw_json(record).map(_.spaces2).getOrElse(manual_raw_text(record))
    val yaml = manual_raw_json(record).map(json_to_yaml).getOrElse(manual_raw_text(record))
    s"""<details class="mt-3 manual-raw-details">
       |  <summary>Raw ${escape(title)}</summary>
       |  ${raw_format_tabs(rendered, yaml, "manual")}
       |</details>""".stripMargin

}
