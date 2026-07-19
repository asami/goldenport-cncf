# Phase 39 - Server Port Allocation

Stage Status:
- Current status: CLOSED
- Current step: Complete
- Owner: CNCF HTTP runtime and artifact deployment metadata.

status = closed

## Purpose

Phase 39 gives each locally runnable CAR or SAR artifact a stable HTTP default
without making application code allocate or probe ports. CNCF resolves explicit
operator overrides first, then coordinates artifact defaults and additional
instances through a machine-local registry.

## Scope

- Define CAR, SAR, additional-instance, and bare-runtime port ranges.
- Resolve explicit configuration and JVM overrides before automatic allocation.
- Persist locked machine-local CAR/SAR assignments.
- Validate declared artifact defaults and reject conflicting ownership.
- Keep a bare CNCF runtime compatible on port `8080`.
- Publish the selected endpoint only after the HTTP server is bound.

## Boundary

- Components declare `textus.server.default-port`; they do not calculate,
  reserve, or probe server ports.
- CNCF owns runtime classification, registry storage, locking, availability,
  and fallback allocation.
- Textus Control Center owns the official artifact default-port catalog.
- Deployments requiring a fixed endpoint use `textus.server.port`; CNCF does
  not replace an occupied explicit value.

## Completion Evidence

- `ServerPortPolicy` centralizes resolution for `CncfRuntime`.
- `ServerPortPolicySpec` verifies CAR/SAR defaults, additional instances,
  explicit overrides, bare-runtime compatibility, persistent assignment, and
  conflict rejection.
- `Http4sHttpServer` reports the actual port only after binding succeeds and
  publishes a process-local bound-endpoint handshake for delayed launcher
  registration.
- `docs/design/server-port-allocation.md` and the CNCF developer guide record
  the artifact metadata and operational contract.

## Deferred Work

- Maintain the official artifact port registry in Textus Control Center.
- Migrate existing production artifacts to declared defaults.
- Add deployment-specific discovery for clients connecting to dynamically
  allocated additional instances.
