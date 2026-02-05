# Phase 3.1.1 — Execution Hub Foundation (Recovery Slice)

status = active

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

## 4. Out of Scope

The following items are explicitly excluded from Phase 3.1.1:

- SOA (XML-based services)
- gRPC-based execution
- Retry, circuit breaker, or resilience mechanisms
- Security, authentication, or schema management
- Performance optimization or benchmarking

## 5. Completion Criteria

Phase 3.1.1 may be closed when all of the following conditions are met:

- DockerComponent executes Operations successfully via CNCF.
- RestComponent executes Operations successfully via CNCF.
- ActionCall, ActionEngine, and Execution Infrastructure remain unchanged.
- Failure semantics are identical across Fat JAR, Docker, and REST execution forms.
- The Phase 3.1 scope defined in `phase-3.md` is fully satisfied.

## 6. Notes

- Detailed implementation tasks and verification steps should be tracked in
  a dedicated checklist document (e.g., `phase-3.1.1-checklist.md`).
- Phase 3.2 and Phase 3.3 definitions are not modified by this recovery slice.
