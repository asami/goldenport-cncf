# Phase 64.1 - Local UnitOfWork Atomic Execution Foundation

status=superseded
planned_at=2026-09-05
revised_at=2026-09-20
parent=[Phase 64 - Minimal Composite StateMachine / Workflow Foundation](phase-64.md)

superseded_by=[Phase 63.1](phase-63.1.md) atomic StateMachine execution contract

> This planned Phase is retained as historical scope evidence. Phase 63.1 is
> closed and already owns the required local UnitOfWork atomic execution;
> Phase 64.2 consumes that completed contract directly. Phase 64.1 is not on
> the Phase 77 / sm-workflow Phase 1 critical path and must not be reopened.

## Purpose

Provide the minimum UnitOfWork execution semantics required by StateMachine / Composite StateMachine / Workflow and by Phase 64.2 testability, without pulling advanced distributed recovery into the sm-workflow critical path.

Phase 64.1 is intentionally limited to local atomic execution.

## Canonical path

```text
CML logical action
  -> ExecProgram[A] = Program[UnitOfWorkOp, A]
  -> local UnitOfWork interpreter
  -> state/local effects
  -> commit
     or
  -> complete rollback
```

## Scope

- Existing `UnitOfWorkOp` / `ExecProgram` inventory and reuse.
- Stable `UnitOfWorkId`.
- Atomic local execution of admitted local effects and StateMachine mutation.
- Complete rollback on failure before commit.
- No successful `CommittedTransition` for a rolled-back attempt.
- Structured success/failure outcome suitable for Phase 64.2 deterministic test interpretation.
- Correlation/provenance needed to connect UnitOfWork, transition and action occurrence.
- Clear classification boundary between local atomic effects and effects that are outside this Phase.

## Invariants

- A failed local UnitOfWork leaves no partial local commit.
- Committed StateMachine history is never rewritten by rollback/retry machinery.
- `UnitOfWorkOp` remains the canonical executable-intent algebra.
- StateMachine/Workflow does not introduce a parallel Action execution algebra.
- External/non-transactional effects are not falsely described as part of the local atomic boundary.
- Unsupported advanced transaction/recovery requirements fail or remain explicitly deferred; they are not silently approximated.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| ATX-01 | Inventory existing UnitOfWork / UnitOfWorkOp / interpreter boundaries. | planned |
| ATX-02 | Freeze stable UnitOfWorkId and transition/action correlation. | planned |
| ATX-03 | Implement/prove complete local atomic commit-or-rollback semantics. | planned |
| ATX-04 | Produce structured execution outcome consumable by Phase 64.2 test/runtime paths. | planned |
| ATX-05 | Prove StateMachine local transition success and injected-failure rollback without production external infrastructure. | planned |

## Acceptance

- Any failed admitted local UnitOfWork is completely rolled back.
- No successful transition occurrence is published for a rolled-back attempt.
- Every committed StateMachine UnitOfWork has a stable UnitOfWorkId and causal correlation.
- StateMachine/Workflow actions continue to use `ExecProgram[UnitOfWorkOp, A]`.
- Phase 64.2 can test success/failure/rollback without depending on 2PC or compensation runtime.

## Deferred to advanced follow-up

The following are explicitly not Phase 64.1 completion requirements:

- distributed atomic / 2PC execution;
- XA/JTA/provider coordination;
- after-commit compensation-handler SPI;
- compensation planning/execution;
- durable `RecoveryRequired` escalation;
- irreversible-effect recovery policy;
- administrative/manual recovery integration;
- multi-step Saga-style recovery.

These are owned by the dedicated advanced transaction/recovery Phase.

## Non-goals

- Making external systems transactional.
- Business compensation.
- Distributed transaction coordination.
- Recovery workflow ownership.
