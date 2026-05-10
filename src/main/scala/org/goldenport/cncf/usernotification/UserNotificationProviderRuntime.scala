package org.goldenport.cncf.usernotification

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.observation.{Cause, Descriptor}

/*
 * Shared user-notification provider traversal rules.
 *
 * @since   May.  7, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object UserNotificationProviderRuntime:
  def providers(base: ExecutionContext): Vector[UserNotificationProvider] =
    _subsystem_from_scope(base.cncfCore.scope)
      .toVector
      .flatMap(_.resolvedSecurityWiring.userNotification.enabledProviders)
      .flatMap(_.provider)

  def notify(
    base: ExecutionContext,
    request: UserNotificationRequest
  ): Consequence[UserNotificationResult] =
    providers(base).headOption match
      case Some(provider) => provider.notify(request)(using base)
      case None => Consequence.serviceUnavailable(
        "No user-notification provider is configured for the current subsystem.",
        Cause.Kind.Capability,
        Seq(
          Descriptor.Facet.Service("user-notification"),
          Descriptor.Facet.Name("user-notification-provider"),
          Descriptor.Facet.State("provider-unavailable")
        )
      )

  @annotation.tailrec
  private def _subsystem_from_scope(
    scope: ScopeContext
  ): Option[org.goldenport.cncf.subsystem.Subsystem] =
    scope match
      case cc: Component.Context =>
        cc.component.subsystem
      case other =>
        other.parent match
          case Some(parent) => _subsystem_from_scope(parent)
          case None => None
