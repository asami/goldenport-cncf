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
an identifier. Creation persists one typed `{ id, key, ... }` entity, with
`id` as its `JobDefinitionId`; that stored entity is the sole key-to-ID
correspondence authority. Exact-key search finds the saved entity and uses its
stored `JobDefinitionId`, and updates retain that identity. In particular,
`a-b` and `a_b` are distinct keys. This is the implemented lightweight
UniversalId/EntityId contract; no key-derived ID, hash, companion integrity
field, or dedicated key-to-ID correspondence table is needed.

EntityStore owns the entity `revision`. The service reads a snapshot and uses
that revision only for its internal conditional save, preventing a stale
internal read from silently overwriting a newer entity. Direct JobDefinition
store records and submitted snapshots carry no domain version, revision, or
hash. Normal lifecycle requests do not carry an expected revision. If a future
UI needs edit-conflict handling, it can opt into the entity revision as its
optimistic-lock token through a separately designed API; it does not need a
second domain revision or `contentRevision`.

## Data representation

This Phase adds no digest, canonical-content, or hash-based control. Direct
JobDefinition store records and submitted snapshots carry no legacy domain
version, revision, or hash exception. Governance accept/apply, review,
promotion, rollout, rollback, audit, and content-history work is deliberately
retired from the Phase 69 sequence; a future need requires newly authorized
Phase work rather than an implicit successor.

## Validation boundary

JM69-06 runs focused JobControl lifecycle and immutable-snapshot tests. The
repository full suite is owned by aggregate Phase 69.7.
