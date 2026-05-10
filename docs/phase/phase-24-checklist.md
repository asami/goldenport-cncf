# Phase 24 — Metrics and Observability Checklist

This document contains detailed task tracking and decisions for Phase 24.
It complements the summary-level phase document (`phase-24.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-24.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## OB-01: Diagnostic Payload Externalization Policy Opening

Status: ACTIVE

### Objective

Open Phase 24 and fix the policy boundary for diagnostic payload storage before
runtime/model changes begin.

### Detailed Tasks

- [ ] Confirm Phase 24 is selected in strategy and Phase 23 remains the latest
      closed phase.
- [ ] Record the diagnostic payload policy:
  - CallTree, Task calltree, execution history, and Job diagnostics store
    compact summaries/references by default.
  - Full result/response payloads are not embedded unbounded in diagnostic
    records.
  - Small values may be inline.
  - Larger values require summary metadata and optional external payload
    references.
- [ ] Record required production policies for diagnostic payloads:
  - redaction;
  - retention;
  - authorization;
  - cleanup;
  - file/object-store destination configuration.
- [ ] Keep OB-01 docs/status only.
- [ ] Keep Web/API error polish under 9.1 and Error Model hardening under 9.7.
- [ ] Record `cncf-samples` sample 13 plus docker-compose observability wiring
      as a concrete Phase 24 driver.

### Expected Output

- Phase 24 is active as the Metrics and Observability phase.
- The payload externalization policy is explicit enough for OB-02/OB-03 to
  implement without reopening the storage boundary.
- The sample-13/docker-compose integration target is visible from the start.

### Guardrails

- Do not change runtime/model code in OB-01.
- Do not store large diagnostic payloads inline merely because the current
  debug UI can display them.
- Do not use OpenTelemetry as the first design step; define CNCF diagnostic
  storage/projection policy first.
- Do not make Domain/App/Web error model changes under Phase 24 unless they are
  observability projections of already structured data.

---

## OB-02: CallTree / Execution History / Job Diagnostic Summary Model

Status: TODO

### Objective

Define the compact summary/reference model for CallTree, Task calltree,
execution history, and Job diagnostic records.

### Detailed Tasks

- [ ] Define summary fields for action/UoW/space/I/O result and response values.
- [ ] Define inline thresholds and truncation metadata.
- [ ] Define payload-reference shape for future external files/object-store
      objects.
- [ ] Align Job diagnostics and task-local calltree with the same summary model.
- [ ] Preserve redaction before summary/externalization.

### Guardrails

- Do not embed full unbounded result/response payloads in Job or execution
  records.
- Do not make CallTree display code the authoritative storage format.

---

## OB-03: Diagnostic Payload External Store and Runtime Config

Status: TODO

### Objective

Implement the external diagnostic payload destination boundary and runtime
configuration.

### Detailed Tasks

- [ ] Define local-file and object-store destination configuration.
- [ ] Define retention and cleanup hooks.
- [ ] Define authorization checks for payload reference access.
- [ ] Define diagnostics when externalization is disabled, unavailable, or
      fails.
- [ ] Make payload externalization opt-in/configured until production policy is
      complete.

### Guardrails

- Do not write sensitive payloads before redaction policy is applied.
- Do not expose filesystem paths or object keys without authorization policy.

---

## OB-04: Structured Diagnostic Dashboard Drill-down

Status: TODO

### Objective

Add dashboard/admin drill-down for structured diagnostics and payload
references.

### Detailed Tasks

- [ ] Drill down from structured taxonomy/cause/disposition/status fields.
- [ ] Surface `Conclusion.previous` chains and source-error traces.
- [ ] Show compact payload summaries and external payload links when available.
- [ ] Keep large payloads collapsed or linked, not fully expanded by default.

### Guardrails

- Do not parse messages as semantic grouping keys.
- Do not make dashboard display shape the persistence contract.

---

## OB-05: Metrics Collection and Metrics Service Expansion

Status: TODO

### Objective

Expand metrics collection and service/query surfaces around runtime,
diagnostic, and payload-externalization signals.

### Detailed Tasks

- [ ] Inventory current metrics produced by action, UoW, entity/data/view/space,
      Job, Event, Web, and Blob paths.
- [ ] Add missing counters/timers for selected Phase 24 runtime surfaces.
- [ ] Add metrics service query surfaces for dashboard/admin use.
- [ ] Align metrics labels with structured diagnostic fields where applicable.

### Guardrails

- Do not treat metrics labels as error semantics.
- Keep high-cardinality values out of default metrics labels.

---

## OB-06: OpenTelemetry Boundary and Export Policy

Status: TODO

### Objective

Define how CNCF diagnostics and metrics can be exported to OpenTelemetry
without making OpenTelemetry the internal source of truth.

### Detailed Tasks

- [ ] Define which CNCF signals map to traces, spans, metrics, and logs.
- [ ] Define redaction and attribute-size limits before export.
- [ ] Define correlation between CNCF Job/Task/CallTree identifiers and
      OpenTelemetry trace/span identifiers.
- [ ] Define provider/configuration boundary for exporter implementations.
- [ ] Validate the policy through `cncf-samples` sample 13 docker-compose
      observability wiring.

### Guardrails

- Do not leak unredacted request/result payloads to telemetry exports.
- Do not require OpenTelemetry to use CNCF diagnostics locally.

---

## OB-07: Phase 24 Verification and Closure

Status: TODO

### Objective

Verify Phase 24 work and close or explicitly defer remaining Metrics and
Observability scope.

### Detailed Tasks

- [ ] Confirm Phase 24 docs and strategy status match implemented behavior.
- [ ] Confirm OB-01 through OB-06 are DONE or explicitly deferred.
- [ ] Run validations required by touched implementation slices.
- [ ] Record closure notes and future observability candidates.

### Guardrails

- Do not close Phase 24 while active Metrics and Observability work remains
  implicit.
