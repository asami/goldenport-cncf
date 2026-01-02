# AGENTS.md

## Purpose

- This repository treats Executable Specification as first-class documentation.
- Scala + functional programming minimizes example-based unit tests.
- Agents must follow the order: rules -> spec -> design -> code.

## Scope (CNCF)

This repository contains CNCF (Cloud-Native Component Framework) code
that extends goldenport core.

Design decisions in goldenport core are authoritative.
CNCF must adapt to core abstractions and must not redefine them
unless explicitly instructed.


## How to Read This Repository (for Agents)

1. `AGENTS.md` (this file)
2. `RULE.md` (top-level rules for code, API, and AI behavior)
3. `README.md` (human-oriented overview and entry point)
4. `docs/rules/` (documentation rules and policies; see document-boundary.md first)
5. `docs/spec/` (static specifications)
6. `docs/design/` (design intent and boundaries; start with `docs/design/protocol-core.md`)
7. `docs/notes/` (non-normative notes; never overrides rules/spec/design)
8. `docs/ai/` (agent/AI operational notes; never overrides rules/spec/design)
9. `src/main/scala/` (implementation)
10. `src/test/scala/` (Executable Specifications)

## Canonical Design Documents

- `docs/design/protocol-core.md`  
  Primary design entry for protocol boundaries and invariants.  
  MUST be read before modifying protocol-related code.

- `docs/design/protocol-introspection.md`  
  Projection / introspection design for CLI help, REST OpenAPI, MCP get_manifest.  
  Read this when working on projection generation.


## Executable Specification Policy

- `src/test/scala` stores Executable Specifications by default.
- Avoid simple example-based unit tests.
- Executable Specifications must:
  - use Given / When / Then structure
  - use Property-Based Testing (ScalaCheck) actively
  - read as behavior documentation


## Specification Categories (by Package)

Executable Specifications are organized by package.

### org.goldenport.protocol

- Fixes Protocol / Model semantics (semantic boundary).
- Covers datatype normalization and parameter resolution.
- Example:
  - `OperationDefinitionResolveParameterSpec.scala`

### org.goldenport.scenario

- Usecase -> usecase slice -> BDD specs.
- Scenario descriptions in Given / When / Then style.
- Human-readable behavior specifications.


## Rules / Spec / Design Boundaries

### rules

- naming rules
- **type modeling rule (abstract class vs trait)**: `docs/rules/type-modeling.md`
- spec style rules
- operation / parameter definition rules
- rules only; no exploration notes

### spec

- static specification documents
- linking to Executable Specifications
- specification itself, not executable

### design

- immutable design decisions
- boundaries, responsibilities, intent
- no exploration notes

### notes

- design exploration memos
- trial and error history
- not normative
- must not override rules, specs, or design decisions

### ai

- agent usage patterns
- prompts or command conventions
- MCP / introspection consumption notes
- not normative
- must not override rules, specs, or design decisions

## CNCF Architecture Boundaries

- CNCF may extend goldenport core abstractions.
- goldenport core must not depend on CNCF.

- CNCF ExecutionContext extends core ExecutionContext.
- RuntimeContext must not leak into goldenport core.

- EnvironmentContext is core-owned; CNCF consumes it and must not reinterpret it.
- CanonicalId is core-owned, opaque, and safe to log; CNCF must not generate, parse, or branch on it.
- CNCF must not add convenience APIs to core abstractions.
- Contexts are immutable, constructed explicitly, and injected (never inferred).

- IDs must follow the Canonical ID design in `docs/design/id.md`.
- Canonical IDs must not be parsed or interpreted by program logic.


## Do / Don't for Agents

### Do

- treat Executable Specifications as the source of truth
- keep Given/When/Then + PBT style
- preserve existing spec semantics

### Don't

- change behavior without updating specs
- change meaning without Executable Specification
- refactor against rules or design guidance
- introduce parallel or competing public APIs without explicit approval
- "improve" architecture speculatively; ask first if intent is unclear


END OF AGENTS.md
