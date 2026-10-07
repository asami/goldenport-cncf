package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, DataStoreMultiGuardReadbackResult, EntityVersionedSideEffect}
import org.goldenport.cncf.event.{EventDeliveryIndexRecordV1, EventRecord, EventRecordJsonV1, EventRecordJsonV2}

/** Pure selected-only event/index Create proposal. No provider, cursor allocation,
  * delivery lookup, commit receipt or dispatch authority is held here.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowSelectedEventRecords private (
  val policyRevision: String,
  val proposedLastSequence: Long,
  val persistent: Vector[EventRecord],
  val creates: Vector[EntityVersionedSideEffect.Create],
  private val selected: WorkflowEventSelection,
  private val canonicalBytes: Vector[String],
  private val encodeStored: EventRecord => String = EventRecordJsonV1.encodeStorage,
  private val decodeStored: String => Consequence[EventRecord] = EventRecordJsonV1.decodeStorageC,
  private[cncf] val indexCreates: Vector[EntityVersionedSideEffect.Create] = Vector.empty
) {
  val requiresCursorUpdate: Boolean = persistent.nonEmpty

  /** Project only canonical event bytes from complete verified storage evidence.
    * This returns candidates, never a dispatch permit or Workflow completion.
    * Host runtime still owns cursor/index and application completion admission.
    */
  private[cncf] def deliveryCandidatesFromVerifiedStorageC(
    verified: DataStoreMultiGuardReadbackResult.Verified
  ): Consequence[Vector[EventRecord]] =
    if (verified == null) Consequence.stateConflict("Verified selected-event storage is missing")
    else _verify_indexes_c(verified).flatMap { _ => creates.foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) {
      (acc, effect) => acc.flatMap { events =>
        val matched = verified.records.filter(item =>
          item.collection == effect.collection && item.entryId == effect.entryId)
        if (matched.size != 1) Consequence.stateConflict("Verified storage is missing a selected event")
        else matched.head.record.getString("event_json") match {
          case Some(bytes) => decodeStored(bytes).map(events :+ _)
          case None => Consequence.stateConflict("Verified storage has no canonical event bytes")
        }
      }
    }.flatMap(admitDeliveryCandidatesC) }

  private def _verify_indexes_c(verified: DataStoreMultiGuardReadbackResult.Verified): Consequence[Unit] =
    indexCreates.foldLeft(Consequence.unit) { (acc, effect) => acc.flatMap { _ =>
      val rows = verified.records.filter(r => r.collection == effect.collection && r.entryId == effect.entryId)
      if (rows.size != 1) Consequence.stateConflict("Verified storage is missing a selected delivery index")
      else for {
        expected <- EventDeliveryIndexRecordV1.decodeC(effect.record)
        actual <- EventDeliveryIndexRecordV1.decodeC(rows.head.record)
        _ <- actual.validateLookupC(effect.entryId, expected.lookupKeyBytes)
        _ <- if (actual.toRecord == expected.toRecord) Consequence.unit
          else Consequence.stateConflict("Verified delivery index differs from its proposal")
        event <- creates.find(_.entryId.print == actual.eventId.value)
          .map(Consequence.success).getOrElse(Consequence.stateConflict("Delivery index reference is outside selected events"))
        eventRows = verified.records.filter(r => r.collection == event.collection && r.entryId == event.entryId)
        bytes <- if (eventRows.size == 1) eventRows.head.record.getString("event_json")
          .map(Consequence.success).getOrElse(Consequence.stateConflict("Delivery index event envelope is missing"))
          else Consequence.stateConflict("Delivery index event reference is missing or ambiguous")
        _ <- actual.validateEventC(bytes).map(_ => ())
      } yield ()
    }}

  /** Pure readback comparison, not proof of transaction completion. The host
    * must independently admit commit/root/Continuation/cursor/index readback.
    * Durable candidates are reconstructed from canonical stored bytes; a
    * transient candidate is never reconstructed from EventStore recovery.
    */
  def admitDeliveryCandidatesC(stored: Vector[EventRecord]): Consequence[Vector[EventRecord]] = {
    try {
      if (stored == null || stored.size != persistent.size || stored.exists(_ == null) ||
          stored.map(encodeStored) != canonicalBytes)
        Consequence.stateConflict("Selected event authoritative readback differs from the prepared durable subset")
      else canonicalBytes.foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) {
        (acc, bytes) => acc.flatMap(records => decodeStored(bytes).map(records :+ _))
      }.map { durable =>
        val byId = durable.map(record => record.id -> record).toMap
        selected.events.map(record => if (record.persistent) byId(record.id) else record)
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Selected event authoritative readback is invalid")
    }
  }
}

object WorkflowSelectedEventRecords {
  /** Explicit V2 preparation. V1 remains strict and no format fallback exists.
    * Creates only new facts; collisions are not retained/deduplicated here.
    */
  private[cncf] def prepareV2C(
    selection: WorkflowEventSelection,
    eventCollection: DataStore.CollectionId,
    indexCollection: DataStore.CollectionId,
    lastSequence: Long
  ): Consequence[WorkflowSelectedEventRecords] =
    if (selection == null || eventCollection == null || indexCollection == null ||
        eventCollection == indexCollection || lastSequence < 0L ||
        (selection != null && lastSequence > Long.MaxValue - selection.persistent.size.toLong))
      Consequence.configurationInvalid("V2 selected events require distinct collections and an overflow-safe cursor")
    else selection.persistent.zipWithIndex.foldLeft[
      Consequence[Vector[(EventRecord, EntityVersionedSideEffect.Create, Option[EntityVersionedSideEffect.Create])]]](
      Consequence.success(Vector.empty)) { case (acc, (record, index)) => acc.flatMap { prepared =>
        for {
          event <- EventRecordJsonV2.prepareCreateC(eventCollection, record, lastSequence + index.toLong + 1L)
          delivery <- EventDeliveryIndexRecordV1.prepareCreateC(indexCollection, event._1)
        } yield prepared :+ (event._1, event._2, delivery)
      }}.map { prepared =>
        val creates = prepared.map(_._2)
        new WorkflowSelectedEventRecords(selection.policyRevision, lastSequence + prepared.size.toLong,
          prepared.map(_._1), creates, selection, creates.map(_.record.getString("event_json").get),
          EventRecordJsonV2.encodeStorage, EventRecordJsonV2.decodeStorageC, prepared.flatMap(_._3))
      }

  /** The last sequence is a host-read cursor proposal, never an allocated
    * range. The host must include its exact expected revision in the atomic
    * transaction and cannot replay a proposal after an uncertain commit.
    */
  def prepareC(
    selection: WorkflowEventSelection,
    eventCollection: DataStore.CollectionId,
    lastSequence: Long
  ): Consequence[WorkflowSelectedEventRecords] =
    if (selection == null || eventCollection == null || lastSequence < 0L ||
        (selection != null && lastSequence > Long.MaxValue - selection.persistent.size.toLong))
      Consequence.configurationInvalid("Selected event preparation requires a collection and overflow-safe nonnegative cursor proposal")
    else selection.persistent.zipWithIndex.foldLeft[
      Consequence[Vector[(EventRecord, EntityVersionedSideEffect.Create)]]](Consequence.success(Vector.empty)) {
        case (acc, (record, index)) => acc.flatMap { prepared =>
          EventRecordJsonV1.prepareCreateC(eventCollection, record, lastSequence + index.toLong + 1L)
            .map(prepared :+ _)
        }
      }.map { prepared =>
        val creates = prepared.map(_._2)
        new WorkflowSelectedEventRecords(selection.policyRevision,
          lastSequence + prepared.size.toLong, prepared.map(_._1), creates, selection,
          creates.map(_.record.getString("event_json").get))
      }
}
