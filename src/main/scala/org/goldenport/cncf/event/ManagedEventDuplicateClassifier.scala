package org.goldenport.cncf.event

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.record.Record

/** Pure interpretation of supplied observations, NEVER proof of a coherent
  * snapshot or absence. No plan/commit adapter exists until host snapshot and
  * cursor-guard authority are implemented. Legacy memory append is unchanged.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[cncf] object ManagedEventDuplicateClassifier {
  val MAX_OBSERVED_ROWS = 1024
  enum Disposition {
    case New(newSlot: Int)
    case RetainedExisting(canonicalBytes: String)
    case RetainedBatch(newOrdinal: Int)
    case Transient(inputOrdinal: Int)
  }
  final class Classification private[ManagedEventDuplicateClassifier] (
    val dispositions: Vector[Disposition],
    private val newTemplates: Vector[String],
    private val cursor: EventCursorRecordV1
  ) {
    val newDurableCount: Int = newTemplates.size
    /** Fresh canonical clones, sequence unissued. Source mutation cannot alter
      * the retained classification and these facts confer no storage authority.
      */
    def newEventsC: Consequence[Vector[EventRecord]] =
      newTemplates.foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) { (acc, bytes) =>
        acc.flatMap(events => EventRecordJsonV2.decodeStorageC(bytes).map { admitted =>
          val unissued = admitted.copy(sequence = 0L)
          unissued._delivery_lookup_key = admitted._delivery_lookup_key
          events :+ unissued
        })
      }
    def cursorProposalC: Consequence[Option[EventCursorRecordV1.Proposal]] = cursor.proposeC(newDurableCount)
    def assignedNewEventsC: Consequence[Vector[EventRecord]] = for {
      events <- newEventsC
      proposal <- cursorProposalC
    } yield events.zip(proposal.toVector.flatMap(_.sequences)).map { case (event, sequence) =>
      val assigned = event.copy(sequence = sequence)
      assigned._delivery_lookup_key = event._delivery_lookup_key
      assigned
    }

    private[cncf] def finalizedReadbackC(selected: Vector[EventRecord], collection: DataStore.CollectionId,
        rows: Vector[org.goldenport.cncf.datastore.DataStoreMultiGuardRecord]): Consequence[Vector[EventRecord]] = {
      val result = for {
        assigned <- assignedNewEventsC
        finalized <- selected.zip(dispositions).foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) {
          case (acc, (event, disposition)) => acc.flatMap { values =>
            val expected = disposition match {
              case Disposition.Transient(_) => None
              case Disposition.New(slot) => Some(EventRecordJsonV2.encodeStorage(assigned(slot)))
              case Disposition.RetainedBatch(slot) => Some(EventRecordJsonV2.encodeStorage(assigned(slot)))
              case Disposition.RetainedExisting(bytes) => Some(bytes)
            }
            expected match {
              case None => Consequence.success(values :+ event)
              case Some(bytes) =>
                val decode = if (bytes.startsWith("cncf.event-record.v1:")) EventRecordJsonV1.decodeStorageC(bytes)
                  else EventRecordJsonV2.decodeStorageC(bytes)
                decode.flatMap { canonical =>
                  val actual = rows.filter(r => r.collection == collection && r.entryId.print == canonical.id.value)
                  if (actual.size == 1 && actual.head.record.getString("event_json").contains(bytes))
                    // Decode fresh from exactly verified storage, preserving hidden delivery keys.
                    decode.map(values :+ _)
                  else Consequence.stateConflict("Finalized durable delivery lacks exact verified storage")
                }
            }
          }
        }
      } yield finalized
      result.recoverWith(_ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Native completion lacks finalized original-order event projection"))
    }
  }

  /** Inputs are selected facts; retained bytes/rows are an untrusted bounded
    * observation inventory. Physical IDs must be checked by the future host.
    * Explicit V1 read-only recovery is key-free, never failed-V2 fallback.
    */
  def classifyC(
    selected: Vector[EventRecord],
    cursor: EventCursorRecordV1,
    retainedEventBytes: Vector[String],
    retainedIndexRows: Vector[(DataStore.EntryId, Record)]
  ): Consequence[Classification] = safely {
    require(selected != null && cursor != null && retainedEventBytes != null && retainedIndexRows != null,
      "Missing classification inputs")
    require(selected.size <= EventPersistencePolicy.MAX_BATCH_SIZE &&
      retainedEventBytes.size <= MAX_OBSERVED_ROWS && retainedIndexRows.size <= MAX_OBSERVED_ROWS,
      "Classification bounds exceeded")
    val retained = retainedEventBytes.map { bytes =>
      val result = if (bytes != null && bytes.startsWith("cncf.event-record.v1:")) EventRecordJsonV1.decodeStorageC(bytes)
        else EventRecordJsonV2.decodeStorageC(bytes)
      val event = admitted(result)
      require(event.persistent && event.lane == EventLane.Transactional && event.sequence <= cursor.lastSequence,
        "Retained event is outside cursor domain")
      event -> bytes
    }
    require(retained.map(_._1.id).distinct.size == retained.size &&
      retained.map(_._1.sequence).distinct.size == retained.size, "Ambiguous retained identities/sequences")
    require(retainedIndexRows.forall(row => row != null && row._1 != null && row._2 != null) &&
      retainedIndexRows.map(_._1).distinct.size == retainedIndexRows.size, "Ambiguous index rows")
    var byId: Map[EventId, Disposition] = retained.map { case (event, bytes) => event.id -> Disposition.RetainedExisting(bytes) }.toMap
    var byKey = Map.empty[CommittedDeliveryLookupKey, Disposition]
    retainedIndexRows.foreach { case (entry, record) =>
      val index = admitted(EventDeliveryIndexRecordV1.decodeC(record))
      admitted(index.validateLookupC(entry, index.lookupKeyBytes))
      val bytes = retained.find(_._1.id == index.eventId).map(_._2).getOrElse(
        throw new IllegalArgumentException("Dangling retained index"))
      val event = admitted(index.validateEventC(bytes))
      val key = event._delivery_lookup_key.get
      require(!byKey.contains(key), "Ambiguous retained delivery key")
      byKey = byKey.updated(key, Disposition.RetainedExisting(bytes))
    }
    retained.foreach { case (event, bytes) => event._delivery_lookup_key.foreach { key =>
      require(byKey.get(key).contains(Disposition.RetainedExisting(bytes)), "Retained keyed event has no exact index")
    }}
    var templates = Vector.empty[String]
    val dispositions = selected.zipWithIndex.map { case (event, inputOrdinal) =>
      require(event != null && event.id != null && event._delivery_lookup_key != null &&
        event.lane == EventLane.Transactional && event.sequence == 0L, "Invalid selected facts")
      if (!event.persistent) Disposition.Transient(inputOrdinal)
      else {
        EventDeliveryIndexRecordV1.reference(event.id) // fully issued structural ID, not rendered parsing
        event._delivery_lookup_key.foreach(CommittedDeliveryKeyJsonV1.encodeStorage)
        val idMatch = byId.get(event.id)
        val keyMatch = event._delivery_lookup_key.flatMap(byKey.get)
        require(!(idMatch.nonEmpty && keyMatch.nonEmpty && idMatch != keyMatch),
          "EventId and delivery key name different retained facts")
        idMatch.orElse(keyMatch) match {
          case Some(Disposition.New(ordinal)) => Disposition.RetainedBatch(ordinal)
          case Some(retained) => retained
          case None =>
            val ordinal = templates.size
            // Sequence 1 is a private shape-admission template only. It is
            // discarded when returning unissued facts, never reserved/stored.
            val template = event.copy(sequence = 1L)
            template._delivery_lookup_key = event._delivery_lookup_key
            templates :+= EventRecordJsonV2.encodeStorage(template)
            val source = Disposition.New(ordinal)
            byId = byId.updated(event.id, source)
            event._delivery_lookup_key.foreach(key => byKey = byKey.updated(key, source))
            source
        }
      }
    }
    // Only new durable facts consume sequence/revision capacity.
    admitted(cursor.proposeC(templates.size))
    new Classification(dispositions, templates, cursor)
  }

  private def admitted[A](result: Consequence[A]): A = result.toOption.getOrElse(
    throw new IllegalArgumentException("Observation canonical admission failed"))
  private def safely[A](f: => A): Consequence[A] = try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("Managed duplicate classification facts are invalid or inconsistent")
  }
}
