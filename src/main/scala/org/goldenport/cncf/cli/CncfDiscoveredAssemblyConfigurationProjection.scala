package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingCandidates
import org.goldenport.cncf.config.{CncfAssemblyConfigurationProjection, CncfConfigurationTarget, SubsystemInstanceId}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/*
 * Projects explicit assembly configuration together with configuration
 * discovered from the selected subsystem descriptor.  Explicit assembly
 * bindings retain precedence over descriptor defaults by canonical parameter
 * identity, while the descriptor path remains the discovered source identity.
 *
 * @since   Aug. 15, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] object CncfDiscoveredAssemblyConfigurationProjection {
  def candidates(
    assemblyValues: Map[String, String],
    assemblySourceIdentity: Option[String],
    descriptor: Option[GenericSubsystemDescriptor],
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    if (assemblySourceIdentity == null || descriptor == null || subsystem == null)
      Consequence.configurationInvalid("CNCF discovered assembly configuration projection is invalid")
    else
      for {
        explicit <- CncfAssemblyConfigurationProjection.candidates(
          assemblyValues,
          assemblySourceIdentity,
          subsystem
        )
        discovered <- _descriptor_candidates(descriptor, subsystem)
        explicitids = explicit.bindings.map(_.parameter.id).toSet
        retained = discovered.bindings.filterNot(binding => explicitids.contains(binding.parameter.id))
        combined <- ConfigurationBindingCandidates.from(explicit.bindings ++ retained)
      } yield combined

  private def _descriptor_candidates(
    descriptor: Option[GenericSubsystemDescriptor],
    subsystem: SubsystemInstanceId
  ): Consequence[ConfigurationBindingCandidates[CncfConfigurationTarget]] =
    descriptor match {
      case None => Consequence.success(ConfigurationBindingCandidates.empty[CncfConfigurationTarget])
      case Some(value) if value == null || value.path == null =>
        Consequence.configurationInvalid("CNCF discovered subsystem descriptor is invalid")
      case Some(value) =>
        CncfAssemblyConfigurationProjection.candidates(
          value.config,
          Some(value.path.toString),
          subsystem
        )
    }
}
