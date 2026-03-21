# Phase 8 — CML Operation Grammar Introduction Checklist

This document contains detailed task tracking and execution decisions
for Phase 8.

It complements the summary-level phase document (`phase-8.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-8.md`) holds summary only.
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

Phase 8 implementation proceeds in this order:

1. OP-01 (grammar + semantics) must be fixed first.
2. OP-02 (AST/model extension) is implemented on top of fixed grammar contract.
3. OP-03 (generator propagation) is implemented after AST shape stabilizes.
4. OP-04 (runtime/projection integration) is applied after generated metadata path is stable.
5. OP-05 (executable specs) is finalized last as closure criteria.

This order minimizes churn across parser/modeler/generator/runtime boundaries.

---

## OP-01: CML Operation Grammar and Semantics

Status: DONE

### Objective

Define and freeze first-class `operation` grammar and semantic contract in CML.

### Detailed Tasks

- [x] Define grammar shape (syntax/sections) for operation declaration.
- [x] Define operation kind contract (`command` / `query`).
- [x] Define canonical input contract (`single input object`) and convenience form (`parameter` sugar).
- [x] Define Command/Query value typing model for operation input.
- [x] Define normalization rule:
  - canonical form `(input)`
  - convenience form `(parameter...)`
  - dual definition `(input + parameter)` consistency
- [x] Define validation rules:
  - Operation.Type and input value type must match
  - dual definition field consistency check
- [x] Define parameter and response shape contract.
- [x] Define naming and deterministic ordering rules.
- [x] Add canonical examples and invalid-case constraints.

---

## OP-02: AST/Model Extension

Status: DONE

### Objective

Implement AST/model extension for operation definitions.

### Detailed Tasks

- [x] Add operation definition model in CML AST.
- [x] Add normalized operation model (`inputType` canonicalization).
- [x] Add Value definition model extension (`CommandDef` / `QueryDef`).
- [x] Connect operation definitions to existing entity/component model structures.
- [x] Ensure parser output is deterministic for equivalent inputs.
- [x] Add model-level validation for invalid operation declarations.

---

## OP-03: Generator Propagation (Cozy -> SimpleModeler -> CNCF)

Status: DONE

### Objective

Implement generation propagation path for operation metadata.

### Detailed Tasks

- [x] Cozy emits operation metadata from CML source.
- [x] Parameter-only operation form auto-generates canonical input value model.
- [x] SimpleModeler propagates metadata through Scala model transformation.
- [x] CNCF generated component surface receives operation metadata hooks.
- [x] Cross-repo integration contract is documented and stable.

---

## OP-04: Runtime/Projection Integration

Status: DONE

### Objective

Align runtime and projection/meta surfaces with generated operation metadata.

### Detailed Tasks

- [x] Align runtime operation resolution with generated operation metadata.
- [x] Map operation type to runtime execution semantics:
  - Command: Job async default
  - Query: synchronous / Ephemeral Job path
- [x] Align `meta.*` and projection outputs with operation declarations.
- [x] Ensure deterministic operation visibility across help/schema/openapi surfaces.
- [x] Remove ambiguous or duplicate runtime exposure paths if present.

---

## OP-05: Executable Specifications (Operation Grammar Path)

Status: DONE

### Objective

Add executable specifications to close Phase 8 with behavior-level guarantees.

### Detailed Tasks

- [x] Add grammar parse specs for valid/invalid operation declarations.
- [x] Add AST/model mapping specs for operation definitions.
- [x] Add normalization specs for:
  - canonical form
  - parameter convenience form
  - dual-definition consistency
- [x] Add type-validation specs (`Operation.Type` vs `Command/Query Value`).
- [x] Add generation propagation specs across Cozy/SimpleModeler/CNCF path.
- [x] Add runtime/projection visibility specs for generated operations.
- [x] Add regression specs for command/query operation semantics.

---

## Deferred / Next Phase Candidates

- Advanced operation overloading/polymorphic dispatch.
- External operation schema federation.
- Operation-level policy governance expansion.

---

## Completion Check

Phase 8 is complete when:

- OP-01 through OP-05 are marked DONE.
- `phase-8.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
