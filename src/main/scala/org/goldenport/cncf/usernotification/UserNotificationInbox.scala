package org.goldenport.cncf.usernotification

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.job.JobExperienceScope

/*
 * Additive provider-owned inbox SPI. CNCF validates and projects records but
 * never owns notification persistence, deduplication, or opaque cursors.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final case class UserNotificationInboxQuery(
  recipientUserId: String,
  application: Option[String] = None,
  unreadOnly: Boolean = false,
  limit: Int = 25,
  cursor: Option[String] = None
) {
  def validate: Consequence[Unit] =
    if (recipientUserId.trim.isEmpty)
      Consequence.argumentInvalid("recipient is required")
    else if (limit < 1 || limit > 100)
      Consequence.argumentInvalid("limit must be in 1..100")
    else
      Consequence.unit
}

final case class UserNotificationInboxEntry(
  id: String,
  recipientUserId: String,
  application: Option[String],
  title: String,
  body: String,
  createdAt: Instant,
  updatedAt: Instant,
  readAt: Option[Instant],
  expiresAt: Option[Instant],
  dedupeKey: String,
  actionUrl: Option[String],
  metadata: Map[String, String] = Map.empty,
  status: String = "active"
)

final case class UserNotificationInboxPage(
  recipientUserId: String,
  application: Option[String],
  unreadOnly: Boolean,
  entries: Vector[UserNotificationInboxEntry],
  totalCount: Int,
  nextCursor: Option[String]
)

final case class UserNotificationInboxView(
  id: String,
  application: Option[String],
  title: String,
  body: String,
  createdAt: Instant,
  updatedAt: Instant,
  readAt: Option[Instant],
  expiresAt: Option[Instant],
  actionUrl: Option[String],
  status: String
)

final case class UserNotificationInboxViewPage(
  entries: Vector[UserNotificationInboxView],
  totalCount: Int,
  nextCursor: Option[String]
)

final case class UserNotificationInboxReadResult(
  id: String,
  recipientUserId: String,
  application: Option[String],
  readAt: Option[Instant],
  changed: Boolean
)

final case class UserNotificationInboxUpdate(
  title: Option[String] = None,
  body: Option[String] = None,
  status: Option[String] = None,
  actionUrl: Option[String] = None,
  expiresAt: Option[Instant] = None,
  metadata: Option[Map[String, String]] = None
)

trait UserNotificationInboxProvider extends UserNotificationProvider {
  def queryInbox(query: UserNotificationInboxQuery)(using org.goldenport.cncf.context.ExecutionContext): Consequence[UserNotificationInboxPage]
  def getInboxEntry(id: String, query: UserNotificationInboxQuery)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Option[UserNotificationInboxEntry]]
  def markInboxRead(id: String, query: UserNotificationInboxQuery)(using org.goldenport.cncf.context.ExecutionContext): Consequence[UserNotificationInboxReadResult]
  def updateInboxEntry(id: String, query: UserNotificationInboxQuery, update: UserNotificationInboxUpdate)(using org.goldenport.cncf.context.ExecutionContext): Consequence[UserNotificationInboxEntry]
}

/*
 * Admission for provider-owned notification links. The original text remains
 * the public reference; only its path is decoded for traversal inspection.
 *
 * @author  ASAMI, Tomoharu
 */
private[usernotification] object UserNotificationLinkPolicy {
  private val _encoded_slash_or_control = "(?i)%(?:2f|5c|0[0-9a-f]|1[0-9a-f]|7f)".r
  private val _remaining_encoded_danger = "(?i)%(?:25)*(?:2e|2f|5c|0[0-9a-f]|1[0-9a-f]|7f)".r

  def isSafe(value: String): Boolean = {
    val raw = Option(value).getOrElse("")
    if (raw.isEmpty || raw.length > 4096 || raw != raw.trim || raw.exists(Character.isISOControl) || raw.contains('\\')) {
      false
    } else {
      try {
        val uri = new URI(raw)
        val path = Option(uri.getRawPath).getOrElse("")
        uri.getScheme == null && uri.getAuthority == null && !uri.isOpaque &&
          path.startsWith("/") && !path.startsWith("//") && _safe_path(path)
      } catch {
        case NonFatal(_) => false
      }
    }
  }

  private def _safe_path(rawpath: String): Boolean = {
    if (rawpath.isEmpty || rawpath.startsWith("//") || rawpath.contains('\\') || rawpath.exists(Character.isISOControl)) {
      false
    } else {
      var current = rawpath
      var rounds = 0
      var admitted = true
      while (admitted && rounds < 3) {
        if (_encoded_slash_or_control.findFirstIn(current).nonEmpty || !_safe_decoded_path(current)) {
          admitted = false
        } else {
          _decode(current) match {
            case Some(next) =>
              val unchanged = next == current
              current = next
              rounds += 1
              if (unchanged && rounds < 3) rounds = 3
            case None =>
              admitted = false
          }
        }
      }
      admitted && _safe_decoded_path(current) && _remaining_encoded_danger.findFirstIn(current).isEmpty
    }
  }

  private def _safe_decoded_path(path: String): Boolean =
    path.startsWith("/") && !path.startsWith("//") &&
      !path.contains('\\') && !path.exists(Character.isISOControl) &&
      path.split("/", -1).forall(segment => segment != "." && segment != "..")

  private def _decode(value: String): Option[String] =
    try {
      val escaped = new StringBuilder
      var index = 0
      while (index < value.length) {
        val character = value.charAt(index)
        if (character == '+') {
          escaped.append("%2B")
        } else if (character == '%' && !_percent_escape(value, index)) {
          escaped.append("%25")
        } else {
          escaped.append(character)
        }
        index += 1
      }
      Some(URLDecoder.decode(escaped.result(), StandardCharsets.UTF_8))
    } catch {
      case NonFatal(_) => None
    }

  private def _percent_escape(value: String, index: Int): Boolean =
    index + 2 < value.length && Character.digit(value.charAt(index + 1), 16) >= 0 && Character.digit(value.charAt(index + 2), 16) >= 0
}
