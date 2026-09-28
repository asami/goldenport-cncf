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
private[http] trait StaticFormAppRendererDashboardPart { self: StaticFormAppRendererSystemAdminPart & StaticFormAppRendererSupport & StaticFormAppRendererBlobTagPart & StaticFormAppRendererComponentAdminPart & StaticFormAppRendererCorePart & StaticFormAppRendererFormPart & StaticFormAppRendererJobPart & StaticFormAppRendererObservabilityPart & StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*
  protected def find_component(
    subsystem: Subsystem,
    name: String
  ): Option[Component] =
    ComponentIdentityCompatibilityAdapter.resolveAliases(
      name,
      ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(subsystem.components),
      ComponentIdentityCompatibilityAdapter.Surface.WebPath
    ) match {
      case result: ComponentIdentityCompatibilityAdapter.Canonical =>
        subsystem.components.find(_.componentId == result.componentid)
      case result: ComponentIdentityCompatibilityAdapter.Adapted =>
        subsystem.components.find(_.componentId == result.componentid)
      case _: ComponentIdentityCompatibilityAdapter.Rejected =>
        None
    }

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
       |      <div class="col-12 col-lg-4"><article class="card h-100 shadow-sm"><div class="card-body"><h2 class="h5 card-title">CNCF</h2><p class="mb-1"><strong id="cncfVersion">-</strong></p><p class="mb-0"><a id="detailsLink" href="/web/system/admin">Admin details</a> · <a id="performanceLink" href="/web/system/performance">Performance details</a> · <a id="manualLink" href="/man/system">Manuals</a> · <a id="consoleLink" href="/web/console">Console</a></p></div></article></div>
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
    val descriptorpath = componentFormsPath
      .map(_.stripPrefix("/form/"))
      .map(componentPath => s"/web/${componentPath}/admin/descriptor")
      .getOrElse("/web/system/admin/descriptor")
    val componentblocks = components.map { component =>
      val services = component.protocol.services.services.map { service =>
        val operations = service.operations.operations.toVector.map { operation =>
          val path = NamingConventions.toNormalizedPath(component.name, service.name, operation.name)
          s"""<li>${escape(operation.name)} <code>${escape(path)}</code></li>"""
        }.mkString("\n")
        s"""<section><h3>${escape(service.name)}</h3><ul>${operations}</ul></section>"""
      }.mkString("\n")
      val version = component.artifactMetadata.map(_.version).getOrElse("unversioned")
      val componentlets = componentlet_table(component)
      val componentpath = NamingConventions.toNormalizedSegment(component.name)
      val entitycount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val datacount = admin_surface_selector_count(webDescriptor, Some(componentpath), "data")
      val aggregatecount = component.aggregateDefinitions.size
      val viewcount = component.viewDefinitions.size
      val formscount = component.protocol.services.services.map(_.operations.operations.toVector.size).sum
      val componentadminpages = webDescriptor.adminPagesFor(componentpath)
      val cards = admin_entry_cards(Vector(
        admin_entry_card(
          "Entities",
          s"${pluralize(entitycount, "runtime descriptor", "runtime descriptors")} ready for list/detail/new/edit.",
          s"/web/${componentpath}/admin/entities",
          Some(entitycount.toString)
        ),
        admin_entry_card(
          "Data",
          if (datacount > 0)
            s"${pluralize(datacount, "descriptor surface", "descriptor surfaces")} available through data admin."
          else
            "Open descriptor-backed data collections and concrete datastore records.",
          s"/web/${componentpath}/admin/data",
          Some(datacount.toString)
        ),
        admin_entry_card(
          "Aggregates",
          s"${pluralize(aggregatecount, "aggregate", "aggregates")} available for read/list drill-down.",
          s"/web/${componentpath}/admin/aggregates",
          Some(aggregatecount.toString)
        ),
        admin_entry_card(
          "Views",
          s"${pluralize(viewcount, "view definition", "view definitions")} available for read-only inspection.",
          s"/web/${componentpath}/admin/views",
          Some(viewcount.toString)
        ),
        admin_entry_card(
          "Descriptor",
          "Inspect routes, forms, auth controls, and admin surfaces.",
          s"/web/${componentpath}/admin/descriptor"
        ),
        admin_entry_card(
          "Forms",
          s"${pluralize(formscount, "operation form", "operation forms")} available for controlled execution.",
          s"/form/${componentpath}",
          Some(formscount.toString)
        )
      ))
      val componentownedadminpages = component_owned_admin_pages(componentadminpages)
      val technicaldetails =
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
           |${componentownedadminpages}
           |${technicaldetails}""".stripMargin
      )
    }.mkString("\n")
    val componentinventory =
      if (componentFormsPath.isEmpty)
        system_admin_component_inventory(components)
      else
        ""
    val runtimerows =
      s"""<tr><th>CNCF version</th><td>${escape(CncfVersion.current)}</td></tr>
         |<tr><th>Subsystem</th><td>${escape(subsystemName)}</td></tr>
         |<tr><th>Subsystem version</th><td>${escape(subsystemVersion.getOrElse("unversioned"))}</td></tr>
         |<tr><th>Components</th><td>${components.size}</td></tr>""".stripMargin
    val runtimecard =
      admin_card(
        "Runtime",
        admin_table(None, runtimerows, tableClass = "table table-sm align-middle mb-0")
      )
    val primarynav = Vector(
      "Application admin" -> "/web/admin",
      "Dashboard" -> dashboardPath,
      "Performance details" -> performancePath,
      "Observability" -> "/web/system/admin/observability",
      "Manuals" -> "/man/system",
      "Console" -> "/web/console"
    )
    simple_page(
      title = title,
      subtitle = subtitle,
      body =
        s"""<section data-textus-page="generated-admin" data-textus-section="admin-root"${ux_profile_attr(profile)}>
           |${runtimecard}
           |${admin_nav_card(primarynav)}
           |${componentinventory}
           |${admin_operational_details(operationalDetails)}
           |${component_admin_actions(componentFormsPath)}
           |${admin_card("Web Descriptor", web_descriptor_summary(webDescriptor, descriptorpath))}
           |${admin_runtime_configuration(runtimeConfiguration)}
           |${admin_job_control(runtimeConfiguration)}
           |${componentblocks}
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
      val componentpath = NamingConventions.toNormalizedSegment(component.name)
      val entitycount = component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).size
      val datacount = admin_surface_selector_count(webDescriptor, Some(componentpath), "data")
      val aggregatecount = component.aggregateDefinitions.size
      val viewcount = component.viewDefinitions.size
      val entries = Vector(
        Option.when(entitycount > 0)(admin_entry_card(
          "Entities",
          s"${pluralize(entitycount, "runtime descriptor", "runtime descriptors")} available for application operations.",
          s"/web/${componentpath}/admin/entities",
          Some(entitycount.toString)
        )),
        Option.when(datacount > 0)(admin_entry_card(
          "Data",
          s"${pluralize(datacount, "descriptor surface", "descriptor surfaces")} available for application operations.",
          s"/web/${componentpath}/admin/data",
          Some(datacount.toString)
        )),
        Option.when(aggregatecount > 0)(admin_entry_card(
          "Aggregates",
          s"${pluralize(aggregatecount, "aggregate", "aggregates")} available for application operations.",
          s"/web/${componentpath}/admin/aggregates",
          Some(aggregatecount.toString)
        )),
        Option.when(viewcount > 0)(admin_entry_card(
          "Views",
          s"${pluralize(viewcount, "view definition", "view definitions")} available for application operations.",
          s"/web/${componentpath}/admin/views",
          Some(viewcount.toString)
        ))
      ).flatten
      Option.when(entries.nonEmpty) {
        admin_card(
          component.name,
          s"""<p class="text-body-secondary">Application-facing generic admin surfaces.</p>
             |${admin_entry_cards(entries)}
             |${admin_action_row(Vector("Component admin" -> s"/web/${componentpath}/admin"), primary = false)}""".stripMargin
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
    val items = pages.flatMap { page =>
      page.canonicalHref.map { href =>
      val description = Option(page.description).map(_.trim).filter(_.nonEmpty)
        .map(value => s"""<p class="mb-2 text-body-secondary">${escape(value)}</p>""")
        .getOrElse("")
      s"""<a class="list-group-item list-group-item-action" href="${escape(href)}">
         |  <div class="d-flex justify-content-between align-items-start gap-3">
         |    <div>
         |      <div class="fw-semibold">${escape(page.effectiveLabel)}</div>
         |      ${description}
         |      <code>${escape(href)}</code>
         |    </div>
         |    <span class="badge text-bg-secondary">${escape(page.effectivePermission)}</span>
         |  </div>
         |</a>""".stripMargin
      }
    }
    if (items.isEmpty)
      admin_empty_state("No descriptor-declared component admin pages are available.")
    else
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
    val systempages = webDescriptor.adminPagesForAudience(WebDescriptor.AdminAudience.System)
    val systempagelinks =
      if (systempages.isEmpty)
        ""
      else
        s"""<div class="mt-3">
           |  <h3 class="h6">System Admin Pages</h3>
           |  ${component_owned_admin_pages_list(systempages)}
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
      |${systempagelinks}
      |${component_dev_dir_diagnostics(components)}""".stripMargin
  }

  protected def component_dev_dir_diagnostics(
    components: Vector[Component]
  ): String = {
    val rows = components.flatMap { component =>
      component.artifactMetadata.toVector.filter(_.sourceType == "component-dev-dir").map { metadata =>
        val base = metadata.archivePath.map(p => Paths.get(p).toAbsolutePath.normalize)
        val classpath = base.map(_.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt"))
        val webroots = base.toVector.flatMap { path =>
          Vector(path.resolve("car.d").resolve("web"), path.resolve("src").resolve("main").resolve("web"), path.resolve("web"))
            .filter(Files.isDirectory(_))
        }.map(path => escape(path.toString)).mkString("<br>")
        s"""<tr>
           |  <td><code>${escape(component.name)}</code></td>
           |  <td><code>${escape(base.map(_.toString).getOrElse(""))}</code></td>
           |  <td><code>${escape(classpath.map(_.toString).getOrElse(""))}</code></td>
           |  <td>${if (webroots.isEmpty) "<span class=\"text-body-secondary\">No Web root</span>" else webroots}</td>
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
      val componentpath = path.stripPrefix("/form/")
      s"""<section class="admin-section">
         |  <h2 class="h5">Component Admin</h2>
         |  <p>Use Application Admin for ordinary operator workflows. This component page remains available for component-specific drill-down and technical reference.</p>
         |  ${admin_action_row(Vector("Application admin" -> "/web/admin"), primary = false)}
         |  ${component_admin_management_cards(componentpath, path)}
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
    val componentpath = formsPath.stripPrefix("/form/")
    s"""<h3 class="h6">Managed Data</h3>
       |${admin_link_list_group(Vector(
         "Entity CRUD" -> s"/web/${componentpath}/admin/entities",
         "Data CRUD" -> s"/web/${componentpath}/admin/data",
         "Aggregate CRUD" -> s"/web/${componentpath}/admin/aggregates",
         "View read" -> s"/web/${componentpath}/admin/views",
         "Tags" -> "/web/admin/tags"
       ))}""".stripMargin
  }

}
