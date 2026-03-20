package org.goldenport.cncf.component

import org.goldenport.cncf.entity.runtime.EntityRuntimePlan

/*
 * Transitional component-side runtime plan provider.
 *
 * Cozy/simplemodeling metadata binding is not wired yet, so components can
 * optionally provide entity runtime plans directly through this interface.
 *
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
trait EntityRuntimePlanProvider {
  def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
    Vector.empty
}

