# Phase 35 - UnitOfWork Resource Lifecycle Checklist

This checklist tracks Phase 35 implementation and evidence. The summary
dashboard is `phase-35.md`.

## UL-01: Generic Lifecycle Contract

Status: DONE (Jul. 17, 2026)

- [x] Separate operational resource ownership from transaction participants.
- [x] Define termination reasons, registration, unregister, and late-resource
  behavior.
- [x] Define Process Execution cancel-and-reap ownership.

Evidence: `docs/design/unit-of-work-resource-lifecycle.md` is normative and
`docs/design/process-execution-runtime.md` records the Process Execution
integration.

## UL-02: UnitOfWork Registry

Status: DONE (Jul. 17, 2026)

- [x] Add thread-safe resource registration and idempotent terminal drain.
- [x] Drain resources in LIFO order on commit, abort, rollback, and dispose.
- [x] Continue draining after a resource release failure.
- [x] Release late registrations immediately with the recorded termination.

Evidence: `UnitOfWorkResourceLifecycleSpec` proves commit ordering, abort
failure tolerance, primary-plus-cleanup `Conclusion` composition, unregister,
late registration, and idempotent dispose.

## UL-03: Process Execution Reclamation

Status: DONE (Jul. 17, 2026)

- [x] Register each active process handle and WorkArea as a UnitOfWork resource.
- [x] Cancel and await active process execution when the UnitOfWork aborts.
- [x] Keep normal terminal completion from being cancelled again.
- [x] Make WorkArea close idempotent across completion/termination races.

Evidence: `ProcessExecutionJobCancellationSpec` proves UnitOfWork abort reaches
an active process handle and that an initial `awaitC` failure retains ownership
until later UnitOfWork cleanup; `ProcessExecutionWorkAreaSpec` remains the
confined WorkArea cleanup coverage.

## UL-04: Verification and Closure

Status: DONE (Jul. 17, 2026)

- [x] Run focused UnitOfWork/Process Execution lifecycle specifications.
- [x] Run full CNCF regression.
- [x] Complete scoped review and update closure evidence for the release
  checkpoint.

Evidence: the focused lifecycle/security run passed 24 tests, and the full
CNCF suite passed 1917 tests with no failures. One repository-environment test
was cancelled because its optional standard CAR fixture was absent; existing
pending specifications remain unchanged.
