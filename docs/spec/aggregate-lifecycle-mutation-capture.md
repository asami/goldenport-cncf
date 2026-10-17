# Aggregate lifecycle mutation capture

Document role: normative internal PHASE-96 / P96-07 / P96-07C4A contract.
The [settled design](../design/aggregate-lifecycle-mutation-capture.md) records
responsibility decisions. Authoring supplies no execution, validation, review,
Step or Phase acceptance.

## Typed internal edges

`ActionCallRepositoryPart` adds exactly these CNCF-internal final entry points:

```scala
private[cncf] final def _aggregate_create_capture_c[C](
  aggregatename: String, commandname: String, persistent: EntityPersistentCreate[C]
)(command: () => Consequence[C]): Consequence[EntityCreateMutationOutcome[C]]

private[cncf] final def _aggregate_delete_observed_capture_c[A <: RecordPresentable](
  aggregatename: String, targetid: EntityId, observedrevision: EntityRevision
)(validate: A => Consequence[Unit]): Consequence[EntityDeleteMutationOutcome]
```

Names are nonnull, nonempty and exactly trimmed; persistent, callbacks, target,
target collection and observation are nonnull. Both edges require an actual
registered Aggregate and its unique declared root Entity collection. A declared
alias resolves only to that canonical collection. Missing or ambiguous
registration, foreign exact collection, unmanaged binding and nonoptimistic
policy reject before domain callbacks or persistence. Only valid managed
Embedded or Detached revision bindings are admitted.

## Create authority and capture

Normal Aggregate type authorization uses `create:<fixed commandname>`. The
complete AggregateAuthorization request retains declared access, resource
family/type, root/alias identity, source and target components, entity names,
operation model, relation rules and natural conditions. The actual selected
UnitOfWork evaluates its initial Authorize before the application command.
The command runs once and returns typed application input C. Its persistent's
collection and optional ID must match the selected exact collection. Generated
IDs remain the existing canonical policy; the helper preallocates no ID.

Exactly one existing `EntityStoreCreateOutcome` interprets that input with the
same authorization and unchanged default create options on the same UoW.
Canonical strict create owns defaults, INITIAL revision, duplicates, root/body
atomicity, provider acknowledgement and optional resident/View reconciliation.
The helper returns the exact interpreted outcome without widening C or
reconstructing a receipt, raw Record, revision or capture.

## Observed lifecycle deletion

Normal instance authorization uses access kind `delete` and the existing
Aggregate authorization loader with full declared policy. The full Aggregate
request survives initial Authorize and `EntityStoreDeleteObservedOutcome`;
there is no Entity/domain downgrade or internal/System grant. Delete has no
commandName and requires no invented Aggregate command definition.

The validated execution policy is Optimistic / AlwaysWrite / ObservedRequired
with the supplied observation. Authorization precedes authoritative loading
and domain resolution. The registered persistent loads the actual root through
existing `loadSnapshot` or `loadDetached`. Exact observation comparison precedes
the contextual Aggregate builder and validation, including equal stale input.
The current revision must have a legal successor under the unchanged canonical
delete exhaustion rule, even for physical deletion.

The actual contextual Aggregate must retain the exact target and the existing
unmanaged root-field subset correspondence. Application/root rendering and
selected identity callbacks are admitted before validation. The pure validator
runs once; it cannot act as a writer. One existing observed-delete outcome
operation receives the same prior revision and full authorization. The provider
owns the atomic stale recheck, physical root/body removal or logical tombstone
and body preservation. The exact deletion receipt retains the matched prior
revision; there is no fabricated post-delete Record or successor receipt.

## Callback and enclosing lifecycle ownership

Both helpers select the actual `execution_context.unitOfWork` once and reuse
the existing preserving Aggregate chokepoint, phases and active evaluation.
The new private lifecycle callback guard retains genuine structured Failure
Conclusions. Null Consequence, Success(null), Failure(null), and NonFatal
callback throws become fixed redacted operationInvalid
`aggregate-lifecycle-capture`, Cause.Kind.Inconsistency, Reason
`aggregate-lifecycle-callback-invalid`. Fatal, control and interruption signals
propagate through the preserving wrappers after their cleanup. This guard does
not globally rewrite provider/interpreter callback or ACK semantics.

There is no inner commit, abort, dispose, interpreter run/execute, new UoW,
event restore/checkpoint, replay, compensation, caller lock or direct datastore
writer. Actual captured result and raw Record references survive optional
coherence failure. Known rollback and durable unknown ACK retain their existing
classifications; unknown ACK supplies no success capture and authorizes no retry.
Provider ACK remains distinct from enclosing UoW commit. Existing C3/update
and all legacy Aggregate signatures, defaults and behavior remain unchanged.

## Executable correspondence and independent oracles

- [Portable behavior](../../src/test/scala/org/goldenport/cncf/action/AggregateLifecycleMutationCaptureSpec.scala): ALC-E1..E6; root/alias create, supplied/generated IDs, both representations and all body modes; physical/logical deletion; normal/factory policy and full authorization vectors; admission/stale/exhaustion/veto; exact callback failure and signal ownership; real coherence faults, atomic winner and ACK distinctions.
- [Portable laws](../../src/test/scala/org/goldenport/cncf/action/AggregateLifecycleMutationCapturePropertySpec.scala): ALC-P1/P2; separate total memory and SQLite create/delete laws, meaningful Unicode/value/revision/finite-mode shrinking, at least 100 successes each, one worker and zero discards.
- [Required native PostgreSQL](../../src/test/scala/org/goldenport/cncf/action/PostgresqlAggregateLifecycleMutationCaptureAcceptanceSpec.scala): ALC-PG1/PG2/PG-P1/PG-P2; actual native Aggregate helpers, both read conventions, exact raw references, complete independent catalog/ordered columns/types/rows/NULL cells, native failure/winner/ACK distinctions and two separate 100-success native laws. Native absence fails acceptance.

The [fixture](../../src/test/scala/org/goldenport/cncf/action/AggregateLifecycleMutationCaptureFixture.scala)
composes existing real UnitOfWorkMutationCaptureFixture sessions, observers and
independent create/delete oracles. Its own named Component declares root/alias
and current operation access. One new runtime, context, UoW and interpreter use
the same actual recording store/Space, selected persistent, Realm/memory, fixed
clock, observability, principal and original deterministic ID context. The
contextual builder loads through its active context. Fixture seed writes are
setup only. Exact native/Space references, factory requests/current UoW,
callback order, full logical/physical/resident/View state and zero enclosing
lifecycle establish correspondence without an after-state-derived oracle.

SQL NULL attributes remain omitted only from logical Records. Complete native
NULL cells and columns remain in the physical oracle under the existing
[SQL NULL boundary](sql-null-record-boundary.md). No nullable Option API or SQL
NULL type is added. Generated laws reuse bounded owned roots only after actual
exact-ID cleanup; they do not turn fresh creation into duplicate-only attempts.
SQLite resources close in the existing fixture's finally; the task-private
native fixture owns PostgreSQL pools and cleanup.

## Retained enclosing obligations

All four original P96-07 criteria remain OPEN. C4B ordinary typed Display
create/delete registration, strict HTTP POST/DELETE and CLI/MCP/SPI revisions;
C4C normal Entity AND Aggregate root/alias create-to-Detail/List,
delete-to-List, real canonical query, committed pending/no replay and native
PG-E12 remain required. P96-06 exact Step commit reconciliation, complete P07
protected Step acceptance/commit, P96-08..11, all Step commits, the sole epoch-1
full Phase review, final validated release/ledger and reciprocal aggregate
handoff retain their original owners. No checklist state is changed here.
