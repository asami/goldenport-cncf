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
 * @version Oct.  8, 2026
 */
private[workflow] final class WorkflowMechanicalTransitionDataStorePersistence[A] private (
  store: DataStore & EntityConditionalTransitionDataStore,
  configuration: Configuration, component: ComponentId, operation: StateMachineOperationIdentity,
  instances: WorkflowInstanceDataStorePersistence, catalog: WorkflowInstanceRecordJsonV1.Catalog,
  instanceCollection: DataStore.CollectionId, checkpointCollection: DataStore.CollectionId,
  codec: PayloadCodec[A], owner: DataStoreComponentOwner
)(using ExecutionContext) {
  import WorkflowInstanceDataStorePersistence.{configurationField, configurationJson, recordField,
    revisionField, storageText, storageJsonC}
  import WorkflowMechanicalTransitionDataStorePersistence.payloadField
  private val configurationBytes = configurationJson(configuration).noSpaces

  def prepareC(intent: Intent[A]): Consequence[WorkflowPreparedDataStoreTransition[Receipt[A]]] =
    for {
      _ <- if (intent != null && intent.configuration == configuration) Consequence.unit
        else Consequence.stateConflict("Mechanical intent differs from its bound configuration")
      receipt <- admitC(component, operation, intent)
      receiptBytes <- WorkflowMechanicalTransitionJsonV1.encodeC(receipt, codec)
      // Re-read the actual native instance; the caller's data is not a guard witness.
      loaded <- instances.load(configuration, receipt.current.identity)
      current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Mechanical instance is absent"))
      currentBytes <- WorkflowInstanceRecordJsonV1.encodeC(current)
      suppliedBytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.current)
      _ <- if (currentBytes == suppliedBytes) Consequence.unit
        else Consequence.stateConflict("Mechanical current instance differs from stored facts")
      nextBytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
      _ <- WorkflowInstanceRecordJsonV1.decodeC(nextBytes, catalog)
      key <- keyC(receipt.reference)
      expected <- EntityRevision.createC(current.revision.value + 1L)
      nextRevision <- expected.nextC
      policy <- DataStoreConditionalValue.from(storageText(configurationBytes))
      root <- DataStoreConditionalRoot.create(owner, instanceCollection, DataStore.StringEntryId(current.identity.value),
        revisionField, Some(expected), Vector(DataStoreConditionalExpectedField(configurationField, policy)),
        Record.dataAuto(recordField -> storageText(nextBytes)), nextRevision)
      successor = Record.dataAuto(revisionField -> 1L, configurationField -> storageText(configurationBytes),
        payloadField -> storageText(receiptBytes))
      pair <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Create(owner,
        checkpointCollection, DataStore.StringEntryId(key), revisionField, successor),
        correlation = DataStoreConditionalCorrelation("workflow-mechanical-transition"))
    } yield WorkflowPreparedDataStoreTransition.create(store, pair, changed => changed match {
      case DataStoreConditionalTransitionResult.Transitioned(rootValue, successorValue) => for {
        actual <- instances.decodeC(rootValue, receipt.next.identity)
        actualBytes <- WorkflowInstanceRecordJsonV1.encodeC(actual)
        retained <- decodeLogicalC(successorValue, receipt.reference)
        retainedBytes <- WorkflowMechanicalTransitionJsonV1.encodeC(retained, codec)
        _ <- if (actualBytes == nextBytes && retainedBytes == receiptBytes) Consequence.unit
          else Consequence.stateConflict("Mechanical pair authoritative readback differs")
      } yield retained
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
          _ <- if (current.identity == receipt.next.identity && current.definition == receipt.next.definition &&
              current.history.take(receipt.next.history.size) == receipt.next.history) Consequence.unit
            else Consequence.stateConflict("Mechanical receipt no longer matches complete native history prefix")
        } yield Some(receipt -> current)
      }
    } yield retained

  private def decodeStoredC(stored: Record, reference: Reference): Consequence[Receipt[A]] = for {
    key <- keyC(reference)
    _ <- if (stored.getString("id").contains(key) && stored.fields.size == 4 &&
        stored.fields.map(_.key).toSet == Set("id", revisionField, configurationField, payloadField)) Consequence.unit
      else Consequence.stateConflict("Mechanical checkpoint physical identity or envelope differs")
    receipt <- decodeLogicalC(Record(stored.fields.filterNot(_.key == "id")), reference)
  } yield receipt

  /** Verified native pairs already carry/check the physical target separately;
    * ordinary loads must pass decodeStoredC's exact physical envelope first.
    */
  private def decodeLogicalC(stored: Record, reference: Reference): Consequence[Receipt[A]] = for {
    revision <- WorkflowContinuationDataStorePersistence.revisionBinding.revision(stored)
    _ <- if (revision.value == 1L && stored.fields.size == 3 &&
        stored.getString(configurationField).contains(storageText(configurationBytes)) &&
        stored.fields.map(_.key).toSet == Set(revisionField, configurationField, payloadField)) Consequence.unit
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
