package org.simplemodeling.componentframework.service

import scala.util.Try
import org.simplemodeling.util.StringUtils.objectToSnakeName
import org.simplemodeling.componentframework.*

/*
 * @since   Apr. 11, 2025
 * @version Apr. 14, 2025
 * @author  ASAMI, Tomoharu
 */
abstract class OperationCall[R <: Result](
  executionContext: ExecutionContext,
  val command: Command
) {
  def name: String = objectToSnakeName("OperationCall", this)

  def apply(): Try[R]
}
