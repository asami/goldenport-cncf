package org.goldenport.cncf.job

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.openapi.OpenApiOperationProjection
import org.goldenport.record.Record

/*
 * The sole Job UX facade. It projects canonical management queries and invokes
 * the engine only after exact scoped admission; it owns no Job state or cache.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceService(
  engine: JobEngine
) {
  def list(
    query: JobExperienceQuery,
    displayLocale: Option[Locale] = None
  )(using ctx: ExecutionContext): Consequence[JobExperiencePage] = {
    val policy = new JobExperiencePolicy(query.scope)
    policy.authorizeScope.flatMap { _ =>
      engine.queryPage(query.management, policy).map { page =>
        JobExperiencePage(
          page.entries.map(summary => _entry(summary, displayLocale.getOrElse(_locale))),
          page.totalCount,
          page.nextCursor
        )
      }
    }
  }

  def get(
    jobId: JobId,
    scope: JobExperienceScope,
    displayLocale: Option[Locale] = None
  )(using ctx: ExecutionContext): Consequence[JobExperienceView] = {
    val policy = new JobExperiencePolicy(scope)
    policy.authorizeScope match {
      case Consequence.Success(_) =>
        _detail(jobId, policy).flatMap { detail =>
          engine.queryManagementResult(jobId, policy).flatMap {
            case Some(result) => Consequence.success(_view(detail, result, policy, displayLocale.getOrElse(_locale)))
            case None => _not_found(jobId)
          }
        }
      case Consequence.Failure(_) =>
        _not_found(jobId)
    }
  }

  def diagnostics(
    jobId: JobId,
    scope: JobExperienceScope,
    offset: Int,
    limit: Int
  )(using ctx: ExecutionContext): Consequence[JobExperienceDiagnostics] = {
    val policy = new JobExperiencePolicy(scope)
    if (offset < 0 || limit < 1 || limit > JobManagementQuery.MaximumLimit)
      Consequence.argumentInvalid("diagnostics offset must be nonnegative and limit must be in 1..100")
    else if (!policy.canDiagnose)
      Consequence.operationIllegal("job.experience.diagnostics", "operator diagnostics require job_admin or content_admin")
    else
      policy.authorizeScope match {
        case Consequence.Success(_) =>
          _detail(jobId, policy).flatMap { _ =>
            for {
              tasks <- engine.queryManagementTasks(jobId, offset, limit, policy).flatMap(_.map(Consequence.success).getOrElse(_not_found(jobId)))
              timeline <- engine.queryManagementTimeline(jobId, offset, limit, policy).flatMap(_.map(Consequence.success).getOrElse(_not_found(jobId)))
            } yield JobExperienceDiagnostics(jobId, _safe_tasks(tasks), _safe_timeline(timeline))
          }
        case Consequence.Failure(_) =>
          _not_found(jobId)
        }
  }

  def control(
    jobId: JobId,
    scope: JobExperienceScope,
    command: JobControlCommand
  )(using ctx: ExecutionContext): Consequence[JobControlResponse] = {
    val policy = new JobExperiencePolicy(scope)
    policy.authorizeScope match {
      case Consequence.Success(_) =>
        _detail(jobId, policy).flatMap { detail =>
          if (!policy.canControl(detail, command))
            Consequence.operationIllegal("job.experience.control", "the requested control is not available")
          else
            engine.control(jobId, JobControlRequest(command), JobControlPolicy.default)
          }
      case Consequence.Failure(_) =>
        _not_found(jobId)
      }
  }

  def operatorCatalog(component: Component)(using ctx: ExecutionContext): Consequence[Record] = {
    val policy = new JobExperiencePolicy(JobExperienceScope.Operator)
    policy.authorizeScope.flatMap { _ =>
      val metrics = engine.metrics.map { value =>
        Record.data("runtime-only" -> true, "running" -> value.running, "queued" -> value.queued, "completed" -> value.completed, "failed" -> value.failed)
      }.getOrElse(Record.data("runtime-only" -> true, "available" -> false))
      val operations = component.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(operation => service.name -> operation)
      }
      def _category_(name: String, service: String, operation: String): Record =
        operations.find { case (servicename, definition) =>
          NamingConventions.equivalentByNormalized(servicename, service) &&
            NamingConventions.equivalentByNormalized(definition.name, operation)
        }.map { case (servicename, definition) =>
          val method = definition match {
            case projection: OpenApiOperationProjection => projection.openApiHttpMethod.toString
            case _ => "unavailable"
          }
          Record.data(
            "name" -> name,
            "available" -> true,
            "operation" -> s"$servicename.${definition.name}",
            "method" -> method,
            "path" -> NamingConventions.toNormalizedPath(component.name, servicename, definition.name)
          )
        }.getOrElse(Record.data("name" -> name, "available" -> false, "handoff" -> "not installed"))
      Consequence.success(Record.data(
        "categories" -> Vector(
          _category_("search", "job", "search_job_definitions"),
          _category_("diagnostics", "job_experience", "get_job_diagnostics"),
          _category_("recovery", "job_experience", "control_job_experience"),
          _category_("definitions", "job", "search_job_definitions"),
          _category_("queue", "job_experience", "list_operator_jobs"),
          _category_("scheduler", "job_experience", "list_operator_jobs"),
          Record.data("name" -> "health", "available" -> engine.metrics.nonEmpty, "runtime-only" -> true),
          Record.data("name" -> "retention", "available" -> false, "handoff" -> "Phase 69.7")
        ),
        "health" -> metrics
      ))
    }
  }

  private def _detail(
    jobid: JobId,
    policy: JobExperiencePolicy
  )(using ctx: ExecutionContext): Consequence[JobManagementDetail] =
    engine.queryManagementDetail(jobid, policy).flatMap(_.map(Consequence.success).getOrElse(_not_found(jobid)))

  private def _entry(summary: JobManagementSummary, locale: Locale): JobExperienceEntry =
    JobExperienceEntry(
      summary.jobId,
      summary.status,
      JobExperienceStatus.from(summary.status),
      JobExperienceVocabulary.resolve(summary.status, locale),
      summary.persistence,
      summary.origin,
      summary.createdAt,
      summary.updatedAt
    )

  private def _view(
    detail: JobManagementDetail,
    result: JobManagementResult,
    policy: JobExperiencePolicy,
    locale: Locale
  )(using ctx: ExecutionContext): JobExperienceView = {
    val summary = detail.summary
    val active = summary.status == JobStatus.Submitted || summary.status == JobStatus.Running
    val controls = JobExperiencePolicy.controls(summary.status).filter(policy.canControl(detail, _))
    val progress =
      if (active) "indeterminate"
      else if (summary.status == JobStatus.Suspended) "paused"
      else "terminal"
    JobExperienceView(
      detail,
      JobExperienceStatus.from(summary.status),
      JobExperienceVocabulary.resolve(summary.status, locale),
      JobExperienceProgress(progress, detail.taskCount),
      _result(summary.status, result),
      controls,
      active && (result match {
        case JobManagementResult.UnavailableAfterRestart(_) => false
        case _ => true
      })
    )
  }

  private def _result(status: JobStatus, result: JobManagementResult): JobExperienceResult =
    result match {
      case JobManagementResult.Available(JobResult.Success(response)) =>
        JobExperienceResult.Available(response.print)
      case JobManagementResult.Available(JobResult.Failure(_)) =>
        JobExperienceResult.Failed
      case JobManagementResult.Pending(_) if JobStatus.isTerminal(status) =>
        JobExperienceResult.Unavailable
      case JobManagementResult.Pending(_) => JobExperienceResult.Pending
      case JobManagementResult.UnavailableAfterRestart(_) => JobExperienceResult.UnavailableAfterRestart
    }

  private def _safe_tasks(page: JobTaskPage): JobTaskPage =
    page.copy(tasks = page.tasks.map { task =>
      task.copy(
        result = JobTaskResultSummary(task.result.success, None),
        compensationFailureSummary = None
      )
    })

  private def _safe_timeline(page: JobTimelinePage): JobTimelinePage =
    page.copy(events = page.events.map(_.copy(note = None)))

  private def _locale(using ctx: ExecutionContext): Locale =
    ctx.runtime.context.i18n.locale

  private def _not_found[A](jobid: JobId): Consequence[A] =
    Consequence.operationNotFound(s"job:${jobid.value}")
}
