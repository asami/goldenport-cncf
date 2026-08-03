package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.ConfigurationBindingResolutionContext

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationResolutionContext private (
  val generic: ConfigurationBindingResolutionContext[CncfConfigurationTarget]
)

object CncfConfigurationResolutionContext {
  def globalOnly: Consequence[CncfConfigurationResolutionContext] =
    ConfigurationBindingResolutionContext.create(
      Vector[CncfConfigurationTarget](CncfConfigurationTarget.Global)
    ).map(new CncfConfigurationResolutionContext(_))

  def forSubsystem(
    subsystem: SubsystemInstanceId
  ): Consequence[CncfConfigurationResolutionContext] =
    CncfConfigurationTarget.SubsystemInstance.create(subsystem).flatMap { target =>
      ConfigurationBindingResolutionContext.create(
        Vector[CncfConfigurationTarget](CncfConfigurationTarget.Global, target)
      ).map(new CncfConfigurationResolutionContext(_))
    }

  def forComponent(
    component: ComponentId,
    subsystem: SubsystemInstanceId,
    componentInstance: ComponentInstanceId
  ): Consequence[CncfConfigurationResolutionContext] =
    if (component == null || componentInstance == null || component.name != componentInstance.name)
      Consequence.configurationInvalid("configuration component resolution context is invalid")
    else
      for {
        componentclass <- CncfConfigurationTarget.ComponentClass.create(component)
        subsysteminstance <- CncfConfigurationTarget.SubsystemInstance.create(subsystem)
        instance <- CncfConfigurationTarget.ComponentInstance.create(subsystem, componentInstance)
        generic <- ConfigurationBindingResolutionContext.create(
          Vector(CncfConfigurationTarget.Global, componentclass, subsysteminstance, instance)
        )
      } yield new CncfConfigurationResolutionContext(generic)
}
