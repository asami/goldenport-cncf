# SimpleEntity Revision and OCC Simplification Proposal

status = proposed, non-normative
date = 2026-07-24
target_phase = 50
related_phase = 49

## Position

This note proposes a simpler canonical concurrency model for `SimpleEntity`.
It is intended as the implementation proposal for Phase 50.

The proposal replaces the separate Entity concurrency-token model considered
during Phase 49. Compatibility with that provisional API is not required.
The useful ability to keep revision outside a domain codec is retained as an
explicit detached-revision extension for Entity models that do not extend
`SimpleEntity`.

The corresponding consideration record is:

- `docs/journal/2026/07/2026-07-24-simpleentity-revision-occ-consideration.md`

This note is not normative. Accepted behavior must be reflected in the
canonical design and specification documents and fixed by Executable
Specifications before Phase 50 closes.

## Goal

Make revision management a basic `SimpleEntity` facility while keeping the
decision to enforce optimistic concurrency under application control.

The central split is:

```text
revision lifecycle
  = always managed by CNCF

revision precondition enforcement
  = selected by Entity or collection concurrency policy
```

This avoids two competing representations of the same persisted state:

```text
SimpleEntity
  + separate EntityConcurrencyToken
  + separate EntitySnapshot
```

The standard representation is:

```text
SimpleEntity
  + framework-managed revision
```

The admitted extension is:

```text
non-SimpleEntity domain value
  + explicit detached EntityRevision carrier
```

Both use one `EntityRevision` value and one atomic persistence kernel. They
are not interchangeable representations for one Entity.

## SimpleEntity Model Change

The only new standard `SimpleEntity` attribute is `revision`.

Existing lifecycle attributes remain unchanged:

```text
createdAt
updatedAt
```

The proposed Scala-level type is a validated value type rather than a raw
numeric value:

```scala
final case class EntityRevision(value: Long)
```

Conceptually, every persisted `SimpleEntity` provides:

```scala
def revision: EntityRevision
```

The ownership boundary is:

| Project | Responsibility |
| --- | --- |
| `simplemodeling-lib` (`goldenport-core`) | Generic datatype, schema, `ValueReader`, `Consequence`, and record-decoding facilities only |
| `simplemodeling-model` | `EntityRevision` and the standard `SimpleEntity.revision` attribute |
| CNCF | Persistence behavior, atomic comparison, automatic advancement, policy, and operation semantics |

`EntityRevision` is an Entity-model concept and should therefore live under
the `org.simplemodeling.model` datatype/model boundary, provisionally:

```scala
org.simplemodeling.model.datatype.EntityRevision
```

It must not be placed in CNCF merely because CNCF enforces OCC.

`simplemodeling-lib` must not gain `SimpleEntity`, OCC, or CNCF semantics. It
is changed only if implementation proves that a genuinely reusable primitive
is missing, such as generic validated integral-value decoding or schema
support. Such a primitive must be independently useful outside Entity
revision. Phase 50 must not add a one-use core convenience API.

## Revision Representation

Revision source-of-truth and revision representation are separate concerns.
The source-of-truth is always `EntityRevision`.

| Entity model | Representation | Admission |
| --- | --- | --- |
| extends `SimpleEntity` | embedded `SimpleEntity.revision` | standard and required |
| does not extend `SimpleEntity` | detached revision carrier | explicit extension |
| extends `SimpleEntity` | detached revision carrier | rejected |
| does not extend `SimpleEntity` | implicit detached revision | rejected |

The revision representation is selected at Entity or collection registration,
not per request. CNCF must reject conflicting declarations, two revision
fields, mirroring, dual writes, and implicit fallback between representations.

The detached extension preserves the Phase 49 capability to:

- keep managed revision outside the domain codec;
- carry revision beside a domain value when that value cannot expose it;
- use the revision-field-independent provider atomic mutation; and
- retain the standard authorization, UnitOfWork, diagnostics, and
  observability boundaries.

It does not preserve the provisional `EntityConcurrencyToken` type or make the
Phase 49 API a compatibility contract. A replacement carrier should use
`EntityRevision`, for example:

```scala
final case class EntityRevisionCarrier[A](
  entity: A,
  revision: EntityRevision
)
```

The final name and declaration surface are fixed by Phase 50 executable
acceptance before implementation.

## Managed Attribute Semantics

`revision` is a framework-managed attribute.

- CNCF assigns the initial revision when an Entity is created.
- CNCF advances the revision exactly once for each successful persistent
  mutation.
- Reads do not advance the revision.
- Failed and rolled-back mutations do not advance the revision.
- Soft delete and restore advance the revision because they mutate persisted
  Entity state.
- Hard delete has no successor revision because the Entity no longer exists.
- Application code may read the revision.
- Application code must not set, patch, reset, decrement, or increment it.
- A create or mutation request that attempts to write `revision` fails
  deterministically as a managed-attribute violation.

The proposed initial revision is:

```text
1
```

A successful mutation from revision `n` persists revision `n + 1` in the same
atomic datastore operation as the business-state change.

## Timestamp Separation

No OCC-specific timestamp is added.

The three attributes have separate responsibilities:

| Attribute | Responsibility |
| --- | --- |
| `createdAt` | Entity creation time |
| `updatedAt` | Last successful update time |
| `revision` | Mutation order and optimistic-concurrency comparison |

`updatedAt` must not be used as the canonical OCC token. Timestamp comparison
introduces clock, precision, collision, and provider-consistency concerns that
do not exist with a monotonically advanced revision.

Transport validators such as an HTTP `ETag` may be derived from `revision`.
They do not replace it as the persistence precondition.

## Concurrency Policy

The application selects whether ordinary mutations enforce revision
comparison. The policy is declarative and applies at Entity or collection
scope.

Proposed model:

```scala
sealed abstract class EntityConcurrencyPolicy

object EntityConcurrencyPolicy {
  case object None extends EntityConcurrencyPolicy
  case object Optimistic extends EntityConcurrencyPolicy
}
```

### None

- CNCF still initializes and advances `revision`.
- Ordinary mutations do not require `expectedRevision`.
- Last-write-wins behavior remains possible.
- The application cannot directly write the revision.

### Optimistic

- Every admitted ordinary mutation requires `expectedRevision`.
- CNCF compares it with the authoritative persisted `revision` in the
  datastore mutation.
- A mismatch changes no state and returns a structured conflict
  `Consequence.Failure(Conclusion)`.
- A request cannot bypass the policy by omitting `expectedRevision`.
- Per-request opt-out is not supported.

The policy is a stable application design decision, not a caller preference.

## Canonical Mutation Contract

The public framework mutation contract becomes:

```text
entity id
  + optional/required expectedRevision according to policy
  + admitted mutation
    -> updated SimpleEntity containing next revision
    | structured revision conflict
    | another structured failure
```

The datastore performs comparison, business mutation, lifecycle metadata
update, and revision advancement atomically:

```text
UPDATE entity
SET business fields,
    updated_at = operation time,
    revision = revision + 1
WHERE id = entity id
  AND revision = expected revision
```

The exact provider operation need not be SQL, but it must provide equivalent
atomic semantics.

## Read and Projection Contract

A loaded `SimpleEntity` already carries its current revision. Therefore a
separate wrapper is not used for the standard model.

A non-`SimpleEntity` Entity admitted to the detached extension returns an
explicit revision carrier. That carrier is not projected implicitly on
ordinary `SimpleEntity` surfaces.

Entity read, search, Aggregate, View, Web, Form, and REST projections expose
the revision where the consumer must be able to submit a later mutation.

Projection rules must preserve the distinction between:

- readable managed metadata; and
- application-writable business fields.

Generated update forms and clients carry the loaded revision as
`expectedRevision`. They must not submit it as an ordinary patch to
`revision`.

## Create Contract

Create requests do not accept an application-supplied revision.

On successful creation CNCF:

1. admits and validates the business value;
2. assigns lifecycle metadata;
3. assigns the initial revision;
4. persists all values atomically; and
5. returns the created `SimpleEntity` with its assigned revision.

## Conditional Transition Contract

Atomic conditional transition always requires an expected revision,
independent of `EntityConcurrencyPolicy`.

```text
conditional transition
  = authoritative revision comparison
  + admitted field expectations
  + atomic successor/root mutation
```

This means:

- `EntityConcurrencyPolicy.None` relaxes ordinary mutation only;
- it does not weaken the conditional-transition guard;
- a transition never infers its expected revision from a newly loaded
  resident value; and
- the transition result returns Entities containing their authoritative
  revisions.

## API Simplification

Phase 50 removes, rather than deprecates, the provisional separate-token API:

- `EntityConcurrencyToken`;
- `EntityMutationExpectation(token)`;
- transition results carrying a token separate from the returned Entity.

The canonical API uses:

- `SimpleEntity.revision`;
- `expectedRevision`;
- `EntityConcurrencyPolicy`; and
- returned `SimpleEntity` values containing the next revision.

The detached extension uses the same `EntityRevision` and an explicitly named
revision carrier for non-`SimpleEntity` values. It is a revised extension API,
not an alias or adapter for `EntitySnapshot[A](entity, token)`.

No compatibility aliases, duplicate storage fields, implicit representation
fallback, or legacy projection roots are introduced.

## Persistence and Migration

The canonical persisted field is proposed as:

```text
revision
```

The embedded `SimpleEntity` path replaces the provisional
`cncfRevision` / `cncf_revision` representation with the standard revision
shape. Phase 50 must separately fix the detached extension's managed physical
field contract before implementation.

Because compatibility is explicitly out of scope:

- a record without `revision` is not silently assigned virtual revision zero;
- runtime load must not synthesize revision from `updatedAt`;
- old schemas and records require an explicit migration or recreation before
  use with the new contract; and
- startup/schema admission should fail deterministically when a required
  revision column or field is unavailable.

Migration tooling and policy may be planned separately if existing deployed
data must be retained.

## Phase Relationship

Phase 49 establishes the atomic conditional-transition behavior and proves the
datastore consistency boundary.

Phase 50 simplifies and standardizes the Entity concurrency surface:

```text
Phase 49
  atomic conditional-transition foundation

Phase 50
  SimpleEntity revision baseline
  + detached revision extension for non-SimpleEntity models
  + ordinary OCC policy
  + API/storage/projection simplification
```

Phase 49's provisional separate-token API must not be treated as a
compatibility commitment.

## Proposed Phase 50 Work

1. Add `EntityRevision` and the standard `SimpleEntity` attribute to
   `simplemodeling-model`, using existing `simplemodeling-lib` generic
   facilities.
2. Extend `simplemodeling-lib` only if an independently reusable datatype,
   schema, or decoding primitive is proven missing.
3. Generalize Phase 49's atomic kernel around `EntityRevision` and add one
   deterministic embedded/detached representation binding.
4. Add managed embedded revision persistence for `SimpleEntity`.
5. Add the explicit detached revision extension for non-`SimpleEntity`
   persistence.
6. Add declarative `None` and `Optimistic` concurrency policies.
7. Integrate expected revision into EntityStore, UnitOfWork, protected DSL,
   generated operation, datastore, and Conditional Transition paths.
8. Project embedded revision through standard REST, Form, Web, View, and
   Aggregate surfaces; keep detached projection explicit.
9. Reject application writes, dual representations, and implicit fallback.
10. Add provider parity, simultaneous-update, rollback, restart, migration,
    and downstream evidence for both admitted representations.
11. Replace the Phase 49 provisional normative contract with the verified
    standard-plus-extension contract.

## Executable Specification Direction

Phase 50 should prove at least:

- every created persisted `SimpleEntity` receives revision 1;
- every successful persistent mutation advances revision exactly once;
- failed or rolled-back mutation does not advance revision;
- policy `None` records revisions without requiring an expected revision;
- policy `Optimistic` rejects missing and stale expected revisions;
- two concurrent updates with one expected revision produce at most one
  winner;
- managed revision input is rejected;
- `updatedAt` is not used as the comparison token;
- conditional transition requires revision under both policies;
- returned Entities contain the authoritative resulting revision;
- no separate concurrency-token type remains;
- a `SimpleEntity` never requires a detached carrier;
- a detached carrier is admitted only for an explicitly configured
  non-`SimpleEntity` model;
- both representations use one `EntityRevision` and one atomic provider
  kernel;
- no Entity contains duplicate embedded and detached revision; and
- records without the required revision fail according to the selected
  explicit migration/admission rule.

Property-based concurrent-attempt evidence should cover bounded caller counts
and every admitted datastore provider profile.

## Deferred Scope

This proposal does not add:

- force overwrite;
- merge policy;
- conflict-resolution UI;
- repair commands;
- timestamp-based OCC;
- distributed consensus;
- leases or fencing tokens; or
- cross-provider atomic mutation.

Force, repair, merge, and conflict-resolution UX remain in the separate future
development item for Entity conflict resolution and repair.
