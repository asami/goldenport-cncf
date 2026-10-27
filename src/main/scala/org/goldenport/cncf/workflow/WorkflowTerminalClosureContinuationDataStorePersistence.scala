package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.{Reference, Receipt, keyC}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.cncf.workflow.WorkflowTerminalClosureContinuation.{Intent, Target}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/**
 * Private borrowed adapter: prepares a pair, never owns or commits a store.
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
private[workflow] final class WorkflowTerminalClosureContinuationDataStorePersistence[A] private (
  store: DataStore & EntityConditionalTransitionDataStore,
  configuration: Configuration, component: ComponentId, operation: StateMachineOperationIdentity,
  instances: WorkflowInstanceDataStorePersistence, catalog: WorkflowInstanceRecordJsonV1.Catalog,
  instancecollection: DataStore.CollectionId, checkpointcollection: DataStore.CollectionId,
  codec: PayloadCodec[A], owner: DataStoreComponentOwner, receiptlimits: WorkflowMechanicalReceiptLimits
)(using ExecutionContext) {
  import WorkflowInstanceDataStorePersistence.{recordField,
    revisionField, storageText, storageJsonC}
  import WorkflowTerminalClosureContinuationDataStorePersistence.payloadField
  private val _receipt_limits = receiptlimits

  def inspectC(instance: InstanceIdentity): Consequence[Target] = for {
    loaded <- instances.load(configuration, instance)
    current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Terminal closure instance is absent"))
    target <- WorkflowTerminalClosureContinuation.targetC(component, current)
  } yield target

  private def _key_c(reference: Reference): Consequence[String] =
    keyC(reference).flatMap { key =>
      val selected = "terminal-closure:" + key
      if (selected.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1024) Consequence.success(selected)
      else Consequence.stateConflict("Terminal closure checkpoint key exceeds its bound")
    }

  def prepareC(intent: Intent[A]): Consequence[WorkflowPreparedDataStoreTransition[Receipt[A]]] = {
    for {
      _ <- if (intent != null && intent.target != null) Consequence.unit
        else Consequence.stateConflict("Terminal closure intent target is missing")
      // Re-read the actual native instance; the caller's data is not a guard witness.
      loaded <- instances.load(configuration, intent.target.instance)
      current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Terminal closure instance is absent"))
      receipt <- WorkflowTerminalClosureContinuation.admitC(component, operation, current, intent)
      receiptbytes <- WorkflowTerminalClosureContinuationJsonV1.encodeC(receipt, codec, _receipt_limits)
      nextbytes = WorkflowInstanceRecordJsonV1._encode_unchecked(receipt.next)
      key <- _key_c(receipt.reference)
      expected <- EntityRevision.createC(current.revision.value + 1L)
      nextrevision <- expected.nextC
      root <- DataStoreConditionalRoot.create(owner, instancecollection, DataStore.StringEntryId(current.identity.value),
        revisionField, Some(expected), Vector.empty,
        Record.dataAuto(recordField -> storageText(nextbytes)), nextrevision)
      successor = Record.dataAuto(revisionField -> 1L,
        payloadField -> storageText(receiptbytes))
      pair <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Create(owner,
        checkpointcollection, DataStore.StringEntryId(key), revisionField, successor),
        correlation = DataStoreConditionalCorrelation("workflow-terminal-closure-transition"))
    } yield WorkflowPreparedDataStoreTransition.create(store, pair, changed => changed match {
      case DataStoreConditionalTransitionResult.Transitioned(rootvalue, successorvalue) => for {
        actual <- instances.decodeC(rootvalue, receipt.next.identity)
        retained <- _decode_logical_c(successorvalue, receipt.reference)
        _ <- WorkflowTerminalClosureContinuation.collectC(retained, actual)
      } yield retained
      case _ => Consequence.stateConflict("Terminal closure revision or checkpoint Create was refused")
    })
  }

  def loadRetainedC(reference: Reference, instance: InstanceIdentity): Consequence[Option[(Receipt[A], InstanceRecord)]] = {
    for {
      key <- _key_c(reference)
      row <- store.load(checkpointcollection, DataStore.StringEntryId(key))
      retained <- row match {
        case None => Consequence.success(None)
        case Some(value) => for {
          receipt <- _decode_stored_c(value, reference)
          _ <- if (receipt.current.identity == instance) Consequence.unit
            else Consequence.stateConflict("Terminal closure checkpoint targets a different native instance")
          loaded <- instances.load(configuration, receipt.next.identity)
          current <- loaded.map(Consequence.success).getOrElse(Consequence.stateConflict("Retained terminal-closure instance is absent"))
          _ <- WorkflowTerminalClosureContinuation.collectC(receipt, current)
        } yield Some(receipt -> current)
      }
    } yield retained
  }

  private def _decode_stored_c(stored: Record, reference: Reference): Consequence[Receipt[A]] = {
     for {
    key <- _key_c(reference)
    _ <- if (stored.getString("id").contains(key) &&
        Vector("id", revisionField, payloadField).forall(field => stored.fields.count(_.key == field) == 1)) Consequence.unit
      else Consequence.stateConflict("Terminal closure checkpoint physical identity or envelope differs")
    receipt <- _decode_logical_c(Record(stored.fields.filterNot(_.key == "id")), reference)
  } yield receipt

  }
  /** Verified native pairs already carry/check the physical target separately;
    * ordinary loads must consume the physical target first.
    */
  private def _decode_logical_c(stored: Record, reference: Reference): Consequence[Receipt[A]] = {
     for {
    _ <- if (Vector(revisionField, payloadField).forall(field => stored.fields.count(_.key == field) == 1))
      Consequence.unit else Consequence.stateConflict("Terminal closure checkpoint consumed envelope fields are ambiguous")
    revision <- WorkflowContinuationDataStorePersistence.revisionBinding.revision(stored)
    // Legacy configurationPayload is inert; current host binding owns policy.
    _ <- if (revision.value == 1L) Consequence.unit
      else Consequence.stateConflict("Terminal closure checkpoint native envelope differs")
    payload <- stored.getString(payloadField).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Terminal closure checkpoint payload is absent"))
    raw <- storageJsonC(payload)
    receipt <- WorkflowTerminalClosureContinuationJsonV1.decodeC(raw, catalog, codec, _receipt_limits)
    _ <- if (receipt.reference == reference && receipt.component == component && receipt.operation == operation)
      Consequence.unit else Consequence.stateConflict("Terminal closure checkpoint owner, operation or reference differs")
  } yield receipt
  }
}

private[workflow] object WorkflowTerminalClosureContinuationDataStorePersistence {
  val payloadField = "terminalClosurePayload"
  def bindC[A](store: DataStore, configuration: Configuration, component: ComponentId,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId, codec: PayloadCodec[A],
    owner: DataStoreComponentOwner)(using ExecutionContext): Consequence[WorkflowTerminalClosureContinuationDataStorePersistence[A]] = {
    for {
      _ <- (instances, checkpoints) match {
        case (a: DataStore.CollectionId.EntityStore, b: DataStore.CollectionId.EntityStore)
            if a.collectionName != b.collectionName => Consequence.unit
        case _ => Consequence.configurationInvalid("Terminal closure pair requires distinct explicit EntityStore collections")
      }
      compound <- store match {
        case value: EntityConditionalTransitionDataStore if store.isAccept(instances) && store.isAccept(checkpoints) =>
          Consequence.success(value.asInstanceOf[DataStore & EntityConditionalTransitionDataStore])
        case _ => Consequence.configurationInvalid("Terminal closure pair requires the same canonical compound provider")
      }
      catalog <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      adapter <- WorkflowInstanceDataStorePersistence.bindC(store, configuration, definitions, instances)
      limits <- configuration.mechanicalReceiptLimitsC
    } yield new WorkflowTerminalClosureContinuationDataStorePersistence(compound, configuration, component, operation,
      adapter, catalog, instances, checkpoints, codec, owner, limits)
  }
}
