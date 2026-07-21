package org.goldenport.cncf.http

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URI, URL}
import java.nio.charset.{Charset, StandardCharsets}
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.slf4j.LoggerFactory

/*
 * @since   Jan. 11, 2026
 *  version Feb.  7, 2026
 *  version Apr. 29, 2026
 *  version May. 30, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
trait HttpDriver {
  def get(path: String, headers: Map[String, String] = Map.empty, properties: Vector[Property] = Vector.empty): HttpResponse
  def post(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse
  def postBag(path: String, body: Option[Bag], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse =
    throw new UnsupportedOperationException("HttpDriver.postBag is not implemented by this driver")
  def put(path: String, body: Option[String], headers: Map[String, String], properties: Vector[Property] = Vector.empty): HttpResponse
}

final class UrlConnectionHttpDriver(
  baseurl: String
) extends HttpDriver {
  private val _default_connect_timeout_ms = 10000
  private val _default_read_timeout_ms = 10000

  def get(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val conn = _open_connection(_build_url(path), "GET", properties)
    headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }
    _execute(conn, None, properties)
  }

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val conn = _open_connection(_build_url(path), "POST", properties)
    headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }
    _execute(conn, body.map(Bag.text(_, StandardCharsets.UTF_8)), properties)
  }

  override def postBag(
    path: String,
    body: Option[Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val conn = _open_connection(_build_url(path), "POST", properties)
    headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }
    _execute(conn, body, properties)
  }

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val conn = _open_connection(_build_url(path), "PUT", properties)
    headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }
    _execute(conn, body.map(Bag.text(_, StandardCharsets.UTF_8)), properties)
  }

  private val _log = LoggerFactory.getLogger(classOf[UrlConnectionHttpDriver])

  private def _open_connection(
    url: URL,
    method: String,
    properties: Vector[Property]
  ): HttpURLConnection = {
    if (
      _public_network_only(properties) &&
      !Option(url.getHost).exists(PublicNetworkAdmission.admitHostC(_).isSuccess)
    )
      throw new java.io.IOException("HTTP target does not satisfy public-network policy")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod(method)
    conn.setConnectTimeout(_connect_timeout_ms(properties))
    conn.setReadTimeout(_read_timeout_ms(properties))
    conn.setInstanceFollowRedirects(_follow_redirects(properties))
    if (method == "PUT" || method == "POST") {
      conn.setDoOutput(true)
    }
    conn.setDoInput(true)
    conn
  }

  private def _connect_timeout_ms(
    properties: Vector[Property]
  ): Int =
    _timeout_ms(properties, Vector("http.connect-timeout-ms"))
      .orElse(_timeout_seconds(properties, Vector("http.connect-timeout-seconds", "http.timeout-seconds")))
      .getOrElse(_default_connect_timeout_ms)

  private def _read_timeout_ms(
    properties: Vector[Property]
  ): Int =
    _timeout_ms(properties, Vector("http.read-timeout-ms"))
      .orElse(_timeout_seconds(properties, Vector("http.read-timeout-seconds", "http.timeout-seconds")))
      .getOrElse(_default_read_timeout_ms)

  private def _follow_redirects(
    properties: Vector[Property]
  ): Boolean =
    _property_string(properties, Vector("http.follow-redirects"))
      .flatMap(_.toBooleanOption)
      .getOrElse(true)

  private def _public_network_only(properties: Vector[Property]): Boolean =
    _property_string(properties, Vector("http.public-network-only"))
      .flatMap(_.toBooleanOption)
      .getOrElse(false)

  private def _timeout_ms(
    properties: Vector[Property],
    names: Vector[String]
  ): Option[Int] =
    _property_string(properties, names).flatMap(_.toIntOption).filter(_ > 0)

  private def _timeout_seconds(
    properties: Vector[Property],
    names: Vector[String]
  ): Option[Int] =
    _property_string(properties, names).flatMap(_.toLongOption).filter(_ > 0).map { seconds =>
      math.min(seconds * 1000L, Int.MaxValue.toLong).toInt
    }

  private def _property_string(
    properties: Vector[Property],
    names: Vector[String]
  ): Option[String] = {
    val keys = names.map(_.toLowerCase(java.util.Locale.ROOT)).toSet
    properties.collectFirst {
      case Property(name, value, _) if keys.contains(name.toLowerCase(java.util.Locale.ROOT)) =>
        String.valueOf(value).trim
    }.filter(_.nonEmpty)
  }

  private def _build_url(
    path: String
  ): URL = {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      URI.create(path).toURL
    } else {
      val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
      val suffix = if (path.startsWith("/")) path else s"/${path}"
      URI.create(s"${base}${suffix}").toURL
    }
  }

  private def _execute(
    conn: HttpURLConnection,
    body: Option[Bag],
    properties: Vector[Property]
  ): HttpResponse = {
    try {
      body.foreach { b =>
        val in = b.openInputStream()
        val out = conn.getOutputStream
        try {
          val buffer = new Array[Byte](8192)
          var read = in.read(buffer)
          while (read != -1) {
            out.write(buffer, 0, read)
            read = in.read(buffer)
          }
          out.flush()
        } finally {
          in.close()
          out.close()
        }
      }
      val code = conn.getResponseCode
      val stream = _response_stream(conn)
      val contenttype = _content_type(conn.getContentType)
      val status = _status(code)
      val bytes = _read_bytes(stream, _max_response_bytes(properties))
      val response =
        if (contenttype.mimeType.isText) {
          val charset = contenttype.charset.getOrElse(StandardCharsets.UTF_8)
          HttpResponse.Text(status, contenttype, Bag.text(new String(bytes, charset), charset))
        } else {
          HttpResponse.Binary(status, contenttype, Bag.binary(bytes))
        }
      response.withHeader(_response_header(conn))
    } finally {
      conn.disconnect()
    }
  }

  private def _response_header(
    conn: HttpURLConnection
  ): Record = {
    val values = conn.getHeaderFields.entrySet().toArray.toVector.flatMap { entry =>
      val e = entry.asInstanceOf[java.util.Map.Entry[String, java.util.List[String]]]
      Option(e.getKey).flatMap { key =>
        Option(e.getValue).flatMap(values =>
          values.toArray.toVector.collectFirst { case v: String if v != null => key -> v }
        )
      }
    }.sortBy { case (key, _) => key.toLowerCase(java.util.Locale.ROOT) }
    Record.create(values)
  }

  private def _response_stream(
    conn: HttpURLConnection
  ): InputStream =
    Option(conn.getErrorStream).getOrElse(conn.getInputStream)

  private def _read_text(
    stream: InputStream,
    charset: Charset
  ): String = {
    new String(_read_bytes(stream, None), charset)
  }

  private def _read_bytes(
    stream: InputStream,
    maxbytes: Option[Long]
  ): Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    val bytes = new Array[Byte](8192)
    var read = stream.read(bytes)
    while (read != -1) {
      maxbytes.foreach { limit =>
        if (buffer.size.toLong + read.toLong > limit) {
          stream.close()
          throw new java.io.IOException("HTTP response exceeds configured byte limit")
        }
      }
      buffer.write(bytes, 0, read)
      read = stream.read(bytes)
    }
    stream.close()
    buffer.toByteArray
  }

  private def _max_response_bytes(properties: Vector[Property]): Option[Long] =
    _property_string(properties, Vector("http.max-response-bytes"))
      .flatMap(_.toLongOption)
      .filter(_ >= 0)

  private def _content_type(
    value: String
  ): ContentType = {
    val (mime, charset) = _parse_content_type(value)
    ContentType(MimeType(mime), charset)
  }

  private def _parse_content_type(
    value: String
  ): (String, Option[Charset]) = {
    if (value == null || value.isEmpty) {
      ("text/plain", Some(StandardCharsets.UTF_8))
    } else {
      val parts = value.split(";").map(_.trim).toVector
      val mime = parts.headOption.getOrElse("text/plain")
      val charset = parts.collectFirst {
        case p if p.toLowerCase.startsWith("charset=") =>
          Charset.forName(p.split("=", 2).last)
      }
      (mime, charset.orElse(Some(StandardCharsets.UTF_8)))
    }
  }

  private def _status(
    code: Int
  ): HttpStatus =
    HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError)
}

final class FakeHttpDriver(
  response: HttpResponse
) extends HttpDriver {
  def get(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val _ = (path, headers, properties)
    response
  }

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val _ = (path, body, headers, properties)
    response
  }

  override def postBag(
    path: String,
    body: Option[Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val _ = (path, body, headers, properties)
    response
  }

  def put(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): HttpResponse = {
    val _ = (path, body, headers, properties)
    response
  }
}

object FakeHttpDriver {
  def okText(
    body: String,
    contentType: String = "text/plain; charset=utf-8"
  ): FakeHttpDriver = {
    val (mime, charset) = _parse_content_type(contentType)
    val ct = ContentType(MimeType(mime), charset)
    val res = HttpResponse.Text(HttpStatus.Ok, ct, Bag.text(body, charset.getOrElse(StandardCharsets.UTF_8)))
    new FakeHttpDriver(res)
  }

  private def _parse_content_type(
    value: String
  ): (String, Option[Charset]) = {
    if (value.isEmpty) {
      ("text/plain", Some(StandardCharsets.UTF_8))
    } else {
      val parts = value.split(";").map(_.trim).toVector
      val mime = parts.headOption.getOrElse("text/plain")
      val charset = parts.collectFirst {
        case p if p.toLowerCase.startsWith("charset=") =>
          Charset.forName(p.split("=", 2).last)
      }
      (mime, charset.orElse(Some(StandardCharsets.UTF_8)))
    }
  }
}
