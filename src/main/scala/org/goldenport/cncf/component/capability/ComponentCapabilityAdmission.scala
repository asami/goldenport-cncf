package org.goldenport.cncf.component.capability

import ComponentCapabilityReference.*
import ComponentCapabilityProjection.*
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart.{_items, _text}

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityAdmission {
  final class Admitted private[ComponentCapabilityAdmission] (
    val projection: Projection,
    val scopeId: String,
    val capabilities: Vector[CapabilityDefinition],
    val realizations: Vector[ResolvedRealization]
  ) {}

  final class ResolvedRealization private[ComponentCapabilityAdmission] (
    val edge: Realization,
    val target: TargetContract
  ) {}

  def admit(projection: Projection, scope: ScopeInput): Either[Diagnostic, Admitted] = {
    for {
      valid <- ComponentCapabilityProjectionCodec.validate(projection)
      admitted <- scope match {
        case ScopeInput.Rejected(diagnostic) => Left(diagnostic)
        case ScopeInput.Ready(selected) => _admit_ready(valid, selected)
        case null => Left(_malformed("/scope", "An explicit scope outcome is required."))
      }
    } yield admitted
  }

  def admitJson(input: SourceInput, scope: ScopeInput): Either[Diagnostic, Admitted] = {
    input match {
      case SourceInput.Rejected(diagnostic) => Left(diagnostic)
      case SourceInput.Acquired(scopeid, json) =>
        scope match {
          case ScopeInput.Rejected(diagnostic) => Left(diagnostic)
          case ScopeInput.Ready(selected) =>
            val result = for {
              _ <- _text(scopeid, "/scopeId")
              ready <- _nonnull(selected, "/scope")
              _ <- _text(ready.scopeId, "/scope/scopeId")
              _ <- Either.cond(scopeid == ready.scopeId, (), _diagnostic(DiagnosticKind.Incompatible, "Acquisition and selected scope identities differ.", "/scopeId"))
              projection <- ComponentCapabilityProjectionCodec.decode(json)
              admitted <- _admit_ready(projection, ready)
            } yield admitted
            result.left.map(error => error.copy(scopeId = error.scopeId.orElse(Option(scopeid))))
          case null => Left(_malformed("/scope", "An explicit scope outcome is required."))
        }
      case null => Left(_malformed("/", "An explicit acquisition outcome is required."))
    }
  }

  private def _admit_ready(projection: Projection, scope: Scope): Either[Diagnostic, Admitted] = {
    val result = for {
      _ <- ComponentCapabilityAdmissionBindingPart._scope(scope)
      definitions <- ComponentCapabilityAdmissionBindingPart._definitions(projection, scope)
      targets <- ComponentCapabilityAdmissionBindingPart._bind(projection, scope, definitions)
    } yield new Admitted(projection, scope.scopeId, definitions, targets.map { (edge, target) =>
      new ResolvedRealization(edge, target)
    })
    _context(result, Some(projection.component.componentId), Some(projection.source)).left.map { error =>
      error.copy(scopeId = error.scopeId.orElse(Option(scope).flatMap(s => Option(s.scopeId))))
    }
  }

  def isCompatible(admitted: Admitted, required: CapabilityReference, provided: CapabilityReference): Boolean = {
    if (admitted == null || required == null || provided == null) {
      false
    } else {
      val knownrequired = admitted.capabilities.count(c => c.identity == required.identity && c.contractVersion == required.contractVersion) == 1
      val knownprovided = admitted.capabilities.count(c => c.identity == provided.identity && c.contractVersion == provided.contractVersion) == 1
      knownrequired && knownprovided && required.identity == provided.identity &&
        (required.contractVersion == provided.contractVersion || _items(admitted.projection.compatibility).exists { edge =>
          edge.identity == required.identity && edge.requiredVersion == required.contractVersion && edge.providedVersion == provided.contractVersion
        })
    }
  }
}
