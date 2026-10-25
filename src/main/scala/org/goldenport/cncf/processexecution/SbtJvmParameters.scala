package org.goldenport.cncf.processexecution

import java.nio.file.{Files, NoSuchFileException, Path}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.ResolvedParameters

/** Runtime-owned SBT heap selection, without changing the user's configuration.
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object SbtJvmParameters {
  val maximumHeapMiBKey: String = "cncf.sbt.jvm.maximum-heap-mib"
  val defaultMaximumHeapMiB: Long = 4096L
  private val _mib = 1024L * 1024L
  private val _environment_keys = Vector("JAVA_OPTS", "SBT_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")
  private val _heap = "(?<!\\S)(?:-J)?-Xm[xs]([0-9]+)([kKmMgG]?)(?!\\S)".r
  private val _maximum_heap = "(?<!\\S)(?:-J)?-Xmx[0-9]+[kKmMgG]?(?!\\S)".r
  private val _memory = "(?<!\\S)--?mem\\s+([0-9]+)(?!\\S)".r

  def resolveC(parameters: ResolvedParameters): Consequence[Long] =
    parameters.get(maximumHeapMiBKey) match {
      case None => Consequence.success(defaultMaximumHeapMiB * _mib)
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.NumberValue(number) if number != null && number.isWhole &&
            number >= defaultMaximumHeapMiB && number <= Long.MaxValue / _mib =>
          Consequence.success(number.toLong * _mib)
        case _ => Consequence.configurationInvalid(
          s"$maximumHeapMiBKey requires an integral MiB value of at least $defaultMaximumHeapMiB")
      }
    }

  private[cncf] def applyC(
    parameters: ResolvedParameters,
    execution: ResolvedProcessExecution
  ): Consequence[ResolvedProcessExecution] = {
    val definition = execution.definition
    definition._sbt_metadata match {
      case None => Consequence.success(execution)
      case Some(metadata) => for {
        baseline <- resolveC(parameters)
        directory <- metadata.binding.workingDirectoryC
        selected <- _select_c(directory, definition._environment.values, baseline)
        (bytes, environment) = selected
        updated <- ProcessProgramDefinition.fromSbtRuntimeC(
          definition.capability, definition.safeProgramIdentity, definition._executable_location,
          (s"-J-Xmx$bytes" +: definition.fixedArguments), definition.maximumLimits,
          environment, metadata)
      } yield execution.copy(definition = updated)
    }
  }

  private def _select_c(
    directory: Path,
    environment: Map[String, String],
    baseline: Long
  ): Consequence[(Long, Map[String, String])] = try {
    val configroot = environment.get("XDG_CONFIG_HOME").filter(_.nonEmpty).map(Path.of(_))
      .orElse(environment.get("HOME").map(x => Path.of(x, ".config")))
    val userfile = configroot.map(_.resolve("sbt/sbtopts"))
    val shared = environment.get("SBT_ETC_FILE").filter(_.nonEmpty).map(Path.of(_))
      .orElse(userfile.filter(Files.exists(_))).getOrElse(Path.of("/etc/sbt/sbtopts"))
    val texts = Vector(directory.resolve(".jvmopts"), directory.resolve(".sbtopts"), shared)
      .map(_read_options) ++ _environment_keys.flatMap(environment.get)
    val requested = texts.flatMap { text =>
      _heap.findAllMatchIn(text).map { found =>
        val multiplier = found.group(2).toLowerCase match {
          case "k" => 1024L
          case "m" => _mib
          case "g" => 1024L * _mib
          case _ => 1L
        }
        Math.multiplyExact(found.group(1).toLong, multiplier)
      }.toVector ++ _memory.findAllMatchIn(text).map(x => Math.multiplyExact(x.group(1).toLong, _mib))
    }
    val bytes = requested.foldLeft(baseline)(_.max(_))
    // Some launchers append these options after CLI -J options. Normalize only
    // existing Xmx tokens; retain every other setting and inherit no ambient env.
    val updated = environment.map { case (key, value) =>
      key -> (if (_environment_keys.contains(key))
        _maximum_heap.replaceAllIn(value, _ => s"-Xmx$bytes") else value)
    }
    Consequence.success(bytes -> updated)
  } catch {
    case NonFatal(_) => Consequence.configurationInvalid("Cannot read or resolve SBT JVM memory options")
  }

  private def _read_options(path: Path): String = try {
    Files.readString(path).linesIterator.map(_.replaceFirst("(^|\\s)#.*$", "")).mkString(" ")
  } catch {
    case _: NoSuchFileException => ""
  }
}
