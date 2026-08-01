package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jul. 17, 2026
 * @version Aug.  1, 2026
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
    subsystem: Subsystem,
    executionContext: ExecutionContext,
    request: WebExecutionRuntimeRequest
  ): Consequence[WebExecutionProjection] =
    WebExecutionResolutionPolicy.resolveForSubsystem(configuration, subsystem).flatMap(
      resolution => resolve(resolution, executionContext, request)
    )

  private[http] def resolve(
    resolution: WebExecutionPolicyResolution,
    executionContext: ExecutionContext,
    request: WebExecutionRuntimeRequest
  ): Consequence[WebExecutionProjection] = {
    given ExecutionContext = executionContext
    val subject = SecuritySubject.current
    val runtimecontext = executionContext.runtime.context
    for {
      formatting <- WebExecutionResolver.resolve(
        resolution.policy,
        resolution.applicationMode,
        WebExecutionResolutionInput(
          runtimeLocale = Some(runtimecontext.formatting.locale),
          runtimeTimezone = Some(runtimecontext.formatting.timezone),
          runtimeDateTimeFormatPolicy = Some(executionContext.core.i18n.dateTimeFormatPolicy),
          authenticated = subject.isAuthenticated,
          userLocale = _attribute(executionContext, _user_locale_keys),
          userTimezone = _attribute(executionContext, _user_timezone_keys),
          displayLocale = request.displayLocale,
          displayTimezone = request.displayTimezone,
          acceptLanguage = request.acceptLanguage
        )
      )
    } yield WebExecutionProjection.create(
      formatting.locale,
      formatting.timezone,
      formatting.projectionPolicy,
      resolution.applicationMode,
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
