package org.goldenport.cncf.context

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.action.CommandExecutionMode
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}

/*
 * @since   Jan. 17, 2026
 *  version Jan. 19, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class GlobalRuntimeContext(
  val core: ScopeContext.Core,
  val config: RuntimeConfig,
//  override val httpDriver: HttpDriver,
  val aliasResolver: AliasResolver,
  var runtimeMode: RunMode,
  var commandExecutionMode: Option[CommandExecutionMode],
  val runtimeVersion: String,
  val subsystemName: String,
  var subsystemVersion: String,
  val resolvedConfiguration: ResolvedConfiguration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
) extends ScopeContext() {
//   val core = ScopeContext.Core(
//     kind = ScopeKind.Runtime,
//     name = name,
//     parent = None,
//     observabilityContext = observabilityContext,
//     httpDriverOption = Some(httpDriver)
//   )
  private var _componentFactory: Option[ComponentFactory] = None
  val assemblyReport: AssemblyReport = new AssemblyReport()

  lazy val resolvedParameters: ResolvedParameters =
    ResolvedParameters.fromResolvedConfiguration(resolvedConfiguration)

  def serverEmulatorBaseUrl: String = config.serverEmulatorBaseUrl

  override def formatPing: String =
    GlobalRuntimeContext.formatPingValue(
      runtimeMode,
      subsystemName,
      subsystemVersion,
      runtimeVersion
    )

  def updateRuntimeMode(mode: RunMode): Unit =
    runtimeMode = mode

  def updateCommandExecutionMode(mode: Option[CommandExecutionMode]): Unit =
    commandExecutionMode = mode

  def updateSubsystemVersion(version: String): Unit =
    subsystemVersion = version

  def componentFactory: Option[ComponentFactory] = _componentFactory

  def updateComponentFactory(factory: ComponentFactory): Unit =
    _componentFactory = Some(factory)
}

object GlobalRuntimeContext {
  final val RuntimeValue = "goldenport-cncf"
  final val SubsystemName = "goldenport-cncf"

  private val _defaultRuntimeVersion: String = CncfVersion.current

  var current: Option[GlobalRuntimeContext] = None

  def formatPingValue(
    mode: RunMode,
    subsystemName: String,
    subsystemVersion: String,
    runtimeVersion: String
  ): String =
    s"runtime: ${RuntimeValue}\n" +
      s"runtime.version: ${runtimeVersion}\n\n" +
      s"mode: ${mode.name}\n" +
      s"subsystem: ${subsystemName}\n" +
      s"subsystem.version: ${subsystemVersion}"

  def defaultPing: String =
    formatPingValue(
      mode = RunMode.Command,
      subsystemName = SubsystemName,
      subsystemVersion = _defaultRuntimeVersion,
      runtimeVersion = _defaultRuntimeVersion
    )

  def create(
    name: String,
    runconfig: RuntimeConfig,
    resolvedConfiguration: ResolvedConfiguration,
    observabilityContext: ObservabilityContext,
    aliasresolver: AliasResolver
  ): GlobalRuntimeContext = {
    val core = ScopeContext.Core(
      ScopeKind.Runtime, // Global
      name,
      None,
      observabilityContext,
      Some(runconfig.httpDriver),
      Some(DataStoreContext(runconfig.dataStoreSpace)),
      Some(EntityStoreContext(runconfig.entityStoreSpace))
    )
    GlobalRuntimeContext(
      core,
      runconfig,
//      runconfig.httpDriver,
      aliasresolver,
      runconfig.mode,
      commandExecutionMode = runconfig.commandExecutionMode,
      runtimeVersion = CncfVersion.current,
      subsystemName = _subsystem_name(resolvedConfiguration),
      subsystemVersion = CncfVersion.current,
      resolvedConfiguration = resolvedConfiguration
    )
  }

  private def _subsystem_name(
    resolvedConfiguration: ResolvedConfiguration
  ): String =
    ConfigurationAccess
      .getString(resolvedConfiguration, RuntimeConfig.SubsystemNameKey)
      .getOrElse(SubsystemName)
}
