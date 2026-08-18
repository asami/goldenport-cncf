# Phase 71 - Entity Conflict Resolution and Repair

status=planned
planned_at=2026-08-19
depends_on=[Phase 50](phase-50.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 71 Checklist](phase-71-checklist.md)
source_journals=[SimpleEntity revision OCC consideration](../journal/2026/07/2026-07-24-simpleentity-revision-occ-consideration.md) and [Phase 49 conflict consideration](../journal/2026/07/2026-07-24-phase-49-entity-conflict-conditional-transition-consideration.md)

## Phase Plan Gate

Phase Plan Gate: HOLD
- entry evidence: one operator-facing application must supply a real stale or
  conflicting Entity/Aggregate case and its authorization owner.
- reason: Phase 49/50 establish the conditional-transition and optimistic
  concurrency baseline, but no accepted driver yet fixes the merge vocabulary
  or privileged repair policy.
- planning target after entry: split the admitted vertical slice into stages
  with a conservative upper bound of six hours each.

## Purpose

Provide an explicit, authorized, auditable conflict inspection and repair
boundary for an Entity or Aggregate after ordinary optimistic concurrency has
returned a structured stale-conflict result. Ordinary create, save, update, and
patch behavior must never silently become force overwrite or last-write-wins.

## Selected Direction

- One admitted application declares its conflict classes, merge policy, and
  privileged operator capabilities; CNCF does not infer a universal field
  merge.
- Inspect, preview, merge, force, and repair are separate authorized
  Operations with distinct audit, reason, actor, before/after revision, and
  CallTree evidence.
- A destructive repair is planned and previewed against an authoritative
  expected revision before it may commit through the normal UnitOfWork and
  persistence boundary.
- The authoritative commit reconciles affected WorkingSet and View state
  through CNCF-owned invalidation/rebuild policy; no application code mutates
  a datastore, transaction, or cache directly.
- Web/API presentation projects bounded conflict facts and operator actions;
  it never exposes raw persisted records, provider handles, or secret values.

## Scope after Gate Opens

1. Freeze conflict identity, authorization, merge-policy, preview, audit,
   observability, failure, and reconciliation contracts with failing-first
   executable specifications.
2. Implement bounded inspect and preview Operations using the existing
   conditional-transition and revision boundaries.
3. Implement explicitly authorized application-declared merge, force, and
   repair paths with authoritative commit and WorkingSet/View reconciliation.
4. Prove one operator-facing Web/API driver, hostile authorization cases,
   replay/idempotency behavior, audit/redaction, and rollback/recovery.
5. Promote accepted behavior, validate, review, and close without claiming
   distributed consensus or a universal merge engine.

## Non-goals

- Automatic field-level merge, hidden last-write-wins, or a generic merge UI
  without an application driver.
- Direct SQL/JDBC, datastore, transaction, or cache mutation from an
  application/operator path.
- Distributed consensus, leases, fencing, multi-region conflict resolution, or
  Saga coordination.
- Replacing Phase 50 ordinary optimistic concurrency semantics.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ECR71-01 | Driver and contract freeze | One application conflict class, authorization owner, policy vocabulary, and failing-first evidence are accepted. | blocked by gate |
| ECR71-02 | Inspection and preview | Bounded conflict comparison and dry-run repair planning are authorized, redacted, and observable. | blocked by ECR71-01 |
| ECR71-03 | Repair execution and reconciliation | Application-declared merge/force/repair commits through UnitOfWork and reconciles read state. | blocked by ECR71-02 |
| ECR71-04 | Operator acceptance and closure | Web/API, hostile authorization, recovery, validation, review, and documentation evidence converge. | blocked by ECR71-03 |

## Acceptance

- An ordinary stale update remains a structured conflict and never writes
  through by default.
- Only an admitted authorized Operation can inspect, preview, merge, force, or
  repair a conflict.
- A successful repair binds one expected revision, actor, reason, policy,
  before/after identity, and correlated audit evidence.
- Rejected, failed, or rolled-back repairs produce no authoritative state or
  read-side mutation.
- One application driver proves its policy without making that policy generic
  CNCF behavior.

## Planning References

- [Phase 71 Checklist](phase-71-checklist.md)
- [Strategy 9.40](../strategy/cncf-development-strategy.md)
- [Phase 49](phase-49.md)
- [Phase 50](phase-50.md)
- `docs/design/entity-conflict-and-conditional-transition.md`
- `docs/spec/entity-conflict-and-conditional-transition.md`
