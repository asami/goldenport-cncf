package org.goldenport.cncf.event

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.record.Record

/** Pure interpretation of supplied observations, NEVER proof of a coherent
  * snapshot or absence. No plan/commit adapter exists until host snapshot and
  * cursor-guard authority are implemented. Legacy memory append is unchanged.
  * @since   Sep. 30, 2026
  * @version Oct. 12, 2026
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
    private val _new_templates: Vector[String],
    private val _cursor: EventCursorRecordV1
  ) {
    val newDurableCount: Int = _new_templates.size
    /** Fresh canonical clones, sequence unissued. Source mutation cannot alter
      * the retained classification and these facts confer no storage authority.
      */
    def newEventsC: Consequence[Vector[EventRecord]] =
      _new_templates.foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) { (acc, bytes) =>
        acc.flatMap(events => EventRecordJsonV2.decodeStorageC(bytes).map { admitted =>
          val unissued = admitted.copy(sequence = 0L)
          unissued._delivery_lookup_key = admitted._delivery_lookup_key
          events :+ unissued
        })
      }
    def cursorProposalC: Consequence[Option[EventCursorRecordV1.Proposal]] = _cursor.proposeC(newDurableCount)
    def assignedNewEventsC: Consequence[Vector[EventRecord]] = {
       for {
      events <- newEventsC
      proposal <- cursorProposalC
    } yield events.zip(proposal.toVector.flatMap(_.sequences)).map { case (event, sequence) =>
      val assigned = event.copy(sequence = sequence)
      assigned._delivery_lookup_key = event._delivery_lookup_key
      assigned
    }
    }

    private[cncf] def finalizedReadbackC(selected: Vector[EventRecord], collection: DataStore.CollectionId,
        rows: Vector[org.goldenport.cncf.datastore.DataStoreMultiGuardRecord]): Consequence[Vector[EventRecord]] = {
      val result = for {
        _ <- if (selected != null && collection != null && rows != null &&
            selected.size == dispositions.size && !selected.contains(null) &&
            !rows.contains(null)) Consequence.unit
          else Consequence.stateConflict("Finalized event projection inputs are incomplete")
        assigned <- assignedNewEventsC
        finalized <- selected.zip(dispositions).foldLeft[Consequence[Vector[EventRecord]]](Consequence.success(Vector.empty)) {
          case (acc, (event, disposition)) => acc.flatMap { values =>
            val target = disposition match {
              case Disposition.Transient(_) => None
              case Disposition.New(slot) => Some(Consequence.success(assigned(slot).id))
              case Disposition.RetainedBatch(slot) => Some(Consequence.success(assigned(slot).id))
              case Disposition.RetainedExisting(bytes) => Some(_decode_stored_c(bytes).map(_.id))
            }
            target match {
              case None => Consequence.success(values :+ event)
              case Some(identity) => identity.flatMap { id =>
                  val actual = rows.filter(r => r.collection == collection && r.entryId != null && r.entryId.print == id.value)
                  if (actual.size != 1 || actual.head.record == null)
                    Consequence.stateConflict("Finalized durable delivery lacks one actual target row")
                  else actual.head.record.getString("event_json") match {
                    case Some(bytes) => _decode_stored_c(bytes).flatMap { retained =>
                      if (retained.id == id && retained.persistent &&
                          retained.lane == EventLane.Transactional && retained.sequence > 0L)
                        Consequence.success(values :+ retained)
                      else Consequence.stateConflict("Finalized durable event identity or native domain differs")
                    }
                    case None => Consequence.stateConflict("Finalized durable event bytes are missing")
                  }
                }
            }
          }
        }
      } yield finalized
      result.recoverWith(_ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Native completion lacks finalized original-order event projection"))
    }
  }

  private def _decode_stored_c(bytes: String): Consequence[EventRecord] = {
    if (bytes != null && bytes.startsWith("cncf.event-record.v1:")) EventRecordJsonV1.decodeStorageC(bytes)
    else EventRecordJsonV2.decodeStorageC(bytes)

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
  ): Consequence[Classification] = _safely {
    require(selected != null && cursor != null && retainedEventBytes != null && retainedIndexRows != null,
      "Missing classification inputs")
    require(selected.size <= EventPersistencePolicy.MAX_BATCH_SIZE &&
      retainedEventBytes.size <= MAX_OBSERVED_ROWS && retainedIndexRows.size <= MAX_OBSERVED_ROWS,
      "Classification bounds exceeded")
    val retained = retainedEventBytes.map { bytes =>
      val result = if (bytes != null && bytes.startsWith("cncf.event-record.v1:")) EventRecordJsonV1.decodeStorageC(bytes)
        else EventRecordJsonV2.decodeStorageC(bytes)
      val event = _admitted(result)
      require(event.persistent && event.lane == EventLane.Transactional && event.sequence <= cursor.lastSequence,
        "Retained event is outside cursor domain")
      event -> bytes
    }
    require(retained.map(_._1.id).distinct.size == retained.size &&
      retained.map(_._1.sequence).distinct.size == retained.size, "Ambiguous retained identities/sequences")
    require(retainedIndexRows.forall(row => row != null && row._1 != null && row._2 != null) &&
      retainedIndexRows.map(_._1).distinct.size == retainedIndexRows.size, "Ambiguous index rows")
    var byid: Map[EventId, Disposition] = retained.map { case (event, bytes) => event.id -> Disposition.RetainedExisting(bytes) }.toMap
    var bykey = Map.empty[CommittedDeliveryLookupKey, Disposition]
    retainedIndexRows.foreach { case (entry, record) =>
      val index = _admitted(EventDeliveryIndexRecordV1.decodeC(record))
      _admitted(index.validateLookupC(entry, index.lookupKeyBytes))
      val bytes = retained.find(_._1.id == index.eventId).map(_._2).getOrElse(
        throw new IllegalArgumentException("Dangling retained index"))
      val event = _admitted(index.validateEventC(bytes))
      val key = event._delivery_lookup_key.get
      require(!bykey.contains(key), "Ambiguous retained delivery key")
      bykey = bykey.updated(key, Disposition.RetainedExisting(bytes))
    }
    retained.foreach { case (event, bytes) => event._delivery_lookup_key.foreach { key =>
      require(bykey.get(key).contains(Disposition.RetainedExisting(bytes)), "Retained keyed event has no exact index")
    }}
    var templates = Vector.empty[String]
    val dispositions = selected.zipWithIndex.map { case (event, inputordinal) =>
      require(event != null && event.id != null && event._delivery_lookup_key != null &&
        event.lane == EventLane.Transactional && event.sequence == 0L, "Invalid selected facts")
      if (!event.persistent) Disposition.Transient(inputordinal)
      else {
        EventDeliveryIndexRecordV1.reference(event.id) // fully issued structural ID, not rendered parsing
        event._delivery_lookup_key.foreach(CommittedDeliveryKeyJsonV1.encodeStorage)
        val idmatch = byid.get(event.id)
        val keymatch = event._delivery_lookup_key.flatMap(bykey.get)
        require(!(idmatch.nonEmpty && keymatch.nonEmpty && idmatch != keymatch),
          "EventId and delivery key name different retained facts")
        idmatch.orElse(keymatch) match {
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
            byid = byid.updated(event.id, source)
            event._delivery_lookup_key.foreach(key => bykey = bykey.updated(key, source))
            source
        }
      }
    }
    // Only new durable facts consume sequence/revision capacity.
    _admitted(cursor.proposeC(templates.size))
    new Classification(dispositions, templates, cursor)
  }

  private def _admitted[A](result: Consequence[A]): A = result.toOption.getOrElse(
    throw new IllegalArgumentException("Observation canonical admission failed"))
  private def _safely[A](f: => A): Consequence[A] = {
     try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("Managed duplicate classification facts are invalid or inconsistent")
  }
  }
}
