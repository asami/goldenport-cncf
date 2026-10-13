package org.goldenport.cncf.event

import org.goldenport.cncf.statemachine.CmlTransitionBinding
import org.simplemodeling.model.datatype.EntityId

/**
 * Test fixture access only; production delivery-key visibility is unchanged.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
private[cncf] object ObservationEventTestSupport {
  def key(event: EventRecord, token: String, entity: EntityId, binding: CmlTransitionBinding): Unit =
    event._delivery_lookup_key = Some(CommittedDeliveryLookupKey(token, entity, binding, "change"))
  def copyKey(from: EventRecord, to: EventRecord): Unit =
    to._delivery_lookup_key = from._delivery_lookup_key
  def clearKey(event: EventRecord): Unit = event._delivery_lookup_key = None
}
