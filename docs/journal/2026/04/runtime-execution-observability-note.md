# Runtime Execution Observability Note

## Purpose

Record the current direction for execution-time observability in CNCF runtime.

## Current Direction

Execution observability has two separate surfaces:

- inline result projection
- retained runtime-observable state

### Inline Result Projection

The current generic meta option is:

- `--calltree`

When enabled, the operation result may include a `calltree` projection.

This is intended for:

- immediate inspection of a single execution
- CLI-driven debugging
- sample walkthroughs

### Retained Runtime State

The runtime should also retain recent execution observations for later inspection.

Intended future surfaces include:

- latest execution snapshot
- latest calltree
- short execution history
- request / response summary

The intended retrieval surface is:

- `admin.execution.*`

and later:

- dashboard / admin console

## Current Position

At the time of this note, the implementation had the inline `--calltree` path.

Follow-up implementation has since added retained admin-visible execution
history and calltree retrieval.

Current implemented surfaces include:

- `admin.execution.history`
- `admin.execution.calltree`

The retained execution record includes action / operation identity, input
parameters, result summary, outcome, captured time, and calltree projection when
calltree capture was enabled.

The current design summary is maintained in:

- `docs/design/observability/calltree-runtime-result.md`

## Next Step

- keep the design document aligned with implementation changes
- reuse the same data in dashboard / admin console
