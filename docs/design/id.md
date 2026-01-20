# ID Design — Canonical, Self-Describing Identifiers

status: decided

Status (as of 2025-12-31):
- CanonicalId is provided by goldenport core
- CNCF treats CanonicalId as opaque and uses it for correlation only
- CNCF does not generate CanonicalId

This document defines the canonical ID design for this system.
It records an architectural decision that has already been reached
through prior discussions and exists to prevent accidental reintroduction
of known anti-patterns.

---

## Core Principle

**IDs are opaque to program logic, but self-describing to humans and observability tools.**

IDs must support:
- observability
- debugging
- log correlation
- AI-assisted inspection

Observability identifiers that reach CLI adapters are rendered through the Presentable stdout/stderr contract locked in `docs/notes/phase-2.8-infrastructure-hygiene.md#purpose-aware-string-rendering-candidate` (see A-3 for the Phase 2.8 policy).

while remaining **non-semantic for program logic**.

---

## Anti-Patterns: Semantic ID / Smart ID

### Semantic ID (informal term)

A **Semantic ID** is an identifier whose internal structure is:

- interpreted as structured information, and
- used by application logic for branching or decision-making.

In other words:

> The contents of the ID are treated as data by the program.

This is not a formally standardized term.
It is used informally among architects as shorthand for a recurring design failure.

---

### Smart ID (colloquial, intentionally negative)

**Smart ID** is a more explicit and intentionally negative term
used to describe the same anti-pattern.

A Smart ID is an ID that is:
- “clever”
- information-rich
- and therefore tempting to parse in code

Typical examples:

```scala
if (id.startsWith("sie-query-exec")) { ... }
