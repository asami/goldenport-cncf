package org.goldenport.cncf.http

import org.goldenport.cncf.config.RuntimeConfig
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
    runtimeConfig: RuntimeConfig
  ): Option[WebDescriptor.Authorization] =
    if (operationSelector.startsWith("admin."))
      Some(_authorization(AdminAuthorizationPolicy.operationRule(operationSelector, runtimeConfig)))
    else subsystem.resolver.resolveOperationDefinition(operationSelector) match {
      case Some(provider: OperationAuthorizationProvider) =>
        Some(_authorization(provider.operationAuthorization(runtimeConfig)))
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
