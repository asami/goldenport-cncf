package org.goldenport.cncf.http

import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec}

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class RuntimeHttpExecutionPlan private (
  val baseUrl: String,
  val limits: HttpExecutionLimits,
  val grants: Map[ComponentId, HttpExecutionGrant],
  val admission: HttpExecutionAdmission
)

private[cncf] object RuntimeHttpExecutionPlan {
  def createC(
    baseUrl: String,
    limits: HttpExecutionLimits,
    grants: Map[ComponentId, HttpExecutionGrant]
  ): Consequence[RuntimeHttpExecutionPlan] =
    HttpExecutionAdmission.fromRuntimeC(baseUrl, limits, grants).map { admission =>
      new RuntimeHttpExecutionPlan(admission.baseUrl, admission.limits, admission.grants, admission)
    }
}

private[cncf] object RuntimeHttpExecutionPlanCodec extends ConfigurationValueCodec[RuntimeHttpExecutionPlan] {
  val SCHEMA = "cncf.runtime-http-execution.v1"
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _invalid = "Invalid runtime HTTP declaration"

  def decode(value: ConfigurationValue): Consequence[RuntimeHttpExecutionPlan] = value match {
    case ConfigurationValue.StringValue(text) =>
      val result = for {
        json <- try _parser.parse(text).left.map(_ => _invalid)
          catch { case NonFatal(_) => Left(_invalid) }
        root <- _fields(json, Set("schema", "baseUrl", "limits", "grants"))
        schema <- _string(root, "schema")
        _ <- Either.cond(schema == SCHEMA, (), _invalid)
        base <- _string(root, "baseUrl")
        limits <- _limits(root("limits").get)
        values <- _array(root, "grants")
        grants <- _sequence(values.map(_grant))
        _ <- Either.cond(grants.map(_._1).distinct.size == grants.size, (), _invalid)
        plan <- _valid(RuntimeHttpExecutionPlan.createC(base, limits, grants.toMap))
      } yield plan
      result.fold(Consequence.configurationInvalid, Consequence.success)
    case _ => Consequence.configurationInvalid("Runtime HTTP plan requires a JSON string")
  }

  def encode(value: RuntimeHttpExecutionPlan): Consequence[ConfigurationValue] = {
    val grants = value.grants.toVector.sortBy(_._1.name).map { case (owner, grant) => Json.obj(
      "componentId" -> Json.fromString(owner.name),
      "origins" -> Json.fromValues(grant.origins.sorted.map(Json.fromString)),
      "methods" -> Json.fromValues(grant.methods.toVector.map(_method).sorted.map(Json.fromString))) }
    val json = Json.obj(
      "schema" -> Json.fromString(SCHEMA),
      "baseUrl" -> Json.fromString(value.baseUrl),
      "limits" -> Json.obj(
        "connectTimeoutMillis" -> Json.fromInt(value.limits.connectTimeoutMillis),
        "readTimeoutMillis" -> Json.fromInt(value.limits.readTimeoutMillis),
        "responseBytes" -> Json.fromLong(value.limits.responseBytes)),
      "grants" -> Json.fromValues(grants))
    Consequence.success(ConfigurationValue.StringValue(json.noSpaces))
  }

  private def _grant(json: Json): Either[String, (ComponentId, HttpExecutionGrant)] = for {
    obj <- _fields(json, Set("componentId", "origins", "methods"))
    ownertext <- _string(obj, "componentId")
    owner <- _valid(ComponentId.parseC(ownertext))
    origins <- _strings(obj, "origins")
    texts <- _strings(obj, "methods")
    methods <- _sequence(texts.map {
      case "GET" => Right(HttpExecutionMethod.Get)
      case "POST" => Right(HttpExecutionMethod.Post)
      case "PUT" => Right(HttpExecutionMethod.Put)
      case _ => Left(_invalid)
    })
    _ <- Either.cond(methods.distinct.size == methods.size, (), _invalid)
  } yield owner -> HttpExecutionGrant(origins, methods.toSet)

  private def _limits(json: Json): Either[String, HttpExecutionLimits] = for {
    obj <- _fields(json, Set("connectTimeoutMillis", "readTimeoutMillis", "responseBytes"))
    connect <- obj("connectTimeoutMillis").flatMap(_.asNumber).flatMap(_.toInt).filter(_ > 0).toRight(_invalid)
    read <- obj("readTimeoutMillis").flatMap(_.asNumber).flatMap(_.toInt).filter(_ > 0).toRight(_invalid)
    bytes <- obj("responseBytes").flatMap(_.asNumber).flatMap(_.toLong).filter(_ > 0).toRight(_invalid)
  } yield HttpExecutionLimits(connect, read, bytes)

  private def _method(method: HttpExecutionMethod): String = method match {
    case HttpExecutionMethod.Get => "GET"
    case HttpExecutionMethod.Post => "POST"
    case HttpExecutionMethod.Put => "PUT"
  }

  private def _fields(json: Json, names: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == names).toRight(_invalid)
  private def _string(obj: JsonObject, name: String): Either[String, String] =
    obj(name).flatMap(_.asString).toRight(_invalid)
  private def _array(obj: JsonObject, name: String): Either[String, Vector[Json]] =
    obj(name).flatMap(_.asArray).toRight(_invalid)
  private def _strings(obj: JsonObject, name: String): Either[String, Vector[String]] =
    _array(obj, name).flatMap(xs => _sequence(xs.map(_.asString.toRight(_invalid))))
  private def _valid[A](value: Consequence[A]): Either[String, A] = value.toOption.toRight(_invalid)
  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, next) => for {
      prior <- acc
      value <- next
    } yield prior :+ value }
}
