# Entity Kind and Working Set Policy

Date: 2026-05-04

This note records the runtime classification boundary for CNCF Entities. It is
not an authorization policy document. Authorization profiles may use the same
classification terms, but Entity kind and Working Set policy are runtime and
modeling concerns.

## Entity Kinds

`resource` is the default kind for durable domain objects that represent
master data, reference data, or content resources. A resource Entity is the
canonical stored record for its domain object.

Examples:

- `Product`
- `Customer`
- `Catalog`
- `BlogPost`

`task` is the kind for active execution or business process objects. A task
Entity may be resident while it is active, but should leave the Working Set
after completion or inactivity.

Examples:

- `SalesOrder`
- active workflow instance
- running job

`cms resource` is a resource specialization for public or author-owned content.
It usually has lifecycle state, publication state, body content, media
references, and public read/search surfaces.

Examples:

- `Notice`
- `BlogPost`
- article-like content Entity

## Working Set Policy

Working Set is not the same axis as `resource` or `task`. It is a runtime
residency policy that decides whether Entity instances should be kept resident
for domain execution.

Recommended defaults:

| Entity shape | Classification | Working Set default |
|--------------|----------------|---------------------|
| Product | resource | resident candidate |
| SalesOrder | task | active-only resident candidate |
| BlogPost | cms resource | disabled |

`Product = resource + resident candidate` because master/reference data is
small enough and stable enough to benefit from residency.

`SalesOrder = task + active-only resident candidate` because it is execution
state. Active orders may benefit from resident aggregate behavior, but completed
or inactive orders should fall back to datastore access.

`BlogPost = cms resource + store-backed canonical + workingSet disabled
default` because article bodies, references, media links, and publication
history grow over time. Keeping the full content Entity resident turns Working
Set into a broad CMS cache instead of an execution model.

## CMS Resource Optimization

CMS resources should use store-backed canonical Entity records. Read
optimization should be handled by smaller derived views, indexes, or caches.

For Blog-style content, suitable cache candidates are:

- `PublishedBlogView`
- `BlogSlugIndex`
- `BlogFeedProjection`
- `BlogAuthorPostView`

These derived records should hold only the data needed for list/search/feed
surfaces, such as id, slug, title, summary, published timestamp,
representative image, author identity, and publication state. Full article
content remains on the canonical `BlogPost`.

Draft/edit state is a lifecycle state of the content resource, not a task
classification by itself. If the application needs review, approval, or
publication workflow state, model that as a separate task Entity such as
`BlogPublicationTask`.
