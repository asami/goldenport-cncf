package org.goldenport.cncf.component.capability

import io.circe.Json
import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityProjectionJsonEncodingPart {
  private[capability] def _encode(value: Projection): Json = {
    Json.obj(
      "schemaVersion" -> Json.fromInt(value.schemaVersion),
      "generatedAbiVersion" -> Json.fromString(value.generatedAbiVersion),
      "producer" -> _producer(value.producer),
      "component" -> _component_coordinate(value.component),
      "package" -> _package_coordinate(value.packageCoordinate),
      "source" -> _source(value.source),
      "capabilities" -> Json.fromValues(value.capabilities.map(_definition)),
      "declarations" -> Json.fromValues(value.declarations.map(_declaration)),
      "compatibility" -> _presence(value.compatibility, items => Json.fromValues(items.map(_compatibility)))
    )
  }

  private def _producer(value: Producer): Json = {
    Json.obj("id" -> Json.fromString(value.id), "version" -> Json.fromString(value.version))
  }

  private def _component_ref(value: ComponentId): Json = {
    Json.obj("namespace" -> Json.fromString(value.namespace.value()), "id" -> Json.fromString(value.localId.value()))
  }

  private def _component_coordinate(value: ComponentCoordinate): Json = {
    Json.obj(
      "namespace" -> Json.fromString(value.componentId.namespace.value()),
      "id" -> Json.fromString(value.componentId.localId.value()),
      "version" -> Json.fromString(value.version)
    )
  }

  private def _package_coordinate(value: PackageCoordinate): Json = {
    Json.obj("coordinate" -> Json.fromString(value.coordinate), "version" -> Json.fromString(value.version))
  }

  private def _model_id(value: ModelElementId): Json = {
    Json.obj("modelId" -> Json.fromString(value.modelId), "elementId" -> Json.fromString(value.elementId))
  }

  private def _term_id(value: TermId): Json = {
    Json.obj("vocabularyId" -> Json.fromString(value.vocabularyId), "termId" -> Json.fromString(value.termId))
  }

  private def _relation_id(value: RelationId): Json = {
    Json.obj("vocabularyId" -> Json.fromString(value.vocabularyId), "relationId" -> Json.fromString(value.relationId))
  }

  private def _profile_id(value: ProfileId): Json = {
    Json.obj("vocabularyId" -> Json.fromString(value.vocabularyId), "profileId" -> Json.fromString(value.profileId))
  }

  private def _source(value: Source): Json = {
    Json.obj(
      "modelAuthority" -> Json.fromString(value.modelAuthority),
      "path" -> Json.fromString(value.path),
      "sourceRevision" -> Json.fromString(value.sourceRevision),
      "line" -> value.line.map(Json.fromInt).getOrElse(Json.Null)
    )
  }

  private def _presence[A](value: Presence[A], encode: A => Json): Json = {
    value match {
      case Present(item) => Json.obj("state" -> Json.fromString("PRESENT"), "value" -> encode(item))
      case Absent(reason, detail) => Json.obj(
        "state" -> Json.fromString("ABSENT"),
        "reason" -> Json.fromString(reason.toString),
        "detail" -> Json.fromString(detail)
      )
    }
  }

  private def _origin(value: Origin): Json = {
    value match {
      case Declared => Json.obj("kind" -> Json.fromString("DECLARED"))
      case Derived(rule, inputs) => Json.obj(
        "kind" -> Json.fromString("DERIVED"),
        "ruleId" -> Json.fromString(rule),
        "inputs" -> Json.fromValues(inputs.map(_source))
      )
    }
  }

  private def _model_reference(value: ModelReference): Json = {
    Json.obj("identity" -> _model_id(value.identity), "boundary" -> Json.fromString(value.boundary.tag))
  }

  private def _semantic_target(value: SemanticTarget): Json = {
    value match {
      case ModelTarget(identity) => Json.obj("kind" -> Json.fromString("MODEL"), "identity" -> _model_id(identity))
      case TermTarget(identity) => Json.obj("kind" -> Json.fromString("TERM"), "identity" -> _term_id(identity))
    }
  }

  private def _label(value: LocalizedLabel): Json = {
    Json.obj(
      "text" -> Json.fromString(value.text),
      "language" -> value.language.map(Json.fromString).getOrElse(Json.Null)
    )
  }

  private def _model_link(value: ModelLink): Json = {
    Json.obj(
      "role" -> Json.fromString(value.role.tag),
      "target" -> _model_reference(value.target),
      "source" -> _source(value.source),
      "origin" -> _origin(value.origin)
    )
  }

  private def _traceability(value: Traceability): Json = {
    Json.obj(
      "role" -> Json.fromString(value.role.tag),
      "target" -> _model_reference(value.target),
      "source" -> _source(value.source),
      "origin" -> _origin(value.origin)
    )
  }

  private def _grounding(value: Grounding): Json = {
    Json.obj(
      "target" -> _semantic_target(value.target),
      "boundary" -> Json.fromString(value.boundary.tag),
      "relation" -> _presence(value.relation, _relation_id),
      "profile" -> _presence(value.profile, _profile_id),
      "context" -> _presence(value.context, Json.fromString),
      "preferredLabel" -> _presence(value.preferredLabel, _label),
      "source" -> _source(value.source),
      "origin" -> _origin(value.origin)
    )
  }

  private def _definition(value: CapabilityDefinition): Json = {
    Json.obj(
      "kind" -> Json.fromString(value.kind),
      "identity" -> _model_id(value.identity),
      "contractVersion" -> Json.fromString(value.contractVersion),
      "name" -> Json.fromString(value.name),
      "summary" -> _presence(value.summary, Json.fromString),
      "description" -> _presence(value.description, Json.fromString),
      "source" -> _source(value.source),
      "discoveryReferences" -> _presence(value.discoveryReferences, items => Json.fromValues(items.map(_model_link))),
      "grounding" -> _presence(value.grounding, items => Json.fromValues(items.map(_grounding))),
      "traceability" -> _presence(value.traceability, items => Json.fromValues(items.map(_traceability)))
    )
  }

  private def _capability_reference(value: CapabilityReference): Json = {
    Json.obj(
      "kind" -> Json.fromString(value.kind),
      "identity" -> _model_id(value.identity),
      "contractVersion" -> Json.fromString(value.contractVersion)
    )
  }

  private def _key(value: DeclarationKey): Json = {
    Json.obj(
      "component" -> _component_ref(value.component),
      "direction" -> Json.fromString(value.direction.tag),
      "capability" -> _capability_reference(value.capability)
    )
  }

  private def _declaration(value: Declaration): Json = {
    Json.obj(
      "component" -> _component_ref(value.component),
      "direction" -> Json.fromString(value.direction.tag),
      "capability" -> _capability_reference(value.capability),
      "source" -> _source(value.source),
      "realizations" -> _presence(value.realizations, items => Json.fromValues(items.map(_realization))),
      "traceability" -> _presence(value.traceability, items => Json.fromValues(items.map(_traceability)))
    )
  }

  private def _realization(value: Realization): Json = {
    Json.obj(
      "declarationKey" -> _key(value.declarationKey),
      "targetKind" -> Json.fromString(value.targetKind.tag),
      "target" -> _model_reference(value.target),
      "targetComponent" -> _component_ref(value.targetComponent),
      "targetContractVersion" -> Json.fromString(value.targetContractVersion),
      "generatedAbiVersion" -> Json.fromString(value.generatedAbiVersion),
      "source" -> _source(value.source),
      "origin" -> _origin(value.origin)
    )
  }

  private def _compatibility(value: Compatibility): Json = {
    Json.obj(
      "identity" -> _model_id(value.identity),
      "requiredVersion" -> Json.fromString(value.requiredVersion),
      "providedVersion" -> Json.fromString(value.providedVersion),
      "source" -> _source(value.source),
      "origin" -> _origin(value.origin)
    )
  }
}
