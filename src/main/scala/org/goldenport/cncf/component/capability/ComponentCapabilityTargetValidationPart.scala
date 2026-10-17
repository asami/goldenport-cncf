package org.goldenport.cncf.component.capability

import ComponentCapabilityReference.Source
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart.*
import ComponentCapabilityTargetSource.*

/*
 * Validate supplied finite metadata without acquiring facts or currentness.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityTargetValidationPart {
  def withScope[A](result: Either[Diagnostic, A], scopeId: Option[String]): Either[Diagnostic, A] = {
    result.left.map(error => error.copy(scopeId = error.scopeId.orElse(scopeId)))
  }

  def validateInputs(acquisition: AcquisitionInput.Acquired, scopeInput: ScopeInput.Ready, bindings: Vector[Binding]): Either[Diagnostic, Unit] = {
    for {
      original <- _nonnull(scopeInput.scope, "/scope")
      targets <- _nonnull(original.targetContracts, "/scope/targetContracts")
      _ <- _context(Either.cond(targets.isEmpty, (), _malformed("/scope/targetContracts", "Actual target adaptation requires an empty original target catalog.")), None, Option(original.source))
      _ <- ComponentCapabilityAdmissionBindingPart._scope(original)
      _ <- _text(acquisition.scopeId, "/acquisition/scopeId")
      _ <- Either.cond(acquisition.scopeId == original.scopeId, (), _diagnostic(DiagnosticKind.Incompatible, "Target acquisition and selected scope identities differ.", "/acquisition/scopeId"))
      _ <- _values(acquisition.snapshots, "/acquisition/snapshots")(_snapshot)
      _ <- _values(bindings, "/bindings")(_binding)
    } yield ()
  }

  private def _snapshot(value: Snapshot, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      snapshot <- _nonnull(value, path)
      _ <- _component(snapshot.component, _child(path, "component"))
      _ <- _source(snapshot.source, _child(path, "source"))
      _ <- _values(snapshot.operations, _child(path, "operations")) { (definition, location) =>
        _nonnull(definition, location).flatMap(value => _text(value.name, _child(location, "name")))
      }
      _ <- _values(snapshot.workflows, _child(path, "workflows")) { (definition, location) =>
        _nonnull(definition, location).flatMap(value => _text(value.name, _child(location, "name")))
      }
      _ <- _values(snapshot.stateMachines, _child(path, "stateMachines")) { (definition, location) =>
        _nonnull(definition, location).flatMap(value => _text(value.name, _child(location, "name")))
      }
    } yield (), Option(value).flatMap(snapshot => Option(snapshot.component)), Option(value).flatMap(snapshot => Option(snapshot.source)))
  }

  private def _key(value: SnapshotKey, path: String): Either[Diagnostic, Unit] = {
    for {
      key <- _nonnull(value, path)
      _ <- _component(key.component, _child(path, "component"))
      // The key has no line. Reuse source lexical validation, then discard its
      // temporary Source context so diagnostics retain only actual input Source.
      _ <- _source(Source(key.modelAuthority, key.path, key.sourceRevision, None), path)
        .left.map(error => error.copy(source = None))
    } yield ()
  }

  private def _binding(value: Binding, path: String): Either[Diagnostic, Unit] = {
    _context(for {
      binding <- _nonnull(value, path)
      _ <- _key(binding.snapshot, _child(path, "snapshot"))
      _ <- _model_id(binding.identity, _child(path, "identity"))
      _ <- _nonnull(binding.targetKind, _child(path, "targetKind"))
      _ <- _text(binding.selector, _child(path, "selector"))
      _ <- _text(binding.contractVersion, _child(path, "contractVersion"))
      _ <- _text(binding.generatedAbiVersion, _child(path, "generatedAbiVersion"))
      _ <- _source(binding.source, _child(path, "source"))
      _ <- _authority(binding.source, binding.identity.modelId, _child(path, "source"))
    } yield (), Option(value).flatMap(binding => Option(binding.snapshot)).flatMap(key => Option(key.component)),
      Option(value).flatMap(binding => Option(binding.source)))
  }

  def validateSource(snapshot: Snapshot, binding: Binding, location: String): Either[Diagnostic, Unit] = {
    val result = for {
      _ <- _authority(binding.source, snapshot.source.modelAuthority, _child(location, "source"))
      _ <- Either.cond(binding.source.path == snapshot.source.path, (), _diagnostic(DiagnosticKind.Incompatible, "Binding source path differs from the selected snapshot.", _child(_child(location, "source"), "path")))
      _ <- Either.cond(binding.source.sourceRevision == snapshot.source.sourceRevision, (), _diagnostic(DiagnosticKind.Incompatible, "Binding source revision differs from the selected snapshot.", _child(_child(location, "source"), "sourceRevision")))
    } yield ()
    _context(result, Some(snapshot.component), Some(binding.source))
  }
}
