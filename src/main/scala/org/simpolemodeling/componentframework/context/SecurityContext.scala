package org.simplemodeling.componentframework.context

import java.time.Instant

/*
 * @since   Dec. 21, 2025
 * @version Dec. 21, 2025
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
)

trait Principal {
  def id: PrincipalId
  def attributes: Map[String, String]
}

final case class Capability(
  name: String
)

object SecurityContext {
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
