package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.configuration.ResolvedConfiguration

object HttpDriverFactory {
  def create(
    driverName: String
  ): Consequence[HttpDriver] =
    driverName match {
      case "nop" | "fake" =>
        Consequence.success(FakeHttpDriver.okText("nop"))
      case "real" | "url-connection" =>
        val baseurl = sys.props.getOrElse("cncf.http.baseurl", ClientConfig.DefaultBaseUrl)
        Consequence.success(new UrlConnectionHttpDriver(baseurl))
      case other =>
        Consequence.failure(
          new IllegalArgumentException(s"Unknown http driver: ${other}")
        )
    }
}
