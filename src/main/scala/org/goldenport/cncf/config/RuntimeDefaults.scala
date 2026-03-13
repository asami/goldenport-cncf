package org.goldenport.cncf.config

import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.observability.LogLevel

/*
 * @since   Mar. 13, 2026
 * @version Mar. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object RuntimeDefaults {
  final case class ModeDefaults(
    logBackend: LogBackend,
    logLevel: LogLevel,
    format: String
  )

  private val _defaults: Map[RunMode, ModeDefaults] = Map(
    RunMode.Command -> ModeDefaults(LogBackend.NopLogBackend, LogLevel.Info, "yaml"),
    RunMode.Server -> ModeDefaults(LogBackend.StdoutBackend, LogLevel.Info, "json"),
    RunMode.Client -> ModeDefaults(LogBackend.NopLogBackend, LogLevel.Info, "yaml"),
    RunMode.ServerEmulator -> ModeDefaults(LogBackend.NopLogBackend, LogLevel.Info, "json"),
    RunMode.Script -> ModeDefaults(LogBackend.NopLogBackend, LogLevel.Info, "yaml")
  )

  def forMode(mode: RunMode): ModeDefaults =
    _defaults.getOrElse(mode, _defaults(RunMode.Command))

  def defaultLogBackend(mode: RunMode): LogBackend =
    forMode(mode).logBackend

  def defaultLogLevel(mode: RunMode): LogLevel =
    forMode(mode).logLevel

  def defaultFormat(mode: RunMode): String =
    forMode(mode).format
}
