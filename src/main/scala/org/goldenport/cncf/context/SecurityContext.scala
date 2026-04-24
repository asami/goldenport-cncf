package org.goldenport.cncf.context

import java.time.Instant
import org.goldenport.id.UniversalId

/*
 * @since   Dec. 21, 2025
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PrincipalId(
  value: String
)

final case class SecurityLevel(
  value: String
)

enum SubjectKind {
  case User, Subsystem, Service, Anonymous, Unspecified
}

final case class SessionContext(
  sessionId: Option[String] = None,
  tokenId: Option[String] = None,
  tokenKind: Option[String] = None,
  issuer: Option[String] = None,
  audience: Option[String] = None,
  authenticatedAt: Option[Instant] = None,
  expiresAt: Option[Instant] = None,
  refreshSessionId: Option[String] = None,
  attributes: Map[String, String] = Map.empty
)

sealed trait AuditOutcome

final case class SecurityContext(
  principal: Principal,
  capabilities: Set[Capability],
  level: SecurityLevel,
  subjectKind: SubjectKind = SubjectKind.Unspecified,
  session: Option[SessionContext] = None
) {
  def hasCapability(name: String): Boolean = {
    val target = _normalize_token(name)
    capabilities.exists(c => _normalize_token(c.name) == target)
  }

  def hasAnyCapability(names: Iterable[String]): Boolean = {
    val targets = names.map(_normalize_token).toSet
    capabilities.exists(c => targets.contains(_normalize_token(c.name)))
  }

  def hasPrivilegeAtLeast(name: String): Boolean =
    SecurityContext.Privilege.rankOf(name) match {
      case target if target >= 0 => SecurityContext.Privilege.rankOf(this) >= target
      case _ => false
    }

  private def _normalize_token(p: String): String =
    p.trim.toLowerCase.replace("_", "").replace("-", "")
}

trait Principal {
  def id: PrincipalId
  def attributes: Map[String, String]
}

final case class Capability(
  name: String
)

object SecurityContext {
  private def _normalize_token(
    p: String
  ): String =
    Option(p).getOrElse("").trim.toLowerCase.replace("_", "").replace("-", "")

  sealed trait Privilege {
    def name: String
    def rank: Int
    def principalId: PrincipalId
    def attributes: Map[String, String]
    def capabilities: Set[Capability]
    def level: SecurityLevel
    def subjectKind: SubjectKind = SubjectKind.User
  }

  object Privilege {
    case object Anonymous extends Privilege {
      val name: String = "anonymous"
      val rank: Int = 0
      val principalId: PrincipalId = PrincipalId("anonymous")
      val attributes: Map[String, String] = Map(
        "anonymous" -> "true",
        "role" -> "anonymous",
        "privilege" -> "anonymous"
      )
      val capabilities: Set[Capability] = Set(
        Capability("anonymous")
      )
      val level: SecurityLevel = SecurityLevel("anonymous")
      override val subjectKind: SubjectKind = SubjectKind.Anonymous
    }

    case object User extends Privilege {
      val name: String = "user"
      val rank: Int = 10
      val principalId: PrincipalId = PrincipalId("test-user-principal")
      val attributes: Map[String, String] = Map(
        "role" -> "user",
        "privilege" -> "user"
      )
      val capabilities: Set[Capability] = Set(
        Capability("user")
      )
      val level: SecurityLevel = SecurityLevel("user")
    }

    case object ApplicationContentManager extends Privilege {
      val name: String = "application_content_manager"
      val rank: Int = 20
      val principalId: PrincipalId = PrincipalId("test-app-content-manager-principal")
      val attributes: Map[String, String] = Map(
        "role" -> "content_manager",
        "privilege" -> "application_content_manager"
      )
      val capabilities: Set[Capability] = Set(
        Capability("content_manager"),
        Capability("content_admin")
      )
      val level: SecurityLevel = SecurityLevel("content_manager")
    }

    case object Operator extends Privilege {
      val name: String = "operator"
      val rank: Int = 20
      val principalId: PrincipalId = PrincipalId("test-operator-principal")
      val attributes: Map[String, String] = Map(
        "role" -> "operator",
        "privilege" -> "operator"
      )
      val capabilities: Set[Capability] = Set(
        Capability("operator")
      )
      val level: SecurityLevel = SecurityLevel("operator")
    }

    case object System extends Privilege {
      val name: String = "system"
      val rank: Int = 30
      val principalId: PrincipalId = PrincipalId("test-system-principal")
      val attributes: Map[String, String] = Map(
        "role" -> "system",
        "privilege" -> "system"
      )
      val capabilities: Set[Capability] = Set(
        Capability("system")
      )
      val level: SecurityLevel = SecurityLevel("system")
    }

    case object Internal extends Privilege {
      val name: String = "internal"
      val rank: Int = 40
      val principalId: PrincipalId = PrincipalId("test-internal-principal")
      val attributes: Map[String, String] = Map(
        "role" -> "internal",
        "privilege" -> "internal"
      )
      val capabilities: Set[Capability] = Set(
        Capability("internal")
      )
      val level: SecurityLevel = SecurityLevel("internal")
    }

    def fromName(name: String): Option[Privilege] =
      _normalize_token(name) match {
        case "anonymous" => Some(Anonymous)
        case "user" => Some(User)
        case "applicationcontentmanager" | "contentmanager" | "contentadmin" => Some(ApplicationContentManager)
        case "operator" => Some(Operator)
        case "system" | "systemoperator" | "systemadmin" => Some(System)
        case "internal" | "subsystem" | "service" | "component" => Some(Internal)
        case _ => None
      }

    def rankOf(name: String): Int =
      fromName(name).map(_.rank).getOrElse(-1)

    def rankOf(security: SecurityContext): Int = {
      val attributes = security.principal.attributes
      val candidate =
        attributes.get("privilege")
          .orElse(attributes.get("privileges"))
          .flatMap(_.split("[,\\s|]+").headOption)
          .flatMap(fromName)
          .map(_.rank)
      candidate
        .orElse(fromName(security.level.value).map(_.rank))
        .getOrElse {
          if (security.subjectKind == SubjectKind.Anonymous) Anonymous.rank
          else User.rank
        }
    }
  }

  final case class AuditEvent(
    executionId: UniversalId,
    principalId: Option[PrincipalId],
    operation: String,
    timestamp: Instant,
    outcome: AuditOutcome
  )

  trait AuditEmitter {
    def emit(event: AuditEvent): Unit
  }
}
