# Entity Conflict and Conditional Transition Implementation Proposal

status = proposed, non-normative
date = 2026-07-24
phase = 49
strategy_items = 9.12, 9.39

## Position

This note proposes the implementation shape for Phase 49, `Entity Conflict and
Conditional Transition`.

It is non-normative. It does not change current runtime behavior. Accepted
semantics must be promoted to `docs/design` and `docs/spec`, then fixed by
Executable Specifications before implementation.

The consideration record is:

- `docs/journal/2026/07/2026-07-24-phase-49-entity-conflict-conditional-transition-consideration.md`

The originating handoff is:

- `docs/journal/2026/07/entity-internal-dsl-conditional-transition-handoff-2026-07-23.md`

## Implementation Goals

- Give every version-aware Entity mutation a framework-owned store-backed
  concurrency token.
- Detect stale writes in the datastore operation, not from a resident cache.
- Add one protected typed DSL operation for an atomic conditional transition.
- Create or bind a successor and update its root reference in one provider
  transaction.
- Return one normal winner/loser result model without converting expected
  contention into an error.
- Preserve the ActionCall, authorization, UnitOfWork, EntityStore,
  observability, audit, lifecycle, cache, and datastore ownership boundaries.
- Prove the same one-winner semantics with in-memory, SQLite, and a shared
  datastore profile.

## Non-Goals

- Public generic conditional CRUD.
- Arbitrary expression or query predicates.
- Domain-specific Review Run vocabulary in CNCF.
- Cross-component or cross-provider transactions.
- Distributed consensus, leases, fencing tokens, or multi-region ownership.
- Conflict merge UI or application repair policy.
- Best-effort fallback when a datastore lacks atomic mutation support.

These deferred features do not keep 9.12 open after Phase 49. The completed
9.12 baseline is the concurrency-token, expected-token, stale-conflict, and
ordinary-overwrite-prevention contract implemented by this phase. A later
force/repair API, merge workflow, or conflict-resolution UI is tracked as
`9.40 Entity Conflict Resolution and Repair`.

## Existing Runtime Grounding

The current Entity effect path is:

```text
ActionCall / Behavior protected DSL
  -> ExecUowM
  -> UnitOfWorkOp
  -> UnitOfWorkInterpreter
  -> EntityStoreSpace
  -> EntityStore
  -> DataStoreSpace
  -> admitted DataStore
```

The current runtime already provides:

- protected `entity_claim_or_load`;
- `EntityStoreClaimOrLoad` in the UnitOfWork algebra;
- authorization metadata on Entity UnitOfWork operations;
- transition validation hooks;
- EntitySpace/Working Set update and eviction hooks;
- View invalidation;
- UnitOfWork and EntityStore CallTree boundaries; and
- datastore duplicate and not-found failures.

The missing parts are:

- canonical Entity concurrency metadata;
- expected-token mutation;
- a provider-neutral atomic compound-mutation capability;
- an Entity conditional-transition operation; and
- provider concurrency evidence.

The current SQL datastore opens one connection per operation. Its
`CommitParticipant` methods record lifecycle but do not keep one JDBC
transaction open across several ordinary DataStore calls. Therefore Phase 49
must not try to implement the transition by composing existing `load`,
`create`, and `update` calls.

## Proposed Model Layers

The proposal separates four representations:

1. domain values used by component code;
2. typed Entity transition intent used by the protected DSL;
3. normalized EntityStore transition plan;
4. record-level atomic datastore mutation plan.

The lower layer must not leak into the higher layer.

### Concurrency token

Proposed public framework value:

```scala
final case class EntityConcurrencyToken(value: Long)
```

The exact representation remains subject to normative review. Required
properties are:

- opaque to component business logic;
- comparable for exact equality;
- monotonically advanced by successful framework mutation;
- persisted as framework-managed Entity metadata;
- returned by Entity persistence/projection APIs only where concurrency control
  is required; and
- never generated from `updatedAt`, JVM time, or a resident object version.

The storage field should be a reserved framework-managed field. A provisional
name is `cncfRevision`. The final name must be aligned with the SimpleEntity
storage-shape policy before implementation.

New records begin with one canonical initial token. Every successful
version-aware save, update, state transition, and conditional transition
advances it exactly once.

### Expected version

Ordinary mutation carries an optional expected token:

```scala
final case class EntityMutationExpectation(
  token: EntityConcurrencyToken
)
```

An absent token preserves explicitly selected legacy/unversioned behavior
during migration. Phase 49 should define which protected and generated paths
require a token. It must not silently infer the expected token from the current
Working Set after the caller has formed its mutation.

### Restricted transition expectation

The conditional-transition expectation is closed and equality-based:

```scala
final case class EntityTransitionExpectation(
  token: EntityConcurrencyToken,
  values: Vector[EntityExpectedValue]
)

final case class EntityExpectedValue(
  field: EntityTransitionField,
  value: EntityTransitionValue
)
```

`EntityTransitionField` and `EntityTransitionValue` are validated framework
types, not raw SQL names or expressions. The field set is produced through
Entity persistence metadata or a component-provided typed transition
definition admitted during CAR construction.

The first implementation supports conjunction only:

```text
revision == expected revision
AND field-1 == expected value-1
AND field-2 == expected value-2
```

It does not support OR, ranges, scripts, functions, nested query expressions,
or caller-provided SQL.

### Successor action

The successor part uses a closed model:

```scala
sealed abstract class EntitySuccessorIntent

object EntitySuccessorIntent {
  final case class Create[C](candidate: C) extends EntitySuccessorIntent
  final case class Bind(id: EntityId) extends EntitySuccessorIntent
}
```

`Create` means that the candidate must be created inside the same atomic
transaction. A duplicate candidate is not silently overwritten.

`Bind` means that an existing admitted successor is referenced. The framework
must authorize and validate the referenced Entity before constructing the
atomic datastore plan.

The exact generic shape may change to preserve Scala type safety. The closed
semantic distinction must remain.

### Root mutation

The root mutation should reuse typed update semantics:

```scala
final case class EntityTransitionMutation[P](
  patch: P,
  persistent: EntityPersistentUpdate[P]
)
```

The patch is normalized through `Update.toChangesRecord`, managed fields are
controlled by EntityStore, and the concurrency token is advanced by the
framework. Component code cannot set the managed token directly.

### Entity-level request and result

Proposed conceptual request:

```scala
final case class EntityConditionalTransition[R, P, C, S](
  rootId: EntityId,
  expectation: EntityTransitionExpectation,
  rootPatch: P,
  successor: EntitySuccessorIntent,
  rootPersistent: EntityPersistent[R],
  patchPersistent: EntityPersistentUpdate[P],
  successorCreate: Option[EntityPersistentCreate[C]],
  successorPersistent: EntityPersistent[S]
)
```

This is illustrative. The implementation should minimize stored typeclass
parameters where Scala inference can preserve them safely.

Proposed result:

```scala
sealed abstract class EntityConditionalTransitionResult[+R, +S]

object EntityConditionalTransitionResult {
  final case class Transitioned[R, S](
    root: R,
    successor: S,
    token: EntityConcurrencyToken
  ) extends EntityConditionalTransitionResult[R, S]

  final case class NotMatched[R](
    existing: R,
    token: EntityConcurrencyToken
  ) extends EntityConditionalTransitionResult[R, Nothing]
}
```

`NotMatched` is a successful `Consequence` containing a normal typed outcome.
Missing root, malformed expectation, unauthorized read/update/create/bind, and
provider failure are not `NotMatched`.

## Datastore Atomic Capability

### Why a new capability is required

Ordinary DataStore operations cannot safely implement:

```text
load root
  -> compare root
  -> create successor
  -> update root
```

Composing those calls can use different SQL connections and different
transactions. Phase 49 therefore needs one provider call that owns the complete
atomic sequence.

### Capability shape

Use a supplementary datastore capability rather than adding an unsafe default
implementation to every `DataStore`:

```scala
trait ConditionalMutationDataStore { self: DataStore =>
  def conditionalTransition(
    plan: DataStoreConditionalTransition
  )(using ExecutionContext): Consequence[DataStoreConditionalTransitionResult]
}
```

The final name is subject to the repository type-modeling rules. The important
contract is:

- a provider either implements the atomic capability or reports it as
  unsupported;
- there is no load-then-save fallback;
- all affected collections must resolve to the same admitted provider and
  transaction domain;
- the provider returns authoritative stored records; and
- provider exceptions are normalized to structured datastore failures.

### Normalized plan

The datastore plan is record-based and bounded:

```scala
final case class DataStoreConditionalTransition(
  root: DataStoreGuardedUpdate,
  successor: DataStoreSuccessorMutation
)
```

Conceptual members:

- root collection and entry id;
- expected revision;
- bounded exact-match fields;
- normalized root changes;
- successor create or bind action;
- next revision; and
- framework correlation metadata needed for safe diagnostics, not payload
  logging.

The plan must reject:

- collections owned by different components;
- collections resolved to different datastore providers;
- mutation of reserved fields by the caller;
- duplicate expected fields;
- unsupported value encodings;
- missing expected revision; and
- empty effective root mutation.

### Provider result

The provider returns one of:

```scala
Transitioned(rootRecord, successorRecord)
NotMatched(existingRootRecord)
```

The provider must read the authoritative root inside the transaction. It must
not use an EntitySpace or caller-provided existing record as the comparison
authority.

### Transaction ordering

A valid provider implementation may use row locking or compare-and-update, but
must provide this semantic result:

```text
begin native transaction
  -> read/lock or conditionally claim root revision
  -> verify exact expected values
  -> create or verify successor
  -> update root and advance revision
  -> commit
```

If root verification fails, no successor is created.

If successor creation or validation fails, the root update is rolled back.

If commit fails, neither mutation is reported as `Transitioned`.

### In-memory reference provider

The in-memory provider should synchronize on one datastore-owned mutation
boundary covering both collections. It is reference behavior for deterministic
specs, not justification for process-local locking in application code.

### SQLite provider

SQLite implementation should use one connection and one explicit native
transaction. The exact dialect strategy may use an immediate write transaction,
a guarded update with affected-row count, or an equivalent serialization
mechanism.

Executable evidence must use independent callers and connections. A test that
serializes calls in one thread is insufficient.

### Shared datastore provider

At least one provider profile representing separately executing callers over a
shared physical datastore must prove the same result. The provider may use row
locking, guarded update, or another native primitive, but must expose the same
CNCF result model.

Provider-specific SQL belongs in the provider implementation and its tests,
not in EntityStore or component code.

## EntityStore and UnitOfWork Integration

### EntityStore

Add a typed EntityStore operation that:

1. converts the expected values with admitted persistence metadata;
2. prepares create/bind successor records;
3. applies SimpleEntity storage-shape and content-body policies;
4. builds the normalized datastore plan;
5. invokes the atomic provider capability;
6. hydrates and converts authoritative result records; and
7. preserves structured failures.

EntityStore must not implement a fallback from `conditionalTransition` to
ordinary `load`, `create`, and `update`.

### UnitOfWork algebra

Add one explicit operation:

```scala
UnitOfWorkOp.EntityStoreConditionalTransition[...]
```

It carries:

- typed request and persistence evidence;
- root read/update authorization;
- successor create or bind/read authorization; and
- no provider or transaction object.

The operation is explicit because it affects authorization, persistence,
transaction behavior, cache coherence, View invalidation, audit, and replay
semantics.

### Interpreter sequence

The interpreter should:

1. canonicalize Entity ids;
2. authorize root read and update;
3. authorize successor create or bind/read;
4. invoke transition validation hooks with current/proposed semantics where
   applicable;
5. execute one EntityStore conditional transition;
6. on `Transitioned`, update or evict affected EntitySpace entries and
   invalidate affected Views after datastore success;
7. on `NotMatched`, evict or refresh the stale root entry from the authoritative
   returned record without reporting a mutation by this caller; and
8. emit a bounded result classification.

No cache mutation or View invalidation caused by this caller occurs before the
provider reports success.

### Protected ActionCall/Behavior DSL

Add protected methods conceptually equivalent to:

```scala
entity_conditional_transition(...)
entity_conditional_transition_internal(...)
```

The ordinary variant uses normal user-facing authorization metadata.

The internal variant uses `ServiceInternal` only for server-owned workflow
state derived from already admitted input. It does not bypass Entity,
collection, successor, or component-scope authorization policy.

The DSL returns `ExecUowM[EntityConditionalTransitionResult[...]]`.

It must not expose the operation automatically as REST, Form, CLI, or MCP CRUD.

## Version-aware Ordinary Mutation

Phase 49 should add the minimum generic conflict foundation rather than only a
transition-specific revision.

The following paths should have an expected-token form:

- Entity save;
- Entity typed update;
- Entity patch update by id;
- Aggregate root update; and
- framework state-transition update.

The initial migration may preserve explicitly unversioned overloads, but new
generated/internal paths should carry an expected token when updating a loaded
Entity.

The datastore comparison must include the expected token in the same native
update operation. Loading a token, comparing it in the interpreter, and then
issuing an unconditional save is invalid.

A stale ordinary mutation returns a structured conflict `Conclusion`. It should
use existing conflict taxonomy and structured expected/actual facets where
available. It must not use application-owned `Status.detailCodes`.

## Authorization and Information Safety

- Root update authorization is checked against the authoritative Entity
  identity and current authorization record.
- Returning `NotMatched(existing)` requires authorization to read the existing
  root.
- Successor `Create` requires create admission for its collection.
- Successor `Bind` requires read admission and any relationship policy required
  by the root mutation.
- Diagnostic attributes contain ids, collections, operation, outcome, and
  revision metadata only when those values are already safe.
- Root records, successor payloads, expected field values, and confidential
  fields are not copied into metrics or CallTree attributes.

If an authorization decision requires the current record, the framework may
perform an authorization load before the atomic transition. That load is not
the concurrency check. The provider still verifies the authoritative record
inside the atomic operation.

## Observability and Audit

Expected call-tree layers:

```text
action:...
  -> uow:entitystore:conditional-transition
     -> space:entitystore:conditional-transition
        -> space:datastore:conditional-transition
```

Bounded outcome values:

- `transitioned`;
- `not-matched`;
- `conflict`;
- `unauthorized`;
- `unsupported-capability`;
- `provider-failure`; and
- `transaction-failure`.

Metrics classify from the typed result or structured `Conclusion`, not from
display-message parsing.

Audit evidence should identify:

- logical operation and component;
- root and successor ids when safe;
- expected and resulting revision tokens;
- result category;
- transaction correlation; and
- caller/security subject according to existing redaction policy.

## Cache and View Coherence

On `Transitioned`:

- install or evict the authoritative root according to Working Set policy;
- install the created successor only when its Working Set policy admits it;
- evict stale pre-transition values;
- invalidate affected rebuildable Views after the datastore commit; and
- never publish an in-memory value before commit.

The first implementation uses component-local `ViewSpace.invalidateAll()`
because the runtime does not yet carry a complete Entity-to-View dependency
map. Exact dependency targeting can replace this conservative operation after
that map becomes a runtime contract.

On `NotMatched`:

- do not invalidate Views as if this caller mutated data;
- replace or evict a stale resident root using the authoritative returned
  record; and
- return the typed existing value after read authorization.

Cross-process cache invalidation remains a distributed-runtime concern. Phase
49 must not claim cluster-wide cache coherence, but one process must reconcile
its local cache after observing an authoritative mismatch.

## Failure and Result Matrix

| Situation | Result |
| --- | --- |
| Expected root and token match; successor succeeds | `Transitioned` |
| Root exists but token or admitted expected value differs | `NotMatched(existing)` |
| Root does not exist | structured not-found failure |
| Successor create collides unexpectedly | structured conflict failure |
| Bound successor does not exist | structured not-found failure |
| Authorization denies any required access | structured authorization failure |
| Provider lacks atomic capability | structured unsupported-capability failure |
| Provider transaction or conversion fails | structured datastore/transaction failure |
| Ordinary update carries stale token | structured conflict failure |

## Executable Specification Plan

### Model properties

- Token advancement is monotonic.
- A transition plan cannot contain duplicate expected fields.
- Caller mutation cannot set reserved revision metadata.
- `NotMatched` preserves the authoritative existing root.
- Result serialization does not expose provider details.

### In-memory concurrency

Given one root and many distinct successor candidates, start simultaneous
UnitOfWork programs behind a barrier.

Then:

- exactly one result is `Transitioned`;
- every other result is `NotMatched`;
- exactly one successor is present;
- the root references that successor;
- the root revision advanced once; and
- no orphan successor exists.

Run this as a property over bounded caller counts greater than one.

### Rollback

Inject failure:

- after guard match;
- during successor create;
- during root update; and
- during commit.

Then neither a successor nor a changed root is externally visible.

### SQLite

Use separate runtime callers and separate connections against one SQLite
database. Prove the same one-winner property and restart visibility.

### Shared datastore

Use the selected shared provider profile with independently executing callers.
Prove the same result without component-local locking.

### Authorization and observability

- Unauthorized root update does not invoke the datastore transition.
- Unauthorized bind does not expose the successor.
- `Transitioned` and `NotMatched` have distinct CallTree outcomes.
- Payload and expected values are absent from CallTree/metrics.
- Structured provider failures remain unchanged through the DSL.

### Cache and View behavior

- No cache update occurs before provider success.
- Successful transition refreshes/evicts root and successor correctly.
- `NotMatched` removes a stale resident root.
- Successful transition invalidates affected Views once.
- Failed transition does not publish candidate state.

### CBD Support acceptance

Two concurrent terminal Review Run successor attempts must:

- retain the terminal predecessor;
- create exactly one accepted successor;
- return the same authoritative successor ownership to the loser;
- invoke expensive successor work once; and
- contain no SQL, JDBC, raw DataStore, or process-local locking in CBD Support.

## Proposed Work Stack

### EC-01: Normative contract

- Promote token, conflict, transition, result, atomicity, and boundary semantics
  to design/spec.
- Fix the exact storage field and migration behavior.

### EC-02: Concurrency token foundation

- Add framework-managed revision metadata.
- Add expected-token Entity mutation forms.
- Add structured stale-write conflict behavior.

### EC-03: Atomic datastore capability

- Add closed record-level plan/result.
- Implement deterministic in-memory behavior.
- Reject unsupported providers without fallback.

### EC-04: EntityStore, UnitOfWork, and DSL

- Add typed Entity transition models.
- Integrate authorization, transition hooks, CallTree, audit, caches, and View
  invalidation.

### EC-05: SQL provider evidence

- Implement SQLite native transaction behavior.
- Implement or select one shared-datastore provider profile.
- Add simultaneous-attempt and rollback specs.

### EC-06: CBD Support driver

- Replace application-side successor ownership logic with the protected DSL.
- Prove one successor owner and retained predecessor.

### EC-07: Verification and closure

- Run focused and full CNCF tests.
- Run downstream CBD Support acceptance.
- Record both 9.12 baseline and 9.39 as completed.
- Add one combined strategy section 8 history item and remove both active
  section 9 entries.
- Preserve 9.40 as the separate future conflict-resolution/repair item.
- Update strategy/phase status and implementation annotations.

## Decisions Required During Normative Promotion

- Final concurrency-token type and reserved storage field name.
- Initial token value and migration behavior for records without a token.
- Whether unversioned save/update remains admitted, deprecated, or restricted
  to explicit repair paths.
- Exact typed mechanism used to admit immutable expected fields.
- Whether `NotMatched` returns the whole typed root or a bounded transition
  projection.
- Provider capability declaration and activation-time validation mechanism.
- Shared-datastore profile used for Phase 49 acceptance.
- Exact View invalidation targeting available in the first implementation.

These questions do not change the selected phase boundary. They must be settled
before the corresponding implementation stage is marked complete.

## References

- `docs/journal/2026/07/2026-07-24-phase-49-entity-conflict-conditional-transition-consideration.md`
- `docs/journal/2026/07/entity-internal-dsl-conditional-transition-handoff-2026-07-23.md`
- `docs/journal/2026/07/entity-internal-dsl-claim-or-load-handoff-2026-07-23.md`
- `docs/journal/2026/07/2026-07-23-datastore-boundary-and-shared-database-decision.md`
- `docs/notes/unitofwork-guideline.md`
- `docs/rules/type-modeling.md`
- `docs/strategy/cncf-development-strategy.md`
