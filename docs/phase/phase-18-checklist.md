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

Status: DONE

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

### Verification Snapshot

- [x] Commit: `992d1d6 Add blob metadata projection to admin views`.
- [x] `StaticFormAppRendererSpec -- -z "execute admin read"` passed.
- [x] `BlobComponentSpec -- -z Blob` passed.
- [x] `Test/compile` passed.
- [x] `git diff --check` passed.

### Review Findings Resolved

- [x] Blob admin subroutes map to admin authorization selectors.
- [x] Blob admin operation failures remain structured errors instead of
      missing-page fallbacks.
- [x] Unsafe external Blob URLs are rendered as text unless explicitly safe.
- [x] Attached Blob delete without `force` is asserted as deterministic `400`.
- [x] Record-valued Aggregate/View detail responses expose Blob metadata at the
      top-level `blobs` field.

### BL-07B: Projection Contract Closure

Status: DONE

### Objective

Close the Aggregate/View Blob projection contract before hardening work. This
slice does not add role-grouped output or Entity-local Association snapshots.

### Contract Decisions

- [x] `blobs` remains a flat additive field.
- [x] `blobs` is omitted when an Aggregate/View item has no associated Blobs.
- [x] Blob rows are ordered by `sortOrder` ascending, then `associationId`.
- [x] Blobs without `sortOrder` are placed after ordered rows.
- [x] Detail responses expose `blobs` at the top level, not inside nested
      `record`.
- [x] List item responses expose `blobs` per item when associations exist.
- [x] Projection rows expose Blob metadata, URL metadata, `associationId`,
      `role`, and `sortOrder`.
- [x] Projection rows never embed Blob payload bytes.
- [x] Entity-local Association snapshots remain deferred to future optimization;
      the repository store remains the source of truth.

### Acceptance Checks

- [x] View detail with no associated Blobs has no `blobs` field.
- [x] View detail with associated Blob exposes a one-row flat `blobs` field.
- [x] Aggregate detail Blob rows are deterministically ordered.
- [x] Aggregate detail Blob rows omit payload fields.
- [x] Aggregate record-valued detail keeps nested `record` payload free of
      `blobs`.

### Verification Snapshot

- [x] Commit: `cca7e38 Close blob projection contract`.
- [x] `StaticFormAppRendererSpec -- -z "execute admin read"` passed.
- [x] `BlobComponentSpec -- -z Blob` passed.
- [x] `Test/compile` passed.
- [x] `git diff --check` passed.

---

## BL-08: Blob Hardening

Status: IN PROGRESS

### Objective

Harden Blob management after the basic runtime and Web/API flows exist.

### BL-08A: External URL Safety Policy

Status: DONE

### Objective

Apply a single safety policy to `external_url` Blob registration, URL
resolution, and Web/admin link rendering.

### Implementation Decisions

- [x] Accept only absolute `http` and `https` external URLs.
- [x] Reject empty URLs, relative URLs, protocol-relative URLs, userinfo URLs,
      local/loopback hosts, whitespace/control characters, and non-web schemes.
- [x] Normalize accepted external URLs before storing `externalUrl`,
      `displayUrl`, and `downloadUrl`.
- [x] Reject unsafe external URL registration before Blob metadata persistence.
- [x] Treat unsafe legacy external URL metadata as unsafe at `resolve_blob_url`.
- [x] Keep unsafe legacy Web/admin display text-only.
- [x] Keep CNCF-owned managed Blob routes linkable in Web/admin pages.

### Acceptance Checks

- [x] Safe `https` and `http` external URLs register successfully.
- [x] Unsafe external URLs fail deterministically.
- [x] Unsafe legacy external URLs are not rendered as active links.
- [x] Unsafe legacy external URLs do not resolve through `resolve_blob_url`.

### Verification Snapshot

- [x] Commit: `8f87196 Harden external blob URLs`.
- [x] `BlobComponentSpec -- -z Blob` passed.
- [x] `StaticFormAppRendererSpec -- -z Blob` passed.
- [x] `Test/compile` passed.
- [x] `git diff --check` passed.

### BL-08B: Blob Metadata Validation

Status: DONE

### Objective

Validate Blob registration metadata without changing BlobStore SPI or sniffing
payload bytes.

### Implementation Decisions

- [x] Add optional `expectedByteSize` / `expectedDigest` to `register_blob`.
- [x] Reject invalid `contentType` syntax while keeping typed `MimeBody` content types.
- [x] Keep existing managed `contentType` default of `application/octet-stream`.
- [x] Validate `expectedDigest` as raw 64-character SHA-256 hex.
- [x] Compare expected byte size and digest against BlobStore-measured results.
- [x] Compensate stored payloads when managed validation fails.
- [x] Reject `expectedByteSize` / `expectedDigest` for `external_url` Blobs.
- [x] Do not add MIME kind policy, maximum size policy, or MIME sniffing in this slice.

### Acceptance Checks

- [x] Managed Blob with matching expected byte size and digest succeeds.
- [x] Invalid content type metadata fails.
- [x] Managed Blob mismatch fails and does not create metadata.
- [x] Managed Blob mismatch deletes the stored payload.
- [x] Invalid expected digest format fails.
- [x] External URL Blob with expected payload metadata fails.
- [x] Register operation metadata exposes the new validation parameters.

### Verification Snapshot

- [x] Commit: current BL-08B change.
- [x] `BlobComponentSpec -- -z Blob` passed.
- [x] `Test/compile` passed.
- [x] `git diff --check` passed.

### BL-08C: Blob Functional ActionCall Entity Chokepoint Boundary

Status: DONE

### Objective

Fix the runtime boundary for Blob metadata and Blob association access.

Blob metadata is a SimpleEntity-backed model and Blob attachment is backed by
Association entities. Production operation paths must therefore use
`FunctionalActionCall` and access those records through ActionCall / UnitOfWork
chokepoints. Direct repository access is allowed only as a low-level
adapter/test fixture and must not be the public operation path, because it
bypasses authorization, metrics, and observability.

### Implementation Decisions

- [x] User-facing Blob operations use `FunctionalActionCall` and `UnitOfWorkOp`
      for Blob metadata create/load/search/delete.
- [x] User-facing Blob operations do not use `ProcedureActionCall` as their
      primary implementation style.
- [x] User-facing Blob attach/list/detach operations use `UnitOfWorkOp` for
      Association create/search/delete.
- [x] Admin-facing Blob operations use `UnitOfWorkOp` for Blob metadata and
      Association access.
- [x] Aggregate/View Blob projection uses ActionCall-provided UoW loaders
      instead of constructing repositories directly.
- [x] Default runtime config includes the standard EntityStore in
      `EntityStoreSpace`, making UoW EntityStore paths usable by builtin
      components.
- [x] Store-only / placeholder entity collections no longer fail durable UoW
      create/load/search flows because cache admission is skipped when the
      collection cannot materialize the typed entity.
- [x] Association list pagination is applied after typed query matching so
      earlier unrelated association rows do not hide matching Blob rows.

### Explicit Boundaries

- `BlobRepository` and `AssociationRepository` remain low-level store adapters.
  They are not the application operation boundary.
- Blob Web/admin pages continue to call Blob component operations; they do not
  access repositories directly.
- The `BlobService` component port exposes BlobStore capability only; it no
  longer advertises repository-backed user/admin metadata operations.
- `DefaultBlobService` is also limited to BlobStore capability and does not
  retain direct repository-backed metadata/association operation methods.
- Aggregate/View projection may read Blob metadata only through loaders supplied
  by the executing ActionCall.
- Application create/update simultaneous Blob attachment workflows are not
  finalized by BL-08C. They require a follow-up slice to provide UoW-backed
  workflow adapters before they are treated as production operation paths.
- Managed registration compensation preserves consistency between metadata and
  payload storage: if payload validation or metadata create fails before a Blob
  row exists, the stored payload is deleted; if a Blob row was created but
  cleanup of that row fails, the payload is left in place and the failure is
  surfaced.

### Acceptance Checks

- [x] Blob registration succeeds through the ActionCall/UoW path.
- [x] Blob attach/list/detach succeeds through the ActionCall/UoW path.
- [x] Admin Blob list/get/association/delete operations remain functional.
- [x] Blob projection remains additive on Aggregate/View output.
- [x] Repository construction no longer appears in production Blob operation
      ActionCall bodies.
- [x] Managed Blob registration does not depend on a post-create metadata load
      after EntityStore create succeeds.
- [x] Direct repository-backed BlobService methods are absent from production
      code.
- [x] Metadata cleanup failure does not leave Blob metadata pointing at a
      deleted managed payload.

### Verification Snapshot

- [x] `BlobComponentSpec -- -z Blob` passed.
- [x] `BlobAttachmentWorkflowSpec -- -z Blob` passed.
- [x] `Test/compile` passed.
- [x] `git diff --check` passed.

### BL-08D: ProcedureActionCall DSL Foundation

Status: DONE

### Objective

Keep `ProcedureActionCall` as an optional procedural implementation style, while
making the intended internal DSL chokepoint explicit for the cases that still
need it. CNCF/CozyTextus feature code should prefer `FunctionalActionCall`.

### Implementation Decisions

- [x] `ProcedureActionCall` exposes a protected `executeProgram` helper for
      running an `ExecUowM` program through the runtime `UnitOfWorkInterpreter`.
- [x] The helper does not make procedural code the default path.
- [x] Blob operations do not use this helper; they are implemented as
      `FunctionalActionCall`.
- [x] Full migration of legacy procedural components is deferred.

### Boundaries

- `FunctionalActionCall` remains the recommended CNCF implementation style.
- `ProcedureActionCall` is for procedural implementations that explicitly opt
  into the same UoW DSL chokepoint.
- BL-08D does not migrate admin/job/workflow legacy procedural components.

### Verification Snapshot

- [x] `Test/compile` passed.
- [x] `ActionCallSpec` passed.

---

### BL-08E: Blob Authorization Policy and Preflight

Status: DONE

### Objective

Define the concrete Blob authorization chokepoints after BL-08C moved Blob
metadata and association access onto `FunctionalActionCall` / UoW.

### Implementation Decisions

- [x] `UnitOfWorkOp.Authorize` provides an explicit authorization preflight
      operation through the same `OperationAccessPolicy` path as EntityStore
      operations.
- [x] Managed `register_blob` validates request metadata and runs Blob metadata
      create authorization before writing payload bytes to `BlobStore`.
- [x] External URL `register_blob` also runs Blob metadata create preflight
      before metadata persistence.
- [x] User-facing `attach_blob_to_entity` requires source entity update
      preflight, Blob read authorization, and Association create authorization.
- [x] User-facing `detach_blob_from_entity` requires source entity update
      preflight and Association delete authorization.
- [x] User-facing `list_entity_blobs` requires source entity read preflight,
      Association search authorization, and Blob read authorization.
- [x] Admin Blob operations continue to use existing admin operation policy plus
      UoW `System` access mode for metadata and association access.

### Boundaries

- `sourceEntityId` for user-facing attach/detach/list is now a canonical
  `EntityId` string, so generic entity authorization can inspect the source
  entity record.
- Blob-specific code does not interpret application entity models; it only
  passes source entity ids through the generic UoW authorization path.
- BL-08E does not add signed URLs, MIME-kind policy, retention policy, or
  production BlobStore backend support.

### Verification Snapshot

- [x] `BlobComponentSpec -- -z Blob` passed.
- [x] `UnitOfWorkTargetAuthorizationSpec -- -z explicit` passed.

### Deferred Hardening Items

- UoW-backed application create/update Blob attachment workflow adapter
- deletion and retention semantics
- signed URL support
- real S3-compatible backend
- thumbnail generation
- virus scanning
- resumable upload

---

## BL-09: Blob-Required Authorization Support

Status: IN PROGRESS

### Objective

Develop only the authorization support required by Blob management flows.

Blob remains the main Phase 18 product goal. Authorization work in this phase is
not a general security-program replacement; it exists to make Blob metadata,
payload, association, admin, and projection flows safe and operable through the
normal CNCF authorization chokepoints.

### BL-09A: Guard/Capability Concept Refinement

Status: DONE

Refine the common authorization vocabulary so Blob policy work has a stable
model:

- [x] Separate guard predicates from capability grants.
- [x] Treat `privilege` as a coarse system/runtime guard.
- [x] Treat ABAC natural conditions as contextual guards/filters.
- [x] Treat role, permission, and relation as capability grant sources.
- [x] Keep ACL as a future optional grant source, not a Phase 18 requirement.
- [x] Reflect the model in authorization design documents.

### BL-09B: Minimal Subject-Side Grant/Config Surface

Status: DONE

Add the minimal subject-side configuration needed to test and operate Blob
authorization:

- [x] roles/scopes/capabilities usable by Blob user/admin flows.
- [x] create grant for Blob EntityCollection registration/upload.
- [x] use/read grant for Blob metadata/payload access when object permission
      alone is insufficient.
- [x] association-domain create/delete grant for Blob attachment and detach.
- [x] store-status read grant for BlobStore diagnostics.

Implementation notes:

- Subsystem descriptors now accept `security.authorization.roles`.
- Role definitions expand to effective `SecuritySubject.capabilities`.
- Role includes are transitive and cycle-safe.
- Canonical subject grant helpers cover collection, association-domain, and
  store resources.
- Raw request `capability` remains a required capability, not a subject-side
  grant source.

Verification snapshot:

- [x] `SecuritySubjectSpec` covers collection/association/store grants and role
      expansion.
- [x] `GenericSubsystemDescriptorSpec` covers descriptor role parsing and SAR
      role override behavior.
- [x] `IngressSecurityResolverSpec` covers raw request `capability` as
      requirement-only input.

Broader subject grant administration, role-definition lifecycle, and identity
provider administration belong to strategy section 8.3 Security.

### BL-09C: Blob Object/Resource Policy Surface

Status: DONE

Add object/resource-side policy only where Blob needs it:

- [x] Blob EntityCollection create/read/update/delete access mapping.
- [x] Blob attachment Association domain create/delete policy.
- [x] BlobStore status/read diagnostics policy.
- [x] delete override policy for cases where `write` is allowed but `delete`
      must require a stronger capability.

Implementation notes:

- Subsystem descriptors now accept `security.authorization.resources`.
- Resource policies cover collection, association-domain, and store resource
  families.
- Policy rules can require subject capabilities and can override the entity
  permission bit used for a specific access kind.
- BL-09C adds the generic policy evaluator. Full Blob operation wiring remains
  BL-09D.

Verification snapshot:

- [x] `GenericSubsystemDescriptorSpec` covers resource policy parsing, invalid
      policy rejection, and inherited override behavior.
- [x] `OperationAccessPolicyResourceSpec` covers Blob collection create,
      delete permission override, association domain grants, store grants, and
      no-policy compatibility behavior.

### BL-09D: Blob Operation Integration

Status: PLANNED

Apply the BL-09B/BL-09C policy surfaces to existing Blob operations without
introducing Blob-private authorization logic:

- [ ] `register_blob`
- [ ] `read_blob`
- [ ] `resolve_blob_url`
- [ ] `get_blob_metadata`
- [ ] `attach_blob_to_entity`
- [ ] `detach_blob_from_entity`
- [ ] `list_entity_blobs`
- [ ] Blob admin operations

### BL-09E: Blob Authorization Visibility

Status: PLANNED

Expose enough policy metadata for operators and developers to understand Blob
authorization decisions:

- [ ] manual/help projection for Blob operation authorization requirements.
- [ ] admin visibility for Blob collection, association-domain, and store
      policy.
- [ ] diagnostics include guard failure vs capability failure distinction.

### Deferred To 8.3 Security

- first-class arbitrary ACL lists.
- general subject grant administration UI.
- full role-definition lifecycle and role-to-capability registry.
- organization-grade policy management beyond Blob-required surfaces.
- external identity/federation policy administration.

---

## Completion Check

Phase 18 is complete when:

- all BL items are marked DONE or explicitly deferred,
- corresponding checkboxes in `phase-18.md` are updated,
- Blob metadata, payload, association, Web/admin, and Aggregate/View visibility
  requirements have executable coverage or documented deferral.
