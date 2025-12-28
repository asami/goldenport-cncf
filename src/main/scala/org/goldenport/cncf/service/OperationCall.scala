package org.goldenport.cncf.service

import scala.util.Try
import org.goldenport.util.StringUtils.objectToSnakeName
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.Action
import org.goldenport.cncf.security.SecuredResource

/*
 * @since   Apr. 11, 2025
 *  version Apr. 14, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
trait OperationCall[R <: Result] {
  def name: String = objectToSnakeName("OperationCall", this)

  def executionContext: ExecutionContext

  def accesses: Seq[ResourceAccess]

  def apply(): R
}

// TEMPORARY (to be removed after demo)
final case class ResourceAccess(
  resource: SecuredResource,
  action: Action
)
