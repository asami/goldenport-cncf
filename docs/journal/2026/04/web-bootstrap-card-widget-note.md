# Web Bootstrap 5 Card Widget Direction

## Context

Phase 12 has established the basic Web foundation:

- Dashboard, Management Console, Manual, and Static Form App use the local
  Bootstrap 5 assets.
- Static Form App pages can be built from CML metadata, static HTML result
  templates, and Textus widgets.
- `textus-sample-app` validates the minimum notice-board flow with static
  result pages, result widgets, paging, error panels, and async command result
  waiting.

The next practical concern is UI quality. The current foundation is functional,
but Static Form App should be able to produce screens that look like normal
Bootstrap 5 business/admin applications without requiring an external Web
framework.

## Decision Direction

Bootstrap 5 should remain the default UI basis for framework-generated Web
pages and for Static Form App widgets.

This is not only visual polish. Bootstrap 5 gives the framework a stable,
offline, responsive component vocabulary for:

- admin lists, detail pages, edit/new forms, and operation forms
- dashboard cards, metrics, warnings, tables, and tabs
- manual/reference navigation
- static application result pages

Offline use remains mandatory, so Bootstrap assets must continue to be served
from local runtime assets rather than from a CDN.

## Widget Direction

Static Form App should gain a richer Textus widget set, especially card-oriented
widgets. Card widgets are important because many simple business applications
are easier to read as cards than as tables:

- search result summaries
- notice/message boards
- detail pages
- menu/action panels
- dashboard summary blocks

The preferred notation remains namespace style:

```html
<textus:record-card source="result" view="detail"></textus:record-card>
```

The compatible HTML-custom-element style should continue to work:

```html
<textus-record-card source="result" view="detail"></textus-record-card>
```

The namespace form is the semantic Textus widget notation. The dash form is the
HTML-compatible notation.

## Candidate Card Widgets

Initial card widget candidates:

- `textus:card` / `textus-card`
  - Generic Bootstrap card container.
  - Useful when static HTML wants a consistent shell around manually authored
    content.
- `textus:record-card` / `textus-record-card`
  - Renders one record/entity/result as a Bootstrap card.
  - Uses `source` and optional `view`.
  - Suitable for detail pages.
- `textus:card-list` / `textus-card-list`
  - Renders a collection of records as cards.
  - Uses `source`, optional `view`, and paging metadata when available.
  - Suitable for notice-board and search result screens.
- `textus:summary-card` / `textus-summary-card`
  - Renders a compact metric/status card.
  - Useful for dashboard and admin top pages.
- `textus:action-card` / `textus-action-card`
  - Renders a title/description/action card.
  - Useful for menu pages and next-step guidance.
- `textus:card-grid` / `textus-card-grid`
  - Provides a Bootstrap grid layout for cards.
  - May be folded into `card-list` if a separate layout widget is not needed.

## Binding Model

Card widgets should use the same data binding model as existing result widgets:

- `source` selects the result/property object.
- `view` selects the CML/Web schema view, defaulting to `summary` for list-like
  widgets and `detail` for single-record widgets when available.
- Field order must come from the resolved schema/view field vector, not from
  reflection or unordered maps.
- WebDescriptor may override presentation details, but it must not become the
  primary schema source.

This keeps the card widget path aligned with the existing table widget direction:
CML-derived schema and view metadata are the primary source, while WebDescriptor
is supplemental.

## Bootstrap Output Policy

Card widgets should render normal Bootstrap 5 markup:

- `.card`
- `.card-body`
- `.card-title`
- `.card-subtitle`
- `.card-text`
- `.card-footer`
- `.row` / `.col-*` for card grids
- `.btn` and `.btn-outline-*` for actions

The framework should avoid inventing a parallel design system. Textus widgets
are server-side semantic widgets that project application data into Bootstrap 5
HTML.

## Scope Boundary

The goal is to make simple and internal Web applications look good with CML +
static HTML + widgets.

Advanced interaction-heavy screens can still use an external Web framework and
call CNCF through REST or Form API. The Static Form App layer should stay
expression-oriented and widget-oriented; it should not grow a general-purpose
template programming language.

## Implementation Order

Recommended implementation sequence:

1. Define the card widget contract in the Static Form App contract document.
2. Implement `record-card`.
3. Implement `card-list` with the same paging behavior as `result-table`.
4. Add `summary-card` for dashboard/admin summary use.
5. Validate with `textus-sample-app` by rendering notice search results as
   cards in addition to the current table view.

## Open Questions

- Whether `card-grid` should be a standalone widget or a layout option on
  `card-list`.
- How much of a card should be driven by explicit attributes such as `title`,
  `subtitle`, and `body` versus schema/view roles.
- Whether CML should introduce portable display roles such as title, subtitle,
  summary, content, and action label, or whether existing schema/view metadata
  is enough for the first implementation.
- How card actions should compose with `textus:action-link` so applications can
  add detail/edit/await links without embedding control syntax.

## Current Position

Bootstrap 5 polish and card widgets should be treated as the next Web UI
quality layer after Phase 12's functional baseline. The initial target is not a
new application framework; it is a better default widget set for Static Form App
and Management Console pages.
