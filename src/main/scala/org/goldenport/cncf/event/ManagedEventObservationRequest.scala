package org.goldenport.cncf.event

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.datastore.{DataStore, DataStoreObservationPolicy, DataStoreObservationTarget}
import org.goldenport.cncf.workflow.WorkflowEventSelection

/** Frozen lookup facts; only an opaque DataStoreSpace invocation grants reads.
  * Expansion is deterministic and bounded, never a caller transaction callback.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[cncf] final class ManagedEventObservationRequest private (
  val policyRevision: String,
  private val selected: Vector[EventRecord],
  private val policy: DataStoreObservationPolicy,
  private val incomingKeys: Vector[String]
) {
  private type Rows = Vector[(DataStoreObservationTarget, Option[Record])]
  private def eventTarget(id: EventId) = DataStoreObservationTarget(policy.eventCollection, DataStore.StringEntryId(id.value))
  private def keyTarget(bytes: String) = DataStoreObservationTarget(policy.indexCollection,
    DataStore.StringEntryId("delivery-v1-" + EventDeliveryIndexRecordV1.digest(bytes)))
  val initialTargets: Vector[DataStoreObservationTarget] = (Vector(policy.cursor) ++
    selected.filter(_.persistent).map(e => eventTarget(e.id)) ++ incomingKeys.map(keyTarget)).distinct

  def referencedTargetsC(rows: Rows): Consequence[Vector[DataStoreObservationTarget]] = safely {
    require(rows.map(_._1) == initialTargets, "Initial target coverage differs")
    incomingKeys.flatMap { bytes => rows.find(_._1 == keyTarget(bytes)).get._2.map { row =>
      val index = admitted(EventDeliveryIndexRecordV1.decodeC(row))
      admitted(index.validateLookupC(keyTarget(bytes).entryId, bytes))
      eventTarget(index.eventId)
    }}.distinct
  }

  def intrinsicTargetsC(rows: Rows): Consequence[Vector[DataStoreObservationTarget]] = safely {
    eventFacts(rows).flatMap { case (_, event) =>
      event._delivery_lookup_key.map(key => keyTarget(CommittedDeliveryKeyJsonV1.encodeStorage(key)))
    }.distinct
  }

  def classifyC(rows: Rows): Consequence[ManagedEventDuplicateClassifier.Classification] = safely {
    val refs = admitted(referencedTargetsC(rows.take(initialTargets.size)))
    val second = (initialTargets ++ refs).distinct
    require(rows.take(second.size).map(_._1) == second, "Referenced target coverage differs")
    refs.foreach(t => require(rows.find(_._1 == t).flatMap(_._2).nonEmpty, "Referenced event is missing"))
    val intrinsic = admitted(intrinsicTargetsC(rows.take(second.size)))
    val complete = (second ++ intrinsic).distinct
    require(rows.map(_._1) == complete, "Closure target coverage differs")
    intrinsic.foreach(t => require(rows.find(_._1 == t).flatMap(_._2).nonEmpty, "Intrinsic index is missing"))
    admitted(policy.validateC(complete))
    val cursorRow = rows.head._2.getOrElse(throw new IllegalArgumentException("Cursor is absent"))
    val cursor = admitted(EventCursorRecordV1.decodeC(cursorRow))
    val events = eventFacts(rows).map(_._1)
    val indexes = rows.collect { case (t, Some(row)) if t.collection == policy.indexCollection => t.entryId -> row }
    admitted(ManagedEventDuplicateClassifier.classifyC(selected, cursor, events, indexes))
  }

  private def eventFacts(rows: Rows): Vector[(String, EventRecord)] = rows.collect {
    case (target, Some(row)) if target.collection == policy.eventCollection =>
      require(row.fields.size == 1 && row.fields.head.key == "event_json", "Unknown event fields")
      val bytes = row.getString("event_json").getOrElse(throw new IllegalArgumentException("Missing event envelope"))
      val event = admitted(if (bytes.startsWith("cncf.event-record.v1:")) EventRecordJsonV1.decodeStorageC(bytes)
        else EventRecordJsonV2.decodeStorageC(bytes))
      require(eventTarget(event.id) == target, "Event physical identity differs from envelope")
      bytes -> event
  }
  private def admitted[A](c: Consequence[A]): A = c.toOption.getOrElse(throw new IllegalArgumentException("Invalid closure"))
  private def safely[A](f: => A): Consequence[A] = try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("Managed event observation closure is invalid")
  }
}

private[cncf] object ManagedEventObservationRequest {
  /** Capture only after EventStore selection. Transient-only work uses no cursor
    * and must use its separate completion route, not this durable observation.
    */
  def createC(selection: WorkflowEventSelection, policy: DataStoreObservationPolicy):
      Consequence[ManagedEventObservationRequest] = try {
    require(selection != null && policy != null && selection.policyRevision != null &&
      selection.policyRevision.nonEmpty && selection.events.size <= EventPersistencePolicy.MAX_BATCH_SIZE &&
      selection.events.exists(_.persistent), "Missing durable selection")
    val frozen = selection.events.map { event =>
      require(event != null && event.id != null && event.sequence == 0L &&
        event.lane == EventLane.Transactional && event._delivery_lookup_key != null, "Invalid selected fact")
      val clone = event.copy()
      clone._delivery_lookup_key = if (event.persistent) {
        EventDeliveryIndexRecordV1.reference(event.id)
        event._delivery_lookup_key.map { key =>
          CommittedDeliveryKeyJsonV1.decodeStorageC(CommittedDeliveryKeyJsonV1.encodeStorage(key)).toOption.get
        }
      } else event._delivery_lookup_key
      clone
    }
    val keys = frozen.filter(_.persistent).flatMap(_._delivery_lookup_key.map(CommittedDeliveryKeyJsonV1.encodeStorage)).distinct
    val request = new ManagedEventObservationRequest(selection.policyRevision, frozen, policy, keys)
    policy.validateC(request.initialTargets).map(_ => request)
  } catch { case NonFatal(_) => Consequence.stateConflict("Managed event lookup request is invalid") }
}
