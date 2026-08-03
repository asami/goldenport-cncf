package org.goldenport.cncf.config

import java.time.ZoneId
import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCollection}

/** Fixed-user values resolved exclusively from the final typed binding collection. */
final case class ResolvedStandaloneUserProfile private[cncf] (
  id: String,
  displayName: Option[String],
  locale: Option[Locale],
  timezone: Option[ZoneId]
)

object ResolvedStandaloneUserProfile {
  def resolve(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[ResolvedStandaloneUserProfile] =
    if (bindings == null)
      Consequence.configurationInvalid("fixed-user configuration bindings are required")
    else
      for {
        idbinding <- bindings.binding(CncfConfigurationParameterCatalog.fixedUserId)
        id <- idbinding.map(_id).getOrElse(
          Consequence.configurationInvalid("fixed-user profile requires textus.fixed-user.id")
        )
        displayname <- bindings.value(CncfConfigurationParameterCatalog.fixedUserDisplayName)
        locale <- bindings.value(CncfConfigurationParameterCatalog.fixedUserLocale)
        timezone <- bindings.value(CncfConfigurationParameterCatalog.fixedUserTimezone)
      } yield ResolvedStandaloneUserProfile(id, displayname, locale, timezone)

  private def _id(
    binding: ConfigurationBinding[String, CncfConfigurationTarget]
  ): Consequence[String] =
    if (_ids(binding).distinct.size > 1)
      Consequence.configurationInvalid("fixed-user identity changes within one resolved configuration are not admitted")
    else
      Consequence.success(binding.value)

  private def _ids(
    binding: ConfigurationBinding[String, CncfConfigurationTarget]
  ): Vector[String] =
    binding.value +: binding.overridden.toVector.flatMap(_ids)
}
