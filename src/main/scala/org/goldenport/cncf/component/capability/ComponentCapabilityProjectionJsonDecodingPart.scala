package org.goldenport.cncf.component.capability

import io.circe.Json
import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.{Diagnostic, DiagnosticKind}
import ComponentCapabilityProjectionCodec.*

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityProjectionJsonDecodingPart {
  private[capability] def _decode(json: Json): Either[Diagnostic, Projection] = {
    val path = "/"
    // Version precedence is intentional: future envelopes need not have v1 keys.
    val result = for {
      header <- json.asObject.toRight(_malformed(path, "A projection object is required."))
      schema <- _read(header, "schemaVersion", path)(_integer)
      _ <- Either.cond(schema == 1, (), _diagnostic(DiagnosticKind.Unsupported, "Unsupported projection schema.", "/schemaVersion"))
      abi <- _read(header, "generatedAbiVersion", path)(_string)
      _ <- Either.cond(abi == "cncf.component-capability.v1", (), _diagnostic(DiagnosticKind.Unsupported, "Unsupported Capability producer ABI.", "/generatedAbiVersion"))
      obj <- _object(json, Vector("schemaVersion", "generatedAbiVersion", "producer", "component", "package", "source", "capabilities", "declarations", "compatibility"), path)
      producer <- _read(obj, "producer", path)(_producer)
      component <- _read(obj, "component", path)(_component_coordinate)
      pkg <- _read(obj, "package", path)(_package_coordinate)
      source <- _read(obj, "source", path)(_source)
      capabilities <- _read(obj, "capabilities", path)((j, p) => _vector(j, p)(_definition))
      declarations <- _read(obj, "declarations", path)((j, p) => _vector(j, p)(_declaration))
      compatibility <- _read(obj, "compatibility", path)((j, p) => _presence(j, p)((v, q) => _vector(v, q)(_compatibility)))
    } yield Projection(schema, abi, producer, component, pkg, source, capabilities, declarations, compatibility)
    val component = json.asObject.flatMap(_("component")).flatMap(j => _component_coordinate(j, "/component").toOption).map(_.componentId)
    _context(result, component, _known_source(json, path))
  }

  private def _known_source(json: Json, path: String): Option[Source] = {
    json.asObject.flatMap(_("source")).flatMap(j => _source(j, _child(path, "source")).toOption)
  }

  private def _owned[A](json: Json, path: String)(result: Either[Diagnostic, A]): Either[Diagnostic, A] = {
    _context(result, None, _known_source(json, path))
  }

  private def _producer(json: Json, path: String): Either[Diagnostic, Producer] = {
    for {
      obj <- _object(json, Vector("id", "version"), path)
      id <- _read(obj, "id", path)(_string)
      version <- _read(obj, "version", path)(_string)
    } yield Producer(id, version)
  }

  private def _component_identity(json: Json, path: String, fields: Vector[String]): Either[Diagnostic, ComponentId] = {
    for {
      obj <- _object(json, fields, path)
      namespace <- _read(obj, "namespace", path)(_string)
      id <- _read(obj, "id", path)(_string)
      component <- ComponentId.parseC(s"$namespace.$id").toOption.toRight(_malformed(path, "Invalid canonical Component identity."))
      _ <- Either.cond(component.namespace.value() == namespace && component.localId.value() == id, (), _malformed(path, "Component fields must equal the canonical separate identity fields."))
    } yield component
  }

  private def _component_ref(json: Json, path: String): Either[Diagnostic, ComponentId] = {
    _component_identity(json, path, Vector("namespace", "id"))
  }

  private def _component_coordinate(json: Json, path: String): Either[Diagnostic, ComponentCoordinate] = {
    for {
      component <- _component_identity(json, path, Vector("namespace", "id", "version"))
      obj <- _object(json, Vector("namespace", "id", "version"), path)
      version <- _read(obj, "version", path)(_string)
    } yield ComponentCoordinate(component, version)
  }

  private def _package_coordinate(json: Json, path: String): Either[Diagnostic, PackageCoordinate] = {
    for {
      obj <- _object(json, Vector("coordinate", "version"), path)
      coordinate <- _read(obj, "coordinate", path)(_string)
      version <- _read(obj, "version", path)(_string)
    } yield PackageCoordinate(coordinate, version)
  }

  private def _model_id(json: Json, path: String): Either[Diagnostic, ModelElementId] = {
    for {
      obj <- _object(json, Vector("modelId", "elementId"), path)
      authority <- _read(obj, "modelId", path)(_string)
      element <- _read(obj, "elementId", path)(_string)
    } yield ModelElementId(authority, element)
  }

  private def _vocabulary_id[A](json: Json, path: String, field: String)(make: (String, String) => A): Either[Diagnostic, A] = {
    for {
      obj <- _object(json, Vector("vocabularyId", field), path)
      authority <- _read(obj, "vocabularyId", path)(_string)
      id <- _read(obj, field, path)(_string)
    } yield make(authority, id)
  }

  private def _term_id(json: Json, path: String): Either[Diagnostic, TermId] = {
    _vocabulary_id(json, path, "termId")(TermId.apply)
  }

  private def _relation_id(json: Json, path: String): Either[Diagnostic, RelationId] = {
    _vocabulary_id(json, path, "relationId")(RelationId.apply)
  }

  private def _profile_id(json: Json, path: String): Either[Diagnostic, ProfileId] = {
    _vocabulary_id(json, path, "profileId")(ProfileId.apply)
  }

  private def _source(json: Json, path: String): Either[Diagnostic, Source] = {
    for {
      obj <- _object(json, Vector("modelAuthority", "path", "sourceRevision", "line"), path)
      authority <- _read(obj, "modelAuthority", path)(_string)
      file <- _read(obj, "path", path)((j, p) => j.asString.toRight(_malformed(p, "A JSON string is required.")))
      revision <- _read(obj, "sourceRevision", path)(_string)
      line <- _read(obj, "line", path) { (j, p) =>
        if (j.isNull) Right(None) else _integer(j, p).map(Some(_))
      }
    } yield Source(authority, file, revision, line)
  }

  private def _presence[A](json: Json, path: String)(decoder: (Json, String) => Either[Diagnostic, A]): Either[Diagnostic, Presence[A]] = {
    for {
      header <- json.asObject.toRight(_malformed(path, "An explicit Presence object is required."))
      state <- _read(header, "state", path)((j, p) => _tag(j, p, Vector("PRESENT" -> true, "ABSENT" -> false)))
      value <- if (state) {
        for {
          obj <- _object(json, Vector("state", "value"), path)
          value <- _read(obj, "value", path)(decoder)
        } yield Present(value)
      } else {
        for {
          obj <- _object(json, Vector("state", "reason", "detail"), path)
          reason <- _read(obj, "reason", path)((j, p) => _tag(j, p, AbsenceReason.values.toVector.map(v => v.toString -> v)))
          detail <- _read(obj, "detail", path)(_string)
        } yield Absent(reason, detail)
      }
    } yield value
  }

  private def _origin(json: Json, path: String): Either[Diagnostic, Origin] = {
    for {
      header <- json.asObject.toRight(_malformed(path, "An Origin object is required."))
      declared <- _read(header, "kind", path)((j, p) => _tag(j, p, Vector("DECLARED" -> true, "DERIVED" -> false)))
      origin <- if (declared) {
        _object(json, Vector("kind"), path).map(_ => Declared)
      } else {
        for {
          obj <- _object(json, Vector("kind", "ruleId", "inputs"), path)
          rule <- _read(obj, "ruleId", path)(_string)
          inputs <- _read(obj, "inputs", path)((j, p) => _vector(j, p)(_source))
          _ <- Either.cond(inputs.nonEmpty, (), _malformed(_child(path, "inputs"), "Derived origin requires nonempty inputs."))
        } yield Derived(rule, inputs)
      }
    } yield origin
  }

  private def _boundary(json: Json, path: String): Either[Diagnostic, ReferenceBoundary] = {
    _tag(json, path, ReferenceBoundary.values.toVector.map(v => v.tag -> v))
  }

  private def _model_reference(json: Json, path: String): Either[Diagnostic, ModelReference] = {
    for {
      obj <- _object(json, Vector("identity", "boundary"), path)
      identity <- _read(obj, "identity", path)(_model_id)
      boundary <- _read(obj, "boundary", path)(_boundary)
    } yield ModelReference(identity, boundary)
  }

  private def _semantic_target(json: Json, path: String): Either[Diagnostic, SemanticTarget] = {
    for {
      obj <- _object(json, Vector("kind", "identity"), path)
      model <- _read(obj, "kind", path)((j, p) => _tag(j, p, Vector("MODEL" -> true, "TERM" -> false)))
      target <- if (model) _read(obj, "identity", path)(_model_id).map(ModelTarget.apply)
        else _read(obj, "identity", path)(_term_id).map(TermTarget.apply)
    } yield target
  }

  private def _label(json: Json, path: String): Either[Diagnostic, LocalizedLabel] = {
    for {
      obj <- _object(json, Vector("text", "language"), path)
      text <- _read(obj, "text", path)(_string)
      language <- _read(obj, "language", path)((j, p) => if (j.isNull) Right(None) else _string(j, p).map(Some(_)))
    } yield LocalizedLabel(text, language)
  }

  private def _model_link(json: Json, path: String): Either[Diagnostic, ModelLink] = {
    _owned(json, path) {
      for {
        obj <- _object(json, Vector("role", "target", "source", "origin"), path)
        role <- _read(obj, "role", path)((j, p) => _tag(j, p, ModelRole.values.toVector.map(v => v.tag -> v)))
        target <- _read(obj, "target", path)(_model_reference)
        source <- _read(obj, "source", path)(_source)
        origin <- _read(obj, "origin", path)(_origin)
      } yield ModelLink(role, target, source, origin)
    }
  }

  private def _traceability(json: Json, path: String): Either[Diagnostic, Traceability] = {
    _owned(json, path) {
      for {
        obj <- _object(json, Vector("role", "target", "source", "origin"), path)
        role <- _read(obj, "role", path)((j, p) => _tag(j, p, TraceabilityRole.values.toVector.map(v => v.tag -> v)))
        target <- _read(obj, "target", path)(_model_reference)
        source <- _read(obj, "source", path)(_source)
        origin <- _read(obj, "origin", path)(_origin)
      } yield Traceability(role, target, source, origin)
    }
  }

  private def _grounding(json: Json, path: String): Either[Diagnostic, Grounding] = {
    _owned(json, path) {
      for {
        obj <- _object(json, Vector("target", "boundary", "relation", "profile", "context", "preferredLabel", "source", "origin"), path)
        target <- _read(obj, "target", path)(_semantic_target)
        boundary <- _read(obj, "boundary", path)(_boundary)
        relation <- _read(obj, "relation", path)((j, p) => _presence(j, p)(_relation_id))
        profile <- _read(obj, "profile", path)((j, p) => _presence(j, p)(_profile_id))
        context <- _read(obj, "context", path)((j, p) => _presence(j, p)(_string))
        label <- _read(obj, "preferredLabel", path)((j, p) => _presence(j, p)(_label))
        source <- _read(obj, "source", path)(_source)
        origin <- _read(obj, "origin", path)(_origin)
      } yield Grounding(target, boundary, relation, profile, context, label, source, origin)
    }
  }

  private def _definition(json: Json, path: String): Either[Diagnostic, CapabilityDefinition] = {
    _owned(json, path) {
      for {
        obj <- _object(json, Vector("kind", "identity", "contractVersion", "name", "summary", "description", "source", "discoveryReferences", "grounding", "traceability"), path)
        _ <- _read(obj, "kind", path)((j, p) => _tag(j, p, Vector("COMPONENT" -> ())))
        identity <- _read(obj, "identity", path)(_model_id)
        version <- _read(obj, "contractVersion", path)(_string)
        name <- _read(obj, "name", path)(_string)
        summary <- _read(obj, "summary", path)((j, p) => _presence(j, p)(_string))
        description <- _read(obj, "description", path)((j, p) => _presence(j, p)(_string))
        source <- _read(obj, "source", path)(_source)
        links <- _read(obj, "discoveryReferences", path)((j, p) => _presence(j, p)((v, q) => _vector(v, q)(_model_link)))
        grounding <- _read(obj, "grounding", path)((j, p) => _presence(j, p)((v, q) => _vector(v, q)(_grounding)))
        traceability <- _read(obj, "traceability", path)((j, p) => _presence(j, p)((v, q) => _vector(v, q)(_traceability)))
      } yield CapabilityDefinition(identity, version, name, summary, description, source, links, grounding, traceability)
    }
  }

  private def _capability_reference(json: Json, path: String): Either[Diagnostic, CapabilityReference] = {
    for {
      obj <- _object(json, Vector("kind", "identity", "contractVersion"), path)
      _ <- _read(obj, "kind", path)((j, p) => _tag(j, p, Vector("COMPONENT" -> ())))
      identity <- _read(obj, "identity", path)(_model_id)
      version <- _read(obj, "contractVersion", path)(_string)
    } yield CapabilityReference(identity, version)
  }

  private def _direction(json: Json, path: String): Either[Diagnostic, Direction] = {
    _tag(json, path, Direction.values.toVector.map(v => v.tag -> v))
  }

  private def _key(json: Json, path: String): Either[Diagnostic, DeclarationKey] = {
    for {
      obj <- _object(json, Vector("component", "direction", "capability"), path)
      component <- _read(obj, "component", path)(_component_ref)
      direction <- _read(obj, "direction", path)(_direction)
      capability <- _read(obj, "capability", path)(_capability_reference)
    } yield DeclarationKey(component, direction, capability)
  }

  private def _declaration(json: Json, path: String): Either[Diagnostic, Declaration] = {
    val result = _owned(json, path) {
      for {
        obj <- _object(json, Vector("component", "direction", "capability", "source", "realizations", "traceability"), path)
        component <- _read(obj, "component", path)(_component_ref)
        direction <- _read(obj, "direction", path)(_direction)
        capability <- _read(obj, "capability", path)(_capability_reference)
        source <- _read(obj, "source", path)(_source)
        realizations <- _read(obj, "realizations", path)((j, p) => _presence(j, p)((v, q) => _vector(v, q)(_realization)))
        traceability <- _read(obj, "traceability", path)((j, p) => _presence(j, p)((v, q) => _vector(v, q)(_traceability)))
      } yield Declaration(component, direction, capability, source, realizations, traceability)
    }
    val component = json.asObject.flatMap(_("component")).flatMap(j => _component_ref(j, _child(path, "component")).toOption)
    _context(result, component, None)
  }

  private def _realization(json: Json, path: String): Either[Diagnostic, Realization] = {
    _owned(json, path) {
      for {
        obj <- _object(json, Vector("declarationKey", "targetKind", "target", "targetComponent", "targetContractVersion", "generatedAbiVersion", "source", "origin"), path)
        key <- _read(obj, "declarationKey", path)(_key)
        kind <- _read(obj, "targetKind", path)((j, p) => _tag(j, p, TargetKind.values.toVector.map(v => v.tag -> v)))
        target <- _read(obj, "target", path)(_model_reference)
        component <- _read(obj, "targetComponent", path)(_component_ref)
        version <- _read(obj, "targetContractVersion", path)(_string)
        abi <- _read(obj, "generatedAbiVersion", path)(_string)
        source <- _read(obj, "source", path)(_source)
        origin <- _read(obj, "origin", path)(_origin)
      } yield Realization(key, kind, target, component, version, abi, source, origin)
    }
  }

  private def _compatibility(json: Json, path: String): Either[Diagnostic, Compatibility] = {
    _owned(json, path) {
      for {
        obj <- _object(json, Vector("identity", "requiredVersion", "providedVersion", "source", "origin"), path)
        identity <- _read(obj, "identity", path)(_model_id)
        required <- _read(obj, "requiredVersion", path)(_string)
        provided <- _read(obj, "providedVersion", path)(_string)
        source <- _read(obj, "source", path)(_source)
        origin <- _read(obj, "origin", path)(_origin)
      } yield Compatibility(identity, required, provided, source, origin)
    }
  }
}
