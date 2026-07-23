# Entity internal DSL: claim-or-load handoff

date=2026-07-23
status=implemented-for-p8-42
requester=textus-cbd-support Phase 8 P8-42

## Problem

CBD Support CAR Review needs to coalesce concurrent identical diagnosis
requests. One request must atomically become the owner of an exact reuse key;
the others must receive the already-active owner or a compatible completed
result. This must work for an initial SQLite profile and a future shared
datastore profile.

The current protected Entity DSL has `entity_create`, `entity_load`,
`entity_update`, `entity_upsert`, and identity lookup. It intentionally does
not expose a raw datastore to components. `entity_upsert` cannot implement
this protocol: when the deterministic key already exists it updates that
record, which could replace the active Review Run ID. A load-then-create
sequence also races across component processes.

## Required extension

Add a protected internal-DSL Entity operation equivalent to:

```
entity_claim_or_load(identity, candidate) -> EntityClaimResult
```

where `EntityClaimResult` is one of:

- `Claimed(CreateResult[T])`: this UnitOfWork created the record;
- `Loaded(T)`: an existing record with the same stable identity was retained;
- `Conflict`: the stored record violates an explicitly supplied immutable
  identity predicate.

The operation must:

1. route only through the component Entity/UnitOfWork boundary;
2. perform datastore-native create-if-absent or an equivalent serializable
   transaction, never a process-local lock;
3. preserve normal Entity authorization, lifecycle, CallTree, audit, and view
   invalidation behavior;
4. work with the configured datastore profile (SQLite initially, shared
   datastore later) without component SQL/JDBC/vendor code;
5. avoid turning a claim into an upsert or a visible generic CRUD endpoint.

## CBD Support use

`ReviewDiagnosis` will use the P8-41 tuple
`(keyDefinitionId, reuseKeyDigest)` as the immutable identity. The claim
result controls whether CBD starts provider work, joins the stored active Run,
or reuses a stored completed Report. Completion subsequently uses normal
internal Entity updates and immutable composition snapshots. Failed,
cancelled, expired, and retention behavior remain CBD P8-43/P8-45 work.

## Acceptance evidence

- CNCF specs prove two concurrent UnitOfWork executions yield exactly one
  `Claimed` result and one-or-more `Loaded` results for one identity.
- A SQLite-backed profile and a shared-datastore test profile prove the same
  result without component SQL/JDBC imports.
- Existing Entity authorization, CallTree/audit emissions, and rebuildable
  View invalidation remain observable for both outcomes.
- A component sample proves a joined request does not invoke its expensive
  work twice.

## Implementation record

On 2026-07-23 CNCF added `EntityStore.EntityClaimResult`,
`UnitOfWorkOp.EntityStoreClaimOrLoad`, and protected ActionCall DSL methods
`entity_claim_or_load` and `entity_claim_or_load_internal`. The latter marks
create and duplicate-load authorization as `ServiceInternal`; it is for
server-owned records derived from admitted input, not for a public endpoint.
The UnitOfWork interpreter still performs the EntityStore operation, updates
EntitySpace on a successful claim, invalidates Views, and emits normal
UnitOfWork/CallTree observations.

`textus-cbd-support` now uses the internal variant for its stable
`ReviewDiagnosis` identity. Its focused executable spec proves a first plan
becomes owner and a second distinct Review ID joins the owner without direct
datastore use. SQLite multi-process conflict mapping and completion/reuse
snapshot handling remain separate acceptance work before the broader Phase 8
item can close.
