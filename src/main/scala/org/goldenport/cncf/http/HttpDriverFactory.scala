package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration

object HttpDriverFactory {
  private val RuntimeKey = "cncf.runtime.http.driver"
  private val FallbackKey = "cncf.http.driver"
  private val DefaultName = "url-connection"

  def create(
    config: ResolvedConfiguration
  ): Consequence[HttpDriver] = {
    val name = getString(config, RuntimeKey)
      .orElse(getString(config, FallbackKey))
      .getOrElse(DefaultName)
    fromString(name)
  }

  private def getString(
    config: ResolvedConfiguration,
    key: String
  ): Option[String] =
    config.get[String](key) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(_) => None
    }

  private def fromString(
    name: String
  ): Consequence[HttpDriver] =
    name match {
      case "url-connection" =>
        val baseurl = sys.props.getOrElse("cncf.http.baseurl", "http://localhost:8080")
        Consequence.success(new UrlConnectionHttpDriver(baseurl))
      case "nop" =>
        Consequence.success(FakeHttpDriver.okText("nop"))
      case other =>
        Consequence.failure(
          new IllegalArgumentException(s"Unknown http driver: ${other}")
        )
    }
}
