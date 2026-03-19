# Phase 4 — State Machine Foundation Checklist

This document contains detailed task tracking and execution decisions
for Phase 4.

It complements the summary-level phase document (`phase-4.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-4.md`) holds summary only.
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

Phase 4 implementation proceeds in this order:

1. SM-01 (model and boundaries) must be fixed first.
2. SM-02 (runtime transition validation hook) is implemented next.
3. SM-04 (executable specs) is expanded in parallel with SM-02 once API stabilizes.
4. SM-03 (introspection projection) is finalized after runtime behavior is stable.

This order minimizes churn in projection/output contracts.

---

## SM-01: Canonical State Machine Model and Boundary Contracts

Status: DONE

### Objective

Define canonical state machine model and strict boundaries:

- CML DSL (declarative)
- AST (structured IR)
- core model (`org.goldenport.statemachine`, pure)
- CNCF execution integration (effectful)

### Detailed Tasks

- [x] Freeze layered architecture (`DSL -> AST -> core -> CNCF`).
- [x] Freeze guard expression strategy to MVEL + Ref.
- [x] Freeze action binding resolution rules (FQ first, scope, global unique, ambiguity failure).
- [x] Freeze determinism rules (priority asc, same-priority declaration order).
- [x] Freeze error semantics (guard failure is Failure; guard false is non-match).
- [x] Define canonical core types in `simplemodeling-lib` core state machine boundary (`StateMachine`, `State`, `Transition`, `Guard`, `Effect` boundary set).
- [x] Define CNCF-side resolver interfaces and adapter boundaries.
- [x] Publish normative design doc in `docs/design/` linked from this checklist.

### Journal Link

- `docs/journal/2026/03/statemachine-dsl-execution-design.md`
- `docs/design/statemachine-boundary-contract.md`

---

## SM-02: Runtime Transition Validation Hook

Status: DONE

### Objective

Integrate transition validation into existing CNCF execution path without bypassing
current execution semantics.

### Detailed Tasks

- [x] Identify integration point in Action/UnitOfWork flow where transition pre-check is deterministic.
- [x] Add transition planner call (`plan(state, event)`) before state mutation.
- [x] Enforce execution order: `exit -> transition action -> entry`.
- [x] Persist resulting state through existing entity runtime path.
- [x] Map transition failures to `Consequence`/observation taxonomy consistently.
- [x] Ensure no transition check runs outside the canonical runtime path.

---

## SM-03: Introspection Projection Surface

Status: DONE

### Objective

Expose state machine structure for CLI/meta projection while preserving model/runtime
separation.

### Detailed Tasks

- [x] Define projection shape (states, events, transitions, guards, priorities).
- [x] Add component/meta operation surface for state machine introspection.
- [x] Provide machine-readable output (JSON/YAML) compatible with existing formatter flow.
- [x] Add deterministic ordering rules for projection output.
- [x] Confirm backward compatibility with existing meta/help behaviors.

---

## SM-04: Executable Specifications

Status: DONE

### Objective

Add executable specifications for transition semantics and guard behavior.

### Detailed Tasks

- [x] Add Given/When/Then specs for basic transition success path.
- [x] Add specs for no-match behavior (`guard=false`).
- [x] Add specs for guard evaluation failure (`Failure` path).
- [x] Add specs for priority and same-priority declaration-order determinism.
- [x] Add specs for action binding ambiguity and missing binding failures.
- [x] Add regression specs for lifecycle compatibility path (temporary PostStatus/Aliveness coexistence).

---

## SM-05: Cozy/CNCF StateMachine Integration Gap Closure

Status: DONE

### Objective

Close the remaining integration gap between Cozy-generated state machine artifacts
and CNCF/core runtime consumption boundaries.

### Detailed Tasks

- [x] Complete Cozy-side state machine graph construction (`Modeler._statemachine`) beyond minimal placeholder behavior.
- [x] Validate complex guard coverage (composite guards, identifier guards, expression guards) against core model boundaries.
- [x] Validate multi-transition priority ordering scenarios, including same-priority deterministic declaration order.
- [x] Confirm generated transition rules map cleanly to core `org.goldenport.statemachine` structures without CNCF-side primitive redefinition.
- [x] Add cross-repo verification scenarios (cozy generation -> CNCF execution) for state machine transition paths.

### External Progress Reference

- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/statemachine-feature-update-2026-03-20.md`

---

## Deferred / Next Phase Candidates

- Policy-driven transition visibility and privilege checks.
- Event envelope linkage for transition lifecycle telemetry.
- Async transition orchestration and saga/compensation semantics.

---

## Completion Check

Phase 4 is complete when:

- SM-01 through SM-05 are marked DONE.
- `phase-4.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
