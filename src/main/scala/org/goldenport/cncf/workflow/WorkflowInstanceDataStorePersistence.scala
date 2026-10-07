package org.goldenport.cncf.workflow

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.entity.{EntityRevisionBinding, EntityRevisionRepresentation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
/** Borrowed canonical datastore adapter. No engine, SQL, resource lifecycle,
  * implicit store selection, command dispatch or cross-store transaction.
  */
final class WorkflowInstanceDataStorePersistence private (
  store: DataStore & EntityVersionedMutationDataStore,
  configuration: Configuration,
  catalog: WorkflowInstanceRecordJsonV1.Catalog,
  collection: DataStore.CollectionId,
  configurationBytes: String
)(using ExecutionContext) extends WorkflowInstancePersistence {
  import WorkflowInstanceDataStorePersistence.*

  def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
    for {
      _ <- matchingConfigurationC(config)
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(record)
      _ <- WorkflowInstanceRecordJsonV1.decodeC(encoded, catalog)
      _ <- if (record.revision == initialRevision && record.lifecycle == Lifecycle.NotStarted &&
          record.history.isEmpty) Consequence.unit
        else Consequence.stateConflict("Workflow create requires an initial record")
      _ <- store.create(collection, id(record.identity), Record.dataAuto(
        revisionField -> 1L, configurationField -> storageText(configurationBytes),
        recordField -> storageText(encoded)))
    } yield record

  def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
    for {
      _ <- matchingConfigurationC(config)
      _ <- identityC(identity)
      stored <- store.load(collection, id(identity))
      decoded <- stored match {
        case Some(value) => decodeC(value, identity).map(Some(_))
        case None => Consequence.success(None)
      }
    } yield decoded

  def append(config: Configuration, identity: InstanceIdentity,
    expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
    for {
      loaded <- load(config, identity)
      current <- loaded.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Workflow instance is unavailable"))
      next <- current.appendC(expectedRevision, entry)
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(next)
      expected <- EntityRevision.createC(expectedRevision.value + 1L)
      changed <- store.compareAndSetEntity(EntityCompareAndSetMutationPlan(
        collection, id(identity), revisionField, expected,
        Record.dataAuto(recordField -> storageText(encoded)),
        EntityMutationReadbackRequirement.AuthoritativeRecord))
      stored <- changed match {
        case EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(value)) =>
          Consequence.success(value)
        case _: EntityMutationProviderResult.Stale =>
          Consequence.stateConflict("Workflow expected revision was consumed by another writer")
        case _ => Consequence.stateConflict("Workflow append lacks authoritative applied readback")
      }
      decoded <- decodeC(stored, identity)
      actual <- WorkflowInstanceRecordJsonV1.encodeC(decoded)
      _ <- if (actual == encoded) Consequence.unit
        else Consequence.stateConflict("Workflow append readback differs from the admitted next record")
    } yield decoded

  /** One canonical compound datastore commit, not an EventEngine/UoW enlistment.
    * No issued payload, evidence or external effect is published by this slice.
    */
  def suspendC(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    componentOwner: DataStoreComponentOwner, continuationCollection: DataStore.CollectionId
  ): Consequence[PersistedSuspension] =
    prepareSuspensionC(intent, componentOwner, continuationCollection).flatMap(_.commitC())

  /** Stored-fact admission only; discarding the prepared value publishes nothing. */
  def prepareSuspensionC(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    componentOwner: DataStoreComponentOwner, continuationCollection: DataStore.CollectionId
  ): Consequence[WorkflowPreparedDataStoreTransition[PersistedSuspension]] =
    _prepare_suspension_c(intent, componentOwner, continuationCollection, None)

  /** Initial issued bytes are co-located in the unique successor, not a later
    * side-effect Save or callback. This still does not enlist active UoW effects.
    */
  private[workflow] def suspendIssuedC[W](intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    componentOwner: DataStoreComponentOwner, continuationCollection: DataStore.CollectionId,
    issued: WorkflowProtocolV1.WorkflowInteraction[W, Nothing],
    codec: WorkflowResultJsonV1.PayloadCodec[W],
    component: org.goldenport.cncf.component.ComponentId,
    completionOperation: StateMachineOperationIdentity
  ): Consequence[PersistedSuspension] =
    prepareIssuedSuspensionC(intent, componentOwner, continuationCollection, issued, codec, component,
      completionOperation).flatMap(_.commitC())

  private[workflow] def prepareIssuedSuspensionC[W](intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    componentOwner: DataStoreComponentOwner, continuationCollection: DataStore.CollectionId,
    issued: WorkflowProtocolV1.WorkflowInteraction[W, Nothing],
    codec: WorkflowResultJsonV1.PayloadCodec[W],
    component: org.goldenport.cncf.component.ComponentId,
    completionOperation: StateMachineOperationIdentity,
    kind: WorkflowIssuedContinuationKind = WorkflowIssuedContinuationKind.WorkOrder
  ): Consequence[WorkflowPreparedDataStoreTransition[PersistedSuspension]] =
    for {
      admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
      _ <- ContinuationRecordJsonV1.encodeC(admitted.continuation)
      bytes <- WorkflowIssuedWorkDataStorePersistence.encodeExactC(issued, codec, kind)
      _ <- WorkflowIssuedWorkDataStorePersistence.matchingC(admitted.next,
        admitted.continuation.continuation, issued, component, completionOperation, kind)
      result <- _prepare_suspension_c(intent, componentOwner, continuationCollection, Some(bytes))
    } yield result

  private def _prepare_suspension_c(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    componentowner: DataStoreComponentOwner, continuationcollection: DataStore.CollectionId,
    issuedbytes: Option[String]
  ): Consequence[WorkflowPreparedDataStoreTransition[PersistedSuspension]] =
    for {
      admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
      _ <- matchingConfigurationC(admitted.configuration)
      _ <- configuration.delivery match {
        case _: DeliveryConfiguration.SameStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Atomic suspension requires same-store delivery")
      }
      _ <- (collection, continuationcollection) match {
        case (a: DataStore.CollectionId.EntityStore, b: DataStore.CollectionId.EntityStore)
            if a.collectionName != b.collectionName => Consequence.unit
        case _ => Consequence.configurationInvalid("Atomic suspension requires distinct explicit EntityStore collections")
      }
      compound <- store match {
        case value: EntityConditionalTransitionDataStore =>
          Consequence.success(value.asInstanceOf[DataStore & EntityConditionalTransitionDataStore])
        case _ => Consequence.configurationInvalid("Atomic suspension requires canonical compound transition capability")
      }
      continuations <- WorkflowContinuationDataStorePersistence.bindC(store, configuration, continuationcollection)
      loaded <- load(configuration, admitted.current.identity)
      current <- loaded.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Workflow instance is unavailable"))
      actual <- WorkflowInstanceRecordJsonV1.encodeC(current)
      supplied <- WorkflowInstanceRecordJsonV1.encodeC(admitted.current)
      _ <- if (actual == supplied) Consequence.unit
        else Consequence.stateConflict("Atomic suspension current instance differs from stored facts")
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(admitted.next)
      expected <- EntityRevision.createC(admitted.current.revision.value + 1L)
      nextrevision <- expected.nextC
      policy <- DataStoreConditionalValue.from(storageText(configurationBytes))
      root <- DataStoreConditionalRoot.create(componentowner, collection, id(current.identity),
        revisionField, Some(expected), Vector(DataStoreConditionalExpectedField(configurationField, policy)),
        Record.dataAuto(recordField -> storageText(encoded)), nextrevision)
      initialsuccessor <- continuations.initialStoredC(admitted.continuation)
      successor = issuedbytes.fold(initialsuccessor)(bytes => initialsuccessor ++ Record.dataAuto(
        WorkflowIssuedWorkDataStorePersistence.payloadField -> storageText(bytes)))
      plan <- DataStoreConditionalTransitionPlan.create(root,
        DataStoreConditionalSuccessor.Create(componentowner, continuationcollection,
          WorkflowContinuationDataStorePersistence.id(admitted.continuation.continuation.continuationId),
          WorkflowContinuationDataStorePersistence.revisionField, successor),
        correlation = DataStoreConditionalCorrelation("workflow-instance-suspension"))
    } yield WorkflowPreparedDataStoreTransition.create(compound, plan, changed => changed match {
        case DataStoreConditionalTransitionResult.Transitioned(rootvalue, successorvalue) =>
          (for {
            instance <- decodeC(rootvalue, current.identity)
            bytes <- WorkflowInstanceRecordJsonV1.encodeC(instance)
            continuation <- continuations.decodeStoredC(successorvalue,
              admitted.continuation.continuation.continuationId)
            _ <- if (issuedbytes.forall(bytes => successorvalue.getString(
                WorkflowIssuedWorkDataStorePersistence.payloadField).contains(storageText(bytes))))
              Consequence.unit
            else Consequence.stateConflict("Atomic suspension issued-work readback differs")
            _ <- if (bytes == encoded && continuation._1 == admitted.continuation &&
                continuation._2 == EntityRevision.INITIAL) Consequence.unit
              else Consequence.stateConflict("Atomic suspension authoritative readback differs")
          } yield PersistedSuspension(instance, continuation._1)).recoverWith { _ =>
            DataStoreConditionalTransitionFailure.transactionIndeterminate(
              "Atomic suspension committed but authoritative readback could not be admitted")
          }
        case _: DataStoreConditionalTransitionResult.NotMatched =>
          Consequence.stateConflict("Atomic suspension expected revision or store policy was consumed")
    })

  private def matchingConfigurationC(config: Configuration): Consequence[Unit] =
    if (config == configuration) Consequence.unit
    else Consequence.stateConflict("Workflow store configuration differs from the bound provider")

  private[workflow] def decodeC(stored: Record, identity: InstanceIdentity): Consequence[InstanceRecord] =
    for {
      _ <- if (stored.getString(configurationField).contains(storageText(configurationBytes))) Consequence.unit
        else Consequence.stateConflict("Persisted Workflow store policy differs")
      payload <- stored.getString(recordField).map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Persisted Workflow record bytes are missing"))
      raw <- storageJsonC(payload)
      record <- WorkflowInstanceRecordJsonV1.decodeC(raw, catalog)
      revision <- revisionBinding.revision(stored)
      _ <- if (record.identity == identity && revision.value - 1L == record.revision.value) Consequence.unit
        else Consequence.stateConflict("Persisted Workflow identity or indexed revision differs")
    } yield record
}

object WorkflowInstanceDataStorePersistence {
  final case class PersistedSuspension(instance: InstanceRecord,
    continuation: ContinuationRuntimePersistence.Record)

  // Canonical Entity CAS uses positive revisions; Workflow revision zero maps
  // to storage revision one. Storage overflow rejects the append before write.
  private val revisionBinding = EntityRevisionBinding(EntityRevisionRepresentation.Detached)
  private[workflow] val revisionField = revisionBinding.storageFieldName
  // Canonical property names survive SQL column normalization. Prefix opaque
  // JSON bytes so SqlDataStore's structured-value decoder cannot reinterpret
  // nulls, arrays or nested ABI/history facts as Record fields.
  private[workflow] val configurationField = "configurationPayload"
  private[workflow] val recordField = "recordPayload"
  private val storagePrefix = "cncf.workflow-json.v1:"
  private[workflow] def storageText(json: String): String = storagePrefix + json
  private[workflow] def storageJsonC(payload: String): Consequence[String] =
    if (payload.startsWith(storagePrefix)) Consequence.success(payload.substring(storagePrefix.length))
    else Consequence.stateConflict("Persisted Workflow payload encoding is unsupported")
  private def id(identity: InstanceIdentity): DataStore.EntryId = DataStore.StringEntryId(identity.value)
  private def identityC(identity: InstanceIdentity): Consequence[Unit] =
    if (identity != null && identity.value != null && identity.value.trim.nonEmpty) Consequence.unit
    else Consequence.stateConflict("Workflow instance identity is missing")

  def bindC(store: DataStore, configuration: Configuration,
    definitions: Vector[DefinitionBinding], collection: DataStore.CollectionId
  )(using ExecutionContext): Consequence[WorkflowInstanceDataStorePersistence] =
    if (store == null || configuration == null || collection == null ||
        collection.collectionName == null || collection.collectionName.trim.isEmpty)
      Consequence.configurationInvalid("Workflow datastore binding is incomplete")
    else for {
      _ <- configuration.validateC
      catalog <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      versioned <- store match {
        case value: EntityVersionedMutationDataStore if value.entityMutationProviderCapabilities.supportsAll(
            Set(EntityMutationProviderFeature.OptimisticCompareAndSet,
              EntityMutationProviderFeature.AuthoritativeRecordResult)) =>
          Consequence.success(value.asInstanceOf[DataStore & EntityVersionedMutationDataStore])
        case _ => Consequence.configurationInvalid("Workflow datastore requires atomic CAS and authoritative readback")
      }
    } yield new WorkflowInstanceDataStorePersistence(versioned, configuration,
      catalog, collection, configurationJson(configuration).noSpaces)

  private[workflow] def configurationJson(config: Configuration): Json = {
    val delivery = config.delivery match {
      case DeliveryConfiguration.SameStore(entity) =>
        Json.obj("kind" -> Json.fromString("same-store"), "entityStore" -> Json.fromString(entity.value))
      case DeliveryConfiguration.CrossStoreCommittedTransition(entity, committed, key, recovery) =>
        Json.obj("kind" -> Json.fromString("cross-store"), "entityStore" -> Json.fromString(entity.value),
          "committedTransition" -> Json.fromString(committed.value),
          "idempotency" -> Json.fromString(key.value), "recoverability" -> Json.fromString(recovery.value))
    }
    Json.obj("schemaVersion" -> Json.fromString("cncf.workflow-store-binding.v1"),
      "storeName" -> Json.fromString(config.workflowStore.name),
      "storeIdentity" -> Json.fromString(config.workflowStore.identity.value),
      "migrationPolicy" -> Json.fromString(config.migrationPolicy.value),
      "retentionPolicy" -> Json.fromString(config.retentionPolicy.value),
      "leasePolicy" -> Json.fromString(config.leasePolicy.value), "delivery" -> delivery)
  }
}
