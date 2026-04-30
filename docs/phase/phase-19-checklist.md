# Phase 19 — BlogComponent Entity Image Usage Checklist

This document contains detailed task tracking and decisions for Phase 19.
It complements the summary-level phase document (`phase-19.md`) and may be
updated while the phase is open.

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-19.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## BI-01: Open Phase 19 and BlogComponent Image Scope

Status: DONE

### Objective

Open Phase 19 and freeze the application-driver scope around `textus-blog`
`BlogComponent`.

### Detailed Tasks

- [x] Create `docs/phase/phase-19.md`.
- [x] Create `docs/phase/phase-19-checklist.md`.
- [x] Mark Phase 19 as the current active phase in strategy.
- [x] Keep Phase 18 as the latest closed phase.
- [x] Confirm the `textus-blog` `BlogComponent` model is the active development
      driver for this phase.
- [x] Record the first expected Entity image binding use cases from
      `BlogComponent`.

### Decisions

- `BlogComponent` is the development driver.
- Blog creation is tied to a user account through the author account reference.
- Blog posts have draft/published and active/inactive lifecycle state; public
  read/search exposes published active posts, while draft and inactive posts
  remain author/admin surfaces.
- Article body `img` tags are part of the image usage scope and should be
  synchronized with managed image records and inline image bindings.
- HTML document handling is a reusable CNCF capability. Phase 19 introduces
  standard HTML tree values first; CML primitive datatype support remains a
  later decision.
- The phase is about the CNCF usage pattern for image binding, not about adding
  a new Blob payload backend.
- Blob metadata/payload ownership remains with the Phase 18 Blob foundation.
- Role-based or repeated Entity image links should use Association/Blob
  attachment semantics unless a domain-specific single reference is justified.

### Guardrails

- Do not embed payload bytes in ordinary Entity records.
- Do not implement S3/S3-compatible BlobStore provider work in this phase.
- Do not turn `BlogComponent` into a generic media-management product.

---

## BI-02: Validate BlogComponent Image Binding Model

Status: DONE

### Objective

Use `BlogComponent` to validate the minimal model for binding images to Entity
instances.

### Detailed Tasks

- [x] Confirm the `BlogPost` primary image path.
- [x] Confirm the `BlogPost` author account ownership path for draft creation.
- [x] Confirm public read/search filtering for published active posts.
- [x] Confirm draft, publish, and deactivate lifecycle operations.
- [x] Confirm repeated/role-based image binding model for cover, inline,
      thumbnail, and gallery roles.
- [x] Confirm article body `img` tag extraction and synchronization into inline
      image records.
- [x] Confirm CNCF standard HTML tree values parse full HTML, read head
      metadata, extract article fragments, inspect `img` nodes, rewrite image
      sources, and render HTML fragments.
- [x] Confirm `importPostTree` accepts a Blog article file tree archive with
      `META-INF/blog.yaml`, parses the entry HTML, and delegates normalized
      content/image input to `registerPost`.
- [x] Verify whether Blob attachment Association rows already satisfy the model.
- [x] Identify whether component-local image binding records are still needed
      or should collapse into Blob/Association usage.
- [x] Record every missing runtime capability as a Phase 19 gap.

### Expected Output

- A short usage contract for domain entities that need images.
- A decision on when a component should store a direct image reference versus
  relying on Association-backed image bindings.

### Current Validation Notes

- `textus-blog` executable specs cover managed ZIP archive import, metadata
  precedence, HTML head fallback, article extraction, inline image source
  rewriting, entity image definitions, payload-backed Blob registration, and
  direct `registerPost` rejection for path-only image specs.
- BI-02 rejects `BlogPost.primaryImageId` and Blog-local image metadata/binding
  entities as canonical state. BlogPost image links are represented by CNCF
  BlobAttachment Association rows only.
- Representative images are derived from Association roles in
  `primary -> cover -> thumbnail -> inline` order; `inline` uses the first
  managed content occurrence, while `gallery` does not act as a representative
  fallback.
- Public get/search and publish/deactivate lifecycle behavior are verified in
  `textus-blog` executable specs.
- CNCF now has a reusable managed Blob payload helper for non-Blob components
  and a core `filebundle` DataType whose client/command boundary accepts
  directories, single files, and existing ZIPs as path-preserving FileBundle
  values for direct execution and as validated ZIP payloads only for network
  transport.
- Remaining Phase 19 work moves to BI-03/BI-04: contract documentation,
  projection shape, and Web/admin/manual behavior for associated images.

---

## BI-03: Entity Image Binding Usage Contract

Status: DONE

### Objective

Define the concrete CNCF usage contract for image binding across component
operations and projection surfaces.

Contract baseline: `docs/design/entity-image-binding-usage-contract.md`.

### Detailed Tasks

- [x] Define create/update workflow for image uploads and existing Blob ids.
- [x] Define protected author/admin workflow for create draft, publish, and
      deactivate operations.
- [x] Define public read/search contract for published active blog posts.
- [x] Define article body `img` tag handling contract, including unmanaged
      source URLs, managed Blob ids, alt/title text, ordering, and binding sync.
- [x] Define file-tree import contract: local-previewable full HTML, relative
      image paths, `META-INF/blog.yaml` metadata precedence, article extraction
      rules, and fallback from HTML head only for missing metadata.
- [x] Define `registerPost` as the lower-level bulk registration boundary used
      by `importPostTree`.
- [x] Define detach/delete semantics for entity images.
- [x] Define list/search behavior for entity images.
- [x] Define Aggregate/View projection shape for associated images.
- [x] Define Web form and admin page expectations.
- [x] Define manual/help metadata expectations for image-capable operations.

### Decisions To Make

- Role names use a recommended CNCF vocabulary
  (`primary`, `cover`, `thumbnail`, `gallery`, `inline`) with application-specific
  free-string extensions.
- Primary image is derived from BlobAttachment Association roles, not stored as
  a direct Entity field by default.
- Content-bearing entities may use the first managed inline image as the
  representative fallback when no explicit primary/cover/thumbnail image exists.
- Ordered images are represented by the association `sortOrder`; projection
  rows include Blob metadata plus `associationId`, `role`, and `sortOrder`.

### Result

- Added `docs/design/entity-image-binding-usage-contract.md`.
- BI-04 inherits the implementation gaps for projection helper expansion,
  Web/admin affordances, and manual/help metadata.

---

## BI-04: Runtime/Web/Projection Gap Implementation

Status: DONE

### Objective

Implement CNCF gaps discovered by the `BlogComponent` driver.

### Candidate Work Areas

- [x] Generic `associationBinding` operation metadata and
      `AssociationBindingWorkflow` core.
- [x] Operation execution adapter for result-field source id binding,
      including `entity_id` Entity-create result handling.
- [x] Operation child Entity binding for SalesOrder/SalesOrderLine-style
      same-request child creation.
- [x] Generated relationship metadata for association/aggregation/composition,
      including composition child-parent-id-field expansion from Cozy CML.
- [x] Embedded value object composition metadata for parent-field `VALUE`
      storage without child Entity or Association creation.
- [x] `textus-blog` BlogComponent CML declares `BlogPost.images` as a
      BlobAttachment Association relationship and `BlogPost.inlineImages` as
      child-parent-id-field composition, and executable specs verify the
      generated metadata.
- [x] Broader upload/register-and-attach adapters outside Entity admin.
      Operation HTML forms and Form API definitions now expose
      `imageBinding` and `associationBinding` controls/metadata; submit paths
      preserve multipart inputs for the existing post-operation binding
      workflows.
- [x] `filebundle` client/command transport DataType.
      Directory, single-file, and existing-ZIP inputs are normalized through
      the core `FileBundle` companion without eager ZIP conversion for command
      execution; client HTTP transport converts them to `application/zip`
      `MimeBody` payloads at the network boundary.
- [x] Packaged source activation hardening for component/subsystem development
      startup.
      `--component-dev-dir` and `--subsystem-dev-dir` are documented as the
      normal selector-less edit/run routes, while expanded `car.d` and `sar.d`
      remain explicit loader/debug inputs through `--component-car-dir` and
      `--subsystem-sar-dir`.
- [x] Non-image Association binding Web/manual surfaces beyond the core
      operation metadata.
- [x] Entity create/update support for Blob attachment requests.
- [x] Projection helper expansion for `images` and `representativeImage`
      contract output.
- [x] Web/admin affordances for associated images on Entity pages, including
      role/sort-order display and attach/detach repair flows.
- [x] Descriptor/manual visibility for image-capable operations, including
      upload and existing-Blob input capabilities.
- [x] Regression coverage for Blob/Association image usage.

### Guardrails

- Keep reusable behavior in CNCF runtime/Web/projection layers.
- Keep blog-specific semantics in `textus-blog`.
- Preserve Phase 18 Blob authorization and payload-storage boundaries.

---

## AF-01: CNCF AtomFeed and Blog Application

Status: DONE

### Objective

Add a reusable CNCF Atom feed model/projection/renderer and use BlogComponent
to validate public Atom feed output for published active posts.

### Detailed Tasks

- [x] Add CNCF `AtomFeed` / `AtomEntry` / `AtomLink` / `AtomPerson` model.
- [x] Add an Atom 1.0 XML renderer with `application/atom+xml` HTTP response
      support.
- [x] Add a Record projection helper that maps title, slug, content,
      created/updated timestamps, and base URL into Atom entries.
- [x] Add BlogComponent `atomFeed` query operation.
- [x] Verify `atomFeed` excludes drafts and inactive posts, respects text and
      limit, and emits entry URLs as `baseUrl + /blog/{slug}`.
- [x] Document the Blog Atom feed URL and required site base URL setting.

### Decisions

- Atom only; RSS remains outside this slice.
- `BlogPost.content` is emitted as escaped `content type="html"` Atom content.
- `textus.site.base-url` / `cncf.site.base-url` is required for feed output.
- `canonicalPath` is not used because it is not a persisted BlogPost field.

---

## BI-05: Verification and Closure

Status: SUSPENDED

### Objective

Close Phase 19 only after the BlogComponent driver demonstrates the intended
Entity image binding usage and the reusable CNCF behavior is verified.

### Detailed Tasks

- [x] Verify `textus-blog` `BlogComponent` compile/generation path.
- [x] Verify live or executable-spec image binding flows.
- [x] Verify projection output for associated images.
- [x] Verify Web/admin/manual behavior.
- [x] Verify BlogComponent relationship metadata generation for image
      Association and inline-image composition.
- [ ] Update strategy history and close phase documents.

---

## Completion Check

Phase 19 is complete when:

- All BI items are marked DONE in this checklist.
- Corresponding checkboxes in `phase-19.md` are marked `[x]`.
- No item remains ACTIVE or SUSPENDED.
- Any remaining work is explicitly deferred to future strategy items.
