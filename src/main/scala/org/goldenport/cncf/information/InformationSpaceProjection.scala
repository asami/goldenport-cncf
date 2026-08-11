package org.goldenport.cncf.information

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentIdentityCompatibilityAdapter

/*
 * @since   May. 20, 2026
 * @version Aug. 11, 2026
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
  ): Option[Component] = {
    val candidates = ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(components)
    ComponentIdentityCompatibilityAdapter.resolveAliases(
      componentname,
      candidates,
      ComponentIdentityCompatibilityAdapter.Surface.WebPath
    ) match {
      case result: ComponentIdentityCompatibilityAdapter.Canonical =>
        components.find(_.componentId == result.componentid)
      case result: ComponentIdentityCompatibilityAdapter.Adapted =>
        components.find(_.componentId == result.componentid)
      case _: ComponentIdentityCompatibilityAdapter.Rejected =>
        None
    }
  }

  def componentSelector(components: Vector[Component], component: Component): String =
    ComponentIdentityCompatibilityAdapter.resolveAliases(
      component.displayName,
      ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(components),
      ComponentIdentityCompatibilityAdapter.Surface.WebPath
    ) match {
      case result: ComponentIdentityCompatibilityAdapter.Canonical if result.componentid == component.componentId =>
        component.displayName
      case result: ComponentIdentityCompatibilityAdapter.Adapted if result.componentid == component.componentId =>
        component.displayName
      case _ =>
        component.name
    }
}
