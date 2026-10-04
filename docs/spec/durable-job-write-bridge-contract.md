# Durable Job Lifecycle Write Bridge Contract

status=normative
phase=JM69-03N
native_root_slice=JM69-09B3B3C2B2A
native_root_validation=VAL-005-validated-independent-conformance-PASS
native_runtime_recovery_binding=C2B2B1-focused-validated-B2-authored-fresh-validation-independent-review-pending

This contract defines the package-internal durable-write bridge used by a
configured `InMemoryJobEngine` for Persistent job admission, first normal
execution start, engine-owned per-task start and post-body outcome checkpoints,
and final terminal settlement checkpoints. It does not define a public
JobEngine API, provider selection mechanism, recovery protocol, or
execution-effect policy.

## R1 Closed boundary and evidence

The bridge has exactly six boundaries:

- `Admission` creates the canonical V2 record.
- `StartIntent` checkpoints a canonical V2 submitted record before normal
  execution begins.
- `RunningIntent` checkpoints a canonical V2 running record after StartIntent
  and before scheduler, runtime-running, or task effects.
- `TaskStartIntent` checkpoints a canonical V2 running task-start intent after
  local task Running registration and before `JobTask.run`.
- `TaskOutcomeCheckpoint` checkpoints a canonical V2 running/pending task
  outcome only after that task body has returned and its local completion and
  transaction outcome have both been registered.
- `TerminalOutcomeCheckpoint` checkpoints a canonical V2 terminal result only
  after final success, non-retry failure, or cancellation settlement has
  registered the closed local lifecycle and result.

For each boundary, the configured engine supplies a closed request and
`DurableJobProjectionEvidence` and `DurableJobRecordAccess` pair. The evidence
source is explicit and package-private. `Admission`, `StartIntent`, and
`RunningIntent` requests carry their boundary only. `TaskStartIntent` carries
only the Job id, current Task id, current parent Task id, and the exact ordered
full `JobTaskReadModel` vector registered by the engine.
`TaskOutcomeCheckpoint` carries only the Job id, completed Task id, parent id,
and that same exact ordered full closed read-model vector.
`TerminalOutcomeCheckpoint` carries only the Job id and exact ordered full
closed read-model vector. No request carries a
`TaskOutcome`, `JobTask`, `OperationResponse`, ActionEngine, UnitOfWork,
ExecutionContext, provider, resolver, credential, raw body or payload, result,
or external-effect value. The bridge must not synthesize authorization, access,
task target, input, result, retry, definition, diagnostics, retention,
calltree reference, timestamp, or external reference from a live execution
object.

The original six nested request shapes remain unchanged. An optionally supplied
DurableJobKeyedEvidenceSource receives an additive private outer envelope with
the original validated JobId, actual next semantic revision and identical nested
request. Its response must match the original opaque Job .value and expected
revision before the unchanged projection/Pending/pair guards. It resolves once;
a keyed refusal or malformed response never falls back to legacy apply.
An ordinary Function1 source retains the original request-only invocation and
the old alias, constructor and defaults. See the keyed contract R1–R11 below.

The Admission evidence has semantic revision `1`; the StartIntent evidence has
semantic revision `2`; and the RunningIntent evidence has semantic revision
`3`. Every TaskStartIntent and TaskOutcomeCheckpoint evidence has the next
contiguous semantic revision of the bridge's retained nonterminal
Running/Pending snapshot. Thus the first task uses TaskStartIntent revision `4`
and TaskOutcomeCheckpoint revision `5`; each later task continues with the
next TaskStartIntent/TaskOutcomeCheckpoint pair.
`TerminalOutcomeCheckpoint` evidence has the next contiguous semantic revision
of the retained nonterminal Running/Pending snapshot and supplies the closed
terminal result and retry evidence that agrees with the settled lifecycle.
DurableJobProjection performs V2 projection and DurableJobStore remains the
canonical authority for access, identity, canonicality, provider revision, and
contiguous semantic revision checks.

## R2 Admission order

For a configured Persistent submission, the required order is:

1. existing pure input/lifetime root preflight, physical provisional lease,
   root ID and Submitted JobRecord construction;
2. bridge Submitted validation, complete lower-only lifetime/native policy,
   actual task and execution-note counts, and pure root ticket admission;
3. exactly one closed Admission evidence/access request and V2 projection of
   that exact Submitted JobRecord;
4. native lifetime `reserveRoot` acknowledgement of the exact Pending payment;
5. ordinary DurableJobStore create acknowledgement at semantic revision `1`;
6. native lifetime `publishRoot` acknowledgement against actual signed proof;
7. bridge retention of the actual signed snapshot and quota/access ownership
   handle, existing local lifetime publication/physical lease commit,
   cancellation-scope creation, local JobRecord insertion, JobEntity sync,
   submitted event, timer or queue registration, and normal synchronous
   execution.

The constructor's native policy defaults to 4096 ledger jobs and 67108864 held
bytes with lifetime defaults 8192/524288/16384. The configured engine passes
the actual State lower-only lifetime policy explicitly; the bridge replaces
only that policy field, preserving its configured global caps. Task and note
counts traverse only the existing collections, without task getters. The Store
factory uses its exact constructor-owned EntityStore without discovery or
bootstrap at construction. The closed admission helper owns only
reserve/create/publish and receives no live task, JobRecord or evidence source.

If evidence, projection, reserve, create or publish refuses, its Consequence
failure is returned without helper-level translation. Native exceptions use the
existing protected closed labels; a lost publish acknowledgement returns the
existing confirmation-unavailable label. The engine observes the existing task admission failure and a
package-private closed Admission refusal fact, but creates no executable local
job, work item, event, timer registration, or task execution.

The physical provisional lease is released on refusal. The single acknowledged
bridge entry remains absent for that failed candidate; pre-native root ID
allocation is still visible. A collision after actual root acknowledgement
likewise refuses local publication while preserving genuine paid/signed evidence.
Committed Pending or Published evidence and any signed root survive uncertain
acknowledgement without refund, compensating deletion, automatic retry or
readback-success inference. Explicit fresh quota recoverRoot may authenticate
and promote an initial signed root as library confirmation only. It does not
install a runtime Job or satisfy native runtime recovery acceptance.

The root Slice retains VAL-005 focused validation and independent conformance
PASS. Authored C2B2B1 now binds same-State native child, Retry, control,
annotation, association, compensation and Cancel intent. It passed current runtime VAL-003 focused validation; independent Slice
conformance, Step acceptance and commit remain pending. Authored B2 fresh-State
runtime/terminal registration authenticates exact native Seeds and store-bound
ownership; fresh validation and independent review remain pending. There is no production bridge-binding caller;
configured operational wiring and real process/CBD acceptance remain JM69-09D
and JM69-10 obligations within the original Phase69.7.

An unconfigured Persistent engine retains its existing behavior. Ephemeral
submission bypasses the bridge entirely.

## R3 Initial job start and in-process task outcome order

R3/R4 describe the original acknowledged new-root sequence. Recovered normal
JobRun continuation uses the N-relative contract in R8; recovery RetryRun and
Control retain their original signed-write exclusions.

Before an admitted configured Persistent job takes its first normal
synchronous or `JobRun` scheduler start, the bridge derives a deterministic
Submitted candidate: it preserves the current submitted value, advances its
update timestamp deterministically, and appends one
`job.durable-start-intent` timeline event. The required order is:

1. closed StartIntent evidence and access;
2. V2 projection of that Submitted candidate;
3. DurableJobStore optimistic checkpoint at semantic revision `2` against the
   snapshot returned by Admission;
4. closed RunningIntent evidence and access;
5. V2 projection of a deterministic Running candidate that copies the local
   Submitted record, advances its update timestamp by one millisecond, and
   appends exactly one `job.durable-running-intent` job-level timeline event;
6. DurableJobStore optimistic checkpoint at semantic revision `3` against the
   snapshot returned by StartIntent;
7. existing `job.scheduler.started` and `job.running` observations and runtime
   Running mutation;
8. local `task.running` registration for the normal task;
9. an engine-constructed closed TaskStartIntent request containing that task's
   dynamic Task id, parent id, and the exact complete local read-model vector;
10. V2 projection of the revision-4 nonterminal Running/Pending candidate,
    whose descriptors exactly match every current local read model and whose
    durable timeline is the revision-3 durable base plus exactly one
    `task.durable-start-intent` event;
11. DurableJobStore optimistic checkpoint at semantic revision `4` against the
    revision-3 Running snapshot; and
12. the existing `JobTask.run` invocation;
13. the existing local `task.succeeded` or `task.failed` registration and local
    `task.transaction.committed` or `task.transaction.failed` registration;
14. an engine-constructed closed TaskOutcomeCheckpoint request containing only
    the completed dynamic Task id, parent id, and exact complete local
    read-model vector;
15. V2 projection of the next nonterminal Running/Pending candidate, whose
    durable timeline is the retained prior durable timeline plus exactly one
    `task.durable-outcome-checkpoint` event; and
16. DurableJobStore optimistic checkpoint against that retained snapshot before
   another task, compensation, job result or terminal settlement, retry or
   control, or canonical-outcome observation.
17. After final local success, non-retry failure, or cancellation settlement,
    an engine-constructed closed TerminalOutcomeCheckpoint request, V2
    projection of the settled terminal candidate, and DurableJobStore
    optimistic checkpoint against the retained nonterminal snapshot; and
18. only after that terminal checkpoint succeeds, the existing terminal event
    or canonical task-outcome observation.

The initial synchronous or `JobRun` dispatch alone establishes Admission,
StartIntent, and RunningIntent. Every in-process `RetryRun` that reaches a task
body instead starts from the bridge's retained nonterminal snapshot and repeats
only the TaskStartIntent and TaskOutcomeCheckpoint sequence in steps 8 through
16; it does not recreate any job-level boundary. Thus an immediate one-task
retry records the first attempt at revisions 4 and 5, the retry attempt at
revisions 6 and 7, and terminal settlement only after that final pair at
revision 8. Timer-enqueued in-process retries use the same retained-snapshot
sequence; each later actual attempt advances the revision once for its start
intent and once for its outcome checkpoint. Before either task-bound candidate
is projected, every retained durable task event id is rehydrated only by an
exact match to a current closed `JobTaskReadModel` id; an unmatched retained id
is a closed bridge refusal.

A terminal V2 success distinguishes an ordinary completed task vector from
retained retry history. An ordinary success contains only `Succeeded` task
models. A vector that retains a prior `Failed` model is not accepted as an
ordinary success merely because its final Job status is `Succeeded`; it must
satisfy the bounded durable retry-history predicate in R4.

### R4 Revision-three and task-bound durable state

The revision-3 record has lifecycle `Running`, result `Pending`, and the
existing nonempty closed task descriptors. It is a job-level intent only.
Revision 4 is still nonterminal `Running`/`Pending`, but declares the dynamic
start intent for one registered task. It intentionally does not copy the local
`task.running` event into the durable timeline: the sole new durable task event
is `task.durable-start-intent`. Revision 5 copies revision 4's durable timeline
and appends only `task.durable-outcome-checkpoint`; its descriptors exactly
correspond to the closed local task vector and its job result remains `Pending`.
Each later task start and outcome, including an in-process `RetryRun` task
attempt, repeats that contiguous nonterminal sequence from the retained
snapshot. A retry start and outcome therefore preserve every prior attempt's
closed descriptor vector and durable task timeline before appending their own
two events. Terminal settlement follows the final actual attempt pair and
validates the complete retained attempt vector. Each retained task event stays
bound to its exact durable task id through every later task-bound checkpoint:
the bridge reconstructs it from the equal current closed read-model id, never
from raw runtime state, and then appends only the current attempt event.
V2 permits the revision-3 Running candidate without live task read-models only
when its supplied closed descriptor vector is nonempty; every task-bound
checkpoint instead requires exact descriptor correspondence for every current
local read model.

Each task-bound candidate starts from the retained durable snapshot timeline;
it does not copy local runtime timeline events into durable history. Before its
one-millisecond event advance, its `updatedAt` is the monotonic maximum of the
retained durable snapshot timestamp and the live local `JobRecord` timestamp.
The resulting candidate timestamp is therefore strictly after both the retained
snapshot and every local task lifecycle timestamp already registered on the
live record.

For a `Succeeded` terminal V2 candidate that retains any `Failed` task model,
the predicate is exact and closed: the current `JobRetryState.attemptCount` is
greater than zero; the final task model is `Succeeded`; at least one prior task
model is `Failed`; and every retained task id has exactly one
`task.durable-start-intent` and exactly one
`task.durable-outcome-checkpoint` event in the candidate durable timeline. This
proves that every retained descriptor belongs to one closed in-process attempt
pair. It does not relax the ordinary
all-succeeded form, nor the Failed or Cancelled terminal forms.

### R5 Refusal closure and dispatch eligibility

The bridge retains the returned current DurableJobStoreSnapshot and acknowledged
quota/access handle in one atomic entry keyed by Job id. Native maxledgerjobs bounds
root handle growth; there is no eviction or refund. A failed StartIntent or RunningIntent evidence request, projection, or
checkpoint preserves that snapshot and leaves the admitted local JobRecord
Submitted. The engine records only the corresponding package-private closed
StartIntent-or-RunningIntent refusal fact; it emits no `job.scheduler.started`
or `job.running` transition and invokes no task. A failed TaskStartIntent
request, projection, or checkpoint preserves the prior durable snapshot after
local task Running registration; it records only TaskStartIntentRefused and
does not invoke or observe that body, synthesize an outcome, finish or settle a
task or job, retry, control, compensate, or write a terminal durable state. A
failed TaskOutcomeCheckpoint preserves its prior durable snapshot after the
already-invoked body and its local completion/transaction registration. It
records only TaskOutcomeCheckpointRefused and authorizes no later task,
compensation, job result or terminal settlement, retry/control, or
canonical-outcome observation. It neither replays that effect nor fabricates a
durable success or failure; later recovery classification owns the unresolved
state. An unmatched durable task id during retained-timeline reconstruction is
the same closed task-bound refusal: it is not dropped, replaced, or inferred.

A failed TerminalOutcomeCheckpoint preserves the prior nonterminal durable
snapshot after final local settlement. The engine records only
TerminalOutcomeCheckpointRefused; it emits no terminal event and makes no
canonical task-outcome observation for that transition. It never fabricates a
terminal durable result. A terminal checkpoint is eligible only when the
configured Persistent job has a retained admitted snapshot and a closed local
task vector, or the narrow confirmed recovered Cancel condition in R8;
unconfigured Persistent and Ephemeral behavior remains unchanged.

A succeeded terminal candidate with a retained `Failed` model that does not
meet the R4 retry-count-and-pair predicate is refused as a nonterminal durable
snapshot. It cannot produce a terminal event or canonical task-outcome
observation by claiming the ordinary all-succeeded form.

Only the configured bridge that admitted or authenticated/adopted the job owns
these gates.
TaskStartIntent and TaskOutcomeCheckpoint bridge writes are eligible for the
initial normal synchronous or `JobRun` dispatch and every in-process `RetryRun`
that reaches a task body. Each retry pair begins from the retained nonterminal
snapshot, advances the semantic revision twice, and completes before the final
terminal checkpoint. Retry dispatch does not repeat Admission, StartIntent, or
RunningIntent. Control dispatch retains its existing task execution semantics
but executes neither task bridge write.
The same TaskStartIntent and post-body TaskOutcomeCheckpoint order applies to
direct and queued same-job task dispatch. A TaskOutcomeCheckpoint refusal
returns the source Consequence failure for direct dispatch without a fabricated
TaskOutcome and prevents settlement or canonical observation. Unconfigured
Persistent and Ephemeral paths retain their existing behavior. Compensation and
rehydrated retry paths do not obtain a new durable write from this contract.
Recovered normal JobRun obtains only the explicit R8 continuation authority.
Cancellation/control dispatch itself obtains none; only its final
closed cancellation settlement may cross TerminalOutcomeCheckpoint.

## R6 Retained and excluded state

Apart from package-private quota/access ownership and the recovery context in
its authenticated prepared entry, the bridge retains
no raw payload or result, raw provider, resolver, credential,
TaskOutcome, JobTask, ActionEngine, or UnitOfWork. It has no
generic storage search and no public capability. Its closed refusal observation
carries only Job id and Admission, StartIntent, RunningIntent, TaskStartIntent,
TaskOutcomeCheckpoint, or TerminalOutcomeCheckpoint category, never a provider
body or failure detail.

A successful StartIntent records intent to begin normal execution. A successful
RunningIntent records only the job-level intent immediately before normal
execution. A successful TaskStartIntent records only one task's pre-body
intent while retaining exact ids for every prior durable task event. A
successful TaskOutcomeCheckpoint records only a closed task
descriptor state with a Pending job result; this applies independently to each
in-process retry attempt and retains the exact prior task-event ids before
appending its current id. Neither task boundary persists a raw result, failure
object, UnitOfWork state, or external-effect value. A successful
TerminalOutcomeCheckpoint persists only its supplied closed durable result and
retry evidence after V2 validation. Its retry-history predicate inspects only
closed task status, retry count, durable task ids, and durable event kinds; it
does not retain a live JobResult, raw task body, raw result, failure object,
UnitOfWork state, or external-effect value.
UnitOfWork and external-effect ordering remain outside this boundary.

[JobDurableLifetimeAdmissionSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeAdmissionSpec.scala)
authors NRA1..NRA8 with actual manual engines and fresh real SQLite providers,
actual commit-before-response-loss hooks and exactly one fault delivery,
adjacent Given/When/Then and direct should assertions. Seed6970933621 requests
at least24 successful finite native runs. Actual root A `PHASE-69.7-JM69-09B3B3C2B2A-VAL-001` completed53 suites:
463 tests ran,458 succeeded and5 failed, with zero compiler warnings and one
existing pending example. Receipt SHA256 is `95ec13eb66279d2a89a04dc9d9325af75e8cd7fc3294a96e5700f53f2dd10966`.
The five failures are NRA2 (missing fixture Detached JobEntity binding), NRA7
(nonterminal V1 fixture), NRA8 (generated failure context hidden), and the two
existing durable cancellation scenarios (pending removal assertions). The frozen
test-only repair installs the declared Detached fixture binding, creates a legal
terminal V1 record, adds ScalaCheck Test.Result clues, and asserts synchronous
cancel pending removal with once-only terminal/event gates and finally cleanup.
VAL-002 stopped before tests because Cozy's development classpath was stale;
the authorized build-only classpath refresh succeeded. VAL-003 and diagnostic
VAL-004 each completed53 suites:463 tests ran,462 passed and only NRA8 failed,
with zero compiler warnings and one existing pending example. Diagnostic
evidence exposed a sequential fixture declaring every task as a root while
the engine correctly gave subsequent tasks a parent. The test-only repair
declares the first task root and subsequent tasks child, retains original
counters, seed6970933621, normal shrinking and at least24 successful runs,
and verifies live and signed parent-chain observations.

Actual `PHASE-69.7-JM69-09B3B3C2B2A-VAL-005` completed53 suites:463 tests ran
and all463 succeeded, with zero failures, zero compiler warnings and one
existing pending example. Receipt SHA256 is
`fa5db495b95e3f0c78a3aff7c75299f75d466b1e7c2344bd00832a6933b1eace`.
Original LC5 and LQ14 remain passed. All eleven prior validated implementation
accumulators are preserved; independent root conformance review is PASS.
The two lifecycle-event cancellation scenarios are explicitly admitted and
the prior B1 validation vector remains intact. Root A is focused-validated;
C2B2, Step and Phase remain incomplete. No Step/full-Phase acceptance or
full-Phase review PASS is claimed. Root conformance PASS is bounded incremental proof.
These in-process owners do not establish real cross-process or CBD Support acceptance.

## R7 Authored State-owned runtime gates and terminal aggregate preservation

An acknowledged native root's private State binding retains its owning Bridge
and admitted root context. Every same-State engine uses that Bridge for eligible
start/running/task/terminal writes and consults the same original checkpoint
failure kind. Per-engine diagnostics merge closed job-owned facts with bounded
deduplication. A different or absent new-root bridge cannot bypass those gates.
Control/Recovery taskbridgewrites=false retains its original signed-write
semantics while still paying native Retry, association and compensation claims.

The six request shapes, semantic revision requirements, exact source equality,
signed Store OCC/canonical gates and timeline/timestamp sequence remain unchanged.
Companions obtain no new TaskStart/Outcome boundary; only terminal projection
signs them. Terminal candidate construction preserves the complete typed model
vector, including normal aggregate compensationStatus/failure/recoveryRequired.
It does not clear those fields before V2 validation.

V2 targetless Root/Child declarations use descriptor compensation=None, with a
present action required nonblank. Aggregate success/failure requires one exact
actual completed companion targeting that original; failed and missing require
both model and record recoveryRequired. A missing original may have no action:
it must be completed committed Succeeded, have nonblank failure and no companion.
Unknown/stray/blank metadata and running terminal aggregates refuse. Actual
companions and complete historical explicit normal descriptors keep their strict
source equality. V1 remains strict and the signed schema is unchanged.

NRT-K drives the original real SQLite Bridge API from acknowledged admission,
start/running and task start/outcome to a prior Running/Pending snapshot. Invalid
root/child aggregates preserve actual prior bytes/signature/revision; valid
absent-action missing signs its exact unchanged request once. NRT-F exercises
both actual engine missing variants, NRT-P1..P3 cover pure projection, and
NRT-A..J cover native runtime/refusal paths. They passed current runtime VAL-003 focused validation; independent Slice
conformance, Step acceptance and commit remain pending. Fresh B2 validation/review,
C/D, JM69-10 and the original full Phase obligations remain open.

Current runtime evidence `PHASE-69.7-JM69-09B3B3C2B2B1-VAL-003` records
54 completed / 0 aborted suites, 477 run / 477 succeeded / 0 failed tests,
zero compiler warnings and one existing SqliteDataStore pending example.
Independent incremental Slice conformance, independent Step acceptance and
commits remain pending. Fresh B2 validation/review, C/D, JM69-10 actual
two-process/CBD Support and original full Phase obligations remain open.
See the [runtime validation chronology](../journal/2026/10/2026-10-02-phase-69.7-native-lifetime-runtime.md).

## R8 Authored recovered ownership and closed history

One private ConcurrentHashMap entry atomically retains the acknowledged signed
snapshot, quota/access binding and optional recovered preparation. Acknowledged
new-root admission and recovery adoption each use putIfAbsent; an occupied entry
refuses without overwrite. Recovery prepares read-only exact Published proof
under the caller context and complete selected policy against the identical
supplied store. It issues no Admission evidence request, root recovery/payment
or signed write. Physical staging/adoption failure publishes no State Seed,
record or timer. Only successful adoption exposes one shared owning handle.

For recovered normal JobRun, the original acknowledged semantic revision N
requires StartIntent N+1 and RunningIntent N+2; subsequent actual task/terminal
requests advance once each. Overflow/out-of-sequence refuses before evidence or
provider mutation. Only native OCC acknowledgement advances the private stage;
entry replacement additionally requires the exact selected prior entry. Stale,
ambiguous or unexpected replacement retains paid/refusal evidence and withholds
subsequent body/canonical success. New-root Admission/Start/Running 1/2/3 and all
six closed request shapes remain unchanged. Recovery RetryRun/Control retain
taskbridgewrites=false while native admission/intent gates remain active.

Each invocation uses unchanged projectV2 against only its acknowledged live
suffix plus the actual next checkpoint event. The pure recovery projection then
merges it with the original authenticated closed timeline/task prefix and
revalidates through the ordinary V2 codec. Original event strings, order,
sequences, timestamps and summaries remain exact, including opaque historic
task IDs. Original referenced, associated, executed, compensation-obligated and
reference-closure descriptors stay retained. Declaration-only unassociated
Pending placeholders may disappear when actual live descriptors arrive; they
never become phantom execution/association. Common executed IDs retain immutable
kind, target, relation and parent. Unsupplied historic closed descriptors remain
byte-for-byte unchanged. Exact access, authorization, inputs, definition,
retention, calltree reference, identity and createdAt are protected; checked next
sequence and monotone updatedAt refuse overflow/conflict before provider writes.
No transient rehydration/queue event enters signed history.

The exceptional zero-model Terminal request applies only to an adopted recovered
Submitted/Running Pending snapshot with confirmed native cancelClaimed and an
actual Cancelled local result. Both engine eligibility guards use that exact
bridge predicate, preserve no-active/pending-work and shared once-only settlement,
and the bridge authenticates fresh original-owner intent before the write. The
closed evidence source supplies exact access, next revision and Cancelled result;
original signed tasks/history/protected metadata survive and only the admitted
terminal event appends. No Start/Running checkpoint, work/cancel payment, getter,
duplicate event or general empty-task permission is introduced. Real stale/lost
terminal acknowledgement retains refusal and suppresses canonical success.

NR2/NR12..NR20/NR24 in
[JobDurableLifetimeRecoverySpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeRecoverySpec.scala)
author these actual engine and provider boundaries with adjacent GWT/should.
Fresh focused validation and independent review remain pending; no Step/Phase
closure follows authored source.


## D1A keyed durable evidence — authored, selected validation passed — 2026-10-03

slice=JM69-09D1A
implementation=authored-selected-validation-passed-independent-complete-Step-pending
current_receipts=REP004-and-ACC001-selected-validation-passed

The original boundary-only Admission/StartIntent/RunningIntent source cannot
independently address overlapping Jobs or the actual retained next revision.
The additive [Keyed Durable Evidence Contract](durable-job-keyed-evidence-contract.md)
fixes R1–R11 for a private outer JobId/expectedrevision/original-request envelope,
matching response provenance and exactly one keyed resolve without legacy
fallback. The six original nested requests, old Function1 alias/constructor,
native signed schema, quota/payment, protected recovery fields, projection,
acknowledgement and stale/ambiguous refusal semantics remain unchanged.

| Example | Actual executable leaf | Rules and observable behavior | Status |
|---|---|---|---|
| KE1 | [KE1 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L28) | R2,R4,R5; Every original boundary; exact Job/revision/request/pair, one resolve | selected-validation-passed; complete-Step-review-pending |
| KE2 | [KE2 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L50) | R4,R5; Foreign Job response refuses with fixed redacted diagnostic | selected-validation-passed; complete-Step-review-pending |
| KE3 | [KE3 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L67) | R4,R5; Stale/future response revision refuses; matched projection stays untouched | selected-validation-passed; complete-Step-review-pending |
| KE4 | [KE4 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L88) | R3,R5; Same provider Failure object; final legacy apply refuses without resolve | selected-validation-passed; complete-Step-review-pending |
| KE5 | [KE5 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L110) | R4; Null/nonpositive/oversized admission guards; inclusive native 512 UTF8 bytes | selected-validation-passed; complete-Step-review-pending |
| KE6 | [KE6 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L143) | R5; Null response envelopes refuse; matched missing pair remains bridge-owned | selected-validation-passed; complete-Step-review-pending |
| KE7 | [KE7 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L163) | R3,R6; Actual legacy Function1 admission singleton, original failure and no publication/body | selected-validation-passed; complete-Step-review-pending |
| KE8 | [KE8 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L192) | R2,R4,R5; Independent opaque key/revision property; seed69709701, shrinking, minimum24 | selected-validation-passed; complete-Step-review-pending |
| KI1 | [KI1 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L33) | R6,R7,R8,R9; Latch-controlled actual two-Job overlap; independent signed revisions1..6 | selected-validation-passed; complete-Step-review-pending |
| KI2 | [KI2 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L94) | R6,R7,R8,R9; Genuine Published root/ordinary revision2/fresh SQLite recovery; requested3..7 | selected-validation-passed; complete-Step-review-pending |
| KI3 | [KI3 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L168) | R5,R6,R7,R8; Four native faults at all six boundaries; exact prior ack/paid authority/body distinction | selected-validation-passed; complete-Step-review-pending |
| KI4 | [KI4 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L259) | R6,R7,R8,R9; Independent admission/drain permutations and caller recipes; real SQLite seeded shrinking minimum24 | selected-validation-passed; complete-Step-review-pending |

Each leaf has semantic afterWord metadata spec:durable-job-keyed-evidence,
example:KE/KI, governing rules, phase:69.7 and slice:JM69-09D1A, adjacent actual
Given/When/Then and should assertions. KE8 and KI4 author seed69709701,
normal shrinking and minimum24 successful cases per new suite. SQLite children
are owned under target/keyed-durable-evidence/work and cleaned in finally.
These authored specifications passed selected current validation; complete
independent Step review remains pending.

Historical C5 REP00255/eight suites and ACC001608/75 suites/one original pending
retain their exact tested subjects, prior REP001 compiler failure and frozen
repair chronology. They are not rebound to these new bridge edits.
Fresh parent-selected five-suite representative and ordered77-suite accumulator
passed; original75 suites and the existing pending stay intact. Evidence is below.

Phase69.7/JM69-09 remain IN_PROGRESS; JM69-10 remains OPEN.
All original full-Step/Phase gates remain unchecked. Complete original Step
protected review, all unwaived M2 and CPBs (including C4 bootstrap M2 and
CPB-PH697-NRT-DISPATCH-REFUSAL-01 / CPB-PH697-NRT-CANCEL-SETTLEMENT-01),
remaining native/all17 correlation and configured production owner/lease/health/
maintenance, integrity/backup/restore/migration, actual JM69-10 two-process/CBD
acceptance, predecessor69.4–69.6 ancestry/deferred audits, aggregate full
validation, sole epoch1 full Phase review and local release are still required.
No partial Step/Phase acceptance or commit, push, publication, deployment or
successor work is claimed.


## D1A selected validation evidence — 2026-10-03

`PH697-D1A-REP-004` passed **68/68 tests in5 suites**, zero failed/pending.
`PH697-D1A-ACC-001` passed **620/620 tests in77 suites**, zero failed
and one original pending example. The ordered accumulator keeps the original75
suites and appends the two keyed-evidence suites. All twelve KE1–KE8 and KI1–KI4
leaves passed; KE8 and KI4 retained seed69709701, ordinary shrinking and at least
24 successful checks each. Actual SBT/wrapper/current-subject reader exits were0,
permissions consumed and final serial locks released. Compiler warnings were0;
JVM runtime diagnostics remain separately classified.

The original failures remain recorded. REP001 failed Test/compile at nine
abstract Consequence.conclusion accesses; the exact two-Spec repair extracts the
original Conclusion via Failure and fails on unexpected Success. REP002 failed
at Conclusion.code; the one-line repair uses status.webCode.code while retaining
full Status equality and effective-message equality. REP003 compiled, with
66/68 tests passing: KI2 recovery refused and KI4 shrinking raised an invalid
JobId recovery bridge. Fixed hyphens in test entropy collided with the existing
UniversalId delimiter and EntityId alphanumeric/underscore policy. The two
fixed literals now use keyed_recovered_root and first_/second_; independent
generators, shrinking, GWT, assertions and numeric signed/native authority
expectations remain unchanged. All three failed receipts retain reader2,
SBT/wrapper1 and released locks. The three byte-changing pre-review repairs
are complete; no independent review or original repair-counter reset occurred.

These receipts retain their actual tested eleven-path subjects. This six-doc
factual projection changes no tested program or declared input and does not
rebind old receipts. Five tested programs,141 preserved paths and425 declared
source observations remain exact, including the admitted external Cozy context.
The additional EntityId source read confirmed its delimiter-safe entropy rule;
no upstream source was edited or external feature acceptance claimed.

Phase69.7/JM69-09 remain IN_PROGRESS; JM69-10 remains OPEN. D1A supplies one
production prerequisite. Complete original protected Step review, all prior
unwaived M2/CPBs, remaining native/all17 correlation, configured production
owner/lease/health/maintenance, integrity/backup/restore/migration, actual
two-process/CBD fresh-runtime acceptance, predecessor69.4–69.6 ancestry/deferred
audits, Step acceptance/local commits, aggregate full validation, sole epoch1
full Phase review and final local release remain required.
