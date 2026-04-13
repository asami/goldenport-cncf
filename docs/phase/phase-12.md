# Phase 12 — Web Layer

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 12.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Promote the CNCF Web Layer from journal notes into an implementation track.
- Define CNCF as an operation-centric web integration surface.
- Establish the Static Form App mechanism first, then place the operational
  dashboard, management console, and manual/reference web apps on top of it.
- Define REST/Form API exposure around existing Component / Service /
  Operation metadata as the execution and validation backbone for that mechanism.
- Define Web Descriptor responsibilities for exposure, security, form behavior,
  and application hosting.
- Keep UI framework choices outside the CNCF core.

## 3. Non-Goals

- No full frontend framework implementation in this phase.
- No broad business UI generation beyond a minimal Static Form App path.
- No replacement of existing CLI/meta projections.
- No generalized external API gateway product in this phase.
- No completion of advanced visualizations such as SVG assembly diagrams unless
  they are needed as minimal projections for the web surface.

## 4. Current Work Stack

- A (DONE): WEB-01 — Consolidate Web Layer scope and canonical design surface.
- B (DONE): WEB-02 — Define operation-centric REST/Form API and Static Form App mechanism.
- C (DONE): WEB-03 — Define Web Descriptor model and configuration path.
- D (DONE): WEB-04 — Define read-only dashboard baseline.
- E (SUSPENDED): WEB-05 — Define management console baseline.
- F (SUSPENDED): WEB-06 — Define manual/reference baseline.
- G (SUSPENDED): WEB-07 — Add executable specifications and minimal runtime hooks.

Current note:
- Web-related journal notes already exist for architecture, operational
  management, descriptor, Form API, API response, SPA integration, and
  wireframe generation.
- This phase should promote only the minimal coherent runtime path first and
  leave broader UI generation as later work.
- Development proceeds with `textus-sample-app` as the practical validation
  driver in a separate working thread.
- The first practical entry point is Dashboard, but implementation still follows
  WEB-01 -> WEB-02 -> WEB-04: Dashboard must be placed on the shared Static Form
  App / Form API mechanism rather than introduced as a special server route.

## 5. Development Items

- [x] WEB-01: Consolidate Web Layer scope and canonical design surface.
- [x] WEB-02: Define REST/Form API exposure and Static Form App mechanism.
- [x] WEB-03: Define Web Descriptor model and configuration path.
- [x] WEB-04: Define read-only dashboard baseline.
- [ ] WEB-05: Define management console baseline.
- [ ] WEB-06: Define manual/reference baseline.
- [ ] WEB-07: Add executable specifications and minimal runtime hooks.

## 6. Next Phase Candidates

- NP-1201: Rich UI generation from wireframe DSL.
- NP-1202: Web application hosting and SPA packaging integration.
- NP-1203: Advanced dashboard visualization such as SVG assembly diagrams.
- NP-1204: Public JavaScript SDK and generated client helpers.

## 7. References

- `docs/journal/2026/04/web-application-integration-note.md`
- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-descriptor-note.md`
- `docs/journal/2026/04/web-api-response-note.md`
- `docs/journal/2026/04/web-integration-spa.md`
- `docs/journal/2026/04/web-wireframe-dsl-note.md`
- `docs/journal/2026/04/web-cml-wireframe-generation-note.md`
- `docs/phase/phase-12-checklist.md`
