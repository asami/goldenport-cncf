package org.goldenport.cncf.context

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Jan. 17, 2026
 *  version Jan. 19, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class GlobalRuntimeContext(
  val core: ScopeContext.Core,
  val config: RuntimeConfig,
  override val httpDriver: HttpDriver,
  val aliasResolver: AliasResolver,
  var runtimeMode: RunMode,
  val runtimeVersion: String,
  val subsystemName: String,
  var subsystemVersion: String,
) extends ScopeContext() {
//   val core = ScopeContext.Core(
//     kind = ScopeKind.Runtime,
//     name = name,
//     parent = None,
//     observabilityContext = observabilityContext,
//     httpDriverOption = Some(httpDriver)
//   )
  private var _componentFactory: Option[ComponentFactory] = None

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
    observabilityContext: ObservabilityContext,
    aliasresolver: AliasResolver
  ): GlobalRuntimeContext = {
    val core = ScopeContext.Core(
      ScopeKind.Runtime, // Global
      name,
      None,
      observabilityContext,
      Some(runconfig.httpDriver)
    )
    GlobalRuntimeContext(
      core,
      runconfig,
      runconfig.httpDriver,
      aliasresolver,
      runconfig.mode,
      runtimeVersion = CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = CncfVersion.current
    )
  }
}
