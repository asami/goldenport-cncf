package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jan. 17, 2026
 * @version Jan. 30, 2026
 * @author  ASAMI, Tomoharu
 */
object HttpDriverFactory {
  val default = new UrlConnectionHttpDriver(ClientConfig.DefaultBaseUrl)

  // Legacy
  def create(
    driverName: String
  ): Consequence[HttpDriver] =
    driverName match {
      case "nop" | "fake" =>
        Consequence.success(FakeHttpDriver.okText("nop"))
      case "real" | "url-connection" =>
        val baseurl = sys.props.getOrElse("cncf.http.baseurl", ClientConfig.DefaultBaseUrl)
        Consequence.success(new UrlConnectionHttpDriver(baseurl))
      case "loopback" =>
        val server = LoopbackHttpServer.create()
        Consequence.success(new LoopbackHttpDriver(server))
      case other =>
        Consequence.failure(
          new IllegalArgumentException(s"Unknown http driver: ${other}")
        )
    }

  def create(
    drivername: String,
    baseurl: String
  ): Consequence[HttpDriver] = drivername match {
    case "nop" | "fake" =>
      Consequence.success(FakeHttpDriver.okText("nop"))
    case "real" | "url-connection" =>
      Consequence.success(new UrlConnectionHttpDriver(baseurl))
    case "loopback" =>
      val server = LoopbackHttpServer.create()
      Consequence.success(new LoopbackHttpDriver(server))
    case other =>
      Consequence.failure(
        new IllegalArgumentException(s"Unknown http driver: ${other}")
      )
  }
}
