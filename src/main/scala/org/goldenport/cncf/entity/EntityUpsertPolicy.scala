package org.goldenport.cncf.entity

import org.goldenport.Consequence

/*
 * @since   Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
/** Bounded retry policy for a stable-identity conditional upsert. */
final case class EntityUpsertPolicy(maxAttempts: Int = 8) {
  def validateC: Consequence[EntityUpsertPolicy] =
    if (maxAttempts < 1)
      Consequence.argumentInvalid("maxAttempts", "positive integer", maxAttempts.toString)
    else
      Consequence.success(this)
}

object EntityUpsertPolicy {
  val default: EntityUpsertPolicy = EntityUpsertPolicy()
}
