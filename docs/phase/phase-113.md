# Phase 113: CAR Docker Runtime Contract

Status: PLANNED
Planned: 2026-10-16
Consumer: textus-control-center Phase 8
Related: CAR packaging, CNCF runtime lifecycle

## Goal

Define the minimum CNCF/CAR runtime contract required to run a CAR as a Docker container without turning CNCF into a general container orchestration framework.

## Scope

- Define the canonical CAR container startup/entrypoint contract.
- Define runtime configuration and secret injection boundaries.
- Define HTTP bind/listen behavior suitable for an internal container network.
- Define graceful shutdown expectations.
- Define persistent-data/volume ownership boundaries where a CAR requires durable local state.
- Define the CNCF health/readiness operation used by an external operational plane; Docker health is not a replacement for CNCF runtime health.
- Define minimal execution-environment metadata needed by management consumers when it belongs in CNCF rather than a launcher-specific protocol.
- Validate representative CAR startup under a container boundary.

## Boundaries

- Do not implement Docker daemon control in CNCF.
- Do not parse Docker Compose.
- Do not create a generic deployment/orchestration framework solely for the Mac mini use case.
- Container lifecycle authority belongs to the deployment/Supervisor layer.
- Cloudflare Tunnel is outside the CAR runtime contract.
- Secrets must not be baked into CAR artifacts or projected through runtime metadata.

## Acceptance

A representative CAR can be packaged/launched in a container with an explicit entrypoint, configuration, internal HTTP endpoint, graceful termination and CNCF health/readiness surface suitable for Control Center observation.

The contract is execution-environment-neutral where practical so later container runtimes do not require application-level redesign.
