package org.goldenport.cncf.component

/*
 * @since   Jan.  8, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentSpace(
) {
  import ComponentSpace._

  var _components: Vector[Component] = Vector.empty
  var _by_name: Map[String, Component] = Map.empty
  var _by_instance_id: Map[ComponentInstanceId, Component] = Map.empty
  var _by_component_id: Map[ComponentId, Vector[Component]] = Map.empty

  private def _get_default_by_component_id(id: ComponentId) =
    _by_component_id.get(id).flatMap(_.headOption)

  def components = _components

  def get(id: ComponentInstanceId): Option[Component] = _by_instance_id.get(id)

  // def defaultInstanceId(id: ComponentId): Option[ComponentInstanceId] =
  //   _by_component_id.get(id).flatMap(_.headOption).map(_.instanceId)

  def find(locator: ComponentLocator): Option[Component] =
    locator match {
      case ComponentLocator.ComponentIdLocator(id) => _get_default_by_component_id(id)
      case ComponentLocator.NameLocator(name) => _by_name.get(name)
    }

  def add(ps: Seq[Component]): ComponentSpace = {
    _components = _components ++ ps.toVector
    _refresh()
    this
  }

  def add(p: Component, pp: Component, ps: Component*): ComponentSpace = {
    add(p +: pp +: ps)
    this
  }

  def add(p: Component): ComponentSpace = {
    _components = _components :+ p
    _refresh()
    this
  }

  private def _refresh(): Unit = {
    _by_name = _components.map(x => x.name -> x).toMap
    _by_instance_id = _components.map(x => x.instanceId -> x).toMap
    _by_component_id =
      _components
        .groupBy(_.componentId)
        .view
        .mapValues(_.toVector)
        .toMap
  }
}

object ComponentSpace {
}
