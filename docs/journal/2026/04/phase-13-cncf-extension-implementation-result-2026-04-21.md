# Phase 13 CNCF Extension Implementation Result

Date: 2026-04-21
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`
Context:
- [Phase 13 CNCF Extension Backlog](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-cncf-extension-backlog-2026-04-21.md)
- [Phase 13 Event Reception Baseline Handoff](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-event-reception-baseline-handoff-2026-04-21.md)

## 1. Summary

This work implemented the first extension slice on top of the Phase 13 event
reception baseline.

The result is that event-triggered follow-up work is now visible from the job
read model and builtin inspection surfaces, and componentlets are protected as
runtime event participants in executable specs.

This slice intentionally stopped before broader async failure semantics and
before the real sample application flow.

## 2. Implemented Scope

### 2.1 M1 EventReception and JobEngine Integration

Implemented:

- structured event-triggered lineage in `JobQueryReadModel`
- explicit projection of:
  - event name
  - event kind
  - parent job id
  - correlation id
  - causation id
  - source subsystem/component
  - target subsystem/component
  - reception rule
  - reception policy
  - policy source
  - job relation
  - saga relation
  - failure policy

Primary implementation:

- [JobEngine.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala)

Notes:

- parent/child job relation is now observable from the child job read model
- lineage is reconstructed from explicit event-reception submit metadata instead
  of relying on hidden runtime assumptions

### 2.2 M2 Inspection and Diagnostic Surfaces

Implemented:

- builtin job inspection now exposes event-triggered lineage fields
- builtin job inspection now exposes debug parameters and execution notes
- builtin event inspection now exposes:
  - source subsystem/component
  - target subsystem/component
  - origin boundary
  - reception rule
  - reception policy
  - policy source
  - saga relation

Primary implementation:

- [JobControlComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)
- [EventComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/event/EventComponent.scala)

Result:

- operators can now answer which event policy was selected and which source and
  target runtime components participated, without reading framework code

### 2.3 M3 Component and Componentlet Participation

Implemented:

- explicit target metadata on dispatched event attributes:
  - `cncf.target.subsystem`
  - `cncf.target.component`
- explicit policy-source metadata on dispatched event attributes:
  - `cncf.event.policySource`
- executable protection that a real runtime componentlet keeps its runtime name
  in event dispatch
- executable protection that metadata-only componentlet entries are not treated
  as runtime event participants

Primary implementation:

- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)
- [ComponentFactoryEventReceptionBootstrapSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala)

Result:

- componentlet participation is now protected at the event-dispatch semantics
  level, not only at resolver/UI level

### 2.4 M4 End-to-End Integration Protection

Implemented executable protection for:

- compatibility-mapped async continuation spawning a child job
- structured child-job lineage visibility
- event inspection visibility of selected rule/policy/source/target metadata
- job inspection visibility of event-triggered lineage
- cross-component dispatch to a runtime componentlet
- negative metadata-only componentlet path

Primary specs:

- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)
- [JobQueryReadModelSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala)
- [EventComponentSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/builtin/event/EventComponentSpec.scala)
- [JobControlComponentSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponentSpec.scala)
- [ComponentFactoryEventReceptionBootstrapSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala)

## 3. Partially Implemented SHOULD Scope

### 3.1 S1 Compatibility Ambiguity Reduction

Implemented:

- explicit runtime-visible `policySource`
- current values:
  - `explicit-rule`
  - `compatibility-mapping`
  - `subsystem-default`

This makes precedence visible from event attributes and job lineage.

Not yet implemented:

- separate warning surface for compatibility mode use
- additional narrowing of legacy compatibility behavior

## 4. Deferred in This Slice

### 4.1 S2 Lineage-Oriented Test Helpers

Deferred.

Reason:

- current specs were still manageable with existing local helpers
- dedicated helper extraction would have added churn without changing runtime
  semantics

### 4.2 S3 Async Failure Semantics

Deferred.

Reason:

- retry versus terminal failure and dead-letter handling still need a broader
  design decision
- this slice kept the Phase 13 baseline intact and did not reopen that contract

### 4.3 LATER Items

Not touched:

- ABAC-aware reception policy selection
- final `sagaId` standardization
- component/componentlet-specific policy overrides
- broader metrics and telemetry export

## 5. Files Changed

Runtime:

- [JobEngine.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala)
- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)
- [JobControlComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)
- [EventComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/event/EventComponent.scala)

Executable specifications:

- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)
- [JobQueryReadModelSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala)
- [ComponentFactoryEventReceptionBootstrapSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala)
- [EventComponentSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/builtin/event/EventComponentSpec.scala)
- [JobControlComponentSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponentSpec.scala)

## 6. Verification

Verified:

- `sbt --no-server --batch compile`
- `sbt --no-server --batch "testOnly org.goldenport.cncf.event.EventReceptionSpec org.goldenport.cncf.job.JobQueryReadModelSpec org.goldenport.cncf.component.ComponentFactoryEventReceptionBootstrapSpec org.goldenport.cncf.component.builtin.event.EventComponentSpec org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponentSpec"`

Result:

- compile passed
- all targeted specs passed

## 7. Remaining Work

The next natural slice is:

1. add clearer operator-facing rendering for lineage fields in builtin admin
   surfaces if needed
2. move to the real application driver in `textus-sample-app-event`
3. decide whether async failure disposition should become a Phase 13 follow-up
   item or remain deferred beyond Phase 13

The key point is that the framework baseline is now strong enough to support
the real sample without relying on hidden event/job behavior.

## 8. Open Semantic Refinement

The first extension slice completed:

- inspection visibility
- lineage visibility
- componentlet participation protection

Same-subsystem sync semantics still need an explicit semantic refinement around:

- async interface vs runtime execution distinction
- one job / one `ExecutionContext` / one `RuntimeContext`
- single `UnitOfWork` / single transaction
- synchronous inline continuation without child job
- framework-owned event history appended to the runtime event and persisted to
  the event store
- compact delta history rather than full snapshots
- deterministic failure on history-cap overflow rather than truncation

This refinement should be documented and implemented as a framework concern in
CNCF, not left to sample-side correction logic.
