package org.simplemodeling.componentframework.service

import org.simplemodeling.componentframework.context.ExecutionContext

/*
 * @since   Apr. 11, 2025
 *  version Apr. 11, 2025
 * @version Dec. 21, 2025
 * @author  ASAMI, Tomoharu
 */
trait ServiceCall {
  protected def execution_context: ExecutionContext
}
