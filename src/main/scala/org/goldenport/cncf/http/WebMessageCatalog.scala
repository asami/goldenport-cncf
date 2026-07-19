package org.goldenport.cncf.http

import java.util.Locale

import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebMessageCatalog(
  app: String,
  locale: Locale,
  messages: Map[String, String]
)

object WebMessageCatalogRuntime {
  def resolve(
    subsystem: Subsystem,
    app: String,
    locale: Locale,
    runtimeLocale: Locale,
    runtimeMessages: Map[String, String]
  ): Map[String, String] = {
    val normalizedapp = NamingConventions.toNormalizedSegment(app)
    val catalogs = subsystem.components
      .flatMap(_.webMessageCatalogs)
      .filter(catalog => NamingConventions.toNormalizedSegment(catalog.app) == normalizedapp)
    val basemessages =
      if (_is_locale_layer(runtimeLocale, locale)) runtimeMessages
      else Map.empty
    _locale_layers(locale).foldLeft(basemessages) { (acc, tag) =>
      catalogs.iterator
        .filter(catalog => _locale_tag(catalog.locale) == tag)
        .foldLeft(acc)((z, catalog) => z ++ catalog.messages)
    }
  }

  private def _locale_layers(locale: Locale): Vector[String] =
    Vector(
      Locale.ROOT.toLanguageTag,
      locale.getLanguage,
      locale.toLanguageTag
    ).map(_.toLowerCase(Locale.ROOT)).filter(_.nonEmpty).distinct

  private def _locale_tag(locale: Locale): String =
    locale.toLanguageTag.toLowerCase(Locale.ROOT)

  private def _is_locale_layer(candidate: Locale, locale: Locale): Boolean =
    candidate == Locale.ROOT ||
      _locale_tag(candidate) == _locale_tag(locale) ||
      (candidate.getLanguage.nonEmpty && candidate.getLanguage == locale.getLanguage)
}
