package org.goldenport.cncf.search

import org.goldenport.Consequence
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.record.Record

/*
 * @since   May.  6, 2026
 * @version May.  6, 2026
 * @author  ASAMI, Tomoharu
 */
enum SearchMode {
  case FullText
  case Semantic
  case Hybrid

  def name: String =
    this match {
      case SearchMode.FullText => "full-text"
      case SearchMode.Semantic => "semantic"
      case SearchMode.Hybrid => "hybrid"
    }
}

object SearchMode {
  def parse(value: String): Option[SearchMode] =
    Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")).flatMap {
      case "" | "full-text" | "fulltext" | "text" | "keyword" => Some(SearchMode.FullText)
      case "semantic" | "embedding" | "vector" => Some(SearchMode.Semantic)
      case "hybrid" => Some(SearchMode.Hybrid)
      case _ => None
    }
}

final case class SearchPlanningProfile(
  searchableFields: Vector[String] = Vector.empty,
  filterFields: Vector[String] = Vector.empty,
  sortableFields: Vector[String] = Vector.empty,
  defaultSort: Vector[Query.SortKey] = Vector.empty,
  semanticEnabled: Boolean = false
) {
  private val _filter_index =
    filterFields.map(x => _normalize(x) -> x).toMap
  private val _sort_index =
    sortableFields.map(x => _normalize(x) -> x).toMap

  def resolveFilter(name: String): Option[String] =
    _filter_index.get(_normalize(name))

  def resolveSort(name: String): Option[String] =
    if (sortableFields.isEmpty)
      Some(name)
    else
      _sort_index.get(_normalize(name))

  private def _normalize(value: String): String =
    NamingConventions.toNormalizedSegment(value).replace("-", "")
}

final case class PlannedWebSearch(
  query: Query[Query.Plan[Record]],
  mode: SearchMode,
  text: Option[String],
  filters: Vector[(String, String)],
  sort: Vector[Query.SortKey],
  limit: Option[Int],
  offset: Option[Int],
  includeTotal: Boolean
)

object WebSearchQueryPlanner {
  val CanonicalTextParameter = "q"
  val TextAliasParameter = "text"

  def plan(
    input: Record,
    profile: SearchPlanningProfile
  ): Consequence[PlannedWebSearch] = {
    _search_mode(input).flatMap {
      case SearchMode.FullText =>
        _plan_full_text(input, profile, SearchMode.FullText)
      case mode @ (SearchMode.Semantic | SearchMode.Hybrid) if profile.semanticEnabled =>
        _plan_full_text(input, profile, mode)
      case other =>
        Consequence.argumentInvalid(s"${other.name} search is not configured")
    }
  }

  private def _plan_full_text(
    input: Record,
    profile: SearchPlanningProfile,
    mode: SearchMode
  ): Consequence[PlannedWebSearch] = {
    val text = _text(input)
    if (text.exists(_.nonEmpty) && profile.searchableFields.isEmpty)
      Consequence.argumentInvalid("text search requested but no searchable fields are configured")
    else
      for {
        filters <- _filters(input, profile)
        sort <- _sort(input, profile)
      } yield {
        val textExpr = text.map(_text_expr(_, profile.searchableFields))
        val filterExpr = _filter_expr(filters)
        val where = _and(Vector(filterExpr) ++ textExpr.toVector)
        val limit = _int(input, "limit").orElse(_int(input, "pageSize")).orElse(_int(input, "query.limit"))
        val offset = _int(input, "offset").orElse {
          for {
            page <- _int(input, "page").filter(_ > 0)
            size <- limit
          } yield (page - 1) * size
        }.orElse(_int(input, "query.offset"))
        val includeTotal = _bool(input, "includeTotal")
          .orElse(_bool(input, "include_total"))
          .orElse(_bool(input, "query.includeTotal"))
          .getOrElse(false)
        PlannedWebSearch(
          Query.plan(Record.empty, where = where, sort = sort, limit = limit, offset = offset, includeTotal = includeTotal),
          mode,
          text,
          filters,
          sort,
          limit,
          offset,
          includeTotal
        )
      }
  }

  private def _search_mode(input: Record): Consequence[SearchMode] =
    _string(input, "searchMode")
      .orElse(_string(input, "search_mode")) match {
        case Some(value) =>
          SearchMode.parse(value) match {
            case Some(mode) => Consequence.success(mode)
            case None => Consequence.argumentInvalid(s"unknown search mode: ${value}")
          }
        case None =>
          Consequence.success(SearchMode.FullText)
      }

  private def _text(input: Record): Option[String] =
    _string(input, CanonicalTextParameter).orElse(_string(input, TextAliasParameter))

  private def _text_expr(text: String, fields: Vector[String]): Query.Expr =
    fields.distinct match {
      case Vector(field) => Query.Contains(field, text, caseInsensitive = true)
      case many => Query.Or(many.map(field => Query.Contains(field, text, caseInsensitive = true)))
    }

  private def _filters(
    input: Record,
    profile: SearchPlanningProfile
  ): Consequence[Vector[(String, String)]] = {
    val unknown = input.asMap.toVector.collect {
      case (key, value) if !_is_control(key) && _string_value(value).exists(_.nonEmpty) && profile.filterFields.nonEmpty && profile.resolveFilter(key).isEmpty =>
        key
    }
    unknown.headOption match {
      case Some(name) => Consequence.argumentInvalid(s"unknown search filter field: ${name}")
      case None =>
        Consequence.success(
          input.asMap.toVector.flatMap {
            case (key, value) if !_is_control(key) =>
              for {
                field <- profile.resolveFilter(key)
                text <- _string_value(value).filter(_.nonEmpty)
              } yield field -> text
            case _ =>
              None
          }
        )
    }
  }

  private def _filter_expr(filters: Vector[(String, String)]): Query.Expr =
    _and(filters.map { case (field, value) => Query.Eq(field, value) })

  private def _sort(
    input: Record,
    profile: SearchPlanningProfile
  ): Consequence[Vector[Query.SortKey]] =
    _string(input, "sort").orElse(_string(input, "sortBy")).orElse(_string(input, "sort_by")) match {
      case None =>
        Consequence.success(profile.defaultSort)
      case Some(raw) =>
        val (fieldText, explicitDirection) =
          if (raw.startsWith("-"))
            raw.drop(1) -> Some(Query.SortDirection.Desc)
          else
            raw -> None
        profile.resolveSort(fieldText) match {
          case Some(field) =>
            val direction = explicitDirection.orElse(_direction(input)).getOrElse(Query.SortDirection.Asc)
            Consequence.success(Vector(Query.SortKey(field, direction)))
          case None =>
            Consequence.argumentInvalid(s"unknown search sort field: ${fieldText}")
        }
    }

  private def _direction(input: Record): Option[Query.SortDirection] =
    _string(input, "direction").orElse(_string(input, "order")).flatMap { value =>
      value.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "desc" | "descending" => Some(Query.SortDirection.Desc)
        case "asc" | "ascending" => Some(Query.SortDirection.Asc)
        case _ => None
      }
    }

  private def _and(items: Vector[Query.Expr]): Query.Expr =
    items.filterNot(_ == Query.True) match {
      case Vector() => Query.True
      case Vector(one) => one
      case many => Query.And(many)
    }

  private def _string(input: Record, name: String): Option[String] =
    input.getString(name).map(_.trim).filter(_.nonEmpty)

  private def _int(input: Record, name: String): Option[Int] =
    input.getAny(name).flatMap {
      case n: Int => Some(n)
      case n: Long if n.isValidInt => Some(n.toInt)
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case _ => None
    }

  private def _bool(input: Record, name: String): Option[Boolean] =
    input.getAny(name).flatMap {
      case b: Boolean => Some(b)
      case n: java.lang.Number => Some(n.intValue != 0)
      case s: String =>
        s.trim.toLowerCase(java.util.Locale.ROOT) match {
          case "true" | "yes" | "on" | "1" => Some(true)
          case "false" | "no" | "off" | "0" => Some(false)
          case _ => None
        }
      case _ => None
    }

  private def _string_value(value: Any): Option[String] =
    Option(value).map(_.toString.trim)

  private def _is_control(name: String): Boolean =
    Set(
      "q",
      "text",
      "searchMode",
      "search_mode",
      "sort",
      "sortBy",
      "sort_by",
      "direction",
      "order",
      "limit",
      "pageSize",
      "page",
      "offset",
      "includeTotal",
      "include_total",
      "query.limit",
      "query.offset",
      "query.includeTotal",
      "component",
      "entity",
      "entityName",
      "view",
      "totalCountPolicy"
    ).contains(name) ||
      name.startsWith("textus.") ||
      name.startsWith("cncf.") ||
      name.startsWith("subject.") ||
      name.startsWith("principal.")
}
