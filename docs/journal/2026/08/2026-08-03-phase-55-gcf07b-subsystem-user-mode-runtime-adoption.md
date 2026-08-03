# Phase 55 GCF-07B Subsystem User-Mode Runtime Codec Adoption

GCF-07B connects the Phase 53 Subsystem user-mode runtime boundary to the
registered GCF-07A `ConfigurationParameter[SubsystemUserMode]` codec.

## Completed Implementation Boundary

- `SubsystemUserMode.resolveForSubsystem` decodes a present canonical effective
  value through `CncfConfigurationParameterCatalog.subsystemUserMode.codec`.
- Existing `ResolvedConfiguration` selection, `ConfigurationResolution`
  provenance, absent-value derived defaults, and Subsystem security validation
  remain authoritative and unchanged in ownership.
- Malformed enum values and present non-string values fail structurally through
  the registered codec; neither derives standalone.
- Historical Phase 53 CS-01--CS-07 sources and executable specifications remain
  unchanged. GCF-07B provides separate Phase 55 executable evidence.

## Deliberately Deferred

- `.textus` / `.cncf` physical-source typed candidate migration;
- StandaloneUserProfile fields, fixed-user identity/formatting, and Web
  parameters;
- `ExecutionContext` and Component migration; and
- replacing legacy trace consumers.

Those changes require an explicit single-load source-projection boundary; this
slice does not create a second effective-value or provenance authority.

## Validation Evidence

- CNCF `Test/compile` passed at serial invocation
  `88637-20260802T182131Z`.
- The focused catalog-runtime, Phase 53 Subsystem user-mode, and runtime
  admission suites passed with 13 succeeded and 0 failed at serial invocation
  `89279-20260802T182237Z`.

Both invocations completed with `sbt_exit=0`, `wrapper_exit=0`, and the shared
serialized SBT lock released. Full suites remain reserved for the Phase 55
release gate; independent review remains the next stage.
