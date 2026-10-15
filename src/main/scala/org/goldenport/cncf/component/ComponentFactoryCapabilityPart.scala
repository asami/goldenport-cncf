package org.goldenport.cncf.component

import org.goldenport.cncf.component.capability.{ComponentCapabilityDiscovery, ComponentCapabilityMetadataProvider, ComponentUsageCapabilityPackageLoader}
import org.goldenport.cncf.component.capability.ComponentCapabilityPackageSource.PackageInput
import org.goldenport.cncf.component.capability.ComponentCapabilityAdmissionScope.Diagnostic

/*
 * Static metadata entry, independent of runtime discovery and bootstrap.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] trait ComponentFactoryCapabilityPart { self: ComponentFactory =>
  def acquireUsageCapabilities(input: PackageInput): Either[Diagnostic, Option[ComponentUsageCapabilityPackageLoader.Acquired]] = {
    ComponentUsageCapabilityPackageLoader.load(input)
  }

  def discoverCapabilities(input: PackageInput, provider: ComponentCapabilityMetadataProvider): Either[Diagnostic, ComponentCapabilityDiscovery.Discovered] = {
    ComponentCapabilityDiscovery.discover(input, provider)
  }
}
