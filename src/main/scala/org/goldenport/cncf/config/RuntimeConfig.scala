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
import org.goldenport.cncf.action.CommandExecutionMode

/*
 * @since   Jan. 18, 2026
 *  version Jan. 30, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuntimeConfig(
  logBackend: LogBackend,
  logLevel: LogLevel,
  serverEmulatorBaseUrl: String,
  httpDriver: HttpDriver,
  dataStoreSpace: DataStoreSpace,
  entityStoreSpace: EntityStoreSpace,
  mode: RunMode,
  commandExecutionMode: Option[CommandExecutionMode] = None
)

object RuntimeConfig {
  val ServerEmulatorBaseUrlKey = "textus.runtime.server-emulator.baseurl"
  val HttpDriverKey = "textus.runtime.http.driver"
  val ModeKey = "textus.runtime.mode"
  val CommandExecutionModeKey = "textus.command.execution-mode"
  val RuntimeCommandExecutionModeKey = "textus.runtime.command.execution-mode"
  val DiscoverClassesKey = "cncf.runtime.discover.classes"
  val ComponentFactoryClassKey = "cncf.runtime.component-factory-class"
  val WorkspaceKey = "cncf.runtime.workspace"
  val ForceExitKey = "cncf.runtime.force-exit"
  val NoExitKey = "cncf.runtime.no-exit"
  val SubsystemNameKey = "textus.subsystem"
  val ComponentNameKey = "textus.component"
  val SubsystemDescriptorKey = "textus.subsystem.descriptor"
  val SubsystemFileKey = "textus.subsystem.file"
  val RuntimeSubsystemNameKey = "textus.runtime.subsystem"
  val RuntimeComponentNameKey = "textus.runtime.component"
  val RuntimeSubsystemDescriptorKey = "textus.runtime.subsystem.descriptor"
  val RuntimeSubsystemFileKey = "textus.runtime.subsystem.file"
  val AssemblyDescriptorKey = "textus.assembly.descriptor"
  val RepositoryDirKey = "textus.repository.dir"
  val ComponentDirKey = "textus.component.dir"
  val LogBackendKey = "textus.logging.backend"
  val RuntimeLogBackendKey = "textus.runtime.logging.backend"
  val LogLevelKey = "textus.logging.level"
  val RuntimeLogLevelKey = "textus.runtime.logging.level"
  val LogFilePathKey = "textus.logging.file.path"
  val RuntimeLogFilePathKey = "textus.runtime.logging.file.path"

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
      mode = RunMode.Command,
      commandExecutionMode = None
    )

  def from(
    configuration: ResolvedConfiguration,
    modeOverride: Option[RunMode] = None
  ): RuntimeConfig = {
    val baseurl =
      _get_string(configuration, ServerEmulatorBaseUrlKey)
        .getOrElse(DefaultServerEmulatorBaseUrl)
    val httpdriver = {
      val a = _get_string(configuration, HttpDriverKey)
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
      _get_string(configuration, ModeKey)
        .getOrElse(DefaultMode)
    val mode =
      modeOverride.orElse(RunMode.from(modeName)).getOrElse(RunMode.Command)
    val commandExecutionMode =
      _get_string(configuration, CommandExecutionModeKey)
        .flatMap(parseCommandExecutionMode)
    val logbackend: LogBackend = {
      val name = _get_string(configuration, RuntimeLogBackendKey).
        orElse(_get_string(configuration, LogBackendKey))
      val logfile =
        _get_string(configuration, RuntimeLogFilePathKey).
          orElse(_get_string(configuration, LogFilePathKey)).
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
      val name = _get_string(configuration, RuntimeLogLevelKey).
        orElse(_get_string(configuration, LogLevelKey))
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
      mode = mode,
      commandExecutionMode = commandExecutionMode
    )
  }

  def create(conf: ResolvedConfiguration): Consequence[RuntimeConfig] = Consequence {
    from(conf)
  }

  def parseCommandExecutionMode(
    value: String
  ): Option[CommandExecutionMode] = {
    value.trim.toLowerCase match {
      case "async" | "async-job" => Some(CommandExecutionMode.AsyncJob)
      case "async-job-and-await" => Some(CommandExecutionMode.AsyncJobAndAwait)
      case "sync-job" => Some(CommandExecutionMode.SyncJob)
      case "sync-job-async-interface" => Some(CommandExecutionMode.SyncJobAsyncInterface)
      case "sync" | "sync-direct" | "sync-direct-no-job" => Some(CommandExecutionMode.SyncDirectNoJob)
      case _ => None
    }
  }

  def getString(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[String] =
    ConfigurationAccess.getString(configuration, key)
      .orElse(_legacy_aliases(key).iterator.flatMap(ConfigurationAccess.getString(configuration, _)).toSeq.headOption)

  private def _get_string(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[String] =
    getString(configuration, key)

  private def _legacy_aliases(
    key: String
  ): Vector[String] = {
    val textusRuntime =
      key match {
        case CommandExecutionModeKey => Vector(RuntimeCommandExecutionModeKey)
        case SubsystemNameKey => Vector(RuntimeSubsystemNameKey)
        case ComponentNameKey => Vector(RuntimeComponentNameKey)
        case SubsystemDescriptorKey => Vector(RuntimeSubsystemDescriptorKey)
        case SubsystemFileKey => Vector(RuntimeSubsystemFileKey)
        case _ => Vector.empty
      }
    val cncfAliases =
      (key +: textusRuntime).collect {
        case k if k.startsWith("textus.") => "cncf." + k.stripPrefix("textus.")
      }
    textusRuntime ++ cncfAliases
  }
}
