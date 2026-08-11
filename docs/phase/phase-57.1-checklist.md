# Phase 57.1 Checklist - Plain Action Direct Execution

status=in-progress
phase=[Phase 57.1 - Plain Action Direct Execution](phase-57.1.md)
predecessor=[Phase 57](phase-57.md)
successor=[Phase 57.2](phase-57.2.md)
completed=AES-01 Inventory and Contract Freeze
next=AES-02 Failing-First Execution Matrix
inventory=[AES-01 action execution inventory](../notes/phase-57.1-aes01-action-execution-inventory.md)
spec=[Action execution semantics](../spec/action-execution-semantics.md)

## AES-01: Inventory and Contract Freeze

Stage Status:
- Current status: DONE
- Owner: Phase 57.1 AES-01
- Update rule: Reopen/update this block only if the inventory or authoritative execution matrix changes; completion basis is all AES-01 checklist items checked.
- Entry rule: Phase 57 is DONE.
- Completion rule: Every affected Action and observable response/job contract
  is recorded.

- [x] Inventory plain `Action`, `QueryAction`, `CommandAction`, and explicit
  Job routes.
- [x] Record direct-response, Job-ID, await, persistence, continuation, and
  observability consumers.
- [x] Freeze plain `Action` as the simplest synchronous route.

## AES-02: Failing-First Execution Matrix

Stage Status:
- Current status: PLANNED
- Owner: Phase 57.1 AES-02
- Update rule: Update this block when failing-first executable coverage changes; do not mark DONE while any AES-02 checklist item is unchecked.
- Entry rule: AES-01 is DONE.
- Completion rule: Direct, query, command, explicit async, context, and exact
  failure behavior are executable.

- [ ] Prove plain direct response and absence of implicit Job creation.
- [ ] Prove query and command behavior remains unchanged.
- [ ] Prove explicit async/job and shared security/context boundaries.

## AES-03: Plain Action Direct Execution

Stage Status:
- Current status: PLANNED
- Owner: Phase 57.1 AES-03
- Update rule: Update this block after the AES-03 runtime repair and focused validation; do not mark DONE while any AES-03 checklist item is unchecked.
- Entry rule: AES-02 is DONE.
- Completion rule: Only the unclassified fallback uses direct synchronous
  execution and focused review accepts the result.

- [ ] Route plain `Action` through direct `ActionCall` execution.
- [ ] Preserve exact response, context, observability, and failure semantics.
- [ ] Run the focused Action/Component validation once.
- [ ] Review once, repair proportionately if needed, and commit the Phase.
