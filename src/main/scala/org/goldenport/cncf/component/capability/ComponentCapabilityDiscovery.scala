package org.goldenport.cncf.component.capability

import scala.util.control.NonFatal
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityAdmission.{Admitted, ResolvedRealization}
import ComponentCapabilityMetadataProvider.*
import ComponentCapabilityPackageLoader.{Acquired, LoadedProjection}
import ComponentCapabilityPackageSource.PackageInput
import ComponentCapabilityTargetAdapter.{Adapted, Bound}
import ComponentCapabilityProjectionCodec.*

/*
 * Sole acceptance facade for a complete static package/native catalog.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityDiscovery {
  final class Discovered private[ComponentCapabilityDiscovery] (
    val acquired: Acquired,
    val nativeInput: NativeInput,
    val entries: Vector[Entry],
    private[cncf] val _adapted: Adapted
  ) {}

  final class Entry private[ComponentCapabilityDiscovery] (
    val loaded: LoadedProjection,
    val admitted: Admitted,
    val realizations: Vector[ResolvedTarget],
    private[cncf] val _bound: Bound
  ) {}

  final class ResolvedTarget private[ComponentCapabilityDiscovery] (
    val realization: ResolvedRealization,
    val contract: NativeContract,
    val snapshot: NativeSnapshot,
    val binding: NativeBinding,
    val target: TargetContract
  ) {}

  def discover(input: PackageInput, provider: ComponentCapabilityMetadataProvider): Either[Diagnostic, Discovered] = {
    for {
      acquired <- ComponentCapabilityPackageLoader.load(input)
      nativeinput <- _provider_input(provider)
      prepared <- ComponentCapabilityDiscoveryBindingPart.adapt(nativeinput)
      _ <- Either.cond(acquired.input.scopeId == prepared.adapted.acquisition.scopeId, (),
        _diagnostic(DiagnosticKind.Incompatible, "Selected package and native scope identities differ.", "/package/scopeId")
          .copy(scopeId = Some(acquired.input.scopeId), component = Some(acquired.input.component.componentId), source = Some(prepared.adapted.scopeInput.scope.source)))
      bound <- _sequence(acquired.projections.map { loaded =>
        for {
          admitted <- ComponentCapabilityTargetAdapter.admit(loaded.projection, prepared.adapted)
          resolved <- ComponentCapabilityDiscoveryBindingPart.resolve(admitted, prepared)
        } yield (loaded, admitted, resolved)
      })
      _ <- _batch_uniqueness(acquired)
    } yield {
      val entries = bound.map { (loaded, original, resolved) =>
        val targets = resolved.map { (realization, contract, snapshot, binding, target) =>
          new ResolvedTarget(realization, contract, snapshot, binding, target)
        }
        new Entry(loaded, original.admitted, targets, original)
      }
      new Discovered(acquired, nativeinput, entries, prepared.adapted)
    }
  }

  private def _provider_input(provider: ComponentCapabilityMetadataProvider): Either[Diagnostic, NativeInput] = {
    _nonnull(provider, "/provider").flatMap { selected =>
      try _nonnull(selected.componentCapabilityMetadata, "/provider/input") catch {
        case NonFatal(_) => Left(_diagnostic(DiagnosticKind.Unavailable, "Native metadata provider could not supply its observation.", "/provider"))
      }
    }
  }

  private def _batch_uniqueness(acquired: Acquired): Either[Diagnostic, Unit] = {
    val definitions = acquired.projections.flatMap { loaded =>
      loaded.projection.capabilities.zipWithIndex.map { (value, index) =>
        ((value.identity, value.contractVersion), loaded, s"/${loaded.resourcePath}/capabilities/$index", value.source)
      }
    }
    val declarations = acquired.projections.flatMap { loaded =>
      loaded.projection.declarations.zipWithIndex.map { (value, index) =>
        (value._key, loaded, s"/${loaded.resourcePath}/declarations/$index", value.source)
      }
    }
    def _unique_[A](values: Vector[(A, LoadedProjection, String, ComponentCapabilityReference.Source)]): Either[Diagnostic, Unit] = {
      values.indices.find(index => values.take(index).exists(_._1 == values(index)._1)) match {
        case Some(index) =>
          val (_, loaded, path, source) = values(index)
          Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated catalog key across package resources.", path)
            .copy(scopeId = Some(acquired.input.scopeId), component = Some(loaded.projection.component.componentId), source = Some(source)))
        case None => Right(())
      }
    }
    for {
      _ <- _unique_(definitions)
      _ <- _unique_(declarations)
    } yield ()
  }
}
