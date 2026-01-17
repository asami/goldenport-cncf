# Specification Linking and Rule Numbering Rules
status=draft

## Purpose

This document defines project-wide operational rules that apply on top of `docs/rules/spec-style.md`.
It is the applied operational extension that ensures Executable Specifications remain aligned with related design documents and normative specifications.

These rules apply when Executable Specifications are paired with design documents and normative specifications.

This framework references the canonical Executable Specification style defined in `docs/rules/spec-style.md`.

- linking from design documents to specifications,
- linking from specifications back to design documents,

- linking from design documents to specifications,
- linking from specifications back to design documents,
- assigning and maintaining rule numbers in specifications,
- and referencing rule numbers consistently across documents
  and Executable Specifications.

The goal is to ensure that specifications, design documents,
and Executable Specifications remain mutually traceable,
auditable, and verifiable by both humans and AI.

These rules are structural and normative.
They do not define system behavior, but they constrain
how behavior-defining documents are written and connected.

---

## 1. Design Document → Specification Linking Rules

### Rule 1.1 Authoritative Specification Reference

When a design document relies on behavior defined in a specification:

- The design document MUST include an explicit reference
  to the authoritative specification.
- The reference MUST identify the specification file path.

Example (normative):

- “Path resolution and CanonicalPath construction are defined in
  docs/spec/path-resolution.md.”

### Rule 1.2 No Rule Duplication

Design documents MUST NOT:

- restate specification rules,
- paraphrase rule logic,
- or redefine behavior already specified elsewhere.

Design documents MAY:

- describe intent, rationale, or architectural motivation,
- but MUST defer all normative behavior to the specification.

---

## 2. Specification → Design Document Linking Rules

### Rule 2.1 Design Context Reference

A specification SHOULD include references to relevant design documents
that provide architectural or conceptual context.

Such references MUST:

- be clearly marked as non-normative,
- not be required to understand or apply the specification rules.

Example:

- “Architectural context is described in execution-model.md.”

### Rule 2.2 One-Way Authority

- Specifications are authoritative for behavior.
- Design documents provide structure, motivation, and context.

A specification MUST NOT depend on a design document
to define its normative rules.

---

## 3. Rule Numbering Scheme

### Rule 3.1 Stable Rule Identifiers

Each normative rule in a specification MUST have a stable identifier.

The identifier format is:

- `R<n>` for rules
- `E<n>` for examples

Where `<n>` is a sequential integer local to the specification.

Example identifiers:

- R1, R2, R3 …
- E1, E2, E3 …

### Rule 3.2 No Semantic Encoding in Numbers

Rule and example numbers MUST NOT encode meaning, category,
priority, or hierarchy.

All semantics are carried by rule text, not by numbering.

---

## 4. Rule Number Assignment Rules

### Rule 4.1 Assignment Order

Rule numbers MUST be assigned:

- in the order the rules are introduced in the specification,
- and MUST remain stable once published.

Reordering sections or restructuring text
does NOT permit renumbering.

### Rule 4.2 Modification Policy

- Modifying the content of a rule MUST keep the same rule number.
- Removing a rule MUST NOT cause renumbering of other rules.
- Deprecated rules MAY remain with explicit deprecation notes.

---

## 5. Rule Number Usage in Specifications

### Rule 5.1 Explicit Rule Labels

Each rule section in a specification MUST explicitly display
its rule number.

Example:

- “## 4. Omission Rules (R2)”

### Rule 5.2 Cross-Rule References

When a rule refers to another rule:

- the referenced rule number MUST be stated explicitly.

Example:

- “This rule is applied after normalization (R5).”

Implicit references such as “the previous rule” are forbidden.

---

## 6. Rule Number Usage in Executable Specifications

### Rule 6.1 Example Identification

Each executable test corresponding to a specification example MUST:

- reference the specification file path,
- reference the Example identifier (E<n>).

Example (comment form):

- “Spec: docs/spec/path-resolution.md”
- “Example: E3 OpenAPI Path with Omitted Operation”

### Rule 6.2 Rule Traceability (Optional)

Executable tests MAY additionally record
which rule identifiers (R<n>) are exercised.

This information is non-normative but aids
AI-assisted review and impact analysis.

---

## 7. Change Impact Rules

### Rule 7.1 Specification Change Impact

When a rule (R<n>) or example (E<n>) is modified:

- all references to that identifier MUST be reviewed,
- corresponding executable tests MUST be updated if affected.

### Rule 7.2 Identifier Stability Contract

Rule and example identifiers are stable contracts.

Breaking changes MUST be expressed by:

- modifying rule content,
- or adding new rules with new identifiers,

not by renumbering existing rules or examples.

---

## Positioning

These rules define structural conventions,
not implementation behavior.

They exist to ensure:

- bidirectional traceability between specifications,
  design documents, and Executable Specifications,
- consistent interpretation by humans and AI,
- and long-term maintainability of
  specification-driven and AI-assisted development.

They build directly on the `Executable Specification` model defined in `docs/rules/spec-style.md` and describe how that model links to broader documentation and operational contexts.
