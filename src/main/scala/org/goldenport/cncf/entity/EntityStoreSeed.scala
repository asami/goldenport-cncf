package org.goldenport.cncf.entity

/*
 * @since   Mar. 27, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityStoreSeedEntry[T](
  entity: T
)

final case class EntityStoreSeed[T](
  entries: Vector[EntityStoreSeedEntry[T]]
)
