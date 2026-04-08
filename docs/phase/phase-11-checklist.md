# Phase 11 — Component Wiring and Subsystem Construction Checklist

This document contains detailed task tracking and execution decisions
for Phase 11.

It complements the summary-level phase document (`phase-11.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-11.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]`
  in the phase document.

---

## Status Semantics

- ACTIVE: currently being worked on (only one item may be ACTIVE).
- SUSPENDED: intentionally paused with a clear resume point.
- PLANNED: planned but not started.
- DONE: handling within this phase is complete.

---

## Recommended Work Procedure

Phase 11 implementation proceeds in this order:

1. CW-01 (port/binding baseline) must be fixed first.
2. CW-02 (subsystem construction path) is implemented next on top of the fixed binding model.
3. CW-03 (executable specifications) is expanded alongside CW-02 and finalized before closure.
4. CW-04 (sample-facing wiring guidance) is finalized after the framework path is stable.

This order minimizes churn in sample-side assembly and adapter realization.

---

## CW-01: Stabilize Component Port/Binding Baseline

Status: ACTIVE

### Objective

Stabilize the wiring-time model for:

- `Port` API resolution
- `VariationPoint` current/inject semantics
- `ExtensionPoint` adapter realization
- binding installation into `Component.Port`

### Detailed Tasks

- [ ] Remove remaining assumptions from the old runtime-shaped port skeleton.
- [ ] Confirm `Port` metamodel shape (`api`, `spi`, `variation`) as the canonical framework baseline.
- [ ] Confirm `ExtensionPoint` as adapter/provider realization boundary.
- [ ] Confirm `Binding.install(...)` and named binding registry behavior.
- [ ] Clarify legacy compatibility behavior for single default binding.
- [ ] Add or update executable specs protecting the baseline semantics.

### Inputs

- `docs/journal/2026/04/port-extension-point-variation-point-redesign-2026-04-07.md`
- `src/main/scala/org/goldenport/cncf/component/Port.scala`
- `src/main/scala/org/goldenport/cncf/component/Component.scala`

---

## CW-02: Subsystem Construction via Bound Components

Status: PLANNED

### Objective

Define subsystem construction so that subsystem assembly is performed
through bound Components rather than ad hoc factory coupling.

### Detailed Tasks

- [ ] Make subsystem-owned binding/install responsibilities explicit.
- [ ] Define how Component-bound services become visible in subsystem scope.
- [ ] Define ordering and deterministic conflict handling when multiple Components contribute bindings.
- [ ] Clarify subsystem lifecycle for binding installation versus runtime execution availability.
- [ ] Add minimal construction path used by sample projects and internal demos.

### Inputs

- `docs/design/cncf-architecture-overview.md`
- `src/main/scala/org/goldenport/cncf/subsystem/`
- `src/main/scala/org/goldenport/cncf/component/`

---

## CW-03: Executable Specifications for Wiring and Assembly

Status: PLANNED

### Objective

Protect the Component wiring model and subsystem construction path with
Executable Specifications.

### Detailed Tasks

- [ ] Add Given/When/Then spec for contract resolution through `PortApi`.
- [ ] Add spec for adapter realization through compatible `ExtensionPoint`.
- [ ] Add spec for named binding installation into `Component.Port`.
- [ ] Add subsystem-level spec for bound Component visibility after assembly.
- [ ] Add deterministic failure-path spec for missing binding / incompatible extension point.

---

## CW-04: Sample-Facing Wiring Rules and Adapter Registration Path

Status: PLANNED

### Objective

Document and validate the minimal sample-facing path for:

- adapter creation
- `ExtensionPoint` registration
- `withBinding(...)`
- `install_binding(...)`

### Detailed Tasks

- [ ] Freeze sample-facing naming and registration steps.
- [ ] Confirm the minimal wiring recipe against `cncf-samples`.
- [ ] Record any framework gaps discovered from sample-first usage.
- [ ] Close this phase only after sample-side wiring no longer requires redesign of framework abstractions.

### Inputs

- `/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/04/port-wiring-guide-for-samples-2026-04-07.md`

---

## Deferred / Next Phase Candidates

- Adapter lifecycle caching/refresh policy.
- Variation inspection/admin surface.
- Remote/multi-subsystem collaborator binding.

---

## Completion Check

Phase 11 is complete when:

- CW-01 through CW-04 are marked DONE.
- `phase-11.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
