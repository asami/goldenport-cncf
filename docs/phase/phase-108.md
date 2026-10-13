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

## Configuration-driven Availability Profile

Phase 108 must make Active/Standby an opt-in deployment/runtime profile rather
than an application programming exercise.

Add a small versioned configuration model with at least single/development
operation, standard Active/Standby operation, Standby read-only query/cache
policy, rotation policy/cadence, warm-up/readiness policy, and graceful drain
policy.

The ordinary production choice should be expressible as a single standard
profile. Detailed fields are overrides, not mandatory boilerplate.

CNCF/Launcher interprets desired state and drives the lifecycle. Applications
must not implement A/B switching or know which physical instance is Active.

Scheduled rotation, CAR-version replacement, failure takeover and
security-triggered replacement feed one common replacement engine with a typed
reason/trigger. Do not implement four independent transition paths.

### Additional acceptance

A representative Subsystem can move from single-instance configuration to the
standard Active/Standby profile without application-code changes. Enabling the
profile creates/warms the Standby, establishes role authority, performs an
automatic scheduled rotation, drains the old Active and records the rotation
result. Changing the admitted CAR uses the same replacement engine.

## Deployment targets and scope

The first physical integration should prove runtime semantics with Docker
Compose before requiring Kubernetes. Define a narrow Deployment Driver/SPI for
the physical operations actually needed by the common replacement engine; do
not reproduce a general container orchestrator inside CNCF.

Phase 108 must demonstrate at least local-ha. Its contracts must support
host-ha placement without application changes; implementation of the necessary
second-host driver/operations may be sliced according to available
infrastructure.

Kubernetes integration is a later/optional driver unless an implementation
dependency proves it necessary. Multi-AZ/site HA, cross-region failover,
distributed database availability and global active-active are explicitly out
of Phase 108 scope.

Availability acceptance must state the failure boundary actually proven. A
two-runtime single-host test proves runtime/local-ha behavior, not host-failure
tolerance.
