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
