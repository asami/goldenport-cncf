# Error Model Policy

status = normative
phase = 23

## 1. Purpose

This document is the Phase 23 normative entry point for CNCF Error Model
realignment. It fixes the policy boundary before changing runtime/model code.

The goals are:

- keep execution logic based on typed failure semantics;
- keep diagnostic explanation descriptive and observable;
- make taxonomy, cause, disposition, facets, and detail codes structured enough
  for helpers, tests, projections, metrics, and dashboards;
- allow pre-stable taxonomy and detail-code changes while Phase 23 converges.

## 2. Core Role Boundaries

### Conclusion

`Conclusion` is the logic-bearing failure semantic record.

- It is the stable input for control-flow decisions.
- It carries status, semantic classification, and enough structured information
  for projections.
- It is the source for Web/API/CLI/admin/debug failure behavior.
- It should not require callers to parse messages.

### Observation

`Observation` is descriptive evidence and diagnostics.

- It explains what was observed.
- It may include rich context, messages, source location, timestamps, and
  diagnostic records.
- It must not be the primary control-flow input.
- Tests and runtime decisions should not depend on exact Observation message
  text.

### Cause, Taxonomy, Disposition, and Facets

`Cause`, taxonomy, disposition, and `Descriptor.Facet` provide structured
classification.

- `Cause` identifies the coarse mechanism or reason family.
- Taxonomy identifies observed category/symptom vocabulary.
- Disposition identifies the intended handling direction where applicable.
- Facets carry machine-readable details such as parameter, field path, policy,
  algorithm, capability, permission, guard, relation, and reason.
- These structures are the preferred keys for helpers, tests, projections,
  metrics, dashboards, and operational diagnostics.

## 3. Pre-Stable Compatibility Policy

CNCF is not yet stable. During Phase 23:

- error taxonomy compatibility is not guaranteed;
- numeric ordering may be changed;
- detail codes may be regenerated or renumbered;
- classifications may be renamed, merged, split, or removed;
- compatibility aliases are not required before CNCF stable.

Each completed Phase 23 slice must still be deterministic and documented.
The purpose is to make the current version operable and evaluable while keeping
the model flexible enough to improve before stable release.

Stable-version compatibility, deprecation, and alias policy are out of EM-01
scope and should be defined later when CNCF approaches a stable release.

## 4. EM-01 Policy Decisions

- Messages are human/operator narrative, not primary matching keys.
- Detail codes are CNCF-visible semantic identifiers.
- CLI exit codes must not be derived directly from detail codes.
- `Status.detailCode`, `Status.detailCodes`, and `strategies` need explicit
  clarification in EM-03.
- Component-local labels and message parsing should be migrated to structured
  `Conclusion` data in EM-04 and EM-05.
- Historical draft notes are inputs only; this document and later Phase 23
  design/spec documents supersede them where they conflict.

## 5. Development Order

Phase 23 proceeds in this order:

1. EM-01 fixes this policy boundary.
2. EM-02 inventories taxonomy, cause, disposition, status/detail facets, and
   component-local classifications, then defines canonical ordering and
   numbering.
3. EM-03 defines deterministic detail-code generation.
4. EM-04 normalizes recurring framework and builtin-component failures onto
   structured `Consequence.Failure(Conclusion)` helpers.
5. EM-05 aligns Web/API/Admin/Observability projections on structured
   `Conclusion` data.
6. EM-06 verifies and closes the phase.

## 6. Historical Inputs

The following notes are provisional inputs to Phase 23, not the final normative
contract:

- `docs/notes/legacy/2026/05/error-semantics.md`
- `docs/notes/legacy/2026/05/conclusion-observation-design.md`
- `docs/notes/legacy/2026/05/observation-descriptor-error-notification-guideline.md`
- `docs/notes/legacy/2026/05/phase-2.9-error-realignment.md`

Use them as source material during inventory and redesign. Do not treat their
older compatibility, freeze, or finality wording as authoritative when it
conflicts with this Phase 23 policy.
