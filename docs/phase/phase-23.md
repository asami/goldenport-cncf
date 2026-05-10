# Phase 23 — Error Model / Consequence-Conclusion Realignment

status = closed

## 1. Purpose of This Document

This work document records the completed Phase 23 work for
`9.7 Error Model / Consequence-Conclusion Realignment`.

Phase 23 fixed the policy boundary for CNCF error semantics.
`EM-01 — Error Taxonomy / Detail Code Policy Opening`,
`EM-02 — Taxonomy / Cause / Disposition Inventory and Canonical Ordering`, and
`EM-03 — Detail Code Generation Model`, `EM-04 — Consequence Helper and
Component Failure Normalization`, and `EM-05 — Web/API/Admin/Observability
Projection Alignment` are complete. `EM-06 — Phase 23 verification and closure`
is complete.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Reorganize `Consequence` / `Conclusion` / `Observation` responsibilities.
- Inventory and redesign taxonomy, cause, disposition, status/detail facets.
- Add, merge, and reserve categories needed for near-future CNCF work.
- Reorder taxonomy, cause, and disposition kinds meaningfully and renumber them.
- Establish detail-code generation rules and put them into normal operation.
- Align Web/API/debug/admin/metrics projections on structured `Conclusion`.
- Convert draft/provisional notes into a normative Error Model contract.

Compatibility policy:

- Pre-stable CNCF does not guarantee backward compatibility for error taxonomy,
  numeric ordering, or detail codes.
- Phase 23 may renumber, rename, merge, or split error classifications.
- Changes must be documented and tested, but compatibility aliases are not
  required until CNCF stable.
- Within each completed slice, generated/observed detail codes should be
  deterministic so the current implementation can be operated and evaluated.

Historical/provisional inputs:

- `docs/notes/legacy/2026/05/error-semantics.md`
- `docs/notes/legacy/2026/05/conclusion-observation-design.md`
- `docs/notes/legacy/2026/05/observation-descriptor-error-notification-guideline.md`
- `docs/notes/legacy/2026/05/phase-2.9-error-realignment.md`

These notes are inputs to Phase 23. They are not the final normative contract.
The first normative policy entry point is `docs/design/error-model-policy.md`.
The canonical EM-02 vocabulary catalog is
`docs/design/error-taxonomy-catalog.md`. The EM-03 numeric detail-code policy is
`docs/design/error-detail-code-policy.md`.

## 3. Non-Goals

- No Scala/runtime behavior change in EM-01.
- No compatibility preservation for current pre-stable error codes unless a
  later Phase 23 slice explicitly chooses an alias or migration rule.
- No broad rewrite of every component-local failure in EM-01.
- No stable-version deprecation policy in EM-01; stable compatibility policy is
  a later pre-release hardening concern.

## 4. Closed Work Stack

- A (DONE): EM-01 — Error Taxonomy / Detail Code Policy Opening.
- B (DONE): EM-02 — Taxonomy / Cause / Disposition Inventory and Canonical Ordering.
- C (DONE): EM-03 — Detail Code Generation Model.
- D (DONE): EM-04 — Consequence Helper and Component Failure Normalization.
- E (DONE): EM-05 — Web/API/Admin/Observability Projection Alignment.
- F (DONE): EM-06 — Phase 23 verification and closure.

Resume hint:

- Phase 23 closed; select next phase.
- Future Error Model hardening remains in the development candidates rather than
  as active Phase 23 work.

## 5. Development Items

- [x] EM-01: Error Taxonomy / Detail Code Policy Opening.
- [x] EM-02: Taxonomy / Cause / Disposition Inventory and Canonical Ordering.
- [x] EM-03: Detail Code Generation Model.
- [x] EM-04: Consequence Helper and Component Failure Normalization.
- [x] EM-05: Web/API/Admin/Observability Projection Alignment.
- [x] EM-06: Phase 23 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-23-checklist.md`.

## 6. Completion Conditions

Phase 23 closed after:

- `Consequence`, `Conclusion`, and `Observation` responsibilities are
  documented and reflected in implementation-facing helpers.
- Canonical taxonomy, cause, disposition, and status/detail facet vocabularies
  are ordered, numbered, and tested.
- Detail-code generation is deterministic and usable in Web/API/debug/admin
  projections.
- Reusable framework and builtin components use structured
  `Consequence.Failure(Conclusion)` paths instead of component-local labels or
  message parsing where Phase 23 chooses to normalize them.
- Metrics, dashboards, Web/admin diagnostics, and observability records project
  structured `Conclusion` data consistently.
- Deferred post-Phase-23 compatibility/stability work is explicitly named.

## 7. Closure Note

Completed scope:

- Formal Error Model vocabulary moved to `org.goldenport.observation` and
  `org.goldenport.conclusion`, with `org.goldenport.Conclusion` remaining the
  public aggregate type.
- Canonical taxonomy, cause, interpretation, disposition, status, and detail
  vocabulary ordering is documented and tested.
- `DetailCode` is a generated numeric `Long` carried by
  `Conclusion.Status.detailCode`; message text and debug labels are not part of
  the generation key.
- `Conclusion.Status` carries generated `webCode`, generated `detailCode`, and
  optional `appCode` / `appStatus`; `Status.detailCodes` and
  `Status.strategies` are removed from the active model.
- Reusable helpers and representative CNCF Blob, Static Form/Web, Job, and
  Event failure paths were normalized onto structured
  `Consequence.Failure(Conclusion)` values with `Conclusion.previous` as the
  source-error link.
- Web/API/Admin/Observability projections read structured status and diagnostic
  fields from materialized `Conclusion` values and do not expose legacy
  `http.xxx` / `codeSource` fields as the active error contract.

Validation evidence from the implementation closure:

- `simplemodeling-lib`: `sbt --batch test` passed.
- CNCF: `sbt --batch test` passed.

Deferred follow-ups are tracked in
`docs/strategy/cncf-development-strategy.md` section 9.7 and related
Observability/Web follow-up sections.
