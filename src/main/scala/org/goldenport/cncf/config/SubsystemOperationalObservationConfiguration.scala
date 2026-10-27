package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationOrigin}
import org.goldenport.cncf.operationalobservation.OperationalObservationRuntime

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
private[cncf] object SubsystemOperationalObservationConfiguration {
  def admitC(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget], selected: SubsystemInstanceId
  ): Consequence[Option[OperationalObservationRuntime]] =
    bindings.binding(CncfConfigurationParameterCatalog.subsystemOperationalObservationPlan).flatMap {
      case None => Consequence.success(None)
      case Some(binding) => binding.target match {
        case target: CncfConfigurationTarget.SubsystemInstance if target.subsystem == selected &&
            binding.provenance.sourceType.contains("file") &&
            Set(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd).contains(binding.provenance.origin) =>
          OperationalObservationRuntime.createC(binding.value).map(Some.apply)
        case _ => Consequence.configurationInvalid("Operational observation requires a selected Subsystem host file binding")
      }
    }
}
