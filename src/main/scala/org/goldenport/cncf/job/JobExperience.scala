package org.goldenport.cncf.job

import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.naming.NamingConventions

/*
 * Safe user and operator vocabulary over the canonical Job management model.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
enum JobExperienceScope {
  case Mine(application: Option[String])
  case Application(application: String)
  case Operator

  def applicationOption: Option[String] = this match {
    case JobExperienceScope.Mine(value) => value
    case JobExperienceScope.Application(value) => Some(value)
    case JobExperienceScope.Operator => None
  }

  def visibilityKey: String = this match {
    case JobExperienceScope.Mine(None) => "mine"
    case JobExperienceScope.Mine(Some(value)) => s"mine:$value"
    case JobExperienceScope.Application(value) => s"application:$value"
    case JobExperienceScope.Operator => "operator"
  }
}

object JobExperienceScope {
  def mine(application: Option[String]): Consequence[JobExperienceScope] =
    application.map(_application).getOrElse(Consequence.success(""))
      .map(value => JobExperienceScope.Mine(Option.when(value.nonEmpty)(value)))

  def application(value: String): Consequence[JobExperienceScope] =
    _application(value).map(JobExperienceScope.Application.apply)

  def parse(scope: Option[String], application: Option[String]): Consequence[JobExperienceScope] =
    scope.map(_.trim.toLowerCase(Locale.ROOT)).filter(_.nonEmpty).getOrElse("mine") match {
      case "mine" => mine(application)
      case "application" =>
        application.map(_application).getOrElse(Consequence.argumentMissing("application"))
          .map(JobExperienceScope.Application.apply)
      case "operator" =>
        if (application.nonEmpty)
          Consequence.argumentInvalid("operator scope does not accept application")
        else
          Consequence.success(JobExperienceScope.Operator)
      case _ => Consequence.argumentInvalid("scope must be mine, application, or operator")
    }

  def normalizedApplication(value: String): Consequence[String] = _application(value)

  private def _application(value: String): Consequence[String] = {
    val raw = Option(value).getOrElse("").trim
    val normalized = NamingConventions.toNormalizedSegment(raw)
    if (raw.isEmpty || normalized.isEmpty)
      Consequence.argumentInvalid("application must be a nonempty normalized segment")
    else if (raw.contains('/') || raw.contains('\\') || raw == "." || raw == ".." || raw.contains(".."))
      Consequence.argumentInvalid("application must not contain a path separator or traversal")
    else if (!normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
      Consequence.argumentInvalid("application must be a normalized segment")
    else
      Consequence.success(normalized)
  }
}

enum JobExperienceStatus {
  case Queued, Running, Paused, Cancelled, Completed, Failed
}

object JobExperienceStatus {
  def from(status: JobStatus): JobExperienceStatus = status match {
    case JobStatus.Submitted => JobExperienceStatus.Queued
    case JobStatus.Running => JobExperienceStatus.Running
    case JobStatus.Suspended => JobExperienceStatus.Paused
    case JobStatus.Cancelled => JobExperienceStatus.Cancelled
    case JobStatus.Succeeded => JobExperienceStatus.Completed
    case JobStatus.Failed => JobExperienceStatus.Failed
  }
}

final case class JobExperienceVocabulary(
  status: String,
  nextStep: String
)

object JobExperienceVocabulary {
  def resolve(status: JobStatus, locale: Locale): JobExperienceVocabulary = {
    val japanese = Option(locale).exists(_.getLanguage.equalsIgnoreCase("ja"))
    val display = JobExperienceStatus.from(status)
    val english = display match {
      case JobExperienceStatus.Queued => JobExperienceVocabulary("Queued", "Wait or refresh")
      case JobExperienceStatus.Running => JobExperienceVocabulary("Running", "Wait or refresh")
      case JobExperienceStatus.Paused => JobExperienceVocabulary("Paused", "Resume when ready")
      case JobExperienceStatus.Cancelled => JobExperienceVocabulary("Cancelled", "Contact an operator or retry when admitted")
      case JobExperienceStatus.Completed => JobExperienceVocabulary("Completed", "View result")
      case JobExperienceStatus.Failed => JobExperienceVocabulary("Failed", "Contact an operator or retry when admitted")
    }
    if (!japanese) english
    else display match {
      case JobExperienceStatus.Queued => JobExperienceVocabulary("待機中", "待機または更新")
      case JobExperienceStatus.Running => JobExperienceVocabulary("実行中", "待機または更新")
      case JobExperienceStatus.Paused => JobExperienceVocabulary("一時停止", "準備ができたら再開")
      case JobExperienceStatus.Cancelled => JobExperienceVocabulary("取消済み", "運用者に連絡するか、許可されていれば再試行")
      case JobExperienceStatus.Completed => JobExperienceVocabulary("完了", "結果を表示")
      case JobExperienceStatus.Failed => JobExperienceVocabulary("失敗", "運用者に連絡するか、許可されていれば再試行")
    }
  }
}

enum JobExperienceResult {
  case Available(text: String)
  case Failed
  case Pending
  case Unavailable
  case UnavailableAfterRestart
}

final case class JobExperienceProgress(
  state: String,
  taskCount: Int
)

final case class JobExperienceEntry(
  jobId: JobId,
  canonicalStatus: JobStatus,
  displayStatus: JobExperienceStatus,
  vocabulary: JobExperienceVocabulary,
  persistence: JobPersistencePolicy,
  origin: JobDataOrigin,
  createdAt: Instant,
  updatedAt: Instant
)

final case class JobExperiencePage(
  entries: Vector[JobExperienceEntry],
  totalCount: Int,
  nextCursor: Option[JobManagementCursor]
)

final case class JobExperienceView(
  detail: JobManagementDetail,
  displayStatus: JobExperienceStatus,
  vocabulary: JobExperienceVocabulary,
  progress: JobExperienceProgress,
  result: JobExperienceResult,
  controls: Vector[JobControlCommand],
  refreshable: Boolean
)

final case class JobExperienceDiagnostics(
  jobId: JobId,
  tasks: JobTaskPage,
  timeline: JobTimelinePage
)

final case class JobExperienceQuery(
  scope: JobExperienceScope,
  persistentOnly: Boolean = false,
  status: Option[JobStatus] = None,
  origin: Option[JobDataOrigin] = None,
  limit: Int = 25,
  cursor: Option[JobManagementCursor] = None
) {
  def management: JobManagementQuery =
    JobManagementQuery(persistentOnly, status, origin, limit, cursor)
}
