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
5. Insert that fact into the isolated engine terminal-fact map, or retain a
   structured refusal.

No lookup occurs before candidate admission. This path has no runtime
rehydration port and never invokes a provider.

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
terminal fact with a stable query facade while no executable record, timer,
queue entry, provider call, task run, or external effect is created. (R1-R5)

### E2 Closed refusal

Missing, corrupt, access-refused, nonterminal, unrepresentable, or
identity-colliding candidates retain their corresponding closed refusal fact
and never enter the terminal map. (R1-R4)

### E3 Fresh ephemeral engine

A fresh in-memory engine with no successful terminal recovery has an empty
terminal-fact map and no terminal query facade. (R4-R5)

## Explicit non-goals

This slice adds no public API, generic `EntityStore`/`DataStore` search,
schema/migration, raw payload or raw result reconstruction, provider registry
or invocation, live task/context construction, scheduler/queue admission,
execution, retry, control, Entity synchronization, cursor/result/control
surface, Phase 69.2 work, publication, or deployment.
