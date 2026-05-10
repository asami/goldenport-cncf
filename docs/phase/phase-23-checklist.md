# Phase 23 — Error Model / Consequence-Conclusion Realignment Checklist

This document contains detailed task tracking and decisions for Phase 23.
It complements the summary-level phase document (`phase-23.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-23.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## EM-01: Error Taxonomy / Detail Code Policy Opening

Status: DONE

### Objective

Open Phase 23 and fix the scope, compatibility policy, numbering policy intent,
and development order for Error Model realignment before runtime/model changes.

### Detailed Tasks

- [x] Confirm Phase 23 is selected in strategy and Phase 22 remains the latest
      closed phase.
- [x] Record the pre-stable compatibility policy:
  - error taxonomy compatibility is not guaranteed;
  - numeric ordering and detail codes may be renumbered;
  - classifications may be renamed, merged, or split;
  - compatibility aliases are not required until CNCF stable.
- [x] Identify historical/provisional inputs:
  - `docs/notes/legacy/2026/05/error-semantics.md`;
  - `docs/notes/legacy/2026/05/conclusion-observation-design.md`;
  - `docs/notes/legacy/2026/05/observation-descriptor-error-notification-guideline.md`;
  - `docs/notes/legacy/2026/05/phase-2.9-error-realignment.md`.
- [x] Define the development order from policy opening to implementation:
  - inventory and canonical ordering;
  - detail-code generation;
  - helper/component failure normalization;
  - Web/API/Admin/Observability projection alignment;
  - verification and closure.
- [x] Keep EM-01 docs/status only.

### Expected Output

- Phase 23 is opened as the active Error Model phase.
- The taxonomy/detail-code compatibility policy is explicit.
- Later slices can change `simplemodeling-lib` and CNCF runtime without
  re-litigating pre-stable compatibility.

### Guardrails

- Do not change runtime/model code in EM-01.
- Do not preserve current detail-code or numeric taxonomy compatibility merely
  because existing values are present.
- Do not treat historical draft notes as normative until they are rewritten or
  superseded by Phase 23 docs/specs.

### Completion Notes

- `docs/design/error-model-policy.md` is the normative Phase 23 policy entry
  point.
- The role boundaries are fixed:
  - `Conclusion` is logic-bearing failure semantics and stable control input.
  - `Observation` is descriptive evidence and diagnostics, not control flow.
  - `Cause`, taxonomy, disposition, and facets are structured classification
    for helpers, tests, projections, metrics, and dashboards.
- Pre-stable compatibility is explicit: taxonomy, numeric ordering, and detail
  codes may change during Phase 23; aliases are not required before CNCF stable.
- Historical notes remain provisional inputs and are superseded where they
  conflict with the Phase 23 policy.

---

## EM-02: Taxonomy / Cause / Disposition Inventory and Canonical Ordering

Status: DONE

### Objective

Inventory current taxonomy, cause, disposition, status/detail facets, add or
merge categories, reserve near-future categories, and define canonical ordering
and numbering.

### Detailed Tasks

- [x] Inventory current `Taxonomy`, `Cause.Kind`, disposition, status, and
      detail-code use across `simplemodeling-lib`, CNCF, and builtin
      components.
- [x] Identify duplicates, ambiguous categories, missing categories, and
      component-local labels that should become core vocabulary.
- [x] Define canonical order and numbering rules for taxonomy/cause/disposition
      values.
- [x] Add tests that intentionally assert the new canonical order and numbers.

### Guardrails

- Compatibility with current pre-stable numeric values is not required.
- Do not implement detail-code generation before the canonical vocabulary is
  chosen.

### Completion Notes

- Active error-model vocabulary moved out of `org.goldenport.provisional.*`:
  - `org.goldenport.observation` now owns `Observation`, `Taxonomy`, `Cause`,
    and observation-side axes.
  - `org.goldenport.conclusion` now owns `Interpretation` and `Disposition`.
  - `org.goldenport.Conclusion` remains the public aggregate type.
- Canonical taxonomy/cause/disposition ordering and numbers are documented in
  `docs/design/error-taxonomy-catalog.md`.
- Pre-stable spelling defects were fixed:
  - `record` is no longer rendered as `value`;
  - `network` is no longer rendered as `operation`;
  - `subsystem` is no longer rendered as `subsytem`;
  - `redundant` is no longer rendered as `missing`.
- `simplemodeling-lib` tests assert the formal package usage and canonical
  vocabulary values.

---

## EM-03: Detail Code Generation Model

Status: DONE

### Objective

Define and implement deterministic detail-code generation for CNCF-visible
errors.

### Detailed Tasks

- [x] Define detail-code family, subcode, facet, and source mapping rules.
- [x] Define how generated detail codes relate to taxonomy, cause, disposition,
      and `Descriptor.Facet`.
- [x] Decide that hand-authored detail-code overrides are not allowed.
- [x] Add tests for stable generation and projection.

### Completion Notes

- `DetailCode` is a numeric `Long` semantic code generated from structured
  `Conclusion` data.
- `Conclusion.Status` carries generated `webCode`, generated numeric
  `DetailCode`, and optional application `appCode` / `appStatus` metadata.
- `Status.strategies` is removed; `Disposition` is the reaction guidance axis.
- `docs/design/error-detail-code-policy.md` records the EM-03 normative policy.

### Guardrails

- Do not derive CLI exit codes directly from detail codes.
- Do not make human-readable messages part of the code-generation key.

---

## EM-04: Consequence Helper and Component Failure Normalization

Status: ACTIVE

### Objective

Normalize recurring framework and builtin-component failures onto structured
`Consequence.Failure(Conclusion)` helpers.

### Detailed Tasks

- [ ] Review existing `Consequence` helpers and add missing semantic helpers
      where recurring failure shapes are found.
- [ ] Replace selected component-local labels/message parsing with structured
      taxonomy/cause/facet construction.
- [ ] Keep low-level `Consequence.failure` available for intentionally
      application-specific structured failures.

### Guardrails

- Do not rewrite unrelated application behavior.
- Do not branch execution logic on `Observation` text or attributes.

---

## EM-05: Web/API/Admin/Observability Projection Alignment

Status: TODO

### Objective

Project structured `Conclusion` data consistently into Web/API/debug/admin,
metrics, dashboards, and observability records.

### Detailed Tasks

- [ ] Align HTTP/Web error rendering with the canonical detail-code model.
- [ ] Align CLI/API projection with `Conclusion` rather than message parsing.
- [ ] Align observability and metrics classification with taxonomy/cause/facet
      data.
- [ ] Ensure debug/admin surfaces expose enough structured detail without
      treating descriptive `Observation` data as control-flow input.

### Guardrails

- Do not expose unstable internal messages as client contract.
- Do not hide structured detail behind free-text-only diagnostics.

---

## EM-06: Phase 23 Verification and Closure

Status: TODO

### Objective

Verify Phase 23 work and close or explicitly defer remaining Error Model scope.

### Detailed Tasks

- [ ] Confirm Phase 23 docs and strategy status match implemented behavior.
- [ ] Confirm EM-01 through EM-05 are DONE or explicitly deferred.
- [ ] Run focused and full validations required by touched implementation
      slices.
- [ ] Record closure notes and next development candidates.

### Guardrails

- Do not close Phase 23 while active Error Model work remains implicit.
