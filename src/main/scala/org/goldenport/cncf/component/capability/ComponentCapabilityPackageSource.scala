package org.goldenport.cncf.component.capability

import java.nio.file.Path
import ComponentCapabilityProjection.{ComponentCoordinate, PackageCoordinate}
import ComponentCapabilityAdmissionScope.Diagnostic

/*
 * Explicit caller selection; construction confers no acquisition authority.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityPackageSource {
  val metadataDirectory: String = "meta/component-capability"
  val indexPath: String = "meta/component-capability/index.json"
  val indexVersion: Int = 1

  enum Location {
    case Directory(root: Path)
    case Car(path: Path)
  }

  enum PackageInput {
    case Selected(scopeId: String, location: Location, component: ComponentCoordinate, packageCoordinate: PackageCoordinate)
    case Rejected(diagnostic: Diagnostic)
  }
}
