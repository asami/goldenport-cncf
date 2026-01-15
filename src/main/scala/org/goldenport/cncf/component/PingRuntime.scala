package org.goldenport.cncf.component

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.context.SystemContext

/*
 * @since   Jan. 13, 2026
 * @version Jan. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class PingRuntimeInfo(
  mode: String,
  subsystem: String,
  runtimeVersion: String,
  subsystemVersion: String
)

object PingRuntime {
  val RuntimeValue: String = "goldenport-cncf"

  private val _mode_key = "cncf.mode"
  private val _subsystem_key = "cncf.subsystem"
  private val _runtime_version_key = "cncf.runtime.version"
  private val _subsystem_version_key = "cncf.subsystem.version"

  private val _default_mode = "command"
  private val _default_subsystem = "goldenport-cncf"

  def systemContext(
    mode: String,
    subsystem: String,
    runtimeVersion: String,
    subsystemVersion: Option[String]
  ): SystemContext = {
    SystemContext(
      configSnapshot = Map(
        _mode_key -> mode,
        _subsystem_key -> subsystem,
        _runtime_version_key -> runtimeVersion
      ) ++ subsystemVersion.map(_subsystem_version_key -> _)
    )
  }

  def fromSystem(
    system: SystemContext
  ): PingRuntimeInfo = {
    val snapshot = system.configSnapshot
    val runtimeVersion = snapshot.getOrElse(_runtime_version_key, CncfVersion.current)
    PingRuntimeInfo(
      mode = snapshot.getOrElse(_mode_key, _default_mode),
      subsystem = snapshot.getOrElse(_subsystem_key, _default_subsystem),
      runtimeVersion = runtimeVersion,
      subsystemVersion = snapshot.getOrElse(_subsystem_version_key, runtimeVersion)
    )
  }

  def format(info: PingRuntimeInfo): String = {
    s"runtime: ${RuntimeValue}\n" +
      s"runtime.version: ${info.runtimeVersion}\n\n" +
      s"mode: ${info.mode}\n" +
      s"subsystem: ${info.subsystem}\n" +
      s"subsystem.version: ${info.subsystemVersion}"
  }
}
