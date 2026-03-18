package org.goldenport.cncf.context

import java.time.Instant
import org.goldenport.id.UniversalId

/*
 * @since   Dec. 21, 2025
 * @version Mar. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PrincipalId(
  value: String
)

final case class SecurityLevel(
  value: String
)

sealed trait AuditOutcome

final case class SecurityContext(
  principal: Principal,
  capabilities: Set[Capability],
  level: SecurityLevel
) {
  def hasCapability(name: String): Boolean = {
    val target = _normalize_token(name)
    capabilities.exists(c => _normalize_token(c.name) == target)
  }

  def hasAnyCapability(names: Iterable[String]): Boolean = {
    val targets = names.map(_normalize_token).toSet
    capabilities.exists(c => targets.contains(_normalize_token(c.name)))
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
  sealed trait Privilege {
    def principalId: PrincipalId
    def attributes: Map[String, String]
    def capabilities: Set[Capability]
    def level: SecurityLevel
  }

  object Privilege {
    case object User extends Privilege {
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
