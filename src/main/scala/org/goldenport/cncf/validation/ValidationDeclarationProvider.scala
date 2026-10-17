package org.goldenport.cncf.validation

/** Typed trusted-local declaration source, admitted before construction.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
abstract class ValidationDeclarationProvider {
  def declaration: ValidationMetadataV1.Declaration
}
