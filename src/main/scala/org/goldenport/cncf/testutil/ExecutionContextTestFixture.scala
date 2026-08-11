package org.goldenport.cncf.testutil

import java.time.Clock
import java.util.Locale
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.context.I18nContext

/*
 * @since   Aug. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
/** General downstream ExecutionContext test support with no runtime-binding admission responsibility. */
object ExecutionContextTestFixture {
  def withClock(
    context: ExecutionContext,
    clock: Clock
  ): ExecutionContext = context match {
    case instance: ExecutionContext.Instance =>
      instance.copy(core = ExecutionContext.create(clock).core)
  }

  def withLocalePolicy(
    context: ExecutionContext,
    locale: Locale,
    allowedLocales: Set[Locale]
  ): ExecutionContext = context match {
    case instance: ExecutionContext.Instance =>
      val i18n = I18nContext.Instant(instance.i18n.core.copy(
        locale = Some(locale),
        allowedLocales = Some(allowedLocales)
      ))
      instance.copy(core = instance.core.copy(i18n = i18n, locale = locale))
  }

  def withSecurityContext(
    context: ExecutionContext,
    security: SecurityContext,
    unitOfWorkToken: String
  ): ExecutionContext = {
    lazy val secured = ExecutionContext.withSecurityContext(context, security)
    lazy val rebound: ExecutionContext =
      ExecutionContext.withRuntimeContext(secured, runtime)
    lazy val runtime =
      secured.runtime.withUnitOfWorkContext(rebound, unitOfWorkToken)
    rebound
  }
}
