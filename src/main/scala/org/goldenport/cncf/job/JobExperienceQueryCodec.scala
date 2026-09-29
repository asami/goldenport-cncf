package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * Shared strict query parsing for protocol and server-rendered Job experience.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object JobExperienceQueryCodec {
  final case class NotificationQuery(
    application: Option[String],
    unreadOnly: Boolean,
    limit: Int,
    cursor: Option[String]
  )

  def parse(
    scope: Option[String],
    application: Option[String],
    persistentOnly: Option[String],
    status: Option[String],
    origin: Option[String],
    limit: Option[String],
    cursor: Option[String],
    forcedScope: Option[String] = None
  ): Consequence[JobExperienceQuery] =
    for {
      resolvedscope <- JobExperienceScope.parse(forcedScope.orElse(scope), application)
      persistent <- _boolean(persistentOnly, default = false, "persistentOnly")
      resolvedstatus <- _status(status)
      resolvedorigin <- _origin(origin)
      resolvedlimit <- _int(limit, default = 25, "limit")
      _ <- if (resolvedlimit >= 1 && resolvedlimit <= JobManagementQuery.MaximumLimit) Consequence.unit else Consequence.argumentInvalid("limit must be in 1..100")
    } yield JobExperienceQuery(
      resolvedscope,
      persistent,
      resolvedstatus,
      resolvedorigin,
      resolvedlimit,
      cursor.filter(_.trim.nonEmpty).map(JobManagementCursor.fromOpaque)
    )

  def parseScope(scope: Option[String], application: Option[String]): Consequence[JobExperienceScope] =
    JobExperienceScope.parse(scope, application)

  def parseNotifications(
    application: Option[String],
    unreadOnly: Option[String],
    limit: Option[String],
    cursor: Option[String]
  ): Consequence[NotificationQuery] =
    for {
      normalized <- application.map(JobExperienceScope.normalizedApplication).getOrElse(Consequence.success(""))
      unread <- _boolean(unreadOnly, default = false, "unreadOnly")
      bounded <- _int(limit, default = 25, "limit")
      _ <- if (bounded >= 1 && bounded <= 100) Consequence.unit else Consequence.argumentInvalid("limit must be in 1..100")
    } yield NotificationQuery(Option.when(normalized.nonEmpty)(normalized), unread, bounded, cursor.filter(_.trim.nonEmpty))

  private def _boolean(value: Option[String], default: Boolean, name: String): Consequence[Boolean] =
    value.map(_.trim.toLowerCase) match {
      case None | Some("") => Consequence.success(default)
      case Some("true") => Consequence.success(true)
      case Some("false") => Consequence.success(false)
      case _ => Consequence.argumentInvalid(s"$name must be true or false")
    }

  private def _int(value: Option[String], default: Int, name: String): Consequence[Int] =
    value.map(_.trim).filter(_.nonEmpty) match {
      case None => Consequence.success(default)
      case Some(text) => text.toIntOption.map(Consequence.success).getOrElse(Consequence.argumentInvalid(s"$name must be an integer"))
    }

  private def _status(value: Option[String]): Consequence[Option[JobStatus]] =
    value.map(_.trim).filter(_.nonEmpty) match {
      case None => Consequence.success(None)
      case Some(text) => Vector(JobStatus.Submitted, JobStatus.Running, JobStatus.Suspended, JobStatus.Cancelled, JobStatus.Succeeded, JobStatus.Failed)
        .find(_.toString.equalsIgnoreCase(text))
        .map(status => Consequence.success(Some(status)))
        .getOrElse(Consequence.argumentInvalid("invalid status"))
    }

  private def _origin(value: Option[String]): Consequence[Option[JobDataOrigin]] =
    value.map(_.trim).filter(_.nonEmpty) match {
      case None => Consequence.success(None)
      case Some(text) => JobDataOrigin.values.find(_.toString.equalsIgnoreCase(text))
        .map(origin => Consequence.success(Some(origin)))
        .getOrElse(Consequence.argumentInvalid("invalid origin"))
    }
}
