package org.goldenport.cncf.display

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Request
import org.goldenport.record.Record
import scala.util.control.NonFatal

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Lossless physical metadata admission, invoked only after Operation authorization. */
object PresentationContextIngress {
  private[cncf] enum Origin {
    case Canonical
    case HttpIngress(request: HttpRequest)
  }

  private val _property_name = "cncf.presentationContext"
  private val _header_name = "x-cncf-presentation-context"
  // Lexical exclusion only: never decode a presentation value during generic enrichment.
  private val _encoded_key = _property_name.iterator.map { char =>
    "(?:" + java.util.regex.Pattern.quote(char.toString) + "|(?i:%" + f"${char.toInt}%02x" + "))"
  }.mkString.r

  def requestC(request: Request, httpRequest: Option[HttpRequest] = None): Consequence[Option[String]] =
    _extract_c(request, httpRequest, Origin.Canonical)

  private[cncf] def _extract_c(request: Request, httprequest: Option[HttpRequest],
    origin: Origin): Consequence[Option[String]] = {
    if (request == null || httprequest == null || httprequest.contains(null) || origin == null) _invalid
    else {
      val raworigin = origin match {
        case Origin.HttpIngress(raw) => Some(raw)
        case Origin.Canonical => request.source.collect { case Request.Source.Http(raw) => raw }
      }
      val properties = if (raworigin.nonEmpty) Vector.empty else
        request.properties.iterator.filter(_.name == _property_name).map(_.value).toVector
      val physical = raworigin.orElse(httprequest) match {
        case Some(raw) => _http_candidates(raw)
        case None => Consequence.success(Vector.empty[Any])
      }
      physical.flatMap(values => _single(properties ++ values))
    }
  }

  private[cncf] def _is_context_query_key(name: String): Boolean =
    name != null && _encoded_key.matches(name)

  /** Preserve every unrelated generic query segment and its existing decoding behavior. */
  private[cncf] def _without_context_query(query: String): String =
    query.split("&", -1).iterator.filterNot { segment =>
      val key = segment.takeWhile(_ != '=')
      _is_context_query_key(key)
    }.mkString("&")

  private def _http_candidates(request: HttpRequest): Consequence[Vector[Any]] = {
    if (request == null) _invalid
    else for {
      raw <- _raw_query(request)
      query <- _query_candidates(raw, _fields(request.query, _ == _property_name))
    } yield query ++ _fields(request.form, _ == _property_name) ++
      _fields(request.header, name => name != null && name.forall(_ <= 127) &&
        name.toLowerCase(Locale.ROOT) == _header_name)
  }

  private def _fields(record: Record, matching: String => Boolean): Vector[Any] = {
    record.fields.iterator.filter(field => matching(field.key)).map(_.value.single).toVector
  }

  private def _raw_query(request: HttpRequest): Consequence[Vector[String]] = {
    val query = request.context.originalUri.flatMap { uri =>
      val index = uri.indexOf('?')
      if (index < 0) None else Some(uri.substring(index + 1).takeWhile(_ != '#'))
    }
    try {
      val values = query.toVector.flatMap(_.split("&", -1)).filter(_.nonEmpty).flatMap { segment =>
        val index = segment.indexOf('=')
        val key = if (index < 0) segment else segment.substring(0, index)
        val value = if (index < 0) "" else segment.substring(index + 1)
        val decodedkey = URLDecoder.decode(key, StandardCharsets.UTF_8)
        if (decodedkey == _property_name) Some(URLDecoder.decode(value, StandardCharsets.UTF_8)) else None
      }
      Consequence.success(values)
    } catch { case NonFatal(_) => _invalid }
  }

  private def _query_candidates(raw: Vector[String], structured: Vector[Any]): Consequence[Vector[Any]] = {
    if (raw.isEmpty) Consequence.success(structured)
    else if (!structured.forall(_.isInstanceOf[String])) _invalid
    else if (raw.size == 1 && structured.nonEmpty && structured != raw) _invalid
    else Consequence.success(raw)
  }

  private def _single(values: Vector[Any]): Consequence[Option[String]] = values match {
    case Vector() => Consequence.success(None)
    case Vector(value: String) => Consequence.success(Some(value))
    case _ => _invalid
  }

  private def _invalid[A]: Consequence[A] =
    Consequence.argumentInvalid("invalid presentation context metadata")
}
