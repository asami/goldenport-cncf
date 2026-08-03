package org.goldenport.cncf.importer

import org.goldenport.Consequence
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationTarget}
import org.goldenport.configuration.ConfigurationBindingCollection

/*
 * The value-only startup-import projection.  Source provenance, aliases, and
 * raw runtime configuration remain outside the importer after binding admission.
 *
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class StartupImportConfiguration(
  dataSource: Option[String],
  entitySource: Option[String]
)

object StartupImportConfiguration {
  def from(
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[StartupImportConfiguration] =
    if (bindings == null)
      Consequence.configurationInvalid("startup-import configuration bindings are required")
    else
      for {
        data <- bindings.value(CncfConfigurationParameterCatalog.startupImportDataFile)
        entity <- bindings.value(CncfConfigurationParameterCatalog.startupImportEntityFile)
      } yield StartupImportConfiguration(_normalize(data), _normalize(entity))

  private def _normalize(value: Option[String]): Option[String] =
    value.flatMap(Option(_)).map(_.trim).filter(_.nonEmpty)
}
