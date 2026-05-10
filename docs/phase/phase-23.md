# Phase 23 — Error Model / Consequence-Conclusion Realignment

status = active

## 1. Purpose of This Document

This work document opens Phase 23 and makes
`9.7 Error Model / Consequence-Conclusion Realignment` the current development
item.

Phase 23 started by fixing the policy boundary for CNCF error semantics before
runtime/model changes. `EM-01 — Error Taxonomy / Detail Code Policy Opening`
is complete, and `EM-02 — Taxonomy / Cause / Disposition Inventory and
Canonical Ordering` is active.

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

## 3. Non-Goals

- No Scala/runtime behavior change in EM-01.
- No compatibility preservation for current pre-stable error codes unless a
  later Phase 23 slice explicitly chooses an alias or migration rule.
- No broad rewrite of every component-local failure in EM-01.
- No stable-version deprecation policy in EM-01; stable compatibility policy is
  a later pre-release hardening concern.

## 4. Active Work Stack

- A (DONE): EM-01 — Error Taxonomy / Detail Code Policy Opening.
- B (ACTIVE): EM-02 — Taxonomy / Cause / Disposition Inventory and Canonical Ordering.
- C (TODO): EM-03 — Detail Code Generation Model.
- D (TODO): EM-04 — Consequence Helper and Component Failure Normalization.
- E (TODO): EM-05 — Web/API/Admin/Observability Projection Alignment.
- F (TODO): EM-06 — Phase 23 verification and closure.

Resume hint:

- Continue with EM-02. Inventory current taxonomy, `Cause.Kind`, disposition,
  status/detail facets, and component-local classifications across
  `simplemodeling-lib`, CNCF, and builtin components.
- Use `docs/design/error-model-policy.md` as the normative Phase 23 policy
  boundary. Keep the error-code policy explicitly pre-stable: renumbering and
  classification changes are allowed during Phase 23, but each completed slice
  must be deterministic and documented.

## 5. Development Items

- [x] EM-01: Error Taxonomy / Detail Code Policy Opening.
- [ ] EM-02: Taxonomy / Cause / Disposition Inventory and Canonical Ordering.
- [ ] EM-03: Detail Code Generation Model.
- [ ] EM-04: Consequence Helper and Component Failure Normalization.
- [ ] EM-05: Web/API/Admin/Observability Projection Alignment.
- [ ] EM-06: Phase 23 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-23-checklist.md`.

## 6. Completion Conditions

Phase 23 can close when:

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
