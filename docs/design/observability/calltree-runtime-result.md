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
