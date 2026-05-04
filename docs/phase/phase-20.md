# Phase 20 — Hierarchical Tagging and Knowledge Structure

status = open

## 1. Purpose of This Document

This work document records the active stack of work items for Phase 20.
It is authoritative for current scope, explicit deferrals, and closure status.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Make `8.5 Tagging and Knowledge Structure` the active development item.
- Add CNCF builtin hierarchical `Tag` as a `SimpleEntity`-backed master Entity.
- Treat Tag trees as master data and resident Working Set candidates.
- Allow Tags to be used in two practical styles:
  - lightweight CMS-style tags for content navigation and search;
  - powertype-like external classification for ordinary Entities.
- Store Entity-to-Tag links outside domain Entity records through Association
  data.
- Make parent Tag search include descendant Tags by default.
- Use CNCF admin/runtime as the generic driver for Tag tree management,
  arbitrary Entity tagging, and tag-expanded Entity search.
- Use `textus-blog` as the CMS driver for BlogPost tags, public tag navigation,
  and parent-tag descendant search.
- Keep Tag as an ordinary `SimpleEntity` so it receives existing Entity
  services, descriptive/content attributes, multilingual title/description,
  lifecycle, admin, search, and projection support.

Final semantic direction:

- `Tag` is CNCF builtin master data, not an application-local string list.
- The Tag hierarchy is a tree in Phase 20.
- Tag tree records are canonical store-backed Entities, while the runtime
  tree is a resident read structure rebuilt from normal EntityStore paths.
- Entity-to-Tag links are Association-backed and external to the tagged Entity.
- Tag search expands a selected parent to all active descendant Tags unless the
  caller explicitly requests direct-only matching.
- RDF and external knowledge graph integration remain separate future work.

## 3. Non-Goals

- No RDF store, RDF import/export, or external knowledge graph integration.
- No DAG/polyhierarchy Tag graph; Phase 20 uses a strict tree.
- No compile-time CML type generation from powertype-like Tags.
- No full-text or embedding search backend implementation.
- No persistent materialized view store.
- No change to ordinary Entity storage shape to embed Tag arrays by default.

## 4. Active Work Stack

- A (ACTIVE): TG-01 — Open Phase 20 and freeze Tag hierarchy semantics.
- B (SUSPENDED): TG-02 — Add CNCF builtin Tag model and resident Tag tree.
- C (SUSPENDED): TG-03 — Add TagAttachment Association and tag-expanded Entity search.
- D (SUSPENDED): TG-04 — Add CNCF admin, manual, and projection surfaces.
- E (SUSPENDED): TG-05 — Apply Tags to `textus-blog` CMS navigation and search.
- F (SUSPENDED): TG-06 — Verification, documentation, and phase closure.

Resume hint:

- Start with TG-01 by documenting the Tag tree, TagAttachment, descendant-search,
  admin driver, and `textus-blog` driver contracts in the checklist. Then move
  to TG-02 implementation.

## 5. Development Items

- [ ] TG-01: Open Phase 20 and freeze Tag hierarchy semantics.
- [ ] TG-02: Add CNCF builtin Tag model and resident Tag tree.
- [ ] TG-03: Add TagAttachment Association and tag-expanded Entity search.
- [ ] TG-04: Add CNCF admin, manual, and projection surfaces.
- [ ] TG-05: Apply Tags to `textus-blog` CMS navigation and search.
- [ ] TG-06: Verification, documentation, and phase closure.

Detailed task breakdown and progress tracking are recorded in
`phase-20-checklist.md`.

## 6. Completion Conditions

Phase 20 can close when:

- CNCF has a builtin `Tag` Entity with strict tree semantics and resident
  Tag tree lookup.
- Entity-to-Tag attachment is Association-backed and works for arbitrary
  Entities.
- Parent Tag search expands to descendant Tags and respects normal EntityStore
  access scope and logical delete behavior.
- CNCF admin/runtime exposes Tag tree management and Entity tag attach/detach
  flows.
- `textus-blog` validates CMS-style BlogPost tags, public tag navigation, and
  parent-tag descendant search.
- Tests cover model, tree, attachment/search, admin/projection, and Blog driver
  behavior.
- Deferred RDF/Knowledge Graph and DAG/polyhierarchy work is explicitly
  recorded outside Phase 20.
