package org.goldenport.cncf.http

import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan.  8, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpExecutionEngine(
  subsystem: Subsystem
) {
  def execute(req: HttpRequest): HttpResponse =
    subsystem.executeHttp(req)
}

object HttpExecutionEngine {
  import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}

  object Factory { // TODO
    def subsystem(): Subsystem =
      DefaultSubsystemFactory.default()

    def engine(): HttpExecutionEngine =
      new HttpExecutionEngine(subsystem())
  }
}
