# Phase 35 - UnitOfWork Resource Lifecycle

Stage Status:
- Current status: CLOSED
- Owner: Phase 35 UnitOfWork Resource Lifecycle
- Update rule: Update this block and `phase-35-checklist.md` whenever a stable
  work-item state changes. Close the phase only when every in-scope checklist
  item is complete or explicitly relocated.

status = closed

## 1. Purpose

Phase 35 implements strategy item `9.30 UnitOfWork Resource Lifecycle`. It
ensures that every non-transactional resource acquired through UnitOfWork is
released on commit, abort, rollback, exception, or explicit disposal.

Process Execution is the driver. Its active process handle and WorkArea must be
stopped and reaped when the owning UnitOfWork ends, independently of Job
cancellation.

## 2. Scope

- Define a generic UnitOfWork-owned operational resource contract and terminal
  reasons.
- Add thread-safe registration, unregister, LIFO drain, late-registration, and
  exactly-once terminal behavior.
- Drain resources from commit, abort, rollback, and dispose paths.
- Register Process Execution handle and WorkArea as one lifecycle resource.
- Preserve Job cancellation as immediate stop notification while UnitOfWork
  owns terminal reaping.
- Add structured cleanup diagnostics without `Status.detailCodes`.
- Update execution, Process Execution, and UnitOfWork lifecycle documents.

## 3. Boundaries

- Transaction participants remain in `CommitProtocol`; they are not generic
  UnitOfWork resources.
- The resource registry does not make external process side effects
  transactional or compensatable.
- No generic resource retry, retention, process scheduler, or legacy-shell
  migration is introduced.
- Existing concurrent RuntimeContext and ingress work remains outside this
  phase unless it is explicitly integrated later.

## 4. Active Work Stack

- A (DONE): UL-01 - Freeze generic resource lifecycle contract.
- B (DONE): UL-02 - Implement UnitOfWork registry and terminal draining.
- C (DONE): UL-03 - Integrate Process Execution handle/WorkArea reclamation.
- D (DONE): UL-04 - Validate lifecycle semantics, document outcomes,
  and close Phase 35.

## 5. Completion Conditions

Phase 35 closes only when:

- resources are released once in reverse registration order on every UnitOfWork
  terminal path;
- a release failure does not prevent remaining registered resources from being
  released;
- late registration is reclaimed immediately;
- normal resource completion can unregister without a second release;
- aborting an active Process Execution UnitOfWork cancels, awaits, and removes
  the process and WorkArea;
- Job cancellation and UnitOfWork termination races remain safe;
- focused and full CNCF regressions pass; and
- design/spec/strategy/phase documentation records the final contract.

## 6. Closure Record

Phase 35 closed on Jul. 17, 2026.

- Focused UnitOfWork, Process Execution, and ingress regression specifications
  passed.
- The full CNCF suite passed with 1917 successful tests, no failures, one
  environment-dependent cancellation, and the existing pending specifications.
- Scoped review confirmed that process ownership survives an initial await
  failure, cleanup continues after cancellation failure, and primary plus
  cleanup failures remain represented by `Conclusion` causal composition.
