package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.entity.{EntityRevisionBinding, EntityRevisionRepresentation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Sep. 29, 2026
 * @version Oct. 10, 2026
 */
/** Borrowed canonical datastore adapter. No engine, SQL, resource lifecycle,
  * implicit store selection, command dispatch or cross-store transaction.
  */
final class WorkflowInstanceDataStorePersistence private (
  store: DataStore & EntityVersionedMutationDataStore,
  configuration: Configuration,
  catalog: WorkflowInstanceRecordJsonV1.Catalog,
  collection: DataStore.CollectionId
)(using ExecutionContext) extends WorkflowInstancePersistence {
  import WorkflowInstanceDataStorePersistence.*

  // Legacy SPI Configuration arguments are not storage selectors or permits.
  // The factory-bound provider/collection owns access and current delivery.
  def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
    for {
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(record)
      _ <- catalog.resolveC(record.definition)
      _ <- if (record.revision == initialRevision && record.lifecycle == Lifecycle.NotStarted &&
          record.history.isEmpty) Consequence.unit
        else Consequence.stateConflict("Workflow create requires an initial record")
      initial = Record.dataAuto(
        revisionField -> 1L,
        recordField -> storageText(encoded))
      associated = summon[ExecutionContext].security.session.flatMap(_.sessionId).fold(initial)(session =>
        initial ++ Record.dataAuto(_session_field -> session,
          _session_workflow_field -> record.definition.workflowIdentity.value))
      _ <- store.create(collection, id(record.identity), associated)
    } yield record

  /** Routing candidates, not authority, a latest-instance choice or proof that
    * Start completed. Association is part of the initial create; normal state
    * consumers still own activation, issued work and update conflicts.
    */
  def findBySessionC(sessionId: String,
    workflowIdentity: WorkflowDefinitionIdentity): Consequence[Vector[InstanceRecord]] =
    for {
      searchable <- store match {
        case value: SearchableDataStore => Consequence.success(value)
        case _ => Consequence.configurationInvalid("Workflow session lookup requires searchable storage")
      }
      found <- searchable.search(collection, QueryDirective(Query.Expr(
        org.goldenport.cncf.directive.Query.And(Vector(
          org.goldenport.cncf.directive.Query.Eq(_session_field, sessionId),
          org.goldenport.cncf.directive.Query.Eq(_session_workflow_field, workflowIdentity.value))))))
      // The in-memory search provider does not evaluate the query expression.
      // Consume the actual association here; SQL can push the same selection
      // down without making provider-specific filtering a routing authority.
      selected = found.records.filter(row => row.getString(_session_field).contains(sessionId) &&
        row.getString(_session_workflow_field).contains(workflowIdentity.value))
      records <- selected.foldLeft(Consequence.success(Vector.empty[InstanceRecord])) { (acc, row) =>
        for {
          prior <- acc
          record <- _decode_record_c(row)
        } yield prior :+ record
      }
    } yield records

  def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
    for {
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
    WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
      .flatMap(admitted => _prepare_suspension_c(admitted, componentOwner, continuationCollection, None))

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
      bytes <- WorkflowIssuedWorkDataStorePersistence.encodeC(issued, codec, kind)
      admission = (instance: InstanceRecord, continuation: Continuation) =>
        WorkflowIssuedWorkDataStorePersistence._publication_c(instance,
          continuation, issued, component, completionOperation, kind)
      readback = (instance: InstanceRecord, continuation: ContinuationRuntimePersistence.Record, stored: Record) =>
        for {
          payload <- stored.getString(WorkflowIssuedWorkDataStorePersistence.payloadField).map(Consequence.success)
            .getOrElse(Consequence.stateConflict("Atomic suspension issued work is unavailable"))
          raw <- storageJsonC(payload)
          saved <- WorkflowIssuedWorkDataStorePersistence.decodeC(raw, codec, kind)
          _ <- WorkflowIssuedWorkDataStorePersistence.matchingC(instance, continuation.continuation,
            saved, component, completionOperation, kind)
        } yield ()
      result <- _prepare_suspension_c(admitted, componentOwner, continuationCollection, Some((bytes, admission, readback)))
    } yield result

  private type IssuedReadback = (InstanceRecord, ContinuationRuntimePersistence.Record, Record) => Consequence[Unit]
  private type IssuedAdmission = (InstanceRecord, Continuation) => Consequence[Unit]

  private def _prepare_suspension_c(admitted: WorkflowInstanceAtomicTransitionV1.AdmittedSuspension,
    componentowner: DataStoreComponentOwner, continuationcollection: DataStore.CollectionId,
    issued: Option[(String, IssuedAdmission, IssuedReadback)]
  ): Consequence[WorkflowPreparedDataStoreTransition[PersistedSuspension]] =
    for {
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
      _ <- if (current.definition.workflowIdentity == admitted.current.definition.workflowIdentity &&
          current.definition.workflowRevision == admitted.current.definition.workflowRevision) Consequence.unit
        else Consequence.stateConflict("Atomic suspension executable target differs")
      // The actual source owns lifecycle/history admission. The caller supplies
      // an expected revision, not a copy certificate or an automatic renewal.
      next <- current.appendC(admitted.current.revision, admitted.next.history.last)
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(next)
      expected <- EntityRevision.createC(admitted.current.revision.value + 1L)
      nextrevision <- expected.nextC
      root <- DataStoreConditionalRoot.create(componentowner, collection, id(current.identity),
        revisionField, Some(expected), Vector.empty,
        Record.dataAuto(recordField -> storageText(encoded)), nextrevision)
      initialsuccessor <- continuations.initialStoredC(admitted.continuation)
      // The native Continuation writer owns raw structural admission. Match
      // issued scope only afterwards, never dereference an unadmitted copy.
      _ <- issued match {
        case Some((_, admission, _)) => admission(next, admitted.continuation.continuation)
        case None => Consequence.unit
      }
      successor = issued.fold(initialsuccessor)(value => initialsuccessor ++ Record.dataAuto(
        WorkflowIssuedWorkDataStorePersistence.payloadField -> storageText(value._1)))
      plan <- DataStoreConditionalTransitionPlan.create(root,
        DataStoreConditionalSuccessor.Create(componentowner, continuationcollection,
          WorkflowContinuationDataStorePersistence.id(admitted.continuation.continuation.continuationId),
          WorkflowContinuationDataStorePersistence.revisionField, successor),
        correlation = DataStoreConditionalCorrelation("workflow-instance-suspension"))
    } yield WorkflowPreparedDataStoreTransition.create(compound, plan, changed => changed match {
        case DataStoreConditionalTransitionResult.Transitioned(rootvalue, successorvalue) =>
          (for {
            instance <- decodeC(rootvalue, current.identity)
            continuation <- continuations.decodeStoredC(successorvalue,
              admitted.continuation.continuation.continuationId)
            _ <- issued match {
              case Some((_, _, readback)) => readback(instance, continuation._1, successorvalue)
              case None => Consequence.unit
            }
          } yield PersistedSuspension(instance, continuation._1)).recoverWith { _ =>
            DataStoreConditionalTransitionFailure.transactionIndeterminate(
              "Atomic suspension committed but authoritative readback could not be admitted")
          }
        case _: DataStoreConditionalTransitionResult.NotMatched =>
          Consequence.stateConflict("Atomic suspension expected revision was consumed")
    })

  private[workflow] def decodeC(stored: Record, identity: InstanceIdentity): Consequence[InstanceRecord] =
    for {
      record <- _decode_record_c(stored)
      _ <- if (record.identity == identity) Consequence.unit
        else Consequence.stateConflict("Persisted Workflow identity differs from its address")
    } yield record

  private def _decode_record_c(stored: Record): Consequence[InstanceRecord] =
    for {
      payload <- stored.getString(recordField).map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Persisted Workflow record bytes are missing"))
      raw <- storageJsonC(payload)
      record <- WorkflowInstanceRecordJsonV1.decodeC(raw, catalog)
      revision <- revisionBinding.revision(stored)
      _ <- if (revision.value - 1L == record.revision.value) Consequence.unit
        else Consequence.stateConflict("Persisted Workflow indexed revision differs")
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
  // Legacy copied policy is passive data. New records never produce it and
  // admission never compares it with the actual factory-selected binding.
  private[workflow] val configurationField = "configurationPayload"
  private[workflow] val recordField = "recordPayload"
  private[workflow] val _session_field = "workflowSessionId"
  private[workflow] val _session_workflow_field = "workflowSessionDefinition"
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
    } yield new WorkflowInstanceDataStorePersistence(versioned, configuration, catalog, collection)
}
