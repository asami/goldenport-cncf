# Web Textus Widget Design

The current widget specification is maintained in
`docs/spec/textus-widget.md`. This note remains as the design discussion and
implementation-planning context behind that spec.

## Purpose

This note defines the Textus widget contract for Static Form App and Bootstrap
5 based Web pages.

The goal is to let CML metadata, static HTML, and Textus widgets produce
readable business/admin screens without adding a general template programming
language. Card widgets are the first high-value extension, but the widget set
also needs layout, feedback, navigation, and media-like building blocks.

Bootstrap 5 page polish is covered separately in
`docs/notes/web-bootstrap-ui-polish-design.md`.

## Scope

In scope:

- card-oriented Textus widgets
- layout and navigation widgets needed by Static Form App pages
- feedback widgets for alerts, empty states, and status display
- lightweight media/content widgets for common business pages
- `textus:xxx` preferred notation and `textus-xxx` compatible notation
- server-side widget rendering before the final HTML response
- source/view data binding
- CML-derived schema/view metadata as the primary display metadata source
- Bootstrap 5 HTML output contract
- paging/action integration for card lists

Out of scope:

- overall page layout polish for Dashboard/Admin/Manual
- arbitrary template control structures
- a visual design system separate from Bootstrap 5
- client-side widget frameworks

## Notation

The preferred notation is namespace style:

```html
<textus:record-card source="result" view="detail"></textus:record-card>
```

The HTML-compatible custom-element style is also accepted:

```html
<textus-record-card source="result" view="detail"></textus-record-card>
```

Both forms are semantically equivalent. The namespace form is the canonical
Textus widget notation. The dash form exists for HTML tooling compatibility.

Unexpanded `textus:` or `textus-` elements in the final response should be
treated as rendering defects.

## Data Binding

Card widgets use the same binding model as existing Static Form App result
widgets:

- `source` selects the result/property object.
- `view` selects the field set used for rendering.
- `href`, `action`, or nested action widgets define navigation.
- missing optional values render as empty or are omitted, depending on the
  widget.

The default view depends on widget shape:

- single-record widgets default to `detail` when available.
- list widgets default to `summary` when available.
- if the requested/default view is unavailable, the renderer may fall back to
  the resolved schema field order.

Field order must come from the resolved schema/view field vector. Rendering
must not depend on reflection order or unordered maps.

## Schema Source

The primary metadata source is the CML-derived schema/view information carried
through the existing Web schema path:

```text
CML entity/value/operation metadata
  -> org.goldenport.schema.Schema / ParameterDefinition
  -> EntityRuntimeDescriptor / operation metadata
  -> WebSchemaResolver.ResolvedWebSchema
  -> Textus widget renderer
```

WebDescriptor may override or supplement presentation details such as field
selection, labels, help, placeholders, and control hints, but it must not become
the primary schema source.

If card widgets need metadata that the shared schema cannot express, the
preferred fix is to extend the shared schema model rather than add a
card-widget-only metadata path.

## Bootstrap Output Contract

Textus widgets render normal Bootstrap 5 markup.

Expected classes include, depending on widget:

- `.card`
- `.card-body`
- `.card-title`
- `.card-subtitle`
- `.card-text`
- `.card-footer`
- `.row`
- `.col-*`
- `.alert`
- `.list-group`
- `.nav`
- `.breadcrumb`
- `.table`
- `.pagination`
- `.badge`
- `.btn`
- `.btn-outline-*`

The widget renderer should not invent a parallel CSS system. Applications may
add local CSS, but generated output must be useful with Bootstrap 5 alone.

## Existing Widgets

The existing widgets remain valid and are the baseline compatibility set:

- `textus-result-view`
- `textus-result-table`
- `textus-property-list`
- `textus-error-panel`
- `textus:action-link`
- `textus-action-link`

New widgets should use the same property expansion, source lookup, action, and
paging conventions where applicable.

## Initial Card Widgets

### `textus:card`

Generic Bootstrap card container.

Typical use:

```html
<textus:card title="Notice" subtitle="Published">
  <p>Static content or nested Textus widgets.</p>
</textus:card>
```

Expected behavior:

- renders a `.card` and `.card-body`.
- renders title/subtitle when provided.
- preserves nested rendered widget content.
- optional footer/actions may be added later.

### `textus:record-card`

Renders one record/entity/result as a Bootstrap card.

Typical use:

```html
<textus:record-card source="result" view="detail"></textus:record-card>
```

Expected behavior:

- resolves one object from `source`.
- selects fields from `view`, defaulting to `detail`.
- renders a title from an explicit `title` attribute or a schema/display role
  when available.
- renders remaining fields as a compact property list inside the card.
- allows nested `textus:action-link` widgets for actions.

### `textus:card-list`

Renders a collection of records as Bootstrap cards.

Typical use:

```html
<textus:card-list source="result.items" view="summary"></textus:card-list>
```

Expected behavior:

- resolves a collection from `source`.
- renders each item through the same field/view logic as `record-card`.
- defaults to `summary` view.
- supports the same paging metadata model as `textus-result-table`.
- works when total count is absent.
- renders paging controls when continuation/page metadata is available.

### `textus:summary-card`

Renders a compact metric/status card.

Typical use:

```html
<textus:summary-card title="HTML requests" value="${result.count}" variant="primary"></textus:summary-card>
```

Expected behavior:

- renders one prominent value with a title and optional subtitle.
- maps variants to Bootstrap contextual classes conservatively.
- is suitable for Dashboard and admin overview pages.

### `textus:action-card`

Renders a title/description/action panel.

Typical use:

```html
<textus:action-card title="Open notice" action="result.action.detail"></textus:action-card>
```

Expected behavior:

- renders title and optional description.
- resolves the action using the same model as `textus:action-link`.
- renders a Bootstrap button or inline form depending on action method.

### `textus:card-grid`

Optional layout widget for cards.

This may become a standalone widget or a layout option on `card-list`. The first
implementation should prefer the simpler shape unless there is a clear need for
a separate layout widget.

## Additional Widget Candidates

These are not all first-implementation requirements, but they should be part of
the early widget design vocabulary so the framework does not overfit on cards.

### Layout Widgets

#### `textus:section`

Semantic section wrapper.

Expected behavior:

- renders a title/subtitle and body content.
- uses Bootstrap spacing conventions.
- gives static pages a consistent way to group content without hand-writing
  repetitive markup.

#### `textus:grid`

Bootstrap grid wrapper for generic repeated content.

Expected behavior:

- accepts column options such as `cols`, `md`, and `lg`.
- renders nested widget content in responsive Bootstrap columns.
- may be used by card lists or action panels.

### Navigation Widgets

#### `textus:breadcrumb`

Breadcrumb navigation.

Expected behavior:

- renders Bootstrap `.breadcrumb`.
- reads explicit items or action/path metadata.
- useful for admin detail/edit pages and static app drill-down pages.

#### `textus:nav-list`

List of navigation links or actions.

Expected behavior:

- renders Bootstrap `.list-group` or `.nav` depending on style.
- supports `source` for result-provided links.
- supports explicit static child items if needed.

#### `textus:pagination`

Standalone paging controls.

Expected behavior:

- reuses the existing `result-table` paging and continuation metadata.
- supports no-total-count paging.
- may be embedded by `result-table`, `card-list`, or custom static pages.

### Feedback Widgets

#### `textus:alert`

Bootstrap alert.

Expected behavior:

- renders messages from `source`, `message`, or result/error metadata.
- maps variants conservatively to Bootstrap alert classes.
- can be used for success, warning, error, and information messages.

#### `textus:empty-state`

Empty result state.

Expected behavior:

- renders a friendly message when a source collection is empty.
- optionally renders a primary action.
- can be used by table and card list widgets.

#### `textus:status-badge`

Status badge.

Expected behavior:

- renders Bootstrap `.badge`.
- maps common status values to contextual classes.
- remains data driven; application-specific mapping can be added later through
  descriptor or schema hints.

### Content Widgets

#### `textus:description-list`

Definition-list projection for record/detail data.

Expected behavior:

- similar to `textus-property-list`, but explicitly renders Bootstrap-friendly
  `<dl>` detail layout.
- useful for Manual and admin detail pages.

#### `textus:markdown`

Safe Markdown-like content renderer for trusted static content or generated
documentation fragments.

Expected behavior:

- not part of the first implementation unless a clear trusted-content source is
  defined.
- must avoid rendering untrusted user content as raw HTML.

### Form Helper Widgets

#### `textus:form-errors`

Validation summary widget.

Expected behavior:

- renders the form-level validation summary.
- complements field-level Bootstrap feedback generated by forms.

#### `textus:hidden-context`

Explicit rendering point for standard hidden form context.

Expected behavior:

- renders standard hidden properties such as origin, paging, search,
  continuation, optimistic token, and security placeholders.
- useful if static pages need to preserve context across custom forms.

## Actions

Widgets should compose with `textus:action-link` rather than invent a
separate action mechanism.

For example:

```html
<textus:record-card source="result" view="detail">
  <textus:action-link source="result.action.primary"></textus:action-link>
</textus:record-card>
```

The action rendering rule remains:

- `GET` action -> anchor/button link.
- non-`GET` action -> inline POST form.
- missing `href` -> render nothing.

## Paging

`textus:card-list` should reuse the existing paging and continuation model used
by `textus-result-table`.

Requirements:

- page size defaults to the runtime/table default unless explicitly provided.
- continuation-backed paging works without total count.
- explicit total-count opt-in is honored when the runtime and descriptor allow
  it.
- the rendered paging controls should remain valid plain HTML links/forms.

## Error Handling

Widgets should fail closed:

- missing source: render an empty state rather than throw.
- non-collection source for `card-list`: render an empty state or one diagnostic
  block in development mode.
- unresolved view: fall back to schema/default field order.
- unresolved schema: render best-effort object fields in stable order only when
  stable metadata is available.

The renderer should avoid exposing raw exceptions in production pages.

Card widgets are not replacements for tables. They add an alternate projection
for result/detail data. Applications should be able to offer both:

- table view for dense administrative lists.
- card view for readable business/user-facing summaries.

## Implementation Plan

Recommended implementation order:

1. Add the general widget contract to the Static Form App widget renderer tests.
2. Implement `textus:record-card` and `textus-record-card`.
3. Implement `textus:card-list` and `textus-card-list`.
4. Reuse `textus-result-table` paging metadata for `card-list`.
5. Extract shared pagination rendering into `textus:pagination` if duplication
   appears.
6. Implement `textus:summary-card` for Dashboard/admin overview use.
7. Add `textus:alert` and `textus:empty-state` once card/table result pages
   need consistent feedback.
8. Validate with `textus-sample-app` by rendering notice search results as
   cards in addition to the current table view.

## Open Design Questions

- Whether display roles such as title, subtitle, summary, and content should be
  added to the shared schema model.
- Whether `card-grid` should be a separate widget or an option on `card-list`.
- How much explicit card layout should be allowed through attributes before it
  becomes a template language.
- Whether Management Console should use card widgets directly or keep its own
  Bootstrap-rendered Scala helpers for operational pages.
- Whether `textus:hidden-context` is useful enough as a public widget or should
  remain an internal form-rendering helper.
- Whether `textus:markdown` belongs in the core widget set or should be a later
  documentation/manual-specific extension.

## Related Documents

- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/notes/cncf-web-layer-phase-12.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `docs/design/web-form-api-schema.md`
- `docs/design/management-console.md`
- `docs/journal/2026/04/web-bootstrap-card-widget-note.md`
