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

Status: DONE

### Objective

Stabilize the wiring-time model for:

- `Port` API resolution
- `VariationPoint` current/inject semantics
- `ExtensionPoint` adapter realization
- binding installation into `Component.Port`

### Detailed Tasks

- [x] Remove remaining assumptions from the old runtime-shaped port skeleton.
- [x] Confirm `Port` metamodel shape (`api`, `spi`, `variation`) as the canonical framework baseline.
- [x] Confirm `ExtensionPoint` as adapter/provider realization boundary.
- [x] Confirm `Binding.install(...)` and named binding registry behavior.
- [x] Clarify legacy compatibility behavior for single default binding.
- [x] Add or update executable specs protecting the baseline semantics.

Verification:

- `sbt --batch "testOnly org.goldenport.cncf.component.PortBindingSpec"`

### Inputs

- `docs/journal/2026/04/port-extension-point-variation-point-redesign-2026-04-07.md`
- `src/main/scala/org/goldenport/cncf/component/Port.scala`
- `src/main/scala/org/goldenport/cncf/component/Component.scala`

---

## CW-02: Subsystem Construction via Bound Components

Status: DONE

### Objective

Define subsystem construction so that subsystem assembly is performed
through bound Components rather than ad hoc factory coupling.

### Detailed Tasks

- [x] Make subsystem-owned binding/install responsibilities explicit.
- [x] Define how Component-bound services become visible in subsystem scope.
- [x] Define ordering and deterministic conflict handling when multiple Components contribute bindings.
- [x] Clarify subsystem lifecycle for binding installation versus runtime execution availability.
- [x] Add minimal construction path used by sample projects and internal demos.

Decision:

- `GenericSubsystemFactory` resolves subsystem descriptors from explicit
  descriptor configuration or subsystem name, discovers descriptor-bound
  Components from repositories, adds default built-ins unless excluded, and
  collapses duplicate Component names before subsystem installation.
- `Subsystem.add` owns installation into subsystem scope by injecting
  Component context, adding Components to `ComponentSpace`, and rebuilding
  `OperationResolver`.
- Component-owned `install_binding(...)` remains inside the Component
  port/binding lifecycle. Runtime invocation uses the rebuilt resolver and does
  not reinstall bindings during operation execution.
- Duplicate Component names are resolved by `AssemblyReport.selectPreferred`;
  dropped candidates are retained as assembly warnings for admin and
  diagnostic views.

### Inputs

- `docs/design/cncf-architecture-overview.md`
- `src/main/scala/org/goldenport/cncf/subsystem/`
- `src/main/scala/org/goldenport/cncf/component/`

Verification:

- `sbt --batch "testOnly org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec"`

---

## CW-03: Executable Specifications for Wiring and Assembly

Status: DONE

### Objective

Protect the Component wiring model and subsystem construction path with
Executable Specifications.

### Detailed Tasks

- [x] Add Given/When/Then spec for contract resolution through `PortApi`.
- [x] Add spec for adapter realization through compatible `ExtensionPoint`.
- [x] Add spec for named binding installation into `Component.Port`.
- [x] Add subsystem-level spec for bound Component visibility after assembly.
- [x] Add deterministic failure-path spec for missing binding / incompatible extension point.

Verification:

- `sbt --batch "testOnly org.goldenport.cncf.component.PortBindingSpec org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec"`

---

## CW-04: Sample-Facing Wiring Rules and Adapter Registration Path

Status: DONE

### Objective

Document and validate the minimal sample-facing path for:

- adapter creation
- `ExtensionPoint` registration
- `withBinding(...)`
- `install_binding(...)`

### Detailed Tasks

- [x] Freeze sample-facing naming and registration steps.
- [x] Confirm the minimal wiring recipe against `cncf-samples`.
- [x] Record any framework gaps discovered from sample-first usage.
- [x] Close this phase only after sample-side wiring no longer requires redesign of framework abstractions.

Decision:

- Sample-facing wiring is fixed in `docs/design/component-port-wiring.md`.
- `Port` remains a wiring-time model. Runtime execution remains on
  `OperationCall` / action execution.
- New sample code should use stable named bindings through
  `withBinding(name, binding)` and `install_binding(name, req)`.
- No additional framework abstraction redesign is required for the Phase 11
  sample-facing path.

### Inputs

- `/Users/asami/src/dev2026/cncf-samples/docs/journal/2026/04/port-wiring-guide-for-samples-2026-04-07.md`
- `docs/design/component-port-wiring.md`

Verification:

- `sbt --batch "testOnly org.goldenport.cncf.component.PortBindingSpec org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec"`

---

## Deferred / Next Phase Candidates

- Adapter lifecycle caching/refresh policy.
- Variation inspection/admin surface.
- Remote/multi-subsystem collaborator binding.

---

## Completion Check

Phase 11 is complete when:

- [x] CW-01 through CW-04 are marked DONE.
- [x] `phase-11.md` summary checkboxes are aligned.
- [x] No item remains ACTIVE or SUSPENDED.
