# Notes: CNCF Documentation / Help Design

## Scope
This note records the design decisions and rationale for documentation
and help facilities in CNCF, as discussed up to this point.
The goal is to preserve semantic intent and architectural consistency,
not to finalize implementation details.

---

## 1. Documentation as Operations

In CNCF, documentation is treated as first-class runtime functionality,
not as static external artifacts.

Documentation is exposed via operations, resolved through the standard
CNCF component.service.operation model.

---

## 2. doc Service (Component-Level)

Each component exposes documentation via a `doc` service.

Canonical form:

    <component>.doc.reference
    <component>.doc.guide
    <component>.doc.cheatsheet
    <component>.doc.help
    <component>.doc.faq

Key properties:

- `doc` is a cross-cutting service
- Ownership is always the component itself
- No special-casing at the naming level
- External appearance is component-specific
- Internal implementation may be shared (generic DocService)

Example:

    hello.doc.help
    payment.doc.reference
    document.doc.guide

---

## 3. Meaning of reference / guide / cheatsheet / help / faq

The vocabulary is intentionally stable and semantically weighted.

- reference
  - Precise, exhaustive, contractual
  - API, operations, parameters, constraints
- guide
  - Goal-oriented, narrative
  - How to use, typical flows
- cheatsheet
  - Ultra-short, immediate answers
  - Minimal commands or examples
- help
  - Anchors and navigation
  - Entry point and signpost
- faq
  - Correction of common misunderstandings
  - Rationale and intent clarification

---

## 4. Definition of help (Key Decision)

Help is not a long explanation.

Help is defined as:

    An anchor and signpost to functions and documentation.

Intended audiences:

- First-time users
  - What is this component?
  - Where should I start?
- Returning users
  - What structure did this have?
  - Where are the main entry points?
- Frequent users
  - Are there uncommon or advanced features?
  - Where are special or debug capabilities?

Help should:

- Provide links (anchors) to:
  - guide
  - cheatsheet
  - reference
  - faq
  - major operations
- Avoid long prose
- Avoid full listings already covered by reference
- Serve as the natural first touchpoint

Help is conceptually a "navigation hub", not documentation content itself.

---

## 5. document Component

The `document` component is a normal component whose purpose is
documentation.

Important semantic clarification:

    document.doc.reference
    = the reference manual of the document component itself

This preserves full symmetry with other components and avoids
overloading "reference" to mean "system-wide manual".

System-wide conceptual explanations belong in guide-style content,
not reference.

---

## 6. System-Level Documentation

System-level or internal documentation is explicitly separated.

Two patterns are allowed:

1. document component (public, user-facing)
2. system component with a dedicated service

Canonical internal form:

    system.system_doc.reference
    system.system_doc.help
    system.system_doc.faq

Intended audience:

- Framework developers
- Operators
- Debugging and observability
- Internal execution model and policies

This separation avoids mixing public component semantics
with internal framework contracts.

---

## 7. Design Principles (Summary)

- Documentation is runtime-resolved, not static
- Naming must preserve ownership and semantic clarity
- help is an anchor hub, not a mini-manual
- reference retains its contractual meaning
- System-wide and internal documentation are explicitly separated
- No component is special-cased at the language level

---

## Status

- Semantic model: agreed
- Naming conventions: agreed
- Ownership rules: agreed
- Implementation details: intentionally deferred

Next steps may include:
- DocDescriptor schema
- Minimum guaranteed content for doc.help
- JSON representation for AI / tooling
- Resolution and injection rules
