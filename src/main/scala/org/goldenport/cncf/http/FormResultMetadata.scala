package org.goldenport.cncf.http

import io.circe.ACursor
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.http.HttpResponse
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Apr. 15, 2026
 * @version Apr. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class FormResultMetadata(
  id: Option[String] = None,
  jobId: Option[String] = None,
  outcome: Option[String] = None,
  message: Option[String] = None,
  actions: Vector[FormResultMetadata.Action] = Vector.empty
) {
  def toTemplateValues: Map[String, String] = {
    val scalar =
      id.map("result.id" -> _).toMap ++
        jobId.map("result.job.id" -> _).toMap ++
        outcome.map("result.outcome" -> _).toMap ++
        message.map("result.message" -> _).toMap ++
        (if (actions.nonEmpty) Map("result.actions.count" -> actions.length.toString) else Map.empty)
    scalar ++ _action_values
  }

  private def _action_values: Map[String, String] = {
    val indexed = actions.zipWithIndex.flatMap { case (action, index) =>
      action.toTemplateValues(s"result.action.${index}")
    }
    val primary = actions.headOption.toVector.flatMap(_.toTemplateValues("result.action.primary"))
    val named = actions.flatMap { action =>
      action.name.toVector.flatMap { name =>
        val key = NamingConventions.toNormalizedSegment(name)
        action.toTemplateValues(s"result.action.${key}")
      }
    }
    (indexed ++ primary ++ named).toMap
  }
}

object FormResultMetadata {
  final case class Action(
    name: Option[String] = None,
    label: Option[String] = None,
    href: Option[String] = None,
    method: Option[String] = None
  ) {
    def toTemplateValues(prefix: String): Vector[(String, String)] =
      Vector(
        name.map(s"${prefix}.name" -> _),
        label.map(s"${prefix}.label" -> _),
        href.map(s"${prefix}.href" -> _),
        method.map(s"${prefix}.method" -> _)
      ).flatten
  }

  def fromHttpResponse(response: HttpResponse): FormResultMetadata =
    fromBody(response.getString.getOrElse(""))

  def fromBody(body: String): FormResultMetadata =
    _json_metadata_or_empty(body).getOrElse(
      FormResultMetadata(
        id = _scalar_id(body),
        jobId = _scalar_job_id(body)
      )
    )

  private def _json_metadata_or_empty(body: String): Option[FormResultMetadata] =
    parse(body).toOption.map { json =>
      val cursor = json.hcursor
      FormResultMetadata(
        id = _first_string(cursor, Vector("id", "result.id", "item.id")),
        jobId = _first_string(cursor, Vector("jobId", "job-id", "job.id", "result.jobId", "result.job-id", "result.job.id")),
        outcome = _first_string(cursor, Vector("outcome", "result.outcome")),
        message = _first_string(cursor, Vector("message", "result.message", "error.message")),
        actions = _actions(cursor)
      )
    }

  private def _scalar_id(body: String): Option[String] =
    body.split(":", 2).toList match {
      case _ :: id :: Nil => Some(id.trim).filter(_.nonEmpty)
      case _ => None
    }

  private def _scalar_job_id(body: String): Option[String] =
    Some(body.trim).filter(_.startsWith("cncf-job-")).filter(_.nonEmpty)

  private def _first_string(
    cursor: HCursor,
    paths: Vector[String]
  ): Option[String] =
    paths.view.flatMap(path => _string_at(cursor, path)).headOption

  private def _string_at(
    cursor: HCursor,
    path: String
  ): Option[String] = {
    val c = path.split("\\.").foldLeft(cursor: ACursor) { case (z, name) => z.downField(name) }
    c.get[String]("value").toOption
      .orElse(c.as[String].toOption)
      .map(_.trim)
      .filter(_.nonEmpty)
  }

  private def _actions(cursor: HCursor): Vector[Action] =
    _json_actions(cursor, "actions") ++
      _json_actions(cursor.downField("result"), "actions") ++
      _json_action(cursor, "action").toVector ++
      _json_action(cursor.downField("result"), "action").toVector

  private def _json_actions(cursor: ACursor, field: String): Vector[Action] =
    cursor.downField(field).as[Vector[Json]].toOption.getOrElse(Vector.empty).flatMap(_json_action)

  private def _json_action(cursor: ACursor, field: String): Option[Action] =
    cursor.downField(field).focus.flatMap(_json_action)

  private def _json_action(json: Json): Option[Action] =
    json.asString match {
      case Some(name) =>
        Some(Action(name = Some(name).map(_.trim).filter(_.nonEmpty)))
      case None =>
        val c = json.hcursor
        val action = Action(
          name = _first_string(c, Vector("name", "id")),
          label = _first_string(c, Vector("label", "title")),
          href = _first_string(c, Vector("href", "path", "url")),
          method = _first_string(c, Vector("method"))
        )
        Option.when(action.toTemplateValues("x").nonEmpty)(action)
    }
}
