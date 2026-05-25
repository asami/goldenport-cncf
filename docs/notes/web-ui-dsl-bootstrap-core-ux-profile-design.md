# Web UI DSL: Bootstrap Core, Material Design, and UX Profile Design

status=draft
updated_at=2026-05-25

## Purpose

This note defines the Web UI DSL direction for CNCF Static Form Web Apps and
framework-generated Web screens.

The design is based on the draft architecture note
`docs/journal/2026/05/bootstrap-core-ux-profile-architecture.md`, which
separates Web UI construction into:

- Bootstrap Core
- Textus Widget
- UX Profile

This note also treats Material Design as a first-class UX profile target, not
only as a remote future theme candidate. Bootstrap Core remains the stable
server-rendered DOM/component baseline; Material Design becomes a selectable
presentation/interaction profile that can be layered on the same semantic
widget model.

The goal is to let application authors, Cozy generation, and AI-assisted UI
work describe screen intent in stable semantic terms, while CNCF controls the
generated HTML structure, accessibility baseline, runtime enhancement boundary,
and profile-specific presentation.

## Positioning

The CNCF Web strategy remains REST-first. A component may always use an
external Web framework over CNCF REST/Form APIs. The Web UI DSL described here
is the CNCF-owned server-rendered path for:

- management console screens;
- Static Form Web Apps;
- development and verification screens;
- internal business applications that benefit from stable, form-first UI;
- AI-generated or Cozy-generated Web screens.

The DSL is not a general-purpose frontend programming language. It is a
semantic screen-description layer that maps to ordinary Bootstrap-backed HTML
first, and can later apply a Material Design-oriented UX profile without
changing operation paths, data binding, or application-local templates.

## Layer Model

```text
CML / Web Source / Page Context / Operation Result
  -> View Model
  -> Textus Widget Tree
  -> Bootstrap Core DOM
  -> UX Profile
  -> Optional Runtime Enhancement
```

### View Model

The View Model is the data surface exposed to a page or widget. It may come
from:

- operation result data;
- static page properties;
- page context such as session, jobs, notifications, or capabilities;
- CML-derived schema and operation metadata;
- WebDescriptor / `src/main/web-inf/*.yaml` presentation metadata.

The View Model is not raw provider payload. Raw RDF, vectors, HTML bodies,
workbook bytes, or JSON provider responses must be summarized before they reach
ordinary UI widgets.

### Textus Widget Tree

The Textus Widget Tree is the semantic UI DSL.

Examples:

```html
<textus:table source="result.information" view="summary"></textus:table>
<textus:record-card source="result.information" view="detail"></textus:record-card>
<textus:action-form action="result.actions.confirm"></textus:action-form>
<textus:capability-message capability="information:edit" policy="authenticated">
  Log in to edit this information.
</textus:capability-message>
```

Widgets express what the page means:

- information list;
- record detail;
- action form;
- candidate list;
- knowledge summary;
- job panel;
- tag tree;
- capability-gated control.

They do not express low-level layout mechanics such as arbitrary CSS grid
fragments, custom component JavaScript, or provider-specific rendering.

### Bootstrap Core DOM

Bootstrap Core is the stable semantic DOM substrate.

Bootstrap is used as more than a stylesheet dependency. In this architecture,
Bootstrap provides the canonical HTML structure for:

- grid and responsive columns;
- forms and validation feedback;
- tables and pagination;
- cards and summary panels;
- navbars and sidebars;
- alerts, badges, modals, and action groups;
- baseline accessibility attributes.

Textus widgets render to Bootstrap Core DOM. Applications may add local CSS,
but generated HTML must remain usable with the CNCF local Bootstrap assets
alone.

### Standard JavaScript Modules

Textus/CNCF standard JavaScript should be organized by responsibility, not as
one hand-written catch-all file.

Source modules should remain small and independently understandable:

```text
textus-form-validation.js
textus-capability.js
textus-job.js
textus-notification.js
textus-islands.js
```

The runtime may later serve a bundled asset such as `textus-web.js`, but that
bundle is a packaging optimization. It must not become the source-level design
unit.

Initial responsibility boundaries:

- `textus-widgets.js`: generic widget behavior and widget rendering support;
- `textus-form-validation.js`: Bootstrap-compatible field validation display,
  summary alerts, `is-invalid`, `invalid-feedback`, and accessibility wiring;
- `textus-app-shell.js`: header/session/logout/job/notification badge support;
- `textus-islands.js`: future island loader and island lifecycle support.

Application-local JavaScript may still exist for app-specific assistance, but
shared behavior that recurs across Static Form pages, authentication pages, and
component-owned Web pages should move into these Textus modules.

### UX Profile

UX Profile is the presentation and interaction policy applied on top of
Bootstrap Core DOM.

A profile may control:

- typography scale;
- spacing and density;
- card/table compactness;
- color and visual emphasis;
- elevation and borders;
- mobile navigation behavior;
- motion and feedback;
- accessibility contrast.

Initial profile names:

- `bootstrap`
- `material`
- `compact`
- `mobile`
- `admin`

Future candidates:

- `enterprise`
- `kiosk`

Material Design target:

- Material Design should be modeled as a UX profile, not as a separate Web
  runtime.
- Material profile may change typography, density, elevation, color tokens,
  shape, motion, and component emphasis.
- Material profile must not change operation selectors, form field names,
  result data binding, capability checks, or server-side authorization.
- Material-specific JavaScript should remain optional runtime enhancement, not
  a requirement for the server-rendered baseline.
- CNCF should preserve a stable Bootstrap Core fallback even when Material
  profile assets are unavailable.
- `high-contrast`

The profile must not change operation paths, form field names, data binding,
authorization semantics, or widget source paths. It changes presentation, not
meaning.

### Runtime Enhancement

The default Web UI DSL runtime is server-rendered and static-first.

JavaScript may enhance the experience, but the primary execution route remains
HTML links and forms. Acceptable enhancement examples:

- normalize ISBN input before submit;
- copy identifiers;
- toggle panels;
- highlight the current sidebar item;
- improve table affordances;
- refresh job status;
- progressively enhance a candidate selector.

JavaScript must not become the owner of business execution. Mutation continues
through operation forms, Form API validation, or explicit REST/Form operation
calls controlled by CNCF.

Possible future enhancement mechanisms include htmx and island-style partial
hydration, but they need explicit contracts before becoming normative.

## Source Layout Contract

The Web UI DSL uses the current Static Form Web App source split:

```text
src/main/web/*.html                     authored public pages and templates
src/main/web/assets/...                 authored public assets
src/main/web/WEB-INF/layouts/...        private layouts
src/main/web/WEB-INF/partials/...       private partials
src/main/web/WEB-INF/widgets/...        private reusable fragments
src/main/web-inf/web.yaml               Web app metadata
src/main/web-inf/form.yaml              form and operation presentation metadata
src/main/web-inf/admin.yaml             admin/debug metadata
```

`src/main/web` is the authored Web application tree. `src/main/web-inf` is the
authored metadata tree. CAR runtime descriptors under `web/WEB-INF/*.yaml` are
generated packaging output.

## DSL Source Forms

The Web UI DSL can be authored or generated from multiple sources:

- CML component, service, operation, entity, value, powertype, statemachine,
  view, and usecase metadata;
- HTML pages containing Textus widgets;
- `src/main/web-inf/web.yaml` for application, layout, route, shell, profile,
  and asset metadata;
- `src/main/web-inf/form.yaml` for operation/form presentation metadata;
- page context supplied by CNCF at render time;
- operation result records.

CML is the preferred semantic source for domain and operation shape.
`web.yaml` and `form.yaml` supplement presentation and routing details. HTML
pages arrange screen composition and include Textus widgets. None of these
sources should duplicate the full meaning of the others.

## Canonical Widget Vocabulary

The widget namespace form is canonical:

```html
<textus:table></textus:table>
```

Dash-form aliases should not be introduced for new widgets unless a specific
HTML tooling limitation requires it.

Core widget categories:

- data display: `textus:table`, `textus:record-card`,
  `textus:description-list`, `textus:field-list`;
- layout and summary: `textus:section`, `textus:card`,
  `textus:summary-card`, `textus:card-list`;
- action and workflow: `textus:action-link`, `textus:action-form`,
  `textus:confirm-action`, `textus:operation-panel`;
- status and feedback: `textus:status-badge`, `textus:error-panel`,
  `textus:empty-state`, `textus:capability-message`;
- domain/runtime helpers: `textus:candidate-list`,
  `textus:knowledge-summary`, `textus:job-panel`, `textus:tag-tree`.

Widget names should describe semantic intent, not the source object that happens
to feed them. For example, `textus:table` is preferred over a result-specific
name because the input can be an operation result, page context, static data,
or another record collection.

## Binding Model

Widgets bind to data through stable paths:

```html
<textus:table source="result.items" view="summary"></textus:table>
<textus:summary-card title="Published" value="${result.counts.published}"></textus:summary-card>
```

Rules:

- `source` selects the input object or collection.
- `view` selects CML/schema/WebDescriptor field metadata.
- `columns` may provide explicit field selection for tabular widgets.
- `href`, `detail-href`, `row-link`, and action metadata define navigation.
- missing optional values render as empty/omitted according to widget rules.
- field order comes from schema/view metadata or explicit `columns`, never
  unordered map iteration.

The binding model must remain deterministic so generated pages are reviewable
and AI-editable.

## Capability-Aware UI

Capability-aware UI is a DSL-level concern, not an application-specific
JavaScript convention.

Use canonical attributes:

```html
data-textus-capability="information:edit"
data-textus-capability-mode="hide|disable"
data-textus-capability-policy="authenticated|subject"
```

Use `textus:capability-message` for user-facing explanation.

The visual gate does not replace operation authorization. It is a page-level
guidance layer that keeps users from being led into unavailable write flows.

## UX Profile Metadata

`web.yaml` may name the active profile:

```yaml
apps:
  - name: textus-knowledge-editor
    layout: default
    uxProfile: bootstrap
```

Open design point: the exact field name may be `uxProfile`, `profile`, or
`theme.profile`. The final name should be selected when implementation begins.

Profile metadata may tune:

- dashboard density;
- table density;
- card equal-height behavior;
- sidebar mobile mode;
- form label placement;
- action button grouping;
- message severity rendering.

Profile metadata must not define domain behavior or operation authorization.

## AI-Assisted UI Generation Rules

The DSL exists partly to make AI-generated screens stable and reviewable.

AI-generated UI should:

- choose semantic widgets before hand-writing Bootstrap fragments;
- use Bootstrap Core classes when raw HTML is needed;
- keep business execution on forms/actions;
- prefer Markdown-like readable HTML fragments over complex JavaScript;
- use CML/schema names consistently;
- keep raw provider payloads out of ordinary UI;
- preserve source layout boundaries;
- use capability attributes for write controls;
- keep mobile behavior in the profile/layout layer rather than per-page hacks.

AI-generated UI should not:

- create a hidden SPA inside Static Form pages;
- invent application-specific permission attributes when CNCF capability
  attributes exist;
- encode domain workflow only in JavaScript;
- depend on CDN CSS or JavaScript;
- use one-off CSS systems that conflict with composed component Web pages.

## Relationship To CML And Cozy

Future Cozy generation should be able to produce Textus Widget Trees from CML.

Expected generation flow:

```text
CML
  -> component/service/operation/entity metadata
  -> view model contract
  -> source HTML with Textus widgets
  -> src/main/web-inf/web.yaml + form.yaml
  -> CAR web/WEB-INF runtime descriptors
```

CML remains the semantic source for:

- operation names and execution policy;
- input/output types;
- entity/value field definitions;
- powertype and statemachine vocabulary;
- usecase and scenario descriptions;
- operation/entity Web hints when those hints describe the operation or field
  itself.

`web.yaml` and `form.yaml` remain the source for app composition and
presentation overrides that do not belong in domain CML.

## Implementation Direction

Near-term implementation should proceed in small slices:

1. Normalize the current Static Form widgets around `textus:` namespace names.
2. Keep Bootstrap Core output stable and covered by renderer specs.
3. Add a minimal UX Profile model with a default `bootstrap` profile.
4. Move repeated TKE layout decisions into profile/layout metadata where they
   are generally useful.
5. Extend widgets when pages need new output shape instead of adding
   application-local rendering JavaScript.
6. Add screenshot/browser smoke only for profile/layout changes where static
   HTML assertions are not enough.

## Open Questions

- What is the final `web.yaml` key for UX Profile selection?
- Should profile metadata be resolved per application, page, widget, or all
  three?
- Which profile properties are safe to expose before they become a parallel
  design system?
- How should future htmx/island enhancement be declared without weakening the
  form-first runtime contract?
- How much CML Web metadata belongs directly on operation/entity definitions
  before it should move to `web-inf` presentation metadata?

## Related Documents

- `docs/journal/2026/05/bootstrap-core-ux-profile-architecture.md`
- `docs/notes/web-textus-widget-design.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/notes/static-form-web-app-bootstrap-guide.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `docs/spec/textus-widget.md`
