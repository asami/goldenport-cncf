package org.goldenport.cncf.http

import org.goldenport.http.{HttpRequest, HttpResponse}

/*
 * @since   Jan.  9, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class HttpServer(
  engine: HttpExecutionEngine
) {
  def execute(req: HttpRequest): HttpResponse =
    engine.execute(req)
}
