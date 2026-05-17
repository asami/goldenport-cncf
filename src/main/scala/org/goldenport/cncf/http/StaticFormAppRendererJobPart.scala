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
trait StaticFormAppRendererJobPart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererFormResultPart with StaticFormAppRendererSystemAdminPart with StaticFormAppRendererTemplatePart =>
  import StaticFormAppRendererSupport.*

  def renderSystemJobTicket(
    jobId: String
  ): Page =
    renderFormResult(
      FormResultProperties(
        FormPageProperties(
          "system",
          "job",
          "result",
          system_job_values(jobId, "accepted", s"/web/system/jobs/${jobId}/await")
        ),
        200,
        "application/json",
        s"""{"jobId":"${json(jobId)}","jobStatus":"accepted","message":"Job result is available from this system page."}"""
      ),
      system_job_template("Job result")
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
          system_job_values(jobId, if (ok) "completed" else "failed", s"/web/system/jobs/${jobId}/await")
        ),
        response.code,
        response.mime.value,
        response.getString.getOrElse("")
      ),
      system_job_template("Job result")
    )
  }

  protected def system_job_values(
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
      "result.action.detail.href" -> s"/web/system/admin/jobs/${escape_path_segment(jobId)}",
      "result.action.detail.method" -> "GET"
    )

  protected def system_job_template(
    title: String
  ): String =
    s"""<article>
       |  <h2>${escape(title)}</h2>
       |  <textus:job-ticket></textus:job-ticket>
       |  <textus:action-link source="result.action.detail" class="btn btn-outline-secondary btn-sm"></textus:action-link>
       |  <textus-error-panel source="error"></textus-error-panel>
       |  <textus-result-view source="result.body"></textus-result-view>
       |  <textus-property-list source="result"></textus-property-list>
       |</article>""".stripMargin


  def renderSystemAdminJobs(
    subsystem: Subsystem
  ): Page = {
    val jobs = subsystem.jobEngine.listJobs(limit = renderer_config.adminPageSize, persistentOnly = true)
    val rows =
      if (jobs.isEmpty)
        """<tr><td colspan="7" class="text-secondary">No persistent jobs have been retained yet. Run a request with <code>--debug.trace-job</code> or <code>textus.debug.trace-job=true</code>.</td></tr>"""
      else
        jobs.map(system_admin_job_row).mkString("\n")
    Page(simple_page(
      title = "System Admin Jobs",
      subtitle = "Persistent job debug and calltree inspection",
      body =
        s"""${admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "System dashboard" -> "/web/system/dashboard",
             "Execution history" -> "/form/admin/execution/history"
           ))}
           |${admin_card(
             "Debug Jobs",
             s"""<p>Query requests normally run directly. Requests submitted with <code>--debug.trace-job</code> are retained here for operational debugging.</p>
                |${admin_table(
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
    val target = job_target(model)
    val calltree = model.calltree.map(job_calltree_panel).getOrElse(
      """<p class="text-secondary">No calltree was saved for this job. Use <code>--debug.save-calltree</code>, or inspect failed/slow persistent jobs.</p>"""
    )
    val taskrows =
      if (model.tasks.tasks.isEmpty)
        """<tr><td colspan="7" class="text-secondary">No task records are available.</td></tr>"""
      else
        model.tasks.tasks.map { task =>
          s"""<tr><td><code>${escape(task.taskId.value)}</code></td><td>${escape(task.status.toString)}</td><td>${escape(task.component.getOrElse(""))}</td><td>${escape(task.operation.getOrElse(""))}</td><td>${escape(task.result.message.getOrElse(""))}</td><td>${escape(task.startedAt.toString)}</td><td>${escape(task.finishedAt.map(_.toString).getOrElse(""))}</td></tr>"""
        }.mkString("\n")
    val eventrows =
      if (model.timeline.events.isEmpty)
        """<tr><td colspan="5" class="text-secondary">No timeline events are available.</td></tr>"""
      else
        model.timeline.events.map { event =>
          s"""<tr><td>${event.sequence}</td><td>${escape(event.kind)}</td><td>${escape(event.occurredAt.toString)}</td><td>${escape(event.taskId.map(_.value).getOrElse(""))}</td><td>${escape(event.note.getOrElse(""))}</td></tr>"""
        }.mkString("\n")
    Page(simple_page(
      title = s"System Admin Job ${model.jobId.value}",
      subtitle = "Job-managed trace and calltree detail",
      body =
        s"""${admin_nav_card(Vector(
             "Debug jobs" -> "/web/system/admin/jobs",
             "System job result" -> s"/web/system/jobs/${escape_path_segment(model.jobId.value)}",
             "System admin" -> "/web/system/admin"
           ))}
           |${admin_card(
             "Summary",
             s"""<dl class="row mb-0">
           |    <dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${escape(model.jobId.value)}</code></dd>
           |    <dt class="col-sm-3">Status</dt><dd class="col-sm-9">${escape(model.status.toString)}</dd>
           |    <dt class="col-sm-3">Persistence</dt><dd class="col-sm-9">${escape(model.persistence.toString)}</dd>
           |    <dt class="col-sm-3">Target</dt><dd class="col-sm-9"><code>${escape(target)}</code></dd>
           |    <dt class="col-sm-3">Result</dt><dd class="col-sm-9">${escape(model.resultSummary.message.getOrElse(""))}</dd>
           |    <dt class="col-sm-3">Trace ID</dt><dd class="col-sm-9"><code>${escape(model.lineage.correlationId.getOrElse(model.debug.parameters.getOrElse("traceId", "")))}</code></dd>
           |    <dt class="col-sm-3">Updated</dt><dd class="col-sm-9">${escape(model.updatedAt.toString)}</dd>
           |  </dl>""".stripMargin
           )}
           |${admin_card("Calltree", calltree)}
           |${admin_card(
             "Tasks",
             admin_table(
               Some("<tr><th>Task</th><th>Status</th><th>Component</th><th>Operation</th><th>Message</th><th>Started</th><th>Finished</th></tr>"),
               taskrows
             )
           )}
           |${admin_card(
             "Timeline",
             admin_table(
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
        jobs.map(application_job_row(appPath, _)).mkString("\n")
    Page(simple_page(
      title = s"${escape(app)} Jobs",
      subtitle = "Your application jobs",
      body =
        s"""<article class="card">
           |  <div class="card-body">
           |    <div class="d-flex flex-wrap justify-content-between gap-2 align-items-start mb-3">
           |      <div>
           |        <h2 class="h5 card-title mb-1">My jobs</h2>
           |        <p class="text-secondary mb-0">Jobs started from this application by the current user or session.</p>
           |      </div>
           |      <a class="btn btn-outline-secondary btn-sm" href="/web/${escape(appPath)}">Back to application</a>
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
    val target = job_target(model)
    val calltree = model.calltree.map(job_calltree_panel).getOrElse(
      """<p class="text-secondary">No CallTree was saved for this job yet.</p>"""
    )
    val result = model.result.map(_.print).getOrElse(model.resultSummary.message.getOrElse(""))
    Page(simple_page(
      title = s"${escape(app)} Job ${model.jobId.value}",
      subtitle = "Application job result",
      body =
        s"""<article class="card mb-3">
           |  <div class="card-body">
           |    <div class="d-flex flex-wrap justify-content-between gap-2 align-items-start mb-3">
           |      <div>
           |        <h2 class="h5 card-title mb-1">Job result</h2>
           |        <p class="text-secondary mb-0"><code>${escape(target)}</code></p>
           |      </div>
           |      <a class="btn btn-outline-secondary btn-sm" href="/web/${escape(appPath)}/jobs">My jobs</a>
           |    </div>
           |    <dl class="row mb-0">
           |      <dt class="col-sm-3">Job ID</dt><dd class="col-sm-9"><code>${escape(model.jobId.value)}</code></dd>
           |      <dt class="col-sm-3">Status</dt><dd class="col-sm-9"><span class="badge text-bg-${escape(job_status_variant(model.status.toString))}">${escape(model.status.toString)}</span></dd>
           |      <dt class="col-sm-3">Result</dt><dd class="col-sm-9">${escape(model.resultSummary.message.getOrElse(""))}</dd>
           |      <dt class="col-sm-3">Updated</dt><dd class="col-sm-9">${escape(model.updatedAt.toString)}</dd>
           |    </dl>
           |  </div>
           |</article>
           |<article class="card mb-3">
           |  <div class="card-body">
           |    <h2 class="h5 card-title">Response</h2>
           |    <pre class="bg-light border rounded p-3 mb-0"><code>${escape(result)}</code></pre>
           |  </div>
           |</article>
           |<article class="card">
           |  <div class="card-body">
           |    <h2 class="h5 card-title">CallTree</h2>
           |    ${calltree}
           |    <div class="d-flex flex-wrap gap-2 mt-3">
           |      <a class="btn btn-sm btn-outline-secondary" href="/web/system/admin/jobs/${escape_path_segment(model.jobId.value)}">System debug detail</a>
           |      <a class="btn btn-sm btn-outline-secondary" href="/form/admin/execution/history">Execution history</a>
           |    </div>
           |  </div>
           |</article>""".stripMargin
    ))
  }

  protected def application_job_row(
    appPath: String,
    model: JobQueryReadModel
  ): String = {
    val target = job_target(model)
    s"""<tr><td><code>${escape(model.jobId.value)}</code></td><td>${escape(model.status.toString)}</td><td><code>${escape(target)}</code></td><td>${escape(model.resultSummary.message.getOrElse(""))}</td><td>${escape(model.updatedAt.toString)}</td><td><a href="/web/${escape(appPath)}/jobs/${escape_path_segment(model.jobId.value)}">Open</a></td></tr>"""
  }

  protected def system_admin_job_row(
    model: JobQueryReadModel
  ): String = {
    val target = job_target(model)
    val trace = model.lineage.correlationId
      .orElse(model.debug.parameters.get("traceId"))
      .getOrElse("")
    s"""<tr><td><code>${escape(model.jobId.value)}</code></td><td>${escape(model.status.toString)}</td><td><code>${escape(target)}</code></td><td>${escape(model.resultSummary.message.getOrElse(""))}</td><td><code>${escape(trace)}</code></td><td>${escape(model.updatedAt.toString)}</td><td><a href="/web/system/admin/jobs/${escape_path_segment(model.jobId.value)}">Open</a></td></tr>"""
  }

  protected def job_target(
    model: JobQueryReadModel
  ): String =
    model.tasks.tasks.headOption.map { task =>
      Vector(task.component, task.service, task.operation).flatten.filter(_.nonEmpty).mkString(".")
    }.filter(_.nonEmpty).orElse(model.debug.requestSummary).getOrElse("")

  protected def job_calltree_panel(
    record: Record
  ): String =
    s"""<pre class="bg-light border rounded p-3"><code>${escape(record.show)}</code></pre>"""

  protected def admin_job_control(
    configuration: Option[ResolvedConfiguration]
  ): String =
    configuration.map { _ =>
      admin_card(
        "Job Control",
        s"""<p>Job control entry points are reserved for system admin operations. Use builtin job and event surfaces as the authoritative source for cross-component continuation observability.</p>
           |<h3 class="h6 mt-3">Operator Checklist</h3>
           |<div class="list-group mb-3">
           |  <div class="list-group-item">Use <code>job_control.job.get_job_status</code> for source/target component, reception policy, task relation, transaction relation, and task summary.</div>
           |  <div class="list-group-item">Use <code>job_control.job.load_job_history</code> for per-task execution timeline.</div>
           |  <div class="list-group-item">Use <code>event.event.load_event</code> for dispatch contract, policy source, and unsupported-policy rejection evidence.</div>
           |  <div class="list-group-item">Use <code>job_control.job_admin.load_job_events</code> for job-to-event cross-links.</div>
           |</div>
           |${admin_link_list_group(Vector(
             "Debug jobs" -> "/web/system/admin/jobs",
             "Execution diagnostics" -> "/form/admin/execution/diagnostics",
             "Execution history" -> "/form/admin/execution/history",
             "Latest calltree" -> "/form/admin/execution/calltree"
           ))}
           |<p class="mt-3 mb-0">Mutation actions must require explicit admin authorization, confirmation for destructive actions, and audit logging before they are enabled.</p>""".stripMargin
      )
    }.getOrElse("")
}
