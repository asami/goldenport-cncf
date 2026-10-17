# UnitOfWork versioned mutation capture ownership

Document role: settled internal P96-07C2B3 design. The paired
[specification](../spec/unitofwork-versioned-mutation-capture.md) owns behavior.
The original [proposal](../notes/phase-96-versioned-uow-capture-proposal.md)
remains nonnormative history.

## Boundary

The existing canonical versioned producer owns effective mutation policy,
revision representation, provider selection, raw acknowledgement and domain
projection. The UnitOfWork interpreter owns authorization, transition hooks
and post-result local coherence. The enclosing caller owns commit or abort.

The capture carrier preserves these responsibilities by retaining the exact
canonical outcome before running optional coherence. It does not flatten
projection Failure into a failed outer mutation and does not replace the
original projection with a cache error. Immutability and companion-only
construction prevent a consumer from manufacturing a committed marker or
substituting a different raw result through a copy/writer facade.

## Interpreter integration

Four fixed private operation variants map mechanically to the existing four
Space ports. The only shared new flow helpers implement typed-save
authorization/transition behavior, selected-id authorization rebinding and
post-result capture/coherence. They introduce no public callback writer.
Legacy node bodies, create/delete capture behavior and default helper recovery
remain unchanged. Classifier additions are Local; generic planning and typed
recording already support the new nodes without modification.

The selected id is an opaque canonical value. Authorization rebinding changes
only targetId and collectionName. Aggregate command semantics and all other
authorization fields remain the caller's original authority. Typed saves
retain lazy collection resolution; record patches retain their existing exact
collection admission. A name ambiguity is not permission to redirect an
exact selected identity.

Coherence order is invalidate Views, evict selected identity, optionally put
the successful projection. This order ensures a real Realm observer can see
invalidation even if removal raises an exception. Record-snapshot residency
uses the actual hydrated projection through the persisted-record decoder;
record-detached residency uses the already revision-free domain Record.
The private opt-in flag extends the existing helper boundary without changing
ordinary callers' recovery. Projection Failure still invalidates/evicts and
skips put. No receipt is followed by a read/retry/compensation.

The active evaluator performs no automatic abort. Event staging stays with
the same Save/SaveDetached/observed/detached patch owners as before, including
pre-provider hook failures and later persistence failures. The unrelated
legacy UpdateDetached event restore is not copied into this family.

## Fixture and oracle ownership

The object-owned
[fixture](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedOutcomeFixture.scala)
composes the unchanged producer Session for real data and provider operations.
It creates its own Component and exactly matching registered persistent,
EntityCollection, store Realm and partitioned memory realm. It copies the
source context's declared clock/security/observability into its runtime.
Runtime supplier and typed interpreter share the same lazy UnitOfWork.

RecordingSpace observes only actual outcomes returned by super. LifecycleStore
delegates the actual provider's operations/capabilities and counts enclosing
prepare/commit/abort. Observers neither replace a writer nor manufacture a
successful result. Tests assert exact Space-outcome and provider-Record
references, descriptor/persistent/context/UoW identity and zero enclosing
lifecycle activity.

RegisteredProbe forwards the old persistent's typed admission and admitted
decode directly. This keeps typed Structured/Throw/null/foreign-id/wrong-revision
projection behavior intact. Record residency uses the production
`EntityPersistent._decode_store_record` path, which calls `fromStoreRecord`.
Only that actual decoder is independently faulted for record coherence. The
finite Good/Structured/Throw/NullConsequence/NullFailure/NullEntity dimension
is distinct from typed projection modes and real Realm removal failure.
All faults arm after an actual provider ACK. No production decoder seam is
introduced. View epoch/counter evidence is local cache evidence and is not
claimed as a Display reload.

Expected primitive, revision branch, ordered logical Fields and complete
physical state are prepared before interpretation. The strict policy declares
Versioned, while explicit patch policies declare CAS or Direct. Direct uses
the existing Managed precondition with registered concurrency None and no
observation. Raw None under assembled Optimistic is tested separately.
Complete pre-action state supplies only retained data, with explicit owned
metadata replacements and fixed field orders. No expected branch/order comes
from actual results/plans, no actual Record is collapsed to a Map, and no
subset comparison substitutes for whole-state correspondence.

Independent SQL readers observe actual durable state. Known rejection rolls
back. Real commit then unknown/throw/null acknowledgement returns no captured
success despite durable changes; the single attempted mutation is not replayed.
Owned pools/readers are closed by their actual SQL factories. DataStore has no
fictional closeC interface.

The old lawful input generator keeps projection failures typed-only. The
new law adds orthogonal Realm and record-decoder fault dimensions, restricting
typed decoder faults to Good by construction. Shrinking reduces input values,
revision/mode and faults without filtering/discarding inputs. Stale outcomes
never ACK or decode; Realm failure takes precedence over record decoding.

## Paired specifications and limits

[Portable executable specification](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedOutcomeSpec.scala)
and [required PostgreSQL executable specification](../../src/test/scala/org/goldenport/cncf/unitofwork/PostgresqlUnitOfWorkVersionedOutcomeAcceptanceSpec.scala)
cover the spec's UVO matrix using the shared fixture. PostgreSQL uses the
existing required 17.11/fsync/synchronous_commit harness. The local import alias
`RequiredPostgresqlAcceptance` denotes that existing required harness, not a
new infrastructure type.

No authoring result is a validation receipt or acceptance transition. All four
original P96-07 criteria remain OPEN, including ordinary registered
Entity/Aggregate/Display mutation routes and transport, enclosing committed
termination/result, contextual afterCommitResponseC Detail/List reload/C3,
and positive lifecycle/committed pending/failure/no-replay/native PG-E12.
Original P96-06 exact commit, P96-07 protected Step acceptance/commit,
P96-08..11, the sole current-epoch full Phase review, final validation/release,
ledger and reciprocal aggregate obligations remain required.

Historical interpreter size and legacy naming observations remain separate
Hygiene: `HYG-P96-C2B2-INTERPRETER-SIZE-OBS001` and
`HYG-P96-C2B2-LEGACY-NAMING-OBS001`. This unit adds fixed flows without a broad
extraction or unrelated source cleanup.
