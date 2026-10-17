# Textus Availability Architecture

status=initial
authority=canonical
updated_at=2026-10-14
runtime=[Subsystem Runtime Architecture](subsystem-runtime-architecture.md)
security=[Textus Security Architecture](textus-security-architecture.md)

## Purpose

This document is the canonical Textus availability architecture. Availability
is continuously exercised during normal operation rather than reserved for a
rare disaster path.

## Principle: Exercise the Recovery Path

A Standby that has only reported process health for months is weak evidence of
availability. Textus therefore supports regular Active/Standby rotation as a
normal operating practice.

A deployment may choose a daily cadence:

```text
Day 1: A ACTIVE  / B STANDBY
Day 2: B ACTIVE  / A STANDBY
Day 3: A ACTIVE  / B STANDBY
```

The exact cadence is deployment policy, not a CNCF constant. The architectural
requirement is that planned rotation uses substantially the same readiness,
switch, drain and takeover path required during real failover.

A successful recent rotation is stronger evidence than a Standby that has never
served as Active.

## Zero-Downtime Replacement

Planned replacement proceeds through:

1. create/start the candidate runtime from admitted CAR/configuration;
2. connect authoritative persistent state;
3. warm reconstructable memory/cache;
4. pass readiness and admission checks;
5. atomically/consistently route new authoritative work to the candidate;
6. stop new work on the former Active;
7. drain bounded in-flight synchronous work;
8. continue/resume durable Jobs/Workflows through durable CNCF semantics;
9. terminate/discard the former runtime;
10. recreate it as the next Standby when policy requires.

Availability must not depend on copying arbitrary process memory from old to new.

## Functional Use of Standby

Standby capacity need not be idle. It may serve:

- read-only queries whose consistency contract permits it;
- cache/read projection workloads;
- warm-up and readiness verification;
- operational diagnostics that do not mutate domain state.

Standby mutation is prohibited until takeover/admission grants Active authority.

## Failure and Takeover

Unexpected Active failure promotes a READY/takeover-capable Standby through the
same authority transition used by planned rotation as far as practical.
Availability design should minimize a separate emergency-only code path.

If no instance satisfies readiness/admission, the system reports degraded or
unavailable state rather than granting mutation authority to an unqualified
instance merely to remain nominally up.

## Availability Evidence

Operational projections should include at least:

- current Active and Standby identities;
- admitted CAR/runtime version;
- lifecycle/readiness state;
- cache/warm-up readiness where material;
- last successful Active rotation/takeover;
- last failed rotation/takeover and reason;
- drain status and outstanding bounded work;
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
developers to implement Active/Standby orchestration. A Subsystem selects a
small declarative availability profile and optionally overrides bounded policy.

The normal production profile should be selectable simply as availability
profile "standard". It means a Textus-managed Active/Standby pair with a
read-only-capable Standby, required readiness/warm-up before promotion, graceful
drain, regular rotation, and failure reporting. Exact defaults, including
rotation cadence, are deployment policy and versioned configuration rather than
application code.

An expanded configuration form may express only intentional deviations:
active-standby profile, Standby read-only query policy, rotation interval,
required warm-up, and graceful drain.

The configuration describes desired availability behavior, not an imperative
sequence of shell/process operations. CNCF Runtime/Launcher owns realization of
the profile.

Initial profile vocabulary should remain small: a development/single-instance
profile and a standard Active/Standby profile are preferable to many subtly
different modes. Additional profiles require demonstrated operational meaning.

## Unified Replacement Engine

Scheduled rotation, admitted CAR release, unexpected failure/takeover,
infrastructure refresh and security-triggered replacement use the same core
replacement state machine as far as their preconditions allow.

The trigger/reason is recorded separately from the replacement mechanism. This
avoids maintaining a rarely exercised emergency-only failover implementation.

## Failure Boundary and Cost Decision

Availability is selected against an explicit failure boundary and business
recovery requirement, not by assuming every production system needs the largest
topology.

Initial profiles are single, local-ha and host-ha. Single permits planned
downtime/rebuild. local-ha runs Active and Standby on one host and protects
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
zero-downtime immutable replacement, regular Active/Standby rotation and,
where selected, separate-host failover.

Multi-AZ/site availability is a future consideration, not a current platform
promise or Phase 108 requirement. Meaningful site-failure architecture must
include DataStore replication, messaging, storage, network/routing,
identity/secrets and consistency/partition policy. Starting a second Domain
Subsystem in another AZ alone is not multi-AZ availability.

The Active/Standby abstraction remains extensible for future experience, but
current implementation must not introduce distributed-consensus, cross-region
or global active-active complexity speculatively.

## Operational Safety by Construction

Textus treats sophisticated availability/release practice as a platform
capability rather than an application-specific SRE procedure. The ordinary
production path should be safe by construction from a small declarative policy.

A Subsystem should be able to request the standard availability/release behavior
without implementing Active/Standby switching, deployment scripts, rollback
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
runtime is probationary Active and the former Active remains fallback-ready.

The fallback instance is valuable because it is not merely a stored old image:
it is the runtime that recently served real production traffic with known
configuration, connectivity and persistent-state access.

A switch-back reactivates that known runtime. It does not restore arbitrary
files, reverse an in-place patch or reconstruct a speculative rollback snapshot.

## Fallback Retention Window

The retention duration is deployment policy. Approximately one business day
(24 hours) is a useful standard-profile default candidate because it allows the
new release to encounter normal production workload before the previous Active
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
