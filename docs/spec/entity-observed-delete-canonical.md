# Canonical observed Entity deletion

Document role: normative PHASE-96 / P96-07 / Slice P96-07B2A contract.
Status: implementation and executable specifications authored; parent validation
and independent acceptance pending. All four original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN.

This contract assembles the existing
[observed-delete provider port](entity-observed-delete.md) through canonical
EntityStoreSpace, StandardEntityStore and UnitOfWork. The provider prerequisite
owns authoritative comparison, native transaction and bounded side effects.
The [design](../design/entity-observed-delete-canonical.md) assigns the canonical
responsibilities. The existing [internal Display update bridge](display-mutation-bridge.md)
is unchanged and is not a delete route.

## R1: Additive typed operation and mandatory admission

`EntityStore.deleteObserved(id: EntityId, expectedRevision: EntityRevision)`
returns `Consequence[EntityDeletionResult]` using ExecutionContext.
`EntityDeletionResult(id, matchedRevision)` is immutable and contains the
authoritative prior matched revision, not a post-delete Record, requested
revision substituted for evidence, or next-revision receipt.

The base EntityStore implementation returns a structured unsupported-capability
failure after mandatory shape admission. Existing implementations therefore
remain source compatible. Legacy Unit-returning delete, restore and hard-delete
semantics are unchanged.

EntityStoreSpace and the active UnitOfWork interpreter admit null operation,
identity, representable nested identity references and expected revision before
calltree attributes, collection enumeration, routing or raw-record loading can
dereference them. Typed revisions must be positive. Identity constructors can
reject impossible identity shapes before an operation exists; that constructor
boundary is distinct from representable request admission. Canonical identities
are never parsed to choose ownership or storage policy.

## R2: Assembled managed revision and strict observation

The registered collection supplies its existing Embedded or Detached revision
binding and physical field name. A caller supplies neither representation nor
revision field. Explicit expected revision remains strict even when the
collection's ordinary concurrency default is None.

The standard store reads the raw target only to admit the assembled revision
representation and select the established server lifecycle policy. Missing,
already logically deleted, dead, malformed managed revision, unmanaged
representation and exhausted revision states fail structurally without effects
or revision synthesis. Existing persisted lifecycle aliases participate in both
canonical exclusion and the provider guards. The actual comparison remains
inside the provider's atomic boundary; the preliminary read is not a lock or
check-then-unconditional mutation.

## R3: Existing logical policy and canonical physical overflow

The server selects logical deletion using the existing `_is_soft_delete_target`
policy and constructs its patch with `_soft_delete_record`. Clock, principal,
post status, aliveness, updated/deleted audit, trace and optional correlation
values come from the server ExecutionContext. The provider advances the stored
managed revision once. Business and canonical content-overflow records remain
unchanged. The returned matched revision remains the prior revision.

Physical deletion carries a pure `ContentBodyStoragePolicy.planForDelete(id)`
intent for that identity's existing canonical overflow entry. The root and this
bounded Delete-only effect are one provider plan. Unrelated root and overflow
entries remain unchanged. Planning performs no eager overflow deletion, body
save, generic mutation or separate transaction. Logical plans have no effects.
Existing content-body save and hydration semantics are unchanged.

## R4: One authoritative provider plan and result

StandardEntityStore submits exactly one `EntityCompareAndDeletePlan` through
`DataStoreSpace.compareAndDeleteEntity`. Its root policy, binding field,
expected revision, lifecycle guards and optional overflow intent are assembled
server-side. Existing B1 shape admission, required new capability checks,
same-provider effect checks and returned-result validation are reused unchanged.

`Deleted(actualPriorRevision)` becomes `EntityDeletionResult(id,
actualPriorRevision)`. `Stale(expected, actual)` becomes the existing structured
canonical stale-entity-revision failure with Expected and Actual facets. The
standard store does not reconstruct a result by reading an absent root, return
null success, fabricate an omitted receipt, retry, or fall back to generic
delete or save.

## R5: Failure scope and no global rollback claim

Missing, malformed, unsupported, stale and actual provider I/O/transaction
failures remain structured Conclusions. Genuine prepublication memory failures
and native SQL checkpoint/commit rejection preserve complete root and effect
state according to the provider contract. Independent JDBC readers establish
SQL persistence and rollback evidence.

A buggy provider's invalid returned result is rejected, but rejection is not
proof that its independently committed effects were rolled back. No automatic
retry follows such uncertainty. Outer UnitOfWork abort is not a new global
transaction, two-phase commit, compensation or rollback guarantee after an
acknowledged provider commit. Calltree observations remain layered and bounded;
payload and provider internals are not exposed as diagnostics.

## R6: Active UnitOfWork authorization and coherence

`UnitOfWorkOp.EntityStoreDeleteObserved(id, expectedRevision, authorization)`
returns the typed deletion result, belongs to the Local planning class and
participates in normal collection resolution and genuine ExecUowM evaluation.
Recording and planning establish program semantics, not persistence evidence.

When authorization is supplied, the interpreter binds only `targetId`,
`collectionName` and `accessKind = delete` to the admitted target. It preserves
all remaining evidence: access mode and model, resource/domain metadata,
source/target component, relation rules, ABAC/natural conditions and visibility.
Normal UserPermission authorization loads the actual raw record, followed by
the Component factory authorization hook, before provider execution. Existing
None authorization remains an internal behavior, not a future public grant.

Only acknowledged Deleted success evicts the actual Component resident entity
and invalidates its ViewSpace. Existing stale/notfound reconciliation evicts
stale resident state without successful-delete View invalidation. Other
prepublication failures preserve appropriate resident/View state. A second
logical or physical observed delete returns structured notfound without replay,
successful receipt or success invalidation.

## R7: Compatibility boundary and remaining obligations

Existing canonical create with absent optional ID continues to use ctx
idGeneration and the assembled INITIAL revision one. A happy-path entity is
loadable/searchable before logical deletion and hidden by normal reads after
deletion. This positive coherence case does not establish creation-failure
safety and changes no creation implementation.

Mandatory P96-07B2B must assess creation failure at root admission, canonical
overflow and commit boundaries, including orphan/eager-effect risk, and freeze
any separately admitted implementation needed. P96-07C must prove registered
ordinary Entity/Aggregate routes, explicit public authorization, committed
outcomes, actual selected contextual reload through
`ActionEngine.afterCommitResponseC`, and preservation of committed outcome
when projection is delayed or fails without replay. B2A alone closes none of
the original Step criteria, independent acceptance, review or release gates.

## Executable specifications

- [EntityObservedDeleteCanonicalSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityObservedDeleteCanonicalSpec.scala): COD-E1–E8, canonical physical/logical policy, strict admission and ordinary create/read coherence.
- [EntityObservedDeleteCanonicalPropertySpec](../../src/test/scala/org/goldenport/cncf/entity/EntityObservedDeleteCanonicalPropertySpec.scala): COD-P1–P3, positive bounded total laws, at least 100 successes and zero discards each.
- [SqliteCanonicalObservedDeleteSpec](../../src/test/scala/org/goldenport/cncf/entity/SqliteCanonicalObservedDeleteSpec.scala): COD-S1–S4, independently reopened file-backed persistence and real native rollback.
- [MysqlCanonicalObservedDeleteAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/entity/MysqlCanonicalObservedDeleteAcceptanceSpec.scala): COD-M1–M3, required positive MySQL 8.4 Testcontainers/JDBC evidence; unavailable live execution is not passing or substituted evidence.
- [UnitOfWorkObservedDeleteSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkObservedDeleteSpec.scala): COD-U1–U6, actual Component resident/View state, UserPermission and typed active-program integration.

Development fixture/sample provenance and unexecuted authoring status belong in
the [journal](../journal/2026/10/2026-10-06-phase-96-entity-observed-delete-canonical.md),
not this normative contract.
