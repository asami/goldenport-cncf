package org.goldenport.cncf.http

import cats.effect.IO
import org.http4s.HttpApp
import org.http4s.implicits.*
import org.http4s.server.websocket.WebSocketBuilder2

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
object DisplayServiceHttpRouteFixture {
  def app(server: Http4sHttpServer): HttpApp[IO] = {
    val websocket: WebSocketBuilder2[IO] = null
    server.routes(websocket).orNotFound
  }
}
