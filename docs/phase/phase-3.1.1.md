# Phase 3.1.1 — Execution Hub Foundation (Recovery Slice)

status = closed

## 1. Purpose

Phase 3.1.1 is a recovery slice introduced to fulfill the original Phase 3.1
definition stated in `phase-3.md`.

During Phase 3.1, the Fat JAR execution form was established as the baseline.
However, Docker- and REST-based execution forms, which were explicitly included
in the Phase 3.1 scope, were not implemented at that time.

Phase 3.1.1 exists to **complete the originally intended Phase 3.1 scope**,
not to revise the Phase 3 structure or redefine later phases.

## 2. Authoritative Context

- `phase-3.md` remains the authoritative Phase 3 definition.
- Phase 3.1.1 is a subordinate recovery slice under Phase 3.1.
- Responsibility boundaries established during Phase 3.1 are reused as-is.

## 3. In Scope

### 3.1 DockerComponent

- Implement Docker-based execution as a first-class Component.
- DockerComponent is positioned at the same level as CollaboratorComponent.
- Docker execution is realized via a Component adapter.
- ActionEngine and Execution Infrastructure must not branch by execution form.
- Failure semantics must match Fat JAR execution
  (Observation mapping and runtime survival).

### 3.2 RestComponent

- Implement HTTP/REST-based execution as a first-class Component.
- External REST services are normalized into the Component / Service / Operation model.
- Transport- and protocol-specific logic must remain inside the adapter layer.
- Failure semantics must match Fat JAR execution.

### 3.3 ShellCommand UnitOfWork Integration

- Expose ShellCommand execution as UnitOfWorkOp.
- Provide ActionCall-level DSL (ShellCommandActionCallPart) analogous to ActionCallHttpPart.
- Ensure ShellCommand execution participates in UnitOfWork lifecycle
  (run / abort / commit) without embedding Consequence inside UnitOfWorkOp.

### 3.4 CallTree Capture (Draft)

- Introduce a core CallTree model for low-level execution tracing.
- Capture ActionEngine-level and UnitOfWork-level execution events into CallTree.
- Thread CallTreeContext through ObservabilityContext for shared capture.
- Treat CallTree as draft infrastructure to be finalized in later phases.

## 4. Out of Scope

The following items are explicitly excluded from Phase 3.1.1:

- SOA (XML-based services)
- gRPC-based execution
- Retry, circuit breaker, or resilience mechanisms
- Security, authentication, or schema management
- Performance optimization or benchmarking

## 5. Completion Criteria

Phase 3.1.1 is considered **closed** with the following status:

- DockerComponent executes Operations successfully via CNCF.
- RestComponent executes Operations successfully via CNCF.
- ShellCommand execution is integrated into UnitOfWork.
- CallTree draft model and capture plumbing exist in core.
- ActionCall, ActionEngine, and Execution Infrastructure remain unchanged.
- Failure semantics across Fat JAR, Docker, and REST execution forms are
  structurally aligned at the framework level.

The following items are **explicitly deferred** to later phases and tracked
in the Phase 3.1.1 checklist:

- Detailed failure-to-Observation mapping
- Runtime survival guarantees under failure conditions
- CallTree formalization and externalization into Observability records

## 6. Notes

- Deferred items are recorded in the Phase 3.1.1 checklist under
  “Deferred / Next Phase Candidates” and are not left implicit.
- This recovery slice completes the originally declared Phase 3.1 scope.
- Subsequent phases (e.g., observability refinement, resilience, survival)
  will build on the execution forms stabilized here.

closed_at = 2026-02-07
