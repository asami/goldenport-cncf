package org.goldenport.cncf.datastore

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.event.*
import org.simplemodeling.model.datatype.EntityRevision

/** Live host-bound, immutable observation handoff. Not permission to publish.
  * No mutable Record or input EventRecord is used as validation authority.
  * @since   Sep. 30, 2026
  * @version Oct. 26, 2026
  */
private[cncf] final class ManagedEventPreparation private[ManagedEventPreparation] (
  private val invocation: DataStoreSpace.ObservationInvocation,
  private val provider: DataStore,
  private val facts: Vector[(DataStoreObservationTarget, Option[ManagedEventPreparation.Fact])],
  private val classification: ManagedEventDuplicateClassifier.Classification,
  val policyRevision: String
) {
  private def liveC: Consequence[Unit] =
    invocation.validateProviderC(provider).flatMap(_ => invocation.validateCapturedSelectionC)

  /** Every projection is fresh; callers cannot change frozen observations. */
  def recordsC: Consequence[Vector[(DataStoreObservationTarget, Option[Record])]] =
    liveC.map(_ => facts.map { case (target, fact) => target -> fact.map(_.toRecord) })

  def classificationC: Consequence[ManagedEventDuplicateClassifier.Classification] =
    liveC.map(_ => classification)

  private[datastore] def validateHostC(host: DataStoreSpace.MultiGuardHostAdmission): Consequence[Unit] =
    liveC.flatMap(_ => invocation.validateHostC(host))

  private[datastore] def validateProviderC(actual: DataStore): Consequence[Unit] =
    liveC.flatMap(_ => invocation.validateProviderC(actual))

  /** Exact ordered native observations, never a revision/hash-only check. */
  private[datastore] def validateObservedC(rows: Vector[(DataStoreObservationTarget, Option[Record])]): Consequence[Unit] =
    recordsC.flatMap { expected =>
      if (rows != null && rows.map(_._1) == expected.map(_._1) && rows.zip(expected).forall {
        case ((_, None), (_, None)) => true
        case ((_, Some(actual)), (_, Some(frozen))) => actual != null &&
          actual.fields.map(_.key).distinct.size == actual.fields.size &&
          actual.fields.sortBy(_.key) == frozen.fields.sortBy(_.key)
        case _ => false
      }) Consequence.unit
      else Consequence.stateConflict("Prepared event observations are stale or incomplete")
    }

  private[datastore] def retainedRecordsC: Consequence[Vector[DataStoreMultiGuardRecord]] =
    recordsC.map(_.tail.collect { case (target, Some(record)) =>
      DataStoreMultiGuardRecord(target.collection, target.entryId, record)
    })

  /** Derive only genuinely new Creates and a real cursor advance. All-retained
    * requires its separate validation-only route, never a dummy increment.
    */
  private[datastore] def planC(transition: DataStoreConditionalTransitionPlan): Consequence[DataStoreConditionalMultiGuardPlan] =
    planWithLocalIntentsC(transition, Vector.empty, Vector.empty)

  /** Canonical local intents only, never an executable callback or an authority
    * token. The captured host separately admits every owner/collection/target.
    * One cursor guard slot remains mandatory on this new-durable route.
    */
  private[datastore] def planWithLocalIntentsC(transition: DataStoreConditionalTransitionPlan,
      guards: Vector[DataStoreConditionalRoot], creates: Vector[EntityVersionedSideEffect.Create]):
      Consequence[DataStoreConditionalMultiGuardPlan] =
    for {
      _ <- liveC
      _ <- if (transition != null && transition.sideEffects.isEmpty) Consequence.unit
        else Consequence.configurationInvalid("Prepared event transition requires an effect-free Workflow transition")
      _ <- if (guards != null && creates != null && !guards.exists(_ == null) && !creates.exists(_ == null) &&
          guards.size < DataStoreConditionalMultiGuardPlan.MAX_ADDITIONAL_GUARDS) Consequence.unit
        else Consequence.configurationInvalid("Local intents require bounded guarded updates and Create-only records")
      _ <- if ((guards.map(_.collection) ++ creates.map(_.collection)).exists(Vector(
          invocation.policy.cursor.collection, invocation.policy.eventCollection, invocation.policy.indexCollection).contains))
        Consequence.configurationInvalid("Local intents cannot write reserved EventStore collections")
        else Consequence.unit
      proposal <- classification.cursorProposalC.flatMap(_.map(Consequence.success).getOrElse(
        Consequence.stateConflict("All-retained events require validation-only completion")))
      events <- classification.assignedNewEventsC
      effects <- events.foldLeft[Consequence[Vector[EntityVersionedSideEffect.Create]]](
        Consequence.success(Vector.empty)) { case (acc, event) => acc.flatMap { creates =>
        // Preserve the hidden key through the canonical codec, not source input.
        EventRecordJsonV2.decodeStorageC(EventRecordJsonV2.encodeStorage(event)).flatMap { canonical =>
          for {
            effect <- EventRecordJsonV2.createSideEffectC(invocation.policy.eventCollection, canonical)
            index <- EventDeliveryIndexRecordV1.prepareCreateC(invocation.policy.indexCollection, canonical)
          } yield creates ++ Vector(effect) ++ index.toVector
        }
      }}
      owner <- DataStoreComponentOwner.create(EventStorePersistenceDeclaration.serviceId.name)
      expected <- EntityRevision.createC(proposal.expected.revision)
      next <- EntityRevision.createC(proposal.next.revision)
      schema <- DataStoreConditionalValue.from(EventCursorRecordV1.schema)
      sequence <- DataStoreConditionalValue.from(proposal.expected.lastSequence.toString)
      guard <- DataStoreConditionalRoot.create(owner, invocation.policy.cursor.collection, invocation.policy.cursor.entryId,
        "revision", Some(expected), Vector(DataStoreConditionalExpectedField("cursor_schema", schema),
          DataStoreConditionalExpectedField("last_sequence", sequence)),
        Record(proposal.next.toRecord.fields.filterNot(_.key == "revision")), next)
      combined <- DataStoreConditionalTransitionPlan.create(transition.root, transition.successor,
        effects ++ creates, transition.correlation)
      allGuards = Vector(guard) ++ guards
      scope <- DataStoreConditionalMultiGuardScope.create(transition.root.componentOwner,
        allGuards.map(g => DataStoreConditionalGuardBinding(g.componentOwner, g.collection)))
      plan <- DataStoreConditionalMultiGuardPlan.create(combined, allGuards, scope)
    } yield plan
}

private[cncf] object ManagedEventPreparation {
  private[datastore] sealed trait Fact { def toRecord: Record }
  private final case class Cursor(revision: Long, sequence: Long) extends Fact {
    def toRecord: Record = Record.dataAuto("cursor_schema" -> EventCursorRecordV1.schema,
      "revision" -> revision, "last_sequence" -> sequence.toString)
  }
  private final case class Envelope(bytes: String) extends Fact {
    def toRecord: Record = EventHistoryCorrelation.storedRowC(bytes).toOption.getOrElse(
      throw new IllegalStateException("Previously admitted canonical event cannot be reconstructed"))
  }
  private final case class Index(key: String, reference: String, sequence: String, digest: String) extends Fact {
    def toRecord: Record = Record.dataAuto("index_schema" -> EventDeliveryIndexRecordV1.schema,
      "lookup_key" -> key, "event_reference" -> reference, "sequence" -> sequence,
      "event_body_digest" -> digest)
  }

  /** Called only after native closure and classification, before rows escape. */
  private[datastore] def captureC(invocation: DataStoreSpace.ObservationInvocation, provider: DataStore,
      rows: Vector[(DataStoreObservationTarget, Option[Record])],
      classification: ManagedEventDuplicateClassifier.Classification): Consequence[ManagedEventPreparation] =
    for {
      _ <- invocation.validateProviderC(provider)
      _ <- invocation.validateCapturedSelectionC
      prepared <- freezeC(invocation, provider, rows, classification)
      _ <- invocation.validateProviderC(provider)
    } yield prepared

  private def freezeC(invocation: DataStoreSpace.ObservationInvocation, provider: DataStore,
      rows: Vector[(DataStoreObservationTarget, Option[Record])],
      classification: ManagedEventDuplicateClassifier.Classification): Consequence[ManagedEventPreparation] =
    try {
      val policy = invocation.policy
      def admitted[A](value: Consequence[A]): A = value.toOption.getOrElse(
        throw new IllegalArgumentException("Invalid frozen observation"))
      def text(record: Record, name: String): String = record.fields.find(_.key == name).get.value.single match {
        case value: String => value
        case Some(value: String) => value
        case _ => throw new IllegalArgumentException("Observation field is not exact text")
      }
      val facts = rows.map { case (target, record) => target -> record.map { row =>
        if (target == policy.cursor) {
          val cursor = admitted(EventCursorRecordV1.decodeC(row))
          Cursor(cursor.revision, cursor.lastSequence)
        } else if (target.collection == policy.eventCollection) {
          admitted(EventHistoryCorrelation.admitStoredRowC(row))
          Envelope(text(row, "event_json"))
        } else if (target.collection == policy.indexCollection) {
          admitted(EventDeliveryIndexRecordV1.decodeC(row))
          Index(text(row, "lookup_key"), text(row, "event_reference"), text(row, "sequence"),
            text(row, "event_body_digest"))
        } else throw new IllegalArgumentException("Observation target is outside declaration")
      }}
      require(facts.head._1 == policy.cursor && facts.head._2.nonEmpty && classification != null)
      Consequence.success(new ManagedEventPreparation(invocation, provider, facts, classification,
        invocation.eventRequest.get.policyRevision))
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Managed event preparation cannot freeze observations")
    }
}
