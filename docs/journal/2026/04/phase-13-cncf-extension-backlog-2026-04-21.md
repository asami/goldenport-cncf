# Phase 13 CNCF Extension Backlog

Date: 2026-04-21
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`
Context:
- [Phase 13 — Event Mechanism Extension](/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-13.md)
- [Phase 13 — Event Mechanism Extension Checklist](/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-13-checklist.md)
- [Phase 13 Event Reception Baseline Handoff](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-event-reception-baseline-handoff-2026-04-21.md)

## 1. Purpose

This backlog records the CNCF-side feature expansion needed to carry the Phase 13
baseline into a usable event-driven implementation path.

The baseline already established:

- subsystem-owned shared `EventBus` / `EventReception`
- rule-based reception policy selection
- baseline sync and async reception policies
- compatibility with `SameJob` / `NewJob`
- internal await support for event-oriented specs

The next step is to turn that baseline into a stable framework contract for
real application work, especially around event and job integration.

## 2. Backlog Structure

Priority bands:

- MUST: required to develop the real Phase 13 scenario without ad hoc behavior
- SHOULD: strongly recommended to reduce churn and improve operability
- LATER: useful extension after the first real scenario is working

## 3. MUST

### M1. Formalize EventReception and JobEngine Integration

Problem:
- The baseline defines sync same-job and async new-job behavior, but the job-side
  contract is still incomplete.

Required work:
- Define the canonical relation between event-triggered dispatch and spawned job.
- Define parent job, child job, causation, and correlation propagation rules.
- Define same-saga versus new-saga runtime behavior in terms of concrete runtime
  metadata.
- Define the minimum job metadata required for event-triggered work.
- Define how event-triggered job completion is surfaced to await/result paths.

Expected outcome:
- Event-driven follow-up work can be reasoned about from the job model without
  relying on implicit framework behavior.

Related phase items:
- EVX-04
- EVX-05
- EVX-07

### M2. Expose Selected Reception Rule and Policy in Inspection Surfaces

Problem:
- The runtime now selects reception rules and policies, but operators and
  developers cannot easily see which rule won and why.

Required work:
- Add selected `EventReceptionRule` visibility to builtin event/job inspection.
- Add selected `EventReceptionExecutionPolicy` visibility to builtin
  event/job inspection.
- Surface source subsystem/component and target component in inspectable form.
- Surface job relation and saga relation for event-triggered dispatch.
- Ensure traces and diagnostic output include the selected rule/policy.

Expected outcome:
- Event-driven failures and unexpected dispatch paths become debuggable without
  code-level tracing.

Related phase items:
- EVX-06
- EVX-07

### M3. Stabilize Component and Componentlet Participation in Event Paths

Problem:
- Componentlets now exist as real runtime components, but event/job semantics
  still need their formal status to be explicit and consistent.

Required work:
- Treat componentlets as first-class event source and target components.
- Ensure event source metadata records componentlet identity, not only root
  component identity.
- Ensure selector resolution, dispatch, and inspection use the accessed
  component/componentlet consistently.
- Define whether any event semantics differ between primary component and
  bundled componentlet. Default assumption should remain "no difference except
  deployability and primacy."

Expected outcome:
- Phase 13 samples can use component boundaries that are real in runtime,
  routing, and observability.

Related phase items:
- EVX-03
- EVX-04
- EVX-06

### M4. Add End-to-End Integration Specs for Real Event Collaboration

Problem:
- Current tests protect the baseline mechanics, but not the full application-like
  collaboration path.

Required work:
- Add same-subsystem cross-component event publish/receive spec.
- Add same-job continuation spec that verifies direct follow-up execution.
- Add new-job continuation spec that verifies spawned job behavior.
- Add spec that verifies event-triggered DB/state mutation becomes observable
  through a read path.
- Add negative spec for metadata-only componentlet presence without runtime
  instance.

Expected outcome:
- The framework has regression protection for the exact collaboration path that
  Phase 13 depends on.

Related phase items:
- EVX-07

## 4. SHOULD

### S1. Reduce Compatibility Ambiguity Between Old Continuation Mode and New Rules

Problem:
- `EventContinuationMode` compatibility still coexists with explicit reception
  rules, which can become a source of ambiguity during implementation.

Required work:
- Make precedence rules explicit in documentation and runtime diagnostics.
- Add warnings or debug diagnostics when compatibility mode is used.
- Narrow the compatibility mapping to a clearly documented subset.

Expected outcome:
- Developers can tell whether behavior came from explicit policy or fallback
  compatibility.

Related phase items:
- EVX-03
- EVX-04

### S2. Add Test Helpers for Event and Job Lineage Assertions

Problem:
- Current tests can validate outcomes, but lineage assertions remain too manual.

Required work:
- Add helpers to await event-triggered job completion.
- Add helpers to assert parent job, child job, correlation, and saga lineage.
- Add subsystem-scoped integration fixtures that create shared event facilities
  without repeated boilerplate.

Expected outcome:
- Event/job integration specs become shorter and less brittle.

Related phase items:
- EVX-05
- EVX-07

### S3. Strengthen Failure Semantics for Async Event Work

Problem:
- Async retry behavior exists as a baseline assumption, but dead-letter and
  poison-event handling are still undefined.

Required work:
- Define failure categories for event-triggered async work.
- Define retry versus terminal failure expectations.
- Define whether dead-letter is in scope for this phase or explicitly deferred.
- Expose failure disposition in inspection surfaces.

Expected outcome:
- Async event behavior is operationally understandable before broader use.

Related phase items:
- EVX-04
- EVX-06

## 5. LATER

### L1. ABAC-Aware Reception Policy Selection

Add authorization-aware policy selection once the baseline collaboration path is
stable.

### L2. Final Saga ID Standardization

Replace provisional same-saga/new-saga lineage handling with a finalized
`sagaId` contract when the surrounding design is ready.

### L3. Component-Specific or Componentlet-Specific Reception Overrides

Add finer-grained policy override capability only after the common path is
stable and the need is demonstrated by real samples.

### L4. Broader Metrics and Telemetry

Expose event/job policy selection and outcomes through metrics and broader
telemetry once the runtime contract settles.

## 6. Recommended Execution Order

1. M1. Formalize EventReception and JobEngine integration.
2. M2. Expose selected rule/policy in inspection surfaces.
3. M3. Stabilize component/componentlet participation in event paths.
4. M4. Add end-to-end integration specs for real event collaboration.
5. S1. Reduce compatibility ambiguity.
6. S2. Add lineage-oriented test helpers.
7. S3. Strengthen async failure semantics.

Reasoning:
- Job integration and selected-policy visibility are the biggest sources of
  accidental framework behavior if postponed.
- Componentlet formalization should happen before more sample logic is built on
  top of runtime assumptions.
- End-to-end specs should be written after the main runtime contract is clearer,
  not before.

## 7. Immediate Use for the Next Sample Step

If the next task is to implement the real Phase 13 sample flow, the minimum CNCF
work to do first is:

- M1. Formalize EventReception and JobEngine integration.
- M2. Expose selected rule/policy in inspection surfaces.

Those two items provide the minimum stable footing for:

- Component A receives input
- event is emitted
- Component B receives event
- state is updated
- read path confirms visible effect

without having to debug hidden framework decisions blindly.
