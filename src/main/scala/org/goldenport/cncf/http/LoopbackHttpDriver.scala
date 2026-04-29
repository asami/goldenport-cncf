package org.goldenport.cncf.http

import java.net.{URI, URL}
import java.nio.charset.{Charset, StandardCharsets}

import org.goldenport.bag.Bag
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.record.Record

/*
 * @since   Jan. 20, 2026
 *  version Feb.  7, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class LoopbackHttpDriver(
  server: LoopbackHttpServer,
  baseurl: String = ClientConfig.DefaultBaseUrl,
  charset: Charset = StandardCharsets.UTF_8
) extends HttpDriver {

  def get(path: String): HttpResponse =
    _execute(_buildRequest(HttpRequest.GET, path, None))

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    val bag = body.map(b => Bag.text(b, charset))
    _execute(_buildRequest(HttpRequest.POST, path, bag, headers))
  }

  override def postBag(
    path: String,
    body: Option[Bag],
    headers: Map[String, String]
  ): HttpResponse =
    _execute(_buildRequest(HttpRequest.POST, path, body, headers))

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    val bag = body.map(b => Bag.text(b, charset))
    _execute(_buildRequest(HttpRequest.PUT, path, bag, headers))
  }

  private def _execute(
    req: HttpRequest
  ): HttpResponse = {
    val result = server.executeWithMetadata(req)
    result.metadata.responseJobId.orElse(result.metadata.debugJobId) match {
      case Some(jobid) if jobid.nonEmpty =>
        result.response.withHeader(_replace_header(result.response.header, "X-Textus-Job-Id", jobid))
      case _ =>
        result.response
    }
  }

  private def _replace_header(
    header: Record,
    name: String,
    value: String
  ): Record = {
    val remaining = header.fields.filterNot(_.key.equalsIgnoreCase(name))
    Record(remaining) ++ Record.data(name -> value)
  }

  private def _buildRequest(
    method: HttpRequest.Method,
    path: String,
    body: Option[Bag],
    headers: Map[String, String] = Map.empty
  ): HttpRequest = {
    val url = _buildUrl(path)
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
