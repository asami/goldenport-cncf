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

## Deployment Transaction and CAR update

Implement release-aware immutable replacement as a first-class use of the
common Replacement Engine.

Required flow:

1. receive/select a newly admitted CAR candidate;
2. require deployment-policy compatibility evidence;
3. build and warm it as the non-Active runtime;
4. execute representative readiness/usage-contract probes;
5. switch new work only after admission;
6. keep the former Active fallback-ready for the configured retention window;
7. treat the new Active as probationary during observation;
8. switch back to the retained old Active on an admitted failure decision;
9. commit deployment only after the window/decision succeeds;
10. then retire or rebuild the old runtime using the committed CAR.

Add release policies sufficient to distinguish manual/notify from
automatic-compatible operation. A standard profile should be able to request an
approximately one-day fallback window without application code.

The implementation must expose Deployment Transaction state and
ReplacementReason (scheduled rotation, CAR release, failure, security,
infrastructure/operator) as operational evidence.

Phase 108 coordinates with Phase 100/follow-up DataStore work: it must not claim
switch-back compatibility if a migration has already made the retained CAR
unable to operate on authoritative state.

## Normal versus Maintenance release

Consume the canonical DataStore ABI classification.

A CAR release may enter automatic-compatible Deployment Transaction only as a
Normal Update whose required DataStore ABI remains backward compatible with the
fallback CAR for the configured retention window.

If admission identifies a breaking DataStore ABI change, Phase 108 must stop
the normal automatic path and return/classify Maintenance Update required. It
must not generate reverse migration, schema rollback, dual-write or other
complexity automatically merely to preserve the normal path.

Maintenance Update is a separately authorized operation and is outside the
daily rotation/release mechanism. After it establishes a new admitted baseline,
normal immutable rotation resumes.

Acceptance must demonstrate both branches: a compatible CAR updates
automatically with fallback-ready old Active, while a deliberately incompatible
DataStore ABI candidate is refused by automatic-compatible policy and surfaced
as Maintenance Update required.
