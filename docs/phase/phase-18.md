# Phase 18 — Builtin Blob Management Component

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 18.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Provide a builtin Blob management component for image, video, attachment, and
  generic binary assets.
- Develop the authorization capabilities required to make Blob management safe
  and operable. Blob remains the main product goal; authorization work is
  included when it is required by Blob flows.
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
- Establish the practical authorization support needed by Blob flows:
  - operation/admin admission;
  - UnitOfWork/resource chokepoints;
  - subject-side role/scope/capability inputs;
  - object-side Entity permission and relation grants;
  - guard predicates such as privilege ceiling and ABAC;
  - create/list/read/update/delete authorization for EntityCollection-like
    resources;
  - generic Association-domain authorization for attach/detach-style flows.

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
- Authorization decisions are capability-oriented with explicit guards:
  `privilege` and ABAC are guard predicates, while role, permission, relation,
  and future ACL are capability grant sources.

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
- No first-class arbitrary ACL list in this phase. ACL remains a lower-priority
  Security work item under strategy section 8.3 unless Blob later proves an
  immediate need.
- No full organization-grade subject grant administration UI in this phase.
  Phase 18 may add only the minimal runtime/config surfaces required by Blob
  authorization. Broader identity/role administration remains 8.3 Security work.

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
  - BL-08C (DONE): Blob FunctionalActionCall Entity access chokepoint boundary.
  - BL-08D (DONE): ProcedureActionCall DSL foundation.
  - BL-08E (DONE): Blob authorization policy and preflight.
- I (IN PROGRESS): BL-09 — Blob-required authorization support.
  - BL-09A (DONE): guard/capability authorization concept refinement.
  - BL-09B (DONE): minimal subject-side grant/config surface for roles,
    scopes, capabilities, and create/use grants needed by Blob.
  - BL-09C (PLANNED): object/resource-side access policy surface for
    Blob EntityCollection, Blob attachment Association domain, and BlobStore
    resources.
  - BL-09D (PLANNED): Blob operation integration on the generic authorization
    policy surface.
  - BL-09E (PLANNED): introspection/manual/admin visibility for effective
    Blob authorization policy.

Current note:

- Phase 17 is closed and remains the SimpleEntity storage-shape baseline.
- Phase 18 starts Blob management as a separate component concern. Authorization
  work in this phase is driven by Blob's concrete requirements.
- Latest implementation snapshot:
  - `992d1d6 Add blob metadata projection to admin views`
  - `cca7e38 Close blob projection contract`
  - `8f87196 Harden external blob URLs`
  - `2d00f58 Harden blob action chokepoints`
  - Current change: add the minimal BL-09B subject-side grant surface. Subsystem
    descriptors can define `security.authorization.roles`, role definitions
    expand transitively into `SecuritySubject` capabilities, and canonical
    collection/association/store grant helpers are available for Blob policy
    integration.
  - BL-07B closes the projection contract:
    Aggregate/View output uses a flat, additive `blobs` field, omits it when
    empty, orders rows by `sortOrder`, and never embeds payload bytes.
  - BL-08A starts hardening with external URL safety policy.
  - BL-08B adds metadata-only validation for managed payload size/digest and
    content-type parsing without introducing MIME-kind policy.
  - BL-08C fixes the production operation boundary: Blob metadata and
    Association-backed attachment access use `FunctionalActionCall` / UoW
    EntityStore operations for authorization and observability. Direct
    repositories remain low-level adapters, not public operation boundaries.
    The Blob component port and default service expose BlobStore capability
    only, not repository-backed user/admin metadata operations. Managed
    registration deletes payloads only when metadata creation definitely failed
    or metadata cleanup has succeeded; if cleanup of created metadata fails,
    the payload is preserved so metadata is not left pointing at missing bytes.
  - BL-08D adds a protected ProcedureActionCall helper for explicit UoW program
    execution. It keeps procedural style optional and does not move Blob away
    from FunctionalActionCall.
  - BL-08E adds an explicit UoW authorization preflight operation and applies
    it to Blob registration and source-entity Blob association flows. Managed
    Blob payload storage now happens after metadata-create authorization, and
    user-facing attach/detach/list requires a canonical source EntityId.
  - BL-09 begins Blob-required authorization support. The model separates guard
    predicates (`privilege`, ABAC, operation/runtime mode) from capability
    grants (role, permission, relation, future ACL) and applies that model only
    as far as Blob create/read/attach/detach/delete/store-status flows need.
  - BL-09B adds descriptor-backed role-to-capability expansion and verifies that
    raw request `capability` remains a required capability, not a forged subject
    grant.
  - UoW-backed application create/update Blob attachment workflow adapters are
    split out as follow-up hardening work.
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
  - [x] BL-08C: Blob FunctionalActionCall Entity access chokepoint boundary.
  - [x] BL-08D: ProcedureActionCall DSL foundation.
  - [x] BL-08E: Blob authorization policy and preflight.
- [ ] BL-09: Blob-required authorization support.
  - [x] BL-09A: Guard/capability authorization concept refinement.
  - [x] BL-09B: Minimal subject-side grant/config surface.
  - [ ] BL-09C: Object/resource-side access policy surface.
  - [ ] BL-09D: Blob integration on generic authorization policies.
  - [ ] BL-09E: Authorization policy introspection/admin visibility.

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
