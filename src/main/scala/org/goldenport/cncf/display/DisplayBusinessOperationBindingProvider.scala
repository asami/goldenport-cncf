package org.goldenport.cncf.display

import org.goldenport.cncf.component.Component

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** Explicit application registration of inert Action-to-Operation metadata. */
trait DisplayBusinessOperationBindingProvider {
  def displayBusinessOperationBindings(component: Component): Vector[DisplayBusinessOperation.Binding]
}
