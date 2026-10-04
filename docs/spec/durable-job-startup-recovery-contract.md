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

All three coordinator entry paths (`recover`, `recoverRuntime`, and
`recoverTerminal`) admit requested `maxCandidates` only in **1..1024 inclusive**
through one shared source-admission helper. Admission precedes any discovery,
EntityStore collection registration/load, runtime port, registration callback,
scheduling, timer, task, event, identity allocation or dispatch.

A null source or store refuses before discovery. Runtime recovery also requires
a non-null port and registration callback; terminal recovery requires a non-null
registration callback. The source is called exactly once with the unchanged
admitted bound. A valid empty vector succeeds with an empty report and no store
or port effects.

### R3 Provider-response admission

The entire response is admitted before processing its first candidate. A null
Consequence, source Failure, NonFatal discovery exception, null vector, or vector
larger than the requested bound refuses. Each candidate must be non-null, with a
non-null/nonblank Job id, exact unique opaque id value, and ordinal equal to its
zero-based vector position. No truncation, sorting, normalization, canonical id
parsing or prefix processing occurs.

Access must be non-null, with non-null/nonblank tenant and subject ids, a
non-null scope set, and only non-null/nonblank scope strings. Empty scopes are
structurally valid. Shape admission grants no authorization: intact records
with unauthorized tenant, subject or scopes keep their original per-record
`Refused` fact through the unchanged DurableJobStore boundary.

Replay evidence and all six Option containers must be non-null. `None`,
`Some(blank)` and `Some(null)` remain structurally admitted as incomplete proof;
`DurableReplayAssessment` retains its existing ordered failed categories,
RecoveryRequired decisions and terminal decisions. No proof token is resolved.

Every source/admission failure returns exactly
`Consequence.argumentInvalid("Durable Job startup recovery batch refused")`.
Provider text, credentials, identities, access and ordinals never enter that
error. Scala NonFatal handling retains the existing fatal/interruption policy.
Refusal has zero store/port/callback effects even with a valid prefix and invalid
final candidate, and publishes no report or replacement of the last successful
engine report. Admitted order, exact identity/access/evidence, Missing, Corrupt,
Refused and Decision facts and eligible runtime/terminal reconstruction retain
their original semantics.

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


## Phase 69.7 B3B3A executable evidence

[DurableJobStartupRecoveryResourceBoundsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStartupRecoveryResourceBoundsSpec.scala)
authors R1..R10: inclusive requested bounds/dependencies, exact ordered missing
and empty reports, redacted discovery failures, oversupply, late malformed
identity/access, duplicates/ordinals, proof-container versus incomplete-token
semantics, actual signed authorization, exact successful-report retention, and
24 seeded finite generated checks. Each behavior has adjacent actual
Given/When/Then and should matchers. Existing startup/runtime/terminal Specs
remain unchanged positive controls. B3B3A focused receipt
`PHASE-69.7-JM69-09B3B3A-VAL-002` discovered 36 suites and passed 310 tests,
with zero failures and zero compiler warnings; the existing SqliteDataStore
pending example remains. Independent Step review and commit remain pending;
input, cumulative task/history/lifetime, query/cursor pages, control timers,
C/D and all JM69-10 acceptance remain mandatory.
