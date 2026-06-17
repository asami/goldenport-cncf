package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.observability.LogLevel
import org.goldenport.cncf.http.{HttpDriver, FakeHttpDriver, HttpDriverFactory, StaticFormAppRendererConfig}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.{EntityStore, EntityStoreSpace}
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.action.CommandExecutionMode
import org.goldenport.cncf.context.IdGenerationContext
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, ObservabilityEngine, OpenTelemetryExportConfig}
import org.goldenport.cncf.blob.BlobStoreConfig

/*
 * @since   Jan. 18, 2026
 *  version Jan. 30, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 *  version Apr. 30, 2026
 * @version Jun. 18, 2026
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
  webProductionAdminEnabled: Boolean = RuntimeConfig.DefaultWebProductionAdminEnabled,
  webProductionAdminSystemRoles: Vector[String] = RuntimeConfig.DefaultWebProductionAdminSystemRoles,
  webProductionAdminComponentRoles: Vector[String] = RuntimeConfig.DefaultWebProductionAdminComponentRoles,
  webProductionAdminJobsRoles: Vector[String] = RuntimeConfig.DefaultWebProductionAdminJobsRoles,
  debugAuthConfig: RuntimeConfig.DebugAuthConfig = RuntimeConfig.DebugAuthConfig(),
  commandExecutionMode: Option[CommandExecutionMode] = None,
  executionHistoryConfig: ObservabilityEngine.ExecutionHistoryConfig =
    ObservabilityEngine.ExecutionHistoryConfig(),
  diagnosticPayloadExternalizationConfig: DiagnosticPayloadExternalizationConfig =
    DiagnosticPayloadExternalizationConfig(),
  openTelemetryExportConfig: OpenTelemetryExportConfig =
    OpenTelemetryExportConfig(),
  staticFormAppRendererConfig: StaticFormAppRendererConfig =
    StaticFormAppRendererConfig.default,
  blobStoreConfig: BlobStoreConfig = BlobStoreConfig(),
  idNamespace: IdGenerationContext.IdNamespace = IdGenerationContext.DefaultNamespace
)

object RuntimeConfig {
  final case class DebugAuthConfig(
    enabled: Boolean = false,
    seedAccountEnabled: Boolean = false,
    autoLoginEnabled: Boolean = false,
    loginName: String = RuntimeConfig.DEFAULT_DEBUG_AUTH_LOGIN_NAME,
    email: String = RuntimeConfig.DEFAULT_DEBUG_AUTH_EMAIL,
    password: String = RuntimeConfig.DEFAULT_DEBUG_AUTH_PASSWORD,
    status: String = RuntimeConfig.DEFAULT_DEBUG_AUTH_STATUS
  ) {
    def effectiveSeedAccountEnabled: Boolean =
      enabled && seedAccountEnabled

    def effectiveAutoLoginEnabled: Boolean =
      enabled && autoLoginEnabled

    def validationError(operationmode: OperationMode): Option[String] =
      if (enabled && !operationmode.allowsDebugAuth)
        Some("textus.debug.auth.enabled is only allowed in demo, develop, or test operation mode")
      else
        None
  }

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
  val IdNamespaceMajorKey = "textus.id.namespace.major"
  val RuntimeIdNamespaceMajorKey = "textus.runtime.id.namespace.major"
  val IdNamespaceMinorKey = "textus.id.namespace.minor"
  val RuntimeIdNamespaceMinorKey = "textus.runtime.id.namespace.minor"
  val DebugCallTreeKey = "textus.debug.calltree"
  val RuntimeDebugCallTreeKey = "textus.runtime.debug.calltree"
  val DebugTraceJobKey = "textus.debug.trace-job"
  val RuntimeDebugTraceJobKey = "textus.runtime.debug.trace-job"
  val DebugSaveCallTreeKey = "textus.debug.save-calltree"
  val RuntimeDebugSaveCallTreeKey = "textus.runtime.debug.save-calltree"
  val DEBUG_AUTH_ENABLED_KEY = "textus.debug.auth.enabled"
  val RUNTIME_DEBUG_AUTH_ENABLED_KEY = "textus.runtime.debug.auth.enabled"
  val DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY = "textus.debug.auth.seed-account.enabled"
  val RUNTIME_DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY = "textus.runtime.debug.auth.seed-account.enabled"
  val DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY = "textus.debug.auth.auto-login.enabled"
  val RUNTIME_DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY = "textus.runtime.debug.auth.auto-login.enabled"
  val DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY = "textus.debug.auth.account.login-name"
  val RUNTIME_DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY = "textus.runtime.debug.auth.account.login-name"
  val DEBUG_AUTH_ACCOUNT_EMAIL_KEY = "textus.debug.auth.account.email"
  val RUNTIME_DEBUG_AUTH_ACCOUNT_EMAIL_KEY = "textus.runtime.debug.auth.account.email"
  val DEBUG_AUTH_ACCOUNT_PASSWORD_KEY = "textus.debug.auth.account.password"
  val RUNTIME_DEBUG_AUTH_ACCOUNT_PASSWORD_KEY = "textus.runtime.debug.auth.account.password"
  val DEBUG_AUTH_ACCOUNT_STATUS_KEY = "textus.debug.auth.account.status"
  val RUNTIME_DEBUG_AUTH_ACCOUNT_STATUS_KEY = "textus.runtime.debug.auth.account.status"
  val ExecutionHistoryRecentLimitKey = "textus.execution.history.recent-limit"
  val RuntimeExecutionHistoryRecentLimitKey = "textus.runtime.execution.history.recent-limit"
  val ExecutionHistoryFilteredLimitKey = "textus.execution.history.filtered-limit"
  val RuntimeExecutionHistoryFilteredLimitKey = "textus.runtime.execution.history.filtered-limit"
  val ExecutionHistoryFilterOperationContainsKey = "textus.execution.history.filter.operation-contains"
  val RuntimeExecutionHistoryFilterOperationContainsKey = "textus.runtime.execution.history.filter.operation-contains"
  val ObservabilityPayloadExternalizationEnabledKey = "textus.observability.payload.externalization.enabled"
  val RuntimeObservabilityPayloadExternalizationEnabledKey = "textus.runtime.observability.payload.externalization.enabled"
  val ObservabilityPayloadExternalizationDestinationKey = "textus.observability.payload.externalization.destination"
  val RuntimeObservabilityPayloadExternalizationDestinationKey = "textus.runtime.observability.payload.externalization.destination"
  val ObservabilityPayloadExternalizationLocalRootKey = "textus.observability.payload.externalization.local.root"
  val RuntimeObservabilityPayloadExternalizationLocalRootKey = "textus.runtime.observability.payload.externalization.local.root"
  val ObservabilityPayloadExternalizationThresholdBytesKey = "textus.observability.payload.externalization.threshold.bytes"
  val RuntimeObservabilityPayloadExternalizationThresholdBytesKey = "textus.runtime.observability.payload.externalization.threshold.bytes"
  val ObservabilityPayloadExternalizationPayloadsKey = "textus.observability.payload.externalization.payloads"
  val RuntimeObservabilityPayloadExternalizationPayloadsKey = "textus.runtime.observability.payload.externalization.payloads"
  val ObservabilityPayloadExternalizationOperationKey = "textus.observability.payload.externalization.operation"
  val RuntimeObservabilityPayloadExternalizationOperationKey = "textus.runtime.observability.payload.externalization.operation"
  val ObservabilityPayloadExternalizationOperationContainsKey = "textus.observability.payload.externalization.operation-contains"
  val RuntimeObservabilityPayloadExternalizationOperationContainsKey = "textus.runtime.observability.payload.externalization.operation-contains"
  val ObservabilityPayloadExternalizationAllowRequestOverrideKey = "textus.observability.payload.externalization.allow-request-override"
  val RuntimeObservabilityPayloadExternalizationAllowRequestOverrideKey = "textus.runtime.observability.payload.externalization.allow-request-override"
  val ObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey = "textus.observability.payload.externalization.unsafe-opaque-payloads"
  val RuntimeObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey = "textus.runtime.observability.payload.externalization.unsafe-opaque-payloads"
  val ObservabilityPayloadExternalizationRetentionDaysKey = "textus.observability.payload.externalization.retention.days"
  val RuntimeObservabilityPayloadExternalizationRetentionDaysKey = "textus.runtime.observability.payload.externalization.retention.days"
  val ObservabilityOtelEnabledKey = "textus.observability.otel.enabled"
  val RuntimeObservabilityOtelEnabledKey = "textus.runtime.observability.otel.enabled"
  val ObservabilityOtelEndpointKey = "textus.observability.otel.endpoint"
  val RuntimeObservabilityOtelEndpointKey = "textus.runtime.observability.otel.endpoint"
  val ObservabilityOtelProtocolKey = "textus.observability.otel.protocol"
  val RuntimeObservabilityOtelProtocolKey = "textus.runtime.observability.otel.protocol"
  val ObservabilityOtelTracesEnabledKey = "textus.observability.otel.traces.enabled"
  val RuntimeObservabilityOtelTracesEnabledKey = "textus.runtime.observability.otel.traces.enabled"
  val ObservabilityOtelMetricsEnabledKey = "textus.observability.otel.metrics.enabled"
  val RuntimeObservabilityOtelMetricsEnabledKey = "textus.runtime.observability.otel.metrics.enabled"
  val ObservabilityOtelLogsEnabledKey = "textus.observability.otel.logs.enabled"
  val RuntimeObservabilityOtelLogsEnabledKey = "textus.runtime.observability.otel.logs.enabled"
  val WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY = "textus.web.renderer.default-page-size"
  val RUNTIME_WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY = "textus.runtime.web.renderer.default-page-size"
  val WEB_RENDERER_ADMIN_PAGE_SIZE_KEY = "textus.web.renderer.admin-page-size"
  val RUNTIME_WEB_RENDERER_ADMIN_PAGE_SIZE_KEY = "textus.runtime.web.renderer.admin-page-size"
  val WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY = "textus.web.renderer.admin-filter-field-limit"
  val RUNTIME_WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY = "textus.runtime.web.renderer.admin-filter-field-limit"
  val WEB_RENDERER_PREVIEW_LIMIT_KEY = "textus.web.renderer.preview-limit"
  val RUNTIME_WEB_RENDERER_PREVIEW_LIMIT_KEY = "textus.runtime.web.renderer.preview-limit"
  val WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY = "textus.web.renderer.debug-body-preview-chars"
  val RUNTIME_WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY = "textus.runtime.web.renderer.debug-body-preview-chars"
  val WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY = "textus.web.renderer.calltree.initial-open-depth"
  val RUNTIME_WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY = "textus.runtime.web.renderer.calltree.initial-open-depth"
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
  val SiteBaseUrlKey = "textus.site.base-url"
  val RuntimeSiteBaseUrlKey = "textus.runtime.site.base-url"
  val SubsystemNameKey = "textus.subsystem"
  val ComponentNameKey = "textus.component"
  val ComponentVersionKey = "textus.component.version"
  val RuntimeComponentVersionKey = "textus.runtime.component.version"
  val ComponentDependenciesResolveEnabledKey = "textus.component.dependencies.resolve.enabled"
  val RuntimeComponentDependenciesResolveEnabledKey = "textus.runtime.component.dependencies.resolve.enabled"
  val ComponentDependenciesCacheDirKey = "textus.component.dependencies.cache.dir"
  val RuntimeComponentDependenciesCacheDirKey = "textus.runtime.component.dependencies.cache.dir"
  val ComponentDependenciesSharedEnabledKey = "textus.component.dependencies.shared.enabled"
  val RuntimeComponentDependenciesSharedEnabledKey = "textus.runtime.component.dependencies.shared.enabled"
  val ComponentDependenciesLocalOverrideEnabledKey = "textus.component.dependencies.local_override.enabled"
  val RuntimeComponentDependenciesLocalOverrideEnabledKey = "textus.runtime.component.dependencies.local_override.enabled"
  val ComponentDependenciesRepositoriesKey = "textus.component.dependencies.repositories"
  val RuntimeComponentDependenciesRepositoriesKey = "textus.runtime.component.dependencies.repositories"
  val SubsystemDescriptorKey = "textus.subsystem.descriptor"
  val SubsystemFileKey = "textus.subsystem.file"
  val SubsystemDevDirKey = "textus.subsystem.dev.dir"
  val SubsystemSarDirKey = "textus.subsystem.sar.dir"
  val RuntimeSubsystemNameKey = "textus.runtime.subsystem"
  val RuntimeComponentNameKey = "textus.runtime.component"
  val RuntimeSubsystemDescriptorKey = "textus.runtime.subsystem.descriptor"
  val RuntimeSubsystemFileKey = "textus.runtime.subsystem.file"
  val RuntimeSubsystemDevDirKey = "textus.runtime.subsystem.dev.dir"
  val RuntimeSubsystemSarDirKey = "textus.runtime.subsystem.sar.dir"
  val ComponentFileKey = "textus.component.file"
  val RuntimeComponentFileKey = "textus.runtime.component.file"
  val ComponentDevDirKey = "textus.component.dev.dir"
  val ComponentCarDirKey = "textus.component.car.dir"
  val AssemblyDescriptorKey = "textus.assembly.descriptor"
  val WebDescriptorKey = "textus.web.descriptor"
  val RepositoryDirKey = "textus.repository.dir"
  val RepositoryComponentDevDirKey = "textus.repository.component.dev.dir"
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
  val WebProductionAdminEnabledKey = "textus.web.production.admin.enabled"
  val RuntimeWebProductionAdminEnabledKey = "textus.runtime.web.production.admin.enabled"
  val WebProductionAdminSystemRolesKey = "textus.web.production.admin.system.roles"
  val RuntimeWebProductionAdminSystemRolesKey = "textus.runtime.web.production.admin.system.roles"
  val WebProductionAdminComponentRolesKey = "textus.web.production.admin.component.roles"
  val RuntimeWebProductionAdminComponentRolesKey = "textus.runtime.web.production.admin.component.roles"
  val WebProductionAdminJobsRolesKey = "textus.web.production.admin.jobs.roles"
  val RuntimeWebProductionAdminJobsRolesKey = "textus.runtime.web.production.admin.jobs.roles"
  val BlobStoreBackendKey = "textus.blob.store.backend"
  val RuntimeBlobStoreBackendKey = "textus.runtime.blob.store.backend"
  val BlobStoreNameKey = "textus.blob.store.name"
  val RuntimeBlobStoreNameKey = "textus.runtime.blob.store.name"
  val BlobStoreContainerKey = "textus.blob.store.container"
  val RuntimeBlobStoreContainerKey = "textus.runtime.blob.store.container"
  val BlobStoreLocalRootKey = "textus.blob.store.local.root"
  val RuntimeBlobStoreLocalRootKey = "textus.runtime.blob.store.local.root"
  val BlobStorePublicBasePathKey = "textus.blob.store.public-base-path"
  val RuntimeBlobStorePublicBasePathKey = "textus.runtime.blob.store.public-base-path"
  val BlobStoreProviderClassKey = "textus.blob.store.provider-class"
  val RuntimeBlobStoreProviderClassKey = "textus.runtime.blob.store.provider-class"
  val BlobMaxByteSizeKey = "textus.blob.max-byte-size"
  val RuntimeBlobMaxByteSizeKey = "textus.runtime.blob.max-byte-size"

  val DefaultServerEmulatorBaseUrl = "http://localhost/"
  val DefaultHttpDriverName = "real"
  val DefaultMode = "command"
  val DefaultOperationMode = OperationMode.Develop
  val DefaultLogFilePath = ".textus/data.d/trace.log"
  val DefaultWebOperationDispatcher = "local"
  val DefaultWebDevelopAnonymousAdmin = true
  val DefaultWebProductionAdminEnabled = false
  val DefaultWebProductionAdminSystemRoles = Vector("system_admin")
  val DefaultWebProductionAdminComponentRoles = Vector("component_operator", "system_admin")
  val DefaultWebProductionAdminJobsRoles = Vector("system_admin", "audit_viewer")
  val DEFAULT_DEBUG_AUTH_LOGIN_NAME = "test"
  val DEFAULT_DEBUG_AUTH_EMAIL = "test@example.com"
  val DEFAULT_DEBUG_AUTH_PASSWORD = "test"
  val DEFAULT_DEBUG_AUTH_STATUS = "active"
  val DefaultIdNamespace: IdGenerationContext.IdNamespace = IdGenerationContext.DefaultNamespace

  val default: RuntimeConfig =
    RuntimeConfig(
      LogBackend.NopLogBackend,
      LogLevel.Info,
      serverEmulatorBaseUrl = DefaultServerEmulatorBaseUrl,
      httpDriver = HttpDriverFactory.default,
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = new EntityStoreSpace().addEntityStore(EntityStore.standard()),
      mode = RunMode.Command,
      operationMode = DefaultOperationMode,
      webOperationDispatcher = DefaultWebOperationDispatcher,
      webOperationDispatcherRestBaseUrl = None,
      webDevelopAnonymousAdmin = DefaultWebDevelopAnonymousAdmin,
      webProductionAdminEnabled = DefaultWebProductionAdminEnabled,
      webProductionAdminSystemRoles = DefaultWebProductionAdminSystemRoles,
      webProductionAdminComponentRoles = DefaultWebProductionAdminComponentRoles,
      webProductionAdminJobsRoles = DefaultWebProductionAdminJobsRoles,
      debugAuthConfig = DebugAuthConfig(),
      commandExecutionMode = None,
      executionHistoryConfig = ObservabilityEngine.ExecutionHistoryConfig(),
      diagnosticPayloadExternalizationConfig = DiagnosticPayloadExternalizationConfig(),
      openTelemetryExportConfig = OpenTelemetryExportConfig(),
      staticFormAppRendererConfig = StaticFormAppRendererConfig.default,
      blobStoreConfig = BlobStoreConfig(),
      idNamespace = DefaultIdNamespace
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
    val diagnosticPayloadExternalizationConfig =
      _diagnostic_payload_externalization_config(configuration, operationMode)
    val openTelemetryExportConfig =
      _open_telemetry_export_config(configuration, operationMode)
    val rendererconfig =
      _static_form_app_renderer_config(configuration)
    val blobStoreConfig = BlobStoreConfig.fromConfiguration(configuration)
    val idNamespace = _id_namespace(configuration)
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
    val webProductionAdminEnabled =
      _get_boolean(configuration, WebProductionAdminEnabledKey)
        .getOrElse(DefaultWebProductionAdminEnabled)
    val webProductionAdminSystemRoles =
      _split_token_list(_get_string(configuration, WebProductionAdminSystemRolesKey))
        .filter(_.nonEmpty) match {
          case Vector() => DefaultWebProductionAdminSystemRoles
          case roles => roles
        }
    val webProductionAdminComponentRoles =
      _split_token_list(_get_string(configuration, WebProductionAdminComponentRolesKey))
        .filter(_.nonEmpty) match {
          case Vector() => DefaultWebProductionAdminComponentRoles
          case roles => roles
        }
    val webProductionAdminJobsRoles =
      _split_token_list(_get_string(configuration, WebProductionAdminJobsRolesKey))
        .filter(_.nonEmpty) match {
          case Vector() => DefaultWebProductionAdminJobsRoles
          case roles => roles
        }
    val debugauthconfig = _debug_auth_config(configuration)
    ObservabilityEngine.updateExecutionHistoryConfig(executionHistoryConfig)
    val config = RuntimeConfig(
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
      webProductionAdminEnabled = webProductionAdminEnabled,
      webProductionAdminSystemRoles = webProductionAdminSystemRoles,
      webProductionAdminComponentRoles = webProductionAdminComponentRoles,
      webProductionAdminJobsRoles = webProductionAdminJobsRoles,
      debugAuthConfig = debugauthconfig,
      commandExecutionMode = commandExecutionMode,
      executionHistoryConfig = executionHistoryConfig,
      diagnosticPayloadExternalizationConfig = diagnosticPayloadExternalizationConfig,
      openTelemetryExportConfig = openTelemetryExportConfig,
      staticFormAppRendererConfig = rendererconfig,
      blobStoreConfig = blobStoreConfig,
      idNamespace = idNamespace
    )
    _validate(config)
    config
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

  private def _validate(config: RuntimeConfig): Unit = {
    config.diagnosticPayloadExternalizationConfig.validationError.foreach { message =>
      throw new IllegalArgumentException(message)
    }
    config.openTelemetryExportConfig.validationError.foreach { message =>
      throw new IllegalArgumentException(message)
    }
    config.staticFormAppRendererConfig.validationError.foreach { message =>
      throw new IllegalArgumentException(message)
    }
    config.debugAuthConfig.validationError(config.operationMode).foreach { message =>
      throw new IllegalArgumentException(message)
    }
  }

  def parseCommandExecutionMode(
    value: String
  ): Option[CommandExecutionMode] = {
    value.trim.toLowerCase match {
      case "sync" | "sync-direct" | "sync-direct-no-job" => Some(CommandExecutionMode.Sync)
      case "job-sync" => Some(CommandExecutionMode.JobSync)
      case "job-async" => Some(CommandExecutionMode.JobAsync)
      case "job-sync-with-async-cont" | "job-sync-with-async-continuation" => Some(CommandExecutionMode.JobSyncWithAsyncCont)
      case "async" | "async-job" => Some(CommandExecutionMode.JobAsync)
      case "async-job-and-await" => Some(CommandExecutionMode.AsyncJobAndAwait)
      case "sync-job" => Some(CommandExecutionMode.JobSync)
      case "sync-job-async-interface" => Some(CommandExecutionMode.SyncJobAsyncInterface)
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

  private def _get_renderer_int(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[Int] =
    _get_string(configuration, key).map { value =>
      scala.util.Try(value.trim.toInt).getOrElse {
        throw new IllegalArgumentException(s"${key} must be an integer: ${value}")
      }
    }

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

  private def _diagnostic_payload_externalization_config(
    configuration: ResolvedConfiguration,
    operationMode: OperationMode
  ): DiagnosticPayloadExternalizationConfig =
    DiagnosticPayloadExternalizationConfig.fromValues(
      enabled = _get_boolean(configuration, ObservabilityPayloadExternalizationEnabledKey).getOrElse(false),
      destination = _get_string(configuration, ObservabilityPayloadExternalizationDestinationKey),
      localRoot = _get_string(configuration, ObservabilityPayloadExternalizationLocalRootKey),
      thresholdBytes = _get_int(configuration, ObservabilityPayloadExternalizationThresholdBytesKey),
      payloadTargets = _split_csv(_get_string(configuration, ObservabilityPayloadExternalizationPayloadsKey)),
      operationExact = _split_csv(_get_string(configuration, ObservabilityPayloadExternalizationOperationKey)),
      operationContains = _split_csv(_get_string(configuration, ObservabilityPayloadExternalizationOperationContainsKey)),
      allowRequestOverride = _get_boolean(configuration, ObservabilityPayloadExternalizationAllowRequestOverrideKey),
      unsafeOpaquePayloads = _get_boolean(configuration, ObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey),
      retentionDays = _get_int(configuration, ObservabilityPayloadExternalizationRetentionDaysKey),
      operationMode = operationMode
    )

  private def _open_telemetry_export_config(
    configuration: ResolvedConfiguration,
    operationMode: OperationMode
  ): OpenTelemetryExportConfig =
    OpenTelemetryExportConfig.fromValues(
      enabled = _get_boolean(configuration, ObservabilityOtelEnabledKey).getOrElse(false),
      endpoint = _get_string(configuration, ObservabilityOtelEndpointKey),
      protocol = _get_string(configuration, ObservabilityOtelProtocolKey),
      tracesEnabled = _get_boolean(configuration, ObservabilityOtelTracesEnabledKey),
      metricsEnabled = _get_boolean(configuration, ObservabilityOtelMetricsEnabledKey),
      logsEnabled = _get_boolean(configuration, ObservabilityOtelLogsEnabledKey),
      operationMode = operationMode
    )

  private def _static_form_app_renderer_config(
    configuration: ResolvedConfiguration
  ): StaticFormAppRendererConfig = {
    val defaults = StaticFormAppRendererConfig.default
    StaticFormAppRendererConfig(
      defaultPageSize = _get_renderer_int(configuration, WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY)
        .getOrElse(defaults.defaultPageSize),
      adminPageSize = _get_renderer_int(configuration, WEB_RENDERER_ADMIN_PAGE_SIZE_KEY)
        .getOrElse(defaults.adminPageSize),
      adminFilterFieldLimit = _get_renderer_int(configuration, WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY)
        .getOrElse(defaults.adminFilterFieldLimit),
      previewLimit = _get_renderer_int(configuration, WEB_RENDERER_PREVIEW_LIMIT_KEY)
        .getOrElse(defaults.previewLimit),
      debugBodyPreviewChars = _get_renderer_int(configuration, WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY)
        .getOrElse(defaults.debugBodyPreviewChars),
      callTreeInitialOpenDepth = _get_renderer_int(configuration, WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY)
        .getOrElse(defaults.callTreeInitialOpenDepth)
    )
  }

  private def _debug_auth_config(
    configuration: ResolvedConfiguration
  ): DebugAuthConfig =
    DebugAuthConfig(
      enabled = _get_boolean(configuration, DEBUG_AUTH_ENABLED_KEY).getOrElse(false),
      seedAccountEnabled = _get_boolean(configuration, DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY).getOrElse(false),
      autoLoginEnabled = _get_boolean(configuration, DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY).getOrElse(false),
      loginName = _get_string(configuration, DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY)
        .map(_.trim).filter(_.nonEmpty).getOrElse(DEFAULT_DEBUG_AUTH_LOGIN_NAME),
      email = _get_string(configuration, DEBUG_AUTH_ACCOUNT_EMAIL_KEY)
        .map(_.trim).filter(_.nonEmpty).getOrElse(DEFAULT_DEBUG_AUTH_EMAIL),
      password = _get_string(configuration, DEBUG_AUTH_ACCOUNT_PASSWORD_KEY)
        .map(_.trim).filter(_.nonEmpty).getOrElse(DEFAULT_DEBUG_AUTH_PASSWORD),
      status = _get_string(configuration, DEBUG_AUTH_ACCOUNT_STATUS_KEY)
        .map(_.trim).filter(_.nonEmpty).getOrElse(DEFAULT_DEBUG_AUTH_STATUS)
    )

  private def _id_namespace(
    configuration: ResolvedConfiguration
  ): IdGenerationContext.IdNamespace = {
    val major = _get_string(configuration, IdNamespaceMajorKey)
      .getOrElse(DefaultIdNamespace.major)
    val minor = _get_string(configuration, IdNamespaceMinorKey)
      .getOrElse(DefaultIdNamespace.minor)
    IdGenerationContext.IdNamespace.normalizeOrThrow(major, minor)
  }

  private def _split_csv(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _split_token_list(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty))

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
        case IdNamespaceMajorKey => Vector(RuntimeIdNamespaceMajorKey)
        case IdNamespaceMinorKey => Vector(RuntimeIdNamespaceMinorKey)
        case DebugCallTreeKey => Vector(RuntimeDebugCallTreeKey)
        case DebugTraceJobKey => Vector(RuntimeDebugTraceJobKey)
        case DebugSaveCallTreeKey => Vector(RuntimeDebugSaveCallTreeKey)
        case DEBUG_AUTH_ENABLED_KEY => Vector(RUNTIME_DEBUG_AUTH_ENABLED_KEY)
        case DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY => Vector(RUNTIME_DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY)
        case DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY => Vector(RUNTIME_DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY)
        case DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY)
        case DEBUG_AUTH_ACCOUNT_EMAIL_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_EMAIL_KEY)
        case DEBUG_AUTH_ACCOUNT_PASSWORD_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_PASSWORD_KEY)
        case DEBUG_AUTH_ACCOUNT_STATUS_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_STATUS_KEY)
        case ExecutionHistoryRecentLimitKey => Vector(RuntimeExecutionHistoryRecentLimitKey)
        case ExecutionHistoryFilteredLimitKey => Vector(RuntimeExecutionHistoryFilteredLimitKey)
        case ExecutionHistoryFilterOperationContainsKey => Vector(RuntimeExecutionHistoryFilterOperationContainsKey)
        case ObservabilityPayloadExternalizationEnabledKey => Vector(RuntimeObservabilityPayloadExternalizationEnabledKey)
        case ObservabilityPayloadExternalizationDestinationKey => Vector(RuntimeObservabilityPayloadExternalizationDestinationKey)
        case ObservabilityPayloadExternalizationLocalRootKey => Vector(RuntimeObservabilityPayloadExternalizationLocalRootKey)
        case ObservabilityPayloadExternalizationThresholdBytesKey => Vector(RuntimeObservabilityPayloadExternalizationThresholdBytesKey)
        case ObservabilityPayloadExternalizationPayloadsKey => Vector(RuntimeObservabilityPayloadExternalizationPayloadsKey)
        case ObservabilityPayloadExternalizationOperationKey => Vector(RuntimeObservabilityPayloadExternalizationOperationKey)
        case ObservabilityPayloadExternalizationOperationContainsKey => Vector(RuntimeObservabilityPayloadExternalizationOperationContainsKey)
        case ObservabilityPayloadExternalizationAllowRequestOverrideKey => Vector(RuntimeObservabilityPayloadExternalizationAllowRequestOverrideKey)
        case ObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey => Vector(RuntimeObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey)
        case ObservabilityPayloadExternalizationRetentionDaysKey => Vector(RuntimeObservabilityPayloadExternalizationRetentionDaysKey)
        case ObservabilityOtelEnabledKey => Vector(RuntimeObservabilityOtelEnabledKey)
        case ObservabilityOtelEndpointKey => Vector(RuntimeObservabilityOtelEndpointKey)
        case ObservabilityOtelProtocolKey => Vector(RuntimeObservabilityOtelProtocolKey)
        case ObservabilityOtelTracesEnabledKey => Vector(RuntimeObservabilityOtelTracesEnabledKey)
        case ObservabilityOtelMetricsEnabledKey => Vector(RuntimeObservabilityOtelMetricsEnabledKey)
        case ObservabilityOtelLogsEnabledKey => Vector(RuntimeObservabilityOtelLogsEnabledKey)
        case WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY => Vector(RUNTIME_WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY)
        case WEB_RENDERER_ADMIN_PAGE_SIZE_KEY => Vector(RUNTIME_WEB_RENDERER_ADMIN_PAGE_SIZE_KEY)
        case WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY => Vector(RUNTIME_WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY)
        case WEB_RENDERER_PREVIEW_LIMIT_KEY => Vector(RUNTIME_WEB_RENDERER_PREVIEW_LIMIT_KEY)
        case WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY => Vector(RUNTIME_WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY)
        case WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY => Vector(RUNTIME_WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY)
        case DiscoverClassesKey => Vector(RuntimeDiscoverClassesKey)
        case ComponentFactoryClassKey => Vector(RuntimeComponentFactoryClassKey)
        case WorkspaceKey => Vector(RuntimeWorkspaceKey)
        case ForceExitKey => Vector(RuntimeForceExitKey)
        case NoExitKey => Vector(RuntimeNoExitKey)
        case SiteBaseUrlKey => Vector(RuntimeSiteBaseUrlKey)
        case SubsystemNameKey => Vector(RuntimeSubsystemNameKey)
        case ComponentNameKey => Vector(RuntimeComponentNameKey)
        case ComponentVersionKey => Vector(RuntimeComponentVersionKey)
        case ComponentDependenciesResolveEnabledKey => Vector(RuntimeComponentDependenciesResolveEnabledKey)
        case ComponentDependenciesCacheDirKey => Vector(RuntimeComponentDependenciesCacheDirKey)
        case ComponentDependenciesSharedEnabledKey => Vector(RuntimeComponentDependenciesSharedEnabledKey)
        case ComponentDependenciesLocalOverrideEnabledKey => Vector(RuntimeComponentDependenciesLocalOverrideEnabledKey)
        case ComponentDependenciesRepositoriesKey => Vector(RuntimeComponentDependenciesRepositoriesKey)
        case SubsystemDescriptorKey => Vector(RuntimeSubsystemDescriptorKey)
        case SubsystemFileKey => Vector(RuntimeSubsystemFileKey)
        case SubsystemDevDirKey => Vector(RuntimeSubsystemDevDirKey)
        case SubsystemSarDirKey => Vector(RuntimeSubsystemSarDirKey)
        case ComponentFileKey => Vector(RuntimeComponentFileKey)
        case LogBackendKey => Vector(RuntimeLogBackendKey)
        case LogLevelKey => Vector(RuntimeLogLevelKey)
        case LogFilePathKey => Vector(RuntimeLogFilePathKey)
        case WebOperationDispatcherKey => Vector(RuntimeWebOperationDispatcherKey)
        case WebOperationDispatcherRestBaseUrlKey => Vector(RuntimeWebOperationDispatcherRestBaseUrlKey)
        case WebDevelopAnonymousAdminKey => Vector(RuntimeWebDevelopAnonymousAdminKey)
        case WebProductionAdminEnabledKey => Vector(RuntimeWebProductionAdminEnabledKey)
        case WebProductionAdminSystemRolesKey => Vector(RuntimeWebProductionAdminSystemRolesKey)
        case WebProductionAdminComponentRolesKey => Vector(RuntimeWebProductionAdminComponentRolesKey)
        case WebProductionAdminJobsRolesKey => Vector(RuntimeWebProductionAdminJobsRolesKey)
        case BlobStoreBackendKey => Vector(RuntimeBlobStoreBackendKey)
        case BlobStoreNameKey => Vector(RuntimeBlobStoreNameKey)
        case BlobStoreContainerKey => Vector(RuntimeBlobStoreContainerKey)
        case BlobStoreLocalRootKey => Vector(RuntimeBlobStoreLocalRootKey)
        case BlobStorePublicBasePathKey => Vector(RuntimeBlobStorePublicBasePathKey)
        case BlobStoreProviderClassKey => Vector(RuntimeBlobStoreProviderClassKey)
        case BlobMaxByteSizeKey => Vector(RuntimeBlobMaxByteSizeKey)
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

  def allowsDebugAuth: Boolean =
    this == OperationMode.Demo || this == OperationMode.Develop || this == OperationMode.Test
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
