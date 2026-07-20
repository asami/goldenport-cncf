# Phase 44 - Managed Service Container Runtime Checklist

This checklist is the authoritative planned Phase 44 state ledger.

## SC-01: Contract Promotion

Status: DONE

- [x] Define service-container ownership, lifecycle, and external endpoint
      semantics in normative design/spec documents.
- [x] Keep one-shot Process Execution and service-container lifecycle distinct.
- [x] Record how the obsolete `ServerDockerAdapter` stub is retired or
      redirected without making it the public contract.

## SC-02: Runtime Model

Status: DONE

- [x] Define logical service, image, ownership, reuse, endpoint, readiness,
      persistence, cleanup, status, and transition models.
- [x] Define a runtime-owned registry keyed by admitted owner and logical
      service identity.
- [x] Define structured lifecycle failure outcomes.

## SC-03: Docker Gateway and Fake Evidence

Status: DONE

- [x] Define a constrained Docker gateway with no arbitrary argument surface.
- [x] Add deterministic fake-gateway executable specifications.
- [x] Verify ownership labels and compatibility checks before reuse.

## SC-04: Lifecycle Runtime

Status: DONE

- [x] Implement external bypass, inspect, create-or-reuse, start, readiness,
      stop, restart, and remove semantics.
- [x] Provide runtime-owned shutdown cleanup outside UnitOfWork terminal
      cleanup; host/subsystem shutdown wiring remains SC-05.
- [x] Make transition and cleanup outcomes idempotent.

## SC-05: Observability and Safety

Status: DONE

- [x] Add payload-safe CallTree and service-container lifecycle metrics.
- [x] Redact credentials, environment values, sensitive mounts, and provider
      payloads.
- [x] Keep endpoint projection credential-free.
- [x] Wire service-container cleanup into host/subsystem runtime shutdown with
      structured diagnostics.

## SC-06: Textus AI Driver

Status: PLANNED

- [ ] Preserve explicit Ollama endpoint precedence.
- [ ] Use the managed service runtime only when an owned local service is
      selected.
- [ ] Keep model installation as a provider-owned observable follow-up step.

## SC-07: Textus SIE Driver

Status: PLANNED

- [ ] Validate owned Fuseki/vector service lifecycle through the common
      contract.
- [ ] Keep compose scripts as development/heavy-test tooling.
- [ ] Keep SIE provider policy outside CNCF core.

## SC-08: Verification and Closure

Status: PLANNED

- [ ] Run focused fake-gateway and lifecycle executable specifications.
- [ ] Run full CNCF and affected downstream tests.
- [ ] Run opt-in live Docker integration as heavy validation.
- [ ] Update strategy/phase evidence and close Phase 44.
