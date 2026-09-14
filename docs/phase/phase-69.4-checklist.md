# Phase 69.4 Checklist - Lightweight JobDefinition Lifecycle

status=in_progress
phase=[Phase 69.4](phase-69.4.md)

Phase 69.3 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-06: Lightweight JobDefinition Lifecycle

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF JobDefinition model and application/admin maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.3 is CLOSED.
- Completion rule: Authored and reconstructed definitions have a direct lifecycle and running Jobs retain immutable meaning.

- [x] Define direct `create`, `update`, `activate`, `retire`, `get`, and `search` semantics, including immutable submitted snapshots.
- [x] Keep the EntityStore-owned entity revision as the sole optimistic-concurrency mechanism; do not require it in ordinary requests or add `contentRevision`.
- [x] Preserve distinct direct keys such as `a-b` and `a_b`; defer EntityId/UniversalId correction to Phase 74.
- [x] Validate direct lifecycle and immutable submitted snapshots with the focused JobControl specification (22/22 passed under `P69.4-JM69-06-LIGHTWEIGHT-VAL-001`; receipt `7aa5f2cb1770abba6229403386217be65a0aa174fe66404a198243fd6af3aa35`).
- [ ] Complete an independent focused review, release commit, and frozen Phase 69.5 handoff.

Evidence:
- The lifecycle contract is recorded in
  `docs/spec/job-definition-governance-contract.md`. Earlier governance-only
  implementation and validation evidence is superseded by this replan and is
  not acceptance evidence for the lightweight contract.

## Phase Completion Gate

- [ ] JM69-06 is DONE with lightweight immutable definition lifecycle evidence.
- [ ] Phase 69.5 receives the frozen lightweight handoff.
