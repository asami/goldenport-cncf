package org.goldenport.cncf.entity.view

/*
 * @since   Mar. 21, 2026
 *  version Mar. 23, 2026
 * @version Apr.  2, 2026
 * @version Apr. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ViewQueryDefinition(
  name: String,
  expression: Option[String] = None
)

final case class ViewDefinition(
  name: String,
  entityName: String,
  viewNames: Vector[String] = Vector.empty,
  viewFields: Map[String, Vector[String]] = Map.empty,
  queries: Vector[ViewQueryDefinition] = Vector.empty,
  sourceEvents: Vector[String] = Vector.empty,
  rebuildable: Option[Boolean] = None
) {
  def fieldsFor(viewName: String): Option[Vector[String]] =
    viewFields
      .find { case (name, _) => _normalize(name) == _normalize(viewName) }
      .map(_._2)
      .filter(_.nonEmpty)

  private def _normalize(p: String): String =
    p.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)
}
