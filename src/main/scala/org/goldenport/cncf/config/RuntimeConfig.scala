package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.configuration.ResolvedConfiguration

final case class RuntimeConfig(
  serverEmulatorBaseUrl: String,
  httpDriver: String,
  mode: RunMode
)

object RuntimeConfig {
  val ServerEmulatorBaseUrlKey = "cncf.runtime.server-emulator.baseurl"
  val HttpDriverKey = "cncf.runtime.http.driver"
  val ModeKey = "cncf.runtime.mode"

  val DefaultServerEmulatorBaseUrl = "http://localhost/"
  val DefaultHttpDriver = "real"
  val DefaultMode = "command"

  def from(configuration: ResolvedConfiguration): RuntimeConfig = {
    val baseUrl =
      extractString(configuration.get[String](ServerEmulatorBaseUrlKey))
        .getOrElse(DefaultServerEmulatorBaseUrl)
    val httpDriver =
      extractString(configuration.get[String](HttpDriverKey))
        .getOrElse(DefaultHttpDriver)
    val modeName =
      extractString(configuration.get[String](ModeKey))
        .getOrElse(DefaultMode)
    val mode =
      RunMode.from(modeName).getOrElse(RunMode.Command)
    RuntimeConfig(
      serverEmulatorBaseUrl = baseUrl,
      httpDriver = httpDriver,
      mode = mode
    )
  }

  val default: RuntimeConfig =
    RuntimeConfig(
      serverEmulatorBaseUrl = DefaultServerEmulatorBaseUrl,
      httpDriver = DefaultHttpDriver,
      mode = RunMode.Command
    )

  private def extractString(
    value: Consequence[Option[String]]
  ): Option[String] =
    value match {
      case Consequence.Success(opt) => opt
      case Consequence.Failure(_) => None
    }
}
