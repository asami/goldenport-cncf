# Phase 108: Active/Standby Immutable Subsystem Runtime

status=planned
planned_at=2026-10-14
depends_on=[Phase 107]
authority=[Subsystem Runtime Architecture](../design/subsystem-runtime-architecture.md), [Availability Architecture](../design/availability-architecture.md)
consumer=Textus Control Center Phase 7

## Goal

Implement the minimal CNCF runtime contract for zero-downtime replaceable Domain
Subsystems with Active/Standby operation, functional read-only Standby use and
regular rotation.

## Work

1. Define runtime instance identity, admitted CAR/config version and lifecycle
   state projection: starting/warming/ready/active/standby/draining/replacing/
   terminated (final ABI names may differ).
2. Separate liveness, readiness and mutation authority.
3. Define Active mutation Capability and Standby read-only/takeover authority;
   network location/hostname is not authority.
4. Define readiness/warm-up contract for reconstructable caches and
   memory-resident projections.
5. Define consistent new-work routing switch and bounded old-Active drain.
6. Align synchronous in-flight operations with drain semantics and durable
   Job/Workflow/Continuation with restart/resume semantics; do not preserve
   arbitrary process memory.
7. Define planned rotation and failure takeover through the same core transition
   path.
8. Expose operational evidence required by Availability Architecture, including
   last successful/failed rotation and degraded single-instance state.
9. Support security-triggered replacement using the same replacement operation;
   do not add memory-integrity/hash machinery.

## Acceptance

A representative Subsystem runs A Active/B Standby, permits admitted read-only
Standby use, rejects Standby mutation, creates/warms a fresh replacement,
switches new work without planned downtime, drains the old Active, preserves
durable Job/Workflow semantics, and subsequently rotates the former Standby/Active
roles. A failed readiness check cannot acquire Active mutation authority.
