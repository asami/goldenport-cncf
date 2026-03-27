package org.goldenport.cncf.projection.model

/*
 * @since   Mar.  5, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class HelpModel(
  `type`: String,
  name: String,
  summary: String,
  component: Option[String] = None,
  service: Option[String] = None,
  selector: Option[HelpSelectorModel] = None,
  children: Vector[String] = Vector.empty,
  details: Map[String, Vector[String]] = Map.empty,
  usage: Vector[String] = Vector.empty
)

final case class HelpSelectorModel(
  canonical: String,
  cli: String,
  rest: String,
  accepted: Vector[String] = Vector.empty
)
