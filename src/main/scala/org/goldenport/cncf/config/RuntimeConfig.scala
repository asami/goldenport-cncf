package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.observability.LogLevel
import org.goldenport.cncf.http.{HttpDriver, FakeHttpDriver, HttpDriverFactory}

/*
 * @since   Jan. 18, 2026
 *  version Jan. 30, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeConfig(
  logBackend: LogBackend,
  logLevel: LogLevel,
  serverEmulatorBaseUrl: String,
  httpDriver: HttpDriver,
  mode: RunMode
)

object RuntimeConfig {
  val ServerEmulatorBaseUrlKey = "cncf.runtime.server-emulator.baseurl"
  val HttpDriverKey = "cncf.runtime.http.driver"
  val ModeKey = "cncf.runtime.mode"

  val DefaultServerEmulatorBaseUrl = "http://localhost/"
  val DefaultHttpDriverName = "real"
  val DefaultMode = "command"

  val default: RuntimeConfig =
    RuntimeConfig(
      LogBackend.NopLogBackend,
      LogLevel.Info,
      serverEmulatorBaseUrl = DefaultServerEmulatorBaseUrl,
      httpDriver = HttpDriverFactory.default,
      mode = RunMode.Command
    )

  def from(configuration: ResolvedConfiguration): RuntimeConfig = {
    val baseurl =
      extractString(configuration.get[String](ServerEmulatorBaseUrlKey))
        .getOrElse(DefaultServerEmulatorBaseUrl)
    val httpdriver = {
      val a = extractString(configuration.get[String](HttpDriverKey))
        .getOrElse(DefaultHttpDriverName)
      HttpDriverFactory.create(a, baseurl) match {
        case Consequence.Success(driver) =>
          driver
        case Consequence.Failure(conclusion) =>
//          _print_error(conclusion) TODO
          FakeHttpDriver.okText("nop")
      }
    }
    val modeName =
      extractString(configuration.get[String](ModeKey))
        .getOrElse(DefaultMode)
    val mode =
      RunMode.from(modeName).getOrElse(RunMode.Command)
    val logbackend: LogBackend = {
      val name = extractString(configuration.get[String]("cncf.runtime.logging.backend")).
        orElse(extractString(configuration.get[String]("cncf.logging.backend")))
      name match {
        case Some(s) => LogBackend.fromString(s) getOrElse LogBackend.StderrBackend
        case None => mode match {
          case RunMode.Server => LogBackend.StdoutBackend
          case _ => LogBackend.NopLogBackend
        }
      }
    }
    val loglevel = {
      val name = extractString(configuration.get[String]("cncf.runtime.logging.level")).
        orElse(extractString(configuration.get[String]("cncf.logging.level")))
      name match {
        case Some(s) => LogLevel.from(s) getOrElse LogLevel.Warn
        case None => LogLevel.Info
      }
    }
    RuntimeConfig(
      logbackend,
      loglevel,
      serverEmulatorBaseUrl = baseurl,
      httpDriver = httpdriver,
      mode = mode
    )
  }

  private def extractString(
    value: Consequence[Option[String]]
  ): Option[String] =
    value match {
      case Consequence.Success(opt) => opt
      case Consequence.Failure(_) => None
    }

  def create(conf: ResolvedConfiguration): Consequence[RuntimeConfig] = Consequence {
    from(conf)
  }
}
