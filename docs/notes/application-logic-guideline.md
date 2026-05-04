# Application Logic Guideline Note

Date: 2026-05-02

## Purpose

This note records the working guideline for CNCF application logic.

Application logic means generated or handwritten component behavior, typically
implemented as `ActionCall` logic. The purpose of this layer is to express domain
intent, not to decide persistence mechanics.

This is a growing note. It should be updated as CNCF discovers sharper
application/internal-DSL boundaries.

## Guideline

Application logic should use the CNCF internal DSL first.

Examples:

- use entity DSL helpers for entity create/load/update/delete/search;
- use association/blob/child binding DSL or workflows where available;
- use framework service helpers for system concerns such as jobs, events, blob
  payloads, and HTTP calls.

Application logic should not:

- call `DataStoreSpace` to load or search entity records;
- construct `DataStore.CollectionId.EntityStore(...)` for business logic;
- directly construct `UnitOfWorkOp.EntityStore*` unless it is implementing a
  framework-owned DSL helper;
- call `EntityStoreSpace` as an unrestricted store;
- duplicate lifecycle checks such as logical delete filtering in component code.

If application logic needs a capability that appears to require raw storage
access, that is a signal to add or improve an internal DSL helper.

Identifier logic follows the same rule. Application code should not hand-roll
raw searches to resolve or validate values such as `slug`, `shortid`, owner ids,
or application-defined unique names. Use the internal DSL identity helpers so
that logical delete filtering, tenant scope, and future `EntitySpace`/working
set behavior stay consistent.

For tenant-aware applications, application logic should not build tenant filters
by hand. Tenant and organization scope should come from `ExecutionContext` and
be interpreted by the internal DSL / `UnitOfWork` layer. In a context without
tenant data, the same application call naturally behaves as global scope.

## Domain Value Types

Raw primitive attributes in domain objects are a bad smell. A CML model or
handwritten domain value should prefer CNCF/goldenport standard data types and
value objects over raw `String`, `Int`, `Long`, or `Boolean` when the value has
domain meaning.

Examples:

- use `MimeType` for content type values, not `String`;
- use `Charset` for text charset values, not `String`;
- use `ContentBody` for persisted content bodies, not a raw text column;
- use `I18nLabel`, `I18nTitle`, `I18nSummary`, `I18nDescription`, or
  `I18nText` for localized metadata and bounded help/descriptive prose, not
  raw localized string maps;
- use explicit value objects or schema data types for identifiers, amounts,
  state tokens, and policies.

Localized metadata and content bodies are separate concerns. Short labels,
titles, summaries, descriptions, help text, and other bounded metadata should
stay in the existing `I18n*` value family. Blog/article/document bodies should
use `ContentAttributes.content` as `ContentBody` with explicit `ContentMarkup`,
`MimeType`, and `Charset`. HTML and Markdown content bodies are single-body
content; rich multilingual document bodies are reserved for the SmartDox Textus
profile rather than modeled as `I18nText`.

Raw primitives are acceptable at boundaries such as JSON/Record parsing,
storage adapters, generated convenience methods, or low-level schema encoding,
but they should be normalized before they become application/domain attributes.
This keeps validation, rendering, storage policy, and future migrations in one
framework-owned place.

## Standard Entity Names

`SimpleEntity.name` is an application-logic name. It is appropriate for stable
machine-facing identifiers such as slug-like lookup keys, route segments, or
application-defined unique names.

The user-facing name of content should normally be `SimpleEntity.title`. For a
blog post, the post's visible name is its title. If the blog also has a URL/SEO
slug, that slug belongs in the application `name` slot or another explicit
identifier field, while UI labels and headings should use `title`.

Derived application fields may still be exposed at the boundary when useful.
For example, a blog response can expose `authorId` as a projection derived from
`SimpleEntity.ownerId`. `securityAttributes` is an implementation structure, not
the CML-facing derived target. The persisted source remains the standard owner
field, which keeps future migration possible if author identity later separates
from owner identity.

## Authorization Position

`ActionCall.authorize()` remains useful, but its role should narrow over time.

It is appropriate for:

- coarse action admission;
- explicit generated metadata exceptions;
- promotion, degradation, or bypass semantics.

It should not be the only enforcement point for ordinary domain resource access.
Ordinary resource protection should hold even when handwritten application logic
uses a domain object helper from an unexpected entry point.

## Review Checklist

When reviewing application logic, check:

- Does the code use internal DSL helpers instead of raw stores?
- Is domain intent visible without persistence details leaking into the action?
- Is `DataStoreSpace` absent from ordinary business logic?
- Is `EntityStoreSpace` not treated as an unrestricted application store?
- Are lifecycle rules such as logical delete handled below the application
  layer?
- Are uniqueness and identity checks using internal DSL helpers instead of
  generic search or raw store access?
- Is tenant scoping delegated to `ExecutionContext`-aware framework helpers?
- Do domain attributes use standard value/data types instead of raw primitives
  where the value has meaning?
- If low-level access seems necessary, should a missing internal DSL helper be
  added instead?

## Open Questions

- Which remaining handwritten `ActionCall` implementations still use low-level
  stores and should be migrated first?
- Should generated component code make direct low-level access impossible by
  construction?
- Which application patterns need new internal DSL helpers to avoid storage
  escape hatches?
