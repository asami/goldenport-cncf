package org.goldenport.cncf.component.capability

import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityTargetSource.*

/*
 * Exact finite selection; no inferred identities, providers or global names.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityTargetSelectionPart {
  type Selection = (Snapshot, Binding, Contract, TargetContract)

  def select(acquisition: AcquisitionInput.Acquired, bindings: Vector[Binding]): Either[Diagnostic, Vector[Selection]] = {
    for {
      _ <- _snapshot_duplicates(acquisition.snapshots)
      _ <- _binding_duplicates(bindings)
      selections <- _sequence(bindings.zipWithIndex.map { (binding, index) =>
        _select(acquisition.snapshots, binding, s"/bindings/$index")
      })
    } yield selections
  }

  private def _snapshot_duplicates(snapshots: Vector[Snapshot]): Either[Diagnostic, Unit] = {
    snapshots.indices.find(index => snapshots.take(index).exists(_.key == snapshots(index).key)) match {
      case Some(index) =>
        val snapshot = snapshots(index)
        Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated exact acquired snapshot key.", s"/acquisition/snapshots/$index")
          .copy(component = Some(snapshot.component), source = Some(snapshot.source)))
      case None => Right(())
    }
  }

  private def _binding_duplicates(bindings: Vector[Binding]): Either[Diagnostic, Unit] = {
    def _context_key_(binding: Binding) = {
      (binding.snapshot.component, binding.identity, binding.targetKind, binding.contractVersion, binding.generatedAbiVersion)
    }
    def _native_key_(binding: Binding) = {
      (binding.snapshot, binding.targetKind, binding.selector)
    }
    bindings.indices.find { index =>
      bindings.take(index).exists { previous =>
        _context_key_(previous) == _context_key_(bindings(index)) || _native_key_(previous) == _native_key_(bindings(index))
      }
    } match {
      case Some(index) =>
        val binding = bindings(index)
        Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated exact target context or native declaration alias.", s"/bindings/$index")
          .copy(component = Some(binding.snapshot.component), source = Some(binding.source)))
      case None => Right(())
    }
  }

  private def _select(snapshots: Vector[Snapshot], binding: Binding, location: String): Either[Diagnostic, Selection] = {
    val candidates = snapshots.zipWithIndex.filter { (snapshot, _) => snapshot.key == binding.snapshot }
    val result = candidates match {
      case Vector((snapshot, index)) =>
        for {
          _ <- ComponentCapabilityTargetValidationPart.validateSource(snapshot, binding, location)
          contract <- ComponentCapabilityTargetBindingPart.selectContract(snapshot, binding.targetKind, binding.selector,
            s"/acquisition/snapshots/$index", location)
        } yield {
          val target = TargetContract(binding.targetKind, binding.identity, snapshot.component,
            binding.contractVersion, binding.generatedAbiVersion, binding.source)
          (snapshot, binding, contract, target)
        }
      case values if values.isEmpty =>
        Left(_diagnostic(DiagnosticKind.Unavailable, "No acquired snapshot has this exact key.", _child(location, "snapshot")))
      case _ =>
        Left(_diagnostic(DiagnosticKind.Ambiguous, "Multiple acquired snapshots have this exact key.", _child(location, "snapshot")))
    }
    _context(result, Some(binding.snapshot.component), Some(binding.source))
  }
}
