# Web Bootstrap 5 UI Polish Design

## Purpose

This note defines the Bootstrap 5 UI polish direction for CNCF/Textus Web
pages.

Phase 12 established the functional Web foundation: Dashboard, Management
Console, Manual, Form API, and Static Form App. The next UI layer should make
those pages look and behave like normal responsive Bootstrap 5 business/admin
applications while keeping the implementation simple and offline-friendly.

## Scope

In scope:

- common Bootstrap 5 layout conventions for built-in Web pages
- Dashboard visual polish
- Management Console visual polish
- Manual/reference visual polish
- Static Form App default page polish
- responsive desktop/tablet/smartphone behavior
- local Bootstrap asset usage

Out of scope:

- Textus widget API details such as `textus:card-list`
- domain-specific application page design
- a new visual design system separate from Bootstrap 5
- advanced SPA behavior or client-side application framework concerns

Textus widget design is covered separately in
`docs/notes/web-textus-widget-design.md`.

## Bootstrap 5 Basis

Bootstrap 5 is the default UI basis for framework-generated Web pages and
Static Form App pages.

The runtime must not depend on CDN access. Bootstrap assets are served from
local Web assets under `/web/assets`, starting with:

```text
/web/assets/bootstrap.min.css
/web/assets/bootstrap.bundle.min.js
```

Application pages may add local CSS or JavaScript, but the baseline must be
usable with local Bootstrap assets alone.

## Common Layout

Built-in Web pages should follow a consistent shell:

- page title and short subtitle
- primary navigation near the top
- constrained content width for text-heavy pages
- Bootstrap grid for dashboard/admin summary areas
- consistent spacing between sections
- tables inside `.table-responsive`
- forms using Bootstrap form controls and validation feedback
- no CDN references

The default layout should remain conservative. It should improve readability
without making the generated page look like an embedded preview or a decorative
marketing page.

## Design Quality Criteria

For the current Phase 12 implementation, `polish` means Bootstrap-backed page
composition that is more readable and operable than raw Bootstrap components
placed directly on a page.

The framework should not introduce a separate visual design system in this
phase. Instead, it should apply stable composition rules on top of Bootstrap 5.

Required criteria:

- information hierarchy is explicit:
  page title, subtitle, navigation, primary content, and secondary actions are
  visually distinct.
- navigation and actions are separated:
  top navigation links, page-level primary actions, and row/detail actions use
  different placement and button treatment.
- data density matches the page type:
  list pages use compact responsive tables, while detail pages use readable
  property sections and forms use grouped controls.
- primary actions are discoverable:
  create/update/edit actions use Bootstrap button styles and are placed near the
  content they affect.
- status and validation are visible:
  warnings, errors, validation messages, and empty states use Bootstrap
  feedback components rather than plain text where practical.
- responsive behavior is preserved:
  tables stay inside responsive wrappers and action groups must wrap on narrow
  screens.
- local Bootstrap remains sufficient:
  generated pages must be usable with the local `/web/assets` Bootstrap files
  without CDN access or application-specific CSS.
- runtime contracts do not change:
  visual polish must not alter Web/Form/API/REST paths, form field names,
  result properties, pagination parameters, or operation dispatch behavior.

For the Management Console entity pages currently being implemented, these
criteria map to:

- card sections for navigation and main content
- responsive hover tables for list/detail data
- grouped row actions for detail/edit links
- explicit form action areas for create/update/cancel controls
- existing validation and hidden-form context behavior preserved unchanged

Executable checks cover the current non-screenshot baseline:

- generated built-in Web pages include only local Bootstrap CSS and JavaScript
  asset paths and do not reference CDN URLs.
- generated built-in Web pages include the viewport meta tag required for
  tablet and smartphone rendering.
- generated built-in Web pages expose responsive Bootstrap structure such as
  `.table-responsive`, wrapping action groups, cards, and shadowed section
  surfaces.
- dashboard and manual polish checks assert Bootstrap health badges,
  card-based manual sections, and responsive tables without changing the
  dashboard JSON state contract or manual read-only behavior.

Future UI quality work beyond these fixed criteria is tracked in
`docs/journal/2026/04/web-bootstrap-polish-quality-future-note.md`.

## Dashboard Polish

Dashboard is the real-time operational view.

Bootstrap usage should emphasize quick health recognition:

- `.card` based health/status panels
- `.badge` for state labels
- `.progress` or simple bars for activity ratios
- `.table` for recent events and counts
- `.nav-tabs` or button groups for graph windows
- responsive grid that remains readable on tablet and smartphone screens

Dashboard should keep the existing functional baseline:

- refresh about once per second
- show CNCF/Textus version, subsystem, component versions
- show HTML request, ActionCall, DSL chokepoint, authorization, and job metrics
- link to admin/performance/manual detail pages

Visual polish must not introduce a separate runtime data path.

## Management Console Polish

Management Console is the strongest Bootstrap 5 candidate because it uses
standard admin patterns.

Recommended polish direction:

- list pages use Bootstrap tables with responsive wrappers
- detail pages use definition lists or property-list style sections
- edit/new pages use Bootstrap form groups, labels, help text, and validation
  feedback
- action areas use `.btn`, `.btn-primary`, `.btn-outline-secondary`, and
  grouped navigation
- collection pages use clear list/detail/edit/new/create routes
- top pages use concise action panels for entity, data, view, and aggregate
  entry points

Management Console should not depend on Static Form App result-page
conventions. It may reuse Textus widgets where useful, but its operational
flows remain built-in admin console flows.

## Manual Polish

Manual/reference pages are read-only.

Recommended Bootstrap usage:

- list groups or button groups for component/service/operation navigation
- small tables for schema/describe data
- code formatting for selectors and paths
- cards or sections for Help, Describe, Schema, OpenAPI, and MCP references
- clear links back to Dashboard, Admin, Performance, and Console

Manual pages must not render mutation forms or inline operation execution.

## Static Form App Polish

Static Form App should be able to produce usable pages from CML metadata,
static HTML, and Textus widgets.

Default rendering should assume:

- Bootstrap form controls for generated forms
- Bootstrap validation feedback for Web input admission errors
- Bootstrap tables for dense result lists
- Bootstrap cards for readable detail/list summaries once card widgets are
  implemented
- local assets only

Static Form App remains expression-oriented and widget-oriented. It should not
grow loops, conditionals, or arbitrary template code. When richer UI behavior is
needed, applications can use an external Web framework and call CNCF through
REST or Form API.

## Responsive Behavior

The target devices include desktop, tablet, and smartphone browsers.

Guidelines:

- use Bootstrap rows and columns rather than fixed-width layout
- keep tables inside `.table-responsive`
- avoid large horizontal control groups that overflow on mobile
- preserve form labels and validation messages on narrow screens
- keep primary actions visible without requiring precise pointer interaction

## Implementation Order

Recommended order:

1. Record Bootstrap 5 layout conventions in this note and the Static Form App
   contract.
2. Polish Management Console list/detail/edit/new pages.
3. Polish Manual reference pages.
4. Polish Dashboard cards/tables/tabs without changing its data contract.
5. Use card widgets from `web-textus-widget-design.md` for sample app
   validation and later admin/dashboard reuse.

## Related Documents

- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/notes/cncf-web-layer-phase-12.md`
- `docs/notes/web-textus-widget-design.md`
- `docs/design/management-console.md`
- `docs/design/web-form-api-schema.md`
- `docs/journal/2026/04/web-bootstrap-card-widget-note.md`
- `docs/journal/2026/04/web-bootstrap-polish-quality-future-note.md`
