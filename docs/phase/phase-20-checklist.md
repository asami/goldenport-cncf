# Phase 20 — Hierarchical Tagging and Knowledge Structure Checklist

This document contains detailed task tracking and decisions for Phase 20.
It complements the summary-level phase document (`phase-20.md`) and may be
used as the closure record for completed Phase 20 work.

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-20.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## TG-01: Open Phase 20 and Freeze Tag Hierarchy Semantics

Status: ACTIVE

### Objective

Open Phase 20 and freeze the CNCF Tag model direction before implementation.

### Detailed Tasks

- [x] Create `docs/phase/phase-20.md`.
- [x] Create `docs/phase/phase-20-checklist.md`.
- [x] Mark Phase 20 as the current active phase in strategy.
- [x] Keep Phase 19 as the latest closed phase.
- [x] Confirm development drivers:
  - CNCF admin/runtime for generic Tag management and arbitrary Entity tagging;
  - `textus-blog` for CMS-style BlogPost tag navigation and search.
- [x] Record that Tag hierarchy is a strict tree in Phase 20.
- [x] Record that parent Tag search includes child Tags by default.
- [x] Record that RDF/Knowledge Graph integration remains under the future RDF
      item, not Phase 20.

### Decisions

- `Tag` is a CNCF builtin `SimpleEntity`.
- `Tag` uses `entityKind = master` and default resident Working Set behavior.
- `Tag` can be used as lightweight CMS tagging or powertype-like external
  classification.
- Entity-to-Tag links are external Association records, not embedded fields on
  the tagged Entity.
- Tag tree lookup is resident for speed, but canonical records stay in
  EntityStore.
- Parent Tag search expands descendants by default; direct-only search remains
  an explicit option.
- Tag display and documentation use existing SimpleEntity name, title,
  descriptive attributes, and content attributes, including multilingual
  display support.

### Guardrails

- Do not implement RDF or external graph integration in Phase 20.
- Do not introduce DAG/polyhierarchy Tag graphs in Phase 20.
- Do not generate compile-time model types from Tags in this phase.
- Do not store tag arrays directly in ordinary Entity records by default.

---

## TG-02: CNCF Builtin Tag Model and Resident Tag Tree

Status: TODO

### Objective

Add the builtin Tag Entity model and the runtime tree used for lookup,
navigation, and descendant expansion.

### Detailed Tasks

- [ ] Add Tag Entity collection metadata as CNCF builtin master data.
- [ ] Define Tag-specific fields:
  - key/code;
  - parent Tag id;
  - path;
  - usage kind: `powertype`, `cms`, `navigation`, or `general`;
  - sort order.
- [ ] Reuse SimpleEntity descriptive/content attributes for multilingual
      title, description, and longer Tag explanation.
- [ ] Validate missing parent, cycle, duplicate sibling key, and invalid
      key/path as deterministic failures.
- [ ] Add Tag tree loader from normal EntityStore paths.
- [ ] Add lookup by id, entropy, key, and path.
- [ ] Add descendant expansion excluding logically deleted Tags.
- [ ] Expose effective Working Set policy as `resident-all`.

### Expected Output

- Tag records can be created, searched, rendered, and described like normal
  SimpleEntities.
- The resident Tag tree can be rebuilt from canonical Tag records.

---

## TG-03: TagAttachment Association and Tag-Expanded Entity Search

Status: TODO

### Objective

Add Association-backed Entity tagging and search by Tag, including descendant
expansion.

### Detailed Tasks

- [ ] Add `AssociationDomain.TagAttachment`.
- [ ] Add TagAttachment storage policy.
- [ ] Add attach/detach/list workflow for arbitrary source Entity ids.
- [ ] Make duplicate `(sourceEntityId, tagId, role)` attachment idempotent.
- [ ] Add `tag_search_entities` behavior:
  - resolve Tag by id/entropy/key/path;
  - expand descendants by default;
  - find matching source Entity ids from TagAttachment;
  - load/search target Entities through normal EntityStore access scope.
- [ ] Add direct-only search option with `includeDescendants = false`.
- [ ] Ensure deleted/out-of-scope Tags and Entities are not returned.

### Expected Output

- Tags behave as external classification data and do not change domain Entity
  storage shape.
- Parent Tag search finds Entities tagged with child Tags.

---

## TG-04: CNCF Admin, Manual, and Projection Surfaces

Status: TODO

### Objective

Expose Tag management and Entity tagging through CNCF admin/runtime surfaces.

### Detailed Tasks

- [ ] Add Tag tree browse surface.
- [ ] Add Tag create/update/move admin actions.
- [ ] Add Entity detail Tag section.
- [ ] Add Entity tag attach/detach admin actions.
- [ ] Add Entity search by Tag admin surface.
- [ ] Project Tag operation metadata in manual/help/meta output.
- [ ] Show attached Tag summaries in relevant projection records.

### Expected Output

- CNCF admin can manage the Tag tree and attach Tags to arbitrary Entities.
- Manual/meta surfaces make Tag-capable operations discoverable.

---

## TG-05: `textus-blog` CMS Tag Driver

Status: TODO

### Objective

Use `textus-blog` to validate lightweight CMS Tag operation and public tag
navigation.

### Detailed Tasks

- [ ] Add BlogPost TagAttachment usage without embedding Tag fields in BlogPost.
- [ ] Let Blog editor/register/update accept Tag refs or Tag paths.
- [ ] Synchronize BlogPost Tags after successful BlogPost mutation.
- [ ] Add public Blog search by Tag.
- [ ] Ensure parent Tag search includes BlogPosts tagged with child Tags.
- [ ] Ensure public Tag search returns only published and active BlogPosts.
- [ ] Add lightweight Tag summaries to Blog list/detail response projections.
- [ ] Keep existing Blog response compatibility where possible.

### Expected Output

- `textus-blog` validates CMS-style tags, public tag navigation, and
  descendant search as the application driver for Phase 20.

---

## TG-06: Verification, Documentation, and Phase Closure

Status: TODO

### Objective

Verify Phase 20 behavior, document decisions, and close the phase.

### Detailed Tasks

- [ ] Add CNCF specs for Tag model, tree, attachment, search, admin, and
      projection behavior.
- [ ] Add `textus-blog` specs for BlogPost tag sync and public tag search.
- [ ] Run CNCF focused Tag specs.
- [ ] Run CNCF `sbt --batch test`.
- [ ] Run `textus-blog` focused `ComponentFactorySpec`.
- [ ] Run `textus-blog` `sbt --batch test`.
- [ ] Run `git diff --check` in touched repos.
- [ ] Record deferred RDF/Knowledge Graph and DAG/polyhierarchy work.
- [ ] Mark all Phase 20 items DONE or explicitly deferred before closure.
