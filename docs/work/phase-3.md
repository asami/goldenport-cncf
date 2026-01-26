# Phase 3 — Execution and Orchestration Hub

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items that deliver the orchestration-oriented Phase 3. It is authoritative for status, scope, and the live priorities; it is not a design journal or speculative narrative. Updates must follow the same stack-based model used in other phase work notes.

Phase 3 work proceeds in parallel with Phase 2.9. Although Phase 2.9 remains active, its core architecture and conceptual backbone are already established, and the remaining effort consists mainly of incremental refinements and tuning. This allows Phase 3 execution and orchestration work to begin without destabilizing the Phase 2.9 track.

## 2. Phase Scope

- Reframe CNCF as a coordinated execution hub rather than a single demo runtime.
- Assemble the foundational runtime layers that host Fat JAR, Docker, and remote (microservice/SOA) components.
- Tie those execution forms into a unified Component/Service/Operation model so generated components deploy consistently.
- Surface transformational tooling: generate executable Entity Components from CML and capture metadata.
- Explore AI agent integration purely as a proof-of-concept, exposing projections (OpenAPI/MCP) without assuming production readiness.

## 3. Sub-phases

- **Phase 3.1: Execution Hub Foundation** — deliver Fat JAR, Docker, and Microservice/SOA component models and unify them under CNCF’s component abstraction.
- **Phase 3.2: CML → Entity Component Generation** — generate, package, and enrich Entity Components derived from CML with documentation and metadata.
- **Phase 3.3: AI Agent Hub (PoC)** — project CNCF operations to OpenAPI/MCP and enable experimental AI agents to invoke them; explicitly PoC-only.

## 4. Current Work Stack

- A (ACTIVE): EH- Foundation — implement component adapters for Fat JAR, Docker, and remote executions, ensuring the Component/Service/Operation model remains consistent.
- B (SUSPENDED): CML Entity Generator — prototype CML-to-component code generation and bundling strategy.
- C (SUSPENDED): AI Agent Hub PoC — sample OpenAPI/MCP projections and an agent sketch that can call CNCF operations safely.

## 5. Development Items

- [ ] EH-01: Define runtime contracts for Fat JAR, Docker, and Microservice/SOA components.
- [ ] EH-02: Verify component orchestration by wiring all execution modes through the same Component -> Service -> Operation graph.
- [ ] CG-01: Generate Entity Components from CML models, including packaging metadata and docs.
- [ ] AI-01: Publish OpenAPI/MCP projections and capture the agent invocation flow as an experimental sketch.

## 6. Next Phase Candidates

- NP-001: Formalize AI agent lifecycle after Phase 3 PoC finds a stable entry point.
- NP-002: Consume generated Entity Components in a CRUD-driven subsystem (Phase 4 prep).

## 7. Notes

- Detailed checklists and verification status belong in linked journal entries or phase-specific checklists (e.g., `phase-3-checklist.md` once created).
- This document will be frozen once Phase 3 is closed; refer forward to Phase 4 materials afterward.
