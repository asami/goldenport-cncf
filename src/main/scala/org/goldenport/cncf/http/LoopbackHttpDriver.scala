package org.goldenport.cncf.http

import java.net.{URI, URL}
import java.nio.charset.{Charset, StandardCharsets}

import org.goldenport.bag.Bag
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.http.{HttpRequest, HttpResponse}

/*
 * @since   Jan. 20, 2026
 *  version Feb.  7, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class LoopbackHttpDriver(
  server: LoopbackHttpServer,
  baseurl: String = ClientConfig.DefaultBaseUrl,
  charset: Charset = StandardCharsets.UTF_8
) extends HttpDriver {

  def get(path: String): HttpResponse =
    server.execute(_buildRequest(HttpRequest.GET, path, None))

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    val bag = body.map(b => Bag.text(b, charset))
    server.execute(_buildRequest(HttpRequest.POST, path, bag))
  }

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    val bag = body.map(b => Bag.text(b, charset))
    server.execute(_buildRequest(HttpRequest.PUT, path, bag))
  }

  private def _buildRequest(
    method: HttpRequest.Method,
    path: String,
    body: Option[Bag]
  ): HttpRequest = {
    val url = _buildUrl(path)
    HttpRequest.fromUrl(method = method, url = url, body = body)
  }

  private def _buildUrl(path: String): URL = {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      URI.create(path).toURL
    } else {
      val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
      val suffix = if (path.startsWith("/")) path else s"/${path}"
      URI.create(s"${base}${suffix}").toURL
    }
  }
}
