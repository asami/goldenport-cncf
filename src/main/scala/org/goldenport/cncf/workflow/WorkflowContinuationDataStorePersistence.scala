package org.goldenport.cncf.workflow

import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.entity.{EntityRevisionBinding, EntityRevisionRepresentation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.Configuration
import org.goldenport.record.{Record as DataRecord}
import org.simplemodeling.model.datatype.EntityRevision

/** Borrowed canonical CAS adapter. No claim expiry, recovery replay, resource
  * ownership or cross-record transaction is inferred by this SPI.
  * @since   Sep. 29, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowContinuationDataStorePersistence private (
  store: DataStore & EntityVersionedMutationDataStore,
  configuration: Configuration,
  collection: DataStore.CollectionId
)(using ExecutionContext) extends ContinuationRuntimePersistence {
  import ContinuationRuntimePersistence.*
  import WorkflowContinuationDataStorePersistence.*
  import WorkflowInstanceDataStorePersistence.{configurationField, configurationJson, storageText, storageJsonC}

  private val configurationBytes = configurationJson(configuration).noSpaces

  def createC(record: Record): Consequence[Unit] =
    initialStoredC(record).flatMap(value =>
      store.create(collection, id(record.continuation.continuationId), value))

  def loadC(identity: ContinuationIdentity): Consequence[Option[Record]] =
    loadStoredC(identity).map(_.map(_._1))

  def claimC(identity: ContinuationIdentity): Consequence[Record] =
    transitionC(identity) { record =>
      if (record.status == Status.Available)
        Consequence.success(record.copy(status = Status.Claimed, claimId = Some(UUID.randomUUID().toString)))
      else Consequence.stateConflict("Continuation is not available for claim")
    }

  def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[Record] =
    withClaimC(identity, claimId)(_.copy(status = Status.Available, claimId = None))

  def completeC(identity: ContinuationIdentity, claimId: String): Consequence[Record] =
    withClaimC(identity, claimId)(_.copy(status = Status.Completed))

  private def withClaimC(identity: ContinuationIdentity, claimId: String)(
    f: Record => Record
  ): Consequence[Record] =
    if (claimId == null || claimId.trim.isEmpty)
      Consequence.stateConflict("Continuation claim token is missing")
    else transitionC(identity) { record =>
      if (record.status == Status.Claimed && record.claimId.contains(claimId))
        Consequence.success(f(record))
      else Consequence.stateConflict("Continuation claim token or status differs")
    }

  private def transitionC(identity: ContinuationIdentity)(
    f: Record => Consequence[Record]
  ): Consequence[Record] =
    for {
      loaded <- loadStoredC(identity)
      current <- loaded.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Continuation is unavailable"))
      next <- f(current._1)
      bytes <- ContinuationRecordJsonV1.encodeC(next)
      revision <- current._2.nextC
      changed <- store.compareAndSetEntity(EntityCompareAndSetMutationPlan(
        collection, id(identity), revisionField, current._2,
        DataRecord.dataAuto(payloadField -> storageText(bytes)),
        EntityMutationReadbackRequirement.AuthoritativeRecord))
      stored <- changed match {
        case EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(value)) =>
          Consequence.success(value)
        case EntityMutationProviderResult.Applied(_) =>
          EntityVersionedMutationFailure.transactionIndeterminate(
            "Continuation mutation was applied without authoritative readback")
        case _ => Consequence.stateConflict("Continuation expected storage revision was not applied")
      }
      result <- (for {
        decoded <- decodeStoredC(stored, identity)
        _ <- if (decoded._1 == next && decoded._2 == revision) Consequence.unit
          else Consequence.stateConflict("Continuation authoritative readback differs")
      } yield decoded._1).recoverWith { _ =>
        EntityVersionedMutationFailure.transactionIndeterminate(
          "Continuation mutation committed but authoritative readback could not be admitted")
      }
    } yield result

  private[workflow] def loadStoredC(identity: ContinuationIdentity): Consequence[Option[(Record, EntityRevision)]] =
    if (identity == null || identity.value == null || identity.value.trim.isEmpty)
      Consequence.stateConflict("Continuation identity is missing")
    else store.load(collection, id(identity)).flatMap {
      case Some(value) => decodeStoredC(value, identity).map(Some(_))
      case None => Consequence.success(None)
    }

  private[workflow] def initialStoredC(record: Record): Consequence[DataRecord] =
    for {
      bytes <- ContinuationRecordJsonV1.encodeC(record)
      _ <- if (record.status == Status.Available && record.claimId.isEmpty) Consequence.unit
        else Consequence.stateConflict("Continuation create requires unclaimed Available state")
    } yield DataRecord.dataAuto(revisionField -> 1L,
      configurationField -> storageText(configurationBytes), payloadField -> storageText(bytes))

  private[workflow] def decodeStoredC(stored: DataRecord, identity: ContinuationIdentity):
      Consequence[(Record, EntityRevision)] =
    for {
      _ <- if (stored.getString(configurationField).contains(storageText(configurationBytes))) Consequence.unit
        else Consequence.stateConflict("Persisted Continuation store policy differs")
      payload <- stored.getString(payloadField).map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Persisted Continuation payload is missing"))
      raw <- storageJsonC(payload)
      record <- ContinuationRecordJsonV1.decodeC(raw)
      revision <- revisionBinding.revision(stored)
      _ <- if (record.continuation.continuationId == identity) Consequence.unit
        else Consequence.stateConflict("Persisted Continuation identity differs")
    } yield (record, revision)
}

object WorkflowContinuationDataStorePersistence {
  private[workflow] val revisionBinding = EntityRevisionBinding(EntityRevisionRepresentation.Detached)
  private[workflow] val revisionField = revisionBinding.storageFieldName
  private[workflow] val payloadField = "continuationPayload"
  private[workflow] def id(identity: ContinuationIdentity): DataStore.EntryId =
    DataStore.StringEntryId(identity.value)

  def bindC(store: DataStore, configuration: Configuration, collection: DataStore.CollectionId)(
    using ExecutionContext
  ): Consequence[WorkflowContinuationDataStorePersistence] =
    if (store == null || configuration == null || collection == null ||
        collection.collectionName == null || collection.collectionName.trim.isEmpty)
      Consequence.configurationInvalid("Continuation datastore binding is incomplete")
    else for {
      _ <- configuration.validateC
      versioned <- store match {
        case value: EntityVersionedMutationDataStore if value.entityMutationProviderCapabilities.supportsAll(
            Set(EntityMutationProviderFeature.OptimisticCompareAndSet,
              EntityMutationProviderFeature.AuthoritativeRecordResult)) =>
          Consequence.success(value.asInstanceOf[DataStore & EntityVersionedMutationDataStore])
        case _ => Consequence.configurationInvalid("Continuation datastore requires atomic CAS and authoritative readback")
      }
    } yield new WorkflowContinuationDataStorePersistence(versioned, configuration, collection)
}
