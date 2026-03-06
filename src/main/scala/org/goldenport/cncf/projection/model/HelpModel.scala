package org.goldenport.cncf.projection.model

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final case class HelpModel(
  `type`: String,
  name: String,
  summary: String,
  children: Vector[String] = Vector.empty,
  details: Map[String, Vector[String]] = Map.empty,
  usage: Vector[String] = Vector.empty
)
