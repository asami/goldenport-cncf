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
   candidate id and admitted durable identity.
6. Insert the fresh runtime record.
7. Register its existing due state.

No port invocation occurs for invalid discovery, missing, corrupt, refused,
terminal, running, suspended, started, recovery-required, or proof-incomplete
candidates. A failed, empty, malformed, or id-mismatched port result is
refused before runtime insertion, queue insertion, or timer registration.

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
