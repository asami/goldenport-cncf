package org.goldenport.cncf.naming

import org.goldenport.util.StringUtils

/*
 * Canonical naming (definition layer):
 * - Component / Service: UpperCamelCase
 * - Operation: lowerCamelCase
 *
 * Runtime naming (execution layer):
 * - all selectors and path segments are kebab-case
 */
/*
 * @since   May. 24, 2025
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object NamingConventions {
  private val _normalizedSegmentRegex = "^[a-z0-9]+(?:-[a-z0-9]+)*$".r

  def toNormalizedSegment(value: String): String =
    StringUtils.toKebabCase(value)

  def toNormalizedSelector(selector: String): String =
    _split_selector(selector).map(toNormalizedSegment).mkString(".")

  def toNormalizedSelector(
    component: String,
    service: String,
    operation: String
  ): String =
    s"${toNormalizedSegment(component)}.${toNormalizedSegment(service)}.${toNormalizedSegment(operation)}"

  def toNormalizedPath(
    component: String,
    service: String,
    operation: String
  ): String =
    s"/${toNormalizedSegment(component)}/${toNormalizedSegment(service)}/${toNormalizedSegment(operation)}"

  def toOperationId(
    component: String,
    service: String,
    operation: String
  ): String = {
    val tokens = _tokens(component) ++ _tokens(service) ++ _tokens(operation)
    tokens.headOption match {
      case Some(head) =>
        head + tokens.tail.map(_capitalize).mkString
      case None =>
        ""
    }
  }

  def isNormalizedSegment(value: String): Boolean =
    value.trim match {
      case _normalizedSegmentRegex() => true
      case _ => false
    }

  def isNormalizedSelector(value: String): Boolean = {
    val segments = value.split("\\.").toVector.map(_.trim).filter(_.nonEmpty)
    segments.nonEmpty && segments.forall(isNormalizedSegment)
  }

  def equivalentByNormalized(lhs: String, rhs: String): Boolean =
    toComparisonKey(lhs) == toComparisonKey(rhs)

  def equivalentSelector(lhs: String, rhs: String): Boolean =
    _split_selector(lhs).map(toComparisonKey) == _split_selector(rhs).map(toComparisonKey)

  def toComparisonKey(value: String): String =
    toNormalizedSegment(value).replace("-", "")

  private def _tokens(value: String): Vector[String] =
    toNormalizedSegment(value).split("-").toVector.filter(_.nonEmpty)

  private def _split_selector(value: String): Vector[String] =
    value.split("\\.").toVector.map(_.trim).filter(_.nonEmpty)

  private def _capitalize(value: String): String =
    value.headOption match {
      case Some(head) => head.toUpper + value.drop(1)
      case None => value
    }
}
