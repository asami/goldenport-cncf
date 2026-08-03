package org.goldenport.cncf.config

import java.nio.file.{Path, Paths}

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
import org.goldenport.cncf.context.{ExecutionProfileResolver, IdGenerationContext, ResolvedExecutionProfile, RuntimeClock}
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, ObservabilityEngine, OpenTelemetryExportConfig}
import org.goldenport.cncf.blob.BlobStoreConfig
import org.goldenport.cncf.resource.{ResourceTreePolicy, ResourceTreeQueryLimits, ResourceUrlPolicy, TextusUrnResourcePolicy, UrnResourceProvider, UrnResourceProviderConfig}

/*
 * @since   Jan. 18, 2026
 *  version Jan. 30, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 *  version Apr. 30, 2026
 *  version Jun. 19, 2026
 * @version Jul. 30, 2026
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
  operationMode: OperationMode = RuntimeConfig.defaultOperationMode,
  webOperationDispatcher: String = RuntimeConfig.defaultWebOperationDispatcher,
  webOperationDispatcherRestBaseUrl: Option[String] = None,
  webDevelopAnonymousAdmin: Boolean = RuntimeConfig.defaultWebDevelopAnonymousAdmin,
  webDemoAssistEnabled: Boolean = RuntimeConfig.DEFAULT_WEB_DEMO_ASSIST_ENABLED,
  webProductionAdminEnabled: Boolean = RuntimeConfig.defaultWebProductionAdminEnabled,
  webProductionAdminSystemRoles: Vector[String] = RuntimeConfig.defaultWebProductionAdminSystemRoles,
  webProductionAdminComponentRoles: Vector[String] = RuntimeConfig.defaultWebProductionAdminComponentRoles,
  webProductionAdminJobsRoles: Vector[String] = RuntimeConfig.defaultWebProductionAdminJobsRoles,
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
  idNamespace: IdGenerationContext.IdNamespace = IdGenerationContext.DEFAULT_NAMESPACE,
  executionProfile: ResolvedExecutionProfile = RuntimeConfig.DEFAULT_EXECUTION_PROFILE,
  resourceUrlPolicy: ResourceUrlPolicy = ResourceUrlPolicy(),
  textusUrnResourcePolicy: TextusUrnResourcePolicy = TextusUrnResourcePolicy(),
  urnResourceProviders: Vector[UrnResourceProvider] = Vector.empty,
  resourceTreePolicy: ResourceTreePolicy = ResourceTreePolicy(),
  mcpClientPolicyPath: Option[Path] = None,
  operationToolPolicyPath: Option[Path] = None
) {
  def executionClock: RuntimeClock = executionProfile.runtimeClock
}

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

  val serverEmulatorBaseUrlKey = "textus.server-emulator.baseurl"
  val runtimeServerEmulatorBaseUrlKey = "textus.runtime.server-emulator.baseurl"
  val httpDriverKey = "textus.http.driver"
  val runtimeHttpDriverKey = "textus.runtime.http.driver"
  val modeKey = "textus.mode"
  val runtimeModeKey = "textus.runtime.mode"
  val operationModeKey = "textus.operation-mode"
  val runtimeOperationModeKey = "textus.runtime.operation-mode"
  val commandExecutionModeKey = "textus.command.execution-mode"
  val runtimeCommandExecutionModeKey = "textus.runtime.command.execution-mode"
  val CLOCK_VIRTUAL_START_AT_KEY = "textus.clock.virtual-start-at"
  val RUNTIME_CLOCK_VIRTUAL_START_AT_KEY = "textus.runtime.clock.virtual-start-at"
  val EXECUTION_PROFILE_KEY = "textus.execution.profile"
  val RUNTIME_EXECUTION_PROFILE_KEY = "textus.runtime.execution.profile"
  val EXECUTION_KEY = "textus.execution.key"
  val RUNTIME_EXECUTION_KEY = "textus.runtime.execution.key"
  val EXECUTION_TIME_MODE_KEY = "textus.execution.time.mode"
  val RUNTIME_EXECUTION_TIME_MODE_KEY = "textus.runtime.execution.time.mode"
  val EXECUTION_TIME_START_AT_KEY = "textus.execution.time.start-at"
  val RUNTIME_EXECUTION_TIME_START_AT_KEY = "textus.runtime.execution.time.start-at"
  val EXECUTION_RANDOM_MODE_KEY = "textus.execution.random.mode"
  val RUNTIME_EXECUTION_RANDOM_MODE_KEY = "textus.runtime.execution.random.mode"
  val EXECUTION_RANDOM_SEED_KEY = "textus.execution.random.seed"
  val RUNTIME_EXECUTION_RANDOM_SEED_KEY = "textus.runtime.execution.random.seed"
  val EXECUTION_IDS_MODE_KEY = "textus.execution.ids.mode"
  val RUNTIME_EXECUTION_IDS_MODE_KEY = "textus.runtime.execution.ids.mode"
  val EXECUTION_SCHEDULER_MODE_KEY = "textus.execution.scheduler.mode"
  val RUNTIME_EXECUTION_SCHEDULER_MODE_KEY = "textus.runtime.execution.scheduler.mode"
  val EXECUTION_ORDERING_MODE_KEY = "textus.execution.ordering.mode"
  val RUNTIME_EXECUTION_ORDERING_MODE_KEY = "textus.runtime.execution.ordering.mode"
  val EXECUTION_LOCALE_KEY = "textus.execution.locale"
  val RUNTIME_EXECUTION_LOCALE_KEY = "textus.runtime.execution.locale"
  val EXECUTION_TIMEZONE_KEY = "textus.execution.timezone"
  val RUNTIME_EXECUTION_TIMEZONE_KEY = "textus.runtime.execution.timezone"
  val EXECUTION_CHARSET_KEY = "textus.execution.charset"
  val RUNTIME_EXECUTION_CHARSET_KEY = "textus.runtime.execution.charset"
  val EXECUTION_LINE_SEPARATOR_KEY = "textus.execution.line-separator"
  val RUNTIME_EXECUTION_LINE_SEPARATOR_KEY = "textus.runtime.execution.line-separator"
  val EXECUTION_MATH_CONTEXT_KEY = "textus.execution.math-context"
  val RUNTIME_EXECUTION_MATH_CONTEXT_KEY = "textus.runtime.execution.math-context"
  val EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY = "textus.execution.i18n.text-normalization-policy"
  val RUNTIME_EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY = "textus.runtime.execution.i18n.text-normalization-policy"
  val EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY = "textus.execution.i18n.text-comparison-policy"
  val RUNTIME_EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY = "textus.runtime.execution.i18n.text-comparison-policy"
  val EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY = "textus.execution.i18n.date-time-format-policy"
  val RUNTIME_EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY = "textus.runtime.execution.i18n.date-time-format-policy"
  val EXECUTION_ENVIRONMENT_ALLOW_KEY = "textus.execution.environment.allow"
  val RUNTIME_EXECUTION_ENVIRONMENT_ALLOW_KEY = "textus.runtime.execution.environment.allow"
  val EXECUTION_ENVIRONMENT_VALUES_KEY = "textus.execution.environment.values"
  val RUNTIME_EXECUTION_ENVIRONMENT_VALUES_KEY = "textus.runtime.execution.environment.values"
  val idNamespaceMajorKey = "textus.id.namespace.major"
  val runtimeIdNamespaceMajorKey = "textus.runtime.id.namespace.major"
  val idNamespaceMinorKey = "textus.id.namespace.minor"
  val runtimeIdNamespaceMinorKey = "textus.runtime.id.namespace.minor"
  val debugCallTreeKey = "textus.debug.calltree"
  val runtimeDebugCallTreeKey = "textus.runtime.debug.calltree"
  val debugTraceJobKey = "textus.debug.trace-job"
  val runtimeDebugTraceJobKey = "textus.runtime.debug.trace-job"
  val debugSaveCallTreeKey = "textus.debug.save-calltree"
  val runtimeDebugSaveCallTreeKey = "textus.runtime.debug.save-calltree"
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
  val executionHistoryRecentLimitKey = "textus.execution.history.recent-limit"
  val runtimeExecutionHistoryRecentLimitKey = "textus.runtime.execution.history.recent-limit"
  val executionHistoryFilteredLimitKey = "textus.execution.history.filtered-limit"
  val runtimeExecutionHistoryFilteredLimitKey = "textus.runtime.execution.history.filtered-limit"
  val executionHistoryFilterOperationContainsKey = "textus.execution.history.filter.operation-contains"
  val runtimeExecutionHistoryFilterOperationContainsKey = "textus.runtime.execution.history.filter.operation-contains"
  val observabilityPayloadExternalizationEnabledKey = "textus.observability.payload.externalization.enabled"
  val runtimeObservabilityPayloadExternalizationEnabledKey = "textus.runtime.observability.payload.externalization.enabled"
  val observabilityPayloadExternalizationDestinationKey = "textus.observability.payload.externalization.destination"
  val runtimeObservabilityPayloadExternalizationDestinationKey = "textus.runtime.observability.payload.externalization.destination"
  val observabilityPayloadExternalizationLocalRootKey = "textus.observability.payload.externalization.local.root"
  val runtimeObservabilityPayloadExternalizationLocalRootKey = "textus.runtime.observability.payload.externalization.local.root"
  val observabilityPayloadExternalizationThresholdBytesKey = "textus.observability.payload.externalization.threshold.bytes"
  val runtimeObservabilityPayloadExternalizationThresholdBytesKey = "textus.runtime.observability.payload.externalization.threshold.bytes"
  val observabilityPayloadExternalizationPayloadsKey = "textus.observability.payload.externalization.payloads"
  val runtimeObservabilityPayloadExternalizationPayloadsKey = "textus.runtime.observability.payload.externalization.payloads"
  val observabilityPayloadExternalizationOperationKey = "textus.observability.payload.externalization.operation"
  val runtimeObservabilityPayloadExternalizationOperationKey = "textus.runtime.observability.payload.externalization.operation"
  val observabilityPayloadExternalizationOperationContainsKey = "textus.observability.payload.externalization.operation-contains"
  val runtimeObservabilityPayloadExternalizationOperationContainsKey = "textus.runtime.observability.payload.externalization.operation-contains"
  val observabilityPayloadExternalizationAllowRequestOverrideKey = "textus.observability.payload.externalization.allow-request-override"
  val runtimeObservabilityPayloadExternalizationAllowRequestOverrideKey = "textus.runtime.observability.payload.externalization.allow-request-override"
  val observabilityPayloadExternalizationUnsafeOpaquePayloadsKey = "textus.observability.payload.externalization.unsafe-opaque-payloads"
  val runtimeObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey = "textus.runtime.observability.payload.externalization.unsafe-opaque-payloads"
  val observabilityPayloadExternalizationRetentionDaysKey = "textus.observability.payload.externalization.retention.days"
  val runtimeObservabilityPayloadExternalizationRetentionDaysKey = "textus.runtime.observability.payload.externalization.retention.days"
  val observabilityOtelEnabledKey = "textus.observability.otel.enabled"
  val runtimeObservabilityOtelEnabledKey = "textus.runtime.observability.otel.enabled"
  val observabilityOtelEndpointKey = "textus.observability.otel.endpoint"
  val runtimeObservabilityOtelEndpointKey = "textus.runtime.observability.otel.endpoint"
  val observabilityOtelProtocolKey = "textus.observability.otel.protocol"
  val runtimeObservabilityOtelProtocolKey = "textus.runtime.observability.otel.protocol"
  val observabilityOtelTracesEnabledKey = "textus.observability.otel.traces.enabled"
  val runtimeObservabilityOtelTracesEnabledKey = "textus.runtime.observability.otel.traces.enabled"
  val observabilityOtelMetricsEnabledKey = "textus.observability.otel.metrics.enabled"
  val runtimeObservabilityOtelMetricsEnabledKey = "textus.runtime.observability.otel.metrics.enabled"
  val observabilityOtelLogsEnabledKey = "textus.observability.otel.logs.enabled"
  val runtimeObservabilityOtelLogsEnabledKey = "textus.runtime.observability.otel.logs.enabled"
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
  val discoverClassesKey = "textus.discover.classes"
  val runtimeDiscoverClassesKey = "textus.runtime.discover.classes"
  val componentFactoryClassKey = "textus.component.factory-class"
  val runtimeComponentFactoryClassKey = "textus.runtime.component-factory-class"
  val workspaceKey = "textus.workspace"
  val runtimeWorkspaceKey = "textus.runtime.workspace"
  val forceExitKey = "textus.force-exit"
  val runtimeForceExitKey = "textus.runtime.force-exit"
  val noExitKey = "textus.no-exit"
  val runtimeNoExitKey = "textus.runtime.no-exit"
  val siteBaseUrlKey = "textus.site.base-url"
  val runtimeSiteBaseUrlKey = "textus.runtime.site.base-url"
  val subsystemNameKey = "textus.subsystem"
  val componentNameKey = "textus.component"
  val componentVersionKey = "textus.component.version"
  val runtimeComponentVersionKey = "textus.runtime.component.version"
  val componentDependenciesResolveEnabledKey = "textus.component.dependencies.resolve.enabled"
  val runtimeComponentDependenciesResolveEnabledKey = "textus.runtime.component.dependencies.resolve.enabled"
  val componentDependenciesCacheDirKey = "textus.component.dependencies.cache.dir"
  val runtimeComponentDependenciesCacheDirKey = "textus.runtime.component.dependencies.cache.dir"
  val componentDependenciesSharedEnabledKey = "textus.component.dependencies.shared.enabled"
  val runtimeComponentDependenciesSharedEnabledKey = "textus.runtime.component.dependencies.shared.enabled"
  val componentDependenciesLocalOverrideEnabledKey = "textus.component.dependencies.local_override.enabled"
  val runtimeComponentDependenciesLocalOverrideEnabledKey = "textus.runtime.component.dependencies.local_override.enabled"
  val componentDependenciesRepositoriesKey = "textus.component.dependencies.repositories"
  val runtimeComponentDependenciesRepositoriesKey = "textus.runtime.component.dependencies.repositories"
  val subsystemDescriptorKey = "textus.subsystem.descriptor"
  val subsystemFileKey = "textus.subsystem.file"
  val subsystemDevDirKey = "textus.subsystem.dev.dir"
  val subsystemSarDirKey = "textus.subsystem.sar.dir"
  val runtimeSubsystemNameKey = "textus.runtime.subsystem"
  val runtimeComponentNameKey = "textus.runtime.component"
  val runtimeSubsystemDescriptorKey = "textus.runtime.subsystem.descriptor"
  val runtimeSubsystemFileKey = "textus.runtime.subsystem.file"
  val runtimeSubsystemDevDirKey = "textus.runtime.subsystem.dev.dir"
  val runtimeSubsystemSarDirKey = "textus.runtime.subsystem.sar.dir"
  val componentFileKey = "textus.component.file"
  val runtimeComponentFileKey = "textus.runtime.component.file"
  val MCP_CLIENT_POLICY_KEY = "textus.mcp.client.policy"
  val RUNTIME_MCP_CLIENT_POLICY_KEY = "textus.runtime.mcp.client.policy"
  val OPERATION_TOOL_POLICY_KEY = "textus.operation-tools.policy"
  val RUNTIME_OPERATION_TOOL_POLICY_KEY = "textus.runtime.operation-tools.policy"
  val componentDevDirKey = "textus.component.dev.dir"
  val componentCarDirKey = "textus.component.car.dir"
  val assemblyDescriptorKey = "textus.assembly.descriptor"
  val TEST_DESCRIPTOR_KEY = "textus.test.descriptor"
  val RUNTIME_TEST_DESCRIPTOR_KEY = "textus.runtime.test.descriptor"
  val TEST_HOME_MODE_KEY = "textus.test.home.mode"
  val RUNTIME_TEST_HOME_MODE_KEY = "textus.runtime.test.home.mode"
  val TEST_HOME_PATH_KEY = "textus.test.home.path"
  val RUNTIME_TEST_HOME_PATH_KEY = "textus.runtime.test.home.path"
  val TEST_HOME_TEMPORARY_KEY = "textus.test.home.temporary"
  val RUNTIME_TEST_HOME_TEMPORARY_KEY = "textus.runtime.test.home.temporary"
  val TEST_HOME_INHERIT_RUNTIME_KEY = "textus.test.home.inherit.runtime"
  val RUNTIME_TEST_HOME_INHERIT_RUNTIME_KEY = "textus.runtime.test.home.inherit.runtime"
  val TEST_HOME_INHERIT_REPOSITORIES_KEY = "textus.test.home.inherit.repositories"
  val RUNTIME_TEST_HOME_INHERIT_REPOSITORIES_KEY = "textus.runtime.test.home.inherit.repositories"
  val TEST_HOME_INHERIT_CREDENTIALS_KEY = "textus.test.home.inherit.credentials"
  val RUNTIME_TEST_HOME_INHERIT_CREDENTIALS_KEY = "textus.runtime.test.home.inherit.credentials"
  val TEST_HOME_INHERIT_LOCAL_DATA_KEY = "textus.test.home.inherit.local-data"
  val RUNTIME_TEST_HOME_INHERIT_LOCAL_DATA_KEY = "textus.runtime.test.home.inherit.local-data"
  val webDescriptorKey = "textus.web.descriptor"
  val repositoryDirKey = "textus.repository.dir"
  val repositoryComponentDevDirKey = "textus.repository.component.dev.dir"
  val componentDirKey = "textus.component.dir"
  val logBackendKey = "textus.logging.backend"
  val runtimeLogBackendKey = "textus.runtime.logging.backend"
  val logLevelKey = "textus.logging.level"
  val runtimeLogLevelKey = "textus.runtime.logging.level"
  val logFilePathKey = "textus.logging.file.path"
  val runtimeLogFilePathKey = "textus.runtime.logging.file.path"
  val webOperationDispatcherKey = "textus.web.operation.dispatcher"
  val runtimeWebOperationDispatcherKey = "textus.runtime.web.operation.dispatcher"
  val webOperationDispatcherRestBaseUrlKey = "textus.web.operation.dispatcher.rest.base-url"
  val runtimeWebOperationDispatcherRestBaseUrlKey = "textus.runtime.web.operation.dispatcher.rest.base-url"
  val webDevelopAnonymousAdminKey = "textus.web.develop.anonymous-admin"
  val runtimeWebDevelopAnonymousAdminKey = "textus.runtime.web.develop.anonymous-admin"
  val WEB_DEMO_ASSIST_ENABLED_KEY = "textus.web.demo-assist.enabled"
  val RUNTIME_WEB_DEMO_ASSIST_ENABLED_KEY = "textus.runtime.web.demo-assist.enabled"
  val webProductionAdminEnabledKey = "textus.web.production.admin.enabled"
  val runtimeWebProductionAdminEnabledKey = "textus.runtime.web.production.admin.enabled"
  val webProductionAdminSystemRolesKey = "textus.web.production.admin.system.roles"
  val runtimeWebProductionAdminSystemRolesKey = "textus.runtime.web.production.admin.system.roles"
  val webProductionAdminComponentRolesKey = "textus.web.production.admin.component.roles"
  val runtimeWebProductionAdminComponentRolesKey = "textus.runtime.web.production.admin.component.roles"
  val webProductionAdminJobsRolesKey = "textus.web.production.admin.jobs.roles"
  val runtimeWebProductionAdminJobsRolesKey = "textus.runtime.web.production.admin.jobs.roles"
  val blobStoreBackendKey = "textus.blob.store.backend"
  val runtimeBlobStoreBackendKey = "textus.runtime.blob.store.backend"
  val blobStoreNameKey = "textus.blob.store.name"
  val runtimeBlobStoreNameKey = "textus.runtime.blob.store.name"
  val blobStoreContainerKey = "textus.blob.store.container"
  val runtimeBlobStoreContainerKey = "textus.runtime.blob.store.container"
  val blobStoreLocalRootKey = "textus.blob.store.local.root"
  val runtimeBlobStoreLocalRootKey = "textus.runtime.blob.store.local.root"
  val blobStorePublicBasePathKey = "textus.blob.store.public-base-path"
  val runtimeBlobStorePublicBasePathKey = "textus.runtime.blob.store.public-base-path"
  val blobStoreProviderClassKey = "textus.blob.store.provider-class"
  val runtimeBlobStoreProviderClassKey = "textus.runtime.blob.store.provider-class"
  val blobMaxByteSizeKey = "textus.blob.max-byte-size"
  val runtimeBlobMaxByteSizeKey = "textus.runtime.blob.max-byte-size"
  val resourceUrlFileRootsKey = "textus.resource.url.file.roots"
  val runtimeResourceUrlFileRootsKey = "textus.runtime.resource.url.file.roots"
  val resourceUrlHttpsHostsKey = "textus.resource.url.https.hosts"
  val runtimeResourceUrlHttpsHostsKey = "textus.runtime.resource.url.https.hosts"
  val resourceTextusUrnFileRootsKey = "textus.resource.urn.textus.file-roots"
  val runtimeResourceTextusUrnFileRootsKey = "textus.runtime.resource.urn.textus.file-roots"
  val resourceUrnProvidersKey = "textus.resource.urn.providers"
  val runtimeResourceUrnProvidersKey = "textus.runtime.resource.urn.providers"
  val resourceTreeFileRootsKey = "textus.resource.tree.file-roots"
  val runtimeResourceTreeFileRootsKey = "textus.runtime.resource.tree.file-roots"
  val RESOURCE_TREE_QUERY_MAX_DEPTH_KEY = "textus.resource.tree.query.max-depth"
  val RUNTIME_RESOURCE_TREE_QUERY_MAX_DEPTH_KEY = "textus.runtime.resource.tree.query.max-depth"
  val RESOURCE_TREE_QUERY_MAX_VISITED_DIRECTORIES_KEY = "textus.resource.tree.query.max-visited-directories"
  val RUNTIME_RESOURCE_TREE_QUERY_MAX_VISITED_DIRECTORIES_KEY = "textus.runtime.resource.tree.query.max-visited-directories"
  val RESOURCE_TREE_QUERY_MAX_ENTRIES_KEY = "textus.resource.tree.query.max-entries"
  val RUNTIME_RESOURCE_TREE_QUERY_MAX_ENTRIES_KEY = "textus.runtime.resource.tree.query.max-entries"
  val RESOURCE_TREE_QUERY_MAX_ENTRY_BYTES_KEY = "textus.resource.tree.query.max-entry-bytes"
  val RUNTIME_RESOURCE_TREE_QUERY_MAX_ENTRY_BYTES_KEY = "textus.runtime.resource.tree.query.max-entry-bytes"
  val RESOURCE_TREE_QUERY_MAX_TOTAL_BYTES_KEY = "textus.resource.tree.query.max-total-bytes"
  val RUNTIME_RESOURCE_TREE_QUERY_MAX_TOTAL_BYTES_KEY = "textus.runtime.resource.tree.query.max-total-bytes"

  val defaultServerEmulatorBaseUrl = "http://localhost/"
  val defaultHttpDriverName = "real"
  val defaultMode = "command"
  val defaultOperationMode = OperationMode.Develop
  val defaultLogFilePath = ".textus/data.d/trace.log"
  val defaultWebOperationDispatcher = "local"
  val defaultWebDevelopAnonymousAdmin = true
  val DEFAULT_WEB_DEMO_ASSIST_ENABLED = false
  val defaultWebProductionAdminEnabled = false
  val defaultWebProductionAdminSystemRoles = Vector("system_admin")
  val defaultWebProductionAdminComponentRoles = Vector("component_operator", "system_admin")
  val defaultWebProductionAdminJobsRoles = Vector("system_admin", "audit_viewer")
  val DEFAULT_DEBUG_AUTH_LOGIN_NAME = "test"
  val DEFAULT_DEBUG_AUTH_EMAIL = "test@example.com"
  val DEFAULT_DEBUG_AUTH_PASSWORD = "test"
  val DEFAULT_DEBUG_AUTH_STATUS = "active"
  val DEFAULT_ID_NAMESPACE: IdGenerationContext.IdNamespace = IdGenerationContext.DEFAULT_NAMESPACE
  val DEFAULT_EXECUTION_PROFILE: ResolvedExecutionProfile = ExecutionProfileResolver.standard
  val DEFAULT_EXECUTION_CLOCK: RuntimeClock = DEFAULT_EXECUTION_PROFILE.runtimeClock

  val default: RuntimeConfig =
    RuntimeConfig(
      LogBackend.NopLogBackend,
      LogLevel.Info,
      serverEmulatorBaseUrl = defaultServerEmulatorBaseUrl,
      httpDriver = HttpDriverFactory.default,
      dataStoreSpace = DataStoreSpace.default(),
      entityStoreSpace = new EntityStoreSpace().addEntityStore(EntityStore.standard()),
      mode = RunMode.Command,
      operationMode = defaultOperationMode,
      webOperationDispatcher = defaultWebOperationDispatcher,
      webOperationDispatcherRestBaseUrl = None,
      webDevelopAnonymousAdmin = defaultWebDevelopAnonymousAdmin,
      webDemoAssistEnabled = DEFAULT_WEB_DEMO_ASSIST_ENABLED,
      webProductionAdminEnabled = defaultWebProductionAdminEnabled,
      webProductionAdminSystemRoles = defaultWebProductionAdminSystemRoles,
      webProductionAdminComponentRoles = defaultWebProductionAdminComponentRoles,
      webProductionAdminJobsRoles = defaultWebProductionAdminJobsRoles,
      debugAuthConfig = DebugAuthConfig(),
      commandExecutionMode = None,
      executionHistoryConfig = ObservabilityEngine.ExecutionHistoryConfig(),
      diagnosticPayloadExternalizationConfig = DiagnosticPayloadExternalizationConfig(),
      openTelemetryExportConfig = OpenTelemetryExportConfig(),
      staticFormAppRendererConfig = StaticFormAppRendererConfig.default,
      blobStoreConfig = BlobStoreConfig(),
      idNamespace = DEFAULT_ID_NAMESPACE,
      executionProfile = DEFAULT_EXECUTION_PROFILE,
      resourceUrlPolicy = ResourceUrlPolicy(),
      textusUrnResourcePolicy = TextusUrnResourcePolicy(),
      urnResourceProviders = Vector.empty,
      resourceTreePolicy = ResourceTreePolicy(),
      mcpClientPolicyPath = None
    )

  def from(
    configuration: ResolvedConfiguration,
    modeOverride: Option[RunMode] = None
  ): RuntimeConfig =
    _from(configuration, modeOverride, None)

  def from(
    configuration: ResolvedConfiguration,
    modeOverride: Option[RunMode],
    executionProfile: ResolvedExecutionProfile
  ): RuntimeConfig =
    if (executionProfile == null)
      throw new IllegalArgumentException("runtime execution profile is required")
    else
      _from(configuration, modeOverride, Some(executionProfile))

  private def _from(
    configuration: ResolvedConfiguration,
    modeoverride: Option[RunMode],
    profileoverride: Option[ResolvedExecutionProfile]
  ): RuntimeConfig = {
    val baseurl =
      _get_string(configuration, serverEmulatorBaseUrlKey)
        .getOrElse(defaultServerEmulatorBaseUrl)
    val httpdriver = {
      val a = _get_string(configuration, httpDriverKey)
        .getOrElse(defaultHttpDriverName)
      HttpDriverFactory.create(a, baseurl) match {
        case Consequence.Success(driver) =>
          driver
        case Consequence.Failure(conclusion) =>
//          _print_error(conclusion) TODO
          FakeHttpDriver.okText("nop")
      }
    }
    val modename =
      _get_string(configuration, modeKey)
        .getOrElse(defaultMode)
    val mode =
      modeoverride.orElse(RunMode.from(modename)).getOrElse(RunMode.Command)
    val operationmode = _operation_mode(configuration)
    val commandexecutionmode =
      _get_string(configuration, commandExecutionModeKey)
        .flatMap(parseCommandExecutionMode)
    val logbackend: LogBackend = {
      val name = _get_string(configuration, logBackendKey)
      val logfile =
        _get_string(configuration, logFilePathKey).
          getOrElse(defaultLogFilePath)
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
      val name = _get_string(configuration, logLevelKey)
      name match {
        case Some(s) => LogLevel.from(s) getOrElse LogLevel.Warn
        case None => RuntimeDefaults.defaultLogLevel(mode)
      }
    }
    val datastorespace = DataStoreSpace.create(configuration)
    val entitystorespace = EntityStoreSpace.create(configuration)
    val executionhistoryconfig = _execution_history_config(configuration)
    val diagnosticpayloadexternalizationconfig =
      _diagnostic_payload_externalization_config(configuration, operationmode)
    val opentelemetryexportconfig =
      _open_telemetry_export_config(configuration, operationmode)
    val rendererconfig =
      _static_form_app_renderer_config(configuration)
    val blobstoreconfig = BlobStoreConfig.fromConfiguration(configuration)
    val resourceurlpolicy = _resource_url_policy(configuration)
    val textusurnresourcepolicy = _textus_urn_resource_policy(configuration)
    val urnresourceproviders = _urn_resource_providers(configuration)
    val resourcetreepolicy = _resource_tree_policy(configuration)
    val mcpclientpolicypath = _get_string(configuration, MCP_CLIENT_POLICY_KEY)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_).toAbsolutePath.normalize())
    val operationtoolpolicypath = _get_string(configuration, OPERATION_TOOL_POLICY_KEY)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_).toAbsolutePath.normalize())
    val idnamespace = _id_namespace(configuration)
    val executionprofile = profileoverride.getOrElse(_execution_profile(configuration, operationmode))
    val weboperationdispatcher =
      _get_string(configuration, webOperationDispatcherKey)
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .getOrElse(defaultWebOperationDispatcher)
    val weboperationdispatcherrestbaseurl =
      _get_string(configuration, webOperationDispatcherRestBaseUrlKey)
    val webdevelopanonymousadmin =
      _get_boolean(configuration, webDevelopAnonymousAdminKey)
        .getOrElse(defaultWebDevelopAnonymousAdmin)
    val webdemoassistenabled =
      _get_boolean(configuration, WEB_DEMO_ASSIST_ENABLED_KEY)
        .getOrElse(DEFAULT_WEB_DEMO_ASSIST_ENABLED)
    val webproductionadminenabled =
      _get_boolean(configuration, webProductionAdminEnabledKey)
        .getOrElse(defaultWebProductionAdminEnabled)
    val webproductionadminsystemroles =
      _split_token_list(_get_string(configuration, webProductionAdminSystemRolesKey))
        .filter(_.nonEmpty) match {
          case Vector() => defaultWebProductionAdminSystemRoles
          case roles => roles
        }
    val webproductionadmincomponentroles =
      _split_token_list(_get_string(configuration, webProductionAdminComponentRolesKey))
        .filter(_.nonEmpty) match {
          case Vector() => defaultWebProductionAdminComponentRoles
          case roles => roles
        }
    val webproductionadminjobsroles =
      _split_token_list(_get_string(configuration, webProductionAdminJobsRolesKey))
        .filter(_.nonEmpty) match {
          case Vector() => defaultWebProductionAdminJobsRoles
          case roles => roles
        }
    val debugauthconfig = _debug_auth_config(configuration)
    ObservabilityEngine.updateExecutionHistoryConfig(executionhistoryconfig)
    val config = RuntimeConfig(
      logbackend,
      loglevel,
      serverEmulatorBaseUrl = baseurl,
      httpDriver = httpdriver,
      dataStoreSpace = datastorespace,
      entityStoreSpace = entitystorespace,
      mode = mode,
      operationMode = operationmode,
      webOperationDispatcher = weboperationdispatcher,
      webOperationDispatcherRestBaseUrl = weboperationdispatcherrestbaseurl,
      webDevelopAnonymousAdmin = webdevelopanonymousadmin,
      webDemoAssistEnabled = webdemoassistenabled,
      webProductionAdminEnabled = webproductionadminenabled,
      webProductionAdminSystemRoles = webproductionadminsystemroles,
      webProductionAdminComponentRoles = webproductionadmincomponentroles,
      webProductionAdminJobsRoles = webproductionadminjobsroles,
      debugAuthConfig = debugauthconfig,
      commandExecutionMode = commandexecutionmode,
      executionHistoryConfig = executionhistoryconfig,
      diagnosticPayloadExternalizationConfig = diagnosticpayloadexternalizationconfig,
      openTelemetryExportConfig = opentelemetryexportconfig,
      staticFormAppRendererConfig = rendererconfig,
      blobStoreConfig = blobstoreconfig,
      idNamespace = idnamespace,
      executionProfile = executionprofile,
      resourceUrlPolicy = resourceurlpolicy,
      textusUrnResourcePolicy = textusurnresourcepolicy,
      urnResourceProviders = urnresourceproviders,
      resourceTreePolicy = resourcetreepolicy,
      mcpClientPolicyPath = mcpclientpolicypath,
      operationToolPolicyPath = operationtoolpolicypath
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

  def create(conf: ResolvedConfiguration): Consequence[RuntimeConfig] =
    ExecutionProfileResolver.resolve(conf, _operation_mode(conf)).flatMap { profile =>
      Consequence(_from(conf, None, Some(profile)))
    }

  private def _operation_mode(configuration: ResolvedConfiguration): OperationMode =
    _get_string(configuration, operationModeKey)
      .flatMap(OperationMode.from)
      .getOrElse(defaultOperationMode)

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
    val recentlimit =
      _get_int(configuration, executionHistoryRecentLimitKey).getOrElse(defaults.recentLimit)
    val filteredlimit =
      _get_int(configuration, executionHistoryFilteredLimitKey).getOrElse(defaults.filteredLimit)
    val filters =
      _split_csv(_get_string(configuration, executionHistoryFilterOperationContainsKey))
        .map(x => ObservabilityEngine.ExecutionHistoryFilter(operationContains = Some(x)))
    defaults.copy(
      recentLimit = math.max(0, recentlimit),
      filteredLimit = math.max(0, filteredlimit),
      filters = filters
    )
  }

  private def _diagnostic_payload_externalization_config(
    configuration: ResolvedConfiguration,
    operationmode: OperationMode
  ): DiagnosticPayloadExternalizationConfig =
    DiagnosticPayloadExternalizationConfig.fromValues(
      enabled = _get_boolean(configuration, observabilityPayloadExternalizationEnabledKey).getOrElse(false),
      destination = _get_string(configuration, observabilityPayloadExternalizationDestinationKey),
      localRoot = _get_string(configuration, observabilityPayloadExternalizationLocalRootKey),
      thresholdBytes = _get_int(configuration, observabilityPayloadExternalizationThresholdBytesKey),
      payloadTargets = _split_csv(_get_string(configuration, observabilityPayloadExternalizationPayloadsKey)),
      operationExact = _split_csv(_get_string(configuration, observabilityPayloadExternalizationOperationKey)),
      operationContains = _split_csv(_get_string(configuration, observabilityPayloadExternalizationOperationContainsKey)),
      allowRequestOverride = _get_boolean(configuration, observabilityPayloadExternalizationAllowRequestOverrideKey),
      unsafeOpaquePayloads = _get_boolean(configuration, observabilityPayloadExternalizationUnsafeOpaquePayloadsKey),
      retentionDays = _get_int(configuration, observabilityPayloadExternalizationRetentionDaysKey),
      operationMode = operationmode
    )

  private def _open_telemetry_export_config(
    configuration: ResolvedConfiguration,
    operationmode: OperationMode
  ): OpenTelemetryExportConfig =
    OpenTelemetryExportConfig.fromValues(
      enabled = _get_boolean(configuration, observabilityOtelEnabledKey).getOrElse(false),
      endpoint = _get_string(configuration, observabilityOtelEndpointKey),
      protocol = _get_string(configuration, observabilityOtelProtocolKey),
      tracesEnabled = _get_boolean(configuration, observabilityOtelTracesEnabledKey),
      metricsEnabled = _get_boolean(configuration, observabilityOtelMetricsEnabledKey),
      logsEnabled = _get_boolean(configuration, observabilityOtelLogsEnabledKey),
      operationMode = operationmode
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
    val major = _get_string(configuration, idNamespaceMajorKey)
      .getOrElse(DEFAULT_ID_NAMESPACE.major)
    val minor = _get_string(configuration, idNamespaceMinorKey)
      .getOrElse(DEFAULT_ID_NAMESPACE.minor)
    IdGenerationContext.IdNamespace.normalizeOrThrow(major, minor)
  }

  private def _resource_url_policy(
    configuration: ResolvedConfiguration
  ): ResourceUrlPolicy =
    ResourceUrlPolicy.fromValuesC(
      fileroots = _split_csv(_get_string(configuration, resourceUrlFileRootsKey)),
      httpshosts = _split_csv(_get_string(configuration, resourceUrlHttpsHostsKey))
    ) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalArgumentException(conclusion.display))
    }

  private def _textus_urn_resource_policy(
    configuration: ResolvedConfiguration
  ): TextusUrnResourcePolicy =
    TextusUrnResourcePolicy.fromValuesC(
      _split_csv(_get_string(configuration, resourceTextusUrnFileRootsKey))
    ) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalArgumentException(conclusion.display))
    }

  private def _urn_resource_providers(
    configuration: ResolvedConfiguration
  ): Vector[UrnResourceProvider] =
    UrnResourceProviderConfig.fromValuesC(
      _split_csv(_get_string(configuration, resourceUrnProvidersKey))
    ) match {
      case Consequence.Success(value) => value.providers
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalArgumentException(conclusion.display))
    }

  private def _resource_tree_policy(
    configuration: ResolvedConfiguration
  ): ResourceTreePolicy =
    ResourceTreePolicy.fromValuesC(
      _split_csv(_get_string(configuration, resourceTreeFileRootsKey)),
      _resource_tree_query_limits(configuration)
    ) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalArgumentException(conclusion.display))
    }

  private def _resource_tree_query_limits(
    configuration: ResolvedConfiguration
  ): ResourceTreeQueryLimits = {
    val defaults = ResourceTreeQueryLimits.default
    ResourceTreeQueryLimits(
      maxDepth = _resource_tree_query_int(configuration, RESOURCE_TREE_QUERY_MAX_DEPTH_KEY, defaults.maxDepth),
      maxVisitedDirectories = _resource_tree_query_int(
        configuration,
        RESOURCE_TREE_QUERY_MAX_VISITED_DIRECTORIES_KEY,
        defaults.maxVisitedDirectories
      ),
      maxEntries = _resource_tree_query_int(configuration, RESOURCE_TREE_QUERY_MAX_ENTRIES_KEY, defaults.maxEntries),
      maxEntryBytes = _resource_tree_query_long(configuration, RESOURCE_TREE_QUERY_MAX_ENTRY_BYTES_KEY, defaults.maxEntryBytes),
      maxTotalBytes = _resource_tree_query_long(configuration, RESOURCE_TREE_QUERY_MAX_TOTAL_BYTES_KEY, defaults.maxTotalBytes)
    )
  }

  private def _resource_tree_query_int(
    configuration: ResolvedConfiguration,
    key: String,
    default: Int
  ): Int =
    _get_string(configuration, key).fold(default) { value =>
      scala.util.Try(value.trim.toInt).toOption.filter(_ >= 0).getOrElse {
        throw new IllegalArgumentException(s"${key} must be a non-negative integer: ${value}")
      }
    }

  private def _resource_tree_query_long(
    configuration: ResolvedConfiguration,
    key: String,
    default: Long
  ): Long =
    _get_string(configuration, key).fold(default) { value =>
      scala.util.Try(value.trim.toLong).toOption.filter(_ >= 0L).getOrElse {
        throw new IllegalArgumentException(s"${key} must be a non-negative integer: ${value}")
      }
    }

  private def _execution_profile(
    configuration: ResolvedConfiguration,
    operationmode: OperationMode
  ): ResolvedExecutionProfile =
    ExecutionProfileResolver.resolve(configuration, operationmode) match {
      case Consequence.Success(profile) => profile
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalArgumentException(conclusion.display))
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
    val textusruntime =
      key match {
        case `serverEmulatorBaseUrlKey` => Vector(runtimeServerEmulatorBaseUrlKey)
        case `httpDriverKey` => Vector(runtimeHttpDriverKey)
        case `modeKey` => Vector(runtimeModeKey)
        case `operationModeKey` => Vector(runtimeOperationModeKey)
        case `commandExecutionModeKey` => Vector(runtimeCommandExecutionModeKey)
        case CLOCK_VIRTUAL_START_AT_KEY => Vector(RUNTIME_CLOCK_VIRTUAL_START_AT_KEY)
        case EXECUTION_PROFILE_KEY => Vector(RUNTIME_EXECUTION_PROFILE_KEY)
        case EXECUTION_KEY => Vector(RUNTIME_EXECUTION_KEY)
        case EXECUTION_TIME_MODE_KEY => Vector(RUNTIME_EXECUTION_TIME_MODE_KEY)
        case EXECUTION_TIME_START_AT_KEY => Vector(RUNTIME_EXECUTION_TIME_START_AT_KEY)
        case EXECUTION_RANDOM_MODE_KEY => Vector(RUNTIME_EXECUTION_RANDOM_MODE_KEY)
        case EXECUTION_RANDOM_SEED_KEY => Vector(RUNTIME_EXECUTION_RANDOM_SEED_KEY)
        case EXECUTION_IDS_MODE_KEY => Vector(RUNTIME_EXECUTION_IDS_MODE_KEY)
        case EXECUTION_SCHEDULER_MODE_KEY => Vector(RUNTIME_EXECUTION_SCHEDULER_MODE_KEY)
        case EXECUTION_ORDERING_MODE_KEY => Vector(RUNTIME_EXECUTION_ORDERING_MODE_KEY)
        case EXECUTION_LOCALE_KEY => Vector(RUNTIME_EXECUTION_LOCALE_KEY)
        case EXECUTION_TIMEZONE_KEY => Vector(RUNTIME_EXECUTION_TIMEZONE_KEY)
        case EXECUTION_CHARSET_KEY => Vector(RUNTIME_EXECUTION_CHARSET_KEY)
        case EXECUTION_LINE_SEPARATOR_KEY => Vector(RUNTIME_EXECUTION_LINE_SEPARATOR_KEY)
        case EXECUTION_MATH_CONTEXT_KEY => Vector(RUNTIME_EXECUTION_MATH_CONTEXT_KEY)
        case EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY => Vector(RUNTIME_EXECUTION_I18N_TEXT_NORMALIZATION_POLICY_KEY)
        case EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY => Vector(RUNTIME_EXECUTION_I18N_TEXT_COMPARISON_POLICY_KEY)
        case EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY => Vector(RUNTIME_EXECUTION_I18N_DATE_TIME_FORMAT_POLICY_KEY)
        case EXECUTION_ENVIRONMENT_ALLOW_KEY => Vector(RUNTIME_EXECUTION_ENVIRONMENT_ALLOW_KEY)
        case EXECUTION_ENVIRONMENT_VALUES_KEY => Vector(RUNTIME_EXECUTION_ENVIRONMENT_VALUES_KEY)
        case `idNamespaceMajorKey` => Vector(runtimeIdNamespaceMajorKey)
        case `idNamespaceMinorKey` => Vector(runtimeIdNamespaceMinorKey)
        case `debugCallTreeKey` => Vector(runtimeDebugCallTreeKey)
        case `debugTraceJobKey` => Vector(runtimeDebugTraceJobKey)
        case `debugSaveCallTreeKey` => Vector(runtimeDebugSaveCallTreeKey)
        case DEBUG_AUTH_ENABLED_KEY => Vector(RUNTIME_DEBUG_AUTH_ENABLED_KEY)
        case DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY => Vector(RUNTIME_DEBUG_AUTH_SEED_ACCOUNT_ENABLED_KEY)
        case DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY => Vector(RUNTIME_DEBUG_AUTH_AUTO_LOGIN_ENABLED_KEY)
        case DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_LOGIN_NAME_KEY)
        case DEBUG_AUTH_ACCOUNT_EMAIL_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_EMAIL_KEY)
        case DEBUG_AUTH_ACCOUNT_PASSWORD_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_PASSWORD_KEY)
        case DEBUG_AUTH_ACCOUNT_STATUS_KEY => Vector(RUNTIME_DEBUG_AUTH_ACCOUNT_STATUS_KEY)
        case `executionHistoryRecentLimitKey` => Vector(runtimeExecutionHistoryRecentLimitKey)
        case `executionHistoryFilteredLimitKey` => Vector(runtimeExecutionHistoryFilteredLimitKey)
        case `executionHistoryFilterOperationContainsKey` => Vector(runtimeExecutionHistoryFilterOperationContainsKey)
        case `observabilityPayloadExternalizationEnabledKey` => Vector(runtimeObservabilityPayloadExternalizationEnabledKey)
        case `observabilityPayloadExternalizationDestinationKey` => Vector(runtimeObservabilityPayloadExternalizationDestinationKey)
        case `observabilityPayloadExternalizationLocalRootKey` => Vector(runtimeObservabilityPayloadExternalizationLocalRootKey)
        case `observabilityPayloadExternalizationThresholdBytesKey` => Vector(runtimeObservabilityPayloadExternalizationThresholdBytesKey)
        case `observabilityPayloadExternalizationPayloadsKey` => Vector(runtimeObservabilityPayloadExternalizationPayloadsKey)
        case `observabilityPayloadExternalizationOperationKey` => Vector(runtimeObservabilityPayloadExternalizationOperationKey)
        case `observabilityPayloadExternalizationOperationContainsKey` => Vector(runtimeObservabilityPayloadExternalizationOperationContainsKey)
        case `observabilityPayloadExternalizationAllowRequestOverrideKey` => Vector(runtimeObservabilityPayloadExternalizationAllowRequestOverrideKey)
        case `observabilityPayloadExternalizationUnsafeOpaquePayloadsKey` => Vector(runtimeObservabilityPayloadExternalizationUnsafeOpaquePayloadsKey)
        case `observabilityPayloadExternalizationRetentionDaysKey` => Vector(runtimeObservabilityPayloadExternalizationRetentionDaysKey)
        case `observabilityOtelEnabledKey` => Vector(runtimeObservabilityOtelEnabledKey)
        case `observabilityOtelEndpointKey` => Vector(runtimeObservabilityOtelEndpointKey)
        case `observabilityOtelProtocolKey` => Vector(runtimeObservabilityOtelProtocolKey)
        case `observabilityOtelTracesEnabledKey` => Vector(runtimeObservabilityOtelTracesEnabledKey)
        case `observabilityOtelMetricsEnabledKey` => Vector(runtimeObservabilityOtelMetricsEnabledKey)
        case `observabilityOtelLogsEnabledKey` => Vector(runtimeObservabilityOtelLogsEnabledKey)
        case WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY => Vector(RUNTIME_WEB_RENDERER_DEFAULT_PAGE_SIZE_KEY)
        case WEB_RENDERER_ADMIN_PAGE_SIZE_KEY => Vector(RUNTIME_WEB_RENDERER_ADMIN_PAGE_SIZE_KEY)
        case WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY => Vector(RUNTIME_WEB_RENDERER_ADMIN_FILTER_FIELD_LIMIT_KEY)
        case WEB_RENDERER_PREVIEW_LIMIT_KEY => Vector(RUNTIME_WEB_RENDERER_PREVIEW_LIMIT_KEY)
        case WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY => Vector(RUNTIME_WEB_RENDERER_DEBUG_BODY_PREVIEW_CHARS_KEY)
        case WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY => Vector(RUNTIME_WEB_RENDERER_CALLTREE_INITIAL_OPEN_DEPTH_KEY)
        case `discoverClassesKey` => Vector(runtimeDiscoverClassesKey)
        case `componentFactoryClassKey` => Vector(runtimeComponentFactoryClassKey)
        case `workspaceKey` => Vector(runtimeWorkspaceKey)
        case `forceExitKey` => Vector(runtimeForceExitKey)
        case `noExitKey` => Vector(runtimeNoExitKey)
        case `siteBaseUrlKey` => Vector(runtimeSiteBaseUrlKey)
        case `subsystemNameKey` => Vector(runtimeSubsystemNameKey)
        case `componentNameKey` => Vector(runtimeComponentNameKey)
        case `componentVersionKey` => Vector(runtimeComponentVersionKey)
        case `componentDependenciesResolveEnabledKey` => Vector(runtimeComponentDependenciesResolveEnabledKey)
        case `componentDependenciesCacheDirKey` => Vector(runtimeComponentDependenciesCacheDirKey)
        case `componentDependenciesSharedEnabledKey` => Vector(runtimeComponentDependenciesSharedEnabledKey)
        case `componentDependenciesLocalOverrideEnabledKey` => Vector(runtimeComponentDependenciesLocalOverrideEnabledKey)
        case `componentDependenciesRepositoriesKey` => Vector(runtimeComponentDependenciesRepositoriesKey)
        case `subsystemDescriptorKey` => Vector(runtimeSubsystemDescriptorKey)
        case `subsystemFileKey` => Vector(runtimeSubsystemFileKey)
        case `subsystemDevDirKey` => Vector(runtimeSubsystemDevDirKey)
        case `subsystemSarDirKey` => Vector(runtimeSubsystemSarDirKey)
        case `componentFileKey` => Vector(runtimeComponentFileKey)
        case MCP_CLIENT_POLICY_KEY => Vector(RUNTIME_MCP_CLIENT_POLICY_KEY)
        case OPERATION_TOOL_POLICY_KEY => Vector(RUNTIME_OPERATION_TOOL_POLICY_KEY)
        case TEST_DESCRIPTOR_KEY => Vector(RUNTIME_TEST_DESCRIPTOR_KEY)
        case TEST_HOME_MODE_KEY => Vector(RUNTIME_TEST_HOME_MODE_KEY)
        case TEST_HOME_PATH_KEY => Vector(RUNTIME_TEST_HOME_PATH_KEY)
        case TEST_HOME_TEMPORARY_KEY => Vector(RUNTIME_TEST_HOME_TEMPORARY_KEY)
        case TEST_HOME_INHERIT_RUNTIME_KEY => Vector(RUNTIME_TEST_HOME_INHERIT_RUNTIME_KEY)
        case TEST_HOME_INHERIT_REPOSITORIES_KEY => Vector(RUNTIME_TEST_HOME_INHERIT_REPOSITORIES_KEY)
        case TEST_HOME_INHERIT_CREDENTIALS_KEY => Vector(RUNTIME_TEST_HOME_INHERIT_CREDENTIALS_KEY)
        case TEST_HOME_INHERIT_LOCAL_DATA_KEY => Vector(RUNTIME_TEST_HOME_INHERIT_LOCAL_DATA_KEY)
        case `logBackendKey` => Vector(runtimeLogBackendKey)
        case `logLevelKey` => Vector(runtimeLogLevelKey)
        case `logFilePathKey` => Vector(runtimeLogFilePathKey)
        case `webOperationDispatcherKey` => Vector(runtimeWebOperationDispatcherKey)
        case `webOperationDispatcherRestBaseUrlKey` => Vector(runtimeWebOperationDispatcherRestBaseUrlKey)
        case `webDevelopAnonymousAdminKey` => Vector(runtimeWebDevelopAnonymousAdminKey)
        case WEB_DEMO_ASSIST_ENABLED_KEY => Vector(RUNTIME_WEB_DEMO_ASSIST_ENABLED_KEY)
        case `webProductionAdminEnabledKey` => Vector(runtimeWebProductionAdminEnabledKey)
        case `webProductionAdminSystemRolesKey` => Vector(runtimeWebProductionAdminSystemRolesKey)
        case `webProductionAdminComponentRolesKey` => Vector(runtimeWebProductionAdminComponentRolesKey)
        case `webProductionAdminJobsRolesKey` => Vector(runtimeWebProductionAdminJobsRolesKey)
        case `blobStoreBackendKey` => Vector(runtimeBlobStoreBackendKey)
        case `blobStoreNameKey` => Vector(runtimeBlobStoreNameKey)
        case `blobStoreContainerKey` => Vector(runtimeBlobStoreContainerKey)
        case `blobStoreLocalRootKey` => Vector(runtimeBlobStoreLocalRootKey)
        case `blobStorePublicBasePathKey` => Vector(runtimeBlobStorePublicBasePathKey)
        case `blobStoreProviderClassKey` => Vector(runtimeBlobStoreProviderClassKey)
        case `blobMaxByteSizeKey` => Vector(runtimeBlobMaxByteSizeKey)
        case `resourceUrlFileRootsKey` => Vector(runtimeResourceUrlFileRootsKey)
        case `resourceUrlHttpsHostsKey` => Vector(runtimeResourceUrlHttpsHostsKey)
        case `resourceTextusUrnFileRootsKey` => Vector(runtimeResourceTextusUrnFileRootsKey)
        case `resourceUrnProvidersKey` => Vector(runtimeResourceUrnProvidersKey)
        case `resourceTreeFileRootsKey` => Vector(runtimeResourceTreeFileRootsKey)
        case RESOURCE_TREE_QUERY_MAX_DEPTH_KEY => Vector(RUNTIME_RESOURCE_TREE_QUERY_MAX_DEPTH_KEY)
        case RESOURCE_TREE_QUERY_MAX_VISITED_DIRECTORIES_KEY => Vector(RUNTIME_RESOURCE_TREE_QUERY_MAX_VISITED_DIRECTORIES_KEY)
        case RESOURCE_TREE_QUERY_MAX_ENTRIES_KEY => Vector(RUNTIME_RESOURCE_TREE_QUERY_MAX_ENTRIES_KEY)
        case RESOURCE_TREE_QUERY_MAX_ENTRY_BYTES_KEY => Vector(RUNTIME_RESOURCE_TREE_QUERY_MAX_ENTRY_BYTES_KEY)
        case RESOURCE_TREE_QUERY_MAX_TOTAL_BYTES_KEY => Vector(RUNTIME_RESOURCE_TREE_QUERY_MAX_TOTAL_BYTES_KEY)
        case _ => Vector.empty
      }
    val cncfaliases =
      (key +: textusruntime).collect {
        case k if k.startsWith("textus.") => "cncf." + k.stripPrefix("textus.")
      }
    textusruntime ++ cncfaliases
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
