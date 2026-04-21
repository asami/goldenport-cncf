# Phase 13 Admin Diagnostics Entry Result

Date: 2026-04-22
Target: `/Users/asami/src/dev2025/cloud-native-component-framework`
Context:
- [Phase 13 Event Reception Baseline Handoff](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-event-reception-baseline-handoff-2026-04-21.md)
- [Phase 13 CNCF Extension Implementation Result](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-cncf-extension-implementation-result-2026-04-21.md)
- [Phase 13 Async Disposition and Rule Precedence Result](/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/phase-13-async-disposition-precedence-result-2026-04-22.md)

## 1. Summary

This slice added an operator-facing admin entry surface for Phase 13 event/job
runtime diagnostics.

The design choice was intentionally narrow:

- `admin` now provides a canonical discovery/summary entry point
- `event` and `job_control` remain the authoritative detailed inspection
  surfaces
- no duplicate admin-side event/job detail browser was introduced

This keeps the runtime contract visible from `admin` without creating a second,
competing projection model.

## 2. Implemented Scope

### 2.1 New Admin Diagnostics Entry

Added a new admin operation:

- `admin.execution.diagnostics`

The operation returns a summary record describing:

- the canonical event diagnostics selectors
- the canonical job diagnostics selectors
- the current Phase 13 event-side field contract
- the current Phase 13 job-side field contract
- short guidance on when to use event inspection versus job inspection

Primary implementation:

- [AdminComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala)

### 2.2 Canonical Route Guidance

The diagnostics surface now points operators to these authoritative selectors:

- `event.event.search_event`
- `event.event.load_event`
- `event.event_admin.load_job_events`
- `job_control.job.get_job_status`
- `job_control.job.load_job_history`
- `job_control.job.get_job_result`
- `job_control.job.await_job_result`
- `job_control.job_admin.load_job_events`

This makes the event/job visibility model discoverable from the admin entry
surface without duplicating the event or job projections themselves.

### 2.3 Stable Field Contract Exposure

The diagnostics surface explicitly publishes the Phase 13 field contracts.

Event-side fields exposed by the summary:

- `reception-rule`
- `reception-policy`
- `policy-source`
- `failure-policy`
- `failure-disposition-base`
- `dispatch-kind`
- `dispatch-status`
- `source-subsystem`
- `source-component`
- `target-subsystem`
- `target-component`

Job-side fields exposed by the summary:

- `reception-rule`
- `reception-policy`
- `policy-source`
- `job-relation`
- `saga-relation`
- `failure-policy`
- `failure-disposition`
- `source-subsystem`
- `source-component`
- `target-subsystem`
- `target-component`

Decision kept by this slice:

- `admin` does not rename or normalize these fields again
- admin summary stays field-compatible with the authoritative builtin event/job
  surfaces

### 2.4 Help and Introspection Discoverability

Because `execution.diagnostics` is registered as a normal admin operation, it is
now discoverable through the existing protocol/meta/help path without inventing
another navigation model.

This was the intended scope for this slice:

- add discoverability by normal operation registration
- avoid creating a second manual/help system specific to diagnostics

## 3. Files Changed

Runtime:

- [AdminComponent.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala)

Executable specification:

- [AdminExecutionDiagnosticsSpec.scala](/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/AdminExecutionDiagnosticsSpec.scala)

## 4. Verification

Verified:

- `sbt --no-server --batch "testOnly org.goldenport.cncf.component.AdminExecutionDiagnosticsSpec org.goldenport.cncf.component.builtin.event.EventComponentSpec org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponentSpec"`
- `sbt --no-server --batch compile`

Result:

- targeted admin/event/job diagnostics specs passed
- compile passed

## 5. Boundaries Preserved

This slice did not implement:

- full admin-side event search/read duplication
- full admin-side job search/read duplication
- ABAC-aware policy selection
- dead-letter or retry orchestration
- generator-native componentlet changes

The purpose here was visibility and navigation only.

## 6. Next Natural Work

The next natural steps after this slice are:

1. update Phase 13 bookkeeping documents if they still lag behind actual
   runtime progress
2. continue with generator-native componentlet work on top of the now-stable
   runtime participant and diagnostics contract
3. optionally expand additional admin/operator surfaces only if the current
   entry-summary approach proves insufficient in real use

The current result is intentionally conservative: `admin` now gives operators a
clear entry point, but the detailed truth still lives in the dedicated `event`
and `job_control` builtins.
