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

- Latest closed phase: `phase-70.1.md` - documentation-only retirement of the
  superseded duplicate activation plan. Phase 70 remains the authoritative
  accepted implementation.
- Phase 70 is closed with the accepted protected component activation
  implementation. Phase 70.1 is closed under `retire-superseded`; Textus BoK
  Phase 8 remains a separate consumer boundary.
- Latest closed Phase 59-series phase: `phase-59.10.md` - DOC-10 canonical
  documentation and Phase 59 series closure. It is closed under
  `phase59.10-clb-doc10-20260827`, consuming the accepted DOC-09 security,
  regression, downstream, and full-suite evidence without changing behavior.
- Phase 59 is fully closed. The Phase 60 series is fully closed: Phase 60 and
  Phases 60.1 through 60.8 completed ADM-01 through ADM-09. Phase 61 is
  closed under `phase61-clb-ic02-20260831`: IC-01 and IC-02 are complete.
  Phase 61.1 is closed under `phase61.1-clb-ic03-20260831`: IC-03 generated
  Information runtime adoption is complete after the required full suite and
  full/focused review gates. Phase 61.2 is closed under
  `phase61.2-clb-ic04-20260831`: IC-04 InformationSpace entity persistence and
  OCC are complete after the required full suite and closure review. Phase
  61.3 is closed under `phase61.3-clb-ic05-20260901`: IC-05 curation and
  Knowledge lifecycle parity is complete after the required full suite,
  mandatory review, and focused closure reviews. Phase 61.4 is closed under
  `phase61.4-clb-ic06-20260901`: IC-06 projects the canonical Information
  Entity through the protected DSL, editor, and applicable System Admin
  Information HTTP/Web surface with sanitized output, structured stale-write
  metadata, and authorization isolation. Phase 61.5 is closed with IC-07A
  persisted-state migration admission. Phase 61.5.1 is closed with the
  qualified IC-07B development-coordinate, representative-consumer,
  configuration-propagation, canonical-SAR-binding, and static packaged
  boundary recorded in `P61.5.1-DEC-QUALIFIED-CLOSE-001`; it makes no broad
  profile, multi-CAR runtime, or final full-suite claim. Phase 61.6 closes the
  Phase 61 series with IC-08 canonical duplicate removal, the accepted full
  review, and the CNCF, Textus Knowledge Editor, and Textus SIE final
  full-suite matrix. It consumes only that qualified handoff and does not claim
  the separately owned CAR runtime outcomes.
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

- Phase 69 is closed: `JM69-01` inventory reconciliation and `JM69-02`'s
  provider-neutral durable Job/Task record contract are accepted. Phase 69.1
  is closed: `JM69-03` establishes durable storage and deterministic process
  recovery. Phase 69.2 is closed: `JM69-04` establishes canonical authorized
  cursor and exact management reads, guarded controls, the compatible
  `listJobs` facade, and the generated `job_control` projection. Phase 69.3
  is closed: `JM69-05` establishes executable JCL runtime semantics. Phase
  69.4 is the active lightweight direct JobDefinition lifecycle work. Phase 62
  and Phase 69.5 through Phase 69.7 remain planned and not started:
  - `phase-69.md` / `phase-69-checklist.md`
  - `phase-69.1.md` / `phase-69.1-checklist.md`
  - `phase-69.5.md` / `phase-69.5-checklist.md`
  - `phase-69.6.md` / `phase-69.6-checklist.md`
  - `phase-69.7.md` / `phase-69.7-checklist.md`
- Latest closed checklist: `phase-69.2-checklist.md`.
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
  - `phase-60.2.md`
  - `phase-60.3.md`
  - `phase-60.4.md`
  - `phase-60.5.md`
  - `phase-60.6.md`
  - `phase-60.7.md`
  - `phase-60.8.md`
  - `phase-61.md`
  - `phase-61.1.md`
  - `phase-61.2.md`
  - `phase-61.3.md`
  - `phase-61.4.md`
  - `phase-61.5.md`
  - `phase-61.5.1.md`
  - `phase-61.6.md`
  - `phase-69.md`
  - `phase-69.1.md`
  - `phase-69.2.md`
  - `phase-69.3.md`
  - `phase-70.md`
  - `phase-70.1.md`
- Phase 69.4 is the active lightweight direct JobDefinition lifecycle work.
  Its direct lifecycle contract is consumed by Phase 69.5; Phase 69.5 through
  Phase 69.7 remain planned and not started.

## Entity ID Contract Recovery

- [Phase 74](phase-74.md) is closed as the EntityId contract/inventory
  authority, [Phase 74.1](phase-74.1.md) is closed as the
  `simplemodeling-model` producer materialization, and
  [Phase 74.2](phase-74.2.md) is closed as the CNCF typed-consumer adoption.
  [Phase 74.3](phase-74.3.md) is closed: focused validation, the aggregate
  suite, Phase full review, and focused closure re-review passed, and its
  distinct local release records the producer/consumer closure.
- Ledgers: [74](phase-74-checklist.md), [74.1](phase-74.1-checklist.md),
  [74.2](phase-74.2-checklist.md), and [74.3](phase-74.3-checklist.md).
  This status projection selects no successor as active.

## Planned Service Execution Contracts

- [Phase 75 - Component/Service Purpose and Operation Statefulness](phase-75.md)
  is planned and not started. Component purpose supports domain/application/both;
  service purpose supplies stateless/stateful defaults with explicit overrides.
- [Phase 75 Checklist](phase-75-checklist.md) is the implementation ledger.
  This addition selects no active phase and changes no existing phase status.

## Completed StateMachine Runtime Sequence

- [Phase 63](phase-63.md), [Phase 63.1](phase-63.1.md), and
  [Phase 63.2](phase-63.2.md) are complete. Phase 63 froze
  contract/normalization, Phase 63.1 owns the completed generation and local
  atomic execution contract, and Phase 63.2 owns the completed
  `CommittedTransition` delivery, observability, cross-repository acceptance,
  and aggregate full suite.
- [Phase 64](phase-64.md) consumes the Phase 63.2 handoff; [Phase 64.2](phase-64.2.md)
  consumes Phase 63.1's atomic-execution contract directly. [Phase 64.1](phase-64.1.md)
  is a superseded historical planning record and is not on the `sm-workflow`
  Phase 1 critical path.
- Ledgers: [63](phase-63-checklist.md), [63.1](phase-63.1-checklist.md), and
  [63.2](phase-63.2-checklist.md).

## Planned Hash Responsibility Review

- [Phase 76 - Hash Responsibility and Integrity Boundary Review](phase-76.md)
  is planned and not started. It is the parent review for CNCF hash,
  fingerprint, checksum, and content-digest controls, including CNCF cleanup.
- [Phase 76 Checklist](phase-76-checklist.md) owns inventory, remediation,
  validation, and closure in CNCF; only external-owner work is handed off.

## Planned StateMachine API/SPI and Skill-Driven Workflow Runtime

- [Phase 77 - StateMachine API/SPI Runtime and First Skill-Driven Workflow Vertical Slice](phase-77.md)
  is planned and not started. It consumes Cozy Phase 62.1's API/SPI and
  ActionExecution contract, Phase 62.2's generated ABI, and Phase 62.3's
  producer fixture/handoff after the Phase 64/64.2 prerequisites.
- [Phase 77 Checklist](phase-77-checklist.md) owns ABI admission, generated
  discovery, Provider SPI construction, the independent WorkflowInstance
  persistence contract, durable Continuation, the minimum typed
  Start/Continuation/WorkOrder/Terminal protocol and its schema-versioned
  fail-closed Skill/Codex JSON encoding, and the Continuation SPI IoC port used
  by Generic Skill Workflow Support. Entity StateMachine persistence remains
  entity-owned; Textus `sm-workflow` supplies its consumer persistence and
  software-development-specific behavior.

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
