package org.goldenport.cncf.security

import java.time.{Instant, ZonedDateTime}
import scala.util.Try
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
  operator: EntityAbacCondition.Operator,
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
      expected.resolve(subject).exists(operator.matches(actual, _))
    }
}

object EntityAbacCondition {
  def apply(
    entityAttribute: String,
    expected: EntityAbacCondition.Value,
    accessKinds: Set[String]
  ): EntityAbacCondition =
    EntityAbacCondition(entityAttribute, Operator.Eq, expected, accessKinds)

  def apply(
    entityAttribute: String,
    expected: EntityAbacCondition.Value
  ): EntityAbacCondition =
    EntityAbacCondition(entityAttribute, Operator.Eq, expected, Set.empty)

  sealed trait Operator {
    def symbol: String
    def matches(actual: String, expected: String): Boolean
  }
  object Operator {
    case object Eq extends Operator {
      val symbol = "="
      def matches(actual: String, expected: String): Boolean =
        normalizeValue(actual) == normalizeValue(expected)
    }
    case object Lt extends Operator {
      val symbol = "<"
      def matches(actual: String, expected: String): Boolean =
        _compare(actual, expected).exists(_ < 0)
    }
    case object Lte extends Operator {
      val symbol = "<="
      def matches(actual: String, expected: String): Boolean =
        _compare(actual, expected).exists(_ <= 0)
    }
    case object Gt extends Operator {
      val symbol = ">"
      def matches(actual: String, expected: String): Boolean =
        _compare(actual, expected).exists(_ > 0)
    }
    case object Gte extends Operator {
      val symbol = ">="
      def matches(actual: String, expected: String): Boolean =
        _compare(actual, expected).exists(_ >= 0)
    }
  }

  sealed trait Value {
    def resolve(subject: SecuritySubject): Option[String]
  }
  object Value {
    final case class Literal(value: String) extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        Some(rawValue(value))
    }
    final case class SubjectAttribute(name: String) extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        subject.attributeValues(name).headOption.map(rawValue)
    }
    case object SubjectId extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        Some(rawValue(subject.subjectId))
    }
    case object Now extends Value {
      def resolve(subject: SecuritySubject): Option[String] =
        Some(Instant.now.toString)
    }
  }

  def parse(text: String): Option[EntityAbacCondition] = {
    val parts = Option(text).getOrElse("").split(":", 2).map(_.trim)
    val expr = parts.headOption.getOrElse("")
    val accessKinds =
      parts.drop(1).headOption.toSet.flatMap(_.split("[,|]")).map(normalize).filter(_.nonEmpty)
    _split_expr(expr).map { case (left, op, right) =>
      EntityAbacCondition(left, op, _value(right), accessKinds)
    }
  }

  def parseList(text: String): Vector[EntityAbacCondition] =
    Option(text).getOrElse("").split("[;\\n]").toVector.flatMap(parse)

  def recordValue(record: Record, name: String): Option[String] = {
    val keys = _aliases(name)
    keys.view
      .flatMap(k => record.getString(k).orElse(record.getString(PathName(k.split("\\.").toVector))))
      .headOption
      .map(rawValue)
  }

  def defaultRecordValue(name: String): Option[String] =
    normalize(name) match
      case "poststatus" | "post_status" => Some("Published")
      case "aliveness" => Some("Alive")
      case _ => None

  def normalize(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)

  def normalizeValue(value: String): String =
    normalize(value)

  def rawValue(value: String): String =
    Option(value).getOrElse("").trim

  private def _value(text: String): Value =
    if (text.equalsIgnoreCase("subject.id") || text.equalsIgnoreCase("principal.id"))
      Value.SubjectId
    else if (text.equalsIgnoreCase("now"))
      Value.Now
    else if (text.toLowerCase(java.util.Locale.ROOT).startsWith("subject."))
      Value.SubjectAttribute(text.drop("subject.".length))
    else
      Value.Literal(text)

  private def _split_expr(
    expr: String
  ): Option[(String, Operator, String)] = {
    val operators = Vector(
      "<=" -> Operator.Lte,
      ">=" -> Operator.Gte,
      "=" -> Operator.Eq,
      "<" -> Operator.Lt,
      ">" -> Operator.Gt
    )
    operators.iterator.flatMap {
      case (symbol, op) if expr.contains(symbol) =>
        expr.split(java.util.regex.Pattern.quote(symbol), 2).map(_.trim) match {
          case Array(left, right) if left.nonEmpty && right.nonEmpty =>
            Some((left, op, right))
          case _ =>
            None
        }
      case _ =>
        None
    }.toVector.headOption
  }

  private def _compare(
    actual: String,
    expected: String
  ): Option[Int] =
    (_instant(actual), _instant(expected)) match
      case (Some(a), Some(e)) => Some(a.compareTo(e))
      case _ =>
        val a = normalizeValue(actual)
        val e = normalizeValue(expected)
        if (a.nonEmpty && e.nonEmpty)
          Some(a.compareTo(e))
        else
          None

  private def _instant(text: String): Option[Instant] = {
    val s = Option(text).getOrElse("").trim
    if (s.isEmpty)
      None
    else
      Try(Instant.parse(s)).toOption
        .orElse(Try(ZonedDateTime.parse(s).toInstant).toOption)
  }

  private def _aliases(name: String): Vector[String] = {
    val n = Option(name).getOrElse("").trim
    val snake = SecuritySubject.snake(n)
    Vector(n, snake).filter(_.nonEmpty).distinct
  }
}
