package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext

/** Common bounded diff policy resolved from ordinary CNCF parameters.
 * @since   Oct. 22, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object GitDiffParameters {
  val key: String = "cncf.git.diff.maximum-bytes"
  val defaultBytes: Long = 1048576L
  val maximumBytes: Long = 16777216L

  def resolveC(context: ExecutionContext): Consequence[Long] =
    if (context == null) _invalid_c else resolveC(context.resolvedParameters)

  def resolveC(parameters: ResolvedParameters): Consequence[Long] =
    if (parameters == null) _invalid_c
    else parameters.get(key) match {
      case None => Consequence.success(defaultBytes)
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.NumberValue(number)
            if number != null && number.isWhole && number.isValidLong && number >= 1 && number <= maximumBytes =>
          Consequence.success(number.toLong)
        case _ => _invalid_c
      }
    }

  private def _invalid_c: Consequence[Long] =
    Consequence.configurationInvalid(s"$key requires an integral numeric byte count from 1 through $maximumBytes")
}
