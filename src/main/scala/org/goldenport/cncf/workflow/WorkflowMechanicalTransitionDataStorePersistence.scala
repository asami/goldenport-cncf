package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/**
 * Private borrowed adapter: prepares a pair, never owns or commits a store.
 * @since   Oct.  2, 2026
 * @version Oct.  9, 2026
 */
private[workflow] final class WorkflowMechanicalTransitionDataStorePersistence[A] private (
  store: DataStore & EntityConditionalTransitionDataStore,
  configuration: Configuration, component: ComponentId, operation: StateMachineOperationIdentity,
  instances: WorkflowInstanceDataStorePersistence, catalog: WorkflowInstanceRecordJsonV1.Catalog,
  instanceCollection: DataStore.CollectionId, checkpointCollection: DataStore.CollectionId,
  codec: PayloadCodec[A], owner: DataStoreComponentOwner
)(using ExecutionContext) {
  import WorkflowInstanceDataStorePersistence.{configurationField, recordField,
    revisionField, storageText, storageJsonC}
  import WorkflowMechanicalTransitionDataStorePersistence.payloadField

  def prepareC(intent: Intent[A]): Consequence[WorkflowPreparedDataStoreTransition[Receipt[A]]] =
    for {
      _ <- if (intent != null && intent.current != null) Consequence.unit
        else Consequence.stateConflict("Mechanical intent target is missing")
      // Re-read the actual native instance; the caller's data is not a guard witness.
      loaded <- instances.load(configuration, intent.current.identity)
      current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Mechanical instance is absent"))
      receipt <- admitC(component, operation, intent.copy(configuration = configuration, current = current))
      receiptBytes <- WorkflowMechanicalTransitionJsonV1.encodeC(receipt, codec)
      nextBytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
      key <- keyC(receipt.reference)
      expected <- EntityRevision.createC(current.revision.value + 1L)
      nextRevision <- expected.nextC
      root <- DataStoreConditionalRoot.create(owner, instanceCollection, DataStore.StringEntryId(current.identity.value),
        revisionField, Some(expected), Vector.empty,
        Record.dataAuto(recordField -> storageText(nextBytes)), nextRevision)
      successor = Record.dataAuto(revisionField -> 1L,
        payloadField -> storageText(receiptBytes))
      pair <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Create(owner,
        checkpointCollection, DataStore.StringEntryId(key), revisionField, successor),
        correlation = DataStoreConditionalCorrelation("workflow-mechanical-transition"))
    } yield WorkflowPreparedDataStoreTransition.create(store, pair, changed => changed match {
      case DataStoreConditionalTransitionResult.Transitioned(rootValue, successorValue) => for {
        actual <- instances.decodeC(rootValue, receipt.next.identity)
        retained <- decodeLogicalC(successorValue, receipt.reference)
      } yield retained.copy(next = actual)
      case _ => Consequence.stateConflict("Mechanical revision or checkpoint Create was refused")
    })

  def loadRetainedC(reference: Reference): Consequence[Option[(Receipt[A], InstanceRecord)]] =
    for {
      key <- keyC(reference)
      row <- store.load(checkpointCollection, DataStore.StringEntryId(key))
      retained <- row match {
        case None => Consequence.success(None)
        case Some(value) => for {
          receipt <- decodeStoredC(value, reference)
          loaded <- instances.load(configuration, receipt.next.identity)
          current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Retained mechanical instance is absent"))
        } yield Some(receipt -> current)
      }
    } yield retained

  private def decodeStoredC(stored: Record, reference: Reference): Consequence[Receipt[A]] = for {
    key <- keyC(reference)
    fields = stored.fields.map(_.key).toSet
    _ <- if (stored.getString("id").contains(key) && stored.fields.size == fields.size &&
        (fields == Set("id", revisionField, payloadField) ||
          fields == Set("id", revisionField, configurationField, payloadField))) Consequence.unit
      else Consequence.stateConflict("Mechanical checkpoint physical identity or envelope differs")
    receipt <- decodeLogicalC(Record(stored.fields.filterNot(_.key == "id")), reference)
  } yield receipt

  /** Verified native pairs already carry/check the physical target separately;
    * ordinary loads must pass decodeStoredC's exact physical envelope first.
    */
  private def decodeLogicalC(stored: Record, reference: Reference): Consequence[Receipt[A]] = for {
    revision <- WorkflowContinuationDataStorePersistence.revisionBinding.revision(stored)
    fields = stored.fields.map(_.key).toSet
    // Legacy configurationPayload is inert; current host binding owns policy.
    _ <- if (revision.value == 1L && stored.fields.size == fields.size &&
        (fields == Set(revisionField, payloadField) ||
          fields == Set(revisionField, configurationField, payloadField))) Consequence.unit
      else Consequence.stateConflict("Mechanical checkpoint native envelope differs")
    payload <- stored.getString(payloadField).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Mechanical checkpoint payload is absent"))
    raw <- storageJsonC(payload)
    receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(raw, catalog, codec)
    _ <- if (receipt.reference == reference && receipt.component == component && receipt.operation == operation)
      Consequence.unit else Consequence.stateConflict("Mechanical checkpoint owner, operation or reference differs")
  } yield receipt
}

private[workflow] object WorkflowMechanicalTransitionDataStorePersistence {
  val payloadField = "mechanicalPayload"
  def bindC[A](store: DataStore, configuration: Configuration, component: ComponentId,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId, codec: PayloadCodec[A],
    owner: DataStoreComponentOwner)(using ExecutionContext): Consequence[WorkflowMechanicalTransitionDataStorePersistence[A]] =
    for {
      _ <- (instances, checkpoints) match {
        case (a: DataStore.CollectionId.EntityStore, b: DataStore.CollectionId.EntityStore)
            if a.collectionName != b.collectionName => Consequence.unit
        case _ => Consequence.configurationInvalid("Mechanical pair requires distinct explicit EntityStore collections")
      }
      compound <- store match {
        case value: EntityConditionalTransitionDataStore if store.isAccept(instances) && store.isAccept(checkpoints) =>
          Consequence.success(value.asInstanceOf[DataStore & EntityConditionalTransitionDataStore])
        case _ => Consequence.configurationInvalid("Mechanical pair requires the same canonical compound provider")
      }
      catalog <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      adapter <- WorkflowInstanceDataStorePersistence.bindC(store, configuration, definitions, instances)
    } yield new WorkflowMechanicalTransitionDataStorePersistence(compound, configuration, component, operation,
      adapter, catalog, instances, checkpoints, codec, owner)
}
