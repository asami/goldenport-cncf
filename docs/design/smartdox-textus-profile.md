# SmartDox Textus Profile

Date: 2026-05-04

## Purpose

This profile defines the SmartDox subset CNCF/Textus uses for persisted content
bodies. It is a content-authoring profile, not the full SmartDox site engine.

The profile is used when `ContentAttributes.markup` is `smartdox`.

## Content Model

- SmartDox content is stored as a single `ContentBody`.
- `ContentBody` is the canonical location for Blog, article, and document
  bodies.
- `I18n*` values remain the standard for short localized metadata and bounded
  help/descriptive prose.
- HTML and GFM Markdown content bodies are single-body content and do not
  provide multilingual switching.
- Rich multilingual document bodies are reserved for future SmartDox profile
  expansion. SD-01 defines the boundary but does not implement multilingual
  rendering.

## Supported Authoring Subset

The supported subset is the safe document/content subset implemented by the
Scala 3 `org.smartdox` parser in `simplemodeling-lib`:

- headings: Org-style, ATX Markdown, and setext;
- ordinary paragraphs and logical prose lines;
- unordered, ordered, and definition lists;
- pipe tables;
- figures and image references;
- source/example/verbatim blocks;
- quotations and horizontal rules;
- inline markup, links, image references, and safe inert inline macros;
- XML and JSON structured tokens preserved as logical tokens.

## Disabled Features

The Textus profile does not execute site or external-data features during CNCF
content rendering:

- no DoxSite navigation/title resolution;
- no include execution;
- no generated diagram execution;
- no CSV-backed table inclusion;
- no bibliography, glossary, RDF, semanticweb, or JSON-LD pipelines;
- no macro side effects.

Unsupported executable constructs must render as inert placeholders or fail
deterministically. They must not perform side effects.

## Reference Policy

SmartDox image and link references are extracted into
`ContentReferenceOccurrence`.

Image references are normalized through the CNCF media path and record canonical
media identity in `normalizedRef`, `urn`, and `targetEntityId`.

SmartDox source spans are the marker for references that may need future
rewrite. The parser attaches them only to concrete, parsed reference nodes
whose source range is mapped back to the original document. Successful image
references rewrite the target URI/path span to
`urn:textus:image:{entropy}`. Link references are indexed but are not
rewritten.

Identical text inside source blocks, XML/JSON structured tokens, comments, or
plain prose is not touched because those regions do not produce source-spanned
reference nodes. If an image reference cannot be normalized, the original
reference is kept and a deterministic `textus:image-normalization-failed`
comment is inserted near that source node.

The source-spanned rewrite surface includes ordinary paragraphs, block image
lines, headings, list items, definition list terms/descriptions, table cells,
captions, quote prose, and mapped inline markup such as bold/italic/delete.
Inline code/pre text remains inert.

## Rendering Policy

SmartDox rendering for CNCF is safe HTML fragment rendering:

- escape text by default;
- render XML/JSON structured tokens as escaped code-like content;
- do not execute macros or external data sources;
- wrap browser-facing output through the CNCF content rendering workflow.

## Deferred Scope

- SmartDox multilingual rendering and authoring syntax details.
- Full legacy SmartDox site generation compatibility.
