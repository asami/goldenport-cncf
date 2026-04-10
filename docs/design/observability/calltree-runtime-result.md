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

## Retained Runtime State

Inline result projection and retained runtime state are separate surfaces.

The current result projection is designed for immediate inspection of the
current operation. A future admin surface may retain recent execution
observations, including latest calltree and short execution history, for
dashboard and admin console use.

That future admin surface should reuse the same calltree event semantics rather
than inventing another timing vocabulary.
