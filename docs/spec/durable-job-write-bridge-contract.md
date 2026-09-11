# Durable Job Lifecycle Write Bridge Contract

status=normative
phase=JM69-03N

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

1. closed Admission evidence and access;
2. V2 projection of the exact Submitted JobRecord;
3. DurableJobStore create at semantic revision `1`;
4. cancellation-scope creation, local JobRecord insertion, JobEntity sync,
   submitted event, timer or queue registration, and normal synchronous
   execution.

If evidence, projection, or create refuses, the original Consequence failure
is returned. The engine observes the existing task admission failure and a
package-private closed Admission refusal fact, but creates no executable local
job, work item, event, timer registration, or task execution.

An unconfigured Persistent engine retains its existing behavior. Ephemeral
submission bypasses the bridge entirely.

## R3 Initial job start and in-process task outcome order

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

The bridge retains only the returned current DurableJobStoreSnapshot keyed by
Job id. A failed StartIntent or RunningIntent evidence request, projection, or
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
task vector; unconfigured Persistent and Ephemeral behavior remains unchanged.

A succeeded terminal candidate with a retained `Failed` model that does not
meet the R4 retry-count-and-pair predicate is refused as a nonterminal durable
snapshot. It cannot produce a terminal event or canonical task-outcome
observation by claiming the ordinary all-succeeded form.

Only a configured bridge that admitted the in-process job owns these gates.
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
Persistent and Ephemeral paths retain their existing behavior. Compensation,
startup recovery, and rehydrated retry paths do not obtain a durable write from
this contract. Cancellation/control dispatch itself obtains none; only its final
closed cancellation settlement may cross TerminalOutcomeCheckpoint.

## R6 Retained and excluded state

The bridge retains no raw payload or result, provider, resolver, credential,
TaskOutcome, JobTask, ActionEngine, UnitOfWork, or ExecutionContext. It has no
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
