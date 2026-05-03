# CT-01 ContentBody / Content Storage Note

Date: 2026-05-04

## Summary

CT-01 moves SimpleEntity content from multilingual `I18nText` to a dedicated
`ContentBody` value. HTML and Markdown content are treated as a single body.
Multilingual rich content is reserved for future SmartDox support, where
SmartDox itself can own the i18n structure.

`ContentAttributes` should carry domain values, not raw strings:

- `content`: `ContentBody`
- `mimeType`: `MimeType`
- `charset`: `Charset`
- `markup`: `ContentMarkup`
- `references`: `ContentReferenceOccurrence`

## Rendering

The CNCF content rendering workflow produces browser-facing HTML from
`ContentAttributes`.

- `html-fragment` stores an HTML fragment and renders it inside
  `<article class="textus-content">...</article>`.
- `markdown-gfm` stores GFM-compatible Markdown and renders it to HTML before
  the same article wrapper is applied.
- `smartdox` is intentionally unsupported in CT-01. SmartDox rendering and
  SmartDox i18n are separate work.

Textus URNs are expanded during render. The stored content remains canonical
content, not browser URL output.

## Storage Policy

The logical model is always `ContentBody`; application and model code should not
decide whether content is a `VARCHAR`, `TEXT`, side table entry, or another
physical layout.

The DataStore-facing content storage policy uses charset-aware byte length:

- small content remains inline in the main entity record;
- large content overflows to content-specific side storage;
- the main record keeps `content_ref`, `content_byte_size`, `content_digest`,
  `content_charset`, and `content_storage` metadata.

The threshold is based on encoded byte length, not character count. This matters
for Japanese and other multibyte text because the byte size depends on actual
data and charset.

The policy also avoids forcing all content into a DB `TEXT` column. Some DBs may
allocate `TEXT`/large-object content away from the main record even when the
value is small, which is not desirable for ordinary short content.

## Binary Content

CT-01 implements text content for HTML and Markdown. `ContentBody` is still the
right boundary for future binary content because binary content has the same
storage-policy problem: small values may be inline, large values may overflow,
and byte size/digest metadata should be framework-owned.

Binary `ContentBody` must not be confused with Image/Video/Audio/Attachment
media payloads. Media payloads remain BlobStore-backed. Content overflow storage
is for the entity's own body, while BlobStore is for referenced binary resources
or opaque attachments.

## Open Points

- Define the exact `ContentBody` binary representation when a concrete use case
  appears.
- Decide whether content overflow needs a backend-specific side table/store API
  instead of the current DataStore collection abstraction.
- Add explicit DB mapping rules for inline byte thresholds per backend.
