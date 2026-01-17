# Path Resolution Specification
status=draft
phase=2.8

## 1. Purpose and Scope

This specification defines the general path resolution rules used by the system
to derive a CanonicalPath from user-facing inputs such as CLI arguments,
HTTP paths, and script invocations.

The purpose of this specification is to:

- eliminate hard-coded alias handling from implementation,
- replace such behavior with explicit, general, and structural rules,
- ensure that path resolution behavior can be understood and verified
  by reading this specification alone.

This document is normative for Phase 2.8 with respect to path resolution behavior.
Any behavior not explainable by this specification MUST NOT be implemented.

---

## 2. Canonical Path Definition (R1)

A CanonicalPath is a fully resolved internal identifier consisting of exactly
three elements:

- component
- service
- operation

All three elements MUST be explicitly determined before control is passed
to the Resolver Core.

The Resolver Core MUST accept CanonicalPath only.
Any omission handling, normalization, or interpretation MUST be completed
beforehand.

---

## 3. Normalization Rules (R5)

Before any semantic resolution is applied, input paths are normalized as follows:

- case differences are normalized (inputs are treated case-insensitively),
- redundant or empty delimiters are removed,
- CLI, HTTP, and Script representations are unified.

Normalization is purely syntactic and MUST NOT introduce semantic assumptions.

---

## 4. Omission Rules (R2)

### General Rule

For non-builtin components only:

If a component satisfies both conditions:

- it contains exactly one service, and
- that service contains exactly one operation,

then the operation name MAY be omitted in the input path.

The omitted operation is resolved by structural uniqueness.

### Characteristics

- This is a structural rule, not an alias.
- No literal string is treated specially.
- Resolution depends solely on component structure.

---

## 5. Builtin Component Handling (R3)

Builtin components do NOT automatically inherit omission rules.

For builtin components:

- applicability of omission rules MUST be explicitly defined in specification,
- absence of such definition means omission is NOT allowed.

This rule prevents implicit special cases from being introduced by implementation.

---

## 6. Suffix Resolution Rules (R4)

A suffix attached to the end of a path:

- specifies representation or output format,
- does NOT affect CanonicalPath structure.

Representative suffixes include, but are not limited to:

- .json
- .html

Suffix resolution is independent from component, service, and operation resolution.

---

## 7. Resolution Order (R6)

Path resolution MUST follow the fixed order below:

1. Normalization (R5)
2. Omission rules (R2, R3)
3. Suffix resolution (R4)
4. CanonicalPath construction (R1)
5. Resolver Core execution

This order is mandatory to ensure deterministic behavior.

---

## 8. Error Handling (R7)

If CanonicalPath cannot be constructed after applying all rules:

- the request MUST fail explicitly,
- implicit fallback, heuristic completion, or alias recovery is prohibited.

The resulting error MUST be classified as NotFound or BadRequest.

---

## 9. Checkable Examples (R8)

The following examples demonstrate path resolution behavior using only
the rules defined in this specification.

Each example explicitly lists the input, applied rules, and result.

---

### E1. Single-operation Component Omission

Input:
  script

Applied Rules:
  R5 → R2 → R1

Result:
  CanonicalPath(script, default, run)

Notes:
  Operation is omitted due to structural uniqueness.

---

### E2. Service-specified Omission

Input:
  script/default

Applied Rules:
  R5 → R2 → R1

Result:
  CanonicalPath(script, default, run)

---

### E3. OpenAPI Path with Omitted Operation

Input:
  spec/openapi

Applied Rules:
  R5 → R2 → R1

Result:
  CanonicalPath(spec, openapi, export)

---

### E4. OpenAPI JSON Representation

Input:
  spec/openapi.json

Applied Rules:
  R5 → R2 → R4 → R1

Result:
  CanonicalPath(spec, openapi, export)

Notes:
  Suffix affects representation only.

---

### E5. Builtin Component without Omission

Input:
  spec

Applied Rules:
  R5 → R3 → R7

Result:
  Error (BadRequest)

Notes:
  Omission is not permitted for builtin components unless explicitly specified.

---

### E6. Invalid Omission

Input:
  foo

Applied Rules:
  R5 → R2 → R7

Result:
  Error (NotFound)

---

## 10. Positioning

This specification replaces hard-coded alias handling with general,
structural resolution rules.

Any behavior not explainable by this document MUST NOT exist
in Phase 2.8 implementation.

## Executable Specification Reference

This specification is accompanied by an Executable Specification.

Each example defined in Section 9 (E1–E6) has a corresponding
executable test case in the test suite.

The authoritative executable validation for this specification is:
  - PathResolutionSpec

Any modification to the rules or examples in this document
MUST be reflected in the corresponding executable tests.
Likewise, any change to the executable tests that affects
observable behavior MUST be reflected in this specification.

This bidirectional reference enables automated consistency
checking by both humans and AI.
