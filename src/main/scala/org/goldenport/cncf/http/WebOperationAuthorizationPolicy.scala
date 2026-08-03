package org.goldenport.cncf.http

import org.goldenport.cncf.config.RuntimeOperationSecurityPolicy
import org.goldenport.cncf.security.{AdminAuthorizationPolicy, OperationAuthorizationProvider}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object WebOperationAuthorizationPolicy {
  def operationRule(
    subsystem: Subsystem,
    operationSelector: String,
    policy: RuntimeOperationSecurityPolicy
  ): Option[WebDescriptor.Authorization] =
    if (operationSelector.startsWith("admin."))
      Some(_authorization(AdminAuthorizationPolicy.operationRule(operationSelector, policy)))
    else subsystem.resolver.resolveOperationDefinition(operationSelector) match {
      case Some(provider: OperationAuthorizationProvider) =>
        Some(_authorization(provider.operationAuthorization(policy)))
      case _ =>
        None
    }

  private def _authorization(
    rule: org.goldenport.cncf.security.OperationAuthorizationRule
  ): WebDescriptor.Authorization =
    WebDescriptor.Authorization(
      roles = rule.roles,
      scopes = rule.scopes,
      capabilities = rule.capabilities,
      operationModes = rule.operationModes,
      anonymousOperationModes = rule.anonymousOperationModes,
      allowAnonymous = rule.allowAnonymous,
      deny = rule.deny,
      requireAuthenticated = rule.requireAuthenticated,
      requireProviderAuthentication = rule.requireProviderAuthentication,
      minimumPrivilege = rule.minimumPrivilege
    )
}
