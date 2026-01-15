# Document Boundary Rules

## Purpose

- This document defines operational rules for placing and maintaining
  documentation under docs/.
- Its goal is to prevent mixing of design decisions, exploratory notes,
  specifications, and operational rules.


## Language Rule

- All documents created under docs/ MUST be written in English.
- This rule applies to design, notes, spec, and rules documents.
- Historical or exploratory content must also follow this rule.


## Overview

- design: normative design intent, boundaries, and contracts
- notes: non-normative exploratory history, rationale, alternatives, and context
- spec: static specifications and references to executable specifications
- rules: writing conventions and operational policies


## docs/design

### Role

- Normative design documents
- Define boundaries, contracts, and intent

### Characteristics

- Stable across versions
- Safe for implementers and users to rely on
- Implicitly carry MUST / SHOULD semantics

### Must Not Contain

- historical background
- rationale or justification
- alternatives or comparisons
- open questions


## docs/notes

### Role

- Preserve exploratory and historical context

### Characteristics

- Non-normative
- Time- and context-dependent
- Optional for implementation and usage

### May Contain

- historical context
- rationale and “why”
- alternatives / why-not
- lessons learned
- maintenance memos


## docs/spec

### Role

- Static specifications
- Define structure, vocabulary, and contracts
- Reference executable specifications in src/test/scala (Executable Specifications)

### Characteristics

- Normative but non-executable
- The executable truth lives in tests


## docs/rules

### Role

- Operational and writing rules
- Conventions and decision criteria

### Characteristics

- Normative rules
- Do not define design intent


## Mixed Content Rule (Important)

- A single document MUST NOT mix design and notes content.
- If both are present, one of the following actions is REQUIRED:
  - Split the document into `*-design.md` and `*-notes.md`
  - Move exploratory sections to notes and leave a reference link in design
- Design documents MUST remain self-contained and normative.


## CNCF-specific boundaries

### Scenario boundary

- Scenario specs may cross multiple layers.
- Scenario specs must avoid asserting internal implementation details.

### Component boundary

- Component seams are strict.
- Service and Receptor interaction must be mediated by ComponentActionEntry.

### Action boundary

- Action and ActionCall are the primary execution seams.
- Crossing Action boundaries requires explicit Consequence propagation.


## Decision Checklist

When deciding where a document belongs:

1. Does violating this document cause misuse or bugs?
   - Yes -> design or spec
   - No  -> notes

2. Does it define MUST / SHOULD semantics?
   - Yes -> design

3. Does it explain why a decision was made?
   - Yes -> notes

4. Is it a writing or operational rule?
   - Yes -> rules


## Notes

- This document is an operational rule, not a design specification.
- New design decisions MUST be recorded under docs/design.
- Exploratory and historical content MUST be recorded under docs/notes.


----------------------------------------------------------------------
Prohibited Actions
----------------------------------------------------------------------
- Modifying existing documents
- Introducing new design decisions
- Redefining terminology or policies


Completion Criteria
----------------------------------------------------------------------
- docs/rules/document-boundary.md exists
- The document is written entirely in English
- Document placement rules are clear and mechanically applicable
- Future documentation changes can be reviewed against this rule

## Document Roles and Evolution

This project classifies documents by role.  
Editing permissions and immutability rules are determined by this role,
not by file location or document format.

### Anchor / Process Documents

Anchor / Process documents define historical or procedural facts,
such as project bootstrap state, phase or stage completion evidence,
and authoritative process anchors.

Examples include (but are not limited to):
- Phase or stage checklists
- Demo bootstrap documents used as process anchors
- Strategy documents that define phase sequencing

Rules:
- Anchor / Process documents MUST be treated as immutable once the
  relevant phase or stage is CLOSED or DONE.
- These documents MUST NOT be edited to reflect later behavior,
  even if the current implementation has evolved.
- Corrections are allowed only as explicit errata sections with
  clear timestamps and rationale, and MUST NOT alter original content.

### Design / Specification Documents

Design / Specification documents define canonical behavior,
contracts, and semantics of the system.

Rules:
- These documents MAY be updated to reflect the current
  canonical behavior of the system.
- They MUST NOT contain process history, checklist status,
  or stage-specific execution evidence.
- When behavior evolves, changes SHOULD be integrated into
  existing design documents rather than creating new ones.

### Notes Documents

Notes documents capture exploratory ideas, historical discussion,
or non-normative context.

Rules:
- Notes documents MAY evolve freely.
- Notes documents MUST NOT override or contradict
  Design / Specification documents.
- Notes documents MUST NOT be used as substitutes for
  canonical design or process documentation.

### Anti-Fragmentation Rule

To prevent documentation fragmentation:

- New standalone documents MUST NOT be created unless:
  - their role is clearly identified, and
  - they have an explicit integration or reference point
    from an existing canonical document.
- When existing documents can be extended without violating
  their role, extension is preferred over creating new files.

These rules are normative and apply across all phases and stages.
