package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, SecurityContext, SecurityLevel, SessionContext, SubjectKind}

/*
 * Shared ingress authentication contract.
 *
 * - Authentication providers belong to deployed components.
 * - CNCF ingress resolves request attributes through providers and normalizes the
 *   result into the common SecurityContext carried by ExecutionContext.
 *
 * @since   Apr.  9, 2026
 * @version Apr.  9, 2026
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
    attributes ++ Map(
      "principal_id" -> principalId.value,
      "authenticated" -> "true"
    )
}

trait AuthenticationProvider {
  def name: String
  def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]]
}
