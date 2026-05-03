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
- Use `BlogPost`, Blob metadata, and BlobAttachment Association rows in
  `BlogComponent` to expose gaps in CNCF runtime, Web, projection, and
  generated component behavior.

Current semantic direction:

- Blob metadata and payload storage remain owned by the CNCF Blob foundation.
- Domain entities should avoid direct image fields by default; BlogComponent
  validates role-based image links through the generic BlobAttachment
  Association model, including primary image selection.
- Application components may provide component-specific image operations as
  workflow adapters over Blob registration and Association attachment.
- Public blog read/search should expose only published and active posts;
  protected author/admin operations own draft, publish, deactivate, and inline
  image synchronization flows.
- Inline article `img` tags are treated as first-class image occurrences that
  can be reconciled with Blob-backed `inline` Association rows.
- Blog file-tree import should parse full HTML through CNCF HTML tree values.
  `META-INF/blog.yaml` is the authoritative metadata source; missing title,
  description, and canonical fields may fall back to the HTML head.
- Production Blog file-tree import uses a managed Blob ZIP archive as the
  user-facing input path. The development `treeRootPath` path remains available
  as a driver path for local validation.
- `registerPost` is the lower-level registration boundary. It accepts
  normalized HTML fragment content and existing Blob references; local
  path-only image payload registration is handled by `importPostTree`.
- Projections should expose image metadata and access URLs without embedding
  payload bytes.
- Web/admin surfaces should make image association visible without requiring
  every component to hand-code Blob administration.
- CNCF should provide a reusable Atom feed model/projection/renderer so
  published Blog posts can be exposed as standards-compliant Atom XML without
  making Atom a Blog-only formatter.

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
- No finalized CML primitive datatype lock-in for HTML/Markdown/SmartDox in
  this phase; Phase 19 defines the reusable content/mimetype and document
  reference operating model first.

## 4. Current Work Stack

- A (DONE): BI-01 — Open Phase 19 and freeze BlogComponent image-use scope.
- B (DONE): BI-02 — Validate `BlogComponent` image binding model against the Blob/Association foundation.
- C (DONE): BI-03 — Define Entity image binding usage contract for create/update/read/search/projection flows.
- D (DONE): BI-04 — Implement CNCF runtime/Web/projection gaps discovered by the BlogComponent driver.
- E (DONE): AF-01 — Add CNCF AtomFeed support and apply it to BlogComponent.
- F (DONE): BW-01 — Add the component-owned Blog Web app driver surface.
- G (DONE): BI-04B — Add CNCF Textus URN and Blob inline-image workflow.
- H (DONE): CR-01 — Add SimpleEntity content reference occurrence support.
- I (DONE): CR-02 — Retire BlogInlineImage and consolidate Blog content references.
- J (PLANNED): MB-01 — Split Blob document references into image, video, attachment, and blob kinds.
- K (PLANNED): CT-01 — Define content/mimetype operation support for HTML, Markdown, and SmartDox.
- L (PLANNED): SD-01 — Add SmartDox and GFM-compatible Markdown support.
- M (SUSPENDED): BI-05 — Verification, documentation, and phase closure.

Resume hint:

- The BI-04 projection/admin/manual slice adds `images` plus derived
  `representativeImage` output, Entity admin image visibility with existing
  Blob attach/detach affordances, and operation manual metadata for
  image-capable operations. The Entity create/update slice connects
  same-request upload and existing Blob id attachment through
  `BlobAttachmentWorkflow`. The current binding-core slice adds generic
  `associationBinding` metadata and `AssociationBindingWorkflow`, with
  `imageBinding` mapped to the same internal Association path. The child
  Entity binding slice adds SalesOrder/SalesOrderLine-style same-request child
  creation using operation result `entity_id`. The relationship metadata slice
  adds generated `relationshipDefinitions` so Cozy SmartDox CML can define
  association, aggregation, and composition and expand composition
  child-parent-id-field storage into child Entity binding metadata. It also
  recognizes embedded value object composition metadata where a target `VALUE`
  is stored in a parent Entity field. The `textus-blog` driver now declares
  `BlogPost.images` as a BlobAttachment Association relationship and
  `BlogPost.inlineImages` as child-parent-id-field composition, with Cozy
  generation and executable specs validating the metadata. The current
  non-image Association admin/manual slice adds generic association list,
  attach, and detach surfaces plus Entity detail relationship sections for
  association-record relationships outside BlobAttachment images. The latest
  operation form/API adapter slice exposes `imageBinding` and
  `associationBinding` metadata on operation HTML forms and Form API
  definitions, preserves multipart upload inputs, and keeps binding-only
  fields out of operation business inputs while the post-operation binding
  workflows consume the original request. The client/command transport slice
  standardizes the core `filebundle` DataType so command execution can carry
  directory, single-file, and existing-ZIP inputs as path-preserving
  `FileBundle` values, while client HTTP transport converts them to
  `application/zip` `MimeBody` payloads at the network boundary. The packaged
  source activation hardening slice clarifies component/subsystem development
  startup: `--component-dev-dir` and `--subsystem-dev-dir` are the normal
  selector-less edit/run routes, while expanded `car.d` and `sar.d` directories
  are explicit loader/debug inputs through `--component-car-dir` and
  `--subsystem-sar-dir`. CAR Web descriptor metadata is sourced from
  `src/main/car/web`, Web application resources remain under `src/main/web`,
  and any future non-public Web app metadata is reserved for `WEB-INF`.
  AF-01 adds a reusable CNCF Atom feed model, renderer, and Record projection
  helper, then applies it to BlogComponent through a public `atomFeed` query
  that emits published active posts as `application/atom+xml`.
  BW-01 adds the `textus-blog` component-owned Web app using the Static Form
  file-layout model: `/web/blog` serves `src/main/web/index.html`,
  `/web/blog/publicblogs` serves `publicblogs.html`, `/web/blog/userblogs`
  serves the author dashboard, and `/web/blog/new` / `/web/blog/update` serve
  separate static form pages. The app can save textarea HTML fragments through
  `saveEditorPost`, upload Blog file trees through `importPostTree(fileBundle)`,
  and insert existing Blob images from an editor picker that synchronizes inline
  BlobAttachment links.
  BI-04B adds the reusable CNCF inline image core behind that Blog driver:
  `TextusUrn` and `UrnRepository` provide `urn:textus:{kind}:{value}`
  resolution, Blob registers `urn:textus:blob:{entropy}`, and UoW-backed
  Blob inline-image operations normalize HTML fragment `img` references,
  register relative filebundle images as managed Blobs, preserve or metadata-
  capture external URLs, attach inline Blob Associations, and render persisted
  Blob URNs back to public Blob content URLs.
  CR-01 generalizes this from image-only handling to SimpleEntity content
  references: `ContentReferenceOccurrence` records where a reference appears
  inside content, including implemented HTML `img/src` and `a/href` references
  plus planned video/media sources, attachments, Textus URNs, external URLs,
  and future Markdown/SmartDox references. BlobAttachment remains the
  Entity-to-Blob relationship; content occurrence data is the content-derived
  reference index. CR-02 applies that model to `textus-blog`: BlogInlineImage is
  retired from the application runtime, `BlogPost.contentAttributes.references`
  becomes the canonical occurrence store, and inline BlobAttachment
  Associations are synchronized Blob-distinct from server-derived references.
  `contentReferences` is not accepted as operation input.
  The media-kind slice splits the document-facing URN vocabulary into
  `urn:textus:image:{value}`, `urn:textus:video:{value}`,
  `urn:textus:attachment:{value}`, and `urn:textus:blob:{value}`. `attachment`
  is for CNCF-opaque formats such as Excel or Word attached as supporting
  entity material, while image/video can carry media-specific metadata as the
  applications deepen. The content/mimetype slice defines how SimpleEntity
  content stores HTML, Markdown, and SmartDox with a content mimetype/format
  contract. The SmartDox/Markdown slice makes GFM-compatible Markdown the user
  facing Markdown baseline and introduces the SmartDox Textus profile; long
  descriptive text that currently looks like localized prose should move away
  from `I18NString` and use SmartDox's own i18n model.

## 5. Development Items

- [x] BI-01: Open Phase 19 and freeze BlogComponent image-use scope.
- [x] BI-02: Validate BlogComponent image binding model against Blob/Association foundation.
- [x] BI-03: Define Entity image binding usage contract.
- [x] BI-04: Implement CNCF runtime/Web/projection gaps discovered by the driver.
- [x] AF-01: Add CNCF AtomFeed support and apply it to BlogComponent.
- [x] BW-01: Add the component-owned Blog Web app driver surface.
- [x] BI-04B: Add CNCF Textus URN and Blob inline-image workflow.
- [x] CR-01: Add SimpleEntity content reference occurrence support.
- [x] CR-02: Retire BlogInlineImage and consolidate Blog content references.
- [ ] MB-01: Split Blob document references into image, video, attachment, and blob kinds.
- [ ] CT-01: Define content/mimetype operation support for HTML, Markdown, and SmartDox.
- [ ] SD-01: Add SmartDox and GFM-compatible Markdown support.
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
- `textus-blog` exposes an Atom feed for published active posts using the CNCF
  AtomFeed helper.
- `textus-blog` exposes a component-owned Blog Web app that exercises public
  read, authenticated editor save, filebundle upload import, Blob image picker
  flows, and Static Form file-layout routing.
- SimpleEntity content reference occurrence behavior is documented and
  exercised beyond image-only `img/src` handling.
- Textus URN media/document kinds distinguish image, video, attachment, and
  generic blob references.
- SimpleEntity content format/mimetype behavior covers HTML, Markdown, and
  SmartDox as planned content formats.
- SmartDox Textus profile and GFM-compatible Markdown support are either
  implemented or explicitly deferred with remaining scope recorded.

## 7. Notes

- Phase 18 remains the closed Blob management foundation.
- Phase 19 is application-driver work: `BlogComponent` should expose practical
  gaps in how ordinary components use images, not redefine Blob storage.
