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

Status: DONE

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
- Tag names/paths use dot notation such as `a.b.c`.
- TagSpace is an operational/application/user boundary for independent Tag
  trees. Runtime resolution merges Subsystem, Component, and User TagSpaces
  from `ExecutionContext`.
- Only TagSpaces selected by the runtime/component are resident; canonical Tag
  records remain store-backed.
- Operational TagSpaces are master-like and maintained by operations. Shared
  application TagSpaces, such as `blog`, can be edited by application users.
  User TagSpaces, such as EC personal tags, are supported as a separate usage
  style.
- `Tag` can be used as lightweight CMS tagging or powertype-like external
  classification.
- Entity-to-Tag links are external Association records, not embedded fields on
  the tagged Entity.
- Tag tree lookup is resident for selected TagSpaces, but canonical records
  stay in EntityStore.
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

Status: DONE

### Objective

Add the builtin Tag Entity model and the runtime tree used for lookup,
navigation, and descendant expansion.

### Detailed Tasks

- [x] Add Tag Entity collection metadata as CNCF builtin master data.
- [x] Define Tag-specific fields:
  - TagSpace;
  - key/code;
  - parent Tag id;
  - path;
  - usage kind: `powertype`, `cms`, `navigation`, or `general`;
  - sort order.
- [x] Reuse SimpleEntity descriptive/content attributes for multilingual
      title, description, and longer Tag explanation.
- [x] Validate missing parent, cycle, duplicate sibling key, and invalid
      key/path as deterministic failures.
- [x] Add Tag tree loader from normal EntityStore paths.
- [x] Add TagSpace-scoped tree cache and runtime effective TagSpace merge.
- [x] Add lookup by id, entropy, key, and path.
- [x] Add descendant expansion excluding logically deleted Tags.
- [x] Expose effective Working Set policy as `resident-all`.

### Expected Output

- Tag records can be created, searched, rendered, and described like normal
  SimpleEntities.
- The resident Tag tree can be rebuilt from canonical Tag records.
- Effective runtime lookup merges Subsystem, Component, User, and explicit
  workflow TagSpaces. Merged trees are built from cached per-space resident
  trees instead of bypassing the TagTree cache.

---

## TG-03: TagAttachment Association and Tag-Expanded Entity Search

Status: DONE

### Objective

Add Association-backed Entity tagging and search by Tag, including descendant
expansion.

### Detailed Tasks

- [x] Add `AssociationDomain.TagAttachment`.
- [x] Add TagAttachment storage policy.
- [x] Add attach/detach/list workflow for arbitrary source Entity ids.
- [x] Make duplicate `(sourceEntityId, tagId, role)` attachment idempotent.
- [x] Add `tag_search_entities` behavior:
  - resolve Tag by id/entropy/key/path;
  - expand descendants by default;
  - find matching source Entity ids from TagAttachment;
  - load/search target Entities through normal EntityStore access scope.
- [x] Add direct-only search option with `includeDescendants = false`.
- [x] Ensure deleted/out-of-scope Tags and Entities are not returned.

### Expected Output

- Tags behave as external classification data and do not change domain Entity
  storage shape.
- Parent Tag search finds Entities tagged with child Tags.
- Generic `tag_search_entities` returns source ids plus visible Entity data
  after EntityStore filtering, rather than returning raw Association source ids
  alone.

---

## TG-04: CNCF Admin, Manual, and Projection Surfaces

Status: TODO

### Objective

Expose Tag management and Entity tagging through CNCF admin/runtime surfaces.

### Detailed Tasks

- [x] Add runtime operations for Tag tree browse/create, attach/detach,
      list tags, and Entity search by Tag.
- [ ] Add generic admin page/UI for Tag tree browse.
- [ ] Add generic admin create/update/move UI for Tags.
- [ ] Add Entity detail Tag section.
- [ ] Add Entity tag attach/detach admin UI affordances.
- [ ] Add Entity search by Tag admin surface.
- [ ] Project Tag operation metadata in manual/help/meta output.
- [x] Show attached Tag summaries in relevant projection records where the
      application driver uses Tags.

### Expected Output

- CNCF admin can manage the Tag tree and attach Tags to arbitrary Entities.
- Manual/meta surfaces make Tag-capable operations discoverable.

---

## TG-05: `textus-blog` CMS Tag Driver

Status: DONE

### Objective

Use `textus-blog` to validate lightweight CMS Tag operation and public tag
navigation.

### Detailed Tasks

- [x] Add BlogPost TagAttachment usage without embedding Tag fields in BlogPost.
- [x] Use shared `blog` TagSpace for BlogPost tags.
- [x] Let Blog editor/register/update accept Tag refs or Tag paths.
- [x] Synchronize BlogPost Tags after successful BlogPost mutation.
- [x] Add public Blog search by Tag.
- [x] Ensure parent Tag search includes BlogPosts tagged with child Tags.
- [x] Ensure public Tag search returns only published and active BlogPosts.
- [x] Add lightweight Tag summaries to Blog list/detail response projections.
- [x] Keep existing Blog response compatibility where possible.

### Expected Output

- `textus-blog` validates CMS-style tags, public tag navigation, and
  descendant search as the application driver for Phase 20.
- Blog uses the shared `blog` TagSpace. Tags are shared across Blog authors,
  while tagged BlogPosts remain filtered by normal public lifecycle visibility.

---

## TG-06: Verification, Documentation, and Phase Closure

Status: TODO

### Objective

Verify Phase 20 behavior, document decisions, and close the phase.

### Detailed Tasks

- [x] Add CNCF specs for Tag model, tree, attachment, and search behavior.
- [ ] Add CNCF specs for generic admin UI/projection behavior.
- [x] Add `textus-blog` specs for BlogPost tag sync and public tag search.
- [x] Run CNCF focused Tag specs.
- [x] Run CNCF `sbt --batch test`.
- [x] Run `textus-blog` focused `ComponentFactorySpec`.
- [x] Run `textus-blog` `sbt --batch test`.
- [x] Run `git diff --check` in touched repos.
- [ ] Record deferred RDF/Knowledge Graph and DAG/polyhierarchy work.
- [ ] Mark all Phase 20 items DONE or explicitly deferred before closure.
