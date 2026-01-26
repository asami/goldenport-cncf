# Phase 3.1 — Execution Hub Foundation Checklist

This document contains **detailed task tracking and execution decisions**
for Phase 3.1 (Execution / Orchestration Hub Foundation).

It mirrors the structure, semantics, and closure rules of the
Phase 2.85 checklist to ensure continuity of workflow and judgment.

---

## Checklist Usage Rules

- This document holds **detailed status and task breakdowns**.
- The phase document (`phase-3.md`) holds **summary only**.
- A development item marked DONE here must also be marked `[x]`
  in the phase summary document.
- Deep reasoning, experiments, and exploration must be recorded
  in journal entries, not inline here.

---

## Checkbox Operation Rules

### Meaning of `[ ]`

- A checkbox `[ ]` indicates that the item is **not yet complete
  in the context of Phase 3.1**.
- `[ ]` may represent one of the following states:
  - BACKLOG
  - PLANNED
  - ACTIVE
  - SUSPENDED

### Meaning of `[x]`

- A checkbox `[x]` indicates that the handling of this item
  **within Phase 3.1 is complete**, regardless of outcome:
  - completed
  - cancelled
  - deferred to a next phase

### Transition Rules

- `[ ] → [x]` is allowed **only once** per item.
- Once an item is marked `[x]`:
  - It must not be modified further in this document
  - No additional tasks may be added under it
  - Any remaining work must move to a next-phase checklist

---

## Status Semantics

- **ACTIVE**  
  Currently being worked on.  
  Only one development item may be ACTIVE at a time.

- **SUSPENDED**  
  Work that was started and intentionally paused.  
  A clear resume point must exist.

- **PLANNED**  
  Planned but not started yet.  
  May be cancelled depending on circumstances.

- **BACKLOG**  
  Kept for consideration without commitment.

- **DONE**  
  Handling of this item **within Phase 3.1 is complete**.

Once marked DONE, the item must not be modified.

---

## EH-01: Fat JAR Component — Baseline Execution Form

Status: ACTIVE

### Objective

Establish **Fat JAR Component** as the baseline execution form
for Phase 3, capable of encapsulating:

- large dependency graphs
- mixed Scala versions
- isolated runtime environments

while remaining operable through CNCF's unified execution model.

---

### Detailed Tasks

- [ ] Define Fat JAR Component boundary and responsibilities
- [ ] Design ClassLoader isolation strategy (parent = null)
- [ ] Define shared API surface between CNCF and Fat JAR
- [ ] Document execution lifecycle (load → invoke → unload)
- [ ] Specify failure modes and Observation mapping

---

### Execution Verification

- [ ] Load Fat JAR Component via CNCF
- [ ] Invoke exactly one Operation successfully
- [ ] Verify isolation from CNCF classpath
- [ ] Verify Observation returned on execution failure

---

### Decisions

- Fat JAR Component is treated as **baseline**, not an edge case
- ClassLoader isolation is mandatory, not optional
- Execution failure must never crash CNCF runtime

---

### Journal Links

- (to be added) Fat JAR loading experiments
- (to be added) ClassLoader behavior notes

---

## EH-02: Execution Hub Responsibility Definition

Status: PLANNED

### Objective

Clarify **what the Execution / Orchestration Hub does and does not do**
before introducing additional component forms.

---

### Detailed Tasks

- [ ] Define responsibilities of Execution Hub
- [ ] Define non-responsibilities explicitly
- [ ] Confirm execution-shape neutrality principle
- [ ] Align with client-only access rule

---

### Notes

- Docker, RPC, and AI Agent concerns are explicitly excluded here

---

## Deferred / Next Phase Candidates

- Docker Component execution (Phase 3.x)
- Antora integration
- CML → Component generation
- AI Agent Hub integration

---

## Completion Check

Phase 3.1 is complete when:

- All ACTIVE items are marked DONE or DEFERRED
- No item remains ACTIVE or SUSPENDED
- Phase summary reflects closure accurately
