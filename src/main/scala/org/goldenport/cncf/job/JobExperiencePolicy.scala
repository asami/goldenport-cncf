package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.cncf.naming.NamingConventions

/*
 * Scope admission for the Job experience projection. It never changes Job state.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperiencePolicy(
  val scope: JobExperienceScope
) extends JobQueryPolicy {
  override val visibilityKey: String = scope.visibilityKey

  def authorizeScope(using ctx: ExecutionContext): Consequence[Unit] =
    scope match {
      case JobExperienceScope.Mine(_) if ctx.security.subjectKind == SubjectKind.Anonymous =>
        Consequence.operationIllegal("job.experience", "an authenticated subject is required")
      case JobExperienceScope.Application(_) if !_broad_reader =>
        Consequence.operationIllegal("job.experience", "application scope requires a broad Job read capability")
      case JobExperienceScope.Operator if !ctx.security.hasAnyCapability(Set("job_admin", "content_admin")) =>
        Consequence.operationIllegal("job.experience", "operator scope requires job_admin or content_admin")
      case _ =>
        Consequence.unit
    }

  def authorizeRead(model: JobQueryReadModel)(using ctx: ExecutionContext): Consequence[Unit] =
    JobQueryPolicy.default.authorizeRead(model).flatMap { _ =>
      if (_admitted(model)) Consequence.unit
      else Consequence.operationIllegal("job.experience", "job is not available in this scope")
    }

  def canControl(detail: JobManagementDetail, command: JobControlCommand)(using ctx: ExecutionContext): Boolean =
    detail.summary.origin == JobDataOrigin.Runtime &&
      JobControlPolicy.default.authorize(detail.summary.jobId, JobControlRequest(command)).isSuccess

  def canDiagnose(using ctx: ExecutionContext): Boolean =
    ctx.security.hasAnyCapability(Set("job_admin", "content_admin"))

  private def _admitted(model: JobQueryReadModel)(using ctx: ExecutionContext): Boolean =
    scope match {
      case JobExperienceScope.Mine(application) =>
        ctx.security.subjectKind != SubjectKind.Anonymous &&
          JobQueryPolicy.isOwner(model.submitter, ctx) &&
          application.forall(JobExperiencePolicy.applicationVisible(model, _))
      case JobExperienceScope.Application(application) =>
        _broad_reader && JobExperiencePolicy.applicationVisible(model, application)
      case JobExperienceScope.Operator =>
        ctx.security.hasAnyCapability(Set("job_admin", "content_admin"))
    }

  private def _broad_reader(using ctx: ExecutionContext): Boolean =
    ctx.security.hasAnyCapability(Set("job_view", "job_admin", "content_manager", "content_admin"))
}

object JobExperiencePolicy {
  def controls(status: JobStatus): Vector[JobControlCommand] = status match {
    case JobStatus.Submitted | JobStatus.Running => Vector(JobControlCommand.Cancel, JobControlCommand.Suspend)
    case JobStatus.Suspended => Vector(JobControlCommand.Cancel, JobControlCommand.Resume)
    case JobStatus.Failed | JobStatus.Cancelled => Vector(JobControlCommand.Retry)
    case JobStatus.Succeeded => Vector.empty
  }

  def applicationVisible(model: JobQueryReadModel, application: String): Boolean = {
    val normalized = NamingConventions.toNormalizedSegment(application)
    model.debug.parameters.get("web.application-job").contains("true") &&
      model.debug.parameters.get("web.app").exists(value =>
        NamingConventions.toNormalizedSegment(value) == normalized
      )
  }
}
