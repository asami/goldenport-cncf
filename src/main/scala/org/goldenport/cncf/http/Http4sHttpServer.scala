package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Port
import org.http4s.{HttpRoutes, MediaType, Response as HResponse, Status as HStatus}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.Charset
import org.http4s.dsl.io.*
import org.goldenport.record.Record
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}

/*
 * @since   Jan.  7, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class Http4sHttpServer(
  engine: HttpExecutionEngine,
  port: Int = 8080
) extends HttpServer(engine) {
  def start(args: Array[String] = Array.empty): Unit = {
    val _ = args
    _server().unsafeRunSync()
  }

  private def _server(): IO[Unit] = {
    val scope = ScopeContext(
      kind = ScopeKind.Subsystem,
      name = "cncf",
      parent = None,
      observabilityContext = ExecutionContext.create().observability
    )
    scope.observe_infoC(
      message = "started",
      attributes = Record.create(
        Vector(
          "kind" -> scope.kind.toString,
          "name" -> scope.name
        )
      )
    )
    val routes = HttpRoutes.of[IO] { req =>
      try {
        for {
          core <- _to_http_request(req)
          res <- _to_http_response(execute(core))
        } yield res
      } catch {
        case e: Throwable =>
          e.printStackTrace(Console.err)
          IO.pure(HResponse[IO](HStatus.InternalServerError))
      }
    }
    EmberServerBuilder
      .default[IO]
      .withPort(Port.fromInt(port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .useForever
  }

  private def _to_http_request(
    req: org.http4s.Request[IO]
  ): IO[HttpRequest] = {
    val method = req.method match {
      case org.http4s.Method.GET => HttpRequest.GET
      case org.http4s.Method.POST => HttpRequest.POST
      case org.http4s.Method.PUT => HttpRequest.PUT
      case org.http4s.Method.DELETE => HttpRequest.DELETE
      case _ => HttpRequest.GET
    }
    val query = Record.create(req.uri.query.params.toVector)
    val header = Record.create(
      req.headers.headers.map(h => h.name.toString -> h.value)
    )
    val context = HttpContext(
      scheme = req.uri.scheme.map(_.value),
      authority = req.uri.authority.map(_.renderString),
      originalUri = Some(req.uri.renderString)
    )
    IO.pure(
      HttpRequest.fromPath(
        method = method,
        path = req.uri.path.renderString,
        query = query,
        header = header,
        context = context,
        form = Record.empty
      )
    )
  }

  private def _to_http_response(
    res: HttpResponse
  ): IO[org.http4s.Response[IO]] = {
    val status = res.code match {
      case 200 => HStatus.Ok
      case 400 => HStatus.BadRequest
      case 404 => HStatus.NotFound
      case _ => HStatus.InternalServerError
    }
    val body = res.getString.getOrElse("")
    val mime = MediaType.parse(res.mime.value).fold(_ => MediaType.text.plain, identity)
    val charset: Option[org.http4s.Charset] =
      res.charset.map(c => org.http4s.Charset.fromNioCharset(c))
    val contentType = `Content-Type`(mime, charset)
    IO.pure(
      HResponse[IO](status).withEntity(body).withContentType(contentType)
    )
  }

}

object Http4sHttpServer {
  def create(): Http4sHttpServer =
    new Http4sHttpServer(
      HttpExecutionEngine.Factory.engine()
    )
}
