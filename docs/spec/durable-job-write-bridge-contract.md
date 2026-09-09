# Durable Job Lifecycle Write Bridge Contract

status=normative
phase=JM69-03L

This contract defines the package-internal durable-write bridge used by a
configured `InMemoryJobEngine` for Persistent job admission, first normal
execution start, and engine-owned per-task start and post-body outcome
checkpoints. It does not define a public JobEngine API, provider selection
mechanism, recovery protocol, terminal lifecycle writer, or execution-effect
policy.

## R1 Closed boundary and evidence

The bridge has exactly five boundaries:

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

For each boundary, the configured engine supplies a closed request and
`DurableJobProjectionEvidence` and `DurableJobRecordAccess` pair. The evidence
source is explicit and package-private. `Admission`, `StartIntent`, and
`RunningIntent` requests carry their boundary only. `TaskStartIntent` carries
only the Job id, current Task id, current parent Task id, and the exact ordered
full `JobTaskReadModel` vector registered by the engine.
`TaskOutcomeCheckpoint` carries only the Job id, completed Task id, parent id,
and that same exact ordered full closed read-model vector. No request carries a
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

## R3 First normal start and task outcome order

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

### R4 Revision-three and task-bound durable state

The revision-3 record has lifecycle `Running`, result `Pending`, and the
existing nonempty closed task descriptors. It is a job-level intent only.
Revision 4 is still nonterminal `Running`/`Pending`, but declares the dynamic
start intent for one registered task. It intentionally does not copy the local
`task.running` event into the durable timeline: the sole new durable task event
is `task.durable-start-intent`. Revision 5 copies revision 4's durable timeline
and appends only `task.durable-outcome-checkpoint`; its descriptors exactly
correspond to the closed local task vector and its job result remains `Pending`.
Each later task start and outcome repeats that contiguous nonterminal sequence.
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
state.

Only a configured bridge that admitted the in-process job owns these gates.
TaskStartIntent and TaskOutcomeCheckpoint bridge writes are eligible only for
the initial normal synchronous or `JobRun` dispatch. Retry and control dispatch
retain their existing task execution semantics but execute neither task bridge
write.
The same TaskStartIntent and post-body TaskOutcomeCheckpoint order applies to
direct and queued same-job task dispatch. A TaskOutcomeCheckpoint refusal
returns the source Consequence failure for direct dispatch without a fabricated
TaskOutcome and prevents settlement or canonical observation. Unconfigured
Persistent and Ephemeral paths retain their existing behavior. Compensation,
retry, cancellation/control, startup recovery, and rehydrated paths do not
obtain a durable write from this contract.

## R6 Retained and excluded state

The bridge retains no raw payload or result, provider, resolver, credential,
TaskOutcome, JobTask, ActionEngine, UnitOfWork, or ExecutionContext. It has no
generic storage search and no public capability. Its closed refusal observation
carries only Job id and Admission, StartIntent, RunningIntent, TaskStartIntent,
or TaskOutcomeCheckpoint category, never a provider body or failure detail.

A successful StartIntent records intent to begin normal execution. A successful
RunningIntent records only the job-level intent immediately before normal
execution. A successful TaskStartIntent records only one task's pre-body
intent. A successful TaskOutcomeCheckpoint records only a closed task
descriptor state with a Pending job result; it does not persist a raw result,
failure object, UnitOfWork state, or external-effect value. None of these
claims makes retry, terminal, UnitOfWork, or external-effect state durable.
Terminal, UnitOfWork, and external-effect ordering remain reserved for separate
approved lifecycle work.
