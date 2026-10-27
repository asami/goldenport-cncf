package org.goldenport.cncf.operationalobservation

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec}
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.servicecontainer.*

/*
 * @since   Oct. 24, 2026
 * @version Oct. 27, 2026
 */
object OperationalObservationPlanCodec extends ConfigurationValueCodec[OperationalObservationPlan] {
  val SCHEMA = "cncf.operational-observation.v1"
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _invalid = "Invalid operational observation declaration"
  def decode(value: ConfigurationValue): Consequence[OperationalObservationPlan] = value match {
    case ConfigurationValue.StringValue(text) if text.length <= 262144 =>
      val result = for {
        json <- _parser.parse(text).left.map(_ => _invalid)
        root <- _fields(json, Set("schema", "profile", "owners", "limits", "sources", "targets"))
        schema <- _string(root, "schema")
        _ <- Either.cond(schema == SCHEMA, (), _invalid)
        profile <- _string(root, "profile")
        ownertexts <- _strings(root, "owners")
        owners <- _sequence(ownertexts.map(x => _valid(ComponentId.parseC(x))))
        limits <- _limits(root("limits").get)
        sourcejson <- _array(root, "sources")
        _ <- Either.cond(sourcejson.size <= 8, (), _invalid)
        sources <- _sequence(sourcejson.map(_source))
        targetjson <- _array(root, "targets")
        _ <- Either.cond(targetjson.size <= 100, (), _invalid)
        targets <- _sequence(targetjson.map(_target))
        plan <- _valid(OperationalObservationPlan(profile, owners, limits, sources, targets).validateC)
      } yield plan
      result.fold(Consequence.configurationInvalid, Consequence.success)
    case _ => Consequence.configurationInvalid(_invalid)
  }
  def encode(plan: OperationalObservationPlan): Consequence[ConfigurationValue] = plan.validateC.map { value =>
    ConfigurationValue.StringValue(Json.obj(
      "schema" -> Json.fromString(SCHEMA), "profile" -> Json.fromString(value.profile),
      "owners" -> Json.fromValues(value.owners.map(x => Json.fromString(x.name))),
      "limits" -> Json.obj("operationMillis" -> Json.fromInt(value.limits.operationMillis),
        "sourceMillis" -> Json.fromInt(value.limits.sourceMillis),
        "sourceBytes" -> Json.fromInt(value.limits.sourceBytes), "responseBytes" -> Json.fromInt(value.limits.responseBytes)),
      "sources" -> Json.fromValues(value.sources.map(_source_json)),
      "targets" -> Json.fromValues(value.targets.map { t =>
        val fields = Vector(
          "targetId" -> Json.fromString(t.targetId), "service" -> Json.fromString(t.service),
          "instance" -> Json.fromString(t.instance), "kind" -> Json.fromString(t.kind),
          "enabled" -> Json.fromBoolean(t.enabled),
          "sources" -> Json.obj(t.sources.toVector.sortBy(_._1.name).map { case (axis, id) => axis.name -> Json.fromString(id) }*)
        ) ++ t.runtimeAssociation.toVector.map(id => "runtimeAssociation" -> Json.obj(
          "componentId" -> Json.fromString(id.componentId.name), "instance" -> Json.fromString(id.instance)))
        Json.obj(fields*)
      })
    ).noSpaces)
  }
  private def _target(json: Json): Either[String, ObservationTarget] = for {
    obj <- _fields(json, Set("targetId", "service", "instance", "kind", "enabled", "sources"), Set("runtimeAssociation"))
    id <- _string(obj, "targetId")
    service <- _string(obj, "service")
    instance <- _string(obj, "instance")
    kind <- _string(obj, "kind")
    enabled <- obj("enabled").flatMap(_.asBoolean).toRight(_invalid)
    sourceobj <- obj("sources").flatMap(_.asObject).toRight(_invalid)
    sources <- _sequence(sourceobj.toVector.map { case (name, raw) => for {
      axis <- _axis(name)
      value <- raw.asString.toRight(_invalid)
    } yield axis -> value })
    association <- obj("runtimeAssociation") match {
      case None => Right(None)
      case Some(raw) => for {
        fields <- _fields(raw, Set("componentId", "instance"))
        owner <- _string(fields, "componentId").flatMap(x => _valid(ComponentId.parseC(x)))
        label <- _string(fields, "instance")
        id <- _valid(ComponentInstanceId.createC(owner, label))
      } yield Some(id)
    }
  } yield ObservationTarget(id, service, instance, kind, enabled, sources.toMap, association)

  private def _source(json: Json): Either[String, ObservationSource] = {
    val common = Set("sourceId", "type", "freshnessMillis")
    for {
      kind <- json.hcursor.get[String]("type").left.map(_ => _invalid)
      obj <- _fields(json, common ++ (kind match {
        case "docker" => Set("baseUrl", "selector")
        case "http" => Set("url", "axis", "scope", "vocabulary", "sampleMode")
        case _ => Set.empty[String]
      }))
      id <- _string(obj, "sourceId")
      ttl <- obj("freshnessMillis").flatMap(_.asNumber).flatMap(_.toLong).toRight(_invalid)
      source <- kind match {
        case "docker" => for {
          base <- _string(obj, "baseUrl")
          selector <- _selector(obj("selector").get)
        } yield ObservationSource.Docker(id, base, selector, ttl)
        case "http" => for {
          url <- _string(obj, "url")
          axis <- _string(obj, "axis").flatMap(_axis)
          scope <- _string(obj, "scope")
          vocabulary <- _strings(obj, "vocabulary")
          mode <- _string(obj, "sampleMode")
        } yield ObservationSource.Http(id, url, axis, scope, vocabulary, mode, ttl)
        case _ => Left(_invalid)
      }
    } yield source
  }
  private def _selector(json: Json): Either[String, DockerObservationSelector] =
    json.hcursor.get[String]("type").left.map(_ => _invalid).flatMap {
      case "name" => _fields(json, Set("type", "name")).flatMap(_string(_, "name")).map(DockerObservationSelector.Name.apply)
      case "compose" => for {
        obj <- _fields(json, Set("type", "project", "service"))
        project <- _string(obj, "project")
        service <- _string(obj, "service")
      } yield DockerObservationSelector.Compose(project, service)
      case "runtime-owned" => for {
        obj <- _fields(json, Set("type", "ownerKind", "ownerId", "serviceId"))
        kindtext <- _string(obj, "ownerKind")
        kind <- ServiceContainerOwnerKind.values.find(_.name == kindtext).toRight(_invalid)
        owner <- _string(obj, "ownerId").flatMap(x => _valid(ServiceContainerOwnerId.parseC(x)))
        service <- _string(obj, "serviceId").flatMap(x => _valid(ServiceContainerId.parseC(x)))
      } yield DockerObservationSelector.RuntimeOwned(ServiceContainerRegistryKey(ServiceContainerOwner(kind, owner), service))
      case _ => Left(_invalid)
    }

  private def _source_json(source: ObservationSource): Json = {
    val common = Vector("sourceId" -> Json.fromString(source.sourceId),
      "freshnessMillis" -> Json.fromLong(source.freshnessMillis))
    val fields = source match {
      case x: ObservationSource.Docker =>
        val selector = x.selector match {
          case DockerObservationSelector.Name(name) => Json.obj("type" -> Json.fromString("name"), "name" -> Json.fromString(name))
          case DockerObservationSelector.Compose(project, service) => Json.obj("type" -> Json.fromString("compose"),
            "project" -> Json.fromString(project), "service" -> Json.fromString(service))
          case DockerObservationSelector.RuntimeOwned(key) => Json.obj("type" -> Json.fromString("runtime-owned"),
            "ownerKind" -> Json.fromString(key.owner.kind.name), "ownerId" -> Json.fromString(key.owner.id.print),
            "serviceId" -> Json.fromString(key.serviceId.print))
        }
        Vector("type" -> Json.fromString("docker"), "baseUrl" -> Json.fromString(x.baseUrl), "selector" -> selector)
      case x: ObservationSource.Http => Vector("type" -> Json.fromString("http"), "url" -> Json.fromString(x.url),
        "axis" -> Json.fromString(x.axis.name), "scope" -> Json.fromString(x.scope),
        "vocabulary" -> Json.fromValues(x.vocabulary.map(Json.fromString)), "sampleMode" -> Json.fromString(x.sampleMode))
    }
    Json.obj((common ++ fields)*)
  }
  private def _limits(json: Json): Either[String, ObservationLimits] = for {
    obj <- _fields(json, Set("operationMillis", "sourceMillis", "sourceBytes", "responseBytes"))
    operation <- _int(obj, "operationMillis")
    source <- _int(obj, "sourceMillis")
    bytes <- _int(obj, "sourceBytes")
    response <- _int(obj, "responseBytes")
  } yield ObservationLimits(operation, source, bytes, response)
  private def _fields(json: Json, required: Set[String], optional: Set[String] = Set.empty): Either[String, JsonObject] =
    json.asObject.filter(x => required.subsetOf(x.keys.toSet) && x.keys.toSet.subsetOf(required ++ optional)).toRight(_invalid)
  private def _string(obj: JsonObject, name: String): Either[String, String] = obj(name).flatMap(_.asString).toRight(_invalid)
  private def _array(obj: JsonObject, name: String): Either[String, Vector[Json]] = obj(name).flatMap(_.asArray).toRight(_invalid)
  private def _strings(obj: JsonObject, name: String): Either[String, Vector[String]] =
    _array(obj, name).flatMap(xs => _sequence(xs.map(_.asString.toRight(_invalid))))
  private def _int(obj: JsonObject, name: String): Either[String, Int] =
    obj(name).flatMap(_.asNumber).flatMap(_.toInt).toRight(_invalid)
  private def _axis(text: String): Either[String, ObservationAxis] =
    ObservationAxis.values.find(_.name == text).toRight(_invalid)
  private def _valid[A](value: Consequence[A]): Either[String, A] = value.toOption.toRight(_invalid)
  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, next) => for { xs <- acc; x <- next } yield xs :+ x }
}
