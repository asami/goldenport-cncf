package org.goldenport.cncf.security

import org.goldenport.cncf.config.{OperationMode, RuntimeOperationSecurityPolicy}

/*
 * @since   Apr. 25, 2026
 * @version May.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object AdminAuthorizationPolicy {
  def operationRule(
    selector: String,
    policy: RuntimeOperationSecurityPolicy
  ): OperationAuthorizationRule =
    policy.operationMode match {
      case OperationMode.Production =>
        _production_rule(selector, policy)
      case _ =>
        OperationAuthorizationRule.developAnonymousAdmin(policy)
    }

  private def _production_rule(
    selector: String,
    policy: RuntimeOperationSecurityPolicy
  ): OperationAuthorizationRule =
    if (!policy.webProductionAdminEnabled)
      OperationAuthorizationRule(deny = true)
    else if (_is_application_admin(selector))
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("operator"),
        roles = policy.webProductionAdminComponentRoles
      )
    else if (_is_component_admin(selector))
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("operator"),
        roles = policy.webProductionAdminComponentRoles
      )
    else if (_is_jobs_admin(selector))
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("system"),
        roles = policy.webProductionAdminJobsRoles
      )
    else
      OperationAuthorizationRule(
        requireAuthenticated = true,
        requireProviderAuthentication = true,
        minimumPrivilege = Some("system"),
        roles = policy.webProductionAdminSystemRoles
      )

  private def _is_application_admin(
    selector: String
  ): Boolean = {
    val normalized = _normalize(selector)
    normalized.startsWith("admin.application.")
  }

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
