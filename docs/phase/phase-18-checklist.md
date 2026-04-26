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
- [x] Record Blob-owned association as the Phase 18 default.
- [x] Record user-facing and admin-facing API directions.
- [x] Record Web management page direction.
- [x] Record Aggregate/View metadata+URL projection direction.

### Decisions

- Blob payloads are not embedded in ordinary entity records.
- Managed Blob payloads are stored in a dedicated Blob DataStore, with S3-like
  object storage as the production-oriented target.
- Managed Blob payloads are expected to be URL-addressable after storage,
  either through CNCF Blob routes or backend-provided object URLs.
- Blob component owns association records for Phase 18.
- Aggregate/View output contains Blob metadata and display/download URLs only.
- `BinaryBag` is the in-process payload carrier for managed Blob payloads.
- Detach removes an association only; delete Blob is a separate operation.

### Guardrails

- Do not implement runtime code in BL-01.
- Do not add real S3, signed URL, thumbnail, virus scan, or resumable upload
  requirements to the first implementation slice.
- Do not turn Blob association into a generic arbitrary relation component in
  this phase.

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

- [x] `BlobId`
- [x] `BlobKind`
- [x] `BlobSourceMode`
- [ ] `BlobMetadata`
- [x] `BlobStorageRef`
- [x] `BlobAccessUrl`
- [x] `BlobPutRequest`
- [x] `BlobPutResult`
- [x] `BlobReadResult`
- [x] `BlobStoreStatus`
- [x] `BlobStore`

`BlobMetadata` remains for BL-03 because metadata Entity and user/admin
operations are intentionally out of BL-02 scope.

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

Status: ACTIVE

### Objective

Expose application/user-facing Blob operations for managed and external URL
Blob flows.

### Expected Operations

- `register_blob`
- `read_blob`
- `get_blob_metadata`
- `attach_blob_to_entity`
- `detach_blob_from_entity`
- `list_entity_blobs`

---

## BL-04: Admin-Facing Blob Operations

Status: PLANNED

### Objective

Expose operator/admin Blob APIs for diagnostics and controlled management.

### Expected Operations

- `admin_list_blobs`
- `admin_get_blob`
- `admin_delete_blob`
- `admin_list_blob_associations`
- `admin_attach_blob_to_entity`
- `admin_detach_blob_from_entity`
- `admin_blob_store_status`

---

## BL-05: Blob-Owned Entity Association Model

Status: PLANNED

### Objective

Implement Blob-owned association records linking arbitrary entity ids to Blob
metadata with role and ordering support.

### Expected Roles

- `mainImage`
- `galleryImage`
- `video`
- `attachment`
- `avatar`
- custom role string

---

## BL-06: Blob Web/Admin Management Pages

Status: PLANNED

### Objective

Add Web management pages for Blob metadata, payload links, associations, and
store status.

### Expected Pages

- `/web/blob/admin`
- `/web/blob/admin/blobs`
- `/web/blob/admin/blobs/{blobId}`
- `/web/blob/admin/associations`
- `/web/blob/admin/store`

---

## BL-07: Aggregate/View Blob Metadata Projection

Status: PLANNED

### Objective

Expose associated Blob metadata and display/download URLs through Aggregate/View
projection without embedding payload bytes.

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
