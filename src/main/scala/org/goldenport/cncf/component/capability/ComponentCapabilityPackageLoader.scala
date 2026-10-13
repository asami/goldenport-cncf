package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityProjection.Projection
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart.{_component, _text}

/*
 * Sole acceptance facade for complete canonical local metadata acquisition.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityPackageLoader {
  final class Acquired private[ComponentCapabilityPackageLoader] (
    val input: PackageInput.Selected,
    val descriptor: ComponentDescriptor,
    val index: Vector[String],
    val projections: Vector[LoadedProjection]
  ) {}

  final class LoadedProjection private[ComponentCapabilityPackageLoader] (
    val filename: String,
    val resourcePath: String,
    val input: SourceInput.Acquired,
    val projection: Projection
  ) {}

  def load(input: PackageInput): Either[Diagnostic, Acquired] = input match {
    case PackageInput.Rejected(diagnostic) => _nonnull(diagnostic, "/package/diagnostic").flatMap(value => Left(value))
    case selected: PackageInput.Selected =>
      val result = for {
        _ <- _validate_selection(selected)
        data <- ComponentCapabilityPackageArchivePart.read(selected.location)
        _ <- Either.cond(data.descriptor.componentId.contains(selected.component.componentId) && data.descriptor.version.contains(selected.component.version), (),
          _diagnostic(DiagnosticKind.Incompatible, "Canonical descriptor and selected Component coordinates differ.", "/descriptor/component"))
        decoded <- _sequence(data.resources.map { (name, text) => _decode(selected, name, text) })
        _ <- _snapshot_uniqueness(decoded)
      } yield {
        val projections = decoded.map { (name, sourceinput, projection) =>
          new LoadedProjection(name, metadataDirectory + "/" + name, sourceinput, projection)
        }
        new Acquired(selected, data.descriptor, data.index, projections)
      }
      result.left.map(error => error.copy(scopeId = error.scopeId.orElse(Option(selected.scopeId)),
        component = error.component.orElse(Option(selected.component).flatMap(value => Option(value.componentId)))))
    case null => Left(_malformed("/package", "An explicit package acquisition outcome is required."))
  }

  private def _validate_selection(input: PackageInput.Selected): Either[Diagnostic, Unit] = {
    for {
      _ <- _text(input.scopeId, "/package/scopeId")
      component <- _nonnull(input.component, "/package/component")
      _ <- _component(component.componentId, "/package/component/componentId")
      _ <- _text(component.version, "/package/component/version")
      release = ComponentReleaseCoordinate.create(component.componentId.sharedIdentity, component.version)
      _ <- Either.cond(release.isSuccess(), (), _malformed("/package/component/version", "A canonical Component release is required."))
      coordinate <- _nonnull(input.packageCoordinate, "/package/package")
      _ <- _text(coordinate.coordinate, "/package/package/coordinate")
      _ <- _text(coordinate.version, "/package/package/version")
    } yield ()
  }

  private def _decode(input: PackageInput.Selected, name: String, text: String): Either[Diagnostic, (String, SourceInput.Acquired, Projection)] = {
    val prefix = "/" + metadataDirectory + "/" + name
    val sourceinput = SourceInput.Acquired(input.scopeId, text)
    for {
      projection <- ComponentCapabilityProjectionCodec.decode(text).left.map(error => error.copy(location = prefix + (if (error.location == "/") "" else error.location)))
      _ <- _context(Either.cond(projection.component == input.component, (),
        _diagnostic(DiagnosticKind.Incompatible, "Projection and selected Component coordinates differ.", prefix + "/component")), Some(projection.component.componentId), Some(projection.source))
      _ <- _context(Either.cond(projection.packageCoordinate == input.packageCoordinate, (),
        _diagnostic(DiagnosticKind.Incompatible, "Projection and selected package coordinates differ.", prefix + "/package")), Some(projection.component.componentId), Some(projection.source))
    } yield (name, sourceinput, projection)
  }

  private def _snapshot_uniqueness(values: Vector[(String, SourceInput.Acquired, Projection)]): Either[Diagnostic, Unit] = {
    def _key_(projection: Projection) = (projection.component.componentId, projection.source.modelAuthority, projection.source.path, projection.source.sourceRevision)
    values.indices.find(index => values.take(index).exists(value => _key_(value._3) == _key_(values(index)._3))) match {
      case Some(index) =>
        val (name, _, projection) = values(index)
        Left(_diagnostic(DiagnosticKind.Ambiguous, "Repeated complete projection source snapshot.", "/" + metadataDirectory + "/" + name + "/source")
          .copy(component = Some(projection.component.componentId), source = Some(projection.source)))
      case None => Right(())
    }
  }
}
