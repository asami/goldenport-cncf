package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.ResolvedParameters

/** Ordinary test capture policy; explicit host ceilings remain authoritative.
 * @since   Oct. 22, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
object SbtTestOutputParameters {
  val stdoutKey: String = "cncf.sbt.test.stdout.maximum-bytes"
  val stderrKey: String = "cncf.sbt.test.stderr.maximum-bytes"
  val defaultStdoutBytes: Long = 64L * 1024L * 1024L
  val defaultStderrBytes: Long = 8L * 1024L * 1024L

  def resolveC(parameters: ResolvedParameters): Consequence[ProcessExecutionLimits] = for {
    stdout <- _bytes_c(parameters, stdoutKey, defaultStdoutBytes)
    stderr <- _bytes_c(parameters, stderrKey, defaultStderrBytes)
  } yield ProcessExecutionLimits(stdoutBytes = Some(stdout), stderrBytes = Some(stderr))

  def applyC(
    parameters: ResolvedParameters,
    operation: SbtOperation,
    hostLimits: ProcessExecutionLimits
  ): Consequence[ProcessExecutionLimits] = operation match {
    case SbtOperation.Test | SbtOperation.TestOnly =>
      resolveC(parameters).map { selected =>
        hostLimits.copy(
          stdoutBytes = hostLimits.stdoutBytes.map(_.min(selected.stdoutBytes.get)),
          stderrBytes = hostLimits.stderrBytes.map(_.min(selected.stderrBytes.get)))
      }
    case _ => Consequence.success(hostLimits)
  }

  private def _bytes_c(parameters: ResolvedParameters, key: String, defaultbytes: Long): Consequence[Long] =
    if (parameters == null) _invalid_c(key)
    else parameters.get(key) match {
      case None => Consequence.success(defaultbytes)
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.NumberValue(number)
            if number != null && number.isWhole && number.isValidLong && number > 0 =>
          Consequence.success(number.toLong)
        case _ => _invalid_c(key)
      }
    }

  private def _invalid_c(key: String): Consequence[Long] =
    Consequence.configurationInvalid(s"$key requires a positive integral numeric byte count within the Long range")
}
