package org.goldenport.cncf.subsystem

/*
 * Ingress-independent current-user evidence required to construct a
 * component-facing execution context. This is not the existing deterministic
 * context.ExecutionProfile and deliberately has no operation-mode, Web-mode,
 * datastore, or arbitrary-properties surface.
 *
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
enum SubsystemCurrentUserEvidence {
  case Fixed
  case Authenticated
  case ControlledTest
}

final case class SubsystemExecutionProfile(
  currentUserEvidence: SubsystemCurrentUserEvidence
)

object SubsystemExecutionProfile {
  val Fixed: SubsystemExecutionProfile =
    SubsystemExecutionProfile(SubsystemCurrentUserEvidence.Fixed)
  val Authenticated: SubsystemExecutionProfile =
    SubsystemExecutionProfile(SubsystemCurrentUserEvidence.Authenticated)
  val ControlledTest: SubsystemExecutionProfile =
    SubsystemExecutionProfile(SubsystemCurrentUserEvidence.ControlledTest)
}
