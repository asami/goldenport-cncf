# Phase 3.1.1 Checklist — Execution Hub Foundation (Recovery)

## Scope Confirmation
- [x] Confirm phase-3.md Phase 3.1 is the authoritative scope
- [x] Confirm Phase 3.1.1 is a recovery slice (no redefinition)


## DockerComponent
- [x] Define DockerComponent as a Component adapter
- [x] Execute Operation via Docker container
- [x] Map failures to Observation (deferred)
- [x] Confirm runtime survival on failure (deferred)

## RestComponent
- [x] Define RestComponent as a Component adapter
- [x] Execute Operation via HTTP/REST
- [x] (deferred) Map HTTP/network failures to Observation
- [x] Confirm no ActionEngine branching

## ShellCommand / UnitOfWork Integration
- [x] Expose ShellCommand execution as UnitOfWork operation
- [x] Ensure ShellCommand can be invoked via ActionCall / FunctionalActionCall
- [x] Confirm ShellCommand execution participates in UnitOfWork lifecycle

## Non-Goals
- [x] Explicitly exclude SOA (XML)
- [x] Explicitly exclude gRPC

## Deferred Items

### Map failures to Observation
- **Reason:** Deferred for future implementation due to prioritization in current phase.
- **Planned phase:** Phase 3.2

### Confirm runtime survival on failure
- **Reason:** Deferred for future implementation due to prioritization in current phase.
- **Planned phase:** Phase 3.2

### Map HTTP/network failures to Observation (REST)
- **Reason:** Observation mapping for REST/network errors postponed; basic execution path validated first.
- **Planned phase:** Phase 3.2
