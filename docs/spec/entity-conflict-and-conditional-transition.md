# Entity Conflict and Conditional Transition

Status: normative static contract

Architectural context is defined by
`docs/design/entity-conflict-and-conditional-transition.md`.

## Scope

This specification defines the concurrency token, version-aware Entity
mutation, protected conditional transition, atomic datastore capability,
authorization, coherence, diagnostics, and provider-equivalence contracts.

Required executable evidence is assigned in the evidence matrix below. A named
specification is evidence only after that file exists and its relevant
examples pass.

> **Phase 50 supersession annotation (2026-07-25):** R1's provisional
> non-negative token range, R3's virtual token `0`, and R4's detached snapshot
> describe the Phase 49 API. They do not govern a provider path after that path
> is migrated to the Phase 50 `EntityRevision` kernel. For a migrated path,
> revision starts at `1`, a physically missing revision is an admission failure
> under Phase 50 acceptance item ER-08, and no virtual-zero compatibility is
> synthesized. The historical text remains below for Phase 49 traceability
> until Phase 50 SE-09 replaces the complete provisional token contract.

## Concurrency Token (R1)

Every newly persisted Entity MUST have one framework-owned
`EntityConcurrencyToken` with canonical persisted value `1`.

The token MUST be a non-negative opaque `Long`. A successful admitted mutation
MUST advance the token exactly once. A failed, rejected, stale, mismatched, or
rolled-back mutation MUST NOT advance it.

Token advancement from `Long.MaxValue` MUST fail before storage mutation.
Component code MUST NOT calculate a token or derive it from time, `updatedAt`,
resident state, or an object version.

## Managed Storage Field (R2)

The logical token field MUST be `cncfRevision`. Its canonical datastore name
MUST be `cncf_revision`.

The field MUST be part of the framework-managed Entity storage shape.
Application create records, domain Entity codecs, patches, import values, and
caller root mutations MUST NOT set or clear it.

Ordinary business-data projections MUST omit the field. A
concurrency-aware persistence or transport contract MAY expose it only as an
`EntityConcurrencyToken`.

## Legacy Record Admission (R3)

A stored Entity record without `cncf_revision` MUST have virtual token `0`.

A concurrency-aware load of that record MUST return token `0`. Its first
version-aware mutation MUST compare expected token `0` against physical field
absence inside the atomic provider operation. One successful mutation MUST
persist token `1`.

Competing first mutations of a legacy record MUST admit exactly one winner.
The runtime MUST NOT backfill the field through an unlocked load followed by an
unconditional write.

## Snapshot Carrier (R4)

A concurrency-aware Entity read MUST return an `EntitySnapshot[A]` containing
the typed Entity and its admitted token.

The token MUST NOT be added to every domain Entity type. A path that forms an
update from loaded state MUST carry the token from `EntitySnapshot[A]` or an
equivalent framework-owned value without reloading it from a Working Set.

Read-only operations MAY continue to return the plain Entity.

## Version-Aware Ordinary Mutation (R5)

Normal Entity full save, typed Entity update, patch update by Entity id,
Aggregate-root mutation, and framework state-transition mutation MUST carry an
`EntityMutationExpectation`.

The datastore provider MUST compare the expected token and persist the change
plus next token in one native atomic operation.

Framework-owned side records required by the Entity storage shape, including
ContentBody overflow records, MUST be prepared without an early datastore
effect and included in the same provider atomic operation as the guarded root.
A stale or failed mutation MUST change neither the root nor any such side
record.

A stale expected token MUST return a structured conflict
`Consequence.Failure(Conclusion)`. It MUST change no stored Entity, token,
EntitySpace value, Working Set value, or View.

A normal ActionCall/UnitOfWork mutation MUST NOT perform an unversioned
overwrite. Create, seed import, and explicitly classified physical migration
are separate operations. Force or repair behavior MUST NOT be represented by
an absent token.

An explicitly unversioned framework mutation MUST declare one closed
framework purpose and MUST be admitted with System access. It MUST NOT be
available through the protected application Entity DSL. Stable-id ownership
MUST use claim-or-load and MUST NOT overwrite an existing Entity.

## Transition Field Admission (R6)

An `EntityTransitionDefinition[R]` MUST own every field admitted to a
conditional expectation.

An admitted field MUST have a stable logical identity, canonical storage
identity, typed persistence codec, and exact-value conversion. Component code
MUST NOT supply a raw datastore column, query path, SQL identifier, expression,
function, or script.

Expected fields MUST be unique. Duplicate expected fields MUST fail before
provider mutation.

## Exact Expectation Values (R7)

The first exact-value algebra MUST admit only text, boolean, integral number,
canonical decimal, instant, admitted identifier, and admitted Entity id.

It MUST reject records, collections, binary values, floating-point
approximations, arbitrary objects, null, missing-field predicates, ranges,
regular expressions, functions, scripts, SQL, and general query expressions.

The expectation MUST be conjunction-only. An empty expected-field set MAY use
the token as its complete guard.

The normalized provider plan MUST contain at most 32 exact expected fields.
Every canonical field identity MUST be non-blank, free of control characters,
and no longer than 128 characters. Text, admitted identifier, and admitted
Entity-id expectation values MUST be no longer than 4096 characters in their
canonical encoded form. Values exceeding these bounds MUST fail before provider
mutation.

## Successor Intent (R8)

A conditional transition MUST use exactly one closed successor intent:

- `Create(candidate)`; or
- `Bind(id)`.

`Create` MUST create the successor inside the same provider transaction as the
root mutation. A duplicate successor MUST be a structured conflict and MUST
NOT overwrite an existing Entity.

`Create` admission MUST evaluate and retain one successor collection and one
optional candidate Entity id. When the candidate supplies an id, that id MUST
belong to the retained collection or construction MUST fail. Provider
preparation MUST NOT reevaluate either identity. When no candidate id exists,
the framework MUST generate it in the retained collection.

`Bind(id)` MUST remain a component-facing intent. Before provider-plan
submission, the framework MUST load and authorize an `EntitySnapshot` for the
bound successor and normalize the bind into successor id plus expected
successor token.

The provider MUST verify the bound successor token inside the same transaction
as the root guard. A missing bound successor MUST be a structured not-found
failure. A changed successor token MUST be a structured conflict without a
successor payload. Neither result may mutate the root.

Root and successor collections MUST belong to one component, one datastore
provider instance, and one provider transaction domain. Any mismatch MUST fail
before mutation.

## Root Mutation (R9)

The root mutation MUST use an admitted typed patch or persistence update codec.

The framework MUST normalize update directives, reject framework-managed
fields, apply Entity storage-shape and lifecycle policy, and supply the next
token. An empty effective root mutation MUST fail before provider mutation.

The provider MUST NOT infer an application successor relationship. The
admitted root mutation MUST contain the successor reference or ownership
change.

## Conditional Result (R10)

Conditional transition MUST return one of these successful typed outcomes:

- `Transitioned(rootSnapshot, successorSnapshot)`; or
- `NotMatched(existingSnapshot)`.

`Transitioned` MUST contain provider-authoritative committed records and their
tokens.

`NotMatched` MUST mean that the root exists and its authoritative token or at
least one admitted expected value differs. It MUST NOT represent not-found,
authorization denial, malformed intent, unsupported provider, provider
failure, conversion failure, or transaction failure.

Returning `NotMatched(existingSnapshot)` MUST require read authorization for
the authoritative returned root after provider execution. The earlier
authorization decision MUST NOT be reused when mismatch proves that the root
changed. A denied caller MUST receive no root payload or actual token.

## Supplementary Datastore Capability (R11)

Versioned mutation and conditional transition MUST be supplementary datastore
capabilities. Ordinary `DataStore` CRUD MUST NOT provide a load-then-write
default implementation.

`DataStoreSpace` MUST resolve provider support explicitly before the first
datastore effect. An unsupported provider MUST return a deterministic
structured unsupported-capability failure.

The absence of an explicit component startup requirement MUST NOT permit a
best-effort fallback. The runtime MAY perform capability admission at
operation time when protected DSL usage is dynamic.

## Provider Plan Boundary (R12)

The normalized provider plan MAY contain only explicit framework-owned
component ownership, bounded record-level mutation data, safe logical
correlation metadata, provider-neutral collection and entry identities, the
expected token for a bound successor, and bounded framework-owned side-record
save/delete effects required by the canonical Entity storage shape.

An admitted plan MUST carry a non-optional expected root revision and the exact
next revision. Its root patch MUST be non-empty and MUST NOT contain the managed
revision field. Expected fields MUST NOT contain the managed revision field.
The plan MUST contain at most 64 side-record effects. Each correlation value
MUST be non-blank, free of control characters, and no longer than 256
characters.

Each provider-bound record MUST contain at most 256 fields. Each ordered
provider-bound sequence MUST contain at most 1024 values. Nested records and
sequences MUST be at most 16 levels deep.

Sequence admission MUST inspect at most the first 1025 values to distinguish
an admitted sequence from an over-limit sequence. It MUST NOT traverse an
entire lazy or otherwise unbounded sequence before returning the deterministic
limit failure.

Provider-bound record values MUST be normalized strings, booleans, numeric
storage scalars, `Instant`, nested `Record`, or ordered `Seq` values. Raw null,
unordered collections, arbitrary objects, domain values, persistence
typeclasses, and callbacks MUST fail before provider execution. The canonical
`SetNull` marker MAY occur only in the root patch.

The guarded root, successor, and side-record primary targets MUST be pairwise
distinct. Side-record targets MUST be unique. A create successor record MUST
carry the canonical initial revision. A bind successor MUST carry the revision
observed and authorized by the upper Entity boundary.

Every side-record effect MUST resolve to the same datastore provider instance
and native transaction domain as the guarded root. Side-record effects MUST be
closed provider-neutral values; the plan MUST NOT contain effect callbacks or
pre-executed datastore results.

Root and successor collections MUST be Entity collections whose typed
`DataStoreComponentOwner` values are equal. The trusted Entity normalization
boundary MUST supply each owner. Neither plan construction nor
`DataStoreSpace` MAY infer component ownership from `EntityId.major`,
`EntityId.minor`, collection naming, or another parsed identifier.
`DataStoreSpace` MUST resolve the root, successor, and every side-record
collection before provider invocation. All collections MUST resolve to the
same `DataStore` instance, which is the transaction-domain owner for this
capability. A provider that internally spans more than one native transaction
domain MUST reject the plan or expose those domains as distinct datastore
instances.

It MUST NOT contain domain objects, persistence typeclasses, authorization
policy, EntitySpace values, Working Set values, SQL, provider expressions,
provider transactions, or arbitrary callbacks.

The provider MUST return authoritative stored records. A caller-provided or
resident record MUST NOT be accepted as comparison authority.

## Atomicity and Rollback (R13)

The provider MUST execute root verification, successor create or verification,
root mutation, framework-owned side-record effects, token advancement, and
authoritative result loading in one native atomic transaction.

If verification does not match, no successor, side-record, or root change may
be made.

Failure before commit during successor work, root mutation, or provider-level
record conversion MUST roll back and leave no externally visible successor,
side-record change, root change, or token advance.

A provider-reported commit rejection MUST leave no committed change. An
indeterminate commit acknowledgment MUST return a structured
transaction-indeterminate failure, MUST NOT be retried automatically, and MUST
require a new authoritative read. Provider atomicity MUST still guarantee that
root and successor are both committed or both absent.

Typed Entity hydration after acknowledged commit MUST NOT be described as
rollback-capable. An unexpected failure at that boundary MUST return a
structured committed-projection failure, preserve the committed datastore
state, evict affected resident state, and prohibit automatic retry.

`Transitioned` MUST NOT be returned before commit succeeds and authoritative
records are projected successfully.

`async`, retry, provider reconnect, or UnitOfWork lifecycle behavior MUST NOT
split the provider atomic boundary.

## UnitOfWork Chokepoint (R14)

Versioned mutation and conditional transition MUST have explicit UnitOfWork
operations.

The canonical execution order MUST be:

1. identity canonicalization;
2. authorization;
3. lifecycle and transition validation;
4. provider-plan normalization;
5. atomic capability resolution;
6. provider execution;
7. post-result authorization for every authoritative record returned after
   mismatch;
8. EntitySpace and Working Set reconciliation;
9. committed-mutation View invalidation; and
10. bounded audit and observability.

Component code MUST reach conditional transition only through a protected
ActionCall/Behavior DSL returning `ExecUowM`.

An internal DSL variant MUST NOT bypass collection, Entity, relationship,
successor, component-scope, or cross-component authorization.

## Authorization (R15)

Conditional transition MUST authorize:

- root read;
- root update;
- successor collection create for `Create`; or
- successor read plus required relationship access for `Bind`.

Authorization denial MUST occur before unauthorized mutation.

An authorization load MAY inspect the current record for object-side policy,
but MUST NOT replace the authoritative token/value comparison inside the
provider transaction.

The normalized bind plan MUST verify the same successor token that was used for
successor authorization. A bound-successor token mismatch MUST fail without
root mutation.

An authoritative root returned for `NotMatched` MUST be read-authorized again
before it is exposed or installed. A post-result denial MUST evict any stale
resident value and MUST return no root payload or actual token.

Unauthorized callers MUST NOT receive root data, successor data, expected
values, confidential fields, or provider details.

## Lifecycle Validation (R16)

Applicable Entity and state-transition validation hooks MUST run before the
provider mutation.

The provider token predicate MUST still detect a race after validation. A
validation hook MUST NOT own the concurrency check.

A hook failure MUST invoke no provider mutation. Post-mutation lifecycle or
audit behavior MUST use only the committed authoritative provider result.

## EntitySpace and Working Set Coherence (R17)

No EntitySpace or Working Set mutation may occur before provider success.

For `Transitioned`, stale root and successor entries MUST be evicted before
authoritative values are installed according to Working Set policy.

For `NotMatched`, the caller MUST NOT be recorded as mutating data. A stale
resident root MUST be evicted or replaced from the authorized authoritative
record.

If post-result read authorization fails, the stale resident root MUST be
evicted and the authoritative record MUST NOT be installed or returned.

A resident token or record MUST NOT bypass the datastore predicate.

## View Coherence (R18)

A committed `Transitioned` or successful ordinary mutation MUST invalidate
the component-local `ViewSpace` after datastore success.

`NotMatched`, stale conflict, authorization denial, validation failure,
unsupported capability, provider failure, and transaction rollback MUST NOT
invalidate Views as if the caller committed a mutation.

The initial implementation MUST use component-local
`ViewSpace.invalidateAll()`. It MUST NOT claim exact dependency targeting or
cross-process cache invalidation.

## Structured Failure Contract (R19)

All failures MUST remain `Consequence.Failure(Conclusion)` values.

The implementation MUST distinguish:

- stale ordinary mutation as conflict;
- missing root or bound successor as not found;
- authorization denial;
- malformed or inadmissible expectation;
- unsupported provider capability;
- successor collision as conflict;
- provider conversion or I/O failure; and
- transaction failure;
- indeterminate commit acknowledgment; and
- committed-projection failure.

Framework diagnostics MUST use existing `Taxonomy`, `Cause.Kind`, and
`Descriptor.Facet` structure. They MUST NOT use application
`Status.detailCodes`, parse display text, or create a Blob-style/component-local
error taxonomy.

## Observability and Audit (R20)

CallTree MUST preserve the ActionCall, UnitOfWork, EntityStoreSpace, and
DataStoreSpace layers for versioned mutation and conditional transition.

Metrics and audit MUST distinguish `transitioned`, `not-matched`, `conflict`,
`unauthorized`, `unsupported-capability`, `provider-failure`, and
`transaction-failure` from typed results or structured `Conclusion`.

Default CallTree, metrics, and audit attributes MUST NOT contain root payloads,
successor payloads, expected field values, confidential values, SQL,
credentials, or provider transaction details.

## In-Memory Reference Provider (R21)

The in-memory provider MUST apply the whole compound transition under one
datastore-owned atomic boundary and MUST publish its replacement state only
after every step succeeds.

For any bounded caller count greater than one, simultaneous valid attempts
against one root token MUST produce:

- exactly one `Transitioned`;
- `NotMatched` for every admitted losing attempt;
- exactly one successor;
- one root reference to that successor;
- exactly one token advance; and
- no orphan successor.

Application or component-local locking MUST NOT be used as the evidence.

## SQLite Provider (R22)

SQLite MUST execute the compound transition using one connection and one
explicit native transaction.

Concurrency evidence MUST use independent callers and independent
connections. It MUST prove one-winner semantics, rollback safety, and
visibility after a new datastore instance opens the same database.

A single-threaded sequence or one reused connection is insufficient evidence.

## Shared MySQL Provider (R23)

The initial shared-datastore acceptance profile MUST use MySQL through the
provider-neutral JDBC and `SqlDataStore.Mysql` boundary.

Evidence MUST use independently executing callers against one physical
database. It MUST prove the same result and rollback matrix as the in-memory
and SQLite providers.

Component code and EntityStore MUST contain no MySQL SQL, driver handle,
connection, credential, or dialect branch.

## Downstream Acceptance (R24)

CBD Support Review Run successor ownership MUST use the protected generic
conditional-transition DSL.

Concurrent eligible attempts MUST retain the terminal predecessor, install
exactly one successor, expose authoritative successor ownership to admitted
losers, and start expensive successor work once.

CBD Support MUST contain no SQL, JDBC, raw `DataStore`, provider transaction,
or process-local ownership lock for this workflow.

## Deferred Conflict Resolution (R25)

The version-conflict baseline MUST NOT add force/repair commands, automatic
merge, conflict-resolution UI, or application overwrite policy.

Those capabilities remain separately owned and MUST NOT weaken the
expected-token requirement of ordinary mutation.

## Executable Examples

### E1: New Entity token

Given a newly created Entity, when it is loaded through a concurrency-aware
read, then its snapshot token is `1` and its business record does not expose
`cncf_revision`.

### E2: Legacy lazy admission

Given a record without `cncf_revision` and concurrent mutations expecting
token `0`, when the provider executes them, then one mutation persists token
`1` and every other mutation observes a mismatch.

### E3: Successful ordinary mutation

Given a snapshot with token `n`, when an ordinary mutation expects `n`, then
the authoritative record is updated and returned with token `n + 1`.

### E4: Stale ordinary mutation

Given a stored token different from the mutation expectation, when the
ordinary mutation executes, then it returns a structured conflict and changes
no stored or resident state.

### E5: Successful conditional transition

Given an authorized root whose token and admitted exact values match, when a
successor transition executes, then the provider commits one successor and one
root mutation and returns `Transitioned`.

### E6: Normal mismatch

Given an authorized root whose token or admitted exact value differs, when a
conditional transition executes, then it returns
`NotMatched(existingSnapshot)` without mutation.

### E7: Unauthorized mismatch

Given a caller that cannot read or update the root, when it submits a
conditional transition, then authorization fails and no authoritative root
payload is returned.

### E8: Rollback after guard admission

Given a matching root and an injected failure after the guard is admitted but
before successor work, when the provider transaction terminates, then neither
successor nor root mutation is visible.

### E9: Rollback during successor work

Given a matching root and an injected failure during successor creation or
verification, when the provider transaction terminates, then neither successor
nor root mutation is visible.

### E10: Rollback during root mutation

Given completed successor work and an injected failure during root mutation,
when the provider transaction terminates, then neither successor nor root
mutation is visible.

### E11: Commit failure remains atomic

Given completed root and successor work, when commit is rejected, then neither
change is visible. When commit acknowledgment is indeterminate, then the
result is transaction-indeterminate, automatic retry is prohibited, and a new
authoritative read observes either both changes or neither change.

### E12: Post-commit projection failure

Given an acknowledged committed transition whose authoritative record cannot
be hydrated into the typed Entity, when result projection runs, then a
committed-projection failure is returned, resident state is evicted, and the
committed mutation is not retried.

### E13: Bound successor changes after authorization

Given an authorized bound-successor snapshot whose token changes before the
provider guard, when the conditional transition executes, then it returns a
structured bound-successor conflict without mutating the root or exposing the
successor.

### E14: Root authorization changes before mismatch result

Given an initially authorized root whose concurrent mutation revokes caller
read access, when the provider returns the changed root as a mismatch, then
post-result authorization denies the payload and evicts stale resident state.

### E15: Unsupported provider

Given a selected datastore without the supplementary capability, when a
versioned mutation or conditional transition is admitted, then it fails before
the first datastore effect.

### E16: Resident stale root

Given a stale root in the Working Set and a newer authoritative datastore
token, when a transition is attempted, then the provider result wins and the
resident root cannot admit a stale mutation.

### E17: Provider parity

Given the same bounded simultaneous-attempt scenario against in-memory,
SQLite, and MySQL profiles, when each profile executes independent callers,
then each profile produces one winner and equivalent authoritative results.

### E18: CBD Support successor ownership

Given concurrent terminal Review Run successor attempts, when CBD Support uses
the protected DSL, then one successor owns continuation work and the terminal
predecessor remains retained.

### E19: Cross-component successor rejection

Given a root or successor collection that is not registered to the executing
component, when either protected conditional-transition helper admits the
request, then it returns a structured component-scope denial before
constructing a UnitOfWork operation.

Given a create codec that declares one successor collection but supplies a
candidate Entity id from another collection, when the successor intent is
constructed, then construction fails before ActionCall or provider admission.

## Executable Specification Evidence Matrix

| Rules | Examples | Executable specification |
| --- | --- | --- |
| R1-R4 | E1-E2 | `EntityConcurrencyTokenSpec` |
| R5, R19 | E3-E4 | `EntityVersionedMutationSpec`, `ContentBodyVersionedMutationSpec` |
| R6-R10 | E5-E6, E13-E14 | `EntityConditionalTransitionModelSpec` |
| R8, R14-R15 | E19 | `EntityConditionalTransitionModelSpec`, `UnitOfWorkConditionalTransitionSpec`, `ActionCallConditionalTransitionDslSpec` |
| R11-R13 | E3-E4, E8-E11, E13, E15 | `EntityVersionedMutationDataStoreSpec`, `ContentBodyVersionedMutationSpec`, `DataStoreConditionalTransitionSpec` |
| R14-R16 | E5, E7, E13-E14 | `UnitOfWorkConditionalTransitionSpec` |
| R17-R18 | E6, E12, E14, E16 | `EntityConditionalTransitionCoherenceSpec` |
| R19-R20 | E4, E7-E15 | `EntityConditionalTransitionDiagnosticsSpec` |
| R21 | E2-E6, E8-E11 | `EntityVersionedMutationDataStoreSpec`, `ContentBodyVersionedMutationSpec`, `InMemoryConditionalTransitionSpec` |
| R22 | E5-E6, E8-E11, E17 | `SqliteConditionalTransitionSpec` |
| R23 | E5-E6, E8-E11, E17 | `MysqlConditionalTransitionAcceptanceSpec` |
| R24 | E18 | CBD Support `ReviewDiagnosisPersistenceSpec` |
| R25 | E4 | Entity conflict API-surface regression specification |

## EC-05 Framework Binding

The protected typed transition MUST traverse:

```text
ActionCall protected DSL
  -> EntityStoreConditionalTransition UnitOfWork operation
  -> UnitOfWorkInterpreter
  -> EntityStoreSpace
  -> EntityStore
  -> DataStoreSpace.conditionalTransition
```

`EntityTransitionField` MUST derive its physical field from
`EntityPersistent.storeFieldName`. A transition expectation MUST reject a
field that is not owned by its `EntityTransitionDefinition`, duplicate fields,
framework-managed revision fields, unsupported exact values, and over-limit
input before provider execution.

The UnitOfWork interpreter MUST authorize root read and update plus successor
create or bind/read before provider execution. A bound successor MUST carry
the authoritative revision observed during that admitted read. A
`NotMatched(existing)` result MUST undergo read authorization against the
returned authoritative record before it is returned or installed.

The EntityStore binding MUST submit a normalized root delta rather than the
full candidate record. It MUST reject empty and ineffective patches,
framework-managed patch fields, and logically deleted roots before provider
execution. Post-result `NotMatched` handling MUST evict stale resident root
state before authorization of the returned authoritative record.

The `ServiceInternal` helper MUST NOT use System access and MUST NOT bypass
authorization, transition validation, UnitOfWork, EntityStore, or datastore
capability resolution.

After Entity-id canonicalization, the ActionCall boundary MUST resolve both
root and successor collections against the executing component's registered
`EntitySpace`. An unregistered collection MUST produce a structured
component-scope denial before a UnitOfWork operation is constructed. This
admission MUST be identical for the user and `ServiceInternal` helpers and
MUST NOT infer ownership by parsing an Entity identifier.

No conditional-transition operation is automatically exposed through
REST, Form, CLI, MCP, or generic CRUD projection.
