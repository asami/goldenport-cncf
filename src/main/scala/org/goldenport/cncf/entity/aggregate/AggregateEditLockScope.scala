package org.goldenport.cncf.entity.aggregate

/*
 * @since   Jun. 14, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
enum AggregateEditLockScope {
  case Principal
  case Session

  def ownerKey(owner: AggregateEditOwner): Option[String] =
    this match {
      case Principal => owner.principalKey
      case Session => owner.sessionKey.orElse(owner.principalKey)
    }
}
