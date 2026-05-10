# Phase 24 — Metrics and Observability

status = active

## 1. Purpose of This Document

This work document opens Phase 24 and selects
`9.4 Metrics and Observability` as the active development item.

Phase 24 starts with a docs/status-only opening slice and then proceeds toward
diagnostic payload externalization, compact diagnostic summary/reference models,
structured dashboard drill-down, metrics expansion, and OpenTelemetry boundary
design.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Define the diagnostic payload policy for CallTree, Task calltree, execution
  history, and Job diagnostics.
- Ensure diagnostic records store compact summaries/references by default.
- Prevent full result/response payloads from being embedded unbounded in
  diagnostics.
- Define when small values may be stored inline and when larger values require
  summary metadata plus optional external payload references.
- Define the diagnostic external-store/runtime-config boundary, including
  retention, redaction, authorization, cleanup, and file/object-store
  destination policy.
- Add structured diagnostic dashboard drill-down for source-error chains,
  taxonomy/cause/disposition grouping, and payload references.
- Expand CNCF metrics collection and metrics service surfaces.
- Define the OpenTelemetry export boundary and policy without forcing all
  internal diagnostics to become OpenTelemetry data.
- Use `cncf-samples` sample 13 as a concrete observability integration driver,
  including docker-compose wiring to the actual observability backend.

Scope boundaries:

- Web/API error presentation polish remains under `9.1 Web Next Stage
  Follow-ups`.
- Error Model hardening remains under `9.7 Error Model Follow-ups`.
- Phase 24 owns observability storage, projection, metrics, and telemetry.

Payload policy:

- CallTree, Task calltree, execution history, and Job diagnostics must store
  compact summaries/references by default.
- Full result/response payloads must not be embedded unbounded in diagnostic
  records.
- Small values may be stored/displayed inline.
- Larger values require summary metadata such as byte size, record count, value
  kind, truncation status, and optional external payload references.
- Redaction, retention, authorization, cleanup, and destination policy are
  mandatory before production use.

## 3. Non-Goals

- No runtime/model changes in the opening slice.
- No production external-payload store without explicit redaction, retention,
  authorization, cleanup, and destination policy.
- No broad Web/API error model redesign; that remains in Web/Error follow-ups.
- No distributed tracing mandate before the OpenTelemetry boundary is designed.
- No replacement of existing CallTree/debug display work; Phase 24 defines the
  storage/projection policy beneath it.

## 4. Active Work Stack

- A (ACTIVE): OB-01 — Diagnostic Payload Externalization Policy Opening.
- B (TODO): OB-02 — CallTree / Execution History / Job Diagnostic Summary Model.
- C (TODO): OB-03 — Diagnostic Payload External Store and Runtime Config.
- D (TODO): OB-04 — Structured Diagnostic Dashboard Drill-down.
- E (TODO): OB-05 — Metrics Collection and Metrics Service Expansion.
- F (TODO): OB-06 — OpenTelemetry Boundary and Export Policy.
- G (TODO): OB-07 — Phase 24 verification and closure.

Resume hint:

- Continue with OB-01. Fix the normative diagnostic payload policy first, then
  proceed to the summary/reference model in OB-02.
- Keep `cncf-samples` sample 13 and docker-compose observability wiring visible
  as the concrete integration driver.

## 5. Development Items

- [ ] OB-01: Diagnostic Payload Externalization Policy Opening.
- [ ] OB-02: CallTree / Execution History / Job Diagnostic Summary Model.
- [ ] OB-03: Diagnostic Payload External Store and Runtime Config.
- [ ] OB-04: Structured Diagnostic Dashboard Drill-down.
- [ ] OB-05: Metrics Collection and Metrics Service Expansion.
- [ ] OB-06: OpenTelemetry Boundary and Export Policy.
- [ ] OB-07: Phase 24 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-24-checklist.md`.

## 6. Completion Conditions

Phase 24 can close when:

- Diagnostic payload externalization policy is documented and reflected in
  implementation-facing contracts.
- CallTree, Task calltree, execution history, and Job diagnostics use compact
  summary/reference records by default.
- Large result/response payloads have a configured externalization path or an
  explicit truncation/summary policy.
- Structured diagnostics can be drilled down from dashboard/admin surfaces,
  including previous/source-error chains and payload references.
- Metrics collection and metrics service surfaces cover the selected Phase 24
  runtime signals.
- OpenTelemetry export policy is documented and does not leak internal,
  unredacted, or unbounded diagnostics.
- `cncf-samples` sample 13 demonstrates the observability connection with
  docker-compose.
- Deferred observability work is explicitly recorded as future scope.
