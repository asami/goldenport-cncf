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
 *  version May. 20, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererSystemAdminPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererJobPart with StaticFormAppRendererObservabilityPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  def renderSubsystemDashboard(subsystem: Subsystem): Page =
    Page(dashboard_shell(
      title = "CNCF Health",
      subtitle = subsystem.name + subsystem.version.map(v => s" ${escape(v)}").getOrElse(""),
      statePath = "/web/system/dashboard/state"
    ))

  def renderComponentDashboard(component: Component): Page =
    renderComponentDashboard(component, NamingConventions.toNormalizedSegment(component.name))

  def renderComponentDashboard(component: Component, componentpath: String): Page =
    Page(dashboard_shell(
      title = s"${escape(component.name)} Dashboard",
      subtitle = "Component health",
      statePath = s"/web/${componentpath}/dashboard/state"
    ))

  def renderDashboardState(
    subsystem: Subsystem,
    componentName: Option[String]
  ): Option[Page] =
    componentName match {
      case Some(name) =>
        find_component(subsystem, name).map(component =>
          Page(component_dashboard_state(component))
        )
      case None =>
        Some(Page(subsystem_dashboard_state(subsystem)))
    }

  def renderSystemAdmin(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(admin_page(
      title = "System Admin Configuration",
      subtitle = "Current CNCF runtime configuration",
      components = subsystem.components,
      subsystemName = subsystem.name,
      subsystemVersion = subsystem.version,
      dashboardPath = "/web/system/dashboard",
      performancePath = "/web/system/performance",
      webDescriptor = webDescriptor,
      runtimeConfiguration = Some(subsystem.configuration),
      operationalDetails = Some(system_admin_operational_details(webDescriptor, subsystem.components)),
      componentFormsPath = None
    ))

  def renderApplicationAdmin(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(simple_page(
      title = "Application Admin",
      subtitle = "Application operator console",
      body =
        s"""${admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "Tags" -> "/web/admin/tags",
             "Associations" -> "/web/admin/associations"
           ))}
           |${application_admin_entry_pages(webDescriptor)}
           |${application_admin_component_cards(subsystem, webDescriptor)}""".stripMargin
    ))

  def renderSystemAdminDescriptor(
    webDescriptor: WebDescriptor = WebDescriptor.empty
  ): Page =
    Page(simple_page(
      title = "System Web Descriptor",
      subtitle = "Management Console descriptor view",
      body =
        s"""${admin_nav_card(Vector("System admin" -> "/web/system/admin", "System dashboard" -> "/web/system/dashboard"))}
           |${web_descriptor_section_nav}
           |${web_descriptor_control_tables(webDescriptor)}
           |${web_descriptor_asset_composition_table(webDescriptor)}
           |${web_descriptor_json_panel(
             "completed-descriptor",
             "Completed Descriptor JSON",
             "The completed view applies framework defaults so the descriptor can be inspected as the runtime sees it.",
             web_descriptor_json(webDescriptor, completed = true)
           )}
           |${web_descriptor_json_panel(
             "configured-descriptor",
             "Configured Descriptor JSON",
             "The configured view keeps explicit descriptor entries for comparison.",
             web_descriptor_json(webDescriptor, completed = false)
           )}""".stripMargin
    ))

  def renderSystemAdminAssemblyWarnings(
    subsystem: Subsystem
  ): Page = {
    val report = assembly_report(subsystem)
    val rows =
      if (report.warnings.isEmpty)
        """<tr><td colspan="6" class="text-secondary">No assembly warnings.</td></tr>"""
      else
        report.warnings.map { warning =>
          s"""<tr>
             |  <td><code>${escape(warning.kind)}</code></td>
             |  <td>${escape(warning.severity)}</td>
             |  <td>${escape(warning.componentName)}</td>
             |  <td>${escape(warning.message)}</td>
             |  <td>${escape(warning.selectedOrigin.getOrElse(""))}</td>
             |  <td>${escape(warning.droppedOrigins.mkString(", "))}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    Page(simple_page(
      title = "Assembly Warnings",
      subtitle = "Runtime assembly diagnostics",
      body =
        s"""${admin_nav_card(Vector("System dashboard" -> "/web/system/dashboard", "System admin" -> "/web/system/admin", "Assembly report" -> "/web/system/admin/assembly/report"))}
           |${admin_card(
             "Warnings",
             s"""<p>${report.warnings.size} warning(s). Assembly warnings are diagnostics, not health errors.</p>
                |${admin_table(
                  Some("<tr><th>Kind</th><th>Severity</th><th>Component</th><th>Message</th><th>Selected</th><th>Dropped</th></tr>"),
                  rows
                )}""".stripMargin
           )}""".stripMargin
    ))
  }

  def renderSystemAdminAssemblyReport(
    subsystem: Subsystem
  ): Page = {
    val record = assembly_report(subsystem).toRecord
    val json = manual_raw_json(record).map(_.spaces2).getOrElse(manual_raw_text(record))
    val yaml = manual_raw_json(record).map(json_to_yaml).getOrElse(manual_raw_text(record))
    Page(simple_page(
      title = "Assembly Report",
      subtitle = "Runtime assembly report",
      body =
        s"""${admin_nav_card(Vector("System dashboard" -> "/web/system/dashboard", "System admin" -> "/web/system/admin", "Assembly warnings" -> "/web/system/admin/assembly/warnings"))}
           |${admin_card("Report", raw_format_tabs(json, yaml, "assembly-report"))}""".stripMargin
    ))
  }


  def renderSystemConsole(subsystem: Subsystem): Page =
    Page(simple_page(
      title = "System Console",
      subtitle = "Controlled operation entry",
      body =
        s"""${admin_nav_card(Vector(
             "System dashboard" -> "/web/system/dashboard",
             "Admin configuration" -> "/web/system/admin",
             "Performance details" -> "/web/system/performance",
             "Documents" -> "/web/system/document"
           ))}
           |${admin_card(
             "Operation forms",
             s"""<p>Console links to operation forms. It does not execute operations inline.</p>
                |${component_form_list(subsystem.components)}""".stripMargin
           )}""".stripMargin
    ))

  protected def find_component(
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

  protected def operation_selector(
    componentName: String,
    serviceName: String,
    operationName: String
  ): String =
    Vector(componentName, serviceName, operationName)
      .map(NamingConventions.toNormalizedSegment)
      .mkString(".")

  protected def dashboard_shell(
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

  protected def subsystem_dashboard_state(subsystem: Subsystem): String =
    dashboard_state_json(subsystem.components, "subsystem", subsystem.name, subsystem.version, job_metrics(subsystem), subsystem.name, subsystem.version, assembly_warning_count(subsystem))

  protected def admin_page(
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
    val profile = webDescriptor.adminProfile
    val descriptorPath = componentFormsPath
      .map(_.stripPrefix("/form/"))
      .map(componentPath => s"/web/${componentPath}/admin/descriptor")
      .getOrElse("/web/system/admin/descriptor")
    val componentBlocks = components.map { component =>
      val services = component.protocol.services.services.map { service =>
        val operations = service.operations.operations.toVector.map { operation =>
          val path = NamingConventions.toNormalizedPath(component.name, service.name, operation.name)
          s"""<li>${escape(operation.name)} <code>${escape(path)}</code></li>"""
        }.mkString("\n")
        s"""<section><h3>${escape(service.name)}</h3><ul>${operations}</ul></section>"""
      }.mkString("\n")
      val version = component.artifactMetadata.map(_.version).getOrElse("unversioned")
      val componentlets = componentlet_table(component)
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityCount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val dataCount = admin_surface_selector_count(webDescriptor, Some(componentPath), "data")
      val aggregateCount = component.aggregateDefinitions.size
      val viewCount = component.viewDefinitions.size
      val formsCount = component.protocol.services.services.map(_.operations.operations.toVector.size).sum
      val componentAdminPages = webDescriptor.adminPagesFor(componentPath)
      val cards = admin_entry_cards(Vector(
        admin_entry_card(
          "Entities",
          s"${pluralize(entityCount, "runtime descriptor", "runtime descriptors")} ready for list/detail/new/edit.",
          s"/web/${componentPath}/admin/entities",
          Some(entityCount.toString)
        ),
        admin_entry_card(
          "Data",
          if (dataCount > 0)
            s"${pluralize(dataCount, "descriptor surface", "descriptor surfaces")} available through data admin."
          else
            "Open descriptor-backed data collections and concrete datastore records.",
          s"/web/${componentPath}/admin/data",
          Some(dataCount.toString)
        ),
        admin_entry_card(
          "Aggregates",
          s"${pluralize(aggregateCount, "aggregate", "aggregates")} available for read/list drill-down.",
          s"/web/${componentPath}/admin/aggregates",
          Some(aggregateCount.toString)
        ),
        admin_entry_card(
          "Views",
          s"${pluralize(viewCount, "view definition", "view definitions")} available for read-only inspection.",
          s"/web/${componentPath}/admin/views",
          Some(viewCount.toString)
        ),
        admin_entry_card(
          "Descriptor",
          "Inspect routes, forms, auth controls, and admin surfaces.",
          s"/web/${componentPath}/admin/descriptor"
        ),
        admin_entry_card(
          "Forms",
          s"${pluralize(formsCount, "operation form", "operation forms")} available for controlled execution.",
          s"/form/${componentPath}",
          Some(formsCount.toString)
        )
      ))
      val componentOwnedAdminPages = component_owned_admin_pages(componentAdminPages)
      val technicalDetails =
        s"""<details class="mt-3">
           |  <summary>Technical details</summary>
           |  <div class="mt-3">
           |    <h3>${escape(component.name)}</h3>
           |    <p class="text-body-secondary">Version ${escape(version)}</p>
           |    ${componentlets}
           |    ${services}
           |  </div>
           |</details>""".stripMargin
      admin_card(
        component.name,
        s"""<p class="text-body-secondary">Version ${escape(version)}</p>
           |${cards}
           |${componentOwnedAdminPages}
           |${technicalDetails}""".stripMargin
      )
    }.mkString("\n")
    val componentInventory =
      if (componentFormsPath.isEmpty)
        system_admin_component_inventory(components)
      else
        ""
    val runtimeRows =
      s"""<tr><th>CNCF version</th><td>${escape(CncfVersion.current)}</td></tr>
         |<tr><th>Subsystem</th><td>${escape(subsystemName)}</td></tr>
         |<tr><th>Subsystem version</th><td>${escape(subsystemVersion.getOrElse("unversioned"))}</td></tr>
         |<tr><th>Components</th><td>${components.size}</td></tr>""".stripMargin
    val runtimeCard =
      admin_card(
        "Runtime",
        admin_table(None, runtimeRows, tableClass = "table table-sm align-middle mb-0")
      )
    val primaryNav = Vector(
      "Application admin" -> "/web/admin",
      "Dashboard" -> dashboardPath,
      "Performance details" -> performancePath,
      "Observability" -> "/web/system/admin/observability",
      "Documents" -> "/web/system/document",
      "Console" -> "/web/console"
    )
    simple_page(
      title = title,
      subtitle = subtitle,
      body =
        s"""<section data-textus-page="generated-admin" data-textus-section="admin-root"${ux_profile_attr(profile)}>
           |${runtimeCard}
           |${admin_nav_card(primaryNav)}
           |${componentInventory}
           |${admin_operational_details(operationalDetails)}
           |${component_admin_actions(componentFormsPath)}
           |${admin_card("Web Descriptor", web_descriptor_summary(webDescriptor, descriptorPath))}
           |${admin_runtime_configuration(runtimeConfiguration)}
           |${admin_job_control(runtimeConfiguration)}
           |${componentBlocks}
           |</section>""".stripMargin
    )
  }

  protected def application_admin_entry_pages(
    webDescriptor: WebDescriptor
  ): String = {
    val pages = webDescriptor.adminPagesForAudience(WebDescriptor.AdminAudience.Application)
    if (pages.isEmpty)
      admin_card("Application Admin Pages", admin_empty_state("No descriptor-declared application admin pages are available."))
    else
      admin_card("Application Admin Pages", component_owned_admin_pages_list(pages))
  }

  protected def application_admin_component_cards(
    subsystem: Subsystem,
    webDescriptor: WebDescriptor
  ): String = {
    val cards = subsystem.components.toVector.flatMap { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      val entityCount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val dataCount = admin_surface_selector_count(webDescriptor, Some(componentPath), "data")
      val aggregateCount = component.aggregateDefinitions.size
      val viewCount = component.viewDefinitions.size
      val entries = Vector(
        Option.when(entityCount > 0)(admin_entry_card(
          "Entities",
          s"${pluralize(entityCount, "runtime descriptor", "runtime descriptors")} available for application operations.",
          s"/web/${componentPath}/admin/entities",
          Some(entityCount.toString)
        )),
        Option.when(dataCount > 0)(admin_entry_card(
          "Data",
          s"${pluralize(dataCount, "descriptor surface", "descriptor surfaces")} available for application operations.",
          s"/web/${componentPath}/admin/data",
          Some(dataCount.toString)
        )),
        Option.when(aggregateCount > 0)(admin_entry_card(
          "Aggregates",
          s"${pluralize(aggregateCount, "aggregate", "aggregates")} available for application operations.",
          s"/web/${componentPath}/admin/aggregates",
          Some(aggregateCount.toString)
        )),
        Option.when(viewCount > 0)(admin_entry_card(
          "Views",
          s"${pluralize(viewCount, "view definition", "view definitions")} available for application operations.",
          s"/web/${componentPath}/admin/views",
          Some(viewCount.toString)
        ))
      ).flatten
      Option.when(entries.nonEmpty) {
        admin_card(
          component.name,
          s"""<p class="text-body-secondary">Application-facing generic admin surfaces.</p>
             |${admin_entry_cards(entries)}
             |${admin_action_row(Vector("Component admin" -> s"/web/${componentPath}/admin"), primary = false)}""".stripMargin
        )
      }
    }
    if (cards.isEmpty)
      admin_card("Component Admin Surfaces", admin_empty_state("No application-facing component admin surfaces are available."))
    else
      cards.mkString("\n")
  }

  protected def component_owned_admin_pages(
    pages: Vector[WebDescriptor.AdminPage]
  ): String =
    if (pages.isEmpty)
      ""
    else {
      s"""<section class="mt-3">
         |  <h3 class="h6">Component Admin Pages</h3>
         |  ${component_owned_admin_pages_list(pages)}
         |</section>""".stripMargin
    }

  protected def component_owned_admin_pages_list(
    pages: Vector[WebDescriptor.AdminPage]
  ): String = {
    val items = pages.map { page =>
      val description = Option(page.description).map(_.trim).filter(_.nonEmpty)
        .map(value => s"""<p class="mb-2 text-body-secondary">${escape(value)}</p>""")
        .getOrElse("")
      s"""<a class="list-group-item list-group-item-action" href="${escape(page.href)}">
         |  <div class="d-flex justify-content-between align-items-start gap-3">
         |    <div>
         |      <div class="fw-semibold">${escape(page.effectiveLabel)}</div>
         |      ${description}
         |      <code>${escape(page.href)}</code>
         |    </div>
         |    <span class="badge text-bg-secondary">${escape(page.effectivePermission)}</span>
         |  </div>
         |</a>""".stripMargin
    }
    s"""<div class="list-group">${items.mkString("\n")}</div>"""
  }

  protected def admin_runtime_configuration(
    configuration: Option[ResolvedConfiguration]
  ): String =
    configuration.map { config =>
      admin_card(
        "Runtime Configuration",
        s"""<p>Resolved runtime configuration values are read-only. Sensitive values are masked.</p>
           |<p>Configuration mutation must use a separate admin action surface with explicit admin authorization and audit logging.</p>
           |${effective_runtime_configuration_table(config)}
           |${runtime_configuration_table(config)}""".stripMargin
      )
    }.getOrElse("")

  protected def effective_runtime_configuration_table(
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
        s"""<tr><td><code>${escape(key)}</code></td><td>${escape(value)}</td></tr>"""
    }
    s"""<h3>Effective Runtime Policy</h3>
       |<div class="table-responsive"><table class="table table-sm">
       |  <thead><tr><th>Key</th><th>Value</th></tr></thead>
       |  <tbody>${rows.mkString("\n")}</tbody>
       |</table></div>""".stripMargin
  }

  protected def runtime_configuration_table(
    config: ResolvedConfiguration
  ): String = {
    val rows = config.configuration.values.toVector
      .filter { case (key, _) => is_runtime_configuration_key(key) }
      .sortBy(_._1)
      .map {
        case (key, value) =>
          val sensitive = is_sensitive_configuration_key(key)
          val rendered = if (sensitive) "********" else configuration_value_text(value)
          val visibility = if (sensitive) "masked" else "visible"
          s"""<tr><td><code>${escape(key)}</code></td><td>${escape(rendered)}</td><td>${visibility}</td></tr>"""
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

  protected def is_runtime_configuration_key(key: String): Boolean =
    key.startsWith("textus.") || key.startsWith("cncf.")

  protected def is_sensitive_configuration_key(key: String): Boolean = {
    val normalized = key.toLowerCase
    Vector("password", "passwd", "secret", "token", "credential", "apikey", "api-key", "private-key")
      .exists(normalized.contains)
  }

  protected def configuration_value_text(
    value: ConfigurationValue
  ): String =
    value match {
      case ConfigurationValue.StringValue(v) => v
      case ConfigurationValue.NumberValue(v) => v.toString
      case ConfigurationValue.BooleanValue(v) => v.toString
      case ConfigurationValue.ListValue(vs) => vs.map(configuration_value_text).mkString("[", ", ", "]")
      case ConfigurationValue.ObjectValue(vs) =>
        vs.toVector.sortBy(_._1).map {
          case (key, value) => s"${key}: ${configuration_value_text(value)}"
        }.mkString("{", ", ", "}")
      case ConfigurationValue.NullValue => "null"
    }

  protected def admin_operational_details(
    html: Option[String]
  ): String =
    html.map { body =>
      admin_card("Operational Details", body)
    }.getOrElse("")

  protected def system_admin_operational_details(
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
           |  ${component_owned_admin_pages_list(systemPages)}
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
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/information">InformationSpace</a>
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/knowledge">KnowledgeSpace</a>
      |        <a class="list-group-item list-group-item-action" href="/web/system/admin/observability">Observability diagnostics</a>
      |        <a class="list-group-item list-group-item-action" href="/form/admin/execution/history">Execution history</a>
      |        <a class="list-group-item list-group-item-action" href="/form/admin/execution/calltree">Latest calltree</a>
      |      </div>
      |    </section>
      |  </div>
      |</div>
      |${systemPageLinks}
      |${component_dev_dir_diagnostics(components)}""".stripMargin
  }

  protected def component_dev_dir_diagnostics(
    components: Vector[Component]
  ): String = {
    val rows = components.flatMap { component =>
      component.artifactMetadata.toVector.filter(_.sourceType == "component-dev-dir").map { metadata =>
        val base = metadata.archivePath.map(p => Paths.get(p).toAbsolutePath.normalize)
        val classpath = base.map(_.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"))
        val webRoots = base.toVector.flatMap { path =>
          Vector(path.resolve("car.d").resolve("web"), path.resolve("src").resolve("main").resolve("web"), path.resolve("web"))
            .filter(Files.isDirectory(_))
        }.map(path => escape(path.toString)).mkString("<br>")
        s"""<tr>
           |  <td><code>${escape(component.name)}</code></td>
           |  <td><code>${escape(base.map(_.toString).getOrElse(""))}</code></td>
           |  <td><code>${escape(classpath.map(_.toString).getOrElse(""))}</code></td>
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

  protected def componentlet_table(
    component: Component
  ): String = {
    val rows = component.componentDescriptors
      .flatMap(_.componentlets)
      .sortBy(_.name)
      .map { componentlet =>
        s"""<tr>
           |  <td><code>${escape(componentlet.name)}</code></td>
           |  <td>${escape(componentlet.kind.getOrElse("componentlet"))}</td>
           |  <td>${escape(componentlet.archiveScope.getOrElse(""))}</td>
           |  <td>${escape(componentlet.implementationClass.getOrElse(""))}</td>
           |  <td>${escape(componentlet.factoryObject.getOrElse(""))}</td>
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

  protected def manual_componentlet_section(
    component: Component
  ): String = {
    val body = componentlet_table(component)
    if (body.isEmpty)
      ""
    else
      manual_card("Componentlets", body, Some("componentlets"))
  }

  protected def component_admin_actions(
    formsPath: Option[String]
  ): String =
    formsPath.map { path =>
      val componentPath = path.stripPrefix("/form/")
      s"""<section class="admin-section">
         |  <h2 class="h5">Component Admin</h2>
         |  <p>Use Application Admin for ordinary operator workflows. This component page remains available for component-specific drill-down and technical reference.</p>
         |  ${admin_action_row(Vector("Application admin" -> "/web/admin"), primary = false)}
         |  ${component_admin_management_cards(componentPath, path)}
         |  <details class="mt-3">
         |    <summary>Technical details</summary>
         |    <div class="mt-3">
         |      ${admin_action_row(Vector("Operation forms" -> path), primary = false)}
         |      ${component_admin_management_links(path)}
         |    </div>
         |  </details>
         |</section>""".stripMargin
    }.getOrElse("")

  protected def component_admin_management_cards(
    componentPath: String,
    formsPath: String
  ): String =
    admin_entry_cards(Vector(
      admin_entry_card("Entities", "List, detail, create, and update entity records.", s"/web/${componentPath}/admin/entities"),
      admin_entry_card("Data", "Manage concrete data collections and datastore records.", s"/web/${componentPath}/admin/data"),
      admin_entry_card("Aggregates", "Inspect aggregate records and aggregate-level operations.", s"/web/${componentPath}/admin/aggregates"),
      admin_entry_card("Views", "Browse read-only view projections and instance detail.", s"/web/${componentPath}/admin/views"),
      admin_entry_card("Tags", "Browse TagSpaces and search tagged Entities.", "/web/admin/tags"),
      admin_entry_card("Descriptor", "Inspect descriptor controls and admin-surface mappings.", s"/web/${componentPath}/admin/descriptor"),
      admin_entry_card("Forms", "Open controlled operation forms outside the admin CRUD surfaces.", formsPath)
    ))

  protected def component_admin_management_links(
    formsPath: String
  ): String = {
    val componentPath = formsPath.stripPrefix("/form/")
    s"""<h3 class="h6">Managed Data</h3>
       |${admin_link_list_group(Vector(
         "Entity CRUD" -> s"/web/${componentPath}/admin/entities",
         "Data CRUD" -> s"/web/${componentPath}/admin/data",
         "Aggregate CRUD" -> s"/web/${componentPath}/admin/aggregates",
         "View read" -> s"/web/${componentPath}/admin/views",
         "Tags" -> "/web/admin/tags"
       ))}""".stripMargin
  }

  protected def web_descriptor_summary(
    descriptor: WebDescriptor,
    descriptorPath: String
  ): String =
    s"""<div class="table-responsive"><table class="table table-sm">
       |  <tbody>
       |    <tr><th>Status</th><td>${if (descriptor.hasControls) "configured" else "default"}</td></tr>
       |    <tr><th>Auth mode</th><td>${escape(descriptor.auth.mode)}</td></tr>
       |    <tr><th>Exposure entries</th><td>${descriptor.expose.size}</td></tr>
       |    <tr><th>Authorization entries</th><td>${descriptor.authorization.size}</td></tr>
       |    <tr><th>Form entries</th><td>${descriptor.form.size}</td></tr>
       |    <tr><th>App entries</th><td>${descriptor.apps.size}</td></tr>
       |    <tr><th>Route entries</th><td>${descriptor.routes.size}</td></tr>
       |    <tr><th>Admin entries</th><td>${descriptor.admin.size}</td></tr>
       |  </tbody>
       |</table></div>
       |${admin_action_row(Vector("Completed descriptor JSON" -> descriptorPath), primary = false)}
       |${web_descriptor_app_list(descriptor)}
       |${web_descriptor_route_list(descriptor)}
       |${web_descriptor_exposure_list(descriptor)}
       |${web_descriptor_admin_list(descriptor)}""".stripMargin

  protected def web_descriptor_admin_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.admin.isEmpty) {
      "<p>No Management Console controls are configured.</p>"
    } else {
      val rows = descriptor.admin.toVector.sortBy(_._1).map {
        case (selector, admin) =>
          val fields = admin.fields.map(_.name).mkString(", ")
          val kind = admin_surface_kind(selector).getOrElse("deferred")
          val destination = web_descriptor_admin_surface_destination(selector, None)
          s"""<tr><td>${destination}</td><td>${escape(kind)}</td><td>${escape(admin.totalCount.name)}</td><td>${escape(fields)}</td><td><code>${escape(selector)}</code></td></tr>"""
      }.mkString("\n")
      s"""<h3>Management Console Controls</h3>
         |<div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Destination</th><th>Type</th><th>Total count</th><th>Fields</th><th>Raw selector</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def web_descriptor_json(
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
      "assets" -> web_descriptor_assets_json(descriptor.assets),
      "theme" -> web_descriptor_theme_json(descriptor.theme),
      "assetComposition" -> (
        if (completed)
          web_descriptor_asset_composition_json(descriptor, componentSegment)
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
              "assets" -> web_descriptor_assets_json(form.assets)
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
                case (name, control) => name -> web_descriptor_form_control_json(control)
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
          web_descriptor_app_json(if (completed) app.completedFor(componentSegment) else app)
        }*
      ),
      "routes" -> Json.arr(
        descriptor.routes.map(web_descriptor_route_json)*
      )
    ).spaces2

  protected def web_descriptor_form_control_json(
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

  protected def web_descriptor_assets_json(
    assets: WebDescriptor.Assets
  ): Json =
    Json.obj(
      "autoComplete" -> Json.fromBoolean(assets.autoComplete),
      "css" -> Json.arr(assets.css.map(Json.fromString)*),
      "js" -> Json.arr(assets.js.map(Json.fromString)*)
    )

  protected def web_descriptor_theme_json(
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

  protected def web_descriptor_asset_composition_json(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): Json = {
    val forms = web_descriptor_form_asset_entries(descriptor, componentSegment)
    Json.obj(
      "global" -> web_descriptor_assets_json(descriptor.assets),
      "apps" -> Json.fromFields(
        descriptor.apps.map(app => app.normalizedName -> web_descriptor_assets_json(app.assets))
      ),
      "forms" -> Json.fromFields(
        forms.map {
          case (selector, _, _, _, form) => selector -> web_descriptor_assets_json(form.assets)
        }
      ),
      "resolvedForms" -> Json.fromFields(
        forms.map {
          case (selector, component, service, operation, _) =>
            selector -> Json.obj(
              "component" -> Json.fromString(component),
              "service" -> Json.fromString(service),
              "operation" -> Json.fromString(operation),
              "componentFormIndex" -> web_descriptor_assets_json(descriptor.formIndexAssets(component)),
              "operationInput" -> web_descriptor_assets_json(descriptor.resultAssets(component, service, operation)),
              "operationResult" -> web_descriptor_assets_json(descriptor.resultAssets(component, service, operation))
            )
        }
      )
    )
  }

  protected def web_descriptor_form_asset_entries(
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

  protected def web_descriptor_control_tables(
    descriptor: WebDescriptor,
    componentSegment: Option[String] = None
  ): String = {
    val body =
      s"""<p>Completed apps, routes, form access, authorization, and admin surfaces.</p>
         |${admin_action_row(Vector("Completed JSON" -> "#completed-descriptor"), primary = false)}
         |${web_descriptor_filter_control}
         |${web_descriptor_apps_table(descriptor, componentSegment)}
         |${web_descriptor_routes_table(descriptor, componentSegment)}
         |${web_descriptor_form_controls_table(descriptor, componentSegment)}
         |${web_descriptor_admin_surfaces_table(descriptor, componentSegment)}
         |${web_descriptor_filter_script}""".stripMargin
    admin_card("Descriptor Controls", body, Some("descriptor-controls"))
  }

  protected def web_descriptor_section_nav: String =
    admin_card(
      "Descriptor Sections",
      """<nav class="nav nav-pills flex-column flex-sm-row gap-2 descriptor-section-nav">
        |  <a class="nav-link border" href="#descriptor-controls">Descriptor Controls</a>
        |  <a class="nav-link border" href="#asset-composition">Asset Composition</a>
        |  <a class="nav-link border" href="#completed-descriptor">Completed JSON</a>
        |  <a class="nav-link border" href="#configured-descriptor">Configured JSON</a>
        |</nav>""".stripMargin
    )

  protected def web_descriptor_json_panel(
    id: String,
    title: String,
    description: String,
    json: String
  ): String =
    admin_card(
      title,
      s"""<details class="descriptor-json-details">
         |  <summary class="h5 mb-3">${escape(title)}</summary>
         |  <p>${escape(description)}</p>
         |  ${raw_format_tabs(json, json_to_yaml(json), "descriptor")}
         |</details>""".stripMargin,
      Some(id)
    )

  protected def web_descriptor_filter_control: String =
    """<div class="mb-3">
      |  <label class="form-label" for="web-descriptor-filter">Filter descriptor tables</label>
      |  <input class="form-control" id="web-descriptor-filter" type="search" placeholder="Filter apps, routes, forms, authorization, and admin surfaces" data-textus-descriptor-filter>
      |  <p class="alert alert-warning mt-2 mb-0 d-none" data-textus-descriptor-filter-empty>No descriptor rows match the filter.</p>
      |</div>""".stripMargin

  protected def web_descriptor_filter_script: String =
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

  protected def web_descriptor_apps_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val apps = web_descriptor_scoped_apps(descriptor, componentSegment)
    val rows = apps.map { app =>
      val completed = app.completedFor(componentSegment)
      s"""<tr data-descriptor-row>
         |  <td><code>${escape(completed.name)}</code></td>
         |  <td>${web_descriptor_path_link(completed.effectivePath)}</td>
         |  <td><code>${escape(completed.effectiveRoot)}</code></td>
         |  <td>${web_descriptor_route_link(completed.effectiveRoute)}</td>
         |  <td>${escape(completed.effectiveKind)}</td>
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

  protected def web_descriptor_scoped_apps(
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

  protected def web_descriptor_routes_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val filtered = descriptor.routes.filter(route =>
      componentSegment.forall(component => NamingConventions.equivalentByNormalized(route.target.component, component))
    )
    val rows = filtered.map { route =>
      s"""<tr data-descriptor-row>
         |  <td>${web_descriptor_path_link(route.path)}</td>
         |  <td>${escape(route.kind.name)}</td>
         |  <td><code>${escape(route.target.component)}</code></td>
         |  <td><code>${escape(route.target.app)}</code></td>
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

  protected def web_descriptor_form_controls_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val selectors = (descriptor.expose.keySet ++ descriptor.form.keySet ++ descriptor.authorization.keySet)
      .toVector
      .filter(selector => selector_matches_component(selector, componentSegment))
      .sortBy(identity)
    val rows = selectors.map { selector =>
      val exposure = descriptor.exposureOf(selector).name
      val form = descriptor.form.get(selector)
      val authorization = descriptor.authorization.get(selector)
      s"""<tr data-descriptor-row>
         |  <td>${web_descriptor_form_link(selector)}</td>
         |  <td>${escape(exposure)}</td>
         |  <td>${form.flatMap(_.enabled).map(_.toString).getOrElse("default")}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.roles).getOrElse(Vector.empty)))}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.scopes).getOrElse(Vector.empty)))}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.capabilities).getOrElse(Vector.empty)))}</td>
         |  <td>${authorization.exists(_.allowAnonymous)}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.operationModes.map(_.name)).getOrElse(Vector.empty)))}</td>
         |  <td>${escape(web_descriptor_csv(authorization.map(_.anonymousOperationModes.map(_.name)).getOrElse(Vector.empty)))}</td>
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

  protected def web_descriptor_admin_surfaces_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String]
  ): String = {
    val rows = descriptor.admin.toVector.sortBy(_._1).filter {
      case (selector, _) => admin_selector_matches_component(selector, componentSegment)
    }.map {
      case (selector, admin) =>
        val fields = admin.fields.map { field =>
          val control = field.control.controlType.getOrElse("default")
          val required = field.control.required.map(value => s", required=${value}").getOrElse("")
          s"${field.name}:${control}${required}"
        }
        val kind = admin_surface_kind(selector).getOrElse("deferred")
        val destination = web_descriptor_admin_surface_destination(selector, componentSegment)
        val support =
          if (web_descriptor_admin_surface_path(selector, componentSegment).isDefined) "implemented baseline"
          else "deferred"
        s"""<tr data-descriptor-row>
           |  <td>${destination}</td>
           |  <td>${escape(kind)}</td>
           |  <td>${escape(admin.totalCount.name)}</td>
           |  <td>${escape(web_descriptor_csv(fields))}</td>
           |  <td>${escape(support)}</td>
           |  <td><code>${escape(selector)}</code></td>
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

  protected def selector_matches_component(
    selector: String,
    componentSegment: Option[String]
  ): Boolean =
    componentSegment.forall { component =>
      selector.split("\\.", 2).headOption.exists(head =>
        NamingConventions.equivalentByNormalized(head, component)
      )
    }

  protected def admin_selector_matches_component(
    selector: String,
    componentSegment: Option[String]
  ): Boolean =
    componentSegment.forall { component =>
      selector.split("\\.", 2).toVector match {
        case Vector(surface, _) if admin_surface_path(surface).isDefined =>
          true
        case Vector(head, _) =>
          NamingConventions.equivalentByNormalized(head, component)
        case _ =>
          true
      }
    }

  protected def web_descriptor_csv(
    values: Vector[String]
  ): String =
    if (values.isEmpty)
      "none"
    else
      values.mkString(", ")

  protected def web_descriptor_path_link(
    path: String
  ): String =
    if (path.startsWith("/web"))
      s"""<a href="${escape(path)}"><code>${escape(path)}</code></a>"""
    else
      s"""<code>${escape(path)}</code>"""

  protected def web_descriptor_route_link(
    route: String
  ): String =
    if (route.startsWith("/web") && !route.contains("{"))
      s"""<a href="${escape(route)}"><code>${escape(route)}</code></a>"""
    else
      s"""<code>${escape(route)}</code>"""

  protected def web_descriptor_form_link(
    selector: String
  ): String =
    selector.split("\\.", 3).toVector match {
      case Vector(component, service, operation) =>
        val path = s"/form/${component}/${service}/${operation}"
        s"""<a href="${escape(path)}"><code>${escape(selector)}</code></a>"""
      case _ =>
        s"""<code>${escape(selector)}</code>"""
    }

  protected def web_descriptor_admin_surface_path(
    selector: String,
    componentSegment: Option[String]
  ): Option[String] = {
    def component_from_selector: Option[(String, String)] =
      selector.split("\\.", 3).toVector match {
        case Vector(component, surface, name) => Some(component -> s"${surface}.${name}")
        case _ => None
      }
    component_from_selector.flatMap {
      case (component, rest) => admin_surface_relative_path(rest).map(path => s"/web/${component}/admin/${path}")
    }.orElse(
      componentSegment.flatMap(component =>
        admin_surface_relative_path(selector).map(path => s"/web/${component}/admin/${path}")
      )
    )
  }

  protected def web_descriptor_admin_surface_destination(
    selector: String,
    componentSegment: Option[String]
  ): String = {
    val maybePath =
      web_descriptor_admin_surface_path(selector, componentSegment)
    maybePath match {
      case Some(path) => s"""<a href="${escape(path)}"><code>${escape(path)}</code></a>"""
      case None => """<span class="text-secondary">Deferred or unsupported</span>"""
    }
  }

  protected def web_descriptor_admin_surface_link(
    selector: String,
    componentSegment: Option[String]
  ): String = {
    val maybePath =
      web_descriptor_admin_surface_path(selector, componentSegment)
    maybePath match {
      case Some(path) => s"""<a href="${escape(path)}"><code>${escape(selector)}</code></a>"""
      case None => s"""<code>${escape(selector)}</code>"""
    }
  }

  protected def admin_surface_kind(
    selector: String
  ): Option[String] =
    (selector.split("\\.", 3).toVector match {
      case Vector(surface, _) =>
        admin_surface_path(surface)
      case Vector(_, surface, _) =>
        admin_surface_path(surface)
      case _ =>
        None
    }).map {
      case "entities" => "entity"
      case "data" => "data"
      case "aggregates" => "aggregate"
      case "views" => "view"
      case other => other
    }

  protected def admin_surface_selector_count(
    descriptor: WebDescriptor,
    componentSegment: Option[String],
    kind: String
  ): Int =
    descriptor.admin.keys.count(selector =>
      admin_selector_matches_component(selector, componentSegment) &&
      admin_surface_kind(selector).contains(kind)
    )

  protected def pluralize(
    n: Int,
    singular: String,
    plural: String
  ): String =
    if (n == 1) s"1 ${singular}" else s"${n} ${plural}"

  protected def admin_entry_card(
    title: String,
    description: String,
    href: String,
    badge: Option[String] = None
  ): String =
    s"""<div class="col-12 col-md-6 col-xl-4">
       |  <article class="card h-100 shadow-sm admin-card">
       |    <div class="card-body">
       |      <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
       |        <h3 class="h5 card-title mb-0">${escape(title)}</h3>
       |        ${badge.map(v => s"""<span class="badge text-bg-secondary">${escape(v)}</span>""").getOrElse("")}
       |      </div>
       |      <p class="card-text text-body-secondary">${escape(description)}</p>
       |      <a class="btn btn-primary btn-sm" href="${escape(href)}">Open ${escape(title)}</a>
       |    </div>
       |  </article>
       |</div>""".stripMargin

  protected def admin_entry_cards(
    cards: Vector[String]
  ): String =
    s"""<div class="row g-3">${cards.mkString("\n")}</div>"""

  protected def system_admin_component_inventory(
    components: Vector[Component]
  ): String = {
    val rows = components.sortBy(_.name).map { component =>
      val componentPath = NamingConventions.toNormalizedSegment(component.name)
      s"""<tr>
         |  <td>${escape(component.name)}</td>
         |  <td><a href="/web/${componentPath}/admin">Component admin</a></td>
         |  <td><a href="/web/${componentPath}/admin/descriptor">Descriptor</a></td>
         |  <td><a href="/form/${componentPath}">Forms</a></td>
         |</tr>""".stripMargin
    }.mkString("\n")
    admin_card(
      "Component Management Console",
      s"""<p>Use component admin pages for ordinary entity/data/view/aggregate work. System admin stays read-only.</p>
         |${admin_table(
           Some("<tr><th>Component</th><th>Admin</th><th>Descriptor</th><th>Forms</th></tr>"),
           rows
         )}""".stripMargin
    )
  }

  protected def admin_surface_relative_path(
    selector: String
  ): Option[String] =
    selector.split("\\.", 2).toVector match {
      case Vector(surface, name) =>
        admin_surface_path(surface).map(path => s"${path}/${NamingConventions.toNormalizedSegment(name)}")
      case _ =>
        None
    }

  protected def admin_surface_path(
    surface: String
  ): Option[String] =
    NamingConventions.toNormalizedSegment(surface) match {
      case "entity" | "entities" => Some("entities")
      case "data" => Some("data")
      case "aggregate" | "aggregates" => Some("aggregates")
      case "view" | "views" => Some("views")
      case _ => None
    }

  protected def web_descriptor_asset_composition_table(
    descriptor: WebDescriptor,
    componentSegment: Option[String] = None
  ): String = {
    val forms = web_descriptor_form_asset_entries(descriptor, componentSegment)
    val scopeRows =
      Vector(web_descriptor_asset_scope_row("global", "web.assets", descriptor.assets)) ++
        Vector(web_descriptor_theme_scope_row("global", "web.theme", descriptor.theme)) ++
        descriptor.apps.map(app =>
          web_descriptor_asset_scope_row("app", app.normalizedName, app.assets)
        ) ++
        descriptor.apps.map(app =>
          web_descriptor_theme_scope_row("app theme", app.normalizedName, app.theme)
        ) ++
        forms.map {
          case (selector, _, _, _, form) =>
            web_descriptor_asset_scope_row("form", selector, form.assets)
        }
    val resolvedRows = forms.flatMap {
      case (selector, component, service, operation, _) =>
        Vector(
          web_descriptor_resolved_asset_row(
            selector,
            "component form index",
            descriptor.formIndexAssets(component)
          ),
          web_descriptor_resolved_asset_row(
            selector,
            "operation input",
            descriptor.resultAssets(component, service, operation)
          ),
          web_descriptor_resolved_asset_row(
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

  protected def web_descriptor_asset_scope_row(
    scope: String,
    selector: String,
    assets: WebDescriptor.Assets
  ): String =
    s"""<tr>
       |  <td>${escape(scope)}</td>
       |  <td><code>${escape(selector)}</code></td>
       |  <td>${assets.autoComplete}</td>
       |  <td>${web_descriptor_asset_url_list(assets.css)}</td>
       |  <td>${web_descriptor_asset_url_list(assets.js)}</td>
       |</tr>""".stripMargin

  protected def web_descriptor_theme_scope_row(
    scope: String,
    selector: String,
    theme: WebDescriptor.Theme
  ): String =
    s"""<tr>
       |  <td>${escape(scope)}</td>
       |  <td><code>${escape(selector)}</code></td>
       |  <td>true</td>
       |  <td>${web_descriptor_asset_url_list(theme.css)}${web_descriptor_theme_variables(theme)}</td>
       |  <td><span class="text-secondary">none</span></td>
       |</tr>""".stripMargin

  protected def web_descriptor_theme_variables(
    theme: WebDescriptor.Theme
  ): String =
    if (theme.variables.isEmpty)
      ""
    else {
      val vars = theme.variables.toVector.sortBy(_._1).map {
        case (key, value) => s"${key}=${value}"
      }.mkString(", ")
      s"""<div><small class="text-secondary">variables: ${escape(vars)}</small></div>"""
    }

  protected def web_descriptor_resolved_asset_row(
    selector: String,
    page: String,
    assets: WebDescriptor.Assets
  ): String =
    s"""<tr>
       |  <td><code>${escape(selector)}</code></td>
       |  <td>${escape(page)}</td>
       |  <td>${assets.autoComplete}</td>
       |  <td>${web_descriptor_asset_url_list(assets.css)}</td>
       |  <td>${web_descriptor_asset_url_list(assets.js)}</td>
       |</tr>""".stripMargin

  protected def web_descriptor_asset_url_list(
    urls: Vector[String]
  ): String =
    if (urls.isEmpty)
      """<span class="text-secondary">none</span>"""
    else
      urls.map(url => s"""<div><code>${escape(url)}</code></div>""").mkString

  protected def web_descriptor_app_json(
    app: WebDescriptor.App
  ): Json = {
    val configured = Json.obj(
      "name" -> Json.fromString(app.name),
      "path" -> Json.fromString(app.path),
      "kind" -> Json.fromString(app.kind),
      "theme" -> web_descriptor_theme_json(app.theme),
      "assets" -> web_descriptor_assets_json(app.assets)
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

  protected def web_descriptor_app_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.apps.isEmpty) {
      "<p>Using built-in Web HTML app defaults.</p>"
    } else {
      val rows = descriptor.apps.map { app =>
        val completed = app.completed
        s"""<tr><td>${escape(completed.name)}</td><td><code>${escape(completed.effectivePath)}</code></td><td><code>${escape(completed.effectiveRoot)}</code></td><td><code>${escape(completed.effectiveRoute)}</code></td><td>${escape(completed.effectiveKind)}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Apps</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Name</th><th>Path</th><th>Root</th><th>Route</th><th>Kind</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def web_descriptor_route_json(
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

  protected def web_descriptor_route_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.routes.isEmpty) {
      "<p>No subsystem Web route aliases are configured.</p>"
    } else {
      val rows = descriptor.routes.map { route =>
        s"""<tr><td><code>${escape(route.path)}</code></td><td>${escape(route.kind.name)}</td><td><code>${escape(route.target.component)}</code></td><td><code>${escape(route.target.app)}</code></td></tr>"""
      }.mkString("\n")
      s"""<h3>Routes</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Path</th><th>Kind</th><th>Target component</th><th>Target app</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def web_descriptor_exposure_list(
    descriptor: WebDescriptor
  ): String =
    if (descriptor.expose.isEmpty) {
      "<p>No explicit Web exposure entries.</p>"
    } else {
      val rows = descriptor.expose.toVector.sortBy(_._1).map {
        case (selector, exposure) =>
          val auth = descriptor.authorization.get(selector).map(_ => "yes").getOrElse("no")
          val form = descriptor.form.get(selector).flatMap(_.enabled).map(_.toString).getOrElse("default")
          s"""<tr><td><code>${escape(selector)}</code></td><td>${escape(exposure.name)}</td><td>${auth}</td><td>${form}</td></tr>"""
      }.mkString("\n")
      s"""<h3>Operation Exposure</h3><div class="table-responsive"><table class="table table-sm">
         |  <thead><tr><th>Selector</th><th>Exposure</th><th>Authorization</th><th>Form</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def performance_page(subsystem: Subsystem): String = {
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
    val jobs = job_metrics(subsystem)
    def card(title: String, body: String, id: Option[String] = None): String =
      s"""<div class="col-12">${admin_card(title, body, id)}</div>"""
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
    val assemblyActions = admin_action_row(Vector(
      "Warning detail" -> "/web/system/admin/assembly/warnings",
      "Assembly report" -> "/web/system/admin/assembly/report"
    ), primary = false)
    val assemblyCard = card(
      "Assembly warnings",
      s"""<p><span class="badge text-bg-secondary">${assembly_warning_count(subsystem)}</span> warning(s).</p>
         |${assemblyActions}""".stripMargin
    )
    val recentErrorsCard = card(
      "Recent errors",
      s"""<p class="text-secondary">HTTP 4xx/5xx entries shown as Dashboard recent failures. They are diagnostics and do not change runtime Health.</p>
         |${recent_errors_table(htmlRequests.recent)}""".stripMargin,
      Some("recent-errors")
    )
    val actionCallActions = admin_action_row(Vector(
      "Execution history" -> "/form/admin/execution/history",
      "Latest calltree" -> "/form/admin/execution/calltree"
    ), primary = false)
    val actionCallCard = card(
      "ActionCall",
      s"""${summary_table(actionCalls.summary)}
         |${actionCallActions}""".stripMargin
    )
    val authorizationCard = card(
      "Authorization",
      s"""${summary_table(authorizationDecisions.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(authorizationDiagnostics, authorizationDiagnosticRecords, Some("authorization"))}""".stripMargin,
      Some("authorization")
    )
    val validationCard = card(
      "Validation",
      s"""${summary_table(validation.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(validationDiagnostics, validationDiagnosticRecords, Some("validation"))}""".stripMargin
    )
    val operationRequestValidationCard = card(
      "Operation Request Validation",
      s"""${summary_table(operationRequestValidation.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(operationRequestValidationDiagnostics, operationRequestValidationDiagnosticRecords, Some("operation-request-validation"))}""".stripMargin
    )
    val blobOperationsCard = card(
      "Blob operations",
      s"""${summary_table(blobOperations.summary)}
         |<h3 class="h6 mt-3">Diagnostic</h3>
         |${diagnostics_table(blobDiagnostics, blobDiagnosticRecords, Some("blob"))}""".stripMargin
    )
    val cards = Vector(
      navigationCard,
      assemblyCard,
      card("HTML request", summary_table(htmlRequests.summary), Some("html-requests")),
      card("Latency", latency_table(htmlRequests.recent)),
      card("Recent requests", recent_requests_table(htmlRequests.recent)),
      recentErrorsCard,
      actionCallCard,
      authorizationCard,
      card("DSL Chokepoints", summary_table(dslChokepoints.summary)),
      validationCard,
      operationRequestValidationCard,
      blobOperationsCard,
      card("Jobs", jobs_table(jobs), Some("jobs"))
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
    val help = HelpProjection.project(component, selector)
    val describe = DescribeProjection.project(component, selector)
    val schema = SchemaProjection.project(component, selector)
    val childLinks = manual_child_links(currentPath, childNames)
    val componentletCard =
      if (selector.exists(NamingConventions.equivalentByNormalized(component.name, _)))
        manual_componentlet_section(component)
      else
        ""
    val storageShapeCard =
      if (selector.exists(NamingConventions.equivalentByNormalized(component.name, _)))
        manual_storage_shape_section(describe)
      else
        ""
    val authorizationPolicyCard =
      if (selector.exists(NamingConventions.equivalentByNormalized(component.name, _)))
        manual_authorization_policy_section(describe)
      else
        ""
    val body =
      s"""${manual_card("Specification navigation",
         s"""<p>This generated specification is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="${escape(currentPath)}#help">Help</a>
            |  <a class="btn btn-outline-primary" href="${escape(currentPath)}#describe">Describe</a>
            |  <a class="btn btn-outline-primary" href="${escape(currentPath)}#schema">Schema</a>
            |  <a class="btn btn-outline-secondary" href="/web/system/document/specification/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${manual_card("Children", childLinks)}
         |${componentletCard}
         |${storageShapeCard}
         |${authorizationPolicyCard}
         |${manual_projection_card("Help", currentPath, help, Some("help"))}
         |${manual_projection_card("Describe", currentPath, describe, Some("describe"))}
         |${manual_projection_card("Schema", currentPath, schema, Some("schema"))}""".stripMargin
    simple_page(title, subtitle, body)
  }

  protected def system_manual_page(
    subsystem: Subsystem,
    component: Component
  ): String = {
    val help = HelpProjection.project(component, None)
    val describe = DescribeProjection.project(component, None)
    val schema = SchemaProjection.project(component, None)
    val componentLinks = manual_component_links(subsystem.components)
    val body =
      s"""${manual_card("Specification navigation",
         s"""<p>This generated specification is read-only. Use it to inspect help, describe, schema, OpenAPI, and MCP entry points.</p>
            |<div class="d-flex flex-wrap gap-2 mt-3">
            |  <a class="btn btn-outline-primary" href="/web/system/dashboard">System dashboard</a>
            |  <a class="btn btn-outline-primary" href="/web/system/admin">Admin configuration</a>
            |  <a class="btn btn-outline-primary" href="/web/system/performance">Performance details</a>
            |  <a class="btn btn-outline-secondary" href="/web/system/document/specification/openapi.json">OpenAPI JSON</a>
            |  <a class="btn btn-outline-secondary" href="/mcp">MCP endpoint</a>
            |  <a class="btn btn-outline-secondary" href="/web/console">Console</a>
            |</div>""".stripMargin)}
         |${manual_card("Components", componentLinks)}
         |${manual_card("Console handoff", """<p class="mb-0">Use <a href="/web/console">System Console</a> for controlled operation entry. Specification pages remain read-only and do not inline operation actions.</p>""")}
         |${manual_authorization_policy_section(describe)}
         |${manual_projection_card("Help", "/web/system/document/specification", help, Some("help"))}
         |${manual_projection_card("Describe", "/web/system/document/specification", describe, Some("describe"))}
         |${manual_projection_card("Schema", "/web/system/document/specification", schema, Some("schema"))}""".stripMargin
    simple_page("System Specification", "Generated runtime specification", body)
  }

  protected def manual_component_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      web_empty_state("No component reference entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.name)
        s"""<a class="btn btn-sm btn-outline-primary" href="/web/${escape(segment)}/document/specification">${escape(component.name)}</a>"""
      }.mkString("""<div class="d-flex flex-wrap gap-2">""", "\n", "</div>")

  protected def manual_component_document_links(
    components: Vector[Component]
  ): String =
    if (components.isEmpty)
      web_empty_state("No component document entries.")
    else
      components.sortBy(_.name).map { component =>
        val segment = NamingConventions.toNormalizedSegment(component.name)
        s"""<a class="btn btn-sm btn-outline-primary" href="/web/${escape(segment)}/document">${escape(component.name)}</a>"""
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
    val recordType = record.getString("type").getOrElse("")
    recordType match {
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
    val detailComponents = manual_record_values(record.asMap.get("details")).get("components").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val components = if (detailComponents.nonEmpty) detailComponents else children
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
    val operationDefs = manual_record_seq(record.asMap.get("operationDefinitions")).flatMap(_.getString("name"))
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
         "Operation definition count" -> operationDefs.size.toString
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
        val targetModel = escape(r.getString("targetModelKind").getOrElse(""))
        val storage = escape(r.getString("storageMode").getOrElse(""))
        val parent = escape(r.getString("parentIdField").getOrElse(""))
        val value = escape(r.getString("valueField").getOrElse(""))
        val sort = escape(r.getString("sortOrderField").getOrElse(""))
        val domain = escape(r.getString("associationDomain").getOrElse(""))
        val targetKind = escape(r.getString("targetKind").getOrElse(""))
        val lifecycle = escape(r.getString("lifecyclePolicy").getOrElse(""))
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

  protected def manual_storage_shape_section(
    record: Record
  ): String = {
    val entities = manual_record_seq(record.asMap.get("entityCollections"))
    if (entities.isEmpty)
      ""
    else {
      val summaryRows = entities.map(manual_storage_shape_summary_row).mkString("\n")
      val fieldTables = entities.map(manual_storage_shape_field_table).mkString("\n")
      manual_card(
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

  protected def manual_authorization_policy_section(
    record: Record
  ): String = {
    val policy = record.getAny("authorizationPolicies").collect { case r: Record => r }
    policy match {
      case Some(p) if AuthorizationPolicyProjection.hasVisiblePolicy(p) =>
        val roles = manual_record_seq(p.asMap.get("roleDefinitions"))
        val resources = manual_record_seq(p.asMap.get("resourcePolicies"))
        val blobRequirements = manual_record_seq(p.asMap.get("blobOperationRequirements"))
        val roleTable =
          if (roles.isEmpty)
            web_empty_state("No role definitions are configured.")
          else
            s"""<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-roles">
               |    <thead><tr><th>Role</th><th>Includes</th><th>Capabilities</th><th>Source</th></tr></thead>
               |    <tbody>${roles.map(manual_authorization_role_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        val resourceTable =
          if (resources.isEmpty)
            web_empty_state("No resource policies are configured.")
          else
            s"""<div class="table-responsive">
               |  <table class="table table-sm table-hover align-middle manual-authorization-resources">
               |    <thead><tr><th>Family</th><th>Resource</th><th>Action</th><th>Capabilities</th><th>Permission</th><th>Source</th></tr></thead>
               |    <tbody>${resources.map(manual_authorization_resource_row).mkString("\n")}</tbody>
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
               |    <tbody>${blobRequirements.map(manual_authorization_blob_requirement_row).mkString("\n")}</tbody>
               |  </table>
               |</div>""".stripMargin
        manual_card(
          "Authorization policies",
          s"""<p class="mb-3">Read-only view of descriptor-backed authorization policy and Blob operation requirements.</p>
             |<h3 class="h6">Resource policies</h3>
             |${resourceTable}
             |<h3 class="h6 mt-3">Role definitions</h3>
             |${roleTable}
             |${blobRequirementTable}
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
    val entityName = record.getString("entityName").getOrElse("")
    val shape = manual_record_values(record.asMap.get("storageShape"))
    val fields = manual_record_seq(shape.get("fields"))
    if (fields.isEmpty)
      s"""<section class="mt-3">
         |  <h3 class="h6">${escape(entityName)} fields</h3>
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
         |  <h3 class="h6">${escape(entityName)} fields</h3>
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
    val qualifiedName = record.getString("name").getOrElse("")
    val qualifiedSegments = qualifiedName.split("\\.").toVector.filter(_.nonEmpty)
    val component = record.getString("component").orElse(qualifiedSegments.headOption).getOrElse("")
    val service = record.getString("service").orElse(qualifiedSegments.lift(1)).getOrElse("")
    val operation = qualifiedSegments.lift(2).orElse(Option(qualifiedName).filter(_.nonEmpty)).getOrElse("")
    val selector = manual_selector_map(record)
    val details = manual_record_values(record.asMap.get("details"))
    val arguments = details.get("arguments").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val returns = details.get("returns").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty)
    val description = details.get("description").map(x => manual_seq_values(Some(x))).getOrElse(Vector.empty).mkString(" ")
    val selectorText = selector.get("canonical").flatMap(manual_scalar).orElse(record.getString("selector")).getOrElse(qualifiedName)
    val restPath = selector.get("rest").flatMap(manual_scalar).map(manual_canonical_rest_path).getOrElse(s"/rest/v1/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}")
    val formPath = s"/form/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    val formApiPath = s"/form-api/${NamingConventions.toNormalizedSegment(component)}/${NamingConventions.toNormalizedSegment(service)}/${NamingConventions.toNormalizedSegment(operation)}"
    val describeArgumentRows = manual_record_seq(record.asMap.get("arguments")).map(manual_parameter_row)
    val parameterRows =
      if (describeArgumentRows.nonEmpty)
        describeArgumentRows
      else
        manual_schema_parameters_from_help(details, component, service, operation)
    s"""<p class="mb-3">${escape(record.getString("summary").getOrElse("Operation reference"))}</p>
       |${if (description.nonEmpty) s"""<p class="mb-3">${escape(description)}</p>""" else ""}
       |${manual_kv_summary(Vector(
         "Selector" -> selectorText,
         "Component" -> component,
         "Service" -> service,
         "Operation" -> operation,
         "Arguments" -> (if (parameterRows.nonEmpty) parameterRows.size else arguments.size).toString,
         "Returns" -> returns.mkString(", ")
       ))}
       |${manual_link_group(Vector(
         "Web specification" -> currentPath,
         "REST" -> restPath,
         "Form" -> formPath,
         "Form API" -> formApiPath,
       "OpenAPI JSON" -> "/web/system/document/specification/openapi.json"
      ))}
       |${manual_child_entity_binding_summary(record)}
       |${manual_association_binding_summary(record)}
       |${manual_image_binding_summary(record)}
       |${manual_parameter_table(parameterRows)}
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
    val targetType = record.getString("targetType").getOrElse(record.getString("type").getOrElse(""))
    targetType match {
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

  protected def manual_raw_json(value: Any): Option[Json] =
    value match {
      case Some(x) => manual_raw_json(x)
      case None => Some(Json.Null)
      case null => Some(Json.Null)
      case r: Record =>
        Some(Json.fromJsonObject(JsonObject.fromIterable(
          r.asMap.toVector.sortBy(_._1.toString).flatMap { case (k, v) =>
            manual_raw_json(v).map(k -> _)
          }
        )))
      case m: Map[?, ?] =>
        Some(Json.fromJsonObject(JsonObject.fromIterable(
          m.toVector.sortBy(_._1.toString).flatMap { case (k, v) =>
            manual_raw_json(v).map(k.toString -> _)
          }
        )))
      case xs: Seq[?] =>
        Some(Json.fromValues(xs.toVector.flatMap(manual_raw_json)))
      case s: String => Some(Json.fromString(s))
      case b: Boolean => Some(Json.fromBoolean(b))
      case i: Int => Some(Json.fromInt(i))
      case l: Long => Some(Json.fromLong(l))
      case d: Double if !d.isNaN && !d.isInfinity => Some(Json.fromDoubleOrNull(d))
      case f: Float if !f.isNaN && !f.isInfinity => Some(Json.fromFloatOrNull(f))
      case n: Number => Some(Json.fromString(n.toString))
      case x => Some(Json.fromString(x.toString))
    }

  protected def manual_raw_text(value: Any, indent: Int = 0): String = {
    val pad = "  " * indent
    value match {
      case Some(x) => manual_raw_text(x, indent)
      case None => "null"
      case null => "null"
      case r: Record =>
        r.asMap.toVector.sortBy(_._1.toString).map { case (k, v) =>
          s"${pad}${k}: ${manual_raw_text(v, indent + 1).stripPrefix("  " * (indent + 1))}"
        }.mkString("\n")
      case m: Map[?, ?] =>
        m.toVector.sortBy(_._1.toString).map { case (k, v) =>
          s"${pad}${k}: ${manual_raw_text(v, indent + 1).stripPrefix("  " * (indent + 1))}"
        }.mkString("\n")
      case xs: Seq[?] =>
        xs.toVector.map { x =>
          val rendered = manual_raw_text(x, indent + 1)
          if (rendered.contains("\n")) s"${pad}-\n$rendered" else s"${pad}- $rendered"
        }.mkString("\n")
      case x => x.toString
    }
  }

  protected def manual_record_values(
    value: Option[Any]
  ): Map[String, Any] =
    value match {
      case Some(Some(x)) => manual_record_values(Some(x))
      case Some(r: Record) => r.asMap
      case Some(m: Map[?, ?]) => m.toVector.collect { case (k: String, v) => k -> v }.toMap
      case _ => Map.empty
    }

  protected def manual_record_seq(
    value: Option[Any]
  ): Vector[Record] =
    value match {
      case Some(Some(x)) => manual_record_seq(Some(x))
      case Some(xs: Seq[?]) => xs.collect { case r: Record => r }.toVector
      case _ => Vector.empty
    }

  protected def manual_seq_values(
    value: Option[Any]
  ): Vector[String] =
    value match {
      case Some(Some(x)) => manual_seq_values(Some(x))
      case Some(xs: Seq[?]) => xs.toVector.flatMap(manual_scalar)
      case Some(x) => manual_scalar(x).toVector
      case None => Vector.empty
    }

  protected def manual_scalar(
    value: Any
  ): Option[String] =
    value match {
      case Some(x) => manual_scalar(x)
      case None => None
      case null => None
      case s: String if s.nonEmpty => Some(s)
      case b: Boolean => Some(b.toString)
      case n: Number => Some(n.toString)
      case _ => None
    }

  protected def raw_format_tabs(
    json: String,
    yaml: String,
    prefix: String
  ): String = {
    val token = s"${prefix}-${math.abs((json + yaml).hashCode)}"
    s"""<div class="${escape(prefix)}-raw-tabs mt-3">
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
       |      <pre class="bg-light border-0 rounded-0 rounded-bottom p-3 mb-0"><code>${escape(json)}</code></pre>
       |    </div>
       |    <div class="tab-pane fade" id="${token}-yaml-pane" role="tabpanel" aria-labelledby="${token}-yaml-tab" tabindex="0">
       |      <pre class="bg-light border-0 rounded-0 rounded-bottom p-3 mb-0"><code>${escape(yaml)}</code></pre>
       |    </div>
       |  </div>
       |</div>""".stripMargin
  }

  protected def json_to_yaml(jsontext: String): String =
    io.circe.parser.parse(jsontext).toOption.map(json_to_yaml).getOrElse(jsontext)

  protected def json_to_yaml(json: Json): String = {
    def go(value: Json, indent: Int): String = {
      val pad = "  " * indent
      value.fold(
        jsonNull = "null",
        jsonBoolean = _.toString,
        jsonNumber = _.toString,
        jsonString = s => yaml_quote(s),
        jsonArray = xs =>
          if (xs.isEmpty) "[]"
          else xs.toVector.map { item =>
            item.fold(
              jsonNull = s"${pad}- null",
              jsonBoolean = b => s"${pad}- ${b}",
              jsonNumber = n => s"${pad}- ${n}",
              jsonString = s => s"${pad}- ${yaml_quote(s)}",
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
              jsonString = s => s"${pad}${key}: ${yaml_quote(s)}",
              jsonArray = _ => s"${pad}${key}:\n${go(item, indent + 1)}",
              jsonObject = _ => s"${pad}${key}:\n${go(item, indent + 1)}"
            )
          }.mkString("\n")
      )
    }
    go(json, 0)
  }

  protected def yaml_quote(s: String): String =
    "\"" + s.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c => c.toString
    } + "\""

  protected def manual_card(
    title: String,
    body: String,
    id: Option[String] = None
  ): String = {
    val idText = id.map(x => s""" id="${escape(x)}"""").getOrElse("")
    s"""<article${idText} class="card manual-card shadow-sm">
       |  <div class="card-body">
       |    <h2 class="card-title h5">${escape(title)}</h2>
       |    ${body}
       |  </div>
       |</article>""".stripMargin
  }

  protected def simple_page(
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

  protected def property_rows(properties: Map[String, String]): String =
    properties.toVector.sortBy(_._1).map { case (key, value) =>
      s"""<dt class="col-sm-3">${escape(key)}</dt><dd class="col-sm-9"><code>${escape(value)}</code></dd>"""
    }.mkString("\n")
}
