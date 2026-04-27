# Phase 18 — Builtin Blob Management Component

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 18.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Provide a builtin Blob management component for image, video, attachment, and
  generic binary assets.
- Support two Blob source modes:
  - `managed`: CNCF stores payload through BlobStore.
  - `external_url`: CNCF stores metadata and URL only.
- Store managed Blob payloads in a dedicated Blob DataStore, with S3-like
  object storage as the primary production assumption.
- Treat stored managed Blob payloads as URL-addressable assets through CNCF
  Blob routes or backend-provided object URLs.
- Keep Blob payloads outside ordinary entity records and outside SimpleEntity
  storage-shape policy.
- Provide Blob metadata as a SimpleEntity so common Entity admin/list/detail
  surfaces can inspect it.
- Provide Blob attach/list/detach through a generic Association runtime
  foundation rather than a Blob-private association table.
- Provide user-facing APIs for upload/register, read, metadata lookup,
  attach/detach, and listing entity Blobs.
- Provide admin-facing APIs and Web management pages for Blob metadata,
  associations, store status, and controlled delete/detach actions.
- Expose associated Blob metadata and display/download URLs through
  Aggregate/View projections without embedding payload bytes.

Current semantic direction:

- Blob metadata is a builtin SimpleEntity-backed management model.
- Blob payload storage is owned by a BlobStore SPI and carried in process by
  `BinaryBag`.
- BlobStore is backed by a dedicated payload DataStore, not by ordinary entity
  storage.
- Managed Blob entity records the storage reference; display/download URLs are
  resolved from that reference when the Blob is read or projected.
- Association is a CNCF runtime/entity foundation, not a standalone public
  component and not a Blob-private mechanism.
- Blob attachment uses the generic Association foundation with
  `associationDomain = blob_attachment`.
- Association repository queries are store-backed. Entity/View/Aggregate
  working sets may carry related Association snapshots when display/runtime
  residency is needed.
- Domain entity components can use associated Blob metadata without changing
  their own storage shape.
- Detaching a Blob from an entity removes the association only; deleting the
  Blob is a separate operation.

## 3. Non-Goals

- No payload embedding in ordinary entity records.
- No Aggregate/View inline payload bytes.
- No real S3 provider in the first slices.
- No assumption that Blob payload bytes are stored in EntityStore/DataStore
  tables used for domain entities.
- No signed URL, thumbnail generation, virus scanning, or resumable upload in
  the first slices.
- No generic public association component in this phase.
- No single mandatory global association table; association storage is
  policy-resolved by domain.
- No Blob payload treatment as SimpleEntity scalar/value-object storage.

## 4. Current Work Stack

- A (DONE): BL-01 — Open Phase 18 docs and freeze Blob scope/API split.
- B (DONE): BL-02 — Blob runtime model, source-mode model, BlobStore SPI, local/in-memory payload store.
- C (DONE): BL-03 — Builtin Blob component user-facing operations.
  - BL-03A (DONE): metadata and payload operations
    (`register_blob`, `read_blob`, `resolve_blob_url`, `get_blob_metadata`).
  - BL-03B (DONE): Blob metadata now persists as a SimpleEntity-backed model.
  - BL-03C (DONE): Blob attach/list/detach operations use the generic
    Association runtime foundation.
- D (DONE): BL-04 — Builtin Blob component admin-facing operations.
  - BL-04A (DONE): read-only admin operations
    (`admin_list_blobs`, `admin_get_blob`, `admin_list_blob_associations`,
    `admin_blob_store_status`).
  - BL-04B (DONE): controlled admin mutation operations
    (`admin_delete_blob`, `admin_attach_blob_to_entity`,
    `admin_detach_blob_from_entity`).
- E (DONE): BL-05 — Generic Association runtime foundation and Blob attachment usage.
- E2 (DONE): BL-05B — Application create/update Blob attachment workflow for
  uploaded payloads and existing Blob ids.
- F (DONE): BL-06 — Web/admin management pages for Blob metadata, payload links, and associations.
  - BL-06A (DONE): read-only Blob admin pages for metadata, associations,
    and store status.
  - BL-06B (DONE): mutation admin pages for delete, attach, and detach.
- G (DONE): BL-07 — Aggregate/View Blob metadata projection support.
  - BL-07A (DONE): flat Blob metadata projection on Aggregate/View read responses.
  - BL-07B (DONE): projection contract closure for ordering, empty behavior,
    top-level placement, and payload exclusion.
- H (IN PROGRESS): BL-08 — Hardening: access control, checksum/content-type/size validation, deletion semantics, and external URL safety policy.
  - BL-08A (DONE): external URL safety policy.
  - BL-08B (DONE): metadata validation for content type, byte size, and digest.

Current note:

- Phase 17 is closed and remains the SimpleEntity storage-shape baseline.
- Phase 18 starts Blob management as a separate component concern.
- Latest implementation snapshot:
  - `992d1d6 Add blob metadata projection to admin views`
  - `cca7e38 Close blob projection contract`
  - `8f87196 Harden external blob URLs`
  - Current change: validate Blob registration metadata.
  - BL-07B closes the projection contract:
    Aggregate/View output uses a flat, additive `blobs` field, omits it when
    empty, orders rows by `sortOrder`, and never embeds payload bytes.
  - BL-08A starts hardening with external URL safety policy.
  - BL-08B adds metadata-only validation for managed payload size/digest and
    content-type parsing without introducing MIME-kind policy.
- `docs/journal/2026/04/blob-management-component-specification-note.md` is the
  source exploration note for this phase.

## 5. Development Items

- [x] BL-01: Open Phase 18 docs and freeze Blob scope/API split.
- [x] BL-02: Blob runtime model, source-mode model, BlobStore SPI, local/in-memory payload store.
- [x] BL-03: Builtin Blob component user-facing operations.
  - [x] BL-03A: Blob metadata and payload operations.
- [x] BL-04: Builtin Blob component admin-facing operations.
  - [x] BL-04A: Read-only admin operations.
  - [x] BL-04B: Controlled admin mutation operations.
- [x] BL-05: Generic Association runtime foundation and Blob attachment usage.
- [x] BL-05B: Entity create/update Blob upload and existing Blob id attachment workflow.
- [x] BL-06: Web/admin management pages for Blob metadata, payload links, and associations.
  - [x] BL-06A: Read-only Blob admin pages.
  - [x] BL-06B: Mutation Blob admin pages.
- [x] BL-07: Aggregate/View Blob metadata projection support.
  - [x] BL-07A: Flat Blob metadata projection.
  - [x] BL-07B: Projection contract closure.
- [ ] BL-08: Hardening: access control, checksum/content-type/size validation, deletion semantics, and external URL safety policy.
  - [x] BL-08A: External URL safety policy.
  - [x] BL-08B: Blob metadata validation.

## 6. Public Interface Direction

Blob metadata fields:

- `id` as the Blob EntityId
- `kind`
- `sourceMode`
- `filename`
- `contentType`
- `byteSize`
- `checksum` / `digest`
- `storageRef` for managed blobs
- `externalUrl` for external URL blobs
- `displayUrl` / `downloadUrl` resolved from either `storageRef` or
  `externalUrl`
- lifecycle/audit fields

Association fields used by Blob attachment:

- `associationId`
- `sourceEntityId`
- `targetEntityId` = Blob EntityId
- `targetKind` = `blob`
- `associationDomain` = `blob_attachment`
- `role`
- optional ordering for galleries/attachments
- lifecycle/audit fields

User-facing Blob operations:

- `register_blob`
- `read_blob`
- `resolve_blob_url`
- `get_blob_metadata`
- `attach_blob_to_entity`
- `detach_blob_from_entity`
- `list_entity_blobs`

Application create/update attachment convention:

- Uploaded payloads: `blob.<role>` or `blob.<role>.<index>`
- Existing Blob references: `blobId.<role>` or `blobId.<role>.<index>`
- Optional metadata: `blob.<role>.kind`, `blob.<role>.filename`,
  `blob.<role>.sortOrder`, `blobId.<role>.sortOrder`
- CNCF provides a compensating workflow helper; application operations opt in
  explicitly and keep Blob payload bytes outside entity records.

Admin-facing Blob operations:

- `admin_list_blobs`
- `admin_get_blob`
- `admin_delete_blob`
- `admin_list_blob_associations`
- `admin_attach_blob_to_entity`
- `admin_detach_blob_from_entity`
- `admin_blob_store_status`

Web management pages:

- `/web/blob/admin`
- `/web/blob/admin/blobs`
- `/web/blob/admin/blobs/{id}`
- `/web/blob/admin/associations`
- `/web/blob/admin/store`

## 7. References

- `docs/journal/2026/04/blob-management-component-specification-note.md`
- `docs/strategy/cncf-development-strategy.md`
- `docs/phase/phase-17.md`
- `docs/design/simpleentity-storage-shape-policy.md`
