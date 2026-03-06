package org.goldenport.cncf.projection.model

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TreeModel(
  subsystem: String,
  components: Vector[TreeModel.ComponentNode]
)

object TreeModel {
  final case class ComponentNode(
    name: String,
    services: Vector[ServiceNode]
  )

  final case class ServiceNode(
    name: String,
    operations: Vector[String]
  )
}
