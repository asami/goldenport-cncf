# Phase 55 GCF-09H — SystemNode shutdown configuration

Date: 2026-08-03

## Decision

Move SystemNode shutdown drain timeout authority from a raw resolved map to the
closed CNCF configuration catalog.

- Canonical key: `textus.system-node.shutdown.drain-timeout-millis`
- Target: `SubsystemInstance` only
- Value: positive integral milliseconds, 1..300000
- Default: 30000 ms when the binding is absent
- Aliases: none
- Precedence: typed per-SystemNode override, resolved binding, then default

Malformed, non-numeric, zero, negative, and over-bound input fails with
structured configuration evidence before Node construction. There is no raw
Subsystem, Action, HTTP, Job, or Component override path.

## Implementation evidence

`SystemNodeShutdownConfiguration` selects the bounded typed value and records
only `Default`, `Resolved`, or `Override` source kind. `CncfRuntime` builds the
resolved collection, creates one SystemNode before the factory, and logs only
that source kind plus the bounded duration. `Subsystem` receives the
preconstructed physical owner through an internal construction boundary and
otherwise uses only the typed default for direct legacy construction.

The existing Phase 54 lifecycle tests were moved from raw-map construction to
the catalog codec and typed configuration boundary; their pool/lease/shutdown
semantics remain unchanged.

## Validation

- `Test/compile`: invocation `9874-20260803T145541Z`, passed.
- Focused suites: `SystemNodeShutdownConfigurationSpec`,
  `CncfConfigurationParameterCatalogSpec`,
  `SystemNodeDataStoreShutdownSpec`, and
  `SystemNodeDataStorePoolRuntimeSpec`; invocation
  `10580-20260803T145650Z`, 37 passed, 0 failed.

## Review fix

Independent review required explicit runtime construction coverage, complete
alias/scope/bounds catalog coverage, and same-file naming repairs. The runtime
specification now proves that a split-file Subsystem timeout is present on the
factory-produced SystemNode and that zero fails before later factory follow-on
work. Catalog specifications explicitly reject alias, Global, ComponentClass,
and ComponentInstance inputs; the configuration observation message contains
only source kind and bounded milliseconds.

- Review-fix `Test/compile`: invocation `22024-20260803T151441Z`, passed.
- Review-fix focused suites add `CncfRuntimeSnapshotBootstrapSpec`; invocation
  `23011-20260803T151624Z`, 48 passed, 0 failed.

Focused re-review is complete.
