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
- repository activation/search policy bootstrap
- front parameter parsing for:
  - `--discover=classes`
  - `--workspace`
  - `--component-factory-class`
  - `--force-exit`
  - `--no-exit`
- extra component assembly
- class discovery helper logic
- embedding handle initialization

The runtime-side launch bootstrap now also carries the current activation rule:

- packaged `repository.d/*.car` stays search-oriented and does not auto-activate by default
- packaged `component.d/*.car` is treated as an active packaged source
- expanded `car.d` and `sar.d` are treated as development/debug shapes and can auto-activate
- subsystem-selected repository injection avoids re-injecting a repository that is already active
- duplicate components introduced by runtime extras are collapsed during initialization

## Current Role Of CncfMain

`CncfMain` is now mostly responsible for:

- obtaining the current working directory
- delegating bootstrap and front/invocation normalization to `CncfRuntime`
- deciding exit behavior for the CLI wrapper

This is much closer to the intended shape of a thin adapter.

`CncfBootstrap` is also thinner now:

- runtime handle creation is owned by `CncfRuntime`
- bootstrap remains as a facade-oriented embedding entry point

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
- whether embedding entry points can share more of the same bootstrap path
- whether a single public launch/embedding model should replace multiple thin
  facades over time

## Current Rule Of Thumb

- `CncfRuntime` owns canonical launch interpretation
- `CncfMain` should not introduce an alternative interpretation of the same
  parameter/config concepts
