# Entity Image Binding Usage Contract

Status: Phase 19 contract baseline
Date: 2026-04-30

## Purpose

This document defines the CNCF usage contract for binding image Blobs to
ordinary Entity instances.

The contract is based on the Phase 18 Blob and Association foundation and the
Phase 19 `textus-blog` `BlogComponent` driver. `BlogComponent` validated that
BlogPost image links can be represented without direct image fields or
component-local image binding entities.

## Canonical Model

Blob metadata and payload storage remain owned by the Blob foundation. Entity
image relationships are represented by `AssociationDomain.BlobAttachment`.

The canonical association shape is:

- `sourceEntityId`: owning Entity id;
- `targetEntityId`: Blob Entity id;
- `targetKind`: `blob`;
- `role`: image role;
- `sortOrder`: optional ordering within a role or owner;
- `attributes`: optional application metadata.

Domain entities should avoid direct image fields by default. Representative
images are derived from associations.

The standard image role vocabulary is:

- `primary`;
- `cover`;
- `thumbnail`;
- `gallery`;
- `inline`.

Applications may use additional role strings. CNCF treats the standard roles as
well-known display and projection hints, not as a closed enum.

Representative image selection uses this priority:

```text
primary -> cover -> thumbnail -> inline
```

The `inline` fallback is for content-bearing entities. When no explicit
representative role exists, the first managed inline image by article/content
occurrence order should represent the Entity. The occurrence order is carried by
association `sortOrder`; ties are resolved by the projection helper's stable
association ordering.

`gallery` images are not representative fallbacks.

## Operation Contract

Create, update, register, and import flows may attach either uploaded Blob
payloads or existing Blob ids.

The internal operation contract is split into a generic Association layer and
an image-specific specialization:

- `associationBinding` describes Entity-to-Entity Association creation from
  existing target ids.
- `imageBinding` describes image-specific input capabilities, such as uploaded
  payloads, existing Blob ids, and archive Blob ids.
- `imageBinding` maps internally to `associationBinding` with
  `domain = blob_attachment` and `targetKind = blob`.

`AssociationBindingWorkflow` is the reusable helper for creating Associations
from existing target ids after an operation succeeds. Source Entity id
resolution can use an operation parameter or a result field. Entity creation
operations should return `entity_id` as the primary source id field, with
`entityId` and `id` as secondary fallbacks.

`ChildEntityBindingWorkflow` covers SalesOrder/SalesOrderLine-style operation
registration. It creates child Entity records from a request collection after
the parent operation succeeds, injects the resolved parent id into the child
parent-id field, and compensates created children plus the created parent when
an entity-create-result registration fails.

`BlobAttachmentWorkflow` is the image/blob specialization for same-request
flows that need Blob uploads or existing Blob references attached to an Entity.
It creates managed Blobs for upload parts, verifies existing Blob references,
and delegates final BlobAttachment creation to the same Association binding
structure.

Application-specific imports, such as a Blog ZIP or file-tree import, are
adapters. They normalize local file references into managed Blobs first, then
attach those Blobs through the same association model.

Lower-level registration operations should accept normalized Entity data plus
existing Blob references. Local path-only payload registration belongs to the
import adapter, not to the lower-level registration boundary.

Detach/delete semantics are explicit:

- detach removes BlobAttachment associations;
- detach does not delete Blob metadata or payloads;
- Blob cleanup is a separate operation unless a component operation explicitly
  owns both the association and the Blob lifecycle.

Protected author/admin workflows may create drafts, publish posts, deactivate
posts, and repair image associations. Public read/search workflows should expose
only public content according to the owning component's visibility rules.

## Read, Search, and Projection Contract

The standard projection shape for associated images is:

```scala
images: Vector[BlobAttachmentProjection]
representativeImage: Option[BlobAttachmentProjection]
```

Each image projection row includes Blob metadata plus:

- `associationId`;
- `role`;
- `sortOrder`.

`BlobProjection` is the current reusable projection helper. It loads
BlobAttachment associations for an Entity, orders them by `sortOrder`, loads the
target Blob metadata, and returns rows that combine Blob metadata with
association data.

Read/detail projections should expose the full `images` collection when the
surface needs associated images. Search/list projections may expose compact
representative image data for thumbnail/card use, but the full associated image
rows remain the canonical detail shape.

Projection output must not embed Blob payload bytes. It should expose metadata
and access URLs only.

## Web, Admin, and Manual Expectations

Web and admin pages should display associated images by role and sort order
when an Entity is image-capable.

Admin repair flows should support:

- listing BlobAttachment associations for an Entity;
- attaching an existing Blob to an Entity with role and optional sort order;
- detaching a BlobAttachment association.

Manual/help metadata for image-capable operations should identify whether an
operation accepts:

- uploaded Blob payloads;
- existing Blob ids;
- both uploaded payloads and existing Blob ids.

Manual/help metadata may also expose generic `associationBinding` information
for non-image Association-capable operations, including domain, target kind,
source id mode, roles, target id parameters, and sort-order parameters.

Generic Web/admin/projection affordances are Phase 19 BI-04 implementation
gaps. This document defines the contract those implementations should follow.

## BlogComponent Driver Result

`textus-blog` `BlogComponent` validates the contract as follows:

- `BlogPost.primaryImageId` is not canonical state;
- Blog-local `ImageAsset` and entity image binding entities are not canonical
  state;
- `BlogPost` image links use BlobAttachment associations;
- `primary`, `cover`, `thumbnail`, `gallery`, and `inline` roles are represented
  as association roles;
- representative image data is derived from associations;
- inline article images are tracked as occurrences while their image references
  resolve to managed Blob content URLs.
