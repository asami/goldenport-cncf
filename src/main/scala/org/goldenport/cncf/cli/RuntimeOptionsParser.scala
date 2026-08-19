package org.goldenport.cncf.cli

import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.protocol.Property

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 *  version May. 25, 2026
 *  version Jun. 29, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] object RuntimeOptionsParser {
  final case class Options(
    json: Boolean = false,
    debug: Boolean = false,
    noExit: Boolean = false,
    format: Option[String] = None,
    pathResolutionCommand: Boolean = false,
    commandExecutionMode: Option[String] = None,
    debugCalltree: Boolean = false,
    debugTraceJob: Boolean = false,
    debugSaveCalltree: Boolean = false
  )

  def extract(
    args: Seq[String]
  ): (Options, Seq[String]) = {
    val clean = Vector.newBuilder[String]
    var options = Options()
    var i = 0
    var stop = false
    while (i < args.length && !stop) {
      val current = args(i)
      if (current == "--") {
        clean ++= args.drop(i + 1)
        stop = true
      } else if (_is_property(current, "--json")) {
        options = options.copy(json = true)
      } else if (_is_property(current, "--debug")) {
        options = options.copy(debug = true)
      } else if (_is_property(current, "--debug.calltree")) {
        options = options.copy(debugCalltree = true)
      } else if (_is_property(current, "--debug.trace-job")) {
        options = options.copy(debugTraceJob = true)
      } else if (_is_property(current, "--debug.save-calltree")) {
        options = options.copy(debugSaveCalltree = true)
      } else if (current == "--no-exit") {
        options = options.copy(noExit = true)
      } else if (_is_property(current, "--format") || _is_property(current, "-format")) {
        _take_value(args, i).foreach { case (value, consumednext) =>
          options = options.copy(format = Some(value))
          if (consumednext) {
            i = i + 1
          }
        }
      } else if (_is_property(current, "--path-resolution")) {
        options = options.copy(pathResolutionCommand = true)
      } else if (_is_key_value(current)) {
        _extract_key_value(current) match {
          case Some((key, value)) if _is_format_key(key) =>
            options = options.copy(format = Some(value))
          case Some((key, value)) if _is_path_resolution_command_key(key) =>
            options = options.copy(pathResolutionCommand = _is_truthy(value))
          case Some((key, value)) if _is_command_execution_mode_key(key) =>
            options = options.copy(commandExecutionMode = Some(value))
          case Some((key, value)) if _is_debug_calltree_key(key) =>
            options = options.copy(debugCalltree = _is_truthy(value))
          case Some((key, value)) if _is_debug_trace_job_key(key) =>
            options = options.copy(debugTraceJob = _is_truthy(value))
          case Some((key, value)) if _is_debug_save_calltree_key(key) =>
            options = options.copy(debugSaveCalltree = _is_truthy(value))
          case Some((key, value)) if _is_framework_key(key) =>
            ()
          case Some((key, value)) if _is_framework_alias(key) =>
            ()
          case _ =>
            clean += current
        }
      } else if (_is_framework_key(current.drop(2))) {
        if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
          if (_is_format_key(current.drop(2))) {
            options = options.copy(format = Some(args(i + 1)))
          } else if (_is_path_resolution_command_key(current.drop(2))) {
            options = options.copy(pathResolutionCommand = _is_truthy(args(i + 1)))
          } else if (_is_command_execution_mode_key(current.drop(2))) {
            options = options.copy(commandExecutionMode = Some(args(i + 1)))
          } else if (_is_debug_calltree_key(current.drop(2))) {
            options = options.copy(debugCalltree = _is_truthy(args(i + 1)))
          } else if (_is_debug_trace_job_key(current.drop(2))) {
            options = options.copy(debugTraceJob = _is_truthy(args(i + 1)))
          } else if (_is_debug_save_calltree_key(current.drop(2))) {
            options = options.copy(debugSaveCalltree = _is_truthy(args(i + 1)))
          }
          i = i + 1
        }
      } else if (_is_framework_alias(current.drop(1))) {
        if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
          i = i + 1
        }
      } else {
        clean += current
      }
      i = i + 1
    }
    if (!stop && i >= args.length) {
      ()
    }
    (options, clean.result())
  }

  def properties(
    options: Options,
    mode: RunMode = RunMode.Command
  ): List[Property] = {
    val b = List.newBuilder[Property]
    val formatvalue = options.format
      .orElse(if (options.json) Some("json") else None)
      .orElse(Some(RuntimeDefaults.defaultFormat(mode)))
    formatvalue.foreach { value =>
      b += Property("textus.format", value, None)
    }
    if (options.debug) b += Property("textus.debug", "true", None)
    if (options.debugCalltree) b += Property(RuntimeConfig.debugCallTreeKey, "true", None)
    if (options.debugTraceJob) b += Property(RuntimeConfig.debugTraceJobKey, "true", None)
    if (options.debugSaveCalltree) b += Property(RuntimeConfig.debugSaveCallTreeKey, "true", None)
    if (options.noExit) b += Property("textus.no-exit", "true", None)
    options.commandExecutionMode.foreach { value =>
      b += Property(RuntimeConfig.commandExecutionModeKey, value, None)
    }
    b.result()
  }

  private def _is_property(
    current: String,
    key: String
  ): Boolean =
    current == key || current.startsWith(s"${key}=")

  private def _is_key_value(
    current: String
  ): Boolean =
    current.startsWith("--") && current.contains("=") ||
      (current.startsWith("-") && !current.startsWith("--") && current.contains("=")) ||
      ((current.startsWith("textus.") || current.startsWith("cncf.")) && current.contains("="))

  private def _extract_key_value(
    current: String
  ): Option[(String, String)] = {
    val raw =
      if (current.startsWith("--")) current.drop(2)
      else if (current.startsWith("-")) current.drop(1)
      else current
    val parts = raw.split("=", 2)
    if (parts.length == 2) Some(parts(0) -> parts(1)) else None
  }

  private def _take_value(
    args: Seq[String],
    index: Int
  ): Option[(String, Boolean)] =
    if (args(index).contains("=")) {
      _extract_key_value(args(index)).map(v => v._2 -> false)
    } else if (index + 1 < args.length && !args(index + 1).startsWith("-")) {
      Some(args(index + 1) -> true)
    } else {
      None
    }

  private def _is_framework_key(
    key: String
  ): Boolean =
    key.startsWith("textus.") || key.startsWith("cncf.")

  private def _is_framework_alias(
    key: String
  ): Boolean =
    key == "log-level" ||
      key == "log-backend" ||
      key == "format" ||
      key == "path-resolution" ||
      key == "textus.format" ||
      key == "textus.path-resolution" ||
      key == "cncf.format" ||
      key == "cncf.path-resolution"

  private def _is_format_key(
    key: String
  ): Boolean =
    key == "textus.format" ||
      key == "textus.output.format" ||
      key == "cncf.format" ||
      key == "cncf.output.format"

  private def _is_path_resolution_command_key(
    key: String
  ): Boolean =
    key == "textus.path-resolution.command" ||
      key == "cncf.path-resolution.command"

  private def _is_command_execution_mode_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.commandExecutionModeKey ||
      key == RuntimeConfig.runtimeCommandExecutionModeKey ||
      key == "cncf.command.execution-mode" ||
      key == "cncf.runtime.command.execution-mode" ||
      key == "runtime.command.execution-mode" ||
      key == "command.execution-mode"

  private def _is_debug_calltree_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.debugCallTreeKey ||
      key == RuntimeConfig.runtimeDebugCallTreeKey ||
      key == "cncf.debug.calltree" ||
      key == "cncf.runtime.debug.calltree"

  private def _is_debug_trace_job_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.debugTraceJobKey ||
      key == RuntimeConfig.runtimeDebugTraceJobKey ||
      key == "cncf.debug.trace-job" ||
      key == "cncf.runtime.debug.trace-job"

  private def _is_debug_save_calltree_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.debugSaveCallTreeKey ||
      key == RuntimeConfig.runtimeDebugSaveCallTreeKey ||
      key == "cncf.debug.save-calltree" ||
      key == "cncf.runtime.debug.save-calltree"

  private def _is_truthy(value: String): Boolean = {
    val normalized = value.trim.toLowerCase
    normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
  }
}
