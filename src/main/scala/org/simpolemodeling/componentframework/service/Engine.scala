package org.simplemodeling.componentframework.service

import scala.util.control.NonFatal
import org.simplemodeling.componentframework.*

class Engine() {
  def execute() = {
    implicit val ctx = ExecutionContext()
    try {
      // execute()
      ctx.commit()
    } catch {
      case NonFatal(e) => ctx.abort()
    } finally {
      ctx.dispose()
    }
  }
}
