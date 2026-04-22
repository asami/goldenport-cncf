package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}

/*
 * Shared authentication-provider traversal rules.
 *
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object AuthenticationProviderRuntime {
  def providers(base: ExecutionContext): Vector[AuthenticationProvider] =
    _subsystem_from_scope(base.cncfCore.scope)
      .toVector
      .flatMap(_.resolvedSecurityWiring.authentication.enabledProviders)
      .flatMap(_.provider)

  def authenticate(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[AuthenticationResult]] =
    _first_match_(providers(base), _.authenticate(request)(using base))

  def login(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[AuthenticationResult]] =
    _first_match_(providers(base), _.login(request)(using base))

  def current_session(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[AuthenticationResult]] =
    _first_match_(providers(base), _.currentSession(request)(using base))

  def logout(
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[org.goldenport.cncf.context.SessionContext]] =
    _first_match_(providers(base), _.logout(request)(using base))

  private def _first_match_[A](
    providers: Vector[AuthenticationProvider],
    f: AuthenticationProvider => Consequence[Option[A]]
  ): Consequence[Option[A]] = {
    def go(rest: Vector[AuthenticationProvider]): Consequence[Option[A]] =
      rest.headOption match {
        case Some(provider) =>
          f(provider).flatMap {
            case Some(result) => Consequence.success(Some(result))
            case None => go(rest.tail)
          }
        case None =>
          Consequence.success(None)
      }

    go(providers)
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
