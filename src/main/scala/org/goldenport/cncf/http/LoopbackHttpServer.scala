package org.goldenport.cncf.http

import org.goldenport.cncf.http.HttpExecutionEngine
import org.goldenport.http.{HttpRequest, HttpResponse}

/*
 * @since   Jan. 20, 2026
 * @version Jan. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class LoopbackHttpServer private (engine: HttpExecutionEngine) {
  def execute(req: HttpRequest): HttpResponse =
    engine.execute(req)
}

object LoopbackHttpServer {
  def create(): LoopbackHttpServer =
    new LoopbackHttpServer(HttpExecutionEngine.Factory.engine())

  def fromEngine(engine: HttpExecutionEngine): LoopbackHttpServer =
    new LoopbackHttpServer(engine)
}
