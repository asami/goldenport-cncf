# Durable Job Runtime-Rehydration Contract

status=normative
phase=JM69-03G
source-authority=`P69.1-JM69-03-RUNTIME-REHYDRATION-001`

This contract defines the package-internal, provider-owned transition from one
explicitly supplied durable startup candidate to a fresh in-process runtime
`JobRecord`. It extends the observational startup-recovery handoff in
[`durable-job-startup-recovery-contract.md`](durable-job-startup-recovery-contract.md)
without changing its overload or its closed report.

## R1 Provider-owned transient port

`DurableJobRuntimeRehydrationPort` receives exactly one explicit startup
candidate, its canonically admitted `DurableJobStoreSnapshot`, and the existing
`DurableJobRecoveryDecision`. It returns the matching live `JobId`, nonempty
`JobTask` list, `ExecutionContext`, and the input and definition reconstruction
values for one new runtime record.

The port is package-private and provider-owned. It is neither a provider
registry nor framework-global provider selection. Its result is transient: no
port, provider object, resolver, credential, closure, raw payload, raw result
body, or failure body is retained in the runtime-rehydration report or durable
record format. Input metadata copied to the runtime record has its inline and
blob payload values removed before the existing management projection is made.

## R2 Immutable admission order

Each candidate follows this order exactly:

1. Validate the explicit bounded candidate vector.
2. Admit the candidate through the existing `DurableJobStore` canonical,
   access, and durable-identity path.
3. Classify the admitted record using unchanged `DurableJobRecovery`.
4. Invoke the provider port only for an allowed classification.
5. Validate the transient result and match its runtime Job id to both the
   candidate id and admitted durable identity. After all existing structural
   and identity checks, admit the complete runtime input under the
   [Job Input Resource Contract](job-input-resource-contract.md). Input refusal
   precedes record construction, clock read, sanitizer, resource reservation,
   runtime insertion, queue and timer effects. Admit the complete live task batch
   and signed task/timeline/diagnostic/retry footprint under the
   [Job Lifetime Resource Contract](job-lifetime-resource-contract.md). This is
   bounded pure observation of declarations, executed normal obligations,
   timeline rows, diagnostics and retries, not a source of paid credits.
6. Refuse locally known runtime, terminal, native-binding and budget collisions,
   quiesce, or an absent/conflicting lifecycle bridge. The explicitly configured
   bridge must own the identical supplied `DurableJobStore` object.
7. Authenticate already `Published` V2 lifetime authority read-only, using that
   store, the candidate access, the caller recovery `ExecutionContext`, and the
   complete selected native policy. Require equality of the entire admitted
   signed snapshot and its provider revision. Check the observed footprint and
   normal/compensation obligations against the exact native Seed, plus unused
   normal capacity for the complete uncancelled live batch.
8. Construct the safe record, reserve original retained/pending capacity, and
   atomically adopt the acknowledged bridge entry. Only then publish the shared
   State binding and exact Seed, commit the physical lease, insert the record,
   and register its existing due state.

Registration performs no root reservation/publication/recovery, Work or Retry
payment, historical floor, maximum, or regenerated Cancel credit. Physical
reservation or bridge-adoption refusal releases only the provisional physical
lease, preserving signed bytes and native paid authority.

No port invocation occurs for invalid discovery, missing, corrupt, refused,
terminal, running, suspended, started, recovery-required, or proof-incomplete
candidates. A failed, empty, malformed, or id-mismatched port result is
refused before runtime insertion, queue insertion, or timer registration.
Input capacity/shape refusal also reports the existing closed `refused` fact.
The eligible source, native load and provider port have already executed and
remain observable; refusal leaves the native revision/timestamp unchanged.
Valid input retains existing sanitization of inline and Blob raw references,
every other metadata/timestamp, and normal due-state execution. C1 executable
IR1..IR6 scenarios are authored in
[JobInputRecoveryResourceBoundsSpec](../../src/test/scala/org/goldenport/cncf/job/JobInputRecoveryResourceBoundsSpec.scala);
actual C1 `PHASE-69.7-JM69-09B3B3C1-VAL-003` discovered 47 suites and passed
410 tests, zero failures, zero compiler warnings, with one existing
SqliteDataStore pending example. Independent Step review, acceptance and commit
remain pending. [C2A lifetime admission](job-lifetime-resource-contract.md)
adds pure signed/live footprint admission before runtime construction. Authored
C2B2B2 replaces native recovery floors with the exact authenticated Seed and
explicit store-bound ownership. Missing, Pending, corrupt, foreign-owner,
policy-mismatched, stale or V1 authority refuses without importing local floors.
Its fresh focused validation, independent review, Step acceptance and commit
remain pending; all original Phase obligations remain mandatory.

Positive LR5/IR5/IR6 fixtures declare each transient reconstructed input by its
original ordered name and `DurableValue.Absent` before genuine native
reserve/create/publish and evidence-source capture. Absent means no durable
value was admitted for that declaration. It retains count/name matching and
signed input equality through every acknowledgement; it does not persist raw
payloads or change runtime legacy-SHA, TTL, timestamp or sanitizer checks.
Malformed negative inputs retain their earlier pure refusal boundary.

## R3 Eligible lifecycle

Only a canonically admitted V2 record with `Submitted` lifecycle,
`schedule.startedAt` absent, `recoveryRequired = false`, complete six-category
replay evidence, and a `resumable` or `retryable` classifier decision reaches
the port. `resumable` requires no durable retry due time; `retryable` requires
one. The classifier remains the sole owner of durable lifecycle truth.

The runtime record is always `Persistent` and `Submitted`; it preserves the
admitted lifecycle priority, run mode, schedule, retry evidence, durable Job
identity, and durable identity `createdAt` and `updatedAt` values exactly, plus
the provider-supplied transient live construction values. The new transient
`job.runtime-rehydrated` timeline event may carry the engine's current
occurrence time, but that time never rewrites durable identity fields. It does
not synthesize a terminal `JobResult` or claim to reconstruct terminal history.
If best-effort Job Entity synchronization records a diagnostic during runtime
rehydration, that diagnostic likewise retains both admitted durable identity
timestamps; its transient annotation time remains independent.

## R4 Closed registration report

The runtime overload retains a package-private vector of candidate ordinal/id
and one closed fact: `registered` or `refused`. It contains no durable body,
decision detail, provider result, error body, live task, execution context,
input payload, credential, provider object, closure, or public recovery API.
The existing observational overload continues to retain its unchanged startup
report.

## R5 Existing scheduling only after insertion

After a successful runtime insertion, a future scheduled start uses the
existing delayed-start timer and a future retry uses the existing delayed-retry
timer. A schedule or retry already due enters the existing queue through its
existing due-state mechanism. Recovery code never directly calls `JobTask.run`,
`ActionEngine`, `UnitOfWork`, or an external effect.

## R6 Recovered ownership, signed checkpoints and prepaid safety

Same-State owners share the installed native quota/access/recovery-context,
bridge and cancellation scope. Pure declarations with Pending transaction and
no task event, association or compensation obligation are not executed normal
IDs. Actual executed and referenced normal IDs, companion originals and claims
must be covered by the native Seed. Historic task strings remain closed values.

A recovered normal `JobRun` starts from acknowledged semantic revision N:
`StartIntent` acknowledges N+1, `RunningIntent` N+2, then actual TaskStart,
TaskOutcome and Terminal requests continue contiguously under original access,
evidence and OCC. The initial signed task/history prefix and protected metadata
remain exact; only the validated current invocation suffix is appended.
Declaration-only placeholders may be replaced by actual live descriptors.
Transient rehydration/queue events never enter signed history. Overflow, stale
or ambiguous acknowledgement withholds subsequent body/canonical success and
retains genuine paid/refusal authority. New-root revisions 1/2/3 remain intact.

Persisted `cancelClaimed` is observed before metadata getters or timer/pending
work registration. Recovery retains a task-free safety record and installs the
cancelled shared scope, then applies original once-only safety settlement with
no second cancel claim or payment. The narrow zero-model terminal checkpoint
requires an adopted recovered binding, fresh authenticated native Cancel intent,
and an actual local Cancelled result. It preserves original signed tasks/history
and appends only the admitted Cancelled terminal event. Real stale/lost terminal
acknowledgement retains refusal and suppresses canonical success. Explicit Retry
alone pays through its existing native gate and resets scope/intent; recovery
`RetryRun` and `Control` retain `taskbridgewrites=false`.

[JobDurableLifetimeRecoverySpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeRecoverySpec.scala)
authors NR1..NR24 with actual SQLite close/reopen, independent literal paid
oracles, bounded ownership races, and Seed6970933641 with at least24 finite
shrinking runs. These are authored scenarios, not fresh validation or JM69-10
real two-process/CBD acceptance.

## Examples

### E1 Scheduled runtime registration and normal queue execution

A provider port may reconstruct a proof-complete V2 Submitted candidate with a
future durable schedule. The engine reports `registered`, retains one timer,
and executes the live task only after normal timer and queue processing.
(R1-R5)

### E2 Delayed-retry runtime registration and normal queue execution

A provider port may reconstruct a proof-complete V2 Submitted delayed-retry
candidate. The engine reports `registered`, retains the delayed-retry timer,
and executes only after its due state joins the normal queue. (R1-R5)

### E3 Refused candidates and port failures

Missing, corrupt, access-refused, running, incomplete-proof, terminal,
failed-port, empty-result, and id-mismatched candidates report `refused`. They
produce no runnable record, timer, queue work, task run, ActionEngine call, or
UnitOfWork action. (R2-R5)

## Explicit non-goals

This slice adds no public `JobEngine` API, generic `EntityStore`/`DataStore`
search, durable record schema or migration, provider registry, global provider
selection, raw-payload persistence, cursor/control/query surface, terminal
result synthesis, Phase 69.2 work, deployment, or publication.
