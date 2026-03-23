package org.goldenport.cncf.directive

import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.directive.Update

/*
 * @since   Mar. 16, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait UpdateDirective[+P <: Update.PatchShape] {
  def patch: P
}

object UpdateDirective {
  final case class ById[P <: Update.PatchShape](
    id: EntityId,
    patch: P
  ) extends UpdateDirective[P]

  final case class ByQuery[P <: Update.PatchShape](
    query: Query[?],
    patch: P
  ) extends UpdateDirective[P]
}
