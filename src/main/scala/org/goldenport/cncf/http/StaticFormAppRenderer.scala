package org.goldenport.cncf.http

import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.CncfVersion
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import io.circe.Json
import io.circe.parser.parse

/*
 * @since   Apr. 12, 2026
 * @version Apr. 15, 2026
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
    body: String
  ) {
    def componentName: String = page.componentName
    def serviceName: String = page.serviceName
    def operationName: String = page.operationName

    def operationLabel: String =
      page.operationLabel

    def nextPageProperties: FormPageProperties =
      page
        .withValue("result.status", status.toString)
        .withValue("result.contentType", contentType)
        .withValue("result.body", body)
        .withValue("paging.page", page.values.getOrElse("paging.page", "1"))
        .withValue("paging.pageSize", page.values.getOrElse("paging.pageSize", "20"))
        .withValue("paging.chunkSize", page.values.getOrElse("paging.chunkSize", "1000"))
        .withValue("paging.href", page.values.getOrElse("paging.href", _default_paging_href))

    private def _default_paging_href: String =
      s"/form/${componentName}/${serviceName}/${operationName}/result?page={page}&pageSize={pageSize}"
  }

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
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    for {
      component <- _find_component(subsystem, componentName)
      service <- component.protocol.services.services.find(x => NamingConventions.equivalentByNormalized(x.name, serviceName))
      operation <- service.operations.operations.find(x => NamingConventions.equivalentByNormalized(x.name, operationName))
      if webDescriptor.isFormEnabled(_operation_selector(component.name, service.name, operation.name))
    } yield {
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val servicePath = NamingConventions.toNormalizedSegment(service.name)
      val operationPath = NamingConventions.toNormalizedSegment(operation.name)
      val action = s"/form/${componentPath}/${servicePath}/${operationPath}"
      Page(_simple_page(
        title = s"${_escape(component.name)}.${_escape(service.name)}.${_escape(operation.name)}",
        subtitle = "HTML form operation",
        body =
          s"""<article>
             |  <form method="post" action="${_escape(action)}">
             |    <div class="mb-3">
             |      <label class="form-label" for="formFields">Fields</label>
             |      <textarea class="form-control" id="formFields" name="fields" rows="6" placeholder="name=value&#10;keyword=sample"></textarea>
             |      <div class="form-text">Use one name=value pair per line. Query-style values are also accepted.</div>
             |    </div>
             |    <button type="submit" class="btn btn-primary">Run</button>
             |    <a class="btn btn-outline-secondary" href="/form/${componentPath}">Operations</a>
             |  </form>
             |</article>""".stripMargin
      ))
    }

  def renderFormResult(
    properties: FormResultProperties
  ): Page = {
    val pageProperties = properties.nextPageProperties
    val template =
      s"""<article>
         |  <h2>Result</h2>
         |  <p>Content-Type $${result.contentType}</p>
         |  <textus-result-view source="result.body"></textus-result-view>
         |  <textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus-result-table>
         |  <textus-property-list source="result"></textus-property-list>
         |  <p><a href="/form/${_escape(NamingConventions.toNormalizedSegment(properties.componentName))}/${_escape(NamingConventions.toNormalizedSegment(properties.serviceName))}/${_escape(NamingConventions.toNormalizedSegment(properties.operationName))}">Run again</a> · <a href="/form/${_escape(NamingConventions.toNormalizedSegment(properties.componentName))}">Operations</a></p>
         |</article>""".stripMargin
    Page(_simple_page(
      title = s"${_escape(properties.operationLabel)} Result",
      subtitle = s"HTTP ${properties.status}",
      body = _render_template(template, pageProperties)
    ))
  }

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
      subtitle = "Resolved Web Descriptor JSON view",
      body =
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/admin">System admin</a> · <a href="/web/system/dashboard">System dashboard</a></p>
           |</article>
           |<article>
           |  <h2>Resolved Descriptor</h2>
           |  <pre class="bg-light border rounded p-3"><code>${_escape(_web_descriptor_json(webDescriptor))}</code></pre>
           |</article>""".stripMargin
    ))

  def renderComponentAdmin(
    subsystem: Subsystem,
    componentName: String,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map(renderComponentAdmin(_, webDescriptor))

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
          "<p>No entity runtime descriptors are registered for this component.</p>"
        } else {
          s"""<div class="table-responsive"><table class="table table-sm">
             |  <thead><tr><th>Entity</th><th>Collection</th><th>Usage</th><th>Operation</th><th>Domain</th><th>Working set</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      Page(_simple_page(
        title = s"${_escape(component.name)} Entity Administration",
        subtitle = "Entity CRUD management baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>Entity CRUD</h2>
             |  ${body}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminEntityType(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    pageRequest: PageRequest = PageRequest(),
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val effectivePageRequest = pageRequest.withTotalCountPolicy(
        webDescriptor.adminTotalCountPolicy(componentPath, "entity", entityPath)
      )
      val result = _admin_entity_list(subsystem, componentPath, entityPath, effectivePageRequest)
      val entityIds = result.ids
      val warningHtml = _admin_warnings(result.warnings)
      val rows =
        if (entityIds.isEmpty) {
          """<tr><td colspan="2">No records are currently available for this entity.</td></tr>"""
        } else {
          entityIds.map { id =>
            s"""<tr><td><code>${_escape(id)}</code></td><td><a href="${_escape(basePath)}/${_escape(id)}">Detail</a> · <a href="${_escape(basePath)}/${_escape(id)}/edit">Edit</a></td></tr>"""
          }.mkString("\n")
        }
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} Administration",
        subtitle = "Entity record list baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/web/${componentPath}/admin/entities">Entity types</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>${_escape(entityLabel)} records</h2>
             |  <p>List with paging${result.total.map(t => s" · total ${_escape(t.toString)}").getOrElse("")}</p>
             |  ${warningHtml}
             |  <p><a class="btn btn-primary" href="${_escape(basePath)}/new">New ${_escape(entityLabel)}</a></p>
             |  <div class="table-responsive"><table class="table table-sm">
             |    <thead><tr><th>Id</th><th>Actions</th></tr></thead>
             |    <tbody>
             |      ${rows}
             |    </tbody>
             |  </table></div>
             |  ${_paging_nav(result.page, result.pageSize, result.total, effectivePageRequest.href(basePath), Some(result.hasNext))}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminEntityDetail(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    id: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val basePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val body = _admin_entity_record_table(subsystem, componentPath, entityPath, id)
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} Detail",
        subtitle = "Entity record detail baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="${_escape(basePath)}">Back to ${_escape(entityLabel)} records</a> · <a href="${_escape(basePath)}/${_escape(id)}/edit">Edit</a> · <a href="/web/${componentPath}/admin/entities">Entity types</a></p>
             |</article>
             |<article>
             |  <h2>${_escape(entityLabel)} detail</h2>
             |  ${body}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminEntityEdit(
    subsystem: Subsystem,
    componentName: String,
    entityName: String,
    id: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val actionPath = s"/form/${componentPath}/admin/entities/${entityPath}/${id}/update"
      val fields = _admin_entity_record_fields(subsystem, componentPath, entityPath, id).getOrElse(Vector("id" -> id))
      val controls = fields.map {
        case (key, value) =>
          s"""<div class="mb-3">
             |  <label class="form-label" for="field-${_escape(key)}">${_escape(key)}</label>
             |  <input class="form-control" id="field-${_escape(key)}" name="${_escape(key)}" value="${_escape(value)}">
             |</div>""".stripMargin
      }.mkString("\n")
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} Edit",
        subtitle = "Entity record edit baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="${_escape(webBasePath)}/${_escape(id)}">Detail</a> · <a href="${_escape(webBasePath)}">Back to ${_escape(entityLabel)} records</a></p>
             |</article>
             |<article>
             |  <h2>Edit ${_escape(entityLabel)}</h2>
             |  <form method="post" action="${_escape(actionPath)}">
             |    ${controls}
             |    <button type="submit" class="btn btn-primary">Update</button>
             |    <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}/${_escape(id)}">Cancel</a>
             |  </form>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminEntityNew(
    subsystem: Subsystem,
    componentName: String,
    entityName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityPath = NamingConventions.toNormalizedSegment(entityName)
      val entityLabel = _title_label(entityPath)
      val webBasePath = s"/web/${componentPath}/admin/entities/${entityPath}"
      val actionPath = s"/form/${componentPath}/admin/entities/${entityPath}/create"
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(entityLabel)} New",
        subtitle = "Entity record create baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="${_escape(webBasePath)}">Back to ${_escape(entityLabel)} records</a> · <a href="/web/${componentPath}/admin/entities">Entity types</a></p>
             |</article>
             |<article>
             |  <h2>New ${_escape(entityLabel)}</h2>
             |  <form method="post" action="${_escape(actionPath)}">
             |    <div class="mb-3">
             |      <label class="form-label" for="entityFields">Fields</label>
             |      <textarea class="form-control" id="entityFields" name="fields" rows="8" placeholder="id=sales-order-1&#10;status=draft"></textarea>
             |      <div class="form-text">Use one name=value pair per line until schema-driven fields are available.</div>
             |    </div>
             |    <button type="submit" class="btn btn-primary">Create</button>
             |    <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}">Cancel</a>
             |  </form>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminEntityUpdateResult(
    componentName: String,
    entityName: String,
    id: String,
    values: Map[String, String],
    applied: Boolean = false,
    message: String = "Entity update execution is not enabled in this baseline."
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
           |    <thead><tr><th>Applied</th><td>${applied}</td></tr></thead>
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
    message: String = "Entity create execution is not enabled in this baseline."
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
           |    <thead><tr><th>Applied</th><td>${applied}</td></tr></thead>
           |    <tbody>${rows}</tbody>
           |  </table></div>
           |</article>""".stripMargin
    ))
  }

  private final case class _AdminListResult(
    ids: Vector[String],
    page: Int,
    pageSize: Int,
    hasNext: Boolean,
    total: Option[Int],
    warnings: Vector[String]
  )

  private def _admin_entity_list(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    pageRequest: PageRequest
  ): _AdminListResult =
    _admin_list_result(
      subsystem,
      "/admin/entity/list",
      Record.create(Vector("component" -> componentPath, "entity" -> entityPath) ++ pageRequest.toPairs)
    )

  private def _admin_entity_record_table(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    id: String
  ): String =
    _admin_key_value_table(
      _admin_operation_lines(
        subsystem,
        "/admin/entity/read",
        Record.data("component" -> componentPath, "entity" -> entityPath, "id" -> id)
      ),
      s"""No record is currently available for id <code>${_escape(id)}</code>."""
    )

  private def _admin_entity_record_fields(
    subsystem: Subsystem,
    componentPath: String,
    entityPath: String,
    id: String
  ): Option[Vector[(String, String)]] =
    _admin_record_fields(
      subsystem,
      "/admin/entity/read",
      Record.data("component" -> componentPath, "entity" -> entityPath, "id" -> id)
    )

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
        val ids = record.getAny("ids") match {
          case Some(xs: Seq[?]) => xs.toVector.map(_.toString)
          case _ => Vector.empty
        }
        _AdminListResult(
          ids,
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
      s"""<div class="alert alert-warning" role="alert">${warnings.map(_escape).mkString("<br>")}</div>"""

  private def _admin_data_record_table(
    subsystem: Subsystem,
    componentPath: String,
    dataPath: String,
    id: String
  ): String =
    _admin_key_value_table(
      _admin_operation_lines(
        subsystem,
        "/admin/data/read",
        Record.data("component" -> componentPath, "data" -> dataPath, "id" -> id)
      ),
      s"""No data record is currently available for id <code>${_escape(id)}</code>."""
    )

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
    emptyMessage: String
  ): String =
    _admin_operation_value_lines(subsystem, path, form)
      .filter(_.nonEmpty)
      .map(_value_table)
      .getOrElse(s"<p>${_escape(emptyMessage)}</p>")

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
      s"<p>${emptyMessage}</p>"
    } else {
      _value_table(lines)
    }

  private def _value_table(lines: Vector[String]): String = {
    val rows = lines.map { line =>
      val i = line.indexOf("=")
      val (key, value) =
        if (i < 0) "value" -> line
        else line.take(i) -> line.drop(i + 1)
      s"""<tr><th>${_escape(key)}</th><td>${_escape(value)}</td></tr>"""
    }.mkString("\n")
    s"""<div class="table-responsive"><table class="table table-sm">
       |  <tbody>${rows}</tbody>
       |</table></div>""".stripMargin
  }

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

  private def _aggregate_definition(
    component: Component,
    aggregateName: String
  ) =
    component.aggregateDefinitions.find(d => NamingConventions.equivalentByNormalized(d.name, aggregateName))

  private def _aggregate_operation_actions(
    component: Component,
    aggregateName: String
  ): String = {
    val componentPath = NamingConventions.toNormalizedSegment(component.name)
    val bindings = _aggregate_operation_bindings(component, aggregateName)
    if (bindings.isEmpty) {
      "<p>No aggregate operations are currently exposed.</p>"
    } else {
      val rows = bindings.map { binding =>
        val path = s"/form/${componentPath}/${NamingConventions.toNormalizedSegment(binding.service)}/${NamingConventions.toNormalizedSegment(binding.operation)}"
        s"""<tr><td>${_escape(binding.kind)}</td><td>${_escape(binding.service)}</td><td>${_escape(binding.operation)}</td><td><a href="${_escape(path)}">Open form</a></td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Kind</th><th>Service</th><th>Operation</th><th>Action</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }
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
          "<p>No view definitions are registered for this component.</p>"
        } else {
          s"""<div class="table-responsive"><table class="table table-sm">
             |  <thead><tr><th>View</th><th>Entity</th><th>Names</th><th>Queries</th><th>Source events</th><th>Rebuildable</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      Page(_simple_page(
        title = s"${_escape(component.name)} View Administration",
        subtitle = "View read management baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>View read</h2>
             |  ${body}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminViewDetail(
    subsystem: Subsystem,
    componentName: String,
    viewName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val viewPath = NamingConventions.toNormalizedSegment(viewName)
      val definition = _view_definition(component, viewName)
      val metadata = definition match {
        case Some(d) =>
          s"""<div class="table-responsive"><table class="table table-sm">
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
          s"""<p>No view definition is registered for <code>${_escape(viewName)}</code>.</p>"""
      }
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(viewPath))} View",
        subtitle = "View read baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin/views">View definitions</a> · <a href="/web/${componentPath}/admin">Component admin</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>${_escape(_title_label(viewPath))} metadata</h2>
             |  ${metadata}
             |</article>
             |<article>
             |  <h2>Read result</h2>
             |  ${_admin_read_result_table(
                   subsystem,
                   "/admin/view/read",
                   Record.data("component" -> componentPath, "view" -> viewPath),
                   s"No view records are currently available for ${viewName}."
                 )}
             |</article>""".stripMargin
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
          "<p>No aggregate definitions are registered for this component.</p>"
        } else {
          s"""<div class="table-responsive"><table class="table table-sm">
             |  <thead><tr><th>Aggregate</th><th>Entity</th><th>Members</th><th>Creates</th><th>Commands</th><th>State</th><th>Invariants</th></tr></thead>
             |  <tbody>${rows}</tbody>
             |</table></div>""".stripMargin
        }
      Page(_simple_page(
        title = s"${_escape(component.name)} Aggregate Administration",
        subtitle = "Aggregate CRUD management baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>Aggregate CRUD</h2>
             |  ${body}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminAggregateDetail(
    subsystem: Subsystem,
    componentName: String,
    aggregateName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val aggregatePath = NamingConventions.toNormalizedSegment(aggregateName)
      val definition = _aggregate_definition(component, aggregateName)
      val metadata = definition match {
        case Some(d) =>
          s"""<div class="table-responsive"><table class="table table-sm">
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
          s"""<p>No aggregate definition is registered for <code>${_escape(aggregateName)}</code>.</p>"""
      }
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(aggregatePath))} Aggregate",
        subtitle = "Aggregate read baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin/aggregates">Aggregate definitions</a> · <a href="/web/${componentPath}/admin">Component admin</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>${_escape(_title_label(aggregatePath))} metadata</h2>
             |  ${metadata}
             |</article>
             |<article>
             |  <h2>Read result</h2>
             |  ${_admin_read_result_table(
                   subsystem,
                   "/admin/aggregate/read",
                   Record.data("component" -> componentPath, "aggregate" -> aggregatePath),
                   s"No aggregate records are currently available for ${aggregateName}."
                 )}
             |</article>
             |<article>
             |  <h2>Operations</h2>
             |  ${_aggregate_operation_actions(component, aggregateName)}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminData(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      Page(_simple_page(
        title = s"${_escape(component.name)} Data Administration",
        subtitle = "Data CRUD management baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>Data CRUD</h2>
             |  <p>Data CRUD execution is not enabled in this baseline. This page reserves the component-scoped management entry point for datastore records and document-level data operations.</p>
             |</article>""".stripMargin
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
      val result = _admin_data_list(subsystem, componentPath, dataPath, effectivePageRequest)
      val recordIds = result.ids
      val warningHtml = _admin_warnings(result.warnings)
      val rows =
        if (recordIds.isEmpty) {
          """<tr><td colspan="2">No records are currently available for this data collection.</td></tr>"""
        } else {
          recordIds.map { id =>
            s"""<tr><td><code>${_escape(id)}</code></td><td><a href="${_escape(basePath)}/${_escape(id)}">Detail</a> · <a href="${_escape(basePath)}/${_escape(id)}/edit">Edit</a></td></tr>"""
          }.mkString("\n")
        }
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data Administration",
        subtitle = "Data record list baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="/web/${componentPath}/admin">Component admin</a> · <a href="/web/${componentPath}/admin/data">Data CRUD</a> · <a href="/form/${componentPath}">Operation forms</a></p>
             |</article>
             |<article>
             |  <h2>${_escape(_title_label(dataPath))} records</h2>
             |  <p>List with paging${result.total.map(t => s" · total ${_escape(t.toString)}").getOrElse("")}</p>
             |  ${warningHtml}
             |  <p><a class="btn btn-primary" href="${_escape(basePath)}/new">New ${_escape(_title_label(dataPath))}</a></p>
             |  <div class="table-responsive"><table class="table table-sm">
             |    <thead><tr><th>Id</th><th>Actions</th></tr></thead>
             |    <tbody>${rows}</tbody>
             |  </table></div>
             |  ${_paging_nav(result.page, result.pageSize, result.total, effectivePageRequest.href(basePath), Some(result.hasNext))}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataDetail(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    id: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val basePath = s"/web/${componentPath}/admin/data/${dataPath}"
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data Detail",
        subtitle = "Data record detail baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="${_escape(basePath)}">Back to ${_escape(_title_label(dataPath))} records</a> · <a href="${_escape(basePath)}/${_escape(id)}/edit">Edit</a></p>
             |</article>
             |<article>
             |  <h2>${_escape(_title_label(dataPath))} detail</h2>
             |  ${_admin_data_record_table(subsystem, componentPath, dataPath, id)}
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataEdit(
    subsystem: Subsystem,
    componentName: String,
    dataName: String,
    id: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val actionPath = s"/form/${componentPath}/admin/data/${dataPath}/${id}/update"
      val fields = _admin_data_record_fields(subsystem, componentPath, dataPath, id).getOrElse(Vector("id" -> id))
      val controls = fields.map {
        case (key, value) =>
          s"""<div class="mb-3">
             |  <label class="form-label" for="data-field-${_escape(key)}">${_escape(key)}</label>
             |  <input class="form-control" id="data-field-${_escape(key)}" name="${_escape(key)}" value="${_escape(value)}">
             |</div>""".stripMargin
      }.mkString("\n")
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data Edit",
        subtitle = "Data record edit baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="${_escape(webBasePath)}/${_escape(id)}">Detail</a> · <a href="${_escape(webBasePath)}">Back to ${_escape(_title_label(dataPath))} records</a></p>
             |</article>
             |<article>
             |  <h2>Edit ${_escape(_title_label(dataPath))}</h2>
             |  <form method="post" action="${_escape(actionPath)}">
             |    ${controls}
             |    <button type="submit" class="btn btn-primary">Update</button>
             |    <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}/${_escape(id)}">Cancel</a>
             |  </form>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataNew(
    subsystem: Subsystem,
    componentName: String,
    dataName: String
  ): Option[Page] =
    _find_component(subsystem, componentName).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val dataPath = NamingConventions.toNormalizedSegment(dataName)
      val webBasePath = s"/web/${componentPath}/admin/data/${dataPath}"
      val actionPath = s"/form/${componentPath}/admin/data/${dataPath}/create"
      Page(_simple_page(
        title = s"${_escape(component.name)} ${_escape(_title_label(dataPath))} Data New",
        subtitle = "Data record create baseline",
        body =
          s"""<article>
             |  <h2>Navigation</h2>
             |  <p><a href="${_escape(webBasePath)}">Back to ${_escape(_title_label(dataPath))} records</a> · <a href="/web/${componentPath}/admin/data">Data CRUD</a></p>
             |</article>
             |<article>
             |  <h2>New ${_escape(_title_label(dataPath))}</h2>
             |  <form method="post" action="${_escape(actionPath)}">
             |    <div class="mb-3">
             |      <label class="form-label" for="dataFields">Fields</label>
             |      <textarea class="form-control" id="dataFields" name="fields" rows="8" placeholder="id=record-1&#10;status=draft"></textarea>
             |      <div class="form-text">Use one name=value pair per line.</div>
             |    </div>
             |    <button type="submit" class="btn btn-primary">Create</button>
             |    <a class="btn btn-outline-secondary" href="${_escape(webBasePath)}">Cancel</a>
             |  </form>
             |</article>""".stripMargin
      ))
    }

  def renderComponentAdminDataUpdateResult(
    componentName: String,
    dataName: String,
    id: String,
    values: Map[String, String],
    applied: Boolean,
    message: String
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
           |    <thead><tr><th>Applied</th><td>${applied}</td></tr></thead>
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
    message: String
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
           |    <thead><tr><th>Applied</th><td>${applied}</td></tr></thead>
           |    <tbody>${_submitted_fields_rows(values)}</tbody>
           |  </table></div>
           |</article>""".stripMargin
    ))
  }

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

  def renderSystemManual(subsystem: Subsystem): Page =
    Page(_simple_page(
      title = "System Manual",
      subtitle = "Read-only reference",
      body =
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/dashboard">System dashboard</a> · <a href="/web/system/admin">Admin configuration</a> · <a href="/web/system/performance">Performance details</a> · <a href="/web/console">Console</a></p>
           |</article>
           |<article>
           |  <h2>Components</h2>
           |  ${_component_reference_list(subsystem.components)}
           |</article>
           |<article>
           |  <h2>Console handoff</h2>
           |  <p>Use <a href="/web/console">System Console</a> for controlled operation entry. Manual pages remain read-only and do not inline operation actions.</p>
           |</article>""".stripMargin
    ))

  def renderSystemConsole(subsystem: Subsystem): Page =
    Page(_simple_page(
      title = "System Console",
      subtitle = "Controlled operation entry",
      body =
        s"""<article>
           |  <h2>Navigation</h2>
           |  <p><a href="/web/system/dashboard">System dashboard</a> · <a href="/web/system/admin">Admin configuration</a> · <a href="/web/system/performance">Performance details</a> · <a href="/web/manual">Manual</a></p>
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
       |      <div class="col-12 col-lg-4"><article id="healthPanel" class="card h-100 border-success"><div class="card-body"><h2 class="h5 card-title">Health</h2><div class="big" id="healthText">UP</div><p class="text-secondary mb-0" id="healthNote">Starting</p></div></article></div>
       |      <div class="col-12 col-lg-4"><article class="card h-100"><div class="card-body"><h2 class="h5 card-title">Subsystem</h2><p class="mb-1"><strong id="subsystemName">-</strong></p><p class="text-secondary mb-0" id="subsystemVersion">-</p></div></article></div>
       |      <div class="col-12 col-lg-4"><article class="card h-100"><div class="card-body"><h2 class="h5 card-title">CNCF</h2><p class="mb-1"><strong id="cncfVersion">-</strong></p><p class="mb-0"><a id="detailsLink" href="/web/system/admin">Admin details</a> · <a id="performanceLink" href="/web/system/performance">Performance details</a> · <a id="manualLink" href="/web/manual">Manual</a> · <a id="consoleLink" href="/web/console">Console</a></p></div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100"><div class="card-body"><span class="text-secondary">Components</span><strong id="componentCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100"><div class="card-body"><span class="text-secondary">Services</span><strong id="serviceCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100"><div class="card-body"><span class="text-secondary">Operations</span><strong id="operationCount">0</strong></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100"><div class="card-body"><span class="text-secondary">HTML requests</span><strong id="requestCount">0</strong><small class="text-secondary" id="requestErrors">errors 0</small></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100"><div class="card-body"><span class="text-secondary">Jobs</span><strong id="jobCount">0</strong><small class="text-secondary" id="jobErrors">errors 0</small></div></div></div>
       |      <div class="col-12 col-sm-6 col-xl"><div class="card metric h-100"><div class="card-body"><span class="text-secondary">Assembly warnings</span><strong id="assemblyWarningCount">0</strong><small><a id="assemblyWarningsLink" href="/form/admin/assembly/warnings">details</a></small></div></div></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-xl-6"><article class="card h-100"><div class="card-body">
       |        <h2 class="h5 card-title">Traffic</h2>
       |        <div class="btn-group mb-3" id="graphTabs" role="group">
       |          <button type="button" class="btn btn-outline-primary btn-sm" data-window="minute">1 minute</button>
       |          <button type="button" class="btn btn-primary btn-sm active" data-window="hour">1 hour</button>
       |          <button type="button" class="btn btn-outline-primary btn-sm" data-window="day">1 day</button>
       |        </div>
       |        <div class="spark" id="requestSpark"></div>
       |      </div></article></div>
       |      <div class="col-12 col-xl-6"><article class="card h-100"><div class="card-body">
       |        <h2 class="h5 card-title">Activity counts</h2>
       |        <div id="activityCounts"></div>
       |      </div></article></div>
       |    </section>
       |    <section class="row g-3 mb-3">
       |      <div class="col-12 col-xl-6"><article class="card h-100"><div class="card-body">
       |        <h2 class="h5 card-title">ActionCall jobs</h2>
       |        <div class="bars" id="jobBars"></div>
       |      </div></article></div>
       |      <div class="col-12 col-xl-6"><article class="card h-100"><div class="card-body">
       |        <h2 class="h5 card-title">Components</h2>
       |        <div class="bars" id="componentBars"></div>
       |      </div></article></div>
       |    </section>
       |    <article class="card"><div class="card-body">
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
       |      return `<table class="table table-sm"><thead><tr><th>Level</th><th>Total</th><th>Total err</th><th>1d</th><th>1d err</th><th>1h</th><th>1h err</th><th>1m</th><th>1m err</th></tr></thead><tbody>$${cells}</tbody></table>`;
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
           |  <p><a href="${_escape(dashboardPath)}">Dashboard</a> · <a href="${_escape(performancePath)}">Performance details</a> · <a href="/web/manual">Manual</a> · <a href="/web/console">Console</a></p>
           |</article>
           |${_admin_operational_details(operationalDetails)}
           |${_component_admin_actions(componentFormsPath)}
           |<article>
           |  <h2>Web Descriptor</h2>
           |  ${_web_descriptor_summary(webDescriptor)}
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
         |  ${_runtime_configuration_table(config)}
         |</article>""".stripMargin
    }.getOrElse("")

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
    descriptor: WebDescriptor
  ): String =
    s"""<div class="table-responsive"><table class="table table-sm">
       |  <tbody>
       |    <tr><th>Status</th><td>${if (descriptor.hasControls) "configured" else "default"}</td></tr>
       |    <tr><th>Auth mode</th><td>${_escape(descriptor.auth.mode)}</td></tr>
       |    <tr><th>Exposure entries</th><td>${descriptor.expose.size}</td></tr>
       |    <tr><th>Authorization entries</th><td>${descriptor.authorization.size}</td></tr>
       |    <tr><th>Form entries</th><td>${descriptor.form.size}</td></tr>
       |    <tr><th>App entries</th><td>${descriptor.apps.size}</td></tr>
       |    <tr><th>Admin entries</th><td>${descriptor.admin.size}</td></tr>
       |  </tbody>
       |</table></div>
       |<p><a href="/web/system/admin/descriptor">Resolved descriptor JSON</a></p>
       |${_web_descriptor_app_list(descriptor)}
       |${_web_descriptor_exposure_list(descriptor)}""".stripMargin

  private def _web_descriptor_json(
    descriptor: WebDescriptor
  ): String =
    Json.obj(
      "status" -> Json.fromString(if (descriptor.hasControls) "configured" else "default"),
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
              "totalCount" -> Json.fromString(admin.totalCount.name)
            )
        }
      ),
      "apps" -> Json.arr(
        descriptor.apps.map { app =>
          Json.obj(
            "name" -> Json.fromString(app.name),
            "path" -> Json.fromString(app.path),
            "kind" -> Json.fromString(app.kind)
          )
        }*
      )
    ).spaces2

  private def _web_descriptor_app_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.apps.isEmpty) {
      "<p>Using built-in Web HTML app defaults.</p>"
    } else {
      val rows = descriptor.apps.map { app =>
        s"""<tr><td>${_escape(app.name)}</td><td><code>${_escape(app.path)}</code></td><td>${_escape(app.kind)}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Apps</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Name</th><th>Path</th><th>Kind</th></tr></thead>
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
           |  <p><a href="/web/system/dashboard">System dashboard</a> · <a href="/web/system/admin">Admin configuration</a> · <a href="/web/manual">Manual</a> · <a href="/web/console">Console</a></p>
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
    properties: FormPageProperties
  ): String = {
    val widgets = _render_widgets(template, properties)
    _render_property_expansions(widgets, properties)
  }

  private def _render_property_expansions(
    template: String,
    properties: FormPageProperties
  ): String =
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(_escape(properties.value(m.group(1))))
    )

  private def _render_widgets(
    template: String,
    properties: FormPageProperties
  ): String = {
    val resultView = """<textus-result-view\s+source="([^"]+)"\s*></textus-result-view>""".r
    val resultTable = """<textus-result-table\s+source="([^"]+)"\s+page="([^"]+)"\s+page-size="([^"]+)"\s+total="([^"]+)"\s+href="([^"]+)"\s*></textus-result-table>""".r
    val propertyList = """<textus-property-list\s+source="([^"]+)"\s*></textus-property-list>""".r
    val errorPanel = """<textus-error-panel\s+source="([^"]+)"\s*></textus-error-panel>""".r
    val a = resultView.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(_render_result_view(m.group(1), properties))
    )
    val b = resultTable.replaceAllIn(a, m =>
      java.util.regex.Matcher.quoteReplacement(_render_result_table(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), properties))
    )
    val c = propertyList.replaceAllIn(b, m =>
      java.util.regex.Matcher.quoteReplacement(_render_property_list(m.group(1), properties))
    )
    errorPanel.replaceAllIn(c, m =>
      java.util.regex.Matcher.quoteReplacement(_render_error_panel(m.group(1), properties))
    )
  }

  private def _render_result_view(
    source: String,
    properties: FormPageProperties
  ): String = {
    val value = properties.value(source)
    s"""<pre class="mt-3 p-3 bg-light border rounded">${_escape(value)}</pre>"""
  }

  private def _render_result_table(
    source: String,
    pagePath: String,
    pageSizePath: String,
    totalPath: String,
    hrefPath: String,
    properties: FormPageProperties
  ): String = {
    val page = _int_property(properties, pagePath, 1)
    val pageSize = _int_property(properties, pageSizePath, 20)
    val total = _optional_int_property(properties, totalPath)
    val href = properties.value(hrefPath)
    val table = _json_table(properties.value(source), page, pageSize).getOrElse("")
    s"""${table}<div class="mt-3">${_paging_nav(page, pageSize, total, href)}</div>"""
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
    body: String,
    page: Int,
    pageSize: Int
  ): Option[String] =
    parse(body).toOption.flatMap { json =>
      val rows = json.asArray.orElse(json.hcursor.downField("result").focus.flatMap(_.asArray))
      rows.flatMap(xs => _records_table(_page_rows(xs, page, pageSize)))
    }

  private def _page_rows(
    rows: Vector[Json],
    page: Int,
    pageSize: Int
  ): Vector[Json] = {
    val offset = math.max(0, page - 1) * math.max(1, pageSize)
    rows.slice(offset, offset + math.max(1, pageSize))
  }

  private def _records_table(rows: Vector[Json]): Option[String] = {
    val objects = rows.flatMap(_.asObject)
    if (objects.isEmpty) {
      None
    } else {
      val headers = objects.flatMap(_.keys).distinct
      val head = headers.map(h => s"<th>${_escape(h)}</th>").mkString
      val body = objects.map { obj =>
        val cells = headers.map { h =>
          val value = obj(h).map(_json_cell).getOrElse("")
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
    s"""{"scope":"${_json(scope)}","name":"${_json(name)}","version":${version.map(v => "\"" + _json(v) + "\"").getOrElse("null")},"observedAt":"${java.time.Instant.now.toString}","status":"UP","cncf":{"version":"${_json(CncfVersion.current)}"},"subsystem":{"name":"${_json(subsystemName)}","version":${subsystemVersion.map(v => "\"" + _json(v) + "\"").getOrElse("null")}},"componentCount":${components.size},"serviceCount":${serviceCount},"operationCount":${operationCount},"actions":{"actionCalls":${_snapshot_json(actionCalls, includeRecent = false)},"jobs":${_jobs_json(running, queued, completed, failed)}},"dsl":{"chokepoints":${_snapshot_json(dslChokepoints, includeRecent = false)}},"authorization":{"decisions":${_snapshot_json(authorizationDecisions, includeRecent = false)}},"assembly":{"warnings":{"count":${assemblyWarningCount}}},"html":{"requests":${_snapshot_json(htmlRequests, includeRecent = true, Some(avgMillis))}},"links":{"admin":"${_json(adminPath)}","performance":"/web/system/performance","manual":"/web/manual","console":"/web/console","assemblyWarnings":"/form/admin/assembly/warnings"},"components":${componentJson}}"""
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
    s"""<div class="table-responsive"><table class="table table-sm">
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
    s"""<div class="table-responsive"><table class="table table-sm">
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
    s"""<div class="table-responsive"><table class="table table-sm">
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
      s"<p>${_escape(emptyMessage)}</p>"
    } else {
      val rows = requests.map { x =>
        s"""<tr><td>${_escape(_instant_text(x.observedAt))}</td><td>${_escape(x.method)}</td><td><code>${_escape(x.path)}</code></td><td>${x.status}</td><td>${x.elapsedMillis} ms</td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm">
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
