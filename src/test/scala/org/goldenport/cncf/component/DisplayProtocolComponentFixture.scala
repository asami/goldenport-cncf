package org.goldenport.cncf.component

import org.goldenport.cncf.display.GeneratedLogicalUiAbi

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
/** Test-only access to the exact wrapper adopted by normal component bootstrap. */
private[cncf] object DisplayProtocolComponentFixture {
  def admittedDefinitions(component: Component): Option[GeneratedLogicalUiAbi.AdmittedDefinitions] =
    component._admitted_logical_ui_definitions
}
