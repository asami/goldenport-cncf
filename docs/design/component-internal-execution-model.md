# Component Internal Execution Model

## Purpose
This document defines the internal execution model for a single CNCF Component.
It fixes how Action execution, UnitOfWork, transaction boundaries, events, and
observability relate inside the component.

## Scope / Non-Goals
- Scope: component-internal execution only.
- Non-Goals: distributed coordination, multi-component transactions,
  sagas, 2PC, or external orchestration.

## Definitions
- UnitOfWork (UoW): action-scoped execution boundary for changes.
- Transaction: datastore-level atomic boundary local to a component.
- Event: fact produced by execution (domain/system).
- Observability: diagnostic outputs (logs/traces/metrics) correlated to execution.

## Internal Execution Boundary (Action ↔ UnitOfWork)
- Action execution is bound to an ActionCall with an explicit ExecutionContext.
- UnitOfWork is the internal boundary for commit/abort semantics.
- Component owns execution concerns; DomainComponent remains pure.

## Persistence Semantics
- A Component assumes a single physical datastore as its internal substrate.
- Datastore updates are committed or aborted through the UnitOfWork boundary.
- Events are facts; when stored locally, they MAY be persisted in the same
  transaction as the associated datastore changes.

## Observability Semantics
- Observability outputs are diagnostic and MUST NOT be transactional.
- Every log/trace/metric record MUST be correlated to the UnitOfWork identifier
  so failed or aborted execution can be attributed.
- Observability follows the execution lifecycle defined in
  `docs/design/execution-model.md`.

## Notes / Future Work
- Cross-component coordination and OTel integration are handled in separate
  documents; they are out of scope here.
- This document complements:
  `docs/design/execution-model.md`,
  `docs/design/execution-context.md`,
  `docs/design/component-model.md`,
  `docs/design/id.md`.
