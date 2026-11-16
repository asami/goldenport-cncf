# Phase 108: Primary/Read Replica Immutable Subsystem Runtime

status=planned
planned_at=2026-10-14
depends_on=[Phase 107]
authority=[Subsystem Runtime Architecture](../design/subsystem-runtime-architecture.md), [Availability Architecture](../design/availability-architecture.md)
consumer=Textus Control Center Phase 7

## Goal

Implement the minimal CNCF runtime contract for zero-downtime replaceable Domain
Subsystems with Primary/Read Replica operation, promotion-ready Standby use and
regular rotation.

## Implementation Priority - 2026-11-12

Within this HA work, prioritize the first operational immutable-infrastructure
delivery defined by
[Availability Architecture](../design/availability-architecture.md#implementation-priority-operational-immutable-replacement).
Use the established control mechanisms and the shared multi-replica cache design.
Do not wait for full automatic Failover or broad topology/driver delivery before
qualifying planned replacement. Authority enforcement and safe refusal on
unexpected failure remain mandatory in the first slice.

| Order | Work unit | Required first-route result | State |
| --- | --- | --- | --- |
| 1 | IR-01 Owning integration | Bind actual runtime admission, scoped old-work completion, persistence/commit, established coordination and enforceable authority-loss handling | OPEN |
| 2 | IR-02 Shared cache path | A/B/C synchronization with source versions, applied progress, late commits, delete/invalidation/purge and consistent initial catch-up | OPEN |
| 3 | IR-03 Planned replacement | Ready B accepts new reads/updates while A completes only admitted work; C starts and becomes a qualified Read Replica | OPEN |
| 4 | IR-04 Retention and repeated operation | Synchronized fallback, admitted switch-back, conditional retirement, explicit and scheduled rotation, compatible selected-CAR replacement | OPEN |
| 5 | IR-05 Operational qualification | Real separate runtimes and persistent updates prove the whole first route, negative safety cases and native operational observations | OPEN |

First operational acceptance is a usable planned-replacement capability, not a
claim of automatic failure takeover, host HA or whole-Phase closure. Later
automatic Failover, expanded deployment and consumer presentation work remains
recorded; it is not silently removed or declared complete by this prioritization.
All first-route units are OPEN. This plan amendment starts no native Session,
registers no Goal and claims no implementation or execution result.

## Work

1. Define runtime instance identity, admitted CAR/config version and lifecycle
   state projection: starting/warming/ready/active/standby/draining/replacing/
   terminated (final ABI names may differ).
2. Separate liveness, readiness and mutation authority.
3. Define Primary mutation admission, Read Replica service and Standby takeover
   authority; network location/hostname is not authority.
4. Define readiness/warm-up contract for reconstructable caches and
   memory-resident projections.
5. Define consistent new-work routing switch and scoped Draining Former Primary
   completion.
6. Align synchronous in-flight operations with drain semantics and durable
   Job/Workflow/Continuation with restart/resume semantics; do not preserve
   arbitrary process memory.
7. Reuse established election/lease/fencing integration in the common replacement
   engine, with distinct Switchover and Failover paths. Deliver controlled
   Switchover and safe authority-loss refusal first; qualify automatic Failover
   subsequently.
8. Expose operational evidence required by Availability Architecture, including
   last successful/failed rotation and degraded single-instance state.
9. Support security-triggered replacement using the same replacement operation;
   do not add memory-integrity/hash machinery.

## Related Planned Deliveries — 2026-11-13

[Phase 123](phase-123.md) owns activity-scoped Entity Working Sets;
[Phase 124](phase-124.md) owns Read-only service mode for failure and maintenance.
Their first single-runtime routes reuse existing resident capabilities. Cluster
qualification consumes actual Phase 108 admission, authority and synchronization
capabilities when available. These are independent planned deliveries, not new
Phase 108 acceptance conditions or predecessors; IR-01..IR-05 remain unchanged.

## Acceptance

The first operational gate runs a representative Subsystem as A Primary/B
Read Replica qualified as Standby, permits admitted read-only
Standby use, rejects Standby mutation, creates/warms a fresh replacement,
switches new work without planned downtime while A's admitted update remains in
flight, propagates its late committed state to B/C, preserves durable work
outcomes and keeps A until replacement readiness and retention allow retirement.
It qualifies current-state switch-back and repeats rotation using the same
engine. Failed readiness or invalid authority cannot acquire Primary mutation
admission. This gate alone proves neither automatic Failover nor Phase closure.

## Configuration-driven Availability Profile

Phase 108 must make Primary/Read Replica operation an opt-in deployment/runtime
profile rather than an application programming exercise.

Add a small versioned configuration model with at least single/development
operation, standard Primary/Read Replica operation, replica read policy,
Standby readiness, rotation policy/cadence, warm-up/readiness policy, and
graceful drain policy.

The ordinary production choice should be expressible as a single standard
profile. Detailed fields are overrides, not mandatory boilerplate.

CNCF/Launcher interprets desired state and drives the lifecycle. Applications
must not implement A/B switching or know which physical instance is Primary.

Scheduled rotation, CAR-version replacement, failure takeover and
security-triggered replacement feed one common replacement engine with a typed
reason/trigger. Share owning mechanisms while preserving distinct graceful and
failure preconditions; do not implement four independent control systems.

### Additional acceptance

A representative Subsystem can move from single-instance configuration to the
standard Primary/Read Replica profile without application-code changes. Enabling the
profile creates/warms the Standby, establishes role authority, performs an
automatic scheduled rotation, drains the Draining Former Primary and records the rotation
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
3. build and warm it as the non-Primary runtime;
4. execute representative readiness/usage-contract probes;
5. switch new work only after admission;
6. keep the former Primary fallback-ready for the configured retention window;
7. treat the new Primary as probationary during observation;
8. switch back to the retained former Primary on an admitted decision while the
   planned-transfer preconditions hold; use Failover after unexpected authority loss;
9. commit deployment only after the window/decision succeeds;
10. then retire or rebuild the old runtime using the committed CAR.

Add release policies sufficient to distinguish manual/notify from
automatic-compatible operation. A standard profile should be able to request an
approximately one-day fallback window without application code. The first route
may select a compatible CAR explicitly and use policy-controlled bounded windows
for qualification; automatic release discovery/rollout follows that route.

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
through the same replacement engine with fallback-ready former Primary, while a
deliberately incompatible DataStore ABI candidate is refused by automatic-compatible
policy and surfaced as Maintenance Update required.
