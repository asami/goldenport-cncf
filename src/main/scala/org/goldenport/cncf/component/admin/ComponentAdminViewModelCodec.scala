package org.goldenport.cncf.component.admin

import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.repository.ComponentResourceLogicalIdentity

/*
 * Strict deterministic codec for the internal Component Admin v1 view model.
 * It serializes safe logical provenance only and performs no I/O or resolution.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentAdminViewModelCodec {
  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)
  private val _root_fields = Set(
    "schema", "componentClass", "selectedLogicalRelease", "logicalReleaseCandidates",
    "selectedLoadedInstance", "loadedInstanceCandidates", "subsystemClass",
    "subsystemInstance", "implicitComponentSubsystem", "resourceState"
  )
  private val _field_fields = Set("value", "provenance")
  private val _provenance_fields = Set("sourceKind", "logicalIdentity")
  private val _component_class_fields = Set("componentId")
  private val _release_fields = Set("componentClass", "release")
  private val _loaded_instance_fields = Set("componentClass", "instanceId")
  private val _subsystem_class_fields = Set("name")
  private val _subsystem_instance_fields = Set("subsystemClass", "instance")
  private val _implicit_subsystem_fields = Set("componentClass", "name")
  private val _logical_identity_fields = Set("componentId", "logicalRelease", "parentComponentId", "childRole", "logicalResource")

  def decodeC(json: String): Consequence[ComponentAdminViewModel] =
    _decode(json).fold(Consequence.argumentInvalid, Consequence.success)

  def encode(value: ComponentAdminViewModel): String =
    ComponentAdminViewModel.validateC(value).toOption match {
      case Some(_) => _json(value).noSpaces
      case None => throw new IllegalArgumentException("Component Admin view model failed codec self-validation")
    }

  private def _decode(text: String): Either[String, ComponentAdminViewModel] =
    try {
      for {
        json <- _strict_json_parser.parse(text).left.map(error => s"Invalid Component Admin view model JSON: ${error.message}")
        root <- _object(json, "view")
        _ <- _exact_fields(root, _root_fields, "view")
        schema <- _string(root, "schema", "view")
        _ <- Either.cond(schema == ComponentAdminViewModel.SCHEMA, (), s"Unsupported Component Admin view model schema: $schema")
        componentclass <- _component_class_field(_required(root, "componentClass", "view"), "view.componentClass")
        selectedrelease <- _logical_release_field(_required(root, "selectedLogicalRelease", "view"), "view.selectedLogicalRelease")
        releasecandidates <- _array(_required(root, "logicalReleaseCandidates", "view"), "view.logicalReleaseCandidates").flatMap(_logical_release_candidates)
        selectedinstance <- _loaded_instance_field(_required(root, "selectedLoadedInstance", "view"), "view.selectedLoadedInstance")
        instancecandidates <- _array(_required(root, "loadedInstanceCandidates", "view"), "view.loadedInstanceCandidates").flatMap(_loaded_instance_candidates)
        subsystemclass <- _subsystem_class_field(_required(root, "subsystemClass", "view"), "view.subsystemClass")
        subsysteminstance <- _subsystem_instance_field(_required(root, "subsystemInstance", "view"), "view.subsystemInstance")
        implicitsubsystem <- _implicit_subsystem_field(_required(root, "implicitComponentSubsystem", "view"), "view.implicitComponentSubsystem")
        resourcestate <- _resource_state_field(_required(root, "resourceState", "view"), "view.resourceState")
        value <- ComponentAdminViewModel.createC(
          componentclass,
          selectedrelease,
          releasecandidates,
          selectedinstance,
          instancecandidates,
          subsystemclass,
          subsysteminstance,
          implicitsubsystem,
          resourcestate
        ).toOption.toRight("Component Admin view model violates v1 validation")
      } yield value
    } catch {
      case NonFatal(error) => Left(s"Invalid Component Admin view model: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _logical_release_candidates(values: Vector[Json]): Either[String, Vector[ComponentAdminViewField[ComponentAdminLogicalRelease]]] =
    _sequence(values.zipWithIndex.map { case (value, index) => _logical_release_field(value, s"view.logicalReleaseCandidates[$index]") })

  private def _loaded_instance_candidates(values: Vector[Json]): Either[String, Vector[ComponentAdminViewField[ComponentAdminLoadedInstance]]] =
    _sequence(values.zipWithIndex.map { case (value, index) => _loaded_instance_field(value, s"view.loadedInstanceCandidates[$index]") })

  private def _component_class_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminComponentClass]] =
    _view_field(json, context)(_component_class)

  private def _logical_release_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminLogicalRelease]] =
    _view_field(json, context)(_logical_release)

  private def _loaded_instance_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminLoadedInstance]] =
    _view_field(json, context)(_loaded_instance)

  private def _subsystem_class_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminSubsystemClass]] =
    _view_field(json, context)(_subsystem_class)

  private def _subsystem_instance_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminSubsystemInstance]] =
    _view_field(json, context)(_subsystem_instance)

  private def _implicit_subsystem_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminImplicitComponentSubsystem]] =
    _view_field(json, context)(_implicit_subsystem)

  private def _resource_state_field(json: Json, context: String): Either[String, ComponentAdminViewField[ComponentAdminResourceState]] =
    _view_field(json, context)(_resource_state)

  private def _view_field[A](
    json: Json,
    context: String
  )(decodevalue: (Json, String) => Either[String, A]): Either[String, ComponentAdminViewField[A]] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _field_fields, context)
      value <- decodevalue(_required(obj, "value", context), s"$context.value")
      provenance <- _provenance(_required(obj, "provenance", context), s"$context.provenance")
    } yield ComponentAdminViewField(value, provenance)

  private def _component_class(json: Json, context: String): Either[String, ComponentAdminComponentClass] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _component_class_fields, context)
      componentid <- _component_id(_required(obj, "componentId", context), s"$context.componentId")
    } yield ComponentAdminComponentClass(componentid)

  private def _logical_release(json: Json, context: String): Either[String, ComponentAdminLogicalRelease] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _release_fields, context)
      componentclass <- _component_class(_required(obj, "componentClass", context), s"$context.componentClass")
      release <- _string(obj, "release", context)
    } yield ComponentAdminLogicalRelease(componentclass, release)

  private def _loaded_instance(json: Json, context: String): Either[String, ComponentAdminLoadedInstance] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _loaded_instance_fields, context)
      componentclass <- _component_class(_required(obj, "componentClass", context), s"$context.componentClass")
      instanceid <- _instance_id(_required(obj, "instanceId", context), s"$context.instanceId")
    } yield ComponentAdminLoadedInstance(componentclass, instanceid)

  private def _subsystem_class(json: Json, context: String): Either[String, ComponentAdminSubsystemClass] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _subsystem_class_fields, context)
      name <- _string(obj, "name", context)
    } yield ComponentAdminSubsystemClass(name)

  private def _subsystem_instance(json: Json, context: String): Either[String, ComponentAdminSubsystemInstance] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _subsystem_instance_fields, context)
      subsystemclass <- _subsystem_class(_required(obj, "subsystemClass", context), s"$context.subsystemClass")
      instance <- _string(obj, "instance", context)
    } yield ComponentAdminSubsystemInstance(subsystemclass, instance)

  private def _implicit_subsystem(json: Json, context: String): Either[String, ComponentAdminImplicitComponentSubsystem] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _implicit_subsystem_fields, context)
      componentclass <- _component_class(_required(obj, "componentClass", context), s"$context.componentClass")
      name <- _string(obj, "name", context)
    } yield ComponentAdminImplicitComponentSubsystem(componentclass, name)

  private def _resource_state(json: Json, context: String): Either[String, ComponentAdminResourceState] =
    json.asString.toRight(s"$context must be a string").flatMap {
      case "available" => Right(ComponentAdminResourceState.Available)
      case "unavailable" => Right(ComponentAdminResourceState.Unavailable)
      case "forbidden" => Right(ComponentAdminResourceState.Forbidden)
      case "stale" => Right(ComponentAdminResourceState.Stale)
      case "incompatible" => Right(ComponentAdminResourceState.Incompatible)
      case "corrupt" => Right(ComponentAdminResourceState.Corrupt)
      case _ => Left(s"$context is not an admitted Component Admin resource state")
    }

  private def _provenance(json: Json, context: String): Either[String, ComponentAdminSafeProvenance] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _provenance_fields, context)
      sourcekind <- _source_kind(_required(obj, "sourceKind", context), s"$context.sourceKind")
      logicalidentity <- _optional_logical_identity(obj, "logicalIdentity", context)
    } yield ComponentAdminSafeProvenance(sourcekind, logicalidentity)

  private def _source_kind(json: Json, context: String): Either[String, ComponentAdminSourceKind] =
    json.asString.toRight(s"$context must be a string").flatMap {
      case "runtime-fact" => Right(ComponentAdminSourceKind.RuntimeFact)
      case "resolved-resource" => Right(ComponentAdminSourceKind.ResolvedResource)
      case "knowledge-manifest" => Right(ComponentAdminSourceKind.KnowledgeManifest)
      case "descriptor" => Right(ComponentAdminSourceKind.Descriptor)
      case _ => Left(s"$context is not an admitted Component Admin source kind")
    }

  private def _optional_logical_identity(
    obj: JsonObject,
    field: String,
    context: String
  ): Either[String, Option[ComponentResourceLogicalIdentity]] =
    _required(obj, field, context) match {
      case value if value.isNull => Right(None)
      case value => _logical_identity(value, s"$context.$field").map(Some(_))
    }

  private def _logical_identity(json: Json, context: String): Either[String, ComponentResourceLogicalIdentity] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, _logical_identity_fields, context)
      componentid <- _component_id(_required(obj, "componentId", context), s"$context.componentId")
      release <- _string(obj, "logicalRelease", context)
      parentid <- _optional_component_id(obj, "parentComponentId", context)
      role <- _string(obj, "childRole", context)
      resource <- _string(obj, "logicalResource", context)
    } yield ComponentResourceLogicalIdentity(componentid, release, parentid, role, resource)

  private def _optional_component_id(obj: JsonObject, field: String, context: String): Either[String, Option[ComponentId]] =
    _required(obj, field, context) match {
      case value if value.isNull => Right(None)
      case value => _component_id(value, s"$context.$field").map(Some(_))
    }

  private def _component_id(json: Json, context: String): Either[String, ComponentId] =
    json.asString.toRight(s"$context must be a string").flatMap { value =>
      ComponentId.parseC(value).toOption match {
        case Some(componentid) if componentid.name == value => Right(componentid)
        case _ => Left(s"$context must be a canonical ComponentId")
      }
    }

  private def _instance_id(json: Json, context: String): Either[String, ComponentInstanceId] =
    for {
      obj <- _object(json, context)
      _ <- _exact_fields(obj, Set("componentId", "label"), context)
      componentid <- _component_id(_required(obj, "componentId", context), s"$context.componentId")
      label <- _string(obj, "label", context)
      instanceid <- ComponentInstanceId.createC(componentid, label).toOption.toRight(s"$context must be a valid ComponentInstanceId")
    } yield instanceid

  private def _object(json: Json, context: String): Either[String, JsonObject] =
    json.asObject.toRight(s"$context must be a JSON object")

  private def _array(json: Json, context: String): Either[String, Vector[Json]] =
    json.asArray.toRight(s"$context must be a JSON array")

  private def _required(obj: JsonObject, field: String, context: String): Json =
    obj(field).getOrElse(throw new IllegalArgumentException(s"$context.$field is required"))

  private def _string(obj: JsonObject, field: String, context: String): Either[String, String] =
    obj(field).flatMap(_.asString).toRight(s"$context.$field must be a string")

  private def _exact_fields(obj: JsonObject, fields: Set[String], context: String): Either[String, Unit] =
    Either.cond(obj.keys.toSet == fields, (), s"$context must contain exactly ${fields.toVector.sorted.mkString(", ")}")

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (z, x) =>
      for {
        xs <- z
        value <- x
      } yield xs :+ value
    }

  private def _json(value: ComponentAdminViewModel): Json =
    Json.obj(
      "schema" -> Json.fromString(value.schema),
      "componentClass" -> _component_class_field_json(value.componentClass),
      "selectedLogicalRelease" -> _logical_release_field_json(value.selectedLogicalRelease),
      "logicalReleaseCandidates" -> Json.fromValues(value.logicalReleaseCandidates.map(_logical_release_field_json)),
      "selectedLoadedInstance" -> _loaded_instance_field_json(value.selectedLoadedInstance),
      "loadedInstanceCandidates" -> Json.fromValues(value.loadedInstanceCandidates.map(_loaded_instance_field_json)),
      "subsystemClass" -> _subsystem_class_field_json(value.subsystemClass),
      "subsystemInstance" -> _subsystem_instance_field_json(value.subsystemInstance),
      "implicitComponentSubsystem" -> _implicit_subsystem_field_json(value.implicitComponentSubsystem),
      "resourceState" -> _resource_state_field_json(value.resourceState)
    )

  private def _component_class_field_json(value: ComponentAdminViewField[ComponentAdminComponentClass]): Json =
    _view_field_json(value, _component_class_json)

  private def _logical_release_field_json(value: ComponentAdminViewField[ComponentAdminLogicalRelease]): Json =
    _view_field_json(value, _logical_release_json)

  private def _loaded_instance_field_json(value: ComponentAdminViewField[ComponentAdminLoadedInstance]): Json =
    _view_field_json(value, _loaded_instance_json)

  private def _subsystem_class_field_json(value: ComponentAdminViewField[ComponentAdminSubsystemClass]): Json =
    _view_field_json(value, _subsystem_class_json)

  private def _subsystem_instance_field_json(value: ComponentAdminViewField[ComponentAdminSubsystemInstance]): Json =
    _view_field_json(value, _subsystem_instance_json)

  private def _implicit_subsystem_field_json(value: ComponentAdminViewField[ComponentAdminImplicitComponentSubsystem]): Json =
    _view_field_json(value, _implicit_subsystem_json)

  private def _resource_state_field_json(value: ComponentAdminViewField[ComponentAdminResourceState]): Json =
    _view_field_json(value, _resource_state_json)

  private def _view_field_json[A](value: ComponentAdminViewField[A], encodevalue: A => Json): Json =
    Json.obj("value" -> encodevalue(value.value), "provenance" -> _provenance_json(value.provenance))

  private def _component_class_json(value: ComponentAdminComponentClass): Json =
    Json.obj("componentId" -> Json.fromString(value.componentId.name))

  private def _logical_release_json(value: ComponentAdminLogicalRelease): Json =
    Json.obj("componentClass" -> _component_class_json(value.componentClass), "release" -> Json.fromString(value.release))

  private def _loaded_instance_json(value: ComponentAdminLoadedInstance): Json =
    Json.obj("componentClass" -> _component_class_json(value.componentClass), "instanceId" -> _instance_id_json(value.instanceId))

  private def _subsystem_class_json(value: ComponentAdminSubsystemClass): Json =
    Json.obj("name" -> Json.fromString(value.name))

  private def _subsystem_instance_json(value: ComponentAdminSubsystemInstance): Json =
    Json.obj("subsystemClass" -> _subsystem_class_json(value.subsystemClass), "instance" -> Json.fromString(value.instance))

  private def _implicit_subsystem_json(value: ComponentAdminImplicitComponentSubsystem): Json =
    Json.obj("componentClass" -> _component_class_json(value.componentClass), "name" -> Json.fromString(value.name))

  private def _resource_state_json(value: ComponentAdminResourceState): Json =
    Json.fromString(value match {
      case ComponentAdminResourceState.Available => "available"
      case ComponentAdminResourceState.Unavailable => "unavailable"
      case ComponentAdminResourceState.Forbidden => "forbidden"
      case ComponentAdminResourceState.Stale => "stale"
      case ComponentAdminResourceState.Incompatible => "incompatible"
      case ComponentAdminResourceState.Corrupt => "corrupt"
    })

  private def _provenance_json(value: ComponentAdminSafeProvenance): Json =
    Json.obj(
      "sourceKind" -> Json.fromString(value.sourceKind match {
        case ComponentAdminSourceKind.RuntimeFact => "runtime-fact"
        case ComponentAdminSourceKind.ResolvedResource => "resolved-resource"
        case ComponentAdminSourceKind.KnowledgeManifest => "knowledge-manifest"
        case ComponentAdminSourceKind.Descriptor => "descriptor"
      }),
      "logicalIdentity" -> value.logicalIdentity.map(_logical_identity_json).getOrElse(Json.Null)
    )

  private def _logical_identity_json(value: ComponentResourceLogicalIdentity): Json =
    Json.obj(
      "componentId" -> Json.fromString(value.componentId.name),
      "logicalRelease" -> Json.fromString(value.logicalRelease),
      "parentComponentId" -> value.parentComponentId.map(parent => Json.fromString(parent.name)).getOrElse(Json.Null),
      "childRole" -> Json.fromString(value.childRole),
      "logicalResource" -> Json.fromString(value.logicalResource)
    )

  private def _instance_id_json(value: ComponentInstanceId): Json =
    Json.obj("componentId" -> Json.fromString(value.componentId.name), "label" -> Json.fromString(value.instance))
}
