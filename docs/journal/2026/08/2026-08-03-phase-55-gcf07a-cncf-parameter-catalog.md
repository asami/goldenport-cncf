# Phase 55 GCF-07A CNCF Parameter Catalog

GCF-07A establishes the first CNCF-owned closed parameter catalog. It adopts
the already authoritative Phase 53 Subsystem user-mode semantic without
migrating RuntimeConfig, StandaloneUserProfile, Web execution, ExecutionContext,
or legacy configuration consumers.

## Completed Behavior

- The catalog owns one exact `ConfigurationParameter[SubsystemUserMode]`
  witness for `textus.subsystem.user-mode`.
- Only `standalone` and `multi-user` decode; the catalog admits only
  SubsystemInstance targets.
- `textus.runtime.subsystem.user-mode`, `cncf.subsystem.user-mode`, and
  `cncf.runtime.subsystem.user-mode` decode to the canonical witness while
  retaining original spelling and document path in provenance.
- Unknown spellings, obsolete Web selectors, malformed values, non-Subsystem
  targets, and canonical-plus-alias collisions fail structurally before
  resolution.

## Validation Evidence

- Generic `Test/compile` passed: `66357-20260802T173803Z`.
- Generic focused core/candidate/resolution suites passed with 22 succeeded and
  0 failed: `66786-20260802T173847Z`.
- CNCF `Test/compile` passed: `67234-20260802T173933Z`.
- CNCF focused catalog, decoder, Phase 55 binding contract, resolver, and
  Subsystem user-mode suites passed with 17 succeeded, 0 failed, and 2
  intentional pending: `69862-20260802T174434Z`.

Full suites remain reserved for the Phase 55 release gate. GCF-07 remains open
for later runtime/profile adoption; this journal does not change those
contracts.
