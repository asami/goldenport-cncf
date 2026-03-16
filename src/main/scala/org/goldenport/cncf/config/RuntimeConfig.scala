package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.observability.LogLevel
import org.goldenport.cncf.http.{HttpDriver, FakeHttpDriver, HttpDriverFactory}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.EntityStoreSpace
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.config.RuntimeDefaults

/*
 * @since   Jan. 18, 2026
 *  version Jan. 30, 2026
 *  version Feb.  1, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeConfig(
  logBackend: LogBackend,
  logLevel: LogLevel,
  serverEmulatorBaseUrl: String,
  httpDriver: HttpDriver,
  dataStoreSpace: DataStoreSpace,
  entityStoreSpace: EntityStoreSpace,
  mode: RunMode
)

object RuntimeConfig {
  val ServerEmulatorBaseUrlKey = "cncf.runtime.server-emulator.baseurl"
  val HttpDriverKey = "cncf.runtime.http.driver"
  val ModeKey = "cncf.runtime.mode"
  val LogBackendKey = "cncf.logging.backend"
  val RuntimeLogBackendKey = "cncf.runtime.logging.backend"
  val LogLevelKey = "cncf.logging.level"
  val RuntimeLogLevelKey = "cncf.runtime.logging.level"
  val LogFilePathKey = "cncf.logging.file.path"
  val RuntimeLogFilePathKey = "cncf.runtime.logging.file.path"

  val DefaultServerEmulatorBaseUrl = "http://localhost/"
  val DefaultHttpDriverName = "real"
  val DefaultMode = "command"
  val DefaultLogFilePath = ".cncf/data.d/trace.log"

  val default: RuntimeConfig =
    RuntimeConfig(
      LogBackend.NopLogBackend,
      LogLevel.Info,
      serverEmulatorBaseUrl = DefaultServerEmulatorBaseUrl,
      httpDriver = HttpDriverFactory.default,
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = new EntityStoreSpace(),
      mode = RunMode.Command
    )

  def from(
    configuration: ResolvedConfiguration,
    modeOverride: Option[RunMode] = None
  ): RuntimeConfig = {
    val baseurl =
      ConfigurationAccess.getString(configuration, ServerEmulatorBaseUrlKey)
        .getOrElse(DefaultServerEmulatorBaseUrl)
    val httpdriver = {
      val a = ConfigurationAccess.getString(configuration, HttpDriverKey)
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
      ConfigurationAccess.getString(configuration, ModeKey)
        .getOrElse(DefaultMode)
    val mode =
      modeOverride.orElse(RunMode.from(modeName)).getOrElse(RunMode.Command)
    val logbackend: LogBackend = {
      val name = ConfigurationAccess.getString(configuration, RuntimeLogBackendKey).
        orElse(ConfigurationAccess.getString(configuration, LogBackendKey))
      val logfile =
        ConfigurationAccess.getString(configuration, RuntimeLogFilePathKey).
          orElse(ConfigurationAccess.getString(configuration, LogFilePathKey)).
          getOrElse(DefaultLogFilePath)
      name match {
        case Some("file") =>
          LogBackend.FileLogBackend(logfile)
        case Some(s) =>
          LogBackend.fromString(s) getOrElse LogBackend.StderrBackend
        case None =>
          RuntimeDefaults.defaultLogBackend(mode)
      }
    }
    val loglevel = {
      val name = ConfigurationAccess.getString(configuration, RuntimeLogLevelKey).
        orElse(ConfigurationAccess.getString(configuration, LogLevelKey))
      name match {
        case Some(s) => LogLevel.from(s) getOrElse LogLevel.Warn
        case None => RuntimeDefaults.defaultLogLevel(mode)
      }
    }
    val datastorespace = DataStoreSpace.create(configuration)
    val entitystorespace = EntityStoreSpace.create(configuration)
    RuntimeConfig(
      logbackend,
      loglevel,
      serverEmulatorBaseUrl = baseurl,
      httpDriver = httpdriver,
      dataStoreSpace = datastorespace,
      entityStoreSpace = entitystorespace,
      mode = mode
    )
  }

  def create(conf: ResolvedConfiguration): Consequence[RuntimeConfig] = Consequence {
    from(conf)
  }
}
