# Web Application Theme and Provider Page Customization Note

status = draft-note
date = 2026-04-25

## Purpose

This note collects source material for a future Web application development
manual.

The target reader is an application/subsystem author who wants to assemble a Web
application from component-provided pages while keeping the visual tone and
small application-specific adaptations coherent.

Two current mechanisms are covered:

- application-wide Bootstrap 5 theme composition
- light customization of Web pages provided by used components

This is not a full customization system. If an application needs layout-level or
behavior-level control, it should provide its own page and call the provider
operation/form APIs directly.

## Responsibility Split

CNCF owns the Web composition layer:

- serves common framework assets under `/web/assets/...`
- injects configured theme assets and Bootstrap variables
- injects light page customization metadata into participating static pages
- keeps WebDescriptor as deployment/subsystem composition metadata

Provider components own their common pages:

- page HTML and local JavaScript
- operation/form API calls
- stable `data-textus-*` hooks if they want to allow light customization
- account/auth/recovery business wording unless overridden by descriptor

Applications/subsystems own composition choices:

- shared brand CSS and Bootstrap variables
- app-scoped CSS/JS refinements
- page title/heading/help/field visibility choices for reused provider pages
- whether to reuse a provider page or replace it with an application-owned page

## Application-Wide Theme Setup

Bootstrap 5 is the assumed UI basis. Applications should use local framework
assets rather than CDN links:

```html
<link href="/web/assets/bootstrap.min.css" rel="stylesheet">
<script src="/web/assets/bootstrap.bundle.min.js"></script>
```

A SAR or runtime Web descriptor can define the shared application theme:

```yaml
web:
  theme:
    name: brand
    css:
      - /web/assets/brand.css
    variables:
      primary: "#14532d"
      body-bg: "#f7f4ec"
      body-color: "#172018"
```

`web.theme.css` is injected into generated pages and component-owned static HTML
pages served through CNCF Web routes. `web.theme.variables` is rendered as
Bootstrap CSS custom properties on `:root`.

Variable names without a leading `--` are treated as Bootstrap variables:

```yaml
variables:
  primary: "#14532d"     # becomes --bs-primary
  --app-shell-bg: "#fff" # remains --app-shell-bg
```

Theme assets can reference nested files under the subsystem Web root:

```text
/web/assets/brand.css
/web/assets/fonts/brand.woff2
/web/assets/images/logo.svg
```

## App-Scoped Theme Refinement

Global theme should carry the shared brand. App-specific differences belong in
`web.apps[].theme`:

```yaml
web:
  apps:
    - name: cwitter
      kind: static-form
      path: /web/cwitter
      root: /web/cwitter
      route: /web/cwitter
      theme:
        css:
          - /web/cwitter/assets/cwitter-theme.css
        variables:
          primary: "#0f766e"
```

Merge order is:

```text
web.theme -> web.apps[].theme
```

This lets the subsystem share a visual baseline while giving an application or
provider page a small local adjustment.

Theme values are presentation metadata. Application business logic must not
depend on theme variables.

## Provider Page Light Customization

Used components can provide common Web pages. For example, `textus-user-account`
provides signup, signin, password reset, and two-factor pages.

The subsystem can customize those pages through `web.pages` without copying the
HTML page:

```yaml
web:
  pages:
    textus-user-account.signup:
      title: Create Cwitter account
      heading: Create account
      subtitle: Cwitter reuses the shared Textus account page; only visible fields are adjusted by the subsystem.
      submitLabel: Create account
      fields:
        - loginName
        - email
        - password
      controls:
        name:
          defaultValue: Cwitter user
        title:
          defaultValue: member
        loginName:
          label: Login name
          help: Cwitter uses this login name as the public handle shown in timelines, mentions, and DMs.
          placeholder: alice
        email:
          label: Email
          help: Used for sign-in, password reset, and optional two-factor notifications.
        password:
          label: Password
          help: Choose a password for this shared Textus account.
```

Selector resolution:

```text
{component}.{app} -> {app}
```

For example:

```text
textus-user-account.signup
signup
```

The component-qualified selector should be preferred in SARs that compose
multiple components.

## Supported Page Customization Fields

The current light customization surface is intentionally small:

```yaml
title: Browser title
heading: Main visible heading
subtitle: Short explanatory text
submitLabel: Primary submit button text
fields: [fieldName, ...]
controls:
  fieldName:
    label: Visible label
    help: Help text
    placeholder: Input placeholder
    defaultValue: Value supplied when a hidden/defaulted field is needed
```

`fields` controls the visible field set and order.

If `fields` is omitted:

- provider default field layout remains visible

If `fields` is present:

- listed fields are shown and ordered by the listed order when possible
- unlisted optional fields are hidden
- unlisted required fields remain visible unless `defaultValue` is explicitly set
- `defaultValue` is a presentation/defaulting aid only; it does not change the
  provider model or operation contract

Undefined optional values are not emitted as JSON `null`. Missing values are
omitted from the injected JSON object so the browser can distinguish "not
configured" from "configured default".

## Provider Page Hook Contract

A provider-owned static page must opt in by exposing stable hooks:

```html
<body data-textus-page="signup">
  <h1 data-textus-role="heading">Create account</h1>
  <p data-textus-role="subtitle">Register a generic account.</p>

  <div data-textus-field="loginName">
    <label for="loginName">Login name</label>
    <input id="loginName" name="loginName" required>
    <div class="form-text">Must be unique.</div>
  </div>

  <button data-textus-role="submit">Create account</button>
</body>
```

Supported hooks:

- `data-textus-page`
- `data-textus-field`
- `data-textus-role="heading"`
- `data-textus-role="subtitle"`
- `data-textus-role="submit"`

The page remains functional without descriptor customization. Hooks are a
composition affordance, not a dependency on a specific application.

## Manual Procedure Draft

1. Decide whether to reuse a provider page or create an application page.

Reuse the provider page when the required change is limited to wording, simple
field selection, or visual theme. Create an application page when layout,
multi-step behavior, or application-specific business logic is needed.

2. Add shared theme assets to the SAR Web root.

Example:

```text
subsystem/web/assets/brand.css
subsystem/web/assets/fonts/brand.woff2
```

3. Declare `web.theme`.

Use Bootstrap variables for broad color/typography/background changes. Keep
application-specific selectors in CSS.

4. Declare app-scoped assets or theme only where needed.

Use `web.apps[].assets` for app scripts/styles and `web.apps[].theme` for
per-app theme refinements.

5. Declare `web.pages` for provider page light customization.

Prefer component-qualified selectors such as `textus-user-account.signup`.
Supply `defaultValue` for any required field that should be hidden.

6. Validate manually in browser.

Recommended checks:

- `/web/{app}` uses the shared theme
- provider signup/signin pages use the same theme
- visible field set matches `fields`
- required hidden fields submit with descriptor-provided defaults
- provider operation succeeds through the original form API

## Cwitter Example Boundary

Cwitter should demonstrate small app-specific code.

The sample can reuse:

- `/web/textus-user-account/signup`
- `/web/textus-user-account/signin`
- password reset and 2FA provider pages

Cwitter-specific wording may appear in `web.pages` because the subsystem is
choosing how the provider page is presented in the Cwitter sample. The provider
model still owns `loginName`; Cwitter can explain that it projects
`handle = loginName`.

This keeps the sample claim intact:

- account UI is reused
- runtime/session behavior is CNCF-managed
- Cwitter code remains focused on timeline, post, mention, and DM behavior

## Open Manual Work

Items to decide before promoting this note into a manual:

- whether to document `web.pages` next to `web.theme` or in a separate
  "provider page reuse" chapter
- whether to include a provider author checklist for stable hooks
- whether to add screenshots or browser-run examples
- whether to provide a descriptor validation command for page customization
