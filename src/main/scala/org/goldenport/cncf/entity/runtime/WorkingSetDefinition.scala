package org.goldenport.cncf.entity.runtime

/*
 * @since   Mar. 15, 2026
 * @version Mar. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WorkingSetDefinition[E](
  entityName: String,
  entities: IterableOnce[E]
)
