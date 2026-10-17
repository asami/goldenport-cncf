package org.goldenport.cncf.component.capability

import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart.*

/*
 * @since   Oct. 5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityAdmissionBindingPart {
  private[capability] def _scope(value: Scope): Either[Diagnostic, Unit] = {
    val result = for {
      scope <- _nonnull(value, "/scope")
      _ <- _text(scope.scopeId, "/scope/scopeId")
      _ <- _source(scope.source, "/scope/source")
      _ <- _values(scope.modelFacts, "/scope/modelFacts")(_model_fact)
      _ <- _values(scope.termFacts, "/scope/termFacts")(_term_fact)
      _ <- _values(scope.relationFacts, "/scope/relationFacts")(_relation_fact)
      _ <- _values(scope.profileFacts, "/scope/profileFacts")(_profile_fact)
      _ <- _values(scope.capabilities, "/scope/capabilities")(_definition)
      _ <- _duplicates(scope.capabilities, "/scope/capabilities")(c => (c.identity, c.contractVersion), _.source)
      _ <- _values(scope.targetContracts, "/scope/targetContracts")(_target_contract)
    } yield ()
    _context(result, None, Option(value).flatMap(s => Option(s.source))).left.map { error =>
      error.copy(scopeId = error.scopeId.orElse(Option(value).flatMap(s => Option(s.scopeId))))
    }
  }

  private def _model_fact(value: ModelFact, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      fact <- _nonnull(value, path)
      _ <- _model_id(fact.identity, _child(path, "identity"))
      _ <- _nonnull(fact.kind, _child(path, "kind"))
      _ <- _source(fact.source, _child(path, "source"))
      _ <- _authority(fact.source, fact.identity.modelId, _child(path, "source"))
    } yield (), None, Option(value).flatMap(f => Option(f.source)))
  }

  private def _term_fact(value: TermFact, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      fact <- _nonnull(value, path)
      _ <- _term_id(fact.identity, _child(path, "identity"))
      _ <- _source(fact.source, _child(path, "source"))
      _ <- _authority(fact.source, fact.identity.vocabularyId, _child(path, "source"))
    } yield (), None, Option(value).flatMap(f => Option(f.source)))
  }

  private def _relation_fact(value: RelationFact, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      fact <- _nonnull(value, path)
      _ <- _relation_id(fact.identity, _child(path, "identity"))
      _ <- _source(fact.source, _child(path, "source"))
      _ <- _authority(fact.source, fact.identity.vocabularyId, _child(path, "source"))
    } yield (), None, Option(value).flatMap(f => Option(f.source)))
  }

  private def _profile_fact(value: ProfileFact, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      fact <- _nonnull(value, path)
      _ <- _profile_id(fact.identity, _child(path, "identity"))
      _ <- _source(fact.source, _child(path, "source"))
      _ <- _authority(fact.source, fact.identity.vocabularyId, _child(path, "source"))
    } yield (), None, Option(value).flatMap(f => Option(f.source)))
  }

  private def _target_contract(value: TargetContract, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      target <- _nonnull(value, path)
      _ <- _nonnull(target.targetKind, _child(path, "targetKind"))
      _ <- _model_id(target.identity, _child(path, "identity"))
      _ <- _component(target.component, _child(path, "component"))
      _ <- _text(target.contractVersion, _child(path, "contractVersion"))
      _ <- _text(target.generatedAbiVersion, _child(path, "generatedAbiVersion"))
      _ <- _source(target.source, _child(path, "source"))
      _ <- _authority(target.source, target.identity.modelId, _child(path, "source"))
    } yield (), Option(value).flatMap(t => Option(t.component)), Option(value).flatMap(t => Option(t.source)))
  }

  private[capability] def _definitions(projection: Projection, scope: Scope): Either[Diagnostic, Vector[CapabilityDefinition]] = {
    val combined = projection.capabilities ++ scope.capabilities
    val duplicate = scope.capabilities.zipWithIndex.find { (definition, index) =>
      (projection.capabilities ++ scope.capabilities.take(index)).exists { previous =>
        previous.identity == definition.identity && previous.contractVersion == definition.contractVersion
      }
    }
    duplicate match {
      case Some((definition, index)) => Left(
        _diagnostic(DiagnosticKind.Ambiguous, "Repeated exact Capability identity/version candidate.", s"/scope/capabilities/$index")
          .copy(scopeId = Some(scope.scopeId), source = Some(definition.source))
      )
      case None => Right(combined)
    }
  }

  private def _resolve_capability(
    identity: ModelElementId,
    version: String,
    definitions: Vector[CapabilityDefinition],
    identitypath: String,
    versionpath: String
  ): Either[Diagnostic, CapabilityDefinition] = {
    val identities = definitions.filter(_.identity == identity)
    val exact = identities.filter(_.contractVersion == version)
    if (identities.isEmpty) {
      Left(_diagnostic(DiagnosticKind.Malformed, "No Capability has this exact qualified identity.", identitypath))
    } else if (exact.isEmpty) {
      Left(_diagnostic(DiagnosticKind.Incompatible, "No Capability has the requested exact contract version.", versionpath))
    } else if (exact.size != 1) {
      Left(_diagnostic(DiagnosticKind.Ambiguous, "Multiple exact Capability candidates.", versionpath))
    } else {
      Right(exact.head)
    }
  }

  private def _unique[A](values: Vector[A], path: String): Either[Diagnostic, A] = {
    values match {
      case Vector(value) => Right(value)
      case items if items.isEmpty => Left(_diagnostic(DiagnosticKind.Malformed, "No exact Local reference candidate.", path))
      case _ => Left(_diagnostic(DiagnosticKind.Ambiguous, "Multiple exact Local reference candidates.", path))
    }
  }

  private def _model_candidates(identity: ModelElementId, scope: Scope, definitions: Vector[CapabilityDefinition]): Vector[ModelKind] = {
    val explicit = scope.modelFacts.filter(_.identity == identity).map(_.kind)
    // Contract versions are not separate model identities. No source candidate is selected here.
    val capability = if (definitions.exists(_.identity == identity)) Vector(ModelKind.ComponentCapability) else Vector.empty
    explicit ++ capability
  }

  private def _local_model(
    reference: ModelReference,
    expected: String,
    scope: Scope,
    definitions: Vector[CapabilityDefinition],
    path: String
  ): Either[Diagnostic, Unit] = {
    if (reference.boundary == ReferenceBoundary.External) {
      Right(())
    } else {
      for {
        kind <- _unique(_model_candidates(reference.identity, scope, definitions), _child(path, "identity"))
        _ <- Either.cond(kind.tag == expected, (), _diagnostic(DiagnosticKind.Incompatible, "Local model kind differs from the full reference role.", _child(path, "identity")))
      } yield ()
    }
  }

  private def _grounding(edge: Grounding, scope: Scope, definitions: Vector[CapabilityDefinition], path: String): Either[Diagnostic, Unit] = {
    _context(if (edge.boundary == ReferenceBoundary.External) {
      Right(())
    } else {
      for {
        _ <- edge.target match {
          case ModelTarget(identity) => _unique(_model_candidates(identity, scope, definitions), _child(_child(path, "target"), "identity")).map(_ => ())
          case TermTarget(identity) => _unique(scope.termFacts.filter(_.identity == identity), _child(_child(path, "target"), "identity")).map(_ => ())
        }
        _ <- _presence(edge.relation, _child(path, "relation")) { (identity, p) =>
          _unique(scope.relationFacts.filter(_.identity == identity), p).map(_ => ())
        }
        _ <- _presence(edge.profile, _child(path, "profile")) { (identity, p) =>
          _unique(scope.profileFacts.filter(_.identity == identity), p).map(_ => ())
        }
      } yield ()
    }, None, Some(edge.source))
  }

  private def _traceability(value: Presence[Vector[Traceability]], scope: Scope, definitions: Vector[CapabilityDefinition], path: String): Either[Diagnostic, Unit] = {
    _presence(value, path) { (items, p) =>
      _values(items, p) { (edge, q) =>
        _context(_local_model(edge.target, edge.role.tag, scope, definitions, _child(q, "target")), None, Some(edge.source))
      }
    }
  }

  private def _definition_links(value: CapabilityDefinition, scope: Scope, definitions: Vector[CapabilityDefinition], path: String): Either[Diagnostic, Unit] = {
    _context(for {
      _ <- _presence(value.discoveryReferences, _child(path, "discoveryReferences")) { (items, p) =>
        _values(items, p) { (edge, q) =>
          _context(_local_model(edge.target, edge.role.tag, scope, definitions, _child(q, "target")), None, Some(edge.source))
        }
      }
      _ <- _presence(value.grounding, _child(path, "grounding")) { (items, p) =>
        _values(items, p)((edge, q) => _grounding(edge, scope, definitions, q))
      }
      _ <- _traceability(value.traceability, scope, definitions, _child(path, "traceability"))
    } yield (), None, Some(value.source))
  }

  private def _target(edge: Realization, scope: Scope, path: String): Either[Diagnostic, TargetContract] = {
    val identities = scope.targetContracts.filter(t => t.component == edge.targetComponent && t.identity == edge.target.identity)
    val kinds = identities.filter(_.targetKind == edge.targetKind)
    val versions = kinds.filter(_.contractVersion == edge.targetContractVersion)
    val exact = versions.filter(_.generatedAbiVersion == edge.generatedAbiVersion)
    _context(if (identities.isEmpty) {
      Left(_diagnostic(DiagnosticKind.Malformed, "No target has this exact Component/model identity.", _child(path, "target")))
    } else if (kinds.isEmpty) {
      Left(_diagnostic(DiagnosticKind.Incompatible, "Target kind differs from the explicit realization kind.", _child(path, "targetKind")))
    } else if (versions.isEmpty) {
      Left(_diagnostic(DiagnosticKind.Incompatible, "Target contract version differs.", _child(path, "targetContractVersion")))
    } else if (exact.isEmpty) {
      Left(_diagnostic(DiagnosticKind.Incompatible, "Target generated ABI differs.", _child(path, "generatedAbiVersion")))
    } else if (exact.size != 1) {
      Left(_diagnostic(DiagnosticKind.Ambiguous, "Multiple matching target contracts.", _child(path, "target")))
    } else {
      Right(exact.head)
    }, Some(edge.declarationKey.component), Some(edge.source))
  }

  private def _declaration(
    value: Declaration,
    scope: Scope,
    definitions: Vector[CapabilityDefinition],
    path: String
  ): Either[Diagnostic, Vector[(Realization, TargetContract)]] = {
    _context(for {
      _ <- _resolve_capability(value.capability.identity, value.capability.contractVersion, definitions,
        _child(_child(path, "capability"), "identity"), _child(_child(path, "capability"), "contractVersion"))
      _ <- _traceability(value.traceability, scope, definitions, _child(path, "traceability"))
      targets <- _sequence(_items(value.realizations).zipWithIndex.map { (edge, index) =>
        _target(edge, scope, s"$path/realizations/value/$index").map(target => edge -> target)
      })
    } yield targets, Some(value.component), Some(value.source))
  }

  private def _compatibility(value: Compatibility, definitions: Vector[CapabilityDefinition], path: String): Either[Diagnostic, Unit] = {
    _context(for {
      _ <- _resolve_capability(value.identity, value.requiredVersion, definitions, _child(path, "identity"), _child(path, "requiredVersion"))
      _ <- _resolve_capability(value.identity, value.providedVersion, definitions, _child(path, "identity"), _child(path, "providedVersion"))
    } yield (), None, Some(value.source))
  }

  private[capability] def _bind(
    projection: Projection,
    scope: Scope,
    definitions: Vector[CapabilityDefinition]
  ): Either[Diagnostic, Vector[(Realization, TargetContract)]] = {
    for {
      _ <- _values(projection.capabilities, "/capabilities")((c, p) => _definition_links(c, scope, definitions, p))
      _ <- _values(scope.capabilities, "/scope/capabilities")((c, p) => _definition_links(c, scope, definitions, p))
      targets <- _sequence(projection.declarations.zipWithIndex.map { (declaration, index) =>
        _declaration(declaration, scope, definitions, s"/declarations/$index")
      })
      _ <- _presence(projection.compatibility, "/compatibility") { (items, p) =>
        _values(items, p)((edge, q) => _compatibility(edge, definitions, q))
      }
    } yield targets.flatten
  }
}
