package org.goldenport.cncf.http

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.Port
import org.http4s.{HttpRoutes, MediaType, Response as HResponse, Status as HStatus}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.dsl.io.*
import org.goldenport.record.Record
import org.goldenport.http.HttpRequest
import org.goldenport.http.HttpResponse
import org.goldenport.cncf.subsystem.HelloWorldSubsystemFactory

object HelloWorldHttpServer extends IOApp.Simple {
  private val _subsystem = HelloWorldSubsystemFactory.helloWorld()

  def run: IO[Unit] = {
    val routes = HttpRoutes.of[IO] { req =>
      for {
        core <- _to_http_request(req)
        res <- _to_http_response(_subsystem.executeHttp(core))
      } yield res
    }
    EmberServerBuilder
      .default[IO]
      .withPort(Port.fromInt(8080).get)
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
    IO.pure(
      HttpRequest(
        url = new java.net.URL(req.uri.renderString),
        method = method,
        query = query,
        form = Record.empty,
        header = header
      )
    )
  }

  private def _to_http_response(
    res: HttpResponse
  ): IO[org.http4s.Response[IO]] = {
    val status = HStatus.fromInt(res.code).getOrElse(HStatus.InternalServerError)
    val body = res.getString.getOrElse("")
    val base = HResponse[IO](status).withEntity(body)
    val contentType =
      MediaType.parse(res.mime.toString).toOption.map(`Content-Type`(_))
    IO.pure(contentType.fold(base)(base.putHeaders(_)))
  }
}
