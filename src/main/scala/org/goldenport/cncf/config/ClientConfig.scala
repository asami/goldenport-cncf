package org.goldenport.cncf.config

import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jan. 18, 2025
 *  version Mar. 20, 2026
 * @version Apr.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object ClientConfig {
  val BaseUrlKey = "client.baseurl"
  val DefaultPortKey = "cncf.server.port"
  val DefaultPort = sys.props.getOrElse(DefaultPortKey, "8080")
  val DefaultBaseUrl = s"http://localhost:$DefaultPort"

  def baseUrl(
    configuration: ResolvedConfiguration
  ): Option[String] =
    configuration.get[String](BaseUrlKey).toOption.flatten
}
