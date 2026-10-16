# Entity versioned mutation producer ports

Document role: normative internal PHASE-96 / P96-07 / P96-07C2B1 contract.
Status: source and executable specifications authored; parent validation and
independent observation pending. All four original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. This producer boundary supplies no Step or Phase acceptance.

The [paired design](../design/entity-versioned-mutation-port.md) owns the stable
responsibility decisions. The [C2A outcome contract](entity-versioned-mutation-outcome.md),
[Display update program](display-mutation-bridge.md), and
[required native persistence](postgresql-phase-96-persistence.md) retain their
original semantics and enclosing integration obligations.

## VP1: Four additive internal ports

`EntityStore` provides these concrete `private[cncf]` methods without defaults
or new abstract requirements on existing stores:

```scala
_save_snapshot_outcome_c[T](
  entity: T, expectedrevision: Option[EntityRevision],
  executionpolicy: EntityMutationExecutionPolicy
)(using tc: EntityPersistent[T], ctx: ExecutionContext):
  Consequence[EntityVersionedMutationOutcome[EntitySnapshot[T]]]

_save_detached_outcome_c[T](
  entity: T, expectedrevision: Option[EntityRevision],
  executionpolicy: EntityMutationExecutionPolicy
)(using tc: EntityPersistent[T], ctx: ExecutionContext):
  Consequence[EntityVersionedMutationOutcome[EntityRevisionCarrier[T]]]

_update_record_snapshot_outcome_c[P](
  id: EntityId, patch: P, expectedrevision: Option[EntityRevision],
  executionpolicy: EntityMutationExecutionPolicy
)(using tc: EntityPersistentUpdate[P], ctx: ExecutionContext):
  Consequence[EntityVersionedMutationOutcome[EntityRecordSnapshot]]

_update_record_detached_outcome_c[P](
  id: EntityId, patch: P, expectedrevision: Option[EntityRevision],
  executionpolicy: EntityMutationExecutionPolicy
)(using tc: EntityPersistentUpdate[P], ctx: ExecutionContext):
  Consequence[EntityVersionedMutationOutcome[EntityRevisionCarrier[Record]]]
```

The four base defaults use the single private
`_unsupported_versioned_outcome[A]` helper. It returns existing
`Consequence.operationInvalid("entity-versioned-mutation", facets)` with
Reason `unsupported-capability` and Capability
`entitystore.versioned-mutation-outcome`. Defaults invoke no old method,
provider, readback or projection. Existing custom/Noop stores therefore need
no added override and cannot silently fall back to legacy writes.

### Input shapes before effects

Standard and Space typed save ports reject null entity or persistent before
accessing either. Patch ports likewise reject null patch or persistent. Typed
save obtains the opaque identity through the existing `Consequence(tc.id(entity))`
boundary. The shared `EntityStore._admit_versioned_outcome_c` requires nonnull
id, id.collection, expectedrevision Option, executionpolicy, its concurrency,
write and precondition enums, and its observedRevision Option. Null shapes use
the existing `argumentMissing`; `Some(null)` in either revision Option uses
`argumentInvalid`. In particular a null collection returns exactly
`Consequence.argumentMissing("id.collection")` before lookup or effects.

This guard validates shapes only. It MUST NOT call semantic `validateC` on the
raw attempt policy. The existing canonical result helper first chooses the
effective assembled/attempt concurrency policy; existing semantic validation
then runs in its original location and order. A registered Optimistic
collection can therefore accept raw concurrency None with WriteIfChanged,
ObservedRequired and Some(observed), exactly as its legacy mirror does.
Invalid effective None+ObservedRequired and missing required observation
remain existing structured rejections. No identity grammar, stronger strict
policy, default or fallback is introduced.

## VP2: Canonical producer and output-before-projection correspondence

The Standard save owner selects the required managed representation and calls
its existing `_save_managed_result` once with unchanged entity,
expectedrevision, executionpolicy and `concurrencyoverride = None`. The update
owner does the same through `_update_by_id_managed_result` with the typed patch
and supplied identity. Unmanaged or wrong-representation bindings reject
through the existing required-binding contract.

| Port | Required representation | Existing projection helper |
| --- | --- | --- |
| `_save_snapshot_outcome_c` | Embedded | `_typed_snapshot_outcome_c` |
| `_save_detached_outcome_c` | Detached | `_typed_detached_outcome_c` |
| `_update_record_snapshot_outcome_c` | Embedded | `_record_snapshot_outcome_c` |
| `_update_record_detached_outcome_c` | Detached | `_record_detached_outcome_c` |

The actual existing producer selects guarded versioned/direct/CAS execution
using the actual provider capabilities. It returns a real result before the
matching C2A helper captures the complete raw Record reference and actual
branch/revision, then hydrates and projects. Record return types do not create
a Record writer API: patch input remains P with `EntityPersistentUpdate[P]`.
No second mutation, generic CRUD fallback, postwrite reload used as raw outcome,
fabricated Applied/NoOp, guessed revision successor or callback executor is
permitted. Stale admission precedes equality, including equal business input;
current maximum-revision NoOp succeeds without requiring a successor.

Applied projection failure retains the existing committed-projection wrapper
and original previous Conclusion; NoOp retains its original Conclusion.
Structured failure, NonFatal containment/redaction and fatal/control propagation
remain defined by C2A. Persistence failure or unknown ACK cannot become an
outcome. Hydrated domain data remains distinct from the exact raw ordered
non-NULL SQL Fields, body pointers, audit and security values. The
[SQL NULL boundary](sql-null-record-boundary.md) omits readback attributes while
retaining complete physical columns/NULL cells. Raw capture preserves that
actual provider Record reference without adding logical NULL Fields.

Every legacy API body, signature, default, typeclass requirement, policy and
direct projection exception remains unchanged. Legacy callers continue through
their original projection boundary, independently of the containing factory.

## VP3: Canonical Space routing

`EntityStoreSpace` supplies the same four `private[cncf]` names and result
types. Save arguments are `(entity: T, persistent: EntityPersistent[T],
expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)`.
Patch arguments are `(id: EntityId, patch: P,
persistent: EntityPersistentUpdate[P], expectedrevision: Option[EntityRevision],
executionpolicy: EntityMutationExecutionPolicy)`. Each uses only
`ctx: ExecutionContext`, with no defaults.

After shape admission each wrapper binds the supplied typeclass, resolves
exactly one store through existing `_by_collection`, invokes the matching
store port once, and returns its carrier unchanged. Existing `_with_calltree`
labels are `space:entitystore:save-snapshot-outcome`,
`space:entitystore:save-detached-outcome`,
`space:entitystore:update-record-snapshot-outcome`, and
`space:entitystore:update-record-detached-outcome`, with existing collection
and identity attributes. Missing-provider failure remains structured and
canonical. Space performs no re-decoding, reload, authorization grant or
UnitOfWork commit.

## VP4: Executable behavioral correspondence

The [port fixture](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationPortFixture.scala)
reuses the lawful Aggregate Root/collection/identity, C2A Probe and full rows
and physical oracles. Typed inputs are Root or object-owned title Patch.
Its recording DataStore delegates actual capabilities, generic CRUD,
direct/CAS/versioned/delete operations and prepare/commit/abort unchanged.
Captured Versioned/Native alternatives retain only real returned results.
They compare the actual branch/readback Record and exact object reference,
without manufacturing a provider result for the oracle.

The [portable specification](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationPortSpec.scala)
and [required native specification](../../src/test/scala/org/goldenport/cncf/entity/PostgresqlEntityVersionedMutationPortAcceptanceSpec.scala)
define the following matrix. Each action calls the actual new producer port.

| Scenario | Required correspondence |
| --- | --- |
| VP-E1 | All four direct Standard and Space ports on memory/SQLite, all four Space ports on native PostgreSQL; changed/current equal actual branch, identity, revision, projection, complete raw/physical state and one real selected primitive with zero generic fallback. Native patch direct/CAS retains actual capabilities and authoritative readback. |
| VP-E2 | Representation-correct stale equal/different input preserves complete rows and physical state with zero projection; embedded stale input equals its supplied expected revision. Separately inconsistent embedded input fails before effects. Current maximum NoOp requires no nextC. |
| VP-E3 | Actual postprovider typed Good/structured Failure/Throw/null Consequence/null entity/foreign identity/wrong revision faults retain raw persistence before projection. Separate freshly seeded legacy sessions retain original structured Failure and direct throw semantics. Exact NoOp Conclusion, Applied previous and redaction/control behavior remain. |
| VP-E4 | SQLite and native PostgreSQL, all four Space ports, Changed/NoOp: the real observer closes the factory-owned writer only after known successful primitive ACK. Same-context overflow hydration produces literal System/Corrupted, exact IllegalStateException and fixed managed-resource closed message, direct or Applied previous; independent live reader proves full unchanged postfault persistence with one attempt. |
| VP-E5 | Native PostgreSQL all four Space ports use unchanged Normal/Reject/Indeterminate/Throw/Null ACK fixture. Reject preserves full state. Unknown ACK following real commit preserves durable changed state, one primitive/commit attempt and zero outcome/projection/replay. Normal retains its actual result. |
| VP-E6 | All four inherited defaults return exact structured unsupported capability without invoking old unimplemented methods. Null shape matrix covers all four methods through Direct and Space; null collection uses the exact argumentMissing oracle with zero effects. Existing unmanaged/wrong-binding rejection remains. |
| VP-E7 | All four ports preserve raw None attempt/effective Optimistic parity with separately seeded legacy sessions and actual plans/full state. Invalid effective policy/missing observation retains exact existing structured failure before writes. |
| VP-P1 | One total memory law, one SQLite law and one mandatory native PostgreSQL law reuse bounded rows/pools sequentially across all four ports. Each sample executes one new Space port with independently prescribed full state, raw reference, projection and counter expectations. |

The null-collection fixture uses an object-owned concrete EntityId subtype
with valid canonical super arguments and deliberately overridden null collection
accessor. It does not change upstream identity construction or use reflection,
Unsafe, mocks, field mutation or constructor bypass.

Native execution requires PostgreSQL 17.11, fsync and synchronous_commit enabled
through the existing mandatory fixture; disabled/unavailable execution supplies
no acceptance. Regular factory-owned writer/reader pools use
`normalizeColumns = true`. The unchanged acknowledgment writer and its
independent reader retain their existing default false convention.
Full ordered non-NULL SQL Fields, absent NULL attributes, security/audit/body/unrelated state and
complete physical table/column/ordered-row maps remain exact equality oracles.
Native direct/CAS preparation may append the physical deleted_at guard column
and NULL cells to every existing root row. Both SQLite and PostgreSQL leave
the prior logical rows unchanged and require absent deletedAt/deleted_at;
complete physical schema and all rows retain the exact appended guard and
NULL values. Supplied invalid boundary nulls and legacy ContentBody inputs
remain their existing contracts.

Every total property uses nonempty ASCII/Japanese/Greek title and retained
values, positive revisions 2..50, representation-corresponding current,
changed, stale and typed projection-fault modes, and strictly reducing
nonempty text/revision/mode shrinking. Each requires passed, succeeded at
least 100 and discarded zero, with no implication, filtering or synthetic
provider success. Deterministic cases establish all variant/mode branches
alongside generated selection. Authoring is not an executed property result.

## VP5: Enclosing integration remains required

These internal outputs establish the canonical producer seam for later
ordinary mutation integration. They grant no authentication or authorization,
make no whole-UnitOfWork commit claim and expose no transport DTO, route,
CLI/MCP/SPI contract, CML or public ABI. Existing writer, body, identity,
revision and provider policies retain their owners.

Original C2B still requires additive canonical create/delete capture before
residency; Entity AND Aggregate authorization/domain/alias correspondence;
ordinary registered Display mutation/descriptor Operations; strict raw JSON
and ordered physical headers; and CLI/MCP/SPI revision representation.
Original C3 still requires actual enclosing commit and selected contextual
`afterCommitResponseC` Detail/List reload, positive create/delete lifecycle,
committed pending/failure and no replay on native PostgreSQL PG12. All four
P96-07 criteria remain OPEN. P96-08..P96-11, protected Step acceptance/commits,
the sole full Phase review for epoch 1, final validation/release/ledger and
the reciprocal aggregate handoff retain their original obligations.
