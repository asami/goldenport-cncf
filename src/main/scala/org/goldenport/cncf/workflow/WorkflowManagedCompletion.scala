package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore, DataStoreComponentOwner}
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Opaque host binding. Public result requests cannot select a storage route,
  * supply a claim token or borrow a DataStoreSpace through this value.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowManagedCompletion[R] private (
  private[workflow] val resultTypeIdentity: String,
  query: (ContinuationIdentity, ExecutionContext) => Consequence[Option[WorkflowManagedCompletion.Retained[R]]],
  run: (ComponentId, ContinuationResult[R], InstanceRecord,
    WorkflowCompletionServiceOperation.SelectedClosing, ExecutionContext) => Consequence[WorkflowManagedCompletion.Completed]
) {
  /** Read committed facts only. No claim, semantic replay or state transition.
    * The returned witness cannot be constructed from application-supplied JSON.
    */
  def loadRetainedC(identity: ContinuationIdentity)(using context: ExecutionContext):
      Consequence[Option[WorkflowManagedCompletion.Retained[R]]] =
    query(identity, context)

  private[workflow] def completeC(owner: ComponentId, submitted: ContinuationResult[R],
      current: InstanceRecord, selected: WorkflowCompletionServiceOperation.SelectedClosing,
      context: ExecutionContext): Consequence[WorkflowManagedCompletion.Completed] =
    run(owner, submitted, current, selected, context)
}

object WorkflowManagedCompletion {
  final case class Completed(resume: ContinuationRuntime.Resume, instance: InstanceRecord)

  final class Retained[R] private[WorkflowManagedCompletion] (
    val completion: WorkflowCompletionRecordJsonV1.Record[R]
  )

  /** Configuration-time binding only. Actual provider/lease admission occurs
    * per call under the Subsystem's captured declarations and event policy.
    */
  def bindC[W, R](subsystem: Subsystem, component: ComponentId,
      configuration: Configuration, operation: StateMachineOperationIdentity,
      definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
      continuations: DataStore.CollectionId, inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R],
      closingType: StateMachineResultTypeReference,
      events: (ContinuationResult[R], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]]
  ): Consequence[WorkflowManagedCompletion[R]] =
    bindForKindC(subsystem, component, configuration, operation, definitions, instances,
      continuations, inputCodec, resultCodec, closingType, events, WorkflowIssuedContinuationKind.WorkOrder)

  private[workflow] def bindForKindC[W, R](subsystem: Subsystem, component: ComponentId,
      configuration: Configuration, operation: StateMachineOperationIdentity,
      definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
      continuations: DataStore.CollectionId, inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R],
      closingType: StateMachineResultTypeReference,
      events: (ContinuationResult[R], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]],
      kind: WorkflowIssuedContinuationKind
  ): Consequence[WorkflowManagedCompletion[R]] =
    if (subsystem == null || component == null || configuration == null || operation == null ||
        definitions == null || definitions.isEmpty || definitions.exists(_ == null) || instances == null ||
        continuations == null || inputCodec == null || resultCodec == null || events == null ||
        resultCodec.typeIdentity == null || resultCodec.typeIdentity.trim.isEmpty ||
        closingType == null || closingType.value == null || closingType.value.trim.isEmpty || kind == null)
      Consequence.configurationInvalid("Managed completion host binding is incomplete")
    else for {
      accepted <- configuration.validateC
      _ <- accepted.delivery match {
        case _: WorkflowInstancePersistence.DeliveryConfiguration.SameStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Managed completion requires same-store delivery")
      }
      _ <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, continuations)
      bindingOwner <- DataStoreComponentOwner.create(component.name)
    } yield {
      new WorkflowManagedCompletion(resultCodec.typeIdentity, (identity, context) =>
        if (identity == null || identity.value == null || identity.value.trim.isEmpty || context == null)
          Consequence.stateConflict("Managed completion query requires an identity and execution context")
        else {
          given ExecutionContext = context
          val environment = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
          for {
            _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, continuations)
            result <- subsystem.withWorkflowVerifiedMultiGuardSpaceC(component, environment) { space =>
              for {
                store <- space.dataStore(instances)
                adapter <- WorkflowCompletionDataStorePersistence.bindForKindC(store, accepted, component, operation,
                  definitions, instances, continuations, inputCodec, resultCodec, kind)
                retained <- adapter.loadC(identity)
                verified <- retained match {
                  case None => Consequence.success(None)
                  case Some(record) if record.closingResult.exists(_.typeReference == closingType) =>
                    Consequence.success(Some(new Retained(record)))
                  case _ => Consequence.stateConflict("Retained completion lacks its bound typed closing result")
                }
              } yield verified
            }
          } yield result
        }, (owner, submitted, current, selected, context) =>
        if (owner != component || context == null || selected == null || selected.nextInstance == null ||
            selected.nextInstance.isEmpty || selected.nextInstance.exists(value =>
              value == null || value.history == null || value.history.isEmpty))
          Consequence.stateConflict("Managed completion requires its bound Component and selected next instance")
        else {
          given ExecutionContext = context
          val environment = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
          subsystem.withWorkflowVerifiedMultiGuardSpaceC(component, environment) { space =>
            for {
              store <- space.dataStore(instances)
              adapter <- WorkflowCompletionDataStorePersistence.bindForKindC(store, accepted, component, operation,
                definitions, instances, continuations, inputCodec, resultCodec, kind)
              batch <- events(submitted, selected)
              retained <- adapter.executeSelectedClosingHostC(current, selected.nextInstance.get.history.last,
                submitted, selected.program, closingType, bindingOwner,
                space, batch, records => records.foldLeft(Consequence.unit)((acc, record) =>
                  acc.flatMap(_ => subsystem.eventBus.dispatchCommittedRecordC(record).map(_ => ()))))
              actual <- WorkflowInstanceRecordJsonV1.encodeC(retained.instance)
              expected <- WorkflowInstanceRecordJsonV1.encodeC(selected.nextInstance.get)
              _ <- if (actual == expected && retained.retained.closingResult.nonEmpty) Consequence.unit
                else org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
                  "Managed completion retained result differs from selected next instance")
            } yield Completed(ContinuationRuntime.Resume(retained.continuation.continuation,
              retained.retained.closingResult.get), retained.instance)
          }
        })
    }
}
