# Phase 2.8 — Work A1  
## Path / Alias Resolution Hygiene (Journal)

Date: 2026-01  
Phase: 2.8  
Work Item: A1  
Status: in progress (journal)

---

## Context

Phase 2.8 focuses on **infrastructure hygiene**.
Work A1 is the foundational task for the phase, addressing
path normalization and alias handling.

Multiple downstream concerns depend on this work:

- Resolver determinism
- Script DSL correctness
- CLI / Script / HTTP consistency
- OpenAPI projection hygiene

Therefore, Work A1 MUST be completed before any resolver
or DSL-level formalization.

---

## Problem Statement

Current issues observed across the codebase and tests:

- Path-like identifiers are handled inconsistently:
  - raw strings
  - partially normalized forms
  - implicit assumptions in resolver and tests
- Alias handling is:
  - partially hard-coded
  - partially implicit
  - not clearly separated from resolver logic
- Different entry points (CLI / Script / HTTP) do not share
  a single, explicit normalization pipeline.

This leads to:
- ambiguous resolution behavior,
- difficulty distinguishing NotFound vs Rejection,
- fragile tests (e.g. ignored ScriptDslSpec).

---

## Scope of Work A1

### Included

- Definition of **CanonicalPath** concept
- Canonical path normalization rules
- Configuration-driven alias model
- Explicit boundary between:
  - normalization / alias expansion
  - resolver core logic

### Explicitly Excluded (Deferred to A2+)

- Prefix matching or search strategy
- Implicit defaults or heuristics
- DSL or repository resolution rules
- UX optimizations

---

## Observations (Initial)

- Several strings such as `admin`, `system`, `default`
  appear as magic values in multiple layers.
- Alias-like behavior exists both:
  - in configuration intent, and
  - embedded in code or tests.
- Resolver currently compensates for
  upstream ambiguity instead of rejecting it.

This suggests the need for:
> a single, canonical preprocessing step
> before resolver invocation.

---

## Concept: CanonicalPath (Draft)

A **CanonicalPath** represents a fully normalized,
alias-expanded, structure-level identifier.

Properties (draft):

- Contains exactly:
  - component
  - service
  - operation
- Contains no raw input artifacts:
  - no original string
  - no alias name
- Equality is structural, not textual.
- String rendering is secondary and derived.

Invariant:
> Resolver MUST receive only CanonicalPath values.

---

## Normalization & Alias Expansion Order (Draft)

Tentative pipeline:

1. Raw input received (CLI / Script / HTTP)
2. Lexical normalization
   - case normalization
   - delimiter normalization
3. Alias expansion
   - exact match only
   - configuration-driven
4. CanonicalPath construction
5. Resolver invocation

Open question:
- Confirm whether alias expansion should happen
  strictly before or after structural validation.

---

## Alias Model (Draft)

- Alias definitions live in **normalized configuration**.
- Alias matching rules:
  - exact match only
  - no prefix or heuristic matching
- Alias expansion:
  - produces canonical identifiers
  - never partial structures

Resolver MUST NOT:
- define aliases
- guess aliases
- compensate for missing alias expansion

---

## Boundary with Resolver Core

Resolver Core assumptions (to be enforced):

- Input is already:
  - normalized
  - alias-expanded
  - canonical
- Resolver logic concerns ONLY:
  - existence
  - ambiguity
  - resolution strategy

Anything else is a bug or upstream violation.

---

## Impact on Tests (Preliminary)

- ScriptDslSpec ignored tests are blocked
  primarily by unresolved alias / normalization rules.
- After Work A1:
  - tests should either:
    - pass deterministically, or
    - fail clearly due to unresolved resolver semantics (A2).

This work should reduce the number of
"gray zone" failures.

---

## Open Questions (To Resolve)

- Exact timing of alias expansion vs validation
- Error classification for:
  - malformed paths
  - unknown aliases
- Representation choice for CanonicalPath
  (value object vs opaque type)

---

## Next Steps

- Write a short design memo:
  - CanonicalPath invariants
  - Normalization rules
- Identify and list all hard-coded alias cases
- Decide alias expansion ordering definitively

Once these are fixed,
move to **Work A2: Resolver Canonical Construction**.

---

## Notes

This document is a **journal entry**.
Content may be rewritten, summarized, or promoted
to design documents once decisions are finalized.
