# SimpleEntity Revision and OCC Simplification Consideration

date = 2026-07-24
status = accepted-direction-adjusted
target_phase = 50
related_phase = 49

## Purpose

This journal records the discussion that changed the planned Entity
concurrency representation from a separate framework token and snapshot
carrier to a standard `SimpleEntity.revision` attribute.

It is a chronological decision record, not a normative specification. The
current implementation proposal is recorded in:

- `docs/notes/simpleentity-revision-occ-simplification-proposal.md`

Accepted behavior must later be reflected in design, specification, and
Executable Specification documents.

## Starting Point

Phase 49 introduced the need for:

- deterministic optimistic concurrency control;
- an authoritative store-backed comparison token;
- atomic conditional Entity transition; and
- exactly-one-winner behavior across concurrent callers.

The initial proposal represented concurrency outside the Entity:

```text
EntityConcurrencyToken
EntitySnapshot(entity, token)
```

The persisted value was considered framework metadata rather than part of the
`SimpleEntity` model.

## Question Raised

The discussion asked whether the concurrency facility should instead be a
basic `SimpleEntity` feature.

The key observation was that revision history remains useful even when an
application does not enforce OCC:

- later activation of OCC does not require inventing a token;
- conditional transition already requires an authoritative revision;
- revision supports transport validators and diagnostics;
- every provider can expose one common mutation sequence; and
- application models avoid carrying a parallel Entity snapshot abstraction.

This led to a distinction between maintaining a revision and enforcing a
revision precondition.

## Accepted Direction

Every persisted `SimpleEntity` has a framework-managed `revision`.

CNCF:

- initializes it on creation;
- advances it on every successful persistent mutation;
- returns it on Entity reads and mutation results; and
- prevents application code from writing it directly.

This happens regardless of whether ordinary OCC is enabled.

The application separately selects an Entity or collection concurrency
policy:

```text
None
Optimistic
```

`None` means that ordinary mutation does not compare an expected revision.
It does not disable revision maintenance.

`Optimistic` means that every ordinary mutation requires
`expectedRevision` and compares it atomically with the authoritative stored
revision.

The selected policy is declarative. It is not a per-request option. Once an
Entity is configured as `Optimistic`, a caller cannot omit the expected
revision to obtain last-write-wins behavior.

## Why Revision Belongs to SimpleEntity

`revision` describes the persistence identity state of the Entity itself. It
is not domain business data, but it has the same framework-managed status as
standard lifecycle metadata.

Putting the revision on `SimpleEntity` provides one canonical representation:

```text
loaded Entity
  = business state
  + lifecycle metadata
  + current revision
```

The alternative requires callers and projections to keep an Entity and a
separate token paired correctly. That increases API surface and creates more
opportunities to lose or mismatch concurrency metadata.

The decision does not make revision application-writable. It remains readable
managed metadata owned by the framework.

## Impact Limited to One New Attribute

`SimpleEntity` already carries creation and update timestamps. The only model
addition selected for this work is:

```text
revision
```

No OCC-specific timestamp is added.

The role split is:

```text
createdAt = when the Entity was created
updatedAt = when the Entity was last changed
revision  = which successful persisted mutation produced this state
```

Using `updatedAt` as an OCC token was rejected because timestamps depend on
clock behavior, provider precision, and collision characteristics. Revision
comparison is deterministic and independent of wall-clock time.

## Datatype and Model Ownership

Repository inspection confirmed that:

- generic datatype, schema, record, `ValueReader`, and `Consequence`
  facilities are owned by `simplemodeling-lib` / `goldenport-core`; and
- `SimpleEntity` is owned by `simplemodeling-model`.

The accepted placement is therefore:

```text
simplemodeling-lib
  generic reusable datatype/decoding primitives only

simplemodeling-model
  EntityRevision
  SimpleEntity.revision

CNCF
  automatic persistence lifecycle
  OCC policy and enforcement
  UnitOfWork/DataStore integration
```

`EntityRevision` is not placed in CNCF because it is part of the
`SimpleEntity` model contract. It is not placed directly in
`simplemodeling-lib` because revision is Entity-specific rather than a generic
core datatype.

If implementation reveals a missing generic integral validation, schema, or
decoding primitive, that primitive may be added to `simplemodeling-lib`.
The addition must be reusable independently of `EntityRevision`; CNCF must not
request a one-use convenience API from core.

## API Consequence

Making revision part of `SimpleEntity` simplifies the OCC API.

The canonical inputs and outputs become:

```text
input:
  entity id
  expectedRevision when required
  mutation

output:
  updated SimpleEntity with its next revision
```

A separate `EntitySnapshot[A]` is no longer needed merely to carry the
revision. Transition results also do not need a second token field beside an
Entity that already contains the authoritative revision.

The proposed `EntityConcurrencyToken` API therefore does not become a
deprecated compatibility layer. Phase 50 replaces it directly.

## Conditional Transition Rule

Conditional transition has stricter semantics than an ordinary update.

It always compares the expected revision, including when the Entity's ordinary
concurrency policy is `None`.

This preserves the Phase 49 requirement:

```text
expected authoritative root
  -> exactly one admitted transition winner
```

An application may choose last-write-wins for ordinary edits without weakening
the atomic guard required by a conditional transition.

## Compatibility Decision

Compatibility with the provisional Phase 49 token/snapshot shape is not
required.

The implementation should not add:

- token aliases;
- snapshot adapters;
- duplicate `revision` and `cncfRevision` fields;
- virtual revision zero for records with no revision; or
- message-level compatibility branches.

If existing persisted data lacks revision, it requires an explicit migration
or recreation. Silent derivation from timestamps or resident state would
weaken the authoritative concurrency contract.

## Subsequent Adjustment: Non-SimpleEntity Extension

After Phase 49 closed, its completed implementation was compared with the
initial Phase 50 replacement plan.

The review clarified that Phase 49 did not implement a separate revision
database. It implemented revision external to the domain Entity:

- the provider stores a framework-managed revision field in the authoritative
  persistence record;
- the domain codec does not own that field; and
- a framework carrier keeps the domain value and revision paired.

Discarding that capability would remove useful revision support for Entity
models that do not extend `SimpleEntity`.

The Phase 50 direction is therefore refined without changing the main
decision:

```text
SimpleEntity
  -> embedded SimpleEntity.revision
  -> canonical path

non-SimpleEntity Entity
  -> explicit detached EntityRevision carrier
  -> extension path
```

Both paths use the same `EntityRevision`, provider-native atomic mutation,
authorization, UnitOfWork, diagnostics, and observability boundaries.

The detached path is not a compatibility layer:

- it does not preserve `EntityConcurrencyToken`;
- it is not selected implicitly when an embedded revision is absent;
- it cannot be selected for an ordinary `SimpleEntity`;
- it cannot coexist with embedded revision for one Entity; and
- it is declared at Entity or collection registration rather than per request.

The reusable Phase 49 assets are the revision-field-independent atomic
datastore operation, compare-and-advance semantics, and domain-codec
separation. The provisional token vocabulary remains scheduled for
replacement.

## Phase Allocation

The selected phase division is:

```text
Phase 49
  Entity Conflict and Conditional Transition
  atomicity and transition semantics

Phase 50
  SimpleEntity Revision and OCC Simplification
  standard revision attribute and simplified OCC API
```

Phase 50 is not a compatibility migration phase. It is a deliberate
simplification of the public and persistence model before the provisional API
becomes established.

## Decision Tracker

| Topic | Decision | Status |
| --- | --- | --- |
| Revision location | Standard managed `SimpleEntity` attribute | accepted |
| Revision lifecycle | CNCF always initializes and advances it | accepted |
| OCC selection | Declarative Entity/collection policy | accepted |
| Policy values | `None` and `Optimistic` baseline | accepted |
| Per-request bypass | Not allowed for `Optimistic` Entities | accepted |
| Conditional transition | Expected revision always required | accepted |
| OCC timestamp | Not added | accepted |
| Existing timestamps | Keep `createdAt` and `updatedAt` unchanged | accepted |
| Revision datatype owner | `simplemodeling-model` | accepted |
| Generic datatype support | `simplemodeling-lib` only when independently reusable support is missing | accepted |
| Runtime OCC owner | CNCF | accepted |
| Separate token API | Replace without compatibility | accepted |
| `SimpleEntity` representation | Embedded `SimpleEntity.revision` only | accepted |
| Non-`SimpleEntity` representation | Explicit detached revision extension | accepted |
| Revision source-of-truth | One `EntityRevision` shared by both representations | accepted |
| Representation fallback | No implicit fallback, mirroring, or dual write | accepted |
| Snapshot carrier | Not part of the canonical API | accepted |
| Storage compatibility | No implicit legacy fallback | accepted |
| Force/merge/repair | Remains separate future work | accepted |

## Matters to Fix During Phase Planning

The direction is accepted, while the Phase 50 plan still needs to fix:

- the final package/API shape within the accepted `simplemodeling-model`
  ownership boundary;
- the Entity/collection declaration and physical managed-field contract for
  the explicit detached non-`SimpleEntity` extension;
- descriptor syntax and precedence for Entity versus collection policy;
- the default policy for newly declared and existing Entity definitions;
- explicit schema migration/admission behavior;
- which read projections expose revision by default;
- generated Form and REST expected-revision transport details;
- no-op mutation treatment;
- provider capability admission; and
- the exact replacement edits required in the Phase 49 design/specification.

These details must be decided before implementation rather than hidden behind
compatibility behavior.

## Deferred Work

Force overwrite, merge workflows, repair operations, and conflict-resolution
Web UI remain separate future development work. They are not removed by the
Phase 50 simplification.

## References

- `docs/phase/phase-49.md`
- `docs/notes/entity-conflict-conditional-transition-implementation.md`
- `docs/design/entity-conflict-and-conditional-transition.md`
- `docs/spec/entity-conflict-and-conditional-transition.md`
- `docs/design/simpleentity-storage-shape-policy.md`
- `docs/strategy/cncf-development-strategy.md`
