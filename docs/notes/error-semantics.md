status = draft
scope = CNCF runtime error semantics

# Error Semantics (CNCF Runtime)

## 1. Purpose

This document defines how CNCF runtime interprets and projects
core-level error semantics to external interfaces.

This note corresponds to **Phase 2.5: Error Semantics Consolidation**
in the CNCF development strategy.

The goal is to finalize runtime-visible error behavior
before entering CML and CRUD-oriented development.

---

## 2. Scope and Non-Scope

### In Scope
- Interpretation of core-level results:
  - `Consequence`
  - `Conclusion`
  - `Observation`
- CNCF-level error projections to:
  - CLI exit codes
  - HTTP status codes
  - client-visible error representations
- Consistent behavior across:
  - command
  - client
  - server-emulator
  - server

### Out of Scope
- CML modeling
- CRUD generation
- Workflow or job orchestration
- Persistence-layer error handling
- Redesign of core error semantics

---

## 3. Role Separation (Core vs CNCF)

### Core Responsibilities
Core defines **error meaning**, not presentation.

Core provides:
- Semantic error containers (`Conclusion`)
- Failure vs defect distinction
- Cause taxonomy (`Cause`, `CauseKind`)
- Observation data model

Core does NOT define:
- CLI exit codes
- HTTP status codes
- Logging or emission behavior
- Client-specific error formats

---

### CNCF Responsibilities
CNCF runtime is responsible for **projecting**
core semantics to external interfaces.

This includes:
- Mapping errors to CLI exit codes
- Mapping errors to HTTP status codes
- Defining stable, client-visible error structures
- Ensuring consistent behavior across all execution modes

---

## 4. Error Projection Concepts

### 4.1 Unified Execution Model

All CNCF execution paths share the same semantic flow:

```
core execution
  → Consequence
    → Conclusion
      → CNCF projection
```

No execution path is allowed to bypass this model.

---

### 4.2 Failure vs Defect

CNCF respects the core-level distinction:

- **Domain Failure**
  - Expected failure within domain rules
  - Projected as client-visible errors
- **Defect**
  - Unexpected or internal failure
  - Projected as internal errors

CNCF must not reinterpret a defect as a domain failure.

---

## 5. CLI Exit Code Projection (Conceptual)

CNCF defines exit code behavior at runtime level.

- Exit codes are NOT defined in core
- Exit codes are stable and machine-consumable
- Exit code meaning is derived from `Conclusion`

Exact exit code values are defined later in this phase
and frozen before CML development.

---

## 6. HTTP Status Projection (Conceptual)

CNCF defines HTTP status mapping based on error semantics.

- HTTP status is derived from `Conclusion`
- Minimal status set is preferred
- HTTP is treated as a projection, not a semantic layer

Exact mappings are defined later in this phase.

---

## 7. Client-Visible Error Representation

CNCF defines a stable client-visible error structure
based on `Conclusion` and `Observation`.

- Human-readable message
- Machine-readable error code
- Optional contextual details

Clients must rely on error codes, not messages.

---

## 8. Phase Boundary Declaration

This document declares a boundary.

After Phase 2.5:
- Error semantics and projections are frozen
- CML and CRUD layers must conform to this contract
- No runtime-visible error redesign is allowed

---

END OF DOCUMENT
