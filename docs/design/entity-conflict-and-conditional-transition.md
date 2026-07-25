# Entity Conflict and Conditional Transition

Status: normative design

## Purpose

This design defines the CNCF Entity concurrency boundary for version-aware
mutation and protected conditional transition.

The boundary provides:

- one framework-owned Entity revision;
- deterministic rejection of stale ordinary mutations;
- one closed conditional-transition model;
- provider-owned atomic mutation without a load-then-write fallback;
- typed normal results for admitted transition and mismatch; and
- the standard CNCF authorization, UnitOfWork, observability, cache, and View
  behavior around the mutation.

This design is the canonical concurrency baseline for Entity and Aggregate
root mutation. Explicit repair, force overwrite, merge workflow, and
conflict-resolution UI are outside this design.

## Runtime Boundary

```text
ActionCall / Behavior protected DSL
  -> UnitOfWork operation
     -> UnitOfWorkInterpreter authorization and lifecycle checks
        -> EntityStoreSpace
           -> EntityStore normalization
              -> DataStoreSpace capability resolution
                 -> atomic datastore provider operation
```

Component code does not receive a `DataStore`, provider capability object,
SQL/JDBC handle, provider transaction, or process-local lock.

Conditional transition is not projected automatically as REST, Form, CLI, MCP,
or public component CRUD. Ordinary generated mutation adapters may transport
revision metadata, but they must enter the same UnitOfWork mutation boundary.

## Concurrency Model

### Entity revision

`EntityRevision` is framework-managed Entity metadata backed by a positive
`Long` from `1` through `Long.MaxValue`.

The revision has these semantics:

- equality is exact numeric equality;
- component business logic does not inspect or calculate the value;
- a successful admitted mutation advances the revision exactly once;
- a failed, rejected, or mismatched mutation does not advance the revision;
- the revision is not derived from time, `updatedAt`, a JVM object version, or a
  resident Working Set value; and
- advancing `Long.MaxValue` fails structurally before mutation.

The canonical persisted initial revision for a newly created Entity is `1`.
There is no revision zero.

### Revision representation

Every revision-managed Entity collection resolves exactly one representation
at component assembly:

- `Embedded`: the standard `SimpleEntity` contract. The domain and persisted
  record contain the read-only managed field `revision`.
- `Detached`: an explicitly admitted extension for an Entity model that does
  not extend `SimpleEntity`. The domain codec remains revision-free and the
  persisted record contains the framework field `cncf_revision`.

An undeclared non-`SimpleEntity` collection remains unversioned. It does not
implicitly select Detached representation. `SimpleEntity + Detached`, dual
managed representation, mirroring, conflicting declarations, and per-request
representation selection fail during admission. A Detached domain model may
have an application-owned field named `revision`; that field remains ordinary
domain data and does not activate or mirror Embedded revision.

Both managed fields are framework-owned:

- Entity create supplies initial revision `1`.
- Entity mutation compares and advances the admitted field.
- Application create inputs, update inputs, patches, imports, and transition
  root mutations cannot write or clear it.
- Embedded codecs own the typed `SimpleEntity.revision` value but application
  mutation adapters omit it from business input.
- Detached codecs do not receive `cncf_revision`.

### Existing records and migration

A revision-managed record whose admitted physical revision is absent, invalid,
or exhausted fails structurally. CNCF does not synthesize a virtual revision,
derive one from `updatedAt`, or silently degrade to last-write-wins.

Existing records require explicit migration or recreation before admission to
a revision-managed collection. Migration is a separately classified System
operation; it is not an unlocked read followed by an ordinary overwrite.

### Runtime values

Embedded reads return the typed `SimpleEntity` value containing its
authoritative `revision`. Detached reads that participate in mutation return:

```scala
final case class EntityRevisionCarrier[A](
  entity: A,
  revision: EntityRevision
)
```

Conditional Transition closes over both representations with
`EntityConditionalTransitionValue.Embedded` or
`EntityConditionalTransitionValue.Detached`. Provider plans remain
representation-neutral and carry one physical revision field plus one
`EntityRevision`.

### Mutation policy and expected revision

The provider plan always carries the authoritative base revision for one
mutation attempt. Application domain logic normally does not supply it:

- `Managed` precondition lets CNCF load and retain the authoritative revision
  inside the protected Entity/UnitOfWork path.
- `ObservedRequired` requires transport or explicit framework metadata to
  match the authoritative revision before mutation.
- `Optimistic` concurrency compares the base revision atomically.
- explicit `None` concurrency still maintains revision but does not reject a
  candidate merely because its framework-managed base changed.

`AlwaysWrite` is the core default. `WriteIfChanged` is an adapter/operation
policy that performs authoritative normalized business-state comparison inside
the provider boundary. Its no-op success does not write, advance revision,
change `updatedAt`, or emit mutation audit state. A stale observed precondition
wins over equality.

Generated Web Form adapters use `WriteIfChanged + ObservedRequired`. Idempotent
REST PUT uses `WriteIfChanged + Managed`; a strong `If-Match:
"revision-N"` selects `ObservedRequired`. Revision transport is framework
metadata, not a business operation parameter. Create never requires an
observed revision.

Read, search, View, and Aggregate projection expose Embedded `revision` as a
read-only field. Detached domain records remain revision-free; an explicitly
revision-aware response may expose Detached revision as `version` metadata.
JSON, YAML, and XML preserve the same projected Embedded field. REST response
validators use the strong `"revision-N"` form only when an admitted Embedded
revision or explicit Detached version is present.

A normal ActionCall/UnitOfWork update path must not perform an unversioned
overwrite. Create, seed import, and explicitly classified physical migration
are separate operations. A future force or repair operation must have an
explicit authorization and audit contract and must not be implemented by
omitting managed revision semantics from an ordinary mutation.

Framework bootstrap, physical migration, and seed-import support may use
explicitly named unversioned operations. Those operations carry a closed
purpose value, require System admission at the UnitOfWork interpreter, and
are not exposed by the protected application Entity DSL. Stable identity
coordination uses claim-or-load rather than overwrite-style upsert.

## Ordinary Version-Aware Mutation

An ordinary version-aware mutation is one provider-owned compare-and-mutate
operation:

```text
verify stored revision
  -> apply admitted changes
  -> apply framework-owned storage-shape side records
  -> persist next revision
  -> return authoritative record and revision
```

The provider comparison and update occur inside one native atomic boundary.
The interpreter must not:

1. load a revision;
2. compare it in application memory; and
3. issue an unconditional save or update.

A revision mismatch for ordinary mutation is a structured
`Consequence.Failure(Conclusion)` with conflict taxonomy. It is not a normal
`NotMatched` result.

Storage-shape preparation is pure until the provider admits the mutation.
Framework-owned side records, such as ContentBody overflow save/delete
effects, are closed values in the provider plan and commit in the same native
transaction as the root. A stale comparison therefore publishes neither a
root candidate nor a side-record candidate.

The failure may carry safe expected and actual revision facets. It must not use
application-owned `Status.detailCodes`, parse display text, or include Entity
payload values.

## Conditional Transition Model

### Transition definition

An `EntityTransitionDefinition[R]` is framework-admitted metadata for one root
Entity type. It owns the closed set of fields that may participate in a
conditional expectation.

Each admitted `EntityTransitionField[R, A]` contains:

- a stable logical field identity;
- its canonical datastore field identity;
- a typed extractor or codec owned by the persistence definition; and
- a conversion into the closed exact-value algebra.

Component code constructs an expectation from admitted typed field values. It
does not submit raw datastore column names, query paths, SQL identifiers, or
expression text.

### Exact-value algebra

The first conditional-transition value algebra supports exact comparison of:

- text;
- boolean;
- integral number;
- canonical decimal;
- instant;
- admitted identifier; and
- admitted Entity id.

Collections, records, binary values, floating-point approximations, arbitrary
objects, null, missing-field predicates, ranges, regular expressions,
functions, scripts, SQL, and general query expressions are not admitted.

Expected fields are unique. The expectation is conjunction-only:

```text
stored revision == expected revision
AND field-1 == expected value-1
AND field-2 == expected value-2
```

An empty field set is valid when the revision alone is the complete guard.

### Successor intent

The successor intent is closed:

```scala
sealed abstract class EntitySuccessorIntent

object EntitySuccessorIntent {
  final case class Create[C](candidate: C) extends EntitySuccessorIntent
  final case class Bind(id: EntityId) extends EntitySuccessorIntent
}
```

`Create` creates exactly one new successor inside the same provider
transaction. A duplicate id or identity is a structured conflict and does not
overwrite the existing Entity.

`Bind` verifies an existing admitted successor and allows the root mutation to
reference it. It does not mutate the successor.

`Bind(id)` is the component-facing intent. Before plan submission, the
framework loads the authoritative Embedded snapshot or Detached carrier for
that id, authorizes successor read and relationship access against the
authoritative record, and normalizes the bind into a provider value containing
the successor id and expected successor revision.

The provider verifies that expected successor revision in the same transaction as
the root guard. A missing successor is not found. A changed successor revision is
a structured bound-successor conflict with no successor payload. Neither
result mutates the root.

The root and successor collections must belong to one component, one selected
datastore provider instance, and one provider transaction domain.

### Root mutation

The root mutation uses an admitted typed patch or persistent update codec.

The framework:

- converts the patch through the normal update semantics;
- removes or rejects framework-managed fields;
- applies lifecycle and storage-shape policy;
- ensures the effective mutation is non-empty;
- supplies the next revision; and
- normalizes the change record for the datastore provider.

The successor reference or ownership field is part of the admitted root
mutation. The provider does not infer application relationships.

### Result

The protected DSL returns:

```scala
sealed abstract class EntityConditionalTransitionResult[+R, +S]

object EntityConditionalTransitionResult {
  final case class Transitioned[R, S](
    root: EntityConditionalTransitionValue[R],
    successor: EntityConditionalTransitionValue[S]
  ) extends EntityConditionalTransitionResult[R, S]

  final case class NotMatched[R](
    existing: EntityConditionalTransitionValue[R]
  ) extends EntityConditionalTransitionResult[R, Nothing]
}
```

`Transitioned` and `NotMatched` are normal successful `Consequence` values.

`NotMatched` means that the root existed but the authoritative revision or at
least one admitted expected value differed. It is not used for not-found,
authorization denial, malformed intent, unsupported provider, transaction
failure, or conversion failure.

Returning `NotMatched(existing)` requires read authorization for the
authoritative root. A caller that cannot read the root receives an
authorization failure without the existing payload.

## Atomic Datastore Capability

### Supplementary provider contract

Atomic Entity mutation is a supplementary datastore capability. The ordinary
`DataStore` CRUD contract does not provide a default implementation.

Providers that support the capability implement a dedicated provider-neutral
port for:

- single-record versioned mutation; and
- compound conditional transition.

`DataStoreSpace` reports the capability as `Supported` or `Unsupported` and
invokes the supplementary port only when supported. Absence of the port is
`Unsupported`; it is never interpreted as permission to emulate atomicity.

Protected DSL use is dynamic and is not declared by every component
descriptor. The runtime does not reject every unsupported datastore at
subsystem startup. Capability resolution occurs before the first datastore
effect of each admitted operation. A future explicit component requirement
may add startup preflight without changing this operation-time contract.

### Normalized provider plan

The provider plan contains only bounded record-level values:

- explicit framework-owned component ownership for the root and successor;
- root collection and entry id;
- expected physical revision state;
- unique admitted exact-match fields;
- normalized root changes;
- next revision;
- successor create record or bound successor id plus expected successor
  revision state;
- bounded framework-owned side-record save/delete effects required by the
  canonical Entity storage shape; and
- bounded logical correlation metadata.

The admitted record plan is closed and bounded:

- at most 32 exact expected fields;
- at most 64 framework-owned side-record effects;
- at most 128 characters per canonical field identity;
- at most 4096 characters per canonical encoded text, identifier, or Entity-id
  expectation value;
- at most 256 characters per correlation value;
- at most 256 fields in each provider-bound record;
- at most 1024 values in each ordered provider-bound sequence; and
- at most 16 nested record/sequence levels.

Sequence admission materializes only a bounded prefix of at most 1025 values.
The extra value distinguishes an admitted sequence from an over-limit one
without traversing the complete input, so lazy or unbounded sequences fail
deterministically rather than stalling plan construction.

Names and correlation values are non-blank and contain no control characters.
The expected root revision is mandatory. Root changes are a non-empty patch and
cannot contain the managed revision field. Exact expected fields cannot contain
the managed revision field. The guarded root, successor, and side-record
primary targets are pairwise distinct, and side-record targets are unique.

Provider-bound root, successor, and side-effect records admit only normalized
storage scalars, `Instant`, nested `Record`, and ordered `Seq` values. Raw null,
unordered collections, arbitrary objects, domain values, callbacks, and
non-canonical `SetNull` markers are rejected before datastore execution.
`SetNull` is admitted only as the canonical root-patch clear marker.

`Create` carries the normalized successor record with its canonical initial
revision. `Bind` carries the successor identity, managed revision field, and
the revision observed by the upper Entity boundary.

The provider plan does not contain:

- domain objects or typeclasses;
- authorization policy;
- a caller transaction;
- SQL or provider expressions;
- EntitySpace or Working Set values; or
- arbitrary callbacks.

The plan is rejected before provider mutation when collections resolve to
different components, providers, or transaction domains.
The guarded root and every framework-owned side record must resolve to the
same datastore provider instance and native transaction domain.

The root and successor collections are Entity collections and carry equal
`DataStoreComponentOwner` values supplied by the trusted Entity normalization
boundary. This ownership value is not derived from `EntityId.major`,
`EntityId.minor`, collection naming, or another parsed identifier. The root,
successor, and every side-record collection are resolved before provider
invocation. In this capability, one `DataStore` instance is the
transaction-domain owner. An internally sharded provider either rejects a plan
spanning native transaction domains or exposes those domains as distinct
datastore instances.

### Provider transaction

Conditional transition executes as one native provider transaction:

```text
begin
  -> read and lock, or conditionally claim, the authoritative root
  -> compare physical revision state
  -> compare admitted exact values
  -> create successor, or verify bound successor identity and revision
  -> mutate root and persist next revision
  -> apply framework-owned storage-shape side records
  -> read authoritative result records
commit
```

Ordinary versioned mutation uses the same provider-owned transaction boundary:

```text
begin
  -> read and lock the authoritative root
  -> validate the admitted physical revision
  -> apply observed-precondition, WriteIfChanged, and concurrency ordering
  -> mutate root and persist next revision, or return authoritative NoOp
  -> apply framework-owned storage-shape side records
  -> read the authoritative result
commit
```

In-memory, SQLite, and the selected shared SQL provider implement this
supplementary capability directly. `EntityStore` and `DataStoreSpace` do not
emulate it with load-check-save.

Failure before the commit attempt causes rollback and leaves neither successor
nor side-record/root mutation externally visible. A provider-reported commit
rejection has the same result. A transport or provider failure that makes
commit outcome indeterminate returns a structured transaction-indeterminate
failure; the caller must not retry automatically and must resolve the
authoritative root through a new read. Native atomicity still requires root,
successor, and framework-owned side records to share one committed or rolled
back outcome.

The provider reports `Transitioned` only after commit success is acknowledged.

Domain values and proposed normalized records are validated before provider
execution. Provider-level SQL/result-set conversion to `Record` occurs before
commit and participates in rollback. Typed Entity hydration of an
authoritative returned `Record` occurs above the provider after commit. An
unexpected post-commit hydration failure is a structured
committed-projection failure: the mutation remains committed, resident state
is evicted, automatic retry is prohibited, and diagnostics identify the
committed outcome without exposing payload.

In-memory behavior uses one datastore-owned atomic boundary and publishes the
new immutable state only after every step succeeds. It is the deterministic
semantic reference, not a component-level locking pattern.

SQLite uses one connection and one explicit native transaction per operation.
Concurrency evidence uses independent callers and independent connections.

The initial shared-datastore acceptance profile is MySQL through the existing
JDBC and `SqlDataStore.Mysql` provider boundary. Shared-profile evidence uses
independently executing callers against one physical database and the same
provider-neutral result contract.

## UnitOfWork Integration

The UnitOfWork algebra has explicit operations for versioned mutation and
conditional transition. These operations carry:

- typed request and persistence evidence;
- root authorization metadata;
- successor authorization metadata;
- transition validation metadata; and
- no datastore/provider transaction object.

The interpreter order is:

1. canonicalize root and successor Entity identities;
2. authorize root read and update;
3. authorize successor create or bind/read and relationship access;
4. run applicable lifecycle and transition-validation hooks;
5. normalize one datastore plan;
6. resolve the atomic provider capability;
7. execute the provider operation;
8. reauthorize every authoritative record that may be returned after a
   mismatch;
9. reconcile EntitySpace and Working Set from the authorized authoritative
   result;
10. invalidate Views only for a committed mutation; and
11. emit bounded audit, CallTree, and metric evidence.

An authorization load used to evaluate object policy is not the concurrency
check. The provider always performs the authoritative comparison inside its
atomic operation.

The protected ActionCall/Behavior DSL returns an `ExecUowM` program. An
internal variant may use `ServiceInternal` only for admitted same-service
workflow work; it does not bypass collection, Entity, successor, relationship,
or cross-component authorization.

## Cache and View Coherence

No EntitySpace, Working Set, or View mutation occurs before provider success.

For `Transitioned`:

- stale root and successor entries are evicted first;
- authoritative returned values are installed only when their Working Set
  policies admit them; and
- the component-local `ViewSpace` is invalidated after commit.

For `NotMatched`:

- the caller is not reported as having performed a mutation;
- Views are not invalidated as a mutation result; and
- the authoritative returned root is reauthorized before it is exposed or
  installed;
- an authorized stale resident root is evicted or replaced from the
  authoritative record; and
- a post-result authorization denial returns no root payload and evicts any
  stale resident value that the caller must no longer observe.

Committed mutations invalidate the component-local
`ViewSpace` through `invalidateAll()`. Exact Entity-to-View dependency
targeting and cross-process or cluster-wide cache invalidation are outside this
contract.

## Authorization and Disclosure

The operation requires:

- root read and update authorization;
- successor collection create authorization for `Create`; or
- successor read plus relationship authorization for `Bind`.

Authorization denial precedes unauthorized mutation. A failure must not expose
root data, successor data, expected values, confidential fields, or provider
details.

If a provider returns an authoritative root for `NotMatched`, the interpreter
must evaluate read authorization again against that returned record before
returning or caching it. The pre-provider authorization result is insufficient
when the revision mismatch proves that the root changed.

Normal diagnostics may contain bounded logical operation, component,
collection, safe Entity id, outcome, and safe revision metadata. Payloads and
expected field values are not default diagnostic attributes.

An actual current revision may be exposed only after read authorization against
the authoritative record. A denied or write-only caller receives no actual
revision.

## Failure Semantics

Framework and provider failures remain normal
`Consequence.Failure(Conclusion)` values.

Required classifications include:

- stale ordinary mutation: conflict;
- missing root or bound successor: not found;
- denied access: authorization;
- malformed or inadmissible expectation: argument/policy failure;
- unsupported provider capability: unsupported operation/capability;
- successor collision: conflict;
- provider conversion or I/O failure: datastore failure; and
- native transaction failure: transaction/datastore failure;
- indeterminate commit acknowledgment: transaction-indeterminate failure; and
- post-commit typed hydration failure: committed-projection failure.

Metrics and audit classification use typed results and structured
`Conclusion` data. They do not parse `Conclusion.display` and do not use
application `Status.detailCodes`.

## Observability and Audit

The expected CallTree shape is:

```text
action:<operation>
  -> uow:entitystore:<versioned-mutation|conditional-transition>
     -> space:entitystore:<versioned-mutation|conditional-transition>
        -> space:datastore:<versioned-mutation|conditional-transition>
```

Bounded outcomes include:

- `transitioned`;
- `not-matched`;
- `conflict`;
- `unauthorized`;
- `unsupported-capability`;
- `provider-failure`; and
- `transaction-failure`.

Audit records identify the admitted logical operation, component, safe Entity
identities, expected/resulting revision where policy permits, result category,
transaction correlation, and security subject according to the existing
redaction policy.

## Scope Boundary

The version-conflict baseline requires:

- normal Entity/Aggregate mutation paths resolve one authoritative base
  revision per attempt;
- stale mutation is rejected atomically;
- Working Set state cannot bypass the datastore check; and
- provider and concurrency evidence passes.

The protected conditional-transition capability requires implementation
through the same boundary and downstream acceptance.

Force/repair commands, merge workflows, overwrite policy, and
conflict-resolution UI remain a separate future capability.

## EC-05 Runtime Binding

The typed framework binding is implemented through:

- `EntityTransitionField` and `EntityTransitionDefinition`, which admit
  logical fields and derive their canonical storage names only through
  `EntityPersistent.storeFieldName`;
- `EntityConditionalTransition`, which carries one root expectation, one
  generated update patch, and one typed `Create` or `Bind` successor intent;
- one private CNCF `EntityStoreConditionalTransition` UnitOfWork operation;
- `EntityStoreSpace` and `EntityStore`, which normalize the typed request into
  the closed provider plan and never fall back to ordinary CRUD; and
- protected `entity_conditional_transition` and
  `entity_conditional_transition_internal` ActionCall helpers.

The component owner is the executing ActionCall component. It is never
inferred from an Entity id. The internal helper changes the Entity access mode
to `ServiceInternal` but preserves root and successor authorization,
UnitOfWork, transition-validation hook, EntityStore, and datastore capability
boundaries.

The executing component becomes trusted ownership evidence only after both
canonical root and successor collections resolve in that component's
registered `EntitySpace`. An unregistered collection is rejected at the
ActionCall boundary before UnitOfWork construction. This prevents a caller
from labeling an arbitrary collection with the executing component owner and
keeps the same admission rule for user and `ServiceInternal` execution.

A create successor intent closes its target identity at construction. It
retains the codec-declared collection and optional candidate Entity id,
rejects an id whose collection differs, and never reevaluates either value
during ActionCall or provider-plan preparation. Missing ids are generated
inside the retained collection.

Provider-plan records are closed at the EntityStore boundary. Entity ids,
identifiers, generated nominal scalar values, and framework state-machine
values are converted to datastore scalars before the plan crosses into
`DataStoreSpace`.

The root provider mutation is a normalized delta, not a replacement record.
Empty patches, ineffective patches, logically deleted roots, and attempts to
write any framework-managed field are rejected before provider invocation.
Framework-owned lifecycle/audit changes may be added to the delta, but
unchanged domain and storage fields are not resubmitted.

The UnitOfWork interpreter evicts a stale resident root before authorizing an
authoritative `NotMatched` record. A denial therefore cannot leave the stale
resident root installed and cannot expose the authoritative payload.

## Authoritative Runtime Path

The version-conflict baseline and conditional-transition capability share one
authoritative runtime path:

```text
ActionCall protected DSL
  -> UnitOfWork
  -> EntityStoreSpace
  -> EntityStore
  -> DataStoreSpace
  -> provider atomic capability
```

Ordinary version-aware save, typed update, patch-by-id, Aggregate update, and
framework-owned state transitions resolve one base revision for the mutation
attempt. The provider compares the expected revision against persisted metadata
inside its atomic boundary. A stale candidate changes neither the root nor
framework side records, and resident state cannot substitute for that provider
check.

The protected conditional-transition DSL admits only collections registered
to the executing component. Server-owned workflows obtain the authoritative
root revision through the protected `ServiceInternal` snapshot loader rather than
through System access or direct storage. The same authorization,
transition-validation, UnitOfWork, EntityStore, and datastore-capability
boundaries therefore remain active.

Authoritative outcomes drive coherence and diagnostics:

- `Transitioned` installs the committed root and successor and invalidates
  affected Views only after provider success;
- `NotMatched` reconciles or evicts stale resident state before returning an
  authorized authoritative snapshot;
- stale conflict, authorization denial, unsupported capability, provider
  failure, and transaction failure remain structured
  `Consequence.Failure(Conclusion)` values; and
- CallTree, runtime metrics, and audit use typed outcomes and structured
  diagnostics without recording payload or parsing display text.
