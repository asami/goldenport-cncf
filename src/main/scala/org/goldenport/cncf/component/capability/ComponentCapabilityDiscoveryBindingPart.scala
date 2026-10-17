package org.goldenport.cncf.component.capability

import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityAdmission.ResolvedRealization
import ComponentCapabilityMetadataProvider.*
import ComponentCapabilityTargetSource.{AcquisitionInput, Snapshot, SnapshotKey, Binding, Contract}
import ComponentCapabilityTargetAdapter.{Adapted, Bound}
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart.{_component, _source}

/*
 * Lossless public/native bridge; accepted construction belongs to Discovery.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityDiscoveryBindingPart {
  final case class Prepared(adapted: Adapted, snapshots: Vector[(Snapshot, NativeSnapshot)], bindings: Vector[(Binding, NativeBinding)]) {}
  type Resolved = (ResolvedRealization, NativeContract, NativeSnapshot, NativeBinding, TargetContract)

  def adapt(input: NativeInput): Either[Diagnostic, Prepared] = {
    input.acquisition match {
      case NativeAcquisition.Rejected(diagnostic) =>
        ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Rejected(diagnostic), input.scopeInput, null)
          .map(value => Prepared(value, Vector.empty, Vector.empty))
      case acquisition: NativeAcquisition.Acquired => input.scopeInput match {
        case _: ScopeInput.Ready =>
          // Transport nulls unchanged to the original adapter so its scope,
          // acquisition and binding validation order remains authoritative.
          val snapshots = Option(acquisition.snapshots).map(_.map { snapshot =>
            val original = Option(snapshot).map(value => Snapshot(value.component, value.source, value.operations, value.workflows, value.stateMachines)).orNull
            original -> snapshot
          })
          val bindings = Option(input.bindings).map(_.map(value => _binding(value) -> value))
          for {
            adapted <- ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(acquisition.scopeId, snapshots.map(_.map(_._1)).orNull), input.scopeInput, bindings.map(_.map(_._1)).orNull)
            _ <- _sequence(input.bindings.zipWithIndex.map { (binding, index) =>
              val path = s"/bindings/$index"
              for {
                _ <- _source(binding.snapshotSource, path + "/snapshot")
                _ <- _component(binding.target.component, path + "/target/component")
                _ <- Either.cond(binding.target.component == binding.snapshotComponent, (),
                  _diagnostic(DiagnosticKind.Incompatible, "Native target and selected snapshot Components differ.", path + "/target/component"))
              } yield ()
            })
          } yield Prepared(adapted, snapshots.get, bindings.get)
        case _ =>
          ComponentCapabilityTargetAdapter.adapt(AcquisitionInput.Acquired(acquisition.scopeId, null), input.scopeInput, null)
            .map(value => Prepared(value, Vector.empty, Vector.empty))
      }
      case null => ComponentCapabilityTargetAdapter.adapt(null, input.scopeInput, null).map(value => Prepared(value, Vector.empty, Vector.empty))
    }
  }

  private def _binding(value: NativeBinding): Binding = {
    Option(value).map { binding =>
      val key = Option(binding.snapshotSource).map(source => SnapshotKey(binding.snapshotComponent, source.modelAuthority, source.path, source.sourceRevision)).orNull
      Option(binding.target).map { target =>
        Binding(key, target.identity, target.targetKind, binding.selector, target.contractVersion, target.generatedAbiVersion, target.source)
      }.orNull
    }.orNull
  }

  def resolve(bound: Bound, prepared: Prepared): Either[Diagnostic, Vector[Resolved]] = {
    _sequence(bound.realizations.zipWithIndex.map { (value, index) =>
      for {
        snapshot <- prepared.snapshots.find(pair => pair._1 eq value.target.snapshot).map(_._2)
          .toRight(_malformed(s"/admitted/realizations/$index/snapshot", "Original native snapshot is required."))
        binding <- prepared.bindings.find(pair => pair._1 eq value.target.binding).map(_._2)
          .toRight(_malformed(s"/admitted/realizations/$index/binding", "Original native binding is required."))
      } yield {
        val contract = value.target.contract match {
          case Contract.Operation(definition) => NativeContract.Operation(definition)
          case Contract.Workflow(definition) => NativeContract.Workflow(definition)
          case Contract.StateMachine(definition) => NativeContract.StateMachine(definition)
        }
        (value.realization, contract, snapshot, binding, value.realization.target)
      }
    })
  }
}
