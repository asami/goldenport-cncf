# Phase 69.7 Checklist - Job Operations and Downstream Acceptance

status=planned
phase=[Phase 69.7](phase-69.7.md)

Phase 69.6 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-09: Security, Retention, Observability, and Operations

Stage Status:
- Current status: OPEN
- Owner: CNCF security, persistence, retention, observability, metrics, audit, health, and operations maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.6 is CLOSED.
- Completion rule: Production Job Management remains bounded, recoverable, diagnosable, and safe throughout its retained lifecycle.

- [ ] Define quotas/bounds, retention classes, expiry/deletion, legal hold where applicable, cleanup, tombstones, maintenance scheduling, integrity, backup/restore, migration checkpoints, health/readiness, repair refusal, and escalation.
- [ ] Define redaction/facets and correlation for Job, Task, attempt, Event, Operation, Workflow, definition, trace/span, CallTree, payload, recovery, subject, tenant, credential, provider, and path data.
- [ ] Define admission, queue, execution, retry, recovery, pagination, storage, expiry, corruption, payload, and control metrics.
- [ ] Add load/bound, quota, retention, expiry/deletion, integrity, redaction, audit, health, maintenance, and hostile-access specifications.

Evidence:
- Pending.

## JM69-10: Cross-Process and Downstream Acceptance

Stage Status:
- Current status: OPEN
- Owner: CNCF, representative Textus consumers, documentation, validation, review, and release maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: JM69-09 is DONE.
- Completion rule: The complete Job Management contract passes restart, downstream, review, and release gates before sequence closure.

- [ ] Run real two-process, scheduled/retry, crash-interrupted, cursor, payload, corruption, incompatible-version, migration, JCL, governance, CompositeQuery, user/admin, notification, and security acceptance fixtures.
- [ ] Prove a fresh CBD Support runtime recovers the exact completed Review Job/result without ComponentFactory-local or component-owned shadow state.
- [ ] Run focused compatibility regressions; promote accepted design/spec/guidance; run full CNCF/downstream validation, review, conditional review-fix/re-review, version checks, and release preparation.
- [ ] Record exact commands, counts, process/storage identities, fixtures, artifact/diff identity, limitations, and release commit evidence.

Evidence:
- Pending.

## Phase Completion Gate

- [ ] JM69-09 and JM69-10 are DONE.
- [ ] Every original Phase 69 acceptance statement has executable or exact operational evidence.
- [ ] CBD Support fresh-runtime acceptance, full validation, review convergence, documentation promotion, version evidence, release commit, and clean intended tree are recorded.
