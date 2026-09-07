# StateMachine Actions on Existing UnitOfWork Algebra

Date: 2026-09-05
Status: Phase 64.2 design decision

## Context

The StateMachine/Composite StateMachine/Workflow design introduced typed,
composable action programs and test interpreters. An intermediate proposal
considered a new StateMachine/Workflow-specific Action algebra.

Inspection of the existing CNCF execution model showed that this would duplicate
an established contract. CNCF already defines:

```text
UnitOfWorkOp[A]
ExecProgram[A] = Program[UnitOfWorkOp, A]
ExecUowM[A]    = UowM[UnitOfWorkOp, A]
```

`UnitOfWorkOp` is documented and implemented as the canonical executable-intent
algebra for both Free/UoW and direct execution DSLs.

## Decision

StateMachine, Composite StateMachine, and Workflow do not introduce a second
canonical execution algebra.

CML logical actions compile to the existing UnitOfWork program:

```text
CML Action
  -> Cozy/SimpleModeler resolver/compiler
  -> ExecProgram[UnitOfWorkOp]
  -> UnitOfWork analysis/planning
  -> interpreter/drivers
```

Model semantics and execution semantics remain distinct: the CML action is a
model-level logical identity; `UnitOfWorkOp` is CNCF's executable vocabulary.

## Planner Implication

`ExecProgram` must not be equated with one local transaction. The existing
algebra contains datastore/entity operations and externally observable effects
such as HTTP/process execution.

Phase 64.2 therefore extends the existing Free × UnitOfWork model primarily by
adding/strengthening pre-execution analysis and planning:

```text
ExecProgram
  -> Local Atomic Segment
  -> Distributed Atomic / 2PC Segment
  -> After-Commit Compensatable Segment
  -> Irreversible Segment
```

Transaction, reversibility, compensation, idempotency, and CML action provenance
are planning metadata/constraints around the canonical UnitOfWork program.

## Algebra Extension Rule

A new `UnitOfWorkOp` case may be added only when the missing executable intent is
generic CNCF functionality and cannot be expressed safely by existing
operations plus metadata. StateMachine/Workflow model identity alone is not a
reason to extend the low-level algebra.

## Testability

The existing Free structure is also the canonical test seam. Test interpreters
and fake drivers consume the same `ExecProgram` as production and can inspect
operations, inject failures, test atomic abort, inspect compensation, and avoid
real external I/O.

## Updated document

- `docs/phase/phase-64.2.md`
