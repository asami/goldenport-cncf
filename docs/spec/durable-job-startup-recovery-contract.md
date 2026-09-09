# Durable Job Startup-Recovery Contract

status=normative
phase=JM69-03F
source-authority=`P69.1-JM69-03-BOUNDED-RECOVERY-001`

This contract defines the bounded, package-internal startup-recovery handoff.
It is observational: it admits and classifies explicitly supplied durable Job
ids, then retains only closed facts. It neither discovers records through a
generic store query nor reconstructs, schedules, resolves, or executes work.

## Provider-owned discovery

### R1 Candidate ownership and shape

`DurableJobStartupRecoverySource` owns candidate discovery. On one invocation
it returns an already bounded, deterministic vector of candidates. Each
candidate has a zero-based stable ordinal, a nonblank unique durable Job id,
the existing `DurableJobRecordAccess` required for admission, and the six
opaque `DurableReplayEvidence` tokens required by
[`durable-job-recovery-contract.md`](durable-job-recovery-contract.md).

### R2 Requested-bound admission

The coordinator rejects a nonpositive requested bound before calling the
source.

### R3 Provider-response admission

The coordinator rejects a response larger than the requested bound, a blank
id, a duplicate id, or any ordinal that is not the corresponding zero-based
vector position. These failures happen before any `DurableJobStore` load.

### R4 Discovery isolation

The coordinator never calls `EntityStore.search`, `EntityStore.searchInternal`,
or any DataStore search API; the source physically owns both discovery and its
bound.

## Engine-owned recovery facts

### R5 Durable-store admission boundary

For each admitted candidate, the coordinator loads exactly that id through
`DurableJobStore`, preserving its existing entity identity, canonical codec,
authorization, and record-identity validation path. Only a successful
authorized load reaches `DurableJobRecovery`.

### R6 Closed recovery facts

The resulting report contains only the candidate ordinal/id and one closed
fact:

- `decision`, containing the existing terminal, resumable, retryable, or
  recovery-required classifier decision;
- `missing`, when the explicitly supplied id has no durable record;
- `corrupt`, when canonical admission or classification cannot produce a
  complete durable fact; or
- `refused`, when the durable record access boundary rejects the supplied
  access.

### R7 Report retention

The report does not retain a durable record, provider failure/body, task,
Action, ActionEngine, resolver, ExecutionContext, credential, raw payload or
result body, closure, clock, scheduler, or replay command. The engine keeps
the latest successful report only for package-internal diagnostics.

## Safety and lifecycle boundary

### R8 Classifier and no-execution boundary

The coordinator delegates all lifecycle decisions to the existing pure
classifier. Consequently, terminal facts remain terminal; only proof-complete
V2 unstarted Submitted records can be reported as `resumable` or `retryable`.
Running, Suspended, started, recovery-required, incomplete-proof, missing,
corrupt, and refused records remain non-executable facts. A report never
queues work, invokes a task/ActionEngine/UnitOfWork, starts a timer, resolves a
provider, or creates an external effect.

### R9 Ephemeral omission

Ephemeral work has no durable record and is not discovered by this boundary.
Its omission from a provider-owned source produces no startup candidate and no
recovery action.

## Examples

### E1 Terminal fact in a fresh engine

A provider-owned terminal candidate is reported as its terminal decision in a
fresh engine without search, scheduling, task execution, or retained runtime
work. (R1, R4-R8)

### E2 Proof-complete and incomplete submitted candidates

Submitted candidates with complete evidence are reported as resumable or
retryable as applicable, while incomplete evidence remains recovery-required.
(R1, R5-R8)

### E3 Interrupted running candidate

A running candidate whose durable execution checkpoint has started remains a
recovery-required fact and never becomes replay work. (R5-R8)

### E4 Missing, corrupt, and refused candidates

Missing storage, corrupt canonical storage, and valid storage denied by record
access are reported as the corresponding distinct closed facts. (R5-R8)

### E5 Invalid provider response

Nonpositive bounds, oversized responses, blank ids, duplicate ids, and
nonmatching ordinals fail before durable-store registration or load. (R1-R4)

### E6 Deterministic candidate admission property

Generated bounded positive unique ordinal sequences preserve ordinal/id order
and report only missing facts against a fresh empty store. (R1-R6)

### E7 Ephemeral omission

A provider source that omits runtime-only Ephemeral work reports only its
durable candidates and produces no execution. (R1, R4, R8-R9)

## Explicit non-goals

This slice adds no generic storage enumeration, cursor/control API, durable
schema or migration, provider implementation selection, live task/context
reconstruction, automatic replay, continuation checkpoint, scheduler dispatch,
public operator surface, or Phase 69.2 query/control behavior.
