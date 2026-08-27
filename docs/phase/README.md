# docs/phase — Engineering Work Management

## Purpose

`docs/phase` is a **dedicated directory for engineering work management**.

It records the *current state of work itself*, such as:

- Which phase we are in
- What the current work is
- What is DONE / OPEN / DEFERRED
- Where to resume work next

This directory does **not** contain design details or thinking processes.

Current baseline:

- Latest closed phase: `phase-60.1.md` - ADM-02 Component Admin identity and
  view model. It is closed under `phase60.1-clb-adm02-20260828`; Phase 60.2
  remains planned and is not started by this closure.
- Latest closed Phase 59-series phase: `phase-59.10.md` - DOC-10 canonical
  documentation and Phase 59 series closure. It is closed under
  `phase59.10-clb-doc10-20260827`, consuming the accepted DOC-09 security,
  regression, downstream, and full-suite evidence without changing behavior.
- Phase 59 is fully closed. Phase 60 is closed after ADM-01. Phase 60.1 is
  closed after ADM-02; `phase-60.2.md` through `phase-60.8.md` retain the
  later independently closable ADM-03 through ADM-09 stages.
- Phase 59.5 persists `HYG-P595-PHASE-001` as nonblocking Hygiene and accepts
  no Development Candidate.
- Phase 59.6 persists `HYG-P596-001` as nonblocking Hygiene and
  `DEV-P596-001` as a separately owned resource-tree API candidate.
- Phase 59.7 persists `HYG-P59.7-001` and `DEV-P59.7-001` as separately owned,
  nonblocking follow-ups outside acceptance.
- Phase 59.1 checklist: `phase-59.1-checklist.md` is closed. It persists
  `HYG-DOC01B2-001` as nonblocking Hygiene and accepts no Development
  Candidate.
- Phase 59 checklist: `phase-59-checklist.md` is closed. It persists
  `HYG-P59-001` as nonblocking Hygiene and accepts no Development Candidate.
- Phase 58.9 checklist: `phase-58.9-checklist.md` is closed. It accepted no
  new Phase Hygiene or Development Candidate record.

- Active phase in the current CNCF sequence: none. `phase-60.2.md` is the next
  planned successor and requires a separate explicit start.
- Latest closed checklist: `phase-60.1-checklist.md`.
- Closed phase set currently includes:
  - `phase-4.md`
  - `phase-5.md`
  - `phase-6.md`
  - `phase-7.md`
  - `phase-8.md`
  - `phase-9.md`
  - `phase-10.md`
  - `phase-11.md`
  - `phase-13.md`
  - `phase-14.md`
  - `phase-15.md`
  - `phase-16.md`
  - `phase-17.md`
  - `phase-18.md`
  - `phase-19.md`
  - `phase-20.md`
  - `phase-21.md`
  - `phase-22.md`
  - `phase-23.md`
  - `phase-24.md`
  - `phase-25.md`
  - `phase-26.md`
  - `phase-27.md`
  - `phase-28.md`
  - `phase-29.md`
  - `phase-30.md`
  - `phase-31.md`
  - `phase-32.md`
  - `phase-33.md`
  - `phase-34.md`
  - `phase-35.md`
  - `phase-36.md`
  - `phase-37.md`
  - `phase-38.md`
  - `phase-39.md`
  - `phase-40.md`
  - `phase-41.md`
  - `phase-42.md`
  - `phase-43.md`
  - `phase-44.md`
  - `phase-45.md`
  - `phase-46.md`
  - `phase-47.md`
  - `phase-60.md`
  - `phase-60.1.md`
- Active phase set currently includes:
  - none; `phase-60.2.md` is planned and not started

## Related Rules

This directory operates under the authoritative rules defined in `docs/rules`.

In particular:
- Document boundary rules → `docs/rules/document-boundary.md`
- Stage status and checklist conventions → `docs/rules/stage-status-and-checklist-convention.md`

Documents under `docs/phase` must not redefine or override rules from `docs/rules`;
they may only reference and apply them.

---

## What Belongs Here

The following types of documents may be placed in `docs/phase`:

- Phase overview documents  
  - e.g. `phase-2.85-demo-readiness.md`
- Work checklists  
  - Work stack (A / B / C …)
  - DONE / OPEN / DEFERRED tracking
- Handover documents
- Operational rules for work management

👉 **Only documents that immediately answer “where are we now?” and “what’s next?”**

---

## What Does NOT Belong Here

The following must **not** be placed in `docs/phase`:

- Design drafts or specification proposals → `docs/notes`
- Thinking logs, experiments, trial-and-error → `docs/journal`
- Mid- to long-term strategy or roadmap → `docs/strategy`
- Finalized, authoritative designs/specifications → `docs/design`

`docs/phase` must be kept **intentionally thin**.

---

## Directory Responsibility Map

| Directory | Responsibility |
|---------|----------------|
| docs/strategy | Mid/long-term strategy, roadmap |
| docs/phase | **Work management, progress, phase state** |
| docs/notes | Pre-design specs and design drafts |
| docs/design | Finalized, authoritative designs |
| docs/journal | Thinking logs, experiments, exploration |

---

## Work Model (Important)

All work tracked in `docs/phase` follows these principles:

- Work is managed as a **stack (A / B / C …)**
- Only **one work item may be ACTIVE at any time**
- Interruption and resumption must be explicit
- `close` means the phase is complete as a progress-tracking unit
- A closed phase may still be referenced by later phases, but follow-up work
  must move to a later phase instead of reopening the old one silently

Detailed operational rules are defined at the beginning of each phase document.

---

## Phase Boundary Rule

- Each phase document must define:
  - Purpose
  - Scope / Non-Goals
  - Current work stack / development items
- Once a phase is marked `close`:
  - Documents under `docs/phase` for that phase should be treated as frozen
    progress records
  - Any additional work must move to the next phase

---

## Writing Style Rules

- No long explanations
- No reasoning or background narratives
- Details must be offloaded via links to journal entries

`docs/phase` is a **map**, not a **story**.

---

## Goal of This Structure

The goal of this structure is to ensure that:

- Humans do not get confused
- AI agents (Chappie / Codex) do not lose context
- Handover can happen with zero verbal explanation

Accurate work management directly impacts both development speed and quality.
