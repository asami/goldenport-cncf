/*
 * @since   Apr. 10, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */

# Runtime Launch Unification Note

## Intent

`CncfMain` had accumulated multiple partial interpretations of CLI arguments and
configuration values before delegating to `CncfRuntime`. This made subsystem
selection, repository handling, and launch behavior drift between:

- raw CLI argument parsing
- resolved configuration lookup
- runtime invocation normalization

The current direction is to make `CncfRuntime` the canonical owner of launch
parameter interpretation and leave `CncfMain` as a thin CLI adapter.

## What Has Moved Into Runtime

The following concerns now live primarily in `CncfRuntime`:

- configuration bootstrap
- canonical invocation parameter normalization
- subsystem name resolution
- subsystem descriptor lookup and invocation rewriting
- front parameter parsing for:
  - `--discover=classes`
  - `--workspace`
  - `--component-factory-class`
  - `--force-exit`
  - `--no-exit`
- extra component assembly
- class discovery helper logic

## Current Role Of CncfMain

`CncfMain` is now mostly responsible for:

- obtaining the current working directory
- delegating bootstrap and front/invocation normalization to `CncfRuntime`
- extracting repository arguments through repository-space helpers
- deciding exit behavior for the CLI wrapper

This is much closer to the intended shape of a thin adapter.

## Why This Matters

This unification reduces the chance that:

- CLI and config specify the same concept differently
- pre-runtime behavior sees a different subsystem than runtime execution
- activation policy diverges between entry points

It also makes future changes easier in areas such as:

- component activation policy
- subsystem activation policy
- runtime observability and admin surfaces
- dashboard/admin-console integration

## Remaining Work

The launch path is not fully unified yet.

Remaining areas to revisit:

- whether `CncfMain` can be reduced even further or folded into runtime entry logic
- whether repository activation/search policy can be represented in the same
  canonical launch model as invocation parameters
- whether embedding entry points can share more of the same bootstrap path

## Current Rule Of Thumb

- `CncfRuntime` owns canonical launch interpretation
- `CncfMain` should not introduce an alternative interpretation of the same
  parameter/config concepts
