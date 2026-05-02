# CML Application Modeling Guideline Note

Date: 2026-05-02

## Purpose

This note records the working guideline for writing CML application models.

The goal is to keep CML focused on the essential application meaning. CNCF and
SimpleEntity already provide common entity concerns such as identity, names,
descriptions, content, lifecycle, publication state, ownership, permissions,
media metadata, audit data, and contextual data. Application CML should use that
foundation instead of restating it as application-specific fields.

This is a growing note. It should be refined as Cozy/CML, SimpleEntity, and the
generated metadata model evolve.

## Core Guideline

CML should describe what is specific to the application.

For normal content and business entities, start from `SimpleEntity` and add only
the fields, relationships, operations, and policies that are essential to the
application's domain.

This makes the application purpose easier to read:

- the CML shows domain-specific data instead of framework boilerplate;
- common lifecycle and authorization behavior stays consistent across apps;
- generated admin, API, help, and Web surfaces can share standard metadata;
- future framework improvements apply without changing each application model.

## SimpleEntity Responsibility

`SimpleEntity` should cover the ordinary cross-cutting entity surface.

Use standard SimpleEntity fields for common concepts:

- `id` / `shortid`: persistence and compact identity.
- `name`: application-logic name, such as a stable slug or lookup key.
- `title`: user-facing display name.
- `headline`, `summary`, `description`, `content`: descriptive/content data.
- `postStatus`: publication state such as draft or published.
- `aliveness`: lifecycle state such as alive, suspended, or dead.
- `ownerId`, `groupId`, `rights`, `privilegeId`: ownership and authorization.
- `createdAt`, `updatedAt`, `createdBy`, `updatedBy`: lifecycle audit fields.
- media/resource/context/audit attributes for standard platform concerns.

Do not add application fields that merely duplicate these standard concepts.
For example, a blog post should normally use `title` for the visible article
name, `name` for the URL/SEO slug, `content` for the HTML fragment,
`postStatus` for draft/published state, `aliveness` for active/inactive state,
and `ownerId` as the persisted author source.

## Application-Specific Fields

Add CML `ATTRIBUTE`s when the concept is genuinely part of the application
domain and is not already covered by SimpleEntity.

Good examples:

- `orderId` on `SalesOrderLine` when composition is stored as a child-parent id
  field.
- `quantity`, `unitPrice`, or `sku` on an order line.
- `sourceUrl`, `altText`, `sortOrder`, and `synchronized` on a
  `BlogInlineImage` occurrence entity.
- business status values that are distinct from lifecycle or publication state.

Avoid fields such as `activeStatus`, `draftStatus`, `authorAccountId`, or
`slug` when their meaning is already covered by `aliveness`, `postStatus`,
`ownerId`, or `name`.

## Derived And Projected Fields

Derived fields are useful at application boundaries, but they should not force
duplicate persistence fields into the entity model.

For example, a Blog response may expose `authorId` because it is useful to
clients and templates. The persisted source can still be
`SimpleEntity.ownerId`. `securityAttributes` is an implementation structure for
delegation and permission data; application CML should target the ordinary
standard field name `ownerId`, just as it targets `title` or `content`.

In that case `authorId` belongs in an operation output, projection, view, or a
generated derived alias, not as a duplicate BlogPost persistence field.

Likewise, a public response may expose `slug` while the stored application
identifier is `SimpleEntity.name`. The public name is a boundary contract; the
storage source remains the standard field.

Current CML supports derived attributes for generated aliases. Derived aliases
should normally point at SimpleEntity standard field names such as `title`,
`content`, `name`, or `ownerId`.

## Relationships And Bindings

CML relationships should describe application structure, not low-level storage
mechanics unless the storage pattern is itself part of the relationship
contract.

Use:

- `association` when independent entities are linked through Association
  records.
- `composition + child-parent-id-field` when child entities are lifecycle
  dependent and store the parent id.
- `composition + embedded-value-object` when the child is a value object stored
  inside the parent record.

For image binding, Blob metadata/payload and BlobAttachment associations are
standard CNCF infrastructure. Application CML should say which entity has images
and which operations accept image input; it should not introduce local
ImageAsset or direct image fields unless there is a domain-specific reason.

## Operation CML

Operation input/output types may contain application-facing names even when the
underlying entity storage uses SimpleEntity fields.

Examples:

- An editor input may accept `slug`; the implementation stores it in
  `SimpleEntity.name`.
- A result may return `authorId`; the implementation derives it from
  `ownerId`.
- A command may accept image attachments; the runtime stores BlobAttachment
  associations.

This keeps external operation contracts clear without polluting the entity's
persistent attribute model.

## Review Checklist

When reviewing CML, check:

- Does the entity extend or otherwise reuse SimpleEntity where appropriate?
- Are `name`, `title`, `content`, `postStatus`, `aliveness`, and `ownerId` being
  reused instead of duplicated?
- Are application attributes genuinely domain-specific?
- Are derived/projection fields kept at the boundary instead of persisted
  unnecessarily?
- Are relationships modeled as association, aggregation, or composition with an
  explicit storage pattern?
- Does the CML make the application's purpose easier to understand?

## Open Questions

- Should CML support first-class nested derived paths such as
  `securityAttributes.ownerId`?
- Should generated operation output metadata distinguish stored fields from
  projected aliases explicitly?
- Which SimpleEntity standard fields need better labels/help text for generated
  Web/admin/manual surfaces?
