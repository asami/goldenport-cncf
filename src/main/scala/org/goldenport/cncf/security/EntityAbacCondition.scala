package org.goldenport.cncf.security

import org.goldenport.datatype.PathName
import org.goldenport.record.Record

/*
 * Natural ABAC condition for entity authorization.
 *
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityAbacCondition(
  entityAttribute: String,
  expected: EntityAbacCondition.Value,
  accessKinds: Set[String] = Set.empty
) {
  def allows(accessKind: String): Boolean =
    accessKinds.isEmpty || accessKinds.contains(EntityAbacCondition.normalize(accessKind))

  def matches(
    record: Record,
    subject: SecuritySubject
  ): Boolean =
    EntityAbacCondition.recordValue(record, entityAttribute)
      .orElse(EntityAbacCondition.defaultRecordValue(entityAttribute))
      .exists { actual =>
      expected.resolve(subject).exists(_ == EntityAbacCondition.normalizeValue(actual))
    }
}

object EntityAbacCondition {
  sealed trait Value {
    def resolve(subject: SecuritySubject): Option[String]
  }
  object Value {
    final case class Literal(value: String) extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        Some(normalizeValue(value))
    }
    final case class SubjectAttribute(name: String) extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        subject.attributeValues(name).headOption.map(normalizeValue)
    }
    case object SubjectId extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        Some(normalizeValue(subject.subjectId))
    }
  }

  def parse(text: String): Option[EntityAbacCondition] = {
    val parts = Option(text).getOrElse("").split(":", 2).map(_.trim)
    val expr = parts.headOption.getOrElse("")
    val accessKinds =
      parts.drop(1).headOption.toSet.flatMap(_.split("[,|]")).map(normalize).filter(_.nonEmpty)
    expr.split("=", 2).map(_.trim) match {
      case Array(left, right) if left.nonEmpty && right.nonEmpty =>
        Some(EntityAbacCondition(left, _value(right), accessKinds))
      case _ =>
        None
    }
  }

  def parseList(text: String): Vector[EntityAbacCondition] =
    Option(text).getOrElse("").split("[;\\n]").toVector.flatMap(parse)

  def recordValue(record: Record, name: String): Option[String] = {
    val keys = _aliases(name)
    keys.view
      .flatMap(k => record.getString(k).orElse(record.getString(PathName(k.split("\\.").toVector))))
      .headOption
      .map(normalizeValue)
  }

  def defaultRecordValue(name: String): Option[String] =
    normalize(name) match
      case "poststatus" | "post_status" => Some("Published")
      case "aliveness" => Some("Alive")
      case _ => None

  def normalize(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)

  def normalizeValue(value: String): String =
    Option(value).getOrElse("").trim

  private def _value(text: String): Value =
    if (text.equalsIgnoreCase("subject.id") || text.equalsIgnoreCase("principal.id"))
      Value.SubjectId
    else if (text.toLowerCase(java.util.Locale.ROOT).startsWith("subject."))
      Value.SubjectAttribute(text.drop("subject.".length))
    else
      Value.Literal(text)

  private def _aliases(name: String): Vector[String] = {
    val n = Option(name).getOrElse("").trim
    val snake = SecuritySubject.snake(n)
    Vector(n, snake).filter(_.nonEmpty).distinct
  }
}
