# Phase 9 — Component/Subsystem Grammar and CAR/SAR Packaging Checklist

This document contains detailed task tracking and execution decisions
for Phase 9.

It complements the summary-level phase document (`phase-9.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-9.md`) holds summary only.
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

Phase 9 implementation proceeds in this order:

1. CS-01: freeze grammar contract.
2. CS-02: implement Cozy model/generator integration.
3. PK-01: freeze CAR/SAR artifact contract.
4. PK-02: implement packaging in sbt-cozy.
5. RT-01: align CNCF runtime loading/visibility.
6. EX-01: close executable specifications and phase docs.

This order minimizes churn across grammar/model/packaging/runtime boundaries.

---

## Implementation Repositories

- Cozy: `/Users/asami/src/dev2025/cozy`
- CNCF: `/Users/asami/src/dev2025/cloud-native-component-framework`
- sbt plugin: `/Users/asami/src/dev2026/sbt-cozy`

---

## CS-01: CML Component/Subsystem Grammar Contract

Status: DONE

### Objective

Freeze grammar contract for Component/Componentlet/ExtensionPoint/Subsystem.

### Detailed Tasks

- [x] Confirm grammar surface for `Component`, `Componentlet`, `ExtensionPoint`, `Subsystem`.
- [x] Define deterministic naming/order rules.
- [x] Define coordinate contract for component dependency declaration.
- [x] Define extension binding syntax and resolution precedence points.
- [x] Define invalid-case constraints and error expectations.
- [x] Record finalized grammar examples.

---

## CS-02: Cozy Grammar Implementation

Status: DONE

### Objective

Implement grammar contract in Cozy parser/model/generator path.

### Detailed Tasks

- [x] Implement parser acceptance for new grammar constructs.
- [x] Extend AST/model structures for Component/Subsystem declarations.
- [x] Emit metadata required by downstream packaging/runtime layers.
- [x] Add deterministic output validation for equivalent definitions.
- [x] Add focused specs for valid/invalid grammar cases.

---

## PK-01: CAR/SAR Model Contract

Status: DONE

### Objective

Freeze CAR/SAR artifact model and extension/config precedence.

### Detailed Tasks

- [x] Fix CAR structure contract (`component/`, `lib/`, `spi/`, `config/`, `docs/`, `meta/`).
- [x] Fix SAR structure contract (`subsystem/`, `extension/`, `config/`, `meta/`).
- [x] Fix extension precedence (`SAR > CAR`) and config merge precedence (`SAR > CAR`).
- [x] Define manifest minimum fields and compatibility constraints.
- [x] Define runtime load sequence and isolation boundaries.

---

## PK-02: sbt-cozy Packaging Implementation

Status: DONE

### Objective

Implement CAR/SAR packaging flow via `sbt-cozy`.

### Detailed Tasks

- [x] Add packaging tasks/settings for CAR build output.
- [x] Add packaging tasks/settings for SAR build output.
- [x] Ensure generated CML/Scala outputs are wired into packaging path.
- [x] Generate manifest and metadata files per contract.
- [x] Add plugin-level tests or scripted verification for packaging outputs.
- [x] Document usage in plugin README with command examples.

---

## RT-01: CNCF Runtime Alignment for CAR/SAR

Status: DONE

### Objective

Align runtime loading and introspection with packaged CAR/SAR artifacts.

### Detailed Tasks

- [x] Define runtime intake path for CAR/SAR artifacts.
- [x] Align component/subsystem resolution path with packaging metadata.
- [x] Expose loaded artifact metadata via `meta/help/describe/schema` where required.
- [x] Add deterministic failure behavior for invalid/missing artifact metadata.
- [x] Ensure no competing parallel loading API is introduced.

---

## EX-01: Executable Specs and Phase Closure

Status: DONE

### Objective

Close Phase 9 with executable specification coverage and document alignment.

### Detailed Tasks

- [x] Add parse/model specs for Component/Subsystem grammar.
- [x] Add packaging specs for CAR/SAR structure and precedence rules.
- [x] Add runtime integration specs for packaged artifact loading/visibility.
- [x] Run focused suites across Cozy/sbt-cozy/CNCF and record commands/results.
- [x] Update `phase-9.md` and `phase-9-checklist.md` consistently.
- [x] Mark phase as closed only after all items are DONE.

---

## Deferred / Next Phase Candidates

- Textus domain implementation (`textus-user-account`, `textus-identity`) as next phase.
- External repository/distribution integration for CAR/SAR artifact publication.
- Advanced extension governance/policy controls.

---

## Completion Check

Phase 9 is complete when:

- CS-01 through EX-01 are marked DONE.
- `phase-9.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
