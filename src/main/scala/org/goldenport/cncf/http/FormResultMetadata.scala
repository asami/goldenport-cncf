package org.goldenport.cncf.http

import io.circe.parser.parse
import org.goldenport.http.HttpResponse

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class FormResultMetadata(
  id: Option[String] = None
) {
  def toTemplateValues: Map[String, String] =
    id.map("result.id" -> _).toMap
}

object FormResultMetadata {
  def fromHttpResponse(response: HttpResponse): FormResultMetadata =
    fromBody(response.getString.getOrElse(""))

  def fromBody(body: String): FormResultMetadata =
    FormResultMetadata(_json_id_or_empty(body).getOrElse(_scalar_id(body)))

  private def _json_id_or_empty(body: String): Option[Option[String]] =
    parse(body).toOption.map { json =>
      val cursor = json.hcursor
      Vector(
        cursor.get[String]("id").toOption,
        cursor.downField("result").get[String]("id").toOption,
        cursor.downField("item").get[String]("id").toOption
      ).flatten.headOption.map(_.trim).filter(_.nonEmpty)
    }

  private def _scalar_id(body: String): Option[String] =
    body.split(":", 2).toList match {
      case _ :: id :: Nil => Some(id.trim).filter(_.nonEmpty)
      case _ => None
    }
}
