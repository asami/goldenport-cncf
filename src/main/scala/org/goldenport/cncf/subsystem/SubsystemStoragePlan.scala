package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration
import org.goldenport.cncf.event.{EventPersistencePolicy, EventStore, EventStorePersistenceDeclaration}

/** Validated host data, not source admission or a runtime capability.
  * Validation acquires no resources; only trusted assembly enters its scope.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[cncf] final class SubsystemStoragePlan private (
  val policy: EventPersistencePolicy,
  val persistence: EventStorePersistenceDeclaration,
  val records: Option[ComponentRecordStorageDeclaration]
) {
  def withConstruction[A](assemble: => A): A = {
    def capture: A = EventStoreConstruction.withService(EventStore.inMemory(policy)) {
      EventStorePersistenceConstruction.withDeclaration(persistence)(assemble)
    }
    records match {
      case Some(declaration) => ComponentRecordStorageConstruction.withDeclaration(declaration)(capture)
      case None => capture
    }
  }
}

private[cncf] object SubsystemStoragePlan {
  def createC(policy: EventPersistencePolicy, persistence: EventStorePersistenceDeclaration,
    records: Option[ComponentRecordStorageDeclaration]): Consequence[SubsystemStoragePlan] =
    if (policy == null || persistence == null || records == null || records.exists(_ == null))
      Consequence.configurationInvalid("Host storage plan requires policy and explicit storage declarations")
    else {
      val validation = records.fold(Consequence.unit)(_.validateReservedC(
        Vector(persistence.cursorCollection, persistence.eventCollection, persistence.deliveryIndexCollection) ++
          persistence.workflowGrants.flatMap(g => Vector(g.rootCollection, g.continuationCollection))))
      validation.map(_ => new SubsystemStoragePlan(policy, persistence, records))
    }
}
