package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.cncf.config.StandaloneUserProfileResolver
import org.goldenport.cncf.http.WebExecutionResolutionPolicy
import org.goldenport.cncf.subsystem.{Subsystem, SubsystemCurrentUserEvidence, SubsystemExecutionProfile}

/*
 * Admits fixed-user HOME profiles at the runtime boundary without selecting an
 * effective profile value. Field binding and provenance remain Phase 55 work.
 *
 * @since   Aug.  1, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] object RuntimeStandaloneUserProfileAdmission {
  type ProfileAdmission =
    SubsystemExecutionProfile => Consequence[Vector[StandaloneUserProfileResolver.Admitted]]
  private[cli] type SubsystemProfileResolution =
    Subsystem => Consequence[SubsystemExecutionProfile]

  def admit(
    subsystem: Subsystem,
    serverExecution: Boolean
  ): Consequence[Vector[StandaloneUserProfileResolver.Admitted]] =
    admit(subsystem, serverExecution, StandaloneUserProfileResolver.resolve, _.executionProfileC)

  private[cli] def admit(
    subsystem: Subsystem,
    serverExecution: Boolean,
    profileAdmission: ProfileAdmission
  ): Consequence[Vector[StandaloneUserProfileResolver.Admitted]] =
    admit(subsystem, serverExecution, profileAdmission, _.executionProfileC)

  private[cli] def admit(
    subsystem: Subsystem,
    serverExecution: Boolean,
    profileAdmission: ProfileAdmission,
    subsystemProfile: SubsystemProfileResolution
  ): Consequence[Vector[StandaloneUserProfileResolver.Admitted]] =
    _execution_profile(subsystem, serverExecution, subsystemProfile).flatMap {
      case profile @ SubsystemExecutionProfile(SubsystemCurrentUserEvidence.Fixed) =>
        _require_descriptor_owned_identity(subsystem).flatMap(_ => profileAdmission(profile))
      case SubsystemExecutionProfile(SubsystemCurrentUserEvidence.Authenticated | SubsystemCurrentUserEvidence.ControlledTest) =>
        Consequence.success(Vector.empty)
    }

  private def _execution_profile(
    subsystem: Subsystem,
    serverexecution: Boolean,
    subsystemprofile: SubsystemProfileResolution
  ): Consequence[SubsystemExecutionProfile] =
    if (serverexecution)
      WebExecutionResolutionPolicy
        .resolveForSubsystem(subsystem.configuration, subsystem)
        .flatMap { resolution =>
          subsystemprofile(subsystem).map {
            case SubsystemExecutionProfile(SubsystemCurrentUserEvidence.ControlledTest) =>
              SubsystemExecutionProfile.ControlledTest
            case _ =>
              resolution.policy.applicationMode.toSubsystemExecutionProfile
          }.recover {
            case _ => resolution.policy.applicationMode.toSubsystemExecutionProfile
          }
        }
    else
      subsystemprofile(subsystem)

  private def _require_descriptor_owned_identity(
    subsystem: Subsystem
  ): Consequence[Unit] =
    subsystem.descriptor
      .map(_.subsystemName.trim)
      .filter(_.nonEmpty)
      .map(_ => Consequence.unit)
      .getOrElse(
        Consequence.securityPermissionDenied(
          "Fixed-user profile admission requires descriptor-owned stable Subsystem identity."
        )
      )
}
