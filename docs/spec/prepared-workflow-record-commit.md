# Prepared Workflow record commit

Status: bounded Phase 88 producer contract

prepareSuspensionC/prepareCompletionC admit the same exact Workflow facts as the
compatible immediate APIs but publish no record, issued work, completion or
typed result. They return a borrowed, non-durable prepared canonical plan.

withSideEffectsC appends explicit normalized canonical side-record values only.
Existing root/successor owner, guards, revision, correlation and immutable issued
bytes must not change. Canonical overlap, duplicate, shape and size constraints
remain enforced; every collection must be accepted by the same borrowed provider.
No SQL, callbacks, domain objects, external effects or second-store operations
are added to the provider plan.

commitC invokes conditionalTransition once and reuses the original authoritative
Workflow readback admission. Only a successful commit exposes the typed outcome.
Guard movement rejects without any side effect. Pre-commit failures roll back
the guarded records and all appended effects. A throwing provider whose outcome
is unknown, or an unadmittable acknowledged readback, is transaction-indeterminate;
the host must query authority rather than automatically retry.

Old suspendC/completeC prepare and commit with no added effects. Preparation owns
no resource lease or database transaction and cannot outlive its host's borrowed
store lease. It is not serializable or restart authority and grants no claim,
admission or command authority.

Executable specifications: WorkflowPreparedDataStoreTransitionSpec and the
prepared groups in WorkflowIssuedWorkDataStorePersistenceSpec and
WorkflowCompletionDataStorePersistenceSpec.

This contract is a local record-plan seam, not active UoW event/data enlistment,
an AtomicWorkflowSuspensionEngine or production Operation/launcher integration.
