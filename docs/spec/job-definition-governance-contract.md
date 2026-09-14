# JobDefinition Lightweight Lifecycle Contract

This document records the deliberately small JobDefinition contract for
`JM69-06` in [Phase 69.4](../phase/phase-69.4.md). It is an operational
definition-management API, not a review or publication system.

## Lifecycle

The public operations are `create`, `update`, `activate`, `retire`, `get`, and
`search`. `create` accepts an optional initial status; `update` replaces the
definition source and preserves the ordinary entity lifecycle. `activate` and
`retire` change that lifecycle state. A submitted Job carries its own immutable
definition snapshot, so a later update never changes a running or completed
Job.

There are no proposals, reviewers, approvals, rollbacks, audits, migration
provenance, staged rollouts, or alternate content histories in this contract.
Those capabilities require demonstrated operational demand and a separately
planned Phase.

## Identity and concurrency

The user key is trimmed according to the existing policy and remains a key, not
an identifier. In particular, `a-b` and `a_b` are distinct keys. This Phase
does not change the existing direct EntityId approach; its UniversalId/EntityId
contract correction is deferred to Phase 74.

EntityStore owns the entity `revision`. The service reads a snapshot and uses
that revision for its conditional save, preventing a stale internal read from
silently overwriting a newer entity. Normal lifecycle requests do not carry an
expected revision. If a future UI needs edit-conflict handling, it can opt into
the entity revision as its optimistic-lock token through a separately designed
API; it does not need a second domain revision or `contentRevision`.

## Data representation

This Phase adds no digest, canonical-content, or hash-based control. The
pre-existing persisted representation, including any legacy fields it already
contains, is not a concurrency or identity mechanism introduced by JM69-06.

## Validation boundary

JM69-06 runs focused JobControl lifecycle and immutable-snapshot tests. The
repository full suite is owned by aggregate Phase 69.7.
