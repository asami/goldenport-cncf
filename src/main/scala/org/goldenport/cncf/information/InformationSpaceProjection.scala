package org.goldenport.cncf.information

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentInformationProjection(
  componentName: String,
  counts: InformationSpaceCounts,
  snapshot: InformationSpaceSnapshot
)

object InformationSpaceProjection {
  def components(components: Vector[Component]): Vector[ComponentInformationProjection] =
    components.sortBy(_.name).map(component)

  def component(component: Component): ComponentInformationProjection =
    ComponentInformationProjection(
      component.name,
      component.informationSpace.counts,
      component.informationSpace.snapshot
    )

  def componentOption(
    components: Vector[Component],
    componentname: String
  ): Option[Component] =
    components.find { component =>
      NamingConventions.equivalentByNormalized(component.name, componentname)
    }
}
