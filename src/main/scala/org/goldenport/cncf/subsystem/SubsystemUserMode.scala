package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationTarget, ConfigurationAccess}
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationOrigin, ConfigurationResolution, ConfigurationValue, ResolvedConfiguration}

/*
 * The user-context admission mode belongs to one stable Subsystem.  It is
 * neither a Web selector nor a SystemNode/JVM-wide execution setting.
 *
 * @since   Aug.  1, 2026
 * @version Aug.  3, 2026
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
      case Some(value) => _resolve_catalog_value(
        value,
        configuration.trace.get(CONFIGURATION_KEY),
        subsystem
      )
      case None => ConfigurationAccess.getString(configuration, CONFIGURATION_KEY) match {
        case Some(value) => _resolve_catalog_value(
          ConfigurationValue.StringValue(value),
          configuration.trace.get(CONFIGURATION_KEY),
          subsystem
        )
        case None => _direct_component_standalone_c(subsystem)
      }
    }

  def resolveRuntimeBindingForSubsystem(
    binding: ConfigurationBinding[SubsystemUserMode, CncfConfigurationTarget],
    subsystem: Subsystem
  ): Consequence[SubsystemUserModeResolution] =
    if (binding == null)
      Consequence.configurationInvalid("runtime Subsystem user-mode binding is required")
    else
      _validate(binding.value, subsystem).flatMap { _ =>
        binding.parameter.codec.encode(binding.value).map { value =>
          SubsystemUserModeResolution(
            binding.value,
            _runtime_binding_trace(binding, value)
          )
        }
      }

  private[cncf] def resolveRuntimeBindingAbsentForSubsystem(
    subsystem: Subsystem
  ): Consequence[SubsystemUserModeResolution] =
    _direct_component_standalone_c(subsystem)

  private def _resolve_catalog_value(
    value: ConfigurationValue,
    trace: Option[ConfigurationResolution],
    subsystem: Subsystem
  ): Consequence[SubsystemUserModeResolution] =
    CncfConfigurationParameterCatalog.subsystemUserMode.codec.decode(value).flatMap { mode =>
      _validate(mode, subsystem).map(_ => SubsystemUserModeResolution(
        mode,
        trace.getOrElse(_trace(mode))
      ))
    }

  private def _runtime_binding_trace(
    binding: ConfigurationBinding[SubsystemUserMode, CncfConfigurationTarget],
    value: ConfigurationValue
  ): ConfigurationResolution =
    ConfigurationResolution(
      CONFIGURATION_KEY,
      value,
      binding.provenance.origin,
      binding.overridden.toList.map(x => _runtime_binding_trace(x, x.parameter.codec.encode(x.value).TAKE)),
      binding.provenance.sourceType,
      Some(binding.provenance.sourceIdentity)
    )

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
