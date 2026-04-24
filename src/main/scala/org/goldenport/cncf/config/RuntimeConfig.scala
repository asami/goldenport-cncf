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
import org.goldenport.cncf.observability.ObservabilityEngine

/*
 * @since   Jan. 18, 2026
 *  version Jan. 30, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 * @version Apr. 25, 2026
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
  operationMode: OperationMode = RuntimeConfig.DefaultOperationMode,
  webOperationDispatcher: String = RuntimeConfig.DefaultWebOperationDispatcher,
  webOperationDispatcherRestBaseUrl: Option[String] = None,
  webDevelopAnonymousAdmin: Boolean = RuntimeConfig.DefaultWebDevelopAnonymousAdmin,
  commandExecutionMode: Option[CommandExecutionMode] = None,
  executionHistoryConfig: ObservabilityEngine.ExecutionHistoryConfig =
    ObservabilityEngine.ExecutionHistoryConfig()
)

object RuntimeConfig {
  val ServerEmulatorBaseUrlKey = "textus.server-emulator.baseurl"
  val RuntimeServerEmulatorBaseUrlKey = "textus.runtime.server-emulator.baseurl"
  val HttpDriverKey = "textus.http.driver"
  val RuntimeHttpDriverKey = "textus.runtime.http.driver"
  val ModeKey = "textus.mode"
  val RuntimeModeKey = "textus.runtime.mode"
  val OperationModeKey = "textus.operation-mode"
  val RuntimeOperationModeKey = "textus.runtime.operation-mode"
  val CommandExecutionModeKey = "textus.command.execution-mode"
  val RuntimeCommandExecutionModeKey = "textus.runtime.command.execution-mode"
  val DebugCallTreeKey = "textus.debug.calltree"
  val RuntimeDebugCallTreeKey = "textus.runtime.debug.calltree"
  val DebugTraceJobKey = "textus.debug.trace-job"
  val RuntimeDebugTraceJobKey = "textus.runtime.debug.trace-job"
  val DebugSaveCallTreeKey = "textus.debug.save-calltree"
  val RuntimeDebugSaveCallTreeKey = "textus.runtime.debug.save-calltree"
  val ExecutionHistoryRecentLimitKey = "textus.execution.history.recent-limit"
  val RuntimeExecutionHistoryRecentLimitKey = "textus.runtime.execution.history.recent-limit"
  val ExecutionHistoryFilteredLimitKey = "textus.execution.history.filtered-limit"
  val RuntimeExecutionHistoryFilteredLimitKey = "textus.runtime.execution.history.filtered-limit"
  val ExecutionHistoryFilterOperationContainsKey = "textus.execution.history.filter.operation-contains"
  val RuntimeExecutionHistoryFilterOperationContainsKey = "textus.runtime.execution.history.filter.operation-contains"
  val DiscoverClassesKey = "textus.discover.classes"
  val RuntimeDiscoverClassesKey = "textus.runtime.discover.classes"
  val ComponentFactoryClassKey = "textus.component.factory-class"
  val RuntimeComponentFactoryClassKey = "textus.runtime.component-factory-class"
  val WorkspaceKey = "textus.workspace"
  val RuntimeWorkspaceKey = "textus.runtime.workspace"
  val ForceExitKey = "textus.force-exit"
  val RuntimeForceExitKey = "textus.runtime.force-exit"
  val NoExitKey = "textus.no-exit"
  val RuntimeNoExitKey = "textus.runtime.no-exit"
  val SubsystemNameKey = "textus.subsystem"
  val ComponentNameKey = "textus.component"
  val SubsystemDescriptorKey = "textus.subsystem.descriptor"
  val SubsystemFileKey = "textus.subsystem.file"
  val RuntimeSubsystemNameKey = "textus.runtime.subsystem"
  val RuntimeComponentNameKey = "textus.runtime.component"
  val RuntimeSubsystemDescriptorKey = "textus.runtime.subsystem.descriptor"
  val RuntimeSubsystemFileKey = "textus.runtime.subsystem.file"
  val AssemblyDescriptorKey = "textus.assembly.descriptor"
  val WebDescriptorKey = "textus.web.descriptor"
  val RepositoryDirKey = "textus.repository.dir"
  val ComponentDirKey = "textus.component.dir"
  val LogBackendKey = "textus.logging.backend"
  val RuntimeLogBackendKey = "textus.runtime.logging.backend"
  val LogLevelKey = "textus.logging.level"
  val RuntimeLogLevelKey = "textus.runtime.logging.level"
  val LogFilePathKey = "textus.logging.file.path"
  val RuntimeLogFilePathKey = "textus.runtime.logging.file.path"
  val WebOperationDispatcherKey = "textus.web.operation.dispatcher"
  val RuntimeWebOperationDispatcherKey = "textus.runtime.web.operation.dispatcher"
  val WebOperationDispatcherRestBaseUrlKey = "textus.web.operation.dispatcher.rest.base-url"
  val RuntimeWebOperationDispatcherRestBaseUrlKey = "textus.runtime.web.operation.dispatcher.rest.base-url"
  val WebDevelopAnonymousAdminKey = "textus.web.develop.anonymous-admin"
  val RuntimeWebDevelopAnonymousAdminKey = "textus.runtime.web.develop.anonymous-admin"

  val DefaultServerEmulatorBaseUrl = "http://localhost/"
  val DefaultHttpDriverName = "real"
  val DefaultMode = "command"
  val DefaultOperationMode = OperationMode.Develop
  val DefaultLogFilePath = ".textus/data.d/trace.log"
  val DefaultWebOperationDispatcher = "local"
  val DefaultWebDevelopAnonymousAdmin = true

  val default: RuntimeConfig =
    RuntimeConfig(
      LogBackend.NopLogBackend,
      LogLevel.Info,
      serverEmulatorBaseUrl = DefaultServerEmulatorBaseUrl,
      httpDriver = HttpDriverFactory.default,
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = new EntityStoreSpace(),
      mode = RunMode.Command,
      operationMode = DefaultOperationMode,
      webOperationDispatcher = DefaultWebOperationDispatcher,
      webOperationDispatcherRestBaseUrl = None,
      webDevelopAnonymousAdmin = DefaultWebDevelopAnonymousAdmin,
      commandExecutionMode = None,
      executionHistoryConfig = ObservabilityEngine.ExecutionHistoryConfig()
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
    val operationMode =
      _get_string(configuration, OperationModeKey)
        .flatMap(OperationMode.from)
        .getOrElse(DefaultOperationMode)
    val commandExecutionMode =
      _get_string(configuration, CommandExecutionModeKey)
        .flatMap(parseCommandExecutionMode)
    val logbackend: LogBackend = {
      val name = _get_string(configuration, LogBackendKey)
      val logfile =
        _get_string(configuration, LogFilePathKey).
          getOrElse(DefaultLogFilePath)
      name match {
        case Some("file") =>
          LogBackend.FileLogBackend(logfile)
        case Some(s) =>
          LogBackend.fromString(s) getOrElse LogBackend.StderrBackend
        case None =>
          RuntimeDefaults.defaultLogBackend(mode)
      }
    } match {
      case backend if _is_test_runtime && _is_console_log_backend(backend) =>
        LogBackend.NopLogBackend
      case backend =>
        backend
    }
    val loglevel = {
      val name = _get_string(configuration, LogLevelKey)
      name match {
        case Some(s) => LogLevel.from(s) getOrElse LogLevel.Warn
        case None => RuntimeDefaults.defaultLogLevel(mode)
      }
    }
    val datastorespace = DataStoreSpace.create(configuration)
    val entitystorespace = EntityStoreSpace.create(configuration)
    val executionHistoryConfig = _execution_history_config(configuration)
    val webOperationDispatcher =
      _get_string(configuration, WebOperationDispatcherKey)
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .getOrElse(DefaultWebOperationDispatcher)
    val webOperationDispatcherRestBaseUrl =
      _get_string(configuration, WebOperationDispatcherRestBaseUrlKey)
    val webDevelopAnonymousAdmin =
      _get_boolean(configuration, WebDevelopAnonymousAdminKey)
        .getOrElse(DefaultWebDevelopAnonymousAdmin)
    ObservabilityEngine.updateExecutionHistoryConfig(executionHistoryConfig)
    RuntimeConfig(
      logbackend,
      loglevel,
      serverEmulatorBaseUrl = baseurl,
      httpDriver = httpdriver,
      dataStoreSpace = datastorespace,
      entityStoreSpace = entitystorespace,
      mode = mode,
      operationMode = operationMode,
      webOperationDispatcher = webOperationDispatcher,
      webOperationDispatcherRestBaseUrl = webOperationDispatcherRestBaseUrl,
      webDevelopAnonymousAdmin = webDevelopAnonymousAdmin,
      commandExecutionMode = commandExecutionMode,
      executionHistoryConfig = executionHistoryConfig
    )
  }

  private def _is_console_log_backend(
    backend: LogBackend
  ): Boolean =
    backend == LogBackend.StdoutBackend || backend == LogBackend.StderrBackend

  private def _is_test_runtime: Boolean =
    _is_truthy(sys.props.get("textus.test"))

  private def _is_truthy(
    value: Option[String]
  ): Boolean =
    value.exists { v =>
      val normalized = v.trim.toLowerCase(java.util.Locale.ROOT)
      normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
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

  private def _get_int(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[Int] =
    _get_string(configuration, key).flatMap(x => scala.util.Try(x.trim.toInt).toOption)

  private def _get_boolean(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[Boolean] =
    _get_string(configuration, key).flatMap(_parse_boolean)

  private def _parse_boolean(
    value: String
  ): Option[Boolean] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => Some(true)
      case "false" | "0" | "no" | "off" => Some(false)
      case _ => None
    }

  private def _execution_history_config(
    configuration: ResolvedConfiguration
  ): ObservabilityEngine.ExecutionHistoryConfig = {
    val defaults = ObservabilityEngine.ExecutionHistoryConfig()
    val recentLimit =
      _get_int(configuration, ExecutionHistoryRecentLimitKey).getOrElse(defaults.recentLimit)
    val filteredLimit =
      _get_int(configuration, ExecutionHistoryFilteredLimitKey).getOrElse(defaults.filteredLimit)
    val filters =
      _split_csv(_get_string(configuration, ExecutionHistoryFilterOperationContainsKey))
        .map(x => ObservabilityEngine.ExecutionHistoryFilter(operationContains = Some(x)))
    defaults.copy(
      recentLimit = math.max(0, recentLimit),
      filteredLimit = math.max(0, filteredLimit),
      filters = filters
    )
  }

  private def _split_csv(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _legacy_aliases(
    key: String
  ): Vector[String] = {
    val textusRuntime =
      key match {
        case ServerEmulatorBaseUrlKey => Vector(RuntimeServerEmulatorBaseUrlKey)
        case HttpDriverKey => Vector(RuntimeHttpDriverKey)
        case ModeKey => Vector(RuntimeModeKey)
        case OperationModeKey => Vector(RuntimeOperationModeKey)
        case CommandExecutionModeKey => Vector(RuntimeCommandExecutionModeKey)
        case DebugCallTreeKey => Vector(RuntimeDebugCallTreeKey)
        case DebugTraceJobKey => Vector(RuntimeDebugTraceJobKey)
        case DebugSaveCallTreeKey => Vector(RuntimeDebugSaveCallTreeKey)
        case ExecutionHistoryRecentLimitKey => Vector(RuntimeExecutionHistoryRecentLimitKey)
        case ExecutionHistoryFilteredLimitKey => Vector(RuntimeExecutionHistoryFilteredLimitKey)
        case ExecutionHistoryFilterOperationContainsKey => Vector(RuntimeExecutionHistoryFilterOperationContainsKey)
        case DiscoverClassesKey => Vector(RuntimeDiscoverClassesKey)
        case ComponentFactoryClassKey => Vector(RuntimeComponentFactoryClassKey)
        case WorkspaceKey => Vector(RuntimeWorkspaceKey)
        case ForceExitKey => Vector(RuntimeForceExitKey)
        case NoExitKey => Vector(RuntimeNoExitKey)
        case SubsystemNameKey => Vector(RuntimeSubsystemNameKey)
        case ComponentNameKey => Vector(RuntimeComponentNameKey)
        case SubsystemDescriptorKey => Vector(RuntimeSubsystemDescriptorKey)
        case SubsystemFileKey => Vector(RuntimeSubsystemFileKey)
        case LogBackendKey => Vector(RuntimeLogBackendKey)
        case LogLevelKey => Vector(RuntimeLogLevelKey)
        case LogFilePathKey => Vector(RuntimeLogFilePathKey)
        case WebOperationDispatcherKey => Vector(RuntimeWebOperationDispatcherKey)
        case WebOperationDispatcherRestBaseUrlKey => Vector(RuntimeWebOperationDispatcherRestBaseUrlKey)
        case WebDevelopAnonymousAdminKey => Vector(RuntimeWebDevelopAnonymousAdminKey)
        case _ => Vector.empty
      }
    val cncfAliases =
      (key +: textusRuntime).collect {
        case k if k.startsWith("textus.") => "cncf." + k.stripPrefix("textus.")
      }
    textusRuntime ++ cncfAliases
  }
}

enum OperationMode(val name: String) {
  case Production extends OperationMode("production")
  case Demo extends OperationMode("demo")
  case Develop extends OperationMode("develop")
  case Test extends OperationMode("test")

  def allowsDevelopAnonymousAdmin: Boolean =
    this == OperationMode.Develop || this == OperationMode.Test
}

object OperationMode {
  def from(value: String): Option[OperationMode] = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")
    values.find(_.name == normalized).orElse {
      normalized match {
        case "prod" => Some(Production)
        case "dev" => Some(Develop)
        case _ => None
      }
    }
  }
}
