package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration

object ClientConfig {
  val BaseUrlKey = "client.baseurl"
  val DefaultBaseUrl = "http://localhost:8080"

  def baseUrl(
    configuration: ResolvedConfiguration
  ): Option[String] =
    configuration.get[String](BaseUrlKey) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(_) => None
    }
}
