# Entity Conflict and Conditional Transition Implementation Proposal

status = EC-05 implemented and release-validated, non-normative
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

## EC-04 Atomic Datastore Capability Plan

status = accepted for implementation
planned_at = 2026-07-24
phase_stage = EC-04

EC-04 implements only the provider-neutral record boundary and deterministic
in-memory reference behavior. Component-facing typed transition definitions,
EntityStore hydration, UnitOfWork operations, authorization, and protected DSL
belong to EC-05 and EC-06. SQLite and MySQL implementations belong to EC-07.

### Model and capability

Add `EntityConditionalTransition.scala` under the datastore package with these
closed model trunks:

- `DataStoreConditionalValue` for text, boolean, integral, canonical decimal,
  instant, admitted identifier, and admitted Entity-id values;
- `DataStoreComponentOwner` for explicit component ownership supplied by the
  trusted Entity normalization boundary;
- `DataStoreConditionalExpectedField` for one canonical storage field and one
  exact expected value;
- `DataStoreConditionalRoot` for root collection/id, revision field, required
  expected revision, unique exact fields, non-empty patch, and next revision;
- `DataStoreConditionalSuccessor` with only `Create` and `Bind`;
- `DataStoreConditionalTransitionPlan` for root, successor, storage-shape side
  effects, and closed bounded correlation metadata;
- `DataStoreConditionalTransitionResult` with only `Transitioned` and
  `NotMatched`; and
- supplementary mix-in capability `EntityConditionalTransitionDataStore`.

The plan constructor is validated. Missing expected revision is rejected by
the constructor rather than represented inside an admitted plan. Provider
implementations defensively validate every admitted plan again before reading
or mutating storage.

The provider model contains only collection/entry identities, exact storage
values, normalized records, revision state, closed side effects, and bounded
logical correlation data. It contains no Entity/domain object, persistence
codec, authorization policy, callback, SQL, provider transaction, EntitySpace,
Working Set, or caller UnitOfWork.

### Admission and boundedness

The first implementation fixes explicit safety limits and tests their boundary
values:

- at most 32 exact expected fields;
- at most 64 framework side-record effects;
- at most 128 characters for a canonical field identity;
- at most 4096 characters for a canonical encoded text, identifier, or
  Entity-id expectation value; and
- at most 256 characters for each correlation value;
- at most 256 fields in each provider-bound record;
- at most 1024 values in each ordered provider-bound sequence; and
- at most 16 nested record/sequence levels.

The sequence validator materializes at most `MAX_COLLECTION_VALUES + 1`
elements. The bounded prefix both supplies the values for recursive validation
and detects an over-limit lazy sequence without a complete traversal.

Names must be non-blank and free of control characters. Expected field names
must be unique and cannot be the revision field. Root changes must be
non-empty and cannot contain the revision field. Root, successor, and
side-record primary targets must be distinct, and side-record targets must be
unique.

Root changes, successor create records, and side-record saves pass the same
closed-record validator. It admits normalized string, boolean, numeric,
`Instant`, nested `Record`, and ordered `Seq` storage values. It rejects null
records/values, unordered collections, arbitrary objects, domain values,
callbacks, and `SetNull` outside the root patch.

`Create` carries a normalized successor record with its canonical initial
revision. `Bind` carries successor collection/id, revision field, and the
expected revision admitted by the upper Entity boundary. A create collision is
a structured conflict. A missing bound successor is not found. A changed bound
successor revision is a structured conflict without a successor payload.

### DataStoreSpace boundary

Add `DataStoreSpace.conditionalTransition(plan)`.

Before invoking a provider, `DataStoreSpace`:

1. requires root and successor collections to be Entity collections;
2. requires their typed `DataStoreComponentOwner` values to match without
   parsing `EntityId.major`, `EntityId.minor`, or collection names;
3. resolves root, successor, and every side-record collection;
4. requires every collection to resolve to the same `DataStore` instance; and
5. requires that instance to implement
   `EntityConditionalTransitionDataStore`.

One `DataStore` instance is the current datastore transaction-domain owner.
The capability contract forbids an implementation from routing an admitted
plan across multiple native transaction domains. A future provider with
internal sharding must reject a cross-domain plan or expose separate datastore
instances; EC-04 does not add a caller-selectable transaction-domain id.

Unsupported capability, component mismatch, provider mismatch, and malformed
plan all fail before the first provider read or mutation. There is no ordinary
CRUD fallback.

### In-memory reference algorithm

`InMemoryDataStore` implements the supplementary capability under one
datastore-owned synchronized boundary:

1. validate the complete plan;
2. snapshot every affected collection into immutable staged state;
3. load the authoritative root and compare revision plus exact fields;
4. return `NotMatched(authoritativeRoot)` without changing staged state when
   the guard differs;
5. stage successor create or verify the bound successor and its revision;
6. stage the root patch and next revision;
7. stage every side-record save/delete;
8. project authoritative root and successor records from staged state; and
9. replace the datastore collection map once, then return `Transitioned`.

No mutable collection is published before every step succeeds. A protected
sealed checkpoint hook supports deterministic test injection after guard
admission, after successor work, after root work, and immediately before the
single publish. The default implementation is inert. Every injected failure
must leave root, successor, and side records byte-for-byte unchanged.

The in-memory provider has no indeterminate commit acknowledgment: its
single-state replacement either occurs or does not occur. Transaction-
indeterminate behavior remains part of the provider contract and is exercised
with native providers in EC-07.

### Result and failure rules

`NotMatched` is returned only when the authoritative root exists and its
revision or an exact expected field differs. It contains the authoritative
root record and performs no mutation.

All other outcomes remain structured failures:

- missing root or bound successor: not found;
- successor create collision: conflict;
- bound successor revision mismatch: conflict;
- malformed plan or unsupported exact value: argument/policy failure;
- unsupported capability or domain mismatch: operation/capability failure; and
- provider/checkpoint failure: original structured failure.

`Transitioned` returns provider-authoritative root and successor records after
the single in-memory publish. EC-05 is responsible for typed hydration and the
committed-projection failure boundary.

### Executable specifications

Add two behavior-oriented executable specifications:

- `DataStoreConditionalTransitionSpec`
  - validates exact-value admission and plan limits;
  - rejects missing revision, duplicate fields, reserved-field mutation,
    empty root patch, open/malformed provider records, overlapping targets,
    typed component-owner mismatch, provider mismatch, and unsupported
    providers before provider execution; and
  - verifies create/bind result and structured failure distinctions.
- `InMemoryConditionalTransitionSpec`
  - proves successful create and bind transitions;
  - proves ordinary mismatch changes no state;
  - injects failure at every staged checkpoint and proves complete rollback;
  - uses a ready/start barrier with ScalaCheck-generated caller counts from 2
    through 12 to prove exactly one `Transitioned`, all admitted losers
    `NotMatched`, one successor, one root successor reference, one token
    advancement, and no orphan side record; and
  - verifies ordinary CRUD cannot observe staged intermediate state.

Focused EC-04 validation is:

```text
sbt -J-Xmx4G --batch "testOnly
  org.goldenport.cncf.datastore.DataStoreConditionalTransitionSpec
  org.goldenport.cncf.datastore.InMemoryConditionalTransitionSpec
  org.goldenport.cncf.datastore.EntityVersionedMutationDataStoreSpec
  org.goldenport.cncf.entity.ContentBodyVersionedMutationSpec"
sbt -J-Xmx4G --batch Test/compile
git diff --check
```

EC-04 is complete only after implementation, focused validation, independent
review, review-fix where required, clean re-review, full CNCF validation, and a
release checkpoint commit. EC-05 starts only after that evidence is recorded.

### Implementation order

The EC-04 implementation turn follows repository authority order:

1. add the selected fixed limits and provider-plan details to the static
   specification;
2. align the normative design with those accepted details;
3. add the executable specification structure and failing behaviors;
4. implement the closed model and validation;
5. implement `DataStoreSpace` capability/domain admission;
6. implement the in-memory staged-state algorithm and rollback checkpoints;
7. run the focused validation matrix; and
8. update the phase evidence without marking EC-04 done before review and
   release validation.

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

## Authoritative Phase Ledger Mapping

The original proposal used a provisional seven-stage numbering. The
authoritative Phase 49 dashboard and checklist supersede that numbering:

- EC-01: normative contract;
- EC-02: concurrency model and storage shape;
- EC-03: version-aware ordinary mutation;
- EC-04: provider-neutral atomic datastore capability and in-memory reference;
- EC-05: typed EntityStore, UnitOfWork, and protected DSL;
- EC-06: coherence, authorization, audit, and diagnostics;
- EC-07: SQLite and shared-provider concurrency evidence;
- EC-08: CBD Support downstream acceptance; and
- EC-09: verification and closure.

EC-01 through EC-04 are complete. EC-04 REVIEW_FIX applies Entity-collection
admission consistently at model,
`DataStoreSpace`, and provider boundaries, while retaining private constructors
and private `copy` methods for the admitted plan algebra. A clean focused
four-suite run passes all 22 tests, and independent `Test/compile`, whole-file
naming/executable-specification scans, and `git diff --check` pass. EC-04
re-review then found unbounded sequence traversal and two executable-evidence
gaps. The subsequent REVIEW_FIX uses a 1025-value bounded prefix, exercises
every structural limit including lazy evaluation, and proves that create and
bind return the records committed by the provider. The focused four-suite
matrix again passes all 22 tests and `Test/compile` passes. The final clean
re-review found no actionable findings, and the full CNCF suite completed 341
suites with all 2397 executed tests successful. EC-05 is the next implementation
slice. Later work must use the dashboard/checklist numbering and must not revive
the provisional mapping.

EC-05 implementation now provides the typed transition model, the private
UnitOfWork algebra operation, EntityStore/EntityStoreSpace normalization, and
the protected ActionCall helpers. The implementation:

- derives expected storage fields only through `EntityPersistent`;
- maps root patches through generated `EntityPersistentUpdate` and
  `Update.toChangesRecord`;
- uses only the supplementary atomic datastore capability;
- applies normal successor create storage/content policy;
- loads bind revision evidence before provider execution;
- reuses `TransitionValidationHook.beforeUpdateById`;
- preserves structured failures and committed-projection handling; and
- does not add an automatic public operation surface.

The focused executable evidence covers typed field admission, exact-value
limits, successful create, normal `NotMatched`, and bound-successor execution.
EC-05 remains in progress until a separate review validates the implementation
and its whole-file naming/specification compliance.

The EC-05 REVIEW found that the initial binding submitted a full root
candidate, re-authorized `NotMatched` before evicting stale resident state,
admitted raw identity objects at the provider boundary, and overclaimed
authorization/coherence evidence. REVIEW_FIX changed the implementation and
evidence as follows:

- provider root changes are now a normalized delta containing changed domain
  fields plus framework-generated lifecycle/audit fields;
- empty, no-op, managed-field, and logically-deleted root patches fail before
  provider mutation;
- `NotMatched` evicts stale resident state before post-result authorization;
- provider-bound records again accept only the closed normalized scalar
  algebra;
- transition-hook rejection and a bound-successor revision race have direct
  executable evidence;
- the protected ActionCall and `ServiceInternal` helpers have direct evidence
  that they construct the same private UnitOfWork operation and differ only in
  admitted access mode; and
- EC-05 metadata no longer claims EC-06 View/coherence completion.

The first clean re-review then found that both helpers stamped every supplied
collection with the executing component owner without first proving collection
ownership. REVIEW_FIX now resolves the canonical root and successor
collections in the executing component's registered `EntitySpace`. A foreign
or otherwise unregistered collection returns a structured component-scope
denial before UnitOfWork construction, including for `ServiceInternal`.
Executable evidence also separates create from bind behavior and gives each
root-patch rejection condition its own semantic test case.

The final follow-up focused runs completed 33 conditional-transition tests
across five suites and 14 versioned-mutation regression tests across four
suites. The added evidence rejects a candidate id from another collection,
proves an admitted candidate id is not reevaluated, and proves an absent id is
generated in the retained admitted collection.
`Test/compile`, tracked and untracked whitespace checks, and whole-file
naming/specification scans also completed successfully.

The second clean re-review found a narrower create-successor identity gap:
ActionCall admitted `EntityPersistentCreate.collection(candidate)`, while
provider preparation preferred `EntityPersistentCreate.id(candidate)` without
requiring both collections to match. REVIEW_FIX closes the intent at
construction by retaining the admitted collection and optional candidate id,
rejecting a mismatched id collection, and generating missing ids only from the
retained collection. Model and UnitOfWork evidence cover mismatch rejection
and prove provider preparation does not reevaluate either identity.

The final clean RE_REVIEW_COMMIT found no actionable finding. Full CNCF release
validation passed all 2415 executed tests across 344 suites, with 0 failed and
0 aborted. EC-05 is complete; EC-06 remains the next Phase 49 slice.

## Resolved and Remaining Decisions

Resolved by the normative contract and completed stages:

- concurrency uses the framework-owned `EntityConcurrencyToken`;
- physical storage uses the canonical managed revision field;
- new records start at token one and legacy records admit virtual token zero;
- ordinary protected mutation requires an explicit expectation;
- explicitly unversioned framework mutation requires a closed purpose plus
  System admission;
- conditional mismatch returns the authoritative typed root after read
  authorization;
- capability resolution is operation-time and never falls back to ordinary
  CRUD;
- SQLite and MySQL are the Phase 49 native-provider profiles; and
- initial View invalidation is component-local and conservative.

Remaining implementation choices are local to their assigned stages:

- EC-05 fixes the typed transition-field admission API and exact Entity result
  carrier;
- EC-06 fixes bounded audit/metric projection details; and
- EC-07 fixes native dialect statements and provider-specific fault
  injection.

## References

- `docs/journal/2026/07/2026-07-24-phase-49-entity-conflict-conditional-transition-consideration.md`
- `docs/journal/2026/07/entity-internal-dsl-conditional-transition-handoff-2026-07-23.md`
- `docs/journal/2026/07/entity-internal-dsl-claim-or-load-handoff-2026-07-23.md`
- `docs/journal/2026/07/2026-07-23-datastore-boundary-and-shared-database-decision.md`
- `docs/notes/unitofwork-guideline.md`
- `docs/rules/type-modeling.md`
- `docs/strategy/cncf-development-strategy.md`
