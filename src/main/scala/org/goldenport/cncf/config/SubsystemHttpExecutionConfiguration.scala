package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationOrigin}
import org.goldenport.cncf.http.RuntimeHttpExecutionPlan

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
/** Host preflight before Component assembly; provenance is supplied by typed bindings. */
private[cncf] object SubsystemHttpExecutionConfiguration {
  def admitC(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget],
    selected: SubsystemInstanceId
  ): Consequence[Option[RuntimeHttpExecutionPlan]] =
    bindings.binding(CncfConfigurationParameterCatalog.subsystemHttpExecutionPlan).flatMap {
      case None => Consequence.success(None)
      case Some(binding) => binding.target match {
        case target: CncfConfigurationTarget.SubsystemInstance if target.subsystem == selected &&
            binding.provenance.sourceType.contains("file") &&
            Set(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd).contains(binding.provenance.origin) =>
          Consequence.success(Some(binding.value))
        case _ => Consequence.configurationInvalid("HTTP Execution requires a selected Subsystem host file binding")
      }
    }
}
