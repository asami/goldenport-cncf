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

- Active phase: `phase-10.md`
- Active checklist: `phase-10-checklist.md`
- Closed phase set currently includes:
  - `phase-4.md`
  - `phase-5.md`
  - `phase-6.md`
  - `phase-7.md`
  - `phase-8.md`
  - `phase-9.md`

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
