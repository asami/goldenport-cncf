package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobManagementQuery(
  persistentOnly: Boolean = true,
  status: Option[JobStatus] = None,
  origin: Option[JobDataOrigin] = None,
  limit: Int = JobManagementQuery.DefaultLimit,
  cursor: Option[JobManagementCursor] = None
) {
  def validate: Consequence[Unit] =
    if (limit <= 0 || limit > JobManagementQuery.MaximumLimit)
      Consequence.operationInvalid(
        "job.management-query",
        s"limit must be in 1..${JobManagementQuery.MaximumLimit}"
      )
    else
      Consequence.unit

  private[job] def filterFingerprint: String =
    JobManagementCursor.fingerprint(
      s"persistentOnly=$persistentOnly|status=${status.map(_.toString).getOrElse("-")}|origin=${origin.map(_.toString).getOrElse("-")}"
    )

  private[job] def accepts(model: JobQueryReadModel): Boolean =
    (!persistentOnly || model.persistence == JobPersistencePolicy.Persistent) &&
      status.forall(_ == model.status) &&
      origin.forall(_ == model.origin)
}

object JobManagementQuery {
  val DefaultLimit: Int = 100
  val MaximumLimit: Int = 100
}

/** URL-safe, versioned, opaque continuation token for a management page. */
final case class JobManagementCursor private (value: String)

final case class JobManagementCursorPayload(
  version: String,
  filterFingerprint: String,
  callerVisibilityFingerprint: String,
  authorizedSnapshotFingerprint: String,
  nextOffset: Int
)

object JobManagementCursor {
  val Version: String = "jmq1"

  /** Wrap an opaque transport token without interpreting it. */
  def fromOpaque(value: String): JobManagementCursor = JobManagementCursor(value)

  def encode(
    filterFingerprint: String,
    callerVisibilityFingerprint: String,
    authorizedSnapshotFingerprint: String,
    nextOffset: Int
  ): JobManagementCursor = {
    val payload = Vector(
      Version,
      filterFingerprint,
      callerVisibilityFingerprint,
      authorizedSnapshotFingerprint,
      nextOffset.toString
    ).mkString("|")
    JobManagementCursor(Base64.getUrlEncoder.withoutPadding.encodeToString(payload.getBytes(StandardCharsets.UTF_8)))
  }

  def decode(cursor: JobManagementCursor): Consequence[JobManagementCursorPayload] =
    try {
      val parts = new String(
        Base64.getUrlDecoder.decode(cursor.value),
        StandardCharsets.UTF_8
      ).split("\\|", -1).toVector
      parts match {
        case Vector(version, filter, caller, snapshot, offset)
            if version == Version && _is_fingerprint(filter) && _is_fingerprint(caller) &&
              _is_fingerprint(snapshot) && _positive_offset(offset) =>
          Consequence.success(JobManagementCursorPayload(version, filter, caller, snapshot, offset.toInt))
        case Vector(version, _, _, _, _) if version != Version =>
          _invalid("unsupported cursor version")
        case _ =>
          _invalid("malformed cursor")
      }
    } catch {
      case _: IllegalArgumentException => _invalid("malformed cursor")
      case _: NumberFormatException => _invalid("malformed cursor")
    }

  private[job] def callerVisibilityFingerprint(ctx: ExecutionContext): String = {
    val security = ctx.security
    val canonical = Vector(
      security.principal.id.value,
      security.subjectKind.toString,
      security.level.value,
      security.session.flatMap(_.sessionId).getOrElse("-"),
      security.capabilities.map(_.name).toVector.sorted.mkString(",")
    ).mkString("|")
    fingerprint(canonical)
  }

  private[job] def snapshotFingerprint(models: Vector[JobQueryReadModel]): String =
    fingerprint(models.map { model =>
      Vector(
        model.jobId.print,
        model.status.toString,
        model.persistence.toString,
        model.origin.toString,
        model.createdAt.toString,
        model.updatedAt.toString,
        model.scheduledStartAt.map(_.toString).getOrElse("-")
      ).mkString("|")
    }.mkString("\n"))

  private[job] def fingerprint(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map("%02x".format(_))
      .mkString

  private def _is_fingerprint(value: String): Boolean =
    value.matches("[0-9a-f]{64}")

  private def _positive_offset(value: String): Boolean =
    value.matches("[1-9][0-9]*") &&
      scala.util.Try(value.toInt).toOption.exists(_ > 0)

  private def _invalid[A](detail: String): Consequence[A] =
    Consequence.operationInvalid("job.management-query.cursor", s"invalid cursor: $detail")
}

/** Deliberately bounded summary; it contains no subject, input, result, task, or timeline data. */
final case class JobManagementSummary(
  jobId: JobId,
  status: JobStatus,
  persistence: JobPersistencePolicy,
  origin: JobDataOrigin,
  createdAt: java.time.Instant,
  updatedAt: java.time.Instant,
  scheduledStartAt: Option[java.time.Instant]
)

object JobManagementSummary {
  private[job] def from(model: JobQueryReadModel): JobManagementSummary =
    JobManagementSummary(
      model.jobId,
      model.status,
      model.persistence,
      model.origin,
      model.createdAt,
      model.updatedAt,
      model.scheduledStartAt
    )
}

final case class JobManagementPage(
  entries: Vector[JobManagementSummary],
  totalCount: Int,
  nextCursor: Option[JobManagementCursor]
)

/** Deliberately bounded retry state for an authorized management detail. */
final case class JobManagementRetrySummary(
  kind: JobRetryKind,
  attemptCount: Int,
  maxAttempts: Int,
  nextRetryDueAt: Option[java.time.Instant],
  exhausted: Boolean,
  recoveryRequired: Boolean,
  deadLetter: Boolean,
  poison: Boolean
)

object JobManagementRetrySummary {
  private[job] def from(state: JobRetryState): JobManagementRetrySummary =
    JobManagementRetrySummary(
      state.kind,
      state.attemptCount,
      state.maxAttempts,
      state.nextRetryDueAt,
      state.exhausted,
      state.recoveryRequired,
      state.deadLetter,
      state.poison
    )
}

/** Summary-only exact-job management detail. */
final case class JobManagementDetail(
  summary: JobManagementSummary,
  retry: JobManagementRetrySummary,
  resultSummary: JobResultSummary,
  taskCount: Int,
  timelineCount: Int
)

/** Exact result availability without durable payload/result reconstruction. */
sealed trait JobManagementResult

object JobManagementResult {
  final case class Available(result: JobResult) extends JobManagementResult
  final case class Pending(summary: JobResultSummary) extends JobManagementResult
  final case class UnavailableAfterRestart(summary: JobResultSummary) extends JobManagementResult
}
