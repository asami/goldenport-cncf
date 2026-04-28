# Phase 19 — BlogComponent Entity Image Usage

status = open

## 1. Purpose of This Document

This work document records the active stack of work items for Phase 19.
It is authoritative for current scope, active work, explicit deferrals, and
closure status.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Use `textus-blog` and its `BlogComponent` as the concrete development driver
  for application-owned image usage.
- Establish the recommended CNCF usage pattern for binding images to ordinary
  Entity instances.
- Treat blog authoring as a user-account owned workflow: blog creation is tied
  to the author account, drafts are private author/admin state, and published
  active posts are public read/search state.
- Include blog lifecycle handling for draft, published, and inactive posts so
  image binding behavior is validated across public and non-public content.
- Align application image usage with the Phase 18 Blob and Association
  foundation instead of embedding payload bytes or ad hoc image fields inside
  domain entity storage.
- Cover common image roles:
  - primary/cover image;
  - inline article images;
  - thumbnail/card image;
  - gallery or ordered supporting images.
- Define how component-owned operations should register/upload image payloads,
  attach existing images to entities, detach images, and list an entity's images.
- Define how article body `img` tags are detected, registered or resolved as
  managed images, and synchronized with Entity image binding records.
- Introduce CNCF standard HTML tree values for parsing full HTML documents,
  extracting article fragments, reading head metadata, finding `img` nodes, and
  rewriting image sources. `BlogComponent` uses these values, but the values are
  not Blog-specific.
- Define how Entity read/search, Aggregate/View projection, Web forms, admin
  pages, and manual/help metadata should expose image association information.
- Use `BlogPost`, `ImageAsset`/Blob metadata, and image binding records in
  `BlogComponent` to expose gaps in CNCF runtime, Web, projection, and generated
  component behavior.

Current semantic direction:

- Blob metadata and payload storage remain owned by the CNCF Blob foundation.
- Domain entities should store image references only when the reference is part
  of their own domain contract; repeated or role-based image links should use
  the generic Association/Blob attachment model.
- Application components may provide component-specific image operations as
  workflow adapters over Blob registration and Association attachment.
- Public blog read/search should expose only published and active posts;
  protected author/admin operations own draft, publish, deactivate, and inline
  image synchronization flows.
- Inline article `img` tags are treated as first-class image occurrences that
  can be reconciled with Blob-backed image assets and `inline` image bindings.
- Blog file-tree import should parse full HTML through CNCF HTML tree values.
  `META-INF/blog.yaml` is the authoritative metadata source; missing title,
  description, and canonical fields may fall back to the HTML head.
- Projections should expose image metadata and access URLs without embedding
  payload bytes.
- Web/admin surfaces should make image association visible without requiring
  every component to hand-code Blob administration.

## 3. Non-Goals

- No new Blob payload storage backend in CNCF core.
- No S3/S3-compatible provider implementation in this phase; that remains under
  the AwsComponent development item.
- No thumbnail generation, image transformation, virus scanning, or resumable
  upload implementation.
- No replacement of the Phase 18 Blob component or BlobStore SPI.
- No general digital asset management product surface.
- No change to SimpleEntity storage-shape rules that would embed Blob payloads
  or repeated image payloads in ordinary entity records.
- No CML primitive datatype for HTML in this phase; HTML tree support is
  introduced as CNCF standard values first.

## 4. Current Work Stack

- A (DONE): BI-01 — Open Phase 19 and freeze BlogComponent image-use scope.
- B (ACTIVE): BI-02 — Validate `BlogComponent` image binding model against the Blob/Association foundation.
- C (SUSPENDED): BI-03 — Define Entity image binding usage contract for create/update/read/search/projection flows.
- D (SUSPENDED): BI-04 — Implement CNCF runtime/Web/projection gaps discovered by the BlogComponent driver.
- E (SUSPENDED): BI-05 — Verification, documentation, and phase closure.

Resume hint:

- Start from `textus-blog` `BlogComponent` and verify which image binding flows
  already work using Phase 18 Blob/Association capabilities. Record every gap as
  a CNCF runtime, Web, projection, or generator work item before changing core
  behavior.

## 5. Development Items

- [x] BI-01: Open Phase 19 and freeze BlogComponent image-use scope.
- [ ] BI-02: Validate BlogComponent image binding model against Blob/Association foundation.
- [ ] BI-03: Define Entity image binding usage contract.
- [ ] BI-04: Implement CNCF runtime/Web/projection gaps discovered by the driver.
- [ ] BI-05: Verification, documentation, and phase closure.

Detailed task breakdown and progress tracking are recorded in
`phase-19-checklist.md`.

## 6. Completion Conditions

Phase 19 can close when:

- `textus-blog` `BlogComponent` demonstrates a reproducible Entity-to-image
  binding flow.
- The recommended CNCF image usage pattern is documented in phase/checklist
  records and, where needed, design/spec notes.
- Blob/Association integration points needed by application components are
  implemented or explicitly deferred.
- Web/admin/manual/projection behavior for associated images is verified or
  explicitly deferred.
- Regression tests cover the CNCF behavior added during this phase.

## 7. Notes

- Phase 18 remains the closed Blob management foundation.
- Phase 19 is application-driver work: `BlogComponent` should expose practical
  gaps in how ordinary components use images, not redefine Blob storage.
