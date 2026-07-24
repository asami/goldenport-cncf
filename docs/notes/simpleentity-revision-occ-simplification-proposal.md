# SimpleEntity Revision and OCC Simplification Proposal

status = accepted implementation proposal, non-normative
date = 2026-07-24
target_phase = 50
related_phase = 49

## Position

This note records the accepted implementation proposal for a simpler canonical
concurrency model for `SimpleEntity` in Phase 50.

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

The accepted Scala-level type is a validated value type rather than a raw
numeric value:

```scala
final class EntityRevision private (val value: Long) extends NominalScalar
```

Construction is companion-validated. `EntityRevision` is deliberately not a
case class because generated `copy` / product construction would provide an
unchecked path around the positive-`Long` invariant. The companion exposes the
canonical initial value, structured construction and decoding, and
overflow-safe advancement.

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

`EntityRevision` is an Entity-model concept and therefore lives under the
`org.simplemodeling.model` datatype/model boundary:

```scala
org.simplemodeling.model.datatype.EntityRevision
```

It must not be placed in CNCF merely because CNCF enforces OCC.

`simplemodeling-lib` must not gain `SimpleEntity`, OCC, or CNCF semantics. It
is changed only if implementation proves that a genuinely reusable primitive
is missing, such as generic validated integral-value decoding or schema
support. Such a primitive must be independently useful outside Entity
revision. Phase 50 must not add a one-use core convenience API.

The SE-01 inventory found no Phase 50-specific generic gap:

- `ValueReader[Long]` already provides integral value decoding;
- existing positive-integer datatype/schema facilities provide the required
  validated-value basis; and
- existing structured `Consequence` and `Conclusion` facilities provide the
  required invalid-value and overflow failures.

SE-02 therefore must not modify `simplemodeling-lib` unless a failing
Executable Specification proves an independently reusable missing primitive.

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
Phase 49 API a compatibility contract. The accepted carrier uses
`EntityRevision`:

```scala
final case class EntityRevisionCarrier[A](
  entity: A,
  revision: EntityRevision
)
```

The detached representation uses the framework-managed physical field
`cncf_revision`. It remains outside the non-`SimpleEntity` domain codec.

### Representation Declaration

The generated Entity model metadata and CNCF `EntityRuntimeDescriptor` expose
one `revisionRepresentation` declaration using:

```scala
enum EntityRevisionRepresentation {
  case Embedded
  case Detached
}
```

The effective binding rules are:

- a type extending `SimpleEntity` is always `Embedded`;
- an explicit `Detached` declaration for a `SimpleEntity` fails component
  assembly;
- a type not extending `SimpleEntity` has no implicit revision representation;
- a non-`SimpleEntity` becomes revision-aware only through an explicit
  `Detached` declaration;
- matching Entity and collection declarations are accepted; and
- conflicting Entity and collection declarations fail component/subsystem
  assembly rather than using precedence.

Representation differs from concurrency policy: an explicit collection
concurrency policy may override an Entity policy, but a collection cannot
override the representation required by the Entity model.

## Managed Attribute Semantics

`revision` is a framework-managed attribute.

- CNCF assigns the initial revision when an Entity is created.
- CNCF advances the revision exactly once for each successful persistent
  mutation under `AlwaysWrite`, including an admitted write whose normalized
  business state is unchanged.
- Under `WriteIfChanged`, equal normalized business state is a successful
  no-op and does not advance revision, `updatedAt`, or mutation audit state.
- Reads do not advance the revision.
- Failed and rolled-back mutations do not advance the revision.
- Soft delete and restore advance the revision because they mutate persisted
  Entity state.
- Hard delete has no successor revision because the Entity no longer exists.
- Application code may read the revision.
- Normal application logic neither receives nor supplies revision as a
  business parameter.
- Application code must not set, patch, reset, decrement, or increment it.
- A create or mutation request that attempts to write `revision` fails
  deterministically as a managed-attribute violation.

The canonical initial revision is:

```text
1
```

A successful mutation from revision `n` persists revision `n + 1` in the same
atomic datastore operation as the business-state change.

The valid range is `1` through `Long.MaxValue`. A mutation that would advance
`Long.MaxValue` fails with a structured revision-exhaustion
`Consequence.Failure(Conclusion)` before any business state, `updatedAt`,
revision, or mutation audit state changes. Revision never wraps, saturates, or
returns to an earlier value.

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

## Concurrency, Write, and Precondition Policies

Entity concurrency, write behavior, and revision-precondition source are
independent decisions.

### Entity Concurrency Policy

The application selects whether ordinary mutations enforce revision comparison.
The policy is declarative and applies at Entity or collection scope.

Accepted model:

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

- Every admitted ordinary mutation reaches the datastore with an expected
  revision.
- CNCF normally obtains the expected revision from its managed Entity load and
  propagates it through UnitOfWork and EntityStore.
- CNCF compares it with the authoritative persisted `revision` in the
  datastore mutation.
- A mismatch changes no state and returns a structured conflict
  `Consequence.Failure(Conclusion)`.
- Normal application logic does not bypass or participate in the comparison by
  omitting a business parameter; no such parameter is part of its contract.
- Per-request opt-out is not supported.

`Optimistic` is the deterministic default. An explicit collection declaration
overrides an Entity declaration, which overrides the default. `None` remains an
explicit last-write-wins choice. The policy is a stable application design
decision, not a caller preference.

Generated Entity metadata and `EntityRuntimeDescriptor` use the
`concurrencyPolicy` field. The effective value is fixed during component
assembly and carried in `EntityRuntimePlan`; it is not resolved from request
properties at mutation time.

### Entity Write Policy

```scala
enum EntityWritePolicy {
  case AlwaysWrite
  case WriteIfChanged
}
```

`AlwaysWrite` is the core Entity mutation default:

- every admitted write reaches persistence;
- every successful write advances revision and `updatedAt`; and
- equal input is still an observed persistent mutation.

`WriteIfChanged` provides one-Entity state deduplication:

- CNCF applies directives, datatype normalization, defaults, and domain
  mutation before comparing state;
- equality compares canonical persisted business state;
- revision, lifecycle timestamps, audit fields, and other managed metadata do
  not participate in equality;
- equal state returns the current Entity and revision without a datastore
  write; and
- the no-op does not advance revision, `updatedAt`, or mutation audit state.

The no-op decision is made against authoritative state inside the provider's
atomic mutation boundary, not only against an earlier EntitySpace or UnitOfWork
copy. This gives concurrent identical requests a deterministic outcome: the
first request may write, while later requests that observe the same desired
business state return the authoritative Entity as a no-op success.

Generated Web/Form updates and REST routes whose protocol semantics are
idempotent select `WriteIfChanged`. Other operations may declare it explicitly.
General request replay, REST idempotency keys, external side effects, and
multi-resource idempotency are separate concerns.

### Revision Precondition Policy

```scala
enum RevisionPreconditionPolicy {
  case Managed
  case ObservedRequired
}
```

`Managed` is the normal application path. CNCF obtains the base revision from
the managed Entity load and keeps it with the mutation attempt. A retry or
replay of that same attempt must not silently reload and replace the base
revision after an ambiguous provider result. A deterministic stale result is
not ambiguous; the authoritative provider boundary may resolve it as a
`WriteIfChanged` no-op only when the desired normalized business state already
matches.

`ObservedRequired` is for strict edit routes. The ingress adapter carries the
revision observed by a user or client as framework metadata. Missing or stale
metadata fails structurally. The observed revision is checked before
`WriteIfChanged` equality so a stale strict edit is not hidden as a no-op.

Neither policy adds revision to the application's domain operation parameters.

### Effective Policy Binding

Concurrency, write, and precondition policy are separate metadata dimensions,
but not every combination is admitted.

| Concurrency | Precondition | Admission and comparison |
| --- | --- | --- |
| `Optimistic` | `Managed` | valid; compare CNCF-managed base revision after `WriteIfChanged` no-op detection |
| `Optimistic` | `ObservedRequired` | valid; require and compare observed revision before `WriteIfChanged` no-op detection |
| `None` | `Managed` | valid; perform no revision precondition comparison |
| `None` | `ObservedRequired` | invalid; fail operation/route assembly because a required observed revision contradicts last-write-wins |

`EntityWritePolicy` is valid with each admitted row. Its no-op guarantee is
independent of revision comparison: `None + Managed + WriteIfChanged` still
makes the authoritative state-equality/write decision atomically, but does not
reject a write because a previously loaded revision became stale.

The declaration and resolution surfaces are:

- `EntityConcurrencyPolicy` belongs to generated Entity metadata and
  `EntityRuntimeDescriptor`, with collection override over Entity declaration
  and then the `Optimistic` default;
- `EntityWritePolicy` and `RevisionPreconditionPolicy` belong to operation
  execution metadata and Web/REST route bindings;
- explicit route binding overrides an operation declaration;
- an operation declaration overrides the adapter profile default;
- the framework fallback is `AlwaysWrite + Managed`;
- generated Web/Form update adapters default to
  `WriteIfChanged + ObservedRequired`;
- idempotent REST update adapters default to `WriteIfChanged + Managed`;
- a strict REST validator binding selects `ObservedRequired`; and
- the effective values are bound into `EntityMutationExecutionPolicy` before
  ActionCall/UnitOfWork execution and are never decoded from business
  parameters.

Invalid combinations and unknown policy values fail deterministically while
assembling the operation/route runtime plan.

## Canonical Mutation Contract

The application-facing mutation contract becomes:

```text
entity id
  + admitted mutation
    -> updated SimpleEntity containing next revision
    | current SimpleEntity for WriteIfChanged no-op
    | structured revision conflict
    | another structured failure
```

The internal framework contract additionally carries:

```text
base EntityRevision
  + EntityConcurrencyPolicy
  + EntityWritePolicy
  + RevisionPreconditionPolicy
```

The datastore performs authoritative state comparison, optional revision
comparison, business mutation, lifecycle metadata update, and revision
advancement atomically. The ordering is:

```text
current = authoritative persisted Entity

if precondition == ObservedRequired
  require observed revision == current revision

if writePolicy == WriteIfChanged
   and desired business state == current business state
  return NoOp(current)

if concurrencyPolicy == Optimistic
  require managed base revision == current revision

require current revision < Long.MaxValue

persist desired business state,
        updatedAt,
        revision = current revision + 1
```

For `None + Managed`, the revision-precondition step is omitted while the
state-equality/write decision and revision advancement remain atomic. For
`Optimistic + Managed + WriteIfChanged`, equality is evaluated before the
managed base revision so a concurrent identical write becomes a no-op. For
`Optimistic + ObservedRequired`, the observed revision is checked first, so a
stale strict edit remains a conflict even when content is equal.

The exact provider operation need not be SQL, but it must provide this
equivalent atomic ordering. A load-check-save sequence outside the
authoritative provider boundary is not compliant.

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

Generated update forms retain the loaded revision as hidden framework metadata.
Strict REST routes use a transport validator. Generated clients expose the
framework metadata channel separately from business operation parameters.
None of these paths submit revision as an ordinary patch to `revision`.

SE-01 fixes transport ownership and semantics, not the wire spelling:

- generated Web/Form adapters own the hidden framework metadata;
- strict REST adapters own the request validator and response validator
  projection;
- generated clients keep revision in their framework metadata channel; and
- no adapter exposes revision as a domain operation parameter.

The exact hidden-field name and HTTP validator encoding are fixed with
Executable Specifications in SE-07, where they can be verified against the
implemented adapter contract. This is not an unresolved domain or persistence
decision.

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
- `EntityConcurrencyPolicy`; and
- `EntityWritePolicy`;
- `RevisionPreconditionPolicy`; and
- returned `SimpleEntity` values containing the next revision.

Expected revision remains an internal persistence precondition and optional
strict transport validator. It is not a normal application-domain operation
parameter.

The detached extension uses the same `EntityRevision` and an explicitly named
revision carrier for non-`SimpleEntity` values. It is a revised extension API,
not an alias or adapter for `EntitySnapshot[A](entity, token)`.

No compatibility aliases, duplicate storage fields, implicit representation
fallback, or legacy projection roots are introduced.

## Persistence and Migration

The canonical persisted field is:

```text
revision
```

The embedded `SimpleEntity` path replaces the provisional
`cncfRevision` / `cncf_revision` representation with the standard revision
shape. The explicit detached extension retains `cncf_revision` as its physical
framework-managed field because that field is intentionally outside the
non-`SimpleEntity` domain codec.

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

### Phase 49 Asset Disposition

| Disposition | Phase 49 asset | Phase 50 treatment |
| --- | --- | --- |
| common kernel reuse | provider compare-and-advance operation and exactly-one-winner datastore behavior | generalize around `EntityRevision` and use from both representations |
| common kernel reuse | in-memory, SQL/SQLite, transaction, rollback, and restart evidence | retain as the provider acceptance baseline |
| common kernel reuse | authorization, UnitOfWork, diagnostics, observability, and Working Set chokepoints | retain unchanged around the common revision kernel |
| detached-extension refactoring | `DataStoreRevisionState` and revision-field-independent mutation plan/result | rename and bind to `EntityRevision` without a public token model |
| detached-extension refactoring | storage stripping/pairing and EntityStore/UnitOfWork snapshot carrier paths | restrict to explicit non-`SimpleEntity` detached representation |
| provisional removal | `EntityConcurrencyToken` | remove; no alias or compatibility adapter |
| provisional removal | `EntityMutationExpectation` token contract | replace with framework-managed or observed revision precondition |
| provisional removal | `EntitySnapshot[A](entity, token)` public shape | replace with embedded `SimpleEntity.revision` or explicit `EntityRevisionCarrier[A]` |
| provisional removal | virtual revision zero, timestamp fallback, and legacy revision synthesis | reject missing revision deterministically |
| provisional removal | separate token projection roots and request parameters | remove from canonical projections and application operation contracts |

## Phase 50 Work

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
6. Add declarative `None` and `Optimistic` concurrency policies, with
   `Optimistic` as the default and CNCF-managed revision propagation.
7. Add `AlwaysWrite` and `WriteIfChanged` independently from managed and
   observed-required revision preconditions.
8. Integrate revision metadata into EntityStore, UnitOfWork, protected DSL,
   datastore, generated adapters, and Conditional Transition without adding a
   business operation parameter.
9. Project embedded revision through standard REST, Form, Web, View, and
   Aggregate surfaces; keep detached projection explicit.
10. Reject application writes, dual representations, and implicit fallback.
11. Add provider parity, simultaneous-update, rollback, restart, migration,
    and downstream evidence for both admitted representations.
12. Replace the Phase 49 provisional normative contract with the verified
    standard-plus-extension contract.

## Executable Specification Direction

Phase 50 should prove at least:

- every created persisted `SimpleEntity` receives revision 1;
- `AlwaysWrite` advances every successful persistent mutation exactly once,
  including equal normalized business state;
- `WriteIfChanged` skips equal normalized business state without changing
  revision, `updatedAt`, or mutation audit state;
- observed strict preconditions fail stale edits before no-op detection;
- normal application logic does not receive or supply revision;
- one mutation attempt preserves its CNCF-managed base revision;
- failed or rolled-back mutation does not advance revision;
- policy `None` records revisions without comparison;
- policy `Optimistic` defaults to CNCF-managed revision comparison;
- `ObservedRequired` rejects missing and stale transport revision;
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
