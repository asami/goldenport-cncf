package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentIdentityCompatibilityAdapter
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.information.value.{InformationSpaceCounts, InformationSpaceSnapshot}

/*
 * @since   May. 20, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentInformationProjection(
  componentName: String,
  counts: InformationSpaceCounts,
  snapshot: InformationSpaceSnapshot
)

object InformationSpaceProjection {
  def components(
    components: Vector[Component]
  ): Consequence[Vector[ComponentInformationProjection]] =
    components.sortBy(_.name).foldLeft(Consequence.success(Vector.empty[ComponentInformationProjection])) {
      case (z, component) =>
        z.flatMap(values => _component(component).map(values :+ _))
    }

  def component(
    component: Component
  )(using ctx: ExecutionContext): Consequence[ComponentInformationProjection] =
    component.informationSpace.snapshotC.map { snapshot =>
      ComponentInformationProjection(
        component.name,
        component.informationSpace.counts,
        snapshot
      )
    }

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

  private def _component(
    component: Component
  ): Consequence[ComponentInformationProjection] = {
    given ExecutionContext = component.logic.executionContext()
    InformationSpaceProjection.component(component)
  }
}
