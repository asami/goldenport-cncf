package org.goldenport.cncf.security

import org.goldenport.record.Record

/*
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityAccessRelation(
  entityField: String,
  subjectAttribute: String,
  accessKinds: Set[String] = Set("read", "search/list")
) {
  def allows(accessKind: String): Boolean =
    accessKinds.isEmpty || accessKinds.contains(_normalize_access_kind(accessKind))

  def matches(
    record: Record,
    subject: SecuritySubject
  ): Boolean =
    _record_values(record, entityField).exists { entityValue =>
      _subject_values(subject, subjectAttribute).exists(_same_value(_, entityValue))
    }

  private def _record_values(record: Record, field: String): Vector[String] =
    Vector(
      record.getString(field),
      record.getString(_snake(field))
    ).flatten.map(_.trim).filter(_.nonEmpty).distinct

  private def _subject_values(
    subject: SecuritySubject,
    attribute: String
  ): Vector[String] =
    attribute.trim match
      case "subjectId" | "subject.id" | "principalId" | "principal.id" =>
        Vector(subject.subjectId)
      case key =>
        subject.attributeValues(key).toVector

  private def _same_value(lhs: String, rhs: String): Boolean =
    lhs.trim == rhs.trim

  private def _snake(text: String): String =
    text.flatMap {
      case c if c.isUpper => "_" + c.toLower
      case c => c.toString
    }.stripPrefix("_")

  private def _normalize_access_kind(text: String): String =
    text.trim.toLowerCase(java.util.Locale.ROOT)
}

object EntityAccessRelation {
  def parse(text: String): Option[EntityAccessRelation] = {
    val parts = text.split(":", 2).toVector.map(_.trim).filter(_.nonEmpty)
    val (relation, kinds) =
      parts match
        case Vector(a, b) => (a, _access_kinds(b))
        case Vector(a) => (a, Set("read", "search/list"))
        case _ => ("", Set.empty[String])
    relation.split("=", 2).toVector.map(_.trim).filter(_.nonEmpty) match
      case Vector(entity, subject) =>
        Some(EntityAccessRelation(entity, _strip_subject_prefix(subject), kinds))
      case _ =>
        None
  }

  private def _access_kinds(text: String): Set[String] =
    text.split("[,|]").toSet.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)

  private def _strip_subject_prefix(text: String): String =
    text.stripPrefix("subject.").stripPrefix("principal.")
}
