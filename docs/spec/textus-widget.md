# Textus Widget Specification

## Purpose

This specification defines Textus widgets for Static Form App and
Bootstrap-based CNCF/Textus Web pages.

Widgets let CML metadata, static HTML, operation results, and runtime
properties produce readable business/admin pages without introducing a general
template programming language.

## Status

This spec defines the widget contract and early widget vocabulary. Existing
implemented widgets are normative compatibility requirements. Card, layout,
navigation, and feedback widgets are the next expansion surface.

## Notation

The canonical notation is namespace style:

```html
<textus:record-card source="result" view="detail"></textus:record-card>
```

The HTML-compatible custom-element style is also valid:

```html
<textus-record-card source="result" view="detail"></textus-record-card>
```

Both forms are semantically equivalent. Renderers should prefer the namespace
form in generated examples and documentation. The dash form exists for HTML
tooling compatibility.

Unexpanded `textus:` or `textus-` elements in the final HTTP response are
rendering defects.

## Rendering Model

Widget rendering is server-side and happens before the final HTML response is
returned.

Widgets render ordinary Bootstrap 5 markup. They must not require a separate
client-side widget framework and must remain useful with only local Bootstrap
assets:

```text
/web/assets/bootstrap.min.css
/web/assets/bootstrap.bundle.min.js
```

Applications may add local CSS, but widget output must not depend on CDN access
or a parallel CSS system.

## Data Binding

Widgets use the same property and result binding model as existing Static Form
App result widgets.

Common attributes:

- `source`: selects the source object, collection, property, result body, or
  action.
- `view`: selects a schema/view field set.
- `entity`: optionally identifies the entity schema used for display metadata.
- `title`, `subtitle`, `message`, `variant`: widget-specific presentation
  attributes.
- `href`, `action`: navigation/action attributes where supported.

Default view rules:

- single-record widgets default to `detail` when available.
- list widgets default to `summary` when available.
- if the requested/default view is unavailable, the renderer may fall back to
  resolved schema field order.

Field order must come from the resolved schema/view field vector. Widget
rendering must not depend on reflection order or unordered maps.

## Schema Source

The primary display metadata source is the Web schema path:

```text
CML entity/value/operation metadata
  -> org.goldenport.schema.Schema / ParameterDefinition
  -> EntityRuntimeDescriptor / operation metadata
  -> WebSchemaResolver.ResolvedWebSchema
  -> Textus widget renderer
```

`WebDescriptor` may supplement or override presentation details such as field
selection, labels, help, placeholders, and control hints. It must not become the
primary schema source.

If a widget needs metadata that the shared schema cannot express, the preferred
fix is to extend `org.goldenport.schema.Schema` or the shared Web schema model
rather than add a widget-only metadata path.

## Bootstrap Output Contract

Widgets render normal Bootstrap 5 elements and classes.

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
- `.table-responsive`
- `.pagination`
- `.badge`
- `.btn`
- `.btn-outline-*`

Widget output must be responsive by default. Dense list output should use
`.table-responsive` or Bootstrap grid/card layout. Action groups must wrap on
narrow screens.

## Existing Widgets

The following widgets are the baseline compatibility set:

- `textus-result-view`
- `textus-result-table`
- `textus-property-list`
- `textus-error-panel`
- `textus:action-link`
- `textus-action-link`
- `textus:action-form`
- `textus-action-form`
- `textus:hidden-context`
- `textus-hidden-context`
- `textus:pagination`
- `textus-pagination`

New widgets must reuse the same property expansion, source lookup, action, and
paging conventions where applicable.

## Action Widgets

`textus:action-link`, `textus-action-link`, `textus:action-form`, and
`textus-action-form` are the baseline action widgets. Other widgets should
compose with them rather than introduce a separate action model.

Action rendering rules:

- `GET` action renders as an anchor/button link.
- non-`GET` `textus:action-link` renders as an inline form.
- `textus:action-form` always renders a form; the action metadata method is
  used unless the widget has a `method` override.
- form-rendered action widgets include standard hidden page context by default.
  Set `context="false"` to suppress hidden context.
- missing or unauthorized `href` renders nothing.

Example:

```html
<textus:record-card source="result" view="detail">
  <textus:action-link source="result.action.primary"></textus:action-link>
</textus:record-card>
<textus:action-form source="result.action.await"></textus:action-form>
```

## Hidden Context Widget

`textus:hidden-context` and `textus-hidden-context` render hidden form inputs
for Static Form App page context. They are intended to be placed inside a plain
HTML `form` element.

Example:

```html
<form method="post" action="/form/notice-board/notice/search-notices">
  <textus:hidden-context></textus:hidden-context>
  <input class="form-control" name="recipientName">
  <button class="btn btn-primary" type="submit">Search again</button>
</form>
```

The widget renders only framework-defined hidden context properties by default:

- `crud.origin.href`
- `crud.success.href`
- `crud.error.href`
- `paging.page`
- `paging.pageSize`
- `paging.chunkSize`
- `paging.includeTotal`
- `paging.href`
- `continuation.id`
- `textus.admin.principalId`
- `textus.admin.subjectId`
- `version`
- `etag`
- `csrf`
- properties under `search.*`

These properties are page context, not operation input. The submit handler must
continue to filter them out before operation dispatch.

Additional explicit keys may be included with `keys`:

```html
<textus:hidden-context keys="return.href,ui.tab"></textus:hidden-context>
```

The `keys` attribute is for application-defined page context. It must not be
used to smuggle ordinary operation arguments around the form schema.

## Paging

List widgets must reuse the existing paging and continuation model used by
`textus-result-table`.

Requirements:

- page size defaults to the runtime/table default unless explicitly provided.
- continuation-backed paging works without total count.
- explicit total-count opt-in is honored only when runtime capability and
  descriptor policy allow it.
- rendered paging controls remain valid plain HTML links/forms.

## Error Handling

Widgets fail closed.

Required behavior:

- missing source renders an empty state rather than throwing.
- non-collection source for a list widget renders an empty state or one
  diagnostic block in development/test mode.
- unresolved view falls back to schema/default field order.
- unresolved schema renders best-effort object fields only when stable metadata
  is available.
- production pages must not expose raw exceptions.

## Card Widgets

### `textus:card` / `textus-card`

Generic Bootstrap card container.

Example:

```html
<textus:card title="Notice" subtitle="Published">
  <p>Static content or nested Textus widgets.</p>
</textus:card>
```

Required behavior:

- renders `.card` and `.card-body`.
- renders title/subtitle when provided.
- preserves nested rendered widget content.
- may support footer/actions in a later extension.

### `textus:record-card` / `textus-record-card`

Renders one record/entity/result as a Bootstrap card.

Example:

```html
<textus:record-card source="result" view="detail"></textus:record-card>
```

Required behavior:

- resolves one object from `source`.
- selects fields from `view`, defaulting to `detail`.
- renders title from an explicit `title` attribute or schema/display role when
  available.
- renders remaining fields as a compact property/description list.
- allows nested `textus:action-link` widgets for actions.

Implemented baseline attributes:

- `source`: record source. Defaults to `result.body`.
- `entity`: entity name used to resolve CML/Web view columns.
- `view`: view name. Defaults to the renderer default view when omitted.
- `columns`: comma-separated fallback column list.
- `title`: field name used as card title. Defaults to `title`, `subject`,
  `name`, `label`, then `id` when present.
- `subtitle`: field name used as card subtitle. Defaults to common secondary
  fields such as `recipient_name`, `sender_name`, `status`, and `updated_at`.

### `textus:card-list` / `textus-card-list`

Renders a collection as Bootstrap cards.

Example:

```html
<textus:card-list source="result.items" view="summary"></textus:card-list>
```

Required behavior:

- resolves a collection from `source`.
- renders each item using the same field/view logic as `record-card`.
- defaults to `summary` view.
- supports the same paging metadata model as `textus-result-table`.
- works when total count is absent.
- renders paging controls when continuation/page metadata is available.

Implemented baseline attributes:

- `source`: collection source. Defaults to `result.body`.
- `entity`: entity name used to resolve CML/Web view columns.
- `view`: view name. Defaults to the renderer default view when omitted.
- `columns`: comma-separated fallback column list.
- `page`, `page-size`, `total`, `href`, `has-next`: same metadata attributes
  as `textus:pagination`.
- `title`, `subtitle`: forwarded to each record card.

### `textus:summary-card` / `textus-summary-card`

Renders a compact metric/status card.

Example:

```html
<textus:summary-card title="HTML requests" value="${result.count}" variant="primary"></textus:summary-card>
```

Required behavior:

- renders one prominent value with a title and optional subtitle.
- maps variants conservatively to Bootstrap contextual classes.
- is suitable for Dashboard and admin overview pages.

Implemented baseline attributes:

- `title`: literal title or property reference.
- `value`: literal value, property name, or `${property.name}` reference.
- `subtitle`: optional literal subtitle, property name, or `${property.name}`
  reference.
- `source`: fallback property/JSON source for the value when `value` is absent.
- `variant`: Bootstrap contextual variant. `error` maps to `danger`.

### `textus:action-card` / `textus-action-card`

Renders a title/description/action panel.

Required behavior:

- renders title and optional description.
- resolves action using the same model as `textus:action-link`.
- renders a Bootstrap button or inline form depending on action method.

### `textus:card-grid` / `textus-card-grid`

Optional layout widget for cards.

This may become a standalone widget or a layout option on `card-list`. A first
implementation should prefer the simpler shape unless a separate layout widget
removes real duplication.

## Layout Widgets

### `textus:section` / `textus-section`

Semantic section wrapper.

Required behavior:

- renders a title/subtitle and body content.
- uses Bootstrap spacing conventions.
- lets static pages group content without repetitive hand-written markup.

### `textus:grid` / `textus-grid`

Bootstrap grid wrapper.

Required behavior:

- accepts column options such as `cols`, `md`, and `lg`.
- renders nested content in responsive Bootstrap columns.
- may be used by card lists or action panels.

## Navigation Widgets

### `textus:breadcrumb` / `textus-breadcrumb`

Breadcrumb navigation.

Required behavior:

- renders Bootstrap `.breadcrumb`.
- reads explicit items or action/path metadata.
- is usable for admin detail/edit pages and static app drill-down pages.

### `textus:nav-list` / `textus-nav-list`

List of navigation links or actions.

Required behavior:

- renders Bootstrap `.list-group` or `.nav` depending on style.
- supports `source` for result-provided links.
- supports explicit static child items when needed.

### `textus:pagination` / `textus-pagination`

Standalone paging controls.

Required behavior:

- reuses `textus-result-table` paging and continuation metadata.
- supports no-total-count paging.
- may be embedded by result-table, card-list, or custom static pages.

Default attributes:

- `page="paging.page"`
- `page-size="paging.pageSize"`
- `total="paging.total"`
- `href="paging.href"`
- `has-next="paging.hasNext"`

`total` is optional. When total count is absent, the widget renders the current
page and previous/next controls. `has-next="false"` disables the next link.

## Feedback Widgets

### `textus:alert` / `textus-alert`

Bootstrap alert.

Required behavior:

- renders messages from `source`, `message`, or result/error metadata.
- maps variants conservatively to Bootstrap alert classes.
- supports success, warning, error, and information messages.

Implemented baseline attributes:

- `title`: optional literal title or property reference.
- `message`: literal message, property name, or `${property.name}` reference.
- `source`: fallback property/JSON source for the message.
- `variant` or `type`: Bootstrap contextual variant. `error` maps to `danger`.

### `textus:empty-state` / `textus-empty-state`

Empty result state.

Required behavior:

- renders a friendly message when a source collection is empty.
- optionally renders a primary action.
- can be used by table and card-list widgets.

Implemented baseline attributes:

- `source`: optional collection source. If the source resolves to a non-empty
  collection, nothing is rendered.
- `message`: literal message, property name, or `${property.name}` reference.

### `textus:status-badge` / `textus-status-badge`

Status badge.

Required behavior:

- renders Bootstrap `.badge`.
- maps common status values to contextual classes.
- remains data driven; application-specific mapping can be added later through
  descriptor or schema hints.

## Content Widgets

### `textus:description-list` / `textus-description-list`

Bootstrap-friendly definition-list projection for record/detail data.

Required behavior:

- renders a `<dl>` detail layout.
- uses resolved schema/view field order.
- is suitable for Manual, admin detail pages, and card bodies.

### `textus:markdown` / `textus-markdown`

Trusted content renderer for Markdown-like documentation fragments.

Required behavior:

- must not render untrusted user content as raw HTML.
- is not part of the first implementation unless a trusted-content source is
  defined.

## Form Helper Widgets

### `textus:form-errors` / `textus-form-errors`

Validation summary widget.

Required behavior:

- renders form-level validation summary.
- complements field-level Bootstrap feedback generated by forms.

### `textus:hidden-context` / `textus-hidden-context`

Hidden form context rendering point.

Required behavior:

- renders standard hidden properties such as origin, paging, search,
  continuation, optimistic token, and security placeholders.
- preserves context across custom static forms.

## Non-Goals

Widgets must not:

- introduce arbitrary template control structures.
- become a client-side component framework.
- bypass operation authorization or Web ingress policy.
- invent a separate action model.
- make WebDescriptor the primary schema source.

## Acceptance Criteria

The widget implementation is conformant when:

- both `textus:xxx` and `textus-xxx` notation are recognized for supported
  widgets.
- no unexpanded Textus widget tags appear in final HTML for supported widgets.
- widgets render local-Bootstrap-compatible HTML.
- field order follows resolved schema/view order.
- action and paging widgets reuse the existing action/paging metadata.
- missing data fails closed with an empty state or controlled diagnostic.
- `textus-result-table` continues to work without total count.

## Related Documents

- `docs/design/web-layer.md`
- `docs/design/web-form-api-schema.md`
- `docs/design/management-console.md`
- `docs/notes/web-textus-widget-design.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `docs/journal/2026/04/web-bootstrap-card-widget-note.md`
