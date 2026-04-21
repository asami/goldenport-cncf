# Phase 13 Async Disposition and Rule Precedence Result

Date: 2026-04-22
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`
Context:
- [Phase 13 Event Reception Baseline Handoff](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-event-reception-baseline-handoff-2026-04-21.md)
- [Phase 13 CNCF Extension Implementation Result](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-cncf-extension-implementation-result-2026-04-21.md)
- [Phase 13 Sync-Inline Hardening Result](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-sync-inline-hardening-result-2026-04-21.md)

## 1. Summary

This slice completed the Phase 13 runtime contract around async event
continuation without reopening the sync-inline baseline.

The implemented result is:

- async failure disposition is now classified as `NotApplicable`,
  `Retryable`, or `Terminal`
- builtin job and event inspection now expose async disposition-related
  metadata explicitly
- rule selection precedence is now fixed in runtime behavior and executable
  specifications as:
  - explicit rule
  - compatibility mapping
  - subsystem default
- generator-native componentlet support now has a more stable runtime contract
  because source/target identity and policy provenance are explicit and
  preserved in async as well as sync paths

This slice did not change:

- sync-inline semantics
- child job submission timing for async/new-job continuation
- event immutability after commit
- dead-letter or retry orchestration

## 2. Implemented Scope

### 2.1 Async Failure Disposition

Implemented a typed runtime classification in the job read model:

- `NotApplicable`
- `Retryable`
- `Terminal`

Current mapping:

- sync-inline paths project `NotApplicable`
- async child job still running or completed successfully projects
  `NotApplicable`
- async child job failed with `failure.policy = retry` projects `Retryable`
- async child job failed with `failure.policy = fail` projects `Terminal`

Primary implementation:

- [JobEngine.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala)

Notes:

- this slice classifies failure outcome; it does not add retry execution
  orchestration
- dead-letter handling remains deferred

### 2.2 Event-Side Async Dispatch Metadata

Added explicit event-side async dispatch metadata so builtin event inspection can
explain the queued async path without pretending to know post-commit child job
outcomes.

Added metadata:

- `cncf.event.failurePolicy`
- `cncf.event.failureDispositionBase`
- `cncf.event.dispatchKind`
- `cncf.event.dispatchStatus`

Current interpretation:

- source event records describe the queued async dispatch contract
- final child job outcome remains visible from the child job inspection surface,
  not by mutating the already committed source event record

Primary implementation:

- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)

### 2.3 Builtin Inspection Visibility

Extended builtin projections so operators can answer:

- which rule was selected
- why it was selected
- whether the async dispatch base is retryable or terminal
- whether dispatch stayed inline or queued a new job
- which runtime source and target component/componentlet participated

Runtime surfaces updated:

- [EventComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/event/EventComponent.scala)
- [JobControlComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)

Result:

- event inspection now shows:
  - source and target metadata
  - rule / policy / policy source
  - failure policy
  - failure disposition base
  - dispatch kind
  - dispatch status
  - event history when present
- job inspection now shows:
  - failure policy
  - final failure disposition
  - existing lineage metadata

### 2.4 Rule Precedence Freeze

The runtime precedence is now treated as fixed:

1. explicit reception rule match
2. compatibility mapping
3. subsystem default

This was already largely true in the runtime structure, but this slice makes the
contract explicit in implementation and executable specifications.

Additional clarification captured by this slice:

- future ABAC-aware selection belongs inside explicit-rule conditions
- ABAC does not become a separate override layer that outranks explicit rules

Current provenance values remain projection-compatible:

- `explicit-rule`
- `compatibility-mapping`
- `subsystem-default`

Runtime implementation:

- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)

### 2.5 Generator-Native Componentlet Runtime Contract

This slice did not redesign componentlets, but it fixed the runtime contract
needed by generator-native componentlet work.

Protected contract:

- real runtime componentlets are first-class event participants
- metadata-only componentlets are never runtime participants
- source and target identities in event/job metadata use resolved runtime
  component/componentlet names
- no alias fallback should replace a real runtime componentlet name in runtime
  metadata
- rule selection and dispatch semantics are determined on the target runtime
  reception side, not on descriptor-only alias abstraction

Executable protection remains centered on:

- [ComponentFactoryEventReceptionBootstrapSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala)
- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)

## 3. Files Changed

Runtime:

- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)
- [JobEngine.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/job/JobEngine.scala)
- [EventComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/event/EventComponent.scala)
- [JobControlComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)

Executable specifications:

- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)
- [JobQueryReadModelSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala)
- [EventComponentSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/builtin/event/EventComponentSpec.scala)
- [JobControlComponentSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponentSpec.scala)

## 4. Executable Protection Added

The updated specs now protect:

- retry-policy child job failure -> `Retryable`
- fail-policy child job failure -> `Terminal`
- event inspection visibility of:
  - dispatch kind
  - dispatch status
  - failure policy
  - failure disposition base
- job inspection visibility of final failure disposition
- explicit rule precedence over compatibility mapping
- continued exclusion of metadata-only componentlets from runtime participation
- continued preservation of real runtime componentlet identity

## 5. Verification

Verified:

- `sbt --no-server --batch compile`
- `sbt --no-server --batch "testOnly org.goldenport.cncf.event.EventReceptionSpec org.goldenport.cncf.job.JobQueryReadModelSpec org.goldenport.cncf.component.builtin.event.EventComponentSpec org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponentSpec org.goldenport.cncf.component.ComponentFactoryEventReceptionBootstrapSpec"`

Result:

- compile passed
- all targeted specs passed

## 6. Deferred

Still deferred after this slice:

- dead-letter / poison-event handling
- automatic retry orchestration
- final ABAC matching implementation
- final saga-id standardization
- post-commit backpatching of already stored event records

This slice deliberately stopped at classification, visibility, and precedence.

## 7. Next Natural Work

The next natural framework work is:

1. reflect the new async disposition contract in additional builtin admin
   surfaces if needed
2. formalize the rule-condition model further when ABAC matching becomes an
   active implementation topic
3. proceed to generator-native componentlet work on top of the now-fixed runtime
   participant contract

The main outcome is that Phase 13 async continuation now has a clearer and more
stable operator-visible contract without weakening the already accepted
sync-inline baseline.
