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
 * @version Oct. 21, 2026
 */
private[workflow] final class WorkflowMechanicalTransitionDataStorePersistence[A] private (
  store: DataStore & EntityConditionalTransitionDataStore,
  configuration: Configuration, component: ComponentId, operation: StateMachineOperationIdentity,
  instances: WorkflowInstanceDataStorePersistence, catalog: WorkflowInstanceRecordJsonV1.Catalog,
  instancecollection: DataStore.CollectionId, checkpointcollection: DataStore.CollectionId,
  codec: PayloadCodec[A], owner: DataStoreComponentOwner, receiptlimits: WorkflowMechanicalReceiptLimits
)(using ExecutionContext) {
  import WorkflowInstanceDataStorePersistence.{configurationField, recordField,
    revisionField, storageText, storageJsonC}
  import WorkflowMechanicalTransitionDataStorePersistence.payloadField
  private val _receipt_limits = receiptlimits

  def prepareC(intent: Intent[A]): Consequence[WorkflowPreparedDataStoreTransition[Receipt[A]]] = {
    for {
      _ <- if (intent != null && intent.current != null) Consequence.unit
        else Consequence.stateConflict("Mechanical intent target is missing")
      // Re-read the actual native instance; the caller's data is not a guard witness.
      loaded <- instances.load(configuration, intent.current.identity)
      current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Mechanical instance is absent"))
      receipt <- _admit_loaded_c_unchecked(component, operation, intent.copy(configuration = configuration, current = current))
      receiptbytes <- WorkflowMechanicalTransitionJsonV1._encode_c_unchecked(receipt, codec, _receipt_limits)
      nextbytes = WorkflowInstanceRecordJsonV1._encode_unchecked(receipt.next)
      key <- keyC(receipt.reference)
      expected <- EntityRevision.createC(current.revision.value + 1L)
      nextrevision <- expected.nextC
      root <- DataStoreConditionalRoot.create(owner, instancecollection, DataStore.StringEntryId(current.identity.value),
        revisionField, Some(expected), Vector.empty,
        Record.dataAuto(recordField -> storageText(nextbytes)), nextrevision)
      successor = Record.dataAuto(revisionField -> 1L,
        payloadField -> storageText(receiptbytes))
      pair <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Create(owner,
        checkpointcollection, DataStore.StringEntryId(key), revisionField, successor),
        correlation = DataStoreConditionalCorrelation("workflow-mechanical-transition"))
    } yield WorkflowPreparedDataStoreTransition.create(store, pair, changed => changed match {
      case DataStoreConditionalTransitionResult.Transitioned(rootvalue, successorvalue) => for {
        actual <- instances.decodeC(rootvalue, receipt.next.identity)
        retained <- _decode_logical_c(successorvalue, receipt.reference)
      } yield retained.copy(next = actual)
      case _ => Consequence.stateConflict("Mechanical revision or checkpoint Create was refused")
    })
  }

  def loadRetainedC(reference: Reference): Consequence[Option[(Receipt[A], InstanceRecord)]] = {
    for {
      key <- keyC(reference)
      row <- store.load(checkpointcollection, DataStore.StringEntryId(key))
      retained <- row match {
        case None => Consequence.success(None)
        case Some(value) => for {
          receipt <- _decode_stored_c(value, reference)
          loaded <- instances.load(configuration, receipt.next.identity)
          current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Retained mechanical instance is absent"))
        } yield Some(receipt -> current)
      }
    } yield retained
  }

  private def _decode_stored_c(stored: Record, reference: Reference): Consequence[Receipt[A]] = {
     for {
    key <- keyC(reference)
    _ <- if (stored.getString("id").contains(key) &&
        Vector("id", revisionField, payloadField).forall(field => stored.fields.count(_.key == field) == 1)) Consequence.unit
      else Consequence.stateConflict("Mechanical checkpoint physical identity or envelope differs")
    receipt <- _decode_logical_c(Record(stored.fields.filterNot(_.key == "id")), reference)
  } yield receipt

  }
  /** Verified native pairs already carry/check the physical target separately;
    * ordinary loads must consume the physical target first.
    */
  private def _decode_logical_c(stored: Record, reference: Reference): Consequence[Receipt[A]] = {
     for {
    _ <- if (Vector(revisionField, payloadField).forall(field => stored.fields.count(_.key == field) == 1))
      Consequence.unit else Consequence.stateConflict("Mechanical checkpoint consumed envelope fields are ambiguous")
    revision <- WorkflowContinuationDataStorePersistence.revisionBinding.revision(stored)
    // Legacy configurationPayload is inert; current host binding owns policy.
    _ <- if (revision.value == 1L) Consequence.unit
      else Consequence.stateConflict("Mechanical checkpoint native envelope differs")
    payload <- stored.getString(payloadField).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Mechanical checkpoint payload is absent"))
    raw <- storageJsonC(payload)
    receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(raw, catalog, codec, _receipt_limits)
    _ <- if (receipt.reference == reference && receipt.component == component && receipt.operation == operation)
      Consequence.unit else Consequence.stateConflict("Mechanical checkpoint owner, operation or reference differs")
  } yield receipt
  }
}

private[workflow] object WorkflowMechanicalTransitionDataStorePersistence {
  val payloadField = "mechanicalPayload"
  def bindC[A](store: DataStore, configuration: Configuration, component: ComponentId,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId, codec: PayloadCodec[A],
    owner: DataStoreComponentOwner)(using ExecutionContext): Consequence[WorkflowMechanicalTransitionDataStorePersistence[A]] = {
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
      limits <- configuration.mechanicalReceiptLimitsC
    } yield new WorkflowMechanicalTransitionDataStorePersistence(compound, configuration, component, operation,
      adapter, catalog, instances, checkpoints, codec, owner, limits)
  }
}
