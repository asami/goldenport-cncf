package org.goldenport.cncf.id

import org.goldenport.Consequence
import org.goldenport.text.Presentable

/*
 * Textus URN is the stable in-document identity form used alongside
 * UniversalId and shortid. Persistent storage keys remain EntityId values.
 *
 * @since   May.  3, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TextusUrn(
  kind: String,
  value: String
) extends Presentable {
  def print: String =
    s"urn:textus:${kind}:${value}"
}

object TextusUrn {
  val BlobKind: String = "blob"

  def blob(value: String): TextusUrn =
    TextusUrn(BlobKind, value)

  def parse(value: String): Consequence[TextusUrn] =
    parseOption(value)
      .map(Consequence.success)
      .getOrElse(Consequence.argumentInvalid(s"invalid Textus URN: ${value}"))

  def parseOption(value: String): Option[TextusUrn] = {
    val trimmed = Option(value).map(_.trim).getOrElse("")
    val prefix = "urn:textus:"
    if (!trimmed.startsWith(prefix))
      None
    else {
      val rest = trimmed.substring(prefix.length)
      val idx = rest.indexOf(':')
      if (idx <= 0 || idx >= rest.length - 1)
        None
      else {
        val kind = rest.substring(0, idx)
        val data = rest.substring(idx + 1)
        if (_safe(kind) && _safeValue(data))
          Some(TextusUrn(kind, data))
        else
          None
      }
    }
  }

  def isTextusUrn(value: String): Boolean =
    parseOption(value).isDefined

  private def _safe(value: String): Boolean =
    value.nonEmpty && value.forall(ch => ch.isLetterOrDigit || ch == '_' || ch == '-')

  private def _safeValue(value: String): Boolean =
    value.nonEmpty && !value.exists(ch => Character.isISOControl(ch) || Character.isWhitespace(ch))
}
