package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.{Diagnostic, DiagnosticKind}
import ComponentCapabilityProjectionCodec.*

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityProjectionValidationPart {
  private[capability] def _text(value: String, path: String): Either[Diagnostic, Unit] = {
    val valid = value != null && value.nonEmpty &&
      !_whitespace(value.codePointAt(0)) && !_whitespace(value.codePointBefore(value.length)) &&
      !value.codePoints().anyMatch(codepoint => Character.isISOControl(codepoint))
    Either.cond(valid, (), _malformed(path, "Text must be nonempty, unpadded and free of ISO controls."))
  }

  private def _whitespace(codepoint: Int): Boolean = {
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
  }

  private def _path(value: String, path: String): Either[Diagnostic, Unit] = {
    val valid = value != null && value.nonEmpty &&
      !value.codePoints().anyMatch(codepoint => Character.isISOControl(codepoint)) &&
      !value.startsWith("/") && !value.contains("\\") &&
      !value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") &&
      value.split("/", -1).forall(segment => segment.nonEmpty && segment != "." && segment != "..")
    Either.cond(valid, (), _malformed(path, "A canonical project-relative POSIX path is required."))
  }

  private[capability] def _source(value: Source, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      source <- _nonnull(value, path)
      _ <- _text(source.modelAuthority, _child(path, "modelAuthority"))
      _ <- _path(source.path, _child(path, "path"))
      _ <- _text(source.sourceRevision, _child(path, "sourceRevision"))
      line <- _nonnull(source.line, _child(path, "line"))
      _ <- Either.cond(line.forall(_ > 0), (), _malformed(_child(path, "line"), "A supplied line must be positive."))
    } yield (), None, Option(value))
  }

  private[capability] def _model_id(value: ModelElementId, path: String): Either[Diagnostic, Unit] = {
    for {
      id <- _nonnull(value, path)
      _ <- _text(id.modelId, _child(path, "modelId"))
      _ <- _text(id.elementId, _child(path, "elementId"))
    } yield ()
  }

  private[capability] def _term_id(value: TermId, path: String): Either[Diagnostic, Unit] = {
    for {
      id <- _nonnull(value, path)
      _ <- _text(id.vocabularyId, _child(path, "vocabularyId"))
      _ <- _text(id.termId, _child(path, "termId"))
    } yield ()
  }

  private[capability] def _relation_id(value: RelationId, path: String): Either[Diagnostic, Unit] = {
    for {
      id <- _nonnull(value, path)
      _ <- _text(id.vocabularyId, _child(path, "vocabularyId"))
      _ <- _text(id.relationId, _child(path, "relationId"))
    } yield ()
  }

  private[capability] def _profile_id(value: ProfileId, path: String): Either[Diagnostic, Unit] = {
    for {
      id <- _nonnull(value, path)
      _ <- _text(id.vocabularyId, _child(path, "vocabularyId"))
      _ <- _text(id.profileId, _child(path, "profileId"))
    } yield ()
  }

  private[capability] def _component(value: ComponentId, path: String): Either[Diagnostic, Unit] = {
    for {
      component <- _nonnull(value, path)
      parsed <- ComponentId.parseC(component.name).toOption.toRight(_malformed(path, "A canonical Component identity is required."))
      _ <- Either.cond(parsed == component, (), _malformed(path, "Component identity must equal canonical identity."))
    } yield ()
  }

  private[capability] def _values[A](values: Vector[A], path: String)(check: (A, String) => Either[Diagnostic, Unit]): Either[Diagnostic, Unit] = {
    _nonnull(values, path).flatMap { items =>
      _sequence(items.zipWithIndex.map { (item, index) => check(item, _child(path, index.toString)) }).map(_ => ())
    }
  }

  private[capability] def _presence[A](value: Presence[A], path: String)(check: (A, String) => Either[Diagnostic, Unit]): Either[Diagnostic, Unit] = {
    value match {
      case Present(item) => check(item, _child(path, "value"))
      case Absent(reason, detail) =>
        for {
          _ <- _nonnull(reason, _child(path, "reason"))
          _ <- _text(detail, _child(path, "detail"))
        } yield ()
      case null => Left(_malformed(path, "An explicit Presence value is required."))
    }
  }

  private[capability] def _items[A](value: Presence[Vector[A]]): Vector[A] = {
    value match {
      case Present(items) => items
      case _: Absent => Vector.empty
    }
  }

  private def _origin(value: Origin, path: String): Either[Diagnostic, Unit] = {
    value match {
      case Declared => Right(())
      case Derived(rule, inputs) =>
        for {
          _ <- _text(rule, _child(path, "ruleId"))
          _ <- _values(inputs, _child(path, "inputs"))(_source)
          _ <- Either.cond(inputs.nonEmpty, (), _malformed(_child(path, "inputs"), "Derived origin requires nonempty inputs."))
        } yield ()
      case null => Left(_malformed(path, "An explicit Origin value is required."))
    }
  }

  private[capability] def _authority(source: Source, authority: String, path: String): Either[Diagnostic, Unit] = {
    Either.cond(source.modelAuthority == authority, (), _diagnostic(DiagnosticKind.Incompatible, "Source authority differs from its owner.", _child(path, "modelAuthority")))
  }

  private def _model_reference(value: ModelReference, path: String): Either[Diagnostic, Unit] = {
    for {
      reference <- _nonnull(value, path)
      _ <- _model_id(reference.identity, _child(path, "identity"))
      _ <- _nonnull(reference.boundary, _child(path, "boundary"))
    } yield ()
  }

  private def _label(value: LocalizedLabel, path: String): Either[Diagnostic, Unit] = {
    for {
      label <- _nonnull(value, path)
      _ <- _text(label.text, _child(path, "text"))
      language <- _nonnull(label.language, _child(path, "language"))
      _ <- language match {
        case Some(text) => _text(text, _child(path, "language"))
        case None => Right(())
      }
    } yield ()
  }

  private def _owned_edge(source: Source, origin: Origin, authority: String, path: String): Either[Diagnostic, Unit] = {
    for {
      _ <- _source(source, _child(path, "source"))
      _ <- _authority(source, authority, _child(path, "source"))
      _ <- _origin(origin, _child(path, "origin"))
    } yield ()
  }

  private def _model_link(value: ModelLink, authority: String, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      edge <- _nonnull(value, path)
      _ <- _nonnull(edge.role, _child(path, "role"))
      _ <- _model_reference(edge.target, _child(path, "target"))
      _ <- _owned_edge(edge.source, edge.origin, authority, path)
    } yield (), None, Option(value).flatMap(v => Option(v.source)))
  }

  private def _traceability(value: Traceability, authority: String, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      edge <- _nonnull(value, path)
      _ <- _nonnull(edge.role, _child(path, "role"))
      _ <- _model_reference(edge.target, _child(path, "target"))
      _ <- _owned_edge(edge.source, edge.origin, authority, path)
    } yield (), None, Option(value).flatMap(v => Option(v.source)))
  }

  private def _grounding(value: Grounding, authority: String, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      edge <- _nonnull(value, path)
      _ <- edge.target match {
        case ModelTarget(identity) => _model_id(identity, _child(_child(path, "target"), "identity"))
        case TermTarget(identity) => _term_id(identity, _child(_child(path, "target"), "identity"))
        case null => Left(_malformed(_child(path, "target"), "A semantic target is required."))
      }
      _ <- _nonnull(edge.boundary, _child(path, "boundary"))
      _ <- _presence(edge.relation, _child(path, "relation"))(_relation_id)
      _ <- _presence(edge.profile, _child(path, "profile"))(_profile_id)
      _ <- _presence(edge.context, _child(path, "context"))(_text)
      _ <- _presence(edge.preferredLabel, _child(path, "preferredLabel"))(_label)
      _ <- _owned_edge(edge.source, edge.origin, authority, path)
    } yield (), None, Option(value).flatMap(v => Option(v.source)))
  }

  private[capability] def _definition(value: CapabilityDefinition, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      definition <- _nonnull(value, path)
      _ <- _model_id(definition.identity, _child(path, "identity"))
      _ <- _text(definition.contractVersion, _child(path, "contractVersion"))
      _ <- _text(definition.name, _child(path, "name"))
      _ <- _presence(definition.summary, _child(path, "summary"))(_text)
      _ <- _presence(definition.description, _child(path, "description"))(_text)
      _ <- _source(definition.source, _child(path, "source"))
      _ <- _authority(definition.source, definition.identity.modelId, _child(path, "source"))
      _ <- _presence(definition.discoveryReferences, _child(path, "discoveryReferences")) { (items, p) =>
        _values(items, p)((edge, q) => _model_link(edge, definition.identity.modelId, q))
      }
      _ <- _presence(definition.grounding, _child(path, "grounding")) { (items, p) =>
        _values(items, p)((edge, q) => _grounding(edge, definition.identity.modelId, q))
      }
      _ <- _presence(definition.traceability, _child(path, "traceability")) { (items, p) =>
        _values(items, p)((edge, q) => _traceability(edge, definition.identity.modelId, q))
      }
    } yield (), None, Option(value).flatMap(v => Option(v.source)))
  }

  private def _capability_reference(value: CapabilityReference, path: String): Either[Diagnostic, Unit] = {
    for {
      reference <- _nonnull(value, path)
      _ <- _model_id(reference.identity, _child(path, "identity"))
      _ <- _text(reference.contractVersion, _child(path, "contractVersion"))
    } yield ()
  }

  private def _key(value: DeclarationKey, path: String): Either[Diagnostic, Unit] = {
    for {
      key <- _nonnull(value, path)
      _ <- _component(key.component, _child(path, "component"))
      _ <- _nonnull(key.direction, _child(path, "direction"))
      _ <- _capability_reference(key.capability, _child(path, "capability"))
    } yield ()
  }

  private def _realization(value: Realization, key: DeclarationKey, authority: String, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      edge <- _nonnull(value, path)
      _ <- _key(edge.declarationKey, _child(path, "declarationKey"))
      _ <- Either.cond(edge.declarationKey == key, (), _diagnostic(DiagnosticKind.Incompatible, "Realization declaration key differs from its enclosing declaration.", _child(path, "declarationKey")))
      _ <- _nonnull(edge.targetKind, _child(path, "targetKind"))
      _ <- _model_reference(edge.target, _child(path, "target"))
      _ <- _component(edge.targetComponent, _child(path, "targetComponent"))
      _ <- _text(edge.targetContractVersion, _child(path, "targetContractVersion"))
      _ <- _text(edge.generatedAbiVersion, _child(path, "generatedAbiVersion"))
      _ <- _owned_edge(edge.source, edge.origin, authority, path)
    } yield (), Some(key.component), Option(value).flatMap(v => Option(v.source)))
  }

  private def _declaration(value: Declaration, projection: Projection, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      declaration <- _nonnull(value, path)
      _ <- _component(declaration.component, _child(path, "component"))
      _ <- Either.cond(declaration.component == projection.component.componentId, (), _diagnostic(DiagnosticKind.Incompatible, "Declaration Component differs from the envelope Component.", _child(path, "component")))
      _ <- _nonnull(declaration.direction, _child(path, "direction"))
      _ <- _capability_reference(declaration.capability, _child(path, "capability"))
      _ <- _source(declaration.source, _child(path, "source"))
      _ <- _authority(declaration.source, projection.source.modelAuthority, _child(path, "source"))
      _ <- _presence(declaration.realizations, _child(path, "realizations")) { (items, p) =>
        _values(items, p)((edge, q) => _realization(edge, declaration._key, declaration.source.modelAuthority, q))
      }
      _ <- _presence(declaration.traceability, _child(path, "traceability")) { (items, p) =>
        _values(items, p)((edge, q) => _traceability(edge, declaration.source.modelAuthority, q))
      }
    } yield (), Option(value).flatMap(v => Option(v.component)), Option(value).flatMap(v => Option(v.source)))
  }

  private def _compatibility(value: Compatibility, authority: String, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      edge <- _nonnull(value, path)
      _ <- _model_id(edge.identity, _child(path, "identity"))
      _ <- _text(edge.requiredVersion, _child(path, "requiredVersion"))
      _ <- _text(edge.providedVersion, _child(path, "providedVersion"))
      _ <- _owned_edge(edge.source, edge.origin, authority, path)
    } yield (), None, Option(value).flatMap(v => Option(v.source)))
  }

  private[capability] def _duplicates[A, K](values: Vector[A], path: String)(key: A => K, source: A => Source): Either[Diagnostic, Unit] = {
    values.indices.find(index => values.take(index).exists(previous => key(previous) == key(values(index)))) match {
      case Some(index) =>
        Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated exact owning identity/key.", _child(path, index.toString)).copy(source = Some(source(values(index)))))
      case None => Right(())
    }
  }

  private[capability] def _projection(value: Projection): Either[Diagnostic, Unit] = {
    val component = Option(value).flatMap(v => Option(v.component)).flatMap(v => Option(v.componentId))
    val source = Option(value).flatMap(v => Option(v.source))
    _context(for {
      projection <- _nonnull(value, "/")
      _ <- Either.cond(projection.schemaVersion == 1, (), _diagnostic(DiagnosticKind.Unsupported, "Unsupported projection schema.", "/schemaVersion"))
      _ <- _text(projection.generatedAbiVersion, "/generatedAbiVersion")
      _ <- Either.cond(projection.generatedAbiVersion == "cncf.component-capability.v1", (), _diagnostic(DiagnosticKind.Unsupported, "Unsupported Capability producer ABI.", "/generatedAbiVersion"))
      producer <- _nonnull(projection.producer, "/producer")
      _ <- _text(producer.id, "/producer/id")
      _ <- _text(producer.version, "/producer/version")
      coordinate <- _nonnull(projection.component, "/component")
      _ <- _component(coordinate.componentId, "/component")
      _ <- _text(coordinate.version, "/component/version")
      release = ComponentReleaseCoordinate.create(coordinate.componentId.sharedIdentity, coordinate.version)
      _ <- Either.cond(release.isSuccess(), (), _malformed("/component/version", "Invalid Component release coordinate."))
      pkg <- _nonnull(projection.packageCoordinate, "/package")
      _ <- _text(pkg.coordinate, "/package/coordinate")
      _ <- _text(pkg.version, "/package/version")
      _ <- _source(projection.source, "/source")
      _ <- _values(projection.capabilities, "/capabilities")(_definition)
      _ <- _duplicates(projection.capabilities, "/capabilities")(_.identity, _.source)
      _ <- _values(projection.declarations, "/declarations")((d, p) => _declaration(d, projection, p))
      _ <- _duplicates(projection.declarations, "/declarations")(_._key, _.source)
      _ <- _presence(projection.compatibility, "/compatibility") { (items, p) =>
        for {
          _ <- _values(items, p)((edge, q) => _compatibility(edge, projection.source.modelAuthority, q))
          _ <- _duplicates(items, p)(edge => (edge.identity, edge.requiredVersion, edge.providedVersion), _.source)
        } yield ()
      }
    } yield (), component, source)
  }
}
