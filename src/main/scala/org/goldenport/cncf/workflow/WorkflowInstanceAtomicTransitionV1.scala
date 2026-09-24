package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.event.EventEngine

/*
 * @since   Sep. 24, 2026
 * @version Sep. 24, 2026
 */

/**
 * Opt-in single-commit owner for WorkflowInstance history, Continuation state,
 * and the active UnitOfWork's event/data effects. An implementation must stage
 * without publishing claimable work and must make its inherited prepare,
 * commit, and abort cover all of those effects in one proven transaction
 * domain. EventEngine.noop does not implement this capability.
 *
 * This interface is a requirement, not evidence that an implementation is
 * atomic. A provider must pass shared-store and failure/recovery conformance
 * before it may be used as a production engine.
 */
trait AtomicWorkflowSuspensionEngine extends EventEngine {
  def stageSuspensionC(
    admitted: WorkflowInstanceAtomicTransitionV1.AdmittedSuspension
  ): Consequence[Unit]
}

/**
 * Additive input contract for an atomic WorkflowInstance/Continuation transition.
 * Admission is pure: it neither writes a store nor enlists a UnitOfWork. A
 * transaction-domain implementation must recheck the expected revision and
 * continuation uniqueness inside its single commit boundary.
 */
object WorkflowInstanceAtomicTransitionV1 {
  val schemaVersion: String = "cncf.workflow-instance-atomic-transition.v1"

  final case class SuspensionIntent(
    schemaVersion: String,
    configuration: WorkflowInstancePersistence.Configuration,
    current: WorkflowInstancePersistence.InstanceRecord,
    expectedRevision: WorkflowInstancePersistence.InstanceRevision,
    entry: WorkflowInstancePersistence.HistoryEntry,
    continuation: ContinuationRuntimePersistence.Record
  )

  final case class AdmittedSuspension private[workflow] (
    configuration: WorkflowInstancePersistence.Configuration,
    current: WorkflowInstancePersistence.InstanceRecord,
    next: WorkflowInstancePersistence.InstanceRecord,
    continuation: ContinuationRuntimePersistence.Record
  )

  def admitSuspensionC(intent: SuspensionIntent): Consequence[AdmittedSuspension] =
    if (intent == null)
      Consequence.stateConflict("Atomic workflow suspension intent is missing")
    else if (intent.schemaVersion != schemaVersion)
      Consequence.stateConflict("Atomic workflow suspension schema is unsupported")
    else if (intent.configuration == null || intent.current == null ||
        intent.expectedRevision == null || intent.entry == null ||
        intent.continuation == null)
      Consequence.stateConflict("Atomic workflow suspension intent is incomplete")
    else
      intent.configuration.validateC.flatMap { configuration =>
        intent.current.appendC(intent.expectedRevision, intent.entry).flatMap { next =>
          _admit_continuation_c(next, intent.continuation).map { continuation =>
            AdmittedSuspension(configuration, intent.current, next, continuation)
          }
        }
      }

  private def _admit_continuation_c(
    next: WorkflowInstancePersistence.InstanceRecord,
    record: ContinuationRuntimePersistence.Record
  ): Consequence[ContinuationRuntimePersistence.Record] = {
    val continuation = record.continuation
    val boundary = next.suspension.flatMap(Option(_))
    if (continuation == null || continuation.continuationId == null ||
        continuation.continuationId.value == null ||
        continuation.continuationId.value.trim.isEmpty ||
        continuation.runId == null || continuation.runId.value == null ||
        continuation.runId.value.trim.isEmpty ||
        continuation.expectedRevision == null ||
        continuation.expectedRevision.value == null ||
        continuation.expectedRevision.value.trim.isEmpty ||
        continuation.requiredOperation == null ||
        continuation.requiredOperation.identity == null ||
        continuation.context == null || continuation.context.snapshot == null ||
        record.claimId == null)
      Consequence.stateConflict("Atomic workflow suspension continuation is incomplete")
    else if (boundary.isEmpty)
      Consequence.stateConflict("Atomic workflow transition has no suspension boundary")
    else if (boundary.exists(_.continuationIdentity.value != continuation.continuationId.value))
      Consequence.stateConflict("Atomic workflow suspension continuation identity differs from history")
    else if (record.status != ContinuationRuntimePersistence.Status.Available ||
        record.claimId.nonEmpty)
      Consequence.stateConflict("Atomic workflow suspension continuation is already claimed or completed")
    else
      Consequence.success(record)
  }
}
