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
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AuthenticationRequest(
  attributes: Map[String, String]
) {
  def attribute(name: String): Option[String] =
    AuthenticationRequest.findFirst(attributes, Vector(name))

  def accessToken: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.AccessTokenKeys)

  def refreshToken: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.RefreshTokenKeys)

  def sessionId: Option[String] =
    AuthenticationRequest.findFirst(attributes, AuthenticationRequest.SessionIdKeys)
      .orElse(AuthenticationRequest.findCookieSession(attributes))
}

object AuthenticationRequest {
  val AccessTokenKeys: Vector[String] = Vector(
    "access_token",
    "token",
    "bearer_token",
    "authorization"
  )

  val RefreshTokenKeys: Vector[String] = Vector(
    "refresh_token",
    "refreshToken"
  )

  val SessionIdKeys: Vector[String] = Vector(
    "x-textus-session",
    "x-cncf-session",
    "session_id",
    "sessionId",
    "textus.session",
    "cncf.session"
  )

  private val CookieHeaderKeys: Vector[String] = Vector(
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
    findFirst(attributes, CookieHeaderKeys).flatMap(_session_cookie_id)

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

final case class AuthenticationResult(
  principalId: PrincipalId,
  attributes: Map[String, String] = Map.empty,
  capabilities: Set[Capability] = Set.empty,
  level: SecurityLevel = SecurityLevel("user"),
  subjectKind: SubjectKind = SubjectKind.User,
  session: Option[SessionContext] = None
) {
  def toSecurityContext: SecurityContext =
    SecurityContext(
      principal = new Principal {
        val id: PrincipalId = principalId
        val attributes: Map[String, String] = AuthenticationResult.defaultAttributes(principalId, attributes)
      },
      capabilities = capabilities,
      level = level,
      subjectKind = subjectKind,
      session = session
    )
}

object AuthenticationResult {
  def defaultAttributes(principalId: PrincipalId, attributes: Map[String, String]): Map[String, String] =
    Option(attributes).getOrElse(Map.empty[String, String]) ++ Map(
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
