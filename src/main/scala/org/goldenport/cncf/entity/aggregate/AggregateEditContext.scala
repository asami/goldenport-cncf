package org.goldenport.cncf.entity.aggregate

import java.time.Instant
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Jun. 14, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AggregateEditContext[A](
  contextId: String,
  aggregateName: String,
  aggregateId: EntityId,
  baseToken: String,
  owner: AggregateEditOwner,
  lockScope: AggregateEditLockScope,
  workingAggregate: A,
  dirty: Boolean,
  createdAt: Instant,
  updatedAt: Instant,
  metadata: Record = Record.empty
) {
  def touch(now: Instant): AggregateEditContext[A] =
    copy(updatedAt = now)

  def update(value: A, now: Instant): AggregateEditContext[A] =
    copy(workingAggregate = value, dirty = true, updatedAt = now)
}
