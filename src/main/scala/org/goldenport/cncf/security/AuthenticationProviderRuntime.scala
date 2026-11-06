package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.ResolvedAuthenticationProviderBinding
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}

/*
 * Shared authentication-provider traversal rules.
 *
 * @since   Apr. 23, 2026
 *  version Aug. 11, 2026
 * @version Nov.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object AuthenticationProviderRuntime {
  def providers(base: ExecutionContext): Vector[AuthenticationProvider] =
    _bound_providers(base).map(_._2)

  private def _bound_providers(base: ExecutionContext): Vector[(ResolvedAuthenticationProviderBinding, AuthenticationProvider)] =
    _subsystem_from_scope(base.cncfCore.scope)
      .toVector
      .flatMap(_.resolvedSecurityWiring.authentication.enabledProviders)
      .flatMap(binding => binding.provider.map(provider => (binding, provider)))

  def authenticate(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[AuthenticationResult]] =
    _first_authenticated_match(base, _.authenticate(request)(using base))

  def login(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[AuthenticationResult]] =
    _first_authenticated_match(base, _.login(request)(using base))

  def currentSession(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[AuthenticationResult]] =
    _first_authenticated_match(base, _.currentSession(request)(using base))

  def logout(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[org.goldenport.cncf.context.SessionContext]] =
    _first_match(providers(base), _.logout(request)(using base))

  private def _first_authenticated_match(
    base: ExecutionContext,
    f: AuthenticationProvider => Consequence[Option[AuthenticationResult]]
  ): Consequence[Option[AuthenticationResult]] =
    _first_match(_bound_providers(base), { (selected: (ResolvedAuthenticationProviderBinding, AuthenticationProvider)) =>
      val (binding, provider) = selected
      // Providers may cache immutable results; selection provenance belongs to this call.
      f(provider).map(_.map(result => SecurityAuthenticationProvenance.withProviderIdentity(result.copy(),
        AuthenticationProviderIdentity(binding.name, provider.name, binding.componentName))))
    })

  private def _first_match[A, B](
    providers: Vector[B],
    f: B => Consequence[Option[A]]
  ): Consequence[Option[A]] = {
    def _go_(rest: Vector[B]): Consequence[Option[A]] =
      rest.headOption match {
        case Some(provider) =>
          f(provider).flatMap {
            case Some(result) => Consequence.success(Some(result))
            case None => _go_(rest.tail)
          }
        case None =>
          Consequence.success(None)
      }

    _go_(providers)
  }

  @annotation.tailrec
  private def _subsystem_from_scope(
    scope: ScopeContext
  ): Option[org.goldenport.cncf.subsystem.Subsystem] =
    scope match {
      case cc: Component.Context =>
        cc.component.subsystem
      case other =>
        other.parent match {
          case Some(parent) => _subsystem_from_scope(parent)
          case None => None
        }
    }
}
