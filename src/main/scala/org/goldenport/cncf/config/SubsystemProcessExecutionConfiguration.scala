package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationOrigin}
import org.goldenport.cncf.processexecution.{RuntimeProcessExecutionPlan, RuntimeProcessExecutionPlanCodec}

/** Called only by the owning runtime preflight, before Component assembly. */
/*
 * @since Oct.  7, 2026
 *  version Oct. 27, 2026
 * @version Nov.  3, 2026
 */
private[cncf] object SubsystemProcessExecutionConfiguration {
  def admitC(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget],
    selected: SubsystemInstanceId,
    ioDefaultMaximumBytes: Long = IoDefaultParameters.BUILTIN_MAXIMUM_BYTES.toLong
  ): Consequence[Option[RuntimeProcessExecutionPlan]] =
    bindings.binding(CncfConfigurationParameterCatalog.subsystemProcessExecutionPlan).flatMap {
      case None => Consequence.success(None)
      case Some(binding) => binding.target match {
        case target: CncfConfigurationTarget.SubsystemInstance if target.subsystem == selected &&
            binding.provenance.sourceType.contains("file") &&
            Set(ConfigurationOrigin.Home, ConfigurationOrigin.Project, ConfigurationOrigin.Cwd).contains(binding.provenance.origin) =>
          RuntimeProcessExecutionPlanCodec.resolveIoDefaultsC(binding.value, ioDefaultMaximumBytes).map(Some(_))
        case target: CncfConfigurationTarget.SubsystemInstance if target.subsystem == selected &&
            binding.provenance.origin == ConfigurationOrigin.Resource &&
            binding.provenance.sourceType.contains(CncfAssemblyConfigurationProjection.ComponentDefaultsSourceType) =>
          RuntimeProcessExecutionPlanCodec.resolveIoDefaultsC(binding.value, ioDefaultMaximumBytes).map(Some(_))
        case _ => Consequence.configurationInvalid("Process Execution requires a selected Subsystem file or CAR-default binding")
      }
    }
}
