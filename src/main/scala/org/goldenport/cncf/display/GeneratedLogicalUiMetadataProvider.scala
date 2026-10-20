package org.goldenport.cncf.display

/*
 * @since   Oct.  3, 2026
 * @version Oct. 20, 2026
 */
/** Auxiliary provider of raw, untrusted static definitions. */
trait GeneratedLogicalUiMetadataProvider {
  def generatedLogicalUiEnabled: Boolean = true
  def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition]
}
