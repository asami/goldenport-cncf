# Phase 69.4 Checklist - Lightweight JobDefinition Lifecycle

status=closed
phase=[Phase 69.4](phase-69.4.md)

Phase 69.3 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-06: Lightweight JobDefinition Lifecycle

Stage Status:
- Current status: DONE
- Owner: CNCF JobDefinition model and application/admin maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.3 is CLOSED.
- Completion rule: Authored and reconstructed definitions have a direct lifecycle and running Jobs retain immutable meaning.

- [x] Define direct `create`, `update`, `activate`, `retire`, `get`, and `search` semantics, including immutable submitted snapshots.
- [x] Keep the EntityStore-owned entity revision as the sole optimistic-concurrency mechanism; direct store records and submitted snapshots carry no domain version, revision, or hash, and ordinary requests require no optimistic-lock token.
- [x] Preserve distinct direct keys such as `a-b` and `a_b`; defer EntityId/UniversalId correction to Phase 74.
- [x] Validate the current lightweight repair with the focused JobControl lifecycle and DurableJobProjection specifications. `P69.4-JM69-06-PHASE-TEST-FIX-001-FOCUSED-VAL-002` passed both specifications on the current repair tree (`sbt_exit=0`, shared lock released); the earlier `P69.4-JM69-06-LIGHTWEIGHT-VAL-001` result remains non-evidence because it predates direct-domain-metadata removal.
- [x] Complete an independent focused review, release commit, and frozen Phase 69.5 handoff.

Evidence:
- The lifecycle contract is recorded in
  `docs/spec/job-definition-governance-contract.md`. Governance accept/apply,
  review, promotion, rollout, rollback, audit, and content-history work is
  deliberately retired from the Phase 69 sequence; a future need requires a
  newly authorized Phase rather than an implicit successor.

## Phase Completion Gate

- [x] JM69-06 is DONE with lightweight immutable definition lifecycle evidence.
- [x] Phase 69.5 receives the frozen lightweight handoff.

## Contract Navigation and Evidence Erratum — 2026-09-28

The canonical contract now resides at
[JobDefinition Lightweight Lifecycle Contract](../spec/job-definition-lifecycle-contract.md).
The former governance filename above remains a compatibility link.

The accepted checklist already cites
`P69.4-JM69-06-PHASE-TEST-FIX-001-FOCUSED-VAL-002`. The obsolete
`P69.4-JM69-06C-VAL-008` reference described by the Hygiene follow-up is
absent from this current ledger. Historical `VAL-016` evidence does not replace
the accepted lightweight repair evidence. This erratum changes neither the
closed Phase status nor the original acceptance claims.
