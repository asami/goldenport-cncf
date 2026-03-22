# Phase 9 — Component/Subsystem Grammar and CAR/SAR Packaging

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 9.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Define and freeze CML grammar for:
  - Component
  - Componentlet
  - ExtensionPoint
  - Subsystem
- Establish CAR/SAR artifact model and packaging contract.
- Implement packaging path with `sbt-cozy` plugin.
- Align CNCF runtime loading boundaries with CAR/SAR model.
- Add executable specifications for parse/model/package/runtime visibility path.

## 3. Non-Goals

- No redesign of Job/Event/StateMachine baselines.
- No new identity/account domain implementation in this phase.
- No broad CLI UX redesign outside grammar/packaging integration needs.
- No speculative architecture changes outside CAR/SAR and Component/Subsystem boundary.

## 4. Current Work Stack

- A (DONE): Freeze Component/Subsystem CML grammar contract.
- B (DONE): Implement Cozy parser/model updates for grammar.
- C (DONE): Define and implement CAR/SAR packaging model integration.
- D (DONE): Implement `sbt-cozy` packaging flow for CAR/SAR artifacts.
- E (ACTIVE): Align CNCF runtime/projection loading path for packaged artifacts.
- F (PLANNED): Add executable specs and close phase documents.

Resume hint:
- Complete runtime loading/visibility alignment for CAR/SAR and close executable specs.

## 5. Development Items

- [x] CS-01: Define/freeze Component/Subsystem/Componentlet/ExtensionPoint CML grammar.
- [x] CS-02: Implement Cozy parser/model/generator updates for grammar contract.
- [x] PK-01: Define/freeze CAR/SAR model contract (manifest, structure, precedence).
- [x] PK-02: Implement CAR/SAR packaging in `sbt-cozy`.
- [ ] RT-01: Align CNCF runtime loading and visibility with CAR/SAR packaging contract.
- [ ] EX-01: Add executable specs and finalize Phase 9 closure.

## 6. Inputs from Previous Phases

- Phase 8 operation grammar and metadata propagation baseline.
- Existing component runtime execution baseline (Phase 3.x to Phase 8).
- Existing plugin-based code generation baseline via Cozy/SimpleModeler.

## 6.1 Implementation Repositories

- Cozy:
  - `/Users/asami/src/dev2025/cozy`
- CNCF:
  - `/Users/asami/src/dev2025/cloud-native-component-framework`
- sbt plugin:
  - `/Users/asami/src/dev2026/sbt-cozy`

## 7. References

- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-subsystem-grammar-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/car-sar-model-note.md`
- `/Users/asami/src/dev2026/sbt-cozy/README.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8.md`

## 8. Latest Verification Snapshot (2026-03-22)

- Cozy:
  - `c322cf0 Integrate component-centric grammar and default component fallback`
  - focused test:
    - `sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"`
    - result: passed (`22 succeeded, 0 failed`)
- sbt-cozy:
  - focused test:
    - `sbt --batch test`
    - result: passed (`10 succeeded, 0 failed`)
- CNCF:
  - RT-01 remains open; CAR/SAR runtime intake and visibility alignment is the current active item.
