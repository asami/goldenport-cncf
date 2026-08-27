package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.repository.ResolvedComponentResources
import org.goldenport.cncf.config.CncfConfigurationTarget
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationBindingTrace}

/*
 * Internal, value-only projection of typed configuration provenance and
 * already-resolved Component resource composition. It performs no discovery,
 * resolution, loading, or authority grant.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class ComponentAdminConfigurationCompositionView(
  identityView: ComponentAdminViewModel,
  configuration: ConfigurationBindingTrace[CncfConfigurationTarget],
  resources: ResolvedComponentResources
)

private[cncf] object ComponentAdminConfigurationCompositionProjection {
  def projectC(
    identityView: ComponentAdminViewModel,
    configuration: ConfigurationBindingCollection[CncfConfigurationTarget],
    resources: ResolvedComponentResources
  ): Consequence[ComponentAdminConfigurationCompositionView] =
    ComponentAdminViewModel.validateC(identityView).flatMap { validated =>
      if (resources == null)
        Consequence.argumentInvalid("Component Admin resolved resources are required")
      else
        ConfigurationBindingTrace.from(configuration).map { trace =>
          ComponentAdminConfigurationCompositionView(validated, trace, resources)
        }
    }
}
