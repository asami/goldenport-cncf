package org.goldenport.cncf.knowledge

import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence

/*
 * Strict deterministic JSON codec for the Component knowledge carrier. The
 * carrier is declaration-only and never reads or returns consumer content.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentKnowledgeCarrierCodec {
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)
  private val _fields = Set("carrierSchema", "consumerContractSchema", "logicalPath", "sha256")

  def decodeC(json: String): Consequence[ComponentKnowledgeCarrier] =
    _decode(json).fold(Consequence.argumentInvalid, Consequence.success)

  def encode(value: ComponentKnowledgeCarrier): String = {
    ComponentKnowledgeCarrier.validateC(value).toOption match {
      case Some(_) => _json(value).noSpaces
      case None => throw new IllegalArgumentException("Component knowledge carrier failed codec self-validation")
    }
  }

  private def _decode(text: String): Either[String, ComponentKnowledgeCarrier] =
    try {
      for {
        json <- _strict_json_parser.parse(text).left.map(error => s"Invalid Component knowledge carrier JSON: ${error.message}")
        root <- json.asObject.toRight("Component knowledge carrier must be a JSON object")
        _ <- Either.cond(root.keys.toSet == _fields, (), "Component knowledge carrier must contain exactly carrierSchema, consumerContractSchema, logicalPath, and sha256")
        carrierSchema <- _string(root, "carrierSchema")
        consumerContractSchema <- _string(root, "consumerContractSchema")
        logicalPath <- _string(root, "logicalPath")
        sha256 <- _string(root, "sha256")
        value = ComponentKnowledgeCarrier(carrierSchema, consumerContractSchema, logicalPath, sha256)
        _ <- ComponentKnowledgeCarrier.validateC(value).toOption.toRight("Component knowledge carrier violates v1 validation")
      } yield value
    } catch {
      case NonFatal(error) => Left(s"Invalid Component knowledge carrier: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _string(root: JsonObject, field: String): Either[String, String] =
    root(field).flatMap(_.asString).toRight(s"Component knowledge carrier.$field must be a string")

  private def _json(value: ComponentKnowledgeCarrier): Json =
    Json.obj(
      "carrierSchema" -> Json.fromString(value.carrierSchema),
      "consumerContractSchema" -> Json.fromString(value.consumerContractSchema),
      "logicalPath" -> Json.fromString(value.logicalPath),
      "sha256" -> Json.fromString(value.sha256)
    )
}
