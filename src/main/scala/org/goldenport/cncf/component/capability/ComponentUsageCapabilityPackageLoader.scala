package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentDescriptor
import ComponentCapabilityAdmissionScope.*
import ComponentCapabilityPackageSource.*
import ComponentCapabilityPackageIndexPart.MetadataFamily
import ComponentCapabilityProjectionCodec.*

/*
 * Optional raw Usage transport; semantic interpretation belongs to the consumer.
 *
 * @since   Oct. 15, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentUsageCapabilityPackageLoader {
  final class Acquired private[ComponentUsageCapabilityPackageLoader] (
    val input: PackageInput.Selected,
    val descriptor: ComponentDescriptor,
    val resourcePath: String,
    val content: String
  ) {}

  def load(input: PackageInput): Either[Diagnostic, Option[Acquired]] = input match {
    case PackageInput.Rejected(diagnostic) => _nonnull(diagnostic, "/package/diagnostic").flatMap(value => Left(value))
    case selected: PackageInput.Selected =>
      val result = for {
        _ <- ComponentCapabilityPackageSelectionPart.validate(selected)
        data <- ComponentCapabilityPackageArchivePart.readUsage(selected.location)
        _ <- ComponentCapabilityPackageSelectionPart.validateDescriptor(selected, data.descriptor)
      } yield {
        if (data.present) Some(new Acquired(selected, data.descriptor, MetadataFamily.Usage.directory + "/usage.json", data.resources.head._2))
        else None
      }
      result.left.map(ComponentCapabilityPackageSelectionPart.contextualize(selected, _))
    case null => Left(_malformed("/package", "An explicit package acquisition outcome is required."))
  }
}
