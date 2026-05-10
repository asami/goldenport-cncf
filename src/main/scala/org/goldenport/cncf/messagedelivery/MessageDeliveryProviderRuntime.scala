package org.goldenport.cncf.messagedelivery

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.observation.{Cause, Descriptor}

/*
 * Shared message-delivery provider traversal rules.
 *
 * @since   Apr. 23, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object MessageDeliveryProviderRuntime:
  def providers(base: ExecutionContext): Vector[MessageDeliveryProvider] =
    _subsystem_from_scope(base.cncfCore.scope)
      .toVector
      .flatMap(_.resolvedSecurityWiring.messageDelivery.enabledProviders)
      .flatMap(_.provider)

  def send(
    base: ExecutionContext,
    message: UnifiedMessage
  ): Consequence[MessageDeliveryResult] =
    providers(base).headOption match
      case Some(provider) => provider.send(message)(using base)
      case None => Consequence.serviceUnavailable(
        "No message-delivery provider is configured for the current subsystem.",
        Cause.Kind.Capability,
        Seq(
          Descriptor.Facet.Service("message-delivery"),
          Descriptor.Facet.Name("message-delivery-provider"),
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
