# StateMachine / Composite StateMachine / Workflow Runtime v1 Decisions

Status: design baseline
Date: 2026-09-05

## Purpose

Freeze the five cross-cutting decisions agreed while refining CML Composite
StateMachine/Workflow and CNCF runtime integration.

## 1. Sequential Semantics by Default

v1 Composite StateMachine processing is deterministic and sequential.

```text
one committed constituent transition
  -> one composite re-evaluation
  -> zero or one derived composite transition
  -> one causally ordered action program
```

Multiple constituent transitions are processed one at a time in committed order.
Intermediate composite states are therefore observable according to that order.

Parallel semantics are future/optional and must be explicit opt-in model
semantics, not a transparent performance optimization. An eventual parallel
mode must define grouping/barrier/join, configuration snapshot, conflict,
causation, and action-order semantics separately.

## 2. Canonical Transition Occurrence / Causation Record

Runtime transition occurrences are first-class durable execution facts, distinct
from CML transition definitions.

A constituent committed transition occurrence should preserve at least:

```text
occurrenceId
definitionId
machineId
subjectRef
fromState
toState
unitOfWorkId
correlationId
causationId?
occurredAt
```

A derived composite transition occurrence should preserve at least:

```text
occurrenceId
definitionId
compositeMachineId
causedBy                 // one occurrence in sequential v1
beforeState
afterState
beforeConfiguration
afterConfiguration
unitOfWorkId / causal UnitOfWork reference
correlationId
occurredAt
```

Nested composites form an explicit causation chain:

```text
constituent transition
  -> composite transition
  -> upper composite transition
  -> action occurrence
  -> Operation / Job
```

The same record supports audit/history, observability, idempotency, recovery,
test assertions, and runtime visualization.

## 3. Minimal Action Metadata

CML v1 action metadata is intentionally small:

```text
ActionMetadata
  actionId
  effectClass
  transactionRequirement
  idempotency
  compensationHandlerRef?
  ordering / provenance
```

Meaning/safety/re-execution semantics belong here. Retry count, backoff, timeout,
circuit breaker, transport tuning, and provider transaction configuration are
runtime policy.

CML logical actions compile to the existing CNCF execution algebra:

```text
CML action
  -> binding/compiler
  -> ExecProgram[A] = Program[UnitOfWorkOp, A]
```

No parallel StateMachine-specific executable algebra is introduced.

## 4. UnitOfWork / Compensation / Recovery Contract

CNCF UnitOfWork is completely atomic for admitted local effects:

```text
failure before commit
  -> complete rollback
  -> no partial local commit
  -> no successful CommittedTransition
```

2PC may optionally enlarge that atomic boundary when all required participants
support it. Required atomicity never silently downgrades.

Outside the committed UnitOfWork, business compensation is explicit application
logic through a registered compensation handler. CNCF does not synthesize an
inverse or automatic Saga chain in v1.

```text
external effect
  -> compensation required
  -> application compensation handler
       success -> compensated
       failure -> durable RecoveryRequired
```

`RecoveryRequired` must contain the original `UnitOfWorkId` plus causal
occurrence/correlation references. It is a durable execution fact.

CNCF responsibility ends at durable escalation of the unrecovered condition.
The business/manual repair procedure belongs to the application/administrator.
An explicit recovery Operation should normally re-enter CNCF through ordinary
authorization, UnitOfWork, and StateMachine boundaries.

Committed transition history is never erased. Business reversal is a new
explicit transition/action.

## 5. Definition Version Pinning

StateMachine, Composite StateMachine, and Workflow behavior is fixed when the
runtime instance is created.

```text
instance
  definitionId
  definitionVersion
  createdAt
```

An existing instance continues using its pinned definition version even after a
new definition is published. New instances use the newly selected/current
version.

This pinning includes all behaviorally relevant semantics, including:

- states/transitions/triggers;
- guards/predicates;
- composite-state derivation rules;
- constituent bindings;
- logical action bindings/program generation contract;
- action metadata relevant to execution meaning; and
- compensation handler binding applicable to that definition version.

No silent semantic upgrade of a running instance is permitted.

Instance migration, if introduced later, is an explicit operation with explicit
validation. It is not part of v1. A future migration must validate that current
constituent configuration maps coherently under the target definition and that
pending/recovery execution state is safe to migrate.

## Consequence

The v1 model favors deterministic explainable execution:

```text
pinned CML definition
  + sequential transition occurrence
  + stable causation / UnitOfWork identity
  + existing Free x UnitOfWork execution
  + complete local rollback
  + explicit application compensation
  + durable recovery escalation
```

This baseline is intentionally conservative. Parallel semantics, automatic
multi-step compensation orchestration, and live instance migration can be added
later as explicit features without weakening v1 guarantees.
