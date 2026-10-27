package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCollection, ConfigurationOrigin, ConfigurationProvenance, ConfigurationResolutionSnapshot}
import org.goldenport.cncf.subsystem.{SubsystemStoragePlan, SubsystemStoragePlanCodec}

/**
 * Internal owning-host admission, not actor authentication or sender authority.
 * @since   Oct.  2, 2026
 * @version Oct. 27, 2026
 */
private[cncf] final class SubsystemStorageConfiguration private[config] (
  val binding: ConfigurationBinding[SubsystemStoragePlan, CncfConfigurationTarget]
) {
  def withConstruction[A](assemble: => A): A = binding.value.withConstruction(assemble)
}

private[cncf] object SubsystemStorageConfiguration {
  def admitC(
    collection: ConfigurationBindingCollection[CncfConfigurationTarget],
    selected: SubsystemInstanceId,
    snapshot: ConfigurationResolutionSnapshot
  ): Consequence[Option[SubsystemStorageConfiguration]] =
    if (collection == null || selected == null || snapshot == null)
      Consequence.configurationInvalid("Host storage admission requires retained runtime configuration and selected Subsystem")
    else collection.binding(CncfConfigurationParameterCatalog.subsystemStoragePlan).flatMap {
      case None => Consequence.success(None)
      case Some(binding) if _selected_component_default(binding, selected) =>
        Consequence.success(Some(new SubsystemStorageConfiguration(binding)))
      case Some(binding) if !_selected_file(binding, selected) =>
        Consequence.configurationInvalid("Host storage requires a selected Subsystem file binding")
      case Some(binding) =>
        for {
          runtime <- CncfRuntimeConfigurationProjection.forSubsystem(snapshot, selected)
          actual <- runtime.binding(CncfConfigurationParameterCatalog.subsystemStoragePlan)
          admitted <- actual match {
            case Some(value) if _selected_file(value, selected) &&
                value.target == binding.target && _same_provenance(value.provenance, binding.provenance) =>
              for {
                expected <- SubsystemStoragePlanCodec.encode(value.value)
                supplied <- SubsystemStoragePlanCodec.encode(binding.value)
                result <- if (expected == supplied)
                  Consequence.success(Some(new SubsystemStorageConfiguration(binding)))
                else Consequence.configurationInvalid("Host storage binding differs from its retained source")
              } yield result
            case _ => Consequence.configurationInvalid("Host storage binding has no matching retained file source")
          }
        } yield admitted
    }

  private def _selected_component_default(
    binding: ConfigurationBinding[SubsystemStoragePlan, CncfConfigurationTarget],
    selected: SubsystemInstanceId
  ): Boolean = binding.target match {
    case target: CncfConfigurationTarget.SubsystemInstance =>
      target.subsystem == selected && binding.provenance.origin == ConfigurationOrigin.Resource &&
        binding.provenance.sourceType.contains(CncfAssemblyConfigurationProjection.ComponentDefaultsSourceType)
    case _ => false
  }

  private def _selected_file(binding: ConfigurationBinding[SubsystemStoragePlan, CncfConfigurationTarget],
    selected: SubsystemInstanceId): Boolean =
    binding.target match {
      case target: CncfConfigurationTarget.SubsystemInstance =>
        target.subsystem == selected && binding.provenance.sourceType.contains("file") &&
          Set(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd)
            .contains(binding.provenance.origin)
      case _ => false
    }

  private def _same_provenance(a: ConfigurationProvenance, b: ConfigurationProvenance): Boolean =
    a.origin == b.origin && a.layer == b.layer && a.sourceIdentity == b.sourceIdentity &&
      a.inputPath == b.inputPath && a.inputSpelling == b.inputSpelling &&
      a.sourceRank == b.sourceRank && a.sourceOrdinal == b.sourceOrdinal &&
      a.sourceType == b.sourceType && a.evidence == b.evidence &&
      a.omittedEvidenceCount == b.omittedEvidenceCount && a.isConfidential == b.isConfidential
}
