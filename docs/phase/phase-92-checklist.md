# Phase 92 Checklist: Shared Workflow Transaction Domain

status=planned
phase=[Phase 92](phase-92.md)

This future ledger is not on the first `sm-workflow` vertical-slice critical
path. The Phase 77.1 post-commit baseline remains explicitly non-atomic.

## WTX-92-01: Transaction-domain contract

Stage Status:
- Current status: OPEN
- Owner: CNCF UnitOfWork / Workflow persistence owners
- Update rule: Close only when one versioned capability binds all participating stores and the active UnitOfWork, with unsupported or mixed domains rejected before publication. Checklist below is the closure basis.

- [ ] Inventory the actual EventStore, DataStore, WorkflowInstance, and Continuation persistence backends used by the stable `sm-workflow` slice.
- [ ] Define one transaction handle and identity whose commit/abort scope includes all four stores and the active UnitOfWork effects.
- [ ] Preserve Phase 77's closed three-method persistence SPI and add only a versioned extension.
- [ ] Reject missing, mixed, or unverifiable transaction-domain capability before staging claimable work.

## WTX-92-02: Durable implementation

Stage Status:
- Current status: OPEN
- Owner: CNCF datastore / EventEngine owners
- Update rule: Close only when one durable backend owns the whole commit and no independent post-commit persistence step is presented as atomic. Checklist below is the closure basis.

- [ ] Implement one durable shared transaction for WorkflowInstance append, Continuation creation, EventStore append, and DataStore effects.
- [ ] Enlist through the active UnitOfWork without a second independent `CommitParticipant.commit`.
- [ ] Keep a successful commit's Continuation externally invisible until all required state is durably committed.
- [ ] Preserve the loose baseline as an explicit compatibility boundary; do not silently reinterpret its outcome as atomic.

## WTX-92-03: Failure and recovery proof

Stage Status:
- Current status: OPEN
- Owner: CNCF Workflow runtime owner
- Update rule: Close only when executable specifications establish the complete failure/recovery contract. Checklist below is the closure basis.

- [ ] Prove one successful shared commit and post-commit claim visibility.
- [ ] Prove prepare rejection and pre-commit abort publish no partial WorkflowInstance or Continuation state.
- [ ] Prove commit failure and indeterminate outcome are distinguishable and recoverable after restart.
- [ ] Prove stale revision, duplicate continuation, and mixed-store attempts fail closed.
- [ ] Validate against a durable backend, not only an in-memory callback sequence.

## WTX-92-04: Consumer migration and closure

Stage Status:
- Current status: OPEN
- Owner: CNCF / `sm-workflow` integration owners
- Update rule: Close only when the consumer migration is explicit, compatible, reviewed, and validated. Checklist below is the closure basis.

- [ ] Record the stable `sm-workflow` baseline and its exact migration need.
- [ ] Exercise the real consumer's suspension/resume path under the shared backend.
- [ ] Complete focused and repository-wide validation, independent review, and release evidence under the Phase 92 execution plan.
- [ ] Confirm Phase 77.1, Phase 77.2, Cozy, and the first `sm-workflow` vertical slice were not retroactively made dependent on Phase 92.
