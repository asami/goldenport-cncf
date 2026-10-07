package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext

/**
 * Internal fixed-target observation primitive. Not event closure or commit authority.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
private[cncf] final case class DataStoreObservationTarget(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId
)

private[cncf] final class DataStoreObservationPolicy private (
  val cursor: DataStoreObservationTarget,
  val eventCollection: DataStore.CollectionId,
  val indexCollection: DataStore.CollectionId
) {
  def validateC(targets: Vector[DataStoreObservationTarget]): Consequence[Unit] =
    if (targets == null || targets.isEmpty || targets.size > 1281 ||
        targets.exists(t => t == null || t.collection == null || t.entryId == null ||
          t.entryId.print == null || t.entryId.print.isEmpty ||
          t.entryId.print.trim != t.entryId.print ||
          t.entryId.print.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) ||
        targets.distinct.size != targets.size || targets.head != cursor ||
        targets.count(_.collection == eventCollection) > 512 ||
        targets.count(_.collection == indexCollection) > 768 ||
        !targets.tail.forall(t => t.collection == eventCollection || t.collection == indexCollection))
      Consequence.configurationInvalid("Observation requires the pinned cursor first and exact admitted distinct targets")
    else Consequence.unit
}

private[cncf] object DataStoreObservationPolicy {
  def createC(cursor: DataStoreObservationTarget, events: DataStore.CollectionId,
      indexes: DataStore.CollectionId): Consequence[DataStoreObservationPolicy] =
    if (cursor == null || cursor.collection == null || cursor.entryId == null ||
        events == null || indexes == null ||
        Vector(cursor.collection, events, indexes).distinct.size != 3)
      Consequence.configurationInvalid("Observation policy requires distinct declared locations")
    else Consequence.success(new DataStoreObservationPolicy(cursor, events, indexes))
}

private[cncf] final class DataStoreObservationSnapshot private[datastore] (
  private val invocation: DataStoreSpace.ObservationInvocation,
  private val provider: DataStore,
  private val observed: Vector[(DataStoreObservationTarget, Option[Record])],
  private val classification: Option[org.goldenport.cncf.event.ManagedEventDuplicateClassifier.Classification],
  private val preparation: Option[ManagedEventPreparation]
) {
  /** Facts are available only in the issuing host scope. No publication adapter. */
  def recordsC: Consequence[Vector[(DataStoreObservationTarget, Option[Record])]] =
    invocation.validateProviderC(provider).map(_ => observed)

  def classificationC: Consequence[org.goldenport.cncf.event.ManagedEventDuplicateClassifier.Classification] =
    invocation.validateProviderC(provider).flatMap { _ => classification match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.configurationInvalid("Fixed-target observation has no event classification")
    }}

  /** Internal provenance check, not publication permission or a serialized receipt. */
  def validateHostSelectionC(expected: org.goldenport.cncf.event.EventStore): Consequence[Unit] =
    invocation.validateProviderC(provider).flatMap(_ => invocation.validateHostSelectionC(expected))

  def preparationC: Consequence[ManagedEventPreparation] =
    invocation.validateProviderC(provider).flatMap(_ => invocation.validateCapturedSelectionC).flatMap { _ =>
      preparation match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.configurationInvalid("Observation has no host-bound preparation")
      }
    }
}

private[datastore] trait EntityObservationDataStore { self: DataStore =>
  private[datastore] final def observeAdmittedC(invocation: DataStoreSpace.ObservationInvocation)
      (using ExecutionContext): Consequence[DataStoreObservationSnapshot] =
    for {
      _ <- invocation.validateProviderC(this)
      rows <- observe_native(invocation)
      classification <- invocation.eventRequest match {
        case Some(request) => request.classifyC(rows).map(Some(_))
        case None => if (rows != null && rows.map(_._1) == invocation.targets)
          Consequence.success(None) else Consequence.configurationInvalid("Observation response target coverage differs")
      }
      preparation <- if (invocation.validateCapturedSelectionC.toOption.nonEmpty)
        ManagedEventPreparation.captureC(invocation, this, rows, classification.get).map(Some(_))
        else Consequence.success(None)
      _ <- invocation.validateProviderC(this)
    } yield new DataStoreObservationSnapshot(invocation, this, rows, classification, preparation)

  protected def observe_native(invocation: DataStoreSpace.ObservationInvocation)
    (using ExecutionContext): Consequence[Vector[(DataStoreObservationTarget, Option[Record])]]
}
