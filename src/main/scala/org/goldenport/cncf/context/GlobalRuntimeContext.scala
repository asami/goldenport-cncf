package org.goldenport.cncf.context

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.http.HttpDriver

/*
 * @since   Jan. 17, 2026
 * @version Jan. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class GlobalRuntimeContext(
  name: String,
  observabilityContext: ObservabilityContext,
  override val httpDriver: HttpDriver,
  var runtimeMode: RunMode,
  val runtimeVersion: String,
  val subsystemName: String,
  var subsystemVersion: String
) extends ScopeContext() {
  val core = ScopeContext.Core(
    kind = ScopeKind.Runtime,
    name = name,
    parent = None,
    observabilityContext = observabilityContext,
    httpDriverOption = Some(httpDriver)
  )

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
}
