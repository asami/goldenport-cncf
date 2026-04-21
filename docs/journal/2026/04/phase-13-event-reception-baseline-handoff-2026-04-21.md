# Phase 13 Event Reception Baseline Handoff

Date: 2026-04-21
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`
Commit: `53dc8b8 Implement Phase 13 event reception baseline`

## 1. Reached State

The Phase 13 event reception baseline is now implemented on top of the
existing `EventBus` / `EventReception` / `JobEngine` foundation.

This work establishes `EventReception` as the canonical subsystem-internal
event collaboration path for the Phase 13 baseline.

Implemented baseline:

- same-subsystem default sync reception
- async new-job same-saga reception
- async new-job new-saga reception
- deterministic policy selection by:
  - subsystem boundary
  - event name
  - event kind
  - selectors
- internal-only await helper
- subsystem-owned shared event facilities
- executable specs for the above

Deferred and intentionally out of this slice:

- async same-job same-transaction
- sync-with-async-fallback
- ABAC-aware policy selection
- richer taxonomy-based event classification
- source component/componentlet-specific override
- final saga-id standardization

## 2. Main Runtime Changes

### 2.1 Separate Reception Rule Layer

`EventReception` now has a separate rule/policy layer instead of relying only
on `CmlSubscriptionDefinition.continuationMode`.

Added runtime model:

- `EventOriginBoundary`
- `EventReceptionCondition`
- `EventExecutionTiming`
- `EventJobRelation`
- `EventSagaRelation`
- `EventTransactionRelation`
- `EventFailurePolicy`
- `EventReceptionExecutionPolicy`
- `EventReceptionRule`

Added baseline policy presets:

- `EventReceptionExecutionPolicy.SameSubsystemDefault`
- `EventReceptionExecutionPolicy.AsyncNewJobSameSaga`
- `EventReceptionExecutionPolicy.AsyncNewJobNewSaga`

Primary implementation:

- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)

### 2.2 Subsystem-Owned Shared Facilities

`Subsystem` now owns:

- one shared `EventBus`
- one registry of bootstrapped `EventReception` instances

This replaces the previous factory-local shared-bus map approach for the
subsystem path.

Primary implementation:

- [Subsystem.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala)
- [ComponentFactory.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala)

### 2.3 Component Metadata Hook for Rules

`Component` now exposes:

- `eventReceptionRuleDefinitions: Vector[EventReceptionRule]`

`ComponentFactory.createEventReception(...)` registers:

- event definitions
- subscriptions
- reception rules

Primary implementation:

- [Component.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/Component.scala)
- [ComponentFactory.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala)

### 2.4 Event Source/Selection Attributes

Added or used as standard attributes:

- `cncf.source.subsystem`
- `cncf.source.component`
- `cncf.event.ingressBoundary`
- `cncf.event.originBoundary`
- `cncf.event.receptionRule`
- `cncf.event.receptionPolicy`
- `cncf.event.sagaRelation`

Boundary reconstruction rule:

- if `cncf.source.subsystem == currentSubsystem`, boundary is `SameSubsystem`
- otherwise boundary is `ExternalSubsystem`
- if source subsystem is missing:
  - use external only when explicit ingress boundary says so
  - otherwise fail deterministically

Current merge rule for attributes:

- input attributes win over context-derived attributes
- context fills only missing standard attributes

### 2.5 Continuation Compatibility

Existing `EventContinuationMode.SameJob/NewJob` is still accepted as a
compatibility input.

Compatibility mapping:

- `SameJob`
  - same-subsystem only -> `SameSubsystemDefault`
- `NewJob`
  - compatibility default -> `AsyncNewJobSameSaga`

If an explicit rule is present, rule-based selection wins over raw
continuation mode.

### 2.6 Emit Path Uses Authorized Reception

`ComponentLogic.EmitEventActionCall` now uses `receiveAuthorized(...)` instead
of `receive(...)`, so event emission from component action paths carries the
bound execution context.

Primary implementation:

- [ComponentLogic.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala)

### 2.7 Internal Await Helper

Added internal-only helper:

- [EventAwaitSupport.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventAwaitSupport.scala)

This is used to remove direct fixed-sleep waiting from event specs.

## 3. Rule Resolution Semantics

Current effective-policy resolution is:

1. match rule by:
   - origin boundary
   - event name
   - event kind
   - event category
   - selectors
2. choose by:
   - higher specificity first
   - then higher `priority`
   - then earlier declaration order

Guard rails:

- equal-priority + equal-condition duplicate rules fail at registration time
- runtime does not resolve equal ambiguity heuristically

## 4. Implemented Baseline Semantics

### 4.1 Same-Subsystem Default

- sync dispatch
- same job
- same saga
- same transaction (Phase 13 semantic baseline)
- failure propagates immediately

Clarification for the next semantic refinement:

- the baseline wording `sync dispatch` does not mean that every async-looking
  interface should execute synchronously in the foreground
- the next refinement explicitly distinguishes:
  - test-style async interface with synchronous execution
  - production-style async job with synchronous inline continuation
- the intended direction for same-subsystem sync reception is the latter:
  - client-facing async job interface
  - async job scheduling
  - same-subsystem event emit/reception/follow-up continuing synchronously
    inside one job / `UnitOfWork` / transaction
  - no child job

### 4.2 Async New-Job Same-Saga

- async dispatch
- new job
- same saga lineage
- new transaction
- default failure policy = `Retry`

### 4.3 Async New-Job New-Saga

- async dispatch
- new job
- new saga lineage
- new transaction
- default failure policy = `Retry`

## 5. Deliberate Simplifications

### 5.1 Saga Identity

There is still no final standardized `sagaId` event attribute.

For Phase 13:

- same-saga keeps existing correlation lineage
- new-saga switches correlation lineage via a new observability correlation
  context

This is explicitly provisional until saga-id standardization is finalized.

### 5.2 Failure Policy

Phase 13 async baseline uses retry-oriented job handling, but it does not
finalize dead-letter behavior.

Current meaning:

- failed async event-triggered work remains a failed/retryable job

### 5.3 Same-Transaction Async

`Async + SameJob + SameTransaction` is not implemented.

It remains a future extension candidate and should not be inferred from the
current runtime.

## 6. Updated Specifications

Updated executable specs:

- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)
- [ComponentFactoryEventReceptionBootstrapSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala)

Added coverage for:

- same-subsystem default sync dispatch
- external async new-job same-saga dispatch
- external async new-job new-saga rule selection
- rule specificity
- duplicate rule rejection
- source-boundary propagation
- subsystem-owned reception registration
- internal await helper use
- no-boundary deterministic failure
- compatibility path from existing continuation mode

## 7. Verification

CNCF:

- `sbt --no-server --batch "testOnly org.goldenport.cncf.event.EventReceptionSpec org.goldenport.cncf.component.ComponentFactoryEventReceptionBootstrapSpec"` passed
- `sbt --no-server --batch compile` passed
- `sbt --no-server --batch publishLocal` passed

Sample driver:

- `/Users/asami/src/dev2026/textus-sample-app-event`
- `sbt --no-server --batch compile` passed

## 8. Next Work Items

Recommended next steps:

1. Add selected-rule/policy visibility to builtin event/job inspection
   surfaces.
2. Implement the actual event-driven scenario in
   `/Users/asami/src/dev2026/textus-sample-app-event`:
   - Component A receives message
   - event is emitted
   - Component B receives event
   - DB is updated
   - read service confirms visible effect
3. Promote the stable subset of the rule/policy model into design once the
   current baseline is used in a real sample path.

## 9. Working Tree Note

There is an unrelated uncommitted change in:

- [build.sbt](/Users/asami/src/dev2025/cloud-native-component-framework/build.sbt)

It was intentionally excluded from commit `53dc8b8`.
