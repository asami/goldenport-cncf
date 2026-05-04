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
- J (DONE): MB-01 — Split Blob document references into image, video, audio, attachment, and blob kinds.
- K (DONE): CT-01 — Define content/mimetype operation support for HTML, Markdown, and SmartDox.
- L (ACTIVE): SD-01 — Add SmartDox and GFM-compatible Markdown support.
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
  capture external URLs, attach inline media/blob Associations, and render
  persisted URNs back to public Blob content URLs.
  CR-01 generalizes this from image-only handling to SimpleEntity content
  references: `ContentReferenceOccurrence` records where a reference appears
  inside content, including implemented HTML `img/src` and `a/href` references
  plus planned video/media sources, attachments, Textus URNs, external URLs,
  and future Markdown/SmartDox references. Entity-to-media/blob relationships
  are separate from content occurrence data, which remains the content-derived
  reference index. CR-02 applies that model to `textus-blog`: BlogInlineImage is
  retired from the application runtime, `BlogPost.contentAttributes.references`
  becomes the canonical occurrence store, and inline Associations are
  synchronized target-distinct from server-derived references.
  `contentReferences` is not accepted as operation input.
  MB-01 turns document-facing media references into CNCF builtin media
  Entities layered over BlobStore Blob metadata. `Image`, `Video`, `Audio`,
  and `Attachment` each point at a Blob with `blobId`; `Blob` remains the
  generic fallback Entity for storage-level access, compatibility, and payloads
  that do not fit one of the media kinds. Textus URNs now include
  `urn:textus:image:{entropy}`, `urn:textus:video:{entropy}`,
  `urn:textus:audio:{entropy}`, `urn:textus:attachment:{entropy}`, and the
  compatibility/fallback `urn:textus:blob:{entropy}` form. Blog inline image
  content is normalized to image URNs and synchronized through
  `MediaAttachment`, while `BlobAttachment` remains available for low-level
  Blob links and compatibility. CT-01 defines SimpleEntity content storage and
  rendering with `ContentBody`, `mimeType`, `charset`, and `ContentMarkup`.
  HTML fragments are stored as single-body content and rendered inside an
  article wrapper; GFM-compatible Markdown renders to HTML, including tables.
  SD-01 adds the first SmartDox content core in `simplemodeling-lib`: a Scala 3
  parser/AST, safe HTML renderer, and reference extractor used by CNCF content
  rendering and normalization. The port covers the content subset needed for
  CNCF, including headings, paragraphs, lists, pipe tables, links, images,
  source blocks, comments, and XML/JSON structured tokens. SmartDox source
  text is not rewritten yet; normalized image/media identity is kept in
  `ContentReferenceOccurrence` metadata until source-span-aware rewriting is
  available. GFM-compatible Markdown inline `![alt](src)` and `[label](href)`
  references are parsed through the same flexmark configuration used for
  rendering; image destinations are normalized to canonical image URNs while
  link destinations are indexed without rewriting. HTML, Markdown, and SmartDox
  image normalization keep successful image rewrites/metadata even when another
  image fails; failed image references are left in the source with a
  `textus:image-normalization-failed` comment so authors can fix only the
  failed reference. Reference-style Markdown links/images remain deferred.
  Content body storage uses charset-aware byte sizing to keep small bodies
  inline and overflow larger bodies to content side storage without exposing DB
  storage choices to application models.
  Binary/opaque payloads remain outside ordinary content bodies and belong
  under Blob/media/attachment boundaries. The `textus-blog` Web driver now
  resolves CNCF and
  SimpleModeling versions from project-local `versions/` files rather than a
  `cncf-samples` root.

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
- [x] MB-01: Split Blob document references into image, video, audio, attachment, and blob kinds.
- [x] CT-01: Define content/mimetype operation support for HTML, Markdown, and SmartDox.
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
- Textus URN media/document kinds distinguish image, video, audio, attachment,
  and generic blob references.
- SimpleEntity content format/mimetype behavior covers HTML, Markdown, and
  SmartDox as planned content formats.
- SmartDox Textus profile and GFM-compatible Markdown support are either
  implemented or explicitly deferred with remaining scope recorded.

## 7. Notes

- Phase 18 remains the closed Blob management foundation.
- Phase 19 is application-driver work: `BlogComponent` should expose practical
  gaps in how ordinary components use images, not redefine Blob storage.
