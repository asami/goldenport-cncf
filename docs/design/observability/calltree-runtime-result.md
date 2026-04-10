# Calltree Runtime Result Projection

## Purpose

This document defines the runtime result projection semantics for CNCF calltree
observability.

The calltree projection is intended for operation-level execution inspection and
performance tuning. It is returned as part of the operation result data only when
the generic calltree meta option is enabled.

## Enablement

The primary configuration key is:

- `textus.calltree`

The CLI-facing meta option is:

- `--calltree`

Older compatibility aliases may remain where already implemented, but new
documentation and samples should use the `textus.*` namespace and avoid a
generic `runtime` segment unless the value is specifically about the runtime
process itself.

## Result Placement

When enabled, the operation result may include:

- `calltree`

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
- calltree projection, when calltree capture was enabled for that action

The initial retention policy is two-tiered:

- keep the most recent 100 action executions unconditionally
- keep up to 10000 additional executions that match configured debug filters

The intent is to make production use bounded by default while still allowing
debug-targeted executions to survive beyond the short recent window.

Admin retrieval surfaces:

- `admin.execution.history`: returns retained action execution records
- `admin.execution.calltree`: returns the calltree projection from the latest retained action execution

History display should support filtering by operation name so dashboard and
admin-console views can focus on the debugging target without dumping the full
retention buffer.

Admin and dashboard surfaces must reuse the same calltree event semantics rather
than inventing another timing vocabulary.
