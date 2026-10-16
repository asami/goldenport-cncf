package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.event.{EventCursorRecordV1, EventRecord, EventStorePersistenceDeclaration}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/**
 * Pure host-configured proposal, not a reservation, commit or dispatch permit.
 * @since   Sep. 30, 2026
 * @version Oct. 12, 2026
 */
private[cncf] final class WorkflowCursorEventProposal private (
  val events: WorkflowSelectedEventRecords,
  val cursorGuard: Option[DataStoreConditionalRoot]
) {
  /** Adds the durable subset and cursor to one pure plan. Host admission and
    * native verified commit remain mandatory; all-transient uses another route.
    */
  def multiGuardPlanC(transition: DataStoreConditionalTransitionPlan): Consequence[DataStoreConditionalMultiGuardPlan] = {
    if (transition == null) Consequence.configurationInvalid("Workflow transition is missing")
    else cursorGuard match {
      case None => Consequence.stateConflict("No durable events: use the verified two-record completion route")
      case Some(guard) => for {
        combined <- DataStoreConditionalTransitionPlan.create(transition.root, transition.successor,
          transition.sideEffects ++ events.creates ++ events.indexCreates)
        scope <- DataStoreConditionalMultiGuardScope.create(transition.root.componentOwner,
          Vector(DataStoreConditionalGuardBinding(guard.componentOwner, guard.collection)))
        plan <- DataStoreConditionalMultiGuardPlan.create(combined, Vector(guard), scope)
      } yield plan
    }
  }

  def deliveryCandidatesFromVerifiedStorageC(
    verified: DataStoreMultiGuardReadbackResult.Verified
  ): Consequence[Vector[EventRecord]] = {
    val admitted = cursorGuard match {
      case None => events.deliveryCandidatesFromVerifiedStorageC(verified)
      case Some(guard) if verified != null =>
        val records = verified.records.filter(r => r.collection == guard.collection && r.entryId == guard.entryId)
        if (records.size != 1) Consequence.stateConflict("Verified storage is missing the proposed cursor")
        else EventCursorRecordV1.decodeC(records.head.record).flatMap { actual =>
          events.deliveryCandidatesFromVerifiedStorageC(verified).flatMap { candidates =>
            if (candidates.filter(_.persistent).forall(record =>
                record.sequence > 0L && record.sequence <= actual.lastSequence))
              Consequence.success(candidates)
            else Consequence.stateConflict("Selected durable events are outside the actual cursor domain")
          }
        }
      case _ => Consequence.stateConflict("Verified cursor storage is missing")
    }
    admitted
  }
}

private[cncf] object WorkflowCursorEventProposal {
  def prepareC(
    selection: WorkflowEventSelection,
    declaration: EventStorePersistenceDeclaration,
    loadedCursor: EventCursorRecordV1
  ): Consequence[WorkflowCursorEventProposal] = _prepare_c(selection, declaration, loadedCursor, false)

  /** Explicit format choice by trusted host assembly, never content sniffing. */
  def prepareV2C(selection: WorkflowEventSelection, declaration: EventStorePersistenceDeclaration,
    loadedCursor: EventCursorRecordV1): Consequence[WorkflowCursorEventProposal] =
    _prepare_c(selection, declaration, loadedCursor, true)

  private def _prepare_c(selection: WorkflowEventSelection, declaration: EventStorePersistenceDeclaration,
    loadedcursor: EventCursorRecordV1, v2: Boolean): Consequence[WorkflowCursorEventProposal] = {
    if (selection == null || declaration == null || loadedcursor == null)
      Consequence.configurationInvalid("Cursor event proposal requires selection, host declaration and loaded cursor")
    else for {
      cursor <- loadedcursor.proposeC(selection.persistent.size)
      events <- if (v2) WorkflowSelectedEventRecords.prepareV2C(selection, declaration.eventCollection,
        declaration.deliveryIndexCollection, loadedcursor.lastSequence)
        else WorkflowSelectedEventRecords.prepareC(selection, declaration.eventCollection, loadedcursor.lastSequence)
      guard <- cursor match {
        case None => Consequence.success(None)
        case Some(proposal) => for {
          owner <- DataStoreComponentOwner.create(EventStorePersistenceDeclaration.serviceId.name)
          expected <- EntityRevision.createC(proposal.expected.revision)
          next <- EntityRevision.createC(proposal.next.revision)
          schema <- DataStoreConditionalValue.from(EventCursorRecordV1.schema)
          sequence <- DataStoreConditionalValue.from(proposal.expected.lastSequence.toString)
          root <- DataStoreConditionalRoot.create(owner, declaration.cursorCollection, declaration.cursorEntryId,
            "revision", Some(expected), Vector(
              DataStoreConditionalExpectedField("cursor_schema", schema),
              DataStoreConditionalExpectedField("last_sequence", sequence)),
            Record(proposal.next.toRecord.fields.filterNot(_.key == "revision")), next)
        } yield Some(root)
      }
    } yield new WorkflowCursorEventProposal(events, guard)
  }
}
