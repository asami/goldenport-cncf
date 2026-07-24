# CNCF Repository Agent Guide

## Purpose

- This guide supplements the shared root `AGENT.md` / `AGENTS.md`.
- It identifies CNCF-specific architecture boundaries and canonical documents.
- It is operational guidance and does not override shared or repository rules.

## AI-Assisted Development Rules

Earlier Chappie-specific rules have been removed. Current agent-facing rules and conventions are located under `docs/ai/`. The `RULE.md` file and documents under `docs/rules/` remain authoritative for all agents.

Shared AI operational directives are mounted under `ai/directive/`.
Repository-local AI bridge guidance lives under `docs/ai/`.
Agents must use the shared directives through that mounted path and follow
repository-local bridge instructions where present.

Agents must comply with these rules before performing any action,
especially before editing files or generating implementation results.

## Scope (CNCF)

This repository contains CNCF (Cloud-Native Component Framework) code
that extends goldenport core.

Design decisions in goldenport core are authoritative.
CNCF must adapt to core abstractions and must not redefine them
unless explicitly instructed.


## CNCF Reading Order

After the shared root directives:

1. `docs/rules/repository-rules.md`
2. `README.md`
3. remaining documents under `docs/rules/`
4. `docs/spec/`
5. `docs/design/cncf-architecture-overview.md`
6. affected subsystem design documents
7. implementation and Executable Specifications

## Canonical Design Documents

- `docs/design/cncf-architecture-overview.md`
  Primary design entry for CNCF architecture boundaries and invariants.
  MUST be read before modifying protocol/runtime boundary code.

- `docs/design/cncf-introspection-spec.md`
  Projection / introspection design for CLI help, REST OpenAPI, and manifest surfaces.
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


## AI Working Agreements

AI agents MUST follow the AI–Human Collaboration Convention.

Canonical reference:
- docs/ai/ai-human-collaboration-convention.md
- docs/ai/shared-directive-bridge.md

END OF CNCF REPOSITORY AGENT GUIDE
