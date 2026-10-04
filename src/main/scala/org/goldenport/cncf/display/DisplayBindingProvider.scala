package org.goldenport.cncf.display

import org.goldenport.cncf.component.Component

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Application-owned typed source registrations, selected after semantic bootstrap. */
trait DisplayBindingProvider {
  def displayBindings(component: Component): Vector[DisplayRuntime.Binding]
}
