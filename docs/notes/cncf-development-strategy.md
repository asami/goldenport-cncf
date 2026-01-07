status = draft
scope = internal development strategy

# CNCF Development Strategy

## 1. Purpose of This Document
- Provide a shared, top-level strategy for growing CNCF in stages.
- Prevent scope drift by making phase boundaries explicit.
- Serve as the meta-context for future notes and design documents.

## 2. Development Philosophy
- Bootstrap first.
- Model before execution.
- Projection over adapter.
- Subsystem as a reusable execution unit.
- One execution model, multiple frontends (HTTP / CLI / Client).

## 3. Phase Overview

### Phase 1: HelloWorld Bootstrap
- Goal: no-setting startup guarantee.
- Artifact: `helloworld-bootstrap.md`.
- Excluded: CRUD, CML, persistence, authentication.

### Phase 1.5: Subsystem Execution Model Fix (Internal)
- Goal: fix execution responsibilities.
- Artifact: `helloworld-step2-subsystem-execution.md`.
- Internal design note, not a demo or article.

### Phase 2: HelloWorld Demo Strategy
- Goal: user-visible demo experience.
- Artifact: `helloworld-demo-strategy.md`.
- Covers: server, OpenAPI, client, command, component demo.
- This phase exists to support a concrete demo article.

### Phase 3: CML → CRUD Domain Subsystem
- Goal: domain modeling and runtime bootstrap.
- Not driven by demo requirements.
- Artifact: `cml-crud-domain-subsystem-bootstrap.md`.

## 4. Relationship Between Phases
- Later phases depend on earlier phases.
- Phase 1.5 constrains Phase 2 and Phase 3.
- Execution model must not be changed by demo needs.

## 5. How to Read the Documents
- CNCF developers: use this strategy to sequence work.
- Demo/article authors: respect phase boundaries.
- AI assistants (Codex / ChatGPT): treat this as the top-level planning context.

## 6. Explicit Non-Goals
- No skipping phases.
- No CRUD before demo foundation.
- No REST adapter explosion.
- No demo-driven architecture distortion.
