# Phase 71 Checklist - Entity Conflict Resolution and Repair

status=planned
phase=[Phase 71 - Entity Conflict Resolution and Repair](phase-71.md)
candidate_journal=[SimpleEntity revision OCC consideration](../journal/2026/07/2026-07-24-simpleentity-revision-occ-consideration.md)

This checklist is the authoritative Phase 71 state ledger while the Phase Plan
Gate is `HOLD`. It records the blocked entry state and becomes the resume point
when a driver is admitted. Only one stage may be `IN_PROGRESS` at a time. No
checklist item authorizes a generic merge engine, a direct persistence
workaround, or distributed conflict coordination.

## ECR71-01: Driver and Contract Freeze

Stage Status:
- Current status: BLOCKED
- Owner: CNCF Entity/Aggregate, authorization, UnitOfWork, Web/API, and first
  operator-facing application maintainers
- Update rule: Update only from accepted driver, contract, executable-specification,
  or review evidence; retain `BLOCKED` until the entry rule is proven.
- Entry rule: a real stale/conflicting Entity/Aggregate case, its application
  owner, and its authorization policy are admitted; Phase 50 remains closed.
- Completion rule: the conflict vocabulary, policy extension, authorization,
  preview, audit, redaction, reconciliation, and failing-first acceptance
  contracts are frozen.

- [ ] Record the first driver and prove why ordinary optimistic concurrency is
      insufficient for its privileged resolution path.
- [ ] Freeze inspect, preview, merge, force, and repair authority boundaries.
- [ ] Freeze application-declared merge-policy identity, version, limits, and
      unsupported-policy behavior without defining a universal field merge.
- [ ] Freeze expected-revision, idempotency, rollback, audit, redaction,
      WorkingSet/View reconciliation, and structured failure semantics.
- [ ] Register failing-first executable specifications and one hostile
      authorization matrix.

Evidence:
- Pending gate evidence.

## ECR71-02: Inspection and Preview

Stage Status:
- Current status: BLOCKED
- Owner: CNCF Entity/Aggregate and application-driver maintainers
- Update rule: Update only from accepted ECR71-01 contract and inspection or
  preview evidence.
- Entry rule: ECR71-01 is DONE.
- Completion rule: authorized bounded inspection and dry-run preview expose
  safe conflict facts without mutating authoritative state.

- [ ] Implement authorized conflict inspection and comparison projection.
- [ ] Implement a preview record bound to conflict, expected revision, actor,
      policy, and safe summary.
- [ ] Prove unauthorized, stale-preview, unavailable-policy, and redaction
      outcomes.

Evidence:
- Pending.

## ECR71-03: Repair Execution and Reconciliation

Stage Status:
- Current status: BLOCKED
- Owner: CNCF Entity/Aggregate, UnitOfWork, and application-driver maintainers
- Update rule: Update only from accepted ECR71-02 evidence and execution or
  reconciliation evidence.
- Entry rule: ECR71-02 is DONE.
- Completion rule: admitted repair executes only through the normal CNCF
  commit boundary and reconciles read state safely.

- [ ] Implement explicitly authorized application-declared merge, force, and
      repair Operations.
- [ ] Bind each execution to the preview/expected revision and prevent replay
      or duplicate mutation.
- [ ] Reconcile WorkingSet/View state after commit and preserve rollback
      safety.
- [ ] Prove no direct datastore/cache mutation path is introduced.

Evidence:
- Pending.

## ECR71-04: Operator Acceptance and Closure

Stage Status:
- Current status: BLOCKED
- Owner: CNCF release and first application-driver maintainers
- Update rule: Update only from accepted ECR71-03 evidence and final
  validation, review, or promotion evidence.
- Entry rule: ECR71-03 is DONE.
- Completion rule: operator acceptance, validation, review, and promoted
  contract evidence converge.

- [ ] Prove the first Web/API operator workflow, authorization matrix, audit,
      redaction, replay, rollback, and recovery outcomes.
- [ ] Run focused and full validation in the admitted repositories.
- [ ] Promote accepted design/specification and operator guidance.
- [ ] Complete independent review, bounded repair/re-review, and Phase release
      closure.

Evidence:
- Pending.
