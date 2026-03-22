package org.goldenport.cncf.entity.view

/*
 * @since   Mar. 21, 2026
 * @version Mar. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ViewDefinition(
  name: String,
  entityName: String,
  viewNames: Vector[String] = Vector.empty
)
