package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, SecurityContext, SecurityLevel, SessionContext, SubjectKind}

/*
 * Shared ingress authentication contract.
 *
 * - Authentication providers belong to deployed components.
 * - CNCF ingress resolves request attributes through providers and normalizes the
 *   result into the common SecurityContext carried by ExecutionContext.
 * - Success(Some(result)) means the provider accepted the request and authenticated it.
 * - Success(None) means the provider did not match the request.
 * - Failure means the provider matched but authentication failed operationally.
 *
 * @since   Apr.  9, 2026
 *  version Jun.  5, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AuthenticationRequest(
  attributes: Map[String, String]
) {
  def attribute(name: String): Option[String] =
    AuthenticationRequest.findFirst(attributes, Vector(name))

  def accessToken: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.ACCESS_TOKEN_KEYS)

  def refreshToken: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.REFRESH_TOKEN_KEYS)

  def federationProvider: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.FEDERATION_PROVIDER_KEYS)

  def federationAssertion: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.FEDERATION_ASSERTION_KEYS)

  def federationAuthorizationCode: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.FEDERATION_AUTHORIZATION_CODE_KEYS)

  def federationCallbackState: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.FEDERATION_CALLBACK_STATE_KEYS)

  def hasFederationCallbackMaterial: Boolean =
    federationAssertion.isDefined ||
      federationAuthorizationCode.isDefined ||
      federationCallbackState.isDefined

  def sessionId: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.SESSION_ID_KEYS)
      .orElse(AuthenticationRequest.findCookieSession(attributes))
      .flatMap(SessionId.option)
      .map(_.value)

  def typedSessionId: Option[SessionId] =
    sessionId.flatMap(SessionId.option)
}

object AuthenticationRequest {
  val ACCESS_TOKEN_KEYS: Vector[String] = Vector(
    "access_token",
    "token",
    "bearer_token",
    "authorization"
  )

  val REFRESH_TOKEN_KEYS: Vector[String] = Vector(
    "refresh_token",
    "refreshToken"
  )

  val FEDERATION_PROVIDER_KEYS: Vector[String] = Vector(
    "federation.provider",
    "federation_provider"
  )

  val FEDERATION_ASSERTION_KEYS: Vector[String] = Vector(
    "federation.assertion",
    "federation_assertion"
  )

  val FEDERATION_AUTHORIZATION_CODE_KEYS: Vector[String] = Vector(
    "federation.authorization_code",
    "federation_authorization_code"
  )

  val FEDERATION_CALLBACK_STATE_KEYS: Vector[String] = Vector(
    "federation.state",
    "federation_state"
  )

  val SESSION_ID_KEYS: Vector[String] = Vector(
    "x-textus-session",
    "x-cncf-session",
    "session_id",
    "sessionId",
    "textus.session",
    "cncf.session"
  )

  private val _cookie_header_keys: Vector[String] = Vector(
    "cookie"
  )

  def findFirst(attributes: Map[String, String], keys: Vector[String]): Option[String] = {
    val normalized = attributes.map { case (k, v) => normalizeToken(k) -> v }
    keys.iterator.flatMap(key => normalized.get(normalizeToken(key))).collectFirst {
      case value if value.trim.nonEmpty =>
        val trimmed = value.trim
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) trimmed.substring(7).trim else trimmed
    }
  }

  def normalizeToken(p: String): String =
    p.trim.toLowerCase.replace("_", "").replace("-", "")

  def findCookieSession(attributes: Map[String, String]): Option[String] =
    findFirst(attributes, _cookie_header_keys).flatMap(_session_cookie_id)

  def redactSensitiveAttributes(attributes: Map[String, String]): Map[String, String] =
    Option(attributes).getOrElse(Map.empty[String, String]).filterNot { case (key, _) =>
      _is_sensitive_attribute_key(key)
    }

  private val _sensitive_attribute_keys: Set[String] = Vector(
    "password",
    "credential",
    "secret",
    "authorization",
    "access_token",
    "refresh_token",
    "bearer_token",
    "id_token",
    "token"
  ).++(
    FEDERATION_ASSERTION_KEYS
  ).++(
    FEDERATION_AUTHORIZATION_CODE_KEYS
  ).++(
    FEDERATION_CALLBACK_STATE_KEYS
  ).map(normalizeToken).toSet

  private val _sensitive_attribute_segments: Set[String] = Set(
    "password",
    "credential",
    "secret",
    "token",
    "assertion"
  )

  private val _federation_attribute_segments: Set[String] = Set(
    "federation",
    "oauth",
    "oidc"
  )

  private def _is_sensitive_attribute_key(key: String): Boolean = {
    val normalized = normalizeToken(key)
    val segments = _attribute_key_segments(key)
    _sensitive_attribute_keys.contains(normalized) ||
      segments.exists(_sensitive_attribute_segments.contains) ||
      (segments.contains("authorization") && segments.contains("code")) ||
      (segments.exists(_federation_attribute_segments.contains) && segments.contains("state"))
  }

  private def _attribute_key_segments(key: String): Set[String] =
    Option(key)
      .toVector
      .flatMap(_.replaceAll("([a-z0-9])([A-Z])", "$1 $2").split("[^A-Za-z0-9]+"))
      .map(_.toLowerCase(java.util.Locale.ROOT))
      .filter(_.nonEmpty)
      .toSet

  private def _session_cookie_id(value: String): Option[String] =
    Option(value)
      .toVector
      .flatMap(_.split(";"))
      .iterator
      .map(_.trim)
      .collectFirst {
        case token if _is_session_cookie(token) =>
          token.dropWhile(_ != '=').drop(1).trim
      }
      .filter(_.nonEmpty)

  private def _is_session_cookie(token: String): Boolean = {
    val key = token.takeWhile(_ != '=').trim.toLowerCase(java.util.Locale.ROOT)
    key == "textus-session" ||
      key == "cncf-session" ||
      key.startsWith("textus-session-") ||
      key.startsWith("cncf-session-")
  }
}

final case class SessionId private (value: String) {
  override def toString: String = value
}

object SessionId {
  val LENGTH_MIN = 1
  val LENGTH_MAX = 64

  def option(value: String): Option[SessionId] =
    Option(value)
      .map(_.trim)
      .filter(isValid)
      .map(new SessionId(_))

  def create(value: String): Consequence[SessionId] =
    option(value)
      .map(Consequence.success)
      .getOrElse(Consequence.valueInvalid(s"Invalid SessionId length: ${Option(value).map(_.length).getOrElse(0)}"))

  def unsafe(value: String): SessionId =
    option(value).getOrElse(throw new IllegalArgumentException(s"Invalid SessionId length: ${Option(value).map(_.length).getOrElse(0)}"))

  def isValid(value: String): Boolean =
    value.nonEmpty && value.length >= LENGTH_MIN && value.length <= LENGTH_MAX
}

final case class PublicPrincipalId private (value: String) {
  override def toString: String = value
}

object PublicPrincipalId {
  val LENGTH_MIN = 1
  val LENGTH_MAX = 64

  def option(value: String): Option[PublicPrincipalId] =
    Option(value)
      .map(_.trim)
      .filter(isValid)
      .map(new PublicPrincipalId(_))

  def create(value: String): Consequence[PublicPrincipalId] =
    option(value)
      .map(Consequence.success)
      .getOrElse(Consequence.valueInvalid(s"Invalid PublicPrincipalId length: ${Option(value).map(_.length).getOrElse(0)}"))

  def unsafe(value: String): PublicPrincipalId =
    option(value).getOrElse(throw new IllegalArgumentException(s"Invalid PublicPrincipalId length: ${Option(value).map(_.length).getOrElse(0)}"))

  def isValid(value: String): Boolean =
    value.nonEmpty && value.length >= LENGTH_MIN && value.length <= LENGTH_MAX
}

final case class AuthenticationResult(
  principalId: PrincipalId,
  attributes: Map[String, String] = Map.empty,
  capabilities: Set[Capability] = Set.empty,
  level: SecurityLevel = SecurityLevel("user"),
  subjectKind: SubjectKind = SubjectKind.User,
  session: Option[SessionContext] = None
) {
  def toSecurityContext: SecurityContext = {
    val resolvedattributes = AuthenticationResult.defaultAttributes(principalId, attributes)
    SecurityContext(
      principal = new Principal {
        val id: PrincipalId = principalId
        val attributes: Map[String, String] = resolvedattributes
      },
      capabilities = capabilities,
      level = level,
      subjectKind = subjectKind,
      session = session
    )
  }
}

object AuthenticationResult {
  def defaultAttributes(principalId: PrincipalId, attributes: Map[String, String]): Map[String, String] =
    AuthenticationRequest.redactSensitiveAttributes(attributes) ++ Map(
      "principal_id" -> principalId.value,
      "authenticated" -> "true"
    )
}

trait AuthenticationProvider {
  def name: String

  // Contract:
  // - Success(Some(result)): accepted and authenticated.
  // - Success(None): request did not match this provider.
  // - Failure: matched request but authentication failed deterministically.
  def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]]

  def login(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
    Consequence.success(None)

  def logout(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[SessionContext]] =
    Consequence.success(None)

  def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
    authenticate(request)
}
