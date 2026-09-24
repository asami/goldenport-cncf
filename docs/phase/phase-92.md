# Phase 92: Shared Transaction Domain for Workflow Suspension

status=planned
execution_priority=deferred_until_sm_workflow_vertical_slice
planned_at=2026-09-24
runtime_baseline=[Phase 77.2](phase-77.2.md)
checklist=[Phase 92 Checklist](phase-92-checklist.md)

## Purpose

Make WorkflowInstance progression, durable Continuation creation, and the
active UnitOfWork's event/data effects one proven persistent transaction.
This is the shared-transaction work separated from Phase 77.1 so that the
first `sm-workflow` vertical slice can use the explicitly non-atomic,
post-commit Continuation baseline without waiting for a new datastore
architecture.

Phase 92 is independent of Phases 89, 90 (Candidate-Admission), and 91
(Execution/Failure Model). Its number does not make those Phases prerequisites.
It does not block Phase 77.1, Phase 77.2, or the first `sm-workflow`
vertical slice.

## Entry condition

Begin only after the first `sm-workflow` vertical slice has a stable result
and its observed persistence/recovery needs have been recorded. Re-estimate
and split this Phase if the shared-store implementation exceeds one bounded
execution Phase. Do not use the Phase 77.1 loose baseline as proof of
atomicity.

## Scope

| ID | Outcome | Status |
| --- | --- | --- |
| WTX-92-01 | Freeze a versioned transaction-domain capability covering WorkflowInstance, Continuation, EventStore, DataStore, and the active UnitOfWork. | planned |
| WTX-92-02 | Implement one durable backend and opt-in UnitOfWork enlistment; reject default or mixed-store configurations before claimable work is published. | planned |
| WTX-92-03 | Prove one shared commit, prepare rejection, abort, commit failure/indeterminacy, restart recovery, and post-commit claim visibility. | planned |
| WTX-92-04 | Migrate the `sm-workflow` consumer only after compatibility and recovery evidence; preserve its existing loose boundary until then. | planned |

## Preserved baseline and exclusions

- Preserve Phase 77's closed `WorkflowInstancePersistence.create/load/append`
  SPI and Cozy's accepted producer ABI. Add a versioned CNCF-owned extension;
  do not retroactively change the Phase 77 contract.
- Phase 77.1 may publish a Continuation only after its UnitOfWork commit and
  successful continuation persistence. It must report a failed or
  indeterminate post-commit persistence step as a committed-but-incomplete
  outcome, not as an atomic rollback or successful publication. The caller
  must not hand out work from that failed result; persisted state may require
  reconciliation.
- The `AtomicWorkflowSuspensionEngine` and pure suspension-intent admission
  prototypes are not evidence of a working shared transaction. They are
  Phase 92 candidates only, outside Phase 77.1 and 77.2 closure.
- Do not simulate atomicity by committing separate participants in order, by
  a post-commit callback, or by an in-memory-only fixture.
- No Cozy source or producer-ABI change is in scope.

## References

- [Phase 77.1](phase-77.1.md), [Phase 77.2](phase-77.2.md), and [Phase 92 Checklist](phase-92-checklist.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md#966-shared-workflow-transaction-domain)
