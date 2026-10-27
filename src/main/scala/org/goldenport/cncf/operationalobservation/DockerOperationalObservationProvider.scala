package org.goldenport.cncf.operationalobservation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import io.circe.Json
import io.circe.jawn.JawnParser
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.servicecontainer.ServiceContainerOwnershipLabels
import org.goldenport.cncf.unitofwork.UnitOfWorkOp

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
private[cncf] object DockerOperationalObservationProvider extends OperationalObservationProvider {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  val states: Set[String] = Set("created", "running", "restarting", "paused", "exited", "dead", "removing")
  def readC(
    source: ObservationSource, request: ObservationRead, effects: UnitOfWorkOp ~> Consequence
  ): AxisObservation = source match {
    case selected: ObservationSource.Docker =>
      // Registry lookup is a read of the existing owner, never resolveC or lazy runtime creation.
      val runtime = OperationalObservationRuntime.component(request.context.cncfCore.scope)
        .flatMap(_.subsystem).flatMap(_.serviceContainerRuntime(using request.context))
      val ownership = selected.selector match {
        case DockerObservationSelector.RuntimeOwned(key) =>
          runtime.toRight(ObservationReason.SourceUnavailable).flatMap(
            _.observationDefinitionC(key).toOption.toRight(ObservationReason.PermissionDenied)).map(_ => ())
        case _ => Right(())
      }
      val sampled = request.now
      val result = ownership.flatMap { _ =>
        OperationalObservationProvider.fetchC(url(selected), request, effects).flatMap { response =>
          if (response.code < 200 || response.code >= 300) Left(
            if (response.code == 401 || response.code == 403) ObservationReason.PermissionDenied else ObservationReason.SourceUnavailable)
          else for {
            text <- response.getString.toRight(ObservationReason.InvalidSource)
            json <- _parser.parse(text).left.map(_ => ObservationReason.InvalidSource)
            value <- decode(selected, json, sampled, item => selected.selector match {
              case DockerObservationSelector.RuntimeOwned(key) =>
                runtime.exists(_.validateDeploymentObservationC(key, item).isSuccess)
              case _ => true
            })
          } yield value
        }
      }
      result.fold(reason => AxisObservation.Unavailable(reason, Some(selected.sourceId)), _.currentAt(request.now))
    case _ => AxisObservation.Unavailable(ObservationReason.Unsupported, Some(source.sourceId))
  }

  private[operationalobservation] def url(source: ObservationSource.Docker): String = {
    val filters = source.selector match {
      case DockerObservationSelector.Name(name) =>
        Json.obj("name" -> Json.arr(Json.fromString("^/" + java.util.regex.Pattern.quote(name) + "$")))
      case DockerObservationSelector.Compose(project, service) =>
        Json.obj("label" -> Json.arr(Json.fromString("com.docker.compose.project=" + project),
          Json.fromString("com.docker.compose.service=" + service)))
      case DockerObservationSelector.RuntimeOwned(key) =>
        Json.obj("label" -> Json.fromValues(Vector(
          ServiceContainerOwnershipLabels.MANAGED -> "true", ServiceContainerOwnershipLabels.OWNER_KIND -> key.owner.kind.name,
          ServiceContainerOwnershipLabels.OWNER_ID -> key.owner.id.print,
          ServiceContainerOwnershipLabels.SERVICE_ID -> key.serviceId.print
        ).map { case (k, v) => Json.fromString(k + "=" + v) }))
    }
    source.baseUrl.stripSuffix("/") + "/containers/json?all=1&filters=" +
      URLEncoder.encode(filters.noSpaces, StandardCharsets.UTF_8)
  }

  private[operationalobservation] def decode(
    source: ObservationSource.Docker, json: Json, sampled: Instant, compatible: Json => Boolean
  ): Either[ObservationReason, AxisObservation] = {
    def _matches_(item: Json): Boolean = {
      val labels = item.hcursor.get[Map[String, String]]("Labels").toOption
      source.selector match {
        case DockerObservationSelector.Name(name) =>
          item.hcursor.get[Vector[String]]("Names").toOption.exists(_.contains("/" + name))
        case DockerObservationSelector.Compose(project, service) => labels.exists(values =>
          values.get("com.docker.compose.project").contains(project) && values.get("com.docker.compose.service").contains(service))
        case DockerObservationSelector.RuntimeOwned(key) => labels.exists(values =>
          values.get(ServiceContainerOwnershipLabels.MANAGED).contains("true") &&
          values.get(ServiceContainerOwnershipLabels.OWNER_KIND).contains(key.owner.kind.name) &&
          values.get(ServiceContainerOwnershipLabels.OWNER_ID).contains(key.owner.id.print) &&
          values.get(ServiceContainerOwnershipLabels.SERVICE_ID).contains(key.serviceId.print))
      }
    }
    json.asArray.toRight(ObservationReason.InvalidSource).flatMap { items =>
      // An exact lookup returning an out-of-selector result is invalid, never absence.
      if (items.exists(item => !_matches_(item))) Left(ObservationReason.InvalidSource)
      else items match {
        case Vector() => Right(AxisObservation.Observed("absent", source.sourceId, sampled, sampled.plusMillis(source.freshnessMillis)))
        case Vector(item) => for {
          _ <- Either.cond(compatible(item), (), ObservationReason.PermissionDenied)
          state <- item.hcursor.get[String]("State").toOption.filter(states).toRight(ObservationReason.Unsupported)
        } yield AxisObservation.Observed(state, source.sourceId, sampled, sampled.plusMillis(source.freshnessMillis))
        case _ => Left(ObservationReason.AmbiguousTarget)
      }
    }
  }
}
