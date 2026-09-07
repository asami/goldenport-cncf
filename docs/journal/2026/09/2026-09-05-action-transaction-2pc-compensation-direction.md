# Action Transaction, 2PC, and Compensation Direction

Date: 2026-09-05
Status: runtime design decision record

## Context

The Composite StateMachine design now uses typed, inspectable Action Programs
that can combine constituent and composite transition actions before runtime
execution.

The next design issue is atomicity when those actions include external I/O.

For purely local effects, the desired rule is simple: action interpretation and
state mutation occur inside one UnitOfWork, and any pre-commit error aborts the
whole StateMachine transition.

For external resources, the guarantee depends on what transaction protocol the
runtime can actually provide.

## Decision

CNCF will explicitly separate logical Action Program composition from execution
transaction planning.

The planner must distinguish:

- local atomic execution;
- distributed atomic execution when 2PC-capable participants are available;
- after-commit execution with explicit compensation when atomic distributed
  commit is unavailable but a business inverse exists; and
- irreversible external effects where no meaningful inverse exists.

## Atomic transition rule

If all required actions participate in one admitted atomic boundary, any
pre-commit error aborts the entire transition. No state change and no
`CommittedTransition` become authoritative.

## 2PC

2PC is optional runtime capability. It is not assumed by CML and is not silently
emulated.

When a generated model requires distributed atomicity, CNCF must prove that all
participants can join the selected protocol. If it cannot, admission fails.

## Compensation

Compensation is distinct from rollback.

A forward external effect may have an explicit compensation action. If later
failure requires recovery, CNCF executes the compensation as a new action
occurrence with its own idempotency, retry, failure, history, and observability.

The historical forward effect remains visible.

## Irreversible effects

Some effects cannot be undone. CNCF must expose that boundary honestly and
support retry/corrective/manual recovery rather than inventing a fake rollback.

## Model/runtime boundary

Cozy/CML owns logical action transaction/reversibility semantics and the
explicit relation between forward and compensation actions.

CNCF owns:

- provider/resource capability admission;
- UnitOfWork and transaction boundaries;
- optional 2PC coordination;
- Action Program segmentation/planning;
- compensation execution;
- irreversible-effect recovery state;
- idempotency and crash recovery;
- observability.

## Phase split

The work is tracked as:

- Cozy Phase 33.1: CML Action Transaction and Compensation Semantics
- CNCF Phase 64.1: Action Transaction, 2PC, and Compensation Runtime

This split preserves the broader rule already established for StateMachine and
Workflow integration:

> CML defines model meaning; CNCF provides reliable execution semantics.
