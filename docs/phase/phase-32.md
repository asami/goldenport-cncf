# Phase 32 - Rule Engine and Inference Runtime

Stage Status:
- Current status: CLOSED
- Owner: Phase 32 Rule Engine and Inference Runtime
- Update rule: Update this block and `phase-32-checklist.md` whenever a stable
  work-item state changes. Close the phase only when every in-scope checklist
  item is complete or explicitly relocated.

status = closed

## 1. Purpose

Phase 32 implements strategy item `9.27 Rule Engine and Inference Runtime`.
It establishes CNCF's engine-neutral rule model and SPI for domain calculation,
constraint, decision-table, derivation, production, and inference rules.

The first drivers are consumption-tax calculation and product-price tables.
The result is reusable CNCF runtime infrastructure, not a component-local rule
library or a concrete JESS-compatible engine.

## 2. Scope

- Define RuleSet, Rule, Fact, WorkingMemory, Agenda, evaluation, firing, and
  explanation models.
- Define an engine-neutral RuleEngine and InferenceEngine SPI.
- Add deterministic built-in decision-table, calculation, constraint, and
  forward-derivation evaluation.
- Route admitted production actions through ActionCall, Event, Job, and Task
  boundaries.
- Add restricted Record-shaped RuleSet descriptors, read-only projection, and
  sanitized diagnostics.

## 3. Boundaries

- CNCF core must not depend on JESS, RETE, Drools, or another rule provider.
- Rule evaluation is separate from action firing and direct persistent mutation.
- Rule actions must not bypass authorization, UnitOfWork, Event, Job, or
  observability chokepoints.
- Business-process engines, BPMN, human tasks, rule-authoring UI, RETE
  optimization, external provider integrations, and untrusted-code sandboxing
  remain deferred.

## 4. Active Work Stack

- A (DONE): RE-01 - Freeze the model, SPI, and execution-boundary contract.
- B (DONE): RE-02 - Implement foundational Rule/Fact/WorkingMemory models.
- C (DONE): RE-03 - Implement deterministic decision-table evaluation.
- D (DONE): RE-04 - Add calculation, constraint, and derivation rules.
- E (DONE): RE-05 - Add RuleEngine and InferenceEngine SPI resolution.
- F (DONE): RE-06 - Admit production actions through CNCF execution paths.
- G (DONE): RE-07 - Add descriptor loading and read-only projections.
- H (DONE): RE-08 - Add CallTree, observability, and diagnostics.
- I (DONE): RE-09 - Complete domain-driver, developer documentation, and
  regression evidence.
- J (DONE): RE-10 - Verify and close Phase 32.

## 5. Development Items

- [x] RE-01: Freeze the model, SPI, and execution-boundary contract.
- [x] RE-02: Implement foundational Rule/Fact/WorkingMemory models.
- [x] RE-03: Implement deterministic decision-table evaluation.
- [x] RE-04: Add calculation, constraint, and derivation rules.
- [x] RE-05: Add RuleEngine and InferenceEngine SPI resolution.
- [x] RE-06: Admit production actions through CNCF execution paths.
- [x] RE-07: Add descriptor loading and read-only projections.
- [x] RE-08: Add CallTree, observability, and diagnostics.
- [x] RE-09: Complete domain-driver, developer documentation, and regression
  evidence.
- [x] RE-10: Verify and close Phase 32.

Detailed task tracking and acceptance evidence are in
`phase-32-checklist.md`.

## 6. Completion Conditions

Phase 32 closes only when:

- the rule model and SPI are documented and covered by executable specs;
- identical normalized facts and RuleSet versions yield deterministic results;
- price-table and tax-calculation drivers demonstrate decision-table and
  calculation semantics;
- constraint failures use structured `Conclusion` behavior;
- rule-produced actions enter canonical ActionCall/Event/Job paths;
- projections and diagnostics are explainable and payload-safe;
- no concrete rule engine becomes a CNCF core dependency;
- deferred process-engine and external-provider scope remains explicit.
