package org.goldenport.cncf.http

import io.circe.ACursor
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.http.HttpResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Apr. 15, 2026
 *  version Apr. 21, 2026
 *  version May. 27, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class FormResultMetadata(
  id: Option[String] = None,
  jobId: Option[String] = None,
  jobStatus: Option[String] = None,
  outcome: Option[String] = None,
  message: Option[String] = None,
  resolverStatus: Option[String] = None,
  candidateCount: Option[Long] = None,
  suggestedFieldCount: Option[Long] = None,
  totalCount: Option[Long] = None,
  fetchedCount: Option[Long] = None,
  limit: Option[Long] = None,
  actions: Vector[FormResultMetadata.Action] = Vector.empty
) {
  def toTemplateValues: Map[String, String] = {
    val hasNext = for {
      fetched <- fetchedCount
      n <- limit
      if n > 0
    } yield fetched >= n
    val scalar =
      id.map("result.id" -> _).toMap ++
        jobId.map("result.job.id" -> _).toMap ++
        jobStatus.map("result.job.status" -> _).toMap ++
        outcome.map("result.outcome" -> _).toMap ++
        message.map("result.message" -> _).toMap ++
        resolverStatus.map("result.resolverStatus" -> _).toMap ++
        candidateCount.map(x => "result.candidateCount" -> x.toString).toMap ++
        suggestedFieldCount.map(x => "result.suggestedFieldCount" -> x.toString).toMap ++
        totalCount.map(x => "result.totalCount" -> x.toString).toMap ++
        totalCount.map(x => "paging.total" -> x.toString).toMap ++
        fetchedCount.map(x => "result.fetchedCount" -> x.toString).toMap ++
        limit.map(x => "result.limit" -> x.toString).toMap ++
        hasNext.map(x => "paging.hasNext" -> x.toString).toMap ++
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

  object Action {
    def fromJson(json: Json): Option[Action] =
      _json_action(json)
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

  def executionTemplateValues(
    metadata: RuntimeContext.ExecutionMetadata
  ): Map[String, String] = {
    val calltree = metadata.inlineCallTree
    val providers = calltree.toVector.flatMap(_provider_labels).distinct
    val hasdiagnostics =
      calltree.nonEmpty ||
        metadata.traceId.nonEmpty ||
        metadata.executionId.nonEmpty ||
        metadata.failure.nonEmpty
    metadata.sagaId.map("result.execution.saga.id" -> _).toMap ++
      metadata.executionJobId.map("result.execution.job.id" -> _).toMap ++
      metadata.executionTaskId.map("result.execution.task.id" -> _).toMap ++
      metadata.traceId.map("result.execution.trace.id" -> _).toMap ++
      metadata.executionId.map("result.execution.id" -> _).toMap ++
      metadata.failure.map("result.execution.failure" -> _).toMap ++
      calltree.map(_ => "result.execution.calltree.captured" -> "true").toMap ++
      calltree.map(x => "result.execution.calltree.json" -> RecordEncoder.json(x)).toMap ++
      (if (hasdiagnostics) Map("result.execution.calltree.href" -> _execution_href("/rest/v1/admin/execution/calltree", metadata)) else Map.empty) ++
      (if (hasdiagnostics) Map("result.execution.history.href" -> _execution_href("/rest/v1/admin/execution/history", metadata)) else Map.empty) ++
      (if (providers.nonEmpty) Map("result.execution.providers" -> providers.mkString(",")) else Map.empty)
  }

  private def _execution_href(
    base: String,
    metadata: RuntimeContext.ExecutionMetadata
  ): String = {
    val params = Vector(
      metadata.executionId.map("executionId" -> _),
      metadata.traceId.map("traceId" -> _)
    ).flatten
    if (params.isEmpty)
      base
    else
      base + "?" + params.map { case (key, value) =>
        s"${_url_encode(key)}=${_url_encode(value)}"
      }.mkString("&")
  }

  private def _url_encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def _json_metadata_or_empty(body: String): Option[FormResultMetadata] =
    parse(body).toOption.map { json =>
      val cursor = json.hcursor
      FormResultMetadata(
        id = _first_string(cursor, Vector(
          "id",
          "result.id",
          "item.id",
          "data.id",
          "data.informationId",
          "data.information_id",
          "data.current.informationId",
          "data.current.information_id"
        )),
        jobId = _first_string(cursor, Vector("jobId", "job-id", "job.id", "result.jobId", "result.job-id", "result.job.id")),
        jobStatus = _first_string(cursor, Vector("jobStatus", "job-status", "job.status", "result.jobStatus", "result.job-status", "result.job.status")),
        outcome = _first_string(cursor, Vector("outcome", "result.outcome", "data.actionStatus", "data.action_status")),
        message = _first_string(cursor, Vector("message", "result.message", "error.message")),
        resolverStatus = _first_string(cursor, Vector("resolverStatus", "resolver_status", "data.resolverStatus", "data.resolver_status")),
        candidateCount = _first_long(cursor, Vector("candidateCount", "candidate_count", "data.addedCandidateCount", "data.added_candidate_count")),
        suggestedFieldCount = _first_long(cursor, Vector("suggestedFieldCount", "suggested_field_count", "data.suggestedFieldCount", "data.suggested_field_count")),
        totalCount = _first_long(cursor, Vector(
          "totalCount",
          "total_count",
          "total-count",
          "data.totalCount",
          "data.total_count",
          "data.total-count",
          "result.totalCount",
          "result.total_count",
          "result.total-count"
        )),
        fetchedCount = _first_long(cursor, Vector(
          "fetchedCount",
          "fetched_count",
          "fetched-count",
          "data.fetchedCount",
          "data.fetched_count",
          "data.fetched-count",
          "result.fetchedCount",
          "result.fetched_count",
          "result.fetched-count"
        )),
        limit = _first_long(cursor, Vector(
          "limit",
          "data.limit",
          "result.limit"
        )),
        actions = _actions(cursor)
      )
    }

  private def _scalar_id(body: String): Option[String] =
    _first_line(body).split(":", 2).toList match {
      case _ :: id :: Nil => Some(id.trim).filter(_.nonEmpty)
      case _ => None
    }

  private def _scalar_job_id(body: String): Option[String] =
    Some(_first_line(body)).filter(_.startsWith("cncf-job-")).filter(_.nonEmpty)

  private def _first_line(body: String): String =
    body.linesIterator.take(1).toVector.headOption.getOrElse("").trim

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

  private def _first_long(
    cursor: HCursor,
    paths: Vector[String]
  ): Option[Long] =
    paths.view.flatMap(path => _long_at(cursor, path)).headOption

  private def _long_at(
    cursor: HCursor,
    path: String
  ): Option[Long] = {
    val c = path.split("\\.").foldLeft(cursor: ACursor) { case (z, name) => z.downField(name) }
    c.get[Long]("value").toOption
      .orElse(c.as[Long].toOption)
      .orElse(c.as[String].toOption.flatMap(_.trim.toLongOption))
      .filter(_ >= 0)
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

  private def _provider_labels(record: Record): Vector[String] = {
    val here =
      if (record.getString("kind").contains("provider"))
        record.getString("label").filter(_.startsWith("provider:")).toVector
      else
        Vector.empty
    here ++ record.asMap.values.toVector.flatMap(_provider_labels)
  }

  private def _provider_labels(value: Any): Vector[String] =
    value match {
      case r: Record => _provider_labels(r)
      case xs: Iterable[?] => xs.toVector.flatMap(_provider_labels)
      case xs: Array[?] => xs.toVector.flatMap(_provider_labels)
      case _ => Vector.empty
    }
}
