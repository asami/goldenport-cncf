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

## Post-Completion Maintenance Convention

A completed phase will sometimes require a corrective change after its normal
work groups have closed. Such work MUST use a separate Post-Completion
Maintenance ledger instead of reopening, renumbering, or modifying a completed
work group.

### Identity and placement

- The canonical identity is `PM-<phase>-<sequence>`, for example
  `PM-53-01`.
- The sequence starts at `01` independently for each phase.
- A PM section MUST appear after every normal phase work group in the phase
  checklist.
- The phase summary MAY append a Post-Completion Maintenance section, but MUST
  preserve the original completion account as historical evidence.
- Completed work-group checkboxes and evidence MUST NOT be changed to make room
  for maintenance work.

### Admission boundary

Work belongs to PM only when it corrects, hardens, or reconciles behavior that
was delivered or claimed by the completed phase. A new capability, independent
generalization, or previously explicit deferral MUST remain in its owning
future phase.

Each PM item MUST record:

- the discovered contradiction or defect;
- the completed contract or evidence it corrects;
- admitted repositories and target programs;
- explicit exclusions and preserved deferrals;
- executable acceptance and regression evidence; and
- release and closure evidence.

### Status model

The base phase remains historically CLOSED while maintenance is active. Its
dashboard MUST additionally report `Maintenance status: OPEN` and the active PM
identity. The PM item uses the normal stable states `OPEN`, `IN_PROGRESS`,
`DONE`, and `CLOSED`; unchecked maintenance items prohibit PM closure.

### Required workflow

Every PM item follows this order:

1. ADMIT — classify the issue as correction rather than new scope and freeze
   repositories, target programs, exclusions, and acceptance criteria.
2. PLAN — produce a bounded implementation and validation plan.
3. IMPLEMENT — change only the admitted maintenance scope and add executable
   regression evidence.
4. FOCUSED VALIDATE — run compilation and affected executable specifications.
5. REVIEW — perform a clean read-only review of the complete PM diff.
6. REVIEW FIX — when actionable findings exist, fix all admitted findings and
   run a focused clean re-review; skip this step after a clean initial review.
7. FINAL VALIDATE — full-test every repository modified by the PM item and run
   any required cross-repository acceptance.
8. RELEASE COMMIT — update the PM ledger, journal, phase/strategy references,
   version evidence, and create the release commit.
9. CLOSE — mark every PM checklist item checked, set the PM item to CLOSED, and
   restore `Maintenance status: CLOSED` without altering the historical base
   phase completion.

Only one PM item for a phase may be `IN_PROGRESS` at a time. Later corrections
use the next PM sequence and never append unchecked work beneath a CLOSED PM
item.

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
