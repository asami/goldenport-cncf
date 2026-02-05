# Phase 3.1.1 Checklist — Execution Hub Foundation (Recovery)

## Scope Confirmation
- [x] Confirm phase-3.md Phase 3.1 is the authoritative scope
- [x] Confirm Phase 3.1.1 is a recovery slice (no redefinition)

## DockerComponent
- [ ] Define DockerComponent as a Component adapter
- [ ] Execute Operation via Docker container
- [ ] Map failures to Observation
- [ ] Confirm runtime survival on failure

## RestComponent
- [ ] Define RestComponent as a Component adapter
- [ ] Execute Operation via HTTP/REST
- [ ] Map HTTP/network failures to Observation
- [ ] Confirm no ActionEngine branching

## Non-Goals
- [ ] Explicitly exclude SOA (XML)
- [ ] Explicitly exclude gRPC
