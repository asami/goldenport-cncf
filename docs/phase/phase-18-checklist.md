# Phase 18 — Builtin Blob Management Component Checklist

This document contains detailed task tracking and decisions for Phase 18.
It complements the summary-level phase document (`phase-18.md`) and may be
updated freely while the phase is open.

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-18.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## BL-01: Phase 18 Open and Blob Scope/API Split

Status: DONE

### Objective

Open Phase 18 and freeze the initial Blob management scope before runtime
implementation starts.

### Detailed Tasks

- [x] Create `docs/phase/phase-18.md`.
- [x] Create `docs/phase/phase-18-checklist.md`.
- [x] Mark Phase 18 as the current active phase in strategy.
- [x] Keep Phase 17 as the latest closed phase.
- [x] Record Blob source modes: `managed` and `external_url`.
- [x] Record Blob attachment as the first Association runtime use case.
- [x] Record user-facing and admin-facing API directions.
- [x] Record Web management page direction.
- [x] Record Aggregate/View metadata+URL projection direction.

### Decisions

- Blob payloads are not embedded in ordinary entity records.
- Managed Blob payloads are stored in a dedicated Blob DataStore, with S3-like
  object storage as the production-oriented target.
- Managed Blob payloads are expected to be URL-addressable after storage,
  either through CNCF Blob routes or backend-provided object URLs.
- Blob metadata is represented as a SimpleEntity-backed model so common Entity
  admin/list/detail surfaces can inspect it.
- Association is a CNCF runtime/entity foundation. Blob attachment is its first
  concrete use case.
- Association storage is policy-resolved by domain; Phase 18 does not require a
  single global physical table for every association use case.
- Aggregate/View output contains Blob metadata and display/download URLs only.
- `BinaryBag` is the in-process payload carrier for managed Blob payloads.
- Detach removes an association only; delete Blob is a separate operation.

### Guardrails

- Do not implement runtime code in BL-01.
- Do not add real S3, signed URL, thumbnail, virus scan, or resumable upload
  requirements to the first implementation slice.
- Do not add a generic public association component in this phase.
- Keep generic association APIs internal to runtime/component implementation
  until a public operational need is proven.

---

## BL-02: Blob Runtime Model and BlobStore SPI

Status: DONE

### Objective

Add the Blob runtime model, source-mode model, BlobStore SPI, and local/in-memory
payload store implementations.

The SPI must model Blob payload storage as a dedicated DataStore concern. Local
and in-memory stores are development/test backends; production design should
assume an S3-like object store that can produce or support URL-addressable Blob
resources.

### Expected Runtime Types

- [x] Blob identity uses `EntityId`
- [x] `BlobKind`
- [x] `BlobSourceMode`
- [x] `BlobMetadata`
- [x] `BlobStorageRef`
- [x] `BlobAccessUrl`
- [x] `BlobPutRequest`
- [x] `BlobPutResult`
- [x] `BlobReadResult`
- [x] `BlobStoreStatus`
- [x] `BlobStore`

`BlobMetadata` remains an API/read projection. BL-03B adds the persistent
`Blob` SimpleEntity model that owns metadata storage.

### Expected Coverage

- [x] Managed Blob payload can be stored and retrieved with `BinaryBag`.
- [x] Managed Blob payload storage returns a deterministic `BlobStorageRef`.
- [x] Managed Blob payload can resolve display/download URL metadata without
  embedding bytes in Entity, Aggregate, or View records.
- [ ] External URL Blob metadata can be registered without payload storage.
- [x] Missing Blob payload by `BlobStorageRef` returns deterministic failure
  because the reference represents an already-registered stored payload.
- [x] I/O failures while resolving an existing storage path are preserved as
  `Consequence.Failure`.
- [x] Local store writes payload outside entity records.

External URL registration remains for BL-03 because BL-02 only defines source
mode and BlobStore runtime semantics.

---

## BL-03: User-Facing Blob Operations

Status: DONE

### Objective

Expose application/user-facing Blob operations for managed and external URL
Blob flows.

### BL-03A: Metadata and Payload Operations

Status: DONE

BL-03A provides the first user-facing Blob component surface:

- [x] builtin `blob` component is installed in the default subsystem.
- [x] `BlobMetadata` records managed/external URL Blob metadata as an API/read
      projection.
- [x] EntityStore-backed Blob repository supports local/test flows through
      runtime DataStore configuration.
- [x] `register_blob` registers managed payloads through `BlobStore`.
- [x] `register_blob` registers `external_url` Blobs as metadata-only rows.
- [x] `read_blob` returns managed Blob payload bytes as an HTTP binary response.
- [x] `resolve_blob_url` returns external URL Blob access metadata without
      payload bytes.
- [x] `read_blob` rejects `external_url` Blobs because they have no managed
      payload.
- [x] `get_blob_metadata` returns Blob metadata records.
- [x] invalid kind/source mode and missing Blob ids fail deterministically.

BL-03A intentionally does not implement entity association operations. Those
belong to BL-05 so generic association data can be modeled explicitly.

### BL-03B: Blob SimpleEntity Persistence

Status: DONE

BL-03B changes Blob metadata persistence from an in-memory metadata map to a
SimpleEntity-backed `Blob` model:

- [x] Add `Blob` entity and `BlobCreate` create model.
- [x] Add EntityStore-backed `BlobRepository`.
- [x] Keep `BlobMetadata` as an API/read projection.
- [x] Expose Blob entity metadata through the builtin Blob component descriptor.
- [x] Keep payload bytes in BlobStore only.
- [x] Keep `register_blob`, `read_blob`, `resolve_blob_url`, and
      `get_blob_metadata` public operation behavior stable.

### BL-03C: Blob Attachment User Operations

Status: DONE

BL-03C adds Blob user-facing association operations on top of the generic
Association runtime foundation:

- [x] `attach_blob_to_entity`
- [x] `detach_blob_from_entity`
- [x] `list_entity_blobs`
- [x] Use `associationDomain = blob_attachment`.
- [x] Store the Blob id as association target with `targetKind = blob`.
- [x] Detach removes association only and does not delete Blob metadata or
      payload.

### Expected Operations

- [x] `register_blob`
- [x] `read_blob`
- [x] `resolve_blob_url`
- [x] `get_blob_metadata`
- [x] `attach_blob_to_entity`
- [x] `detach_blob_from_entity`
- [x] `list_entity_blobs`

---

## BL-04: Admin-Facing Blob Operations

Status: DONE

### Objective

Expose operator/admin Blob APIs for diagnostics and controlled management.

### BL-04A: Read-Only Admin Operations

Status: DONE

BL-04A provides the read-only operator surface needed before Web/admin pages:

- [x] `admin_list_blobs`
- [x] `admin_get_blob`
- [x] `admin_list_blob_associations`
- [x] `admin_blob_store_status`

The operations are diagnostic/read-only. They must not delete payloads, mutate
Blob metadata, or change associations.
List operations use bounded `offset`/`limit` paging so Web/admin pages do not
materialize the full Blob or Blob association inventory in one response.

### BL-04B: Controlled Admin Mutation Operations

Status: DONE

BL-04B adds controlled mutation after the read-only surface is stable:

- [x] `admin_delete_blob`
- [x] `admin_attach_blob_to_entity`
- [x] `admin_detach_blob_from_entity`

Delete semantics:

- default delete rejects Blobs that still have Blob associations.
- `force=true` deletes referencing Blob associations before deleting metadata.
- managed Blob delete removes Blob metadata first, then removes the managed
  payload through `BlobStore`.
- external URL Blob delete removes metadata only.
- payload delete failure is reported after metadata deletion; orphan payload
  cleanup/retention policy is deferred to BL-08.

---

## BL-05: Generic Association Runtime Foundation

Status: DONE

### Objective

Implement a generic Association runtime/entity foundation that Blob attachment
can use without introducing a public association component.

### Implemented Baseline

- [x] Add `org.goldenport.cncf.association` runtime package.
- [x] Add `Association` SimpleEntity-backed model.
- [x] Add `AssociationRepository`.
- [x] Add `AssociationStoragePolicy` with shared and domain-specific
      collection support.
- [x] Keep `AssociationRepository` store-backed; repository-level working set is
      intentionally not part of the Association foundation.

### Storage Policy

- Default logical model is generic `Association`.
- Physical collection is resolved by `AssociationStoragePolicy`.
- Blob attachment uses `association_blob_attachment` as a domain-specific
  collection.
- A single global table is not required for high-volume domains such as likes.

### Residency Policy

- Store remains the source of truth for Association repository queries.
- Entity/View/Aggregate working sets may carry related Association snapshots for
  display or low-latency reads.
- Blob display optimization belongs to BL-07 Aggregate/View projection support,
  not to `AssociationRepository`.
- Logically deleted associations are not returned by normal EntityStore search.

### Expected Roles

- `mainImage`
- `galleryImage`
- `video`
- `attachment`
- `avatar`
- custom role string

---

## BL-05B: Entity Create/Update Blob Attachment Workflow

Status: DONE

### Objective

Allow application create/update operations to attach media in the same request,
without embedding payload bytes in entity records.

### Implemented Baseline

- [x] Add `BlobAttachmentWorkflow`.
- [x] Support uploaded payload fields:
      `blob.<role>` and `blob.<role>.<index>`.
- [x] Support existing Blob references:
      `blobId.<role>` and `blobId.<role>.<index>`.
- [x] Register uploaded payloads as managed Blobs in `BlobStore`.
- [x] Validate existing Blob ids through Blob metadata lookup.
- [x] Attach uploaded and existing Blobs through the generic Association
      foundation.
- [x] Compensate newly uploaded Blobs and newly created associations when the
      workflow fails.
- [x] Keep existing Blob ids out of compensation deletion.
- [x] Render `XBlob` operation fields as file inputs in generated Web forms.
- [x] Project `XBlob` operations as `multipart/form-data` in OpenAPI.

### Request Convention

- Upload: `blob.<role>` / `blob.<role>.<index>`
- Existing reference: `blobId.<role>` / `blobId.<role>.<index>`
- Metadata: `blob.<role>.kind`, `blob.<role>.filename`, `*.sortOrder`

### Compensation Policy

- Newly uploaded Blob metadata and payload are removed if later attachment
  fails.
- Associations created in the same request are removed if a later attachment
  fails.
- Existing Blob metadata and payload are never deleted by this workflow.
- Domain entity rollback is caller-supplied for create flows and not automatic
  for update flows.

---

## BL-06: Blob Web/Admin Management Pages

Status: DONE

### Objective

Add Web management pages for Blob metadata, payload links, associations, and
store status.

### BL-06A: Read-Only Admin Pages

Status: DONE

BL-06A adds the first browser management surface for existing Blob admin
operations.

- [x] `/web/blob/admin`
- [x] `/web/blob/admin/blobs`
- [x] `/web/blob/admin/blobs/{id}`
- [x] `/web/blob/admin/associations`
- [x] `/web/blob/admin/store`
- [x] Pages use existing Blob admin operations as the data source.
- [x] Blob list/detail pages show metadata and access URLs only.
- [x] Association page supports source/blob/role filters.
- [x] Store page shows BlobStore backend status.
- [x] Mutation pages remain deferred to BL-06B.

### Expected Pages

- `/web/blob/admin`
- `/web/blob/admin/blobs`
- `/web/blob/admin/blobs/{id}`
- `/web/blob/admin/associations`
- `/web/blob/admin/store`

### BL-06B: Mutation Admin Pages

Status: DONE

Add Web flows for controlled admin delete, attach, and detach operations.

- [x] Blob detail page links to delete confirmation.
- [x] Delete confirmation page shows Blob metadata and explicit `force`.
- [x] Delete submit uses `admin_delete_blob`.
- [x] Association page includes attach form.
- [x] Association rows include detach actions.
- [x] Attach/detach submits use existing Blob admin operations.
- [x] Mutation failures render structured Web errors.
- [x] Production anonymous access to mutation routes is denied.

---

## BL-07: Aggregate/View Blob Metadata Projection

Status: IN PROGRESS

### BL-07A: Flat Blob Metadata Projection

Status: DONE

### Objective

Expose associated Blob metadata and display/download URLs through Aggregate/View
projection without embedding payload bytes.

### Implementation Decisions

- [x] Use a flat additive `blobs` field in Aggregate/View response records.
- [x] Resolve rows from Blob-owned `blob_attachment` associations.
- [x] Include Blob metadata plus `associationId`, `role`, and `sortOrder`.
- [x] Do not embed managed payload bytes.
- [x] Do not reintroduce Association repository working sets.
- [x] Do not change generated Aggregate/View model construction.

### Acceptance Checks

- [x] Aggregate detail response includes associated Blob metadata.
- [x] Aggregate list item response includes associated Blob metadata.
- [x] View detail response includes associated Blob metadata.
- [x] Blob projection preserves display/download URL metadata only.

---

## BL-08: Blob Hardening

Status: PLANNED

### Objective

Harden Blob management after the basic runtime and Web/API flows exist.

### Deferred Hardening Items

- access control details
- checksum/content-type/size validation
- deletion and retention semantics
- external URL safety policy
- signed URL support
- real S3-compatible backend
- thumbnail generation
- virus scanning
- resumable upload

---

## Completion Check

Phase 18 is complete when:

- all BL items are marked DONE or explicitly deferred,
- corresponding checkboxes in `phase-18.md` are updated,
- Blob metadata, payload, association, Web/admin, and Aggregate/View visibility
  requirements have executable coverage or documented deferral.
