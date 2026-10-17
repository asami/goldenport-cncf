package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentDescriptor
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityProjection.Projection
import ComponentCapabilityProjectionCodec.*

/*
 * Sole acceptance facade for complete canonical local metadata acquisition.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
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
        _ <- ComponentCapabilityPackageSelectionPart.validate(selected)
        data <- ComponentCapabilityPackageArchivePart.read(selected.location)
        _ <- ComponentCapabilityPackageSelectionPart.validateDescriptor(selected, data.descriptor)
        decoded <- _sequence(data.resources.map { (name, text) => _decode(selected, name, text) })
        _ <- _snapshot_uniqueness(decoded)
      } yield {
        val projections = decoded.map { (name, sourceinput, projection) =>
          new LoadedProjection(name, metadataDirectory + "/" + name, sourceinput, projection)
        }
        new Acquired(selected, data.descriptor, data.index, projections)
      }
      result.left.map(ComponentCapabilityPackageSelectionPart.contextualize(selected, _))
    case null => Left(_malformed("/package", "An explicit package acquisition outcome is required."))
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
