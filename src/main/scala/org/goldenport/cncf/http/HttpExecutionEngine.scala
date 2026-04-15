package org.goldenport.cncf.http

import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.Consequence
import org.goldenport.protocol.{Request, Response}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan.  8, 2026
 *  version Jan.  9, 2026
 *  version Mar. 19, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpExecutionEngine(
  subsystem: Subsystem,
  webDescriptorOption: Option[WebDescriptor] = None
) {
  lazy val webDescriptor: WebDescriptor =
    webDescriptorOption.getOrElse(WebDescriptorResolver
      .resolve(subsystem)
      .toOption
      .getOrElse(WebDescriptor.empty))

  def execute(req: HttpRequest): HttpResponse =
    subsystem.executeHttp(req)

  def execute(req: Request): Consequence[Response] =
    subsystem.execute(req)

  def runtimeSubsystem: Subsystem = subsystem
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
