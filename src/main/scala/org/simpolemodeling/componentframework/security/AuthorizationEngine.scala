package org.simplemodeling.componentframework.security

import org.simplemodeling.componentframework.context.ExecutionContext
import org.simplemodeling.componentframework.context.SecurityLevel

/*
 * @since   Dec. 21, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
sealed trait AuthorizationDecision

object AuthorizationDecision {
  case object Allow extends AuthorizationDecision
  case object Deny extends AuthorizationDecision
}

trait AuthorizationEngine {
  def authorize(
    ctx: ExecutionContext,
    resource: SecuredResource,
    action: Action
  ): AuthorizationDecision
}

final class DefaultAuthorizationEngine extends AuthorizationEngine {
  override def authorize(
    ctx: ExecutionContext,
    resource: SecuredResource,
    action: Action
  ): AuthorizationDecision =
    ctx.security match {
      case Some(sec) if sec.level == resource.securityLevel =>
        AuthorizationDecision.Allow
      case _ =>
        AuthorizationDecision.Deny
    }
}

// TEMPORARY (to be removed after demo)
trait SecuredResource {
  def securityLevel: SecurityLevel
}

// TEMPORARY (to be removed after demo)
final case class Action(
  name: String
)
