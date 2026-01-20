# Stage Status and Checklist Convention

This document defines the mandatory convention for Stage Status blocks and
checklist-based closure in CNCF documentation.

## Purpose

- Make current work status deterministic and discoverable.
- Define a single, canonical rule for Stage status reporting.
- Ensure that DONE/CLOSED states are derived from explicit checklists.

## Required Placement

- Each Stage section MUST contain a Stage Status block.
- The Stage Status block MUST appear immediately after the Stage header.

## Stage Status Block (Mandatory)

Stage Status:
- Current status: <OPEN | IN_PROGRESS | DONE | CLOSED>
- Owner: <human or team label>
- Update rule: <when and how this block must be updated>

### Checklist Notation Rules

The checklist used in phase and hygiene documents is a **state ledger**, not a task board.

The following notations are permitted:

- [x] **DONE / DECIDED**
  - The item is completed within the current phase scope, **or**
    the item is **decided** in the current phase as a *Future Development Candidate*.
  - If marked as a Future Development Candidate, the checklist line MUST include:
    - An explicit marker such as `(Future Development Candidate)`
    - A reference to the candidate list number, e.g. `DP-03`
  - No further work on this item is allowed in the same phase.

- [ ] **OPEN**
  - The item is identified and in scope for the current phase.
  - No implementation work has started yet.

Additional rules:

- Checklist entries must always represent a **stable state**.
- Checklist entries must not encode narrative intent such as “next phase assumption” or “future expectation”.
- Items not in scope for the current phase must not be represented as OPEN.
- No checklist entry is ever deleted; state transitions are explicit.
- Once an item is added to a checklist, it MUST NOT be deleted.
  Reduction is allowed only by consolidation/merging into another explicit item.
- Future Development Candidates MUST remain in the checklist.
  They are closed by marking the item as `[x]` with the required candidate marker and `DP-xx` reference.

RATIONALE:
This rule prevents ambiguity between unfinished work and intentionally deferred work,
and provides a stable reference model for both human maintainers and AI agents.

## Checklist Relationship (Mandatory)

- Each Stage MUST include at least one explicit checklist.
- The Stage Status block MUST reference the checklist as the closure basis.
- A Stage is DONE or CLOSED only when all checklist items are checked.
- If any checklist item is unchecked, the Stage MUST NOT be marked DONE or CLOSED.

## DONE / CLOSED Determination Rule

- DONE: all required checklist items are checked and no outstanding items remain.
- CLOSED: DONE plus any deferred items have explicit relocation targets outside
  the Stage.

Optional Progress Detail
----------------------------------------------------------------------
In addition to the mandatory `Current status` field, a Stage MAY include
an optional `Current step` field.

Rules:
- `Current status` MUST contain only a stable state name
  (e.g. Work in progress, DONE, CLOSED, BLOCKED).
- `Current step` MAY describe the active checklist step or sub-step.
- Completion judgment MUST be based on checklist state, not on
  `Current step` text.

## Example (Stage 5 / Stage 6 Style)

Stage Status:
- Current status: CLOSED
- Owner: Phase 2.6 demo completion
- Update rule: Update when all Stage checklists are fully checked and deferred
  items are relocated.

Checklist:
- [x] Evidence (verified): command list is documented
- [x] Evidence (verified): expected outputs are documented
- [x] Evidence (verified): reproduction steps are runnable

If any item above is unchecked, the Stage status MUST NOT be DONE or CLOSED.
