# Phase 8 — CML Operation Grammar Introduction

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 8.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Introduce `operation` grammar into CML as a first-class construct.
- Define canonical semantic contract for operation:
  - name
  - input type (canonical single input object)
  - parameter convenience form (syntactic sugar)
  - output shape
  - execution kind (command/query)
- Define value typing model for operation input:
  - `Command` Value
  - `Query` Value
- Define normalization rule:
  - canonical form: `Operation(input: Type)`
  - convenience form: `Operation(parameter...)`
  - both normalize to canonical input model
- Define validation rules:
  - operation type and input value type must match (`Command`/`Query`)
  - dual definition (`Input + Parameter`) must be field-consistent
- Extend CML AST/model and generator pipeline for operation metadata propagation.
- Integrate generated operation metadata into CNCF runtime path.
- Add executable specifications for grammar parse, AST mapping, generation, and runtime visibility.

## 3. Non-Goals

- No redesign of existing Aggregate/View semantic contracts.
- No workflow/orchestration feature expansion.
- No security model redesign.
- No broad CLI UX redesign beyond operation grammar integration requirements.

## 4. Current Work Stack

- A (DONE): Define operation grammar syntax and semantic contract.
- B (DONE): Implement AST/model extensions for operation definition.
- C (DONE): Implement generator propagation (Cozy -> SimpleModeler -> CNCF).
- D (DONE): Integrate runtime exposure and projection/meta visibility.
- E (ACTIVE): Add executable specifications and regression coverage.

Resume hint:
- Finalize runtime execution semantics and close executable specification gaps.

## 5. Development Items

- [x] OP-01: Define and freeze CML operation grammar and semantic contract.
- [x] OP-02: Implement CML AST/model extension for operation definitions.
- [x] OP-03: Implement generation propagation path across Cozy/SimpleModeler/CNCF.
- [x] OP-04: Align runtime/meta/projection exposure with generated operation metadata.
- [ ] OP-05: Add executable specifications for parse->AST->generation->runtime path.

## 6.1 Latest Verification Snapshot (2026-03-22)

- Cozy:
  - `94da76c feat(modeler): implement operation grammar normalization and emission`
- SimpleModeler:
  - `8faf81c feat(component): propagate operation metadata to generated DomainComponent`
- CNCF:
  - `3ac3d96 feat(projection): expose CML operation metadata in component meta surfaces`
- Focused CNCF verification:
  - `sbt --batch "testOnly org.goldenport.cncf.projection.AggregateViewProjectionAlignmentSpec"`
  - result: passed (`2 succeeded, 0 failed`)
  - `sbt --batch "testOnly org.goldenport.cncf.cli.CommandExecuteComponentSpec"`
  - result: passed (`42 succeeded, 0 failed`)
  - `sbt --batch "testOnly org.goldenport.cncf.job.SCENARIO.JobLifecycleScenarioSpec"`
  - result: passed (`3 succeeded, 0 failed`)

## 6. Inputs from Previous Phases

- Phase 7 Aggregate/View model and generation baselines are prerequisites.
- Phase 6 Job/Task execution model remains execution boundary baseline.

## 7. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-7.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-7-checklist.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/strategy/cncf-development-strategy.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-arg-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-input-command-query-value-handoff.md`
