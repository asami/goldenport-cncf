package org.goldenport.cncf.usernotification

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.cncf.job.JobExperienceScope
import org.goldenport.observation.{Cause, Descriptor}

/*
 * Resolves provider-owned inbox behavior and enforces bounded public projection.
 * It deliberately owns neither inbox state nor provider cursor semantics.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object UserNotificationInboxRuntime {
  def list(
    base: ExecutionContext,
    application: Option[String],
    unreadOnly: Boolean,
    limit: Int,
    cursor: Option[String]
  ): Consequence[UserNotificationInboxViewPage] = {
    val now = base.clock.instant()
    given ExecutionContext = base
    _query(base, application, unreadOnly, limit, cursor).flatMap { query =>
      _provider(base).flatMap { provider =>
        _provider_call(provider.queryInbox(query)).flatMap { page =>
          _validate_page(page, query, now).map { _ =>
            UserNotificationInboxViewPage(page.entries.map(_view), page.totalCount, page.nextCursor)
          }
        }
      }
    }
  }

  def get(
    base: ExecutionContext,
    id: String,
    application: Option[String]
  ): Consequence[UserNotificationInboxView] = {
    val now = base.clock.instant()
    given ExecutionContext = base
    _query(base, application, unreadonly = false, limit = 1, cursor = None).flatMap { query =>
      _provider(base).flatMap { provider =>
        _provider_call(provider.getInboxEntry(id, query)).flatMap {
          case Some(entry) if _admitted_entry(entry, id, query, now) =>
            Consequence.success(_view(entry))
          case _ =>
            _not_found(id)
        }
      }
    }
  }

  def markRead(
    base: ExecutionContext,
    id: String,
    application: Option[String]
  ): Consequence[UserNotificationInboxReadResult] = {
    val initialnow = base.clock.instant()
    given ExecutionContext = base
    _query(base, application, unreadonly = false, limit = 1, cursor = None).flatMap { query =>
      _provider(base).flatMap { provider =>
        _provider_call(provider.getInboxEntry(id, query)).flatMap {
          case Some(existing) if _admitted_entry(existing, id, query, initialnow) =>
            _provider_call(provider.markInboxRead(id, query)).flatMap { result =>
              val completedat = base.clock.instant()
              _validate_read_result(result, existing, completedat).flatMap { _ =>
                if (existing.readAt.nonEmpty) {
                  if (!result.changed && result.readAt == existing.readAt) Consequence.success(result)
                  else _provider_failure()
                } else if (result.changed) {
                  if (result.readAt.exists(readat => !readat.isBefore(initialnow) && !readat.isAfter(completedat))) Consequence.success(result)
                  else _provider_failure()
                } else {
                  _provider_call(provider.getInboxEntry(id, query)).flatMap {
                    case Some(current) if _admitted_entry(current, id, query, completedat) &&
                      result.readAt.nonEmpty && current.readAt == result.readAt &&
                      current.recipientUserId == existing.recipientUserId &&
                      current.application == existing.application &&
                      current.createdAt == existing.createdAt &&
                      current.dedupeKey == existing.dedupeKey =>
                      Consequence.success(result)
                    case _ =>
                      _provider_failure()
                  }
                }
              }
            }
          case _ =>
            _not_found(id)
        }
      }
    }
  }

  def update(
    base: ExecutionContext,
    id: String,
    application: Option[String],
    update: UserNotificationInboxUpdate
  ): Consequence[UserNotificationInboxView] = {
    val now = base.clock.instant()
    given ExecutionContext = base
    if (!base.security.hasAnyCapability(Set("notification_admin", "content_admin"))) {
      Consequence.operationIllegal("user-notification.inbox.update", "notification_admin or content_admin is required")
    } else {
      _validate_update(update, now).flatMap { _ =>
        _query(base, application, unreadonly = false, limit = 1, cursor = None).flatMap { query =>
          _provider(base).flatMap { provider =>
            _provider_call(provider.getInboxEntry(id, query)).flatMap {
              case Some(existing) if _admitted_entry(existing, id, query, now) =>
                _provider_call(provider.updateInboxEntry(id, query, update)).flatMap { entry =>
                  if (_valid_update_result(existing, entry, update, query, now)) Consequence.success(_view(entry))
                  else _provider_failure()
                }
              case _ =>
                _not_found(id)
            }
          }
        }
      }
    }
  }

  private def _query(
    base: ExecutionContext,
    application: Option[String],
    unreadonly: Boolean,
    limit: Int,
    cursor: Option[String]
  ): Consequence[UserNotificationInboxQuery] = {
    if (base.security.subjectKind == SubjectKind.Anonymous) {
      Consequence.operationNotFound("notification")
    } else {
      application.map(JobExperienceScope.normalizedApplication).getOrElse(Consequence.success(""))
        .flatMap { normalized =>
          val query = UserNotificationInboxQuery(
            recipientUserId = base.security.principal.id.value,
            application = Option.when(normalized.nonEmpty)(normalized),
            unreadOnly = unreadonly,
            limit = limit,
            cursor = cursor
          )
          query.validate.map(_ => query)
        }
    }
  }

  private def _provider(base: ExecutionContext): Consequence[UserNotificationInboxProvider] =
    UserNotificationProviderRuntime.providers(base)
      .collectFirst { case provider: UserNotificationInboxProvider => provider }
      .map(Consequence.success)
      .getOrElse(_provider_failure())

  private def _provider_call[A](operation: => Consequence[A]): Consequence[A] =
    try {
      operation match {
        case Consequence.Success(value) => Consequence.success(value)
        case Consequence.Failure(_) => _provider_failure()
      }
    } catch {
      case NonFatal(_) => _provider_failure()
    }

  private def _validate_page(
    page: UserNotificationInboxPage,
    query: UserNotificationInboxQuery,
    now: Instant
  ): Consequence[Unit] = {
    if (page.recipientUserId != query.recipientUserId || page.application != query.application || page.unreadOnly != query.unreadOnly) {
      _provider_failure()
    } else if (page.totalCount < 0 || page.entries.size > query.limit || page.entries.size > page.totalCount) {
      _provider_failure()
    } else if (!page.entries.forall(entry => _matches_query(entry, query) && _safe_entry(entry, now))) {
      _provider_failure()
    } else {
      Consequence.unit
    }
  }

  private def _admitted_entry(
    entry: UserNotificationInboxEntry,
    id: String,
    query: UserNotificationInboxQuery,
    now: Instant
  ): Boolean =
    entry.id == id && _matches_query(entry, query) && _safe_entry(entry, now)

  private def _matches_query(
    entry: UserNotificationInboxEntry,
    query: UserNotificationInboxQuery
  ): Boolean =
    entry.recipientUserId == query.recipientUserId &&
      query.application.forall(application => entry.application.contains(application)) &&
      (!query.unreadOnly || entry.readAt.isEmpty)

  private def _safe_entry(entry: UserNotificationInboxEntry, now: Instant): Boolean =
    _safe_id(entry.id) &&
      entry.title.length <= 256 &&
      !entry.title.exists(Character.isISOControl) &&
      entry.body.length <= 4096 &&
      !entry.body.exists(Character.isISOControl) &&
      _safe_status(entry.status) &&
      entry.application.forall(value => JobExperienceScope.normalizedApplication(value).toOption.contains(value)) &&
      !entry.updatedAt.isBefore(entry.createdAt) &&
      entry.readAt.forall(readat => !readat.isBefore(entry.createdAt) && !readat.isAfter(now)) &&
      !_expired(entry, now) &&
      _safe_link(entry.actionUrl) &&
      entry.metadata.size <= 32 && entry.metadata.forall { case (key, value) =>
        key.length <= 64 && value.length <= 256 && !key.exists(Character.isISOControl) && !value.exists(Character.isISOControl)
      }

  private def _validate_read_result(
    result: UserNotificationInboxReadResult,
    entry: UserNotificationInboxEntry,
    now: Instant
  ): Consequence[Unit] =
    if (result.id != entry.id || result.recipientUserId != entry.recipientUserId ||
      result.application != entry.application || result.readAt.isEmpty ||
      result.readAt.exists(readat => readat.isBefore(entry.createdAt) || readat.isAfter(now))) _provider_failure()
    else Consequence.unit

  private def _validate_update(
    update: UserNotificationInboxUpdate,
    now: Instant
  ): Consequence[Unit] = {
    val safe = update.title.forall(value => value.trim.nonEmpty && value.length <= 256 && !value.exists(Character.isISOControl)) &&
      update.body.forall(value => value.length <= 4096 && !value.exists(Character.isISOControl)) &&
      update.status.forall(_safe_status) &&
      update.actionUrl.forall(value => _safe_link(Some(value))) &&
      update.expiresAt.forall(!_.isBefore(now)) &&
      update.metadata.forall(_.size <= 32) && update.metadata.forall(_.forall { case (key, value) =>
        key.length <= 64 && value.length <= 256 && !key.exists(Character.isISOControl) && !value.exists(Character.isISOControl)
      })
    if (safe) Consequence.unit else Consequence.argumentInvalid("invalid notification update")
  }

  private def _valid_update_result(
    existing: UserNotificationInboxEntry,
    entry: UserNotificationInboxEntry,
    update: UserNotificationInboxUpdate,
    query: UserNotificationInboxQuery,
    now: Instant
  ): Boolean =
    _admitted_entry(entry, existing.id, query, now) &&
      entry.id == existing.id &&
      entry.recipientUserId == existing.recipientUserId &&
      entry.application == existing.application &&
      entry.createdAt == existing.createdAt &&
      entry.dedupeKey == existing.dedupeKey &&
      entry.readAt == existing.readAt &&
      !entry.updatedAt.isBefore(existing.updatedAt) &&
      update.title.fold(entry.title == existing.title)(_ == entry.title) &&
      update.body.fold(entry.body == existing.body)(_ == entry.body) &&
      update.status.fold(entry.status == existing.status)(_ == entry.status) &&
      update.actionUrl.fold(entry.actionUrl == existing.actionUrl)(value => entry.actionUrl.contains(value)) &&
      update.expiresAt.fold(entry.expiresAt == existing.expiresAt)(value => entry.expiresAt.contains(value)) &&
      update.metadata.fold(entry.metadata == existing.metadata)(_ == entry.metadata)

  private def _expired(entry: UserNotificationInboxEntry, now: Instant): Boolean =
    entry.expiresAt.exists(!_.isAfter(now))

  private def _safe_id(value: String): Boolean =
    Option(value).exists(_.trim.nonEmpty) && value.length <= 256 && !value.exists(Character.isISOControl)

  private def _safe_status(value: String): Boolean =
    Set("active", "inactive", "queued", "read").contains(Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT))

  private def _safe_link(value: Option[String]): Boolean =
    value.forall(_safe_url)

  private def _safe_url(value: String): Boolean =
    UserNotificationLinkPolicy.isSafe(value)

  private def _view(entry: UserNotificationInboxEntry): UserNotificationInboxView =
    UserNotificationInboxView(
      entry.id,
      entry.application,
      entry.title,
      entry.body,
      entry.createdAt,
      entry.updatedAt,
      entry.readAt,
      entry.expiresAt,
      entry.actionUrl,
      entry.status
    )

  private def _not_found[A](id: String): Consequence[A] =
    Consequence.operationNotFound(s"notification:$id")

  private def _provider_failure[A](): Consequence[A] =
    Consequence.serviceUnavailable(
      "User-notification inbox is unavailable.",
      Cause.Kind.Capability,
      Seq(Descriptor.Facet.Service("user-notification"), Descriptor.Facet.State("provider-unavailable"))
    )
}
