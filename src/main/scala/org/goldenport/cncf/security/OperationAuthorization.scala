package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.record.Record

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationAuthorizationRule(
  operationModes: Vector[OperationMode] = Vector.empty,
  allowAnonymous: Boolean = false,
  anonymousOperationModes: Vector[OperationMode] = Vector.empty,
  deny: Boolean = false,
  requireAuthenticated: Boolean = false,
  requireProviderAuthentication: Boolean = false,
  minimumPrivilege: Option[String] = None,
  roles: Vector[String] = Vector.empty,
  scopes: Vector[String] = Vector.empty,
  capabilities: Vector[String] = Vector.empty
)

object OperationAuthorizationRule {
  def developAnonymousAdmin(
    runtimeConfig: RuntimeConfig
  ): OperationAuthorizationRule =
    OperationAuthorizationRule(
      allowAnonymous = runtimeConfig.webDevelopAnonymousAdmin,
      anonymousOperationModes = Vector(OperationMode.Develop, OperationMode.Test)
    )

  def fromRecord(record: Record): OperationAuthorizationRule =
    OperationAuthorizationRule(
      operationModes = _operation_modes(record, "operationModes", "operation_modes", "modes"),
      allowAnonymous = _boolean(record, "allowAnonymous", "allow_anonymous").getOrElse(false),
      anonymousOperationModes = _operation_modes(record, "anonymousOperationModes", "anonymous_operation_modes", "anonymousModes", "anonymous_modes"),
      deny = _boolean(record, "deny", "denied").getOrElse(false),
      requireAuthenticated = _boolean(record, "requireAuthenticated", "require_authenticated", "authenticated").getOrElse(false),
      requireProviderAuthentication = _boolean(record, "requireProviderAuthentication", "require_provider_authentication", "providerAuthenticated", "provider_authenticated").getOrElse(false),
      minimumPrivilege = _string(record, "minimumPrivilege", "minimum_privilege", "privilege"),
      roles = _string_vector(record, List("roles", "role")),
      scopes = _string_vector(record, List("scopes", "scope")),
      capabilities = _string_vector(record, List("capabilities", "capability"))
    )

  private def _string(
    record: Record,
    keys: String*
  ): Option[String] =
    keys.iterator.map(record.getString).collectFirst {
      case Some(s) if s.trim.nonEmpty => s.trim
    }

  private def _operation_modes(
    record: Record,
    keys: String*
  ): Vector[OperationMode] =
    _string_vector(record, keys.toList).flatMap(OperationMode.from)

  private def _boolean(record: Record, keys: String*): Option[Boolean] =
    keys.iterator.map(record.getString).collectFirst {
      case Some(s) if _truthy(s) => true
      case Some(s) if _falsy(s) => false
    }

  private def _truthy(s: String): Boolean = {
    val v = s.trim.toLowerCase(java.util.Locale.ROOT)
    v == "true" || v == "yes" || v == "on" || v == "1"
  }

  private def _falsy(s: String): Boolean = {
    val v = s.trim.toLowerCase(java.util.Locale.ROOT)
    v == "false" || v == "no" || v == "off" || v == "0"
  }

  private def _string_vector(
    record: Record,
    keys: List[String]
  ): Vector[String] =
    keys.iterator.map(record.getAny).collectFirst {
      case Some(xs: Seq[?]) =>
        xs.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(s: String) if s.trim.nonEmpty =>
        s.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty)
    }.getOrElse(Vector.empty)
}

trait OperationAuthorizationProvider {
  def operationAuthorization(
    runtimeConfig: RuntimeConfig
  ): OperationAuthorizationRule
}

object OperationAuthorization {
  def authorize(
    selector: String,
    rule: OperationAuthorizationRule
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (rule.deny)
      _denied(selector, "denied")
    else if (rule.operationModes.nonEmpty && !rule.operationModes.contains(ctx.operationMode))
      _denied(selector, "operation-mode")
    else if (ctx.security.subjectKind == SubjectKind.Anonymous && !rule.allowAnonymous)
      _denied(selector, "anonymous")
    else if (
      ctx.security.subjectKind == SubjectKind.Anonymous &&
        rule.anonymousOperationModes.nonEmpty &&
        !rule.anonymousOperationModes.contains(ctx.operationMode)
    )
      _denied(selector, "anonymous-operation-mode")
    else if (rule.requireAuthenticated && !SecuritySubject.current.isAuthenticated)
      _denied(selector, "authenticated")
    else if (rule.requireProviderAuthentication && !SecuritySubject.current.isProviderAuthenticated)
      _denied(selector, "provider-authentication")
    else if (rule.minimumPrivilege.exists(x => !ctx.security.hasPrivilegeAtLeast(x)))
      _denied(selector, "minimum-privilege")
    else if (!_roles_allowed(rule.roles))
      _denied(selector, "role")
    else if (!_scopes_allowed(rule.scopes))
      _denied(selector, "scope")
    else if (!_capabilities_allowed(rule.capabilities))
      _denied(selector, "capability")
    else
      Consequence.unit

  private def _roles_allowed(
    required: Vector[String]
  )(using ctx: ExecutionContext): Boolean =
    _category_allowed(required, SecuritySubject.current.roles)

  private def _scopes_allowed(
    required: Vector[String]
  )(using ctx: ExecutionContext): Boolean =
    _category_allowed(required, SecuritySubject.current.scopes)

  private def _capabilities_allowed(
    required: Vector[String]
  )(using ctx: ExecutionContext): Boolean =
    _category_allowed(required, SecuritySubject.current.capabilities)

  private def _category_allowed(
    required: Vector[String],
    actual: Set[String]
  ): Boolean =
    required.isEmpty || required.exists(x => actual.contains(SecuritySubject.normalize(x)))

  private def _denied[A](
    selector: String,
    reason: String
  )(using ctx: ExecutionContext): Consequence[A] =
    Consequence.securityPermissionDenied(
      s"Operation access is denied: ${selector}",
      Seq(
        Descriptor.Facet.Name("operation-authorization"),
        Descriptor.Facet.Parameter.argument("selector"),
        Descriptor.Facet.Value(selector),
        Descriptor.Facet.Parameter.argument("reason"),
        Descriptor.Facet.Value(reason)
      )
    )
}
