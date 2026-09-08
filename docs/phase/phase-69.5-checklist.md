# Phase 69.5 Checklist - CompositeQuery v2

status=planned
phase=[Phase 69.5](phase-69.5.md)

Phase 69.4 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-07: CompositeQuery v2

Stage Status:
- Current status: OPEN
- Owner: CNCF CompositeQuery, App/Domain protocol, subsystem, scheduler, cancellation, and observability maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.4 is CLOSED.
- Completion rule: Bounded parallel and cross-subsystem query composition is deterministic, secure, cancellable, and diagnosable.

- [ ] Inventory CompositeQuery v1/page-view consumers without moving presentation composition into Domain logic.
- [ ] Define typed branch, dependency, ordering, timeout, cancellation, partial-failure, fallback, aggregate-result, and cross-subsystem protocol semantics.
- [ ] Preserve Component/Subsystem ownership, authorization, subject, tenant, trace context, bounded CNCF scheduling, resource limits, and deterministic output ordering.
- [ ] Define Ephemeral/Persistent Job policy and bounded redacted branch outcome, latency, cancellation, and partial-failure diagnostics.
- [ ] Add sequential/parallel equivalence, dependency ordering, cancellation, timeout, partial failure, authorization, cross-subsystem, bound, and deterministic-result specifications.

Evidence:
- Pending.

## Phase Completion Gate

- [ ] JM69-07 is DONE with bounded deterministic CompositeQuery v2 evidence.
- [ ] Phase 69.6 receives the frozen composition handoff.
