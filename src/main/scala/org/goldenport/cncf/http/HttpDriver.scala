package org.goldenport.cncf.http

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.{Charset, StandardCharsets}
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus, StringResponse}

/*
 * @since   Jan. 11, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
trait HttpDriver {
  def get(path: String): HttpResponse
  def post(path: String, body: Option[String], headers: Map[String, String]): HttpResponse
}

final class UrlConnectionHttpDriver(
  baseurl: String
) extends HttpDriver {
  def get(path: String): HttpResponse = {
    val conn = _open_connection_(path, "GET")
    _execute_(conn, None)
  }

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    val conn = _open_connection_(path, "POST")
    headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }
    _execute_(conn, body)
  }

  private def _open_connection_(
    path: String,
    method: String
  ): HttpURLConnection = {
    val url = _build_url_(path)
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod(method)
    conn.setDoInput(true)
    if (method == "POST") {
      conn.setDoOutput(true)
    }
    conn
  }

  private def _build_url_(
    path: String
  ): URL = {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      new URL(path)
    } else {
      val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
      val suffix = if (path.startsWith("/")) path else s"/${path}"
      new URL(s"${base}${suffix}")
    }
  }

  private def _execute_(
    conn: HttpURLConnection,
    body: Option[String]
  ): HttpResponse = {
    body.foreach { b =>
      val bytes = b.getBytes(StandardCharsets.UTF_8)
      conn.getOutputStream.write(bytes)
      conn.getOutputStream.flush()
      conn.getOutputStream.close()
    }
    val code = conn.getResponseCode
    val stream = _response_stream_(conn)
    val text = _read_text_(stream, StandardCharsets.UTF_8)
    val contentType = _content_type_(conn.getContentType)
    val status = _status_(code)
    StringResponse(status, contentType, Bag.text(text, StandardCharsets.UTF_8))
  }

  private def _response_stream_(
    conn: HttpURLConnection
  ): InputStream =
    Option(conn.getErrorStream).getOrElse(conn.getInputStream)

  private def _read_text_(
    stream: InputStream,
    charset: Charset
  ): String = {
    val buffer = new ByteArrayOutputStream
    val bytes = new Array[Byte](8192)
    var read = stream.read(bytes)
    while (read != -1) {
      buffer.write(bytes, 0, read)
      read = stream.read(bytes)
    }
    stream.close()
    new String(buffer.toByteArray, charset)
  }

  private def _content_type_(
    value: String
  ): ContentType = {
    val (mime, charset) = _parse_content_type_(value)
    ContentType(MimeType(mime), charset)
  }

  private def _parse_content_type_(
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

  private def _status_(
    code: Int
  ): HttpStatus = code match {
    case 200 => HttpStatus.Ok
    case 404 => HttpStatus.NotFound
    case _ => HttpStatus.InternalServerError
  }
}

final class FakeHttpDriver(
  response: HttpResponse
) extends HttpDriver {
  def get(path: String): HttpResponse = {
    val _ = path
    response
  }

  def post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): HttpResponse = {
    val _ = (path, body, headers)
    response
  }
}

object FakeHttpDriver {
  def okText(
    body: String,
    contentType: String = "text/plain; charset=utf-8"
  ): FakeHttpDriver = {
    val (mime, charset) = _parse_content_type_(contentType)
    val ct = ContentType(MimeType(mime), charset)
    val res = StringResponse(HttpStatus.Ok, ct, Bag.text(body, charset.getOrElse(StandardCharsets.UTF_8)))
    new FakeHttpDriver(res)
  }

  private def _parse_content_type_(
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
