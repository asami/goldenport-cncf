# Phase 13 Handoff for textus-sample-app-event

Target application:
- `/Users/asami/src/dev2026/textus-sample-app-event`

Framework source:
- `/Users/asami/src/dev2025/cloud-native-component-framework`

Relevant framework commits:
- `53dc8b8 Implement Phase 13 event reception baseline`
- `2ff51c7 Implement Phase 13 CNCF extension backlog slice`
- `65f94d0 Clarify Phase 13 sync event reception semantics`

This handoff records the current CNCF-side state for the next `textus-sample-app-event`
work. The goal is to remove sample-side correction logic and rely on the framework
for same-subsystem sync event reception.

## 1. Current framework state

The CNCF side now provides the following behavior for same-subsystem sync reception:

- client-facing command contract may remain job-oriented
- same-subsystem reception uses a sync-inline continuation path inside the current execution
- no child job is created for same-subsystem sync continuation
- the follow-up action runs under the current runtime and current `UnitOfWork`
- persistent event commit is deferred until after sync follow-up completion
- the final committed event record includes framework-owned source/target/policy metadata
- framework-owned event history is appended to the runtime event and persisted to the event store

Implemented framework files:
- [EventReception.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/event/EventReception.scala)
- [OperationRequestActionDispatcher.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/OperationRequestActionDispatcher.scala)
- [ComponentLogic.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala)

Executable protection:
- [EventReceptionSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala)

Verified on framework side:
- same-subsystem sync reception keeps one job / one runtime / one `UnitOfWork`
- no child job is created for the sync-inline follow-up
- target failure prevents commit
- persistent same-subsystem sync reception stores framework-owned source/reception/dispatch history
- history overflow fails before commit

## 2. Semantics now expected by the sample

The intended production semantics for the sample are:

- client interface: async job-oriented command interface
- runtime scheduling: async at the client/job boundary
- once the job body starts:
  - source action runs
  - source event is emitted
  - same-subsystem subscription is resolved
  - target componentlet follow-up action runs synchronously inline
  - one final commit occurs
- there is no child job for the same-subsystem sync continuation

For the sync path, the framework is now expected to own:

- source/target/policy metadata population
- target runtime component/componentlet identity
- event history accumulation
- event store persistence timing
- rollback on follow-up failure

## 3. Sample-side correction logic that should now be removed

The following sample-side correction logic should be treated as obsolete and should be removed
as the next step:

- source action local `commit()` used to force visibility
- source action direct accepted-body update used as a workaround for follow-up visibility
- direct source-to-target `EventReception` dispatch from the application side
- manual source/target/policy metadata injection in the application

If any of these are still required after adapting the sample to the current framework,
that indicates a remaining framework gap and should be reported back explicitly.

## 4. What the sample should do next

The next sample-side implementation should be limited to normal application intent:

1. `public-notice` receives the source command
2. `public-notice` emits the event through the normal framework path
3. `notice-admin` receives the event through normal subscription bootstrap
4. `notice-admin` follow-up action writes the DB effect
5. a read path confirms the persisted effect

The sample should not compensate for missing sync semantics inside the app layer.

## 5. What to verify in textus-sample-app-event

After removing the correction logic, verify the following acceptance points:

1. The source command still returns the intended job-oriented interface.
2. The job body executes the same-subsystem event continuation inline.
3. No child job is materialized for the `public-notice -> notice-admin` sync path.
4. The target follow-up action sees source-side state through the shared runtime/`UnitOfWork` path.
5. The final event store record contains framework-owned metadata, including:
   - source subsystem/component
   - target subsystem/componentlet
   - selected policy
   - policy source
   - append-only event history
6. The target component identity recorded in event metadata is the real runtime component/componentlet name, not a root alias substitute.
7. If the target follow-up action fails, neither source-side nor target-side persistent effect is committed.

## 6. Event history contract now available from the framework

The framework now records event history on the event itself and persists it into the final
event store record.

Current attributes:
- `cncf.event.history`
- `cncf.event.historyCount`
- `cncf.event.historyFormat = delta-trail`
- `cncf.event.historyOverflow = fail-fast`

Current stages appended by the framework:
- `source`
- `reception`
- `dispatch`

History is append-only.
The framework does not overwrite earlier source metadata with later target metadata.

## 7. Known boundaries

This slice is only about same-subsystem sync reception.

Not the subject of this handoff:
- async new-job refinement
- ABAC-aware reception policy selection
- finalized saga-id standardization
- richer inspection rendering for event history

## 8. Expected outcome of the next sample-side slice

The desired result is:

- `textus-sample-app-event` runs the `public-notice -> notice-admin` sync flow without app-side correction logic
- persisted event metadata is framework-owned
- the sample becomes a true acceptance driver for the Phase 13 sync-inline semantics

If that result cannot be achieved with the current framework state, the remaining failure should
be described as a concrete framework mismatch, not compensated inside the sample.
