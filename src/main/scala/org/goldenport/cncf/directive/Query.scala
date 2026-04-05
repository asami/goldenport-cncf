package org.goldenport.cncf.directive

import java.time.Instant
import org.simplemodeling.model.directive.Condition
import org.goldenport.record.Record
import org.goldenport.record.RecordPresentable
import org.goldenport.text.Presentable
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.entity.EntityPersistableQuery

/*
 * @since   Feb. 19, 2026
 *  version Mar. 30, 2026
 *  version Apr.  4, 2026
 * @version Apr.  5, 2026
 * @author  ASAMI, Tomoharu
 */
case class Query[T](query: T) extends RecordPresentable {
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

  def toRecord(): Record =
    Query.toRecord(this)
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

  def toRecord(
    query: Query[?]
  ): Record =
    query.query match {
      case p: Plan[?] =>
        Record.dataAuto(
          "condition" -> _value(p.condition),
          "where" -> _expr_record_option(p.where),
          "sort" -> p.sort.map(_sort_record),
          "limit" -> p.limit,
          "offset" -> p.offset
        )
      case other =>
        Record.dataAuto(
          "condition" -> _value(other)
        )
    }

  def plan[T](
    condition: T,
    where: Expr = True,
    sort: Vector[SortKey] = Vector.empty,
    limit: Option[Int] = None,
    offset: Option[Int] = None
  ): Query[Plan[T]] =
    Query(Plan(condition, where, sort, limit, offset))

  def fromRecord(record: Record): Query[?] = {
    val condition = Record.create(
      record.fields.iterator.collect {
        case field if !_is_query_control_parameter(field.key) && !_is_query_control_container(field.key, field.value.single) =>
          field.key -> field.value.single
      }.toVector
    )
    val limit = _query_control_int(record, "limit")
    val offset = _query_control_int(record, "offset")
    Query.plan(condition, limit = limit, offset = offset)
  }

  def withControls(
    query: Query[?],
    record: Record
  ): Query[?] = {
    val limit = _query_control_int(record, "limit")
    val offset = _query_control_int(record, "offset")
    val sanitizedCondition = _condition_without_legacy_controls(query.query)
    if (limit.isEmpty && offset.isEmpty)
      sanitizedCondition match {
        case None => query
        case Some(condition) => query.query match {
          case p: Plan[?] =>
            Query(Plan(
              condition = condition,
              where = p.where,
              sort = p.sort,
              limit = p.limit,
              offset = p.offset
            ))
          case _ =>
            Query(condition)
        }
      }
    else
      sanitizedCondition.getOrElse(query.query) match {
        case p: Plan[?] =>
          Query(Plan(
            condition = p.condition,
            where = p.where,
            sort = p.sort,
            limit = limit.orElse(p.limit),
            offset = offset.orElse(p.offset)
          ))
        case other =>
          Query.plan(
            other,
            where = whereOf(Query(other)),
            sort = sortOf(query),
            limit = limit,
            offset = offset
          )
      }
  }

  private def _condition_without_legacy_controls(
    condition: Any
  ): Option[Any] =
    condition match {
      case p: Plan[?] =>
        _condition_without_legacy_controls(p.condition).map(x => p.copy(condition = x))
      case rec: Record =>
        val filtered = rec.asMap.filterNot { case (k, v) =>
          _is_query_control_parameter(k) || _is_legacy_query_control_blob(k, v) || _is_query_control_container(k, v)
        }
        Some(Record.create(filtered))
      case other =>
        None
    }

  private def _is_legacy_query_control_blob(
    key: String,
    value: Any
  ): Boolean =
    key == "query" && Option(value).exists {
      case s: String =>
        val normalized = s.toLowerCase
        normalized.contains("limit=") || normalized.contains("offset=")
      case _ =>
        false
    }

  private def _is_query_control_container(
    key: String,
    value: Any
  ): Boolean =
    key == "query" && (value match {
      case rec: Record =>
        rec.fields.forall(x => x.key == "limit" || x.key == "offset")
      case m: Map[?, ?] =>
        m.keys.forall {
          case s: String => s == "limit" || s == "offset"
          case _ => false
        }
      case other =>
        val normalized = Option(other).fold("")(_.toString.toLowerCase)
        (normalized.contains("limit=") || normalized.contains("limit:")) &&
        (normalized.contains("offset=") || normalized.contains("offset:"))
    })

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

  private def _sort_record(
    key: SortKey
  ): Record =
    Record.dataAuto(
      "path" -> key.path,
      "direction" -> key.direction.toString.toLowerCase
    )

  private def _expr_record_option(
    expr: Expr
  ): Option[Record] =
    expr match {
      case True => None
      case other => Some(_expr_record(other))
    }

  private def _expr_record(
    expr: Expr
  ): Record =
    expr match {
      case True =>
        Record.dataAuto("op" -> "true")
      case False =>
        Record.dataAuto("op" -> "false")
      case And(items) =>
        Record.dataAuto("op" -> "and", "items" -> items.map(_expr_record))
      case Or(items) =>
        Record.dataAuto("op" -> "or", "items" -> items.map(_expr_record))
      case Not(item) =>
        Record.dataAuto("op" -> "not", "item" -> _expr_record(item))
      case FieldCondition(path, condition) =>
        Record.dataAuto(
          "op" -> "field_condition",
          "path" -> path,
          "condition" -> Presentable.print(condition)
        )
      case Eq(path, value) =>
        Record.dataAuto("op" -> "eq", "path" -> path, "value" -> _value(value))
      case Ne(path, value) =>
        Record.dataAuto("op" -> "ne", "path" -> path, "value" -> _value(value))
      case Gt(path, value) =>
        Record.dataAuto("op" -> "gt", "path" -> path, "value" -> _value(value))
      case Gte(path, value) =>
        Record.dataAuto("op" -> "gte", "path" -> path, "value" -> _value(value))
      case Lt(path, value) =>
        Record.dataAuto("op" -> "lt", "path" -> path, "value" -> _value(value))
      case Lte(path, value) =>
        Record.dataAuto("op" -> "lte", "path" -> path, "value" -> _value(value))
      case In(path, values) =>
        Record.dataAuto("op" -> "in", "path" -> path, "values" -> values.map(_value))
      case NotIn(path, values) =>
        Record.dataAuto("op" -> "not_in", "path" -> path, "values" -> values.map(_value))
      case IsNull(path) =>
        Record.dataAuto("op" -> "is_null", "path" -> path)
      case IsNotNull(path) =>
        Record.dataAuto("op" -> "is_not_null", "path" -> path)
      case Like(path, pattern, caseInsensitive) =>
        Record.dataAuto("op" -> "like", "path" -> path, "pattern" -> pattern, "case_insensitive" -> caseInsensitive)
      case StartsWith(path, value, caseInsensitive) =>
        Record.dataAuto("op" -> "starts_with", "path" -> path, "value" -> value, "case_insensitive" -> caseInsensitive)
      case EndsWith(path, value, caseInsensitive) =>
        Record.dataAuto("op" -> "ends_with", "path" -> path, "value" -> value, "case_insensitive" -> caseInsensitive)
      case Contains(path, value, caseInsensitive) =>
        Record.dataAuto("op" -> "contains", "path" -> path, "value" -> value, "case_insensitive" -> caseInsensitive)
    }

  private def _value(
    value: Any
  ): Any =
    value match {
      case null => null
      case m: Record => _record_value(m)
      case m: RecordPresentable => _record_value(m.toRecord())
      case Condition.Any => null
      case Condition.Is(expected) => _value(expected)
      case Condition.In(candidates) => candidates.toVector.map(_value)
      case m: Iterable[?] => m.iterator.map(_value).toVector
      case m: Array[?] => m.toVector.map(_value)
      case m: Option[?] => m.map(_value)
      case m: Presentable => m.print
      case other => other
    }

  private def _record_value(
    record: Record
  ): Record =
    Record.create(
      record.asMap.flatMap {
        case (_, null) => None
        case (_, Condition.Any) => None
        case (k, v) => Some(k -> _value(v))
      }
    )

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
      case rec: Record => _expr_from_record(rec)
      case m: Map[?, ?] => _expr_from_map(m.asInstanceOf[Map[String, Any]])
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
    _compose_clauses(clauses)
  }

  private def _expr_from_record(record: Record): Expr =
    _expr_from_map(record.asMap)

  private def _int_option(p: Option[Any]): Option[Int] =
    p.flatMap {
      case i: Int => Some(i)
      case l: Long if l.isValidInt => Some(l.toInt)
      case s: Short => Some(s.toInt)
      case b: Byte => Some(b.toInt)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case _ => None
    }

  private def _query_control_int(
    record: Record,
    key: String
  ): Option[Int] =
    record.getRecord("query").flatMap(_.getAny(key)).flatMap(value => _int_option(Some(value)))

  private def _expr_from_map(record: Map[String, Any]): Expr = {
    val clauses = record.toVector.flatMap {
      case (name, _) if _is_framework_parameter(name) => None
      case (name, _) if _is_query_control_parameter(name) => None
      case (name, _) if _is_visibility_control_parameter(name) => None
      case (name, value) =>
      Some(Eq(name, value))
    }
    _compose_clauses(clauses)
  }

  private def _is_framework_parameter(name: String): Boolean =
    name.startsWith("textus.") || name.startsWith("cncf.")

  private def _is_query_control_parameter(name: String): Boolean =
    name.startsWith("query.")

  private def _is_visibility_control_parameter(name: String): Boolean =
    name == "postStatus" || name == "post_status" || name == "aliveness"

  private def _compose_clauses(clauses: Vector[Expr]): Expr =
    clauses match {
      case Vector() => True
      case Vector(one) => one
      case many => And(many)
    }

  private def _extract(root: Any, path: String): Option[Any] = {
    if (path == null || path.isEmpty)
      Option(root)
    else
      path.split("\\.").toVector.foldLeft(Option(root)) { (acc, segment) =>
        acc.flatMap(_extract_field(_, segment))
      }
  }

  private val _query_aliases = Map(
    "name" -> Vector("nameAttributes.name", "name_attributes.name"),
    "title" -> Vector("nameAttributes.title", "name_attributes.title"),
    "poststatus" -> Vector("lifecycleAttributes.postStatus", "lifecycle_attributes.post_status"),
    "aliveness" -> Vector("lifecycleAttributes.aliveness", "lifecycle_attributes.aliveness")
  )

  private def _extract_field(value: Any, name: String): Option[Any] =
    value match {
      case r: Record =>
        _extract_from_map(r.asMap, name).orElse(_extract_alias(value, name))
      case p: Product =>
        _extract_from_product(p, name).orElse(_extract_alias(value, name))
      case m: Map[?, ?] =>
        _extract_from_map(m.asInstanceOf[Map[String, Any]], name).orElse(_extract_alias(value, name))
      case _ =>
        None
    }

  private def _extract_from_product(value: Product, name: String): Option[Any] = {
    val normalized = _normalize_name(name)
    value.productElementNames.zip(value.productIterator)
      .find { case (k, _) => _normalize_name(k) == normalized }
      .flatMap { case (_, v) => _normalize_extracted(v) }
  }

  private def _extract_from_map(value: Map[String, Any], name: String): Option[Any] = {
    val normalized = _normalize_name(name)
    value.collectFirst {
      case (k, v) if _normalize_name(k) == normalized => v
    }.flatMap(_normalize_extracted)
  }

  private def _extract_alias(value: Any, name: String): Option[Any] =
    _query_aliases.getOrElse(_normalize_name(name), Vector.empty).iterator.flatMap(path => _extract(value, path)).toSeq.headOption

  private def _normalize_extracted(value: Any): Option[Any] =
    value match {
      case None => None
      case Some(v) => _normalize_extracted(v)
      case v: org.goldenport.text.Presentable => Some(v.print)
      case v => Some(v)
    }

  private def _normalize_name(name: String): String =
    RuntimeContext.PropertyNameStyle.CamelCase.transform(name)

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
