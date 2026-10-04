package domain.logicalui

import org.goldenport.cncf.display.GeneratedLogicalUiAbi

trait DomainLogicalUiMetadataProvider extends org.goldenport.cncf.display.GeneratedLogicalUiMetadataProvider {
  override def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition] =
    DomainLogicalUiDefinitions.definitions
}
