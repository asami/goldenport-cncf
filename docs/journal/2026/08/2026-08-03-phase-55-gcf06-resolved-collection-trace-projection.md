# Phase 55 GCF-06 Resolved Collection and Trace Projection

GCF-06 completes the generic derived diagnostic trace boundary. The generic
trace is derived only from `ConfigurationBindingCollection`; it does not become
a second selection or mutation authority. CNCF executable specifications
exercise the generic projection without converting qualified targets into a
catalog or String target representation. Existing CNCF legacy trace consumers
remain GCF-09 migration work.

## Completed Behavior

- Explanations are deterministically ordered by canonical parameter identity.
- Lookup accepts the exact original typed parameter witness and rejects a
  different witness for the same canonical identity structurally.
- Target, visible-or-redacted value, provenance, and newest-first direct
  override history are projected from the same effective binding graph.
- The diagnostic history retains at most 16 entries and records the omitted
  count.
- Source identity is bounded to 256 UTF-16 code units without splitting a
  surrogate pair; the truncated code-unit count is retained.
- Confidential effective and historical values are structurally `Redacted`
  without encoding a secret for the trace.
- CNCF qualified targets pass through the generic projection unchanged.

## Validation and Review Evidence

- Generic focused trace validation passed with 27 succeeded and 0 failed:
  `36123-20260802T164435Z`.
- Generic `Test/compile` passed: `36631-20260802T164525Z`.
- Generic development `publishLocal` for `goldenport-core_3:0.4.3-SNAPSHOT`
  passed: `37108-20260802T164616Z`.
- CNCF `Test/compile` passed: `40159-20260802T165249Z`.
- CNCF focused binding trace, resolver, and Phase 55 contract suites passed
  with 8 succeeded, 0 failed, and 3 intentional pending:
  `41867-20260802T165630Z`.
- The independent review and focused re-review are clean. Direct `git diff
  --check` passed in both admitted repositories.

The intentional CNCF pending scenarios remain later-stage work. Full suites
remain reserved for the Phase 55 release gate.
