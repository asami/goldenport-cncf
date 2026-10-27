package org.goldenport.cncf.operationalobservation

import java.time.Instant
import io.circe.Json
import io.circe.jawn.JawnParser
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import scala.util.Try

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
private[cncf] object HttpOperationalObservationProvider extends OperationalObservationProvider {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  def readC(
    source: ObservationSource, request: ObservationRead, effects: UnitOfWorkOp ~> Consequence
  ): AxisObservation = source match {
    case selected: ObservationSource.Http =>
      val sampled = request.now
      val result = OperationalObservationProvider.fetchC(selected.url, request, effects).flatMap { response =>
        if (selected.axis == ObservationAxis.Reachability)
          Right(AxisObservation.Observed("reachable", selected.sourceId, sampled,
            sampled.plusMillis(selected.freshnessMillis), Some(selected.scope)))
        else if (response.code == 401 || response.code == 403)
          Left(ObservationReason.PermissionDenied)
        else if (response.code < 200 || response.code >= 300)
          Left(ObservationReason.SourceUnavailable)
        else for {
          text <- response.getString.toRight(ObservationReason.InvalidSource)
          json <- _parser.parse(text).left.map(_ => ObservationReason.InvalidSource)
          value <- decode(selected, json, sampled, request.now)
        } yield value
      }
      result.fold(reason => AxisObservation.Unavailable(reason, Some(selected.sourceId)), _.currentAt(request.now))
    case _ => AxisObservation.Unavailable(ObservationReason.Unsupported, Some(source.sourceId))
  }

  private[operationalobservation] def decode(
    source: ObservationSource.Http, json: Json, sampled: Instant, now: Instant
  ): Either[ObservationReason, AxisObservation] = {
    val payload = json.asObject.flatMap(_("data")).filter(_.isObject).getOrElse(json)
    val cursor = payload.hcursor
    for {
      value <- cursor.get[String]("value").orElse(cursor.get[String]("status")).toOption
        .filter(source.vocabulary.contains).toRight(ObservationReason.InvalidSource)
      scope <- cursor.downField("scope").focus match {
        case None if source.sampleMode == "current" => Right(source.scope)
        case Some(raw) => raw.asString.filter(_ == source.scope).toRight(ObservationReason.InvalidSource)
        case None => Left(ObservationReason.InvalidSource)
      }
      observed <- cursor.downField("observedAt").focus match {
        case None if source.sampleMode == "current" => Right(sampled)
        case Some(value) => for {
          raw <- value.asString.toRight(ObservationReason.InvalidSource)
          instant <- Try(Instant.parse(raw)).toOption.filter(_ => raw.endsWith("Z")).toRight(ObservationReason.InvalidSource)
        } yield instant
        case None => Left(ObservationReason.InvalidSource)
      }
      _ <- Either.cond(!observed.isAfter(now) && observed.plusMillis(source.freshnessMillis).isAfter(now),
        (), ObservationReason.Stale)
    } yield AxisObservation.Observed(value, source.sourceId, observed,
      observed.plusMillis(source.freshnessMillis), Some(scope))
  }
}
