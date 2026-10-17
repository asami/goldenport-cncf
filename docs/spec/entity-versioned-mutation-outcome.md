# Entity versioned mutation outcome

Document role: normative internal PHASE-96 / P96-07 / P96-07C2A kernel contract.
The provider-result/projection boundary is settled. Authoring supplies no test,
review, Step or Phase acceptance. The four original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN.

The [design](../design/entity-versioned-mutation-outcome.md) records ownership
and compatibility decisions. Existing [revision and mutation semantics](entity-conflict-and-conditional-transition.md),
[Aggregate observed snapshot](aggregate-observed-snapshot.md),
[Display Mutation](display-mutation.md), [Display update program](display-mutation-bridge.md)
and [required PostgreSQL persistence](postgresql-phase-96-persistence.md)
retain their policies and enclosing lifecycle obligations.

## VO1: Internal raw outcome

`private[cncf] final class EntityVersionedMutationOutcome[+A]` has
companion-only construction (`private[EntityVersionedMutationOutcome]`) and
immutable `id: EntityId`, `persisted: EntityRecordSnapshot`, `applied: Boolean`
and `projection: Consequence[A]`. It is not a case class and exposes no
`apply`/`copy`, writer, Record facade, Presentable or serialized carrier.
Its `toString` is exactly `EntityVersionedMutationOutcome` without data or error
details.

The companion owns:

```scala
private[entity] def _from_provider_c[A](id: EntityId,
  result: EntityVersionedMutationResult, binding: EntityRevisionBinding
)(project: (Record, EntityRevision) => Consequence[A]):
  Consequence[EntityVersionedMutationOutcome[A]]
```

Nullable boundary shapes reject before projection. Stale returns the existing
`EntityConcurrencyMetadata.staleMutation(expected, actual)` with its exact
facets and zero callback invocations. Applied and NoOp admit only their supplied
actual Record and set `applied` to true and false respectively.

Raw identity admission requires exactly one `id` Field with one exact String
equal to `DataStore.EntryId(id).print`, the exact typed EntityId, or the matching
DataStore.EntryId. No ID parsing, owner inference, Field collapse or value
normalization occurs. Revision is obtained only through `binding.revision`.
Null/missing/duplicate/foreign identity, unsupported/missing/malformed revision,
or NonFatal metadata admission failure returns fixed redacted `operationInvalid`
with `Cause.Kind.Inconsistency` and Reason `entity-mutation-outcome-invalid`.
No original input or exception text is retained.

Before hydration or domain decoding, `persisted` captures the exact complete
raw Record reference and its admitted actual revision. Every ordered non-NULL
SQL Field, security, audit and overflow pointer survives unchanged. SQL NULL
attributes are absent in the provider Record under the
[SQL NULL readback contract](sql-null-record-boundary.md); physical columns and
NULL cells remain complete preservation oracles. Supplied invalid/legacy
raw-null inputs retain their existing contracts. Detached
`cncf_revision` remains in this raw snapshot. The raw snapshot is not the
hydrated domain response. No readback, revision successor or provider call is
performed by the factory.

Projection runs exactly once under NonFatal containment. Structured Failure
is preserved. Null Consequence, Success(null) or directly thrown NonFatal
becomes fixed redacted `operationInvalid` with `Cause.Kind.Inconsistency` and
Reason `entity-mutation-projection-invalid`, without retaining the throwable.
Fatal/control signals, including InterruptedException, propagate. Applied
projection Failure uses existing `committedProjectionFailure`; NoOp Failure
remains unmarked. Valid persistence returns Success(outcome) even when its
projection is Failure. Invalid persistence supplies no outcome.

## VO2: Canonical projection helpers and legacy parity

`StandardEntityRevisionPart` supplies four private entity helpers:

| Helper | Projection | Lawful representation |
| --- | --- | --- |
| `_typed_snapshot_outcome_c[T]` | EntitySnapshot[T] | Embedded |
| `_typed_detached_outcome_c[T]` | EntityRevisionCarrier[T] | Detached |
| `_record_snapshot_outcome_c` | EntityRecordSnapshot | Embedded |
| `_record_detached_outcome_c` | EntityRevisionCarrier[Record] | Detached |

Each delegates raw capture to the factory and uses the corresponding extracted
legacy hydration/admission/decode expression. A successful projection requires
nonnull carrier and entity/Record, exactly the captured revision and selected
identity. Typed identity is checked through `persistent.id`; Record identity
uses the same exact admission as raw capture. Mismatch is projection-invalid
and retains the raw outcome. Domain Records are not compared to unhydrated raw
Records and no domain normalization is added.

The existing `_typed_snapshot`, `_typed_detached_carrier`, `_record_snapshot`
and `_record_detached_carrier` retain signatures, evaluation order, structured
failure wrapping and direct exception/null semantics. They call the extracted
expressions directly, without the new containing factory. Applied retains the
committed marker, NoOp the exact original Conclusion and Stale its canonical
facets. Provider planning and other mutation helpers retain their behavior.

NoOp structured projection Failure retains the original Conclusion itself.
Applied and stale helpers can construct fresh wrappers whose observation
timestamps differ between independent calls. Correspondence requires each
timestamp to be present and compares all other observation data using the
expected wrapper's timestamp; status, interpretation, disposition, previous
Conclusion and its exact reference remain required.

## VO3–VO4: Executable correspondence

The [fixture](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationOutcomeFixture.scala)
reuses the canonical Aggregate Root, fixture and MemoryStore. Candidates use
the real prior root, existing complement, ContentBodyStoragePolicy planning
and `StandardEntityStore._mutate_versioned`, with strict Optimistic,
WriteIfChanged, ObservedRequired and observed policy. Positive cases do not
fabricate provider results. Selected/unrelated root/body Records and independent
SQL physical snapshots are complete preservation oracles; SQL NULL is absent
logically and present physically. Supplied legacy nullable Field vectors are
compared without pretty-rendering NULL. Complete ordered raw
Fields, complete retained Fields and complete physical table/column/ordered-row
maps remain equality predicates. Diagnostic clues identify only table addresses,
column vectors, row counts and differing Field/cell keys or indices, without
rendering nullable values. Changed audit correspondence derives exactly one
`updated_at`/`updatedAt` and `updated_by`/`updatedBy` key from the independent
prior Record and checks the updated values using those exact keys; it does not
normalize or rename the observed raw Fields.

The [portable spec](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationOutcomeSpec.scala)
covers actual memory and task-private factory-owned SQLite Applied/current NoOp,
stale equal/different input, maximum current NoOp, four lawful projections,
postprovider typed domain faults, metadata negatives and legacy parity.
Pure tampered-record/callback and visibility negatives supply no runtime
acceptance credit. Two total portable laws span both representations, with
meaningful nonempty multilingual values, varied positive revisions and
changed/current/stale/failure modes. Each requires passed, at least 100
successes and zero discards with strictly reducing lawful shrinking.

The [native spec](../../src/test/scala/org/goldenport/cncf/entity/PostgresqlEntityVersionedMutationOutcomeAcceptanceSpec.scala)
requires real PostgreSQL 17.11 with fsync and synchronous_commit, independent
owned pools/readers, both representations and complete physical preservation.
The unchanged acknowledgment fixture provides Normal, Reject, Indeterminate,
Throw and Null commit modes. Regular native writers and independent readers
retain `normalizeColumns = true`. The acknowledgment writer retains its
existing default false convention, and only its independent acknowledgment
scenario reader uses that same default convention. Physical SQL columns and
the complete raw equality predicates remain unchanged. Actual mutation flatMaps into outcome consumption:
known rejection preserves rows; unknown ACK after real commit leaves durable
changes but no raw success/outcome or projection callback. Each makes one
commit attempt with no replay. One sequential native total law reuses bounded
rows/pools across both representations and requires the same 100/zero gate.
Disabled or unavailable native execution supplies no acceptance.

### Actual postprovider body-read semantics

[ContentBodyStoragePolicy](../../src/main/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicy.scala)
reads the explicit existing pairs `content_storage`/`contentStorage`,
`content_ref`/`contentRef` and `content_charset`/`contentCharset`. The first
nonempty trimmed snake spelling takes precedence, followed by the camel
spelling; charset additionally retains its existing `charset` fallback.
Payload text retains its exact nonblank whitespace. Hydration reads the exact
selected reference and replaces only logical content placeholders when that
body exists, retaining all other ordered Fields, security and
audit. SQL NULL attributes are already absent at readback; pure supplied
legacy raw-null ContentBody inputs retain their compatibility policy.
Missing-content overflow preservation retains the complete input Record
with empty side intent. Charset resolution determines encoded bytes, digest
and inline/overflow threshold while writes continue to emit canonical snake
metadata. No generic field normalization, new write/address or schema policy
is introduced. The [body executable specification](../../src/test/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicySpec.scala)
supplies real-memory exact-reference, absent-body, preservation, charset and
precedence scenarios alongside the retained payload and metadata laws.

MissingBody deliberately deletes only the selected owned overflow row after
actual acknowledged Applied or current NoOp. `ContentBodyStoragePolicy.hydrate`
returns the unchanged raw Record when the body is absent; the reused
identity-only decoder accepts it. All four lawful helpers therefore retain
projection Success on memory, SQLite and PostgreSQL. This contract introduces
no body-corruption policy.

ClosedOwnedSqlReader closes only the factory-owned SQLite/native writer after
ACK and independent full observation. The same original context then reaches
the closed managed resource during actual overflow hydration, producing read
Failure. All four helpers retain Success(outcome) with raw data, actual
revision/applied flag and projection Failure. Separate live readers prove
postfault selected/unrelated/body/physical state unchanged. Overflow markers
are required before close; inline bypass, injected non-owning close, changed
context and simulated errors do not satisfy this case. Cleanup close is
idempotent.

## VO5: Enclosing boundaries remain required

This carrier describes supplied admitted raw provider data. It is not a
producer-provenance certificate, whole-UnitOfWork committed receipt, public
wire DTO or actual Display reload. Later canonical integration must establish
runtime origin and the enclosing commit/response boundary.

There is no new schema/version, route, write, transaction, authorization,
security or equality policy. Original C2B/C3 create/delete, ordinary Operations,
strict raw HTTP ingress, commit/contextual reload and PG12 obligations remain
mandatory. All four P96-07 criteria remain OPEN; this kernel closes no Step,
Phase, release or successor gate.
