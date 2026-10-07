package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.event.{EventPersistencePolicy, EventRecord, EventStore, EventLane}

/** Pure, frozen prepare-time selection. This object neither writes nor
  * dispatches and is not a commit receipt or an authority token.
  * Only persistent records receive sequences, event Creates and delivery-index
  * Creates in the subsequent host-admitted transaction.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowEventSelection private (
  val policyRevision: String,
  val events: Vector[EventRecord]
) {
  val persistent: Vector[EventRecord] = events.filter(_.persistent)
  val transient: Vector[EventRecord] = events.filterNot(_.persistent)

  /** Pure planning projection. The runtime must supply its verified commit
    * outcome; merely supplying this enum does not authorize publication.
    * Durable events use authoritative stored readback, not these unissued
    * records. Transient events have no replay or crash-recovery guarantee.
    */
  def transientDeliveryCandidates(outcome: WorkflowEventSelection.Outcome): Vector[EventRecord] =
    if (outcome == WorkflowEventSelection.Outcome.CommitAndReadbackVerified) transient
    else Vector.empty
}

object WorkflowEventSelection {
  /** Runtime entry: Workflow consumes the store's decision, never sender policy flags. */
  def prepareFromStoreC(store: EventStore, records: Vector[EventRecord]): Consequence[WorkflowEventSelection] =
    if (store == null || records == null || records.exists(record => record == null || record.lane != EventLane.Transactional))
      Consequence.configurationInvalid("Workflow requires an EventStore and transactional records")
    else store.preparePersistenceC(records).map(selected =>
      new WorkflowEventSelection(selected.policyRevision, selected.events))

  enum Outcome {
    case Pending, CommitAndReadbackVerified, RolledBack, TransactionIndeterminate
  }

  /** Compatibility pure planning helper; runtime selects through EventStore. */
  def prepareC(policy: EventPersistencePolicy, records: Vector[EventRecord]):
      Consequence[WorkflowEventSelection] =
    if (policy == null) Consequence.configurationInvalid("Workflow requires an explicit event persistence policy")
    else policy.selectC(records).map(selected => new WorkflowEventSelection(policy.revision, selected))
}
