package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.configuration.{ConfigurationOrigin, ConfigurationResolution, ConfigurationValue, ResolvedConfiguration}

/*
 * The user-context admission mode belongs to one stable Subsystem.  It is
 * neither a Web selector nor a SystemNode/JVM-wide execution setting.
 *
 * @since   Aug.  1, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
enum SubsystemUserMode(val name: String) {
  case Standalone extends SubsystemUserMode("standalone")
  case MultiUser extends SubsystemUserMode("multi-user")

  def toExecutionProfile: SubsystemExecutionProfile = this match {
    case SubsystemUserMode.Standalone => SubsystemExecutionProfile.Fixed
    case SubsystemUserMode.MultiUser => SubsystemExecutionProfile.Authenticated
  }
}

final case class SubsystemUserModeResolution(
  mode: SubsystemUserMode,
  trace: ConfigurationResolution
)

object SubsystemUserMode {
  val CONFIGURATION_KEY = "textus.subsystem.user-mode"
  val DIRECT_COMPONENT_STANDALONE_SOURCE_TYPE = "derived-default"
  val DIRECT_COMPONENT_STANDALONE_SOURCE_ID = "textus-direct-component-standalone"
  val CONTROLLED_TEST_STANDALONE_SOURCE_TYPE = "controlled-test"
  val CONTROLLED_TEST_STANDALONE_SOURCE_ID = "textus-controlled-test-standalone"
  val FIXED_CONTEXT_COMPATIBLE_CAPABILITY = "user.fixed-context-compatible@1"

  def parse(value: String): Option[SubsystemUserMode] =
    Option(value).flatMap {
      case "standalone" => Some(SubsystemUserMode.Standalone)
      case "multi-user" => Some(SubsystemUserMode.MultiUser)
      case _ => None
    }

  def canonicalConfigurationValue(
    configuration: ResolvedConfiguration
  ): Option[ConfigurationValue] =
    _canonical_configuration_value(configuration.configuration.values, CONFIGURATION_KEY.split('.').toList)

  def resolveForSubsystem(
    configuration: ResolvedConfiguration,
    subsystem: Subsystem
  ): Consequence[SubsystemUserModeResolution] =
    canonicalConfigurationValue(configuration) match {
      case Some(ConfigurationValue.StringValue(value)) =>
        parse(value)
          .map(mode => _validate(mode, subsystem).map(_ => SubsystemUserModeResolution(
            mode,
            configuration.trace.get(CONFIGURATION_KEY).getOrElse(_trace(mode))
          )))
          .getOrElse(Consequence.argumentFormatError(CONFIGURATION_KEY, "standalone or multi-user", value))
      case Some(value) => Consequence.argumentFormatError(
        CONFIGURATION_KEY,
        "string standalone or multi-user",
        value.toString
      )
      case None => ConfigurationAccess.getString(configuration, CONFIGURATION_KEY) match {
        case Some(value) =>
          parse(value)
            .map(mode => _validate(mode, subsystem).map(_ => SubsystemUserModeResolution(
              mode,
              configuration.trace.get(CONFIGURATION_KEY).getOrElse(_trace(mode))
            )))
            .getOrElse(Consequence.argumentFormatError(CONFIGURATION_KEY, "standalone or multi-user", value))
        case None => _direct_component_standalone_c(subsystem)
      }
    }

  private def _canonical_configuration_value(
    values: Map[String, ConfigurationValue],
    segments: List[String]
  ): Option[ConfigurationValue] =
    values.get(CONFIGURATION_KEY).orElse {
      segments match {
        case Nil => None
        case head :: Nil => values.get(head)
        case head :: tail => values.get(head) match {
          case Some(ConfigurationValue.ObjectValue(nested)) =>
            _canonical_configuration_value(nested, tail)
          case _ => None
        }
      }
    }

  private def _validate(
    mode: SubsystemUserMode,
    subsystem: Subsystem
  ): Consequence[Unit] =
    subsystem.executionProfileForUserModeC(mode).flatMap { profile =>
      profile.currentUserEvidence match {
        case SubsystemCurrentUserEvidence.ControlledTest => Consequence.unit
        case SubsystemCurrentUserEvidence.Fixed if mode == SubsystemUserMode.Standalone => Consequence.unit
        case SubsystemCurrentUserEvidence.Authenticated if mode == SubsystemUserMode.MultiUser => Consequence.unit
        case SubsystemCurrentUserEvidence.Fixed => Consequence.securityPermissionDenied(
            s"$CONFIGURATION_KEY=multi-user requires authenticated-user wiring for the Subsystem."
          )
        case SubsystemCurrentUserEvidence.Authenticated => Consequence.securityPermissionDenied(
            s"$CONFIGURATION_KEY=standalone requires fixed-user wiring for the Subsystem."
          )
      }
    }

  private def _direct_component_standalone_c(
    subsystem: Subsystem
  ): Consequence[SubsystemUserModeResolution] =
    subsystem.executionProfileForUserModeC(SubsystemUserMode.Standalone).flatMap { profile =>
      profile.currentUserEvidence match {
        case SubsystemCurrentUserEvidence.ControlledTest =>
          _default(CONTROLLED_TEST_STANDALONE_SOURCE_TYPE, CONTROLLED_TEST_STANDALONE_SOURCE_ID)
        case SubsystemCurrentUserEvidence.Fixed if subsystem.directComponentProvides(FIXED_CONTEXT_COMPATIBLE_CAPABILITY) =>
          _default(DIRECT_COMPONENT_STANDALONE_SOURCE_TYPE, DIRECT_COMPONENT_STANDALONE_SOURCE_ID)
        case SubsystemCurrentUserEvidence.Fixed => Consequence.argumentMissing(
            s"$CONFIGURATION_KEY (direct Component requires $FIXED_CONTEXT_COMPATIBLE_CAPABILITY)"
          )
        case _ => Consequence.securityPermissionDenied(
            s"Missing $CONFIGURATION_KEY requires fixed-user direct-Component execution evidence."
          )
      }
    }

  private def _default(
    sourcetype: String,
    sourceid: String
  ): Consequence[SubsystemUserModeResolution] =
    Consequence.success(SubsystemUserModeResolution(
      SubsystemUserMode.Standalone,
      _trace(SubsystemUserMode.Standalone, ConfigurationOrigin.Default, Some(sourcetype), Some(sourceid))
    ))

  private def _trace(
    mode: SubsystemUserMode,
    origin: ConfigurationOrigin = ConfigurationOrigin.Default,
    sourcetype: Option[String] = None,
    sourceid: Option[String] = None
  ): ConfigurationResolution =
    ConfigurationResolution(
      key = CONFIGURATION_KEY,
      finalValue = ConfigurationValue.StringValue(mode.name),
      origin = origin,
      history = Nil,
      sourceType = sourcetype,
      sourceId = sourceid
    )
}
