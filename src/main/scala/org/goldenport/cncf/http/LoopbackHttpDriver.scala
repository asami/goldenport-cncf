package org.goldenport.cncf.http

import java.net.{URI, URL}
import java.nio.charset.{Charset, StandardCharsets}

import org.goldenport.bag.Bag
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Property
import org.goldenport.record.Record

/*
 * @since   Jan. 20, 2026
 *  version Feb.  7, 2026
 *  version Apr. 29, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class LoopbackHttpDriver(
  server: LoopbackHttpServer,
  baseurl: String = ClientConfig.DefaultBaseUrl,
  charset: Charset = StandardCharsets.UTF_8
) extends HttpDriver {

  def get(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): HttpResponse =
    _execute(_build_request(HttpRequest.GET, path, None, headers))

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val bag = body.map(b => Bag.text(b, charset))
    _execute(_build_request(HttpRequest.POST, path, bag, headers))
  }

  override def postBag(
    path: String,
    body: Option[Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse =
    _execute(_build_request(HttpRequest.POST, path, body, headers))

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val bag = body.map(b => Bag.text(b, charset))
    _execute(_build_request(HttpRequest.PUT, path, bag, headers))
  }

  private def _execute(
    req: HttpRequest
  ): HttpResponse =
    server.executeWithMetadata(req).response

  private def _build_request(
    method: HttpRequest.Method,
    path: String,
    body: Option[Bag],
    headers: Map[String, String] = Map.empty
  ): HttpRequest = {
    val url = _build_url(path)
    val query = Option(url.getQuery)
      .filter(_.nonEmpty)
      .map(HttpRequest.parseQuery)
      .getOrElse(Record.empty)
    val header =
      if (headers.isEmpty)
        Record.empty
      else
        Record.create(headers.toVector.sortBy { case (key, _) =>
          key.toLowerCase(java.util.Locale.ROOT)
        })
    HttpRequest.fromUrl(method = method, url = url, query = query, header = header, body = body)
  }

  private def _build_url(path: String): URL = {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      URI.create(path).toURL
    } else {
      val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
      val suffix = if (path.startsWith("/")) path else s"/${path}"
      URI.create(s"${base}${suffix}").toURL
    }
  }
}
