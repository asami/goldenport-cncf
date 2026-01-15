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
