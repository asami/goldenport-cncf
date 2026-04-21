# Event Reception Policy Selection

Status: draft note (non-normative)  
Updated: 2026-04-21

## 1. Purpose

This note consolidates the current Phase 13 direction for event reception
execution semantics.

It summarizes and extends the following earlier notes/documents:

- `docs/notes/event-reception-latest-processing-spec.md`
- `docs/notes/event-driven-job-management-decisions.md`
- `docs/design/event-driven-job-management.md`
- `docs/journal/2026/04/event-mechanism-extension-work-items.md`

This note is not yet the canonical design. It is a working consolidation note
for the next design step.

## 2. Problem Statement

The current runtime has a narrow continuation model:

- `SameJob`
- `NewJob`

That is no longer sufficient for the Phase 13 event collaboration model.

Event reception execution must now be selected by combining:

- event origin boundary
- event kind / selectors
- future context conditions such as ABAC-style caller constraints

Therefore, reception behavior should no longer be modeled as a single
continuation enum only.

## 3. Current Implementation Boundary

Current implementation already provides:

- `EventBus`
- `EventReception`
- `CmlEventDefinition`
- `CmlSubscriptionDefinition`
- `EventContinuationMode.SameJob`
- `EventContinuationMode.NewJob`
- standard event attributes for observability, job context, and security

This note does not replace those mechanisms.

The intended direction is:

- keep the current mechanisms
- reinterpret continuation as part of a broader reception policy
- extend the runtime so selection is deterministic and explicit

## 4. Canonical Direction

Event reception should be modeled as:

- `condition`
- `policy`
- deterministic policy selection

instead of:

- one fixed continuation mode per subscription

In other words, "reception mode" is conceptually a rule-based execution policy
selection.

## 5. Required Selection Axes

At minimum, reception policy selection must consider:

### 5.1 Origin Boundary

- same subsystem
- external subsystem

This distinction is essential because job, transaction, and retry semantics are
different across the boundary.

### 5.2 Event Classification

Initially:

- event name
- event kind
- event category
- selector attributes

Future extension may include:

- taxonomy-oriented event classification
- source component / componentlet

### 5.3 Context Condition

This is not required for the first slice, but the model should keep space for:

- principal / caller class
- security level
- capability / permission
- tenant / deployment scope
- ABAC-style attribute conditions

## 6. Recommended Conceptual Model

Recommended conceptual types:

```scala
enum EventOriginBoundary:
  case SameSubsystem
  case ExternalSubsystem

final case class EventReceptionCondition(
  originBoundary: Option[EventOriginBoundary] = None,
  eventName: Option[String] = None,
  eventKind: Option[String] = None,
  eventCategory: Option[CmlEventCategory] = None,
  selectors: Map[String, String] = Map.empty
)
```

```scala
enum EventExecutionTiming:
  case Sync
  case Async

enum EventJobRelation:
  case SameJob
  case NewJob

enum EventSagaRelation:
  case SameSaga
  case NewSaga

enum EventTransactionRelation:
  case SameTransaction
  case NewTransaction

enum EventFailurePolicy:
  case Fail
  case Retry

enum EventFallbackPolicy:
  case None
  case FallbackToAsyncSameJob

final case class EventReceptionExecutionPolicy(
  timing: EventExecutionTiming,
  jobRelation: EventJobRelation,
  sagaRelation: EventSagaRelation,
  transactionRelation: EventTransactionRelation,
  failurePolicy: EventFailurePolicy,
  fallbackPolicy: EventFallbackPolicy = EventFallbackPolicy.None
)
```

```scala
final case class EventReceptionRule(
  condition: EventReceptionCondition,
  policy: EventReceptionExecutionPolicy,
  priority: Int = 0
)
```

The exact type names may still change. The structural separation is the
important part.

## 7. Recommended Canonical Modes

As execution-policy shapes, the following modes are currently meaningful.

### 7.1 Same Subsystem Default

- sync
- separate task
- same job
- same saga
- same transaction
- failure propagates to the whole execution

This is the recommended default for same-subsystem reception.

#### Client Interface vs Runtime Execution

Phase 13 same-subsystem default must be read as:

- client-facing async job interface
- runtime async job scheduling
- synchronous inline continuation inside the job body

This is different from an async-looking interface that still executes
synchronously to completion in the foreground.

| Mode | Client Interface | Runtime Scheduling | Runtime Body | Intended Use |
| --- | --- | --- | --- | --- |
| `SyncJobAsyncInterface` | async job-style | synchronous | synchronous | testing / controlled verification |
| `Async Job + Sync Inline Continuation` | async job-style | asynchronous | synchronous inline continuation | canonical same-subsystem sync reception target |

The first mode remains useful as a framework/testing mode. The second is the
intended production semantics target for same-subsystem sync event reception.

For this target semantics, the framework direction is:

- one job
- one `ExecutionContext`
- one `RuntimeContext`
- one `UnitOfWork`
- one transaction
- no child job for same-subsystem sync continuation
- framework-owned event history is appended to the runtime event and persisted
  to the event store
- history uses compact delta records rather than full snapshots
- history overflow is deterministic failure rather than truncation

### 7.2 Sync with Async Fallback

- same baseline as same-subsystem default
- if the reception task fails, record the failed state and then hand off to an
  async same-job recovery path

This should be treated as a recovery/fallback policy rather than a primary
canonical mode.

### 7.3 Async A

- async
- separate task
- same job
- same saga
- same transaction
- failure policy is configurable (`Fail` or `Retry`)

This mode is conceptually valid, but implementation feasibility must be
verified because "async + same transaction" is not trivial.

### 7.4 Async B

- async
- separate task
- new job
- same saga
- new transaction
- failure policy is configurable (`Fail` or `Retry`)

This is the recommended default async mode.

### 7.5 Async C

- async
- separate task
- new job
- new saga
- new transaction
- failure policy is configurable (`Fail` or `Retry`)

This mode is appropriate when the event-triggered path should be fully
independent from the originating business flow.

## 8. Cross-Subsystem Mapping

Cross-subsystem reception should not introduce a separate unrelated model.

The current recommendation is:

- external A = async B
- external B = async C

Meaning:

- external subsystem / same saga -> async new-job same-saga
- external subsystem / new saga -> async new-job new-saga

Cross-subsystem reception should not attempt `SameTransaction`.

Cross-subsystem `SameJob` is also not a normal target model.

## 9. Policy Selection Rule

The runtime should resolve the effective reception policy by rule specificity.

Recommended first-pass order:

1. `originBoundary + eventName + eventKind + selectors`
2. `originBoundary + eventName + eventKind`
3. `originBoundary + eventName`
4. `originBoundary`
5. global default

This keeps selection deterministic and explainable.

## 10. Event Attributes Needed for Selection

To make boundary-aware selection deterministic, standard event attributes must
preserve enough source context across subsystem propagation.

At minimum:

- source subsystem
- source component
- event name
- event kind
- correlation id
- causation id
- job id
- task id
- saga id (future)

Current implementation already carries several context attributes. This note
extends the direction by requiring source-boundary reconstruction for policy
selection.

## 11. Phase 13 Scope

The Phase 13 implementation slice should focus on:

1. same-subsystem default sync policy
2. cross-subsystem-capable async same-saga policy
   (`Async + NewJob + SameSaga + NewTransaction`)
3. cross-subsystem-capable async new-saga policy
   (`Async + NewJob + NewSaga + NewTransaction`)
4. deterministic policy selection by:
   - origin boundary
   - event name
   - event kind
   - selector attributes
5. preservation of source-boundary and job/correlation attributes required for
   the above selection

This keeps Phase 13 implementable while preserving the intended policy model.

## 12. Deferred / Future Extension

The following are intentionally outside the main Phase 13 implementation slice
and should be treated as future extensions:

- async A (`Async + SameJob + SameTransaction`)
- sync-with-async-fallback
- ABAC-based condition matching
- richer event classification such as taxonomy-oriented rule matching
- source component / componentlet specific overrides
- final saga-id standard attribute design

These items remain valid design directions, but they should not block the
baseline Phase 13 policy model.

## 13. Relation to Existing Notes

### 13.1 `event-reception-latest-processing-spec.md`

That note remains the current implementation summary:

- current `ReceptionInput` flow
- current listener model
- current entity subscription behavior

This note adds the next-step policy-selection model on top of it.

### 13.2 `event-driven-job-management-decisions.md`

That note remains valid for the higher-level job-management philosophy:

- single-node baseline
- replayability
- idempotency
- non-goal of distributed orchestration

This note refines how event reception should choose job/saga/transaction
relations inside that baseline.

## 14. Open Questions

- Should failure policy default to `Fail` or `Retry` for external-subsystem reception?
- Where should saga identity live in the canonical event attributes?
- Should policy selection live on `CmlSubscriptionDefinition`, a separate
  reception rule object, or a subsystem-level registry?
- How should selector priority and override conflicts be represented in CML?

Deferred-future questions:

- Can async A be realized without breaking transaction semantics?

## 15. Suggested Next Design Step

Promote this note into design after the following are fixed:

1. canonical policy object shape
2. same-subsystem vs external-subsystem defaults
3. standard source-boundary event attributes
4. exact deferral boundary for async A and fallback semantics
