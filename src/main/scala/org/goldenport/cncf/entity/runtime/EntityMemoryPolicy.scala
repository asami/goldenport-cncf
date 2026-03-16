package org.goldenport.cncf.entity.runtime

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait EntityMemoryPolicy

object EntityMemoryPolicy {
  case object StoreOnly extends EntityMemoryPolicy
  case object LoadToMemory extends EntityMemoryPolicy
}
