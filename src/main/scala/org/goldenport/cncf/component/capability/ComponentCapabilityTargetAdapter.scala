package org.goldenport.cncf.component.capability

import ComponentCapabilityProjection.Projection
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityAdmission.{Admitted, ResolvedRealization}
import ComponentCapabilityProjectionCodec.{_malformed, _nonnull}
import ComponentCapabilityTargetSource.*

/*
 * Sole acceptance facade for pure actual metadata adaptation and R12 binding.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentCapabilityTargetAdapter {
  final class Adapted private[ComponentCapabilityTargetAdapter] (
    val acquisition: AcquisitionInput.Acquired,
    val scopeInput: ScopeInput.Ready,
    val bindings: Vector[Binding],
    val scope: Scope,
    val targets: Vector[AdaptedTarget]
  ) {}

  final class AdaptedTarget private[ComponentCapabilityTargetAdapter] (
    val snapshot: Snapshot,
    val binding: Binding,
    val contract: Contract,
    val target: TargetContract
  ) {}

  final class Bound private[ComponentCapabilityTargetAdapter] (
    val admitted: Admitted,
    val adapted: Adapted,
    val realizations: Vector[BoundRealization]
  ) {}

  final class BoundRealization private[ComponentCapabilityTargetAdapter] (
    val realization: ResolvedRealization,
    val target: AdaptedTarget
  ) {}

  def adapt(
    acquisition: AcquisitionInput,
    scopeInput: ScopeInput,
    bindings: Vector[Binding]
  ): Either[Diagnostic, Adapted] = {
    acquisition match {
      case AcquisitionInput.Rejected(diagnostic) =>
        _nonnull(diagnostic, "/acquisition/diagnostic").flatMap(value => Left(value))
      case _ => scopeInput match {
        case ScopeInput.Rejected(diagnostic) =>
          _nonnull(diagnostic, "/scope/diagnostic").flatMap(value => Left(value))
        case ready: ScopeInput.Ready => acquisition match {
          case acquired: AcquisitionInput.Acquired => _adapt_ready(acquired, ready, bindings)
          case null => Left(_malformed("/acquisition", "An explicit target acquisition outcome is required."))
          case AcquisitionInput.Rejected(diagnostic) => Left(diagnostic)
        }
        case null => Left(_malformed("/scope", "An explicit selected scope outcome is required."))
      }
    }
  }

  private def _adapt_ready(acquisition: AcquisitionInput.Acquired, scopeinput: ScopeInput.Ready, bindings: Vector[Binding]): Either[Diagnostic, Adapted] = {
    val result = for {
      _ <- ComponentCapabilityTargetValidationPart.validateInputs(acquisition, scopeinput, bindings)
      selections <- ComponentCapabilityTargetSelectionPart.select(acquisition, bindings)
    } yield {
      val targets = selections.map { (snapshot, binding, contract, target) =>
        new AdaptedTarget(snapshot, binding, contract, target)
      }
      val scope = scopeinput.scope.copy(targetContracts = targets.map(_.target))
      new Adapted(acquisition, scopeinput, bindings, scope, targets)
    }
    ComponentCapabilityTargetValidationPart.withScope(result, Option(acquisition.scopeId))
  }

  def admit(projection: Projection, adapted: Adapted): Either[Diagnostic, Bound] = {
    for {
      selected <- _nonnull(adapted, "/adapted")
      admitted <- ComponentCapabilityAdmission.admit(projection, ScopeInput.Ready(selected.scope))
      bound <- _bind_admitted(admitted, selected)
    } yield bound
  }

  def admitJson(input: SourceInput, adapted: Adapted): Either[Diagnostic, Bound] = {
    input match {
      case SourceInput.Rejected(diagnostic) => Left(diagnostic)
      case _ =>
        val result = for {
          selected <- _nonnull(adapted, "/adapted")
          admitted <- ComponentCapabilityAdmission.admitJson(input, ScopeInput.Ready(selected.scope))
          bound <- _bind_admitted(admitted, selected)
        } yield bound
        val scopeid = input match {
          case SourceInput.Acquired(value, _) => Option(value)
          case _ => None
        }
        ComponentCapabilityTargetValidationPart.withScope(result, scopeid)
    }
  }

  private def _bind_admitted(admitted: Admitted, adapted: Adapted): Either[Diagnostic, Bound] = {
    ComponentCapabilityTargetBindingPart.bind(admitted, adapted.targets).map { selections =>
      val realizations = selections.map { (realization, target) => new BoundRealization(realization, target) }
      new Bound(admitted, adapted, realizations)
    }
  }
}
