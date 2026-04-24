package org.goldenport.cncf.security

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}

/*
 * @since   Apr. 25, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object AdminAuthorizationPolicy {
  def operationRule(
    selector: String,
    runtimeConfig: RuntimeConfig
  ): OperationAuthorizationRule =
    runtimeConfig.operationMode match {
      case OperationMode.Production =>
        _production_rule(selector, runtimeConfig)
      case _ =>
        OperationAuthorizationRule.developAnonymousAdmin(runtimeConfig)
    }

  private def _production_rule(
    selector: String,
    runtimeConfig: RuntimeConfig
  ): OperationAuthorizationRule =
    if (!runtimeConfig.webProductionAdminEnabled)
      OperationAuthorizationRule(deny = true)
    else if (_is_component_admin(selector))
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("operator"),
        roles = runtimeConfig.webProductionAdminComponentRoles
      )
    else if (_is_jobs_admin(selector))
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("system"),
        roles = runtimeConfig.webProductionAdminJobsRoles
      )
    else
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("system"),
        roles = runtimeConfig.webProductionAdminSystemRoles
      )

  private def _is_component_admin(
    selector: String
  ): Boolean = {
    val normalized = _normalize(selector)
    normalized.startsWith("admin.entity.") ||
      normalized.startsWith("admin.data.") ||
      normalized.startsWith("admin.view.") ||
      normalized.startsWith("admin.aggregate.")
  }

  private def _is_jobs_admin(
    selector: String
  ): Boolean = {
    val normalized = _normalize(selector)
    normalized.startsWith("admin.execution.") ||
      normalized.startsWith("admin.jobs.")
  }

  private def _normalize(
    text: String
  ): String =
    Option(text).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
}
