package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebExecutionRuntimeRequest(
  displayLocale: Option[String] = None,
  displayTimezone: Option[String] = None,
  acceptLanguage: Option[String] = None,
  publicDisplayName: Option[String] = None
)

object WebExecutionRuntimeProjection {
  private val _user_locale_keys = Vector("locale", "user.locale", "textus.locale")
  private val _user_timezone_keys = Vector(
    "timeZone",
    "timezone",
    "time_zone",
    "user.timeZone",
    "user.timezone"
  )

  def resolve(
    configuration: ResolvedConfiguration,
    executioncontext: ExecutionContext,
    request: WebExecutionRuntimeRequest
  ): Consequence[WebExecutionProjection] = {
    given ExecutionContext = executioncontext
    val subject = SecuritySubject.current
    for {
      policy <- WebExecutionResolutionPolicy.fromConfiguration(configuration)
      formatting <- WebExecutionResolver.resolve(
        policy,
        WebExecutionResolutionInput(
          runtimeLocale = Some(executioncontext.core.locale),
          runtimeTimezone = Some(executioncontext.core.timezone),
          runtimeDateTimeFormatPolicy = Some(executioncontext.core.i18n.dateTimeFormatPolicy),
          authenticated = subject.isAuthenticated,
          userLocale = _attribute(executioncontext, _user_locale_keys),
          userTimezone = _attribute(executioncontext, _user_timezone_keys),
          displayLocale = request.displayLocale,
          displayTimezone = request.displayTimezone,
          acceptLanguage = request.acceptLanguage
        )
      )
    } yield WebExecutionProjection.create(
      formatting.locale,
      formatting.timezone,
      formatting.projectionPolicy,
      WebExecutionSubjectProjection.create(subject.isAuthenticated, request.publicDisplayName),
      subject.capabilities
    )
  }

  private def _attribute(
    executioncontext: ExecutionContext,
    keys: Vector[String]
  ): Option[String] =
    keys.iterator
      .flatMap(executioncontext.security.principal.attributes.get)
      .map(_.trim)
      .find(_.nonEmpty)
}
