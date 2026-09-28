# Operation / Client / UI Continuity

Date: 2026-09-27

Status: architectural direction

## Context

Textus Flutter Application Framework (TFAF) is being developed with the NICT Editing Studio application as its Development Driver. The first UI pattern is Resource List + Resource Detail, initially backed by fake data and later by server Operations.

This exposes an important CNCF concern: Operation must be usable as part of a continuous semantic contract from model/server execution to generated client binding and application UI.

## Direction

CNCF Operation contracts should remain suitable for the following continuity:

```text
CML / Application Model
        -> CNCF Operation
        -> transport/API representation
        -> typed/generated client binding
        -> client semantic data/action source
        -> application UI
```

The client UI must not need to reconstruct Operation semantics from ad-hoc REST endpoints.

The intended semantic correspondence is:

- Query returning a collection -> collection/list data source
- Query returning one Resource/View -> detail data source
- Command -> client/UI Action
- asynchronous Command/Job -> execution status/progress/result
- Workflow / Continuation -> workflow/human-interaction UI

## Design implication

This does not make CNCF depend on Flutter or TFAF. CNCF owns server/runtime Operation semantics and a contract that can be projected into clients.

Transport details such as REST/JSON remain realizations of that contract rather than the semantic API itself.

CML/CNCF metadata should therefore preserve enough information for Cozy or another generator to create a typed client and binding without application-specific glue.

## Development proof

The first proof is intentionally indirect:

1. TFAF defines a semantic ResourceDataSource boundary.
2. NICT Editing Studio drives it first with fake data.
3. The same boundary is then implemented using CNCF server Operations.
4. Standard List/Detail UI and its configuration remain unchanged.

If replacing fake data with CNCF Operations requires redesigning ordinary List/Detail UI semantics, the model/server/client boundary should be reviewed rather than hidden in application adapters.

This journal records the requirement so future Operation/API work considers client-generation and UI continuity from the start.
