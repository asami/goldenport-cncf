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

- A (DONE): OB-01 — Diagnostic Payload Externalization Policy Opening.
- B (DONE): OB-02 — CallTree / Execution History / Job Diagnostic Summary Model.
- C (DONE): OB-03 — Diagnostic Payload External Store and Runtime Config.
- D (ACTIVE): OB-04 — Structured Diagnostic Dashboard Drill-down.
- E (TODO): OB-05 — Metrics Collection and Metrics Service Expansion.
- F (TODO): OB-06 — OpenTelemetry Boundary and Export Policy.
- G (TODO): OB-07 — Phase 24 verification and closure.

Resume hint:

- Continue with OB-04. Build dashboard/admin drill-down for structured
  diagnostic grouping, source-error chains, and payload references.
- Keep `cncf-samples` sample 13 and docker-compose observability wiring visible
  as the concrete integration driver.

## 5. Development Items

- [x] OB-01: Diagnostic Payload Externalization Policy Opening.
- [x] OB-02: CallTree / Execution History / Job Diagnostic Summary Model.
- [x] OB-03: Diagnostic Payload External Store and Runtime Config.
- [ ] OB-04: Structured Diagnostic Dashboard Drill-down.
- [ ] OB-05: Metrics Collection and Metrics Service Expansion.
- [ ] OB-06: OpenTelemetry Boundary and Export Policy.
- [ ] OB-07: Phase 24 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-24-checklist.md`.

OB-01 completion note:

- `docs/design/observability/diagnostic-payload-externalization-policy.md`
  is the normative Phase 24 policy entry point for diagnostic payload storage.
- Runtime summary/reference shape starts in OB-02.
- External store implementation and runtime configuration keys start in OB-03.

OB-02 completion note:

- CNCF now has a reusable `DiagnosticPayloadSummary` /
  `DiagnosticPayloadReference` model for CallTree, execution history, Job
  calltree/debug records, and task-local calltree projections.
- CallTree action/UoW/space/I/O payload fields use compact summary records for
  request, Web parameters, query, response, and result values.
- Retained execution history keeps a structured `resultSummaryRecord` plus a
  short display string for existing admin/list views.
- Redaction is applied before summary/inline projection. Generic scalar/string
  values are not inlined by default.
- Generic JSON/YAML operation responses are summary-only in diagnostics; secret
  bearing operation results should use typed result/value-class records.
- OB-03 owns external file/object storage, payload-reference resolution,
  retention, cleanup, authorization, and runtime configuration keys.

OB-03 completion note:

- Diagnostic payload externalization is runtime-configured and disabled by
  default.
- Develop/test mode can write selected diagnostic payloads to
  `target/cncf.d/observability/payloads`; production mode requires an explicit
  destination.
- Blob/object-store persistence is routed through CNCF `BlobStore`; CNCF does
  not add direct S3-specific APIs.
- CallTree, execution history, and Job calltree storage can attach payload
  references and externalization status without changing operation outcomes.
- System-admin payload reference resolution is available through the
  observability payload route, while broader dashboard drill-down remains OB-04.

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
