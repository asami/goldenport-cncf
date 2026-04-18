package org.goldenport.cncf.http

import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.security.OperationAuthorizationProvider
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
    subsystem.resolver.resolveOperationDefinition(operationSelector) match {
      case Some(provider: OperationAuthorizationProvider) =>
        val rule = provider.operationAuthorization(runtimeConfig)
        Some(WebDescriptor.Authorization(
          operationModes = rule.operationModes,
          anonymousOperationModes = rule.anonymousOperationModes,
          allowAnonymous = rule.allowAnonymous
        ))
      case _ =>
        None
    }
}
