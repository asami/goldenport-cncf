# Entity Observed Delete

Status: normative static provider contract.

This contract is the P96-07B1 datastore prerequisite. It extends the
[Entity conflict and conditional transition contract](entity-conflict-and-conditional-transition.md)
without replacing its revision, authorization or canonical Entity rules.
The settled provider responsibilities are described in the
[design](../design/entity-observed-delete.md).

## Scope and typed outcome

`EntityCompareAndDeletePlan` carries one collection, entry identity, admitted
revision field, typed expected `EntityRevision`, root mutation, exclusion
guards and bounded Delete-only side effects. The expected revision is a strict
optimistic precondition; a provider MUST NOT ignore it or obtain a replacement
observation on behalf of the caller.

`EntityDeleteRootMutation` has exactly two policies:

- `Physical` removes the root and may remove the explicitly listed side records.
- `Logical(changes)` applies the caller's server-owned lifecycle patch and
  advances the stored root revision exactly once. It MUST NOT remove overflow
  records or admit any deletion side effect.

The provider returns exactly one authoritative typed result:

- `Deleted(matchedRevision)` records the actual root revision matched inside
  the provider atomic boundary. It is the prior revision for both policies.
- `Stale(expectedRevision, actualRevision)` records the requested observation
  and the different authoritative current root revision, with no mutation.

There is no no-op deletion result, omitted success marker, wire receipt,
fabricated post-delete record or tombstone-next-revision receipt. A successful
logical deletion stores the next revision but returns the matched prior one.

## Compatibility and capability admission

The existing `EntityVersionedMutationDataStore` adds a default
`compareAndDeleteEntity` method returning structured unsupported-capability.
Existing provider implementations remain source-compatible and do not gain
deletion capability merely by implementing the older mutation port.

Both `OptimisticCompareAndDelete` and `AuthoritativeDeleteResult` MUST be
advertised for this operation. `AtomicSideEffects` is additionally required
when side effects are nonempty. The existing `guardedBaseline` is unchanged.
Only the actual implemented memory and SQL providers advertise the new
features. Unsupported providers MUST NOT fall back to generic delete,
guarded update, a shadow store or a successful empty result.

`DataStoreSpace.compareAndDeleteEntity` validates shape before looking up the
root provider, admits capabilities and same-provider side effects, then
invokes that provider exactly once. It normalizes existing provider failures
and validates the returned result without reloading a record. A malformed
plan, unsupported capability or cross-provider effect MUST fail before a
write or effect.

## Plan and result shape

Shared admission MUST reject:

- null plans and mandatory nested collection, identity, revision, mutation,
  guard or effect references;
- blank or invalid collection, entry, revision, guard or patch identities;
- unsupported guard values and malformed nested effects;
- empty or duplicate logical patch keys, and logical patches of root `id` or
  the admitted managed revision field;
- logical deletion with any side effect;
- more than 64 side effects, duplicate side-effect targets or the root as a
  side-effect target.

SQL normalized-column admission additionally rejects managed revision or
identity aliases that would target protected physical columns. Schema
preparation does not initialize an absent revision or make an invalid record
revision-managed.

Returned revisions MUST be nonnull. `Deleted.matchedRevision` MUST equal the
requested revision. A returned `Stale.expectedRevision` MUST equal the
requested revision and its `actualRevision` MUST differ. A null result or an
inconsistent result is a structured invalid-authoritative-result failure,
never a receipt synthesized from the plan.

## Existing root admission

The root and its actual revision are read inside the provider's existing
atomic boundary. Existing exclusion admission makes absent, hidden or
already-deleted roots unavailable. Missing or invalid managed revisions fail
structurally; no revision zero or implicit revision initialization is allowed.

A different actual revision produces `Stale` without changing the root,
revision or side records. A matching root MUST also pass `actual.nextC`, even
for physical deletion. A root at `Long.MaxValue` therefore cannot be deleted
through this strict operation. Exhaustion fails before any write.

## Provider atomicity

The memory provider uses its existing synchronized boundary, also shared by
ordinary CRUD and search. It stages the root and every selected side
collection, runs the existing `RootPrepared`, `SideEffectsPrepared` and
`BeforePublish` checkpoints, and publishes the collection replacement once.
Any failure before publication leaves every stored root and side record
unchanged. No caller-owned lock or separately visible map is involved.

The SQL provider uses its existing connection and native mutation transaction.
MySQL locks the selected root with `FOR UPDATE`; SQLite retains its configured
`IMMEDIATE` transaction. Physical deletion uses a parameterized root DELETE
qualified by identity, expected revision, exclusion guards and the dialect's
revision validity/range predicates. Logical deletion uses the existing
qualified native update and native record merge. Exactly one affected root
row is required.

For zero affected rows, the provider reads the actual root on the same
transaction connection. It diagnoses absence/exclusion, invalid revision or
a genuinely different current revision. An unchanged matching revision with
zero affected rows is a structured provider failure, not success or invented
staleness. A root mutation affecting more than one row also fails.

Only after the qualified root mutation succeeds may SQL apply Delete-only
side effects on that same connection. The existing three checkpoints precede
commit. Root, effects and commit form one provider mutation: injected root,
side-effect, precommit or rejected-commit failures roll back the data changes.
`Deleted` becomes observable only after the existing commit path succeeds.
Connection/pool ownership and borrowed-resource semantics remain unchanged.
MySQL schema preparation stays outside this data transaction; this contract
does not claim transactional DDL rollback.

## Canonical caller responsibility

This is a datastore provider contract, not an Entity lifecycle or Display
mutation route. A later canonical Entity caller remains responsible for
admitted storage identity and representation, authorization, lifecycle
policy and server-generated logical changes, overflow planning, UnitOfWork
execution, resident reconciliation, View invalidation and committed outcome
handling. A Display binding MUST NOT use this provider API as a persistence
shortcut.

P96-07B2 retains canonical typed Entity/UoW observed deletion and positive
create/delete lifecycle obligations. P96-07C retains registered ordinary
Entity/Aggregate mutation routes and contextual postcommit reload through
`ActionEngine.afterCommitResponseC`. The original four P96-07 checklist
criteria remain OPEN: canonical delegation, update-first Detail/Edit/Save/
refreshed Detail, positive create/delete projection paths, and unchanged
failure plus committed-outcome preservation under projection failure.
Provider readiness alone closes none of these criteria.

## Executable evidence

The shared [real-provider fixture](../../src/test/scala/org/goldenport/cncf/datastore/EntityObservedDeleteFixture.scala)
uses actual provider state, bounded concurrent callers and independent JDBC
reopen reads. Recording providers are restricted to routing/capability/result
admission scenarios. A named scenario is evidence only after it executes and
passes; source presence or cancellation is not proof.

| Suite | Required behavior |
| --- | --- |
| [EntityObservedDeleteProviderSpec](../../src/test/scala/org/goldenport/cncf/datastore/EntityObservedDeleteProviderSpec.scala) | OD-E1 physical root/effects; E2 logical prior/next revision and preservation; E3 intervening actual CAS then stale; E4 record/shape admission; E5 capabilities, provider domain and result rejection; E6 checkpoint atomicity; E7 genuine concurrent winner and ordinary CRUD/search monitor sharing. |
| [SqliteEntityObservedDeleteSpec](../../src/test/scala/org/goldenport/cncf/datastore/SqliteEntityObservedDeleteSpec.scala) | OD-S1 physical independent reopen; S2 logical independent reopen; S3 stale/revision/exclusion/shape preservation; S4 native rollback; S5 independent-datasource race; S6 normalized protection, executed guarded SQL and actual zero-row diagnosis. |
| [EntityObservedDeletePropertySpec](../../src/test/scala/org/goldenport/cncf/datastore/EntityObservedDeletePropertySpec.scala) | OD-P1 generated policy/revision/effect semantics; P2 generated stale preservation; P3 generated selected-target scope. Every law requires at least 100 successful cases and zero discards. |
| [MysqlEntityObservedDeleteAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/datastore/MysqlEntityObservedDeleteAcceptanceSpec.scala) | OD-M1 physical visibility; M2 logical lifecycle/revision; M3 revision/exclusion/shape matrix; M4 root-checkpoint rollback; M5 side/precommit/commit rollback; M6 independent native concurrent winner. |

Positive live MySQL 8.4 evidence is required for provider readiness. The
Testcontainers suite follows `CNCF_LIVE_MYSQL_TEST=true`; without opt-in it
cancels explicitly. Canceled or ignored required scenarios, Docker
unavailability, SQLite substitution or mocked MySQL are not acceptance.
Existing native/versioned/conditional mutation, SQL lifecycle and UoW parity
regressions remain part of the parent-selected accumulator.
