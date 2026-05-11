# Calltree Runtime Result Projection

## Purpose

This document defines the runtime result projection semantics for CNCF calltree
observability.

The calltree projection is intended for operation-level execution inspection and
performance tuning. It is captured by the ExecutionContext and is returned as
debug data only when the inline debug option is enabled.

## Enablement

The primary configuration keys are:

- `textus.debug.calltree`
- `textus.debug.trace-job`
- `textus.debug.save-calltree`

The CLI-facing meta options are:

- `--debug.calltree`
- `--debug.trace-job`
- `--debug.save-calltree`

`--debug.calltree` returns the captured CallTree in the operation response
under top-level debug data. It does not make a Query retained as an inspectable
job.

For request debugging that must survive the immediate response, use debug trace-job
execution:

- CLI/client: `--debug.trace-job`
- HTTP/curl property: `textus.debug.trace-job=true`
- HTTP/curl header: `X-Textus-Debug-Trace-Job: true`

`--debug.save-calltree` requests CallTree storage on a persistent job even when
the execution succeeds and is not slow.

Older compatibility aliases may remain where already implemented, but new
documentation and samples should use the `textus.*` namespace and avoid a
generic `runtime` segment unless the value is specifically about the runtime
process itself.

## Result Placement

When inline debug is enabled, the operation result may include:

- `debug.calltree`

This projection is part of the operation result data. It is not an admin-only
report and it is not emitted as a side-channel log.

The reason is that calltree is frequently needed together with the business
result that caused the execution path. Returning it in the result data makes CLI
debugging, sample verification, and performance analysis reproducible.

## Node Kinds

The current projection uses explicit node kinds:

- `enter`: execution entered the labeled frame.
- `exit`: execution left the labeled frame.
- `active`: execution is still inside the labeled frame at result projection time.
- `failure`: execution recorded a failure for the labeled frame.

`active` is a snapshot. It does not mean the frame has completed.

This distinction is required because an operation can assemble its own result
while the outer action frame is still open. In that case, the result can safely
show elapsed time so far, but it must not pretend that an `exit` event has
already occurred.

## Timing Attributes

Timing attributes are represented as data attributes on calltree nodes.

`enter` nodes may include:

- `started_at_nanos`

`exit` nodes may include:

- `started_at_nanos`
- `ended_at_nanos`
- `duration_nanos`
- `duration_micros`
- `duration_millis`

`active` nodes may include:

- `started_at_nanos`
- `sampled_at_nanos`
- `duration_nanos`
- `duration_micros`
- `duration_millis`

`failure` nodes may include:

- `failed_at_nanos`

The `*_nanos` values are monotonic clock readings, not wall-clock timestamps.
They are intended for duration calculation and relative performance analysis
within a single process execution.

The `duration_*` values are redundant convenience projections of the same
elapsed interval. `duration_nanos` is the canonical precision value; micros and
millis are included for easier reading in YAML output and operational diagnosis.

## Retained Execution History

Inline result projection and retained execution history are separate surfaces.

The current result projection is designed for immediate inspection of the
current operation.

CNCF also retains action execution records for later inspection. Each retained
record should include:

- action / operation name
- input parameters
- input parameter summary
- outcome
- result type
- result summary
- captured time
- job id, when execution happened inside a managed job
- calltree projection, when calltree capture was enabled for that action

The initial retention policy is two-tiered:

- keep the most recent 100 action executions unconditionally
- keep up to 10000 additional executions that match configured debug filters

The intent is to make production use bounded by default while still allowing
debug-targeted executions to survive beyond the short recent window.

Job-managed debugging is the canonical path for target-request calltree
inspection. Normal Query executions are direct synchronous executions. When
`textus.debug.trace-job=true` is present, Query execution remains
response-compatible but the JobEngine uses a persistent job so operators can inspect:

- `/web/system/admin/jobs`
- `/web/system/admin/jobs/{jobId}`
- `job_control.job.get_job_calltree`

HTTP responses that create or return a managed debug job include
`X-Textus-Job-Id`. The CLI client reads that protocol header and, only when
`--debug.trace-job` is explicitly requested, prints
`Debug job: <jobId> (/web/system/admin/jobs/<jobId>)` to stderr. The response
body/stdout contract remains the original operation result.

The older `admin.execution.history` and `admin.execution.calltree` surfaces
remain useful as execution-history references, but target-request debugging
should prefer the job-specific admin detail page.

Configuration keys:

- `textus.execution.history.recent-limit`: size of the unconditional recent buffer
- `textus.execution.history.filtered-limit`: size of the debug-filtered buffer
- `textus.execution.history.filter.operation-contains`: comma-separated operation-name substrings retained in the filtered buffer

These keys are runtime variation points. They are intended to be supplied from
configuration, environment, or CLI property sources and to appear in admin
variation inspection surfaces.

Variation list inspection should show:

- key
- current/default value
- short description

Individual variation detail operations should show:

- key
- current/default value
- short description
- detailed description for the individual variation

Admin retrieval surfaces:

- `admin.execution.history`: returns retained action execution records
- `admin.execution.calltree`: returns the calltree projection from the latest retained action execution

`admin.execution.history` returns a record with:

- `recent_limit`
- `filtered_limit`
- `filter_count`
- `operation_filter`
- `count`
- `executions`

Each entry in `executions` uses:

- `id`
- `operation`
- `parameters`
- `parameters_text`
- `outcome`
- `result_type`
- `result_summary`
- `captured_at`
- `calltree`, only when calltree capture was enabled for that action

`admin.execution.calltree` returns the latest retained execution using the same
entry fields and always includes `calltree`. If the latest execution was not
captured with calltree enabled, `calltree` is an empty sequence. If no retained
execution exists, it returns an empty-status record.

History display should support filtering by operation name so dashboard and
admin-console views can focus on the debugging target without dumping the full
retention buffer.

Admin and dashboard surfaces must reuse the same calltree event semantics rather
than inventing another timing vocabulary.

## Result Payload Policy

CallTree is an execution diagnostic structure, not a result payload archive.
Action, UnitOfWork, Space, I/O, Task, and Job calltree projections should carry
compact result summaries by default.

Default inline result data should be limited to:

- result kind/type;
- outcome/status;
- byte/character size where applicable;
- record/element count where applicable;
- paging summary such as `offset`, `limit`, `fetched_count`, and `total_count`;
- small scalar or small structured values that are safe to show inline.

OB-02 standardizes this shape as `DiagnosticPayloadSummary`. CallTree action,
UnitOfWork, Space, and I/O nodes should put summary records in payload-bearing
fields such as `request`, `web_parameters`, `query`, `response`, and `result`.
Those records preserve compatible projection keys such as `kind`, `inline`,
`size_bytes`, `field_count`, `record_count`, `fetched_count`, `total_count`,
and optional `payload_href` / `external_href`.

OB-03 adds opt-in externalization. When configured, matching CallTree
payload-bearing fields may include `externalization_status`,
`externalization_reason`, and `DiagnosticPayloadReference` projection keys such
as `payload_href`, `payload_ref`, and `payload_storage`. The default remains no
external write.

Large result/response bodies must not be copied wholesale into CallTree,
execution history, Job Entity, Task Execution Tree, or task-local calltree
records. For large values, the diagnostic projection should show summary
metadata and an explicit indication that the full payload is not inline.

Scalar/string result values are non-inline by default. CML confidentiality
metadata is applied before any inline display or summary generation, with
name-based redaction kept only as a fallback.

Generic JSON/YAML operation responses are also non-inline by default. They are
not considered secret-aware payloads because arbitrary JSON/YAML fields cannot
be reliably matched to CML result confidentiality metadata. Secret-bearing
operation results should be modeled as typed result/value-class records so the
diagnostic summarizer can apply field-level confidentiality before projection.

The cross-cutting Phase 24 policy is defined in
`docs/design/observability/diagnostic-payload-externalization-policy.md`.
That policy owns the inline/summary/truncated/externalized vocabulary,
redaction-before-output rule, and external payload reference boundary.

This keeps Web/admin diagnostics readable while still allowing deep inspection
when a developer or operator explicitly asks for large payload capture.
