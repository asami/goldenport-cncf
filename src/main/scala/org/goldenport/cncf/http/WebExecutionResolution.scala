package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale
import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.goldenport.Consequence
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebExecutionResolutionPolicy(
  applicationMode: WebApplicationMode = WebApplicationMode.Standalone,
  applicationLocale: Option[Locale] = None,
  applicationTimezone: Option[ZoneId] = None,
  dateFormat: Option[WebDisplayFormatPolicyId] = None,
  dateTimeFormat: Option[WebDisplayFormatPolicyId] = None,
  displayOverrideEnabled: Boolean = false,
  httpLanguageNegotiationEnabled: Boolean = false,
  publicCapabilities: Vector[String] = Vector.empty
)

object WebExecutionResolutionPolicy {
  val APPLICATION_MODE_KEY = "textus.web.execution.application-mode"
  val LOCALE_KEY = "textus.web.execution.locale"
  val TIMEZONE_KEY = "textus.web.execution.timezone"
  val DATE_FORMAT_KEY = "textus.web.execution.date-format"
  val DATE_TIME_FORMAT_KEY = "textus.web.execution.date-time-format"
  val DISPLAY_OVERRIDE_ENABLED_KEY = "textus.web.execution.display-override.enabled"
  val HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY = "textus.web.execution.http-language-negotiation.enabled"
  val PUBLIC_CAPABILITIES_KEY = "textus.web.execution.public-capabilities"

  def fromConfiguration(configuration: ResolvedConfiguration): Consequence[WebExecutionResolutionPolicy] =
    for {
      applicationmode <- _application_mode(_value(configuration, APPLICATION_MODE_KEY))
      applicationlocale <- _locale(LOCALE_KEY, _value(configuration, LOCALE_KEY))
      applicationtimezone <- _timezone(TIMEZONE_KEY, _value(configuration, TIMEZONE_KEY))
      dateformat <- _format_policy(DATE_FORMAT_KEY, _value(configuration, DATE_FORMAT_KEY))
      datetimeformat <- _format_policy(DATE_TIME_FORMAT_KEY, _value(configuration, DATE_TIME_FORMAT_KEY))
      displayoverride <- _boolean(DISPLAY_OVERRIDE_ENABLED_KEY, _value(configuration, DISPLAY_OVERRIDE_ENABLED_KEY))
      httplanguage <- _boolean(HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY, _value(configuration, HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY))
    } yield WebExecutionResolutionPolicy(
      applicationMode = applicationmode,
      applicationLocale = applicationlocale,
      applicationTimezone = applicationtimezone,
      dateFormat = dateformat,
      dateTimeFormat = datetimeformat,
      displayOverrideEnabled = displayoverride.getOrElse(false),
      httpLanguageNegotiationEnabled = httplanguage.getOrElse(false),
      publicCapabilities = _tokens(_value(configuration, PUBLIC_CAPABILITIES_KEY))
    )

  private def _value(configuration: ResolvedConfiguration, key: String): Option[String] =
    _aliases(key).iterator
      .flatMap(ConfigurationAccess.getString(configuration, _))
      .find(_.trim.nonEmpty)

  private def _aliases(key: String): Vector[String] = {
    val suffix = key.stripPrefix("textus.")
    Vector(
      key,
      s"textus.runtime.$suffix",
      s"cncf.$suffix",
      s"cncf.runtime.$suffix"
    )
  }

  private def _application_mode(value: Option[String]): Consequence[WebApplicationMode] =
    value match {
      case None => Consequence.success(WebApplicationMode.Standalone)
      case Some(x) => WebApplicationMode.parse(x)
        .map(Consequence.success)
        .getOrElse(Consequence.argumentFormatError(APPLICATION_MODE_KEY, "standalone or multi-user", x))
    }

  private def _locale(name: String, value: Option[String]): Consequence[Option[Locale]] =
    value match {
      case None => Consequence.success(None)
      case Some(x) => WebExecutionResolver.parseLocale(x)
        .map(locale => Consequence.success(Some(locale)))
        .getOrElse(Consequence.argumentFormatError(name, "BCP 47 locale", x))
    }

  private def _timezone(name: String, value: Option[String]): Consequence[Option[ZoneId]] =
    value match {
      case None => Consequence.success(None)
      case Some(x) => Try(ZoneId.of(x.trim)).toOption
        .map(timezone => Consequence.success(Some(timezone)))
        .getOrElse(Consequence.argumentFormatError(name, "IANA timezone", x))
    }

  private def _format_policy(
    name: String,
    value: Option[String]
  ): Consequence[Option[WebDisplayFormatPolicyId]] =
    value match {
      case None => Consequence.success(None)
      case Some(x) => WebDisplayFormatPolicyId.parse(x)
        .map(format => Consequence.success(Some(format)))
        .getOrElse(Consequence.argumentFormatError(name, "stable display format policy identifier", x))
    }

  private def _boolean(name: String, value: Option[String]): Consequence[Option[Boolean]] =
    value match {
      case None => Consequence.success(None)
      case Some(x) => x.trim.toLowerCase(Locale.ROOT) match {
        case "true" | "1" | "yes" | "on" => Consequence.success(Some(true))
        case "false" | "0" | "no" | "off" => Consequence.success(Some(false))
        case _ => Consequence.argumentFormatError(name, "boolean", x)
      }
    }

  private def _tokens(value: Option[String]): Vector[String] =
    value.toVector
      .flatMap(_.split("[,|\\s]+").toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
}

final case class WebExecutionResolutionInput(
  runtimeLocale: Option[Locale] = Some(Locale.ROOT),
  runtimeTimezone: Option[ZoneId] = Some(ZoneId.of("UTC")),
  runtimeDateTimeFormatPolicy: Option[String] = Some("default"),
  authenticated: Boolean = false,
  userLocale: Option[String] = None,
  userTimezone: Option[String] = None,
  displayLocale: Option[String] = None,
  displayTimezone: Option[String] = None,
  acceptLanguage: Option[String] = None
)

final case class ResolvedWebExecutionFormatting(
  locale: Locale,
  timezone: ZoneId,
  projectionPolicy: WebExecutionProjectionPolicy
)

object WebExecutionResolver {
  def resolve(
    policy: WebExecutionResolutionPolicy,
    input: WebExecutionResolutionInput
  ): Consequence[ResolvedWebExecutionFormatting] =
    for {
      locale <- _resolve_locale(policy, input)
      timezone <- _resolve_timezone(policy, input)
      datetimeformat <- _resolve_date_time_format(policy, input.runtimeDateTimeFormatPolicy)
    } yield ResolvedWebExecutionFormatting(
      locale,
      timezone,
      WebExecutionProjectionPolicy(
        applicationMode = policy.applicationMode,
        dateFormat = policy.dateFormat.getOrElse(WebDisplayFormatPolicyId.LOCALIZED_MEDIUM),
        dateTimeFormat = datetimeformat,
        publicCapabilities = policy.publicCapabilities
      )
    )

  def parseLocale(value: String): Option[Locale] = {
    val normalized = Option(value).map(_.trim.replace('_', '-')).getOrElse("")
    if (normalized.isEmpty)
      None
    else if (normalized.equalsIgnoreCase("und"))
      Some(Locale.ROOT)
    else {
      val locale = Locale.forLanguageTag(normalized)
      Option.when(locale.getLanguage.nonEmpty && locale.toLanguageTag != "und")(locale)
    }
  }

  private def _resolve_locale(
    policy: WebExecutionResolutionPolicy,
    input: WebExecutionResolutionInput
  ): Consequence[Locale] = {
    val display = Option.when(policy.displayOverrideEnabled)(input.displayLocale).flatten
    val user = Option.when(
      policy.applicationMode == WebApplicationMode.MultiUser && input.authenticated
    )(input.userLocale).flatten
    display.orElse(user) match {
      case Some(value) => parseLocale(value)
        .map(Consequence.success)
        .getOrElse(Consequence.argumentFormatError("locale", "BCP 47 locale", value))
      case None => policy.applicationLocale
        .orElse(input.runtimeLocale)
        .map(Consequence.success)
        .getOrElse(_negotiated_locale(policy, input))
    }
  }

  private def _negotiated_locale(
    policy: WebExecutionResolutionPolicy,
    input: WebExecutionResolutionInput
  ): Consequence[Locale] =
    if (!policy.httpLanguageNegotiationEnabled)
      Consequence.success(Locale.ROOT)
    else
      input.acceptLanguage match {
        case None => Consequence.success(Locale.ROOT)
        case Some(value) => _parse_accept_language(value).map(_.getOrElse(Locale.ROOT))
      }

  private def _resolve_timezone(
    policy: WebExecutionResolutionPolicy,
    input: WebExecutionResolutionInput
  ): Consequence[ZoneId] = {
    val display = Option.when(policy.displayOverrideEnabled)(input.displayTimezone).flatten
    val user = Option.when(
      policy.applicationMode == WebApplicationMode.MultiUser && input.authenticated
    )(input.userTimezone).flatten
    display.orElse(user) match {
      case Some(value) => Try(ZoneId.of(value.trim)).toOption
        .map(Consequence.success)
        .getOrElse(Consequence.argumentFormatError("timezone", "IANA timezone", value))
      case None => Consequence.success(
        policy.applicationTimezone.orElse(input.runtimeTimezone).getOrElse(ZoneId.of("UTC"))
      )
    }
  }

  private def _resolve_date_time_format(
    policy: WebExecutionResolutionPolicy,
    runtimepolicy: Option[String]
  ): Consequence[WebDisplayFormatPolicyId] =
    policy.dateTimeFormat match {
      case Some(value) => Consequence.success(value)
      case None => runtimepolicy.map(_.trim.toLowerCase(Locale.ROOT)).filter(_.nonEmpty) match {
        case None | Some("default") | Some("application-default") =>
          Consequence.success(WebDisplayFormatPolicyId.APPLICATION_DEFAULT)
        case Some("localized") | Some("localized-medium") =>
          Consequence.success(WebDisplayFormatPolicyId.LOCALIZED_MEDIUM)
        case Some(value) => WebDisplayFormatPolicyId.parse(value)
          .map(Consequence.success)
          .getOrElse(Consequence.argumentFormatError("dateTimeFormatPolicy", "stable display format policy identifier", value))
      }
    }

  private def _parse_accept_language(value: String): Consequence[Option[Locale]] =
    Try(Locale.LanguageRange.parse(value)).toOption match {
      case None =>
        Consequence.argumentFormatError("Accept-Language", "weighted BCP 47 language ranges", value)
      case Some(ranges) =>
        Consequence.success(
          ranges.asScala
            .find(range => range.getWeight > 0.0 && range.getRange != "*")
            .flatMap(range => parseLocale(range.getRange))
        )
    }
}
