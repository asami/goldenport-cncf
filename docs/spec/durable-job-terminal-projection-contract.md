# Durable Job Terminal-Projection Contract

status=normative
phase=JM69-03H
source-authority=`P69.1-JM69-03-TERMINAL-PROJECTION-001`

This contract defines the package-internal, engine-owned projection of an
already admitted terminal durable Job fact. It is a read-model boundary: it
retains the exact closed durable snapshot and derives the existing
`JobQueryReadModel` only. It does not reconstruct an executable `JobRecord`,
`JobResult`, `JobTask`, `ExecutionContext`, provider, resolver, or payload.

## R1 Terminal-only admission order

For every candidate, the order is immutable:

1. Validate the bounded, provider-owned candidate vector under the existing
   startup-recovery rules.
2. Admit exactly that candidate through `DurableJobStore`, including canonical
   codec, access, storage identity, and durable-body identity validation.
3. Classify the admitted record with unchanged `DurableJobRecovery`.
4. Project only a lifecycle/result-consistent terminal decision to a closed
   terminal fact.
5. Admit its bounded pure signed footprint and refuse locally known identity,
   native-binding and budget collisions before provider work.
6. Authenticate already `Published` V2 native lifetime authority read-only with
   the exact supplied store, candidate access, caller recovery context and
   complete selected policy. Require the entire admitted signed snapshot and
   provider revision to equal the authenticated current snapshot. Its exact
   paid Seed must cover executed normal IDs, history and compensation claims.
7. Reserve retained physical capacity, then publish only the closed terminal
   projection, exact Seed and private quota/access/context binding with
   `bridge=None`, or retain a structured refusal.

No lookup occurs before candidate admission. This path has no runtime
rehydration port or live provider reconstruction. Its authorized store and
native quota reads use the caller-owned persistence provider; registration
performs no native write, payment, bootstrap, floor import or root publication.

## R2 Eligible terminal facts

Only an admitted durable record classified as exactly one of
`terminal-succeeded`, `terminal-failed`, or `terminal-cancelled` is eligible.
Its durable lifecycle and durable result outcome must match that decision.
The durable Job and task references must also be representable by the existing
query facade; otherwise projection is refused before insertion.

A terminal task facade is representable only when its transaction outcome is
closed: `Committed` projects to succeeded / `committed`, `Failed` projects to
failed / `failed`, and `Compensated` projects only when its compensation
descriptor is succeeded or failed, respectively as succeeded /
`compensation-committed` or failed / `compensation-failed`. Pending,
missing, not-required, and pending compensation facts are refused. The facade
uses the durable compensation operation id as its compensation action
reference; the exact durable descriptor remains only in the retained snapshot.

Missing storage remains `missing`; canonical/structural admission or
classification failure remains `corrupt`; access denial, nonterminal outcome,
unrepresentable query reference, or identity collision remains `refused`.
Resumable, retryable, recovery-required, submitted, running, suspended, and
started records never enter the terminal map.
Missing/Pending/corrupt native authority, V1 recovery, owner/policy mismatch or
stale whole-snapshot/provider-revision proof likewise refuses. It cannot borrow
unbound bootstrap floors or recreate a surviving ledger.

## R3 Closed terminal-fact model

`DurableJobTerminalProjection` retains the canonically admitted
`DurableJobStoreSnapshot` and the closed recovery decision package-internally.
Those retained values preserve the durable identity, revision, authorization,
lifecycle, task descriptors, input/result metadata, timeline, diagnostics,
definition, retention, integrity, and provider revision exactly.

The derived `JobQueryReadModel` exposes only existing management-facing
facade fields. It carries a durable origin and terminal status/summary, but no
raw `JobResult`/`OperationResponse`, input payload body, calltree body, live
task, execution context, provider, action engine, unit of work, clock, timer,
queue, closure, credential, or external-effect capability.

## R4 Engine isolation and collision handling

`InMemoryJobEngine` keeps terminal facts in a dedicated private map. That map
is not either `JobRecord` map and has no scheduler, timer, queue, Entity,
ActionEngine, UnitOfWork, task, retry, control, or synchronization path.
Before insertion, the engine refuses an id already present in either executable
durable/runtime map or its terminal-fact map. Runtime rehydration likewise
refuses an id already retained as a terminal fact. A terminal fact therefore
cannot become a runnable record or overwrite an executable identity.

The private native ownership binding carries no lifecycle-write capability and
requires no configured bridge. Its `None` is authoritative even if a bridge is
configured later; only the absence of any native binding permits the ordinary
configured-bridge fallback. Exact numeric `lifetimeSnapshot` diagnostics consult
the original authorized quota/context. This read-only ownership handle is
separate from the closed facade and never constructs an executable record.
Identity or physical capacity refusal preserves both signed bytes and native
paid authority and publishes no terminal fact or Seed.

The map is process-local. A new fresh ephemeral engine has no terminal facts
until this terminal-only recovery path successfully registers one.

## R5 Existing read-only facade routes

The existing status, query, list, task-page, timeline-page, trace-tree, and
task-detail views read a registered terminal fact through its derived
`JobQueryReadModel`. No public `JobEngine` method is added or changed. Result,
await, control, retry, execution, submission, Entity synchronization, and
cleanup continue to operate only on executable `JobRecord` maps and do not
accept terminal facts.

## Examples

### E1 Terminal durable facts in a distinct process

An explicitly supplied succeeded, failed, or cancelled durable candidate is
canonically admitted and classified in a fresh engine. It registers one closed
terminal fact and exact native paid Seed with a stable query facade while no
executable record, timer, queue entry, reconstruction callback, task run or
external effect is created. Authorized persistence reads remain observable.
(R1-R5)

### E2 Closed refusal

Missing, corrupt, access-refused, nonterminal, unrepresentable, or
identity-colliding candidates retain their corresponding closed refusal fact
and never enter the terminal map. (R1-R4)

### E3 Fresh ephemeral engine

A fresh in-memory engine with no successful terminal recovery has an empty
terminal-fact map and no terminal query facade. (R4-R5)

The NR3/NR4/NR10 and NR23 behaviors in
[JobDurableLifetimeRecoverySpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeRecoverySpec.scala)
author exact native ownership, refusal and terminal reopening. Fresh focused
validation, independent review and Step acceptance remain pending; reopening
providers within one process does not close JM69-10 actual process acceptance.

## Explicit non-goals

This slice adds no public API, generic `EntityStore`/`DataStore` search,
schema/migration, raw payload or raw result reconstruction, provider registry
or reconstruction invocation, live task/context construction, scheduler/queue admission,
execution, retry, control, Entity synchronization, cursor/result/control
surface, Phase 69.2 work, publication, or deployment.
