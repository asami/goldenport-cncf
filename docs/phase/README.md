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

- [Phase 95](phase-95.md) plans a UI-facing View Model and Aggregate command
  contract over existing CNCF `ViewSpace`/`AggregateSpace`, with REST first and
  gRPC deferred. It does not block the Editing Studio Flutter application's
  fake, local, or provisional CRUD-adapter development.
- The first `sm-workflow` vertical slice has priority over shared Workflow
  transaction redesign. [Phase 77.1](phase-77.1.md) and
  [Phase 77.2](phase-77.2.md) use an explicitly loose post-commit Continuation
  baseline. [Phase 94](phase-94.md) owns the later durable shared transaction
  domain; it is not a prerequisite for that first slice and is independent
  of Phase 89. The `sm-workflow` plans already name CNCF Phase 90 for
  Candidate-Admission and Phase 91 for Execution/Failure Model work; this
  deferral does not reuse those numbers or remove those dependencies.
- Planned post-`sm-workflow` producer/consumer sequence: Cozy Phase 66 produces
  the generalized Composite StateMachine semantic artifact and
  [Phase 89](phase-89.md) admits it into CNCF. Both require a stable first
  `sm-workflow` vertical slice plus a concrete consumer requirement; neither
  blocks Phase 64, Phase 77, or `sm-workflow` Phase 1.
- Latest closed phase: [Phase 90](phase-90.md) - generic Candidate-Admission
  runtime support with loose evidence persistence and an explicit sm-workflow
  consumer handoff. Its final source/spec tree passed the full suite on
  2026-09-25; real sm-workflow CML generation remains consumer-owned.
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
- [Phase 64](phase-64.md) is complete: it consumes the Phase 63.2 handoff and
  closes the released Cozy Phase 62.3 Workflow subset required by Phase 77 and
  the first `sm-workflow` vertical slice. [Phase 64.2](phase-64.2.md) is also
  complete under an explicitly authorized forced minimum closure: it accepts
  the existing deterministic UnitOfWork planning/recording foundation and
  transfers formal ABI admission plus production StateMachine/Composite/
  Workflow execution acceptance to Phase 77. [Phase 64.1](phase-64.1.md) is a
  superseded historical planning record and is not on the `sm-workflow` Phase
  1 critical path.
- Ledgers: [63](phase-63-checklist.md), [63.1](phase-63.1-checklist.md), and
  [63.2](phase-63.2-checklist.md).

## Planned Hash Responsibility Review

- [Phase 76 - Hash Responsibility and Integrity Boundary Review](phase-76.md)
  is planned and not started. It is the parent review for CNCF hash,
  fingerprint, checksum, and content-digest controls, including CNCF cleanup.
- [Phase 76 Checklist](phase-76-checklist.md) owns inventory, remediation,
  validation, and closure in CNCF; only external-owner work is handed off.

## Planned StateMachine API/SPI and Skill-Driven Workflow Runtime

The originally estimated 960-minute Phase 77 was split before goal creation
into the planned, serial [Phase 77](phase-77.md) ->
[Phase 77.1](phase-77.1.md) -> [Phase 77.2](phase-77.2.md) sequence. It
consumes Cozy Phase 62.1's API/SPI and ActionExecution contract, Phase 62.2's
generated ABI, and Phase 62.3's producer fixture/handoff after the completed
Phase 64 and forced-minimum Phase 64.2 prerequisites.

- [Phase 77 Checklist](phase-77-checklist.md) owns generated ABI admission,
  ComponentFactory discovery, and the independent WorkflowInstance persistence
  SPI.
- [Phase 77.1 Checklist](phase-77.1-checklist.md) owns ActionExecution,
  Provider runtime, durable Continuation, and fail-closed resume.
- [Phase 77.2 Checklist](phase-77.2-checklist.md) owns the Generic Skill
  projection, typed Start/Continuation/WorkOrder/Terminal protocol,
  schema-versioned fail-closed Skill/Codex JSON, the real producer-fixture
  vertical slice, and the `sm-workflow` consumer handoff. It is the sole
  repository-wide full-suite validation owner. Entity StateMachine persistence
  remains entity-owned; Textus `sm-workflow` supplies consumer persistence and
  software-development-specific behavior.
- [Phase 94 Checklist](phase-94-checklist.md) owns the deferred single-commit
  domain spanning EventStore, DataStore, WorkflowInstance, Continuation, and
  UnitOfWork. Its atomicity proof is outside the serial Phase 77 sequence and
  must not block the first `sm-workflow` vertical slice.

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

## Ledger Inventory — 2026-09-28

This dated navigation inventory lists every phase/checklist Markdown file
present on 2026-09-28 and copies its top-level status literally. A missing
status is reported rather than inferred. The older `Current baseline` and
closed-phase lists above are historical snapshots where they differ from
these ledgers. For example, the Phase 69.4 active pointer above predates its
closed ledger. Each phase and checklist remains the authority for its own
acceptance, successor, and resume evidence. This inventory changes no status.

| Ledger | Declared status |
| --- | --- |
| [phase-2.85-checklist](phase-2.85-checklist.md) | No top-level status declared |
| [phase-2.85-demo-readiness](phase-2.85-demo-readiness.md) | status = closed |
| [phase-3.1-checklist](phase-3.1-checklist.md) | No top-level status declared |
| [phase-3.1.1-checklist](phase-3.1.1-checklist.md) | No top-level status declared |
| [phase-3.1.1](phase-3.1.1.md) | status = closed |
| [phase-3.1](phase-3.1.md) | status = done |
| [phase-3.2](phase-3.2.md) | status = done |
| [phase-3.3-checklist](phase-3.3-checklist.md) | No top-level status declared |
| [phase-3.3](phase-3.3.md) | status = closed |
| [phase-3](phase-3.md) | status = closed |
| [phase-4-checklist](phase-4-checklist.md) | No top-level status declared |
| [phase-4](phase-4.md) | status = close |
| [phase-5-checklist](phase-5-checklist.md) | No top-level status declared |
| [phase-5](phase-5.md) | status = close |
| [phase-6-checklist](phase-6-checklist.md) | No top-level status declared |
| [phase-6](phase-6.md) | status = close |
| [phase-7-checklist](phase-7-checklist.md) | No top-level status declared |
| [phase-7](phase-7.md) | status = close |
| [phase-8-checklist](phase-8-checklist.md) | No top-level status declared |
| [phase-8](phase-8.md) | status = close |
| [phase-9-checklist](phase-9-checklist.md) | No top-level status declared |
| [phase-9](phase-9.md) | status = close |
| [phase-10-checklist](phase-10-checklist.md) | No top-level status declared |
| [phase-10](phase-10.md) | status = close |
| [phase-11-checklist](phase-11-checklist.md) | No top-level status declared |
| [phase-11](phase-11.md) | status = complete |
| [phase-12-checklist](phase-12-checklist.md) | No top-level status declared |
| [phase-12](phase-12.md) | status = complete |
| [phase-13-checklist](phase-13-checklist.md) | No top-level status declared |
| [phase-13](phase-13.md) | status = closed |
| [phase-14-checklist](phase-14-checklist.md) | No top-level status declared |
| [phase-14](phase-14.md) | status = closed |
| [phase-15-checklist](phase-15-checklist.md) | No top-level status declared |
| [phase-15](phase-15.md) | status = closed |
| [phase-16-checklist](phase-16-checklist.md) | No top-level status declared |
| [phase-16](phase-16.md) | status = closed |
| [phase-17-checklist](phase-17-checklist.md) | No top-level status declared |
| [phase-17](phase-17.md) | status = closed |
| [phase-18-checklist](phase-18-checklist.md) | Status: DONE |
| [phase-18](phase-18.md) | status = closed |
| [phase-19-checklist](phase-19-checklist.md) | Status: DONE |
| [phase-19](phase-19.md) | status = closed |
| [phase-20-checklist](phase-20-checklist.md) | Status: DONE |
| [phase-20](phase-20.md) | status = closed |
| [phase-21-checklist](phase-21-checklist.md) | Status: DONE |
| [phase-21](phase-21.md) | status = closed |
| [phase-22-checklist](phase-22-checklist.md) | Status: DONE |
| [phase-22](phase-22.md) | status = closed |
| [phase-23-checklist](phase-23-checklist.md) | Status: DONE |
| [phase-23](phase-23.md) | status = closed |
| [phase-24-checklist](phase-24-checklist.md) | Status: DONE |
| [phase-24](phase-24.md) | status = closed |
| [phase-25-checklist](phase-25-checklist.md) | Status: DONE |
| [phase-25](phase-25.md) | status = closed |
| [phase-26-checklist](phase-26-checklist.md) | Status: DONE |
| [phase-26](phase-26.md) | status = closed |
| [phase-27-checklist](phase-27-checklist.md) | Status: DONE |
| [phase-27](phase-27.md) | status = completed |
| [phase-28-checklist](phase-28-checklist.md) | No top-level status declared |
| [phase-28](phase-28.md) | status = closed |
| [phase-29-checklist](phase-29-checklist.md) | No top-level status declared |
| [phase-29](phase-29.md) | status = closed |
| [phase-30-checklist](phase-30-checklist.md) | Status: DONE |
| [phase-30](phase-30.md) | status = closed |
| [phase-31-checklist](phase-31-checklist.md) | Status: DONE |
| [phase-31](phase-31.md) | status = closed |
| [phase-32-checklist](phase-32-checklist.md) | Status: DONE |
| [phase-32](phase-32.md) | status = closed |
| [phase-33-checklist](phase-33-checklist.md) | Status: DONE |
| [phase-33](phase-33.md) | status = closed |
| [phase-34-checklist](phase-34-checklist.md) | Status: DONE (Jul. 17, 2026) |
| [phase-34](phase-34.md) | status = closed |
| [phase-35-checklist](phase-35-checklist.md) | Status: DONE (Jul. 17, 2026) |
| [phase-35](phase-35.md) | status = closed |
| [phase-36-checklist](phase-36-checklist.md) | Status: DONE |
| [phase-36](phase-36.md) | status = closed |
| [phase-37-checklist](phase-37-checklist.md) | Status: DONE |
| [phase-37](phase-37.md) | status = closed |
| [phase-38-checklist](phase-38-checklist.md) | Status: DONE |
| [phase-38](phase-38.md) | status = closed |
| [phase-39-checklist](phase-39-checklist.md) | Status: DONE |
| [phase-39](phase-39.md) | status = closed |
| [phase-40-checklist](phase-40-checklist.md) | Status: DONE |
| [phase-40](phase-40.md) | status = closed |
| [phase-41-checklist](phase-41-checklist.md) | Status: DONE |
| [phase-41](phase-41.md) | status = closed |
| [phase-42-checklist](phase-42-checklist.md) | Status: DONE |
| [phase-42](phase-42.md) | status = closed |
| [phase-43-checklist](phase-43-checklist.md) | Status: DONE |
| [phase-43](phase-43.md) | status = closed |
| [phase-44-checklist](phase-44-checklist.md) | Status: DONE |
| [phase-44](phase-44.md) | status = closed |
| [phase-45-checklist](phase-45-checklist.md) | status=closed |
| [phase-45](phase-45.md) | status=closed |
| [phase-46-checklist](phase-46-checklist.md) | No top-level status declared |
| [phase-46](phase-46.md) | status=closed |
| [phase-47-checklist](phase-47-checklist.md) | status=closed |
| [phase-47](phase-47.md) | status=closed |
| [phase-48-checklist](phase-48-checklist.md) | status=closed |
| [phase-48](phase-48.md) | status=closed |
| [phase-49-checklist](phase-49-checklist.md) | status=closed |
| [phase-49](phase-49.md) | status=closed |
| [phase-50-checklist](phase-50-checklist.md) | status=closed |
| [phase-50](phase-50.md) | status=closed |
| [phase-51-checklist](phase-51-checklist.md) | status=closed |
| [phase-51](phase-51.md) | status=closed |
| [phase-52-checklist](phase-52-checklist.md) | status=closed |
| [phase-52](phase-52.md) | status=closed |
| [phase-53-checklist](phase-53-checklist.md) | status=closed |
| [phase-53](phase-53.md) | status=closed |
| [phase-54-checklist](phase-54-checklist.md) | status=closed |
| [phase-54](phase-54.md) | status=closed |
| [phase-55-checklist](phase-55-checklist.md) | status=closed |
| [phase-55](phase-55.md) | status=closed |
| [phase-56-checklist](phase-56-checklist.md) | status=closed |
| [phase-56](phase-56.md) | status=closed |
| [phase-57-checklist](phase-57-checklist.md) | status=closed |
| [phase-57.1-checklist](phase-57.1-checklist.md) | status=closed |
| [phase-57.1](phase-57.1.md) | status=closed |
| [phase-57.2-checklist](phase-57.2-checklist.md) | status=closed |
| [phase-57.2](phase-57.2.md) | status=closed |
| [phase-57.3-checklist](phase-57.3-checklist.md) | status=done |
| [phase-57.3](phase-57.3.md) | status=done |
| [phase-57.4-checklist](phase-57.4-checklist.md) | status=done |
| [phase-57.4](phase-57.4.md) | status=done |
| [phase-57.5-checklist](phase-57.5-checklist.md) | status=done |
| [phase-57.5](phase-57.5.md) | status=done |
| [phase-57](phase-57.md) | status=closed |
| [phase-58-checklist](phase-58-checklist.md) | status=closed |
| [phase-58.1-checklist](phase-58.1-checklist.md) | status=done |
| [phase-58.1](phase-58.1.md) | status=done |
| [phase-58.2-checklist](phase-58.2-checklist.md) | status=done |
| [phase-58.2](phase-58.2.md) | status=done |
| [phase-58.3-checklist](phase-58.3-checklist.md) | status=done |
| [phase-58.3](phase-58.3.md) | status=done |
| [phase-58.4-checklist](phase-58.4-checklist.md) | status=done |
| [phase-58.4](phase-58.4.md) | status=done |
| [phase-58.5-checklist](phase-58.5-checklist.md) | status=done |
| [phase-58.5](phase-58.5.md) | status=done |
| [phase-58.6-checklist](phase-58.6-checklist.md) | status=done |
| [phase-58.6.1-checklist](phase-58.6.1-checklist.md) | status=closed |
| [phase-58.6.1](phase-58.6.1.md) | status=closed |
| [phase-58.6](phase-58.6.md) | status=done |
| [phase-58.7-checklist](phase-58.7-checklist.md) | status=closed |
| [phase-58.7](phase-58.7.md) | status=closed |
| [phase-58.8-checklist](phase-58.8-checklist.md) | status=closed |
| [phase-58.8](phase-58.8.md) | status=closed |
| [phase-58.9-checklist](phase-58.9-checklist.md) | status=closed |
| [phase-58.9](phase-58.9.md) | status=closed |
| [phase-58](phase-58.md) | status=closed |
| [phase-59-checklist](phase-59-checklist.md) | status=closed |
| [phase-59.1-checklist](phase-59.1-checklist.md) | status=closed |
| [phase-59.1](phase-59.1.md) | status=closed |
| [phase-59.2-checklist](phase-59.2-checklist.md) | status=closed |
| [phase-59.2.1-checklist](phase-59.2.1-checklist.md) | status=closed |
| [phase-59.2.1](phase-59.2.1.md) | status=closed |
| [phase-59.2.2-checklist](phase-59.2.2-checklist.md) | status=closed |
| [phase-59.2.2](phase-59.2.2.md) | status=closed |
| [phase-59.2.3-checklist](phase-59.2.3-checklist.md) | status=closed |
| [phase-59.2.3](phase-59.2.3.md) | status=closed |
| [phase-59.2](phase-59.2.md) | status=closed |
| [phase-59.3-checklist](phase-59.3-checklist.md) | status=closed |
| [phase-59.3](phase-59.3.md) | status=closed |
| [phase-59.4-checklist](phase-59.4-checklist.md) | status=closed |
| [phase-59.4](phase-59.4.md) | status=closed |
| [phase-59.5-checklist](phase-59.5-checklist.md) | status=closed |
| [phase-59.5](phase-59.5.md) | status=closed |
| [phase-59.6-checklist](phase-59.6-checklist.md) | status=closed |
| [phase-59.6](phase-59.6.md) | status=closed |
| [phase-59.7-checklist](phase-59.7-checklist.md) | status=closed |
| [phase-59.7](phase-59.7.md) | status=closed |
| [phase-59.8-checklist](phase-59.8-checklist.md) | status=closed |
| [phase-59.8](phase-59.8.md) | status=closed |
| [phase-59.9-checklist](phase-59.9-checklist.md) | status=closed |
| [phase-59.9](phase-59.9.md) | status=closed |
| [phase-59.10-checklist](phase-59.10-checklist.md) | status=closed |
| [phase-59.10](phase-59.10.md) | status=closed |
| [phase-59](phase-59.md) | status=closed |
| [phase-60-checklist](phase-60-checklist.md) | status=closed |
| [phase-60.1-checklist](phase-60.1-checklist.md) | status=closed |
| [phase-60.1](phase-60.1.md) | status=closed |
| [phase-60.2-checklist](phase-60.2-checklist.md) | status=closed |
| [phase-60.2](phase-60.2.md) | status=closed |
| [phase-60.3-checklist](phase-60.3-checklist.md) | status=closed |
| [phase-60.3](phase-60.3.md) | status=closed |
| [phase-60.4-checklist](phase-60.4-checklist.md) | status=closed |
| [phase-60.4](phase-60.4.md) | status=closed |
| [phase-60.5-checklist](phase-60.5-checklist.md) | status=closed |
| [phase-60.5](phase-60.5.md) | status=closed |
| [phase-60.6-checklist](phase-60.6-checklist.md) | status=closed |
| [phase-60.6](phase-60.6.md) | status=closed |
| [phase-60.7-checklist](phase-60.7-checklist.md) | status=closed |
| [phase-60.7](phase-60.7.md) | status=closed |
| [phase-60.8-checklist](phase-60.8-checklist.md) | status=closed |
| [phase-60.8](phase-60.8.md) | status=closed |
| [phase-60](phase-60.md) | status=closed |
| [phase-61-checklist](phase-61-checklist.md) | status=closed |
| [phase-61.1-checklist](phase-61.1-checklist.md) | status=closed |
| [phase-61.1](phase-61.1.md) | status=closed |
| [phase-61.2-checklist](phase-61.2-checklist.md) | status=closed |
| [phase-61.2](phase-61.2.md) | status=closed |
| [phase-61.3-checklist](phase-61.3-checklist.md) | status=closed |
| [phase-61.3](phase-61.3.md) | status=closed |
| [phase-61.4-checklist](phase-61.4-checklist.md) | status=closed |
| [phase-61.4](phase-61.4.md) | status=closed |
| [phase-61.5-checklist](phase-61.5-checklist.md) | status=closed |
| [phase-61.5.1-checklist](phase-61.5.1-checklist.md) | status=closed |
| [phase-61.5.1](phase-61.5.1.md) | status=closed |
| [phase-61.5](phase-61.5.md) | status=closed |
| [phase-61.6-checklist](phase-61.6-checklist.md) | status=closed |
| [phase-61.6](phase-61.6.md) | status=closed |
| [phase-61](phase-61.md) | status=closed |
| [phase-62-checklist](phase-62-checklist.md) | status=planned |
| [phase-62.1-checklist](phase-62.1-checklist.md) | status=planned |
| [phase-62.1](phase-62.1.md) | status=planned |
| [phase-62](phase-62.md) | status=planned |
| [phase-63-checklist](phase-63-checklist.md) | status=complete |
| [phase-63-execution-plan](phase-63-execution-plan.md) | status=complete |
| [phase-63.1-checklist](phase-63.1-checklist.md) | status=complete |
| [phase-63.1](phase-63.1.md) | status=complete |
| [phase-63.2-checklist](phase-63.2-checklist.md) | status=completed |
| [phase-63.2](phase-63.2.md) | status=completed |
| [phase-63](phase-63.md) | status=complete |
| [phase-64-checklist](phase-64-checklist.md) | status=completed |
| [phase-64.1](phase-64.1.md) | status=superseded |
| [phase-64.2](phase-64.2.md) | status=completed |
| [phase-64](phase-64.md) | status=completed |
| [phase-65-checklist](phase-65-checklist.md) | status=planned |
| [phase-65](phase-65.md) | status=planned |
| [phase-66-checklist](phase-66-checklist.md) | status=planned |
| [phase-66](phase-66.md) | status=planned |
| [phase-67-checklist](phase-67-checklist.md) | status=planned |
| [phase-67](phase-67.md) | status=planned |
| [phase-68-checklist](phase-68-checklist.md) | status=planned |
| [phase-68](phase-68.md) | status=planned |
| [phase-69-checklist](phase-69-checklist.md) | status=closed |
| [phase-69.1-checklist](phase-69.1-checklist.md) | status=closed |
| [phase-69.1](phase-69.1.md) | status=closed |
| [phase-69.2-checklist](phase-69.2-checklist.md) | status=completed |
| [phase-69.2](phase-69.2.md) | status=completed |
| [phase-69.3-checklist](phase-69.3-checklist.md) | status=closed |
| [phase-69.3](phase-69.3.md) | status=closed |
| [phase-69.4-checklist](phase-69.4-checklist.md) | status=closed |
| [phase-69.4](phase-69.4.md) | status=closed |
| [phase-69.5-checklist](phase-69.5-checklist.md) | status=planned |
| [phase-69.5](phase-69.5.md) | status=planned |
| [phase-69.6-checklist](phase-69.6-checklist.md) | status=planned |
| [phase-69.6](phase-69.6.md) | status=planned |
| [phase-69.7-checklist](phase-69.7-checklist.md) | status=planned |
| [phase-69.7](phase-69.7.md) | status=planned |
| [phase-69](phase-69.md) | status=closed |
| [phase-70-checklist](phase-70-checklist.md) | status=closed |
| [phase-70.1-checklist](phase-70.1-checklist.md) | status=closed |
| [phase-70.1](phase-70.1.md) | status=closed |
| [phase-70](phase-70.md) | status=closed |
| [phase-71-checklist](phase-71-checklist.md) | status=planned |
| [phase-71](phase-71.md) | status=planned |
| [phase-72-checklist](phase-72-checklist.md) | status=planned |
| [phase-72](phase-72.md) | status=planned |
| [phase-73-checklist](phase-73-checklist.md) | status=planned |
| [phase-73](phase-73.md) | status=planned |
| [phase-74-checklist](phase-74-checklist.md) | status=closed |
| [phase-74.1-checklist](phase-74.1-checklist.md) | status=closed |
| [phase-74.1](phase-74.1.md) | status=closed |
| [phase-74.2-checklist](phase-74.2-checklist.md) | status=closed |
| [phase-74.2](phase-74.2.md) | status=closed |
| [phase-74.3-checklist](phase-74.3-checklist.md) | status=closed |
| [phase-74.3](phase-74.3.md) | status=closed |
| [phase-74](phase-74.md) | status=closed |
| [phase-75-checklist](phase-75-checklist.md) | status=planned |
| [phase-75](phase-75.md) | status=planned |
| [phase-76-checklist](phase-76-checklist.md) | status=planned |
| [phase-76](phase-76.md) | status=planned |
| [phase-77-checklist](phase-77-checklist.md) | status=closed |
| [phase-77.1-checklist](phase-77.1-checklist.md) | status=closed |
| [phase-77.1](phase-77.1.md) | status=closed |
| [phase-77.2-checklist](phase-77.2-checklist.md) | status=planned |
| [phase-77.2](phase-77.2.md) | status=planned |
| [phase-77](phase-77.md) | status=closed |
| [phase-78-checklist](phase-78-checklist.md) | status=planned |
| [phase-78](phase-78.md) | status=planned |
| [phase-79](phase-79.md) | status=planned |
| [phase-80-participant-invocation-addendum](phase-80-participant-invocation-addendum.md) | Status: planned / normative addendum to Phase 80 |
| [phase-80-skill-workflow-support-addendum](phase-80-skill-workflow-support-addendum.md) | Status: planned / normative addendum to Phase 80 |
| [phase-80-statemachine-api-spi-addendum](phase-80-statemachine-api-spi-addendum.md) | Status: planned / normative refinement |
| [phase-80-workflow-api-proxy-roadmap-addendum](phase-80-workflow-api-proxy-roadmap-addendum.md) | Status: planned / forward-compatibility clarification |
| [phase-80-workflow-spi-addendum](phase-80-workflow-spi-addendum.md) | Status: planned / normative refinement |
| [phase-80](phase-80.md) | Status: planned |
| [phase-81](phase-81.md) | Status: planned |
| [phase-82](phase-82.md) | Status: planned |
| [phase-83](phase-83.md) | Status: planned |
| [phase-84](phase-84.md) | Status: planned |
| [phase-85](phase-85.md) | Status: planned |
| [phase-86](phase-86.md) | Status: planned |
| [phase-87](phase-87.md) | Status: planned |
| [phase-88](phase-88.md) | Status: planned |
| [phase-89-checklist](phase-89-checklist.md) | status=planned |
| [phase-89](phase-89.md) | status=planned |
| [phase-90-checklist](phase-90-checklist.md) | status=planned |
| [phase-90](phase-90.md) | status=planned |
| [phase-91](phase-91.md) | No top-level status declared |
| [phase-92](phase-92.md) | No top-level status declared |
| [phase-93](phase-93.md) | No top-level status declared |
| [phase-94-checklist](phase-94-checklist.md) | status=planned |
| [phase-94](phase-94.md) | status=planned |
