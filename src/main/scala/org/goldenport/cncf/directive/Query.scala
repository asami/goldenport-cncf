package org.goldenport.cncf.directive

import java.time.Instant
import org.simplemodeling.model.directive.Condition

/*
 * @since   Feb. 19, 2026
 * @version Mar. 23, 2026
 * @author  ASAMI, Tomoharu
 */
case class Query[T](query: T) {
  def matches[A](value: A): Boolean =
    Query.matches(this, value)

  def where: Query.Expr =
    Query.whereOf(this)

  def sort: Vector[Query.SortKey] =
    Query.sortOf(this)

  def limit: Option[Int] =
    Query.limitOf(this)

  def offset: Option[Int] =
    Query.offsetOf(this)

  def completeSql(
    tables: Iterable[(String, String)]
  )(
    f: Query.SqlNames => String
  ): String =
    Query.completeSql(tables)(f)
}

object Query {
  sealed trait Expr

  case object True extends Expr
  case object False extends Expr
  final case class And(items: Vector[Expr]) extends Expr
  final case class Or(items: Vector[Expr]) extends Expr
  final case class Not(item: Expr) extends Expr
  final case class FieldCondition(path: String, condition: Condition[Any]) extends Expr

  final case class Eq(path: String, value: Any) extends Expr
  final case class Ne(path: String, value: Any) extends Expr
  final case class Gt(path: String, value: Any) extends Expr
  final case class Gte(path: String, value: Any) extends Expr
  final case class Lt(path: String, value: Any) extends Expr
  final case class Lte(path: String, value: Any) extends Expr
  final case class In(path: String, values: Vector[Any]) extends Expr
  final case class NotIn(path: String, values: Vector[Any]) extends Expr
  final case class IsNull(path: String) extends Expr
  final case class IsNotNull(path: String) extends Expr
  final case class Like(path: String, pattern: String, caseInsensitive: Boolean = false) extends Expr
  final case class StartsWith(path: String, value: String, caseInsensitive: Boolean = false) extends Expr
  final case class EndsWith(path: String, value: String, caseInsensitive: Boolean = false) extends Expr
  final case class Contains(path: String, value: String, caseInsensitive: Boolean = false) extends Expr

  enum SortDirection {
    case Asc
    case Desc
  }

  final case class SortKey(
    path: String,
    direction: SortDirection = SortDirection.Asc
  )

  // SQL-like query directives (where/sort/limit/offset).
  // Keep Query[T](query) shape for backward compatibility.
  final case class Plan[T](
    condition: T,
    where: Expr = True,
    sort: Vector[SortKey] = Vector.empty,
    limit: Option[Int] = None,
    offset: Option[Int] = None
  )

  final case class SqlTableName(
    entityName: String,
    tableName: String
  )

  final class SqlNames private[directive] (
    private val _table_map: Map[String, String]
  ) {
    def table(entityName: String): String =
      _table_map.getOrElse(entityName, entityName)

    def tableOption(entityName: String): Option[String] =
      _table_map.get(entityName)

    def qualify(entityName: String, columnName: String): String =
      s"${table(entityName)}.$columnName"
  }

  // Marker trait for Cozy-generated query condition objects:
  // case class Person(...) extends Query.ConditionShape
  trait ConditionShape extends Product

  def plan[T](
    condition: T,
    where: Expr = True,
    sort: Vector[SortKey] = Vector.empty,
    limit: Option[Int] = None,
    offset: Option[Int] = None
  ): Query[Plan[T]] =
    Query(Plan(condition, where, sort, limit, offset))

  def completeSql(
    tables: Iterable[(String, String)]
  )(
    f: SqlNames => String
  ): String = {
    val names = new SqlNames(tables.toMap)
    f(names)
  }

  def completeSqlWithNames(
    tables: Iterable[SqlTableName]
  )(
    f: SqlNames => String
  ): String =
    completeSql(tables.map(x => x.entityName -> x.tableName))(f)

  def matches[A](
    query: Query[?],
    value: A
  ): Boolean =
    eval(whereOf(query), value)

  def matches[A](
    condition: Any,
    value: A
  ): Boolean =
    eval(toExpr(condition), value)

  def whereOf(query: Query[?]): Expr =
    query.query match {
      case p: Plan[?] if p.where != True => p.where
      case p: Plan[?] => toExpr(p.condition)
      case other => toExpr(other)
    }

  def sortOf(query: Query[?]): Vector[SortKey] =
    query.query match {
      case p: Plan[?] => p.sort
      case _ => Vector.empty
    }

  def limitOf(query: Query[?]): Option[Int] =
    query.query match {
      case p: Plan[?] => p.limit.filter(_ >= 0)
      case _ => None
    }

  def offsetOf(query: Query[?]): Option[Int] =
    query.query match {
      case p: Plan[?] => p.offset.filter(_ >= 0)
      case _ => None
    }

  def toExpr(condition: Any): Expr =
    condition match {
      case expr: Expr => expr
      case p: Plan[?] => if (p.where != True) p.where else toExpr(p.condition)
      case cond: Condition[Any @unchecked] => FieldCondition("", cond)
      case shape: Product => _expr_from_shape(shape)
      case literal => Eq("", literal)
    }

  def eval(entityExpr: Expr, value: Any): Boolean =
    entityExpr match {
      case True => true
      case False => false
      case And(items) => items.forall(eval(_, value))
      case Or(items) => items.exists(eval(_, value))
      case Not(item) => !eval(item, value)
      case FieldCondition(path, cond) =>
        _extract(value, path).exists(cond.accepts)
      case Eq(path, expected) =>
        _extract(value, path).contains(expected)
      case Ne(path, expected) =>
        !_extract(value, path).contains(expected)
      case Gt(path, expected) =>
        _compare(path, value, expected).exists(_ > 0)
      case Gte(path, expected) =>
        _compare(path, value, expected).exists(_ >= 0)
      case Lt(path, expected) =>
        _compare(path, value, expected).exists(_ < 0)
      case Lte(path, expected) =>
        _compare(path, value, expected).exists(_ <= 0)
      case In(path, candidates) =>
        _extract(value, path).exists(candidates.contains)
      case NotIn(path, candidates) =>
        _extract(value, path).forall(v => !candidates.contains(v))
      case IsNull(path) =>
        _extract(value, path).isEmpty
      case IsNotNull(path) =>
        _extract(value, path).nonEmpty
      case Like(path, pattern, ci) =>
        _extract(value, path).exists(v => _like(v.toString, pattern, ci))
      case StartsWith(path, prefix, ci) =>
        _extract(value, path).exists(v => _string_match(v.toString, prefix, ci, (a, b) => a.startsWith(b)))
      case EndsWith(path, suffix, ci) =>
        _extract(value, path).exists(v => _string_match(v.toString, suffix, ci, (a, b) => a.endsWith(b)))
      case Contains(path, token, ci) =>
        _extract(value, path).exists(v => _string_match(v.toString, token, ci, (a, b) => a.contains(b)))
    }

  def sortValues[E](
    values: Vector[E],
    sort: Vector[SortKey]
  ): Vector[E] =
    sort.foldRight(values) { (key, acc) =>
      val sorted = acc.sortBy(v => _sort_key_value(v, key.path))(Ordering.Option(_sortable_ordering))
      key.direction match {
        case SortDirection.Asc => sorted
        case SortDirection.Desc => sorted.reverse
      }
    }

  def sliceValues[E](
    values: Vector[E],
    offset: Option[Int],
    limit: Option[Int]
  ): Vector[E] = {
    val dropped = offset.filter(_ > 0).map(values.drop).getOrElse(values)
    limit.filter(_ >= 0).map(dropped.take).getOrElse(dropped)
  }

  private def _expr_from_shape(shape: Product): Expr = {
    val clauses = shape.productElementNames.zip(shape.productIterator).toVector.flatMap {
      case (name, cond: Condition[Any @unchecked]) =>
        cond match {
          case Condition.Any => None
          case _ => Some(FieldCondition(name, cond))
        }
      case (name, value) =>
        Some(Eq(name, value))
    }
    clauses match {
      case Vector() => True
      case Vector(one) => one
      case many => And(many)
    }
  }

  private def _extract(root: Any, path: String): Option[Any] = {
    if (path == null || path.isEmpty)
      Option(root)
    else
      path.split("\\.").toVector.foldLeft(Option(root)) { (acc, segment) =>
        acc.flatMap(_extract_field(_, segment))
      }
  }

  private def _extract_field(value: Any, name: String): Option[Any] =
    value match {
      case p: Product =>
        p.productElementNames.zip(p.productIterator).find(_._1 == name).map(_._2)
      case m: Map[?, ?] =>
        m.asInstanceOf[Map[String, Any]].get(name)
      case _ =>
        None
    }

  private def _compare(path: String, actualRoot: Any, expected: Any): Option[Int] =
    _extract(actualRoot, path).flatMap(_compare_values(_, expected))

  private def _compare_values(actual: Any, expected: Any): Option[Int] = (actual, expected) match {
    case (a: Int, b: Int) => Some(a.compareTo(b))
    case (a: Long, b: Long) => Some(a.compareTo(b))
    case (a: Double, b: Double) => Some(a.compareTo(b))
    case (a: Float, b: Float) => Some(a.compareTo(b))
    case (a: BigDecimal, b: BigDecimal) => Some(a.compare(b))
    case (a: Instant, b: Instant) => Some(a.compareTo(b))
    case (a: String, b: String) => Some(a.compareTo(b))
    case (a: Comparable[Any @unchecked], b) if a.getClass.isInstance(b) =>
      Some(a.compareTo(b))
    case _ =>
      None
  }

  private def _like(value: String, pattern: String, caseInsensitive: Boolean): Boolean = {
    val regex = "^" + java.util.regex.Pattern.quote(pattern)
      .replace("%", "\\E.*\\Q")
      .replace("_", "\\E.\\Q") + "$"
    val flags = if (caseInsensitive) java.util.regex.Pattern.CASE_INSENSITIVE else 0
    java.util.regex.Pattern.compile(regex, flags).matcher(value).matches()
  }

  private def _string_match(
    source: String,
    target: String,
    caseInsensitive: Boolean,
    fn: (String, String) => Boolean
  ): Boolean = {
    val left = if (caseInsensitive) source.toLowerCase else source
    val right = if (caseInsensitive) target.toLowerCase else target
    fn(left, right)
  }

  private def _sort_key_value(value: Any, path: String): Option[Sortable] =
    _extract(value, path).flatMap(_to_sortable)

  private type Sortable = Any

  private val _sortable_ordering: Ordering[Sortable] = new Ordering[Sortable] {
    def compare(x: Sortable, y: Sortable): Int = (x, y) match {
      case (a: Int, b: Int) => a.compareTo(b)
      case (a: Long, b: Long) => a.compareTo(b)
      case (a: Double, b: Double) => a.compareTo(b)
      case (a: Float, b: Float) => a.compareTo(b)
      case (a: BigDecimal, b: BigDecimal) => a.compare(b)
      case (a: Instant, b: Instant) => a.compareTo(b)
      case (a: String, b: String) => a.compareTo(b)
      case (a: Comparable[Any @unchecked], b) if a.getClass.isInstance(b) =>
        a.compareTo(b)
      case _ =>
        x.toString.compareTo(y.toString)
    }
  }

  private def _to_sortable(value: Any): Option[Sortable] =
    value match {
      case null => None
      case v => Some(v)
    }
}
