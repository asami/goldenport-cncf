# Entity Conflict and Conditional Transition

Status: normative design

## Purpose

This design defines the CNCF Entity concurrency boundary for version-aware
mutation and protected conditional transition.

The boundary provides:

- one framework-owned Entity concurrency token;
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
or public component CRUD. Ordinary generated mutation adapters may transport a
concurrency token, but they must enter the same UnitOfWork mutation boundary.

## Concurrency Model

### Entity concurrency token

`EntityConcurrencyToken` is an opaque framework value backed by a non-negative
`Long`.

The token has these semantics:

- equality is exact numeric equality;
- component business logic does not inspect or calculate the value;
- a successful admitted mutation advances the token exactly once;
- a failed, rejected, or mismatched mutation does not advance the token;
- the token is not derived from time, `updatedAt`, a JVM object version, or a
  resident Working Set value; and
- advancing `Long.MaxValue` fails structurally before mutation.

The canonical persisted initial token for a newly created Entity is `1`.

### Managed storage field

The logical framework field is `cncfRevision`. Its canonical datastore field
is:

```text
cncf_revision
```

`cncf_revision` is framework-managed Entity metadata.

- Entity create supplies the field.
- Entity mutation compares and advances the field.
- Application create records, patches, full records, import values, and
  transition expectations cannot write the field directly.
- Domain Entity codecs do not own the field.
- Ordinary business-data projections omit the field.
- Concurrency-aware persistence and transport projections may expose it as a
  token according to their contract.

The field is part of the canonical SimpleEntity storage-shape policy and is
reserved independently of application naming aliases.

### Legacy records

A stored Entity record without `cncf_revision` has virtual token `0`.

Virtual token `0` is used only for deterministic lazy admission of records
created before the managed field existed:

- a concurrency-aware load returns token `0`;
- the first version-aware mutation must carry expected token `0`;
- the provider predicate matches physical field absence for expected token
  `0`;
- a successful mutation persists token `1`; and
- competing first mutations still admit exactly one winner.

The runtime must not persist `0` for a newly created Entity. It must not
backfill a record through an unlocked read followed by an unconditional write.

### Snapshot carrier

The concurrency token is not added to every domain Entity type. A
concurrency-aware read returns:

```scala
final case class EntitySnapshot[A](
  entity: A,
  token: EntityConcurrencyToken
)
```

`EntitySnapshot[A]` is a framework persistence value. It is not an Aggregate,
View, or domain Entity.

Read-only operations may continue to return `A`. A path that forms an update
from a loaded Entity must use an `EntitySnapshot[A]` or another framework
value carrying the same admitted token.

### Mutation expectation

An ordinary version-aware mutation carries:

```scala
final case class EntityMutationExpectation(
  token: EntityConcurrencyToken
)
```

The following normal mutation families require an expected token:

- Entity full save;
- typed Entity update;
- patch update by Entity id;
- Aggregate-root mutation; and
- framework state-transition mutation.

A normal ActionCall/UnitOfWork update path must not perform an unversioned
overwrite. Create, seed import, and explicitly classified physical migration
are separate operations. A future force or repair operation must have an
explicit authorization and audit contract and must not be implemented by
omitting the token from an ordinary mutation.

Framework bootstrap, physical migration, and seed-import support may use
explicitly named unversioned operations. Those operations carry a closed
purpose value, require System admission at the UnitOfWork interpreter, and
are not exposed by the protected application Entity DSL. Stable identity
coordination uses claim-or-load rather than overwrite-style upsert.

## Ordinary Version-Aware Mutation

An ordinary version-aware mutation is one provider-owned compare-and-mutate
operation:

```text
verify stored token
  -> apply admitted changes
  -> apply framework-owned storage-shape side records
  -> persist next token
  -> return authoritative record and token
```

The provider comparison and update occur inside one native atomic boundary.
The interpreter must not:

1. load a token;
2. compare it in application memory; and
3. issue an unconditional save or update.

A token mismatch for ordinary mutation is a structured
`Consequence.Failure(Conclusion)` with conflict taxonomy. It is not a normal
`NotMatched` result.

Storage-shape preparation is pure until the provider admits the mutation.
Framework-owned side records, such as ContentBody overflow save/delete
effects, are closed values in the provider plan and commit in the same native
transaction as the root. A stale comparison therefore publishes neither a
root candidate nor a side-record candidate.

The failure may carry safe expected and actual token facets. It must not use
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
stored token == expected token
AND field-1 == expected value-1
AND field-2 == expected value-2
```

An empty field set is valid when the token alone is the complete guard.

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
framework loads an `EntitySnapshot` for that id, authorizes successor read and
relationship access against the snapshot, and normalizes the bind into a
provider value containing the successor id and expected successor token.

The provider verifies that expected successor token in the same transaction as
the root guard. A missing successor is not found. A changed successor token is
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
- supplies the next concurrency token; and
- normalizes the change record for the datastore provider.

The successor reference or ownership field is part of the admitted root
mutation. The provider does not infer application relationships.

### Result

The protected DSL returns:

```scala
sealed abstract class EntityConditionalTransitionResult[+R, +S]

object EntityConditionalTransitionResult {
  final case class Transitioned[R, S](
    root: EntitySnapshot[R],
    successor: EntitySnapshot[S]
  ) extends EntityConditionalTransitionResult[R, S]

  final case class NotMatched[R](
    existing: EntitySnapshot[R]
  ) extends EntityConditionalTransitionResult[R, Nothing]
}
```

`Transitioned` and `NotMatched` are normal successful `Consequence` values.

`NotMatched` means that the root existed but the authoritative token or at
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
when the token mismatch proves that the root changed.

Normal diagnostics may contain bounded logical operation, component,
collection, safe Entity id, outcome, and safe revision metadata. Payloads and
expected field values are not default diagnostic attributes.

An actual current token may be exposed only after read authorization against
the authoritative record. A denied or write-only caller receives no actual
token.

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
identities, expected/resulting token where policy permits, result category,
transaction correlation, and security subject according to the existing
redaction policy.

## Scope Boundary

The version-conflict baseline requires:

- normal Entity/Aggregate mutation paths use expected tokens;
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
