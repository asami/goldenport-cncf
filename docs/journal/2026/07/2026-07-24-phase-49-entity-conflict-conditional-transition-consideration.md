# Phase 49 Entity Conflict and Conditional Transition Consideration

date=2026-07-24
status=accepted-phase-scope
strategy_items=9.12,9.39
phase=49

## Position of This Record

This journal records why Phase 49 combines the concurrency foundation from
`9.12 Entity and Aggregate Version Conflict Policy` with
`9.39 Entity Conditional Transition Internal DSL`.

It is a chronological decision record, not a normative specification. The
proposed implementation is recorded separately in:

- `docs/notes/entity-conflict-conditional-transition-implementation.md`

Stable contracts must be promoted to `docs/design`, `docs/spec`, and
Executable Specifications before implementation is considered complete.

## Starting Problem

CBD Support must retain a terminal Review Run and allow exactly one caller to
install its successor for the same reuse identity. Two callers can observe the
same terminal root and both attempt to replace its active Run pointer.

The required behavior is:

```text
stable root identity
  + expected terminal root state
  + expected active successor owner
  + candidate successor
    -> exactly one Transitioned
    -> all other callers receive NotMatched(authoritative current root)
```

A normal load followed by an update cannot provide this guarantee. A
process-local lock cannot provide it across runtime processes. An upsert cannot
provide it because an upsert is allowed to replace an existing record.

## Why 9.12 and 9.39 Belong in One Phase

The two items express different levels of the same consistency boundary:

- 9.12 defines a store-backed concurrency token and deterministic stale-write
  detection.
- 9.39 uses that foundation to guard a multi-record Entity transition with a
  restricted expected predicate.

Implementing 9.39 without the 9.12 foundation would create a one-off CBD-style
compare-and-swap operation with no canonical Entity conflict semantics.
Implementing the relevant part of 9.12 first and deferring 9.39 would leave the
original successor-ownership problem unresolved and would not prove that the
concurrency contract supports a real compound transition.

Phase 49 therefore uses the conditional transition as the vertical driver for
the generic conflict foundation.

## Selected Phase 49 Scope

Phase 49 is named:

```text
Entity Conflict and Conditional Transition
```

It includes:

1. a framework-owned, store-backed Entity concurrency token;
2. deterministic stale-write and expected-token conflict semantics;
3. one closed, typed conditional-transition model;
4. a datastore-native atomic operation that verifies the expected root,
   creates or binds one successor, updates the root, and advances its token;
5. protected ActionCall/Behavior internal DSL and UnitOfWork algebra support;
6. authorization, lifecycle validation, audit, CallTree, metrics, EntitySpace,
   Working Set, and View invalidation integration;
7. deterministic in-memory reference behavior;
8. SQLite atomicity and simultaneous-attempt evidence;
9. one shared-datastore provider profile with the same simultaneous-attempt
   evidence; and
10. CBD Support acceptance proving one terminal Review Run gains exactly one
    successor owner.

## Deliberate Scope Limit on 9.12

Phase 49 does not attempt to finish every possible conflict-resolution feature.
It implements the reusable 9.12 foundation required by ordinary version-aware
Entity mutation and by 9.39.

This bounded scope is the complete baseline meaning of 9.12. When Phase 49
meets its acceptance criteria:

- 9.12 is recorded as completed for the Entity/Aggregate version-conflict
  policy baseline;
- 9.39 is recorded as completed for the conditional-transition internal DSL;
- both active entries are removed from strategy section 9; and
- one combined completed-history item,
  `Entity Conflict and Conditional Transition`, is added to strategy section 8.

9.12 must not remain indefinitely as a partially completed umbrella. Explicit
force/repair commands, merge workflows, and conflict-resolution UI are tracked
immediately as the separate future development item
`9.40 Entity Conflict Resolution and Repair`. This prevents the deferred scope
from disappearing when 9.12 and 9.39 move to completed history.

The following remain outside this phase:

- conflict-resolution Web UI;
- merge editors;
- application-specific overwrite policy;
- general repair workflows;
- arbitrary caller-authored compare-and-swap expressions;
- cross-component atomic mutation;
- distributed consensus, leases, or fencing;
- multi-region ownership; and
- a public conditional CRUD protocol.

Transport projection of an existing structured conflict may be added where
required for executable acceptance, but transport-specific conflict UX is not
a Phase 49 goal. The broader operator/application resolution surface belongs
to 9.40.

## Selected Semantic Distinctions

### Stale ordinary update

An ordinary version-aware save or update with an obsolete expected token is a
structured `Consequence.Failure(Conclusion)` with conflict semantics. The
requested mutation did not satisfy its declared concurrency precondition.

### Conditional transition not matched

`NotMatched(existing)` is a normal typed result. It is expected control flow:
another caller may already have completed the valid transition, or the root may
no longer satisfy the admitted immutable predicate.

### Infrastructure or policy failure

Authorization denial, malformed transition policy, unsupported provider
capability, datastore failure, transaction failure, and conversion failure
remain structured `Consequence` failures. They must not be collapsed into
`NotMatched`.

## Selected Atomicity Boundary

The datastore provider, not component code, owns atomicity.

The protected DSL produces a closed transition intent. `UnitOfWork` authorizes
and interprets that intent. `EntityStore` translates it to a provider-neutral
datastore mutation plan. One admitted datastore provider executes the complete
plan in one native transaction.

The required transaction contains:

```text
load or lock authoritative root
  -> verify token and restricted expected values
  -> create or bind successor
  -> update root active reference and managed revision
  -> commit
```

Any failure rolls back both successor and root mutation.

Component code receives no raw `DataStore`, database connection, SQL/JDBC
handle, transaction object, or process-local lock.

## Alternatives Rejected

### Load, check, then save in the internal DSL

Rejected because the check and update race across UnitOfWork and process
boundaries.

### Process-local synchronization

Rejected because it cannot coordinate multiple CNCF processes or a shared
datastore deployment.

### Reusing upsert

Rejected because upsert may mutate an existing record and does not express
one-winner successor ownership.

### Implementing only a CBD Support repository method

Rejected because it would bypass the CNCF authorization, UnitOfWork,
observability, datastore isolation, and provider substitution boundaries.

### Exposing a general predicate language

Rejected because arbitrary predicates broaden the security and provider
translation surface. The first contract admits only a closed framework-owned
expectation model needed for revision and exact immutable-value matching.

### Making every DataStore silently emulate the operation

Rejected because load-then-save is not a valid fallback. A provider that
cannot supply the atomic capability must fail activation or operation
admission deterministically.

## Relationship to Claim-or-Load

`entity_claim_or_load` solves initial ownership for a stable Entity identity.
It must not be expanded into an upsert or replacement operation.

Conditional transition solves a later lifecycle problem:

```text
existing retained root
  -> verify exact previous owner/state
  -> install one successor
```

The operations share datastore atomicity requirements but have different
semantics and result models. Phase 49 may reuse provider machinery, while
keeping both protected DSL operations explicit.

## Phase Completion Direction

Phase 49 can close only when:

- normative design and specification documents define token, transition, and
  result semantics;
- version-aware mutation cannot be bypassed by a resident Working Set value;
- concurrent transition specs produce exactly one `Transitioned`;
- rollback leaves neither an orphan successor nor a partially updated root;
- SQLite and a shared-datastore profile prove the same semantic result;
- authorization and observability evidence remain on the normal ActionCall and
  UnitOfWork path; and
- CBD Support uses the generic primitive without SQL/JDBC/raw DataStore access.

## References

- `docs/journal/2026/07/entity-internal-dsl-conditional-transition-handoff-2026-07-23.md`
- `docs/journal/2026/07/entity-internal-dsl-claim-or-load-handoff-2026-07-23.md`
- `docs/journal/2026/07/2026-07-23-datastore-boundary-and-shared-database-decision.md`
- `docs/notes/unitofwork-guideline.md`
- `docs/strategy/cncf-development-strategy.md`
