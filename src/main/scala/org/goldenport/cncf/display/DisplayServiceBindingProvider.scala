package org.goldenport.cncf.display

import org.goldenport.cncf.component.Component

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
/** Passive application-owned logical resource mapping, separate from typed sources. */
trait DisplayServiceBindingProvider {
  def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding]
}
