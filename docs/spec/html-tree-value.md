# CNCF HTML Tree Value

This specification defines CNCF standard values for processing HTML as a tree.
The values are reusable processing and presentation values, not persistence
records.

## Standard Values

- `HtmlDocument`: parsed full HTML document.
- `HtmlNode`: common node contract.
- `HtmlElement`: element node with tag name, attributes, and children.
- `HtmlText`: text node.
- `HtmlComment`: comment node.
- `HtmlFragment`: renderable node sequence extracted from a document.

## Required Behavior

- Parse HTML text or UTF-8 files into `HtmlDocument`.
- Read document metadata:
  - `head.title`
  - `meta[name=description]`
  - `link[rel=canonical]`
- Extract article content using this order:
  - `article[data-blog-content]`
  - first `article`
  - `main article`
  - otherwise deterministic validation failure.
- Find `img` elements in a fragment and expose `src`, `alt`, `title`, and
  occurrence index.
- Rewrite `img src` values and render the fragment back to HTML.

## Storage Boundary

HTML tree values are processing/presentation values. Application entities may
store canonical rendered HTML fragments as strings, but should not persist the
HTML tree value directly unless a component-specific storage contract explicitly
defines that shape.
