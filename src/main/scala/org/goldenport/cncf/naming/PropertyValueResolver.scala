package org.goldenport.cncf.naming

import io.circe.Json
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.record.Record

/*
 * @since   Jun. 01, 2026
 * @version Jun. 01, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PropertyValueResolver(
  propertyNameContext: RuntimeContext.PropertyNameContext = RuntimeContext.PropertyNameContext.default
) {
  def value(
    properties: Map[String, String],
    name: String
  ): Option[String] =
    _select_candidate(_map_candidates(properties, name))

  def recordValue(
    record: Record,
    name: String
  ): Option[Any] =
    _select_candidate(_map_candidates(record.asMap, name))

  def recordString(
    record: Record,
    name: String
  ): Option[String] =
    recordValue(record, name).map(_.toString)

  def jsonAt(
    json: Json,
    path: Vector[String]
  ): Option[Json] =
    path.foldLeft(Option(json)) { (z, name) =>
      z.flatMap { current =>
        current.asArray.flatMap { xs =>
          name.toIntOption.flatMap(i => xs.lift(i))
        }.orElse(jsonField(current, name))
      }
    }

  def jsonField(
    json: Json,
    name: String
  ): Option[Json] =
    json.asObject.flatMap { obj =>
      _select_candidate(_json_candidates(obj.toIterable.toVector, name))
    }

  def pathAliases(name: String): Vector[String] = {
    val segments = name.split('.').toVector
    val exact = segments.mkString(".")
    val aliases = _accepted_input_styles.map(style => segments.map(style.transform).mkString("."))
    (Vector(exact) ++ aliases).distinct.filterNot(_ == name)
  }

  def segmentAliases(name: String): Vector[String] =
    (Vector(name) ++ _accepted_input_styles.map(_.transform(name))).distinct.filterNot(_ == name)

  def normalizedPath(name: String): String =
    name.split('.').toVector.map(normalizedSegment).mkString(".")

  def normalizedSegment(name: String): String =
    camelSegment(name).toLowerCase(java.util.Locale.ROOT)

  def camelSegment(name: String): String =
    RuntimeContext.PropertyNameStyle.CamelCase.transform(name)

  def snakeSegment(name: String): String =
    RuntimeContext.PropertyNameStyle.SnakeCase.transform(name)

  def kebabSegment(name: String): String =
    RuntimeContext.PropertyNameStyle.KebabCase.transform(name)

  private def _accepted_input_styles: Vector[RuntimeContext.PropertyNameStyle] =
    Vector(
      RuntimeContext.PropertyNameStyle.CamelCase,
      RuntimeContext.PropertyNameStyle.SnakeCase,
      RuntimeContext.PropertyNameStyle.KebabCase
    ).filter(propertyNameContext.acceptedInputStyles.contains)

  private def _map_candidates[A](
    values: Map[String, A],
    name: String
  ): Vector[(String, A)] = {
    _distinct_candidates(
      values.get(name).map(name -> _).toVector ++
        pathAliases(name).flatMap(key => values.get(key).map(key -> _))
    )
  }

  private def _json_candidates(
    values: Vector[(String, Json)],
    name: String
  ): Vector[(String, Json)] = {
    val bykey = values.toMap
    _distinct_candidates(
      bykey.get(name).map(name -> _).toVector ++
        segmentAliases(name).flatMap(key => bykey.get(key).map(key -> _))
    )
  }

  private def _distinct_candidates[A](candidates: Vector[(String, A)]): Vector[(String, A)] =
    candidates.foldLeft(Vector.empty[(String, A)]) { (z, candidate) =>
      if (z.exists(_._1 == candidate._1))
        z
      else
        z :+ candidate
    }

  private def _select_candidate[A](candidates: Vector[(String, A)]): Option[A] =
    candidates.map(_._2).distinct match {
      case Vector(value) => Some(value)
      case _ => None
    }
}

object PropertyValueResolver {
  val default: PropertyValueResolver = PropertyValueResolver()

  def value(
    properties: Map[String, String],
    name: String
  ): Option[String] =
    default.value(properties, name)

  def recordValue(
    record: Record,
    name: String
  ): Option[Any] =
    default.recordValue(record, name)

  def recordString(
    record: Record,
    name: String
  ): Option[String] =
    default.recordString(record, name)

  def jsonAt(
    json: Json,
    path: Vector[String]
  ): Option[Json] =
    default.jsonAt(json, path)

  def jsonField(
    json: Json,
    name: String
  ): Option[Json] =
    default.jsonField(json, name)
}
