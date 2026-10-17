# Canonical Entity create/delete mutation capture

Document role: normative internal PHASE-96 / P96-07 / P96-07C2B2 contract.
Authoring establishes no validation, review, Step, Phase or release acceptance.
All four original [P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. The [paired design](../design/entity-mutation-capture-outcome.md)
records ownership and compatibility decisions.

## Captured receipts and optional coherence

`private[cncf] final class EntityCreateMutationOutcome[T]` is a non-case class
constructed only by its companion. Its immutable `created: CreateResult[T]`
is the exact canonical result object, and `persisted: EntityRecordSnapshot`
retains the exact `created.record` Record reference and admitted INITIAL revision.
`coherence: Consequence[Unit]` describes optional resident/View work. There is
no public apply/copy, caller-supplied committed flag, writer facade, wire DTO,
postwrite reload used as raw evidence, or enclosing-UnitOfWork commit claim.
Its `toString` is exactly `EntityCreateMutationOutcome`.

The companion's `_from_created_c(expectedcollection, expectedid, created,
binding)(cohere)` requires nonnull collection, Option, binding, representation,
result, ID, Some Record and Fields. The actual ID collection must equal the
selected collection; a supplied expected ID must equal the actual ID. Exactly
one opaque raw ID must correspond through the existing versioned outcome
identity admission. Exactly one selected revision Field must be present and
`binding.revision(record)` must be INITIAL. No ID parsing, normalization,
coercion or field projection participates. Invalid admission returns fixed
redacted `operationInvalid` with `Cause.Kind.Inconsistency` and Reason
`entity-create-mutation-outcome-invalid`, before invoking coherence.

`private[cncf] final class EntityDeleteMutationOutcome` likewise has companion-only
construction, immutable exact `deleted: EntityDeletionResult` and optional
`coherence: Consequence[Unit]`. `_from_deleted_c(expectedid, expectedrevision,
deleted)(cohere)` admits only the exact nonnull selected identity and positive
matched prior revision. It fabricates neither post-delete Record nor revision
successor. Invalid admission returns the same structured category with Reason
`entity-delete-mutation-outcome-invalid`. Its fixed `toString` is exactly
`EntityDeleteMutationOutcome`.

After valid capture, optional coherence runs at most once. A structured Failure
retains its original Conclusion. Null callback, null Consequence, Failure(null)
and directly escaped NonFatal become fixed redacted `operationInvalid` with
Reason `entity-mutation-coherence-invalid`. Unit success is retained. Fatal and
control signals propagate. A coherence Failure remains inside successful outer
capture: it cannot erase the receipt, replay mutation, compensate it, or mark
the whole UnitOfWork committed. Pure factory admission does not prove origin
or persistence; positive runtime evidence must use real canonical results.

## Two opt-in internal UnitOfWork operations

`UnitOfWorkOp.EntityStoreCreateOutcome[T]` retains the typed entity, its existing
`EntityPersistentCreate[T]`, unchanged default `EntityCreateOptions`, and optional
authorization. Shape guards run before dispatch. Canonical authorization runs
before resolving the typed collection and optional identity. The exact registered
managed binding is required from the execution context before writing; absent
or unmanaged binding uses the existing representation Policy, Expected
`embedded-or-detached`, Actual `unmanaged` failure.

The interpreter calls unchanged Space.create through one existing
`EntityStoreCreate` node, captures its actual result, then performs View
invalidation, selected resident eviction, and existing persisted-record optional
put in that order. Missing resident support preserves existing optional-put
semantics. No compensating write follows optional failure.

Only this captured-create route opts into the private `capturecoherence` flag
on persisted-record optional put. A registered decoder's `Failure(null)` passes
unchanged to the existing outer coherence capture before legacy recovery can
replace its malformed boundary; capture redacts it with Reason
`entity-mutation-coherence-invalid`. The private flag defaults to false for
existing persisted/domain put callers. Real structured Conclusions,
NotImplemented suppression and all old API recovery behavior remain unchanged.

`UnitOfWorkOp.EntityStoreDeleteObservedOutcome` carries the typed ID,
`expectedrevision`, and optional authorization. It retains canonical observed-delete
shape admission and collection checking. Authorization rebinds only targetId,
collectionName and accessKind=`delete`; all other evidence survives. The existing
authorization loader and one unchanged Space.deleteObserved call own persistence.
The exact acknowledged deletion result is captured before View invalidation and
resident eviction. Existing stale/notfound reconciliation remains attached to
outer persistence/admission failures; an inner coherence Failure is not a new
persistence failure.

Both nodes are Local for the existing planner and recorder. Free/ConsequenceT
construction is pure. Recording receives actual typed interpreted outcomes and
causes no additional persistence; recording alone proves neither persistence nor
commit. Existing create/delete signatures, defaults, returns, optional-coherence
behavior, Aggregate compensation and enclosing commit behavior retain their
existing contracts.

## Independent executable correspondence

The [registered fixture](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkMutationCaptureFixture.scala)
uses real Component/EntityDescriptor/Realm bindings, normal create defaults,
StandardEntityStore, active UnitOfWork interpretation, observing delegates and
deterministic test identity context. It never repairs or supplies provider results.
Registered decoder and Realm faults are armed only after actual acknowledgements.
The selected root/body, old/unrelated roots/bodies, resident/View state, native
and Space result references, writer attempts and enclosing lifecycle observations
are independent contracts.

Memory expected creation independently enumerates a new immutable Map from
literal input/default values, then appends INITIAL revision and prescribed body
metadata. Actual Field vectors are never collapsed, sorted or subset-compared.
The full seven overflow fields use `content_byte_size`, `content_digest` and
`content_charset`. UTF-8/SHA-256 describes product content integrity only.

SQL fixtures explicitly declare complete root/body columns before action.
Only revision and byte-size columns are integral; other columns are TEXT.
Strict-create state values are `published` and `alive`; SQL logical delete stores
text `3` in those TEXT columns, whereas memory stores numeric 3. Physical permission
remains the literal compact JSON String; logical SQL permission is independently
constructed ordered nested Records. Declared JSON-shaped business examples have
independent Record/Vector expectations. Exact and normalized readers use explicit
alias tables, including `source_entity_id` to `sourceEntityId`. PostgreSQL logical
order is id, selected revision, then metadata order; SQLite retains metadata order.
Actual after state never determines expected values or Field order.

[SQL NULL omission](sql-null-record-boundary.md) remains logical-only: complete
physical columns, types and NULL cells remain in the full physical oracle. The
exact-reader delete guards include deleted_at and deletedAt; normalized schema
uses only the canonical snake column. Physical deletion removes only the selected
root and owned overflow. Logical deletion preserves body and all unrelated state,
updates the specified eight audit/lifecycle fields, and advances the selected
stored revision while its receipt retains the old matched revision.

- [EntityMutationCaptureOutcomeSpec](../../src/test/scala/org/goldenport/cncf/entity/EntityMutationCaptureOutcomeSpec.scala): CMC-01 through CMC-04 and CMC-P1; admission-only receipt/reference, invalid shape, optional failure and total shrinking laws.
- [UnitOfWorkMutationCaptureSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkMutationCaptureSpec.scala): UMC-01 through UMC-06 and UMC-P1; real memory/SQLite persistence, authority, legacy comparison, rollback/ACK, recording and sequential laws.
- [PostgresqlUnitOfWorkMutationCaptureAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/PostgresqlUnitOfWorkMutationCaptureAcceptanceSpec.scala): PG-CMC-01, PG-CMC-02 and PG-CMC-P1; required PostgreSQL 17.11 with fsync/synchronous_commit, both read conventions, full native state and acknowledgement distinctions.

Each total law requires at least 100 successful samples, zero discards and
meaningful lawful shrinking. Missing required native execution is failure,
never cancellation, skipping or fabricated success. These are executable
requirements, not a claim that validation has run.

## Retained enclosing obligations

Ordinary Entity/Aggregate/Display integration, the four versioned UoW outcome
routes, strict ingress/revision transport, whole-commit handling, contextual
Detail/List reload, C3, PG12 and subsequent original P96-07 obligations remain
OPEN. The original protected Step review/commit, one current-epoch full Phase
review, final validation/release and reciprocal aggregate gates remain unchanged.
