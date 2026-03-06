package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.projection.model.TreeModel

object TreeProjection {
  def project(base: Component): TreeModel = {
    val components = base.subsystem.map(_.components).getOrElse(Vector(base)).sortBy(_.name)
    TreeModel(
      subsystem = base.subsystem.map(_.name).getOrElse("subsystem"),
      components = components.map { component =>
        val services = component.protocol.services.services.sortBy(_.name).map { service =>
          TreeModel.ServiceNode(
            name = service.name,
            operations = service.operations.operations.toVector.map(_.name).sorted
          )
        }
        TreeModel.ComponentNode(
          name = component.name,
          services = services
        )
      }
    )
  }
}
