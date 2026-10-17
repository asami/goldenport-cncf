package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityProjectionCodec.*
import ComponentCapabilityProjectionValidationPart.{_component, _text}

/*
 * Exact selected package facts shared by the two fixed metadata families.
 *
 * @since   Oct. 15, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[capability] object ComponentCapabilityPackageSelectionPart {
  def validate(input: PackageInput.Selected): Either[Diagnostic, Unit] = {
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

  def validateDescriptor(input: PackageInput.Selected, descriptor: ComponentDescriptor): Either[Diagnostic, Unit] = {
    Either.cond(descriptor.componentId.contains(input.component.componentId) && descriptor.version.contains(input.component.version), (),
      _diagnostic(DiagnosticKind.Incompatible, "Canonical descriptor and selected Component coordinates differ.", "/descriptor/component"))
  }

  def contextualize(input: PackageInput.Selected, diagnostic: Diagnostic): Diagnostic = {
    diagnostic.copy(scopeId = diagnostic.scopeId.orElse(Option(input.scopeId)),
      component = diagnostic.component.orElse(Option(input.component).flatMap(value => Option(value.componentId))))
  }
}
