# Phase 69.3 Checklist - Executable JCL Runtime

status=in_progress
phase=[Phase 69.3](phase-69.3.md)

Phase 69.2 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-05: Executable JCL Runtime

Stage Status:
- Current status: IN_PROGRESS
- Current step: JM69-05G compatibility repair is awaiting focused validation and lightweight Step review.
- Owner: CNCF JCL, JobDefinition, Action, Event, Operation, Task, Workflow, scheduler, and security maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.2 is CLOSED.
- Completion rule: Closed procedural and Event-driven JCL executes deterministically through existing runtime authorities.

- [ ] Freeze executable grammar and typed model for `flow`, `events`, and `onEvent` while retaining diagnostics-only `profile` semantics.
- [ ] Define identities, sequencing, conditions, continuation, terminal outcomes, retry, timeout, cancellation, bounded iteration, and the JCL/Workflow ownership boundary.
- [ ] Route every node through Action/Event/Operation/Task/Job, authorization, UnitOfWork, execution-profile, and observability contracts.
- [ ] Define correlation, causation, replay, duplicate delivery, same/new-Job continuation, and required/possible/forbidden Event expectations.
- [ ] Reject cyclic/unbounded, unknown, incompatible, ambiguous, unsupported, and script-like content before execution; persist position and continuation through durable records.
- [ ] Preserve immutable accepted JobDefinition snapshots and add positive, negative, determinism, replay, retry, cancellation, restart, and no-bypass specifications.

Evidence:
- JM69-05G defines a local versioned JobDefinition direct-ID plus exact-key
  legacy fallback, safe YAML construction, and typed Action/Workflow target
  validation. Parent-owned focused specification runs and fresh review remain
  pending; this checklist does not mark the Step or Phase complete.

## Phase Completion Gate

- [ ] JM69-05 is DONE with immutable executable definition and deterministic runtime evidence.
- [ ] Phase 69.4 receives the frozen JCL and definition-snapshot handoff.
