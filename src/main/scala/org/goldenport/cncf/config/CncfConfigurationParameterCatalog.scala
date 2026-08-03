package org.goldenport.cncf.config

import java.math.MathContext
import java.nio.charset.Charset
import java.time.{Instant, ZoneId}
import java.util.{IllformedLocaleException, Locale}

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionIdMode, ExecutionOrderingMode, ExecutionProfileMode, ExecutionRandomMode, ExecutionSchedulerMode, ExecutionTimeMode, RuntimeClock}
import org.goldenport.cncf.http.WebDisplayFormatPolicyId
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationParameter, ConfigurationValue, ConfigurationValueCodec}

/*
 * @since   Aug.  3, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationParameterCatalog private[config] (
  val definitions: Vector[CncfConfigurationParameterDefinition],
  val schema: CncfConfigurationDocumentSchema
) {
  /**
   * Resolves only the registered canonical identity.  Decode-only aliases are
   * intentionally excluded from external binding-string admission.
   */
  def canonicalDefinition(
    parameterId: CanonicalParameterId
  ): Consequence[CncfConfigurationParameterDefinition] =
    Option(parameterId).flatMap(id => definitions.find(_.parameterId == id)).fold[Consequence[CncfConfigurationParameterDefinition]](
      Consequence.configurationInvalid("CNCF canonical configuration parameter id is not admitted")
    )(Consequence.success)
}

object CncfConfigurationParameterCatalog {
  val FIXED_USER_ID_KEY = "textus.fixed-user.id"
  val FIXED_USER_DISPLAY_NAME_KEY = "textus.fixed-user.display-name"
  val FIXED_USER_LOCALE_KEY = "textus.fixed-user.locale"
  val FIXED_USER_TIMEZONE_KEY = "textus.fixed-user.timezone"
  val WEB_EXECUTION_LOCALE_KEY = "textus.web.execution.locale"
  val WEB_EXECUTION_TIMEZONE_KEY = "textus.web.execution.timezone"
  val WEB_EXECUTION_DATE_FORMAT_KEY = "textus.web.execution.date-format"
  val WEB_EXECUTION_DATE_TIME_FORMAT_KEY = "textus.web.execution.date-time-format"
  val WEB_EXECUTION_DISPLAY_OVERRIDE_ENABLED_KEY = "textus.web.execution.display-override.enabled"
  val WEB_EXECUTION_HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY = "textus.web.execution.http-language-negotiation.enabled"
  val WEB_EXECUTION_PUBLIC_CAPABILITIES_KEY = "textus.web.execution.public-capabilities"
  val REPOSITORY_DIR_KEY = "textus.repository.dir"
  val REPOSITORY_COMPONENT_DEV_DIR_KEY = "textus.repository.component.dev.dir"
  val COMPONENT_DIR_KEY = "textus.component.dir"
  val COMPONENT_DEV_DIR_KEY = "textus.component.dev.dir"
  val COMPONENT_CAR_DIR_KEY = "textus.component.car.dir"
  val COMPONENT_FILE_KEY = "textus.component.file"
  val SUBSYSTEM_DEV_DIR_KEY = "textus.subsystem.dev.dir"
  val SUBSYSTEM_SAR_DIR_KEY = "textus.subsystem.sar.dir"
  val COLLABORATOR_REPOSITORIES_KEY = "textus.collaborator.repositories"
  val FORCE_EXIT_KEY = "textus.force-exit"
  val NO_EXIT_KEY = "textus.no-exit"
  val WEB_DESCRIPTOR_KEY = "textus.web.descriptor"
  val SERVICE_CONTAINER_DRIVER_KEY = "textus.service-container.driver"
  val SERVICE_CONTAINER_DOCKER_EXECUTABLE_KEY = "textus.service-container.docker.executable"
  val STARTUP_IMPORT_DATA_FILE_KEY = "textus.import.data.file"
  val STARTUP_IMPORT_ENTITY_FILE_KEY = "textus.import.entity.file"
  val SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY = "textus.system-node.shutdown.drain-timeout-millis"
  val OPERATION_MODE_KEY = "textus.operation-mode"
  val WEB_DEVELOP_ANONYMOUS_ADMIN_KEY = "textus.web.develop.anonymous-admin"
  val WEB_DEMO_ASSIST_ENABLED_KEY = "textus.web.demo-assist.enabled"
  val WEB_PRODUCTION_ADMIN_ENABLED_KEY = "textus.web.production.admin.enabled"
  val WEB_PRODUCTION_ADMIN_SYSTEM_ROLES_KEY = "textus.web.production.admin.system.roles"
  val WEB_PRODUCTION_ADMIN_COMPONENT_ROLES_KEY = "textus.web.production.admin.component.roles"
  val WEB_PRODUCTION_ADMIN_JOBS_ROLES_KEY = "textus.web.production.admin.jobs.roles"
  val EXECUTION_PROFILE_KEY = "textus.execution.profile"
  val EXECUTION_KEY = "textus.execution.key"
  val CLOCK_VIRTUAL_START_AT_KEY = "textus.clock.virtual-start-at"
  val EXECUTION_TIME_MODE_KEY = "textus.execution.time.mode"
  val EXECUTION_TIME_START_AT_KEY = "textus.execution.time.start-at"
  val EXECUTION_RANDOM_MODE_KEY = "textus.execution.random.mode"
  val EXECUTION_RANDOM_SEED_KEY = "textus.execution.random.seed"
  val EXECUTION_IDS_MODE_KEY = "textus.execution.ids.mode"
  val EXECUTION_SCHEDULER_MODE_KEY = "textus.execution.scheduler.mode"
  val EXECUTION_ORDERING_MODE_KEY = "textus.execution.ordering.mode"
  val EXECUTION_LOCALE_KEY = "textus.execution.locale"
  val EXECUTION_TIMEZONE_KEY = "textus.execution.timezone"
  val EXECUTION_CHARSET_KEY = "textus.execution.charset"
  val EXECUTION_LINE_SEPARATOR_KEY = "textus.execution.line-separator"
  val EXECUTION_MATH_CONTEXT_KEY = "textus.execution.math-context"
  val EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY = "textus.execution.i18n.text-normalization-policy"
  val EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY = "textus.execution.i18n.text-comparison-policy"
  val EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY = "textus.execution.i18n.date-time-format-policy"
  val EXECUTION_ENVIRONMENT_ALLOW_KEY = "textus.execution.environment.allow"
  val EXECUTION_ENVIRONMENT_VALUES_KEY = "textus.execution.environment.values"
  private val _subsystem_user_mode_codec: ConfigurationValueCodec[SubsystemUserMode] =
    new ConfigurationValueCodec[SubsystemUserMode] {
      override def decode(value: ConfigurationValue): Consequence[SubsystemUserMode] =
        value match {
          case ConfigurationValue.StringValue(text) =>
            SubsystemUserMode.parse(text).fold[Consequence[SubsystemUserMode]](
              Consequence.configurationInvalid("subsystem user-mode requires standalone or multi-user")
            )(Consequence.success)
          case _ => Consequence.configurationInvalid("subsystem user-mode requires a string")
        }

      override def encode(value: SubsystemUserMode): Consequence[ConfigurationValue] =
        Option(value).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid("subsystem user-mode is required")
        )(x => Consequence.success(ConfigurationValue.StringValue(x.name)))
    }

  private val _subsystem_user_mode_parameter =
    _take(
      ConfigurationParameter.create(
        _take(CanonicalParameterId.parse(SubsystemUserMode.CONFIGURATION_KEY)),
        _subsystem_user_mode_codec
      )
    )

  private def _profile_string_parameter(key: String): ConfigurationParameter[String] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[String] {
        def decode(value: ConfigurationValue): Consequence[String] = value match {
          case ConfigurationValue.StringValue(text) if text.trim.nonEmpty => Consequence.success(text.trim)
          case ConfigurationValue.StringValue(_) => Consequence.configurationInvalid(s"$key requires a non-empty string")
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: String): Consequence[ConfigurationValue] =
          Option(value).map(_.trim).filter(_.nonEmpty).fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$key requires a non-empty string")
          )(x => Consequence.success(ConfigurationValue.StringValue(x)))
      }
    ))

  private val _fixed_user_id_parameter = _profile_string_parameter(FIXED_USER_ID_KEY)
  private val _fixed_user_display_name_parameter = _profile_string_parameter(FIXED_USER_DISPLAY_NAME_KEY)
  private val _fixed_user_locale_parameter =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(FIXED_USER_LOCALE_KEY)),
      new ConfigurationValueCodec[Locale] {
        def decode(value: ConfigurationValue): Consequence[Locale] = value match {
          case ConfigurationValue.StringValue(text) =>
            _locale(text).fold[Consequence[Locale]](
              Consequence.configurationInvalid(s"$FIXED_USER_LOCALE_KEY requires a BCP 47 locale")
            )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$FIXED_USER_LOCALE_KEY requires a string")
        }

        def encode(value: Locale): Consequence[ConfigurationValue] =
          Option(value).filter(_ != Locale.ROOT).map(_.toLanguageTag).filter(_ != "und").fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$FIXED_USER_LOCALE_KEY requires a BCP 47 locale")
          )(x => Consequence.success(ConfigurationValue.StringValue(x)))
      }
    ))
  private val _fixed_user_timezone_parameter =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(FIXED_USER_TIMEZONE_KEY)),
      new ConfigurationValueCodec[ZoneId] {
        def decode(value: ConfigurationValue): Consequence[ZoneId] = value match {
          case ConfigurationValue.StringValue(text) =>
            scala.util.Try(ZoneId.of(text.trim)).toOption.fold[Consequence[ZoneId]](
              Consequence.configurationInvalid(s"$FIXED_USER_TIMEZONE_KEY requires an IANA timezone")
            )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$FIXED_USER_TIMEZONE_KEY requires a string")
        }

        def encode(value: ZoneId): Consequence[ConfigurationValue] =
          Option(value).fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$FIXED_USER_TIMEZONE_KEY requires an IANA timezone")
          )(x => Consequence.success(ConfigurationValue.StringValue(x.getId)))
      }
    ))

  private def _locale_parameter(key: String): ConfigurationParameter[Locale] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[Locale] {
        def decode(value: ConfigurationValue): Consequence[Locale] = value match {
          case ConfigurationValue.StringValue(text) => _locale(text).fold[Consequence[Locale]](
            Consequence.configurationInvalid(s"$key requires a BCP 47 locale")
          )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: Locale): Consequence[ConfigurationValue] =
          Option(value).filter(_ != Locale.ROOT).map(_.toLanguageTag).filter(_ != "und").fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$key requires a BCP 47 locale")
          )(x => Consequence.success(ConfigurationValue.StringValue(x)))
      }
    ))

  private def _timezone_parameter(key: String): ConfigurationParameter[ZoneId] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[ZoneId] {
        def decode(value: ConfigurationValue): Consequence[ZoneId] = value match {
          case ConfigurationValue.StringValue(text) => scala.util.Try(ZoneId.of(text.trim)).toOption.fold[Consequence[ZoneId]](
            Consequence.configurationInvalid(s"$key requires an IANA timezone")
          )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: ZoneId): Consequence[ConfigurationValue] = Option(value).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$key requires an IANA timezone")
        )(x => Consequence.success(ConfigurationValue.StringValue(x.getId)))
      }
    ))

  private def _instant_parameter(key: String): ConfigurationParameter[Instant] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[Instant] {
        def decode(value: ConfigurationValue): Consequence[Instant] = value match {
          case ConfigurationValue.StringValue(text) =>
            scala.util.Try(RuntimeClock.parseInstant(text.trim)).toOption.fold[Consequence[Instant]](
              Consequence.configurationInvalid(s"$key requires an ISO-8601 date-time with an offset")
            )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: Instant): Consequence[ConfigurationValue] =
          Option(value).fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$key requires an ISO-8601 date-time with an offset")
          )(x => Consequence.success(ConfigurationValue.StringValue(x.toString)))
      }
    ))

  private def _enum_parameter[A](
    key: String,
    parse: String => Option[A],
    render: A => String,
    expected: String
  ): ConfigurationParameter[A] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[A] {
        def decode(value: ConfigurationValue): Consequence[A] = value match {
          case ConfigurationValue.StringValue(text) => parse(text).fold[Consequence[A]](
            Consequence.configurationInvalid(s"$key requires $expected")
          )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: A): Consequence[ConfigurationValue] = Option(value).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$key requires $expected")
        )(x => Consequence.success(ConfigurationValue.StringValue(render(x))))
      }
    ))

  private def _charset_parameter(key: String): ConfigurationParameter[Charset] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[Charset] {
        def decode(value: ConfigurationValue): Consequence[Charset] = value match {
          case ConfigurationValue.StringValue(text) => scala.util.Try(Charset.forName(text.trim)).toOption.fold[Consequence[Charset]](
            Consequence.configurationInvalid(s"$key requires a supported charset")
          )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: Charset): Consequence[ConfigurationValue] = Option(value).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$key requires a supported charset")
        )(x => Consequence.success(ConfigurationValue.StringValue(x.name)))
      }
    ))

  private def _line_separator_parameter(key: String): ConfigurationParameter[String] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[String] {
        def decode(value: ConfigurationValue): Consequence[String] = value match {
          case ConfigurationValue.StringValue(text) => text.trim.toLowerCase(Locale.ROOT) match {
            case "lf" => Consequence.success("\n")
            case "crlf" => Consequence.success("\r\n")
            case "cr" => Consequence.success("\r")
            case _ => Consequence.configurationInvalid(s"$key requires lf, crlf, or cr")
          }
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: String): Consequence[ConfigurationValue] = value match {
          case "\n" => Consequence.success(ConfigurationValue.StringValue("lf"))
          case "\r\n" => Consequence.success(ConfigurationValue.StringValue("crlf"))
          case "\r" => Consequence.success(ConfigurationValue.StringValue("cr"))
          case _ => Consequence.configurationInvalid(s"$key requires lf, crlf, or cr")
        }
      }
    ))

  private def _math_context_parameter(key: String): ConfigurationParameter[MathContext] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[MathContext] {
        def decode(value: ConfigurationValue): Consequence[MathContext] = value match {
          case ConfigurationValue.StringValue(text) => _math_context(text).fold[Consequence[MathContext]](
            Consequence.configurationInvalid(s"$key requires decimal32, decimal64, decimal128, or unlimited")
          )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: MathContext): Consequence[ConfigurationValue] = _math_context_name(value).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$key requires decimal32, decimal64, decimal128, or unlimited")
        )(x => Consequence.success(ConfigurationValue.StringValue(x)))
      }
    ))

  private def _environment_values_parameter(key: String): ConfigurationParameter[Map[String, String]] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[Map[String, String]] {
        def decode(value: ConfigurationValue): Consequence[Map[String, String]] = value match {
          case ConfigurationValue.ObjectValue(values) if values.nonEmpty =>
            val entries = values.toVector.map { case (name, raw) =>
              _configuration_scalar(raw).filter(_ => Option(name).exists(_.trim.nonEmpty)).map(name.trim -> _)
            }
            if (entries.forall(_.nonEmpty)) Consequence.success(entries.flatten.toMap)
            else Consequence.configurationInvalid(s"$key requires a non-empty object of scalar values")
          case ConfigurationValue.ObjectValue(_) => Consequence.success(Map.empty)
          case _ => Consequence.configurationInvalid(s"$key requires an object of scalar values")
        }
        def encode(value: Map[String, String]): Consequence[ConfigurationValue] =
          Option(value).filter(_.forall { case (name, text) => Option(name).exists(_.trim.nonEmpty) && text != null }).fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$key requires an object of scalar values")
          )(x => Consequence.success(ConfigurationValue.ObjectValue(x.map { case (name, text) => name.trim -> ConfigurationValue.StringValue(text) })))
      }
    ))

  private def _format_parameter(key: String): ConfigurationParameter[WebDisplayFormatPolicyId] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[WebDisplayFormatPolicyId] {
        def decode(value: ConfigurationValue): Consequence[WebDisplayFormatPolicyId] = value match {
          case ConfigurationValue.StringValue(text) => WebDisplayFormatPolicyId.parse(text).fold[Consequence[WebDisplayFormatPolicyId]](
            Consequence.configurationInvalid(s"$key requires a stable display format policy identifier")
          )(Consequence.success)
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: WebDisplayFormatPolicyId): Consequence[ConfigurationValue] = Option(value).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$key requires a stable display format policy identifier")
        )(x => Consequence.success(ConfigurationValue.StringValue(x.name)))
      }
    ))

  private def _boolean_parameter(key: String): ConfigurationParameter[Boolean] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[Boolean] {
        def decode(value: ConfigurationValue): Consequence[Boolean] = value match {
          case ConfigurationValue.BooleanValue(value) => Consequence.success(value)
          case ConfigurationValue.StringValue(text) => text.trim.toLowerCase(Locale.ROOT) match {
            case "true" | "1" | "yes" | "on" => Consequence.success(true)
            case "false" | "0" | "no" | "off" => Consequence.success(false)
            case _ => Consequence.configurationInvalid(s"$key requires a boolean")
          }
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: Boolean): Consequence[ConfigurationValue] =
          Option(value).fold[Consequence[ConfigurationValue]](Consequence.configurationInvalid(s"$key requires a boolean"))(
            x => Consequence.success(ConfigurationValue.StringValue(x.toString))
          )
      }
    ))

  private val _web_execution_locale_parameter = _locale_parameter(WEB_EXECUTION_LOCALE_KEY)
  private val _web_execution_timezone_parameter = _timezone_parameter(WEB_EXECUTION_TIMEZONE_KEY)
  private val _web_execution_date_format_parameter = _format_parameter(WEB_EXECUTION_DATE_FORMAT_KEY)
  private val _web_execution_date_time_format_parameter = _format_parameter(WEB_EXECUTION_DATE_TIME_FORMAT_KEY)
  private val _web_execution_display_override_enabled_parameter = _boolean_parameter(WEB_EXECUTION_DISPLAY_OVERRIDE_ENABLED_KEY)
  private val _web_execution_http_language_negotiation_enabled_parameter = _boolean_parameter(WEB_EXECUTION_HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY)
  private val _web_execution_public_capabilities_parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse(WEB_EXECUTION_PUBLIC_CAPABILITIES_KEY)),
    new ConfigurationValueCodec[Vector[String]] {
      def decode(value: ConfigurationValue): Consequence[Vector[String]] = value match {
        case ConfigurationValue.StringValue(text) => Consequence.success(_tokens(text))
        case _ => Consequence.configurationInvalid(s"$WEB_EXECUTION_PUBLIC_CAPABILITIES_KEY requires a string")
      }
      def encode(value: Vector[String]): Consequence[ConfigurationValue] =
        Option(value).filter(_.forall(_ != null)).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$WEB_EXECUTION_PUBLIC_CAPABILITIES_KEY requires capability tokens")
        )(x => Consequence.success(ConfigurationValue.StringValue(_tokens(x.mkString(",")).mkString(","))))
    }
  ))

  private def _tokens_parameter(key: String): ConfigurationParameter[Vector[String]] =
    _take(ConfigurationParameter.create(
      _take(CanonicalParameterId.parse(key)),
      new ConfigurationValueCodec[Vector[String]] {
        def decode(value: ConfigurationValue): Consequence[Vector[String]] = value match {
          case ConfigurationValue.StringValue(text) => Consequence.success(_tokens(text))
          case _ => Consequence.configurationInvalid(s"$key requires a string")
        }
        def encode(value: Vector[String]): Consequence[ConfigurationValue] =
          Option(value).filter(_.forall(_ != null)).fold[Consequence[ConfigurationValue]](
            Consequence.configurationInvalid(s"$key requires path tokens")
          )(x => Consequence.success(ConfigurationValue.StringValue(_tokens(x.mkString(",")).mkString(","))))
      }
    ))

  private val _repository_dir_parameter = _tokens_parameter(REPOSITORY_DIR_KEY)
  private val _repository_component_dev_dir_parameter = _tokens_parameter(REPOSITORY_COMPONENT_DEV_DIR_KEY)
  private val _component_dir_parameter = _tokens_parameter(COMPONENT_DIR_KEY)
  private val _component_dev_dir_parameter = _tokens_parameter(COMPONENT_DEV_DIR_KEY)
  private val _component_car_dir_parameter = _tokens_parameter(COMPONENT_CAR_DIR_KEY)
  private val _component_file_parameter = _tokens_parameter(COMPONENT_FILE_KEY)
  private val _subsystem_dev_dir_parameter = _tokens_parameter(SUBSYSTEM_DEV_DIR_KEY)
  private val _subsystem_sar_dir_parameter = _tokens_parameter(SUBSYSTEM_SAR_DIR_KEY)
  private val _collaborator_repositories_parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse(COLLABORATOR_REPOSITORIES_KEY)),
    new ConfigurationValueCodec[Vector[String]] {
      def decode(value: ConfigurationValue): Consequence[Vector[String]] = value match {
        case ConfigurationValue.StringValue(text) => Consequence.success(_comma_separated_paths(text))
        case _ => Consequence.configurationInvalid(s"$COLLABORATOR_REPOSITORIES_KEY requires a string")
      }
      def encode(value: Vector[String]): Consequence[ConfigurationValue] =
        Option(value).filter(_.forall(_ != null)).fold[Consequence[ConfigurationValue]](
          Consequence.configurationInvalid(s"$COLLABORATOR_REPOSITORIES_KEY requires path values")
        )(x => Consequence.success(ConfigurationValue.StringValue(_comma_separated_paths(x.mkString(",")).mkString(","))))
    }
  ))
  private val _force_exit_parameter = _boolean_parameter(FORCE_EXIT_KEY)
  private val _no_exit_parameter = _boolean_parameter(NO_EXIT_KEY)
  private val _web_descriptor_parameter = _profile_string_parameter(WEB_DESCRIPTOR_KEY)
  private val _service_container_driver_parameter = _profile_string_parameter(SERVICE_CONTAINER_DRIVER_KEY)
  private val _service_container_docker_executable_parameter = _profile_string_parameter(SERVICE_CONTAINER_DOCKER_EXECUTABLE_KEY)
  private val _startup_import_data_file_parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse(STARTUP_IMPORT_DATA_FILE_KEY)),
    ConfigurationValueCodec.string
  ))
  private val _startup_import_entity_file_parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse(STARTUP_IMPORT_ENTITY_FILE_KEY)),
    ConfigurationValueCodec.string
  ))
  private val _system_node_shutdown_drain_timeout_millis_parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse(SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY)),
    new ConfigurationValueCodec[Long] {
      def decode(value: ConfigurationValue): Consequence[Long] =
        value match {
          case ConfigurationValue.NumberValue(number) if number.isValidLong && number.isWhole =>
            _drain_timeout_millis(number.toLong)
          case ConfigurationValue.StringValue(text) if text.matches("[0-9]+") =>
            scala.util.Try(text.toLong).toOption match {
              case Some(number) => _drain_timeout_millis(number)
              case None => Consequence.configurationInvalid(
                s"$SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY requires an integer in 1..300000; rejected value: $text"
              )
            }
          case _ =>
            Consequence.configurationInvalid(s"$SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY requires an integer in 1..300000; rejected value: $value")
        }

      def encode(value: Long): Consequence[ConfigurationValue] =
        _drain_timeout_millis(value).map(x => ConfigurationValue.NumberValue(BigDecimal(x)))
    }
  ))
  private val _operation_mode_parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse(OPERATION_MODE_KEY)),
    new ConfigurationValueCodec[OperationMode] {
      def decode(value: ConfigurationValue): Consequence[OperationMode] = value match {
        case ConfigurationValue.StringValue(text) =>
          OperationMode.from(text).fold[Consequence[OperationMode]](
            Consequence.configurationInvalid(s"$OPERATION_MODE_KEY requires production, demo, develop, or test")
          )(Consequence.success)
        case _ => Consequence.configurationInvalid(s"$OPERATION_MODE_KEY requires a string")
      }
      def encode(value: OperationMode): Consequence[ConfigurationValue] = Option(value).fold[Consequence[ConfigurationValue]](
        Consequence.configurationInvalid(s"$OPERATION_MODE_KEY requires production, demo, develop, or test")
      )(x => Consequence.success(ConfigurationValue.StringValue(x.name)))
    }
  ))
  private val _web_develop_anonymous_admin_parameter = _boolean_parameter(WEB_DEVELOP_ANONYMOUS_ADMIN_KEY)
  private val _web_demo_assist_enabled_parameter = _boolean_parameter(WEB_DEMO_ASSIST_ENABLED_KEY)
  private val _web_production_admin_enabled_parameter = _boolean_parameter(WEB_PRODUCTION_ADMIN_ENABLED_KEY)
  private val _web_production_admin_system_roles_parameter = _tokens_parameter(WEB_PRODUCTION_ADMIN_SYSTEM_ROLES_KEY)
  private val _web_production_admin_component_roles_parameter = _tokens_parameter(WEB_PRODUCTION_ADMIN_COMPONENT_ROLES_KEY)
  private val _web_production_admin_jobs_roles_parameter = _tokens_parameter(WEB_PRODUCTION_ADMIN_JOBS_ROLES_KEY)
  private val _execution_profile_parameter = _enum_parameter(EXECUTION_PROFILE_KEY, ExecutionProfileMode.parse, _.name, "standard, seeded, or controlled")
  private val _execution_key_parameter = _profile_string_parameter(EXECUTION_KEY)
  private val _clock_virtual_start_at_parameter = _instant_parameter(CLOCK_VIRTUAL_START_AT_KEY)
  private val _execution_time_mode_parameter = _enum_parameter(EXECUTION_TIME_MODE_KEY, ExecutionTimeMode.parse, _.name, "system, offset, or manual")
  private val _execution_time_start_at_parameter = _instant_parameter(EXECUTION_TIME_START_AT_KEY)
  private val _execution_random_mode_parameter = _enum_parameter(EXECUTION_RANDOM_MODE_KEY, ExecutionRandomMode.parse, _.name, "system or seeded")
  private val _execution_random_seed_parameter = _profile_string_parameter(EXECUTION_RANDOM_SEED_KEY)
  private val _execution_ids_mode_parameter = _enum_parameter(EXECUTION_IDS_MODE_KEY, ExecutionIdMode.parse, _.name, "production or deterministic")
  private val _execution_scheduler_mode_parameter = _enum_parameter(EXECUTION_SCHEDULER_MODE_KEY, ExecutionSchedulerMode.parse, _.name, "realtime or manual")
  private val _execution_ordering_mode_parameter = _enum_parameter(EXECUTION_ORDERING_MODE_KEY, ExecutionOrderingMode.parse, _.name, "concurrent or deterministic")
  private val _execution_locale_parameter = _locale_parameter(EXECUTION_LOCALE_KEY)
  private val _execution_timezone_parameter = _timezone_parameter(EXECUTION_TIMEZONE_KEY)
  private val _execution_charset_parameter = _charset_parameter(EXECUTION_CHARSET_KEY)
  private val _execution_line_separator_parameter = _line_separator_parameter(EXECUTION_LINE_SEPARATOR_KEY)
  private val _execution_math_context_parameter = _math_context_parameter(EXECUTION_MATH_CONTEXT_KEY)
  private val _execution_i18n_text_normalization_policy_parameter = _profile_string_parameter(EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY)
  private val _execution_i18n_text_comparison_policy_parameter = _profile_string_parameter(EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY)
  private val _execution_i18n_date_time_format_policy_parameter = _profile_string_parameter(EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY)
  private val _execution_environment_allow_parameter = _tokens_parameter(EXECUTION_ENVIRONMENT_ALLOW_KEY)
  private val _execution_environment_values_parameter = _environment_values_parameter(EXECUTION_ENVIRONMENT_VALUES_KEY)

  private val _definitions =
    Vector(
      _take(
        CncfConfigurationParameterDefinition.registered(
          SubsystemUserMode.CONFIGURATION_KEY,
          Vector(
            "textus.runtime.subsystem.user-mode",
            "cncf.subsystem.user-mode",
            "cncf.runtime.subsystem.user-mode"
          ),
          _subsystem_user_mode_parameter,
          Vector("phase-53: subsystem user-mode admission"),
          isConfidential = false,
          Set(CncfConfigurationTargetKind.SubsystemInstance)
        )
      ),
      _web_definition(_web_execution_locale_parameter),
      _web_definition(_web_execution_timezone_parameter),
      _web_definition(_web_execution_date_format_parameter),
      _web_definition(_web_execution_date_time_format_parameter),
      _web_definition(_web_execution_display_override_enabled_parameter),
      _web_definition(_web_execution_http_language_negotiation_enabled_parameter),
      _web_definition(_web_execution_public_capabilities_parameter),
      _repository_definition(_repository_dir_parameter),
      _repository_definition(_repository_component_dev_dir_parameter),
      _repository_definition(_component_dir_parameter),
      _repository_definition(_component_dev_dir_parameter),
      _repository_definition(_component_car_dir_parameter),
      _repository_definition(_component_file_parameter),
      _repository_definition(_subsystem_dev_dir_parameter),
      _repository_definition(_subsystem_sar_dir_parameter),
      _repository_definition(_collaborator_repositories_parameter),
      _process_exit_definition(_force_exit_parameter),
      _process_exit_definition(_no_exit_parameter),
      _web_definition(_web_descriptor_parameter),
      _service_container_definition(_service_container_driver_parameter),
      _service_container_definition(_service_container_docker_executable_parameter),
      _startup_import_definition(_startup_import_data_file_parameter),
      _startup_import_definition(_startup_import_entity_file_parameter),
      _operation_security_definition(_operation_mode_parameter),
      _operation_security_definition(_web_develop_anonymous_admin_parameter),
      _operation_security_definition(_web_demo_assist_enabled_parameter),
      _operation_security_definition(_web_production_admin_enabled_parameter),
      _operation_security_definition(_web_production_admin_system_roles_parameter),
      _operation_security_definition(_web_production_admin_component_roles_parameter),
      _operation_security_definition(_web_production_admin_jobs_roles_parameter),
      _execution_profile_definition(_execution_profile_parameter),
      _execution_profile_definition(_execution_key_parameter),
      _execution_profile_definition(_clock_virtual_start_at_parameter),
      _execution_profile_definition(_execution_time_mode_parameter),
      _execution_profile_definition(_execution_time_start_at_parameter),
      _execution_profile_definition(_execution_random_mode_parameter, confidential = false),
      _execution_profile_definition(_execution_random_seed_parameter, confidential = true),
      _execution_profile_definition(_execution_ids_mode_parameter),
      _execution_profile_definition(_execution_scheduler_mode_parameter),
      _execution_profile_definition(_execution_ordering_mode_parameter),
      _execution_profile_definition(_execution_locale_parameter),
      _execution_profile_definition(_execution_timezone_parameter),
      _execution_profile_definition(_execution_charset_parameter),
      _execution_profile_definition(_execution_line_separator_parameter),
      _execution_profile_definition(_execution_math_context_parameter),
      _execution_profile_definition(_execution_i18n_text_normalization_policy_parameter),
      _execution_profile_definition(_execution_i18n_text_comparison_policy_parameter),
      _execution_profile_definition(_execution_i18n_date_time_format_policy_parameter),
      _execution_profile_definition(_execution_environment_allow_parameter),
      _execution_profile_definition(_execution_environment_values_parameter, confidential = true),
      _take(CncfConfigurationParameterDefinition.registered(
        SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY,
        Vector.empty,
        _system_node_shutdown_drain_timeout_millis_parameter,
        Vector("phase-55: SystemNode shutdown drain timeout"),
        isConfidential = false,
        Set(CncfConfigurationTargetKind.SubsystemInstance)
      ))
    )

  val subsystemUserMode: ConfigurationParameter[SubsystemUserMode] =
    _subsystem_user_mode_parameter
  val fixedUserId: ConfigurationParameter[String] = _fixed_user_id_parameter
  val fixedUserDisplayName: ConfigurationParameter[String] = _fixed_user_display_name_parameter
  val fixedUserLocale: ConfigurationParameter[Locale] = _fixed_user_locale_parameter
  val fixedUserTimezone: ConfigurationParameter[ZoneId] = _fixed_user_timezone_parameter
  val webExecutionLocale: ConfigurationParameter[Locale] = _web_execution_locale_parameter
  val webExecutionTimezone: ConfigurationParameter[ZoneId] = _web_execution_timezone_parameter
  val webExecutionDateFormat: ConfigurationParameter[WebDisplayFormatPolicyId] = _web_execution_date_format_parameter
  val webExecutionDateTimeFormat: ConfigurationParameter[WebDisplayFormatPolicyId] = _web_execution_date_time_format_parameter
  val webExecutionDisplayOverrideEnabled: ConfigurationParameter[Boolean] = _web_execution_display_override_enabled_parameter
  val webExecutionHttpLanguageNegotiationEnabled: ConfigurationParameter[Boolean] = _web_execution_http_language_negotiation_enabled_parameter
  val webExecutionPublicCapabilities: ConfigurationParameter[Vector[String]] = _web_execution_public_capabilities_parameter
  val repositoryDir: ConfigurationParameter[Vector[String]] = _repository_dir_parameter
  val repositoryComponentDevDir: ConfigurationParameter[Vector[String]] = _repository_component_dev_dir_parameter
  val componentDir: ConfigurationParameter[Vector[String]] = _component_dir_parameter
  val componentDevDir: ConfigurationParameter[Vector[String]] = _component_dev_dir_parameter
  val componentCarDir: ConfigurationParameter[Vector[String]] = _component_car_dir_parameter
  val componentFile: ConfigurationParameter[Vector[String]] = _component_file_parameter
  val subsystemDevDir: ConfigurationParameter[Vector[String]] = _subsystem_dev_dir_parameter
  val subsystemSarDir: ConfigurationParameter[Vector[String]] = _subsystem_sar_dir_parameter
  val collaboratorRepositories: ConfigurationParameter[Vector[String]] = _collaborator_repositories_parameter
  val forceExit: ConfigurationParameter[Boolean] = _force_exit_parameter
  val noExit: ConfigurationParameter[Boolean] = _no_exit_parameter
  val webDescriptor: ConfigurationParameter[String] = _web_descriptor_parameter
  val serviceContainerDriver: ConfigurationParameter[String] = _service_container_driver_parameter
  val serviceContainerDockerExecutable: ConfigurationParameter[String] = _service_container_docker_executable_parameter
  val startupImportDataFile: ConfigurationParameter[String] = _startup_import_data_file_parameter
  val startupImportEntityFile: ConfigurationParameter[String] = _startup_import_entity_file_parameter
  val systemNodeShutdownDrainTimeoutMillis: ConfigurationParameter[Long] = _system_node_shutdown_drain_timeout_millis_parameter
  val operationMode: ConfigurationParameter[OperationMode] = _operation_mode_parameter
  val webDevelopAnonymousAdmin: ConfigurationParameter[Boolean] = _web_develop_anonymous_admin_parameter
  val webDemoAssistEnabled: ConfigurationParameter[Boolean] = _web_demo_assist_enabled_parameter
  val webProductionAdminEnabled: ConfigurationParameter[Boolean] = _web_production_admin_enabled_parameter
  val webProductionAdminSystemRoles: ConfigurationParameter[Vector[String]] = _web_production_admin_system_roles_parameter
  val webProductionAdminComponentRoles: ConfigurationParameter[Vector[String]] = _web_production_admin_component_roles_parameter
  val webProductionAdminJobsRoles: ConfigurationParameter[Vector[String]] = _web_production_admin_jobs_roles_parameter
  val executionProfile: ConfigurationParameter[ExecutionProfileMode] = _execution_profile_parameter
  val executionKey: ConfigurationParameter[String] = _execution_key_parameter
  val clockVirtualStartAt: ConfigurationParameter[Instant] = _clock_virtual_start_at_parameter
  val executionTimeMode: ConfigurationParameter[ExecutionTimeMode] = _execution_time_mode_parameter
  val executionTimeStartAt: ConfigurationParameter[Instant] = _execution_time_start_at_parameter
  val executionRandomMode: ConfigurationParameter[ExecutionRandomMode] = _execution_random_mode_parameter
  val executionRandomSeed: ConfigurationParameter[String] = _execution_random_seed_parameter
  val executionIdsMode: ConfigurationParameter[ExecutionIdMode] = _execution_ids_mode_parameter
  val executionSchedulerMode: ConfigurationParameter[ExecutionSchedulerMode] = _execution_scheduler_mode_parameter
  val executionOrderingMode: ConfigurationParameter[ExecutionOrderingMode] = _execution_ordering_mode_parameter
  val executionLocale: ConfigurationParameter[Locale] = _execution_locale_parameter
  val executionTimezone: ConfigurationParameter[ZoneId] = _execution_timezone_parameter
  val executionCharset: ConfigurationParameter[Charset] = _execution_charset_parameter
  val executionLineSeparator: ConfigurationParameter[String] = _execution_line_separator_parameter
  val executionMathContext: ConfigurationParameter[MathContext] = _execution_math_context_parameter
  val executionI18nTextNormalizationPolicy: ConfigurationParameter[String] = _execution_i18n_text_normalization_policy_parameter
  val executionI18nTextComparisonPolicy: ConfigurationParameter[String] = _execution_i18n_text_comparison_policy_parameter
  val executionI18nDateTimeFormatPolicy: ConfigurationParameter[String] = _execution_i18n_date_time_format_policy_parameter
  val executionEnvironmentAllow: ConfigurationParameter[Vector[String]] = _execution_environment_allow_parameter
  val executionEnvironmentValues: ConfigurationParameter[Map[String, String]] = _execution_environment_values_parameter

  val closed: CncfConfigurationParameterCatalog =
    _take(create(_definitions))

  val standaloneUserProfile: CncfConfigurationParameterCatalog =
    _take(create(Vector(
      _profile_definition(fixedUserId),
      _profile_definition(fixedUserDisplayName),
      _profile_definition(fixedUserLocale),
      _profile_definition(fixedUserTimezone)
    )))

  def create(
    definitions: Vector[CncfConfigurationParameterDefinition]
  ): Consequence[CncfConfigurationParameterCatalog] =
    if (definitions == null || definitions.isEmpty || definitions.exists(_ == null))
      Consequence.configurationInvalid("CNCF configuration parameter catalog is invalid")
    else if (definitions.map(_.canonicalSpelling).distinct.size != definitions.size ||
      definitions.map(_.parameterId).distinct.size != definitions.size)
      Consequence.configurationInvalid("CNCF configuration parameter catalog contains duplicate canonical identities")
    else
      CncfConfigurationDocumentSchema.create(definitions).map { schema =>
        new CncfConfigurationParameterCatalog(definitions, schema)
      }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(throw new IllegalStateException(result.display))

  private def _profile_definition[A](
    parameter: ConfigurationParameter[A]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      Vector.empty,
      parameter,
      Vector("phase-53: StandaloneUserProfile HOME admission"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.Global, CncfConfigurationTargetKind.SubsystemInstance)
    ))

  private def _web_definition[A](
    parameter: ConfigurationParameter[A]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-53: WebExecutionResolutionPolicy"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.SubsystemInstance)
    ))

  private def _repository_definition(
    parameter: ConfigurationParameter[Vector[String]]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-55: repository bootstrap policy"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.Global)
    ))

  private def _service_container_definition(
    parameter: ConfigurationParameter[String]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-55: service-container runtime policy"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.SubsystemInstance)
    ))

  private def _process_exit_definition(
    parameter: ConfigurationParameter[Boolean]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-55: runtime process-exit policy"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.Global)
    ))

  private def _startup_import_definition(
    parameter: ConfigurationParameter[String]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-55: startup import configuration"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.SubsystemInstance)
    ))

  private def _operation_security_definition[A](
    parameter: ConfigurationParameter[A]
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-55: runtime operation and Web authorization policy"),
      isConfidential = false,
      Set(CncfConfigurationTargetKind.SubsystemInstance)
    ))

  private def _execution_profile_definition[A](
    parameter: ConfigurationParameter[A],
    confidential: Boolean = false
  ): CncfConfigurationParameterDefinition =
    _take(CncfConfigurationParameterDefinition.registered(
      parameter.id.value,
      _runtime_aliases(parameter.id.value),
      parameter,
      Vector("phase-55: runtime execution-profile configuration projection"),
      isConfidential = confidential,
      Set(CncfConfigurationTargetKind.SubsystemInstance)
    ))

  private def _runtime_aliases(key: String): Vector[String] = {
    val suffix = key.stripPrefix("textus.")
    Vector(s"textus.runtime.$suffix", s"cncf.$suffix", s"cncf.runtime.$suffix")
  }

  private def _tokens(value: String): Vector[String] =
    Option(value).toVector.flatMap(_.split("[,|\\s]+").toVector).map(_.trim).filter(_.nonEmpty)

  private def _comma_separated_paths(value: String): Vector[String] =
    Option(value).toVector.flatMap(_.split(",", -1).toVector).map(_.trim).filter(_.nonEmpty)

  private def _drain_timeout_millis(value: Long): Consequence[Long] =
    if (value >= 1L && value <= 300000L)
      Consequence.success(value)
    else
      Consequence.configurationInvalid(s"$SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY requires an integer in 1..300000; rejected value: $value")

  private def _locale(text: String): Option[Locale] =
    Option(text).map(_.trim).filter(_.nonEmpty).flatMap { tag =>
      try {
        val value = new Locale.Builder().setLanguageTag(tag).build
        Option(value).filter(x => x != Locale.ROOT && x.toLanguageTag != "und")
      } catch {
        case _: IllformedLocaleException => None
      }
    }

  private def _math_context(text: String): Option[MathContext] =
    Option(text).map(_.trim.toLowerCase(Locale.ROOT)).flatMap {
      case "decimal32" => Some(MathContext.DECIMAL32)
      case "decimal64" => Some(MathContext.DECIMAL64)
      case "decimal128" => Some(MathContext.DECIMAL128)
      case "unlimited" => Some(MathContext.UNLIMITED)
      case _ => None
    }

  private def _math_context_name(value: MathContext): Option[String] =
    if (value == MathContext.DECIMAL32) Some("decimal32")
    else if (value == MathContext.DECIMAL64) Some("decimal64")
    else if (value == MathContext.DECIMAL128) Some("decimal128")
    else if (value == MathContext.UNLIMITED) Some("unlimited")
    else None

  private def _configuration_scalar(value: ConfigurationValue): Option[String] =
    value match {
      case ConfigurationValue.StringValue(text) => Some(text)
      case ConfigurationValue.NumberValue(number) => Some(number.toString)
      case ConfigurationValue.BooleanValue(boolean) => Some(boolean.toString)
      case _ => None
    }
}
