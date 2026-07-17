# Execution Clock

Date: 2026-07-15

## Purpose

CNCF owns one runtime clock and carries it into every `ExecutionContext`.
Components use that clock through the internal DSL so time-dependent behavior
can run consistently under production time and a configured virtual start.

## Configuration

The primary key is:

```text
textus.clock.virtual-start-at
```

The value must be an ISO-8601 date-time with `Z` or an explicit offset, for
example:

```text
2026-07-28T09:00:00Z
2026-07-28T18:00:00+09:00
```

The key may be supplied by ordinary CNCF configuration or at startup:

```console
cncf --textus.clock.virtual-start-at=2026-07-28T18:00:00+09:00 server
```

`textus.runtime.clock.virtual-start-at`, `cncf.clock.virtual-start-at`, and
`cncf.runtime.clock.virtual-start-at` remain compatibility aliases. A local
date-time without an offset is rejected because it does not identify one
instant.

The unified execution-profile keys are
`textus.execution.time.mode` and `textus.execution.time.start-at`. The existing
`textus.clock.virtual-start-at` key maps to offset mode when the unified keys
are absent. If both start keys are present they must identify the same instant;
a mismatch is a configuration error. Manual mode conflicts with the offset
compatibility key.

## Semantics

Without the key, runtime bootstrap selects the system UTC clock. With the key,
bootstrap creates `Clock.offset(systemClock, duration)` so the first observed
time is the configured instant and time continues to advance with the process.
This is not a fixed clock.

The same runtime clock instance is used to create the global execution context
and is recovered from `GlobalRuntimeContext` when component execution contexts
are created. The execution-context timezone remains a separate concern; the
initial implementation uses UTC.

The test-only controlled profile selects a manual clock and an operational
scheduler as one runtime-owned pair. Its in-process test control may advance
time and run eligible CNCF work until idle. Components can read the selected
clock but cannot advance it. Manual time control does not change monotonic
performance measurement and does not introduce general scheduling semantics.

## Component Rule

Handwritten component and provider behavior accesses time only through these
protected internal DSL helpers:

- `execution_clock`;
- `current_instant`;
- `current_zoned_datetime`.

Direct process-clock creation or `now()` calls in component logic bypass
runtime policy and deterministic execution, so they are prohibited. Framework
bootstrap code is the boundary that may select a system clock.

## Verification Evidence

Executable specifications cover:

- advancing offset behavior against an advancing base clock;
- explicit-offset parsing and local-date-time rejection;
- configuration and CLI argument resolution;
- propagation from global runtime configuration to component execution;
- internal DSL access to the injected clock.

`src/test/scala/org/goldenport/cncf/action/ExecutionClockDslSpec.scala`
demonstrates both a fixed `ExecutionContext` clock and the normal controlled
runtime-to-ActionCall path. The component runtime boundary rules are
`R1` and `R2` in `docs/spec/component-runtime-boundary-capabilities.md`.

Operational tests that use virtual time must record both the wall-clock run
date and the configured CNCF execution date. Virtual time validates runtime
behavior and date-sensitive flows; it must not be presented as unrecorded
wall-clock elapsed time.

## Related Contracts

The complete execution-profile, random, ID, scheduling, environment, and
replayability contract is defined in
`docs/design/execution-determinism.md`.

## Related Non-Normative Work

Broader execution determinism, including random streams, ID entropy, manual
scheduling, execution ordering, and environment assumptions, is explored in
`docs/notes/execution-determinism-capability-design.md`. That note does not
change the clock contract defined here.
