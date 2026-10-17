package org.goldenport.cncf.display

import org.goldenport.cncf.component.Component

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
/** Optional application-owned mutation registrations, admitted before service publication. */
trait DisplayMutationServiceBindingProvider {
  def displayMutationBindings(component: Component): Vector[DisplayMutationRuntime.Binding]
}
