package org.goldenport.cncf.config

import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jan. 18, 2025
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object ClientConfig {
  val BaseUrlKey = "client.baseurl"
  val DefaultBaseUrl = "http://localhost:8080"

  def baseUrl(
    configuration: ResolvedConfiguration
  ): Option[String] =
    configuration.get[String](BaseUrlKey).toOption.flatten
}
