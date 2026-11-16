# Textus Availability Architecture

status=initial
authority=canonical
updated_at=2026-11-12
runtime=[Subsystem Runtime Architecture](subsystem-runtime-architecture.md)
security=[Textus Security Architecture](textus-security-architecture.md)
cache=[Runtime Cache Synchronization](runtime-cache-synchronization.md)

## Purpose

This document is the canonical Textus availability architecture. Availability
is continuously exercised during normal operation rather than reserved for a
rare disaster path.

The protocol additions below are intended design, not evidence of an installed
coordination service, implemented fencing, public ABI or qualified automatic
failover. A deployment may advertise those capabilities only after its actual
control, persistence and runtime bindings have been qualified together.

## Roles and Established Control Mechanisms

The following terminology is adopted for Textus HA design and explanatory prose:

| Term | Meaning |
| --- | --- |
| Primary | Runtime responsible for new authoritative mutation admission |
| Read Replica | Synchronized replica serving permitted read-cache/projection queries |
| Standby | Runtime qualified and ready for promotion to Primary |
| Draining Former Primary | Former Primary completing only its explicitly retained pre-switch admitted work |

The topology is a Primary plus one or more Read Replicas. Standby describes
promotion readiness, while Read Replica describes read service; neither role
implies the other. A takeover-ready Read Replica has both roles. After its old
work is settled, a retained former Primary is no longer described as draining;
it can remain a synchronized fallback under the separate retention policy.

Use Primary/Read Replica rather than Master/Slave in current design prose.
Existing lifecycle state names such as `ACTIVE`, `STANDBY` and `DRAINING` remain
unchanged: `ACTIVE` maps to Primary admission, and `DRAINING` can represent the
Draining Former Primary's bounded completion role. This terminology adoption
does not rename runtime APIs, serialized states or configuration tokens, or
rewrite historical journal records. It does not imply a completed code migration.

The adoption is recorded in the
[new terminology journal entry](../journal/2026/11/2026-11-12-ha-role-terminology-adoption.md).
[Terminology notes](../notes/ha-role-terminology.md) explain how to read older
records alongside this current vocabulary. Historical journals remain unchanged;
subsequent decisions are recorded as new entries rather than retrospective edits.

Adopt established coordination mechanisms rather than implement a CNCF-specific
consensus, voting, failure-detector or distributed-lock protocol:

| Mechanism | Required responsibility |
| --- | --- |
| Leader Election | Select one owner of new Primary admission in the declared authority scope |
| Lease | Maintain that ownership for a bounded period and report renewal/loss through the selected control service |
| Fencing | Enforce which runtime/work authority may still cause mutations at the relevant execution and persistence boundaries |
| Readiness | Qualify runtime compatibility, authoritative-state access and cache/source progress independently of election success |
| Membership/role observation | Publish current admitted roles and transitions for routing, orchestration and operational diagnosis |

etcd Election/Lease is the first candidate binding. Reuse an already deployed
equivalent coordination facility where it satisfies the same contract; the
design does not require installing Kubernetes or selecting a new dependency
before the deployment is known. Reuse the selected service's consensus and
membership implementation. Its internal consensus leader is not the Textus
Primary, and its store revision is not a business-data commit position.

Election/Lease alone does not fence a stale runtime. In particular, guarding an
etcd transaction with leadership does not automatically protect a PostgreSQL
commit, provider call or another external effect. The binding must enforce its
authority at the actual affected boundary, without a check-then-write race.
An observed leader name, heartbeat timeout, load-balancer route or cache cursor
is not sufficient evidence of that enforcement.

Control-service voting membership and read-cache membership are independent.
A majority-based two-voter control cluster cannot continue majority decisions
after losing one voter. A host-failure-tolerant deployment therefore needs a
suitable independent voting arrangement or an existing coordination service.
Adding C as a read cache does not implicitly add a control voter. Declare the
control service's failure domains and quorum availability with the runtime and
DataStore failure boundaries; do not add hidden in-process majority substitutes.

### New Admission and Old-Work Completion Authority

Planned transfer separates two authorities:

- current-Primary authority admits new independent work;
- scoped completion authority permits only exact pre-switch admitted work and
  its required child work on the Draining Former Primary.

The admission boundary must close the former runtime to new independent work,
including stale routes, scheduled work and event-driven entry. It must retain
the actual admitted work correspondence through completion. Merely labeling all
requests with an old generation, without distinguishing admitted work, is not
a completion permit. Continuing old work must not create an unrestricted second
Primary or convert unrelated new work into a child of an old request.

The selected coordination/runtime/persistence integration must support this
scoped overlap. A generic single-writer lease or database-failover product is
not a drop-in implementation of it. Define and qualify the owning typed
continuation/admission contract before claiming the planned protocol is usable.
Reuse native identities and authority mechanisms; do not introduce management
hashes or application-owned pseudo-locks for this purpose.

Loss of valid completion authority or an unexpected failure changes the case to
the Failover protocol below. Graceful completion permission does not survive
fencing merely because the work started earlier. Before a replacement admits
updates after failure, invalid former authority must be unable to produce later
mutations through the protected boundaries. Already committed or uncertain
external effects remain recovery facts, not effects that fencing can undo.

## Principle: Exercise the Recovery Path

A Standby that has only reported process health for months is weak evidence of
availability. Textus therefore supports regular Primary/Standby rotation as a
normal operating practice.

A deployment may choose a daily cadence:

```text
Day 1: A Primary / B Read Replica + Standby
Day 2: B Primary / A Read Replica + Standby
Day 3: A Primary / B Read Replica + Standby
```

The exact cadence is deployment policy, not a CNCF constant. The architectural
requirement is that planned rotation exercises the shared readiness, routing,
authority-transition and synchronization mechanisms also needed by failover.
The protocols share mechanisms, not identical preconditions: failover cannot
require an unreachable former Primary to acknowledge graceful drain.

A successful recent rotation is stronger evidence than a Standby that has never
served as Primary.

## Zero-Downtime Replacement

### Planned Switchover

Use Switchover for a controlled rotation, compatible release, infrastructure
refresh or admitted switch-back while the former Primary can cooperate and its
completion authority remains valid. Planned replacement switches new admission
before all old work has drained. The current Read Replica B, qualified as Standby,
may become the next Primary while former Primary A completes only its already
admitted work. A replacement Read Replica C is then started; the common
synchronization mechanism supports additional replicas too.

The intended sequence is:

1. create/start the candidate runtime from admitted CAR/configuration;
2. connect authoritative persistent state;
3. warm reconstructable memory/cache;
4. pass readiness and compatibility checks, including required source/cache
   progress, and prepare the selected control service's ownership transfer;
5. establish B's valid new-admission ownership and consistently switch new reads
   and mutations to B while closing former-Primary admission, including stale
   routes and background new work; preserve only A's scoped old-work permits;
6. allow the former Primary to complete exactly its already admitted updates and
   required child work while the new Primary accepts new work;
7. start replacement Read Replica capacity and establish its baseline/catch-up;
8. keep the former Primary synchronized during drain and fallback retention;
9. settle admitted updates, qualify replacement Read Replica capacity, and satisfy
   the retention/observation and switch-back conditions;
10. retire/discard the former runtime only after all those conditions hold.

New-admission authority and old-work completion authority are distinct. This is
not unrestricted active-active writing. Native transaction/version semantics
own update conflicts; cache synchronization owns propagation of confirmed
changes from either admitted execution source. A routing switch alone cannot
make a stale cache current or resolve a causal dependency on an unfinished update.

HTTP completion is not the drain criterion: commit/abort and required durable
outcome handling must be settled. Long-running Jobs/Workflows use their actually
supported continuation/recovery boundaries. Persistence alone does not prove
that an interrupted Running task can be resumed or an external effect replayed.
Do not force-interrupt validly admitted old work merely to expire a drain timer;
report prolonged drain or uncertainty and retain the runtime while its planned
retirement conditions fail. Before transfer, refusal leaves A as the admitted
Primary only while its authority remains valid. After transfer, do not restore
A's admission based on a timeout: qualify an explicit switch-back. Loss of
authority, a crash or an unresponsive participant invokes the Failover conditions.

Availability must not depend on copying arbitrary process memory from old to new.

## Functional Use of Standby

Standby capacity need not be idle. It may serve:

- read-only queries whose consistency contract permits it;
- cache/read projection workloads;
- warm-up and readiness verification;
- operational diagnostics that do not mutate domain state.

Standby mutation is prohibited until takeover/admission grants Primary admission authority.
The Draining Former Primary is a distinct role: its completion authority covers
only pre-switch admitted work and grants no new independent admissions.

## Shared Cache Synchronization and Retirement

[Runtime Cache Synchronization](runtime-cache-synchronization.md) owns generic
multi-replica synchronization, deletion/invalidation/purge, version ordering,
applied positions and gap-free initial build/catch-up. HA uses it rather than
creating an A/B/C-specific replication loop. The current Primary, serving and
warming read caches, and retained former Primaries participate according to role.

Only confirmed updates propagate. Late commits by the Draining Former Primary
must reach the new Primary and its replicas through the same source-owned ordering
as new-Primary commits. Duplicate or delayed notifications must not overwrite
newer values, and cache deletion/purge must not permit stale resurrection.

Planned retirement requires the conjunction of settled old admitted work, ready
required replacement Read Replica capacity, satisfied fallback-retention/observation policy,
and no outstanding switch-back or unresolved outcome requiring the old runtime.
Neither a timer, new-Primary liveness nor completion of the old HTTP requests is
sufficient alone. Multiple replacement members are handled by the configured
capacity/readiness policy, not a hard-coded requirement for exactly one C.

Retained fallback members continue catch-up. Switch-back rechecks current source
progress, runtime/DataStore compatibility and readiness, and transfers admission
with the same old-work drain rules; it does not restore an earlier memory image.
These are design requirements, not a claim of completed runtime implementation.

## Failure and Takeover

### Unplanned Failover

Use Failover when the current Primary crashes, cannot renew valid ownership,
loses required connectivity or cannot safely cooperate in planned transfer.
Unreachable does not prove dead: a network-isolated or paused runtime may return.
Do not use its failure to answer as permission to create another independent
writer, and do not wait for it to acknowledge graceful drain.

The intended sequence is:

1. observe suspected failure and control-service ownership state; retain the
   reason and uncertainty rather than declare process death from a timeout;
2. use the selected existing election/lease mechanism to authorize a takeover
   candidate; without valid control-service ownership/quorum, admit no new Primary;
3. invalidate former admission and completion authority and establish effective
   fencing at the protected mutation/effect boundaries before B can perform
   mutations; election success alone does not complete this step;
4. qualify B against compatible runtime/configuration, available authoritative
   persistence, actual source progress and the admitted data-loss/recovery policy;
5. only with valid ownership, fencing and readiness together, activate B and
   update routing/discovery; stale routes must not bypass authority enforcement;
6. reconcile admitted work from authoritative durable outcomes, resume only
   eligible work under its actual owning contract, and retain uncertain effects
   for reconciliation without automatic blind replay;
7. restore read-cache/Standby capacity, rebuilding or catching up each member
   through the shared synchronization mechanism.

Candidate selection and election may overlap preparation, but mutation service
cannot begin until all promotion conditions hold. An elected but unready
candidate is not an available Primary. A cache checkpoint or control-store
revision does not prove business-data durability. Where authoritative data is
replicated, its owning DataStore's admitted replication/recovery policy governs
acknowledged-write preservation and any permitted loss; this design does not
infer zero loss from cache synchronization or leader election.

Do not inherit A's graceful completion permits after fencing. Confirmed committed
work remains committed; confirmed aborted work is not reported as committed;
running or outcome-unknown work is not presumed unexecuted. In particular,
`Running` durable Job records require their owning recovery disposition rather
than unconditional execution on B. A late acknowledgement may help establish
an earlier committed fact but cannot restore revoked authority.

A failed runtime may be isolated or stopped without waiting for normal planned
retention or replacement-cache conditions. Preserve required work/outcome
evidence independently; stopping it is not proof its work settled or was undone.
If A returns, re-admit it as a rebuilding/catching-up replica. It does not regain
Primary authority because of its previous role. Later failback is a separately
admitted planned Switchover, with current compatibility and readiness checks.

If no instance satisfies readiness/admission, the system reports degraded or
unavailable state rather than granting mutation authority to an unqualified
instance merely to remain nominally up.

### Switchover and Failover Comparison

| Condition | Planned Switchover | Unplanned Failover |
| --- | --- | --- |
| Former Primary | Cooperating with valid scoped completion authority | Unavailable, unsafe or no longer authorized |
| New independent admissions | Transfer to B through established ownership/admission control | Begin only after takeover ownership, fencing and readiness are established |
| Already admitted updates on A | Complete under their retained exact scope while B accepts new work | Do not assume completion; reconcile durable outcomes and prevent invalid late effects |
| Source/cache progress | Qualify before switch and continue applying A's late commits | Recover authoritative progress and apply the admitted recovery/data-loss policy |
| Retention and retirement | Wait for settled work, replacement capacity and retention/observation conditions | Isolation does not wait for graceful retention; outcome recovery remains separate |
| Return of former Primary | Retained fallback stays synchronized; switch-back is explicit | Rejoin as replica first; any later failback uses planned admission |

Both protocols reuse existing election/lease/fencing integration, source
readiness, routing and synchronization. CNCF owns their composition and the
scoped old-work semantics; it does not implement a second consensus algorithm.

## Availability Evidence

Operational projections should include at least:

- current Primary, Read Replica and Standby identities;
- protocol/reason (Switchover or Failover), current Primary admission owner and
  actual control-service ownership/lease status;
- fencing readiness/failure at each required protected boundary;
- admitted CAR/runtime version;
- lifecycle/readiness state;
- cache/warm-up readiness where material;
- per-cache applied progress, lag/gaps and required replacement capacity;
- last successful Primary rotation/takeover;
- last failed rotation/takeover and reason;
- drain status and outstanding bounded work;
- retained old-work completion authority and unresolved update outcomes;
- fallback-retention status and individual unmet retirement conditions;
- durability/resume status for Jobs/Workflows;
- degraded/single-instance operation.

Textus Control Center is the system-level presentation owner. CNCF owns the
runtime/availability semantics and authoritative state.

## Security Relationship

Regular replacement limits the useful lifetime of runtime persistence and
continuously tests reconstruction. It is defense in depth, not proof that an
instance or persistent state is uncompromised.

The same rotation mechanism should support security-triggered replacement.
Security must not add speculative memory-integrity checking or management hashes
where discard/reconstruct is the intended recovery mechanism.

## Declarative Availability Profiles

Availability is a runtime/deployment concern and should not require application
developers to implement Primary/Read Replica orchestration. A Subsystem selects a
small declarative availability profile and optionally overrides bounded policy.

The normal production profile should be selectable simply as availability
profile "standard". It means a Textus-managed Primary and one Standby-capable
Read Replica, with required readiness/warm-up before promotion, graceful drain,
regular rotation, and failure reporting. Exact defaults, including
rotation cadence, are deployment policy and versioned configuration rather than
application code.

An expanded configuration form may express only intentional deviations:
Primary/Read Replica topology, replica read policy and Standby readiness,
rotation interval, required warm-up, and graceful drain.

The configuration describes desired availability behavior, not an imperative
sequence of shell/process operations. CNCF Runtime/Launcher owns realization of
the profile.

Initial profile vocabulary should remain small: a development/single-instance
profile and a standard Primary/Read Replica profile are preferable to many subtly
different modes. Additional profiles require demonstrated operational meaning.

## Unified Replacement Engine

Scheduled rotation, admitted CAR release, unexpected failure/takeover,
infrastructure refresh and security-triggered replacement use the same core
replacement engine and owning control integration as far as their preconditions
allow. The engine has explicit Switchover and Failover paths. Switchover retains
valid scoped completion and fallback policy; Failover fences invalid authority
and reconciles interrupted work without requiring unreachable-node drain.

The trigger/reason and selected protocol are recorded. Reuse established control
and shared readiness/synchronization mechanisms instead of creating a rarely
exercised emergency-only consensus implementation. Sharing an engine does not
permit applying graceful-completion assumptions after authority loss.

## Protocol Qualification and Reference Mechanisms

For full automatic-HA protocol qualification, demonstrate at least: controlled switch with
late A commits; stale routing refusal; exact old-work admission scope; heartbeat
loss and network partition; valid lease loss with enforced fencing; ownership
acquired by an unready candidate; lack of control quorum; confirmed/aborted/unknown
work recovery; multiple read replicas and missing history; and rejoin/failback
without spontaneous re-promotion. Include the commit/effect boundary rather than
only mocked leader names or load-balancer observations. These are future
qualification scenarios, not recorded passing results.

The following established implementations inform the control binding; they do
not provide Textus-specific drain semantics or establish an installed dependency:

- [etcd Election/Lock API](https://etcd.io/docs/v3.6/dev-guide/api_concurrency_reference_v3/):
  lease-bound election, leadership observation and guards for etcd transactions.
- [etcd Lease API](https://etcd.io/docs/v3.6/learning/api/#lease-api):
  lease lifetime, renewal and key expiry.
- [etcd quorum/failure tolerance](https://etcd.io/docs/v3.7/faq/#what-is-failure-tolerance):
  control-voter majority and failure-domain considerations.
- [Kubernetes leader-election implementation](https://github.com/kubernetes/client-go/blob/master/tools/leaderelection/leaderelection.go):
  leader election alone explicitly does not guarantee fencing.
- [Patroni watchdog design](https://patroni.readthedocs.io/en/latest/watchdog.html):
  an example of runtime isolation when leader ownership cannot be maintained,
  not a PostgreSQL-specific implementation to copy into CNCF blindly.

Exact control-provider selection, lease/failure-detection parameters, fencing
binding, durable old-work authority representation and recovery/loss policy
remain implementation/deployment contracts. Unsupported bindings must not be
presented as qualified automatic HA through a configuration label alone.

## Implementation Priority: Operational Immutable Replacement

The highest implementation priority within this HA work is the minimum usable
immutable-infrastructure path. Deliver controlled planned replacement before
attempting the full automatic-failure-takeover feature set. The architectural
Failover contract remains in force; deferring its automation does not permit
stale-authority writes or unsafe promotion.

The first operational delivery includes:

1. A Primary and one Standby-capable Read Replica, with a transient replacement
   replica C. Prove the first physical route with the existing local-ha/Compose
   direction and state its actual failure boundary.
2. Reconstructable runtime/cache state from admitted immutable CAR/configuration
   and authoritative persistent state; qualify compatibility and readiness.
3. The selected existing coordination binding plus enforceable new-admission
   and scoped completion authority. Prove refusal on authority loss, even before
   automatic Failover is offered. No live write path may rely on routing alone.
4. Planned Switchover while A finishes admitted updates and B accepts new work.
   Supply the common cache mechanism needed for A/B/C, including late commits,
   update/delete/invalidation/purge, applied progress, gaps and baseline catch-up.
   Design membership for multiple Read Replicas without making large-topology
   qualification a prerequisite for this bounded delivery.
5. Launch/warm the replacement Read Replica, retain the former Primary in sync,
   perform admitted switch-back, and retire it only when all planned conditions
   hold. Preserve durable work outcomes; do not claim unsupported Running-task
   migration or replay external effects to make replacement appear successful.
6. Explicit operator-triggered replacement and configurable scheduled rotation
   through the same engine, plus replacement by an explicitly selected compatible
   CAR. Do not require automatic release discovery or a fixed daily interval.
7. Observable role, authority/readiness, source/cache progress, drain, retention,
   switch-back and retirement outcomes sufficient for actual operation and
   diagnosis. A polished Control Center screen is a later consumer delivery,
   not a substitute for or a prerequisite to those native observations.

Demonstrate the complete sequence against real separate runtime instances and
authoritative state: an update spans the admission switch, its late commit
reaches B/C, the old instance stays until its conditions are satisfied, and an
admitted switch-back sees current state. Repeat rotation and compatible-CAR
replacement. Include failed readiness, synchronization gaps, authority loss,
drain/retention refusal and uncertain outcomes; liveness and mocks alone do not
prove the first operational route.

Automatic candidate takeover after unexpected Primary loss, richer partition
recovery policy and automatic release discovery/rollout follow the first route.
Until those are qualified, report automatic Failover unavailable and enforce the
defined safe/degraded behavior. Multiple-replica scale, additional deployment
drivers, host-failure qualification and future site-level extensions do not
block first local-ha planned-replacement acceptance. They retain their own
declared failure boundaries and must not be advertised from that first result.

This is delivery ordering, not withdrawal of safety requirements or a claim that
the implementation already exists. [Phase 108](../phase/phase-108.md) records
the open work and separates first operational acceptance from broader delivery.

## Failure Boundary and Cost Decision

The [Availability and Immutable Infrastructure technical guide](../../../../dev2026/textus-technical-documentation/src/main/smartdox/technical-guide/availability/textus-cncf-availability-architecture.dox/README.md)
explains this intended architecture for readers. The
[operating-model notes](../notes/immutable-infrastructure-operating-model.md)
connect the agreed decisions and source documents. Neither the guide nor its
generation/inspection status establishes implemented runtime capability.

Availability is selected against an explicit failure boundary and business
recovery requirement, not by assuming every production system needs the largest
topology.

Initial profiles are single, local-ha and host-ha. Single permits planned
downtime/rebuild. local-ha runs Primary and Standby on one host and protects
against application/runtime failure, bad runtime state and planned immutable
replacement, but not host/OS/power loss. host-ha places the roles on separate
hosts and adds application-host failure tolerance.

local-ha is intentionally useful for ordinary business systems whose main need
is protection from application/runtime defects and zero-downtime maintenance,
while accepting host recovery time for lower operational cost.

Runtime host HA does not imply system-wide host HA. PostgreSQL/DataStore,
messaging, storage, identity/secrets and other stateful dependencies require
compatible availability before the complete system can claim host-failure
tolerance. Control Center should present achieved failure boundaries by layer
rather than a single misleading HA badge.

## Current Scope and Multi-AZ Boundary

Current Textus scope covers process/runtime failure, application defects,
zero-downtime immutable replacement, regular Primary/Standby rotation and,
where selected, separate-host failover.

Multi-AZ/site availability is a future consideration, not a current platform
promise or Phase 108 requirement. Meaningful site-failure architecture must
include DataStore replication, messaging, storage, network/routing,
identity/secrets and consistency/partition policy. Starting a second Domain
Subsystem in another AZ alone is not multi-AZ availability.

The Primary/Read Replica abstraction remains extensible for future experience, but
current implementation must not introduce distributed-consensus, cross-region
or global active-active complexity speculatively.

## Operational Safety by Construction

Textus treats sophisticated availability/release practice as a platform
capability rather than an application-specific SRE procedure. The ordinary
production path should be safe by construction from a small declarative policy.

A Subsystem should be able to request the standard availability/release behavior
without implementing Primary/Standby switching, deployment scripts, rollback
scripts or bespoke health orchestration. Conceptually, a standard availability
profile plus an automatic-compatible release policy and a fallback-retention
policy is sufficient.

This is the operational counterpart of Safe-by-Construction Application
Security: common safety must come from framework/runtime structure first, with
application-specific escape or override only where necessary.

## Deployment Transaction

A compatible immutable release is treated as a long-running Deployment
Transaction:

```text
Admit
  -> Create/Warm candidate
  -> Probe
  -> Switch
  -> Observe during Fallback Retention Window
       -> anomaly: Switch-back
       -> healthy: Commit deployment
  -> Retire/rebuild old runtime
```

The switch is not the deployment commit. During the observation window the new
runtime is probationary Primary and the former Primary remains fallback-ready.

The fallback instance is valuable because it is not merely a stored old image:
it is the runtime that recently served real production traffic with known
configuration, connectivity and persistent-state access.

A switch-back reactivates that known runtime. It does not restore arbitrary
files, reverse an in-place patch or reconstruct a speculative rollback snapshot.

## Fallback Retention Window

The retention duration is deployment policy. Approximately one business day
(24 hours) is a useful standard-profile default candidate because it allows the
new release to encounter normal production workload before the previous Primary
is retired. It is not a hard CNCF constant.

During the window, the platform observes bounded operational signals such as
Operation failures/error rate, latency, Job/Workflow failures, DataStore errors,
authorization/security anomalies and declared application health probes.

Automatic switch-back policy must be bounded and explicit; ambiguous business
correctness remains a human/operational decision rather than an invented
generic metric.

## Data Compatibility During Fallback

Fallback is meaningful only while the retained CAR can still operate against
the authoritative persistent state. Destructive/incompatible DataStore contract
changes therefore cannot be committed merely because traffic switched.

Schema/data evolution should support an expand/migrate/contract style where
appropriate:

```text
expand to old+new compatible state
 -> switch new CAR
 -> fallback retention / observation
 -> deployment commit
 -> contract old compatibility when explicitly safe
```

The exact DataStore migration mechanism is owned by the DataStore architecture
and Phase 100/follow-up work. Availability Architecture owns the invariant that
a release must not claim switch-back capability after making the retained
runtime incompatible with authoritative state.

## CAR Release Operations

CAR publication is not automatic deployment authority. A release progresses
through compatibility/admission and deployment policy before the Replacement
Engine acts.

A useful policy vocabulary includes manual, notify and automatic-compatible.
The automatic-compatible policy may automatically deploy only when required
runtime, configuration, dependency and DataStore/switch-back compatibility
evidence is admitted. Breaking or migration-sensitive changes require an
explicitly stronger decision/human approval.

Scheduled rotation and CAR-version deployment deliberately share the same
switch/drain machinery; the difference is that a release rotation changes the
admitted CAR version.

## Normal Update and Maintenance Update

Availability Architecture adopts [DataStore ABI](datastore-abi.md) as the
simplifying boundary for release operations.

### Normal Update

Normal Update is the everyday release path. It MUST preserve the DataStore ABI
needed by the retained fallback CAR for the complete Fallback Retention Window.

A Normal Update is eligible for automatic-compatible policy and the ordinary
Deployment Transaction:

```text
compatible CAR -> Admit -> Warm/Probe -> Switch
 -> Fallback Retention -> Commit -> Retire
```

Because persistent backward compatibility is an admission invariant, the normal
path does not need generic reverse-schema migration, database rollback or
per-release expand/contract orchestration merely to support switch-back.

### Maintenance Update

A release that cannot preserve the required DataStore ABI is classified as a
Maintenance Update before ordinary automatic deployment.

Maintenance Update is a separate operation with an explicit migration contract.
It may require human approval, maintenance window, backup/recovery preparation,
dedicated migration validation and downtime. Ordinary fallback-ready semantics
must not be advertised unless that specific migration actually provides them.

After maintenance establishes the new DataStore ABI baseline, later compatible
releases return to Normal Update.

### Deliberate simplification

Textus deliberately prefers a strong backward-compatibility constraint on daily
persistent evolution over a platform that attempts to make arbitrary breaking
database changes transparently zero-downtime.

This keeps the standard availability/release engine small, routinely exercised
and understandable. Exceptional complexity is paid only by the exceptional
breaking update that requires it.
