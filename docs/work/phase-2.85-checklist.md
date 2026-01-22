# Phase 2.85 — Development Checklist

This document contains **detailed task tracking and decisions**
for Phase 2.85.

It complements the summary-level phase document (`phase-2.85.md`)
and may be updated freely while the phase is open.

---

## Checklist Usage Rules

- This document holds **detailed status and task breakdowns**.
- The phase document (`phase-2.85.md`) holds **summary only**.
- A development item marked DONE here must also be marked `[x]`
  in the phase document.
- Reasoning, experiments, and deep dives should be recorded
  in journal entries when necessary.

---

## Checkbox Operation Rules

### Meaning of `[ ]`

- A checkbox `[ ]` indicates that the item is **not yet complete
  in the context of this phase**.
- `[ ]` may represent any of the following states:
  - BACKLOG
  - PLANNED
  - ACTIVE
  - SUSPENDED

### Meaning of `[x]`

- A checkbox `[x]` indicates that the handling of this item
  **within this phase is complete**, regardless of outcome:
  - completed
  - cancelled
  - deferred to a next phase

### Transition Rules

- `[ ] → [x]` is allowed **only once** per item.
- Once an item is marked `[x]`:
  - It must not be modified further in this document
  - No additional tasks may be added under it
  - Any remaining work must move to the next phase

---

## Status Semantics

- **ACTIVE**  
  Currently being worked on.  
  Only one development item may be ACTIVE at a time.

- **SUSPENDED**  
  Work that was started and intentionally paused.  
  A clear resume point must exist.

- **PLANNED**  
  Planned to be worked on, but not started yet.  
  May be cancelled depending on circumstances.

- **BACKLOG**  
  Kept as a development item just in case.  
  There is no commitment to work on it.

- **DONE**  
  Handling of this item **within this phase is complete**.

Once marked DONE, the item must not be modified.

---

## DI-01: Minimal sbt project for a CNCF HelloWorld component

Status: DONE

### Objective

Create the **smallest possible sbt-based project**
that defines a CNCF component and can be executed locally.

This project will be used directly in the article demo.

---

### Detailed Tasks

- [x] Create minimal sbt project skeleton
- [x] Define HelloWorld component class
- [x] Define minimal service / operation
- [x] Verify local execution (`sbt run` or equivalent)

---

### Decisions

- Use **sbt** (not scala-cli) for consistency with CNCF examples.
- Avoid abstraction, reuse, or optional features.
- Keep structure flat and readable.

---

### Notes

- Prefer explicit configuration over magic defaults.
- This project is **demo-only**, not a reference architecture.

---

### Journal Links

- (add if needed) `docs/journal/2026/01/YYYY-MM-DD-hello-world-sbt-skeleton.md`

---

## DI-02: Deploy and execute HelloWorld component using existing loader

Status: ACTIVE

### Objective

Verify that the HelloWorld component can be **loaded and executed**
using the existing CNCF component loader **without modifying its design**.

This item explicitly relies on the *existing* CNCF loader behavior as documented
in the journal entry:
`docs/journal/2026/01/2026-01-22-component-loading-discovery-latest.md`.

No new descriptor mechanisms (component.yaml, META-INF descriptors, or
priority rules) are introduced in this phase.

---

### Detailed Tasks

- [ ] Prepare component descriptor
- [ ] Confirm loader discovery
- [ ] Execute component via CLI or server
- [ ] Verify output correctness

---

### Deferred Conditions

If loader changes are required beyond minimal glue code,
this item must be **deferred** and linked to a next-phase item.

---

### Related Next Phase Items

- NP-003: Component lifecycle extension

---

## DI-03: Document reproducible demo execution steps

Status: PLANNED

### Objective

Produce **article-ready, copy-paste-safe**
execution steps for the HelloWorld demo.

---

### Detailed Tasks

- [ ] Decide execution path (CLI or HTTP)
- [ ] Write minimal execution steps
- [ ] Verify steps from a clean environment
- [ ] Align steps with article narrative

---

### Notes

- Assume readers have no prior CNCF knowledge.
- Prefer commands over explanation.
- Avoid optional flags unless required.

---

## Deferred / Out-of-Scope Notes

- Error handling details → NP-001
- OpenAPI representation → NP-002

---

## Completion Check

Phase 2.85 is complete when:

- All development items are marked DONE in this document
- Corresponding checkboxes in `phase-2.85.md` are marked `[x]`
- No development item remains ACTIVE or SUSPENDED
