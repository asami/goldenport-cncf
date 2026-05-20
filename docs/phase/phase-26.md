# Phase 26 — Knowledge Import and InformationSpace

status = closed

## 1. Purpose of This Document

This work document records Phase 26, which selects the next
`9.5 Knowledge Structure Follow-ups` slice after the closed Phase 25 baseline.

Phase 25 completed the CNCF `KnowledgeSpace` runtime projection baseline and
validated `textus-sie` retrieval output into `KnowledgeFrame` /
`KnowledgeWorkingSetSnapshot`. Phase 26 adds the upstream curated information
lifecycle: domain authoring input, InformationSpace staging, validation,
resolution, confirmation, publication, and KnowledgeSpace materialization.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Add a component-owned CNCF `InformationSpace` for curated/editable knowledge
  information.
- Keep `InformationSpace` separate from `KnowledgeSpace`: editing and curation
  live in InformationSpace; runtime semantic traversal lives in KnowledgeSpace.
- Add the minimum import batch, staging record, validation issue, resolution
  candidate, identity binding, publication status, and conflict model.
- Add the first paper authoring domain as the executable vertical slice.
- Define provider-neutral Knowledge engine SPI boundaries for authority
  resolution, RDF publication, vector publication, and KnowledgeSpace
  materialization.
- Use `textus-sie` as the driver implementation for RDF/vector provider
  behavior without making CNCF core depend on Fuseki, Chroma, or SIE classes.
- Add system admin/debug projection for InformationSpace state.

Scope boundaries:

- Phase 26 does not reopen Phase 25 KS items.
- Phase 26 does not make `KnowledgeSpace` editable.
- Phase 26 does not add a raw RDF authoring UI.
- Phase 26 keeps XML import deferred until a concrete source requires it.
- Phase 26 keeps `textus-structured-knowledge` as a separate parallel plan
  unless explicitly selected later.

## 3. Active Work Stack

- A (DONE): KI-01 — Open Phase 26 and freeze InformationSpace boundary.
- B (DONE): KI-02 — InformationSpace core model and component-owned skeleton.
- C (DONE): KI-03 — Import batch, staging record, validation issue, and
  lifecycle API.
- D (DONE): KI-04 — Identity binding and authority resolution candidate model.
- E (DONE): KI-05 — Paper authoring domain and validation profile.
- F (DONE): KI-06 — Confirmation, publication status, and conflict model.
- G (DONE): KI-07 — Knowledge engine SPI and `textus-sie` provider integration.
- H (DONE): KI-08 — RDF/vector publication and KnowledgeSpace materialization
  flow.
- I (DONE): KI-09 — InformationSpace admin/debug projection.
- J (DONE): KI-10 — Docker-backed SIE smoke and Phase 26 closure.

Resume hint:

- Phase 26 is closed. Start the next selected phase from the strategy document;
  do not reopen KI items unless a regression needs a targeted fix.

## 4. Development Items

- [x] KI-01: Open Phase 26 and freeze InformationSpace boundary.
- [x] KI-02: InformationSpace core model and component-owned skeleton.
- [x] KI-03: Import batch, staging record, validation issue, and lifecycle API.
- [x] KI-04: Identity binding and authority resolution candidate model.
- [x] KI-05: Paper authoring domain and validation profile.
- [x] KI-06: Confirmation, publication status, and conflict model.
- [x] KI-07: Knowledge engine SPI and `textus-sie` provider integration.
- [x] KI-08: RDF/vector publication and KnowledgeSpace materialization flow.
- [x] KI-09: InformationSpace admin/debug projection.
- [x] KI-10: Docker-backed SIE smoke and Phase 26 closure.

Detailed task breakdown and progress tracking are recorded in
`phase-26-checklist.md`.

## 5. Completion Conditions

Phase 26 closed after:

- CNCF has a component-owned `InformationSpace` runtime skeleton with
  import/edit/validate/resolve/confirm/publish/conflict lifecycle operations.
- Paper authoring input can be validated, resolved, confirmed, published, and
  materialized into `KnowledgeSpace`.
- `textus-sie` implements the provider side for the Phase 26 SPI boundaries.
- System admin/debug pages show InformationSpace state without exposing raw RDF
  or vector payloads.
- Docker-backed Fuseki and the SIE-compatible Chroma adapter validated the
  import-to-publication-to-KnowledgeSpace flow through
  `docker/ks-14/scripts/run-ki10-smoke.sh`.
- Deferred production hardening was recorded in strategy follow-ups.
