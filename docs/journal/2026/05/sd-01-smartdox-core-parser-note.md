# SD-01 SmartDox Core Parser Note

Date: 2026-05-04

## Summary

SD-01 moves the SmartDox parser/AST subset needed by CNCF content handling into
`simplemodeling-lib` as Scala 3 code. The canonical package is `org.smartdox`.
This slice is not a full SmartDox site engine port.

The source grammar is
`/Users/asami/src/dev2025/smartdox/docs/spec/smartdox-grammar.md`.

## Supported Grammar Subset

The CNCF content subset covers ordinary prose and references:

- sections: Org-style `* Title`, ATX Markdown `# Title`, and setext headings;
- document metadata: top-level `HEAD` as simple key/value metadata;
- descriptive sections: `SUMMARY` and `LEAD` as ordinary sections;
- blocks: paragraphs, unordered/ordered/definition lists, pipe tables,
  figures, image references, source/example blocks, horizontal rules,
  quotations, and comments;
- inline markup: bold, italic, underline, code, preformatted text, delete,
  spans, links, image references, and safe inline macros;
- links: `[[uri]]`, `[[uri][label]]`, Markdown `[label](uri)`, automatic URLs,
  `site:[target]`, and legacy `[target.dox]`;
- comments: line comments and `#+begin_comment` blocks are removed.

## Explicit Non-Goals

This slice does not port executable or site-level behavior:

- no DoxSite navigation or title resolution;
- no semanticweb/RDF/JSON-LD pipeline;
- no bibliography, glossary, or site metadata pipeline;
- no generated diagram execution;
- no CSV-backed table inclusion;
- no file/include macro execution.

Executable constructs are rendered as inert placeholders or ignored, never
executed as side effects.

## CNCF Integration

`ContentRenderWorkflow` renders `ContentMarkup.SmartDox` by parsing SmartDox and
producing deterministic escaped HTML, then wrapping it in
`<article class="textus-content">...</article>`.

`ContentReferenceWorkflow` extracts SmartDox image/link references from the AST.
SmartDox image references are converted through the existing HTML/media
normalization path so Blob URLs and Textus Blob URNs can become canonical image
URN occurrences. The stored SmartDox source is not rewritten in SD-01 because
source-span-aware rewriting is not implemented yet; normalized media identity is
stored in `ContentReferenceOccurrence.normalizedRef`, `urn`, and
`targetEntityId`.

`ContentAttributes.content` stays a single `ContentBody`. Multilingual rich
content should be represented by the future SmartDox Textus profile rather than
by `I18nText` at the ContentBody layer.

GFM-compatible Markdown reference normalization is implemented for inline
`![alt](src)` and `[label](href)` syntax. Markdown is parsed with the same
flexmark GFM configuration used by `ContentRenderWorkflow`. Inline image
destinations are resolved through the CNCF media path and rewritten to
canonical `urn:textus:image:{entropy}` values only after all image references
validate. Inline links are indexed in `ContentReferenceOccurrence` and are not
rewritten in this slice.

HTML, Markdown, and SmartDox content reference normalization use partial
failure handling for images. A successful image reference is kept as normalized
metadata and, where the source format is span-aware enough, a rewritten source
reference. A failed image reference keeps its original source and receives a
`textus:image-normalization-failed` comment in the content. This keeps the
authoring document repairable without discarding other successfully normalized
images.

Reference-style Markdown links/images, shortcut references, collapsed
references, and autolinks are implemented as the SD-01 completion slice.
Reference-style image definitions are rewritten to canonical image URNs after
the same safe media validation as inline image references. Reference-style links
and autolinks are indexed in `ContentReferenceOccurrence` but are not rewritten.
Code blocks, fenced blocks, inline code, HTML blocks, and plain text that
merely resembles Markdown syntax are not rewritten because only flexmark link
and image AST nodes are processed.

## Ported Modules

The first port is intentionally small and framework-facing:

- `org.smartdox.Dox`: minimal AST for document, body, section, paragraph, list,
  table, figure/image, source/verbatim block, quote, horizontal rule, inline
  markup, links, inline macros, structured tokens, and `I18NFragment`;
- `org.smartdox.parser.Dox2Parser`: Scala 3 parser facade compatible with the
  package shape expected by downstream migration work;
- `org.smartdox.renderer.DoxHtmlRenderer`: deterministic safe HTML fragment
  renderer;
- `org.smartdox.DoxReferenceExtractor`: image/link occurrence extraction for
  CNCF `ContentReferenceOccurrence`.

The port does not import the Scala 2.12 `smartdox` artifact, `scalaz`, or
`goldenport-scala-lib`.

## Logical Line / Token Policy

SmartDox uses logical lines, not a raw line-by-line parse.

Ordinary adjacent prose lines are joined into one paragraph:

```text
aaa
bbb
```

becomes one paragraph with text `aaa bbb`.

Structured XML and JSON are handled differently. They are preserved as one
`StructuredToken` while the structure remains open:

```xml
<a>

  <b/>
</a>
```

becomes `StructuredToken("xml", raw)` and keeps the blank line.

```json
{
  "a": [

    {"b": 1}
  ]
}
```

becomes `StructuredToken("json", raw)` and keeps the blank line.

This prevents XML/JSON from being split into unrelated paragraphs. JSON array
detection is deliberately conservative so leading Markdown links such as
`[label](https://example.com)` and legacy site links such as `[local.dox]` do
not get misclassified as JSON.

## Image Reference Classification

SD-01 uses a small v1 heuristic to classify `[[...]]` as an image reference.
The parser treats these as image-like:

- paths ending with `.png`, `.jpg`, `.jpeg`, `.gif`, or `.webp`;
- `urn:textus:image:{value}`;
- `urn:textus:blob:{value}`;
- `/web/blob/content/{id-or-entropy}`.

This suffix-based path detection is only a fallback hint. The long-term
authoritative classification should come from:

- explicit SmartDox syntax or macro/type information;
- Textus URN kind;
- Media Entity kind;
- Blob Entity `contentType` / `kind`;
- filebundle MIME detection for local relative files.

Suffix-only classification is not sufficient for extensionless images,
mislabelled files, or non-image data with an image-like filename. CNCF
normalization must still validate resolved Blob/Media content through the safe
media path before treating a reference as an inline image.

In other words, suffix checks are a parser-side v1 convenience for recognizing
plain path image references. They are not the semantic source of truth for CNCF
content processing. Once a reference is resolved to Textus URN, Media Entity, or
Blob Entity, the resolved kind/content type decides whether the reference is
actually usable as an image.

## Current Limitations

- SmartDox source rewrite is not span-aware yet. SD-01 extracts normalized
  references but keeps the original SmartDox source text, except for appended
  failure comments for image references that could not be normalized.
- XML and JSON structured tokens are preserved, escaped, and rendered as code;
  they are not schema-validated or converted into typed XML/JSON ASTs.
- Image classification for plain paths still uses suffix hints in v1.
- List nesting is shallow. The parser handles basic list blocks but does not
  fully reproduce the legacy SmartDox nested list engine.
- Inline XML-style tag parsing is intentionally minimal and safe-rendered.
- Markdown inline and reference-style image/link normalization is implemented,
  including collapsed references, shortcut references, and autolinks.
- The SmartDox Textus profile is documented in
  `docs/design/smartdox-textus-profile.md`.
- SmartDox multilingual rendering remains future work.

## Review Points

When extending the port, keep these invariants:

- do not execute include/macros/diagram/table data sources during content
  rendering;
- escape rendered HTML by default;
- do not rewrite source text unless parser nodes carry source spans;
- preserve logical tokens for XML/JSON and similar structured text;
- route media/blob reference resolution through CNCF normal Entity/Media/Blob
  paths so logical delete, tenant scope, and authorization assumptions remain
  intact.
