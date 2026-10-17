# UnitOfWork versioned mutation capture

Document role: internal PHASE-96 / P96-07 / P96-07C2B3 contract.
The [design](../design/unitofwork-versioned-mutation-capture.md) defines ownership.
This contract extends the existing [versioned outcome](entity-versioned-mutation-outcome.md)
and [producer ports](entity-versioned-mutation-port.md), preserving their policies,
failure vocabulary and projection semantics. It does not close any original
[P96-07 criterion](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime).

## Three independently observable results

`private[cncf] final class EntityVersionedMutationCaptureOutcome[+A]` has only
immutable `mutation: EntityVersionedMutationOutcome[A]` and
`coherence: Consequence[Unit]`. Construction is companion-only. This is a
non-case class with no public factory, copy, writer, serialization or committed
flag. Its `toString` is exactly `EntityVersionedMutationCaptureOutcome`.

The companion `_from_outcome_c(expectedid, outcome)(cohere)` admits the
selected nonnull identity/collection, the exact matching outcome identity and
collection, nonnull persisted snapshot/Record/Fields/revision, and a nonnull
projection excluding `Success(null)` and `Failure(null)`. Admission performs
no read, hydration, decoding, revision successor calculation or reconstruction.
Invalid input returns operation-invalid with operation
`entity-versioned-mutation-capture`, cause Inconsistency and reason
`entity-versioned-mutation-capture-invalid`, without invoking the callback or
rendering inputs/exceptions.

After admission the capture retains the exact outcome object, its actual
Applied/NoOp flag, persisted Record and projection Consequence/Conclusion.
Projection Failure is an admitted result. The existing `EntityMutationCoherence`
boundary invokes the callback once and retains a real structured Conclusion
by reference. Null callback, null result, `Failure(null)` and thrown NonFatal
produce the existing fixed `entity-mutation-coherence-invalid` failure.
InterruptedException, ControlThrowable and fatal errors propagate.

Raw persistence acknowledgement, domain projection and local cache coherence
are distinct. A successful capture can contain either or both projection and
coherence Failure. None is an enclosing UnitOfWork commit receipt. The existing
Applied projection wrapper's reason `committed-entity-projection-failure`
retains its original provider/projection meaning and previous Conclusion;
it is not evidence that an enclosing operation committed.

## Four internal operation nodes

All four nodes are `private[cncf]`, have no defaults or public overloads and
classify as Local. Existing nodes and the earlier create/delete capture nodes
retain their fields, defaults, bodies and result/exception behavior.

| Node | Canonical Space port | Projection / optional residency put |
| --- | --- | --- |
| `EntityStoreSaveSnapshotOutcome[T]` | `_save_snapshot_outcome_c` | `EntitySnapshot[T]` / its entity with the supplied persistent |
| `EntityStoreSaveDetachedOutcome[T]` | `_save_detached_outcome_c` | `EntityRevisionCarrier[T]` / its entity with the supplied persistent |
| `EntityStoreUpdateRecordSnapshotOutcome[P]` | `_update_record_snapshot_outcome_c` | `EntityRecordSnapshot` / its actual hydrated Record through the persisted-record decoder |
| `EntityStoreUpdateRecordDetachedOutcome[P]` | `_update_record_detached_outcome_c` | `EntityRevisionCarrier[Record]` / its revision-free domain Record through the domain-record decoder |

Typed nodes carry entity, optional expected revision, persistent, optional
authorization and execution policy. Patch nodes additionally carry the selected
id and patch instead of entity. No variant calls a legacy fallback writer.

## Admission, authorization and transitions

Entity/patch and persistent must be nonnull before access. Typed identity
selection uses `Consequence(persistent.id(entity))`. The existing
`EntityStore._admit_versioned_outcome_c` shape gate admits identity/collection,
Option and policy shapes before collection lookup, authorization or mutation.
Null authorization Option and `Some(null)` fail before effects. Raw policy
shapes are admitted here; semantic policy validation follows canonical effective
policy assembly, preserving raw None under an assembled Optimistic descriptor.

Authorization Some is copied with only targetId and collectionName rebound to
the actual selected identity and collection. Aggregate family/command access,
resource type, components, access policy/mode, operation model, relations,
natural conditions and every other field remain intact. None remains None.
An unrelated supplied target cannot redirect the authorization record loader.

Typed flows mirror Save and SaveDetached. A non-noop transition hook loads
current once, authorizes against the corresponding authorization Record, and
uses current-aware beforeSave or the existing missing-current overload. With
noop the existing lazy authorization loader and simple beforeSave remain.
Patch nodes use the existing authorize-and-validate-patch flow and matching
observed/detached producer. Exact collection resolution remains as in these
owners; typed save gains no eager name-based lookup.

## Post-acknowledgement coherence and reconciliation

After the matching Space port returns, the companion retains that same outcome.
Its single callback invalidates all current Component Views, evicts the selected
identity, then optionally puts the successful projected value. Projection
Failure still invalidates and evicts but skips every put/decode. Record nodes
opt into coherence containment while existing callers retain the default-false
helper behavior, including NotImplemented recovery.

Realm removal failure precedes decoding/put. It is captured separately without
discarding the acknowledged mutation or projection. Record residency decoding
failure likewise remains in coherence; a real structured Conclusion retains
identity, and malformed/NonFatal failures use the fixed redacted boundary.
The record-detached node must not treat its domain Record as a raw persisted
Record or require a managed revision that the projection intentionally removed.

Outer stale/notfound/pre-outcome failures use existing versioned reconciliation.
Retained projection Failure is never unwrapped and reconciled as an outer
failure. No node commits, aborts, reloads after a receipt, retries, compensates
or introduces an event checkpoint. Pending events follow the actual Save,
SaveDetached and observed/detached patch owners. The special legacy typed
UpdateDetached restore policy remains unrelated and unchanged.

## Executable specification correspondence

The paired executable specifications are
[UnitOfWorkVersionedOutcomeSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedOutcomeSpec.scala)
and mandatory native
[PostgresqlUnitOfWorkVersionedOutcomeAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/PostgresqlUnitOfWorkVersionedOutcomeAcceptanceSpec.scala).
Their shared object-owned
[UnitOfWorkVersionedOutcomeFixture](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedOutcomeFixture.scala)
composes the unchanged provider fixture and installs a fresh matching registered
persistent, Component, Realm, memory realm and shared active UnitOfWork.

| Requirement | Observable contract |
| --- | --- |
| UVO-01 | All four Applied/current NoOp routes; exact Space/outcome/provider Record references, full state, canonical hydrated projection, actual resident/View behavior and zero enclosing lifecycle. |
| UVO-02 | Stale equal/changed input before equality, inconsistent Embedded input and current maximum NoOp without a successor. |
| UVO-03 | Every typed projection mode with independent legacy active owners; all four real SQLite/PostgreSQL owned-reader closures after ACK. |
| UVO-04 | Real post-ACK Realm failures, all six record decoder modes, skipped puts after projection Failure, direct companion admission/callback/control boundaries. |
| UVO-05 | Actual access/factory denial, selected-id rebinding, current/simple hook and event-owner parity, invalid shapes/bindings/providers/capabilities and effective-policy parity. |
| UVO-06 | Patch Direct/CAS policy selection and complete guard-schema correspondence; effect-free Local Free planning/typed recording. |
| UVO-07 | Actual SQLite known rejection and PostgreSQL Normal/Reject/Indeterminate/Throw/Null commit acknowledgements, one attempt and no replay. |
| UVO-P1 | One sequential bounded total law for each memory/SQLite/PostgreSQL backend; at least 100 successes and zero discards, meaningful shrinking over every lawful input/fault dimension. |

Strict Optimistic + WriteIfChanged + ObservedRequired selects Versioned for all
four variants. Patch AlwaysWrite + Optimistic + ObservedRequired selects CAS.
Patch assembled None + AlwaysWrite + Managed, with no observed or expected
revision, selects Direct. Managed is the existing non-observed policy; no
Optional enum or production policy change is introduced.

Expected complete ordered logical Fields and complete physical tables/column
types/rows are prescribed before action from the input/backend/policy and
baseline. Expected primitive/order/values are not derived from actual plans,
Applied flags or result Records. SQL NULL attributes remain omitted under the
[SQL NULL boundary](sql-null-record-boundary.md). Direct/CAS may append only
the existing nullable deletion guard, including physical null cells on every
existing root row. All title-only scenarios preserve body, unrelated rows,
security, identity and other business/audit data except the explicitly owned
title/revision/update metadata and patch trace/correlation metadata.

Authorship supplies no passing evidence. Parent-owned representative14 and
accumulator82 validation, independent whole-ten assurance and original formal
acceptance gates remain required. All four P96-07 criteria remain OPEN:
ordinary registered Entity/Aggregate and Display mutation/transport integration,
enclosing committed termination/result, contextual afterCommitResponseC
Detail/List reload/C3, and positive lifecycle/committed pending/failure/no-replay
native PG-E12 obligations. Original P96-06 exact commit, P96-07 protected Step
review/commit, P96-08..11, sole current-epoch full Phase review, final validation,
release/ledger and reciprocal aggregate obligations are unchanged.
