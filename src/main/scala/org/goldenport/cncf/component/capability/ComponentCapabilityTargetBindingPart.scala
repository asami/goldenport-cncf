package org.goldenport.cncf.component.capability

import ComponentCapabilityProjection.TargetKind
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityAdmission.{Admitted, ResolvedRealization}
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart._text
import ComponentCapabilityTargetSource.*
import ComponentCapabilityTargetAdapter.AdaptedTarget

/*
 * Native shape validation and original admitted-edge joins; no execution.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityTargetBindingPart {
  def selectContract(
    snapshot: Snapshot,
    targetKind: TargetKind,
    selector: String,
    snapshotLocation: String,
    bindingLocation: String
  ): Either[Diagnostic, Contract] = {
    val candidates: Vector[(Contract, String)] = targetKind match {
      case TargetKind.Operation => snapshot.operations.zipWithIndex.collect {
        case (definition, index) if definition.name == selector => (Contract.Operation(definition), s"$snapshotLocation/operations/$index")
      }
      case TargetKind.Workflow => snapshot.workflows.zipWithIndex.collect {
        case (definition, index) if definition.name == selector => (Contract.Workflow(definition), s"$snapshotLocation/workflows/$index")
      }
      case TargetKind.StateMachine => snapshot.stateMachines.zipWithIndex.collect {
        case (definition, index) if definition.name == selector => (Contract.StateMachine(definition), s"$snapshotLocation/stateMachines/$index")
      }
    }
    val result = candidates match {
      case Vector((contract, location)) =>
        _context(_validate_contract(contract, location), Some(snapshot.component), Some(snapshot.source)).map(_ => contract)
      case values if values.isEmpty =>
        val otherkind = snapshot.operations.exists(_.name == selector) || snapshot.workflows.exists(_.name == selector) || snapshot.stateMachines.exists(_.name == selector)
        if (otherkind) {
          Left(_diagnostic(DiagnosticKind.Incompatible, "The literal selector belongs to a different native target family.", _child(bindingLocation, "targetKind")))
        } else {
          Left(_diagnostic(DiagnosticKind.Unavailable, "No native declaration has this exact selector.", _child(bindingLocation, "selector")))
        }
      case _ => Left(_diagnostic(DiagnosticKind.Ambiguous, "Multiple native declarations have this exact selector.", _child(bindingLocation, "selector")))
    }
    _context(result, Some(snapshot.component), None)
  }

  private def _validate_contract(contract: Contract, location: String): Either[Diagnostic, Unit] = {
    contract match {
      case Contract.Operation(definition) =>
        for {
          _ <- _text(definition.kind, _child(location, "kind"))
          _ <- _text(definition.inputType, _child(location, "inputType"))
          _ <- _text(definition.outputType, _child(location, "outputType"))
          _ <- _text(definition.inputValueKind, _child(location, "inputValueKind"))
        } yield ()
      case Contract.Workflow(definition) =>
        for {
          _ <- _nonnull(definition.id, _child(location, "id"))
          registrations <- _nonnull(definition.registrations, _child(location, "registrations"))
          _ <- _sequence(registrations.zipWithIndex.map { (registration, index) =>
            _nonnull(registration, s"$location/registrations/$index")
          })
        } yield ()
      case Contract.StateMachine(definition) =>
        val path = _child(location, "normalized")
        for {
          option <- _nonnull(definition.normalized, path)
          normalized <- option match {
            case Some(value) => _nonnull(value, path)
            case None => Left(_diagnostic(DiagnosticKind.Unsupported, "Actual StateMachine metadata requires its normalized contract.", path))
          }
          identity <- _nonnull(normalized.identity, _child(path, "identity"))
          _ <- _text(identity.name, _child(_child(path, "identity"), "name"))
          _ <- Either.cond(identity.name == definition.name, (), _malformed(_child(_child(path, "identity"), "name"), "Normalized StateMachine identity must match its declaration name."))
          version <- _nonnull(normalized.version, _child(path, "version"))
          _ <- Either.cond(version.value > 0, (), _malformed(_child(path, "version"), "Normalized StateMachine version must be positive."))
        } yield ()
    }
  }

  def bind(admitted: Admitted, targets: Vector[AdaptedTarget]): Either[Diagnostic, Vector[(ResolvedRealization, AdaptedTarget)]] = {
    _sequence(admitted.realizations.zipWithIndex.map { (realization, index) =>
      val exact = targets.filter(_.target == realization.target)
      val location = s"/admitted/realizations/$index/target"
      val result = exact match {
        case Vector(target) => Right(realization -> target)
        case values if values.isEmpty => Left(_malformed(location, "The admitted target has no exact actual adapted metadata."))
        case _ => Left(_diagnostic(DiagnosticKind.Ambiguous, "The admitted target has multiple actual adapted matches.", location))
      }
      ComponentCapabilityTargetValidationPart.withScope(
        _context(result, Some(realization.edge.declarationKey.component), Some(realization.edge.source)), Some(admitted.scopeId))
    })
  }
}
