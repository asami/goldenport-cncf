# Phase 49 - Entity Conflict and Conditional Transition

status=open
started_at=2026-07-24
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 49 Checklist](phase-49-checklist.md)

## Purpose

Provide one store-backed Entity concurrency contract and one protected
conditional-transition internal DSL so CNCF can reject stale writes
deterministically and atomically install exactly one successor for an admitted
root transition.

Phase 49 combines the concurrency foundation selected from
`9.12 Entity and Aggregate Version Conflict Policy` with
`9.39 Entity Conditional Transition Internal DSL`. The conditional transition
is the vertical driver that proves the generic conflict model through a real
compound Entity mutation.

Successful closure completes both the 9.12 version-conflict baseline and 9.39.
They are recorded together in strategy completed history rather than leaving
9.12 as a partially completed umbrella.

## Selected Direction

- Every version-aware Entity mutation uses a framework-owned concurrency token
  persisted as managed Entity metadata.
- A stale ordinary save/update is a structured conflict
  `Consequence.Failure(Conclusion)`.
- A conditional transition verifies one required token plus a closed set of
  exact immutable-value expectations.
- `Transitioned` and `NotMatched(existing)` are normal typed conditional
  transition outcomes.
- The datastore provider executes root verification, successor create/bind,
  root mutation, and token advancement as one native atomic operation.
- Component code reaches the capability only through protected
  ActionCall/Behavior DSL and UnitOfWork algebra.
- No provider may emulate atomicity with an unlocked load-then-save sequence.
- Authorization, lifecycle validation, audit, CallTree, metrics, EntitySpace,
  Working Set, and View invalidation remain on the normal CNCF chokepoint.
- In-memory behavior is the deterministic semantic reference. SQLite and one
  shared-datastore profile must prove equivalent simultaneous-attempt
  behavior.
- CBD Support terminal Review Run successor ownership is the first downstream
  acceptance driver, while CNCF types and operations remain domain-neutral.

## Scope

- Normative design and static specification for:
  - Entity concurrency token and managed storage metadata;
  - expected-token mutation and stale conflict behavior;
  - restricted conditional-transition expectation;
  - successor create/bind semantics;
  - typed `Transitioned` and `NotMatched(existing)` results;
  - datastore atomic capability and unsupported-provider behavior;
  - authorization, observability, audit, transaction, and cache boundaries.
- Typed concurrency, expectation, successor, mutation, request, and result
  models.
- Expected-token forms for the Entity/Aggregate mutation paths needed by the
  conditional-transition foundation.
- One record-level atomic datastore mutation plan and result.
- Deterministic in-memory implementation.
- SQLite implementation using one native transaction and independent
  connections in concurrency evidence.
- One shared-datastore provider profile proving the same semantic result.
- EntityStore, EntityStoreSpace, UnitOfWorkOp, UnitOfWorkInterpreter, and
  protected ActionCall/Behavior DSL integration.
- Structured conflict and unsupported-capability failures using normal
  `Consequence`/`Conclusion` semantics.
- Working Set/EntitySpace reconciliation and View invalidation only after the
  authoritative datastore outcome.
- CBD Support migration from application-side successor ownership to the
  generic protected DSL.

## Boundaries

- Phase 49 does not expose generic conditional CRUD through REST, Form, CLI,
  MCP, or public component APIs.
- The expected predicate is a closed equality model. Arbitrary expressions,
  scripts, SQL, ranges, and caller-authored query predicates are excluded.
- Component code receives no raw `DataStore`, SQL/JDBC handle, database
  connection, provider transaction, or process-local lock.
- Upsert is not conditional transition and cannot replace its semantics.
- `entity_claim_or_load` remains the initial stable-identity ownership
  operation and is not expanded into replacement/update behavior.
- A provider without the required atomic capability fails deterministically.
  CNCF does not offer a best-effort non-atomic fallback.
- The atomic transition is limited to collections owned by one component and
  admitted to one datastore transaction domain.
- Cross-component atomic mutation, distributed consensus, leases, fencing,
  multi-region ownership, and cluster-wide cache invalidation are excluded.
- Conflict merge UI, repair UI, application overwrite policy, and general
  workflow/state-machine replacement are excluded.
- CBD-specific Review state names, reuse keys, retention policy, and successor
  payload construction remain in CBD Support.
- Deferred force/repair commands, merge workflows, and conflict-resolution UI
  do not keep 9.12 open. They are tracked by
  `9.40 Entity Conflict Resolution and Repair`.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| EC-01 | Normative contract | Design/spec fix token, conflict, transition, atomicity, provider, and result semantics. | done |
| EC-02 | Concurrency model and storage shape | Managed revision metadata and typed concurrency values have deterministic persistence and migration behavior. | active |
| EC-03 | Version-aware mutation | Required Entity/Aggregate mutation paths compare the caller token atomically and return structured stale conflicts. | planned |
| EC-04 | Atomic datastore capability | A closed provider-neutral plan executes guard, successor, root update, and token advance in one transaction without fallback. | planned |
| EC-05 | EntityStore, UnitOfWork, and DSL | Protected typed conditional transition preserves authorization, lifecycle, transaction, and normal effect boundaries. | planned |
| EC-06 | Coherence and diagnostics | EntitySpace, Working Set, View, audit, CallTree, metrics, and structured failures reflect only authoritative outcomes. | planned |
| EC-07 | Provider and concurrency evidence | In-memory, SQLite, and one shared profile prove one winner, rollback safety, restart visibility, and provider parity. | planned |
| EC-08 | CBD Support acceptance | Terminal predecessor retention and exactly-one successor ownership use the generic CNCF DSL without storage bypass. | planned |
| EC-09 | Verification and closure | Focused/full validation, review, documentation promotion, downstream evidence, and closure records are complete. | planned |

## Acceptance

- A newly persisted version-aware Entity has one canonical concurrency token.
- Every successful admitted version-aware mutation advances the token exactly
  once.
- An ordinary mutation carrying a stale expected token changes no stored or
  resident state and returns a structured conflict.
- A resident Working Set value cannot bypass the store-backed token check.
- A conditional transition verifies its token and admitted immutable values
  against the authoritative stored root inside the provider transaction.
- Exactly one of two or more simultaneous valid successor attempts returns
  `Transitioned`.
- Losing attempts return `NotMatched(existing)` after normal read
  authorization and observe the authoritative root.
- A failed guard creates no successor and changes no root.
- Failure during successor creation, root mutation, or commit leaves no orphan
  successor and no partially changed root.
- `Transitioned` updates or evicts admitted EntitySpace/Working Set values and
  invalidates affected Views only after datastore success.
- `NotMatched` reconciles a stale local root without reporting a mutation by
  the losing caller.
- Authorization denial occurs before an unauthorized mutation and cannot leak
  root or successor payloads.
- CallTree, metrics, and audit distinguish transition, mismatch, stale
  conflict, unsupported capability, authorization denial, and provider failure
  without parsing display text or recording payload values.
- SQLite evidence uses independent callers/connections against one database.
- A shared-datastore profile proves the same one-winner and rollback
  semantics.
- CBD Support retains the terminal predecessor, installs exactly one successor,
  and invokes successor work once without SQL/JDBC/raw DataStore access.
- The verified concurrency-token, expected-token, stale-conflict, Working Set,
  and ordinary-overwrite-prevention behavior completes the 9.12 baseline.
- The verified protected atomic transition completes 9.39.

## Verification

Phase 49 closure requires:

- focused model, UnitOfWork, EntityStore, DataStore, authorization,
  observability, cache, and provider executable specifications;
- property-based simultaneous-attempt evidence for bounded caller counts;
- SQLite transaction and restart evidence;
- shared-datastore acceptance evidence;
- CBD Support downstream acceptance;
- `sbt --batch Test/compile`;
- the full CNCF test suite;
- relevant downstream test suites;
- `git diff --check`;
- read-only review, review-fix where required, and clean re-review;
- strategy, phase, design, spec, and implementation documentation aligned with
  the verified result.
- one combined strategy section 8 completion record for
  `Entity Conflict and Conditional Transition`;
- removal of 9.12 and 9.39 from active/future strategy section 9; and
- preservation of 9.40 as the separate future conflict-resolution/repair
  development item.

## Planning References

- `docs/journal/2026/07/2026-07-24-phase-49-entity-conflict-conditional-transition-consideration.md`
- `docs/notes/entity-conflict-conditional-transition-implementation.md`
- `docs/journal/2026/07/entity-internal-dsl-conditional-transition-handoff-2026-07-23.md`
- `docs/journal/2026/07/entity-internal-dsl-claim-or-load-handoff-2026-07-23.md`
- `docs/journal/2026/07/2026-07-23-datastore-boundary-and-shared-database-decision.md`
- `docs/notes/unitofwork-guideline.md`

## Normative Contract

- `docs/design/entity-conflict-and-conditional-transition.md`
- `docs/spec/entity-conflict-and-conditional-transition.md`

These documents complete the EC-01 normative contract. Independent review,
review-fix, and clean re-review found no remaining actionable issue. Later
stages must provide the executable evidence assigned by the static
specification.

## Current Resume Point

EC-01 is complete. EC-02A completed the typed concurrency token, snapshot
carrier, and isolated framework metadata codec with property-based evidence.
The slice passed focused validation, clean read-only re-review, and the full
CNCF suite. EC-02 remains active. EC-02B next integrates the codec into
canonical Entity create/load and managed storage-shape paths.
