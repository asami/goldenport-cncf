package org.goldenport.cncf.component

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.SystemContext
final case class RuntimeMetadataInfo(
  mode: String,
  subsystem: String,
  runtimeVersion: String,
  subsystemVersion: String
)

object RuntimeMetadata {
  val RuntimeValue: String = "goldenport-cncf"
  val SubsystemName: String = "goldenport-cncf"

  private val ModeKey = "cncf.mode"
  private val SubsystemKey = "cncf.subsystem"
  private val RuntimeVersionKey = "cncf.runtime.version"
  private val SubsystemVersionKey = "cncf.subsystem.version"

  def systemContext(
    mode: String,
    subsystem: String,
    runtimeVersion: String,
    subsystemVersion: Option[String]
  ): SystemContext = {
    SystemContext(
      configSnapshot = Map(
        ModeKey -> mode,
        SubsystemKey -> subsystem,
        RuntimeVersionKey -> runtimeVersion
      ) ++ subsystemVersion.map(SubsystemVersionKey -> _)
    )
  }

  def fromSystem(
    system: SystemContext
  ): RuntimeMetadataInfo = {
    val snapshot = system.configSnapshot
    val runtimeVersion = snapshot.getOrElse(RuntimeVersionKey, CncfVersion.current)
    RuntimeMetadataInfo(
      mode = snapshot.getOrElse(ModeKey, RuntimeConfig.default.mode.name),
      subsystem = snapshot.getOrElse(SubsystemKey, SubsystemName),
      runtimeVersion = runtimeVersion,
      subsystemVersion = snapshot.getOrElse(SubsystemVersionKey, runtimeVersion)
    )
  }

  def format(info: RuntimeMetadataInfo): String = {
    s"runtime: ${RuntimeValue}\n" +
      s"runtime.version: ${info.runtimeVersion}\n\n" +
      s"mode: ${info.mode}\n" +
      s"subsystem: ${info.subsystem}\n" +
      s"subsystem.version: ${info.subsystemVersion}"
  }
}
