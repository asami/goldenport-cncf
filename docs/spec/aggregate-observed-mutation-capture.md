# Aggregate observed mutation capture

Document role: internal PHASE-96 / P96-07 / P96-07C2B4 contract.
The [paired design](../design/aggregate-observed-mutation-capture.md) owns the
settled responsibility decisions. Authoring supplies no validation or review
receipt and closes no Step or Phase criterion.

## Internal command boundary

`ActionCallRepositoryPart` provides the additive CNCF-internal final helper:

```scala
private[cncf] final def _aggregate_update_observed_capture_c[A <: RecordPresentable](
  aggregatename: String, targetid: EntityId, commandname: String,
  observedrevision: EntityRevision
)(command: A => Consequence[A]): Consequence[EntityVersionedMutationCaptureOutcome[?]]
```

Names must be nonnull, nonempty and exactly trimmed. Target, its collection,
observation and callback must be nonnull. Structured argument admission
precedes lookup, callback and persistence. Exact declared Aggregate-to-Entity
aliases and the existing root resolver remain authoritative; a foreign or
ambiguous root is not an alternate route to persistence.

The Authorization phase retains normal declared Aggregate policy, including
roles, access, relations, natural conditions and operation model. The complete
`AggregateAuthorization.Request` carries the aggregate resource family/type,
command access, components, exact target and entity names into the same actual
active UnitOfWork. Its factory authorization runs before Aggregate resolution
or domain invocation. The same authorization is supplied to the canonical save
node for its authoritative recheck. No system grant or Entity downgrade is
introduced.

## Authoritative observation and complete domain value

The selected collection must be Optimistic with valid managed Embedded or
Detached revision binding. The explicit validated execution policy is
Optimistic / WriteIfChanged / ObservedRequired / Some(observedrevision).
The selected existing Space loadSnapshot/loadDetached supplies authoritative
root and revision. Missing or stale roots reject before the contextual
Aggregate builder or domain command, including stale equal input. A current
maximum-revision NoOp requires no successor calculation.

The actual registered AggregateCollection resolves with the same context.
Its current Record and the root persistent's Record undergo existing exact-root
normalization and selected-ID checks. The unmanaged root-field subset must
correspond to the Aggregate. The Method phase calls the typed domain command
once. Its returned complete Record must retain the exact selected target.
Persistence is a full save, not a patch. The selected descriptor's exact
persistent decodes that Record and supplies its selected identity before any
save attempt.

Builder, Record rendering, domain, conversion and selected identity callbacks
use a new-only bounded guard. Genuine structured failures retain their exact
Conclusion. Null Consequence, Success(null), Failure(null) or NonFatal throw
becomes `operationInvalid("aggregate-observed-capture", Inconsistency, ...)`
with reason `aggregate-callback-invalid` and no original secret or exception.
Fatal errors, ControlThrowable and InterruptedException propagate unchanged.
Canonical provider and acknowledgement failures retain their own vocabulary.

## Exact capture and caller-owned lifecycle

The helper selects `execution_context.unitOfWork` once and evaluates existing
Authorize and EntityStoreSaveSnapshotOutcome/EntityStoreSaveDetachedOutcome
nodes through `evaluateInActiveUnitOfWorkC`. It does not call committing
`exec_c`, interpreter run/execute, commit, abort, restore, reload or retry.
The caller retains pending events, including domain-staged events on failure.
The existing save node owns transition validation, provider choice, atomic
stale recheck, capture and View/residency reconciliation.

The actual capture is widened to an existential projection without rebuilding
its mutation, projection, raw Record or coherence. Embedded contains the actual
typed snapshot; Detached contains the actual revision carrier. Exact provider
ACK Record and Space outcome references survive optional projection/coherence
failure. No post-capture projection, guessed Applied flag, revision, wrapper
factory or public committed marker is permitted. Provider ACK is distinct from
enclosing UnitOfWork commit.

## Canonical outcome-preserving telemetry opt-in

`DslChokepointRunner` owns two explicit internal APIs, `_run_preserving_c` and
`_phase_preserving_c`, each requiring an explicit hooks Option. Selection is
the existing explicit vector, otherwise framework vector, otherwise defaults;
Some(empty) disables callbacks. The new Aggregate path uses actual framework
or default selection for its outer scope and every phase. Existing public
run/phase, defaults, hooks and legacy Aggregate callers retain their behavior.

The opt-in invokes its body once after attempted enters. Well-formed success
or Failure returns by exact Consequence identity. Ordinary NonFatal body throws
use existing Conclusion.from mapping. Malformed outcomes use fixed structured
`dsl-chokepoint-outcome-invalid`, distinct from the Aggregate callback error.
Telemetry NonFatal failures at enter/leave/phaseEnter/phaseLeave are best effort:
remaining callbacks continue and the body result is retained.

Each hook enters the attempted prefix before its enter callback. Entering
fatal signals stop further enters and body invocation. Every attempted hook
is released once in reverse; entering/body fatal cleanup receives Failure,
otherwise cleanup receives the validated body outcome. Fatal cleanup never
prevents remaining releases. The exact first entering/body signal wins over
cleanup signals; otherwise the first reverse-release fatal wins. Cleanup
completes before that same signal escapes. There is no suppressed-exception
mutation, interrupt flag mutation, recursive logging or retry.

This additive opt-in resolves the source contradiction found in the old
runner: public run/phase catch Throwable, so an inner NonFatal-only callback
guard could not itself preserve fatal/control/interruption propagation.
Changing those legacy semantics globally is outside this contract.

## Executable correspondence

- [AggregateObservedMutationCaptureSpec](../../src/test/scala/org/goldenport/cncf/action/AggregateObservedMutationCaptureSpec.scala): real memory/SQLite root and alias commands, current/changed/stale/maximum outcomes, authorization, event ownership, exact root/identity, malformed callbacks, signal propagation, all post-ACK projection/Realm faults, actual framework hook faults, independent winner and two backend laws.
- [PostgresqlAggregateObservedMutationCaptureAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/action/PostgresqlAggregateObservedMutationCaptureAcceptanceSpec.scala): required PostgreSQL 17.11/version170011 with fsync and synchronous_commit on; actual native Aggregate routes, exact capture/full state, default/factory authorization, projection/coherence/telemetry faults, independent winner, real owned-reader closure and Normal/Reject/Indeterminate/Throw/Null ACK behavior.
- [DslChokepointPreservingOutcomeSpec](../../src/test/scala/org/goldenport/cncf/observability/DslChokepointPreservingOutcomeSpec.scala): exact normal/nested results, full hook order, ordinary/malformed bodies, fatal/control/interruption identity and balanced cleanup, selector precedence and legacy regression, and a pure generated hook law.

Logical oracles retain complete ordered Fields for selected/body/unrelated
Records, with SQL NULL attributes absent. Physical oracles retain complete
tables, column order/type, ordered rows and NULL cells through independent
readers. Expected changes are prescribed before action: only title, revision,
fixed update timestamp and update owner may change. Three sequential total
Aggregate laws (memory/SQLite/native) and one runner law each require at least
100 successes, zero discards and one worker with meaningful valid shrinking.
Native disabled or unavailable execution supplies no acceptance credit.

## Remaining original gates

All four original P96-07 criteria remain OPEN: ordinary registered Entity AND
Aggregate authorization/domain/alias and Display CUD plus descriptor Operations;
strict raw JSON, ordered physical HTTP headers and CLI/MCP/SPI revisions;
actual current enclosing committed termination and known result; contextual
afterCommitResponseC Detail/List reload/C3, positive create/delete, committed
pending/projection failure, no replay and native PG-E12. P96-08..11, original
P96-06 exact Step commit, P96-07 protected Step acceptance/commit, the sole
epoch-1 full Phase review, final validation/release/ledger and reciprocal
aggregate handoff remain mandatory. This internal seam promotes no kernel,
successor, main checkout, push or publication.
