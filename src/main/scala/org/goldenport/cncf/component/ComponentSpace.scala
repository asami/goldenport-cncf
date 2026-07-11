package org.goldenport.cncf.component

import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Jan.  8, 2026
 *  version Jan. 15, 2026
 *  version Apr. 24, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentSpace(
) {
  import ComponentSpace._

  private var _components: Vector[Component] = Vector.empty
  private var _by_name: Map[String, Component] = Map.empty
  private var _by_instance_id: Map[String, Component] = Map.empty
  private var _by_component_id: Map[ComponentId, Vector[Component]] = Map.empty

  private def _get_default_by_component_id(id: ComponentId) =
    _by_component_id.get(id).flatMap(_.headOption)

  def components = _components

  def get(id: ComponentInstanceId): Option[Component] =
    _by_instance_id.get(id.canonicalKey)

  def findInstance(id: ComponentInstanceId): Option[Component] = get(id)

  // def defaultInstanceId(id: ComponentId): Option[ComponentInstanceId] =
  //   _by_component_id.get(id).flatMap(_.headOption).map(_.instanceId)

  def find(locator: ComponentLocator): Option[Component] =
    locator match {
      case ComponentLocator.ComponentIdLocator(id) => _get_default_by_component_id(id)
      case ComponentLocator.NameLocator(name) =>
        _by_name.get(name).orElse(_components.find(x => _matches_component_name(x, name)))
    }

  def add(ps: Seq[Component]): ComponentSpace = {
    _components = _components ++ ps.toVector
    _refresh()
    this
  }

  def add(bundle: Component.Bundle): ComponentSpace =
    add(bundle.participants)

  def add(p: Component, pp: Component, ps: Component*): ComponentSpace = {
    add(p +: pp +: ps)
    this
  }

  def add(p: Component): ComponentSpace = {
    _components = _components :+ p
    _refresh()
    this
  }

  def upsert(ps: Seq[Component]): ComponentSpace = {
    ps.foreach { component =>
      val key = component.instanceId.canonicalKey
      val index = _components.indexWhere(_.instanceId.canonicalKey == key)
      if (index >= 0)
        _components = _components.updated(index, component)
      else
        _components = _components :+ component
    }
    _refresh()
    this
  }

  private def _refresh(): Unit = {
    val duplicateid = _components
      .groupBy(_.instanceId.canonicalKey)
      .collectFirst { case (id, xs) if xs.size > 1 => id }
    require(duplicateid.isEmpty, s"duplicate component instance id: ${duplicateid.getOrElse("")}")
    _by_name = _components
      .groupBy(_.name)
      .view
      .mapValues { xs =>
        xs.find(_.instanceMetadata.exists(_.isDefault))
          .orElse(xs.find(_.instanceId.instance == "default"))
          .getOrElse(xs.head)
      }
      .toMap
    _by_instance_id = _components.map(x => x.instanceId.canonicalKey -> x).toMap
    _by_component_id =
      _components
        .groupBy(_.componentId)
        .view
        .mapValues(_.toVector)
        .toMap
  }
}

object ComponentSpace {
  private def _matches_component_name(component: Component, name: String): Boolean =
    NamingConventions.equivalentByNormalized(component.name, name) ||
      component.artifactMetadata.toVector.exists { metadata =>
        metadata.component.exists(NamingConventions.equivalentByNormalized(_, name)) ||
          NamingConventions.equivalentByNormalized(metadata.name, name)
      }
}
