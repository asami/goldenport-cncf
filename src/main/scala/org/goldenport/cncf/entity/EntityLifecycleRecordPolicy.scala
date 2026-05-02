package org.goldenport.cncf.entity

import java.util.Locale
import org.goldenport.record.Record

/*
 * @since   May. 2, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
object EntityLifecycleRecordPolicy {
  def isLogicallyDeleted(record: Record): Boolean =
    SimpleEntityStorageShapePolicy.value(record, "deletedAt").exists(_is_present)

  def filterNotLogicallyDeleted(records: Vector[Record]): Vector[Record] =
    records.filterNot(isLogicallyDeleted)

  def postStatusToken(record: Record): Option[String] =
    SimpleEntityStorageShapePolicy.value(record, "postStatus").flatMap(postStatusToken)

  def alivenessToken(record: Record): Option[String] =
    SimpleEntityStorageShapePolicy.value(record, "aliveness").flatMap(alivenessToken)

  def postStatusToken(value: Any): Option[String] = {
    val s = _normalized(value)
    if (s.contains("published"))
      Some("published")
    else if (s.contains("draft"))
      Some("draft")
    else if (s.contains("archived"))
      Some("archived")
    else
      None
  }

  def alivenessToken(value: Any): Option[String] = {
    val s = _normalized(value)
    if (s.contains("alive"))
      Some("alive")
    else if (s.contains("suspended"))
      Some("suspended")
    else if (s.contains("dead"))
      Some("dead")
    else
      None
  }

  private def _normalized(value: Any): String =
    value.toString.trim.toLowerCase(Locale.ROOT)

  private def _is_present(value: Any): Boolean =
    value != null && value.toString.trim.nonEmpty
}
