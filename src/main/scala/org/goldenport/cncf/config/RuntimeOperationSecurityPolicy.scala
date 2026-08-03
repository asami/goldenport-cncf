package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationBindingCollection

/*
 * The admitted runtime authority for operation mode and Web authorization.
 * It intentionally contains values only: no raw configuration, aliases,
 * targets, provenance, or binding collection crosses this boundary.
 *
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeOperationSecurityPolicy(
  operationMode: OperationMode,
  webDevelopAnonymousAdmin: Boolean,
  webDemoAssistEnabled: Boolean,
  webProductionAdminEnabled: Boolean,
  webProductionAdminSystemRoles: Vector[String],
  webProductionAdminComponentRoles: Vector[String],
  webProductionAdminJobsRoles: Vector[String]
)

object RuntimeOperationSecurityPolicy {
  def default: RuntimeOperationSecurityPolicy =
    RuntimeOperationSecurityPolicy(
      RuntimeConfig.defaultOperationMode,
      RuntimeConfig.defaultWebDevelopAnonymousAdmin,
      RuntimeConfig.DEFAULT_WEB_DEMO_ASSIST_ENABLED,
      RuntimeConfig.defaultWebProductionAdminEnabled,
      RuntimeConfig.defaultWebProductionAdminSystemRoles,
      RuntimeConfig.defaultWebProductionAdminComponentRoles,
      RuntimeConfig.defaultWebProductionAdminJobsRoles
    )

  def from(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[RuntimeOperationSecurityPolicy] =
    if (bindings == null)
      Consequence.configurationInvalid("runtime operation security policy bindings are required")
    else
      for {
        operationmode <- bindings.value(CncfConfigurationParameterCatalog.operationMode)
        anonymousadmin <- bindings.value(CncfConfigurationParameterCatalog.webDevelopAnonymousAdmin)
        demoassist <- bindings.value(CncfConfigurationParameterCatalog.webDemoAssistEnabled)
        productionadmin <- bindings.value(CncfConfigurationParameterCatalog.webProductionAdminEnabled)
        systemroles <- bindings.value(CncfConfigurationParameterCatalog.webProductionAdminSystemRoles)
        componentroles <- bindings.value(CncfConfigurationParameterCatalog.webProductionAdminComponentRoles)
        jobsroles <- bindings.value(CncfConfigurationParameterCatalog.webProductionAdminJobsRoles)
      } yield RuntimeOperationSecurityPolicy(
        operationmode.getOrElse(RuntimeConfig.defaultOperationMode),
        anonymousadmin.getOrElse(RuntimeConfig.defaultWebDevelopAnonymousAdmin),
        demoassist.getOrElse(RuntimeConfig.DEFAULT_WEB_DEMO_ASSIST_ENABLED),
        productionadmin.getOrElse(RuntimeConfig.defaultWebProductionAdminEnabled),
        _roles(systemroles, RuntimeConfig.defaultWebProductionAdminSystemRoles),
        _roles(componentroles, RuntimeConfig.defaultWebProductionAdminComponentRoles),
        _roles(jobsroles, RuntimeConfig.defaultWebProductionAdminJobsRoles)
      )

  private def _roles(value: Option[Vector[String]], default: Vector[String]): Vector[String] =
    value.filter(_.nonEmpty).getOrElse(default)
}
