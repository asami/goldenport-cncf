package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityRevision

/*
 * Transport representation for framework-managed Entity revisions.
 *
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object EntityRevisionTransport {
  val observedRevisionPropertyName: String =
    "textus.entity.mutation.observed-revision"

  val ifMatchHeaderName: String =
    "If-Match"

  def entityTag(revision: EntityRevision): String =
    s""""revision-${revision.value}""""

  def parseEntityTagC(value: String): Consequence[EntityRevision] = {
    val normalized = Option(value).map(_.trim).getOrElse("")
    val prefix = "\"revision-"
    if (
      normalized.startsWith(prefix) &&
      normalized.endsWith("\"") &&
      normalized.length > prefix.length + 1
    )
      EntityRevision.createC(
        normalized.substring(prefix.length, normalized.length - 1)
      )
    else
      Consequence.argumentFormatError(
        ifMatchHeaderName,
        "\"revision-N\"",
        value
      )
  }
}
