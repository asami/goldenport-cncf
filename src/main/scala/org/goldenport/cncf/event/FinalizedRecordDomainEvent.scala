package org.goldenport.cncf.event

/** Exact finalized record delivery, not a reconstruction of an arbitrary
  * original DomainEvent subclass. Subscribers can inspect its retained body,
  * sequence and selected persistence status. It grants no storage authority.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final case class FinalizedRecordDomainEvent(record: EventRecord) extends DomainEvent
