package org.goldenport.cncf.knowledge

import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity

/*
 * Deterministic codec for portable model and diagram context references. It
 * decodes only identities already present in the enclosing manifest resources.
 *
 * @since   Aug. 24, 2026
 * @version Aug. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object PortableModelResourceContextCodec {
  private[knowledge] def _decode_json(
    json: Json,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, PortableModelResourceContext] =
    try {
      for {
        obj <- _object(json, context)
        modelsjson <- _field(obj, "models", context)
        models <- _array(modelsjson, s"$context.models").flatMap(_models(_, resources, s"$context.models"))
        diagramsjson <- _field(obj, "diagrams", context)
        diagrams <- _array(diagramsjson, s"$context.diagrams").flatMap(_diagrams(_, resources, s"$context.diagrams"))
        value = PortableModelResourceContext(
          models = models,
          diagrams = diagrams,
          extensions = _extensions(obj, Set("models", "diagrams"))
        )
        _ <- PortableModelResourceContext.validateC(value, resources).toOption.toRight(s"$context violates portable model resource validation")
      } yield value
    } catch {
      case NonFatal(error) => Left(s"Invalid portable model resource context: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private[knowledge] def _encode_json(context: PortableModelResourceContext): Json =
    _json_object(
      Vector(
        "models" -> Json.arr(context.models.sortBy(value => _identity_order(value.entry.binding.logicalIdentity)).map(_model_json)*),
        "diagrams" -> Json.arr(context.diagrams.sortBy(value => _identity_order(value.entry.binding.logicalIdentity)).map(_diagram_json)*)
      ),
      context.extensions
    )

  private def _models(
    values: Vector[Json],
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Vector[PortableModelResource]] =
    _sequence(values.zipWithIndex.map { case (value, index) => _model(value, resources, s"$context[$index]") })

  private def _diagrams(
    values: Vector[Json],
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, Vector[PortableDiagramResource]] =
    _sequence(values.zipWithIndex.map { case (value, index) => _diagram(value, resources, s"$context[$index]") })

  private def _model(
    json: Json,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, PortableModelResource] =
    for {
      obj <- _object(json, context)
      identityjson <- _field(obj, "logicalIdentity", context)
      identity <- _identity(identityjson, s"$context.logicalIdentity")
      entry <- _resource_for(identity, resources, s"$context.logicalIdentity")
    } yield PortableModelResource(entry, _extensions(obj, Set("logicalIdentity")))

  private def _diagram(
    json: Json,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, PortableDiagramResource] =
    for {
      obj <- _object(json, context)
      identityjson <- _field(obj, "logicalIdentity", context)
      identity <- _identity(identityjson, s"$context.logicalIdentity")
      entry <- _resource_for(identity, resources, s"$context.logicalIdentity")
      generatedjson <- _field(obj, "generatedFrom", context)
      generated <- _array(generatedjson, s"$context.generatedFrom").flatMap(_generated_from(_, s"$context.generatedFrom"))
    } yield PortableDiagramResource(entry, generated, _extensions(obj, Set("logicalIdentity", "generatedFrom")))

  private def _generated_from(values: Vector[Json], context: String): Either[String, Vector[PortableDiagramGeneratedFrom]] =
    _sequence(values.zipWithIndex.map { case (value, index) =>
      for {
        obj <- _object(value, s"$context[$index]")
        identityjson <- _field(obj, "sourceIdentity", s"$context[$index]")
        identity <- _identity(identityjson, s"$context[$index].sourceIdentity")
        digest <- _string(obj, "sourceSha256", s"$context[$index]")
      } yield PortableDiagramGeneratedFrom(identity, digest, _extensions(obj, Set("sourceIdentity", "sourceSha256")))
    })

  private def _identity(json: Json, context: String): Either[String, ComponentResourceLogicalIdentity] =
    for {
      obj <- _object(json, context)
      _ <- Either.cond(
        obj.keys.toSet == Set("componentId", "logicalRelease", "parentComponentId", "childRole", "logicalResource"),
        (),
        s"$context must contain only a canonical logical identity"
      )
      componentid <- _component_id(obj, "componentId", context)
      release <- _string(obj, "logicalRelease", context)
      parentid <- _optional_component_id(obj, "parentComponentId", context)
      childrole <- _string(obj, "childRole", context)
      logicalresource <- _string(obj, "logicalResource", context)
    } yield ComponentResourceLogicalIdentity(componentid, release, parentid, childrole, logicalresource)

  private def _resource_for(
    identity: ComponentResourceLogicalIdentity,
    resources: Vector[ComponentKnowledgeResourceEntry],
    context: String
  ): Either[String, ComponentKnowledgeResourceEntry] =
    resources.filter(_.binding.logicalIdentity == identity) match {
      case Vector(entry) => Right(entry)
      case Vector() => Left(s"$context must identify exactly one existing manifest resource")
      case _ => Left(s"$context matches more than one manifest resource")
    }

  private def _model_json(value: PortableModelResource): Json =
    _json_object(Vector("logicalIdentity" -> _identity_json(value.entry.binding.logicalIdentity)), value.extensions)

  private def _diagram_json(value: PortableDiagramResource): Json =
    _json_object(
      Vector(
        "logicalIdentity" -> _identity_json(value.entry.binding.logicalIdentity),
        "generatedFrom" -> Json.arr(value.generatedFrom.sortBy(source => _identity_order(source.sourceIdentity)).map(_generated_from_json)*)
      ),
      value.extensions
    )

  private def _generated_from_json(value: PortableDiagramGeneratedFrom): Json =
    _json_object(
      Vector(
        "sourceIdentity" -> _identity_json(value.sourceIdentity),
        "sourceSha256" -> Json.fromString(value.sourceSha256)
      ),
      value.extensions
    )

  private def _identity_json(value: ComponentResourceLogicalIdentity): Json =
    Json.obj(
      "componentId" -> Json.fromString(value.componentId.name),
      "logicalRelease" -> Json.fromString(value.logicalRelease),
      "parentComponentId" -> value.parentComponentId.map(id => Json.fromString(id.name)).getOrElse(Json.Null),
      "childRole" -> Json.fromString(value.childRole),
      "logicalResource" -> Json.fromString(value.logicalResource)
    )

  private def _identity_order(value: ComponentResourceLogicalIdentity): (String, String, String, String, String) =
    (value.componentId.name, value.logicalRelease, value.parentComponentId.map(_.name).getOrElse(""), value.childRole, value.logicalResource)

  private def _component_id(obj: JsonObject, field: String, context: String): Either[String, ComponentId] =
    _string(obj, field, context).flatMap { value =>
      ComponentId.parseC(value).toOption match {
        case Some(id) if id.name == value => Right(id)
        case _ => Left(s"$context.$field must be a canonical ComponentId")
      }
    }

  private def _optional_component_id(obj: JsonObject, field: String, context: String): Either[String, Option[ComponentId]] =
    obj(field) match {
      case None => Right(None)
      case Some(value) if value.isNull => Right(None)
      case Some(value) =>
        value.asString.toRight(s"$context.$field must be a ComponentId, null, or absent").flatMap { text =>
          ComponentId.parseC(text).toOption match {
            case Some(id) if id.name == text => Right(Some(id))
            case _ => Left(s"$context.$field must be a canonical ComponentId")
          }
        }
    }

  private def _object(json: Json, context: String): Either[String, JsonObject] =
    json.asObject.toRight(s"$context must be an object")

  private def _array(json: Json, context: String): Either[String, Vector[Json]] =
    json.asArray.map(_.toVector).toRight(s"$context must be an array")

  private def _field(obj: JsonObject, field: String, context: String): Either[String, Json] =
    obj(field).toRight(s"$context requires $field")

  private def _string(obj: JsonObject, field: String, context: String): Either[String, String] =
    _field(obj, field, context).flatMap(_.asString.toRight(s"$context.$field must be a string"))

  private def _extensions(obj: JsonObject, known: Set[String]): Map[String, Json] =
    obj.toMap.filterNot { case (key, _) => known.contains(key) }

  private def _json_object(known: Vector[(String, Json)], extensions: Map[String, Json]): Json = {
    val knownkeys = known.map(_._1).toSet
    val unknown = extensions.iterator.filterNot { case (key, _) => knownkeys.contains(key) }.toVector.sortBy(_._1)
    Json.obj((known ++ unknown).map { case (key, value) => key -> _canonical_json(value) }*)
  }

  private def _canonical_json(json: Json): Json =
    json.arrayOrObject(
      json,
      values => Json.arr(values.map(_canonical_json)*),
      obj => Json.fromJsonObject(JsonObject.fromIterable(obj.toIterable.toVector.sortBy(_._1).map { case (key, value) => key -> _canonical_json(value) }))
    )

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }
}
