package org.goldenport.cncf.display

import io.circe.Json
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.protocol.spec.ParameterDefinition
import scala.util.control.NonFatal

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** A closed, standalone descriptor protocol; decoding never constructs capabilities. */
object DisplayBusinessOperationCodec {
  import DisplayBusinessOperation.*
  val schema: String = "cncf.display-business-operation-descriptor.v1"
  val version: Int = 1
  private val _parser = JawnParser(allowDuplicateKeys = false)

  def encodeJsonC(registry: Registry, resourceType: String, screenId: String): Consequence[Json] =
    if (registry == null) _input_invalid_c("ELEMENT_INVALID", "registry")
    else registry.describeC(resourceType, screenId).map(_payload)
  def encodeC(registry: Registry, resourceType: String, screenId: String): Consequence[String] =
    encodeJsonC(registry, resourceType, screenId).map(DisplayMutationJson._render)

  def decodeC(registry: Registry, json: String): Consequence[Descriptor] = {
    if (registry == null || json == null) _input_invalid_c("ELEMENT_INVALID", "descriptor")
    else for {
      parsed <- _parse_c(json)
      _ <- if (parsed.hcursor.get[String]("schema").toOption.contains(schema) &&
          parsed.hcursor.downField("version").focus.flatMap(_.asNumber).exists(_.toString == version.toString))
        Consequence.unit else _input_invalid_c[Unit]("PROTOCOL_INVALID", "$.schema/version")
      resource <- _string_c(parsed, "resourceType", "$")
      definition <- parsed.hcursor.downField("definition").focus.map(Consequence.success)
        .getOrElse(_input_invalid_c("ELEMENT_INVALID", "$.definition"))
      screen <- _string_c(definition, "screenId", "$.definition")
      canonical <- registry.describeC(resource, screen)
      _ <- _correspondence_c(parsed, _payload(canonical), "$")
    } yield canonical
  }

  private def _parse_c(json: String): Consequence[Json] = {
    try _parser.parse(json).fold(_ => _input_invalid_c("ELEMENT_INVALID", "$"), Consequence.success)
    catch { case NonFatal(_) => _input_invalid_c("ELEMENT_INVALID", "$") }
  }
  private def _string_c(json: Json, name: String, path: String): Consequence[String] =
    json.hcursor.get[String](name).toOption.map(Consequence.success)
      .getOrElse(_input_invalid_c("ELEMENT_INVALID", path + "." + name))

  /** Compare closed shape and every scalar against the admitted oracle, with trusted paths only. */
  private def _correspondence_c(candidate: Json, canonical: Json, path: String): Consequence[Unit] = {
    canonical.asObject match {
      case Some(expected) => candidate.asObject match {
        case Some(actual) if actual.keys.toSet == expected.keys.toSet =>
          expected.toVector.foldLeft(Consequence.unit) { case (result, (name, value)) =>
            result.flatMap(_ => _correspondence_c(actual(name).get, value, path + "." + name))
          }
        case _ => _input_invalid_c("ELEMENT_INVALID", path)
      }
      case None => canonical.asArray match {
        case Some(expected) => candidate.asArray match {
          case Some(actual) if actual.size == expected.size =>
            expected.indices.foldLeft(Consequence.unit) { (result, index) =>
              result.flatMap(_ => _correspondence_c(actual(index), expected(index), s"$path[$index]"))
            }
          case _ => _input_invalid_c("SELECTION_MISMATCH", path)
        }
        case None => if (candidate == canonical) Consequence.unit else _input_invalid_c("SELECTION_MISMATCH", path)
      }
    }
  }
  private def _text(value: String): Json = Json.fromString(value)
  private def _kind(kind: ParameterDefinition.Kind): String = kind match {
    case ParameterDefinition.Kind.Argument => "argument"
    case ParameterDefinition.Kind.Property => "property"
    case ParameterDefinition.Kind.Switch => "switch"
  }
  private def _payload(descriptor: Descriptor): Json = {
    val definition = descriptor.definition
    val provenance = definition.provenance
    Json.obj("schema" -> _text(schema), "version" -> Json.fromInt(version),
      "componentId" -> _text(descriptor.source.componentId.name), "resourceType" -> _text(descriptor.source.resourceType),
      "definition" -> Json.obj("screenId" -> _text(definition.screenId), "revision" -> _text(definition.definitionRevision)),
      "provenance" -> Json.obj("producerId" -> _text(provenance.producerId), "sourceId" -> _text(provenance.sourceId),
        "revision" -> _text(provenance.revision)),
      "actions" -> Json.fromValues(descriptor.actions.map { action =>
        Json.obj("scope" -> _text(action.scope.tag), "id" -> _text(action.id), "label" -> _text(action.label),
          "bindingId" -> _text(action.bindingId),
          "operation" -> Json.obj("componentId" -> _text(action.target.componentId.name),
            "service" -> _text(action.target.service), "operation" -> _text(action.target.operation)),
          "input" -> Json.fromValues(action.parameters.map { parameter =>
            Json.obj("name" -> _text(parameter.name), "kind" -> _text(_kind(parameter.kind)),
              "datatype" -> _text(parameter.datatype), "multiplicity" -> _text(parameter.multiplicity))
          }))
      }))
  }
}
