package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.{HttpRoutes, MediaType, Response as HResponse, Status as HStatus}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.Charset
import org.http4s.syntax.all.*
import org.http4s.dsl.io.*
import org.http4s.multipart.Multipart
import org.goldenport.record.Record
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody}

/*
 * @since   Jan.  7, 2026
 * @version Jan. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class Http4sHttpServer(
  engine: HttpExecutionEngine,
  port: Int = 8080
) extends HttpServer(engine) {
  private val _bind_host = Host.fromString("0.0.0.0").get

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
      .withHost(_bind_host)
      .withPort(Port.fromInt(port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .use { _ =>
        // Block forever to keep server mode alive.
        IO.never
      }
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
    val contentTypeHeader = req.headers.get[`Content-Type`]

    if (_is_multipart(contentTypeHeader))
      _to_multipart_http_request(req, method, query, header, context)
    else
      _to_regular_http_request(req, method, query, header, context)
  }

  private def _is_multipart(contentType: Option[`Content-Type`]): Boolean =
    contentType.exists { header =>
      header.mediaType.mainType.equalsIgnoreCase("multipart") &&
        header.mediaType.subType.equalsIgnoreCase("form-data")
    }

  private def _to_regular_http_request(
    req: org.http4s.Request[IO],
    method: HttpRequest.Method,
    query: Record,
    header: Record,
    context: HttpContext
  ): IO[HttpRequest] =
    req.body.compile.to(Array).map { bytes =>
      val bodyOption =
        if (bytes.isEmpty) None
        else Some(Bag.binary(bytes.toArray))
      HttpRequest.fromPath(
        method = method,
        path = req.uri.path.renderString,
        query = query,
        header = header,
        body = bodyOption,
        context = context,
        form = Record.empty
      )
    }

  private def _to_multipart_http_request(
    req: org.http4s.Request[IO],
    method: HttpRequest.Method,
    query: Record,
    header: Record,
    context: HttpContext
  ): IO[HttpRequest] =
    req.as[Multipart[IO]].flatMap { multipart =>
      multipart.parts.toVector.traverse { part =>
        part.name match {
          case Some(name) if name.nonEmpty =>
            part.body.compile.to(Array).map { bytes =>
              val bag = Bag.binary(bytes.toArray)
              val contentType =
                part.headers
                  .get[`Content-Type`]
                  .map(_.value)
                  .map(ContentType.parse)
                  .getOrElse(ContentType.OCTET_STREAM)
              Some(name -> MimeBody(contentType, bag))
            }
          case _ =>
            IO.pure(None)
        }
      }.map { entries =>
        val values = entries.flatten
        val form =
          if (values.isEmpty) Record.empty else Record.create(values)
        HttpRequest.fromPath(
          method = method,
          path = req.uri.path.renderString,
          query = query,
          header = header,
          body = None,
          context = context,
          form = form
        )
      }
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
