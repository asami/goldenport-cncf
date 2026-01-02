# Consequence Rules

This document defines **mandatory and recommended usage rules**
for `Consequence` within SimpleModeling projects.

These rules exist to preserve semantic clarity and prevent
accidental mixing of computation meanings.

---

## MUST Rules

### MUST use `Consequence` as the unified result type

- Do NOT introduce parallel result types such as:
  - Try
  - Either
  - Validated
  - Custom Result / Outcome types
- All operation outcomes must be expressed as `Consequence[T]`.

---

### MUST use `zip*` APIs for validation / semantic interpretation

When interpreting requests, parameters, or domain inputs:

- MUST use:
  - zip
  - zipWith
  - zip3With
  - zipN
- MUST NOT use sequential `flatMap` / `for` style
  for semantic interpretation.

Rationale:
- Semantic interpretation requires error aggregation.
- Order-dependent evaluation hides missing or invalid inputs.

---

### MUST keep exception boundaries explicit

- MUST use:
  - Consequence { ... }
  - Consequence.run { ... }
- MUST NOT allow raw exceptions to leak across module boundaries.

All exceptions must be normalized into `Conclusion`.

---

### MUST NOT provide or rely on symbolic DSLs

- MUST NOT use symbolic operators such as:
  - |@|
- Applicative composition must be expressed explicitly
  via zip-based APIs.

---

### MUST NOT read undefined parameters via `take_*`

- take_* MUST NOT read values that are not defined in ParameterDefinition.
- If a parameter is not defined, take_* MUST yield Consequence.Failure.

---

## SHOULD Rules

### SHOULD prefer explicit API over implicit behavior

- Prefer named methods (zip3With) over chained transformations.
- Prefer structural composition over clever syntax.

---

### SHOULD treat `flatMap` as fail-fast semantics

- Use `flatMap` / `for` only when:
  - Sequential dependency is intended
  - Fail-fast behavior is correct
- Do NOT use `flatMap` where error aggregation is required.

---

### SHOULD document intent via API choice

API choice communicates meaning:

- zip*  → validation / interpretation
- flatMap → sequential control flow

Choose APIs so that readers (and AI tools) can infer intent
without additional comments.

---

### SHOULD use applicative style for multi-parameter interpretation

- When interpreting multiple parameters, use zip / zip3With to aggregate errors.

---

## MAY Rules

### MAY use cats interoperability when required

- MAY use `Monad[Consequence]` for integration with cats-based code.
- cats usage is optional and must not become a hard dependency
  of core logic.

---

### MAY use sequential style when fail-fast is required

- Use sequential style only when early exit is required by design.

## Summary Table

Semantic Intent              API to Use
---------------------------  ------------------------
Exception boundary           Consequence.apply, run
Validation / aggregation     zip*
Sequential (fail-fast)       map, flatMap
cats interoperability        Monad[Consequence]
Symbolic DSL                 Not allowed

---

## Final Note

These rules are intentionally strict.

They exist to ensure that **the meaning of computation
is explicit in code**, enabling:

- Correct reasoning
- Reliable testing
- AI-assisted development without ambiguity

---

## Consequence usage in CNCF

- Consequence is the only error propagation mechanism across:
  - Action
  - ActionCall
  - Component boundaries
- CNCF code must not call `.RAISE` in normal execution paths.
- Scenario specs may inspect Success / Failure,
  but should not depend on specific Conclusion details.
