# Aggregate observed capture ownership

Document role: settled internal PHASE-96 / P96-07C2B4 design.
The [paired specification](../spec/aggregate-observed-mutation-capture.md)
defines behavior. This document records no validation or acceptance result.

## Active owner and authorization

ActionCallRepositoryPart owns one additive internal typed Aggregate command
edge. The actual declared alias/root resolver and contextual Aggregate builder
remain its domain correspondence owners. AggregateAuthorization supplies the
complete request, normal policy and metadata; the selected UnitOfWork owns
factory authorization before domain and canonical authorization recheck before
persistence. The request is never downgraded to Entity update or replaced by a
manual grant.

The helper selects the actual existing lazy UnitOfWork once. Small new-only
lifting through ConsequenceT/Free reaches evaluateInActiveUnitOfWorkC. Existing
exec_c and interpreter run own commit/abort and are therefore unsuitable for
this capture seam: even an eager-looking composition through them would cross
the enclosing lifecycle boundary. This helper introduces neither a fresh UoW
nor an event restore/checkpoint policy. Domain and transition events remain in
the actual caller-owned pending buffer.

## Exact producer and independent results

The current authoritative root/revision precedes the domain command and stale
comparison precedes equality. Exact root normalization and unmanaged-field
subset correspondence retain the prior C1 semantics. Full updated Record
conversion uses the same descriptor persistent as the selected registered
collection. Existing fixed typed-save nodes own transition checks, provider
mutation and capture before optional coherence. The canonical provider alone
owns atomic revision recheck, Applied/NoOp, successor, body effects and ACK.

The existing runtime collection erases its entity type to Any. Safe widening of
the unchanged capture to `EntityVersionedMutationCaptureOutcome[?]` keeps that
projection opaque without a Record/Any writer facade, cast/rebuild of the
projection or new factory. Exact mutation, persisted raw Record, projection
Consequence/Conclusion and coherence remain independently observable. Optional
projection or cache failure cannot replace known raw persistence, cause replay
or fabricate whole-UoW committed evidence. Projection Failure still follows the
existing invalidate/evict/skip-put flow; actual Realm failures supply the
separate coherence fault dimension.

## Canonical preserving runner

The source investigation found public DslChokepointRunner.run/phase catch
Throwable. The earlier assumption that an inner Aggregate NonFatal guard
could propagate fatal/control/interruption through those wrappers was false.
The settled owner for the additive opt-in is the canonical shared runner.
Its new run/phase APIs reuse existing hook selection and one private balanced
mechanism. Only the new Aggregate path opts in; C1/legacy bodies, defaults,
public API and hook implementations retain their old semantics.

The mechanism records the attempted prefix before enter, invokes the body once,
validates its Consequence and releases the prefix in reverse. Normal cleanup
is outside the entering/body catch, preventing a cleanup signal from causing
duplicate cleanup. NonFatal telemetry failures are best effort. One optional
Throwable retains the exact first primary across all required releases;
entering/body primary takes precedence, otherwise first reverse cleanup fatal
wins. No suppressed exceptions or interrupt state are modified. Genuine body
Failure remains identical; malformed shared outcomes have their own fixed
diagnostic, while the Aggregate callback guard retains its separate redacted
boundary.

## Fixture and oracle responsibilities

The object-owned fixture composes existing real provider data and observation
helpers but installs its own actual Component, Aggregate registrations,
matching descriptor persistent, Realm, partitioned memory, View and shared
active runtime/UoW/interpreter. Optional framework hooks are installed on the
completed context before its first lazy UoW selection; a later copied context
would fail to establish this correspondence because the existing hook helper
does not rebind RuntimeContext. Actual ProcedureActionCall execution reaches
the internal helper.

RecordingStore forwards actual capabilities/results and arms faults only
after real authoritative ACK. RecordingSpace retains the exact returned typed
outcome. Provider JDBC commit and enclosing DataStore/UoW lifecycle are separate
counters. Independent complete logical/physical baselines prescribe all wanted
branches, ordered fields and cells before action. They do not derive expected
state from actual plans/results. Native writer closure occurs after positive
ACK and uses a separate live reader to establish durable state while actual
overflow hydration fails. Real native ACK faults retain rollback versus unknown
commit semantics without a fabricated capture.

Bounded reused roots/pools support three separate sequential total Aggregate
laws and one pure hook law with 100-success/zero-discard gates. These are
authored obligations, not passing evidence. Parent representative15,
accumulator86, fresh independent whole-nine assurance and original formal
acceptance remain required.

## Retained work and debt

All four P96-07 criteria remain OPEN: registered Entity/Aggregate/Display CUD
and descriptor Operations; raw JSON/ordered HTTP headers/CLI-MCP-SPI revision
transport; actual enclosing committed termination/result; selected contextual
afterCommitResponseC Detail/List/C3, positive create/delete and committed
pending/failure/no replay/native PG-E12. Original P96-06 exact commit, P96-07
protected Step acceptance/commit, P96-08..11, sole epoch-1 full Phase review,
final validation/release/ledger and reciprocal aggregate handoff remain.

Historical ActionCallRepositoryPart size, legacy protected/parameter naming,
nested AggregateRootState and import debt remain separate Hygiene, as already
recorded by the parent. This unit does not extract the trait or change those
legacy contracts. No separate new Development Candidate is introduced.
