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

The current implementation has the inline `--calltree` path.

The retained admin-visible execution history is still a follow-up item.

## Next Step

- add retained execution observations to runtime state
- expose them through admin operations
- reuse the same data in dashboard / admin console
