# Entity Kind and Working Set Policy

Date: 2026-05-04

This note records the runtime classification boundary for CNCF Entities. It is
not an authorization policy document. Authorization profiles may still use
legacy `operationKind` terms, but `entityKind` is the canonical modeling and
runtime classification.

## Entity Kinds

`master` is stable canonical master/reference data.

Examples:

- `Product`
- `Customer`
- `Catalog`

Default runtime policy: resident candidate. The store record remains canonical,
but master data is usually small and stable enough to benefit from residency.

`document` is authored content or document data. `cms` is not an Entity kind;
CMS content should be modeled as `entityKind = document` with
`applicationDomain = cms` and a suitable `usageKind`, such as
`public-content`.

Examples:

- `BlogPost`
- `NewsArticle`
- `Notice`

Default runtime policy: Working Set disabled. Optimize reads through derived
views, indexes, and projections rather than resident full content bodies.

`workflow` is a business entity with explicit state transitions.

Examples:

- `SalesOrder`
- approval workflow instance
- publication workflow instance

Default runtime policy: active-only resident candidate. CNCF records this as a
kind default candidate, but does not enable residency from `entityKind` alone.
The workflow state field and active-state policy should be configured by the
application; completed or inactive workflow records should fall back to the
store.

`task` is an execution unit without a domain state machine.

Examples:

- import task
- render task
- one-shot job task

Default runtime policy: Working Set disabled unless explicitly configured.

`actor` is a proxy or representative of an external object or party.

Examples:

- external account
- partner organization proxy
- service account proxy

Default runtime policy: private/security-sensitive and not resident unless the
application explicitly enables residency.

`asset` is media/blob-backed asset metadata.

Examples:

- `Image`
- `Video`
- `Audio`
- `Attachment`
- generic `Blob`

Default runtime policy: Working Set disabled. Payloads remain in Blob/media
storage, not in the main Entity record.

## Compatibility Axes

`entityKind` is separate from:

- `operationKind`: legacy authorization/runtime compatibility axis.
- `applicationDomain`: business, CMS, generic, or another application domain.
- `usageKind`: public-content, business-object, executable, and other usage
  labels.

`operationKind` is intentionally shrinking in current CNCF code. Its active use
is a legacy `resource` / `task` bridge carried into authorization context and
ABAC condition keys such as `application.entityOperationKind`. New runtime
defaults should not be derived from it. Keep the type available because it may
be reused if a real operation-specific classification emerges later.

Legacy mapping:

| `entityKind` | legacy `operationKind` |
|--------------|------------------------|
| `master` | `resource` |
| `document` | `resource` |
| `actor` | `resource` |
| `asset` | `resource` |
| `workflow` | `task` |
| `task` | `task` |

Existing descriptors that only declare `operationKind` remain valid. New
descriptors should declare `entityKind` and let legacy `operationKind` be
derived unless a compatibility override is required.

The policy implementation source is `EntityKindRuntimePolicy`. It owns the
legacy `operationKind` projection and the default Working Set policy for each
canonical `entityKind`.

## Working Set Policy

Working Set is not the same axis as Entity kind. It is a runtime residency
policy that decides whether Entity instances should be kept resident for domain
execution.

Recommended defaults:

| Entity shape | Classification | Working Set default |
|--------------|----------------|---------------------|
| Product | `master` | resident candidate |
| BlogPost | `document` + CMS/public-content | disabled |
| SalesOrder | `workflow` | active-only resident candidate |
| ImportTask | `task` | disabled |
| ExternalAccount | `actor` | disabled unless explicit |
| Image | `asset` | disabled |

`BlogPost = document + store-backed canonical + workingSet disabled default`
because article bodies, references, media links, and publication history grow
over time. Keeping the full content Entity resident turns Working Set into a
broad CMS cache instead of an execution model.

## Document Optimization

Document Entities should use store-backed canonical records. Read optimization
should be handled by smaller derived views, indexes, or caches.

For Blog-style content, suitable cache candidates are:

- `PublishedBlogView`
- `BlogSlugIndex`
- `BlogFeedProjection`
- `BlogAuthorPostView`

These derived records should hold only the data needed for list/search/feed
surfaces, such as id, slug, title, summary, published timestamp,
representative image, author identity, and publication state. Full article
content remains on the canonical `BlogPost`.

Draft/edit state is a lifecycle state of the document Entity, not a task
classification by itself. If the application needs review, approval, or
publication workflow state, model that as a separate `workflow` Entity such as
`BlogPublicationWorkflow`.
