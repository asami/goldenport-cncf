status = draft
scope = internal development strategy

# CNCF Development Strategy

## 1. Purpose of This Document
- Provide a shared, top-level strategy for growing CNCF in stages.
- Prevent scope drift by making phase boundaries explicit.
- Serve as the meta-context for future notes and design documents.
- This document covers strategy only; execution, verification, and results live in notes.

## 2. Development Philosophy
- Bootstrap first.
- Model before execution.
- Projection over adapter.
- Subsystem as a reusable execution unit.
- One execution model, multiple frontends (HTTP / CLI / Client).

## 3. Phase Overview

### Phase 1: HelloWorld Bootstrap
- Goal: no-setting startup guarantee.
- Artifact (notes): `docs/notes/helloworld-bootstrap.md`.
- Excluded: CRUD, CML, persistence, authentication.

### Phase 1.5: Subsystem Execution Model Fix (Internal)
- Goal: fix execution responsibilities.
- Artifact (notes): `docs/notes/helloworld-step2-subsystem-execution.md`.
- Internal design note, not a demo or article.

### Phase 2: HelloWorld Demo Strategy
- Goal: user-visible demo experience.
- Artifact (notes): `docs/notes/helloworld-demo-strategy.md`.
- Covers: server, OpenAPI, client, command, component demo.
- This phase exists to support a concrete demo article.

### Phase 2.5: Error Semantics Consolidation

**Goal**
Finalize and freeze the error / failure semantics of CNCF
before entering CML and CRUD-oriented development.

**Scope**
- Consolidate core-level semantics:
  - Observation
  - Conclusion
  - Cause / CauseKind
  - Detail error code strategy
- Define CNCF-level projections:
  - CLI exit code mapping
  - HTTP status mapping
  - Client-visible error representation

**Non-goals**
- No CML modeling
- No CRUD generation
- No workflow or job orchestration

**Rationale**
Error semantics must be completed before domain expansion.
CML and CRUD layers will rely on this frozen contract.

**Artifacts**
- CNCF note: `error-semantics.md`
- Core note: `core-error-semantics.md`
- Related design notes:
  - `docs/notes/scope-context-design.md`
  - `docs/notes/conclusion-observation-design.md`
  - `docs/notes/observability-engine-build-emit-design.md`

### Phase 2.6: Demo Completion on Frozen Platform

**Goal**
Complete remaining Phase 2.0 demo stages on top of frozen platform contracts.

**Scope**
- No platform contract changes:
  - Execution model
  - Error semantics
  - Observability semantics
  - ScopeContext model
- Complete demo stages:
  - OpenAPI projection
  - Client demo
  - Custom component demo
  - Demo consolidation
- Stage 4 (Client demo) is DONE; client demo verified via real/fake HttpDriver with curl-equivalent output (`ok`).

**Exit Criteria**
- See: `docs/notes/phase-2.6-demo-done-checklist.md`

**Relationship**
Phase 2.0 may be incomplete; Phase 2.6 completes it without re-opening
platform contracts. Phase 3 starts only after Phase 2.6 exit criteria are met.

**References**
- `docs/notes/helloworld-demo-strategy.md`
- `docs/notes/helloworld-bootstrap.md`
- `docs/notes/phase-2.5-observability-overview.md`
- `docs/notes/interrupt-ticket.md`

### Phase 2.8: Deferred Development Resolution

**Purpose**
Resolve architectural technical debt discovered during Phase 2.6 without adding new features.

**Scope**
- Path alias resolution logic
- Canonical vs alias routing normalization
- Normalize `CncfMain` command invocation:
  - Introduce `OperationDefinition` for CLI command definitions.
  - Definition-driven parameter validation and execution dispatch.
  - Consolidate command-line arguments and configuration inputs.

**Non-goals**
- No semantic changes
- No new features

**Artifact (notes)**
- `docs/notes/phase-2.8-infrastructure-hygiene.md`

### Phase 2.9: Error Model Realignment

**Purpose**
Re-align error taxonomy and definitions before Phase 3 (CML).

**Scope**
- Use Phase 2.5 semantics as foundation
- Incorporate practical issues discovered in Phase 2.6
- Define CNCF-level exit code policy:
  - OS / shell exit codes are constrained to 8-bit (0–255) and exposed as `Int`.
  - `Conclusion.detailCode` is planned as `Long` and represents semantic detail.
  - Exit codes MUST NOT be derived directly from `detailCode`.
- Introduce a CNCF framework policy to compute exit codes from `Conclusion`:
  - Mapping extracts only operationally relevant information
    (e.g. success vs failure, retryable vs non-retryable, usage vs defect).
  - Mapping normalizes results to valid 8-bit exit codes.
- Centralize process termination:
  - `CncfMain` is the sole component allowed to invoke `sys.exit`.
  - All other layers propagate failures via `Consequence` / `Conclusion` only.

**Non-goals**
- No CML modeling
- No CRUD generation

**Artifact (notes)**
- `docs/notes/phase-2.9-error-realignment.md`

**Relationship**
Phase 3.0 starts only after Phase 2.9 is complete.

### Phase 3: CML → CRUD Domain Subsystem
- Goal: domain modeling and runtime bootstrap.
- Not driven by demo requirements.
- Artifact (notes): `docs/notes/cml-crud-domain-subsystem-bootstrap.md`.

### Phase 4: State Machine Foundation
- Goal: introduce a first-class state machine model usable by domain subsystems and components.
- Scope:
- Define state / transition / guard / effect representation.
- Enable introspection outputs (e.g., transition table / diagram source).
- Provide runtime hooks for validating transitions during execution.
- Non-goals:
- No workflow engine.
- No persistence/event sourcing requirements.
- Artifact (notes): `docs/notes/state-machine-foundation.md`.

### Phase 5: Event Foundation
- Goal: introduce a first-class event model for domain and runtime observability.
- Scope:
- Define event envelope (id, time, correlation, payload).
- Define event emission points from execution / domain actions.
- Define minimal event handling contract (publish/subscribe boundary).
- Non-goals:
- No event sourcing mandate.
- No distributed saga/orchestration engine.
- Artifact (notes): `docs/notes/event-foundation.md`.

## 4. Relationship Between Phases
- Later phases depend on earlier phases.
- Phase 1.5 constrains Phase 2 and Phase 3.
- Execution model must not be changed by demo needs.
- Phase 4 depends on Phase 3 outputs (domain model / CRUD scaffolding as baseline consumers).
- Phase 5 depends on Phase 4 (events may reference state transitions and state machine lifecycle).

## 5. How to Read the Documents
- CNCF developers: use this strategy to sequence work.
- Demo/article authors: respect phase boundaries.
- AI assistants (Codex / ChatGPT): treat this as the top-level planning context.
- Notes contain execution details and results for each phase.

## 6. Explicit Non-Goals
- No skipping phases.
- No CRUD before demo foundation.
- No REST adapter explosion.
- No demo-driven architecture distortion.
